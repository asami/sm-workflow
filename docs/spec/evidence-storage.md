# Local Evidence operation on the common Evidence service

Status: E1 contract accepted and locally committed on 2026-10-20;
E2 implementation and connected verification remain open.
Source: [Phase 6.4](../phase/phase-6.4.md), P64-01..03.
The E2–E4 contract below adopts [Common Evidence](common-evidence.md) and
[Phase 12](../phase/phase-12.md)'s producer delivery (2026-10-20). Phase 12 owns
common identity/storage/read and automatic Action-to-Journal recording; Phase 6.4
owns trial adoption and operational extensions on that same service. Neither
this update nor the existing E1 acceptance declares installed Evidence support
or E2 success. See the [two-chat handoff](../journal/2026/10/2026-10-20-phase-12-common-evidence-handoff.md).
Native Plan/Usage Contract adoption remains with the existing execution owner;
no saved request, decision, history, counter or accepted result is rewritten here.

The E1 documentation candidate passed its selected `contract-compile`, genuine
independent review and normal native Step closure. Commit
`07a97986cbee9ffe71fdf69b89a60eff98768134` records that contract-design acceptance;
it establishes no E2 storage implementation or Phase-wide acceptance.

## Material and ownership

| Material | Treatment |
| --- | --- |
| Disposable work | Private or ignored temporary paths; no obligation to archive it. |
| Selected execution evidence | Completed logs, raw responses, review reports and media stay outside Git. Archive only released, task-owned material worth retaining. |
| Canonical development artifacts | Source, intentional executable fixtures, requirements, accepted input models, human decisions and Phase status retain their normal Git/lifetime policy. |
| Journal references | Runtime records actual storage observations and Evidence references through common Journal; do not embed payloads or require AI Markdown edits. |

An archive result records storage. Test outcome, review acceptance, applicability
to another candidate and successful common Journal recording remain separate facts.
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
host. Phase 12 owns the canonical public contracts; the names below describe
responsibilities rather than a second E2 API. The primary route consumes actual
completed Action outputs inside Advance, using producer-declared selection and
existing result/output identity. Explicit registration supports additional
AI/client-selected external material. No recording-only AI request is necessary.
Neither the listed names nor this plan asserts an installed Operation.

| Responsibility | Business input | Actual returned result |
| --- | --- | --- |
| `EvidenceStorageService.allocateEvidence(EvidenceAllocationInput)` | Selected source reference, evidence kind, optional preferred filename and genuinely supplied relationship to project/Phase/task/run. Resolve existing session context where present. | `EvidenceAllocationResult`: outcome, optional actual retained `EvidenceRecord`, and explanation. Only `allocated` supplies a successfully reserved identity/destination. |
| `EvidenceStorageService.archiveEvidence(EvidenceArchiveInput)` | Existing allocated Evidence ID and actual release of the owned source from live use. | `EvidenceArchiveResult`: the same record, actual storage outcome, availability, separate Journal outcome and real Entry reference only when recorded. |
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
host; exact admitted logical archive workspace/relative filename;
representation; allocation/storage observation; and completion/retention basis.
Keep current availability in each response as a fresh observation. Include an
actual existing Artifact/Blob reference only if that storage owner produced it;
a File copy does not manufacture a Blob reference. Journal-ready fields are a
view of actual storage observations, not a second stored payload. Public callers
use Evidence ID and actual Journal Entry identity/revision. They need no absolute
display location or additional CNCF location-resolution API. Journal observations
remain historical; current availability/retention belongs to Evidence lookup.

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

Allocation persists the chosen identity and internal logical destination before
returning the Evidence reference. A failed or uncertain allocation returns its actual outcome; no invented
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

The result carries the configured host context and Evidence reference, not a
required absolute filename. Host notation grants no remote access. Retain the
internal filename over restart, month rollover, lost response and later common
Journal recording. Unsupported names and occupied
destinations have explicit outcomes; never silently relocate or replace them.

