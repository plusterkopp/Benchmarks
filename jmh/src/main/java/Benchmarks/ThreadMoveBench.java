package Benchmarks;

import net.openhft.affinity.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

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

	static {
		AffinityManager am = AffinityManager.getInstance();
		StringBuilder sb = new StringBuilder();
		am.visitEntities( le -> sb.append( le.toString() + ", "));
		if (sb.length() > 2) {
			sb.setLength( sb.length() - 2);
		}
		System.out.println( "CPU Layout: " + sb);
	}

	@Setup(Level.Trial)
	public void setup() {
		currentCPUid = Affinity.getCpu();
	}

	@Benchmark
	public void cycleCores() {
		int	nCPUs = layout.cpus();
		int	nextCPUid = currentCPUid + 1;
		if ( nextCPUid >= nCPUs) {
			nextCPUid = 0;
		}
		int currentCore = layout.coreId( currentCPUid);
		while ( layout.coreId( nextCPUid) == currentCore) {
			if ( ++nextCPUid >= nCPUs) {
				nextCPUid = 0;
			}
		}
		Affinity.setAffinity( nextCPUid);
		currentCPUid = nextCPUid; // Affinity.getCpu();
	}

	@Benchmark
	public void cycleInCore() {
		if ( currentCPUid == 0) {
			currentCPUid = 1;
		} else {
			currentCPUid = 0;
		}
		Affinity.setAffinity( currentCPUid);
	}

	@Benchmark
	public void recycleInCore() {
		Affinity.setAffinity( currentCPUid);
	}

	@Benchmark
	@Measurement( iterations = 1, timeUnit = TimeUnit.SECONDS, time = 10)
	public void noCycleCores() {
		CpuLayout layout = idla.getDefaultLayout();
		int	nCPUs = layout.cpus();
		int	nextCPUid = currentCPUid + 1;
		if ( nextCPUid >= nCPUs) {
			nextCPUid = 0;
		}
		int currentCore = layout.coreId( currentCPUid);
		while ( layout.coreId( nextCPUid) == currentCore) {
			if ( ++nextCPUid >= nCPUs) {
				nextCPUid = 0;
			}
		}
		currentCPUid = nextCPUid; // Affinity.getCpu();
	}

	@Benchmark
	@Measurement( iterations = 1000, timeUnit = TimeUnit.MILLISECONDS, time = 50)
	public void cycleCPUs() {
		CpuLayout layout = idla.getDefaultLayout();
		int	nCPUs = layout.cpus();
		int	nextCPUid = currentCPUid + 1;
		if ( nextCPUid >= nCPUs) {
			nextCPUid = 0;
		}
		Affinity.setAffinity( nextCPUid);
		currentCPUid = nextCPUid; // Affinity.getCpu();
	}

	public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( ThreadMoveBench.class.getSimpleName())
				.warmupIterations( 2)
				.warmupTime( TimeValue.seconds( 5))
		        .mode( Mode.SampleTime)
		        .timeUnit(TimeUnit.MICROSECONDS)
		        .measurementIterations( 1)
				.measurementTime( TimeValue.seconds( 50))
		        .forks(1)
                .build();
        new Runner(opt).run();
    }
}
