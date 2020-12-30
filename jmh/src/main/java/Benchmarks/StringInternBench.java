package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class StringInternBench {

	@Param( { "10000", "100000", "1000000"}) // "100", "1000", , "100000", "1000000"
	int uniqueCount;

	final int stringCount = 1_000_000;
	List<String>  stringList = new ArrayList<String>( stringCount);
	int runCount = 1;
	ConcurrentHashMap<String, String> chm;
	ReadWriteLock   lock = new ReentrantReadWriteLock();

	@Setup(Level.Invocation)
	public void setup() {
		lock.writeLock().lock();
		try {
			chm = new ConcurrentHashMap<>();
			stringList.clear();
			for (int i = 0; i < stringCount; i++) {
				long value = i % uniqueCount;
				stringList.add(Integer.toString(runCount) + "-" + Long.toString(value));
			}
			runCount++;
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	public void internString() {
		for (int i = 0; i < stringList.size(); i++) {
			String s = stringList.get( i);
			String intern = s.intern();
//			if ( s != intern) {
//				stringList.set(i, intern);
//			}
		}
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	@Threads( 4)
	public void internString4T() {
		lock.readLock().lock();
		try {
			for (int i = 0; i < stringList.size(); i++) {
				String s = stringList.get( i);
				String intern = s.intern();
//			if ( s != intern) {
//				stringList.set(i, intern);
//			}
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	public void internCHM() {
		for (int i = 0; i < stringList.size(); i++) {
			String s = stringList.get( i);
			String present = chm.putIfAbsent(s, s);
//			if (present != null) {
//				stringList.set( i, present);
//			}
		}
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	@Threads( 4)
	public void internCHM4T() {
		lock.readLock().lock();
		try {
			for (int i = 0; i < stringList.size(); i++) {
				String s = stringList.get( i);
				String present = chm.putIfAbsent(s, s);
//			if (present != null) {
//				stringList.set( i, present);
//			}
			}
		} finally {
			lock.readLock().unlock();
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
