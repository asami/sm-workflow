# Phase 17: Task and Standalone Fix Extensions

Status: planned; implementation and acceptance pending
Planned: 2026-10-26 (Asia/Tokyo)
Owner: sm-workflow development
Predecessor: [Phase 6.7](phase-6.7.md) shared Task/FIX main route
Checklist: [P17 acceptance](phase-17-checklist.md)

## Ownership

Consolidate the remaining GTASK/SFIX work after the prioritized single-repository
delivery in 6.7. Preserve the original plan IDs and completion policies.
This is not a second Task/fix engine or a requirement to implement every possible
skill. The original [GoalTask](../notes/goal-task-delivery-plan.md) and
[FIX](../notes/standalone-fix-delivery-plan.md) plans remain requirement detail.

## Delivery order

Phase 17 is the holding plan for remaining extensions, not a requirement to
implement them all in one batch. When real development needs an item, transfer
the smallest coherent slice into the appropriate active or next Phase. Record
the originating GTASK/SFIX and P17 IDs, destination, acceptance conditions and
retained evidence in both plans. The destination then owns execution/acceptance;
mark the source item transferred, not completed or independently executable.
Unselected items remain here. Do not enlarge an already executing Step silently
or reset its Session, history, counts or applicable results. An initial transfer
may be into 6.7; later needs need not reopen a completed 6.7.

| Unit | Transferred remainder | Outcome |
| --- | --- | --- |
| X1 | GTASK-3 wider sources | Referenced specification/Issue/review and combined inputs use existing provenance and explicit adopted requirements; later edits do not silently expand scope |
| X2 | SFIX-3 selection options | Dirty/focused/full options use existing metadata, explicit user scope and preserved unrelated changes; no extra repair engines |
| X3 | GTASK-3 composition | Several coherent dependent units in one Task share history/counts and one integrated Task closure; internal repair does not create new Sessions |
| X4 | GTASK-3 / SFIX-3 integrations | An authorized Phase 14 investigation proposal can enter Task/fix; related-repository selection consumes Phase 7 where needed, with honest partial outcomes |
| X5 | GTASK-4 / SFIX-4 extended adoption | Publish matching extended caller contracts and prove real use with preserved 6.7 results and active Sessions |

Use one representative connected scenario per new capability before expanding
breadth. Journal/direct-input Task, basic test/review fix, conditional assessment,
integrated Task review and ordinary commit were allocated to 6.7 and must not be
rebuilt or held back for this Phase.

## Boundaries and dependencies

The three dependency modes, assigned-chat FIFO, required commit/publishLocal
delivery and best-effort synchronization of larger deliveries are owned by
[Phase 7](phase-7.md#dependency-development-delivery-priority--2026-10-26).
X4 consumes that owner for optional Task/FIX integrations; it does not delay or
duplicate the initial Phase 7 route.

Phase 7 remains the owner of multi-repository/workspace/artifact operations;
only X4's related-repository scenario depends on those available capabilities.
Phase 14 remains investigation-only unless repair/development is authorized;
only that integration scenario needs its delivered output. X1-X3 do not wait for
either whole Phase. Phase 9 retains provider routing/model comparison, and
Phase 16 retains development experimentation. These are not moved here.

Standalone sm-review or sm-validated-commit are separate candidate capabilities,
not silently added to this Task/FIX remainder. sm-review-fix is already in 6.7.
Dirty/focused variants are options, not mandatory separate skills.

Preserve distinct Task and fix completion policies, runtime-owned convergence,
existing authorization, all counters and evidence. Reuse qualified 6.7 behavior,
not repeat its full acceptance for every extension. No existing Phase Session
is migrated to a new Task; no source edit, implementation start or acceptance
claim follows from this planning update.
