package benches;


import de.icubic.mm.bench.base.*;
import net.openhft.affinity.*;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ForkJoinPool.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

public class FibonacciForkAff extends RecursiveTask<Long> {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	public static ThreadLocal<NumberFormat> NFInt_TL = ThreadLocal.withInitial( () -> {
		NumberFormat df = DecimalFormat.getIntegerInstance(Locale.US);
		df.setGroupingUsed( true);
		return df;
	});
	public static ThreadLocal<NumberFormat> NFFlt_TL = ThreadLocal.withInitial( () -> {
		NumberFormat df = DecimalFormat.getNumberInstance( Locale.US);
		df.setGroupingUsed( true);
		df.setMinimumFractionDigits( 2);
		return df;
	});

	static ForkJoinPool	fjp = new ForkJoinPool( getCPUCount(), createFJThreadFactory(), null, false);

	public static long fibonacciNonRec(int n) {
		if ( n < 2) {
			return n;
		}

		long fib = 1;
		long fib1 = 1;  // 1 davor
		long fib2 = 1;  // 2 davor

		for ( int i = 3;  i <= n;  i++) {
			fib =  fib1 + fib2;
			fib2 = fib1;
			fib1 = fib;
		}
		return fib;
	}

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
						// binde an Gruppe
						AffinityManager.getInstance().bindToGroup(group);
						// Logging
//						List<LayoutEntity> boundTo = AffinityManager.INSTANCE.getBoundTo( Thread.currentThread());
//						BenchLogger.sysinfo( Thread.currentThread()  + " #" + threadCounter.incrementAndGet() + " bound to: " + boundTo.get( 0));
					}
				};
				return t;
			}
		};
		return factory;
	}

	public static int getCPUCount() {
		IAffinity aff = Affinity.getAffinityImpl();
		if ( aff instanceof IDefaultLayoutAffinity) {
			IDefaultLayoutAffinity idla = (IDefaultLayoutAffinity) aff;
			CpuLayout cpuLayout = idla.getDefaultLayout();
			return cpuLayout.cpus();
		}
		return 0;
	}

	public static void showLayout() {
		IAffinity aff = Affinity.getAffinityImpl();
		if ( aff instanceof IDefaultLayoutAffinity) {
			IDefaultLayoutAffinity idla = (IDefaultLayoutAffinity) aff;
			CpuLayout cpuLayout = idla.getDefaultLayout();
			BenchLogger.sysout( cpuLayout.toString());
		} else {
			BenchLogger.sysout( "no cpu layout");
		}
	}


	public static int	rekLimit = 8;

	private static long stealCount;

	private static AtomicLong forks = new AtomicLong( 0);

	public static class Result {
		public long	durNS;
		public double speedup;
		public double timeInJobsPerc;
		public double singleJobNSAvg;
		int	rekLimit;
	}

	static int timeGoalNS = 1_000_000_000;


	public static void main( String[] args) {

		showLayout();

		int[] fiboArgA = { 0};
		BenchLogger.sysinfo( "Warmup max " + getCPUCount() + " Threads (" + Runtime.getRuntime().availableProcessors() + ")");
		int[] max20 = { 20};
		long	singleNS[] = getSingleThreadNanos( max20, 1e9);
		BenchLogger.sysinfo( "Warmup complete");
		singleNS = getSingleThreadNanos( fiboArgA, 2 * 1e9);
		BenchLogger.sysinfo( "Single Thread Times complete");
		Result[] results = new Result[ fiboArgA[ 0] + 1];
		for ( int rekLimit = 2;  rekLimit <= fiboArgA[ 0];  rekLimit++) {
			results[ rekLimit] = new Result();
			runWithRecursionLimit( rekLimit, fiboArgA[ 0], singleNS, results[ rekLimit]);
		}
		BenchLogger.sysout( "CSV results for Fibo " + fiboArgA[ 0] + "\n" + "RekLimit\t" + "Jobs ns\t" + "time ms");
		NumberFormat nfi = NFInt_TL.get();
		NumberFormat nfd = NFFlt_TL.get();
		for ( int rekLimit = 2;  rekLimit <= fiboArgA[ 0];  rekLimit++) {
			Result result = results[rekLimit];
			System.out.println( rekLimit + "\t"
				+ nfd.format( result.singleJobNSAvg) + "\t"
				+ nfi.format( result.durNS) + "\t"
				+ nfd.format( result.timeInJobsPerc) + "%\t"
				+ nfd.format( result.speedup) + "\t"
			);
		}
	}

	public static long[] getSingleThreadNanos( int nMaxA[], final double minRuntimeNS) {
		List<Long> timesNS = new ArrayList<>();
		List<Future<Long>>  futures = new ArrayList<>();
		int nThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
		ForkJoinPool	fjp = new ForkJoinPool( nThreads);
		for ( int i = 0;  ;  i++) {
			final int n = i;
			Callable<Long> runner = new Callable<Long>() {
				@Override
				public Long call() {
					long	start = System.nanoTime();
					long result = fibonacci0( n);
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
							result = fibonacci0( n);
						}
						end = System.nanoTime();
						durNS = end - start;
						fact *= 1.1;
					}
					long durNSRel = (long) (durNS / ntimes);
					timesNS.add( durNSRel);
					NumberFormat nf = NFInt_TL.get();
					BenchLogger.sysinfo( "Single Fib(" + n + ")=" + nf.format( result)
						+ " in " + nf.format( durNSRel) + " ns"
						+ " (" + nf.format( ntimes) + " loops in " + nf.format( durNS) + " ns)");
					return durNSRel;
				}
			};
			Future<Long> future = fjp.submit(runner);
			futures.add( future);
			// Warmup? Dann haben wir schon einen Wunschwert in nMaxA und brechen ab, wenn wir den erreicht haben
			if ( nMaxA[ 0] > 0 && i > nMaxA[ 0]) {
				break;
			}
			// wenn wir den nMax erst rausfinden sollen (so daß fibo(n) länger als x Nanos braucht, brauchen wir ein anderes Abbruchkriterium:
			// wir schauen, ob der ExecutorService voll ist und holen dann den ältesten Future aus unserer Liste raus
			// (der am schnellsten fertig werden sollte). Dann warten wir den ab und gucken, ob die Laufzeit dort groß genug war.
			// Auf diese Weise warten wir zwar noch weitere ab, aber das ist OK.
			if ( fjp.hasQueuedSubmissions()) {
				try {
					// dann result abwarten und auswerten
					Future<Long> toCheck = futures.remove( 0);
					long result = toCheck.get();
					if ( result > timeGoalNS) {
						nMaxA[ 0] = i;
						break;
					}
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				} catch (ExecutionException e) {
					throw new RuntimeException(e);
				}
			}
		}
		fjp.shutdownNow();
		try {
			fjp.awaitTermination( 1, TimeUnit.HOURS);
		} catch ( InterruptedException e) {
			BenchLogger.sysinfo( "Single Timeout");
		}
		long result[] = new long[ timesNS.size()];
		for ( int i = 0;  i < result.length;  i++) {
			result[ i] = timesNS.get( i);
		}
		return result;
	}

	public static void runWithRecursionLimit(int r, int n, long singleNS[], Result result) {
		rekLimit = r;
		long	startNS = System.nanoTime();
		long	fiboResult;
		long	endNS;
		int	loops = 0;
		do {
			loops++;
			fiboResult = fibonacci( n);
			endNS = System.nanoTime();
		} while ( endNS - startNS < timeGoalNS);
		// Steals zählen
		long	currentSteals = fjp.getStealCount();
		long	newSteals = currentSteals - stealCount;
		stealCount = currentSteals;
		long	forksCount = forks.getAndSet( 0);
		final long durNS = endNS - startNS;
		long stealsPerLoop = newSteals / loops;
		long forksPerLoop = forksCount / loops;
		// wie oft waren wir direkt am rekLimit
		long stopsPerLoop0 = fibonacciNonRec( n - r + 1);
		// wie oft waren wir eins unter dem rekLimit
		long stopsPerLoop1 = fibonacciNonRec( n - r);
		long durNSPerLoop = durNS / loops;
		// wir nehmen an, daß alle CPUs ausgelastet waren (was bei sehr vielen Jobs auch zutreffen sollte), dann hatten wir so viel CPU Zeit insgesamt
		long totalCPUNSPerLoop = getCPUCount() * durNSPerLoop;
		// davon haben wir so viel Zeit in den MiniJobs verbracht, der Rest war Overhead und join-Aufwand
		long nonRekNS = singleNS[ r] * stopsPerLoop0 + singleNS[ r-1] * stopsPerLoop1;
		double singleJobNSAvg = 1.0 * nonRekNS / ( stopsPerLoop0 + stopsPerLoop1);
		double timeInJobsPerc = 100.0 * nonRekNS / totalCPUNSPerLoop;
		NumberFormat nf = NFInt_TL.get();
		double speedup = 1.0 * singleNS[n] / durNSPerLoop;
		BenchLogger.sysinfo( "Fib(" + n + ")=" + nf.format( fiboResult)
				+ " in " + nf.format(durNSPerLoop) + " ns,"
				+ " recursion limit: " + r
				+ " at " + nf.format( singleJobNSAvg) + " ns,"
				+ " in jobs: " + String.format( "%.2f", timeInJobsPerc) + " %, "
				+ " speedup: " + String.format( "%.2f", speedup)
				+ " steals: " + stealsPerLoop
				+ " forks " + forksPerLoop
//				+ " stops0 " + stopsPerLoop0
//				+ " stops1 " + stopsPerLoop1
		);
		result.durNS = durNSPerLoop;
		result.rekLimit = r;
		result.speedup = speedup;
		result.timeInJobsPerc = timeInJobsPerc;
		result.singleJobNSAvg = singleJobNSAvg;
	}

	static long fibonacci( final long arg) {
		FibonacciForkAff	task = new FibonacciForkAff( arg);
		long result = fjp.invoke( task);
		forks.set( task.forkCount);
//		BenchLogger.sysinfo( "active: " + task.activeCount + ", running: " + task.runningCount);
		return result;
	}

	long	n;
	private long forkCount = 0;
	private long activeCount = 0;
	private long runningCount = 0;

	public FibonacciForkAff( long n) {
		super();
		this.n = n;
	}

	@Override
	protected Long compute() {
		// wenn Argument zu klein ist, daß sich aufteilen nicht mehr lohnt, dann berechne es ohne weitere Verteilung auf Jobs, aber immernoch rekursiv
		if ( n <= rekLimit) {
			runningCount = Math.max( runningCount, fjp.getRunningThreadCount());
			activeCount = Math.max( activeCount, fjp.getActiveThreadCount());
			return fibonacci0( n);
		}
		// Aufteilen wird als lohnenswert angesehen, teile also in einen kleineren Job (n-2) und einen größeren (n-1) auf
		FibonacciForkAff	ff1 = new FibonacciForkAff( n-1);
		FibonacciForkAff	ff2 = new FibonacciForkAff( n-2);
		ff1.fork();			// beginne den größeren Job als asynchronen Task im Pool
		long	r2 = ff2.compute();	// berechne den kleineren selbst
		long	r1 = ff1.join();			// dann warte, daß der große fertig ist
		forkCount = ff2.forkCount + ff1.forkCount + 1;
		runningCount = Math.max( ff1.runningCount, ff2.runningCount);
		activeCount = Math.max( ff1.activeCount, ff2.activeCount);
		return r1 + r2;					// fasse beide Teilergebnisse zusammen
	}

}

