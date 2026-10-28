# Precommit selected-Step review

P69-PRECOMMIT-REVIEW-01 adds an ordinary native route from conditional Step
Closing to independent review when an observation arrives before any closure
attempt. An external report motivates review; it is never installed as a native
review verdict. The selected Plan, accepted prefix and repair policy remain
unchanged. This contract grants no process, commit or remote authority.

## R1 — Passive description

`GoalPhaseService.describeGoalPhaseStepReview` accepts
`GoalPhaseStepReviewDescriptionInput(sessionId)` and returns
`GoalPhaseStepReviewDescriptionResult(sessionId,outcome,message,target,requestOperation)`.
The eligible outcome is `review-available` and requestOperation is
`requestGoalPhaseStepReview`.

The owner walks contiguous actual native history. Only the active unsuspended
selected Step at Closing, with valid conditional admission, complete passed
retained candidate/checks and no same-generation ReviewStep or previous request,
is eligible. Description performs reads only. It appends no history, issues no
work and reruns no successful check. Candidate and dependency freshness use the
existing typed observation and validation preconditions.

The typed target binds Session and definition, participant, exact source revision
and cause, complete Plan, Step and generation, original base and completed prefix,
candidate and its retained revisions, validation/check reference, conditional
admission and repair progress. Public Record naming is normalized before strict
decoding. Missing, null, extra, crossed or stale fields refuse.

## R2 — One native request

`GoalPhaseService.requestGoalPhaseStepReview` accepts
`GoalPhaseStepReviewRequestInput(sessionId,target,reason)` and returns
`GoalPhaseStepReviewRequestResult(sessionId,accepted,outcome,message)`.
Successful outcome is `review-requested`. Reason must contain non-whitespace
prose, use well-formed UTF-8, contain no control characters and occupy at most
16 KiB. It is retained in the native request and included as an observation-only
motivation in assigned ReviewStep WorkOrder context and the existing Advance
message. The selected Plan Step purpose remains immutable. No field is
interpreted as a command, result, approval or external evidence reference.

The operation repeats current eligibility and freshness checks, compares the
complete structured target and appends exactly one mechanical transition through
the existing atomic native owner. Its immutable cause is
`ordered-step-review-request:<original-source-revision>`; its reference prefixes
that cause with the Session identity and uses revision `1`. The CML edge is
Closing → Review on `step-review-requested`. Native CAS arbitrates concurrent
requests. A repeated request refuses without another append.

## R3 — Review and repair lifetime

`StepReviewRequested(candidate,checksReceipt)` clears current conditional
admission and establishes the internal `stepReviewRequired` obligation. It
preserves Plan, generation, base, completed prefix, candidate, successful checks,
all repair counts/control/observations and native history. It reserves no repair.

Ordinary Advance issues the existing independently assigned ReviewStep on that
same generation. Repeated Advance returns the same pending work. Reopening after
the mechanical request, including before issuance, reconstructs the obligation
and motivation from the retained request and exact historical candidate/checks.
Fresh and retained pending ReviewStep outputs carry the same motivation in their
Advance message; RepairStep messages do not inherit it.

Only a genuine independent repair verdict reserves a normal repair batch. The
existing ceiling of ten is unchanged: a Step with eight consumed batches moves
to nine only when its actual repair verdict is admitted. RepairStep completion
requires ordinary candidate capture and fresh successful qualification. The
obligation survives candidate clearing, policy adoption, repair and restart.
Even an unchanged conditional Plan must then receive a fresh independent
ReviewStep; the covered-review-completion shortcut cannot discharge this request.
Clean triage also retains the obligation. Decision, uncertainty and scope
judgments retain their existing stop semantics.

An actual independent accept verdict permits ordinary Closing. The separately
authorized ordinary Step commit resets `stepReviewRequired` for the next Step or
whole-Phase inspection. Unrelated conditional Steps keep their normal admission
behavior. Old successful validation receipts remain immutable historical facts.

## R4 — Refusal boundary

PhaseClosing, Decision, current Human or Work suspensions, required-review
frontiers, missing/failed/incomplete checks, stale candidate/dependency facts and
already committed Steps refuse. Any actual closure check, intent, commit or
unknown effect must follow its existing owning recovery route. This request
cannot reopen or reinterpret such an attempt, manufacture Human work, alter Plan
policy, refund counters or create new effect authority.

## Executable evidence

- `GoalPhaseOrderedProgressionSpec`: preserving accepted state and eight consumed
  repairs, charging the genuine ninth repair once, refusing crossed/ineligible
  frontiers and closure intents, retaining review through fresh qualification,
  refusing covered completion and resetting on normal Step commit.
- `GoalPhaseStepReviewRequestSpec`: real public Start/work/Advance/checks in
  newly test-owned repositories, accepted prefix, passive description, exact and
  malformed requests, candidate drift, one append, restart before issuance,
  repeated Advance, actual independent repair, new checks/review and local
  closure with next-Step reset. No native records are fabricated.
- `NativeGoalStepReviewRequestCliSpec`: actual installed executable selected by
  `smworkflow.spec.installedCommand`; OLD installation root selected by
  `smworkflow.spec.legacyRuntimeRoot`, NEW root by `smworkflow.spec.runtimeRoot`.
  A new isolated host reaches conditional Closing with OLD, then NEW reopens the
  same Session, requests review and completes genuine repair, fresh checks,
  independent review and ordinary local Step closure. Both roots must already
  exist; the suite neither updates an image nor operates a real Session.
- Generated ABI and Help specifications cover the CML-declared business records
  and Operations. Generate products through normal Cozy generation.

These are executable coverage declarations. Their presence does not claim a
successful generation, compilation, test run or installation qualification.
