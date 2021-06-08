package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.*;
import java.util.concurrent.*;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MulDivBench {

	final int ArraySizeM = 1;
	final int ArraySize = ArraySizeM * 1000;
	static double	one = 1;

	ThreadLocal<double[]> tlFromD;
	ThreadLocal<double[]> tlToD;

	ThreadLocal<long[]> tlFromL;
	ThreadLocal<long[]> tlToL;

	double divisorD = 1;
	long divisorL = 3;

	@Setup(Level.Trial)
	public void setup() {
		tlFromD = ThreadLocal.withInitial(() -> {
			final double[] arr = new double[ArraySize];
			Arrays.fill( arr, 1);
			return arr;
		});
		tlToD = ThreadLocal.withInitial(() -> {
			final double[] arr = new double[ArraySize];
			Arrays.fill(arr, 1);
			return arr;
		});
		divisorD = 1.3 + Math.random();


		tlFromL = ThreadLocal.withInitial(() -> {
			final long[] arr = new long[ArraySize];
			Arrays.fill( arr, 1);
			return arr;
		});
		tlToL = ThreadLocal.withInitial(() -> {
			final long[] arr = new long[ArraySize];
			Arrays.fill( arr, 1);
			return arr;
		});
		divisorL = (long) (2 + (2 * Math.random()));
	}

	@Setup(Level.Iteration)
	public void get() {
		tlFromD.get();
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void d_byRandom() {
		double[] from = tlFromD.get();
		double[] to = tlToD.get();
		for ( int i = from.length - 1; i >= 0; -- i) {
			to[ i] = from[ i] / divisorD;
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void l_byRandom() {
		long[] from = tlFromL.get();
		long[] to = tlToL.get();
		for ( int i = from.length - 1; i >= 0; -- i) {
			to[ i] = from[ i] / divisorL;
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void l_byConst() {
		long[] from = tlFromL.get();
		long[] to = tlToL.get();
		final long d = 3;
		for ( int i = from.length - 1; i >= 0; -- i) {
			to[ i] = from[ i] / d;
		}
	}


	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void d_byRandomLocal() {
		final double d = divisorD;
		double[] from = tlFromD.get();
		double[] to = tlToD.get();
		for ( int i = from.length - 1; i >= 0; -- i) {
			to[ i] = from[ i] / d;
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void d_byConst() {
		final double d = 1.6;
		double[] from = tlFromD.get();
		double[] to = tlToD.get();
		for ( int i = from.length - 1; i >= 0; -- i) {
			to[ i] = from[ i] / d;
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void d_byOne() {
		double[] from = tlFromD.get();
		double[] to = tlToD.get();
		for ( int i = from.length - 1; i >= 0; -- i) {
			to[ i] = from[ i] / one;
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void d_byThree() {
		final double d = 3;
		double[] from = tlFromD.get();
		double[] to = tlToD.get();
		for ( int i = from.length - 1; i >= 0; -- i) {
			to[ i] = from[ i] / d;
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void d_timesTwo() {
		double[] from = tlFromD.get();
		final double factor = 2;
		double[] to = tlToD.get();
		for ( int i = from.length - 1; i >= 0; -- i) {
			to[ i] = from[ i] * factor;
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void d_timesOther() {
		double[] from = tlFromD.get();
		double[] to = tlToD.get();
		final double factor = 1.6;
		for ( int i = from.length - 1; i >= 0; -- i) {
			to[ i] = from[ i] * factor;
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void d_plusOther() {
		double[] from = tlFromD.get();
		double[] to = tlToD.get();
		final double summand = 1.6;
		for ( int i = from.length - 1; i >= 0; -- i) {
			to[ i] = from[ i] + summand;
		}
	}


	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( MulDivBench.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .measurementTime( TimeValue.seconds( 5))
		        .timeUnit(TimeUnit.NANOSECONDS)
		        .warmupIterations(1)
		        .measurementIterations(5)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
