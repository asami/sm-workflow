# Phase 25.1: OpenAI Daily Allowance Reservation and Settlement

Status: planned
Planned: 2026-11-24 (Asia/Tokyo)
Scheduled: immediately after [Phase 28.1](phase-28.1.md), followed by [Phase 25.2](phase-25.2.md)
Depends on: [Phase 25](phase-25.md) OpenAI Task API and allowance ledger
Checklist: [Phase 25.1 acceptance](phase-25.1-checklist.md)

## Goal

Make the daily OpenAI allowance practical for source-code review and bounded
bug fixes through the ordinary sm-workflow routes. Reserve only the tokens a
request can consume, then return the unused portion when provider-reported
usage is verified. The released Phase 25 implementation reserves the fixed
1,050,000-token input limit even for short requests. Use its operational
qualification record (`docs/notes/phase-25-operational-qualification.md` in
the development checkout) as an initial observation when sizing and validating
the improvement.

## Delivery

### P251-01 — Size the request by task

Derive a conservative input bound from the exact request to be sent, including
its instructions, schema and message overhead. Review inputs include the diff,
relevant source and check evidence. Repair inputs include the diagnostic,
selected source and patch contract. Configure separate completion-token limits
for review and bounded bug fixes and enforce the selected limit at dispatch.
Calibrate against representative task sizes, including reasoning-token usage,
truncation and retries. Verify the counting method for eligible models and
decline the daily route when no safe bound is available.

### P251-02 — Return unused tokens safely

Reserve the input bound plus the enforced completion-token limit in the existing
organization, model-group and UTC-day ledger. Keep that reservation while the
request is in flight or its usage is uncertain. Settle authoritative usage tied
to the exact dispatched request once and return only the unused portion to that
day's allowance. Settlement is independent of whether sm-workflow accepts the
review or patch. Preserve the request claim, original reservation and history,
including reservations from before the update. Concurrent admissions include
all outstanding reservations and settled usage.

### P251-03 — Verify whether local reservations can be omitted

A provider-side mechanism can replace the local reservation only if it is
verified to refuse requests that would incur overage
for the selected organization, model group and UTC day, including under
concurrency. Keep outcome and usage records in that case. The
[OpenAI complimentary-token policy](https://help.openai.com/en/articles/10306912-sharing-feedback-evaluation-and-fine-tuning-data-and-api-inputs-and-outputs-with-openai)
can charge the whole request that crosses the daily limit. Usage reporting
alone does not qualify as free-only admission. Deliver request-sized reservation
and settlement when no such admission mechanism is verified.

### P251-04 — Qualify the ordinary Task API routes

Use offline provider fixtures for review, bug repair, concurrent requests,
restart and uncertain usage. Verify eligibility, refusal before dispatch and
the separate promotional-credit policy. Record the scoped validation and
independent review before Phase closure.

## Ownership and boundaries

textus-ai owns provider sizing, quota admission and accounting. sm-workflow
supplies the review or repair context and consumes the typed outcome through
the existing routes. Reuse Phase 25's ledger, eligible-model restrictions,
configured daily margin and separate verified promotional-credit policy.
Exhausted or unknown daily allowance grants no permission to use a billed model
or paid overage. [Phase 27](phase-27.md) consumes this capability for
cost-aware routing.
