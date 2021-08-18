package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Warmup(iterations = 5, time = 4, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class JMMTest1 {

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
    }

    final int runs = 1 << 20;
    final AtomicBoolean readyFlagAB = new AtomicBoolean( true);
    Point2D toAdd = new Point2D( 3, 5);
    Point2DV toAddV = new Point2DV( 3, 5);
    boolean dummyFlag = false;
    volatile boolean dummyFlagV = false;

    @Benchmark
    @OperationsPerInvocation(value = runs)
    @OutputTimeUnit( TimeUnit.NANOSECONDS)
    public Point2D addValueAB() {
        Point2D sum = new Point2D( 0, 0);
        Point2D toAdd = this.toAdd;
        for ( int i = 0;  i < runs;  i++) {
            if ( readyFlagAB.compareAndSet( true, false)) {
                sum.add(toAdd);
                readyFlagAB.lazySet( true);
            }
        }
        return sum;
    }

//    @Benchmark
//    @OperationsPerInvocation(value = runs)
//    @OutputTimeUnit( TimeUnit.NANOSECONDS)
//    public Point2D addValueDummyFlag() {
//        Point2D sum = new Point2D( 0, 0);
//        Point2D toAdd = this.toAdd;
//        for ( int i = 0;  i < runs;  i++) {
//            dummyFlag = true;
//            sum.add(toAdd);
//            dummyFlag = false;
//        }
//        return sum;
//    }

    @Benchmark
    @OperationsPerInvocation(value = runs)
    @OutputTimeUnit( TimeUnit.NANOSECONDS)
    public Point2D addValueDummyFlagV() {
        Point2D sum = new Point2D( 0, 0);
        Point2D toAdd = this.toAdd;
        for ( int i = 0;  i < runs;  i++) {
            dummyFlagV = true;
            sum.add(toAdd);
            dummyFlagV = false;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(value = runs)
    @OutputTimeUnit( TimeUnit.NANOSECONDS)
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
    @OutputTimeUnit( TimeUnit.NANOSECONDS)
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
    @OutputTimeUnit( TimeUnit.NANOSECONDS)
    public Point2D addValueV() {
        Point2D sum = new Point2D( 0, 0);
        Point2DV toAddV = this.toAddV;
        for ( int i = 0;  i < runs;  i++) {
            sum.add( toAdd);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(value = runs)
    @OutputTimeUnit( TimeUnit.NANOSECONDS)
    public Point2D addValueSync() {
        Point2D sum = new Point2D( 0, 0);
        Point2D toAdd = this.toAdd;
        for ( int i = 0;  i < runs;  i++) {
            synchronized ( sum) {
                sum.add(toAdd);
            }
        }
        return sum;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( JMMTest1.class.getSimpleName())
                .warmupIterations(3)
                .measurementTime(TimeValue.seconds( 10))
                .measurementIterations( 5)
                .timeUnit( TimeUnit.NANOSECONDS)
                .forks(1)
                .build();
        new Runner(opt).run();
    }

}
