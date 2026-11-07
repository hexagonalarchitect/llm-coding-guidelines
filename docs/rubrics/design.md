# Rubric: design

This family judges module depth, after John Ousterhout's *A Philosophy of Software Design*:

- an interface much smaller than the behaviour it hides;
- a different abstraction in each layer;
- no complexity that does not pay for itself.

It holds the review part of rule `G13`, and is adapted from the reference project's `ousterhout` family. Each item is answered `violation`, `holds` or `cannot_determine`, in the format of [../rubrics.md](../rubrics.md#2-the-rubric-format-v1).

**What the reference project has.** Its set holds **one** item for `G13`: the pass-through function and the sinkhole workflow, the red flag `G13` names. The other red flags, and the item "complexity added for speed without a cited measurement", are listed with one-line tests in a design note. Each becomes an item, *with its own calibration cases*, before it is sent to the reviewer. Advisory helpers feed this item and never block on their own: a depth ratio, a pass-through scan and a duplicate-literal scan.

**Guide recommendation:** follow the same path for every red flag you add. Write the item, write at least 3 known-bad, 3 known-good and 1 cannot-determine case for it, calibrate, and keep it advisory until its per-item accuracy clears the bar ([../rubrics.md, 4.4](../rubrics.md#44-the-bar-and-when-a-question-may-block)). Candidates, with one-line tests:

| Red flag | One-line test |
| :--- | :--- |
| Shallow module | The interface is about as complex as the implementation. |
| Information leakage | Two modules must change together for one design decision. |
| Temporal decomposition | Code is split by the order things happen, not by what they know. |
| Overexposure | Common use of the module must know about rare features. |
| Repetition | The same non-trivial fragment appears in several places. |
| Special-general mixture | General-purpose code holds a special case for one caller. |
| Conjoined methods | One function cannot be understood without reading another. |
| Hard to name | No precise name fits; the thing does several jobs. |
| Hard to describe | Its documentation must be long to be complete. |
| Non-obvious code | Behaviour or meaning cannot be understood quickly. |
| Speed without measurement | Complexity added for performance, with no cited measurement. |

The item below uses the guide's lending-library domain. All snippets are sketches, not run.

## G13 No pass-through functions or sinkhole workflows

- **Question:** Does every function, module or workflow this change adds or changes do more than forward its arguments, unchanged, to a single other function?
- **Scope:** all source and test code under `src/` and `tests/` (the planted-breach folders excepted).
- **Violation when:**
  - a new function's whole body is a call to one other function with the same arguments (`fn add_money(a, b) { add(a, b) }`);
  - a module re-exports another's functions one by one, each a forwarding wrapper (`fn place_hold(journal, hold, member) { PlaceHold::run(journal, hold, member) }`);
  - an application workflow only calls the domain's `decide` and returns its result. This is the *sinkhole workflow*. The guide's statement of `G13` makes it a concern when more than a fifth of a context's workflows are like this.
- **Holds when:**
  - the function adds a step: a lookup, a check, a mapping, recording, or stamping the time;
  - it fixes a meaningful argument (`fn advance_a_week(d) { advance_days(d, 7) }`);
  - an infrastructure wrapper's `create` and `create_null` choose between implementations;
  - an accessor reads a field.
- **Cannot determine when:** the forwarding target is outside the diff and not in the repository, and its signature may differ, so the function may be adapting types.
- **Pass example:** `fn create_null() -> LoanStore { LoanStore::new(NullFile::new(), Vec::new()) }` holds: a Nullable form is a second implementation, not a pass-through.
- **Fail example:** `fn run(command: LoanCommand, loan: Loan) -> Result<Vec<LoanEvent>, Refusal> { Loan::decide(command, loan) }`, in the `RenewLoan` workflow, is a violation: the workflow adds nothing to the domain call.
- **Evidence:** the line declaring the forwarding function.
- **Severity:** concern
- **Status:** L
