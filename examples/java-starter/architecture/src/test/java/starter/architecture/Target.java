package starter.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * A body of compiled code a check is run against: the real code, or one planted breach.
 *
 * <p>It is read once, by path: the classes for the architecture checks, the source files for the
 * source scans (C12), and an in-process run of the Jupiter tests among the classes for the test
 * judges (E8, E22), after one warm-up run.
 *
 * @param name what this target is, for messages
 * @param classes the imported classes
 * @param sources the source files
 * @param testsRun how many Jupiter tests ran
 * @param failures the failure messages of the tests that failed
 */
record Target(String name, JavaClasses classes, List<SourceFile> sources, long testsRun, List<String> failures) {

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    /**
     * One source file.
     *
     * @param path where it is
     * @param packageName its {@code package} line, or empty
     * @param text its text
     */
    record SourceFile(Path path, String packageName, String text) {}


    static Target read(String name, List<Path> dirs, List<Path> sourceDirs) {
        JavaClasses classes = new ClassFileImporter().importPaths(dirs);
        List<SourceFile> sources = sourceDirs.stream().flatMap(Target::javaFiles).toList();
        List<String> tests = classes.stream().filter(Target::isJupiterTest).map(JavaClass::getName).sorted().toList();
        if (tests.isEmpty()) {
            return new Target(name, classes, sources, 0, List.of());
        }
        try (URLClassLoader loader = new URLClassLoader(urls(dirs), Target.class.getClassLoader())) {
            DiscoverySelector[] selectors = tests.stream()
                    .map(test -> DiscoverySelectors.selectClass(load(test, loader)))
                    .toArray(DiscoverySelector[]::new);
            // Run the tests twice and judge the second run. The first run loads and JIT-compiles the
            // target's classes in this JVM, so a breach meant for another check (an E22 test that
            // asserts nothing) cannot also trip the 20 ms logic budget on a cold start and fire E8.
            run(selectors);
            EngineExecutionResults run = run(selectors);
            List<String> failures = run.testEvents().failed().stream()
                    .flatMap(event -> event.getPayload(TestExecutionResult.class).stream())
                    .flatMap(result -> result.getThrowable().stream())
                    .map(thrown -> String.valueOf(thrown.getMessage()))
                    .toList();
            return new Target(name, classes, sources, run.testEvents().started().count(), failures);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<SourceFile> javaFiles(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(file -> file.toString().endsWith(".java")).sorted().map(Target::sourceFile).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SourceFile sourceFile(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher declared = PACKAGE.matcher(text);
            String packageName = declared.find() ? Objects.requireNonNullElse(declared.group(1), "") : "";
            return new SourceFile(file, packageName, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static EngineExecutionResults run(DiscoverySelector[] selectors) {
        return EngineTestKit.engine("junit-jupiter")
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .selectors(selectors)
                .execute();
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
