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
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class BranchingCode {

	int countFlags = 1024;
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
		}
	}

	@Benchmark
	public int largeIf() {
		boolean flags[] = flagsA[ flagIndex++ & ( countFlags - 1)];
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
	public int cascadeIfHalf() {
		boolean flags[] = flagsA[ flagIndex++ & ( countFlags - 1)];
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
		boolean flags[] = flagsA[ flagIndex++ & ( countFlags - 1)];
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
			.warmupIterations(5)
			.measurementIterations( 5)
			.measurementTime(TimeValue.seconds( 5))
			.forks(1)
			.build();
		new Runner(opt).run();
	}

}
