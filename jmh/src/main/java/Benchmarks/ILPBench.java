package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.Arrays;
import java.util.concurrent.*;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ILPBench {

	private static final int ILPMAX = 16;
	private static final int ArraySize = 100 * ILPMAX;
	private static double[] y;
	private static double[] x;

	@Setup(Level.Trial)
	public static void setup() {
		x = new double[ ArraySize];
		y = new double[ ArraySize];
		for (int i = 0; i < x.length; i++) {
			x[ i] = Math.random();
			y[ i] = Math.random();
		}
	}

	double sum = 0;

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public double noILP() {
		double sum = 0;
//		for ( int j = ArraySize - 1;  j >= 0; j--) {
		for ( int j = 0;  j < ArraySize; j++) {
			sum += x[ j] * y[ j];
		}
		return sum;
	}


	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public double h2ILP() {
		double sum0 = 0;
		double sum1 = 0;
		for ( int j = ArraySize - 1;  j >= 0; j -= 2) {
			sum0 += x[ j] * y[ j];
			sum1 += x[ j-1] * y[ j-1];
		}
		return sum0 + sum1;
	}


	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public double h4ILP() {
		double sum = 0;
		final double[] sums = new double[ 4];
		for ( int s = 0;  s < 4;  s++) {
			sums[ s] = 0;
		}
		for ( int j = ArraySize - 1;  j >= 0; j -= 4) {
			sums[ 0] += x[ j] * y[ j];
			sums[ 1] += x[ j-1] * y[ j-1];
			sums[ 2] += x[ j-2] * y[ j-2];
			sums[ 3] += x[ j-3] * y[ j-3];
		}
		for ( int s = 0;  s < 4;  s++) {
			sum += sums[ s];
		}
		return sum;
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public double nx4ILP() {
		return nxILP( 4);
	}

	private double nxILP( final int ilpFactor) {
		double sum = 0;
		final double[] sums = new double[ ilpFactor];
		Arrays.fill( sums, 0);
		for ( int j = ArraySize - 1;  j >= 0; j -= ilpFactor) {
			for ( int s = 0;  s < ilpFactor;  s++) {
				sums[ s] += x[ j-s] * y[ j-s];
			}
		}
		for ( int s = 0;  s < ilpFactor;  s++) {
			sum += sums[ s];
		}
		return sum;
	}


	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public double n16ILP() {
		double sum = 0;
		final int	n = 16;
		final double[] sums = new double[ n];
		Arrays.fill( sums, 0);
		for ( int j = ArraySize - 1;  j >= 0; j -= n) {
			for ( int s = 0;  s < n;  s++) {
				sums[ s] += x[ j-s] * y[ j-s];
			}
		}
		for ( int s = 0;  s < n;  s++) {
			sum += sums[ s];
		}
		return sum;
	}


	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public double n8ILP() {
		double sum = 0;
		final int	n = 8;
		final double[] sums = new double[ n];
		Arrays.fill( sums, 0);
		for ( int j = ArraySize - 1;  j >= 0; j -= n) {
			for ( int s = 0;  s < n;  s++) {
				sums[ s] += x[ j-s] * y[ j-s];
			}
		}
		for ( int s = 0;  s < n;  s++) {
			sum += sums[ s];
		}
		return sum;
	}


	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public double n4ILP() {
		double sum = 0;
		final int	n = 4;
		final double[] sums = new double[ n];
		Arrays.fill( sums, 0);
		for ( int j = ArraySize - 1;  j >= 0; j -= n) {
			for ( int s = 0;  s < n;  s++) {
				sums[ s] += x[ j-s] * y[ j-s];
			}
		}
		for ( int s = 0;  s < n;  s++) {
			sum += sums[ s];
		}
		return sum;
	}


	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( ILPBench.class.getSimpleName())
		        .warmupIterations(3)
				.warmupTime(TimeValue.seconds(1))
		        .measurementIterations(5)
				.measurementTime(TimeValue.seconds(1))
				.forks(1)
                .build();
        new Runner(opt).run();
    }
}
