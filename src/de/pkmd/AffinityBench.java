package de.pkmd;

import net.openhft.affinity.*;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.concurrent.*;

public class AffinityBench {

    static int RunTimeMillis = 10 * 1000;
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

        SynchronousQueue<Item> sQueue = new SynchronousQueue<>();
        ArrayBlockingQueue<Item> abQueue = new ArrayBlockingQueue<>( 1);
        runBenches( idla, sQueue);
        runBenches( idla, abQueue);
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
        Thread producer = createProducer( prodCPUid, RunTimeMillis, queue, true);
        Thread consumer = createConsumer( consCPUid, RunTimeMillis, queue, true);
        startJoin( producer, consumer, qShortName( queue) + " runSingleSocket r/w: " + prodCPUid + "/" + consCPUid);
        producer = createProducer( prodCPUid, RunTimeMillis, queue, false);
        consumer = createConsumer( consCPUid, RunTimeMillis, queue, false);
        startJoin( producer, consumer, qShortName( queue) + " runSingleSocket dry: " + prodCPUid + "/" + consCPUid);
    }

    private static void runSingleSocket(IDefaultLayoutAffinity idla, BlockingQueue<Item> queue) {
        CpuLayout cpuLayout = idla.getDefaultLayout();
        int socket = cpuLayout.sockets() - 1;
        for ( int prodCore = 0;  prodCore < cpuLayout.coresPerSocket();  prodCore++) {
            for ( int consCore = 0;  consCore < cpuLayout.coresPerSocket();  consCore++) {
                if ( prodCore < consCore) {
                    int prodCPUid = findCPUOnCoreSocket( cpuLayout, prodCore, socket);
                    if (prodCPUid == -1) {
                        System.err.println( "did not find cpu on socket " + socket + " core " + prodCore);
                        return;
                    }
                    int consCPUid = findCPUOnCoreSocket( cpuLayout, consCore, socket);
                    if (consCPUid == -1) {
                        System.err.println( "did not find cpu on socket " + socket + " core " + consCore);
                        return;
                    }
                    Thread producer = createProducer( prodCPUid, RunTimeMillis, queue, true);
                    Thread consumer = createConsumer( consCPUid, RunTimeMillis, queue, true);
                    startJoin( producer, consumer, qShortName( queue) + " runSingleSocket r/w: " + prodCPUid + "/" + consCPUid);
                    producer = createProducer( prodCPUid, RunTimeMillis, queue, false);
                    consumer = createConsumer( consCPUid, RunTimeMillis, queue, false);
                    startJoin( producer, consumer, qShortName( queue) + " runSingleSocket dry: " + prodCPUid + "/" + consCPUid);
                }
            }
        }
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
        Thread producer = createProducer( prodCPUid, RunTimeMillis, queue, true);
        Thread consumer = createConsumer( consCPUid, RunTimeMillis, queue, true);
        startJoin( producer, consumer, qShortName( queue) + " runSingleCore r/w: " + prodCPUid + "/" + consCPUid);
        producer = createProducer( prodCPUid, RunTimeMillis, queue, false);
        consumer = createConsumer( consCPUid, RunTimeMillis, queue, false);
        startJoin( producer, consumer, qShortName( queue) + " runSingleCore dry: " + prodCPUid + "/" + consCPUid);
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
        Thread producer = createProducer( cpuID, RunTimeMillis, queue, true);
        Thread consumer = createConsumer( cpuID, RunTimeMillis, queue, true);
        startJoin( producer, consumer, qShortName( queue) + " runSingleLCPU r/w: " + cpuID);
        producer = createProducer( cpuID, RunTimeMillis, queue, false);
        consumer = createConsumer( cpuID, RunTimeMillis, queue, false);
        startJoin( producer, consumer, qShortName( queue) + " runSingleLCPU dry: " + cpuID);
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
        }, "comsumer");
        return t;
    }

}
