# Phase 2 runtime entry inventory

Date: 2026-09-29
Status: preparation completed; bounded upstream persistence through S5 validated; production runtime not implemented
Initial scope: sm-workflow-owned preparation; CNCF and ArtScene inspected read-only
Expanded scope: CNCF Phase 88 source implementation authorized on 2026-09-29

## Implementation follow-up

The observations below retain the initial read-only baseline. Following explicit
user scope expansion, CNCF Phase 88 is in progress and now includes
WorkflowInstanceRecordJsonV1 and WorkflowInstanceDataStorePersistence.
The strict codec and borrowed canonical CAS provider passed SQLite close/reopen,
concurrent-writer and fail-closed integrity tests. Related Workflow/datastore
regression 235: 35 suites, 244 successful tests, 0 failures; 7 existing live MySQL
cases canceled and 1 existing pending example were not executed acceptance.
All seven new provider examples ran.

The next source slice binds that provider through canonical Component/Factory
bootstrap, preserving Component-over-Factory source selection including explicit
opt-out. Each create/load/append borrows a fresh managed datastore view within
the owning Subsystem lease; the bootstrap-time view is not retained. Canonical
SystemNode owns the physical resource. Eight bootstrap examples, including
shared-binding concurrent CAS and shutdown/reopen, ran in regression-238:
109 suites, 876 successful tests, 0 failures, 7 existing MySQL cases canceled and
3 existing examples pending. See the
[bootstrap journal](../journal/2026/09/2026-09-29-cncf-phase-88-instance-bootstrap.md).

S3 adds borrowed WorkflowContinuationDataStorePersistence and an explicit
WorkflowInstanceDataStorePersistence.suspendC composition method. Existing
canonical conditional transition commits one instance-history update and the
exact Available Continuation in distinct EntityStore collections together;
canonical Entity CAS owns claim/release/complete. Ten new executable examples
cover rollback, independent SQLite connection races, exact claim tokens,
resource reopen, malformed policy/payload and indeterminate readback. Focused-242
passed 24/24 tests in three suites. Limited recovery resolved regression-243's
initial permission timeout; prerequisite-244 refreshed its stale Cozy classpath.
Regression-245 completed 124 suites: 1024 succeeded, 0 failed, 8 existing live
MySQL checks canceled and 5 existing examples pending, with lock=released. See the
[S3 journal](../journal/2026/09/2026-09-29-cncf-phase-88-paired-suspension.md).
This is record-transaction evidence, not AtomicWorkflowSuspensionEngine or
active UnitOfWork/event enlistment. S3 alone does not publish issued work;
S4 below adds that bounded composition. Retained result/admission evidence,
production Continuation binding and separate-process launcher recovery remain open.

S4 adds WorkflowIssuedWorkDataStorePersistence with explicit Component, completion
Operation and application codec binding. The issued bytes are an immutable
property of the newly created Continuation, committed together with instance
history by the same canonical conditional transition. Its existing issued-work
SPI permits exact reads/retries only, never independent initial issue or retrofit.
Recovery validates exact typed facts and suspension history; claim CAS preserves
the payload through SQLite resource reopen. Nine S4 examples ran; focused-248
passed 34/34 tests in four suites and regression-249 completed 125 suites with
1033 succeeded, 0 failed, 8 existing live MySQL cases canceled and 5 pending.
This is not production claim admission, active UnitOfWork/evidence/completion
atomicity, separate-process restart or downstream artifact consumption. See the
[S4 journal](../journal/2026/09/2026-09-29-cncf-phase-88-issued-work-publication.md).

S5 adds an opt-in canonical existing-successor Update capability and
WorkflowCompletionDataStorePersistence. One transaction guards the instance
and exact claimed Continuation/issued bytes, appends host-owned history, marks
completion and retains typed result/ExecutionEvidence. Recovery validates
canonical bytes and adjacent suspension/completion history without replay,
including after later host progression. Oversized canonical exact guards reject
without truncation. Existing optional dispatch evidence remains optional.
Eight kernel and thirteen Workflow examples ran in focused-254 (77/77 tests,
nine suites). Regression-255 passed 1054 tests in 127 suites, 0 failed/aborted,
8 existing MySQL checks canceled and 5 placeholders pending. Exact receipts,
earlier failures and source hashes are in the
[S5 journal](../journal/2026/09/2026-09-29-cncf-phase-88-guarded-completion.md).

