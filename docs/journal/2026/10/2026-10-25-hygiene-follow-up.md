# Repository synchronization maintenance follow-up

Date: 2026-10-25
Owner: sm-workflow-dev
Discovery: independent focused review of the historical standalone probe intent.
These records do not reopen a Phase or alter a Goal, Session, accepted result,
history or repair counter.

## HYG-SM-WORKFLOW-DEV-PROBE-001 — OPEN

- Location: `src/test/probes/GoalPhaseCmlDerivationProbe.scala`.
- Evidence: this dormant standalone Scala 2.12 probe retains thirteen phases,
  six Required Operations and an `accepted ... combinations=13` message.
  The ordinary GoalPhase derivation specification selects its embedded probe,
  with fourteen phase combinations and seven Required Operations, instead.
  The preserved twenty-five-name Provided Operation expectation is a historical
  intermediate snapshot and does not claim the latest producer ABI.
- Category / priority: historical test-input clarity, low.
- Risk: a maintainer could manually run the old probe against current CML or
  mistake its success message for current acceptance.
- Boundary: the present intent preserves the user's existing expectation update;
  compile-only validation passed, with no probe execution or ABI acceptance.
- Follow-up: explicitly label or retire the dormant historical probe in a
  separately selected maintenance task while retaining its provenance and the
  active specifications' ownership of runtime and ABI acceptance.
