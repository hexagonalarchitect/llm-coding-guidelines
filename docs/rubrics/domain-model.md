# Rubric: domain model

This family judges the domain-driven design of the code base:

- the model speaks the business's language;
- only the import layer knows a legacy system's file formats;
- an aggregate is the unit of one consistent change;
- a reaction reads the publisher's view as of the event it reacts to;
- one stored instance has one writer.

It holds the review parts of rules `A7`, `B11`, `B12`, `M5` and `M7`. It is adapted from the reference project's `evans-vernon` family (after Eric Evans's ubiquitous language and Vaughn Vernon's aggregate rules).

Each item is answered `violation`, `holds` or `cannot_determine`, in the format of [../rubrics.md](../rubrics.md#2-the-rubric-format-v1).

The examples use the guide's lending-library domain:
- members borrow items from branches;
- a loan accrues fines;
- each member has an account;
- every change is recorded in a journal.

The scopes use a generic layout: `kernel/`, `contexts/<context>/{contracts,domain,codec,application}/`, `workflows/`, `hosts/`, `infrastructure/` and `tests/`. Replace them with your own paths. All snippets are sketches, not run.

## A7 Only the import layer knows the legacy format

- **Question:** Is production code outside the import context free of knowledge of the legacy system's files: no record offsets, lengths, bit layouts, addresses or record numbers taken from the legacy format?
- **Scope:** every package under `src/` except the import context (`src/contexts/import/`): the kernel, every context's contracts, domain, codec and application packages, the workflows, the hosts and the infrastructure. No path is backticked after the word "except", because the runner reads every backticked path after it as an exclusion.
- **Violation when:** a constant, name or comment ties the code to the legacy layout. Examples:
  - a hex offset or record length "as in the legacy record" (`const DUE_DATE_AT = 0x5E`);
  - a bit mask over the legacy format's packed byte;
  - a field holding a record number or an index into one of the legacy files (`legacy_record: u32`);
  - slot arithmetic over the legacy record (`0x0310 + 0x26 * slot`).
- **Holds when:**
  - the numbers are business rules or the code base's own format (`const MAX_RENEWALS = 3`, a codec version, a field width of the code base's own export format with no reference to the legacy one);
  - a mention of the legacy system by name, or of a design document by ID;
  - hex literals with no tie to the legacy format, such as a bit flag of the code base's own type.

  Code inside the import context is out of scope.
