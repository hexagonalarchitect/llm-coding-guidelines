# Rubrics and the calibrated model reviewer

The guide's model-review rules (`V1` to `V8`) depend on **rubrics**: binary questions that an isolated reviewer model answers about a diff. The questions cover things a type checker or a syntax-tree check cannot see. Does a domain name speak the business's language? Does a test helper compute its expected value with the production formula? Does an event carry the facts it was decided on?

This document covers:

- the rubric format;
- how the isolated reviewer runs;
- how to calibrate it, so that a question blocks a merge only when it has been shown to be accurate;
- where hand mutants fit beside the reviewer (`E14`).

The rubric questions themselves are in the family files, adapted from the reference project with domain examples in the guide's lending-library domain:

| File | Rules | Reference project's family |
| :--- | :--- | :--- |
| [rubrics/domain-model.md](rubrics/domain-model.md) | `A7`, `B11`, `B12`, `M5`, `M7` | `evans-vernon` |
| [rubrics/exactness.md](rubrics/exactness.md) | `D4`, `D8`, `D9`, `P11` | `exact` |
| [rubrics/tests.md](rubrics/tests.md) | `E11`, `E16`, `E25` | `khorikov-shore` |
| [rubrics/design.md](rubrics/design.md) | `G13` | `ousterhout` |

