package de.icubic.mm.server.tests.performance.workerqueue;

import java.util.concurrent.*;

import de.icubic.mm.server.utils.*;

public class WorkerQueueFactory {

	static enum EWorkQueueType {
		Multi_LBQ_A,
		DIS, DIS_A, B_DIS, B_DIS_A,
		LBQ, LBQ_A, Multi_LBQ, C_Multi_LBQ, C_Multi_LBQ_A,
		B1_DIS, B1_DIS_A, DIS_Bo, DIS_Bo_A,
		// Steal_LBQ, C_Steal_LBQ,
		CLBQ, CLBQ_A, B_CLBQ, B_CLBQ_A, B1_CLBQ, B1_CLBQ_A,
		Multi_CLBQ, Multi_CLBQ_A, C_Multi_CLBQ, C_Multi_CLBQ_A,
		B_Multi_CLBQ, B_Multi_CLBQ_A, CB_Multi_CLBQ, CB_Multi_CLBQ_A,
		ABQ, ABQ_A, B_ABQ, B_ABQ_A, B1_ABQ, B1_ABQ_A, Multi_ABQ, Multi_ABQ_A,
		C_Multi_ABQ, C_Multi_ABQ_A, B_Multi_ABQ, B_Multi_ABQ_A, CB_Multi_ABQ, CB_Multi_ABQ_A,
	}

	public static IWorkQueue getWorkQueue( EWorkQueueType type, int nthreads, final int nQueues, final int totalTasks) {
		int nQ1pCore = Runtime.getRuntime().availableProcessors();
		// must not be more than Threads
		nQ1pCore = Math.min( nthreads, nQ1pCore);
		switch ( type) {
		case DIS:
			return new DisruptorQueue( nthreads, nQueues, totalTasks, false, totalTasks);
		case DIS_A:
			return new DisruptorQueue( nthreads, nQueues, totalTasks, false, totalTasks, true);
		case B_DIS:
			return new DisruptorQueue( nthreads, nQueues, totalTasks, true, totalTasks);
		case B_DIS_A:
			return new DisruptorQueue( nthreads, nQueues, totalTasks, true, totalTasks, true);
		case B1_DIS:
			return new DisruptorQueue( 1, nQueues, totalTasks, true, totalTasks);
		case B1_DIS_A:
			return new DisruptorQueue( 1, nQueues, totalTasks, true, totalTasks, true);
		case DIS_Bo:
			return new DisruptorQueue( nthreads, nQueues, totalTasks, false, 100000);
		case DIS_Bo_A:
			return new DisruptorQueue( nthreads, nQueues, totalTasks, false, 100000, true);
		case LBQ:
			return new SimpleWorkQueue( nthreads, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedBlockingQueue<Runnable>();
				}
			};
		case LBQ_A:
			return new SimpleWorkQueue( nthreads, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedBlockingQueue<Runnable>();
				}
			};
		case Multi_LBQ:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedBlockingQueue<Runnable>();
				}
			};
		case Multi_LBQ_A:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedBlockingQueue<Runnable>();
				}
			};
		case C_Multi_LBQ:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedBlockingQueue<Runnable>();
				}
			};
		case C_Multi_LBQ_A:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, false, true) {
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
			return new SimpleWorkQueue( nthreads, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CLBQ_A:
			return new SimpleWorkQueue( nthreads, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case Multi_CLBQ:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case Multi_CLBQ_A:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case C_Multi_CLBQ:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case C_Multi_CLBQ_A:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case ABQ:
			return new SimpleWorkQueue( nthreads, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks);
				}
			};
		case ABQ_A:
			return new SimpleWorkQueue( nthreads, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks);
				}
			};
		case Multi_ABQ:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case Multi_ABQ_A:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case C_Multi_ABQ:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case C_Multi_ABQ_A:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case B_CLBQ:
			return new SimpleWorkQueue( nthreads, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case B_CLBQ_A:
			return new SimpleWorkQueue( nthreads, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case B1_CLBQ:
			return new SimpleWorkQueue( 1, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case B1_CLBQ_A:
			return new SimpleWorkQueue( 1, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case B_Multi_CLBQ:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case B_Multi_CLBQ_A:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CB_Multi_CLBQ:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CB_Multi_CLBQ_A:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case B_ABQ:
			return new SimpleWorkQueue( nthreads, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks);
				}
			};
		case B_ABQ_A:
			return new SimpleWorkQueue( nthreads, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks);
				}
			};
		case B1_ABQ:
			return new SimpleWorkQueue( 1, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks);
				}
			};
		case B1_ABQ_A:
			return new SimpleWorkQueue( 1, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks);
				}
			};
		case B_Multi_ABQ:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case B_Multi_ABQ_A:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case CB_Multi_ABQ:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case CB_Multi_ABQ_A:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, true, true) {
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

