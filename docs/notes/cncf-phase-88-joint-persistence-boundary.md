# CNCF Phase 88 joint persistence boundary

Date: 2026-09-30
Status: S3-S10 bounded record compositions validated; no production joint runtime claim

## S3 bounded producer slice

Use canonical EntityConditionalTransitionDataStore for one transaction that
guards the stored instance revision, appends its suspension history and creates
the exact Available Continuation. Reuse WorkflowInstanceAtomicTransitionV1
pure admission, WorkflowInstanceRecordJsonV1 catalog rehydration and
ContinuationRecordJsonV1. The unchanged instance and Continuation SPIs remain
available. Add the compound operation on the explicit borrowed provider, not
as a public Operation, an alternative engine or a Skill-selected action.

Both collections must be explicit, distinct canonical EntityStore collections
in the same borrowed physical datastore. Reject cross-store delivery and
missing compound/CAS capability; never emulate atomicity using two calls.
Configuration/policy identity is retained with both payloads. The provider
owns no SQL, JDBC connection or resource lifecycle.

Continuation claim/release/complete use canonical Entity CAS and authoritative
readback. Claims persist across owner reconstruction; no timeout, automatic
release or uncertain-effect replay is inferred. A caller must provide the exact
current claim token for release/completion. The storage revision is distinct
from the Continuation's immutable StateMachine expected revision.

Executed acceptance: successful paired readback and reopen; duplicate Continuation
rollback; stale and divergent instance rejection; provider failure rollback
after successor/root/before commit; one paired suspension winner and no orphan
across two physical SQLite adapters; one concurrent claim winner; exact token
and completion replay rejection; policy/payload drift rejection without write;
revision movement between read and commit; committed-but-unadmittable readback
classified transaction-indeterminate rather than safe to retry.

Focused-242 passed 24/24 tests in three suites, including all ten S3 examples.
Regression-243's initial timeout was resolved by limited recovery; its stale
Cozy classpath failure was then closed by prerequisite-244. Regression-245
completed 124 suites with 1024 succeeded, 0 failed, 8 existing live MySQL cases
canceled and 5 existing examples pending. This is selected producer regression,
not whole-CNCF full-test, downstream artifact or production acceptance. See the
[S3 journal](../journal/2026/09/2026-09-29-cncf-phase-88-paired-suspension.md).

Owned producer sources: WorkflowInstanceDataStorePersistence.scala,
WorkflowContinuationDataStorePersistence.scala and
WorkflowInstanceDataStoreSuspensionSpec.scala under the CNCF checkout.
S2 Component/Factory bootstrap and all unrelated sources remain unchanged.
The paired transaction commits records, not an AI result or external command.
Schema preparation may precede the transaction; rollback proof concerns record
state, not absence of schema DDL. Reopen tests reconstruct SQLite resources
in one process, not a separate-process launcher restart.

## S4 bounded issued-work publication and recovery

WorkflowIssuedWorkDataStorePersistence adds exact typed issued payload to the
new Continuation Entity created by S3's canonical compound transition. Initial
publication, instance history and Available Continuation share one commit.
Canonical side-effect Save is deliberately not an immutable-create substitute:
it could overwrite. There is no second store call or post-commit publication.

Host binding fixes Component identity, completion Operation, application codec,
catalog, configuration and both collections. Admission/recovery checks the exact
handle, run, Continuation, StateMachine revision, operation/input/result types,
context and completion/evidence contract plus matching suspension history.
Strict codec round-trip and authoritative issued-byte readback are required.
The unchanged issued-work SPI accepts only an already published identical retry;
missing issued payload on a legacy pair rejects without retrofit.

Focused-248 passed 34/34 tests in four suites, including all nine S4 examples.
Regression-249 completed 125 suites: 1033 succeeded, 0 failed, 8 existing live
MySQL cases canceled and 5 existing examples pending. Both completed with
SBT/wrapper exit 0 and lock=released, binding unchanged S4 source identities.
See the [S4 journal](../journal/2026/09/2026-09-29-cncf-phase-88-issued-work-publication.md).
Claim/release/complete preserve the immutable issued property, including SQLite
resource reopen. Read/retry confer no claim or execution authority.

## S5 bounded guarded completion and retained result

Existing canonical Create/Bind cannot update both existing records with guards.
DataStoreConditionalSuccessor.Update wraps the same validated conditional root;
EntityConditionalUpdateDataStore is an opt-in capability, not general UoW
participation. In-memory stages snapshots and canonical SQL guards/locks the
second existing record in the same record transaction. No unguarded side Save,
private SQL grammar or resource lifecycle is introduced.

