package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class GenericLockBench {

	public interface ILockType {
		boolean isAskLock();
	}

	// Ask locks (5)
	final static class ReadLock implements ILockType {
		public boolean isAskLock() {
			return true;
		}
		public String toString() {
			return "a1";
		}
	}

	final static class SharedLock implements ILockType {
		public boolean isAskLock() {
			return true;
		}
		public String toString() {
			return "a2";
		}
	}

	final static class UpgradeableLock implements ILockType {
		public boolean isAskLock() {
			return true;
		}
		public String toString() {
			return "a3";
		}
	}

	final static class OptimisticLock implements ILockType {
		public boolean isAskLock() {
			return true;
		}
		public String toString() {
			return "a4";
		}
	}

	final static class SnapshotLock implements ILockType {
		public boolean isAskLock() {
			return true;
		}
		public String toString() {
			return "a5";
		}
	}

	// Non-ask locks (5)
	final static class WriteLock implements ILockType {
		public boolean isAskLock() {
			return false;
		}
		public String toString() {
			return "x1";
		}
	}

	final static class ExclusiveLock implements ILockType {
		public boolean isAskLock() {
			return false;
		}
		public String toString() {
			return "x2";
		}
	}

	final static class UpgradeableReadLock implements ILockType {
		public boolean isAskLock() {
			return false;
		}
		public String toString() {
			return "x3";
		}
	}

	final static class PessimisticLock implements ILockType {
		public boolean isAskLock() {
			return false;
		}
		public String toString() {
			return "x4";
		}
	}

	final static class DirtyReadLock implements ILockType {
		public boolean isAskLock() {
			return false;
		}
		public String toString() {
			return "x5";
		}
	}

	@State(Scope.Benchmark)
	public static class LockTypes {
		private static final List<ILockType> ALL_LOCKS = createAllLocks();

		private static List<ILockType> createAllLocks() {
			return Arrays.asList(
				new ReadLock(),       // ask
				new SharedLock(),
				new UpgradeableLock(),
				new OptimisticLock(),
				new SnapshotLock(),

				new WriteLock(),      // non-ask
				new ExclusiveLock(),
				new UpgradeableReadLock(),
				new PessimisticLock(),
				new DirtyReadLock()
			);
		}

		public static List<ILockType> all() {
			return ALL_LOCKS;
		}

		public static int countAsk() {
			return (int) ALL_LOCKS.stream().filter(ILockType::isAskLock).count();
		}
	}

	public static abstract class AbstractState {

		static final VarHandle AddCounterVH;
		static final VarHandle RemoveCounterVH;
		static final VarHandle AddAskCounterVH;
		static final VarHandle RemoveAskCounterVH;

		static {
			try {
				MethodHandles.Lookup l = MethodHandles.lookup().in( AbstractState.class);
				AddCounterVH = l.findVarHandle( AbstractState.class, "addCounter", long.class);
				RemoveCounterVH = l.findVarHandle( AbstractState.class, "removeCounter", long.class);
				AddAskCounterVH = l.findVarHandle( AbstractState.class, "addAskCounter", long.class);
				RemoveAskCounterVH = l.findVarHandle( AbstractState.class, "removeAskCounter", long.class);
			} catch ( ReflectiveOperationException e) {
				throw new Error( e);
			}
		}

		private long addCounter = 0;
		private long removeCounter = 0;
		private long addAskCounter = 0;
		private long removeAskCounter = 0;

		protected abstract void remove(ILockType lock);

		protected abstract void add(ILockType lock);

		public abstract boolean isLocked( boolean forAsk);

		public abstract int count();

		public abstract String printLocks();
	}

	public static class StateV1 extends AbstractState {
		private final Set<ILockType> locks = Collections.synchronizedSet( new HashSet<>());     // non-ask
		private final Set<ILockType> askLocks = Collections.synchronizedSet( new HashSet<>());  // ask

		public void add(ILockType lock) {
			if (lock.isAskLock()) {
				if ( askLocks.add(lock)) {
					AddAskCounterVH.getAndAdd( this, 1);
				}
			} else {
				if ( locks.add(lock)) {
					AddCounterVH.getAndAdd( this, 1);
				}
			}
		}
		public void remove(ILockType lock) {
			if (lock.isAskLock()) {
				if ( askLocks.remove(lock)) {
					RemoveAskCounterVH.getAndAdd( this, 1);
				}
			} else {
				if ( locks.remove(lock)) {
					RemoveCounterVH.getAndAdd( this, 1);
				}
			}
		}

		public boolean isLocked(boolean forAsk) {
			final Set<ILockType> l = forAsk ? askLocks : locks;
			return ! l.isEmpty();
		}

		@Override
		public int count() {
			return askLocks.size() + locks.size();
		}

		@Override
		public String printLocks() {
			return locks + "/" + askLocks +
				" +" + AddCounterVH.getAcquire( this) + "/" + AddAskCounterVH.getAcquire( this) +
				" -" + RemoveCounterVH.getAcquire( this) + "/" + RemoveAskCounterVH.getAcquire( this)
				;
		}

	}


	public abstract static class StateV2 extends AbstractState {
		protected final Set<ILockType> locks = new HashSet<>();

		public void add(ILockType lock) {
			synchronized ( locks) {
				if ( locks.add(lock)) {
					if (lock.isAskLock()) {
						AddAskCounterVH.getAndAdd( this, 1);
					} else {
						AddCounterVH.getAndAdd( this, 1);
					}
				}
			}
		}

		public void remove(ILockType lock) {
			synchronized (locks) {
				if (locks.remove(lock)) {
					if (lock.isAskLock()) {
						RemoveAskCounterVH.getAndAdd( this, 1);
					} else {
						RemoveCounterVH.getAndAdd( this, 1);
					}
				}
			}
		}

		@Override
		public int count() {
			synchronized ( locks) {
				return locks.size();
			}
		}

		@Override
		public String printLocks() {
			synchronized (locks) {
				List<ILockType> bothLocks = locks.stream().filter(l -> !l.isAskLock()).collect(Collectors.toList());
				List<ILockType> askLocks = locks.stream().filter(l -> l.isAskLock()).collect(Collectors.toList());
				return bothLocks + "/" + askLocks +
					" +" + AddCounterVH.getAcquire( this) + "/" + AddAskCounterVH.getAcquire( this) +
					" -" + RemoveCounterVH.getAcquire( this) + "/" + RemoveAskCounterVH.getAcquire( this)
					;
			}
		}
	}

	public static class StateV2Stream extends StateV2 {

		// Stream-based (lazy filter)
		public boolean isLocked(boolean forAsk) {
			synchronized (locks) {
				return locks.stream()
					.anyMatch(forAsk ? ILockType::isAskLock : l -> !l.isAskLock());
			}
		}
	}

	public static class StateV2Loop extends StateV2 {

		// Stream-based (lazy filter)
		public boolean isLocked(boolean forAsk) {
			synchronized (locks) {
				for (ILockType lock : locks) {
					if (forAsk == lock.isAskLock()) {
						return true;
					}
				}
				return false;
			}
		}
	}

	public static class StateV3 extends StateV2 {

		private static final VarHandle LockVH;
		private static final VarHandle AskLockVH;

		static {
			try {
				MethodHandles.Lookup l = MethodHandles.lookup().in( StateV3.class);
				LockVH = l.findVarHandle( StateV3.class, "locked", boolean.class);
				AskLockVH = l.findVarHandle( StateV3.class, "askLocked", boolean.class);
			} catch ( ReflectiveOperationException e) {
				throw new Error( e);
			}
		}

		private boolean locked = false;
		private boolean askLocked = false;

		public void add(ILockType lock) {
			synchronized ( locks) {
				super.add(lock);
				updateFlags();
			}
		}
		public void remove(ILockType lock) {
			synchronized ( locks) {
				super.remove(lock);
				updateFlags();
			}
		}

		private void findLocked( boolean lockedA[]) {
			byte found = 0;	// bei 2 raus
			for ( ILockType lock : locks) {
				if ( lock.isAskLock()) {
					if ( ! lockedA[ 1]) {	// wenn erstes Ask-Lock
						lockedA[ 1] = true;
						found++;
					}
				} else {
					if ( ! lockedA[ 0]) {	// wenn erster Beid-Lock
						lockedA[ 0] = true;
						found++;
					}
				}
				if (found > 1) {
					return;
				}
			}
		}

		private void updateFlags() {
			boolean lockedA[] = { false, false};
			findLocked( lockedA);
			LockVH.setRelease( this, lockedA[ 0]);
			AskLockVH.setRelease( this, lockedA[ 1]);
		}

		public boolean isLocked(boolean forAsk) {
			final VarHandle vh = forAsk ? AskLockVH : LockVH;
			return (boolean) vh.getAcquire( this);
		}
	}



	private static final List<ILockType> ALL_LOCKS = LockTypes.all();

	@Param({"0", "2", "5", "8"})
	public int fillSize;

	@Param({"2", "100", "10000"})
	public int mutateChance;

	private AbstractState stateV1;
	private AbstractState stateV2Stream, stateV2Loop;
	private AbstractState stateV3;

	private final Random rnd = new Random(42);

	final int extraBits = 1 << 1;
	final int mutateThreshold = extraBits * ALL_LOCKS.size();
	int mutateBounds;

	@Setup( Level.Trial)
	public void setup() {
		stateV1 = new StateV1();
		stateV2Stream = new StateV2Stream();
		stateV2Loop = new StateV2Loop();
		stateV3 = new StateV3();

		// Shuffle and pick first fillSize items
		List<ILockType> shuffled = new ArrayList<>(ALL_LOCKS);
		Collections.shuffle(shuffled, rnd);

		for (int i = 0; i < fillSize && i < shuffled.size(); i++) {
			ILockType lock = shuffled.get(i);
			stateV1.add(lock);
			stateV2Stream.add(lock);
			stateV2Loop.add(lock);
			stateV3.add(lock);
		}

		mutateBounds = mutateThreshold * mutateChance;
	}

	@TearDown( Level.Trial)
	public void tearDown() {
		System.out.println( "locks"
			+ " V1: " + stateV1.printLocks()
			+ " V2 S: " + stateV2Stream.printLocks()
			+ " V2 L: " + stateV2Stream.printLocks()
			+ " V3: " + stateV3.printLocks()
		);
	}

	private void mutate(AbstractState state) {
		if ( fillSize == 0) {	// darf eh nichts hinzufügen
			return;
		}
		int code = rnd.nextInt( mutateBounds);
		if ( code >= mutateThreshold) {
			return;
		}
		boolean lockMe = ( code & 1) != 0;
		int index = ( code % mutateThreshold) / 2;
		ILockType lock = LockTypes.all().get(index);
		if ( ! lockMe) {
			state.remove( lock);
			return;
		}
		if ( state.count() < fillSize) {
			state.add( lock);
		}
	}


	@Benchmark
	public boolean test_V1_ask() {
		mutate( stateV1);
		return stateV1.isLocked(true);
	}

	@Benchmark
	public boolean test_V1_nonAsk() {
		mutate( stateV1);
		return stateV1.isLocked(false);
	}
	@Benchmark
	@Threads( 4)
	public boolean test_V1_nonAsk_T4() {
		mutate( stateV1);
		return stateV1.isLocked(false);
	}

	@Benchmark
	public boolean test_V2_stream_ask() {
		mutate( stateV2Stream);
		return stateV2Stream.isLocked(true);
	}

	@Benchmark
	public boolean test_V2_stream_nonAsk() {
		mutate( stateV2Stream);
		return stateV2Stream.isLocked(false);
	}
	@Benchmark
	@Threads( 4)
	public boolean test_V2_stream_nonAsk_T4() {
		mutate( stateV2Stream);
		return stateV2Stream.isLocked(false);
	}

	@Benchmark
	public boolean test_V2_loop_ask() {
		mutate( stateV2Loop);
		return stateV2Loop.isLocked(true);
	}

	@Benchmark
	public boolean test_V2_loop_nonAsk() {
		mutate( stateV2Loop);
		return stateV2Loop.isLocked(false);
	}
	@Benchmark
	@Threads( 4)
	public boolean test_V2_loop_nonAsk_T4() {
		mutate( stateV2Loop);
		return stateV2Loop.isLocked(false);
	}

	@Benchmark
	public boolean test_V3_nonAsk() {
		mutate( stateV3);
		return stateV3.isLocked(false);
	}
	@Benchmark
	@Threads( 4)
	public boolean test_V3_nonAsk_T4() {
		mutate( stateV3);
		return stateV3.isLocked(false);
	}

	@Benchmark
	public boolean test_V3_ask() {
		mutate( stateV3);
		return stateV3.isLocked(true);
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
			.include( GenericLockBench.class.getSimpleName())

			.warmupIterations(3)
			.warmupTime( TimeValue.seconds( 1))

			.measurementIterations( 2)
			.measurementTime( TimeValue.seconds( 5))

			.forks(1)
			.build();
		new Runner(opt).run();
	}

}
