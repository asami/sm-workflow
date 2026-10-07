# SplitPhase public advance and retained checkpoint integration

Date: 2026-10-02
Scope: unchanged original sm-workflow Phase 2, admitted CNCF Phase 88 consumer
Status: frozen generated-contract frontier implemented and focused-validated
under parent-only limited recovery; not ordinary accepted PLAN or installed action

## Continuity

The preceding implementation turn made progress: six checkpoint preparation,
codec, publication and spec files now exist. Actual representative 813 passed
13/13 and affected 814 passed 32/32, both exits 0/0 and lock released. Complete
logs and summaries are in the planning-checkpoint-publication journal. Normal
testOnly ran the corrected official runtime prerequisite automatically. This
evidence is passive preflight, not native application publication or public
advance. The active original Goal observation retains createdAt 1790766223 and
the unchanged objective; do not replace it with a storage or ABI-only Goal.

Retain the user's parent-only limited recovery, no agents, no shared-skill
mutation and no Git writes. Original authority/base/binding/PLAN-owner evidence
gaps remain explicit; do not reconstruct them from current HEAD or claim normal
PLAN admission. Preserve checkpoint prerequisite 3/3, snapshot 3/3, producer 3/3,
test-settings 1/3 and all Step/Phase review histories. The consumed execution
ledger remains 483 identities through 814. This design turn starts no SBT,
allocates no command identity and edits no production/spec files.

cncf-goal-phase's separation of PLAN from IMPLEMENT is retained for this
design handoff. No capability lease or observable model/effort is invented.
The next turn must implement the frozen frontier, not repeat this investigation.

## Current source findings

1. CML and SplitPhaseDefinitionBinding declare six Provided Operations at
   split-phase-issued-reply-v4; advanceSplitPhase is absent. The private
   checkpoint preparation's operation constant is not a generated or installed
   Operation. Existing preview and completion must not alias advance execution.
2. impl/ComponentFactory delegates to GoalPhaseLauncherAssembly; its existing
   fallback lists only GoalPhase/StepReview services. A new generated declaration
   alone does not install a production SplitPhase service or make the launcher
   endpoint available. Default multi-workflow installation remains required.
3. ManagedTypedPartitionCompletion.recoverForCallC calls P.recoverC and
   P.recoverPlanningC. Those call C.prepareC, pendingC, semantic admission,
   deterministic resume and candidate continuation. They are not the proposed
   observation-only checkpoint recovery and must not be used by new advance.
4. Native WorkflowManagedMechanicalTransition.loadRetainedC returns a private
   receipt/current observation. Its adapter checks configuration, operation and
   complete native checkpoint history prefix. Application mapping, index,
   independent inputs and candidate documents still need their own observation.
5. Native WorkflowManagedCompletion.loadRetainedC admits a canonical stored
   result and typed closing result. Its adapter checks an adjacent suspended
   entry and unsuspended completion entry in actual history, but the application
   still needs to compare the complete original and completion prefix fields.
   In particular, adjacency alone does not prove the application-selected
   causation, unchanged progression, correlation, composite/predecessor metadata,
   original Start history or exact committed revision in the mapping.
6. P.admitC defines that completion append precisely: suspended current lifecycle,
   progression, correlation, derived composite and committed predecessor are
   preserved; causation is the assigned result identity; suspension clears.
   Its typed closing result is StateMachineOperationResult(P.closingType,
   submitted.resultReference). Recovery can compare these facts directly,
   without constructing P.Pending/P.Plan or applying S.admitC/S.resumeC.
7. ManagedTypedPartitionIssue can read an actual WorkOrder without issuing it.
   Its issue path allocates a Reply and constructs a suspension, so public
   routing must read current suspension/work first rather than call issue again.
   A completed original partition issue needs the saved new mapping; the old
   reconstructive enrichment recovery is not an alternate route.
