package starter.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * A flat source scan: a forbidden form, matched in the source text of the files in the listed
     * packages, after comments and string literals are blanked out. It is for forms the compiled
     * classes no longer show, such as a {@code default} arm of a {@code switch} (C12).
     *
     * @param id the check id
     * @param packages the packages it selects, by each file's package declaration (sub-packages included)
     * @param forbidden the form it forbids
     * @param instead what to write instead, for the failure message
     */
    record Source(String id, List<String> packages, Pattern forbidden, String instead) implements Check {

        private static final Pattern NOT_CODE =
                Pattern.compile("/\\*.*?\\*/|//[^\n]*|\"(?:\\\\.|[^\"\\\\\n])*\"|'(?:\\\\.|[^'\\\\\n])'", Pattern.DOTALL);

        @Override
        public Optional<String> dormantUntil() {
            return Optional.empty();
        }

        @Override
        public Verdict evaluate(Target target) {
            long selected = 0;
            List<String> findings = new ArrayList<>();
            for (Target.SourceFile file : target.sources()) {
                if (packages.stream().noneMatch(p -> file.packageName().equals(p) || file.packageName().startsWith(p + "."))) {
                    continue;
                }
                selected++;
                String code = codeOnly(file.text());
                Matcher found = forbidden.matcher(code);
                while (found.find()) {
                    long line = code.substring(0, found.start()).chars().filter(c -> c == '\n').count() + 1;
                    findings.add("[" + id + "] " + file.path() + ":" + line + ": " + instead);
                }
            }
            return new Verdict(selected, findings);
        }

        /** Blanks comments, string and character literals, keeping every line break so line numbers hold. */
        private static String codeOnly(String text) {
            Matcher notCode = NOT_CODE.matcher(text);
            StringBuilder code = new StringBuilder();
            while (notCode.find()) {
                notCode.appendReplacement(code, notCode.group().replaceAll("[^\n]", ""));
            }
            notCode.appendTail(code);
            return code.toString();
        }
    }
}
