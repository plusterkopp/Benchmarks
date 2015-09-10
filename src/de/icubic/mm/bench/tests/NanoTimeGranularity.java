package de.icubic.mm.bench.tests;
import java.text.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import de.icubic.mm.bench.base.*;

@SuppressWarnings("boxing")
public class NanoTimeGranularity {

	public static void main( String[] args) throws InterruptedException {
		runNanos();
	}

	private static void runNanos() throws InterruptedException {
		final int	batchSize = 1000000;	//  runs per loop
		final double	timeGoalS =5;
		final int nThreads = Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 - 1);
		Thread[]	threads = new Thread[ nThreads];
		final SortedMap<Long, Long>[]	granularityMaps = new SortedMap[ nThreads];

		final AtomicInteger runs = new AtomicInteger( 0);
		final AtomicLong runTimeNS = new AtomicLong( 0);
		for ( int ti = 0;  ti < nThreads;  ti++) {
			final int index = ti;
			granularityMaps[ ti] = new TreeMap<Long, Long>();
			threads[ ti] = new Thread( "nanoTime granularitiy-" + index) {
				@Override
				public void run() {
					long	now = System.currentTimeMillis();
					int	lRuns = 0;
					long	lRunTimeNS = 0;
					long	endGoal = ( long) ( now + timeGoalS * 1000);
					while ( now < endGoal) {
						lRunTimeNS += fillMap( batchSize, granularityMaps[ index]);
						now = System.currentTimeMillis();
						lRuns += batchSize;
					}
					runs.addAndGet( lRuns);
					runTimeNS.addAndGet( lRunTimeNS);
				}
			};
		}
		for ( int ti = 0;  ti < nThreads;  ti++) {
			threads[ ti].start();
		}
		for ( int ti = 0;  ti < nThreads;  ti++) {
			threads[ ti].join();
		}
		final SortedMap<Long, Long>	granularityMap = new TreeMap<Long, Long>();
		for ( int ti = 0;  ti < nThreads;  ti++) {
			SortedMap<Long, Long> gm = granularityMaps[ ti];
			for ( Map.Entry<Long, Long>gmEntry : gm.entrySet()) {
				Long	dist = gmEntry.getKey();
				Long	occ = gmEntry.getValue();
				registerDistance( granularityMap, dist, occ, true);
			}
		}

		NumberFormat	nf = DecimalFormat.getNumberInstance();
		nf.setMaximumFractionDigits( 2);
		for ( Long diffToLastTime : granularityMap.keySet()) {
			long	occ = granularityMap.get( diffToLastTime);
			if ( occ > 1)
				BenchLogger.sysout( " G: " + diffToLastTime + "ns " + occ + " mal (" + nf.format( 100.0 * occ / runs.get()) + "%)");
		}
		final double runtimeNS = runTimeNS.get();
		BenchLogger.sysout( "Runs: " + runs.get() / 1e6 + " M in " + runtimeNS / 1e6 + " ms (" + ( runtimeNS / runs.get()) + " ns/Run in " + nThreads + " Threads)");
	}

	private static double fillMap( int batchSize, SortedMap<Long, Long> granularityMap) {
		long[]	nList = new long[ batchSize];
		for ( int i = 0;  i < batchSize;  i++)
			nList[ i] = System.nanoTime();

		long distance = nList[ batchSize-1] - nList[0];

		for ( int i = 1;  i < batchSize;  i++) {
			long	diffToLastTime = nList[ i] - nList[ i-1];
			registerDistance( granularityMap, diffToLastTime, 1, false);
		}

		return distance;
	}

	private static void registerDistance( SortedMap<Long, Long> granularityMap, long key, long increment, boolean coalesce) {
		long coalescedDiff = findDiff( granularityMap, key, coalesce);
		if ( coalescedDiff == - 1) {
			granularityMap.put( key, increment);
		} else {
			long occ = granularityMap.get( coalescedDiff);
			granularityMap.put( coalescedDiff, occ + increment);
		}
	}

	private static long findDiff( SortedMap<Long, Long> gMap, long keyOrNeighbor, boolean coalesce) {
		if ( gMap.containsKey( keyOrNeighbor))
			return keyOrNeighbor;
		if ( coalesce) {
			int	range = 2;
			for ( long neighbor = keyOrNeighbor - range;  neighbor <= keyOrNeighbor + range;  neighbor++) {
				if ( gMap.containsKey( neighbor))
					return neighbor;
			}
		}
		return -1;
	}

}
