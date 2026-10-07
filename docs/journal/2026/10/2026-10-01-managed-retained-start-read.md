# Managed retained Start read

Continue active Phase 2 with parent-direct limited recovery. Previous turn
implemented read-only historical Start recovery with port fixtures. Own
GoalPhaseManagedStartAssembly.scala, the existing managed selection/reopen
scenario in GoalPhaseManagedSuspensionOperationSpec.scala and this journal.

Bind recovery to the current ActionCall Component/Subsystem. Require its active
dispatch lease before framework access, admit the exact Workflow store/root/
Continuation declaration through existing WorkflowComponentStorage, and use a
read-only instance adapter. Selection is also read through that same current
call. Restore the DataStoreSpace binding on all exits. No captured Factory
ExecutionContext, new upstream API, pool, public Operation or write grant.

Extend the actual SQLite selection/reopen scenario with a private probe inside
existing Start dispatch. Reopened host reads exact original Start from SQL;
escaped and null cores fail. After a later durable history entry, the probe
recovers original revision-one Start and observed revision two without altering
the current record. Start itself still rejects resetting later progression.
No new public recovery Operation is inferred from this test probe.

GivenWhenThen clauses surround the new reopen/read/progression expectations.
Validate packageBin and the unchanged eighteen-suite accumulator through the
shared serial wrapper. Registration/writer grants, authenticated participants,
CLI activation and complete Phase assurance remain open.

Attempt515 compiled and passed 138/138 in 18 suites with lock released.
Inspection before closure moves pure WorkflowComponentStorage host-grant
admission ahead of application provider binding, so an incomplete nested
configuration or foreign root fails before resource access. Add those exact
negative checks inside the same live-call SQLite probe; no new scope/API.
Rerun the unchanged accumulator on the amended source before retaining success.
Summary: `/tmp/skill.cncf.d/cncf-sbt-013f997280b4d9ead04834c133f2a7984dac82a6353e44a7ecf45595d32ba2e0-0d6434654694c857f45181bb6e37844a/27062-20261001T035136Z.summary.json`.

## Verified result

Attempt `SMWF-MANAGED-RETAINED-START-DIRECT-20261001-516` completed actual
packageBin and 138/138 tests in 18 suites. Full structured summary and terminal
marker agree on compilation success, zero failed/aborted/canceled/ignored/
pending, sbt_exit=0, wrapper_exit=0 and lock=released. No live SBT session.
git diff --check passed.

Summary: `/tmp/skill.cncf.d/cncf-sbt-928f895c03abd11fa9e7e843897938d85582f1e3040db13fd33494c703e9926a-618836b73c591a2ba42a6e39d89188a5/27932-20261001T035239Z.summary.json`.

Actual SQL reopen/progression proof closes only the read-only prerequisite.
The probe remains test-private; no new transport or association installation
is enabled. Actor admission, common Create-only writer grants and atomic
registration receipts are the next production boundary. No commit, acceptance
review or complete Phase/goal claim.
