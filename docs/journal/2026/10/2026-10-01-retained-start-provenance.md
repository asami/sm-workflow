# Read-only retained Start provenance

Continue active Phase 2 under parent-direct limited recovery. Previous turn
completed committed Start readback and 136 tests. Own GoalPhaseStartExecution,
GoalPhaseStartExecutionSpec and this journal; preserve all unrelated work.

Implement a private-application read-only retained Start loader. Re-admit the
original host selection, use the existing Start identity scheme unchanged, load
and validate the current durable record, and require its first history entry to
be the exact original activation with the admitted definition and correlation.
Derive the original Start projection in memory; return its separately observed
current revision. Do not run Start or append, reset or infer the current WAIT/
WorkOrder. A constructed witness is historical provenance only, not caller
authentication or storage-write authority. No new public Operation/CML ABI,
identity/hash scheme, managed pool, grant or installer.

Extract the existing activation entry and Start projection helpers so normal
Start and read-only provenance use the same facts. New GivenWhenThen examples
prove recovery after later progression with no writes, and rejection of absent,
NotStarted or altered original correlation. Existing rehydration and real
SQLite consumer scenarios remain in the eighteen-suite accumulator.

Validate actual packageBin and that accumulator through the shared serial
wrapper. Registration, actor authentication, grants/atomic receipts, launcher
activation and Phase review/release remain unimplemented/unaccepted.

## Verified result

Attempt `SMWF-RETAINED-START-DIRECT-20261001-514` completed packageBin and
138/138 tests in 18 suites. Structured summary and terminal marker agree:
compilation succeeded, zero failed/aborted/canceled/ignored/pending,
sbt_exit=0, wrapper_exit=0, lock=released. No live SBT session remains.
git diff --check passed.

Summary: `/tmp/skill.cncf.d/cncf-sbt-7f7d47470517fa3f28e2ab5d8f95596581e8ca85f292b7b8be871e06bd963c18-1686852b55e2ae953a64f29ff2239c28/25132-20261001T034801Z.summary.json`.

The new loader is an internal registration prerequisite, not yet bound to a
public transport or production association writer. Its focused scenarios use
the persistence port fixture; the accumulator's existing actual SQLite paths
verify unchanged Start/runtime integration, not this loader's independent SQL
reopen acceptance. No commit or Phase/goal completion claim.
