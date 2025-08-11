package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class NatLog {

	static double xValues[];
	static double xValuesM1[];
	static int exponents[];
	static final int numExponents = 20;
	static final int maxExponent = 3;

	@Setup(Level.Trial)
	public static void setup() {
		xValues = new double[ numExponents];
		xValuesM1 = new double[ numExponents];
		exponents = new int[ numExponents];
		for ( int i = 0;  i < numExponents;  i++) {
			int ex = maxExponent - i;
			exponents[ i] = ex;
			xValuesM1[ i] = Math.pow( 10, ex);
			xValues[ i] = 1 + xValuesM1[ i];
		}
	}

	private static void checkDiffs() {
		setup();
		for ( int i = 0;  i < numExponents;  i++) {
			double resultLog = Math.log( xValues[ i]);
			double resultLog1p = Math.log1p( xValuesM1[ i]);
			if (resultLog != resultLog1p) {
				System.out.println( "at " + i + ": " +
						"ln(" + xValues[ i] + ") != ln1p(" + xValuesM1[ i] + ") " +
						"diff:" + (resultLog - resultLog1p));
			}
		}
	}

	@Benchmark
	@OperationsPerInvocation(numExponents)
	public double ln() {
		double sum = 0;
		for ( int i = 0;  i < numExponents;  i++) {
			double resultLog = Math.log( xValues[ i]);
			sum += resultLog;
		}
		return sum;
	}

	@Benchmark
	@OperationsPerInvocation(maxExponent)
	public double lnLT1() {
		double sum = 0;
		for ( int i = 0;  i < maxExponent;  i++) {
			double resultLog = Math.log( xValues[ i]);
			sum += resultLog;
		}
		return sum;
	}

	@Benchmark
	public double ln10E3() {
		return Math.log( xValues[ 0]);
	}
	@Benchmark
	public double ln10EM3() {
		return Math.log( xValues[ 6]);
	}
	@Benchmark
	public double ln1p10EM3() {
		return Math.log1p( xValuesM1[ 6]);
	}

	@Benchmark
	@OperationsPerInvocation(numExponents)
	public double ln1p() {
		double sum = 0;
		for ( int i = 0;  i < numExponents;  i++) {
			double resultLog1p = Math.log1p( xValuesM1[ i]);
			sum += resultLog1p;
		}
		return sum;
	}



	public static void main(String[] args) throws RunnerException {
		checkDiffs();
        Options opt = new OptionsBuilder()
                .include( NatLog.class.getSimpleName())
		        .warmupIterations(5)
				.warmupTime(TimeValue.seconds(1))
		        .measurementTime(TimeValue.seconds( 5))
				.measurementIterations( 1)
				.timeUnit( TimeUnit.NANOSECONDS)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
