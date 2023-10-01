package misc;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class TimerEarlyTest {

	private final long millisAtNanoZero;

	public TimerEarlyTest() {
		millisAtNanoZero = calibrate();
	}

	private long calibrate() {
		long millisNow = System.currentTimeMillis();
		long millisLast = millisNow;
		while ( millisNow == millisLast) {
			millisNow = System.currentTimeMillis();
		}
		// nanos bei neuer Milli-Zeit
		long nanos = System.nanoTime();
		long nanosInMillis = nanos / 1000_000L;
		long nanosRem = nanos % 1000_000L;
		System.out.println( "nanoRem: " + nanosRem);
		long millisAtNanoZero = millisNow - nanosInMillis;
		return millisAtNanoZero;
	}

	public static void main(String[] args) throws InterruptedException {

		TimerEarlyTest test = new TimerEarlyTest();
		test.run();
	}

	ScheduledExecutorService executor = Executors.newScheduledThreadPool( Runtime.getRuntime().availableProcessors());

	private void run() throws InterruptedException {
		NumberFormat nfI = NumberFormat.getIntegerInstance();
		nfI.setGroupingUsed( true);
		NumberFormat nfD = NumberFormat.getNumberInstance();
		nfD.setGroupingUsed( true);
		nfD.setMaximumFractionDigits( 2);

		long startNS = System.nanoTime();
		int maxJobs = 10_000;
		long scheduleAt = System.currentTimeMillis() + 10000;
		List<Job> jobs = new ArrayList<>( maxJobs);
		for ( int i = 0;  i < maxJobs / 2;  i++) {
			long rndOffset = 10 * (long) (Math.random() * 100);
			long scheduleAtNanos = asNanos( scheduleAt + rndOffset);
			Job job = new Job( i, scheduleAtNanos);
			long delayNS = scheduleAtNanos - System.nanoTime();
			executor.schedule( job, delayNS, TimeUnit.NANOSECONDS);
			jobs.add( job);
		}
		for ( int i = 0;  i < maxJobs / 2;  i++) {
			long rndOffset = 1 * (long) (Math.random() * 1000);
			long scheduleAtNanos = asNanos( scheduleAt + rndOffset);
			Job job = new Job( i, scheduleAtNanos);
			long delayNS = scheduleAtNanos - System.nanoTime();
			executor.schedule( job, delayNS, TimeUnit.NANOSECONDS);
			jobs.add( job);
		}

		long nanosToSchedule = System.nanoTime() - startNS;
		System.out.println( "scheduled " + jobs.size() + " jobs in " + nfI.format(nanosToSchedule) + " ns"
				+ ", " + nfD.format( 1.0 * nanosToSchedule / jobs.size()) + " ns/job"
		);

		executor.shutdown();
//		Comparator<Job> compLast = ( a, b) -> Long.compare( a.scheduledForNS, b.scheduledForNS);
//		long lastJobSchedule = jobs.stream().max( compLast).get().scheduledForNS;
//		long sleepMS = lastJobSchedule - System.currentTimeMillis();
//		Thread.sleep( Math.max( 0, sleepMS));
		System.out.println( "checking after " + nfI.format( System.nanoTime() - startNS));

//		boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
		while ( ! executor.awaitTermination(10, TimeUnit.SECONDS)) {
			long finishedCount = jobs.stream()
					.filter(j -> j.hasRun())
					.count();
			System.out.println( "finished " + nfI.format( finishedCount) + "/" + nfI.format( jobs.size()));
		}
		System.out.println( "finished after " + nfI.format(System.nanoTime() - startNS));

		// Auswertungen
		long hasNotRunCount = jobs.stream()
				.filter(j -> ! j.hasRun())
				.count();
		long earlyCount = jobs.stream()
				.filter(j -> j.wasEarlyBy() > 0)
				.count();
		System.out.println( "not run: " + hasNotRunCount);
		System.out.println( "early: " + earlyCount);

		Comparator<Job> comp = ( a, b) -> Long.compare( a.ranAtNS, b.ranAtNS);
		long first = jobs.stream().min( comp).get().ranAtNS;
		long last = jobs.stream().max( comp).get().ranAtNS;
		System.out.println( "run duration: " + nfI.format( last - first) + " ns");
		System.out.println( "min delay: " + nfI.format( first - asNanos( scheduleAt)));

		Comparator<Job> compLateBy = (a, b) -> Long.compare(a.wasLateBy(), b.wasLateBy());
		Job maxLateJob = jobs.stream().max( compLateBy).get();
		System.out.println("max late: " + maxLateJob.toString( nfI));


		if ( earlyCount > 0) {
			Comparator<Job> compEarlyBy = (a, b) -> Long.compare(a.wasEarlyBy(), b.wasEarlyBy());
			Job maxEarlyJob = jobs.stream().max(compEarlyBy).get();
			System.out.println("max early: " + maxEarlyJob.toString( nfI));
		}

	}

	private long asNanos(long millis) {
		long millisSinceNanoZero = millis - millisAtNanoZero;
		return 1000_000 * millisSinceNanoZero;
	}

	private static class Job implements Runnable {
		private final int index;
		private final long scheduledForNS;
		private volatile long ranAtNS = 0;

		private Job(int jobIndex, long scheduledForNanos) {
			index = jobIndex;
			scheduledForNS = scheduledForNanos;
		}

		@Override
		public void run() {
			ranAtNS = System.nanoTime();
		}

		public boolean hasRun() {
			return ranAtNS > 0;
		}

		public long wasEarlyBy() {
			if ( hasRun()) {
				return 0;
			}
			if ( scheduledForNS > ranAtNS) {
				return scheduledForNS - ranAtNS;
			}
			return 0;
		}

		public long wasLateBy() {
			if ( hasRun()) {
				return 0;
			}
			if ( scheduledForNS > ranAtNS) {
				return 0;
			}
			return ranAtNS - scheduledForNS;
		}

		public String toString( NumberFormat nf) {
			long ranAtL = ranAtNS;
			if ( ranAtL == 0) {
				return "" + index + " not run";
			}
			long earlyNS = scheduledForNS - ranAtL;
			if ( earlyNS > 0) {
				return "" + index + " early " + nf.format( earlyNS);
			}
			if ( earlyNS < 0) {
				return "" + index + " late " + nf.format( -earlyNS);
			}
			return "" + index + " on time";
		}
	}
}
