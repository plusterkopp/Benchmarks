/*
 * Created on 13.08.2007
 *
 */
package de.icubic.mm.bench.benches;

import java.text.*;
import java.util.*;
import java.util.concurrent.*;

import de.icubic.mm.bench.base.*;
import de.icubic.mm.server.utils.*;

/**
 * <p>@author ralf
 *
 */
public class CollCounterTest {

	static NumberFormat lnf = DecimalFormat.getNumberInstance();
	static {
		lnf.setMaximumFractionDigits( 0);
		lnf.setGroupingUsed( true);
	};

	private static final int	ArraySize	= 1000 * 1000 * 1;
	static int[]	valueArray = new int[ ArraySize];
	static {
		Random rnd = new Random();
		for ( int i = 0;  i < ArraySize;  i++) {
			valueArray[ i] = rnd.nextInt();
		}
	}
	/**
	 * @param args
	 */
	public static void main( String[] args) {

		abstract class CounterBenchRunnable extends AbstractBenchRunnable {
			protected final int nruns = 543;

			protected Collection<Integer>	coll;

			public CounterBenchRunnable( String name) {
				super( name);
			}

			@Override
			public long getRunSize() {
				return nruns * coll.size();
			}

			long	sum = 0;
			long getCount() {
				return sum;
			}

			@Override
			public String getName() {
				return super.getName() + " (" + lnf.format( getCount()) + ")";
			}

			@Override
			public void reset() {
				super.reset();
				if ( coll == null) {
					return;
				}
				coll.clear();
				for ( int i = 0; i < valueArray.length; i++) {
					coll.add( valueArray[ i]);
				}
//				System.out.println( getName() + ": Size=" + coll.size());
			}
		};

		// nacktes Array
		class ArrayCounterBenchRunnable extends CounterBenchRunnable {

			public ArrayCounterBenchRunnable( String name) {
				super( name);
				coll = null;
			}

			@Override
			public long getRunSize() {
				return nruns * valueArray.length;
			}

			@Override
			public void run() {
				for ( int r = nruns; r > 0 ; r--) {
					sum = 0;
					for ( int i = valueArray.length - 1;  i >= 0;  --i) {
						sum += valueArray[ i];
					}
				}
			}
		};
		final CounterBenchRunnable abench = new ArrayCounterBenchRunnable( "array");

		// ArrayList
		class ArrayListCounterBenchRunnable extends CounterBenchRunnable {

			public ArrayListCounterBenchRunnable( String name) {
				super( name);
				list = new ArrayList<Integer>( valueArray.length);
				coll = list;
				reset();
			}

			private ArrayList<Integer>	list;

			@Override
			public void run() {
				for ( int r = nruns; r > 0 ; r--) {
					sum = 0;
					final int size = list.size();
					for ( int i = size - 1;  i >= 0;  --i) {
						sum += list.get( i);
					}
				}
			}
		};
		final CounterBenchRunnable albench = new ArrayListCounterBenchRunnable( "arrayList");

		// LinkedList
		class LinkedListCounterBenchRunnable extends CounterBenchRunnable {

			public LinkedListCounterBenchRunnable( String name) {
				super( name);
				list = new LinkedList<Integer>();
				coll = list;
				reset();
			}

			private LinkedList<Integer>	list;

			@Override
			public void run() {
				for ( int r = nruns; r > 0 ; r--) {
					sum = 0;
					Iterator<Integer>it = list.listIterator();
					while ( it.hasNext()) {
						sum += it.next();
					}
				}
			}
		};
		final CounterBenchRunnable lbench = new LinkedListCounterBenchRunnable( "linkedList");


		Thread	t = new Thread() {
			@Override
			public void run() {
				BenchLogger.initConsole();
				BenchLogger.sysinfo( "filled " +lnf.format( valueArray.length) + " elements");
				List<CounterBenchRunnable>	benches = IQequitiesUtils.List( abench, albench, lbench);
				IBenchRunner	runner = new BenchRunner( null);
				runner.setRuntime( TimeUnit.SECONDS, 15);
				for ( CounterBenchRunnable bench : benches) {
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
			e.printStackTrace();
		}
	}

}
