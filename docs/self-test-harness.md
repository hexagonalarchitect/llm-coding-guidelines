# The self-test harness (`F5`)

Rule `F5` asks that every automated rule prove it works. A check that has never been seen to fail
is a claim, not a guard: a misspelt package name, a selector that matches nothing, or a refactor
that moves code out of the check's view all leave the check green while it guards nothing. Models
are good at producing exactly this kind of green.

The self-test harness turns that claim into a test. It has three parts:

1. a **breach folder** that holds one small, deliberate violation per check, kept out of every
   production build;
2. a **check registry** that lists every automated check by id; and
3. a **meta-test** that runs every check against the real code and against every breach, and
   asserts the results.

This document explains each part in language-neutral terms. The worked example is the Java
starter in [`examples/java-starter/`](../examples/java-starter/), where the gate
(`./gradlew check`) runs the harness on every commit. Paths below are relative to that folder.

---

## 1. The breach folder

### Layout

Each check owns one folder, named by its check id, holding the smallest code that breaks that
check and nothing else:

```text
breaches/
  A1-layers/src/starter/loans/domain/DomainReachesUp.java
  A1-codec-layer/src/starter/loans/codec/CodecReachesUp.java
  C12-no-default-arm/src/starter/loans/domain/DefaultHidesACase.java
  A10-one-wrapper/src/starter/loans/application/GsonOutsideItsWrapper.java
  D1-pure-core/src/starter/loans/domain/DueDateFromTheClock.java
  E1-no-mocking/src/starter/loans/application/MockedNotices.java
  E22-counted-assertions/src/starter/breaches/RawAssertionTest.java
  E22-asserts-something/src/starter/breaches/AssertsNothingTest.java
  E8-time-budget/src/starter/breaches/SlowLogicTest.java
  build.gradle.kts
```

A check id is a rule id plus a short name (`A1-layers`, `A1-codec-layer`), because one rule often
needs more than one check. The folder name **is** the link between a breach and its check: the
meta-test pairs them by name, so there is no second list to keep in step.

A breach lives in the package or namespace of the layer it pretends to be. The `A1-layers` breach
is declared in `starter.loans.domain`, because the check selects code by package, and a breach the
check cannot see proves nothing:

```java
package starter.loans.domain;

import starter.loans.application.LoanDesk;

/** BREACH of A1-layers: the domain names an application type, a layer above it. */
public final class DomainReachesUp {

    private DomainReachesUp() {}

    /** Returns the refusal case of the workflow's error union. */
    public static Class<?> leak() {
        return LoanDesk.Refused.class;
    }
}
```

A breach should look like the mistake it stands for. A clock read in the domain, a mocking
library in a test fixture, a test that asserts nothing: these are the mistakes a model makes, and
a breach shaped like them also documents the rule.

### Keeping breaches out of production builds

Three properties keep breaches harmless, whatever the language:

1. **Each breach is its own build unit.** One crate, project, module or source set per breach, so
   each compiles alone. A breach that fails to compile cannot hide inside another, and one breach
   cannot satisfy or trip a different check by sharing a compilation with it.
2. **Nothing depends on a breach.** No production package, test package or host names a breach
   unit, so no breach can reach a shipped artifact. In the reference Rust code base the layer
   table gives `breaches/**` a `Breach` layer that references nothing and that nothing references.
3. **The meta-test reads breaches by path, not by dependency.** It gets the compiled output (or
   the source, for a source-level check) from a known location, so breaches never appear on a
   classpath, module path or import path that production code uses.

In the Java starter, `breaches/` is one Gradle project that creates a source set per folder:

```kotlin
// breaches/build.gradle.kts (abridged)
val breachFolders = projectDir.listFiles { f -> f.isDirectory && f.resolve("src").isDirectory }!!
breachFolders.forEach { folder ->
    val set = sourceSets.create(folder.name.replace('-', '_')) {
        java.setSrcDirs(listOf(folder.resolve("src")))
        compileClasspath = breachClasspath
    }
    allBreaches { dependsOn(set.classesTaskName) }
}
tasks.named("jar") { enabled = false }   // nothing is packaged from here, ever
```

No project declares a dependency on `:breaches`; `./gradlew :host:installDist` never compiles a
breach. Only the architecture test task depends on `:breaches:allBreaches`, and it passes the
folder to the meta-test as a path:

```kotlin
// architecture/build.gradle.kts (abridged)
tasks.test {
    dependsOn(..., ":breaches:allBreaches")
    systemProperty("starter.real", real.joinToString(File.pathSeparator))
    systemProperty("starter.breaches", rootDir.resolve("breaches").path)
}
```

