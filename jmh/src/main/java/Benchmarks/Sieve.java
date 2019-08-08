package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.*;
import java.util.concurrent.*;

@State(Scope.Benchmark)
public class Sieve {

	int size = 1 << 15;
	boolean flags[] = new boolean[ size + 1];

	@Setup(Level.Trial)
	public void setup() {
		int count = sieve();
		System.out.println( "" + count + " primes");
	}

	@Benchmark
    public int sieve() {
	    int count = 0 ;
	    for ( int i = 0;  i <= size;  i++)
		    flags[i] = true;
	    for ( int   i = 0;  i <= size;  i++) {
		    if (flags[i]) {
			    int prime = i + i + 3;
			    int k = i + prime;
			    while (k <= size) {
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
		        .warmupIterations(1)
				.warmupTime( TimeValue.seconds( 1))
		        .measurementIterations(5)
				.measurementTime( TimeValue.seconds( 5))
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
