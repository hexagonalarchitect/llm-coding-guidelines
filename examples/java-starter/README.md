# Java starter

A small, runnable Java 21 project that shows the guide's core mechanisms working: a layered
multi-project build, a pure functional core, a Nullable infrastructure wrapper, honest and fast
tests, and the self-test harness (`F5`) that proves every automated check against a planted
breach. Copy its shape on day one, then grow it.

The example context is "library loans", as in the guide's Example 2. Everything is deliberately
tiny: 30 Java files, about 2,050 lines including build files, plus the rule table.

## Run it

You need a JDK 21. The Gradle wrapper (Gradle 8.14.3) is committed, so nothing else is needed.

```sh
./gradlew check           # the gate: compile, tests, mutation testing, architecture meta-test
scripts/gate.sh           # the same, plus a smoke run of the host, held to a 2-minute budget
./gradlew :host:run       # run the demo: renew, return, then a refused second return
```

From a clean checkout (dependencies already downloaded), `scripts/gate.sh` was green in 16 to 27 s
when this was written, on a 4-core cloud container. Measure your own machine and CI before you
set budgets (`templates/budgets.toml` at the repository root).

## Layout

```text
settings.gradle.kts            the project graph: one project per layer
build.gradle.kts               compiler flags, Error Prone and NullAway, test set-up, for every project
gradle/libs.versions.toml      every dependency version, pinned in one place
**/gradle.lockfile             the locked dependency graph of each project, transitive versions included
standards/rules.toml           the rule table: templates/rules.toml adjusted for this project; each rule names its checks
kernel/                        Kernel: Result
loans/domain/                  Domain: LoanDays, ids, the Loan decider, LoanRules; tests and properties
loans/application/             Application: LoanDesk workflow; scenario tests over the Nullable wrapper
infrastructure/notices/        Infrastructure: NoticeLog, the one wrapper allowed to use Gson
host/                          Host: Main, which owns the clock and wires the real wrapper
testing/                       Test support: Expect (counting assertions), the E22 and E8 judges
architecture/                  The self-test harness: Checks, Check, Target, MetaTest (test code only)
breaches/<check-id>/src/       One planted violation per check, each its own source set
scripts/gate.sh                The gate entry point
```

Package names follow the layers: `starter.kernel`, `starter.loans.domain`,
`starter.loans.application`, `starter.infrastructure.notices`, `starter.host`. The architecture
checks select code by these packages, so a class placed in the wrong package is caught even when
the Gradle graph would have let it compile.

## What each file illustrates

| File | Demonstrates | Guide rules |
| :--- | :--- | :--- |
| `settings.gradle.kts`, `*/build.gradle.kts` | One project per layer; each project's dependencies are the ones its layer allows | `A1` |
| `build.gradle.kts` | `-Xlint:all -Werror`, Error Prone, NullAway as an error, one place for all of it | `F1` |
| `gradle/libs.versions.toml` | Every direct version pinned in one file | `H2` |
| `gradle.lockfile` (each project), `buildscript-gradle.lockfile`, `settings-gradle.lockfile` | Every resolved configuration locked in `STRICT` mode, transitive versions and the plugins' classpath included; rewritten only by `./gradlew resolveAndLockAll --write-locks` | `H2` |
| `kernel/.../Result.java` | Refusals as values: a sealed `Ok`/`Err`, never null, never thrown | `C1`, `C11`, `C12` |
| `loans/domain/.../LoanDays.java` | A constrained value: private constructor, `create` returning `Result`, `value()` | `C3` |
| `loans/domain/.../LoanId.java`, `BookId.java`, `MemberId.java` | Typed ids; other aggregates referenced by id only | `B5` |
| `loans/domain/.../LoanRules.java` | Domain constants in one rules module | `G7` |
| `loans/domain/.../Loan.java` | The decider: sealed states, commands and events; `decide`, `evolve`, `isTerminal`; exhaustive `switch` with no `default`; immutable event lists; the date as a leading parameter | `B3`, `B7`, `C2`, `C12`, `D1` |
| `loans/domain/.../LoanTest.java` | Readable examples of the decider, each a logic-tier test under 20 ms | `B3`, `E8`, `E22` |
| `loans/domain/.../LoanProperties.java` | jqwik properties with a fixed seed: `create` accepts exactly the legal range; `decide` is deterministic; `evolve` is total; a terminal loan refuses everything; a renewal must extend | `C3`, `B3`, `D`-group |
| `loans/domain/build.gradle.kts` | PIT mutation testing of the domain, threshold 90 %, part of `check` | `E`-group |
| `infrastructure/notices/.../NoticeLog.java` | A Nullable wrapper: `create`/`createNull`, no I/O in either factory, an embedded stub, recorded outputs as a named type, loud failure when configured responses run out | `E1`, `E5` |
| `infrastructure/notices/.../NoticeLogTest.java` | Narrow tests of the real backend against a temporary file, and of the stub | `E5` |
| `loans/application/.../LoanDesk.java` | An application service that is itself Nullable; the workflow's error union | `E5`, `C11` |
| `loans/application/.../LoanDeskTest.java` | Scenario tests with no mocking library; outputs compared as a whole list against literals | `E1`, `E9` |
| `host/.../Main.java` | The host reads the clock and wires the real wrapper; nothing else does | `A1`, `D1` |
| `testing/.../Expect.java` | The counting assertion module | `E22` |
| `testing/.../CountedAssertions.java` | Judge: a test that counted no assertion fails | `E22` |
| `testing/.../TimeBudget.java` | Judge: a per-test budget by tier (logic 20 ms, scenario 200 ms, architecture 10 s, nightly unjudged) | `E8` |
| `testing/src/main/resources/...` | Both judges switched on for every Jupiter test by service-loader autodetection | `E8`, `E22` |
| `architecture/.../Checks.java` | The registry of every automated check | `A1`, `A10`, `C12`, `D1`, `D2`, `E1`, `E22`, `E8` |
| `architecture/.../Check.java`, `Target.java` | How a check is run on a body of compiled code (and its sources) and what it reports | `F5` |
| `standards/rules.toml` | The rule table, copied from the template and adjusted before its first commit: layer patterns for this layout, the reimplementation-only rows deleted, and `status`, `checks` and `parts_pending` filled for the rules checked here | `F4`, `F5`, `I1` |
| `architecture/.../MetaTest.java` | The meta-test: ids and breach folders match; each check passes on the real code and selects something there unless dormant; each breach fires its own check only (after one warm-up run of its tests); and each rule's `checks` names only registered checks, while every registered check is named by some rule | `F5` |
| `breaches/*/` | One planted violation per check | `F5` |
| `scripts/gate.sh` | The gate and its wall-time budget | `E8` |

