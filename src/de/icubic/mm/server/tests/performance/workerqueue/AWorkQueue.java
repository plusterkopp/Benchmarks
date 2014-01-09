package de.icubic.mm.server.tests.performance.workerqueue;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import de.icubic.mm.bench.base.*;


public abstract class AWorkQueue implements IWorkQueue {

	int nThreads;
	APoolWorker[] threads;
	int totalTasks;
	int	maxQueues;

	private AtomicInteger taskDone = new AtomicInteger( 0);
//	protected AtomicInteger taskQueued = new AtomicInteger( 0);

	Object lock = new Object();

	private boolean isBatched;
	private int batchSize = 1000;
	public AtomicInteger batchCount = new AtomicInteger();
	public final AtomicBoolean isStuck = new AtomicBoolean( false);
	private WorkAssignerThread workAssignerThread;

	abstract class APoolWorker extends Thread {

		public APoolWorker( int qIndex, int index, String queueName) {
			super( "Worker-" + queueName + "-Q" + qIndex + "-" + index);
		}

		AtomicInteger taskDoneW = new AtomicInteger( 0);
		AtomicBoolean stopNow = new AtomicBoolean( false);

		abstract BlockingQueue<Runnable> getQueue();

		/* Method to retrieve task from worker queue and start executing it. This thread will wait for a task if there
		 * is no task in the queue. */
		@Override
		public void run() {
			BlockingQueue<Runnable> queue = getQueue();

			if ( isBatched()) {
				runBatched( queue);
				return;
			}

			Runnable r = null;
			while ( stopNow.get() == false) {
				try {
					if ( workAssignerThread.getNumQueued() >= totalTasks) {	// assign finished, no point in waiting for more jobs indefinitely
						r = queue.poll( 100, TimeUnit.SECONDS);
						if ( r == null) {
							BenchLogger.syserr( Thread.currentThread().getName() + ": no more jobs after " + taskDoneW.get());
							return;
						}
					} else {
//						r = queue.take();
						r = queue.poll( 1, TimeUnit.SECONDS);
					}
				} catch ( InterruptedException e1) {
					break;
				}
				try {
					if ( r != null) {
						r.run();
						taskDoneW.incrementAndGet();
					} else {
						int	s = queue.size();
						if ( s > 0)
							BenchLogger.syserr( Thread.currentThread().getName() + ": got no job out of " + s + " in queue");
					}
				} catch ( java.lang.Throwable e) {
					e.printStackTrace();
				}
			}
		}

		private void runBatched( BlockingQueue<Runnable> queue) {
			final int batchSize = getBatchSize();
			Collection<Runnable> runList = new ArrayList<Runnable>( batchSize);

			while ( stopNow.get() == false) {
				try {
					if ( ! queue.isEmpty()) {
						queue.drainTo( runList, batchSize);
						batchCount.incrementAndGet();
						for ( Runnable r: runList) {
							try {
								r.run();
								taskDoneW.incrementAndGet();
							} catch ( java.lang.Throwable e) {
								e.printStackTrace();
							}
						}
						runList.clear();
					} else {
						Runnable r = queue.take();
						try {
							r.run();
							taskDoneW.incrementAndGet();
						} catch ( java.lang.Throwable e) {
							e.printStackTrace();
						}
						batchCount.incrementAndGet();
					}
				} catch ( InterruptedException e1) {
//					System.out.println( Thread.currentThread().getName() + " int " + stopNow.get());
					break;
				}
			}
//			System.out.println( Thread.currentThread().getName() + " closed " + stopNow.get() + ", " + taskDoneW.get() + "t");
		}
	}

	public AWorkQueue( int nThreads, int nQueues, int totalTasks) {
		this( nThreads, nQueues, totalTasks, false);
	}

	public AWorkQueue( int nThreads, int nQueues, int totalTasks, boolean isBatched) {
		this.nThreads = nThreads;
		this.totalTasks = totalTasks;
		this.maxQueues = nQueues;
		this.setBatched( isBatched);
		checkParams();
	}

	private void checkParams() {
		int	numAT = getNumAssignerThreads();
		int	freeAfterAssign = Runtime.getRuntime().availableProcessors() - numAT;
		if ( freeAfterAssign < nThreads) {	// not enough
			int	nt = nThreads - numAT;	// how many threads remain after making space for assigner
			if ( nt >= maxQueues)	{	// if more then maxQueues: OK
				nThreads = nt;
			} else {
				if ( maxQueues > 1) {	// if only one queue, we can handle it
					while ( nt < maxQueues) {
						maxQueues--;
						numAT = getNumAssignerThreads();
						nt = nThreads - numAT;
					}
					nThreads = nt;
				}
			}
		}
	}

