package perf;

import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ConsumerProducerDemo {

	static final int    JobCount = 10_000;

	static final LongAdder BusyWaitRounds = new LongAdder();
	static final LongAdder BusyWaitNanos = new LongAdder();

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
		private long startNS = 0;

		public Producer(BlockingQueue<Item> q) {
			queue = q;
		}

		void run() {
			t = new Thread( () -> start() , "Producer");
			t.start();
			System.out.print( "started " + t.getName());
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
			long durNS = System.nanoTime() - startNS;
			System.out.print( " rate: " + String.format( "%.1f", 1e9 * JobCount / durNS) + "/s");
		}

		public void start() {
			startNS = System.nanoTime();
			int i = 0;
			try {
				for ( i = 0; i < JobCount; i++) {
					Item item = new Item( i);
					item.enterQueue();
					queue.put( item);
//					Wait(1);
					// einmal nanoLatency abziehen
					long offerTookNS = System.nanoTime() - item.enterQueueNS;
					long nanoLat = BusyWaitUntilNanos(item.enterQueueNS, (long) (1_000_000 * ratio));
					if ( offerTookNS > nanoLat) {
						histOfferBlock.recordValue(offerTookNS - nanoLat);
					} else {
						histOfferBlock.recordValue( 0);
//						System.err.println( "offerTook: " + offerTookNS + " nanoLat: " + nanoLat);
					}
				}
			} catch (InterruptedException e) {
				System.err.println( "interrupted at " + i);
			}
		}
	}

	private static class Consumer {
		private final BlockingQueue<Item> queue;
		private Thread[]  threads = null;
		private int poolSize = 1;
		private long startNS = 0;
		private AtomicInteger jobsRemaining = new AtomicInteger( JobCount);

		public Consumer(BlockingQueue<Item> q) {
			queue = q;
		}

		void setPoolSize( int i) {
			poolSize = i;
		}

		void run() {
			startNS = System.nanoTime();
			threads = new Thread[ poolSize];
			for ( int i = 0;  i < poolSize;  i++) {
				threads[ i] = new Thread( () -> start() , "Consumer-" + i);
			}
			for ( int i = 0;  i < poolSize;  i++) {
				threads[ i].start();
			}
			System.out.print( "started Consumer(s) poolSize: " + poolSize + " " + queue.getClass().getSimpleName());
			if ( queue.remainingCapacity() < Integer.MAX_VALUE) {
				System.out.print( " capacity: " + queue.remainingCapacity());
			}
			System.out.println();
		}

		void join() {
			try {
				for( Thread t: threads) {
					t.join();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			long durNS = System.nanoTime() - startNS;
			System.out.println( ", consumer rate: " + String.format( "%.1f", 1e9 * JobCount / durNS) + "/s");
		}

		public void start() {
			int i = 0;
			int pollTimeoutCount = 0;
			try {
				for ( i = 0;  jobsRemaining.decrementAndGet() >= 0;  i++) {
					long beforePollNS = System.nanoTime();
					final Item item = queue.poll( 10, TimeUnit.SECONDS);
					if ( item == null) {
						pollTimeoutCount++;
						continue;
					}
					item.leaveQueue();
					int size = queue.size();
//					Wait(1);
					long nanoLat = BusyWaitUntilNanos( item.leaveQueueNS, 1_000_000);
					item.finishJob();
					long pollTookNS = item.leaveQueueNS - beforePollNS;
					if ( pollTookNS > nanoLat) {
						histPoll.recordValue(pollTookNS - nanoLat);
					} else {
						histPoll.recordValue( 0);
//						System.err.println( "poll took less than nanoLat: " + pollTookNS + " / " + nanoLat);
					}
					long inQueueNS = item.leaveQueueNS - item.enterQueueNS;
					if ( inQueueNS > nanoLat) {
						histInQueue.recordValue(inQueueNS - nanoLat);
					} else {
						histInQueue.recordValue( 0);
					}
					long inJobNS = item.finishJobNS - item.leaveQueueNS;
					histInJob.recordValue( ( inJobNS - nanoLat * 2) > 0 ? ( inJobNS - nanoLat * 2) : 0);
					long totalNS = item.finishJobNS - item.enterQueueNS;
					histInTotal.recordValue( ( totalNS - nanoLat * 3) > 0 ? ( totalNS - nanoLat * 3) : 0);
					histQueueSize.recordValue( size);
				}
			} catch (InterruptedException e) {
				System.err.println( Thread.currentThread().getName() + " interrupted at " + i);
			}
			if ( pollTimeoutCount > 0) {
				System.err.println( "poll timeout count: " + pollTimeoutCount);
			}
		}

	}

	public static void main(String[] args) {
		BusyWaitRounds.increment(); // damit ich keine Division durch 0 bekomme
		BlockingQueue queues[] = {
//				new ConcurrentLinkedBlockingQueue<Item>(),
//				new LinkedBlockingQueue<Item>(),
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
		System.out.println( "Hot");
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, 1, 1);
		}

		System.gc();
//		System.out.println( "Pool Size: " + 2);
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, 2, 51.0/100.0);
		}
		System.gc();
		int ncpus2 = Runtime.getRuntime().availableProcessors() / 2;
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, 4, 1.0/( ncpus2 - 0.5));
		}
		System.gc();
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, ncpus2 * 4, 1.0/( ncpus2 * 2));
		}
	}

	private static void runJoin( BlockingQueue<Item> q, int poolSize, double ratio) {
		Producer  producer = new Producer( q);
		Consumer  consumer = new Consumer( q);
		consumer.setPoolSize( poolSize);
		producer.setRatio( ratio);

		consumer.run();
		producer.run();
		producer.join();
		consumer.join();

		List<String>    reports = new ArrayList<>();
		reports.add( printHistogram( histQueueSize, "queue_size"));
		reports.add( printHistogram( histOfferBlock, "ns_for_offer"));
		reports.add( printHistogram( histPoll,"ns_for_poll"));
		reports.add( printHistogram( histInQueue,"ns_in_queue"));
		reports.add( printHistogram( histInJob, "ns_in_job"));
		reports.add( printHistogram( histInTotal, "ns_total"));
		reports = tabulate( reports);
		for ( String s: reports) {
			System.out.println( s);
		}
		Histogram[] histos = { histOfferBlock, histQueueSize, histPoll, histInQueue, histInJob, histInTotal};
		for (int i = 0; i < histos.length; i++) {
			histos[ i].reset();
		}
	}

	private static List<String> tabulate(List<String> reports) {
		List<String>    result = new ArrayList<>( reports);
		List<String[]> splits = new ArrayList<>();
		for ( String s: reports) {
			String[] split = s.split(" ");
			splits.add( split);
		}
		List<Integer> maxL = new ArrayList<>();
		for ( String[] split: splits) {
			for ( int i = 0;  i < split.length;  i++) {
				int l = split[ i].length();
				// ensure element at i
				while ( maxL.size() < i+1) {
					maxL.add( 0);
				}
				if ( maxL.get( i) < l) {
					maxL.set( i, l);
				}
			}
		}
		for ( int i = 0;  i < splits.size();  i++) {
			String[] split = splits.get( i);
			StringBuilder newSB = new StringBuilder();
			for ( int j = 0;  j < split.length;  j++) {
				int max = maxL.get( j);
				String s = split[j];
				int fillCount = max - s.length();
				if ( fillCount <= 0) {
					newSB.append( s).append( ' ');
					continue;
				}
				StringBuilder sb = new StringBuilder( s);
				for ( int f = 0;  f < fillCount;  f++) {
					if ( isNumeric( s)) {
						sb.insert( 0, ' ');
					} else {
						sb.append( ' ');
					}
				}
				newSB.append( sb).append( ' ');
			}
			String newString = newSB.toString().replace( '_', ' ');
			result.set( i, newString);
		}
		return result;
	}

	static final NumberFormat    nf = NumberFormat.getNumberInstance();
	private static boolean isNumeric( String s) {
		try {
			nf.parse( s);
			return true;
		} catch ( ParseException e) {
			return false;
		}
	}

	static void Wait(int ms) {
		try {
			Thread.sleep( ms);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	static long BusyWaitUntilNanos( long now, long nanos) {
		// wie lange dauerte bis jetzt System.nanoTime über alle Aufrufe hier durchschnittlich?
		long    avgNanoTimeLatency = BusyWaitNanos.longValue() / BusyWaitRounds.longValue();
		// ziehe eine halbe Latenz ab, damit wir nicht immer mit der Zeit drüber liegen
		long    nanos2 = now + nanos - avgNanoTimeLatency / 2;
		// Rundenzähler für den Adder später
		long    rounds = 0;
		while ( System.nanoTime() < nanos2) {
			rounds++;
		};
		BusyWaitRounds.add( rounds);    // nicht jede Runde adden, sondern erst am Ende einmal
		BusyWaitNanos.add( nanos);
		avgNanoTimeLatency = BusyWaitNanos.longValue() / BusyWaitRounds.longValue();
		return avgNanoTimeLatency;
	}

	private static String printHistogram( Histogram hist, String name) {
		double[] percentages = { 0, 5, 10, 25, 50, 75, 90, 95, 99, 99.9, 99.99, 100};
		StringBuilder sb = new StringBuilder();
		NumberFormat nf = DecimalFormat.getIntegerInstance();
		sb.append(name + " " + nf.format(hist.getTotalCount()) + " entries, avg = " + nf.format(hist.getMean()) + " percentiles: ");
		for (int i = 0; i < percentages.length;  i++) {
			double perc = percentages[ i];
			sb.append(perc + ": " + nf.format(hist.getValueAtPercentile(perc)) + "  ");
		}
		return sb.toString();
	}

}
