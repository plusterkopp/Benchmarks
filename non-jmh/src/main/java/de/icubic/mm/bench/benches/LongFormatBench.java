/*
 * Created on 13.08.2007
 *
 */
package de.icubic.mm.bench.benches;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.communication.util.*;
import de.icubic.mm.server.utils.*;

/**
 * <p>@author ralf
 *
 */
public class LongFormatBench {

	enum	RunMode {
		SingleDigitNative( 0, 9, false),
		SingleDigitFast( 0, 9, true),
		FourDigitNative( 0, 9999, false),
		FourDigitFast( 0, 9999, true),
		TenDigitNative( 0, 999999999L, false),
		TenDigitFast( 0, 999999999L, true);

		final int	minVal;
		final long	maxVal;
		final boolean	isFast;

		RunMode( int minval, long maxval, boolean isFast) {
			this.minVal = minval;
			this.maxVal = maxval;
			this.isFast = isFast;
		}
	};

	/**
	 * @param args
	 */
	public static void main( String[] args) {

		final IBenchRunnable lbench = new AbstractBenchRunnable( "loop") {

			private final int nruns = 550055;
			private StringBuilder	sb = new StringBuilder( 100);

			public void run() {
				for ( int i = nruns; i > 0 ; i--) {
					sb.append( "   ");
					sb.setLength( 0);
				}
			}

			public long getRunSize() {
				return nruns;
			}
		};

		class ModeBenchRunnable extends AbstractBenchRunnable {

			private final RunMode mode;
			private final int nruns = 1;
			private int size = 1000;
			private StringBuilder	sb = new StringBuilder( 100);

			private long[]		values;
			private String	s;

			public ModeBenchRunnable( String name, RunMode mode) {
				super( name);
				this.mode = mode;
				setup( mode, size);
			}

			private void setup( RunMode mode, int size) {
				values = new long[ size];
				Random	rnd = new Random();
				for ( int i = 0;  i < size;  i++) {
					values[ i] = rnd.nextLong() % ( mode.maxVal + 1);
				}
			}

			@Override
			public void run() {
				if ( mode.isFast) {
					for ( int i = values.length - 1;  i  >= 0;  i--) {
						final long v = values[ i];
						if ( v <= Integer.MAX_VALUE) {
							DoubleToString.append( sb, ( int) v);
						} else {
							DoubleToString.append( sb, v);
						}
						sb.setLength( 0);
					}
				} else {
					for ( int i = values.length - 1;  i  >= 0;  i--) {
//						sb.append( values[ i]);
//						sb.setLength( 0);
						s = "" + values[ i];
					}
				}
			}

			@Override
			public long getRunSize() {
				return nruns * values.length;
			}
		};

		Thread	t = new Thread() {
			@Override
			public void run() {
				try {
					NumberFormat nf = DecimalFormat.getNumberInstance();
					nf.setMaximumFractionDigits( 3);

					int nCores = Runtime.getRuntime().availableProcessors();
					// Warmup
					final int warmupS = 30;
					ThreadPoolExecutor	tpe = new AddThreadBeforeQueuingThreadPoolExecutor( 0, nCores, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), 0);
					for ( final RunMode rm: RunMode.values()) {
						tpe.execute( new Runnable() {
							@Override
							public void run() {
								ModeBenchRunnable lfbench = new ModeBenchRunnable( "LongFormatBench-" + rm, rm);
								IBenchRunner	runner = new BenchRunner( lfbench);
								runner.setRuntime( TimeUnit.SECONDS, warmupS);
								runner.run();
							}
						});
					}
					tpe.execute( new Runnable() {
						@Override
						public void run() {
							IBenchRunner	runner = new BenchRunner( lbench);
							runner.run();
						}
					});
					tpe.shutdown();
					boolean ok;
					while ( ! tpe.awaitTermination( 10, TimeUnit.SECONDS));
					BenchLogger.sysout( "Warmup Complete ");

					final int	runtimeS = 60;
					// EmpyLoop nach Warmup
					IBenchRunner	runner = new BenchRunner( lbench);
					runner.setRuntime( TimeUnit.SECONDS, runtimeS);
					runner.run();
					final double	emptyLoopsPerSec = runner.getRunsPerSecond();
					runner.printResults();
					int nThreads = Math.max( 1, nCores / 4);
					// Ernst
					tpe = new AddThreadBeforeQueuingThreadPoolExecutor( 0, nThreads , 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), 0);
					for ( final RunMode rm: RunMode.values()) {
						tpe.execute( new Runnable() {
							@Override
							public void run() {
								try {
									ModeBenchRunnable qdfbench = new ModeBenchRunnable( "LongFormatBench-" + rm, rm);
									IBenchRunner	runner = new BenchRunner( qdfbench);
									runner.setEmptyLoops( emptyLoopsPerSec);
									runner.setRuntime( TimeUnit.SECONDS, runtimeS);
//									sysout( "Run " + qdfbench.getName());
									runner.run();
									runner.printResults();
									BenchRunner.addToComparisonList( qdfbench.getName(), runner.getRunsPerSecond());
								} catch ( Exception e) {
									BenchLogger.syserr( "Exception", e);
								}
							}
						});
					}
					tpe.shutdown();
//					sysout( "Shutdown Runs");
					ok = tpe.awaitTermination( ( 1 + tpe.getTaskCount() / nThreads) * runtimeS, TimeUnit.HOURS);
//					sysout( "Runs Terminated " + ( ok ? "success" : "timeout"));
					BenchRunner.printComparisonList();
				} catch ( InterruptedException ie) {}
			}
		};
		t.start();
		try {
			t.join();
		} catch (InterruptedException e) {
			BenchLogger.syserr( "", e);
		}
		System.exit( 0);
	}


}
