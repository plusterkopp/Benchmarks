package shipilev;

import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.*;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class Megamorphic {

	static interface I {
		public void m();
	}

	static abstract class A implements I {
		int c1, c2, c3;
	}

	static abstract class B implements I {
		int c4, c5, c6, c7, c8;
	}

	static class C1 extends A {
		public void m() { c1++; }
	}
	static class C2 extends A {
		public void m() { c2++; }
	}
	static class C3 extends A {
		public void m() { c3++; }
	}
	static class C4 extends B {
		public void m() { c4++; }
	}
	static class C5 extends B {
		public void m() { c5++; }
	}
	static class C6 extends B {
		public void m() { c6++; }
	}
	static class C7 extends B {
		public void m() { c7++; }
	}
	static class C8 extends B {
		public void m() { c8++; }
	}

	I[] as;

	@Param({"1", "2", "3", "4", "8"})
	private int implementations;

	@Setup
	public void setup() {
		Random  rnd = new Random(0);
		Class aClass = ( new C1()).getClass();
		String prefix = aClass.getName();
		prefix = prefix.substring( 0, prefix.length() - 2);

		as = new I[300];
		for (int c = 0; c < 300; c++) {
			int r = rnd.nextInt( implementations);
			String clazzName = prefix + "C" + ( 1 + r);
			try {
				Class clazz = Class.forName(clazzName);
				as[c] = (I) clazz.getDeclaredConstructor().newInstance();
			} catch ( Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Benchmark
	@OperationsPerInvocation( 300)
	public void test() {
		for (I a : as) {
			a.m();
		}
	}
}

