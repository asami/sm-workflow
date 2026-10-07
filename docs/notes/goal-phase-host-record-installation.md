# GoalPhase host record installation boundary

Status: Phase 2 design candidate; no Operation is admitted or exposed by this
note. Existing configured Factory readers are implemented, but their test-only
installers are not production installation evidence.

## Responsibility

The Subsystem owns one physical pool and its managed DataStoreSpace lease.
The existing post-resolution ComponentFactory hook composes readers and services
from the resolved host snapshot. It neither installs initial data nor records
human consent. Ordinary reopen does not rewrite selections, associations,
authorizations, Decisions or Workflow history.

Workflow selects the next operation and validates the result. Skills return
only the requested semantic result; they cannot select a record collection,
declare themselves human, grant authority or route a storage write. CLI,
server and MCP must dispatch the same admitted Operation implementation.

## Separate proposed entry points

Names below are design candidates, not additions to the generated CML ABI.

| Entry | Caller supplies | Host derives and checks | Allowed effect |
| --- | --- | --- | --- |
| recordGoalPhaseSelection | Explicit selected workflow/input, participant and idempotency identity | Independently established human interaction attribution, admitted workflow revision, exact Phase/repository and participant scope | One immutable selection; no Start, authorization or Git effect |
| registerGoalPhaseSubmission | Start invocation reference and immutable Step submission identity | Admitted submitting participant, exact stored Start provenance and current Phase/repository scope | One immutable submission-to-Start association; no review judgment or closing grant |
| submitGoalPhaseResult | Issued work identity and its exact typed semantic result | Current retained work/Continuation, authenticated participant binding, input type, revision and result scope | Existing completion path and its retained typed completion; no initial submission association |
| recordGoalPhaseDecision | Exact pending Decision identity/revision and selected answer | Independently established human interaction attribution and exact current intent | One immutable Decision answer; never an implicit closing grant |
| authorizeGoalPhaseClosure | Explicit bounded closing intent and authority limits | Independently established human authorization, current snapshot/intent, permitted command catalog and validity interval | One immutable bounded authorization; no command execution by the recording operation |

Authentication establishes a subject, not an explicit human selection or
approval. `OperationAuthorizationRule.requireProviderAuthentication`, roles,
scopes and capabilities can restrict entry but cannot supply the missing human
interaction evidence. An `explicitHuman` field in submitted JSON, a principal
attribute, an AI response or a scheduler continuation is not such evidence.
Human-facing entries remain disabled until the host's attributable interaction
contract and its verification path are fixed and executable.

The [human interaction boundary](goal-phase-human-interaction-contract.md)
records the provider-neutral verifier prerequisite, separation of authenticated
actor from human choice, answerable-question admission, immutable answer/receipt
and independent command authorization. It is a design candidate, not a new
enabled Operation. Existing automatic stop-only questions are not human answers.

An ordinary semantic result must not be blocked merely because it lacks human
approval: it uses the issued-work participant contract, not the human entries.
It also must not obtain human authority as a side effect of completion.

## Actual lifecycle and existing seams

The association must precede the first review, not be installed by its result:

`record selection → Start → register submission → requestStepClose → WorkOrder → submitStepReview`

`GoalPhaseStepCloseServiceOperation.BoundCall.execute` resolves the submission
association first, loads the instance and checks active `step.Awaiting`; only
then does it prepare and persist an issued review. There is no issued review
to authorize initial association installation. The association's Start WAIT
must be derived from retained Start provenance, not copied from a requester.
`GoalPhaseStartExecution.loadRetainedStartC` now supplies an internal read-only
prerequisite: it re-admits selection, validates the loaded history and derives
the original activation projection, with observed current revision separately
retained. It performs no writes and grants neither actor nor collection
authority. `GoalPhaseManagedStartAssembly.loadRetainedForCallC` now uses the
current owning Component and Subsystem dispatch lease with the canonical
Workflow grant. Actual SQLite reopen and later progression are verified through
a private dispatch probe; escaped cores, malformed declarations and foreign
root collections fail. Production registration/writer integration remains pending.
Registration therefore needs a separate admitted entry or an atomic host-owned
Step-submission entry, before `requestStepClose`. Do not move the association
write to `submitStepReview` or fabricate an initial WorkOrder.

`WorkflowCompletionServiceOperation` checks the retained WorkOrder, operation,
handle and selected instance. `WorkflowProtocolV1.admitResultC` correlates run,
Continuation, revision, snapshot, type, facts and evidence; optional worker/model
strings are payload checks, not authentication. No authenticated submitting
participant binding is established by those checks alone.

