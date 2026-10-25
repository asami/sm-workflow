# GoalPhase completion with best-effort repository synchronization

Status: accepted direction; initial completion/safe-deferral policy delivered, Human-confirmed continuation extension planned
Decision: 2026-10-23
Delivery: [next-development plan](../notes/goal-phase-post-completion-sync-plan.md)

The initial GPSYNC-01..08 producer boundary is verified and locally published,
and the CNCF Phase 116 owner has completed supported policy adoption and that
same Session. The Human-confirmed HEAD/dirty continuation requirements below
remain the target extension (GPSYNC-09..12 and amended earlier criteria), not a
claim that the complete specification is already implemented. Shared-skill
promotion remains separately pending.

## Completion and synchronization are separate outcomes

GoalPhase durably completes after its required implementation, validation,
review and commits have actually been accepted. Repository synchronization is
then a best-effort follow-up. A sync failure, missing effect permission, human
question or unresolved execution must not revoke that Phase completion.

Record and display the Phase outcome separately from the sync outcome. Sync
can be not attempted (with reason), in progress, succeeded, deferred, failed or
unknown. These are required business distinctions, not a frozen wire enum.
Never label an unsynchronized result as synchronized or a running/unknown effect
as stopped. A storage failure before durable Phase completion is a completion
failure; it must not be swallowed as a harmless synchronization problem.

Final Git success, a durable local-completion payload and the native Completed
lifecycle are distinct facts. The existing mechanical storage contract admits
only an unsuspended Active source followed by Active, or by Completed with
empty progression and suspension. A saved Active/`phase.Closed` local-completion
receipt followed by a refused terminal write remains a real completion failure.
Ordinary `advanceGoalPhase` consumes that exact saved receipt and appends only
its missing adjacent Completed successor. It does not repeat Final, checks,
review, accepted Submit, staging or source-history reflection. A later passive
Advance returns the same Terminal with unchanged native revision and history.

`GoalPhasePostCompletionSyncSpec` extends its existing completion-storage-boundary
acknowledgement failure/reopen case to count exactly that one append and zero
subsequent execution increments. The isolated installed case in
`NativeGoalReleaseRepairContinuationCliSpec` additionally requires an actual
SQLite terminal-write refusal and reopen under one newly selected immutable
image; its qualification conditions are in
[the release contract](goal-phase-release-repair-continuation.md).
These additions are evidence obligations, not an unexecuted validation claim.

Unrelated uncommitted work is not a global-clean prerequisite for Phase
completion. Use the accepted candidate, actual required commit results and
recorded work ownership. Outstanding work belonging to this Phase must still be
completed; do not infer ownership solely from a filename or silently include
new work in an already reviewed candidate.

## User-confirmed HEAD and dirty-state continuation — 2026-10-23

A moved HEAD or newly observed uncommitted changes must enter the existing Human
confirmation path, rather than become an unconditional structural failure. When
both are present, ask one combined question. Show the previously selected and
current HEAD, a concise commit/change summary, staged/unstaged/untracked scope,
and the proposed continuation and treatment of those changes. Do not require
the user to approve an opaque path count or repeat already recorded approval.

If the user confirms that the observed HEAD and uncommitted work are acceptable,
record that decision and continue the same GoalPhase/RepositorySync execution
from its current frontier. Record the current continuation selection separately
from the original base and accepted candidate history. Do not re-Start, reset
repair counts, require a clean tree, or reject the same acknowledged condition
again at a downstream gate. A negative answer or a requested correction waits
for that correction without discarding work.

Use existing Human question/answer, Workflow state and Journal/Evidence. Retain
only the actual observed repository/branch/HEAD, change scope, intended action
and answer needed for continuation; do not introduce content digests, permission
expiry, a new approval ledger or an AI review to approve the Human answer.
Resume after handback using the retained decision. Ask again only for a materially
new unacknowledged change affecting the selected action; elapsed time, caller or
worker replacement, and reopening the same state do not invalidate confirmation.