Breaches compile under the same warnings-as-errors and static-analysis settings as production
code. That is deliberate: a breach must break exactly one rule, not the compiler's rules as well.

---

## 2. The check registry

Every automated check is listed once, by id, in one place
(`architecture/src/test/java/starter/architecture/Checks.java`):

```java
static List<Check> all() {
    return List.of(layers(), codecLayer(), oneWrapperPerLibrary(), noAmbientStateInCore(), noDefaultArm(),
            noMocking(), assertionsAreCounted(), new Check.Judge(CountedAssertions.CHECK_ID), new Check.Judge(TimeBudget.CHECK_ID));
}
```

A check reports two things on any body of code, not one (`Check.java`):

```java
record Verdict(long selected, List<String> findings) {}
```

- `findings` are the violations. Empty means the check passed.
- `selected` is how much code the check looked at. It is what makes vacuity visible (section 4).

There are two kinds of check, and the harness treats them alike:

- **Static checks** inspect code without running it. In the starter most are ArchUnit rules over
  compiled classes, and one (`C12-no-default-arm`) is a flat scan of source text, for a form the
  compiled class no longer shows (a `default` arm). Each one names what it selects as well as the
  rule:

  ```java
  static Check noAmbientStateInCore() {
      return Check.Arch.live("D1-pure-core",
              resideInAnyPackage(KERNEL, DOMAIN),                       // what it selects
              noClasses().that().resideInAnyPackage(KERNEL, DOMAIN)    // what it forbids
                      .should().dependOnClassesThat().belongToAnyOf(Clock.class, InstantSource.class,
                              Random.class, SecureRandom.class, ThreadLocalRandom.class, UUID.class,
                              HashMap.class, HashSet.class)
                      .orShould().callMethodWhere(clockRead)
                      .because("the core takes time and randomness as parameters and keeps a stable order"));
  }
  ```

  A source scan selects files by their package declaration, the same packages the ArchUnit rules
  use, and counts the files it read as its `selected`.

- **Test-runner judges** fail a test from inside the test runner: the counting-assertion guard
  (`E22`) and the per-test time budget (`E8`). In the starter they are JUnit extensions in
  `testing/`, switched on for every test by service-loader autodetection, so no test can opt out
  by forgetting an annotation. A judge puts its check id at the start of its failure message:

  ```java
  throw new AssertionFailedError("[" + CHECK_ID + "] " + context.getDisplayName() + " took "
          + took.toMillis() + " ms, over its " + budget.toMillis() + " ms budget. ...");
  ```

  To run a judge against a target, the harness runs the target's tests in-process (JUnit's
  `EngineTestKit`, in `Target.java`) and collects failures that carry the judge's id. Its
  `selected` count is the number of tests that ran.

A judge is a check like any other: it has an id, a breach, a vacuity test and a place in the
registry. The `E8` breach is a test that sleeps past its tier's budget; the `E22` breach is a test
that asserts nothing. Without them, a judge that silently stopped loading would leave every test
green.

---

## 3. What the meta-test asserts

`architecture/src/test/java/starter/architecture/MetaTest.java` holds three tests.

### 3.1 Every check has exactly one breach, and every breach has a check

```java
@Test
void everyCheckHasExactlyOneBreachAndEveryBreachHasACheck(Expect expect) {
    expect.equal(new TreeSet<>(CHECKS.stream().map(Check::id).toList()), new TreeSet<>(BREACH_TARGETS.keySet()));
}
```

The set of check ids must equal the set of breach folder names. This is what makes "adding a check
without a breach" fail the gate: register a check `C2-immutable-core` with no
`breaches/C2-immutable-core/` and this test fails with both sets printed. It also catches the
reverse, a breach left behind after its check was deleted or renamed.

### 3.2 Every breach fires its own check, and no other

```java
BREACH_TARGETS.forEach((breach, target) -> {
    for (Check check : CHECKS) {
        boolean fired = !check.evaluate(target).findings().isEmpty();
        boolean own = check.id().equals(breach);
        if (own && !fired) problems.add("breach " + breach + " does not fire its own check");
        if (!own && fired) problems.add("breach " + breach + " also fires " + check.id());
    }
});
```

Every check runs against every breach: an n-by-n matrix whose diagonal must be all "fired" and
whose other cells must all be "silent".

**"Fires its own check"** proves the check can see a violation at all. A selector with a typo, a
rule pointed at the wrong package, or a judge that is not loaded all fail here.

**"And only its own"** matters as much, for four reasons:

