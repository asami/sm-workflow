# SplitPhase planning checkpoint publication and native integration contract

Date: 2026-10-02
Scope: original sm-workflow Phase 2; admitted CNCF Phase 88 consumer
Status: source-grounded parent-only limited-recovery PLAN; not accepted PLAN

## Continuity and assurance boundary

The preceding turn made concrete progress: passive snapshot source/specs were
implemented and final representative 808 passed 15/15; affected 809 passed
52/52. Both actual summaries report exits 0/0 and lock released. That evidence
proves the passive prerequisite, not a native checkpoint, read-only recovery,
generated public advance, independent review, commit or Phase closure.

The active original Goal remains createdAt 1790766223 with unchanged objective.
Retain the user's parent-only limited recovery and no-agents direction, original
authority/base/Goal-binding/PLAN-owner evidence gaps, all previous repair ledgers,
and all original review/release obligations. Do not fabricate an original base
from current HEAD or reopen another Phase. Model/effort are not directly
observable. This turn runs no SBT, changes no production/spec/shared-skill source,
creates no runner/permission identity and performs no Git mutation. The 478
consumed identities remain unchanged. This is an authoritative design handoff
under the retained exception, not an ordinary accepted implementation manifest.

## Current-source findings that constrain the implementation

1. `WorkflowManagedMechanicalTransition.executeC` captures a Create-only program,
   prepares the native root/checkpoint pair, commits one host transaction and
   returns a privately constructed Completed only after verified readback.
   Its program preparation and createsC are private to cncf/workflow; sm-workflow
   must not call or duplicate that internal interpreter. The application instead
   builds a closed descriptor vector and checks its actual count before staging.
   Native capture independently enforces the same maximum of 64 local Creates.
2. `WorkflowMechanicalTransitionJsonV1.encodeC` bounds the complete native
   receipt at 1MiB, including both current and next instance histories. The
   existing R codec's standalone bound cannot prove that capacity. Preflight the
   actual receipt envelope with the same native codec before execution. A raw
   anticipated Receipt used for encoding is not a native Completed/readback.
3. Native `loadRetainedC` verifies its exact physical envelope, configuration,
   logical reference, Component/Operation and complete committed history prefix.
   It retains later legitimate history. It does not read application documents.
   The consumer must verify those rows and independently read current original
   and effective inputs; native receipt success alone is insufficient.
4. `C.preparePlanningC` requires the context's exact active native revision.
   `T.bindPlanningC` adds original Start/source/proposal/history relationships.
   Those are fresh-execution constructors, not recovery constructors for a newer
   instance. Recovery compares the retained original prefix/current facts without
   calling C.preparePlanningC, C.prepareC, W.advanceC, E.enumerateC or S.prepareC.
5. Existing enrichment storage stages one result publication and Built documents
   only for Q.Projected. Its old recovery reconstructs planning. Do not reuse
   either omission as the new mixed-frontier publication/recovery contract.
6. The generated SplitPhase v4 Provided API still has no advanceSplitPhase. The
   native mechanical binding accepts an Operation identity but does not itself
   prove that this Operation was generated/installed or that its actor was
   admitted. Actual production execution must wait for generated advance and
   current-call actor admission. Never append under submitPartitionSemanticResult,
   previewSplitPhase or a fabricated generated operation to conceal this gap.

Sources inspected: the four native mechanical transition/persistence/JSON/program
preparation files, ComponentRecordStorage, WorkflowInstancePersistence,
SplitPhaseDefinitionBinding, the actual CML, C/T preparation and codecs, planning
input/context/Start readers, and managed typed partition completion/publication.

## Immutable index, mapping and snapshot ownership

Use the already declared enrichment publication collection for two distinct
new record purposes; use its existing captured same-store metadata. No second
Workflow grant, checkpoint collection, pool, SQLite path or host-owned engine.
The native mechanical receipt remains in declaration.continuations, paired with
declaration.instances. Application records remain outside that reserved pair.

An immutable ContextIndex stores the exact instance identity, original context
identity/revision and complete mapping reference. Its fixed V1 lookup key is
derived only from those query facts, using a canonical JSON pair of instance
identity and context identity as the identity argument to M.keyC, with context
revision as the revision argument, and a `planning-context:` purpose prefix.
This is an explicit reversible storage locator, not a management hash, a policy
recipe, a current-HEAD identity or a newest-record selector. Bound the final
prefixed UTF-8 key to 1024 bytes; do not trim it. Confirm the decoded index's
complete query tuple independently before following its mapping reference.

