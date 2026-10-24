# GoalPhase selected Step closure recovery

Date: 2026-10-24 (Asia/Tokyo)

The normal generated `GoalPhaseService.describeGoalPhaseClosureRecovery(sessionId)`
and `recoverGoalPhaseClosure(input)` operations support an existing Session paused
at a selected Step's unresolved native Closing commit intent. They preserve its
original accepted implementation, successful checks, independent review, Plan,
generation, history and repair counters. They grant no new semantic acceptance.

## R1 — Exact description and historical uncertainty

Description reconstructs contiguous native history, including earlier completed
Steps, without live Git during history traversal. It describes only an actual
unresolved commit intent. A failed structural precheck without an intent, a clean
check without an intent, a completed result, other stages and unexplained history
gaps refuse.

The strongly decoded target includes Session/definition/participant, Plan and
revision, Step, generation, original Closing source revision, current frontier,
intent identity/reference revision/native record revision, ordinal and attempt,
parent, reviewed candidate, accepted review, original logical owned paths, any
saved actual execution summary and the current actual Git observation. Public
JSON uses the normal generated naming/normalization rules; generation retains
its native Long range and ordinal/attempt require JSON integers.

Git observation uses existing typed `git_*` operations in the current owning
UnitOfWork. It reads the installed root, branch, HEAD/tree, index, merge and
selected state and actual commit metadata, then checks for observation drift.
Physical `selectedPaths` comes from the typed value of
`SelectedIndexChanges(selection, originalParentHead)`, including deletions,
additions and rename destinations. The reviewed-candidate comparison separately
determines `selectedChangesEmpty`. Selection does not come from declared-path
metadata or a comparison against current HEAD: it must remain available after
an exact matching commit already exists. The
application never discovers selection by reading configuration, argv or Git
output. Description writes no records and supplies no authority. These separate
reads are not an atomic filesystem snapshot.

An old `.v3` closure receipt keeps its exact codec and meaning. An intent with no
execution summary remains **unknown**. A later dry-run or unchanged HEAD cannot
establish historical Failed(1), NotStarted, NotExecuted or success. The current
observation and any actual saved terminal summary remain distinct facts.

## R2 — Existing authority and independent Human direction

An actual saved local `CommitSelected` execution is known ended only when its
bound summary has termination exactly `Exited(code)`, a matching exit code and
consistent outcome (`failed` with nonzero code or `succeeded` with zero). An
absent summary, an unknown/live/cancelled/timed-out termination, an incomplete
result, nonzero alone, unchanged HEAD alone or stderr text grants no automatic
continuation. This closed local program disables hooks and signing and has no
remote effect. Independently existing Step closure authority remains required.

Read-only description returns `continuation-ready`, empty Human choices and
`advanceGoalPhase` when those saved terminal facts, current typed Git facts and
existing authority admit one of the two R3 continuations. Otherwise it retains
`decision-required` and the genuine independent Human route below. Description
does not persist a continuation or grant authority.

The installed independent evidence purpose is `goal-phase-closure-recovery`.
`NativeHostPreparationService.prepareEvidence` accepts kind `closure-recovery`
with a generated description as request, generated recovery Answer as result,
participant, actual evidence reference, execution evidence, trusted issuer and
time. Passive preparation encodes an import row. It provisions nothing and
grants no execution or recovery authority. The normal trusted-host evidence
reader must independently admit the exact complete target, choice, rationale,
participant, reference and issuer.

`AuthorizeNewAttempt` and `ReconcileCommit` require `priorExecutionEnded=true`
in both Answer and recovery Input, attested by that independently trusted host
record. The production owner must retain actual terminal invocation evidence
for the old execution owner before issuing this attestation. Preparation does
not discover a process or verify terminal provenance from arbitrary caller data.
An AI-supplied Boolean, authenticated caller, unchanged HEAD or missing result
receipt cannot substitute for the independent attestation. Test fixture evidence
is fixture-only and never authorizes a real Session.

`Defer` is available without asserting prior execution ended. It preserves native
history and Git state and creates no mechanical direction. It grants no effect.
Its current target and independent Human evidence still must match.

## R3 — Immutable direction or observed continuation

Ordinary Advance may save one small private typed `Continuation(target, action)`
at the exact execution frontier through the existing guarded mechanical
receipt/history boundary. It is distinct from Human `Direction`; it fabricates
no Human reference or `AuthorizeNewAttempt` evidence.

| Automatic action | Required saved execution and actual observation | Ordinary Advance |
| --- | --- | --- |
| `retry-preparation` | Actual failed nonzero Exited result; original branch/root, no merge, original parent HEAD/tree, exact reviewed index, selected worktree clean, all pending/selected paths owned | Recheck the exact observation and existing Step closure authority, save one fresh selected structural check and incremented attempt intent, invoke local commit once |
| `reconcile-commit` | Actual consistent Exited result; actual HEAD is a single-parent child of the original parent with exact reviewed tree/index and clean selected state | Recheck the observation, authority and typed commit metadata, then record normal StepCommitObserved without invoking commit |

