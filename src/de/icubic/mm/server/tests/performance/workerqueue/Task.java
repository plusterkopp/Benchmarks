package de.icubic.mm.server.tests.performance.workerqueue;

import de.icubic.mm.bench.base.*;


public class Task implements Runnable {

	public static int Matrix_size = 50;

	static int[][] matrix_A = null;
	static int[][] matrix_B = null;

	static {
		matrix_A = fillupMatrix();
		matrix_B = fillupMatrix();
	}

	int realSize = Matrix_size;
	long	enqueuedAtNano = 0;
	long	finishedAtNano = 0;
	Thread	finishedBy = null;

	static ThreadLocal<int[][]> matrix_R_TL = new ThreadLocal<int[][]>() {
		@Override
		protected int[][] initialValue() {
			return new int[ Matrix_size][ Matrix_size];
		}
	};

	/**
	 *
	 */
	public Task( int size) {
		super();
		realSize = size;
	}

	@Override
	public void run() {
		int[][] matrix_R = matrix_R_TL.get();
		for ( int i = 0; i < realSize; i++) {
			for ( int j = 0; j < realSize; j++) {
				for ( int k = 0; k < realSize; k++) {
					matrix_R[ i][ j] = matrix_A[ i][ k] * matrix_B[ k][ j];
				}
			}
		}
		onFinish();
	}

	private void onFinish() {
		finishedAtNano = BenchRunner.getNow();
		finishedBy = Thread.currentThread();
//		assert( finishedAtNano >= enqueuedAtNano);
	}

	private static int[][] fillupMatrix() {
		int index = 0;
		int[][] matrix = new int[ Matrix_size][ Matrix_size];

		for ( int i = 0; i < Matrix_size; i++) {
			for ( int j = 0; j < Matrix_size; j++) {
				matrix[ i][ j] = index++;
			}
		}
		return matrix;
	}

	public long getSize() {
		return realSize * realSize * realSize;
	}

	public long getLatency() {
		long	latency = finishedAtNano - enqueuedAtNano;
		if ( latency < 0) {
			if ( latency > -1000) {	// on 4 socket, we sometimes get -513/514 ns negative time
				//			BenchLogger.sysout( "Latency < 0: " + latency);
				return 0;
			}
		}
		return latency;
	}

	public void onEnqueue( boolean isLatencyRun) {
		finishedBy = null;
		if ( isLatencyRun) {
			enqueuedAtNano = BenchRunner.getNow();
		}
	}

}