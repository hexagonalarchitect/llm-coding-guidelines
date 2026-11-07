# Implementing the rules in C#

This page gives the main .NET tools for each rule group. The structure follows [java.md](java.md), which explains each check in more depth.

**Project shape.** Use one project (`.csproj`) per package in the layer table, at the guide's paths: `src/Kernel`, `src/Contexts/Loans/Domain` and so on. Each project's root namespace mirrors its path. Shared settings live in `Directory.Build.props`, and per-layer settings in `Directory.Build.props` files placed in layer folders, or in a `Layer.<name>.props` file that each project imports. Like Gradle convention plugins, per-layer props files scope rules to layers without path matching. Versions below are placeholders.

Statuses: **auto**, **report**, **review** and **neutral** mean the same as in the Java page.

## F, H and I: hygiene and supply chain

```xml
<!-- Directory.Build.props (all projects) -->
<Project>
  <PropertyGroup>
    <LangVersion>13.0</LangVersion>                          <!-- pinned language version (F1) -->
    <TreatWarningsAsErrors>true</TreatWarningsAsErrors>
    <Nullable>enable</Nullable>                               <!-- C5: nullable reference types -->
    <AnalysisLevel>9</AnalysisLevel><AnalysisMode>All</AnalysisMode>   <!-- pinned analyser set, not "latest" -->
    <EnforceCodeStyleInBuild>true</EnforceCodeStyleInBuild>
    <CheckForOverflowUnderflow>true</CheckForOverflowUnderflow>       <!-- F1: overflow checking is on -->
    <GenerateDocumentationFile>true</GenerateDocumentationFile>       <!-- G12: CS1591 becomes an error -->
    <Deterministic>true</Deterministic>
    <ContinuousIntegrationBuild Condition="'$(CI)' == 'true'">true</ContinuousIntegrationBuild>   <!-- H6 -->
    <RestorePackagesWithLockFile>true</RestorePackagesWithLockFile>   <!-- H2: packages.lock.json -->
    <NuGetAudit>true</NuGetAudit><NuGetAuditMode>all</NuGetAuditMode><NuGetAuditLevel>high</NuGetAuditLevel>
  </PropertyGroup>
</Project>
```

- **F1, warnings and formatting.**
  - Every rule's severity is set in a committed `.editorconfig` (`dotnet_diagnostic.<ID>.severity = error`), so the file names every rule kept.
  - Formatting is checked with `dotnet format --verify-no-changes`, using a pinned SDK.
  - A flat test rejects `<NoWarn>`, `<WarningsNotAsErrors>` and `#pragma warning disable` unless an `[allow]` entry lists them. `[SuppressMessage]` attributes need an allow entry too.
  - **C# is the one language here with a real overflow switch:** `CheckForOverflowUnderflow`.
- **F3, F6:** SonarAnalyzer.CSharp (a free NuGet analyser) has S1135 (TODO tags) and S125 (commented-out code). A small script checks that each TODO has a backlog id and that cited rule ids exist. Use BannedApiAnalyzers for `Console.WriteLine` and `Debug.WriteLine`.
- **H1:** `global.json` with `"sdk": { "version": "9.0.100", "rollForward": "disable" }`.
- **H2.**
  - Central Package Management: `Directory.Packages.props` with `ManagePackageVersionsCentrally` and `CentralPackageTransitivePinningEnabled`, and exact versions written as `[1.2.3]` to forbid ranges.
  - Lock files and `dotnet restore --locked-mode` in the gate.
  - `TreatWarningsAsErrors` makes NuGet resolution warnings (NU1603, NU1605, NU1608) errors.
  - Pin analysers and tools (`dotnet-tools.json`) the same way.
- **H4:** the supply runner reads `packages.lock.json` and gets each version's publish date from the NuGet registration API (the `published` field). Renovate's `minimumReleaseAge` keeps update proposals in line.
- **H5:** nightly `dotnet list package --vulnerable --include-transitive`, with a judge failing on High or Critical. NuGet audit during restore also raises NU1903 and NU1904 at those levels; in the commit gate, keep audit warnings out of `TreatWarningsAsErrors` so that new advisories hit only the nightly run.
- **H6:** `Deterministic` plus `ContinuousIntegrationBuild` (which normalises source paths). The nightly run builds two clean clones at different paths with `--no-restore` after restore, and compares SHA-256 hashes of the output assemblies.
- **I6:** Roslyn analysers run on source and are not fooled by inlining. NetArchTest and ArchUnitNET read IL, and C# `const` values are inlined into the IL like Java constants, so the I6 caveat in java.md section 3 applies.

