package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

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
		        .timeUnit(TimeUnit.MICROSECONDS)
		        .warmupIterations(1)
				.warmupTime( TimeValue.seconds( 1))
		        .measurementIterations(5)
				.measurementTime( TimeValue.seconds( 5))
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
