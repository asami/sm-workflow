# SplitPhase public advance: managed mechanical transition prerequisite

Date: 2026-10-02
Product scope: original sm-workflow Phase 2, with admitted CNCF Phase 88
Status: producer prerequisite implemented and focused-validated; consumer integration and independent acceptance open

## Continuity and validated predecessor

The user requested `テスト設定を補正して続けて`. The preceding
[issued Reply unit](2026-10-02-split-phase-issued-reply-contract.md) removed
fixture result allocation maps and made result fixtures consume the Reply in
the actual issued WorkOrder. Validation 793 passed runtime preparation,
packaging and 433/433 tests in 67 suites, SBT/wrapper exits 0/0 and
`lock=released`. Its actual summary was read again; this is reuse of existing
evidence, not another invocation or validation of future source.

The same original Goal now reports active, with createdAt 1790766223 and the
unchanged original cncf-goal-phase objective. The preceding journal's blocked
observation remains historical. No replacement Goal, new Phase, base selection,
review epoch or acceptance is created. Retain the explicit user-directed
parent-only limited recovery. Missing original typed authority/base/binding,
PLAN-owner and review records are still assurance gaps, not missing generic
permission and not evidence that can be reconstructed from current HEAD.
This document is a bounded design handoff, not an accepted ordinary Cycle Plan.

The initial PLAN turn changed only this journal and the Phase 2 projection. No main/Test
source, upstream repository, shared skill or Git index/history is changed.
No SBT process or agent was started in that turn. The cncf-goal-phase PLAN separation keeps
implementation and validation for the next work state; it does not waive the
original independent reviews, Step commits or Phase release requirements.

## Observed integration gaps

The default `impl/ComponentFactory.scala` delegates to
`GoalPhaseLauncherAssembly.factory`. `GoalPhaseConfiguredComponentFactory`
binds one ServiceDefinition after initialization parameters are resolved.
Typed SplitPhase services are not installed by that default route.

`SplitPhaseExplicitPlanningAdvance` already owns deterministic enumeration,
allocation continuation, projection, candidate admission and objective routing.
It is a private pure kernel, not a generated Operation or execution grant.
`SplitPhaseExplicitPartitionContext.prepareC` currently requires a
PartitionSemanticRequired outcome; an all-known planning selection cannot use
that semantic-only context preparation as though it were a WorkOrder.
`SplitPhaseCandidateAllocationPolicy` currently anchors new references to a
semantic ResultReference. An all-known route has no such result.

CNCF `WorkflowComponentStorage` binds instances, issued WorkOrders/Decisions
and their completions. `WorkflowManagedCompletion` requires an actual issued
interaction/result, and `WorkflowClosingProgramPreparation` requires a typed
Completed ActionExecution. Ordinary instance append does not atomically create
the application planning rows. None is a correct mechanical-only save API.
Separate append/Create calls or an invented AI result would violate the
required transaction or semantic boundary.

## Decision: generic managed mechanical transition

Add a CNCF mechanical-only storage capability before wiring public advance.
Use the existing Workflow owner, Instances root and Continuations successor
collection in the same physical DataStore. The successor is a distinct typed
mechanical checkpoint receipt, not an AvailableContinuation, WorkOrder,
Decision or submitted AI result. The collection declaration does not imply
that every stored row represents an issued interaction.

Factory-time composition remains metadata-only through
`WorkflowComponentStorage.bindMechanicalC`. Physical access occurs inside the
incoming ExecutionContext via `Subsystem.withWorkflowVerifiedMultiGuardSpaceC`.
Borrow its existing DataStore/provider and release the call-scoped lease on
every exit. Do not allocate a pool, decode config as authority, access SQLite
directly, retain a provider/space across calls or substitute memory storage.

The proposed public host capability is `WorkflowManagedMechanicalTransition[A]`:

- `executeC(intent, program: ExecUowM[Unit], records)` performs one mechanical
  transition under the bound component, configuration, operation and codec.
- `loadRetainedC(reference)` observes the exact retained receipt in a fresh
  authorized call. It does not retry execution, claim work or allocate data.
- Completed/Retained witnesses are privately constructed only after canonical
  storage verification. Decoded receipt data alone is not such a witness.

The typed intent identifies an explicit checkpoint reference/revision, the
complete expected current instance, the one requested next history entry and
the application payload. The bound host validates the exact configuration,
catalog, instance/definition ownership, revision, correlation and causation.
Initial scope is Active, unsuspended current and next states, with exactly one
ordinary append; terminal states and interaction suspension are not admitted.
Application Workflow logic chooses the transition, not a skill or AI worker.

