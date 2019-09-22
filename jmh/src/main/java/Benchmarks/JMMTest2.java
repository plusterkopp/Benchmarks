package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class JMMTest2 {

    static class Point2D {
        int x;
        int y;

        Point2D(int a, int b) {
            x = a;
            y = b;
        }

        public void add( Point2D h) {
            x += h.x;
            y += h.y;
        }

        public void add( Point2DV h) {
            x += h.x;
            y += h.y;
        }

        public void add( int a, int b) {
            x += a;
            y += b;
        }
    }

    static class Point2DV {
        volatile int x;
        volatile int y;

        Point2DV(int a, int b) {
            x = a;
            y = b;
        }
        public void add( Point2D h) {
            x += h.x;
            y += h.y;
        }

        public void add( Point2DV h) {
            x += h.x;
            y += h.y;
        }

        public void add( int a, int b) {
            x += a;
            y += b;
        }
    }

    final int runs = 1 << 20;
    final AtomicBoolean readyFlagAB = new AtomicBoolean( true);
    Point2D toAdd = new Point2D( 3, 5);
    Point2DV toAddV = new Point2DV( 3, 5);
    boolean dummyFlag = false;
    volatile boolean dummyFlagV = false;

    @Benchmark
    @OperationsPerInvocation(value = runs)
    public Point2D addValue() {
        Point2D sum = new Point2D( 0, 0);
        Point2D toAdd = this.toAdd;
        for ( int i = 0;  i < runs;  i++) {
            sum.add( toAdd);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(value = runs)
    public Point2DV addValueVV() {
        Point2DV sum = new Point2DV( 0, 0);
        Point2DV toAddV = this.toAddV;
        for ( int i = 0;  i < runs;  i++) {
            sum.add( toAdd);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(value = runs)
    public Point2D addValueNV() {
        Point2D sum = new Point2D( 0, 0);
        Point2DV toAddV = this.toAddV;
        for ( int i = 0;  i < runs;  i++) {
            sum.add( toAdd);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(value = runs)
    public Point2D addValueNVC() {
        Point2D sum = new Point2D( 0, 0);
        Point2DV toAddV = this.toAddV;
        int a = toAddV.x;
        int b = toAddV.y;
        for ( int i = 0;  i < runs;  i++) {
            sum.add( a, b);
        }
        return sum;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( JMMTest2.class.getSimpleName())
                .warmupIterations( 3)
                .warmupTime(TimeValue.seconds( 4))
                .measurementIterations( 5)
                .measurementTime(TimeValue.seconds( 10))
                .timeUnit( TimeUnit.NANOSECONDS)
                .mode( Mode.AverageTime)
                .forks(1)
                .build();
        new Runner(opt).run();
    }

}
