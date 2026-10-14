# Phase 8: Runtime Extensions and Practical Closure

Status: planned
Decision: 2026-10-05; documented 2026-10-06 (Asia/Tokyo)
Planned after: Phase 7, with carryover recorded from Phase 2 onward
Depends on: practical Phase 2, Phase 5 and the selected Phase 6/7 delivery

## Safety Sandbox adoption boundary (2026-10-15)

Safety Sandbox is deliberately adopted only after the basic command-driven workflow functions are operational. It must not become a prerequisite that delays the foundation delivered by the earlier phases.

Use real sm-workflow operation as the development driver for CNCF Phase 110 and textus-cbd-support Phase 16. Map CNCF hierarchical sandbox scopes as Server -> Client/Command -> Operation/Worker, with the resolved Effective Sandbox carried by CNCF ExecutionContext. During initial CLI use, exercise Client/Command and Operation scopes against real Git/SBT/worktree/provider effects; when resident-server operation is selected, add the Server deployment scope without changing Operation semantics.

Gaps discovered by this use are fixed in the owning shared layer: CNCF for runtime capability/enforcement/loading admission, CBD Support for lint/review, sm-workflow only for workflow-specific scope/profile definitions. Do not add sm-workflow-specific direct filesystem/process/network bypasses.

Detailed rationale: [Safety Sandbox adoption as a development driver](../journal/2026-10-15-safety-sandbox-development-driver.md).

Adopt it incrementally against the real workflow in this order: (1) Command/Operation ExecutionContext scope, (2) worktree/resource boundaries, (3) managed Git/SBT process execution and arbitrary-shell rejection, (4) provider/network authority, (5) CAR Loading Admission plus CBD lint/review, and (6) Server Sandbox plus Client->Server ExecutionContext propagation when resident-server operation is introduced. Each slice is accepted through an actual sm-workflow scenario and may create a bounded upstream CNCF Phase 110 or cbd-support Phase 16 gap; re-consume the upstream fix before moving on. Do not build all sandbox families in advance.

## Goal

Close the current development rollout by resolving the necessary extensions
deferred from Phase 2 and missing functionality discovered through real use
after Phase 6 skill-connected acceptance and during Phase 7. Phase 5 validates
the foundation without creating skills; Phase 6 defines thinking modes and
creates/connects the initial skills. Follow the [incremental-use roadmap](README.md).
Phase 8 is not a prerequisite for gradual use and must not retroactively reopen
the accepted practical scope of an earlier phase.

Local LLM integration is reference information for developer-run manual trials,
not a Phase 8 or Phase 9 completion condition. During development, use the
[local-provider trial notes](phase-8-local-llm-reference.md) as reference
information. Results from those trials may justify a selected extension only
when real use shows a concrete need; the trial itself creates no acceptance gate.

## Carryover from Phase 2

