package de.icubic.mm.server.tests.performance.workerqueue;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import de.icubic.mm.bench.base.BenchLogger;
import de.icubic.mm.communication.util.AddThreadBeforeQueuingThreadPoolExecutor;
import de.icubic.mm.server.utils.AffinityThread;
import net.openhft.affinity.AffinityManager;
import net.openhft.affinity.impl.LayoutEntities.LayoutEntity;
import net.openhft.affinity.impl.LayoutEntities.Socket;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DisruptorQueue implements IWorkQueue {

	static NumberFormat nf = MainClass.lnf;

	public class TaskEventFactory implements EventFactory<TaskEvent> {
		@Override
		public TaskEvent newInstance() {
			return new TaskEvent();
		}
	}

	static class TaskEvent {
		Task	task;
		protected int index;
		public TaskEvent() {}
	}

	static class BatchInfo {
		public BatchInfo( long start, long size) {
			this.start = start;
			this.size = size;
		}
		long	start;
		long	size;
		@Override
		public String toString() {
			return "" + start + "_" + nf.format( size);
		}

	}

	static class TaskHandler implements WorkHandler<TaskEvent> , EventHandler<TaskEvent> {
		int completionCount = 0;
		int batchCount = 0;
		List<BatchInfo> batchInfos = new ArrayList<DisruptorQueue.BatchInfo>();

		@Override
		public void onEvent( TaskEvent event, long sequence, boolean endOfBatch) throws Exception {
			// TODO Auto-generated method stub
		}

		@Override
		public void onEvent( TaskEvent event) throws Exception {
			// TODO Auto-generated method stub
		}

		void addBatchInfo( long batchStart, long batchEnd) {
			batchInfos.add( new BatchInfo( batchStart, batchEnd - batchStart + 1));
		}
}
	TaskHandler[]		thArray;

	private RingBuffer<TaskEvent> ringBuffer;
	private int nThreads;
	EventFactory<TaskEvent> eventFactory;
	WorkerPool<TaskEvent> workerPool;
	CountDownLatch	assigningFinished = new CountDownLatch( 1);
	CountDownLatch	workersReady;
	ThreadPoolExecutor executor;
//	Set<Integer> published = new HashSet<Integer>( 1000);

	private Disruptor<TaskEvent> disruptor;

	private boolean useAffinity;

//	public final AtomicInteger taskQueued = new AtomicInteger( 0);

	public DisruptorQueue( int nthreads, int nQueues, int totalTasks, boolean batch, int bufSize) {
		this( nthreads, nQueues, totalTasks, batch, bufSize, false);
	}

	public DisruptorQueue( int nthreads, int nQueues, int totalTasks, boolean batch, int bufSize, boolean useAffinity) {
		this.useAffinity = useAffinity;
		final int numAT = getNumAssignerThreads();
		int	freeAfterAssign = Runtime.getRuntime().availableProcessors() - numAT;
		if ( freeAfterAssign >= nthreads) {	// enough
			nThreads = nthreads;
		} else {
			nThreads = Math.max( 1, nthreads - numAT);
		}
		// if affinity, use only threads of the same node
		int	threadsPerSocket = AffinityThread.getThreadsPerSocket();
		nThreads = Math.min( nThreads, threadsPerSocket);
		workersReady = new CountDownLatch( nThreads);
		createRingBuffer( bufSize, numAT > 1, useAffinity);
		thArray = new TaskHandler[ nThreads];
		if ( batch) {
			setupForBatching();
		} else {
			setup();
		}
	}

	@Override
	public int getNumThreads() {
		return nThreads;
	}

	private void setupForBatching() {
		for( int i = 0;  i < nThreads;  i++) {
			final int	fi = i;
			thArray[ i] = new TaskHandler() {
//				boolean lastEOB = true;
//				boolean atStart = true;
//				long	lastBatchStart = 0;
				@Override
				public void onEvent( TaskEvent te, long sequence, boolean endOfBatch) throws Exception {
					boolean myTurn = ( sequence % nThreads) == fi;
					if ( myTurn) {
						te.task.run();
						completionCount++;
					}
//					if ( atStart) {
//						lastBatchStart = sequence;
//						atStart = false;
//					}
//					if ( lastEOB) {
//						lastBatchStart = sequence;
//					}
					if ( endOfBatch) {
//						addBatchInfo( lastBatchStart, sequence);
						batchCount++;
					}
//					lastEOB = endOfBatch;
				}
			};
		}
		disruptor.handleEventsWith( thArray);
		disruptor.start();
	}

	/**
	 * initialisiert {@link RingBuffer} und {@link WorkerPool}. Startet ihn auch gleich, da sonst {@link TaskEvent}
	 * verlorengehen. {@link WorkerPool#start(Executor)} f�ngt seine Arbeit beim aktuellen Stand des {@link RingBuffer}
	 * an, nicht etwa am Anfang.
	 */
	private void setup() {
		SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
		for( int i = 0;  i < nThreads;  i++) {
			thArray[ i] = new TaskHandler() {
				@Override
				public void onEvent( TaskEvent te) throws Exception {
					Task	task = te.task;
					assert( task != null);
					if ( task != null) {
						task.run();
						completionCount++;
					}
				}
			};
		}
		workerPool = new WorkerPool<TaskEvent>( ringBuffer, sequenceBarrier , new FatalExceptionHandler(), thArray);
		ringBuffer.addGatingSequences( workerPool.getWorkerSequences());
		workerPool.start( executor);
	}

	private void createRingBuffer(int bufSize, boolean isMulti, boolean useAffinity) {
		eventFactory = new TaskEventFactory();
		int	pot = 1;
		while ( pot < bufSize && pot <= ( Integer.MAX_VALUE / 2)) {
			pot *= 2;
		}
		executor = AddThreadBeforeQueuingThreadPoolExecutor.getExecutor( nThreads, "Disruptor", new LinkedBlockingQueue<Runnable>());
		final AtomicInteger threadNumber = new AtomicInteger( 0);
		ThreadFactory	tf;
		if ( useAffinity) {
			tf = new ThreadFactory() {
				@Override
				public Thread newThread( final Runnable r) {
					Socket node = WorkAssignerThread.createdOnSocket;
					Thread t = new AffinityThread( () ->  {
							workersReady.countDown();
							r.run();
						},
					"Disruptor-" + threadNumber.incrementAndGet(), node);
					return t;
				}
			};
		} else {
			tf = new ThreadFactory() {
				@Override
				public Thread newThread( final Runnable r) {
					Thread t = new Thread( () ->  {
							workersReady.countDown();
							r.run();
						},
					"Disruptor-" + threadNumber.incrementAndGet());
					return t;
				}
			};
		}
		executor.setThreadFactory( tf);
		executor.prestartAllCoreThreads();
		disruptor = new Disruptor<TaskEvent>( eventFactory, pot, executor,
				 ( isMulti ? ProducerType.MULTI : ProducerType.SINGLE),
//				ProducerType.MULTI,
				new YieldingWaitStrategy());
		ringBuffer = disruptor.getRingBuffer();
	}

	@Override
	public void startAllThreads( String id) {
	}

	@Override
	public int stopWhenAllTaskFinished( String id) {
		// wait for threads to complete running the tasks
			try {
				BenchLogger.sysinfo( "Assigning tasks finished?");
				assigningFinished.await();
				BenchLogger.sysinfo( "Main waiting for " + id);
				if ( workerPool != null) {
					workerPool.drainAndHalt();
				} else {
					disruptor.shutdown();
				}
				executor.shutdown();
				executor.awaitTermination( 10, TimeUnit.SECONDS);
				disruptor = null;
				ringBuffer = null;
				BenchLogger.sysinfo( "Main resuming from " + id);
//				checkExecution( thArray);
			} catch ( InterruptedException e) {
				BenchLogger.syserr( "", e);
			}
			int completionCount = getCompletionCount();
		return completionCount;
	}

	private int getCompletionCount() {
		int completionCount = 0;
		for ( int i = 0;  i < nThreads;  i++) {
			completionCount += thArray[ i].completionCount;
		}
		return completionCount;
	}

	private void checkExecution( TaskHandler[] ths) {
		int	sum = 0;
//		Set<Integer> all = new HashSet<Integer>();
		StringBuilder sb = new StringBuilder( "Batches per handler: ");
		for ( int i = 0; i < ths.length; i++) {
			TaskHandler handler = ths[ i];
			if ( i > 0) {
				sb.append( ", ");
			}
			sb.append( i + ": " + handler.batchInfos);

			//			all.addAll( taskWorkHandler.completed);
//			final int size = taskWorkHandler.completed.size();
//			sb.append( i + ": " + nf.format( size));
//			sum += size;
		}
		sb.append( ", sum= " + nf.format( sum));
//		sb.append( ", all= " + nf.format( all.size()));
//		published.removeAll( all);
//		sb.append( ", not run: " + new TreeSet<Integer>( published));
		BenchLogger.syserr( sb.toString());
	}

	@Override
	public void execute( Runnable t) {
	}

	@Override
	public BlockingQueue<Runnable> createQueue() {
		return null;
	}

	@Override
	public int getBatchCount() {
		if ( workerPool != null) {
			return getCompletionCount();
		}
		int	batchCount = 0;
		for ( TaskHandler th : thArray) {
			batchCount += th.batchCount;
		}
		return batchCount;
	}

	@Override
	public int getNumQueues() {
		return 1;
	}

	@Override
	public int getNumAssignerThreads() {
		int	cores = AffinityManager.getInstance().getNumSockets() * AffinityThread.getThreadsPerSocket();
		if ( cores > 1) {
			int	nt = getNumQueues();
			nt = Math.min( nt,  cores / 2);
			return nt;
		}
		return 1;
	}

	@Override
	public WorkAssignerThread newAssignerThread( final Task[] tasks, long jobsPerSecond) {
		WorkAssignerThread wat = new WorkAssignerThread( this, tasks, jobsPerSecond) {
			@Override
			protected void assignBlock( int start, int end, int ft, long[] sizes, long jobsPerSecond) throws InterruptedException {
				Thread	t = Thread.currentThread();
				AssignerThread	at = ( AssignerThread) t;
				final boolean latencyRun = isLatencyRun();
				for ( int i = start; i < end; i++) {
					long	next = ringBuffer.next();
					Task task = tasks[ i];
					try {
						TaskEvent te = ringBuffer.get( next);
						te.task = task;
						task.onEnqueue( latencyRun);
						te.index = i;
						sizes[ ft] += task.getSize();
					} finally {
						ringBuffer.publish( next);
					}
					int queued = at.queuedJobCount.incrementAndGet();
					if ( latencyRun) {
						throttle( task.enqueuedAtNano, queued, jobsPerSecond);
					}
				}
			}

			@Override
			public void run() {
				super.run();
				assigningFinished.countDown();
			}

			@Override
			protected int getNumQueues() {
//				return Math.max( Runtime.getRuntime().availableProcessors() / 2, workQ.getNumQueues());
				return 1;
			}

		};
		return wat;
	}

	@Override
	public void waitForWorkersCreated() throws InterruptedException {
		if ( workersReady != null) {
			workersReady.await();
		}
	}

	@Override
	public LayoutEntity getLayoutEntityFor( int threadIndex) {
		return null;
	}

	@Override
	public void createQueue(int threadIndex) {
		// TODO Auto-generated method stub
	}

}
