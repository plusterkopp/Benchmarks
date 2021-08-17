package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class TimerCancelBench {

	final ScheduledExecutorService	ses = new ScheduledThreadPoolExecutor( 1);
	ScheduledFuture<Integer>	fut;

	@Setup
	public void setup() {
		fut = ses.schedule(() -> 42, 100, TimeUnit.SECONDS);
	}

    @Benchmark
    public void cancelAndRestart() {
        fut.cancel(true);
		fut = ses.schedule(() -> 42, 100, TimeUnit.SECONDS);
    }

    @TearDown
	public void tearDown() {
		fut.cancel(true);
	}


    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( TimerCancelBench.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .timeUnit(TimeUnit.NANOSECONDS)
		        .warmupIterations(1)
				.warmupTime( TimeValue.seconds( 1))
		        .measurementIterations(5)
				.measurementTime( TimeValue.seconds( 5))
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
