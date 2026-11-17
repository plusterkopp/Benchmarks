package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import utils.MMKF4JavaStub;

import java.time.*;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Compares the classic {@link MMKF4JavaStub} Date &lt;-&gt; Excel-date conversions
 * (based on {@code TimeZone.getDefault()} offset arithmetic) with an equivalent
 * conversion built on {@code java.time}.
 */
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ExcelDateConversionBench {

	// DST-observing, non-UTC zone: forced as JVM default so the classic
	// (TimeZone.getDefault()-based) and java.time conversions are directly
	// comparable regardless of the host machine's configured zone.
	private static final ZoneId ZONE = ZoneId.of( "Europe/Berlin");
	private static final long NANOS_PER_DAY = 24L * 60 * 60 * 1_000_000_000L;

	final int ArraySizeM = 1;
	final int ArraySize = ArraySizeM * 1000 * 1000;

	private ThreadLocal<long[]> tlMillis;
	private ThreadLocal<Date[]> tlDates;
	private ThreadLocal<double[]> tlExcelDates;

	private double dummyD;
	private Date dummyDate;
	private long dummyMillis;

	@Setup(Level.Trial)
	public void setup() {
		TimeZone.setDefault( TimeZone.getTimeZone( ZONE));

		checkDstInvariant();
		checkClassicMatchesJavaTime();

		Instant iMin = Instant.parse( "2015-01-01T00:00:00.000Z");
		Instant iMax = Instant.parse( "2025-01-01T00:00:00.000Z");
		long millisDist = iMax.toEpochMilli() - iMin.toEpochMilli();

		tlMillis = ThreadLocal.withInitial( () -> {
			long[] arr = new long[ ArraySize];
			Random rnd = new Random();
			for ( int i = arr.length - 1; i >= 0; -- i) {
				long dist = (long) (rnd.nextDouble() * millisDist);
				arr[ i] = iMin.toEpochMilli() + dist;
			}
			return arr;
		});

		// Date[] derived from the same millis so all benchmark families operate on identical data
		tlDates = ThreadLocal.withInitial( () -> {
			long[] millis = tlMillis.get();
			Date[] arr = new Date[ millis.length];
			for ( int i = millis.length - 1; i >= 0; -- i) {
				arr[ i] = new Date( millis[ i]);
			}
			return arr;
		});

		tlExcelDates = ThreadLocal.withInitial( () -> {
			Date[] dates = tlDates.get();
			double[] arr = new double[ dates.length];
			for ( int i = dates.length - 1; i >= 0; -- i) {
				arr[ i] = MMKF4JavaStub.toAccurateExcelDate( dates[ i]);
			}
			return arr;
		});
	}

	@Setup(Level.Iteration)
	public void get() {
		tlMillis.get();
		tlDates.get();
		tlExcelDates.get();
	}

	/**
	 * The required guarantee: in a non-UTC, DST-observing zone, two dates that
	 * share the same time of day but fall on different calendar days (here:
	 * spanning the Europe/Berlin spring DST transition) must convert to Excel
	 * dates whose difference is an integer, i.e. the time-of-day fraction must
	 * not be perturbed by the DST offset change.
	 */
	private void checkDstInvariant() {
		Date before = Date.from( LocalDateTime.of( 2026, 3, 1, 10, 0).atZone( ZONE).toInstant());
		Date after = Date.from( LocalDateTime.of( 2026, 6, 1, 10, 0).atZone( ZONE).toInstant());

		checkIntegerDiff( "classic", MMKF4JavaStub.toAccurateExcelDate( before), MMKF4JavaStub.toAccurateExcelDate( after));
		checkIntegerDiff( "javaTime", toAccurateExcelDateJavaTime( before), toAccurateExcelDateJavaTime( after));
	}

	private void checkIntegerDiff( String label, double excelBefore, double excelAfter) {
		double diff = excelAfter - excelBefore;
		if ( Math.abs( diff - Math.rint( diff)) > 1e-9) {
			System.err.println( "DST invariant violated (" + label + "): " + excelBefore + " -> " + excelAfter + ", diff " + diff);
		}
	}

	private void checkClassicMatchesJavaTime() {
		Date[] samples = {
			new Date(),
			Date.from( LocalDateTime.of( 2026, 10, 25, 1, 30).atZone( ZONE).toInstant()),
			Date.from( LocalDateTime.of( 2026, 10, 25, 3, 30).atZone( ZONE).toInstant()),
			Date.from( LocalDateTime.of( 2027, 3, 28, 3, 30).atZone( ZONE).toInstant())
		};
		for ( Date date: samples) {
			double classic = MMKF4JavaStub.toAccurateExcelDate( date);
			double javaTime = toAccurateExcelDateJavaTime( date);
			if ( Math.abs( classic - javaTime) > 1e-9) {
				System.err.println( "mismatch for " + date + ": classic=" + classic + " javaTime=" + javaTime);
			}
		}
	}

	public static double toAccurateExcelDateJavaTime( Date date) {
		return toAccurateExcelDateJavaTime( date.toInstant().atZone( ZONE));
	}

	public static double toAccurateExcelDateJavaTime( ZonedDateTime zdt) {
		long epochDay = zdt.toLocalDate().toEpochDay();
		double dayFraction = zdt.toLocalTime().toNanoOfDay() / (double) NANOS_PER_DAY;
		double d = epochDay + dayFraction;
		// emulate the same Windows 1900-leap-year bug as MMKF4JavaStub
		if ( d > 60) {
			d++;
		}
		return d;
	}

	public static Date fromAccurateExcelDateJavaTime( double excelDate) {
		return Date.from( fromAccurateExcelDateJavaTimeZDT( excelDate).toInstant());
	}

	public static ZonedDateTime fromAccurateExcelDateJavaTimeZDT( double excelDate) {
		if ( excelDate > 61) {
			excelDate--;
		}
		long epochDay = (long) Math.floor( excelDate);
		double dayFraction = excelDate - epochDay;
		long nanoOfDay = Math.min( (long) (dayFraction * NANOS_PER_DAY), NANOS_PER_DAY - 1);
		LocalDateTime ldt = LocalDateTime.of( LocalDate.ofEpochDay( epochDay), LocalTime.ofNanoOfDay( nanoOfDay));
		return ldt.atZone( ZONE);
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void toExcelDateClassic() {
		Date[] dates = tlDates.get();
		for ( int i = dates.length - 1; i >= 0; -- i) {
			dummyD = MMKF4JavaStub.toAccurateExcelDate( dates[ i]);
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void toExcelDateJavaTime() {
		Date[] dates = tlDates.get();
		for ( int i = dates.length - 1; i >= 0; -- i) {
			dummyD = toAccurateExcelDateJavaTime( dates[ i]);
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void fromExcelDateClassic() {
		double[] excelDates = tlExcelDates.get();
		for ( int i = excelDates.length - 1; i >= 0; -- i) {
			dummyDate = MMKF4JavaStub.fromAccurateExcelDate( excelDates[ i]);
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void fromExcelDateJavaTime() {
		double[] excelDates = tlExcelDates.get();
		for ( int i = excelDates.length - 1; i >= 0; -- i) {
			dummyDate = fromAccurateExcelDateJavaTime( excelDates[ i]);
		}
	}

	// The variants below start/end at a raw epoch-milli long instead of a
	// pre-built Date, to also measure the cost of the Date <-> millis boundary.

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void toExcelDateClassicFromMillis() {
		long[] millis = tlMillis.get();
		for ( int i = millis.length - 1; i >= 0; -- i) {
			Date date = new Date( millis[ i]);
			dummyD = MMKF4JavaStub.toAccurateExcelDate( date);
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void fromExcelDateClassicToMillis() {
		double[] excelDates = tlExcelDates.get();
		for ( int i = excelDates.length - 1; i >= 0; -- i) {
			Date date = MMKF4JavaStub.fromAccurateExcelDate( excelDates[ i]);
			dummyMillis = date.getTime();
		}
	}

	// java.time never needs a Date here: it goes straight from millis to
	// ZonedDateTime and back, unlike the classic conversion which is bound to
	// the Date-based MMKF4JavaStub API.

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void toExcelDateJavaTimeFromMillis() {
		long[] millis = tlMillis.get();
		for ( int i = millis.length - 1; i >= 0; -- i) {
			ZonedDateTime zdt = Instant.ofEpochMilli( millis[ i]).atZone( ZONE);
			dummyD = toAccurateExcelDateJavaTime( zdt);
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void fromExcelDateJavaTimeToMillis() {
		double[] excelDates = tlExcelDates.get();
		for ( int i = excelDates.length - 1; i >= 0; -- i) {
			ZonedDateTime zdt = fromAccurateExcelDateJavaTimeZDT( excelDates[ i]);
			dummyMillis = zdt.toInstant().toEpochMilli();
		}
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
			.include( ExcelDateConversionBench.class.getSimpleName())
			.mode( Mode.AverageTime)
			.measurementTime( TimeValue.seconds( 5))
			.measurementIterations( 3)
			.timeUnit( TimeUnit.NANOSECONDS)
			.warmupIterations( 3)
			.warmupTime( TimeValue.seconds( 1))
			.forks( 1)
			.build();
		new Runner( opt).run();
	}
}