8. PlanningInputDataStorePort reads nine separately declared purpose rows.
   PartitionContextDataStorePort loads raw context only. Typed Start preparation
   independently reads source, optionally selected proposal and profile selection.
   These can support complete-data observations without a planning kernel call.
9. Enrichment storage metadata binds fifteen distinct same-store collections.
   Checkpoint mapping/index can use its existing publication collection; no new
   pool, provider, grant, SQL path or workflow storage service is needed. Per-call
   ComponentRecordStorage binding owns application Create/read access.
10. Existing actor guards admit an authenticated provider/trusted-local User or
    Service and compare the original participant. They do not establish that a
    different public operation is the current advance call. The new generated
    decoder and bound call must additionally enforce the exact owning
    SplitPhaseService.advanceSplitPhase operation/component, independently of
    stored participant text or a decoded command.
11. sbt-cozy defines cozyGenerate as generation and cozyPrepareRuntime as main
    compile plus runtime evidence/classpath. The catalog specs separately require
    an actual current packageBin JAR. Explicit packaging is needed when the
    declarations change; a pre-existing older JAR is not fresh catalog evidence.
    No standalone Test/compile is necessary beside successful focused tests.

Sources: current CML, DefinitionBinding, RuntimeCatalog, fixture assembly,
typed Start/current planning/context readers, managed partition issue/completion,
P completion construction, native mechanical/managed completion and its datastore
query, impl/ComponentFactory, current generated ABI/catalog/packaging specs, and
the installed sbt-cozy source. Upstream inspection is read-only.

## Public command contract

Add generated SplitPhaseService.advanceSplitPhase as a COMMAND, with input
SplitPhaseAdvanceCommand and output SplitPhaseAdvanceResult (extends existing
OperationResult, as the other interaction-returning public results do). Add the
Provided Operation to the Workflow itself; it is not a Required AI SPI or a
Judgment action. Append it after existing operations, preserving their order.

SplitPhaseAdvanceCommand has exactly:

- instanceIdentity: string, required;
- context: existing generated SplitPhasePartitionContextReference, required.

The context contains exact identity/revision. No stage, next operation, worker
selection, candidate, minutes, checkpoint/mapping reference, result reference,
participant assertion, SQL path, argv, override or retry flag comes from the
caller. The host admits the instance/catalog and original Start participant.
The command is a query of current workflow progression, not human consent or
an execution witness. Same original context queries select the immutable index;
later semantic work uses an explicit successor context.

Use a new closed codec for the actual generated command, with exact prefix and
{schemaVersion,payload} envelope at
sm-workflow.split-phase-advance-command.v1. Bound the whole UTF-8 frame to 16KiB.
Require closed root/payload/context fields and canonical nonempty strings; do
not trim, fill defaults or accept schema/command aliases. Validate the final
lookup locator through the existing publication indexKeyC on the generated
instance/context tuple, so non-ASCII expansion and the final 1024-byte key bound
are evaluated at this external-input boundary rather than duplicated.
CLI positional and server/MCP arg1 carry the same complete frame under
WorkflowJsonCommandRequest; no transport may supply a route selector.

Bump the complete generated Workflow definition to
split-phase-public-advance-v5. Update the exact application declaration guard and
current-version specs, not unrelated historical evidence. Old v4 bytes are not
converted, rebound to v5 or erased. A v4 stored definition remains incompatible
with the newly admitted v5 catalog and must fail closed; migration requires its
own actual design/evidence, not replacing its revision text. Legacy cncf-* skills
remain untouched and compatible with their existing workflows.

Public responses must ultimately be actual canonical WorkflowInteraction values:
terminal/current retained progression first, an actual pending WorkOrder or
Decision next, and new work only if the current state requires it. Do not return
only a snapshot/store acknowledgement and call the full advance complete.
Missing host policy data is a host/configuration need, not fabricated AI work.
Partition/scope/reasoning requests expose only genuine unknown work; the skill
returns that requested data and never chooses a processing operation.

