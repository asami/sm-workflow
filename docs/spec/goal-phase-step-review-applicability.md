# GoalPhase Step review applicability — existing Phase 5 policy

Status: source implementation and executable cases added; UNVERIFIED. Normal
Cozy generation, affected-suite execution and assembled independent review
have not run for these inputs. This is an existing-policy implementation
correction, not a new policy, approval, repair frame or acceptance cycle.

## R1 — Historical timestamps do not expire local evidence

The CML StepReviewEvidence and application ReviewEvidence no longer contain
validUntil. reviewedAt remains a historical event time, not an admission-age
or clock-order certificate. Application policy receives no clock and checks
actual target and evidence shape; CAM still checks actual target and full-Step
coverage. Clock passage, future observations or backwards clock movement do
not withdraw an otherwise applicable review or request an automatic renewal.
Actual malformed timestamps, null evidence, wrong targets and inadequate
coverage retain their concrete failures.

E1: GoalPhaseStepReviewApplicabilitySpec evaluates identical applicable evidence
before its event time and a year after the former expiry. Wrong targets and
focused coverage remain rejected for full-Step admission. StepClosureAdmissionSpec
retains missing/coverage/target/malformed evidence cases with no expiry-only
expectation.

## R2 — Producer and legacy payload readers share one contract

Step-close writes use v2 and omit expiry. The v1 wrapper remains readable.
Both issued Step inputs and review-result payload codecs use the same review
encoder/decoder. A legacy validUntil value is ignored, including missing,
expired, null, malformed and non-string values; readers neither rewrite the
row nor fabricate an observation, participant or permission. Actual required
fields and unknown nonlegacy fields retain their input-contract validation.
The public model is regenerated from CML normally, never edited in target.

E2: GoalPhaseStepReviewApplicabilitySpec covers both codecs, new output and old
v1 payloads, and malformed actual input. Its legacy cases are codec evidence,
not a claim of completed historical SQLite acceptance across other controls.

## R3 — Repair and projection use actual judgment and state

Repair preparation keeps observedAt/admittedAt as historical facts and removes
the separate clock-order admission predicate. Repair itself still requires a
new judgment because the preceding disposition was repair; unchanged coverage
or old review age does not convert that judgment into acceptance. Current
target/coverage, retained completion, actual current state and CAS still govern
the operation. Shared fixtures, repair preparation and the managed physical
reopen scenario no longer expect an expired review to be rejected merely by
its timestamp.

E3: GoalPhaseRepairCandidateBoundarySpec preserves applicable coverage after the
former expiry and still requires repair re-review. GoalPhaseRepairLoopSpec
observes the current Decision after the former expiry without a new Result.
GoalPhaseManagedSuspensionOperationSpec reads committed repair judgment after
reopen, retaining actual history and denying foreign Operation/result scope.
These amended existing cases are pending execution, not PASS evidence.

## Connected migration still incomplete

Step candidate program/planning digests, repair digest production, remaining
whole-payload/history comparisons and merge review expiry remain unconverted.
Closure requestDigest/idempotency hashing and selected review/question body
certificates now have a connected source correction described in
[closure applicability](goal-phase-closure-applicability.md), still UNVERIFIED.
Old authorization digest/expiry reads are corrected, but actual historical
addresses must be resolved, not rediscovered by hashing or invented mapping.
Removing fields from canonical encoding does not by itself prove all historical
continuation compatibility. The connected producer/consumer/storage/legacy/test
migration must remove them, not rename them to whole-declaration equality or
renew authority. Actual provider/executor/Workflow-result connection, batchB
validation/FIX/cost and batchC assembled independent read-only review remain
open. No Phase 5 checklist item closes on this partial implementation.
