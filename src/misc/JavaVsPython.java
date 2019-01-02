package misc;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import gnu.trove.set.hash.*;

class SpeedTest {

	static int iterations = 10_000_000;

	public static void main( String[] args) {

		Runnable	troveRunner = () -> {
			TIntHashSet counts = new TIntHashSet( 2 * iterations);

			for ( int i = 0; i < iterations; i++) {
				counts.add( i);
			}
			if ( counts.size() < 10) {
				System.out.println( counts.size());
			}
		};

		Runnable plainRunner = () -> {
			HashSet counts = new HashSet( 2 * iterations);

			for ( int i = 0; i < iterations; i++) {
				counts.add( i);
			}
			if ( counts.size() < 10) {
				System.out.println( counts.size());
			}
		};

		run( 1, "First Plain ", plainRunner);
		run( 1, "First Trove ", troveRunner);

		run( 5, "Warmup Plain ", plainRunner);
		run( 5, "Warmup Trove ", troveRunner);

		run( 20, "Plain ", plainRunner);
		run( 100, "Trove ", troveRunner);

	}

	private static void run( int runs, String marker, Runnable runner) {
		System.gc();
		long startTime = System.currentTimeMillis();
		long totalTimeMS;

		for ( int i = 0;  i < runs;  i++) {
			runner.run();
		}

		totalTimeMS = System.currentTimeMillis() - startTime;
		double totalTimeNS = totalTimeMS * 1_000_000;
		NumberFormat nf = DecimalFormat.getNumberInstance();
		nf.setGroupingUsed( true);
		nf.setMaximumFractionDigits( 2);
		System.out.println( marker + "(" + runs + ") "
				+ nf.format( totalTimeNS / ( runs * iterations)) + " ns/iteration, total = " + ( totalTimeMS / 1000f) + " s");
	}

}
