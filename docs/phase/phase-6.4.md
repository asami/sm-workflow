# Phase 6.4: Common Evidence Adoption and Development Operation

status=in_progress
planned_at=2026-10-13
depends_on=phase-6.3.md; accepted-phase-11; phase-12-minimum-delivery
checklist=[Phase 6.4 Checklist](phase-6.4-checklist.md)

Owner: sm-workflow. Predecessor: [6.3](phase-6.3.md). Successor: [7](phase-7.md).

## Current execution — 2026-10-21

The existing Session `96c294d73be3d48067f3721d8a2abf33` has adopted
`phase-6.4-evidence-storage@2` through the supported remaining-Plan operation.
The old unanswered dependency question is retained as superseded-by-adoption.
Normal Advance has issued `ImplementStep / P64-E2` at evidence revision 28;
E1, original base and repair history remain. The producer's Phase 12 is closed
and normally published. The consumer host now declares the separate Evidence
collection/archive workspace and E2's selected TestOnly program. Validation,
fresh-process ID retrieval, independent review and Step acceptance remain open.
Historical statements below about Plan revision 1 describe the original Start.

E1's contract documentation is now accepted through actual selected Compile,
genuine independent review and normal local native commit
`07a97986cbee9ffe71fdf69b89a60eff98768134`. The same Session issued E2 at source
revision 24 without replaying the accepted implementation, review or Compile.
Under the earlier design, E2's inspection discovered that the selected public File result does not expose
the actual resolved absolute location. The exact upstream scope proposal is
recorded in the [contract](../spec/evidence-storage.md#e2-discovered-public-location-prerequisite).
The new common-Evidence allocation below supersedes that design prerequisite,
without submitting an answer to its retained native question.
E2 through E4 and all connected Phase acceptance remain open; no storage success
is claimed from this design prerequisite.

The initial E1 delivery record follows; its pending statements describe that
earlier handoff rather than the current accepted E1 frontier.

The installed command successfully started this Phase in the development host
and issued `ImplementStep / P64-E1` through its first explicit Advance.
The selected Plan is `phase-6.4-evidence-storage`, revision `1`, with the
existing E1 -> E2 -> E3 -> E4 sequence and all P64-01..18 requirements retained.
The original source base is `d35d6d237ea57d77181aeee10902eeae085dbd9d`.
The Start-captured Usage Contract requires actual connected evidence at Phase
review, including the one-file route, AI recording and fresh-process lookup.

The [E1 proposed contract](../spec/evidence-storage.md) records inspected reuse,
minimum public types, durable records and the managed text File route with its
explicit host-prepared directory prerequisite. Later directory/compression
producer delivery remains required. This semantic handoff does not accept E1
or check any outcome; storage implementation, native validation, independent
review and closure retain their own gates.
See the [execution record](../journal/2026/10/2026-10-20-phase-6.4-evidence-storage.md)
for the actual session and host-owned routing location.

## Common Evidence adoption — 2026-10-20

[Phase 12](phase-12.md), owned by sm-workflow開発, delivers the minimum common
Evidence service and actual automatic Action -> archive -> Journal -> read route
before E2 integration. This Phase, owned by sm-workflow-dev開発, adopts that
delivery and completes operational failure/batch/compression/retention use.
See the [common specification](../spec/common-evidence.md) and
[two-chat handoff](../journal/2026/10/2026-10-20-phase-12-common-evidence-handoff.md).
One common implementation is extended; no separate 6.4 Evidence/Journal store.

Existing E1 acceptance, Goal/Session/base, native Plan, pending decisions, counters,
history and validation remain. The following E2–E4 text is the adopted future
plan; native adoption is owned by the current execution and is not performed by
editing these documents. Preserve all checklist IDs and checked flags. E1 text
remains historical contract acceptance, not a reason to retain superseded absolute
path display or AI Markdown transcription in the new route. The old location API
proposal's design motivation is superseded; its actual native records remain.
Phase 12 delivery supplies reusable producer evidence, not automatic E2 acceptance.

## Goal

Keep development/test work files and execution evidence outside Git. Adopt the
common Evidence service: Advance captures declared completed Action outputs,
saves actual material and records its Evidence reference through common Journal.
AI consumes IDs/results without clerical notification or Markdown transcription.
A fresh process reads the Journal entry and retrieves the same Evidence payload.
Explicit AI/client registration remains available for additional external material.

This is newly adopted scope, not a repartition of Phase 6 through 6.3. Their
ownership, acceptance and PHASE-6.3 aggregate-full-validation responsibility
remain unchanged. Gradual use may start after 6.3; this storage feature is
delivered next, before Phase 7 workspace synchronization.

The installed single-repository `sm-repository-sync` is already required for
[6.3 production acceptance](phase-6.3.md#single-repository-production-acceptance--2026-10-18).
This Phase adds archive/recording/lookup to that usable route; neither it nor
its storage completion is a gate for initial synchronization use.

Preserve the [6.3 use tiers](phase-6.3.md#three-skill-use-tiers--2026-10-18):
ordinary SplitPhase and single-repository RepositorySync, limited single-repository
GoalPhase. Evidence storage supports those routes without itself delivering the
Phase 7 related-project management needed for full practical GoalPhase adoption.

## Main route first

```text
Advance obtains one completed test Action's result and released log
  -> common Evidence allocates/reuses the source-associated Evidence ID
  -> managed storage archives the actual log outside Git
  -> common Journal records the storage observation and Evidence reference
  -> AI receives short actual outcomes and references, without transcription
  -> fresh process reads Journal and retrieves the same Evidence payload by ID
```

Use the normal public Operation/skill-client path, configured durable storage
and one local file driver. A mocked archive response or an allocation-only test
does not establish this route. Complete it before implementing multiple files,
compression and retention-management branches. Keep it as regression coverage.

## Connected development order

| Batch | Checklist | Delivery |
| --- | --- | --- |
| E1: minimum contract | P64-01–03 | Classify evidence/work files versus canonical artifacts; reuse existing reference/storage facilities; specify one-file allocate/archive/lookup and the AI record response. |
| E2: working main route | P64-04–08 | Adopt Phase 12 on the trial host; actual Action-driven archive, common Journal reference, fresh-process Journal/Evidence read and Git exclusion. |
| E3: bounded failure and observation | P64-09–12 | Exercise Phase 12 failure/observation in actual development; reuse producer checks, add missing operational cases and Journal-only continuation without rerunning the Action. |
| E4: operational branches and closure | P64-13–18 | Extend the same common service for batch, compression and retention/hold/due; coordinate runtime ownership, then integrate/validate/close and hand off. |

Each batch specifies its behavior before implementation. E1 freezes only what
E2 needs; detailed branch design and its matrix grow with E3/E4. No separate
acceptance/review loop is required per helper or individual file.

## Current contract for E2–E4 (E1 acceptance retained)

- Reuse existing Artifact/Blob references and configured file/storage access
  where appropriate. Inspect `GoalPhaseArtifactReadPolicy` and the existing
  artifact-reference applicability contract; those read boundaries do not
  themselves grant archive writes. Do not create a second Workflow engine,
  generic Blob store or NativeHostEvidence issuer to implement storage.
- Runtime selects actual declared Action outputs and source relationships;
  explicit AI/client input is for additional external material. Reuse existing
  context and actual result/output identities rather than another full envelope.
  Phase 12 defines and delivers the shared public typed interface.
- sm-workflow owns naming and retains the exact logical destination internally.
  Return Evidence ID, real Journal Entry reference when recorded, and independent
  outcomes. No absolute display path or additional CNCF location API is required.
  Preserve the saved destination; unsupported/conflicting names remain explicit.
- The archive root is `~/backup/journal/YYYY/MM/<project>/<phase-or-task>/<run-id>/`
  on the configured host. Use the user's local date for allocation year/month,
  retain the original execution date separately, and keep a previously returned
  destination stable across a month boundary. Never invent a Phase just to name
  a directory. Location notation is not remote-access authority.
- Model allocation, successful storage, failure/indeterminate outcome and actual
  availability separately. Allocation is not storage. IDs/references and typed
  state carry meaning; no management hashes, content-derived names, copied-state
  certificates, claim locks or contention protocol for hypothetical writers.
- Active outputs remain at their bound private/ignored locations while any
  writer, runner or resumable consumer needs them. Archive only selected,
  released, task-owned material. Verify the actual archived file is readable
  before removing its temporary source; a failure must not discard the source.
- Runtime records the actual storage observation and Evidence reference through
  common Journal. AI uses the returned IDs and states. Pending allocation is not
  storage; Action outcome, archive, Journal recording, current availability and
  review/acceptance remain separate. Same-source continuation reuses the record
  without another Action, allocation or recording-only AI task.

## Storage and retention boundary

Temporary work files belong in private or ignored work areas. Archive only
selected evidence worth retaining, not every temporary file. Logs, raw result
JSON, saved review reports, screenshots/videos and generated test observations
stay outside Git regardless of size/compression. Project records may retain
Evidence/Journal references and observed retention facts, not raw execution reports.

Source, intentional executable fixtures, requirements, human decisions,
canonical Phase status and accepted models used by successors keep their
development/management roles. Their lifetime is not an evidence-retention TTL.
Already tracked/staged evidence requires separately authorized cleanup; this
Phase does not rewrite Git history, unstage user work or migrate all old files.

Default retention is 30 days after the producing workflow/Phase completes.
Active workflows have no expiry; unresolved investigations or explicitly needed
checks may be held with reason and review date. Return/store the exact expiry
basis. E4 supports listing due material and holds, not scheduled deletion.
Expiry does not revoke past acceptance. Missing evidence for a new claim remains
missing and may require new validation under its normal policy.

For multiple files, return per-file source/Evidence/status associations and
truthful partial outcomes. If compression is selected, settle the internal `.gz`
destination before storage, return the same Evidence ID and prove decompression. Do not recompress
already compressed media without need or change evidence identity because its
physical representation changes.

## Failure and restart scope

E3 uses the existing allocated Evidence ID to observe actual retained state
after an archive response is lost. Unknown file/store outcomes remain explicit;
do not blindly repeat a destructive move, allocate another archive, replace an
existing destination or fabricate a successful record. Bind evidence references
only to actually stored material. Journal-write failure may be recorded again
from the stored result without rerunning the test or moving the file again.

This is bounded evidence-storage observation and lookup. It does not reinstate
excluded same-execution replay, competing-caller, exhaustive-history or generic
interrupted-Workflow recovery guarantees from earlier Phases. Existing source
authority, execution permissions and single-user policy remain in force.

## Validation, completion and handoff

E2 proves Phase 12 adoption with one actual Action-driven archive, common Journal
recording and fresh-process Journal-to-Evidence read through configured services. E3 adds selected file
and persistence failure cases; E4 extends the same driver for batch/compression
and expiry/hold boundaries. Check that the source repository's selected Git
delta contains only intended source/management changes and location records,
not the execution payload. Store this Phase's own evidence outside Git too.

Use focused Executable Specifications and per-batch acceptance/review appropriate
to the changed boundary. Complete one independent Phase review and final full
sm-workflow validation using sbt-native locks for the settled Phase 6.4 implementation,
plus the ordinary local release closure. Reuse applicable earlier evidence;
storage moves alone neither invalidate tests nor justify rerunning them. Phase
6.3 remains final owner of its original four-member aggregate chain; Phase 6.4
does not extend that chain or defer its validation to Phase 7.

Hand successors the accepted shared storage Operations/client response,
host/root setup, automatic recording and supported failure/retention limits. RepositorySync
uses this facility for its own released evidence before scope freeze; it does
not treat successful storage as product validation or archive live inputs.

## Exclusions and upstream boundary

No remote transfer, distributed store, full historical migration, Git history
repair, automatic deletion/scheduler, new worktree operation, or dependency-JAR
publication/overlay is included. Phase 7 retains the artifact-overlay feature.
This plan authorizes no runtime execution, installation, cleanup or commit now.

Reuse accepted CNCF/Cozy contracts. If implementation needs changes to either,
follow the existing upstream wait condition: identify the missing contract and
wait for its separately owned delivery rather than editing the dependency or
building a local substitute. The shared evidence-retention policy is the current
operational baseline; freeze product behavior in sm-workflow design/spec.
