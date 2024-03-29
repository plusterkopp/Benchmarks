package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class DateCompare {

	Date    date1;
	Date    date2;

	Instant inst1;
	LocalDate ld1;
	Instant inst2;
	LocalDate ld2;

	Calendar cal1 = Calendar.getInstance();
	Calendar cal2 = Calendar.getInstance();

	@Setup(Level.Trial)
	public void setup() {
		date1 = new Date();
		date2 = new Date( date1.getTime() + 1);

		inst1 = Instant.ofEpochMilli( date1.getTime());
		ld1 = LocalDate.ofInstant( inst1, ZoneId.systemDefault());
		inst2 = Instant.ofEpochMilli( date2.getTime());
		ld2 = LocalDate.ofInstant( inst2, ZoneId.systemDefault());

		cal1.setTime( date1);
		cal2.setTime( date2);

		if ( compareDateCalSingleCall() != compareDateAsCal()) {
			System.err.println( "mismatch: compareDateCalSingleCall - compareDateAsCal");
		}
		if ( compareDateCalSingleCall() != compareDateAsNewCal()) {
			System.err.println( "mismatch: compareDateCalSingleCall - compareDateAsNewCal");
		}
		if ( compareDateCalSingleCall() != compareDateCalSingleCallDayOfYear()) {
			System.err.println( "mismatch: compareDateCalSingleCall - compareDateCalSingleCallDayOfYear");
		}
		if ( compareDateCalSingleCall() != compareDateDeprecatedYMD()) {
			System.err.println( "mismatch: compareDateCalSingleCall - compareDateDeprecatedYMD");
		}
		if ( compareDateCalSingleCall() != compareDateDeprecatedYMDSingleCall()) {
			System.err.println( "mismatch: compareDateCalSingleCall - compareDateDeprecatedYMDSingleCall");
		}
		if ( compareDateCalSingleCall() != compareDateDeprecatedYMDSingleCall()) {
			System.err.println( "mismatch: compareDateCalSingleCall - compareDateDeprecatedYMDSingleCall");
		}
		if ( compareDateCalSingleCall() != compareDateLocalDateIsBefore()) {
			System.err.println( "mismatch: compareDateCalSingleCall - compareDateLocalDateIsBefore");
		}
		if ( compareDateCalSingleCall() != compareDateLocalDateFromDateIsBefore()) {
			System.err.println( "mismatch: compareDateCalSingleCall - compareDateLocalDateFromDateIsBefore");
		}
	}

	// return true, if day of date1 before day of date2

	@Benchmark
	public boolean compareDateDeprecatedYMD() {
		if ( date1.getYear() < date2.getYear()) {
			return true;
		}
		if ( date1.getYear() > date2.getYear()) {
			return false;
		}
		if ( date1.getMonth() < date2.getMonth()) {
			return true;
		}
		if ( date1.getMonth() > date2.getMonth()) {
			return false;
		}
		if ( date1.getDay() < date2.getDay()) {
			return true;
		}
		return false;
	}

	@Benchmark
	public boolean compareDateDeprecatedYMDSingleCall() {
		int year1 = date1.getYear();
		int year2 = date2.getYear();
		if ( year1 < year2) {
			return true;
		}
		if ( year1 > year2) {
			return false;
		}
		int month1 = date1.getMonth();
		int month2 = date2.getMonth();
		if ( month1 < month2) {
			return true;
		}
		if ( month1 > month2) {
			return false;
		}
		if ( date1.getDay() < date2.getDay()) {
			return true;
		}
		return false;
	}

	@Benchmark
	public boolean compareDateAsCal() {
		if ( cal1.get( Calendar.YEAR) < cal2.get( Calendar.YEAR)) {
			return true;
		}
		if ( cal1.get( Calendar.YEAR) > cal2.get( Calendar.YEAR)) {
			return false;
		}
		if ( cal1.get( Calendar.MONTH) < cal2.get( Calendar.MONTH)) {
			return true;
		}
		if ( cal1.get( Calendar.MONTH) > cal2.get( Calendar.MONTH)) {
			return false;
		}
		if ( cal1.get( Calendar.DAY_OF_MONTH) < cal2.get( Calendar.DAY_OF_MONTH)) {
			return true;
		}
		return false;
	}

	@Benchmark
	public boolean compareDateAsNewCal() {
		Calendar cal1 = Calendar.getInstance();
		cal1.setTime( date1);
		Calendar cal2 = Calendar.getInstance();
		cal2.setTime( date2);

		if ( cal1.get( Calendar.YEAR) < cal2.get( Calendar.YEAR)) {
			return true;
		}
		if ( cal1.get( Calendar.YEAR) > cal2.get( Calendar.YEAR)) {
			return false;
		}
		if ( cal1.get( Calendar.MONTH) < cal2.get( Calendar.MONTH)) {
			return true;
		}
		if ( cal1.get( Calendar.MONTH) > cal2.get( Calendar.MONTH)) {
			return false;
		}
		if ( cal1.get( Calendar.DAY_OF_MONTH) < cal2.get( Calendar.DAY_OF_MONTH)) {
			return true;
		}
		return false;
	}

	@Benchmark
	public boolean compareDateCalSingleCall() {
		int year1 = cal1.get(Calendar.YEAR);
		int year2 = cal2.get(Calendar.YEAR);
		if ( year1 < year2) {
			return true;
		}
		if ( year1 > year2) {
			return false;
		}
		int month1 = cal1.get(Calendar.MONTH);
		int month2 = cal2.get(Calendar.MONTH);
		if ( month1 < month2) {
			return true;
		}
		if ( month1 > month2) {
			return false;
		}
		if ( cal1.get( Calendar.DAY_OF_MONTH) < cal2.get( Calendar.DAY_OF_MONTH)) {
			return true;
		}
		return false;
	}

	@Benchmark
	public boolean compareDateCalSingleCallDayOfYear() {
		int year1 = cal1.get(Calendar.YEAR);
		int year2 = cal2.get(Calendar.YEAR);
		if ( year1 < year2) {
			return true;
		}
		if ( year1 > year2) {
			return false;
		}
		if ( cal1.get( Calendar.DAY_OF_YEAR) < cal2.get( Calendar.DAY_OF_YEAR)) {
			return true;
		}
		return false;
	}

	@Benchmark
	public boolean compareDateIsBefore() {
		return date1.before( date2);
	}

	@Benchmark
	public boolean compareDateLocalDateIsBefore() {
		return ld1.isBefore( ld2);
	}

	@Benchmark
	public boolean compareDateLocalDateFromDateIsBefore() {
		Instant inst1;
		inst1 = Instant.ofEpochMilli( date1.getTime());
		LocalDate ld1;
		ld1 = LocalDate.ofInstant( inst1, ZoneId.systemDefault());
		Instant inst2;
		inst2 = Instant.ofEpochMilli( date2.getTime());
		LocalDate ld2;
		ld2 = LocalDate.ofInstant( inst2, ZoneId.systemDefault());

		return ld1.isBefore( ld2);
	}



	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( DateCompare.class.getSimpleName())
		        .warmupIterations(5)
	            .warmupTime(TimeValue.seconds(2))
		        .measurementIterations(5)
				.measurementTime(TimeValue.seconds(5))
				.forks(1)
                .build();
        new Runner(opt).run();
    }
}
