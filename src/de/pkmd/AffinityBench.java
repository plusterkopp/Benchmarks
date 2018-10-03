package de.pkmd;

import net.openhft.affinity.*;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class AffinityBench {

    static int RunTimeMillis = 10 * 1000;
    static final CountDownLatch startLatch = new CountDownLatch( 2);
    static final SynchronousQueue<Item> queue = new SynchronousQueue();

    static int L2Size = 256 * 1024;

    static long producerLoopCount = 0;
    static long consumerLoopCount = 0;

    static byte blackHole = 0;

    private static class Item {
        byte[] l2cacheBytes = new byte[ L2Size];

        public void write() {
            for ( int i = 0;  i < l2cacheBytes.length;  i += 8) {
                l2cacheBytes[ i] = (byte) (i & 255);
            }
        }
        public void read() {
            for ( int i = 0;  i < l2cacheBytes.length;  i += 8) {
                blackHole &= l2cacheBytes[ i];
            }
        }
    }

    static Item item1 = new Item();
    static Item item2 = new Item();

    public static void main( String[] args) {
        IAffinity iaff = Affinity.getAffinityImpl();
        if ( iaff instanceof IDefaultLayoutAffinity) {
            IDefaultLayoutAffinity idla = (IDefaultLayoutAffinity) iaff;
            CpuLayout cpuLayout = idla.getDefaultLayout();
            runSingleLCPU( idla);
            if (cpuLayout.threadsPerCore() > 1) {
                runSingleCore( idla);
            }
            if ( cpuLayout.coresPerSocket() > 1) {
                runSingleSocket( idla);
            }

        }
    }

    private static void runSingleSocket(IDefaultLayoutAffinity idla) {
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
                    Thread producer = createProducer( prodCPUid, RunTimeMillis);
                    Thread consumer = createConsumer( consCPUid);
                    startJoin( producer, consumer, "runSingleSocket: " + prodCPUid + "/" + consCPUid);
                }
            }
        }
    }

    private static int findCPUOnCoreSocket(CpuLayout cpuLayout, int core, int socket) {
        for ( int cpuID = 0;  cpuID < cpuLayout.cpus();  cpuID++) {
            if ( cpuLayout.coreId( cpuID) == core) {
                if ( cpuLayout.socketId( cpuID) == socket) {
                    return cpuID;
                }
            }
        }
        return -1;
    }

    private static void runSingleCore(IDefaultLayoutAffinity idla) {
        CpuLayout cpuLayout = idla.getDefaultLayout();
        int prodCPUid = cpuLayout.cpus() - 1;
        int consCPUid = getCPUSameCoreSameSocket( cpuLayout, prodCPUid);
        if (consCPUid == -1) {
            System.err.println( "did not find cpu same socket same core for " + prodCPUid);
            return;
        }
        Thread producer = createProducer( prodCPUid, RunTimeMillis);
        Thread consumer = createConsumer( consCPUid);
        startJoin( producer, consumer, "runSingleCore: " + prodCPUid + "/" + consCPUid);
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

    private static void runSingleLCPU(IDefaultLayoutAffinity idla) {
        CpuLayout cpuLayout = idla.getDefaultLayout();
        int cpuID = cpuLayout.cpus() - 1;
        Thread producer = createProducer( cpuID, RunTimeMillis);
        Thread consumer = createConsumer( cpuID);
        startJoin( producer, consumer, "runSingleLCPU: " + cpuID);
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
        long durMS = System.currentTimeMillis() - then;
        double runsPerS = producerLoopCount / ( 0.001 * durMS);
        NumberFormat nf = DecimalFormat.getIntegerInstance();
        nf.setGroupingUsed( true);
        System.out.println( name + ": " + nf.format( runsPerS) + " /sec");
    }

    private static Thread createProducer(int cpuID, int runTimeMillis) {
        producerLoopCount = 0;
        long stopAtTS = System.currentTimeMillis() + runTimeMillis;
        Thread t = new Thread( () -> {
            Affinity.setAffinity( cpuID);
            Item item = item1;
            item.write();
            queue.offer( item);
            while ( System.currentTimeMillis() < stopAtTS) {
                if ( item == item1) {
                    item = item2;
                } else {
                    item = item1;
                }
                item.write();
                queue.offer( item);
                producerLoopCount++;
            }
        }, "producer");
        return t;
    }

    private static Thread createConsumer(int cpuID) {
        consumerLoopCount = 0;
        Thread t = new Thread( () -> {
            Affinity.setAffinity( cpuID);
            try {
                Item item;
                while ( ( item = queue.poll( 10, TimeUnit.MILLISECONDS)) != null) {
                    item.read();
                    consumerLoopCount++;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "comsumer");
        return t;
    }

}
