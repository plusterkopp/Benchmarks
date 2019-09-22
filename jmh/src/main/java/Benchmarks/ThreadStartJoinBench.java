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
public class ThreadStartJoinBench {

	@Benchmark
	public void threadStartJoinRunnable() {
		final int i[] = new int[ 1];
		Thread  thread = new Thread(new Runnable() {
			@Override
			public void run() {
				i[ 0]++;
			}
		}, "Runnable");
		thread.start();
		try {
			thread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Benchmark
	public void threadStartJoinLambda() {
		final int i[] = new int[ 1];
		Thread  thread = new Thread( () -> i[ 0]++, "Lambda");
		thread.start();
		try {
			thread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Benchmark
	public void threadStartJoinOverride() {
		final int i[] = new int[ 1];
		Thread  thread = new Thread( "Lambda") {
			@Override
			public void run() {
				i[ 0]++;
			}
		};
		thread.start();
		try {
			thread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}


	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( ThreadStartJoinBench.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .measurementTime( TimeValue.seconds( 5))
		        .timeUnit(TimeUnit.MICROSECONDS)
		        .warmupIterations(5)
		        .measurementIterations(5)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