Read this index before allocating candidates or selecting a fresh checkpoint
anchor. A present index selects its retained mapping/anchor; current naming
policy must not regenerate them. An absent index is not permission to overwrite
an existing mapping. Fresh anchor metadata is explicit host-selected M.Reference;
candidate allocation remains the existing policy's mechanical route.

MappingReference uses explicit identity/revision. Its physical key uses M.keyC
with a disjoint `planning-snapshot:` purpose prefix and the same final key bound.
The mapping's complete Record contains:

- mapping reference;
- complete original C.Record, including owner/configuration/Start reference;
- complete original T.Record, including command and selected input/profile data;
- closed origin: Mechanical(checkpoint M.Reference), or PartitionCompletion
  (actual continuation identity and canonical S.ResultReference);
- the complete passive R.Record, not a winner-only projection;
- original predecessor revision and committed result revision.

The native mechanical payload is this whole closed Record. The application
mapping row contains the same logical Record, and its index points to it. The
duplicate logical value is deliberate: native receipt and application locator
can be compared completely without introducing a second progression authority.
Decode is passive; native and application raw bytes are not execution grants.
An original partition AI completion captures the same R format in its mapping
row inside its existing completion transaction; it does not manufacture a
mechanical receipt or append a second mechanical history entry.

Each immutable index belongs to one exact context revision. Later scope/reasoning
work must use an explicitly admitted successor work context/record, not overwrite
this index or silently substitute its current native revision. That later
continuation design is still part of the original Phase, not permission to let
the skill select a stage or rebuild the frontier during observation.

## Capture from original execution, never from recovery recomputation

Mechanical capture receives a constructed T.PlanningBound, the exact original
W outcome and explicit mapping/checkpoint references. Separate named capture
entry points cover an unchanged selection and an actual constructed
H.MechanicalAllocated continuation; no overloaded nullable fallback.

For unchanged selection require outcome.selection to be the bound selection.
For allocated capture require allocated.need.selection to be the bound selection,
allocated.anchor to equal the selected checkpoint anchor, the outcome to carry
allocated.data, the same partition Input and the exact
original Enumerated object. Original proposal/boundary/estimate/policy data and
the entire objective input remain preserved. Do not accept a newly selected W
outcome that happens to share reference strings but has altered policy/demand.
Copy through R.projectC only; no planning processor is called by capture.

For partition completion capture consume its actual private P.Plan, original
pending binding and final mechanical outcome after candidate allocation. Retain
the canonical submitted continuation/result identity and actual plan.next.
DecisionNeeded with no mechanical outcome produces no fake R snapshot; its
genuine human direction/Decision remains a separate required integration.
Never re-enter P.recoverC/P.recoverPlanningC to build this mapping.

Mechanical preparation derives one active unsuspended HistoryEntry with the
next sequence/revision, unchanged correlation and semantic progression, explicit
checkpoint causation and no suspension. Check and pre-encode the anticipated
next instance/native receipt; neither is a commit acknowledgement. Do not store
an application locator in the CML state name or reuse derived-composite/entity-
transition fields as unrelated metadata. Actual native execute still independently
reads the canonical current instance and admits the one-append transition.

## Complete publication and capacity preflight

Prepare an ordered descriptor vector, retaining the entire R frontier separately:

1. Newly allocated planning row when its complete W.Data differs from the
   original retained context planning row. If unchanged, independently verify
   and reuse the original row; do not Create its already existing identity.
2. Proposal, ownership and handoff rows for every actual Built member, including
   Built members inside Q.Requirements. Use the existing F codecs and actual
   assigned references. No documents are invented for semantic/Decision members.
   Local handoffs remain in R; they do not disappear with an empty cross-child
   handoff document.
3. Complete mapping row and ContextIndex row, with their disjoint physical keys.

Use canonical declared purposes for all physical targets. Require duplicate-free
complete target+entry tuples and no collision with the reserved instance/native
receipt targets. Check the actual vector length <=64, including both application
mapping/index rows; do not infer the limit from a candidate count alone. Original
AI completion additionally includes its actual new estimates/handoff publication
effects and existing closing effects in the total.

