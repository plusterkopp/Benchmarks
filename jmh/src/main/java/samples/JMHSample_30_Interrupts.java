/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package samples;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

import java.util.concurrent.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Group)
public class JMHSample_30_Interrupts {

    /*
     * JMH can also detect when threads are stuck in the benchmarks, and try
     * to forcefully interrupt the benchmark thread. JMH tries to do that
     * when it is arguably sure it would not affect the measurement.
     */

    /*
     * In this example, we want to measure the simple performance characteristics
     * of the ArrayBlockingQueue. Unfortunately, doing that without a harness
     * support will deadlock one of the threads, because the executions of
     * take/put are not paired perfectly. Fortunately for us, both methods react
     * to interrupts well, and therefore we can rely on JMH to terminate the
     * measurement for us. JMH will notify users about the interrupt actions
     * nevertheless, so users can see if those interrupts affected the measurement.
     * JMH will start issuing interrupts after the default or user-specified timeout
     * had been reached.
     *
     * This is a variant of org.openjdk.jmh.samples.JMHSample_18_Control, but without
     * the explicit control objects. This example is suitable for the methods which
     * react to interrupts gracefully.
     */

    private BlockingQueue<Integer> q;

    @Setup
    public void setup() {
        q = new ArrayBlockingQueue<>(1);
    }

    @Group("Q")
    @Benchmark
    public Integer take() throws InterruptedException {
        return q.take();
    }

    @Group("Q")
    @Benchmark
    public void put() throws InterruptedException {
        q.put(42);
    }

    /*
     * ============================== HOW TO RUN THIS TEST: ====================================
     *
     * You can run this test:
     *
     * a) Via the command line:
     *    $ mvn clean install
     *    $ java -jar target/benchmarks.jar JMHSample_30 -t 2 -f 5 -to 10
     *    (we requested 2 threads, 5 forks, and 10 sec timeout)
     *
     * b) Via the Java API:
     *    (see the JMH homepage for possible caveats when running from IDE:
     *      http://openjdk.java.net/projects/code-tools/jmh/)
     */

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JMHSample_30_Interrupts.class.getSimpleName())
                .threads(2)
                .forks(5)
                .timeout(TimeValue.seconds(10))
                .build();

        new Runner(opt).run();
    }

}
