# Workflow design choices and rejected alternatives

These considerations explain the related design/notes. They do not replace
their contracts, change Phase status or report implementation progress.

## Separate the judgment from the action

The [deterministic closing notes](../../../notes/deterministic-operations-and-closing.md)
separate semantic review from an actual commit. The reason is recoverability:
an accepted review can survive a caller interruption while the effect has not
yet happened, or an effect can succeed before its response is returned. Making
the AI interpret both as one successful conversation would hide that distinction
and encourage duplicate effects. Runtime-owned progression also removes AI work
whose only purpose is waiting or bookkeeping.

## File inventory is not a new requirement decision

The [repair-control design](../../../design/runtime-owned-repair-control.md) grew out of
repairs blocked because a necessary source file was missing from an earlier
inventory. Same-purpose, same-owner implementation additions should not require
a new Human approval for each file. Genuine changes to acceptance, ownership or
permitted effects remain decisions. The rejected alternative made accidental
file-list detail more restrictive than the authorized development purpose.

## Preserve uncertainty without making resumption exceptional

An interruption does not prove nonexecution. Conversely, a harmless setting or
source correction should not strand a recoverable Session. The
[validation-continuation design](../../../design/goal-task-validation-continuation.md)
keeps those facts separate so the execution platform can collect what happened
and resume when its real conditions are satisfied. Copying old inputs, requiring
unchanged-body certificates or clearing an intent manually would create another
control system instead of recovering the existing one.

## A forced ending must remain an ending

Repairing HEAD, candidate, Plan and checks merely to close an abandoned run can
trigger an unbounded sequence of new repair work. Forced closure instead records
the actual Human decision and incomplete work while retaining prior state.
Accepting an outcome is not the same claim as ordinary successful validation.
The [ending dispositions](../../../phase/maintenance-dispositions.md) retain actual
management decisions separately from this reasoning.

## Reuse evidence instead of repeating a review ritual

[Candidate admission](../../../notes/candidate-admission-model.md) and
[verification-cost notes](../../../notes/managed-test-suites-and-verification-cost-guard.md)
separate applicability from the age or position of a review. An actual changed
input can need a focused new check; a new checklist row or caller handoff alone
does not justify repeating every successful check. This preserves quality while
reducing AI cost. Old PASS text alone is not applicable evidence either.

## Duration and special environment are different test properties

The [test migration notes](../../../notes/label-driven-test-migration-plan.md) use the
standard `@DoNotDiscover` for explicit-only tests. An external DB requirement
does not imply long duration, so marking every external-service test `@Slow`
would misdescribe it. Slow can be combined when duration actually warrants it.
The custom ON_DEMAND concept duplicated standard discovery behavior.

Large application-copying fixtures were also questioned because their build,
publication and launcher costs can obscure the intended failure. Small real
fixtures should establish the relevant behavior; explicit deployment scenarios
remain separate when they are actually required.
