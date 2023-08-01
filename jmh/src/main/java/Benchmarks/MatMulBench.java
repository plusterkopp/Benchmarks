package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MatMulBench {

	static final int n = 2;
	static final int nOps = 2 * n*n*n - n*n;
	static final int nOps2 = 2 * nOps;
	static double[][] a = new double[n][n];
	static double[][] b = new double[n][n];
	static double[][] c = new double[n][n];

	@State(Scope.Thread)
	@AuxCounters(AuxCounters.Type.OPERATIONS)
	public static class OpCount {
		public long count = 0;
	}

	@Setup(Level.Trial)
	public void setup() {
		Random rand = new Random();

		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				a[i][j] = rand.nextDouble();
				b[i][j] = rand.nextDouble();
				c[i][j] = 0;
	}   }	}

//	@OperationsPerInvocation(nOps)
	@Benchmark
	public long matmul(OpCount opc) {
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				double cc = 0;
				for (int k = 0; k < n; k++) {
					cc += a[i][k] * b[k][j];
					opc.count++;
				}
				c[i][j] = cc;
		}   }
		return opc.count;
	}

	@Benchmark
//	@OperationsPerInvocation(nOps)
	public long matmulFMA(OpCount opc) {
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				double cc = 0;
				for (int k = 0; k < n; k++) {
					cc = Math.fma( a[i][k], b[k][j], cc);
					opc.count++;
				}
				c[i][j] = cc;
			}   }
		return opc.count;
	}

//	@OperationsPerInvocation(nOps2)
	@Benchmark
	public long matmul2(OpCount opc) {
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				double cc = 0;
				for (int k = 0; k < n; k++) {
					cc += a[i][k] * b[k][j];
					opc.count++;
					cc += b[i][k] * a[k][j];
					opc.count++;
				}
				c[i][j] = cc;
		}   }
		return opc.count;
	}

	@Benchmark
//	@OperationsPerInvocation(nOps2)
	public long matmulFMA2(OpCount opc) {
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				double cc = 0;
				for (int k = 0; k < n; k++) {
					cc = Math.fma( a[i][k], b[k][j], cc);
					opc.count++;
					cc = Math.fma( b[i][k], a[k][j], cc);
					opc.count++;
				}
				c[i][j] = cc;
		}   }
		return opc.count;
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( MatMulBench.class.getSimpleName())
		        .mode( Mode.AverageTime)
		        .timeUnit(TimeUnit.NANOSECONDS)
		        .warmupIterations(5)
				.warmupTime( TimeValue.seconds( 2))
		        .measurementIterations(5)
				.measurementTime( TimeValue.seconds( 5))
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
