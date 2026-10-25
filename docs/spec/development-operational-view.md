# Development Operational View

Contract authored on 2026-10-25 for Control Center Phase 5.1 / CC51-S2.
The admitted Unit B source implements the normal provided service, exact
association, read-only native projection and managed native event producers
below. Compilation, executable specifications, independent review and consumer
acceptance remain separate evidence; this source record claims no installation
or successful execution. Native Session history and existing work are retained.

## Owners and usage

sm-workflow owns current development state. The existing CNCF Service Bus
EventStore owns committed significant development events. Control Center reads
both through an ordinary authenticated sm-workflow service and composes its
shared Operational Summary for Web and Menu Bar. Current state and history can
be available independently. Neither source reconstructs or certifies the other.

The initial producer supports native GoalPhase Sessions. SplitPhase and
RepositorySync observations require their own actual retained-state adapters;
they return unsupported until implemented. No generic WorkflowEngine instance
map is substituted for a native GoalPhase Session. The managed development
Journal (Finding, ExecutionObservation, EvidenceStorageObservation) retains its
separate contract and is not the Service Bus journal.

## Exact subject and configuration

`DevelopmentSubject` contains these required opaque, exact identities:

| Field | Meaning |
| --- | --- |
| `car` | Developed CAR coordinate: group, artifact, version; it is not the generator or sm-workflow CAR coordinate |
| `producerComponentId` | Actual namespace-qualified sm-workflow Component identity |
| `producerInstanceId` | Actual owning Component instance identity, using its canonical representation |
| `workflow` | `GoalPhase` in the initial adapter |
| `sessionId` | Actual native Session identity |
| `repositoryIdentity`, `phaseIdentity` | Exact identities admitted in the retained native Start |

All fields are required in v1, with no latest-session search or inferred default.
One host-selected association connects that subject to the native declaration.
The producer verifies its own Component/instance and retained repository/Phase/
Session; it does not trust an incoming CAR association as authorization. A
canonical Component instance ID need not identify an OS process; no process UUID
is invented. A deployment requiring a stronger incarnation identity must supply
an actual host-owned binding and revise the contract explicitly.

The optional initialization parameter
`sm-workflow.development-observation` has schema
`sm-workflow.development-observation.v1` and contains one exact `subject` plus an
optional `ownerDetailsUrl`. It binds an already existing Session and producer;
it never starts, migrates or selects a new Session. The deployment uses existing
ordinary parameters, datastore purposes, managed resources and grants. The
parameter accepts no paths, SQL, provider, collection or storage selector. It
grants no access. An absent parameter disables this read capability explicitly.
Multiple associations and automatic association updates are outside v1.

Control Center explicitly selects the producer and the same subject. Coordinates,
versions and opaque IDs are compared exactly, including SNAPSHOT versions; a
matching repository basename, display name, image or Launcher lease is insufficient.
IDs must be nonblank and have no surrounding whitespace or control characters;
each encoded identity is at most 1024 UTF-8 bytes. Malformed UTF-16, including an
unpaired surrogate, is rejected before UTF-8 encoding can replace it. Oversized
identities are rejected, never shortened. Changing association is host configuration, not an
observation request or implicit permission to edit the consumer's settings.

## Public operations and results

The normal provided `DevelopmentObservationService` exposes two ordinary
operations. Their transport follows CNCF's existing Operation ABI; read-only
semantics do not imply a newly invented HTTP route or unauthenticated GET.

- `readDevelopmentState(DevelopmentStateReadInput)` accepts `schemaVersion`
  `sm-workflow.development-observation.v1` and the exact `subject` only.
- `readDevelopmentHistory(DevelopmentHistoryReadInput)` accepts the same fields
  and optional `limit`, default 20, integer 1 through 100.

