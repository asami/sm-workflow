# Managed GoalPhase four-Operation service

Date: 2026-10-01
Phase: sm-workflow Phase 2
State: IMPLEMENT

Current CML declares startGoalPhase, requestStepClose, submitStepReview and
submitStepRepair, not public advance. Runtime-entry inventory explicitly forbids
inventing public advanceWorkflow or a parallel MCP schema. Correct the preceding
next-action wording accordingly: internal advance exists, but exposing restart
recovery needs an admitted typed management contract. This does not block the
already-declared managed repair completion joining the main Service assembly.

Retain explicit parent-direct limited recovery. Own
GoalPhaseManagedServiceAssembly.scala, GoalPhaseManagedSuspensionOperationSpec.scala
and this journal. Keep the three-Operation review-slice entry compatible. Add a
four-Operation repair-slice entry with explicit artifact-bound repair policy and
event preparation. Fail closed on incomplete ports, retain generated names/order,
and bind every operation through the existing managed assemblies. Do not create
storage/pool/SQL, schema/CML, public management API, default factory wiring or
commit changes. Preserve all unrelated work.

Run existing host Start/issue/review/reopen behavior through the new full service
composition; retain old review-slice admission refusals and add missing repair
ports and mismatched producer refusal. Existing managed repair/re-review behavior
remains regression evidence. Validate the same six suites through the serial
wrapper. Automatic follow-up/recovery and production/transport acceptance remain
required; this composition is not completion of the Phase.

## Implementation and focused evidence

Added bindRepairSliceC with explicit RepairPorts. It requires the same admitted
producer as the review ports and nonnull event preparation, then binds the
existing managed repair completion assembly and verifies all four generated
Operation names/order. bindReviewSliceC remains a compatible three-Operation
entry through the shared private composer. No ad-hoc public advance was added.

The real fixture Component calls the four-Operation composer in create_Core;
existing normal Start/close/review dispatch, reopen and replay assertions now
exercise that composition. Missing repair ports/events or producer are refused.
The modified behavior has adjacent Given/When/Then clauses. Other managed and
legacy repair/review suites remain regression evidence; this test does not claim
that full-service automatic repair/re-review follow-up is already wired.

Invocation `SMWF-MANAGED-FOUR-OP-SERVICE-DIRECT-20261001-460` passed 51 tests
in six suites, zero failures/aborts, SBT exit 0, wrapper exit 0 and lock released.
The persistent summary was read after terminal completion. No live SBT remains.

- Log: `/tmp/skill.cncf.d/cncf-sbt-265f81eb4d81906394e3498d7b9422a369586b106156663874a5acbc593046d6-3d54f12d7a4f85065afab0bb900a87c6/57935-20260930T234415Z.log`
- Summary: `/tmp/skill.cncf.d/cncf-sbt-265f81eb4d81906394e3498d7b9422a369586b106156663874a5acbc593046d6-3d54f12d7a4f85065afab0bb900a87c6/57935-20260930T234415Z.summary.json`

Owned byte identities captured during/after validation matched:

- GoalPhaseManagedServiceAssembly.scala: `c3fb9ddc65b702c87b14a2a4c93f1fcdedcec45b8a713e573519cf29bdd65e64`
- GoalPhaseManagedSuspensionOperationSpec.scala: `3af3647ff38ad630dafe46e0269233744d4def228dca6a33259c754fde5cbb51`

`git diff --check` passed. No producer refresh, source mutation outside this
repository, commit, full suite, independent review or Phase closure ran.
Next wire workflow-owned managed follow-up to these declared completion
Operations, retaining internal restart recovery instead of a callback-only
implementation. Public recovery still requires the admitted management contract;
production default factory, CLI/server/MCP, authority/command adapters and all
Phase acceptance gates remain unfinished.
