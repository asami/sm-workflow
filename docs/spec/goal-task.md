# Ad hoc development with sm-goal-task

Date: 2026-10-24 (Asia/Tokyo)
Status: adopted specification; implementation and installed acceptance pending
Delivery: [GoalTask and shared execution plan](../notes/goal-task-delivery-plan.md)

## Purpose and input

`sm-goal-task` completes an ad hoc development request through integrated Task
review and local commit, without requiring a numbered Phase or Phase documents.
A journal is the usual input, not the only input. Specifications, Issues, review
reports, direct chat instructions and combinations of these are supported sources.
Task development may add a feature, change behavior, integrate components or fix
a defect; an initial failure is not required.

Retain source references and the adopted objective, scope, acceptance conditions,
dependencies and validation policy in the Task's existing business execution
model. Narrative source documents are requirements inputs, not a replacement for
runtime state. Do not execute every suggestion or superseded passage in a journal.
Resolve requirements against the user's actual request and decisions. Ask only
for material ambiguity or scope/authority changes, not routine plan confirmation.
Later source edits do not silently change an active Task's adopted requirements.

## Shared execution and distinct completion

| Entry | Acceptance and completion |
| --- | --- |
| sm-goal-phase | Existing formal Phase lifecycle and actual Step/Phase closure |
| sm-goal-task | Ad hoc Task scope, one integrated Task review, required actual local commit(s), durable completion |
| sm-fix family | One Step-equivalent repair request, focused validation and conditional review; no implicit commit |

Reuse the [standalone FIX execution path](standalone-fix-acceptance.md), Phase 6.5
implementation/FIX/assessment, Phase 6.6 diagnosis where needed, runtime validation,
independent review and typed Git commit operations. Share execution components
and evidence, not terminal policies. A fix accepted without review or commit is
not automatically a completed Task. Do not wrap every internal repair in another
standalone Session or reset counters by changing entrypoint.

A small Task has one Step-equivalent work unit. Larger Tasks may use coherent
internal units with explicit dependencies, without inventing Phase numbers or
requiring repository Phase-management files. Keep existing WorkOrder/Continuation,
Journal/Evidence/Finding and repair semantics; no parallel executor or generic
Task framework is required before the first connected route.

The caller supplies authorized semantic work and uses normal public continuation.
sm-workflow owns mechanical validation, execution/waiting/result capture,
progression and commit through CNCF typed operations. Product SBT must not escape
to an AI-created supplementary runner. Actual missing capabilities remain gaps.

## Validation and integrated review

During implementation, use compilation, required focused behavioral tests and
applicable lint. Ordinary local changes need no individual AI review when evidence
settles their conditions. Preserve actual non-execution after compilation failure;
empty selection or compilation alone cannot establish code-test success.

At Task closure perform one integrated independent review of the Task objective,
adopted acceptance conditions, actual implementation, relevant architecture and
cross-unit behavior. Reuse settled decisions, test results and prior review;
do not re-review mechanically established facts. This is Phase-equivalent review
responsibility scoped to this Task, not a whole-repository audit or a request to
review unrelated work. It is required even when internal units used reviewless
acceptance. No second Phase/Release review is added after it.

Group actionable in-scope findings into bounded repair. Revalidate affected checks
and review only findings, repair delta and affected meaning. Reuse the integrated
review for unchanged scope; do not restart it after each repair. Speculative
improvements remain separate nonblocking candidates. Keep existing convergence
and repair-count policy; interim commits, review or provider changes reset nothing.

Select final validation by actual Task impact and explicit requirements. Full or
heavy suites are not mandatory merely because this is a Task. Required broader
checks remain required; reuse still-applicable results rather than repeat them
at Final. Lack of required evidence prevents a success claim, not a fabricated PASS.

## Commit and completion

A normal invocation of this entrypoint includes local commit of its intended
changes as part of completion; no extra general commit confirmation is needed
within that admitted scope. An explicit no-commit instruction overrides this
default and must be reflected as a changed delivery condition, not an ordinary
committed completion claim. Push, remote synchronization, publication and release
remain separate explicitly authorized operations.

Commit the actual accepted Task delta within its logical ownership, preserving
unrelated staged/unstaged work. Do not pass future nonexistent unchanged paths as
mandatory pathspecs; retain tracked deletion/addition/rename semantics. Reuse the
selected-commit correction and normal recovery/reconciliation contract. A failed
or uncertain commit leaves Task delivery incomplete while preserving accepted
implementation, validation and review. Never replay effects blindly.

Complete only after required actual commit results are recorded and the adopted
conditions are met. Return actual commit references, validation/review evidence
and unresolved nonblocking items. A no-change request reports no-change honestly
and creates no empty commit just to satisfy this policy. The first delivery is
single-repository; later multi-repository delivery must report partial outcomes
without pretending commits are atomic across repositories.

Common Journal records the Task outcome and Evidence links using existing
projection. A Journal storage failure is separate from an actual completed commit;
report it without rerunning the effect. When the source journal needs a development
summary, update its authorized scope without overwriting prior discussion. Durable
completion references actual commits; no self-referential commit-hash file is required.

## Preservation and adoption

Preserve ongoing edits, Session/Task/Phase identity, accepted work, history, repair
counts and applicable validation/review. Do not migrate active Phase work into a
Task to bypass its obligations, re-Start, resubmit accepted work, mutate private
runtime storage or rerun successful checks solely to install this capability.
Existing evidence retains its original attribution. Recovery success remains
separate from cause resolution under the common recovery-observation contract.
