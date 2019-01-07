/**
 *
 */
package main.java.utils;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Soll als Ersatz für {@link LinkedBlockingQueue} dienen. Erweitert die {@link ConcurrentLinkedQueue} um das
 * {@link BlockingQueue} Interface, wobei aber bei {@link BlockingQueue#take()} keine so schnelle Reaktion auf das
 * Nachfüllen der Queue bei vormals leerer Queue erfolgen kann. Verzichtet auf das Locking und vermeidet damit die bei
 * {@link LinkedBlockingQueue} aufgetretenen Probleme (Bug 38717).
 *
 * @author rhelbing
 *
 */
public class ConcurrentLinkedBlockingQueue<E> extends ConcurrentLinkedQueue<E> implements BlockingQueue<E> {

	static	long	SleepLogIntervalMS = 10000;

	// ist zwar alles lahmgelegt, aber für etwaige Nachforschungen später man noch drin gelassen
	static class Stats {
		long	totalSleepMS = 0;
		long	totalSleeps = 0;
		long	lastSleepLogTime = 0;
		long lastSleep = 0;
		/**
		 * sofort
		 */
		long takes0 = 0;
		/**
		 * nach < 1ms pollen
		 */
		long takes1 = 0;
		/**
		 * echte takes mit sleep
		 */
		long takes2 = 0;
		long	polls0 = 0;
		long	polls1 = 0;
		long	pollsU = 0;
	}

//	ThreadLocal<ConcurrentLinkedBlockingQueue.Stats> tlStats = new ThreadLocal<ConcurrentLinkedBlockingQueue.Stats>() {
//		@Override
//		protected Stats initialValue() {
//			return new Stats();
//		}
//	};

	/**
	 *
	 */
	private static final long serialVersionUID = 3803416920146175588L;
	private AtomicLong size = new AtomicLong( 0);
//	private AtomicLong inCounter = new AtomicLong( 0);
//	private AtomicLong outCounter = new AtomicLong( 0);

	/**
	 * maximale Wartezeit für die {@link #poll()} Schleife in {@link #take()}. Wir beginnen mit 1ms, und verdoppeln bis maximal {@link #maxSleepDurationMS}.
	 */
	private int	maxSleepDurationMS = 10;

	/**
	 *
	 */
	public ConcurrentLinkedBlockingQueue() {
		super();
	}

	/**
	 * @param c
	 */
	public ConcurrentLinkedBlockingQueue( Collection<? extends E> c) {
		super( c);
	}

	@Override
	public void put( E e) throws InterruptedException {
		offer( e);	// wir sind unbounded
	}

	@Override
	public boolean offer( E e, long timeout, TimeUnit unit) throws InterruptedException {
		return offer( e);	// wir sind unbounded
	}

	@Override
	public E take() throws InterruptedException {
		// erster Versuch
		E e = take0();
		if ( e != null)
			return e;
		// zweiter Versuch, polle 1ms ohne Pause
		e = take1ms( 1);
		if ( e != null)
			return e;
		// letzter Versuch: polle mit länger werdenden Sleeps
		e = takeSleep();
		return e;
	}

	private E takeSleep() throws InterruptedException {
		E e = null;
		int	sleepDur = 1;
		while ( e == null) {
			sleep( sleepDur);
			if ( sleepDur < maxSleepDurationMS) {
				sleepDur *= 2;
			}
			e = poll0();
		}
		decSize();
//		Stats	stats = tlStats.get();
//		stats.takes2++;
		return e;
	}

	private E take1ms( int i) {
		E e = poll0();
		long	start = System.currentTimeMillis();
		long	now = start;
		while ( e == null && ( now - start) < 1 ) {
			e = poll0();
			now = System.currentTimeMillis();
		}
		if ( e != null) {
			decSize();
//			Stats	stats = tlStats.get();
//			stats.takes1++;
		}
		return e;
	}

	private E take0() {
		E e = poll0();
		if ( e != null) {
			decSize();
//			Stats	stats = tlStats.get();
//			stats.takes0++;
		}
		return e;
	}

	private void sleep( int sleepDur) throws InterruptedException {
		Thread.sleep( sleepDur);
//		Stats	stats = tlStats.get();
//		stats.totalSleepMS += sleepDur;
//		stats.lastSleep = sleepDur;
//		stats.totalSleeps++;
//		logStats( stats);
	}

//	private void logStats( Stats stats) {
//		long	now = System.currentTimeMillis();
//		if ( stats.lastSleepLogTime > 0) {
//			long	timeSinceMS = now - stats.lastSleepLogTime;
//			if ( timeSinceMS > SleepLogIntervalMS) {
//				NumberFormat	nf = IcubicConstants.NF.get();
//				nf.setMaximumFractionDigits( 2);
//				String	avg = nf.format( ( double) stats.totalSleepMS / stats.totalSleeps);
//				IQLog.logTest.system( Thread.currentThread().getName() +
//						" CLBQ Sleep: " + stats.lastSleep + " Tot: " + stats.totalSleepMS + " ms, " + stats.totalSleeps + " sleeps, Ø: " + avg +
//						" T: " + stats.takes0 + "/" + stats.takes1 + "/" + stats.takes2 +
//						" P: " + stats.polls0 + "/" + stats.polls1 + "/" + stats.pollsU +
//						" QS: " + fastSize() + "/" + size());
//				stats.lastSleepLogTime = now;
//			}
//		} else
//			stats.lastSleepLogTime = now;
//	}

	@Override
	public E poll( long timeout, TimeUnit unit) throws InterruptedException {
		E	e = poll();
		if ( e != null) {
			return e;
		}
		// wir geben noch nicht auf
//		tlStats.get().pollsU--;
		long	millis = unit.toMillis( timeout);
		return poll1( millis);
	}

	private E poll1( long millis) throws InterruptedException {
		long	now = System.currentTimeMillis();
		long	endPoll = now + millis;
		int	sleepDur = 1;
		long	rest = millis;
		E e = null;
//		Stats	stats = tlStats.get();
		while ( now < endPoll) {
			e = super.poll();
			if ( e != null) {
				decSize();
//				stats.polls1++;
				break;	// gefunden
			}
			final long min = Math.min( sleepDur, rest);
			sleep( ( int) min);
			if ( sleepDur < maxSleepDurationMS) {
				sleepDur *= 2;
			}
			now = System.currentTimeMillis();
			rest = endPoll - now;
		}
		if ( e == null) {
//			stats.pollsU++;
		}
		return e;
	}

	@Override
	public int remainingCapacity() {
		return Integer.MAX_VALUE;
	}

	@Override
	public int drainTo( Collection<? super E> c) {
		return drainTo( c, Integer.MAX_VALUE);
	}

	@Override
	public int drainTo( Collection<? super E> c, int maxElements) {
		if ( c == this) {
			throw new IllegalArgumentException( "drain to myself");
		}
		int	counter = 0;
		Iterator<E> it = iterator();
		while ( counter < maxElements && it.hasNext()) {
			E e = it.next();
			c.add( e);
			it.remove();
			counter++;
			if ( e != null) {
				decSize();
			}
		}
		return counter;
	}

	private void decSize() {
//		outCounter.incrementAndGet();
		size.decrementAndGet();
	}

	@Override
	public boolean add( E e) {
		boolean success = super.add( e);
		if ( success)
			incSize();
		return success;
	}

	private void incSize() {
//		inCounter.incrementAndGet();
		size.incrementAndGet();
	}

	@Override
	public boolean offer( E e) {
		boolean success = super.offer( e);
		if ( success) {
			incSize();
		}
		return success;
	}

	public long fastSize() {
		return size.longValue();
	}

	@Override
	public boolean remove( Object o) {
		boolean success =  super.remove( o);
		if ( success) {
			decSize();
		}
		return success;
	}

	@Override
	public E remove() {
		E e = super.remove();
		if ( e != null) {
			decSize();
		}
		return e;
	}

	@Override
	public void clear() {
		super.clear();
		size.set( 0);
//		inCounter.set( 0);
//		outCounter.set( 0);
	}

	public int getMaxSleepDurationMS() {
		return maxSleepDurationMS;
	}

	public void setMaxSleepDurationMS( int maxSleepDurationMS) {
		this.maxSleepDurationMS = maxSleepDurationMS;
	}

	@Override
	public E poll() {
		E e = poll0();
//		Stats stats = tlStats.get();
		if ( e != null) {
			decSize();
//			stats.polls0++;
		} else {
//			stats.pollsU++;
		}
		return e;
	}

	private E poll0() {
		E e = super.poll();
		return e;
	}

}
