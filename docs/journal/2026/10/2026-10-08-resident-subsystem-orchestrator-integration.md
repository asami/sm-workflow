# Resident sm-workflow Subsystem and Orchestrator Integration

Date: 2026-10-08
Status: architecture direction
Consumer: Textus Orchestrator

sm-workflow will evolve from its initial one-shot/command execution topology toward a resident Subsystem that centrally owns development Workflow instances, worker bindings, execution/review progression and development status.

This deployment evolution does not require a Skill API redesign.

Skill, CLI, MCP and Textus Orchestrator continue to use the same typed sm-workflow application Operations. The difference is transport/deployment binding: an operation may execute in the one-shot/local runtime or be sent to the resident sm-workflow Subsystem.

Textus Orchestrator treats sm-workflow as the SOFTWARE_ENGINEERING capability provider. It does not invoke Codex directly or own GoalPhase progression.

A Codex chat/session may bind/register as a sm-workflow worker execution context. sm-workflow issues typed WorkOrder/Continuation requests and receives Result/Evidence. Codex chat state is execution context, not development-process authority.

This preserves the current public contracts while enabling centralized multi-chat/multi-machine development management and Orchestrator communication.
