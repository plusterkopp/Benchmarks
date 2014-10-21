package de.icubic.mm.server.tests.performance.workerqueue;

import java.text.*;
import java.util.*;

import org.apache.commons.math3.analysis.*;
import org.apache.commons.math3.analysis.solvers.*;
import org.apache.commons.math3.exception.*;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.communication.util.Utils.MutableInteger;
import de.icubic.mm.communication.util.Utils.MutableLong;

public class ProblemSizer {

	Task[]	tasks;

	public ProblemSizer( Task[] tasks) {
		super();
		this.tasks = tasks;
	}

	private long getRawSpeed( Task[] tasks, int batchSize, double runTimeS) {
		int		startIndex = 0;
		int		endIndex = startIndex + batchSize;
		int		runTimeMS = ( int) ( 1000 * runTimeS);
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
		BenchLogger.sysinfo( "estimate: " + nf.format( runCount) + " jobs in " + nf.format( runTimeMS) + "ms, actual " + nf.format( durMS) + " ms " +
				"(" + nf.format( ops / durMS) + " ops/ms, " + nf.format( jobsPerSec) + " jobs/s, " + nf.format( nsPerJob) + " ns/job");
		return nsPerJob;
	}

	public void setProblemSize( final long avgNS) {
		final MutableLong	mResult = new MutableLong( ( long) 1e9);
		final MutableInteger	batchSize = new MutableInteger( 1000);
		UnivariateFunction	taskSizeToRuntimeNS = new UnivariateFunction() {
			@Override
			public double value( double pSize) {
				// setup tasks, using pSize as upper bound for random to pseudo random get Matrix Size
				fillTasks( tasks, pSize);
				System.gc();
				double runTimeMS;
				if ( Math.abs( mResult.longValue()) > avgNS * 0.8) {
					runTimeMS = 0.1;
				} else if ( Math.abs( mResult.longValue()) > avgNS / 2) {
					runTimeMS = 1;
				} else {
					runTimeMS = 2;
				}
				double nsPerJob = getRawSpeed( tasks, batchSize.getInteger(), runTimeMS);
				mResult.setValue( ( long) ( nsPerJob - avgNS));
				final double result = mResult.doubleValue();
				BenchLogger.sysinfo( "eval for: " + pSize + " -> " + result);
				return result;
			}
		};
		int	maxEval = 30;
		double	upperBound = 1;
		if ( taskSizeToRuntimeNS.value( upperBound) > 0) {	// even with size 1, we are too slow: use 0 then
			taskSizeToRuntimeNS.value( 0);
			return;
		}
		// we can actually iterate
		while ( taskSizeToRuntimeNS.value( upperBound) < 0) {
			upperBound *= 2;
		}
		batchSize.setValue( 10000);
		UnivariateSolver	solver = new RiddersSolver() {
			@Override
			public double getFunctionValueAccuracy() {
				return avgNS * 0.05;
			}
		};
		try {
			BenchLogger.sysinfo( "estimate problem size for " + BenchLogger.lnf.format( avgNS)
					+ " ns, +/- " + BenchLogger.lnf.format( ( long) solver.getFunctionValueAccuracy()));
			solver.solve( maxEval, taskSizeToRuntimeNS, upperBound / 3, upperBound * 1.5, 3 * upperBound / 4);
		} catch ( TooManyEvaluationsException tmee) {
			BenchLogger.sysinfo( "reached eval limit, cancelling estimation");
		}
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
