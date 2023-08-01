package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class FormatterThreadedBench {

	public static final String YYYY_MM_DD = "yyyy-MM-dd";
	static final ThreadLocal<DateFormat> DateFormatTL = ThreadLocal.withInitial( () -> new SimpleDateFormat( YYYY_MM_DD));
	static final DateFormat sharedDF = new SimpleDateFormat( YYYY_MM_DD);
	static final Date date1 = new Date();
	static final Date date2 = new Date( date1.getTime() + 10000000);

	static final String formattedDate1 = sharedDF.format( date1);
	static final String formattedDate2 = sharedDF.format( date2);

	static final LongAdder mismatchAdder = new LongAdder();
	static final LongAdder totalAdder = new LongAdder();

	@State(Scope.Thread)
	@AuxCounters(AuxCounters.Type.EVENTS)
	public static class MismatchEvent {

		public void check(String s, Date date) {
			if ( date.equals( date1)) {
				if ( ! formattedDate1.equals(s)) {
					mismatchAdder.increment();
				}
			} else if ( date.equals( date2)) {
				if ( ! formattedDate2.equals(s)) {
					mismatchAdder.increment();
				}
			}
			totalAdder.increment();
		}

		public long totalMismatches() {
			return mismatchAdder.longValue();
		}
		public long total() {
			return totalAdder.sumThenReset();
		}
	}

	private Date selectDate() {
		if ( Math.random() < 0.5) {
			return date1;
		}
		return date2;
	}

	private String doIt( DateFormat df) {
		Date date = selectDate();
		String s = df.format( date);
		return s;
	}

	private String checkIt( DateFormat df, MismatchEvent me) {
		Date date = selectDate();
		String s = df.format( date);
		me.check( s, date);
		return s;
	}

	@Benchmark
	public String privateNewFormatUnchecked() {
		SimpleDateFormat df = new SimpleDateFormat( YYYY_MM_DD);
		return doIt( df);
	}

	@Benchmark
	public String privateNewFormat( MismatchEvent me) {
		SimpleDateFormat df = new SimpleDateFormat( YYYY_MM_DD);
		return checkIt( df, me);
	}

	@Benchmark
    @GroupThreads( 4)
    public String privateNewFormatT4( MismatchEvent me) {
	    SimpleDateFormat df = new SimpleDateFormat( YYYY_MM_DD);
		return checkIt( df, me);
    }

	@Benchmark
	public String sharedFormatUnchecked( MismatchEvent me) {
		DateFormat df = sharedDF;
		return checkIt( df, me);
	}

	@Benchmark
	public String sharedFormat( MismatchEvent me) {
		DateFormat df = sharedDF;
		return checkIt( df, me);
	}

	@Benchmark
	@GroupThreads( 4)
	public String sharedFormatT4( MismatchEvent me) {
		DateFormat df = sharedDF;
		return checkIt( df, me);
	}

	@Benchmark
	public String tlFormatUnchecked( MismatchEvent me) {
		DateFormat df = DateFormatTL.get();
		return checkIt( df, me);
	}

	@Benchmark
	public String tlFormat( MismatchEvent me) {
		DateFormat df = DateFormatTL.get();
		return checkIt( df, me);
	}

	@Benchmark
	@GroupThreads( 4)
	public String tlFormatT4( MismatchEvent me) {
		DateFormat df = DateFormatTL.get();
		return checkIt( df, me);
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( FormatterThreadedBench.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .timeUnit(TimeUnit.NANOSECONDS)
		        .warmupIterations(1)
				.warmupTime( TimeValue.seconds( 1))
		        .measurementIterations(5)
				.measurementTime( TimeValue.seconds( 5))
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
