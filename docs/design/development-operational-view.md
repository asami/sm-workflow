# Development Operational View Integration

Designed 2026-10-25 for CC51-S2 and implemented in the subsequently admitted Unit B
source scope. Follow the [specification](../spec/development-operational-view.md).
The source includes CML/ABI, normal factory service, native owners and executable
specifications. This record claims no executed-suite result, installation or
consumer acceptance; those require their actual separate evidence.

## Unit B implementation boundary

`DevelopmentOperationalView` owns strict wire decoding and safe result fields;
`DevelopmentEventCorrelation` owns the exact ordered-tuple SHA-256 lookup.
`GoalPhaseNativeObservation` reads the actual canonical retained Session through
`retainedSessionOptionC`, validates the bound repository/Phase and projects only
the native InstanceRecord. One final managed read returns `changing` with no
stale state if the selected record differs. It reaches no progression, provider,
Git, acknowledgement or completion code. Optional worker/provider/time/work
details remain absent when this restricted decoder has no actual safe source.

`DevelopmentObservationService` is generated from authored CML and bound by the
normal launcher assembly. State and history fail independently. History uses
`ManagedEventHistory.bindForCallC` with fixed namespace, exact key, finite names
and limit, then validates every complete application envelope; it never filters
after the limit. Unknown fields and duplicate nested Record identities are
refused. Provider exception text cannot become a public observation.

`GoalPhaseDevelopmentEvents` supplies event records to the existing native Start,
issued Human decision, actual decision-completion callback and terminal mechanical
owner. The actual EventStore persistence selection is checked with the public
`WorkflowEventSelection.prepareFromStoreC`, without reparsing host configuration.
The final native transition reuses exact `completionEntryC` current/already-committed
guards and acknowledgement behavior in `GoalPhasePostCompletionSync` and
`GoalPhaseNativeRepositoryClosure`. No after-return store or second controller is
introduced. Stable occurrence times are retained in ordinary payloads; the normal
managed intent retains selected event identity, causation and commit/readback.

Terminal publication requires the separately owned generic CNCF mechanical
predicate to permit an unsuspended Active record's exact contiguous Completed
entry with no progression. The prior generic predicate allowed Active successors
only. The admitted CNCF prerequisite changes that predicate and its two existing
specifications; no application event vocabulary enters CNCF. Publication and
qualification of that prerequisite are required before these terminal fixtures
can establish successful integration.

Native GoalPhase has no terminal Failed lifecycle, so no failure event is invented.
Committed event/state survival after the test-owned post-commit response hook
failure is distinct from confirmed Service Bus delivery. Existing managed delivery
failure can return an error after a real commit; it must not trigger replay or
erase committed business state. Generic CNCF tests own rollback, uncertain commit
and dispatch protocol guarantees; application and fresh-CLI fixtures must verify
the actual integration, with no pre-seeded event or Workflow rows.

## Inspected owners and connection points

Repository identities, rather than checkout directory names, define ownership.
The inspected CNCF source is the Control Center-selected checkout of
`asami/goldenport-cncf`, HEAD `1150f07ce45b99bf48cce9d055c7db7c68ea6ebd`
with retained admitted S1 changes. sm-workflow and Control Center are their
existing workspaces. The caller retains its actual Session, original base,
accepted S1 commit `c971975e8a0fa50a4e948d7afca4eea5d68a8c6c`, all failed
attempts, verification and counters. No stored Session is edited by this work.

| Actual source | Observed behavior | Planned connection |
| --- | --- | --- |
| `GoalPhaseNativeRun.retainedSessionC` | Reads actual managed Start/current InstanceRecord, verifies host scope and participant | Owner-side read adapter, with explicit absent/error classification and no Git-root observation |
| `GoalPhaseNativeWorkContinuation` | Retained decoding and progression share a large implementation; Advance may dispatch, persist or run work | Extract a restricted retained-state projection; never invoke Advance as a read |
| `ThreeWorkflowLauncherAssembly.bindC` | Binds native progression, preparation, development Journal and Evidence services | Add the real provided observation service to the normal factory and ABI, not only a test assembly |
| `WorkflowManagedMechanicalTransition.executeC` | Accepts EventRecord batch; managed host commits and dispatches verified records | Start and actual terminal/mechanical milestones |
| `WorkflowManagedIssuedWork.suspendC` and `WorkflowManagedIssuedDecision.suspendC` | Accept event records with actual native suspension | Attributable actual work/Human milestones |
| `WorkflowManagedCompletion.bindC` and managed decision completion | Native completion event callback and post-commit dispatch | Accepted semantic/Human outcomes with precise business meaning |
| `WorkflowEventSelection`, `WorkflowSelectedEventRecords` | Host persistence selection, event/cursor/index proposals and authoritative readback | Reuse unchanged ownership and commit protocol |
| `EventRecord` / `EventRecordJsonV1` / `EventRecordJsonV2` | Generic `attributes` map is already encoded unchanged; current Create rows contain `event_json` | Add generic namespace/key/name/sequence same-row lookup projection, without application envelope decoding |
| `EventStorePersistenceDeclaration` / `Subsystem` | Captured service collections, Workflow grants, managed borrowed resource binding | CNCF-owned restricted history reader with the existing lease and read authority |
| `EventStore.Query` / built-in `event.search_event` | Query has no subject correlation; built-in search has no target/limit input; inspected default EventStore is in-memory | Insufficient for durable subject-bounded history; do not treat the mirror as the managed journal |

