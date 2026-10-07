# CNCF Phase 88 S12: canonical multi-guard transaction design

Date: 2026-09-30
Status: validated pure input, same-owner memory, default-deny policy, Subsystem host construction and internal admitted route candidates; public provider atomicity/readback still pending
Depends on: accepted S10 EventRecord batch and S11 authoritative readback

## Problem

The current canonical `DataStoreConditionalTransitionPlan` guards the Workflow
instance root and one Continuation successor. Its other effects are Create-only
records. A global EventStore sequence cursor is a third independently revised
record. Adding a cursor Save to `sideEffects` would overwrite competing writers
and cannot prove a contiguous, unique global sequence. Calling ordinary
`EventStore.append` after the Workflow commit would split the transaction.

## S12 boundary

Introduce an opt-in canonical datastore capability for one closed transaction
containing the existing root/Continuation transition, a bounded vector of
additional *independently checked revision updates*, and Create-only side
records. Each additional update has an exact collection/entry identity,
expected revision and required fields, next revision, and typed changes. It is
not a generic callback or an unguarded Save. The old two-record API retains
its behavior, and stores lacking the new capability reject the composition
before writing. The memory provider stages one replacement state; SQL performs
all guards and writes on one physical connection and commits once. Any failed
guard or Create collision rolls back all records. A commit acknowledgement
followed by bad authoritative readback is transaction-indeterminate, never
proof of rollback or permission to replay.

The transaction must reject duplicate/overlapping targets across the root,
successor, additional guards and side records, null/oversized plans, unsupported
collections, invalid revisions and fields, and ambiguous component ownership.
Cross-component cursor authority cannot be granted merely by supplying a
`componentOwner` string: a trusted host binding must admit the permitted
Workflow and EventStore owners, canonical collections and one borrowed physical
datastore. This admission contract is part of S12 implementation, not an
implicit right granted by the low-level record plan. No executable operation,
Skill or AI action receives a transaction callback.

The required caller route is Workflow → `DataStoreSpace` → one physical
provider. The S12 Workflow adapter holds `DataStoreSpace`, never a raw
memory/SQLite provider. `DataStoreSpace` starts default-deny, compares the
plan with host-installed exact owner/root/successor/guard and side-Create
collection permissions, resolves every collection, rejects a split provider
domain, then calls only an opt-in multi-guard capability. The binding is
immutable, tied to that `DataStoreSpace`, and issued only during trusted
runtime/component assembly from canonical descriptors through a host-only
constructor/factory path, with no public setter. The current
package-limited plan scope is not that host grant. CNCF's
`docs/design/workflow-multi-guard-transaction.md` records the matching
route gates and executable cases.

The executable specification must exercise the Workflow adapter and
`DataStoreSpace` route, plus real memory/SQLite atomicity, rollback,
independent writers and post-commit readback. A counting fake may establish
pre-provider rejection. Missing/divergent readback after a commit is
transaction-indeterminate and cannot be replayed. Focused-287 proves only
same-owner internal memory staging. A direct SQLite method was withdrawn
before testing; no public S12 provider is accepted.

## Host admission refinement (2026-09-30)

The present `DataStoreSpace` is mutable (`addDataStore`, `useDataStore`, and a
thread-local `bindDataStore`); it cannot safely treat a construction-time
provider lookup as permanent authority. The existing Workflow persistence
binding also resolves a managed provider inside a `Subsystem`/`SystemNode`
lease. S12 therefore needs a *call-scoped* admitted view created by trusted
component assembly **inside that lease**. It must carry canonical component
identities and exact permitted collections, and hold one resolved physical
provider for the entire call. The operation must check the caller-supplied
plan against this host view before touching the provider; the plan's
`DataStoreConditionalMultiGuardScope` remains data, never a grant.

No caller-facing `grant(ownerName, collections)` setter or public factory is
acceptable. Host assembly must derive the Workflow and borrowed component
identities from canonical component declarations; if the EventStore owner or
its cursor/event/index collections are not declared, admission fails closed.
Every root, successor, additional guard, and Create collection must resolve
to the call's one provider object. A changed thread-local binding, lease
expiry, or a second wrapper for the same SQLite path is not silently treated
as the same provider. Where the managed resolver returns distinct wrappers,
the host must explicitly supply a single shared managed view or reject the
composition; path-string equality is not a transaction guarantee.

Route implementation starts with the default-deny entry and pure comparison.
The remaining route specs must use counting fakes to show that wrong
owner/collection and split-provider admission reject before provider
invocation. A positive fake spec must then prove the exact admitted view
reaches the opt-in capability. Only after that route is fixed should memory
and SQLite provider behavior be promoted to public S12 acceptance.

The default-deny `DataStoreSpace` entry and the separate deterministic
owner/collection comparison passed focused-290 (20/20, after Cozy classpath
refresh-289). This does **not** yet prove a host-issued call-scoped view,
split-domain rejection or a positive provider call; those remain the next
route acceptance cases.

