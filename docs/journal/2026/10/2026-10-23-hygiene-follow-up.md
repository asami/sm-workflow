# Ordinary commit / sync maintenance follow-up

Date: 2026-10-23
Owner: sm-workflow
Discovery: independent focused review of reason-separated ordinary commits.
These records do not reopen a Phase, change a Goal/Session or reset counters.

## HYG-ORDINARY-SYNC-P13-REFERENCES-001 — OPEN

- Location: `docs/phase/phase-7.md` catch-up references and
  `docs/phase/phase-15.md` dependency summary.
- Evidence: both retain Phase 13 as recursive repository catch-up owner, while
  the adopted `docs/phase/phase-13.md` now plans AI usage accounting.
- Category / priority: future-plan consistency, low.
- Risk: later planning may infer the obsolete catch-up dependency.
- Boundary: these future plans are not executed or accepted by this commit.
  Their actual replacement owner/disposition is not selected here.
- Follow-up: reconcile those references during future-plan maintenance without
  inventing a replacement Phase or changing active work.

## HYG-ORDINARY-SYNC-PREPARATION-NAMING-001 — OPEN

- Location: `NativePreparationOperationFixture.scala`, protected members
  `preparationService`, `preparationOperations`, `evidenceCollection`,
  `withPreparation`, `importRows` and `nativeRow`.
- Evidence: those existing camelCase names predate the current change; the new
  optional-limit helper follows protected snake_case naming.
- Category / priority: naming-only test-fixture maintenance, low.
- Risk: inconsistent protected-member naming; no behavior defect identified.
- Boundary: renaming existing callers is outside the large-input regression.
- Follow-up: a bounded naming-only fixture/call-site cleanup when selected.

## HYG-GPSYNC-01 — OPEN

- Discovery: 2026-10-23, independent review of the initial GPSYNC-01..08 repair.
- Location: inherited private naming in GoalPhaseOrderedProgression,
  GoalPhaseNativeRun, GoalPhaseNativeRepositoryClosure, NativeExternalEvidence,
  ThreeWorkflowExternalEvidencePorts and ThreeWorkflowRuntimeParameters.
- Category / priority: naming maintenance, low.
- Risk: maintenance inconsistency; changed-line naming passed after correction.
- Boundary / follow-up: select a separate naming task preserving public and
  persisted contracts; do not broaden the completion repair.

## HYG-GPSYNC-02 — OPEN

- Discovery: 2026-10-23, final-source strict CAR lint for the same repair.
- Location: existing datastore, host-filesystem, resolved-parameter,
  subsystem-configuration, released-ABI baseline and development-toolchain inputs.
- Evidence: 14 inherited WARN, zero FAIL; strict lint exits 1. The detailed
  inventory is retained in the repair's private evidence archive referenced by
  the completion-sync handoff, outside Git.
- Category / priority: conformance maintenance, low.
- Risk: residual conformance debt; released ABI compatibility remains unverified.
- Boundary / follow-up: a separately owned conformance inventory; no local API
  workaround or claim of released ABI compatibility in this repair.

## DEV-GPSYNC-01 — OPEN

- Owner / target: sm-workflow, GPSYNC-09..12 and amended earlier acceptance items.
- Evidence: the latest spec/plan/handoff requires combined Human confirmation
  and preserve-dirty Equal/LocalAhead continuation; the accepted initial repair
  instead uses conservative safe sync deferral.
- Dependency: one retained Human decision carried through sync selection,
  preservation, integration, convergence and passive observation, retaining
  standalone FULL_STATE and actual effect authority.
- Resume: adopt a coherent follow-on implementation/validation scope at the next
  owning boundary, preserving Goal/Session/Plan/base and accepted result history.
- Boundary: this extension was not substituted into the issued initial repair.
  No gate skip, process fallback, silent checkpoint or accepted-work replay.

## DEV-GPSYNC-02 — OPEN

- Owner / target: development-workstation, normal shared sm-goal-phase promotion.
- Evidence: the reviewed source is `skills/sm-goal-phase/SKILL.md`; installed
  shared discovery still selects development-workstation's copy.
- Dependency / resume: the shared-skill owner selects normal promotion and
  actual caller loading before claiming consumption of the updated instructions.
- Boundary: outside this producer repair; do not silently change the sibling
  checkout or symlink, or equate installed Operation tests with skill consumption.
