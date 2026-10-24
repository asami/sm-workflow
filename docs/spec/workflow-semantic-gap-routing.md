# Workflow-specific semantic gaps and AI invocation reduction

date=2026-10-21
status=adopted-design-not-implemented
phase=[Phase 6.5](../phase/phase-6.5.md)

## Common principle and ownership

Compute with existing facts first. Continue without AI when the workflow's
declared acceptance conditions are satisfied; issue semantic work only for the
remaining identified uncertainty. Reuse the [GoalPhase assessment contract](goal-phase-step-assessment.md).
This is a shared design principle, not a new generic gap engine, workflow,
snapshot system or ledger. Each workflow retains its typed inputs, admission,
authority, durable progression and effect ownership.

| Workflow | Mechanical owner | Semantic residual | Completion |
| --- | --- | --- | --- |
| GoalPhase | Selected checks, signals and Step admission | Unsettled implementation/business meaning | Accepted Step and native commit |
| RepositorySync | Git integration, selected checks, merge admission and convergence | Unsettled interaction between integrated changes | Accepted actual merge candidate, native commit and authorized convergence |
| SplitPhase | Calibration, enumeration, ranking, projection and proposal validation | Explicit unknown estimates, boundaries, scope or reasoning classifications | Admitted enrichment and deterministically selected proposal |

Missing required evidence is an execution/information gap, not semantic CLEAN.
Known in-boundary failures enter existing FIX; uncertain effects use observation,
not blind retry. Preserve applicable earlier results and actual failure history.

## RepositorySync: establish clean merge admission first

Keep equal/local-ahead/remote-ahead routes and their selected validation policy.
The new route is the existing conflict-free pending merge -> actual selected
integration checks -> applicable Scala/CAR lint -> deterministic assessment ->
formal merge admission -> native merge commit -> authorized push/convergence.
Non-Scala/non-CAR scopes explicitly record non-applicability, not invented PASS.
An explicit applicable validation policy is still required.

Add a first-class merge admission outcome and connect its actual callers through
commit/convergence. Do not forge ReviewAccepted or use the non-merge
IntegrationValidated shortcut to bypass merge commit. The accepted result names
the actual pending candidate, selected policy/results and review disposition.
Reuse existing checks of parent identities, staged tree and effect authority at
their owning boundaries; do not create duplicate certificates. Skipping review
does not grant commit/push authority or permit force-push.

Start with mechanically available signals: overlapping changes by both parents,
declared public-contract/component/dependency changes, attributable lint results
and existing metadata. Compare the merge interaction with base/local/remote and
previously accepted judgments. Large upstream changes or old findings alone do
not automatically require review or cleanup. Unavailable analysis is not a zero
signal. Do not build a whole-program semantic scanner or rerun all parent builds
solely to manufacture a baseline.

Routing after selected checks:

- Conditions satisfied, no unresolved signal or mandatory review: admit with
  zero AI triage/review/waiver calls.
- Residual merge uncertainty: bounded independent triage; CLEAN returns to
  admission, SUSPICIOUS/semantic DECLINE goes to scoped Detailed Review.
- Explicit required/high-risk review: directly dispatch Detailed Review.
- AI-generated conflict resolution: require independent Detailed Review in 6.5.
  Human resolution keeps its explicit admission policy; do not infer acceptance.
- Known failed test/lint: bounded FIX directly, without manufacturing a review
  finding to unlock the current review-origin repair route. Then affected checks
  and assessment of the actual repaired candidate.

Connect failure-origin repair to the existing bounded materialization/staging
and continuation mechanisms. Preserve the original integration and repair counts.
Review-driven repair carries findings, repair delta and affected meaning; reuse
unaffected reviews and results rather than start another whole-merge review.
Every new AI-authored conflict resolution still satisfies its required independent
review; a previous candidate's acceptance is not automatically current acceptance.

## SplitPhase: retain the deterministic engine and reuse first

The existing SemanticRequired/typed scope/reasoning paths remain authoritative.
Reuse known/calibrated estimates and admitted answers. AI cannot overwrite known
inputs, choose the final partition, change authority or replace deterministic
enumeration/ranking. A complete-input route must progress without semantic AI.

First reduce repeated context loading and reuse saved answers at their actual
admitted source/scope. Repeated Advance, observation and process reopen must not
reissue completed semantic work or recalculate an already retained frontier.
Changed relevant inputs can create a new bounded gap; unchanged known facts do
not need re-estimation or another admission merely to read them.

