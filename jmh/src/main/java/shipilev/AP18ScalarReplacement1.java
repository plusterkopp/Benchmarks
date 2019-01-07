package shipilev;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class AP18ScalarReplacement1 {

    int x;

    @Benchmark
    public int single() {
        MyObject o = new MyObject(x);
        return o.x;
    }

    static class MyObject {
        final int x;
        public MyObject(int x) {
            this.x = x;
        }
    }

}
