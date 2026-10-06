# Phase 8: Concrete Local Provider Integration and Routing Validation

Status: planned
Planned: 2026-10-07
Depends on: Phase 6
Related: Phase 7 RepositorySync / Machine Placement experiments

## Goal

Validate the provider-neutral routing contract from Phase 6 with real local-LLM execution without making sm-workflow depend on a particular proxy, coding-agent harness, or model provider.

The primary path is Codex itself using a supported local/self-hosted model/provider path where practical. OpenCodex and OpenCode are optional experimental adapters/comparators, not architectural dependencies and not Phase 8 completion prerequisites.

## Priority

1. Establish Codex + local LLM as the first concrete local execution path.
2. Exercise bounded JUDGMENT, IMPLEMENTATION/TEST_FIX and independent REVIEW.
3. Exercise typed COMPLETED / DECLINED / BLOCKED outcomes and bounded escalation to a stronger provider.
4. Capture ExecutionEvidence required for routing KPI and Project x Provider/Machine profiling.
5. Compare optional OpenCodex/OpenCode paths only after the native/direct Codex path is understood.

## Provider independence

Phase 8 MUST preserve the Phase 6 boundary:

~~~text
WorkClassification
  -> ExecutionRequirement
  -> Provider Selection
  -> concrete adapter/harness
  -> ExecutionEvidence
~~~

No Codex, OpenCodex, OpenCode, Ollama, LM Studio, model name, machine model, or provider-specific reasoning option becomes Workflow transition semantics.

OpenCodex MUST NOT become a required runtime dependency. If evaluated, it is a replaceable adapter/proxy experiment. OpenCode is likewise an optional independent harness/provider adapter candidate.

## Driver scenarios

Use real development work with deterministic validation. Initial scenarios SHOULD include:

- bounded compile-diagnostic JUDGMENT;
- HYGIENE / TRIVIAL_COMPILE_FIX;
- SIMPLE_LOGIC;
- selected STANDARD_LOGIC work;
- TEST_FIX from deterministic compile/test evidence;
- independent local REVIEW;
- staged review where selected local PASS results are checked by a stronger independent reviewer;
- provider DECLINED followed by stronger-provider re-execution;
- BLOCKED entering typed decision/error handling rather than blind escalation.

## Decline / escalation validation

A local provider is not required to manufacture a completion. Phase 8 must prove that DECLINED is a normal routing outcome, preserves semantic WorkOrder identity/context/evidence, and causes bounded selection of the next admitted capable provider.

The experiment should distinguish capability/reasoning decline from context/resource/tool-environment limitations where practical.

## Evidence and routing metrics

Record at least:

- Project/work identity and semantic work type/classification;
- abstract ReasoningLevel / ExecutionRequirement;
- provider/profile and execution-context/machine identity where available;
- COMPLETED / DECLINED / BLOCKED and typed reason;
- compile/test/validation result;
- Review result and stronger-review disagreement when staged/sampled;
- Fix/retry/escalation path;
- elapsed time and available usage/cost/resource evidence.

Aggregate enough evidence to derive local completion, validated local completion, decline, escalation, post-local validation failure, and local-vs-strong Review disagreement rates.

Do not collapse the observations into a single opaque heaviness score. Preserve them for ProjectExecutionProfile and later corpus/experiment analysis.

## Machine drivers

MacBook Air M3/24GB and Mac mini/48GB are useful heterogeneous local drivers when available. Phase 8 does not require those exact machines. Machine identity/capability is evidence/policy, not semantics.

The Air-class driver is useful for testing whether bounded Judgment, ROUTINE/SIMPLE work, portions of STANDARD work, and first-stage Review are practical on a smaller local machine. The larger local machine can provide a comparison point before cloud escalation.

Repository-based migration between machines belongs to the coarser Machine Placement / RepositorySync concern. Phase 8 may collect comparable evidence but MUST NOT require fine-grained cross-machine migration for individual Fix/Review operations.

## OpenCodex experiment

After the Codex-direct/local path is characterized, optionally evaluate OpenCodex for:

- easier local/multi-provider switching;
- compatibility with Codex task/subagent execution;
- tool-call/streaming/reasoning fidelity;
- whether it changes decline/completion/validation behavior;
- operational complexity and failure modes.

Success of this experiment does not make OpenCodex mandatory. Failure does not block Phase 8.

## OpenCode experiment

OpenCode remains an optional alternative coding-agent/provider harness. Evaluate it only through the same logical ExecutionRequirement -> Provider Selection -> ExecutionEvidence contract so results are comparable with Codex-based execution.

## Completion

Phase 8 is complete when:

1. at least one real local LLM executes admitted semantic work through the Phase 6 provider-neutral contract;
2. local execution covers at least bounded Judgment, Implementation/Fix, and independent Review scenarios;
3. DECLINED -> stronger-provider escalation and BLOCKED -> typed handoff are demonstrated;
4. deterministic validation proves that COMPLETED is not acceptance authority;
5. routing evidence/KPIs can be aggregated by Project x Provider/Machine;
6. no optional OpenCodex/OpenCode dependency is required for the generic Workflow/routing contract;
7. optional adapter experiments, if performed, are recorded as comparative evidence rather than architecture.

## Non-goals

- Making OpenCodex mandatory.
- Making OpenCode mandatory.
- Encoding a named local/cloud model in Workflow semantics.
- Automatically learning or self-modifying routing policy.
- Building sophisticated Machine Placement/migration optimization.
- Claiming all STANDARD work can run locally.
