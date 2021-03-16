package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.*;
import java.util.concurrent.*;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class StreamAndLoop {

	@Param({
		"1"
		, "10"
		, "100"
		, "100000"
		, "1000000"})
	int size;

	List<Long> list;
	long[]  array;

	@Setup(Level.Trial)
	public void setup() {
		list = new ArrayList<>( size);
		array = new long[ size];
		Random  rnd = new Random();
		for ( int i = 0;  i < size;  i++) {
			long nextLong = rnd.nextLong();
			list.add( nextLong);
			array[ i] = nextLong;
		}
	}

	@Benchmark
	public long searchMaxListStream() {
		Optional<Long> result = list.stream().max(Long::compare);
		return result.get();
	}

	@Benchmark
	public long searchMaxArrayStream() {
		OptionalLong result = Arrays.stream(array).max();
		return result.getAsLong();
	}

	@Benchmark
	public long searchMaxListFor() {
		long result = list.get(0);
		for ( int i = 1;  i < size;  i++) {
			result = Long.max( result, list.get( i));
		}
		return result;
	}

	@Benchmark
	public long searchMaxArrayFor() {
		long result = array[ 0];
		for ( int i = 1;  i < size;  i++) {
			result = Long.max( result, array[ i]);
		}
		return result;
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( StreamAndLoop.class.getSimpleName())
		        .warmupIterations( 3)
		        .warmupTime( TimeValue.seconds( 3))
		        .measurementTime( TimeValue.seconds( 5))
				.measurementIterations( 5)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