E2 first proves one uncompressed UTF-8 text log using one configured local driver.
Do not call a mocked destination, allocation-only example or in-memory reference
successful archival. E4 adds optional compression, settling the internal `.gz`
name before storage and proving decompression through the same ID. Evidence identity does not change
because representation changes; already compressed media need no recompression.

For E2, the host explicitly registers source and archive workspaces/grants and
prepares the selected dated project/Phase-or-task/run parent under the required
archive root. sm-workflow validates names, resolves the admitted destination and
retains its exact logical filename; it does not accept an arbitrary caller-selected
destination. Use the actual admitted File binding for physical access without
reading private binding internals or guessing HOME/a display-only root. Missing binding
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
| Journal recorded / alreadyRecorded / disabled / unavailable / conflict / indeterminate | Actual common Journal outcome; only recorded/alreadyRecorded supply real Entry references, independently of storage success. |

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

## Automatic recording, public references and retention

Advance uses actual declared output/result identity to allocate or reuse an
Evidence ID, save the completed log through the common service, and append the
storage observation and reference through the existing common Journal owner.
Phase 12 adds this typed Evidence producer; the existing Finding-recording source
must not be forged to use it. AI receives short actual outcomes and references,
not a Markdown transcription task or a requirement to obtain physical paths.

Return Evidence ID, host context, representation, storage outcome, current
availability, known producer relationships and retention basis/date, plus separate
Journal status and its Entry reference only when actually recorded. Allocation
is not storage. Active work has no expiry. Repeated Advance reuses the same
actual source/output association; it does not allocate duplicate Evidence.
Unknown storage remains unknown until supported observation. Storage success
with Journal failure permits later Journal-only recording from the saved
observation without another test, archive or destructive move. Keep the original
source/result history and introduce no shadow Journal, digest control or host
authority claim. Journal read and Evidence read remain separate public operations.

The default is 30 days after actual producing workflow/Phase completion. Active
work has no expiry. E4 adds explicit holds with reason/review date and due-material
queries; it adds no automatic deletion or scheduler. Expiry or missing material
does not revoke prior acceptance and cannot supply evidence for a new claim.

## E2 evidence and producer delivery boundary

### Consumer connection under the adopted remaining Plan

The Phase 6.4 trial host consumes the normally published Phase 12 CAR through
the installed CLI. It configures `sm-workflow.evidence.v1` with a separate
canonical `common_evidence` collection in its existing `workflow` datastore,
host identity `mini-development-sm-workflow-dev`, logical workspace
`evidence-archive` and relative directory `logs`. Keep Workflow continuations,
host authority records and the existing `journal` datastore unchanged. The
host owns the dated archive root and prepares its `logs` parent; the application
uses only the admitted managed File operations.

The selected `evidence-main-route` check remains ordinal 2, kind TestOnly. Its
fixed host program runs the existing `RepositorySyncFileEffectSpec`, exercising
actual typed File UoW capture/application, Unicode and assigned-path behavior.
That suite proves its own File composition scope; it does not test the installed
Evidence service or supply E2 acceptance by itself. The owning normal Advance's
completed Test log is the real producer input for the consumer connection.
Reuse Phase 12's separately established Evidence producer guarantees rather
than introducing another writer or rerunning its full qualification here.

Consume the actual returned `validation.evidence_recording` for the selected
source. Keep Action outcome, Evidence allocation/storage and Journal outcome
separate. From a genuinely returned Journal Entry identity/revision, invoke
`JournalService.readJournalEntry` in a fresh installed process; obtain its
Evidence reference and invoke `EvidenceService.readEvidence` in another fresh
process. Require the actual payload and its source/output association to match
the completed selected Test. Do not derive an Evidence ID from filenames,
substitute direct filesystem reads, or infer storage from test success. Keep
raw responses, Test logs and local host configuration outside the selected Git
delta. A missing/failed/uncertain result remains open under the normal recovery
contract without replaying the Test or archive.

