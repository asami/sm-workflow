# GoalPhase selected Step closure recovery

Date: 2026-10-24 (Asia/Tokyo)

Planned amendment adopted 2026-10-24: the
[recovery observation contract](recovery-observations-and-cause-resolution.md)
and [ROBS delivery plan](../notes/recovery-observations-delivery-plan.md) add
ordinary continuation for ended/no-commit/in-authority preparation correction,
plus deterministic Journal/Evidence/Finding recording. Recovery success does
not resolve the underlying cause. R2/R4 below describe the existing explicit
Human route; implementation must distinguish the new proven ordinary case from
uncertain-effect recovery rather than apply that Human requirement to both.
The bounded ordinary continuation below implements the ended-execution closure
case. It grants no Journal/Finding delivery work or new semantic acceptance.
Preserve original receipts, Session/history/counters and accepted evidence.

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
The ordinary observation retains its existing physical path vector from typed
`SelectedIndexChanges(selection, originalParentHead)`. A known-partial target
also binds the actual typed execution selection scopes, which may be directory
scopes. `CheckSelectedIndex` preparation retains both its typed
check and `GitExecutionResult.selectedPaths`; `SelectedIndexChanges` uses that
same host-fixed scope. Full `PendingPaths(originalParent)` includes deletions
and both names of renames under CNCF's no-renames interpretation. Every required
path must be covered by an actual exact path or directory scope. The
reviewed-candidate comparison separately determines `selectedChangesEmpty`.
Selection remains available after an exact matching commit already exists. The
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
target/selection to agree on their fixed declared scope. CommitSelected derives
the actual staged physical paths inside that scope, checks selected worktree
cleanliness and commits exactly those physical files. Unchanged nonexistent
future paths are not commit arguments. Partial kind sets remain
valid; each actual operation still needs its own grant.

Attempts are bounded to 0..255; revision arithmetic refuses exhaustion. History
traversal follows only the contiguous intent, optional actual execution summary,
either explicit recovery direction or observed continuation, and fresh attempt
or observed reconciliation. A continuation requires its exact saved execution
frontier; it cannot coexist with Human direction, cross intent/execution rows,
skip native history or rewrite a target. Reconstruction binds the target to the
original input, definition, participant, installed root/branch, ownership,
ordinal and attempt, without live Git. Inherited next-attempt authority is the
retained genuine direction or observed continuation target. It
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
retains that actual terminal fact. Only the R2/R3 terminal-result and current
Git gates may then admit bounded ordinary continuation. A saved success without
a native committed receipt can reconcile only an actual exact matching child;
it cannot authorize a new commit attempt.

Git effects and storage are not one atomic transaction. The runtime attestation
that the old owner ended remains required for the explicit Human route when the
saved actual summary cannot establish termination. No process cancellation,
stale-owner takeover, arbitrary discovery scan, private SQLite mutation or manual
production commit is part of this contract.

## R5 — Executable regression evidence

`GoalPhaseClosureRecoverySpec` covers unchanged `.v3` bytes, exact target/name
roundtrip and Long generation, independent purpose/issuer/participant/reference/
target/prior-ended admission, passive Defer, both interruption windows, immutable
unknown intent traversal, preserved accepted check/review/counters, stale/crossed/
duplicate/gapped rejection and exact single-parent commit reconciliation.

`GoalPhaseNativeClosureSpec` retains legacy failed EOF retry and correction
coverage. `NativeGoalClosureRecoveryCliSpec` creates a fresh isolated normal
Session, performs ordinary Start/implementation/Test/review once, induces an
actual ended selected commit failure using a fixture-owned `.git/index.lock`,
removes that lock after actual process termination, then uses read-only public
description and ordinary Advance without new Human recovery direction. It
checks retained history/accepted work/counters and exactly one actual commit in
the same Session. A separate fixture arranges an observed matching prior-owner
commit and proves ordinary reconciliation creates no duplicate. Unknown-effect
Human admission, Defer and drift guards remain covered by the pure specs.
SQLite reads are read-only observations; fixtures never seed or edit native rows.

The optional `smworkflow.spec.installedCommand` system property selects an
absolute installed normal CLI for those same public calls against the isolated
host. Parent-selected focused tests, independent review, required publication
and installed qualification remain acceptance evidence to obtain, not claims
made by authoring these sources. No real incident Session is operated by the
specifications.

## R6 — Adoption of an existing Session by a repaired installation

Capture the complete old registered installation before refreshing the producer.
Keep its `selected` file, concrete generation manifest, runtime modules and CAR
artifacts intact. Prepare the repaired complete installation separately. Select
the repaired runtime through the normal installed command; retain the consumer
host configuration, native stores, index, accepted candidate and Session ID.
Do not migrate private rows or rerun Start/Submit/accepted Test/review.

For an eligible old actual failure, call
`describeGoalPhaseClosureRecovery(sessionId)` and follow its `continuation-ready`
ordinary `advanceGoalPhase` instruction. Description remains read-only. Advance
rechecks current typed Git state and independent Step closure authority and
saves its attempt-local continuation before the new bounded effect. Retained
`.progress.saved` history reopens through ordinary traversal without migration.
Uncertain/live results retain the explicit independent Human procedure above.

