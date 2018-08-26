package misc;

import java.util.*;

import gnu.trove.set.hash.*;

class SpeedTest {
	public static void main( String[] args) {

		Runnable	troveRunner = () -> {
			int iterations = 10_000_000;
			TIntHashSet counts = new TIntHashSet( 2 * iterations);

			for ( int i = 0; i < iterations; i++) {
				counts.add( i);
			}
			if ( counts.size() < 10) {
				System.out.println( counts.size());
			}
		};

		Runnable plainRunner = () -> {
			int iterations = 10000000;
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
		long totalTime;

		for ( int i = 0;  i < runs;  i++) {
			runner.run();
		}

		totalTime = System.currentTimeMillis() - startTime;
		System.out.println( marker + "(" + runs + ") TOTAL TIME = " + ( totalTime / ( runs * 1000f)));
	}

}
