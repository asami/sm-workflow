# SplitPhase managed planning checkpoint execution and observation

Date: 2026-10-02
Scope: unchanged original sm-workflow Phase 2; admitted CNCF Phase 88 consumer
Status: six-path IMPLEMENT and focused validation completed under parent-only
limited recovery; independent review/acceptance and public integration pending

## Continuity and verified prerequisite

The preceding goal turn made implementation progress, not a verified wait or
status-only continuation. The twelve frozen public-contract paths now declare
advanceSplitPhase at split-phase-public-advance-v5 and a closed two-field
generated command. Actual generation 815, packageBin/representative 816 (8/8,
two suites) and affected 817 (89/89, eleven suites) finished with exits 0/0,
lock=released and no failed/aborted/canceled/ignored/pending examples. Current
source and 817's actual structured summary were inspected, along with the
previous integration journal. The generated prerequisite is implemented; do not
repeat its PLAN or declare the installed public operation complete.

Continue the original active Goal, createdAt 1790766223, without replacing its
objective, starting another Phase or shrinking its closure requirements. The
user's parent-only limited recovery/no-agents override remains; no Git writes,
shared-skill changes or upstream source changes are selected. Original ordinary
authority/base/binding/PLAN-owner and independent review/commit evidence gaps
remain unproven. They are not new permission requests and may not be backfilled
from current HEAD. Prior repair ledgers remain: producer 3/3, passive snapshot
3/3, checkpoint preparation 3/3, test settings 1/3, generated advance 0/3.

The original PLAN turn used cncf-goal-phase's separate PLAN state and did not implement or
run SBT. The full consumed ledger remains 486 identities through 817; no fresh
permission is allocated. Source findings below change the next implementation
action: reuse the actual public native mechanical API and separate raw Start
observation, rather than create another persistence primitive or reuse the
existing reconstructive recovery. No active SBT session is being polled.

## Authoritative source findings

1. WorkflowComponentStorage.bindMechanicalC binds the public
   WorkflowManagedMechanicalTransition[A]. executeC consumes an actual native
   Intent plus an ExecUowM local-Create program, extracts the complete Create
   set, and commits through the existing Subsystem verified multi-guard space.
   It returns private Completed only for a verified canonical native receipt.
   loadRetainedC returns private Retained(receipt,current), not an executable
   planning object. The native adapter validates physical envelope, complete
   configuration/component/operation/reference and full committed history
   prefix. This is sufficient native functionality; no upstream repair selected.
2. The native mechanical binding does not authorize the application's current
   actor or transport Operation. The application must independently require
   the exact generated advance call and original participant. ComponentRecordStorage
   likewise explicitly says it is not actor/Operation authorization.
3. ComponentRecordStorage.bindForCallC borrows the current owning Subsystem and
   execution context. stageCreateC performs no write and yields a fixed-target
   closing intent; native executeC interprets the complete program in the same
   physical transaction. Do not call createC independently for publication or
   use raw SQLite, another provider or a new application-owned pool.
4. K.Captured already contains actual original planning construction, complete
   mapping/index and one anticipated mechanical append. U.prepareC supplies
   all changed input rows, every Built proposal/ownership/handoff, mapping and
   immutable context index; it checks final keys, row counts and whole native
   receipt before staging. Keep these existing contracts and bounds unchanged.
5. Existing SplitPhaseTypedStartDataStorePort.prepareForCallC reads the raw
   Start, selected source/proposal/profile and then calls T.prepareC. That
   constructs an admitted StartRequest and initial instance. Recovery needs
   its complete independent reads but not these execution-preparation objects.
   Introduce an observation method and reuse its reading in fresh preparation.
6. Existing PlanningInputDataStorePort.loadCurrentForCallC independently reads
   nine purpose rows; PartitionContextDataStorePort independently reads the raw
   original context. Recovery can read all nine original and effective rows by
   their saved references and compare their complete values. No planning kernel
   or latest-version lookup is needed. Missing rows already fail closed.
7. Existing enrichment verification calls U.projectC(plan), which requires a
   reconstructed P.Plan. Existing typed completion recovery calls P.recoverC
   and recoverPlanningC, including planning recomputation. Neither route is an
   observation implementation for this new checkpoint. Leave them unchanged
   here; original AI transaction capture and its replacement observation route
   are the following integration obligation, not an alias.
