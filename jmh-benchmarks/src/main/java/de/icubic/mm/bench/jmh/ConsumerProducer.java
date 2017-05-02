package de.icubic.mm.bench.jmh;

import org.HdrHistogram.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.text.*;
import java.util.concurrent.*;

@State(Scope.Benchmark)
public class ConsumerProducer {

	static final int LoopsMax = 10_000_000;
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
			histo.recordValue( 1e-3 * ( pairs[i].after -  pairs[i].before));
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
						.append( p)
						.append( "% < ")
						.append( nf.format( valueAtP));
			}
		}
		System.out.println( "Latencies:" + sb.toString());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void one_to_one_lt() {
		one_to_one( new LinkedTransferQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void one_to_one_lbq() {
		one_to_one( new LinkedBlockingQueue<Pair>());
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void one_to_one_abqfull() {
		one_to_one( new ArrayBlockingQueue<Pair>( LoopsMax));
	}

	@Benchmark
	@OperationsPerInvocation( LoopsMax)
	public void one_to_one_abq100() {
		one_to_one( new ArrayBlockingQueue<Pair>( 100));
	}

	private void one_to_one( BlockingQueue<Pair> q) {
		Thread  producer = new Thread( "producer") {
			@Override
			public void run() {
				for ( int i = LoopsMax - 1; i >= 0; -- i ) {
					Pair    p = new Pair( System.nanoTime());
					pairs[ i] = p;
					q.offer( p);
				}
			}
		};
		Thread  consumer = new Thread( "producer") {
			@Override
			public void run() {
				try {
					for ( int i = LoopsMax - 1; i >= 0; -- i ) {
						Pair p = q.take();
						p.after = System.nanoTime();
					}
				} catch ( InterruptedException e) {}
			}
		};
		producer.start();
		consumer.start();
		try {
			producer.join();
			consumer.join();
		} catch ( InterruptedException e) {}
	}


	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( ConsumerProducer.class.getSimpleName())
		        .warmupIterations(5)
		        .measurementTime(TimeValue.seconds( 20))
				.measurementIterations( 3)
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