The original execution summary and accepted input remain immutable. Each
ordinary Advance invokes at most one commit; a new failure returns honest Wait
with its own actual summary. There is no internal retry loop or repair-count
refund. An unresolved old effect or new authority still uses explicit Human
direction; no current observation alone substitutes for historical termination.

Recovery compares the supplied target to freshly described current facts and
appends only an immutable typed mechanical direction with a normal conditional
transition at that exact frontier. It never executes commit, calls Advance,
re-Starts a Session, submits semantic work or repeats successful Step checks or
review. Missing evidence, crossed/foreign/stale/duplicate direction, different
selection and unexplained history gaps refuse.

| Choice | Required actual observation | Later separate ordinary Advance |
| --- | --- | --- |
| AuthorizeNewAttempt | Original branch, no merge, original parent HEAD/tree, exact reviewed index, complete owned selected state, 1..256 unique safe physical paths within original logical ownership | Recheck the exact live observation and physical selection, retain normal Step closure authority, save fresh check and intent under an incremented attempt, invoke the selected commit once |
| ReconcileCommit | Actual HEAD is a single-parent child of the original parent, exact reviewed tree, clean selected state and exact selected paths | Independently recheck actual typed commit metadata and record normal StepCommitObserved without another commit |
| Defer | Exact current described unresolved target | No direction and no continuation granted |

The new attempt retains the original input/source/generation/controller and old
unknown intent. A changed host path vector alone cannot discharge that intent.
The target explicitly binds a corrected physical vector inside unchanged logical
ownership. A separate Advance rejects drift before saving the new check or
intent. Host bootstrap independently requires CheckSelectedIndex,
SelectedIndexChanges and CommitSelected declarations present for the same
target/selection to agree on their physical vector. Partial kind sets remain
valid; each actual operation still needs its own grant.

Attempts are bounded to 0..255; revision arithmetic refuses exhaustion. History
traversal follows only the contiguous intent, optional actual execution summary,
explicit recovery direction, and fresh attempt or observed reconciliation. It
does not reset Start/cursors or invent controller acceptance. Legacy failed EOF
precheck retry and its bounded correction rules remain intact.

## R4 — Terminal summaries and interruption

Every newly observed selected commit result is saved in a separate bounded typed
mechanical execution-result receipt **before** the executed acknowledgement and
before `valueC` can discard a failed result. The receipt records operation,
target, outcome, actual process termination and exit code, actual selected paths,
and sanitized stderr bounded to 8192 bytes with an explicit truncation indicator.
Diagnostics are explanatory only; no stderr parsing controls recovery.

Failures and incomplete results return honest Wait. A failed commit is never
reported as a selected-index precheck failure. A prepared-intent interruption
before a summary leaves the old result unknown. Interruption after a summary
retains that actual terminal fact; it does not authorize replay. Even a saved
success without a committed native receipt requires explicit reconciliation or
independently authorized new-attempt direction under its actual current state.

Git effects and storage are not one atomic transaction. The runtime attestation
that the old owner ended is required because recovery cannot safely assume an
old effect is inactive. No process cancellation, stale-owner takeover, automatic
retry, arbitrary discovery scan, private SQLite mutation or manual production
commit is part of this contract.

## R5 — Executable regression evidence

`GoalPhaseClosureRecoverySpec` covers unchanged `.v3` bytes, exact target/name
roundtrip and Long generation, independent purpose/issuer/participant/reference/
target/prior-ended admission, passive Defer, both interruption windows, immutable
unknown intent traversal, preserved accepted check/review/counters, stale/crossed/
duplicate/gapped rejection and exact single-parent commit reconciliation.

`GoalPhaseNativeClosureSpec` retains legacy failed EOF retry and correction
coverage. `NativeGoalClosureRecoveryCliSpec` creates a fresh isolated normal
Session, performs ordinary Start/implementation/Test/review once, induces an
actual selected commit pathspec failure, then uses public description, trusted
fixture preparation, public recovery and a separate ordinary Advance. It checks
Defer, prior-ended refusal, stale/foreign/selection rejection, physical drift,
retained history/accepted work/counters and exactly one new actual commit in the
same Session. A separate fixture arranges an independently observed matching
prior-owner commit and proves reconciliation creates no duplicate. SQLite reads
are read-only observations; fixtures never seed or edit native rows.

The optional `smworkflow.spec.installedCommand` system property selects an
absolute installed normal CLI for those same public calls against the isolated
host. Parent-selected focused tests, independent review, required publication
and installed qualification remain acceptance evidence to obtain, not claims
made by authoring these sources. No real incident Session is operated by the
specifications.
