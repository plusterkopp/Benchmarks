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
public class StringInternBench {

	@Param( { "10000", "100000", "1000000", "10000000"}) // "100", "1000", , "100000", "1000000"
	int uniqueCount;

	final int stringCount = 1_000_000;
	List<String>  stringList = new ArrayList<String>( stringCount);
	int runCount = 1;

	@Setup(Level.Invocation)
	public void setup() {
		stringList.clear();
		for (int i = 0; i < stringCount; i++) {
			long value = i % uniqueCount;
			stringList.add( Integer.toString( runCount) + "-" + Long.toString( value));
		}
		runCount++;
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	public void internString() {
		for (int i = 0; i < stringList.size(); i++) {
			String s = stringList.get( i);
			String intern = s.intern();
			stringList.set( i, intern);
		}
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	public void internCHM() {
		ConcurrentHashMap<String, String> chm = new ConcurrentHashMap<>();
		for (int i = 0; i < stringList.size(); i++) {
			String s = stringList.get( i);
			String present = chm.putIfAbsent(s, s);
			if (present != null) {
				stringList.set( i, present);
			}
		}
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( StringInternBench.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .timeUnit(TimeUnit.NANOSECONDS)
		        .warmupIterations(5)
				.warmupTime( TimeValue.seconds( 1))
		        .measurementIterations(5)
				.measurementTime( TimeValue.seconds( 5))
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