### Authority-source decision and selected-event correction

The Subsystem-owned service option below was selected for the candidate
implementation. Its typed declaration and call-scoped managed host view are
covered by focused-293 alongside selected-event policy (45/45 across five
suites). The host specs cover no-declaration/unknown-grant rejection, one
managed resource, split-resource rejection and an escaped view's rejection.
They do not yet establish a positive provider capability or atomic persistence.
Crucially, this declaration grants storage locations/permissions, not
persist-all semantics. Workflow must first evaluate the separate versioned
kind policy described in [the selected-event contract](workflow-event-persistence-selection.md).
Only the persistent subset enters event/cursor/index record preparation.
Transient events are released only after verified commit/readback, discarded
on rollback, and suppressed on an indeterminate result.

Source inspection after focused-290 found that `Subsystem` owns a fixed
`EventStore.inMemory` service. Component bootstrap borrows that service; it
does not install a persisted EventStore component. The existing
`WorkflowInstancePersistenceBinding.Declaration` supplies the Workflow
instance collection only, while the continuation collection is passed to the
prepared suspension call. There is currently no trusted declaration for an
EventStore cursor collection, event collection, delivery-key index collection,
or their storage binding. The pure S12 host policy may compare those labels,
but it cannot *issue* authority from them.

The alternatives considered were:

1. **Subsystem-owned EventStore service (recommended):** add an explicit,
   opt-in system-service persistence declaration with canonical service
   identity, exact cursor/event/index collections, and the datastore binding.
   Trusted `Subsystem` assembly validates it and borrows one managed provider
   together with the Workflow binding inside one lease. Legacy in-memory
   EventStore stays the no-declaration default and cannot obtain S12 access.
2. **Component-owned EventStore:** create/register a real canonical EventStore
   component and make its entity-space/storage declarations authoritative.
   This changes the current subsystem-wide service topology and requires a
   migration plan for existing event consumers.

Neither a caller-supplied `DataStoreComponentOwner` nor a host-policy value is
an acceptable substitute for either declaration. The positive S12 route must
remain default-deny until the selected model's source is executable-
spec verified. This decision precedes a public provider capability or SQLite
implementation.

## Sequence and delivery-key follow-on

S12 is a primitive, not itself an EventStore adapter. A subsequent producer
slice must use an explicitly bootstrapped global cursor record, read its
revision/last sequence, derive a checked contiguous range without overflow,
and atomically advance that cursor with the two Workflow records, each event
Create and each internal delivery-key index Create. EventRecord `event_json`
remains the S9 canonical form; delivery keys are not smuggled into it. The
adapter must distinguish an existing EventId, an existing delivery key, and a
stale cursor, then define exact idempotent-read and conflict outcomes. A stale
precommit proposal may be freshly prepared and re-admitted; an indeterminate
commit cannot be blindly retried. Active UnitOfWork data/closing-program
enlistment and production claim admission remain separate open gates to P2-U05.

## Executable acceptance required before checking P2-U15

1. Memory and SQLite: three independent revision guards plus multiple Creates
   succeed together and return exact authoritative records.
2. A stale third guard, duplicate side identity, or injected precommit failure
   leaves all three guarded records and all side records unchanged; two
   independent SQLite writers admit exactly one winner.
3. Overlap, unauthorized cross-component binding, unsupported provider and
   malformed/oversized input reject before publication.
4. After acknowledged commit, missing/divergent readback of *any* guarded or
   created record is transaction-indeterminate with no success projection.
5. Existing S3-S11 tests still pass. The focused and related regression
   receipts must bind the same complete source tree before acceptance.

S12 does not close P2-U05, provide a sequence cursor or constitute a production
EventStore. The pure plan contract passed focused-286 after Cozy classpath
prerequisite-285; earlier failed attempts and both accepted receipts are in the
[S12 contract journal](../journal/2026/09/2026-09-30-cncf-phase-88-s12-contract-candidate.md).
No public provider capability, S12 acceptance, commit or publication is claimed.
An [internal same-owner memory candidate](../journal/2026/09/2026-09-30-cncf-phase-88-s12-memory-staging.md)
subsequently passed focused-287 (16/16); it does not establish cross-owner
admission, SQLite behavior or post-commit readback.

## Internal admitted provider route (2026-09-30)

WorkflowPreparedMultiGuardTransition retains a DataStoreSpace, not a raw
provider. DataStoreSpace revalidates the plan, checks host policy and live
issuing-thread lease before lookup, and requires every collection to resolve
to the same admitted provider object. It alone constructs an opaque invocation;
the internal EntityConditionalMultiGuardDataStore seam rechecks provider
identity, policy and lease after lookup, immediately before provider dispatch.
A throwing route is classified transaction-indeterminate, with no automatic
replay or successful Workflow completion projection.

