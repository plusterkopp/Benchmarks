package de.icubic.mm.server.tests.performance.workerqueue;

import java.io.*;
import java.text.*;
import java.util.*;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.server.tests.performance.workerqueue.WorkerQueueFactory.*;
import de.icubic.mm.server.utils.*;
import net.openhft.affinity.*;
import net.openhft.affinity.impl.*;

public class MainClassAff {

	public static NumberFormat	lnf = new DecimalFormat();
	public static NumberFormat	dnf = new DecimalFormat();
	private static double secsPerJobNSpeed;
	private static double secsPerJobNHalfSpeed;
	private static double secsPerJobSocketSpeed;
	private static double secsPerJobNMinusOneSpeed;
	private static double singleSpeed;

	public static void main( String[] args) {

		if ( args.length !=5) {
			BenchLogger.sysout( "Incorrect usage");
			BenchLogger.sysout( "Usage -> java MainClass " + "<number of task> <job size in NS> <machine name>");
			System.exit( - 1);
		}

		IAffinity aff = Affinity.getAffinityImpl();
		if ( ! ( aff instanceof WindowsJNAAffinity)) {
			BenchLogger.sysout( "Need Windows Affinity");
			System.exit( - 1);
			return;
		}
		WindowsJNAAffinity	waff = ( WindowsJNAAffinity) aff;
		WindowsCpuLayout layout = ( WindowsCpuLayout) waff.getDefaultLayout();
		int nThreads = layout.cpus();
		int nQueues = layout.sockets();
		int totalTasks = Integer.parseInt( args[ 2]);
		long jobSizeNS = Long.parseLong( args[ 3]);
		String	machine = args[4];

		String	outName = "logs" + File.separator + "WorkerQueue-" + nThreads + "T-" + nQueues + "Q-" + totalTasks + "-" + jobSizeNS + "ns";
		DateFormat	df = new SimpleDateFormat( "yyyy-MM-dd");
		outName += "-" + machine + "-" + df.format( new Date());
		BenchLogger.setLogName( outName);

		CSVWriter	writer = new CSVWriter();
		writer.put( "Threads", "" + nThreads);
		writer.put( "Queues", "" + nQueues);
		writer.put( "Jobs", "" + totalTasks);

		dnf.setMaximumFractionDigits( 3);
		lnf.setMaximumFractionDigits( 1);
		lnf.setGroupingUsed( true);

		Task[]	tasks;

		BenchLogger.sysinfo( AffinityLock.dumpLocks());
		BenchLogger.sysinfo( "Creating " + lnf.format( totalTasks) + " jobs ");
		tasks = WorkAssignerThread.createTasks( totalTasks);
		BenchLogger.sysinfo( "Creating " + lnf.format( totalTasks) + " jobs finished on node " + WorkAssignerThread.createdOnNode);
		BenchLogger.sysout( "warmup");
		getRawSpeed( tasks, 5, 1, 100, false);

		ProblemSizer	ps = new ProblemSizer( tasks);
		ps.setProblemSize( jobSizeNS);
		long ops = 0;
		for ( Task task : tasks) {
			ops += task.getSize();
		}
		writer.put( "MOps", String.format( Locale.GERMANY, "%e", ops / 1e6));
		writer.put( "Ops/Job", String.format( Locale.GERMANY, "%e", ( double) ops / tasks.length));

		BenchLogger.sysinfo( "warmup 2");
		getRawSpeed( tasks, 20, 1, 1000, false);
		BenchLogger.sysout( "estimate single thread");
		singleSpeed = getRawSpeed( tasks, 10, 1, 10000, false);
		double nSpeed = getRawSpeed( tasks, 10, nThreads, 10000, false);
		secsPerJobNSpeed = 1 / nSpeed;
		BenchLogger.sysout( "Max Speedup: " + dnf.format( nSpeed / singleSpeed));
		double nSocketSpeed = getRawSpeed( tasks, 10, AffinityThread.getThreadsPerSocket(), 10000, false);
		secsPerJobSocketSpeed = 1 / nSocketSpeed;
		BenchLogger.sysout( "Node Speedup: " + dnf.format( nSocketSpeed / singleSpeed));
		double nOtherSocketSpeed = getRawSpeed( tasks, 10, AffinityThread.getThreadsPerSocket(), 10000, true);
		BenchLogger.sysout( "Other Node Speedup: " + dnf.format( nOtherSocketSpeed / singleSpeed));
		if ( AffinityThread.getThreadsPerCore() > 1) {
			double nHalfSpeed = getRawSpeed( tasks, 10, nThreads / 2, 10000, false);
			secsPerJobNHalfSpeed = 1 / nHalfSpeed;
			BenchLogger.sysout( "Half Speedup: " + dnf.format( nHalfSpeed / singleSpeed));
		}
		double nMinusOneSpeed = getRawSpeed( tasks, 10, nThreads - 1, 10000, false);
		secsPerJobNMinusOneSpeed = 1 / nMinusOneSpeed;
		BenchLogger.sysout( "MinusOne Speedup: " + dnf.format( nMinusOneSpeed / singleSpeed));
		writer.put( "Single", String.format( Locale.GERMANY, "%e", singleSpeed));
		StatsUtils	statsUtils = new StatsUtils();
		double speed;
		// throughput run
		for ( EWorkQueueType type : EWorkQueueType.values()) {	//  Arrays.asList( EWorkQueueType.Disruptor, EWorkQueueType.DisruptorB)
			System.gc();
			speed = run( type, nThreads, nQueues, tasks, 0);
			writer.put( type.toString(), String.format( Locale.GERMANY, "%e", speed));
		}
		Task.MatrixSize = 0;
		tasks = WorkAssignerThread.createTasks( totalTasks);
		for ( EWorkQueueType type : EWorkQueueType.values()) {	//  Arrays.asList( EWorkQueueType.Disruptor, EWorkQueueType.DisruptorB)
			System.gc();
			// for latency run, limit jobs per second…
			final long maxJobsPerSec = 1000000; // Math.min( 1000000, ( long) singleSpeed);
			speed = run( type, nThreads, nQueues, tasks, maxJobsPerSec);
			BenchLogger.sysinfo( type.name() + " ");
			writer.put( type.toString(), String.format( Locale.GERMANY, "%e", speed));
			// …and print latency stats
			statsUtils.computeStatsFor( tasks);
			BenchLogger.sysout( statsUtils.asString());
		}
		BenchLogger.sysout( "\n" + writer.asString());
	}

