# Phase 1 Checklist: Common Contract Application Executable Specifications

Status: planned
phase: [Phase 1](phase-1.md)

This is the sole closure authority for Phase 1 after the 2026-09-20 common
contract scope reconciliation. It preserves the older
[operational inventory](phase-1-checklist.md) as post-Phase-1 planning, without
claiming that any of its open items are complete.


## Candidate-Admission Model acceptance

- [ ] CAM-01: A Skill can bring program and planning/management artifacts to a candidate state and invoke the profile Step-close Operation without selecting the next Workflow state.
- [ ] CAM-02: `RequestStepClose` records closure intent plus candidate snapshot/evidence and evaluates admission before commit.
- [ ] CAM-03: A fresh full-Step review supplied proactively is reused and does not cause a duplicate semantic review WorkOrder.
- [ ] CAM-04: A successful scoped review is preserved as evidence but produces a full-review Continuation when the closure contract requires full-Step scope.
- [ ] CAM-05: A repair that invalidates prior review coverage produces the policy-required focused or full re-review according to evidence freshness/scope.
- [ ] CAM-06: Review/Repair/ReReview completion resumes the existing closure admission automatically; the Skill does not submit a second close request.
- [ ] CAM-07: Missing semantic evidence becomes a typed Continuation, missing deterministic evidence is produced/verified by internal providers, and authority gaps become Decision boundaries.
- [ ] CAM-08: Semantic Result/Evidence cannot directly select transition, commit, or Step Closed.
- [ ] CAM-09: Final deterministic commit includes the admitted program and planning/management-file state and records commit/evidence receipts before Step Closed.
- [ ] CAM-10: Candidate/Admission semantics do not introduce Phase/Checklist vocabulary into CNCF generic Workflow DTO/runtime contracts.

## Entry and common-contract binding

- [ ] P1-E01: Record the completed Cozy Phase 62.3 producer handoff and CNCF
  Phase 77 release/revisions plus their compatible generated Workflow ABI
  versions.
- [ ] P1-E02: Bind application definitions only to CNCF
  `WorkflowStartRequest`/`WorkflowStartResult`, `WorkflowHandle`, closed
  `Continuation`, `ContinuationRequest`/`ContinuationResult`/`WorkResult`,
  typed Result/Evidence/ExecutionEvidence, and fail-closed JSON codecs;
  preserve Workflow/Continuation identity, expected revision, and
  `ContextSnapshot`; create no application-owned generic protocol.
- [ ] P1-E03: Define `WorkflowInteraction` as the public projection of a
  framework `WorkflowHandle` and current `Continuation`, preserving identity,
  expected revision, typed response admission, exact registered completion
  Operation identity, suspension, and terminal semantics.

## Application workflows and fixtures

- [ ] P1-A01: Define typed application start/work/result/terminal payloads for
  GoalPhaseWorkflow, SplitPhaseWorkflow, and RepositorySyncWorkflow.
- [ ] P1-A02: Define the three CML Workflow/StateMachine definitions and bind
  their application payloads to the common CNCF contract without recreating
  generic Start, Handle, or Continuation semantics.
- [ ] P1-A03: Provide deterministic/test Providers and fixtures sufficient to
  exercise each definition through CNCF Phase 77.
- [ ] P1-A04: Supply application Presentation content to the common
  title/current-situation/optional-summary-next-action-reason-progress model.
- [ ] P1-A05: Supply a versioned application mapping policy that consumes the
  abstract ReasoningLevel. A Skill/Host-dispatched external WorkOrder records
  compatible ExecutionEvidence; deterministic/local Providers invent no worker
  profile, and concrete worker selection is never a Workflow guard or
  transition.
- [ ] P1-A06: Specialize CNCF `JudgmentAction` for each genuinely contextual
  software-development judgment using typed goal, context, alternatives,
  criteria, and expected result; keep deterministic work as
  `OperationAction`.
- [ ] P1-A07: Define application `JudgmentResult` payloads containing an
  admitted decision, rationale, and evidence without encoding the next state
  or next Action in the worker result.

## Executable specifications

- [ ] P1-S01: Prove each application Workflow starts, absorbs deterministic
  progression, returns its first semantic boundary, accepts a typed result,
  and reaches the correct typed terminal result.
- [ ] P1-S02: Prove deterministic/test Provider execution requires no AI turn,
  while semantic WorkOrder/Decision boundaries remain explicit.
- [ ] P1-S03: Prove schema-versioned JSON fixtures round-trip
  Start/ContinuationRequest/ContinuationResult/WorkResult/Terminal common
  envelopes with each application's typed payloads and fail closed for
  incompatible schema, identity, revision/ContextSnapshot, result, or required
  evidence.
- [ ] P1-S04: Prove profile-specific start/completion operations and
  WorkflowInteraction remain projections of the common framework contract
  rather than a second generic lifecycle; expose no Skill-facing generic
  `StartWorkflowRun` or `advanceWorkflow` protocol.
- [ ] P1-S05: Record exact fixture, generated ABI, CNCF, and sm-workflow
  revisions in the consumer handoff.
- [ ] P1-S06: Exercise Codex as the initial external worker for at least one
  `JudgmentAction` through the CNCF Skill/Continuation JSON boundary and prove
  its typed decision/rationale/evidence round trip.
- [ ] P1-S07: Prove the same judgment contract with a deterministic Provider
  and show that worker replacement does not alter Workflow definition identity
  or transition semantics.
- [ ] P1-S08: Prove only StateMachine guards/transitions map an admitted
  `JudgmentResult` to ACCEPT/REVISE/ESCALATE-equivalent progression; reject an
  unknown decision and any worker attempt to select or mutate the next state.
- [ ] P1-S09: Prove each profile-specific start Operation requires matching
  explicit human invocation authority; reject recommendation-only start,
  mismatched profile selection, and attempts by a Skill, Workflow, or terminal
  result to manufacture the selection record.
- [ ] P1-S10: Prove launcher JSON is a fixture adapter for the registered typed
  application Operations, not the Skill contract itself, and that a
  Continuation-selected completion Operation returns the same common envelope
  independently of transport grammar.

## Exclusions

- [ ] P1-X01: Prove Phase 1 completion does not require production public
  skills/catalogs, standalone/CAR distribution, broad CLI/UI, production SQLite
  profile, operational lease/restart/concurrency/recovery hardening, concrete
  AI provider dispatch, cost dashboards, or MCP/server adapters.
- [ ] P1-X02: Permit a tiny fixture adapter only when necessary for an
  executable specification; it must not become a production operational
  surface.

## Closure

- [ ] P1-C01: Run the relevant executable specifications on the final intended
  application definition/fixture tree and record their evidence.
- [ ] P1-C02: Update Phase 1 status only after every item in this checklist is
  complete; do not treat the historical operational inventory as a closure
  prerequisite.
