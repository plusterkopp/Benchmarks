/**
 *
 */
package benches;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.communication.util.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * @author rhelbing
 *
 * Startparameter:
-server
-Xss2m
-Xmx8G (2/3 des physischen RAMs)
-Xms3G
-XX:NewSize=2G
-XX:PermSize=256M
-XX:+UseConcMarkSweepGC
-XX:+UseNUMAInterleaving
-XX:+UseCompressedOops
-XX:+DoEscapeAnalysis
-XX:+TieredCompilation
-Xloggc:logs/gc.log
-verbose:gc
-XX:+PrintGCDateStamps
-XX:+PrintGCDetails
-XX:+PrintPromotionFailure
 *
 */
public class MemSpeed extends AbstractBenchRunnable {

	static enum RunMode {
		SerialParallel,	// mit N Threads, die jeweils einen gleich großen Abschnitt des Speichers bearbeiten
		Serial,	// durchläuft die aufeinanderfolgenden Longs aller Buckets, geht am Ende zum nächsten Bucket
		// Random,	// springt von Bucket zu Bucket bei gleichen Index im Bucket, also größere Sprünge
		}
	static int	mega = 1024 * 1024;
	/**
	 * @param args
	 */
	public static void main( String[] args) {
		long	memSizeMax = Runtime.getRuntime().maxMemory();

		BenchRunner	runner = new BenchRunner( null);
		runner.setRuntime( TimeUnit.SECONDS, 30);
		double	memSizeMB = 1024;
		double	lastMemSizeMB = 0;
		MemSpeed	instance = new MemSpeed( ( long) memSizeMB);
		runner.setCSVName( "MemSpeed.csv", instance.getCSVHeader());
		while ( memSizeMB * mega < memSizeMax) {
			try {
				if ( ( long) lastMemSizeMB != ( long) memSizeMB) {
					instance = new MemSpeed( ( long) memSizeMB);
					runner.setBenchRunner( instance);
					for ( RunMode rm: RunMode.values()) {
						instance.runmode = rm;
						runner.run();
//						BenchRunner.addToComparisonList( instance.getName(), runner.getRunsPerSecond());
						runner.printResults();
						double	rps = runner.getRunsPerSecond();
						instance.setResult( rm, rps);
						double	mbps = rps / mega;
						BenchLogger.sysout( instance.getName() + ": " + BenchLogger.LNF.format( mbps) + " MB/s");
					}
					runner.writeCSV( instance.getCSVLine());
					instance.dispose();
					System.gc();
					lastMemSizeMB = memSizeMB;
				}
				memSizeMB = memSizeMB * Math.sqrt( 2);
				runner.setBenchRunner( null);
			} catch ( OutOfMemoryError ooe) {
				runner.setBenchRunner( null);
				System.gc();
				BenchLogger.syserr( "OOE at " + memSizeMB, ooe);
				System.exit( 0);
			}
		}
//		BenchRunner.printComparisonList();
	}

	private void dispose() {
		arrays.clear();
	}

	private Map<RunMode, Double> results = new EnumMap<RunMode, Double>( RunMode.class);

	private void setResult( RunMode rm, double rps) {
		results .put( rm, rps);
	}

	/**
	 * Bytes der Felder
	 */
	private long memSize;
	/**
	 * Liste der Buckets
	 */
	ArrayList<long[]>	arrays;
	/**
	 * Gesamtzahl der Long-Einträge aller Buckets
	 */
	private long nLongs;;
	/**
	 * nur zur Verifikation
	 */
	private long startValue;

	private RunMode	runmode;

	/**
	 * zur Verhinderung von mehrfachem Anfordern des Speichers für den zweiten Runmode
	 */
	private long allocated;

	public MemSpeed( long memSizeMB) {
		this( "MemSize " + memSizeMB + " MB");
		memSize = memSizeMB * mega;
		nLongs = memSize / 8;
		runmode = RunMode.Serial;
		allocated = 0;
		setup();
	}

	public MemSpeed( String string) {
		super( string);
	}