Pre-encode each row and the entire anticipated native receipt with the same
closed codecs used at commit. An oversized whole history/frontier or Create set
is a bounded host-capacity refusal, not an AI request, truncation, split commit,
retry loop or successful empty publication. Native capture/encoding independently
recheck their own limits. Stage only this already checked vector through freshly
bound ComponentRecordStorage.stageCreateC; retain no core/pool/provider/lease.

## Read-only recovery and uncertainty

The managed boundary first checks the current owning Component/Operation/actor,
then reads the exact index and mapping. Follow the stored origin, not a policy
allocator. Mechanical origin loads the exact native Retained and compares its
whole payload with the mapping. Completion origin loads the actual issued work
and retained generic completion and checks the actual canonical result reference,
closing result and committed native prefix without semantic re-admission.

Read original context and typed Start independently and compare complete records.
Read all nine original purpose rows against originalContext.facts and all nine
effective purpose rows against R.selection; compare complete data, not IDs alone.
Read every actual Built proposal/ownership/handoff row and compare its complete
data with the retained R member. Independently current original Start selection,
source and any originally selected proposal remain required. Later legitimate
native history is retained, but cannot make a foreign mapping/prefix current.

Recovery's private managed observation is not C.PreparedPlanning or W.Outcome.
It grants no execution simply by decoding. It performs no E/Q/A/O calculation,
allocation, AI issue/resend, human verification, native append or application
write. A pure shape-inspection helper may return only inspection/data, not a
claim that SQL readback or actor admission occurred. Immediate uncertain commit
observation stays uncertain; only a later independent read may establish the
same retained outcome, without another execution.

Public advance must return canonical terminal or actual pending WorkOrder/
Decision first. It must not rerun a checkpoint, issue duplicate work or ask the
skill to route a host-data/partition/scope/reasoning stage. Its operation and
result schema, generated ABI/catalog and default multi-workflow installation
remain necessary public work after the passive publication prerequisite.

## Exact next implementation frontier

Implement a passive publication preparation prerequisite, not native execute or
a fake installed advance. This closes the missing complete input/Start/origin
wrapper, actual publication vector and whole-envelope capacity checks before
the managed execution boundary is installed.

The mapping wire schema is `sm-workflow.split-phase-planning-checkpoint.v1`,
storage field `splitPhasePlanningCheckpointPayload`, native payload type identity
`SplitPhasePlanningCheckpoint`. The index schema is
`sm-workflow.split-phase-planning-context-index.v1`, storage field
`splitPhasePlanningContextIndexPayload`. Both use their exact schema prefix and
closed `{schemaVersion,payload}` envelope with a whole UTF-8 1MiB bound.
Mapping wire leaves use the existing complete prefixed C/T frames and the existing
R payload JSON envelope; decoded in-memory data retains actual C/T/R Record
types. Close nested unknown/missing data through the owning existing codec and
canonical semantic re-encoding, not a legacy adapter or executable constructor.
Mapping/index codecs must compare exact selected references and complete cross-
record source/Start/instance/configuration/origin/revision relationships. These
remain passive shape checks; they do not verify a provider or authenticate an
actor. Preserve R's exact BigInt decimal strings unchanged.

The count boundary spec may use complete duplicate-free passive descriptor
vectors to test exactly 64 versus 65; state that it is a capacity-shape test.
Do not manufacture private W candidates or native Completed values just to
reach those vector lengths. Actual capture tests retain their real whole
frontiers and derived descriptor counts.

New production files under the existing admission package:

1. SplitPhasePlanningCheckpointPreparation.scala: closed index/mapping/origin
   data; private captures from actual constructed mechanical and completion
   values; exact original/allocated relationships; passive inspection only.
2. SplitPhasePlanningCheckpointJsonV1.scala: closed V1 index and mapping codecs,
   reusing full C/T/R codecs rather than deriving or admitting executable values;
   native PayloadCodec for the complete mapping Record.
3. SplitPhasePlanningCheckpointPublication.scala: ordered complete row descriptors,
   fixed reversible index/mapping locators, same-store declared target mapping,
   duplicate/count/row/native-receipt preflight. No native execute or SQL write.

New executable specs, one per production responsibility:

4. SplitPhasePlanningCheckpointPreparationSpec.scala
5. SplitPhasePlanningCheckpointJsonV1Spec.scala
6. SplitPhasePlanningCheckpointPublicationSpec.scala

