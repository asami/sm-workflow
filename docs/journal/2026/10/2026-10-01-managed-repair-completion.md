# Managed repair completion

Date: 2026-10-01
Phase: sm-workflow Phase 2
State: IMPLEMENT

Continue the explicitly requested parent-direct limited recovery. Own
GoalPhaseRepairCompletionOperation.scala, GoalPhaseManagedRepairPublication.scala,
new GoalPhaseManagedRepairCompletionAssembly.scala,
GoalPhaseManagedSuspensionOperationSpec.scala and this journal. Preserve all
other work. No CNCF/Cozy edits, provider/pool creation, ABI/schema/CML changes,
legacy compatibility removal, commit or acceptance changes.

Separate repair completion from subsequent re-review publication. Bind generated
submitStepRepair through opaque managed completion, derive repair authority from
original issued repair plus its adjacent committed review history and retained
review witness, and admit only the exact ordinary completed RepairStepResult.
The closing program clears the selected repair suspension and records its exact
typed result atomically. Return a Wait interaction; no legacy loop callback,
fresh review judgment, independent append or process-local Intent map.

Executable behavior extends the reopened-host repair scenario: malformed outcome
and evidence reference fail without mutation, valid normal Subsystem dispatch
retains the repair result and clears one suspension, and a fifth reopened host
reads exactly that result and rejects replay. Use adjacent Given/When/Then.
Validate the existing six focused suites through the shared serial wrapper.
Re-review publication and public advance/default factory wiring remain required
next work; this slice is not full Phase acceptance.

## Bounded validation repair

Invocation 455 compiled the three main sources, then failed Test compilation
at GoalPhaseManagedSuspensionOperationSpec.scala:393: ScalaTest's runtime type
matcher could not resolve OperationResponse.Scalar as used in that assertion.
No behavior suite ran. SBT/wrapper exited 1/1 and the serial lock was released.
The persistent summary was read. Freeze M1 repair to that assertion only: match
the scalar response, decode WorkflowWaitJsonV1 and assert exact Handle and Wait
revision/context/presentation. This preserves and strengthens the original
observable expectation; no main-source or contract change is part of the repair.
Use the same six focused suites with a fresh serial invocation identity.

Failed summary: `/tmp/skill.cncf.d/cncf-sbt-752c96024320be78d2497d72928ab1ee94a099da95b2e69a814853766932fbb6-c9b2f29e85c86f410eab87540a6f2072/47405-20260930T233054Z.summary.json`

## Implemented route and final focused evidence

Managed repair publication now supplies read-only ordinary-result admission:
exact issued headers, current suspension, instance Handle, completed outcome,
nonblank summary and evidence identity; adjacent historical review suspension
and completion; opaque retained review and original issued input; deterministic
repair successor ID, run and required committed result reference. It consumes
no claim and performs no write. The managed closing program projects one
repair-result history entry, and framework completion commits that projection
with the typed semantic/closing result. No callback issues re-review.

GoalPhaseManagedRepairCompletionAssembly composes this through captured Component
storage authority in create_Core. Its persistence adapter refuses independent
create/append, and its managed Operation has no legacy UnitOfWork fallback.
Existing legacy bindC/repair-loop behavior remains compatible.

Final invocation `SMWF-MANAGED-REPAIR-COMPLETION-DIRECT-20261001-456` passed all
51 tests in six suites, zero failures/aborts, SBT exit 0, wrapper exit 0,
lock released. The structured summary was read. The extended scenario confirms
invalid outcomes/evidence leave revision 4 suspended, normal dispatch completes
revision 5 with the exact Wait JSON, and a fifth host retains both repair and
closing results while rejecting replay. Legacy review/repair suites also passed.

- Log: `/tmp/skill.cncf.d/cncf-sbt-ba1580ae71d203016af3acccc3192db14ed641db52b270a7a16897c2a93069ad-37fcd9a5225ee0de092b56684be5a37e/48488-20260930T233158Z.log`
- Summary: `/tmp/skill.cncf.d/cncf-sbt-ba1580ae71d203016af3acccc3192db14ed641db52b270a7a16897c2a93069ad-37fcd9a5225ee0de092b56684be5a37e/48488-20260930T233158Z.summary.json`

Owned source SHA-256 identities captured during and after final validation:

| Source | SHA-256 |
| --- | --- |
| GoalPhaseRepairCompletionOperation.scala | `bb5e548e9495b9dc39d77b48c03fa86ee5d12c146f7bb2a35efe7fc8fd40e4de` |
| GoalPhaseManagedRepairPublication.scala | `4496deb8cb48f959daafd1a46b6e01fb182ff7a399d3ff7d99574ed0b8ebdc75` |
| GoalPhaseManagedRepairCompletionAssembly.scala | `edcc13df06deb5dfe7087e1a3201b44f4257d11617d6cd557679747977fc3461` |
| GoalPhaseManagedSuspensionOperationSpec.scala | `01108f49af96e4fca4c8f1b3af09d28c55e1b8968a78891ea34f4e24b4f3b073` |

The consumed CNCF SNAPSHOT is unchanged at
`2c4a0f75d1e8ce4241deaa14d13ff5b1bdd03983546a0eb83878ac158332e2b1`.
No producer refresh was needed. `git diff --check` passed. The compiler reports
an unchecked generic WorkOrder pattern warning in the new admission, consistent
with existing typed adapters; this is not claimed as warning-free compilation.

No live SBT remains. No independent review, full-suite/CLI/process validation,
acceptance commit or Phase release was performed. Re-review WorkOrder issuance
must next recover from retained RepairStepResult and read authoritative repaired
artifact bytes, preserving candidate/evidence freshness and exact-retry semantics.
The production default factory, public advance and complete service composition
are still unfinished. Keep the original Phase 2 goal active.
