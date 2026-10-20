# Development Journal runtime, J1 and J2 Finding and Find

The retained Phase 11 J1 producer is the actual
`GoalPhaseService.submitGoalPhaseFindingRecording` Operation. Its canonical
managed completion commits the native `InstanceRecord` before the Journal
append runs. Business completion and Journal persistence are separate commits;
there is no atomic business/Journal completion guarantee. A Journal failure
leaves the actual accepted business result accepted and never replays completion.

At a fresh native Finding frontier, a configured Journal now adopts automatic
Finding recording. Eligibility comes from actual accepted native review and the
completed reviewed Step commit, or actual accepted whole-Phase review. A caller
cannot assert acceptance. Normal `advanceGoalPhase` records the complete eligible
pending tuple without an AI transcription task, clerical Required request,
Markdown write or dual write. With Journal absent, the explicitly configured
file-recording policy continues to own fresh clerical recording.

Adoption occurs only at a fresh frontier. Previously issued recording suspensions
retain their captured requests and destinations and complete through the unchanged
`submitGoalPhaseFindingRecording` Operation, including its J1 after-commit Journal
projection when Journal is subsequently configured. Journal failure never switches
to file recording, another store or another destination.

The invocation's ClosingProgram captures the actual admitted recording request.
The after-commit projection derives the entry from that request, the submitted
recording result and the actual committed instance. It preserves the selected
repository, Phase, optional Step, session, batch and revision references, every
original observation and its category/meaning, requested destination, actual
saved file/section, item outcome and rationale. The entry contains neither the
full Plan, a Workflow envelope nor logs. Its summary is a mechanical item-count
and result-outcome projection, not a new AI interpretation.

## Fixed host configuration

The optional Component initialization parameter `sm-workflow.journal` is resolved
from the owning host parameter snapshot. Absent configuration disables Journal
recording and reading while preserving existing callers. Its exact structure is:

```json
{
  "schemaVersion": "sm-workflow.journal.v1",
  "storeName": "workflow",
  "collection": "entity:smworkflow.cli.development_journal"
}
```

The named purpose must be canonical. The collection must be an exact canonical
EntityStore collection, distinct from workflow instances and continuations.
The host separately declares that named managed SQL purpose and the existing
`ComponentRecordStorage` Create grant for this Component and collection. The
parameter grants no write permission. Operations accept no provider, purpose,
collection, path, environment variable, System property or SQL selector.

Both append and read bind `ComponentRecordStorage.bindForCallC` using the current
owning `ActionCall.Core`. Its current managed lease and host declarations own
storage access. No private SQL, shadow store, anonymous implicit grant or guessed
provider actor is used. The ordinary three-workflow assembly binds JournalService
even when its optional policy is disabled.

## Immutable identity and storage

J1 retains `ExecutionObservation`, revision `1`, with its original internal Entry
JSON shape and canonical encoding unchanged. Its ordinary business key
is `execution-observation:` followed by URL-safe unpadded Base64 of UTF-8 compact
JSON array `[actual resultReference.identity, actual resultReference.revision]`.
This reversible tuple framing is neither a content hash nor an acceptance or
consent verifier. Input identities longer than 8192 characters, malformed data,
noncanonical encodings or incomplete tuples are invalid. Explicit revisions
other than `1` return `unsupportedRevision`. Corrections belong to Phase 11.1.

J2 adds a distinct immutable `FindingEntry` payload with kind `Finding`, revision
`1`, and storage prefix `sm-workflow.journal.finding.v1:` in the same declared
collection. Its key is `finding:` followed by URL-safe unpadded Base64 of UTF-8
compact JSON `[actual review source.identity, source.revision, observation.identity]`.
Canonical decoding verifies exact reversible framing and the same 8192-character
identity bound. The original review kind, observation identity, category, summary
and rationale are retained. The eligibility context contains repository, Phase,
session, optional Step, actual native source revision, selected Plan identity and
revision, stage, original base, actual completed Step commits and their accepted
review references, and the available Phase candidate/check/review context. Recording
time comes from the current runtime clock. No actor, provider, authentication or
Evidence claim is inferred from these business values.

One row contains the complete versioned `developmentJournalPayload` plus bounded
queryable kind, repository, Phase, session, source identity/revision, recording
time and immutable revision fields. No mapping, index or outbox is added.
Reads verify the selected key, payload, revision, source and scope fields.
Corrupt rows never become found Entries. Public messages retain typed boundary
status/detail diagnostics while withholding free-form provider messages that can
contain SQL, secrets or raw payloads.

## Append outcomes

Append first loads the exact stable key. Equivalent source and semantic content,
excluding runtime `recordedAt`, returns `alreadyRecorded` with the retained ID and
time. Different content is `conflict` and is never updated. An initial failed
load is `unavailable` and performs no Create.

An absent key permits one Create through the current-call managed storage API.
A successful `createC` returns `recorded` with the submitted Entry's
`JournalEntryReference`. A failed `createC` returns `indeterminate` with sanitized
typed diagnostics and no Entry reference, because a generic failure does not
establish whether the write landed. Append performs no post-create load, retry
or error-to-success reinterpretation. A subsequent ordinary invocation can
independently find an existing row through its initial source-key lookup.
Disabled recording returns `disabled` with no Entry claim.

