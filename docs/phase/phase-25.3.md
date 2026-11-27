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
- Select OpenAI for eligible bounded reviews and compiler repairs after the
  ordinary allowance check. Record why a request took a different route or
  made no provider call. Keep deterministic repair and the established Codex
  route for work that needs broader context.
- Cover the single-group path through an installed offline fixture before
  extending it to two groups. Retain existing authorization and recovery for
  uncertain effects and preserve free-only admission.

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
acceptance and qualify two live cases through the ordinary installed route
before Phase 25.3 closes. Use OpenAI for one small supplied-input review and
one bounded compiler repair. Retain the provider receipt and accepted review
for the first case, and the provider receipt, admitted patch and successful
recompile for the second. A fixture or alternate provider does not fulfill
either case. If OpenAI cannot be used, retain the blocking reason and leave
the affected case pending.

P253-04's two live cases are satisfied: the installed daily-only route admitted
an OpenAI `gpt-5.6-terra` supplied-input review (3,683 reported tokens) and
applied a bounded compiler repair (383 reported tokens); the repaired candidate
then compiled successfully. Both used daily group 2. The 1M group, independent
accounting and refusal scenarios were qualified offline. Provider-reported usage
is not settled billing. The retained qualification index is
`323-p253-04-genuine-openai-qualification.json`; Phase validation, independent
Phase review and closure remain pending.

Use the [Phase 25 qualification](../notes/phase-25-operational-qualification.md)
observations (3,509 review tokens and 415 repair tokens) as sizing examples,
not throughput guarantees. At verified 1M/10M limits and 10% margins,
the combined usable budget is 9.9M tokens per UTC day, subject to independent
group admission. Record measured results separately from estimates.

### P253-05 — Selection and completion metrics

Phase 25.3 acceptance requires the initiating chat and the structured
[Phase 25.2 metrics](phase-25.2.md#p252-03--completion-output-and-verification)
to report matching totals, selections and outcomes. Record decisions when the
ordinary runtime or caller makes them, including decisions that never dispatch.
Report the number of candidates, selections, exclusions, actual starts, terminal
successes and failures, and unknown outcomes. State the unit and coverage for
each row. Keep the following scopes distinct.

| Scope | Candidate total and selection evidence |
| --- | --- |
| AI routing | Count work items evaluated for routing by role, then show selections and exclusions for OpenAI daily, OpenAI promotional credit, Codex and any other configured route. Count actual invocations and retries separately from logical work items. |
| Automatic repair | Count diagnostics or candidates evaluated for each of Scalafix, Metals, Scalafmt and built-in repair. Show eligible, selected, applied, successfully revalidated and retained, and rolled-back applications. Keep diagnostic counts distinct from application counts. |
| Validation | Count planned or discovered checks and actual selections, starts and outcomes by Compile, ADMISSION, TestOnly, FULL and FixCheck. For test discovery, show discovered and selected test cases when retained. Label check counts and test-case counts separately. |

Report why work was excluded or selected without starting, including
ineligible input, a disabled route, missing binding, allowance refusal,
deterministic resolution and an earlier failing check. For OpenAI dispatch,
retain the actual model, allowance category or group, provider-reported tokens
and known outcome. Show verified billing separately when available.

Deduplicate retained decisions and actual executions by their existing
identities across resumes. A logical item may be considered for more than one
route, so the per-route totals need not add up to the number of distinct work
items. Show zero with complete evidence, a known subtotal with partial
coverage, and an explicit unknown when a count cannot be established. Keep
rows visible for unselected and unobserved routes. Verify the chat and
structured totals against the same records on the normal installed path.

Verify the existing automatic repair counts through the same installed
completion route. For Scalafix, exercise one supported edit that passes
revalidation and is retained in an accepted commit, and one completely
observed run with no applied edit. Show the corresponding counts of one and
zero. Keep check-only lint, rejected proposals and rollback out of the success
count. Show incomplete observation and its reason separately for Scalafix,
Metals and Scalafmt.

### Operational validation during Phase 25.4

At Phase 25.3 close, carry forward the installed version, effective route
configuration, offline and live qualification results, and any known
limitations. Continue wider observation during
[Phase 25.4](phase-25.4.md#operational-validation-of-phase-253), using eligible
reviews and bounded compiler repairs as they arise through the normal
installed route and Admission.
Record why work stays off the API and distinguish routing, eligibility and
quota from task classification, context preparation and genuine tool needs.
Carry concrete classification or context cases into Phase 25.4's planned work.

## Ownership and boundaries

textus-ai owns eligibility, quota admission, reservation and accounting.
sm-workflow consumes typed routing outcomes and supplies task context.
Reuse existing ledgers and interfaces rather than introduce a parallel
metering service. Shared-server and generalized cost-aware routing work
remain in their respective phases.