The [2026-10-07 Phase 2 revision](phase-2.md#completion-ownership-and-carryover)
makes the following ownership effective for planning now, rather than waiting
for every historical checkbox to pass. Implementation/acceptance status remains
as recorded; no transfer claims completion.

The owning user's 2026-10-11 Phase 5.1 correction supersedes historical
intermediate-recovery, replay and competing-submission guarantees in this
carryover. Those requirements are withdrawn, not a mandatory deferred backlog.
Normal continuation after a requested answer remains supported; abandoning an
unsuccessful run means starting a new execution from the beginning, without
restoring uncertain intermediate effects. Retain historical IDs/results as
history and record withdrawn items as superseded. Reintroduction would require
an explicit new scope decision, not merely an old unchecked row.

| Original IDs | Remaining work allocated after practical Phase 2 |
| --- | --- |
| P2-T01..T05; transport remainder of P2-T06 | Remaining resident-server/MCP/CLI exposure and equivalence selected by actual need; historical concurrency/intermediate-recovery guarantees are superseded. Direct public Operations and required decisions remain the foundation, with Phase 5.1's new-execution fresh start. Initial skill-facing command/CLI wiring belongs to Phase 6 |
| P2-R01..R03 | Resident-reference refresh and generic management/Job visibility beyond necessary normal startup |
| P2-U05/U15/U16 and extended D/provider/event variants | Only breadth not needed by the selected practical scenarios; existing validated primitives remain reusable |
| P2-C01 | Phase 5 provides already-planned observations and Phase 6 initial-use feedback; broader comparative cost/round-trip evaluation belongs here, with benefit unproven until measured |
| P2-C02 and bundle notes | Initial skill creation/connection belongs to Phase 6; broader optional distribution/coverage belongs here |

P2-R04/R05 practical three-route assembly and real-effect requirements remain
Phase 2. Workspace-wide synchronization/overlay remains Phase 7. No complete
selected workflow, broken normal entry or required independent decision/review
is transferred here. Resolve individual extended rows by actual need during
use, retaining original IDs and existing evidence rather than re-inventorying
the complete history on each continuation.

The table is an initial ownership map, not an assertion that every feature is
unimplemented or a final decision to implement every historical idea. Preserve
the original Phase 2 checklist IDs and evidence in the Phase 2 planning record.
The user selected practical goal-phase, split-phase and repository-sync routes
on 2026-10-06. At the scheduled Phase 2 handoff, record each route and the actual
remaining behavior for each transferred item. Work necessary for any of these
three routes remains in Phase 2, including connected completion, required
effects, permissions and normal continuation under the corrected Phase 5.1
boundary; an entire selected workflow cannot be deferred
while claiming Phase 2 success.

| Origin | Extension owned here when not needed for the Phase 2 practical route | Handoff state |
| --- | --- | --- |
| P2-D01..D03; P2-U05/U15/U16 | Broader provider, transaction and event integration beyond the selected route | Separate necessary extensions from withdrawn recovery guarantees; retain existing partial validation |
| P2-G01..G06 | Additional GoalPhase operations/scenarios beyond its selected practical route | Preserve normal start/work/result/continuation coverage; apply Phase 5.1's fresh-start boundary |
| P2-T01..T06 | Remaining server/MCP/CLI exposure and equivalence selected by actual need | Record installed transports and unimplemented/unvalidated routes separately; withdrawn concurrency/intermediate-recovery guarantees are not pending acceptance |
| P2-R01..R03 | Resident references, refresh and generic management/Job visibility | Carry only the uncompleted integration; do not replace canonical ownership |
| P2-R04..R05 | Remaining workflow-profile coverage and real external-command adapters | Retain actual implementation/effect evidence; selected real-use adapters cannot be mock-only |
| P2-C01..C02 and bundle notes | Remaining cost/round-trip measurement and broader skill distribution/coverage beyond the Phase 6 minimum | Public Operation control stays in Phase 2; initial command/CLI wiring and skill creation/connection belong to Phase 6, not this backlog |

Completed P2 preparation and upstream primitives remain completed at their actual
evidence scope. Transferring a row does not mark its unchecked specifications
passed, reset repair counts, change the original base/PLAN epoch, fabricate old
state or require unrelated tests to be rerun. No copying of generated artifacts
or content-integrity ledger is required for this handoff.

## Needs discovered during use

Record an ordinary item here or link an existing journal with:

- original ID/phase or discovery context and the concrete user scenario;
- impact: blocks supported use, bounded enhancement, or optional future work;
- current implementation state, known limitations and existing test/review links;
- disposition: addressed in Phase 6/7, selected for Phase 8, unnecessary,
  superseded, or explicitly assigned to a later plan, with a brief reason.

Initial skill integration gaps are resolved in Phase 6. Use-blocking gaps found
after its acceptance receive early attention during real use and Phase 7; users
need not wait for Phase 8. Keep those phase scopes bounded. Other missing functions accumulate
here without forcing repeated expansion of the current implementation batch.
No real-use discoveries are claimed by creating this plan; add them as observed.

Acceptance Boundary behavior introduced in Phase 6 is part of this real-use observation. If practical use shows that a project quality attribute is missing or poorly expressible, record the concrete supported-use impact here. Do not broaden the boundary merely because Review or an implementation provider proposes stronger assurance. Boundary vocabulary/configuration extensions are selected in Phase 8 only from observed need; the richer mechanism that uses the boundary for convergence and provider routing belongs to Phase 9.

The [Development Artifact Overlay](../spec/development-artifact-overlay.md)
initial delivery and its six SimpleModeler acceptance scenarios belong to
Phase 7. Record only optional later expansion here; do not defer core publication,
selection, native operation integration or consumer verification to Phase 8.

## Selection and execution

Carry forward Phase 6 command ownership and live Phase-document synchronization
for every selected extension. Broader tool adapters may be chosen here from real
need, but the required SBT/Git lifecycle and normal plan/progress synchronization
are Phase 6 deliveries, not deferred backlog. AI semantic providers must not
become shell runners. A selected extension updates the encoded plan from the
latest adopted documents and writes progress without replacing narrative edits
or erasing prior facts. Verify affected connections in the selected scenario;
do not reopen earlier acceptance or add a new synchronization test campaign.

1. Review Phase 2 carryover, Phase 5 runtime evidence, Phase 6 connected acceptance
   and actual use after Phase 6 and during Phase 7. Confirm which
   functions are still needed and which are unnecessary or superseded.
2. Freeze a finite set of user-visible scenarios for this closing phase. Record
   a reason for exclusions; do not silently drop an unresolved supported-use bug.
3. Implement each connected scenario and its Executable Specifications together.
   Use focused checks during implementation, then integrated validation and
   independent review, with affected rechecks after fixes.
4. Exercise the completed supported workflow in normal use and update usage
   instructions, supported operations and remaining explicit limitations.

Phase 3/4 remain separately planned extensions. Consume them only when a selected
real requirement needs them; do not make their full completion an automatic
Phase 8 gate. Broader CNCF work is driven only to the boundary needed by selected
scenarios and retains its owning project's responsibilities.

## Completion

- [ ] Every inherited/discovered item has an explicit disposition and a link to
      actual evidence or a reason for exclusion/future placement.
- [ ] The finite selected scenarios are implemented and their connected
      Executable Specifications and required regressions pass.
- [ ] Independent review has no unresolved blockers for the supported scope;
      known supported-use failures are not relabeled optional to claim closure.
- [ ] Real-use instructions match the available normal entry routes and outcomes.
- [ ] Normal closing work for the delivered scope is complete, with history
      retained and no claim that unimplemented optional features were delivered.

An item found unnecessary through use may close with that reason. Phase 8 closes
the agreed rollout, not every imaginable future feature. Preserve Phase 5's
removal of management hashes, tamper defenses, unchanged-content proofs and
internal time-only expiry across every extension; do not reintroduce them as
completion or evidence-reuse requirements.
