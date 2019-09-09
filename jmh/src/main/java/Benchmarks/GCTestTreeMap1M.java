package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 4, time = 30)
@Fork(1)
@State(Scope.Benchmark)
public class GCTestTreeMap1M {

	@Param({
			"10"
			, "100"
			, "1000"
			, "10000"
	})
	int fillSize;

    static class Container {
        int    array[];

        Container( int size) {
        	array = new int[ size / 4];
		}
    }

    SortedMap<Integer, Container> map = new TreeMap<>();
	Random  rnd = new Random();

	int	mapSize = 1_000_000;

	@Setup
    public void fillList() {
        for ( int i = 0;  i < mapSize;  i++) {
            map.put( i, new Container( fillSize));
        }
    }

	private Container addRemove() {
		int key = rnd.nextInt( mapSize);
		Container c = new Container( fillSize);
		c.array[ 0] = key;
		Container container = map.put( key, c);
		return container;
	}

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx16G", "-XX:+UseParallelGC"})
	public Container addRemovePS() {
    	return addRemove();
	}

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx16G", "-XX:+UseG1GC"})
	public Container addRemoveG1() {
		return addRemove();
	}

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx16G", "-XX:+UseConcMarkSweepGC"})
	public Container addRemoveCMS() {
		return addRemove();
	}

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx16G", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC"})
	public Container addRemoveShen() {
		return addRemove();
	}

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx16G", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC"})
	public Container addRemoveZGC() {
		return addRemove();
	}


	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( GCTestTreeMap1M.class.getSimpleName())
                .warmupIterations(3)
                .measurementTime(TimeValue.seconds( 10))
                .measurementIterations( 5)
//                .timeUnit( TimeUnit.NANOSECONDS)
                .forks(1)
                .build();
        new Runner(opt).run();
    }

}
