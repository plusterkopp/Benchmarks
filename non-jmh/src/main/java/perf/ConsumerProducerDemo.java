package perf;

import de.icubic.mm.server.utils.ConcurrentLinkedBlockingQueue;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.DoubleHistogram;
import org.HdrHistogram.Histogram;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ConsumerProducerDemo {

	static final int    JobCount = 10_000;

	static Histogram histPoll = new ConcurrentHistogram( 4);
	static Histogram histInQueue = new ConcurrentHistogram( 4);
	static Histogram histInJob = new ConcurrentHistogram( 4);
	static Histogram histInTotal = new ConcurrentHistogram( 4);
	static Histogram histOfferBlock = new ConcurrentHistogram( 4);
	static Histogram histQueueSize = new ConcurrentHistogram( 4);

	private static class Item {
		final public long id;
		long    enterQueueNS;
		long    leaveQueueNS;
		long    finishJobNS;

		private Item(long id) {
			this.id = id;
		}

		void enterQueue() {
			enterQueueNS = System.nanoTime();
		}
		void leaveQueue() {
			leaveQueueNS = System.nanoTime();
		}
		void finishJob() {
			finishJobNS = System.nanoTime();
		}
	}

	private static class Producer {
		private final BlockingQueue<Item> queue;
		private Thread t = null;
		private double ratio = 1;

		public Producer(BlockingQueue<Item> q) {
			queue = q;
		}

		void run() {
			t = new Thread( () -> start() , "Producer");
			t.start();
			System.out.println( "started " + t.getName());
		}

		void setRatio(double r) {
			ratio = r;
		}

		void join() {
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public void start() {
			int i = 0;
			try {
				for ( i = 0; i < JobCount; i++) {
					Item item = new Item( i);
					item.enterQueue();
					queue.put( item);
					histOfferBlock.recordValue( System.nanoTime() - item.enterQueueNS);
//					Wait(1);
					BusyWaitUntilNanos( item.enterQueueNS + ( long) ( 1_000_000 * ratio));
				}
			} catch (InterruptedException e) {
				System.err.println( "interrupted at " + i);
			}
		}
	}

	private static class Consumer {
		private final BlockingQueue<Item> queue;
		private ExecutorService    executor = null;
		private Thread  t = null;
		private int poolSize = 1;

		public Consumer(BlockingQueue<Item> q) {
			queue = q;
		}

		void setPoolSize( int i) {
			poolSize = i;
		}

		void run() {
			t = new Thread( () -> start() , "Consumer");
			t.start();
			System.out.print( "started " + t.getName() + " poolSize: " + poolSize + " " + queue.getClass().getSimpleName());
			if ( queue.remainingCapacity() < Integer.MAX_VALUE) {
				System.out.print( " capacity: " + queue.remainingCapacity());
			}
			System.out.println();
		}

		void join() {
			try {
				t.join();
				executor.shutdown();
				executor.awaitTermination( 1, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public void start() {
			executor = Executors.newFixedThreadPool( poolSize);
			int i = 0;
			int pollTimeoutCount = 0;
			try {
				for ( i = 0;  i < JobCount;  i++) {
					long beforePollNS = System.nanoTime();
					final Item item = queue.poll( 10, TimeUnit.SECONDS);
					if ( item == null) {
						pollTimeoutCount++;
						continue;
					}
					int size = queue.size();
					executor.execute( () -> {
						item.leaveQueue();
//					Wait(1);
						BusyWaitUntilNanos( item.leaveQueueNS + 1_000_000);
						item.finishJob();
						histPoll.recordValue( item.leaveQueueNS - beforePollNS);
						histInQueue.recordValue( item.leaveQueueNS - item.enterQueueNS);
						histInJob.recordValue( item.finishJobNS - item.leaveQueueNS);
						histInTotal.recordValue( item.finishJobNS - item.enterQueueNS);
						histQueueSize.recordValue( size);
					});
				}
			} catch (InterruptedException e) {
				System.err.println( "interrupted at " + i);
			}
			if ( pollTimeoutCount > 0) {
				System.err.println( "poll timeout count: " + pollTimeoutCount);
			}
		}

	}

	public static void main(String[] args) {
		BlockingQueue queues[] = {
				new ConcurrentLinkedBlockingQueue<Item>(),
				new LinkedBlockingQueue<Item>(),
				new LinkedTransferQueue<Item>(),
				new ArrayBlockingQueue<Item>( 100),
				new ArrayBlockingQueue<Item>( 1),
				new SynchronousQueue<Item>(),
		};
		// Warmup
		System.out.println( "Warmup");
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, 1, 1);
		}

		System.gc();
		// Echt
		System.out.println( "Pool Size: " + 1);
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, 1, 1);
		}

		System.gc();
		System.out.println( "Pool Size: " + 2);
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, 2, 2.0/3.0);
		}
	}

	private static void runJoin( BlockingQueue<Item> q, int poolSize, double ratio) {
		Producer  producer = new Producer( q);
		Consumer  consumer = new Consumer( q);
		consumer.setPoolSize( poolSize);
		producer.setRatio( ratio);
		consumer.run();
		producer.run();
		consumer.join();
		producer.join();
		printHistogram( histOfferBlock,
				"offer blocked");
		printHistogram( histQueueSize,
				"queue size   ");
		printHistogram( histPoll,
				"poll wait    ");
		printHistogram( histInQueue,
				"ns in Queue  ");
		printHistogram( histInJob,
				"ns in Job    ");
		printHistogram( histInTotal,
				"ns total     ");
		Histogram[] histos = { histOfferBlock, histQueueSize, histPoll, histInQueue, histInJob, histInTotal};
		for (int i = 0; i < histos.length; i++) {
			histos[ i].reset();
		}
	}

	static void Wait(int ms) {
		try {
			Thread.sleep( ms);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	static void BusyWaitNanos(long nanos){
		long t = System.nanoTime() + nanos;
		BusyWaitUntilNanos( t);
	}

	static void BusyWaitUntilNanos( long nanos) {
		while ( System.nanoTime() < nanos);
	}

	private static void printHistogram( Histogram hist, String name) {
		double[] percentages = { 0, 5, 10, 25, 50, 75, 90, 95, 99, 99.9, 99.99, 100};
		StringBuilder sb = new StringBuilder();
		NumberFormat nf = DecimalFormat.getIntegerInstance();
		sb.append(name + " " + nf.format(hist.getTotalCount()) + " entries, avg = " + nf.format(hist.getMean()) + " percentiles: ");
		for (int i = 0; i < percentages.length;  i++) {
			double perc = percentages[ i];
			sb.append(perc + ": " + nf.format(hist.getValueAtPercentile(perc)) + "  ");
		}
		System.out.println(sb.toString());
	}

}
