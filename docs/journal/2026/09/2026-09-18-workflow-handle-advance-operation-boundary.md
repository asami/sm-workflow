# Workflow Handle and Advance Operation Boundary

Date: 2026-09-18
Status: Design direction

## Context

CNCF Workflow is accessed from a Skill through CNCF Operations. A Skill does not
hold or invoke an in-process Workflow object directly and does not manipulate the
Workflow StateMachine state.

A profile-specific start Operation creates or obtains the durable WorkflowInstance
and returns a typed handle/reference. After that point, interaction should use the
generic Workflow Operation boundary.

## Selected interaction shape

Conceptually:

```text
startGoalPhase(input)
  -> WorkflowHandle

advanceWorkflow(handle, response?)
  -> WorkflowInteraction
```

The same pattern applies to other profiles:

```text
startSplitPhase(...)
  -> WorkflowHandle
  -> advanceWorkflow(...)

startRepositorySync(...)
  -> WorkflowHandle
  -> advanceWorkflow(...)
```

The start Operation is profile/Component specific because it owns admission and
initial input semantics. Subsequent progression is generic Workflow runtime
behavior.

## WorkflowHandle

The handle is not an object exposing methods such as `setState` or
`transitionTo`. It is a typed reference/capability identifying a durable
WorkflowInstance through the CNCF Operation boundary.

Its serialized form may carry or resolve information such as Component identity,
Workflow definition identity, and Workflow instance identity, but the exact ABI
is deferred.

The WorkflowInstance remains owned and persisted by its Component. The Skill
retains only the handle needed to address it.

## advanceWorkflow

`advanceWorkflow` is the preferred working name for the generic progression
Operation.

It means: admit any response supplied for the current external interaction, then
run Workflow-owned automatic/deterministic progression until the next external
interaction boundary or terminal outcome is reached.

This is intentionally not `stepWorkflow`: one call may absorb many internal
transitions and deterministic Operations.

`workflowAction` / `actionWorkflow` are avoided because Action already has
StateMachine/CML semantics. `issueWorkflow` describes only the outward request
side and does not express admission of a previous response plus state progression.

## No public suspend/resume protocol requirement

A separate public `Suspended` state token, Continuation object, and
`resumeWorkflow` Operation are not required by this interaction model.

The durable WorkflowInstance already owns the waiting/progression state, and the
WorkflowHandle is its stable reference. Therefore the next response can be
supplied to `advanceWorkflow` itself:

```text
advanceWorkflow(handle)
  -> AIWorkRequest

AI/Skill performs exactly that work

advanceWorkflow(handle, AIWorkResult)
  -> next WorkflowInteraction
```

The runtime may internally use suspended/waiting concepts, but they need not
become a second public lifecycle protocol unless a later requirement proves that
necessary.

## External interaction result

The result of `advanceWorkflow` should represent the next boundary rather than
leak internal StateMachine transitions.

A working umbrella term is `WorkflowInteraction`, with outcomes such as:

- semantic/AI work request;
- human decision request;
- wait/external-event condition;
- completed terminal result; or
- failed terminal/result state.

Exact names and schemas remain ABI work.

## Human and AI boundary

Human and AI participation use the same Workflow Operation architecture. They do
not receive authority to mutate Workflow state directly.

```text
WorkflowInstance
  -> advanceWorkflow
  -> external request
  -> Skill / AI / Human
  -> typed response
  -> advanceWorkflow(handle, response)
  -> deterministic admission
  -> Workflow progression
```

Human Decision and semantic AI work remain semantically distinct interaction
types even though both return through the same Operation boundary.

## Design rule

> Skills call CNCF Operations. A profile-specific start Operation returns a
> WorkflowHandle; thereafter generic `advanceWorkflow` uses that handle to
> admit external responses and advance the durable WorkflowInstance to its next
> external boundary. The handle is a reference, not a mutable Workflow object.

This direction should be reconciled with existing sm-workflow documents that
currently expose Suspended Continuation / resume terminology before those terms
are frozen into the public CNCF Workflow ABI.

## Specification follow-up

The corresponding normative design input is
[`workflow-handle-and-advance-operation-boundary.md`](../../../notes/workflow-handle-and-advance-operation-boundary.md).
Writing that specification clarified the following consequences of this design
direction:

- `sm-goal-phase`, `sm-split-phase`, and `sm-repository-sync` remain separate
  human-selected entry points. Direct skill selection, or explicit selection in
  a host/client, supplies invocation authority for the matching profile-specific
  start Operation.
- A recommendation such as `SPLIT_REQUIRED` may carry a typed start-input
  reference, but it is advisory and cannot start another WorkflowInstance or
  manufacture a human-selection record.
- A generic public `StartWorkflowRun` is replaced by profile-specific start
  Operations. A generic start application service may still be shared behind
  those Operations as an implementation detail.
- `WorkflowInteraction` is the public next-boundary projection of a framework
  `WorkflowHandle` plus its current CNCF `Continuation`. Continuation identity,
  expected revision, ContextSnapshot, and typed completion/evidence remain
  available through the projection and its fail-closed wire forms; Continuation
  is not a second public root identity or an independent suspend/resume
  lifecycle.
- Work-result and human-decision admission may be decomposed into internal
  application commands, while the skill-facing progression protocol remains
  `advanceWorkflow(handle, response?)`.
- Revision guards, idempotency, leases, typed response admission, and read-only
  status/history remain required; they move to the handle/interaction protocol
  rather than depending on a public Continuation lifecycle.

These clarifications require one coordinated reconciliation of the main design,
Phase 1 plan/checklist, and the three profile definitions. Partial edits must not
leave generic and profile-specific start contracts, or public Continuation and
WorkflowInteraction lifecycles, active at the same time.
