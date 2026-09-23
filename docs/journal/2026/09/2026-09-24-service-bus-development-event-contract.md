# Service Bus Development Event Contract

Date: 2026-09-24

sm-workflow adopts CNCF Service Bus authoritative events as the asynchronous development-monitoring boundary. The primary target scenario is sm-workflow running Codex/OpenClaw while Control Center monitors development CAR progress remotely from Web, smartphone and Apple Watch/Pixel Watch.

Significant workflow, GoalPhase, Action, Judgment and Admission lifecycle changes are journaled. High-frequency progress/reference traffic is transient by default. The journal is not used for Event Sourcing; sm-workflow state remains authoritative for current state while the journal is authoritative for selected historical facts.

Persistent events provide temporal decoupling: Control Center/mobile/watch need not be online when a development event occurs. Human approval returns as an authenticated admission/continuation operation, never as a client-authored ApprovalAccepted event. sm-workflow validates current state and publishes the authoritative outcome.
