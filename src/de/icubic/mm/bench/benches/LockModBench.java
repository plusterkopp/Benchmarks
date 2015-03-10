/**
 *
 */
package de.icubic.mm.bench.benches;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.*;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.server.utils.*;

/**
 * incrementiere Variable unter Lock, mit verschiedenen Locks.<br>
 * Wir haben N Longs, R Lese-Threads, die die Werte unter Leselock auslesen und ihn in einen
 * Zeitraum T verarbeiten. M Threads, die die Werte mit einer Wahrscheinlichkeit P verändern.
 *
 * @author rhelbing
 *
 */
public class LockModBench {

	static final int Procs = Runtime.getRuntime().availableProcessors();

	static final ThreadLocal<Random> tlRnd = ThreadLocal.withInitial( () -> new Random());
	static final int N = Procs;
	/**
	 * Platz zwischen den zu lesenden Werten. Damit es dem Cache nicht zu gut geht, lesen wir die
	 * Werte auch noch alle, nachden wir den eigentlichen Werten haben.
	 */
	static final int PaddingFactor = 100000;
	static final long[] values = new long[ N * PaddingFactor];

	static final int R = Procs - 1;
	static final int M = Procs - R;
	static final double P = 0.001;
	/**
	 * Nanos
	 */
	static final long T = 1000;
	static long TLoops;

	static interface LockType {
		boolean readAndProcess();
		boolean maybeWrite();
		String getRunInfo();
	}

	static class WorkerJob<T extends LockType> implements Runnable {

		public WorkerJob( T t, Predicate<T> action) {
			super();
			this.action = action;
			this.t = t;
		}

		Predicate<T> action;
		AtomicBoolean	stop = new AtomicBoolean( false);
		long	counter = 0;
		private T t;

		@Override
		public void run() {
			while ( ! stop.get()) {
				// hole Werte und dazugehöriges Lock, verarbeite für gewünschte Zeit
				if ( action.test( t)) {
					counter++;
				}
			}
		}

		void stop() {
			stop.set( true);
		}
	}

	static class LTSync implements LockType {

		static final Object[]	locks = new Object[ N];
		static {
			for( int i = 0;  i < N;  i++) {
				locks[ i] = new Object();
			}
		}

		@Override
		public boolean readAndProcess() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			synchronized ( locks[ index]) {
				int startIndex = index * PaddingFactor;
				long	v = values[ startIndex];
				process( v, values, startIndex);
			}
			return true;
		}

