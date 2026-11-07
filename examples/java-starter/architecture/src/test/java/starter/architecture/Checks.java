package starter.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import starter.testing.CountedAssertions;
import starter.testing.TimeBudget;

/**
 * The registry of every automated check. The meta-test runs each one against the real code and
 * against every breach, and fails if a check has no breach in {@code breaches/<id>/} (F5).
 */
final class Checks {

    private Checks() {}

    private static final String KERNEL = "starter.kernel..";
    private static final String DOMAIN = "starter.loans.domain..";
    private static final String APPLICATION = "starter.loans.application..";
    private static final String INFRASTRUCTURE = "starter.infrastructure..";
    private static final String HOST = "starter.host..";
    private static final String CODEC = "starter.loans.codec..";

    static List<Check> all() {
        return List.of(layers(), codecLayer(), oneWrapperPerLibrary(), noAmbientStateInCore(), noDefaultArm(),
                noMocking(), assertionsAreCounted(), new Check.Judge(CountedAssertions.CHECK_ID), new Check.Judge(TimeBudget.CHECK_ID));
    }

    /** A1: each layer reaches only the layers below it. */
    static Check layers() {
        return Check.Arch.live("A1-layers",
                resideInAnyPackage(KERNEL, DOMAIN, APPLICATION, INFRASTRUCTURE, HOST),
                layeredArchitecture().consideringOnlyDependenciesInLayers()
                        .withOptionalLayers(true)
                        .layer("Kernel").definedBy(KERNEL)
                        .layer("Domain").definedBy(DOMAIN)
                        .layer("Application").definedBy(APPLICATION)
                        .layer("Infrastructure").definedBy(INFRASTRUCTURE)
                        .layer("Host").definedBy(HOST)
                        .whereLayer("Kernel").mayNotAccessAnyLayer()
                        .whereLayer("Domain").mayOnlyAccessLayers("Kernel")
                        .whereLayer("Application").mayOnlyAccessLayers("Kernel", "Domain", "Infrastructure")
                        .whereLayer("Infrastructure").mayOnlyAccessLayers("Kernel")
                        .whereLayer("Host").mayOnlyAccessLayers("Kernel", "Domain", "Application", "Infrastructure"));
    }

    /** A1, dormant: a codec reaches only the kernel and its own domain. No codec exists yet. */
    static Check codecLayer() {
        return Check.Arch.dormant("A1-codec-layer", "BACKLOG-3 (first persisted form)",
                resideInAPackage(CODEC),
                noClasses().that().resideInAPackage(CODEC)
                        .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, INFRASTRUCTURE, HOST)
                        .because("a codec depends on the kernel and its own domain only"));
    }

    /** A10: a third-party library is used only by its one wrapper. One line per allowed library. */
    static Check oneWrapperPerLibrary() {
        return Check.Arch.live("A10-one-wrapper",
                resideOutsideOfPackage("starter.infrastructure.notices.."),
                noClasses().that().resideOutsideOfPackage("starter.infrastructure.notices..")
                        .should().dependOnClassesThat().resideInAPackage("com.google.gson..")
                        .because("Gson belongs to its wrapper, starter.infrastructure.notices"));
    }

    /** D1, D2: no clock, ambient randomness, or hash-ordered collections in the kernel and domain. */
    static Check noAmbientStateInCore() {
        DescribedPredicate<JavaMethodCall> clockRead = describe("a read of the clock",
                call -> (call.getName().equals("now") && call.getTargetOwner().getPackageName().equals("java.time"))
                        || (call.getTargetOwner().isEquivalentTo(System.class)
                                && List.of("currentTimeMillis", "nanoTime").contains(call.getName()))
                        || (call.getTargetOwner().isEquivalentTo(Math.class) && call.getName().equals("random")));
        return Check.Arch.live("D1-pure-core",
                resideInAnyPackage(KERNEL, DOMAIN),
                noClasses().that().resideInAnyPackage(KERNEL, DOMAIN)
                        .should().dependOnClassesThat().belongToAnyOf(Clock.class, InstantSource.class,
                                Random.class, SecureRandom.class, ThreadLocalRandom.class, UUID.class,
                                HashMap.class, HashSet.class)
                        .orShould().callMethodWhere(clockRead)
                        .because("the core takes time and randomness as parameters and keeps a stable order"));
    }

    /**
     * C12: no {@code default} arm in any {@code switch} of the kernel, domain or application. The core
     * switches only over its own sealed types and enums, so a default arm can only hide a case added
     * later; the compiler checks exhaustiveness without it. A flat source scan, because the compiled
     * class no longer shows whether the source had a default arm.
     */
    static Check noDefaultArm() {
        return new Check.Source("C12-no-default-arm",
                List.of("starter.kernel", "starter.loans.domain", "starter.loans.application"),
                Pattern.compile("\\bdefault\\s*(->|:)"),
                "a default arm in a switch over the system's own types; list every case so a new one is a compile error");
    }

    /** E1: no mocking library anywhere, production or test. Use the Nullable wrappers. */
    static Check noMocking() {
        return Check.Arch.live("E1-no-mocking",
                describe("any class", c -> true),
                noClasses().should().dependOnClassesThat()
                        .resideInAnyPackage("org.mockito..", "org.easymock..", "org.jmock..", "org.powermock..", "io.mockk..")
                        .because("tests use Nullable wrappers (createNull), not mocks"));
    }

    /** E22: tests assert only through the counting module, so every assertion is counted. */
    static Check assertionsAreCounted() {
        DescribedPredicate<JavaClass> tests = describe("test classes", c -> c.getMethods().stream().anyMatch(
                m -> m.isAnnotatedWith("org.junit.jupiter.api.Test") || m.isAnnotatedWith("net.jqwik.api.Property")));
        return Check.Arch.live("E22-counted-assertions",
                tests,
                noClasses().that(tests).should().dependOnClassesThat().haveFullyQualifiedName("org.junit.jupiter.api.Assertions")
                        .orShould().dependOnClassesThat().resideInAnyPackage("org.assertj..", "org.hamcrest..", "org.junit")
                        .because("assert through starter.testing.Expect, which counts"));
    }
}
