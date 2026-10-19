# Production-first rollout: defer CBD review integration to Phase 10

Date: 2026-10-18
Status: adopted planning decision; no source, live workflow or acceptance change

## Decision

The user prioritizes production adoption and cost reduction and explicitly asks
for CBD review integration as a new Phase. This supersedes today's earlier
CBD15/15.1 handoff/adoption dependency on Phase 6.1 closure. It does not erase
that adoption, issued work, implementation, failed attempts or validation history.

[Phase 10](../../../phase/phase-10.md) owns P6-SQ01..05 and the required CBD15/15.1
handoffs. Existing Phase numbers 6.4 and 7/8/9 keep their meanings. Phase 10 is
independent follow-up after initial-use acceptance in 6.3, not a gate for initial
production use or unrelated 7/8/9 work and not forced to wait for those phases.

| Owner | Revised delivery |
| --- | --- |
| 6.1 / existing P61-ADMISSION | Actual reviewer result, existing host/effect authority and ordinary Review/Submit/Advance/Step commit without a mandatory CBD call |
| 6.2 | Existing FIX, affected review, recording and dependency decisions on that route |
| 6.3 | Integrated Phase review, Final reuse, installed-use acceptance and unchanged four-member aggregate full validation |
| 10 / P6-SQ01..05 | CBD Step integration first, then integrated review, affected repair and Final reuse with actual accepted CBD results |
| CBD15 / 15.1 | Existing optimized producer plans; required handoffs feed Phase 10, no longer block 6.1 |

Generic review, meaningful coverage, blocker disposition and required independence
remain initial-use obligations. CBD-specific evaluation/coverage is deferred and
must not be reported as delivered, PASS or a fabricated empty result. Preserve
accepted CNCF/Cozy guarantees and the already accepted Phase 6 CBD16 managed-effect
slice; this is not authority to remove runtime safeguards or reopen Phase 6.

## Instructions for the active sm-workflow development chat

1. Adopt at the next ordinary planning/dispatch boundary. Collect already issued
   work under its original contract; assess applicability without retargeting it.
   Preserve Goal/base/PLAN epoch, current counters, accepted PLAN/COMMAND, all
   tracked/untracked changes and original evidence/findings/failures. Do not copy
   a dated counter into live state or restart the Goal.
2. Keep P61-ADMISSION open for the revised ordinary review route. Reconcile its
   actual code: inspected `ThreeWorkflowHostEvidencePorts.goalReview` still calls
   `GoalPhaseScopedQualityReview.admitC` after host evidence. Therefore this
   documentation edit alone does not enable CBD-free execution.
3. Scope the minimal caller/requirement/test changes so ordinary reviewer results
   and the existing admission contract can complete without CBD. Preserve actual
   result/subject binding, independence, host/effect authority and blockers.
   Do not return success from a dummy CBD adapter, swallow provider errors, label
   absent CBD coverage PASS or add an alternate policy engine/compatibility layer.
   Retain the CBD integration work for Phase 10 without leaving it as an accidental
   mandatory call on the initial-use path. Save displaced work using normal
   history/evidence practices, not duplicate production implementations.
4. Continue any independent normal Action/UoW owner-binding repair and its meaningful
   validation. CBD deferral does not resolve that reproduced defect. Determine
   current repair state from the owning chat; the earlier adoption journal is a
   historical snapshot, not current completion evidence.
5. Prove actual review -> Submit -> Advance -> authorized local Step commit with
   CBD unavailable/not configured, plus applicable rejection behavior. Reuse
   prior results only for unchanged conditions. Never use historical Submit09/10
   scripts without reassessing their contract and actual current state.
6. Continue 6.2 and 6.3 using the accepted ordinary route. Retain independent
   reviews and the existing aggregate full-test owner PHASE-6.3; no Phase 10 wait
   or new review cycle. Report unsupported CBD evaluation honestly at first use.
7. Record available actual AI-use/cost, duration, intervention and rework observations
   during gradual production adoption. No new instrumentation project or asserted
   savings target is necessary to start use.

## CBD development chat

Preserve existing Phase 15/15.1 implementation, tests and the main-route-first
producer plan. Their accepted SQR-04/RR06 handoffs now unblock sm-workflow Phase
10, not Phase 6.1. Producer work can progress separately under its existing
instructions; this document neither stops another chat nor starts a new Goal.
No whole-consumer closure prerequisite or new hurried internal-AI workaround.

## Verification boundary

This edit checks documentation ownership, references and unchecked-item transfer.
It does not execute tests, modify source/generated files or private workflow
records, change acceptance, deploy, commit, push or send a message to another chat.

Documentation checks: 21 affected documents and 274 local link targets resolve;
all five transferred P6-SQ items remain unchecked with one completion ledger.
The four-member aggregate sequence and PHASE-6.3 owner are unchanged. Both
repositories pass `git diff --check`. No product test was run for this plan edit.
