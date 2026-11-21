# Phase 24: Context Packaging and Practical API-Credit Use

Status: planned
Planned: 2026-11-18 (Asia/Tokyo)
Allocated: Phase 24 on 2026-11-19; unpublished API Phase 22 renamed without accepting any condition
Owner: sm-workflow-dev開発 (consumer development)
Checklist: [Phase 24 delivery ledger](phase-24-checklist.md)
Successor: [Phase 25](phase-25.md)
Decision: [development order and allocation](../journal/2026/11/2026-11-18-api-credit-routing-development-order.md)

## Goal

Make one normal software-development route practically usable with eligible
OpenAI API credits to reduce Codex consumption. Deterministically package
sufficient bounded context, invoke the existing textus-ai OpenAI adapter, and
accept the result through the ordinary workflow. Unsupported work or a known
decline/budget stop returns to a capable configured agent with retained work.
First deliver a supplied-input independent review, then a bounded patch task
with runtime-owned application and checks. Make the review route usable as soon
as its connected acceptance passes; do not wait for generic Task API expansion.

Design inputs:

- [Context Packaging and cost-aware routing](https://github.com/asami/sm-workflow/blob/main/docs/journal/2026/2026-11-18-context-packaging-provider-routing.md)
- [textus-ai Task API integration](https://github.com/asami/sm-workflow/blob/main/docs/journal/2026/2026-11-18-textus-ai-task-api-integration.md)

## Prerequisites and development schedule

The adopted order is **current stabilization -> remaining Phase 7 -> necessary
producer/setup work below -> Phase 24 -> Phase 25 expansion**. These are
dependency-ordered development units, not invented calendar estimates or new
replacement Goals. During prerequisite assessment, inspect the actual delivered
version and retained evidence; a planned document or source symbol alone is not
connected acceptance. Ready capabilities are consumed without reimplementation.

| Order / unit | Required outcome and owner | Current evidence and scheduling rule |
| --- | --- | --- |
| 1: current stabilization | Consume the stabilization delivery and its normally installed control runtime through the existing owner. | Phase 8 closed on 2026-11-19 as a Human-approved FORMAL release with additional validation/review NOT RUN — USER-APPROVED. Preserve its actual Session/history and remaining assurance in canonical Phase 22; native reconciliation is not a retrospective release gate. |
| 2: remaining Phase 7 | Finish the existing managed-development Phase in sm-workflow-dev開発, retaining accepted work/history/counters. | Phase 7 remains unfinished; this plan does not restart or operate it. |
| PRE-1: connection inventory | sm-workflow-dev開発 identifies the concrete caller -> runtime dispatcher -> textus-ai SPI/adapter -> typed result -> Admission path and existing agent Continuation route. | Assess after 7, before consumer implementation. Record exact reusable contracts and missing capabilities; no second execution engine. |
| PRE-2: minimum CNCF producer work, if missing | CNCF開発 qualifies only the required generic API/agent dispatch, context-independence and one-shot lifecycle capabilities from CNCF Phase 80 P80-A/B/C. | Phase 80's selected interface slice is planned, not proven delivered by that status. Reconcile actual code/evidence first; develop only demonstrated gaps in canonical goldenport-cncf/main. Full Phase 80 extension coverage is not required. |
| PRE-3: minimum textus-ai adapter readiness, if missing | The textus-ai owner confirms or repairs existing AiRunner/OpenAI request/result handling, configured limits and usage/cost attribution needed for the selected bounded calls. | Local textus-ai-core has OpenAI adapters, AiRunner, token metadata and cost calculation; installed consumer readiness is unverified. Reuse them; do not require Phase 25's generic task API. PRE-2/3 may proceed independently after PRE-1 when their contracts permit. |
| PRE-4: usable API configuration | Confirm eligible organization/project access, operator-managed credentials, enabled credit benefit and enforceable no-paid-overage configuration for the selected route. Owner: operator with runtime configuration support. | Account activation, available credits and end-to-end enforcement are unverified. Never request secrets in chat or label unknown credit as free; if enforcement cannot be established, keep API execution disabled with a precise setup gap. |
| 3: Phase 24 consumer delivery | Implement Q1 -> Q2 -> Q3 below using the qualified producer interfaces and normal installed control version. | Required producer changes receive their ordinary owner validation/review and necessary publication/delivery; only changed connections need consumer qualification. |
| 4: Phase 25 | Expand the usable route with a generic task contract and selected provider policies. | Separately planned; no Phase 25 gate on Phase 24 completion. |

The [P24-PRE connection and readiness inventory](../notes/phase-24-provider-prerequisites.md)
records the applicable Phase 9 evidence, named owner gaps, and the unverified
access and no-paid-overage gate. It does not satisfy or replace the remaining
Phase 7 predecessor.

PRE-2/3 are conditional development work, not assumed completed prerequisites.
Any required dependency change uses its normal owner and applicable approval
boundary; this planning record does not dispatch another chat or grant execution,
publication or shared-installation authority. Do not insert whole Phases 8.1, 9,
10, 13, 20, 22 or 23 ahead of Phase 24 merely because related work exists there.

The latest user-selected consumer lane takes priority over earlier schedules for
this chat: stabilization -> Phase 7 -> the concrete prerequisites -> Phase 24.
Existing Phase 22 is runtime stabilization, and existing Phase 23 is strategic
convergence; both keep their own plans, condition IDs and history. This API plan
formerly used unpublished number 22. Its P24 IDs map one-to-one from those
unaccepted local P22 IDs; they do not replace canonical stabilization P22 IDs.

## Phase boundary and reusable foundations

| Work | Ownership / evidence treatment |
| --- | --- |
| Existing Phase 9 R1/R2 invocation, result acceptance and retained-work handoff | Reuse actual supported contracts/evidence. A shared delivery can support each applicable ledger through references; no duplicate implementation or identical live call for numbering alone. Phase 9's broader convergence/policy scenarios remain there. |
| Deterministic bounded context assembly and practical OpenAI-credit route | Phase 24 consumer scope. Task requirements and allowed workspace actions remain sm-workflow-owned. |
| Provider transport, credentials, formatting and existing cost policy | Existing textus-ai APIs; extend only the necessary producer gap. No direct OpenAI client duplicated in sm-workflow. |
| Generic AiTaskRequest/Result, capability catalog and selected multi-provider/quota selection | Phase 25; not a Phase 24 prerequisite. |
| Systematic parent/child usage and Phase-total accounting | Existing Phases 13/20. Phase 24 records available per-call facts without waiting for their full delivery. |
| CBD integration and Journal/Evidence foundations | Existing Phases 10–12 keep their identities, scope and accepted history. |

## Main-route delivery

| Step | Connected delivery | Exit evidence |
| --- | --- | --- |
| Q1: usable API review | Existing diff/specification/check context -> deterministic package -> configured textus-ai/OpenAI call -> typed independent review -> ordinary Admission. | One real bounded review is accepted through the normal route with actual provider/model/request/result and observed usage. Setup and usage instructions permit repeated ordinary use without hand-built requests. |
| Q2: bounded patch and agent handoff | Same packaging contract -> API patch candidate -> runtime path/scope checks, workspace application and required focused checks; known decline, unsupported context/tools or exhausted bounded attempts -> existing agent assignment. | A small actual code task reaches normal acceptance; focused scenarios prove retained candidate/history/remaining allowance and no blind replay of uncertain execution. API inference alone grants no repository or command authority. |
| Q3: practical adoption and closure | Qualify the connected route and no-overage behavior, independent integrated review, normal local closure and operator handoff. | Supported configuration, actual accepted outcomes, API token/cost observations, packaging overhead and limitations are recorded. Savings are reported only where measured; no mandatory broad model benchmark or fixed savings percentage. |

## Minimum contract carried forward from the extension proposal

- Build packages from existing structured inputs, diagnostics and explicitly
  relevant files, with stable ordering, provenance/revision, included/excluded
  resources, byte/token estimates and a conservative completeness status.
  Several files may fit; file count alone does not establish complexity.
- Bound construction cost and cache/reuse where useful. Normal construction
  makes no AI call. Unknown/incomplete context, incompatible tools or excessive
  input yields an explicit decline/handoff. The proposal's 64 KiB/1 MiB values
  are optional starting policies, configurable through ordinary properties and
  checked against actual model/output limits, never API-wide hard limits.
- Keep the consumer request/result boundary provider-neutral using the existing
  textus-ai contract. Configure model/effort and the selected profile; do not
  implement a generic Task API or an AI routing classifier merely for Q1.
- Enforce the selected no-paid-overage policy before enabling the API route.
  An estimated per-call cap alone does not prove remaining promotional credit.
  Reuse verified provider/configuration controls; if unavailable, report the
  concrete prerequisite instead of silently spending purchased credits or
  building a speculative billing platform. Include concurrent/in-flight work
  where relevant to the enforced budget; unknown outcomes/usage remain unknown.
- Retain actual API/provider/model and issued identity, token usage, configured
  cost basis, observed/estimated cost distinction, latency, decline/failure and
  packaging overhead through existing evidence. Do not infer subscription-dollar
  equivalence or fabricate Codex usage savings.
- Keep retries finite through existing execution/repair owners. textus-ai
  internal fallback must be configured and observable; it cannot silently
  duplicate sm-workflow repair cycles or refund shared allowance. Lost responses
  retain uncertainty and use normal result recovery, not automatic Take Over.
- Review independence, Candidate–Admission, validation, Git/SBT, commit and
  publication remain with existing owners and actual authorization boundaries.

## Completion and exclusions

Completion requires Q1–Q3's bounded practical route and
[P24-01–11](phase-24-checklist.md), with actual integration, necessary checks,
independent review and normal local closure. Claim only the qualified capabilities.
Provider breadth, generic task services, auto-learning routing, Local LLM setup,
all-provider comparisons and a new budget/telemetry platform are excluded.
This plan starts no Goal/Session and preserves existing accepted results,
UNKNOWN/failed histories, worktrees, repair counts and successful validation.
