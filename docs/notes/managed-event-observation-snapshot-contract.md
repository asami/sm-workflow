# Managed event observation snapshot

Date: 2026-09-30
Status: bounded native closure/classifier and isolation specs validated; publication pending

## Purpose and separation

The pure ManagedEventDuplicateClassifier interprets supplied rows. It cannot
prove completeness, absence, physical identity, lease authority or transaction
coherence. Its result must not be connected to publication through a generic
DataStore.load loop. The next implementation slice adds a separate internal
host-admitted observation capability, not an extension of the existing
postcommit multi-guard readback with optional records.

Workflow uses DataStoreSpace. Trusted Subsystem assembly captures the EventStore
declaration and Workflow grant and borrows the same managed physical resource
for both services. The provider performs native reads; Workflow never calls
SQLite directly. No AI action, skill, caller-supplied SQL or transaction callback
participates in lookup, closure, classification or validation.

## Admission and frozen request

The observation policy is distinct from the multi-guard write policy. Admitted
side-Create collections do not automatically grant read access. Trusted assembly
derives exactly three read locations from the installed declaration: the pinned
cursor entry, event collection and delivery-index collection. Workflow root and
Continuation locations are not general lookup targets. An owner name, decoded
declaration or user-constructed comparison policy is never runtime authority.

A host-generated bounded request captures:

- The admitted Workflow grant and captured declaration, including cursor entry.
- The policy revision and ordered selected batch, bound to this preparation.
- Distinct durable EventIds and canonical complete delivery-key bytes only.
- The issuing provider/view, physical resource, live lease and issuing thread.

Capture must freeze mutable delivery-key state. Use issued structural EventId
constituents and the admitted key codec; never rendered selectors or payload
equality. Retained incoming payloads need not be encodable: only a genuinely
new durable fact needs full envelope admission. Transient facts create no
EventId/key lookup targets. Existing ordinary selection's duplicate-ID rejection
remains unchanged in this slice; the classifier's broader fixtures do not
silently relax it.

The request and eventual snapshot have private host construction, not public
case-class copying or JSON reconstruction. Exported decoded facts are data.
Any later publication adapter must require the original opaque snapshot rather
than accepting those facts back as evidence.

## One native read transaction

Read the pinned cursor first to establish the native SQLite read snapshot.
All following probes and closure reads use that same connection and transaction.
Ending the read-only transaction invokes no write-commit hook. No schema DDL,
Create, initialization, migration or repair occurs during observation.

The bounded algorithm is fixed, not recursive graph traversal:

1. Read every distinct incoming durable EventId and incoming delivery-key target.
2. For present incoming-key indexes, strictly decode and validate the physical
   target against the requested full key, then load each referenced event.
3. For every present event from steps 1 and 2, read its intrinsic delivery-key
   index, if any. Validate that index against this exact event. An unexpected
   reference rejects; it does not trigger another traversal.
4. Validate the complete event/index closure and the cursor bounds, then invoke
   the pure classifier. Return the opaque observation and pure classification
   only after final host and lease validation and successful read cleanup.

All repeated targets are deduplicated by complete structural identity while
retaining a role mapping to the original batch. A cached read from this native
transaction may satisfy multiple roles; a previous preparation's read may not.
Physical event IDs must equal the canonical event entry identity derived from
the decoded issued EventId. Check exactly one reserved SQL id before removing
it from logical fields. Index identity, full key, referenced EventId, sequence
and full envelope digest must all agree. V1 recovery is explicitly key-free;
failed V2 decoding never falls back.

For at most 256 selected facts, the upper bound is 512 distinct event probes
(incoming IDs plus incoming-index references) and 768 distinct index probes
(incoming keys plus intrinsic keys of those events). These bounds fit the pure
classifier's separate 1024-row inventory bounds. Enforce the probe bounds as
well as codec byte/field bounds before accepting provider output; do not
truncate, page, auto-split or infer absence from an exhausted limit.

## Present, absent and required roles

Every requested target receives exactly one typed observation: Present with
canonical admitted content, or Absent for an exact row probe in an existing,
admitted collection. Missing observations, duplicate results, SQL/decoding
failures, unsupported providers and missing tables are errors, not Absent.

The cursor is required. A referenced event and an intrinsic index of a present
keyed event are required. Their absence rejects as corruption. Incoming ID/key
probes alone may legitimately be absent. A missing collection requires explicit
trusted provisioning in a separate boundary, never lazy initialization here.
This is stricter than ordinary SqlDataStore.load's missing-table behavior.

The snapshot carries the exact requested targets, presence/absence map, retained
canonical bytes, required closure and decoded cursor facts. Construction binds
it to the frozen batch/policy and host admission. An empty vector of retained
rows is not proof that all lookup targets were checked.

Check the live lease, issuing thread and exact provider before transaction entry,
before each read and after closure/cleanup before issuing the snapshot. Released
or revoked authority, provider substitution and use after leaving the host scope
reject. A captured snapshot grants no standalone resource ownership.

## Publication boundary: later, separate slice

Successful observation permits preparation, not commit or delivery. A later
native publication route must validate the bound observation inside the write
transaction before staging: exact cursor schema/revision/sequence and exact
observed presence/content/absence, including required closure. Cursor equality
alone is not a substitute for checking observed rows. Normal writers must all
participate in this cursor domain; unmanaged bypass writers are unsupported.

