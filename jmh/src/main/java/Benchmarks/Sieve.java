package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.*;
import java.util.concurrent.*;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class Sieve {

	static final int size = 8190;
	static final boolean flags[] = new boolean[ size + 1];
	static final int runs = 1000;

	@Setup(Level.Trial)
	public void setup() {
		int count = sieve();
		System.out.println( "" + count + " primes");
	}

	@Benchmark
	@BenchmarkMode(Mode.AverageTime)
	@OutputTimeUnit(TimeUnit.MICROSECONDS)
	@OperationsPerInvocation(runs)
	public int sieve() {
		int s = 0;
		for ( int i = 0;  i < runs;  i++) {
			s = sieve0();
		}
		return s;
	}

    public int sieve0() {
	    int count = 0 ;
	    for ( int i = 0;  i < flags.length;  i++)
		    flags[i] = true;
	    for ( int   i = 0;  i < flags.length;  i++) {
		    if (flags[i]) {
			    int prime = i + i + 3;
			    int k = i + prime;
			    while (k < flags.length) {
				    flags[k] = false;
				    k += prime;
			    }
			    count++;
		    }
	    }
	    return count;
    }


    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( Sieve.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .timeUnit(TimeUnit.MICROSECONDS)
		        .warmupIterations(5)
				.warmupTime( TimeValue.seconds( 2))
		        .measurementIterations(5)
				.measurementTime( TimeValue.seconds( 5))
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
