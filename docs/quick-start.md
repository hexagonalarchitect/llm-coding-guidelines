# Quick start: day one in an empty repository

This page takes an empty repository to a working gate in a day, in any language. Each step names the rules it starts and says what to do in any language; the **Java** paragraphs work it through with the [Java starter](../examples/java-starter/README.md), a Gradle project that already does every step and whose `scripts/gate.sh` runs green. For an existing code base, see [the existing-project variant](#an-existing-project) at the end.

The order matters. The toolchain and the build come first because every later check runs inside them; the meta-test comes with the first check, not after the tenth, because a check that has never been seen to fail guards nothing (`F5`).

Terms you may not know are in the [glossary](glossary.md). The rules themselves, each with its statement, are in the [guide](../README.md#4-the-rules-by-group).

**Java, the short way.** Copy the starter's committed files only (never its `build/` or `.gradle/` folders, which a plain `cp -r` would bring along from a checkout that has been built), then rename it:

```sh
# from the root of this repository: only tracked files, no build output
git archive --format=tar HEAD:examples/java-starter | (mkdir -p ~/src/lending && tar -x -C ~/src/lending)
# or, from a plain copy of the folder:
# rsync -a --exclude build --exclude .gradle --exclude .jqwik-database examples/java-starter/ ~/src/lending/
cd ~/src/lending && git init
./gradlew check            # compile with -Werror, Error Prone and NullAway, tests, PIT, the meta-test
scripts/gate.sh            # the same plus a smoke run of the host, against a 2-minute budget
```

The starter's `.gitignore` already ignores `build/`, `.gradle/` and `.jqwik-database`. Then rename the `starter` package root to yours: the source folders, the `package` lines, `NullAway:AnnotatedPackages` in `build.gradle.kts`, the package constants in `architecture/.../Checks.java`, and the `breaches/*/src/` folders. Run `./gradlew check` after each rename. The steps below explain what you copied, so you can grow it, or rebuild it in another language.

**Java, the long way: what to copy verbatim.** If you build your own project step by step instead, these starter files are domain-free and can be copied as they are, changing only the package line (paths under [`examples/java-starter/`](../examples/java-starter/README.md)):

| File | What it is |
| :--- | :--- |
| `architecture/src/test/java/starter/architecture/Check.java` | the check kinds (ArchUnit rule, source scan, test-runner judge) and their verdict |
| `architecture/src/test/java/starter/architecture/Target.java` | how a body of code (the real code, or one breach) is read and its tests run |
| `architecture/src/test/java/starter/architecture/MetaTest.java` | the meta-test, including the rule-table comparison |
| `architecture/build.gradle.kts` | how the meta-test gets the real code's and the breaches' paths (edit `realProjects`) |
| `breaches/build.gradle.kts` | one source set per breach folder, nothing packaged |
| `testing/src/main/java/starter/testing/Expect.java` | the counting assertion module (`E22`) |
| `testing/src/main/java/starter/testing/CountedAssertions.java` | the judge that fails an assertion-free test (`E22`) |
| `testing/src/main/java/starter/testing/TimeBudget.java` | the per-test time budget judge (`E8`) |
| `testing/src/main/resources/META-INF/services/org.junit.jupiter.api.extension.Extension` and `testing/src/main/resources/junit-platform.properties` | switch both judges on for every Jupiter test |
| `testing/build.gradle.kts` | the test-support project every project's tests depend on |
| `kernel/src/main/java/starter/kernel/Result.java` | the kernel's `Result` |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `scripts/gate.sh`, `.gitignore` | compiler flags, locking, versions, project list, gate (edit the project list) |

`architecture/src/test/java/starter/architecture/Checks.java` is the one harness file you rewrite: it names your packages and your checks. Copy the breach folders only for the checks you keep, and write the lockfiles afresh with `./gradlew resolveAndLockAll --write-locks`.

---

## 1. Pin the toolchain and lock the dependencies (`H1`, `H2`, `H4`)

**Any language.**

- Pin the compiler or runtime to an exact version in a committed file, never a floating channel, and pin the build tool's own launcher (a wrapper) to an exact version and checksum (`H1`).
- Put every direct version in one central file, exact, with no ranges (`H2`).
- Turn on the package manager's lockfile for every dependency, transitive ones included, commit it, and make the gate restore in locked mode so that a drifting lockfile fails (`H2`).
- Every version you adopt is at least 14 days old, and the commit that adds a package names its licence and its reason (`H4`). Until the supply-chain runner exists ([step 10](#10-what-to-defer-and-when)), check the age by hand on the registry page.

**Java.** The starter commits the Gradle wrapper, asks for a Java 21 toolchain, keeps every version in [`gradle/libs.versions.toml`](../examples/java-starter/gradle/libs.versions.toml), and locks every configuration of every project in `STRICT` mode ([`build.gradle.kts`](../examples/java-starter/build.gradle.kts)):

```kotlin
subprojects {
    dependencyLocking {
        lockAllConfigurations()
        lockMode = LockMode.STRICT          // a configuration with no lock state fails to resolve
    }
    tasks.register("resolveAndLockAll") {
        notCompatibleWithConfigurationCache("resolves configurations at execution time")
        doFirst { require(gradle.startParameter.isWriteDependencyLocks) { "run with --write-locks" } }
        doLast { configurations.filter { it.isCanBeResolved }.forEach { it.resolve() } }
    }
}
```

After changing a version, run `./gradlew resolveAndLockAll --write-locks` and commit the lockfiles with the change.

Pin the Gradle wrapper to an exact version and the distribution's SHA-256, which Gradle publishes beside each distribution (`https://services.gradle.org/distributions/gradle-<version>-bin.zip.sha256`):

```sh
./gradlew wrapper --gradle-version 8.14.3 --gradle-distribution-sha256-sum <the published SHA-256>
```

This writes `distributionSha256Sum` into `gradle/wrapper/gradle-wrapper.properties`, and the wrapper then refuses a download with any other checksum. The starter's wrapper is not pinned this way yet: the environment it was built in could not reach `services.gradle.org` to read the checksum, so do this step when you copy it. The starter stops there; before you rely on it, add the wrapper's `distributionSha256Sum`, an exact JDK check and `gradle/verification-metadata.xml` ([java.md, section 2](languages/java.md#2-group-h-supply-chain-and-reproducible-builds)). Other languages: [Kotlin](languages/kotlin.md), [C#](languages/csharp.md), [TypeScript](languages/typescript.md).

## 2. Warnings are errors (`F1`)

**Any language.** Set warnings-as-errors, the extra warnings you keep, the language version and overflow checking once, centrally; no package may override them. Pin one formatter and one linter configuration. A suppression is an `[allow]` entry in the rule table (step 3), scoped to one member.

**Java.** The starter sets `-Xlint:all -Werror` and Error Prone with NullAway as an error for every project, in one `subprojects {}` block. On day one this already pays: a model that returns `null` from `Result.ok` fails at `compileJava` with NullAway's message, and `LocalDate.now()` with no zone fails with Error Prone's `JavaTimeDefaultTimeZone`. Java has no switch that traps integer overflow; [java.md, F1](languages/java.md#1-group-f-compiler-hygiene-and-self-proving-rules) gives the nearest equivalent.

## 3. Create the layer modules and copy the rule table (`A1`, `F4`, `I1`)

**Any language.**

1. Make one build unit (crate, Gradle project, Maven module, `.csproj`, workspace package) per layer of the [layer table](../README.md#example-1-package-layers-third-party-packages-and-vocabulary-checks-a1-a10-a9-b22): `kernel/`, then for your first context `contexts/<context>/domain/` and `contexts/<context>/application/`, one `infrastructure/<wrapper>/` per external system, `hosts/<name>/`, test packages under `tests/`, and `breaches/` for the planted breaches. Add `contracts/` and `codec/` when a second context or the first persisted form needs them, and the composition package (`Composition`, called `Day` in the guide) when a second context's application appears.
2. Let each unit depend only on what its row allows. The build graph then holds most of `A1` before any check exists.
3. Copy [templates/rules.toml](../templates/rules.toml) to `standards/rules.toml`. Keep the path patterns for your build system and delete the others; unless you are reimplementing a reference program, delete the `Import` and `ImportHost` layer rows and the `reimplementation = "whole"` rule rows (`A5`, `A7`, `D4`, `D10`, `E15`), and drop `Import` from `B19`'s `applies_to`. Every rule starts `planned` (or `process`). The schema is [docs/rule-table.md](rule-table.md).
4. Copy [templates/budgets.toml](../templates/budgets.toml) to `standards/budgets.toml`; you will fill it in at step 5.
5. **Make these edits before the first commit.** Until the two files are first committed as principle files, every change is free: delete rows, rename stages, set numbers. From that commit on, they change only by adding or tightening unless the commit cites a dated approval (`F4`): removing a row is then a loosening, and a new stage budget needs its decision record ([rule-table.md, section 6](rule-table.md#6-tightening-only-f4)). Until the history runner exists, hold that by review.

**Java.** The starter's own copy is [`standards/rules.toml`](../examples/java-starter/standards/rules.toml), adjusted exactly this way and with its layer patterns set to its layout. Its [`settings.gradle.kts`](../examples/java-starter/settings.gradle.kts) has one project per layer for one context, `loans`: `:kernel`, `:loans:domain`, `:loans:application`, `:infrastructure:notices`, `:host`, plus `:testing` (the test support of steps 7 and 8), `:architecture` (the checks and the meta-test) and `:breaches`. Its package roots follow the layers (`starter.kernel`, `starter.loans.domain`, ...), so the checks can select code by package. It is flatter than the template's `contexts/<context>/...` paths; match the template's `gradle` patterns to your layout, or the layout to the patterns. Java has no re-exports, so the guide's facade (`A1`) becomes "the application depends on its domain with `implementation`, never `api`"; the starter, being small, lets the host name domain types directly and says so.

## 4. The first three checks, each with its planted breach and the meta-test (`F5`)

**Any language.** Build the self-test harness with the first check, not later ([docs/self-test-harness.md](self-test-harness.md) explains it in full):

- a **check registry**: one list of every automated check, each with an id made of its rule id and a short name (`A1-layers`);
- a **breach folder** per check, `breaches/<check-id>/`, holding the smallest code that breaks that check and nothing else, in a build unit nothing depends on;
- a **meta-test** that asserts three things: the set of check ids equals the set of breach folders; every check passes on the real code and selects something there (a check that selects nothing is vacuous), unless it is marked dormant with the backlog item in which its target appears, in which case it must select nothing; and every breach fires its own check and no other.

Start with three checks that models break most often and that are cheap to write:

| Check | Rule | Forbids | Breach |
| :--- | :--- | :--- | :--- |
| `A1-layers` | `A1` | a layer reaching one its row does not allow | a domain class naming an application type |
| `D1-pure-core` | `D1`, `D2`, `D12` | clocks, ambient randomness and hash-ordered collections in kernel and domain | a domain class reading today's date from the clock |
| `E1-no-mocking` | `E1` | any mocking library, anywhere | a test fixture calling a mocking library |

When a check and its breach pass the meta-test, set the rule's `status` in `standards/rules.toml` (`partial` with `parts_pending`, or `built`) and name the check in the row's `checks` field (`checks = ["D1-pure-core"]` on `D1`, `D2` and `D12`). The meta-test compares those fields with the registry, so a check no rule names, or a rule that names a check that does not exist, fails the gate. Then plant one change in the real code (a clock read in the domain), see the meta-test fail with the check's id and the offending member, and restore it.

**Java.** The starter's three are ArchUnit rules in [`Checks.java`](../examples/java-starter/architecture/src/test/java/starter/architecture/Checks.java), each registered with what it selects as well as what it forbids:

```java
static Check noAmbientStateInCore() {
    return Check.Arch.live("D1-pure-core",
            resideInAnyPackage(KERNEL, DOMAIN),                       // what it selects: vacuous if empty
            noClasses().that().resideInAnyPackage(KERNEL, DOMAIN)    // what it forbids
                    .should().dependOnClassesThat().belongToAnyOf(Clock.class, InstantSource.class,
                            Random.class, SecureRandom.class, ThreadLocalRandom.class, UUID.class,
                            HashMap.class, HashSet.class)
                    .orShould().callMethodWhere(clockRead)
                    .because("the core takes time and randomness as parameters and keeps a stable order"));
}
```

`A1-layers` is ArchUnit's `layeredArchitecture()` over the layer packages, in the same file; [java.md, A1 (b)](languages/java.md#4-group-a-architecture-layers-and-dependencies) explains the form and what package layers cannot express. `D1-pure-core` bans clock reads, not `java.time` as a whole: value types such as `LocalDate` are allowed, and `D13` governs which time types a signature uses. Its breach is [`breaches/D1-pure-core/src/starter/loans/domain/DueDateFromTheClock.java`](../examples/java-starter/breaches/D1-pure-core/src/starter/loans/domain/DueDateFromTheClock.java). The `:breaches` project compiles each folder as its own source set, and [`MetaTest.java`](../examples/java-starter/architecture/src/test/java/starter/architecture/MetaTest.java) reads the real code's and each breach's classes by path. The starter also has `A10-one-wrapper` (Gson only in its wrapper) and a dormant `A1-codec-layer`, which shows how a check waits for code that does not exist yet.

## 5. A gate script with cold and warm budgets (`E8`, `S9`)

**Any language.** One script is the gate, locally and on CI. It:

- runs every stage (restore, format, build, lint, architecture tests and meta-test, other tests) and exits non-zero if any fails;
- never pipes a stage into `tail`, `head` or anything else whose exit status replaces the stage's (`set -euo pipefail` in bash), because `gate | tail && git push` pushes past a red gate (`S9`);
- times each stage and the whole run, and fails a stage over its budget.

The template's stage names (`restore`, `format`, `build`, `lint`, `architecture`, `tests`, `breaches`) come from a Rust stack; [templates/budgets.toml](../templates/budgets.toml) maps each to Gradle tasks. Rename the rows to your stages before the file's first commit, while that is free (step 3).

Keep one budget table per **track**: warm local, cold local (build output removed), warm CI and cold CI (no restored cache). A cold run must neither fail against warm numbers nor loosen them. The template's numbers come from the reference code base's Rust stack; before you enforce them, run the gate three times per track on your own machine and on CI, and set each budget to about 1.5 times the slower run, with a comment saying where it came from. The full gate stays under 2 minutes and the quick inner-loop check under 15 seconds (`E8`).

**Java.** The starter's [`scripts/gate.sh`](../examples/java-starter/scripts/gate.sh) runs `./gradlew check` and a smoke run of the host, and fails over a 120 s total; it was green in 16 to 27 s warm on a 4-core cloud container. Grow it into one timed Gradle invocation per stage. For a cold local run, use `./gradlew clean check --no-build-cache` (or a fresh clone); for cold CI, a run with no restored `~/.gradle` cache. The JVM's cold cost is mostly dependency download, daemon start and configuration, so the cold and warm numbers differ more than in a compiled-to-native stack; see [the guide's Section 5](../README.md#5-keeping-the-gate-fast).

## 6. The first decider and its property tests (`B3`, `C1`, `C3`, `C12`, `G7`, `E10`)

**Any language.** Write the first aggregate as a pure decider: `decide(leading parameters, command, state)` returns a list of events or a refusal; `evolve(state, event)` is total; `is_terminal` says when it has ended, after which every command is refused. States, commands and events are closed unions; every match lists every case, with no wildcard (`C12`). Simple values are constrained types with a hidden representation, a `create` that returns the value or a typed error, and a `value` accessor (`C3`); domain constants live in a rules module (`G7`). Then write properties, with a fixed seed in the gate: `create` accepts exactly the legal values; `decide` is deterministic; `evolve` is total; a terminal state refuses every command (`E10` lists the full set an aggregate grows into).

Add a check for `C12` with its breach (step 4's pattern), because the compiler checks exhaustiveness but not a wildcard that hides a future case.

**Java.** The starter's [`Loan.java`](../examples/java-starter/loans/domain/src/main/java/starter/loans/domain/Loan.java) is the decider over sealed interfaces of records, with every `switch` exhaustive and no `default`; [`LoanDays.java`](../examples/java-starter/loans/domain/src/main/java/starter/loans/domain/LoanDays.java) is a constrained value with a private constructor; [`LoanRules.java`](../examples/java-starter/loans/domain/src/main/java/starter/loans/domain/LoanRules.java) holds the constants; [`LoanProperties.java`](../examples/java-starter/loans/domain/src/test/java/starter/loans/domain/LoanProperties.java) has the jqwik properties with a fixed seed. Errors come back through the kernel's sealed [`Result`](../examples/java-starter/kernel/src/main/java/starter/kernel/Result.java), never as exceptions (`C1`). The `C12-no-default-arm` check is a flat source scan of kernel, domain and application sources, with its breach in `breaches/C12-no-default-arm/`. PIT runs on the domain inside `check`; the starter uses a threshold as a stop-gap until a mutation ratchet exists (`E34`).

## 7. The counting-assertion guard (`E22`)

**Any language.** Tests assert only through one counting module of your own; a test that finishes with no counted assertion fails; a check bans the test framework's own assertion functions; the main line holds no skipped or focused tests. This is the guard against the commonest model shortcut, a test that runs code and checks nothing. It needs two checks, each with its breach: the ban on direct assertions (static) and the judge that fails an assertion-free test (in the test runner). Prove the judge with a breach that is a test asserting nothing; without it, a judge that silently stopped loading would leave every test green.

**Java.** The starter's [`Expect`](../examples/java-starter/testing/src/main/java/starter/testing/Expect.java) counts, and [`CountedAssertions`](../examples/java-starter/testing/src/main/java/starter/testing/CountedAssertions.java), a Jupiter extension switched on for every test by service-loader autodetection, hands each test an `Expect` and fails it if nothing was counted. The ArchUnit check `E22-counted-assertions` bans JUnit `Assertions`, AssertJ and Hamcrest in test classes. The meta-test proves the judge by running the breach's test in-process and finding the failure that starts with `[E22-asserts-something]`.

## 8. The per-test time budget judge (`E8`)

**Any language.** Each test belongs to a tier with a per-test budget (logic 20 ms, scenario 200 ms, narrow integration 200 ms; the architecture tests 10 s in total). A judge times each test body on the monotonic clock and fails a test over its tier's budget; a slower test is tagged for the nightly run, and an untagged slow test fails the gate. Its breach is a test that sleeps past the logic budget. Leave headroom: a shared CI runner can be several times slower than your machine, so a test near its budget locally will fail on CI. Fix a slow test by profiling it, never by cutting cases, raising its budget or moving it to another tier.

**Java.** The starter's [`TimeBudget`](../examples/java-starter/testing/src/main/java/starter/testing/TimeBudget.java) is a Jupiter interceptor that reads the tier from the test's tag and fails the test with `[E8-time-budget] ... took 25 ms, over its 20 ms budget`. Under PIT it is switched off, because instrumented code is slow by design. jqwik properties run on their own engine, so neither judge sees them yet; [java.md, E8](languages/java.md#e8-per-test-budgets-on-the-jvm) describes the next steps (a warm-up listener, a judge over the test reports).

## 9. CI

**Any language.** CI runs the same gate script on every push, with the toolchain installed at the pinned exact version and the package cache restored. It records each stage's time per track, so the wall-time ratchet (`E8`) has its history from the first day. Add a nightly job early, even if it only runs the package audit (`H5`) at first; slow suites, fresh property seeds and mutation runs move there later. If CI lands green branches by itself, pushing is merging: builders commit locally, and only the reviewer pushes, through one landing script ([docs/runners.md, section 5](runners.md#5-the-landing-script-s9), rule `S9`).

**Java.** A sketch for GitHub Actions *(not run as part of the starter)*:

```yaml
name: gate
on: [push]
jobs:
  gate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "21.0.5" }   # an exact version (H1)
      - uses: gradle/actions/setup-gradle@v4                        # restores ~/.gradle: a warm track
      - run: scripts/gate.sh
```

Pin each action to a commit hash rather than a tag once you adopt it (`H2`).

---

## 10. What to defer, and when

Day one ends with: a pinned, locked build with warnings as errors; the layer units and both principle files; four to eight checks, each proved by its planted breach in the meta-test; the counting and timing judges; a gate script with budgets measured on your stack; and CI. Everything else waits for a trigger:

| Add | When | Rules it starts | Spec |
| :--- | :--- | :--- | :--- |
| **The history runner** | Before the second contributor (person or agent) commits, or at the first decision record, whichever is first | `F4`, `I2`, `I3`, `S5`, `R5`, `E23`, `E12`, then `E30`, `S4`, `S8`, `B26` | [runners.md, section 1](runners.md#1-the-history-check) |
| **The inventory runner** | When the suite has a second test tier, or a test-report judge is needed for the 10 slowest tests and the stage ratchet, or a shared behaviour suite (`spec/`) exists | `E8` (gate half), `E36` | [runners.md, section 2](runners.md#2-the-inventory-check) |
| **The supply-chain runner** | Before the lockfile outgrows a hand check (a few dozen entries), and before the first release; the nightly audit and reproducible-build check from the first nightly job | `H4`, `H5`, `H6`, then `H9` | [runners.md, section 3](runners.md#3-the-supply-chain-check) |
| **The landing script** | As soon as CI lands green branches by itself | `S9` | [runners.md, section 5](runners.md#5-the-landing-script-s9) |
| **The model-review runner** | When there is enough code to write 50 labelled calibration cases in your language; run every item advisory until it meets the bar | `V1`–`V8`, the `L` halves of `Q+L` rules, `B11`, `D8`, `G13` | [runners.md, section 4](runners.md#4-the-model-review-runner), [rubrics.md](rubrics.md) |
| **Contracts, codecs, the composition package** | At the second context, the first persisted form, the second context's application | `A2`, `A8`, `A14`, `B1`, `B15`, `B17`, `B23`, `M1`, `P2`, `P3`, `P15` | the guide's groups A, B, M, P |
| **Journal, store and messaging rules** | When the journal and the store land; write their checks and breaches earlier and keep them dormant | `M2`–`M7`, `P5`–`P10`, `R2`–`R4`, `E35` | the guide's groups M, P, R |
| **Mutation ratchet, simulation** | When the core has a few aggregates and a nightly job; until then, hand mutants per branch (`E14`) | `E34`, `E32` | the guide's group E |

A rule you are not ready for stays `planned` in the rule table. After the table's first commit as a principle file, never delete its row (that is a loosening, `F4`); before it, deleting rows you will never need (step 3) is free. A check you can write before its target code exists is written now, with its breach, and marked dormant with the backlog item that brings the code: the meta-test then wakes it on the commit that adds that code.

---

## An existing project

Follow [the guide's Section 2](../README.md#2-how-to-adopt-these-rules-in-an-existing-project), which orders the rule groups by payoff for a code base that already exists. Three differences from the path above:

- **Wire the meta-test first**, with whatever checks you add first, so no check arrives unproved.
- **Hold the line, then tighten.** A new check usually finds existing violations. Record each as an `[allow]` entry with its reason, its decision record and a dated approval, or freeze them with the tool's baseline (ArchUnit's `FreezingArchRule`, a lint baseline file), and make the count one that can only fall. New code meets the rule at once.
- **Set where history starts.** Point the history runner's range (`[range].since` in the rule table) at the commit before it was added, so old commits are not judged by rules that did not exist.
