# The four non-code check runners: specifications

The guide names four check runners that do not inspect source code: the **history check**, the **inventory check**, the **supply-chain check** and the **model-review runner**. This document specifies each one closely enough to build it in a day or two in any language, and adds a fifth short section on the **landing script** that rule `S9` asks for.

Each specification describes what the reference project's runner does. Where this document goes beyond that, the text is marked **Guide recommendation**. Numbers such as budgets, day limits and accuracy bars are the reference project's own choices, not values taken from a published source. Choose your own, record them in a principle file (`F4`), and tighten them over time.

The rubric format, the rubric questions and calibration have their own document: [rubrics.md](rubrics.md).

## Contents

- [0. What every runner shares](#0-what-every-runner-shares)
- [1. The history check](#1-the-history-check)
- [2. The inventory check](#2-the-inventory-check)
- [3. The supply-chain check](#3-the-supply-chain-check)
- [4. The model-review runner](#4-the-model-review-runner)
- [5. The landing script (`S9`)](#5-the-landing-script-s9)
- [6. Build order](#6-build-order)

---

## 0. What every runner shares

All four runners in the reference project share one shape, and a port should keep it. The shared parts are written once here and not repeated in each section.

### 0.1 A pure judge behind a thin reader

Split each runner into two layers:

- **The reader.** This is the only code that does I/O. It calls `git`, reads files, fetches from registries or starts the reviewer model. It turns everything it reads into plain values: commits, file snapshots, test cases and pins.
- **The judge.** This is a set of pure functions, one per rule, from those values to a list of findings. A judge never reads the clock. "Today" is the commit date of `HEAD`, so the same commit gives the same verdict next week. The one exception is the supply-chain age rule, which compares against the current time and is described in section 3.

With this split, the judge can be tested on recorded input (the planted breaches, below). A reimplementation in another language can also share the same fixture files.

### 0.2 One finding per line

Every finding is one line:

```
RULE  offender  | instead: what to do  [decision record]
```

- `RULE` is the rule ID (`S5`, `E8`, `H4`, `V3`), followed by two spaces.
- `offender` names exactly what broke the rule. That may be a commit (short hash and subject), a file and line, an allow entry, a test name or a pin (`crates syn 3.0.9`).
- `| instead:` gives the allowed alternative. Write it as an instruction the author can follow, not as a restatement of the rule.
- The bracketed decision record is optional. The history check adds the record that the rule table names for the rule. The other runners leave it out.

Examples taken from the reference runners and made generic:

```
S5  3 commits change 512 counted lines (generated files excluded)  | instead: at most 400 changed lines on a branch: merge sooner, or cite the approver's override naming S5 (`Owner-yes: YYYY-MM-DD S5`)  [references/short-branches.md]
E8  loans-domain: due_dates_never_fall_on_a_closed_day took 31.2 ms, over the logic budget of 20 ms  | instead: make it faster, or move it to a `nightly` module
H4  4c1e2aa rust/Cargo.lock: crates syn 3.0.9: published 2026-11-01, 3.0 days before the commit  | instead: pin a version published at least 14 days before the commit
V8  tests/loans/due_dates.rs:41 ("check_eq!(due, day(15))") changes, deletes or moves an existing test  | instead: add tests; changing one needs the spec to name the file
```

After the findings, each runner prints one summary line, such as `history: 20 check(s); real: 0 violation(s), range a1b2c3d..HEAD, 6 commit(s); breaches: 95 fixture(s), 0 problem(s); 1840 ms`.

This format comes from rule `F5`, which requires every failure message to name the rule ID, the offending edge or member, the allowed alternative and the decision record.

### 0.3 Exit codes

| Code | Meaning |
| :--- | :--- |
| `0` | Green: no finding. |
| `1` | Red: at least one finding, or a planted breach that did not behave as expected. |
| `2` | Could not judge. The tool could not be built, `git` or a registry did not answer, or an input could not be read. |

Exit code `2` is never a pass. A gate that treats `2` as green turns an outage into a silent hole. In the reference project, the supply-chain check exits `2` when a registry does not answer. The inventory check exits `2` on input it cannot read. Every wrapper script exits `2` when its tool fails to compile.

**Guide recommendation:** treat any exit code other than `0` as red in the gate. Report `2` separately in the log, so that a person can tell "the rule broke" from "the check could not run".

### 0.4 Two modes: real and breaches

Each runner has at least two modes:

- **`real`** judges the actual repository: its commit range, test results, lockfiles or diff.
- **`breaches`** judges every planted breach and checks that each one behaves as expected. This is how the runner proves itself (rule `F5`).

The gate runs both, usually as `all`. A runner whose breaches fail is red even when the real repository is clean.

### 0.5 Planted breaches: the fixture layout

The history and inventory checks share one fixture layout, which a port can copy:

```
breaches/<runner>/
  _base/                 a small, clean, valid copy of the inputs: config files, a decision record, a history file
  <RULE>--<slug>/        overrides files of _base so that exactly RULE fires
  clean--<slug>/         overrides files of _base so that a tempting near miss is accepted
```

A fixture folder holds only the files that differ from `_base`. The reader looks in the fixture folder first and falls back to `_base` for everything else. A file named `<path>.deleted` hides `<path>` from the base. For the history check, a fixture's history is a JSON file instead of a git repository:

```json
{
  "head_date": "2026-11-04",
  "commits": [
    {
      "hash": "ccc3333",
      "date": "2026-11-04T11:00:00+00:00",
      "subject": "chore: relax the budget (ADR-0011)",
      "body": "",
      "files": [
        { "path": "standards/slo.toml", "status": "M", "added": 1, "removed": 0,
          "old": "[budget.gate_time]\nmax_seconds = 120\n",
          "new": "[budget.gate_time]\nmax_seconds = 300\n" }
      ]
    }
  ]
}
```

Commits are listed newest first. `old` and `new` may instead be `oldFile` and `newFile`, which are paths to files inside the fixture. `head_date` defaults to the date of the newest commit.

Breaches mode runs every check on every fixture and reports a problem in each of these cases:

1. `_base` fires anything. The base must be clean.
2. A `<RULE>--<slug>` fixture fires nothing for `RULE` (`<name>  fired nothing for <RULE>`).
3. A `<RULE>--<slug>` fixture also fires another rule (`<name>  also fired <finding>`). A breach must isolate its rule.
4. A `clean--<slug>` fixture fires anything (`<name>  (clean) fired <finding>`).
5. A rule the runner holds has no fixture (`<RULE>  has no breach under <dir>`).

The supply-chain and model-review runners keep their planted cases in code instead: a list of `(name, expected rule or none, findings)`, checked by the same five conditions. Either form works. Files are easier to share between implementations in different languages.

**Guide recommendation:** write a `clean--` twin for every breach that guards a threshold. A 400-line limit gets a 401-line breach and a 400-line clean twin. A date cut-off gets a day-after breach and an on-the-day twin. The reference project's history check has clean twins for its branch-size limits (`S5`), its shape-file insert (`F4`) and its Rust test rename (`E23`). They catch off-by-one errors and checks that fire on everything.

### 0.6 Selection and dormancy (`F5`)

A check that selects nothing passes vacuously. In real mode, each runner therefore also reports two findings about itself:

```
<RULE>  selects nothing on the real repository  | instead: mark it dormant with the milestone its subject appears in, or fix the selection  [F5]
<RULE>  is marked dormant until <milestone> but selects <n> item(s)  | instead: remove the dormant marker  [F5]
```

Each check has a `select` function next to its `find` function. `select` returns what the check looked at: commits, files, entries or test cases. Dormancy is read from the rule table, not hard-coded. The reference project uses per-runner keys (`history_dormant_until`, `inventory_dormant_until`, `replay_dormant_until`). These let one rule have an active architecture-test half and a dormant history half.

One exception exists. A commit rule (one that reads only the commit range) that selects nothing because the range is empty is not flagged. A branch with no new commits is not a vacuous rule.

### 0.7 Configuration lives in a principle file

Every number and list a runner uses lives in a committed principle file, protected by `F4`: the history config, the reliability-targets file and the rule table. The runner also has the same defaults in code, so a fixture needs no config file. Because the config is a principle file, loosening it (raising a limit, dropping a protected path) is itself a history-check finding unless the commit cites a dated approval.

**Pitfall from the reference project:** test every glob in the config against a real path. One append-only glob there was written relative to the wrong folder and never matched the files it was meant to protect. Nobody noticed, because no check flags a glob that matches nothing. **Guide recommendation:** make each list-valued config key subject to the selection rule in 0.6, so that a glob matching no tracked file is a finding.

---

## 1. The history check

### 1.1 Purpose

The history check inspects git history and the repository's governance files: commits, principle-file diffs, decision records, committed public-surface files and branch limits. It holds the rules that no compiler or syntax tree can see, because they are about *how* a change was made rather than what the code says.

### 1.2 Rules held

The reference runner holds 20 rules. Three kinds of input feed them:

- **Range**: the commits of the branch, read from git.
- **Tree**: the files at `HEAD`.
- **Both**: the files at `HEAD` and the commits.

| Rule | What it checks | Reads |
| :--- | :--- | :--- |
| `B24` | Commits touching the shared kernel run the full gate; kernel public-surface changes are flagged for review | Range |
| `B26` | The product vision exists, is short and names no technology; changes to it cite a decision record | Both |
| `B27` | A core context's first aggregate lands with its property-test list | Both |
| `E12` | Feature and fix commits that change core behaviour bring a test (the replay half is a separate script, 1.6) | Range |
| `E23` | Refactoring commits change no assertion | Range |
| `E30` | Quarantine entries are complete and expire within 7 days | Tree |
| `E33` | No `*.received.*` files; approval files only in approval folders | Tree |
| `F2` | No proprietary material: banned file extensions and a size cap on tracked files | Tree |
| `F4` | Principle files only tighten; structural files need a decision record; append-only files only grow | Range |
| `F6` | Every feature flag has a removal date that has not passed | Tree |
| `I2` | The decision-record lint | Tree |
| `I3` | Structural commits cite a decision record | Range |
| `I5` | Allow entries are complete, not stale and not lapsed | Tree |
| `R1` | The reliability-targets file holds the agreed targets, windows and minimum samples | Tree |
| `R5` | Fix commits carry a `Prevents:` line; postmortems link what they promoted | Both |
| `S1` | Every backlog item (spec) names what it serves | Tree |
| `S2` | The health report prints every required field | Tree |
| `S4` | Specs that add a module, context or wrapper hold two interface sketches | Tree |
| `S5` | Branches stay under 400 changed lines and 24 hours | Range |
| `S8` | Spike folders are dated and deleted after 14 days | Tree |

The rule IDs are the guide's. In your project, drop the rows for rules you do not adopt, and keep the `F5` machinery (0.4 to 0.6) for the rows you keep.

### 1.3 Inputs

- **The commit range** (see 1.4), newest first. Merge commits are skipped. For each commit, read:
  - the hash;
  - the committer date (ISO 8601), never the author date or the wall clock;
  - the subject and the body;
  - the changed files. For each file, read its path relative to the project root, its status (`A`, `M`, `D` or `R`), the lines added and removed, and the old and new content. Read the content lazily, only when a check asks for it.
- **The parent's copy of the history config**, for each commit (`F4` reads its protected-file lists from the parent; see 1.5).
- **A snapshot of the tree at `HEAD`**, with three functions: `read(path)`, `list(folder)` (recursive) and `size(path)`. The snapshot also holds the list of tracked files (`git ls-files`).
- **The `HEAD` date**: the committer date of `HEAD` itself, which every date rule uses as "today". It is present even when the range is empty.
- **Committed governance files:**
  - the rule table;
  - the history config;
  - the allow file;
  - the reliability-targets file;
  - the flag list;
  - the context map;
  - the decision records and their index;
  - the specs;
  - the vision document;
  - the public-surface files.

The `git` calls of the reference reader are listed below. Any git library gives the same data.

```
git log --no-merges --no-renames --format=<fields separated by \x1f, commits by \x1e> --raw --numstat --relative <since>..HEAD -- .
git show <hash>:./<path>          new content
git show <hash>^:./<path>         old content, and the parent's history config
git ls-files                      tracked files
git log -1 --format=%cI HEAD      HEAD date
git merge-base HEAD <main>        range start
git merge-base --is-ancestor A B  range start
```

`--no-renames` makes a move show as a delete plus an add, so a moved file cannot carry a principle-file edit past `F4`. `--relative` gives paths relative to the project folder when it is a subfolder of the repository.

### 1.4 The range: which commits are judged

The commit rules judge only unmerged work, and only commits made after the rules existed.

1. Read `since` from the history config (`[range] since = "<hash>"`). It is the commit just before the history check's own first commit. `F4` treats it as a grant (1.5), so moving it needs a dated approval.
2. Compute `mb = merge-base(HEAD, <main branch>)`.
3. The range start is:
   - both present: `mb` if `since` is an ancestor of `mb`, else `since`. In other words, the later of the two.
   - only `since`: `since`.
   - only `mb`: `mb`.
   - neither: stop with an error ("no range: set `since` in the history config, or fetch the main branch"). The run is not green.
4. The range is `start..HEAD`. A `--since <rev>` argument overrides step 1.

**Shallow clones and missing history.** In the reference project the reader fails loudly in two cases: when `git log start..HEAD` fails (for example, `since` is not in a shallow clone), and when there is no range at all. It never falls back to an empty range. The CI job fetches the full history (`fetch-depth: 0`) for this reason. An empty range (a branch with no new commits) is fine: the tree rules still run, and the commit rules have nothing to judge (0.6).

**Guide recommendation:** make "could not compute the range" exit `2` with a one-line message, rather than an unhandled exception. In CI, fetch the full history, or at least deep enough to reach both the merge base and `since`. Do not fall back to "judge only `HEAD`". That silently skips every commit of a stacked branch except the last one.

### 1.5 Algorithms, rule by rule

The steps below use these terms:

- *A commit's type* is the conventional-commit prefix of its subject (`feat`, `fix` or `refactor`, matched by `^([a-z]+)(\([^)]*\))?!?:`).
- *A trailer* is a `Key: value` line anywhere in the message.
- *Cites a record* means the message contains a decision-record ID. The reference uses `N-\d{4}`; use your own ID pattern.
- *A dated approval* is a trailer `Owner-yes: YYYY-MM-DD [scope ...]` whose date is a real date no later than the commit's own date. A value that is not a date counts for nothing. A date in the future counts for nothing. A yes with no scope, or with scope `F4`, counts for `F4`. Every other rule needs its own ID in the scope (`Owner-yes: 2026-11-04 S5`), so a yes given for one purpose never lifts another rule.

**`B24` — kernel commits.** For each commit that touches the kernel's source folder:
1. If no `Gate:` trailer starts with `full`, report it ("a commit touching the kernel runs the whole gate").
2. If it also touches the kernel's public-surface file and has no `Review:` trailer, report it ("flag the surface change for review").

**`B26` — the vision.**
1. If the vision file is missing, report it.
2. If its word count (runs of non-whitespace) is above `max_words` (400), report it.
3. For each word in the `technology` list, match case-insensitively with word boundaries that treat `.` and `#` as part of a word. Report each match.
4. For each commit in the range that touches the vision file and cites no record, report it.

**`B27` — a core aggregate lands with its property list.**
1. From the context map, take the contexts with `subdomain = "core"` and a status of implemented.
2. For each such context, walk the range oldest first. Find the first commit that changes a source file of that context so that the new text contains the aggregate-root marker and the old text did not. In the reference project the marker is a regular expression for implementing the aggregate-root trait.
3. If such a commit exists, require that some commit at or before it *adds* a test file whose path contains the context's name and `property` or `properties`. If none does, report the first commit.

**`E12` — red before green (history half).** Report each commit that meets all four conditions:
- its type is `feat` or `fix`;
- it changes a behaviour path (kernel, domain or application source);
- it changes no test source;
- it has no `E12-accepted: <reason>` trailer.

The parent-commit replay is a separate script (1.6).

**`E23` — refactoring leaves assertions alone.**
1. For each commit of type `refactor`, and each changed test-source file, extract the *assertion skeleton* of the old and new text. The skeleton is the list of every assertion call with its whole argument list. Find each call by its opening bracket and its matching close, skipping brackets inside string literals, and collapse whitespace.
2. If the old and new skeletons differ, report the commit and the file.

Comparing skeletons, rather than raw lines, lets a refactoring rename a test or reshape its setup without a finding, while any change to what is asserted fires. The clean twin renames a test function and leaves its assertion alone. Note the trade-off: a compiler-forced rename *inside* an assertion (a renamed production function) changes the skeleton and fires. **Guide recommendation:** either accept that false positive (and give the rename commit another type), or normalise identifiers the same commit renamed in production code before you compare skeletons.

**`E30` — quarantine entries.** For each `[[quarantine]]` row of the allow file:
1. Each of `test`, `reason`, `owner_yes` and `expires` must be present.
2. `expires − owner_yes` must be at most `max_days` (7).
3. `expires` must not be before the `HEAD` date.

**`E33` — snapshot hygiene.** For each tracked file outside the breaches folder:
1. If the file name contains `.received.`, report it.
2. If it contains `.approved.` and no path segment is one of `approval_folders`, report it.

**`F2` — no proprietary material.** For each tracked file:
1. If its extension, lowercased, is in the `extensions` list, report it.
2. If its size is above `max_bytes` (2 MiB in the reference project), report it.

A separate pre-commit script covers files that are staged but not yet committed.

**`F4` — principle files only tighten.** This is the largest check. For each commit in the range:
1. **Choose the lists to judge by.** Parse the history config three times: from the commit's parent, from the commit's old version and from its new version (the old and new versions exist when the commit touches the config). Take the union of the `principle`, `structural`, `append_only` and `append_only_shapes` lists. The union stops one commit from dropping a file from a protected list and loosening that file at the same time.
2. **For each changed file, classify it** by the first list it matches: shape file, append-only file, principle file, or none.
3. **Shape files** (a line per persisted union, `Type: CaseA, CaseB`). If the new text does not only append to the old, report it twice: once as "needs a dated approval" and once as "needs a decision record". Appending means three things:
   - every old line's key appears in the new file in the same order;
   - each old line's cases are a prefix of the new line's cases;
   - new lines may be inserted anywhere.

   Deleting the file needs a dated approval.
4. **Append-only files** (review cases, golden files, corpora, migrations). Any removed line, or a deletion of the file, needs a dated approval.
5. **Principle files** in TOML or JSON. Flatten old and new into `key.path → value`, labelling array-of-table rows by their `id` or `name` field (`rule[V3].status`). Then compare:
   - A key that existed and is now gone means "removed", which needs a dated approval. The exception is keys whose removal tightens: `*_pending`, `*dormant_until`, `milestone`, `override_until`, `owner_yes`.
   - A changed value is judged by the direction table below.
   - A new row in an exception table (`allow`, `quarantine`) needs a dated approval. A new layer row needs a decision record.
6. **Principle files in other formats** (Markdown rubrics). Any removed line needs a dated approval.
7. **Structural files** (context map, glossary, policy table). Any change needs a cited record. A context-map change that alters a context's subdomain class, adds a context or removes a relation must cite a record that *exists* among the decision records.
8. **Decide.** A change that needs a dated approval is reported unless the commit carries one (scope empty or `F4`). A change that needs a record is reported unless the commit cites one.

The direction table: how each key loosens. It is matched on the key's last segment first, then on its top-level table.

| Key pattern | Loosens when | Needs |
| :--- | :--- | :--- |
| `max*`, `depth`, `revisit_from` | the number grows | dated approval |
| `target` | the number shrinks | dated approval |
| `override_until`, `*_until`, `remove_by`, `expires` | the date moves later | dated approval |
| `owner_yes`, `*_yes`, `since` | any change (a grant) | dated approval |
| `enabled` | `true` → `false` | dated approval |
| `status*` | rank drops (`built` 3 > `partial` 2 > `dormant` 1) | dated approval |
| `principle`, `structural`, `append_only*`, `technology`, `extensions`, banned-word lists | an item is dropped | dated approval |
| `generated`, `boilerplate`, `approval_folders`, `allow`, vocabulary and primitive allow lists | an item is added | dated approval |
| `game_paths`-style include lists | a path is dropped, or a new `!` exclusion appears | dated approval |
| anything else | any change (direction unknown) | decision record |

An unparsable new file needs a decision record. **Guide recommendation:** add a row to this table whenever you add a numeric key to a principle file. An unknown key falls through to "needs a record", which is safe but noisy.

**`F6` — flag expiry.** For each flag in the flag list:
1. If it has no `remove_by` date, report it.
2. If `remove_by` is before the `HEAD` date, report it ("remove the flag and the code behind it").

**`I2` — the decision-record lint.** For each record file (`decisions/<prefix>-NNNN-<slug>.md`):
1. The index's decisions section links it. Only that section counts; a link elsewhere in prose does not.
2. It has `## Context`, `## Decision`, `## Consequences` and `## Compliance` sections.
3. Its first heading starts with `# <ID>`, and its front matter has `type: Decision`.
4. From number `revisit_from` (11) on, it has a `Revisit when` line.
5. `Compliance` names at least one existing rule ID, or says `manual: <reason>; review by YYYY-MM-DD`. Report each ID-shaped token that is not a rule, for groups that exist in the rule table.
6. Supersede links are symmetric. If A has `supersedes: B`, then B must exist and have `superseded_by: A`, and the reverse.
7. A superseded record is no longer the record of any rule in the rule table.

Then, for each allow entry, its `record` must name an existing decision record.

**`I3` — structural commits cite a record.** For each commit that cites no record, report it if any changed file is one of:
- the context map;
- the rule table;
- a file of a `Contracts` package;
- a package manifest whose dependency lines changed.

For the manifest case, compare the set of dependency lines before and after; reformatting does not count. Files under `spikes/` are ignored.

**`I5` — allow entries.** For each `[[allow]]` row:
1. It names a `target`, and the target still exists. A target is either a file path or a qualified function name; for the latter, a source file must declare a module ending with the qualifier and a function of that name.
2. Its rule is in the config's `readers` list, the rules whose checks actually read the allow file. An entry for any other rule matches nothing and is stale from its first day.
3. It has `rule`, `reason` and `owner_yes`.
4. Its optional `review_by` date has not passed.

For each quarantine row, the named test must still exist in the test sources.

**`R1` — reliability targets.**
1. For each required budget, its field must be present and at least as strict as the agreed value. The reference project's budgets:
   - crash-free sessions `>= 0.999`;
   - save-and-load success `>= 1.0`;
   - flaky gate runs `<= 0.01`;
   - gate time `<= 120` s;
   - two zero-tolerance divergence counts `<= 0`.
2. Each non-zero budget has a `window` matching exactly one of `N days`, `the last N gate runs` or `each run`, and a whole `min_sample >= 1`. A window of `banana` once passed a looser check; the regular expression is there for that reason.

**`R5` — defects promote a check.**
1. For each commit of type `fix`, require a `Prevents:` trailer. A value that is empty, or that starts with `none` and is shorter than 12 characters (`none` with no reason), is reported.
2. For each postmortem file, require a `Promoted: <rule ID or path>` line that resolves to a rule ID or a tracked file.

**`S1` — backlog items serve something.** For each spec file, require a `**Serves.**` paragraph. Report it if:
- it holds no ID-shaped token; or
- a token does not resolve (a decision-record ID that has no file, or a rule ID not in the rule table).

**`S2` — the health report.** Read the source of the health-report program. Collect the strings it actually prints; in the reference project these are the string literals passed to its output call, not comments. Report each required field that no printed string contains. The required fields are: rule count, allow entries, tool count, gate time, review time, public types, save size, red-to-green and toil.

**`S4` — design it twice.** For each spec whose `**Adds.**` line names a module, context or wrapper, count the numbered items under its alternatives section. Report it if there are fewer than 2.

**`S5` — short branches.** This rule is judged over the whole range at once.
1. **Count lines.** For each change, count `added + removed`. Skip files matching `generated`, and skip `boilerplate` files once `boilerplate_yes` is a date no later than `HEAD`. A fix-up commit counts again; changes are summed, not netted.
2. **Apply a recorded override.** A dated override in the config (`owner_yes`, `override_until`) excludes a commit's changes outside *protected paths* when the commit is dated on or before `override_until`. Protected paths (`game_paths` in the reference config) always count.
3. **Pick the limit.** It is `max_changed_lines` (400), unless a raised limit applies: the reference project's 550 for a branch that lands a new domain package. The raised limit applies only when all of the following hold:
   - the approval date is no later than `HEAD`;
   - every commit is dated on or before its end date;
   - the branch adds a manifest matching the landing pattern, the manifest is still tracked at `HEAD`, and no commit deletes one. A move, or an add-then-delete, lands nothing.
4. **Report** if counted lines exceed the limit.
5. **Report** if the time from the oldest to the newest commit exceeds `max_hours` (24). The hours rule applies unless every commit is covered by the recorded override and none touches a protected path.
6. A trailer `Owner-yes: <date> S5` in any commit of the range lifts both limits for the branch.

**`S8` — spikes.** For each folder directly under `spikes/`:
1. Its name must start with `YYYY-MM-DD-`.
2. Its date must be no more than `max_days` (14) before the `HEAD` date.

**Guide recommendation:** port the rules in this order: `S5`, `F4`, `I3`, `R5`, `E23`, `I2`. They catch the commonest model shortcuts:
- growing branches;
- loosening a limit to get green;
- landing structure without a decision;
- fixing without a guard;
- editing assertions while "refactoring";
- writing decision records nobody can find.

### 1.6 The red-before-green replay (`E12`, second half)

The reference project keeps the replay in a separate script because it builds code. For each commit of the range whose type is `feat` or `fix` and that changes a behaviour path:

1. Create a fresh worktree at the commit's parent.
2. Bring in the commit's new or changed tests, keeping the parent's production code.
3. Build, then run the tests. The verdict is one of:
   - `red`: the tests fail by an assertion;
   - `OnlyCompileError`: the build fails;
   - `NoFailingTest`: the tests pass;
   - `NotAnAssertion`: any other failure, such as a crash or a missing runner.

Only `red` passes. The other verdicts block unless the message carries `E12-accepted: <reason>`.

The replay must tell an assertion failure from a crash. In the Rust workspace, `cargo test` exits `101` for both a failed assertion and an unhandled panic, so the replay is dormant there (`replay_dormant_until`). **Guide recommendation:** have the house assertion macro print a fixed marker line on failure, and treat "exit non-zero with the marker" as `red` and "exit non-zero without it" as `NotAnAssertion`.

### 1.7 Cadence

`commit`. The history check is a stage of both the full gate and the quick inner-loop script. It runs on every commit locally and on every push in CI. Its budget in the reference project is 10 s. The F# gate measured it at 5.3 to 6.6 s on a CI runner, compiling included.

### 1.8 Planted breaches

The reference project has 95 fixtures over one `_base`. Among them:

- 7 clean twins;
- at least one breach for each of the 20 rules;
- 23 for `F4` alone. These cover loosening, a dropped rule, a future-dated yes, a junk yes, a yes for another rule, moving `override_until` later, list shrinkage, a shape reordering, an unknown key and more;
- 12 for `S5`. These cover 401 boilerplate lines against a 400 twin, 551 lines against a 550 twin, a moved package, an add-then-delete, the raised limit after its date, and a protected path inside the override.

In addition to the fixture checks, the breaches mode runs a small table of path-join cases (Windows separators, `..`, escaping the root). The reader's path arithmetic must give the same answer on every OS.

### 1.9 Performance notes

- Read file contents lazily. Most checks need only paths and line counts; `git show` per file per commit is the expensive part.
- A single `git log --raw --numstat` call gives paths, statuses and counts for the whole range.
- Parse each principle file once per commit version, not once per check.
- Keep the judge free of I/O so the 95 fixtures run in memory. In the reference project, all fixtures plus the real range run well inside the 10 s stage budget.

---

## 2. The inventory check

### 2.1 Purpose

The inventory check judges the *test run* rather than the code. It checks four things:

- every test ran within its tier's time budget;
- every test package's whole run fit its budget;
- every package that has tests actually reported;
- every case of the shared, language-neutral behaviour suite ran as a test.

### 2.2 Rules held

| Rule | What it checks |
| :--- | :--- |
| `E8` | Per-test tier budgets, per-package total budgets, every tested package reported, every reported package has a tier |
| `E36` | Every case file of the shared behaviour suite is run by a test named `spec/<case>` |
| `E11` (one implementation only) | Test names: no `Method_Scenario_Result`, no leading `should`, no public function name of the code under test |

In the reference project, the Rust workspace holds `E8` and `E36` in this runner. Its `E11` name check reads the source instead (an architecture check), because a Rust test's reported name is its function path.

`E8` also has a gate-level half: per-stage budgets and the wall-time ratchet. In the reference project that half lives in a separate pipeline judge, not in the inventory runner. The guide's rule table attributes both halves to the inventory check. It is specified here (2.5) so the two can be built together.

### 2.3 Inputs

- **Timing summaries.** One JUnit-style XML file per test package, `<results dir>/<package>.junit.xml`, written by the gate's test stage. Each `<testcase>` gives:
  - `name`: the test's full reported name;
  - `time`: decimal seconds;
  - `start_ms` (optional, a custom attribute): the start time in milliseconds since the epoch.

  Read every `<testcase .../>` element wherever it sits, so that both a house runner's one-element-per-line output and another runner's single-line file are accepted.
- **Tier budgets** from the reliability-targets file:

  ```toml
  [tiers."loans-domain"]
  tier = "logic"
  max_ms = 20            # most one test may take

  [tiers."architecture-tests"]
  tier = "architecture"
  total_max_s = 10       # most the whole package's run may take
  ```

- **The expected packages.** These are the workspace members that have tests: a `.rs` file under `tests/`, or a `#[test]` in `src/`. In another language, read the build system's list of test projects. A recorded run (a fixture) lists them in `packages.txt` instead.
- **The shared suite's case files**, below `spec/`. A file is a case when it is:
  - any file below `scenarios/` except Markdown and `.gitkeep`; or
  - exactly `recordings/<seam>/cases/<file>.jsonl`.

  A recording's README, manifest, coverage, tables and worlds are not cases.
- **Dormancy markers**: `inventory_dormant_until` per rule in the rule table.

### 2.4 Algorithms

**Parsing.** Convert `time` to whole microseconds with exact decimal parsing: `0.0015` becomes 1,500 µs. Reject exponents and signs. Do not use floating point; a budget at exactly 20 ms must compare exactly. Decode the five XML entities in names.

**Nightly tests.** A test whose name has a *module* segment exactly equal to `nightly` (`nightly::every_day_round_trips`, `a::nightly::b`) is excluded from every budget, because the gate does not run it. The last segment does not count: a test function *named* `nightly` is not exempt, and neither is a module `late_nightly`. A separate source check refuses other names that merely say "nightly", so the two agree.

**`E8`:**
1. For each expected package with no summary file, report "has tests but wrote no timing summary".
2. For each package that reported but has no tier, report "has no tier".
3. For each non-nightly test over `max_ms`, report the test, its time, the tier name and the budget.
4. If the package has `total_max_s`, compute its elapsed time. When every case has a start time, use the wall span: the latest end minus the earliest start, because tests run in parallel and their sum overstates the time. Otherwise use the sum of the times. Report the package if the elapsed time is over budget.

**`E36`:** for each case file `c`, if no test name contains `spec/<c>`, report `spec/<c> is not run`.

**`E11`** (on an implementation whose reported names are free text). Take the last dotted segment of the name. Report it if any of these holds:
- it matches `^[A-Za-z0-9]+_[A-Za-z0-9]+_[A-Za-z0-9]+$`;
- it starts with `should` (case-insensitive);
- its first word is a public function name from the public-surface files of the packages this test package references. The kernel is excluded, because its utility names may appear in test names.

**Real mode also reports:**
- `no timing summary` when the results folder holds no summary at all. This is an empty run, not a pass.
- the `F5` selection lines (0.6). `E8` selects the test cases; `E36` selects the case files.

It also writes the ten slowest tests to `slowest.txt`, which the gate prints after its stages.

### 2.5 Stage budgets and the wall-time ratchet (`E8`, gate half)

This half is held by the reference project's pipeline judge. Its inputs are:
- the gate's stage times for this run (`name → ms`);
- the stage budgets from the reliability-targets file (`[stages.<track>]`);
- the gate's total budget;
- a history file of earlier runs, one tab-separated line per run: `stamp, tree hash, green|red, name=ms,..., reason`.

**Budgets:**
1. Look up each stage's budget, first by its name and then by its family (the part before the first `.`, so `quick.loans` takes `quick`). A stage with no budget is a finding.
2. A stage over its budget is a finding.
3. A gate total over the total budget is a finding.

**Ratchet:**
1. For each stage, take its times from the last 20 *green* runs.
2. Judge only when there are at least 5 earlier green runs.
3. Report the stage if this run's time is more than 10 % over their median *and* at least 1 s over it, unless the author named that stage in a `NEXT_SLOWER="stage: reason; stage: reason"` variable. The 1 s floor sits above the measured noise of a shared machine.
4. Judge the total the same way, against earlier green runs that had exactly the same set of stages. Twelve stages each 0.9 s slower pass every stage's floor but not the total's.

**Tracks.** Keep separate budget tables and histories for each environment: local warm, local cold, CI warm and CI cold. A cold build judged against warm medians fails. Worse, if recorded, it loosens the medians. The gate picks the cold track when build outputs are missing.

**Flaky share (`R1`).** The share of runs that went red on a tree on which a later run went green with no change. Runs the judge itself turned red (budgets, ratchet) are prefixed `judged:` and do not count. The share is judged over the configured window, and only once the window holds `min_sample` runs.

### 2.6 Cadence

`commit`. The inventory runs as a gate stage right after the test stage, on the summaries that stage wrote. The reference project's budget is 5 s locally and 10 s on CI; it measured 3.7 to 4.5 s on CI. The stage budgets and ratchet are judged at the end of every gate run, and the run is recorded green or red either way.

The inventory check needs no git history, so shallow clones do not affect it. The ratchet needs its *own* history. On CI the reference project carries the history file in the CI cache: every run restores it, and only the scheduled nightly runs save it, so there is one consistent history. A branch's first CI run starts with fewer than 5 green runs, and its ratchet does not yet judge.

### 2.7 Planted breaches

The reference project's Rust workspace has these fixtures, in the 0.5 layout with a `results/` folder per fixture:

- `E8--slow-test`: one test over the logic budget.
- `E8--total`: a package over its total.
- `E8--no-tier`: a package with no tier.
- `E8--package-missing`: `packages.txt` names a tested package with no summary.
- `E8--nightly-name`: a test function named `nightly` and a module `late_nightly`, both over budget, neither exempt.
- `E36--case-not-run`: a scenario file that no test runs.
- `E36--recording-not-run`: a recording case file that no test runs. `_base` holds a recording's non-case files (README, manifest, table), so a base that fires nothing proves they are not selected.

An architecture test runs the fixtures with the "fires only its own rule" check. The breaches script also runs one *real* planted test, which sleeps past the logic budget, through the house test module and the real summary writer, and judges it against the base. This proves the whole path from the test runner to the finding, not only the judge.

The pipeline judge's budget, ratchet and flake cases are planted in code in the same way. Among them: a 10 % slowdown under the 1 s floor (clean), twelve stages each 0.9 s slower (the total fires), and a named `NEXT_SLOWER` reason (clean).

### 2.8 Performance notes

The judge is linear in the number of test cases. Parsing is the cost: read each XML file once with a streaming or substring reader, not a full DOM, when there are thousands of cases. Integer microseconds avoid floating-point comparisons at the budget edge.

**Guide recommendation:** give a shared CI runner 2.5 to 4 times the local time, because its tests run that much slower. Fix a slow test by profiling it; never cut cases, raise a per-test budget or move the test to another tier to get green (see the guide's Section 5).

---

## 3. The supply-chain check

### 3.1 Purpose

The supply-chain check keeps third-party code from entering the build too young, unaudited or irreproducible.

### 3.2 Rules held

| Rule | Part | Cadence in the reference project |
| :--- | :--- | :--- |
| `H4` | Every version a commit pins was published at least 14 days before the commit; each new dependency entry is named in the commit message with its licence, and has a reason | `commit` (gate stage, with cached dates) and `nightly` |
| `H5` | Audit all packages, transitive ones included; high and critical advisories fail | `nightly` |
| `H6` | Two clean release builds at different paths give byte-identical artefacts | `nightly` |

The rule table's `H4` is `partial`: when the toolchain vendor's release metadata cannot be reached from the development environment, a toolchain bump exits `2` and is judged only on CI.

### 3.3 Inputs

- **The commit range**, the same as the history check's (1.4): `since` from the history config, through `HEAD`, oldest first. For each commit, read its hash, committer date, message, changed files (against its *first parent*, so that merges list files), and the old and new text of each pin file.
- **Pin files**, the files that fix a version:
  - package lockfiles;
  - the tool manifest;
  - the toolchain file;
  - the SDK file;
  - the versions table, which pins tools outside lockfiles, such as the mutation tester and the audit tool;
  - the CI workflow's actions.
- **Registries**: the package registry's version endpoint for each ecosystem, the toolchain's release channel files, and `git ls-remote` plus a shallow fetch for CI actions.
- **The dependency register** (`dependencies.toml`): one entry per third-party package, with `licence` and `reason`.
- **Advisory databases**, through the ecosystem's audit tool. Pin the audit tool's version in the versions table and install it if missing.
- **A date cache**, `.gate/published.tsv`: `registry, name, version, ISO date`. It is not committed. A published date never changes, so caching it is safe.

### 3.4 Algorithms

**`H4`, age.** For each commit in the range, oldest first:
1. For each changed pin file, parse the old and new text into sets of `(registry, name, version)`. The pins *added* by the commit are `new − old`. A version bump adds the new version only. A project or path dependency (one without a registry source) is not a pin.
2. For each added pin, find its publication date:
   - Look in the cache first.
   - Otherwise ask the registry. A 404 means "no date" (the registry does not know the version). A timeout, a 5xx or a non-JSON body means "unreachable".
   - A date before 2000 is "no date". One registry dates unlisted versions to 1900; an unlisted version has no real publication date.
   - For a CI action, the pin must be `owner/repo@<40-hex SHA> # <tag>`. Run `git ls-remote` on the tag and its peeled form. If the tag does not name that SHA, the result is "no date". Otherwise fetch the tag at depth 1 and take the latest of its tagger date and commit dates. An action pinned by tag alone, or a docker image, is "no date" (unpinned).
   - Cache only real dates.
3. Judge the pin. Let `commit_date = min(commit date, now)`, so a commit dated in the future proves no age. Then:
   - unreachable gives no verdict, but is listed as "could not judge";
   - no date is a finding;
   - `commit_date − published < 14 days` is a finding. A version published after the commit is negative and therefore a finding.

**`H4`, licence and reason.** For each non-merge commit that changes the dependency register, look at each *new* entry, matched by section and name:
1. Some single line of the commit message must name the package as a whole word together with its licence. The licence is the SPDX text, or the same text with ` OR ` written as `/`.
2. The entry's `reason` must not be empty.

A merge is skipped because its branch's own commits were judged.

**Cut-over for newer parts.** The action-pin and licence parts were added after the age rule. They judge only commits after the commit that introduced them, which is a constant in the runner. If git cannot list that commit (a shallow clone, or a rewrite), no commit is judged by those parts and the run reports "could not judge" (exit `2`).

**Exit for `age`:** `1` if any finding; otherwise `2` if any pin could not be judged; otherwise `0`. An unreachable registry is never a pass.

**`H5`, the audit.**
1. Restore every project with the audit enabled over *all* packages. Make high and critical advisories errors and keep low and moderate ones as reported notes.
2. If the audit feed did not answer, exit `2`.
3. Any high or critical advisory: exit `1`.
4. For the Rust workspace, run the pinned audit tool in JSON mode over the lockfile. Any vulnerability exits `1`. No JSON (the advisory database could not be fetched) exits `2`.
5. Run the same audit over the planted vulnerable project or lockfile (3.6). It must fail. If it does not, the run is red.

The gate's own restore also audits, but as warnings, so that an advisory published overnight does not break an unrelated daytime commit. The nightly run turns them into errors.

**`H6`, reproducible builds.**
1. Create two fresh worktrees of `HEAD` at different paths. The reference project uses names of different lengths (`one`, `two-two`), so a baked-in path also shifts offsets.
2. In each, run the *release* build script itself. It must remap source paths and go offline after fetching.
3. In each, list `name sha256` for every library and every shipped binary. Skip build metadata files and the binaries of test-only tools, which may embed their own folder on purpose. Sort the list.
4. If the first build fails, exit `2`. If the second fails, exit `2`. If nothing was built, exit `2`.
5. If the two lists differ, report each differing artefact (`H6  differs: <artefact>`) and exit `1`.
6. Remove both worktrees however the run ends.

### 3.5 Cadence and history

- **`H4` age** runs at `commit` as a gate stage after its planted cases. With the date cache warm it takes about a second. A pin the cache has never seen costs one registry call. It also runs nightly.
- **`H5`** and **`H6`** run only `nightly`. They need the network and minutes of build time.
- **Shallow clones.** `H4` needs the range from `since`. If `git log since..HEAD` fails, the runner exits `2`. If the cut-over commit is missing, the newer parts report "could not judge" and the run exits `2`. Fetch the full history in CI.
- **No network.** The age check exits `2`. Do not let a developer's offline laptop or a restricted sandbox turn this into green; the gate must show the run as not judged.

### 3.6 Planted breaches

- **`H4`.** About 19 planted cases of the pure judgement, in code, with clean twins:
  - published 3 days before (fires); 14 days before (clean); after the commit (fires);
  - no date (fires); an unlisted version dated 1900 (fires); a commit dated 60 days in the future (judged at now, fires);
  - an unreachable registry (no verdict);
  - a lockfile bump adds only the new version; a project reference and a path crate are not pins; an unchanged file adds nothing (clean);
  - the action parser: SHA plus tag; tag only; quoted; a docker image; a local action and a commented-out line, which are not pins;
  - the versions-table and toolchain parsers;
  - a new dependency with no licence in the message (fires); its name inside another word, or its licence on another line (fires); no reason (fires); named with `MIT/Apache-2.0` for `MIT OR Apache-2.0` (clean).
- **`H5`.** A real project pinned to an old version with a known high advisory, and a real lockfile pinned to an old crate with a published advisory. Each must make the audit fail. These are the only breaches that need the network.
- **`H6`.** The reference project has **no** planted breach for `H6`. **Guide recommendation:** add one. Build a tiny fixture package whose build embeds its absolute source path or the build time, and require the comparison to report it. Without it, a comparison that hashes the wrong folder, or an empty one, passes forever.

### 3.7 Performance notes

- Cache publication dates forever. Only first-time pins hit the network.
- Diff pin sets rather than re-judging every pin of every lockfile on every commit.
- Run `H5` and `H6` in the nightly job. Two clean release builds take a minute or two for a small workspace, but grow with it.

---

## 4. The model-review runner

### 4.1 Purpose

The model-review runner grades a diff against binary rubric questions, using an isolated reviewer model, where syntax and types cannot see enough. It is a *measured instrument*. It refuses to run when its calibration is stale, it believes only findings it can verify against the diff, and it keeps a ledger that turns repeated concerns into mechanical checks.

The rubric format, the questions and calibration are in [rubrics.md](rubrics.md). This section specifies the runner.

### 4.2 Rules held

| Rule | What the runner does |
| :--- | :--- |
| `V1` | Lints the rubric files: fields, order, rule IDs, severities, scopes, one item per rule |
| `V2` | Builds the prompt from an allow list only, checks it holds no forbidden input, runs the reviewer in a fresh read-only worktree with the answer key removed |
| `V3` | Verifies each finding's rule, scope, file, line and quote; downgrades unverified ones; decides MERGE or BLOCK |
| `V4` | Lints the calibration set; runs and scores calibrations; writes the record |
| `V5` | Refuses to review when the calibration is stale or the weekly canary was missed or not run |
| `V6` | Second reviewer of another model family (**not in the runner's code**; see 4.4) |
| `V7` | Appends verified findings to a ledger and prints promotion tasks |
| `V8` | Refuses the diff, before any model call, if it touches protected paths |

### 4.3 Commands

The reference runner's subcommands. Each takes `--next <project dir>`; reviewer commands also take `--engine <name>`, where `stub:<file>` returns a recorded reply for tests.

| Command | Calls the model? | When | What it does |
| :--- | :--- | :--- | :--- |
| `lint` | no | every commit (gate stage) | `V1` rubric lint and `V4` case-set lint |
| `breaches` | no | every commit (gate stage) | All planted cases of `V1`–`V5`, `V7` and `V8` |
| `protect <diff> [--spec <file>]` | no | before review | `V8` alone |
| `review <diff> [--forbid <file>] [--spec <file>] [--item <name>]` | yes | on demand, before landing | `V5`, `V8`, `V2`, then the review, `V3` and `V7` |
| `calibrate [--runs 3] [--only a,b]` | yes, many times | on demand, after any trigger | `V4`: runs the set and writes the record |
| `rescore <record> [--replace <record>]` | no | after a label or scorer change | `V4` from saved replies |
| `canary` | yes, once | weekly (nightly script when 6 or more days old; also a weekly CI schedule) | `V5`'s hidden canary |

So the model-review runner at `commit` cadence is cheap and model-free: it lints and proves itself. The model is called only to review a branch, to calibrate, and for the canary.

### 4.4 The `review` algorithm

Input: a diff file. Optional inputs: a file of forbidden strings, the spec file and an item name.

1. **Strip the diff.** Keep the text from the first `diff --git ` line on. A format-patch's headers (subject, author, message) are the author's words and must never reach the reviewer (`V2`).
2. **Parse the diff** into file changes. For each file, record:
   - the old and new path;
   - whether it was created or deleted;
   - the removed lines with their old line numbers;
   - the *shown* lines (added and context) with their new line numbers.

   Root every path to the repository: a diff taken inside a subfolder names `tools/x`, which becomes `<project>/tools/x`.
3. **Choose the items in scope.** An item is in scope when some changed path matches one of the path globs in its `Scope` field and none of its exclusions. Exclusions are paths after the word "except", plus the breaches folder. See [rubrics.md](rubrics.md#23-scope).
4. **Refuse if stale (`V5`).** Build the health record and refuse with one line per trigger if any of these holds:
   - there is no calibration record (trial runs do not count);
   - the model ID differs from the record's;
   - the prompt hash differs;
   - the rubric hash differs;
   - the record is more than 30 days old;
   - the record failed the bar;
   - there is no canary record;
   - the canary is more than 7 days old;
   - the last canary was missed;
   - there have been 3 or more disputes since the record;
   - there has been 1 or more escaped defect since the record.

   Disputes and escaped defects are ledger entries with a `kind` and a date.
5. **Refuse if protected (`V8`).** Report every file whose old or new path starts with a protected prefix. In the reference project these are the standards folder, the breaches folder, the golden files and the three gate scripts. Then report every existing test file (under the tests folder, not newly created) that has removed lines or was moved, unless the spec file names it.
6. **Build the prompt (`V2`)** from exactly three parts: the in-scope items (all fields, including the pass and fail examples), the quantitative results (default: "the gate is green") and the diff. The fixed preamble asks for:
   - one verdict per item: `violation`, `holds` or `cannot_determine`;
   - the exact quoted line for each violation;
   - JSON only, in the shape `{"findings":[{"rule","file","line","quote","verdict","severity","why","checkable"}]}`.

   `checkable` is true when a deterministic check could have seen the violation.
7. **Check blindness (`V2`).** For each forbidden string of 12 characters or more, such as the commit message lines or the author's summary, refuse if the prompt contains it.
8. **Run the reviewer in isolation (`V2`):**
   - Create a fresh detached worktree of `HEAD` in a temporary folder.
   - Delete the answer key from it: the calibration cases, the calibration records and the review reports. Each was found to be one search away before it was removed.
   - Start the reviewer in that worktree with read-only tools only (read, grep, glob) and every write, shell and network tool denied.
   - Pass the prompt on standard input.
   - Remove the worktree however the run ends.
   - Record the run's reported cost.
9. **Parse the reply.** Take the outermost `{ ... }` and read `findings`. A reply that is not this JSON is a `V3` failure ("the reviewer's reply is not the findings JSON"), which is red.
10. **Verify each violation finding (`V3`).** Lowercase and trim the verdict and severity. A violation is verified only when all of the following hold:
    - its rule is an in-scope item;
    - its file matches that item's own scope, so a finding on a file another item put in scope does not verify;
    - its `(file, line)` is a shown line of the diff (added or context);
    - its whitespace-normalised quote is non-empty and is contained in that line, and is at least 8 characters long or equal to the whole line.

    A verified violation takes the **item's** severity (`block` or `concern`), never the reviewer's choice. An item whose severity cannot be read blocks (fail closed). An unverified violation becomes a `note`, and the reason is printed (`<rule> downgraded to a note: <reason>`).
11. **Apply the language's calibration status.** In the reference project, a verified violation on a path of the Rust workspace becomes `advice`, because the rubrics were calibrated only on the other language. Advice never blocks, and the ledger's promotions do not count it. A note says so.
12. **Record (`V7`).** Append one JSON line per verified-or-downgraded violation to the ledger: `{date, item, rule, severity, checkable}`. Print every promotion due: a rule with 2 or more ledger entries of severity `concern` and `checkable = true` prints `<rule>: <n> concerns a check could have seen | promote it: write the check and its breach, then mark the rubric item "now enforced by <id>"`.
13. **Decide.** `BLOCK` if any verified violation has severity `block`; otherwise `MERGE`. Print each finding and exit `0` for `MERGE` and `1` for `BLOCK` or a refusal.

**`V6` in the reference project.** The runner's code has no second-reviewer path, and `V6` has no planted breach. The playbook describes the second reviewer as an attended step on the approver's own machine: send every remaining block, with the same blind input, to a second model of another family. A block it does not confirm becomes a concern and a calibration case. In unattended sessions, a verified block blocks on its own. **Guide recommendation:** if you have a second family available, implement step 12a: for each verified block, run the second reviewer on the same prompt in the same isolated way; downgrade an unconfirmed block to `concern`, and write the case into a `disputes/` folder for labelling. Plant a breach in which the second reviewer's stub reply does not confirm the block, and check that the outcome is `MERGE` with a concern.

### 4.5 The `calibrate`, `rescore` and `canary` algorithms

These are specified with the scoring in [rubrics.md, Calibration](rubrics.md#4-calibration). In outline:

- **`calibrate`** builds each case's prompt exactly as `review` does, runs it an odd number of times (3), verifies each reply with step 10, scores the majority per label, and writes two files: a record (`evals/calibration-<stamp>.json`) and the raw replies (`.replies.jsonl`). It exits `1` below the bar. With `--only`, it writes a `trial-` record that `V5` ignores.
- **`rescore`** recomputes a record from saved replies when labels or the scorer change. It refuses when the prompt, the rubric or the diffs differ from those the replies answered.
- **`canary`** runs the one case named in `evals/canary.txt` and writes `evals/canary.json` (`date`, `case`, `caught`, `cost`). It counts as caught only if every violation label of the case is said at an accepted line.

### 4.6 Cadence

- `lint` and `breaches`: every commit. The gate stage budget is 10 s locally; it measured 5.5 to 6.9 s on CI.
- `review`: on demand, once per branch, after the gate is green and before the branch lands (`S9`).
- `canary`: weekly. The canary's age is checked by every `review` (`V5`).
- `calibrate`: on any `V5` trigger. In the reference project a full run of 88 cases × 3 runs took about 87 minutes and cost about USD 42 in model usage. A canary run cost about USD 0.17.

**History.** The runner reads no commit range. It needs a worktree of `HEAD` (any clone works, including a shallow one) and the diff, which the caller produces, for example `git diff <merge-base>..HEAD`. **Guide recommendation:** produce the diff in the landing script from the same merge base the history check uses, so that the reviewer sees exactly the unmerged work.

### 4.7 Planted breaches

The reference runner has about 70 planted cases in code. Each is a pair of an expected rule (or none, for a clean twin) and the findings a pure function returned. A sample:

- **`V1`:** a missing field; an item for a rule a script decides; a rule with a review part and no item; an excuse written inside an item, which excuses nothing; a scope that names no path; a clean rubric.
- **`V2`:** the commit message in the prompt; a format-patch subject left in by not stripping the headers; a clean prompt.
- **`V3`:**
  - a quote not on its line; a line outside the diff; a one-character quote;
  - a rule with no in-scope item; a finding on a file outside its item's scope;
  - a verdict in capitals that must still block; a reviewer that says `block` on a concern-only item;
  - a block on a Rust path, which becomes advice;
  - a verified block that stays a block (clean).
- **`V4`:**
  - too few cases for an item; a score under the bar; an unreadable reply, which never helps the score;
  - a downgraded finding, which is no true positive; a stray finding beside the fault (fires); an alternative evidence line (clean);
  - a reviewer that flags every cannot-determine label, which fails the hard-negative bar although overall TNR passes;
  - 5 of 40 traps flagged (fires) against 2 of 40 (clean);
  - misses that all fall on hard cases (fires) against the same misses on easy cases (clean);
  - an out-of-scope violation, even as a note (fires);
  - a case line that copies rubric text;
  - mixed set versions.
- **`V5`:** a changed rubric; an old calibration; a missed canary; no canary this week; three disputes; a rescored record that must sort after its source; a fresh calibration (clean).
- **`V7`:** the second checkable concern (fires); one concern, or concerns no check could see (clean).
- **`V8`:**
  - a protected path; a protected file renamed away; a path written relative to the project;
  - an existing test line changed; a test moved out of the tests folder;
  - the spec names the test file (clean); production code changed (clean).

The breaches check also requires at least one planted case for each of `V1`–`V5`, `V7` and `V8`.

### 4.8 Performance notes

- Model calls dominate: minutes per review, and over an hour per calibration. Run one review per branch, not per commit, and send only the items in scope.
- Keep every model-free part (parsing, scope, verification, scoring) pure, so the model-free checks run in seconds in every gate.
- Save raw replies with every calibration. Most later changes (a label fix, a scorer fix) can then be rescored for free instead of rerun.
- **Guide recommendation:** add a spending cap to `calibrate`. Project the cost from the first runs and stop before the cap. The reference project added this in a pending change, after a projected USD 50 run stopped at USD 4.48.

---

## 5. The landing script (`S9`)

Rule `S9` applies wherever CI fast-forwards any green working branch onto the main line. There, *pushing a branch is merging it*. A model builder therefore commits locally only. The reviewer, after the blind review passes, lands the branch through **one** script. The reference project's landing script does most of what follows; the items it does not do are marked.

1. **Bring the branch up to date.** Fetch, fast-forward the local branch to its remote copy if one exists, and stop if the two have diverged (exit `3`). Then merge the main line.
2. **Refuse conflict markers.** After any merge, and after any automatic conflict resolution, search the tree for lines starting `<<<<<<<` or `>>>>>>>`. If any are found, stop (`MARKERS LEFT`, exit `3`). The reference script resolves some conflicts mechanically: lockfiles take the main line's copy and are regenerated, and Markdown, TOML, source and append-only shape files keep both sides. It refuses any other conflicted file (`UNRESOLVED <file>`). The marker search is the backstop for all of it.
3. **Never let a pipe hide a gate's exit code.** Run each gate as `gate > log 2>&1` and test its own exit status. Never write `gate | tail && git push`. The exit status of a pipeline is the last command's, so `tail` succeeds and the push goes out past a red gate. In the reference project this happened twice. If you must pipe, use `set -o pipefail` (bash) or capture `PIPESTATUS`.
4. **Push only on green.** Run every gate the main line runs, here two gates, one per language. Push only when all exit `0`. The reference script retries a gate once in two narrow cases, both from load when several gates run in parallel on one machine:
   - every red line is an `E8` ratchet finding: rerun with `NEXT_SLOWER` naming those stages and the reason;
   - every red line is an `E8` timing finding: one plain rerun.

   Any other red stops the landing (exit `1`).
5. **Verify the remote head.** After the push, compare `git ls-remote origin refs/heads/<branch>` with the local `HEAD`. If they differ, stop (`PUSH FAILED`, exit `5`). A push can fail silently behind a filter or a proxy. Note that the reference script pipes `git push` through `grep` to drop `remote:` noise, which hides the push's own exit code. This check is what makes that safe.
6. **Land stacked branches in order.** Land each branch only after its parent is on the main line: land the parent, wait until the main line contains it, then rebase or merge the child onto the new main line and land it. **Not in the reference script:** it lands one branch per call, so the order is kept by the reviewer's routine. **Guide recommendation:** take an ordered list of branches. Before landing branch *n*, check that `git merge-base --is-ancestor <branch n−1> origin/<main>` holds, and stop if it does not. In the reference project, two stacked branches once landed while the check they added was still under a review that went on to find bypasses in it.

Exit codes in the reference script: `0` landed, `1` a gate is red, `2` bad arguments, `3` diverged or conflicts left, `5` the remote head does not match.

**Guide recommendation:** add the model review to the script as a step between 4 and 5. Produce the diff from the merge base, run `review`, and push only on `MERGE`. Then the review cannot be skipped by landing by hand. Clean up large build outputs at the end; the reference script deletes the planted-breach build folder, about 2 GB per worktree.

---

## 6. Build order

**Guide recommendation:** for a team starting from nothing, this order gives a working runner each day.

1. **The shared frame.** Do this first, about half a day: the finding format, exit codes, the fixture loader with `_base` overlay and `.deleted`, the breaches-mode checks (0.5), and the selection and dormancy lines (0.6). Write the frame once and share it between runners, or copy it.
2. **History check, core.** Day one: the range (1.4), the commit reader, `S5`, `F4` (start with TOML flattening and a direction table of five rows), `I3` and `R5`, each with one breach and one clean twin.
3. **Inventory check.** Half a day: summary parsing with exact microseconds, `E8` per-test, per-package and missing-package checks, and `E36`. Make the test runner write `start_ms`.
4. **Supply-chain check.** Day two: the pin parsers for your ecosystems, the age judgement with its planted cases, the date cache, then the nightly audit and the two-build comparison with a planted non-reproducible fixture.
5. **Model-review runner.** Two days or more: diff parsing, scope, verification and scoring are a day of pure code with planted cases. Writing and labelling the first 50 calibration cases, and the first calibration, take longer than the code (see [rubrics.md](rubrics.md)). Until calibration meets the bar, run the reviewer with every verified finding forced to `advice`.
6. **The landing script.** An hour. Adopt it before the model-review runner if CI lands green branches on its own.
