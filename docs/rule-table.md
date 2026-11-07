# The rule table: schema

The guide refers throughout to "the rule table": the layer rows that `A1` reads, the word lists that `A9` and `B22` read, the `[allow]` table, each rule's status and dormant marker. This page defines that table. A starter copy is in [`templates/rules.toml`](../templates/rules.toml), and the speed budgets that `E8` reads are in [`templates/budgets.toml`](../templates/budgets.toml).

The table is data, not prose. The checks read it, a meta-test checks it against the checks (`F5`, `I1`), and the history check keeps it from being loosened without a dated approval (`F4`). Keep it at `standards/rules.toml`, next to the other principle files.

## 1. Layout

| Section | Holds | Read by |
| :--- | :--- | :--- |
| `[[layer]]` | one row per kind of package, matched by build-file path | `A1`, `A2`, `A10`, `A14`, `E3` |
| `[words]` | technology and user-interface words banned in core names | `A9` |
| `[names]` | vague and pattern words (`vague`), irregular past participles (`past_irregular`) | `B22`, `B7` |
| `[decider]` | the leading parameters a `decide` may take (`leading`) | `B3` |
| `[banned]` | dependency-injection containers (`containers`), mocking libraries (`mocking`) | `A10`, `E1` |
| `[packages]` | each production package's third-party packages | `A10`, `H4` |
| `[primitives]` | raw primitives allowed in core signatures (`allowed`) | `C4` |
| `[text]` | string literals allowed in the core as keys (`keys`) | `D5` |
| `[glossary]` | names exempt from the glossary (`generic`) | `B4` |
| `[vocabulary]` | the closed vocabularies of the context map | `B1`, `B13` |
| `[allow]` | every exception, one list per reading rule | `I2`, `I5`, and each list's rule |
| `[structure]` | complexity and size limits | `G3`, `G4`, `G7`, `G11`, `A17`, `B25` |
| `[supply]` | the minimum package age | `H4` |
| `[range]`, `[s5]`, `[vision]`, `[spikes]`, `[quarantine]`, `[i2]`, `[f2]`, `[f4]`, `[e33]` | the numbers and paths the history check reads | `S5`, `B26`, `S8`, `E30`, `I2`, `F2`, `F4`, `P15`, `E33`, and the commit rules |
| `[[rule]]` | one row per rule | `I1`, `F5`, `F3`, `I2`, `S2`, `F4` |

The reference code base kept the history numbers in a separate `history.toml` and the structure limits in its linter configuration. The template puts them in one table so that a new project has one file to copy. Either layout works if every file is listed as a principle file under `[f4]`.

## 2. `[[rule]]`: one row per rule

### Fields

