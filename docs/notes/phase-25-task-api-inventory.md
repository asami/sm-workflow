# Phase 25: task API inventory

This inventory is a source assessment for P25-T1. It proposes bounded producer
handoffs; it does not authorize dependency edits or establish an installed
artifact, provider availability, billing entitlement, or connected Phase 25
result. Names for a new task API are illustrative and must be reconciled with
its owners before implementation.

## Delivered evidence and consumer entry points

[Phase 24's local handoff](../phase/phase-24.md#local-handoff--2026-11-22)
records an OpenAI HTTP 200, typed response, request ID and usage through the
existing review SPI in a separate synthetic compiler-error fixture. Its
suggested correction compiled and passed three assertions. The formal local
release retains Q2 review and Q3 patch qualification as post-release work; it
does not establish actual Session Admission or ordinary patch application.
[Phase 9 routing](../spec/phase-9-provider-routing.md) records the package,
attempt and takeover boundaries; the earlier Phase 9 paired fixture used
Gemma, not OpenAI. These observations cannot substitute for P25-09/10.

| Ordinary entry point | Reuse and actual boundary | Remaining consumer work, owned by sm-workflow-dev |
| --- | --- | --- |
| Independent review | `scripts/goal-phase-dispatch.py` assembles `sm-workflow.supplied-review.v1` from the issued independent work, candidate, checks and explicitly selected files. `GoalPhaseApiReview.scala` validates the package against the current suspension, calls CNCF `AiRunner.generateRecord`, requests `software-analysis`, no tools and zero record retries, and maps the result to `GoalPhaseWorkResult`. Submit remains separate. | Make eligible normal dispatch select a policy-admitted task route and retain the producer receipt with the original candidate/checks; preserve independent context, explicit package completeness, limits and separate Admission. Current `runGoalPhaseApiReview` is an explicit operation, not proof of ordinary automatic selection. |
| Compiler repair | `GoalPhaseCompilePackage.scala` and `IncrementalSbtDiagnosisInformation.scala` prepare bounded diagnostic and whole candidate-source evidence for a current failed `RepairStep`; incomplete/oversized input requires an agent. `DeterministicRepair.scala` and `GoalPhaseNativeWorkContinuation.scala` retain deterministic transaction, revalidation and rollback/unknown state. | After applicable deterministic repair, connect a typed patch candidate to the ordinary repair route; scope-check, apply and validate it under runtime authority. Preserve original validation, candidate, remaining repair allowance and uncertain effects. Existing package preparation itself does not call a provider or grant write authority. |

## Reusable producer contracts and gaps

| Boundary and owner | Existing concrete source | Gap / bounded candidate handoff |
| --- | --- | --- |
| Generic port, CNCF開発 | `goldenport-cncf/src/main/scala/org/goldenport/cncf/spi/ai/runner/AiRunner.scala`: `AiRunner`, socket set, `AiRunnerRequirement`, `AiRecordRequest`/`AiRecordResponse`. A record request carries prompt, schema, token limit, purpose, provider/model hints, tools, execution class, trace policy and metadata; response has record, optional model and string metadata. | It is a useful provider-neutral record primitive, not a complete declared task/result lifecycle: package/schema identity, latency, typed outcome, attempt/uncertainty and typed usage/verified quota categories are not all represented by this port. First let textus-ai reconcile its public boundary against it. CNCF owner change is a candidate only if a shared port extension proves necessary; no Goal/Admission types belong in it. |
| Task and selection, textus-ai owner | `textus-ai-core/.../runtime/TextusAiRunner.scala`, `AiProfileConfig.scala`, `AiRuntimeBinding.scala` and `AiRuntimeProtocol.scala`: purpose/profile resolution, execution class, explicit provider bindings, configured limits and provider admission. `provider/openai/OpenAiRuntimeProvider.scala` supplies the existing OpenAI adapter; Google, Anthropic and Gemma sources also exist. | Reconcile a generic request with task kind, immutable package reference/provenance, output schema, required capabilities/tools, reasoning and latency/budget constraints; return typed completed/declined/failed/uncertain facts and attribution. Deterministic capability and verified quota/budget selection, including separate included-quota preference and paid-overage prohibition, is not demonstrated by these sources or Phase 24. A second provider must be selected and connected through the same contract; a source file or profile does not establish availability. Keep agent execution on its actual Continuation. |
| Record generation and fallback, textus-ai owner | `TextusAiRunner.generateRecord` resolves one effective provider and generates against the supplied schema. Its record retry limit defaults to 1 and is bounded 0–3; Phase 24 explicitly sets 0. `AiOperationalStrategy.maxProviderAttempts` bounds provider selection to one or two, not the number of calls. In the separate `generate` path, `TextusAiRunner._primary_candidate_` invokes the provider three times for CandidateRanking; `_run_` can invoke further bounded repairs, and a permitted fallback can add another invocation. | Do not assume the `generate` strategy applies to `generateRecord`: its implementation follows the separate single-selected-service record path. Establish a bounded, observable task/record attempt policy with explicit dispatch uncertainty and no blind replay; coordinate rather than multiply sm-workflow repair/Take Over allowance. Preserve provider result even when later interpretation fails. |
| Usage and policy, textus-ai owner | `AiCostAccounting.scala` provides rate schedules and cost admission/accounting; `AiProfileConfig.scala` resolves policy and validates token/cost limits. `GoalPhaseApiReview.providerResult` projects selected `ai.usage.*`, execution provider/profile, response IDs and bounded HTTP evidence. | Separate reported usage from estimates, actual from configured model/provider, and observed from unknown cost; retain issued/result/trace and each attributable attempt. Establish verified quota category and a synchronized no-paid-overage boundary where required. A subscription, cost estimate, provider source or configured max alone is not verified API credit or enforcement. Avoid routing-only model calls. |
| Application safety, sm-workflow-dev | `GoalPhaseApiReview.scala` checks independent issued work and bounded input; `GoalPhaseNativeWorkContinuation.scala` and native validation retain Session/repair state. | Provider output is a candidate only: sm-workflow owns workspaces, scope, checks, independent review, Admission and completion. Credentials do not grant shell, Git or commit authority. Known decline/ineligibility can follow existing agent continuation; potentially dispatched or unknown work needs recovery, not silent switching. |

The producer handoff should be a narrow request for the generic contract,
selection, task/record attempt semantics and typed usage facts described above,
with its owner determining which existing modules suffice and supplying its own
validation and delivery. If that reconciliation establishes a necessary CNCF
port change, it needs a separate bounded handoff to CNCF開発. These are
candidates, not issued requests or a claim that dependency repair is enabled.
The consumer must adapt only after attributable delivery and must not import
GoalPhase concepts into textus-ai.

## Qualification still required

The operator must establish available provider credentials and verified usage
policy, including concurrency and in-flight claims where relevant, before an
eligible call. Phase 24's [prerequisite inventory](phase-24-provider-prerequisites.md)
explains why a prepaid balance or per-call estimate alone does not prove a
no-overage boundary. P25 still requires a real ordinary OpenAI review admitted
in the same Session, a real post-deterministic compiler repair applied and
checked by sm-workflow, an additional connected capable provider under the same
contract, observable fallback/uncertainty, and independent integrated review.
No provider call, dependency mutation, validation or acceptance is asserted by
this inventory.
