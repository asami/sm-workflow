# Normal three-workflow host

The default ComponentFactory selects the native GoalPhase, SplitPhase and
RepositorySync assemblies together. Use the official development CAR/classpath
and the ordinary CNCF launcher. Factory composition does not start a workflow,
install initial records, initialize a second pool or grant a process command.

## Host configuration

Place trusted configuration in the owning host's normal `.textus/config.json`
or assembly configuration, not in an AI result or an Operation argument. The
existing source admission rules for storage/process plans still apply.

| Configuration | Meaning |
| --- | --- |
| `textus.subsystem.storage.plan` | Event policy, Workflow instance/Continuation pair and any purpose-specific Component Create-only collection grants; **not initial record data** |
| `sm-workflow.goal-phase.persistence.*` | The common logical Workflow store, identity, policies and instance/Continuation collections used by all three routes |
| `sm-workflow.host.definition` | Required closed `sm-workflow.three-workflow-host.v1` definition: fixed repository target, Goal input/candidate paths/checks/review/closure policies and RepositorySync input/closure selection/checks/conflict/review policies |
| `sm-workflow.goal-phase.host-records.*` | Goal selection/association/authorization/decision and native plan read destinations |
| `sm-workflow.repository-sync.host-records.*` | RepositorySync selected Start read destination |
| SplitPhase input/context/selection/typed-Start/checkpoint/allocation settings | Existing purpose-specific native read/write/allocation declarations; no legacy entry fallback |
| `sm-workflow.host.evidence.*` | `store-name`, `record-collection` and trusted `issuer` for separately issued host evidence |

`ThreeWorkflowHostParameters.Definition` is the current typed host-definition
schema. Repository roots are absolute/normalized. Nested policy fields are
explicit; unknown fields, including a caller-added grant or consent field, are
rejected. EntityCollectionId labels must obey their canonical syntax (use
underscores rather than hyphens). Configuration must not alias an input purpose
to the Workflow instance/Continuation pair.

## Operations and completion

The three native services publish fifteen workflow Operations: four GoalPhase,
six SplitPhase and five RepositorySync Operations. Phase 2 specifications call
these Operations directly on the configured component/runtime. The following
one-shot Help example documents existing launcher support; complete command/CLI
wiring and skill use belong to Phase 6, not Phase 2 acceptance:

```sh
/path/to/sm-workflow/bin/launcher \
  --classpath-file /path/to/sm-workflow/target/cncf.d/runtime-classpath.txt \
  --main-class org.goldenport.cncf.CncfMain -- \
  --component-dev-dir /path/to/sm-workflow \
  --textus.assembly.descriptor=/path/to/assembly.yaml \
  command meta.help org.simplemodeling.textus.SmWorkflow.SplitPhaseService
```

Prepare the official artifacts through the supported serialized SBT route before
using this command. The launcher itself runs no SBT. `scripts/run-server.sh` uses
the same prepared component; its loopback MCP catalog includes the fifteen
native Operations and six standard tools.

Start uses actual selected host records. Advance owns routing and mechanical
progression; the client returns only the issued typed result to its specified
completion Operation. The client cannot select a cursor/stage, manufacture a
Human answer or convert a claimed review into independent evidence. Execution
permissions for real Git/SBT/document effects remain separate CNCF-managed
process grants.

## Direct Operation caller for Phase 2

Use the owning Subsystem's normal Operation dispatcher, rather than calling an
admission helper or constructing a replacement Workflow runtime. The component
must already have been created by the configured factory after the common
Subsystem storage/process bootstrap. Resolve the current caller's ingress
ExecutionContext normally; `ExecutionContext.create()` alone is not an
authenticated caller or an effect grant.

The direct call used in the executable specification is:

```scala
import org.goldenport.protocol.{Argument, Request}

val response = owner.executeOperationResponseInContext(
  Request.of(
    component = component.name,
    service = "SplitPhaseService",
    operation = "advanceSplitPhase",
    arguments = List(Argument("arg1", advanceWire))
  ),
  actor
)
```

Here `owner`, `component` and `actor` are the actual configured Subsystem,
component and ingress-resolved caller. `advanceWire` is the complete command
encoded by `SplitPhaseAdvanceCommandJsonV1`; it is not a shell command. The
dispatcher constructs the actual owning ActionCall and executes its normal
commit/after-commit response path. No launcher process or command executor is
needed merely to invoke this Operation. A Scala `CommandAction` is the CNCF
Operation execution abstraction, not evidence that the caller started a CLI.

Each of the fifteen published Operations accepts one JSON string in `arg1`:

| Service | Operation | Input encoder / payload codec |
| --- | --- | --- |
| GoalPhaseService | `startGoalPhase` | `GoalPhaseStartServiceOperation.encodeCommand` |
| GoalPhaseService | `advanceGoalPhase` | `GoalPhaseNativeServiceAssembly.encodeAdvance(instanceIdentity)` |
| GoalPhaseService | `submitGoalPhaseWorkResult` | `WorkflowResultJsonV1` with `GoalPhaseNativeWork.resultCodec` |
| GoalPhaseService | `submitGoalPhaseOrderedDecision` | `WorkflowResultJsonV1` with `GoalPhaseOrderedHumanDecision.answerCodec` |
| SplitPhaseService | `startSplitPhase` | `SplitPhaseStartServiceOperation.encodeCommand` |
| SplitPhaseService | `advanceSplitPhase` | `SplitPhaseAdvanceCommandJsonV1.encodeC` |
| SplitPhaseService | `submitPartitionSemanticResult` | `WorkflowResultJsonV1` with `SplitPhaseExplicitPartitionSemanticJson.resultCodec` |
| SplitPhaseService | `submitHandoffScopeResult` | `WorkflowResultJsonV1` with `SplitPhaseScopeSemanticJson.resultCodec` |
| SplitPhaseService | `submitSplitHumanDirection` | `WorkflowResultJsonV1` with `SplitPhaseHumanDirectionAnswerContract.payloadCodec` |
| SplitPhaseService | `submitReasoningResult` | `WorkflowResultJsonV1` with `SplitPhaseExplicitReasoningSemanticJson.resultCodec` |
| RepositorySyncService | `startRepositorySync` | `RepositorySyncStartServiceOperation.encodeCommand` |
| RepositorySyncService | `advanceRepositorySync` | `RepositorySyncManagedServiceAssembly.encodeAdvance(instanceIdentity)` |
| RepositorySyncService | `submitRepositorySyncWorkResult` | `WorkflowResultJsonV1` with `RepositorySyncConflictSemantic.resultCodec` |
| RepositorySyncService | `submitNativeMergeReview` | `WorkflowResultJsonV1` with `RepositorySyncNativeMergeReview.resultCodec` |
| RepositorySyncService | `submitRepositoryConflictDecision` | `WorkflowResultJsonV1` with `RepositorySyncHumanConflict.answerCodec` |

These encoder names describe the existing in-component implementation and its
executable specs; the `private[smworkflow]` admission helpers are not a new public
Scala library API for skills. The published service/Operation names and typed
wire contract are the external interface. Transport adapters can use that
interface in Phase6 without requiring the skill to dispatch internal stages.

GoalPhase and RepositorySync advance frames contain only `schemaVersion` and
`instanceIdentity`. Their schemas are respectively
`sm-workflow.goal-phase-advance.v1` and `sm-workflow.repository-sync-advance.v1`.
SplitPhase advance additionally carries its typed partition context reference.
Do not add a stage, cursor, winning candidate, argv, storage plan or permission
to any advance command. The saved input and native state choose the next action.

For a completion, retain the handle, run/continuation identity, expected revision,
context snapshot, completion Operation and assigned result reference from the
issued WorkOrder or Decision. Return the requested typed judgment/result and its
evidence in a `WorkflowResultJsonV1` frame. The existence of a JSON frame neither
authenticates its participant nor creates independent review/Human evidence.
For Phase2 those evidence records may be explicitly specification-owned input
fixtures, prepared through the declared managed record grants.

Successful responses are JSON strings in `OperationResponse.Scalar`. Decode
an actual returned Wait, WorkOrder, Decision or Terminal with the corresponding
Workflow protocol codec and payload type. Result-submission Operations may return
an Operation-specific acknowledgement envelope instead (for example the
SplitPhase Human submission includes its issued Decision and retained completion);
do not assume every response is a Wait frame. A completion acknowledgement is not
whole-workflow completion: call the owning advance Operation again to let the
Workflow absorb mechanical progression. Acceptance requires the selected
scenario's actual final outcome/effects and fresh-runtime recovery, not merely
a successful dispatcher call or a Wait response.

Ordered GoalPhase semantic decisions use their own Required SPI,
`GoalPhaseOrderedHumanService.presentOrderedGoalDecision`, not the older
Step-closure Human input. The issued input includes the original assigned work,
actual requesting result/reference and decision reason. Its answer contains
`choice` (`ResumeAssignedWork`, `Defer` or `Cancel`) and a nonempty `rationale`,
with the common result envelope and separately supplied host Human evidence.
Completion records direction only. Owning advance may reissue the same semantic
work at a new generation with that rationale, preserving Plan/base/closed Steps
and repair allowance; an answer cannot supply commands, accepted review, a new
Plan or a refunded repair. Defer/Cancel latch without new work or mutation and
do not falsely complete the Phase. Unknown native effects cannot use this
semantic-resumption path; observe them before any separate recovery action.