The runner that executes all this is specified in [runners.md, section 4](runners.md#4-the-model-review-runner).

This document describes what the reference project does. Text marked **Guide recommendation** goes beyond it. The numbers (case counts, accuracy bars, day limits) are the reference project's own choices, not values from a published source.

## Contents

- [1. What a rubric is for](#1-what-a-rubric-is-for)
- [2. The rubric format (`V1`)](#2-the-rubric-format-v1)
- [3. How the isolated reviewer runs (`V1`–`V8`)](#3-how-the-isolated-reviewer-runs-v1v8)
- [4. Calibration](#4-calibration)
- [5. Hand mutants beside the reviewer (`E14`)](#5-hand-mutants-beside-the-reviewer-e14)
- [6. Adopting rubrics in a new project](#6-adopting-rubrics-in-a-new-project)

---

## 1. What a rubric is for

A rubric item covers the **review half** of a rule. In the rule table, such a rule has kind `L` (review only) or `Q+L` (a mechanical check plus review). The mechanical half catches what it can. For example, `B12`'s check flags a workflow with two `record` calls, and `E11`'s check rejects `should...` names. The rubric item asks the question the check cannot settle, such as "are these two aggregates' events in one journal entry without a registered policy?".

Three principles shape every item:

- **Binary, one principle per item.** Each item asks one yes-or-no question and gives no score. The reviewer answers `violation`, `holds` or `cannot_determine`.
- **An explicit way out.** `cannot_determine` is a legitimate answer, used when the deciding fact is outside the diff and outside the repository. It never blocks. Without it, a reviewer guesses, and guesses look like findings.
- **A reference answer.** Every item carries a passing and a failing example with the expected verdict. The reviewer sees them; they anchor what "violation" means.

A rubric is **not** where a mechanical check belongs. When the same concern recurs and a check could have seen it, the runner's ledger opens a task to write that check (`V7`). The item then reads `now enforced by <rule ID>`. It stays as a reference for the reviewer but no longer blocks on its own.

A rule that cannot be judged from a diff gets no item. In the reference project, the governance family's monthly vitality review (`I9`) is a dated process step, judged by whether a dated record exists. Its family file says so in a line the lint recognises (see 2.4).

---

## 2. The rubric format (`V1`)

### 2.1 File layout

One Markdown file per family, under `standards/rubrics/<family>.md`. The rubrics are principle files: `F4` lets them only add or tighten without a dated approval, and any edit changes the rubric hash, which forces a recalibration (`V5`).

Each file has three parts:

- a short introduction naming the rules the family holds;
- optional "not a rubric item" lines;
- one item per rule.

### 2.2 The item

An item is a level-two heading `## <RULE> <title>`, followed by exactly these ten bullets, in this order. Each bullet is written `- **Field:** text`, and a field may run over several lines.

| Field | What it holds | What the lint checks |
| :--- | :--- | :--- |
| **Question** | One yes-or-no question about one principle, answerable from the diff and the repository. | Not empty; ends with `?`. |
| **Scope** | The layers and paths the item applies to, with paths in backticks. The runner sends the item only when the diff touches one of them. | Names at least one path in backticks. |
| **Violation when** | The observable signs, as concrete as the rule allows: names, call shapes, literal patterns. This is the item's meaning of **fail**. | Not empty. |
| **Holds when** | What a passing change looks like, including the near misses that still pass. | Not empty. |
| **Cannot determine when** | What the reviewer would need and does not have. The answer is then `cannot_determine`, which never blocks. | Not empty. |
| **Pass example** | A short snippet, its expected verdict (`holds`) and a one-line reason. | Not empty. |
| **Fail example** | A short snippet, its expected verdict (`violation`) and a one-line reason. | Not empty. |
| **Evidence** | Which line a violation finding must quote: "the line that opens the record literal", "the line in `evolve` that reads the changing data". | Not empty. |
| **Severity** | `block` or `concern`: what a *verified* violation becomes. The reviewer cannot change it. | Starts with `block` or `concern`. |
| **Status** | `L` (live), or `now enforced by <rule ID>` once promoted. It may name the mechanical checks that run beside the item. | Not empty. |

The lint (`Review lint`, every commit) also checks the rubric set as a whole:

- the item's rule exists in the rule table and has a review part (kind `L` or `Q+L`). An item for a rule a script decides is refused;
- no rule has more than one item;
- every rule with a review part has an item, unless a family file excuses it (2.4).

### 2.3 Scope

The scope is what keeps the reviewer's prompt small and its findings honest. The runner reads it mechanically:

- **Includes** are the backticked paths that start with the project's root and appear before the word `except`. `*` matches one path segment.
- **Excludes** are the backticked paths after `except`, plus the breaches folder. Planted breaches are fixtures, not code.
- An item is **in scope for a diff** when some changed path matches an include and no exclude.
- A **finding verifies** only if *its own file* is in its item's scope. A finding on an import file does not verify just because another file of the diff put the item in scope.

Write scopes as paths, never as prose such as "every project". In the reference project, a scope sentence containing "every" once put every item into every prompt.

In the reference project the scopes name only the first language's source folders. A diff in the second language's workspace therefore gets no items at all, which is one reason that language's verdicts are advisory (4.5).

### 2.4 Excusing a rule

A rule with a review part that cannot be judged from a diff is excused by one bullet *before the first item*:

```markdown
- **I9 — not a rubric item:** the monthly vitality review is a dated process step in an attended session, judged by whether its dated record exists, not from a diff.
```

The excuse must sit before the first item and carry a reason of at least five words. A bare mention inside an item excuses nothing.

### 2.5 The reviewer's answer

For every in-scope item, the reviewer returns one record. Violations need `file`, `line` and `quote`:

```json
{"findings": [
  {"rule": "P11", "file": "contexts/loans/domain/src/loan.rs", "line": 41,
   "quote": "Overdue { item } => state.fines + tariff.daily_rate(item) * days",
   "verdict": "violation", "severity": "block",
   "why": "evolve reads today's tariff; a replay after a tariff change gives another balance",
   "checkable": false}
]}
```

- `severity` in the reply is ignored for violations. A verified violation takes the item's severity.
- `checkable` says whether a deterministic check could have seen the violation. It feeds the ledger (`V7`).

---

## 3. How the isolated reviewer runs (`V1`–`V8`)

These are the steps of one review, in order, with the rule each one rests on. The algorithms are in [runners.md, 4.4](runners.md#44-the-review-algorithm).

1. **The gate is green first.** The review grades what the mechanical checks cannot see. It does not replace them, and it runs only on a branch whose gate passed.
2. **Protected paths (`V8`), before any model call.** The diff is refused if it touches protected paths: the standards (including the rubrics and the calibration cases), the planted breaches, the golden files or the gate scripts. It is also refused if it changes, deletes or moves an existing test line in a file the spec does not name. Adding tests is always allowed. A refused diff goes back to its author or to the approver; it is never reviewed.
3. **A fresh calibration (`V5`).** The review refuses to run if the calibration is stale, for any of these reasons:
   - the model, the prompt or a rubric has changed since the calibration (compared by ID and hashes);
   - the calibration is more than 30 days old, or failed the bar;
   - there have been 3 disputes, or 1 escaped defect, since the calibration;
   - the weekly hidden canary was missed, or has not run in the last 7 days.
4. **A blind input (`V2`).** The prompt is built from an allow list, and nothing else:
   - the diff (format-patch headers stripped);
   - the in-scope rubric items, with their examples;
   - the quantitative results;
   - read-only access to the repository.

   It never includes the author's summary, commit message, claims or test narrative. A check refuses the prompt if any forbidden string of 12 characters or more appears in it.
5. **An isolated checkout (`V2`).** Each review runs in a fresh worktree of `HEAD` with no leftover outputs or other reviews. The answer key is deleted from the worktree before the reviewer starts: the calibration cases, the calibration records and the earlier review reports. The reviewer gets read-only tools and no shell, no write access and no network.
6. **A verdict per question, with evidence (`V1`, `V3`).** The reviewer answers every in-scope item. A script verifies each violation:
   - the rule is an in-scope item;
   - the file is in that item's scope;
   - the file and line are a line the diff shows;
   - the quote is on that line (at least 8 characters, or the whole line).

   A verified violation takes the item's severity. An unverified one becomes a `note`, with the reason printed. A reply that is not the findings JSON is red.
7. **Language calibration status (`V5`).** In the reference project, a verified violation on a path of the language the rubrics were *not* calibrated on becomes `advice`. Advice is recorded and counted by calibration, but it never blocks, and the ledger does not count it toward promotion.
8. **A second reviewer of another family (`V6`).** If one is available, it reviews every remaining block with the same blind input. A block it does not confirm becomes a concern and a new calibration case. Where none is available, a verified block blocks on its own. In the reference project this step is an attended one on the approver's machine; the runner's code has no `V6` path (see [runners.md, 4.4](runners.md#44-the-review-algorithm)).
9. **The outcome (`V3`).** The branch merges only if no verified `block` remains. Every block goes back to the author as a spec line, and the branch is reviewed again after the fix. Every concern is listed in the report.
10. **The ledger (`V7`).** Each verified or downgraded violation is appended to a ledger: date, item, rule, severity and `checkable`. When a rule collects its second checkable concern, the runner prints a promotion task: write the check and its planted breach, then mark the item `now enforced by <ID>`.
11. **Feed the set.** Every dispute, every finding the approver overturned and every defect that escaped review becomes a calibration case (4.1). An escaped defect is also a recalibration trigger.

---

## 4. Calibration

An uncalibrated reviewer is an opinion. Rule `V4` turns it into an instrument: a labelled set of cases, run repeatedly, scored against a stated bar, with the result recorded.

### 4.1 The case format

```
standards/review-cases/rc-NN/
  diff.patch    a unified git diff, paths from the repository root: exactly what the reviewer sees
  labels.toml   the expected verdict for every item in scope: never shown to the reviewer
  why.md        the labeller's reasons, and for a hardened case one line on what makes it hard: never shown
```

```toml
case = "rc-07"
kind = "planted"            # planted | found | good
set_version = 2
hard = true                 # optional, planted cases only: the fault needs a cross-reference or subtle semantics
out_of_scope = ["A7"]       # optional: items tempting here but outside their Scope; a violation finding for one is a false alarm

[[label]]                   # one label per item whose Scope the diff touches, not only the planted one
rule = "D4"
verdict = "violation"       # violation | holds | cannot_determine
file = "contexts/loans/domain/src/fine.rs"     # violations only: the path as in the diff
line = 19                   # the line number in the new file
quote = "(daily * 7) as i32"                   # the exact text of that line, trimmed
alt_lines = [21]            # optional: other lines of the same file that are equally valid evidence
alt = [{ file = "contexts/loans/domain/src/tariff.rs", line = 8 }]   # optional: evidence in another file

[[label]]
rule = "B11"
verdict = "holds"
near_miss = true            # optional, holds labels of good cases only: the label the case is built around
```

The three kinds of case:

- **planted**: a fault written on purpose.
- **found**: from a real review, a dispute between reviewers or a postmortem.
- **good**: a change judged clean, often one that looks like a breach the rubric allows.

A case labels **every item its diff puts in scope**, not only the planted one, so each case also measures false alarms. The lint checks that the labelled items are exactly the in-scope items.

### 4.2 Known-good and known-bad cases per question

The reference project's minimum is **3 violation labels and 3 holds labels per item** (`V4`), in a set of **50 to 200 cases**. A small set like this only shows a question can be answered. Make it hard, so it measures judgement rather than recall of the examples. The reference project's first set (50 cases, 42 planted and 8 good) scored a perfect 1.00 / 1.00. An audit then found that 44 of the 50 cases were easy, decoys and `cannot_determine` were not scored, and many planted lines contained the rubric's own example text. The second set fixed this. Its rules, which a port should adopt:

1. **No rubric text in a case.** No added or context line may contain a backticked snippet of 10 or more characters from the rubric files. No line of 12 or more characters (whitespace-normalised) may be a substring of the rubric text. Declaration lines (module, namespace, import) are exempt. The lint enforces this, and it is why case code uses synonyms of the rubric's example names.
2. **No comment announces the fault.** Some planted cases give a plausible wrong reason instead.
3. **Unlisted synonyms.** Where a rubric lists bad names, the cases use other names that still breach.
4. **Long files with the fault in the middle.** Seventeen cases are files of 59 to 77 lines, with several functions that hold and the fault in the middle.
5. **Faults split across two files.** The deciding fact is in one file and the evidence in the other.
6. **Cannot-determine cases.** At least one per item, ideally two, where the item's own "Cannot determine when" clause applies. Use these only where that clause applies; a label the reviewer can dispute from the rubric is not a calibration case.
7. **Near-miss good cases.** One standalone good case per item, built around what the rubric allows, marked `near_miss`.
8. **Multi-fault cases.** A few cases with two faults of different items.
9. **Out-of-scope temptations.** A legacy offset inside the import layer, for example, where the legacy-format item is not in scope.

The lint enforces the following on the set:

- at least 3 violation and 3 holds labels per item;
- labels only for rubric items, with a known verdict;
- one verdict per rule per case (a two-file fault is one label with `alt` evidence);
- `hard` only on planted cases;
- `near_miss` only on holds labels of good cases;
- `alt` only on violation labels;
- `out_of_scope` items are rubric items and are not also labelled;
- from set version 2 on, at least 10 hard cases and 10 near misses;
- one set version across all cases;
- every violation label's evidence line is a line of the diff.

The second set's coverage, per item, as a guide to proportions:

| Item | violation | holds | cannot_determine |
| :--- | ---: | ---: | ---: |
| `A7` | 5 | 61 | 1 |
| `B11` | 4 | 27 | 1 |
| `B12` | 4 | 13 | 2 |
| `M5` | 4 | 15 | 2 |
| `M7` | 3 | 22 | 2 |
| `D4` | 4 | 30 | 1 |
| `D8` | 4 | 27 | 1 |
| `D9` | 3 | 7 | 1 |
| `P11` | 3 | 33 | 1 |
| `E11` | 4 | 16 | 1 |
| `E16` | 5 | 14 | 2 |
| `E25` | 4 | 14 | 3 |
| `G13` | 3 | 83 | 2 |

The set had 88 cases: 45 planted and 43 good, including 20 cannot-determine cases, 13 near-miss cases, 3 two-fault cases and 2 out-of-scope cases.

**Guide recommendation:** check every case mechanically while drafting it:

- every hunk header's counts match its lines;
- the patch applies (`git apply --check`) to the old file rebuilt from its own context;
- each label's line and quote match the diff, and the evidence is an added line.

The reference project ran these checks from scratch scripts. Make them part of the lint.

### 4.3 Measuring accuracy

Each case runs **3 times** (an odd number, so a majority exists). Each run's reply is verified exactly as in a real review, and then scored per label by **majority**. A reply that is not valid findings JSON counts as wrong on every judgement: a miss on a violation, a false alarm on a negative, never a `cannot_determine`.

The judgements:

- **Positives** are the violation labels. A run "says violation" only if it has a non-note violation finding for that rule within **one line** of an accepted evidence line: `line`, `alt_lines` or `alt`. Paths are compared rooted. A finding elsewhere is not the planted fault.
- **Negatives:**
  - each holds and cannot_determine label, where any non-note violation finding for the rule is a false alarm;
  - for each planted rule of a case, "the rest of the case": a violation finding for that rule away from all its accepted lines, which catches decoys beside the fault;
  - each `out_of_scope` item: a violation finding for it, *even one downgraded to a note*, is a false alarm.
- **Hard positives** are the violations of `hard` cases.
- **Hard negatives** are the cannot_determine labels, near-miss labels, each planted case's rest, and the out-of-scope items.
- **Traps** are the cannot_determine and near-miss labels alone. The planted cases' rest is mostly easy and would dilute the hard-negative rate.

The scores:

| Measure | Definition |
| :--- | :--- |
| TPR | caught positives / positives, with a 95 % Wilson interval |
| TNR | negatives left alone / negatives, with a 95 % Wilson interval |
| Gwet's AC1 | agreement between the labels and the majority verdicts (binary) |
| Hard TPR, hard TNR, trap TNR | as above, on the hard subsets |
| Cannot-determine rate | share of cannot_determine labels the majority answered `cannot_determine` (reported, no bar yet) |
| Hedged | share of decidable labels the majority answered `cannot_determine` (reported) |
| Unstable | the labels on which the three runs did not agree (listed) |
| Unparsable | runs whose reply was not the findings JSON (counted) |

Wilson's interval for `k` of `n`, with `z = 1.96`:
- `centre = (p + z²/2n) / (1 + z²/n)`;
- `half = z·√(p(1−p)/n + z²/4n²) / (1 + z²/n)`.

Gwet's AC1 for two raters on a binary scale:
- `pa` is the observed agreement;
- `π` is the mean "yes" rate over both raters;
- `pe = 2π(1−π)`;
- `AC1 = (pa − pe)/(1 − pe)`.

### 4.4 The bar, and when a question may block

**What the reference project does.** The bar applies to the whole set, and the review refuses to run (`V5`) unless the last calibration met it:

- TPR ≥ 0.90 and TNR ≥ 0.95 overall;
- hard-negative TNR ≥ 0.90 and hard-positive TPR ≥ 0.85;
- trap TNR ≥ 0.90.

The hard bars were added after the audit. On the second set, a reviewer that flagged *every* cannot_determine label would still have passed at an overall TNR of 0.951, and five misses could all have fallen on the hard cases.

Each item's severity applies as soon as the set passes. New questions are kept out of the prompt until they have cases. In the reference project's design family, only one of fourteen red flags is an item so far, and each of the others "becomes an item, with its own calibration cases, before it is sent to the reviewer".

**Guide recommendation: a per-question threshold, and advisory until calibrated.** A set-level bar can hide one weak question behind twelve strong ones. Add a per-item gate:

1. Score each item separately: its TPR over its violation labels and its TNR over its holds, cannot_determine, near-miss and stray judgements.
2. Let an item **block** only when the **lower bound** of its Wilson interval meets the bar, not just its point estimate. Three violation labels can never reach a lower bound of 0.90. Grow the cases until the bound clears.
3. Until then, the item runs **advisory**: its verified violations are forced to `advice`, recorded and scored, but never block. This is the same mechanism the reference project uses per language (4.5); apply it per item as well.
4. Record each item's state (advisory or blocking) and the record it was promoted on, in the calibration record. The review then prints which items could block this run.

With ten violation labels all caught, Wilson's lower bound is about 0.72. With thirty, about 0.89. With thirty-six, about 0.90. A blocking question therefore needs roughly 30 to 40 known-bad and as many known-good cases. Treat 3 + 3 as the floor for entry, not the bar for blocking.

### 4.5 Recalibrating

`V5` lists the triggers, and every review checks them:

- the model changed;
- the prompt changed (hash);
- a rubric changed (hash);
- more than 30 days have passed;
- 3 disputes, or 1 escaped defect, since the last calibration;
- the last calibration failed the bar;
- the weekly canary was missed or did not run.

A recalibration runs the whole current set again. Because any prompt or rubric change triggers it, a team should batch rubric edits. In the reference project, a third set version (90 cases, with a prompt change) waits unmerged, because merging it would make every review refuse until a calibration costing about USD 45 to 50 is run.

**When the language changes.** Calibration is specific to the language the cases are written in. Naming conventions, idioms, what a "record literal" or a "pass-through" looks like, and which facts are visible in a diff all differ between languages. In the reference project, the rubrics were calibrated only on the first language's code. Its Rust workspace is therefore handled like this:

- **Advisory.** Every verified violation on a Rust path is forced to `advice`. It never blocks, it is still counted by calibration, and the ledger's promotions ignore it.
- **No items reach Rust diffs yet.** The items' scopes name only the first language's folders, so a Rust-only diff gets no items at all.
- **Waiting on set version 2.** The rule table marks all of `V1`–`V8` `partial` on Rust paths, waiting for a rubric set with Rust scopes and Rust cases.

To calibrate for a new language:

1. **Add the language's paths to every item's scope.** Keep one item per rule. Add a sentence or a near miss where the language changes the meaning: a Rust struct-update literal, an F# record expression.
2. **Rewrite the pass and fail examples** in the new language, or give one pair per language.
3. **Write new cases in the new language**, meeting the same minimums and hardening rules (4.2). Do not translate the old cases line by line; the old fault shapes may not be the new language's common faults.
4. **Bump the set version.** Cases of two set versions may not be mixed in one calibration.
5. **Calibrate, and lift the language's advisory flag only when the new record meets the bar.** **Guide recommendation:** apply the per-item threshold of 4.4 per language, so `E16` can block on one language while still advisory on the other.

### 4.6 Recording calibration results

Each calibration writes two files under `evals/`. The rubric files and cases are protected by `V8`; the `evals/` folder is hidden from the reviewer.

**`calibration-<yyyy-mm-dd-hhmm>.json`**, the record:

| Field | Meaning |
| :--- | :--- |
| `date`, `trial` | when; `true` for a partial run (`--only`), which `V5` ignores |
| `model`, `temperature` | the reviewer model ID and its settings |
| `prompt_hash`, `rubric_hash`, `cases_hash` | what the replies answered |
| `set_version`, `cases`, `runs` | which set, which cases, how many runs each |
| `tpr`, `tpr_wilson95`, `tnr`, `tnr_wilson95`, `gwet_ac1` | the headline scores |
| `positives`, `negatives`, `unstable`, `unparsable` | the counts behind them |
| `hard_*`, `traps`, `trap_tnr` | the hard subsets |
| `cannot_determine_labels`, `cannot_determine_rate`, `cannot_determine_wilson95`, `hedged_on_decidable` | the third verdict |
| `meets_bar` | the verdict `V5` reads |
| `seconds`, `cost_usd` | what it took |

**`calibration-<stamp>.replies.jsonl`** holds every raw reply, as `{case, run, reply}` per line.

**Rescoring.** When labels or the scorer change, but not the prompt, the rubric or the diffs, recompute the record from the saved replies rather than rerunning. The rescored record gets a later stamp, so it is the one `V5` reads. It names its source (`rescored_from`) and any cases replaced by a fresh run (`replaced`, `replaced_from`). Rescoring refuses if the prompt, rubric or diff hashes differ from those the replies answered.

**The set's own history.** The case set grows only by adding cases. Changing a label needs a reason in that case's `why.md`, and resets the stored scores, which are then rescored. Keep a short README in the cases folder with:
- the set version;
- what changed from the previous version, and why;
- the per-item coverage table.

**The canary.** `evals/canary.txt` names one case with a known fault. Each weekly run writes `evals/canary.json` (`date`, `case`, `caught`, `cost`). The canary counts as caught only if every violation label is said at an accepted line.

The reference project's records, as an example of what to keep:

| Record | Set | Positives / negatives | TPR [95 % CI] | TNR [95 % CI] | AC1 | Hard TPR / hard TNR / trap TNR | Unstable | Time, cost |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| first | v1, 50 cases | 44 / 199 | 1.00 [0.92, 1.00] | 1.00 [0.98, 1.00] | 1.00 | (not measured) | 5 | 40 min, USD 22 |
| current | v2, 88 cases | 50 / 435 | 0.96 [0.87, 0.99] | 0.989 [0.97, 0.995] | 0.98 | 1.00 / 0.97 / 0.95 | 12 | 87 min, USD 42 |

The first record's perfect scores came from an easy set. The second is lower and worth more.

**Guide recommendation:** add the per-item scores and each item's advisory or blocking state (4.4) to the record. Plot TPR and TNR per item across records in the health report, so a slipping question is seen before the canary misses.

---

## 5. Hand mutants beside the reviewer (`E14`)

A rubric asks whether the change *reads* right. A hand mutant asks whether its tests would *notice* if it were wrong. The guide's Group V asks the blind reviewer to do both: "Whatever the runner's status, a blind reviewer also runs a handful of hand mutants of the branch's new code and reports the survivors (`E14`)."

**What the reference project does.** The blind review of a branch reports the changes it planted in the new code and whether the tests caught them, as ordinary findings. Two examples from its review reports, made generic:

- A finding (severity *fix soon*): "The planted change altered an input only." The reviewer's one mutant changed a test input, not the routine. The fix planted several mutants in the routine's tables and in its effort calculation, and each was caught. One more, removing an integer wrap, needed a real mutation tool.
- A note: "No test of its own for the new function; the planted change on this side alters an input only."

Separately, a nightly mutation-testing run lists surviving mutants as advisory output. Neither result is a target (`E14`): survivors are reported beside coverage, assertion-free tests and red-before-green results, and never turned into a score anyone is asked to raise.

**Guide recommendation: a procedure.** Run it in the same isolated worktree as the review, after the gate is green:

1. Pick 3 to 5 sites in the branch's new or changed **production** code, preferring the code the rubric items do not cover: boundaries (`<` vs `<=`), arithmetic operators, a dropped guard, a swapped branch, an off-by-one in a date or count, a removed wrap or rounding step.
2. For each site, apply one small change to the production code, never to a test or its input. Run the narrowest test set that covers the site, then restore the file.
3. Record each mutant as `file:line`, the change, and caught or survived, with the test that caught it.
4. Report each survivor as a finding. Make it `concern` by default, or `block` when the surviving change alters a public result the spec promises. Report the full list in the review record beside the rubric verdicts.
5. Never weaken a mutant to make it caught, and never count mutants as a percentage target.

Hand mutants and rubric items cover different ground. A rubric item (`E25`) can see that an expected value repeats the production formula. A mutant shows that such a test passes whatever the formula does.

**Guide recommendation:** once a mutation tool is in place, a hand mutant that keeps surviving is a candidate for a planted mutant in that tool's configuration, the same way a repeated rubric concern becomes a check (`V7`).

---

## 6. Adopting rubrics in a new project

**Guide recommendation:** take these steps in order.

1. **Start with three or four items** from the family files, chosen for the shortcuts your models actually take. `E25` (leaked algorithm), `E16` (record literals in tests) and `G13` (pass-through workflows) are common and cheap to plant.
2. **Rewrite every scope** for your folder layout, and every example in your domain and language.
3. **Write the case set before you trust the reviewer.** Start with at least 3 + 3 per item and one cannot-determine case each, following the hardening rules (4.2). Have a second person label them blind, and settle disagreements in `why.md`.
4. **Calibrate, and run every item advisory** until its per-item lower bound meets the bar (4.4).
5. **Turn on blocking item by item.** Run the canary weekly, add every dispute and escaped defect as a case, and let the ledger promote repeated checkable concerns into checks.
