/*
 * Created on 13.08.2007
 *
 */
package de.icubic.mm.bench.benches;

import java.util.*;
import java.util.concurrent.*;

import de.icubic.mm.bench.base.*;

/**
 * <p>@author ralf
 *
 */
public class StreamReduceBench {

	private static final int MaxIndex = 50 * 1000000;

	private static abstract class StreamRunnable extends AbstractBenchRunnable {

		public StreamRunnable( String string) {
			super( string);
		}

		protected int nruns = 1;

		ArrayList<Integer> nums;
		int	sum;

		@Override
		public long getRunSize() {
			return nruns * MaxIndex;
		}

		@Override
		public void setup() {
			super.setup();
			nums = new ArrayList<>( MaxIndex);
		    for (int i = 1; i < MaxIndex; i++) {
		    	nums.add( i);
		    }
		}
	}

	private static abstract class AStreamRunnable extends StreamRunnable {

		public AStreamRunnable( String string) {
			super( string);
		}

		int[] nums;
		int	sum;

		@Override
		public long getRunSize() {
			return nruns * MaxIndex;
		}

		@Override
		public void setup() {
			nums = new int[ MaxIndex];
		    for (int i = 0; i < MaxIndex; i++) {
		    	nums[ i] = 1;
		    }
		}
	}

	/**
	 * @param args
	 */
	public static void main( String[] args) {

		IBenchRunnable lbench = new AbstractBenchRunnable( "loop") {

			private final int nruns = 550055;

			@Override
			public void run() {
				for ( int i = nruns; i > 0 ; i--) {
//					@SuppressWarnings("unused") long time = System.nanoTime();
				}
			}

			@Override
			public long getRunSize() {
				return nruns;
			}
		};

		IBenchRunnable	srBench = new StreamRunnable( "SR") {
			@Override
			public void run() {
				sum = 0;
				for ( int i = nruns; i > 0; i--) {
					sum = nums.stream().reduce( 0, Integer::sum);
				}
			}
		};

		IBenchRunnable psBench = new StreamRunnable( "PSR") {
			@Override
			public void run() {
				sum = 0;
				for ( int i = nruns; i > 0; i--) {
					sum = nums.parallelStream().reduce( 0, Integer::sum);
				}
			}
		};

		IBenchRunnable asBench = new AStreamRunnable( "ASR") {
			@Override
			public void run() {
				sum = 0;
				for ( int i = nruns; i > 0; i--) {
					sum = Arrays.stream( nums).reduce( 0, Integer::sum);
				}
			}
		};

		IBenchRunnable apsBench = new AStreamRunnable( "APSR") {
			@Override
			public void run() {
				sum = 0;
				for ( int i = nruns; i > 0; i--) {
					sum = Arrays.stream( nums).parallel().reduce( 0, Integer::sum);
				}
			}
		};

		IBenchRunner	runner = new BenchRunner( lbench);
		runner.setRuntime( TimeUnit.SECONDS, 10);
		runner.run();
		runner.printResults();
		double lruns = runner.getRunsPerSecond();
		runner.setEmptyLoops( lruns);

		IBenchRunnable[]	benches = { srBench, psBench, asBench, apsBench};
		for ( IBenchRunnable bench : benches) {
			System.gc();
			runner.setBenchRunner( srBench);
			runner.run();
			runner.printResults();
			BenchRunner.addToComparisonList( bench.getName(), runner.getRunsPerSecond());
		}
		BenchRunner.printComparisonList();
	}

}
