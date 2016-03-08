/**
 *
 */
package de.icubic.mm.server.utils;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import net.openhft.affinity.*;
import net.openhft.affinity.AffinityManager.*;
import net.openhft.affinity.impl.*;

/**
 * Jeder Thread kann mit {@link Affinity#getThreadId()} seine native Thread ID feststellen, aber ein
 * {@link AffinityThread} verspricht, das auch hin und wieder zu tun, damit {@link ThreadMonitor} das abrufen kann.
 *
 * @author rhelbing
 *
 */
public class AffinityThread extends Thread {

	static {
//		Affinity.getCpu();
	}
	// Last Location sample, sollte gelegentlich vom Thread aktualisiert werden. Es gibt keine Möglichkeit, das von einem anderem Thread aus zu tun.
	int	cpuId = -1;
	int	skipCounter = 0;
	int	skipCounterMax = 1;

	static private final ThreadLocal<StringBuilder> scratchSB = new ThreadLocal<StringBuilder>() {
		@Override
		protected StringBuilder initialValue() {
			return new StringBuilder();
		}
		@Override
		public StringBuilder get() {
			StringBuilder sb = super.get();
			sb.setLength( 0);
			return sb;
		}
	};

	/**
	 * @param cpuId
	 * @return g/n/s/c
	 */
	public static String getLocation( int cpuId) {
		if ( cpuId < 0) {
			return "";
		}
		CpuLayout layout = AffinityLock.cpuLayout();
		int	coreId = layout.coreId( cpuId);
		int	socketId = layout.socketId( cpuId);

		StringBuilder sb = scratchSB.get();
		if ( layout instanceof GroupedCpuLayout) {
			GroupedCpuLayout	g = ( GroupedCpuLayout) layout;
			if ( g.groups() > 1) {
				sb.append( g.groupId( cpuId));
				sb.append( "/");
			}
		}
		if ( layout instanceof NumaCpuLayout) {
			NumaCpuLayout	n = ( NumaCpuLayout) layout;
			if ( n.numaNodes() > 1) {
				sb.append( n.numaNodeId( cpuId));
				sb.append( "/");
			}
		}
		if ( layout.sockets() > 1) {
			sb.append( socketId);
			sb.append( "/");
		}
		sb.append( coreId);
		return sb.toString();
	}



	public AffinityThread() {
		super();
		// TODO Auto-generated constructor stub
	}



	public AffinityThread( Runnable target, String name, int calcGroupId) {
		super( createBindingRunnable( calcGroupId, target), name);
	}

	public AffinityThread( Runnable target, String name, LayoutEntity bindTo) {
		super( createBindingRunnable( bindTo, target), name);
	}

	private static Runnable createBindingRunnable( int groupIndex, Runnable target) {
		if ( groupIndex < 0) {
			return target;
		}
		AffinityManager am = AffinityManager.INSTANCE;
		int numSockets = am.getNumSockets();
		final int socketIndex = groupIndex % numSockets;
		Socket socket = am.getSocket( socketIndex);
		return createBindingRunnable( socket, target);
	}

	private static Runnable createBindingRunnable( LayoutEntity bindTo, Runnable target) {
		if ( bindTo == null) {
			return target;
		}
		if ( ( bindTo instanceof Socket) && AffinityManager.INSTANCE.getNumSockets() == 1) {
			return target;
		}

		Runnable	bindingRunnable = new Runnable() {
			@Override
			public void run() {
				bindTo.bind();
//				BenchLogger.sysinfo( "bound " + Thread.currentThread().getName() + " to " + bindTo.getLocation());
				target.run();
			}
		};
		return bindingRunnable;
	}



	public AffinityThread( Runnable target) {
		super( target);
		// TODO Auto-generated constructor stub
	}



	public AffinityThread( String name) {
		super( name);
		// TODO Auto-generated constructor stub
	}



	public AffinityThread( ThreadGroup group, Runnable target, String name, long stackSize) {
		super( group, target, name, stackSize);
		// TODO Auto-generated constructor stub
	}



	public AffinityThread( ThreadGroup group, Runnable target, String name) {
		super( group, target, name);
		// TODO Auto-generated constructor stub
	}



	public AffinityThread( ThreadGroup group, Runnable target) {
		super( group, target);
		// TODO Auto-generated constructor stub
	}



	public AffinityThread( ThreadGroup group, String name) {
		super( group, name);
		// TODO Auto-generated constructor stub
	}



