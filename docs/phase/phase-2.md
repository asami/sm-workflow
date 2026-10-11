# Phase 2: First Usable Runtime Slice

Status: completed on 2026-10-08

Completion boundary: the adopted finite direct-Operation slice for SplitPhase,
RepositorySync and GoalPhase, not the broader Goal. Actual scoped closing
succeeded in `ed1a76b07bb00c88123c50ef907f90604d3ce1bf`, with the saved verified
native closure receipt recorded in
`.codex-workflow/phases/PHASE-2/phase2-verified-closing-20261008.md`.
The preparation and earlier blocked states below remain historical evidence,
not a pending Phase 2 commit or a reason to reopen its accepted slice.

Historical completion handoff (2026-10-07):
[remaining correction, integrated acceptance and closing](phase-2-completion-handoff-20261007.md).
It retains the blocked state after1122. Subsequent execution, correction and
acceptance are recorded below and in the linked journal, not retroactively in
that handoff.
Current evidence: [three-workflow validation](../journal/2026/10/2026-10-07-three-workflow-integrated-validation.md)
records1123:40/40,1124:1492/1492 in167 complete suites and actual1125 local
CAR/manual verification, followed by actual integrated recovery-full-review PASS
with no Current Boundary Blockers, recorded in retained ledgerR7. P2-M02..M04
are satisfied; the final P2-M05 declaration is selected for the closing commit. The user
explicitly adopted the handoff including its two-reference correction; prior
blocked states and1116/1122 failures remain historical, not current permission gaps.
Closing-evidence update (2026-10-08): human-directed record-format-only correction
is complete. Required current producer-full tests passed: SimpleModeler1126
90 succeeded/17 suites/3 canceled; CNCF1127 4296 succeeded/579 suites/13 canceled,
1 ignored/46 pending. Both have0 failed/aborted, SBT0/wrapper0/released lock.
Unexecuted examples remain unexecuted; no new product fix or review was added.
Final closing preparation also settles exact owned stage/preserve paths and
comment/format-only source maintenance. Actual full validations and R7 remain
applicable; no fourth repair, new repair frame or counter reset is introduced.
The final declaration includes all six persisted follow-up findings. Future
Phase5/6/7/8 planning and shared Phase-index projections remain preserved rather
than silently committed or accepted. CLI/skill wiring, expanded transport and
cost work remain with their recorded later owners.
Full consumed-command history is779 through1127, distinct from unchanged original
repair counter3/3. The ordinary owned-scope commits subsequently succeeded as
recorded above; the preparation account does not describe current pending work.
Plan revision: 2026-10-07, requested by the user after stopping development.
The planning edit alone did not resume execution. The subsequent explicit user
resume adopts this sequence under the same Goal and retained history; see
[resume record](../journal/2026/10/2026-10-07-optimized-plan-resumption.md).

## Current delivery contract

Deliver the public Operations of all three practical workflows as independently
callable component Operations: SplitPhase, RepositorySync and GoalPhase. These
are the Operations that skills will call later. Verify them through connected
Executable Specifications that call Operations directly on the actual component/
runtime, then fix integration failures. The user's final 2026-10-07 clarification
moves command/CLI wiring together with skill integration to Phase 6; a command
process is not required as the test caller in Phase 2.
Phase 2 does not require a deployed skill client,
a separate development host/client application, or a GitHub input integration.
Executable Specifications supply the selected input and AI/review/Human test
results; label that supply as test evidence, not a real human interaction channel.
Checking helpers or state-machine functions alone is insufficient: the public
Operation execution, durable progression and required effects must actually work.
An internal helper or a passing fixture is not a separate delivery/acceptance gate.

This plan supersedes earlier implementation ordering and statements that all
historical runtime, skill, cost and transport work must finish before Phase 2.
The [previous plan and implementation history](phase-2-history-through-20261007.md)
retains those statements and their evidence. The existing P2-M01..M05 checklist
remains the completion boundary; no accepted result is upgraded by this revision.

## Historical stopped baseline and first action on the earlier resume

| Boundary | Actual stopped state | Next use |
| --- | --- | --- |
| CNCF managed process bootstrap | Approved 13-file change applied; request 1059 passed 123 tests / 17 suites; 1058 compile failure retained | Reuse this producer validation where applicable; refresh the selected consumer dependency when needed, without repeating unchanged producer tests |
| SimpleModeler Help generator | Source and spec applied on the admitted scala12 checkout; not compiled/tested | Validate the generator change, then refresh its selected development artifact and regenerate consumer output |
| sm-workflow consumer | 1057 passed 122 tests / 16 suites before the latest producer handoff; earlier broad/targeted results retained | Supporting evidence for those inputs, not proof of the new producer combination or installed completion |
| Installed runtime | ComponentFactory still selects GoalPhaseLauncherAssembly; ThreeWorkflowLauncherAssembly has a HostDecoder contract but no production implementation | Connect normal host configuration, storage, process effects and result ingress first |
| Actual Goal | Same identity `01a103b3-5f45-7ec2-ac5c-7f9fb01df42a`, ACTIVE after explicit resume; stopped 1060 preparation never submitted | Allocate a fresh current-resume request through the normal registered route |

