package de.icubic.mm.bench.jmh;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

public class ILPBench {

	private static final int ILPMAX = 16;
	private static final int ArraySize = 1000 * ILPMAX;
	private static double[] y;
	private static double[] x;


	@Benchmark
	public void noILP() {
		double sum = 0;
		for ( int j = ArraySize - 1;  j >= 0; j--) {
			sum += x[ j] * y[ j];
		}
	}


	@Benchmark
	public void h2ILP() {
		double sum = 0;
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
	public void h4ILP() {
		double sum = 0;
		sum = 0;
		final double[] sums = new double[ 4];
		for ( int s = 0;  s < 4;  s++) {
			sums[ s] = 0;
		}
		for ( int j = ArraySize - 1;  j >= 0; j -= 4) {
			sums[ 0] += x[ j] * y[ j];
			sums[ 1] += x[ j-1] * y[ j-1];
		}
		for ( int s = 0;  s < 4;  s++) {
			sum += sums[ s];
		}
	}

	@Benchmark
	public void nx4ILP() {
		nxILP( 4);
	}

	private void nxILP( final int ilpFactor) {
		double sum = 0;
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
	public void n16ILP() {
		final int	n = 16;
		double sum = 0;
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
	public void n8ILP() {
		final int	n = 8;
		double sum = 0;
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
	public void n4ILP() {
		final int	n = 4;
		double sum = 0;
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
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