	/**
	 * falls {@link Thread#currentThread()} ein {@link AffinityThread} ist: ruft {@link Affinity#getCpu()} ab und speichert sie für später
	 */
	public static void recordCpuId() {
		Thread current = Thread.currentThread();
		if ( current instanceof AffinityThread) {
			AffinityThread at = ( AffinityThread) current;
			if ( at.skipCounter >= at.skipCounterMax) {
				at.skipCounter = 0;
				at.cpuId = Affinity.getCpu();
			} else {
				at.skipCounter++;
			}
		}
	}

	/**
	 * das ist nicht notwendigerweise die CPU, auf der der Thread gerade läuft (falls er überhaupt läuft), sondern die, bei der er zuletzt seine CPU abgefragt hat
	 * @return {@link #cpuId}
	 * @see #recordCpuId()
	 */
	public int	getLastCpuId() {
		return cpuId;
	}

	public String getLocation() {
		List<AffinityManager.LayoutEntity> boundTo = AffinityManager.INSTANCE.getBoundTo( this);
		String	locString = getLocation( cpuId);
		if ( boundTo.isEmpty()) {
			return locString;
		}
		LayoutEntity entity = boundTo.get( 0);
		if ( entity != null) {
			String	boundString = entity.getLocation();
			if ( entity instanceof Socket) {
				boundString += "/x";
			} else if ( entity instanceof NumaNode) {
				boundString += "/x/x";
			}
			return locString + " [" + boundString + "]";
		}
		return getLocation( cpuId);
	}

	public static int getCoresPerSocket() {
		return 	AffinityLock.cpuLayout().coresPerSocket();
	}

	public static int getThreadsPerCore() {
		return 	AffinityLock.cpuLayout().threadsPerCore();
	}

	public static int getThreadsPerSocket() {
		final CpuLayout cpuLayout = AffinityLock.cpuLayout();
		return 	cpuLayout.threadsPerCore() * cpuLayout.coresPerSocket();
	}

	public static int getNumThreadsOnNode( NumaNode node) {
		int	sum = 0;
		final AffinityManager am = AffinityManager.INSTANCE;
		for ( int i = 0;  i < am.getNumSockets();  i++) {
			Socket s = am.getSocket( i);
			if ( s.getNode() == node) {
				sum += getThreadsPerSocket();
			}
		}
		return sum;
	}

	public static Map<LayoutEntity, Collection<Thread>> getAffinities() {
		Map<LayoutEntity, Collection<Thread>> collected = new HashMap<>();
		IAffinity aff = Affinity.getAffinityImpl();
		if ( ! ( aff instanceof WindowsJNAAffinity)) {
			return collected;
		}
		Consumer<LayoutEntity> collector = ( entity) -> {
			Collection<Thread> threads = entity.getThreads();
			if ( threads != null && ! threads.isEmpty()) {
				SortedSet<Thread> sorted = new TreeSet<>( ( t1, t2) -> t1.getName().compareTo( t2.getName()));
				sorted.addAll( threads);
				collected.put( entity, sorted);
			}
		};
		AffinityManager.INSTANCE.visitEntities( collector);
		return collected;
	}

	public static Map<String, Collection<String>> getBoundLocations() {
		Map<LayoutEntity, Collection<Thread>> affinities = getAffinities();
		Map<String, Collection<String>> result = new HashMap<>();
		Function<Thread, String> mapper = ( t) -> t.getName();
		affinities.forEach( ( entity, threads) ->
			result.put( entity.getLocation(),
					threads.stream()
						.map( mapper)
						.collect( Collectors.toList())));
		return result;
	}



	public static int getNumNodes() {
		IAffinity aff = Affinity.getAffinityImpl();
		if ( ! ( aff instanceof WindowsJNAAffinity)) {
			return 1;
		}
		WindowsJNAAffinity waff = ( WindowsJNAAffinity) aff;
		WindowsCpuLayout layout = ( WindowsCpuLayout) waff.getDefaultLayout();
		return layout.numaNodes();
	}

	public static NumaNode getOtherNode( NumaNode node) {
		NumaNode[] nodeA = new NumaNode[ 1];
		nodeA[ 0] = null;
		AffinityManager.INSTANCE.visitEntities( ( entity) -> {
			if ( ( entity instanceof NumaNode) && node != entity) {
				nodeA[ 0] = ( NumaNode) entity;
			}
		});
		return nodeA[ 0];
	}



	public static String getBoundTo() {
		List<LayoutEntity> boundTo = AffinityManager.INSTANCE .getBoundTo( Thread.currentThread());
		LayoutEntity first = boundTo.size() == 1 ? boundTo.get( 0) : null;
		if ( first != null) {
			String loc = first.getLocation();
			return loc;
		}
		return "none";
	}

}
