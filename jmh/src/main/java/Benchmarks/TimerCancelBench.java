package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

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
	final Callable<Integer> call42 = () -> delay;

	@Setup(Level.Iteration)
	public void setup() {
		ses = new ScheduledThreadPoolExecutor( 1);
		delayStart = 100;
		delay = 0;
		fut = ses.schedule( call42, delayStart + delay, TimeUnit.MILLISECONDS);
	}

	@Benchmark
	public void cancelAndRestart() {
		fut.cancel(true);
		delay++;
		fut = ses.schedule( call42, delayStart + delay, TimeUnit.MILLISECONDS);
	}

	@TearDown(Level.Iteration)
	public void tearDown() {
		fut.cancel(true);
		ses.shutdown();
	}


	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include( TimerCancelBench.class.getSimpleName())
				.mode( Mode.AverageTime)
				.timeUnit(TimeUnit.NANOSECONDS)
				.warmupIterations(1)
				.warmupTime( TimeValue.seconds( 1))
				.measurementIterations(15)
				.measurementTime( TimeValue.seconds( 1))
				.forks(1)
				.addProfiler( "gc")
				.jvmArgsPrepend( "-XX:+UseShenandoahGC")
				.build();
		new Runner(opt).run();
	}
}