These are source observations, not live installed-operation evidence. The dated
2026-09-24 event journal and original event notes express intent; no matching
named GoalPhase/Approval producer was found in the inspected Scala/CML. Existing
managed durable event machinery is real and should be extended at its normal
producer arguments, rather than replaced with an after-response publish call.

## Owner read adapter and normal service

Introduce `DevelopmentOperationalView` as the typed public contract/projection
and `DevelopmentObservationService` as the ordinary Operation binding. A
`GoalPhaseNativeObservation` owner adapter consumes the actual native retained
Session and decodes only safe retained state, work kind and attention. Refactor
the needed read-only fold from `GoalPhaseNativeWorkContinuation` into that adapter
or a small reusable read-only function. Its dependency surface contains reads
and pure decoders, not execution/acknowledgement/transition ports. Existing
progression delegates to shared decoding where appropriate; no duplicate state
machine or projected workflow store is introduced.

The observation configuration is decoded through ordinary
`ThreeWorkflowRuntimeParameters`, bound to the actual declaration and Component,
and passed to the same factory. Session selection remains exact. `not-found`
requires an actual absent retained Start, not string matching an exception.
Participant denial remains denial. To obtain a consistent state, the adapter
reads the selected retained revision, performs its pure projection, and compares
one final current revision. A difference returns changing with no state facts.
It never calls `GoalPhaseNativeRun.sessionC` for a Git root/branch check, runs
`_advance_retained_c`, or invokes retained completion synchronization.

Declaration/capability absence is returned as configured absence or unsupported.
The successful service records observation time from the current ExecutionContext.
No display field comes from a user-controlled error message or provider log.
If normal read authority cannot currently be expressed by the existing host,
report that deployment limitation; do not bypass participant admission.

## Event generation at existing native owners

Introduce a pure `GoalPhaseDevelopmentEvents` constructor. It receives the
actual selected subject, native transition revision, immutable transition or
continuation identity, native business outcome and call clock. Produce bounded
transactional EventRecords through existing event identity construction, with
stable causation captured by the normal intent. It cannot write, dispatch,
allocate a sequence, grant persistence or create a new repair loop.

Connect it to native Start's saved mechanical transition, work/Human suspension,
managed completion event callbacks and actual terminal progression. Reuse the
records parameters listed above. The normal EventStore host policy must select
these events for persistence. Failure to satisfy that existing policy/grant is
an explicit capability/deployment failure, not permission for local storage or
a caller `persistent=true` bypass. Do not fabricate failure/completion events
from logs, AI summaries, failed tests or transport acceptance.

An initial association is configured before new attributable events can be
written. Already running Sessions may subsequently be read without rewriting
their history. No retroactive publication of old transitions is implied. Read
traffic emits no authoritative lifecycle event. Detailed ProviderExecution and
Judgment producers remain separate until connected to real persisted milestones.

## Generic CNCF correlation and sm-workflow interpretation

The earlier application-column design would make CNCF event codecs depend on
sm-workflow. It is superseded here before implementation or Submit. No inspected
CNCF source justifies that dependency. Existing generic metadata is sufficient
to carry a producer-defined lookup association; its managed query connection
still needs an ordinary generic infrastructure extension.

Concrete source evidence, relative to the selected CNCF repository:

- `event/EventStore.scala`: EventRecord already has `payload: Map[String, Any]`
  and `attributes: Map[String, String]`; EventRecordFactory preserves reception
  attributes. Existing trace correlationId is separately populated for native
  lifecycle events. Query currently selects name/kind/lane/persistence/time and
  offset/limit, not arbitrary attributes or development identities.
