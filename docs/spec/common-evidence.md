# Common Evidence and automatic Journal connection

date=2026-10-20
status=minimum-producer-implemented-local-installed-route-qualified
owner=sm-workflow
producer_phase=[Phase 12](../phase/phase-12.md)
consumer_phase=[Phase 6.4](../phase/phase-6.4.md)

## Responsibility

Journal records actual observations, findings and decisions with their context.
Evidence retains the material supporting those records: logs, result documents,
review reports and media. Both are common runtime services. Neither an Evidence
ID nor a Journal entry proves test success, review acceptance or access authority.

| Owner | Canonical responsibility |
| --- | --- |
| Common Journal (Phase 11) | Structured entry, source/result relationships, recording outcome, read/find |
| Common Evidence (Phase 12) | Evidence identity, payload association, actual archival and retrieval, storage observations and retention basis |
| CNCF File/Blob/DataStore | Admitted physical I/O and configured persistence, reused through public UoW APIs |
| Phase 6.4 | Adoption in real development, failure/batch/compression/retention operation and consumer acceptance |

Phase 12 first delivers one real local text-log producer through Advance and
common Journal. Phase 6.4 extends this same service for its later operational
requirements; it does not create a parallel Evidence store or Journal writer.
Phase 11's existing producers and accepted results remain unchanged. Its current
Finding/recording variants do not already provide an Evidence producer: Phase 12
adds the necessary typed storage-observation projection in the common owner.

## Common model

- Evidence ID: stable ordinary business identity, independent of physical name.
- Content: kind, media type/encoding, representation and an actual payload
  reference. A reserved destination is not a stored payload.
- Source: actual operation/result/run and output identity, with known project,
  Phase/Step/session relationships. Unknown values remain absent. No full Workflow
  envelope, invented Phase or AI-generated execution identity is needed.
- Storage: retained logical File target/relative filename or actual existing
  Artifact/Blob reference, allocation and storage observations, host context.
- Lifecycle: occurrence/allocation/storage times and retention basis; current
  availability is observed on lookup rather than inferred from an old Journal.

Store these business facts through the configured common Component datastore,
distinct from Workflow/host-authority records. Do not create private SQL access,
a filesystem index, digest identity or a second generic storage implementation.
Use an existing Artifact/Blob identity only when that owner actually supplied it.

Public references are Evidence ID and Journal Entry identity/revision with the
existing host/component context. Callers need neither a physical absolute path
nor a new CNCF absolute-display-location API. Runtime retains exact admitted
logical destinations internally, stable across retry observation/month rollover.
Logical paths must never be presented as observed physical absolute paths.

The physical archive policy remains the configured host's
`~/backup/journal/YYYY/MM/<project>/<phase-or-task>/<run-id>/`. Physical placement
and naming still matter, but are storage responsibilities. The first text route
may use an explicitly host-prepared dated parent and admitted File bindings.
Document this setup honestly; broader directory/binary/compression capabilities
require their real producer delivery, not shell/NIO/private-binding substitutes.

Stored payload content is not silently overwritten. A different execution/output
or replacement content receives a new identity with a relationship where needed.
Compression can change representation while preserving the same logical material;
it must not silently substitute different evidence content.

## Automatic primary route

```text
Advance executes an Action or accepts its actual delegated result
  -> use that Action's declared evidence selection and retained output identity
  -> confirm selected material is complete and available for archival
  -> allocate/reuse the same Evidence reference and save the actual payload
  -> record the actual storage observation and Evidence reference in common Journal
  -> return short outcomes/references and continue ordinary progression
  -> a fresh process reads Journal, then reads Evidence by ID
```

Each supported producer declares which actual outputs to retain, their release
point and retention policy. Start with one existing completed test Action and
one UTF-8 log, including a known failed test's log when available. Existing
test-result semantics and validation policies remain unchanged. Do not archive
all temporary files, every read/status poll or Journal/Evidence calls recursively.
Later lint, delegated review and diagnosis producers reuse this contract rather
than imposing a new Evidence interface on each skill.

The runtime uses existing actual result/output identity to associate one
Evidence record with that material. Repeated Advance, later read and recording
recovery reuse it; they do not allocate another ID or rerun the Action. Preserve
the source-to-Evidence association before an effect can need later observation.
An uncertain save is resolved through supported observation of that association,
not blind replay, filename guessing or content-hash proof. This is a bounded
recording lifecycle, not a new generic replay or distributed exactly-once engine.

