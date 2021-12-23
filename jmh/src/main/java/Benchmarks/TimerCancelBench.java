package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.concurrent.*;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class TimerCancelBench {

	ScheduledExecutorService	ses;
	ScheduledFuture<Integer>	fut;
	int delayStart;
	int delay;
	final Callable<Integer> call42 = () -> Integer.valueOf( 1);

	private void schedule() {
		fut = ses.schedule( call42, delayStart, TimeUnit.MILLISECONDS);
	}

	private void cancel() {
		if ( fut != null) {
			fut.cancel(true);
			fut = null;
		}
	}

	@Setup(Level.Iteration)
	public void setup() {
		ses = new ScheduledThreadPoolExecutor( 1);
		delayStart = 100;
		delay = 0;
//		schedule();
	}

	@Benchmark
	public void cancelAndRestart() {
		cancel();
		delay++;
		schedule();
	}

	@TearDown(Level.Iteration)
	public void tearDown() {
		cancel();
		ses.shutdown();
		awaitTermination();
//			System.gc();
	}

	private void awaitTermination() {
		try {
			ses.awaitTermination( 10, TimeUnit.SECONDS);
			ses = null;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}


	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include( TimerCancelBench.class.getSimpleName())
				.mode( Mode.AverageTime)
				.timeUnit(TimeUnit.NANOSECONDS)
				.warmupIterations(1)
				.warmupTime( TimeValue.seconds( 5))
				.measurementIterations( 15)
				.measurementTime( TimeValue.milliseconds( 1000))
				.forks(1)
//				.addProfiler( "gc")
				.jvmArgsPrepend(
//						"-XX:+UnlockExperimentalVMOptions",
						"-XX:+UseShenandoahGC",
						"-Xms4G"
				)
//				.shouldDoGC( true)
				.build();
		new Runner(opt).run();
	}
}
