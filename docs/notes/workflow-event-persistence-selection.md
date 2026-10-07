# Workflow event persistence selection

Date: 2026-09-30
Status: EventStore-owned policy correction passed focused-309; managed runtime integration pending

## Corrected boundary

Persist **selected events**, not the entire EventStore input stream. Preserve
the S12 storage-domain, exact-permission and atomic-update work. A Subsystem
declaration admits a destination and collection permissions only. It neither
selects event kinds nor authorizes dispatch. Selection policy and event-access
policy are separate from this storage declaration.

`EventPersistencePolicy` declares a version and unique exact `EventTypeId`
rules: `Persist` or `Transient`. In this record boundary, EventTypeId matches
`EventRecord.kind` exactly; it is not inferred from class names, payloads or
the caller's `persistent` hint. EventStore owns this fixed policy. Unmatched
kinds are transient; malformed kinds or batches reject before any writes.
There is no implicit persist-all, wildcard, or AI classification. Senders send
ordinary events without a persistence option. Per-send overrides are a future
extension and are not used by this contract.

Workflow asks EventStore for its deterministic selection once during prepare,
before sequence allocation or provider writes. `prepareFromStoreC` is the
runtime selection entry. `WorkflowEventSelection` retains the selected policy
revision, original event order, and separate persistent/transient vectors.
It normalizes `persistent` from the policy without mutating input records.
Selection changes require a newly prepared/admitted proposal; a commit must
not reevaluate a newer policy. This candidate handles unissued transactional
records only, with bounded size and unique identities.
The fixed policy belongs to the configured EventStore, never an AI result or
a skill-submitted override. The old `prepareC(policy, records)` remains a pure
compatibility planning helper, not runtime policy ownership or storage authority.

## Persistence and delivery

Only the persistent subset participates in cursor allocation, canonical event
Creates and internal delivery-key index Creates. Mixed batches allocate a
contiguous durable sequence range for that subset alone. Transient records
retain sequence zero and create no cursor movement, EventStore records or
delivery index. Empty/all-transient selections require no event cursor guard;
the underlying Workflow root/Continuation transaction still commits normally.

Both subsets remain pending until the Workflow transaction commits and every
required durable record passes authoritative readback. Durable delivery uses
those stored records; transient delivery uses the selected in-memory records.
The first implementation drains durable and transient deliveries in original
input order after success. A transient delivery is best-effort, not replayable,
and may be lost between verified commit and dispatch or on process restart.
Applications requiring reliable recovery must declare `Persist`.

On a definite rollback, discard **both** pending subsets; publish neither.
On transaction-indeterminate (including missing/divergent post-commit
readback), suppress both deliveries, retain diagnostic correlation, and do not
automatically replay/retry the transaction. A later durable recovery query
does not recreate transient events. A post-commit delivery failure does not
roll back the already committed Workflow transaction.

Nontransactional events are outside this Workflow preparation API, but the
configured EventStore applies its policy to ordinary append in either lane.
Workflow rollback cannot retract an already emitted nontransactional event.

## Candidate and remaining executable gates

The candidate policy is pure and performs no writes, dispatch, AI work or
external command execution. Its outcome-based transient-delivery projection
is a planning function, not a commit receipt or publication authorization.
Runtime assembly must derive the outcome from the actual managed transaction.

`WorkflowEventSelectionSpec` covers exact mixed selection, caller-hint
normalization, deterministic order, missing/unknown kinds, invalid/duplicate
rules, empty/all-transient batches, invalid lanes/issued sequences/identities,
rollback/indeterminate suppression, and prepare-time revision retention.
It does not prove actual persistence or dispatch. Those integration specs
must additionally prove selected-only Creates and index entries, durable-only
cursor allocation, original-order dispatch only after successful readback,
zero delivery on provider rollback/readback uncertainty, and no implicit
persist-all when a Subsystem declaration is installed.

The candidate `EventStore.inMemory(policy)` applies selection to ordinary append,
including the existing EventEngine emit path: only durable events enter records,
identity/delivery indexes and sequence allocation; transient events return in
input order at sequence zero. No caller persistence option is required. This is
in-memory retention, not managed SQLite persistence or Workflow dispatch acceptance.
The legacy no-argument store remains unchanged and cannot prepare the new route.
The legacy prepared two-record adapter is not integrated with this selection.
S12 and P2-U15 remain open until the host-admitted adapter/provider path and
integration gates pass. Append success alone is not dispatch authorization.

