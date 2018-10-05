/*
 * Created on 13.08.2007
 *
 */
package de.icubic.mm.bench.benches;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.bench.benches.DoubleFormatTest.*;
import de.icubic.mm.communication.util.*;

/**
 * <p>@author ralf
 *
 */
public class QuoteDoubleFormatBench {

	static NumberFormat	nfnf = BenchLogger.LNF;

	static double[]		values = {
			99.9968,
			12345.67, 12345.5, 12345,
			1234.565, 1234.56, 1234.5, 1234,
			123.4567, 123.455, 123.45500001, 123.4550000000000001, 123.45, 123.5, 123,
			12.34567, 12.3456, 12.345, 12.34, 12.3, 12, 12.03, 12.005, 12.004,
			1.234567, 1.23456, 1.2345, 1.235, 1.23, 1.2, 1, 1.01, 1.001, 1.0003,
			0.1234567, 0.123456, 0.12345, 0.1235, 0.123, 0.12, 0.5,
			0.01234567, 0.0123456, 0.012345, 0.01234, 0.0125, 0.012, 0.01,
			0.001234567, 0.00123456, 0.0012345, 0.001234, 0.00123, 0.0015, 0.001,
		};

	static String	strings[];

	private static void setupStatics() {
		List<Double>	plusMinusValues = new ArrayList<Double>();
		DoubleFormatTest.setupValueList( plusMinusValues, values);
		values = new double[ plusMinusValues.size()];
		for ( int i = 0;  i < plusMinusValues.size();  i++) {
			values[ i] = plusMinusValues.get( i).doubleValue();
		}
		strings = new String[ values.length];
		for ( int i = 0;  i < strings.length;  i++) {
			strings[ i] = "" + values[ i];
		}
	}

	static RunMode	runMode;

	/**
	 * @param args
	 */
	public static void main( String[] args) {

		class ModeBenchRunnable extends AbstractBenchRunnable {

			private final RunMode mode;
			private final int nruns = 1;

			public ModeBenchRunnable( String name, RunMode mode) {
				super( name);
				this.mode = mode;
			}

			@Override
			public void run() {
				switch ( mode) {
				case SC:
					for ( int i = 0;  i < nruns;  i++) {
						for ( String s: strings) {
							Double.parseDouble( s);
						}
					}
					break;
				default:
					for ( int i = 0;  i < nruns;  i++) {
						for ( double value: values) {
							mode.getStringValue( value);
						}
					}
				}
			}

			@Override
			public long getRunSize() {
				return nruns * values.length;
			}

			/* (non-Javadoc)
			 * @see de.icubic.utils.bench.base.AbstractBenchRunnable#getName()
			 */
			@Override
			public String getName() {
				return super.getName() + mode;
			}
		};

		setupStatics();

		Thread	t = new Thread() {
			@Override
			public void run() {
				try {
					NumberFormat nf = DecimalFormat.getNumberInstance();
					nf.setMaximumFractionDigits( 3);

					int nCores = Runtime.getRuntime().availableProcessors();
					// Warmup
					final int warmupS = 30;
					ThreadPoolExecutor	tpe = new AddThreadBeforeQueuingThreadPoolExecutor( 0, nCores, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), 0);
					for ( final RunMode rm: RunMode.values()) {
						tpe.execute( new Runnable() {
							@Override
							public void run() {
								ModeBenchRunnable qdfbench = new ModeBenchRunnable( "QuoteDoubleFormatBench", rm);
								IBenchRunner	runner = new BenchRunner( qdfbench);
								runner.setRuntime( TimeUnit.SECONDS, warmupS);
								runner.run();
							}
						});
					}
					tpe.shutdown();
//					sysout( "Shutdown Warmup");
					boolean ok = tpe.awaitTermination( 5 + 2 * warmupS, TimeUnit.SECONDS);
					BenchLogger.sysout( "Warmup Complete " + ( ok ? "success" : "timeout"));

					final int	runtimeS = 120;
					int nThreads = nCores / 2;
					// Ernst
					tpe = new AddThreadBeforeQueuingThreadPoolExecutor( 0, nThreads , 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), 0);
					for ( final RunMode rm: RunMode.values()) {
						tpe.execute( new Runnable() {
							@Override
							public void run() {
								try {
									ModeBenchRunnable qdfbench = new ModeBenchRunnable( "QuoteDoubleFormatBench", rm);
									IBenchRunner	runner = new BenchRunner( qdfbench);
									runner.setRuntime( TimeUnit.SECONDS, runtimeS);
//									sysout( "Run " + qdfbench.getName());
									runner.run();
									runner.printResults();
									if ( rm != RunMode.SC)
										BenchRunner.addToComparisonList( qdfbench.getName(), runner.getRunsPerSecond());
								} catch ( Exception e) {
									BenchLogger.sysout( "Exception");
									BenchLogger.syserr( "", e);
								}
							}
						});
					}
					tpe.shutdown();
//					sysout( "Shutdown Runs");
					ok = tpe.awaitTermination( 5 + 2 * runtimeS, TimeUnit.HOURS);
//					sysout( "Runs Terminated " + ( ok ? "success" : "timeout"));
					BenchRunner.printComparisonList();
				} catch ( InterruptedException ie) {}
			}
		};
		t.start();
		try {
			t.join();
		} catch (InterruptedException e) {
			BenchLogger.syserr( "", e);
		}
		System.exit( 0);
	}


}