The adapter compares the complete actual current instance with the supplied
expected instance, constructs the next record through normal append admission,
and builds an effect-free conditional pair: native instance revision update
and immutable checkpoint Create. It preserves configuration guards and the
native physical revision convention. No unconditional instance overwrite is
introduced.

`WorkflowMechanicalProgramPreparation` captures Create-only local intents from
ExecUowM[Unit]. It rejects guards and every other UnitOfWork opcode before
effect interpretation, with at most 64 Creates and no duplicate targets. It
does not fabricate Completed(StateMachineOperationResult). Native host policy
still checks actual captured per-collection grants, reserved targets and
combined plan limits. This closed interpreter is not an arbitrary command or
application-code sandbox.

Attach captured Creates to `WorkflowPreparedDataStoreTransition.hostParticipantC`
and commit through `ManagedCompletionUnitOfWork`. Instance, checkpoint,
candidate documents and selected durable EventRecords share that native commit.
Return success only for the canonical Verified outcome after whole native
post-image readback, including every created application row. No direct
prepared-transition commit or split write is an alternate production route.

EventStore continues selecting persistence by event kind. Ordinary event
records require no caller persistence flag; transient delivery and rollback
follow the existing managed completion contract. No EventStore policy,
Subsystem admission, SQL transaction implementation or provider change is
needed for this prerequisite.

## Receipt and recovery contract

Use a closed version-1 mechanical receipt codec, not a semantic-result codec.
Its complete record includes explicit reference, component, operation,
original current instance, exact appended next instance and typed application
payload. Reuse the existing catalog-validated instance codec. Reject unknown
fields, malformed revisions, mismatched payload type and invalid ownership or
history. The application later verifies its full payload and every retained
document against independently read current inputs.

Use a separate `mechanicalPayload` storage field, with the existing stored
configuration and native revision fields. Physical checkpoint keys are
`mechanical:<escaped identity>@<escaped revision>`: percent-encode UTF-8 bytes
outside ASCII unreserved characters, including `%`, `:` and `@`; use uppercase
hex. Reject empty/noncanonical logical references and keys over 1024 UTF-8
bytes; reject receipt encodings over 1 MiB before effects. These are explicit
bounds, not management hashes or implicit latest-record selection.

The prefix distinguishes normal usage, but is not an exclusive namespace
grant: any occupied target, including an older continuation, refuses Create
atomically. Never overwrite, redirect, choose another identity or retry with
a new key after a conflict. Existing continuation/semantic receipt formats
and their key recipes remain unchanged.

Repeated execute with a stale root or occupied checkpoint refuses without
partial publication. After an uncertain commit, return the actual indeterminate
outcome; do not automatically resend. A later explicit observation reads the
exact selected checkpoint and verifies original history as a complete prefix
of the current instance, preserving legitimate later history. Missing,
divergent, malformed or foreign records refuse usable recovery. Recovery must
not allocate, invoke AI, rewrite history or reconstruct authority from payload.

## Frozen next implementation frontier

All paths below are relative to the already admitted CNCF repository
`/Users/asami/src/dev2025/cloud-native-component-framework`:

1. Add `src/main/scala/org/goldenport/cncf/workflow/WorkflowMechanicalTransition.scala`.
2. Add `src/main/scala/org/goldenport/cncf/workflow/WorkflowMechanicalTransitionJsonV1.scala`.
3. Add `src/main/scala/org/goldenport/cncf/workflow/WorkflowMechanicalProgramPreparation.scala`.
4. Add `src/main/scala/org/goldenport/cncf/workflow/WorkflowMechanicalTransitionDataStorePersistence.scala`.
5. Add `src/main/scala/org/goldenport/cncf/workflow/WorkflowManagedMechanicalTransition.scala`.
6. Modify `src/main/scala/org/goldenport/cncf/workflow/WorkflowComponentStorage.scala`, adding only the composition binding.
7. Add `src/test/scala/org/goldenport/cncf/workflow/WorkflowMechanicalTransitionSpec.scala`.
8. Add `src/test/scala/org/goldenport/cncf/workflow/WorkflowMechanicalProgramPreparationSpec.scala`.
9. Add `src/test/scala/org/simplemodeling/cncfclient/WorkflowMechanicalTransitionSpec.scala`.
10. Update `docs/phase/phase-88.md` with the bounded implementation evidence.
11. Add `docs/journal/2026/10/2026-10-02-managed-mechanical-workflow-transition.md`.