This closes bounded source-level instance/bootstrap, issued-work publication
and guarded result completion, not separate application AdmissionEvidence
payload/scope, active UnitOfWork enlistment, full joint runtime recovery,
production launcher/MCP or management visibility.
The resolved downstream artifact below has not been refreshed and does not
prove consumption of the new provider. Exact evidence and remaining work:
[upstream slice journal](../journal/2026/09/2026-09-29-cncf-phase-88-durable-instance-provider.md).

## Baseline and evidence limits

Latest consumer update (2026-10-01): local refresh-420 and consumer-421 resolve
the corrected pre-core shared-storage framework JAR with SHA-256
`a5677a49439bda7eba06d50cbc2bc80e4c41fc854589227b2a6ecee23aa9c343`.
The actual JAR's default/named/descriptor preparation call sites were inspected;
consumer recompilation and 53 tests in nine suites passed. This supersedes the
404/405 artifact below, not the open production managed-binding or launcher
acceptance requirements. See the
[shared-storage consumer refresh](../journal/2026/10/2026-10-01-shared-storage-consumer-refresh.md).

Current consumer update (2026-10-01): refresh-404 produced the local CNCF
SNAPSHOT, and consumer-405 resolved its actual Ivy JAR with SHA-256
`41f03ec0e9a7092374f84cd18d6843ff6639a61535e1c531eacaa6b6e67349c9`.
The GoalPhase suspension snapshot conformance change and review/repair
regressions passed 40 tests in 4 suites. This supersedes the initial artifact
observation below for current consumer resolution only; it does not prove
production managed binding/bootstrap or launcher/server recovery. See the
[consumer conformance journal](../journal/2026/10/2026-10-01-goal-phase-snapshot-conformance.md).

Phase 1 is completed with 34/34 bounded application/common-contract items and
full-229 (526/526 tests, 56 suites). Its test service assemblies and actual
Codex/Provider comparison are not a production persistent host.

The observed CNCF source checkout is
`/Users/asami/src/dev2025/cloud-native-component-framework`, HEAD
`c4e1b24c134512f848aa11bd42d2664bddbb1396`, clean at inspection. Its
`docs/phase/phase-88.md` (Workflow Server Application Support) and
`docs/phase/phase-87.md` (Workflow Management Web Console) both say `planned`.
Status text alone is not a capability test; the concrete implementation
observations below determine the entry gaps.

The local resolved CNCF artifact remains `goldenport-cncf_3:0.5.3-SNAPSHOT`,
JAR SHA-256
`ecb5ef1f4b6fd2511a9eb833f3dde9c4e1f6a322fb87abf1da8bb3d61f7f48b7`.
This is an independently observed artifact, not proof that the source HEAD
produced it. The JAR inventory includes the provider-neutral instance contract
and Continuation InMemory/LocalJson classes. This inventory does not replace
an executable production-provider/restart test.

ArtScene reference checkout:
`/Users/asami/src/dev2026/textus-art-scene`, HEAD
`b94453729267ea1185bd343fb8d20549d0ffa4f3`, clean at inspection. No upstream
source, build, publication, agent deployment or user configuration was changed.

## Available facilities and remaining connections

