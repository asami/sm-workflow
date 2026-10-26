# Runtime-owned local publication

## Ownership

sm-workflow owns local-publication necessity, execution, retained actual outcomes
and return to the owning workflow. Explicit `publishLocal` requests use the same
path. AI may answer a concrete unresolved question about a staged path's build
relevance. It does not choose a publication policy, create a standalone SBT
runner, or maintain a second permission ledger.

`sbt_publish_local_attempt` constructs one typed External UoW intent. CNCF's
existing interpreter admits the current workspace and process capability and
executes normal `sbt --batch ... publishLocal`. Executable, cwd, environment,
JVM policy, deadlines, output bounds, termination and native sbt locking remain
framework responsibilities. No application-specific main or process runner is
added. Independent nonpublication development commands remain supported.

## Host configuration

The ordinary CNCF Component parameter `sm-workflow.local-publication` contains
`storeName`, an existing managed entity `collection`, and declared `targets`.
Each target has `identity`, `component`, logical `workspace`, optional
`dependency`, and optional `beforeChecks` (default false). The host must admit
the record collection, typed Git observation including IndexTree, and the exact
typed SBT `publish-local` program for that workspace. Properties are resolved
through Component initialization and ExecutionContext, not parsed by the app.

The caller supplies no executable, argv, environment, destination, limits or
grants. Existing authorization for the same target, purpose, acceptance boundary
and effects continues. Publication is not a blanket new Human gate; genuinely
uncovered scope alone needs a new decision. Missing host capability remains a
typed admission/configuration result, never an implicit fallback.

## Public operations

- `LocalPublicationService.ensureLocalPublication`: accepts `requestIdentity`,
  `targetIdentity`, optional `ownerSessionId`, `explicit`, and optional bounded
  `answer` (`question`, `publish`, `rationale`). A selected owner Session is
  authenticated through its existing native record. The request identity is
  immutable for its participant, target, purpose and observed source.
- `LocalPublicationService.collectLocalPublication`: the same identifying
  fields, without an answer. It reads retained outcomes only. It cannot inspect
  source, launch SBT, recover a consumer or advance a Session.

Responses contain `status`, `reason`, and the actual publication facts. The
facts identify the logical target, observed native source, issued question,
actual process capture when available, and confirmed preparation successor.
Request/source facts use normal Git revisions and managed records; no new
content hash or manual artifact enrollment is introduced.

## Necessity and continuation

Only the latest actual target publication, when successful for a clean native source, permits skipping the
same source and known documentation-only edits. A target-scoped immutable attempt sequence retains actual order; any intervening dirty/other-source publication supersedes the old receipt, and a latest intent without a terminal result prevents new-effect bypass. Missing applicable success,
actual source/build edits, and explicit requests require publication. Unknown
staged paths alone produce a bounded build-relevance question. Unknown unstaged
inputs are conservatively rebuilt; an answer cannot stand in for unknown bytes.
Renamed source paths retain their original provenance in this decision.

The runtime saves request and intent before execution, then the actual result.
`published`, `unnecessary`, `semantic-required`, `not-started`,
`admission-rejected`, `failed`, and `uncertain` are distinct. Collection and
repeated ensure reuse successful terminal results. An intent without a terminal
result remains uncertain and is never replayed. Only an actual typed NotStarted
result permits the next ordinary ensure to perform one preparation successor
under the unchanged scope; preparation does not consume a product repair.
Admission rejection, actual failure and unknown effects are not nonstart.

Configured `beforeChecks` targets use this same implementation before native
Step/Phase checks. `dependency` narrows a prerequisite to the selected dependency
binding. Waiting returns the retained publication facts through ordinary Advance;
publication does not rewrite Start, Plan, original base, accepted results or
repair counters. Existing completed validation is collected without republishing.

## Publisher restoration and qualification

An installed baseline that cannot yet execute PublishLocal cannot publish itself
through an AI fallback. The explicit source-build selection
`TEXTUS_SM_WORKFLOW_CNCF_SOURCE_ROOT` uses ordinary SBT project dependency
resolution, validates declared producer coordinates, and prepares the supported
normal source host. It does not copy JARs, publish CNCF, isolate caches, consume
an unrelated merge, or turn a policy refusal into a retry. Its first publication
still runs through the sm-workflow Operation and UoW. Normal publication and
isolated installed-CLI qualification are separate results.

Focused verification covers necessity, bounded semantic answers, actual typed
outcomes, response recollection, nonstart continuation and no replay. A fresh
plain SBT fixture verifies actual publication and skip through ordinary source
and isolated installed CLI. Existing real Sessions remain owned by their callers.
