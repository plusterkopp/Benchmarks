package perf;

import org.HdrHistogram.*;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ConsumerProducerDemoSleep {

	public static final int JobDurationNS = 1000_000;
	public static final int RunTimeS = 10;
	static final int    JobCount = ( int) (1_000_000_000L * RunTimeS / JobDurationNS);

	static Histogram histPoll = new SynchronizedHistogram( 4);
	static Histogram histConsumeJobToJob = new SynchronizedHistogram( 4);
	static Histogram histInQueue = new SynchronizedHistogram( 4);
	static Histogram histInJob = new SynchronizedHistogram( 4);
	static Histogram histInTotal = new SynchronizedHistogram( 4);
	static Histogram histQueueSizeOnPoll = new SynchronizedHistogram( 4);
	static Histogram histConsBatchSize = new SynchronizedHistogram( 4);
	static Histogram histOfferBlock = new Histogram( 4);
	static Histogram histOfferJobToJob = new Histogram( 4);
	static Histogram histProdBatchSize = new Histogram( 4);
//	static Histogram histConsumerTPT = new SynchronizedHistogram( 4);

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
		private	long offersBlockedTotal = 0;
		private	long offersDelayedTotal = 0;

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
			System.out.print( " rate: "
					+ String.format( "%.1f", 1e9 * JobCount / durNS) + "/s "
					+ String.format( "%,d", offersBlockedTotal) + " ns offers blocked, "
					+ String.format( "%,d", offersDelayedTotal) + " ns total delay"
			);
		}

		public void start() {
			startNS = System.nanoTime();
			int i = 0;
			long    afterPutNS;
			long    lastEnterNS = 0;
			try {
				while ( i < JobCount) {
					long    startLoopNS = System.nanoTime();
					long    sinceStartNS = startLoopNS - startNS;
					// how many jobs should we have produced by now?
					long    targetJobCount = (long) Math.min( JobCount, sinceStartNS / ( ratio * JobDurationNS));
					// init afterPutNS, too for test, to limit while loop duration in case of long put delays
					afterPutNS = startLoopNS;
					int count = 0;
					while ( i < targetJobCount && afterPutNS < startLoopNS + 1_000_000) {
						Item item = new Item( i);
						item.enterQueue();
						queue.put( item);
						afterPutNS = System.nanoTime();
						long    putTookNS = afterPutNS - item.enterQueueNS;
						offersBlockedTotal += putTookNS;
						histOfferBlock.recordValue(putTookNS);
						if ( i > 0) {
							histOfferJobToJob.recordValue( item.enterQueueNS - lastEnterNS);
						}
						lastEnterNS = item.enterQueueNS;
						i++;
						count++;
					}
					histProdBatchSize.recordValue( count);
					Wait( 1);
				}
			} catch (InterruptedException e) {
				System.err.println( "interrupted at " + i);
			}
//			System.out.print( " " + Thread.currentThread().getName() + " finished");
		}
	}

	private static class Consumer {
		private final BlockingQueue<Item> queue;
		private Thread[]  threads = null;
		private int poolSize = 1;
		private long startNS = 0;
		private AtomicInteger jobsRemaining = new AtomicInteger( JobCount);
		private long pollsBlockedTotalNS = 0;

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
				threads[ i] = new Thread( () -> {
					try {
						start();
					} catch ( Exception e) {
						System.err.println( "exception in " + Thread.currentThread());
						e.printStackTrace( System.err);
					}
				}, "Consumer-" + i);
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
			System.out.println( ", consumer rate: "
					+ String.format( "%,.1f", 1e9 * JobCount / durNS) + "/s "
					+ String.format( "%,d", pollsBlockedTotalNS) + " ns total delay"
			);
		}

		public void start() {
			// lokale Histogramme pro Thread, um den Concurrency Overhead zu vermeiden.
            Histogram histPollL = new Histogram( 4);
            Histogram histInQueueL = new Histogram( 4);
            Histogram histInJobL = new Histogram( 4);
            Histogram histInTotalL = new Histogram( 4);
			Histogram histQueueSizeL = new Histogram( 4);
			Histogram histConsumeJobToJobL = new Histogram( 4);
			Histogram histConsBatchSizeL = new Histogram( 4);
//            Histogram histConsumerTPTL = new Histogram( 4);

            Long lastFinishedNS = null;
			long startNS = System.nanoTime();
			try {
				while ( jobsRemaining.get() > 0) {
					// einen Job nehmen und warten
					long beforePollNS = System.nanoTime();
					Item item = queue.poll( 10 * JobDurationNS, TimeUnit.NANOSECONDS);
					if ( item == null) {
						continue;
					}
					int remaining = jobsRemaining.decrementAndGet();
					item.leaveQueue();
					Wait(1);
					item.finishJob();
					record(item, beforePollNS, queue.size(), lastFinishedNS, histPollL, histInQueueL, histInJobL, histInTotalL, histQueueSizeL, histConsumeJobToJobL);
					lastFinishedNS = item.finishJobNS;
					// measure time: how far can we go until we must wait() again
					long startLoopNS = System.nanoTime();
					beforePollNS = startLoopNS;
					long sinceStartNS = startLoopNS - startNS;
					long catchUp = JobCount - sinceStartNS / JobDurationNS;
					long currentJobCount = Math.max(0, catchUp);
//					remaining = jobsRemaining.get();
//					System.out.println( Thread.currentThread().getName()
//							+ " catching up " + ( remaining - currentJobCount) + "/" + ( remaining - catchUp)
//							+ " jobs (" + remaining + "-" + currentJobCount + "), ");
					int count = 1;  // how many did we take int this round, including the first (which was actually before the wait())
					while (jobsRemaining.get() > currentJobCount) {
						item = queue.poll( 10 * JobDurationNS, TimeUnit.NANOSECONDS);
						if ( item == null) {
							continue;
						}
						jobsRemaining.decrementAndGet();
						item.leaveQueue();
						item.finishJob();
						record(item, beforePollNS, queue.size(), lastFinishedNS, histPollL, histInQueueL, histInJobL, histInTotalL, histQueueSizeL, histConsumeJobToJobL);
						lastFinishedNS = item.finishJobNS;
						beforePollNS = lastFinishedNS;
						count++;
					}
					histConsBatchSizeL.recordValue( count);
				}
			} catch (InterruptedException e) {
				System.err.println( Thread.currentThread().getName() + " interrupted at " + jobsRemaining.get() + " remaining");
			}
//			histConsumerTPT.add( histConsumerTPTL);
			histInJob.add( histInJobL);
			histInQueue.add( histInQueueL);
			histInTotal.add( histInTotalL);
			histPoll.add( histPollL);
			histQueueSizeOnPoll.add( histQueueSizeL);
			histConsumeJobToJob.add( histConsumeJobToJobL);
			histConsBatchSize.add( histConsBatchSizeL);
//			System.out.print( " " + Thread.currentThread().getName() + " finished with " + histInJobL.getTotalCount() + " jobs");
		}

		private void record(Item item, long beforePollNS, long size, Long lastFinishedNS,
		                    AbstractHistogram histPollL, AbstractHistogram histInQueueL,
		                    AbstractHistogram histInJobL, AbstractHistogram histInTotalL,
		                    AbstractHistogram histQueueSizeL, Histogram histConsumeJobToJobL) {
			// Zeiterfassung
			long pollTookNS = item.leaveQueueNS - beforePollNS;
			pollsBlockedTotalNS += pollTookNS;
			histPollL.recordValue( pollTookNS);
			long inQueueNS = item.leaveQueueNS - item.enterQueueNS;
			histInQueueL.recordValue( inQueueNS);
			long inJobNS = item.finishJobNS - item.leaveQueueNS;
			histInJobL.recordValue( inJobNS);
			long totalNS = item.finishJobNS - item.enterQueueNS;
			histInTotalL.recordValue( totalNS);
			histQueueSizeL.recordValue( size);
			if ( lastFinishedNS != null) {
				long sinceLastNS = item.finishJobNS - lastFinishedNS;
				histConsumeJobToJobL.recordValue( sinceLastNS);
			}
		}

	}

	public static void main(String[] args) {
		BlockingQueue queues[] = {
//				new ConcurrentLinkedBlockingQueue<Item>(),
//				new LinkedBlockingQueue<Item>(),
				new ArrayBlockingQueue<Item>( 100),
				new LinkedTransferQueue<Item>(),
				new ArrayBlockingQueue<Item>( 1),
				new SynchronousQueue<Item>(),
		};
		int ncpus2 = Runtime.getRuntime().availableProcessors() / 2;
		// Warmup
		System.out.println( "Warmup");
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, ncpus2, 1.0/( ncpus2 * 0.9));
		}

		// Echt
		System.out.println( "\nHot running " + JobCount + " Jobs, "
				+ JobDurationNS + " ns each");

		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, 1, 1);
		}

