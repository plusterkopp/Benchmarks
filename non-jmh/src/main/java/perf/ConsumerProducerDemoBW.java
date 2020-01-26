package perf;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.SynchronizedHistogram;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ConsumerProducerDemoBW {

	public static final int JobDurationNS = 1000_000;
	public static final int RunTimeS = 10;
	static final int    JobCount = ( int) (1e9 * RunTimeS / JobDurationNS);

//	static final LongAdder BusyWaitRounds = new LongAdder();
//	static final LongAdder BusyWaitNanos = new LongAdder();

	static Histogram histPoll = new SynchronizedHistogram( 4);
	static Histogram histInQueue = new SynchronizedHistogram( 4);
	static Histogram histInJob = new SynchronizedHistogram( 4);
	static Histogram histInTotal = new SynchronizedHistogram( 4);
	static Histogram histOfferBlock = new Histogram( 4);
	static Histogram histQueueSize = new SynchronizedHistogram( 4);
	static Histogram histConsumerTPT = new SynchronizedHistogram( 4);

	static PrintWriter  writer = null;

	private static class NanoTimeStats {
		long	calls = 0;
		long	nanos = 0;

		public void reset() {
			calls = 0;
			nanos = 0;
		}
	}

	private static ThreadLocal<NanoTimeStats> NanoTimeStatsTL = new ThreadLocal<NanoTimeStats>() {
		@Override
		protected NanoTimeStats initialValue() {
			return new NanoTimeStats();
		}
	};

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
		private	long offersDelayedExcess = 0;
		private double nanoTimeLatencyD = 0;

		public Producer(BlockingQueue<Item> q) {
			queue = q;
		}

		void run() {
			t = new Thread( () -> produce() , "Producer");
			t.start();
			System.out.print( "started " + t.getName());
			if (writer != null) {
				writer.print( "started " + t.getName());
			}
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
			String finalOutS = " rate: "
				+ String.format("%.1f", 1e9 * JobCount / durNS) + "/s "
				+ String.format("%,d", offersBlockedTotal) + " ns offers blocked, "
				+ String.format("%,d", offersDelayedExcess) + " ns excess delay"
				+ " nanotime latency: "
				+ String.format("%.2f", nanoTimeLatencyD);
			System.out.print( finalOutS);
			if ( writer != null) {
				writer.print( finalOutS);
			}
		}

		public void produce() {
			long nanosToWait = (long) (JobDurationNS * ratio);
			startNS = System.nanoTime();
			int i = 0;
			long nanoLat = 1;
			NanoTimeStats stats = NanoTimeStatsTL.get();
			try {
				for ( i = 0; i < JobCount; i++) {
					Item item = new Item( i);
					item.enterQueue();
					queue.put( item);
//					Wait(1);
					// einmal nanoLatency abziehen
					long offerTookNS = System.nanoTime() - item.enterQueueNS;
					offersBlockedTotal += offerTookNS;
					if ( i == 10) {
						nanoLat = computeNanoTimeLatencyL( );
					}
					if ( offerTookNS < nanosToWait) {
						BusyWaitUntilNanos(item.enterQueueNS, nanosToWait, nanoLat, stats);
					} else {
						offersDelayedExcess += offerTookNS - nanosToWait;
					}
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
			nanoTimeLatencyD = computeNanoTimeLatencyD();
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
				threads[ i] = new Thread( () -> start() , "Consumer-" + i);
			}
			for ( int i = 0;  i < poolSize;  i++) {
				threads[ i].start();
			}
			StringBuilder sb = new StringBuilder( "started Consumer(s) poolSize: " + poolSize + " " + queue.getClass().getSimpleName());
			if ( queue.remainingCapacity() < Integer.MAX_VALUE) {
				sb.append( " capacity: " + queue.remainingCapacity());
			}
			System.out.println( sb);
			if ( writer != null) {
				writer.println( sb);
			}
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
			String logOutputS = ", consumer rate: "
				+ String.format("%,.1f", 1e9 * JobCount / durNS) + "/s "
				+ String.format("%,d", pollsBlockedTotalNS) + " ns poll delay";
			System.out.println( logOutputS);
			if ( writer != null) {
				writer.println( logOutputS);
			}
		}

		public void start() {
			// lokale Histogramme pro Thread, um den Concurrency Overhead zu vermeiden.
            Histogram histPollL = new Histogram( 4);
            Histogram histInQueueL = new Histogram( 4);
            Histogram histInJobL = new Histogram( 4);
            Histogram histInTotalL = new Histogram( 4);
            Histogram histQueueSizeL = new Histogram( 4);
            Histogram histConsumerTPTL = new Histogram( 4);

            long lastFinishedNS = 0;
			int i = 0;
			int pollTimeoutCount = 0;
			long nanoLat = 1;
			NanoTimeStats stats = NanoTimeStatsTL.get();
			try {
				long beforePollNS = System.nanoTime();
				for ( i = 0;  jobsRemaining.decrementAndGet() >= 0;  i++) {
					final Item item = queue.poll( 10, TimeUnit.SECONDS);
					if ( item == null) {    // sollte nicht auftreten
						pollTimeoutCount++;
						continue;
					}
					item.leaveQueue();
					int size = queue.size();
//					Wait(1);
					if ( i == 10) {
						nanoLat = computeNanoTimeLatencyL();
					}
					BusyWaitUntilNanos( item.leaveQueueNS, JobDurationNS, nanoLat, stats);
					item.finishJob();
					// Zeiterfassung
					long pollTookNS = item.leaveQueueNS - beforePollNS;
					if ( pollTookNS > nanoLat) {
						pollsBlockedTotalNS += pollTookNS - nanoLat;
						histPollL.recordValue(pollTookNS - nanoLat);
					} else {
						histPollL.recordValue( 0);
//						System.err.println( "poll took less than nanoLat: " + pollTookNS + " / " + nanoLat);
					}
					long inQueueNS = item.leaveQueueNS - item.enterQueueNS;
					if ( inQueueNS > nanoLat) {
						histInQueueL.recordValue(inQueueNS - nanoLat);
					} else {
						histInQueueL.recordValue( 0);
					}
					long inJobNS = item.finishJobNS - item.leaveQueueNS;
					histInJobL.recordValue( ( inJobNS - nanoLat * 2) > 0 ? ( inJobNS - nanoLat * 2) : 0);
					long totalNS = item.finishJobNS - item.enterQueueNS;
					histInTotalL.recordValue( ( totalNS - nanoLat * 3) > 0 ? ( totalNS - nanoLat * 3) : 0);
					histQueueSizeL.recordValue( size);
					if ( i > 0) {
						long	nsSinceLastJob = item.finishJobNS - lastFinishedNS;
						long	jobsPerSec = 1_000_000_000L / nsSinceLastJob;
						histConsumerTPTL.recordValue( jobsPerSec);
					}
					lastFinishedNS = item.finishJobNS;
					beforePollNS = lastFinishedNS;
				}
			} catch (InterruptedException e) {
				System.err.println( Thread.currentThread().getName() + " interrupted at " + i);
			}
			if ( pollTimeoutCount > 0) {
				System.err.println( "poll timeout count: " + pollTimeoutCount);
			}
			histConsumerTPT.add( histConsumerTPTL);
			histInJob.add( histInJobL);
			histInQueue.add( histInQueueL);
			histInTotal.add( histInTotalL);
			histPoll.add( histPollL);
			histQueueSize.add( histQueueSizeL);
		}

	}

	public static void main(String[] args) {
		// csv Ausgabe initialisieren
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
		OutputStream oStream = null;
		String outName = "ConsumerProducerDemo-" + df.format(new Date()) + ".out";
		try {
			oStream = new FileOutputStream(outName);
			writer = new PrintWriter( oStream);
		} catch (FileNotFoundException e) {
			System.err.print( "can not output to " + outName + ": ");
			e.printStackTrace( System.err);
		}

//		BusyWaitRounds.increment(); // damit ich keine Division durch 0 bekomme
		// Werte für NanoLatency sammeln
		NanoTimeStats stats = NanoTimeStatsTL.get();
		BusyWaitUntilNanos( System.nanoTime(), 1_000_000, 1, stats);
		BlockingQueue queues[] = {
//				new ConcurrentLinkedBlockingQueue<Item>(),
//				new LinkedBlockingQueue<Item>(),
				new LinkedTransferQueue<Item>(),
				new ArrayBlockingQueue<Item>( 100),
				new ArrayBlockingQueue<Item>( 1),
				new SynchronousQueue<Item>(),
		};
		int ncpus2 = Runtime.getRuntime().availableProcessors() / 2;
		// Warmup
		System.out.println( "Warmup");
		if (writer != null) {
			writer.println( "Warmup");
		}
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, ncpus2, 1.0/( ncpus2 * 0.9));
		}

		// Echt
		System.gc();
		stats.reset();
		BusyWaitUntilNanos( System.nanoTime(), 1_000_000, 1, stats);
		NanoTimeStats	nanoTimeStats = NanoTimeStatsTL.get();
		String hotOutputS = "\nHot running "
			+ String.format("%,d Jobs %,d", JobCount, JobDurationNS) + " ns each, nanotime latency: "
			+ String.format("%.2f", 1.0 * nanoTimeStats.nanos / nanoTimeStats.calls);
		System.out.println(hotOutputS);
		if (writer != null) {
			writer.println( hotOutputS);
		}

		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, 1, 1);
		}

		System.gc();
