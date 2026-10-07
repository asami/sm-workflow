# GoalPhase managed suspension Operation

Continue Phase 2 after producer refresh 426 and consumer validation 427.
Freeze application programs `GoalPhaseReviewBoundary.scala`,
`GoalPhaseStepCloseServiceOperation.scala`, and new
`GoalPhaseManagedSuspensionOperationSpec.scala`. The two existing sources were
read completely before editing. Preserve legacy bind callers and the generated
Operation. Add a host-selected opaque managed-issued dependency, pure exact
WorkOrder preparation from the admitted projected suspension, then one managed
commit before response publication. Managed code must not stage legacy append,
claim a Continuation or publish issued payload independently. Actual retained
instance and issued work must match the selected projection. Errors return no
WorkOrder and never fall back to the legacy route.

Executable spec uses public CNCF host/binding APIs from the application package:
execute the real Operation action, recover its exact issued work through a fresh
host over the same SQLite file, reject duplicate close and missing cursor with
unchanged root/no work. No real Git, production launcher or synthetic claim
authority. Full Phase validation and acceptance remain pending.

## Pre-review focused fixture repair (428)

Invocation `SMWF-GOAL-MANAGED-SUSPENSION-DIRECT-20261001-428` compiled both
application sources, then failed test compilation. The new external-package
spec incorrectly accessed private[cncf] `ExecutionContext.runtime` and imported
two `ContinuationIdentity` types ambiguously. No tests ran. SBT/wrapper 1/1,
lock released. Freeze blocker `GOAL-MANAGED-SUSPENSION-FIXTURE-COMPILE` as M1
orchestrated repair attempt 1: only the new spec; use public
`ResolvedParameters.fromResolvedConfiguration` and fully qualify the runtime
Continuation identity. Preserve package, production APIs and every assertion.
Re-run the same five suites. No architecture or acceptance expansion.

Invocation 429 resolved compilation and ran 40 tests: 38 passed, two new
examples failed during fixture seeding (`Workflow create requires an initial
record`), before invoking close. SBT/wrapper 1/1, lock released. Admit subordinate
M1 fixture repair attempt 2 on the same spec: create the initial record and
append its already-selected first active history entry through the public
instance binding. Do not weaken create admission or seed raw SQL. Preserve
all Operation assertions and re-run the same five suites.

Invocation 430 passed 39/40 tests, including actual managed close and exact
host-reopen recovery. The remaining expectation incorrectly required a cursor
for an empty event batch. Unchanged `DataStoreSpace.conditionalHostEventsFinalizedC`
selects the transient route when `selection.persistent.isEmpty` (lines 565–566);
that route admits only Workflow records and does not observe/write the cursor.
Freeze M1 expectation repair attempt 3 in the same new spec: assert exact work
publication and suspended root under empty-event selection, then explicit
cursor first-install success proving the call did not create/reset it. This
corrects the fixture against the existing event-policy contract; it does not
relax production admission or introduce sender persistence overrides.

## Focused closure evidence (431)

`SMWF-GOAL-MANAGED-SUSPENSION-DIRECT-20261001-431` passed 40 tests in
five suites; none failed or aborted. Test compilation succeeded, SBT/wrapper
0/0, serial lock released, and `git diff --check` passed. The fixture repair
batch is resolved without a product change or public framework workaround.

The real generated request creates its CommandAction and executes the bound
ProcedureActionCall. The managed branch commits the exact projected instance,
Continuation and issued WorkOrder through the opaque host API, then verifies
retained instance and work before encoding the response. It succeeds with
`ContinuationRuntime.empty`; legacy append rejects and legacy UoW supplier
throws, so neither can secretly implement the successful path. Duplicate close
is rejected. A fresh host opens the same SQLite file and recovers the same
WorkOrder and typed input. Empty-event issue neither requires nor initializes
the cursor under the unchanged EventStore policy.

Evidence:
- log: `/tmp/skill.cncf.d/cncf-sbt-a75d0eb6e37132272dfe2b3da61a61532b4cb7f818fea2aa8f7b15cd69769cfd-eef9c2cdfe7f17743059129ffb6d9e41/95467-20260930T214837Z.log`
- summary: `/tmp/skill.cncf.d/cncf-sbt-a75d0eb6e37132272dfe2b3da61a61532b4cb7f818fea2aa8f7b15cd69769cfd-eef9c2cdfe7f17743059129ffb6d9e41/95467-20260930T214837Z.summary.json`

This is actual Operation-action persistence/reopen evidence, not deployed
Subsystem service dispatch, separate-process CLI/server/MCP, managed review
completion or production factory composition. Next bind `submitStepReview` to
the recovered issued work and actual managed instance provider, test its retained
review result/replay rejection, then connect canonical host service composition.
Full Phase validation, independent review and acceptance commits remain pending.
