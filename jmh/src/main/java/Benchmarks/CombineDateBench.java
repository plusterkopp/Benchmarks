package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.time.*;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)

public class CombineDateBench {

	public static final ZoneId zoneID = ZoneId.of( "Europe/Berlin");

	private static final int NumValues = 24;
	private LocalTime[] localTimesA;
	private LocalDate[] localDatesA;
	private long[] localDateSecondsA;
	private int index = 0;

	private int nextIndex() {
		int result = index;
		if ( ++index >= NumValues) {
			index = 0;
		}
		return result;
	}

	@Setup( Level.Trial)
	public void setup() {
		localTimesA = new LocalTime[ NumValues];
		Arrays.setAll( localTimesA, i -> LocalTime.of( i % 12, 0));
		localDatesA = new LocalDate[ NumValues];
		Arrays.setAll( localDatesA, i -> {
			if ( i < 12) {
				return LocalDate.of( 2026, 10, 26);
			}
			return LocalDate.of( 2027, 3, 28);
		});
		localDateSecondsA = new long[ NumValues];
		Arrays.setAll( localDateSecondsA, i ->
			localDatesA[ i].atStartOfDay( zoneID).toEpochSecond()
			);

		// vergleichen
		for ( int i = 0;  i < NumValues;  i++) {
			Date fromSecondsNanos = combineDateSecondsNanos(i);
			Date fromFull = combineDateEpochFull(i);
			if ( ! fromSecondsNanos.equals( fromFull)) {
				System.err.println(
					"d: " + localDatesA[ i] + " t: " + localTimesA[ i]
					+ " SN: " + fromSecondsNanos + " full: " + fromFull + " at " + i);
//				System.exit( 1);
			}
		}
	}

	private Date combineDateSecondsNanos( int i) {
		long epochSecondsDate = localDateSecondsA[ i];
		long epochNanosTime = localTimesA[ i].toNanoOfDay();
		Instant instant0 = Instant.ofEpochSecond( epochSecondsDate, 0);
		ZonedDateTime zdt0 = instant0.atZone(zoneID);
		ZoneOffset o0 = zdt0.getOffset();
		Instant instant = Instant.ofEpochSecond( epochSecondsDate, epochNanosTime);
		ZonedDateTime zdt = instant.atZone(zoneID);
		ZoneOffset o = zdt.getOffset();

		ZonedDateTime zdtc = zdt;
		int diff = o.getTotalSeconds() - o0.getTotalSeconds();
		if (diff > 0) {
			zdtc = zdt.minusSeconds( diff);
		}

		Date result = asDate( zdt);

//		System.err.println(
//			"d: " + localDatesA[ i] + " t: " + localTimesA[ i]
//				+ " inst: " + instant + " zdt: " + zdt // + " corr: " + zdtc
////				+ " inst0: " + instant0 + " zdt0: " + zdt0
//				+ " dr: " + result + " at " + i);

		return result;
	}

	private Date asDate( ZonedDateTime zdt) {
		Date result = new Date(zdt.getYear() - 1900, zdt.getMonthValue() - 1, zdt.getDayOfMonth()
			, zdt.getHour(), zdt.getMinute(), zdt.getSecond());
		return result;
	}

	private Date combineDateEpochFull( int i) {
		// der Tag bleibt in der richtigen Zeitzone
		LocalDate localDate = localDatesA[ i];
		LocalTime localTime = localTimesA[ i];
		LocalDateTime dt = localDate.atStartOfDay();

		long localTimeNanos = localTime.toNanoOfDay();
		dt = dt.plusNanos( localTimeNanos);
		ZonedDateTime zonedUTC = dt.atZone( ZoneOffset.UTC);
		ZonedDateTime zoned = zonedUTC.withZoneSameInstant(zoneID);

		Date date = Date.from( zoned.toInstant());
		return date;
	}

	@Benchmark
	public Date combineDateSecondsNanos() {
		int i = nextIndex();
		return combineDateSecondsNanos( i);
	}

	@Benchmark
	public Date combineDateEpochFull() {
		int i = nextIndex();
		return combineDateEpochFull( i);
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
			.include( CombineDateBench.class.getSimpleName())
			.warmupIterations(3)
			.warmupTime( TimeValue.seconds( 1))
			.measurementIterations(5)
			.measurementTime( TimeValue.seconds( 1))
			.build();
        new Runner(opt).run();
    }
}
