package encounters;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;

@JCStressTest
@Outcome(id = "1, 2", expect = ACCEPTABLE, desc = "T1 updated, then T2 updated.")
@Outcome(id = "2, 1", expect = ACCEPTABLE, desc = "T2 updated, then T1 updated.")
@Outcome(id = "1, 1", expect = ACCEPTABLE, desc = "Both T1 and T2 updated concurrently.")

@State
public class VolatileIncrementAtomicityTest {
	volatile int v;

	@Actor
	void actor1( II_Result r) {
		r.r1 = ++v;
	}

	@Actor
	void actor2(II_Result r) {
		r.r2 = ++v;
	}
}
