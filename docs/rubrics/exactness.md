# Rubric: exactness

This family judges what keeps the code base replayable and, where it reimplements a reference program, provably equal to it:

- arithmetic at the reference's working width, through kernel wrappers;
- a core that speaks of actors and roles instead of "the human";
- a core entity free of any one pluggable engine's attributes;
- events that carry the facts they were decided on.

It holds the review parts of rules `D4`, `D8`, `D9` and `P11`. It is adapted from the reference project's `exact` family.

`D4` applies only when reimplementing a reference program, and greenfield projects can drop it. The other three apply to any event-sourced core.

Each item is answered `violation`, `holds` or `cannot_determine`, in the format of [../rubrics.md](../rubrics.md#2-the-rubric-format-v1). The examples use the guide's lending-library domain. For `D4`, imagine that the code base must reproduce a legacy circulation program that computed fines in 16-bit integers. All snippets are sketches, not run.

## D4 Exact arithmetic through kernel integer wrappers

- **Question:** Does every new or changed arithmetic on reference-width integers go through the kernel's wrappers, which widen to the reference's working width before the operation and truncate only on store, with no raw narrow arithmetic, raw narrowing conversion or floating point in the domain?
- **Scope:** `src/kernel/` and every context's domain package (`src/contexts/*/domain/`).
- **Violation when:**
  - the domain multiplies, adds or divides narrow values directly (`daily * 7i16`, `I16::value(a) * I16::value(b)`);
  - it converts with a raw narrowing cast;
  - it goes through floating point;
  - a kernel wrapper operation computes in the narrow width before widening (`I16(a + b)`);
  - a wrapper rounds division differently from the reference (C truncates toward zero).
- **Holds when:**
  - the domain uses the wrappers' operations (`I16::add`, `I16::mul`, which return the wide type) and `I16::store`, or `Money` operations;
  - wide arithmetic on a wrapper's widened result (`I16::mul(a, b) / 5`);
  - counts and indexes that are not reference values (a list length, a round number);
  - the kernel's own single explicit narrowing inside `store` or a conversion function.
- **Cannot determine when:** an operand's type is declared outside the diff and not in the repository, so it is unknown whether the arithmetic is on a reference-width value.
- **Pass example:** `I16::add(fine.daily, surcharge).store()` holds: widened, then truncated on store.
- **Fail example:** `(daily * 7i16) as i32` with `daily: i16` is a violation: the product wraps at 16 bits before the widening, where the reference computed it at 32.
- **Evidence:** the line with the raw operation, conversion or floating point.
- **Severity:** block
- **Status:** L (`D4`'s mechanical check for raw conversions outside the kernel, and the wrappers' property list, run beside it)

## D8 No special human user in the core

- **Question:** Does the domain change speak of who acts only through actors and their roles, with no notion of a human, user or operator-controlled unit?
- **Scope:** every context's domain package (`src/contexts/*/domain/`).
- **Violation when:** a type, field or condition singles out the human: `HumanBranch`, `UserBranch`, `is_operator_controlled`, `if branch == network.user_branch`, or a branch of logic that skips automation for "the user's branch".
- **Holds when:**
  - commands carry an `Actor` with `Role`s (`LibrarianOf(branch)`, `BranchManagerOf(branch)`, `Administrator`), and rights are a pure function of them;
  - "member" meaning a library member, and "manager" meaning a role, are fine;
  - any number of librarians can act.
- **Cannot determine when:** a field or parameter of a type declared outside the diff (`controller: BranchController`) may encode who controls a branch, and its declaration is not in the repository.
- **Pass example:** `fn may_waive_fine(actor, branch) -> bool { actor.roles.iter().any(|r| r == LibrarianOf(branch)) }` holds: a role, not a human.
- **Fail example:** `if transfer.from == network.user_branch { return None }` is a violation: the core knows which branch the human runs.
- **Evidence:** the line that names or tests for the human.
- **Severity:** block
- **Status:** L (`D8`'s mechanical check for a type named for the user runs beside it)

## D9 No engine-specific attributes in the core entity

- **Question:** Does the core entity stay free of attributes that only one pluggable engine reads, so that each engine builds its own profile through its own mapping? Here the core entity is the member in the members context, and an engine is, for example, one of several recommendation engines.
- **Scope:** the core entity's context and the engines' context, every layer (`src/contexts/members/`, `src/contexts/recommendations/`).
- **Violation when:** a field is added to `Member`, its preferences or a members event, and the field is documented or named for one engine (`shelf_bot_score`, `quick_pick_weight`, one engine's `genre_vector`).
- **Holds when:**
  - the field is a lasting fact every engine may read (date of birth, preferred branch, preferred languages);
  - an engine's profile type and mapping live in the engines' context, built from the core entity (`ShelfBotMapping::profile(member)`);
  - session state lives in the engines' context.
- **Cannot determine when:** a new attribute's documentation does not say who reads it, and no reader is in the diff or the repository.
- **Pass example:** `struct ShelfBotProfile { recent_genres: Vec<Genre> }`, built by `ShelfBotMapping::profile(member)` in the recommendations context, holds: the engine's view is its own.
- **Fail example:** `shelf_bot_score: Score // the other engines ignore it`, in the members domain's `Member`, is a violation: one engine's attribute sits in the core.
- **Evidence:** the line declaring the engine-specific field.
- **Severity:** block
- **Status:** L (`D9`'s dependency and field checks run beside it once the engines' context exists)

## P11 Events carry the facts they used

- **Question:** Can every new or changed event be applied by `evolve` without reading data that may have changed since the event was decided?
- **Scope:** every context's contracts and domain packages (`src/contexts/*/contracts/`, `src/contexts/*/domain/`).
- **Violation when:** an event lacks a value its `evolve` needs, and `evolve` looks that value up in current state, a global setting, a tariff table or external content. Examples: a fine computed from today's daily rate, a loan period looked up by item type in the current rules, a replacement cost read from the current catalogue.
- **Holds when:**
  - the event carries the decided values (amount, rate, date, rules version or content version), and `evolve` uses only them and the aggregate's own state;
  - `evolve` reads only data the event identifies by a fixed version;
  - a change with no events, or with events that already carry all they need.
- **Cannot determine when:** the `evolve` for a new event is not in the diff and not in the repository yet.
- **Pass example:** `FineCharged { amount, on } => Account { owed: owed + amount, ..state }` holds: the amount was decided once and travels with the event.
- **Fail example:** `LoanOverdue { item, days } => Account { owed: owed + tariff.daily_rate(item) * days, ..state }` is a violation: a replay after a tariff change gives a different balance.
- **Evidence:** the line in `evolve` that reads the changing data.
- **Severity:** block
- **Status:** L (`P11`'s mechanical check runs beside it: every event type carries a rules-version field, or the glossary marks it rule-independent)
