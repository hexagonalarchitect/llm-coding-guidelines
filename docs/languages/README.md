# The rules in other languages

The guide ([README.md](../../README.md)) describes how a Rust code base checks its rules. These pages tell an agent how to build the same checks in another language, without the reference code base:

- **[Java](java.md)** is the main page. It covers every rule group, names the tool and setting for each rule that has an automated check, gives short snippets, and explains the language-neutral runners, the planted-breach meta-test (`F5`) and gate speed on the JVM. Its core is compiled and runs in the [Java starter](../../examples/java-starter/README.md), a Gradle project whose gate is green; [docs/quick-start.md](../quick-start.md) walks through it.
- **[Kotlin](kotlin.md)** builds on the Java page and adds Detekt, Konsist and kotest.
- **[C#](csharp.md)** covers MSBuild props, Roslyn analysers, BannedApiAnalyzers, NetArchTest or ArchUnitNET, FsCheck or CsCheck, and Stryker.NET.
- **[TypeScript](typescript.md)** covers a strict tsconfig, ESLint (with the functional plugin), dependency-cruiser, fast-check and StrykerJS.

## Common ground in every language

- **Scope rules by build structure.** Give each package in the layer table its own build unit: a Gradle project, a `.csproj` or a workspace package. Then point each tool at a per-layer configuration. A rule "in `Kernel` and `Domain`" becomes a line in two configuration files, not a path match inside the check.
- **Prefer flat, syntactic rules** (guide, `F5`): ban a form outright rather than try to prove it harmless.
- **Every check ships a planted breach** under `breaches/`, and its failure message names the rule id.
- **The history, inventory, supply-chain and model-review runners are language-neutral.** Only their inputs change: lockfiles, JUnit, TRX or other test-result XML, API surface files, and rubric path scopes. [java.md section 14](java.md#14-the-runners-in-a-jvm-project) lists the inputs for the JVM. The other pages name their counterparts.
- **Some rules stay review in every language,** because they judge meaning rather than form: `B11`, `D8`, `G13`, and the `L` halves of `Q+L` rules. The model reviewer stays advisory until its rubrics are calibrated on the language's own cases (`V4`). [java.md section 13](java.md#13-rules-that-stay-review-in-java-and-why) lists them with reasons.

## Main tool per rule group

| Group | Java | Kotlin | C# | TypeScript |
| :--- | :--- | :--- | :--- | :--- |
| **F** Compiler hygiene | javac `-Xlint:all -Werror`, Error Prone (pinned list), Spotless with google-java-format, Checkstyle | `allWarningsAsErrors`, `explicitApi()`, Detekt, ktfmt or ktlint | `TreatWarningsAsErrors`, `.editorconfig` severities, `CheckForOverflowUnderflow`, `dotnet format` | strict `tsconfig`, ESLint `--max-warnings 0`, Prettier |
| **H** Supply chain | Gradle wrapper checksum, exact JDK, `dependencyLocking` (strict), verification metadata, OWASP dependency-check or osv-scanner, reproducible archives | same as Java | `global.json` (`rollForward: disable`), Central Package Management, `packages.lock.json` with `--locked-mode`, NuGet audit, `Deterministic` and `ContinuousIntegrationBuild` | exact `engines` and `packageManager`, `save-exact`, frozen lockfile, pnpm `minimumReleaseAge`, `pnpm audit` or osv-scanner |
| **I** Governance | neutral history runner; a suppression judge for `I5` | same as Java | neutral history runner | neutral history runner |
| **A** Layers | Gradle projects per layer with a build-file test, ArchUnit (`layeredArchitecture`, `slices`) | the same projects, Konsist `assertArchitecture`, ArchUnit | `.csproj` references with a test, NetArchTest or ArchUnitNET | workspaces, dependency-cruiser (`allowed` mode), `no-restricted-imports` |
| **C** Functional core | sealed interfaces and records, kernel `Result`, NullAway, Checkstyle `IllegalToken` and `MatchXpath`, ArchUnit, forbidden-apis, house Error Prone checks | sealed types with exhaustive `when`, Detekt (`ElseCaseInsteadOfExhaustiveWhen`, `UnsafeCallOnNullableType`), Konsist | records, nullable reference types, BannedApiAnalyzers, house Roslyn analysers, Dunet or OneOf for closed unions | discriminated unions, `switch-exhaustiveness-check`, eslint-plugin-functional, branded types |
| **D** Determinism | forbidden-apis signature files, ArchUnit (no `Set.of`/`Map.of` or hash collections in core), Checkstyle bans on float tokens | Detekt `ForbiddenImport` and `ForbiddenMethodCall`, Konsist | BannedApiAnalyzers `BannedSymbols.txt` per layer | `no-restricted-globals` and `no-restricted-properties`; `bigint` in place of floats |
| **G** Simplicity | Checkstyle (`CyclomaticComplexity`, `MethodLength`, `FileLength`, `MagicNumber`), PMD `CognitiveComplexity`, `-Xdoclint` | Detekt complexity rules and `Undocumented*` | CA1502, SonarAnalyzer (S138, S104, S134, S109, S107), CS1591 | ESLint `complexity`, `max-depth`, `max-lines*`, `max-params`, `no-magic-numbers`, `jsdoc/require-jsdoc` |
| **E** Tests | JUnit 5 with a counting `Expect` parameter and extension, jqwik, PIT, JaCoCo, ArchUnit bans on Mockito, JUnit XML with an in-process timing judge | kotest (`AssertionMode.Error`, `PropTestConfig(seed)`), Kover, PIT (noisy on Kotlin) | NUnit `AssertCount` or a house check in xUnit, FsCheck or CsCheck, Stryker.NET, coverlet, TRX | Vitest or Jest (`expect.hasAssertions`), fast-check, StrykerJS, JUnit reporter |
| **B** Domain model | ArchUnit decider and glossary rules, a committed API dump (`B23`) | Konsist, binary-compatibility-validator `.api` files | NetArchTest custom rules, PublicApiAnalyzers | custom ESLint or compiler-API checks, API Extractor |
| **M** Messaging | ArchUnit marker interfaces (`Command`, `Event`, `View`) | Konsist | NetArchTest | custom ESLint rule |
| **P** Persistence | ArchUnit DTO shapes, jqwik round-trip and never-fails properties, golden files, shape file from source | same as Java, with Konsist | NetArchTest, FsCheck or CsCheck, golden files | custom rules, fast-check, golden files |
| **R**, **S**, **V** | neutral runners; Java path scopes in rubrics | neutral | neutral | neutral |
