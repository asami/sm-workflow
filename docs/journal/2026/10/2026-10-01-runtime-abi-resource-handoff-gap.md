# Official runtime ABI resource handoff gap

Date: 2026-10-01
State: scope decision required; sm-workflow Phase 2 remains incomplete

The previous turn changed main runtime catalog/Factory admission and passed
58 tests in eight suites (attempt 467). This turn inspected the build handoff
read-only. No build or product source change was made.

## Evidence and cause

Cozy ScalaGenerator.scala computes canonical Workflow and Candidate-Admission
JSON alongside generated Scala. Its Realm includes
`target/cozy/statemachine-workflow-abi.json` and
`target/cozy/candidate-admission-producer-abi.json` through the official generator
metadata paths. The producer is already responsible for these representations;
sm-workflow must not hand-maintain another serializer or promote test fixtures.

The inspected sbt-cozy 0.1.17 CozyDelegatedGenerator.generate copies generated
Scala, installs component API descriptor, model metadata and generation
provenance, then deletes delegate-work. It does not preserve those two ABI
side outputs or register them as main resources. The corresponding current
sbt-cozy source also has no handoff for those filenames. Current sm-workflow
target contains only test-classpath copies of GoalPhase ABI JSON. The main
compiled descriptors are present, but no official generated JSON resource is
available for the Runtime Catalog loader. Running another unchanged focused
suite would not repair or prove this packaging path.

Inspected paths:
- `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/modeler/ScalaGenerator.scala`
- `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/modeler/StateMachineWorkflowAbiGenerator.scala`
- `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/modeler/CandidateAdmissionProducerAbiGenerator.scala`
- `/Users/asami/src/dev2026/sbt-cozy-0.1.17/src/main/scala/org/goldenport/cozy/CozyPlugin.scala`
- `/Users/asami/src/dev2026/sbt-cozy/src/main/scala/org/goldenport/cozy/CozyPlugin.scala`
- sm-workflow `project/plugins.sbt` selects 0.1.17 by default, with existing
  explicit version override support.

## Proposed bounded dependency work (not admitted or implemented)

Admit `/Users/asami/src/dev2026/sbt-cozy` as an additional mutation repository,
then establish the exact version/consumer binding before edits. Preserve the
official generated sidecars before disposable output cleanup and package them
through Compile/resourceManaged. Couple cache validity to their presence and
the same generated-source epoch, reject ambiguous multiple-source definitions,
preserve canonical bytes, and ensure removed declarations do not leave stale
resources. Avoid a second Cozy invocation or independent CML parsing in the
application build. The resource handoff should be generic, not CNCF- or
sm-workflow-specific.

Executable evidence must cover resource generation, byte equality with the
official producer, incremental regeneration after missing/deleted resources,
stale-output removal, packaged-JAR resource loading on a main-only classpath,
and consumer Runtime Catalog admission. A local SNAPSHOT prerequisite refresh
may follow admitted dependency work; no release coordinate, remote publication,
commit or acceptance is authorized by this proposal.

That repository is outside the existing frozen mutation set (sm-workflow and
the separately admitted CNCF prerequisite). Read-only inspection does not
authorize its mutation. Request a direct user scope decision before editing;
do not fabricate Phase Goal binding or human-authority evidence to bypass it.
The goal is not complete or marked blocked on this first observed scope gate.
Public recovery, trusted runtime configuration/authority, launcher/server parity,
managed external commands and the remaining Phase closure gates remain open.
