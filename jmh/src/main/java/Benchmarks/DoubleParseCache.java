package Benchmarks;

import org.apache.commons.collections4.map.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class DoubleParseCache {

	@Param( { "100", "1000", "10000", "100000", "1000000"}) // "
	int size;

	final int ArraySizeM = 1;
	final int ArraySize = ArraySizeM * 1000 * 100;
	final NumberFormat nf = DecimalFormat.getNumberInstance( Locale.US);

	ThreadLocal<char[][]> tlC;
	ThreadLocal<String[]> tlS;

	double  dummyD;
	private long hits;
	private long misses;
	SortedMap<Integer, Integer> lengths = new TreeMap<>();

	@Setup(Level.Trial)
	public void setup() {
		tlC = new ThreadLocal<char[][]>() {
			@Override
			protected char[][] initialValue() {
				nf.setGroupingUsed( false);
				nf.setMaximumFractionDigits( 2);
				final char[][] arr = new char[ArraySize][];
				Random  rnd = new Random();
				Set<String> distinct = new HashSet<>();
				for ( int i = arr.length - 1; i >= 0; -- i) {
					double  d = rnd.nextDouble();
					int magnitude = rnd.nextInt( 4);
					for ( int m = 0;  m < magnitude;  m++) {
						d *= 10;
					}
					String  s = nf.format( d);
					arr[ i] = s.toCharArray();
					int l = s.length();
					int num = 0;
					if ( lengths.containsKey( l)) {
						num = lengths.get( l);
					}
					lengths.put( l, num + 1);
					distinct.add( s);
				}
				System.out.println( "\nLengths: " + lengths + ", " + distinct.size() + " distinct values");
				return arr;
			}
		};
		char[][] arr = tlC.get();
		tlS = new ThreadLocal<String[]>() {
			@Override
			protected String[] initialValue() {
				final String[] arrS = new String[ArraySize];
				for ( int i = arrS.length - 1; i >= 0; -- i) {
					arrS[ i] = new String( arr[ i]);
				}
				return arrS;
			}
		};
	}

	@Setup(Level.Iteration)
	public void get() {
		tlC.get();
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void scan() {
		char[][] values = tlC.get();
		for ( int i = values.length - 1; i >= 0; -- i) {
			char[] arr = values[ i];
			String  s = new String( arr);
			dummyD = Double.valueOf( s);
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void scanS() {
		String[] values = tlS.get();
		for ( int i = values.length - 1; i >= 0; -- i) {
			String  s = values[ i];
			dummyD = Double.valueOf( s);
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
		if ( hits == 0) {
			return;
		}
		double hitsPerc = 100.0 * hits / ( hits + misses);
		nf.setGroupingUsed( true);
		System.out.println( "Hits: " + nf.format( hits) + ", Misses: " + nf.format( misses) + " " + nf.format( hitsPerc) + "% hits");
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void scanCachedP() {
		final Map<String, Double> scanCache = new LRUMap<String, Double>(size);
		scanCached( scanCache);
	}

	@Fork( jvmArgsPrepend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI", "-XX:+UseJVMCICompiler"})
	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void scanCachedPGraal() {
		final Map<String, Double> scanCache = new LRUMap<String, Double>(size);
		scanCached( scanCache);
	}

	private void scanCached(final Map<String, Double> scanCache) {
		char[][] values = tlC.get();
		for ( int i = values.length - 1; i >= 0; -- i) {
			char[] arr = values[ i];
			String  s = new String( arr);
			Double dD = scanCache.get( s);
			if  ( dD != null) {
				dummyD = dD.doubleValue();
				hits++;
			} else {
				dummyD = Double.valueOf(s);
				scanCache.put( s, dummyD);
				misses++;
			}
		}
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void scanCachedSP() {
		final Map<String, Double> scanCache = new LRUMap<String, Double>(size);
		scanCachedS( scanCache);
	}

	@Fork( jvmArgsPrepend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI", "-XX:+UseJVMCICompiler"})
	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void scanCachedSPGraal() {
		final Map<String, Double> scanCache = new LRUMap<String, Double>(size);
		scanCachedS( scanCache);
	}

	private void scanCachedS(final Map<String, Double> scanCache) {
		String[] values = tlS.get();
		for ( int i = values.length - 1; i >= 0; -- i) {
			String  s = values[ i];
			Double dD = scanCache.get( s);
			if  ( dD != null) {
				dummyD = dD.doubleValue();
				hits++;
			} else {
				dummyD = Double.valueOf(s);
				scanCache.put( s, dummyD);
				misses++;
			}
		}
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( DoubleParseCache.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .timeUnit(TimeUnit.NANOSECONDS)
		        .warmupIterations(2)
		        .measurementIterations(3)
				.measurementTime( TimeValue.seconds( 5))
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
