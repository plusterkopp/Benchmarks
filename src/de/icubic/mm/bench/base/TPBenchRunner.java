package de.icubic.mm.bench.base;

import java.util.concurrent.*;

public class TPBenchRunner extends BenchRunner {

	private int nThreads;

	public TPBenchRunner( IBenchRunnable bench) {
		this( bench, Runtime.getRuntime().availableProcessors());
	}

	public TPBenchRunner( IBenchRunnable bench, int availableProcessors) {
		super( bench);
		nThreads = availableProcessors;
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		double emptyLoopNanos = 0;
//		System.out.print( "calibrate: " + benchRunnable.getName());
		// Kalibriere
		long nruns = 1;
		runNanosActual = runTP( nruns);
//		System.out.print( ", " + loopCount + "/" +  lnf.format( runNanosActual));
		// mindestens eine Sekunde, für Hotspot Warmup
		while ( runNanosActual < 1e9) {
			nruns *= 2;
			runNanosActual = runTP( nruns);
//			System.out.print( ", " + loopCount + "/" +  lnf.format( runNanosActual));
		}
//		System.out.println();
		// jetzt solange laufen lassen, daß gewünschte Zeit zusammenkommt
		final double f = ( double) runNanosDesired / runNanosActual;
		nruns *= f;
		if ( nruns < nThreads) {
			nruns = nThreads;
		}
		runNanosActual = runTP( nruns);
		loopCount = benchRunnable.getTotalRunSize( nruns);
		// dann leere Schleife abziehen, wenn bekannt -- und kleiner (JRockit)
		if ( emptyLoopsPerSecond > 0)
			emptyLoopNanos = Math.round( 1e9 / emptyLoopsPerSecond);
		emptyLoopNanos *= loopCount;
		if ( emptyLoopNanos < runNanosActual)
			runNanosActual -= emptyLoopNanos;
	}

	private long runTP( long nruns) {
		// aufräumen
		benchRunnable.reset();
		// TPE bauen
		BlockingQueue<Runnable>	queue = new LinkedBlockingQueue<Runnable>();
		ThreadPoolExecutor	tpe = new ThreadPoolExecutor( nThreads, nThreads, 1, TimeUnit.MINUTES, queue);
		long	start = System.nanoTime();
		for ( long r = nruns;  r > 0;  --r) {
			tpe.execute( benchRunnable);
		}
		tpe.shutdown();
		awaitTPE( tpe);
		long	end = System.nanoTime();
		long	nanos = end - start;
		if ( nanos >= 0) {
			return nanos;
		}
		System.out.println( "Nanotime Rollover: " + nruns);
		return 0;
	}

	private void awaitTPE( ThreadPoolExecutor tpe) {
		try {
			boolean finished = tpe.awaitTermination( 0, TimeUnit.SECONDS);
			if ( finished) {
				return;
			}
			int seconds = 1;
			while ( ! tpe.awaitTermination( seconds, TimeUnit.SECONDS)) {
				seconds *= 2;
			}
		} catch ( InterruptedException ie) {
		}
	}

}
