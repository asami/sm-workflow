# EventStore selection provenance and publication handoff

Date: 2026-09-30
Status: implementation contract; no runtime activation or acceptance

## Current source gap

The legacy Subsystem default remains EventStore.inMemory without a persistence
policy. Focused-344 now validates trusted EventStoreConstruction service capture
and observeHostEventsC: the verified host selects ordinary records through its
captured service, without caller-supplied selection or revision. Service identity
checks reject another store even with the same policy revision, and reject
caller-created selections. Nested bootstrap does not replace a captured service;
escaped leases and legacy no-policy hosts reject the new route. These are
internal source checks, not application bootstrap or publication acceptance.
EventStore.PersistenceSelection is a public case class; policyRevision and
decoded selected facts cannot establish provenance by themselves. No existing
snapshot or classifier is permission to publish.

## Trusted bootstrap and selection

Keep storage declarations and persistence policy separate. Trusted bootstrap
must install one captured EventStore service with its fixed versioned exact-kind
policy independently of EventStorePersistenceDeclaration's resource/grants.
Preserve the legacy no-policy construction for legacy memory operation; it must
fail closed on the new managed preparation route. Do not infer a policy from
collections, sender persistent flags, a request revision label or observed rows.
Do not invent the application's durable-kind catalog to activate the route.

The internal host entry consumes component identity, ordinary transactional
events and the managed environment, not a caller-created WorkflowEventSelection,
EventStore, policy or policyRevision. It captures the installed service by object
identity, resolves the installed grant/declaration, acquires the live lease,
and asks that service to select the batch. The selected batch is passed directly
to request capture inside the same host scope, not exported and resubmitted.
Policy evaluation, target generation, closure, classification and preparation
are deterministic Workflow/runtime work, never skill/AI routing.

Existing pure selection and fixed-target/selected observation APIs remain useful
for compatibility and specs. They must not be promoted into publication entry
points simply because their revision/records match. Bootstrap's configuration
identity and captured service, not a revision string alone, identify the policy.

## Opaque immutable preparation

Only trusted host assembly constructs the preparation. Bind it to the installed
service/policy, selected batch identity, declaration/grant, pinned cursor,
provider view/physical resource, issuing thread and live lease. Construction
requires successful native closure, strict interpretation and classification.
No public case-class copy, JSON reconstruction, user-provided evidence vector,
decoded record, hash or matching owner label can mint that provenance.

Freeze storage evidence into closed immutable facts:

- cursor schema, revision and canonical last-sequence text;
- exact typed event targets and canonical retained V1/V2 envelope bytes;
- exact index targets and canonical five-text-field index content;
- explicit absent incoming targets and required closure membership;
- genuinely new canonical V2 templates, classifier dispositions and new count.

Raw mutable logical Records must not be retained as validation authority or
returned for a caller to amend. Produce fresh decoded Record/EventRecord copies
only as pure projections of the immutable preparation. Mutation of source
hidden keys, returned payload projections or returned logical Records must not
alter the cursor predicate, observation inventory or intended Creates.

Do not encode an incoming retained payload merely to capture the request: its
exact retained bytes win. Canonical admission/freeze of genuinely new durable
facts occurs after classification; unsupported new payloads reject before any
write. The handoff must not later consult source EventRecords to build storage
effects. Transient delivery remains an original-order, in-memory batch concern,
not durable evidence or a replay source. Its application completion/delivery
adapter is a separate gate; arbitrary transient payloads do not become storage
authority by passing through this handoff.

## Native publication contract

