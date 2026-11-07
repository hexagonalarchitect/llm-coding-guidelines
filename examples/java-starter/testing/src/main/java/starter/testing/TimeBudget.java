package starter.testing;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.opentest4j.AssertionFailedError;

/**
 * Judge for E8: each test body must finish within its tier's budget.
 *
 * <p>Tiers come from tags: untagged tests are logic tests (20 ms), {@code @Tag("scenario")} gets
 * 200 ms, {@code @Tag("architecture")} 10 s, and {@code @Tag("nightly")} is not judged here
 * because it runs in the nightly job. The time of the test body alone is measured, not set-up.
 */
public final class TimeBudget implements InvocationInterceptor {

    /** The check id this judge reports under; the meta-test and its breach use the same id. */
    public static final String CHECK_ID = "E8-time-budget";

    /** Set this system property to {@code off} only where code is slow by design (mutation testing). */
    public static final String SWITCH = "starter.budgets";

    /** Creates the judge; JUnit loads it through the service file. */
    public TimeBudget() {}

    @Override
    public void interceptTestMethod(
            Invocation<Void> invocation, ReflectiveInvocationContext<Method> call, ExtensionContext context)
            throws Throwable {
        long start = System.nanoTime();
        invocation.proceed();
        Duration took = Duration.ofNanos(System.nanoTime() - start);
        Duration budget = budget(context.getTags());
        if (!"off".equals(System.getProperty(SWITCH)) && took.compareTo(budget) > 0) {
            throw new AssertionFailedError("[" + CHECK_ID + "] " + context.getDisplayName() + " took "
                    + took.toMillis() + " ms, over its " + budget.toMillis() + " ms budget. Make it faster or tag it"
                    + " for its real tier.");
        }
    }

    private static Duration budget(Set<String> tags) {
        if (tags.contains("nightly")) {
            return Duration.ofDays(1);
        }
        if (tags.contains("architecture")) {
            return Duration.ofSeconds(10);
        }
        if (tags.contains("scenario")) {
            return Duration.ofMillis(200);
        }
        return Duration.ofMillis(20);
    }
}
