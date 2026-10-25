# sm-goal-task: ad hoc development through integrated review and commit

Date: 2026-10-24 (Asia/Tokyo)
Status: specification and development plan adopted; implementation pending
Audience: sm-workflow producer and consumer developers

## Decision

The user defines sm-goal-task as an ad hoc development entrypoint. A journal is
the usual input, but specifications, Issues, review reports and direct instructions
are equally valid sources. No numbered Phase is required. The Task ends after
Task-wide integrated independent review, required local commit and recorded
completion. Push/publication/release is separate explicitly authorized work.

Read the [specification](../../../spec/goal-task.md) and
[GTASK-1–4 plan/checklist](../../../notes/goal-task-delivery-plan.md).
Coordinate with the [standalone fix plan](../../../notes/standalone-fix-delivery-plan.md).
Both reuse shared implementation/diagnosis/FIX/validation/assessment and existing
WorkOrder/Continuation, but have distinct completion policies. sm-fix is one
Step-equivalent unit with focused tests and conditional review, ordinarily without
a commit. sm-goal-task adds a required integrated Task review and actual commit.

## Development order

Deliver one single-repository journal request -> adopted Task conditions -> actual
implementation -> focused validation -> integrated review -> local commit ->
durable completion first. Reuse the first SFIX execution slice without waiting for
all its variants. Do not fabricate a GoalPhase committed Step or require Phase
management documents. Internal work may be divided when genuinely needed, not as
a per-file/per-finding review ceremony.

Use one integrated Task review scoped to the adopted objective and actual changes.
Reuse prior tests/review and settled propositions. After a finding, review only
the finding, repair delta and affected meaning; Final does not repeat reviews.
Select broader validation by impact/explicit requirements, not an unconditional
full/heavy gate. Runtime owns SBT and typed Git; AI performs issued semantic work.

Respect actual commit selection, unrelated dirty work and existing recovery rules.
A failed commit preserves accepted tests/review but is not Task completion.
Journal projection failure must not replay a completed commit. No-change results
remain honest and create no artificial commit.

## Preservation and scheduling

This is planned follow-on development consuming Phase 6.5/6.6 shared paths, not
new work required to complete an active Phase. No Phase number is allocated by
this update. Phase 14 may later supply investigation results; general diagnosis,
multi-repository management and enhanced model routing are not initial blockers.

Keep existing source edits, Sessions/Plans/continuations, accepted implementation,
history, repair counts and applicable validation/review. Do not migrate active
Phase work to a Task, re-Start, replay accepted Submit, patch private storage or
repeat successful checks for adoption. Deliver a public supported continuation
and caller contract with actual validation evidence when implemented.

This update edits documentation only. No live Session operation, product code
change, SBT execution, commit, push or implementation-completion claim is made.