| Area | Observed facility | Required Phase 2 connection |
| --- | --- | --- |
| Bootstrap | CNCF ComponentFactory admits generated Workflow metadata and supplies a ContinuationRuntimeSource IoC hook | Configure one admitted durable runtime shared by both modes; do not create a private scheduler |
| Datastore | ComponentDataStore supports local policy, SQLite and SystemNode-managed SQL resource lifecycle | Consume canonical component/store identity and lifecycle; SQLite alone does not persist Workflow instances |
| Workflow instances | WorkflowInstancePersistence defines create/load/append, ABI binding, revision/history and explicit storage/delivery policy | Concrete durable provider, exact binding rehydration, CAS/restart behavior and host configuration |
| Continuations | ContinuationRuntimePersistence.LocalJson persists claim/completion; IssuedWorkOrderPersistence.LocalJson retains issued payload | Agree provider/recovery boundaries with instance/evidence persistence; independent files do not prove a shared transaction |
| Evidence | CandidateAdmissionEvidencePersistence has typed retention and LocalJson | Preserve exact accepted result/evidence and external-effect receipts across recovery |
| Management | Built-in WorkflowComponent queries subsystem.workflowEngine; SubSystem currently creates WorkflowEngine.inMemory | Canonical visibility of the newer independent WorkflowInstancePersistence records, without shadow registration |
| sm-workflow services | Phase 1 fixtures bind generated Operations to explicit host ports | Production service bindings and configured host ports; default ComponentFactory is not these fixture assemblies |
| MCP | Component has mcpReadyServices hook; current sm-workflow returns Set.empty | Expose only admitted bound services after transport-equivalence proof; enabling names alone is insufficient |

Source anchors within the CNCF checkout are
`src/main/scala/org/goldenport/cncf/workflow/WorkflowInstancePersistence.scala`,
`ContinuationRuntimePersistence.scala`, `IssuedWorkOrderPersistence.scala`,
`CandidateAdmissionEvidencePersistence.scala`,
`component/ComponentFactoryWorkflowPart.scala`,
`component/ComponentFactoryStateMachinePart.scala`,
`datastore/ComponentDataStore.scala`, `subsystem/SubSystem.scala` and
`component/builtin/workflow/WorkflowComponent.scala` (all latter paths under
`src/main/scala/org/goldenport/cncf/`).
The instance contract explicitly supplies no storage, migration, lease or
continuation-resume implementation. No concrete class extending that contract
was found in the inspected CNCF main-source tree. The legacy event-driven
WorkflowEngine and the newer persistence records must not be conflated.

## ArtScene reuse inventory

`src/main/scala/org/simplemodeling/textus/artscene/impl/ComponentFactory.scala`
uses the canonical BundleFactory/PrimaryComponentFactory boundary, generated
Aggregate/View/Entity factories and `use_component_application_datastore()`
before aggregate mutations/view searches. It receives provider/reference data
through factory inputs. These are reusable composition patterns, not evidence
that ArtScene owns a generic persistent Workflow host.

`scripts/run-server.sh` delegates to the normal `cncf . server` launcher with
`conf/cncf/assembly-standalone.yaml`. The assembly declares component identities,
configuration and an explicit local-subject/security policy. Reuse canonical
assembly/configuration mechanisms, not ArtScene component lists, facility
policy, notification routing or its security capabilities. No server was started
for this source inventory.

The current sm-workflow `scripts/run-server.sh` builds a JVM classpath and starts
CncfMain; its existence is not durable-runtime proof. Its source-discovery/cache
conventions must be reconciled with the admitted assembly path when Phase 88
is consumed, not bypassed with a second public CLI grammar.

## Entry gates and ownership

The first production runtime slice requires an admitted durable instance
provider/bootstrap binding and a defined recovery boundary for instance,
Continuation, issued work and retained evidence. Exact interfaces must follow
the admitted CNCF contract; this document invents no provider API, JSON schema,
SQL table layout, human completion endpoint or configuration key.

The CNCF Phase 87 management connection gates management acceptance, not every
Phase 2 task. Existing bootstrap/datastore mechanisms can be inventoried and
application-owned configuration/reference contracts can be prepared before it.
Changing generic CNCF provider/bootstrap/management mechanisms is upstream
work; it requires an explicitly expanded implementation scope. Reading that
repository does not authorize editing or publishing it.

Once admitted, sm-workflow owns exact service/host composition, profile policy,
reference data and thin transport adapters. CNCF retains instance/progression,
Continuation and history authority. Aggregate/View holds application-owned
data or derived projections, never a second mutable Workflow progression.

SQLite remains the local physical backend direction, configured through the
Textus/CNCF datastore provider. Do not hard-code a database path or SQL/JDBC in
public skills/CML/domain, or silently select an in-memory fallback when durable
storage is required. Long AI/commands and human waits occur outside short write
transactions. Migration, claim recovery and external-effect reconciliation must
have admitted policies before claiming restart safety.

