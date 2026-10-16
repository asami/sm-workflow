# Phase 96 delivery friction: implications for sm-workflow

Date: 2026-10-17
Status: source-based investigation; findings recorded, implementation unchanged
Inspected revision: `e699111f1a324e2f329c3b5775b1917817d0a57f`

## Purpose and scope

The owner requested checking whether sm-workflow can reproduce the procedural
delays observed during CNCF Phase 96 development: artificial pauses at each
workflow state, execution-procedure failures preventing already-reviewed work
from closing, and repeated small validation/review/documentation cycles.

This entry records the findings and proposed acceptance focus. It does not
reopen released Phases, change repair counters, adopt additional release gates,
or authorize implementation. The investigation read current source and plans;
it did not execute tests or reproduce a production interruption.

## Findings

### 1. The normal runtime progresses, but the actual client loop remains planned

`GoalPhaseNativeWorkContinuation.advanceC` continues through available mechanical
work. In particular, `continueClosureC` closes the accepted Step and prepares
the next implementation request, or proceeds to whole-Phase validation. There
is no observed rule requiring a separate chat turn solely for each internal
state transition.

However, Phase 6 is still marked planned. Its client must retain the session ID,
display the actual Submit result and call Advance without another human
instruction or an AI acknowledgement/planning turn. The repository's
`sm-goal-phase` skill explicitly delegates that loop to the host/client.
Phase 5.3's closure record also explicitly excludes Phase 6 CLI/skill proof.

**Risk:** a correct runtime can still be surrounded by a client that waits for
the user, performs unnecessary status queries, or delegates deterministic
commands to new AI runners. The current runtime evidence alone does not prove
that this operational friction has been eliminated end to end.

Sources:

- [Native continuation](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeWorkContinuation.scala): `advanceC`, `continueClosureC`.
- [Phase 6](../../../phase/phase-6.md): common client loop and command execution ownership.
- [Semantic worker skill](../../../../skills/sm-goal-phase/SKILL.md).
- [Phase 5.3 closure](../../../phase/phase-5.3-closure.md): actual validation and independent review.

### 2. A pre-command interruption can be classified as an indeterminate transaction

`GoalPhaseNativeClosure.advanceC` saves intent, acknowledges
`goal-step-commit-prepared`, and only then calls `commitGoalStepC`. Its `ackC`
maps every unsuccessful acknowledgement to `transactionIndeterminate`, using
the same classification before and after command execution.

Native validation similarly acknowledges `goal-check-prepared` before calling
`repositoryValidationC`. This deserves attention when reporting whether a
command was actually started: failure at the prepared acknowledgement does not
establish that the external command ran. The intent has already been saved,
so it is also not correct to report that nothing at all changed.

**Evidence limit:** the assembly's acknowledgement callback defaults to success.
This is an injectable interruption path, not evidence that ordinary production
calls are currently failing there. No interruption was reproduced in this audit.

**Risk:** reporting a known pre-command stop as an unknown command result can
make a procedural failure look like a product failure or lead to unnecessary
revalidation. This inspection did not establish that accepted product work is
actually rolled back.

The existing normal-use contract deliberately does not promise intermediate
recovery or replay. Keep that decision. The proposed concern is accurate failure
classification and handling at the real client boundary, not a new recovery
engine, blind retry, copied-state proof or automatic retry of unknown effects.

Sources:

- [Native Step closure](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeClosure.scala): `advanceC`, `Store.ackC`.
- [Native validation](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeValidation.scala): `checksC`, `Store.acknowledgeC`.
- [Native service assembly](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeServiceAssembly.scala): default acknowledgement callback.
- [Phase 6](../../../phase/phase-6.md): normal-use limit and command ownership.

### 3. Repair returns to the Step's selected validation set

`GoalPhaseOrderedProgression` clears candidate/check/review state when repair
work completes and returns to candidate inspection. Native validation selects
`step.checks` or `phaseChecks` and runs that selected sequence. It reuses a
retained validation result for the current candidate, but this path does not
provide impact-based reuse of individual checks across changed repair candidates.

**Risk:** if a Step declares a broad or expensive check set, even a small repair
can return to that entire set and another review. This is not necessarily an
incorrect validation obligation; its cost depends on Step/check selection.
Same-candidate result reuse must not be described as selective reuse after a
change, and planned test-management features must not be presented as delivered.

Use a connected practical delivery unit and a proportionate check set. Reuse
actual applicable evidence where the adopted contract permits it; preserve
required integration checks and independent review. Do not replace applicability
reasoning with hashes, file copies or another unchanged-content ledger.

Sources:

- [Ordered progression](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseOrderedProgression.scala): `SemanticCompleted`, `ReviewObserved`, `success(clear = true)`.
- [Native validation](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeValidation.scala): `advanceC`, `storeC`, `checksC`.
- [Managed test delivery checklist](../../../phase/managed-test-suites-phase-checklist.md): planned status and current normal-use qualification.

### 4. Batched documentation and review behavior still needs connected acceptance

Phase 6 already assigns deterministic command/history maintenance to runtime,
refreshes Phase documents at meaningful dispatch boundaries, and batches Hygiene
and development-candidate recording into one AI task. It explicitly rejects a
separate review cycle for each entry and recursively reviewing the recording.

**Risk:** these are planned integration requirements. The current source
inspection does not establish that real skill use avoids per-helper review,
repeated progress-document edits or a new model call for each mechanical action.
The Phase 6 implementation should demonstrate the existing batching requirements,
rather than add more bookkeeping to enforce them.

## Proposed focus for the existing Phase 6 driver

Use its already-planned connected GoalPhase scenario to observe the following:

1. Accepted Submit -> chat display -> Advance -> next actual request proceeds
   without a human prompt or AI turn whose only purpose is advancement.
2. Commands run through runtime ownership. A known pre-start failure is reported
   as such; an unknown effect is not blindly replayed, and neither is reported
   as a test failure without an actual failed test result.
3. A bounded repair shows exactly which checks and review scope are repeated
   and why. Avoid helper-by-helper delivery cycles; do not skip required checks.
4. One review's optional findings are recorded as a batch; no findings skips
   recording. Phase Final uses applicable evidence instead of restarting the
   comprehensive Phase Review, consistent with the
   [staged quality review decision](2026-10-13-staged-quality-review-matrix.md).

These are proposed observations within the existing integration effort, not a
new exhaustive interruption matrix or additional mandatory review process.

## Recording validation

Only this journal entry was added. Relative document/source links and whitespace
were checked. No Scala, configuration, Phase status or acceptance evidence was
modified; no build/test, commit, deployment or other chat action was performed.
