package Benchmarks;

import org.HdrHistogram.DoubleHistogram;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

@State(Scope.Benchmark)
public class ConsumerProducerPreAlloc {

	static final int LoopsMax = 1_000_000;
	public static final int N_CORES_HALF = Runtime.getRuntime().availableProcessors() / 2;
	public static double[] Percentiles = { 50, 90, 99, 99.9, 99.99};

	static private class    Pair {
		long    before;
		long    after;

		public Pair( long l ) {
			before = l;
		}

		public Pair() {
		}
	}

	Pair[]  pairs = new Pair[ LoopsMax];
	DoubleHistogram histo;

	final BiConsumer<Queue<Pair>, Pair> blockingQueueConsumer = ( q, p) -> {
		try {
			( ( BlockingQueue) q).put(p);
		} catch ( InterruptedException e) {}
	};

	final Function<Queue<Pair>, Pair> blockingQueueTaker = ( q) -> {
		try {
			return ( ( BlockingQueue<Pair>) q).take();
		} catch (InterruptedException e) {}
		return null;
	};

	final BiConsumer<Queue<Pair>, Pair> queueConsumer = ( q, p) -> q.add(p);

	final Function<Queue<Pair>, Pair> queueTaker = ( q) -> {
		Pair p;
		do {
			p = q.poll();
		} while ( p == null);
		return p;
	};

	@Setup( Level.Trial)
	public void createHistogram() {
		histo = new DoubleHistogram( 1000000, 3);
		histo.setAutoResize( true);
		for ( int i = 0;  i < LoopsMax;  i++) {
			Pair    p = new Pair();
			pairs[ i] = p;
		}
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
	public void n_to_1TB_lt() {
		n_to_n( N_CORES_HALF, 1, new LinkedTransferQueue<Pair>(), 100);
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_1T_lbq() {
		n_to_n( N_CORES_HALF, 1,new LinkedBlockingQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_1TB_lbq() {
		n_to_n( N_CORES_HALF, 1,new LinkedBlockingQueue<Pair>(),100);
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_1T_abqfull() {
		n_to_n( N_CORES_HALF, 1,new ArrayBlockingQueue<Pair>( LoopsMax));
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_1TB_abqfull() {
		n_to_n( N_CORES_HALF, 1,new ArrayBlockingQueue<Pair>( LoopsMax), 100);
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_1T_abq100() {
		n_to_n( N_CORES_HALF, 1,new ArrayBlockingQueue<Pair>( 100));
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void n_to_1TB_abq100() {
		n_to_n( N_CORES_HALF, 1,new ArrayBlockingQueue<Pair>( 100), 100);
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

	private void n_to_n(int nP, int nC, Queue<Pair> q) {
		if (q instanceof BlockingQueue) {
			BlockingQueue bq = (BlockingQueue) q;
			n_to_n( nP, nC, bq, blockingQueueConsumer, blockingQueueTaker);
		} else {
			n_to_n( nP, nC, q, queueConsumer, queueTaker);
		}
	}

	private void n_to_n(int nP, int nC, Queue<Pair> q, BiConsumer<Queue<Pair>, Pair> putConsumer, Function<Queue<Pair>, Pair> getNextElement) {
		List<Thread> threads = new ArrayList<>(  2* nP);
		// die Producer anlegen
		final int loopsPerThreadP = LoopsMax / nP;
		for ( int i = 0, startIndex = 0;  i < nP;  i++, startIndex += loopsPerThreadP) {
			int startIndexF = startIndex;
			int endIndex = startIndex + loopsPerThreadP;
			Thread producer = new Thread( "producer-" + i ) {
				int count = 0;
				@Override
				public void run() {
					for ( int i = startIndexF; i < endIndex; i++ ) {
						Pair p = pairs[ i ];
						p.before = System.nanoTime();
						putConsumer.accept( q, p);
						count++;
					}
				}
			};
			threads.add( producer);
		}
		// die Consumer anlegen
		final int loopsPerThreadC = ( loopsPerThreadP * nP) / nC;
		for ( int i = 0;  i < nC;  i++) {
			Thread  consumer = new Thread( "consumer-" + i) {
				int count = 0;
				@Override
				public void run() {
					for ( int i = 0;  i < loopsPerThreadC;  i++) {
						Pair p = getNextElement.apply( q);
						p.after = System.nanoTime();
						count++;
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

	private void n_to_n(int nP, int nC, BlockingQueue<Pair> q, int batchSize) {
		List<Thread> threads = new ArrayList<>(  2* nP);
		// die Producer anlegen
		final int loopsPerThreadP = LoopsMax / nP;
		for ( int i = 0, startIndex = 0;  i < nP;  i++, startIndex += loopsPerThreadP) {
			int startIndexF = startIndex;
			int endIndex = startIndex + loopsPerThreadP;
			Thread producer = new Thread( "producer-" + i ) {
				int count = 0;
				@Override
				public void run() {
					for ( int i = startIndexF; i < endIndex; i++ ) {
						Pair p = pairs[ i ];
						p.before = System.nanoTime();
						try {
							q.put( p);
						} catch (InterruptedException e) {}
						count++;
					}
				}
			};
			threads.add( producer);
		}
		// die Consumer anlegen
		final int loopsPerThreadC = ( loopsPerThreadP * nP) / nC;
		for ( int i = 0;  i < nC;  i++) {
			Thread  consumer = new Thread( "consumer-" + i) {
				int count = 0;
				@Override
				public void run() {
					ArrayList<Pair> drainTo = new ArrayList<>( batchSize);
					for ( int i = 0;  i < loopsPerThreadC;  ) {
                        int bs = q.drainTo( drainTo, batchSize);
   						for ( int bi = 0;  bi < bs;  bi++) {
   							Pair p = drainTo.get( bi);
							p.after = System.nanoTime();
						}
                        count += bs;
   						i += bs;
   						drainTo.clear();
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

	private void n_to_n1( int nP, int nC, ConcurrentLinkedQueue<Pair> q) {
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
		final int loopsPerThreadC = ( loopsPerThreadP * nP) / nC;
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
                .include( ConsumerProducerPreAlloc.class.getSimpleName())
		        .warmupIterations(3)
		        .measurementTime(TimeValue.seconds( 10))
				.measurementIterations( 5)
		        .forks(1)
                .build();
        new Runner(opt).run();
        System.out.println( "ran on " + Runtime.getRuntime().availableProcessors() + " cpus");
    }
}
