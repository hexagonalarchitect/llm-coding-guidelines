package starter.architecture;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import starter.testing.Expect;

/**
 * The self-test harness (F5): every check passes on the real code and looks at something there,
 * and every planted breach fires its own check and no other.
 */
@Tag("architecture")
class MetaTest {

    private static final List<Check> CHECKS = Checks.all();
    private static final Target REAL = Target.read("the real code", paths(System.getProperty("starter.real", "")),
            paths(System.getProperty("starter.realSources", "")));
    private static final Path BREACHES = Path.of(System.getProperty("starter.breaches", "breaches"));
    private static final Map<String, Target> BREACH_TARGETS = readBreaches();
    private static final Path RULES = Path.of(System.getProperty("starter.rules", "standards/rules.toml"));

    /** The runners whose checks live in the check registry; a live row of another runner names none. */
    private static final List<String> REGISTRY_RUNNERS = List.of("architecture", "inventory");

    /** The statuses at which a check exists. */
    private static final List<String> CHECKED = List.of("built", "partial", "dormant");

    private static final Pattern FIELD = Pattern.compile("^(id|status|check|checks)\\s*=\\s*(.*)$");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

    /**
     * One rule row, as far as this test reads it.
     *
     * @param id the rule id
     * @param status its status
     * @param runner the runner that owns its check (the row's runner field)
     * @param checks the check ids that hold it
     */
    private record Row(String id, String status, String runner, List<String> checks) {}

    @Test
    void everyCheckHasExactlyOneBreachAndEveryBreachHasACheck(Expect expect) {
        expect.equal(new TreeSet<>(CHECKS.stream().map(Check::id).toList()), new TreeSet<>(BREACH_TARGETS.keySet()));
    }

    @Test
    void theRuleTableNamesEveryCheckAndOnlyRegisteredOnes(Expect expect) {
        List<String> registered = CHECKS.stream().map(Check::id).toList();
        List<Row> rows = readRules();
        List<String> problems = new ArrayList<>();
        for (Row row : rows) {
            for (String named : row.checks()) {
                if (!registered.contains(named)) {
                    problems.add(row.id() + " names " + named + ", which is not in the check registry");
                }
            }
            if (CHECKED.contains(row.status()) && REGISTRY_RUNNERS.contains(row.runner()) && row.checks().isEmpty()) {
                problems.add(row.id() + " is " + row.status() + " but names no check in `checks`");
            }
        }
        for (String id : registered) {
            if (rows.stream().noneMatch(row -> row.checks().contains(id))) {
                problems.add("check " + id + " is named by no rule's `checks` in " + RULES);
            }
        }
        expect.that(rows.size() > 100, "the rule table at " + RULES + " was read");
        expect.equal(List.of(), problems);
    }

    @Test
    void everyCheckPassesOnTheRealCodeAndSelectsSomethingThere(Expect expect) {
        List<String> problems = new ArrayList<>();
        for (Check check : CHECKS) {
            Check.Verdict verdict = check.evaluate(REAL);
            if (!verdict.findings().isEmpty()) {
                problems.add(check.id() + " fails on the real code: " + verdict.findings());
            }
            if (check.dormantUntil().isEmpty() && verdict.selected() == 0) {
                problems.add(check.id() + " is vacuous: it selects nothing in the real code");
            }
            if (check.dormantUntil().isPresent() && verdict.selected() > 0) {
                problems.add(check.id() + " is dormant until " + check.dormantUntil().get() + " but now selects "
                        + verdict.selected() + " classes: wake it up");
            }
        }
        expect.equal(List.of(), problems);
    }

    @Test
    void everyBreachFiresItsOwnCheckAndNoOther(Expect expect) {
        List<String> problems = new ArrayList<>();
        BREACH_TARGETS.forEach((breach, target) -> {
            for (Check check : CHECKS) {
                boolean fired = !check.evaluate(target).findings().isEmpty();
                boolean own = check.id().equals(breach);
                if (own && !fired) {
                    problems.add("breach " + breach + " does not fire its own check");
                }
                if (!own && fired) {
                    problems.add("breach " + breach + " also fires " + check.id());
                }
            }
        });
        expect.equal(List.of(), problems);
    }

    /**
     * Reads the [[rule]] rows of the rule table: a flat line reader for the four fields it needs, each
     * on one line, rather than a TOML library in the test classpath.
     */
    private static List<Row> readRules() {
        List<String> lines;
        try {
            lines = Files.readAllLines(RULES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Row> rows = new ArrayList<>();
        Map<String, String> fields = new TreeMap<>();
        boolean inRule = false;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("[")) {
                if (inRule) {
                    rows.add(row(fields));
                }
                inRule = line.equals("[[rule]]");
                fields.clear();
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (inRule && field.matches()) {
                fields.put(String.valueOf(field.group(1)), String.valueOf(field.group(2)));
            }
        }
        if (inRule) {
            rows.add(row(fields));
        }
        return rows;
    }

    private static Row row(Map<String, String> fields) {
        List<String> checks = new ArrayList<>();
        Matcher quoted = QUOTED.matcher(fields.getOrDefault("checks", ""));
        while (quoted.find()) {
            checks.add(String.valueOf(quoted.group(1)));
        }
        return new Row(unquote(fields.getOrDefault("id", "")), unquote(fields.getOrDefault("status", "")),
                unquote(fields.getOrDefault("check", "")), checks);
    }

    private static String unquote(String value) {
        return value.replace("\"", "").strip();
    }

    private static Map<String, Target> readBreaches() {
        try (Stream<Path> folders = Files.list(BREACHES)) {
            return folders.filter(folder -> Files.isDirectory(folder.resolve("src")))
                    .map(folder -> folder.getFileName().toString())
                    .collect(Collectors.toMap(Function.identity(), MetaTest::readBreach, (a, b) -> a, TreeMap::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Target readBreach(String id) {
        Path classes = BREACHES.resolve("build/classes/java").resolve(id.replace('-', '_'));
        if (!Files.isDirectory(classes)) {
            throw new IllegalStateException("breach " + id + " has no compiled classes at " + classes);
        }
        return Target.read("breach " + id, List.of(classes), List.of(BREACHES.resolve(id).resolve("src")));
    }

    private static List<Path> paths(String joined) {
        return Arrays.stream(joined.split(File.pathSeparator))
                .filter(path -> !path.isEmpty())
                .map(Path::of)
                .filter(Files::isDirectory)
                .toList();
    }
}
