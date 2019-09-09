package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.*;
import java.util.concurrent.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 4, time = 30)
@Fork(1)
@State(Scope.Benchmark)
public class GCTestTreeMap {

    static class Container {
        int    array[] = new int[ 250];
    }

    SortedMap<Integer, Container> map = new TreeMap<>();
	Random  rnd = new Random();

	@Param({
			"1"
//			, "10"
			, "100"
//			, "1000"
			, "10000"
			, "100000"
			, "200000"
			, "500000"
			, "1000000"})
    int fillSize;

	@Setup
    public void fillList() {
        for ( int i = 0;  i < fillSize;  i++) {
            map.put( i, new Container());
        }
    }

	private Container addRemove() {
		int key = rnd.nextInt( fillSize);
		Container c = new Container();
		c.array[ 0] = key;
		Container container = map.put( key, c);
		return container;
	}

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-XX:+UseParallelGC"})
	public Container addRemovePS() {
    	return addRemove();
	}

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-XX:+UseG1GC"})
	public Container addRemoveG1() {
		return addRemove();
	}

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-XX:+UseConcMarkSweepGC"})
	public Container addRemoveCMS() {
		return addRemove();
	}

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC"})
	public Container addRemoveShen() {
		return addRemove();
	}

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC"})
	public Container addRemoveZGC() {
		return addRemove();
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( GCTestTreeMap.class.getSimpleName())
                .warmupIterations(3)
                .measurementTime(TimeValue.seconds( 10))
                .measurementIterations( 5)
//                .timeUnit( TimeUnit.NANOSECONDS)
                .forks(1)
                .build();
        new Runner(opt).run();
    }

}
