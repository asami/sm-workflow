# Human-in-the-Loop through Continuation and Admission

Date: 2026-10-05
Status: consumer design principle
Related: CNCF Phase 77, Phase 90, Phase 102

sm-workflow should model human review/approval through CNCF Admission semantics and use Continuation only as the generic runtime mechanism when an external human result is required.

The parent Skill, Dot/OpenClaw, Slack adapter or other host must not own hidden knowledge of internal GoalPhase states or decide how approval advances the Workflow. It receives the current typed continuation/request, obtains the required typed decision/result, and resumes through the admitted operation.

This preserves sm-workflow as a component-level abstraction: planning, implementation, review, repair and closing can evolve their internal Human/AI participation without changing the external GoalPhase contract.

A later policy change from mandatory human review to AI-assisted or automatic admission must therefore be expressible without rewriting callers or exposing a new outer orchestration loop.