	private void allocate( final ArrayList<long[]> arrays, long nLongs) {
		final AtomicLong	remaining = new AtomicLong( nLongs);
		final int nThreads = Runtime.getRuntime().availableProcessors();
		final ConcurrentLinkedQueue<long[]> q = new ConcurrentLinkedQueue<long[]>();
		final StringBuffer	sb = new StringBuffer();
		ThreadPoolExecutor tpe = AddThreadBeforeQueuingThreadPoolExecutor.getExecutor( nThreads, "Allocator", new LinkedBlockingQueue<Runnable>());
		for ( int ti = 0;  ti < nThreads;  ti++) {
			tpe.execute( new Runnable() {
				@Override
				public void run() {
					int	allocs = 0;
					while ( remaining.getAndAdd( -mega) > mega) {
						long[] bucket = new long[ mega];	// mache alle Buckets 8 MB groß, auch wenn das vielleicht mehr ist als verlangt
						q.add( bucket);
						allocs++;
					}
					remaining.addAndGet( mega);
					if ( allocs > 0)
						sb.append( allocs + ", ");
				}
			});
		}
		tpe.shutdown();
		try {
			while ( ! tpe.awaitTermination( 10, TimeUnit.SECONDS)) {
				BenchLogger.sysout( "Waiting for Allocators");
			}
		} catch ( InterruptedException e) {
			BenchLogger.syserr( "Interrupted Waiting for Allocators", e);
			System.exit( 1);
		}
		if ( sb.length() > 0) {
//			BenchLogger.sysout( "Allocs: " + sb.toString());
		}
		arrays.addAll( q);
		while ( remaining.getAndAdd( -mega) > 0) {
			long[] bucket = new long[ mega];	// mache alle Buckets 8 MB groß, auch wenn das vielleicht mehr ist als verlangt
			arrays.add( bucket);
		}
	}

	private long[] getArrayForIndex( long index) {
		return arrays.get( getArrayIndexForIndex( index));
	}

	private int getArrayIndexForIndex( long index) {
		final long shifted = index >> 20;
		int	arrayIndex = ( int) shifted;
		return arrayIndex;
	}

	private int getIndexInArrayForIndex( long index) {
		return ( int) ( index & ( mega - 1));
	}

	@Override
	public String getName() {
		String	s = super.getName();
		return s + " " + runmode;
	}

	/* (non-Javadoc)
	 * @see de.icubic.utils.bench.base.IBenchRunnable#getRunSize()
	 */
	@Override
	public long getRunSize() {
		// Zahl der Bytes
		return memSize;
	}

	long	getValue( long index) {
		long[] array = getArrayForIndex( index);
		int	indexInArray = getIndexInArrayForIndex( index);
		return array[ indexInArray];
	}

	void	putValue( long index, long value) {
		int	indexInArray = ( int) ( index & ( mega - 1));
		long[] array = getArrayForIndex( index);
		array[ indexInArray] = value;
	}

	@Override
	public void run() {
		switch ( runmode) {
		case Serial:
			runSerial(); break;
//		case Random:
//			runRandom(); break;
		case SerialParallel:
			runSerialParallel(); break;
		}
	}

	/**
	 * nicht wirklich random, sondern nimmt jedesmal einen anderen Bucket, also die Innenschleife geht über verschiedene Felder so daß Quelle und Ziel der Zuweisung weit auseinander liegen
	 */
	private void runRandom() {
//		long lastValue = startValue;
		final int size = arrays.size();
		// vermeide ArrayList.rangeCheck()
		long[][]	a = new long[arrays.size()][];
		for ( int i = 0;  i < size;  i++) {
			a[ i] = arrays.get( i);
		}
		for ( int outer = 0;  outer < mega;  outer++) {
			for ( int inner = 1;  inner < size;  inner++) {
				long[] sArray = a[ inner - 1];
				long[] dArray = a[ inner];
				dArray[ outer] = sArray[ outer] + 1;
			}
			if ( outer < ( mega - 1)) {
				long[] sArray = a[ size - 1];
				long[] dArray = a[ 0];
				dArray[ outer+1] = sArray[ outer] + 1;
			}
		}
	}

