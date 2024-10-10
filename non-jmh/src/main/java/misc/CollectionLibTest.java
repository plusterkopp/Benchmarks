package misc;

import com.sun.management.HotSpotDiagnosticMXBean;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap;

import javax.management.MBeanServer;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CollectionLibTest {
	private ArrayList<Integer> jdkList_1;
	private ArrayList<Integer> jdkList_10;
	private ArrayList<Integer> jdkList_100;
	private ArrayList<Integer> jdkList_1000;
	private ArrayList<Integer> jdkList_10000;
	private ArrayList<Integer> jdkList_100000;

	private Map<Integer, Integer> jdkMap_1;
	private Map<Integer, Integer> jdkMap_10;
	private Map<Integer, Integer> jdkMap_100;
	private Map<Integer, Integer> jdkMap_1000;
	private Map<Integer, Integer> jdkMap_10000;
	private Map<Integer, Integer> jdkMap_100000;

	private TIntArrayList troveList_1;
	private TIntArrayList troveList_10;
	private TIntArrayList troveList_100;
	private TIntArrayList troveList_1000;
	private TIntArrayList troveList_10000;
	private TIntArrayList troveList_100000;

	private TIntIntHashMap troveMap_1;
	private TIntIntHashMap troveMap_10;
	private TIntIntHashMap troveMap_100;
	private TIntIntHashMap troveMap_1000;
	private TIntIntHashMap troveMap_10000;
	private TIntIntHashMap troveMap_100000;

	private MutableIntList ecList_1;
	private MutableIntList ecList_10;
	private MutableIntList ecList_100;
	private MutableIntList ecList_1000;
	private MutableIntList ecList_10000;
	private MutableIntList ecList_100000;

	private IntIntHashMap ecMap_1;
	private IntIntHashMap ecMap_10;
	private IntIntHashMap ecMap_100;
	private IntIntHashMap ecMap_1000;
	private IntIntHashMap ecMap_10000;
	private IntIntHashMap ecMap_100000;

	public static void main(String[] args) {
		CollectionLibTest test = new CollectionLibTest();
		test.create();

		try {
			dumpHeap( test.getClass().getSimpleName() + "-dump.hprof", false);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void dumpHeap(String filePath, boolean onlyLive) throws IOException {
		MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
			server,
			"com.sun.management:type=HotSpotDiagnostic",
			HotSpotDiagnosticMXBean.class);
		mxBean.dumpHeap(filePath, onlyLive);
	}

private void create() {
		// JDK Collections: List
		jdkList_1 = new ArrayList<>(1);
		fillList( jdkList_1, 1);
		jdkList_10 = new ArrayList<>(10);
		fillList( jdkList_10, 10);
		jdkList_100 = new ArrayList<>(100);
		fillList( jdkList_100, 100);
		jdkList_1000 = new ArrayList<>(1000);
		fillList( jdkList_1000, 1000);
		jdkList_10000 = new ArrayList<>(10000);
		fillList( jdkList_10000, 10000);
		jdkList_100000 = new ArrayList<>(100000);
		fillList( jdkList_100000, 100000);

		// JDK Collections: Map
		jdkMap_1 = new HashMap<>(1);
		fillMap( jdkMap_1, 1);
		jdkMap_10 = new HashMap<>(10);
		fillMap( jdkMap_10, 10);
		jdkMap_100 = new HashMap<>(100);
		fillMap( jdkMap_100, 100);
		jdkMap_1000 = new HashMap<>(1000);
		fillMap( jdkMap_1000, 1000);
		jdkMap_10000 = new HashMap<>(10000);
		fillMap( jdkMap_10000, 10000);
		jdkMap_100000 = new HashMap<>(100000);
		fillMap( jdkMap_100000, 100000);

	// Trove Collections: List
	troveList_1 = new TIntArrayList( 1);
	fillList( troveList_1, 1);
	troveList_10 = new TIntArrayList(10);
	fillList( troveList_10, 10);
	troveList_100 = new TIntArrayList(100);
	fillList( troveList_100, 100);
	troveList_1000 = new TIntArrayList(1000);
	fillList( troveList_1000, 1000);
	troveList_10000 = new TIntArrayList(10000);
	fillList( troveList_10000, 10000);
	troveList_100000 = new TIntArrayList(100000);
	fillList( troveList_100000, 100000);

	// Trove Collections: Map
	troveMap_1 = new TIntIntHashMap(1);
	fillMap( troveMap_1, 1);
	troveMap_10 = new TIntIntHashMap(10);
	fillMap( troveMap_10, 10);
	troveMap_100 = new TIntIntHashMap(100);
	fillMap( troveMap_100, 100);
	troveMap_1000 = new TIntIntHashMap(1000);
	fillMap( troveMap_1000, 1000);
	troveMap_10000 = new TIntIntHashMap(10000);
	fillMap( troveMap_10000, 10000);
	troveMap_100000 = new TIntIntHashMap(100000);
	fillMap( troveMap_100000, 100000);

	// Eclipse Collections: List
	ecList_1 = new IntArrayList( 1);
	fillList( ecList_1, 1);
	ecList_10 = new IntArrayList(10);
	fillList( ecList_10, 10);
	ecList_100 = new IntArrayList(100);
	fillList( ecList_100, 100);
	ecList_1000 = new IntArrayList(1000);
	fillList( ecList_1000, 1000);
	ecList_10000 = new IntArrayList(10000);
	fillList( ecList_10000, 10000);
	ecList_100000 = new IntArrayList(100000);
	fillList( ecList_100000, 100000);

	// Eclipse Collections: Map
	ecMap_1 = new IntIntHashMap(1);
	fillMap( ecMap_1, 1);
	ecMap_10 = new IntIntHashMap(10);
	fillMap( ecMap_10, 10);
	ecMap_100 = new IntIntHashMap(100);
	fillMap( ecMap_100, 100);
	ecMap_1000 = new IntIntHashMap(1000);
	fillMap( ecMap_1000, 1000);
	ecMap_10000 = new IntIntHashMap(10000);
	fillMap( ecMap_10000, 10000);
	ecMap_100000 = new IntIntHashMap(100000);
	fillMap( ecMap_100000, 100000);

}

	private void fillMap( IntIntHashMap map, int max) {
		for ( int i = 0;  i < max;  i++) {
			map.put( i, i);
		}
	}
	private void fillList( MutableIntList list, int max) {
		for ( int i = 0;  i < max;  i++) {
			list.add( i);
		}
	}

	private void fillMap( TIntIntHashMap map, int max) {
		for ( int i = 0;  i < max;  i++) {
			map.put( i, i);
		}
	}
	private void fillList( TIntArrayList list, int max) {
		for ( int i = 0;  i < max;  i++) {
			list.add( i);
		}
	}

	private void fillMap( Map<Integer, Integer> map, int max) {
		for ( int i = 0;  i < max;  i++) {
			map.put( i, new Integer( i));
		}
	}

	private void fillList(Collection<Integer> list, int max) {
		for ( int i = 0;  i < max;  i++) {
			list.add( new Integer( i));
		}
	}
}
