# Phase 25.3: Dual OpenAI Daily Allowance Utilization

Status: planned; execution not started
Planned: 2026-11-25 (Asia/Tokyo)
Scheduled: after [Phase 18](phase-18.md), followed by [Phase 25.4](phase-25.4.md)
Depends on: [Phase 25.1](phase-25.1.md) reservation and settlement
Checklist: [Phase 25.3 acceptance](phase-25.3-checklist.md)

## Goal

Use both independent OpenAI complimentary daily model-group allowances
(1 million and 10 million tokens where verified for the organization)
through ordinary sm-workflow Task API routes. Preserve free-only admission
and use the available AI capability pool efficiently.

## Delivery

### P253-01 — Verify entitlement and model-group eligibility

Represent both groups independently using verified organization entitlements
and exact provider model identifiers. Treat 1M and 10M as configured,
verified limits, not universal entitlements. Verify project data-sharing
eligibility and current provider policy. Unknown or ineligible models or
projects must not enter the complimentary route. Do not infer eligibility
from model display names or ChatGPT subscriptions.

### P253-02 — Independent reservation and settlement

Reuse Phase 25.1 request-sized reservation and settlement with independent
organization/model-group/UTC-day balances and configurable safety margins
(default 10%). Admit concurrent requests atomically within their own group.
Never transfer unused allowance between groups. Preserve outstanding claims,
idempotent settlement, uncertain usage and original UTC-day attribution.
Count reasoning tokens according to the provider usage contract without
double counting. Each dispatched retry requires its own reservation.
Coordinate all participating clients; account conservatively for external
usage and refuse when safe admission cannot be established.

### P253-03 — Use both groups through ordinary task routing

Select eligible models by task capability, quality requirements, available
allowance and cost. Prefer the 10M group for routine review and bounded
repair when suitable; use the 1M group for higher-capability tasks or
eligible alternatives when appropriate. These are preferences, not a fixed
model hierarchy. Permit both groups to operate concurrently. On exhaustion,
consider the other group only if its model satisfies the task contract and
passes independent admission. No implicit paid overage or promotional-credit
fallback; preserve separately authorized policies. Expose typed refusal and
route attribution for Phase 25.2 metrics and Phase 27 cost-aware routing.

### P253-04 — Qualify and observe

Use offline fixtures for both groups, concurrent admission, exhaustion of
either group, independent margins, retries, restart, uncertain usage,
duplicate settlement, eligibility changes and UTC rollover (09:00 JST).
Verify that neither group can borrow from the other. Qualify ordinary
review and repair routes with explicitly authorized bounded live tests
where available; distinguish reported usage from verified billing.
Use the [Phase 25 qualification](../notes/phase-25-operational-qualification.md)
observations (3,509 review tokens and 415 repair tokens) as sizing examples,
not throughput guarantees. At verified 1M/10M limits and 10% margins,
the combined usable budget is 9.9M tokens per UTC day, subject to independent
group admission. Record measured results separately from estimates.

## Ownership and boundaries

textus-ai owns eligibility, quota admission, reservation and accounting.
sm-workflow consumes typed routing outcomes and supplies task context.
Reuse existing ledgers and interfaces rather than introduce a parallel
metering service. Shared-server and generalized cost-aware routing work
remain in their respective phases.
