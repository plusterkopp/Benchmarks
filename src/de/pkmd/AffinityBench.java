package de.pkmd;

import net.openhft.affinity.*;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class AffinityBench {

    static int RunTimeMillis = 30 * 1000;

    // Counter
    static volatile long producerLoopCount = 0;
    static void incrementP() {
//        producerLoopCount.increment();
        producerLoopCount++;
    }
    static long producerLoopCount() {
//        return producerLoopCount.longValue();
        return producerLoopCount;
    }
    static void producerLoopCountReset() {
//        producerLoopCount.reset();
        producerLoopCount = 0;
    }

    static volatile long consumerLoopCount = 0;
    static void incrementC() {
//        consumerLoopCount.increment();
        consumerLoopCount++;
    }
    static long consumerLoopCount() {
//        return consumerLoopCount.longValue();
        return consumerLoopCount;
    }
    static void consumerLoopCountReset() {
//        consumerLoopCount.reset();
        consumerLoopCount = 0;
    }

    static volatile boolean ProducerFinished;

    static byte blackHole = 0;

    private static class Item {
	    static int L2Size = 256 * 1024;
	    static int cacheLineSize = 64;
        byte[] l2cacheBytes = new byte[ L2Size];

        public void write() {
            for ( int i = 0;  i < l2cacheBytes.length;  i += cacheLineSize) {
                l2cacheBytes[ i] = (byte) (i & 255);
            }
        }
        public void read() {
            for ( int i = 0;  i < l2cacheBytes.length;  i += cacheLineSize) {
                blackHole &= l2cacheBytes[ i];
            }
        }
    }

    static Item item1 = new Item();
    static Item item2 = new Item();

    public static void main( String[] args) {
        IAffinity iaff = Affinity.getAffinityImpl();
        if ( ! ( iaff instanceof IDefaultLayoutAffinity)) {
            return;
        }
        IDefaultLayoutAffinity idla = (IDefaultLayoutAffinity) iaff;
        findCoresBySocket( idla);
        printLayout( idla);

        SynchronousQueue<Item> sQueue = new SynchronousQueue<>();
        ArrayBlockingQueue<Item> abQueue = new ArrayBlockingQueue<>( 1);
        runBenches( idla, sQueue);
        runBenches( idla, abQueue);
    }

    static Map<Integer, SortedSet<Integer>> coresBySocket = new HashMap<>();

    private static void findCoresBySocket(IDefaultLayoutAffinity idla) {
        CpuLayout cpuLayout = idla.getDefaultLayout();
        for ( int cpuID = 0;  cpuID < cpuLayout.cpus();  cpuID++) {
            int core = cpuLayout.coreId( cpuID);
            int socket = cpuLayout.socketId( cpuID);
            SortedSet coresOnSocket = coresBySocket.computeIfAbsent( socket, s -> new TreeSet<>());
            coresOnSocket.add( core);
        }
//        System.out.println( "coresBySocket: " + coresBySocket);
    }

    private static void printLayout(IDefaultLayoutAffinity idla) {
        CpuLayout cpuLayout = idla.getDefaultLayout();
        StringBuilder sb = new StringBuilder();
        for ( int cpuID = 0;  cpuID < cpuLayout.cpus();  cpuID++) {
            if ( cpuID > 0) {
                sb.append( ", ");
            }
            int core = cpuLayout.coreId( cpuID);
            int socket = cpuLayout.socketId( cpuID);
            sb.append( String.format(
                    "L%02d %02d/%d", cpuID, core, socket));
        }
        System.out.println( "Layout: " + sb);
    }

    private static void runBenches(IDefaultLayoutAffinity idla, BlockingQueue<Item> queue) {
        CpuLayout cpuLayout = idla.getDefaultLayout();

        runSingleLCPU( idla, queue);
        if (cpuLayout.threadsPerCore() > 1) {
            runSingleCore( idla, queue);
        }
        if ( cpuLayout.coresPerSocket() > 1) {
            runSingleSocket( idla, queue);
        }
        if ( cpuLayout.sockets() > 1) {
            runMultiSocket( idla, queue);
        }
    }

    private static void runMultiSocket(IDefaultLayoutAffinity idla, BlockingQueue<Item> queue) {
        runMultiSocket(idla, queue, false);
        runMultiSocket(idla, queue, true);
    }

    private static void runMultiSocket(IDefaultLayoutAffinity idla, BlockingQueue<Item> queue, boolean readWrite) {
        CpuLayout cpuLayout = idla.getDefaultLayout();
        SortedSet<Integer> sockets = new TreeSet<>(coresBySocket.keySet());
        int maxSocketID = Collections.max( sockets);
        double[][] resultA = new double[ maxSocketID + 1][ maxSocketID + 1];
        for ( double[] line: resultA) {
            Arrays.fill( line, Double.NaN);
        }
        for ( int prodSocket: sockets) {
            for ( int consSocket: sockets) {
                if ( prodSocket < consSocket) {
                    int prodCPUid = findCPUOnCoreSocket( cpuLayout, -1, prodSocket);
                    if (prodCPUid == -1) {
                        System.err.println( "did not find cpu on socket " + 0);
                        continue;
                    }
                    int consCPUid = findCPUOnCoreSocket( cpuLayout, -1, consSocket);
                    if (consCPUid == -1) {
                        System.err.println( "did not find cpu on socket " + 1);
                        continue;
                    }
                    final String cpuInfo = cpuInfo(idla, prodCPUid) + "-" + cpuInfo(idla, consCPUid);
                    Thread producer = createProducer( prodCPUid, RunTimeMillis, queue, readWrite);
                    Thread consumer = createConsumer( consCPUid, RunTimeMillis, queue, readWrite);
                    startJoin( producer, consumer,
                            qShortName( queue) + " runSingleSocket " +( readWrite ? "r/w" : "dry") + ": " + cpuInfo);
                    resultA[ prodSocket][ consSocket] = 1000.0 * producerLoopCount() / RunTimeMillis;
                }
            }
        }
        System.out.print( " ; ");
        for ( int socket: sockets) {
            System.out.print( socket + "; ");
        }
        System.out.println();
        for ( int prodSocket: sockets) {
            System.out.print( prodSocket + "; ");
            for ( int consSocket: sockets) {
                final double v = resultA[prodSocket][consSocket];
                if ( Double.isNaN( v)) {
                    System.out.print( " ; ");
                } else {
                    System.out.print(String.format( "%.0f; ", v));
                }
            }
            System.out.println();
        }
    }

    private static void runSingleSocket(IDefaultLayoutAffinity idla, BlockingQueue<Item> queue) {
	    runSingleSocket(idla, queue, false);
	    runSingleSocket(idla, queue, true);
    }

    private static void runSingleSocket(IDefaultLayoutAffinity idla, BlockingQueue<Item> queue, boolean readWrite) {
        CpuLayout cpuLayout = idla.getDefaultLayout();
        int socket = cpuLayout.sockets() - 1;
        int maxCoreID = Collections.max( coresBySocket.get( socket));
        double[][] resultA = new double[ maxCoreID + 1][ maxCoreID + 1];
        for ( double[] line: resultA) {
            Arrays.fill( line, Double.NaN);
        }

        for (int prodCore: coresBySocket.get( socket)) {
            for (int consCore: coresBySocket.get( socket)) {
                if ( prodCore < consCore) {
                    int consCPUid = findCPUOnCoreSocket( cpuLayout, consCore, socket);
                    if (consCPUid == -1) {
                        System.err.println( "did not find cpu on socket " + socket + " core " + consCore);
                        continue;
                    }
                    int prodCPUid = findCPUOnCoreSocket( cpuLayout, prodCore, socket);
                    if (prodCPUid == -1) {  // core IDs need not be continuous
                        System.err.println( "did not find cpu on socket " + socket + " core " + prodCore);
                        continue;
                    }
                    final String cpuInfo = cpuInfo(idla, prodCPUid) + "-" + cpuInfo(idla, consCPUid);
                    Thread producer = createProducer( prodCPUid, RunTimeMillis, queue, readWrite);
                    Thread consumer = createConsumer( consCPUid, RunTimeMillis, queue, readWrite);
                    startJoin( producer, consumer,
                            qShortName( queue)
		                            + " runSingleSocket " +
		                            ( readWrite ? "r/w" : "dry") +
		                            ": " + cpuInfo);
                    resultA[ prodCore][ consCore] = 1000.0 * producerLoopCount() / RunTimeMillis;
                }
            }
        }
        System.out.print( " ; ");
        for ( int core: coresBySocket.get( socket)) {
            System.out.print( core + "; ");
        }
        System.out.println();
        for (int prodCore: coresBySocket.get( socket)) {
            System.out.print( prodCore + "; ");
            for (int consCore : coresBySocket.get(socket)) {
                final double v = resultA[prodCore][consCore];
                if ( Double.isNaN( v)) {
                    System.out.print( " ; ");
                } else {
                    System.out.print(String.format( "%.0f; ", v));
                }
            }
            System.out.println();
        }
    }

    private static void runSingleCore(IDefaultLayoutAffinity idla, BlockingQueue<Item> queue) {
        runSingleCore( idla, queue, false);
        runSingleCore( idla, queue, true);
    }

    private static void runSingleCore(IDefaultLayoutAffinity idla, BlockingQueue<Item> queue, boolean readWrite) {
        CpuLayout cpuLayout = idla.getDefaultLayout();
        int prodCPUid = cpuLayout.cpus() - 1;
        int consCPUid = getCPUSameCoreSameSocket( cpuLayout, prodCPUid);
        if (consCPUid == -1) {
            System.err.println( "did not find cpu same socket same core for " + prodCPUid);
            return;
        }
        final String cpuInfo = cpuInfo(idla, prodCPUid) + "-" + cpuInfo(idla, consCPUid);
        Thread producer = createProducer( prodCPUid, RunTimeMillis, queue, readWrite);
        Thread consumer = createConsumer( consCPUid, RunTimeMillis, queue, readWrite);
        startJoin( producer, consumer, qShortName( queue) + " runSingleCore " + ( readWrite ? "r/w" : "dry") +
                                               ": " + cpuInfo);
    }

    private static void runSingleLCPU(IDefaultLayoutAffinity idla, BlockingQueue<Item> queue) {
	    runSingleLCPU( idla, queue, false);
	    runSingleLCPU( idla, queue, true);
    }

    private static void runSingleLCPU(IDefaultLayoutAffinity idla, BlockingQueue<Item> queue, boolean readWrite) {
        CpuLayout cpuLayout = idla.getDefaultLayout();
        int cpuID = cpuLayout.cpus() - 1;
        final String cpuInfo = cpuInfo(idla, cpuID);
        Thread producer = createProducer( cpuID, RunTimeMillis, queue, readWrite);
        Thread consumer = createConsumer( cpuID, RunTimeMillis, queue, readWrite);
        startJoin( producer, consumer, qShortName( queue)
                                               + " runSingleLCPU " + ( readWrite ? "r/w" : "dry") + ": " + cpuInfo);
    }

    private static void startJoin(Thread producer, Thread consumer, String name) {
        long then = System.currentTimeMillis();
        try {
            producer.start();
            consumer.start();
            producer.join();
            consumer.join();
        } catch ( InterruptedException ie) {
            System.out.println( "interrupted");
        }
        if ( producerLoopCount() != consumerLoopCount()) {
            System.err.println( name + ": " + producerLoopCount() + " != " + consumerLoopCount());
        }
        long durMS = System.currentTimeMillis() - then;
        double runsPerS = producerLoopCount() / ( 0.001 * RunTimeMillis);
        NumberFormat nf = DecimalFormat.getIntegerInstance();
        nf.setGroupingUsed( true);
        System.out.println( name + ": " + nf.format( runsPerS) + " /sec");
    }

    private static Thread createProducer(int cpuID, int runTimeMillis, BlockingQueue<Item> queue, boolean readWrite) {
        producerLoopCountReset();
        ProducerFinished = false;
        long stopAtTS = System.currentTimeMillis() + runTimeMillis;
        Thread t = new Thread( () -> {
            Affinity.setAffinity( cpuID);
            try {
                Item item = item1;
                if (readWrite) {
                    item.write();
                }
                queue.put(item);
                incrementP();
                while (System.currentTimeMillis() < stopAtTS) {
                    if (item == item1) {
                        item = item2;
                    } else {
                        item = item1;
                    }
                    if (readWrite) {
                        item.write();
                    }
                    queue.put(item);
                    incrementP();
                }
            } catch ( InterruptedException ie) {
                ie.printStackTrace();
            } finally {
                ProducerFinished = true;
            }
        }, "producer");
        return t;
    }

    private static Thread createConsumer(int cpuID, int runTimeMillis, BlockingQueue<Item> queue, boolean readWrite) {
        consumerLoopCountReset();
        long startTS = System.currentTimeMillis();
        long stopAtTS = startTS + runTimeMillis;
        Thread t = new Thread( () -> {
            Affinity.setAffinity( cpuID);
            try {
                Item item;
                long loops = 0;
                while ( ( ! ProducerFinished) || ! queue.isEmpty()) {
                    loops++;
                    while ( ( item = queue.poll( 20, TimeUnit.MICROSECONDS)) != null) {
                        if (readWrite) {
                            item.read();
                        }
                        incrementC();
                    }
                }
                long now = System.currentTimeMillis();
                if ( consumerLoopCount() != producerLoopCount() /*|| now < stopAtTS*/) {    // sollte nicht passieren
                    System.out.println(
                            Thread.currentThread().getName()
                            + " exit after " + (now - startTS) + " ms "
                            + "(" + ( stopAtTS - now) + " ms early) with "
                            + consumerLoopCount() + " consumed, " + producerLoopCount() + " produced");
                }
//                if ( loops > 0) {
//                    System.out.println( String.format( "%,d loops", loops));
//                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "consumer");
        return t;
    }

	private static String cpuInfo(IDefaultLayoutAffinity idla, int cpu) {
		CpuLayout cpuLayout = idla.getDefaultLayout();
		return String.format( "%02d/%02d/%d", cpu, cpuLayout.coreId(cpu), cpuLayout.socketId(cpu));
	}

	private static int findCPUOnCoreSocket(CpuLayout cpuLayout, int core, int socket) {
		for ( int cpuID = 0;  cpuID < cpuLayout.cpus();  cpuID++) {
			if ( core == -1 || cpuLayout.coreId( cpuID) == core) {  // -1 : ignore core (match every core)
				if ( cpuLayout.socketId( cpuID) == socket) {
					return cpuID;
				}
			}
		}
		return -1;
	}

	private static int getCPUSameCoreSameSocket( CpuLayout cpuLayout, int prodCPUid) {
		for ( int i = 0;  i < cpuLayout.cpus();  i++) {
			if (i != prodCPUid) {
				if ( cpuLayout.socketId( prodCPUid) == cpuLayout.socketId( i)) {
					if ( cpuLayout.coreId( prodCPUid) == cpuLayout.coreId( i)) {
						return i;
					}
				}
			}
		}
		return -1;
	}

	private static String qShortName( BlockingQueue q) {
		if (q instanceof SynchronousQueue) {
			return "SQ";
		}
		if (q instanceof ArrayBlockingQueue) {
			return "AQ";
		}
		return "..";
	}


}
