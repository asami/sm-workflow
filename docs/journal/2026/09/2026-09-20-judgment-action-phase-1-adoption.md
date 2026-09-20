# Phase 1 Adoption of CNCF JudgmentAction

Date: 2026-09-20
Status: accepted planning decision

## Decision

sm-workflow Phase 1 consumes CNCF Phase 77 `JudgmentAction` and
`JudgmentResult`; it does not define a product-specific AI Action type.

Application Workflow definitions supply software-development judgment payloads:
goal, context, admitted alternatives, criteria, and expected result. The worker
returns only decision, rationale, and evidence. CNCF StateMachine guards and
transitions determine subsequent progression.

Codex is the initial external worker exercised through the CNCF Generic Skill /
Continuation boundary. A deterministic Provider proves the same contract and
provider replaceability. Future jev, human, local-model, or other Provider
integration must not require changing Workflow definitions or transition
semantics.
