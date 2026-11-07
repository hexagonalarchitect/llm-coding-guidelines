# Implementing the rules in Java

This page tells an agent working on a Java code base how to build an automated check for every rule in the [guide](../../README.md) that has one, without the reference Rust code base. It follows the guide's group order (F, H, I, A, C, D, G, E, B, M, P, R, S, V), then covers the shared runners, the planted-breach meta-test and gate speed.

**The Java starter.** [`examples/java-starter/`](../../examples/java-starter/README.md) is a small, runnable Gradle project that compiles and runs the core of this page: the layers, a decider, a constrained value, a Nullable wrapper, ArchUnit checks with planted breaches and the meta-test, a source scan for `C12`, the counting-assertion and time-budget judges, jqwik, PIT, Error Prone, NullAway and dependency locking. Its `scripts/gate.sh` runs green. Where this page and the starter differ, the starter's code is the one that has been compiled and run; each such place below says what the starter does and when the alternative is worth it. Snippets marked *(not compiled)* were written for this page and have not been built; check them against your pinned tool versions. [docs/quick-start.md](../quick-start.md) walks through the starter step by step.

**Assumptions.** Java 21 or later. The starter uses Java 21, so it names unused pattern bindings (`case Return ignored ->`); the snippets on this page use Java 25 (the current LTS release), where the unnamed pattern `_` (Java 22 or later) replaces them. Gradle with the Kotlin DSL is the main build tool, and Maven equivalents follow each Gradle snippet where they differ. Versions in snippets are placeholders. Pin your own exact versions in the version catalog, each at least 14 days old (`H4`). Where this page is not sure of an exact option name, it says so. Check that option against the tool's documentation for your pinned version before relying on it.

**Status words used below.** **auto** means a tool or test fails the gate. **report** means a tool reports the finding but does not fail the gate, as the rule asks. **review** means the model reviewer or a person holds the rule (each one says why). **process** means a script or routine holds it, and **neutral** means a language-neutral runner that works the same in any language.

---

## 0. Project shape: layers are Gradle projects, and convention plugins scope the rules

Most rules apply to some layers only, such as "in `Kernel` and `Domain`". In Java, scope them by build structure rather than by clever matching:

1. **One Gradle project (or Maven module) per package in the layer table**, at the same paths the guide uses:

   ```
   kernel/                          -> Kernel
   contexts/loans/contracts/        -> Contracts (context "loans")
   contexts/loans/domain/           -> Domain
   contexts/loans/codec/            -> Codec
   contexts/loans/application/      -> Application
   day/                             -> Day (cross-context composition)
   infrastructure/orderstore/       -> Infrastructure
   legacy-import/ , hosts/import/   -> Import, ImportHost (only when reimplementing a reference program)
   hosts/server/                    -> Host
   tests/architecture/ , tests/...  -> Test
   breaches/**                      -> Breach (no production project depends on it; see section 15)
   ```

   The starter uses a flatter version of this shape for one context: `kernel/`, `loans/domain/`, `loans/application/`, `infrastructure/notices/`, `host/`, plus `testing/` (test support), `architecture/` (the checks and the meta-test) and `breaches/`. The paths in [templates/rules.toml](../../templates/rules.toml)'s `gradle` patterns are the ones above; change either to match the other.

