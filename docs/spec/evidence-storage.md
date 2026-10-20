# Local evidence storage: minimum contract

Status: proposed E1 semantic delivery for Phase 6.4, 2026-10-20;
native validation and independent acceptance remain pending.
Source: [Phase 6.4](../phase/phase-6.4.md), P64-01..03.
This document defines the minimum E2 behavior; it declares no installed API,
implemented archive driver, successful validation or independent acceptance.

## Material and ownership

| Material | Treatment |
| --- | --- |
| Disposable work | Private or ignored temporary paths; no obligation to archive it. |
| Selected execution evidence | Completed logs, raw responses, review reports and media stay outside Git. Archive only released, task-owned material worth retaining. |
| Canonical development artifacts | Source, intentional executable fixtures, requirements, accepted input models, human decisions and Phase status retain their normal Git/lifetime policy. |
| Journal references | Record actual evidence identity, host, location, state and retention basis; do not embed evidence payloads. |

An archive result records storage. Test outcome, review acceptance, applicability
to another candidate and successful journal editing remain separate facts.
Existing tracked evidence is not automatically migrated, unstaged or deleted.

## Inspected existing interfaces

| Existing owner | Reuse and actual limit |
| --- | --- |
| `GoalPhaseArtifactReadPolicy` | Declares a canonical store and separate reference collection and prevents overlap with host-record purposes. It authorizes a bounded read boundary; it grants no archive write. |
| [Artifact reference contract](goal-phase-artifact-reference-applicability.md) | Reuse actual Artifact/Blob identities and storage addresses when a stored artifact is applicable. Keep bounded current-call payload reads and existing Create/readback uncertainty. Add no digest, copied-body or size-equality certificate. |
| Common Component datastore | Retain business allocation and storage observations through the existing configured persistence and current-call access. Use a distinct declared evidence purpose; never put payloads in Workflow instance or host-authority rows. |
| `WorkflowFileExecution` and CNCF `FileTarget` | Current-call `file_read_text` and `file_write_text` support bounded UTF-8 acquisition, CreateNew and UpdateExisting with actual conflict outcomes. Workspaces and grants belong to the host. |
| CNCF local `BlobStore` | Existing binary storage, metadata and fresh-store reading are relevant to later media. `put` currently chooses its own key from ID/filename and writes to that path; its API alone establishes neither an arbitrary exact reserved path nor no-overwrite installation. Do not assert it already implements the archive contract. |

The inspected File implementation stages a file inside its destination's parent.
It does not expose directory creation, binary copy, compression or source deletion
through the two public text intents. Select those existing intents for E2's
uncompressed text route, with an explicitly host-prepared local run directory.
That prerequisite is part of host setup and must be documented and exercised;
allocation and archive never create a directory by a different execution route.
This selection does not deliver E4 compression or binary/media archival.
Do not substitute application NIO, generic Process commands or a new Blob store.
If an additional producer contract is necessary, identify it and follow the
Phase's existing upstream wait boundary before dependency implementation.

## One-file public business boundary

Use ordinary generated typed Operations and structured results on the configured
host. The names below are proposed public CML contracts for E2, not installed
Operations. No second Workflow engine or caller-owned storage protocol is
introduced.

| Responsibility | Business input | Actual returned result |
| --- | --- | --- |
| `EvidenceStorageService.allocateEvidence(EvidenceAllocationInput)` | Selected source reference, evidence kind, optional preferred filename and genuinely supplied relationship to project/Phase/task/run. Resolve existing session context where present. | `EvidenceAllocationResult`: outcome, optional actual retained `EvidenceRecord`, and explanation. Only `allocated` supplies a successfully reserved identity/destination. |
| `EvidenceStorageService.archiveEvidence(EvidenceArchiveInput)` | Existing allocated Evidence ID and explicit release of this task-owned source from live use. | `EvidenceArchiveResult`: the same record, actual storage outcome, availability and explanation. |
| `EvidenceStorageService.lookupEvidence(EvidenceLookupInput)` | Existing Evidence ID and Boolean `readContent`. | `EvidenceLookupResult`: found/absent/unavailable outcome, optional actual record, current availability, optional bounded text content and explanation. |

`EvidenceAllocationInput` carries a logical source workspace and relative file,
kind, optional preferred filename, actual producer references and optional
execution time. `EvidenceArchiveInput` carries only `evidenceId` and
`sourceReleased`; it cannot replace the saved source, producer or destination.
`EvidenceLookupInput` carries only `evidenceId` and `readContent`. E2 accepts
one UTF-8 text file. Later supported kinds/representations use the same business
identity rather than overloading a text string with encoded binary payloads.

`EvidenceRecord` retains the existing Entity identity as Evidence ID; source
workspace/relative path; producer project and actual Phase or task plus run;
evidence kind; optional execution time; allocation time and local date; configured
host; exact archive workspace/relative filename and absolute display location;
representation; allocation/storage observation; and completion/retention basis.
Keep current availability in each response as a fresh observation. Include an
actual existing Artifact/Blob reference only if that storage owner produced it;
a File copy does not manufacture a Blob reference. Journal-ready fields are a
view of this record and the actual read observation, not a second stored payload.

Persist the business record using the existing configured Component datastore
with a separately declared evidence-record collection. It must be distinct from
Workflow instances/continuations, Goal selections/plans, host evidence and
artifact-reference collections. Create the allocation before returning it;
update the same identity with actual storage observations. A fresh service call
reads that configured collection and admitted archive workspace. An in-memory
map, task-file scan or private Workflow-store access is not a lookup implementation.

