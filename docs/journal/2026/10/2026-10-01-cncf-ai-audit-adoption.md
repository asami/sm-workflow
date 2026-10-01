# CNCF AI Audit adoption direction

Date: 2026-10-01

Decision: sm-workflow will consume the generic CNCF AI Audit capability planned in CNCF Phase 97 for AI-backed JudgmentAction/Continuation activity. sm-workflow will not define an independent authoritative AI communication log.

Workflow context and later Candidate-Admission/review outcomes are important evidence because an AI response that appears valid at generation time may be corrected, rejected or fail downstream. These outcomes should remain correlated with the original AI Interaction.

The resulting history is intended not only for audit but also for prompt/context tuning, deviation detection and progressive determinization of stable work into normal Workflow/rules/programs.

## AI precision feedback

sm-workflow treats its deterministic execution and admission lifecycle as an evidence-producing harness for AI quality. For each semantic AI/agent work item, CNCF AI Audit should be able to correlate agent/provider/model and prompt/context version with work type, deterministic validation/test receipts, review result, Candidate-Admission result, human correction, retry/escalation, downstream outcome, latency, usage and cost.

This enables empirical comparison of Dot, OpenClaw, Codex and future agents without embedding any of them into sm-workflow semantics. The same Workflow may be driven by a project-resident external agent while Git/SBT/test and other admitted procedural operations remain Workflow-owned deterministic providers.

A target operating pattern is:

~~~text
project agent (Dot / OpenClaw / Codex / ...)
    -> sm-workflow
         -> deterministic Git/SBT/test/validation
         -> review / Candidate-Admission
         -> CNCF AI Audit evidence
              -> quality metrics
              -> routing / operating-policy feedback
~~~

Useful feedback includes Admission pass/reject rate, human modification rate, validation/test failure, retry/escalation, downstream failure, latency and cost per admitted result. These metrics may recommend a different agent/model for a work type, but they do not themselves authorize routing or policy changes.

This separation is deliberate: if a low-cost Dot can directly drive sm-workflow, it may operate as a project-resident agent without creating Codex tasks; if its allowance/cost or quality is unattractive, OpenClaw or another agent can drive the same Workflow surface. sm-workflow therefore standardizes the work protocol and evidence, not the agent.