| Field | Required | Meaning | Allowed values |
| :--- | :--- | :--- | :--- |
| `id` | yes | The rule's id: its group letter and a number. Ids are final. A new rule takes the next number after the highest in its group, and a retired id is never reused, so a citation in old code or an old record can never point to a different rule. | `A1`, `E37`, ... |
| `group` | yes | The rule's group. | `F`, `H`, `I`, `A`, `C`, `D`, `G`, `E`, `B`, `M`, `P`, `R`, `S`, `V` |
| `title` | yes | The rule's name, as the guide gives it. | text |
| `kind` | yes | How the rule is checked (see [Kinds](#kinds)). | `Q`, `L`, `Q+L`, `report`, `process` |
| `cadence` | yes | When the check runs (`I1`). | `commit`, `nightly`, `on-demand` |
| `extent` | yes | Whether the check judges one thing or the whole (`I1`). | `atomic`, `holistic` |
| `applies_to` | yes | The layers or areas the rule governs, named by `[[layer]]` names or areas (`tests`, `repo`, `standards`, `process`, `tools`). | text |
| `characteristics` | yes | The quality characteristics the rule serves (`I1`). | a list of the project's characteristic ids; the template uses the reference's seven: `exactness`, `determinism`, `testability`, `evolvability`, `recoverability`, `deployability`, `simplicity` |
| `family` | one of `family` or `record` | The principle family the rule comes from (`I1`). | `evans-vernon` (domain-driven design and layering), `wlaschin` (functional domain modelling), `khorikov-shore` (testing and Nullables), `ousterhout` (simplicity and module design), `exact` (determinism, exactness and persistence), `sre` (reliability), `supply-chain`, `governance`, `llm-gates` (model review), `house` (the project's own hygiene) |
| `record` | one of `family` or `record` | The decision record or concept page the rule rests on (`I1`, `I2`). | a path or record id |
| `check` | yes | The runner that owns the rule's check (see [Runners](#runners)). For `Q+L`, the runner of the `Q` half; the `L` half is always the model-review runner. | `architecture`, `history`, `inventory`, `supply`, `review`, `meta`, `mutation`, `release`, `report`, `process` |
| `status` | yes | How far the check is built (see [Statuses](#statuses-and-dormant-markers)). | `planned`, `dormant`, `partial`, `built`, `review`, `report`, `process` |
| `dormant_until` | no | The backlog item in which the code the check inspects first appears. While it is set, the check must select nothing. | a backlog item id |
| `<runner>_dormant_until` | no | The same, for one runner's half of a rule that two runners check (for example `history_dormant_until`, `inventory_dormant_until`, or `replay_dormant_until` for one part of a history check). The runner reads its own key first and falls back to `dormant_until` only when it is the rule's only runner. | a backlog item id |
| `parts_pending` | no | The parts of the rule the check does not cover yet, each naming what it waits for. A `partial` rule has at least one. | a list of text |
| `milestone` | no | The backlog item that builds the check (for a `planned` rule) or that built it. | a backlog item id |
| `reimplementation` | no | Set on the rules that apply only when reimplementing a reference program: `whole` (`A5`, `A7`, `D4`, `D10`, `E15`) or `clause` (`E25`, `G3`, `G4`, `G7`, where only one clause does). A greenfield project deletes the `whole` rows, or leaves them `planned` with a note. | `whole`, `clause` |
| `origin` | no | `guide` for the two rules the guide adds (`S9`, `E37`), which are not in the reference code base's table. | `guide` |

Fields the reference table carried that a new project does not need: its milestone vocabulary (`when`, `when_text`), its tool numbers (`tools`, T1 to T12), its review bookkeeping (`fields_reviewed`), and the per-language status columns (`status_rust`, `rust_pending`, `rust_dormant_until`) it kept while two implementations ran side by side. A project with two implementations in different languages can do the same: one `status_<language>` and `<language>_dormant_until` per language.

### Kinds

- **`Q`** (quantitative): a mechanical check decides. It must ship with a planted breach (`F5`).
- **`L`** (language-model): the model-review runner decides, against a binary rubric item that names the rule (`V1`–`V8`). No mechanical check exists; the rubric item is the check.
- **`Q+L`**: a mechanical check covers what syntax and types can see, and a rubric item covers the rest. The row's `check` names the mechanical half's runner. When a rubric item's concern becomes checkable, it is promoted to the `Q` half (`V7`).
- **`report`**: the check reports a measure as a trend and never fails the gate (`G2`). A report becomes a ratchet only through a decision record, after enough data has set its floor.
- **`process`**: no check; the rule is held by an attended routine or a script outside the gate (`R8`, `S7`, `S9`, `E37`).

### Cadence and extent

- **`commit`**: the gate runs it on every commit (or on every branch, for history rules that read a commit range).
- **`nightly`**: a scheduled run off the commit path, for checks too slow for the gate (simulation, mutation, audits, reproducible builds, restore drills).
- **`on-demand`**: at release, or in an attended review (`H9`, `P4`, `R8`, `I9`, `S7`).
- **`atomic`**: the check judges one characteristic in one place (one package, one type, one commit).
- **`holistic`**: the check judges several things together or the whole repository (`A5`, `F4`, `F5`, `E8`, `S5`, `G2`).

### Runners

The guide names five check runners. The template adds the meta-test and four owners for rules outside them.

| `check` | Runner | Sees |
| :--- | :--- | :--- |
| `architecture` | the architecture tests | build files, the package graph, syntax trees or compiled types, compiler and linter configuration |
| `history` | the history check | commits on the range, principle-file diffs, decision records, committed surface and shape files, branch limits |
| `inventory` | the inventory check | the test list, per-test timings, which named tests and shared-suite cases exist and run |
| `supply` | the supply-chain check | lock files, publish dates, advisories, two clean builds compared by hash |
| `review` | the model-review runner | the diff, against rubric items, in an isolated checkout |
| `meta` | the meta-tests among the architecture tests | the rule table against the checks: `F5` (each check selects something unless dormant, and fires on its breach alone), `I1` (each row's fields), `I6` |
| `mutation` | a nightly mutation run | surviving mutants (`E34`) |
| `release` | the release script | the release record (`H9`) |
| `report` | the nightly report | trends only (`G2`) |
| `process` | nobody: an attended routine | — |

The template assigns every row a runner: the reference's own `check` where its table names one, and otherwise the runner that fits the tools its row lists (package-graph, syntax and type tools to `architecture`; test-list tools to `inventory`; git tools to `history`; supply-chain and reproducibility tools to `supply`). Reassign freely; a rule may move to a cheaper runner at any time, which `F4` treats as neutral.

### Statuses and dormant markers

| `status` | Meaning | Can fail a commit |
| :--- | :--- | :--- |
| `planned` | No check yet. The default for every rule in a new project. | no |
| `dormant` | The check is written and has its planted breach, but the code it inspects does not exist yet; the row carries `dormant_until`. | no |
| `partial` | Part of the rule is checked; `parts_pending` lists the rest. | yes, for the built part |
| `built` | The whole rule is checked, and its planted breach proves it (`F5`). A `built` rule may also carry `dormant_until` when its target code is still missing. | yes |
| `review` | An `L` rule whose rubric item is calibrated and runs (`V4`). | yes, through a verified block (`V3`) |
| `report` | A report rule that runs. | no |
| `process` | A process rule. | no |

The lifecycle is `planned` → `dormant` (check and breach written, nothing to inspect yet) → `partial` or `built`. The history check ranks `built` above `partial` above `dormant` above every other status, and lowering a rule's rank is a loosening (`F4`).

A dormant marker is not a skip. The `F5` meta-test asserts both directions: a check without a marker must select something in the real code (otherwise it is vacuous), and a check with a marker must select nothing (otherwise the marker is stale and fails, telling you to remove it). So a rule wakes on the commit that first adds the code it inspects, and that commit must also satisfy it. Removing a dormant marker or a `parts_pending` entry is a tightening.

The guide's group tables show more detailed statuses (`built (dormant)`, `partial (replay dormant)`, `built (Q; L advisory)`). They are derived from these fields: `status` plus a `dormant_until` or `<runner>_dormant_until` marker, plus the `kind` and whether the model reviewer is advisory for that language.

## 3. `[[layer]]`: the layer table

Each row is one layer. `A1` finds a package's row by matching the path of its build file, relative to the workspace root, against the row's patterns; the first row that matches wins, and a package that matches no row fails.

| Field | Meaning |
| :--- | :--- |
| `name` | The layer: `Kernel`, `Contracts`, `Domain`, `Codec`, `Application`, `Composition`, `Infrastructure`, `Host`, `Breach`, `Test`, and, when reimplementing a reference program, `Import` and `ImportHost`. The guide's examples call the composition layer `Day`. |
| `cargo`, `maven`, `gradle`, `npm`, `msbuild` | The row's path patterns, one list per build system (`Cargo.toml`, `pom.xml`, `build.gradle(.kts)`, `package.json`, `*.csproj`/`*.fsproj`). Keep the lists for the build systems you use. In a pattern, `{context}` is a context of the context map and must be the same everywhere it appears in the pattern; `{name}` matches one path segment; `*` matches within a segment; `**` matches any depth. |
| `references` | The layers the row's packages may depend on: a layer name, or a qualified target: `Layer:own` (the same context), `Layer:upstream` (a context upstream in the context map), `Layer:any`. `*` allows every reference. |
| `packages` | Third-party packages the whole layer may use (`*` for all; only `Test` should have it). Production layers keep this empty and list each package's own allowances under `[packages]`. |

Order matters. A specific row comes before the general row whose pattern would also match (`ImportHost`'s `hosts/import/` before `Host`'s `hosts/{name}/`), and `Breach` comes before `Test`. `A2` reads the `{context}` captures and the context map to judge `:own` and `:upstream`. `A14` reads that there is one `Application` per context and at most one `Composition` package. `E3` narrows the `Test` row's `*` per kind of test package.

## 4. The word and name lists

Each list is a closed vocabulary that a check reads. The direction in which each list loosens is fixed (see [`F4`](#6-tightening-only-f4)).

- **`[words].infrastructure`, `[words].screens`** (`A9`): no word from either list in a type, module, or namespace of `Domain` or `Contracts`, or in an `Application` port. Matched as a word inside the name, so `SqlOrder` fails. Removing a word loosens.
- **`[names].vague`** (`B22`): no module, file, type, or top-level function of `Domain`, `Contracts`, or `Application` is named with, or ends in, one of these, unless the whole name is a glossary term. `OrderManager` fails. Removing a word loosens.
- **`[names].past_irregular`** (`B7`): event names end in `ed` or in one of these. Adding a word loosens, because it admits more names.
- **`[decider].leading`** (`B3`): the only parameter types a `decide` may take before its command and state, by simple type name. Adding one loosens.
- **`[banned].containers`** (`A10`), **`[banned].mocking`** (`E1`): packages that no package may depend on, named as the build file names them. Removing one loosens. The reference code base's table listed only one ecosystem's names, and its architecture check kept the other ecosystem's names in code. Keep every ecosystem's names in the table, so that the table is the whole rule.
- **`[packages]`** (`A10`): `package = ["third-party", ...]`. Every production package that uses a third-party package is listed with exactly those packages, and each third-party package appears under one wrapper only. Adding an entry loosens; its commit names the package's licence and reason (`H4`) and cites the dated approval.
- **`[primitives].allowed`** (`C4`), **`[text].keys`** (`D5`), **`[glossary].generic`** (`B4`): allow-lists. Adding to them loosens.
- **`[vocabulary]`** (`B13`, `B1`): the context map's allowed `subdomain`, `status`, `logic`, `architecture`, and `relation_kind` values. Adding a value loosens.

## 5. `[allow]`: exceptions as data

Every exception to a rule is an entry in `[allow]`, never a skip list inside a check, so that the report can count them (`S2`) and the history check can see each one arrive (`F4`).

The template has one list per reading rule: `general` (any rule whose check reads it), `wildcard` (`C12`), `custom_equality` (`B9`), `struct_constrained` (`C3`), `same_entry` (`B12`), `states_as_flags` (`C8`), `warnings_kept` (`F1`, `H5`), and `quarantine` (`E30`). An entry is an inline table:

| Field | Meaning |
| :--- | :--- |
| `rule` | The rule id it excuses. |
| `file`, `member` | The exact file and member, never a wildcard. |
| `reason` | Why, in one sentence, and when it should go away. |
| `record` | The decision record the entry relaxes (`I2`). |
| `owner_yes` | The date of the approval, `YYYY-MM-DD`, not after the commit that adds it. |
| `review_by` | Optional. The entry fails the gate once this date has passed (`I5`). |
| `expires` | For `quarantine` entries: at most `[quarantine].max_days` after `owner_yes` (`E30`). |

**How the expiring rules apply.**

- **`I2`** requires every entry to name the record it relaxes. The same lint requires that a superseded record hosts no active rule, so when a record is superseded, the entries that cite it move to the new record.
- **`I5`** fails the gate on an entry that no longer matches any violation (the code was fixed, so the exception must go), and on an entry whose `review_by` has passed. Only rules listed in `[allow].readers` read the table. An entry for a rule not listed there matches nothing, so `I5` refuses it. This stops an entry from silencing a check that never reads it.
- **`E30`** fails the gate on a quarantine entry whose `expires` has passed, so a flaky test is fixed or deleted within the window.

Adding an entry loosens, so it needs the dated approval in the commit (`F4`). Removing an entry tightens and needs nothing.

## 6. Tightening only (`F4`)

The files listed under `[f4].principle` (the rule table, the budgets, the rubrics) change only by adding or tightening, unless the commit message cites a dated approval (`Owner-yes: YYYY-MM-DD`, a real date not after the commit). The history check compares each changed principle file with its parent version, key by key. The reference code base's direction table, which the template follows (`review_by` is the template's addition):

| A change to | Loosens when | Examples |
| :--- | :--- | :--- |
| a key starting with `max`, and `revisit_from` | the number grows | `[s5].max_changed_lines`, `[structure].max_complexity`, a budget's `max_ms` |
| `target` | the number shrinks | a reliability target |
| an expiry (`expires`, `review_by`, a limit override's `*_until` date) | the date moves later | an override's end date |
| a ban or guard list (`[banned]`, `[words]`, `[names].vague`, `[f4]` paths, `[vision].technology`, `[f2].extensions`) | it loses an item | removing `Manager` from `vague` |
| an allow list (`[allow]`, `[packages]`, `[primitives]`, `[text]`, `[glossary]`, `[decider]`, `[vocabulary]`, `past_irregular`, `[s5].generated`, `[e33].approval_folders`) | it gains an item | a new allow entry |
| a rule's `status` | its rank falls (`built` > `partial` > `dormant` > any other) | `built` → `dormant` |
| `enabled` | it goes from `true` to `false` | — |
| an approval or the range start (`owner_yes`, `[range].since`) | it changes at all | — |
| any key or row | it is removed: always a loosening, except `parts_pending`, `dormant_until`, `*_dormant_until`, `milestone`, an override's `*_until` and `owner_yes`, whose removal tightens | removing a `[[rule]]` row or a budget |
| a key of unknown direction (including a changed `dormant_until`) | it changes either way; the commit must cite a decision record instead | a renamed stage, a stage budget whose key does not start with `max` |

Files under `[f4].structural` (the context map, the glossary, the policy table) change only through a commit that cites a decision record. Paths under `[f4].append_only` never lose or change a line, and the union shape files under `append_only_shapes` only gain cases at the end of a line (`P15`). Separately, `I3` requires every commit that touches the rule table to cite a decision record. So adding a rule is a tightening that needs no approval, but it still cites its record.

Stage budgets are named after their stage (`build = 40`), so the check knows no direction for them and asks for a decision record on any change; in the reference code base, each raise also cited the dated approval. A project that wants the check itself to demand the approval names them `max_build` and so on.

Two consequences of these rules matter in practice. A budget whose stage no longer exists is kept with a comment, because removing it counts as a loosening. A rule that is not checked yet stays `planned`; deleting the row would be a loosening.

## 7. The limit tables

| Key | Rule | Meaning |
| :--- | :--- | :--- |
| `[structure].max_complexity`, `max_complexity_shell`, `max_nesting` | `G3`, `A17` | complexity per function (15, and 5 in `Application`, `Composition`, `Host`) and nesting depth (4) |
| `[structure].max_function_lines`, `max_module_lines`, `max_file_lines` | `G4` | size backstops (80, 500, 600) |
| `[structure].max_parameters`, `max_same_primitive_parameters` | `G11` | parameters per public function (4) and of one primitive type (2) |
| `[structure].domain_literals` | `G7` | the unnamed numeric literals `Domain` may hold (`0`, `1`, `-1`) |
| `[structure].max_aggregate_collections` | `B25` | collection-typed fields per aggregate state; unset until the report has data |
| `[supply].min_age_days` | `H4` | minimum age of a newly pinned version (14) |
| `[range].since` | commit rules | the first commit the history rules judge; never moves later |
| `[s5].max_changed_lines`, `max_hours`, `generated` | `S5` | branch size and age, and the generated files excluded from the count |
| `[vision].max_words`, `technology` | `B26` | the vision's length and the technology words it may not name |
| `[spikes].max_days` | `S8` | spike folder age |
| `[quarantine].max_days` | `E30` | quarantine length |
| `[i2].revisit_from` | `I2` | the first record number that must carry a "Revisit when" line |
| `[f2].extensions`, `max_bytes` | `F2` | file types and sizes never committed |
| `[f4].*` | `F4`, `P15` | principle, structural, and append-only paths |
| `[e33].approval_folders` | `E33` | where approval files may live |

A dated, temporary exception to a limit (the reference code base raised `S5`'s 400 lines to 550 for branches that land a new domain package, until a set date) is a key beside the limit with its own `owner_yes` and `*_until` date, and the check reads it only until that date.

## 8. Worked example: adding a rule end to end

This example adds a rule that commands are not named as past-tense facts, the mirror of `B7`. It follows the reference code base's playbook.

**1. Pick the id.** Group B's highest id is `B27`, so the new rule is `B28`. Do not fill a gap: `B2`, `B10`, and the other missing numbers were retired.

**2. Write the decision record** (`D-0031` here) stating the rule, why, and its `Compliance` line (`Enforced by B28 (architecture, per commit)`), with a "Revisit when" trigger (`I2`).

**3. Add the row.** Adding a row is a tightening, so it needs no approval, but the commit cites the record (`I3`).

```toml
[[rule]]
id = "B28"
group = "B"
title = "Commands are intents, not facts"
kind = "Q"
cadence = "commit"
extent = "atomic"
applies_to = "Contracts, Domain"
characteristics = ["evolvability"]
family = "evans-vernon"
record = "D-0031"
check = "architecture"
status = "planned"
milestone = "BL-112"
```

**4. Write the check** with the cheapest tool that sees the thing: here a syntax walk over `Contracts` and `Domain`. It selects every type marked as a command (`M1`'s marker) and fails on a name that ends in `ed` or in a word of `[names].past_irregular`, the same list `B7` reads. The failure message names the rule id, the offending type, the allowed alternative, and the record (`F5`):

```text
select:  every type in a Contracts or Domain package marked as a Command
fail:    name ends in "ed", or ends in a word of [names].past_irregular
message: "B28: command OrderShipped in contexts/orders/contracts reads as a fact
          | instead: name the intent (ShipOrder) | D-0031"
```

**5. Plant the breach.** Add `breaches/b28/`, a tiny `Contracts` package (it matches the `Breach` layer row, so no production build sees it) with one command named `LoanRenewed`. Nothing else in it breaks any rule.

**6. Run the meta-test** (`F5`). It must show that `B28` fires on `breaches/b28` and on nothing else, that no other rule fires on `breaches/b28`, and that `B28` selects at least one command in the real code. If the project has no commands yet, the selection is empty. Then add `dormant_until = "BL-120"` (the item that adds the first command) and set the status to `dormant`. The meta-test then insists that the selection stays empty until that item lands.

**7. Run it on the real code.** Zero violations is the stance: rename the offending commands, or, for a real exception, add an `[allow].general` entry with its reason, record, and dated approval, and list `B28` in `[allow].readers`.

**8. Set the status** to `built` (or `partial` with `parts_pending`, if only part of the rule is checked) and commit:

```text
B28: commands are intents, not facts (D-0031)

Check in the architecture tests; breach breaches/b28 (a command named LoanRenewed).
```

If a rubric item covered the same concern before, mark it "now enforced by B28" (`V7`). From here on, lowering `B28`'s status or deleting its row is a loosening that needs a dated approval (`F4`).