Current direct Operation evidence covers SplitPhase semantic completion/Preview/
Apply/reopen, practical RepositorySync convergence and conflict/Human handling,
and complete ordered GoalPhase closure with semantic Human direction.1098 passes
all four connected GoalPhase cases and selected ABI consumer/catalog checks.
These are explicitly specification-owned inputs/evidence, not real Human issuer
or installed skill-client proof.1100 additionally passes actual RepositorySync
SBT success/failure and fresh-runtime receipt retention using the
[fixed SBT adapter](sbt-validation-runtime.md).1101 additionally passes actual
GoalPhase Step/Phase/RepositorySync SBT checks, reviewed Step commit, whole-Phase
convergence and fresh-runtime no replay (3/3 in the combined suite).
Git recovery now also passes1104: commit, non-force push and final fetch complete
in real owned repositories, but their caller JVM is terminated before receiving
the managed result. A fresh JVM's public advance observes the retained intent
and actual repository postimage, reaches convergence without repeating that
effect and retains exact terminal/history on another reopen. This covers these
three known postimages; it does not authorize replay of an unobserved effect or
claim recovery of unknown product checks/document writes. Required
remaining practical recovery/breadth, integrated regressions and independent
acceptance remain open. See the existing acceptance table; no Phase acceptance is claimed.

1106 additionally passes all nine direct SplitPhase examples. Scope Enriched,
or scope DecisionNeeded followed by verified Human ProvideResolution, progresses
through assigned unknown-only reasoning to complete Preview. Human Defer/Cancel
produce their retained typed stopped terminal without reasoning or document
writes. Pending scope/Human/reasoning and final state reopen in separate JVMs.
An exact scope result submitted again observes the original canonical completion
and returns its historical acknowledgement, including after terminal progression;
it does not attempt another adjacent completion. Changed duplicates, crossed
references and answers without separately supplied host evidence remain refused.
The Human resolution input and checkpoint destinations must be separately
declared in trusted configuration via
`sm-workflow.split-phase.human-resolutions.*` and
`sm-workflow.split-phase.human-checkpoints.*`; they neither
seed records nor issue Human evidence. This proves these selected successful
reasoning successors, not every semantic DecisionNeeded successor or uncertain
document effect. The changed completion still requires integrated regression
and independent acceptance.

Direct invocation avoids caller CLI startup; it does not remove actual managed
external effects inside a Workflow. Git integration, selected product validation
and document writes must still execute through their configured CNCF runtime
programs, on spec-owned repositories/remotes/files with bounded grants. A test
must not return canned command success or use a direct SQLite write in their place.

## Current evidence adapter and Phase 2 Operation verification

`NativeHostEvidence` reads the `nativeHostEvidencePayload` framed record through
the owning incoming DataStoreSpace and clock. Records bind a declared purpose,
lookup identity, reference/revision, actual issuer/participant, complete current
typed input/result and validity interval. The record codec is passive: encoding
JSON is not issuing evidence. No public completion Operation writes or attests
these records. Missing evidence remains absent rather than creating consent,
review acceptance or an authorization.

Declare each configured purpose-specific record collection in the normal storage
plan's Component Create-only grants, with the component owner and the same
logical store as the Workflow pair. Keep those collections distinct from the
Workflow instance/Continuation and Event collections. The normal-host executable
example declares its selected purpose-specific destinations, including separate
Human resolution and checkpoint destinations. That selected collection set is
not a universal fixed number or a record-population mechanism.

For Phase 2, the public Operations must work through direct Operation calls with
the Executable Specification as caller. Command/CLI wiring and actual skill
integration are Phase 6. The
spec may supply declared test-owned input/evidence through managed storage.
That setup is not a real Human/reviewer issuer and must not be presented as one;
it also cannot replace the public Operation implementation, its permissions, real
effects or saved Workflow progression. No separate deployed host/client or
GitHub integration is required to run these specs. Assess the current adapter's
record-only assumptions against this boundary and the single-user typed policy
rather than requiring another application to make Phase 2 executable.

The tested SplitPhase Preview/Apply/reopen examples support their selected
scenarios, not completion of all required breadth or Phase acceptance. The storage
plan's `records` array grants Create-only storage; it does not itself seed input
or Workflow instances. Do not write SQLite directly, fabricate Workflow history
or substitute a private runtime for Operation execution. Normal Help/MCP/refusal
evidence alone is not positive workflow or Phase acceptance. Follow the
[current acceptance table](../phase/phase-2-executable-specification-checklist.md)
and [resumption journal](../journal/2026/10/2026-10-07-optimized-plan-resumption.md)
for admitted results and remaining work.
