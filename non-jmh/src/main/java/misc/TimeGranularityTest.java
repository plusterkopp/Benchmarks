package misc;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.HdrHistogram.Histogram;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

public class TimeGranularityTest {

	static ThreadLocal<NumberFormat> nfITL = ThreadLocal.withInitial( () -> {
		NumberFormat nf = NumberFormat.getIntegerInstance();
		nf.setGroupingUsed( true);
		return nf;
	});
	static ThreadLocal<NumberFormat> nfDTL = ThreadLocal.withInitial( () -> {
		NumberFormat nf = NumberFormat.getNumberInstance();
		nf.setGroupingUsed( true);
		nf.setMaximumFractionDigits( 6);
		return nf;
	});

	// default: run for some seconds
	static double seconds = 10;
	static long maxRecord = 1_000_000_000;
	static LongAdder globalMeasurementCounter = new LongAdder();
	static final List<Long> threadNSList = new ArrayList<>();

	static Map<ExecutorService, AtomicBoolean> poolToTimeoutFlag = new HashMap<>();

	public static void main(String[] args) {
		parseArgs( args);
		System.out.println( "currentTimeMillis");
		run( System::currentTimeMillis);
		System.out.println( "\n" + "nanoTime");
		run( System::nanoTime);
		System.out.println( "\n" + "Instant.now");
		Instant start = Instant.now();
		long startS = start.getEpochSecond();
		long startNS = start.getNano();
		run( () -> {
			Instant now = Instant.now();
			long nowS = now.getEpochSecond();
			long nowNS = now.getNano();
			return 1_000_000 * ( nowS - startS) + ( nowNS - startNS);
		});
	}

	private static void run( LongSupplier timeSupplier) {

		int threadCount = Runtime.getRuntime().availableProcessors();
		ExecutorService pool = Executors.newFixedThreadPool( threadCount);
		// reset stop conditions
		globalMeasurementCounter.reset();
		poolToTimeoutFlag.put( pool, new AtomicBoolean( false));

		List<Future<long[]>> resultFutures = submitFutures(pool, threadCount, timeSupplier);

		long[] values;
//		values = collectValues( resultFutures, true);
//		values = null;
//		System.gc();
//		values = collectValues( resultFutures, false);
//		values = null;
		System.gc();
		values = collectValues( pool, resultFutures, false);

		terminatePool( pool, "measument");

		logHistogram( values, true);
		logHistogram( values, false);
	}

	private static void logHistogram(long[] values, boolean recordSame) {
		long startNS = System.nanoTime();
		Histogram histogram = new Histogram( 5);
		long last = values[ 0];
		for ( int i = 1;  i < values.length;  i++) {
			long current = values[ i];
			long diff = current - last;
			if ( recordSame || diff > 0) {
				histogram.recordValue(diff);
			}
			last = current;
		}
		NumberFormat nfI = nfITL.get();
		NumberFormat nfD = nfDTL.get();
		System.out.println( "histogram "
				+ ( recordSame ? "including" : "ignoring")
				+ " same value filled " + nfI.format( histogram.getTotalCount())
				+ " values in "
				+ nfI.format( System.nanoTime() - startNS) + " ns"
		);
		System.out.println( "mean: " + nfD.format( histogram.getMean()));

		startNS = System.nanoTime();
//		AllValuesIterator it = new AllValuesIterator( histogram);
//		while ( it.hasNext()) {
//			HistogramIterationValue itValue = it.next();
//			if ( itValue.getCountAtValueIteratedTo() > 0) {
//				System.out.println(itValue);
//			}
//		}
		SortedMap<Double, Long> percentilesToValues = new TreeMap<>();
		// collect some interesting percentiles by percentile-level
		double[] percentiles = { 100, 99.9999, 99.999, 99.99, 99.9, 99, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0};
		long lastValue = -1;
		for ( double p: percentiles) {
			long value = histogram.getValueAtPercentile( p);
			if ( value != lastValue) {
//				System.out.print( nfD.format( p) + ": " + nfI.format( value) + "    ");
				double pp = histogram.getPercentileAtOrBelowValue( value);
//				percentilesToValues.put( p, value);
				percentilesToValues.put( pp, value);
				lastValue = value;
			}
		}
		// add some percentiles by value
		long[] valuesForPercentiles = { 1, 0};
		for ( long value: valuesForPercentiles) {
			double perc = histogram.getPercentileAtOrBelowValue( value);
			percentilesToValues.put( perc, value);
		}
		// print values
		percentilesToValues.forEach( ( p, v) -> {
			String percPart;
			if ( p < 99 || p == 100) {
				percPart = nfD.format( p);
			} else {
				percPart = "100-" + nfD.format( 100-p);
			}
			System.out.print( percPart + ": " + nfI.format( v) + "    ");
		});
		// lasse zu jedem value nur das größte Perzentil drin
		System.out.println();

		System.out.println( "histogram logged in "
				+ nfI.format( System.nanoTime() - startNS) + " ns"
		);
	}

