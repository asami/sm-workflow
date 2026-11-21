# Phase 27: Multi-Provider Execution and Cost-Aware Routing

Status: planned
Planned: 2026-11-21 (Asia/Tokyo)
Depends on: Phase 26 dual-CLI practical interoperability
Related: [Phase 9](phase-9.md), [Phase 13](phase-13.md), [Phase 24](phase-24.md), [Phase 25](phase-25.md), [Phase 26](phase-26.md)
Checklist: [Phase 27 acceptance](phase-27-checklist.md)

## Goal

Extend the proven common sm-workflow Server and CLI client boundary from Phase 26 to practical selection among Codex, Claude Code, and optionally GitHub Copilot, with **separate execution route, provider/model and billing/allowance identity**. Choose suitable routes based on capability, availability, quality/convergence and verifiable cost constraints. Reuse existing provider and usage capabilities; do not create a parallel router or ledger.

## AI Capability Pool and surplus-capacity allocation

Treat all eligible AI execution routes as a **capability pool**, not a fixed Local -> API -> CLI hierarchy. After capability, quality, security, authorization and convergence constraints, choose the route with lowest **expected total incremental cost**, including retries, validation, latency constraints and the opportunity cost of consuming scarce allowance. Favor otherwise-expiring surplus capacity (e.g., eligible Copilot free allowance, subscription headroom or promotional API credits) when actually available, permitted for automation, and fit for the task; preserve scarce capacity for forecast demand. Never spend or exhaust allowances merely to use them.

Track per route: provider/model, execution interface, account and shared quota pool, remaining allowance and reset/expiry when observable, confidence/unknown status, expected demand, measured success/retry cost, and whether incremental paid billing is authorized. Do not invent balances or assume free Copilot automation/SDK entitlements. Unknown capacity cannot be treated as verified free. Do not silently use paid overage or violate provider terms. Use deterministic scheduling, not an AI call to decide routing.

## Execution and billing model

| Route | Execution boundary | Billing/allowance category |
| --- | --- | --- |
| Codex CLI | interactive/agent CLI via sm-workflow CLI | ChatGPT/Codex subscription allowance when eligible |
| Claude Code CLI | interactive/agent CLI via sm-workflow CLI | Claude subscription allowance when eligible |
| Copilot CLI (candidate) | interactive/agent CLI via sm-workflow CLI | Copilot plan allowance when eligible |
| OpenAI API | textus-ai provider API | OpenAI API credits or paid API billing |
| Anthropic API | textus-ai provider API | Anthropic API credits or paid API billing |
| Copilot SDK (candidate) | Copilot agent runtime via SDK | Copilot plan allowance or BYOK provider billing, according to actual auth configuration |
| Local LLM | local provider | local resource consumption |

SDK is **not** assumed to be an independent inference API or separate quota. Distinguish provider, model, execution surface, authenticated account, quota pool and actual payer. Multiple routes sharing one allowance must be counted against the same pool. Verify current plan terms, supported automation and billing before enabling a route; do not infer free API quotas from subscription access.

## Delivery slices

### R1 — Route identity and actual allowances
- Inventory Phase 26 CLI contracts and Phase 24/25 textus-ai provider/task interfaces, Phase 9 routing and Phase 13 usage accounting.
- Define a provider-neutral execution route descriptor (provider, model, CLI/API/SDK, account/quota pool, capabilities, workspace, authentication/billing class).
- Distinguish observed balances/usage from estimates and unknowns; do not invent a universal credit conversion.
- Explicitly disable paid overage/fallback by default, including BYOK and SDK modes.

### R2 — Practical additional route and cost policy
- Preserve Codex/Claude CLI operation from Phase 26.
- Qualify at least one additional real route from an available API, Copilot CLI or Copilot SDK using a bounded real task; choose based on actual accessible entitlement and supported execution.
- Apply a deterministic pool scheduler combining task suitability, quality, availability, shared quota eligibility, expiring surplus capacity, forecast demand, opportunity cost and bounded expected total cost; avoid an AI call solely for routing.
- Reuse textus-ai provider adapters and existing Phase 9 Take Over semantics. A CLI agent continuation is not interchangeable with a stateless API inference response.

### R3 — Evidence and fallback
- Measure attributable per-task route, observed token/credit usage (where provided), cost, latency, outcome and validation quality; record observed versus unknown allowance and whether surplus-capacity selection actually avoided incremental spend. Keep subscription allowance and billed dollars separate.
- Demonstrate safe refusal/decline or a finite switch at an authorized continuation boundary when a route is unavailable/limited; preserve Workspace ownership, Admission and repair limits.
- Verify that exhausted or unknown quota never silently causes paid execution; require explicit approval for paid routes.
- Record reproducible evidence and independent review, with unverified scenarios clearly identified.

## Responsibilities and boundaries

- sm-workflow: task classification, Workflow/Continuation/Admission, workspace ownership and application-level Take Over.
- textus-ai: generic API/SDK provider integration, credentials, model capabilities, quota/budget policy and provider-level bounded fallback, as already allocated in Phase 25.
- Phase 9: broader convergence/quality-based routing; Phase 13/20: systematic usage/phase accounting; Phase 24: initial practical API-credit path.
- textus-orchestrator: future cross-participant coordination where needed; avoid duplicating its orchestration.
- Development Center/Control Center: later UI projection, not Phase 27 acceptance.

## Non-goals

- Rebuilding Phase 26 server/CLI connection.
- Reimplementing Phase 25 provider SDK/API infrastructure.
- Unbounded automatic worker fleet, IoC multi-wait, leasing or autonomous dispatch; evaluate separately after practical routes work.
- Promising that Copilot CLI and SDK have independent free allowances.
- Provider price benchmarking without real measured inputs.

## Exit criteria

The same sm-workflow Server supports the Phase 26 Codex/Claude CLI path and at least one verified additional route (API, Copilot CLI or SDK), with explicit route/allowance identity, bounded selection, no unapproved paid overage, correct continuation/admission and actual evidence. Copilot integration is conditional on available entitlement and supported SDK/CLI functionality.

## Scheduling

Phase 26 is the functional prerequisite. Do not alter the previously selected stabilization → Phase 7 → Phase 24 → Phase 25 priority merely by recording Phase 27. Decide its implementation slot against actual accepted dependencies. Planning does not start a Workflow Session or grant changes in producer repositories.

## Scheduling decision — 2026-11-22

Adopted order: Phase 24 -> 28 -> 18 -> 23 -> 26 -> **27** -> 22 -> 17 -> 21 -> 15 -> 14. Phase 27 follows actual Phase 26 common Server/multi-CLI interoperability, not a speculative early Phase 27.1 split. Phase 25 remains deferred; qualify concrete required API/provider prerequisites without assuming its completion. Preserve existing scope, evidence and no-unapproved-paid-overage policy. No Session starts.

## Adopted prerequisite update — 2026-11-22

Phase 27 follows **Phase 25 -> Phase 26** after Phase 23. Use the verified generic task/provider interface and common Server/multi-CLI route as available, while preserving distinct execution and billing/allowance identities. The adopted full order is 24 -> 28 -> 18 -> 23 -> 25 -> 26 -> 27 -> 22 -> 17 -> 21 -> 15 -> 14. This supersedes older scheduling-only notes; no Session starts.
