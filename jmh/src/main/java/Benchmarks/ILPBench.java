package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

@State(Scope.Benchmark)
public class ILPBench {

	private static final int ILPMAX = 16;
	private static final int ArraySize = 1000 * ILPMAX;
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
	public void noILP() {
		for ( int j = ArraySize - 1;  j >= 0; j--) {
			sum += x[ j] * y[ j];
		}
	}


	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void h2ILP() {
		sum = 0;
		final double[] sums = new double[ 2];
		for ( int s = 0;  s < 2;  s++) {
			sums[ s] = 0;
		}
		for ( int j = ArraySize - 1;  j >= 0; j -= 2) {
			sums[ 0] += x[ j] * y[ j];
			sums[ 1] += x[ j-1] * y[ j-1];
		}
		for ( int s = 0;  s < 2;  s++) {
			sum += sums[ s];
		}
	}


	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void h4ILP() {
		sum = 0;
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
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void nx4ILP() {
		nxILP( 4);
	}

	private void nxILP( final int ilpFactor) {
		final double[] sums = new double[ ilpFactor];
		for ( int s = 0;  s < ilpFactor;  s++) {
			sums[ s] = 0;
		}
		for ( int j = ArraySize - 1;  j >= 0; j -= ilpFactor) {
			for ( int s = 0;  s < ilpFactor;  s++) {
				sums[ s] += x[ j-s] * y[ j-s];
			}
		}
		for ( int s = 0;  s < ilpFactor;  s++) {
			sum += sums[ s];
		}
	}


	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void n16ILP() {
		final int	n = 16;
		final double[] sums = new double[ n];
		for ( int s = 0;  s < n;  s++) {
			sums[ s] = 0;
		}
		for ( int j = ArraySize - 1;  j >= 0; j -= n) {
			for ( int s = 0;  s < n;  s++) {
				sums[ s] += x[ j-s] * y[ j-s];
			}
		}
		for ( int s = 0;  s < n;  s++) {
			sum += sums[ s];
		}
	}


	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void n8ILP() {
		final int	n = 8;
		final double[] sums = new double[ n];
		for ( int s = 0;  s < n;  s++) {
			sums[ s] = 0;
		}
		for ( int j = ArraySize - 1;  j >= 0; j -= n) {
			for ( int s = 0;  s < n;  s++) {
				sums[ s] += x[ j-s] * y[ j-s];
			}
		}
		for ( int s = 0;  s < n;  s++) {
			sum += sums[ s];
		}
	}


	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void n4ILP() {
		final int	n = 4;
		final double[] sums = new double[ n];
		for ( int s = 0;  s < n;  s++) {
			sums[ s] = 0;
		}
		for ( int j = ArraySize - 1;  j >= 0; j -= n) {
			for ( int s = 0;  s < n;  s++) {
				sums[ s] += x[ j-s] * y[ j-s];
			}
		}
		for ( int s = 0;  s < n;  s++) {
			sum += sums[ s];
		}
	}


	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( ILPBench.class.getSimpleName())
		        .warmupIterations(5)
		        .measurementIterations(12)
				.measurementTime(TimeValue.seconds(5))
				.forks(1)
                .build();
        new Runner(opt).run();
    }
}
