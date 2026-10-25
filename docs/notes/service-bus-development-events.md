# Service Bus Development Events

sm-workflow is intended to publish significant development lifecycle facts through the CNCF Service Bus. These events provide an authoritative operational history and temporally decouple sm-workflow from Control Center, mobile/watch clients and future consumers.

## Delivery status and CC51-S2 contract (2026-10-25)

The vocabulary and scenarios below preserve the original 2026-09-24 design
intent. Unit B now supplies source for actual native GoalPhaseStarted /
GoalPhaseCompleted and genuine Plan/dependency Human question/decision producers,
plus the normal provided read-only DevelopmentObservationService. This source
record is not an executed validation, installation or consumer acceptance receipt.
Its actual managed development Journal retains Finding, ExecutionObservation
and EvidenceStorageObservation under a different contract; it does not satisfy
the Service Bus history requirement.

The [operational-view specification](../spec/development-operational-view.md)
defines the v1 public read and significant-event contract. Its
[design](../design/development-operational-view.md) connects the native retained
state owner, managed event writer and existing CNCF EventStore, and names
separate literal implementation sets and normal validation requirements.
The admitted implementation includes the CML/ABI, factory, native owner source
and executable specifications. Production host grants and runtime installation
are outside that change. The host selects persistence for `development` events
and supplies ordinary managed-event and history-view authority; the isolated
specifications select their own explicit capabilities and storage policy.

CNCF already supports selected persistent events in managed native Workflow
transitions/completions, including verified storage and post-commit dispatch.
Reuse the records parameters/callbacks of WorkflowManagedMechanicalTransition,
WorkflowManagedIssuedWork/Decision and WorkflowManagedCompletion/DecisionCompletion.
Do not add an after-response dual write, another event bus, event store or retry
controller. JournalPolicy.AUTHORITATIVE below is policy intent, not a claim
about a runtime enum. Actual persistence remains the host's EventPersistencePolicy.

The concrete CNCF gap is a **generic** managed committed-event read with exact
opaque namespace/key and finite event-name selection **before** its bounded
latest-sequence query. Existing EventRecord attributes can carry that pair.
CNCF owns its shape, generic same-row projection, canonical record checks,
persistence, bounded query, authorization and lease; it does not recognize the
development schema, CAR/Phase/Session fields, lifecycle names or key calculation.
sm-workflow owns the versioned subject-to-key mapping and full envelope decoding,
including rejection of a mismatched subject or lookup collision after the generic
read. The earlier application-specific CNCF column design is superseded before
implementation; codecs must not acquire an application dependency.

Generic event search and the default EventStore in-memory view are not that
durable history API. Planned generic same-row query fields preserve legacy event
bytes; v1 makes retained-window coverage explicit and claims no backfill or
complete offline catch-up. sm-workflow will expose only its typed safe result
through its normal observation service; the Service Bus stays internal.

Initial implementation targets genuine GoalPhase start/terminal facts and actual
Human question/decision milestones. Action, Judgment, ProviderExecution and the
remote approval scenario below still require their actual corresponding writers
and admission delivery. Transport acceptance is not ApprovalAccepted; a failed
check is not GoalPhaseFailed. Phase 5.1 provides read-only monitoring and safe
configured details links. Remote watch/mobile admission remains future work.

## Principles

- sm-workflow remains authoritative for current workflow/development state.
- Selected Service Bus events with JournalPolicy.AUTHORITATIVE are authoritative records of what happened.
- OpenTelemetry remains observational telemetry.
- Event Sourcing/state reconstruction is not a goal.
- Human instructions/approvals are operations/admission/continuation requests. Clients must not manufacture outcome events.
- After an operation is validated/applied, sm-workflow publishes the resulting event.

## Initial authoritative event vocabulary

Lifecycle:
- WorkflowStarted
- WorkflowCompleted
- WorkflowFailed
- GoalPhaseStarted
- GoalPhaseCompleted
- GoalPhaseFailed
- ActionStarted
- ActionCompleted
- ActionFailed

Judgment/admission:
- JudgmentRequested
- JudgmentCompleted
- AdmissionRequested / ApprovalRequested
- AdmissionAccepted / ApprovalAccepted
- AdmissionRejected / ApprovalRejected
- AdmissionExpired or AdmissionCancelled when those semantics are introduced

Provider/execution milestones where they are meaningful operational facts:
- ProviderExecutionStarted
- ProviderExecutionCompleted
- ProviderExecutionFailed

High-frequency progress, heartbeat, status/reference requests and similar information are normally transient (JournalPolicy.NONE) unless a specific workflow declares them authoritative.

## Envelope

Events should carry stable identity/context sufficient for correlation and presentation, including eventId, eventType, timestamp, source, subject, correlationId, causationId, workflow handle/id, CAR/project identity where available, phase/action identity and relevant outcome/error metadata. Provider identity (Codex/OpenClaw/etc.) may be included when relevant but consumers must not depend on provider internals.

## Remote monitoring scenario

Control Center consumes current sm-workflow views plus journaled events to project development status onto development CARs and present timelines on Web/mobile/watch. ApprovalRequested may reach a watch long after publication because it is journaled. Approve invokes an authenticated sm-workflow admission/continuation operation; only sm-workflow publishes ApprovalAccepted after validation.
