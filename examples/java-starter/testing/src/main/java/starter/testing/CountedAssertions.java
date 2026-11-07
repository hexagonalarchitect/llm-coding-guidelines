package starter.testing;

import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.opentest4j.AssertionFailedError;

/**
 * Judge for E22: a test that completes without one counted assertion fails.
 *
 * <p>It hands each test a fresh {@link Expect} and, after the test body returns, fails the test if
 * that {@code Expect} counted nothing, or if the test never asked for one.
 */
public final class CountedAssertions implements ParameterResolver, InvocationInterceptor {

    /** The check id this judge reports under; the meta-test and its breach use the same id. */
    public static final String CHECK_ID = "E22-asserts-something";

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(CHECK_ID);

    /** Creates the judge; JUnit loads it through the service file. */
    public CountedAssertions() {}

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == Expect.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return context.getStore(NAMESPACE).getOrComputeIfAbsent(Expect.class, k -> new Expect(), Expect.class);
    }

    @Override
    public void interceptTestMethod(
            Invocation<Void> invocation, ReflectiveInvocationContext<Method> call, ExtensionContext context)
            throws Throwable {
        invocation.proceed();
        Expect expect = context.getStore(NAMESPACE).get(Expect.class, Expect.class);
        if (expect == null || expect.count() == 0) {
            throw new AssertionFailedError("[" + CHECK_ID + "] " + context.getDisplayName()
                    + " asserted nothing. Take an Expect parameter and assert through it.");
        }
    }
}