2. **One Java package root per project** that mirrors the path, so bytecode-level checks can also select by package: `com.example.kernel`, `com.example.contexts.loans.domain`, `com.example.infrastructure.orderstore`, and so on. (`import` is a Java keyword, so the import layer's package is `com.example.legacyimport`.)

3. **One convention plugin per layer** in an included build (`build-logic/`) once the project has more than a handful of projects (the starter, being small, configures every project from one `subprojects {}` block in its root `build.gradle.kts`), such as `lcg.kernel`, `lcg.domain`, `lcg.contracts`, `lcg.codec`, `lcg.application`, `lcg.day`, `lcg.infrastructure`, `lcg.host` and `lcg.test`. Each applies a shared `lcg.java-base` plugin (toolchain, compiler flags, Error Prone, formatter, locking) and then points every per-file tool at that layer's configuration: `config/checkstyle/<layer>.xml`, `config/forbidden/<layer>.txt`, `config/pmd/<layer>.xml`, and the Error Prone check list for that layer. A rule "in `Kernel` and `Domain`" becomes a line in those two layers' configuration files, with no path matching in the check itself.

4. **Architecture tests** live in `tests/architecture` (the starter's `architecture/`), which depends on every production project (the `Test` row allows `*`). They run ArchUnit over the compiled classes, together with flat text checks over build files and source. Give every check an id made of its rule id and a short name, and put the id at the start of its failure message, so that a failure names the rule, the offending edge, the allowed alternative and the decision record (`F5`). The starter registers each check under such an id (`A1-layers`, `A10-one-wrapper`, `C12-no-default-arm`) in one registry, `Checks.all()`, and prefixes its ArchUnit rule's description with `[A1-layers]`; the snippets below use ArchUnit's `.as("A10 ...").because("... see ADR-0012")`, which does the same for a rule outside such a registry.

5. **Optionally, use JPMS** (`module-info.java`) in production projects. `requires` makes the compiler enforce the project graph a second time, and `exports` is Java's explicit export list (`C10`).

```kotlin
// settings.gradle.kts
pluginManagement { includeBuild("build-logic") }
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS   // repositories declared once, centrally (H2)
    repositories { mavenCentral() }
}
include(":kernel", ":contexts:loans:contracts", ":contexts:loans:domain", ":contexts:loans:codec",
        ":contexts:loans:application", ":day", ":infrastructure:orderstore", ":hosts:server",
        ":tests:architecture")
```

The tools used below are these:

- **javac** with `-Xlint:all -Werror` and `-Xdoclint`.
- **Error Prone** (Gradle plugin `net.ltgt.errorprone`), with **NullAway** and a small set of **custom Error Prone checks** in `tools/errorprone-checks`.
- **Checkstyle** and **PMD** (core Gradle plugins), and **Spotless** with google-java-format.
- **forbidden-apis** (`de.thetaphi.forbiddenapis`), which bans methods and classes in the bytecode.
- **ArchUnit**, which checks structure and signatures in the bytecode.
- **JUnit 5 (Jupiter)**, **jqwik**, **PIT** (`info.solidsoft.pitest`) and **JaCoCo** for tests and coverage.
- **OWASP dependency-check** or **osv-scanner** for the nightly audit.
- The guide's language-neutral **history**, **inventory**, **supply-chain** and **model-review** runners, with JVM inputs described in [section 14](#14-the-runners-in-a-jvm-project).

---

## 1. Group F: compiler hygiene and self-proving rules

| Rule | Java check | Status |
| :--- | :--- | :--- |
| **F1** | javac `-Xlint:all -Werror` set once in `lcg.java-base`. Error Prone runs with `disableAllChecks = true` plus an explicit, pinned list of kept checks. Spotless with a pinned google-java-format version runs as `spotlessCheck`. Checkstyle runs with `maxErrors = 0`. A flat test fails if any project build file touches compiler arguments or suppresses a tool. Every `@SuppressWarnings` must match an `[allow]` entry. For overflow checking, see the note below the table. | auto |
| **F2** | The history check scans committed files for reference-product assets (by extension, size and a hash list). Quoted proprietary words in strings and comments are left to the model reviewer. | auto (+ review) |
| **F3** | Checkstyle `TodoComment` with a pattern for any `TODO` that lacks a backlog id. A neutral script finds rule and decision ids cited in comments and checks that each exists. | auto |
| **F4** | History check, which is language-neutral. | neutral |
| **F5** | The breach meta-test (see [section 15](#15-planted-breaches-and-the-meta-test-f5)). Each check counts what it selects, and the meta-test fails a live check that selects nothing on the real code and a dormant one that selects something; ArchUnit's own empty-selection guard is turned off (`allowEmptyShould(true)`), because a breach target has no classes in most layers. | auto |
| **F6** | forbidden-apis `jdk-system-out` and a ban on `Throwable#printStackTrace()` catch debug leftovers. Error Prone `UnusedVariable` and `UnusedMethod` catch private dead code. A Checkstyle `RegexpSinglelineJava` heuristic catches commented-out code. ArchUnit reports public methods that only test classes call. Flag expiry and same-commit deletion of dead code go to the history check. | auto (+ report) |

**F1 (overflow).** Java has no switch that traps integer overflow, because `int` and `long` arithmetic wraps silently. The nearest you can get:

- In `Kernel` and `Domain`, a custom Error Prone check rejects `+`, `-`, `*`, unary `-`, `++` and `--` on `int` and `long` outside the kernel's checked-arithmetic class. All arithmetic then goes through `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact` and `Math.toIntExact`, or through kernel wrappers that return a `Result`. `Math.*Exact` throws `ArithmeticException` on overflow, which is the Java counterpart of Rust's overflow panic.
- Elsewhere, enable the same check if you can afford it. Otherwise record the gap as an `[allow]` entry with a reason.

```kotlin
// build-logic/src/main/kotlin/lcg.java-base.gradle.kts (excerpt)
import net.ltgt.gradle.errorprone.errorprone

plugins { java; id("net.ltgt.errorprone"); id("com.diffplug.spotless"); checkstyle; pmd; id("de.thetaphi.forbiddenapis") }

java { toolchain { languageVersion = JavaLanguageVersion.of(25); vendor = JvmVendorSpec.ADOPTIUM } }

dependencies {
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)
    errorprone(project(":tools:errorprone-checks"))   // house checks; see C6, C12, E22
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        disableAllChecks = true                      // F1: the kept list below is the whole linter configuration
        error("NullAway", "CheckReturnValue", "MissingCasesInEnumSwitch", "ReferenceEquality",
              "UnusedVariable", "UnusedMethod", "EmptyCatch", "CatchAndPrintStackTrace", "Var")
        option("NullAway:OnlyNullMarked", "true")    // NullAway reads JSpecify @NullMarked packages
    }
}

spotless { java { googleJavaFormat(libs.versions.googleJavaFormat.get()) } }   // spotlessCheck runs in `check`

checkstyle { toolVersion = libs.versions.checkstyle.get(); maxErrors = 0; maxWarnings = Int.MAX_VALUE }
```

The starter takes a simpler first step: Error Prone's default checks, which `-Werror` turns into errors, plus NullAway as an error with `option("NullAway:AnnotatedPackages", "starter")` (NullAway 0.12.4 has no `OnlyNullMarked` option). The pinned list above is the stricter form `F1` asks for once the project grows: every kept check is named.

Each layer plugin then adds its own entries to the error list (for example `error("CoreDivision", "CoreArithmetic")` in `lcg.kernel` and `lcg.domain`) and sets `checkstyle.configFile` to its layer's file.

- **`-Xlint:all` includes `processing`.** With annotation processors this group can warn noisily ("No processor claimed any of these annotations"). If you turn it off (`-Xlint:all,-processing`), record that as an `[allow]` entry.
- **The plugin handles JDK internals.** The `net.ltgt.errorprone` plugin adds the `--add-exports` and `--add-opens` flags that Error Prone needs on JDK 16 and later.
- **Older NullAway versions** use `NullAway:AnnotatedPackages` (a package prefix) instead of `OnlyNullMarked`. Which option your pinned version supports is something to check in its documentation.

**Narrow suppressions (F1).** Checkstyle's `SuppressWarningsHolder` and `SuppressWarnings` checks can flag every `@SuppressWarnings`, and the suppressions XML file is the allow list. Each suppression needs a matching `[allow]` entry with the rule id and decision record. The `I5` judge (see [section 3](#3-group-i-governance)) proves each one is still needed. A flat test reads every `build.gradle.kts` below the root and fails on any of these strings: `compilerArgs`, `errorprone {`, `isIgnoreFailures`, `ignoreFailures`, `maxWarnings`, `disable(` and `-Xlint:-`. Only `build-logic/` may configure tools.

**Maven.** Set `maven-compiler-plugin` `<release>25</release>`, `<failOnWarning>true</failOnWarning>` and `<compilerArgs><arg>-Xlint:all</arg><arg>-XDcompilePolicy=simple</arg><arg>-Xplugin:ErrorProne -XepDisableAllChecks -Xep:NullAway:ERROR ...</arg></compilerArgs>`, with Error Prone on `<annotationProcessorPaths>`. Put the `--add-exports` lines in `.mvn/jvm.config`, as Error Prone's installation page shows. Spotless has a Maven plugin, and `maven-checkstyle-plugin` takes `<failOnViolation>true</failOnViolation>`.

**F3 (TODO).** In each layer's Checkstyle file:

```xml
<module name="TodoComment">
  <property name="id" value="F3"/>
  <property name="format" value="TODO(?!\(BL-\d+\))"/>   <!-- flags a TODO not followed by (BL-<n>) -->
</module>
```

Keep other ids out of the rule-id shape, as `F3` says. The id scanner (any word-bounded group letter plus one or two digits) is a neutral script over `src/**/*.java` comments.

---

## 2. Group H: supply chain and reproducible builds

| Rule | Java check | Status |
| :--- | :--- | :--- |
| **H1** | The Gradle wrapper is pinned with `distributionSha256Sum`. Toolchain auto-download is off, and an exact JDK path and version are pinned. A `verifyJdk` task fails on any other JDK. Under Maven, use the Maven Wrapper and enforcer `requireJavaVersion` with an exact range `[25.0.1]`. | auto |
| **H2** | The version catalog `gradle/libs.versions.toml` is the only place a version appears, and every version is exact. `dependencyLocking { lockAllConfigurations(); lockMode = LockMode.STRICT }` and committed `gradle.lockfile`s lock the graph. `failOnVersionConflict()`, `failOnDynamicVersions()`, `failOnChangingVersions()` and `failOnNonReproducibleResolution()` make resolution problems errors. `gradle/verification-metadata.xml` holds SHA-256 checksums. The tool versions (Checkstyle, PMD, Error Prone, google-java-format, PIT, JaCoCo) come from the same catalog and are locked like any other configuration. | auto |
| **H4** | The supply runner reads every coordinate in the lockfiles and checks its publish date on Maven Central (14 days or more). The history check requires the licence and reason lines in the commit message. Renovate's `minimumReleaseAge: "14 days"` keeps update proposals in line. | auto (neutral runner) |
| **H5** | Nightly OWASP dependency-check (`dependencyCheckAnalyze`, `failBuildOnCVSS = 7.0f`) or osv-scanner over the lockfiles. | auto, nightly |
| **H6** | Archive tasks are reproducible (`isPreserveFileTimestamps = false`, `isReproducibleFileOrder = true`). Each night, two clean clones at different paths run `--offline --no-build-cache` after restore and compare the SHA-256 of every artefact. Under Maven, set `project.build.outputTimestamp` and use `artifact:compare`. | auto, nightly |
| **H9** | A release task (supply runner, `on-demand`) refuses a commit without a recorded green full gate, writes a software bill of materials (the CycloneDX Gradle plugin, `org.cyclonedx.bom`, or Maven `cyclonedx-maven-plugin`) beside the SHA-256 of every artefact in `build/distributions`, and records the lockfile diff since the last release tag. The release checklist's questions are judged by a person or the reviewer. | planned |

```kotlin
// lcg.java-base (continued)
dependencyLocking { lockAllConfigurations(); lockMode = LockMode.STRICT }   // import org.gradle.api.artifacts.dsl.LockMode
configurations.configureEach {
    resolutionStrategy {
        failOnVersionConflict(); failOnDynamicVersions(); failOnChangingVersions(); failOnNonReproducibleResolution()
    }
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
```

Use these commands to write and restore the lock state:

- `./gradlew dependencies --write-locks` writes the lockfiles only for the configurations that task resolves. To lock every configuration, the starter registers a task in each project and runs `./gradlew resolveAndLockAll --write-locks`:

  ```kotlin
  // examples/java-starter/build.gradle.kts, inside subprojects { } (compiled)
  dependencyLocking { lockAllConfigurations(); lockMode = LockMode.STRICT }
  tasks.register("resolveAndLockAll") {
      notCompatibleWithConfigurationCache("resolves configurations at execution time")
      doFirst { require(gradle.startParameter.isWriteDependencyLocks) { "run with --write-locks" } }
      doLast { configurations.filter { it.isCanBeResolved }.forEach { it.resolve() } }
  }
  ```

  It also locks the plugin classpath with `buildscript { configurations.classpath { resolutionStrategy.activateDependencyLocking() } }` in the root build file, which writes `buildscript-gradle.lockfile`. The starter does not yet use `failOn*` resolution rules, verification metadata or an exact-JDK check.
- `./gradlew --write-verification-metadata sha256 help` writes the checksum file.
- The gate never passes `--write-locks`, and a changed lockfile in the gate's working tree fails the gate.

Lock the plugin classpath of `build-logic` the same way, because `build-logic` is a build of its own with its own lockfiles.

**H1, the exact JDK.** A Gradle toolchain pins only the major version and the vendor, and auto-provisioning (the foojay resolver) downloads whichever patch release is newest, which is a floating version. To pin exactly:

- Turn auto-download off with `org.gradle.java.installations.auto-download=false` in `gradle.properties`.
- Point Gradle at an installed JDK with `org.gradle.java.installations.paths=/opt/jdk-25.0.1`.
- Check the result with a task:

```kotlin
val verifyJdk by tasks.registering {
    val launcher = javaToolchains.launcherFor(java.toolchain)
    val pinned = providers.fileContents(rootProject.layout.projectDirectory.file(".jdk-version")).asText.map { it.trim() }
    doLast {
        val actual = launcher.get().metadata.javaRuntimeVersion
        check(actual == pinned.get()) { "H1: JDK $actual is not the pinned ${pinned.get()} (see .jdk-version)" }
    }
}
tasks.named("check") { dependsOn(verifyJdk) }
```

The JVM that runs Gradle itself can be pinned with daemon JVM criteria (`./gradlew updateDaemonJvm --jvm-version=25`, Gradle 8.8 or later), but that criterion is a major version only. In CI, install the exact JDK build (for example `actions/setup-java` with a full version string).

**H2, central versions with no overrides.** A flat test fails if any `build.gradle.kts` outside `build-logic/` contains a quoted coordinate with a version (a regex such as `"[\w.-]+:[\w.-]+:\d`). Dependencies must come from `libs.*`. To force a transitive version, add an entry with `{ strictly = "x.y.z" }` in the catalog, never a module-local override. Maven has no native lockfile:

- Use maven-enforcer rules `requirePluginVersions`, `dependencyConvergence`, `requireUpperBoundDeps` and `banDynamicVersions`. `banDynamicVersions` is in enforcer 3.2.1 or later, as far as this page knows.
- Run `mvn -C` (strict checksums) in the gate.
- For a lockfile with checksums, use a third-party plugin such as `io.github.chains-project:maven-lockfile` (goals `generate` and `validate`).

Gradle's native locking and verification are stronger, so prefer Gradle for these rules.

**H4, publish dates.** The supply runner turns each `group:artifact:version` line of every `gradle.lockfile` into a POM URL, for example `https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.46.1.0/sqlite-jdbc-3.46.1.0.pom`, and reads its `Last-Modified` header. Search's `core=gav` JSON response also has a `timestamp` field, in milliseconds. Plugins from `plugins.gradle.org/m2` work the same way. Any version newer than 14 days at the adopting commit fails.

**H5, the nightly audit.** Run one of these:

```kotlin
// root build.gradle.kts, applied only in the nightly job
plugins { id("org.owasp.dependencycheck") }
dependencyCheck {
    failBuildOnCVSS = 7.0f                     // high (7.0) and critical (9.0) fail
    nvd { apiKey = providers.environmentVariable("NVD_API_KEY").orNull }
}
```

The alternative is osv-scanner, which reads `gradle.lockfile`, `gradle/verification-metadata.xml` and `pom.xml`:

```
osv-scanner scan source --lockfile=gradle.lockfile --format=json > osv.json   # v2 syntax; v1 is `osv-scanner --lockfile=...`
```

This page knows of no osv-scanner flag that sets a severity threshold, so a short judge reads the JSON and fails when a finding's CVSS score is 7.0 or more. Keep the audit out of the commit gate, as the rule asks.

**H6, the nightly reproducibility check.**

```bash
set -euo pipefail
for d in /tmp/repro-a /tmp/repro-b-at-another/path; do
  git clone --quiet "$REPO" "$d" && (cd "$d" && ./gradlew --quiet dependencies >/dev/null)   # restore
done
for d in /tmp/repro-a /tmp/repro-b-at-another/path; do
  (cd "$d" && ./gradlew --offline --no-build-cache --no-configuration-cache assemble)       # network off after restore
done
diff <(cd /tmp/repro-a && find . -path '*/build/libs/*' -type f -exec sha256sum {} + | sort -k2) \
     <(cd /tmp/repro-b-at-another/path && find . -path '*/build/libs/*' -type f -exec sha256sum {} + | sort -k2)
```

javac records only source file names (not absolute paths) in class files, so the clone paths do not leak into the artefacts. Gradle has no build mode equivalent to a "continuous-integration build" switch, so the archive settings above and `--offline` are the closest match. Under Maven, set `<project.build.outputTimestamp>` and run `mvn clean verify artifact:compare` (maven-artifact-plugin).

---

## 3. Group I: governance

| Rule | Java check | Status |
| :--- | :--- | :--- |
| **I1** | A neutral script lints the rule table: each row names its qualities, cadence, scope and decision record. | auto (neutral) |
| **I2**, **I3** | History check (decision records; commits that touch `settings.gradle.kts`, any `dependencies {}` block, the version catalog, a `contracts` project's public surface file, or the rule table must cite a decision record). | neutral |
| **I5** | The allow-list judge runs each tool with its suppressions switched off: Checkstyle without the suppression filter, Error Prone without `@SuppressWarnings` (a custom check can list them), and ArchUnit with `FreezingArchRule` stores. It fails on any allow entry that excuses nothing, or whose review-by date has passed. | auto |
| **I6** | See the note below the table: javac inlines constants, so bytecode checks can miss a dependency. | auto |
| **I9** | An attended monthly session, so this rule is review. The scratch self-test adds `implementation(project(":infrastructure:orderstore"))` to a copy of `contexts/loans/domain/build.gradle.kts` and expects the gate to fail. | review (attended) |

**I6 in Java.** Java has an inlining problem that Rust does not. **javac copies compile-time constants** (`static final` primitives and `String`s initialised with constant expressions) into the class that uses them. The bytecode then holds no reference to the declaring class, so an ArchUnit, forbidden-apis or JDeps check never sees that dependency. Three defences close the gap:

1. **Layers are Gradle projects.** A constant from a project that is not on the compile classpath does not compile, so the project graph closes the gap for every cross-layer edge.
2. **Within one project**, a check that must see constant use (for example `G7`'s `Rules` class) runs on source: Checkstyle, Error Prone (which sees the symbol before inlining) or JavaParser.
3. **Each bytecode check's planted breach includes a constant-only reference.** A meta-test asserts that the source-level or project-level check catches it.

JIT inlining happens at run time and does not affect any of these checks.

---

## 4. Group A: architecture, layers and dependencies

| Rule | Java check | Status |
| :--- | :--- | :--- |
| **A1** | (a) A flat test classifies every project path in `settings.gradle.kts` by the layer table (first match wins, and an unclassified project fails). It reads each `build.gradle.kts` for `project(":...")` and `projects.*` references and compares them with the row's `references`. Each project must apply exactly the convention plugin of its layer. (b) ArchUnit `layeredArchitecture()` is a second line of defence over packages. (c) The facade half, described below the table. | auto |
| **A2** | ArchUnit `slices().matching("com.example.contexts.(*).domain..").should().notDependOnEachOther()`, plus a rule that a `domain` project references only its own and upstream `contracts` projects. | auto |
| **A5** | A flat test over build files rejects `files(...)`, `fileTree(...)`, `flatDir`, `mavenLocal()` and any project path outside the root in production projects. Only the exact-comparison test project may name the reference program. | auto |
| **A6** | The project graph is acyclic, because Gradle refuses project cycles at compile time and the A1 test reports them. ArchUnit `slices().matching("com.example.(**)").should().beFreeOfCycles()` covers packages inside a project. For type-level cycles, freeze accepted ones with an `[allow]` entry using `FreezingArchRule`. | auto |
| **A7** | Checkstyle `IllegalTokenText` on `NUM_INT` and `NUM_LONG` matching long hexadecimal literals (`0[xX][0-9a-fA-F]{5,}`) outside `legacyimport`. ArchUnit name rules for decompiler-style names (`sub_[0-9A-F]+`, `FUN_`, `DAT_`, `ptr`, `Ptr`, `Offset`, `Address`). The meaning-level half goes to model review. | auto + review |
| **A8** | ArchUnit: only `application` and test classes depend on `..codec..`. `codec` classes depend on no I/O packages (the `D1` list). Public members of `infrastructure` name no `domain`, `contracts`, `codec` or third-party type, only kernel types and the opaque envelope record (`kind`, `version`, `byte[]` or a kernel byte-sequence payload). | auto |
| **A9** | An ArchUnit custom condition splits each simple class name and package segment into CamelCase words and rejects the words in `[words].infrastructure` and `[words].screens`, in `domain`, `contracts` and `application` ports. A port's error must be a sealed type declared in `application` that names no storage type. | auto |
| **A10** | (a) A flat test reads each project's `libs.*` dependencies and compares them with `[packages]` (each third-party library has one wrapper project; layer rows may allow whole layers). (b) ArchUnit backs it up: no class outside `com.example.infrastructure.<wrapper>..` and `hosts` depends on that library's packages. (c) DI containers are banned everywhere. | auto |
| **A14** | Covered by the A1 test: one `application` project per context, no edges between application or domain projects of different contexts, and only `day` spans contexts. | auto |
| **A17** | Checkstyle in `application`, `day` and `hosts`: `CyclomaticComplexity` `max=5`, and `IllegalToken` `LITERAL_INSTANCEOF` and `LITERAL_SWITCH` (branch on a kernel `Result` through its `fold` method instead). An invariant evaluated in the shell is left to model review. | auto + review |
| **A11** | ArchUnit: only the store wrapper (`infrastructure.<store>..`) depends on `java.sql..`, `javax.sql..` or the driver. A narrow test reads every migration and query text and fails on a table name without its context's prefix, or a join or foreign key across two contexts' tables other than the journal. Dormant until the store exists. | auto (dormant) |
| **A13** | The A1 build-file test: each `plugins/<name>` project references only `kernel` and its own contract project, never another plug-in. A registry test checks that every plug-in project is registered and every registry key has a project. Dormant until the first plug-in exists. | auto (dormant) |

**A1 (b): ArchUnit layers.**

```java
@AnalyzeClasses(packages = "com.example", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerRules {
  @ArchTest static final ArchRule a1_layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
      .layer("Kernel").definedBy("com.example.kernel..")
      .layer("Contracts").definedBy("com.example.contexts.*.contracts..")
      .layer("Domain").definedBy("com.example.contexts.*.domain..")
      .layer("Codec").definedBy("com.example.contexts.*.codec..")
      .layer("Application").definedBy("com.example.contexts.*.application..")
      .layer("Day").definedBy("com.example.day..")
      .layer("Infrastructure").definedBy("com.example.infrastructure..")
      .layer("Host").definedBy("com.example.hosts..")
      .whereLayer("Kernel").mayNotAccessAnyLayer()
      .whereLayer("Infrastructure").mayOnlyAccessLayers("Kernel")
      .whereLayer("Domain").mayOnlyAccessLayers("Kernel", "Contracts")
      .whereLayer("Host").mayNotBeAccessedByAnyLayer()
      .as("A1 the layer table governs every dependency").because("see ADR-0001");

  @ArchTest static final ArchRule a1_classified = classes().should().resideInAnyPackage(
      "com.example.kernel..", "com.example.contexts.*.contracts..", "com.example.contexts.*.domain..",
      "com.example.contexts.*.codec..", "com.example.contexts.*.application..", "com.example.day..",
      "com.example.infrastructure..", "com.example.hosts..")
      .as("A1 every class belongs to a layer");
}
```

Package layers cannot express "own context" versus "upstream context". The project-level test (a) holds those parts, and `A2` and `B1` hold the rest.

**A1 (c): the facade half.** In Java an `application` project re-exports nothing by default, because Java has no re-export statement. A domain type reaches `day` only through `api` (instead of `implementation`) in `application/build.gradle.kts`, or through JPMS `requires transitive`. A flat rule therefore reads:

- An `application` project declares its domain project with `implementation(...)`, never `api(...)`, and its `module-info.java` (if any) has no `requires transitive` on its domain.
- Only `day` and `hosts` classes may depend on `com.example.contexts.*.application.facade..` (ArchUnit).
- Every public type in `..application.facade..` is a public record or sealed interface declared in that package that wraps or mirrors one listed domain type: a rules section, a workflow input, a view or a refusal.
- Whether the list holds only those kinds is checked by review at each edit of the list, as in the guide.

Java cannot re-export a type under a new name, so the Rust bypasses (`self::`, `crate::` renames) have no counterpart. A public method signature outside the facade that names a domain type is the remaining hole. Ban it with ArchUnit: public methods of `application` classes outside `facade` may not have domain types in their raw parameter or return types.

**A9: word matching.** Match whole CamelCase words, not substrings, so that `Profile` does not fail on `File`:

```java
static ArchCondition<JavaClass> noWordFrom(Set<String> banned) {
  return new ArchCondition<>("use no word from " + banned) {
    @Override public void check(JavaClass c, ConditionEvents events) {
      var words = new ArrayList<String>();
      for (String part : (c.getPackageName() + "." + c.getSimpleName()).split("[._$]"))
        words.addAll(List.of(part.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")));
      words.stream().filter(w -> banned.stream().anyMatch(b -> b.equalsIgnoreCase(w)))
           .forEach(w -> events.add(SimpleConditionEvent.violated(c, "A9: " + c.getName() + " contains '" + w + "'")));
    }
  };
}
```

Package segments are lower case (`orderjson`), so the split above only separates words in class names. Either keep package segments to a single word (a flat rule: `[a-z]+`) or also match each banned word as a substring of a package segment.

**A10 (c): no DI container.** Ban the containers outright:

```java
@ArchTest static final ArchRule a10_no_container = noClasses().should().dependOnClassesThat().resideInAnyPackage(
    "org.springframework..", "com.google.inject..", "dagger..", "jakarta.inject..", "javax.inject..",
    "io.micronaut.context..", "org.picocontainer..")
    .as("A10 no dependency-injection container").because("hosts wire the system by passing functions ([banned].containers)");
```

Under Maven, use enforcer `bannedDependencies` per module and the same ArchUnit rules.

---

## 5. Group C: the functional core, types and errors

| Rule | Java check | Status |
| :--- | :--- | :--- |
| **C1** | Checkstyle `IllegalToken` `LITERAL_THROW` (with `id="C1"`) in `kernel`, `domain` and `application`. ArchUnit forbids `throws` clauses on their methods and constructing `Throwable` subtypes there. Errors come back through the kernel `Result` type below. Every context has a sealed error type with a `Defect` case that carries no `String`. | auto |
| **C2** | ArchUnit `fields().that().areDeclaredInClassesThat().resideInAnyPackage(CORE).should().beFinal()`. Error Prone `Var` makes every reassigned local or parameter need `@Var`, and Checkstyle `IllegalImport` bans `com.google.errorprone.annotations.Var` in core. Dependencies on mutable collections, arrays, `java.util.concurrent.atomic..` and `StringBuilder` are banned in public types (see below). | auto |
| **C3** | Constrained values (see below): the canonical constructor may be called only from inside the record, `create` and `value` must exist, and a property test per type is checked by name. | auto |
| **C4** | ArchUnit: public methods and record components in `domain` and `contracts` have no raw type in `int, long, short, byte, char, boolean, String, BigInteger, BigDecimal` or their boxes. Types that implement the kernel marker `Constrained` are exempt. Counts and dates use kernel types. Glossary `carries:` annotations are checked by a neutral glossary script. | auto |
| **C5** | NullAway with `@NullMarked` on every package (`package-info.java`, which a test requires). Checkstyle `IllegalToken` `LITERAL_NULL` in `kernel` and `domain`. `IllegalImport` bans `org.jspecify.annotations.Nullable` there. ArchUnit bans `Objects.isNull`, `nonNull` and `requireNonNull` and the `java.util.Map#get` family in core (see C6). | auto |
| **C6** | ArchUnit and forbidden-apis ban partial methods. The custom Error Prone check `CoreDivision` allows division only by a non-zero constant from a `Rules` class. `CoreArithmetic` handles overflow (see F1). Exhaustive `switch` comes from sealed types with no `default` (C12). Unlisted partial calls go to review. | auto (+ review) |
| **C7** | ArchUnit: every public `run` method of a workflow class in `..application..` has the shape `CompletionStage<Result<Seq<Event>, WorkflowError>> run(Command)` and takes no functional-interface parameter whose method returns `CompletionStage` or `void` (C13). Keep it dormant until the workflows exist. | auto (dormant) |
| **C8** | An ArchUnit rule evaluated in **report** mode: records in `domain` and `contracts` with `boolean` components named `is*`, `has*` or `can*`, two or more `Optional` components, or `Optional<Seq<...>>`, unless listed in `[allow].states_as_flags`. | report |
| **C9** | Error Prone `CheckReturnValue`, with `@CheckReturnValue` on every core and application package (`package-info.java`). `@CanIgnoreReturnValue` is banned there by `IllegalImport`. Turning a refusal into a default value goes to review. | auto + review |
| **C10** | Java is private by default. `module-info.java` lists `exports` explicitly, with no `requires transitive` in `domain` or `contracts`. A `contracts` change cites a decision record (history check, against the committed surface file from `B23`). A type that carries an invariant has no public constructor (C3). | auto |
| **C11** | ArchUnit, through generic type inspection: no public method in core or application returns `Result<?, String>`, `Result<?, ? extends Throwable>` or `Result<Result<...>, ...>`, and none declares `throws`. The four error kinds are sealed types named by convention (`*Refusal`, `*WrapperError`, `DecodeError`, `Defect`). | auto |
| **C12** | Checkstyle `MatchXpath` bans `default` in every `switch` in `kernel`, `domain` and `application`. Error Prone `MissingCasesInEnumSwitch` is an error, and switches over enums are written as switch expressions. | auto |
| **C13** | ArchUnit: in core, public methods do not return `void`, and no parameter type is a functional interface whose single abstract method returns `void` or `CompletionStage`/`Future`. That bans `Runnable`, `Consumer` and `BiConsumer`. | auto |
| **C14** | Checkstyle `IllegalToken` `LITERAL_TRY` and `LITERAL_CATCH` in `kernel`, `domain`, `contracts` and `application`. `IllegalCatch` (no `catch (Exception`, `Throwable` or `RuntimeException`) and `EmptyCatchBlock` everywhere, with one suppression for the host's top-level handler. Error Prone `EmptyCatch` is also on. A handler that logs and returns a fallback goes to review. | auto + review |
| **C15** | ArchUnit: enums in `domain` and `contracts` declare no instance fields of a primitive or `String` type, and no class there calls `Enum#ordinal()` or `Enum#compareTo`. Integer tags live only in `codec` and `legacyimport`. | auto |

### A kernel `Result` and immutable sequence

The kernel may depend on nothing but the JDK (`A1`), so it defines its own types. Vavr's `Either` would be a third-party dependency of `Kernel`.

```java
/** Success or typed failure; the only error channel of the core (C1, C11). */
public sealed interface Result<T, E> {
  /** A success holding its value. */
  record Ok<T, E>(T value) implements Result<T, E> {}
  /** A failure holding its typed error. */
  record Err<T, E>(E error) implements Result<T, E> {}

  /** Wraps a success. */
  static <T, E> Result<T, E> ok(T value) { return new Ok<>(value); }
  /** Wraps a failure. */
  static <T, E> Result<T, E> err(E error) { return new Err<>(error); }

  /** Folds both cases into one value; the shell branches with this, never with instanceof (A17). */
  default <R> R fold(Function<? super T, ? extends R> onOk, Function<? super E, ? extends R> onErr) {
    return switch (this) {
      case Ok<T, E> ok -> onOk.apply(ok.value());
      case Err<T, E> err -> onErr.apply(err.error());
    };
  }
}
```

`Seq<T>` is the kernel's immutable list: a final class around `List.copyOf(...)` that exposes no mutator, returns `Optional` from `first()`, and has no `get(int)`. **Ban `java.util.List`, `Map` and `Set` in public core signatures** (C2), because the interfaces carry `add` and `put` even when the instance is unmodifiable. Ordered maps and sets are kernel `SortedMapOf<K, V>` and `SortedSetOf<T>`, built on `TreeMap` and `TreeSet` behind an unmodifiable view (`D12`).

**C2, banned types in public signatures.** ArchUnit's fluent API cannot see type arguments, so use a custom condition over raw and generic types:

```java
static final Set<String> MUTABLE = Set.of("java.util.ArrayList", "java.util.LinkedList", "java.util.HashMap",
    "java.util.TreeMap", "java.util.HashSet", "java.util.TreeSet", "java.util.List", "java.util.Map",
    "java.util.Set", "java.util.Collection", "java.util.Deque", "java.lang.StringBuilder");

static final ArchCondition<JavaMethod> exposeNoMutableType = new ArchCondition<>("expose no array or mutable collection") {
  @Override public void check(JavaMethod m, ConditionEvents events) {
    var types = new ArrayList<JavaType>(m.getParameterTypes());
    types.add(m.getReturnType());
    for (JavaType t : types)
      for (JavaClass raw : t.getAllInvolvedRawTypes())
        if (raw.isArray() || MUTABLE.contains(raw.getName()))
          events.add(SimpleConditionEvent.violated(m, "C2: " + m.getFullName() + " exposes " + raw.getName()));
  }
};
@ArchTest static final ArchRule c2_public_types = methods().that().arePublic()
    .and().areDeclaredInClassesThat().resideInAnyPackage("com.example.kernel..", "com.example.contexts.*.domain..")
    .should(exposeNoMutableType).as("C2 no mutable collections in public core types");
```

`JavaType.getAllInvolvedRawTypes()` is in ArchUnit 1.x. Apply the same condition to record components (the fields of a record class), and exempt `Seq` itself. Remember that a record is only **shallowly** immutable: a component of type `List` is mutable unless the compact constructor copies it. That is why the component types must be kernel types.

### C3: constrained values with records

A record's canonical constructor must be at least as accessible as the record, so **a public record cannot hide its constructor**. There are two workable shapes. The starter uses the second, a final class with a private constructor, because it hides the representation with no check at all; the first keeps the record's generated members at the price of an ArchUnit rule.

**Shape 1: a record whose constructor only `create` may call, enforced by ArchUnit** *(not compiled)*.

```java
/** Loan duration in days, 1..Rules.MAX_LOAN_DAYS (C3). Build it with create; value() returns the raw count. */
public record LoanDays(int value) implements Constrained {

  /** Canonical constructor; only create calls it (ArchUnit rule c3_only_create). */
  public LoanDays {
    assert isLegal(value) : "C3 defect: LoanDays built without create";   // [allow] C1 entry: runs only under -ea
  }

  /** Validates a raw day count and returns the constrained value or a typed error. */
  public static Result<LoanDays, LoanDaysError> create(int raw) {
    if (raw < 1) return Result.err(LoanDaysError.BELOW_MINIMUM);
    if (raw > Rules.MAX_LOAN_DAYS) return Result.err(LoanDaysError.EXCEEDS_MAXIMUM);
    return Result.ok(new LoanDays(raw));
  }

  private static boolean isLegal(int raw) { return raw >= 1 && raw <= Rules.MAX_LOAN_DAYS; }
}
```

The validation belongs in `create`, because the core may not throw (C1). The compact constructor is the natural place for a backstop check, but any check there would have to throw. The example therefore uses an `assert`, which runs only when tests enable assertions (`-ea`, which Gradle's `Test` task enables by default). It catches a path that bypasses `create` during tests and costs nothing in production. Because it is still a throw, it needs one `[allow]` entry that names `C1`. If you would rather have no exception at all, leave the compact constructor for normalising only (such as `List.copyOf`), and let the ArchUnit rule below do the work. Either way, `value()` is the record's generated accessor, and Java records have no default zero value, so `[allow].struct_constrained` is rarely needed.

```java
static final ArchCondition<JavaClass> constructedOnlyByItself = new ArchCondition<>("be constructed only inside itself") {
  @Override public void check(JavaClass c, ConditionEvents events) {
    for (JavaConstructor ctor : c.getConstructors())
      for (JavaConstructorCall call : ctor.getCallsOfSelf())
        if (!call.getOriginOwner().equals(c))
          events.add(SimpleConditionEvent.violated(call, "C3: " + call.getDescription() + "; use " + c.getSimpleName() + ".create"));
  }
};
@ArchTest static final ArchRule c3_only_create = classes().that().implement(Constrained.class)
    .should(constructedOnlyByItself).as("C3 constrained values are built only through create");
```

Add three more rules:

- Each `Constrained` type declares `public static Result<Self, ?> create(...)` and `value()`.
- Its record components are private, which records already guarantee.
- The test classes contain a property named `<Type>Properties.createAcceptsExactlyLegalValues` (see `E10`).

Reflection and serialisation could bypass the constructor rule. Reflection is banned by forbidden-apis `jdk-reflection`, and reflection-driven serialisers are banned in core by `P2`.

**Shape 2: a final class with a private constructor** (the starter's [`LoanDays`](../../examples/java-starter/loans/domain/src/main/java/starter/loans/domain/LoanDays.java), compiled). This gives true hiding without a check:

```java
public final class LoanDays implements Comparable<LoanDays> {
    /** Why a raw day count is not a legal LoanDays. */
    public enum Invalid { BELOW_MINIMUM, ABOVE_MAXIMUM }

    private final int days;

    private LoanDays(int days) { this.days = days; }

    /** Validates a raw day count: the value, or why it was refused. */
    public static Result<LoanDays, Invalid> create(int raw) {
        if (raw < LoanRules.MIN_LOAN_DAYS) return Result.err(Invalid.BELOW_MINIMUM);
        if (raw > LoanRules.MAX_LOAN_DAYS) return Result.err(Invalid.ABOVE_MAXIMUM);
        return Result.ok(new LoanDays(raw));
    }

    /** The validated day count. */
    public int value() { return days; }

    // compareTo, equals, hashCode and toString written by hand (abridged)
}
```

The starter's property `loanDaysAcceptsExactlyTheLegalRange` is the boundary property `C3` asks for. The cost is hand-written `equals`, `hashCode` and `toString`, which `B9` counts as custom equality (an `[allow].custom_equality` entry plus law properties, which the starter does not have yet). Use it when the representation must stay hidden from reflection-free callers as well. To forbid public constructors on non-record classes: `constructors().that().areDeclaredInClassesThat().implement(Constrained.class).and().areDeclaredInClassesThat().areNotRecords().should().bePrivate()`. (`areNotRecords()` exists in ArchUnit 1.x; on older versions, test `JavaClass.isRecord()` in a custom predicate.)

### C6: partial operations and totality

Ban the partial operations in the core forbidden-apis signatures file (`config/forbidden/core.txt`):

```
@defaultMessage C6: partial operation; use the kernel's total alternative (Seq.first(), SortedMapOf.find(), Result)
java.util.Optional#get()
java.util.Optional#orElseThrow()
java.util.Iterator#next()
java.util.List#get(int)
java.util.List#getFirst()
java.util.List#getLast()
java.util.Map#get(java.lang.Object)
java.lang.Integer#parseInt(java.lang.String)
java.lang.Long#parseLong(java.lang.String)
java.lang.Math#floorDiv(int,int)
java.lang.Math#floorMod(int,int)
java.math.BigInteger#divide(java.math.BigInteger)
```

forbidden-apis matches a method on the class you name. A call through an implementing class (`ArrayList.get`) is banned separately, or else unreachable because of the C2 type bans. `Optional#orElseThrow(Supplier)` also throws, so list it with its parameter (`java.util.Optional#orElseThrow(java.util.function.Supplier)`).

**Division by a named constant** is a short custom Error Prone check in `tools/errorprone-checks`, enabled only in core layers:

```java
@AutoService(BugChecker.class)
@BugPattern(summary = "C6: divide only by a non-zero named constant from a Rules class", severity = BugPattern.SeverityLevel.ERROR)
public final class CoreDivision extends BugChecker implements BugChecker.BinaryTreeMatcher {
  @Override public Description matchBinary(BinaryTree tree, VisitorState state) {
    if (tree.getKind() != Tree.Kind.DIVIDE && tree.getKind() != Tree.Kind.REMAINDER) return Description.NO_MATCH;
    Symbol divisor = ASTHelpers.getSymbol(tree.getRightOperand());
    boolean namedRule = divisor instanceof Symbol.VarSymbol v
        && v.getConstValue() instanceof Number n && n.longValue() != 0
        && v.owner.getSimpleName().contentEquals("Rules");
    return namedRule ? Description.NO_MATCH : describeMatch(tree);
  }
}
```

Compile the checks project with `compileOnly` dependencies on `error_prone_check_api` and AutoService, plus the `--add-exports jdk.compiler/com.sun.tools.javac.*=ALL-UNNAMED` flags. Test it with `error_prone_test_helpers`' `CompilationTestHelper`, where `// BUG: Diagnostic contains: C6` comments are the planted breaches. Compound `/=` is a `CompoundAssignmentTree`, which C2 makes unreachable because there is no reassignment. Unlike the Rust `syn` check, Error Prone sees full types, so a partial call reached through type inference is visible. You can extend the list from inside the check (match `MethodInvocationTree`s against a table) instead of leaving unlisted calls to review.

### C12: exhaustive `switch` with no `default`

Sum types are sealed interfaces of records (or enums for plain cases). Exhaustiveness checking depends on the kind of `switch`:

- A **switch expression** is exhaustive by the language specification.
- A **switch statement with patterns** (Java 21 or later) is exhaustive by the language specification.
- An old-style **switch statement over an enum** is *not* checked. Write enum switches as switch expressions, and keep Error Prone `MissingCasesInEnumSwitch` as a backstop.

What javac cannot do is reject a `default` that hides a future case. The starter bans it with a flat source scan, `C12-no-default-arm` (compiled, with its planted breach): it reads every source file whose package is in the kernel, domain or application, blanks comments and string literals, and fails on `default ->` or `default:`. A source scan is needed because the compiled class does not show whether the source had a `default` arm. With Checkstyle, the same ban is one module in the core layers' configuration *(not compiled)*:

```xml
<module name="MatchXpath">
  <property name="id" value="C12"/>
  <property name="query" value="//LITERAL_SWITCH//LITERAL_DEFAULT"/>
  <message key="matchxpath.match" value="C12: no default case over the system's own unions; list every case ([allow].wildcard to excuse)"/>
</module>
```

In core layers you switch only on your own types (C4 bans raw primitives in signatures), so banning every `default` is the flat form `F5` recommends. To confirm the query against your Checkstyle version, print a sample's syntax tree with `java -jar checkstyle-<v>-all.jar -t Sample.java`. Two more points:

- **Total type patterns.** A catch-all can also be a total type pattern, such as `case LoanState s ->` over a `LoanState` selector. Catch it with a house Error Prone check that matches `CaseTree` labels whose pattern type equals the selector type, or with review.
- **Unnamed patterns.** The pattern `case LoanState.Active _ ->` names one case and is not a wildcard.

---

## 6. Group D: determinism and exactness

| Rule | Java check | Status |
| :--- | :--- | :--- |
| **D1** | ArchUnit: `kernel` and `domain` depend on no class in `java.io..`, `java.nio..`, `java.net..`, `java.util.concurrent..`, `java.lang.reflect..`, `java.text..`, `java.util.logging..`, `System.Logger`, `Thread`, `ThreadLocal`, `Runtime`, `ProcessBuilder`, `ProcessHandle` or `Locale`. **Clock reads** are banned, not `java.time` as a whole: `java.time.Clock`, `InstantSource`, every `now(...)` on a `java.time` type, and `System#currentTimeMillis`/`nanoTime` (the starter's `D1-pure-core`). `java.time` value types such as `LocalDate` are allowed in the core; which time types a signature may use is `D13`'s rule. forbidden-apis `jdk-unsafe` (default locale and charset), `jdk-system-out`, `System#getenv`, `System#getProperty`, `UUID#randomUUID()`. Checkstyle `IllegalToken` `LITERAL_SYNCHRONIZED`. ArchUnit static fields must be `final` and of an immutable kernel type, or a primitive or `String` constant. | auto |
| **D2** | forbidden-apis bans `java.util.Random`, `java.util.random.RandomGenerator`, `java.util.concurrent.ThreadLocalRandom`, `java.security.SecureRandom`, `java.util.SplittableRandom`, `Math#random()` and `StrictMath#random()` outside the kernel's seeded service. ArchUnit bans calls named `hashCode` (any owner), `System#identityHashCode`, `Objects#hash` and `Objects#hashCode` in `domain`. | auto |
| **D3** | Checkstyle `IllegalToken` `LITERAL_FLOAT`, `LITERAL_DOUBLE`, `NUM_FLOAT`, `NUM_DOUBLE` in `kernel` and `domain`. ArchUnit bans dependencies on `Float`, `Double`, `java.lang.Math`, `StrictMath` and `BigDecimal` there. Integer `Math` operations go through kernel wrappers. | auto |
| **D4** | Ported programs only. Checkstyle `IllegalToken` `TYPECAST`, `LITERAL_SHORT` and `LITERAL_BYTE` outside `kernel`. Each kernel wrapper has jqwik properties against a `long`-widened reference. javac knows every type, so a house Error Prone check can see type-inferred narrowing too. The Rust `L` part becomes **auto** in Java. | auto |
| **D5** | Checkstyle `IllegalToken` `STRING_LITERAL` and `TEXT_BLOCK_LITERAL_BEGIN` in `kernel`, `domain` and `contracts`, suppressed only for the one listed `TextKeys` file that mirrors `[text].keys`. A test compares that file with `[text].keys`. | auto |
| **D8** | Review: whether a type stands for "the human user" is a question of meaning. A word list (`User`, `Me`, `Human`, `Operator`) can help, as a `report`-mode ArchUnit name rule. | review |
| **D11** | ArchUnit: classes named `*View` or `*Projection` (or implementing the kernel `View` marker) in `contracts` and `application` depend on nothing from the D2 list and have only `final` fields. Their public methods do not call methods named `set*`, `put*`, `add*`, `remove*` or `clear` on anything. | auto |
| **D12** | forbidden-apis and ArchUnit ban `HashMap`, `HashSet`, `LinkedHashMap`, `LinkedHashSet`, `Hashtable`, `IdentityHashMap`, `WeakHashMap`, `ConcurrentHashMap`, **`Set.of`, `Set.copyOf`, `Map.of`, `Map.ofEntries`, `Map.copyOf`**, `Collectors#toSet`, `Collectors#toMap`, `Collectors#groupingBy`, `Collectors#toUnmodifiableSet` and `Collectors#toUnmodifiableMap`. `hashCode` calls are banned as under D2. | auto |
| **D13** | ArchUnit: no `java.time.Instant`, `Clock`, `LocalDateTime` or `ZonedDateTime` in any `domain` or `application` signature. The core uses the kernel `Date`. The `infrastructure/clock` wrapper exposes `WallInstant` (from `Instant.now()`) and `Elapsed` (from `System.nanoTime()`) as distinct records. | auto |
| **D9** | Optional (see the guide). Once "engine" is defined for the project: ArchUnit, each engine project's packages may read the core entity only through a mapping class it owns, and no component of the core entity record is read by only one engine (a report from `getAccessesToSelf()`); the rest is review. | review (optional) |
| **D10** | Reimplementations only. The inventory runner checks that each component listed in the component table has its contract page (`docs/components/<name>.md`) with inputs, outputs, random draws and reference routines. Whether a component reads only its declared inputs is review, helped by an ArchUnit report of the classes each component's package accesses. | auto + review (dormant) |

**Why D12 bans `Set.of` and `Map.of`.** The immutable collections from `Set.of(...)` and `Map.of(...)` iterate in an order that is deliberately salted **per JVM run**, so the same program gives a different order on the next start. `HashMap` order depends on `hashCode`, which for enums and plain objects is an identity hash that differs between runs. Neither belongs in a deterministic core.

forbidden-apis signature syntax names one overload at a time, and `Set.of` has twelve. Ban the calls by name with ArchUnit:

```java
@ArchTest static final ArchRule d12_no_hash_order = noClasses()
    .that().resideInAnyPackage("com.example.kernel..", "com.example.contexts.*.domain..")
    .should().callMethodWhere(new DescribedPredicate<JavaMethodCall>("Set/Map.of, copyOf or ofEntries") {
      @Override public boolean test(JavaMethodCall call) {
        var target = call.getTarget();
        return (target.getOwner().isEquivalentTo(Set.class) || target.getOwner().isEquivalentTo(Map.class))
            && List.of("of", "copyOf", "ofEntries").contains(target.getName());
      }
    })
    .as("D12 no hash-ordered collections in the core").because("use the kernel's SortedMapOf and SortedSetOf");
```

`DescribedPredicate` declares `test` in ArchUnit 1.x (`apply` before 0.22).

**The core signatures file** gathers D1 and D2 in one place:

```
@defaultMessage D1/D13: no clock in the core; the core reads Kernel's Date, the Clock wrapper provides WallInstant and Elapsed
java.lang.System#currentTimeMillis()
java.lang.System#nanoTime()
java.time.Instant#now()
java.time.LocalDate#now()
java.time.Clock
@defaultMessage D2: randomness only through Kernel's seeded service
java.util.Random
java.util.concurrent.ThreadLocalRandom
java.security.SecureRandom
java.util.SplittableRandom
java.lang.Math#random()
java.util.UUID#randomUUID()
```

```kotlin
// lcg.kernel and lcg.domain (and the kernel's own random service gets a scoped [allow] for java.util.SplittableRandom)
forbiddenApis {
    bundledSignatures = setOf("jdk-unsafe", "jdk-deprecated", "jdk-internal", "jdk-non-portable", "jdk-system-out", "jdk-reflection")
    signaturesFiles = files(rootDir.resolve("config/forbidden/core.txt"))
}
```

Kernel's seeded random service has to implement its own generator (for example SplitMix64, written as pure functions over a `long` state), or it needs a scoped allow entry for `SplittableRandom`.

---

## 7. Group G: simplicity and module design

| Rule | Java check | Status |
| :--- | :--- | :--- |
| **G3** | Checkstyle `CyclomaticComplexity` `max=15` (core and the rest) and `max=5` (`application`, `day`, `hosts`, the `A17` file). PMD `CognitiveComplexity` `reportLevel=15` where the cognitive measure is wanted. Nesting depth of 4: Checkstyle `NestedIfDepth` `max=3`, `NestedForDepth` `max=3` and `NestedTryDepth` `max=3` (see below). Ported routines: a Checkstyle suppression per method, counted by a ratchet test. | auto |
| **G4** | Checkstyle `MethodLength` `max=80` and `FileLength` `max=600` at severity error. For 500 lines per module, see below. Reported, never gated: `LambdaBodyLength`, `BooleanExpressionComplexity`, `MethodCount`, and `MethodLength` on tests, all at severity `warning`. | auto + report |
| **G7** | Checkstyle `MagicNumber` with `ignoreNumbers` `-1, 0, 1`, `ignoreHashCodeMethod=false` and `ignoreFieldDeclaration=false` in `domain`, suppressed for files named `Rules.java`. A `Rules` class is a `final` class of `static final` constants. | auto |
| **G11** | ArchUnit custom conditions on public methods and constructors of production classes (record canonical constructors excepted): at most 4 parameters; not 3 or more parameters of the same primitive or `String` type (`create` excepted). In `domain`, `contracts` and `application`, no `boolean`, `Boolean`, `Map.Entry` or tuple-library type (`org.apache.commons.lang3.tuple..`, `org.javatuples..`) in public signatures. In `infrastructure`, no tuple types. | auto |
| **G12** | javac `-Xdoclint:all/protected` in core layers (missing comments become warnings and then errors under `-Werror`). Checkstyle `MissingJavadocType`, `MissingJavadocMethod` (`scope=public`, `allowMissingPropertyJavadoc=false`) and `JavadocVariable` for public fields. Document record components with `@param` tags in the record's comment, which doclint checks. | auto |
| **G13** | Review: the design rubric judges depth and purpose. The pass-through ratio (the share of workflows that only forward their arguments, flagged above 20 percent) can be a `report`-mode ArchUnit helper that counts `run` methods whose body makes exactly one call. | review (+ report) |
| **G2** | A nightly report, never a gate: instability and distance from the main sequence per project from the ArchUnit package graph (abstractness by role), cohesion per class (PMD `LooseCoupling` and a JavaParser count of public methods sharing domain types), duplicated token runs (PMD CPD), commits touching more than three contexts (`git log --name-only`), and `G4`'s small indicators. | report |

**Nesting depth.** No stock Java check measures general nesting depth across `if`, `for`, `while`, `switch`, `try` and lambdas together. The three Checkstyle checks count each statement kind separately (`max` counts nesting *beyond* the first level, so `max=3` allows depth 4). For one combined measure, write a small JavaParser test over `src/main/java` that walks statement nodes and fails beyond depth 4.

**500 lines per module.** In Java a "module" in this sense is a top-level class. Require `OuterTypeNumber` `max=1` (one top-level type per file), and then `FileLength` bounds the type at 600. For the stricter 500 limit, use a short JavaParser test that measures each top-level type's line range. PMD's `NcssCount` (`classReportLevel`) counts statements rather than lines, so it is only a proxy.

**Gated versus report-only.** In Checkstyle, gating checks use `<property name="severity" value="error"/>` and report-only checks use `value="warning"`. The Gradle extension sets `maxErrors = 0` and `maxWarnings = Int.MAX_VALUE`, so warnings appear in the HTML and XML report without failing the build.

**Ported routines (G3, G4 and G7).** For a routine that may exceed a limit, put a suppression in `config/checkstyle/suppressions.xml` naming the file, the check and an `[allow]` id. A ratchet test counts the entries and compares the count with the merge base (`git show $(git merge-base HEAD origin/main):config/checkstyle/suppressions.xml`). The count can only fall. For ArchUnit-based limits, `FreezingArchRule.freeze(rule)` does the same job natively. Set `freeze.store.default.allowStoreUpdate=false` in `archunit.properties` on CI, so new violations fail and fixed ones are dropped from the store.

```xml
<!-- config/checkstyle/domain.xml (excerpt): every module carries its rule id for F5 messages -->
<module name="Checker">
  <module name="FileLength"><property name="id" value="G4"/><property name="max" value="600"/></module>
  <module name="TreeWalker">
    <module name="CyclomaticComplexity"><property name="id" value="G3"/><property name="max" value="15"/></module>
    <module name="MethodLength"><property name="id" value="G4"/><property name="max" value="80"/></module>
    <module name="MagicNumber"><property name="id" value="G7"/><property name="ignoreNumbers" value="-1, 0, 1"/></module>
    <module name="IllegalToken"><property name="id" value="C1"/><property name="tokens" value="LITERAL_THROW"/></module>
    <module name="IllegalToken"><property name="id" value="C5"/><property name="tokens" value="LITERAL_NULL"/></module>
    <module name="IllegalToken"><property name="id" value="D3"/>
      <property name="tokens" value="LITERAL_FLOAT, LITERAL_DOUBLE, NUM_FLOAT, NUM_DOUBLE"/></module>
    <module name="LambdaBodyLength"><property name="severity" value="warning"/><property name="max" value="10"/></module>
  </module>
</module>
```

---

## 8. Group E: testing and verification

### Test suites, tags and tiers

Use Gradle's JVM Test Suite plugin so that each tier is its own source set, task, dependency list and result directory. The inventory runner reads tiers from the directory names, because **JUnit XML does not record tags**.

```kotlin
// lcg.domain (logic tests) and tests/scenarios (scenario tests) use the same pattern
testing {
    suites {
        val test by getting(JvmTestSuite::class) {                 // logic tier: build/test-results/test
            useJUnitJupiter(libs.versions.junit.get())
            dependencies { implementation(project(":tests:builders")); implementation(project(":tests:check")) }
            targets.all { testTask.configure { useJUnitPlatform { excludeTags("nightly", "quarantine") } } }
        }
    }
}
val nightlyTest by tasks.registering(Test::class) {                 // same classes, nightly tag only
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("nightly") }
}
```

Scenario, narrow, smoke and architecture tiers are further suites (`register<JvmTestSuite>("scenarioTest")` and so on), or test projects under `tests/`. Under Maven, use Surefire `<excludedGroups>nightly</excludedGroups>` and a `nightly` profile with `<groups>nightly</groups>`.

| Rule | Java check | Status |
| :--- | :--- | :--- |
| **E1** | (a) Resolution fails if any configuration pulls in `org.mockito`, `org.easymock`, `io.mockk`, `org.jmock`, `org.powermock`, `org.jmockit`, WireMock or MockServer. (b) ArchUnit bans the same packages and `org.springframework.boot.test.mock..`. (c) No test class implements an interface declared in `..infrastructure..` or `..application..` (the port interfaces). (d) No class outside `infrastructure` has a name ending in `Stub`, `Fake`, `Mock` or `Dummy`. (e) Wrapper constructors are private, and ports are built only from wrappers (see E5). | auto |
| **E2** | ArchUnit over production classes: no dependency on `org.junit..`, jqwik or assertion libraries, no member annotated `VisibleForTesting` (Guava's annotation has CLASS retention, which ArchUnit still reads), and no member named `*ForTest*` or `*ForTesting*`. forbidden-apis `jdk-reflection` in tests (`setAccessible`). Production code may not read `System.getProperty` or `getenv` outside `hosts`. Because tests in the same package can call package-private members, an ArchUnit condition over test classes fails any access to a non-public member of a production class. JPMS: no qualified `exports ... to` or `opens` towards test modules. | auto |
| **E3** | One suite per tier. ArchUnit over test classes, scoped by suite: logic tests depend only on their context's `domain` and `contracts`, `kernel`, upstream `contracts`, `tests/builders` and test libraries. Scenario tests enter through `application` with `createNull` wrappers. Only the `narrowTest` suite may touch `java.io`, `java.nio.file`, `java.net`, `ProcessBuilder`, `Thread.sleep` or a wrapper's `create`. | auto |
| **E5** | ArchUnit over each public class in `infrastructure` and each `application` service with infrastructure below it: `public static T create(...)` and `public static T createNull(...)` exist; `createNull`'s parameters are not third-party types; a zero-argument `createNull()` exists (Java has no optional parameters, so the zero-argument overload is the required form, with others taking a `NullConfig` record). Neither factory calls a third-party class directly (inspect `getMethodCallsFromSelf()`). Writers expose `outputs()` returning `Seq` of a sealed behaviour type, never `String` or a primitive. Loud exhaustion and event simulation are proved by each wrapper's contract tests (E17). | auto |
| **E8** | Per-test budgets by tier: the starter's `TimeBudget` extension fails a test whose body exceeds its tier's budget, read from its tag. A judge over JUnit XML adds the 10 slowest tests and the stage wall-time ratchet. See below. | auto |
| **E9** | ArchUnit: only scenario-suite test classes call `outputs()` on wrappers, and only for wrappers listed as unmanaged in the dependency table. The expected list must be a literal or golden file, never production encoder output, which is review. Keep it dormant until writers exist. | auto (+ review) |
| **E10** | jqwik. ArchUnit over test classes requires, for each `@AggregateRoot`, a class `<Aggregate>Properties` with the named properties. Seeds and failure examples are described below. | auto |
| **E11** | ArchUnit on test methods: names do not start with `should`, do not match `^[a-z]+_[A-Za-z]+_[A-Za-z]+$`, and contain no public method name of the class under test. Each test method calls `decide`, `handle` or `run` exactly once, counted through `getMethodCallsFromSelf()`. Calls inside lambdas compile into synthetic methods, so include the test's synthetic `lambda$` methods in the count. Smoke tests are excepted. Domain phrasing is review. | auto + review |
| **E12** | History check: check out the parent's production sources, keep the commit's test sources, and run the new or changed tests. The JUnit XML tells an assertion failure (`<failure type="org.opentest4j.AssertionFailedError">` or `java.lang.AssertionError`) from a crash (any other exception type) and from a compile failure (`compileTestJava` fails). **This settles the Rust exit-code-101 problem**, so the replay can be live in Java. | auto |
| **E13** | ArchUnit: static fields in test classes and in `application` are `final`, and of an immutable type or a write-once holder. No `@Execution(SAME_THREAD)`, `@Isolated` or `@ResourceLock` without a `// sequential:` reason comment (Checkstyle `RegexpMultiline`), and the number of such annotations is capped by an ArchUnit count. Retries are banned: no `org.gradle.test-retry` plugin, no junit-pioneer `@RetryingTest`, no `@RepeatedIfExceptionsTest`, and Maven Surefire `rerunFailingTestsCount` must stay 0. The nightly run shuffles and runs in parallel (see below). | auto |
| **E14** | JaCoCo branch coverage on core projects with one pinned `toolVersion`. A ratchet file holds the last covered-branch ratio, and the gate fails if it drops. The uncovered branches are listed from `jacocoTestReport.xml`. Coverage is printed beside the E22, E12 and (once PIT runs) surviving-mutant results. Keeping coverage out of targets and prompts is review. | auto + review |
| **E16** | ArchUnit over test classes outside `tests/builders`: no call to the constructor of a `domain`, `kernel` or `contracts` record, except single-component records. Builders read in domain language, which is review. | auto + review |
| **E17** | Each wrapper with a third-party dependency has one JUnit **test interface** `<Wrapper>Contract` with `@Test default` methods and an abstract `subject()` factory. Two classes implement it: `<Wrapper>NullTest` in the fast tier (`createNull`) and `<Wrapper>RealTest` in `narrowTest` (`create`). Identical test names follow by construction. ArchUnit requires both implementations, in those suites, and requires each narrow test class to reference exactly one wrapper. | auto |
| **E21** | Each wrapper's sealed error type lists its failure modes. ArchUnit checks that public wrapper methods return `Result<?, <Wrapper>Error>`. A test checks that, for each permitted subtype of the error, the contract interface has a test method whose name contains the case name and which configures it through `createNull`. Hangs are timed out on `System.nanoTime()` (`Elapsed`), never on wall-clock time. Health-report counts and user notices by text key are review. | auto (+ review) |
| **E22** | The counting `Expect` parameter and the `CountedAssertions` extension that fails a test with no counted assertion (both in the starter), an ArchUnit ban on direct assertion APIs (the starter's `E22-counted-assertions`), a house Error Prone tautology check, and bans on `@Disabled` and `Assumptions`. See below. | auto |
| **E23** | History check: for a commit marked as a refactoring, map changed lines in `src/test/java` to `@Test` method bodies (JavaParser gives each method's line range) and fail on any change other than a rename forced by the compiler. Detect renames by comparing token streams with identifiers normalised. | auto |
| **E25** | The house Error Prone check `ExpectedLeak`: in an `expect.equal(expected, actual)` call, the two arguments may not call the same production method, and may not be the same source text (`state.getSourceForNode`). Local variables, operator chains and helpers are review. Collaborator-based isolation is already banned by E1. | auto + review |
| **E26** | `@Tag("smoke")` on 1 to 3 tests in a `smokeTest` suite. The inventory runner puts them in the gate when their total is 10 s or less, otherwise in the nightly run. ArchUnit (or a test) checks that, together, smoke test call paths reach every wrapper's `create`, using the transitive call graph from `getMethodCallsFromSelf()`. | auto (dormant until hosts exist) |
| **E30** | `@Tag("quarantine")`, excluded from the gate. A test checks that each quarantined test has an allow entry with a reason, a dated approval and an expiry of at most 7 days. Quarantined counts go into the `R1` flaky indicator. | auto |
| **E31** | A gate test runs the same seed and commands twice in one JVM and compares a SHA-256 of the canonical encoding, including the two-order variant. The nightly run replays a golden journal in two fresh JVMs: one with `-XX:ActiveProcessorCount=1` and `-Djava.util.concurrent.ForkJoinPool.common.parallelism=1`, and one with default parallelism, plus a CI matrix job on a second OS. The static proxy is D12's ban, extended to `application`, `day` and `codec`. | auto (+ nightly) |
| **E33** | ApprovalTests.Java (or similar): `*.received.*` is in `.gitignore`, and the history check rejects committed received files and approved files that changed without approval. ArchUnit bans `org.approvaltests.reporters.AutoApproveReporter` and similar auto-approving reporters. The scrubber has a test that renders the same output under two kernel clock values. | auto |
| **E36** | A JUnit `@TestFactory` that turns each case file of `spec/` into a `DynamicTest` named `spec/<case>`. The inventory runner reads the JUnit XML and fails on any case file that no test of that name ran. | auto |
| **E15** | Reimplementations only. One exact-comparison test project depends on the reference (the only project `A5` allows to). A JaCoCo report of the reference's routines (when the reference runs on the JVM) or the reference's own coverage tool, over the recorded cases, must reach every reachable branch of each component, with exceptions listed with reasons. The gate runs the recordings and a few hundred jqwik differential properties for touched components; the nightly run covers all. | auto (dormant) |
| **E37** | Comparing tests assert a `Counts` record (`proven`, `compared` per check, `owed` per item, and a count per label) against a literal. Any change in a count, or a missing or unknown kind, fails the test. Treat it as process, with an optional ArchUnit check that every `@TestFactory` or comparing test calls `Counts.pin(...)`. | process (+ auto) |
| **E32** | A nightly jqwik stateful property (`ActionChain`) over 1,000 or more seeds, with Nullable wrappers whose `createNull` options inject faults by seeded draws and with per-run fault rates (swarm testing). It checks `B18`'s invariants after every step, uses `Statistics.coverage` to require each listed rare state and fault at least once a night, and appends each failing seed and command list to a protected corpus that a gate test replays. | auto (dormant, nightly) |
| **E34** | PIT, nightly, over `kernel` and `domain`: a judge counts `SURVIVED` in `mutations.xml` per project against a committed count that may not rise, and each equivalent mutant is an `[allow]` entry. No percentage threshold (see below). | auto (planned, nightly) |
| **E35** | Narrow tests of the store wrapper: each opens a fresh database in a temporary directory in its arrange step and reads back through a second wrapper instance. ArchUnit bans an in-memory engine standing in for the real one (for example H2 or HSQLDB in a project that ships SQLite) and any assertion on query text. Dormant until the store exists. | auto (dormant) |

### E1 and E5: Nullable wrappers instead of Mockito

Fail resolution before any code compiles:

```kotlin
// lcg.java-base
val mocking = setOf("org.mockito", "org.easymock", "io.mockk", "org.jmock", "org.powermock", "org.jmockit",
                    "org.wiremock", "com.github.tomakehurst", "org.mock-server")
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group in mocking)
            throw GradleException("E1: ${requested.group}:${requested.name} is a mocking library ([banned].mocking); use the wrapper's createNull")
    }
}
```

`spring-boot-starter-test` brings Mockito in transitively, so the rule catches it as well. Under Maven, use enforcer `bannedDependencies` with `<exclude>org.mockito</exclude>` and the rest of the list.

A Java Nullable wrapper corresponding to the guide's Example 3:

```java
/** Sends notices; Nullable through createNull (E1, E5). */
public final class NoticeWrapper {
  private final Backend backend;
  private final ArrayList<NoticeSent> recorded = new ArrayList<>();   // infrastructure may mutate (C2 binds the core only)

  private NoticeWrapper(Backend backend) { this.backend = backend; }

  /** Real factory: stores configuration, connects on first use (no I/O here). */
  public static NoticeWrapper create(EndpointConfig endpoint) { return new NoticeWrapper(new Backend.Real(endpoint)); }

  /** Nullable factory: every send succeeds. */
  public static NoticeWrapper createNull() { return createNull(new NullConfig(Seq.of(Result.ok(Unit.UNIT)))); }

  /** Nullable factory with configured responses; running out fails loudly. */
  public static NoticeWrapper createNull(NullConfig config) { return new NoticeWrapper(new Backend.Null(new ArrayDeque<>(config.responses().toList()))); }

  /** Sends a notice, or replays the next configured response. */
  public Result<Unit, NoticeError> send(RecipientId recipient, TextKey key) {
    Result<Unit, NoticeError> outcome = switch (backend) {
      case Backend.Real real -> real.endpoint().sendWire(recipient, key);
      case Backend.Null nul -> {
        if (nul.canned().isEmpty()) throw new IllegalStateException("NoticeWrapper.createNull responses exhausted"); // E5
        yield nul.canned().removeFirst();
      }
    };
    outcome.fold(ok -> recorded.add(new NoticeSent.Dispatched(recipient, key)), err -> false);
    return outcome;
  }

  /** Recorded output behaviours for scenario assertions (E5, E9). */
  public Seq<NoticeSent> outputs() { return Seq.copyOf(recorded); }

  private sealed interface Backend {
    record Real(EndpointConfig endpoint) implements Backend {}
    record Null(ArrayDeque<Result<Unit, NoticeError>> canned) implements Backend {}
  }
}
```

`Infrastructure` may throw (C1 binds `Kernel`, `Domain` and `Application`), and loud failure on exhaustion is what `E5` asks for. `Unit` stands for the kernel's single-value type.

### E8: per-test budgets on the JVM

The JVM changes how per-test budgets behave:

- **The first test in a fork pays the start-up costs:** class loading, static initialisation and interpreted (not yet JIT-compiled) code. A domain test that takes 2 ms when warm can take 40 ms when it runs first.
- **Gradle's JUnit XML `time`** for a test includes its `@BeforeEach` and extension overhead, and the first test of a class also pays for loading that class.
- **Garbage collection pauses** add noise.

Measure in-process, on the test body only, never with a hard timeout that interrupts the test.

**What the starter does** ([`TimeBudget`](../../examples/java-starter/testing/src/main/java/starter/testing/TimeBudget.java), compiled): one Jupiter `InvocationInterceptor`, switched on for every test by service-loader autodetection (`junit.jupiter.extensions.autodetection.enabled=true` in `testing/src/main/resources/junit-platform.properties`), times the test body with `System.nanoTime()` and fails the test at once if it is over its tier's budget. The tier comes from the test's tag: untagged is logic (20 ms), `@Tag("scenario")` 200 ms, `@Tag("architecture")` 10 s, and `@Tag("nightly")` is not judged. Its failure message starts with its check id, `[E8-time-budget]`, which is how the meta-test proves it against its breach (an untagged test that sleeps 60 ms). Under PIT it is switched off with `-Dstarter.budgets=off`, because instrumented code is slow by design.

```java
public final class TimeBudget implements InvocationInterceptor {
    public static final String CHECK_ID = "E8-time-budget";

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> call,
            ExtensionContext context) throws Throwable {
        long start = System.nanoTime();
        invocation.proceed();
        Duration took = Duration.ofNanos(System.nanoTime() - start);
        Duration budget = budget(context.getTags());          // by tag, as above
        if (!"off".equals(System.getProperty("starter.budgets")) && took.compareTo(budget) > 0) {
            throw new AssertionFailedError("[" + CHECK_ID + "] " + context.getDisplayName() + " took "
                    + took.toMillis() + " ms, over its " + budget.toMillis() + " ms budget. ...");
        }
    }
}
```

That is enough to start. As the suite grows, add the parts the starter does not have *(not compiled)*: judge after the run instead of failing in the test, so the gate can print the 10 slowest tests and each stage against its budget, and warm the JVM up first:

1. **A warm-up `LauncherSessionListener`** (JUnit 5.8 or later, registered in `META-INF/services/org.junit.platform.launcher.LauncherSessionListener`) runs once per fork before any test. It loads the kernel and domain classes and runs a canned `decide`/`evolve` sequence a few hundred times. Keep it under its own small budget, because it counts in the stage time.
2. **A timing extension** that records instead of failing, measuring only the test method body with the monotonic clock:

   ```java
   /** Records each test body's elapsed time for the inventory judge (E8). */
   public final class BudgetExtension implements InvocationInterceptor {
     @Override public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> call,
                                               ExtensionContext context) throws Throwable {
       long start = System.nanoTime();
       try { invocation.proceed(); }
       finally { context.publishReportEntry("elapsed_us", Long.toString((System.nanoTime() - start) / 1_000)); }
     }
   }
   ```

   Register it globally with `junit.jupiter.extensions.autodetection.enabled=true` and a `META-INF/services/org.junit.jupiter.api.extension.Extension` file. Report entries reach the **Open Test Reporting** XML (`junit.platform.reporting.open.xml.enabled=true` plus the `junit-platform-reporting` artifact). This page is not sure whether Gradle's legacy JUnit XML keeps them. If yours does not, have the extension append a line to `build/test-timings/<pid>-<thread>.tsv` instead.
3. **The inventory judge** reads every tier's results and timings. It fails any untagged test over its tier budget (logic 20 ms, scenario 200 ms, narrow 200 ms, exact comparison 200 ms), fails the architecture suite if its total exceeds 10 s, prints the 10 slowest tests, and prints each stage's time against its budget.
4. **jqwik runs on its own engine,** so Jupiter extensions do not apply to properties. Implement the same timing as a jqwik `AroundPropertyHook`, or judge properties on the XML `time` and give them headroom.
5. **Make the forks steadier:** use a fixed heap (`minHeapSize = maxHeapSize`) and a small fixed `maxParallelForks`. Consider `-XX:TieredStopAtLevel=1` (C1 only) for short logic-test JVMs, but measure it first, because heavy property tests can get slower without C2.

JUnit's own `junit.jupiter.execution.timeout.default` (for example `"200 ms"`) is a useful hang guard, but not a budget judge. It includes cold start and interrupts the test instead of reporting.

**The stage ratchet** is neutral. The gate script times each Gradle invocation (format, compile, lint, test, architecture, breaches) and appends the times to a metrics file kept outside the working branch. It then compares each stage with the median of the last 20 green runs, and fails on growth over 10 percent unless a recorded reason exists. Keep separate tables for cold and warm caches, locally and on CI, as the guide asks.

### E10: jqwik properties, fixed seeds in the gate and fresh seeds at night

jqwik annotation values must be compile-time constants, so one property cannot switch seeds by environment. The starter's properties ([`LoanProperties`](../../examples/java-starter/loans/domain/src/test/java/starter/loans/domain/LoanProperties.java), compiled) run the gate's fixed seed and return their verdict as a `boolean`, which is jqwik's own assertion:

```java
class LoanProperties {
    private static final String SEED = "20261107";

    @Property(seed = SEED, tries = 200)
    boolean decideIsDeterministic(
            @ForAll("dates") LocalDate today, @ForAll("commands") Loan.Command command, @ForAll("states") Loan.State state) {
        return Loan.decide(today, command, state).equals(Loan.decide(today, command, state));
    }

    @Provide
    Arbitrary<Loan.Command> commands() {
        return Arbitraries.oneOf(days().map(Loan.Renew::new), Arbitraries.just(new Loan.Return()));
    }
    // dates(), states(), days() and the other properties: see the file
}
```

For the nightly run with fresh seeds, write each property body once as a private method and give it a second thin entry point with no `seed` and more tries, tagged `nightly` (jqwik's `@net.jqwik.api.Tag`) and excluded from the gate *(not compiled)*. A boolean property is not seen by the starter's counting-assertion judge, which runs on Jupiter only; to count jqwik assertions, implement the judge as a jqwik `AroundTryHook` as well.

- **Failures.** jqwik prints the failing seed and the shrunk sample. The nightly job turns each failure into an `@Example` method (jqwik's plain test) named for the behaviour and holding the shrunk values. Set `jqwik.failures.after.default = PREVIOUS_SEED` in `src/test/resources/jqwik.properties`, so a local re-run tries the failing seed first.
- **Stateful properties** use `net.jqwik.api.state.ActionChain` (jqwik 1.7 or later).
- **Generator checks.** A generator test asserts that every value from `arbitrary.sampleStream().limit(1_000)` is valid, and `Statistics.collect(...)` and `Statistics.coverage(...)` report spread.
- **The property list** is checked by an ArchUnit test over test classes. For each `@AggregateRoot`, `<Aggregate>Properties` must declare methods named `reachableStatesKeepInvariants`, `decideIsDeterministic`, `refusalChangesNothing`, `evolveIsTotal`, `terminalStateAcceptsNoCommand`, one method named `model*` or `metamorphic*`, one named `commandSequences*`, and `snapshotEqualsReplayAtEveryPrefix` where snapshots are kept.

### E22: counting assertions in JUnit 5

Pass the counting object into each test as a parameter, so there is no static or thread-local state for `E13` to object to. The starter calls it `Expect` (a name that does not clash with the harness's `Check` type) and has the extension fail the test straight after its body returns ([`Expect`](../../examples/java-starter/testing/src/main/java/starter/testing/Expect.java) and [`CountedAssertions`](../../examples/java-starter/testing/src/main/java/starter/testing/CountedAssertions.java), compiled; comments abridged):

```java
/** The counting assertion module (E22): tests assert only through it. */
public final class Expect {
    private int count;

    Expect() {}

    /** Asserts that a claim holds. */
    public void that(boolean condition, String claim) {
        count++;
        if (!condition) throw new AssertionFailedError("expected that " + claim);
    }

    /** Asserts that two values are equal. */
    public <T> void equal(T expected, T actual) {
        count++;
        if (!Objects.equals(expected, actual))
            throw new AssertionFailedError("expected " + expected + " but was " + actual, expected, actual);
    }

    int count() { return count; }
}

/** Judge for E22: hands each test an Expect and fails a test that counted no assertion. */
public final class CountedAssertions implements ParameterResolver, InvocationInterceptor {
    public static final String CHECK_ID = "E22-asserts-something";
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(CHECK_ID);

    @Override public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == Expect.class;
    }

    @Override public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return context.getStore(NAMESPACE).getOrComputeIfAbsent(Expect.class, k -> new Expect(), Expect.class);
    }

    @Override public void interceptTestMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> call, ExtensionContext context) throws Throwable {
        invocation.proceed();
        Expect expect = context.getStore(NAMESPACE).get(Expect.class, Expect.class);
        if (expect == null || expect.count() == 0)
            throw new AssertionFailedError("[" + CHECK_ID + "] " + context.getDisplayName()
                    + " asserted nothing. Take an Expect parameter and assert through it.");
    }
}
```

Both judges are listed in `META-INF/services/org.junit.jupiter.api.extension.Extension` of the `testing` project, which every project's tests depend on, so no test can opt out by forgetting an annotation. A test that never asks for an `Expect` fails too. An `afterEach` callback works as well; the interceptor has the advantage that it judges only the test body. Three cases need more than the starter has:

- **`@TestFactory` methods** (E36) produce dynamic tests that receive no parameters. Exempt the factory method itself, and have each `DynamicTest` executable create its own `Expect` and check its count at the end.
- **jqwik properties** run on jqwik's engine, so Jupiter extensions do not see them. The starter's properties return a `boolean`, which is jqwik's own assertion; to count them, add a jqwik `ResolveParameterHook` plus `AroundTryHook` that do what the extension does *(not compiled)*.
- **Newer JUnit versions** (5.13 and later) add `computeIfAbsent` and may deprecate `getOrComputeIfAbsent`. Use whichever your pinned version offers.

The rest of E22:

- **No direct assertions.** ArchUnit over test classes bans `org.junit.jupiter.api.Assertions`, `org.junit.Assert`, `org.assertj..`, `org.hamcrest..` and `com.google.common.truth..`. The starter's `E22-counted-assertions` check selects test classes by their `@Test` or `@Property` methods and bans the first four. Checkstyle `IllegalToken` `LITERAL_ASSERT` bans Java `assert` statements in tests.
- **Tautologies.** A house Error Prone check flags `expect.equal(x, x)` (the same source text, or the same constant on both sides) and two literals on both sides. The same check serves E25.
- **No focused or pending tests.** JUnit 5 has no "focus" annotation. Pending means `@Disabled`, the `@DisabledIf*` and `@EnabledIf*` conditions, and `Assumptions` (assume calls skip a test silently). ArchUnit bans them on the main line. As a backstop, set `junit.jupiter.conditions.deactivate=*` in `junit-platform.properties`, which makes every `@Disabled`-style condition ineffective. A flat check rejects `--tests` filters and `filter {}` blocks in CI scripts and build files.

### E13: order, time and repetition (nightly)

```properties
# junit-platform.properties for the nightly run (the gate keeps default ordering)
junit.jupiter.testmethod.order.default = org.junit.jupiter.api.MethodOrderer$Random
junit.jupiter.testclass.order.default = org.junit.jupiter.api.ClassOrderer$Random
junit.jupiter.execution.order.random.seed = ${seed recorded by the nightly script}
junit.jupiter.execution.parallel.enabled = true
junit.jupiter.execution.parallel.mode.default = concurrent
```

The nightly script runs the suite twice with the same seed, once with `parallel.enabled=false` and once with `true`, and compares the per-test outcomes from the two XML sets. Any difference is a flaky test that blocks the next merge. Gradle also supports `-Djunit.jupiter...` system properties on the `Test` task (`systemProperty(...)`), so one properties file plus overrides is enough.

### E14 and E34: coverage and mutation testing (PIT)

```kotlin
// lcg.kernel and lcg.domain
plugins { jacoco; id("info.solidsoft.pitest") }
jacoco { toolVersion = libs.versions.jacoco.get() }
pitest {
    junit5PluginVersion = libs.versions.pitestJunit5.get()
    targetClasses = setOf("com.example.kernel.*", "com.example.contexts.*.domain.*")
    outputFormats = setOf("XML", "HTML")
    timestampedReports = false
    threads = 4
}
```

- **Mutation runs.** Run `pitest` nightly on core projects. A judge counts `status="SURVIVED"` in `build/reports/pitest/mutations.xml` and keeps a ratchet: the surviving count per project may not rise. `mutationThreshold` is a percentage target, which `E14` forbids as a goal, so gate only on the ratchet. The starter runs PIT on its domain inside `check` (18 mutants, all killed, in about 2 s) with `mutationThreshold = 90` as a stop-gap floor while it has no ratchet; replace it with the ratchet when the nightly run exists.
- **Changed lines.** For `B27` (surviving mutants on a core merge's changed lines), filter the XML by the diff's files and line numbers. Under Maven, `pitest-maven:scmMutationCoverage` mutates only changed files.
- **Incremental analysis** (`historyInputLocation` and `historyOutputLocation`) speeds up repeated runs.
- **Hand mutants** remain process until the ratchet runs.

---

## 9. Group B: bounded contexts, domain model and glossary

| Rule | Java check | Status |
| :--- | :--- | :--- |
| **B1** | The A1 build-file test also reads `context-map.toml`. Every project edge between contexts must be a declared relation in the declared direction, every declared relation must have an edge, and `separate-ways` must have none. Relation kinds come from `[vocabulary].relation_kind`. | auto |
| **B3** | Deciders as static functions over sealed types (see below). ArchUnit checks, for each class annotated with the kernel's `@AggregateRoot`: `public static <State> evolve(<State>, <Event>)`; `public static Result<Seq<<Event>>, <Refusal>> decide(<leading...>, <Command>, <State>)` with the leading parameter types taken from `[decider].leading`; and `public static boolean isTerminal(<State>)` where the aggregate can end. `State`, `Event` and `Command` must be sealed interfaces in the same context. | auto |
| **B4** | ArchUnit plus the glossary file: every public type, every permitted subtype of a sealed type, and every record component name in `domain` and `contracts` is a term in the context's section of the glossary. Read record components with `javaClass.reflect().getRecordComponents()`. Generic helpers are allowed by `[glossary].generic`. Translations are marked. Unused entries are **report** only. | auto + report |
| **B5** | `@AggregateRoot` marks roots. ArchUnit: no component of a root's state record (recursively, through kernel collections) has the type of another root's state, or a collection of such states. Only `<Root>Id` types may refer to other aggregates. | auto |
| **B6** | ArchUnit: every record permitted by the aggregate's `Event` sealed interface has exactly one component of the root's `Id` type. | auto |
| **B7** | ArchUnit: each event record's simple name ends in `ed` or is in `[names].past_irregular`, carries the aggregate's `Id`, and has components only of id types, constrained values or kernel value types, never a state type. | auto |
| **B8** | ArchUnit: classes named `*Store` exist only in `infrastructure`, one per aggregate root, plus the event store. Store ports in `application` are functional interfaces (`@FunctionalInterface`) or `java.util.function` types. | auto |
| **B9** | Records give structural equality. Error Prone `ReferenceEquality` (`==` on types that override `equals`) is an error. ArchUnit: a class in core that declares `equals(Object)`, `hashCode()` or `compareTo` and is not a record or an enum must be in `[allow].custom_equality`. Each listed type has jqwik law properties (reflexive, symmetric, transitive, hash agrees with equals, ordering agrees with equals), checked by name. | auto |
| **B11** | Review: whether names read as the business does is a question of meaning. | review |
| **B12** | ArchUnit: only classes annotated `@ScheduledJob`, or `@Policy(consistency = SAME_ENTRY)`, may append events of two aggregates through one journal entry API. Anything else needs `[allow].same_entry`. The `L` half is review. | auto + review |
| **B13** | A test reads `context-map.toml` (`class`, `logic`, `architecture` and `status` from `[vocabulary]`). A `transaction-script` context has no `@AggregateRoot`, and a `domain-model` or `event-sourced` context has `decide` and `evolve` per root. | auto |
| **B14** | The A1 test plus ArchUnit: `kernel` and `generic` contexts depend on no `core` or `supporting` context project, and no public kernel name contains a term from a `core` context's glossary. | auto |
| **B15** | ArchUnit: `contracts` event types are distinct from `domain` event types, and the domain declares `toPublic(<Event>) -> Seq<<PublicEvent>>`. C12's ban on `default` makes the mapping total. | auto |
| **B17** | ArchUnit: only classes in `..application.inbound..` (or one named `Inbound` class) of a downstream context depend on an upstream context's `..contracts..`, unless the relation is `conformist`. | auto |
| **B18** | ArchUnit: `public static Seq<Breach> invariants(<State>)` on each root. The scenario harness and simulation runner call it after every step and stop with the journal slice on any breach. | auto |
| **B22** | ArchUnit: simple names of classes, packages and public static methods in `domain`, `contracts` and `application` are not equal to, and do not end with, a word in `[names].vague`, unless the whole name is a glossary term. Compare CamelCase words as in A9, so `Database` does not fail on `Base`. | auto |
| **B23** | A surface test dumps every public type and member signature of `kernel`, each `contracts` project and each port package (ArchUnit or reflection), sorted, one per line, and compares the dump with the committed `api/<project>.api`. Running it with `-Papi.update` rewrites the file. The history check requires a version note and a boundary-test change in the same commit. japicmp (Gradle plugin `me.champeau.gradle.japicmp`) can add binary-compatibility checks against the last release. | auto |
| **B24** | History check: a commit touching `kernel/` runs `./gradlew check` (the full gate). A change to `api/kernel.api` is flagged for review. | auto + report |
| **B26** | History check (neutral): the vision file's word count, banned technology words and decision citation. | neutral |
| **B27** | History check, dormant until core contexts exist: properties exist before first merge (E10), plus the core's JaCoCo ratchet and PIT survivors on changed lines (E14). | auto (dormant) |
| **B19** | ArchUnit name rule in `domain` and `application`: no method or class name starting `getOrCreate`, `findOrCreate` or `ensure`. ArchUnit: no `decide` reaches an id generator (`UUID`, a counter); new ids arrive in the command. The import clauses are review. | auto (dormant) |
| **B20** | A test reads the committed policy table (`standards/policies.toml`) and the classes annotated `@Policy` in `application`: every policy is listed and every listed policy exists. Each policy has a jqwik property that feeds it a duplicated event and expects no change. Whose job a reaction is, is review. | auto + review (dormant) |
| **B21** | ArchUnit, for classes annotated `@ProcessManager`: one `react(<State>, <Input>)` returning a named reaction record (next state and commands, no tuple), its own id type, other aggregates held by id only (as `B5`), and no `java.time` access. Its property list (as `E10`) names "duplicates change nothing" and "a finished process is terminal". | auto (dormant) |
| **B25** | A nightly report counts collection-typed components (kernel `Seq`, `SortedMapOf`, `SortedSetOf`) per root state record, against `[structure].max_aggregate_collections` once set, and a size-budget property over a long simulated run. A report until a decision record promotes it. | report (planned) |

### B3: a decider in Java

The starter's [`Loan`](../../examples/java-starter/loans/domain/src/main/java/starter/loans/domain/Loan.java) (compiled, abridged; its states, commands and events are sealed interfaces of records nested in `Loan`, and `Refusal` is an enum):

```java
/** The loan aggregate as a pure decider (B3). */
public final class Loan {
    private Loan() {}

    public sealed interface State permits Active, Ended {}
    public sealed interface Command permits Renew, Return {}
    public sealed interface Event permits Opened, Renewed, Returned {}
    // record Active(LoanId loanId, BookId bookId, MemberId memberId, LoanDays duration, LocalDate since) implements State
    // record Ended(LoanId loanId, MemberId memberId, LocalDate returnedOn) implements State; and so on

    /** Decides what a command does to a loan; the only place business decisions live (B3). */
    public static Result<List<Event>, Refusal> decide(LocalDate today, Command command, State state) {
        return switch (state) {
            case Active active -> switch (command) {
                case Renew renew -> renew.duration().compareTo(active.duration()) > 0
                        ? Result.ok(List.of(new Renewed(active.loanId(), renew.duration(), today)))
                        : Result.err(Refusal.NOT_AN_EXTENSION);
                case Return ignored -> Result.ok(List.of(new Returned(active.loanId(), active.memberId(), today)));
            };
            case Ended ended -> switch (command) {
                case Renew ignored -> Result.err(Refusal.LOAN_ENDED);
                case Return ignored -> Result.err(Refusal.LOAN_ENDED);
            };
        };
    }

    /** Folds one event into the state; total over every (state, event) pair (B3, C6). */
    public static State evolve(State state, Event event) {
        return switch (event) {
            case Opened opened -> start(opened);
            case Renewed renewed -> switch (state) {
                case Active active -> new Active(
                        active.loanId(), active.bookId(), active.memberId(), renewed.duration(), renewed.renewedOn());
                case Ended ended -> ended;
            };
            case Returned returned -> new Ended(returned.loanId(), returned.memberId(), returned.returnedOn());
        };
    }

    /** Whether the loan has reached its end (B3). */
    public static boolean isTerminal(State state) {
        return switch (state) {
            case Active active -> false;
            case Ended ended -> true;
        };
    }
}
```

Because all three types are sealed and no switch has a `default` (which the starter's `C12-no-default-arm` check enforces), adding a case breaks the compile wherever it is not handled. Two differences from this page's rules, both deliberate in a small starter: it uses `java.time.LocalDate` and `java.util.List` (from `List.of`, which is immutable) where a larger project would use kernel `Date` and `Seq` types (`D13`, `C2`), and it has no `@AggregateRoot` marker yet. On Java 22 or later, replace the `ignored` bindings with `_`. A record pattern over a pair (`switch (new Step(command, state))` with `case Step(Renew r, Active a)`) is also exhaustive-checked, and it mirrors the Rust `match (command, state)` most closely.

---

## 10. Group M: messaging and event envelopes

| Rule | Java check | Status |
| :--- | :--- | :--- |
| **M1** | The kernel declares marker interfaces `Command`, `Event` and `View`. ArchUnit checks three things. First, every top-level type in `..contracts..` implements exactly one of them, or is a sealed interface whose cases do. Second, contracts records declare no methods except their accessors and the generated `equals`, `hashCode` and `toString`, and static `create` factories on constrained values are allowed. Third, no store or journal API accepts a `Command` subtype as an event. | auto |
| **M2** | ArchUnit: the journal envelope record has `correlationId`, `causationId` and `version` components, of kernel types, and no generated-id call (`UUID.randomUUID` is banned by D1) reaches them. The sequence number is the journal's own type. Keep it dormant until the journal lands. | auto (dormant) |
| **M3** | ArchUnit: one dispatcher class in `application` holds the subscriber list as one ordered, declared constant; no `ServiceLoader`, classpath scanning, `CompletableFuture.runAsync` or executor fan-out in `application`. | auto (dormant) |
| **M4** | A narrow test makes the store's Nullable form fail the second write of `record`, and a scenario test shows that nothing was published. | auto (dormant) |
| **M5** | ArchUnit: no view-reading port in `application` is named `current*`, and handler methods take the event's sequence number when they read a view. Whether the handler reads the view as of that number is review. | auto + review (dormant) |
| **M6** | A scenario test with a handler that always fails expects the run to stop with a diagnosable defect. A test checks that each projection class keeps a checkpoint and is idempotent (replaying an event twice changes nothing). | auto (dormant) |
| **M7** | A concurrency scenario sends commands from timers and background work through the one queue; a narrow test opens the store twice and expects the second writer to be refused (the store takes an exclusive `FileChannel.lock`). | auto + review (dormant) |

---

## 11. Group P: persistence, codecs and versioning

| Rule | Java check | Status |
| :--- | :--- | :--- |
| **P2** | ArchUnit: records in `..codec..dto..` have components only of primitive types (no `float` or `double`), `String`, arrays, or other DTO records. No `Optional`, `Map`, `Set`, sealed type or `Map.Entry` is allowed. `kernel`, `domain` and `contracts` depend on no serialisation annotations (`com.fasterxml.jackson..`, `jakarta.json..`, `com.google.gson..`, `jakarta.xml.bind..`) and do not implement `java.io.Serializable`. Codecs decode through each value's `create`, never its constructor (C3's rule enforces this). A test compares the field-tag constants with the committed tag table, which is append-only. | auto |
| **P3** | ArchUnit: each codec has `public static byte[] encode(<T>)` (or a kernel byte sequence) and `public static Result<<T>, DecodeError> decode(byte[])`. jqwik properties named `roundTrip` and `neverFails` (`@ForAll byte[]`). One golden file per persisted kind and version in `src/test/resources/golden/<kind>/v<n>.bin`, decoded by a test. The history check keeps golden files append-only, and a decoder-table test checks one decoder per released version. | auto |
| **P6** | ArchUnit: only `hosts` (the edge) call `CommandId`'s factory, and `CommandId` never appears as a component of a domain record other than the command envelope. A narrow store test checks the unique key, and a scenario test checks that a repeated command returns `AlreadyRecorded` with the first position. | auto (dormant) |
| **P11** | ArchUnit: `evolve` methods call nothing outside their own context's `domain`, `contracts` and `kernel` (inspect `getMethodCallsFromSelf()`, including synthetic lambdas). Whether events carry the facts they used is review. | auto + review |
| **P12** | ArchUnit: every `Command` record in `contracts` has a `basedOn` component of the kernel sequence type, unless the glossary marks the command head-independent. | auto |
| **P15** | A shape test writes each persisted or sorted union's cases in order to `shapes.txt` and compares the output with the committed file line by line. A second test checks the file against `git show $(git merge-base HEAD origin/main):shapes.txt`: cases only appended, none moved or removed. When no merge base exists, skip locally and fail on CI. An empty file is refused. | auto |
| **P4** | Per tagged release, a test opens each committed golden stored instance (`src/test/resources/golden-instances/<release>/`) and compares its state hash; the history check keeps the folder append-only (`F4`). | planned |
| **P5** | A source scan of the store wrapper bans `UPDATE` and `DELETE` statements on the journal table; a narrow test and a property show that a gap or broken hash chain refuses to open. | auto (dormant) |
| **P7** | ArchUnit: the store's one public write method is `record(instance, entry, expectedPosition)` returning `Result<Position, Conflict>`, and the entry record holds the command, events, `CommandId`, `BasedOn`, date, versions and the previous hash. Durable-commit settings are checked by a narrow test; holding a write open across computation is review. | auto + review (dormant) |
| **P8** | ArchUnit: the snapshot record has `atSeq` and `checksum` components. A scenario test opens from the newest sound snapshot and passes over a torn one. | auto (dormant) |
| **P9** | The store's `createNull` takes a `failAt(Step)` option; the steps are an enum named in its documentation comment. The inventory runner checks one scenario test per step; a narrow test kills a real child JVM mid-write (`ProcessBuilder`, narrow tier only). | auto (dormant) |
| **P10** | ArchUnit: only the store writes the archive and projections, through one derivation class; each projection has `empty()` and `step(...)` and no other way to build it. A jqwik property compares the rebuilt value with the live one. | auto (dormant) |
| **P13** | For each file-rewriting tool: a round trip over a fixture with an unknown field keeps it, running twice gives identical bytes, and a narrow test shows the tool refuses its input path as its output path. | auto (dormant) |

**P15, where the order comes from.** For **enums**, `values()` returns the declaration order, which is also the `compareTo` and `ordinal()` order, so an enum used as a sort key must be append-only. For **sealed interfaces**, `Class.getPermittedSubclasses()` documents its order as unspecified. Read the order from the source `permits` clause (with JavaParser) or from the codec's tag table instead. Require an explicit `permits` clause on every persisted sealed type, so the order is visible.

---

## 12. Groups R, S and V: reliability, scope and the model reviewer

These groups are about git history, files and process, so the language matters little. The runners in [section 14](#14-the-runners-in-a-jvm-project) hold them.

| Rule | Java check | Status |
| :--- | :--- | :--- |
| **R1** | The history and inventory runners read the service-level file. Flaky-gate counts come from CI history: a red run followed by a green run on the same commit, plus quarantined tests (E30). | neutral |
| **R5** | History check: a `Prevents:` line on fix commits, and the postmortem link check. | neutral |
| **R2** | A nightly verifier re-decides golden journals and checks the hash chain and state-hash checkpoints; a narrow test plants a corrupted record and expects it found. | auto (dormant, nightly) |
| **R3** | A nightly drill restores the latest backup to a scratch directory, runs the database's full integrity and foreign-key checks, opens it, plays one step and compares the state hash; the health check fails when the last good drill is over a week old. | auto (dormant, nightly) |
| **R4** | A narrow test per repair or migration tool: without a fresh verified copy of its input, it refuses to run. | auto (dormant) |
| **R6** | A test checks that a crash report holds the journal tail, the seed and the build id (from the JAR manifest), and a tool turns a report into a JUnit regression test. | auto (dormant) |
| **R8** | Process, at release: the first ring's minimum time and sessions are recorded in the release record. | process |
| **S1**, **S2**, **S4**, **S5**, **S8** | History check. S5 counts `git diff --numstat` lines over the branch's commits. Generated sources (`build/`, annotation-processor output) are excluded, and `.java` sources never are. S8 checks that `settings.gradle.kts` includes nothing under `spikes/`, that no build file references it, and that the spike folders are no older than 14 days. | neutral |
| **S3** | An `exemplars/` Gradle project, built in the gate, holds the typed id, constrained value, decider, codec, Nullable wrapper, builder and narrow test exemplars. A test checks that the exemplar list matches the project's classes. Duplicate patterns are review. | auto + review |
| **S9** | The landing script (neutral): `set -euo pipefail`, never pipe `./gradlew` into `tail` or `head`, refuse leftover conflict markers, push only after every gate exits green, check that the remote head equals the local head, and land stacked branches in order. | process |
| **S7** | Process: a research backlog with dated, logged slots; adoption goes through a written evaluation and a decision record. | process |
| **V1**–**V8** | The model-review runner is language-neutral. For Java, give the rubric items Java path scopes (`**/src/main/java/com/example/contexts/*/domain/**`, `**/src/test/java/**`). Calibrate on Java cases (`V4`: 50 to 200 labelled cases, at least 3 faulty and 3 good per item). Keep findings advisory until calibration passes, as the Rust workspace does. For V8, the protected paths are existing `src/test/java` lines, `config/**`, `breaches/**`, `src/test/resources/golden/**` and the gate scripts. | neutral (advisory until calibrated) |

---

## 13. Rules that stay review in Java, and why

| Rule (part) | Why there is no reasonable automation |
| :--- | :--- |
| **B11**, **D8**, **G13** | They judge meaning (business language, "the user" as a concept, design depth). Word lists can feed a report but cannot decide these rules. |
| **F2** (quoted proprietary words) | Recognising quoted text from another product needs the product's text, or judgement. |
| **A7** (L half), **A17** (invariant in the shell), **B12** (L half), **P11** (facts carried) | Bytecode shows calls, not intent. Whether an `if` in a workflow re-checks an aggregate invariant is a judgement. |
| **C9** (refusal turned into a default), **C14** (logged, then defaulted) | Mechanically, `fold(ok -> x, err -> DEFAULT)` looks the same as a legitimate mapping. |
| **E9** (expected list is a literal), **E11** (domain phrasing), **E16** (builders read in domain language), **E25** (local variables, helpers) | These need data-flow or language judgement beyond a flat rule. |
| **E14** (coverage is not a target in prompts and reports) | Prompts and reports are prose. |
| **S3** (a second way of doing the same thing) | Recognising two ways of doing the same thing is a judgement. |
| **I9** | An attended monthly session. |

Some rules are *more* automatable in Java than in Rust, because javac and Error Prone know every type:

- **`D4`'s** type-inferred conversions.
- **`C6`'s** unlisted partial calls (they can be matched inside an Error Prone check).
- **`E12`'s** crash-versus-assertion replay, which the JUnit XML failure type settles.

One rule is *harder*: `I6`, because javac inlines constants (see [section 3](#3-group-i-governance)).

---

## 14. The runners in a JVM project

The guide's five runners are language-neutral programs. Write them in any language (a JVM `tools/` project keeps everything in one build). Here is what each one reads in a JVM project:

- **The architecture check** is the `tests/architecture` project. It runs ArchUnit over compiled classes, flat text and TOML parsing of `settings.gradle.kts`, every `build.gradle.kts` and `gradle/libs.versions.toml` (A1, A10, B1, H2), and JavaParser where source is needed (G4 class length, nesting depth, P15 `permits` order). It runs in the gate as its own `Test` task with a 10 s budget.
- **The history check** runs `git log`, `git diff --numstat` and `git show <merge-base>:<path>` against:
  - decision records, the rule table, the allow list and the context map;
  - `gradle.lockfile` and `gradle/libs.versions.toml` diffs (I3, and H4's new-package lines in the commit message);
  - `api/*.api` surface files (B23, B24) and `shapes.txt` (P15);
  - `config/checkstyle/suppressions.xml` counts (the G3 ratchet);
  - `src/test/java` hunks mapped to `@Test` method line ranges with JavaParser (E23);
  - for E12, the parent-commit replay with Gradle and the JUnit XML failure types;
  - `*.received.*` files (E33) and golden files (P3).
- **The inventory check** reads `build/test-results/<suite>/TEST-*.xml` (Gradle) or `target/surefire-reports/TEST-*.xml` (Maven). Each `<testcase>` has `classname`, `name` and `time`. It takes the tier from the suite directory, reads the in-process timings from E8, and reads the `spec/` file list for E36 (testcase names `spec/<case>`).
- **The supply-chain check** reads every `gradle.lockfile` (or the Maven lockfile plugin's output) and `gradle/verification-metadata.xml`, checks Maven Central publish dates (H4), runs osv-scanner or OWASP dependency-check (H5), and runs the two-clone hash comparison of `build/libs/**` and `build/distributions/**` (H6).
- **The model-review runner** works on a fresh, read-only clone, with the diff, the rubric items whose Java path scopes the diff touches, and the automated results (V2). Nothing in it is Java-specific except the path scopes.

---

## 15. Planted breaches and the meta-test (F5)

[docs/self-test-harness.md](../self-test-harness.md) explains the harness in language-neutral terms, using the starter as its worked example.

**What the starter does** (compiled; `./gradlew check` runs it). `breaches/` is one Gradle project, included in `settings.gradle.kts` but depended on by no other project. Each breach is a folder named by its check id, `breaches/<check-id>/src/` (for example `breaches/A10-one-wrapper/src/starter/loans/application/GsonOutsideItsWrapper.java`), and the project creates one source set per folder, so each breach compiles alone under the same compiler settings as production code. The check registry (`architecture/.../Checks.java`) lists every check by the same id, and the meta-test (`MetaTest.java`) reads the real code and each breach's classes and sources **by path**, then asserts three things: the set of check ids equals the set of breach folders; every check passes on the real code and selects something there (or, if marked dormant with its backlog item, selects nothing); and every breach fires its own check and no other. A fourth test reads `standards/rules.toml` and checks each row's `checks` field against the registry: every named check exists, and every registered check is named by a rule. The two test-runner judges (`E8`, `E22`) are proved by running each breach's tests in-process with JUnit's `EngineTestKit` and reading the failure messages that carry the judge's id.

**The alternative** for a larger project with per-layer convention plugins: make `breaches/` a separate Gradle build, never included in the main `settings.gradle.kts`, where each breach is a tiny project that applies the convention plugin of the layer it imitates, through `includeBuild("../build-logic")`. Then Checkstyle, PMD and forbidden-apis configurations are exercised exactly as in production. Either way, the meta-test proves, for each rule, that the check fires on its breach and on nothing else:

- **ArchUnit rules** are evaluated in-process on the breach classes: `rule.evaluate(new ClassFileImporter().importPath(breachClassesDir))` gives an `EvaluationResult`, and `hasViolation()` must be true for the breach's own rule and false for every other rule. Compile all breach sources **once**, in the same build (the starter's source sets) or in one breach build, and import each breach's class directory, so the gate never builds the workspace twice (guide section 5). The starter compiles breaches with the production compiler settings, so a breach must break exactly one rule and nothing else.
- **Checkstyle, PMD and forbidden-apis** are run through their Java APIs on the breach sources or classes (Checkstyle's `Checker`, PMD's `PmdAnalysis`, and forbidden-apis' `Checker` class), and the meta-test asserts that the reported check `id` equals the breach's rule id.
- **Custom Error Prone checks** are proved with `CompilationTestHelper` (`// BUG: Diagnostic contains: C6`). Stock javac and Error Prone settings are proved by compiling the breach file in-process with `ToolProvider.getSystemJavaCompiler()` and the same options.
- **Build-file rules** (A1, A10, H2, F1) are proved by running the flat test on the breach's `build.gradle.kts`.
- **Non-empty selection.** Use the harness's method, not ArchUnit's guard. ArchUnit fails a rule whose `that()` selects nothing by default (`archRule.failOnEmptyShould=true` in 1.x), which would fail every breach target, since a breach has classes in one layer only. So the starter evaluates each rule with `allowEmptyShould(true)` and has each check report how much it selected (`Check.Verdict.selected`, from the same predicate as the rule's `that()`); the meta-test then fails a live check that selects nothing on the real code (it is vacuous) and a dormant check that selects something (it should wake up). See [self-test-harness.md, section 4](../self-test-harness.md#4-vacuity-a-check-must-select-real-code). For per-layer tool configurations, the meta-test asserts that at least one main-build project applies each layer plugin, unless the rule is listed as dormant with its backlog item.
- **Failure messages** name the rule id (ArchUnit `.as("A10 ...")`, a Checkstyle `id`, an Error Prone summary prefix), the offending edge or member, the allowed alternative and the decision record (ArchUnit `.because(...)`, a Checkstyle `<message>`).
- **The constant-only reference.** Each bytecode check's breach set includes a reference through a compile-time constant (`I6`).

Gradle TestKit (`GradleRunner.create().withProjectDir(dir).withArguments("check").buildAndFail()`) can drive a whole breach build when a check exists only as a Gradle task. Each such run costs seconds, so keep TestKit for the few rules that need it.

---

## 16. Keeping the gate fast

Section 5 of the guide comes from a Rust workspace. The JVM counterparts:

- **Gradle caches.** Set `org.gradle.caching=true`, `org.gradle.configuration-cache=true` and `org.gradle.parallel=true` in `gradle.properties`. The configuration cache is the reason the checks above use providers and avoid reaching into other projects at execution time. A remote build cache, populated by CI and read by developers, makes cold local builds warm.
- **Test forking.** Use `maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)` on each `Test` task. **Never set `forkEvery = 1`**: each new JVM pays start-up, class loading and JIT warm-up again. This is the JVM version of the guide's "one test binary per crate" advice. Within a fork, JUnit's parallel mode (E13) adds concurrency without new JVMs.
- **Keep debug information.** The guide's advice to turn debug information off does *not* carry over. javac's line tables are cheap, and without them (`-g:none`) stack traces lose their line numbers. JIT warm-up is the JVM cost to manage instead (E8).
- **Start-up time.** Consider class-data sharing for test JVMs (`-XX:SharedArchiveFile=...`, or `-XX:+AutoCreateSharedArchive` on JDK 19 or later), and `-XX:TieredStopAtLevel=1` for short logic-test forks. Measure each before adopting it, as the guide requires.
- **ArchUnit import cost.** ArchUnit's JUnit 5 support caches imported classes per `@AnalyzeClasses` location, so give every architecture test class the same `packages` and `importOptions` to share one import.
- **One build, not two.** The breach meta-test reuses compiled breach classes and in-process tool APIs (section 15), and never runs a second Gradle build of the workspace.
- **Keep the quick check quick.** Run the logic-tier tests with the build cache on, compile-avoidance (Gradle's ABI-based recompilation) and the configuration cache, under 15 s. A kernel change runs the full gate (`B24`).
