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
public class BranchingCode {

	static final int countFlags = 1 << 8;
	private boolean[][]   flagsA;
	int flagIndex = 0;

	@Setup
	public void setup() {
		Random random = new Random(1234);
		flagsA = new boolean[ countFlags][ 4];
		for ( int i = 0;  i < flagsA.length;  i++) {
			boolean[] flags = flagsA[i];
			for (int j = 0; j < 4; j++) {
				flags[ j] = random.nextBoolean();
			}
			// teste, ob alles zum gleichen Ergebnis führt
			if ( largeIf( flags[ 0], flags[ 1], flags[ 2], flags[ 3]) != cascadeIfHalf( flags[ 0], flags[ 1], flags[ 2], flags[ 3])) {
				System.err.println( "mismatch half at " + i + " for " + Arrays.toString( flags));
			}
			if ( largeIf( flags[ 0], flags[ 1], flags[ 2], flags[ 3]) != cascadeIfFull( flags[ 0], flags[ 1], flags[ 2], flags[ 3])) {
				System.err.println( "mismatch full at " + i + " for " + Arrays.toString( flags));
			}
			if ( largeIf( flags[ 0], flags[ 1], flags[ 2], flags[ 3]) != largeIfB( flags[ 0], flags[ 1], flags[ 2], flags[ 3])) {
				System.err.println( "mismatch bool at " + i + " for " + Arrays.toString( flags));
			}
		}
	}

	private boolean[] getFlags() {
		int index = flagIndex++ & (countFlags - 1);
		boolean flags[] = flagsA[index];
		return flags;
	}

	@Benchmark
	public boolean[] baseline() {
		boolean flags[] = getFlags();
		return flags;
	}

	@Benchmark
	public int largeIf() {
		boolean flags[] = getFlags();
		return largeIf( flags[ 0], flags[ 1], flags[ 2], flags[ 3]);
	}

	private int largeIf( boolean f0, boolean f1, boolean f2, boolean f3) {
		if (
			( f0 && f1)
				||
				( f2 && ! f3)
		) {
			return 1;
		}
		return 0;
	}

	@Benchmark
	public int largeIfB() {
		boolean flags[] = getFlags();
		return largeIfB( flags[ 0], flags[ 1], flags[ 2], flags[ 3]);
	}

	private int largeIfB( boolean f0, boolean f1, boolean f2, boolean f3) {
		if (
				( f0 & f1)
					|
				( f2 & ! f3)
		) {
			return 1;
		}
		return 0;
	}

	@Benchmark
	public int cascadeIfHalf() {
		boolean flags[] = getFlags();
		return cascadeIfHalf( flags[ 0], flags[ 1], flags[ 2], flags[ 3]);
	}

	private int cascadeIfHalf( boolean f0, boolean f1, boolean f2, boolean f3) {
		if ( f0 && f1) {
			return 1;
		}
		if ( f2 && ! f3) {
			return 1;
		}
		return 0;
	}

	@Benchmark
	public int cascadeIfFull() {
		boolean flags[] = getFlags();
		return cascadeIfFull( flags[ 0], flags[ 1], flags[ 2], flags[ 3]);
	}

	private int cascadeIfFull( boolean f0, boolean f1, boolean f2, boolean f3) {
		if ( f0) {
			if ( f1) {
				return 1;
			}
		}
		if ( f2) {
			if ( ! f3) {
				return 1;
			}
		}
		return 0;
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include( BranchingCode.class.getSimpleName())
				.warmupIterations(8)
				.measurementIterations( 5)
				.measurementTime(TimeValue.seconds( 10))
				.forks(1)
				.build();
		Collection<RunResult> results = new Runner(opt).run();
		JMHUtils.reportWithBaseline( results, "baseline");
	}

}