## The checks and their breaches

| Check id | Kind | What it forbids | Breach |
| :--- | :--- | :--- | :--- |
| `A1-layers` | ArchUnit | A layer reaching one it may not (domain to application, and so on) | `breaches/A1-layers`: a domain class names `LoanDesk.Refused` |
| `A1-codec-layer` | ArchUnit, **dormant** | A codec reaching application, infrastructure or host; no codec exists yet | `breaches/A1-codec-layer`: a codec class names `LoanDesk.Refused` |
| `A10-one-wrapper` | ArchUnit | Gson used outside `starter.infrastructure.notices` | `breaches/A10-one-wrapper`: an application class calls `new Gson()` |
| `C12-no-default-arm` | Source scan | A `default` arm in any `switch` in kernel, domain or application code (comments and string literals are ignored) | `breaches/C12-no-default-arm`: a domain `switch` over `Loan.State` with a `default` arm |
| `D1-pure-core` | ArchUnit | `Clock`, `InstantSource`, `Random`, `SecureRandom`, `ThreadLocalRandom`, `UUID`, `HashMap`, `HashSet`, `java.time.*.now`, `System.currentTimeMillis`/`nanoTime`, `Math.random` in kernel or domain | `breaches/D1-pure-core`: a domain class calls `LocalDate.now` |
| `E1-no-mocking` | ArchUnit | Any dependency on Mockito, EasyMock, JMock, PowerMock or MockK, in any class | `breaches/E1-no-mocking`: a fixture calls `Mockito.mock` |
| `E22-counted-assertions` | ArchUnit | A test class calling JUnit `Assertions`, AssertJ or Hamcrest directly | `breaches/E22-counted-assertions`: a test calls `Assertions.assertEquals` |
| `E22-asserts-something` | JUnit judge | A test that finishes without one counted assertion | `breaches/E22-asserts-something`: a test that asserts nothing |
| `E8-time-budget` | JUnit judge | A test over its tier's budget | `breaches/E8-time-budget`: an untagged test that sleeps 60 ms |

`docs/self-test-harness.md` at the root of this repository explains the harness in full, using
this project as the worked example.

## Planted changes that the gate catches

Each of these was made to the real code, run, seen to fail with the right check, and restored.

