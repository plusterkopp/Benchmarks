package de.icubic.mm.bench.benches;


import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ForkJoinPool.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import de.icubic.mm.bench.base.*;
import net.openhft.affinity.*;
import net.openhft.affinity.AffinityManager.*;

public class FibonacciForkAff extends RecursiveTask<Long> {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	public FibonacciForkAff( long n) {
		super();
		this.n = n;
	}


	static ForkJoinPool	fjp = new ForkJoinPool( getCPUCount(), createFJThreadFactory(), null, false);

	static long	fibonacci0( long n) {
		if ( n < 2) {
			return n;
		}
		return fibonacci0( n - 1) + fibonacci0( n - 2);
	}

	/**
	 * normalerweise benutzt ein {@link ForkJoinPool} eine Standard-{@link ThreadFactory}. Um wieder an alle lCPUs des
	 * Systems zu kommen, müssen wir Affinitäten benutzen und eine eigene {@link ThreadFactory} bauen
	 *
	 * @return
	 */
	private static ForkJoinWorkerThreadFactory createFJThreadFactory() {
		int	bound = 1;
		IAffinity aff = Affinity.getAffinityImpl();
		if ( aff instanceof IDefaultLayoutAffinity) {
			IDefaultLayoutAffinity idla = (IDefaultLayoutAffinity) aff;
			CpuLayout cpuLayout = idla.getDefaultLayout();
			if ( cpuLayout instanceof GroupedCpuLayout) {
				GroupedCpuLayout gCpuLayout = (GroupedCpuLayout) cpuLayout;
				bound = gCpuLayout.groups();
			}
		}
		if ( bound == 1) {	// haben wir nur eine Gruppe (also <= 64 CPUs), reicht die normale Factory
			return ForkJoinPool.defaultForkJoinWorkerThreadFactory;
		}

		final int thresh = bound;	// wird 2 bei zwei Gruppen
		IntBinaryOperator cycleCounter = ( a,  b) -> {
			int	sum = a+b;
			while ( sum >= thresh) {
				sum -= thresh;
			}
			return sum;
		};
		AtomicInteger	threadCounter = new AtomicInteger(0);
		AtomicInteger	groupCounter = new AtomicInteger( 0);
		// ThreadFactory, die jeden neuen Thread immer zuerst noch an die richtige Gruppe bindet. Wir gehen davon aus,
		// daß alle Gruppen gleich viele CPUs haben, und nutzen einen zyklischen Gruppenzähler
		ForkJoinWorkerThreadFactory	factory = new ForkJoinWorkerThreadFactory() {
			@Override
			public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
				ForkJoinWorkerThread t = new ForkJoinWorkerThread( pool) {
					@Override
					protected void onStart() {
						super.onStart();
						int	group = groupCounter.getAndAccumulate( 1, cycleCounter);	// threadsicherer zyklische Zähler
						// bionde an Gruppe
						AffinityManager.INSTANCE.bindToGroup(group);
						// Logging
						List<LayoutEntity> boundTo = AffinityManager.INSTANCE.getBoundTo( Thread.currentThread());
						BenchLogger.sysinfo( Thread.currentThread()  + " #" + threadCounter.incrementAndGet() + " bound to: " + boundTo.get( 0));
					}
				};
				return t;
			}
		};
		return factory;
	}

	private static int getCPUCount() {
		IAffinity aff = Affinity.getAffinityImpl();
		if ( aff instanceof IDefaultLayoutAffinity) {
			IDefaultLayoutAffinity idla = (IDefaultLayoutAffinity) aff;
			CpuLayout cpuLayout = idla.getDefaultLayout();
			return cpuLayout.cpus();
		}
		return 0;
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
		BenchLogger.sysinfo( "Warmup max " + getCPUCount() + " Threads (" + Runtime.getRuntime().availableProcessors() + ")");
		long	singleNS[] = getSingleThreadNanos( 20, 5e9);
		BenchLogger.sysinfo( "Warmup complete");
		singleNS = getSingleThreadNanos( fiboArg, 1e9);
		BenchLogger.sysinfo( "Single Thread Times complete");
		Result[] results = new Result[ fiboArg + 1];
		for ( int rekLimit = 2;  rekLimit <= fiboArg;  rekLimit++) {
			results[ rekLimit] = new Result();
			runWithRecursionLimit( rekLimit, fiboArg, singleNS[ rekLimit], results[ rekLimit]);
		}
		BenchLogger.sysout( "CSV results for Fibo " + fiboArg + "\n" + "RekLimit\t" + "Jobs ns\t" + "time ms");
		for ( int rekLimit = 2;  rekLimit <= fiboArg;  rekLimit++) {
			BenchLogger.sysout( rekLimit + "\t" + singleNS[ rekLimit] + "\t" + results[ rekLimit].durMS);
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
		FibonacciForkAff	task = new FibonacciForkAff( arg);
		long result = fjp.invoke( task);
		forks.set( task.forkCount);
		return result;
	}

	@Override
	protected Long compute() {
		// wenn Argument zu klein ist, daß sich aufteilen nicht mehr lohnt, dann berechne es ohne weitere Verteilung auf Jobs, aber innernoch rekursiv
		if ( n <= rekLimit) {
			return fibonacci0( n);
		}
		// Aufteilen wird als lohnenswert angesehen, teile also in einen kleineren Job (n-2) und einen größeren (n-1) auf
		FibonacciForkAff	ff1 = new FibonacciForkAff( n-1);
		FibonacciForkAff	ff2 = new FibonacciForkAff( n-2);
		ff1.fork();			// beginne den größeren Job als asynchronen Task im Pool
		long	r2 = ff2.compute();	// berechne den kleineren selbst
		long	r1 = ff1.join();			// dann warte, daß der große fertig ist
		forkCount = ff2.forkCount + ff1.forkCount + 1;
		return r1 + r2;					// fasse beide Teilergebnisse zusammen
	}

}
