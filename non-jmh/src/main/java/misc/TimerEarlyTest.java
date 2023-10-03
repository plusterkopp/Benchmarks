package misc;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class TimerEarlyTest {

	private static class Job implements Runnable {
		private final int index;
		private final int execSize;
		private final long scheduledForNS;
		private volatile long ranAtNS = 0;

		private Job( int executorSize, int jobIndex, long scheduledForNanos) {
			index = jobIndex;
			scheduledForNS = scheduledForNanos;
			execSize = executorSize;
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
				return "" + execSize + "/" + index + " not run";
			}
			long earlyNS = scheduledForNS - ranAtL;
			if ( earlyNS > 0) {
				return "" + execSize + "/" +  index + " early " + nf.format( earlyNS);
			}
			if ( earlyNS < 0) {
				return "" + execSize + "/" + index + " late " + nf.format( -earlyNS);
			}
			return "" + execSize + "/" + index + " on time";
		}
	}

	public static void main(String[] args) throws InterruptedException {

		TimerEarlyTest test = new TimerEarlyTest();
		test.run();
	}
	private final long millisAtNanoZero;

	ScheduledExecutorService executorN = Executors.newScheduledThreadPool( Runtime.getRuntime().availableProcessors());
	ScheduledExecutorService executor1 = Executors.newScheduledThreadPool( 1);

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

	void executorsDo(Consumer<ScheduledExecutorService> ses) {
		ses.accept( executorN);
		ses.accept( executor1);
	}

	private void run() throws InterruptedException {
		NumberFormat nfI = NumberFormat.getIntegerInstance();
		nfI.setGroupingUsed( true);
		NumberFormat nfD = NumberFormat.getNumberInstance();
		nfD.setGroupingUsed( true);
		nfD.setMaximumFractionDigits( 2);

		long startNS = System.nanoTime();
		int maxJobs = 100_000;
		int jobsPerLoop = maxJobs / ( 2 * 2);   // 2 Loops, 2 Executors each
		long scheduleAt = System.currentTimeMillis() + 7000;
		List<Job> jobs = new ArrayList<>( maxJobs);
		for ( int i = 0;  i < jobsPerLoop;  i++) {
			long rndOffset = 10 * (long) (Math.random() * 300);
			long scheduleAtNanos = asNanos( scheduleAt + rndOffset);
			long delayNS = scheduleAtNanos - System.nanoTime();
			scheduleJobs( delayNS, i, scheduleAtNanos, jobs);
		}
		for ( int i = 0;  i < jobsPerLoop;  i++) {
			long rndOffset = 1 * (long) (Math.random() * 3000);
			long scheduleAtNanos = asNanos( scheduleAt + rndOffset);
			long delayNS = scheduleAtNanos - System.nanoTime();
			scheduleJobs( delayNS, i, scheduleAtNanos, jobs);
		}

		long nanosToSchedule = System.nanoTime() - startNS;
		System.out.println( "scheduled " + jobs.size() + " jobs in " + nfI.format(nanosToSchedule) + " ns"
				+ ", " + nfD.format( 1.0 * nanosToSchedule / jobs.size()) + " ns/job"
		);

		System.out.println( "checking after " + nfI.format( System.nanoTime() - startNS));

		List<Thread> waiters = new ArrayList<>();
		executorsDo( executor -> {
			executor.shutdown();
			Thread t = new Thread( () -> {
				while (true) {
					try {
						if ( executor.awaitTermination(10, TimeUnit.SECONDS)) break;
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
					long finishedCount = jobs.stream()
							.filter( Job::hasRun)
							.count();
					System.out.println( "finished " + nfI.format( finishedCount)
							+ "/" + nfI.format( jobs.size())
							+ " after " + nfI.format(System.nanoTime() - startNS)
					);
				}
			});
			waiters.add( t);
			t.start();
		});
		for (Thread waiter : waiters) {
			waiter.join();
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

	private void scheduleJobs(long delayNS, int i, long scheduleAtNanos, List<Job> jobs) {
		executorsDo( e -> {
			ScheduledThreadPoolExecutor se = (ScheduledThreadPoolExecutor) e;
			Job job = new Job( se.getCorePoolSize(), i, scheduleAtNanos);
			e.schedule( job, delayNS, TimeUnit.NANOSECONDS);
			jobs.add( job);

		});
	}

	private long asNanos(long millis) {
		long millisSinceNanoZero = millis - millisAtNanoZero;
		return 1000_000 * millisSinceNanoZero;
	}

}
