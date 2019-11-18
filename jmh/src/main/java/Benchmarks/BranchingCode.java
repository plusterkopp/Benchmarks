package Benchmarks;


import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.*;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class BranchingCode {

	private static final int COUNT = 1024 * 1024;

	private byte[] sorted;
	private byte[] unsorted;

	int countFlags = 1024;
	private boolean[][]   flagsA;
	int flagIndex = 0;

	@Setup
	public void setup() {
		sorted = new byte[COUNT];
		unsorted = new byte[COUNT];
		Random random = new Random(1234);
		random.nextBytes(sorted);
		random.nextBytes(unsorted);
		Arrays.sort(sorted);

		flagsA = new boolean[ countFlags][ 4];
		for ( int i = 0;  i < flagsA.length;  i++) {
			boolean[] flags = flagsA[i];
			for (int j = 0; j < 4; j++) {
				flags[ j] = random.nextBoolean();
			}
			// teste, ob alles zum gleichen Ergebnis führt
			if ( largeIf( flags) != cascadeIfHalf( flags)) {
				System.err.println( "mismatch half at " + i + " for " + Arrays.toString( flags));
			}
			if ( largeIf( flags) != cascadeIfFull( flags)) {
				System.err.println( "mismatch full at " + i + " for " + Arrays.toString( flags));
			}
		}
	}

	@Benchmark
	public int largeIf() {
		boolean flags[] = flagsA[ flagIndex++ & ( countFlags - 1)];
		return largeIf( flags);
	}

	private int largeIf( boolean flags[]) {
		if (
				( flags[ 0] && flags[ 1])
						||
						( flags[ 2] && ! flags[ 3])
		) {
			return 1;
		}
		return 0;
	}

	@Benchmark
	public int cascadeIfHalf() {
		boolean flags[] = flagsA[ flagIndex++ & ( countFlags - 1)];
		return cascadeIfHalf( flags);
	}

	private int cascadeIfHalf( boolean flags[]) {
		if ( flags[ 0] && flags[ 1]) {
			return 1;
		}
		if ( flags[ 2] && ! flags[ 3]) {
			return 1;
		}
		return 0;
	}

	@Benchmark
	public int cascadeIfFull() {
		boolean flags[] = flagsA[ flagIndex++ & ( countFlags - 1)];
		return cascadeIfFull( flags);
	}

	private int cascadeIfFull( boolean flags[]) {
		if ( flags[ 0]) {
			if ( flags[ 1]) {
				return 1;
			}
		}
		if ( flags[ 2]) {
			if ( ! flags[ 3]) {
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
				.measurementTime(TimeValue.seconds( 5))
				.forks(1)
				.build();
		new Runner(opt).run();
	}

}