Active logs/inputs remain at their current bindings until their writers and
consumers release them. The first implementation may copy a completed immutable
log and retain its source. No automatic destructive move or cleanup is required.
Live/incomplete inputs produce an explicit pending/unavailable outcome.

AI selects meaning and supplies real external material when necessary. Normal
runtime-owned outputs require no AI notification, renaming, journal transcription
or extra AI call. Explicit allocate/archive/read/find operations remain available
for externally supplied or additionally selected material through the same owner.
Exact generated public type/Operation names are settled by Phase 12 implementation;
these responsibilities do not declare an already installed API.

## Outcomes and recovery

Keep Action outcome, Evidence allocation/storage, Journal recording, current
availability and acceptance separate. Only actual storage succeeds as stored;
only recorded/alreadyRecorded Journal outcomes return an actual Entry reference.
Use the common Journal's disabled/unavailable/conflict/indeterminate distinctions.

If the Action succeeded but storage failed, retain the Action result and source.
If storage succeeded but Journal failed, retain the Evidence and retry only
recording from the stored observation when eligible. Journal's source key must
refer to the actual observation, not mutable current availability. Failed or
uncertain writes do not become success because the original Action succeeded.
An uncertain source/result is never promoted to known completion for archival.
Common storage success is not permission to overwrite the saved workflow state.

Ordinary progression reports recording problems without repeating completed
business effects. The explicitly selected evidence-acceptance scenario remains
open until actual archival and retrieval succeed; it cannot pass with recording
disabled. Avoid a global new gate on unrelated work just to enforce recording.

## Retrieval and retention

Public bounded read/lookup returns metadata and selected content through normal
admitted storage. Journal read alone does not establish payload availability.
Evidence can also be read directly by ID without a Journal dependency. Phase 12
provides enough source lookup for its recording recovery and public ID retrieval;
broader find filters/manage operations grow with Phase 6.4's real requirements.

Phase 12 stores the completion-based retention basis without requiring full
retention management to close. Phase 6.4 implements 30 days from actual producing
workflow/Phase completion, active no-expiry, explicit hold/review, due queries,
batch partial outcomes and optional compression. No automatic deletion or scheduler.
Expiry/missing payload does not erase prior Journal or revoke past acceptance;
it also cannot establish a new validation claim.

## Adoption and exclusions

The implemented minimum selects the first completed Test/TestOnly stdout in a
native validation batch. Compile and live/incomplete material are excluded.
Optional ComponentParam `sm-workflow.evidence` enables it. Public operations are
`EvidenceService.readEvidence(EvidenceReadInput)` and
`EvidenceService.recordEvidenceJournal(EvidenceJournalInput)`; both inputs have
an Evidence `identity` and optional `revision`. The latter consumes a retained
storage observation and does not replay Test or archival. Allocation and writing
remain native producer responsibilities; generic upload/find/manage are not
delivered. Typed SBT success remains authoritative: raw exit zero with an
Unverified TestOnly result is failed. The CLI renders
`validation.evidence_recording` with separate storage/Journal outcomes.

The writer consumes a known File write acknowledgement, without a write/readback
success gate. Fresh-process reads are normal retrieval and integration checks.
The [actual delivery](../journal/2026/10/2026-10-20-phase-12-producer-delivery.md)
records the supported host setup and still-open consumer/closure boundary.

Phase 12 implementation/delivery is owned by sm-workflow開発; Phase 6.4 integration
and operational acceptance by sm-workflow-dev開発. Handoff includes the actual
public contract, supported producer, host setup, artifact/deployment identity,
validation results, limits and a working automatic-save/read example.
Share accepted code/evidence, not Workflow database rows or live Session copies.
Initial Phase 12 completion does not wait for every Action, media kind, batch,
compression, Phase 11.1 or Phases 7–10. Phase 6.4 retains its own open acceptance.

No automatic migration/deletion of tracked files, duplicate Markdown Journal,
new transport, storage hash-control framework or speculative backup system is
introduced. Raw execution material remains outside Git; plans and handoffs here
are development documents, not copies of execution evidence.
