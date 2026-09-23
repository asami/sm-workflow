# Service Bus Development Events

sm-workflow publishes significant development lifecycle facts through the CNCF Service Bus. These events provide an authoritative operational history and temporally decouple sm-workflow from Control Center, mobile/watch clients and future consumers.

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
