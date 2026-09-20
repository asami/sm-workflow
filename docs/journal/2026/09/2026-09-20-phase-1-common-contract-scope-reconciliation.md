# Phase 1 Common Contract Scope Reconciliation

Date: 2026-09-20
Status: accepted design decision

## Decision

`sm-workflow` Phase 1 is the application executable-specification consumer of
the CNCF Phase 77 common Workflow contract. It owns application payloads,
GoalPhase/SplitPhase/RepositorySync definitions, application presentation
content, profile-mapping policy, deterministic/test fixtures, and executable
specifications. It does not own a replacement generic Start, Handle,
Continuation, Result/Evidence, or JSON protocol.

`WorkflowInteraction` is the public projection of framework `WorkflowHandle`
and current `Continuation`. Profile-specific start operations may specialize
the framework Start operation, but preserve framework identity, revision,
response-admission, suspension, and terminal semantics.

Phase 1 accepts the common `Presentation` including title/current situation and
optional summary/next action/reason/progress. A Skill/Host-produced WorkResult
uses typed `ExecutionEvidence` to record the abstract requirement, selected
profile, and mapping-policy version; concrete worker selection never controls
application Workflow transitions.

## Scope

Phase 1 closes when the three application Workflows execute reproducibly as
executable specifications on the completed Phase 77 foundation. Production
public skills, CLI/bundle distribution, SQLite operational profile, broad
restart/concurrency/lease hardening, production provider dispatch/recovery,
and transport adapters remain post-Phase-1 operational hardening.

The previous operational checklist remains historical planning inventory. The
[Common Contract Application Executable Specifications checklist](../../../phase/phase-1-executable-specification-checklist.md)
is the Phase 1 closure authority.

## Executable-specification scope review adoption

The executable-specification scope review dated 2026-09-20 is adopted by this
decision record as Phase 1 planning input. This record is the durable reference;
no separate handoff document is required. The Phase and closure checklist now
make the following points explicit:

- Phase 1 depends on Cozy Phase 62.3 producer handoff as well as CNCF Phases
  64, 64.2, and 77; this is a dependency record, not an assertion that those
  upstream phases are complete.
- CNCF owns `ContinuationRequest`/`ContinuationResult`, Workflow and
  Continuation identity, expected revision, `ContextSnapshot`, typed
  Completion/Evidence, and the fail-closed JSON wire contract. `sm-workflow`
  only specializes these contracts with application payloads.
- `ExecutionEvidence` records a worker-profile/mapping-policy choice only for
  a Skill/Host-dispatched external WorkOrder. A deterministic/local Provider
  records no invented worker profile; evidence never becomes a Workflow guard
  or transition input.
- The historical generic-model and public-operation proposals remain
  post-Phase-1 planning material and cannot enlarge the executable-specification
  closure boundary.
