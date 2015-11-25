package de.icubic.mm.server.tests.performance.workerqueue;

import java.text.*;
import java.util.*;
import java.util.function.*;

import org.apache.commons.math3.analysis.*;
import org.apache.commons.math3.analysis.solvers.*;
import org.apache.commons.math3.exception.*;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.communication.util.Utils.*;

public class ProblemSizer {

	Task[]	tasks;
	private int batchSize = 1;
	private double runtimeMS = 1000;

	public ProblemSizer( Task[] tasks) {
		super();
		this.tasks = tasks;
	}

	public static long getRawSpeed( Task[] tasks, int batchSize, double runTimeS) {
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
		NumberFormat	nf = BenchLogger.LNF;
		BenchLogger.sysinfo( "estimate: " + nf.format( runCount) + " jobs in " + nf.format( runTimeMS) + "ms, actual " + nf.format( durMS) + " ms " +
				"(" + nf.format( ops / durMS) + " ops/ms, " + nf.format( jobsPerSec) + " jobs/s, " + nf.format( nsPerJob) + " ns/job");
		return nsPerJob;
	}

	public void setProblemSize( final double target, Function<Double, Double> function) {
		final MutableDouble	lastDiff = new MutableDouble( 1e9);
		Map<Double, Double> cache = new HashMap<>();
		batchSize = 100;
		BenchLogger.LNF.setMaximumFractionDigits( 6);
		UnivariateFunction	evalFunction = new UnivariateFunction() {
			@Override
			public double value( double pSize) {
//				System.gc();
				// determine desired runtime (accuracy) by distance from target
				if ( Math.abs( lastDiff.longValue()) > target * 0.8) {
					runtimeMS = 100;
				} else if ( Math.abs( lastDiff.longValue()) > target / 2) {
					runtimeMS = 1000;
				} else {
					runtimeMS = 2000;
				}
				double actual = cache.computeIfAbsent( pSize, function);
				lastDiff.setValue( actual - target);
				final double result = lastDiff.doubleValue();
				BenchLogger.sysinfo( "eval for: " + BenchLogger.LNF.format( pSize) + " -> "
						+ BenchLogger.LNF.format( result) + " (" + BenchLogger.LNF.format( actual) + " of "
						+ BenchLogger.LNF.format( target) + ")");
				return result;
			}
		};
		double	upperBound = 1;
		if ( evalFunction.value( upperBound) > 0) {	// even with size 1, we are too slow: use 0 then
			evalFunction.value( 0);
			return;
		}
		// we can actually iterate
		while ( evalFunction.value( upperBound) < 0) {
			upperBound *= 1.4;
		}
		batchSize = 1000;
		UnivariateSolver	solver = new RiddersSolver() {
			@Override
			public double getFunctionValueAccuracy() {
				return target * 0.05;
			}
		};
		try {
			BenchLogger.LNF.setMaximumFractionDigits( 3);
			BenchLogger.sysinfo( "estimate problem size for " + BenchLogger.LNF.format( target)
					+ ", +/- " + BenchLogger.LNF.format( ( long) solver.getFunctionValueAccuracy()));
			int	maxEval = 30;
			solver.solve( maxEval, evalFunction, upperBound / 3, upperBound * 1.5, 3 * upperBound / 4);
		} catch ( TooManyEvaluationsException tmee) {
			BenchLogger.sysinfo( "reached eval limit, cancelling estimation");
		}
	}

	public static void fillTasks( Task[] tasks, double pSize) {
		Random random = new Random( 0);
		int tSize;
		for ( int	i = 0;  i < tasks.length;  i++) {
			double	r = random.nextDouble();
			tSize = ( int) Math.round( pSize * r);
			Task t = new Task( tSize);
			tasks[ i] = t;
		}
	}

	public double getRunTimeMS() {
		return runtimeMS  ;
	}

	public int getBatchSize() {
		return batchSize;
	}

}
