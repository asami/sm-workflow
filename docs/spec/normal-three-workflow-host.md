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

### H8 — Read declared host input, not a serializer-body certificate

The `sm-workflow.three-workflow-host.v1` producer retains its existing fields.
Its reader checks the declared nested object fields and decodes field types
once. It does not re-encode the decoded Definition to prove equality with the
complete input. Subsequent host assembly still resolves actual executable
targets and validates its policy, scope and authority; successful decoding
alone grants no command or Human permission.

Executable examples in `NativeHostEvidenceSpec`:

- H8-1: the ordinary Definition and a repeated array presentation of an existing
  `semanticPaths` Set decode to the same typed meaning. A caller-added grant is
  refused. Set normalization is not proof that input stayed unchanged.
- H8-2: extra or missing fields at every nested object, a wrong schema, a wrong
  primitive, null policy and wrong Set element types are refused by declared
  shape/type admission, without a full re-encoding comparison.

These examples are authored but UNVERIFIED until current-input runner execution;
they do not establish whole-Phase removal or actual host/executor acceptance.

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

SplitPhase's v1 decoder consumes its declared instance/context fields rather
than requiring whole regenerated-JSON equality. Unused diagnostics, including
extra root/payload/context members, do not reject resumption and never select a
stage, action, command or storage target. Missing or wrongly typed consumed
fields still fail conversion; the genuine managed Operation resolves actual
context/Start/participant/native state. The connected diagnostic-tolerance,
foreign-target and SQLite/separate-JVM reopen cases were extended on 2026-10-09.
Fresh Actual106 verifies40/40 cases in four whole suites. Its independent
review found only newly introduced local helper naming; the later M0 rename
and references were checked separately without changing executed behavior or
claiming post-rename compilation. Original source FINDINGS is preserved, not
called independent PASS. Earlier Actual105 does not cover these subsequent
edits, and neither result completes the remaining Phase5 interface. See the
[advance input removal record](../journal/2026/10/2026-10-09-split-advance-unused-input-removal.md).

The codec also no longer constructs a prospective physical planning-index key
or repeats generated shape admission after typed decode. Required generated
producer fields remain present and logical strings remain unnormalized data;
actual context/Start/native consumers resolve meaning, while actual physical
index read/publication enforces its bounded locator at consumption. Fresh
Actual107 verifies28/28 codec+completeDirect cases and independent source PASS:
a long absent context reaches owning lookup without changing pending state.
This does not imply oversized keys work in real planning-index access or close
all remaining checks/session/AI execution/full Phase5 obligations. See the
[physical-key boundary record](../journal/2026/10/2026-10-10-split-advance-physical-key-boundary.md).

GoalPhase and RepositorySync advance now likewise consume schema and the actual
String instance without refusing unused root fields or pre-admitting logical
identity canonicality. They do not interpret extra stage/argv/receipt/permission
fields. Native Start/host/participant/current instance remain actual consuming
boundaries. Split also removes the speculative16KiB/whole-String UTF8 preflight
and does not check the prefix its encoder just produced; natural parser/typed
fields/published prefix and actual physical/transport/resource owners remain.
Fresh Actual108 verifies46/46 across five whole suites with independent source
PASS. Genuine decorated Goal/Repository progression, missing native target/
unchanged original state, and large-diagnostic pending Split/fresh/JVM behavior
remain covered. This is bounded advance consumption, not session association,
actual AI execution/result or full Phase5 acceptance. See the
[three-advance correction record](../journal/2026/10/2026-10-10-three-advance-consumed-input.md).

### Result submission without echoed control context (Phase5, scoped validation)

The normal three-service assembly additionally accepts
`schemaVersion: "sm-workflow.result-submission.v1"` at all nine result-submission
Operations. Submit session context or the necessary issued request selector,
`result.value`, `completionFacts` and `executionEvidence`. Partition, handoff-scope
and reasoning semantic completions use their actual saved WorkOrder destination
internally; an echoed `result.reference` is not required or consumed there.
Other completion Operations still consume an actual result/evidence reference
(`identity`, `revision`) under `result.reference`; it is not invented from an
issued question. The value uses the selected Operation's
payload codec listed above. Facts and evidence retain the Workflow protocol
representation. Omitted facts mean none; omitted evidence means no observed
executor evidence, never invented worker/model/context identities.

No handle, run identity, expected revision, context snapshot or result-type echo
is required. The server reads the actual saved WorkOrder or Decision identified
through session resolution or the necessary request identity using that provided Operation's managed issued-work
binding. It supplies internal context to the existing owning completion.
Result meaning, actual reference/scope, current state, authority and atomic CAS
remain with that completion. The wrapper creates no verifier, comparison record,
claim, authorization or alternate transition path. Legacy complete-envelope
input still uses the native decoder; the table above describes that encoding.

Request identity disambiguates an actual issued generation, including a delayed
answer; it is not authorization. Normal session association and targeted result
submission have scoped direct Operation evidence (Actual11569/69; subsequent
assigned-destination follow-through Actual11634/34). Neither infers a Codex chat
identity or proves actual AI execution, all R1-R5/E1-E12 cases, or full Phase
acceptance. See [the connected session contract](session-workflow-context-resolution.md)
for exact remaining association, ambiguity, concurrent-update and consumed-input
obligations.

The existence of a JSON frame neither
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