- `event/EventRecordJsonV1.scala`: `_attributes` / `_decode_attributes` encode the
  generic string map (up to 256 entries, keys up to 128 characters, values up to
  8192 characters). V2 embeds that same generic body. Application payload codecs
  do not belong in either file.
- `workflow/WorkflowManagedMechanicalTransition.scala`, issued-work/decision and
  completion peers accept EventRecord batches; they do not require sm-workflow
  envelope types. Existing persistence selection, cursor/index readback and
  post-commit dispatch remain their owners.
- `event/EventStorePersistenceDeclaration.scala` and `subsystem/Subsystem.scala`
  own the actual declared managed EventStore and lease. The default in-memory
  EventStore and built-in unbounded searches do not establish managed history.

The proposed CNCF `EventHistoryCorrelation` is only a bounded pair of opaque
strings from attributes `history.namespace` and `history.key`. CNCF validates
the generic pair, metadata shape and canonical record consistency. It neither
knows CAR, Phase, Session, the development envelope, its namespace value, nor
the producer's key calculation. Namespaces are values, not an application
registry or permission. Records with neither attribute remain unchanged;
one-sided or invalid pairs are invalid rather than silently indexed.

sm-workflow owns construction of the namespace and SHA-256 lookup label defined
in the specification, exact subject-to-key mapping and all development decoding.
Its `GoalPhaseDevelopmentEvents` constructs safe envelope and attributes from
the same admitted subject. Its `DevelopmentEventCorrelation` helper centralizes
that mapping. On read, `DevelopmentObservationService` computes the requested
pair, supplies its fixed event-name vocabulary, then validates every full result
envelope/subject/producer/native revision and verifies the mapping again. A
malformed, mismatched or colliding application record invalidates the whole
observation; it is not dropped after limiting. This is application validation,
not another access check or a cryptographic authority token.

### Separately scoped CNCF prerequisite

The missing **generic managed committed-event reader** is named
`ManagedEventHistory`, bound by the existing EventStore-owning Subsystem. It
returns canonical typed EventRecords and bounded history metadata to the
authorized calling Operation. sm-workflow converts those records to its safe
public history result. Control Center never sees the raw generic record API.
No sm-workflow source parses storage rows or opens SQL.

Planned `ManagedEventHistory.Query` contains only `correlationNamespace`,
`correlationKey`, `names` (1 through 32 distinct exact event names, each within
the existing 128-character EventRecord name bound), and `limit` (1 through 100,
default 20). It is a typed input to the existing EventStore owner's managed read
binding, not a second bus/store/query engine or a remote public service. Ordinary
EventStore.Query retains its current behavior; no claim is made that it already
supports this managed predicate or that an unbounded adapter can supply it.

The V1/V2 Create helpers call the generic projection helper on attributes/name/
sequence alone. Four same-row scalar fields support the fixed managed query:

```text
event_history_namespace = EventRecord.attributes["history.namespace"]
event_history_key       = EventRecord.attributes["history.key"]
event_history_name      = EventRecord.name
event_history_sequence  = committed EventRecord.sequence (numeric Long)
```

These generic fields are derived inside the existing event Create and verified
against its canonical bytes, with no payload decoding beyond the generic event
codec. Codecs may not import sm-workflow, match a development schema/namespace,
decode CAR/Session fields, compute application hashes or recognize lifecycle
names. Tests must exercise at least two unrelated producer namespaces/payloads
through exactly the same code path; no special application branch is permitted.

The read uses captured managed service datastore/lease and existing introspection
authority. Apply exact namespace/key, finite names and persistent transactional
predicates before descending numeric sequence and limit+1. Verify every returned
row's generic metadata against canonical EventRecord bytes. Unsupported query
providers return an explicit failure; do not use in-memory/unbounded fallbacks.
Return a retained indexed window with no completeness/backfill claim. Legacy
bytes, old row recovery, unrelated records and existing collections are retained;
no additional table, cursor reset, migration or new revision allocator is added.

The pair is an association supplied by an authorized producer, not a proof of
origin or permission to read another scope. The calling Operation still owns its
business authorization; EventStore introspection and host-owned leases/grants
remain independently required. sm-workflow binds the configured exact subject
before calling this generic reader and never exposes arbitrary pair/name input
in its public DevelopmentHistoryReadInput.

An in-memory EventStore or missing/incompatible datastore query capability must
not satisfy the durable read. The existing managed provider must demonstrate
predicate pushdown, numeric ordering and a bound in an isolated provider Spec.
If that cannot be achieved using the normal DataStore query surface within the
literal unit below, return the exact provider gap and a separately bounded scope
proposal. Do not scan and filter all rows, add raw SQL to sm-workflow, or silently
expand the CNCF source scope.

