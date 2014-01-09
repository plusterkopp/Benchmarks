package de.icubic.mm.bench.benches;

import java.util.*;
import java.util.concurrent.*;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.server.utils.*;

public class MulDivBench {

	static double	one = 1;

	static abstract class AbstractArrayBench extends AbstractBenchRunnable {

		public AbstractArrayBench( String string) {
			super( string);
		}

		final long nruns = 222;
		final int arraySize = 1024 * 10;

		ThreadLocal<double[]> tl;

		public long getRunSize() {
			return nruns * arraySize;
		}

		@Override
		public void setup() {
			tl = new ThreadLocal<double[]>() {
				@Override
				protected double[] initialValue() {
					final double[] arr = new double[ arraySize];
					for ( int i = arr.length - 1; i >= 0; -- i) {
						arr[ i] = 1;
					}
					return arr;
				}
			};
			super.setup();
		}
	}


	/**
	 * @param args
	 */
	public static void main( String[] args) {

		IBenchRunnable byOneBench = new AbstractArrayBench( "byOne") {
			public void run() {
				double[] values = tl.get();
				for ( long r = nruns; r > 0; -- r) {
					for ( int i = values.length - 1; i >= 0; -- i) {
						values[ i] /= one;
					}
				}
			}
		};

		IBenchRunnable byOtherBench = new AbstractArrayBench( "byOther") {
			public void run() {
				final double d = 1.6;
				double[] values = tl.get();
				for ( long r = nruns; r > 0; -- r) {
					for ( int i = values.length - 1; i >= 0; -- i) {
						values[ i] /= d;
					}
				}
			}
		};

		IBenchRunnable byThree = new AbstractArrayBench( "byThree") {
			public void run() {
				final double d = 3;
				double[] values = tl.get();
				for ( long r = nruns; r > 0; -- r) {
					for ( int i = values.length - 1; i >= 0; -- i) {
						values[ i] /= d;
					}
				}
			}
		};

		IBenchRunnable timesTwoBench = new AbstractArrayBench( "timesTwo") {
			public void run() {
				double[] values = tl.get();
				final double factor = 2;
				for ( long r = nruns; r > 0; -- r) {
					for ( int i = values.length - 1; i >= 0; -- i) {
						values[ i] *= factor;
					}
					if ( values[ 0] > Double.MAX_VALUE / factor) {
						setup();
					}
				}
			}
		};

		IBenchRunnable timesOtherBench = new AbstractArrayBench( "timesOtherNoCheck") {
			public void run() {
				double[] values = tl.get();
				final double factor = 1.6;
				for ( long r = nruns; r > 0; -- r) {
					for ( int i = values.length - 1; i >= 0; -- i) {
						values[ i] *= factor;
					}
				}
			}
		};

		IBenchRunnable plusOtherBench = new AbstractArrayBench( "plusOther") {
			public void run() {
				double[] values = tl.get();
				final double summand = 1.6;
				for ( long r = nruns; r > 0; -- r) {
					for ( int i = values.length - 1; i >= 0; -- i) {
						values[ i] += summand;
					}
				}
			}
		};

		List<IBenchRunnable> benches = IQequitiesUtils.List( plusOtherBench, byOneBench, byThree, byOtherBench, timesTwoBench, timesOtherBench);
		IBenchRunner runner = new BenchRunner( byOneBench);
		runner.setRuntime( TimeUnit.SECONDS, 10);
		for ( IBenchRunnable bench : benches) {
			runner.setBenchRunner( bench);
			runner.run();
			runner.printResults();
		}
		// nimm plusOther als EmptyLoop: also das Durchlaufen des Feldes mit Anfasssen jedes Werts
		runner = new BenchRunner( plusOtherBench);
		runner.setRuntime( TimeUnit.SECONDS, 5);
		runner.run();
		runner.printResults();
		runner.setEmptyLoops( runner.getRunsPerSecond());

		System.out.println( "Single-Thread Warmup complete");
		runner.setRuntime( TimeUnit.SECONDS, 20);
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
