package starter.breaches;

import org.junit.jupiter.api.Test;
import starter.testing.Expect;

/** BREACH of E8-time-budget: an untagged (logic tier, 20 ms) test that takes 60 ms. */
class SlowLogicTest {

    @Test
    void takesTooLong(Expect expect) throws InterruptedException {
        Thread.sleep(60);
        expect.that(true, "the slow test still asserts");
    }
}
