# Phase 25.4: Strategic Judgement, Task Design and Context Assessment

Status: planned; execution not started
Planned: 2026-11-25 (Asia/Tokyo)
Scheduled: after [Phase 25.3](phase-25.3.md), followed by [Phase 25.5](phase-25.5.md)
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
contract and validation. Apply the Free Advanced AI-first / Programmatic Fallback policy in P254-06.
Mechanically decidable facts and hard constraints remain programmatic. Record classification evidence and uncertainty.
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

### P254-06 — Strategic judgement and task design

Separate Strategic Judgement (strategy and risk), Task Assessment
(classification and capability requirements), Task Design (review viewpoints
and execution instructions), and Task Execution (performing the work).
Prefer verified eligible Sol/Astra-class advanced models in the 1M daily
allowance for semantic strategic judgement, assessment and design whenever
free-only admission is available. No failed programmatic classification is
required first. Do not invoke AI for mechanically decidable facts or merely
to consume allowance.

Use stateless requests with bounded Strategy Context: goal, current state,
constraints, evidence, summarized attempt history, available capabilities,
candidate identity and typed decision contract. sm-workflow owns persistent
state. Generate typed Task Specifications containing review viewpoints,
execution instructions, repair strategy, task decomposition, required context,
expected outputs and validation conditions as applicable. Record provenance
and uncertainty. Use rules and templates to produce the same typed contract
when advanced free admission is unavailable or the result is unusable.
Never automatically pay for strategic judgement. Preserve uncertain-usage
recovery and reservations; resume advanced judgement when eligible capacity
returns. If safe programmatic judgement is impossible, use existing Take Over
or clarification.

Model requirements are independent of entitlement: [Phase 23 P23-05 divergence
recovery](phase-23.md#bounded-divergence-recovery-p23-05) is an Astra-required
task class, not a routine strategic-judgement fallback. That separate
policy may use separately authorized paid Astra when free Astra cannot
admit the required context or quota. It does not change this default, allow
implicit paid overage, or permit substitution with a lower model.

Prefer eligible Terra-class models in the 10M daily allowance as the main
execution capability for ordinary bounded review, repair and code generation
against the Task Specification. Use deterministic tools for mechanical work,
other capable models where justified, and agent CLIs for exploration or
session-dependent work. Advanced models remain execution candidates when
genuinely needed. These are capability preferences, not a fixed hierarchy.

AI-generated plans are proposals. Programmatic controls enforce authorization,
sharing, quota, scope, mandatory tests and Admission. Plans cannot weaken
mandatory checks or grant permissions. Retain validated reusable review
viewpoints and instruction templates with provenance, applicability conditions
and versioning; revalidate before reuse. Promote generalized knowledge to
deterministic rules only after review and verification.

Compare advanced-design plus Terra-execution with programmatic-design plus
Terra-execution and applicable direct review baselines. Measure end-to-end
quality, missed findings, retries, latency and total tokens including design
calls. Verify free-unavailable fallback, invalid plans, restored allowance
and unchanged mandatory validation. Treat model effectiveness as a hypothesis
until observed.

### P254-07 — Entitlement-independent architecture and economic evidence

Treat Task Design / Task Execution separation as independent of billing
or allowance. Use complimentary capacity to qualify the architecture first.
Keep the Phase 25.4 default free-only strategic judgement and programmatic
fallback; exhaustion alone never authorizes paid use. Preserve interfaces
so Phase 27 can select explicitly budget-authorized paid advanced design
with lower-cost execution when evidence supports its economic value.
Compare against direct advanced execution and programmatic design with
ordinary execution on comparable tasks and mandatory quality gates.
Measure total incremental cost per successfully accepted task, including
design, context preparation, execution, retries, validation, failed attempts
and amortized reusable design artifacts; report latency and quality separately.
Keep observed charges, allowance consumption, estimated prices and unknowns
distinct. Record conditions under which separation is beneficial or not.

## Ownership and boundaries

sm-workflow owns task semantics, context assessment, Workflow and Admission.
textus-ai owns provider capability descriptors, generic execution and quota
admission. Reuse Phase 9 convergence and Take Over semantics, Phase 25.2
metrics and Phase 25.3 allowance accounting. Do not duplicate Phase 26 server
work or Phase 27 generalized routing. Planning does not start a Session.