## Managed routing, commit and recovery boundary

The managed implementation follows this order after the public contract is
generated and its actual current-call actor admitted:

1. Load canonical current native state and raw original context/Start; compare
   handle/catalog/component and original participant. Terminal or current issued
   WorkOrder/Decision takes precedence over fresh planning. Observation cannot
   call the issue allocator, human verifier or a write entry point.
2. Read immutable ContextIndex by the command's complete instance/context tuple
   BEFORE candidate/checkpoint allocation. A present index requires its exact
   mapping and stored origin. Missing or crossed referenced rows fail closed;
   they do not fall back to current naming policy or a new computation.
3. Mechanical origin loads actual native Retained under the generated advance
   operation and compares its entire payload with the application mapping. AI
   origin loads the actual issued WorkOrder and generic retained completion,
   canonical result/closing data and current history; compare the exact original,
   issue and completion prefix without P recovery/admission or kernel calls.
   Preserve legitimate later native history; do not overwrite it with the prefix.
4. Compare independently loaded original context and Start, source/selection/
   originally selected proposal, all nine original and all nine effective
   planning purpose rows, and every Built proposal/ownership/handoff. The passive
   R record is the complete stored frontier, including local handoffs and
   unresolved needs, not input to enumerate/select/reallocate.
5. Only genuine fresh active-unsuspended execution may construct T.PlanningBound
   and call W once. Unknown partition work uses its actual issue path. Known
   work consumes one actual allocation need/continuation. Complete capture and
   publication preflight run before any staging, including all actual effects.
6. Bind each checked Row via fresh ComponentRecordStorage.stageCreateC and join
   the resulting Create-only program with WorkflowManagedMechanicalTransition
   executeC in the same admitted Subsystem DataStoreSpace/provider transaction.
   Never use separate instance append and side-row writes or duplicate SQLite.
7. A native Completed is necessary but not application success: independently
   verify the whole retained input/document set before returning the public
   projection. Readback failure is indeterminate, not an automatic retry or
   permission to truncate/split the commit. A later read may establish the same
   mapping, with no re-execution. Native rollback/grants/limits remain intact.
8. Original AI completion captures the same K/R mapping within its existing
   closing transaction. Replace the old incomplete application publication path,
   rather than stage overlapping old/new candidate effects or manufacture a
   second mechanical receipt. DecisionNeeded has no fake planning snapshot;
   its actual human direction/Decision remains distinct required work.

Single-user execution adds no claim, application lock, contention retry,
management hash, perpetual monitoring or current-HEAD identity. Component
construction retains purpose metadata only; all provider/pool access is borrowed
from the already initialized Subsystem at the actual call boundary.

## Next exact implementation frontier: actual generated advance contract

First remove the verified public-Operation prerequisite, not install an empty
action or execute under a fabricated identity. Freeze these twelve paths:

1. src/main/cozy/sm-workflow.cml
2. src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseDefinitionBinding.scala
3. src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseFixtureServiceAssembly.scala
4. src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseAdvanceCommandJsonV1.scala (new)
5. src/main/car/abi-manifest.json
6. src/test/scala/org/simplemodeling/textus/smworkflow/SplitPhaseGeneratedAbiSpec.scala
7. src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseAdvanceCommandJsonV1Spec.scala (new)
8. src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseRuntimeCatalogSpec.scala
9. src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseExplicitPartitionDefinitionSpec.scala
10. src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseStartCurrentCallSpec.scala
11. src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseStartCommandAdapterSpec.scala
12. src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseStartExecutionSpec.scala

Changes in existing version specs are exact v4-to-v5 substitutions only; keep
their substantive cases. Generated ABI spec also exercises actual command field
construction and exact seventh operation/input/output. The new codec specs use
real generated values, closed/unknown/missing/wrong primitive fields, formatting,
Unicode/delimiters/key limits, nulls and whole UTF-8 bounds. Generated carriers
and decoded commands remain data, not authority. Add an actual old-version
catalog/definition refusal case rather than claiming migration support.

