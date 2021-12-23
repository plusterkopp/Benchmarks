package benches;

/**
 *
 */


import de.icubic.mm.bench.base.*;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.*;

/**
 * incrementiere Variable unter Lock, mit verschiedenen Locks.<br>
 * Wir haben N Longs, R Lese-Threads, die die Werte unter Leselock auslesen und ihn in einen
 * Zeitraum T verarbeiten. M Threads, die die Werte mit einer Wahrscheinlichkeit P verändern.
 *
 * @author rhelbing
 *
 */
public class LockModBench {

	static final int NProcs = Runtime.getRuntime().availableProcessors();

	static final ThreadLocal<Random> tlRnd = ThreadLocal.withInitial( () -> new Random());
	/**
	 * Anzahl der zu lesenden/schreibenden Werte und der dazugehörigen Locks
	 */
	static final int N = ( int) Math.round( Math.sqrt( NProcs));
	/**
	 * Platz zwischen den zu lesenden Werten. Damit es dem Cache nicht zu gut geht, lesen wir die
	 * Werte auch noch alle, nachden wir den eigentlichen Werten haben.
	 */
	static final int PaddingFactor = 100000;
	/**
	 * hält alle Werte und sorgt für genug Platz dazwischen, damit nicht alles in den Cache paßt
	 */
	static final long[] values = new long[ N * PaddingFactor];

	/**
	 * Anzahl der Lese-Threads, mindestens 2
	 */
	static final int R = Math.max( 2, NProcs - 1);
	/**
	 * Anzahl der Schrei-Threads, mindestens 1
	 */
	static final int M = Math.max( 1, NProcs - R);
	/**
	 * Wahrscheinlichkeit je Durchlauf, daß ein Schreibthread tatsächlich einen Wert ändern muß. Wird benutzt, um das
	 * Verhältnis von Lese- zu Schreibvorgängen zu variieren.
	 */
	static final double P = 0.1;
	/**
	 * Zeit in ns, die das Lese-Lock gehalten wird, um den gelesenen Wert zu verarbeiten und währenddessen seine
	 * Gültigkeit zu garantieren.
	 */
	static final long T = 0;
	/**
	 * Anzahl der Dummy-Schleifen, die etwa gebraucht wird, um die Haltezeit {@link #T} zu simulieren
	 */
	static long TLoops;

	/**
	 * wird mit verschiedenen Lock-Arten implementiert
	 */
	static interface LockType {
		/**
		 * Liest einen zufällig ausgewählten Wert mit Index zwischen 0 und {@link #N} und fordert dabei das dazugehörige (Lese)Lock an. Führt eine etwa
		 * {@link #T} ns dauernde Operation im Anschluß durch und gibt erst dann das Lock wieder frei.
		 *
		 * @return true (nur für Kompatibilität mit {@link #maybeWrite()}
		 */
		boolean readAndProcess();

		/**
		 * Inkrementiert mit einer Wahrscheinlichkeit von {@link LockModBench#P} einen zufällig ausgewählten Wert und
		 * fordert dabei das dazugehörige (Schreib)Lock an. Gibt das Lock sofort nach der Operation wieder frei.
		 *
		 * @return true, wenn die Wahrscheinlichkeit eintrat und ein Wert verändert wurde, sonst false.
		 */
		boolean maybeWrite();
		/**
		 * wird bei {@link LTStOLock} benutzt, um die Zahl der pessimistischen Locks auszugeben
		 * @return "" oder mehr Info
		 */
		String getRunInfo();
	}