		@Override
		public boolean maybeWrite() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			double p = rnd.nextDouble();
			if ( p < P) {
				synchronized ( locks[ index]) {
					int startIndex = index * PaddingFactor;
					values[ startIndex]++;
				}
				return true;
			}
			return false;
		}

		@Override
		public String getRunInfo() {
			return "";
		}
	}

	static class LTReeLock implements LockType {

		static final ReentrantReadWriteLock[]	locks = new ReentrantReadWriteLock[ N];
		static {
			for( int i = 0;  i < N;  i++) {
				locks[ i] = new ReentrantReadWriteLock();
			}
		}

		@Override
		public boolean readAndProcess() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			Lock	l = locks[ index].readLock();
			l.lock();
			try {
				int startIndex = index * PaddingFactor;
				long	v = values[ startIndex];
				process( v, values, startIndex);
			} finally {
				l.unlock();
			}
			return true;
		}

		@Override
		public boolean maybeWrite() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			double p = rnd.nextDouble();
			if ( p < P) {
				Lock	l = locks[ index].writeLock();
				l.lock();
				try {
					int startIndex = index * PaddingFactor;
					values[ startIndex]++;
				} finally {
					l.unlock();
				}
				return true;
			}
			return false;
		}

		@Override
		public String getRunInfo() {
			return "";
		}
	}

	static class LTStPLock implements LockType {

		static final StampedLock[]	locks = new StampedLock[ N];
		static {
			for( int i = 0;  i < N;  i++) {
				locks[ i] = new StampedLock();
			}
		}

		@Override
		public boolean readAndProcess() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			StampedLock	l = locks[ index];
			long	stamp = l.readLock();
			try {
				int startIndex = index * PaddingFactor;
				long	v = values[ startIndex];
				process( v, values, startIndex);
			} finally {
				l.unlockRead( stamp);
			}
			return true;
		}

		@Override
		public boolean maybeWrite() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			double p = rnd.nextDouble();
			if ( p < P) {
				StampedLock	l = locks[ index];
				long stamp = l.writeLock();
				try {
					int startIndex = index * PaddingFactor;
					values[ startIndex]++;
				} finally {
					l.unlockWrite( stamp);
				}
				return true;
			}
			return false;
		}

		@Override
		public String getRunInfo() {
			return "";
		}
	}

	static class LTStOLock implements LockType {

		static final StampedLock[]	locks = new StampedLock[ N];
		static {
			for( int i = 0;  i < N;  i++) {
				locks[ i] = new StampedLock();
			}
		}

		AtomicLong	pessimizedCounter = new AtomicLong( 0);

		@Override
		public boolean readAndProcess() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			final StampedLock	l = locks[ index];
			long	stamp = l.tryOptimisticRead();
				int startIndex = index * PaddingFactor;
				long	v = values[ startIndex];
				process( v, values, startIndex);
				if ( ! l.validate( stamp)) {
					pessimizedCounter.incrementAndGet();
					try {
						stamp = l.readLock();
						v = values[ startIndex];
						process( v, values, startIndex);
					} finally {
						l.unlockRead( stamp);
					}
				}
			return true;
		}

		@Override
		public boolean maybeWrite() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			double p = rnd.nextDouble();
			if ( p < P) {
				StampedLock	l = locks[ index];
				long stamp = l.writeLock();
				try {
					int startIndex = index * PaddingFactor;
					values[ startIndex]++;
				} finally {
					l.unlockWrite( stamp);
				}
				return true;
			}
			return false;
		}

		@Override
		public String getRunInfo() {
			return BenchLogger.lnf.format( pessimizedCounter.get());
		}
	}

	static void process( long v, long[] values, int startIndex) {
		long	l;
		int 	i;
		long	vv = v;
		for ( l = 0, i = 0;  l < TLoops;  l++) {
			vv += values[ i + startIndex];
			i++;
			if ( i > PaddingFactor) {
				i = 0;
			}
		}
		if ( vv < v) {
			System.out.println( "should not get smaller");
		}
	}

	/**
	 * bestimme {@link #TLoops} aus {@link #T}
	 */
	static void calibrate() {
		int	startIndex = 0;
		IBenchRunnable	cloop = new AbstractBenchRunnable( "CLoop") {
			@Override
			public void run() {
				long sum = 0;
				for ( int i = 0;  i < PaddingFactor;  i++) {
					sum += values[ i + startIndex];
				}
				if ( sum == -20) {
					System.out.print( "Dummy Test");
				}
			}
			@Override
			public long getRunSize() {
				return PaddingFactor;
			}
		};
		BenchRunner	runner = new BenchRunner( cloop);
		int timeS = 5;
		runner.setRuntime( TimeUnit.SECONDS, timeS);
		runner.run();
		double rpns = runner.getRunsPerSecond() * 1e-9;
		TLoops = ( long) ( T * rpns);
		BenchLogger.sysinfo( "Calibrate: " + TLoops + " ops in " + T + " ns");
	}

	public static void main( String[] args) {
		NumberFormat nf = BenchLogger.lnf;
		BenchLogger.sysout( "Lock Perf Test with " + R + " readers, " + M + " writers, "
				+ T + " ns process time after read, "
				+ nf.format( 100.0 * P) + " % chance of modification, "
				+ N + " locked objects, " + nf.format( 8 * PaddingFactor) + " B padding");
		calibrate();
		List<LockType> lockTypes = IQequitiesUtils.List( new LTSync(), new LTReeLock(), new LTStPLock(), new LTStOLock());
		Thread[]	readers = new Thread[ R];
		Thread[] writers = new Thread[ M];
		List<WorkerJob<LockType>> jobs = new ArrayList<>();
		List<WorkerJob<LockType>> rJobs = new ArrayList<>();
		List<WorkerJob<LockType>> mJobs = new ArrayList<>();
		for ( final LockType lockType : lockTypes) {
			String lockName = lockType.getClass().getSimpleName();
			// Reader
			for ( int i = 0;  i < readers.length;  i++) {
				WorkerJob<LockType> j = new WorkerJob<LockType>( lockType, t -> t.readAndProcess());
				rJobs.add( j);
				readers[ i] = new Thread( j, lockName + "-R-" + i);
				readers[ i].start();
			}
			// Writer
			for ( int i = 0;  i < M; i++) {
				WorkerJob<LockType> j = new WorkerJob<LockType>( lockType, t -> t.maybeWrite());
				mJobs.add( j);
				writers[ i] = new Thread( j, lockName + "-W-" + i);
				writers[ i].start();
			}
			jobs.addAll( rJobs);
			jobs.addAll( mJobs);
			// Threads laufen lassen
			int runSecs = 20;
			sleep( runSecs * 1000);
			// stoppen
			for ( WorkerJob<LockType> j : jobs) {
				j.stop();
			}
			// beenden
			try {
				for ( Thread thread : writers) {
					thread.join( 1);
//					BenchLogger.sysinfo( "Thread " + thread.getName() + " finished");
				}
				for ( Thread thread : readers) {
					thread.join( 1);
//					BenchLogger.sysinfo( "Thread " + thread.getName() + " finished");
				}
			} catch ( InterruptedException e) {
				e.printStackTrace();
			}
			// Ergebnisse einsammeln
			long	rSum = 0;
			for ( WorkerJob<LockType> job : rJobs) {
				rSum += job.counter;
			}
			long	mSum = 0;
			for ( WorkerJob<LockType> job : mJobs) {
				mSum += job.counter;
			}
			double readsPS = rSum / runSecs;
			double writesPS = mSum / runSecs;
			BenchLogger.sysout( lockName + ": " + nf.format( readsPS) + " rps, " + nf.format( writesPS) + " wps, " + lockType.getRunInfo());
		}
	}

	private static void sleep( int i) {
		try {
			Thread.sleep( i);
		} catch ( InterruptedException e) {
			e.printStackTrace();
		}
	}
}
