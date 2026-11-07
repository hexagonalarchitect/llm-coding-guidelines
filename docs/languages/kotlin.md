# Implementing the rules in Kotlin

Kotlin on the JVM shares almost all of its build, supply-chain and runner story with Java. Read [java.md](java.md) first: sections 0 (one Gradle project per layer, one convention plugin per layer), 2 (H rules), 3 (I rules), 14 (runners), 15 (planted breaches) and 16 (speed) apply unchanged. ArchUnit and forbidden-apis also work on Kotlin bytecode. This page gives the Kotlin-specific tools per group:

- **Detekt** for lints, complexity and size;
- **Konsist** for source-level architecture and declaration rules;
- **kotest** (or JUnit 5 with jqwik) for tests and properties.

Versions are placeholders: pin your own in the version catalog (`H2`, `H4`). Statuses: **auto**, **report**, **review** and **neutral** mean the same as in the Java page.

## F, H and I: hygiene and supply chain

- **F1, warnings and formatting.**
  - Compiler settings, set in the shared convention plugin: `kotlin { jvmToolchain(21); explicitApi() }` and `compilerOptions { allWarningsAsErrors = true; progressiveMode = true; languageVersion = KotlinVersion.KOTLIN_2_1; apiVersion = KotlinVersion.KOTLIN_2_1 }`. Pin the language and API versions explicitly (`H1`).
  - Formatter: ktfmt or ktlint through Spotless (`spotless { kotlin { ktfmt(libs.versions.ktfmt.get()) } }`).
  - Detekt with `buildUponDefaultConfig = false`, so the configuration file names every rule kept, and `build: maxIssues: 0`.
  - **Overflow:** Kotlin `Int` and `Long` wrap silently, as in Java. Route core arithmetic through `Math.addExact` and the other `*Exact` methods, or kernel wrappers, and enforce that with a custom Detekt rule (`visitBinaryExpression`) in core projects. This is **auto** with a house rule, and is otherwise an `[allow]`-recorded gap.
- **F3:** Detekt `ForbiddenComment` with `allowedPatterns` matching `TODO\(BL-\d+\)`. Check the property names for your Detekt version; 1.23 has `comments` and `allowedPatterns`.
- **F6:** Detekt `ForbiddenMethodCall` (`kotlin.io.println`, `kotlin.io.print`), `UnusedPrivateMember` and `UnusedPrivateProperty`, plus a regex heuristic for commented-out code (this page knows of no stock Detekt rule for it). ArchUnit reports public members used only by tests.
- **H1, H2, H5 and H6** are as in Java: the Gradle wrapper checksum, an exact JDK, `dependencyLocking`, verification metadata, the nightly OWASP or osv-scanner audit, and reproducible archives. Pin the Kotlin Gradle plugin in `build-logic`'s locked classpath.
- **I6:** Konsist and Detekt read source, so inlining cannot fool them. Kotlin `const val` and `inline` functions are inlined into the caller's bytecode, so for ArchUnit checks the I6 caveat in java.md applies, and more strongly.

## A: layers and dependencies

- **A1, A2, A6, A10 and A14.** The project graph is the main defence: a flat test over `settings.gradle.kts` and each `build.gradle.kts`, as in java.md section 4. Konsist then checks imports in source:

  ```kotlin
  @Test fun `A1 the layer table governs every dependency`() {
      Konsist.scopeFromProduction().assertArchitecture {
          val kernel = Layer("Kernel", "com.example.kernel..")
          val domain = Layer("Domain", "com.example.contexts..domain..")
          val infrastructure = Layer("Infrastructure", "com.example.infrastructure..")
          kernel.dependsOnNothing()
          domain.dependsOn(kernel)
          infrastructure.dependsOn(kernel)
      }
  }
  ```

  For per-context slices and cycles, use ArchUnit `slices()` on bytecode. For A10, Konsist `files.assertFalse { it.hasImport { i -> i.name.startsWith("org.sqlite") } }` outside the wrapper project, plus the build-file allow-list. DI containers (Koin, Dagger, Hilt, Spring, kotlin-inject) are banned through the same resolution check as Mockito in java.md section 8.
