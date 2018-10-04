package de.pkmd;

import net.openhft.affinity.*;
import org.apache.commons.math3.ml.clustering.*;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class AffinityBench {

    static int RunTimeMillis = 1 * 1000;
    static final CountDownLatch startLatch = new CountDownLatch( 2);

    static int L2Size = 256 * 1024;

    static long producerLoopCount = 0;
    static long consumerLoopCount = 0;

    static byte blackHole = 0;

    private static class Item {
        byte[] l2cacheBytes = new byte[ L2Size];
        int cacheLineSize = 64;

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
        CpuLayout cpuLayout = idla.getDefaultLayout();
        int prodCPUid = findCPUOnCoreSocket( cpuLayout, -1, 0);
        if (prodCPUid == -1) {
            System.err.println( "did not find cpu on socket " + 0);
            return;
        }
        int consCPUid = findCPUOnCoreSocket( cpuLayout, -1, 1);
        if (consCPUid == -1) {
            System.err.println( "did not find cpu on socket " + 1);
            return;
        }
        final String cpuInfo = cpuInfo(idla, prodCPUid) + "-" + cpuInfo(idla, consCPUid);
        Thread producer;
        Thread consumer;
//        producer = createProducer( prodCPUid, RunTimeMillis, queue, true);
//        consumer = createConsumer( consCPUid, RunTimeMillis, queue, true);
//        startJoin( producer, consumer, qShortName( queue) + " runSingleSocket r/w: " + cpuInfo);
        producer = createProducer( prodCPUid, RunTimeMillis, queue, false);
        consumer = createConsumer( consCPUid, RunTimeMillis, queue, false);
        startJoin( producer, consumer, qShortName( queue) + " runSingleSocket dry: " + cpuInfo);
    }

    private static class ResultInfo implements Clusterable {
        int idProducer;
        int idConsumer;
        String info;
        double[] valueA;

        private ResultInfo(int idProducer, int idConsumer, String info, double value) {
            this.idProducer = idProducer;
            this.idConsumer = idConsumer;
            this.info = info;
            valueA = new double[ 1];
            valueA[ 0] = value;
        }

        @Override
        public double[] getPoint() {
            return valueA;
        }

        @Override
        public String toString() {
            return info;
        }
    }

    private static void runSingleSocket(IDefaultLayoutAffinity idla, BlockingQueue<Item> queue) {
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
                    Thread producer;
                    Thread consumer;
//                    producer = createProducer( prodCPUid, RunTimeMillis, queue, true);
//                    consumer = createConsumer( consCPUid, RunTimeMillis, queue, true);
//                    startJoin( producer, consumer,
//                            qShortName(queue) + " runSingleSocket r/w: " + cpuInfo);
                    producer = createProducer( prodCPUid, RunTimeMillis, queue, false);
                    consumer = createConsumer( consCPUid, RunTimeMillis, queue, false);
                    startJoin( producer, consumer,
                            qShortName( queue) + " runSingleSocket dry: " + cpuInfo);
                    resultA[ prodCore][ consCore] = 1.0 * producerLoopCount / RunTimeMillis;
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
                    System.out.print(v + "; ");
                }
            }
            System.out.println();
        }
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

    private static void runSingleCore(IDefaultLayoutAffinity idla, BlockingQueue<Item> queue) {
        CpuLayout cpuLayout = idla.getDefaultLayout();
        int prodCPUid = cpuLayout.cpus() - 1;
        int consCPUid = getCPUSameCoreSameSocket( cpuLayout, prodCPUid);
        if (consCPUid == -1) {
            System.err.println( "did not find cpu same socket same core for " + prodCPUid);
            return;
        }
        final String cpuInfo = cpuInfo(idla, prodCPUid) + "-" + cpuInfo(idla, consCPUid);
        Thread producer;
        Thread consumer;
//        producer = createProducer( prodCPUid, RunTimeMillis, queue, true);
//        consumer = createConsumer( consCPUid, RunTimeMillis, queue, true);
//        startJoin( producer, consumer, qShortName( queue) + " runSingleCore r/w: " + cpuInfo);
        producer = createProducer( prodCPUid, RunTimeMillis, queue, false);
        consumer = createConsumer( consCPUid, RunTimeMillis, queue, false);
        startJoin( producer, consumer, qShortName( queue) + " runSingleCore dry: " + cpuInfo);
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

    private static void runSingleLCPU(IDefaultLayoutAffinity idla, BlockingQueue<Item> queue) {
        CpuLayout cpuLayout = idla.getDefaultLayout();
        int cpuID = cpuLayout.cpus() - 1;
        final String cpuInfo = cpuInfo(idla, cpuID);
        Thread producer;
        Thread consumer;
//        producer = createProducer( cpuID, RunTimeMillis, queue, true);
//        consumer = createConsumer( cpuID, RunTimeMillis, queue, true);
//        startJoin( producer, consumer, qShortName( queue) + " runSingleLCPU r/w: " + cpuInfo);
        producer = createProducer( cpuID, RunTimeMillis, queue, false);
        consumer = createConsumer( cpuID, RunTimeMillis, queue, false);
        startJoin( producer, consumer, qShortName( queue) + " runSingleLCPU dry: " + cpuInfo);
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
        if ( producerLoopCount != consumerLoopCount) {
            System.err.println( name + ": " + producerLoopCount + " != " + consumerLoopCount);
        }
        long durMS = System.currentTimeMillis() - then;
        double runsPerS = producerLoopCount / ( 0.001 * durMS);
        NumberFormat nf = DecimalFormat.getIntegerInstance();
        nf.setGroupingUsed( true);
        System.out.println( name + ": " + nf.format( runsPerS) + " /sec");
    }

    private static Thread createProducer(int cpuID, int runTimeMillis, BlockingQueue<Item> queue, boolean readWrite) {
        producerLoopCount = 0;
        long stopAtTS = System.currentTimeMillis() + runTimeMillis;
        Thread t = new Thread( () -> {
            Affinity.setAffinity( cpuID);
            try {
                Item item = item1;
                if (readWrite) {
                    item.write();
                }
                queue.put(item);
                producerLoopCount++;
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
                    producerLoopCount++;
                }
            } catch ( InterruptedException ie) {
                ie.printStackTrace();
            }
        }, "producer");
        return t;
    }

    private static Thread createConsumer(int cpuID, int runTimeMillis, BlockingQueue<Item> queue, boolean readWrite) {
        consumerLoopCount = 0;
        long startTS = System.currentTimeMillis();
        long stopAtTS = startTS + runTimeMillis;
        Thread t = new Thread( () -> {
            Affinity.setAffinity( cpuID);
            try {
                Item item;
                while ( ( item = queue.poll( 100, TimeUnit.MILLISECONDS)) != null) {
                    if (readWrite) {
                        item.read();
                    }
                    consumerLoopCount++;
                }
                long now = System.currentTimeMillis();
                if ( consumerLoopCount != producerLoopCount || now < stopAtTS) {
                    System.out.println("c exit after " + (now - startTS) + " ms with "
                            + consumerLoopCount + " consumed, " + producerLoopCount + " produced");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "consumer");
        return t;
    }

}
