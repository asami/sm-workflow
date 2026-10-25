# Recovery correction and observation delivery plan

Date: 2026-10-24 (Asia/Tokyo)
Status: ROBS-1 producer correction qualified; ROBS-2/3 planned; real Session adoption remains owner-controlled
Producer: sm-workflow開発; consumer: sm-workflow-dev開発
Specification: [recovery observations](../spec/recovery-observations-and-cause-resolution.md)
Handoff: [English developer instructions](../journal/2026/10/2026-10-24-recovery-observations-handoff.md)

## Delivery position

Extend the already-requested producer correction for commit selection and excessive
recovery approvals. Deliver the main path at the next coherent boundary, ahead of
broader diagnostic automation. This is a bounded correction slice, not a new
numbered Phase, a redefinition of Phase 6.5 Steps or a Phase 6.6 prerequisite.
Keep ongoing producer/consumer edits, accepted results and existing Phase plans.
Phase 11/12 services supply Journal/Finding/Evidence; do not reopen their completed
acceptance or wait for all Phase 9 diagnosis/convergence features.

## Main route first

### ROBS-1 — Correct selection and ordinary continuation

Separate logical owned scope from actual accepted Git change selection. Omit
nonexistent unchanged future files, retain tracked deletions/additions/renames and
exclude unrelated edits. Fix the responsible producer or configuration-generation
boundary through existing typed Git operations; no filesystem-existence filter.
Implement ended/no-commit/in-authority correction as ordinary continuation and
matching-commit reconciliation without duplicate effects. Preserve observation
requirements for live/unknown effects and genuine authority changes.

### ROBS-2 — One recovery with automatic records

Connect the actual selected-commit incident to existing Evidence references,
one deterministic Journal projection and an explicitly operation-sourced Finding.
Record recovery success separately from unresolved cause. Use current common
model/storage conventions; extend the review-origin-only producer boundary
explicitly rather than fabricating review results. Deliver one real failure ->
correction -> ordinary continuation -> records -> public retrieval route first.
Keep recording failures separate from already-completed business effects.

### ROBS-3 — Repeated observation and cause resolution

Make repeated observation of the same actual result repeat-safe. Link fresh
occurrences only with an established cause/producer identity; preserve ambiguous
cases as observations. Resolve a cause only from an attributable fix/explanation
and affected evidence. Feed existing Step/Release assessment and convergence
context without an extra AI call or new repair budget. Generalize to other actual
recovery producers only after the commit route works; universal detection and
whole-history backfill are outside this slice.

### ROBS-4 — Existing Session adoption and handoff

Provide the public adoption procedure to the consumer, retaining its exact current
Session, accepted work/review, failed/unknown history, counts and applicable tests.
Do not require re-Start, replayed acceptance, direct storage edits or a successful
check rerun just for adoption. Use the next required validation/closure or already
applicable observations. A live effect is collected, not killed or duplicated.
If observation recording is not yet installed, disclose that limitation while
retaining actual recovery evidence; do not reintroduce approval as a detector.

## Acceptance checklist

- [x] ROBS-A01: A nonexistent unchanged future owned path cannot break the current
  commit; tracked deletion/rename/addition semantics and unrelated-edit exclusion
  are demonstrated through the responsible typed selection route.
- [x] ROBS-A02: An ended, confirmed no-commit, in-authority preparation correction
  continues without Human reapproval; matching existing commit reconciliation
  creates no duplicate; live/unknown effects are not blindly replayed.
- [ ] ROBS-A03: Actual recovery produces linked Journal/Evidence/Finding data with
  zero additional AI calls and can be retrieved through common public operations.
- [ ] ROBS-A04: Successful recovery leaves cause unresolved; an attributable cause
  correction/explanation changes only its own disposition with affected evidence.
- [ ] ROBS-A05: Re-reading the same result duplicates neither event nor count;
  a new related occurrence retains its own evidence, and ambiguous causes are not
  merged merely because stderr matches.
- [ ] ROBS-A06: Journal/Evidence/Finding recording failure reports actual partial
  state without replaying commit, inventing a reference or revoking business success.
- [ ] ROBS-A07: Same-Session adoption preserves accepted work/review, historical
  uncertainty/failure, repair counts and validation attribution without re-Start or
  unnecessary validation. Existing repair/convergence limits remain effective.
- [ ] ROBS-A08: Human output distinguishes recovery and cause state; existing
  Step/Release assessment can consume unresolved items without a new AI gate.

Use focused behavioral checks for changed semantics and applicable existing
evidence. Normal Phase SBT is runtime-owned via Submit/Advance. Tests in isolated
fixtures do not establish adoption in the existing consumer Session; report both
separately. This checklist records acceptance, not new per-invocation approvals.

Producer qualification for A01/A02 is bounded to the correction and isolated
installed generations; it is not acceptance of a consumer Phase or the pending
observation features. Same-Session consumer instructions are in the
[ordinary-continuation handoff](../journal/2026/10/2026-10-24-selected-closure-continuation-adoption.md).
The existing consumer already reported progress beyond its former Q1 closure;
adoption must preserve that progress rather than repeat that effect.
Actual qualification records remain outside Git at
`macmini:~/backup/journal/2026/10/sm-workflow/closure-selection-continuation/p65-closure-selection-continuation-20261024-01/`,
with default retention through 2026-11-23 and live owner references preserved.