8. Typed Start's initial correlation is its original selection identity; its
   causation is the immutable Start reference identity. TypedStartExecution's
   first actual history entry is sequence/revision one, Active,
   entry.EntryAssessment, with that correlation/causation and absent composite,
   predecessor and suspension. Read-only relation checking can compare these
   stored facts directly, without Start request construction or consent checks.
9. Candidate allocation policy is needed only by fresh planning. Recovery must
   use the mapping's saved identities and complete effective planning row even
   when the allocation policy has been removed or changed. Physical collection
   declarations and current native actor/storage permission remain required.
10. The generated public command still has exactly instanceIdentity/context.
    The managed checkpoint seam below is an internal execution/observation
    dependency for the eventual Operation. It does not install a temporary
    snapshot-returning public facade or replace canonical WorkflowInteraction.

Sources read: actual WorkflowManagedMechanicalTransition,
WorkflowMechanicalTransitionDataStorePersistence, WorkflowComponentStorage,
ComponentRecordStorage; actual K/U/J, typed Start preparation/read port/read
policy/execution, profile selection, raw context and nine-purpose readers,
managed partition preparation/issue/completion, candidate allocation policy,
existing call-scoped Operation binding and native ActionCall.Core; actual
checkpoint and SQLite managed-context executable examples. Upstream read only.

## Frozen six-path implementation frontier

1. src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseTypedStartDataStorePort.scala
2. src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePlanningCheckpointObservation.scala (new)
3. src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePlanningCheckpointDataStorePort.scala (new)
4. src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseManagedPlanningCheckpoint.scala (new)
5. src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePlanningCheckpointObservationSpec.scala (new)
6. src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseManagedPlanningCheckpointSpec.scala (new)

Evidence targets: this journal and docs/phase/phase-2.md. CML, codecs, existing
K/U, candidate allocation policy, default Factory, managed AI completion,
upstream, build settings and installed skills are excluded from this frontier.
New causal source requirements invalidate this exact manifest before editing;
do not silently expand the six paths or implement a substitute API.

### Raw Start observation

Add observeForCallC(reference,core): Consequence[Option[T.Record]] to the
existing Start reader. Decode the actual exact raw row; independently load its
selected source, optional original proposal and profile selection through
existing call-scoped readers; compare each complete value with the saved row.
No T.prepareC, Start adapter, context preparation, human verifier, instance
allocation, admission object or write occurs in this method. Missing original
proposal remains absent rather than adopting a later planning proposal.

prepareForCallC reuses observation then calls existing T.prepareC for genuine
fresh execution preparation. Preserve its signature and all Start behavior;
no duplicate source/profile reads or copied semantic validation policy. Recovery
compares the returned whole T.Record with mapping.originalStart and uses the
current catalog/configuration separately. Raw successful observation is data,
never a human ingress proof or execution grant.

### Complete checkpoint observation

The new observation module checks raw data relationships only. Its result is
Unit, not a native receipt or execution witness. Inputs are the exact index,
mapping, independently loaded original context/Start, original/effective nine-
purpose facts, ordered candidate documents and actual native mechanical receipt.
All complete values must agree, not merely their reference/revision strings.

Check index == K.index(mapping), native payload type/value and component/
advance operation/reference against the complete mapping and mechanical origin,
original owner handle/revision/configuration and Start workflow/instance. Check
the original Start history fields described above against the receipt's actual
predecessor. Do not regenerate naming policy, re-create history, enumerate cuts,
select an objective or manufacture an expected native receipt. Native Retained
already establishes exact one-append receipt and later current history prefix;
do not add a second competing native interpreter. Preserve legitimate later
native state. Candidate documents cover every Built member in saved order,
including a mixed Requirements frontier, with exact proposal/ownership/handoffs.

The datastore port retains only U.Targets and declared reader metadata. Query
the index via U.indexKeyC(command instance/context), decode through actual J,
then load its exact mapping via U.mappingKeyC. An absent index returns None;
present index with missing/crossed/malformed mapping fails, never recomputes.
Read original context and observed Start independently, both full fact sets,
and all Built documents through freshly bound ComponentRecordStorage/readers.
Stage none of these reads. Keep the whole payload limits and exact schemas.

Candidate/raw relation test inputs may be constructed as inert data, including
negative substitutions. Only the managed facade can construct its private
Observed result, after actual native Retained and complete independent rows have
passed. Raw receipt data or a returned Unit must not confer this capability.

### Current native call and atomic execution

