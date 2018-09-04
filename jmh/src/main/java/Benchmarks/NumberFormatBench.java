package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;
import utils.*;

import java.math.BigDecimal;
import java.text.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class NumberFormatBench {

	public static final int CAPACITY = 200;
	static double dummyD;
	static BigDecimal bd;

	private static List<String> valueL;
	private static String[]	valueA;
	private static String[]	resultA;

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

	final static int Size = 1000;
	static NumberFormat nf;

	private static void setupStatics() {
		valueL = new ArrayList<String>();
		nf = DecimalFormat.getNumberInstance( Locale.US);
		nf.setGroupingUsed( false);
		nf.setMaximumFractionDigits( 4);
		for ( double d : values) {
			valueL.add( nf.format( d));
		}
		Random	rnd = new Random( 1);
		while ( valueL.size() < Size) {
			final double v = Math.pow(10, 6 * rnd.nextDouble() - 3);
			valueL.add( nf.format(v));
		}
		valueA = valueL.toArray( new String[ valueL.size()]);

		dummyDA = new double[ valueA.length];
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			dummyDA[ i] = Double.valueOf( valueA[ i]);
		}

		dummyBDA = new BigDecimal[ valueA.length];
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			dummyBDA[ i] = new BigDecimal( valueA[ i]);
		}

		resultA = new String[ valueA.length];
		System.out.println( "Using " + Runtime.getRuntime().availableProcessors() + " hw threads");
	}

	@Setup(Level.Trial)
	public static void setup() {
		setupStatics();
	}

//	@Benchmark
//	@OperationsPerInvocation( Size)
	public void formatBDFull() {
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			resultA[ i] = dummyBDA[ i].toPlainString();
		}
	}

//	@Benchmark
//	@OperationsPerInvocation( Size)
	public void formatBDSingle() {
		final int digits = nf.getMaximumFractionDigits();
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			resultA[ i] = dummyBDA[ i].setScale(digits).toPlainString();
		}
	}

	@Benchmark
	@OperationsPerInvocation( Size)
	public void formatBDDouble() {
		final int digits = nf.getMaximumFractionDigits();
		final StringBuilder sb = new StringBuilder(CAPACITY);
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			sb.setLength( 0);
			sb.append( dummyBDA[ i].setScale( 13).setScale(digits).toPlainString());
		}
	}

	@Benchmark
	@OperationsPerInvocation( Size)
	public void formatBDDoubleNew() {
		final int digits = nf.getMaximumFractionDigits();
		final StringBuilder sb = new StringBuilder(CAPACITY);
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			sb.setLength( 0);
			BigDecimal bd = BigDecimal.valueOf( dummyDA[ i]);
			sb.append( bd.setScale( 13).setScale(digits).toPlainString());
		}
	}

//	@Benchmark
//	@OperationsPerInvocation( Size)
	public void formatNF() {
		final StringBuilder sb = new StringBuilder(CAPACITY);
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			sb.setLength( 0);
			sb.append( nf.format( dummyDA[ i]));
		}
	}

//	@Benchmark
//	@OperationsPerInvocation( Size)
	public void formatJ() {
		final StringBuilder sb = new StringBuilder(CAPACITY);
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			sb.setLength( 0);
			sb.append( dummyDA[ i]);
		}
	}

//	@Benchmark
//	@OperationsPerInvocation( Size)
	public void formatQPDSNStripped() {
		final int digits = nf.getMaximumFractionDigits();
		final StringBuilder sb = new StringBuilder(CAPACITY);
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			sb.setLength( 0);
			QuotePrecision.DSInstance.checkAndformat0( dummyDA[ i], sb,true, digits);
		}
	}

	@Benchmark
	@OperationsPerInvocation( Size)
	public void formatQPDSN() {
		final int digits = nf.getMaximumFractionDigits();
		final StringBuilder sb = new StringBuilder(CAPACITY);
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			sb.setLength( 0);
			QuotePrecision.DSInstance.checkAndformat0( dummyDA[ i], sb,false, digits);
		}
	}

//	@Benchmark
//	@OperationsPerInvocation( Size)
	public void formatQPBDNStripped() {
		final int digits = nf.getMaximumFractionDigits();
		final StringBuilder sb = new StringBuilder(CAPACITY);
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			sb.setLength( 0);
			QuotePrecision.BDInstance.checkAndformat0( dummyDA[ i], sb,true, digits);
		}
	}

	@Benchmark
	@OperationsPerInvocation( Size)
	public void formatQPBDN() {
		final int digits = nf.getMaximumFractionDigits();
		final StringBuilder sb = new StringBuilder(CAPACITY);
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			sb.setLength( 0);
			QuotePrecision.BDInstance.checkAndformat0( dummyDA[ i], sb,false, digits);
		}
	}

//	@Benchmark
//	@OperationsPerInvocation( Size)
	public void formatDoubleToString() {
		StringBuilder	sb = new StringBuilder();
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			sb.setLength( 0);
			DoubleToString.append( sb, dummyDA[ i]);
			resultA[ i] = sb.toString();
		}
	}

	@Benchmark
	@OperationsPerInvocation( Size)
	public void formatDoubleToStringN() {
		final int digits = nf.getMaximumFractionDigits();
		StringBuilder	sb = new StringBuilder();
		for ( int i = valueA.length - 1;  i >= 0;  i--) {
			sb.setLength( 0);
			DoubleToString.appendFormatted( sb, dummyDA[ i],
					digits,'.', ',', 3, '-',  '\uFFFF');
			resultA[ i] = sb.toString();
		}
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( NumberFormatBench.class.getSimpleName())
		        .warmupIterations(1)
		        .warmupTime( TimeValue.seconds( 10))
				.measurementIterations( 3)
		        .measurementTime(TimeValue.seconds( 20))
//		        .addProfiler(GCProfiler.class)
//		        .addProfiler(CompilerProfiler.class)
				.mode( Mode.AverageTime)
				.timeUnit( TimeUnit.MICROSECONDS)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
