# Replaceable Semantic Management Agent

Date: 2026-10-05
Status: integration direction

## Context

Textus Control Center may use a long-lived semantic management agent. OpenAI Dot is a preferred candidate for project-context management and supervision, while OpenClaw must remain a replaceable alternative.

## sm-workflow boundary

sm-workflow remains the development-process authority. Dot/OpenClaw MUST NOT own phase state, continuation state, admission state, review state, or terminal/closing state.

A management agent may:

- inspect workflow/project status through admitted operations;
- interpret current development context;
- propose or initiate an allowed goal/phase operation;
- execute a semantic Work Order when bound as the selected worker;
- request or relay human decisions;
- react to workflow events.

It may not infer or directly mutate the next workflow state from conversation memory.

The existing typed Start / Continuation / WorkOrder / Result / Evidence / Decision contracts are the integration boundary. Agent products are adapters/providers, not Workflow vocabulary.

## Replaceability

A workflow started while Dot is the management agent must remain resumable when OpenClaw becomes the management agent, and vice versa, subject only to ordinary authorization/capability requirements.

No Dot/OpenClaw session identifier, prompt format, model name, or proprietary task state becomes canonical sm-workflow state.

GitHub/design artifacts and sm-workflow durable state provide reconstruction authority. Control Center provides the integrated operational projection.

## Human interaction

Slack is the primary near-term human interaction surface for approvals, questions, and status notifications. Human decisions are submitted through typed decision/admission operations; Slack conversation text is not workflow authority.

This preserves future mobile/watch interaction without changing workflow semantics.