Finding append uses the same honest initial lookup and single Create contract
through `ComponentRecordStorage.bindForCallC(currentCore, fixedPolicy, actualKey)`.
It does not use private SQL, backend APIs, post-create readback, retry, or an
error-to-success conversion. Known `unavailable`, `conflict`, and `indeterminate`
outcomes remain distinct and carry no Entry reference.

## Native automatic Finding intent and result

`GoalPhaseNativeJournalFinding` uses the existing managed mechanical transition
API bound to the normal Advance operation. Actual CML same-stage
`journal-findings-intent` and `journal-findings-result` transitions exist only at
Implementation, PhaseInspection and PhaseClosing. Before any Journal effect, one
versioned typed intent persists the source native revision, complete ordered
pending tuple, actual eligibility context, and frozen canonical policy storeName
and collection. A batch larger than 256 stays at Wait with every pending item;
no subset is attempted. The terminal payload records the known ordered per-item
outcomes after the Journal attempts.

Read-only traversal recognizes the exact native causation names before generic
state dispatch. Reconstruction validates actual contiguous mechanical receipts,
source revision, pending tuple, eligibility context, policy and next revisions.
A changed or removed Journal declaration pauses as a policy conflict; it never
reinterprets an earlier intent. An intent without a complete terminal receipt
remains unknown and paused. No append, consumed review or business operation is
automatically replayed, even when the first effect may have succeeded. Interrupted
mechanical acknowledgements cannot authorize another effect.

A known terminal result consumes exactly its attempted pending keys once and
continues the already accepted business workflow. Known non-success recording
outcomes do not invalidate accepted work. Native `journaled` batches remain
distinct from legacy `completed` file records. Duplicate-source admission checks
pending, legacy completed and journaled keys. Journal-only batches contribute no
Git management paths, Phase recording paths or staging permissions.

Normal Advance exposes `GoalPhaseFindingRecordingState.journaled` with bounded
`JournalFindingBatch` values containing sourceRevision, committedRevision, the
ordered original sources/observations, and every per-item `JournalRecordingState`
with a real Entry reference only for `recorded` or `alreadyRecorded`. It never
fabricates a legacy recording request/result or Workflow continuation envelope.

`GoalPhaseFindingRecordingSubmissionResult.journalRecording` is optional to
preserve the existing public constructor/projection boundary. Actual producer
completion returns its Journal status alongside the actual business acceptance.
Partial and failed clerical recording replies remain honest business results;
their original finite detail can still be Journaled after managed completion.

## Public read

The generated Provided ABI exposes the ordinary COMMAND
`JournalService.readJournalEntry(input: JournalReadInput): JournalReadResult`.
Input contains identity and optional revision only. There is no arbitrary Entry
record command. Status is `found`, `absent`, `unavailable`, `invalid`,
`unsupportedRevision` or `disabled`. Only `found` returns the actual retained Entry
and a derived Markdown view. The view is at most 4096 characters and ends with
an explicit truncation marker when bounded; persistence retains complete detail.
Read never recursively records a Journal and never scans workflow rows or files.

Read dispatches by the canonical identity prefix to the corresponding typed
decoder. Public `JournalEntry.recording` and `JournalEntry.finding` are optional
variants: producers and stored decoders enforce exactly one content variant by
kind. Finding content includes the original review observation and actual
eligibility context; ExecutionObservation retains its original detail. Both use
the same `JournalReadResult` and explicit 4096-character derived view bound while
the stored payload retains complete detail.

## Public finite find

The ordinary generated Provided COMMAND
`JournalService.findJournalEntries(input: JournalFindInput): JournalFindResult`
requires `repositoryIdentity`. Optional exact filters are `phaseIdentity`,
`sessionIdentity`, `kind` (only `ExecutionObservation` or `Finding`) and a complete
`sourceReference` containing identity and revision. Text must be nonblank, exact
with no surrounding whitespace or control characters. Missing required scope,
invalid text, unsupported kind, invalid limit or time bounds return typed
`invalid` before resolving storage. Limit defaults to 20 and accepts only 1–20.
There is no query expression, column, projection, sort, count, offset, cursor,
provider, backend, collection or store selector in this input.

Optional `recordedFrom` and `recordedBefore` accept only canonical whole-second
UTC `yyyy-MM-ddTHH:mm:ssZ` values; fractional seconds, offsets and noncanonical
representations are invalid. When both are present, from must be earlier than
before. From is inclusive and before exclusive. Stored canonical Instants retain
their original zero or fractional second precision. The pushed-down string
comparisons remove only the bound's final `Z`: `Gte(recordedAt, fromWithoutZ)` and
`Lt(recordedAt, beforeWithoutZ)`. Thus the exact zero-second time and every
fractional time within the lower second are included together, while the exact
upper second and all its fractions are excluded. Stored values are never
truncated and fractional filter bounds are never rounded or accepted.

