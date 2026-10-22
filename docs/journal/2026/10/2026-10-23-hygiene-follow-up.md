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