`WorkflowManagedCompletion` uses the captured Subsystem Workflow storage grant
and its verified managed space. The closing program's prepared local effects
are enlisted by `WorkflowCompletionDataStorePersistence` in the same native
transaction. This proves an integration seam for admitted effects, not a grant
to arbitrary host-record collections. `ManagedCompletionParticipant` owns a
transaction lifecycle and must not be confused with an authenticated actor.

The current `Subsystem.admitWorkflowStorageC` grant fixes the Workflow instance
and Continuation pair. The application's host-record read policy does not
extend that grant to association or receipt writes. Extending an admitted
writer requires an explicit collection-purpose contract and common grant
validation, rather than passing a raw DataStore or adding a postcommit write.

The existing verified completion path does not currently admit arbitrary local
registration Creates. EventStorePersistenceDeclaration.policyC admits only its
event/index collections for side Creates; DataStoreLocalCompletionPlan rejects
local writes to reserved EventStore collections. Declaring a local closing
intent therefore cannot authorize an association write. Do not use an event
collection, a dummy cursor or fabricated Continuation to bypass that boundary.

## Registration storage unit

For standalone pre-review registration, retain the association and its receipt
as **one immutable record**, keyed by the allocated submission identity. The
logical receipt is a typed part of that record, not a second mutable status row.
This lets the existing Create-only physical operation retain the complete
registration in one transaction; no new multi-record registration primitive is
required solely to separate association from receipt.

The closed record contract must retain:

- schema and allocated registration/submission identities;
- explicit owning Component, workflow definition/revision and Start instance;
- admitted selection, Phase/repository and submitting participant identities;
- exact original Start projection derived by the managed historical reader;
- exact typed registration input and its idempotency identity;
- typed accepted-registration receipt, with no approval or command receipt.

The receipt states that the host accepted registration, not that Step review,
human approval or Git commit succeeded. The observed current Workflow revision
used for admission is not an assertion that the historical Start is current.
A retry compares the retained typed request, not a current clock or a newly
generated receipt. A caller must never submit the receipt or storage envelope.

The existing association v1 codec contains only submission identity and Start
WAIT. It is insufficient for this complete registration contract. No silent
envelope extension, permissive v1 decoder or success receipt inferred from those
old bytes is allowed; the closed new record/reader contract must be fixed and
verified before a production writer is enabled. Existing read-only fixtures
remain evidence for that original format only.

The private `GoalPhaseSubmissionRegistration` candidate now prepares and encodes
this single record from a typed request and retained original Start witness.
Its receipt fields are derived internally; callers cannot supply a receipt.
Closed decoding rejects altered correlation, additional approval fields and
legacy association bytes. Preparation and decoding have no write effect and do
not establish authenticated consent or committed registration. PackageBin and
the focused eighteen-suite accumulator passed (140 tests); production Operation,
Create-only grant, actor admission and committed readback remain pending. See
`docs/journal/2026/10/2026-10-01-registration-record-contract.md`.

### Managed Create-only admission and outcome boundary

The production writer must not be a copy of `ManagedHostRecordRead` with
`store.create` substituted for `store.load`. The read policy is an application
restriction, not a framework write capability. `DataStoreSpace.dataStore`
selects a provider by `isAccept` and can fall back to another registered/default
provider; `SqlDataStore.isAccept` accepts all collections. Neither proves the
declared owner, exact collection or same managed physical resource.

Before provider access, trusted Subsystem construction must capture the exact
Component/store/registration-collection Create-only declaration. The active
Operation call must obtain an opaque framework-issued capability from that
captured declaration and current managed lease. No caller-supplied owner label,
configuration-shaped argument or decoded registration may issue it. Its narrow
surface is load/create of the allocated registration identity; no save, update,
delete, provider escape or alternative collection. Borrow the existing shared
resource, restore nested bindings, and reject use after the dispatch lease.
This is a storage admission seam, not another initialization API. Factory
post-resolution assembly remains pure and does not install records.

Physical Create and publication are distinct. Current SQL `create` performs
table preparation, existence checking and insertion through `_with_connection`;
that helper does not itself introduce an explicit transaction or normalize a
racing insertion failure into `DataStoreDuplicate`. Single-record atomicity
must be verified on the admitted physical provider rather than inferred from
the method name. Schema preparation must never be interpreted as registration.

