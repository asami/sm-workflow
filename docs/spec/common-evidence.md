# Common Evidence and automatic Journal connection

date=2026-10-21
status=E4-producer-extension-implemented-validation-pending
owner=sm-workflow
producer_phase=[Phase 12](../phase/phase-12.md)
consumer_phase=[Phase 6.4](../phase/phase-6.4.md)

Planned producer extension (2026-10-24):
[recovery observations](recovery-observations-and-cause-resolution.md) reuse
actual operation/correction/result Evidence in deterministic Journal/Finding
projections. Reuse retained payload references rather than copying logs; preserve
actual partial/unavailable storage observations separately from successful
business recovery. This introduces no AI recording task or additional Phase 12
acceptance gate. See the [ROBS delivery plan](../notes/recovery-observations-delivery-plan.md).

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

New managed archives select the exact relative destination
`<directory>/YYYY/MM/<repository>/<phase-or-task>/<session>/<opaque>.log[.gz]`.
The owning core clock's zone selects year and month. Repository, phase/session
and opaque names must be literal safe segments; traversal and missing or invented
Phase values are rejected. CNCF prepares the admitted parent and installs exact
CreateNew bytes. The application performs only UTF-8 encoding/decoding; CNCF owns
gzip, input/stored/expanded bounds, workspace authority and physical path safety.
Retained allocations keep their original path across month and policy changes.

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
point and retention policy. Native GoalPhase validation selects every completed
Test/TestOnly stdout, including a known failed test's log when available. Existing
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
The public generated types and Operations are listed below. Source associations
are business relationships, never grants of File access or proof of test success.

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

The E4 extension implements 30 days from the actual observed producing native
Phase completion, active no-expiry, explicit hold/review, indexed due queries,
batch partial outcomes and optional compression. No automatic deletion or scheduler.
Expiry/missing payload does not erase prior Journal or revoke past acceptance;
it also cannot establish a new validation claim.

## Adoption and exclusions

The native producer selects all completed Test/TestOnly stdout in a validation
batch; its old `selected` helper remains the first-output compatibility view.
Compile and live/incomplete material are excluded.
Optional ComponentParam `sm-workflow.evidence` enables it. Public operations are
`EvidenceService.readEvidence(EvidenceReadInput)` and
`EvidenceService.recordEvidenceJournal(EvidenceJournalInput)`; both inputs have
an Evidence `identity` and optional `revision`. The latter consumes a retained
storage observation and does not replay Test or archival. The same service adds
the bounded archive/detail/retention/due Operations below. Typed SBT success
remains authoritative: raw exit zero with an
Unverified TestOnly result is failed. The CLI renders
`validation.evidence_recording` with separate storage/Journal outcomes.

The writer consumes a known File write acknowledgement, without a write/readback
success gate. Fresh-process reads are normal retrieval and integration checks.
The [initial delivery](../journal/2026/10/2026-10-20-phase-12-producer-delivery.md)
records the earlier minimum's qualification. E4's changed producer, upstream
File capability and consumer connection require their own actual validation and
normal local publication; this specification does not claim those results.

## Public E4 operations and DTOs

All operations belong to the existing `EvidenceService`, are normal synchronous
CommandActions, and execute through their owning Procedure UoW and fixed host
policy. None accepts a datastore, workspace, path, clock or expiry selector.

| Operation | Input | Result |
| --- | --- | --- |
| `archiveEvidenceBatch` | `EvidenceBatchArchiveInput` | `EvidenceBatchArchiveResult` |
| `describeEvidence` | `EvidenceDescribeInput` | `EvidenceDetailResult` |
| `manageEvidenceRetention` | `EvidenceRetentionInput` | `EvidenceDetailResult` |
| `findDueEvidence` | `EvidenceDueInput` | `EvidenceDueResult` |

`EvidenceArchiveItem` contains `sourceReference: WorkflowArtifactReference`,
`repositoryIdentity`, `phaseIdentity`, optional `stepIdentity`, `sessionIdentity`,
`outputIdentity`, `actionOutcome`, released `text`, and `compression` (`identity`
or `gzip`). `EvidenceBatchArchiveInput.items` is nonempty and limited to 20.
Each `EvidenceBatchItemResult` preserves `sourceReference`, `outputIdentity` and
the unchanged `EvidenceRecordingState`. The batch result has `status`, `message`
and ordered `items`. `stored` means every individual storage result is stored;
`partial` means only some are, and `failed` means none are. Journal status remains
independent within each item. There is no rollback. Every actual storage
observation is passed to the existing `DevelopmentJournalEvidence.recordC`.

Representation does not participate in Evidence identity. Its fixed suffix row
is saved with the final allocation before write intent and physical installation.
A retained allocation with a different representation conflicts. Known observations
are reused; intent without an observation remains indeterminate without retry.
Directory failure, CreateNew conflict and uncertain writes retain the association
and original input. Reads resolve the public ID, use the stored representation,
and decode canonical UTF-8 after CNCF's bounded expansion. No readback gate follows
a Create acknowledgement.

