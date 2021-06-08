/*
 * Created on 13.08.2007
 *
 */
package benches;

import de.icubic.mm.bench.base.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * @author ralf
 *
 */
public class ILPTest {

	private static final int ILPMAX = 16;
	private static final int ArraySize = 1000 * ILPMAX;
	private static double[] y;
	private static double[] x;

	/**
	 * @param args
	 */
	public static void main( final String[] args) {

		final IBenchRunnable lbench = new AbstractBenchRunnable( "loop") {
			private final int nruns = 550055;

			public void run() {
				for ( int i = nruns; i > 0 ; i--) {
					//					@SuppressWarnings("unused") long time = System.nanoTime();
				}
			}

			public long getRunSize() {
				return nruns;
			}
		};

		abstract class SumBenchRunnable extends AbstractBenchRunnable {

			public SumBenchRunnable( final String name) {
				super( name);
			}

			double	sum = 0;
			protected int nruns = 5122;

			public double getSum() {
				return sum;
			}

			public long getRunSize() {
				return nruns * ArraySize;
			}

		}

		final IBenchRunnable noILP = new SumBenchRunnable( "NoILP") {

			public void run() {
				for ( int i = nruns; i > 0 ; i--) {
					sum = 0;
					for ( int j = ArraySize - 1;  j >= 0; j--) {
						sum += x[ j] * y[ j];
					}
				}
			}
		};

		final IBenchRunnable iLPn8 = new SumBenchRunnable( "iLPn8") {

			final int	n = 8;

			public void run() {
				for ( int i = nruns; i > 0 ; i--) {
					sum = 0;
					final double[] sums = new double[ n];
					for ( int s = 0;  s < n;  s++) {
						sums[ s] = 0;
					}
					for ( int j = ArraySize - 1;  j >= 0; j -= n) {
						for ( int s = 0;  s < n;  s++) {
							sums[ s] += x[ j-s] * y[ j-s];
						}
					}
					for ( int s = 0;  s < n;  s++) {
						sum += sums[ s];
					}
				}
			}
		};

		final IBenchRunnable iLPn4 = new SumBenchRunnable( "iLPn4") {

			final int	n = 4;

			public void run() {
				for ( int i = nruns; i > 0 ; i--) {
					sum = 0;
					final double[] sums = new double[ n];
					for ( int s = 0;  s < n;  s++) {
						sums[ s] = 0;
					}
					for ( int j = ArraySize - 1;  j >= 0; j -= n) {
						for ( int s = 0;  s < n;  s++) {
							sums[ s] += x[ j-s] * y[ j-s];
						}
					}
					for ( int s = 0;  s < n;  s++) {
						sum += sums[ s];
					}
				}
			}
		};

		final IBenchRunnable iLPn16 = new SumBenchRunnable( "iLPn16") {

			final int	n = 16;

			public void run() {
				for ( int i = nruns; i > 0 ; i--) {
					sum = 0;
					final double[] sums = new double[ n];
					for ( int s = 0;  s < n;  s++) {
						sums[ s] = 0;
					}
					for ( int j = ArraySize - 1;  j >= 0; j -= n) {
						for ( int s = 0;  s < n;  s++) {
							sums[ s] += x[ j-s] * y[ j-s];
						}
					}
					for ( int s = 0;  s < n;  s++) {
						sum += sums[ s];
					}
				}
			}
		};

		class ILPBenchRunnable extends SumBenchRunnable {

			final int	ilpFactor;

			public ILPBenchRunnable( int i) {
				super( "ILPn_");
				ilpFactor = i;
			}

			public void run() {
				for ( int i = nruns; i > 0 ; i--) {
					sum = 0;
					final double[] sums = new double[ ilpFactor];
					for ( int s = 0;  s < ilpFactor;  s++) {
						sums[ s] = 0;
					}
					for ( int j = ArraySize - 1;  j >= 0; j -= ilpFactor) {
						for ( int s = 0;  s < ilpFactor;  s++) {
							sums[ s] += x[ j-s] * y[ j-s];
						}
					}
					for ( int s = 0;  s < ilpFactor;  s++) {
						sum += sums[ s];
					}
				}
			}

			@Override
			public String getName() {
				return super.getName() + "_" + ilpFactor;
			}
		};

		final IBenchRunnable iLP2 = new SumBenchRunnable( "iLP2") {

			public void run() {
				for ( int i = nruns; i > 0 ; i--) {
					sum = 0;
					final double[] sums = new double[ 2];
					for ( int s = 0;  s < 2;  s++) {
						sums[ s] = 0;
					}
					for ( int j = ArraySize - 1;  j >= 0; j -= 2) {
						sums[ 0] += x[ j] * y[ j];
						sums[ 1] += x[ j-1] * y[ j-1];
					}
					for ( int s = 0;  s < 2;  s++) {
						sum += sums[ s];
					}
				}
			}

		};

		// init the source array
		setup();

		final IBenchRunner	runner = new BenchRunner( lbench);
		runner.setRuntime( TimeUnit.SECONDS, 10);
		runner.run();
		runner.printResults();
		final double lruns = runner.getRunsPerSecond();
		runner.setEmptyLoops( lruns);

		List<IBenchRunnable> runners = new ArrayList<IBenchRunnable>();
		runners.add(  noILP);
		runners.add(  iLP2);
		runners.add(  iLPn4);
		runners.add(  iLPn8);
		runners.add(  iLPn16);
		for ( int i = 2;  i <= ILPMAX;  i *= 2) {
			runners.add( new ILPBenchRunnable( i));
		}
		for ( final IBenchRunnable ibr : runners) {
			runner.setBenchRunner( ibr);
			runner.run();
			runner.printResults();
			final double nruns = runner.getRunsPerSecond();
			if ( ibr instanceof SumBenchRunnable) {
				final SumBenchRunnable sbr = ( SumBenchRunnable) ibr;
				BenchLogger.sysinfo( ibr.getName() + " result = " + sbr.getSum());
			}
		}

		runner.setRuntime( TimeUnit.SECONDS, 50);
		for ( final IBenchRunnable ibr : runners) {
			runner.setBenchRunner( ibr);
			runner.run();
			runner.printResults();
			final double nruns = runner.getRunsPerSecond();
			BenchRunner.addToComparisonList( ibr.getName() + "-50", nruns);
		}

		BenchRunner.printComparisonList();
	}

	private static void setup() {
		x = new double[ ArraySize];
		y = new double[ ArraySize];
		for (int i = 0; i < x.length; i++) {
			x[ i] = Math.random();
			y[ i] = Math.random();
		}
	}
}