Consumer evidence paths are this journal and `docs/phase/phase-2.md`. Preserve
all unrelated dirty/untracked files. The public consumer Operation/CML,
default Factory and mechanical allocation anchor are subsequent integration
frontiers, not edits silently admitted into this producer unit. If a necessary
subordinate path emerges, record its exact causal admission before editing.
Out-of-workspace edits require actual platform permission; this plan does not
assert that permission was granted. No agent or upstream mutation occurred here.

## Executable specification and validation budget

Pure specs cover closed codec, identity bounds, active/unsuspended one-append
admission, complete instance comparison and Create-only program rejection.
Native specs outside private CNCF packages exercise actual configured
Service/CLI dispatch and the shared SQLite provider: whole atomic success,
missing/foreign grant refusal, occupied checkpoint/document targets, no partial
writes, rollback, readback uncertainty, physical reopen, full-row tampering,
later-history preservation and expired/undeclared call denial. Mere direct
provider fixtures do not establish the managed public host path. Every new
behavior block has adjacent Given/When/Then and should matchers.

Proposed producer accumulator, once the implementation frontier is complete:

```text
--batch
testOnly org.simplemodeling.cncfclient.WorkflowMechanicalTransitionSpec org.goldenport.cncf.workflow.WorkflowMechanicalTransitionSpec org.goldenport.cncf.workflow.WorkflowMechanicalProgramPreparationSpec org.simplemodeling.cncfclient.WorkflowClosingCreateSpec org.simplemodeling.cncfclient.WorkflowComponentStorageSpec org.goldenport.cncf.workflow.WorkflowClosingProgramPreparationSpec org.goldenport.cncf.workflow.WorkflowPreparedDataStoreTransitionSpec org.goldenport.cncf.workflow.WorkflowManagedIssuedWorkSpec org.goldenport.cncf.workflow.WorkflowCompletionDataStorePersistenceSpec
```

Run only through the required machine-wide serial wrapper, with existing
scoped command controls, normal caches and fixed JVM settings. No SBT runs in
this PLAN turn. TestOnly provides changed project/Test compilation; do not
duplicate Test/compile or run full suites here. Refresh the unchanged local
CNCF SNAPSHOT only after successful producer validation and before a consumer
actually uses the new API. Invocation 794 is not allocated or executed here.
Full producer/consumer validation remains at original Phase release.

Class C protected persistence/API work requires independent focused conformance
review before acceptance, and the original Phase's one full review remains
separate. Estimate for this prerequisite only: 2–4 hours including native
fixtures, focused validation and review, low confidence because the new receipt
must fit existing private host boundaries. This is not a new whole-Phase
packing estimate or evidence of the parent's model/effort. No numeric AI price,
new review epoch or reset of prior repair counters is inferred. Any new unit's
pre-review correction budget is at most three; predecessor 792 retains 2/3
and issued Reply unit 793 retains 0/3. Stop for authority conflict, unsettled
identity, pool/provider substitution, boundary growth or stalled/exhausted
correction rather than quietly broadening this manifest.

## Destination: Workflow-owned public advance

After this prerequisite, separately freeze the consumer integration:

- Generalize common planning preparation/registration and typed Start binding
  while keeping semantic preparation restricted to genuine semantic needs.
- Add a mechanical checkpoint allocation anchor; do not fabricate an AI
  ResultReference or change existing issued Reply/recovery recipes.
- Generate an `advanceSplitPhase` Operation that accepts exact data/context
  selection and expected native revision, never a processor/stage choice.
- Reuse one enumeration and absorb deterministic allocation/projection/
  objective steps. Persist all-known progress/documents through the new
  mechanical capability with zero AI requests.
- For actual current suspension return the retained WorkOrder/Decision after
  current-call checks, without reissuing or reallocating. After retained
  completion recover complete data and route from the actual current state;
  the old completion WAIT is not a new actionable work request.
- Issue typed WorkOrders only for unknown duration/semantic boundary or
  reasoning gaps. Missing host evidence is a host need, not invented AI data;
  genuine human decisions use actual verified human ingress.
- Install GoalPhase and SplitPhase services together after resolved
  configuration, checking actual Operation/MCP collisions. Do not construct
  another pool inside create_Core or infer human consent from authentication.
- Carry the next semantic request through generic bundled `sm-*` skills. The
  skill returns the requested result; Workflow owns routing and managed
  git/SBT/other command execution. Legacy cncf-* skills remain unchanged.