	private static double getRawSpeed( final Task[] tasks, final int runTimeS, int nThreads, int batchSizeArg, boolean useOtherNode) {
		final int		width = tasks.length / nThreads;
		final int		batchSize = Math.min( batchSizeArg, width);

		final long[]		runCounts = new long[ nThreads];
		final long[]		opsCounts = new long[ nThreads];
		Thread[]				threads = new Thread[ nThreads];

		long	startTime = System.currentTimeMillis();
		final int		runTimeMS = 1000 * runTimeS;

		for ( int t = 0;  t < nThreads; t++) {
			final int lowerBound = width * t;
			final int upperBound = lowerBound + width;
			final int tF = t;
			Runnable r = new Runnable() {
				@Override
				public void run() {
					long	startTimeT = System.currentTimeMillis();
					final long	runUntil = startTimeT + runTimeMS;

					int		startIndex = lowerBound;
					int		endIndex = startIndex + batchSize;
					opsCounts[ tF] = 0;
					while ( System.currentTimeMillis() < runUntil) {
						for ( int i = startIndex;  i < endIndex;  i++) {
							final Task task = tasks[ i];
							task.run();
							opsCounts[ tF] += task.getSize();
						}
						startIndex = endIndex;
						endIndex += batchSize;
						if ( endIndex > upperBound) {
							startIndex = lowerBound;
							endIndex = startIndex + batchSize;
						}
						runCounts[ tF] += batchSize;
					}
				}
			};
			AffinityManager.NumaNode node = null;
			if ( nThreads <= AffinityThread.getNumThreadsOnNode( WorkAssignerThread.createdOnNode)) {
				if ( useOtherNode && AffinityThread.getNumNodes() > 1) {
					node = AffinityThread.getOtherNode( node);
				} else {
					node = WorkAssignerThread.createdOnNode;
				}
			}
			threads[ t] = new AffinityThread( r, "RawSpeed " + t + "/" + nThreads, node);
			threads[ t].start();
		}
		long	runCount = 0;
		long	opsCount = 0;
		for ( int t = 0;  t < nThreads;  t++) {
			try {
				threads[ t].join();
			} catch ( InterruptedException e) {
			}
			opsCount += opsCounts[ t];
			runCount += runCounts[ t];
		}
		long durMS = System.currentTimeMillis() - startTime;
		final double jobsPerSec = 1000.0 * runCount / durMS;
		BenchLogger.sysout( "No-Queue Speed in " + nThreads + " Threads: " + lnf.format( runCount) + " jobs in " + lnf.format( runTimeMS) + "ms, total size " + lnf.format( opsCount / 1e6) + " M in " + lnf.format( durMS) + " ms " +
				"(" + lnf.format( opsCount / durMS) + " ops/ms, " + lnf.format( jobsPerSec) + " jobs/s");
		return jobsPerSec;
	}