## A: layers and dependencies

- **A1, A2, A6, A14.** `ProjectReference` edges are the package graph. A test parses each `.csproj` and checks the edges against the layer table; MSBuild itself rejects project cycles. NetArchTest or ArchUnitNET checks namespaces inside the assemblies:

  ```csharp
  var result = Types.InAssembly(typeof(LoanDays).Assembly)
      .That().ResideInNamespace("Example.Contexts.Loans.Domain")
      .ShouldNot().HaveDependencyOnAny("Example.Infrastructure", "Example.Contexts.Loans.Application", "System.IO", "System.Net")
      .GetResult();
  Assert.True(result.IsSuccessful, "A1: " + string.Join(", ", result.FailingTypeNames ?? []));   // route through the counting Check (E22)
  ```

- **A10:** a test reads each project's `PackageReference`s and compares them with `[packages]`. NetArchTest checks that only the wrapper's namespace depends on the package's namespaces. Ban `Microsoft.Extensions.DependencyInjection`, Autofac and similar containers in every project; the host wires by passing functions.
- **A9, B22:** NetArchTest `HaveNameMatching` and `HaveNameEndingWith`, or a custom rule splitting names into CamelCase words.
- **A17:** CA1502 with a `CodeMetricsConfig.txt` entry `CA1502(Method): 5` in shell projects (`15` elsewhere). Check the exact entry syntax in the CA1502 documentation. Banning `switch` and `is` over domain types in the shell needs a small house Roslyn analyser. The invariant part is **review**.

## C, D and G: the functional core

- **C1, C14:** a house Roslyn analyser (`SyntaxKind.ThrowStatement`, `ThrowExpression`, `TryStatement`) reports these in Kernel, Domain and Application, enabled through each layer's `.editorconfig`. Use CA1031 (do not catch general exceptions) and the empty-catch rules at the edges. Results come from a kernel `Result<T, TError>`, or from OneOf or Dunet as a listed core library.
- **C2.** Use records with `init` accessors and `System.Collections.Immutable` (`ImmutableArray`, `ImmutableSortedDictionary`), which ships in the base class library. NetArchTest `.Should().BeImmutable()` covers core types. BannedApiAnalyzers bans `List<T>`, `Dictionary<,>`, `HashSet<T>` and arrays in public types (or use a NetArchTest custom rule).
- **C3:** classes and records *can* have a private constructor in C#: `public sealed record LoanDays { private LoanDays(int value) => Value = value; public int Value { get; } public static Result<LoanDays, LoanDaysError> Create(int raw) ... }`. A NetArchTest custom rule requires every `IConstrained` type to have no public constructor, plus `Create` and `Value`. A record's `with` expression can bypass `Create`, so prefer a sealed class, or ban `with` on constrained types with an analyser.
- **C5:** `<Nullable>enable</Nullable>` with warnings as errors. In core, ban `?` reference types, the `!` null-forgiving operator and `null` literals with a house analyser. Use `default` only for value types listed in `[allow].struct_constrained`.
- **C6, C12.** BannedApiAnalyzers (`BannedSymbols.txt`, rule RS0030) bans `Enumerable.First`, `Single`, `Last` and `ElementAt`, `Nullable<T>.Value`, `int.Parse` and the list indexer. **C# has no closed class hierarchies** in the language versions this page assumes, so a switch expression over an abstract record base needs a `_` arm (CS8509), which C12 forbids. Three ways out:
  - generate matchers with a source generator such as Dunet, or use OneOf's `Match`, which takes one function per case and is exhaustive by construction;
  - use the ExhaustiveMatching analyser's closed-hierarchy attribute;
  - if your C# version has the proposed union types, use them.

  For enums, a switch expression without `_` raises CS8524 (unnamed enum values). Suppress CS8524 alone, with an `[allow]` entry, and keep CS8509 as an error.
- **C9:** CA1806 (do not ignore method results), plus `[MustUseReturnValue]` (JetBrains annotations) or a house analyser for the kernel `Result`.
- **C10:** use `internal` by default; PublicApiAnalyzers holds the explicit surface (see B23).
- **D1–D3, D12, D13.** One `BannedSymbols.txt` per core layer, added as `<AdditionalFiles>` in that layer's props file:

  ```
  P:System.DateTime.Now;D1: the core reads Kernel's Date
  P:System.DateTimeOffset.UtcNow;D1: use the Clock wrapper
  T:System.Random;D2: randomness only through Kernel's seeded service
  M:System.Guid.NewGuid;D1: ids are made at the edge (P6)
  T:System.Collections.Generic.HashSet`1;D12: use ImmutableSortedSet
  T:System.Collections.Generic.Dictionary`2;D12: use ImmutableSortedDictionary
  ```

  Note that `string.GetHashCode()` is randomised per process in .NET, so hash-ordered output differs from run to run. Ban `double`, `float` and `decimal` in Kernel and Domain (D3) with a house analyser. This page is not sure BannedApiAnalyzers catches the keyword spellings, so verify that with a planted breach.
