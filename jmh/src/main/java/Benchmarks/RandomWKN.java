package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class RandomWKN {

	int codeA = 'A';
	int codeZ = 'Z';
	int range = codeZ - codeA + 1;
	StringBuilder   sbResult = new StringBuilder( "123456");
	int len;

	@Setup(Level.Trial)
	public static void setup() {
	}

	@Benchmark
	public void generateSimple() {
		String result = "";
		for ( int i = 0;  i < 3;  i++) {
			double rnd = Math.random();
			int code = (int) Math.round( rnd * range + codeA);
			char c = (char) code;
			result = result + c;
		}
		int digits = (int) (Math.random() * 900 + 100);
		result = result + digits;
		len = result.length();
//		System.out.println( result);
	}

	@Benchmark
	public void generateOpt() {
		sbResult.setLength(0);

		double rnd1 = Math.random();
		int code1 = (int) Math.round( rnd1 * range + codeA);
		char c1 = (char) code1;

		double rnd2 = Math.random();
		int code2 = (int) Math.round( rnd2 * range + codeA);
		char c2 = (char) code2;

		double rnd3 = Math.random();
		int code3 = (int) Math.round( rnd3 * range + codeA);
		char c3 = (char) code3;

		sbResult.append( c1);
		sbResult.append( c2);
		sbResult.append( c3);

		int digits = (int) (Math.random() * 900 + 100);
		sbResult.append( digits);
		len = sbResult.length();
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( RandomWKN.class.getSimpleName())
		        .warmupIterations(2)
				.warmupTime(TimeValue.seconds( 5))
		        .measurementTime(TimeValue.seconds( 10))
				.measurementIterations( 3)
				.timeUnit( TimeUnit.MICROSECONDS)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
