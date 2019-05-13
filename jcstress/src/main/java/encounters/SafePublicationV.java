package encounters;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.I_Result;

@JCStressTest

@Outcome(id = "0", expect = Expect.FORBIDDEN, desc = "object created, but no field initialized")
@Outcome(id = "1", expect = Expect.FORBIDDEN, desc = "object created, but only 1 field initialized")
@Outcome(id = "2", expect = Expect.FORBIDDEN, desc = "object created, but only 2 fields initialized")
@Outcome(id = "3", expect = Expect.FORBIDDEN, desc = "object created, but only 3 fields initialized")
@Outcome(id = "4", expect = Expect.ACCEPTABLE, desc = "object created, and fully initialized")
@Outcome(id = "-1", expect = Expect.ACCEPTABLE_INTERESTING, desc = "object not yet created")

@State
public class SafePublicationV {
    int x = 1;

    volatile MyObject o; // volatile, no race

    @Actor
    public void publish() {
        o = new MyObject(x);
    }

    @Actor
    public void consume(I_Result res) {
        MyObject lo = o;
        if (lo != null) {
            res.r1 = lo.x00 + lo.x01 + lo.x02 + lo.x03;
        } else {
            res.r1 = -1;
        }
    }

    static class MyObject {
        int x00, x01, x02, x03;
        public MyObject(int x) {
            x00 = x;
            x01 = x;
            x02 = x;
            x03 = x;
        }
    }
}