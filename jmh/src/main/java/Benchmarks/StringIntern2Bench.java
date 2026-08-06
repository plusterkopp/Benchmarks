package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// eher nicht hilfreich, hat Setup vor jedem Aufruf

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class StringIntern2Bench {

	@Param( { "10000", "100000", "1000000"}) // "100", "1000", , "100000", "1000000"
	int uniqueCount;

	static final int subCountSmall = 10;
	static final int subCountMid = 1000;
	static final int subCountLarge = 100_000;

	final int runSize = 1_000;
	final int stringCount = 1_000_000;
	List<String>  stringList = new ArrayList<>( stringCount);
	int runCount = 1;
	ConcurrentHashMap<String, String> chm;
	ReadWriteLock   lock = new ReentrantReadWriteLock();

	ConcurrentHashMap<Integer, ConcurrentHashMap<String, String>> subMapC = new ConcurrentHashMap<>();

	private enum ESubSize {
		Small, Mid, Large
	}
	EnumMap<ESubSize, ConcurrentHashMap<String, String>> subMapE = new EnumMap<>( ESubSize.class);
	ConcurrentHashMap<String, String> chmSmall = new ConcurrentHashMap<>();
	ConcurrentHashMap<String, String> chmMid = new ConcurrentHashMap<>();
	ConcurrentHashMap<String, String> chmLarge = new ConcurrentHashMap<>();


	@Setup(Level.Trial)
	public void setupSubMapE() {
		for ( ESubSize s: ESubSize.values()) {
			subMapE.put( s, new ConcurrentHashMap<>());
		}
		chm = new ConcurrentHashMap<>();
	}

	@Setup(Level.Invocation)
	public void setup() {
		lock.writeLock().lock();
		try {
			chm.clear();
			subMapC.clear();
			for ( ESubSize s: ESubSize.values()) {
				subMapE.get( s).clear();
			}
			chmSmall.clear();
			chmMid.clear();
			chmLarge.clear();

			stringList.clear();
			for (int i = 0; i < stringCount; i++) {
				long value = i % uniqueCount;
				stringList.add( runCount + "-" + value);
			}
			runCount++;
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	public String internString() {
		String intern = null;
		for (String s : stringList) {
			intern = s.intern();
//			if ( s != intern) {
//				stringList.set(i, intern);
//			}
		}
		return intern;
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	@Threads( 4)
	public String internString4T() {
		String intern = null;
		lock.readLock().lock();
		try {
			for (String s : stringList) {
				intern = s.intern();
//			if ( s != intern) {
//				stringList.set(i, intern);
//			}
			}
		} finally {
			lock.readLock().unlock();
		}
		return intern;
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	public String internCHM() {
		String present = null;
		for (String s : stringList) {
			present = chm.computeIfAbsent( s, existing -> existing);
//			if (present != null) {
//				stringList.set( i, present);
//			}
		}
		return present;
	}

	@Benchmark
	@OperationsPerInvocation( stringCount)
	@Threads( 4)
	public String internCHM4T() {
		String present = null;
		lock.readLock().lock();
		try {
			for (String s : stringList) {
				present = chm.computeIfAbsent( s, existing -> existing);
//			if (present != null) {
//				stringList.set( i, present);
//			}
			}
		} finally {
			lock.readLock().unlock();
		}
		return present;
	}

	@Benchmark
	@OperationsPerInvocation( runSize)
	public String internCHMSubRndSwitch() {
		String present = null;
		for (int i = 0; i < runSize; i++) {
			Random rnd = new Random();
			int max;
			final Map<String, String> map;
			int selector = rnd.nextInt( 3);
			switch ( selector) {
				case 0:
					max = subCountSmall;
					map = chmSmall;
					break;
				case 1:
					max = subCountMid;
					map = chmMid;
					break;
				case 2:
					max = subCountLarge;
					map = chmLarge;
					break;
				default:
					map = null;
					max = 0;
			}
			max = Math.min( max, stringCount);
			int index = i % max;
			String s = stringList.get( index);
			present = map.putIfAbsent(s, s);
		}
		return present;
	}

	@Benchmark
	@OperationsPerInvocation( runSize)
	public String internCHMSubSmall() {
		return internCHMSubRndSel( 0);
	}

	@Benchmark
	@OperationsPerInvocation( runSize)
	public String internCHMSubMid() {
		return internCHMSubRndSel( 1);
	}

	@Benchmark
	@OperationsPerInvocation( runSize)
	public String internCHMSubLarge() {
		return internCHMSubRndSel( 2);
	}

	private String internCHMSubRndSel( int selector) {
		String present = null;
		for (int i = 0; i < runSize; i++) {
			int max;
			final Map<String, String> map;
			switch ( selector) {
				case 0:
					max = subCountSmall;
					map = chmSmall;
					break;
				case 1:
					max = subCountMid;
					map = chmMid;
					break;
				case 2:
					max = subCountLarge;
					map = chmLarge;
					break;
				default:
					map = null;
					max = 0;
			}
			max = Math.min( max, stringCount);
			int index = i % max;
			String s = stringList.get( index);
			present = map.putIfAbsent(s, s);
		}
		return present;
	}

	@Benchmark
	@OperationsPerInvocation( runSize)
	@Threads( 4)
	public String internCHMRndSwitch4T() {
		String present = null;
		lock.readLock().lock();
		try {
			for (int i = 0; i < runSize; i++) {
				Random rnd = new Random();
				int max;
				final Map<String, String> map;
				int selector = rnd.nextInt( 3);
				switch ( selector) {
					case 0:
						max = subCountSmall;
						map = chmSmall;
						break;
					case 1:
						max = subCountMid;
						map = chmMid;
						break;
					case 2:
						max = subCountLarge;
						map = chmLarge;
						break;
					default:
						map = null;
						max = 0;
				}
				max = Math.min( max, stringCount);
				int index = i % max;
				String s = stringList.get( index);
				present = map.putIfAbsent(s, s);
			}
		} finally {
			lock.readLock().unlock();
		}
		return present;
	}

	@Benchmark
	@OperationsPerInvocation( runSize)
	@Threads( 4)
	public String internCHMSwitchSmall4T() {
		return internCHMSwitchMT( 0);
	}

	@Benchmark
	@OperationsPerInvocation( runSize)
	@Threads( 4)
	public String internCHMSwitchMid4T() {
		return internCHMSwitchMT( 1);
	}

	@Benchmark
	@OperationsPerInvocation( runSize)
	@Threads( 4)
	public String internCHMSwitchLarge4T() {
		return internCHMSwitchMT( 2);
	}

	private String internCHMSwitchMT( int selector) {
		String present = null;
		lock.readLock().lock();
		try {
			for (int i = 0; i < runSize; i++) {
				int max;
				final Map<String, String> map;
				switch ( selector) {
					case 0:
						max = subCountSmall;
						map = chmSmall;
						break;
					case 1:
						max = subCountMid;
						map = chmMid;
						break;
					case 2:
						max = subCountLarge;
						map = chmLarge;
						break;
					default:
						map = null;
						max = 0;
				}
				max = Math.min( max, stringCount);
				int index = i % max;
				String s = stringList.get( index);
				present = map.putIfAbsent(s, s);
			}
		} finally {
			lock.readLock().unlock();
		}
		return present;
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( StringIntern2Bench.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .timeUnit(TimeUnit.NANOSECONDS)
		        .warmupIterations(3)
				.warmupTime( TimeValue.seconds( 1))
		        .measurementIterations(5)
				.measurementTime( TimeValue.seconds( 2))
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
