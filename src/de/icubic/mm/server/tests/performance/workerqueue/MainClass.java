package de.icubic.mm.server.tests.performance.workerqueue;

import java.io.*;
import java.text.*;
import java.util.*;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.server.tests.performance.workerqueue.WorkerQueueFactory.EWorkQueueType;

public class MainClass {

	public static NumberFormat	nf = new DecimalFormat();

	public static void main( String[] args) {

		if ( args.length !=5) {
			System.out.println( "Incorrect usage");
			System.out.println( " Usage -> java MainClass " + "<number of threads> <number of queues> <number of task> <job size in NS> <machine name>");
			System.exit( - 1);
		}

		int nThreads = Integer.parseInt( args[ 0]);
		int nQueues = Integer.parseInt( args[ 1]);
		int totalTasks = Integer.parseInt( args[ 2]);
		long jobSizeNS = Long.parseLong( args[ 3]);
		String	machine = args[4];

		String	outName = "logs" + File.separator + "WorkerQueue-" + nThreads + "T-" + nQueues + "Q-" + totalTasks;
		DateFormat	df = new SimpleDateFormat( "yyyy-MM-dd");
		outName += "-" + machine + "- " + df.format( new Date());
		BenchLogger.setLogName( outName);

		CSVWriter	writer = new CSVWriter();
		writer.put( "Threads", "" + nThreads);
		writer.put( "Queues", "" + nQueues);
		writer.put( "Jobs", "" + totalTasks);

		nf.setMaximumFractionDigits( 1);
		nf.setGroupingUsed( true);

		Task[]	tasks;

		BenchLogger.sysout( "Creating " + nf.format( totalTasks) + " jobs ");
		tasks = WorkAssignerThread.createTasks( totalTasks);
		ProblemSizer	ps = new ProblemSizer( tasks);
		ps.setProblemSize( jobSizeNS);
		long ops = 0;
		for ( Task task : tasks) {
			ops += task.getSize();
		}
		writer.put( "MOps", String.format( Locale.GERMANY, "%e", ops / 1e6));
		writer.put( "Ops/Job", String.format( Locale.GERMANY, "%e", ( double) ops / tasks.length));

		BenchLogger.sysout( "warmup");
		getRawSpeed( tasks, 5);
		BenchLogger.sysout( "estimate single thread");
		double singleSpeed = getRawSpeed( tasks, 5);
		writer.put( "Single", String.format( Locale.GERMANY, "%e", singleSpeed));
		StatsUtils	statsUtils = new StatsUtils();
		double speed;
		// throughput run
		for ( EWorkQueueType type : EWorkQueueType.values()) {	//  Arrays.asList( EWorkQueueType.Disruptor, EWorkQueueType.DisruptorB)
			System.gc();
			speed = run( type, nThreads, nQueues, tasks, 0);
			writer.put( type.toString(), String.format( Locale.GERMANY, "%e", speed));
		}
		Task.Matrix_size = 0;
		tasks = WorkAssignerThread.createTasks( totalTasks);
		for ( EWorkQueueType type : EWorkQueueType.values()) {	//  Arrays.asList( EWorkQueueType.Disruptor, EWorkQueueType.DisruptorB)
			System.gc();
			// for latency run, limit jobs per second…
			final long maxJobsPerSec = 1000000; // Math.min( 1000000, ( long) singleSpeed);
			speed = run( type, nThreads, nQueues, tasks, maxJobsPerSec);
			writer.put( type.toString(), String.format( Locale.GERMANY, "%e", speed));
			// …and print latency stats
			statsUtils.computeStatsFor( tasks);
			BenchLogger.sysout( statsUtils.asString());
		}
		BenchLogger.sysout( "\n" + writer.asString());
	}

	private static double getRawSpeed( Task[] tasks, int runTimeS) {
		int		startIndex = 0;
		int		batchSize = 10000;
		int		endIndex = startIndex + batchSize;
		int		runTimeMS = 1000 * runTimeS;
		long	startTime = System.currentTimeMillis();
		long	runUntil = startTime + runTimeMS;
		long	ops = 0;
		long	now;
		long	runCount = 0;
		while ( ( now = System.currentTimeMillis()) < runUntil) {
			for ( int i = startIndex;  i < endIndex;  i++) {
				final Task task = tasks[ i];
				task.run();
				ops += task.getSize();
			}
			startIndex = endIndex;
			endIndex += batchSize;
			if ( endIndex > tasks.length) {
				startIndex = 0;
				endIndex = startIndex + batchSize;
			}
			runCount += batchSize;
		}
		long	durMS = now - startTime;
		final double jobsPerSec = 1000.0 * runCount / durMS;
		BenchLogger.sysout( "Single Thread Speed: " + nf.format( runCount) + " jobs in " + nf.format( runTimeMS) + "ms, total size " + nf.format( ops / 1e6) + " M in " + nf.format( durMS) + " ms " +
				"(" + nf.format( ops / durMS) + " ops/ms, " + nf.format( jobsPerSec) + " jobs/s");
		return jobsPerSec;
	}

	private static double run( EWorkQueueType type, int nThreads, int nQueues, Task[] tasks, long assignJobsPerSec) {
		boolean throttledForLatency = ( assignJobsPerSec > 0);
		BenchLogger.sysout( "building " + type
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
		String	id = "" + type + " - " + workQueue.getNumThreads() + "T - " + workQueue.getNumQueues() + "Q";
		workQueue.startAllThreads( id);

		long tasksDone = workQueue.stopWhenAllTaskFinished( id);
		int batchesDone = workQueue.getBatchCount();

		// sp�ter joinen, damit wir nicht zu fr�h zum wait am Lock der WorkQueue laufen
		try {
			workAssigner.join();
//			BenchLogger.sysout( "Assigner " + id + " finished");
		} catch ( InterruptedException e) {
			e.printStackTrace();
		}
		long totalSize = workAssigner.getSize();

		long endTime = System.currentTimeMillis();
		long durMS = endTime - startTime;
		StringBuilder	sb = new StringBuilder();
		sb.append( type.name() + ( throttledForLatency ? " (throttled)" : "") + ": " + nf.format( tasksDone) + " jobs,  ");
		if ( tasksDone != batchesDone) {
			sb.append( "in " + nf.format( batchesDone) + " batches, (" + nf.format( ( double) tasksDone / batchesDone) + " t/b), ");
		}
		final double completedJobsPerSec = 1000.0 * tasksDone / durMS;
		BenchLogger.sysout( sb.toString()  + "total size: " + nf.format( totalSize / 1e6) + "M " +
				"in " + durMS + " ms " +
				"(" + nf.format( totalSize / durMS) + " ops/ms, " +
				nf.format( completedJobsPerSec) + " jobs/s");
		return completedJobsPerSec;
	}
}