DataStoreMultiGuardRouteSpec supplies an opt-in counting fixture returning
NotMatched without writes. Its ten examples cover one admitted invocation,
unadmitted/null input, owner/collection/guard mismatch before lookup, split or
rebound providers, unsupported providers, released/escaped leases, release
during lookup, cross-thread use and provider exception without replay. No
production memory or SQLite provider implements the seam. This is internal
route admission evidence only, not cross-owner commit, rollback, readback,
selected-event dispatch or public provider capability acceptance.

Attempt 296 stopped before compilation on stale Cozy classpath; prerequisite
297 completed compile/classpath export. Attempt 298 compiled production code
but failed three test line-break expressions; its receipt was invalidated by
repository-tree drift and is not acceptance evidence. After correcting the
expressions, settled-tree attempt 299 passed 60/60 tests in seven suites,
with accepted receipt, exits 0/0 and lock released. S12, P2-U15, P2-U16 and
P2-U05 remain open. Full-suite validation was not performed.

Focused-301 then passed 65/65 in eight suites, adding five real same-owner
memory staging examples behind a test-only opt-in through the admitted route.
Three guards and two Creates update together; stale third guards, second
Create collisions and four pre-publication failures preserve every record.
Cross-owner remains rejected. Fixture store reads are diagnostic observations,
not an admitted Workflow readback contract or delivery authorization. The next
contract must retain/read back all guarded and Create records through the same
admitted provider before success projection; the current two-record result
does not prove that. S12/P2-U15 remain open. See the
[memory route journal](../journal/2026/09/2026-09-30-cncf-phase-88-s12-memory-route.md).

## Native memory acknowledgement and complete readback (2026-09-30)

The private verified route and EventStore-owned selection passed focused-309
(107/107 in 14 suites). Memory post-images were then moved from fixture-side
preload reconstruction into the provider's actual staged state. The internal
same-owner acknowledgement captures root, successor, additional guards and
Creates before publication and exposes Committed only after state replacement.
Existing two-record callers retain their result; no production opt-in is added.

Focused-310 passed 144/144 in 18 suites, including nine complete readback
examples and existing conditional transition regressions. New cases retain
prepared post-images after later storage mutation and reject all four
pre-publication checkpoint failures without acknowledgement or writes.
Exit 0/0 and lock=released were observed. These are focused tests, not typed
Phase/PLAN receipts or production delivery acceptance. Cross-owner memory,
SQL native transaction/snapshot, host bootstrap and selected cursor/index/event
commit/dispatch remain open; S12/P2-U15/P2-U16/P2-U05 are not complete.

## SQLite native candidate and logical storage representation (2026-09-30)

Focused-316 passed 164/164 tests in 20 suites. The internal same-owner SQLite
candidate uses one write transaction for guards, Creates and schema expansion,
retains precommit full post-images, and reads all targets in one native snapshot.
Eleven test-only route cases cover rollback (including added columns), independent
writers, reopen, uncertainty and rejected authority. No production opt-in is added.

Physical SQL id is checked against the target, then removed from the logical
Record; business writes to that reserved column reject before staging. Logical
field order alone is ignored during pure update/Create validation. Duplicate
fields are rejected, not collapsed, and values and complete ordered targets
remain exact. SQL acknowledgement and readback use the same sorted logical
representation, preserving exact full-vector equality. Existing row/schema APIs
are not globally changed. Snapshot-phase competing-writer evidence passed
focused-317 (165/165 tests in 20 suites): native SQLITE_BUSY during paused
readback, a successful explicit later write, and retained post-image revision.
Managed host/cross-owner activation and selected cursor/index/event dispatch
remain open.

## Managed host authority boundary (2026-09-30)

Focused-318 passed 169/169 tests in 20 suites. Real Subsystem-managed SQLite
resolution now has verified-route denial/lifecycle examples for exact declaration
permissions, issuing thread and lease lifetime, caller binding preservation,
provider replacement, and exception cleanup followed by a fresh lease.
The host-issued view still reaches an unsupported verified capability; this
is not a successful cross-owner transaction or production activation. The next
implementation boundary must preserve that authority in a managed verified
provider, without widening legacy or same-owner SqlDataStore entry points.

## Explicit managed verified provider (2026-09-30)

Focused-319 passed 173/173 tests in 20 suites. A separate internal Subsystem
verified route checks shared managed resource identity and borrows a non-owning
SQLite view. Its opaque invocation is validated through native staging and
precommit, then complete snapshot readback. Cross-owner Workflow records and
EventStore cursor with event/index fixture Creates commit and recover exactly;
stale cursor and second Create collision roll back. Ordinary SQL/raw host views
and same-owner entry points are unchanged. This supersedes the previously open
managed connection step, not the remaining concurrency/revocation or canonical
selected-event sequence/record/dispatch runtime integration gates.
