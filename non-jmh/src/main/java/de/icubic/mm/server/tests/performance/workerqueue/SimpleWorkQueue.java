package de.icubic.mm.server.tests.performance.workerqueue;

import de.icubic.mm.bench.base.*;

import java.util.concurrent.*;

public abstract class SimpleWorkQueue extends AWorkQueue {
	private BlockingQueue<Runnable> queue;

//	/* constructor to initiate worker threads and queue associated with it */
//	public SimpleWorkQueue( int nThreads, int totalTasks) {
//		this( nThreads, totalTasks, false, false);
//	}
//
	/* constructor to initiate worker threads and queue associated with it */
	public SimpleWorkQueue( int nThreads, int totalTasks, boolean isBatched, boolean useAffinity) {
		super( nThreads, 1, totalTasks, isBatched, useAffinity);
//		queue = createQueue();
		threads = new PoolWorker[ nThreads];
	}

	/* Executes the given task in the future. Queues the task and notifies the waiting thread. Also it makes the Work
	 * assigner to wait if the queued task reaches to threshold */
	@Override
	public void execute( Runnable r) {
		queue.offer( r);
//		taskQueued.incrementAndGet();
	}

	/* Worker thread to execute user tasks */
	private class PoolWorker extends APoolWorker {

		PoolWorker( int index, String queueName) {
			super( 1, index, queueName);
		}

		@Override
		BlockingQueue<Runnable> getQueue() {
			return queue;
		}

	}

	@Override
	public void createQueue(int threadIndex) {
		synchronized ( this) {
			if ( queue == null) {
				queue = createQueue();
				BenchLogger.sysinfo( Thread.currentThread().getName() + " create queue " + threadIndex);
			}
		}
	}

	@Override
	PoolWorker createPoolWorker( int index, String queueName) {
		return new PoolWorker( index, queueName);
	}

	@Override
	String getQueueStatus() {
		BlockingQueue<Runnable>	q = queue;
		if ( q.isEmpty()) {
			return "empty";
		} else {
			return "size: " + q.size();
		}
	}

	@Override
	public BlockingQueue<Runnable> createQueue() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected int size() {
		return queue.size();
	}

	@Override
	int getQueueIndex( Object key) {
		if ( key == queue) {
			return 0;
		}
		return -1;
	}


}
