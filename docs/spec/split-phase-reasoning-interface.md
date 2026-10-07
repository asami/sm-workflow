# SplitPhase reasoning interface

Status: declared interface implementation in progress; native/default runtime unproven.

## R1 — Distinct Workflow-owned reasoning boundary

SplitPhaseWorkflow declares its reasoning constituent, AssessReasoningDemand
Judgment, AdmitReasoningDemand Admission and required
`assess-reasoning-demand-capability`. The Required Operation is
SplitSemanticService.assessReasoningDemand with SplitPhaseReasoningSemanticRequest
and SplitPhaseReasoningSemanticResult. The Provided completion Operation is
SplitPhaseService.submitReasoningResult with SplitPhaseReasoningSubmissionResult
as its acknowledgment. Their converted descriptors originate from the actual
generated Workflow and producer, not participant-supplied operation choices.
The Judgment alternatives are Enriched and DecisionNeeded; they are semantic
data, not state-transition events or execution grants.

## R2 — Purpose-specific payload and assigned reply

The request preserves context, original source and authority, baseline demand
reference and the exact ordered unknown items. Every unknown item carries its
identity, reason and complete original closure. The request also carries the
exact result identity/revision selected by Workflow, not by the participant.
Preparation requires that explicit, nonblank assignment and offers no two-argument
default or automatic allocation. Missing assignment cannot construct a prepared request.
The common response must use that assigned reference; another identity or
revision is rejected for both alternatives. The semantic result carries context,
decision, rationale, ordered classifications with per-item rationale and evidence
references. Classification names are ExpensiveReasoning and SettledExecution.
AI cannot supply replacement planning/demand references, policies, actors,
commands, stages or a winner. The existing application codec's payload decodes
directly into generated raw value classes without alias or schema adaptation.
That representability is not semantic admission; current facts, coverage and
execution evidence remain the separate owning Workflow's responsibility.

## R3 — Versioned definition, no implicit saved-instance migration

The extended definition is `split-phase-reasoning-reply-v10`. Existing saved
`split-phase-human-terminal-v8` and `split-phase-reasoning-demand-v9` remain their original versions and are incompatible
with the current compiled catalog. Loading resources or observing a saved binding
must not silently migrate it or authorize a task under the new capability.
All25 entry/Human derivations retain their previous states and terminal dominance
with the reasoning Awaiting constituent present.
The assigned-reply request uses sm-workflow.split-phase-reasoning-request.v2;
request.v1 has no fallback or implicit assignment. Judgment result.v1 is unchanged.

## Evidence boundary

Generated declarations and raw payload compatibility do not establish installed
participant/handler, canonical native issue/completion, managed successor/recovery,
public/default routing, CLI/server/MCP equivalence or measured AI cost. Those are
required original PHASE-2 obligations and need their own executable evidence.
