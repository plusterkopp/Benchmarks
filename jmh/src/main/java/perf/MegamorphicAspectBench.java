package perf;


import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.lang.reflect.Constructor;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Vergleicht verschiedene Arten der Code-Verzweigung:
 * <ul>
 * <li>Polymorphie, </li>
 * <li>Enum, </li>
 * <li>switch/case über ein Enum, </li>
 * <li>if/else über symbolische Namen, </li>
 * <li>Suche in Enum-Values</li>
 * </ul>
 * Wir nennen die verschiedenen Fälle A1-A5.
 * Wir bauen dazu eine große Menge vorgefertigter Objekte, die so zusammengesetzt sind, daß jedes für
 * jede der Arten den gleichen Wert liefert. Also ein Objekt ({@link MiniAspect}) stellt die o.g. Aufruf-Arten bereit, und die
 * sind dann für dieses Objekt immer z.B. A1.
 * Für jeder der 5 Arten gibt es einen Benchmark, der ruft dann die Werte aller MiniAspects ab.
 * Wir stellen bis zu 5 Ausprägungen, Fälle, Implementationen A1-5 bereit, aber steuern über einen JMH-Parameter, wieviele davon in einem Lauf
 * tatsächlich vorkommen. Es gibt also einen Lauf, der nur A1 enthält, einen mit A1 und A2, etc.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class MegamorphicAspectBench {
	/**
	 * Enum, dessen Elemente die eine Methode unterschiedlich implementieren
	 */
	private static enum EMiniAspectType {
		A1 {
			@Override
			public int getInt(AspectSource src) {
				return src.getA1();
			}
		}, A2 {
			@Override
			public int getInt(AspectSource src) {
				return src.getA2();
			}
		}, A3 {
			@Override
			public int getInt(AspectSource src) {
				return src.getA3();
			}
		}, A4 {
			@Override
			public int getInt(AspectSource src) {
				return src.getA4();
			}
		}, A5 {
			public int getInt(AspectSource src) {
				return src.getA5();
			}
		};

		abstract public int getInt(AspectSource src);
	}

	/**
	 * bündelt die Datenquelle {@link AspectSource} und die Aufrufwege (über Name, Enum, etc)
	 */
	private static abstract class MiniAspect {
		final AspectSource source;
		final EMiniAspectType type;
		final String name;

		protected MiniAspect(AspectSource src, EMiniAspectType aType, String aName) {
			source = src;
			type = aType;
			name = aName;
		}

		/**
		 * wird in {@link MiniAspectA1} etc implementiert
		 * @return eine der getA* von {@link AspectSource}
		 */
		abstract public int getInt();

		public String toString() {
			return "" + source + " - " + type + "/" + name;
		}

		/**
		 * ruft per switch/case anhand von {@link #type} die entsprechende Methode in {@link AspectSource} auf
		 */
		public int getIntByType() {
			switch (type) {
				case A1:
					return source.getA1();
				case A2:
					return source.getA2();
				case A3:
					return source.getA3();
				case A4:
					return source.getA4();
				case A5:
					return source.getA5();
			}
			throw new IllegalStateException( "switch label missing in " + this);
		}

		/**
		 * ruft die {@link EMiniAspectType#getInt(AspectSource)} von {@link #type} auf
		 */
		public int getIntByTypeMethod() {
			return type.getInt(source);
		}

		/**
		 * probiert mit if/else die Namen durch, um den richtigen getA* Aufruf von {@link AspectSource} zu finden
		 */
		public int getIntByName() {
			if (name.equals(EMiniAspectType.A1.name())) {
				return source.getA1();
			}
			if (name.equals(EMiniAspectType.A2.name())) {
				return source.getA2();
			}
			if (name.equals(EMiniAspectType.A3.name())) {
				return source.getA3();
			}
			if (name.equals(EMiniAspectType.A4.name())) {
				return source.getA4();
			}
			if (name.equals(EMiniAspectType.A5.name())) {
				return source.getA5();
			}
			throw new IllegalStateException( "unknown name in " + this);
		}

		/**
		 * probiert mit for-Loop die Namen der {@link EMiniAspectType} durch, um den richtigen {@link EMiniAspectType}
		 * für den Aufruf von {@link EMiniAspectType#getInt(AspectSource)} zu finden
		 */
		public int getIntByTypeName() {
			for ( EMiniAspectType t : EMiniAspectType.values()) {
				if ( name.equals( t.name())) {
					return t.getInt( source);
				}
			}
			throw new IllegalStateException( "unknown name in " + this);
		}
	}

	private static class AspectSource {
		final int a1;
		final int a2;
		final int a3;
		final int a4;
		final int a5;

		private AspectSource(int b1, int b2, int b3, int b4, int b5) {
			a1 = b1;
			a2 = b2;
			a3 = b3;
			a4 = b4;
			a5 = b5;
		}

		public int getA1() {
			return a1;
		}

		public int getA2() {
			return a2;
		}

		public int getA3() {
			return a3;
		}

		public int getA4() {
			return a4;
		}

		public int getA5() {
			return a5;
		}

		public String toString() {
			return "" + a1 + "_" + a2 + "_" + a3 + "_" + a4 + "_" + a5;
		}
	}

	private static class MiniAspectA1 extends MiniAspect {
		protected MiniAspectA1(AspectSource src, EMiniAspectType aType, String aName) {
			super(src, aType, aName);
		}

		@Override
		public int getInt() {
			return source.getA1();
		}
	}

	private static class MiniAspectA2 extends MiniAspect {
		protected MiniAspectA2(AspectSource src, EMiniAspectType aType, String aName) {
			super(src, aType, aName);
		}

		@Override
		public int getInt() {
			return source.getA2();
		}
	}

	private static class MiniAspectA3 extends MiniAspect {
		protected MiniAspectA3(AspectSource src, EMiniAspectType aType, String aName) {
			super(src, aType, aName);
		}

		@Override
		public int getInt() {
			return source.getA3();
		}
	}

	private static class MiniAspectA4 extends MiniAspect {
		protected MiniAspectA4(AspectSource src, EMiniAspectType aType, String aName) {
			super(src, aType, aName);
		}

		@Override
		public int getInt() {
			return source.getA4();
		}
	}

	private static class MiniAspectA5 extends MiniAspect {
		protected MiniAspectA5(AspectSource src, EMiniAspectType aType, String aName) {
			super(src, aType, aName);
		}

		@Override
		public int getInt() {
			return source.getA5();
		}
	}

	@Param({"1", "2", "3", "5"})
	private int implementations;

	private MiniAspect[] miniAspects;


	/**
	 * baut massig {@link MiniAspect}-Instanzen, deren Felder {@link MiniAspect#name}, {@link MiniAspect#type} und
	 * {@link MiniAspect#source} verschieden, aber in sich konsistent belegt sind. Das heißt, wenn ein MiniAspect der
	 * Unterklasse {@link MiniAspectA1} erzeugt wird, hat der auch als type A1, als name A1, etc.
	 * Schauen dann auch noch, ob alles richtig zusammenpaßt, also ob die verschiedenen Arten der A*-Aufrufe eines MiniAspects auch immer die gleichen
	 * Werte liefern. Am Ende haben wir in {@link #miniAspects} ein Array mit zufällig belegten Abrufmethoden.
	 */
	@Setup
	public void setup() {
		Random rnd = new Random(0);
		AspectSource src = new AspectSource(1, 2, 3, 4, 5);
		Class aClass = (new MiniAspectA1(src, EMiniAspectType.A1, "A1")).getClass();
		String prefix = aClass.getName();
		prefix = prefix.substring(0, prefix.length() - 2);

		int numElements = 300;
		miniAspects = new MegamorphicAspectBench.MiniAspect[numElements];
		for (int c = 0; c < numElements; c++) {
			int r = rnd.nextInt(implementations);
			String clazzName = prefix + "A" + (1 + r);
			String enumName = "A" + (1 + r);
			try {
				Class clazz = Class.forName(clazzName);
				EMiniAspectType aType = EMiniAspectType.valueOf(enumName);
				int c5 = c * 5;
				src = new AspectSource( c5 + 1, c5 + 2, c5 + 3, c5 + 4, c5 + 5);
				Constructor declaredConstructor = clazz.getDeclaredConstructor(AspectSource.class, EMiniAspectType.class, String.class);
				MiniAspect ma = (MiniAspect) declaredConstructor.newInstance(src, aType, enumName);
				miniAspects[c] = ma;
				int maInt = ma.getInt();
				int maIntByName = ma.getIntByName();
				int maIntByType = ma.getIntByType();
				int maIntByTypeMethod = ma.getIntByTypeMethod();
				if (maInt != maIntByName) {
					System.err.println( "getInt = " + maInt + ", getIntByName = " + maIntByName + " for " + ma);
				}
				if (maInt != maIntByType) {
					System.err.println( "getInt = " + maInt + ", getIntByType = " + maIntByType + " for " + ma);
				}
				if (maInt != maIntByTypeMethod) {
					System.err.println( "getInt = " + maInt + ", getIntByTypeMethod = " + maIntByTypeMethod + " for " + ma);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * ruft per Polymorphie die entsprechende Methode der konkreten MiniAspect-Unterklasse auf
	 * @return dummy Summe
	 * @see MiniAspect#getInt()
	 */
	@Benchmark
	@OperationsPerInvocation(300)
	public int inherited() {
		int sum = 0;
		for (MiniAspect ma : miniAspects) {
			sum += ma.getInt();
		}
		return sum;
	}

	/**
	 * @return dummy Summe
	 * @see MiniAspect#getIntByType()
	 */
	@Benchmark
	@OperationsPerInvocation(300)
	public int byType() {
		int sum = 0;
		for (MiniAspect ma : miniAspects) {
			sum += ma.getIntByType();
		}
		return sum;
	}

	/**
	 * @return dummy Summe
	 * @see MiniAspect#getIntByTypeMethod()
	 */
	@Benchmark
	@OperationsPerInvocation(300)
	public int byTypeMethod() {
		int sum = 0;
		for (MiniAspect ma : miniAspects) {
			sum += ma.getIntByTypeMethod();
		}
		return sum;
	}

	/**
	 * @return dummy Summe
	 * @see MiniAspect#getIntByName()
	 */
	@Benchmark
	@OperationsPerInvocation(300)
	public int byName() {
		int sum = 0;
		for (MiniAspect ma : miniAspects) {
			sum += ma.getIntByName();
		}
		return sum;
	}

	/**
	 * @return dummy Summe
	 * @see MiniAspect#getIntByTypeName()
	 */
	@Benchmark
	@OperationsPerInvocation(300)
	public int byTypeName() {
		int sum = 0;
		for (MiniAspect ma : miniAspects) {
			sum += ma.getIntByTypeName();
		}
		return sum;
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(MegamorphicAspectBench.class.getSimpleName())
				.mode(Mode.AverageTime)
				.timeUnit(TimeUnit.NANOSECONDS)
				.warmupIterations(3)
				.warmupTime(TimeValue.seconds(1))
				.measurementIterations(5)
				.measurementTime(TimeValue.seconds(3))
				.forks(1)
				.build();
		new Runner(opt).run();
	}
}