	@Override
	public int getNumThreads() {
		return nThreads;
	}

	@Override
	public void startAllThreads( final String id) {
		startPoolWorkers( id);
		Thread watcher = new Thread( "Watcher " + id) {
			@Override
			public void run() {
				StringBuilder sb = new StringBuilder();
				boolean threadsStopped = false;
				long	lastTotal = -1;
				int	stuckRounds = 0;
				int	restartNo = 0;
				while ( ! threadsStopped) {
					int total = 0;
					for ( APoolWorker t : threads) {
						total += t.taskDoneW.get();
					}
					taskDone.set( total);
					if ( total >= totalTasks || ( isStuck.get() && size() == 0)) {
						threadsStopped = true;
						doInterruptAllWaitingThreads();
						sb.append( "closed threads after " + total + " Tasks");
					}
					try {
						if ( total == lastTotal) {
							isStuck.set( true);
							BenchLogger.syserr( Thread.currentThread().getName() + " stuck at " + MainClass.nf.format( total) + " finished tasks, qStat: " + getQueueStatus());
							if ( ++stuckRounds > 5) {
								for ( APoolWorker pw : threads) {
									pw.interrupt();
								}
							}
							if ( ++stuckRounds > 6) {
								totalTasks -= lastTotal;
								startPoolWorkers( id + ++restartNo);
							}
							boolean allRunning = true;
							for ( APoolWorker pw : threads) {
								if ( ! pw.isAlive()) {
									allRunning = false;
								}
							}
							if ( ! allRunning) {
								for ( APoolWorker pw : threads) {
									if ( pw.isAlive()) {
										BenchLogger.syserr( pw.getName() + ": " + Arrays.toString( pw.getStackTrace()));
									}
								}
							}
							Thread.sleep( 10000);
						} else {
							isStuck.set( false);
							Thread.sleep( 100);
						}
						lastTotal = total;
					} catch ( InterruptedException e) {
					}
				}
				try {
					for ( APoolWorker t : threads) {
						t.join();
					}
					threads = null;
				} catch ( InterruptedException e) {
				}
				BenchLogger.sysout( sb.toString() + ", Watcher " + id +  " finished");
			}
		};
		watcher.start();
	}

	protected abstract int size();

	private void startPoolWorkers( String id) {
		threads = new APoolWorker[ nThreads];
		for ( int i = 0; i < nThreads; i++) {
			threads[ i] = createPoolWorker( i, id);
			threads[ i].start();
		}
	}

	abstract APoolWorker createPoolWorker( int index, String queueName);

	public void doInterruptAllWaitingThreads() {
		// Interrupt all the threads
		for ( int i = 0; i < nThreads; i++) {
			APoolWorker t = threads[ i];
			t.stopNow.set( true);
			t.interrupt();
		}
		synchronized ( lock) {
			lock.notifyAll();
			BenchLogger.sysout( "Main notified");
		}
	}

	@Override
	public int stopWhenAllTaskFinished( String id) {
		// wait for threads to complete running the tasks
		synchronized ( lock) {
			try {
				BenchLogger.sysout( "Main waiting for " + id);
				lock.wait();
				BenchLogger.sysout( "Main resuming from " + id);
			} catch ( InterruptedException e) {
				e.printStackTrace();
			}
		}
		return taskDone.get();
	}

	public boolean isBatched() {
		return isBatched;
	}

	public void setBatched( boolean isBatched) {
		this.isBatched = isBatched;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize( int batchSize) {
		this.batchSize = batchSize;
	}

	@Override
	public int getBatchCount() {
		if ( isBatched())
			return batchCount.get();
		return taskDone.get();
	}

	@Override
	public int getNumQueues() {
		return maxQueues;
	}

	@Override
	public int getNumAssignerThreads() {
		int	cores = Runtime.getRuntime().availableProcessors();
		if ( cores > 1) {
			int	nt = getNumQueues();
			nt = Math.min( nt,  cores / 2);
			return nt;
		}
		return 1;
	}

	@Override
	public WorkAssignerThread newAssignerThread( Task[] tasks, long jobsPerSecond) {
		workAssignerThread = new WorkAssignerThread( this, tasks, jobsPerSecond);
		return workAssignerThread;
	}

	abstract String getQueueStatus();

	public String getName() {
		return getClass().getSimpleName();
	}
}