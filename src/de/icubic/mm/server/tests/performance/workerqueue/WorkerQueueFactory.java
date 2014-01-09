package de.icubic.mm.server.tests.performance.workerqueue;

import java.util.concurrent.*;

import de.icubic.mm.server.utils.*;

public class WorkerQueueFactory {

	static enum EWorkQueueType {
		DIS, B_DIS, B1_DIS, DIS_Bo, // DisruptorBoB,
		LBQ, Multi_LBQ, C_Multi_LBQ,
		// Steal_LBQ, C_Steal_LBQ,
		CLBQ, B_CLBQ, B1_CLBQ, Multi_CLBQ, C_Multi_CLBQ, B_Multi_CLBQ, CB_Multi_CLBQ, ABQ, B_ABQ, B1_ABQ, Multi_ABQ, C_Multi_ABQ, B_Multi_ABQ, CB_Multi_ABQ,
	}

	public static IWorkQueue getWorkQueue( EWorkQueueType type, int nthreads, final int nQueues, final int totalTasks) {
		int nQ1pCore = Runtime.getRuntime().availableProcessors();
		// must not be more than Threads
		nQ1pCore = Math.min( nthreads, nQ1pCore);
		switch ( type) {
		case DIS:
			return new DisruptorQueue( nthreads, nQueues, totalTasks, false, totalTasks);
		case B_DIS:
			return new DisruptorQueue( nthreads, nQueues, totalTasks, true, totalTasks);
		case B1_DIS:
			return new DisruptorQueue( 1, nQueues, totalTasks, true, totalTasks);
		case DIS_Bo:
			return new DisruptorQueue( nthreads, nQueues, totalTasks, false, 100000);
			// case DisruptorBoB:
			// return new DisruptorQueue( nthreads, nQueues, totalTasks, true, 100000);
		case LBQ:
			return new SimpleWorkQueue( nthreads, totalTasks) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedBlockingQueue<Runnable>();
				}
			};
		case Multi_LBQ:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedBlockingQueue<Runnable>();
				}
			};
		case C_Multi_LBQ:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedBlockingQueue<Runnable>();
				}
			};
			// case Steal_LBQ:
			// return new WorkStealingQueue( nthreads, nQueues, totalTasks);
			// case C_Steal_LBQ:
			// if ( nQ1pCore == nQueues)
			// return null;
			// return new WorkStealingQueue( nthreads, nQ1pCore, totalTasks);
		case CLBQ:
			return new SimpleWorkQueue( nthreads, totalTasks) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case Multi_CLBQ:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case C_Multi_CLBQ:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case ABQ:
			return new SimpleWorkQueue( nthreads, totalTasks) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks);
				}
			};
		case Multi_ABQ:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case C_Multi_ABQ:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
			// Batches
		case B_CLBQ:
			return new SimpleWorkQueue( nthreads, totalTasks, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case B1_CLBQ:
			return new SimpleWorkQueue( 1, totalTasks, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case B_Multi_CLBQ:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CB_Multi_CLBQ:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case B_ABQ:
			return new SimpleWorkQueue( nthreads, totalTasks, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks);
				}
			};
		case B1_ABQ:
			return new SimpleWorkQueue( 1, totalTasks, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks);
				}
			};
		case B_Multi_ABQ:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case CB_Multi_ABQ:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};

		default:
			throw new RuntimeException( "incorrect input: " + type);
		}
	}
}

