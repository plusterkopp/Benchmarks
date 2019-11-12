/* Created on 13.08.2007 */
package benches;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.server.utils.*;

/**
 * <p>
 * @author ralf
 *
 */
public class SyncCounterTest2 {

	static NumberFormat lnf = DecimalFormat.getNumberInstance();
	static {
		lnf.setMaximumFractionDigits( 0);
		lnf.setGroupingUsed( true);
	};

	/**
	 * @param args
	 */
	public static void main( String[] args) {

		abstract class CounterBenchRunnable extends AbstractBenchRunnable {
			protected final int nruns = 5500;
			int nCores;

			public CounterBenchRunnable( String name) {
				super( name);
				setNCores( Runtime.getRuntime().availableProcessors());
			}

			private void setNCores( int availableProcessors) {
				nCores = availableProcessors;
				setup();
			}

			@Override
			public long getRunSize() {
				return nruns * nCores;
			}

			abstract long getCount();

			@Override
			public String getName() {
				return super.getName() + " (" + lnf.format( getCount()) + ")";
			}
		};

		// ohne Sync: einfach zählen (nur für Single-Thread)
		class UnsyncCounterBenchRunnable extends CounterBenchRunnable {

			public UnsyncCounterBenchRunnable( String name) {
				super( name);
			}

			private long[]	counters;

			@Override
			public void run() {
				for ( int r = nruns; r > 0 ; r--) {
					for ( int i = counters.length - 1;  i >= 0 ;  i--) {
						counters[ i]++;
					}
				}
			}

			@Override
			long getCount() {
				long sum = 0;
				for ( int i = 0;  i < counters.length;  i++) {
					sum += counters[ i];
				}
				return sum;
			}

			@Override
			public void reset() {
				super.reset();
				counters = new long[ nCores];
				for ( int i = 0;  i < counters.length;  i++) {
					counters[ i] = 0;
				}
			}
		};

		final CounterBenchRunnable ubench = new UnsyncCounterBenchRunnable( "unsync");

		// ohne Sync: Atomic
		class AtomicCounterBenchRunnable extends CounterBenchRunnable {
			public AtomicCounterBenchRunnable( String name) {
				super( name);
			}

			private AtomicLong[]	counters;

			@Override
			public void run() {
				for ( int r = nruns; r > 0 ; r--) {
					for ( int i = counters.length - 1;  i >= 0 ;  i--) {
						counters[ i].incrementAndGet();
					}
				}
			}

			@Override
			long getCount() {
				long sum = 0;
				for ( int i = 0;  i < counters.length;  i++) {
					sum += counters[ i].get();
				}
				return sum;
			}

			@Override
			public void reset() {
				super.reset();
				counters = new AtomicLong[ nCores];
				for ( int i = 0;  i < counters.length;  i++) {
					counters[ i] = new AtomicLong( 0);
				}
			}
		};

		final CounterBenchRunnable abench = new AtomicCounterBenchRunnable( "atomic");

		// Accumulator
		class AccumCounterBenchRunnable extends CounterBenchRunnable {
			public AccumCounterBenchRunnable( String name) {
				super( name);
			}

			private LongAccumulator[]	counters;

			@Override
			public void run() {
				for ( int r = nruns; r > 0 ; r--) {
					for ( int i = counters.length - 1;  i >= 0 ;  i--) {
						counters[ i].accumulate( 1);
					}
				}
				for ( int i = 0;  i < counters.length;  i++) {
					counters[ i].get();
				}
			}

			@Override
			long getCount() {
				long sum = 0;
				for ( int i = 0;  i < counters.length;  i++) {
					sum += counters[ i].get();
				}
				return sum;
			}

			@Override
			public void reset() {
				super.reset();
				counters = new LongAccumulator[ nCores];
				for ( int i = 0;  i < counters.length;  i++) {
					counters[ i] = new LongAccumulator( ( a, b) -> a+b, 0);
				}
			}
		};

		final CounterBenchRunnable acbench = new AccumCounterBenchRunnable( "accum");

		// ohne Sync: Volatile
		class VolatileCounterBenchRunnable extends CounterBenchRunnable {

			public VolatileCounterBenchRunnable( String name) {
				super( name);
			}

			private volatile long[]	counters;

			@Override
			public void run() {
				for ( int r = nruns; r > 0 ; r--) {
					for ( int i = counters.length - 1;  i >= 0 ;  i--) {
						counters[ i]++;
					}
				}
			}

			@Override
			long getCount() {
				long sum = 0;
				for ( int i = 0;  i < counters.length;  i++) {
					sum += counters[ i];
				}
				return sum;
			}

			@Override
			public void reset() {
				super.reset();
				counters = new long[ nCores];
				for ( int i = 0;  i < counters.length;  i++) {
					counters[ i] = 0;
				}
			}
		};

		final CounterBenchRunnable vbench = new VolatileCounterBenchRunnable( "volatile");

		// mit Locking, die Zähler sind aber nicht mehr volatil
		abstract class LockedCounterBenchRunnable extends VolatileCounterBenchRunnable {

			protected long[]	counters;
			protected Lock[] 	locks;

			public LockedCounterBenchRunnable( String name) {
				super( name);
			}

			@Override
			long getCount() {
				long sum = 0;
				for ( int i = 0;  i < counters.length;  i++) {
					sum += counters[ i];
				}
				return sum;
			}

			@Override
			public void setup() {
				super.setup();
				locks = new ReentrantLock[ nCores];
				for ( int i = 0;  i < locks.length;  i++) {
					locks[ i] = new ReentrantLock();
				}
			}

			@Override
			public void reset() {
				super.reset();
				counters = new long[ nCores];
				for ( int i = 0;  i < counters.length;  i++) {
					counters[ i] = 0;
				}
			}
		};

		// Locking mit Sync
		final LockedCounterBenchRunnable sbench = new LockedCounterBenchRunnable( "sync") {

			@Override
			public void run() {
				for ( int r = nruns; r > 0 ; r--) {
					for ( int i = counters.length - 1;  i >= 0 ;  i--) {
						synchronized ( locks[ i]) {
							counters[ i]++;
						}
					}
				}
			}
		};

		// mit tryLock
		final LockedCounterBenchRunnable tbench = new LockedCounterBenchRunnable( "tlock") {

			@Override
			public void run() {
				for ( int r = nruns; r > 0 ; r--) {
					for ( int i = counters.length - 1;  i >= 0 ;  i--) {
						if ( locks[ i].tryLock()) {
							try {
								counters[ i]++;
							} finally {
								locks[ i].unlock();
							}
						}
					}
				}
			}

			@Override
			public long getTotalRunSize( long nruns) {
				return getCount();
			}

		};

		// mit Lock
		final LockedCounterBenchRunnable lbench = new LockedCounterBenchRunnable( "lock") {

			@Override
			public void run() {
				for ( int r = nruns; r > 0 ; r--) {
					for ( int i = counters.length - 1;  i >= 0 ;  i--) {
						locks[ i].lock();
						try {
							counters[ i]++;
						} finally {
							locks[ i].unlock();
						}
					}
				}
			}
		};

		// mit StampedLock
		final LockedCounterBenchRunnable stbench = new LockedCounterBenchRunnable( "stamped") {

			protected StampedLock[] 	slocks;

			@Override
			public void setup() {
				super.setup();
				slocks = new StampedLock[ nCores];
				for ( int i = 0;  i < locks.length;  i++) {
					slocks[ i] = new StampedLock();
				}
			}

			@Override
			public void run() {
				for ( int r = nruns; r > 0 ; r--) {
					for ( int i = counters.length - 1;  i >= 0 ;  i--) {
						long stamp = slocks[ i].writeLock();
						try {
							counters[ i]++;
						} finally {
							slocks[ i].unlockWrite( stamp);
						}
					}
				}
			}
		};

		Thread t = new Thread() {
			@Override
			public void run() {
				try {
				IBenchRunner	runner = new BenchRunner( ubench);
				runner.setRuntime( TimeUnit.SECONDS, 10);
				runner.run();
				runner.printResults();
				BenchRunner.addToComparisonList( ubench.getName(), runner.getRunsPerSecond());
				List<CounterBenchRunnable>	benches = IQequitiesUtils.List( abench, acbench, sbench, lbench, tbench, stbench);
				List<Integer>	threadCounts = IQequitiesUtils.List( 1, 2, 4, 16, 64);
				for ( Integer nThreads : threadCounts) {
						BenchLogger.sysout( "Using " + nThreads + " Threads, "
								+ Runtime.getRuntime().availableProcessors() + " cores");
					runner = new TPBenchRunner( null, nThreads);
					runner.setRuntime( TimeUnit.SECONDS, 10);
					for ( CounterBenchRunnable bench : benches) {
						runner.setBenchRunner( bench);
						runner.run();
						runner.printResults();
						BenchRunner.addToComparisonList( bench.getName(), runner.getRunsPerSecond());
						System.gc();
					}
					BenchRunner.printComparisonList();
					BenchRunner.clearComparisonList();
				}
				} catch ( Exception e) {
					BenchLogger.syserr( "", e);
					System.exit( 1);
				}
			}
		};
		t.start();
		try {
			t.join();
		} catch ( InterruptedException e) {
			BenchLogger.syserr( "", e);
		}
	}

}