| Change | What failed |
| :--- | :--- |
| `Loan.isTerminal` calls `LocalDate.now(ZoneOffset.UTC)` | `MetaTest`: `D1-pure-core fails on the real code: [Method <starter.loans.domain.Loan.isTerminal(...)> calls method <java.time.LocalDate.now(java.time.ZoneId)> in (Loan.java:157)]` |
| A test in `LoanTest` stops asserting | `:loans:domain:test`: `[E22-asserts-something] loanDaysAreValues(Expect) asserted nothing.`, and the same finding in `MetaTest` |
| A test in `LoanTest` sleeps 25 ms | `:loans:domain:test`: `[E8-time-budget] anEndedLoanRefusesARenewal(Expect) took 25 ms, over its 20 ms budget.`, and the same in `MetaTest` |
| `E1`'s row names `E1-no-mocks` instead of `E1-no-mocking` | `MetaTest.theRuleTableNamesEveryCheckAndOnlyRegisteredOnes`: `E1 names E1-no-mocks, which is not in the check registry, check E1-no-mocking is named by no rule's checks` |
| A new check `C2-immutable-core` is added without a breach | `MetaTest.everyCheckHasExactlyOneBreachAndEveryBreachHasACheck`: the check id set and the breach folder set differ |
| The `D1-pure-core` breach also names `LoanDesk.Refused` | `MetaTest`: `breach D1-pure-core also fires A1-layers` |
| `Loan.isTerminal` replaces its `Ended` case with `default -> true` | `MetaTest`: `C12-no-default-arm fails on the real code: [... Loan.java:159: a default arm in a switch over the system's own types; ...]` |
| A class appears in `starter.loans.codec` | `MetaTest`: `A1-codec-layer is dormant until BACKLOG-3 (first persisted form) but now selects 1 classes: wake it up` |
| `Result.ok` returns `null` on a path | `:kernel:compileJava`: `[NullAway] returning @Nullable expression from method with @NonNull return type` |
| `Main` calls `LocalDate.now()` with no zone | `:host:compileJava`: Error Prone `[JavaTimeDefaultTimeZone]`, an error under `-Werror` |

## Tool notes

- **Error Prone 2.36.0 and NullAway 0.12.4** run on JDK 21 through the `net.ltgt.errorprone`
  Gradle plugin with no extra flags. NullAway treats every package under `starter` as annotated
  and reports as an error.
- **PIT 1.17.4** (Gradle plugin 1.19.0, JUnit 5 plugin 1.2.1) runs the domain's Jupiter tests
  and jqwik properties and killed 18 of 18 mutants when this was written. `toString` and
  `hashCode` are excluded from mutation. The `E8` judge is switched off inside PIT only
  (`-Dstarter.budgets=off`), because instrumented code is slow by design.
- **jqwik 1.9.2** properties use a fixed seed and write their failure database under `build/`.
- **Dependency locking** is on for every configuration of every project, in `STRICT` mode, and
  for the plugins' classpath; the lockfiles are committed (`H2`). After changing a version in
  `gradle/libs.versions.toml`, run `./gradlew resolveAndLockAll --write-locks` and commit the
  changed lockfiles with the version. The gate never writes locks.
- The Gradle distribution's SHA-256 is not pinned in `gradle-wrapper.properties`, because the
  environment it was built in could not reach `services.gradle.org`; pin it with
  `./gradlew wrapper --gradle-version 8.14.3 --gradle-distribution-sha256-sum <sum>`, with the
  sum Gradle publishes beside the distribution (`docs/quick-start.md`, step 1). The JDK is also
  pinned by major version only, and there is no `gradle/verification-metadata.xml`; add an exact
  JDK check and checksum verification when adopting this (`H1`, `H2`; see `docs/languages/java.md`).
- **PIT's `mutationThreshold = 90`** is a simple floor while the project is tiny. The guide wants
  a ratchet on the surviving-mutant count instead, never a percentage target (`E14`, `E34`);
  replace the threshold with a ratchet once a nightly mutation run exists.

## Known gaps, stated honestly

- **Host to domain.** The guide lets a host reach only `Application`, the composition package and
  `Infrastructure`, and lets it see listed domain types through a facade of re-exports. Java has
  no re-exports, so here the host may name domain types directly (`A1-layers` allows
  `Host -> Domain`). A stricter project would give the application its own input and output types.
- **The judges cover Jupiter tests only.** jqwik properties do not run Jupiter extensions; they
  return their verdict as a `boolean`, which is their assertion. A jqwik `AroundPropertyHook` could
  carry both judges over.
- **Tautological assertions are not flagged** (`E22` asks for that); `Expect` only counts.
- **`C12` is a flat source scan.** `javac` rejects a non-exhaustive `switch` over a sealed type,
  and `C12-no-default-arm` rejects every `default` arm in kernel, domain and application sources.
  It does not know the selector's type, so it also bans `default` over a JDK type there; the core
  switches only over its own types, so that is the flat form the guide prefers (`F5`). It is a
  regular expression over source text with comments and string literals blanked: a text block
  holding `default ->` would fool it, and a total type pattern (`case Loan.State s ->`) is a
  catch-all it does not see. An Error Prone check would close both.
- **Compile-time constants are invisible to bytecode checks.** Java inlines `static final` strings
  and primitives, so a domain class that reads `LoanDesk.RETURN_NOTICE` leaves no dependency on
  `LoanDesk` in its class file. The Gradle project graph still blocks it here.
- **The meta-test's heavy work runs in class initialisation**, outside the per-test body the
  `E8` judge measures; the gate's wall-time budget covers it instead.