Every valid response carries the supported schema and actual `observedAt` from
the current call's clock. Observations echo the verified bound subject and actual
producer; a refusal never echoes an unverified association as an owner fact.
Absent configuration supplies no invented subject. Validation and ordinary
authorization precede storage access. Unsupported wire versions and unknown request fields are
invalid; they cannot select another decoder. A result contains either the safe
observation or a finite reason, never raw controller/record/Continuation JSON,
Plan contents, paths, argv, error logs, credentials or a Human evidence token.
There is no query expression, storage selector, arbitrary filter or caller URL.

| Result status | Meaning |
| --- | --- |
| `observed` | Bound source read and safe projection succeeded; history may be an empty retained window |
| `not-configured` | Capability has no host-selected association |
| `unsupported` | Workflow family or committed-history capability is not implemented |
| `not-found` | Authorized exact native Session is absent; not a completed or idle Session |
| `permission-denied` | Existing Operation/participant/EventStore read authority is insufficient |
| `unavailable` | Selected source could not be read, including an unsupported managed backend |
| `invalid-source` | Subject, canonical payload, revision consistency or shape failed validation |
| `changing` | A bounded consistent snapshot could not be obtained during concurrent progression |

Malformed requests fail as `invalid` before lookup. Ordinary authentication
failure remains the normal protocol denial. Error details are fixed safe codes;
provider exception text is not returned. A status without a valid observation
carries no state/history facts. Control Center retains independent source
statuses, maps its own bounded timeout to unavailable/timeout, and never upgrades
these statuses to health, success, idle, zero progress or completion.

## Current-state projection

A successful state observation contains an opaque native `revision`,
`workflowState` from the actual retained lifecycle and safe `stage` when the
existing native decoder establishes it. Optional fields are `stepIdentity`,
`workKind`, `actionIdentity`, `providerIdentity`, `workerIdentity`, `startedAt`
and a finite `attention` (`human-required`, `repair-required`, `none`, `unknown`).
Every value needs its actual retained source; absence is represented by omission.
An accepted semantic result is not Step acceptance or Phase completion. A failed
check or a pending repair does not itself establish `GoalPhaseFailed`.

The owner uses the existing managed retained-session read and native state
decoders, within the current ActionCall and resource lease. Reading may not call
Start, Advance, Submit, recovery, candidate observation, Git, SBT, a provider or
an acknowledgement hook. It creates no intent, continuation, history entry,
event or repair attempt. Extract a genuinely read-only decoder from progression
where necessary; a `historyonly` argument to Advance does not prove this property.

The adapter fixes one actual revision for projection and rechecks it once after
the read. A changed revision returns `changing`; it does not loop or advance.
`stateChangedAt` is optional and distinct from `observedAt`: re-reading an old
state does not create a new transition time. Elapsed time may be derived only
from a real start time and observation time. Unknown provider/worker/time facts
stay absent. They do not prevent other valid state facts from being displayed.

## Significant event producer and persistence

The sm-workflow native transition/completion owner is the writer. It derives safe
events from actual applied native transitions, issues and accepted decisions,
using the existing managed Workflow APIs' event-record arguments/callbacks.
Events commit with the corresponding native transition through CNCF's existing
EventStore persistence selection, cursor and delivery-index mechanisms. They
are dispatched only after verified native commit/readback. Control Center and
execution providers do not author these outcome events.

The initial required facts are `GoalPhaseStarted`, `GoalPhaseCompleted`,
`GoalPhaseFailed` when a genuine terminal failure is committed, and
`ApprovalRequested`, `ApprovalAccepted`, `ApprovalRejected` where the real
Human question and decision semantics match. Deferred or cancelled questions
retain their actual semantics; do not label them accepted or rejected merely
because a Submit was accepted. Generic lifecycle aliases need an explicit mapping
and must not duplicate one transition as separate apparent actions. Action,
Judgment and ProviderExecution events are emitted only at their corresponding
actual persisted milestones; otherwise they remain future vocabulary.

