# Phase 20 Acceptance Checklist

Status: planned; all items pending.
Plan: [Phase 20](phase-20.md)

- [ ] P20-01: Bind the actual Phase execution and its invocation/resumption membership
  using existing identities. Define the completion boundary and included closing
  work. Distinguish completed, failed, interrupted and forced-closed outcomes;
  unrelated executions and a new execution of the same Phase number are not
  silently combined. Retain links to related abandoned work where known.

- [ ] P20-02: Aggregate nonoverlapping recorded observations, retaining parent-inclusive
  and cumulative semantics from Phase 13. Include failure and repair consumption;
  repeated reads/notifications do not add consumption. Mark partial/unavailable
  coverage and the observed interval; never replace missing usage with zero.

- [ ] P20-03: Project a small completion summary with model/provider and available work
  category breakdowns, quality disposition, elapsed-time scope and usage coverage.
  Keep Codex/local execution separate. Distinguish end-to-end elapsed time from
  known execution/wait time; do not invent missing timing or compare overlapping
  child durations as wall-clock duration. Reuse Phase 6.10's summary surface.

- [ ] P20-04: Verify aggregation with bounded cases covering ordinary resumption,
  repair/failure, repeated observations and missing coverage, then demonstrate
  one actual connected Phase summary. Fixtures alone do not establish live
  coverage. Recollect the summary without new AI work or replaying completion.

No implementation, measurement coverage or Phase acceptance is claimed by this plan.
