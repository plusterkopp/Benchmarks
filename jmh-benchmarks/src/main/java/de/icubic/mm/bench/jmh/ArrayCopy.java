package de.icubic.mm.bench.jmh;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

@State(Scope.Benchmark)
public class ArrayCopy {

	private static final int Mega = 1024 * 1024;
	private static final int ArraySize = 512 * Mega;
	private static byte[] y;
	private static byte[] x;

	@Setup(Level.Trial)
	public static void setup() {
		x = new byte[ ArraySize];
		y = new byte[ ArraySize];
	}

	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void byteCopy() {
		for ( int j = ArraySize - 1;  j >= 0; j--) {
			y[ j] = x[ j];
		}
	}


	@Benchmark
	@OperationsPerInvocation(ArraySize)
	public void arrayCopy() {
		System.arraycopy( x, 0, y, 0, x.length);
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( ArrayCopy.class.getSimpleName())
		        .warmupIterations(5)
		        .measurementTime(TimeValue.seconds( 10))
				.measurementIterations( 5)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