The explicit `@DoNotDiscover` suite
`NativeGoalClosureContinuationAdoptionCliSpec` requires absolute properties
`smworkflow.spec.installedCommand`, `smworkflow.spec.legacyRuntimeRoot` and
`smworkflow.spec.runtimeRoot`. Both runtime roots are complete distinct registered
installations with actual JAR modules, not source/class directories. Its two
inherited scenarios start fresh isolated hosts through the old installation,
declare an unchanged nonexistent owned future path, complete ordinary work and
one Test/review/closure grant, and retain the old actual pathspec failure. They
switch only the selected installation, then qualify ordinary retry and
matching-commit reconciliation while retaining old summaries, original history
prefix, Session, Plan and accepted evidence. Every semantic answer, independent
review and authority grant is explicitly specification-owned; no real Human
consent or real incident Session is supplied by this suite. Publication,
execution and independent review remain parent-owned acceptance evidence.

## R7 — Known successful partial commit completion

`CompletePartialCommit` is a distinct choice offered only when the saved original
execution actually succeeded with `Exited(0)` and the native facts prove one
bounded chain. Let P be the immutable accepted parent, C the actual partial
commit, T the exact accepted final tree after only the already admitted terminal
LF correction, and D the future completion. Current HEAD must be exactly C with
native parents `[P]`, index T, no merge/replacement/intervening history or
unexplained owned worktree state. Actual `TreeChanges(P,T)`, `TreeChanges(P,C)`
and `TreeChanges(C,T)` retain every old/new mode and optional blob. C must contain
only complete accepted path values from T; the nonempty remainder must be exactly
the still staged accepted paths. A delete or rename cannot disappear from the
proof. The existing admitted normalization receipt and original candidate remain
distinct immutable facts; arbitrary content or mode corrections refuse.

The exact public target binds all existing Session/definition/participant/Plan/
Step/generation/source/frontier/intent/execution/attempt facts plus P, C, T,
normalization, complete native deltas, remaining modes/blobs and physical
selection. Independent Human evidence binds that entire target and attests the
original owner ended. Development approval is not this real recovery answer.
Wrong participant/reference, stale target, nonzero/unknown original, foreign
change, changed index, incomplete selection or unadmitted normalization refuses.
The predicates for `AuthorizeNewAttempt` and `ReconcileCommit` are unchanged.

Recovery appends direction only. A separate ordinary owning Advance rechecks
the exact live observation and containment, retains normal Step closure
authority, writes one distinct completion intent, rechecks at the effect
boundary, and invokes typed `CommitSelected` once. Persist the actual execution
before projecting its value or claiming closure. Verify native `D.parents=[C]`,
`D.tree=T`, actual completed remainder and unchanged scope. P and C are retained;
there is no reset, amend, rebase, resubmission or accepted Test/review replay.

Ordinary closure also proves full candidate/index equality (or the existing
admitted LF-only relation), full pending ownership and physical coverage before
saving its intent and immediately before effect. An incomplete selection is a
preparation failure with no commit effect or additional semantic repair charge.
These observations do not promise atomic exclusion of arbitrary file writers.

## R8 — Immutable completion history and compatibility

Completion roles have separate codecs
`sm-workflow.goal-phase-partial-completion-intent.v1` and
`sm-workflow.goal-phase-partial-completion-execution.v1`. Their exact original
payload and recovery direction are bound across adjacent native revisions.
Foreign or gapped records cannot be skipped. A saved resultless completion
intent remains UNKNOWN and is never replayed. Actual nonzero/incomplete outcomes
remain actual failures/incomplete observations; no automatic retry or broader
unknown-effect recovery choice is introduced. Unsupported completion states
remain explicitly unresolved/Defer.

A completed chain uses `sm-workflow.goal-phase-native-step-closure.v4`; ordinary
results still encode `.v3` with no new absent fields. Old `.v3` histories stay
read-only and retain the direct-parent rule. Absent optional fields on historical
targets, closed Steps and StepEvidence mean ordinary behavior, never inferred
partial completion. Malformed or crossed chains refuse. New partial records
require the updated runtime.

Both live completion verification and recording-only history reconstruction
produce `StepCommitChain(originalParent, partial, completion)`. The latter reads
only adjacent retained accepted facts and performs no Git observation. The
distinct Closing event records actual D with native parent C and retains the
whole P→C→D proof in `ClosedStep.partialCompletion`. Public work, completion,
terminal, journal and whole-Phase StepEvidence projections preserve it. Next
Step and Phase use actual D as their tip; the normal next-Step counter
transition and all required later Phase validation/review remain unchanged.
Repeated collection or Advance never appends another completion commit.

`GoalPhasePartialCommitClosureSpec` and the adjacent contract suites cover the
proof, versioned retention, preserved history and ordinary compatibility.
`NativeGoalPartialCommitClosureCliSpec` requires the explicit installed command
and `smworkflow.spec.partialCompletionInstallations` image. Its fresh isolated
repositories execute real Git and public operations. Fixture-only finite fault
injection produces an actual successful partial Git execution; it never seeds a
success receipt. It proves one completion, nonreplay, later Step/Phase history,
ordinary direct-parent compatibility and no effect after selection drift or an
unknown completion intent. Authored tests are not claims of executed acceptance.
