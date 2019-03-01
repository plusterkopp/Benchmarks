/*
 * Created on 13.08.2007
 *
 */
package de.icubic.mm.bench.benches;

import java.util.concurrent.*;

import de.icubic.mm.bench.base.*;

/**
 * <p>@author ralf
 *
 */
public class StacktraceTest {

	static int	stackDepth;

	/**
	 * @param args
	 */
	public static void main( String[] args) {

		// leer loop
		final IBenchRunnable lbench = new AbstractBenchRunnable( "loop") {

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

		// Exception anlegen
		final IBenchRunnable stackBench = new AbstractBenchRunnable( "Stacktrace") {

			private final int nruns = 1000;

			public void run() {
				try {
					for ( int i = nruns;  i > 0;  i--) {
						Throwable e = createException( stackDepth);
						e.toString();
					}
				} catch ( Exception e) {
					BenchLogger.syserr( "", e);
					return;
				}
			}

			private Throwable createException( int depth) {
				if ( depth <= 0)
					return new Throwable();
				return createException( depth - 1);
			}

			public long getRunSize() {
				return nruns;
			}

			@Override
			public String getName() {
				return super.getName() + "-" + stackDepth;
			}
		};

		// keine Exception anlegen
		final IBenchRunnable stackDummy = new AbstractBenchRunnable( "Stackdummy") {

			private final int nruns = 1000;

			public void run() {
				try {
					for ( int i = nruns;  i > 0;  i--) {
						Exception	e = createException( stackDepth);
						if ( e != null)
							e.toString();
					}
				} catch ( Exception e) {
					BenchLogger.syserr( "", e);
					return;
				}
			}

			private Exception createException( int depth) {
				if ( depth <= 0)
					return null;
				return createException( depth - 1);
			}

			public long getRunSize() {
				return nruns;
			}

			@Override
			public String getName() {
				return super.getName() + "-" + stackDepth;
			}
		};

		Thread	t = new Thread() {
			@Override
			public void run() {
				IBenchRunner	runner = new BenchRunner( stackBench);
				runner.setRuntime( TimeUnit.SECONDS, 4);

				runner.setBenchRunner( lbench);
				runner.run();
				runner.printResults();
				double lruns = runner.getRunsPerSecond();

				runner.setEmptyLoops( lruns);
				stackDepth = 0;
				runner.setBenchRunner( stackDummy);
				runner.run();
				runner.printResults();
				double runsD0 = runner.getRunsPerSecond();
				runner.setBenchRunner( stackBench);
				runner.run();
				runner.printResults();
				double runsT0 = runner.getRunsPerSecond();
				BenchLogger.sysout( stackBench.getName() + " = " + runsD0/runsT0);

				for ( int depth = 1;  depth < 1000;  depth *= 2) {
					stackDepth = depth;
					runner.setBenchRunner( stackDummy);
					runner.run();
					runner.printResults();
					double nrunsD = runner.getRunsPerSecond();
					runner.setBenchRunner( stackBench);
					runner.run();
					runner.printResults();
					double nrunsT = runner.getRunsPerSecond();

					BenchLogger.sysout( "D0/D" + depth + " = " + runsD0/nrunsD + " (" + runsD0/nrunsD/depth + ")");
					BenchLogger.sysout( "T0/T" + depth + " = " + runsT0/nrunsT + " (" + runsT0/nrunsT/depth + ")");
					BenchLogger.sysout( "D" + depth + "/T" + depth + " = " + nrunsD/nrunsT);
				}
			}
		};
		t.start();
		try {
			t.join();
		} catch ( InterruptedException e) {
			BenchLogger.syserr( "", e);
		}
	}

}
