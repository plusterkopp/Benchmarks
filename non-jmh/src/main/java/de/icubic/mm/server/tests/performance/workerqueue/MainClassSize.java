package de.icubic.mm.server.tests.performance.workerqueue;

import java.io.*;
import java.text.*;
import java.util.*;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.server.utils.*;
import net.openhft.affinity.*;
import net.openhft.affinity.impl.*;
import net.openhft.affinity.impl.LayoutEntities.*;

public class MainClassSize {

	public static NumberFormat	lnf = new DecimalFormat();
	public static NumberFormat	dnf = new DecimalFormat();

	public static void main( String[] args) {

		if ( args.length !=5) {
			BenchLogger.sysout( "Incorrect usage");
			BenchLogger.sysout( "Usage -> java MainClass " + "<number of task> <job size in NS> <machine name>");
			System.exit( - 1);
		}

		IAffinity aff = Affinity.getAffinityImpl();
		if ( ! ( aff instanceof IDefaultLayoutAffinity)) {
			BenchLogger.sysout( "Need Layout Affinity");
			System.exit( - 1);
			return;
		}
		IDefaultLayoutAffinity	waff = ( IDefaultLayoutAffinity) aff;
		CpuLayout layout = waff.getDefaultLayout();
		int nThreads = layout.cpus();
		int nSockets = layout.sockets();
		int nThreadsPerSocket = layout.threadsPerCore() * layout.coresPerSocket();
		int totalTasks = Integer.parseInt( args[ 2]);
		long jobSizeNS = Long.parseLong( args[ 3]);
		String	machine = args[4];

		String	outName = "logs" + File.separator + "WorkerQueue-" + nThreads + "T-" + nSockets + "Q-" + totalTasks + "-" + jobSizeNS + "ns";
		DateFormat	df = new SimpleDateFormat( "yyyy-MM-dd");
		outName += "-" + machine + "-" + df.format( new Date());
		BenchLogger.setLogName( outName);

		CSVWriter	writer = new CSVWriter();
		writer.put( "Threads", "" + nThreads);
		writer.put( "Queues", "" + nThreadsPerSocket);
		writer.put( "Jobs", "" + totalTasks);

		dnf.setMaximumFractionDigits( 3);
		lnf.setMaximumFractionDigits( 1);
		lnf.setGroupingUsed( true);

		Task[]	tasks;

		BenchLogger.sysinfo( layout.toString());
		BenchLogger.sysinfo( "Creating " + lnf.format( totalTasks) + " jobs ");
		tasks = WorkAssignerThread.createTasks( totalTasks);
		BenchLogger.sysinfo( "Creating " + lnf.format( totalTasks) + " jobs finished on socket " + WorkAssignerThread.createdOnSocket);
		BenchLogger.sysout( "warmup");
		getRawSpeed( tasks, 5000, 1, 100, false);

		double	jpsMulti = 1;
		double	jpsSocket = 1;
		double	jpsSingle = 1;
		double targetRTS = 1;
		// find speedup for jobs of 10ms
		for ( targetRTS = 1e-6;  targetRTS <= 1.1e-2;  targetRTS *= 10) {
			solveForRuntimeSingle( tasks, targetRTS);
			jpsMulti = getRawSpeed( tasks, 5000, nThreads, 100, false);
			jpsSocket = getRawSpeed( tasks, 5000, nThreadsPerSocket, 100, false);
			jpsSingle = getRawSpeed( tasks, 5000, 1, 100, false);
			BenchLogger.LNF.setMaximumFractionDigits( 3);
			BenchLogger.sysout( "Total Speedup at target run time " + BenchLogger.LNF.format( targetRTS * 1000) + " ms: "
					+ BenchLogger.LNF.format( jpsMulti / jpsSingle) + " (Multi: " + BenchLogger.LNF.format( jpsMulti)
					+ " Single: " + BenchLogger.LNF.format( jpsSingle) + ")");
			BenchLogger.sysout( "Socket Speedup at target run time " + BenchLogger.LNF.format( targetRTS * 1000) + " ms: "
					+ BenchLogger.LNF.format( jpsSocket / jpsSingle) + " (Multi: " + BenchLogger.LNF.format( jpsSocket)
					+ " Single: " + BenchLogger.LNF.format( jpsSingle) + ")");
		}

		// solve for speedup: try to reach 70% of the 100ms Speedup
		double targetSpeedup = 0.9 * jpsMulti / jpsSingle;
		BenchLogger.sysinfo( "computing size for total speedup: " + targetRTS);
		solveForSpeedup( tasks, targetSpeedup, nThreads);
		BenchLogger.sysout( "Total Speedup at target run time " + BenchLogger.LNF.format( targetRTS * 1000) + " ms: "
				+ BenchLogger.LNF.format( jpsMulti / jpsSingle) + " (Multi: " + BenchLogger.LNF.format( jpsMulti)
				+ " Single: " + BenchLogger.LNF.format( jpsSingle) + ")");

		// solve for speedup: try to reach 70% of the 100ms Speedup
		targetSpeedup = 0.9 * jpsSocket / jpsSingle;
		BenchLogger.sysinfo( "computing size for socket speedup: " + targetRTS);
		solveForSpeedup( tasks, targetSpeedup, nThreadsPerSocket);
		BenchLogger.sysout( "Socket Speedup at target run time " + BenchLogger.LNF.format( targetRTS * 1000) + " ms: "
				+ BenchLogger.LNF.format( jpsSocket / jpsSingle) + " (Multi: " + BenchLogger.LNF.format( jpsSocket)
				+ " Single: " + BenchLogger.LNF.format( jpsSingle) + ")");
	}

