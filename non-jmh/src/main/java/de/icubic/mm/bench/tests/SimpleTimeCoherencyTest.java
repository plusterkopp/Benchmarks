package de.icubic.mm.bench.tests;

import de.icubic.mm.bench.base.*;

public class SimpleTimeCoherencyTest {
	public static void main( String[] args) {
		final long anchorNanos = System.nanoTime();

		long lastNanoTime = System.nanoTime();
		long accumulatedNanos = lastNanoTime - anchorNanos;

		long numCallsSinceAnchor = 1L;

		for ( int i = 0; i < 100; i++) {
			TestRun testRun = new TestRun( accumulatedNanos, lastNanoTime);

			Thread t = new Thread( testRun);
			t.start();

			try {
				t.join();
			} catch ( InterruptedException ie) {
			}

			lastNanoTime = testRun.lastNanoTime;
			accumulatedNanos = testRun.accumulatedNanos;
			numCallsSinceAnchor += testRun.numCallsToNanoTime;
		}

		BenchLogger.sysout( "" + numCallsSinceAnchor);
		BenchLogger.sysout( "" + accumulatedNanos);
		BenchLogger.sysout( "" + ( lastNanoTime - anchorNanos));
	}

	static class TestRun implements Runnable {
		volatile long accumulatedNanos;
		volatile long lastNanoTime;
		volatile long numCallsToNanoTime;

		TestRun( long acc, long last) {
			accumulatedNanos = acc;
			lastNanoTime = last;
		}

		@Override
		public void run() {
			long lastNanos = lastNanoTime;
			long currentNanos;

			do {
				currentNanos = System.nanoTime();
				accumulatedNanos += currentNanos - lastNanos;
				lastNanos = currentNanos;
				numCallsToNanoTime++;
			} while ( currentNanos - lastNanoTime <= 100000000L);

			lastNanoTime = lastNanos;
		}
	}
}