- **D5:** an analyser bans string literals in core outside the `TextKeys` file.
- **G3, G4, G7, G11.** Complexity: CA1502 (`15`, and `5` in the shell). SonarAnalyzer rules cover the rest: S138 (function length, 80), S104 (file length, 600), S134 (nesting, 4), S109 (magic numbers), S107 (parameters, 4). Sonar's thresholds are set in a `SonarLint.xml` additional file, whose format you should check for your analyser version. Report-only backstops are set to severity `suggestion`. Banning tuples (`ValueTuple`) and `bool` parameters in public signatures needs a NetArchTest custom rule.
- **G12:** `GenerateDocumentationFile` with CS1591 (missing XML comment on a public member) as an error in core projects.
- **D8, B11, G13:** **review**.

## E: tests

- **Frameworks.** Use xUnit or NUnit, with FsCheck or CsCheck for properties (E10). Pin the gate seed (FsCheck's `Replay`, CsCheck's `seed:` argument) and use fresh seeds at night; CsCheck also reads seed settings from environment variables, which you should check in its documentation. Use Stryker.NET (`dotnet stryker`, `thresholds.break`, and `--since` for changed code) for E14 and E34, gating on a surviving-mutant ratchet rather than a score.
- **E1:** the guide's `[banned].mocking` list already names .NET libraries. Reject Moq, NSubstitute, FakeItEasy, JustMock and WireMock.Net in `Directory.Packages.props` with a test, and ban their namespaces with NetArchTest. Nullable wrappers expose `Create(...)` and `CreateNull(...)` with optional parameters (C# default arguments).
- **E2:** ban `[InternalsVisibleTo]` (a test over the source or the assembly attributes), reflection into private members (`BindingFlags.NonPublic`, through BannedApiAnalyzers), and `#if DEBUG` or test symbols in production code (an analyser on preprocessor directives).
- **E22:** NUnit exposes `TestContext.CurrentContext.AssertCount`. A `[TearDown]` in a base fixture, or an `ITestAction`, fails tests whose count is zero. With xUnit, count calls in a house `Check` and fail in a `BeforeAfterTestAttribute.After`. Ban direct `Assert` and FluentAssertions calls with NetArchTest. Ban `[Fact(Skip = ...)]`, `[Ignore]` and `[Explicit]` with an analyser or a source scan.
- **E8, E36.** `dotnet test --logger "trx"` (or the JunitXml.TestLogger package) gives each test's duration. Tiers are separate test projects, nightly tests carry `[Trait("Category", "Nightly")]`, and the gate runs `--filter "Category!=Nightly"`. JIT warm-up applies as on the JVM, so measure the test body in-process and warm up in an assembly-level fixture.
- **E12:** in the TRX results, assertion failures have the framework's assertion exception type, which tells them apart from crashes.
- **E13:** ban `static` mutable fields with an analyser. Retries (xRetry, NUnit `[Retry]`) are banned. For the nightly shuffle, use a custom `ITestCaseOrderer` (xUnit) that shuffles with a recorded seed. This page cannot vouch for a built-in random-order switch in either framework, so check the version you pin.
- **E14:** coverlet branch coverage, with a ratchet file.

## B, M and P

- **B3:** deciders are static methods on a static class over generated unions, such as `Dunet` `[Union]` records with a `Match` method. NetArchTest custom rules check the `Evolve`, `Decide` and `IsTerminal` signatures. **B4–B9, B13–B18, M1, P2, P3, P6, P11, P12:** custom rules follow java.md sections 9 to 11 one for one. Records give structural equality (B9).
- **B23:** Microsoft.CodeAnalysis.PublicApiAnalyzers keeps committed `PublicAPI.Shipped.txt` and `PublicAPI.Unshipped.txt` files and fails on any undeclared change (RS0016, RS0017).
- **P2:** ban `System.Text.Json` attributes and `[Serializable]` in core, and ban reflection-based `JsonSerializer` calls outside the codec wrapper. A source-generated serialiser counts as no reflection.
- **P15:** take the order from source (the enum declaration order, and the order of the generated union's cases).

## R, S and V

These are neutral, as in java.md section 12. Give the rubric scopes `src/**/*.cs` paths.
