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

//	@Setup
//    public void fillList() {
//        for ( int i = 0;  i < mapSize;  i++) {
//            map.put( i, new Container( fillSize));
//        }
//    }

	private Container addRemove( int mapSize) {
		int key = rnd.nextInt( mapSize);
		Container c = new Container( fillSize);
		c.array[ 0] = key;
		Container container = map.put( key, c);
		return container;
	}

	static int mapSizeFor16G = 1_000_000;
	static int mapSizeFor4G = mapSizeFor16G / 4;
	static int mapSizeFor1G = mapSizeFor4G / 4;

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx16G", "-XX:+UseParallelGC"})
	public Container addRemovePS_16G() {
    	return addRemove( mapSizeFor16G);
	}

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx16G", "-XX:+UseG1GC"})
	public Container addRemoveG1_16G() {
		return addRemove( mapSizeFor16G);
	}

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx16G", "-XX:+UseConcMarkSweepGC"})
	public Container addRemoveCMS_16G() {
		return addRemove( mapSizeFor16G);
	}

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx16G", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC"})
	public Container addRemoveShen_16G() {
		return addRemove( mapSizeFor16G);
	}

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx16G", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC"})
	public Container addRemoveZGC_16G() {
		return addRemove( mapSizeFor16G);
	}

	
	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx4G", "-XX:+UseParallelGC"})
	public Container addRemovePS_4G() { return addRemove( mapSizeFor4G); }

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx4G", "-XX:+UseG1GC"})
	public Container addRemoveG1_4G() { return addRemove( mapSizeFor4G); }

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx4G", "-XX:+UseConcMarkSweepGC"})
	public Container addRemoveCMS_4G() { return addRemove( mapSizeFor4G); }

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx4G", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC"})
	public Container addRemoveShen_4G() { return addRemove( mapSizeFor4G); }

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx4G", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC"})
	public Container addRemoveZGC_4G() { return addRemove( mapSizeFor4G); }


	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx1G", "-XX:+UseParallelGC"})
	public Container addRemovePS_1G() { return addRemove( mapSizeFor1G); }

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx1G", "-XX:+UseG1GC"})
	public Container addRemoveG1_1G() { return addRemove( mapSizeFor1G); }

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx1G", "-XX:+UseConcMarkSweepGC"})
	public Container addRemoveCMS_1G() { return addRemove( mapSizeFor1G); }

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx1G", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC"})
	public Container addRemoveShen_1G() { return addRemove( mapSizeFor1G); }

	@Benchmark
	@Fork(value = 1, jvmArgsPrepend = {"-Xmx1G", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC"})
	public Container addRemoveZGC_1G() { return addRemove( mapSizeFor1G); }

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
