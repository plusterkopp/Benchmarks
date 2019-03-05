package de.icubic.mm.server.utils;

import java.util.*;
import java.util.concurrent.*;

public class ConcurrentLinkedParkingQueue<E> implements BlockingQueue<E> {

    private ConcurrentLinkedQueue<E> queue = new ConcurrentLinkedQueue<>();
    private WaitStrategy waitStrategy;

    public ConcurrentLinkedParkingQueue(int numConsumers) {
        this( new ParkWaitStrategy( numConsumers));
    }

    public ConcurrentLinkedParkingQueue(WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
    }

    @Override
    public E remove() {
        throw new IllegalArgumentException();
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new IllegalArgumentException();
    }

    @Override
    public E poll() {
        return queue.poll();
    }

    @Override
    public E peek() {
        return queue.peek();
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public Iterator<E> iterator() {
        return queue.iterator();
    }

    @Override
    public Object[] toArray() {
        return queue.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return queue.toArray( a);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return queue.containsAll( c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return queue.removeAll( c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return queue.retainAll( c);
    }

    @Override
    public void clear() {
        queue.clear();
    }

    @Override
    public boolean add(E e) {
        throw new IllegalArgumentException();
    }

    @Override
    public boolean offer(E e) {
        queue.offer(e);
        waitStrategy.release();
        return true;
    }

    @Override
    public void put(E e) throws InterruptedException {
        queue.add(e);
        waitStrategy.release();
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit)
            throws InterruptedException {
        put(e);
        return true;
    }

    @Override
    public E take() throws InterruptedException {
        while (true) {
            if (Thread.currentThread().isInterrupted()) return null;
            E value = queue.poll();
            if (value != null)
                return value;
            waitStrategy.block();
        }
    }

	@Override
	public E poll( long timeout, TimeUnit unit) throws InterruptedException {
		E	e = poll();
		if ( e != null) {
			return e;
		}
		long	millis = unit.toMillis( timeout);
		return poll1( millis);
	}

	private E poll1( long millis) throws InterruptedException {
		long	now = System.currentTimeMillis();
		long	endPoll = now + millis;
		int	sleepDur = 1;
		long	rest = millis;
		E e = null;
		while ( now < endPoll) {
			e = poll();
			if ( e != null) {
				break;	// gefunden
			}
			final long min = Math.min( sleepDur, rest);
			Thread.sleep( ( int) min);
			if ( sleepDur < 10) {
				sleepDur *= 2;
			}
			now = System.currentTimeMillis();
			rest = endPoll - now;
		}
		return e;
	}

    @Override
    public int remainingCapacity() {
		return Integer.MAX_VALUE;
    }

    @Override
    public boolean remove(Object o) {
        throw new IllegalArgumentException();
    }

    @Override
    public boolean contains(Object o) {
        throw new IllegalArgumentException();
    }

    @Override
    public int drainTo(Collection<? super E> c) {
		return drainTo( c, Integer.MAX_VALUE);
   }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
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
		}
		return counter;
  }

    @Override
    public E element() {
        throw new IllegalArgumentException();
    }

}
