# Implementing the rules in TypeScript

This page gives the main TypeScript tools for each rule group. The structure follows [java.md](java.md), which explains each check in more depth.

**Project shape.** Use one workspace package (pnpm or npm workspaces) per package in the layer table, at the guide's paths: `kernel/`, `contexts/loans/domain/` and so on. Each has its own `package.json` (its declared dependencies are the package graph) and extends a per-layer ESLint configuration (`eslint.layer.domain.mjs` and the like). The flat-config `files` globs scope each rule set to its layer's folders, which plays the role of Gradle's convention plugins. Versions below are placeholders.

Statuses: **auto**, **report**, **review** and **neutral** mean the same as in the Java page.

## F, H and I: hygiene and supply chain

- **F1, compiler and lint settings.** One `tsconfig.base.json` that every package extends:

  ```jsonc
  { "compilerOptions": {
      "strict": true, "noUncheckedIndexedAccess": true, "exactOptionalPropertyTypes": true,
      "noImplicitReturns": true, "noFallthroughCasesInSwitch": true, "noImplicitOverride": true,
      "noPropertyAccessFromIndexSignature": true, "useUnknownInCatchVariables": true,
      "verbatimModuleSyntax": true, "isolatedModules": true, "allowUnreachableCode": false, "allowUnusedLabels": false,
      "noUnusedLocals": true, "noUnusedParameters": true, "target": "ES2023" } }
  ```

  - `tsc --noEmit` errors fail the gate.
  - ESLint runs with `--max-warnings 0`, and its flat configuration lists every rule kept (no `extends` of a floating preset).
  - Prettier runs with `--check` and a pinned version.
  - A flat test rejects `eslint-disable`, `@ts-ignore` and `@ts-expect-error` comments unless an `[allow]` entry lists them, and per-package `tsconfig` overrides of the strict flags.
  - **Overflow:** JavaScript `number` is a float64, so integers lose precision silently past 2^53. See D3 for the remedy.
- **F3, F6.** `no-warning-comments` flags TODOs; a short script allows only `TODO(BL-<n>)` and checks cited rule ids. Also turn on `no-console`, `no-debugger`, and `@typescript-eslint/no-unused-vars`. Use `ts-prune` or `knip` to report exports used only by tests.
- **H1.** Pin Node exactly:
  - `.nvmrc` or `.node-version` holds the exact version;
  - `"engines": { "node": "22.11.0" }` in `package.json`, with `engine-strict=true` in `.npmrc`;
  - the `"packageManager": "pnpm@<exact>"` field.
- **H2:** `save-exact=true` in `.npmrc`, a committed lockfile, and `pnpm install --frozen-lockfile` (or `npm ci`) in the gate. A test rejects any `^` or `~` version range in a `package.json`. ESLint, Prettier, TypeScript and Stryker are devDependencies pinned the same way.
- **H4:** pnpm's `minimumReleaseAge` setting (in minutes, in `pnpm-workspace.yaml`, pnpm 10.16 or later) refuses packages younger than the limit. Set it to 20160 for 14 days. npm offers `--before <date>` instead. The supply runner checks lockfile versions against the registry's `time` field.
- **H5:** nightly `pnpm audit --audit-level high` (or `npm audit --audit-level=high`), or osv-scanner on the lockfile.
- **H6:** `tsc` output is deterministic. The nightly run builds two clean clones at different paths with the network off after install, and compares the SHA-256 hashes of `dist/**` and of `npm pack` tarballs (npm normalises tarball timestamps).
- **I6:** ESLint and dependency-cruiser read source, so inlining cannot fool them. Bundler output is never checked.

## A: layers and dependencies

- **A1, A2, A6, A14.** dependency-cruiser in "allowed" mode, so that anything not explicitly allowed fails, including unclassified folders:

  ```js
  // .dependency-cruiser.cjs (excerpt)
  module.exports = {
    forbidden: [
      { name: "A6-no-circular", severity: "error", from: {}, to: { circular: true } },
      { name: "A2-domain-only-own-contracts", severity: "error",
        from: { path: "^contexts/([^/]+)/domain" }, to: { path: "^contexts/(?!$1/)[^/]+/domain" } },
    ],
    allowed: [
      { from: { path: "^kernel/" }, to: { path: "^kernel/" } },
      { from: { path: "^contexts/([^/]+)/domain/" }, to: { path: ["^kernel/", "^contexts/$1/(domain|contracts)/"] } },
      // ... one entry per row of the layer table
    ],
    allowedSeverity: "error",
  };
  ```

  Upstream-context `contracts` entries come from the context map (B1); generate the `allowed` list from the layer table and context map rather than writing it by hand. A test also checks each workspace `package.json` dependency list against the table.
