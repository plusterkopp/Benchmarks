package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.concurrent.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 4, time = 30)
@Fork(1)

@State(Scope.Benchmark)
public class Mandelbrot {

	private static final int SIZE = 500;
	static int BAILOUT = 16;
	static int MAX_ITERATIONS = 1000;

	private int iterate( double x, double y) {
		double cr = y - 0.5f;
		double ci = x;
		double zi = 0.0f;
		double zr = 0.0f;
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
				return 0;
		}
	}

	@Benchmark
	public int runHotspot() {
		int x, y;
		int result = 0;
		for ( y = 1 - SIZE; y < SIZE - 10; y++) {
			for ( x = 1 - SIZE; x < SIZE - 1; x++) {
				result += iterate( x / ( double) SIZE, y / ( double) SIZE);
			}
		}
		return result;
	}

	@Benchmark
	@Fork( jvmArgsPrepend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI", "-XX:+UseJVMCICompiler"})
	public int runGraal() {
		int x, y;
		int result = 0;
		for ( y = 1 - SIZE; y < SIZE - 10; y++) {
			for ( x = 1 - SIZE; x < SIZE - 1; x++) {
				result += iterate( x / ( double) SIZE, y / ( double) SIZE);
			}
		}
		return result;
	}



	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( Mandelbrot.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