Native Goal/Repository Human questions now recover original saved meaning and
presentation without whole regenerated-question equality. Actual assignment,
requesting-result/native target, executable route/type/model and owned slot
remain admission inputs. Managed completion supplies committed state directly;
its acknowledgement does not reread full question/answer/source copies. A
retained retry consumes the saved answer, ignores explanation-only differences
and rejects a substituted choice/literal resolution. Repository Human/conflict
v1 readers admit declared nested structure and optional defaults without
re-encoding equality. See [Human question applicability](native-human-question-applicability.md).
These changed Operation/spec inputs are UNVERIFIED. The separate independent
Goal/Repository/Split Human host attestation has a selected-target correction
below; native Split Human question/completion gates still require correction.

## Current evidence adapter and Phase 2 Operation verification

`NativeHostEvidence` reads the `nativeHostEvidencePayload` framed record through
the owning incoming DataStoreSpace and clock. Records bind a declared purpose,
lookup identity, reference/revision, actual issuer/participant, selected effect
scope or semantic attestation, and historical occurrence time. The record codec is passive: encoding
JSON is not issuing evidence. No public completion Operation writes or attests
these records. Missing evidence remains absent rather than creating consent,
review acceptance or an authorization.

The configured factory installs Reader-backed Human/review EvidencePorts as
trusted component dependencies. Public requests cannot supply such a port or
turn authentication into its successful admission. These ports return the
host boundary's outcome, not an internally rechecked receipt. Independent
record-read permission, current declared issuer/purpose, actual target/reference/
actor and occurrence are checked by Reader. The direct-operation host-boundary
example exercises a physical stored row, stale construction-time issuer versus
current authority, absent/foreign evidence and future occurrence. This is
UNVERIFIED test code and specification-issued input, not a genuine Human channel.

The Phase 5 migration writes `sm-workflow.native-host-evidence.v7` records without
`validUntil`. Its reader recognizes stored v1/v2/v3/v4/v5/v6 records, retains their actual scope,
reference, issuer/participant and occurrence time, and disregards the obsolete
optional expiry field. It does not renew authority or rewrite the stored row.
Current v7 input rejects undeclared fields, including `validUntil`; recognizing a
legacy expiry does not allow other caller-added claims. Applicability depends on
the actual current call and its purpose/binding, not elapsed time. A future
occurrence time remains a contradictory input, not a TTL.

Step/Phase closing and Repository effects now store actual selected target
facts, not full Plan/Start-command copies. Current producers/readers,
legacy extraction and actual call consumers use that same scope contract.
They retain actual invocation/participant/native frontier/candidate/chain/review/
effect/physical target and issuer relationships. Review attestations separately
retain actual candidate/assignment/model/result/reviewer/disposition without
Plan/policy/diff/log/transport certificates; WorkRequest v2 removes review policy
markers and v1 reads discard them. Split Human answers select original Scope or
Reasoning targets without full question/frontier/Result copies. This follow-through
is UNVERIFIED; neither prior Phase2 nor focused expiry PASS covers it. See
[native host authority applicability](native-host-authority-applicability.md).
Review-specific coverage and pending checks are in
[native host review applicability](native-host-review-applicability.md).

Goal/Repository Human evidence stores the actual issued question target,
submitting participant/Human worker, requesting result/native assignment and
choice/literal resolution, not full question/result/Plan/context/explanation
copies. Human EvidencePorts delegate to Reader's single host-input admission;
no copied receipt, issuer getter or second target/time check returns to consumers.
Selected scope is produced once and actual-call admission remains.
V1–v6 extraction preserves original meaning/time without rewrite or renewal;
v7 rejects reintroduced copies. Independent Human ingress still remains separate
from effect permission and completion cannot provision these records. See
[native Human applicability](native-host-human-applicability.md). Changed
codec/Operation/native parent-child inputs are UNVERIFIED; native Split Human
question/history/completion gates and broader connected acceptance remain open.

Split Start independently attests actual command/mode/actor/invocation and its
Workflow/source-authority/selected-proposal target, not provider configuration,
complete selection/recommendation or proposal-child copies. The normal Reader
admits independently stored evidence using the current host declaration and call
clock; the Start assembly has no separate verifier receipt or validity clock.
Execution takes the admitted original prepared
Start rather than a second argument whose complete body must match again.
V1–v6 readers select original consent without decoding unused copies or renewing
it; authorized Human relays and actual-call/state/CAS/scope/permissions remain.
See [Split Start applicability](native-host-split-start-applicability.md).
Source/spec changes are UNVERIFIED. Split preparation/planning retained-body
gates and native Human completion still require connected correction.

SplitPhase Start/answer and RepositorySync conflict-answer host records likewise
have no mandatory expiry. Authentication, actual participant/owner/purpose and
the genuine issued question/result relationship remain required. Expiry removal
does not extend an ActionCall's managed storage lifetime, forgive an unrelated
answer or waive independent host verification. This connected seam has normal
generation and focused Operation/codec regression evidence; it is not Phase 5
acceptance or evidence that
the separate legacy Step/review/closure contracts have all migrated. The older
GoalPhase Human-answer storage/public codec likewise writes its expiry-free v2
receipt and recognizes historical v1 answers without expiry evaluation. Its CML
receipt declaration no longer requires `validUntil`; normal producer generation
and the affected focused specifications have run. Legacy decoding is structural and cannot itself
issue Human admission or change the retained question/answer.

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