1. **Attribution.** If a breach trips two checks, the gate stays red when its own check breaks,
   because the other check still fires. The broken check then goes unnoticed for as long as the
   overlap lasts. A one-to-one matrix means each check is proved by evidence that no other check
   can supply.
2. **Precision.** A check that fires on breaches meant for other rules is too broad, and it will
   be too broad on real code too, which teaches people (and models) to add exceptions until it is
   quiet. Overlap is an early sign of a check that will be loosened later.
3. **A clean breach is the rule's best documentation.** A breach that breaks exactly one rule
   shows what that rule forbids and nothing else.
4. **Safe deletion.** When a check is retired, its breach goes with it, and no other check loses
   evidence it was silently relying on.

The starter shows the overlap case: when the `D1-pure-core` breach was changed to also name
`LoanDesk.Refused`, the meta-test failed with `breach D1-pure-core also fires A1-layers`.

Two practical notes:

- **Run each breach alone.** In the starter each breach is read without the real code around it,
  so a breach's findings are exactly its own. (The alternative, real code plus one breach, works
  too; then "fired" means "more findings than on the real code alone".)
- **A rule's failure message names the check id.** Static rules are wrapped with
  `rule.as("[" + id + "] " + ...)` and judges prefix their messages, so a gate failure says which
  check fired and where.

### 3.3 Every check passes on the real code and selects something there

```java
for (Check check : CHECKS) {
    Check.Verdict verdict = check.evaluate(REAL);
    if (!verdict.findings().isEmpty())
        problems.add(check.id() + " fails on the real code: " + verdict.findings());
    if (check.dormantUntil().isEmpty() && verdict.selected() == 0)
        problems.add(check.id() + " is vacuous: it selects nothing in the real code");
    if (check.dormantUntil().isPresent() && verdict.selected() > 0)
        problems.add(check.id() + " is dormant until " + check.dormantUntil().get()
                + " but now selects " + verdict.selected() + " classes: wake it up");
}
```

The first condition is the ordinary gate: the real code obeys every rule. When a planted clock read
was added to `Loan.isTerminal`, this test failed with
`D1-pure-core fails on the real code: [Method <starter.loans.domain.Loan.isTerminal(...)> calls method <java.time.LocalDate.now(java.time.ZoneId)> in (Loan.java:157)]`.
The other two conditions are about vacuity and dormancy, covered next.

---

## 4. Vacuity: a check must select real code

A check that selects nothing passes on everything. It passes on the real code, and with the
"run each breach alone" design it may even fire on its breach, because the breach sits in exactly
the package the check names while the real code has moved elsewhere. So a breach alone does not
prove that a check guards the real code. The vacuity condition does: on the real code, every live
check must select at least one class (or, for a judge, at least one test).

Vacuity has many causes, and all of them are quiet:

- a package or module renamed while the check kept the old name;
- a selector that never matched (`starter.loan.domain..` for `starter.loans.domain..`);
- a check scoped to test code when the tests moved to another source set;
- a judge whose service file was dropped, so it never loaded.

Two details make the condition honest:

- **Count what the rule is about, not what it imports.** The `selected` predicate is the rule's own
  `that(...)` clause, so the count is what the rule actually looked at. The A10 check selects the
  classes outside the wrapper, the `E22-counted-assertions` check selects test classes, and so on.
- **Turn off the tool's own emptiness guard where you check vacuity yourself.** ArchUnit fails a
  rule whose `that(...)` selects nothing by default. The starter turns that off
  (`allowEmptyShould(true)`), because a breach target legitimately has no classes in most layers,
  and asserts selection explicitly on the real code instead, where it belongs.

The starter's selections on the real code, when this was written: `A1-layers` 37 classes,
`A10-one-wrapper` 32, `D1-pure-core` 23, `E1-no-mocking` 40, `E22-counted-assertions` 4 test
classes, and both judges 10 tests each. Recording such numbers (and failing when one drops to
zero) is the same idea as `E37`'s pinned counts: a comparison that silently stops comparing also
disagrees with nothing.

---

## 5. Dormant checks

Sometimes a rule is decided before the code it governs exists. The guide's example is a `Codec`
layer rule written before the first persisted form. Such a check would be vacuous, and deleting it
would lose the decision. The answer is a **dormant** check: it carries a marker naming the backlog
item in which its target code will appear.

```java
static Check codecLayer() {
    return Check.Arch.dormant("A1-codec-layer", "BACKLOG-3 (first persisted form)",
            resideInAPackage(CODEC),
            noClasses().that().resideInAPackage(CODEC)
                    .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, INFRASTRUCTURE, HOST)
                    .because("a codec depends on the kernel and its own domain only"));
}
```

