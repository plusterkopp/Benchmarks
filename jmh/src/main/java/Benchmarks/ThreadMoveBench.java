package Benchmarks;

import net.openhft.affinity.*;
import net.openhft.affinity.impl.LayoutEntities.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.*;
import java.util.concurrent.*;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ThreadMoveBench {

	IAffinity aff = Affinity.getAffinityImpl();;
	IDefaultLayoutAffinity	idla = (IDefaultLayoutAffinity) aff;
	CpuLayout layout = idla.getDefaultLayout();
	int	currentCPUid = 0;

	int cpuIdSameCore0;
	int cpuIdSameCore1;

	static {
		AffinityManager am = AffinityManager.getInstance();
		am.dumpLayout();
//		StringBuilder sb = new StringBuilder();
//		am.visitEntities( le -> sb.append( le.toString() + ", "));
//		if (sb.length() > 2) {
//			sb.setLength( sb.length() - 2);
//		}
//		System.out.println( "CPU Layout: " + sb);
	}

	@Setup(Level.Trial)
	public void setup() {
		currentCPUid = Affinity.getCpu();
		// only for SMT
		AffinityManager am = AffinityManager.getInstance();
		CpuLayout layout = am.getLayout();
		cpuIdSameCore0 = -1;
		if ( layout.threadsPerCore() > 1) {
			// pick a pair of cpuIDs on same core
			am.visitEntities( e -> {
				if ( cpuIdSameCore0 > -1) {
					return;
				}
				if ( e instanceof Core) {
					Core c = (Core) e;
					List<Integer> cpusOnCore = new ArrayList<>();
					for ( int cpuID = 0;  cpuID < layout.cpus();  cpuID++) {
						if ( layout.coreId( cpuID) == c.getId()) {
							cpusOnCore.add( cpuID);
						}
					}
					if (cpusOnCore.size() > 1) {
						cpuIdSameCore0 = cpusOnCore.get( 0);
						cpuIdSameCore1 = cpusOnCore.get( 1);
					}
				}
			});
		}
		if ( cpuIdSameCore0 == -1) {
			System.err.println( "did not find core with more than one thread");
		}
	}

	@Benchmark
//	@Description( "go to next lcpu in a different core")
	public void cycleCores() {
		int	nCPUs = layout.cpus();
		int	nextCPUid = incOrReset( currentCPUid, nCPUs);
		int currentCore = layout.coreId( currentCPUid);
		while ( layout.coreId( nextCPUid) == currentCore) {
			nextCPUid = incOrReset( nextCPUid, nCPUs);
		}
		Affinity.setAffinity( nextCPUid);
		currentCPUid = nextCPUid; // Affinity.getCpu();
	}

	@Benchmark
//	@Description( "change between different lcpus on same core")
	public void cycleInCore() {
		if ( currentCPUid != cpuIdSameCore0) {
			currentCPUid = cpuIdSameCore0;
		} else {
			currentCPUid = cpuIdSameCore1;
		}
		Affinity.setAffinity( currentCPUid);
	}

	@Benchmark
//	@Description( "get current cpuID executing this thread")
	public int getCurrentCPUid() {
		return Affinity.getCpu();
	}

	@Benchmark
//	@Description( "change affinity to same value (measure only overhead, no actual migration)")
	public void recycleInCore() {
		Affinity.setAffinity( currentCPUid);
	}

	private int incOrReset( int value, int max) {
		int result = value + 1;
		if ( result >= max) {
			return 0;
		}
		return result;
	}

	@Benchmark
//	@Description( "look for next lcpu in a different core, but do not change affinity")
//	@Measurement( iterations = 1, timeUnit = TimeUnit.SECONDS, time = 10)
	public void noCycleCores() {
//		CpuLayout layout = idla.getDefaultLayout();
		int	nCPUs = layout.cpus();
		int currentCore = layout.coreId( currentCPUid);

		int	nextCPUid = incOrReset( currentCPUid, nCPUs);
		while ( layout.coreId( nextCPUid) == currentCore) {
			nextCPUid = incOrReset( nextCPUid, nCPUs);
		}
		currentCPUid = nextCPUid; // Affinity.getCpu();
	}

	@Benchmark
//	@Description( "change to next lcpu (may be in same core)")
//	@Measurement( iterations = 1000, timeUnit = TimeUnit.MILLISECONDS, time = 50)
	public void cycleCPUs() {
//		CpuLayout layout = idla.getDefaultLayout();
		int	nCPUs = layout.cpus();
		int	nextCPUid = incOrReset( currentCPUid, nCPUs);
		Affinity.setAffinity( nextCPUid);
		currentCPUid = nextCPUid; // Affinity.getCpu();
	}

	public static void main(String[] args) throws RunnerException {
		String simpleName = ThreadMoveBench.class.getSimpleName();
		ChainedOptionsBuilder ob = new OptionsBuilder()
				.include( simpleName)
				.warmupIterations(2)
				.warmupTime(TimeValue.seconds(5))
				.mode(Mode.SampleTime)
				.timeUnit(TimeUnit.MICROSECONDS)
				.measurementIterations(1)
				.measurementTime(TimeValue.seconds(50))
				.forks(1);

		// cycleInCore only if SMT
		AffinityManager am = AffinityManager.getInstance();
		CpuLayout layout = am.getLayout();
		if ( layout.threadsPerCore() < 2) {
			ob = ob.exclude( simpleName + "." + "cycleInCore");
		}

		Options opt = ob.build();
        new Runner(opt).run();
    }
}
