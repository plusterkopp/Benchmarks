package de.icubic.mm.communication.util;

import org.HdrHistogram.Histogram;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.Thread.UncaughtExceptionHandler;
import java.text.NumberFormat;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TestNonEarlyScheduledTPE {

	static UncaughtExceptionHandler ueh;
	static NumberFormat nfI = NumberFormat.getIntegerInstance();
	public static double[] PercentilesSymmetric = { 0.01, 0.1, 1, 10, 50, 90, 99, 99.9, 99.99};
	public static double[] PercentilesSimple = generateSteps( 0, 100, 10);

	static String tfName;

	private static double[] generateSteps( double start, double end, int steps) {
		double result[] = new double[ steps +1];
		double size = ( end - start) / steps;
		for ( int i = 0;  i <= steps;  i++) {
			result[ i] = start + i * size;
		}
		return result;
	}

	@BeforeClass
	public static void setup() {
		nfI.setGroupingUsed( true);
		ueh = new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException( Thread t, Throwable e) {
				System.err.println( "Error in Thread [" + t.getName() + "]");
				e.printStackTrace( System.err);
		}	};
		try {
			Thread.sleep( 1);
		} catch ( InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	long	startNanos = System.nanoTime();
	final ConcurrentLinkedQueue<Histogram> threadHistoList = new ConcurrentLinkedQueue<>();
	final ConcurrentLinkedQueue<Histogram> threadHistoSchedDiffList = new ConcurrentLinkedQueue<>();
	final ConcurrentLinkedQueue<Histogram> threadHistoRunAtDiffList = new ConcurrentLinkedQueue<>();

	long histOffset = 10_000_000L;
	ThreadLocal<Histogram> histogramTL = ThreadLocal.withInitial( () -> new Histogram( 5));
	ThreadLocal<Histogram> histogramSchedDiffTL = ThreadLocal.withInitial( () -> new Histogram( 5));
	ThreadLocal<Histogram> histogramRunAtDiffTL = ThreadLocal.withInitial( () -> new Histogram( 5));

	ThreadFactory buildThreadFactory( String tfName) {
		ThreadFactory tf = new NamedCountedThreadFactory(
			tfName, IQuoteThread.getDefaultUncaughtExceptionHandler())
		{
			@Override
			public Thread newThread( Runnable r) {
				Runnable wrapper = () -> {
					threadHistoList.add( histogramTL.get());
					threadHistoSchedDiffList.add( histogramSchedDiffTL.get());
					threadHistoRunAtDiffList.add( histogramRunAtDiffTL.get());

					r.run();
				};
				return super.newThread( wrapper);
			}
		};
		return tf;
	}

	long getNow() {
		return System.nanoTime() - startNanos;
	}

	@Test
	public void testScheduleAtNanos1() {
		ThreadFactory tf = buildThreadFactory( "1");
		NonEarlyScheduledTPE nest = new NonEarlyScheduledTPE( 5, tf);
		long createdAt = System.nanoTime();
		long runAt = createdAt + 1000_000;

		Runnable job = () -> {
			long now = System.nanoTime();
			long lateNS = now - runAt;
			System.out.println( Thread.currentThread().getName() + " running " + nfI.format( lateNS) + " late");
		};
		nest.scheduleAtNanos( job, runAt);
	}

	private void scheduleAfterNS( NonEarlyScheduledTPE nest, long id, long delay) {
		long createdAt = System.nanoTime();
		long runAt = createdAt + delay;
		Runnable job = () -> {
			long now = System.nanoTime();
			long lateNS = now - runAt;
			Histogram histo = histogramTL.get();
			histo.recordValue( lateNS + histOffset);
		};
		nest.scheduleAtNanos( job, runAt);
	}

	@Test
	public void testScheduleAtNanos2() {
		ThreadFactory tf = buildThreadFactory( "O");
		int nThreads = 10;
		NonEarlyScheduledTPE nest = new NonEarlyScheduledTPE( nThreads, tf);
		Random rnd = new Random();
		int maxJobs = 100_000;
		int estimatedNSPerJob = 2000;
		int maxDelayNS = ( maxJobs * estimatedNSPerJob) / nThreads;
		String title = "One shot " + nfI.format( maxJobs) + " jobs, "
			+ "maxDelay " + nfI.format( maxDelayNS)
			+ " in " + nThreads + "T";

		long startSched = System.nanoTime();
		for ( int i = 0;  i < maxJobs;  i++) {
			scheduleAfterNS( nest, i, rnd.nextInt( maxDelayNS));
		}
		System.out.println( title + " scheduled after " + nfI.format( System.nanoTime() - startSched) + " ns");

		await( nest);
		System.out.println( title + " finished after " + nfI.format( System.nanoTime() - startSched) + " ns");

		Histogram combinedHisto = new Histogram( 5);
		threadHistoList.forEach( h -> {
			combinedHisto.add( h);
			h.reset();
		});
		displayHistogram( title, combinedHisto);
		System.gc();
	}

	private void displayHistogram( String title, Histogram combinedHisto) {
		StringBuilder sb = new StringBuilder( title + ": ");
		sb.append( "collected ")
		.append( combinedHisto.getTotalCount())
		.append( " between ").append( nfI.format( combinedHisto.getMinValue() - histOffset))
		.append( " and ").append( nfI.format( combinedHisto.getMaxValue() - histOffset));
//		sb.append( "\nLatencies simple: ");
//		appendHistogramValues0( combinedHisto, PercentilesSimple, sb);
		sb.append( "\nLatencies symmetric: ");
		appendHistogramValues0( combinedHisto, PercentilesSymmetric, sb);
		System.out.println( sb);
		threadHistoList.clear();
	}

	private void displayCombined( String title, ConcurrentLinkedQueue<Histogram> histoList) {
		Histogram combinedHisto = new Histogram(5);
		histoList.forEach( h -> {
			combinedHisto.add( h);
			h.reset();
		});
		displayHistogram( title, combinedHisto);
	}

	public void appendHistogramValues0( Histogram histogram, double[] percentiles, StringBuilder sb) {
		double lastValue = 0;
		for ( int i = percentiles.length - 1;  i >= 0;  i--) {
			double p = percentiles[ i];
			double	valueAtP = histogram.getValueAtPercentile( p);
			if ( i == percentiles.length - 1 || valueAtP < lastValue) {
				lastValue = valueAtP;
				sb.append( " ")
				.append( p)
				.append( "% < ")
				.append( nfI.format( valueAtP - histOffset))
				.append( "  ");
	}	}	}

	private void await( ThreadPoolExecutor tpe) {
		try {
			while ( ! tpe.getQueue().isEmpty()) {
				Thread.sleep( 10);
			}
			tpe.shutdown();
			tpe.awaitTermination( 10, TimeUnit.SECONDS);
		} catch ( InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void testPeriodic( ScheduledThreadPoolExecutor es, String suffix) {
		int maxJobs = 100000;
		CountDownLatch	countdown = new CountDownLatch( maxJobs);
		AtomicInteger	countAI = new AtomicInteger();
		long startNSA[] = { System.nanoTime()};
		TimeUnit tu = TimeUnit.MICROSECONDS;
		long first = tu.convert( 10, TimeUnit.MILLISECONDS);
		int times = 100;
		final String title = suffix + " " + maxJobs + " periodic " + suffix + " " + times + " " + tu;

		NonEarlyScheduledTPE.ScheduledRunnable job = new NonEarlyScheduledTPE.ScheduledRunnable() {
			@Override
			public void run() {
				try {
				countdown.countDown();
				final long lateNS;
				int id = countAI.getAndIncrement();
				long runAt = startNSA[ 0] + tu.toNanos( id * times + first);
				long now = System.nanoTime();
				final long lastRunAt = getLastRunAt();
				if ( lastRunAt > 0) {	// only when set (running in NonEarlyTPE)
					final long scheduledFor = getScheduledFor();
					lateNS = lastRunAt - scheduledFor;
					final long schedDiff = runAt - scheduledFor;
//					if ( schedDiff >= 0) {
						histogramSchedDiffTL.get().recordValue( histOffset + schedDiff);
//					}
					final long nowDiff = now - lastRunAt;
//					if ( nowDiff >= 0) {
						histogramRunAtDiffTL.get().recordValue( histOffset + nowDiff);
//					}
				} else {
					lateNS = now - runAt;
				}
				Histogram histo = histogramTL.get();
				if ( lateNS >= 0) {
					histo.recordValue( histOffset + lateNS);
				} else {
					histo.recordValue( histOffset + lateNS);
				}
				} catch ( Exception t) {
					t.printStackTrace( System.err);
				}
			}
		};
		long beforeSchedule = System.nanoTime();
		ScheduledFuture<?> future = es.scheduleAtFixedRate( job, first, times, tu);
		startNSA[ 0] = System.nanoTime();
		System.out.println( title + " scheduled after " + nfI.format( System.nanoTime() - beforeSchedule) + " ns");
		try {
			countdown.await();
			es.shutdown();
			System.out.println( title + " shutdown after " + nfI.format( System.nanoTime() - beforeSchedule) + " ns");
			es.awaitTermination( 1, TimeUnit.MINUTES);
			System.out.println( title + " finished after " + nfI.format( System.nanoTime() - beforeSchedule) + " ns");

			displayCombined( title, threadHistoList);
			displayCombined( title + " runAt", threadHistoRunAtDiffList);
			displayCombined( title + " sched", threadHistoSchedDiffList);
		} catch ( InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testPeriodicSTPE() {
		ThreadFactory tf = buildThreadFactory( "S");
		ScheduledThreadPoolExecutor es = new ScheduledThreadPoolExecutor( 10, tf);
		testPeriodic( es, "S");
		System.gc();
	}

	@Test
	public void testPeriodicNETPE() {
		long earlyBefore = NonEarlyScheduledTPE.EarlyCounter.longValue();

		ThreadFactory tf = buildThreadFactory( "N");
		ScheduledThreadPoolExecutor es = new NonEarlyScheduledTPE( 10, tf);
		testPeriodic( es, "N");
		System.gc();

		long early = NonEarlyScheduledTPE.EarlyCounter.longValue() - earlyBefore;
		if ( early > 0) {
			System.out.println( nfI.format( early) + " early jobs rescheduled");
		}
		System.out.println( "stats"
			+ " latency: " + nfI.format( NonEarlyScheduledTPE.LatencyCumulated.longValue())
			+ " early: " + nfI.format( NonEarlyScheduledTPE.EarlyCumulated.longValue())
			+ " both: " + nfI.format( NonEarlyScheduledTPE.BothCumulated.longValue())
		);
	}

}