The ordinary continuation preserves uncommitted work without staging or committing
it. If a different route intends to include it in a checkpoint/commit, that effect
and scope must be made explicit in the question and authorized; a bare "continue"
is not an implicit request to commit, stash, discard or publish unrelated work.
Existing Git effect authority still applies. Show any additional actually needed
effect in the same decision where possible rather than serial approval prompts.

User confirmation is an authoritative continuation decision, not proof that
historical tests ran on a new tree. Retain previous PASS/failure/unknown results
with their actual candidates and reuse applicable evidence. A mere HEAD change
or dirty-state acknowledgment adds no automatic full test, review or Phase
restart. If the accepted development target actually changes, record the new
selection and specific remaining acceptance work or explicit user disposition;
never silently transfer an old PASS to changed inputs.

## Best-effort follow-up

Attempt synchronization when its target and required effects are already
selected and authorized. Reuse RepositorySync and CNCF typed Git execution;
Phase review or completion does not grant fetch, push or commit permission.
A disabled/no-push selection produces an explicit not-attempted/deferred result,
without an automatic approval question merely to finish the Phase.

After the observed HEAD/dirty state has been confirmed, continue through the
supported committed-candidate route without absorbing unrelated dirty work.
Include already-converged/local-ahead synchronization in the initial acceptance;
the same acknowledged dirty state is not by itself a deferral reason. A route
that would instead absorb that work into FULL_STATE must not be selected silently.
Do not remove only the old cleanliness predicate and leave checkpoint behavior
unchanged. Standalone RepositorySync retains its explicitly selected semantics.

Actual merge conflicts, a required unsupported worktree-changing integration,
missing effect authority or an uncertain in-flight effect can still defer sync.
Report that concrete reason, not a repeated "HEAD changed/dirty" refusal. Such
sync deferral does not revoke the durably completed Phase. No implicit stage,
checkpoint, stash, reset, deletion or force push is permitted.

Durably complete the Phase before any follow-up Start or remote effect. Retain
any actual linked RepositorySync identity, selected candidate/target and actual
result through existing Workflow persistence and Journal/Evidence. Define the
normal caller continuation for this link in the owning typed contracts; never
reopen a completed GoalPhase to represent sync progress, hide an untracked
background task, or make AI maintain a second execution ledger.

Reopening the completed Phase must not start another sync. Resume a known sync
through its own existing continuation; observe an uncertain Start/effect before
any retry. A crash after Phase completion but before sync starts leaves the
Phase completed and sync not attempted/unknown according to actual evidence.
No new outbox, timer, polling agent or reservation framework is required.

## Existing runs and validation reuse

Offer the normal supported policy-adoption/continuation path for an existing
PhaseClosing run whose required development results are already accepted.
Preserve its Session, original base, Plan/Step identities, accepted candidate,
commit/check/review references, history, consumed repair counts and original
success/failure/unknown outcomes. Retain the adoption as a new fact, without
rewriting old history or directly editing persisted Session data.

If a child Start or Git effect is already in flight or uncertain, retain and
reconcile its actual state; do not recreate it or reinterpret uncertainty as
nonexecution. An explicitly selected stricter sync-required condition is not
silently discarded: use the same normal policy-adoption boundary when changing
it. Planning this feature does not switch any live run.

Sync resumption alone does not replay Phase tests/reviews. Real integration or
candidate changes retain the normal applicable RepositorySync validation and
review obligations. The existing successful Phase result remains a statement
about its accepted candidate, not every later repository state.

## Deferred capabilities

General remote integration/merge while preserving unrelated dirty work may be
added independently. The initial route includes user-confirmed continuation and
committed-candidate synchronization; it does not require a universal dirty-merge
algorithm to complete a Phase and report a concrete remaining sync limitation.
No new worktree operation, general dirty-merge algorithm, unconditional AI gate
or compatibility alias is part of this initial delivery.