	private static double solveForRuntimeSingle( Task[] fTasks, double runtimeS) {
		ProblemSizer	ps = new ProblemSizer( fTasks);
		double	result[] = new double[ 1];
		ps.setProblemSize( runtimeS, ( pSize) -> {
			// setup tasks, using pSize as upper bound for random to pseudo random get Matrix Size
			ProblemSizer.fillTasks( fTasks, pSize);
			System.gc();
			int	runtimeMS = ( int) Math.max( 100, ps.getRunTimeMS());
			double singleSpeed = getRawSpeed( fTasks, runtimeMS, 1, ps.getBatchSize(), false);
			result[ 0] = pSize;
			return 1 / singleSpeed;
		});
		return result[ 0];
	}

	private static void solveForSpeedup( Task[] fTasks, double targetSpeedup, int nThreads) {
		ProblemSizer	ps = new ProblemSizer( fTasks);
		ps.setProblemSize( targetSpeedup, ( pSize) -> {
			// setup tasks, using pSize as upper bound for random to pseudo random get Matrix Size
			ProblemSizer.fillTasks( fTasks, pSize);
			System.gc();
			int	runtimeMS = ( int) Math.max( 100, ps.getRunTimeMS());
			double multiSpeed = getRawSpeed( fTasks, runtimeMS, nThreads, ps.getBatchSize(), false);
			if ( multiSpeed < 100) {	// too slow, will probably never reach goal
				BenchLogger.sysout( "abort at " + pSize);
				return targetSpeedup;
			}
			int	batchSize = Math.max( ps.getBatchSize(), ps.getBatchSize() / nThreads);
			double singleSpeed = getRawSpeed( fTasks, runtimeMS, 1, batchSize, false);
			return multiSpeed / singleSpeed;
		});
	}

	private static double getRawSpeed( final Task[] tasks, final int runTimeMS, int nThreads, int batchSizeArg, boolean useOtherNode) {
		final int		width = tasks.length / nThreads;
		final int		batchSize = Math.min( batchSizeArg, width);

		final long[]		runCounts = new long[ nThreads];
		final long[]		opsCounts = new long[ nThreads];
		Thread[]				threads = new Thread[ nThreads];

		long	startTime = System.currentTimeMillis();

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
			Socket  socket = null;
			if ( nThreads <= AffinityThread.getThreadsPerSocket()) {
				if ( useOtherNode && AffinityThread.getNumSockets() > 1) {
					socket = AffinityThread.getOtherSocket( socket);
				} else {
					socket = WorkAssignerThread.createdOnSocket;
				}
			}
			threads[ t] = new AffinityThread( r, "RawSpeed " + t + "/" + nThreads, socket);
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
		BenchLogger.sysinfo( "No-Queue Speed in " + nThreads + " Threads: " + lnf.format( runCount) + " jobs in " + lnf.format( runTimeMS) + "ms, total size " + lnf.format( opsCount / 1e6) + " M in " + lnf.format( durMS) + " ms " +
				"(" + lnf.format( opsCount / durMS) + " ops/ms, " + lnf.format( jobsPerSec) + " jobs/s)");
		return jobsPerSec;
	}
}
