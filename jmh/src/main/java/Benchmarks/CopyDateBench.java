package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.*;
import java.util.concurrent.*;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)

public class CopyDateBench {

	private Date time;
	private Date date;

	private static class GenericDate {

		private static Date TODAY = new Date();

		public static final ThreadLocal<Calendar> scratchCalendar;

		static {
			scratchCalendar = ThreadLocal.withInitial( () -> Calendar.getInstance());
			final Calendar c = getScratchCalendar();
			c.set(Calendar.HOUR_OF_DAY, 8);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			TODAY = c.getTime();
		}

		public static final Calendar getScratchCalendar() {
			return scratchCalendar.get();
		}

		private static Date changeTimeOfDate(Date date, int hour) {
			final Calendar c = getScratchCalendar();
			c.setTime(date);
			c.set(Calendar.HOUR_OF_DAY, hour);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			return c.getTime();
		}

		public static Date get8oClockDate(Date date) {
			return changeTimeOfDate( date, 8);
		}

		public static Date copyTimeToDate( Date time, Date date) {
			long millis = GenericDate.get8oClockDate( date).getTime()
					+ time.getTime()
					- GenericDate.get8oClockDate( time).getTime();
			return new Date( millis);
		}

		public static Date copyTimeToDateCalendar( Date time, Date date){
			final Calendar calendar = getScratchCalendar();

			calendar.setTime( time);
			int dd = calendar.get( Calendar.HOUR_OF_DAY);
			int mm = calendar.get( Calendar.MINUTE);
			int ss = calendar.get( Calendar.SECOND );
			int ms = calendar.get( Calendar.MILLISECOND );

			calendar.setTime( date);
			calendar.set( Calendar.HOUR_OF_DAY, dd);
			calendar.set( Calendar.MINUTE, mm);
			calendar.set( Calendar.SECOND, ss);
			calendar.set( Calendar.MILLISECOND, ms);

			return calendar.getTime();
		}

		public final static long millisPerSecond = 1000L;
		public final static long millisPerMinute = millisPerSecond * 60;
		public final static long millisPerHour = 60 * millisPerMinute;
		public final static long millisPerDay = 24 * millisPerHour;

		/**
		 * Rechnet die angegebene Uhrzeit in Millisekunden um. Gezählt wird ab 8 Uhr. Wir nutzen die
		 * Zeiten von 0:00:00 bis 23:59:59.<br>
		 * Zulässige Formate:<br>
		 * HH:MM:SS<br>
		 * H:MM:SS<br>
		 * HH:MM<br>
		 * H:MM<br>
		 * 0 <= HH < 24; 0 <= (MM, SS) < 60
		 *
		 * @param time
		 * @return Millisekunden zwischen 08.00 und der korrekt erkannten Zeit, sonst -1
		 */
		public static long stringToMilliesSince0800(String time) {
			// Wir liefern bei bei fehlerhafter Übergabe -1 zurück.
			long errorReturn = -1L;

			try {
				if (time == null || time.isEmpty())
					return errorReturn;

				String timeArr[] = time.split(":");
				int timeArrLength = timeArr.length;	// wir wollen hier genau 3

				if (timeArrLength > 3 || timeArrLength < 2) {
					return errorReturn;
				}

				long timeInMillies = -1L * 8 * millisPerHour;
				long value;

				for ( int i = timeArr.length - 1; i >= 0; i--) {
					String digits = timeArr[ i].trim();
					value = Long.parseLong( digits);
					switch ( i) {
						case 0:
							if ( value < 0 || value > 23) {
								return errorReturn;
							}
							timeInMillies += value * millisPerHour;
							break;
						case 1:
							if ( value < 0 || value > 59) {
								return errorReturn;
							}
							timeInMillies += value * millisPerMinute;
							break;
						case 2:
							if ( value < 0 || value > 59) {
								return errorReturn;
							}
							timeInMillies += value * millisPerSecond;
							break;
					}
				}
				return timeInMillies;
			} catch ( NumberFormatException nfe) {
				return errorReturn;
			}
		}

		public static Date addMillisToDate(Date date, long millies) {
			long millis = date.getTime() + millies;
			return new Date(millis);
		}

		public static Date todayWithTimeofDayFrom( Date timeDate) {
			String	timePart = timeDate.toString();
			timePart = timePart.substring( 11, 19);
			long	millies = stringToMilliesSince0800( timePart);
			Date	today = TODAY;
			today = addMillisToDate( today, millies);
			return today;
		}

	}

	@Setup(Level.Trial)
	public void setup() {
		time = new Date();
		date = new Date( time.getTime() + 1000);
	}

	@Benchmark
	@Fork(jvmArgsPrepend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI", "-XX:+UseJVMCICompiler"})
	public Date copyTimeToDateGraal() {
		return GenericDate.copyTimeToDate( time, date);
	}

	@Benchmark
	@Fork(jvmArgsPrepend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI", "-XX:+UseJVMCICompiler"})
	public Date copyTimeToDateCalGraal() {
		return GenericDate.copyTimeToDateCalendar( time, date);
	}

	@Benchmark
	@Fork(jvmArgsPrepend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI", "-XX:+UseJVMCICompiler"})
	public Date todayWithTimeGraal() {
		return GenericDate.todayWithTimeofDayFrom( time);
	}

	@Benchmark
	public Date copyTimeToDate() {
		return GenericDate.copyTimeToDate( time, date);
	}

	@Benchmark
	public Date copyTimeToDateCal() {
		return GenericDate.copyTimeToDateCalendar( time, date);
	}

	@Benchmark
	public Date todayWithTime() {
		return GenericDate.todayWithTimeofDayFrom( time);
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( CopyDateBench.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
