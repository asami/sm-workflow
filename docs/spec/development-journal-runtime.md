# Development Journal runtime, J1

This is the adopted Phase 11 J1 contract. The first producer is the actual
`GoalPhaseService.submitGoalPhaseFindingRecording` Operation. Its canonical
managed completion commits the native `InstanceRecord` before the Journal
append runs. Business completion and Journal persistence are separate commits;
there is no atomic business/Journal completion guarantee. A Journal failure
leaves the actual accepted business result accepted and never replays completion.

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

J1 supports only `ExecutionObservation`, revision `1`. The ordinary business key
is `execution-observation:` followed by URL-safe unpadded Base64 of UTF-8 compact
JSON array `[actual resultReference.identity, actual resultReference.revision]`.
This reversible tuple framing is neither a content hash nor an acceptance or
consent verifier. Input identities longer than 8192 characters, malformed data,
noncanonical encodings or incomplete tuples are invalid. Explicit revisions
other than `1` return `unsupportedRevision`. Corrections belong to Phase 11.1.

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

## Executable specifications

`DevelopmentJournalSpec` authors canonical identity, corruption, semantic
equality, immutable source-key lookup, direct Create success and failure outcomes,
conflict, unavailable/indeterminate, host
configuration and public input/status examples. Every scenario has explicit
Given/When/Then adjacent to setup, action and assertions.

`GoalPhaseNativeFindingRecordingSpec` uses the actual normal managed Operation
fixture. Review and recording inputs are explicitly specification-owned. The
Journal has no row before business completion; successful Create returns a
reference and a separate public read returns the actual original observation/result. Withheld
separate Create scope returns unavailable while the actual business completion
remains accepted and recorded exactly once.

`DevelopmentJournalCliSpec` obtains an actual entry from that connected producer,
closes its runtime, and starts a separate ordinary current-artifact CNCF CLI
process against the same retained normal host configuration and declared SQL
purpose. Public JournalService returns the identical ID, detail and recording
time. A second process repeats the public read. There is no direct SQL seeding,
custom product main, reflected state or hand-built Workflow envelope.

The parent owns compilation, the selected three-suite runtime validation,
independent review and transitions. Spec authoring alone is not a PASS claim.