	private static void terminatePool(ExecutorService pool, String name) {
		if ( pool == null) {
			return;
		}
		long startNS = System.nanoTime();
		boolean poolTerminated = false;
		try {
			pool.shutdown();
			poolTerminated = pool.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
//		System.out.println( "pool " + name
//				+ ( poolTerminated ? " " : " not ") + "terminated after "
//				+ nfITL.get().format( System.nanoTime() - startNS) + " ns"
//		);
	}

	private static long[] collectValues(
			ExecutorService pool, List<Future<long[]>> resultFutures, boolean useFastSort)
	{
		boolean needsTimeout = resultFutures.stream()
				.map(f -> ! f.isDone())
				.reduce(false, (a, b) -> a || b);
		long startNS = System.nanoTime();
		ScheduledExecutorService timeoutService = Executors.newSingleThreadScheduledExecutor();
		long nanos = (long) (1e9 * seconds);
		timeoutService.schedule(() -> {
			if ( ! pool.isTerminated()) {
				AtomicBoolean timeoutReached = poolToTimeoutFlag.get(pool);
				timeoutReached.set(true);
				NumberFormat nfI = nfITL.get();
				System.out.println("timeout for " + resultFutures.size()
						+ " jobs signalled in "
						+ nfI.format(System.nanoTime() - startNS) + " ns"
				);
			}
		}, nanos, TimeUnit.NANOSECONDS);

//		System.out.println( "timeout scheduled in "
//				+ nfI.format( System.nanoTime() - startNS) + " ns"
//		);

		NumberFormat nfI = nfITL.get();
		long startSortNS;
		startSortNS = System.nanoTime();
		long[] sortedValues;

		if ( useFastSort) {
			sortedValues = collectSortedResultsMerge( resultFutures);
		} else {
			sortedValues = collectSortedResultsJDK( resultFutures);
		}
		System.out.println( "values " + nfI.format( sortedValues.length)
				+ " sorted "
				+ ( useFastSort ? "fast" : "jdk")
				+ " in " + nfI.format( System.nanoTime() - startSortNS) + " ns"
		);

//		startSortNS = System.nanoTime();
//		if ( ! Arrays.equals( valuesJDK, fastValues)) {
//			System.out.println( "fast fail");
//		}
//		System.out.println( "sort verified in "
//				+ nfI.format( System.nanoTime() - startSortNS) + " ns"
//		);

		terminatePool( timeoutService, "timeout");
		return sortedValues;
	}

	private static long[] collectSortedResultsJDK(List<Future<long[]>> resultFutures) {
		NumberFormat nfI = nfITL.get();
		NumberFormat nfD = nfDTL.get();
		long startNS = System.nanoTime();
		int[] sizeA = { 0};
		resultFutures.forEach( f -> {
			long[] result = new long[0];
			try {
				result = f.get();
				sizeA[ 0] += result.length;
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		});
		System.out.println( "getting " + nfI.format( sizeA[ 0]) + " values after "
				+ nfI.format( System.nanoTime() - startNS) + " ns"
		);
		long totalThreadNS = threadNSList.stream().reduce( 0L, ( a, b) -> a+b);
		System.out.println( resultFutures.size() + " threads, " +
				"avg " +  nfD.format( 1.0 * totalThreadNS / sizeA[ 0]) + " ns/measurement " +
				"in " + nfD.format( 1e-9 * totalThreadNS) + " s total runtime");
		System.out.println( "allocating " + nfI.format( Long.BYTES * sizeA[ 0]) + " bytes");
		startNS = System.nanoTime();
		long[]  resultFull = new long[ sizeA[ 0]];
		int index = 0;
		for ( Future<long[]> f: resultFutures) {
			long[] result = new long[0];
			try {
				result = f.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
			System.arraycopy(result, 0, resultFull, index, result.length);
			index += result.length;
 		}
		System.out.println( "values fetched in "
				+ nfI.format( System.nanoTime() - startNS) + " ns"
		);
		resultFutures.clear();  // for GC

		long startSortNS = System.nanoTime();
		Arrays.sort( resultFull);
		System.out.println( "values sorted in "
				+ nfI.format( System.nanoTime() - startSortNS) + " ns"
		);
		return resultFull;
	}

	private static long[] collectSortedResultsMerge(List<Future<long[]>> resultFutures) {
		List<long[]> longAList = new ArrayList<>();
		resultFutures.forEach( f -> {
			long[] result = new long[0];
			try {
				result = f.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
			longAList.add( result);
		});
		int totalSize = longAList.stream().mapToInt( longA -> longA.length).sum();
		// merge sort
		long[] resultList = new long[ totalSize];
		int index = 0;
		int[] indices = new int[ longAList.size()];
		while ( index < totalSize) {
			// finde den kleinsten
			int minIndex = -1;
			long minValue = 0;
			for ( int i = 0;  i < longAList.size();  i++) {
				long[] longs = longAList.get( i);
				int indexInLongs = indices[ i];
				if ( indexInLongs < longs.length) {
					long value = longs[indexInLongs];
					if ( minIndex == -1 || value < minValue) {
						minIndex = i;
						minValue = value;
					}
				}
			}
			resultList[ index] = minValue;
			// erhöhe Index der Liste, aus der wir genommen haben
			indices[ minIndex]++;
			// erhöhe Index in Result Liste
			index++;
		}
		return resultList;
	}

	private static List<Future<long[]>> submitFutures(
			ExecutorService pool, int threadCount, LongSupplier timeSupplier)
	{
		long startNS = System.nanoTime();

		AtomicBoolean timeoutReached = poolToTimeoutFlag.get(pool);
		int maxPerThreadCount = (int) (maxRecord / threadCount);
		List<Future<long[]>> futures = new ArrayList<>();
		threadNSList.clear();
		Callable<long[]> job = () -> {
			TLongList values = new TLongArrayList( maxPerThreadCount);
			while ( ( ! timeoutReached.get())
					&& ( continueMeasurements( values.size(), maxPerThreadCount, globalMeasurementCounter)))
			{
				long time = timeSupplier.getAsLong();
				globalMeasurementCounter.increment();
				values.add( time);
			}
			NumberFormat nfI = nfITL.get();
			long threadNS = System.nanoTime() - startNS;
			System.out.println( Thread.currentThread().getName() + " "
					+ nfI.format( values.size())
					+ "/" + nfI.format( globalMeasurementCounter.longValue())
					+ " measurements in "
					+ nfI.format(threadNS) + " ns"
			);
			threadNSList.add( threadNS);
			return values.toArray( ); // new long[ values.size()]
		};
		for ( int i = 0;  i < threadCount;  i++) {
			Future<long[]> f = pool.submit(job);
			futures.add( f);
		}
		pool.shutdown();
//		System.out.println( "pool shutdown after "
//				+ nfITL.get().format( System.nanoTime() - startNS) + " ns"
//		);
		return futures;
	}

	private static boolean continueMeasurements(
			long measureCountByCaller, long maxPerThreadCount, LongAdder globalMeasurementCounter)
	{
		if ( measureCountByCaller < maxPerThreadCount) {
			return true;
		}
		return false;
//		return globalMeasurementCounter.longValue() < maxRecord;
	}

	private static void parseArgs(String[] args) {
		OptionParser optionParser = new OptionParser();
		OptionSpec<Integer> secondsSpec = optionParser
				.accepts( "seconds")
				.withRequiredArg()
				.ofType( Integer.class);

		OptionSet options = optionParser.parse(args);

		if ( options.hasArgument( "seconds")) {
			Integer secondsI = options.valueOf(secondsSpec);
			if (secondsI != null) {
				seconds = secondsI;
			}
		}
		// determine maxRecord to fit in available heap
		long heapSpace = (long) (Runtime.getRuntime().maxMemory() * 0.9);
		maxRecord = heapSpace / ( 8*2);
		NumberFormat nfI = nfITL.get();
		System.out.println( "using " + nfI.format( heapSpace)
				+ " bytes for " + nfI.format( maxRecord) + " measurements");
	}
}