E2 must adopt Phase 12's real automatic route on the trial host: one actual test
Action's released log, saved Evidence reference, actual archive/readback, common
Journal observation, and a fresh process reading the Entry then the same Evidence
payload. Explicit external-material registration remains a supported supplementary
route. The selected Git delta must contain intended source and canonical
records only. Keep the execution payload and raw observations outside Git.

Phase 12 binds common CML types and durable records to existing managed storage;
E2 adopts those actual contracts and checks the consumer connection. Inspect the
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

## E2 discovered public location prerequisite

The remainder of this section records the earlier accepted-design assumption
and native proposal, preserved as history. The 2026-10-20 common Evidence decision
removes absolute-display-path acquisition from current E2 acceptance. Do not
request or implement this API merely to satisfy that superseded design. Its
saved native question/result is unchanged by this document; the owning runtime
must process the actual same-Session adoption. Directory/compression capabilities
remain separately necessary where the selected operation actually requires them.

The issued E2 implementation inspection found an additional prerequisite in
the selected text route. CNCF's public `FileReadTextResult` contains only
`target`, `path` and optional `text`; `FileWriteTextResult` contains only
`target`, `path` and `outcome`. Neither exposes the resolved absolute location.
`WorkspaceBinding`, `WorkspaceExecutionAdmission` and the execution context's
binding access are `private[cncf]`. Their root revalidation and relative-path
resolution therefore cannot be consumed as a public application API.

This matters before allocation can return its required exact destination.
Combining a separately configured display root with the logical target is no
proof that it is the actual admitted binding. The application must not bypass
that boundary with private access, direct NIO, a shell command, an additional
filesystem interpreter or a guessed HOME. The accepted requirement remains.

The bounded dependency proposal `P64-E2-public-file-location@1` requests a
producer-owned public way to obtain the exact resolved display location for
the same admitted File target/path, including a missing leaf in a prepared
parent. The producer selects its API. Preserve current Component authority,
root revalidation, path/link checks and bounded File acquisition. The selected
linked files are CNCF's `workspace/WorkspaceFileExecution.scala` and
`workspace/WorkspaceFileExecutionSpec.scala` under their existing main/test
Scala package roots. No producer file has been changed or implicitly approved.
Normal Human scope admission, separate owner delivery and actual consumer
verification remain required. This proposal does not deliver the later
directory/compression requirements or change the selected Plan/checks.


## E3 bounded consumer failure coverage — 2026-10-21

The selected `evidence-failure-route` check is ordinal 3, TestOnly,
`CommonEvidenceSpec`. It extends the existing failure specifications through
an isolated admitted typed File/UoW host without creating a Workflow Session.
Physical files and File authority are real; allocation and observation metadata
in these injected cases remain explicitly specification-owned memory seams.

| P64 | Selected observation | Limit |
| --- | --- | --- |
| 09 | Actual read-only authority denies an archive write; original released text survives and Action outcome stays separate from indeterminate storage | Does not simulate a kernel crash or establish every permission failure |
| 10 | Actual File creation followed by injected observation-publication failure; later File/UoW reads the original allocation/payload, keeps observation unknown and attempts no second write | Models loss of published storage observation, not a network packet drop; metadata persistence is a specification seam |
| 11 | Actual occupied CreateNew destination is retained unchanged; incomplete/unreleased Test and completed Compile do not supply automatic producer material; invalid source is refused before allocation | No live production Evidence is deleted, relocated or overwritten to manufacture failure |
| 12 | Existing Journal seam fails append, then records/already-records only the retained observation; the installed public Journal-only command is also exercised using E2's actual retained Evidence | Failure injection is bounded specification coverage; the public retained Entry path proves actual continuation, not a forced production Journal outage |

Producer qualification is reused only at its existing scope. These cases do
not create another Evidence writer, storage interpreter, public upload API or
runtime repair. Public invalid/unsupported reads preserve their actual outcomes.
Test success, historical storage status, current payload availability, Journal
outcome and Step acceptance remain separate. P64-13..18 are still E4 work.
