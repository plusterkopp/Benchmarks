package de.icubic.mm.server.tests.performance.workerqueue;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import de.icubic.mm.bench.base.BenchLogger;

public abstract class MultiWorkQueue extends AWorkQueue {
	private AtomicInteger queue_no = new AtomicInteger();
	private BlockingQueue<Runnable>[] queues;

//	/* constructor to initiate worker threads and queue associated with it */
//	public MultiWorkQueue( int nThreads, int nQueues, int totalTasks) {
//		this( nThreads, nQueues, totalTasks, false, false);
//	}
//
	/* constructor to initiate worker threads and queue associated with it */
	public MultiWorkQueue( int nThreads, int nQueues, int totalTasks, boolean isBatched, boolean useAffinity) {
		super( nThreads, nQueues, totalTasks, isBatched, useAffinity);

		queues = new BlockingQueue[ maxQueues];
//		for ( int i = 0; i < maxQueues; i++) {
//			queues[ i] = createQueue();
//		}
	}

	/* Executes the given task in the future. Queues the task and notifies the waiting thread. Also it makes the Work
	 * assigner to wait if the queued task reaches to threshold */
	@Override
	public void execute( Runnable r) {
		final int index = ( queue_no.getAndIncrement() % maxQueues);
		queues[ index].offer( r);
//		taskQueued.incrementAndGet();
	}

	/* Worker thread to execute user tasks */
	class PoolWorker extends APoolWorker {

		private int queueIndex;

		PoolWorker( int qi, int index, String queueName) {
			super( qi, index, queueName);
			queueIndex = qi;
		}

		@Override
		BlockingQueue<Runnable> getQueue() {
			return queues[ queueIndex];
		}

	}

	@Override
	public void createQueue(int threadIndex) {
		synchronized (queues) {
			if ( queues[ threadIndex] == null) {
				queues[ threadIndex] = createQueue();
				BenchLogger.sysinfo( Thread.currentThread().getName() + " create queue " + threadIndex);
			}
		}
	}

	@Override
	PoolWorker createPoolWorker( int threadIndex, String queueName) {
		return new PoolWorker( threadIndex % maxQueues, threadIndex, queueName);
	}

	@Override
	public BlockingQueue<Runnable> createQueue() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	String getQueueStatus() {
		StringBuilder sbEmpty = new StringBuilder( "empty: ");
		StringBuilder	sbNotEmpty = new StringBuilder( "filled: ");
		for ( int i = 0;  i < queues.length;  i++) {
			BlockingQueue<Runnable>	q = queues[ i];
			if ( q == null || q.isEmpty()) {
				sbEmpty.append( i + " ");
			} else {
				sbNotEmpty.append( i + " (" + q.size() + ")  ");
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
