# ADMISSION label operation

## Decision

Adopt canonical ADMISSION labels in sm-workflow and CNCF executable specifications.
The target is at most one minute of aggregate test-body execution, excluding all
preparation and cleanup. It is not a fixed timeout or a per-test allowance.
Do not equate SBT elapsed time with body time or discard successful over-target
results. Measurement gaps remain explicit.

## Delivery and adoption

sm-workflow uses its existing `testOnly --purpose ADMISSION` route. Initial
representatives cover ordered GoalPhase progression, RepositorySync input
selection and SplitPhase candidate allocation. CNCF starts with validation
metadata interpretation, discovery selection and operation binding, exposed via
`testAdmission` and `admissionTestSelection` over canonical metadata. No manual
class-name enrollment or second label vocabulary is introduced.

These are additive selections. Preserve existing FULL/HEAVY classification and
running validations. Existing Session, Plan, accepted results, history, repair
counts and evidence retain their original scope. Adopt the new selection only
when preparing a subsequent validation; no restart, replay or retrospective
coverage claim is needed. Keep normal Phase validation runtime-owned.

See [the specification](../../../spec/label-driven-test-selection.md#admission-operating-target).

Local execution evidence (2026-10-26):
`/Users/asami/backup/journal/2026/10/admission-label-operation/run-6709fa56e14d/`.
Original invocation records remain at their bound locations; logs and result
payloads are not committed to Git.
