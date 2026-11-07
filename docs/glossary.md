# Glossary

The guide's own terms, for a reader who has not seen the reference code base. Each entry is one or two lines and links to the rules that use the term; the rules are stated in full in the [guide](../README.md#4-the-rules-by-group). Terms of the domain you build (the *ubiquitous language*) belong in your project's own glossary, which is a different thing: see [glossary (the domain one)](#glossary-the-domain-one).

[A](#a) · [B](#b) · [C](#c) · [D](#d) · [E](#e) · [F](#f) · [G](#g) · [H](#h) · [I](#i) · [J](#j) · [K](#k) · [L](#l) · [M](#m) · [N](#n) · [O](#o) · [P](#p) · [Q](#q) · [R](#r) · [S](#s) · [T](#t) · [U](#u) · [V](#v) · [W](#w)

---

## A

**Aggregate, aggregate root.** A cluster of domain state changed as one unit by one decision, named by its root and its typed identifier. Other aggregates refer to it by identifier only. [`B3`](../README.md#group-b-bounded-contexts-domain-model-and-glossary), [`B5`](../README.md#group-b-bounded-contexts-domain-model-and-glossary), [`B6`](../README.md#group-b-bounded-contexts-domain-model-and-glossary).

**Allow entry.** One recorded exception to a rule, in the rule table's `[allow]` section: the rule id, the exact file and member, the reason, the decision record it relaxes and a dated approval (`owner_yes`), optionally a review-by date. An entry that no longer excuses anything fails the gate. [`F1`](../README.md#group-f-compiler-hygiene-and-self-proving-rules), [`I2`](../README.md#group-i-governance-decision-records-and-vitality), [`I5`](../README.md#group-i-governance-decision-records-and-vitality); schema in [rule-table.md](rule-table.md#5-allow-exceptions-as-data).

**Architecture check, architecture tests.** The checks that read build files, the package graph and the code's syntax or compiled types, together with the meta-test. In Java, ArchUnit tests and flat build-file tests. [`A1`](../README.md#group-a-architecture-layers-and-dependencies), [`F5`](../README.md#group-f-compiler-hygiene-and-self-proving-rules).

## B

**BasedOn.** The journal sequence number a command was computed against; a stale command is refused as stale. [`P12`](../README.md#group-p-persistence-codecs-and-versioning).

**Blind review.** A review whose reviewer sees the diff and the code but never the author's summary, claims or commit message, in a fresh checkout. [`V2`](../README.md#group-v-blind-model-reviewer-gate-and-calibration), [`S9`](../README.md#group-s-scope-branches-spikes-and-exemplars).

**Breach, breach folder.** See [planted breach](#planted-breach).

**Builder (test-data builder).** A test helper that builds domain values in the domain's language, so tests do not spread literal records. [`E16`](../README.md#group-e-testing-and-verification).

**Built, partial, planned.** A rule's status in the rule table: `built` (the whole rule is checked and its breach proves it), `partial` (part is checked; the rest is listed as pending), `planned` (no check yet; every rule starts here). See also [dormant](#d). [`I1`](../README.md#group-i-governance-decision-records-and-vitality); [rule-table.md](rule-table.md#statuses-and-dormant-markers).

## C

**Calibration.** Measuring the model reviewer on a set of labelled cases (50 to 200, at least 3 faulty and 3 good per rubric item, each run 3 times) against a bar of true-positive and true-negative rates, before any of its findings may block. A stale calibration stops the review. [`V4`](../README.md#group-v-blind-model-reviewer-gate-and-calibration), [`V5`](../README.md#group-v-blind-model-reviewer-gate-and-calibration); [rubrics.md](rubrics.md#4-calibration).

**Canary.** A diff with a known fault, given to the reviewer weekly without notice; a missed canary stops the review until recalibration. [`V5`](../README.md#group-v-blind-model-reviewer-gate-and-calibration).

**Check id.** A rule id plus a short name (`A1-layers`, `C12-no-default-arm`), because one rule may need several checks. It names the check's breach folder and starts its failure message. [`F5`](../README.md#group-f-compiler-hygiene-and-self-proving-rules); [self-test-harness.md](self-test-harness.md#2-the-check-registry).

**Codec.** The layer that turns domain values into persisted or wire forms and back, through plain data-transfer objects and versioned decoders. It does no I/O. [`A8`](../README.md#group-a-architecture-layers-and-dependencies), [`P2`](../README.md#group-p-persistence-codecs-and-versioning), [`P3`](../README.md#group-p-persistence-codecs-and-versioning).

**Cold and warm track.** Kinds of gate run judged apart: warm (build output and caches present) and cold (a fresh clone or no restored cache), each locally and on CI. Each track has its own stage budgets and its own ratchet history, so a cold run neither fails against warm numbers nor loosens them. [`E8`](../README.md#group-e-testing-and-verification); [templates/budgets.toml](../templates/budgets.toml).

**Composition, Day.** The one thin package that schedules work across the contexts' application packages, and the only application package that spans contexts. The template calls its layer row `Composition`; the guide's examples call it `Day`, after the reference code base's daily cycle. Name yours after what it schedules. [`A1`](../README.md#group-a-architecture-layers-and-dependencies), [`A14`](../README.md#group-a-architecture-layers-and-dependencies), [`A17`](../README.md#group-a-architecture-layers-and-dependencies).

**Context (bounded context).** A part of the domain with its own model and language, built as its own `Domain`, `Contracts`, `Codec` and `Application` packages. [`A2`](../README.md#group-a-architecture-layers-and-dependencies), [`B1`](../README.md#group-b-bounded-contexts-domain-model-and-glossary), [`B13`](../README.md#group-b-bounded-contexts-domain-model-and-glossary).

**Context map.** A committed structural file listing each context (its subdomain class, logic style, architecture style, status) and each relation between two contexts with its kind and direction. The package graph must equal it. [`B1`](../README.md#group-b-bounded-contexts-domain-model-and-glossary), [`B13`](../README.md#group-b-bounded-contexts-domain-model-and-glossary), [`F4`](../README.md#group-f-compiler-hygiene-and-self-proving-rules).

**Contracts.** A context's published types (commands, events, views) and the only part another context may see. [`A2`](../README.md#group-a-architecture-layers-and-dependencies), [`M1`](../README.md#group-m-messaging-and-event-envelopes), [`B23`](../README.md#group-b-bounded-contexts-domain-model-and-glossary).

**Constrained value.** A simple domain type with a hidden representation, a `create` that returns the value or a typed error, and a `value` accessor. [`C3`](../README.md#group-c-functional-core-types-and-errors), [`C4`](../README.md#group-c-functional-core-types-and-errors).

## D

**Day.** See [Composition, Day](#c).

**Decider.** An aggregate written as pure functions: `decide(leading parameters, command, state)` returns events or a refusal, `evolve(state, event)` folds an event into the state, and `is_terminal` says when it has ended. Business decisions live only in `decide`. [`B3`](../README.md#group-b-bounded-contexts-domain-model-and-glossary), [`E10`](../README.md#group-e-testing-and-verification); Java: the starter's [`Loan.java`](../examples/java-starter/loans/domain/src/main/java/starter/loans/domain/Loan.java).

**Decision record.** A short, numbered, committed document stating a decision, why, its `Compliance` (the rule ids that enforce it), and a "Revisit when" trigger. Structural commits and loosening changes cite one. [`I2`](../README.md#group-i-governance-decision-records-and-vitality), [`I3`](../README.md#group-i-governance-decision-records-and-vitality), [`F4`](../README.md#group-f-compiler-hygiene-and-self-proving-rules).

**Defect.** A broken invariant or unsupported case the core detects; it is returned as a typed defect case, not thrown, and stops the host with a bug report. One of the four error kinds. [`C1`](../README.md#group-c-functional-core-types-and-errors), [`C11`](../README.md#group-c-functional-core-types-and-errors).

**Dormant.** A check that is written and has its planted breach, but whose target code does not exist yet; its rule-table row names the backlog item that brings that code (`dormant_until`). The meta-test requires a dormant check to select nothing, and fails as soon as it selects something, which wakes it. [`F5`](../README.md#group-f-compiler-hygiene-and-self-proving-rules); [self-test-harness.md](self-test-harness.md#5-dormant-checks).

## E

**Envelope.** The wrapper around a journaled event or a command: correlation and causation ids and a schema version for events, a `CommandId` made at the edge for commands. In the store, an opaque record of kind, version and payload. [`M2`](../README.md#group-m-messaging-and-event-envelopes), [`P6`](../README.md#group-p-persistence-codecs-and-versioning), [`A8`](../README.md#group-a-architecture-layers-and-dependencies).

**Exemplar.** The one compiled, reviewed example of a recurring need (typed id, constrained value, decider, codec, Nullable wrapper, builder, narrow test) that new code copies; a second way of doing the same thing is a finding. [`S3`](../README.md#group-s-scope-branches-spikes-and-exemplars).

## F

**Facade.** The one listed file through which an application package may expose chosen domain types (rules sections, workflow inputs, views, refusals; never an aggregate, its commands or its events) to the composition package. Java has no re-exports, so there the facade is a package of application-owned types. [`A1`](../README.md#group-a-architecture-layers-and-dependencies); [java.md, A1 (c)](languages/java.md#4-group-a-architecture-layers-and-dependencies).

**Flaky gate run.** A gate run that is red and then green on the same commit; counted against a budget. [`R1`](../README.md#group-r-reliability-error-budgets-and-postmortems), [`E8`](../README.md#group-e-testing-and-verification), [`E13`](../README.md#group-e-testing-and-verification).

**Flat syntactic rule.** A check that forbids a form outright instead of resolving aliases or proving a use harmless; each bypass found gets its own breach. [`F5`](../README.md#group-f-compiler-hygiene-and-self-proving-rules).

## G

**Gate.** The one script that runs every commit-time check and test and must exit green before a merge; the **quick check** is its inner-loop subset (under 15 s). [`E8`](../README.md#group-e-testing-and-verification), [`S9`](../README.md#group-s-scope-branches-spikes-and-exemplars).

<a id="glossary-the-domain-one"></a>
**Glossary (the domain one).** A committed structural file of your domain's terms per context, each with a one-line meaning (and, for constrained types, what it carries). Every public domain name must be a term in it; it is not this page. [`B4`](../README.md#group-b-bounded-contexts-domain-model-and-glossary), [`C4`](../README.md#group-c-functional-core-types-and-errors), [`F4`](../README.md#group-f-compiler-hygiene-and-self-proving-rules).

**Golden file.** A committed, never-edited sample of a persisted form at one version, which every later build must still decode. [`P3`](../README.md#group-p-persistence-codecs-and-versioning), [`P4`](../README.md#group-p-persistence-codecs-and-versioning), [`F4`](../README.md#group-f-compiler-hygiene-and-self-proving-rules).

## H

**Hand mutant.** A small deliberate change to new production code (an off-by-one, a dropped branch, a swapped comparison), made by the builder or reviewer to see whether the tests notice; a survivor is killed by a sharper test or argued equivalent. The stand-in until a mutation ratchet runs. [`E14`](../README.md#group-e-testing-and-verification), [`E34`](../README.md#group-e-testing-and-verification); [rubrics.md](rubrics.md#5-hand-mutants-beside-the-reviewer-e14).

**History check.** The runner that reads git: commit messages, principle-file diffs, decision records, surface and shape files, branch size and age. [`F4`](../README.md#group-f-compiler-hygiene-and-self-proving-rules), [`S5`](../README.md#group-s-scope-branches-spikes-and-exemplars); [runners.md](runners.md#1-the-history-check).

## I

**Inventory check.** The runner that reads the test list and test reports: per-test time budgets by tier and which shared-suite cases ran. [`E8`](../README.md#group-e-testing-and-verification), [`E36`](../README.md#group-e-testing-and-verification); [runners.md](runners.md#2-the-inventory-check).

**Invariants.** An aggregate's function from state to a list of breaches, empty when valid, run after every scenario and simulation step. [`B18`](../README.md#group-b-bounded-contexts-domain-model-and-glossary).

## J

**Journal.** The append-only log of accepted commands and their events, one atomic entry per command, hash-chained. [`P5`](../README.md#group-p-persistence-codecs-and-versioning), [`P7`](../README.md#group-p-persistence-codecs-and-versioning), [`R2`](../README.md#group-r-reliability-error-budgets-and-postmortems).

## K

**Kernel.** The bottom layer, depending on nothing but the language's core library: `Result`, immutable collections, the domain date, the seeded random service, integer wrappers. Shared by every context. [`A1`](../README.md#group-a-architecture-layers-and-dependencies), [`B24`](../README.md#group-b-bounded-contexts-domain-model-and-glossary).

**Kinds (Q, L, Q+L, report, process).** How a rule is checked: a mechanical check (`Q`), the model reviewer against a rubric item (`L`), both, a trend report that never gates, or an attended routine. [rule-table.md](rule-table.md#kinds).

## L

**Layer table.** The ordered `[[layer]]` rows of the rule table that classify every package by its build-file path and list what it may depend on; the first match wins. [`A1`](../README.md#group-a-architecture-layers-and-dependencies); [templates/rules.toml](../templates/rules.toml).

**Logic, scenario, narrow tests.** The test tiers: logic tests of kernel and domain in memory, by output (20 ms each); scenario tests through the application with Nullable edges (200 ms); narrow integration tests of one real wrapper against its real edge (200 ms). [`E3`](../README.md#group-e-testing-and-verification), [`E8`](../README.md#group-e-testing-and-verification), [`E17`](../README.md#group-e-testing-and-verification).

**Loosening, tightening.** A change to a principle file that admits more (removing a banned word, raising a budget, adding an allow entry, lowering a rule's status) or less. Loosening needs a dated approval in the commit (`Owner-yes: YYYY-MM-DD`). [`F4`](../README.md#group-f-compiler-hygiene-and-self-proving-rules); [rule-table.md](rule-table.md#6-tightening-only-f4).

## M

**Meta-test.** The test that proves every check: the check ids equal the breach folders; each check passes on the real code and selects something there unless dormant; each breach fires its own check and no other. [`F5`](../README.md#group-f-compiler-hygiene-and-self-proving-rules); [self-test-harness.md](self-test-harness.md#3-what-the-meta-test-asserts).

**Model-review runner.** The runner that has an isolated reviewer model answer calibrated rubric questions about a diff and verifies each finding against the diff. [`V1`](../README.md#group-v-blind-model-reviewer-gate-and-calibration)–[`V8`](../README.md#group-v-blind-model-reviewer-gate-and-calibration); [runners.md](runners.md#4-the-model-review-runner).

## N

**Nullable.** An infrastructure wrapper with two factories, `create` (real) and `create_null` (an embedded stub with configurable responses), neither doing I/O; it records its outputs as named behaviour values. It replaces mocking libraries. [`E1`](../README.md#group-e-testing-and-verification), [`E5`](../README.md#group-e-testing-and-verification), [`E17`](../README.md#group-e-testing-and-verification); Java: the starter's [`NoticeLog.java`](../examples/java-starter/infrastructure/notices/src/main/java/starter/infrastructure/notices/NoticeLog.java).

## O

**Owed work.** Items a comparing test knows it does not yet compare (recorded by the reference but not modelled, or a named temporary adaptation), counted and pinned per kind so the count can only fall on purpose. [`E37`](../README.md#group-e-testing-and-verification), [`E15`](../README.md#group-e-testing-and-verification).

**Owner-yes.** The dated approval line a loosening commit or an allow entry carries. [`F4`](../README.md#group-f-compiler-hygiene-and-self-proving-rules), [`I2`](../README.md#group-i-governance-decision-records-and-vitality).

## P

**Pending.** The part of a `partial` rule not yet checked, listed with what it waits for (`parts_pending`). Shown in the guide as *Pending in Rust* for the reference code base. [`I1`](../README.md#group-i-governance-decision-records-and-vitality).

<a id="planted-breach"></a>
**Planted breach.** The smallest deliberate violation of one check, kept in `breaches/<check-id>/` outside every production build, which the meta-test uses to prove the check fires on it and only it. Also: a **planted change**, a one-off edit to real code or data to see the right check or test fail, then restored. [`F5`](../README.md#group-f-compiler-hygiene-and-self-proving-rules); [self-test-harness.md](self-test-harness.md#1-the-breach-folder).

**Prevents line.** The line a fix commit carries naming the type, rule, test or rubric item that stops its class of defect, or `none` with a reason. [`R5`](../README.md#group-r-reliability-error-budgets-and-postmortems).

**Principle file.** A file of standards that changes only by adding or tightening unless the commit cites a dated approval: the rule table (with its allow list), the budgets and the rubrics. [`F4`](../README.md#group-f-compiler-hygiene-and-self-proving-rules); listed under `[f4].principle` in [templates/rules.toml](../templates/rules.toml).

**Proven and provisional recordings.** A recorded case is *proven* when its recording was checked by coverage against a real reference run, otherwise *provisional*; each test pins how many of each it holds. [`E37`](../README.md#group-e-testing-and-verification), [`E36`](../README.md#group-e-testing-and-verification); the guide's [differential testing subsection](../README.md#differential-testing-against-a-reference-programs-recordings).

## Q

**Quarantine.** Taking a flaky test out of the gate by an allow entry with a reason, a dated approval and an expiry of at most 7 days. [`E30`](../README.md#group-e-testing-and-verification).

## R

**Ratchet.** A recorded number that may move only one way: a test count or coverage that may not fall, a surviving-mutant count or a stage's wall time that may not grow (beyond 10 % over the median of its last 20 green runs, for time). Never a target to reach. [`E8`](../README.md#group-e-testing-and-verification), [`E14`](../README.md#group-e-testing-and-verification), [`E34`](../README.md#group-e-testing-and-verification), [`G3`](../README.md#group-g-simplicity-and-module-design).

**Recordings.** Captured runs of a reference program, stored as language-neutral case files and replayed against the new system. [`E36`](../README.md#group-e-testing-and-verification), [`E37`](../README.md#group-e-testing-and-verification), [`E15`](../README.md#group-e-testing-and-verification).

**Reference code base.** The working Rust code base this guide was written from. You never need it; its paths, numbers and statuses are quoted as examples. [Section 1](../README.md#1-the-idea-in-one-page).

**Reference program.** An existing program a new system must reproduce exactly, when the project is a reimplementation. Only the rules marked for reimplementation need it (`A5`, `A7`, `D4`, `D10`, `E15`, `E25`'s clause). [`A5`](../README.md#group-a-architecture-layers-and-dependencies), [`E15`](../README.md#group-e-testing-and-verification).

**Refusal.** `decide`'s typed answer when a command is not allowed; never a silent empty success, and one of the four error kinds. [`B3`](../README.md#group-b-bounded-contexts-domain-model-and-glossary), [`C11`](../README.md#group-c-functional-core-types-and-errors).

**Rubric.** A set of binary questions about one principle each (rule id, scope, when it is violated, when it holds, a passing and a failing example), which the model reviewer answers about a diff. No scores. [`V1`](../README.md#group-v-blind-model-reviewer-gate-and-calibration); [rubrics.md](rubrics.md#2-the-rubric-format-v1).

**Rule table.** The committed data file listing every rule with its kind, cadence, scope, runner and status, plus the layer table, word lists, allow entries and limits the checks read. [`I1`](../README.md#group-i-governance-decision-records-and-vitality), [`F4`](../README.md#group-f-compiler-hygiene-and-self-proving-rules); [rule-table.md](rule-table.md), [templates/rules.toml](../templates/rules.toml).

## S

**S5 lines.** A branch's size for rule `S5`: changed lines summed over all its commits (so a fix-up commit counts again), excluding generated files; at most 400, and the branch merged within a day. [`S5`](../README.md#group-s-scope-branches-spikes-and-exemplars).

**Selection.** What a check looks at on a body of code (classes, files, tests). An empty selection on the real code means the check is **vacuous**, unless it is dormant. [`F5`](../README.md#group-f-compiler-hygiene-and-self-proving-rules); [self-test-harness.md](self-test-harness.md#4-vacuity-a-check-must-select-real-code).

**Shape file.** A committed file listing each persisted or sorted union's cases in order; cases may only be added at the end. [`P15`](../README.md#group-p-persistence-codecs-and-versioning).

**Smoke tier.** One to three tests that start the real hosts and together reach every real wrapper's real factory. [`E26`](../README.md#group-e-testing-and-verification).

**Spike.** Throw-away exploratory code in a dated folder outside the workspace, deleted after 14 days. [`S8`](../README.md#group-s-scope-branches-spikes-and-exemplars).

**Store, stored instance.** The store is the persistence wrapper and its database; a stored instance is one persisted copy of the system's state, with its journal, snapshots and archive. [`B8`](../README.md#group-b-bounded-contexts-domain-model-and-glossary), [`P4`](../README.md#group-p-persistence-codecs-and-versioning), [`M7`](../README.md#group-m-messaging-and-event-envelopes).

**Supply-chain check.** The runner that checks lockfiles, package ages, advisories and reproducible builds. [`H4`](../README.md#group-h-supply-chain-and-reproducible-builds)–[`H6`](../README.md#group-h-supply-chain-and-reproducible-builds); [runners.md](runners.md#3-the-supply-chain-check).

**Surface file.** A committed text dump of a package's public API; the gate fails on any difference not committed with it. [`B23`](../README.md#group-b-bounded-contexts-domain-model-and-glossary), [`B24`](../README.md#group-b-bounded-contexts-domain-model-and-glossary).

## T

**Tier.** The class of a test (logic, scenario, narrow integration, exact comparison, architecture, smoke, nightly), which sets its per-test time budget and what it may touch. [`E3`](../README.md#group-e-testing-and-verification), [`E8`](../README.md#group-e-testing-and-verification); [templates/budgets.toml](../templates/budgets.toml).

**Track.** See [cold and warm track](#c).

## U

**Upcast.** Turning a decoded older version of a persisted form into the current one, the last step of every per-version decoder. [`P3`](../README.md#group-p-persistence-codecs-and-versioning).

## V

**Vacuous check.** See [selection](#s).

**Verified finding.** A reviewer finding whose rule id, diff line and quote a script has confirmed; only a verified block can block a merge. [`V3`](../README.md#group-v-blind-model-reviewer-gate-and-calibration).

**Vitality review.** A monthly attended session that reads the rules, allow entries and trends, proposes removals, and re-runs the gate's self-test. [`I9`](../README.md#group-i-governance-decision-records-and-vitality).

## W

**Wrapper.** The one infrastructure package that may use a given third-party library; it translates every failure into its own closed error union, and is Nullable. [`A10`](../README.md#group-a-architecture-layers-and-dependencies), [`C14`](../README.md#group-c-functional-core-types-and-errors), [`E5`](../README.md#group-e-testing-and-verification), [`E21`](../README.md#group-e-testing-and-verification).