Binding checks the actual compiled generated advance service/input/result and
admitted v5 catalog, same-store Workflow declaration and fifteen distinct U
purposes. Retain metadata only. At every actual call borrow the Core's current
owning Subsystem/context and create the native mechanical binding there; never
capture a Factory-time Core, provider, lease or physical pool.

Require Core.action.request to be the exact SplitPhaseService.advanceSplitPhase
command, decode its one canonical argument with the actual new codec, and
compare that command with the checkpoint's index (or read query). Reject absent
action/context/component, another service/operation, changed arguments, forged
authentication, anonymous and an authenticated different original participant.
Require provider/trusted-local authenticated User or Service. Current storage
declarations/grants must be admitted; command/participant strings alone are not
authority. Actor admission precedes staging or native access.

Provide executePreparedForCallC(captured,core) and explicitly named
loadMechanicalForCallC(command,core), not a generic public response API.
Mechanical execution accepts only the existing privately constructed K.Captured
with actual mechanicalIntent; PartitionCompletion is not rewritten into a
mechanical append. Read the immutable query index first; if present, use the
same complete read-only observation rather than execute or stage another time.
If absent, independently match original context/Start/current facts and native
predecessor to captured construction, then U.prepareC preflights the entire set.
Bind/stage every U.Row on the actual call and combine the complete ExecUowM
program before exactly one native executeC(intent,program). No per-row commits,
truncation, application claim/lock, contention retry or management hash.

Private native Completed is necessary, not application success. Load actual
Retained and independently observe the complete publication before constructing
private application Observed. A failure during native commit or post-commit
observation is transaction-indeterminate; never rerun automatically or assert
rollback merely because success could not be observed. A later read may prove
that exact publication. Read-only relation refusal is an ordinary conflict,
not permission to repair data or invoke a processor. An AI-origin mapping fails
closed at this explicitly mechanical seam; it requires the subsequent genuine
completion observation path, never a fresh mechanical fallback.

## Executable examples and validation

Pre-review recovery, same Step/PLAN: 821 completed 2 suites with 8/9 passed,
one failed, exits 1/1 and lock released. Preserve initial repairs 1-3/3 and
their exact receipts; do not reset Step/Phase review repair counters or invent
a full-review source. The attributable current user direction is "テスト設定を
補正して続けて", with retained parent-only limited recovery. The updated
cncf-goal-phase execution-contract's pre-review recovery rule permits the
concrete in-scope test correction and continuation without another identical
direction request. Record a separate bounded recovery batch (limit 3; attempt
1 frozen here), not a fresh Phase or accepted PLAN.

Cause proven from SqlDataStore.update and its SQL column update: update applies
only supplied columns. Adding an unrelated fixture column does not remove the
existing JSON payload; the spec therefore incorrectly expects a read failure
while the original payload remains intact. M1 correction owns only the new
SplitPhaseManagedPlanningCheckpointSpec: update each verified one-field fixture
row's actual payload field to a malformed string, assert full observation
refusal, and restore the exact original value. Rename the scenario accurately
from missing to malformed. No production, acceptance, schema, public interface,
dependency, or path expansion; validation remains the same two-suite command
followed by the unchanged eight-suite accumulator. The observed transaction
and authentication cases are already green, not accepted Phase evidence.

821 summary:
/tmp/skill.cncf.d/a-8d1d6e66-4cac-49f8-bb97-f2218b57d0d0/22053-20261002T133105Z.summary.json.

IMPLEMENT evidence 820: compilation completed and nine examples ran (2 passed,
7 failed, no abort/cancel/ignore/pending), exits 1/1 and lock released. Two exact
causes are source-grounded: the older passive fixture seeds its first entry at
proposal.Awaiting, whereas actual typed Start appends entry.EntryAssessment;
and catalog.definition is admitted from the complete packaged ABI artifact,
whose fixtureSha256 differs from a separately serialized single-workflow
compiled artifact. These are not reasons to relax Start lineage or compare only
workflow names. Repair 3/3 is frozen to a local observation-test fixture in the
owned spec, prepared through existing constructors using the actual Start first
entry; and bind verification against catalog.workflow's admitted complete
binding plus full compiled Workflow equality, preserving all immutable fields.
The old fixture remains unchanged for prior suites. Same six-path frontier and
two commands; counters for all preceding frontiers stay unchanged. Summary:
/tmp/skill.cncf.d/a-17aa1b54-555f-4ec8-9d78-6067bf227ff4/21149-20261002T132830Z.summary.json.

