package shipilev;

import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.*;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UseParallelGC", "-Xms1g", "-Xmx1g"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ToArrayBench {

	@Param({"0", "1", "10", "100", "1000"})
	int size;

	@Param({"arraylist", "hashset"})
	String type;

	Collection<Foo> coll;

	@Setup
	public void setup() {
		if (type.equals("arraylist")) {
			coll = new ArrayList<Foo>();
		} else if (type.equals("hashset")) {
			coll = new HashSet<>();
		} else {
			throw new IllegalStateException();
		}
		for (int i = 0; i < size; i++) {
			coll.add(new Foo(i));
		}
	}

	@Benchmark
	public Object[] simple() {
		return coll.toArray();
	}

	@Benchmark
	public Foo[] zero() {
		return coll.toArray(new Foo[0]);
	}

	@Benchmark
	public Foo[] sized() {
		return coll.toArray(new Foo[coll.size()]);
	}

	public static class Foo {
		private int i;

		public Foo(int i) {
			this.i = i;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Foo foo = (Foo) o;
			return i == foo.i;
		}

		@Override
		public int hashCode() {
			return i;
		}
	}
}