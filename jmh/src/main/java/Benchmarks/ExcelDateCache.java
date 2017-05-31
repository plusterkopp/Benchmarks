package Benchmarks;

import de.icubic.mm.server.utils.MMKF4JavaStub;
import gnu.trove.map.hash.TLongDoubleHashMap;
import org.apache.commons.collections4.map.LRUMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.text.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class ExcelDateCache {

	final int ArraySizeM = 1;
	final int ArraySize = ArraySizeM * 1000 * 100;
	final NumberFormat nf = DecimalFormat.getNumberInstance( Locale.US);

	ThreadLocal<Date[]> tlD;

	private long hits;
	private long misses;
	private double dummyD;

	@Setup(Level.Trial)
	public void setup() {
		long millisPerDay = 1000 * 60 * 60 * 24;
		Instant iMin = Instant.parse( "2015-01-01T00:00:00.000Z");
		Instant iMax = Instant.parse( "2045-01-01T00:00:00.000Z");
		long millisDist = iMax.toEpochMilli() - iMin.toEpochMilli();
		tlD = new ThreadLocal<Date[]>() {
			@Override
			protected Date[] initialValue() {
				Date[]  arr = new Date[ ArraySize];
				Random  rnd = new Random();
				Set<Date> distinct = new HashSet<>();
				for ( int i = arr.length - 1; i >= 0; -- i) {
					long dist = (long) (rnd.nextDouble() * millisDist);
					Instant instant = Instant.ofEpochMilli( iMin.toEpochMilli() + dist);
					instant = instant.truncatedTo(ChronoUnit.DAYS);
					arr[ i] = Date.from( instant);
					distinct.add( arr[ i]);
				}
				System.out.println( "" + distinct.size() + " distinct values");
				return arr;
			}
		};
	}

	@Setup(Level.Iteration)
	public void get() {
		tlD.get();
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void convertNoCache() {
		Date[] dateValues = tlD.get();
		for ( int i = dateValues.length - 1; i >= 0; -- i) {
			Date date = dateValues[ i];
			dummyD = MMKF4JavaStub.toAccurateExcelDate( date);
		}
	}

	private void convertCachedT( TLongDoubleHashMap cache) {
		Date[] dateValues = tlD.get();
		final double notFound = cache.getNoEntryValue();
		final boolean notFoundIsNaN = Double.isNaN(notFound);
		for ( int i = dateValues.length - 1; i >= 0; -- i) {
			Date date = dateValues[ i];
			dummyD = cache.get( date.getTime());
			if ( dummyD == notFound ||
					( Double.isNaN( dummyD) && notFoundIsNaN)) {
				dummyD = MMKF4JavaStub.toAccurateExcelDate( date);
				cache.put( date.getTime(), dummyD);
				misses++;
			} else {
				hits++;
			}
		}
	}

	private void convertCached( Map<Long, Double> cache) {
		Date[] dateValues = tlD.get();
		for ( int i = dateValues.length - 1; i >= 0; -- i) {
			Date date = dateValues[ i];
			Double dD = cache.get( date.getTime());
			if ( dD == null) {
				dD = MMKF4JavaStub.toAccurateExcelDate( date);
				cache.put( date.getTime(), dD);
				misses++;
			} else {
				hits++;
			}
			dummyD = dD;
		}
	}

	@Setup(Level.Iteration)
	public void clearCache() {
		hits = 0;
		misses = 0;
		System.gc();
	}

	@TearDown( Level.Trial)
	public void printHitsMisses() {
		if ( ( hits + misses) == 0) {
			return;
		}
		double hitsPerc = 100.0 * hits / ( hits + misses);
		nf.setGroupingUsed( true);
		System.out.println( "\nHits: " + nf.format( hits) + ", Misses: " + nf.format( misses) + " " + nf.format( hitsPerc) + "% hits");
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void convertCached1MT() {
		TLongDoubleHashMap cache = new TLongDoubleHashMap(10000, 0.8f, Long.MIN_VALUE, Double.NaN);
		convertCachedT( cache);
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void convertCached1ML() {
		final Map<Long, Double> scanCache = new LRUMap<>(1_000_000);
		convertCached( scanCache);
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void convertCached1KL() {
		final Map<Long, Double> scanCache = new LRUMap<>(1_000);
		convertCached( scanCache);
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( ExcelDateCache.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .measurementTime( TimeValue.seconds( 10))
		        .timeUnit(TimeUnit.NANOSECONDS)
		        .warmupIterations(5)
		        .measurementIterations(3)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