Cost reduction is an explicit design goal: executable specs must show no AI
call for fully known work, no second enumeration after allocation, and no new
request/allocation during recovery. CLI/server/MCP, separate-process execution,
human ingress, follow-on semantic/Decision work, publication/application and
managed external commands remain original acceptance requirements. This
prerequisite does not replace the original Phase with a storage-only project.

Next bounded work state: IMPLEMENT the producer prerequisite under the retained
limited recovery, subject to actual scoped permissions. Ordinary authority,
review, commit and final closure gates remain unsatisfied and are not waived.

## IMPLEMENT entry

The next Goal turn observes the same original Goal active. Previous turn
classification: progress in source-grounded design evidence and its authoritative
journal/Phase projection, not implemented public advance or acceptance. Execution
owner is the parent under the retained explicit limited-recovery direction,
not a registered implementation agent or an ordinary PLAN-owner lease. Exact
parent model/effort are not directly observable; no configured agent runtime or
independent review is asserted. All eleven producer and two consumer evidence
paths above remain the frozen frontier. Original source/test changes and native
specs remain subject to actual scoped platform permissions and proportional
validation. There is no live SBT handle; no invocation is scheduled at entry.

## Candidate implementation and validation selection

The five new producer implementation files and metadata-only ComponentStorage
binding are now candidate source. Private mechanical preparation captures only
bounded Creates; managed execution uses the existing host participant/UoW and
verified native post-images. Exact checkpoint recovery rechecks the complete
instance history prefix and rejects a different owner, operation, reference,
schema, indexed revision or physical envelope. No pool/provider/policy change
or synthetic AI result was added. Actual scoped platform requests admitted
these exact edits; that permission is not independent implementation acceptance.

Three frozen executable-spec files add fifteen behavior blocks: four pure
transition/codec cases, four Create-only program cases and seven native public
Service/CLI cases. Each has adjacent Given/When/Then and should matchers.
Native cases cover missing Workflow composition, success/reopen/later history,
document/checkpoint collision, absent/foreign document grants, actual committed
storage followed by codec readback refusal, checkpoint payload tampering and
valid-but-divergent history refusal. They demonstrate managed storage, not
genuine human Start or default consumer installation.

Validation 794 selects the exact producer accumulator above, without duplicate
compile, generation, publication, Git or source edits during SBT. Parent-direct
execution retains the explicit limited recovery instead of claiming a normal
registered-runner receipt. Fresh permission storage and the sole metadata
builder carry the complete 462-entry prior consumed-ID ledger and a helper-owned
60-minute window. The ID is CNCF-MECHANICAL-WORKFLOW-DIRECT-20261002-794.
Current unit pre-review corrections remain 0/3 before validation. No producer
or consumer success, review, Step acceptance or release is claimed yet.

## Validation 794 and frozen fixture repair 1/3

794 terminated with SBT/wrapper exits 1/1 and `lock=released`. Main compilation
of 216 sources succeeded, including the six changed implementation sources.
Test compilation found one E049 ambiguity at public native spec line 157:
the helper parameter `name` shadows inherited OperationDefinition.name inside
its anonymous class. No test ran. Readable log and summary are retained at
`/tmp/skill.cncf.d/a-f148814c-4bee-4420-b870-0d12193eb512/34851-20261002T095941Z.log`
and the adjacent `.summary.json`. The consumed ID is retired in the complete
463-entry ledger. No live SBT handle remains.

Frozen M1 FIX: only rename the helper argument to `operationname` and its one
Specification use in `org/simplemodeling/cncfclient/WorkflowMechanicalTransitionSpec.scala`.
No test behavior, assertions, production API, codec, grant or selected argv
changes. Execution owner remains parent-direct under limited recovery, model
and effort not directly observable, no agent or independent review claimed.
This byte-changing validation correction is cycle 1/3 for this unit; preserve
all predecessor counters. Re-run the exact accumulator with a fresh 795 ID,
fresh permission artifact and the complete prior ledger after the correction.

## Validation 795 and frozen same-boundary repair 2/3

795 completed main/Test compilation and ran 93 tests: 88 passed, five native
tests failed and the pure transition suite aborted before its four tests.
SBT/wrapper exits 1/1 and lock=released; no live handle remains. Summary/log:
`/tmp/skill.cncf.d/a-dcf8a489-d01c-4e67-88c6-bcaa814f63e7/36522-20261002T100331Z.summary.json`
and adjacent `.log`. The 464-entry consumed-ID ledger retains both attempts.