Fixture assembly updates its complete generated operation list but does not
advertise or install an unwired advance action, as it already excludes converted
partition completion. Preserve its existing operations; do not use fixtures as
the production default assembly. Future managed wiring is still required.

Run generation first using exact logical argv ["--batch","cozyGenerate"], then
mechanically synchronize the authored ABI from actual generated model metadata
using the unchanged complete projection exercised by ComponentPackagingDescriptorSpec.
Use apply_patch for the authored file; do not hand-edit generated products,
filter comparisons, remove old exports or introduce an empty facade. Inspect
the whole generated surface before authoring; generation changes bytes and is
not itself validation. This preparation is not duplicate Test/compile.

On the settled source/ABI tree, run one representative with fresh permission:

```text
["--batch", "packageBin", "testOnly org.simplemodeling.textus.smworkflow.SplitPhaseGeneratedAbiSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseAdvanceCommandJsonV1Spec"]
```

Then one affected accumulator without edits between validated source/spec bytes:

```text
["--batch", "testOnly org.simplemodeling.textus.smworkflow.ComponentPackagingDescriptorSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseRuntimeCatalogSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionDefinitionSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseStartCurrentCallSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseStartCommandAdapterSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseStartExecutionSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedTypedStartOperationSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedPlanningBindingSpec org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningCheckpointPreparationSpec org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningCheckpointJsonV1Spec org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningCheckpointPublicationSpec"]
```

Use the retained user parent-only execution exception, exact serial wrapper,
fresh typed metadata/full consumed-ID ledger, normal caches/fixed JVM and scoped
platform review. Actual terminal marker/log/summary and lock release remain
mandatory. No source edits while SBT is live. No full suite, duplicate compile,
upstream refresh or shared-skill change absent an actual causal requirement.

Class C, bounded settled declaration/codec work with a known protected interface
and medium confidence; estimate 30-90 minutes from twelve files, generated
provenance and current sub-ten-second focused runs. This is not a new whole-Phase
packing claim. New prerequisite validation repairs begin at 0/3, without resetting
any prior ledger. Evidence ownership is this journal and docs/phase/phase-2.md.
Readiness/independent protected review and ordinary acceptance remain unproven.

## Remaining original end state

Next is IMPLEMENT the exact generated-contract frontier. Do not repeat PLAN or
promote declaration success to working public advance. Managed same-pool commit,
full-set readback, read-only current-prefix recovery, original AI-completion
capture and canonical current-state routing follow within this same Phase.
All-known no-AI execution, actual partition/scope/reasoning WorkOrders and human
Decisions, complete document application, CLI/server/MCP/separate processes,
default GoalPhase+SplitPhase assembly, generic packaged sm-* skills retaining
cncf-* compatibility, Workflow-owned external commands/merge routing, original
independent Step reviews/exact commits and one Phase full review/full validation/
final release remain required. Nothing in this handoff completes that objective.

## Actual generated-contract implementation and validation

The following IMPLEMENT turn completed the twelve frozen declaration/codec/ABI/
spec paths without widening them. CML now declares the seventh public COMMAND
advanceSplitPhase with SplitPhaseAdvanceCommand and SplitPhaseAdvanceResult;
the complete Workflow revision is split-phase-public-advance-v5. Generated
command construction has exactly instanceIdentity and context (identity/revision).
Existing states, four semantic actions and two Required correlations remain.
The complete compiled declaration guard and fixture declaration inventory agree
with that generated contract. The historical fixture deliberately does not bind
advance or converted partition completion; no empty production action was added.

