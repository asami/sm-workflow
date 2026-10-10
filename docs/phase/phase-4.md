# Phase 4: Service Bus Development Events

## Delivery sequence (2026-10-05)

The [incremental-use plan](README.md) completes Phase 2 at its first usable
runtime boundary, then builds the runtime foundation without skills in Phase 5
and connects thinking modes/skills in Phase 6. Gradual real use starts after
Phase 6 acceptance and continues through Phase 7, with remaining necessary work
closed in Phase 8. Phase 4 is an orthogonal monitoring extension, not a
prerequisite for the Phase 5 foundation or Phase 6/7 rollout.
Its scheduling follows actual monitoring needs; its own
Service Bus/Control Center acceptance remains unchanged.

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
