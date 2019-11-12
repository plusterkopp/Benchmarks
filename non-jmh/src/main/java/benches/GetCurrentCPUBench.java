/*
 * Created on 13.08.2007
 *
 */
package benches;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.server.utils.*;
import net.openhft.affinity.*;
import net.openhft.affinity.impl.*;
import net.openhft.affinity.impl.WindowsJNAAffinity.*;
import net.openhft.affinity.impl.WindowsJNAAffinity.PROCESSOR_NUMBER.*;

/**
 * <p>@author ralf
 *
 */
public class GetCurrentCPUBench {

	static NumberFormat lnf = DecimalFormat.getNumberInstance();
	static {
		lnf.setMaximumFractionDigits( 3);
		lnf.setGroupingUsed( true);
	};

	static final IAffinity	AffinityImpl = Affinity.getAffinityImpl();
	static final WindowsJNAAffinity WinImpl = WindowsJNAAffinity.INSTANCE;

	static int	runSize = 1000;

	static abstract class BenchRunnable extends AbstractBenchRunnable {
		public BenchRunnable( String string) {
			super( string);
		}

		@Override
		public long getRunSize() {
			return runSize;
		}
	}

	final IBenchRunnable	direct = new BenchRunnable( "direct") {
		@Override
		public void run() {
			for ( int i = runSize;  i >= 0;  --i) {
				final short[] gA = new short[1];
				final byte[] pA = new byte[1];
				WindowsJNAAffinity.getCurrentCpuInfo( gA, pA);
			}
		}
	};

	final IBenchRunnable	direct0 = new BenchRunnable( "direct no acc") {
		@Override
		public void run() {
			final short[] gA = new short[1];
			final byte[] pA = new byte[1];
			for ( int i = runSize;  i >= 0;  --i) {
				WindowsJNAAffinity.getCurrentCpuInfo( gA, pA);
			}
			if ( gA[0] > 1000) {
				System.out.println();
			}
		}
	};

	final IBenchRunnable	struct = new BenchRunnable( "struct") {
		@Override
		public void run() {
			for ( int i = runSize;  i >= 0;  --i) {
				PROCESSOR_NUMBER.ByReference procNum = new ByReference();
				WinImpl.getCurrentProcessorNumber( procNum);
			}
		}
	};

	final IBenchRunnable	structTL = new BenchRunnable( "struct no acc") {

		ThreadLocal<PROCESSOR_NUMBER.ByReference> pnTL = new ThreadLocal<PROCESSOR_NUMBER.ByReference>() {
			@Override
			protected ByReference initialValue() {
				return new PROCESSOR_NUMBER.ByReference();
			}
		};
		@Override
		public void run() {
			for ( int i = runSize;  i >= 0;  --i) {
				PROCESSOR_NUMBER.ByReference procNum = pnTL.get();
				WinImpl.getCurrentProcessorNumber( procNum);
			}
			if ( pnTL.get().group.shortValue() > 1000) {
				System.out.println();
			}
		}
	};

	/**
	 * @param args
	 */
	public static void main( String[] args) {

		Thread	t = new Thread() {
			@Override
			public void run() {
				GetCurrentCPUBench thisBench = new GetCurrentCPUBench();
				if ( Affinity.getAffinityImpl() != WinImpl) {
					System.err.println( "no windows affinity");
					return;
				}
//				BenchLogger.initConsole();
				List<IBenchRunnable>	benches = IQequitiesUtils.List( thisBench.direct, thisBench.direct0, thisBench.struct, thisBench.structTL);
				IBenchRunner	runner = new BenchRunner( null);
				runner.setRuntime( TimeUnit.SECONDS, 60);
				for ( IBenchRunnable bench : benches) {
					System.gc();
					runner.setBenchRunner( bench);
					runner.run();
					runner.printResults();
					BenchRunner.addToComparisonList( bench.getName(), runner.getRunsPerSecond());
				}
				BenchRunner.printComparisonList();
				BenchRunner.clearComparisonList();
			}
		};
		t.start();
		try {
			t.join();
		} catch ( InterruptedException e) {
			BenchLogger.syserr( "", e);
		}
	}

}