A dormant check is held to every rule except vacuity:

- **It still needs a breach, and the breach must fire it alone.** `breaches/A1-codec-layer/` holds
  a codec class that names an application type. So the check is proved to work the day its target
  code appears, not merely believed to.
- **It must pass on the real code.** While it selects nothing this is trivially true.
- **It must wake up.** The moment the real code gives a dormant check something to select, the
  meta-test fails: `A1-codec-layer is dormant until BACKLOG-3 (first persisted form) but now selects
  1 classes: wake it up`. Removing the marker is then a one-line change in the same commit as the
  first codec class, and from then on the vacuity condition applies.

Without that last condition a dormant marker becomes a permanent exemption. Name the backlog item
in the marker, in a form that does not look like a rule id (`BACKLOG-3`, not `B3`), so a
cited-id check (`F3`) can resolve it.

---

## 6. Adding a new check

A team (or a model) adds a check in one commit, with these steps:

1. **Pick an id**: the rule id plus a short name, such as `C2-immutable-core`.
2. **Write the check** and register it in `Checks.all()`. Give it a selector that names what it is
   about, and a failure message that names the check id, the offending member, and the allowed
   alternative (`.because(...)`).
3. **Run the gate and watch it fail** on `everyCheckHasExactlyOneBreachAndEveryBreachHasACheck`.
   This is the harness telling you the check is unproved.
4. **Write the breach**: create `breaches/C2-immutable-core/src/...` with the smallest code that
   breaks the rule, in the package the check selects, shaped like the real mistake. The Gradle
   build picks up the new folder automatically.
5. **Run the gate again.** Expect the matrix to show the new breach firing its own check only. If
   it also fires another check, shrink the breach until it does not; if it fires nothing, the
   check is wrong.
6. **Check selection on the real code.** If the new check selects nothing, either fix its
   selector or mark it dormant with the backlog item that will bring its target.
7. **If the real code breaks the new rule**, fix the code in the same change, or record a scoped,
   dated exception (`I2`) rather than weakening the check.

A judge follows the same steps, with its breach a small test the judge should fail.

When a review finds a way **around** an existing check, prefer a flat rule that forbids the
bypass form outright over one that tries to resolve it (`F5`'s note on flat syntactic rules), and
give that form its own check id and breach folder (`A1-layers`, `A1-no-reexport-alias`, ...). Do
not drop a second file into an existing breach folder: the folder would then fire if either file
fired, and the one that stopped firing would go unnoticed. In the starter, one bypass is a known gap: Java inlines compile-time constants,
so a domain class reading `LoanDesk.RETURN_NOTICE` (a `static final String`) leaves no reference
to `LoanDesk` in its bytecode, and no bytecode-level check can see it. Here the build's project
graph blocks it; elsewhere a source-level rule would be needed.

---

## 7. Running it

```sh
cd examples/java-starter
./gradlew check          # compile with -Werror, Error Prone and NullAway; every test under both judges;
                         # PIT on the domain; the architecture meta-test
scripts/gate.sh          # the same plus a host smoke run, held to a 2-minute budget
```

On a clean checkout the gate was green in 27 seconds when this was written. The changes below were
each planted in the real code, seen to fail with the right check, and restored:

| Planted change | Failure |
| :--- | :--- |
| `Loan.isTerminal` reads `LocalDate.now` | `D1-pure-core fails on the real code` |
| A domain test asserts nothing | `[E22-asserts-something] loanDaysAreValues(Expect) asserted nothing` (in the module's own test run and in the meta-test) |
| A domain test sleeps 25 ms | `[E8-time-budget] anEndedLoanRefusesARenewal(Expect) took 25 ms, over its 20 ms budget` |
| A check is registered with no breach | `everyCheckHasExactlyOneBreachAndEveryBreachHasACheck` fails, listing both sets |
| A breach also names an application type | `breach D1-pure-core also fires A1-layers` |
| A class appears in the dormant check's package | `A1-codec-layer is dormant until BACKLOG-3 ... but now selects 1 classes: wake it up` |

---

## 8. Porting the harness to another language

Nothing above depends on Java. To build it elsewhere you need:

- a way to compile or parse each breach as its own unit that nothing depends on (a workspace member
  excluded from default builds, a project no solution references, a package outside the
  production `tsconfig` or module graph);
- checks that can run against an arbitrary set of files or compiled units, not only "the
  project", and that report what they selected as well as what they found;
- for judges, a way to run a chosen test file in-process and read its failures; and
- one registry of check ids and one folder per id.

Start with the meta-test and one check. Every later check then arrives with its proof.