Two concrete subordinate causes are established. Test ComponentId literals
`test.Mechanical` and `test.Foreign` violate the namespace's two-segment
minimum; change only those three fixture literals to org.test.*. More
importantly, SqlDataStore._multi_guard_logical_record first checks physical id
against the selected target, then removes id from its verified logical
post-image (lines 716–724). The new adapter wrongly reused its physical-load
decoder, which requires id, for that logical post-image. Actual uncertain
postcommit/reopen recovery passed, independently showing the physical reader
works; the error is the immediate projection boundary, not transaction scope.

Frozen FIX within the same adapter and two spec paths: retain the full physical
id/field check for ordinary load, add a separate private exact logical-envelope
decoder, strip only the already checked physical id in load, and use logical
decoding only for canonical verified pair projection. Reject duplicates and
all unknown logical fields; rely on the existing verified native target/id
checks, never an optional-id shortcut or fabricated readback. No native
provider, policy, grant, public API or selected test/assertion is changed.
This correction is 2/3; owner remains parent under limited recovery. Revalidate
the unchanged accumulator with fresh 796 and the complete ledger.

## Validation 796 and frozen fixture repair 3/3

796 completed main/Test compilation and all nine suites: 96/97 passed, one
failed, none aborted/canceled/ignored/pending. SBT/wrapper exits 1/1 and
lock=released; no live handle remains. Evidence is the complete summary and
log at `/tmp/skill.cncf.d/a-d61bfffc-4a63-460c-b7d4-05ea4628414c/39306-20261002T100923Z.summary.json`
and adjacent `.log`; the complete consumed ledger now has 465 entries.
Atomic success, physical reopen, later-history recovery, collision rollback,
postcommit uncertainty and tamper refusal pass after the exact decoder repair.

The sole failure is fixture construction in the absent/foreign document-grant
case: ComponentRecordStorageDeclaration.createC deliberately rejects an empty
grant vector. The absent-grant fixture must instead select the existing
six-argument SubsystemEventStoreBootstrap.createC overload, which supplies no
record declaration. The seven-argument overload remains for nonempty exact or
foreign grants. This is a fixture setup error, not a missing production grant.

Frozen correction 3/3 changes only host setup in the public native mechanical
spec: extract the shared scope/configuration, use the no-record-declaration
overload when recordGrant=false, and construct the nonempty declaration only
otherwise. Keep all negative-operation, unchanged-history and absent-checkpoint
assertions and all production permission checks. No provider, production API,
policy, selected argv or independent acceptance is changed. Parent limited
recovery remains execution owner; predecessor repair counters remain retained.
Revalidation 797 uses the exact same accumulator and full prior ledger with
fresh typed permission. A fourth unbounded repair cycle is not admitted.

## Validation 797: bounded prerequisite progress

797 completed main/Test compilation and the unchanged nine-suite accumulator:
97/97 tests passed, nine suites completed, none failed/aborted/canceled/ignored/
pending. SBT/wrapper exits 0/0 and lock=released. The complete summary was read
at `/tmp/skill.cncf.d/a-281d2d01-f80e-4106-9ebd-d74dc401f030/42678-20261002T101731Z.summary.json`
with adjacent `.log`. The native absent and foreign grant cases now reach the
actual managed call and refuse without advancing history or retaining a
checkpoint. The entire original validation selection was retained. Both
repositories' git diff --check passed; no source changes occurred during SBT.
The completed command ID is retained in the full 466-entry ledger. No live SBT
handle or scheduled invocation remains, and the repair count stays 3/3.

This is focused evidence for the generic producer mechanical capability, not
independent review, consumer acceptance, full repository validation or Phase
closure. It neither proves the consumer's generated advanceSplitPhase nor
installs SplitPhase in the default Factory. The current consumer predecessor
remains 793; no new consumer test result or dependency refresh is invented.
No source version, Git stage/commit/push, shared skill or remote warehouse was
changed. Existing cncf-* skills remain untouched.

The next bounded state is PLAN for consumer integration within the original
active Goal. Start from the native capability now tested, refresh the unchanged
local CNCF SNAPSHOT before actual consumer use, and freeze the all-known context
and mechanical allocation anchors, whole retained payload/document comparison,
current-state public routing and executable operation specs before edits.
Unknown-duration/boundary/reasoning work must still issue actual typed requests;
all-known progress and recovery must not allocate or fake AI results. Default
multi-service composition, genuine human ingress, follow-on Decision work,
managed git/SBT/other commands and generic packaged sm-* skills remain owed.
Original authority/base/binding/PLAN-owner and independent review/commit/full
release gaps remain explicitly unsatisfied. The same original Goal stays
active; this storage prerequisite does not replace or complete Phase 2.