- **A9 and B22:** Konsist `classes()` and `packages`, splitting names into CamelCase words.
- **A17:** Detekt `CyclomaticComplexMethod` `threshold: 5` in shell projects' configuration. A Konsist check bans `when` and `is` over domain types there, so the shell branches on the kernel `Result` through `fold`. The invariant part is **review**.

## C, D and G: the functional core

- **C1, C11, C14.** Kotlin's own `Result<T>` carries a `Throwable`, so it breaks `C11`. Use a kernel `sealed interface Result<out T, out E>`, or Arrow's `Either` as a listed core library. A house Detekt rule bans `throw`, `try` and `catch` in core projects (`visitThrowExpression`, `visitTryExpression`). Detekt `ForbiddenMethodCall` bans `kotlin.error`, `kotlin.require`, `kotlin.check`, `kotlin.requireNotNull` and `kotlin.TODO`. In the edge layers, use `TooGenericExceptionCaught`, `SwallowedException` and `EmptyCatchBlock`.
- **C2.** Konsist bans `var` properties and parameters in core, and Detekt reports `VarCouldBeVal`. Kotlin's `List` is a read-only *view*, not an immutable value, so ban `MutableList`, `ArrayList`, `Array` and `MutableMap` in public core types (Konsist return and parameter types). Use kernel persistent collections, or `kotlinx.collections.immutable` as a listed core library.
- **C3.** A `@JvmInline value class LoanDays private constructor(val value: Int)` with `companion object { fun create(raw: Int): Result<LoanDays, LoanDaysError> }` gives true hiding: Kotlin value classes and data classes *can* have private constructors. Konsist checks that every type implementing `Constrained` has a private primary constructor, a `create` and a `value`. A data class with a private constructor still exposes `copy`, which can break the invariant (newer Kotlin versions warn about this). Prefer value classes or plain classes.
- **C5:** Konsist bans nullable types (`?`) in core declarations and the `null` literal in core source. Detekt `UnsafeCallOnNullableType` (`!!`) and `LateinitUsage` catch the rest. Platform types from Java calls are banned too, because core may not call Java APIs at all (Konsist imports).
- **C6, C12.** `when` over a sealed type or enum is exhaustive. As a statement, that has been an error since Kotlin 1.7. Detekt `ElseCaseInsteadOfExhaustiveWhen` (C12) forbids an `else` branch where the cases are enumerable. `ForbiddenMethodCall` bans `first()`, `last()`, `single()`, `get(...)` on lists, `getValue`, `!!` and `toInt()` on strings, and a house rule allows `/` and `%` only by `Rules` constants.
- **C9:** Kotlin has no built-in unused-result error for ordinary functions. A house Detekt rule flags calls whose result type is the kernel `Result` and whose value is unused, or use Error Prone `CheckReturnValue` on the Java side of mixed projects. Turning a refusal into a default value is **review**.
- **C10:** `explicitApi()` makes every public declaration state its visibility and return type. Use `internal` by default.
- **C13, C15:** Konsist checks parameter function types returning `Unit` or `Deferred`/`CompletableFuture`, public `Unit` returns, and enum classes with integer properties.
- **D1–D3, D12, D13.**
  - Detekt `ForbiddenImport` and `ForbiddenMethodCall` in core: `java.io`, `java.nio`, `java.net`, `java.time.Instant`, `System.currentTimeMillis`, `kotlin.random.Random`, `java.util.UUID.randomUUID`, `Thread`, `kotlinx.coroutines` (except in application), and `java.util.Locale`.
  - Konsist bans `Double` and `Float` in core types and floating literals in source.
  - Kotlin's `setOf` and `mapOf` build `LinkedHashSet` and `LinkedHashMap`, which iterate in *insertion* order, so they are deterministic, unlike Java's `Set.of`. They are still hash collections, so ban them with `hashSetOf` and `hashMapOf`, and use `sortedMapOf`/`sortedSetOf` behind kernel immutable types.
