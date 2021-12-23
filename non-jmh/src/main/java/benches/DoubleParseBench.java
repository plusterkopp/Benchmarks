/*
 * Created on 13.08.2007
 *
 */
package benches;

import de.icubic.mm.bench.base.*;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * <p>@author ralf
 *
 */
public class DoubleParseBench {



	private static List<String>	valueL;
	private static String[]	valueA;

	private static void setupStatics() {
		double[]		values = {
				12345.67, 12345.5, 12345,
				1234.565, 1234.56, 1234.5, 1234,
				123.4567, 123.455, 123.45, 123.5, 123,
				12.34567, 12.3456, 12.345, 12.34, 12.3, 12, 12.03, 12.005, 12.004,
				1.234567, 1.23456, 1.2345, 1.235, 1.23, 1.2, 1, 1.01, 1.001, 1.0003,
				0.1234567, 0.123456, 0.12345, 0.1235, 0.123, 0.12, 0.5,
				0.01234567, 0.0123456, 0.012345, 0.01234, 0.0125, 0.012, 0.01,
				0.001234567, 0.00123456, 0.0012345, 0.001234, 0.00123, 0.0015, 0.001,
			};
		valueL = new ArrayList<String>();
		NumberFormat	nf = DecimalFormat.getNumberInstance( Locale.US);
		nf.setGroupingUsed( false);
		nf.setMaximumFractionDigits( 3);
		for ( double d : values) {
			valueL.add( nf.format( d));
		}
		valueA = valueL.toArray( new String[ valueL.size()]);
	}

	static boolean	useAQFormat = true;

	static void runWith(IBenchRunner runner, IBenchRunnable bench, int seconds) {
		runner.setRuntime( TimeUnit.SECONDS, seconds);
		runner.setBenchRunner( bench);
		runner.run();
		runner.printResults();
	}

	// Renner
	static final IBenchRunnable dPBench = new AbstractBenchRunnable("Double.valueOf1") {
		public void run() {
			for (String value : valueA) {
				double v = Double.valueOf(value);
			}
		}

		public long getRunSize() {
			return valueL.size();
		}

		/* (non-Javadoc)
		 * @see de.icubic.utils.bench.base.AbstractBenchRunnable#getName()
		 */
		@Override
		public String getName() {
			return super.getName();
		}
	};

	/**
	 * @param args
	 */
	public static void main( String[] args) {

		setupStatics();

		Thread	t = new Thread() {
			@Override
			public void run() {
				IBenchRunner	runner = new BenchRunner(dPBench);

				BenchLogger.sysinfo( "warmup 10s");
				runWith( runner, dPBench, 10);

				BenchLogger.sysinfo( "run 20s");
				runWith( runner, dPBench, 20);
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