WorkflowCompletionDataStorePersistence binds the same explicit typed
component/Operation/codecs/catalog/store collections. The host owns the entry
and claim token; the submitted semantic result owns no progression selector.
Before writing, validate exact current suspension, issued WorkOrder, claim token,
result type/identities/context, completion facts and execution-evidence references.
One commit appends history, completes the claim and retains strict result bytes
including ExecutionEvidence and completing instance revision; issued bytes remain
immutable. Provided dispatch evidence must match, while absence remains compatible
with the existing optional Protocol contract.

Recovery requires Completed state and adjacent suspension/completion history,
even after later history, and never claims or replays work. Missing/malformed
result and legacy/orphan state fail closed without repair. Applied bad readback
is transaction-indeterminate, not retry authority. Canonical exact-value guard
limit is 4096 encoded characters; oversized claim/issued/policy bytes reject
before writing without truncation. SQLite reopen is same-process resource
reconstruction and MySQL locking code is not live MySQL executable acceptance.
SQL schema preparation may precede the record transaction.

Focused-254 passed 77/77 tests in nine suites, including eight kernel and
thirteen Workflow S5 examples. Regression-255 completed 127 suites with
1054 succeeded, 0 failed/aborted, 8 existing MySQL cases canceled and 5 existing
placeholders pending. Accepted receipts bind the same complete tree and all
nine owned source identities, with exits 0/0 and lock=released. See the
[S5 journal](../journal/2026/09/2026-09-29-cncf-phase-88-guarded-completion.md).

## S6 bounded application payload/scope retention

CandidateAdmissionSubmissionJsonV1 composes the existing AdmissionSubmission
model and evidence JsonV1 as one explicitly declared result PayloadCodec.
Candidate reference/revision, typed candidate, ordered evidence payload/scope,
requirement identity and provenance stay exact. Strict fields/schema/nested
identity and round-trip reject duplicate, lossy or throwing inputs before write.
No new evidence datastore, adapter, side Save or SQL is introduced; S5 is unchanged.

S5's same canonical completion transaction therefore retains application evidence
with the generic ExecutionEvidence, completed claim and instance history.
Recovery is read-only, requires the admitted catalog/codecs and never grants
admission, a claim or effect replay. Shape-valid stale/wrong-target/narrow evidence
is preserved and only current application EvidencePolicy determines reuse.

Focused-257 passed 36/36 tests in five suites, including all fourteen new S6
examples. Regression-258 completed 129 suites with 1068 succeeded, 0 failed/aborted,
8 existing opt-in MySQL examples canceled and 5 placeholders pending. Both
successful receipts bind the same complete tree and three owned source hashes,
with exits 0/0 and lock=released; all nine S5 source hashes are unchanged. See the
[S6 journal](../journal/2026/09/2026-09-29-cncf-phase-88-admission-evidence.md).
This is producer record retention and same-process resource reopen, not a
production host binding, separate-process restart or active UoW implementation.

## S7 prepared canonical record-plan seam

WorkflowPreparedDataStoreTransition separates preparation from one canonical
commit. The existing suspension/issued-work/completion APIs delegate to their
new prepare methods then commit, preserving exact guarded publication/readback.
Preparation reads and validates but publishes nothing, owns no transaction or
claim, and captures the same borrowed provider. withSideEffectsC accepts only
explicit normalized canonical same-store record effects under the existing
plan validator. Commit rechecks guards and preserves typed rollback/indeterminate
outcomes; post-commit projection uncertainty is never automatic retry authority.

Seventeen new examples cover memory/SQLite nonpublication, effect admission,
stale/replay/claim rejection, rollback/resource reopen and uncertainty. Focused-259
passed 70/70 tests in seven suites. Regression-260 completed 141 suites with
1158 succeeded, zero failed/aborted, eight existing opt-in MySQL examples canceled
and nine existing placeholders pending. Both accepted receipts bind the same
complete tree and seven owned source hashes; S6 remains unchanged. See the
[S7 journal](../journal/2026/09/2026-09-29-cncf-phase-88-prepared-record-commit.md).

This is P2-U10, a Work B prerequisite for full P2-U05. It supplies no EventStore
adapter, active UoW data interception, closing-program interpreter or atomic
EventEngine. A future composition must normalize transactional EventRecords and
proven staged data into that same admitted store and reject unsupported
participants before execution. Preparation is process-local, retains no lease
and confers no portable/restart authority; restart must query/re-admit facts.

## S8 bounded Create-only records

The closed EntityVersionedSideEffect.Create case uses existing canonical
normalization and the same memory replacement-state/SQL native transaction.
Duplicate identity rejects without upsert, retaining rollback of the guarded
records and earlier effects. The ordinary versioned-mutation route shares the
same semantics. Fourteen new examples include independent SQLite writers and
prepared Workflow collision after preparation.