## Original dependency scope and current status

The following Unit A/B lists preserve the original dependency split and literal
source scope. They are historical scope records, not new proposals or a request
to repeat admission. The Unit A managed-history implementation is now present in
CNCF; Unit B's subsequently admitted source implementation is described above.
Publication, installation, executed-suite evidence and Control Center acceptance
remain separately attributable to their actual owning work. The Unit C consumer
boundary below remains an anticipated, separately owned integration.

### Unit A: CNCF committed-history implementation (original prerequisite scope)

The concrete prerequisite file set, relative to `goldenport-cncf`, is:

```text
docs/spec/managed-event-history.md
docs/design/managed-event-history.md
src/main/scala/org/goldenport/cncf/event/EventHistoryCorrelation.scala
src/main/scala/org/goldenport/cncf/event/ManagedEventHistory.scala
src/main/scala/org/goldenport/cncf/event/EventRecordJsonV1.scala
src/main/scala/org/goldenport/cncf/event/EventRecordJsonV2.scala
src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala
src/main/scala/org/goldenport/cncf/subsystem/EventStoreHistoryBinding.scala
src/test/scala/org/goldenport/cncf/event/ManagedEventHistorySpec.scala
src/test/scala/org/goldenport/cncf/event/EventHistoryCorrelationSpec.scala
src/test/scala/org/goldenport/cncf/subsystem/EventStoreHistoryBindingSpec.scala
src/test/scala/org/goldenport/cncf/datastore/sql/ManagedEventHistoryQuerySpec.scala
```

At proposal time, the new-file entries named implementation targets. Existing
EventStore persistence declaration/selection and managed Workflow commit APIs
were to be reused; that scope granted no redesign of them. Unit A establishes
the exact normal public binding signature used by Unit B. Installation, grants,
publication and native validation selection are separate caller-owned effects.

### Unit B: sm-workflow native producer and provided view (implemented source; original scope)

The original Unit B scope, dependent on the exact Unit A binding, was the
following v1 GoalPhase adapter, safe service and significant native producers:

```text
src/main/cozy/sm-workflow.cml
src/main/scala/org/simplemodeling/textus/smworkflow/admission/DevelopmentOperationalView.scala
src/main/scala/org/simplemodeling/textus/smworkflow/admission/DevelopmentEventCorrelation.scala
src/main/scala/org/simplemodeling/textus/smworkflow/admission/DevelopmentObservationService.scala
src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeObservation.scala
src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseDevelopmentEvents.scala
src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeRun.scala
src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeWorkContinuation.scala
src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseOrderedHumanDecision.scala
src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowLauncherAssembly.scala
src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowRuntimeParameters.scala
src/main/car/abi-manifest.json
src/main/catalog/car/org/simplemodeling/textus/textus-sm-workflow.yaml
src/test/scala/org/simplemodeling/textus/smworkflow/admission/DevelopmentOperationalViewSpec.scala
src/test/scala/org/simplemodeling/textus/smworkflow/admission/DevelopmentEventCorrelationSpec.scala
src/test/scala/org/simplemodeling/textus/smworkflow/admission/DevelopmentObservationServiceSpec.scala
src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseDevelopmentEventsSpec.scala
src/test/scala/org/simplemodeling/textus/smworkflow/admission/DevelopmentObservationCliSpec.scala
src/test/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowLauncherAssemblySpec.scala
src/test/scala/org/simplemodeling/textus/smworkflow/ComponentPackagingDescriptorSpec.scala
docs/spec/development-operational-view.md
docs/design/development-operational-view.md
docs/notes/service-bus-development-events.md
```

CML remains the source of generated ABI/model code. Its existing managed
generation produces outputs; no generated Scala is copied or independently
hand-edited. The eventual effect/validation scope must include ordinary required
generation and descriptor verification. Unlisted retained-state or execution
modules are not implicitly admitted. Preserve all concurrent changes in listed
files, including existing recovery work. Absence of Unit A permits an honest
unsupported history response, but is not complete CC5-07 delivery.

### Unit C: existing Control Center S2 ownership

The following literal consumer change set is anticipated inside the already
issued S2 paths, after both producer contracts are real:

