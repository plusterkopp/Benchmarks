package Benchmarks;

import java.util.*;
import java.util.concurrent.*;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import utils.*;


@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class StringBuilderEqualsBench {

	private CharSequence seqA;
	private CharSequence[] seqArray;

	@Setup(Level.Trial)
	public void setup() throws RunnerException {
		seqA = new StringBuilder( "0123456789");
		seqArray = new CharSequence[ seqA.length() + 1];
		// ein SB mehr als wir Zeichen haben
		for ( int i = 0;  i < seqArray.length;  i++) {
			StringBuilder sb = new StringBuilder(seqA);
			// ersetze ein Zeichen, oder beim letzten SB kein Zeichen mehr
			if ( i < sb.length()) {
				sb.setCharAt(i, '_');
			}
			seqArray[ i] = sb;
		}
		boolean equalsAsString[] = new boolean[ seqArray.length];
		String seqAString = seqA.toString();
		for (int i = 0; i < seqArray.length; i++) {
			equalsAsString[ i] = seqAString.equals( seqArray[ i].toString());
		}
		// Teste gegen Utils
		boolean equalsAsUtils[] = new boolean[ seqArray.length];
		for (int i = 0; i < seqArray.length; i++) {
			equalsAsUtils[ i] = Utils.safeEquals( seqA, seqArray[ i]);
		}
		if ( ! Arrays.equals( equalsAsString, equalsAsUtils)) {
			throw new RunnerException( "equals mismatch string: " + Arrays.toString( equalsAsString) + " utils: " + Arrays.toString( equalsAsUtils));
		}
		// Teste gegen SB Equals
//		boolean equalsAsSBEquals[] = new boolean[ seqArray.length];
//		for (int i = 0; i < seqArray.length; i++) {
//			equalsAsSBEquals[ i] = seqA.equals( seqArray[ i]);
//		}
//		if ( ! Arrays.equals( equalsAsString, equalsAsSBEquals)) {
//			throw new RunnerException( "equals mismatch string: " + Arrays.toString( equalsAsString) + " sb equals: " + Arrays.toString( equalsAsSBEquals));
//		}


	}

	@Benchmark
	@OperationsPerInvocation( 10)	// Länge von seqA
	public boolean equalsUtils() {
		boolean result = false;
		for ( int i = 0;  i < seqArray.length;  i++) {
			result ^= Utils.safeEquals( seqA, seqArray[ i]);
		}
		return result;
	}

	@Benchmark
	@OperationsPerInvocation( 10)	// Länge von seqA
	public boolean equalsSBCompare() {
		boolean result = false;
		for ( int i = 0;  i < seqArray.length;  i++) {
			StringBuilder seqASB = (StringBuilder) seqA;
			StringBuilder another = (StringBuilder) seqArray[i];
			result ^= ( seqASB.compareTo( another) == 0);
		}
		return result;
	}

	@Benchmark
	@OperationsPerInvocation( 10)	// Länge von seqA
	public boolean equalsStringEquals() {
		boolean result = false;
		for ( int i = 0;  i < seqArray.length;  i++) {
			String seqAString = seqA.toString();
			String s = seqArray[i].toString();
			result ^= seqAString.equals( s);
		}
		return result;
	}

	@Benchmark
	@OperationsPerInvocation( 10)	// Länge von seqA
	public boolean equalsStringEqualsOpt() {
		boolean result = false;
		String seqAString = seqA.toString();
		for ( int i = 0;  i < seqArray.length;  i++) {
			String s = seqArray[i].toString();
			result ^= seqAString.equals( s);
		}
		return result;
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( StringBuilderEqualsBench.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .timeUnit(TimeUnit.NANOSECONDS)
		        .warmupIterations(5)
				.warmupTime( TimeValue.seconds( 1))
		        .measurementIterations(5)
				.measurementTime( TimeValue.seconds( 5))
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
