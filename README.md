> **DRAFT** — A working draft. It holds the guide (this page: 157 rules, each with its statement), a rule-table template and its schema, speed-budget and runner specifications, rubrics with their calibration, guides for Java, Kotlin, C# and TypeScript, and a runnable Java starter project. The numbers it quotes come from one reference code base at the time of writing; measure your own.

# Coding guidelines for model-written code

## How this guide is organised

The guide was written from a working Rust code base, called *the reference code base* throughout. You never need to see it. Where this page quotes its paths, checks or numbers, they are examples of one way to hold a rule; the language guides give the equivalent in your language, and the templates give a starting point you adjust and measure.

| Document | What it holds | Read it when |
| :--- | :--- | :--- |
| **README.md** (this page) | The idea (Section 1), the adoption order for an existing project (Section 2), worked examples (Section 3), every rule with its statement by group (Section 4), and keeping the gate fast (Section 5) | First, and whenever you cite or apply a rule |
| [docs/quick-start.md](docs/quick-start.md) | A day-one path for an empty repository in any language, worked through in Java with the starter | You are starting a new project |
| [docs/glossary.md](docs/glossary.md) | The guide's own terms (decider, Nullable, planted breach, dormant, ratchet, owed work, ...), each linked to its rules | A term is unfamiliar |
| [docs/rule-table.md](docs/rule-table.md) and [templates/rules.toml](templates/rules.toml) | The rule table's schema, and a starter copy with all 157 rule rows, the layer table, word lists and the `[allow]` table | You set up `standards/`, add a rule, or add an exception |
| [templates/budgets.toml](templates/budgets.toml) | Per-test and per-stage speed budgets, the wall-time ratchet, and where each number came from | You set up the gate (`E8`) |
| [docs/self-test-harness.md](docs/self-test-harness.md) | Planted breaches, the check registry and the meta-test (`F5`), in language-neutral terms | Before you write your first check |
| [docs/languages/](docs/languages/README.md): [Java](docs/languages/java.md), [Kotlin](docs/languages/kotlin.md), [C#](docs/languages/csharp.md), [TypeScript](docs/languages/typescript.md) | The tool, setting and check for each rule in each language | You build a check in your language |
| [examples/java-starter/](examples/java-starter/README.md) | A runnable Gradle project: the layers, a decider, a Nullable wrapper, architecture checks with planted breaches and a meta-test, counting assertions, per-test time budgets, property and mutation tests, Error Prone and NullAway | You want a working shape to copy, in Java or as a model for another language |
| [docs/runners.md](docs/runners.md) | Specifications for the history, inventory, supply-chain and model-review runners, and the landing script (`S9`) | You build the runners that do not read source code |
| [docs/rubrics.md](docs/rubrics.md) and [docs/rubrics/](docs/rubrics/tests.md) | The rubric format, the rubric questions by family, and how to calibrate the model reviewer | Before you turn on model review (`V1`–`V8`) |

## 1. The idea in one page

When a language model writes most of a code base and a person steers, prose style guides fail. Models follow local patterns, take shortcuts to make tests pass, and quietly erode module boundaries unless every boundary is held by a fast, automated check.

This guide states **157 rules**, each with its statement, says how the reference code base checks them, and says which parts remain pending there at the time of writing. 155 come from the reference code base's [rule table](docs/rule-table.md) (a starter copy is [templates/rules.toml](templates/rules.toml)); two (`S9` and `E37`) are new guidance drawn from its practice, with no Rust check yet. The reference catalogued 32 of its rules (dormant, planned, report and process rows, none of which gates a commit there) without a prose statement; this guide states them from their rule-table rows and design notes, and marks one, `D9`, as optional. A small subset of rules (`A5`, `A7`, `D4`, `D10`, `E15`, `E25`, and the ported-routine clauses of `G3`, `G4`, and `G7`) applies only when reimplementing an existing reference program and can be skipped in greenfield projects.

### Four mechanisms hold the line

1. **Automated checks**: Static, structural, git-history, supply-chain, or test-runner checks run on every commit (`commit`), every night (`nightly`), or on demand (`on-demand`).
2. **Planted breaches (`F5`, [self-test harness](docs/self-test-harness.md))**: As a general principle under rule `F5`, every automated check is expected to ship with a deliberate violation—a **planted breach**—in an isolated breach workspace (`breaches/**`), where an architecture meta-test verifies that the check selects real code (unless marked dormant) and fails on that breach alone. In the rule table, a Rust status of `built` means the table marks the check as built; however, the table names one of the five check runners for only 47 of the 119 `built` or `partial` rules (one more, `F5`, names the meta-test itself, and 71 name none) and records no per-rule planted breach in the table itself. This guide asks adopters to require a planted breach for every automated check they build, and states this principle once here rather than repeating breach caveats on individual rules.
3. **Calibrated model review (`V1`–`V8`, [rubrics](docs/rubrics.md))**: Where syntax and types cannot see enough (for example, whether domain names reflect the business domain or whether a test helper leaks the production formula), an isolated model reviewer grades the diff against binary rubric questions. **Rust status at the time of writing**: in the Rust workspace, the model-review runner runs in **advisory-only mode** because the rubrics were calibrated (`V4`) only on code in another language (the code base's earlier F# track). Until a rubric set calibrated on Rust cases exists, the runner forces every verified finding on a Rust path to the severity `advice`, which never blocks (the rule table also notes that the rubric file-path scopes do not yet name Rust paths). So pure review rules (`B11`, `D8`, `G13`) and the model-review half of combined or partial rules do not block Rust commits.
4. **Tightening-only principle files and expiring exceptions (`F4`, `I2`, `I5`)**: Principle files (the rule table, its allow-list, the speed and reliability budgets in [templates/budgets.toml](templates/budgets.toml), and the rubrics) may only add or tighten rules unless a commit cites a dated approval (`F4`). Scoped exceptions live in the `[allow]` table, where each entry gives a reason, names the decision record it relaxes (`I2`), and carries a dated approval; rule `I5` checks that stale or expired exceptions fail the gate, though `I5` is dormant in this workspace until the central allow-list reader is enabled.

### How to read the group tables

Each group in Section 4 starts with a summary table showing every rule's ID, title, status in the reference code base, cadence, and its check and scope there. **The status and check columns describe the reference Rust code base, not your project**: a new project starts every rule at `planned` in its own copy of the rule table and moves it on as each check and its planted breach land. For the tool, setting and check that hold each rule in another language, see [docs/languages/](docs/languages/README.md): [Java](docs/languages/java.md), [Kotlin](docs/languages/kotlin.md), [C#](docs/languages/csharp.md) and [TypeScript](docs/languages/typescript.md).

Where the reference's rule table names one of its five check runners, the table refers to it in plain terms (the paths are the reference code base's; [docs/runners.md](docs/runners.md) specifies the four that do not read source code for any language):

- the **architecture check** (`rust/tests/architecture`), which inspects Cargo manifests, module graphs, and Rust syntax trees;
- the **history check** (`tools/history`), which inspects git commits, principle-file diffs, decision records, committed public-surface files, and branch limits;
- the **inventory check** (`tools/inventory`), which checks test wall-time budgets and shared behaviour-suite cases;
- the **supply-chain check** (`tools/supply`), which checks package ages, security advisories, and reproducible build hashes; and
- the **model-review runner** (`tools/review`), which grades diffs against binary rubrics in an isolated checkout.

For the 71 `built` or `partial` rules where the rule table records no check runner (and for `F5`, which names the meta-test), the last column summarises what the rule checks from its statement and the rule table metadata. The status values are:

- **`built`**: the rule table marks the check as built. Whether a planted breach proves a given rule is not recorded per rule in the rule table (which names one of the five check runners on 47 rules, the meta-test on one, and lists no per-rule breach); this guide asks adopters to require one (`F5`).
- **`built (partly report-only)`**: Marked `built` in the rule table, with a sub-clause (or, for `C8`, the check's flag) that reports or flags findings for review rather than failing the gate (`B4`, `B24`, `C8`, `G4`, `S2`).
- **`built (Q; L advisory)`**: Marked `built` in the rule table with kind `Q+L`; the mechanical check (`Q`) is marked built, while the qualitative model-review half (`L`) is advisory-only on Rust paths (`A7`, `B12`, `E11`, `P11`).
- **`built (dormant)`**: Marked `built` in the rule table, and carrying a `dormant_until` marker naming the backlog item in which the crates or files it inspects appear (`C7`, `I5`, `M2`). It does not fail commits while dormant.
- **`built (unverified in sources)`**: Marked `built` in the rule table, but the rule table names neither a check runner nor any checking tool (`I1`, `I6`).
- **`partial`**: Part of the rule is checked mechanically; the remaining parts wait for target code or rely on the advisory model reviewer (`E3` is also shown as `partial` because the rule table lists a pending part for it despite its `built` status). If the rule table also marks its active check or replay dormant until target files exist (a `dormant_until` marker, or one on the check's part, such as a replay or history half), it is marked **`partial (dormant)`** or names the dormant half (for example `partial (replay dormant)`).
- **`review (advisory on Rust)`** / **`review (attended process)`**: Held by the model reviewer (advisory-only on Rust) or by an attended session (`I9`), with no automated check.
- **`dormant` / `planned` / `report` / `process`**: The 32 rows the reference's rule table catalogues without a prose statement. This guide states them from their rule-table rows and the design notes those rows point to, with the reference product's own names made generic (its stored instance is a *stored instance*, its database file the *store*), and the italic *Reference status* note after each statement gives the status. None gates commits in the reference code base. `D9` is marked optional, because it rests on a one-line source.
- **`process (new guidance)`**: A rule this guide adds from the reference code base's practice (`S9`, `E37`). It is not in the rule table and has no automated check in Rust; the reference code base holds it by script, review, or test convention.

---

## 2. How to adopt these rules in an existing project

Adopt rules in order of engineering payoff. Do not try to adopt all 157 rules on day one; wire the meta-test first so every rule you add proves itself ([docs/self-test-harness.md](docs/self-test-harness.md)). Section 4 orders the rule groups in this same sequence. For an empty repository, follow [docs/quick-start.md](docs/quick-start.md) instead, which builds the same foundations in a day.

1. **Pin the build and turn warnings into errors (`F1`, `H1`, `H2`, `H5`)**
   Pin the compiler toolchain and every direct and transitive dependency in committed lockfiles. Enable warnings-as-errors and arithmetic overflow checks centrally. Run vulnerability audits in a nightly job so overnight advisories do not break unrelated daytime commits.
2. **Add the self-test harness and governance lints (`F4`, `F5`, `I2`, `I3`, `I5`)**
   Create a `breaches/` folder excluded from production builds. Require every new architecture check to ship with one small broken sample that makes that exact check fail (`F5`), enforce tightening-only edits on principle files (`F4`), and add a staleness check (`I5`, dormant in this workspace) for `[allow]` entries.
3. **Lock the package dependency graph (`A1`, `A2`, `A6`, `A9`, `A10`)**
   Classify every package into an ordered layer table (`Kernel`, `Contracts`, `Domain`, `Codec`, `Application`, `Infrastructure`, `Host`, `Test`). Fail the gate on unclassified packages, undeclared cross-context edges, cycles, infrastructure words in core names, or third-party libraries used outside their single wrapper package.
4. **Make the core pure, total, deterministic, and simple (`C1`–`C6`, `C12`, `D1`–`D3`, `D12`, `G3`, `G4`)**
   Ban I/O, clocks, ambient randomness, floating point, hash-ordered collections, mutation, nulls, panics, and partial operations (`unwrap`, unchecked indexing) in `Kernel` and `Domain`. Enforce function complexity ceilings (`<= 15` in core, `<= 5` in the shell) and size backstops (`80` / `500` / `600` lines).
5. **Enforce honest, fast tests (`E1`, `E2`, `E5`, `E8`, `E12`, `E22`, `E23`, `E37`)**
   Route assertions through a counting assertion macro so assertion-free tests fail the gate and tautological assertions are flagged. Ban mocking libraries in favour of Nullable infrastructure wrappers. Enforce red-before-green (`E12`, whose parent-commit replay is dormant in the Rust workspace) and forbid test edits in refactoring commits. Give every test tier and gate stage a time budget, and keep the build fast enough to meet them (`E8`, and Section 5), and make every comparing test pin how much it compared (`E37`).
6. **Add bounded-context, decider, and persistence boundaries (`B1`, `B3`, `B4`, `M1`, `P2`, `P3`, `P15`)**
   Keep the package graph equal to the declared context map, require domain aggregates to follow the pure `decide` / `evolve` decider pattern, keep serialisation in pure `Codec` packages using plain data-transfer objects, and verify committed public-surface and union-shape files in git history.
7. **Add reliability budgets, scope fences, and calibrated model review (`R1`, `R5`, `S1`–`S5`, `S9`, `V1`–`V8`)**
   Track error budgets and `Prevents:` links on fix commits, keep branches under one day and 400 lines, push only after review wherever green branches land by themselves (`S9`), and—once a rubric set calibrated on the language's own cases is in place—run the isolated model reviewer (`V1`–`V8`, partial and advisory-only on the Rust workspace) on diffs that static analysis cannot fully judge.

---

## 3. Worked examples

### Example 1: Package layers, third-party packages, and vocabulary checks (`A1`, `A10`, `A9`, `B22`)

Under `A1`, every crate's `Cargo.toml` path below the Rust workspace root must match a row in the ordered `[[layer]]` table in the rule table. In this table, `Day` (`day/Cargo.toml`) is the single thin cross-context composition package that schedules work across context `Application` packages (`A14`); it may also name `Kernel` and any context's `Contracts`, because Rust reaches a type only through a direct dependency, but never a `Domain` crate or an `Infrastructure` wrapper. Meanwhile `Import` and `ImportHost` isolate legacy-format conversion when reimplementing a reference program (`A7`). The first matching pattern wins, which is why `ImportHost` (`hosts/import/Cargo.toml`) is placed before `Host` (`hosts/{name}/Cargo.toml`) and `Breach` (`breaches/**`) is placed before `Test` (`tests/{name}/Cargo.toml`). The reference code base's rows follow; the published template ([templates/rules.toml](templates/rules.toml), schema in [docs/rule-table.md](docs/rule-table.md)) has the same rows with path patterns for Cargo, Maven, Gradle, npm and MSBuild, and calls the `Day` row `Composition`:

```toml
[[layer]]
name = "Kernel"
rust = ["kernel/Cargo.toml"]
references = []
packages = []

[[layer]]
name = "Contracts"
rust = ["contexts/{context}/contracts/Cargo.toml"]
references = ["Kernel", "Contracts:upstream"]
packages = []

[[layer]]
name = "Domain"
rust = ["contexts/{context}/domain/Cargo.toml"]
references = ["Kernel", "Contracts:own", "Contracts:upstream"]
packages = []

[[layer]]
name = "Codec"
rust = ["contexts/{context}/codec/Cargo.toml"]
references = ["Kernel", "Contracts:own", "Domain:own"]
packages = []

[[layer]]
name = "Application"
rust = ["contexts/{context}/application/Cargo.toml"]
references = ["Kernel", "Infrastructure", "Domain:own", "Codec:own", "Contracts:own", "Contracts:upstream"]
packages = []

[[layer]]
name = "Day"
rust = ["day/Cargo.toml"]
references = ["Application:any", "Contracts:any", "Kernel"]
packages = []

[[layer]]
name = "Infrastructure"
rust = ["infrastructure/{name}/Cargo.toml"]
references = ["Kernel"]
packages = []

[[layer]]
name = "Import"
rust = ["import/Cargo.toml"]
references = ["Kernel", "Contracts:any"]
packages = []

[[layer]]
name = "ImportHost"
rust = ["hosts/import/Cargo.toml"]
references = ["Kernel", "Contracts:any", "Import", "Day"]
packages = []

[[layer]]
name = "Host"
rust = ["hosts/{name}/Cargo.toml"]
references = ["Application:any", "Day", "Infrastructure"]
packages = []

[[layer]]
name = "Breach"
rust = ["breaches/**"]
references = []
packages = []

[[layer]]
name = "Test"
rust = ["tests/{name}/Cargo.toml"]
references = ["*"]
packages = ["*"]
```

A dependency edge alone does not stop the Day from reaching a context's domain, because an application crate could re-export its whole `Domain`. `A1`'s facade half closes that door. In the reference code base, an application crate may expose domain types to the Day only through one listed file (`for_the_day.rs`), and every item in it is a literal re-export of one listed type from the crate's own domain (`pub use <own_domain_crate>::<path>;`, with no renaming, no glob, and no `crate`, `self`, or `super` path). The lists hold only rules sections, workflow inputs, views, and refusals, never an aggregate, its commands, or its events; that a list keeps to these kinds is checked by review at each edit of the list, not by the check. Outside that file an application crate re-exports nothing (one named exception predates the rule) and declares no public type alias; no application crate uses `pub extern crate` or `macro_rules!` (a macro could write a re-export no item shows); and only the Day and the hosts may name the facade module. The check is a flat syntactic rule that resolves no alias (see `F5`). Its decision record also states what it leaves to review: a public signature outside the facade that names a domain type, a domain dependency renamed in a manifest, and whether the facade file is declared from the crate root.

Rule `A10` reads a per-crate allow-list of third-party packages. Every production crate that uses one is listed with exactly the packages it may use, and each package appears under its one wrapper crate. The layer rows' own `packages` field allows packages for a whole layer (the `Test` row allows all). A filled entry looks like this:

```toml
[packages]
# crate = [the third-party packages it may use]; each is its one wrapper, with licence and reason recorded (H4)
orderstore = ["rusqlite"]
```

Rules `A9` and `B22` then check names against committed word lists in the rule table, each with its own scope:

- **`A9`** checks every **type, module, and namespace** in `Domain`, `Contracts`, and `Application` **ports** against `[words].infrastructure` and `[words].screens`.
- **`B22`** checks every **module, file, type, and top-level function** in `Domain`, `Contracts`, and `Application` to ensure it is not named with, and does not end with, a word from `[names].vague` (unless the whole name is a glossary term).

```toml
[words]
infrastructure = ["Sql", "Json", "Http", "Socket", "File", "Db", "Clock", "Thread", "Cache", "Queue"]
screens = ["Button", "Click", "Screen", "Page"]

[names]
vague = ["Entities", "ValueObjects", "Services", "Factories", "Repositories", "Helpers",
         "Utils", "Common", "Shared", "Misc", "Base", "Impl", "Processor", "Handler", "Data", "Info", "Manager"]
```

Under these lists, a domain type named `SqlOrder` fails `A9` (contains `Sql`), while `OrderManager` fails `B22` (ends in `Manager`) and `OrderRepositories` fails `B22` (ends in `Repositories`).

### Example 2: Constrained values and aggregate state transitions (`C3`, `B3`, `B5`, `B7`, `C12`, `G7`, `G11`, `G12`)

Rule `C3` requires simple domain values in `Kernel`, `Domain`, and `Contracts` to hide their representation, expose `create` and `value`, and forbid public constructors. Rule `B3` requires each aggregate in `Domain` to be a pure decider: a total `evolve(state, event) -> State` over the context's own types, an `is_terminal` check for aggregates that can end, and a `decide` function taking the code base's declared leading parameters (`[decider].leading`), a command, and the state to return either a list of events or a refusal. Every public item carries a documentation comment (`G12`), every function takes at most 4 parameters (`G11`), domain constants live in a `rules` module (`G7`), other aggregates are referenced only by typed ID (`B5`), events are past-tense facts carrying the aggregate ID (`B7`), and pattern matches over the code base's own unions use no `_` wildcard arm (`C12`):

```rust
/// Named domain constants for library loan rules (G7).
pub mod rules {
    /// Maximum allowed duration of a single library loan in days.
    pub const MAX_LOAN_DAYS: u16 = 30;
}

/// Validation failure when constructing a [`LoanDays`] value (C1, C3, C11).
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum LoanDaysError {
    /// The requested loan duration was zero days.
    BelowMinimum,
    /// The requested loan duration exceeded [`rules::MAX_LOAN_DAYS`].
    ExceedsMaximum,
}

/// Constrained day count for a library loan, bounded to `1..=MAX_LOAN_DAYS` (C3).
#[derive(Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Debug)]
pub struct LoanDays(u16);

impl LoanDays {
    /// Validates a raw day count and returns a constrained [`LoanDays`] or a [`LoanDaysError`].
    pub fn create(raw: u16) -> Result<Self, LoanDaysError> {
        if raw < 1 {
            Err(LoanDaysError::BelowMinimum)
        } else if raw > rules::MAX_LOAN_DAYS {
            Err(LoanDaysError::ExceedsMaximum)
        } else {
            Ok(Self(raw))
        }
    }

    /// Returns the underlying validated day count.
    pub fn value(self) -> u16 {
        self.0
    }
}

/// Past-tense domain events for a library loan aggregate (B3, B5, B7).
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum LoanEvent {
    /// Recorded when a member checks out a book.
    LoanOpened {
        /// Identifier of the loan aggregate.
        loan_id: LoanId,
        /// Identifier of the borrowed book aggregate.
        book_id: BookId,
        /// Identifier of the borrowing member aggregate.
        member_id: MemberId,
        /// Approved duration of the loan.
        duration: LoanDays,
        /// Domain date on which the loan opened.
        opened_on: Date,
    },
    /// Recorded when an active loan is extended.
    LoanRenewed {
        /// Identifier of the loan aggregate.
        loan_id: LoanId,
        /// Updated duration of the loan.
        duration: LoanDays,
        /// Domain date on which the renewal took effect.
        renewed_on: Date,
    },
    /// Recorded when the borrowed book is returned.
    LoanReturned {
        /// Identifier of the loan aggregate.
        loan_id: LoanId,
        /// Domain date on which the book was returned.
        returned_on: Date,
    },
}

/// Lifecycle state of a library loan aggregate as a sum type (B3, B5, C8).
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum LoanState {
    /// The book is checked out to a member.
    Active {
        /// Identifier of the loan aggregate.
        loan_id: LoanId,
        /// Identifier of the borrowed book aggregate.
        book_id: BookId,
        /// Identifier of the borrowing member aggregate.
        member_id: MemberId,
        /// Approved duration of the loan.
        duration: LoanDays,
        /// Domain date of the most recent checkout or renewal.
        since: Date,
    },
    /// The book has been returned and the loan lifecycle has ended.
    Returned {
        /// Identifier of the loan aggregate.
        loan_id: LoanId,
        /// Domain date on which the loan closed.
        returned_on: Date,
    },
}

/// Folds a [`LoanEvent`] into the current [`LoanState`] with no wildcard arms (B3, C6, C12).
pub fn evolve(state: LoanState, event: LoanEvent) -> LoanState {
    match (state, event) {
        (
            LoanState::Active { .. } | LoanState::Returned { .. },
            LoanEvent::LoanOpened { loan_id, book_id, member_id, duration, opened_on },
        ) => LoanState::Active { loan_id, book_id, member_id, duration, since: opened_on },
        (
            LoanState::Active { book_id, member_id, .. },
            LoanEvent::LoanRenewed { loan_id, duration, renewed_on },
        ) => LoanState::Active { loan_id, book_id, member_id, duration, since: renewed_on },
        (
            LoanState::Returned { loan_id, returned_on },
            LoanEvent::LoanRenewed { .. },
        ) => LoanState::Returned { loan_id, returned_on },
        (
            LoanState::Active { .. } | LoanState::Returned { .. },
            LoanEvent::LoanReturned { loan_id, returned_on },
        ) => LoanState::Returned { loan_id, returned_on },
    }
}

/// Returns `true` once the loan has reached its terminal [`LoanState::Returned`] state (B3, C12).
pub fn is_terminal(state: &LoanState) -> bool {
    match state {
        LoanState::Active { .. } => false,
        LoanState::Returned { .. } => true,
    }
}

/// Commands a member or librarian can send to an existing loan (B3).
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum LoanCommand {
    /// Extend the active loan to a new approved duration.
    Renew {
        /// Requested new duration of the loan.
        duration: LoanDays,
    },
    /// Record that the borrowed book has come back.
    Return,
}

/// Why [`decide`] turned a command down: a refusal, never a silent empty success (B3).
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum LoanRefusal {
    /// The loan has already ended, and an ended aggregate refuses every command.
    LoanEnded,
}

/// Decides what a command does to a loan, with the date as its one leading parameter (B3, C12, G11).
///
/// Every (command, state) pair is listed, with no `_` arm, so a new command or state
/// cannot be forgotten. The business decisions live here and nowhere else.
pub fn decide(
    today: Date,
    command: LoanCommand,
    state: LoanState,
) -> Result<List<LoanEvent>, LoanRefusal> {
    match (command, state) {
        (LoanCommand::Renew { duration }, LoanState::Active { loan_id, .. }) => {
            Ok(List::of(LoanEvent::LoanRenewed { loan_id, duration, renewed_on: today }))
        }
        (LoanCommand::Return, LoanState::Active { loan_id, .. }) => {
            Ok(List::of(LoanEvent::LoanReturned { loan_id, returned_on: today }))
        }
        (LoanCommand::Renew { .. }, LoanState::Returned { .. })
        | (LoanCommand::Return, LoanState::Returned { .. }) => Err(LoanRefusal::LoanEnded),
    }
}
```

The same decider in Java, compiled and tested with property tests, is [`Loan.java`](examples/java-starter/loans/domain/src/main/java/starter/loans/domain/Loan.java) in the Java starter, and its constrained value is [`LoanDays.java`](examples/java-starter/loans/domain/src/main/java/starter/loans/domain/LoanDays.java). In the Rust code, `List` and `List::of` stand for the code base's immutable list type (rule `C2` forbids arrays and other mutable collections in public types); like `Date`, `LoanId`, `BookId` and `MemberId`, they are assumed from the kernel, and every id type and `Date` must be `Copy`. Here each decision yields exactly one event. Opening the loan (the first `LoanOpened`) is a separate create step that is not shown. A returned loan refuses `Renew` and `Return` with `LoanEnded`, which is `B3`'s rule that an ended aggregate refuses every command.

### Example 3: Nullable infrastructure wrappers (`E1`, `E5`)

Rule `E1` bans mocking libraries and external stub types, and rule `E5` requires every `Infrastructure` wrapper to be Nullable (a Java version, with its narrow and Nullable tests, is the starter's [`NoticeLog.java`](examples/java-starter/infrastructure/notices/src/main/java/starter/infrastructure/notices/NoticeLog.java)). Each wrapper keeps its representation private, embeds its stub inside its own file, and exposes both `create` and `create_null` with no I/O on construction, recording writes as typed behaviour values and failing loudly when configured responses run out:

```rust
use std::collections::VecDeque;

/// Recorded output behaviour for a dispatched notice (E5).
#[derive(Clone, PartialEq, Eq, Debug)]
pub enum NoticeSent {
    /// A notice was dispatched to `recipient` with the given text resource `key`.
    Dispatched { recipient: RecipientId, key: TextKey },
}

/// Infrastructure wrapper with an embedded Nullable backend (E1, E5).
pub struct NoticeWrapper {
    inner: Backend,
    recorded: Vec<NoticeSent>,
}

enum Backend {
    Real { endpoint: EndpointConfig },
    Null { canned: VecDeque<Result<(), NoticeError>> },
}

impl NoticeWrapper {
    /// Real factory: stores configuration and connects only on first use (no I/O here).
    pub fn create(endpoint: EndpointConfig) -> Self {
        Self { inner: Backend::Real { endpoint }, recorded: Vec::new() }
    }

    /// Nullable factory: optional non-third-party responses, no I/O.
    pub fn create_null(responses: Option<Vec<Result<(), NoticeError>>>) -> Self {
        let canned = VecDeque::from(responses.unwrap_or_else(|| vec![Ok(())]));
        Self { inner: Backend::Null { canned }, recorded: Vec::new() }
    }

    /// Sends a notice or replays the next configured Nullable response.
    pub fn send(&mut self, recipient: RecipientId, key: TextKey) -> Result<(), NoticeError> {
        match &mut self.inner {
            Backend::Real { endpoint } => endpoint.send_wire(recipient, key)?,
            Backend::Null { canned } => {
                // E5: a configured response list that runs out fails loudly.
                canned.pop_front().expect("NoticeWrapper::create_null responses exhausted")?;
            }
        }
        self.recorded.push(NoticeSent::Dispatched { recipient, key });
        Ok(())
    }

    /// Returns the recorded output behaviours for scenario assertions (E5, E9).
    pub fn outputs(&self) -> &[NoticeSent] {
        &self.recorded
    }
}
```

---

## 4. The rules by group

The 14 groups below are ordered in the adoption sequence from Section 2. **This guide holds 157 rules**, each with its statement: the reference rule table's 155 plus two new guidance rules (`E37`, `S9`). The last column of the table below lists the 32 rules whose statements this guide wrote from the reference's rule-table rows because the reference had none; `D9` among them is optional. A new rule takes the next id after the highest in its group, so that it never reuses a retired id; missing numbers were retired in the reference code base.

| Group | Theme | Rules (157) | Stated by this guide from the reference's rule table (32) |
| :--- | :--- | :--- | :--- |
| **F** | [Compiler hygiene and self-proving rules](#group-f-compiler-hygiene-and-self-proving-rules) | F1–F6 (6) | — (0) |
| **H** | [Supply chain and reproducible builds](#group-h-supply-chain-and-reproducible-builds) | H1, H2, H4–H6, H9 (6) | H9 (1) |
| **I** | [Governance, decision records, and vitality](#group-i-governance-decision-records-and-vitality) | I1–I3, I5, I6, I9 (6) | — (0) |
| **A** | [Architecture, layers, and dependencies](#group-a-architecture-layers-and-dependencies) | A1, A2, A5–A11, A13, A14, A17 (12) | A11, A13 (2) |
| **C** | [Functional core, types, and errors](#group-c-functional-core-types-and-errors) | C1–C15 (15) | — (0) |
| **D** | [Determinism and exactness](#group-d-determinism-and-exactness) | D1–D5, D8–D13 (11) | D9, D10 (2) |
| **G** | [Simplicity and module design](#group-g-simplicity-and-module-design) | G2–G4, G7, G11–G13 (7) | G2 (1) |
| **E** | [Testing and verification](#group-e-testing-and-verification) | E1–E3, E5, E8–E17, E21–E23, E25, E26, E30–E37 (27) | E15, E32, E34, E35 (4) |
| **B** | [Bounded contexts, domain model, and glossary](#group-b-bounded-contexts-domain-model-and-glossary) | B1, B3–B9, B11–B15, B17–B27 (24) | B19–B21, B25 (4) |
| **M** | [Messaging and event envelopes](#group-m-messaging-and-event-envelopes) | M1–M7 (7) | M3–M7 (5) |
| **P** | [Persistence, codecs, and versioning](#group-p-persistence-codecs-and-versioning) | P2–P13, P15 (13) | P4, P5, P7–P10, P13 (7) |
| **R** | [Reliability, error budgets, and postmortems](#group-r-reliability-error-budgets-and-postmortems) | R1–R6, R8 (7) | R2–R4, R6, R8 (5) |
| **S** | [Scope, branches, spikes, and exemplars](#group-s-scope-branches-spikes-and-exemplars) | S1–S5, S7–S9 (8) | S7 (1) |
| **V** | [Blind model-reviewer gate and calibration](#group-v-blind-model-reviewer-gate-and-calibration) | V1–V8 (8) | — (0) |

---

### Group F: Compiler hygiene and self-proving rules

Group F turns compiler warnings, unresolved `TODO` comments, and unapproved edits to principle files into gate failures, and requires every automated rule to prove itself against a planted breach (`F5`).

| Rule | Title | Reference (Rust) status | Cadence | Check and scope in the reference Rust code base |
| :--- | :--- | :--- | :--- | :--- |
| **F1** | Warnings are errors | `built` | `commit` | Enforces warnings-as-errors, pinned formatter and linter configs, overflow checks, and narrowly scoped `[allow]` entries. |
| **F2** | No third-party proprietary material | `partial` | `commit` | History check scans committed files; model review for quoted proprietary words is pending (advisory on Rust). |
| **F3** | TODOs and cited ids must resolve | `built` | `commit` | Architecture check verifies every `TODO` backlog ID and every decision or rule ID cited in comments. |
| **F4** | Principle files only tighten | `built` | `commit` | History check blocks loosening edits to principle files without dated approval and requires decision IDs on structural file edits. |
| **F5** | Every rule proves it works | `built` | `commit` | The architecture meta-test verifies that checks select real code unless dormant and fire on their `breaches/**` sample alone. |
| **F6** | No commented-out code or debug leftovers | `partial (history dormant)` | `commit` | Static checks catch commented-out code and debug leftovers; history checks for flag expiry, dead code, and test-only items are dormant. |

- **F1 — Warnings are errors**: Compiler warnings are errors, with a fixed opt-in list of extra warnings and lints and a pinned language version, all set once centrally; no package suppresses warnings, and a local suppression has an `[allow]` entry and is scoped as narrowly as possible. There is one code formatter with a pinned configuration, a linter whose pinned configuration names every rule kept, and arithmetic overflow checking is on.
- **F2 — No third-party proprietary material**: No third-party proprietary material (quoted text, data files, or pictures from a reference product) is committed; an automated check enforces this. *Pending in Rust*: model review for quoted proprietary words in strings or comments (advisory-only on Rust).
- **F3 — TODOs and cited ids must resolve**: Every `TODO` carries a backlog item ID, and every decision or rule ID named in a code comment exists. *Keep other ids out of the rule-id shape*: in the reference code base the check reads every word-bounded token of a rule group's letter plus one or two digits as a rule id. Any other kind of id with that shape, such as a numbered request or a component, either fails the check (no such rule) or silently cites an unrelated rule. Give such ids a prefix or another form (for example `REQ-7`, `seam-C5`) from the start.
- **F4 — Principle files only tighten**: The principle files (the rule table, the allow list, the reliability targets, and the rubrics) change only by adding or tightening unless the commit cites an explicit dated approval. Review cases, golden files, the simulation corpus, and committed migrations are append-only, and the structural facts (the context map, the glossary, the policy table) change only through reviewed commits that cite a decision record.
- **F5 — Every rule proves it works**: Every rule in the rule table has a check. Every automated rule has a breach example that fires exactly that rule, a selection that is not empty on the real code unless the rule is marked dormant with the backlog item in which its target appears, and a failure message naming the rule ID, the offending edge or member, the allowed alternative, and its decision record.
  - *A planted breach proves the check fires; a pinned count proves it still looks.* A test that compares many items (a differential, recording, or property test) proves nothing by "no disagreement" alone, because a comparison that silently stops comparing also disagrees with nothing. Pin how many items it compared, per kind, and how many it owes (`E37`). A check that is true by construction proves nothing about the code under test: name what it really proves.
  - *Flat syntactic rules beat clever resolution.* A check that tries to resolve aliases, renames, or import paths invites one bypass per case it fails to resolve. In the reference code base, the first facade check of `A1` matched re-exports by resolving aliases, and three blind reviews in a row found ways around it (the third found six more: renames through `self::`, `crate::` and `super::`, a renamed facade, a two-hop rename). It was replaced by a flat rule that resolves nothing and simply forbids every form that could hide a re-export, with a planted breach for each bypass found. Prefer forbidding a form outright to proving it harmless.
- **F6 — No commented-out code or debug leftovers**: There is no commented-out code or debug leftover, no always-off flag without an expiry in the flag list, and code a change makes dead is deleted in the same commit; a public member that only tests use is reported. *Pending in Rust*: history checks for flag expiry in the flag list, deleting dead code in the same commit, and reporting public members used only by tests are dormant until the target files land.

---

### Group H: Supply chain and reproducible builds

Group H pins the compiler toolchain and package lockfiles on every commit, enforces a 14-day minimum age on newly adopted versions, and runs vulnerability and reproducible-build checks every night.

| Rule | Title | Reference (Rust) status | Cadence | Check and scope in the reference Rust code base |
| :--- | :--- | :--- | :--- | :--- |
| **H1** | The toolchain is pinned exactly | `built` | `commit` | Verifies the toolchain version is pinned exactly and never floats. |
| **H2** | Packages and tools pinned exactly | `built` | `commit` | Verifies exact central dependency pins, committed lockfiles, and locked-mode restore. |
| **H4** | A minimum age for new versions | `partial` | `commit` | Supply-chain check verifies lockfile and version-table publish dates (`>= 14` days); the rule table marks the check partial and records no Rust-specific pending part. |
| **H5** | A nightly package audit | `built` | `nightly` | Supply-chain check audits direct and transitive dependencies and fails on high or critical advisories. |
| **H6** | Builds are deterministic and reproducible | `built` | `nightly` | Supply-chain check runs two clean release builds at different paths and compares SHA-256 hashes. |
| **H9** | Releases | `planned` | `on-demand` | Planned release-time supply-chain check. |

- **H1 — The toolchain is pinned exactly**: The language toolchain is pinned to an exact version, never a floating channel or automatic upgrade, and is upgraded only deliberately under the package age rule (`H4`).
- **H2 — Packages and tools pinned exactly**: Every dependency, direct and transitive, is pinned to an exact version, managed centrally with no per-package overrides and no ranges or wildcards; dependency-resolution warnings are errors; lock files are committed and the gate restores in locked mode; and build tools and linters are pinned to the same toolchain version.
- **H4 — A minimum age for new versions**: Every pinned version was published at least 14 days before the commit that adopts it, and each new package is named in its commit message with its licence and its reason: what it makes deployable, testable, or faster, and that module boundaries stay intact. *Partly built*: the rule table marks the check partial and records no Rust-specific pending part.
- **H5 — A nightly package audit**: The package audit covers all packages, transitive ones included, and high and critical advisories are errors in a separate nightly audit run, so that a new advisory does not break unrelated commits.
- **H6 — Builds are deterministic and reproducible**: The gate builds in continuous-integration mode, and in the nightly run two clean builds from a fresh clone, with the network off after restore, produce identical SHA-256 hashes for every build artefact.
- **H9 — Releases**: A release is cut only from a commit with a recorded green full gate. It carries a software bill of materials beside the build hashes of every shipped artefact, and a record of the pinned versions and the changes since the last release. A committed release checklist holds questions with actions, and its automated items must pass. *Reference status*: planned; a release script checks the automated part (`Q`) and the checklist's questions are judged (`L`); run at release (`on-demand`).

---

### Group I: Governance, decision records, and vitality

Group I links the rule table and structural commits to decision records, checks for stale allow entries, and schedules an attended monthly vitality review.

| Rule | Title | Reference (Rust) status | Cadence | Check and scope in the reference Rust code base |
| :--- | :--- | :--- | :--- | :--- |
| **I1** | Every rule states what it serves | `built (unverified in sources)` | `commit` | Marked `built` in the rule table, but the table names neither a check runner nor any checking tool. |
| **I2** | Decision records are linted | `built` | `commit` | History check lints decision record sections, `Compliance` rule IDs, `revisit when` triggers, and symmetric supersede links. |
| **I3** | Structural commits cite a decision record | `built` | `commit` | History check verifies that commits touching the context map, dependencies, `Contracts` surfaces, or the rule table cite a decision record. |
| **I5** | Stale allow entries fail | `built (dormant)` | `commit` | History check is written, but dormant until the central allow-list reader is enabled; does not fail commits while dormant. |
| **I6** | Checks are not fooled by inlining | `built (unverified in sources)` | `commit` | Holds by construction in Rust (syntax-tree and linter checks inspect source before inlining); no check runner or checking tool in the rule table. |
| **I9** | A monthly vitality review | `review (attended process)` | `on-demand` | Attended monthly review and scratch self-test drill; no automated check. |

- **I1 — Every rule states what it serves**: Every row of the rule table names the quality characteristics it serves, its cadence (`commit`, `nightly`, `on-demand`), its scope (`atomic` or `holistic`), and its decision record or principle family. *Rust status*: marked `built` in the rule table, but unverified in the sources (it names neither a check runner nor a checking tool).
- **I2 — Decision records are linted**: Decision records have the required sections, including `Compliance` and a `revisit when` trigger; `Compliance` names existing rule IDs or a manual check with a reason and review date; a superseded record hosts no active rule; supersede links are symmetric; and each allow entry names the record it relaxes.
- **I3 — Structural commits cite a decision record**: A commit that changes the context map, any package dependency, the public surface of a `Contracts` package, or the rule table cites a decision record ID in its message.
- **I5 — Stale allow entries fail**: An allow entry that no longer excuses any violation fails the gate, and an optional review-by date fails once it lapses. *Dormant in Rust*: the history check is dormant until the central allow-list reader lands.
- **I6 — Checks are not fooled by inlining**: A check that inspects compiled output runs on builds with cross-module inlining disabled and is written at type level, or else inspects source before any inlining happens. Each such check's breach example includes a small inlinable callee, and a meta-test fails if this protection is lost. *Rust status*: the rule table notes that this rule holds by construction in Rust because Clippy and syntax-tree checks inspect source before inlining, and names no check runner or checking tool for it.
- **I9 — A monthly vitality review**: A dated vitality review takes place monthly in an attended session: the rules, allow entries, decision-record review dates, and health trends are read; obsolete rules are proposed for removal; the gate's self-test plants a forbidden dependency in a scratch copy and expects a failure; and a commit 30 days old is built. *Rust status*: attended process (`review`), with no automated check.

---

### Group A: Architecture, layers, and dependencies

Group A enforces the workspace's hexagonal and bounded-context package structure, bans dependency cycles and technology terms in core names, restricts each third-party library to a single wrapper crate, and keeps the application shell thin. Rules `A5` and `A7` apply when reimplementing a reference program.

| Rule | Title | Reference (Rust) status | Cadence | Check and scope in the reference Rust code base |
| :--- | :--- | :--- | :--- | :--- |
| **A1** | The layer table governs every dependency | `built` | `commit` | Matches every `Cargo.toml` path and dependency edge against `[[layer]]` in the rule table; a flat facade check limits what application crates re-export to the `Day`. |
| **A2** | Contexts see each other only through contracts | `built` | `commit` | Checks `Domain` crate dependencies and imports so contexts share only `Kernel` and upstream `Contracts`. |
| **A5** | Production code never depends on the reference program | `built` | `commit` | Verifies no production crate depends on paths outside the workspace or on the reference program. |
| **A6** | No dependency cycles | `built` | `commit` | Checks that there are no dependency cycles between packages. |
| **A7** | Only the import layer knows the legacy format | `built (Q; L advisory)` | `commit` | Architecture check scans names and hex constants outside `Import`; model-review (`L`) half is advisory on Rust. |
| **A8** | Codecs are pure and the store keeps envelopes | `built` | `commit` | Verifies `Codec` purity and consumers and checks that `Infrastructure` public types expose only opaque envelopes. |
| **A9** | Names say purpose, not technology | `built` | `commit` | Scans `Domain`, `Contracts`, and `Application` port names against `[words]` and checks port error unions. |
| **A10** | One wrapper per third-party package | `built` | `commit` | Checks `Cargo.toml` dependencies against `[packages]` and `[banned].containers`. |
| **A11** | Storage is partitioned like the contexts | `dormant` | `commit` | Dormant check on `Infrastructure`. |
| **A13** | Plug-ins are independent | `dormant` | `commit` | Dormant until the first plug-in exists. |
| **A14** | The application layer is partitioned by context | `built` | `commit` | Verifies one `Application` crate per context with no cross-context `Application` or `Domain` edges, plus one cross-context composition crate (`Day`). |
| **A17** | The shell stays humble | `partial` | `commit` | Architecture check enforces complexity (`<= 5`) and bans domain branching in `Application`, `Day`, and `Hosts`; invariant check is advisory (`L`). |

- **A1 — The layer table governs every dependency**: Every package in the workspace has a row in the layer table (layer, context, role), and its dependencies on other packages are exactly those its row allows: `Kernel` depends on nothing but the language's core library; `Contracts` on `Kernel` and upstream `Contracts`; a `Codec` on `Kernel` and its own context's `Contracts` and `Domain`; `Infrastructure` on `Kernel` only; each context's `Application` on `Kernel`, `Infrastructure`, and its own context's `Domain`, `Codec`, and `Contracts` plus upstream `Contracts`; the composition package (`Day`) on any `Application`, any `Contracts`, and `Kernel`; `Import` (when reimplementing a reference program) on `Kernel` and any `Contracts`, and its host on those, `Import`, and `Day`; `Hosts` on `Application`, `Day`, and `Infrastructure`. Nothing depends on a `Host` or a `Test` package, and an unclassified package fails the check. *The facade half*: an `Application` package exposes its domain's types to the composition package only through one listed facade file of literal, listed re-exports (rules sections, workflow inputs, views, refusals; never an aggregate, its commands, or its events); it re-exports nothing elsewhere and has no construct that could hide a re-export, and only the composition package and the hosts name the facade (Example 1).
- **A2 — Contexts see each other only through contracts**: A context sees another context only through that context's published `Contracts`, so each keeps its own model and language. A context's `Domain` depends only on `Kernel`, its own `Contracts`, and the `Contracts` of contexts upstream of it, never on another context's `Domain`.
- **A5 — Production code never depends on the reference program**: No production package depends on code outside the workspace, and in particular not on the reference program the system is checked against; only the dedicated exact-comparison test package may depend on that reference.
- **A6 — No dependency cycles**: There are no dependency cycles between packages. Where the language allows cycles inside one package (between namespaces, or mutually recursive modules, types, or functions), they need an `[allow]` entry, so that declaration order is dependency order.
- **A7 — Only the import layer knows the legacy format**: Only the `Import` layer knows a legacy external format: its file layouts, offsets, and addresses. Elsewhere no declared name is a decompiler's default name or speaks of pointers, no integer is named as an offset or address, and the domain holds no address-shaped constants such as long hexadecimal literals. *Advisory in Rust*: the `L` model-reviewer component of this `Q+L` rule is advisory-only on Rust paths.
- **A8 — Codecs are pure and the store keeps envelopes**: A `Codec` does no I/O and only `Application` and `Test` packages depend on it. `Infrastructure`'s public types name no `Domain`, `Contracts`, `Codec`, or third-party type; the store keeps opaque envelopes of kind, version, and payload.
- **A9 — Names say purpose, not technology**: No type, module, or namespace in `Domain`, `Contracts`, or `Application` ports contains a word from the listed infrastructure terms (`Sql`, `Json`, `Http`, `Socket`, `File`, `Db`, `Clock`, `Thread`, `Cache`, `Queue`) or user-interface terms (`Button`, `Click`, `Screen`, `Page`). A port's error is a closed union owned by the `Application` layer that names no storage type.
- **A10 — One wrapper per third-party package**: Each third-party package is used by at most one production package, its `Infrastructure` wrapper, apart from the core library and a short allow-list; every package's third-party dependencies are named in its allow-list (`[packages]`), and networking, storage, and serialisation packages appear only in their wrapper and the `Hosts`. There is no dependency-injection container (`[banned].containers`): the `Hosts` wire the system by passing functions.
- **A11 — Storage is partitioned like the contexts**: Query text and opening the store happen only in the store's `Infrastructure` wrapper. Every table name starts with its context's prefix, and no statement or migration joins or keys across two contexts' tables, except the shared journal. *Reference status*: dormant until the store lands; a static scan, plus a narrow test for migrations.
- **A13 — Plug-ins are independent**: Each plug-in package (rule sets, text packs, engine mappings) depends only on `Kernel` and its own contract, and no plug-in depends on another. The plug-in registry is total: every plug-in is registered, and every registry key is backed by a package. *Reference status*: dormant until the first plug-in exists; a package-graph check plus a registry test.
- **A14 — The application layer is partitioned by context**: There is one `Application` package per context, which never depends on another context's `Application` or `Domain`, plus at most one thin composition package (`Day`, the single cross-context orchestration crate) that schedules work across the context applications and is the only application package spanning contexts. The check reads the package dependency graph, which compiler optimisation cannot hide.
- **A17 — The shell stays humble**: Every function in the `Application` packages, the composition package (`Day`), and the `Hosts` has a complexity of at most 5 (cyclomatic, or the stricter cognitive measure where that is what the tooling offers), branches on no domain type or case, and checks no aggregate invariant, which belongs with its aggregate. A function that needs more is split into a domain decision, tested by its output, and a thin workflow. *Pending in Rust*: checking that no aggregate invariant is evaluated in the shell relies on model review (`L`, advisory-only on Rust).

---

### Group C: Functional core, types, and errors

Group C keeps `Kernel`, `Domain`, and `Contracts` functional: errors are values of four typed kinds, state is immutable and null-free, primitives are wrapped in constrained types, functions are total, lifecycles are sum types, and panic handlers live only at the edges.

| Rule | Title | Reference (Rust) status | Cadence | Check and scope in the reference Rust code base |
| :--- | :--- | :--- | :--- | :--- |
| **C1** | Errors are values | `built` | `commit` | Bans panics and throws in `Kernel`, `Domain`, and `Application` and checks typed error and defect unions. |
| **C2** | No mutation in the core | `built` | `commit` | Bans mutable bindings, mutable fields, interior mutability, and mutable collections in `Kernel` and `Domain`. |
| **C3** | Simple values are constrained types | `partial` | `commit` | Checks private fields, `create`, `value`, and `[allow].struct_constrained`; boundary property check per type is pending. |
| **C4** | No primitive obsession | `partial` | `commit` | Rejects raw primitives in `Domain` and `Contracts` public signatures and fields; glossary `carries:` and counts/dates checks are pending. |
| **C5** | No null in the core | `built` | `commit` | Bans null literals, null checks, nullable pointers, and default-initialised values in `Kernel` and `Domain`. |
| **C6** | Functions are total | `partial` | `commit` | Architecture check bans listed partial methods, non-exhaustive matches, non-constant divisors, and unchecked overflow; unlisted partial calls rely on review (`L`). |
| **C7** | Workflows are pipelines | `built (dormant)` | `commit` | Signature check on `Application` workflows is written, but dormant until the application workflows exist. |
| **C8** | States are types | `built (partly report-only)` | `commit` | Reports for review (without failing the gate) any `Domain` or `Contracts` record with `is`/`has`/`can` boolean fields, 2+ optional fields, or optional collections. |
| **C9** | No squashed errors | `partial` | `commit` | Architecture check makes discarding a `Result` an error; turning a refusal or wrapper error into a default value is a review finding (`L`). |
| **C10** | Each core unit declares its public surface explicitly | `built` | `commit` | Enforces explicit exports, no wildcard re-exports, abstract invariant-carrying types, and decision citations on `Contracts` changes. |
| **C11** | Typed errors of four kinds | `built` | `commit` | Rejects string or exception errors and nested `Result<Result<...>>` in public signatures and enforces the four error kinds. |
| **C12** | No wildcard cases over the system's own unions | `built` | `commit` | Architecture check bans `_` or catch-all bindings over workspace enums in `Kernel`, `Domain`, and `Application` unless in `[allow].wildcard`. |
| **C13** | Dependencies are rejected, not injected | `built` | `commit` | Rejects function parameters returning async results or `()` and public functions returning `()` in `Kernel` and `Domain`. |
| **C14** | Exception handlers only at the edges | `partial` | `commit` | Restricts `catch_unwind` to `Infrastructure`, `Hosts`, and `Codec`; full error-coverage and logged-then-defaulted payloads rely on review (`L`). |
| **C15** | No integer-valued enumerations in the domain | `built` | `commit` | Rejects numeric enum discriminants and integer `#[repr(...)]` on enums in `Domain` and `Contracts`. |

- **C1 — Errors are values**: `Kernel`, `Domain`, and `Application` never throw, raise, or panic. Each context has a typed error union, and a broken invariant or unsupported case the core detects is returned as a typed defect case without free text, which `Application` carries to the `Host`, which stops with a bug report and the journal slice (the recent sequence of recorded events leading up to the failure).
- **C2 — No mutation in the core**: `Kernel` and `Domain` use no mutation: no mutable bindings or fields, no reference cells, and no arrays or other mutable collections in public types. They use immutable lists, maps, sets, or other immutable collections.
  - *Fast code under "no mutation"*: copy-per-step accumulation (rebuilding a list or map on every step of a fold) is quadratic, and it hides well in small tests. In the reference code base, an aggregate that rebuilt its whole results map for every recorded result spent 40% of a recording test's instructions on that copy. Prefer a fixed-size structure made once (one entry per known item, replaced in place through a map-and-collect over the value the function owns) or one linear pass. When optimising, keep the old algorithm in the tests as a reference model and compare the two over seeded cases: a few chosen seeds that reach every outcome in the fast tier, broad seeds in the nightly run. Then kill a handful of hand mutants of the new code (`E14`).
- **C3 — Simple values are constrained types**: Simple domain types in `Kernel`, `Domain`, and `Contracts` have a hidden representation, a `create` from the raw value to either the type or a typed error, a `value` accessor, and no public constructor. Each has a boundary property showing that `create` accepts exactly its legal values, and a constrained type with a default zero value needs an `[allow].struct_constrained` entry stating why that value is legal. *Pending in Rust*: verifying that each constrained type has its boundary property test (waits for the domain context crates).
- **C4 — No primitive obsession**: Public functions and record fields in `Domain` and `Contracts` use domain types, not raw integers, strings, decimals, or booleans (an alias of a primitive counts as the primitive; `[primitives].allowed` is empty), and each constrained type's glossary entry names the invariant, unit, or identity it carries (`carries:`). Counts are not storage (no fixed-size allocations or capacity constants; limits are rules), and dates use `Kernel`'s date type, never integer arithmetic or packed bytes. *Pending in Rust*: glossary `carries:` annotations and the counts and dates checks (wait for glossary edits, domain context crates, and advisory model review).
- **C5 — No null in the core**: `Kernel` and `Domain` use no null: no null literals, null checks, types that admit null, or default-initialised values.
- **C6 — Functions are total**: Functions in `Kernel` and `Domain` are total: no partial operations such as taking the head of a list, unwrapping an optional value, find-or-throw lookups, or unchecked indexing, and an incomplete set of pattern cases is a compile error. Division is permitted only by non-zero named constants from the context's rules module, and arithmetic overflow is checked rather than silently wrapping. *Pending in Rust*: known partial standard-library methods are matched by name from a static list; unlisted partial operations rely on model review (`L`, advisory-only on Rust).
- **C7 — Workflows are pipelines**: Each `Application` workflow is a pipeline from a command to an asynchronous result of either events or a workflow error: impure load, one pure decision over values, impure store. A decision that needs data read in the middle becomes several such sandwiches, the core never receives an effectful function, and the pipeline composes modules that own their knowledge rather than one module per step. *Dormant in Rust*: the check is built but dormant until the `Application` workflow modules exist.
- **C8 — States are types**: A lifecycle is a union of states (offered, signed, expired), not flags or optional fields that must agree. A check **reports for review** any `Domain` or `Contracts` record with an `is`, `has`, or `can` boolean field, two or more optional fields, or an optional collection (unless excused in `[allow].states_as_flags`). *Partly report-only*: the automated check flags matching records for review rather than failing the gate outright.
- **C9 — No squashed errors**: Discarding a result value is a compile-time error, and turning a refusal or a wrapper error into a default value **is a review finding**. *Pending in Rust*: turning a refusal or wrapper error into a default value is assigned to model review (`L`, advisory-only on Rust); `drop` and `forget` are permitted in `Infrastructure` and `Hosts` where real resource guards live.
- **C10 — Each core unit declares its public surface explicitly**: Every `Kernel`, `Domain`, and `Contracts` unit states explicitly what it publishes: through a separate interface declaration where the language has one, otherwise through private-by-default items and an explicit export list with no wholesale re-exports of modules. A type that carries an invariant is abstract, and a change to a `Contracts` interface cites a decision record.
- **C11 — Typed errors of four kinds**: No public signature in `Kernel`, `Domain`, `Contracts`, or `Application` uses a string or exception as its error type or nests one result inside another. Errors come in four kinds: a domain refusal is `decide`'s error; an infrastructure failure is a wrapper error carried in the workflow's error union; undecodable input is the `Codec`'s decode error, quarantined at the edge and never reaching the domain; and a defect is the typed defect case.
- **C12 — No wildcard cases over the system's own unions**: In `Kernel`, `Domain`, and `Application`, no wildcard or catch-all pattern covers the cases of a union defined in the code base, except by `[allow].wildcard` entry.
- **C13 — Dependencies are rejected, not injected**: In `Kernel` and `Domain`, no parameter has a function type returning an asynchronous result or nothing (unit or void), and no public function returns nothing.
- **C14 — Exception handlers only at the edges**: Exception or panic handlers exist only in `Infrastructure`, `Hosts`, and `Codec`; each names the failures it expects, apart from the host's single top-level handler. A wrapper translates every failure into its own closed error union at its boundary, and no handler is empty or turns a caught failure into a value. *Pending in Rust*: verifying that a wrapper's error enum covers every failure reported by the wrapped API, and catching handlers that use an error payload (such as logging it) before returning a fallback value, rely on model review (`L`, advisory-only on Rust).
- **C15 — No integer-valued enumerations in the domain**: `Domain` and `Contracts` contain no integer-valued enumerations: no case carries an explicit numeric discriminant or integer representation. Categories are sum types of plain cases and flag sets are sets of cases; integer tags appear only as wire tags in `Codec` and the `Import` layer.

---

### Group D: Determinism and exactness

Group D makes `Kernel` and `Domain` bit-for-bit reproducible: no I/O or ambient state, one seeded random service, no floats, widened integer wrappers for reference-matching arithmetic (`D4`, when reimplementing a reference program), no user-facing strings or special human-user types in the core, deterministic ordered collections, and three distinct time types.

| Rule | Title | Reference (Rust) status | Cadence | Check and scope in the reference Rust code base |
| :--- | :--- | :--- | :--- | :--- |
| **D1** | No I/O or ambient state in the core | `built` | `commit` | Bans I/O, clock, environment, process, thread, lock, ID generation, non-const statics, and locale APIs in `Kernel` and `Domain`. |
| **D2** | Randomness only through one seeded service | `built` | `commit` | Bans external random generators and hash-derived randomness in `Kernel` and `Domain`. |
| **D3** | No floating point in the core | `built` | `commit` | Bans `f32` and `f64` types and floating-point operations in `Kernel` and `Domain`. |
| **D4** | Exact arithmetic through kernel integer wrappers | `partial (Q+L; L advisory)` | `commit` | Rejects raw narrow integers and conversions outside `Kernel` and checks wrapper properties; type-inferred `try_into()` into a wrapper is advisory (`L`). |
| **D5** | No user-facing text in the core | `built` | `commit` | Rejects string literals in `Kernel`, `Domain`, and `Contracts` unless listed as keys in `[text].keys`. |
| **D8** | No special human user in the core | `review (advisory on Rust)` | `commit` | Assigned to model review (`L`, advisory-only on Rust). |
| **D9** | No engine-specific attributes in the core entity | `dormant` | `commit` | Dormant check on domain contexts. |
| **D10** | Every component has its contract page and reads only its declared inputs | `dormant` | `commit` | Dormant check on `Domain`. |
| **D11** | Projections only read | `built` | `commit` | Checks that projections and read views in `Contracts` and `Application` draw no randomness and mutate no state. |
| **D12** | No hash-ordered collections in the core | `built` | `commit` | Bans `HashMap`, `HashSet`, and hash-derived ordering or keys in `Kernel` and `Domain`. |
| **D13** | Three distinct time types | `partial` | `commit` | Rejects wall-clock `Instant` in `Domain` and `Application` signatures; splitting `Instant` and `Elapsed` in the `Clock` wrapper is pending. |

- **D1 — No I/O or ambient state in the core**: `Kernel` and `Domain` perform no I/O and use no clock, environment, process, console, thread, lock, generated identifier, global mutable state, or non-constant module-level value, and no culture-sensitive parsing, formatting, or comparison.
- **D2 — Randomness only through one seeded service**: `Kernel` and `Domain` draw randomness only through `Kernel`'s single seeded random service: no other generator, and no hashing used as a random source outside `Kernel`'s keyed draws.
- **D3 — No floating point in the core**: `Kernel` and `Domain` use no floating-point types or floating-point operations.
- **D4 — Exact arithmetic through kernel integer wrappers**: When the system must reproduce a reference program exactly, `Kernel` and `Domain` arithmetic goes through `Kernel` integer wrappers that widen to the reference's working width before every operation and truncate only on store, with the reference's signedness and division rounding. Each wrapper operation has a property proving it equals the widened reference computation, and raw numeric conversions appear only in `Kernel`. *Pending in Rust*: because the syntax-tree check (`syn`) has no type inference, a narrow value converted via a type-inferred trait call (`I16::create(x.try_into()?)`) is checked by model review (`L`, advisory-only on Rust).
- **D5 — No user-facing text in the core**: `Kernel`, `Domain`, and `Contracts` contain no user-facing text: string literals appear only as listed identifiers and keys (`[text].keys`), and words come from text resources at the edge.
- **D8 — No special human user in the core**: `Domain` has no special notion of "the human user": it models actors and roles only, and no type is named for the user. *Advisory in Rust*: marked `review` for Rust (`L`, advisory-only on Rust).
- **D9 — No engine-specific attributes in the core entity** *(optional: state it before adopting)*: When several interchangeable engines (plug-ins that compute behaviour over the same core entity) read a core entity, the entity holds no attribute that only one engine uses. Each engine derives its own profile from the core entity through its own mapping, an anticorruption layer that the engine owns. *Reference status*: dormant, `Q+L`. The reference states this rule in one line of a design checklist and records no check design, so an adopter should define "engine" and the mapping's place before relying on it.
- **D10 — Every component has its contract page and reads only its declared inputs**: When the core is cut into components for exact comparison with a reference program, each component has a contract page listing its inputs, its outputs, the random draws it consumes, and the reference routines it covers, so that nothing falls between two components. A component reads only the inputs its page declares. *Reference status*: dormant; a test-inventory check that each page exists (`Q`) and review of the reads (`L`). Applies when reimplementing a reference program.
- **D11 — Projections only read**: Projections and read views draw no randomness and change no state.
- **D12 — No hash-ordered collections in the core**: `Kernel` and `Domain` use no hash-ordered collections or hash-derived data: no hash maps or hash sets, and no hash code used for an order, a key, or a random draw. They use ordered maps and sets (`BTreeMap`, `BTreeSet`).
- **D13 — Three distinct time types**: The core reads only `Kernel`'s domain `Date`; the `Clock` wrapper provides a wall-clock `Instant`, for metadata only, and a monotonic `Elapsed` time, for timeouts and time budgets. No `Domain` or `Application` signature takes a wall-clock `Instant`. *Pending in Rust*: reworking the `Clock` wrapper in `Infrastructure` to expose wall-clock `Instant` and monotonic `Elapsed` as distinct types is pending.

---

### Group G: Simplicity and module design

Group G enforces function complexity and size limits, bans unnamed magic numbers in the domain, restricts parameter lists and tuples in public signatures, requires documentation comments on every public core item, and defines a design rubric for module depth. The ported-routine clauses of `G3`, `G4`, and `G7` apply when reimplementing a reference program.

| Rule | Title | Reference (Rust) status | Cadence | Check and scope in the reference Rust code base |
| :--- | :--- | :--- | :--- | :--- |
| **G2** | The structure report | `report` | `nightly` | Nightly holistic structure report; never gates commits. |
| **G3** | A complexity ceiling | `built` | `commit` | Architecture check enforces function complexity (`<= 15`, or `<= 5` in `Application` and `Hosts`) and nesting depth (`<= 4`). |
| **G4** | Size backstops, never targets | `built (partly report-only)` | `commit` | Architecture check enforces 80 lines/function, 500/module, and 600/file, and reports smaller indicators (lambda/tuple/condition/member/test size) without gating. |
| **G7** | No magic numbers in the domain | `built` | `commit` | Architecture check rejects unnamed numeric literals other than `0`, `1`, and `-1` in `Domain` outside a `rules` module. |
| **G11** | Named shapes, not positions | `built` | `commit` | Rejects >4 parameters or 3+ same-primitive parameters on public functions, plus tuples and boolean parameters in public signatures. |
| **G12** | Every public member is documented | `built` | `commit` | Verifies that every public item in `Kernel`, `Domain`, and `Contracts` has a doc comment. |
| **G13** | A design rubric of complexity red flags | `review (advisory on Rust)` | `commit` | Evaluated by the model reviewer (`L`, advisory-only on Rust). |

- **G2 — The structure report**: A nightly report shows, as trends, each package's instability and distance from the main sequence (with abstractness defined by role; `Kernel` and `Contracts` excluded), each module's cohesion (whether its public functions form one group linked by shared domain types), duplicated token runs, commits that touch more than three contexts, and the small size indicators of `G4`. A metric becomes a ratchet only through a decision record, after about a month of data has set its floor. *Reference status*: a report; it never gates a commit.
- **G3 — A complexity ceiling**: Each function has a complexity of at most 15 (the `Application` and `Host` layers have a lower ceiling of 5 under `A17`) and a nesting depth of at most 4. Routines ported from a reference program may exceed a limit only through a scoped allowance whose count can only fall.
- **G4 — Size backstops, never targets**: At most 80 lines per function or member, 500 per module, and 600 per file, with ported reference routines handled as for the complexity ceiling. Smaller backstops (lambda length, tuple size, boolean operators per condition, members per type, test function length) **are reported, not gated**. *Partly report-only*: the `80` / `500` / `600` line limits gate commits, whereas the smaller backstops (lambda length, tuple size, boolean operators per condition, members per type, and test function length) only report.
- **G7 — No magic numbers in the domain**: `Domain` contains no unnamed numeric literal other than `0`, `1`, and `-1`; constants live in a named `rules` module, with ported reference routines handled as for the complexity ceiling.
- **G11 — Named shapes, not positions**: No public function of a production package takes more than 4 parameters (records bundle anything more) or 3 or more parameters of the same primitive type, smart constructors excepted for the latter. The public signatures of `Domain`, `Contracts`, and `Application` use no tuples and no boolean parameters, and `Infrastructure`'s public signatures use no tuples; tuples remain fine inside functions.
- **G12 — Every public member is documented**: Every public member of `Kernel`, `Domain`, and `Contracts` has a documentation comment, placed on its declaration, or on its separate interface declaration where the language has one.
- **G13 — A design rubric of complexity red flags**: A design rubric holds the standard complexity red flags, each with a one-line test and caveats, plus two further items: the pass-through workflow (an `Application` workflow that only forwards its arguments, a concern when more than a fifth—20%—of a context's workflows do so) and complexity added for speed without a cited measurement. Advisory automated helpers feed the rubric but never block on their own. *Advisory in Rust*: evaluated by the model reviewer (`L`, advisory-only on Rust).

---

### Group E: Testing and verification

Group E replaces mocking libraries with Nullable wrappers, bans test hooks in production code, enforces per-tier speed budgets and counting assertions, requires red-before-green and untouched tests in refactors, and checks aggregate properties, wrapper contract suites, and deterministic replay. Comparing tests pin how much they compared (`E37`, new guidance). Rules `E15` and `E25`'s exact-comparison clause, and the subsection on recordings at the end of this group, apply when reimplementing a reference program.

| Rule | Title | Reference (Rust) status | Cadence | Check and scope in the reference Rust code base |
| :--- | :--- | :--- | :--- | :--- |
| **E1** | No test doubles except Nullables | `partial` | `commit` | Bans mocking crates (a list in the architecture check's code), external stubs, and direct wrapper construction; checking trait objects standing in for ports is pending. |
| **E2** | Test through the public interface, with no hooks | `built` | `commit` | Bans `#[cfg(test)]` hooks, exposed internals, test-only methods, and private reflection in production crates. |
| **E3** | Each test package has one kind | `partial` | `commit` | Checks test-crate dependencies by tier; running scenario tests through `Application` with Nullable edges waits for scenario crates. |
| **E5** | Every wrapper and service is Nullable | `partial` | `commit` | Checks `create` and `create_null` signatures; checking no-I/O factories, trackers, `simulate`, exhausted-response panics, and `Application` services is pending. |
| **E8** | Speed budgets and a wall-time ratchet | `partial` | `commit` | Inventory check enforces test and gate wall-time budgets and the 10% / 20-run ratchet; narrow-test count reporting per wrapper is pending. |
| **E9** | Interactions checked only at the unmanaged edge | `partial (dormant)` | `commit` | Static check is dormant until writer wrappers exist; checking the unmanaged-dependency table and reviewing literal/golden expectations (`L`) are pending. |
| **E10** | Properties per aggregate, of the right kinds | `partial` | `commit` | Architecture check reads each aggregate's property list by name; generator spread, seed handling, snapshot prefixes, and `L` review of the property kinds are pending. |
| **E11** | Tests read as behaviour, one act each | `built (Q; L advisory)` | `commit` | Architecture check enforces naming rules and single-act calls (`decide`/`handle`/`run`); `L` review of domain phrasing is advisory on Rust. |
| **E12** | Red before green | `partial (replay dormant)` | `commit` | History check is wired, but parent-commit test replay is dormant until exit-code `101` crash-vs-assertion distinction lands. |
| **E13** | Tests are independent of order, time and repetition | `partial` | `commit` | Architecture check bans mutable statics (write-once caches excepted) in tests and `Application`, bans retries, and caps sequential tests; nightly shuffle/parallel comparison and retry-loop review (`L`) are pending. |
| **E14** | Coverage and defect counts are tripwires, not targets | `partial` | `commit` | Static check exists; pinning the Rust branch-coverage ratchet, health-report co-display, and prompt/report review (`L`) are pending. Hand mutants per branch are process. |
| **E15** | Exact comparison against the reference program | `dormant` | `commit` | Dormant exact-comparison test gate. |
| **E16** | Test data comes from builders | `partial (Q+L; L advisory)` | `commit` | Rejects raw `Domain` struct literals in tests (allowing 1-field newtypes and `..builder()`); `Kernel`/`Contracts` structs and `L` naming review are pending. |
| **E17** | One contract test list per wrapper, run on both forms | `partial` | `commit` | Checks that a wrapper's contract list runs on both factories; checking 1 wrapper per narrow test, tier separation, and matching test names is pending. |
| **E21** | Each wrapper declares its failure modes | `partial` | `commit` | Checks that each wrapper's operations return its own error enum and that each failure mode has a test reaching `create_null`; hang timeouts, mode-from-options wiring, health-report counts, and notice review (`L`) are pending. |
| **E22** | Every test asserts something | `built` | `commit` | Architecture check requires house counting-assertion macros, bans native `assert!`, flags tautologies, and rejects ignored or focused tests. |
| **E23** | Refactoring commits leave tests alone | `built` | `commit` | History check rejects refactoring commits that edit lines inside test bodies (beyond compiler-forced renames). |
| **E25** | No test leaks the algorithm | `partial (Q+L; L advisory)` | `commit` | Architecture check catches identical free-function calls on both sides of an assertion; local bindings, operator chains, and helpers rely on review (`L`). |
| **E26** | A small smoke tier | `partial (dormant)` | `commit` | Dormant until hosts and wrappers exist; 10 s gate routing, call-path tracing from smoke tests to `create`, and narrow-test pairing review (`L`) are pending. |
| **E30** | Quarantine is recorded data | `partial (dormant)` | `commit` | History check on 7-day quarantine allow entries is dormant; feeding quarantined counts into `R1`'s flake indicator is pending. |
| **E31** | The same inputs give the same state | `partial` | `nightly` | Enforces a static proxy banning hash-ordered collections in `Application`, `Day`, and `Codec` and custom hashers in tests; live state-hash and cross-OS replay are pending. |
| **E32** | Many-seed simulation with invariants and reached checks | `dormant` | `nightly` | Dormant nightly simulation rule. |
| **E33** | Hygiene for snapshot tests | `partial` | `commit` | History check blocks `*.received.*` files and unapproved snapshots; testing the scrubber under two clocks waits for the first approval test. |
| **E34** | Mutation ratchet | `planned` | `nightly` | Planned nightly mutation-testing ratchet on `Kernel` and `Domain`. |
| **E35** | Store narrow tests assert behaviour through read-back | `dormant` | `commit` | Dormant narrow-test check on `Infrastructure`. |
| **E36** | One shared, language-neutral behaviour suite | `built` | `commit` | Inventory check fails the gate on any case file of `spec/` (scenarios, and recordings' `cases/*.jsonl` only) that no test named `spec/<case>` runs. |
| **E37** | Comparing tests pin what they compared | `process (new guidance)` | `commit` | New guidance; no rule-table row or Rust check. The reference code base's recording tests pin their counts by convention. |

- **E1 — No test doubles except Nullables**: Tests use no test doubles except Nullable infrastructure: no mocking library anywhere (`[banned].mocking`), no test-defined implementation of an `Infrastructure` or `Application` contract, and no stub, fake, or mock type outside `Infrastructure`, where an embedded stub lives in its wrapper's own file. Wrappers cannot be built from outside their own package except through their factories, and ports can be built only from wrappers. *Rust status*: the rule table's `[banned].mocking` list names only .NET mocking libraries, kept from the code base's earlier track; the Rust mocking crates (`mockall` and similar) are listed in the architecture check's own code, which refuses both lists. *Pending in Rust*: verifying that a trait object is not passed to stand in for a port.
- **E2 — Test through the public interface, with no hooks**: Tests go through the public interface and production code has no test hooks: no exposing internals to test packages, no reflection into private members, no conditional compilation for tests (`#[cfg(test)]`), no members named for tests, and no test flags. The Nullable factory is the one test seam.
- **E3 — Each test package has one kind**: A context's logic tests use only that context, `Kernel`, upstream `Contracts`, the test-data builders, and the test libraries, and test by output; scenario tests run through the `Application` layer with Nullable edges; and only the narrow integration test package touches real I/O, real wrappers, the network, processes, or the clock. *Pending in Rust*: running scenario test packages through `Application` with Nullable edges waits for the scenario test crates (the rule table marks the rule `built` alongside this pending part, so this guide shows `E3` as `partial`).
- **E5 — Every wrapper and service is Nullable**: Every `Infrastructure` wrapper, and every `Application` service with infrastructure below it, exposes a real factory (`create`) and a Nullable factory (`create_null`) with only optional parameters, none of a third-party type, and neither factory does I/O (connecting happens on first use). Writers record their outputs as named behaviour types, never strings or primitives, and reads are not recorded; event sources expose simulation through the same handler as the real callback; and a configured response list that runs out fails loudly. *Pending in Rust*: verifying no I/O in factories, output trackers with named behaviour types, event simulation methods, loud failure when configured responses run out, and `Application` service factory pairs (waits for the first writer wrappers and application services).
- **E8 — Speed budgets and a wall-time ratchet**: A logic test takes at most 20 ms, a scenario test at most 200 ms, the quick inner-loop check under 15 s, the full gate under 2 minutes, and the architecture tests under 10 s; anything slower is tagged for the nightly run, and an untagged slow test fails the gate. The wall time of the gate, the quick check, and each step may not grow more than 10% over the median of its last 20 green runs without a recorded reason, and the gate prints its 10 slowest tests and each step's time against its budget. *Pending in Rust*: counting and reporting narrow integration tests per wrapper (waits for the first narrow wrapper tests).
  - *Budgets per test and per stage* (built in the reference code base: the inventory check judges each test against its tier, and the pipeline judges each stage). Give each test tier a per-test budget (in the reference code base: logic 20 ms, scenario 200 ms, narrow integration 200 ms, exact comparison 200 ms per test; the architecture tests 10 s in total) and each gate stage (format, build, lint, test, breaches) a wall-time budget. Keep separate stage tables for a cold-cache and a warm-cache run, locally and on CI, so that a cold run neither fails against the warm medians nor loosens them. These per-test numbers are the rule's; the stage budgets are the reference code base's, measured on its Rust stack. Measure your own stack on the developer machine and on CI, cold and warm, before you enforce any stage budget, and record each number with where it came from in your copy of [templates/budgets.toml](templates/budgets.toml).
  - *Leave headroom for the CI runner.* In the reference code base a shared CI runner was about 2.5 to 4 times slower than the developer container: a test stage took 2.6 s locally and 6.3 to 6.5 s on the runner, and property tests that took about 5 to 7 ms locally hit the 20 ms budget there. Your ratio depends on your stack and your runner, so measure it; whatever it is, a test near its budget locally will fail on CI.
  - *Fix slowness by profiling.* Never by cutting cases, raising a per-test budget, or moving a test to another tier. In the reference code base the property tests that hit the logic budget on the runner were each profiled and made faster (a quadratic duplicate check, dates computed by a fold instead of a closed form, a sort that copied too much), with no case cut and no budget raised. Splitting one test into two with the same total cases is fine. Reducing a nightly run's breadth is the one exception, and only with evidence: in the reference code base a nightly property run went from 2,000 to 480 seeded cases after its hand mutants were re-run, and the one mutant only the larger run had killed got a chosen case of its own in the fast tier.
  - *Change a stage budget only with recorded evidence*: the runs measured, the cause, and a decision record. The budget breach that proves the stage check fires stays unchanged.
  - *Count timing re-runs.* A gate that is red only because of timing may be re-run once (the reference code base's landing script does this when every red is a timing breach), but a red run followed by a green one on the same commit is what the flaky-gate indicator (`R1`) counts. Count such a re-run as a flaky run, or record explicitly that it is not counted and why; the reference code base has not yet recorded either choice.
- **E9 — Interactions checked only at the unmanaged edge**: Recorded outputs are asserted only in scenario tests and only for outputs to systems outside the application's control, listed in a dependency table, and are compared as a whole list against literals or golden files, never partially and never against the production encoder's output. A managed store is checked by reading its state back, and domain tests are output-based. *Pending in Rust*: dormant until writer wrappers exist; checking the dependency table for unmanaged status and model review (`L`, advisory on Rust) that expected lists are literals or golden files rather than encoder output are pending.
- **E10 — Properties per aggregate, of the right kinds**: Each aggregate's property list includes at least: reachable states keep their invariants; `decide` is deterministic and a refusal changes nothing; `evolve` is total; a terminal state accepts no command; one model-based or metamorphic property (a round trip alone does not count); one stateful property over random command sequences; and, where snapshots are kept, snapshot equals replay at every prefix. Generators are tested to produce only valid values, the gate runs a fixed seed and the nightly run fresh seeds, and a failure's seed and shrunk case become a named example test. *Pending in Rust*: the architecture check reads each aggregate's property list by the tests' names; generator spread reports, fixed/fresh seed handling with a failing seed kept as a named example, snapshot-prefix properties (no context keeps snapshots yet), and model review (`L`, advisory on Rust) that the properties are of the kinds their names claim are pending.
- **E11 — Tests read as behaviour, one act each**: Test names describe, in the domain's language, what a user would notice, with no `method_scenario_result` pattern, no leading `should`, and no public function name of the code under test. Each test checks one behaviour with a single call to a `decide`, `handle`, or `run` entry point, smoke tests excepted; branching inside a test body is allowed for table-driven tests. *Advisory in Rust*: the `L` model-reviewer component of this `Q+L` rule is advisory-only on Rust paths.
- **E12 — Red before green**: For a feature or fix commit that changes `Kernel`, `Domain`, or `Application` behaviour, its new or changed tests, run against the parent commit's production code, must fail by an assertion. If they only fail to compile, or do not fail at all, the commit is blocked unless review accepts a reason. *Dormant in Rust*: parent-commit replay is dormant because an unhandled panic or abort in `cargo test` exits with code `101` just like a failed assertion; distinguishing a crash from a house-macro assertion failure is pending.
- **E13 — Tests are independent of order, time and repetition**: There is no static or module-level mutable state in test code or an `Application` package; in test code a write-once cache (in Rust a `OnceLock` or `LazyLock` that every test reads with the same value) is allowed, while the reference code base's linter configuration bans even those in core and application crates (`D1`). Tests forced to run sequentially carry a comment giving the reason and are capped in number. The nightly run executes the suite in random order with a recorded seed, both sequentially and in parallel; an outcome that differs on the same commit is a flaky test that blocks the next merge, and retries are forbidden everywhere. *Pending in Rust*: nightly random-order execution and sequential-versus-parallel comparison for Rust crates, plus model review (`L`, advisory on Rust) for hand-written retry loops around test acts.
- **E14 — Coverage and defect counts are tripwires, not targets**: The core's branch coverage, recorded per commit with one pinned tool version, may not drop, and the report lists its uncovered branches. Neither coverage nor a defect count is a target in any rubric, report, or agent prompt, and coverage is always reported beside the assertion-free and red-before-green results and, once mutation testing exists, the surviving-mutant count.
  - *Hand mutants on every branch* (process; new guidance). Until a mutation-testing ratchet runs (`E34`, planned), the builder and then the reviewer each make a handful of hand mutants of the new code (an off-by-one, a dropped branch, a swapped comparison, a step skipped) and run the tests against each. A surviving mutant is either killed by a new or sharper test, or argued equivalent in the review. In the reference code base this was routine: blind reviews found branches where 10 of 13 or 10 of 14 mutants survived the new tests, and the builders' commits report their mutant counts (for example "19 hand mutants, all killed"). *Pending in Rust*: pinning the Rust coverage tool and branch ratchet, co-reporting in the health report, and model review (`L`, advisory on Rust) of prompts and session reports.
- **E15 — Exact comparison against the reference program**: When the system must reproduce a reference program, each component's recordings and differential properties pass, and a component counts as exact only when the completeness bar is met: the reference program's branch coverage of that component's routines, over the recorded cases, reaches every reachable branch, with each exception listed with a reason. The gate runs the recordings and a few hundred differential properties for each touched component; the nightly run covers every component. *Reference status*: dormant until the exact-comparison harness exists (in the meantime the shared suite's recording tests, `E36` and `E37`, hold the components already compared).
- **E16 — Test data comes from builders**: Tests build their data only through test-data builders that read in the domain's language, never through literal domain records spread across many tests. *Pending in Rust*: extending the literal check from `Domain` structs to `Kernel` and `Contracts` structs (1-field tuple structs and struct-update syntax `Record { field, ..builder() }` are permitted by design), and model review (`L`, advisory on Rust) that builders read in the domain's language.
- **E17 — One contract test list per wrapper, run on both forms**: Each wrapper with a direct third-party dependency has one contract test list, written as a function of a factory, that runs against the real factory (`create`) in the narrow integration tests and against the Nullable factory (`create_null`) in the fast tier, covering ordering, edge cases, and its declared failure modes. Each narrow test exercises one wrapper, and an `Application` service composed of wrappers relies on its wrappers' lists. *Pending in Rust*: the architecture check requires each such wrapper's contract list and its runs with both factories; checking that each narrow test exercises a single wrapper, enforcing fast-versus-narrow tier placement, and verifying identical test names across both runs are pending.
- **E21 — Each wrapper declares its failure modes**: Each wrapper declares its external failure modes (for example a full disk, a locked file, a closed socket, a slow answer, a hang, or a corrupt stored record). Each mode is a configurable response of its Nullable form with one test, a hang being timed out on monotonic elapsed time, and each becomes a typed wrapper error and a user notice by text key, and is counted in the health report. *Pending in Rust*: the architecture check requires typed wrapper errors and one test per declared mode reaching `create_null`; hang-timeout modes, verifying that tests configure modes via `create_null` options, counting modes in the health report, and model review (`L`, advisory on Rust) of user notice delivery are pending.
- **E22 — Every test asserts something**: Every test executes at least one assertion through the code base's counting assertion module; no test calls the test framework's assertion functions directly, tautological assertions **are flagged**, and the main branch holds no focused or pending tests, which the gate detects and fails on.
- **E23 — Refactoring commits leave tests alone**: A refactoring commit changes no line inside a test body other than renames the compiler forces, and keeps the gate green.
- **E25 — No test leaks the algorithm**: An expected value is never computed by the production function or the same chain of operations over the same inputs. Collaborator-based isolation is forbidden in the exact-comparison tests and in `Kernel` arithmetic tests. *Pending in Rust*: the static check catches identical free-function calls on both sides of a single assertion; expected values bound to a local variable first, computed via operator or iterator chains, or built via test helpers rely on model review (`L`, advisory-only on Rust).
- **E26 — A small smoke tier**: There are 1 to 3 smoke tests (the server host starts, runs one simulation step, and answers on its socket; every host builds) that together reach every real wrapper's real factory (`create`). They run in the gate if they finish within 10 s, otherwise in the nightly run and before each release, and a smoke failure is fixed together with the narrow test that would have caught it. *Pending in Rust*: dormant until hosts and wrappers exist; 10 s gate routing, tracing the full call path from smoke tests to each wrapper's `create`, and model review (`L`, advisory on Rust) that smoke fixes include a narrow test are pending.
- **E30 — Quarantine is recorded data**: A flaky test may be quarantined only by an allow entry giving the reason, a dated approval from the engineering decision-maker, and an expiry of at most 7 days. An expired entry fails the gate, and quarantined tests are listed in the report and counted in the flaky-gate indicator. *Dormant in Rust*: the history check is dormant, and feeding the quarantined test count into `R1`'s flaky-gate indicator is pending.
- **E31 — The same inputs give the same state**: The same seed and commands produce the same state hash twice in the gate, including when independent work of one step is processed in two different orders. In the nightly run a golden journal replays to the same snapshot bytes in two fresh processes, one single-threaded and one multi-threaded, and on a second operating system. *Pending in Rust*: enforces a static proxy banning hash-ordered collections in `Application`, `Day` (the cross-context composition crate), and `Codec` crates and custom hashers in tests; live state-hash determinism tests and multi-process/second-OS nightly replay wait for the domain contexts.
- **E32 — Many-seed simulation with invariants and reached checks**: A nightly run plays many seeded simulations (1,000 to start) with Nullable edges and faults injected by seeded draws. Each run picks its own fault rates and set of enabled fault points (swarm testing) and is checked against a listed invariant set, and the scenario suite runs once against the real store. Each listed rare state and fault must be reached at least once a night; a failing run is appended to a protected corpus as its commit and command list, and the gate replays the corpus. *Reference status*: dormant; nightly.
- **E33 — Hygiene for snapshot tests**: Approval or snapshot files are used only for rendered or serialised output, never for a rule with a known value or in place of exact-comparison tests. No `received` files (`*.received.*`) are committed, nothing is approved automatically on a developer machine, and the scrubber is tested under two different clocks. *Pending in Rust*: testing the output scrubber under two clocks waits for the first approval test.
- **E34 — Mutation ratchet**: A nightly mutation run over `Kernel` and `Domain` counts surviving mutants. The count may not rise, no work item adds a new survivor on its changed lines, and each equivalent mutant is recorded as an allow entry with a reason. The count is a ratchet, never a target score. *Reference status*: planned, after a mutation-tool spike; until then, hand mutants per branch (`E14`).
- **E35 — Store narrow tests assert behaviour through read-back**: Each narrow test of the store uses a fresh database, cleaned in its arrange step, and writes and reads back through separate store instances. No test asserts on query text or on the last statement sent, and no second database engine stands in for the real one anywhere. *Reference status*: dormant until the store lands; static checks plus review.
- **E36 — One shared, language-neutral behaviour suite**: Scenario cases and reference recordings are written once as language-neutral data files in a shared behaviour suite (`spec/`). When the system is implemented in more than one language, each implementation's gate runs the whole shared suite, and a case that an implementation's runner skips fails that gate. *Rust status*: built. The Rust spec runner turns each case file of the suite (each scenario file, and each recording's `cases/*.jsonl` file, not its README, manifest, or tables) into exactly one test named `spec/<case>`, and the inventory check fails the gate on any case file that no test of that name runs.
- **E37 — Comparing tests pin what they compared** *(new guidance; no Rust check)*: A test that compares many items (a differential test against a reference, a test over recordings, a property test against a model) asserts exactly how many items it compared, per kind of comparison, and how many items of each kind it owes (recorded by the reference but not yet modelled), and how many cases it ran under each label. Any change in a count, a missing kind, or an unknown kind fails the test, as does any disagreement. "No disagreement" alone passes when the comparison silently stops, so it proves nothing on its own. A check that holds by construction (for example one that re-derives what it compares from the same input) is documented as such, with what it really proves. *In the reference code base*: each recording test pins one line of named counts per case file (`cases: proven`, `compared: <check>`, `owed: <item>`), and a planted fault (one recorded outcome swapped for another) fails 66 of the 91 recording tests.

#### Differential testing against a reference program's recordings

When a system must reproduce a reference program (`E15`, `D4`, `E25`), recordings of the reference program's behaviour become the shared suite's cases (`E36`). The reference code base's practice:

- **One test per case file**, named after it, so the inventory can prove every file runs (`E36`), and every count pinned per file (`E37`).
- **Proven or provisional.** Each case is labelled proven only when its recording was checked by coverage against a real reference run, otherwise provisional, and each test pins its label counts. Beware coverage exemptions: a rule such as "memory the recorder never wrote reads as zero" can make "proven" meaningless, because it hides the very reads a faulty recorder would get wrong. In the reference code base such an exemption was found unsound in review, and the affected cases are described as "labelled proven, pending a re-check", never as proven.
- **Large files are split only by a recorded rule**, written in the splitting tool, with a check that the pieces concatenate back to the recorder's file byte for byte (and a hash taken before and after).
- **Recorder faults are findings.** When the comparison shows the recorder wrote something wrong (in the reference code base, a field never written and a table taken after the change instead of before), report it to whoever owns the recorder and get it re-recorded. Do not adapt the runner around it silently; a temporary adaptation is acceptable only when it is named and counted as owed work, and it is removed when the fixed recording arrives.
- **Every check is proved by a planted data change**: change one recorded value (or one line of the system under test), see the right test fail with the case, the point, and both values, then restore it. Record the planted changes in the commit.
- **Keep the oracle independent of the builder's model.** When the builder writes both the code and the reference model it is tested against, a misreading of the reference program appears in both and the test agrees with itself. In the reference code base a builder's reference model shared its misreading of a date-scheduling routine; the reviewer's dates, taken from the reference program, became the test oracle instead.

---

### Group B: Bounded contexts, domain model, and glossary

Group B ties the crate graph to the declared context map and the ubiquitous-language glossary, enforces the functional aggregate decider pattern (`decide` / `evolve` / `invariants`), and commits public API surface files.

| Rule | Title | Reference (Rust) status | Cadence | Check and scope in the reference Rust code base |
| :--- | :--- | :--- | :--- | :--- |
| **B1** | The dependency graph equals the context map | `built` | `commit` | Verifies that crate dependencies match declared context-map relations and directions (`[vocabulary].relation_kind`). |
| **B3** | Each aggregate is a decider | `built` | `commit` | Verifies `evolve`, `decide` (with `[decider].leading` parameters), refusal results, and `is_terminal` signatures. |
| **B4** | The ubiquitous language is the glossary | `built (partly report-only)` | `commit` | Checks `Domain` and `Contracts` public names against the glossary and checks translations; unused glossary entries are reported only. |
| **B5** | Aggregates refer to each other by identifier | `built` | `commit` | Verifies aggregate root markers and ensures aggregate states hold other aggregates only by typed ID. |
| **B6** | One decision changes one aggregate | `built` | `commit` | Checks that every event an aggregate root's `decide` returns carries that root's ID type exactly once; naming other roots' IDs is allowed. |
| **B7** | Domain events are past-tense facts | `built` | `commit` | Verifies events end in `-ed` or match `[names].past_irregular`, carry their aggregate ID, and hold only IDs and values. |
| **B8** | Stores exist only for aggregate roots | `built` | `commit` | Verifies stores exist only for aggregate roots in `Infrastructure` and store ports are function types in `Application`. |
| **B9** | Value objects have structural equality | `partial` | `commit` | Bans reference equality and checks `[allow].custom_equality`; checking property-test laws for custom equality is pending. |
| **B11** | The model speaks the language of the business | `review (advisory on Rust)` | `commit` | Evaluated by the model reviewer (`L`, advisory-only on Rust). |
| **B12** | Several aggregates in one entry only by name | `built (Q; L advisory)` | `commit` | Checks scheduled jobs, registered policies, and `[allow].same_entry`; `L` review component is advisory on Rust. |
| **B13** | Each context declares its kind and style | `built` | `commit` | Checks context-map fields against `[vocabulary]` and verifies code matches (`transaction-script` vs `domain-model`/`event-sourced`). |
| **B14** | Generic code knows nothing of the core | `partial` | `commit` | Checks that `Kernel` and `generic` contexts depend on no `core` or `supporting` context and use no `core` glossary terms; the rule table does not record what part remains pending. |
| **B15** | Public events are distinct from internal events | `built` | `commit` | Verifies `Contracts` events are distinct from `Domain` internal events and checks the total mapping with no wildcard arm. |
| **B17** | Upstream contracts are named only by inbound translation | `built` | `commit` | Verifies that only a downstream context's `Inbound` module names upstream `Contracts` types (unless `conformist`). |
| **B18** | Each aggregate exposes its invariants | `partial` | `commit` | Checks each aggregate root's `invariants` signature; running it after every scenario and simulation step is pending. |
| **B19** | Create and reconstitute are separate | `dormant` | `commit` | Dormant check on `Domain`, `Application`, and `Import`. |
| **B20** | Every cross-aggregate reaction is listed in the policy table | `dormant` | `commit` | Dormant check on `Application` and the policy table. |
| **B21** | Process manager shape and attribute | `dormant` | `commit` | Dormant check on process managers. |
| **B22** | No pattern-named or vague names | `built` | `commit` | Rejects modules, files, types, and top-level functions in `Domain`, `Contracts`, and `Application` matching `[names].vague`. |
| **B23** | The public surface is committed | `partial` | `commit` | Compares `Kernel` and `Contracts` public APIs against committed surface files; version notes, boundary tests, and port surfaces are pending. |
| **B24** | The kernel is a shared kernel | `built (partly report-only)` | `commit` | History check runs the full gate when `Kernel` changes and flags `Kernel` public-surface diffs for review (report-only). |
| **B25** | Aggregate size | `planned` | `nightly` | Planned nightly report on aggregate size. |
| **B26** | A short, approved product vision | `partial` | `commit` | History check verifies the product vision document's word count (~400 words), banned tech words, and decision citations; feeding it to `L` review is pending. |
| **B27** | A stricter gate for the core | `partial (dormant)` | `commit` | History check is dormant until core contexts exist; exact-comparison inventory (`E15`), coverage ratchet, and mutant review (`L`) are pending. |

- **B1 — The dependency graph equals the context map**: The package dependency graph equals the declared context map: every dependency between contexts is a declared relation in the declared direction, every declared relation exists in the code, and a `separate-ways` relation has no edge at all. Each relation's kind comes from a fixed list (`published-language`, `open-host-service`, `customer-supplier`, `conformist`, `anticorruption-layer`, `shared-kernel`, `partnership`, `separate-ways`).
- **B3 — Each aggregate is a decider**: Each aggregate is a decider: a total `evolve` from state and event to state over the context's own types, and a `decide` from a closed list of leading parameters (`[decider].leading`), a command, and a state to either a list of events or a refusal. Business decisions live only in `decide`, an invalid transition is a refusal rather than a silent empty success, and an aggregate that can end exposes `is_terminal` and refuses every command once it has ended.
- **B4 — The ubiquitous language is the glossary**: Every public type, union case, and record field of a `Domain` or `Contracts` package is a term in that context's section of the glossary, apart from generic helpers allowed by pattern (`[glossary].generic`). Each term appears once per section with a one-line meaning, a name public in two contexts is marked in the later one as a deliberate translation, and an entry that no code uses **is reported**. *Partly report-only*: missing glossary terms and unmarked cross-context translations fail the gate, whereas unused glossary entries are reported without failing the gate.
- **B5 — Aggregates refer to each other by identifier**: Aggregates are explicitly marked as aggregate roots, and an aggregate's state refers to another aggregate only by its typed identifier, never by holding its state or a collection of its states.
- **B6 — One decision changes one aggregate**: Every event a single `decide` returns carries its own aggregate's identifier exactly once. Naming other aggregates' identifiers is fine (Example 2's `LoanOpened` names the book and the member): naming is not changing.
- **B7 — Domain events are past-tense facts**: Domain events, public and private, are named in the past tense (ending in `-ed` or listed in `[names].past_irregular`), carry the identifier of their aggregate, and contain only identifiers and values, never another aggregate's state.
- **B8 — Stores exist only for aggregate roots**: Stores exist only for aggregate roots and only in `Infrastructure`, apart from the single event store per stored instance, which holds its journal, snapshots, and archive. Store ports are function types declared in the `Application` layer.
- **B9 — Value objects have structural equality**: Value objects have structural equality and reference equality is forbidden. A type with custom equality, ordering, or hashing needs an `[allow].custom_equality` entry and properties proving the laws (reflexive, symmetric, transitive, hash agrees with equality, ordering agrees with equality). *Pending in Rust*: verifying the property-test suite of laws for custom-equality types (waits for the domain context crates).
- **B11 — The model speaks the language of the business**: Names, modules, and workflows in `Domain` read as the business does, not as storage structures or a legacy program's memory layout. *Advisory in Rust*: evaluated by the model reviewer (`L`, advisory-only on Rust).
- **B12 — Several aggregates in one entry only by name**: Within one journal entry, events of more than one aggregate come only from scheduled jobs or from policies registered with same-entry consistency; any other case needs an `[allow].same_entry` entry that gives a reason. *Advisory in Rust*: the `L` model-reviewer component of this `Q+L` rule is advisory-only on Rust paths.
- **B13 — Each context declares its kind and style**: Each context declares its subdomain class (`core`, `supporting`, `generic`), its logic style (`transaction-script`, `active-record`, `domain-model`, `event-sourced`), its architecture style (`layered`, `ports-and-adapters`, `cqrs`), and its status (`implemented`, `sample`, `planned`), from listed vocabularies (`[vocabulary]`). The code agrees with the declaration: a `transaction-script` context has no aggregate roots, and a `domain-model` or `event-sourced` context has `decide` and `evolve` per aggregate.
- **B14 — Generic code knows nothing of the core**: A `generic` context and `Kernel` depend on no `core` or `supporting` context, and `Kernel`'s public names contain no term from a `core` context's glossary. *Pending in Rust*: marked `partial` in the rule table; the rule table does not record what part remains pending.
- **B15 — Public events are distinct from internal events**: A context's public events, published in its `Contracts`, are types distinct from the internal events its `evolve` folds, and its `Domain` has a total mapping from each internal event to a list of public events with no wildcard case.
- **B17 — Upstream contracts are named only by inbound translation**: Only a downstream context's designated `Inbound` translation module may name an upstream context's `Contracts` types, unless the context map declares the relation `conformist`. That module translates upstream messages into the downstream context's own commands.
- **B18 — Each aggregate exposes its invariants**: Each aggregate exposes an `invariants` function from state to a list of breaches, empty when valid. Scenario tests and simulation runs check it after every step, and any breach is a defect that stops the run with the relevant slice of the journal. *Pending in Rust*: the signature check is live; checking invariants after every scenario and nightly simulation step is pending.
- **B19 — Create and reconstitute are separate**: No `Domain` or `Application` name says `getOrCreate`, `findOrCreate`, or `ensure…`. A new aggregate's identifier is taken by `Application` from a counter and passed in its command, so `decide` generates none. When reimplementing a reference program, `Import` reconstitutes aggregates with findings (repaired, rejected), never invents identifiers or history, and records an imported aggregate's first event as an import. *Reference status*: dormant; a name check plus review.
- **B20 — Every cross-aggregate reaction is listed in the policy table**: Every reaction of one aggregate to another's event is listed in a committed policy table with what it reacts to, what it changes, its consistency (`same-entry` or later), whose job it is, its delay, and what happens on failure. Every `Application` policy is listed, every listed policy exists, and each policy is safe to run twice, which a property with a duplicated event shows. *Reference status*: dormant; whose job a reaction is is judged by review.
- **B21 — Process manager shape and attribute**: A process manager is marked as one and has the shape *state → input → reaction*, where the reaction is a named record of the next state and the commands to send (no tuple, `G11`). It has its own identifier, holds aggregates by identifier only, reads time only from its inputs, and its property list includes "duplicates change nothing" and "a finished process is terminal". *Reference status*: dormant until the first process manager exists.
- **B22 — No pattern-named or vague names**: No module, file, type, or top-level function in `Domain`, `Contracts`, or `Application` is named with, or ends in, a pattern or vague word from a listed set (`[names].vague`: `Entities`, `ValueObjects`, `Services`, `Factories`, `Repositories`, `Helpers`, `Utils`, `Common`, `Shared`, `Misc`, `Base`, `Impl`, `Processor`, `Handler`, `Data`, `Info`, `Manager`), unless the whole name is a glossary term.
- **B23 — The public surface is committed**: The public surface of `Kernel`, each `Contracts` package, and each port module is written to a committed surface file, and the gate fails on any difference not committed with it. A `Contracts` surface change comes with a version note and a boundary-test change in the same commit, and each context-map edge has its list of boundary tests. *Pending in Rust*: requiring a version note and boundary-test change alongside a `Contracts` surface change, and checking port-module surfaces (waits for context port modules and boundary tests).
- **B24 — The kernel is a shared kernel**: A commit that touches `Kernel` runs the whole gate rather than the quick check, and any change to `Kernel`'s public surface **is flagged for review**. *Partly report-only*: running the full gate on `Kernel` edits is enforced, while `Kernel` public-surface diffs are flagged for review rather than failing the gate on their own.
- **B25 — Aggregate size**: An aggregate's state holds at most a set number of collection-typed fields (`[structure].max_aggregate_collections`), and its property list has a size-budget property: over a long simulated run, its collections stay within the domain's own limits. It starts as a nightly report, not a gate, because a size threshold rewards splitting a true invariant across aggregates; a decision record promotes it. *Reference status*: planned, as a report.
- **B26 — A short, approved product vision**: An approved product vision document exists, is under about 400 words, names no technology from a listed set, and is part of the reviewer's input. It changes only through a decision record; agents may only propose edits to it. *Pending in Rust*: including the vision document in the model reviewer's prompt (`L`, advisory-only on Rust).
- **B27 — A stricter gate for the core**: Core contexts have a stricter gate: each aggregate's property list exists before its first merge, each core component has its exact-comparison tests before its first merge, the core's own branch-coverage ratchet may not fall, and, once mutation testing exists, surviving mutants on a core merge's changed lines are reviewed before it merges. *Pending in Rust*: dormant until core contexts exist; exact-comparison inventory (`E15`), the core branch-coverage ratchet, and surviving-mutant review (`L`) are pending (dropping an existing aggregate's property tests is refused by `E10`'s syntax-tree check).

---

### Group M: Messaging and event envelopes

Group M requires every `Contracts` type to be marked as a `Command`, `Event`, or `View`, and gives every journaled event a deterministic causal envelope. Five in-process messaging rules (`M3`–`M7`: one dispatcher, persist before publish, a handler reads the view as of the event, no dead letters, one writer) are dormant in the reference code base until its journal lands.

| Rule | Title | Reference (Rust) status | Cadence | Check and scope in the reference Rust code base |
| :--- | :--- | :--- | :--- | :--- |
| **M1** | Every contracts type is one message kind | `built` | `commit` | Verifies every `Contracts` type is marked as `Command`, `Event`, or `View`, has no behaviour methods, and never stores commands as events. |
| **M2** | Every journaled event has a causal envelope | `built (dormant)` | `commit` | Check for deterministic `correlation_id`, `causation_id`, and `version` envelope fields is built, but dormant until journal code lands. |
| **M3** | Single declared in-process event dispatcher | `dormant` | `commit` | Dormant check on `Application`. |
| **M4** | Persist before publish | `dormant` | `commit` | Dormant check on `Application` and `Infrastructure`. |
| **M5** | Handler reads publisher's view as of the event's sequence number | `dormant` | `commit` | Dormant `Q+L` check on `Contracts` and `Application`. |
| **M6** | No dead letter in the core | `dormant` | `commit` | Dormant check on `Application` and `Infrastructure`. |
| **M7** | One writer per stored instance | `dormant` | `commit` | Dormant `Q+L` check on `Application`, `Hosts`, and `Infrastructure`. |

- **M1 — Every contracts type is one message kind**: Every `Contracts` type is exactly one marked message kind: a `Command` (an intent that may be refused), an `Event` (a fact), or a `View` (a snapshot to read). `Contracts` types carry no behaviour, and commands are never stored as events.
- **M2 — Every journaled event has a causal envelope**: Every journaled event has an envelope with a `correlation_id` (the command or scheduled job that started the chain), a `causation_id` (the command or event that caused it), and a schema `version`, all deterministic. The sequence number is the journal's own, never an ID or a business key. *Dormant in Rust*: the check is built but dormant until the journal envelope types and store land.
- **M3 — Single declared in-process event dispatcher**: Contexts exchange events through one in-process dispatcher, whose subscriber table is a single declared, ordered list in `Application`. Handler order is fixed by that list, so any random draws the handlers make happen in a fixed order, and there is no reflection discovery, container scanning, or asynchronous fan-out. *Reference status*: dormant.
- **M4 — Persist before publish**: An event reaches another context or a host only after the journal entry that holds it has committed (`P7`). *Reference status*: dormant; a narrow test forces a failure on the second write, and a scenario test shows nothing was published.
- **M5 — Handler reads publisher's view as of the event's sequence number**: A handler that needs more than the event reads the publisher's view as of the event's sequence number, never the current view, and no view-reading port is named `current…`. This is `P11`'s rule for `evolve`, applied to handlers. *Reference status*: dormant, `Q+L`.
- **M6 — No dead letter in the core**: A handler that cannot process a committed event stops the run with a diagnosable error, never parking the event aside. Projections and outbound pushes keep a checkpoint and are idempotent. *Reference status*: dormant; a scenario with a poisoned handler, plus a check that each projection has a checkpoint.
- **M7 — One writer per stored instance**: All commands for one stored instance, timers and background work included, pass through one ordered queue, with no competing consumers. The store holds an exclusive lock on its file, and a second writer process is refused. *Reference status*: dormant, `Q+L`; a concurrency scenario and a narrow test that a second writer is refused.

---

### Group P: Persistence, codecs, and versioning

Group P isolates persistence formats inside pure `Codec` crates as plain data-transfer objects, requires version headers and upcasting decoders backed by property tests and immutable golden files, enforces edge-generated command IDs and optimistic-concurrency sequence numbers (`BasedOn`, the journal event sequence number a command was computed against), and checks union shapes against a committed, append-only shape file.

| Rule | Title | Reference (Rust) status | Cadence | Check and scope in the reference Rust code base |
| :--- | :--- | :--- | :--- | :--- |
| **P2** | Persisted forms are plain data-transfer objects | `partial` | `commit` | Checks `Codec` DTO field shapes, bans serialisation attributes in core crates, and checks smart-constructor decoding; field-tag table check is pending. |
| **P3** | Persisted forms are versioned and proved | `partial` | `commit` | Checks format/version headers and typed `encode`/`decode` signatures; round-trip/never-fails properties, golden files, and decoder table checks are pending. |
| **P4** | Golden stored instances | `planned` | `on-demand` | Planned release check that every golden stored instance still opens to its state hash. |
| **P5** | The journal is append-only and gap-free per branch | `dormant` | `commit` | Dormant store check. |
| **P6** | Command ids are made at the edge | `partial (dormant)` | `commit` | Type check for edge-only `CommandId` is dormant until command envelopes land; store unique-key and duplicate-command tests are pending. |
| **P7** | One accepted command is one atomic journal entry | `dormant` | `commit` | Dormant check on `Infrastructure` and `Application`. |
| **P8** | A snapshot records AtSeq and a checksum | `dormant` | `commit` | Dormant check on `Infrastructure` and `Application`. |
| **P9** | Crash injection | `dormant` | `commit` | Dormant crash-injection test check. |
| **P10** | The archive and every projection are written only by the store through one derivation entry | `dormant` | `commit` | Dormant check on `Infrastructure`, `Application`, and `Contracts`. |
| **P11** | Events carry the facts they used | `built (Q; L advisory)` | `commit` | Static check verifies `evolve` purity and event payloads; `L` model-review component is advisory on Rust. |
| **P12** | Commands carry the position they were based on | `built` | `commit` | Checks that every `Contracts` command carries a `BasedOn` journal sequence number unless the glossary marks it head-independent. |
| **P13** | A tool that rewrites a file | `dormant` | `commit` | Dormant check on file-rewriting tools. |
| **P15** | Persisted or sorted unions are append-only | `built` | `commit` | Architecture check compares the committed shape file with the code's shapes line for line and checks append-only against the copy at the merge base (an error on CI when no merge base is found); the history check's `F4` keeps the file itself append-only. |

- **P2 — Persisted forms are plain data-transfer objects**: Persisted and wire forms are plain data-transfer objects in the context's `Codec`: integers and other primitives (no floating point), arrays, and nested transfer objects, with no optional, map, set, union, or tuple fields. Field tags are listed in a committed table and never reused, `Kernel`, `Domain`, and `Contracts` carry no serialisation annotations, no reflection-driven serialiser is used, and decoding builds domain values through their smart constructors. *Pending in Rust*: verifying the committed field-tag table (waits for the context codecs).
- **P3 — Persisted forms are versioned and proved**: Every persisted form (snapshot, archive record, journal payload, content pack, wire message) starts with a format ID and version. `encode` is total and writes the header; `decode` handles every released version through per-version decoders ending in an upcast, returns a typed error, never fails abruptly, and refuses a newer version by its number. Each codec has a round-trip property and a never-fails property over arbitrary input, and every persisted kind and version has a committed golden file that must still decode and is never edited. *Pending in Rust*: verifying round-trip and never-fails property tests, committed golden files, and the per-version decoder table (waits for the context codecs).
- **P4 — Golden stored instances**: Each release commits a stored instance captured mid-run and a crashed one (whose tail was not snapshotted), each with the state hash it reached. Every later build opens each one to that hash, which makes them the test of every migration, and they are append-only. *Reference status*: planned; a test per tagged release, and append-only in history (`F4`).
- **P5 — The journal is append-only and gap-free per branch**: The store has no path that updates or deletes a journal entry: an undo or an alternative history moves the head of a branch, and nothing is deleted. A gap or a broken chain refuses to open and is never skipped. *Reference status*: dormant until the store lands; a static scan for update and delete statements on the journal, a narrow test, and a property.
- **P6 — Command ids are made at the edge**: Every command envelope carries a `CommandId` made at the edge, never in the core and never a business key. The store has a unique key on `(stored instance, branch, CommandId)`, and a repeated append returns `already recorded` with the first position and writes nothing. *Pending in Rust*: dormant until command envelopes and the store land; the narrow test for the store's unique key and the scenario test for submitting the same command twice are pending.
- **P7 — One accepted command is one atomic journal entry**: Each accepted command is one atomic journal entry, holding the command, the events its decisions produced, its `CommandId`, `BasedOn`, the domain date, the engine and content versions, and the previous entry's hash. The store's single `record` operation takes the stored instance, the entry, and the expected journal position (`P12`), and returns a conflict as a typed result. Derived writes are in the same transaction or rebuilt on open, and the store is configured for durable commits (in the reference, a write-ahead log with full synchronisation on a local file). *Reference status*: dormant; the entry shape and the one `record` operation are checked, and review checks that no write is held open across computation.
- **P8 — A snapshot records AtSeq and a checksum**: A snapshot records the journal position it was taken at (`AtSeq`) and a checksum. Opening means taking the newest sound snapshot and folding the entries after `AtSeq` through `evolve`, with a gap check, and a torn snapshot is passed over. *Reference status*: dormant; a required-field check and a scenario test.
- **P9 — Crash injection**: The store's Nullable form can fail at each named step of `record`, and the steps are listed in its documentation comment. One scenario test per step shows that crash, reopen, and retry of the same envelope end in the same state as a run with no crash, and one narrow test kills a real process mid-write. *Reference status*: dormant; the inventory checks for one test per declared step.
- **P10 — The archive and every projection are written only by the store through one derivation entry**: Only the store writes the archive and the projections, through one derivation entry. Each is an `{ Empty; Step }` pair with `rebuild = fold Step Empty` and no second implementation, and a property and a verification tool show that the rebuilt value equals the live one. *Reference status*: dormant.
- **P11 — Events carry the facts they used**: Events carry the facts they used (amounts, rule set or content version, dates), and `evolve` never reads current mutable data or external content to interpret an event. *Advisory in Rust*: the `L` model-reviewer component of this `Q+L` rule is advisory-only on Rust paths.
- **P12 — Commands carry the position they were based on**: A command carries the journal sequence number it was based on (`BasedOn`, the event sequence number read before deciding), and a stale command is refused as stale unless the command is declared independent of the head in the glossary.
- **P13 — A tool that rewrites a file**: A tool that rewrites a file (a content compiler, an importer's export, a bug-report slicer, an export) keeps the fields and sections it does not understand, never overwrites its input, and gives the same output when run twice. *Reference status*: dormant; a round trip over a fixture with an unknown field, and a narrow test that the tool refuses its input path as its output.
- **P15 — Persisted or sorted unions are append-only**: A union that is persisted or used as a sort key only gains cases at the end, because union values may be ordered by their declaration order. A committed shape file lists each union's cases in order and must equal the code's shapes line for line, so no case is added, moved, or dropped without the file saying so. Against the shape file at the merge base with the main line, each union keeps its cases in order, only adds at the end, and is never removed. *Rust status*: when no merge base can be found (a shallow clone, no remote branch) the append-only half is skipped with a notice locally and is an error on CI; an empty shape file is refused on the real code (only a planted breach may have one).

---

### Group R: Reliability, error budgets, and postmortems

Group R enforces service-level reliability budgets and requires every fix commit to name the check or test that prevents a repeat (`Prevents:`).

| Rule | Title | Reference (Rust) status | Cadence | Check and scope in the reference Rust code base |
| :--- | :--- | :--- | :--- | :--- |
| **R1** | Reliability targets with error budgets | `partial` | `commit` | History check enforces the flaky-gate window and budget; crash-free session and store-and-reopen counters wait for sessions and stored instances. |
| **R2** | Each journal entry carries the previous entry's hash | `dormant` | `nightly` | Dormant nightly journal-hash check. |
| **R3** | Nightly backup restore and integrity drill | `dormant` | `nightly` | Dormant nightly SQLite restore and `PRAGMA integrity_check` drill. |
| **R4** | Repair and migration tools require a verified copy | `dormant` | `commit` | Dormant check on repair and migration tools. |
| **R5** | Defects promote a check | `partial` | `commit` | History check verifies `Prevents:` lines on fix commits; postmortem link check (dormant until postmortem files exist) and `L` review are pending. |
| **R6** | Crash reports carry journal tail, seed, and build id | `dormant` | `commit` | Dormant check on `Hosts` and tooling. |
| **R8** | Staged rollout | `process` | `on-demand` | Release-time process rule. |

- **R1 — Reliability targets with error budgets**: A service-level file names the reliability indicators with their agreed targets, strict from the start (for example crash-free sessions, store-and-reopen success, flaky gate runs, and gate duration), each counted over a window with a minimum sample. The gate reads the policy, a spent error budget means reliability work only, and exactness and replay divergence are zero tolerance, never budgets. *Pending in Rust*: crash-free session and store-and-reopen counters wait until sessions and stored instances exist.
- **R2 — Each journal entry carries the previous entry's hash**: Each journal entry carries the previous entry's hash (`P7`), and the host writes a state-hash checkpoint every *n* entries. A nightly out-of-band verifier re-decides the golden journals and real stored instances on the same engine version and compares events and checkpoints, and a mismatch stops the line. The database's own integrity checks prove a file's structure, not that its events are the right ones; the hash chain does that. *Reference status*: dormant; nightly, with a narrow test that a planted corrupted record is found.
- **R3 — Nightly backup restore and integrity drill**: A nightly drill restores the latest backup to a scratch location, runs the database's full integrity and foreign-key checks, opens it, plays one step, and compares the state hash. The health check fails when the last good drill is over a week old. Stored instances are tombstoned, never hard-deleted, and backups use the database's own backup mechanism, never a file copy taken while a write may be in flight. *Reference status*: dormant; nightly, the drill as a test with a recorded result. (In the reference's SQLite terms: `PRAGMA integrity_check`, not `quick_check`, plus `foreign_key_check`; the backup API or `VACUUM INTO`.)
- **R4 — Repair and migration tools require a verified copy**: A repair or migration tool refuses to run without a fresh, verified copy of its input, and works on the copy. *Reference status*: dormant; a narrow test.
- **R5 — Defects promote a check**: Every fix commit names, in a `Prevents` line, the type, rule, test, or rubric item that stops its class of defect, or `none` with a reason. Data loss, replay divergence, a long-broken main branch, or a defect that escaped review requires a blameless postmortem that asks why until the answer is a design or process change and ends with the rule or test it promoted; a link check fails on `none` or a dangling link. *Pending in Rust*: postmortem file link verification (dormant until the postmortems folder exists) and model review (`L`, advisory-only on Rust).
- **R6 — Crash reports carry journal tail, seed, and build id**: A crash report carries the journal's tail, the random seed, and the build identifier, and a tool minimises it into a regression test. *Reference status*: dormant; a check of the report's fields, with a worked example.
- **R8 — Staged rollout**: A new build runs on a first ring of devices or users for a set minimum time and number of sessions before it reaches the wider audience, and the decision-maker can change these numbers at any time. In the reference, the minimum is two days and three sessions on the owner's own devices. *Reference status*: process, at release.

---

### Group S: Scope, branches, spikes, and exemplars

Group S prevents scope creep and pattern drift: every backlog item must cite what it serves, session metrics and dormant rules are tracked, recurring patterns have one compiled exemplar, new modules start with two interface sketches, branches stay under one day and 400 lines, exploratory spikes live outside the workspace and expire after 14 days, and nothing is pushed before review where a green branch lands by itself.

| Rule | Title | Reference (Rust) status | Cadence | Check and scope in the reference Rust code base |
| :--- | :--- | :--- | :--- | :--- |
| **S1** | Every backlog item serves something | `built` | `commit` | History check verifies backlog links to capabilities, decisions, or rules, and checks file-touch scope. |
| **S2** | Budgets and trends reported each session | `built (partly report-only)` | `commit` | History check records session metrics against budgets; rules that do not fire outside their breach test are flagged (report-only), never removed automatically. |
| **S3** | One reviewed exemplar per recurring need | `partial` | `commit` | Builds the compiled exemplars in the exemplar list; typed-ID and builder exemplars and `L` review of duplicate patterns and edits are pending. |
| **S4** | Design it twice, interface first | `partial (dormant)` | `commit` | History check on interface-sketch specs is written, but dormant until specs carry an `Adds` line. |
| **S5** | Branches are short-lived | `built` | `commit` | History check enforces `<= 1` day branch age and `<= 400` changed lines summed over the branch's commits (excluding generated and listed boilerplate files). |
| **S7** | Research is standing, adoption is evaluated | `process` | `on-demand` | Standing research process rule. |
| **S8** | Spikes are thrown away | `partial` | `commit` | History and dependency checks verify spike isolation and the 14-day deletion limit; the rule table does not record what part remains pending. |
| **S9** | No push before review where green branches land by themselves | `process (new guidance)` | `commit` | New guidance; no rule-table row or Rust check. Held by a landing script and the reviewer's routine. |

- **S1 — Every backlog item serves something**: Every backlog item names what it serves (a required capability, a recorded decision, or a rule ID); an item that serves nothing is not started without explicit dated approval. A clean-up inside files an item already touches is part of that item; anywhere else it is a separate item.
- **S2 — Budgets and trends reported each session**: Each reporting period gives the rule count, allow entries, tool count, gate time, review time per item, public types per context, stored-instance size and open time, red-to-green time, and toil (manual interventions per merged item, where a repeated manual step becomes a tool). Growth past a budget needs approval, and a rule that has not fired outside its own breach test for a set number of sessions **is flagged, never removed automatically**. *Partly report-only*: growth past a budget requires approval, whereas a rule that has not fired outside its own breach test for a set number of sessions is flagged for review rather than removed or failing the gate.
- **S3 — One reviewed exemplar per recurring need**: An exemplar list names one compiled, reviewed example per recurring need (typed ID, constrained value, decider, codec, Nullable wrapper, builder, narrow test), built in the gate, or the backlog item it waits for. A change to an exemplar is reviewed under this rule, and a second way of doing the same thing is a blocking review finding unless allowed. *Pending in Rust*: the typed-ID and builder exemplars wait for the domain context crates, and reviewing exemplar edits and refusing duplicate patterns rely on model review (`L`, advisory-only on Rust).
- **S4 — Design it twice, interface first**: A new module, context, or wrapper starts with a spec holding two different interface sketches, each a signature with a short comment, and their comparison. Bodies come only after the chosen interface is reviewed, and a second reviewer reads the spec for gaps first. *Dormant in Rust*: the history check is dormant until specs carry an `Adds` line.
- **S5 — Branches are short-lived**: A branch is merged within a day and holds at most 400 changed lines, summed over all of its commits (so a fix-up commit counts again), excluding generated files, unless explicitly overridden. A dated approval may exclude listed boilerplate files (in the reference code base, the manifest and linter file each new context crate repeats) and may grant a dated, temporary exception with a higher limit (in the reference code base 550 lines for a branch that adds a domain crate, which lands with its property list, until a set date). Rust source is never excluded. An overrun is split into stacked branches, never compressed (by reformatting, for example).
- **S7 — Research is standing, adoption is evaluated**: Open research topics live in one research backlog, each naming what it serves (`S1`), and a fixed recurring slot goes to the top topic, with each slot logged with its outcome. A finding is adopted only by evaluate-then-adopt: a written evaluation naming its hypothesis, prediction, and measure against the current rule, tool, or practice; then a decision record; then the normal rule change, where loosening still needs the decision-maker's approval. Nothing is adopted from a research note alone. *Reference status*: process; a log of dated slots.
- **S8 — Spikes are thrown away**: Exploratory code lives in a dated spike folder outside the workspace, and no package depends on it. Nothing moves from a spike into production code or tests except by being rewritten test-first, and a spike folder older than 14 days is deleted with its finding recorded in its backlog item. *Pending in Rust*: marked `partial` in the rule table (with a history check); the rule table does not record what part remains pending.
- **S9 — No push before review where green branches land by themselves** *(new guidance; no Rust check)*: Where CI fast-forwards any green working branch onto the main line, pushing a branch is merging it. So a model builder commits locally only, and the reviewer pushes after the blind review passes (`V2`, `V3`). In the reference code base an unreviewed change landed this way twice: once a branch pushed before its review, and once two stacked branches that landed while the check they added was still under a review that went on to find bypasses in it. All landing goes through one script that:
  - refuses to continue if any conflict marker is left after a merge;
  - never pipes a gate into `tail`, `head`, or any other command whose exit code replaces the gate's (in the reference code base, `gate | tail && git push` pushed past a red gate twice);
  - pushes only when every gate exits green;
  - after the push, verifies that the remote branch head equals the local head;
  - lands stacked branches in order, each one only after its parent is on the main line.

---

### Group V: Blind model-reviewer gate and calibration

Group V defines the architecture of the automated model-review runner. **Rust status at the time of writing**: all eight rules (`V1`–`V8`) are marked `partial` because the rubrics are calibrated (`V4`) only on code in another language, the code base's earlier F# track; the rule table also notes that the rubric items' file-path scopes do not yet name Rust paths. Until a rubric set with Rust scopes and Rust calibration cases is calibrated, the model-review runner forces every verified finding on a Rust path to the severity `advice`: it is recorded and still counted by calibration, but never blocks, and the findings ledger (`V7`) does not count it toward promotion. Whatever the runner's status, a blind reviewer also runs a handful of hand mutants of the branch's new code and reports the survivors (`E14`).

| Rule | Title | Reference (Rust) status | Cadence | Check and scope in the reference Rust code base |
| :--- | :--- | :--- | :--- | :--- |
| **V1** | Rubric items are binary questions | `partial (advisory on Rust)` | `commit` | Model-review runner validates rubric schema and binary verdicts; Rust file-path scopes and calibration cases are pending. |
| **V2** | The automated reviewer is blind and isolated | `partial (advisory on Rust)` | `commit` | Model-review runner executes in a fresh read-only checkout with allow-listed inputs; Rust rubric scopes are pending. |
| **V3** | Review findings are verified records | `partial (advisory on Rust)` | `commit` | Model-review runner verifies JSON findings (`rule`, `file`, `line`, `quote`); advisory-only on Rust paths. |
| **V4** | The reviewer is calibrated on labelled cases | `partial (advisory on Rust)` | `commit` | Model-review runner checks calibration metrics (50–200 cases, 3+3 per item, 3 runs, TP `>= 0.90`, TN `>= 0.95`); Rust cases are pending. |
| **V5** | No review on a stale calibration | `partial (advisory on Rust)` | `nightly` | Model-review runner checks hash changes, 30-day age, 3 disputes, accuracy bar, and weekly canary; advisory-only on Rust paths. |
| **V6** | A second reviewer from another model family | `partial (advisory on Rust)` | `commit` | Model-review runner supports second-family confirmation; in an unattended session where no second reviewer is available, a verified block blocks on its own; Rust scopes are pending. |
| **V7** | A findings ledger promotes repeat concerns | `partial (advisory on Rust)` | `commit` | Model-review runner tracks findings per rule ID to promote repeat concerns; Rust rubric scopes are pending. |
| **V8** | Protected paths checked before review | `partial (advisory on Rust)` | `commit` | Model-review runner checks diffs on existing tests, standards, breaches, golden files, and gate scripts; Rust rubric scopes are pending. |

- **V1 — Rubric items are binary questions**: Every rubric item is a binary question about one principle, with its rule ID, scope, violation, holds and cannot-determine conditions, a passing and a failing snippet with expected verdicts, evidence, severity, and status. Rubrics use no scores. *Pending on Rust paths*: rubric file scopes do not yet match the Rust workspace.
- **V2 — The automated reviewer is blind and isolated**: Each item and each review runs in a fresh checkout with no leftover history, outputs, or other reviews, and the reviewer has no write access. Its input is built from an allow list (the diff, the rubric items whose scope the diff touches, the automated results, and read-only repository access) and never includes the author's summary, commit message, claims, or test narrative. *Pending on Rust paths*: rubric file scopes do not yet match the Rust workspace.
- **V3 — Review findings are verified records**: Review findings are structured records with `rule`, `file`, `line`, `quote`, `verdict`, `severity`, and `reason` (`why`); a script verifies the rule ID, the diff line, and the quote, an unverified block or concern is downgraded to a note, and a merge happens only with no verified block. *Advisory on Rust paths*: the model-review runner executes in advisory-only mode on Rust diffs until Rust rubric scopes and calibration cases land.
- **V4 — The reviewer is calibrated on labelled cases**: The reviewer is calibrated on a set of 50 to 200 labelled cases, with at least 3 faulty and 3 good cases per review item; each case runs 3 times and the majority counts. The record holds the true-positive and true-negative rates with 95% Wilson intervals, an agreement statistic, the unstable cases, the model, its settings, and the prompt and rubric hashes, and the bar is a true-positive rate of at least 0.90 and a true-negative rate of at least 0.95. *Pending on Rust paths*: Rust calibration cases are pending.
- **V5 — No review on a stale calibration**: The review refuses to run when the model, prompt, or a rubric has changed, the calibration is over 30 days old, there have been 3 panel disputes or an escaped defect, the last run failed the bar, or the weekly hidden canary (a diff with a known fault) was missed or did not run. *Advisory on Rust paths*: the model-review runner is advisory-only on Rust diffs until Rust rubric scopes and calibration cases land.
- **V6 — A second reviewer from another model family**: When a second reviewer model from a different family is available, it reviews every block and a weekly sample of merged diffs; a block it does not confirm becomes a concern and a calibration case. Where no second reviewer is available, a verified block blocks on its own. *Pending on Rust paths*: in an unattended session where no second-family reviewer is available, a verified block blocks on its own, and Rust rubric scopes are pending.
- **V7 — A findings ledger promotes repeat concerns**: A findings ledger counts review findings per rule ID, and the second occurrence of a concern that an automated check could detect opens a task to promote it to such a check. *Pending on Rust paths*: Rust rubric scopes are pending.
- **V8 — Protected paths checked before review**: Protected paths are checked on the diff before review: an author may add tests but may not change or delete an existing test line unless the spec names the file, and may not touch the standards, breach examples, golden files, or gate scripts, whose changes go to the responsible approver. *Pending on Rust paths*: Rust rubric scopes are pending.

---

## 5. Keeping the gate fast

Speed budgets (`E8`) bind every test and every gate stage, and the cold build is usually the stage that breaks first. These build-profile practices come from the reference code base's Rust workspace; each was measured before it was adopted, and none removed a test, a check, or a budget. The times quoted are that code base's, on its machines: they show the size of each effect, not what your stack will see. Measure before and after each change on your own stack, and record budgets with where they came from ([templates/budgets.toml](templates/budgets.toml)). The JVM counterparts follow the Rust list; [docs/languages/](docs/languages/README.md) has the other languages'.

### In the reference Rust code base

- **Turn debug information off in development and test builds.** In the reference code base it was about a quarter of a cold build's compile time (a cold build went from 23.3 s to 16.4 s with the change below). A failed assertion still names its file and line (in Rust through `#[track_caller]`), and a developer who needs a debugger turns it back on for one build through an environment variable.
- **Optimise only the crates that need it.** The reference code base optimises its kernel (the random-number stream) and its recordings runner in development builds, because a run of the recording tests walks about 45 million random draws: unoptimised, each recording test took 80 to 130 ms against its 200 ms budget, with no headroom for a slower CI runner. Optimising the domain crates or the parsers did not pay: optimised domain crates made slow code fast enough to pass the budgets, so a quadratic algorithm stayed hidden (without the optimisation the heaviest recording files ran 60 to 95 ms), and they pushed the cold CI build to 95.6 s against its 75 s budget. Optimising the parsers saved about 0.9 s in the test stage and cost about 9 s in every cold build.
- **One test binary per crate.** Each separate test file is linked as its own binary, so a crate with many test files pays the link step many times. Declare the test files as modules of one test target (in Rust, `autotests = false` and a `tests/all.rs` that declares each file), and add a check that no file under `tests/` is left out of every test target, so an undeclared file cannot silently stop running. In the reference code base this took 94 test binaries to 69, with the same 662 tests.
- **Never build the workspace twice.** A gate stage that builds in its own target directory (in the reference code base, the planted-breach stage) recompiles every dependency. Removing that second build took the cold breach stage on CI from about 95 s to 19 s. Where a separate build is unavoidable, delete its output when the gate ends, so it does not fill the disk.

### On the JVM

Detail and settings are in [docs/languages/java.md, section 16](docs/languages/java.md#16-keeping-the-gate-fast); the [Java starter](examples/java-starter/README.md) shows the per-test judge and the single build with its breaches.

- **Let Gradle reuse work.** Turn on the build cache, the configuration cache and parallel project builds (`org.gradle.caching`, `org.gradle.configuration-cache`, `org.gradle.parallel` in `gradle.properties`), keep the daemon warm locally, and cache `~/.gradle` (or use a remote build cache filled by CI) so that a CI run is warm. On the JVM the cold cost is mostly dependency download, daemon start and Gradle configuration, not compilation.
- **Fork few test JVMs and keep them.** Use a small fixed `maxParallelForks` and never `forkEvery = 1`: every new JVM pays start-up, class loading and JIT warm-up again. This is the JVM form of "one test binary per crate". Class-data sharing and `-XX:TieredStopAtLevel=1` can shorten short test JVMs; measure each first.
- **Keep debug information.** Unlike Rust's, javac's line and variable tables are cheap, and stack traces need them. JIT warm-up is the cost to manage: the first test in a fork can take many times its warm time, so judge per-test budgets on the test body only (as the starter's `E8` judge does) and leave headroom.
- **Never build the workspace twice.** Compile the planted breaches as source sets of the same build and read their classes by path, as the starter does, rather than running a second Gradle build per breach.
