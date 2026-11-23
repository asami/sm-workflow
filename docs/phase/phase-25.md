# Phase 25: textus-ai Task API and Provider Expansion

Status: planned
Planned: 2026-11-18 (Asia/Tokyo)
Allocated: Phase 25 on 2026-11-19; unpublished API Phase 23 renamed
Scheduled: immediately after [Phase 28](phase-28.md), followed by [Phase 28.1](phase-28.1.md)
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

At completion, ordinary sm-workflow commands prefer OpenAI API for eligible
work under the configured usage policy. Cover supplied-input independent
reviews and small compiler-error repairs first. Use the same eligibility rules
for other task kinds with a supported input and result contract and an admitted
application route.

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
| T2: selected provider routing | Prefer OpenAI for eligible work and qualify one additional available provider for a selected task. Resolve declared requirements against capabilities and verified quota/budget policy, with the same consumer result contract. |
| T3: bounded fallback and adoption | Prove finite provider-level fallback and sm-workflow Take Over remain distinct, preserve uncertainties/usage/repair allowance, and qualify normal result acceptance with producer handoffs, independent integrated review and local closure. |

Use a finite selected provider set, not every named vendor or a mandatory broad
benchmark. Verify each selected API's programmatic access and billing before
enablement; subscription/agent quotas are not assumed to be API credits.
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
- Verify one real review and one real compiler repair from ordinary
  `sm-workflow` commands with attributable provider and Admission evidence.
  Record eligible tasks, actual API selections and handoffs so usage is visible
  after rollout.

## Reuse and completion

Reuse Phase 24's delivered review package and connectivity evidence. Its
post-release operational checks remain Phase 24 work. P25-09 and P25-10
verify OpenAI selection and result acceptance in ordinary commands after
Phase 28, using its diagnostic and deterministic repair route. Phase 9
keeps broader convergence policy; Phases 13/20 keep systematic accounting.
Phase 10 CBD, completed Journal/Evidence Phases 11/12 and Journal 11.1/11.2
retain their scope and history; none is overwritten by this new Phase number.
Canonical Phase 22 runtime stabilization and Phase 23 strategic convergence
also remain unchanged. P25-01–08 map from the unpublished API P23-01–08;
they do not replace canonical strategic-convergence P23 conditions.

Completion requires T1–T3 and P25-01–11 through normal
validation/independent review/closure. Unsupported providers and unknown quotas
are explicit limits, not fabricated success or implicit paid fallback. Phase 25
does not delay Phase 24 adoption and this plan starts no Goal/Session or retry.
