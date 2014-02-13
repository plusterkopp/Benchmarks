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
public class NanoTimeTest {

	/**
	 * @param args
	 */
	public static void main( String[] args) {

//		testRollOver();

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
		IBenchRunnable nbench = new AbstractBenchRunnable( "nanoTime") {

			private int nruns = 550055;
			long[]	times = new long[ nruns + 1];

			public void run() {
				for ( int i = nruns; i > 0 ; i--) {
					times[ i] = System.nanoTime();
				}
			}

			public long getRunSize() {
				return nruns;
			}
		};
		IBenchRunnable cbench = new AbstractBenchRunnable( "currentTimeMillis") {

			private int nruns = 550055;
			long[]	times = new long[ nruns + 1];

			public void run() {
				for ( int i = nruns; i > 0 ; i--) {
					times[ i] = System.currentTimeMillis();
//					Date d = new Date( /* echte Zeit */);
				}
			}

			public long getRunSize() {
				return nruns;
			}
		};

		IBenchRunner	runner = new BenchRunner( nbench);
		runner.setRuntime( TimeUnit.SECONDS, 10);

		runner.setBenchRunner( lbench);
		runner.run();
		runner.printResults();
		double lruns = runner.getRunsPerSecond();

		runner.setEmptyLoops( lruns);
		runner.setBenchRunner( nbench);
		runner.run();
		runner.printResults();
		double nruns = runner.getRunsPerSecond();

		runner.setBenchRunner(cbench);
		runner.run();
		runner.printResults();
		double cruns = runner.getRunsPerSecond();

		System.out.println( cbench.getName() + "/" + nbench.getName() + " = " + cruns/nruns);
	}

	@SuppressWarnings("unused")
	private static void testRollOver() {
		Map<Long, Long> durs = new HashMap<Long, Long>();
		long	now = System.nanoTime();
		long	zero = now;
		long	length = 0;
		long	count = 0;
		while ( now >= zero) {
			long	nownow = System.nanoTime();
			count++;
			long dur = ( nownow - now);
			now = nownow;
			length += dur;
			Long	ct = durs.get( dur);
			if ( ct == null) {
				durs.put( dur, 1L);
			} else {
				durs.put( dur, ct + 1);
			}
		}
		System.out.print( "Rollover nach " + length + "ns (" + ( length / 1e6) + "ms");
		System.out.println( ", " + count + " Aufrufe, Zeit zwischen Aufrufen:");
		Map<Long, Long> dursSort =new TreeMap<Long, Long>(durs);
		for (Long dur : dursSort.keySet()) {
			System.out.print( dur + "ns: " + dursSort.get( dur) + ",  ");
		}
		System.out.println();
	}

}
