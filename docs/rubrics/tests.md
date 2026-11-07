# Rubric: tests

This family judges the tests. It draws on two sources:

- Vladimir Khorikov's four pillars: a test protects a behaviour, resists refactoring, and reads as that behaviour.
- James Shore's *Testing Without Mocks*: test data is built through builders in the domain's language, the real code runs under test, and expected values do not leak the algorithm.

It holds the review parts of rules `E11`, `E16` and `E25`, and is adapted from the reference project's `khorikov-shore` family.

Each item is answered `violation`, `holds` or `cannot_determine`, in the format of [../rubrics.md](../rubrics.md#2-the-rubric-format-v1). The examples use the guide's lending-library domain. All snippets are sketches, not run.

These three items are the cheapest to plant and the most common model shortcuts. Start a new rubric set with them (see [../rubrics.md, 6](../rubrics.md#6-adopting-rubrics-in-a-new-project)).

## E11 Tests read as behaviour, one act each

- **Question:** Is each new or changed test named for one behaviour that a user of the system would notice, in the domain's language, and does it make one act?
- **Scope:** every test package (`tests/`, and each package's own test folder), including the test-data builders. The planted-breach folders are not tests.
- **Violation when:**
  - the name follows `Method_Scenario_Result` (`decide_Renew_ReturnsRenewed`);
  - the name starts with "should";
  - the name is a public function name of the code under test (`"Fine.daily_rate"`);
  - one test makes two acts on different behaviours: two `decide` calls checked for two outcomes, typically joined by "and" in the name.
- **Holds when:**
  - the name says what happens, in domain terms, however long (`a member with three overdue items cannot borrow another`);
  - several checks on the one outcome;
  - a table-driven test that loops over cases of one rule;
  - a kernel wrapper test that names the arithmetic it pins (widening, truncation);
  - a file with no test, such as a builder module.
- **Cannot determine when:** the test's name comes from a shared helper or a property generator outside the diff and not in the repository.
- **Pass example:** `test "a hold on a returned item goes to the first member in the queue"`, with one act and one check, holds.
- **Fail example:** `test "renewing extends the due date and returning clears the hold"`, with two `Loan::decide` calls, is a violation: two behaviours, two acts.
- **Evidence:** the test's name line.
- **Severity:** concern
- **Status:** L (`E11`'s mechanical name check and act count run beside it)

## E16 Test data comes from builders

- **Question:** Does every new or changed test build its domain values through builders written in the domain's language, never by spelling out a domain record literal in the test, and are new builders themselves named in the domain's language?
- **Scope:** every test package (`tests/`, and each package's own test folder), including the test-data builders.
- **Violation when:**
  - a test body constructs a domain record field by field (a member, a loan, an account);
  - a builder is generic plumbing rather than domain language (`set_field("age", 70)`, `make_rec`, `with_value`).
- **Holds when:**
  - data is built as `a_member().aged(70).with_card_expiring(day(2027, 1, 1))`;
  - kernel values (`Money::pounds(2)`, `day(2026, 7, 1)`) and command or event values are written inline;
  - an expected public event is written as a literal in an assertion;
  - record literals sit inside the builders module itself;
  - a language's struct-update form spreads a builder's result (`Loan { due, ..a_loan() }`), when the project's rule allows it.
- **Cannot determine when:** a literal's type cannot be told from the diff or the repository; it may be a test-local type, not a domain type.
- **Pass example:** `let senior = a_member().aged(70).with_loans(2)` holds: the builder reads as the domain and hides the other fields.
- **Fail example:** `let full = Member { id: MemberId(12), loans: vec![...], fines: Money::zero(), card: Card { ... } }`, in a borrowing test, is a violation: the test breaks whenever `Member` gains a field.
- **Evidence:** the line that opens the record literal, or the builder's declaration line.
- **Severity:** concern
- **Status:** L (`E16`'s mechanical check for domain record literals outside the builders runs beside it)

## E25 No test leaks the algorithm

- **Question:** Is every expected value in the change's tests a fixed, independently known value, never computed with the production function or the same chain of operations over the same inputs?
- **Scope:** every test package (`tests/`, and each package's own test folder), including the test-data builders.
- **Violation when:**
  - the expected value calls the production code that computes the answer (`Tariff::fine_for(Adult, 3)`, `Fines::cap(Fines::daily(item))`);
  - it repeats the production chain (`loans.iter().map(...).fold(Money::zero(), Money::add)` for a member's total owed).
- **Holds when:**
  - the expected value is a literal (`3`, `Money::pence(240)`), hand-worked in a comment if needed;
  - a kernel wrapper property compares against the widened reference computation (`(a as i32 * b as i32) as i16`), which `D4` requires as an independent oracle;
  - production code is used only to arrange the inputs;
  - a file with no expected values.
- **Cannot determine when:** the expected value comes from a helper outside the diff and not in the repository, so whether it reuses production code cannot be seen.
- **Pass example:** `check_eq!(owed(after), Money::pence(240), "three days at the adult rate")` holds.
- **Fail example:** `check_eq!(owed(after), Tariff::daily_rate(Adult) * 3, "three days at the adult rate")` is a violation: if `daily_rate` is wrong, the test agrees with it.
- **Evidence:** the line that computes the expected value.
- **Severity:** concern
- **Status:** L (`E25`'s mechanical heuristic, an identical free-function call on both sides of one assertion, runs beside it)

A hand mutant complements this item. `E25` sees a leaked formula in the test's text; a mutant of the production formula that survives shows the leak in behaviour. See [../rubrics.md, 5](../rubrics.md#5-hand-mutants-beside-the-reviewer-e14).
