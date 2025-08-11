package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class DoubleSearch {

	@Param({
			"1"
			, "3"
			, "10"
			, "30"
			, "100"
	})
	int size;
	double array[];
	final int searchValueCount = 10;
	double searchValues[];

	boolean setupFinished = false;
	@Setup(Level.Trial)
	public void setup() {
//		if ( setupFinished) {
//			return;
//		}
		array = new double[ size];
		searchValues = new double[ searchValueCount];
		Random rnd = new Random();
		Set<Double> aSet = new HashSet<>( array.length);
		aSet.add( Double.NEGATIVE_INFINITY);
		while ( aSet.size() < array.length) {
			double r = rnd.nextInt( 1000);
			aSet.add( r);
		}
		Set<Double> sSet = new HashSet<>( searchValues.length);
		while ( sSet.size() < searchValues.length) {
			double r = rnd.nextInt( 1000);
			sSet.add( r);
		}
		array = aSet.stream().sorted().mapToDouble( d -> d).toArray();
		searchValues = sSet.stream().mapToDouble( d -> d).toArray();
		System.err.println( "array: " + Arrays.toString( array) + ", search: " + Arrays.toString( searchValues));
		test();
		setupFinished = true;
	}

	private void test() {
		int index0A[] = new int[ searchValues.length];
		int index1A[] = new int[ searchValues.length];
		for ( int i = 0;  i < searchValues.length;  i++) {
			index0A[ i] = search0( array, searchValues[ i]);
			index1A[ i] = search1( array, searchValues[ i]);
		}
		if ( ! Arrays.equals( index0A, index1A)) {
			System.err.println( "mismatch, 0: " + Arrays.toString( index0A) + ", 1: " + Arrays.toString( index1A));
			System.exit( 0);
		}
	}

	static int search0(double array[], double x) {
		int result = -1;
		for ( int i = 0;  i < array.length;  i++) {
			double a = array[ i];
			if ( x < a) {
				break;
			} else {
				result = i;
			}
		}
		return result;
	}

	static int search1( double array[], double x) {
		int ind = Arrays.binarySearch(array, x);
		if ( ind >= 0) {
			return ind;
		}
		if ( ind == -1) {
			return ind;
		}
		return - ( 2 + ind);
	}

	@Benchmark
	@BenchmarkMode(Mode.AverageTime)
	@OperationsPerInvocation( searchValueCount)
	public int searchLinear() {
		int r = 0;
		for ( int i = 0;  i < searchValues.length;  i++) {
			r += search0( array, searchValues[ i]);
		}
		return r;
	}

	@Benchmark
	@BenchmarkMode(Mode.AverageTime)
	@OperationsPerInvocation( searchValueCount)
	public int searchBinary() {
		int r = 0;
		for ( int i = 0;  i < searchValues.length;  i++) {
			r += search1( array, searchValues[ i]);
		}
		return r;
	}

	public static void main(String[] args) throws RunnerException {
	    Options opt = new OptionsBuilder()
			    .include( DoubleSearch.class.getSimpleName())
			    .mode( Mode.AverageTime)
			    .warmupIterations(5)
			    .warmupTime( TimeValue.seconds( 1))
			    .measurementIterations(5)
			    .measurementTime( TimeValue.seconds( 10))
			    .forks(1)
			    .build();
	    new Runner(opt).run();
    }
}