IMPLEMENT evidence 819: production sources compiled, but the private spec's
write/read callback lexical scope lacked its actual ExecutionContext given.
The command terminated with exits 1/1 and lock released before tests. Repair
2/3 is frozen to providing each Probe helper's current executionContext at its
DataStore call sites. No runtime implementation or command changes. Summary:
/tmp/skill.cncf.d/a-07c37bd4-88ba-4745-8fc6-edad8ad22c41/20647-20261002T132718Z.summary.json.

IMPLEMENT evidence 818: the serial representative command terminated with
exits 1/1 and lock released before tests. Compilation identified the exact
HistoryEntry field as currentProgression, not progression. Repair 1/3 is frozen
to that field spelling in the owned relation implementation and negative example;
no behavior, frontier or validation command changes. Actual summary:
/tmp/skill.cncf.d/a-c08ec9d0-0071-4f84-8279-dff1d487deeb/20201-20261002T132615Z.summary.json.

Implementation refinement, frozen before instrumentation: these same six paths
can exercise an interrupted post-commit observation with a package-private test
binding accepting a zero-argument Consequence[Unit] row-observation check. The
ordinary binding supplies Consequence.unit. Invoke this check only after actual
native Retained and all independent rows have been read, before relation checks.
It can refuse inert observation only; it cannot supply or replace any native
receipt, row, transaction, participant or private Observed. No retry or public
configuration knob is introduced. This stays inside the existing manifest and
tests actual commit followed by uncertainty and later exact recovery.

Every added/changed behavior group uses adjacent Given/When/Then and should
matchers. Do not test journal prose, source-text shape or acceptance metadata.

Observation examples: all-known allocated and unchanged-preallocated mappings;
complete mixed Built/Requirements documents; changed same-reference context,
Start source/proposal/profile, any of nine original/effective facts or document
fields; crossed index/native reference/component/operation/payload; changed
original first-history fields; null/missing records; preserved valid later
history via real managed observation; raw data cannot construct private Observed.
No semantic/planning processor appears in observation code.

Managed examples use real authenticated native Service/launcher test calls and
SQLite from existing Subsystem bootstrap, not private pools or raw provider
substitutes. Test probes are labelled internal seam probes, not evidence that
the production default Operation is installed. Privately seed complete original
host records/native instance as fixtures, not human ingress evidence. Exercise
actual constructed all-known K capture, complete one-transaction instance/native
receipt/map/index/effective-planning/all Built-document publication, then exact
read-only recovery after physical close/reopen. Remove/change candidate naming
policy on recovery while preserving required storage declarations; stored
identities and data remain exact. Valid later native history remains current.

Reject missing/foreign Create grant and occupied immutable mapping/index or
candidate row; verify the original instance and every attempted new row remain
unchanged through physical reopen. Reject wrong owning operation and forged or
different actor before effects. Corrupt or remove one independently stored
required value in isolated fixture cases and fail the full observation rather
than accept equal references or compute another checkpoint. An unavailable
post-commit read must not yield Observed or retry. Any test-only read-failure
instrumentation is scoped to inert row observation, delegates actual native
execution and cannot construct/override native Completed/Retained; freeze a
causal additional seam in PLAN first if these six paths cannot supply it.

Class C protected consumer integration, medium confidence, 1-3 hours bounded
estimate from six paths, existing public mechanical API and current seconds-long
focused commands plus SQLite examples. This is an internal Slice estimate, not
a newly packed Phase, a profile attestation or ordinary PLAN-owner admission.
Ordinary protected independent conformance review remains required and pending.
New frontier validation repair limit 0/3; preserve every preceding counter.

One representative command on settled source:

```text
["--batch", "testOnly org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningCheckpointObservationSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedPlanningCheckpointSpec"]
```

One unchanged-tree affected accumulator:

```text
["--batch", "testOnly org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedStartPreparationSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedTypedStartReadSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedTypedStartOperationSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedPlanningBindingSpec org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningCheckpointPreparationSpec org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningCheckpointJsonV1Spec org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningCheckpointPublicationSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseAdvanceCommandJsonV1Spec"]
```