```text
src/main/scala/org/simplemodeling/textus/controlcenter/development/DevelopmentCarSummary.scala
src/main/scala/org/simplemodeling/textus/controlcenter/development/DevelopmentCarSourceCodec.scala
src/main/scala/org/simplemodeling/textus/controlcenter/development/DevelopmentCarSourceClient.scala
src/main/scala/org/simplemodeling/textus/controlcenter/impl/OperationalSummaryServiceFactoryImpl.scala
src/main/scala/org/simplemodeling/textus/controlcenter/summary/OperationalSummary.scala
src/main/web/textus-control-center/assets/development-car.js
src/main/web/textus-control-center/assets/development-car-panel.js
src/main/web/textus-control-center/index.html
native/macos/Sources/ControlCenterClient/OperationalSummary.swift
native/macos/Sources/ControlCenterClient/DevelopmentCar.swift
native/macos/Sources/ControlCenterMenuBar/DevelopmentCarView.swift
native/macos/Sources/ControlCenterMenuBar/MenuBarView.swift
src/test/scala/org/simplemodeling/textus/controlcenter/development/DevelopmentCarSummarySpec.scala
src/test/scala/org/simplemodeling/textus/controlcenter/OperationalSummaryActionSpec.scala
docs/spec/development-car-observations.md
docs/design/development-car-observations.md
```

Use the current Summary administrative authorization and normal typed Operation
invocation; presentations only render that Summary. Keep configured source and
subject selection in the existing ordinary host configuration mechanism. A
needed concrete config/grant change is returned to the caller for its existing
admission, not embedded into semantic editing. Preserve existing runtime leases,
health scope, disabled semantics and existing link policy. Details URL absence
does not prevent valid status/history display.

## Validation and acceptance handoff

| Boundary | Required actual evidence |
| --- | --- |
| Unit A generic projection | GWT and properties: at least two unrelated producer namespaces/payloads use the same generic path; opaque namespace/key and finite name selection before limit; generic metadata/bytes mismatch rejected; numeric descending ordering above JavaScript safe-integer range; limit 1/20/100 and invalid limits; legacy coverage explicit; no application imports, namespace/schema branches or payload field interpretation |
| Unit A managed provider | Isolated declared managed SQLite fixture: rollback leaves no event; committed event survives close/reopen; bound predicate query returns matching newest rows among unrelated subjects; normal introspection denial and closed lease reject access; no shadow store |
| Unit B state read | Genuine native Start/work/Human/terminal fixtures; absent/mismatched Session; optional provider/time; concurrent revision returns changing; repeated reads leave revision/continuations/counter/event rows unchanged and invoke zero Git/SBT/provider/Advance hooks |
| Unit B producers and correlation | Canonical application subject-to-key mapping is deterministic and tuple-sensitive; exact envelope/metadata correspondence, unknown schema, mismatched full subject and forced lookup collision are rejected without post-limit row filtering. Actual committed native Start, Human issue and accepted/rejected decision; rollback and unknown commit do not dispatch; duplicate retained completion reuses existing facts; delivery failure preserves committed history and business meaning; no event for mere read or semantic transport acceptance |
| Normal composition | Generated descriptor/ABI, ThreeWorkflow normal factory and installed CLI read through actual operations, normal auth/participant/EventStore refusal; separately scoped package/install evidence attributable to the exact build |
| Unit C declared check | Existing ordinal 2 `development-car` / `cc51-development-car` selects exactly `org.simplemodeling.textus.controlcenter.development.DevelopmentCarSummarySpec`; implement the real class with subject, independent availability, freshness/ahead-history, ordering/truncation, optional facts, safe-link cases |
| Connected consumer | Ordinary Summary action with real producer and committed-event fixture; shared API/Web/native display; original manual UI requirement and integrated Phase checks retain their scope |

The caller must select necessary dependency producer/provider/ABI checks through
normal native validation before claiming their acceptance. The one consumer
model Spec alone cannot verify Unit A/B or the connected presentation. Report
selection gaps as such; do not substitute outside-workflow SBT or ignored Specs.
No validation was executed during this planning unit. S2's assigned-scope remains
unsatisfied until connected implementation and its real evidence are accepted.

At the original planning boundary, the caller was to return this three-document
outcome through GoalPhaseWorkResult as decision-needed with a bounded Unit A
IMPLEMENTATION dependency proposal. That instruction records the original
handoff; it does not request renewed Unit A/B admission, resubmission of accepted
work or replay of successful validation. Current integration and publication
results must be reported from their actual evidence. The Control Center caller
retains its own scope, validation selection, independent review and closure;
producer source presence alone does not satisfy that consumer acceptance. No
second workflow, approval loop, repair counter or requalification of the already
accepted S1 runtime is introduced.