- **Cannot determine when:** a bare numeric or hex constant has no name, comment or use that says whether it is a business rule, the code base's own format or the legacy layout, and the repository does not say either.
- **Pass example:** `const TITLE_WIDTH = 25 // our export format, version 1` holds: the width belongs to the code base's own codec.
- **Fail example:** `const MEMBER_RECORD_LENGTH = 0x6A // the legacy catalogue's member record`, in the members codec, is a violation: the codec now encodes the legacy layout outside the import layer.
- **Evidence:** the line holding the offset, mask, length, address or record-number field.
- **Severity:** block
- **Status:** L (`A7`'s mechanical check for address-shaped constants and decompiler-style names in the domain runs beside it)

## B11 The model speaks the language of the business

- **Question:** Are the names this change adds or changes in the domain (types, fields, union cases, modules and functions) words of the business rather than words of storage, databases or generic programming?
- **Scope:** every context's domain package (`src/contexts/*/domain/`).
- **Violation when:** a domain name speaks storage or plumbing: `Row`, `Table` (meaning a database table), `Entity`, `Record` as a type suffix, `Key`, `Val`, `Data`, `upsert`, `insert`, `calc_val`, `process_item`, `Repository`. A meaningless code (`kind: i32`, `status_code`) also counts.
- **Holds when:**
  - names read as the business does (`Hold`, `HoldPlaced`, `renews`, `FineCharged`, `OverdueNotice`);
  - the decider vocabulary (`decide`, `evolve`, `create`, `value`) and kernel wrapper names (`Money`, `Day`);
  - a business term that happens to sound technical, if the glossary defines it (in this domain a `Catalogue` or a `Shelf` is fine; in a sports domain the league `Table` is);
  - short local names in a lambda or a two-line scope.
- **Cannot determine when:** a name is an abbreviation or a term that could be business jargon (a cataloguing or acquisitions term), and neither the glossary nor the diff explains it.
- **Pass example:** `enum ReservationEvent { HoldPlaced(MemberId, ItemId) }` holds: a librarian would say it.
- **Fail example:** `struct LedgerRow { key: i32, val: Money, kind: i32 }` is a violation: a member's fines and payments are named as database rows.
- **Evidence:** the line declaring the first such name.
- **Severity:** concern
- **Status:** L

## B12 Several aggregates in one entry only by name

- **Question:** Does each application workflow in the change put the events of only one aggregate into a journal entry, unless the other aggregates' events come from a scheduled job or a policy registered with consistency `same-entry`?
- **Scope:** every context's application package (`src/contexts/*/application/`) and `src/workflows/`.
- **Violation when:** one workflow calls `decide` on two aggregates (a loan and the member's account, two branches' stock, a hold and the item it is on) and passes both event lists to one journal `record` call, with no registered policy or scheduled job named.
- **Holds when:**
  - a workflow decides and records one aggregate;
  - it reads another aggregate's state without recording that aggregate's events;
  - a second aggregate's events come from a policy looked up in the policy register as `same-entry`, or from a scheduled job;
  - it submits a command to the instance's queue and records nothing itself;
  - a workflow with no `record` call at all.
- **Cannot determine when:** a workflow records through a helper whose body is outside the diff and not in the repository, so the number of aggregates in the entry cannot be seen.
- **Pass example:** `loan.decide(Return { on }).and_then(|events| journal.record(on, events))` holds: one aggregate, one entry.
- **Fail example:** `journal.record(on, loan_events.chain(account_events))` is a violation: the loan and the member's account change in one entry with no registered policy.
- **Evidence:** the `record` call, or the list passed to it, that holds the second aggregate's events.
- **Severity:** block
- **Status:** L (`B12`'s mechanical check for a workflow with two `record` calls runs beside it)

## M5 A handler reads the publisher's view as of the event

- **Question:** When a handler or reaction in the change needs more than the event it reacts to, does it read the publishing context's view as of that event's sequence number, and never "current" or "latest" state?
- **Scope:** every context's contracts and application packages (`src/contexts/*/contracts/`, `src/contexts/*/application/`).
- **Violation when:**
  - a handler reads `views.current_availability`, `latest_fines`, `now` or a live aggregate to learn about the event;
  - a view-read port in the contracts is named `current...` or `latest...`, or documented as "now".
- **Holds when:**
  - the handler uses only the event's own data;
  - it reads `views.availability_as_of(envelope.sequence, item)`;
  - a view port takes the sequence number (`balance_as_of(sequence, member) -> Money`);
  - a command workflow (not a reaction to an event) reads the state it is about to change;
  - files with no handler and no view port.
- **Cannot determine when:** a handler calls a view function whose name says neither "as of" nor "current" (`views.stock(branch)`), and its declaration is outside the diff and not in the repository.
- **Pass example:** `let stock = catalogue.stock_as_of(returned.sequence, returned.event.branch)` holds: the stock as it stood after that return.
- **Fail example:** `let stock = catalogue.latest_stock(returned.event.branch)` is a violation: replaying the reaction later reads stock that includes later returns.
- **Evidence:** the line that reads current state, or the port field named or documented as current.
- **Severity:** block
- **Status:** L (`M5`'s mechanical check for a view port named `current...` runs beside it)

## M7 One writer per stored instance

- **Question:** Does every path in the change that issues commands to, or writes to, a stored instance (here: one library's journal) go through that instance's single ordered queue, so that no two writers or consumers can act on it at once?
- **Scope:** every context's application package (`src/contexts/*/application/`), `src/workflows/`, `src/hosts/` and `src/infrastructure/`.
- **Violation when:**
  - commands are submitted concurrently (`join_all`, `Task.WhenAll`, a parallel map over commands);
  - a timer, background job or second host appends to the store directly instead of posting to the queue;
  - the store's file is opened so that another writer may also open it (a shared-write mode);
  - a second consumer drains the same queue.
- **Holds when:**
  - commands are posted to the queue one after another;
  - pure computation runs in parallel (independent overdue calculations, each from its own inputs) and its results go to the queue as one command in a fixed order;
  - reads run in parallel;
  - the store opens its file exclusively or read-shared;
  - a Nullable store that writes nowhere;
  - an application workflow that records to the journal it is given (the queue's consumer runs it).
- **Cannot determine when:** commands are handed to a function whose body is outside the diff and not in the repository, and its name does not say whether it posts to the queue or writes directly.
- **Pass example:** `let notices = loans.par_iter().map(overdue_notice)` followed by `library.submit(SendNotices(notices, on))` holds: the parallel part is pure and the command is one.
- **Fail example:** `branches.iter().map(|b| library.submit(ReviewHolds(b))).join_all()` is a violation: the commands race, and their order differs from run to run.
- **Evidence:** the line that starts the concurrent submission, the direct append, or the file opening with the shared mode.
- **Severity:** block
- **Status:** L (`M7`'s mechanical checks for concurrent submission and the second-writer test run beside it)
