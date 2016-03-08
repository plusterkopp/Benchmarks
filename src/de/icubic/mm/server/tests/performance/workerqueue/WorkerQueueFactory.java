package de.icubic.mm.server.tests.performance.workerqueue;

import java.util.concurrent.*;

import de.icubic.mm.server.utils.*;

public class WorkerQueueFactory {

	static enum EWorkQueueType {
		LTQ, LTQ_A, LTQ_B, LTQ_AB, LTQ_B1, LTQ_AB1,
		LTQ_M, LTQ_AM, LTQ_MC, LTQ_AMC,
		LTQ_BM, LTQ_ABM, LTQ_BMC, LTQ_ABMC,

		DIS, DIS_A, DIS_B, DIS_AB,
		LBQ, LBQ_A, LBQ_M, LBQ_AM, LBQ_MC, LBQ_AMC,
		DIS_B1, DIS_AB1, DIS_Bo, DIS_ABo,
		// Steal_LBQ, C_Steal_LBQ,
		CLBQ, CLBQ_A, CLBQ_B, CLBQ_AB, CLBQ_B1, CLBQ_AB1,
		CLBQ_M, CLBQ_AM, CLBQ_MC, CLBQ_AMC,
		CLBQ_BM, CLBQ_ABM, CLBQ_BMC, CLBQ_ABMC,
		ABQ, ABQ_A, B_ABQ, B_ABQ_A, B1_ABQ, B1_ABQ_A, ABQ_M, ABQ_AM,
		ABQ_MC, ABQ_AMC, ABQ_BM, ABQ_ABM, ABQ_BMC, ABQ_ABMC,
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
		case DIS_B:
			return new DisruptorQueue( nthreads, nQueues, totalTasks, true, totalTasks);
		case DIS_AB:
			return new DisruptorQueue( nthreads, nQueues, totalTasks, true, totalTasks, true);
		case DIS_B1:
			return new DisruptorQueue( 1, nQueues, totalTasks, true, totalTasks);
		case DIS_AB1:
			return new DisruptorQueue( 1, nQueues, totalTasks, true, totalTasks, true);
		case DIS_Bo:
			return new DisruptorQueue( nthreads, nQueues, totalTasks, false, 100000);
		case DIS_ABo:
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
		case LBQ_M:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedBlockingQueue<Runnable>();
				}
			};
		case LBQ_AM:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedBlockingQueue<Runnable>();
				}
			};
		case LBQ_MC:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedBlockingQueue<Runnable>();
				}
			};
		case LBQ_AMC:
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
		case CLBQ_M:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CLBQ_AM:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CLBQ_MC:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CLBQ_AMC:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CLBQ_B:
			return new SimpleWorkQueue( nthreads, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CLBQ_AB:
			return new SimpleWorkQueue( nthreads, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CLBQ_B1:
			return new SimpleWorkQueue( 1, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CLBQ_AB1:
			return new SimpleWorkQueue( 1, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CLBQ_BM:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CLBQ_ABM:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CLBQ_BMC:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case CLBQ_ABMC:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ConcurrentLinkedBlockingQueue<Runnable>();
				}
			};
		case LTQ:
			return new SimpleWorkQueue( nthreads, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedTransferQueue<Runnable>();
				}
			};
		case LTQ_A:
			return new SimpleWorkQueue( nthreads, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedTransferQueue<Runnable>();
				}
			};
		case LTQ_M:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedTransferQueue<Runnable>();
				}
			};
		case LTQ_AM:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedTransferQueue<Runnable>();
				}
			};
		case LTQ_MC:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedTransferQueue<Runnable>();
				}
			};
		case LTQ_AMC:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedTransferQueue<Runnable>();
				}
			};
		case LTQ_B:
			return new SimpleWorkQueue( nthreads, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedTransferQueue<Runnable>();
				}
			};
		case LTQ_AB:
			return new SimpleWorkQueue( nthreads, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedTransferQueue<Runnable>();
				}
			};
		case LTQ_B1:
			return new SimpleWorkQueue( 1, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedTransferQueue<Runnable>();
				}
			};
		case LTQ_AB1:
			return new SimpleWorkQueue( 1, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedTransferQueue<Runnable>();
				}
			};
		case LTQ_BM:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedTransferQueue<Runnable>();
				}
			};
		case LTQ_ABM:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedTransferQueue<Runnable>();
				}
			};
		case LTQ_BMC:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedTransferQueue<Runnable>();
				}
			};
		case LTQ_ABMC:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new LinkedTransferQueue<Runnable>();
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
		case ABQ_M:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case ABQ_AM:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case ABQ_MC:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, false, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case ABQ_AMC:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, false, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
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
		case ABQ_BM:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case ABQ_ABM:
			return new MultiWorkQueue( nthreads, nQueues, totalTasks, true, true) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case ABQ_BMC:
			if ( nQ1pCore == nQueues)
				return null;
			return new MultiWorkQueue( nthreads, nQ1pCore, totalTasks, true, false) {
				@Override
				public BlockingQueue<Runnable> createQueue() {
					return new ArrayBlockingQueue<Runnable>( totalTasks / nQueues);
				}
			};
		case ABQ_ABMC:
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

