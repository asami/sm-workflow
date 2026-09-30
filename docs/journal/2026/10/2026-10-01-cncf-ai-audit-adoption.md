# CNCF AI Audit adoption direction

Date: 2026-10-01

Decision: sm-workflow will consume the generic CNCF AI Audit capability planned in CNCF Phase 96 for AI-backed JudgmentAction/Continuation activity. sm-workflow will not define an independent authoritative AI communication log.

Workflow context and later Candidate-Admission/review outcomes are important evidence because an AI response that appears valid at generation time may be corrected, rejected or fail downstream. These outcomes should remain correlated with the original AI Interaction.

The resulting history is intended not only for audit but also for prompt/context tuning, deviation detection and progressive determinization of stable work into normal Workflow/rules/programs.