After historical 262-264 tree-drift rejections, focused-265 passed 57/57 in six
suites. Regression-266 completed 142 suites with 1172 successful tests, zero
failed/aborted, eight existing opt-in MySQL examples canceled and nine existing
placeholders pending. Both accepted receipts bind the same complete tree and
eight owned source hashes, exits 0/0 and lock=released. P2-U11 is checked; see the
[S8 journal](../journal/2026/09/2026-09-29-cncf-phase-88-create-only-side-records.md).
This supplies no EventRecord serialization, sequence allocator, EventStore,
active UoW enlistment or AtomicWorkflowSuspensionEngine; P2-U05 remains open.

## Full joint runtime gates still open

S10 connects one complete batch of already-issued transactional EventRecords to
S7's prepared Workflow plan through S9's strict codec and S8's Create-only
effect. Codec validation precedes canonical Create-key derivation so unresolved
EventId options cannot generate hidden identity during preflight. Duplicate
keys, unordered/duplicate assigned sequences, invalid records
and an already-used event collection reject before publication. The same
canonical commit retains or rolls back all event and guarded Workflow records;
it does not call the ordinary EventStore. Six new memory/SQLite examples pass.
After raw focused-272 fixture failure/tree-drift rejection and focused-273
stale Cozy classpath failure, Cozy prerequisite-274 enabled focused-275 and
regression-276 on the first admitted S10 tree. The codec-first correction
superseded that tree; concurrent Cozy source edits then made focused-277 stop
before tests. Accepted Cozy refresh-278, final focused-279 (44/44) and
regression-280 (1190 successful / 143 suites) bind the same complete tree
`88bbea9f991d101e33b853c506b6904208b4742b0cf4217854d80000ec3a78ea`.
P2-U13 is checked; P2-U05 remains open. See the
[S10 journal](../journal/2026/09/2026-09-30-cncf-phase-88-prepared-event-batch.md).
No global sequence authority, durable delivery-key index, EventStore adapter,
active UoW data interception or closing-program enlistment is supplied.

S11 adds an authoritative post-commit readback gate for each S10
event byte string. Missing/divergent event readback after an acknowledged
guarded transition becomes transaction-indeterminate before the Workflow
projection runs. Focused-281 passed 45/45 in three suites; regression-282
passed 1191 tests in 143 suites. Both accepted receipts bind the same complete
tree `99047c6527bbcfd4c8369ee2b0b33f9e46892adeb6f63a281f5b8ee9dd65cdf9`,
with SBT/wrapper exits 0/0 and lock released. Focused-279/regression-280 remain
historical S10 evidence only. The existing conditional plan has two guarded Workflow
records and Create-only side effects: it cannot atomically reserve an
independent global EventStore sequence cursor without a new admitted
multi-guard transaction capability. P2-U14 is checked; S11 does not claim that
capability or close P2-U05. See the
[S11 journal](../journal/2026/09/2026-09-30-cncf-phase-88-event-readback-candidate.md).

The next bounded prerequisite is S12's independently guarded third-record
capability, not a side-effect Save of the global sequence cursor. Its
[design boundary](cncf-phase-88-s12-multi-guard-design.md) specifies same-store
commit, explicit cross-component host authority, rollback/readback and
executable acceptance. P2-U15 and P2-U05 remain unchecked.

S9 implements a bounded pure EventRecord codec and Create-only bridge, with
twelve executed examples. Fully issued canonical ID, time, typed payload and
assigned sequence are retained. Strict non-JSON storage prefix preserves exact
text through the unchanged SQL decoder; missing ID parts, unsupported payloads
and internal delivery keys reject. Following accepted classpath refresh-268
and scoped repair of five focused-269 failures, focused-270 passed 50/50 and
regression-271 passed 1184 tests / 143 suites on one complete tree. P2-U12 is
checked; full P2-U05 remains open. Historical rejected-267 stays unaccepted.
See the
[S9 journal](../journal/2026/09/2026-09-29-cncf-phase-88-event-record-storage-codec.md).

The existing AtomicWorkflowSuspensionEngine contract requires the active
UnitOfWork's event/data effects to share the same prepare/commit/abort boundary.
Current ordinary UnitOfWork/EventEngine does not supply that implementation.
S3 must not implement or advertise that capability merely because its two
records are atomic. A post-commit callback is not a shared transaction.

S4 supplies bounded typed WorkOrder publication; S5 adds guarded
claim/result/history completion and generic ExecutionEvidence retention.
S6 adds exact application AdmissionEvidence payload/scope inside that result.
S7 exposes the existing record plans before commit, not event/data enlistment.
Production claim admission remains open, along with active UoW/closing-program effects and
indeterminate external-effect reconciliation. Ordinary Continuation SPI calls do not validate issued bytes;
the production host must admit the configured issue before exposure.
Independent LocalJson calls are not a substitute. No AI or external command
runs in a write transaction.

After producer composition is admitted, refresh the exact CNCF SNAPSHOT before
downstream tests. Bind one production service through normal Factory/launcher
Operations first; test separate-process recovery before server/MCP concurrency.
The public sm-* skills remain semantic-result-only and cncf-* independent.
