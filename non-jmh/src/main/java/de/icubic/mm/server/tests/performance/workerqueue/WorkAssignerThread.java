package de.icubic.mm.server.tests.performance.workerqueue;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.server.utils.*;
import net.openhft.affinity.*;
import net.openhft.affinity.AffinityManager.*;
import net.openhft.affinity.impl.*;
import net.openhft.affinity.impl.LayoutEntities.*;

import java.util.*;
import java.util.concurrent.atomic.*;

import static de.icubic.mm.bench.base.BenchLogger.*;

/*
 * Assigns task to worker queue
 */
public class WorkAssignerThread extends Thread {

	static Socket createdOnSocket;

	protected final IWorkQueue workQ;

	private int totalTasks;

	protected long size = 0;

	private Task[] tasks;
	AssignerThread[]	threads;

	private long jobsPerSecond;

	private long enqueueStartNano;

	class AssignerThread extends AffinityThread {
		public AssignerThread( Runnable runnable, String string, LayoutEntity bindTo) {
			super( runnable, string, bindTo);
		}

		AtomicInteger	queuedJobCount = new AtomicInteger( 0);
	}

	// populate initial capacity
	public WorkAssignerThread( IWorkQueue workQ, Task[] tasks, long jobsPerSecond) {
		this.workQ = workQ;
		this.totalTasks = tasks.length;
		this.tasks = tasks;
		this.jobsPerSecond = jobsPerSecond;
		setName( "Assigner-" + workQ.getClass().getSimpleName() + "-" + tasks.length);
	}

	@Override
	public void run() {
		final int numThreads = workQ.getNumAssignerThreads();
		BenchLogger.sysinfo( "assigning " + totalTasks + " tasks started in " + numThreads + " threads" + ( jobsPerSecond >0 ? " (throttled)" : ""));
		threads = new AssignerThread[ numThreads];
		final long	sizes[] = new long[ numThreads];
		final int	block = totalTasks / numThreads;
		Socket socket = createdOnSocket;
		enqueueStartNano = BenchRunner.getNow();
		for ( int t = 0;  t < numThreads;  t++) {
			final int	threadIndex = t;
			AssignerThread	thread = new AssignerThread( new Runnable() {
				final int	start = block * threadIndex;
				@Override
				public void run() {
					try {
						workQ.createQueue( threadIndex);
						workQ.waitForWorkersCreated();
						assignBlock( start, start+block, threadIndex, sizes, jobsPerSecond / numThreads);
					} catch ( InterruptedException e) {
						BenchLogger.syserr( "", e);
					}
				}
			}, "Assigner-" + t, workQ.getLayoutEntityFor( threadIndex));
			threads[ t] = thread;
			thread.start();
		}
		try {
			boolean allRunning = true;
			while ( allRunning) {
				for ( int t = 0;  allRunning && t < numThreads;  t++) {
					final AssignerThread thread = threads[ t];
					thread.join( 1000);
					allRunning = thread.isAlive();
				}
			}
			int running = numThreads;
			Set<Thread> reported = new HashSet<Thread>();
			while ( running > 0) {
				for ( int t = 0;  t < numThreads;  t++) {
					final AssignerThread thread = threads[ t];
					thread.join( 10000);
					if ( ! thread.isAlive()) {
						running--;
					}
					if ( workQ instanceof AWorkQueue) {
						AWorkQueue awq = ( AWorkQueue) workQ;
						if ( awq.isStuck.get()) {
							if ( ! reported.contains( thread)) {
								BenchLogger.syserr( thread.getName() + ( thread.isAlive() ? " alive at " +Arrays.toString( thread.getStackTrace()) : " dead ")
										+ "with " + LNF.format( block - thread.queuedJobCount.get()) + " jobs to assign "
										+ "while queue stuck");
								if ( ! thread.isAlive()) {
									reported.add( thread);
								}
							}
						}
					}
				}
			}
		} catch ( InterruptedException e) {
			BenchLogger.syserr( "", e);
		}
		long	durNS = BenchRunner.getNow() - enqueueStartNano;
		int	sum = 0;
		size = 0;
		for ( int t = 0;  t < numThreads;  t++) {
			final AssignerThread at = threads[ t];
			sum += at.queuedJobCount.get();
			size += sizes[ t];
		}
		BenchLogger.sysout( "assigning " + sum + " tasks finished " + LNF.format( sum / ( 1e-9 * durNS)) + " jobs/s" + ( jobsPerSecond >0 ? " (throttled)" : ""));
	}

	protected int getNumQueues() {
		return Math.max( 2, workQ.getNumQueues());
	}

	protected void assignBlock( int start, int end, int ft, long[] sizes, long jobsPerSecond) throws InterruptedException {
		Thread	t = Thread.currentThread();
		AssignerThread	at = ( AssignerThread) t;
		final boolean latencyRun = isLatencyRun();
		for ( int i = start; i < end; i++) {
			final Task task = tasks[ i];
			task.onEnqueue( latencyRun);
			workQ.execute( task);
			int	queued = at.queuedJobCount.incrementAndGet();
			sizes[ ft] += task.getSize();
			if ( latencyRun) {
				throttle( task.enqueuedAtNano, queued, jobsPerSecond);
			}
		}
	}

	protected boolean isLatencyRun() {
		return jobsPerSecond > 0;
	}

	protected void throttle( long enqueuedAtNano, int queued, long jobsPerSeond) throws InterruptedException {
		long	timeSinceStartNS = enqueuedAtNano - enqueueStartNano;
		long	maxJobsAllowed = ( long) ( jobsPerSeond * ( timeSinceStartNS / 1e9));
		if ( queued > maxJobsAllowed) {
			double msTooFast = 1000.0 * ( queued - maxJobsAllowed) / jobsPerSeond;
			if ( msTooFast > 0.5) {
				Thread.sleep( 1);
			} else {
				Thread.yield();
			}
		}
	}

	public long getSize() {
		return size;
	}

	public static Task[] createTasks( int total) {
		recordCPU();
		Random random = new Random( 0);
		Task[]	tasks = new Task[ total];
		int tSize;
		for ( int	i = 0;  i < total;  i++) {
			if ( Task.MatrixSize > 0) {
				tSize = Math.round( 1 + random.nextInt( Task.MatrixSize));
			} else {
				tSize = 0;
			}
			Task t = new Task( tSize);
			tasks[ i] = t;
		}
		return tasks;
	}

	private static void recordCPU() {
		createdOnSocket = null;
		IAffinity aff = Affinity.getAffinityImpl();
		if (aff instanceof IDefaultLayoutAffinity) {
			IDefaultLayoutAffinity idl = (IDefaultLayoutAffinity) aff;
			CpuLayout layout = idl.getDefaultLayout();
			if (layout instanceof VanillaCpuLayout) {
				VanillaCpuLayout vl = (VanillaCpuLayout) layout;
				int cpuID = aff.getCpu();
				int socketID = layout.socketId( cpuID);
				createdOnSocket = vl.packages.get( socketID);
			}
		}
	}

	public int getNumQueued() {
		if ( threads != null) {
			int	sum = 0;
			for ( AssignerThread at : threads) {
				if ( at != null) {
					sum += at.queuedJobCount.get();
				}
			}
			return sum;
		}
		return 0;
	}
}
