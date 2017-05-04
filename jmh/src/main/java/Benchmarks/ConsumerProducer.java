package Benchmarks;

import org.HdrHistogram.DoubleHistogram;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;

@State(Scope.Benchmark)
public class ConsumerProducer {

	static final int LoopsMax = 1_000_000;
	public static final int N_CORES_HALF = Runtime.getRuntime().availableProcessors() / 2;
	public static double[] Percentiles = { 50, 90, 99, 99.9, 99.99};

	static private class    Pair {
		long    before;
		long    after;

		public Pair( long l ) {
			before = l;
		}
	}

	Pair[]  pairs = new Pair[ LoopsMax];
	DoubleHistogram histo;

	@Setup( Level.Trial)
	public void createHistogram() {
		histo = new DoubleHistogram( 1000000, 3);
		histo.setAutoResize( true);
	}

	@TearDown( Level.Iteration)
	public void recordHistogramValues() {
		for ( int i = 0;  i < pairs.length;  i++) {
			// wegen Rundungsfehlern werden evtl nicht alle Einträge belegt
			final Pair pair = pairs[i];
			if (pair != null) {
				histo.recordValue( 1e-3 * ( pair.after - pair.before));
			}
		}
		System.gc();
	}

	@TearDown( Level.Trial)
	public void printHistogram() {
		NumberFormat nf = DecimalFormat.getNumberInstance();
		double lastValue = 0;
		StringBuilder   sb = new StringBuilder();
		for ( int i = Percentiles.length - 1;  i >= 0;  i--) {
			double p = Percentiles[ i];
			double	valueAtP = histo.getValueAtPercentile( p);
			if ( i == Percentiles.length - 1 || valueAtP < lastValue) {
				lastValue = valueAtP;
				sb.append( " ")
						.append( nf.format( p))
						.append( "% < ")
						.append( nf.format( valueAtP));
			}
		}
		if ( lastValue > 0) {
			System.out.println("\nLatencies (µs):" + sb.toString());
		}
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void one_to_one_lt() {
		n_to_n( 1, 1, new LinkedTransferQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void one_to_one_lbq() {
		n_to_n( 1, 1, new LinkedBlockingQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void one_to_one_abqfull() {
		n_to_n( 1, 1, new ArrayBlockingQueue<Pair>( LoopsMax));
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void one_to_one_abq100() {
		n_to_n( 1, 1, new ArrayBlockingQueue<Pair>( 100));
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void one_to_one_cl() {
		n_to_n( 1, 1, new ConcurrentLinkedQueue<>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_n2_lt() {
		n_to_n( 2, 2, new LinkedTransferQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_n2_lbq() {
		n_to_n( 2, 2, new LinkedBlockingQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_n2_abqfull() {
		n_to_n( 2, 2, new ArrayBlockingQueue<Pair>( LoopsMax));
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_n2_abq100() {
		n_to_n( 2, 2, new ArrayBlockingQueue<Pair>( 100));
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_n2_cl() {
		n_to_n( 2, 2, new ConcurrentLinkedQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_12_lt() {
		n_to_n( 2, 1, new LinkedTransferQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_12_lbq() {
		n_to_n( 2, 1, new LinkedBlockingQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_12_abqfull() {
		n_to_n( 2, 1, new ArrayBlockingQueue<Pair>( LoopsMax));
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_12_abq100() {
		n_to_n( 2, 1, new ArrayBlockingQueue<Pair>( 100));
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_12_cl() {
		n_to_n( 2, 1, new ConcurrentLinkedQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_nT_lt() {
		n_to_n( N_CORES_HALF, N_CORES_HALF, new LinkedTransferQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_nT_lbq() {
		n_to_n( N_CORES_HALF, N_CORES_HALF,new LinkedBlockingQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_nT_abqfull() {
		n_to_n( N_CORES_HALF, N_CORES_HALF,new ArrayBlockingQueue<Pair>( LoopsMax));
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_nT_abq100() {
		n_to_n( N_CORES_HALF, N_CORES_HALF,new ArrayBlockingQueue<Pair>( 100));
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_nT_cl() {
		n_to_n( N_CORES_HALF, N_CORES_HALF,new ConcurrentLinkedQueue<>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_1T_lt() {
		n_to_n( N_CORES_HALF, 1, new LinkedTransferQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_1T_lbq() {
		n_to_n( N_CORES_HALF, 1,new LinkedBlockingQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_1T_abqfull() {
		n_to_n( N_CORES_HALF, 1,new ArrayBlockingQueue<Pair>( LoopsMax));
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_1T_abq100() {
		n_to_n( N_CORES_HALF, 1,new ArrayBlockingQueue<Pair>( 100));
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_1T_cl() {
		n_to_n( N_CORES_HALF, 1,new ConcurrentLinkedQueue<>());
	}

	@Benchmark
	@BenchmarkMode( Mode.AverageTime)
	@OutputTimeUnit( TimeUnit.NANOSECONDS)
	public void nanoTime() {
		System.nanoTime();
	}

	private void one_to_one( ConcurrentLinkedQueue<Pair> q) {
		Thread  producer = new Thread( "producer") {
			@Override
			public void run() {
				for ( int i = LoopsMax - 1; i >= 0; -- i ) {
					Pair    p = new Pair( System.nanoTime());
					pairs[ i] = p;
					q.add( p);
				}
			}
		};
		Thread  consumer = new Thread( "consumer") {
			@Override
			public void run() {
				for ( int i = LoopsMax - 1; i >= 0; -- i ) {
					Pair p = null;
					do {
						p = q.poll();
					} while ( p == null);
					p.after = System.nanoTime();
				}
			}
		};
		producer.start();
		consumer.start();
		try {
			producer.join();
			consumer.join();
		} catch ( InterruptedException e) {}
	}

	private void n_to_n( int nP, int nC, BlockingQueue<Pair> q) {
		List<Thread> threads = new ArrayList<>(  2* nP);
		// die Producer anlegen
		final int loopsPerThreadP = LoopsMax / nP;
		for ( int i = 0, startIndex = 0;  i < nP;  i++, startIndex += loopsPerThreadP) {
			int startIndexF = startIndex;
			int endIndex = startIndex + loopsPerThreadP;
			Thread producer = new Thread( "producer-" + i ) {
				@Override
				public void run() {
					for ( int i = startIndexF; i < endIndex; i++ ) {
						Pair p = new Pair( System.nanoTime() );
						pairs[ i ] = p;
						try {
							q.put( p );
						} catch ( InterruptedException e ) {}
					}
				}
			};
			threads.add( producer);
		}
		// die Consumer anlegen
		final int loopsPerThreadC = LoopsMax / nC;
		for ( int i = 0;  i < nC;  i++) {
			Thread  consumer = new Thread( "consumer-" + i) {
				@Override
				public void run() {
					try {
						for ( int i = 0;  i < loopsPerThreadC;  i++) {
							Pair p = q.take();
							p.after = System.nanoTime();
						}
					} catch ( InterruptedException e) {}
				}
			};
			threads.add( consumer);
		}
		threads.forEach( t -> t.start());
		try {   // join geht im Lambda nur mit try/catch, aber dann wird es häßlich
			for ( Thread t : threads) {
				t.join();
			}
		} catch ( InterruptedException e) {}
	}

	private void n_to_n( int nP, int nC, ConcurrentLinkedQueue<Pair> q) {
		final int loopsPerThreadP = LoopsMax / nP;
		List<Thread> threads = new ArrayList<>(  nP + nC);
		for ( int i = 0, startIndex = 0;  i < nP;  i++, startIndex += loopsPerThreadP) {
			int startIndexF = startIndex;
			int endIndex = startIndex + loopsPerThreadP;
			Thread  producer = new Thread( "producer-" + i) {
				@Override
				public void run() {
					for ( int i = startIndexF;  i < endIndex;  i++) {
						Pair    p = new Pair( System.nanoTime());
						pairs[ i] = p;
						q.add( p);
					}
				}
			};
			threads.add( producer);
		}
		// die Consumer anlegen
		final int loopsPerThreadC = LoopsMax / nC;
		for ( int i = 0;  i < nC;  i++) {
			Thread  consumer = new Thread( "consumer-" + i) {
				@Override
				public void run() {
					for ( int i = 0;  i < loopsPerThreadC;  i++) {
						Pair p = null;
						do {
							p = q.poll();
						} while ( p == null);
						p.after = System.nanoTime();
					}
				}
			};
			threads.add( consumer);
		}
		threads.forEach( t -> t.start());
		try {   // join geht im Lambda nur mit try/catch, aber dann wird es häßlich
			for ( Thread t : threads) {
				t.join();
			}
		} catch ( InterruptedException e) {}
	}


	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( ConsumerProducer.class.getSimpleName())
		        .warmupIterations(3)
		        .measurementTime(TimeValue.seconds( 10))
				.measurementIterations( 5)
		        .forks(1)
                .build();
        new Runner(opt).run();
        System.out.println( "ran on " + Runtime.getRuntime().availableProcessors() + " cpus");
    }
}