//		System.out.println( "Pool Size: " + 2);
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, 2, 51.0/100.0);
		}
		System.gc();
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, ncpus2, 1.0/( ncpus2 * 0.9));
		}
		System.gc();
		for ( BlockingQueue<Item> q : queues) {
			runJoin( q, ncpus2 * 4, 1.0/( ncpus2 * 4 * 0.9));
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
		reports.add( printHistogram( histConsumerTPT, "jobs/s"));
		reports = tabulate( reports);
		for ( String s: reports) {
			System.out.println( s);
		}
		Histogram[] histos = { histOfferBlock, histQueueSize, histPoll, histInQueue, histInJob, histInTotal, histConsumerTPT};
		for (int i = 0; i < histos.length; i++) {
			histos[ i].reset();
		}
		System.out.println();
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

	static long computeNanoTimeLatencyL() {
		NanoTimeStats stats = NanoTimeStatsTL.get();
		if ( stats.calls == 0) {
			return 0;
		}
		return stats.nanos / stats.calls;
	}

	static double computeNanoTimeLatencyD() {
		NanoTimeStats stats = NanoTimeStatsTL.get();
		if ( stats.calls == 0) {
			return 0;
		}
		return 1.0 * stats.nanos / stats.calls;
//		return 1.0 * BusyWaitNanos.longValue() / BusyWaitRounds.longValue();
	}

	static void BusyWaitUntilNanos( long now, long nanos, long avgNanoTimeLatency, NanoTimeStats statsP) {
		// ziehe eine halbe Latenz ab, damit wir nicht immer mit der Zeit drüber liegen
		long    nanos2 = now + nanos - avgNanoTimeLatency / 2;
		// Rundenzähler für den Adder später
		long    rounds = 0;
		while ( System.nanoTime() < nanos2) {
			rounds++;
		};
		NanoTimeStats stats = statsP != null ? statsP : NanoTimeStatsTL.get();
		stats.calls += rounds;    // nicht jede Runde adden, sondern erst am Ende einmal
		stats.nanos += nanos;
	}

	private static String printHistogram( Histogram hist, String name) {
		double[] percentages = { 0, 5, 10, 25, 50, 75, 90, 95, 99, 99.9, 99.99, 100};
		StringBuilder sb = new StringBuilder();
		NumberFormat nf = DecimalFormat.getIntegerInstance();
		String totalCountS = nf.format(hist.getTotalCount());
		String avgS = nf.format(hist.getMean());
		sb.append(name + " " + totalCountS + " entries, avg = " + avgS);
		String avgPctS = String.format("%.1f", hist.getPercentileAtOrBelowValue((long) hist.getMean())) + "%";
		sb.append(" " + avgPctS);
		sb.append(" percentiles: ");
		// writer
		if (writer != null) {
			writer.print( name.replace( '_', ' ') + "; " + "avg; avg pct; ");
			for (int i = 0; i < percentages.length;  i++) {
				double perc = percentages[ i];
				writer.print( perc +"; ");
			}
			writer.println();
			writer.print( totalCountS + "; " + avgS + "; " + avgPctS + "; ");
			for (int i = 0; i < percentages.length;  i++) {
				double perc = percentages[ i];
				writer.print( nf.format(hist.getValueAtPercentile(perc)) +"; ");
			}
			writer.println();
		}
		for (int i = 0; i < percentages.length;  i++) {
			double perc = percentages[ i];
			sb.append(perc + ": " + nf.format(hist.getValueAtPercentile(perc)) + "  ");
		}
		return sb.toString();
	}

}