Source: [stopped handoff and original validation references](../journal/2026/10/2026-10-07-approved-help-and-runtime-bootstrap-resumption.md).
The approved SimpleModeler addition is retained in ledger R6, with R5 history;
original bases, PLAN epoch, original nine findings, review history and recorded
repair counts remain intact. Replanning creates no fresh repair allowance.

The first execution action is the pending generator validation and producer
handoff below, not another full inventory, another general approval request, or
a new implementation of already-tested CNCF bootstrap facilities. Existing
specific approvals remain applicable within their scope; actual platform
execution review still applies. This document grants no additional external effects.

## Finite practical scenarios

| Workflow | Required user-visible result | Boundaries retained |
| --- | --- | --- |
| SplitPhase | Normal Start -> issued semantic/Human results -> selected complete Preview -> authorized Apply with actual document changes | Known and required unknown/scope/reasoning paths; Preview is read-only; the saved selected plan drives Apply; invalid/crossed results and document conflicts do not duplicate writes |
| RepositorySync | One selected branch and configured upstream reach observed convergence, preserving intended local changes | Equal/ahead/behind/diverged paths, clean integration, semantic/Human conflict handling, required validation and independent review, actual non-force effects |
| GoalPhase | One selected repository/Phase with a supplied bounded ordered Step plan reaches whole-Phase completion | Implementation/result, Step validation/review/repair/commit, required decisions, Phase validation/review and repository closure; a closed Step is not a completed Phase |

Use direct Operation calls and the configured durable store for all three.
The Executable Specification acts as the caller and supplies the selected
input plus work/review/Human test results through public Operations. Test-owned
input/evidence fixtures may prepare declared records through existing managed
storage; distinguish those fixtures from Operation execution and do not claim
they prove production human/reviewer issuance. Do not replace the Operation's
implementation, bypass its required permissions, seed fabricated Workflow history,
or return canned command success. Runtime must not invent a successful response.
Command/CLI wiring, generic installable sm-* skill clients, their interaction/
result relay and abstract thinking modes belong to Phase 6. Existing Help/CLI/MCP
tests remain supporting evidence, not Phase 2 entry prerequisites. A separately deployed input issuer or
the location of another host/client is not a Phase 2 prerequisite.
Owned local repositories/remotes and documents provide real effects in acceptance
without operating on the user's live remote. Product SBT validation must actually
execute through its supported runtime adapter, not return a canned receipt.

Each Operation route must resume from a freshly opened runtime using saved state, without
substituting a private runtime implementation or conversation memory. Explicit
test-owned input preparation is allowed; reopening must use the real saved state,
not reinstall Workflow instances or repeat completed effects. Separate-process
reopen may use a specification-owned Operation caller and need not use CLI
transport. Exercise pending work/result
recovery and relevant uncertain-effect recovery; observe uncertain effects before
retrying. Existing broad unit/fixture tests support these scenarios without
being re-created for every possible interruption permutation.

## Remaining implementation order

These are work batches under the existing checklist, not new Phases, approval
frames, repair budgets or independent review gates.

### 1. Complete the upstream handoff and direct Operation execution

- Finish the pending SimpleModeler generator/spec validation. Reuse applicable
  CNCF 1059 evidence. Refresh only the changed selected producer artifacts in
  dependency order, then regenerate/compile sm-workflow and check actual Help.
  Generated Scala is an output, not the place to repair the generator.
- Implement the production HostDecoder/configuration and connect
  `impl/ComponentFactory` to the existing three-workflow assembly. Use CNCF-owned
  storage and process lifecycle and current typed scope/permissions. Connect the
  public result Operations; no actual skill or separate host/client is required.
  Do not invent another engine, pool or host authority. Review record-only adapter
  assumptions against this Operation boundary and the single-user typed policy,
  rather than turning a missing external issuer into an execution stop.
- Write direct-Operation connected specs alongside these changes. Make one complete
  SplitPhase Preview/Apply path work with the spec as its caller first, including
  separate-process reopen. This proves the shared component runtime early; it is
  an internal milestone and does not close Phase 2 or defer the other workflows.

### 2. Finish RepositorySync, then GoalPhase on the same connection

- Connect the existing RepositorySync progression/effect/result modules to those
  Operations. Implement missing integration for the finite scenarios above, with real
  Git effects, configured validation, spec-supplied review/Human test response and
  observed terminal state. Reuse existing conflict and recovery behavior.
