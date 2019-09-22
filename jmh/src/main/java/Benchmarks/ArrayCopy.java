package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.concurrent.*;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ArrayCopy {

	private static final int Mega = 1024 * 1024;
	private static final int LongSize = 8;
	private static final int ArraySizeL = 16 * Mega;
	private static final int ArraySizeB = LongSize * ArraySizeL;
	private static byte[] yBA;
	private static byte[] xBA;

	private static long[] yLA;
	private static long[] xLA;

	@Setup(Level.Trial)
	public static void setup() {
		xBA = new byte[ArraySizeB];
		yBA = new byte[ArraySizeB];

		xLA = new long[ArraySizeL];
		yLA = new long[ArraySizeL];
	}

	@Benchmark
	@OperationsPerInvocation(ArraySizeB / Mega)
	public void byteCopy() {
		for ( int j = ArraySizeB - 1;  j >= 0; j--) {
			yBA[ j] = xBA[ j];
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySizeB / Mega)
	public void longCopy() {
		for ( int j = ArraySizeL - 1;  j >= 0; j--) {
			yLA[ j] = xLA[ j];
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySizeB / Mega)
	public void arrayCopyBytes() {
		System.arraycopy(xBA, 0, yBA, 0, xBA.length);
	}

	@Benchmark
	@OperationsPerInvocation(ArraySizeB / Mega)
	public void arrayCopyLongs() {
		System.arraycopy(xLA, 0, yLA, 0, xLA.length);
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( ArrayCopy.class.getSimpleName())
		        .warmupIterations(5)
		        .measurementTime(TimeValue.seconds( 60))
				.measurementIterations( 3)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
