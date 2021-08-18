package shipilev;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@State(Scope.Benchmark)
public class AFUBench {
	A a;
	B b;

	@Setup
	public void setup() {
		a = new A();
		b = new B(); // pollute the class hierarchy
	}

	@Benchmark
	public int updater() {
		return a.updater();
	}

	@Benchmark
	public int plain() {
		return a.plain();
	}

	public static class A {
		static final AtomicIntegerFieldUpdater<A> UP
				= AtomicIntegerFieldUpdater.newUpdater(A.class, "v");

		volatile int v;

		public int updater() {
			return UP.get(this);
		}

		public int plain() {
			return v;
		}
	}

	public static class B extends A {}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include( AFUBench.class.getSimpleName())
				.mode( Mode.AverageTime)
				.timeUnit( TimeUnit.NANOSECONDS)
				.warmupIterations(1)
				.measurementTime(TimeValue.seconds( 10))
				.measurementIterations( 4)
				.forks(1)
				.build();
		new Runner(opt).run();
	}

}