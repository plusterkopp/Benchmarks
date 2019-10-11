package perf;

import de.icubic.mm.server.utils.ConcurrentLinkedBlockingQueue;
import org.HdrHistogram.DoubleHistogram;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ConsumerProducerDemo {

	static final int    JobCount = 10_000;

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

		public Producer(BlockingQueue<Item> q) {
			queue = q;
		}

		void run() {
			t = new Thread( () -> start() , "Producer");
			t.start();
			System.out.println( "started " + t.getName());
		}

		void join() {
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public void start() {
			DoubleHistogram histOfferBlock = new DoubleHistogram( 4);
			long	blockA[] = new long[ JobCount];
			int i = 0;
			try {
				for ( i = 0; i < JobCount; i++) {
					Item item = new Item( i);
					item.enterQueue();
					queue.put( item);
					blockA[ i] = System.nanoTime() - item.enterQueueNS;
//					Wait(1);
					BusyWaitUntilNanos( item.enterQueueNS + 1_000_000);
				}
			} catch (InterruptedException e) {
				System.err.println( "interrupted at " + i);
			}
			for ( i = 0; i < blockA.length; i++) {
				histOfferBlock.recordValue( blockA[ i]);
			}
			printHistogram( histOfferBlock,
					"offer blocked");
		}
	}

	private static class Consumer {
		private final BlockingQueue<Item> queue;
		private Thread t = null;

		public Consumer(BlockingQueue<Item> q) {
			queue = q;
		}

		void run() {
			t = new Thread( () -> start() , "Consumer");
			t.start();
			System.out.println( "started " + t.getName());
		}

		void join() {
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public void start() {
			List<Item>  items = new ArrayList<Item>( JobCount);
			long	pollA[] = new long[ JobCount];
			Item item;
			int i = 0;
			int pollTimeoutCount = 0;
			try {
				for ( i = 0;  i < JobCount;  i++) {
					long beforePollNS = System.nanoTime();
					item = queue.poll( 10, TimeUnit.SECONDS);
					if ( item == null) {
						pollTimeoutCount++;
						continue;
					}
					item.leaveQueue();
					pollA[ i] = item.leaveQueueNS - beforePollNS;
//					Wait(1);
					BusyWaitUntilNanos( beforePollNS + 1_000_000);
					item.finishJob();
					items.add(item);
				}
			} catch (InterruptedException e) {
				System.err.println( "interrupted at " + i);
			}
			if ( pollTimeoutCount > 0) {
				System.err.println( "poll timeout count: " + pollTimeoutCount);
			}
			DoubleHistogram histPoll = new DoubleHistogram( 4);
			DoubleHistogram histInQueue = new DoubleHistogram( 4);
			DoubleHistogram histInJob = new DoubleHistogram( 4);
			DoubleHistogram histInTotal = new DoubleHistogram( 4);
			for ( Item it : items) {
				long inQueue = it.leaveQueueNS - it.enterQueueNS;
				histInQueue.recordValue( inQueue);
				long inJob = it.finishJobNS - it.leaveQueueNS;
				histInJob.recordValue( inJob);
				long total = it.finishJobNS - it.enterQueueNS;
				histInTotal.recordValue( total);
			}
			for ( i = 0; i < pollA.length; i++) {
				histPoll.recordValue( pollA[ i]);
			}
			printHistogram( histPoll,
					"poll wait    ");
			printHistogram( histInQueue,
					"in Queue     ");
			printHistogram( histInJob,
					"in Job       ");
			printHistogram( histInTotal,
					"total        ");
		}

	}

	public static void main(String[] args) {
//		BlockingQueue<Item> q = new ConcurrentLinkedBlockingQueue<>();
//		BlockingQueue<Item> q = new LinkedBlockingQueue<>();
//		BlockingQueue<Item> q = new LinkedTransferQueue<>();
//		BlockingQueue<Item> q = new ArrayBlockingQueue<>( 100);
		BlockingQueue<Item> q = new SynchronousQueue<>();
		Producer  producer = new Producer( q);
		Consumer  consumer = new Consumer( q);
		// Warmup
		consumer.run();
		producer.run();
		consumer.join();
		producer.join();

		System.gc();
		// Echt
		consumer.run();
		producer.run();
		consumer.join();
		producer.join();
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

		private static void printHistogram( DoubleHistogram hist, String name) {
		StringBuilder sb = new StringBuilder();
		NumberFormat nf = DecimalFormat.getIntegerInstance();
		sb.append( name + " " + nf.format( hist.getTotalCount()) + " entries, avg = " + nf.format( hist.getMean()) + " percentiles: ");
		for ( int perc = 0;  perc <= 100;  perc += 10) {
			sb.append( perc + ": " + nf.format( hist.getValueAtPercentile( perc)) + "  ");
		}
		System.out.println( sb.toString());
	}

}