Native publication accepts only the original host-bound preparation and frozen
Workflow transition. Revalidate host/service/domain/thread/lease identity and
all exact cursor/present/absent/closure observations inside the write transaction
before staging. Reject stale evidence without automatic retry/reclassification.
All-retained durable input uses an explicit validation-only cursor predicate,
not a dummy guarded update. All-transient/empty input performs no cursor work.
The complete postcommit readback covers retained facts as well as new Creates.
Storage verification alone still grants neither application completion nor
dispatch/redelivery permission. Focused-347 now validates the internal mixed/new
durable managed SQLite route: host-bound generated effects, full native write
transaction observation revalidation and retained/new complete readback
(228/228, 26 suites). Transient-only completion is still unimplemented;
retained-route-specific concurrency/rollback/authority evidence
and production runtime/application dispatch acceptance remain open. See the
[publication journal](../journal/2026/09/2026-09-30-prepared-event-publication.md).
Focused-349 adds original-view identity, thread, revoked/escaped publication,
injected-effect, reserved-column and Continuation-conflict rejection with fresh
post-scope storage inspection (229/229, 26 suites). In-transaction checkpoint
rollback, revocation during staging/readback and independent writer competition
on this prepared route remain required; legacy route evidence cannot substitute.
See the [authority journal](../journal/2026/09/2026-09-30-prepared-publication-authority.md).
Focused-350 adds actual managed prepared-route rollback at all four precommit
checkpoints, actual staging versus postcommit-readback revocation, and a
latch-controlled independent JDBC writer after observation validation. It
proves exact SQLITE_BUSY, one complete nine-record storage verification and
subsequent lock release (232/232, 26 suites). These bounded cases do not close
all-retained/transient-only completion, application dispatch, crash recovery or
every commit/cleanup uncertainty case. See the
[transaction journal](../journal/2026/09/2026-09-30-prepared-publication-isolation.md).
Focused-352 adds a separate all-retained validation-only native completion:
the Workflow pair changes, while cursor/event/index do not. Five configurations
prove unchanged and exhausted cursor success, stale cursor/index and new-durable
rejection (233/233, 26 suites). Retained-specific authority, rollback, revocation,
competition and uncertainty specs remain required before application activation.
See the [retained journal](../journal/2026/09/2026-09-30-retained-event-completion.md).
Focused-354 adds retained-route-specific rollback at all four precommit
checkpoints, actual staging/postcommit-readback lease revocation, and independent
JDBC writer exclusion followed by lock release (236/236, 26 suites). Fresh reads
compare every retained/cursor field and confirm no alias/new Creates. Committed
but revoked readback does not yield Verified or retry. Malformed/failed/throwing
readback, retained authority/absence/key-free cases and transient-only completion
remain open. See the
[retained isolation journal](../journal/2026/09/2026-09-30-retained-completion-isolation.md).
Focused-355 adds nine malformed native acknowledgement and twelve faulty
postcommit readback configurations at the test provider return boundary after
actual managed SQLite commit/readback (238/238, 26 suites). All classify as
transaction-indeterminate without retry/repair/compensating rollback; fresh
inspection sees the committed pair with unchanged cursor/retained facts.
This does not prove native I/O/cleanup failure or crash recovery. Retained
authority/absence/key-free cases, transient-only completion and runtime dispatch
remain open. See the
[retained readback journal](../journal/2026/09/2026-09-30-retained-completion-readback.md).
Retained host/thread/revoked/released/escaped authority spec is now written but
UNVALIDATED: attempt 356 stopped on stale Cozy classpath before test compilation;
ordinary recovery 357 failed Cozy compile on missing sha256 members (13 errors),
so export did not run. Both locks released. Prior 238/238 does not cover the new
source. Cozy semantic repair needs its owner or explicit scope expansion; no
generator bypass or unrelated source repair was attempted. See the
[pending authority journal](../journal/2026/09/2026-09-30-retained-authority-pending-cozy.md).
The pending status is now resolved: after user-reported Cozy repair, ordinary
compile/export 358 passed and focused-359 compiled the unchanged spec and passed
239/239 in 26 suites. All five retained host/thread/revoked/released/escaped
configurations reject before provider writes and preserve full stored fields.
Other authority/absence/key-free and transient-only/runtime requirements remain
open. See the
[resumed authority journal](../journal/2026/09/2026-09-30-retained-authority-resumed.md).
Focused-361 proves key-free retained V1/V2 and incoming-key index absence:
unchanged cases verify the four-record pair/cursor/event without index creation;
an index created after observation rejects completion and remains untouched
(240/240, 26 suites). Absent target authority requires provisioned schema;
missing collection is not absence. Invalid transition coverage, transient-only
completion and common host/runtime routing remain open. See the
[key-free/absence journal](../journal/2026/09/2026-09-30-retained-key-free-absence.md).
Focused-363 adds retained foreign-owner/collection/effect/reserved-id rejection
and external root/successor conflicts (241/241, 26 suites). A coherent foreign
owner pair rejects at host admission; root conflict is read-only NotMatched,
successor conflict rolls back. All retained/cursor fields and external changes
remain exact, with no retry. Transient-only/empty native completion and common
host-owned routing are the next implementation boundaries, not skill-side
dispatch. See the
[transition journal](../journal/2026/09/2026-09-30-retained-transition-admission.md).
Focused-365 implements a separate transient/empty native completion through
captured EventStore selection and an opaque live-host pair invocation. No
cursor/event/index observation or allocation is performed. Empty/transient and
malformed cursor cases verify exactly two Workflow records; durable/mixed input
rejects (242/242, 26 suites). Transient-specific lifecycle/rollback/uncertainty/
authority evidence and common deterministic routing remain open. Storage
verification grants no transient dispatch or application/UoW completion. See the
[transient implementation journal](../journal/2026/09/2026-09-30-transient-empty-completion.md).
Focused-367 adds four transient precommit checkpoint rollbacks, actual staging/
postcommit-readback revocation and empty/transient pair success with existing
durable facts (245/245, 26 suites). Durable observations are zero and the SQL
hook captures no event-domain statements; this is not an exhaustive metadata
audit. Fresh inspection distinguishes rollback from committed-unverified pair
state with exact unchanged durable fields. Transient authority/readback faults/
writer competition and common routing remain open. See the
[transient isolation journal](../journal/2026/09/2026-09-30-transient-completion-isolation.md).
Focused-369 adds six transient absent/unconfigured-service/thread/revoked/
released/escaped-host rejections and ten malformed acknowledgement or faulty
readback return configurations (247/247, 26 suites). The former start no provider
write; the latter leave the actual committed pair intact but return indeterminate
without retry/compensation. Transient ordinary-input admission is not a retained
preparation handoff. Native I/O/cleanup/crash and exhaustive malformed-shape
coverage remain distinct. Independent writer and common routing are next. See
the [transient authority/readback journal](../journal/2026/09/2026-09-30-transient-authority-readback.md).

