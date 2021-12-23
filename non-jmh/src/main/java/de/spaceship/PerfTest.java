package de.spaceship;

import org.apache.commons.math3.stat.descriptive.*;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;

public class PerfTest {
	private static final long TEST_COOL_OFF_MS = 10;
	private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

	private static final Spaceship[] SPACESHIPS = { new SynchronizedSpaceship(), new ReadWriteLockSpaceShip(),
			new ReentrantLockSpaceship(), new StampedLockSpaceship(), new StampedLockWithRetriesSpaceship(),
			new LockFreeSpaceship(), };

	private static long TEST_DURATION_MS;

	static NumberFormat NF = DecimalFormat.getNumberInstance( Locale.US);

	public static void main( final String[] args) throws Exception {
		NF.setMaximumFractionDigits( 2);
		int cpus = Runtime.getRuntime().availableProcessors();
		TEST_DURATION_MS = Long.parseLong( args[ 0]);
		int nRuns = Integer.parseInt( args[ 1]);

		List<Results> results = new ArrayList<>();

		for ( int nr = 1; nr <= cpus; nr *= 2) {
			for ( int nw = 1; nw <= cpus - nr; nw *= 2) {
				for ( int i = 0; i < nRuns; i++) {
					System.out.println( "*** Run - " + nr + " r - " + nw + " w: #" + i);
					for ( final Spaceship spaceship : SPACESHIPS) {
						System.gc();
						Thread.sleep( TEST_COOL_OFF_MS);

						Results result = perfRun( spaceship, nr, nw);
						results.add( result);
					}
				}
			}
		}
		analyzeAll( results);
		EXECUTOR.shutdown();
	}

	private static void analyzeAll( List<Results> results) {
		for ( final Spaceship spaceship : SPACESHIPS) {
			List<Results> spaceShipResults = results.stream()
					.filter( r -> spaceship.getClass().getSimpleName().equals( r.className))
					.collect( Collectors.toList());
			boolean hasR = true;
			for ( int nr = 1; hasR; nr *= 2) {
				int fnr = nr;
				List<Results> resultsR = spaceShipResults.stream()
						.filter( r -> r.numReaders == fnr)
						.collect( Collectors.toList());
				if ( resultsR.isEmpty()) {
					hasR = false;
					continue;
				}
				boolean hasW = true;
				for ( int nw = 1; hasW; nw *= 2) {
					int fnw = nw;
					List<Results> resultsW = resultsR.stream()
							.filter( r -> r.numWriters == fnw)
							.collect( Collectors.toList());
					if ( resultsW.isEmpty()) {
						hasW = false;
						continue;
					}
					analyze( resultsW);
				}
			}
		}
	}

	/**
	 * @param results same {@link Spaceship}, same number of readers and writers
	 */
	private static void analyze( List<Results> results) {
		SummaryStatistics	trStats = statsFrom( results, r -> r.totalReads * 1e-6);
		SummaryStatistics	tmStats = statsFrom( results, r -> r.totalMoves * 1e-6);
		SummaryStatistics	traStats = statsFrom( results, r -> ( double) r.totalReadAttempts / r.totalReads);
		SummaryStatistics	tmaStats = statsFrom( results, r -> ( double) r.totalMoveAttempts/ r.totalMoves);
		SummaryStatistics	tomStats = statsFrom( results, r -> r.totalObservedMoves * 1e-6);
		Results r1 = results.get( 0);
		String out = r1.className + " " + r1.numReaders + " r/" + r1.numWriters + " w";
		out += " totalReads Ø " + format( trStats.getMean()) + ", σ " + format( trStats.getStandardDeviation());
		out += " totalMoves Ø " + format( tmStats.getMean()) + ", σ " + format( tmStats.getStandardDeviation());
		out += " totalReadAttempts Ø " + format( traStats.getMean()) + ", σ " + format( traStats.getStandardDeviation());
		out += " totalMoveAttempts Ø " + format( tmaStats.getMean()) + ", σ " + format( tmaStats.getStandardDeviation());
		out += " totalObservedMoves Ø " + format( tomStats.getMean()) + ", σ " + format( tomStats.getStandardDeviation());
		System.out.println( out);
	}

	static SummaryStatistics statsFrom( Collection<Results> results, ToDoubleFunction<Results> f) {
		SummaryStatistics stats = new SummaryStatistics();
		for ( Results r : results) {
			stats.addValue( f.applyAsDouble( r));
		}
		return stats;
	}

	private static Results perfRun(final Spaceship spaceship, int numReaders, int numWriters) throws Exception {
		final Results results = new Results( spaceship.getClass().getSimpleName(), numReaders, numWriters);
		final CyclicBarrier startBarrier = new CyclicBarrier( numReaders + numWriters + 1);
		final CountDownLatch finishLatch = new CountDownLatch( numReaders + numWriters);
		final AtomicBoolean runningFlag = new AtomicBoolean(true);

		for (int i = 0; i < numWriters; i++) {
			EXECUTOR.execute(new WriterRunner(i, results, spaceship, runningFlag, startBarrier, finishLatch));
		}

		for (int i = 0; i < numReaders; i++) {
			EXECUTOR.execute(new ReaderRunner(i, results, spaceship, runningFlag, startBarrier, finishLatch));
		}

		awaitBarrier(startBarrier);

		Thread.sleep(TEST_DURATION_MS);
		runningFlag.set(false);

		finishLatch.await();

		System.out.format("%d readers %d writers %31s %s%n", numReaders, numWriters,
				spaceship.getClass().getSimpleName(), results);

		return results;
	}

