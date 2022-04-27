package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.concurrent.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 5)
@Measurement(iterations = 4, time = 5)
@Fork(1)

@State(Scope.Benchmark)
public class Mandelbrot {

	@State(Scope.Thread)
	@AuxCounters(AuxCounters.Type.OPERATIONS)
	public static class OpCounters {
		// These fields would be counted as metrics
		public long iterations;
	}


	private static final int SIZE = 500;
	static double BAILOUT = 16;
	static int MAX_ITERATIONS = 1000;

	private int iterateOrig( double x, double y) {
		double cr = y - 0.5;
		double ci = x;
		double zi = 0.0;
		double zr = 0.0;
		int i = 0;
		while ( true) {
			i++;
			double temp = zr * zi;
			double zr2 = zr * zr;
			double zi2 = zi * zi;
			zr = zr2 - zi2 + cr;
			zi = temp + temp + ci;
			if ( zi2 + zr2 > BAILOUT)
				return i;
			if ( i > MAX_ITERATIONS)
				return i;
		}
	}

	private int iterate( final double x, final double y) {
		double cr = y - 0.5;
		double zi = 0.0;
		double zr = 0.0;
		int i = 0;
		while ( true) {
			i++;
			if ( i > MAX_ITERATIONS) {
				return i;
			}
			double zr2 = zr * zr;
			double zi2 = zi * zi;
			if ( zi2 + zr2 > BAILOUT) {
				return i;
			}
			double temp = zr * zi;
			zr = zr2 - zi2 + cr;
			zi = temp + temp + x;
		}
	}

	public void checkMethods( OpCounters ops) {
		boolean abortMe = false;

		ops.iterations = 0;
		long resultOptNoDiv = runOptNoDiv( ops);

		ops.iterations = 0;
		long resultOpt = runOpt( ops);

		if ( resultOptNoDiv != resultOpt) {
			System.err.println( String.format( "mismatch: optNoDiv=%,d opt=%,d", resultOptNoDiv, resultOpt));
			abortMe |= overThreshold( resultOptNoDiv, resultOpt);
		}

		ops.iterations = 0;
		long resultOrigNoDiv = runOrigNoDiv( ops);

		ops.iterations = 0;
		long resultOrig = runOrig( ops);

		if ( resultOrigNoDiv != resultOrig) {
			System.err.println( String.format( "mismatch: origNoDiv=%,d orig=%,d", resultOrigNoDiv, resultOrig));
			abortMe |= overThreshold( resultOrigNoDiv, resultOrig);
		}
		if ( resultOpt != resultOrig) {
			System.err.println( String.format( "mismatch: opt=%,d orig=%,d", resultOpt, resultOrig));
			abortMe |= overThreshold( resultOpt, resultOrig);
		}
		if ( resultOptNoDiv != resultOrigNoDiv) {
			System.err.println( String.format( "mismatch: optNoDiv=%,d origNoDiv=%,d", resultOptNoDiv, resultOrigNoDiv));
			abortMe |= overThreshold( resultOptNoDiv, resultOrigNoDiv);
		}
		if (abortMe) {
			System.exit( 1);
		}
	}

	private boolean overThreshold(long a, long b) {
		long d = a-b;
		return Math.abs( d) > Math.abs( b) * 0.001;
	}

	@Benchmark
	@OperationsPerInvocation( 4 * SIZE*SIZE - 18 * SIZE)
	public long runOptNoDiv( OpCounters ops) {
		double x, y;
		double noDiv = 1.0 / ( double) SIZE;
		int result = 0;
		for (y = 1 - SIZE; y < SIZE - 10; y++) {
			for ( x = 1 - SIZE; x < SIZE - 1; x++) {
				ops.iterations += iterate( x * noDiv, y * noDiv);
			}
		}
		return ops.iterations;
	}

	@Benchmark
	@OperationsPerInvocation( 4 * SIZE*SIZE - 18 * SIZE)
	public long runOpt( OpCounters ops) {
		int x, y;
		int result = 0;
		for ( y = 1 - SIZE; y < SIZE - 10; y++) {
			for ( x = 1 - SIZE; x < SIZE - 1; x++) {
				ops.iterations += iterate( x / ( double) SIZE, y / ( double) SIZE);
			}
		}
		return ops.iterations;
	}

	@Benchmark
	@OperationsPerInvocation( 4 * SIZE*SIZE - 18 * SIZE)
	public long runOrigNoDiv( OpCounters ops) {
		double x, y;
		double noDiv = 1.0 / ( double) SIZE;
		for ( y = 1 - SIZE; y < SIZE - 10; y++) {
			for ( x = 1 - SIZE; x < SIZE - 1; x++) {
				ops.iterations += iterateOrig( x * noDiv, y * noDiv);
			}
		}
		return ops.iterations;
	}

//	@Fork( jvmArgsPrepend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI", "-XX:+UseJVMCICompiler"})

	@Benchmark
	@OperationsPerInvocation( 4 * SIZE*SIZE - 18 * SIZE)
	public long runOrig( OpCounters ops) {
		int x, y;
		for ( y = 1 - SIZE; y < SIZE - 10; y++) {
			for ( x = 1 - SIZE; x < SIZE - 1; x++) {
				ops.iterations += iterateOrig( x / ( double) SIZE, y / ( double) SIZE);
			}
		}
		return ops.iterations;
	}



	public static void main(String[] args) throws RunnerException {
		Mandelbrot m = new Mandelbrot();
		OpCounters ops = new OpCounters();
		m.checkMethods( ops);

        Options opt = new OptionsBuilder()
                .include( Mandelbrot.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
