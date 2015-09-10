/*
 * Created on 13.08.2007
 *
 */
package de.icubic.mm.bench.benches;

import java.util.concurrent.*;

import de.icubic.mm.bench.base.*;

/**
 * nimm ein Feld mit vielen Integers, und tu (fast) nur echte Werte rein, aber
 * auch ein paar Nullen. Dann durchlaufe das Feld und �bertrage die
 * Nicht-Null-Werte in ein neues Feld. Das eine Mal mit einem Nulltest, das
 * andere Mal mit einem NPE-Catch.
 * <p>
 * @author ralf
 *
 */
public class NPEvsNullCheckTest {

	private static final int ArraySize = 10000;
	private static final double ratio = 0.00001;
	private static Integer[] sourceArray;
	private static int[] destArray;

	/**
	 * @param args
	 */
	public static void main( String[] args) {

		IBenchRunnable lbench = new AbstractBenchRunnable( "loop") {
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

		IBenchRunnable npebench = new AbstractBenchRunnable( "NPE") {
			private int nruns = 50;
			@SuppressWarnings("unused")
			long[]	times = new long[ nruns + 1];

			public void run() {
				for ( int i = nruns; i > 0 ; i--) {
					for ( int j = ArraySize - 1;  j >= 0; j--) {
						try {
							destArray[ j] = sourceArray[ j];
						} catch( NullPointerException npe) {
//							destArray[ j] = 0;
						} catch( Exception e) {
						}
					}
				}
			}

			public long getRunSize() {
				return nruns * ArraySize;
			}
		};

		IBenchRunnable nullcheckbench = new AbstractBenchRunnable( "Null-Check") {
			private int nruns = 50;

			public void run() {
				for ( int i = nruns; i > 0 ; i--) {
					for ( int j = ArraySize - 1;  j >= 0; j--) {
						try {
							final Integer value = sourceArray[ j];
							if ( value != null) {
								destArray[ j] = value.intValue();
							} else {
//							destArray[ j] = 0;
							}
						} catch (Exception e) {
						}
					}
				}
			}

			public long getRunSize() {
				return nruns * ArraySize;
			}
		};

		// init the source array
		setup();

		IBenchRunner	runner = new BenchRunner( npebench);
		runner.setRuntime( TimeUnit.SECONDS, 10);

		runner.setBenchRunner( lbench);
		runner.run();
		runner.printResults();
		double lruns = runner.getRunsPerSecond();

		runner.setEmptyLoops( lruns);
		runner.setBenchRunner( npebench);
		runner.run();
		runner.printResults();
		double nruns = runner.getRunsPerSecond();

		runner.setBenchRunner(nullcheckbench);
		runner.run();
		runner.printResults();
		double cruns = runner.getRunsPerSecond();

		runner.setRuntime( TimeUnit.SECONDS, 50);

		runner.setBenchRunner( npebench);
		runner.run();
		runner.printResults();
		nruns = runner.getRunsPerSecond();

		runner.setBenchRunner(nullcheckbench);
		runner.run();
		runner.printResults();
		cruns = runner.getRunsPerSecond();

		BenchLogger.sysout( nullcheckbench.getName() + "/" + npebench.getName() + " = " + cruns/nruns);
	}

	private static void setup() {
		sourceArray = new Integer[ ArraySize ];
		destArray = new int[ ArraySize ];
		int nullCounter = 0;
		for (int i = 0; i < sourceArray.length; i++) {
			double rnd = Math.random();
			if ( rnd < ratio) {
				sourceArray[i] = null;
				nullCounter ++;
			} else {
				sourceArray[i] = 200;
			}
		}
		BenchLogger.sysout( "" + nullCounter + "/" + ArraySize + " Nullen");
	}

}
