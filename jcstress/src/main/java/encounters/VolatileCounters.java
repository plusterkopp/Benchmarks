package encounters;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.I_Result;

@JCStressTest
@Outcome(id = "1", expect = Expect.FORBIDDEN, desc = "Both actors must have a chance for one increment.")
@Outcome(id = "2", expect = Expect.ACCEPTABLE_INTERESTING, desc = "thread preempted during increments")
@Outcome(id = "3", expect = Expect.ACCEPTABLE_INTERESTING, desc = "thread preempted during increments")
@Outcome(id = "4", expect = Expect.ACCEPTABLE_INTERESTING, desc = "thread preempted during increments")
@Outcome(id = "5", expect = Expect.ACCEPTABLE_INTERESTING, desc = "thread preempted during increments")
@Outcome(id = "6", expect = Expect.ACCEPTABLE_INTERESTING, desc = "thread preempted during increments")
@Outcome(id = "7", expect = Expect.ACCEPTABLE_INTERESTING, desc = "thread preempted during increments")
@Outcome(id = "8", expect = Expect.ACCEPTABLE_INTERESTING, desc = "thread preempted during increments")
@Outcome(id = "9", expect = Expect.ACCEPTABLE_INTERESTING, desc = "thread preempted during increments")
@Outcome(id = "10", expect = Expect.ACCEPTABLE_INTERESTING, desc = "thread preempted during increments")
@Outcome(id = "11", expect = Expect.ACCEPTABLE_INTERESTING, desc = "slow increments")
@Outcome(id = "12", expect = Expect.ACCEPTABLE_INTERESTING, desc = "slow increments")
@Outcome(id = "13", expect = Expect.ACCEPTABLE_INTERESTING, desc = "slow increments")
@Outcome(id = "14", expect = Expect.ACCEPTABLE_INTERESTING, desc = "slow increments")
@Outcome(id = "15", expect = Expect.ACCEPTABLE_INTERESTING, desc = "slow increments")
@Outcome(id = "16", expect = Expect.ACCEPTABLE_INTERESTING, desc = "slow increments")
@Outcome(id = "17", expect = Expect.ACCEPTABLE_INTERESTING, desc = "slow increments")
@Outcome(id = "18", expect = Expect.ACCEPTABLE_INTERESTING, desc = "slow increments")
@Outcome(id = "19", expect = Expect.ACCEPTABLE_INTERESTING, desc = "slow increments")
@Outcome(id = "20", expect = Expect.ACCEPTABLE, desc = "all increments completed")

@State
public class VolatileCounters {
    volatile int x;

    @Actor
    void actor1() {
        for (int i = 0; i < 10; i++) {
            x++;
        }
    }

    @Actor
    void actor2() {
        for (int i = 0; i < 10; i++) {
            x++;
        }
    }

    @Arbiter
    public void arbiter(I_Result r) {
        r.r1 = x;
    }
}