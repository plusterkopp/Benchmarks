/*
 * Created on 13.08.2007
 *
 */
package de.icubic.mm.bench.benches;

import java.util.*;
import java.util.concurrent.*;

import de.icubic.mm.bench.base.*;
import gnu.trove.map.hash.*;
import net.openhft.koloboke.collect.map.hash.*;

/**
 * <p>@author ralf
 *
 */
public class MapBench {

	private static int RunSize = 100;
	private static final int ReadFactor = 1000;

	/**
	 * @param args
	 */
	public static void main( String[] args) {

		// leer loop
		final IBenchRunnable lbench = new AbstractBenchRunnable( "loop") {

			private final int nruns = 550055;

			@Override
			public void run() {
				for ( int i = nruns; i > 0 ; i--) {
//					@SuppressWarnings("unused") long time = System.nanoTime();
				}
			}

			@Override
			public long getRunSize() {
				return nruns;
			}
		};

		abstract class MapRunnable extends AbstractBenchRunnable {

			public MapRunnable( String string) {
				super( string);
				init();
			}

			@Override
			public void run() {
//				BenchLogger.sysout( getName() + " Map: " + getMap());
				int	sum = 0;
				try {
					for ( int j = 0;  j < ReadFactor; j++) {
						for ( int i = RunSize;  i > 0;  i--) {
							int value = getValue( i);
							sum += value;
						}
					}
				} catch ( Exception e) {
					BenchLogger.syserr( "" + sum, e);
					return;
				}
			}

			abstract Object getMap();
			abstract int getValue( int i);

			@Override
			public long getRunSize() {
				return RunSize * ReadFactor;
			}

			@Override
			public String getName() {
				return super.getName() + "-" + RunSize;
			}

			abstract void createMap();

			public void init() {
				createMap();
//				BenchLogger.sysout( getName() + " Map: " + getMap());
				Random	rnd = new Random();
				try {
					for ( int i = RunSize;  i > 0;  i--) {
						int	key = rnd.nextInt( RunSize);
						int	value = rnd.nextInt();
						putValueAt( value, key);
					}
				} catch ( Exception e) {
					BenchLogger.syserr( "", e);
					return;
				}
//				BenchLogger.sysinfo( "Filled " + getName());
			}

			abstract void putValueAt( int value, int key);
		}

		abstract class UtilMapRunnable extends MapRunnable {

			public UtilMapRunnable( String string) {
				super( string);
			}

			Map<Integer, Integer> map;

			@Override
			int getValue( int i) {
				final Integer	key = Integer.valueOf( i);
				final Integer intV = map.get( key);
				if ( intV == null) {
					return 0;
				}
				return intV;
			}

			@Override
			void putValueAt( int value, int key) {
				map.put( key, value);
			}

			@Override
			Object getMap() {
				return map;
			}
		}

		final MapRunnable hashMapBench = new UtilMapRunnable( "HashMap") {
			@Override
			void createMap() {
				map = new HashMap<Integer, Integer>();
			}
		};

		final MapRunnable concHashMapBench = new UtilMapRunnable( "ConcHashMap") {
			@Override
			void createMap() {
				map = new ConcurrentHashMap<Integer, Integer>();
			}
		};

		final MapRunnable koloHashMapBench = new UtilMapRunnable( "KoloHashMap IntInt") {
			@Override
			void createMap() {
				map = HashIntIntMaps.newMutableMap();
			}
		};

		final MapRunnable koloHashMapBench4 = new UtilMapRunnable( "KoloHashMap IntInt Imm") {
			@Override
			void createMap() {
				map = new HashMap<Integer, Integer>( RunSize);
			}

			@Override
			public void init() {
				super.init();
				// jetzt mit der ursprünglichen Map eine konstante erzeugen
				map = HashIntIntMaps.newImmutableMap( map);
			}

		};

		final MapRunnable koloHashMapBench2 = new UtilMapRunnable( "KoloHashMap ObjObj") {
			@Override
			void createMap() {
				map = HashObjObjMaps.newMutableMap();
			}
		};

		final MapRunnable tIntIntHashMapBench = new MapRunnable( "TIntIntHashMap") {

			private TIntIntHashMap map;

			@Override
			void createMap() {
				map = new TIntIntHashMap();
			}

			@Override
			int getValue( int i) {
				return map.get( i);
			}

			@Override
			void putValueAt( int value, int key) {
				map.put( key, value);
			}

			@Override
			Object getMap() {
				return map;
			}

		};


		Thread	t = new Thread() {
			@Override
			public void run() {
				BenchRunner	runner = new BenchRunner( lbench);
				runner.setCSVName( "MapBench-wksdrrh1.csv", "Test\tns/run");
				runner.setRuntime( TimeUnit.SECONDS, 10);
				runner.run();
				runner.printResults();
				double lruns = runner.getRunsPerSecond();
				runner.setEmptyLoops( lruns);

				MapRunnable[] benches = { hashMapBench, concHashMapBench, koloHashMapBench, koloHashMapBench2, tIntIntHashMapBench, koloHashMapBench4};
				int secs = 5;
				BenchLogger.sysinfo( "Warmup " + secs + " s");
				runner.setRuntime( TimeUnit.SECONDS, secs);
				for ( MapRunnable iBenchRunnable : benches) {
					runner.setBenchRunner( iBenchRunnable);
					runner.run();
					runner.printResults();
				}

				int[] runSizes = { 10, 100, 1000, 10000};
				for ( int i : runSizes) {
					RunSize = i;
					secs = 5;
					BenchLogger.sysinfo( "Runs " + secs + " s");
					runner.setRuntime( TimeUnit.SECONDS, secs);
					for ( MapRunnable iBenchRunnable : benches) {
						iBenchRunnable.init();
						System.gc();
						runner.setBenchRunner( iBenchRunnable);
						runner.run();
						runner.printResults();
						runner.writeCSV( iBenchRunnable.getName() + "\t" + runner.getTimePerRun( TimeUnit.NANOSECONDS));
						double rps = runner.getRunsPerSecond();
						BenchRunner.addToComparisonList( iBenchRunnable.getName(), rps);
					}
				}
				BenchRunner.printComparisonList();
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
