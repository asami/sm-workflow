# Phase 4: Service Bus Development Events

## Goal

Integrate sm-workflow with CNCF Service Bus so development lifecycle and admission facts can be monitored asynchronously and durably by Control Center and future mobile/watch clients.

## Scope

- Define the sm-workflow development event vocabulary.
- Publish significant Workflow, GoalPhase, Action, Judgment and Admission lifecycle events.
- Use JournalPolicy.AUTHORITATIVE for durable operational facts.
- Keep high-frequency progress/heartbeat/reference traffic transient by default.
- Populate correlation/causation and workflow/CAR/project context.
- Expose enough current-state identity for Control Center to combine sm-workflow views with journal timelines.
- Validate the remote-monitoring scenario with Control Center.
- Ensure approval is performed through admission/continuation operation and the resulting accepted/rejected event is producer-authored.
- Keep Codex/OpenClaw provider details behind sm-workflow contracts.

## Dependencies

- CNCF Service Bus / Phase 93.
- Control Center development-operations integration.

## Non-goals

- Event Sourcing or workflow state reconstruction from the journal.
- Direct Internet exposure of the Service Bus.
- Smartphone/watch implementation in this phase.
- Kafka/Kinesis SYSTEM transport implementation.
