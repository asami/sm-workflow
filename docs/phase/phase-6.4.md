# Phase 6.4: Local Evidence Storage and AI Recording

status=planned
planned_at=2026-10-13
depends_on=phase-6.3.md
checklist=[Phase 6.4 Checklist](phase-6.4-checklist.md)

Owner: sm-workflow. Predecessor: [6.3](phase-6.3.md). Successor: [7](phase-7.md).

## Goal

Keep development/test work files and execution evidence outside Git. Let AI
notify sm-workflow of selected evidence, receive its exact future filename,
request archival after its live use ends, and record the actual archival result
in the project journal. Resolve the same Evidence reference after process restart.

This is newly adopted scope, not a repartition of Phase 6 through 6.3. Their
ownership, acceptance and PHASE-6.3 aggregate-full-validation responsibility
remain unchanged. Gradual use may start after 6.3; this storage feature is
delivered next, before Phase 7 workspace synchronization.

## Main route first

```text
One completed test log outside Git
  -> AI notifies sm-workflow of the selected file
  -> sm-workflow returns Evidence ID and exact reserved destination
  -> sm-workflow archives the file after the caller releases live use
  -> actual stored result returns to AI
  -> AI records ID, host/location and retention date in the project journal
  -> fresh process resolves the same ID and reads the archived log
```

Use the normal public Operation/skill-client path, configured durable storage
and one local file driver. A mocked archive response or an allocation-only test
does not establish this route. Complete it before implementing multiple files,
compression and retention-management branches. Keep it as regression coverage.

## Connected development order

| Batch | Checklist | Delivery |
| --- | --- | --- |
| E1: minimum contract | P64-01–03 | Classify evidence/work files versus canonical artifacts; reuse existing reference/storage facilities; specify one-file allocate/archive/lookup and the AI record response. |
| E2: working main route | P64-04–08 | Actual local archive and durable reference, real AI journal recording, fresh-process lookup/read and Git exclusion. |
| E3: bounded failure and observation | P64-09–12 | Preserve source on failed/uncertain storage, resolve an already allocated ID after lost response, reject unavailable/in-use or conflicting destinations truthfully, and report journal-write failure separately. |
| E4: operational branches and closure | P64-13–18 | Multiple-file mapping, optional text compression, retention/hold/status, integrated review/validation and Phase 7 handoff. |

Each batch specifies its behavior before implementation. E1 freezes only what
E2 needs; detailed branch design and its matrix grow with E3/E4. No separate
acceptance/review loop is required per helper or individual file.

## Contract work to freeze in E1

- Reuse existing Artifact/Blob references and configured file/storage access
  where appropriate. Inspect `GoalPhaseArtifactReadPolicy` and the existing
  artifact-reference applicability contract; those read boundaries do not
  themselves grant archive writes. Do not create a second Workflow engine,
  generic Blob store or NativeHostEvidence issuer to implement storage.
- AI supplies the selected source, project, actual Phase/task/run relationship,
  evidence kind and optional preferred name. Runtime resolves existing session
  context rather than requiring another full context envelope. Exact public
  type/Operation names are design/spec work, not declared APIs in this plan.
- sm-workflow owns destination naming and returns Evidence identity, host,
  exact reserved filename/location and storage state. Preserve that destination
  through archival; do not silently choose a different filename after AI has
  recorded it. Unsupported/conflicting names have an explicit result.
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
- AI records the actual result: Evidence ID, host/location, state and retention
  date. A pending location may be recorded as pending, never as stored. Keep
  archive success distinct from successful AI journal write and from execution
  success, review acceptance or applicability to another Candidate.

## Storage and retention boundary

Temporary work files belong in private or ignored work areas. Archive only
selected evidence worth retaining, not every temporary file. Logs, raw result
JSON, saved review reports, screenshots/videos and generated test observations
stay outside Git regardless of size/compression. Project records may retain
their location and retention date, not embed the raw execution report.

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

For multiple files, return per-file source/destination/status associations and
truthful partial outcomes. If compression is selected, settle `.gz` filenames
before they are returned and prove readable decompression. Do not recompress
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

E2 proves the one-file route with actual storage, actual AI journal editing and
a fresh process using the ordinary configured services. E3 adds selected file
and persistence failure cases; E4 extends the same driver for batch/compression
and expiry/hold boundaries. Check that the source repository's selected Git
delta contains only intended source/management changes and location records,
not the execution payload. Store this Phase's own evidence outside Git too.

Use focused Executable Specifications and per-batch acceptance/review appropriate
to the changed boundary. Complete one independent Phase review and final full
serialized sm-workflow validation for the settled Phase 6.4 implementation,
plus the ordinary local release closure. Reuse applicable earlier evidence;
storage moves alone neither invalidate tests nor justify rerunning them. Phase
6.3 remains final owner of its original four-member aggregate chain; Phase 6.4
does not extend that chain or defer its validation to Phase 7.

Hand Phase 7 the accepted storage Operations/client response, host/root setup,
recording instructions and supported failure/retention limits. RepositorySync
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
