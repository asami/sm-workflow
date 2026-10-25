# Whole-Phase remaining candidate recovery

Updated: 2026-10-23.

Stable failure: **SM-WHOLE-CAND-01**. This limited contract continues an original
whole-Phase inspection after every ordered Step closed. It does not restart or
resubmit the Goal/Session and does not settle historical uncertainty.

## R1 — Exact original whole-Phase prefix

`describeGoalPhaseWholePhaseCandidateRecovery(sessionId)` reconstructs the actual
native history, full controller/Plan identity and revision, base/tip, generation,
repair counters, ordered completed candidates and ClosedStep commits. It requires
the original unsuspended PhaseInspection source and its exact adjacent inspection,
then every adjacent successful staging intent/result, with matching payloads and
references. Management selection may be empty: source → inspection is then the
complete frontier. Whole-Phase preparation always uses empty history paths, so
history entries, gaps, unresolved staging, unexpected interleaving, candidate,
observation intent, selected-Step or later scope refuse.

The target also binds definition, participant, root/branch, fixed candidate paths,
document policy and recording paths; typed repository/head/tree/dirty/indexTree;
fixed phaseValidationPolicy including metadata; canonical GitDiffParameters; and
the normalized JSON of ordinary resolved `textus.subsystem.process-execution.plan`.
The policy wire carries the opaque plan as sorted JSON text to preserve arbitrary
configuration map keys through public Record property-name conversion; equality
uses parsed JSON semantics. Only ExecutionContext.resolvedParameters supplies that opaque host plan. CNCF owns
interpretation of programs, argv, limits and grants. Missing or malformed plan
fails closed. Description obtains stable typed Git observations and PendingPaths
against the original tip. IndexTree/write-tree may write Git objects and index
cache. It never runs PendingDiff/Test/staging or writes Workflow history.

## R2 — Separate Human and prior invocation evidence

The public Operation accepts exactly one input representation: existing
`--input '<complete business JSON>'` or `--inputResource file:///absolute/path.json`.
The Resource contains the entire unchanged `GoalPhaseWholePhaseCandidateRecoveryInput`,
including its complete target and independently supplied Human reference. It is
not a target locator or evidence shortcut. Missing or simultaneous representations
refuse. Only an absolute local file URI without authority, query or fragment is
accepted; escape spaces and other URI characters normally.

The executing call reads the resource once through CNCF's normal Resource service
and its configured roots, read limit and failure handling. It then uses the same
strict business decoder and unchanged recovery admission as inline input.
Malformed or incomplete DTOs, explicit null, foreign/stale targets, absent or
invalid Human evidence and unconfirmed prior termination still refuse. This
input read precedes history-only admission; recovery performs no candidate Git,
staging, source-history write or SBT effect. No new size check or direct host-file
read is added in the application. Configure sufficient normal Resource read
capacity for the complete DTO; increasing Git's diff limit alone does not do so.

Example using the normal installed command and the owning host:

```sh
sm-workflow --runtime-root /absolute/owning-host command \
  org.simplemodeling.textus.SmWorkflow.GoalPhaseService.recoverGoalPhaseWholePhaseCandidate \
  --inputResource file:///absolute/complete-recovery-input.json --json
```

This is the existing recovery Operation, not Start, Submit, a preparatory import,
or automatic Advance. A transport repair by itself changes no target, Human
authority, Plan, counters, accepted results or retained failure history.

`recoverGoalPhaseWholePhaseCandidate(input)` requires separately provisioned
independent evidence with purpose `goal-phase-whole-phase-candidate-recovery`,
binding the complete target, component instance, actual participant/reference,
choice, rationale and `priorExecutionEnded`. Choices are exactly
AuthorizeNewObservation/Defer. Authorize requires affirmative ended attestation;
Defer does not. The trusted independent host must observe the fresh genuine Human
answer and real terminal evidence of the prior invocation ending before it
provisions an affirmative attestation. The Boolean is not proof. Passive
`prepareEvidence(kind=whole-phase-candidate-recovery)` only serializes supplied
facts; it does not establish termination or grant authority. AI text, error,
timeout, authentication, past Submit or development approval are not substitutes.

Recovery performs history-only replay, checks the exact current frontier and
current resolved policy, and admits the independent evidence. Defer appends
nothing. Authorization conditionally appends one adjacent mechanical direction in
namespace `ordered-whole-phase-candidate-recovery` without Git/File/SBT/Advance.
Missing/stale/crossed evidence and ended=false authorization refuse.

## R3 — Intent before effects; no replay

A separate ordinary Advance dispatches this direction before normal entry Git
observations. It persists its own observation-intent in the direction record's
new candidate source namespace, then invokes acknowledgement
`goal-whole-phase-candidate-observation-prepared`. Only afterwards does it recheck
resolved host/diff/Phase/document policy and the pinned actual repository/index
through typed UoW, then enter shared remaining candidate observation. Original
staging/history loops never run again. An existing unresolved intent returns
Wait. Failed new observation remains UNKNOWN and cannot be automatically retried
or reauthorized by an invented chain. Future failure recovery is outside scope.

Direction v1 decoding validates the full original prefix and controller using
retained policy/snapshot, without live Git or today's policy equality. Current
config drift therefore does not invalidate a completed historical traversal;
live description/admission/new dispatch own current equality checks.
An already accepted direction reconstructs with its captured candidate paths,
repository snapshot and policy. Current candidate paths must be non-null, safe,
distinct and an ordered append-only extension of that captured vector. Only
candidate paths are substituted into the declaration for historical
reconstruction; all other actual source/controller/document/recording/completed
candidate arguments remain exact. Complete reconstructed-target equality, the
original frontier and the exact adjacent native successor remain mandatory.
Reordered, removed, unsafe or duplicate paths refuse. No saved native record,
payload, successful evidence or UNKNOWN fact is changed.

Historical reconstruction grants no expanded current observation authority.
An old pending Direction under appended candidate paths still reaches the
existing strict current candidate-policy/controller guard and refuses before
saving any new observation intent or performing effects. Current description,
admission, frontier/policy checks and independent Human/ended attestation remain
unchanged. Completed observations reopen their saved candidate/Test/ReviewPhase
without another observation or check. No schema or public API version changes.

## R4 — Normal checks and independent Phase review

The fresh candidate preserves exact successful Step evidence and proceeds through
existing PhaseValidation, fixed actual typed UoW SBT Test, ordinary independent
ReviewPhase and existing closure. No prior whole PASS/UNKNOWN/failed check becomes
success for this candidate. Plan/base/counters, accepted semantic work, Step
Compile, Step commits and old receipts remain immutable. Reopen/Advance reuses
saved candidate/check/review facts without repeating observation or Test.

Executable evidence: GoalPhaseWholePhaseCandidateRecoverySpec E1–E4 is pure
contract/provenance evidence only; NativeGoalWholePhaseCandidateRecoveryCliSpec E5
uses a new isolated fixture, actual Compile, independent Step review/commit,
original inspection acknowledgement refusal, explicit installed public recovery,
one actual Test and ReviewPhase. It operates no production Session. Fixture-owned
negative evidence is never claimed as actual process termination evidence.
P116-CONT-02-TWO-READERS extends that same E5: appended paths at the pending
Direction hit the existing pre-intent refusal without a native row or Test;
restoring the exact configuration permits the original single actual Test.
Completed safe append returns the identical ReviewPhase and full diff, while
reordered original paths refuse. Each passive/refused call preserves complete
native record and stored payload JSON, original source bytes and modification
time, index, Compile and Test counts. All evidence belongs to the isolated fixture.
