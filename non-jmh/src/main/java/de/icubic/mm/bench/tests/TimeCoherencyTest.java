package de.icubic.mm.bench.tests;

import de.icubic.mm.bench.base.*;

import java.text.*;

/**
 * test coherency between {@link System#currentTimeMillis()} and {@link System#nanoTime()}
 */
public class TimeCoherencyTest {

	static final int MAX_THREADS = Math.max( 1, Runtime.getRuntime().availableProcessors() - 1);
	static final long RUNTIME_NS = 1000000000L * 100;
	static final long BIG_OFFSET_MS = 2;

	static long	startNanos;
	static long	firstNanoOrigin;
	static {
		initNanos();
	}

	private static void initNanos() {
		long	millisBefore = System.currentTimeMillis();
		long	millisAfter;
		do {
			startNanos = System.nanoTime();
			millisAfter = System.currentTimeMillis();
		} while ( millisAfter != millisBefore);
		firstNanoOrigin = ( long) ( millisAfter - ( startNanos / 1e6));
	}

	static NumberFormat lnf = DecimalFormat.getNumberInstance();
	static {
		lnf.setMaximumFractionDigits( 3);
		lnf.setGroupingUsed( true);
	};

	static class TimeCoherency {
		long	firstOrigin;
		long	lastOrigin;
		long	numMismatchToLast = 0;
		long	numMismatchToFirst = 0;
		long	numMismatchToFirstBig = 0;
		long	numChecks = 0;

		public TimeCoherency( long firstNanoOrigin) {
			firstOrigin = firstNanoOrigin;
			lastOrigin = firstOrigin;
		}
	}

	public static void main( String[] args) {
		Thread[]	threads = new Thread[ MAX_THREADS];
		for ( int i = 0;  i < MAX_THREADS;  i++) {
			final int	fi = i;
			final TimeCoherency	tc = new TimeCoherency( firstNanoOrigin);
			threads[ i] = new Thread() {
				@Override
				public void run() {
					long	start = getNow( tc);
					long	firstOrigin = tc.lastOrigin;	// get the first origin for this thread
					BenchLogger.sysout( "Thread " + fi + " started at " + lnf.format( start) + " ns");
					long	nruns = 0;
					while ( getNow( tc) < RUNTIME_NS) {
						nruns++;
					}
					final long	runTimeNS = getNow( tc) - start;
					final long	originDrift = tc.lastOrigin - firstOrigin;
					nruns += 3;	// account for start and end call and the one that ends the loop
					final long skipped = nruns - tc.numChecks;
					BenchLogger.sysout( "Thread " + fi + " finished after " + lnf.format( nruns) + " runs in " + lnf.format( runTimeNS) + " ns (" + lnf.format( ( double) runTimeNS / nruns) + " ns/call) with"
							+ "\n\t" + lnf.format( tc.numMismatchToFirst) + " different from first origin (" + lnf.format( 100.0 * tc.numMismatchToFirst / nruns) + "%)"
							+ "\n\t" + lnf.format( tc.numMismatchToLast) + " jumps from last origin (" + lnf.format( 100.0 * tc.numMismatchToLast / nruns) + "%)"
							+ "\n\t" + lnf.format( tc.numMismatchToFirstBig) + " different from first origin by more than " + BIG_OFFSET_MS + " ms"
									+ " (" + lnf.format( 100.0 * tc.numMismatchToFirstBig / nruns) + "%)"
							+ "\n\t" + "total drift: " + lnf.format( originDrift) + " ms, " + lnf.format( skipped) + " skipped (" + lnf.format( 100.0 * skipped / nruns) + " %)");
				}};
			threads[ i].start();
		}
		try {
			for ( Thread thread : threads) {
				thread.join();
			}
		} catch ( InterruptedException ie) {};
	}

	public static long getNow( TimeCoherency coherency) {
		long	millisBefore = System.currentTimeMillis();
		long	now = System.nanoTime();
		if ( coherency != null) {
			checkOffset( now, millisBefore, coherency);
		}
		return now - startNanos;
	}

	private static void checkOffset( long nanoTime, long millisBefore, TimeCoherency tc) {
		long	millisAfter = System.currentTimeMillis();
		if ( millisBefore != millisAfter) {
			// disregard since thread may have slept between calls
			return;
		}
		tc.numChecks++;
		long	nanoMillis = ( long) ( nanoTime / 1e6);
		long	nanoOrigin = millisAfter - nanoMillis;
		long	oldOrigin = tc.lastOrigin;
		if ( oldOrigin != nanoOrigin) {
			tc.lastOrigin = nanoOrigin;
			tc.numMismatchToLast++;
		}
		if ( tc.firstOrigin != nanoOrigin) {
			tc.numMismatchToFirst++;
		}
		if ( Math.abs( tc.firstOrigin - nanoOrigin) > BIG_OFFSET_MS) {
			tc.numMismatchToFirstBig ++;
		}
	}
}
