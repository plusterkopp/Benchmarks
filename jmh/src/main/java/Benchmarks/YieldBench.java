package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.concurrent.*;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class YieldBench {

    @Benchmark
    public void yieldBenchUncontended() {
        Thread.yield();
    }

    @Benchmark @Threads( 8)
    public void yieldBenchContended8() {
        Thread.yield();
    }

    @Benchmark @Threads( 32)
    public void yieldBenchContended32() {
        Thread.yield();
    }

    @Benchmark @Threads( 4)
    public void yieldBenchContended4() {
        Thread.yield();
    }

    @Benchmark @Threads( 16)
    public void yieldBenchContended16() {
        Thread.yield();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( YieldBench.class.getSimpleName())
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
