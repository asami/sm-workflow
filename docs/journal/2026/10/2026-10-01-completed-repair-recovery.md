# Completed repair recovery for managed re-review

Date: 2026-10-01
Phase: sm-workflow Phase 2
State: IMPLEMENT

Continue user-requested parent-direct limited recovery. Own
GoalPhaseManagedRepairPublication.scala, GoalPhaseManagedSuspensionOperationSpec.scala
and this journal. No other source, ABI/schema, provider/pool or public Operation
change. Preserve all coexisting work and the full Phase goal.

Before re-review publication, recover original review Intent from an opaque
retained RepairStepResult witness and authoritative instance history. Require
the exact typed closing result and matching repair completion causation,
progression and adjacent suspension. Reconstruct only the historical suspension
prefix and reuse the existing managed repair admission as read-only correlation;
do not replay completion, claims, writes or fresh review admission. Return
immutable original review facts, not a claim of acceptance or current freshness.

Extend the fifth-host executable scenario with adjacent Given/When/Then:
recover after repair completion; preserve unknown historical admission time;
read authoritative changed bytes and retain expired evidence while requiring
a new full review; reject missing witness and unrelated history without mutation.
Validate through the same six-suite serial command used for invocation 456.
This is the durable predecessor boundary of re-review publication, not its
implementation or public advance/CLI acceptance. Publication must later freeze
its first candidate in issued work and return that exact candidate on retries
without re-reading changed artifact bytes.

## Implemented prerequisite and focused evidence

Added read-only recoverCompletedRepairC. It verifies the retained repair closing
type/reference and completion history, reconstructs its historical suspended
prefix, and derives the original review Intent through the same managed reads
used for ordinary repair admission. admitRepairC now discards that derived Intent
rather than duplicating admission. No legacy runtime, map, write or new claim is
introduced. Completed-repair recovery deliberately does not publish its successor.

Invocation `SMWF-COMPLETED-REPAIR-RECOVERY-DIRECT-20261001-457` passed 51 tests
in six suites, zero failures/aborts. SBT exit 0, wrapper exit 0, lock released;
the persistent summary was read after the terminal marker. The modified fifth-host
scenario passed recovery, changed-byte/full-review preparation, stale evidence
preservation, absent-witness/unrelated-history refusal and unchanged-history
assertions. Existing review, repair and managed completion regressions passed.

- Log: `/tmp/skill.cncf.d/cncf-sbt-d8c52e3e299e6ab8359e94bafffbad64dea743a32f60bdf78d3de9ca13f9fdc2-db1a2ea12780c7f42ad80b8c78ad84ff/50574-20260930T233504Z.log`
- Summary: `/tmp/skill.cncf.d/cncf-sbt-d8c52e3e299e6ab8359e94bafffbad64dea743a32f60bdf78d3de9ca13f9fdc2-db1a2ea12780c7f42ad80b8c78ad84ff/50574-20260930T233504Z.summary.json`

`git diff --check` passed. No producer source/publication, commit or full
acceptance gate ran, and no live SBT remains. The existing unchecked generic
WorkOrder pattern warning remains. Next implementation must bind managed
re-review publication, validate current revision against this retained predecessor,
read current bytes once on first publication, atomically retain suspension/
Continuation/issued input, and serve exact retries from that retained issued input.
CLI advance, complete default factory, failure/concurrency/process coverage,
independent reviews and Phase release remain unfinished.
