# Phase 25.4: Task Classification and Context Assessment

Status: planned; execution not started
Planned: 2026-11-25 (Asia/Tokyo)
Scheduled: after [Phase 25.3](phase-25.3.md), before Phase 28.2 (proposed)
Depends on: existing Phase 9 routing, [Phase 25.2](phase-25.2.md) metrics and Phase 25.3 dual allowance
Checklist: [Phase 25.4 acceptance](phase-25.4-checklist.md)

## Goal

Improve task classification independently of model selection. Distinguish
reasoning difficulty from change size and context requirements so bounded API
tasks can use suitable high-capability models without unnecessary agent
execution. Supply provider-neutral capability requirements to Phase 27.

## Delivery

### P254-01 — Task taxonomy

Extend existing typed task and judgment contracts rather than replace them.
Classify purpose (implementation, repair, review, judgment, planning),
reasoning difficulty, change scope, context breadth, acquisition method,
continuity, quality requirements, verifiability and failure impact.
Distinguish deterministic repair, routine compiler repair, local semantic
repair, difficult type or logic repair, and exploration-dependent repair.
Distinguish lint, smell detection, local semantic review, specification and
invariant review, and architectural review. Include classification, strategy
selection, root-cause analysis, convergence and Take Over judgments.
Keep unknown dimensions explicit; file count alone cannot determine difficulty.

### P254-02 — Context assessment

Represent context sufficiency independently of reasoning difficulty:
self-contained, statically resolvable, dynamically resolvable, exploration
required, session dependent, or unknown. Identify required evidence, missing
context, provenance, exact candidate identity and bounded input requirements.
Reuse deterministic context extraction where possible. Allow typed requests
for additional information with bounded iterations; use existing Take Over
when exploration or session continuity is required. Do not expose additional
source or secrets beyond existing authorization and sharing policy.

### P254-03 — Capability requirements and classification policy

Derive provider-neutral requirements for reasoning, context, tools, output
contract and validation. Use deterministic rules first; permit a bounded
lightweight AI judgment only for genuinely ambiguous classification under
existing budget policy. Record classification evidence and uncertainty.
Routing itself remains deterministic. Separate task taxonomy from the AI
capability model and allowance identity. Candidate routes include deterministic
tools, local LLMs, eligible API models and agent CLIs. Routine bounded tasks
may fit Terra/Luna-class models; difficult bounded type, invariant, security
or convergence judgments may fit Sol-class models when verified eligible.
These are hypotheses, not fixed model assignments or quality guarantees.
Reuse Phase 25.3 admission; preserve existing defaults unless an explicit
qualified policy changes them. Phase 27 owns generalized cost-aware routing.

### P254-04 — Failure-aware reclassification

Distinguish insufficient reasoning, missing context, exploration needs,
output truncation, excessive task size, ambiguous specification, nonconvergence,
quota refusal and uncertain execution. Choose bounded remedies: additional
context, different capable model, task split, clarification, or Take Over.
Do not escalate reasoning merely because context is missing. Preserve repair
limits, candidate validation, rollback and authorization; uncertain execution
must follow existing recovery semantics.

### P254-05 — Evidence and qualification

Extend available Phase 25.2 observations with task classification, context
assessment, selected capability and route, failure reason, validation outcome,
retries, latency and reported token usage. Preserve missingness and avoid
additional calls solely for metrics. Compare representative bounded tasks
using identical context and validation across eligible model classes where
authorized. Distinguish measured quality and cost from estimates; do not
assume stronger models always improve outcomes. Verify deterministic rules,
unknown classifications, bounded context requests, failure-specific routing,
resume and ordinary review/repair/judgment paths using fixtures and scoped
qualification. Record independent review before closure.

## Ownership and boundaries

sm-workflow owns task semantics, context assessment, Workflow and Admission.
textus-ai owns provider capability descriptors, generic execution and quota
admission. Reuse Phase 9 convergence and Take Over semantics, Phase 25.2
metrics and Phase 25.3 allowance accounting. Do not duplicate Phase 26 server
work or Phase 27 generalized routing. Planning does not start a Session.
