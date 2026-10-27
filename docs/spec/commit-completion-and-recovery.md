# Commit completion and recovery standardization

Status: adopted target specification; implementation and qualification pending.
Owner: sm-workflow development.
Plan: [Phase 6.8](../phase/phase-6.8.md).
Acceptance: [P68 checklist](../phase/phase-6.8-checklist.md).

## Predecessor boundary and reuse

The bounded P67-T1 partial-commit repair is a predecessor delivery, not proof
that this entire contract is implemented. Do not delay active Phase 6.7
restoration or enlarge its acceptance. Reuse actual delivered selection checks,
partial-chain handling, tests and installed qualification.

Reuse [purpose-based authorization](goal-phase-dependency-scope.md),
[selected closure recovery](goal-phase-closure-recovery.md),
[typed Git](normal-typed-git-workflow.md),
[source-history recovery](goal-phase-source-history-recovery.md),
[repair control](runtime-owned-repair-control.md) and
[recovery observations](recovery-observations-and-cause-resolution.md).
Close remaining connections and contradictory gates rather than building
another authorization ledger, executor or repair controller.

## Runtime-owned authorization

Authorization covers Component, purpose, acceptance conditions and operations,
including actual effects and explicit user exclusions. Planned/actual files are
work inventories, not per-file approval gates. Admit necessary same-purpose
file additions through the existing runtime route; do not infer Component
membership solely from a directory prefix.

Saved approved documents and historical digest fields remain evidence.
Neither document-SHA equality nor unchanged file counts determine approval
validity or continuation. Do not replace them with another seal/certificate.
Native Git object IDs remain Git identities: actual parent/tree/content
relationships needed to identify an effect are not document-SHA approval gates.

AI answers only an issued unresolved semantic question. Skills relay runtime
decisions and do not invent consent or stop conditions. Changed boundaries,
new effects or explicit exclusions retain genuine Human decisions. Never
fabricate an answer to a pending Human record.

## Same-purpose inventory and Plan adoption — 2026-10-27

Runtime evaluates an inventory amendment against the existing attributed
authorization: Component, purpose, acceptance conditions and permitted operations
(including effects and explicit exclusions). When all four remain covered,
runtime carries that authorization forward and records the necessary inventory,
Plan revision, candidate/staging selection and commit-registration updates through
their existing owners. A missing registration is preparation work, not a new
Human decision merely because another file or staging ordinal is needed.
This also applies before commit preparation; do not defer it until closure.

Use existing typed scope and recorded work relationships for deterministic
decisions. AI supplies only unresolved semantic classification when requested;
neither the caller AI nor a path-prefix heuristic grants authority. Ask Human
only for a genuinely uncovered boundary/effect, an explicit exclusion, or an
unresolved authorization decision. Do not require an independent new Human
record for every Plan revision or same-Component inventory append.

An existing Human wait caused solely by the obsolete fixed-inventory gate is
resolved by the public runtime continuation/adoption owner under applicable
existing authorization. Append a reasoned supersession referencing that authority;
preserve the old unanswered question without inventing a user answer. Keep
completed Steps, original base, accepted conditions/results, effect history and
repair counters. Inventory adoption consumes no product FIX cycle and refunds
none. Duplicate adoption reuses the recorded outcome without duplicate effects.
An outstanding genuinely new decision must not be silently superseded.

The P67-T4 ABI manifest incident is the representative case: packaging the
already authorized Task/FIX routes needs `src/main/car/abi-manifest.json` in the
same Component. Qualify a necessary analogous addition without new Human input,
including its actual selection/commit connection, and a contrasting uncovered
operation that still requests Human. The current bounded inventory-adoption
implementation requires fresh Human admission and is reuse material, not proof
of this permanent contract. No incident-specific exception or new approval
ledger is needed. See the [adoption handoff](../journal/2026/10/2026-10-27-phase-6.8-authority-carry-forward-handoff.md).

## Mechanical commit hygiene


Public property: `sm-workflow.commit.hygiene.mode`.
Use ordinary sm-workspace configuration resolution and existing precedence.

| Mode | Behavior |
| --- | --- |
| omitted or `auto-fix` | Apply supported mechanical corrections, recheck and commit when clean. |
| `error` | Report the finding without hygiene edits or a commit. |
| unsupported value | Normal configuration error, not an implicit fallback. |

Start with removal of extra terminal LF blank lines, preserving the terminal
newline, other bytes and mode. Reuse the narrow existing EOF rule; Git whitespace
diagnostics alone do not authorize changing intentional data/test expectations.
Broader trailing-space, indentation, CRLF and formatter changes are outside
initial scope.

In ordinary Advance: inspect selected candidate -> correct once if applicable
-> update selected staging -> recheck -> commit. If findings remain, return
their actual diagnostic without a repair loop. Use managed file and typed Git
operations, not a shell/Python repair subprocess. Preserve unrelated changes and
partial staging; never stage an entire file just to include a hygiene correction
when that would include unrelated edits.

Reuse applicable compile/test/review results for this established correction,
retaining their original targets and the actual correction relationship.
Normalization alone requires no AI call, independent review, whole-suite test
or repeated business approval. It is preparation, not a product FIX batch:
integrate this distinction with existing RRC counting without refunding consumed
counts or exempting ordinary semantic/minor fixes generally.

