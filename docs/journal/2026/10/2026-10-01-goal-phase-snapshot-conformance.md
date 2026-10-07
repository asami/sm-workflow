# GoalPhase review snapshot conformance and consumer artifact refresh

Status: application suspension conformance verified; production managed bootstrap remains open.

## Retained boundary

This is sm-workflow Phase 2 implementation under the retained user-authorized
parent-direct recovery. CNCF Phase 88 remains the admitted upstream source
scope. Existing dirty work, independent cncf-* skills and default runtime
behavior are preserved. No acceptance, release, commit or push occurred.

## Corrected application projection

`GoalPhaseReviewBoundary.suspendedRecordC` previously wrote
`stepIdentity@candidateRevision` into `SuspensionBoundary.contextSnapshot`.
Canonical `WorkflowIssuedWorkDataStorePersistence` and
`WorkflowCompletionDataStorePersistence` compare that reference with the
issued continuation's `context.snapshot.workflowRevision`. Consequently the
actual application projection could not enter their durable path.

The projection now uses that continuation snapshot's workflow revision. The
existing rejection of foreign workflow snapshots remains. Candidate authority
is not replaced by a workflow version: the exact typed `StepCloseRequest`
(program/planning digests), pending candidate revision and semantic review
evidence retain their separate roles. The new `GoalPhaseReviewSnapshotSpec`
checks the persisted reference, unchanged typed input/candidate, continuation
identity/revision, and rejection before projection for a foreign snapshot.
Existing review, candidate-repair and repair-loop suites verify the affected
consumer behavior, including candidate/evidence drift rejection.

## Producer/consumer evidence

The preceding CNCF `publishLocal` refresh-404 supplied the unchanged development
coordinate `org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT`. Consumer invocation
`SMWF-GOAL-REVIEW-SNAPSHOT-DIRECT-20261001-405` ran in sm-workflow with:

```text
--batch
show Test / dependencyClasspath
testOnly org.simplemodeling.textus.smworkflow.admission.GoalPhaseReviewSnapshotSpec org.simplemodeling.textus.smworkflow.admission.GoalPhaseReviewBoundarySpec org.simplemodeling.textus.smworkflow.admission.GoalPhaseRepairCandidateBoundarySpec org.simplemodeling.textus.smworkflow.admission.GoalPhaseRepairLoopSpec
```

The actual classpath reports
`/Users/asami/.ivy2/local/org.goldenport/goldenport-cncf_3/0.5.3-SNAPSHOT/jars/goldenport-cncf_3.jar`.
Its observed SHA-256 is
`41f03ec0e9a7092374f84cd18d6843ff6639a61535e1c531eacaa6b6e67349c9`.
The consumer compiled 73 main and 61 test sources and executed 40 tests across
4 suites: all passed, none canceled/pending/aborted. SBT and wrapper exits were
both zero, with `lock=released`; the matching summary was read after termination.

Evidence log:
`/tmp/skill.cncf.d/cncf-sbt-31502f034509a5ba5815644038018e6751c70821dda81e20cccfef63edb73e49-a78f4669c5079d484df3444587506248/32364-20260930T200136Z.log`.
Summary is the adjacent `32364-20260930T200136Z.summary.json`.
`git diff --check` also passed. This proves current artifact resolution and
application conformance/regression, not use of the new managed binding in a
production application Operation.

## Next actual integration obligation

Main-source searches confirm that Subsystem currently captures the EventStore
service and persistence declaration through private[cncf]
`EventStoreConstruction` and `EventStorePersistenceConstruction` only.
`GenericSubsystemDescriptor` has no corresponding declaration projection.
`withWorkflowVerifiedMultiGuardSpaceC` requires the captured declaration,
exact component grant and one shared managed physical datastore resource.
The public `WorkflowManagedCompletion` binding cannot supply these missing
host facts, and creating a declaration alone grants no runtime access.

The next implementation must wire policy/service and validated storage grants
through canonical host bootstrap. Do not expose a request-controlled policy,
raw DataStoreSpace or claim token; do not move application tests into a CNCF
package to bypass visibility. Prove construction isolation, absent/foreign
grant rejection and actual generated GoalPhase completion against managed
SQLite before calling the application binding production-ready.
Launcher separate-process reopen and server/MCP equivalence remain required;
all production acceptance gates and Phase completion remain open.
