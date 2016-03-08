package de.icubic.mm.server.tests.performance.workerqueue;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import de.icubic.mm.bench.base.*;

public class WorkStealingQueue extends AWorkQueue {
	private AtomicInteger queue_no = new AtomicInteger();
	private BlockingDeque<Runnable>[] queues;

	/* constructor to initiate worker threads and queues associated with it */
	public WorkStealingQueue( int nThreads, int nQueues, int totalTasks, boolean useAffinity) {
		super( nThreads, nQueues, totalTasks, useAffinity);

		queues = new BlockingDeque[ nThreads];
		for ( int i = 0; i < nQueues; i++) {
			queues[ i] = new LinkedBlockingDeque<Runnable>();
		}
	}

	Runnable stealWork( int index) {
		for ( int i = 0; i < maxQueues; i++) {
			if ( i != index) {
				Object o = queues[ i].pollFirst();
				if ( o != null) {
					return ( Runnable) o;
				}
			}
		}
		return null;
	}

	@Override
	public void createQueue(int threadIndex) {
		// TODO Auto-generated method stub		
	}

	/* Executes the given task in the future. Queues the task and notifies the waiting thread. Also it makes the Work
	 * assigner to wait if the queued task reaches to threshold */
	@Override
	public void execute( Runnable r) {
		try {
			final int index = ( queue_no.getAndIncrement() % maxQueues);
			queues[ index].putFirst( r);
//			queues[ queue_no].putFirst( r);
//			queue_no++;
//			if ( queue_no == maxQueues) {
//				queue_no = 0;
//			}
//			taskQueued.incrementAndGet();
		} catch ( InterruptedException e) {
			BenchLogger.syserr( "", e);
		}
	}

	/* Clean-up the worker thread when all the tasks are done */
	@Override
	public synchronized void doInterruptAllWaitingThreads() {
		// Interrupt all the threads
		for ( int i = 0; i < nThreads; i++) {
			threads[ i].interrupt();
		}
		synchronized ( lock) {
			lock.notify();
		}

	}

	/* Worker thread to execute user tasks */
	class PoolWorker extends APoolWorker {

		private int queueIndex;

		PoolWorker( int qi, int index, String queueName) {
			super( qi, index, queueName);
			queueIndex = qi;
		}

		/* Method to retrieve task from worker queues and start executing it. This thread will wait for a task if there
		 * is no task in the queues. */
		@Override
		public void run() {

			Runnable r = null;

			while ( stopNow.get() == false) {
				r = queues[ queueIndex].pollLast();
				if ( null == r) {
					r = stealWork( queueIndex);
					if ( null == r) {
						// looks like there is no work to steal
						break;
					}
				}
				// If we don't catch RuntimeException,
				// the pool could leak threads
				try {
					r.run();
				} catch ( java.lang.Throwable e) {

				}
				taskDoneW.incrementAndGet();
			}
		}

		@Override
		BlockingQueue<Runnable> getQueue() {
			// wird hier nicht aufgerufen
			return null;
		}
	}

	@Override
	public BlockingQueue<Runnable> createQueue() {
		// nicht augerufen, brauchen hier Deque
		return null;
	}

	@Override
	PoolWorker createPoolWorker( int threadIndex, String queueName) {
		return new PoolWorker( threadIndex % maxQueues, threadIndex, queueName);
	}

	@Override
	String getQueueStatus() {
		StringBuilder sbEmpty = new StringBuilder( "empty: ");
		StringBuilder	sbNotEmpty = new StringBuilder( "filled: ");
		for ( int i = 0;  i < queues.length;  i++) {
			BlockingQueue<Runnable>	q = queues[ i];
			if ( q != null) {
				if ( q.isEmpty()) {
					sbEmpty.append( i + " ");
				} else {
					sbNotEmpty.append( i + " (" + q.size() + ")  ");
				}
			}
		}
		return sbEmpty.toString() + ", " + sbNotEmpty.toString();
	}

	@Override
	protected int size() {
		int	sum = 0;
		for ( int i = 0;  i < queues.length;  i++) {
			BlockingQueue<Runnable>	q = queues[ i];
			if ( q != null) {
				sum += q.size();
			}
		}
		return sum;
	}

	@Override
	int getQueueIndex( Object key) {
		for ( int i = 0; i < queues.length; i++) {
			if ( key == queues[ i]) {
				return i;
			}
		}
		return -1;
	}

}