The caller supplies no destination root, SQL collection, executable command,
grant, consent certificate or second full Workflow context envelope. A business
reference grants no path access. Ordinary framework/session/source admission
remains required. A separate evidence Operation must not infer an unrelated
Goal's context from prose or private Workflow storage.

Allocation persists the chosen identity and exact destination before returning
them. A failed or uncertain allocation returns its actual outcome; no invented
successful identity is recorded. Archive observes the original allocation.
Lookup uses that same retained identity across process restart rather than
searching for a likely filename or allocating a replacement.

## Destination and representation

Resolve the configured host's archive root beneath
`~/backup/journal/YYYY/MM/<project>/<phase-or-task>/<run-id>/`.
Use the user's local allocation date for year/month, and retain execution date
separately. A task with no Phase uses its real task relationship, never an
invented Phase. Project/task/run names are logical business data, validated and
mapped within the host's admitted roots rather than interpreted as arbitrary paths.

The result names the exact host and absolute final filename. Host notation does
not grant remote access. Retain the chosen filename over restart, month rollover,
lost archive response and later journal editing. Unsupported names and occupied
destinations have explicit outcomes; never silently relocate or replace them.

E2 first proves one uncompressed UTF-8 text log using one configured local driver.
Do not call a mocked destination, allocation-only example or in-memory reference
successful archival. E4 adds optional compression, settling the final `.gz` name
before returning it and proving decompression. Evidence identity does not change
because representation changes; already compressed media need no recompression.

For E2, the host explicitly registers source and archive workspaces/grants and
prepares the selected dated project/Phase-or-task/run parent under the required
archive root. sm-workflow validates names, resolves the admitted destination and
retains its exact filename; it does not accept an arbitrary caller-selected
destination. Derive the displayed absolute location from the actual admitted
binding, never a guessed HOME or an unrelated display-only root. Missing binding
or parent yields an explicit unavailable result. An allocation across a month
boundary continues using its retained parent. General directory provisioning
requires the separately delivered producer contract below; the prerequisite is
not evidence that arbitrary future runs already work.

## State and actual effects

Keep allocation/storage state distinct from current availability and from
journal recording. E2 needs the successful branch below; E3 freezes detailed
failure variants before implementing them.

| Fact | Meaning |
| --- | --- |
| Allocated | Identity and destination are retained; no successful payload installation is claimed. |
| Stored | Actual payload installation and supported readback succeeded at the reserved destination. |
| Storage failed | Actual confirmed failure is reported and the original source remains available. |
| Storage indeterminate | An attempted effect has an uncertain outcome; retain the source and observe the same allocation without blind replay. |
| Available / unavailable | Current supported read observation, separate from previously recorded storage success. |
| Journal recorded / failed / not attempted | Actual AI document-edit outcome; storage success does not set it automatically. |

Archive only released material after all writers, runners and resumable consumers
have finished with it. Read through admitted source/storage facilities, install
without overwriting an occupied destination, then establish actual readability.
Keep the source until successful readback. E2 may retain the source after a
successful copy; neither allocation nor readback itself authorizes cleanup.
Never delete the original on failure or uncertainty.

Retain actual effect observations through existing configured durable facilities.
A saved location is no proof of file presence. A file's presence after an
uncertain effect is no license to fabricate successful storage or acceptance;
lookup must report what the actual supported observation establishes.

## AI record response and retention

Return these journal fields from the actual result: Evidence ID, host, exact
location, representation, storage state, current availability, producing
project/Phase/task/run relationship and retention basis/date. An allocated
destination may be recorded as pending and never as stored. Missing expiry
is explicit when the producing workflow remains active.

The AI records the returned fields in the selected canonical project journal
and reports whether that actual edit succeeded. It may record a later stored
result without rerunning a test or archiving the same payload again. No new
recording service, host-evidence issuer or test-success attestation is implied.

The default is 30 days after actual producing workflow/Phase completion. Active
work has no expiry. E4 adds explicit holds with reason/review date and due-material
queries; it adds no automatic deletion or scheduler. Expiry or missing material
does not revoke prior acceptance and cannot supply evidence for a new claim.

## E2 evidence and producer delivery boundary

E2 must show one actual completed log selected by the AI/client, the returned
reserved destination, actual archive/readback, actual AI journal edit, and a
fresh process resolving and reading the same ID through the ordinary configured
services. The selected Git delta must contain intended source and canonical
records only. Keep the execution payload and raw observations outside Git.

E2 binds the proposed CML types and separately declared durable records to the
existing datastore and the selected managed text File driver. Inspect the
actual installed versions at their usage boundary; this source inventory is
not an installed-capability or producer-delivery certificate. Retain P64-09..18
for their E3/E4 owners, including partial batches, conflicts, failure observation,
retention, independent Phase review and final serialized full validation.

The inspected producer lacks two later prerequisites: managed preparation of
ordinary nested archive directories beneath an admitted root, and bounded
binary/compressed archive installation/readback at an already selected exact
filename without overwrite. Before adopting those branches, request their
separately owned CNCF contract/delivery with current-UoW admission, real conflicts,
finite acquisition/representation limits and truthful failure/uncertainty.
The producer chooses its concrete API. sm-workflow retains naming, identity,
durable observations and retention meaning. Do not edit CNCF/Cozy, create an
application filesystem interpreter, use shell compression or silently weaken
P64-14 to proceed. The complete Phase stays open until its actual requirements
and upstream delivery are satisfied.