## First bounded vertical slice

Use GoalPhaseWorkflow first, through the generated `GoalPhaseService`
Operations: `startGoalPhase`, `requestStepClose`, `submitStepReview` and
`submitStepRepair`. Host-owned selection and exact authority are prerequisites,
not fields supplied or fabricated by the semantic worker.

1. Launcher starts an exact human-selected instance in the configured store
   with no server running; a close request issues one typed full-review order.
2. A new process reopens that exact instance and issued order, preserving ABI
   identity, revision and semantic payload without relying on conversation.
3. Exact typed review completion resumes automatic deterministic evaluation.
   Missing commit authority yields Decision; no real Git command is executed.
4. Stale/foreign/replayed completions fail according to the same common guards,
   with no fabricated instance or duplicated effect.
5. Canonical server and MCP repeat the same Operations/outcomes. Shared-store
   concurrency/recovery is proved before simultaneous server/CLI use is claimed.

This safe initial slice need not implement real commit/push or human-decision
resume. Those remain explicit additional acceptance boundaries, not stubbed
success. Production operations for status/history/result and decision resolution
must be consumed or defined/admitted as typed contracts before use; no generic
public `advanceWorkflow` or manual parallel MCP schema is introduced.

Public skills remain `sm-goal-phase`, `sm-split-phase`, `sm-repository-sync`.
They execute only the requested semantic action and return its typed result to
the Host-supplied completion binding. Mechanical progression/command dispatch
is Workflow-owned. Existing `cncf-*` skills and state remain independent.

## Next handoff

Latest storage follow-up (2026-10-01): config decode now defers private resource
acquisition centrally; default/named/descriptor hosts with a trusted persistence
declaration prepare one managed Workflow/EventStore resource before Component
core assembly. Focused-417/418 passed 86/90 tests, with owned/borrowed cleanup.
Canonical runtime integration-419 passed 18 tests, including explicit first
install and owned close/reopen. Those internal runtime specs supply the trusted
declaration; automatic launcher discovery and migrations remain open. Consumer
421 now resolves refresh-420 and passes 53 tests. Earlier Blob/incidental-pool
and stale-artifact observations below are historical, not the current handoff.

2026-10-01 resource follow-up: owned/inherited SystemNode lifecycle passed
focused-413 (5 tests). RuntimeConfig now separates package-scoped validated
Settings from physical store acquisition; the default Subsystem and Metrics
factories use settings-only decoding. Focused-414 passed 49 tests and
focused-415 passed 47, including managed Completion regression. Blob still
creates a settings-only pool, and Generic Subsystem readers remain to be
corrected. No full lifecycle closure or Phase acceptance is claimed. See the
[settings/resource journal](../journal/2026/10/2026-10-01-runtime-settings-resource-separation.md).

2026-10-01 host update: CNCF now has a public programmatic
`SubsystemEventStoreBootstrap` delegating to canonical factory construction.
Focused-412 passed 45 tests, including managed Completion fixtures moved onto
that public path. This is not automatic launcher configuration or real GoalPhase
composition. The canonical-host log has an unresolved pool start/stop mismatch;
attribute it and prove resource ownership before production lifecycle promotion.
The producer changed after refresh-404 and needs a fresh local SNAPSHOT before
downstream consumption. See the
[public host bootstrap journal](../journal/2026/10/2026-10-01-public-eventstore-host-bootstrap.md).

Consume CNCF Phase 88 with producer/consumer executable evidence and an exact
artifact identity, then implement the first local GoalPhase slice above. If
upstream implementation is to be done by this task, add the CNCF repository to
the explicit mutation scope first. That expansion is now recorded above; the
next work is application AdmissionEvidence payload/scope retention and active
UnitOfWork event/data/closing-program consistency/recovery composition,
followed by an exact local artifact refresh and consumer acceptance.
Phase 87 visibility can follow independently.
See the [Phase 2 checklist](../phase/phase-2-executable-specification-checklist.md).