`EventStorePersistenceSelectionSpec` adds eight examples for ordinary emit without
sender options, durable-only retention/indexes/sequences, transient-only batches,
store-owned prepare-time revision, both lanes, rejection before mutations,
transient/durable identity isolation and first-record durable deduplication.
All eight examples passed focused-309 (107/107 in 14 suites), after Cozy
prerequisite-306 and two test-fixture corrections. All seven complete multi-guard
readback examples passed in the same run. Exit 0/0 and lock=released were observed.
This is focused evidence, not managed runtime or typed Phase/PLAN acceptance.
See the [resumed validation journal](../journal/2026/09/2026-09-30-focused-validation-resumed.md).

The [ordinary-send follow-up](../journal/2026/09/2026-09-30-eventstore-ordinary-send-boundary.md)
records the earlier unexecuted state. Its recovery interpretation was subsequently
corrected by the [platform recovery journal](../journal/2026/09/2026-09-30-platform-recovery-clause-correction.md);
the bounded prerequisite and focused tests have now executed successfully.

## Historical focused evidence (before the ownership correction)

Attempt 291 stopped before compilation/specs due to the stale Cozy generator
classpath. Prerequisite 292 completed Cozy compile/classpath export. CNCF
attempt 293 then passed five suites and 45 tests: WorkflowEventSelectionSpec,
EventStorePersistenceHostSpec, DataStoreConditionalMultiGuardPlanSpec,
DataStoreConditionalUpdateSpec and EventRecordJsonV1Spec. The host separation
example installs a declaration but selects a transient-only batch without
opening a managed resource or creating a SQLite file. All serial invocations
reached terminal completion with the lock released; 293 exited zero.
This is focused evidence, not full-suite or production delivery acceptance.

## Selected-only canonical Create preparation (2026-09-30)

`WorkflowSelectedEventRecords.prepareC` connects the persistent subset to
canonical EventRecord Creates, proposing overflow-safe contiguous sequences
only for those events. Empty/transient-only batches create no event records
or cursor movement. This is pure preparation, not global sequence allocation:
the host must atomically guard the exact cursor revision via DataStoreSpace
before the proposed range becomes authoritative. No cursor/index write occurs.

The codec sequence-copy helper retains private delivery keys, so unsupported
discriminators still reject rather than silently disappearing. Preparation
retains canonical durable bytes. `admitDeliveryCandidatesC` rejects missing,
divergent, reordered or unexpected durable readback; an exact match reconstructs
durable candidates and interleaves transient candidates in original order.
This pure comparison is not commit evidence or dispatch authorization. The host
must independently verify commit and root/Continuation/cursor/index readback;
a comparison failure after commit is transaction-indeterminate, not rollback
or permission to retry. All-transient delivery also requires verified commit.

Focused-294 passed 30/30; final focused regression-295 passed 50/50 in six
suites after adding delivery-key preservation through selection and sequence
copying. Both exits were 0/0 and the serial lock was released. Actual S12
provider, cursor/index allocation, selected-event commit/dispatch and P2-U16
remain open. This is not full-suite or production runtime acceptance.

## Selected canonical managed composition (2026-09-30)

Focused-320 passed 176/176 tests in 20 suites. The internal verified-storage
projection decodes exact selected canonical event bytes, compares the frozen
proposal and reconstructs mixed-order candidates. Real host compositions prove
selected-only Creates, durable-only sequence proposals guarded by revision and
last sequence, rollback on cursor mismatch/second event collision, and absent
transient storage after reopen. This does not authorize dispatch or accept a
versioned global cursor/index representation, all-transient runtime path or
EventStore query/replay service. The unsupported delivery-key codec gate remains.

## Cursor/index schema design continuation (2026-09-30)

The [versioned cursor/index contract](event-cursor-and-delivery-index-contract.md) is a design candidate, not
executable acceptance. It clarifies that delivery-discriminator indexes are
only for persistent events with explicit typed keys; key-free events use their
EventId collection identity. Decimal-text cursor sequences differ from prior
integral test fixtures. Full delivery facts require a separate V2 envelope;
V1 keeps rejecting unsupported keys. Pure codec/spec and host identity work
must precede managed duplicate reconciliation or actual runtime dispatch.