The writer outcome protocol is:

1. Independently admit the authenticated participant/current scope, then derive
   the record using retained original Start and the exact typed request.
2. Read the allocated submission identity through the narrow managed capability.
   An equal decoded record is an idempotent retry; a different or malformed
   record is a conflict. A failed observation is not absence.
3. Only a verified absent record permits Create. Never use upsert or allocate a
   different identity on a race or uncertain outcome.
4. On successful Create, require committed readback and exact typed comparison
   before returning the stored receipt. Missing, failed or different readback
   cannot produce success; retain an explicit indeterminate outcome.
5. A specifically established duplicate/race may be resolved only by
   authoritative readback of the same identity under the same admission. Equal
   facts return the retained receipt, different facts conflict, and an unreadable
   outcome remains indeterminate. Do not turn every exception into a duplicate,
   claim rollback from an error, or compensate by deleting/replacing the record.

Required executable evidence includes absent-grant/foreign-owner/store/collection
rejection before provider mutation; escaped lease rejection; same shared pool;
equal retry/no new write; conflicting and simultaneous registrations; reopen
readback; and failure after physical insertion with no premature success. The
existing 140-test accumulator does not yet establish those production properties.

## Storage and idempotency

1. Decode a closed typed request before acquiring resources. Reject unknown
   fields, missing identities, foreign scope and caller-selected destinations.
2. Resolve the owning Component, active managed lease, declared logical store
   and purpose-specific collection from the current call and host snapshot.
   The existing application read declaration is not a common write grant.
3. Require host-issued owner/collection **Create-only** write authority.
   A physical provider's `isAccept` routing result is not that authority.
4. Derive record contents from the admitted interaction or retained Workflow
   facts; do not persist an arbitrary caller-supplied storage payload.
5. For standalone registration, use one Create-only record containing both
   association and receipt. Verify that the selected provider commits the whole
   record atomically. If an entry requires multiple physical records, or is part
   of a Workflow transition, enlist all effects in the same admitted guarded
   physical transaction. Do not use independent `create` calls or compensation
   to claim atomicity. Do not use `save`/upsert for immutable records.
6. The same idempotency identity and equal typed request returns the retained
   receipt with no new effect. Reuse with different scope/input is a conflict.
   Record comparison is semantic typed comparison, not a management hash.
7. Return a success receipt only after authoritative committed readback.
   Rollback returns no success. An uncertain physical outcome is explicitly
   indeterminate: do not claim absence, create another identity or execute the
   next command. Recovery reads the original identity through the same host.

The existing SQL `create` rejects a duplicate ID; this alone does not prove
multi-record atomic installation or authorize an application writer.
Current codecs/readers can reject malformed stored data, but they do not
authenticate who installed it. No startup migration or test fixture can bridge
these gaps.

## Required executable evidence before activation

- Normal transport dispatch denies anonymous/foreign participants and all
  unimplemented human entry routes before any record or Workflow change.
- Pre-review registration records only its host-derived association and
  registration receipt. AI completion retains its typed result through the
  existing completion transaction; human-origin/authorization fields are rejected.
- Exact retry, conflicting retry and two independent writers preserve one
  immutable record and receipt without overwrites.
- Failed standalone Create publishes neither the association nor its embedded
  receipt. Transition-side records roll back with Workflow/Continuation guards.
- Reopen returns the same committed receipt using no installer or fixture Map.
  Missing, malformed, wrong-purpose and wrong-owner records fail closed.
- Committed readback failure is indeterminate and prevents advancement/Git.
- Human selection, Decision and closure authorization are separately proven
  against the actual host interaction source; authentication alone fails.
- All routes borrow the Subsystem pool; Component shutdown cannot close it.

## Next implementation boundary

First freeze the submitting-actor binding and the host-owned submission
registration transaction, including its pre-review position and exact retained
Start provenance. Add the association writer only when this route supplies
common Create-only grants and atomic receipts. Independently bind completion
callers to their retained issued work before activating AI result entry; use
the existing managed completion transaction rather than a second result store.
Human entry implementation follows its independent host interaction contract;
do not invent a trusted-port boolean or expose disabled placeholders as usable
operations. Update CML Operations and generated ABI together only after their
typed request/result, authorization, failure and recovery contracts are admitted.

Default launcher activation, controlled external commands, sm-* skill packaging,
independent reviews and complete Phase validation/release remain required.
