package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class CountersThreaded {

	static final AtomicLong	sumAL = new AtomicLong();
	static final AtomicLong	sumLA = new AtomicLong();

	static final AtomicLong	counterAL = new AtomicLong();
	static final LongAdder counterLA = new LongAdder();

	volatile Thread reader = null;

	@State(Scope.Thread)
	@AuxCounters(AuxCounters.Type.OPERATIONS)
	public static class OpCounters {
		// This accessor will also produce a metric
		public long total() {
			long sum;
			sum = sumAL.getAndSet(0) + sumLA.getAndSet(0);
			if ( sum > 0) {
				NumberFormat nfi = DecimalFormat.getIntegerInstance();
				nfi.setGroupingUsed( true);
//				System.out.println( "\n" +
//					"reporting sum: " + nfi.format( sum));
			}
			return sum;
		}
	}

	private void readCounters() {
		long sum = counterAL.get();
		sumAL.set( sum);
		sum = counterLA.sum();
		sumLA.set( sum);
	}

	@Setup(Level.Trial)
	public void setupReader() {
		if (reader != null) {
			return;
		}
		reader = new Thread(  () -> {
			while (true) {
				readCounters();
				try {
					Thread.sleep( 2000);
				} catch ( InterruptedException e) {
					break;
				}
			}
//			System.out.println( "reader exiting");
		}, "reader");
		reader.start();
//		System.out.println( "started reader");
	}

	@Setup(Level.Iteration)
	public void resetCounters() {
		counterAL.set( 0);
		counterLA.reset();
	}

	@TearDown( Level.Trial)
	public void tearDown() {
		reader.interrupt();
		try {
			reader.join();
			reader = null;
		} catch ( InterruptedException e) {
		}
		readCounters();
		printCounters( "teardown");
	}

	private void printCounters( String reason) {
		NumberFormat nfi = DecimalFormat.getIntegerInstance();
		nfi.setGroupingUsed( true);
		System.out.println( "\n" + reason
			+ " counted"
			+ " AL: " + nfi.format( sumAL)
			+ ", LA: " + nfi.format( sumLA));
	}

	@Benchmark
	@Threads( 1)
	public void countAL01( OpCounters oc) {
		counterAL.incrementAndGet();
	}
	@Benchmark
	@Threads( 4)
	public void countAL04( OpCounters oc) {
		counterAL.incrementAndGet();
	}
	@Benchmark
	@Threads( 16)
	public void countAL16( OpCounters oc) {
		counterAL.incrementAndGet();
	}
	@Benchmark
	@Threads( 64)
	public void countAL64( OpCounters oc) {
		counterAL.incrementAndGet();
	}

	@Benchmark
	@Threads( 1)
	public void countLA01( OpCounters oc) {
		counterLA.increment();
	}
	@Benchmark
	@Threads( 4)
	public void countLA04( OpCounters oc) {
		counterLA.increment();
	}
	@Benchmark
	@Threads( 16)
	public void countLA16( OpCounters oc) {
		counterLA.increment();
	}
	@Benchmark
	@Threads( 64)
	public void countLA64( OpCounters oc) {
		counterLA.increment();
	}


	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( CountersThreaded.class.getSimpleName())
		        .mode( Mode.Throughput)
		        .timeUnit(TimeUnit.MICROSECONDS)
		        .warmupIterations(2)
				.warmupTime( TimeValue.seconds( 1))
		        .measurementIterations(1)
				.measurementTime( TimeValue.seconds( 15))
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
