package de.icubic.mm.bench.benches;


import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import de.icubic.mm.bench.base.*;

public class FibonacciFork extends RecursiveTask<Long> {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	public FibonacciFork( long n) {
		super();
		this.n = n;
	}

	static ForkJoinPool	fjp = new ForkJoinPool( Runtime.getRuntime().availableProcessors());

	static long	fibonacci0( long n) {
		if ( n < 2) {
			return n;
		}
		return fibonacci0( n - 1) + fibonacci0( n - 2);
	}

	static int	rekLimit = 8;

	private static long stealCount;

	long	n;

	private long forkCount;

	private static AtomicLong forks = new AtomicLong( 0);

	static class Result {
		long	durMS;
		int	rekLimit;
	}

	public static void main( String[] args) {

		int fiboArg = 49;
		BenchLogger.sysinfo( "Warmup");
		long	singleNS[] = getSingleThreadNanos( 20, 5e9);
		BenchLogger.sysinfo( "Warmup complete");
		singleNS = getSingleThreadNanos( fiboArg, 1e9);
		BenchLogger.sysinfo( "Single Thread Times complete");
		Result[] results = new Result[ fiboArg + 1];
		for ( int rekLimit = 2;  rekLimit <= fiboArg;  rekLimit++) {
			results[ rekLimit] = new Result();
			runWithRecursionLimit( rekLimit, fiboArg, singleNS[ rekLimit], results[ rekLimit]);
		}
		System.out.println( "CSV results for Fibo " + fiboArg + "\n" + "RekLimit\t" + "Jobs ns\t" + "time ms");
		for ( int rekLimit = 2;  rekLimit <= fiboArg;  rekLimit++) {
			System.out.println( rekLimit + "\t" + singleNS[ rekLimit] + "\t" + results[ rekLimit].durMS);
		}
	}

	private static long[] getSingleThreadNanos( final int n, final double minRuntimeNS) {
		final long timesNS[] = new long[ n + 1];
		ExecutorService	es = Executors.newFixedThreadPool( Math.max( 1, Runtime.getRuntime().availableProcessors() / 8));
		for ( int i = 2;  i <= n;  i++) {
			final int arg = i;
			Runnable runner = new Runnable() {
				@Override
				public void run() {
					long	start = System.nanoTime();
					long result = fibonacci0( arg);
					long	end = System.nanoTime();
					double	durNS = end - start;
					long 		ntimes = 1;
					double fact = 1;
					while ( durNS < minRuntimeNS) {
						long	oldNTimes = ntimes;
						if ( durNS > 0) {
							ntimes = Math.max( 1, ( long) ( oldNTimes * fact * minRuntimeNS / durNS));
						} else {
							ntimes *= 2;
						}
//						NumberFormat nf = BenchLogger.lnf;
//						BenchLogger.sysinfo( "single for " + arg + " took " + nf.format( durNS / oldNTimes) + " ns/loop, starting " + nf.format( ntimes) + " loops");
						start = System.nanoTime();
						for ( long i = 0;  i < ntimes;  i++) {
							result = fibonacci0( arg);
						}
						end = System.nanoTime();
						durNS = end - start;
						fact *= 1.1;
					}
					timesNS[ arg] = ( long) ( durNS / ntimes);
					BenchLogger.sysinfo( "Single Fib(" + arg + ")=" + result + " in " + ( timesNS[ arg] / 1e6) + "ms (" + ntimes + " loops in " + (durNS / 1e6)
							+ " ms)");
				}
			};
			es.execute( runner);
		}
		es.shutdown();
		try {
			es.awaitTermination( 1, TimeUnit.HOURS);
		} catch ( InterruptedException e) {
			BenchLogger.sysinfo( "Single Timeout");
		}
		return timesNS;
	}

	private static void runWithRecursionLimit( int r, int arg, long singleThreadNanos, Result result) {
		rekLimit = r;
		long	start = System.currentTimeMillis();
		long	fiboResult = fibonacci( arg);
		long	end = System.currentTimeMillis();
		// Steals zählen
		long	currentSteals = fjp.getStealCount();
		long	newSteals = currentSteals - stealCount;
		stealCount = currentSteals;
		long	forksCount = forks.getAndSet( 0);
		final long durMS = end-start;
		BenchLogger.sysinfo( "Fib(" + arg + ")=" + fiboResult + " in " + durMS + "ms, recursion limit: " + r +
				" at " + ( singleThreadNanos / 1e6) + "ms, steals: " + newSteals + " forks " + forksCount);
		result.durMS = durMS;
		result.rekLimit = r;
	}

	static long fibonacci( final long arg) {
		FibonacciFork	task = new FibonacciFork( arg);
		long result = fjp.invoke( task);
		forks.set( task.forkCount);
		return result;
	}

	@Override
	protected Long compute() {
		if ( n <= rekLimit) {
			return fibonacci0( n);
		}
		FibonacciFork	ff1 = new FibonacciFork( n-1);
		FibonacciFork	ff2 = new FibonacciFork( n-2);
		ff1.fork();
		long	r2 = ff2.compute();
		long	r1 = ff1.join();
		forkCount = ff2.forkCount + ff1.forkCount + 1;
		return r1 + r2;
	}

}
