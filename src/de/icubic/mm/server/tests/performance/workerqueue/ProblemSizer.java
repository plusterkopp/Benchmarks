package de.icubic.mm.server.tests.performance.workerqueue;

import java.text.*;
import java.util.*;

import org.apache.commons.math3.analysis.*;
import org.apache.commons.math3.analysis.solvers.*;

import de.icubic.mm.bench.base.*;

public class ProblemSizer {

	Task[]	tasks;

	public ProblemSizer( Task[] tasks) {
		super();
		this.tasks = tasks;
	}

	private long getRawSpeed( Task[] tasks, int runTimeS) {
		int		startIndex = 0;
		int		batchSize = 1000;
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
		final long nsPerJob = ( long) ( 1e6 * durMS / runCount);
		NumberFormat	nf = BenchLogger.lnf;
		BenchLogger.sysout( "estimate: " + nf.format( runCount) + " jobs in " + nf.format( runTimeMS) + "ms, total size " + nf.format( ops / 1e6) + " M in " + nf.format( durMS) + " ms " +
				"(" + nf.format( ops / durMS) + " ops/ms, " + nf.format( jobsPerSec) + " jobs/s, " + nf.format( nsPerJob) + " ns/job");
		return nsPerJob;
	}

	public void setProblemSize( final long avgNS) {
		UnivariateFunction	taskSizeToRuntimeNS = new UnivariateFunction() {
			@Override
			public double value( double pSize) {
				// setup tasks, using pSize as upper bound for random to pseudo random get Matrix Size
				fillTasks( tasks, pSize);
				System.gc();
				double nsPerJob = getRawSpeed( tasks, 1);
				final double result = nsPerJob - avgNS;
				BenchLogger.sysout( "eval for: " + pSize + " -> " + result);
				return result;
			}
		};
		int	maxEval = 30;
		double	upperBound = 1;
		while ( taskSizeToRuntimeNS.value( upperBound) < 0) {
			upperBound *= 2;
		}
		UnivariateSolver	solver = new BrentSolver();
		solver.solve( maxEval, taskSizeToRuntimeNS, upperBound / 2, upperBound, 3 * upperBound / 4);
	}

	protected void fillTasks( Task[] tasks, double pSize) {
		Random random = new Random( 0);
		int tSize;
		for ( int	i = 0;  i < tasks.length;  i++) {
			double	r = random.nextDouble();
			tSize = ( int) Math.round( pSize * r);
			Task t = new Task( tSize);
			tasks[ i] = t;
		}
	}

}
