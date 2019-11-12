/* Created on 13.08.2007 */
package benches;

import java.util.*;
import java.util.concurrent.*;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.communication.util.Utils.MutableLong;
import de.icubic.mm.server.utils.*;

/**
 * <p>
 *
 * @author ralf
 *
 */
public class LongVsPrimitiveBench {

	static abstract class LongBench extends AbstractBenchRunnable {

		public LongBench( String string) {
			super( string);
		}

		final long nruns = 2222;
		final int arraySize = 1024 * 10;

		public long getRunSize() {
			return nruns * arraySize;
		}

	}

	/**
	 * @param args
	 */
	public static void main( String[] args) {

		IBenchRunnable pBench = new LongBench( "primitive") {

			ThreadLocal<long[]> tl;

			public void run() {
				long[] values = tl.get();
				for ( long r = nruns; r > 0; -- r) {
					for ( int i = values.length - 1; i >= 0; -- i) {
						++ values[ i];
					}
				}
			}

			@Override
			public void setup() {
				tl = new ThreadLocal<long[]>() {
					/* (non-Javadoc)
					 * @see java.lang.ThreadLocal#initialValue()
					 */
					@Override
					protected long[] initialValue() {
						return new long[ arraySize];
					}

				};
				super.setup();
			}
		};

		IBenchRunnable oBench = new LongBench( "object") {

			ThreadLocal<Long[]> tl;

			public void run() {
				Long[] values = tl.get();
				for ( long r = nruns; r > 0; -- r) {
					for ( int i = values.length - 1; i >= 0; -- i) {
						values[ i] = new Long( values[ i].longValue() + 1);
					}
				}
			}

			@Override
			public void setup() {
				tl = new ThreadLocal<Long[]>() {
					/* (non-Javadoc)
					 * @see java.lang.ThreadLocal#initialValue()
					 */
					@Override
					protected Long[] initialValue() {
						Long[] values = new Long[ arraySize];
						for ( int i = values.length - 1; i >= 0; -- i) {
							values[ i] = new Long( 0);
						}
						return values;
					}
				};
				super.setup();
			}
		};

		IBenchRunnable aBench = new LongBench( "autobox") {

			ThreadLocal<Long[]> tl;

			public void run() {
				Long[] values = tl.get();
				for ( long r = nruns; r > 0; -- r) {
					for ( int i = values.length - 1; i >= 0; -- i) {
						values[ i]++;
					}
				}
			}

			@Override
			public void setup() {
				tl = new ThreadLocal<Long[]>() {
					/* (non-Javadoc)
					 * @see java.lang.ThreadLocal#initialValue()
					 */
					@Override
					protected Long[] initialValue() {
						Long[] values = new Long[ arraySize];
						for ( int i = values.length - 1; i >= 0; -- i) {
							values[ i] = 0L;
						}
						return values;
					}
				};
				super.setup();
			}
		};

		IBenchRunnable mBench = new LongBench( "mutable") {

			ThreadLocal<MutableLong[]> tl;

			public void run() {
				MutableLong[] values = tl.get();
				for ( long r = nruns; r > 0; -- r) {
					for ( int i = values.length - 1; i >= 0; -- i) {
						values[ i].increment();
					}
				}
			}

			@Override
			public void setup() {
				tl = new ThreadLocal<MutableLong[]>() {
					/* (non-Javadoc)
					 * @see java.lang.ThreadLocal#initialValue()
					 */
					@Override
					protected MutableLong[] initialValue() {
						MutableLong[] values = new MutableLong[ arraySize];
						for ( int i = values.length - 1; i >= 0; -- i) {
							values[ i] = new MutableLong( 0);
						}
						return values;
					}
				};
				super.setup();
			}
		};

		List<IBenchRunnable> benches = IQequitiesUtils.List( pBench, oBench, aBench, mBench);
		IBenchRunner runner = new BenchRunner( pBench);
		runner.setRuntime( TimeUnit.SECONDS, 10);
		for ( IBenchRunnable bench : benches) {
			runner.setBenchRunner( bench);
			runner.run();
			runner.printResults();
		}
		BenchLogger.sysout( "Single-Thread Warmup complete, switching to " + Runtime.getRuntime().availableProcessors() + " Threads");
		runner = new TPBenchRunner( pBench);
		runner.setRuntime( TimeUnit.SECONDS, 30);
		for ( IBenchRunnable bench : benches) {
			System.gc();
			runner.setBenchRunner( bench);
			runner.run();
			runner.printResults();
			BenchRunner.addToComparisonList( bench.getName(), runner.getRunsPerSecond());
		}
		BenchRunner.printComparisonList();
	}

}
