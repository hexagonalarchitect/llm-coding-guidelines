package starter.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * A body of compiled code a check is run against: the real code, or one planted breach.
 *
 * <p>It is read once, by path: the classes for the architecture checks, and one in-process run of
 * the Jupiter tests among them for the test judges (E8, E22).
 *
 * @param name what this target is, for messages
 * @param classes the imported classes
 * @param testsRun how many Jupiter tests ran
 * @param failures the failure messages of the tests that failed
 */
record Target(String name, JavaClasses classes, long testsRun, List<String> failures) {

    static Target read(String name, List<Path> dirs) {
        JavaClasses classes = new ClassFileImporter().importPaths(dirs);
        List<String> tests = classes.stream().filter(Target::isJupiterTest).map(JavaClass::getName).sorted().toList();
        if (tests.isEmpty()) {
            return new Target(name, classes, 0, List.of());
        }
        try (URLClassLoader loader = new URLClassLoader(urls(dirs), Target.class.getClassLoader())) {
            DiscoverySelector[] selectors = tests.stream()
                    .map(test -> DiscoverySelectors.selectClass(load(test, loader)))
                    .toArray(DiscoverySelector[]::new);
            EngineExecutionResults run = EngineTestKit.engine("junit-jupiter")
                    .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                    .selectors(selectors)
                    .execute();
            List<String> failures = run.testEvents().failed().stream()
                    .flatMap(event -> event.getPayload(TestExecutionResult.class).stream())
                    .flatMap(result -> result.getThrowable().stream())
                    .map(thrown -> String.valueOf(thrown.getMessage()))
                    .toList();
            return new Target(name, classes, run.testEvents().started().count(), failures);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isJupiterTest(JavaClass type) {
        return type.getMethods().stream().anyMatch(m -> m.isAnnotatedWith("org.junit.jupiter.api.Test"));
    }

    private static Class<?> load(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("cannot load test class " + name, e);
        }
    }

    private static URL[] urls(List<Path> dirs) {
        return dirs.stream().map(dir -> {
            try {
                return dir.toUri().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }).toArray(URL[]::new);
    }
}