Only then bundle independent unknowns already identified at the same semantic
frontier, if actual duplication justifies it. Partition, scope and reasoning gaps
can arise sequentially after earlier results; do not speculate on later gaps or
merge different authority/Human-decision/lifecycle boundaries. Absence of safe
bundling is acceptable. Do not require a new generic SemanticGapBundle API.

Ordinary admitted semantic enrichment has no unconditional second AI review.
Schema, coverage, preservation of known inputs and partition constraints provide
mechanical assurance within those propositions. They do not prove that a novel
estimate or semantic boundary is factually correct. Retain assumptions, rationale
and evidence; route only actual contradictions or remaining semantic uncertainty.

The existing Enriched/DecisionNeeded contracts and their Human continuations must
not be silently reinterpreted. Distinguish these reasons at the owning boundary:

| Reason | Handling |
| --- | --- |
| Bounded reasoning exceeds the selected provider's capability | Use an existing supported provider handoff, if available; otherwise retain the established capable profile/path |
| Required information is absent | Obtain the missing information through the existing information/decision route |
| Policy/authority choice belongs to a human | Existing Human decision; stronger AI cannot grant authority |
| Tool/transport failure or unknown execution | Existing execution failure/observation, not semantic DECLINE or success |

Do not add a confidence-scoring system, fabricate a new native result, erase a
retained DecisionNeeded or build Phase 9's routing merely to enable a cheap model.

### Connected Split observation and bounded reuse

The normal Split Advance observes its actual retained native source after business
progression. When the existing common Journal is configured, it returns optional
`journalRecording` for a `SplitRouting` entry in that same store. Public Journal
read/find can select it by repository, `SplitPhase`, Session and full source tuple.
The entry identifies the actual branch, selected operation and retained assignment;
it does not claim that a provider executed. Repeated pending/terminal observations
return the same immutable entry. Selected Preview observation reads its retained
publication and exact terminal successor; a known prepared Preview can finish its
one pending terminal append. Completed APPLY observation requires its actual applied
effect receipt and exact terminal successor, and never prepares/applies files again.
Unfinished or unknown application remains unavailable for its owning recovery route. Disabled policy adds no storage read. Recording
failure reports its own status without replaying progression or a semantic answer.

`Phase65SplitReuseSpec` uses production public Operations and normal SQLite
reopen to cover known-input selection without work issuance, pending assignment
reuse, exact unknown-only answer coverage, retained answer to ordinary Preview
admission, repeated terminal observation, common Journal lookup, and distinct
DecisionNeeded/partial-enrichment/missing-handler outcomes. Its Start consent and
semantic answers belong to the fixture; installed CLI/provider qualification is
separate Q4 work. The fixed partition engine and existing scope/reasoning guards
remain authoritative. No generic bundle, new mandatory review or provider-routing
engine is introduced.

## Semantic worker context and records

Keep host command, lifecycle, effects and routing controls enforced by their
existing owners. Give a semantic worker its issued purpose, exact bounded source
context, unresolved facts/signals, relevant results and typed reply contract.
Do not remove necessary host controls simply to shorten the entire skill file.
Avoid repeated full-history/log loading, duplicate AI interpretation and AI tasks
whose only purpose is recording a decision.

Use common Journal/Evidence for actual branch reasons, referenced inputs/results,
executed/skipped semantic actions and repair/provider outcomes when observed.
Recording failure does not replay business effects or semantic work. No new
metrics subsystem or second ledger is required.

## Model policy and acceptance scope

LLM evaluation, benchmark corpora, model comparisons, Local LLM installation and
performance/accuracy scoring are outside Phase 6.5. Future model comparison and
enhanced provider routing belong with Phase 9, not a 6.5 prerequisite; Local LLM
evaluation remains optional reference work, not a mandatory Phase 9 gate.

Use Luna for bounded semantic work when the existing supported configuration and
ordinary task acceptance permit it. Do not pin all semantic roles to Luna. If it
cannot satisfy the contract, use the existing adequate profile and record the
outcome through normal workflow evidence. No repeated model experiment or new
routing mechanism is required. Phase 6.5 can pass without any successful Luna
conversion; it cannot pass without its required workflow behavior. AI invocation
elimination remains the primary cost reduction.

Deliver in order: GoalPhase main route/FIX/conditional actions; RepositorySync
clean merge then repair/escalation; SplitPhase known-input and retained-answer
reuse then bounded unknown/context improvements; installed integrated acceptance.
Reuse existing implementation and applicable tests. Verify only changed branches
and missing connections, plus the selected integrated acceptance. Do not reset
live Sessions, Goals, accepted Steps, counters or existing validation history.
