package de.icubic.mm.bench.jmh;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.concurrent.*;

@State(Scope.Benchmark)
public class MulDivBench {

	final int ArraySizeM = 1;
	final int ArraySize = ArraySizeM * 1000 * 1000;
	static double	one = 1;

	ThreadLocal<double[]> tl;

	@Setup(Level.Trial)
	public void setup() {
		tl = new ThreadLocal<double[]>() {
			@Override
			protected double[] initialValue() {
				final double[] arr = new double[ArraySize];
				for ( int i = arr.length - 1; i >= 0; -- i) {
					arr[ i] = 1;
				}
				return arr;
			}
		};
	}

	@Setup(Level.Iteration)
	public void get() {
		tl.get();
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void byOther() {
		final double d = 1.6;
		double[] values = tl.get();
		for ( int i = values.length - 1; i >= 0; -- i) {
			values[ i] /= d;
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void byOne() {
		double[] values = tl.get();
		for ( int i = values.length - 1; i >= 0; -- i) {
			values[ i] /= one;
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void byThree() {
		final double d = 3;
		double[] values = tl.get();
		for ( int i = values.length - 1; i >= 0; -- i) {
			values[ i] /= d;
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void timesTwo() {
		double[] values = tl.get();
		final double factor = 2;
		for ( int i = values.length - 1; i >= 0; -- i) {
			values[ i] *= factor;
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void timesOtherNoCheck() {
		double[] values = tl.get();
		final double factor = 1.6;
		for ( int i = values.length - 1; i >= 0; -- i) {
			values[ i] *= factor;
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void plusOther() {
		double[] values = tl.get();
		final double summand = 1.6;
		for ( int i = values.length - 1; i >= 0; -- i) {
			values[ i] += summand;
		}
	}



	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( MulDivBench.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .measurementTime( TimeValue.seconds( 5))
		        .timeUnit(TimeUnit.NANOSECONDS)
		        .warmupIterations(5)
		        .measurementIterations(5)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