- **A10.** dependency-cruiser rules on `to: { dependencyTypes: ["npm"] }`, with a `path` naming each package and a `pathNot` for its wrapper. Each package's `package.json` lists only the third-party packages allowed in `[packages]`. Ban DI containers (`inversify`, `tsyringe`, `typedi`) everywhere.
- **A9, B22:** a custom ESLint rule, or `@typescript-eslint/naming-convention` with a `custom.regex` that bans the words, applied to types, classes and exported functions in the domain, contracts and application layers. Split names into CamelCase words.
- **A17:** `complexity: ["error", 5]` in the shell layers. `no-restricted-syntax` bans `SwitchStatement` and `instanceof` there; branch on the kernel `Result` with its `match`. The invariant part is **review**.

## C, D and G: the functional core

- **C1, C14:** eslint-plugin-functional (v6 rule names): `functional/no-throw-statements` and `functional/no-try-statements` in Kernel, Domain and Application. Use a kernel `Result<T, E>` discriminated union, or `neverthrow` as a listed core library.
- **C2:** `functional/no-let`, `functional/immutable-data`, `functional/prefer-immutable-types` and `functional/type-declaration-immutability` (public types are `readonly` and `ReadonlyArray` or `ReadonlyMap`), plus `functional/no-classes` in the core.
- **C3:** a branded type with a factory:

  ```ts
  export type LoanDays = number & { readonly __brand: "LoanDays" };
  export const LoanDays = {
    create: (raw: number): Result<LoanDays, LoanDaysError> =>
      raw < 1 ? err("BelowMinimum") : raw > Rules.MAX_LOAN_DAYS ? err("ExceedsMaximum") : ok(raw as LoanDays),
    value: (d: LoanDays): number => d,
  };
  ```

  A brand can only be produced by a cast. `@typescript-eslint/consistent-type-assertions` with `{ assertionStyle: "never" }` everywhere except each constrained type's own file makes `create` the only way in. In the core, use `bigint` or a safe-integer brand instead of `number` (see D3).
- **C4:** `no-restricted-syntax` on `TSStringKeyword`, `TSNumberKeyword` and `TSBooleanKeyword` inside exported declarations of the domain and contracts layers, excepting constrained-type files.
- **C5:** `unicorn/no-null` (eslint-plugin-unicorn), `@typescript-eslint/no-non-null-assertion`, and `strictNullChecks` (part of `strict`). Optional values use a kernel `Option`.
- **C6, C12.**
  - `noUncheckedIndexedAccess` makes `xs[i]` and `record[key]` possibly `undefined`.
  - `no-restricted-properties` bans `JSON.parse` and `Number.parseInt` without a result wrapper.
  - `no-restricted-syntax` with ``BinaryExpression[operator='/']:not([right.object.name='Rules'])`` allows division only by `Rules` constants.
  - Sum types are discriminated unions. `@typescript-eslint/switch-exhaustiveness-check` with `allowDefaultCaseForExhaustiveSwitch: false`, plus `no-restricted-syntax` on `SwitchCase[test=null]` (a `default:` case), bans wildcard cases over the code base's own unions.
- **C9:** `@typescript-eslint/no-floating-promises` covers promises; for `Result`, use `neverthrow/must-use-result` (eslint-plugin-neverthrow) or a house rule.
- **C10:** use explicit `export` lists, and `no-restricted-syntax` on `ExportAllDeclaration` (`export *`) in the core.
- **C13:** `functional/no-return-void`, plus `no-restricted-syntax` on function-typed parameters returning `void` or `Promise`.
- **C15:** `no-restricted-syntax` on `TSEnumDeclaration` in domain and contracts; categories are string-literal unions.
- **D1, D2, D13.** `no-restricted-globals` (`Date`, `process`, `console`, `setTimeout`, `fetch`, `crypto`, `Intl`), `no-restricted-properties` (`Math.random`, `Date.now`, `performance.now`), `no-restricted-imports` (`node:*`, `fs`, `net`, `os`), and `no-restricted-syntax` on calls to `toLocale*` and `localeCompare` in Kernel and Domain.
- **D3.** All JavaScript numbers are floating point, so "no floating point in the core" cannot be checked on `number`. Use **`bigint`** for domain integers: it is exact, cannot overflow, and its `/` truncates. Alternatively use a kernel safe-integer brand whose operations check `Number.isSafeInteger`. Ban `TSNumberKeyword` and numeric literals with fractions (`Literal[raw=/\./]`) in the core. This is **auto** when the core uses `bigint`; otherwise it is a **review** gap, recorded with that reason.
- **D12.** JavaScript `Map` and `Set` iterate in insertion order by specification, so they are deterministic. The rule then concerns object key order (integer-like keys come first) and hash libraries. Ban `Object.keys`, `Object.values` and `Object.entries` over records used as maps in the core, and use kernel sorted collections for anything ordered by key.
- **D5:** `no-restricted-syntax` on string `Literal` and `TemplateLiteral` in the core, outside the `textKeys.ts` file.
- **G3, G4, G7, G11.** ESLint core rules:
  - `complexity: ["error", 15]` (`5` in the shell), `max-depth: ["error", 4]`;
  - `max-lines-per-function: ["error", 80]`, `max-lines: ["error", 500]` (in TypeScript a file is a module, so the 500-line module limit is the binding one);
  - `max-params: ["error", 4]`;
  - `@typescript-eslint/no-magic-numbers` with `ignore: [-1, 0, 1]`, excluding `rules.ts`.

  Ban tuple types (`TSTupleType`) and `boolean` parameters in public signatures with `no-restricted-syntax`. Report-only backstops (`max-statements`, `max-nested-callbacks`) run in a separate report-only ESLint invocation whose exit code is ignored.
