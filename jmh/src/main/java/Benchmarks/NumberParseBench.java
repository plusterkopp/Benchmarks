package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
//import sun.misc.FloatingDecimal;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class NumberParseBench {

	static double dummyD;
	static BigDecimal bd;

	private static List<String> valueL;
	private static String[]	valueA;

	static double dummyDA[];
	static BigDecimal dummyBDA[];

	final static double[]		values = {
			12345.67, 12345.5, 12345,
			1234.565, 1234.56, 1234.5, 1234,
			123.4567, 123.455, 123.45, 123.5, 123,
			12.34567, 12.3456, 12.345, 12.34, 12.3, 12, 12.03, 12.005, 12.004,
			1.234567, 1.23456, 1.2345, 1.235, 1.23, 1.2, 1, 1.01, 1.001, 1.0003,
			0.1234567, 0.123456, 0.12345, 0.1235, 0.123, 0.12, 0.5,
			0.01234567, 0.0123456, 0.012345, 0.01234, 0.0125, 0.012, 0.01,
			0.001234567, 0.00123456, 0.0012345, 0.001234, 0.00123, 0.0015, 0.001,
	};

	final static int Size = 100;

	private static void setupStatics() {
		valueL = new ArrayList<String>();
		NumberFormat nf = DecimalFormat.getNumberInstance( Locale.US);
		nf.setGroupingUsed( false);
		nf.setMaximumFractionDigits( 4);
		for ( double d : values) {
			valueL.add( nf.format( d));
		}
		Random	rnd = new Random( 1);
		while ( valueL.size() < Size) {
			final double v = Math.pow(10, 6 * rnd.nextDouble() - 4);
			valueL.add( nf.format(v));
		}
		valueA = valueL.toArray( new String[ valueL.size()]);
		dummyDA = new double[ valueA.length];
		dummyBDA = new BigDecimal[ valueA.length];
	}

	@Setup(Level.Trial)
	public static void setup() {
		setupStatics();
	}

	@Benchmark
	@OperationsPerInvocation( Size)
	public void parseDoubleValueOf() {
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			dummyDA[ i] = Double.valueOf( valueA[ i]);
		}
	}

//	@Benchmark
//	@OperationsPerInvocation( Size)
//	public void parseFloatingDecimal() {
//		for ( int i = valueA.length - 1;  i >= 0;  i--) {
//			dummyDA[ i] = FloatingDecimal.parseDouble( valueA[ i]);
//		}
//	}
//
	@Benchmark
	@OperationsPerInvocation( Size)
	public void parseBigDecimal() {
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			dummyBDA[ i] = new BigDecimal( valueA[ i]);
		}
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( NumberParseBench.class.getSimpleName())
		        .warmupIterations(5)
		        .measurementTime(TimeValue.seconds( 20))
				.measurementIterations( 3)
				.mode( Mode.AverageTime)
				.timeUnit( TimeUnit.MICROSECONDS)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
