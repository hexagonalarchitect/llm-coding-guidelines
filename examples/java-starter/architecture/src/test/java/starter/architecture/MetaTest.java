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
    private static final Target REAL = Target.read("the real code", paths(System.getProperty("starter.real", "")));
    private static final Path BREACHES = Path.of(System.getProperty("starter.breaches", "breaches"));
    private static final Map<String, Target> BREACH_TARGETS = readBreaches();

    @Test
    void everyCheckHasExactlyOneBreachAndEveryBreachHasACheck(Expect expect) {
        expect.equal(new TreeSet<>(CHECKS.stream().map(Check::id).toList()), new TreeSet<>(BREACH_TARGETS.keySet()));
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
        return Target.read("breach " + id, List.of(classes));
    }

    private static List<Path> paths(String joined) {
        return Arrays.stream(joined.split(File.pathSeparator))
                .filter(path -> !path.isEmpty())
                .map(Path::of)
                .filter(Files::isDirectory)
                .toList();
    }
}
