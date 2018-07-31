package de.spaceship;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PerfTest {
	private static final long TEST_COOL_OFF_MS = 10;
	private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

	private static final Spaceship[] SPACESHIPS = { new SynchronizedSpaceship(), new ReadWriteLockSpaceShip(),
			new ReentrantLockSpaceship(), new StampedLockSpaceship(), new StampedLockWithRetriesSpaceship(),
			new LockFreeSpaceship(), };

	private static int NUM_WRITERS;
	private static int NUM_READERS;
	private static long TEST_DURATION_MS;

	public static void main(final String[] args) throws Exception {
		NUM_READERS = Integer.parseInt(args[0]);
		NUM_WRITERS = Integer.parseInt(args[1]);
		TEST_DURATION_MS = Long.parseLong(args[2]);

		for (int i = 0; i < 5; i++) {
			System.out.println("*** Run - " + i);
			for (final Spaceship spaceship : SPACESHIPS) {
				System.gc();
				Thread.sleep(TEST_COOL_OFF_MS);

				perfRun(spaceship);
			}
		}

		EXECUTOR.shutdown();
	}

	private static void perfRun(final Spaceship spaceship) throws Exception {
		final Results results = new Results();
		final CyclicBarrier startBarrier = new CyclicBarrier(NUM_READERS + NUM_WRITERS + 1);
		final CountDownLatch finishLatch = new CountDownLatch(NUM_READERS + NUM_WRITERS);
		final AtomicBoolean runningFlag = new AtomicBoolean(true);

		for (int i = 0; i < NUM_WRITERS; i++) {
			EXECUTOR.execute(new WriterRunner(i, results, spaceship, runningFlag, startBarrier, finishLatch));
		}

		for (int i = 0; i < NUM_READERS; i++) {
			EXECUTOR.execute(new ReaderRunner(i, results, spaceship, runningFlag, startBarrier, finishLatch));
		}

		awaitBarrier(startBarrier);

		Thread.sleep(TEST_DURATION_MS);
		runningFlag.set(false);

		finishLatch.await();

		System.out.format("%d readers %d writers %31s %s%n", NUM_READERS, NUM_WRITERS,
				spaceship.getClass().getSimpleName(), results);
	}

	private static void awaitBarrier(final CyclicBarrier barrier) {
		try {
			barrier.await();
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	static class Results {
		final long[] reads = new long[NUM_READERS];
		final long[] moves = new long[NUM_WRITERS];

		final long[] readAttempts = new long[NUM_READERS];
		final long[] observedMoves = new long[NUM_READERS];
		final long[] moveAttempts = new long[NUM_WRITERS];

		public String toString() {
			long totalReads = 0;
			for (final long v : reads) {
				totalReads += v;
			}
			final String readsSummary = String.format("%,d : ", totalReads);

			long totalMoves = 0;
			for (final long v : moves) {
				totalMoves += v;
			}
			final String movesSummary = String.format("%,d : ", totalMoves);

			long totalReadAttempts = 0;
			for (final long v : readAttempts) {
				totalReadAttempts += v;
			}
			final String readAttemptsSummary = String.format("%,d : ", totalReadAttempts);

			long totalMoveAttempts = 0;
			for (final long v : moveAttempts) {
				totalMoveAttempts += v;
			}
			final String moveAttemptsSummary = String.format("%,d : ", totalMoveAttempts);

			long totalObservedMoves = 0;
			for (final long v : observedMoves) {
				totalObservedMoves += v;
			}
			final String observedMovesSummary = String.format("%,d : ", totalObservedMoves);

			return "reads=" + readsSummary + ( int) ( totalReads / ( 1e3 * TEST_DURATION_MS)) + " M/s" // + Arrays.toString(reads) 
				+ " moves=" + movesSummary + ( int) ( totalMoves / ( 1e3 * TEST_DURATION_MS)) + " M/s" // + Arrays.toString(moves)
				+ " readAttempts=" + ( 100 * totalReadAttempts / totalReads) + "%" // readAttemptsSummary + ( int) ( totalReadAttempts / ( 1e3 * TEST_DURATION_MS)) + " M/s" // + Arrays.toString(readAttempts) 
				+ " moveAttempts=" + ( 100 * totalMoveAttempts / totalMoves) + "%" // moveAttemptsSummary + ( int) ( totalMoveAttempts / ( 1e3 * TEST_DURATION_MS)) + " M/s" // + Arrays.toString(moveAttempts) 
				+ " observedMoves=" + observedMovesSummary + ( int) ( totalObservedMoves / ( 1e3 * TEST_DURATION_MS)) + " M/s" // + Arrays.toString(observedMoves)
				;
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

			latch.countDown();
		}
	}
}
