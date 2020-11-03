package perf;


import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;
import utils.*;

import java.util.*;
import java.util.concurrent.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class BranchingCodeString {

	static final int count = 1 << 8;
	private String[] stringsA;
	int flagIndex = 0;

	@Setup
	public void setup() {
		Random random = new Random(1234);
		stringsA = new String[count];
		for (int i = 0; i < stringsA.length; i++) {
			int rnd = random.nextInt(6);
			stringsA[i] = "#" + rnd;
		}
	}

	private String getString() {
		int index = flagIndex++ & (count - 1);
		String s = stringsA[index];
		return s;
	}

	@Benchmark
	public String baseline() {
		String s = getString();
		return s;
	}

	@Benchmark
	public int singleSimpleEquals() {
		String s = getString();
		return singleSimpleEquals( s);
	}

	private int singleSimpleEquals(String a) {
		if (
			Objects.equals( a, "#1")
		) {
			return 1;
		}
		return 0;
	}

	@Benchmark
	public int singleLargeIf() {
		String s = getString();
		return singleLargeIf( s);
	}

	private int singleLargeIf(String a) {
		if (
			a.length() == 2 && a.charAt( 0) == '#' && a.charAt( 1) == '1'
		) {
			return 1;
		}
		return 0;
	}

	@Benchmark
	public int singleLargeIfB() {
		String s = getString();
		return singleLargeIfB( s);
	}

	private int singleLargeIfB(String a) {
		if (
			a.length() == 2 && ( a.charAt( 0) == '#' & a.charAt( 1) == '1')
		) {
			return 1;
		}
		return 0;
	}

	@Benchmark
	public int multiSimpleEquals() {
		String s = getString();
		return multiSimpleEquals( s);
	}

	private int multiSimpleEquals(String a) {
		if (
			Objects.equals( a, "#0") || Objects.equals( a, "#1") || Objects.equals( a, "#2")
		) {
			return 1;
		}
		return 0;
	}

	@Benchmark
	public int multiLargeIf() {
		String s = getString();
		return multiLargeIf( s);
	}

	private int multiLargeIf(String a) {
		if (
			a.length() == 2 && a.charAt( 0) == '#' &&
				( a.charAt( 1) == '0' || a.charAt( 1) == '1' ||a.charAt( 1) == '2')
		) {
			return 1;
		}
		return 0;
	}

	@Benchmark
	public int multiLargeIfB() {
		String s = getString();
		return multiLargeIfB( s);
	}

	private int multiLargeIfB(String a) {
		if (
			a.length() == 2 && ( a.charAt( 0) == '#' &
				( a.charAt( 1) == '0' | a.charAt( 1) == '1' | a.charAt( 1) == '2'))
		) {
			return 1;
		}
		return 0;
	}

	@Benchmark
	public int multiLargeIfB2() {
		String s = getString();
		return multiLargeIfB2( s);
	}

	private int multiLargeIfB2(String a) {
		if ( a.length() != 2) {
			return 0;
		}
		char c = a.charAt(1);
		if (
			a.charAt( 0) == '#' &
				( c == '0' | c == '1' | c == '2')
		) {
			return 1;
		}
		return 0;
	}

	@Benchmark
	public int multiLargeIfB3() {
		String s = getString();
		return multiLargeIfB3( s);
	}

	private int multiLargeIfB3(String a) {
		if ( a.length() != 2) {
			return 0;
		}
		char c = a.charAt(1);
		if (
			a.charAt( 0) == '#' &
				( c >= '0' & c <= '2')
		) {
			return 1;
		}
		return 0;
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include( BranchingCodeString.class.getSimpleName())
				.warmupIterations(8)
				.measurementIterations( 5)
				.measurementTime(TimeValue.seconds( 5))
				.forks(1)
				.build();
		Collection<RunResult> results = new Runner(opt).run();
		JMHUtils.reportWithBaseline( results, "baseline");
	}

}