## Outcome selection

| Observed state | Action |
| --- | --- |
| Clean authorized candidate | Existing selected commit path. |
| Supported hygiene finding | Apply effective mode and retain outcome. |
| Confirmed ended/no commit, correctable preparation | Correct and continue under existing applicable authority. |
| Successful effect with uncollected result | Collect/reconcile it without re-executing. |
| Known successful partial commit with exact accepted remainder | Qualified bounded completion when existing authority covers it. |
| Live or unknown execution | Observe/collect actual state; absent output grants no replay. |
| Actual failed effect | Retain failure and establish actual effects before selecting recovery. |
| Unresolved meaning or uncovered effect | Specific semantic or Human question. |

Use existing public continuation and durable intent/result records. A new
justified execution retains prior attempts. Repeated collection after success
creates no new commit. Do not generalize the two-commit case to arbitrary chains.
Preserve actual P -> C -> D parentage; never flatten D into a fictitious child
of P. Retain ordinary single-commit and RepositorySync merge-parent semantics.
Recording reconstruction, next Step, Phase evidence and Journal must agree.

## Completion records and Terminal scope — 2026-10-27

Runtime owns the selected work's completion records and local closure, including
necessary tracked English journal/checklist/handoff updates and their commit.
The ordinary route is accepted implementation/checks/review -> prepare completion
records -> apply and check the scoped record changes -> commit through the shared
owner -> persist actual closure/result -> return scoped Terminal. If the existing
Step route already committed implementation, reuse that commit and perform only
the outstanding scoped record commit; do not amend/rewrite or duplicate it.
Coalesce record edits into the existing closure commit when still possible.

Mechanical result projection needs no AI. When an English narrative requires
semantic authorship, runtime issues that work and receives its result through
the existing continuation before declaring closure complete. The caller may author
the text but must not start an unissued post-Terminal editing/commit workflow or
invoke cncf-commit-execution as a substitute for runtime closure. Do not add an
independent review or FULL solely for evidence-backed completion prose; reuse
applicable acceptance and perform only relevant record consistency/hygiene checks.
Actual changed business meaning uses the existing affected assessment path.

Tracked records describe established acceptance facts and outstanding effects
truthfully. The eventual commit identity, terminal event and delivery outcome
belong in runtime Journal/Evidence/result after the effect. Do not require a
tracked document to contain its own commit ID or final Terminal receipt, creating
an endless record/commit loop. Raw execution evidence remains outside Git.

`Terminal / completed` must identify its actual completion scope and identity,
parent relation when known, acceptance/record/commit results, and separate
publication/synchronization status. Use the existing typed result model and CML
projection; exact field names are an implementation choice. No artificial parent
Phase is required for a standalone Task. Nested completion is not outer completion.

| Scope | Meaning of completed |
| --- | --- |
| Selected Stage/Step local closure | Its admitted work, required completion records and local commit obligation are fulfilled; enclosing Phase may remain in progress |
| Phase local closure | All selected Phase obligations and its required records/commits are fulfilled; optional/best-effort remote sync is reported separately |
| Delegated delivery | Delegated acceptance, mandatory local commit, required publication and result-return obligations are fulfilled; requester adoption remains a separately attributed result |
| Existing standalone FIX or another explicitly uncommitted entry | Its unchanged contract is fulfilled and uncommitted status is explicit; this specification does not add a commit obligation to test-fix |

If records, required commit or closure-result persistence remain unfinished,
report the actual nonterminal closure state or failure through the existing owner;
do not emit overall completed merely because implementation was accepted. The
implementation/check/review acceptance remains valid. Commit success with an
uncollected closure result is reconciled, not retried. Stage completion never
marks the enclosing Phase completed. Optional sync failure/deferment neither
revokes local closure nor permits a false remote-success claim. Phase 6.9 owns
sync orchestration optimization, not this local closure correction.

For existing executions that already returned Terminal before record closure,
keep that historical event and its original scope. Reconcile remaining records
through a qualified public runtime closure continuation linked to the same owner
and Session, preserving bases, history, repair counts and successful results.
Do not overwrite Terminal history, re-Start development, replay accepted Submit
or perform an external skill commit. The public route must collect an already
completed external record commit if attributable, rather than recreating it.
Normal record preparation consumes no product FIX cycle and refunds none.

## Adoption and evidence

Record effective mode, findings, corrections, effect/result and continuation
reason through existing Journal/Evidence/Finding, without an AI call for recording.
Recovery success remains distinct from root-cause resolution.

Keep Goal, Session, original base, Plan, accepted results, failures/UNKNOWN,
repair counts and applicable checks. Use public adoption, not private state
edits, re-Start, accepted Submit replay or counter reset. Preserve historical
record meanings and state which runtime can consume new partial-chain records.

Current P67-T1 development approval explicitly excludes real consumer Session
operations and reserves a later real-target Human decision. This planning does
not remove that restriction or answer that question. Future normal recovery
must not universally inherit that extra confirmation requirement.

Step, release and RepositorySync share preparation/recovery policy with their
own acceptance boundaries. Phase 7 consumes this owner. Runtime-owned
publishLocal remains the publication owner, including necessity and result
collection; no AI publication runner is introduced.