All six paths are under src/main/scala or src/test/scala respectively at
org/simplemodeling/textus/smworkflow/admission. Evidence ownership is this journal
and docs/phase/phase-2.md. Existing processor, fixture, context/Start/raw codec,
completion, enrichment, CML, generated binding/catalog/Factory, upstream, build
and shared-skill files are excluded from this prerequisite. Inspect any proposed
new necessary source or contract change in parent PLAN before editing it.

Reuse the actual typed Start/partition/allocation fixtures without editing them.
Positive capture tests must construct their inputs through real APIs, including
P.Plan when testing original AI completion capture. Raw codec/anticipated native
receipt tests are explicitly passive shape/capacity tests, not execution proof.
Use adjacent Given/When/Then and should matchers in every behavioral group.
Required cases: unchanged/preallocated versus actual mechanical allocation;
changed same-reference objectives/refused crossed original inputs; both origins;
all mixed frontier Built documents/local handoffs; no duplicate Create for an
unchanged retained planning row; injective Unicode/delimiter keys and final key
bound; complete wrapper roundtrip and unknown/missing/crossed fields; true UTF-8
row limits; actual 64/65 descriptor boundary; native envelope overflow even when
R alone fits; no fake snapshot for DecisionNeeded; private constructors/nulls.

Frozen validation requests through fresh permissions and the existing serial
wrapper under retained parent-only recovery:

```text
["--batch", "testOnly org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningCheckpointPreparationSpec org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningCheckpointJsonV1Spec org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningCheckpointPublicationSpec"]
["--batch", "testOnly org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningSnapshotSpec org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningSnapshotJsonV1Spec org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedPlanningBindingSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedPartitionCompletionSpec org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnrichmentPublicationSpec"]
```

Class C, settled bounded preparation with medium confidence; estimated slice
execution 45-120 minutes from six files, reuse of existing typed/raw APIs and
already measured focused tests. No new whole-Phase packing/profile claim. Ordinary
protected acceptance still needs original admitted authority/base/binding/lease
and independent conformance review; these remain missing, not inferred here.
New prerequisite pre-review repairs start 0/3 without resetting passive snapshot
3/3, producer 3/3, test-settings 1/3 or any Step/Phase review repair counter.
No full suite, duplicate compile or producer refresh is selected absent an
actual dependency failure. Preserve all unrelated dirty/untracked work.

## End-state fidelity and next state

Next state: IMPLEMENT this exact passive publication prerequisite under retained
limited recovery, without claiming ordinary PLAN admission. It is not a substitute
for the requested native connection. The following integration still must install
generated advanceSplitPhase before production execution; bind current-call actor
and immutable context index before allocation; enlist these actual Creates and
native checkpoint in one same-pool transaction; verify whole-set readback and
read-only current-prefix recovery; capture the same format during original AI
completion; and deliver actual partition/scope/reasoning WorkOrders and human
Decisions with full document publication/application. CLI/server/MCP/separate
processes, default GoalPhase+SplitPhase assembly, generic packaged sm-* skills,
Workflow-owned external commands/merge routing, independent original Step reviews
and exact commits, one Phase full review/full validation/release remain required.

## Implementation candidate (not acceptance)

The six frozen source/spec files now exist. Capture keeps the full original
Start/context and distinguishes an anticipated mechanical append from the actual
partition-completion origin. Closed mapping/index codecs reuse their owning
leaf codecs. Publication preparation derives all Built document rows, including
mixed requirement frontiers, skips unchanged planning Creates, and checks the
whole anticipated native envelope and complete descriptor count before staging.
No managed writer, native execute, installed advance or readback is asserted.

The specs construct actual bound planning/allocation/completion values. They
cover unchanged data, crossed selections, both origins, DecisionNeeded without
a fabricated snapshot, closed frames, mixed documents/local handoffs, declared
targets, Unicode keys, 64/65 descriptor counts including closing effects, UTF-8
bounds and whole-native-history overflow. Raw descriptor count tests are passive
capacity tests, not executable closing programs. Validation is pending; repair
count remains 0/3. Existing parent-only recovery and original closure obligations
remain unchanged. No shared skill, upstream source or Git mutation is selected.

### First validation and bounded compile repair

810 reached SBT and terminated with exits 1/1, `lock=released`, before tests.
The new capture imported ContinuationIdentity from WorkflowProtocolV1, but the
actual protocol identity lives in cncf.workflow; the wildcard then selected
the different instance-persistence identity. Correct only that import, retaining
the actual submitted continuation and existing owner identity. The third error
was a cascading inferred-Any value. Repair 1/3, not a permission failure or a
semantic contract change. Re-run the exact representative command on this
settled correction under a fresh identity.

