package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.BitSet;
import java.util.concurrent.*;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class Sieve {

	static final int size = 81900;
	static final boolean flags[] = new boolean[ size + 1];
	static final BitSet bitSet = new BitSet( size +1);
	static final int runs = 100;

	@Setup(Level.Trial)
	public void setup() {
		int count0 = sieve0();
		int countBS = sieveBS();
		System.out.println( count0 + "/" + countBS + " primes to " + size);
	}

//	@Benchmark
//	@BenchmarkMode(Mode.AverageTime)
//	@OperationsPerInvocation(runs)
//	public int sieve() {
//		int s = 0;
//		for ( int i = 0;  i < runs;  i++) {
//			s = sieve0();
//		}
//		return s;
//	}

	@Benchmark
	@BenchmarkMode(Mode.AverageTime)
	@OperationsPerInvocation(size)
	public int sieve1() {
		return sieve0();
	}

	@Benchmark
	@BenchmarkMode(Mode.AverageTime)
	@OperationsPerInvocation(size)
	public int sieveBS() {
		return sieveBS0();
	}

	private int sieve0() {
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

	private int sieveBS0() {
		int count = 0 ;
		int size = bitSet.size();
		for ( int i = 0;  i < size;  i++)
			bitSet.set( i);
		for ( int   i = 0;  i < flags.length;  i++) {
			if ( bitSet.get( i)) {
				int prime = i + i + 3;
				int k = i + prime;
				while (k < flags.length) {
					bitSet.clear( k);
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
			    .warmupIterations(5)
			    .warmupTime( TimeValue.seconds( 1))
			    .measurementIterations(3)
			    .measurementTime( TimeValue.seconds( 5))
			    .forks(1)
			    .build();
	    new Runner(opt).run();
    }
}
