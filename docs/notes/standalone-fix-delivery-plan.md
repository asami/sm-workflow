# Standalone sm-fix delivery plan

Date: 2026-10-24 (Asia/Tokyo)
Status: P67-T1 source implementation in progress; native validation and installed acceptance pending
Owner: sm-workflow development
Specification: [Step-equivalent standalone acceptance](../spec/standalone-fix-acceptance.md)
Handoff: [English developer handoff](../journal/2026/10/2026-10-24-standalone-fix-handoff.md)

## Scheduling and responsibility

Allocation revised 2026-10-26: [Phase 6.7](../phase/phase-6.7.md) owns SFIX-1/2,
the SFIX-3 finding-origin sm-review-fix entry and initial SFIX-4 delivery.
[Phase 17](../phase/phase-17.md) owns remaining SFIX-3 dirty/focused/full selection,
investigation/related-project integration and extended SFIX-4 adoption.
Reuse Phase 6.5 execution and Phase 6.6 diagnosis without enlarging their
acceptance or interrupting ongoing work/recovery. Original SFIX IDs remain
requirements; Phase checklists own delivery evidence without duplicate runs.
Phase 14 retains investigation, Phase 7 workspace support and Phase 9 enhanced
routing; none is a whole-Phase prerequisite for the initial repair route.

## Main route first

Coordinate SFIX-1/2 with [GTASK-1/2](goal-task-delivery-plan.md): reuse one execution
path while retaining distinct acceptance/commit policies. GoalTask adds a normal
development input, integrated Task review and local closure; standalone FIX keeps
conditional review and no implicit commit. Neither route waits for all variants
of the other. This does not enlarge Phase 6.5/6.6 acceptance.

### SFIX-1 — One single-repository request through existing FIX

Add the thin `sm-test-fix` entry over shared repair WorkOrder/Continuation,
diagnosis and runtime validation. Establish one actual failing test -> bounded
repair -> successful focused validation -> accepted uncommitted result. Define
standalone acceptance explicitly without weakening GoalPhase's committed Step
contract or inventing a Phase. Reuse existing source scope and authorization.

### SFIX-2 — Conditional review and repeated repair

Reuse deterministic assessment: settled local fixes finish with zero independent
review calls; explicit required review or unresolved acceptance meaning dispatches
one independent scoped review. Follow-up FIX/re-review remains in the same unit,
uses affected checks and retains prior evidence/counters. There is no AI call to
waive review and no synthetic PASS for skipped review.

### SFIX-3 — Reuse for other entry conditions (split between 6.7 and 17)

Phase 6.7 adds `sm-review-fix` as finding-origin input. Phase 17 holds dirty/focused
selection as options, not new repair engines. Use existing full-test policy when
explicitly requested. Connect Phase 14 `sm-diagnose` proposals only when repair is
authorized; investigation-only requests remain read-only. Do not wait for all
entrypoint variants before delivering SFIX-1/2.

### SFIX-4 — Delivery and adoption

Deliver matching caller skills/contracts and normal public operations; record
actual execution, review disposition and acceptance in common Journal/Evidence.
Use existing runtime-owned SBT. Confirm preservation of existing work and results,
without moving active Phase repairs to a standalone Session. Supply operational
examples and English handoff. Commit/release remains explicitly selected work.

## P67-T1 source delivery

The [standalone runtime contract](../spec/standalone-fix-runtime.md) records the
new public source route, shared check/diagnostic/Evidence machinery and its
truthful fixture-versus-installed evidence boundary. No SFIX or Phase acceptance
checkbox is closed by source implementation or bounded admission fixtures alone.

## P67-T3 source delivery

The [review-origin runtime contract](../spec/standalone-review-fix-runtime.md) adds
identified original findings, exact disposition/change/check attribution and
conditional affected review through the same standalone engine. Native checks
and actual installed skill/delivery evidence remain separate; no checklist box
is closed by source fixtures.

## Acceptance checklist — pending

- [ ] SFIX-A01: A real single-repository test failure is repaired and passes actual
  focused behavioral validation; acceptance does not require a Phase or commit.
- [ ] SFIX-A02: Compilation failure means affected tests not executed; empty or
  compile-only success cannot satisfy code-repair focused acceptance.
- [ ] SFIX-A03: A local repair with sufficient evidence finishes with zero
  independent review/waiver calls and a truthful not-required review disposition.
- [ ] SFIX-A04: Explicit review or residual meaning causes scoped independent
  review; repair and re-review reuse unaffected evidence and retain counters.
- [ ] SFIX-A05: Review-origin findings retain identity and change/verification
  links; mechanically established resolution needs no duplicate AI review.
- [ ] SFIX-A06: Dirty selection preserves unrelated edits; focused/full policies
  retain explicit obligations; diagnosis-only performs no unauthorized source edit.
- [ ] SFIX-A07: No implicit commit/push/release or Phase/Release Review occurs;
  explicitly selected delivery reuses applicable acceptance evidence.
- [ ] SFIX-A08: Runtime owns SBT launch/wait/results; no AI runner bypass, new
  repair ledger or fabricated observations. Existing Phase repairs remain in-place.
- [ ] SFIX-A09: Adoption preserves Session/history/counts/accepted results without
  re-Start or redundant checks; recovery success does not falsely close a cause.

Use focused behavior checks for changed admission and routing, plus one connected
real request. Reuse prior producer evidence. Documentation alone does not establish
that these entrypoints exist, are installed or have passed the checklist.

## P67-T4 source delivery boundary

The [Task/FIX delivery contract](../spec/task-fix-delivery.md) supplies matching
thin skill sources and passive public result/adoption preparations. Their exact
caller observations retain destination state and distinct owners; they grant no
consent, effect or requester acceptance. Actual installed and authorized
existing-chat evidence is pending. Keep active Sessions pinned: this consumer's
v3 GoalPhase closure codec cannot adopt retained v4 partial completion. Source
CAR ABI reconciliation is outside this Step's owned paths. See the
[English source handoff](../journal/2026/10/2026-10-27-phase-6.7-t4-source-handoff.md).