The new prefixed codec accepts closed generated data only. It checks complete
root/payload/context shape, canonical strings and malformed Unicode, the whole
16KiB UTF-8 frame including prefix/formatting, and the final escaped 1024-byte
context locator through existing publication indexKeyC. Encoding/decoding never
constructs a current-call admission, grants storage access or chooses a route.
The examples include reordered/formatted JSON, unknown execution selectors,
missing/wrong primitive fields, nulls, Unicode/delimiters, last-fitting and first-
excessive physical locators, and the exact whole byte boundary.

Generation 815 actually emitted 110 Scala sources. The authored manifest was
mechanically projected from complete generated metadata with apply_patch using
the unchanged ComponentPackagingDescriptorSpec projection. Whole exports
comparison found no removed or changed existing services, operations, types,
entities or component identity: only advanceSplitPhase and the two declared
types were added. No generated file was hand-edited. Generated Provided API and
actual command carrier were inspected; successful packageBin built the current
artifact before packaged catalog acceptance.

The old-version refusal example independently admits and binds mutually
consistent complete v4-revision producer/Workflow data before showing that the
current compiled v5 catalog refuses it and the immutable bindings differ. It
does not claim a replay of an archived old package, migration, version rewriting
or deletion. Existing version-specific Start/definition tests retain their
substantive cases with only exact current-version substitutions.

Actual serialized invocations (all fresh permission and full consumed ledger):

- SMWF-PUBLIC-ADVANCE-GENERATE-DIRECT-20261002-815: exact cozyGenerate preparation;
  sbt_exit=0, wrapper_exit=0, lock=released. Log:
  /tmp/skill.cncf.d/a-9a6c6f6f-0b70-494c-973e-996121a40324/10323-20261002T125837Z.log
  Summary: same directory, 10323-20261002T125837Z.summary.json.
- SMWF-PUBLIC-ADVANCE-ABI-CODEC-DIRECT-20261002-816: exact frozen packageBin and
  two representative suites; 8/8 succeeded, two suites completed, none aborted,
  canceled, ignored or pending; exits 0/0, lock=released. Log:
  /tmp/skill.cncf.d/a-82eec8c9-d33e-4548-bb7c-02d0eef600ad/11365-20261002T130003Z.log
  Summary: same directory, 11365-20261002T130003Z.summary.json.
- SMWF-PUBLIC-ADVANCE-AFFECTED-DIRECT-20261002-817: exact frozen affected
  accumulator; 89/89 succeeded, eleven suites completed, none aborted, canceled,
  ignored or pending; exits 0/0, lock=released. Log:
  /tmp/skill.cncf.d/a-03e72748-06c5-42ec-90a8-bb15831dc305/12023-20261002T130059Z.log
  Summary: same directory, 12023-20261002T130059Z.summary.json.

Actual terminal results and complete structured summaries were checked. There
were no source/spec edits between successful 816 and 817, and none while any
SBT invocation was live. The corrected Test/compile dependency automatically
prepared official runtime classpath and development manifest for normal
testOnly. New frontier validation repairs remain 0/3; preceding repair ledgers
are unchanged. Tracked diff and all twelve owned untracked file whitespace
checks passed. The complete consumed ledger is now 486 identities through 817.
No full-suite rerun was selected; earlier 804's 924/924 is historical evidence,
not a full-suite claim for this newer source tree.

This is real implementation progress, not Phase acceptance. No upstream edits,
shared-skill edits, subagents or Git stage/commit/push occurred. Independent
protected review and ordinary Step admission/commit remain unproven. The
original Goal stays active with createdAt 1790766223; its original scope and
authority/base/binding/PLAN-owner gaps remain explicit.

Next is source-grounded PLAN for managed advance/current-call admission,
same-pool transaction, complete independent observation and prefix-only recovery,
then original AI completion capture/default assembly within the unchanged Phase.
Do not repeat this implemented declaration prerequisite or substitute old
reconstructive recovery. Public CLI/server/MCP operation readiness and all
original semantic/human/external-command/review/release requirements remain open.