The owning `FindCall` is a `ProcedureActionCall`. Inside its execution, the
protected `component_datastore(policy.storeName)` resolves the current owning
Component's managed store only after valid input and enabled policy. Find does
not acquire a synthetic record ID or Create grant. The existing host owns
collection read access; the initialization policy fixes purpose and collection.
The public `SearchableDataStore.search` receives one `QueryDirective` containing
an `And` expression with required repository equality and all selected optional
scope, kind, source and time clauses. SQL applies these clauses before the finite
limit. There is no filtering of an already limited result.

The fixed projection contains physical `id`, `developmentJournalPayload`, `kind`,
`repositoryIdentity`, `phaseIdentity`, `sessionIdentity`, `sourceIdentity`,
`sourceRevision`, `recordedAt` and `revision`. Order is actual physical `id`
ascending, offset zero, and query limit is caller limit plus one. This is stable
identity order, with no time-ranking claim. Every returned row, including the
extra row used for `hasMore`, must have a canonical physical key obtained through
the provider's `recordKey("id")` normalization. That key must match the identity
in the corresponding versioned J1 or Finding payload. The original kind-specific
decoder validates payload and every indexed field. Unknown keys, malformed or
mismatched payloads and indexes make the whole result `unavailable`; no row is
silently omitted. There is no count query, file/backend fallback or AI call.

Only `found` returns entries or `hasMore`; an empty successful query is `found`
with empty entries and `hasMore=false`. Disabled policy returns `disabled`
without storage access. Unsupported search capability, provider failure or
invalid stored rows return `unavailable` with sanitized diagnostics and no
entries or `hasMore`. A successful result returns at most the selected limit of
`JournalEntrySummary` values: actual Entry reference, kind, repository, Phase,
optional Step, session, source reference, original recording time, summary and
`summaryTruncated`. Summary is at most 280 characters, with the explicit
` [Journal summary truncated]` marker included inside that bound when needed.
Stored content remains complete. `hasMore=true` asks the caller to refine filters;
it offers no total count, cursor, ranking or completeness guarantee. Selected
`readJournalEntry` returns complete typed content and its bounded view. Neither
query nor read records a Journal or issues Work.

## Executable specifications

`DevelopmentJournalSpec` authors canonical identity, corruption, semantic
equality, immutable source-key lookup, direct Create success and failure outcomes,
conflict, unavailable/indeterminate, host
configuration and public input/status examples. Every scenario has explicit
Given/When/Then adjacent to setup, action and assertions.

`DevelopmentJournalFindingSpec` uses an explicitly synthetic Storage seam for
Finding key framing, canonical typed payloads, original content and eligibility
context, semantic reuse, differing/corrupt rows, failed initial lookup and failed
Create without readback. Its long rationale example checks bounded display and
complete stored detail. Pure mechanical payload examples cover full typed
intent/result preservation, the 256-item bound, mismatched Entry references and
automatic batches granting no file paths.

`GoalPhaseNativeFindingRecordingSpec` uses the actual normal managed Operation
fixture. Review and recording inputs are explicitly specification-owned. The
Journal has no row before business completion; successful Create returns a
reference and a separate public read returns the actual original observation/result. Withheld
separate Create scope returns unavailable while the actual business completion
remains accepted and recorded exactly once.

Its J1 episodes issue real file-only work with Journal absent, close the producer
runtime, reopen the same managed SQL runtime with Journal configured and
`initialize=false`, then fulfill the captured contract. Separate J2 episodes
accept actual specification-owned native Phase reviews and call normal Advance
without clerical Submit; they publicly read actual Findings and retain every
per-item state once. The bounded two-Step fixture proves review acceptance alone
does not append and the actual reviewed commit supplies Step eligibility before
automatic recording and original Step-two continuation. Withheld Journal Create
scope reports unavailable separately from accepted business state without legacy
Work. A durable-intent acknowledgement interruption reopens at paused unknown
without another append or review; removal of its frozen policy pauses as conflict.

`DevelopmentJournalFindSpec` authors explicitly synthetic searchable DataStore
unit seams for missing/blank scope, kind/limit/time admission, disabled no-access,
the exact fixed query directive, whole-second lower/upper behavior, stable ID
ordering, finite `hasMore`, summary truncation, original J1 decoding, normalized
physical keys, corrupt physical/index/payload rows including the extra row,
empty success and unsupported/failed/interrupted providers. These seams are not
managed SQL or actual Workflow/reviewer acceptance evidence.

`DevelopmentJournalCliSpec` obtains an actual entry from that connected producer,
closes its runtime, and starts a separate ordinary current-artifact CNCF CLI
process against the same retained normal host configuration and declared SQL
purpose. Public finite find pushes repository, Phase, session, kind and complete
source filters into the ordinary managed SQL query with limit one. It returns the
identical Entry reference, summary, source and recording time. A separate fresh
process publicly reads that selected reference and returns the identical full
detail; another process repeats the public read. There is no direct SQL seeding,
custom product main, reflected state or hand-built Workflow envelope.

The parent owns compilation, the selected focused accumulator runtime validation,
independent review and transitions. Spec authoring alone is not a PASS claim.
