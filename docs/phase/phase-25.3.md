# Phase 25.3: Dual OpenAI Daily Allowance Utilization

Status: planned; execution not started
Planned: 2026-11-25 (Asia/Tokyo)
Scheduled: immediately after [Phase 28.2](phase-28.2.md), followed by [Phase 25.4](phase-25.4.md).
Depends on: [Phase 25.1](phase-25.1.md) reservation and settlement
Checklist: [Phase 25.3 acceptance](phase-25.3-checklist.md)

## Goal

Use both independent OpenAI complimentary daily model-group allowances
(1 million and 10 million tokens where verified for the organization)
through ordinary sm-workflow Task API routes. First make the existing single
daily allowance usable in routine review and compiler repair. Preserve
free-only admission and use the available AI capability pool efficiently.

## Delivery

### Ordinary OpenAI routing and operational repair

Make a small supplied-input review and a bounded compiler repair select an
eligible OpenAI route through the ordinary installed invocation. Reuse the
[Phase 25 producer connection and live qualification](../notes/phase-25-operational-qualification.md)
as a starting point. Include the wiring or implementation fixes required for
this outcome in Phase 25.3.

- Verify the effective caller policy enables bounded API review and the
  ordinary runtime has the Textus AI binding, approved connection and verified
  allowance admission. Use the existing host configuration path and keep
  operator-owned `.textus/config.json` under operator control.
- Exercise a review package within the existing bound. Keep the established
  Codex route for a package that exceeds the bound or requires broader context.
  Retain distinguishable outcomes for a disabled policy, unavailable binding,
  unsuitable package, and pre-dispatch allowance refusal. Record the selected
  route and actual receipt in the existing Phase 25.2 metrics.
- Cover the single-group path through an installed offline fixture before
  extending it to two groups. Hand off the qualified installed route to
  Phase 25.4 for live observation during ordinary review and compiler repair.
  Retain existing authorization and recovery for uncertain effects and
  exclude implicit paid fallback.

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
Verify that neither group can borrow from the other. Record installed offline
acceptance and the deployed configuration requirements for Phase 25.4. The
operational validation of actual provider selection, usage and billing occurs
during Phase 25.4 development under the existing authorization.
Use the [Phase 25 qualification](../notes/phase-25-operational-qualification.md)
observations (3,509 review tokens and 415 repair tokens) as sizing examples,
not throughput guarantees. At verified 1M/10M limits and 10% margins,
the combined usable budget is 9.9M tokens per UTC day, subject to independent
group admission. Record measured results separately from estimates.

### Operational validation during Phase 25.4

At Phase 25.3 close, carry forward the installed version, effective route
configuration, check results and any known limitations. Close the implementation
and offline qualification before starting
[Phase 25.4](phase-25.4.md#operational-validation-of-phase-253), and retain the
operational follow-up as pending until there is real evidence. Observe eligible
reviews and bounded compiler repairs as they arise during Phase 25.4 through
the normal installed route and Admission. Use existing Phase 25.2 metrics to
record model, provider, allowance group and reported usage when available.
Record why work stays off the API and distinguish routing, eligibility and
quota from task classification, context preparation and genuine tool needs.
Carry concrete classification or context cases into Phase 25.4's planned work.

## Ownership and boundaries

textus-ai owns eligibility, quota admission, reservation and accounting.
sm-workflow consumes typed routing outcomes and supplies task context.
Reuse existing ledgers and interfaces rather than introduce a parallel
metering service. Shared-server and generalized cost-aware routing work
remain in their respective phases.