- **G12:** `jsdoc/require-jsdoc` (eslint-plugin-jsdoc) with `publicOnly: true` and the declaration contexts listed, in the core layers.
- **D8, B11, G13:** **review**.

## E: tests

- **Frameworks.** Use Vitest (or Jest), fast-check for properties (E10), and StrykerJS for E14 and E34 (`stryker.config.json` with `incremental: true`; gate on a surviving-mutant ratchet, not on `thresholds.break`). Coverage comes from `@vitest/coverage-v8` with branch thresholds as the ratchet.
- **E1:** `no-restricted-imports` bans `sinon`, `testdouble`, `ts-mockito`, `jest-mock-extended`, `vitest-mock-extended`, `nock` and `msw`. `no-restricted-properties` bans `vi.mock`, `vi.fn`, `vi.spyOn`, `jest.mock`, `jest.fn` and `jest.spyOn`. Nullable wrappers export `create(...)` and `createNull(options?)`.
- **E2:** ban exports named `*ForTest*` and `__test__`. Ban `process.env.NODE_ENV === "test"` branches in production code with `no-restricted-syntax`.
- **E22.** In Jest, `expect.hasAssertions()` in a `beforeEach` in the setup file makes every test assert. This page is not sure Vitest honours the same call in a global `beforeEach`, so test it with a planted breach, or use a house counting `check` with an `afterEach` that fails at zero. `vitest/expect-expect`, `vitest/no-focused-tests` and `vitest/no-disabled-tests` (from `@vitest/eslint-plugin`; Jest has the same in `eslint-plugin-jest`) ban `.only`, `.skip` and `.todo`. Direct `expect` outside the `check` module is banned with `no-restricted-imports` and `no-restricted-globals`.
- **E10:** run `fc.assert(fc.property(...), { seed: GATE_SEED, numRuns: 200 })` in the gate, and fresh seeds at night through `fc.configureGlobal({ seed })` driven by the nightly script. fast-check prints the seed and `path` of a failure; replay them in a named example test.
- **E8, E13, E36.**
  - Vitest's JUnit reporter (`--reporter=junit --outputFile=...`) gives each test's duration for the inventory judge.
  - Tiers are separate Vitest projects or config files, and nightly tests are `*.nightly.test.ts`, excluded from the gate's `include`.
  - Module loading and transformation make the first test in each worker slower, so warm up in a global setup and measure the test body.
  - The nightly run sets `sequence: { shuffle: true, seed }`, and runs once with `--no-file-parallelism` and once in parallel.
  - `retry` must stay 0, and `jest.retryTimes` is banned.
  - E36's spec runner generates one `test("spec/<case>", ...)` per case file.
- **E12:** in the JUnit XML, an assertion failure has an `AssertionError` type, which tells it apart from a crash.
- **E23, E30, E33:** history checks, as in Java. For E23, the TypeScript compiler API gives test function ranges. Reject committed `*.received.*` and auto-updated snapshots (`-u` in CI scripts).

## B, M and P

- **B3:** deciders are exported functions over discriminated unions, with exhaustive `switch` and no `default`. A custom ESLint rule, or a test using the TypeScript compiler API, checks the `evolve`, `decide` and `isTerminal` signatures. **B4–B9, B13–B18, M1, P2, P3, P6, P11, P12:** custom rules follow java.md sections 9 to 11. B9: value objects are plain readonly data compared with a kernel structural `equals`; ban `===` on objects in the core with a type-aware custom rule.
- **B23:** API Extractor (`api-extractor run`, with `--local` only on developer machines) writes a committed `*.api.md` report per kernel and contracts package, and fails CI on any undeclared difference.
- **P2:** no decorators (`class-transformer`, `class-validator`) in the core; codecs decode through `create`.
- **P15:** take the order from source (union member order through the compiler API, or the codec's tag table).

## R, S and V

These are neutral, as in java.md section 12. Give the rubric scopes `**/*.ts` paths.