`EvidenceLifecycleState` has `status`, optional `completedAt`, `expiresAt`,
`completionReference: WorkflowArtifactReference`, `holdReason` and `holdReviewAt`.
`EvidenceDetailResult` has `status`, `message`, optional `read: EvidenceReadResult`,
`lifecycle` and `representation`. Its lifetime status is `active`, `completed`,
`expired`, `held` or `unmanaged`; invalid, disabled, absent and unavailable results
remain explicit. The nested read independently reports current found, missing,
indeterminate or unavailable payload. An expired payload can still be found.
Original metadata identity and path remain visible even if its payload is missing
or cannot be decoded.

`EvidenceRetentionInput` contains `identity`, optional `revision`, `action`,
optional `reason` and optional `reviewAt`. `track` accepts neither reason nor review
date and initializes only that existing allocation's projection, consuming an
already retained completion marker when available. Absence of a marker never
creates a completion time or expiry. Managed active means no completion has been
observed for that association; it does not certify that a process is running.
`hold` requires an exact nonblank reason and canonical future Instant `reviewAt`.
`release` requires a reason and no review date. Review passing never releases a
hold. Previous hold/release changes remain in the operational projection; immutable
Journal storage observations and payloads are not rewritten. A save acknowledgement
returns its resulting operational state directly, without a lifecycle readback gate.

`EvidenceDueInput` contains required `repositoryIdentity`, optional `phaseIdentity`
and `sessionIdentity`, `kind` (`expiry` or `holdReview`), optional `limit` (1–20,
default 20), and optional exclusive Evidence `afterIdentity`. `EvidenceDueResult`
has `status`, `message`, `items: EvidenceDetailResult*`, `hasMore` and optional
`nextIdentity`. Production pushes kind/scope/held and numeric deadline predicates
to managed search, orders by physical identity, and requests limit plus one.
Epoch values preserve Instant nanoseconds. Expiry excludes held records;
holdReview finds held records at or after their review instant. Selected payloads
are read normally by Evidence identity. Unsupported search returns unavailable,
never a successful empty answer or a full-collection client scan.

## Completion hook, compatibility and host setup

After `GoalPhaseNativeRepositoryClosure.Store.terminalC` receives the actual
`Lifecycle.Completed` InstanceRecord from `closeC`, it records one immutable
completion marker for that exact repository, phase and session, using the current
owning clock instant and actual instance identity/revision. Repeated calls retain
the original marker time/reference. Public inputs cannot set completion or expiry.
Only matching tracked lifecycle rows are projected in finite 20-row keyset pages,
up to 4096 per workflow. Their expiry is exactly completedAt plus 30 days, retaining
holds. Search/save failure or the bound leaves projection unavailable/indeterminate;
it does not reopen completed work. The normal terminal message separately reports
an unavailable completion observation. A retained marker with an incomplete active
projection is reported unavailable; explicit tracking can consume that same marker.
No observation invents an earlier completion timestamp.

The schema stays `sm-workflow.evidence.v1`. Allocation, Source, Observation,
EvidenceMetadata, EvidenceReadResult, EvidenceReadInput, EvidenceJournalInput and
EvidenceRecordingState retain their previous shapes, as does strict v1 Journal
metadata validation. Existing `archiveC` callers keep the original writer and path
semantics. New representation, completion and operational lifecycle records use
separate fixed suffix keys in the same configured common Evidence collection.
Legacy missing representation means old identity/UTF-8 read. Legacy missing
lifecycle means explicitly unmanaged, with no inferred completion or expiry.
There is no migration, historical-row rewrite, file relocation or automatic delete.
`readEvidence` continues to report physical availability independently of lifetime.

The existing optional `sm-workflow.evidence` configuration is unchanged:

```json
{
  "schemaVersion": "sm-workflow.evidence.v1",
  "storeName": "workflow",
  "collection": "entity:smworkflow.host.common_evidence",
  "hostIdentity": "development-host",
  "workspaceIdentity": "evidence-archive",
  "directory": "logs"
}
```

The normal host must already admit that component datastore/collection, managed
create/load/save/search, and the configured File workspace's manage/read access.
The collection remains separate from Workflow, host-authority and Journal rows.
Lifecycle creation/saving/search uses actual owning `component_datastore`; it adds
no database, index file, provider, configuration keys or application filesystem
interpreter. A provider without indexed search is explicitly unsupported. CNCF's
typed directory and represented-byte File capability must be normally available
before runtime use. Physical workspace and ResourceReadLimits remain CNCF-owned.

`CommonEvidenceSpec` and `CommonEvidenceLifecycleSpec` describe business behavior
using explicit specification-owned seams. `CommonEvidenceCliSpec` retains its
source-launcher tests and adds the opt-in leaf
`qualify installed Evidence batch gzip detail hold due and release through ordinary fresh processes`.
Select `-Dsmworkflow.spec.installedCommand=/absolute/executable` after normal
publication to qualify genuine installed Operations against an isolated normal
SQLite host. It retains actual argv, process IDs and stdout/stderr paths. Without
that property only this leaf cancels. Neither these source changes nor isolated
qualification establishes live consumer Phase completion or E4 acceptance.

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