All eight named existing accumulator suites were verified against current files.
An initial draft used a nonexistent Start-reader suite name; it was corrected
before command allocation, with no test run or repair cycle consumed. Use
the required serial wrapper, fresh typed permission/full consumed ledger and
retained parent-only command exception; no concurrent SBT, private caches,
unneeded packageBin/cozyGenerate/full-suite/duplicate compile or upstream refresh.
Require actual final marker/complete summary/log and lock release. No source
edits during a live SBT process. Stop for actual changed API/design/authority
requirements before broadening; scoped local failures use only the frozen 0/3
budget, not a reset of preceding exhausted repair history.

## Original end state stays open

The original PLAN's next state was IMPLEMENT these six paths; that PLAN started no validation and
does not claim native application execution already exists. Current-state
public routing must still read actual pending WorkOrder/Decision or terminal
native state before new work, consume fresh planning only when needed, capture
the same mapping in original AI completion, and return real canonical
WorkflowInteraction rather than this internal Observed. Unknown-only partition/
scope/reasoning work, genuine human decisions, complete document application,
default GoalPhase+SplitPhase Factory, CLI/server/MCP/separate-process proofs,
generic packaged sm-* skills retaining cncf-* compatibility, Workflow-owned
git/sbt/other commands and merge delegation remain required. So do original
independent Step reviews/exact commits and one Phase full review/full validation/
final release. Keep the active original Goal, not a checkpoint-only substitute.

## IMPLEMENT outcome and exact verification

All six frozen paths are now implemented. observeForCallC independently reads
the immutable typed Start/source/selected proposal/profile without T.prepareC;
genuine fresh Start preparation still invokes its existing constructor after
that read. The checkpoint read port queries immutable context index first and
compares the exact mapping, original/effective nine-purpose facts and every
Built document. Its relation module returns Unit only. The managed dependency
requires the exact owning authenticated advance call and current declarations,
stages all U.Rows, and invokes the actual CNCF managed mechanical transaction
once. Only actual Completed followed by native Retained plus complete
independent readback can yield its privately constructed Observed. Readback
failure is indeterminate, not success, rollback evidence or an automatic retry.

Representative 822 passed 9/9, two suites; affected accumulator 823 passed
32/32, eight suites, on unchanged source/spec bytes. Both have sbt_exit=0,
wrapper_exit=0, lock=released; no failed, aborted, canceled, ignored or pending
examples. Actual summaries were read completely. Test compilation automatically
prepared Cozy runtime classpath/manifest through the corrected prerequisite;
no manual runtime preparation, extra generation or packaging was selected.

- 822 log: /tmp/skill.cncf.d/a-09d194ca-ba8a-4200-a0d7-51dded05aa8e/22827-20261002T133312Z.log
- 822 summary: /tmp/skill.cncf.d/a-09d194ca-ba8a-4200-a0d7-51dded05aa8e/22827-20261002T133312Z.summary.json
- 823 log: /tmp/skill.cncf.d/a-9cf52ad2-1d6f-4091-9beb-e64ca7fdf7dc/23278-20261002T133416Z.log
- 823 summary: /tmp/skill.cncf.d/a-9cf52ad2-1d6f-4091-9beb-e64ca7fdf7dc/23278-20261002T133416Z.summary.json

The real Service/launcher internal probe exercises actual one-pool SQLite
commit, every staged document row, physical close/reopen, removed/changed
candidate naming policy, valid later native history, missing Create grant,
occupied map/index/candidate, independent malformed original/effective/document
rows, wrong operation, different participant, forged authentication and genuine
commit followed by refused inert row observation and later recovery. Raw
positive/negative relation examples additionally cover unchanged preallocation,
mixed Built/Requirements documents, crossed receipt fields and first-history
facts, and inability to construct private Observed. Fixtures seed original
Start data privately; this is not evidence of real human consent or production
default public advance installation.

The complete consumed-ID ledger is now 492 through 823. Initial validation
repairs remain exhausted 3/3; separate attributable pre-review recovery batch
used 1/3. All previous counters are unchanged. There is no live SBT session,
Git mutation, shared-skill installation, upstream edit, new full-suite receipt,
ordinary PLAN-owner/profile attestation, independent review or acceptance
commit. Mechanical diff/owned-untracked whitespace checks passed. Preserve
original authority/base/binding gaps; do not infer them from successful tests.

IMPLEMENT validation is complete, not Phase closure. The ordinary next gate is
independent protected review, still unperformed under the retained no-agents
recovery. The next technical integration frontier is original AI-completion
capture/observation and canonical public current-state routing using this same
mapping—not another planning recomputation or snapshot-returning facade.
All original end-state obligations listed above remain in scope and the
original Goal stays active.