	private static double run( EWorkQueueType type, int nThreads, int nQueues, Task[] tasks, long assignJobsPerSec) {
		boolean throttledForLatency = ( assignJobsPerSec > 0);
		BenchLogger.sysinfo( "building " + type
				+ "-" + nThreads + "T-"
				+ nQueues + "Q"
				+ ( throttledForLatency ? " (throttled)" : ""));
		// Get Worker Queue based on users choice
		IWorkQueue workQueue = WorkerQueueFactory.getWorkQueue( type, nThreads, nQueues, tasks.length);
		if ( workQueue == null)
			return 0;
		// Start the work assigner thread
		WorkAssignerThread workAssigner = workQueue.newAssignerThread( tasks, assignJobsPerSec);

		// Populate the task into worker queue
		workAssigner.start();

		long startTime = System.currentTimeMillis();
		final int numThreadsActual = workQueue.getNumThreads();
		String	id = "" + type + " - " + numThreadsActual + "T - " + workQueue.getNumQueues() + "Q";
		try {
			workQueue.startAllThreads( id);
			BenchLogger.sysinfo( "bound: " + AffinityThread.getBoundLocations());
		} catch ( InterruptedException ie) {
			BenchLogger.syserr( "startAllThreads interrupted", ie);
		}

		long tasksDone = workQueue.stopWhenAllTaskFinished( id);
		int batchesDone = workQueue.getBatchCount();

		// später joinen, damit wir nicht zu früh zum wait am Lock der WorkQueue laufen
		try {
			workAssigner.join();
//			BenchLogger.sysout( "Assigner " + id + " finished");
		} catch ( InterruptedException e) {
			BenchLogger.syserr( "", e);
		}
		long totalSize = workAssigner.getSize();

		long endTime = System.currentTimeMillis();
		long durMS = endTime - startTime;
		StringBuilder	sb = new StringBuilder();
		sb.append( type.name() + ( throttledForLatency ? " (throttled)" : "") + ": " + lnf.format( tasksDone) + " jobs,  ");
		if ( tasksDone != batchesDone) {
			sb.append( "in " + lnf.format( batchesDone) + " batches, (" + lnf.format( ( double) tasksDone / batchesDone) + " t/b), ");
		}
		final double completedJobsPerSec = 1000.0 * tasksDone / durMS;
		sb.append( "total size: " + lnf.format( totalSize / 1e6) + "M " +
				"in " + lnf.format( durMS) + " ms " +
				"(" + lnf.format( totalSize / durMS) + " ops/ms, " +
				lnf.format( completedJobsPerSec) + " jobs/s, ");
		if ( ! throttledForLatency) {
			double secsPerJobExpected;
			if ( numThreadsActual == nThreads) {
				secsPerJobExpected = secsPerJobNSpeed;
			} else if ( numThreadsActual == AffinityThread.getThreadsPerSocket()) {
				secsPerJobExpected = secsPerJobSocketSpeed;
			} else if ( numThreadsActual == nThreads / 2) {
				secsPerJobExpected = secsPerJobNHalfSpeed;
			} else if ( numThreadsActual == nThreads - 1) {
				secsPerJobExpected = secsPerJobNMinusOneSpeed;
			} else if ( numThreadsActual == 1) {
				secsPerJobExpected = 1.0 / singleSpeed;
			} else {
				final double threadFactor = ( double) workQueue.getNumThreads() / nThreads;	// adjust for using less threads than planned
				secsPerJobExpected = secsPerJobNSpeed * threadFactor;
			}
			double	secPerJob = 1 / completedJobsPerSec;
			final double overheadSecs = secPerJob - secsPerJobExpected;
			double	overheadRel = overheadSecs / secPerJob;
				sb.append( "speedup " + dnf.format( completedJobsPerSec / singleSpeed)
						+ ", "  +dnf.format( overheadSecs * 1e9) + " ns (" + dnf.format( 100.0 * overheadRel) + "%) overhead, "
						+ dnf.format( 100.0 * completedJobsPerSec * secsPerJobExpected) + "% of max perf");
		}
		BenchLogger.sysout( sb.toString());
		return completedJobsPerSec;
	}
}
