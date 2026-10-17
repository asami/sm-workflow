# AI Review Execution Boundary

Date: 2026-10-18

## Decision

cbd-support owns review knowledge and review-policy construction, but AI execution is not intrinsically part of that responsibility.

The common internal product should be a structured `ReviewRequest` (or equivalent review specification), rather than only an opaque prompt string. It should be able to carry review class/scope, instructions and criteria, required context/evidence, expected result schema, policy, and a rendered prompt.

Two execution routes are supported:

1. **Standalone cbd-support**
   - cbd-support may use its own AI runner and return the completed review.
   - This preserves convenient CLI/direct usage.

2. **sm-workflow orchestration**
   - cbd-support returns the `ReviewRequest` without invoking AI internally.
   - sm-workflow gives that request to the active Codex/LLM worker and owns model selection, execution, escalation, evidence recording, and workflow continuation.
   - The execution engine may therefore be Codex, a local LLM, or another compatible reasoning engine.

The review definition/policy must not be duplicated between these routes. Both originate from the same cbd-support review knowledge.

## Rationale

When sm-workflow already owns the development context, having cbd-support start another AI session causes unnecessary context reconstruction and model cost, and makes model routing harder to control. Returning the review specification keeps cbd-support independent of a particular AI runtime and lets sm-workflow reuse its existing model-routing and escalation mechanisms.

This also makes the same review request suitable for local-LLM evaluation, decline/escalation to a stronger model, and later A/B evaluation of reasoning engines.

## Responsibility boundary

- CAR lint: deterministic structural/mechanical inspection.
- cbd-support: semantic review knowledge, criteria, policy, and ReviewRequest construction.
- Codex/LLM: semantic reasoning and review execution.
- sm-workflow: orchestration, execution-engine selection, escalation, evidence/history, and admission/continuation decisions.

Review depth can vary by workflow timing (step-end, phase review, phase final review) while retaining the same execution boundary.
