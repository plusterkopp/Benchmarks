package Benchmarks;


import net.openhft.affinity.*;
import net.openhft.affinity.impl.LayoutEntities.*;
import org.openjdk.jmh.annotations.*;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ForkJoinPool.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class FibonacciForkAff extends RecursiveTask<Long> {

	static class Result {
		long	durNS;
		int	rekLimit;
	}

	static ThreadLocal<NumberFormat> NFInt_TL = ThreadLocal.withInitial( () -> {
		NumberFormat df = DecimalFormat.getIntegerInstance(Locale.US);
		df.setGroupingUsed( true);
		return df;
	});

	public FibonacciForkAff(long n) {
		super();
		this.n = n;
	}


	static ForkJoinPool	fjp = new ForkJoinPool( getCPUCount(), createFJThreadFactory(), null, false);

	private static AtomicLong forks = new AtomicLong( 0);

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
						System.out.println( Thread.currentThread()  + " #" + threadCounter.incrementAndGet() + " bound to: " + boundTo.get( 0));
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

	public static void main( String[] args) {
	}

	@Setup(Level.Trial)
	public void setup() {
		System.out.println( "Warmup max " + getCPUCount() + " Threads (" + Runtime.getRuntime().availableProcessors() + ")");
		long	singleNS[] = getSingleThreadNanos( 1000, 5e9);
		System.out.println( "Warmup complete");
		singleNS = getSingleThreadNanos( 1000, 3e9);
		int fiboArg = singleNS.length - 1;
		System.out.println( "Single Thread Times complete");
		Result[] results = new Result[ singleNS.length + 1];
		for ( int rekLimit = 2;  rekLimit <= fiboArg;  rekLimit++) {
			results[ rekLimit] = new Result();
			runWithRecursionLimit( rekLimit, fiboArg, singleNS[ rekLimit], results[ rekLimit]);
		}
		System.out.println( "CSV results for Fibo " + fiboArg + "\n" + "RekLimit\t" + "Jobs ns\t" + "time ms");
		NumberFormat nf = NFInt_TL.get();
		for ( int rekLimit = 2;  rekLimit <= fiboArg;  rekLimit++) {
			System.out.println( rekLimit + "\t" + nf.format( singleNS[ rekLimit]) + "\t" + nf.format( results[ rekLimit].durNS));
		}
	}

	private static long[] getSingleThreadNanos( final int maxMillis, final double minRuntimeNS) {
		ArrayList<Long> timesNS_AL = new ArrayList<>();
		int minIndex = 2;
		IntStream.range( 0, minIndex).forEach( i -> timesNS_AL.add( 0L));
		long nanos = 0;
		for ( int i = minIndex;  nanos < maxMillis * 1e6;  i++) {
			nanos = getSingleThreadNanos0( i, minRuntimeNS);
			timesNS_AL.add( nanos);
		}
		long[] result = new long[ timesNS_AL.size()];
		for( int i = 0;  i < result.length;  i++) {
			result[ i] = timesNS_AL.get( i);
		}
		return result;
	}

	private static long getSingleThreadNanos0(int arg, double minRuntimeNS) {
		long	start = System.nanoTime();
		long    result = fibonacci0( arg);
		long	end = System.nanoTime();
		double	durNS = end - start;
		long 	ntimes = 1;
		double  fact = 1;
		while ( durNS < minRuntimeNS) {
			long	oldNTimes = ntimes;
			if ( durNS > 0) {
				ntimes = Math.max( 1, ( long) ( oldNTimes * fact * minRuntimeNS / durNS));
			} else {
				ntimes *= 2;
			}
			start = System.nanoTime();
			for ( long i = 0;  i < ntimes;  i++) {
				result = fibonacci0( arg);
			}
			end = System.nanoTime();
			durNS = end - start;
			fact *= 1.1;
		}
		long resultNS = (long) (durNS / ntimes);
		NumberFormat nf = NFInt_TL.get();
		System.out.println( "Single Fib(" + arg + ")=" + nf.format( result)
				+ " in " + nf.format( resultNS) + " ns"
				+ " (" + nf.format( ntimes) + " loops in " + nf.format( durNS) + " ns)");
		return resultNS;
	}

	private static void runWithRecursionLimit( int r, int arg, long singleThreadNanos, Result result) {
		rekLimit = r;
		long	startNS = System.nanoTime();
		long	fiboResult = fibonacci( arg);
		long	endNS = System.nanoTime();
		// Steals zählen
		long	currentSteals = fjp.getStealCount();
		long	newSteals = currentSteals - stealCount;
		stealCount = currentSteals;
		long	forksCount = forks.getAndSet( 0);
		final long durNS = endNS - startNS;
		NumberFormat nf = NFInt_TL.get();
		System.out.println( "Fib(" + arg + ")=" + nf.format( fiboResult)
				+ " in " + nf.format( durNS) + " ns, recursion limit: " + r +
				" at " + ( singleThreadNanos / 1e6) + "ms, steals: " + newSteals + " forks " + forksCount);
		result.durNS = durNS;
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
		// wenn Argument zu klein ist, daß sich aufteilen nicht mehr lohnt, dann berechne es ohne weitere Verteilung auf Jobs, aber immernoch rekursiv
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
