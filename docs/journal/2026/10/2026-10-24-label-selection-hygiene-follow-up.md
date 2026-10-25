# Label-selection maintenance

Date: 2026-10-24 (Asia/Tokyo)
Owner: sm-workflow
Boundary: nonblocking follow-up; focused LTEST acceptance remains closed.

## HYG-LTEST-01 — OPEN

Pre-existing `requestedScope` and `actualScope` local names in
`GoalPhaseValidationMetadata` differ from the flatcase naming convention.
There is no behavioral risk from these unchanged names. Select a separate
naming-only maintenance task; preserve public and persisted contracts.

## HYG-LTEST-02 — OPEN

The read-only `labelledTestSelection` inspection task evaluates framework
discovery without explicit caller cleanup of `Test/testLoader`. Its separate
metadata loader closes correctly, and selected-test execution closes its shared
loader on success/failure. Repeated inspections may retain framework resources
within the SBT session. Add inspection-owned cleanup at a separate maintenance
boundary, preserving discovery, selection and execution semantics.

## Evidence location and scope

Independent review dispositions and actual execution evidence are outside Git:
`macmini:~/backup/journal/2026/10/sm-workflow/label-tests-20261024/a-4769766c-28bb-4b7e-b86b-24e93bef8359/`.
Retention: 2026-11-23; bound active consumer references remain on hold, next
review 2026-10-31. No new Development Candidate was opened by this migration.
This ledger neither reopens an accepted Phase nor accepts unrelated source work.