Focused-372 adds the internal ordinary-input conditionalHostEventsVerified entry:
captured EventStore selection and frozen new-durable count mechanically choose
transient/empty, all-retained or prepared completion. Five successful input
configurations prove one selected provider route/commit/readback; four failures
prove no fallback. Independent transient pair-writer competition and postcommit
lock release also pass (250/250, 26 suites). This does not expose a public
Operation, complete active UoW or authorize dispatch. Runtime assembly and
original-order delivery remain separate next work. See the
[host routing journal](../journal/2026/09/2026-09-30-host-completion-routing.md).

Focused-375 connects the existing typed completion/prepared adapter internally
to the common host entry, requiring the identical borrowed provider. Actual
claimed WorkOrders complete through all five input modes; foreign provider,
re-claim, stale root and injected local effects reject. Failed/throwing trusted
postcommit projection is indeterminate without replay (270/270, 27 suites).
This is not active-UoW enlistment: the current public Operation still uses
separate postcommit resume/history paths. Activation must wait for one shared
commit and explicit delivery admission, not a postcommit adapter call. See the
[typed completion journal](../journal/2026/09/2026-09-30-typed-host-completion.md).

Focused-379 verifies the non-activated internal participant lifecycle, including
interrupt/control cleanup and no replay (294/294, 31 suites). Its native boundary
and ordered batch are test doubles; actual prepared-host/closing-data enlistment,
central UoW integration and actual mixed-event delivery remain open. The earlier
extra-approval stop was a parent classification error, not a `/approve` task.
See the [participant journal](../journal/2026/10/2026-10-01-isolated-managed-participant.md).

Focused-381 connects actual host storage to the non-activated participant
(299/299, 31 suites). Verified completion does not call the test delivery port
until callbacks/cleanup succeed; stale guard, projection uncertainty and escaped
lease suppress it. Foreign provider and unenlisted closing effects reject.
Actual closing/data enlistment and production event dispatch remain open. See
the [host participant journal](../journal/2026/10/2026-10-01-host-storage-participant.md).

Focused-383 composes host-admitted local revision guards and Create-only records
with the actual new-durable native transaction (301/301, 31 suites). Complete
readback includes local records; postcommit local readback failure is uncertain,
not rollback. Standard grants do not expand. Retained/transient local composition,
closing-program interpretation and production dispatch remain open. See the
[native local-intent journal](../journal/2026/10/2026-10-01-native-local-intent-composition.md).

Focused-385 completes internal local-intent native composition for every event
mode and connects it to the typed Completion participant (304/304, 31 suites).
Retained is validation-only; transient/empty has no event-domain SQL. Zero-guard
Create-only input needs no dummy cursor. Local intent cannot write the captured
EventStore reserved collections. Actual admitted closing-program interpretation,
immutable local handoff, production grants/UoW and dispatch remain open. See the
[all-mode journal](../journal/2026/10/2026-10-01-all-mode-local-completion.md).

## Required executable evidence

1. Host preparation uses its captured policy-bearing service despite caller
   persistent flags; different event kinds select durable/transient normally.
2. Legacy unconfigured service and supplied policy/selection/revision substitutes
   cannot authorize preparation/publication; missing host/grant/domain rejects.
3. Same revision on another store/policy cannot substitute for service identity.
4. Source keys and all exported projections may be mutated without changing
   frozen observation predicates, new count, envelope bytes or intended Creates.
5. Unsupported retained incoming payload succeeds by retention; unsupported new
   payload rejects with unchanged storage/cursor and no dispatch.
6. Wrong thread, escaped scope, release/revocation and another provider/resource
   suppress usable preparation. Decode/cleanup failure issues no preparation.
7. No write, schema expansion, cursor reset or dispatch from construction.

Native publication races/rollback, validation-only completion and configured
runtime/launcher/MCP acceptance remain independent requirements. Existing
focused-341 does not prove these new provenance/handoff requirements.
Focused-344 adds bounded host-selection evidence (225/225, 26 suites), but no
application publication adapter exists yet. Focused-345 adds an initial opaque
ManagedEventPreparation that freezes native rows before export and rechecks the
captured host on each fresh projection (225/225, 26 suites). Focused-346 now
exercises populated retained V2 event/index projections, unsupported retained
incoming payload, source/projected key mutation, later actual DB changes and
direct preparation rejection on release, wrong thread and actual revocation
(227/227, 26 suites). Later DB changes do not update frozen facts: native
write-time validation remains mandatory. The initial internal native route is
recorded above; full publication coverage and dispatch remain open.
See the [independence journal](../journal/2026/09/2026-09-30-event-preparation-independence.md) and
[handoff journal](../journal/2026/09/2026-09-30-immutable-event-preparation.md).
