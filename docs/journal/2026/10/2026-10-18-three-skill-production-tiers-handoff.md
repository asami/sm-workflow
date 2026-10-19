# Three-skill production use: Phase 6.3 and Phase 7 handoff

Date: 2026-10-18
Status: adopted planning clarification; implementation and live adoption not claimed

## Decision

The user explicitly adopts three different use tiers at Phase 6.3 completion:

| Skill | Phase 6.3 | Phase 7 |
| --- | --- | --- |
| `sm-split-phase` | Ordinary production use | Continue use |
| `sm-repository-sync` | Ordinary single-repository production use | Extend to related repositories and dependency-aware synchronization |
| `sm-goal-phase` | Limited single-repository production use | Full practical adoption through collaboration with related-project/worktree/artifact management |

This refines the earlier [single-repository production handoff](2026-10-18-phase-63-single-repository-production-handoff.md).
It does not postpone normal split/sync use or weaken any accepted review,
validation, authority or closure obligation. Limited GoalPhase means a bounded
supported development scope, not reduced assurance or incomplete work labeled PASS.
CBD integration stays in Phase 10, outside the initial-use prerequisites.

The [roadmap](../../../phase/README.md#production-availability-by-completed-phase--clarified-2026-10-18),
[6.3 use tiers](../../../phase/phase-6.3.md#three-skill-use-tiers--2026-10-18) and
[Phase 7 adoption boundary](../../../phase/phase-7.md#full-practical-goalphase-adoption--2026-10-18)
are the current allocation. Phase 6.4 adds evidence storage without changing
those tiers. Phase 8 selected extensions and Phase 9 observed provider switching
remain later improvements, not prerequisites for the accepted initial scope.

## Active development chat: adoption instructions

1. Adopt at the next normal planning/dispatch boundary. Preserve Goal/base/PLAN
   epoch, current counters, findings, all tracked/untracked changes, generated
   work, execution successes/failures and original validation evidence. Collect
   issued work under its issued contract; do not retarget an in-flight worker.
2. Keep accepted PLAN/COMMAND and other precursor results accepted. Continue
   currently authorized work and necessary independent repairs. This edit does
   not start Phase 6.3/7, reset the active Goal or change its current live state.
3. At 6.3 integration, map existing actual evidence to P63-USE01 (single-repo
   sync), P63-USE02 (ordinary split) and P63-USE03 (limited local development)
   in the [shared checklist](../../../phase/managed-test-suites-phase-checklist.md#phase-63-production-use-acceptance--2026-10-18).
   New labels 02/03 expose existing three-family acceptance; they are not extra
   review/full-test cycles or reason to rerun every precursor scenario.
4. For SplitPhase, retain actual request/decision/result and resulting usable
   Phase plans with work coverage, dependencies and validation allocation.
   For GoalPhase, retain actual local implementation/repair/validation/review/
   Admission and Step/Phase closure evidence. Fill only missing installed-route
   gaps. A mock or helper-only result does not establish normal installed use.
5. Keep P63-USE01's actual authorized integration/push/convergence condition.
   Publish usage/setup information that states the three tiers separately.
   Do not claim broad multi-project GoalPhase support at 6.3 or delay ordinary
   split/sync until Phase 7. Preserve the existing four-member aggregate full-
   validation owner PHASE-6.3 and required independent review/closure.
6. During Phase 7, prove the combined path within existing A/B/C/D scenarios:
   local GoalPhase commit -> explicit artifact publication/selection and affected
   consumer checks -> RepositorySync workspace synchronization/convergence.
   GoalPhase owns local development; resources/artifact operations and
   RepositorySync own related-project management. No automatic artifact switch
   on Git sync, duplicate workflow engine or separate full integration campaign.
7. Report retained evidence, actual remaining installed gaps and supported use
   limits at the relevant acceptance. Existing unsupported features cannot be
   reported as delivered merely because planning terminology changed.

## Scope of this update

Only plans/checklist wording and handoff documentation change. No production or
test source, generated artifact, private workflow record or Git index is edited.
No product test, installation, deployment, commit, push, new Goal or message to
another chat is performed. Historical evidence retains its original conditions.

Document validation checked eight affected files and 203 local link targets;
new section targets exist. P63-USE01..03 each has one unchecked completion row.
The four-member aggregate sequence/owner and Phase 10 CBD allocation are
preserved. `git diff --check` passes; no product test or acceptance is claimed.

The later [cost-first execution-order handoff](2026-10-18-production-cost-first-execution-order.md)
optimizes the order within these unchanged use tiers: finish ready 6.3 main routes
with reused evidence, and connect Phase 7 A into C before remaining operation
breadth. It preserves every existing completion/validation obligation.
