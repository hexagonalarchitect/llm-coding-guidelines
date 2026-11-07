package starter.breaches;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import starter.testing.Expect;

/** BREACH of E22-counted-assertions: one assertion bypasses the counting module. */
class RawAssertionTest {

    @Test
    void assertsPastTheCounter(Expect expect) {
        expect.that(true, "the counted assertion passes");
        Assertions.assertEquals(2, 1 + 1);
    }
}
