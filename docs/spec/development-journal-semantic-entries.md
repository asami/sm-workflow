# Shared development Journal semantic entries

Use the ordinary installed `sm-workflow` JournalService for a concrete recording
or lookup purpose. Phase 11.1 adds Decision, Issue and Handoff entries while
preserving the existing immutable Entry operations. These are available
contracts, not a new Phase to start whenever a skill needs a Journal.

The runtime remains responsible for its automatic Action, Finding and Evidence
observations. Use a semantic entry when the authorized work supplies a reusable
decision, issue or handoff, or explicitly requests that record. Reuse the same
meaning-bearing work and actual source references; do not start a separate model
call, a Journal-only Task, a transcription loop or a Markdown dual write.
Operational data belongs to the configured managed store. `docs/journal` is
design documentation, never an operational store or message transport.

## Select the existing operation

Call `org.simplemodeling.textus.SmWorkflow.JournalService.<operation>` through the
normal parent-owned installed CLI with scoped escalation. Supply the declared
public DTO as `--input` JSON. No source launcher or database access is needed.

| Purpose | Operation | Input |
| --- | --- | --- |
| Find summaries in a repository scope | `findJournalEntries` | `JournalFindInput` |
| Read a selected immutable Entry | `readJournalEntry` | `JournalReadInput` |
| Record an actual Decision, Issue or Handoff | `recordJournalEntry` | `JournalSemanticRecordInput` |
| Correct one semantic entry with a reason | `correctJournalEntry` | `JournalSemanticChangeInput` |
| Change an Issue state without replacing its content | `updateJournalIssueState` | `JournalIssueStateInput` |
| Read current or selected past semantic content | `readJournalSemanticEntry` | `JournalSemanticReadInput` |
| Find by current Issue state or related identity | `findJournalSemanticEntries` | `JournalSemanticFindInput` |

Read only the selected detail when needed. A summary or a successful recording
does not establish consent, test success, review acceptance or Workflow closure.
Keep the current Session and its actual outcomes; Journal operations do not
replace Submit, Advance, a Human answer or an adoption operation.

## Recording and changes

`JournalSemanticRecordInput` contains `kind`, `repositoryIdentity`,
`phaseIdentity`, optional `stepIdentity`, `sessionIdentity`, `sourceReference`
and `content`. Use the actual existing association; do not invent a Session or
an Evidence identity to make a record writable. `kind` is `Decision`, `Issue`
or `Handoff`.

`content` contains `summary`, `rationale`, `links` and optional `state`.
An Issue requires `open`, `inProgress`, `resolved` or `closed`; Decision and
Handoff omit state. Each link has a `kind` and a complete `reference` with
`identity` and string `revision`. Supported link kinds are `Finding`,
`Decision`, `Issue`, `Fix`, `Validation` and `Handoff`. Use genuinely returned
references; the shape alone proves neither existence nor acceptance. Preserve
the runtime's declared validation and cardinality rules rather than adding
another skill-side limit.

`correctJournalEntry` takes `identity`, `repositoryIdentity`, string
`expectedRevision`, actual `sourceReference`, `reason` and the complete
replacement `content`. Omitting an old link removes it; it is not a patch.
For an Issue, include its current intended state. Correction preserves the
entry's original kind, scope and source, with prior revisions retained.

`updateJournalIssueState` takes the same identity, repository, expected revision,
source and reason fields, plus `state`. It preserves the content and links.
Use the actual returned revision for a subsequent change. A stale expected
revision is a conflict: read the selected current entry and decide whether the
change still applies instead of overwriting it. Never supply or repair the
provider's internal numeric CAS field; the public revision remains a string.

Only actual `recorded`, `alreadyRecorded` or `updated` results supply a successful
Entry reference. Preserve `invalid`, `absent`, `conflict`, `disabled`,
`unavailable` and `indeterminate` honestly. An uncertain write acknowledgement
does not authorize replay; use the public read for the actual known identity
when available and retain the uncertainty. Recording an Issue as resolved is
a meaning-bearing decision, not a consequence of a successful write.

## Lookup and retained references

`JournalFindInput` requires `repositoryIdentity`. Optional exact filters are
`phaseIdentity`, `sessionIdentity`, `kind`, complete `sourceReference`,
`recordedFrom`, `recordedBefore` and `limit`. The common find supports the
declared immutable kinds and Decision, Issue and Handoff. Time bounds use
whole-second UTC; the lower bound is inclusive and the upper bound exclusive.
Retain the normal public limit and use `hasMore` to refine the query, not to
invent a cursor or a completeness claim.

`JournalSemanticFindInput` contains `scope: JournalFindInput` and optional
`state` and `relatedIdentity`. State filtering requires Issue or no kind;
related identity matches a current direct link, not a transitive graph search.
An empty `found` result is a valid no-match observation.

`readJournalSemanticEntry` takes the actual `identity` and optional string
`revision`. Omit revision for current content or supply one for that retained
revision. Use this operation for semantic detail; use `readJournalEntry` for
immutable detail. Preserve the supplied complete typed content, source,
revision and change reason, while displaying only the relevant summary/view.

## Storage and delivery boundaries

Journal, Workflow and startup import use the existing managed storage owners.
With the delivered CNCF managed-startup fix, matched declared collections use
the same managed destination and lease. Do not require duplicated global
settings to compensate for the former early-configuration split. Respect
legitimate named-store overrides and the existing Workflow/EventStore shared
resource requirement. A storage setup failure is not permission to select an
empty replacement database, rewrite stored records or copy a live SQLite file.

Existing DB/payload relocation is a separately authorized operational migration.
Preserve old stores, actual Sessions, histories, repair counts and evidence
references. Recording a Handoff does not deliver a chat message, activate an
artifact, migrate data or establish receiver adoption.

The accepted Phase 11.1 source delivery includes actual Decision/Issue/Handoff
use, correction, state changes and separate-process reads. Historical Finding
to consumer chaining and automatic Evidence-save success are not established
by that delivery. Retain these limits rather than claiming universal acceptance.