	private static void awaitBarrier(final CyclicBarrier barrier) {
		try {
			barrier.await();
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	static String format( double x) {
		if ( x >= 1) {
			NF.setMaximumFractionDigits( 2);
		} else if ( x >= 0.1) {
			NF.setMaximumFractionDigits( 3);
		} else if ( x >= 0.01) {
			NF.setMaximumFractionDigits( 4);
		} else {
			NF.setMaximumFractionDigits( 5);
		}
		return NF.format( x);
	}

	static class Results {
		final long[] reads;
		final long[] moves;

		final long[] readAttempts;
		final long[] observedMoves;
		final long[] moveAttempts;

		final int numReaders;
		final int numWriters;
		final String className;

		long totalReads;
		long totalMoves;
		long totalReadAttempts;
		long totalMoveAttempts;
		long totalObservedMoves;

		public Results( String className, int r, int w) {
			numReaders = r;
			numWriters = w;
			this.className = className;

			reads = new long[ numReaders];
			moves = new long[ numWriters];
			readAttempts = new long[ numReaders];
			observedMoves = new long[ numReaders];
			moveAttempts = new long[ numWriters];
		}

		public String toString() {

			final String readsSummary = String.format("%,d : ", totalReads);
			final String movesSummary = String.format("%,d : ", totalMoves);
			final String readAttemptsSummary = String.format("%,d : ", totalReadAttempts);
			final String moveAttemptsSummary = String.format("%,d : ", totalMoveAttempts);
			final String observedMovesSummary = String.format("%,d : ", totalObservedMoves);

			return "reads=" + format( totalReads / ( 1e3 * TEST_DURATION_MS)) + " M/s" // + readsSummary + Arrays.toString(reads)
				+ " moves=" + format( totalMoves / ( 1e3 * TEST_DURATION_MS)) + " M/s" // + movesSummary + Arrays.toString(moves)
				+ " readAttempts=" + ( 100 * totalReadAttempts / totalReads) + "%" // readAttemptsSummary + ( int) ( totalReadAttempts / ( 1e3 * TEST_DURATION_MS)) + " M/s" // + Arrays.toString(readAttempts)
				+ " moveAttempts=" + ( 100 * totalMoveAttempts / totalMoves) + "%" // moveAttemptsSummary + ( int) ( totalMoveAttempts / ( 1e3 * TEST_DURATION_MS)) + " M/s" // + Arrays.toString(moveAttempts)
				+ " observedMoves=" + format( totalObservedMoves / ( 1e3 * TEST_DURATION_MS)) + " M/s" // + observedMovesSummary + Arrays.toString(observedMoves)
				;
		}

		public synchronized void close() {
			totalReads = LongStream.of( reads).sum();
			totalMoves = LongStream.of( moves).sum();
			totalReadAttempts = LongStream.of( readAttempts).sum();
			totalMoveAttempts = LongStream.of( moveAttempts).sum();
			totalObservedMoves = LongStream.of( observedMoves).sum();
		}

	}

	static class WriterRunner implements Runnable {
		private final int id;
		private final Results results;
		private final Spaceship spaceship;
		private final AtomicBoolean runningFlag;
		private final CyclicBarrier barrier;
		private final CountDownLatch latch;

		WriterRunner(final int id, final Results results, final Spaceship spaceship, final AtomicBoolean runningFlag,
				final CyclicBarrier barrier, final CountDownLatch latch) {
			this.id = id;
			this.results = results;
			this.spaceship = spaceship;
			this.runningFlag = runningFlag;
			this.barrier = barrier;
			this.latch = latch;
		}

		public void run() {
			awaitBarrier(barrier);

			long movesCounter = 0;
			long movedAttemptsCount = 0;

			while (runningFlag.get()) {
				movedAttemptsCount += spaceship.move(1, 1);

				++movesCounter;
			}

			results.moveAttempts[id] = movedAttemptsCount;
			results.moves[id] = movesCounter;
			results.close();

			latch.countDown();
		}
	}

	static class ReaderRunner implements Runnable {
		private final int id;
		private final Results results;
		private final Spaceship spaceship;
		private final AtomicBoolean runningFlag;
		private final CyclicBarrier barrier;
		private final CountDownLatch latch;

		ReaderRunner(final int id, final Results results, final Spaceship spaceship, final AtomicBoolean runningFlag,
				final CyclicBarrier barrier, final CountDownLatch latch) {
			this.id = id;
			this.results = results;
			this.spaceship = spaceship;
			this.runningFlag = runningFlag;
			this.barrier = barrier;
			this.latch = latch;
		}

		public void run() {
			awaitBarrier(barrier);

			int[] currentCoordinates = new int[] { 0, 0 };
			int[] lastCoordinates = new int[] { 0, 0 };

			long readsCount = 0;
			long readAttemptsCount = 0;
			long observedMoves = 0;

			while (runningFlag.get()) {
				readAttemptsCount += spaceship.readPosition(currentCoordinates);

				if (lastCoordinates[0] != currentCoordinates[0] || lastCoordinates[1] != currentCoordinates[1]) {
					++observedMoves;
					lastCoordinates[0] = currentCoordinates[0];
					lastCoordinates[1] = currentCoordinates[1];
				}

				++readsCount;
			}

			results.reads[id] = readsCount;
			results.readAttempts[id] = readAttemptsCount;
			results.observedMoves[id] = observedMoves;
			results.close();

			latch.countDown();
		}
	}
}
