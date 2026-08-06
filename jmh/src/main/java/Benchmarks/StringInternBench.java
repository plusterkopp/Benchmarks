package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
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

	private String internString0() {
		String intern = null;
		for (int i = 0; i < stringList.size(); i++) {
			String s = stringList.get( i);
			intern = s.intern();
		}
		return intern;
	}


	@Benchmark
	@OperationsPerInvocation( stringCount)
	public String internString01T() {
		return internString0();
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	@Threads( 4)
	public String internString04T() {
		// wird gebraucht, um setup zu garantieren
		lock.readLock().lock();
		try {
			return internString0();
		} finally {
			lock.readLock().unlock();
		}
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	@Threads( 16)
	public String internString16T() {
		// wird gebraucht, um setup zu garantieren
		lock.readLock().lock();
		try {
			return internString0();
		} finally {
			lock.readLock().unlock();
		}
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	@Threads( 64)
	public String internString64T() {
		// wird gebraucht, um setup zu garantieren
		lock.readLock().lock();
		try {
			return internString0();
		} finally {
			lock.readLock().unlock();
		}
	}

	private String internCHM0() {
		String present = null;
		for (int i = 0; i < stringList.size(); i++) {
			String s = stringList.get( i);
			present = chm.computeIfAbsent( s, existing -> existing);
		}
		return present;
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	public String internCHM01T() {
		return internCHM0();
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	@Threads( 4)
	public String internCHM04T() {
		lock.readLock().lock();
		try {
			return internCHM0();
		} finally {
			lock.readLock().unlock();
		}
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	@Threads( 16)
	public String internCHM16T() {
		lock.readLock().lock();
		try {
			return internCHM0();
		} finally {
			lock.readLock().unlock();
		}
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	@Threads( 64)
	public String internCHM64T() {
		lock.readLock().lock();
		try {
			return internCHM0();
		} finally {
			lock.readLock().unlock();
		}
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( StringInternBench.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .timeUnit(TimeUnit.NANOSECONDS)
		        .warmupIterations(3)
				.warmupTime( TimeValue.seconds( 1))
		        .measurementIterations(5)
				.measurementTime( TimeValue.seconds( 1))
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
