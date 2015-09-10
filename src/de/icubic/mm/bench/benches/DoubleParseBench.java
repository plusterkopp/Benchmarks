/*
 * Created on 13.08.2007
 *
 */
package de.icubic.mm.bench.benches;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;

import de.icubic.mm.bench.base.*;

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

	/**
	 * @param args
	 */
	public static void main( String[] args) {


		// Renner
		final IBenchRunnable dPBench = new AbstractBenchRunnable( "DoubleParse") {

			private final long nruns = 50000;
			public void run() {
				for ( int i = 0;  i < nruns;  i++) {
					for ( String value: valueA) {
//						FloatingDecimal	fd =FloatingDecimal.readJavaFormatString( value);
						double v = Double.valueOf( value);
//						fd.doubleValue();
					}
				}
			}

			public long getRunSize() {
				return nruns * valueL.size();
			}

			/* (non-Javadoc)
			 * @see de.icubic.mm.bench.base.AbstractBenchRunnable#getName()
			 */
			@Override
			public String getName() {
				return super.getName();
//				if ( useAQFormat)
//					return super.getName() + "AQ";
//				else
//					return super.getName() + "DF";
			}


		};

		setupStatics();

		Thread	t = new Thread() {
			@Override
			public void run() {
				IBenchRunner	runner = new BenchRunner( dPBench);
				runner.setRuntime( TimeUnit.SECONDS, 20);

				runner.setBenchRunner( dPBench);
				runner.run();
				runner.printResults();
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
