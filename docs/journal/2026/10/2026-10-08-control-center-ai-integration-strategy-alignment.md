# Control Center AI Integration Strategy Alignment

Date: 2026-10-08
Status: consumer alignment
Authority: textus-control-center/docs/strategy/ai-integrated-development-operations.md

sm-workflow aligns with the Control Center target architecture in which Dot/Astra, OpenClaw/local LLM and Codex are separate capability providers.

## sm-workflow responsibility

sm-workflow remains the development-process authority and normal gateway to Codex software-engineering work.

A Codex chat/session may be bound as a worker execution context. Once bound, implementation/review work should flow through typed WorkOrder/Continuation/Result/Evidence rather than through repeated outer-agent prompt relaying.

OpenClaw is not the normal programming intermediary. An OpenClaw-originated software-development request should enter sm-workflow and let its execution policy bind Codex. Dot similarly submits goals/plans/candidates through admitted Control Center/sm-workflow boundaries rather than owning Codex workflow state.

This preserves model/agent replaceability and avoids making agent-to-agent invocation chains part of Workflow semantics.