The event envelope uses `sm-workflow.development-event.v1`, the exact subject,
`eventType`, `nativeRevision`, actual operation/continuation causation when
available, `occurredAt` from the transition clock and finite safe outcome metadata.
The committed record retains its producer-created `eventId`; the EventStore
assigns the positive store sequence. Event
identity/causation is frozen by the normal transition intent, not regenerated
on each observation or retry. The existing host policy selects persistence for
this vocabulary. `JournalPolicy.AUTHORITATIVE` in older notes expresses that
requirement; it is not a claim that a same-named runtime enum exists.

The initial native lifecycle has no committed terminal-failure state, so it
does not emit `GoalPhaseFailed`. Approval issue events apply to real Plan revision
and dependency-scope questions; accepted/rejected events apply to their actual
native Human answers, not continuation/renewal choices. Public causation is the
bounded owning `GoalPhaseService.<operation>@<nativeRevision>`; raw artifact
references, paths and evidence tokens are excluded. The private stable event
identity also includes the actual immutable native causation. Start, issued
question and terminal occurrence times are retained in their existing managed
payloads. Managed completion captures one event batch before the commit callback.

The ordinary host must select `development` events for persistent storage and
provide its existing managed event and history-view authority. Producer
preparation verifies the actual EventStore policy through
`WorkflowEventSelection.prepareFromStoreC`; `persistent=true` alone grants
nothing. These requirements do not alter production parameters or grants.

No outbox, second store, after-response dual write, automatic replay or new retry
controller is added. Existing retained/unknown-effect handling remains decisive:
unknown commit/readback is not a publication receipt, and uncertain effects are
not blindly repeated. Delivery failure after confirmed commit leaves history
committed; it does not roll back an accepted business result or prove delivery.
Before this producer is installed there is no historical backfill claim.

## Committed history read

The history operation delegates to a CNCF-owned **generic** managed read of the
declared EventStore event collection. CNCF owns event metadata, persistence,
query bounds, sequence ordering, introspection authority and resource leases.
Only sm-workflow interprets `sm-workflow.development-event.v1`, CAR coordinates,
native Phase/Session identities, lifecycle vocabulary and their relationships.
CNCF codecs and history binding must contain no such application schema or types.

Existing `EventRecord.attributes: Map[String, String]` carries generic correlation
metadata without changing the canonical V1/V2 event envelopes. The planned generic
history contract reserves two attributes, `history.namespace` and `history.key`.
Namespace is an exact nonblank identifier of at most 128 UTF-8 bytes and key an
opaque exact nonblank identifier of at most 256 UTF-8 bytes, with no surrounding
whitespace or control characters. Both must be present or both absent. These
bounds fit the existing attribute encoding; they grant no authority. Existing
trace `correlationId` is preserved and is not repurposed for this lookup.

sm-workflow owns the mapping: namespace `org.simplemodeling.textus.development.v1`
and key lowercase hex SHA-256 of UTF-8 compact JSON of this ordered string array:

```text
["sm-workflow.development-subject.v1", car.group, car.artifact, car.version,
 producerComponentId, producerInstanceId, workflow, sessionId,
 repositoryIdentity, phaseIdentity]
```

Use the exact strings without normalization or inferred values. This bounded
lookup key is not an authorization, consent or event-acceptance proof. CNCF
does not compute it, decode it, inspect the subject payload or recognize its
namespace. sm-workflow recomputes it from the verified configured subject and
checks every returned full envelope/subject; a collision or mismatched payload
is invalid-source, never a silently discarded row or a false association.

The planned generic reader accepts a namespace/key, a finite exact event-name
selection and a limit, from the already authorized calling Operation. For this
application, sm-workflow supplies the mapped key and its own fixed supported
event names. CNCF applies only these generic predicates and persistent-record
selection at the owning storage boundary **before** ordering and limiting.
It verifies generic projected metadata, event name and sequence against decoded
canonical EventRecord bytes. sm-workflow subsequently validates its application
schema, full subject and safe fields for every returned record. A bad record
invalidates that history observation; local filtering may not claim completeness.