//		System.out.println( "Pool Size: " + 2);
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, 2, 52.0/100.0);
		}
		int factor = 1;
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, ncpus2 * factor, 1.0/( ncpus2 * factor * 0.9));
		}
		factor = 4;
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, ncpus2 * factor, 1.0/( ncpus2 * factor * 0.9));
		}
	}

	private static void runJoin( BlockingQueue<Item> q, int poolSize, double ratio) {
		System.gc();
		Producer  producer = new Producer( q);
		Consumer  consumer = new Consumer( q);
		consumer.setPoolSize( poolSize);
		producer.setRatio( ratio);

		consumer.run();
		producer.run();
		producer.join();
		consumer.join();

		List<String>    reports = new ArrayList<>();
		reports.add( printHistogram( histQueueSizeOnPoll, "queue_size"));
		reports.add( printHistogram( histOfferBlock, "ns_for_offer"));
		reports.add( printHistogram( histOfferJobToJob, "ns_offerToOffer"));
		reports.add( printHistogram( histProdBatchSize, "prod_batch_size"));
		reports.add( printHistogram( histPoll,"ns_for_poll"));
		reports.add( printHistogram( histInQueue,"ns_in_queue"));
		reports.add( printHistogram( histInJob, "ns_in_job"));
		reports.add( printHistogram( histInTotal, "ns_total"));
		reports.add( printHistogram( histConsumeJobToJob, "ns_jobTojob"));
		reports.add( printHistogram( histConsBatchSize, "cons_batch_size"));
//		reports.add( printHistogram( histConsumerTPT, "jobs/s"));
		reports = tabulate( reports);
		for ( String s: reports) {
			System.out.println( s);
		}
		Histogram[] histos = {histQueueSizeOnPoll, histOfferBlock, histOfferJobToJob, histProdBatchSize, histPoll, histInQueue, histInJob, histInTotal, histConsumeJobToJob, histConsBatchSize};
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

	private static String printHistogram( Histogram hist, String name) {
		double[] percentages = { 0, 5, 10, 25, 50, 75, 90, 95, 99, 99.9, 99.99, 100};
		StringBuilder sb = new StringBuilder();
		NumberFormat nfi = DecimalFormat.getIntegerInstance();
		sb.append( name + " " + nfi.format(hist.getTotalCount()) + " entries, avg = ");
		double mean = hist.getMean();
		if ( mean < 100) {
			sb.append( String.format( "%.2f", mean));
		} else {
			sb.append( nfi.format( mean));
		}
		sb.append( " percentiles: ");
		for (int i = 0; i < percentages.length;  i++) {
			double perc = percentages[ i];
			sb.append(perc + ": " + nfi.format(hist.getValueAtPercentile(perc)) + "  ");
		}
		return sb.toString();
	}

}