Three cases stay distinct:

- New durable facts: increment cursor only by new count, Create only new events
  and intrinsic indexes, atomically with Workflow records.
- All retained, with durable input: validate cursor and observations without
  incrementing revision or sequence; commit Workflow records through an explicit
  validation-only predicate route. Do not fake an update of the same values.
- All transient/empty: no cursor observation or cursor work; require the separate
  verified Workflow completion route before delivery.

Current additional guards require updates and cannot express the second case.
Do not weaken that contract or use a dummy cursor write. Complete publication
readback must also cover retained facts, not only newly created plan targets.
Any stale/missing/changed observation rejects with no implicit reclassification,
retry, repair, alias insertion or sequence reuse. Commit uncertainty remains
indeterminate and suppresses delivery. Verified storage is not application/
UnitOfWork completion or permission to redeliver retained facts.

## Executable acceptance for the observation slice

Before connecting classification to publication, exercise the real managed host:

- Present and exact absent incoming probes; referenced/intrinsic closure and
  mixed keyed/key-free V1/V2 retention; EventId and key disagreement rejection.
- Missing cursor/table, physical-ID mismatch, dangling reference, absent
  intrinsic index, wrong full key/digest, duplicated or incomplete provider
  observations; no normalization, repair or schema change.
- No transient probes, target deduplication, maximum bounded closure and source
  key mutation after capture; no arbitrary collection/entry/owner substitution.
- One native snapshot despite independent writer competition during closure;
  never a mixture of revisions. No generic load fallback.
- Revocation at entry, between probes and before snapshot issuance; wrong thread,
  another provider/resource and escaped host scope reject.
- No storage changes, write-commit hook or dispatch from successful or failed
  observation; failed read cleanup cannot return an authoritative snapshot.

Native publication competition, validation-only retained completion, full
readback and actual dispatch are separate executable gates. S12/P2-U15/P2-U16/
P2-U05 remain open. No public Operation/CLI activation follows from this note.

## Fixed-target implementation progress (focused-337)

DataStoreObservation implements a separate internal read policy, opaque host
invocation and lease-scoped fixed-target snapshot. Subsystem derives the read
policy from the captured declaration only for the explicit verified managed
view; ordinary and raw multi-guard spaces gain no read admission. DataStoreSpace
checks each target's provider identity. Native SQLite uses one read transaction,
checks the lease before each probe, rejects missing tables/cursor and validates
physical IDs before returning logical records. Cleanup uses rollback, not a
write commit. Facts can be read only on the issuing thread in the live host scope.

This primitive accepts a bounded exact target manifest; it does NOT yet build
the selected-batch lookup request, freeze its policy/batch provenance, perform
referenced-event/intrinsic-index closure, decode cursor/envelopes, or call the
classifier. Its optional rows must not be promoted to the complete event
observation contract above. No publication adapter exists. Independent-writer
competition and explicit mid-read revocation remain untested on this new route.

## Event closure implementation progress

The subsequent internal observeSelectedEventsC route captures selected lookup
identities, canonical durable keys and policy revision in a private request.
Transient-only/empty batches reject this cursor-bearing route. Initial targets
are distinct incoming durable EventIds and full-key-derived index targets plus
the pinned cursor. Native SQL reads these, referenced events and intrinsic
indexes on the same connection/read transaction using a closed three-stage
algorithm. Existing-table row absence is retained explicitly. There is no
caller-supplied callback or additional observation transaction.

After successful read cleanup, strict closure interpretation checks exact target
coverage, required reference/index presence, event physical identity, cursor
codec/bounds and index/envelope integrity. It passes complete retained facts to
the pure duplicate classifier. Snapshot classification is available only in the
issuing live host scope. Fixed-target snapshots have no event classification.
Classification output remains pure data, not publication or dispatch authority.

Capture freezes lookup keys via canonical reconstruction and copies EventRecord
values; it does not pre-encode incoming payloads that may be retained. New facts
are frozen by the classifier's canonical templates. This is not yet a full
publication handoff with deep-frozen arbitrary incoming payload state or proof
that a supplied policyRevision originated from the configured EventStore.
Those concerns must be resolved by trusted runtime integration, not by treating
decoded selection facts or a revision label as permission.

Native write-time observation validation, all-retained validation-only completion,
retained postcommit readback and application/dispatch integration remain open.

## Bounded isolation evidence (focused-341)

Focused-341 passed 223/223 tests in 26 suites after a test-only package-visibility
repair of compile-failed-340. No production source changes were needed.
The instrumented provider borrows the actual host-managed resource under the
same issuing lease and invokes the production native observation implementation.
A separate JDBC writer competing before reference expansion receives exact
SQLITE_BUSY (5); three event and two index probes retain one coherent cursor
2/2 snapshot. A later explicit write succeeds after cleanup, while snapshot
facts stay unchanged. No write-commit hook executes on the read route.

Actual lease revocation at entry, reference probe preparation, intrinsic-index
probe preparation and after native cleanup suppresses snapshot issuance. There
is no automatic retry. An in-flight read may finish after revocation; final
admission still rejects. New host inspection finds the retained cursor unchanged.
This evidence does not establish WAL/non-SQLite concurrency, native write-time
validation or authority to publish/replay the observed facts.
