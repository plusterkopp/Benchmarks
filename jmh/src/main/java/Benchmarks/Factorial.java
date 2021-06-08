package Benchmarks;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class Factorial {

	final int	arg = 100;
	
	static int	count100outResult = 0;
	static double	factResult = 0;

	private static int count1outResult = 0;
	
	@Setup(Level.Trial)
	public static void setup() {
		count100outResult = 0;
		factResult = 0;
		count1outResult  = 0;
	}

	@Benchmark
	@OperationsPerInvocation(arg)
	public void DoubleFactorial() {
		int	count100out = 0;
		double fact = 1;
		for ( int i = 1;  i <= arg;  i++) {
			fact *= i;
			if ( fact > 1e100) {
				fact /= 1e100;
				count100out++;
			}
		}
		if ( count100outResult == 0) {
			System.out.println( "double fact = " + fact + " * 10^" + ( 100*count100out));
		}
		count100outResult = count100out;
		factResult = fact;		
	}

	@Benchmark
	@OperationsPerInvocation(arg)
	public void ALongFactorial() {
		long limit = Long.MAX_VALUE / arg;
		int	count1out = 0;
		long fact = 1;
		for ( int i = 1;  i <= arg;  i++) {
			while ( fact > 100 && fact % 100 == 0) {
				fact /= 100;
				count1out += 2;
			}
			while (fact > limit) {
				fact /= 10;
				count1out++;
			}
			fact *= i;
//			if ( fact > 100) {
//				while ( fact > 10 && fact % 10 == 0) {
//					fact /= 10;
//					count1out++;
//				}
//			}
		}
		if ( count1outResult == 0) {
			System.out.println( "long fact = " + fact + " * 10^" + ( 1*count1out));
		}
		count1outResult = count1out;
		factResult = fact;
	}

	@Benchmark
	@OperationsPerInvocation(arg)
	public void BigIntFactorial() {
		BigInteger fact = BigInteger.ONE;
		for ( int i = 1;  i <= arg;  i++) {
			fact = fact.multiply( BigInteger.valueOf( i));
		}
		if ( count1outResult == 0) {
			System.out.println( "bigint fact = " + fact);
		}
		count1outResult = 1;
		factResult = fact.doubleValue();
	}

	@Benchmark
	@OperationsPerInvocation(arg)
	public void BigDecFactorial() {
		BigDecimal fact = BigDecimal.ONE;
		for ( int i = 1;  i <= arg;  i++) {
			fact = fact.multiply( BigDecimal.valueOf( i));
		}
		if ( count1outResult == 0) {
			System.out.println( "bigdec fact = " + fact);
		}
		count1outResult = 1;
		factResult = fact.doubleValue();
	}


	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( Factorial.class.getSimpleName())
		        .warmupIterations(4)
				.warmupTime(TimeValue.seconds(5))
		        .measurementTime(TimeValue.seconds( 10))
				.measurementIterations( 1)
				.timeUnit( TimeUnit.MILLISECONDS)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