- **D5:** Konsist bans string templates and literals in core, except in the `TextKeys` file.
- **G3, G4, G7, G11, G12.**
  - G3: Detekt `CyclomaticComplexMethod` `threshold: 15`, or `CognitiveComplexMethod`, and `NestedBlockDepth` `threshold: 4`.
  - G4: `LongMethod` `threshold: 80` and `LargeClass` `threshold: 500`. Konsist counts file lines up to 600. The report-only backstops (`ComplexCondition`, `TooManyFunctions`) go in a second Detekt task with its own configuration and `ignoreFailures = true`, so they report without failing.
  - G7: `MagicNumber` with `ignoreNumbers: ['-1', '0', '1']`, excluding `**/Rules.kt`.
  - G11: `LongParameterList` with `functionThreshold: 4`. Konsist bans `Pair`, `Triple` and `Boolean` parameters in public signatures.
  - G12: `UndocumentedPublicClass`, `UndocumentedPublicFunction` and `UndocumentedPublicProperty`.
- **D8, G13:** **review**, for the same reasons as in Java.

## E: tests

- **E1, E5:** Ban MockK, Mockito and mockito-kotlin at dependency resolution, as in java.md section 8. Write Nullable wrappers with `create()` and `createNull(...)` in a `companion object`; Kotlin default arguments give the "only optional parameters" shape directly. Konsist checks the factories, private constructors, and the absence of `*Fake`, `*Stub` and `*Mock` names outside infrastructure.
- **E22:** With kotest, set `override val assertionMode = AssertionMode.Error` in the project configuration (`AbstractProjectConfig`). A test that runs no kotest assertion then fails. Konsist bans `kotlin.test.assert*`, JUnit `Assertions` and AssertJ in tests. Detekt or Konsist bans focused and disabled tests: kotest `f:` and `!` name prefixes, `.config(enabled = false)` and `xtest`.
- **E10:** kotest properties use `checkAll(PropTestConfig(seed = 20261107L), arbCommand, arbState) { ... }` in the gate. Nightly variants omit the seed and carry a nightly tag (kotest `Tag` objects, excluded with `kotest.tags=!Nightly`). kotest prints the failing seed, and a failure becomes a named example test. With JUnit 5, use jqwik exactly as in java.md.
- **E8, E13, E36:** kotest writes JUnit XML through the JUnit Platform, so the Java inventory judge applies. Warm-up and in-process timing work as described in java.md section 8. Use kotest `Spec`-level extensions instead of Jupiter's. Random order uses kotest `specExecutionOrder = SpecExecutionOrder.Random` and `testCaseOrder`. Ban retries: kotest `retries`/`invocations` with flakiness intent, and the `org.gradle.test-retry` plugin.
- **E12:** The parent-commit replay works as in Java; an assertion failure is `AssertionError` or `AssertionFailedError` in the XML.
- **E14 and E34.** Use Kover (`org.jetbrains.kotlinx.kover`) for branch coverage and its ratchet. PIT runs on Kotlin bytecode but produces many unkillable mutants in compiler-generated code. Arcmutate sells a Kotlin plugin that filters them, and this page knows of no free equivalent. Until mutation testing is usable, hand mutants stay process.
- **E2:** Detekt or Konsist bans `@VisibleForTesting` and `*ForTest` names in production. `internal` is visible to the same module's tests in Kotlin, so tests must use only `public` API: a Konsist check over test files rejects references to `internal` declarations.
- **E23, E30, E33:** history checks, as in Java. For E23, the Kotlin PSI (through Konsist or Detekt) gives the line ranges of test functions.

## B, M and P

- **B3** deciders are top-level or `object` functions over sealed interfaces, with exhaustive `when` and no `else`. Konsist checks the `evolve`, `decide` and `isTerminal` signatures on each `@AggregateRoot`. **B4–B9, B13–B18, M1, P2, P3, P6, P11, P12:** the Konsist and ArchUnit rules follow java.md sections 9 to 11 one for one. Kotlin data classes give structural equality (B9), and Detekt `EqualsWithHashCodeExist` catches inconsistent custom equality.
- **B23:** the binary-compatibility-validator plugin (`org.jetbrains.kotlinx.binary-compatibility-validator`) writes committed `api/<project>.api` files (`apiDump`) and fails `check` on any difference (`apiCheck`). Recent Kotlin Gradle plugins ship a built-in ABI validation; check its status for your Kotlin version.
- **P15:** a sealed hierarchy's order comes from source (Konsist reads the declarations in file order); enums use `entries` order.
- **B11:** **review**.

## R, S and V

These are neutral, as in java.md section 12. Give the rubric scopes `**/src/main/kotlin/**` paths.
