package starter.testing;

import java.util.Objects;
import org.opentest4j.AssertionFailedError;

/**
 * The counting assertion module (E22). Tests receive one as a parameter and assert only through
 * it; {@link CountedAssertions} fails any test whose {@code Expect} counted no assertion.
 *
 * <p>Tests never call the framework's assertion functions directly: an architecture check holds
 * that, so every assertion is counted.
 */
public final class Expect {

    private int count;

    Expect() {}

    /**
     * Asserts that a claim holds.
     *
     * @param condition the claim's truth
     * @param claim what was claimed, for the failure message
     */
    public void that(boolean condition, String claim) {
        count++;
        if (!condition) {
            throw new AssertionFailedError("expected that " + claim);
        }
    }

    /**
     * Asserts that two values are equal.
     *
     * @param expected the expected value
     * @param actual the actual value
     * @param <T> the value type
     */
    public <T> void equal(T expected, T actual) {
        count++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionFailedError("expected " + expected + " but was " + actual, expected, actual);
        }
    }

    int count() {
        return count;
    }
}
