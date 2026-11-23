# Phase 25: textus-ai Task API and Provider Expansion

Status: planned
Planned: 2026-11-18 (Asia/Tokyo)
Allocated: Phase 25 on 2026-11-19; unpublished API Phase 23 renamed
Scheduled: immediately after [Phase 28](phase-28.md), before [Phase 18](phase-18.md)
Checklist: [Phase 25 delivery ledger](phase-25-checklist.md)
Design input: [textus-ai Task API integration proposal](https://github.com/asami/sm-workflow/blob/main/docs/journal/2026/2026-11-18-textus-ai-task-api-integration.md)
Decision: [allocation record](../journal/2026/11/2026-11-18-api-credit-routing-development-order.md)

## Goal and responsibility

Extend the working Phase 24 route through a reusable textus-ai task boundary and
selected provider capabilities/quotas. sm-workflow declares development-task
requirements and retains Context Packaging, workspace actions, validation,
Admission, application Take Over and completion. textus-ai owns provider
adapters, credentials, provider formatting, model/capability selection, budget
policy, usage and bounded provider-level fallback, without GoalPhase internals.

The feature release provides ordinary sm-workflow routing that prefers OpenAI
for eligible work under the configured usage policy. Cover supplied-input
independent reviews and small compiler-error repairs first. Use the same
eligibility rules for other task kinds with a supported input and result
contract and an admitted application route. Where the host cannot establish
the applicable OpenAI allowance, the released route declines before dispatch
and follows the existing eligible fallback.

The proposed `infer` / `execute(AiTaskRequest)` names are illustrative, not
existing APIs or adopted implementation signatures. The textus-ai owner first
reconciles AiRunner, existing inference APIs and module boundaries and develops
only missing reusable capability. sm-workflow-dev開発 owns consumer integration;
shared CNCF changes, if needed, belong to CNCF開発 in canonical main. This plan
does not assign new producer work or grant another Component's change authority.

## Delivery order

| Step | Connected outcome |
| --- | --- |
| T1: task contract | Inventory Phase 24 and existing textus-ai/CNCF APIs; settle generic task kind, packaged input, structured result, capabilities/tools, reasoning, latency/budget and attributable outcomes through the ordinary review and repair entry points. Reuse the existing workflow engine and store. |
| T2: selected provider routing | Qualify OpenAI selection, transport and result handling, and qualify one additional provider through the same consumer contract. Connect the production usage-policy authority and verify that OpenAI declines before dispatch without a verified reservation. |
| T3: bounded fallback and adoption | Prove finite provider-level fallback and sm-workflow Take Over remain distinct, preserve uncertainties/usage/repair allowance, and verify one real OpenAI review and one real bounded compiler repair through normal validation and Admission. Complete producer handoffs, independent integrated review and feature release. |

Use a finite selected provider set. Before enabling a live provider, verify
programmatic access and billing for the deployment. Subscription and agent
quotas do not establish API credits.
`preferIncludedQuota` and `allowPaidOverage=false` express different requirements;
retain enforcement limits and never invent exact promotional balances. Routing
does not invoke an expensive AI classifier just to choose a provider.

Retain task/provider/model, issued/result/trace references, schema and context
provenance, observed vs estimated usage/cost, quota category when verified and
explicit finish/decline/failure/uncertainty. Provider-internal attempts are bounded
and attributable so application repair cannot accidentally multiply them.
Codex agent execution remains on its actual supported Continuation boundary;
do not disguise it as interchangeable stateless inference.

## Ordinary OpenAI use

- For reviews, send the selected diff, relevant specification and retained check
  results through the existing supplied-input route. Keep reviewer independence
  and admit the typed result in the same Session.
- For repair, run Phase 28's applicable deterministic actions first. Send a
  remaining small compiler error with its diagnostic, selected candidate and
  sufficient bounded source context. sm-workflow validates the patch's scope,
  applies it, recompiles or runs the selected checks, and admits the result.
- For each enabled route, prefer OpenAI when the package and typed result are
  supported and the verified quota and budget permit the request. Continue
  through the existing Codex agent route when a task requires broader context
  or tools, or when the API is ineligible or declines with a known outcome.
  Preserve any uncertain attempt for recovery under the existing continuation
  policy.
- For simple eligible tasks, prefer a configured model in the larger daily
  complimentary-token group, initially `gpt-5.6-terra`. Reserve the full input
  and enforced output bound before dispatch against the shared organization,
  model group and UTC day, including in-flight use. Reserve only up to the
  daily allocation minus the headroom configured by
  `textus.ai.bounded-task.openai-daily-margin-percent` (default 10%, range
  1–99%). Changing the property adjusts future admissions against the same
  ledger, including reservations already recorded that day. Adjust the margin
  from observed usage during operation. The headroom reduces the risk of billed
  usage from traffic outside the ledger. If such traffic still causes an
  overrun despite the margin, the operator accepts that residual cost. Check
  that the reservation covers the dispatch window at the UTC-day boundary.
  When the daily check
  conclusively declines before dispatch, consider the linked organization's
  monthly promotional credit only when a verified reservation covers the full
  potential charge. If neither reservation qualifies, use the existing agent
  route. Preserve an uncertain reservation or provider execution and prevent
  replay of the same request. Record the reservation basis and actual provider
  usage separately.
- Before release, verify both ordinary `sm-workflow` entry points, including
  the OpenAI binding through deterministic transport and allowance fixtures.
  Identify simulated evidence explicitly and verify pre-dispatch refusal
  without a qualifying allowance.
- Install and qualify the production `OpenAiTaskAllowanceAuthority` before
  release. Before enabling live OpenAI, verify the selected deployment's
  access, eligible usage and allowance enforcement.
- Run one real supplied-input independent review and one real bounded
  compiler-error repair through ordinary `sm-workflow` commands before
  release. Record actual provider selection, request identity, observed usage,
  validation and Admission for both.

## Qualification evidence and remaining gates

The [operational qualification](../notes/phase-25-operational-qualification.md)
records the two isolated real OpenAI cases admitted through ordinary routes,
the separate connected Ollama response and their limitations. T2 has a local
Step closure; T3 checks and review, full Phase validation and review, feature
release and formal local closure remain pending.

## Post-release operational verification

Further qualification from routine use, including wider workload coverage and
observations from ongoing use, follows the release. Perform that work in
parallel with development of the next scheduled phase, [Phase 18](phase-18.md).
Compare total tokens per admitted task across selected models, including
retries and rework, as part of that observation.

## Reuse and completion

Reuse Phase 24's delivered review package and connectivity evidence. Its
post-release operational checks remain Phase 24 work. Phase 25 uses Phase 28's
diagnostic and deterministic repair route. Phase 9
keeps broader convergence policy; Phases 13/20 keep systematic accounting.
Phase 10 CBD, completed Journal/Evidence Phases 11/12 and Journal 11.1/11.2
retain their scope and history; none is overwritten by this new Phase number.
Canonical Phase 22 runtime stabilization and Phase 23 strategic convergence
also remain unchanged. P25-01–08 map from the unpublished API P23-01–08;
they do not replace canonical strategic-convergence P23 conditions.

Close Phase 25 at feature release once T1–T3 and P25-01–11 pass normal
validation and independent integrated review. The production allowance
connection and the two live OpenAI checks are part of this boundary. Further
operational verification follows the release. Unsupported providers and
unknown quotas remain explicit and cannot authorize paid fallback.