	/**
	 * führt wiederholt eine Aktion aus, bis die Schleife durch {@link #stop()} von außen beendet wird. Zählt die Zahl
	 * der erfolgreichen Durchläufe in {@link #counter}.
	 *
	 * @param <T>
	 */
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
				// hole Werte und dazugehöriges Lock, verarbeite für gewünschte Zeit, bzw schreibe Wert
				if ( action.test( t)) {	// für Schreib-Aktion: zähle nur, wenn wirklich geschrieben
					counter++;
				}
			}
		}

		void stop() {
			stop.set( true);
		}
	}

	/**
	 * synchronize Block, für Lesen und Schreiben, sperrt das Objekt jeweils komplett
	 */
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

	/**
	 * nutzt {@link ReentrantReadWriteLock}, {@link ReentrantReadWriteLock#readLock()} beim Lesen,
	 * {@link ReentrantReadWriteLock#writeLock()} beim Schreiben,
	 */
	static class LTReentLock implements LockType {

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

	/**
	 * {@link StampedLock} mit ausschließlich pessimistischen Lese/Schreiblocks
	 */
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

	/**
	 * {@link StampedLock} mit optimistischem Leseversuchen und Rückfall auf pessimistich bei Fehlschlag
	 */
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
			final StampedLock	lock = locks[ index];
			long	stamp = lock.tryOptimisticRead();
				int startIndex = index * PaddingFactor;
				long	v = values[ startIndex];
				process( v, values, startIndex);
				if ( ! lock.validate( stamp)) {
					pessimizedCounter.incrementAndGet();
					try {
						stamp = lock.readLock();
						v = values[ startIndex];
						process( v, values, startIndex);
					} finally {
						lock.unlockRead( stamp);
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
				StampedLock	lock = locks[ index];
				long stamp = lock.writeLock();
				try {
					int startIndex = index * PaddingFactor;
					values[ startIndex]++;
				} finally {
					lock.unlockWrite( stamp);
				}
				return true;
			}
			return false;
		}

		@Override
		public String getRunInfo() {
			return BenchLogger.LNF.format( pessimizedCounter.get());
		}
	}

	static void process( long v, long[] values, int startIndex) {
		if ( TLoops <= 0) {
			return;
		}
		long	loopCounter;
		int 	arrayIndex;
		long	dummySum = v;
		for ( loopCounter = 0, arrayIndex = 0;  loopCounter < TLoops;  loopCounter++) {
			dummySum += values[ arrayIndex + startIndex];
			arrayIndex++;
			if ( arrayIndex > PaddingFactor) {
				arrayIndex = 0;
			}
		}
		// um zu verhindern, daß der JIT was wegoptimiert, verwenden wir hier den berechneten Wert. Da wir die Feldwerte
		// nie belegen, bleiben alle Werte 0 und der IF-Zweig wird nie ausgeführt.
		if ( dummySum < v) {
			BenchLogger.sysout( "should not get smaller");
		}
	}

	/**
	 * bestimme {@link #TLoops} aus {@link #T}: <br>
	 * stellt in einem Zeitintervall von 5s fest, wieviele Durchläufe möglich sind und berechnet daraus TLoops so, daß
	 * {@link #TLoops} Schleifen etwa {@link #T} ns benötigen
	 */
	static void calibrate() {
		if ( T == 0) {
			TLoops = 0;
			return;
		}
		int	startIndex = 0;
		// führt den gleichen Code aus wie process
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
		BenchLogger.sysinfo( "Calibrate: " + TLoops
				+ " (" + BenchLogger.LNF.format( T * rpns) + ")"
				+ " ops in " + T + " ns");
	}

	public static void main( String[] args) {
		NumberFormat nf = BenchLogger.LNF;
		BenchLogger.sysout( "Lock Perf Test with " + R + " readers, " + M + " writers, "
				+ T + " ns process time after read, "
				+ nf.format( 100.0 * P) + " % chance of modification, "
				+ N + " locked objects, " + nf.format( 8 * PaddingFactor) + " B padding");
		calibrate();
		List<LockType> lockTypes = List( new LTSync(), new LTReentLock(), new LTStPLock(), new LTStOLock());
		Thread[]	readers = new Thread[ R];
		Thread[] writers = new Thread[ M];
		List<WorkerJob<LockType>> allJobs = new ArrayList<>();
		List<WorkerJob<LockType>> readerJobs = new ArrayList<>();
		List<WorkerJob<LockType>> writerJobs = new ArrayList<>();
		for ( final LockType lockType : lockTypes) {
			String lockName = lockType.getClass().getSimpleName();
			// Reader
			for ( int i = 0;  i < readers.length;  i++) {
				WorkerJob<LockType> readerJob = new WorkerJob<LockType>( lockType, t -> t.readAndProcess());
				readerJobs.add( readerJob);
				readers[ i] = new Thread( readerJob, lockName + "-R-" + i);
				readers[ i].start();
			}
			// Writer
			for ( int i = 0;  i < M; i++) {
				WorkerJob<LockType> writerJob = new WorkerJob<LockType>( lockType, t -> t.maybeWrite());
				writerJobs.add( writerJob);
				writers[ i] = new Thread( writerJob, lockName + "-W-" + i);
				writers[ i].start();
			}
			allJobs.addAll( readerJobs);
			allJobs.addAll( writerJobs);
			// Threads laufen lassen
			int runSecs = 20;
			sleep( runSecs * 1000);
			// stoppen durch Setzen des Stop-Flags
			for ( WorkerJob<LockType> j : allJobs) {
				j.stop();
			}
			// Ende der Threads abwarten
			try {
				for ( Thread thread : writers) {
					thread.join( 1);
				}
				for ( Thread thread : readers) {
					thread.join( 1);
				}
			} catch ( InterruptedException e) {
				BenchLogger.syserr( "", e);
			}
			// Ergebnisse einsammeln
			long	rSum = 0;
			for ( WorkerJob<LockType> job : readerJobs) {
				rSum += job.counter;
			}
			long	mSum = 0;
			for ( WorkerJob<LockType> job : writerJobs) {
				mSum += job.counter;
			}
			double readsPS = rSum / runSecs;
			double writesPS = mSum / runSecs;
			BenchLogger.sysout( lockName + ": " + nf.format( readsPS) + " r/s, " + nf.format( writesPS) + " w/s, " + lockType.getRunInfo());
			BenchRunner.addToComparisonList( lockName + " reads", readsPS);
		}
		BenchRunner.printComparisonList( LTReentLock.class.getSimpleName() + " reads");
	}

	private static void sleep( int i) {
		try {
			Thread.sleep( i);
		} catch ( InterruptedException e) {
			BenchLogger.syserr( "", e);
		}
	}

	public static <T> List<T> List( T...elements) {
		return Arrays.asList( elements);
	}


}
