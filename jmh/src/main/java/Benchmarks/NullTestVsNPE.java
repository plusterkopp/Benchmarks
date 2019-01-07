package Benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class NullTestVsNPE {

    public static final int Million = 1_000_000;
    @Param( { "10", "10000"}) // "100", "1000", , "100000", "1000000"
    int chance;

    private boolean bArray[];

    @Setup( Level.Trial)
    public void setupArray() {
        bArray = new boolean[ chance];
        bArray[ 0] = false;
        for ( int i = 1;  i < bArray.length;  i++) {
            bArray[ i] = true;
        }
    }

    @Benchmark
    @OperationsPerInvocation( Million)
    public long runIfPlain() {
        int loops = Million / chance;
        long sum = 0;
        for ( int loop = 0;  loop < loops;  loop++) {
            sum = 0;
            String s;
            for ( int i = 0;  i < bArray.length;  i++) {
                if ( bArray[ i]) {
                    s = "string";
                } else {
                    s = null;
                }
                if (s != null) {
                    sum += s.length();
                }
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation( 3 * Million)
    public long runIfCasc() {
        int loops = Million / ( chance - 2);
        long sum = 0;
        for ( int loop = 0;  loop < loops;  loop++) {
            sum = 0;
            for ( int i = 0;  i < bArray.length - 2;  i++) {
                String s1;
                if ( bArray[ i]) {
                    s1 = "string";
                } else {
                    s1 = null;
                }
                String s2;
                if ( bArray[ i+1]) {
                    s2 = "strong";
                } else {
                    s2 = null;
                }
                String s3;
                if ( bArray[ i+2]) {
                    s3 = "strung";
                } else {
                    s3 = null;
                }
                if ( s1 != null) {
                    sum += s1.length();
                }
                if ( s2 != null) {
                    sum += s2.length();
                }
                if ( s3 != null) {
                    sum += s3.length();
                }
            }
        }
        return sum;
    }

    @Fork( jvmArgsPrepend = {"-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI", "-XX:+UseJVMCICompiler"})
    @Benchmark
    @OperationsPerInvocation( 3 * Million)
    public long runNPECascGraal() {
        int loops = Million / ( chance - 2);
        long sum = 0;
        for ( int loop = 0;  loop < loops;  loop++) {
            sum = 0;
            for ( int i = 0;  i < bArray.length - 2;  i++) {
                String s1;
                if ( bArray[ i]) {
                    s1 = "string";
                } else {
                    s1 = null;
                }
                String s2;
                if ( bArray[ i+1]) {
                    s2 = "strong";
                } else {
                    s2 = null;
                }
                String s3;
                if ( bArray[ i+2]) {
                    s3 = "strung";
                } else {
                    s3 = null;
                }
                try {
                    sum += s1.length();
                    sum += s2.length();
                    sum += s3.length();
                } catch (NullPointerException npe) {
                    sum += 0;
                }
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation( 3 * Million)
    public long runNPECasc() {
        int loops = Million / ( chance - 2);
        long sum = 0;
        for ( int loop = 0;  loop < loops;  loop++) {
            sum = 0;
            for ( int i = 0;  i < bArray.length - 2;  i++) {
                String s1;
                if ( bArray[ i]) {
                    s1 = "string";
                } else {
                    s1 = null;
                }
                String s2;
                if ( bArray[ i+1]) {
                    s2 = "strong";
                } else {
                    s2 = null;
                }
                String s3;
                if ( bArray[ i+2]) {
                    s3 = "strung";
                } else {
                    s3 = null;
                }
                try {
                    sum += s1.length();
                    sum += s2.length();
                    sum += s3.length();
                } catch (NullPointerException npe) {
                    sum += 0;
                }
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation( Million)
    public long runNPEPlain() {
        int loops = Million / chance;
        long sum = 0;
        for ( int loop = 0;  loop < loops;  loop++) {
            sum = 0;
            String s;
            for ( int i = 0;  i < bArray.length;  i++) {
                if ( bArray[ i]) {
                    s = "string";
                } else {
                    s = null;
                }
                try {
                    sum += s.length();
                } catch (NullPointerException npe) {
                    sum += 0;
                }
            }
        }
        return sum;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include( NullTestVsNPE.class.getSimpleName())
                .warmupIterations(1)
                .measurementTime(TimeValue.seconds( 5))
                .measurementIterations( 3)
                .mode( Mode.AverageTime)
                .timeUnit(TimeUnit.NANOSECONDS)
                .forks(1)
                .build();
        new Runner(opt).run();
    }

}