	private void runSerial() {
		long[]	array = arrays.get( 0);
//		long lastValue = startValue;	// nur zur Verifikation, nicht für den Bench-Lauf
		int outerRun = 0;
		long remaining = nLongs;
		while ( remaining > 0) {
			long	longs = Math.min( remaining, mega);
			remaining -= longs;
			for ( int i = 1;  i < longs;  i++) {
				array[ i] = array[ i - 1] + 1;
//				lastValue = array[ i];
			}
			if ( remaining > 0) {
				long[] lastArray = array;
				outerRun++;
				array = arrays.get( outerRun);
				array[ 0] = lastArray[ mega - 1] + 1;
//				lastValue = array[ 0];
				remaining--;
			}
		}
//		if ( startValue + nLongs != lastValue + 1) {
//			System.err.print( "Mismatch: " + startValue + " + " + nLongs + " != " + lastValue);
//			System.exit( 0);
//		}
	}

	private void runSerial( long startIndex, long numLongs) {
		int outerRun = getArrayIndexForIndex( startIndex);
		long[]	array = arrays.get( outerRun);
		long remaining = numLongs;
		int startIndexInArray = getIndexInArrayForIndex( startIndex);
		while ( remaining > 0) {
			long	longs = Math.min( remaining, mega);
			remaining -= longs;
			for ( int i = startIndexInArray + 1;  i < longs;  i++) {
				array[ i] = array[ i - 1] + 1;
			}
			if ( remaining > 0) {
				long[] lastArray = array;
				outerRun++;
				array = arrays.get( outerRun);
				array[ 0] = lastArray[ mega - 1] + 1;
				remaining--;
			}
		}
	}

	private void runSerialParallel() {
		int	nThreads = Runtime.getRuntime().availableProcessors();
		final long	longsPerThread = nLongs / nThreads;
		final CountDownLatch	starter = new CountDownLatch( nThreads);
		final CountDownLatch	stopper = new CountDownLatch( nThreads);
		for ( int tc = 0;  tc < nThreads;  tc++) {
			final long	startIndex = tc * longsPerThread;
			final String tName = "SP " + tc;
			Thread	t = new IQuoteThread( tName) {
				public void run() {
//					final String name = Thread.currentThread().getName();
//					System.out.print( name + " started ");
					starter.countDown();
					try {
						starter.await( 1, TimeUnit.SECONDS);
					} catch ( InterruptedException e) {
						BenchLogger.syserr( "can not start " + tName, e);
						return;
					}
//					System.out.print( name + " running ");
					runSerial( startIndex, longsPerThread);
					stopper.countDown();
//					System.out.print( name + " finished ");
				}
			};
			t.start();
		}
//		BenchLogger.sysout( nThreads + "Threads launched ");
		try {
			stopper.await();
		} catch ( InterruptedException e) {
			BenchLogger.syserr( "can not wait", e);
			return;
		}
	}

	@Override
	public void setup() {
		System.gc();
		if ( allocated >= nLongs)
			return;
		super.setup();
		arrays = new ArrayList<long[]>();
		long	nLongs = memSize / 8;
		allocate( arrays, nLongs);
		System.gc();
		if ( arrays.isEmpty())
			return;
		long[] array = arrays.get( 0);
		if ( array.length > 0) {
			startValue = ( long) ( 1000 * Math.random());
			array[ 0] = startValue;
		}
		for ( long[] a: arrays) {
			allocated += a.length;
		}
	}

	/* (non-Javadoc)
	 * @see de.icubic.utils.bench.base.AbstractBenchRunnable#getCSVHeader()
	 */
	@Override
	public String getCSVHeader() {
		return toCSV( "MB"
				,  RunMode.SerialParallel + " MB/s"
				,  RunMode.Serial + " MB/s"
//				, RunMode.Random + " MB/s"
				);
	}

	/* (non-Javadoc)
	 * @see de.icubic.utils.bench.base.AbstractBenchRunnable#getCSVLine()
	 */
	@Override
	public String getCSVLine() {
		return toCSV( memSize / mega
				, results.get( RunMode.SerialParallel) / mega
				, results.get( RunMode.Serial) / mega
//				, results.get( RunMode.Random) / mega
				);
	}

}
