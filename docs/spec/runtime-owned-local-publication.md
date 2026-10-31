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
`dependency`, and optional `beforeChecks` and `beforeSync` (both default false). The host must admit
the record collection, typed Git observation including IndexTree, and the exact
typed SBT `publish-local` program for that workspace. Properties are resolved
through Component initialization and ExecutionContext, not parsed by the app.

The optional `catalogOutputs` array defaults to empty for existing hosts. Each
entry contains exactly `targetIdentity`, `path` and `selection`: a declared
target, one canonical relative `src/main/catalog/car/*.yaml` path, and a valid
Git selection identity. At most sixteen entries and one entry per target are
accepted. This policy supplies no process or File authority. The host separately
admits a finite fixed `TreeEntries` program selecting exactly that catalog,
ordinary `BlobContent`, and File reads through the target's existing workspace
grant and resource limits. Missing admission fails through the owning UoW.

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

For an explicitly bound catalog, the runtime may project one unstaged modified
worktree entry with an unchanged index status and no rename. It reads the actual
index tree's regular blob and the actual working text through typed Git and File
UoW operations. Both texts must be valid schemaVersion 2 CAR catalogs whose
nonempty version entries identify the exact target component. Only canonical
four-space `publishedAt: <ISO offset timestamp>` version lines may differ. The
timestamp values must parse as offset dates and match the parsed version fields
in order. Core RecordDecoder parses a copy with just those timestamp scalars
quoted, because its YAML boundary rejects timestamp tags. The original file
bytes are never rewritten. Parsed JSON with only version timestamps removed and
the exact text with only those line values replaced must both be identical.
Comments, whitespace, line endings, block scalars, other metadata and all other
paths stay inputs. A final ordinary Git observation must equal the initial one.

Staged catalogs, renames, additions, deletions, conflicts, nonregular entries,
missing or binary text, unrecognized catalogs and meaningful catalog changes
are not projected. Crossed or extra selected tree entries fail. HEAD and index
tree remain exact, and unknown publication intent remains unreplayed. Hosts
without this policy keep their previous source comparison. Persisted Target,
Source, Request and Result schemas and equality are unchanged; no hash, second
publication ledger or general ignored path is introduced. Projection proves
only the difference from the actual indexed catalog. An older retained source
that already contains ambiguous catalog dirtiness is preserved conservatively;
the runtime cannot rewrite that history or automatically claim applicability.

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
The catalog fixture publishes once, observes its timestamp output from another
process without publishing again, then requires normal publication for changed
catalog metadata and for staged catalog/other source inputs. Coherent installed
source fixtures configure sbt-cozy application activation in fresh
`.cozy/config.yaml`; their unchanged `project.yaml` retains normal project
coordinates and publication declarations.

## Standard SBT and synchronization completion

The shipped Component defaults include target `repository`, workspace
`sm-workflow-repository`, a managed publication collection and a typed
`publish-local` program. CNCF resolves `${executable.sbt}` from PATH; a normal
host needs no explicit executable or separate publication host. Host overrides
still require their corresponding storage/workspace grants.

Publication updates artifacts and publication metadata and never commits them.
A host target with `beforeSync: true` selects publication at the final integrated,
clean source frontier of RepositorySync. The runtime retains one request for that
Session/frontier, collects it on re-entry, then commits the resulting ordinary
`src/main/catalog/car/*.yaml` outputs before push and final convergence. Neither
staging nor an uncertain commit intent is replayed automatically. Noncatalog,
staged, renamed or concurrently changed outputs leave synchronization unfinished.
The final observation requires a clean tree and equal local/tracking/remote tips.
The default target does not silently enable publication for every sync.

## Installed command selection

Install the operational command through CNCF's `install-cli sm-workflow
--published-target org.simplemodeling.textus.SmWorkflow`, without a component
version. The generated command uses `newest`: each invocation compares enabled
stable and SNAPSHOT versions in the local component catalog by version number,
preferring stable over the SNAPSHOT of the same base version. A normal local
publication supplies subsequent invocations without reinstalling the command.
CNCF owns runtime resolution, component admission and artifact loading; the
workstation supplies no application launcher or source-classpath fallback.
Runtime version selection remains a separate normal CNCF host setting.

Call the generated command with an Operation selector and select the host by
process cwd. The finite skill caller's `--runtime-root` sets that cwd; it is not
forwarded as an executable argument and no extra `command` token is added.
Existing running invocations, accepted results, Sessions and uncertain intents
are not replayed when installation or publication changes.
