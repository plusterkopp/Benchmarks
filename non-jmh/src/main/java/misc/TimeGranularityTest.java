package misc;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.HdrHistogram.Histogram;

import java.text.NumberFormat;
import java.time.Duration;
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

	static private final List<long[]>	lastResultList = new ArrayList<>();
	static private long[] sortedResultJDK;

	static private double[] percentiles = { 100, 99.99999, 99.9999, 99.999, 99.99, 99.9, 99, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0};

	public static void main(String[] args) {
		buildPercentiles();
		parseArgs( args);

		Instant startInst = Instant.now();

		System.out.println( "currentTimeMillis");
		run( System::currentTimeMillis, false);

		System.out.println( "\n" + "nanoTime");
		run( System::nanoTime, false);

		System.out.println( "\n" + "Instant.now");
		Instant start = Instant.now();
		run( () -> {
			Instant now = Instant.now();
			return Duration.between( start, now).toNanos();
		}, false);

		compareSortAlgos();

		Duration dur = Duration.between( startInst, Instant.now());
		System.out.println( "finished in " + dur);
	}

	private static void buildPercentiles() {
		List<Double> pList = new ArrayList<>( 1000);
		double v = 1;
		for ( int pow = 0;  pow < 6;  pow++) {
			pList.add( 100 - v);
			v *= 0.1;
		}
		for ( int pct = 0;  pct <= 100;  pct++) {
			pList.add( Double.valueOf(pct));
		}
		pList.sort( Double::compare);
		// reverse des Kleinen Mannes
		int lastIndex = pList.size() - 1;
		percentiles = new double[ pList.size()];
		for ( int i = 0;  i < pList.size();  i++) {
			percentiles[ i] = pList.get( lastIndex - i);
		}
	}

	private static void compareSortAlgos() {
		long[] resultsSol = sortResultsMergeSol();
		compareResults( sortedResultJDK, "JDK", resultsSol, "Sol");

		resultsSol = null;	// GC
		long[] resultsLaguna = sortResultsMergeLaguna();
		compareResults( sortedResultJDK, "JDK", resultsLaguna, "laguna");
	}

	private static void compareResults(long[] resultsA, String nameA, long[] resultsB, String nameB) {
		if ( resultsA.length != resultsB.length) {
			System.err.println( "result lengths differ " + nameA + ": " + resultsA.length + " != " + resultsB.length + " " + nameB);
			return;
		}
		for  ( int i = 0; i < resultsA.length; i++ ) {
			long valueA = resultsA[ i];
			long valueB = resultsB[ i];
			if ( valueA != valueB ) {
				System.err.println( "mismatch at " + i + ": " + valueA + " != " + valueB + " " + nameA + " - " + nameB + " = " + ( valueA - valueB));
				return;
			}
		}
	}

	private static void run( LongSupplier timeSupplier, boolean useMergeSort) {

		int threadCount = Runtime.getRuntime().availableProcessors();
		ExecutorService pool = Executors.newFixedThreadPool( threadCount);
		// reset stop conditions
		globalMeasurementCounter.reset();
		poolToTimeoutFlag.put( pool, new AtomicBoolean( false));

		CountDownLatch startLatch = new CountDownLatch( threadCount);
		List<Future<long[]>> resultFutures = submitFutures(pool, threadCount, timeSupplier, startLatch);

		long[] values;
		System.gc();
		values = collectValues( pool, resultFutures, useMergeSort);

		terminatePool( pool, "measument");

		logHistogram( values, true);
		logHistogram( values, false);
	}

	private static void logHistogram(long[] values, boolean recordSame) {
		long startNS = System.nanoTime();
		Histogram histogram = new Histogram( 5);
//		fillHistogramSimpleDiff( values, histogram, recordSame);
		fillHistogramSteps( values, histogram, recordSame);
		NumberFormat nfI = nfITL.get();
		NumberFormat nfD = nfDTL.get();
		System.out.println( "histogram "
				+ ( recordSame ? "including" : "ignoring")
				+ " same value filled " + nfI.format( histogram.getTotalCount())
				+ " values in "
				+ nfI.format( System.nanoTime() - startNS) + " ns"
		);
		System.out.println(
			"mean: " + nfD.format( histogram.getMean())
			+ " median: " + nfD.format( histogram.getValueAtPercentile( 50))
			+ " min nonzero: " + nfD.format( histogram.getMinNonZeroValue())
			+ " max: " + nfD.format( histogram.getMaxValue())
		);

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
		long lastValue = -1;
		for ( double p: percentiles) {
			long value = histogram.getValueAtPercentile( p);
			if ( value != lastValue) {
				double pp = histogram.getPercentileAtOrBelowValue( value);
//				System.out.print( nfD.format( p) + ": " + nfI.format( value)
//					+ " (<" + nfD.format( pp)+ ")"
//					+ "    ");
//				percentilesToValues.put( p, value);
				percentilesToValues.put( pp, value);
				lastValue = value;
			}
		}
//		System.out.println();
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

	private static void fillHistogramSimpleDiff(long[] values, Histogram histogram, boolean recordSame) {
		long last = values[ 0];
		for ( int i = 1;  i < values.length;  i++) {
			long current = values[ i];
			long diff = current - last;
			if ( recordSame || diff > 0) {
				histogram.recordValue(diff);
			}
			last = current;
		}
	}

	private static void fillHistogramSteps(long[] values, Histogram histogram, boolean recordSame) {
		// angenommen, wir haben die Werte 1 1 1 1 4 4 6 6 6 6 6
		// dann haben wirfür recordSame  3 + 1 + 4 mal 0 aufzuzeichnen
		// dazu 2 × (4-1), 5 × (6-4)
		long last = values[ 0];
		long currentStep = 0;
		boolean firstStep = true;
		long lastStep;
		int runLength = 0;
		int totalRuns0 = 0;
		for ( int i = 1;  i < values.length;  i++) {
			long current = values[ i];
			if ( last == current) {
				if ( recordSame) {
					totalRuns0++;	// später recorden
				}
				runLength++;	// aktuellen Lauf inkrementieren
				continue;
			}
			// wir haben einen neuen Wert und schließen den alten Lauf ab
			// ist das unser erster Schritt? Also haben wir einen alten Lauf?
			if ( firstStep) {
				firstStep = false;	// sonst nichts tun, nichts eintragen, weiter unten Lauf initialisieren
			} else {
				// die bisherige Schrittgröße mit Anzahl der Vorkommen im Histo eintragen
				histogram.recordValueWithCount( currentStep, runLength);
			}
			runLength = 1;
			currentStep = current - last;

			// wichtig
			last = current;
		}
		// letzten Lauf eintragen
		histogram.recordValueWithCount( currentStep, runLength);
		// das zählt in unserem Fall doppelt, weil im Beispiel 3/4 1en, 1/2 4en und 4/5 6en als gleiche Werte zählen,
		// und die Schritte auf 4 und 6 nochmalals Schritte auf den Vorgängerwert gezählt wurden.
		// So gesehen ist recordSame bei dieser Zählmethode hier nicht so sinnvoll.
		if ( recordSame) {
			// 0-Schritte eintragen
			histogram.recordValueWithCount( 0, totalRuns0);
		}
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
			ExecutorService pool, List<Future<long[]>> resultFutures, boolean useMergeSort)
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

		sortedValues = collectSortedResults( resultFutures, useMergeSort);

		System.out.println( "values " + nfI.format( sortedValues.length)
				+ " sorted "
				+ ( useMergeSort ? "merge" : "jdk")
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

	private static long[] collectSortedResults(List<Future<long[]>> resultFutures, boolean useMerge) {
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
		System.out.println( "allocating " + nfI.format( Long.BYTES * ( long) sizeA[ 0]) + " bytes");

		long[] resultsFull = sortResultsJDK( resultFutures, sizeA[ 0]);
		return resultsFull;
	}

	private static long[] sortResultsJDK(List<Future<long[]>> resultFutures, int sizeFull) {
		NumberFormat nfI = nfITL.get();
		long startFetchNS = System.nanoTime();
		long[]  resultFull = new long[ sizeFull];
		lastResultList.clear();
		int index = 0;
		for ( Future<long[]> f: resultFutures) {
			long[] result = new long[0];
			try {
				result = f.get();
				lastResultList.add( result);
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
			System.arraycopy(result, 0, resultFull, index, result.length);
			index += result.length;
		}
		resultFutures.clear();  // for GC
		long fetchNS = System.nanoTime() - startFetchNS;
		System.out.println( "values fetched in " + nfI.format(fetchNS) + " ns"
		);

		long startSortNS = System.nanoTime();
//		Arrays.sort( resultFull);
		Arrays.parallelSort( resultFull);
		long sortNS = System.nanoTime() - startSortNS;
		System.out.println( "values sorted in " + nfI.format( sortNS) + " ns"
			+ " fetch+sort took " + nfI.format( fetchNS + sortNS)
		);
		sortedResultJDK = resultFull;
		return resultFull;
	}

	private static long[] sortResultsMergeSol() {
		NumberFormat nfI = nfITL.get();

		long startSortNS = System.nanoTime();

		int sourceCount = lastResultList.size();
		int sourceLength = lastResultList.get(0).length;

		// Copies references only, not the long[] contents.
		long[][] sources = lastResultList.toArray(new long[sourceCount][]);

		long[] result = new long[sourceCount * sourceLength];
		int[] positions = new int[sourceCount];
		int[] heap = new int[sourceCount];

		// Initially, every source is in the heap.
		for (int source = 0; source < sourceCount; source++) {
			heap[source] = source;
		}

		// Bottom-up heap construction.
		for (int i = (sourceCount >>> 1) - 1; i >= 0; i--) {
			siftDown(heap, sourceCount, i, sources, positions);
		}

		int heapSize = sourceCount;

		for (int out = 0; out < result.length; out++) {
			int source = heap[0];
			result[out] = sources[source][positions[source]++];

			if (positions[source] == sourceLength) {
				// Remove the exhausted source.
				heap[0] = heap[--heapSize];
			}

			if (heapSize > 0) {
				siftDown(heap, heapSize, 0, sources, positions);
			}
		}

		long sortNS = System.nanoTime() - startSortNS;
		System.out.println( "values sorted Sol in " + nfI.format( sortNS) + " ns");
		return result;
	}

	private static void siftDown( int[] heap, int size, int index, long[][] sources, int[] positions) {

		int source = heap[index];
		long value = sources[source][positions[source]];
		int half = size >>> 1;

		while (index < half) {
			int child = (index << 1) + 1;
			int right = child + 1;

			int childSource = heap[child];
			long childValue =
				sources[childSource][positions[childSource]];

			if (right < size) {
				int rightSource = heap[right];
				long rightValue =
					sources[rightSource][positions[rightSource]];

				if (rightValue < childValue) {
					child = right;
					childSource = rightSource;
					childValue = rightValue;
				}
			}

			if (value <= childValue) {
				break;
			}

			heap[index] = childSource;
			index = child;
		}

		heap[index] = source;
	}

	private static List<Future<long[]>> submitFutures(
		ExecutorService pool, int threadCount, LongSupplier timeSupplier, CountDownLatch startLatch)
	{
		AtomicBoolean timeoutReached = poolToTimeoutFlag.get(pool);
		int maxPerThreadCount = (int) (maxRecord / threadCount);
		List<Future<long[]>> futures = new ArrayList<>();
		threadNSList.clear();

		Callable<long[]> job = () -> {
			startLatch.await();
			long startNS = System.nanoTime();
			long[] values = new long[ maxPerThreadCount];
			int index = 0;
			while ( ( ! timeoutReached.get())
				&& ( continueMeasurements( index, maxPerThreadCount, globalMeasurementCounter)))
			{
				long time = timeSupplier.getAsLong();
				globalMeasurementCounter.increment();
				values[ index++] = time;
			}
			NumberFormat nfI = nfITL.get();
			long threadNS = System.nanoTime() - startNS;
//			System.out.println( Thread.currentThread().getName() + " "
//				+ nfI.format( values.size())
//				+ "/" + nfI.format( globalMeasurementCounter.longValue())
//				+ " measurements in "
//				+ nfI.format(threadNS) + " ns"
//			);
			threadNSList.add( threadNS);
			return values; // new long[ values.size()]
		};

		for ( int i = 0;  i < threadCount;  i++) {
			Future<long[]> f = pool.submit(job);
			futures.add( f);
			startLatch.countDown();
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
		maxRecord = heapSpace / ( 8*4);	// eigentlich /16, aber damit kriegen wir OOME
		maxRecord = Math.min( Integer.MAX_VALUE, maxRecord);
		NumberFormat nfI = nfITL.get();
		System.out.println( "using " + nfI.format( heapSpace)
				+ " bytes for " + nfI.format( maxRecord) + " measurements");
	}

	private static long[] sortResultsMergeLaguna() {
		NumberFormat nfI = nfITL.get();
		long startSortNS = System.nanoTime();

		int sourceCount = lastResultList.size();
		int sourceLength = lastResultList.get(0).length;

		// Copies references only, not the long[] contents.
		long[][] sources = lastResultList.toArray(new long[sourceCount][]);

		long[] result = new long[ sourceCount * sourceLength];
		int[] positions = new int[sourceCount];
		PriorityQueue<Integer> minHeap = new PriorityQueue<>(sourceCount);

		// Initialize heap with all sources, positioned at 0.
		for (int source = 0; source < sourceCount; source++) {
			positions[source] = 0;
			minHeap.offer(source);
		}

		int out = 0;
		while (out < result.length && !minHeap.isEmpty()) {
			int source = minHeap.poll();
			result[out++] = sources[source][positions[source]++];

			if (positions[source] < sourceLength) {
				minHeap.offer(source);
			}
		}

		long sortNS = System.nanoTime() - startSortNS;
		System.out.println( "values sorted Laguna in " + nfI.format( sortNS) + " ns");
		return result;
	}

}