- Connect GoalPhase's ordered Step and whole-Phase closure using the same runtime
  and RepositorySync closure facilities where currently required. Exercise actual
  product validation and commit outcomes plus required review/repair/decision
  paths; retain repository-local versus workspace-wide ownership.
- Complete remaining SplitPhase cases and the three routes' separate-process
  recovery specs in this connected batch. Fix integration defects together;
  do not cycle through new per-helper acceptance or a new generic framework.

### 3. Integrated acceptance, independent review and closing

- Exercise all three direct-Operation scenarios, recovery and current generated
  Help/ABI. Run required consumer regressions/full validation on the assembled
  candidate, including resolution of the previously failed full-suite groups.
  Earlier selected PASS results do not convert the old full-suite failure to PASS.
- Perform independent read-only review of the delivered Phase scope, including
  changed producer contracts and their consumer connections. Recheck affected
  behavior after fixes and complete required checks; do not repeat an unchanged
  full suite or review solely because another helper/checkpoint was recorded.
- Document Operation inputs/results/configuration and limitations, attach actual evidence
  to P2-M02..M05, disposition remaining original IDs by the ownership table below,
  and perform the ordinary scoped commit/closing process in the development task.
  Planning work here does not authorize a commit, push or release operation.

## Completion ownership and carryover

Semantic reasoning mapping policy remains a resident reference-data candidate;
its semantic model and runtime mapping are implemented in Phase 5. It is not
an additional Phase 2 runtime prerequisite.

| Original obligation | Current owner and completion treatment |
| --- | --- |
| P2-M01..M05, practical parts of P2-D01..D03 / P2-G01..G06 / P2-R04..R05 / P2-T06 | Phase 2: all three public Operations with direct Executable Specification callers, actual effects, supplied decision/result handling, durable recovery, integrated validation/review, Operation usage and closing |
| Broader P2-U05/U15/U16, provider/transaction/event variants | Phase 8; required practical behavior remains Phase 2; completed evidence is retained |
| P2-T01..T05 and remaining transport portions of P2-T06 | Phase 6 owns the initial command/CLI wiring used by skills; Phase 8 owns full resident-server/MCP/CLI equivalence, concurrency and transport-specific restart breadth. Public decision Operation behavior stays Phase 2 |
| P2-R01..R03 | Phase 8 for resident-reference refresh, generic management/Job visibility beyond required normal startup |
| P2-C01 | Phase 5 owns the already-planned runtime observations; Phase 6 observes initial skill use; Phase 8 owns broader comparative AI-cost/round-trip evaluation. No measured AI-cost-reduction target gates Phase 2 |
| P2-C02 and skill bundle notes | Phase 6 owns command/CLI wiring, initial thinking-mode/skill interface, creation and real connection, including real skill-side input/result/interaction relay; optional broader distribution/coverage belongs to Phase 8 |
| Workspace-wide synchronization and Development Artifact Overlay | Phase 7; Phase 2's single-repository sync is the reusable prerequisite, not the multi-repository delivery |

An original checkbox retains its actual implementation/validation state even
when responsibility moves. Moving an obligation is neither acceptance nor a
waiver of a supported-use failure. The [Phase 8 map](phase-8.md) records destinations.
History preservation must not restore these deferred obligations to Phase 2.

The development task must read this revised completion scope on resume and use
it for planning and acceptance. Preserve the actual Goal identity/state and
binding history; this edit does not mutate the Goal tool. If its immutable
objective still spans later phases, report Phase 2 acceptance separately from
that broader Goal rather than adding later work back to Phase 2 or falsely
marking the broader Goal complete. Do not recreate a Goal to erase history.

## Validation cadence and progress reporting

Use compile/focused checks to diagnose actual failures during the connected
batches. A producer API change needs producer validation, dependency refresh and
consumer validation; combine related changes before that handoff where possible.
Publish success is not test evidence. Reuse prior validation only for its actual
inputs and relevant behavior; no TTL, hash, copy or unchanged-content certificate.

Development SBT continues through the registered runner and shared serial wrapper;
wait for released locks before another top-level invocation. This development
harness rule adds no global lock or cncf-skill dependency to product semantics.
Keep the existing whole-Phase review and required release checks, proportionate
to the delivered scope. A dependency refresh alone does not require a helper review.

Update the current [acceptance checklist](phase-2-executable-specification-checklist.md)
with the remaining connection, actual evidence and next action. Put individual
run/failure history in journals; do not prepend another "Current" narrative to
every planning document. Report progress by accepted behavior, not test count.
Do not introduce a new metric collector, status ledger or automated replanner.

Related: [route map](phase-2-practical-route-map.md),
[rollout](README.md),
[replanning decision](../journal/2026/10/2026-10-07-development-plan-optimization.md).
