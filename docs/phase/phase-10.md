# Phase 10: CBD Review Integration

status=planned
Planned: 2026-10-18
Owner: sm-workflow
Prerequisites: accepted initial-use workflow boundary from Phase 6.3 and the
required bounded CBD15 / CBD15.1 producer handoffs.
Checklist: [Phase 10](phase-10-checklist.md)
Contract: [Scoped quality admission](../spec/goal-phase-scoped-quality-admission.md)
Decision/handoff: [Production-first allocation](../journal/2026/10/2026-10-18-production-first-cbd-review-phase-10.md)

## Purpose and scheduling

Add CBD-backed scoped quality review to the working sm-workflow lifecycle after
initial production adoption. Initial use and cost reduction take priority over
this integration. Phase 10 is separately scheduled after 6.3; its number does
not require completion of 6.4 or 7/8/9, and none of those phases waits for Phase
10 merely to use the existing review workflow. No new Goal is started here.

CBD15 owns evaluation semantics; CBD15.1 owns structured ReviewRequest preparation
and external-result acceptance. sm-workflow owns executor/context selection,
existing Continuation, CAM, repair/closure decisions and effect authority.
Required independent review stays independent. Do not duplicate producer rules.

## Retained work and item ownership

Transfer the unfinished P6-SQ01..05 integration obligations from 6.1/6.3, keeping
their IDs, source, findings and original validation attribution. The Phase 10
checklist is their sole completion ledger; their P6 prefix is historical, not a
remaining Phase 6 gate. Source/fixture/compiler evidence is not real external
review or acceptance. Phase 6's accepted managed-effect/CBD16 slice stays accepted.

Keep the existing CBD provider/Operation and consumer adapter/tests as the
starting point. Reconcile the actual implementation at activation; do not restore
old synchronous AI acquisition just because a historical test used that route.
No compatibility aliases, implicit fallback or parallel evaluator is required.

## Main route first

1. Inspect the accepted initial-use route and CBD SQR-04 / RR06 handoffs. Settle
   actual Operation signatures, typed request/result and execution attribution
   against existing call sites. Wait only for needed producer acceptance, not
   CBD standalone/full-roadmap closure. Producers do not wait for this Phase.
2. Complete P6-SQ01 as one vertical slice: prepare through CBD -> issue actual
   semantic work -> accept its external result through CBD -> existing CAM ->
   same Review/Submit/Advance -> authorized local Step commit. Preparation and
   acceptance invoke no CBD AI; Submit does not trigger a second review.
3. Extend that proven route to integrated Phase Review (P6-SQ02) and one affected
   repair/re-review (P6-SQ03), retaining applicable evidence and finding identity.
4. Complete Final evidence reuse and explicit gap behavior (P6-SQ04/05). Cover
   wrong-target/failed/incomplete results with bounded specs, not a broad live-AI
   matrix. An enabled required CBD evaluation failing is not ordinary-review PASS.
5. Validate the changed route, perform required independent review and Phase
   closure, and hand the accepted integration to existing users/successor work.

Keep tests with each connected package. Reuse producer guarantees and accepted
initial-use behavior under their actual conditions; no per-helper review cycle,
duplicate full validation, management hash, TTL, copied state or new Job store.
No CBD review is required merely because a state label or commit changed.

## Validation, use and cost

This Phase owns its focused/regression and required Phase validation; it is not
added to the PHASE-6..6.3 aggregate sequence and cannot retroactively invalidate
that release. Select validation for actual changes and applicable repository
rules at activation; do not claim that earlier full validation covers new code.

Prove an actual external result and normal authorized commit on the Step route,
then integrated detection, affected repair and Final reuse. Existing permissions,
subject binding, coverage and blocker handling remain enforced. Preserve the
already supported initial-use route; transition the selected review contract
explicitly rather than silently downgrading it on provider failure.

Use available initial-production observations as a baseline for comparable
workflow runs: AI use/cost where observable, elapsed time, interventions and
rework. Record conditions and unavailable measurements; do not invent savings or
require new telemetry infrastructure or a fixed savings threshold for acceptance.
Full provider matrices, standalone CBD implementation, UI/KPI, new security
scanners and broad legacy CAR Review migration are outside this Phase.
