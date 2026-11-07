package starter.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.Optional;

/**
 * One automated check. Every check has an id, a planted breach in {@code breaches/<id>/}, and
 * reports how much code it selected as well as what it found (F5).
 */
sealed interface Check {

    /** The check id: a rule id plus a short name, e.g. {@code A1-layers}. */
    String id();

    /** The backlog item in which this check's target code appears, while there is none yet. */
    Optional<String> dormantUntil();

    /** Runs the check. */
    Verdict evaluate(Target target);

    /**
     * What a check saw and found on one target.
     *
     * @param selected how many classes (or tests) the check looked at
     * @param findings the violations; empty means the check passed
     */
    record Verdict(long selected, List<String> findings) {}

    /**
     * An architecture check written with ArchUnit, over the classes it selects.
     *
     * @param id the check id
     * @param dormantUntil the backlog item that wakes it, if dormant
     * @param selects the classes the rule is about; empty on the real code means the check is vacuous
     * @param rule the rule
     */
    record Arch(String id, Optional<String> dormantUntil, DescribedPredicate<JavaClass> selects, ArchRule rule)
            implements Check {

        static Arch live(String id, DescribedPredicate<JavaClass> selects, ArchRule rule) {
            return new Arch(id, Optional.empty(), selects, rule.as("[" + id + "] " + rule.getDescription()));
        }

        static Arch dormant(String id, String until, DescribedPredicate<JavaClass> selects, ArchRule rule) {
            return new Arch(id, Optional.of(until), selects, rule.as("[" + id + "] " + rule.getDescription()));
        }

        @Override
        public Verdict evaluate(Target target) {
            long selected = target.classes().stream().filter(selects).count();
            List<String> findings = rule.allowEmptyShould(true).evaluate(target.classes()).getFailureReport().getDetails();
            return new Verdict(selected, findings);
        }
    }

    /**
     * A test-runner judge (a JUnit extension in {@code :testing}) that fails a test from inside.
     * It selects the tests that ran and finds the failures that carry its id.
     *
     * @param id the check id, which the judge puts at the start of its failure message
     */
    record Judge(String id) implements Check {

        @Override
        public Optional<String> dormantUntil() {
            return Optional.empty();
        }

        @Override
        public Verdict evaluate(Target target) {
            List<String> findings = target.failures().stream().filter(m -> m.startsWith("[" + id + "]")).toList();
            return new Verdict(target.testsRun(), findings);
        }
    }
}
