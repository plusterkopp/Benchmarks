package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class DateParseBench {

	/**
	 * yyyy-MM-dd HH:mm:ss
	 */
	static ThreadLocal<DateFormat> DF_yyyy_MM_dd_hh_mm_ss_Hyphen = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		}
	};

	/**
	 * yyyy-MMdd-HHmmss Datei- und Verzeichnisnamenskomponente für Logging, daher etwas kompatker
	 */
	static ThreadLocal<DateFormat> DF_yyyy_MMdd_hhmmss = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("yyyy-MMdd-HHmmss");
		}
	};

	/**
	 * yyyy-MMdd-HHmmss.SSS Datei- und Verzeichnisnamenskomponente für Logging, daher etwas kompatker
	 */
	static ThreadLocal<DateFormat> DF_yyyy_MMdd_hhmmssSSS = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("yyyy-MMdd-HHmmss.SSS");
		}
	};

	/**
	 * yyyy-MM-dd HH:mm:ss.SSS	was bei log4j2 DEFAULT_PERIOD heißt (aber wohl nicht mehr dokumentiert ist)
	 */
	static ThreadLocal<DateFormat> DF_yyyy_MM_dd_hh_mm_ss_SSS = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		}
	};

	/**
	 * yyyyMMdd<br>
	 * darf nicht lenient sein, da sonst Datumswerte mit Bindestrich falsch geparst werden
	 */
	static ThreadLocal<DateFormat> DF_yyyyMMdd = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
			sdf.setLenient( false);
			return sdf;
		}
	};
	/**
	 * yyyyMMdd HH:mm:ss
	 */
	static ThreadLocal<DateFormat> DF_yyyyMMdd_hh_mm_ss = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("yyyyMMdd HH:mm:ss");
		}
	};
	/**
	 * dd.MM.yyyy
	 */
	static ThreadLocal<DateFormat> DF_dd_MM_yyyy = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
			sdf.setLenient( false);
			return sdf;
		}
	};
	/**
	 * dd.MM.yyyy HH:mm:ss
	 */
	static ThreadLocal<DateFormat> DF_dd_MM_yyyy_hh_mm_ss = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
		}
	};
	/**
	 * DATETIME. Eigentlich veraltetes Datumsformat, das noch Verwendung findet.
	 */
	@Deprecated ThreadLocal<DateFormat> DF_DATETIME = DF_dd_MM_yyyy_hh_mm_ss;

	/**
	 * dd MMM yyyy, Locale.US
	 */
	static ThreadLocal<DateFormat> DF_dd_MMM_yyyy_US = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("dd MMM yyyy", Locale.US);
		}
	};
	/**
	 * dd MMM yyyy, Locale.DE
	 */
	static ThreadLocal<DateFormat> DF_dd_MMM_yyyy_DE = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("dd MMM yyyy", Locale.GERMAN);
		}
	};
	/**
	 * dd MMM yyyy HH:mm:ss, Locale.US
	 */
	static ThreadLocal<DateFormat> DF_dd_MMM_yyyy_hh_mm_ss = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US);
		}
	};
	/**
	 * yyyy.MM.dd HH:mm:ss
	 */
	static ThreadLocal<DateFormat> DF_yyyy_MM_dd_hh_mm_ss = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
		}
	};
	/**
	 * yyyy-MM-dd
	 */
	static ThreadLocal<DateFormat> DF_yyyy_MM_dd = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("yyyy-MM-dd");
		}
	};
	/**
	 * dieses angelsächsisch angehauchte Format yyyy.MM.dd ist Unsinn, weil im englischen Sprachraum mit Bindestrichen
	 * getrennt wird
	 */
	static ThreadLocal<DateFormat> DF_yyyy_MM_dd_Dots = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("yyyy.MM.dd");
		}
	};

	/**
	 * ddMMMyy US
	 */
	static ThreadLocal<DateFormat> DF_dd_MMM_yy_US = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("ddMMMyy", Locale.US);
		}
	};
	/**
	 * ddMMMyy DE
	 */
	static ThreadLocal<DateFormat> DF_dd_MMM_yy_DE = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("ddMMMyy", Locale.GERMAN);
		}
	};
	static ThreadLocal<DateFormat> DF_dd_MM = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("dd.MM.");
		}
	};
	/**
	 * MM/dd/yyyy
	 */
	static ThreadLocal<DateFormat> DF_MM_dd_yyyy_slashes = ThreadLocal.withInitial(() -> new SimpleDateFormat("MM/dd/yyyy"));
	/**
	 * dd/MM/yy
	 */
	static ThreadLocal<DateFormat> DF_dd_MM_yy_slashes = ThreadLocal.withInitial(() -> new SimpleDateFormat("dd/MM/yy"));
	/**
	 * HH:mm
	 */
	static ThreadLocal<DateFormat> DF_Time_HH_mm = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("HH:mm");
		}
	};
	/**
	 * HH:mm:ss
	 */
	static ThreadLocal<DateFormat> DF_Time_HH_mm_ss = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("HH:mm:ss");
		}
	};
	/**
	 * HH:mm:ss
	 */
	static ThreadLocal<DateFormat> DF_Time_mm_ss = ThreadLocal.withInitial( () -> new SimpleDateFormat("HH:mm:ss"));
	/**
	 * HH:mm:ss, GMT
	 */
	static ThreadLocal<DateFormat> DF_Time_HH_mm_ss_GMT = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			DateFormat df = new SimpleDateFormat("HH:mm:ss");
			df.setTimeZone( TimeZone.getTimeZone( "GMT"));
			return df;
		}
	};
	/**
	 * HH:mm:ss:SSS (Doppelpunkt vor den Millis, because reasons)
	 */
	static ThreadLocal<DateFormat> DF_Time_HH_mm_ss__SSS = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("HH:mm:ss:SSS");
		}
	};
	/**
	 * HH:mm:ss.SSS
	 */
	static ThreadLocal<DateFormat> DF_Time_HH_mm_ss_SSS = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("HH:mm:ss.SSS");
		}
	};
	/**
	 * yyyy.MM.dd HH:mm:ss.SSS <br>
	 * das für den Client, von der Architektur gutgeheißen
	 */
	static ThreadLocal<DateFormat> DF_yyyy_MM_dd_HH_mm_ss_SSS = new ThreadLocal<>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS");
		}
	};

	@State(Scope.Thread)
	@AuxCounters(AuxCounters.Type.EVENTS)
	public static class OpCounters {
		// These fields would be counted as metrics
		public double attempts;
		public double exceptions;
		public double successes;

		public double successRate() {
			return 100.0 * successes / attempts;
		}
	}


	List<String> dates;
	List<ThreadLocal<DateFormat>> tls;

	ThreadLocal<DateFormat[]> tlBundle;

	AtomicInteger dateIndex = new AtomicInteger();


	@Setup(Level.Trial)
	public void setup() {
		dates = new ArrayList<>( List.of(
			"12.04.2003"
			, "12.04.2003 12:04:05"
			, "2003.11.23 12:04:05.432"
			, "2003-11-23 12:04:05.432"
			, "12 MAR 1999"
			, "12MAR19"
			, "12 DEZ. 1999"
			, "12DEZ.19"
			, "2045-04-06"
			, "04/06/2025"
			, "2045.04.06"
			, "20450406"
		));
		tls = new ArrayList<>( List.of(
			DF_dd_MM_yyyy
			, DF_dd_MM_yyyy_hh_mm_ss
			, DF_yyyy_MM_dd_HH_mm_ss_SSS
			, DF_yyyy_MM_dd_hh_mm_ss_SSS
			, DF_dd_MMM_yyyy_US
			, DF_dd_MMM_yy_US
			, DF_dd_MMM_yyyy_DE
			, DF_dd_MMM_yy_DE
			, DF_yyyy_MM_dd
			, DF_MM_dd_yyyy_slashes
			, DF_yyyy_MM_dd_Dots
			, DF_yyyyMMdd
		));
	}
	@Setup(Level.Iteration)
	public void setupIteration() {
		Collections.shuffle( dates);
		Collections.shuffle( tls);
		tlBundle = ThreadLocal.withInitial( () -> getDateFormats());
	}

	private Date parseDate( String string, DateFormat[] dateFormats, OpCounters opc) throws ParseException {
		ParseException lastPE = null;
		for ( DateFormat df : dateFormats) {
			opc.attempts++;
			df.setLenient( false);
			try {
				Date d = df.parse( string);
				opc.successes++;
				return d;
			} catch ( ParseException pe) {
				opc.exceptions++;
				lastPE = pe;
			}
		}
		if ( lastPE != null) {
			throw lastPE;
		}
		return null;
	}

	private Date parseDateByLength(String string, DateFormat[] dateFormats, OpCounters opc) throws ParseException {
		ParseException lastPE = null;
		for ( DateFormat df : dateFormats) {
			opc.attempts++;
			df.setLenient( false);
			try {
				// für SimpleDateFormat: parse nur, wenn Länge stimmt, aber die bekommen wir nur bei SDF
				if ( ! ( df instanceof SimpleDateFormat)) {
					Date d = df.parse( string);
					opc.successes++;
					return d;
				}
				SimpleDateFormat sdf = ( SimpleDateFormat) df;
				String pattern = sdf.toPattern();
				if ( pattern.length() == string.length()) {
					Date d = df.parse( string);
					opc.successes++;
					return d;
				} else // jetzt gehts ab. Teste, ob wir MMM mit deutscher Locale haben, denn da hat der Monat vier Zeichen, also eins mehr als das Muster
					if ( pattern.contains( "MMM") && ! pattern.contains( "MMMM")) {
						DateFormatSymbols dfs = sdf.getDateFormatSymbols();
						if ( dfs.getShortMonths()[ 0].equals( "Jan.")) {
							if ( pattern.length() +1 == string.length()) {
								Date d = df.parse(string);
								opc.successes++;
								return d;
							}
						}
					}
			} catch ( ParseException pe) {
				opc.exceptions++;
				lastPE = pe;
			}
		}
		if ( lastPE != null) {
			throw lastPE;
		}
		return null;
	}

	private Date parseDateByLengthTL(String string, List<ThreadLocal<DateFormat>> tls, OpCounters opc) throws ParseException {
		ParseException lastPE = null;
		for ( ThreadLocal tl: tls) {
			DateFormat df = (DateFormat) tl.get();
			opc.attempts++;
			df.setLenient( false);
			try {
				// für SimpleDateFormat: parse nur, wenn Länge stimmt, aber die bekommen wir nur bei SDF
				if ( ! ( df instanceof SimpleDateFormat)) {
					Date d = df.parse( string);
					opc.successes++;
					return d;
				}
				SimpleDateFormat sdf = ( SimpleDateFormat) df;
				String pattern = sdf.toPattern();
				if ( pattern.length() == string.length()) {
					Date d = df.parse( string);
					opc.successes++;
					return d;
				} else // jetzt gehts ab. Teste, ob wir MMM mit deutscher Locale haben, denn da hat der Monat vier Zeichen, also eins mehr als das Muster
					if ( pattern.contains( "MMM") && ! pattern.contains( "MMMM")) {
						DateFormatSymbols dfs = sdf.getDateFormatSymbols();
						if ( dfs.getShortMonths()[ 0].equals( "Jan.")) {
							if ( pattern.length() +1 == string.length()) {
								Date d = df.parse(string);
								opc.successes++;
								return d;
							}
						}
					}
			} catch ( ParseException pe) {
				opc.exceptions++;
				lastPE = pe;
			}
		}
		if ( lastPE != null) {
			throw lastPE;
		}
		return null;
	}

	String getDate() {
		int index = dateIndex.incrementAndGet();
		while ( index >= dates.size()) {
			dateIndex.compareAndSet( index, 0);
			index = dateIndex.get();
		}
		return dates.get( index);
	}

	DateFormat[] getDateFormats() {
		DateFormat dfA[] = new DateFormat[ tls.size()];
		for ( int i = 0;  i < dfA.length;  i++) {
			dfA[ i] = tls.get( i).get();
		}
		return dfA;
	}

	DateFormat[] getDateFormatsBundle() {
		return tlBundle.get();
	}

	@Threads( 4)
	@Benchmark
	public Date parseDate( OpCounters opc) throws ParseException {
		String dateString = getDate();
		DateFormat[] dateFormats = getDateFormats();
		return parseDate( dateString, dateFormats, opc);
	}

	@Threads( 4)
	@Benchmark
	public Date parseDateByLengthBundle( OpCounters opc) throws ParseException {
		String dateString = getDate();
		DateFormat[] dateFormats = getDateFormatsBundle();
		return parseDateByLength( dateString, dateFormats, opc);
	}

	@Threads( 4)
	@Benchmark
	public Date parseDateByLength( OpCounters opc) throws ParseException {
		String dateString = getDate();
		DateFormat[] dateFormats = getDateFormats();
		return parseDateByLength( dateString, dateFormats, opc);
	}

	@Threads( 4)
	@Benchmark
	public Date parseDateByLengthTL( OpCounters opc) throws ParseException {
		String dateString = getDate();
		return parseDateByLengthTL( dateString, tls, opc);
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( DateParseBench.class.getSimpleName())
		        .warmupIterations(5)
	            .warmupTime(TimeValue.seconds(1))
		        .measurementIterations(40)
				.measurementTime(TimeValue.seconds(1))
				.forks(1)
                .build();
        new Runner(opt).run();
    }
}