The missing infrastructure is source-bounded **managed committed** reading and
generic same-row lookup projection. Current `EventStore.Query` has no attribute
correlation predicate; its in-memory mirror, raw SQL, development Journal or
filtered `event.search_event` response cannot supply this capability. Add no
application-specific field to that Query or the storage codecs. Generic query
fields are written with the existing event row in the same managed commit.
No independent correlation table is introduced. New fields do not rewrite legacy
event bytes or migrate existing rows. Legacy/unindexed rows remain explicitly
outside v1 coverage; they are never reported as a complete absence of history.

Return the newest matching `limit` events in descending numeric EventStore
sequence, reading at most `limit + 1` matching rows to determine `truncated`.
Use a single bounded provider query/snapshot. A backend that cannot perform the
required predicate/order/bound returns unavailable/unsupported; do not compensate
with an unbounded scan or guessed latest row. Sequence is scoped to the selected
EventStore and is serialized as a decimal string, avoiding JavaScript precision
loss. It orders commits, not timestamps. No cross-store sequence comparison.

Each event projection contains only event identity/type, sequence, exact subject,
native revision, occurred time, and available safe action/outcome fields.
`observedAt` records the read time. No invented commit/delivery timestamp is
returned. The result reports `coverage=retained-v1-window`, `returned` and
`truncated`; it makes no total-count, all-history, gap-free or catch-up guarantee.
An empty observed window means no indexed matching retained v1 events. Future
watch/offline catch-up needs a separately specified cursor/retention contract.

Current and history responses are checked against the same complete subject.
They are not an atomic combined snapshot. A mismatched subject is invalid-source.
A history event whose numeric native revision is newer than the observed state
is labelled ahead-of-state pending refresh; it cannot mutate or overwrite state.
It remains a historical fact. Same-source opaque revisions are not ordered unless
their owning contract declares them numeric, as native GoalPhase currently does.

Control Center preserves original source times. Its configured maximum read age
and existing clock-skew tolerance determine stale/future observations. An old
event is not stale merely because it occurred long ago; a cached history read
can be stale. Refreshing the Summary cannot renew cached source timestamps.

## Authentication and details destination

Normal caller authentication, Operation authorization, actual participant/scope
admission and managed storage leases remain required. Event history also uses
the existing `EventPolicyEngine.authorizeIntrospection` (`event_view` or existing
equivalent authority). Administrative Summary access does not silently grant
participant or event permissions, impersonate the Session owner, or install grants.
Refusal is an explicit source status. Read-only access supplies no execution,
Human decision, publication, replay or storage write authority.

`ownerDetailsUrl` is optional host configuration for this exact subject, returned
only after existing URL/origin policy validation. It opens an actual owning
development-control surface; no sm-workflow Dashboard route currently exists in
this contract. V1 uses no template interpolation, query-token injection or
operation URLs. Absent or invalid destinations remain unavailable. Authentication
occurs at the owning surface. Control Center applies its existing link policy
again; it neither embeds credentials nor provides Phase 6 admission controls.

## Required evidence

Executable Specs must establish no effects from repeated state/history reads,
exact association/refusal, optional facts, concurrent revisions, source freshness,
bounded ordered committed history, canonical envelope/row agreement, managed
lease closure, rollback/unknown/delivery separation, safe details and normal
factory/ABI binding. Persisted event reads must survive a normal host restart;
an in-memory test alone is insufficient. Consumer coverage includes the declared
`org.simplemodeling.textus.controlcenter.development.DevelopmentCarSummarySpec`
and ordinary Summary/API/Web/native integration. Existing S1 receipts remain S1
evidence. The [design](../design/development-operational-view.md) names concrete
implementation units and validation requirements; this document records no test
execution, installed API, live event, Step acceptance or Phase completion.