810 log: /tmp/skill.cncf.d/a-a75d0379-0ef4-43e9-b164-308180e06b39/612-20261002T123645Z.log
810 summary: /tmp/skill.cncf.d/a-a75d0379-0ef4-43e9-b164-308180e06b39/612-20261002T123645Z.summary.json

811 compiled all three production sources and automatically prepared official
runtime artifacts through Test/compile. Test compilation exposed the inverse
namespace error in the new local fixture: WorkflowContinuation is inside
WorkflowProtocolV1 (already wildcard-imported), while ContextReference is in
cncf.workflow. Correct only this test import. Repair 2/3; exits 1/1 and lock
released, no tests executed or skipped. Exact representative command remains.

811 log: /tmp/skill.cncf.d/a-a1798cd5-011c-4382-9a0e-bdc5e6fb02a0/1101-20261002T123745Z.log
811 summary: /tmp/skill.cncf.d/a-a1798cd5-011c-4382-9a0e-bdc5e6fb02a0/1101-20261002T123745Z.summary.json

812 compiled production and tests, automatically prepared runtime, and executed
all 13 tests: 12 passed, one failed. The mixed scope fixture's handoff spans two
two-item source children; internalizing the complete handoff needs all four
180-minute items in one child, exceeding the 480-minute ceiling. Therefore its
Built members correctly have no local handoff. Keep that mixed-frontier document
test intact and add an independently constructed four-single-item source
handoff, whose two-item internalization is feasible. Assert local handoff
retention on that real frontier, not on the impossible shape. Repair 3/3;
no production change, expectation waiver or invented candidate. Exits 1/1,
lock released; exact representative revalidation remains selected.

812 log: /tmp/skill.cncf.d/a-67e0ce28-c307-4d56-bfe8-cbabc777ac66/1569-20261002T123846Z.log
812 summary: /tmp/skill.cncf.d/a-67e0ce28-c307-4d56-bfe8-cbabc777ac66/1569-20261002T123846Z.summary.json

## Settled focused validation and next integration boundary

813 passed **13/13 tests in three new suites**. 814 passed **32/32 tests in
the five exact frozen affected suites** on unchanged source/spec bytes. Both
actual complete summaries report exits 0/0, lock released, and zero failures,
aborted suites, canceled, ignored or pending tests. The observed count for this
affected command is 32; do not copy the earlier broader 809 accumulator's 52.
Both normal testOnly invocations automatically produced the official runtime
classpath and development manifest via the corrected Test/compile dependency,
without a separate cozyPrepareRuntime invocation. No clean-generation claim.

813 log: /tmp/skill.cncf.d/a-efa3cbb2-9b29-495e-963f-95df4ef50926/2148-20261002T124012Z.log
813 summary: /tmp/skill.cncf.d/a-efa3cbb2-9b29-495e-963f-95df4ef50926/2148-20261002T124012Z.summary.json
814 log: /tmp/skill.cncf.d/a-36fd39aa-29b1-4b3d-b003-e0561f246141/2541-20261002T124055Z.log
814 summary: /tmp/skill.cncf.d/a-36fd39aa-29b1-4b3d-b003-e0561f246141/2541-20261002T124055Z.summary.json

Retained correction ledger: this prerequisite 3/3, passive snapshot 3/3,
producer 3/3, test-settings 1/3. Complete append-ordered execution ledger is
483 identities through 814. No full-suite run, source/spec changes while SBT
was live, independent review, Git mutation, new Phase or acceptance occurred.
Tracked diff-check and explicit whitespace checks for all six untracked owned
source/spec files and this journal pass. Green passive checks do not establish
atomic storage, installed public advance, authenticated actor or recovery.

Next state is parent integration PLAN: install the generated public advance
contract/current actor before production execution, read the immutable index
before allocation, stage this complete vector with the native checkpoint in
one same-pool transaction, independently read back every retained input/document,
and integrate the same mapping format into the original AI completion transaction.
Recovery must stay observation-only with no enumeration/allocation/semantic
re-admission. Original scope/reasoning WorkOrders, genuine human Decisions,
default multi-workflow assembly, transports, external commands, packaged skills
and all original review/commit/release obligations remain open. No new source
frontier or ordinary accepted PLAN is inferred by this handoff.
