/**
 *
 */
package de.icubic.mm.bench.benches;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * incrementiere Variable unter Lock, mit verschiedenen Locks.<br>
 * Wir haben N Longs, R Lese-Threads, die die Werte unter Leselock auslesen und ihn in einen
 * Zeitraum T verarbeiten. M Threads, die die Werte mit einer Wahrscheinlichkeit P verändern.
 *
 * @author rhelbing
 *
 */
public class LockModBench {

	static final ThreadLocal<Random> tlRnd = ThreadLocal.withInitial( () -> new Random());
	static final int N = 8;
	/**
	 * Platz zwischen den zu lesenden Werten. Damit es dem Cache nicht zu gut geht, lesen wir die
	 * Werte auch noch alle, nachden wir den eigentlichen Werten haben.
	 */
	static final int PaddingFactor = 100000;
	static final long[] values = new long[ N * PaddingFactor];

	static final int R = 2;
	static final int M = 1;
	static final double P = 0.01;
	/**
	 * Nanos
	 */
	static final long T = 1000;
	static long TLoops;

	static interface LockType {
		void readAndProcess();
		boolean maybeWrite();
	}

	static class ReaderT<T extends LockType> implements Runnable {

		AtomicBoolean	stop = new AtomicBoolean( false);
		long	counter = 0;
		final T	locktype;

		/**
		 * @param locktype
		 */
		private ReaderT( T locktype) {
			super();
			this.locktype = locktype;
		}

		@Override
		public void run() {
			while ( ! stop.get()) {
				// hole Werte und dazugehöriges Lock, verarbeite für gewünschte Zeit
				locktype.readAndProcess();
				counter++;
			}
		}
	}

	static class LTSync implements LockType {

		static final Object[]	locks = new Object[ N];
		static {
			for( int i = 0;  i < N;  i++) {
				locks[ i] = new Object();
			}
		}

		@Override
		public void readAndProcess() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			synchronized ( locks[ index]) {
				int startIndex = index * PaddingFactor;
				long	v = values[ startIndex];
				process( v, values, startIndex);
			}
		}

		@Override
		public boolean maybeWrite() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			double p = rnd.nextDouble();
			if ( p < P) {
				synchronized ( locks[ index]) {
					int startIndex = index * PaddingFactor;
					values[ startIndex]++;
				}
				return true;
			}
			return false;
		}
	}

	static class LTReeLock implements LockType {

		static final ReentrantReadWriteLock[]	locks = new ReentrantReadWriteLock[ N];
		static {
			for( int i = 0;  i < N;  i++) {
				locks[ i] = new ReentrantReadWriteLock();
			}
		}

		@Override
		public void readAndProcess() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			Lock	l = locks[ index].readLock();
			l.lock();
			try {
				int startIndex = index * PaddingFactor;
				long	v = values[ startIndex];
				process( v, values, startIndex);
			} finally {
				l.unlock();
			}
		}

		@Override
		public boolean maybeWrite() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			double p = rnd.nextDouble();
			if ( p < P) {
				Lock	l = locks[ index].writeLock();
				l.lock();
				try {
					int startIndex = index * PaddingFactor;
					values[ startIndex]++;
				} finally {
					l.unlock();
				}
				return true;
			}
			return false;
		}
	}

	static class LTStPLock implements LockType {

		static final StampedLock[]	locks = new StampedLock[ N];
		static {
			for( int i = 0;  i < N;  i++) {
				locks[ i] = new StampedLock();
			}
		}

		@Override
		public void readAndProcess() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			StampedLock	l = locks[ index];
			long	stamp = l.readLock();
			try {
				int startIndex = index * PaddingFactor;
				long	v = values[ startIndex];
				process( v, values, startIndex);
			} finally {
				l.unlockRead( stamp);
			}
		}

		@Override
		public boolean maybeWrite() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			double p = rnd.nextDouble();
			if ( p < P) {
				StampedLock	l = locks[ index];
				long stamp = l.writeLock();
				try {
					int startIndex = index * PaddingFactor;
					values[ startIndex]++;
				} finally {
					l.unlockWrite( stamp);
				}
				return true;
			}
			return false;
		}
	}

	static class LTStOLock implements LockType {

		static final StampedLock[]	locks = new StampedLock[ N];
		static {
			for( int i = 0;  i < N;  i++) {
				locks[ i] = new StampedLock();
			}
		}

		@Override
		public void readAndProcess() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			StampedLock	l = locks[ index];
			long	stamp = l.tryOptimisticRead();
			try {
				int startIndex = index * PaddingFactor;
				long	v = values[ startIndex];
				process( v, values, startIndex);
				if ( ! l.validate( stamp)) {
					stamp = l.readLock();
					v = values[ startIndex];
					process( v, values, startIndex);
				}
			} finally {
				l.unlockRead( stamp);
			}
		}

		@Override
		public boolean maybeWrite() {
			Random rnd = tlRnd.get();
			int index = rnd.nextInt( N);
			double p = rnd.nextDouble();
			if ( p < P) {
				StampedLock	l = locks[ index];
				long stamp = l.writeLock();
				try {
					int startIndex = index * PaddingFactor;
					values[ startIndex]++;
				} finally {
					l.unlockWrite( stamp);
				}
				return true;
			}
			return false;
		}
	}

	static void process( long v, long[] values, int startIndex) {
		long	l;
		int 	i;
		long	vv = v;
		for ( l = 0, i = 0;  l < TLoops;  l++) {
			vv += values[ i + startIndex];
			i++;
			if ( i > PaddingFactor) {
				i = 0;
			}
		}
		if ( vv < v) {
			System.out.println( "should not get smaller");
		}
	}
}
