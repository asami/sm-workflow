# Practical Execution Routing Phase Split

Date: 2026-10-03
Status: design decision

## Background

While preparing sm-workflow for practical use after Phase 2, the implementation/review routing discussion showed that model/effort matching alone is insufficient.

Existing sm-goal-phase behavior already allowed obvious one/two-line edits to be performed by the current task. This led to a TRIVIAL fast-path concept. Separately, matching-profile implementation can avoid lossy handoff and repository rediscovery, while large implementation can justify delegation simply to protect parent context. Review has a different reason for delegation: independence.

The first draft risked mixing Skill semantic assessment, sm-workflow policy, and concrete parent/child task selection.

## Decision

Use three layers:

1. sm-* Skill performs application-semantic WorkClassification during planning.
2. sm-workflow resolves logical placement, independence, and reasoning requirements.
3. the execution harness selects the concrete provider/profile.

Do not make the Skill choose Luna/Sol or parent/child. Do not make sm-workflow know ChatGPT/Codex task topology.

## Phase split

Phase 5 becomes the practical vertical slice: ReasoningClass plus PROGRAMMING/ENGINEERING, TRIVIAL INLINE, matching-profile bounded INLINE implementation, Luna/high PROGRAMMING delegation, and independent Sol review with evidence.

Phase 6 generalizes the proven Skill/Workflow execution-routing model: generic placement/independence semantics, execution identity and evidence conformance, provider capability matching, fallback/escalation, richer context/resource routing, and broader participant classes.

CNCF Phase 80 is the natural generic owner because it already exists for operationally demonstrated Workflow invocation/participant/context integration extensions. Its minimum extension is required by sm-workflow Phase 5; broader generalization is driven by sm-workflow Phase 6 evidence.

Phase 2 remains unchanged and focused on server/MCP application runtime.
