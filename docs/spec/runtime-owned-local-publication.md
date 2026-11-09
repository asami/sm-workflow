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
  source for a publication effect, launch SBT, recover a consumer or advance a Session.
  When the producer declares artifact inputs, it may perform typed current Git
  observation to return a separate nonexecuting applicability fact.

Responses contain `status`, `reason`, and the actual publication facts. The
facts identify the logical target, observed native source, issued question,
actual process capture when available, and confirmed preparation successor.
Request/source facts use normal Git revisions and managed records; no new
content hash or manual artifact enrollment is introduced.

## Declared committed non-input source applicability

The optional producer `artifactInputs` vector defaults to empty. Each entry has
exactly `schemaVersion=sm-workflow.local-publication-artifact-inputs.v1`,
`identity`, positive canonical string `revision`, `targetIdentity`, absolute URI
`destination`, artifact ContextReference, and distinct exact repository-relative
file `nonInputPaths`. One declaration is permitted per existing producer target.
The declaration resolves through the normal Component parameter and the
selected fixed `publish-local` program. It supplies no arbitrary command or
process authority. Consumer `nonInputExclusions` never create a producer declaration.

Declarations cannot classify `src/`, `project/`, `build.sbt`, `version.sbt`,
`project.yaml`, `.cozy/`, `.textus/`, `.git*` controls, `.jvmopts` or `.sbtopts`
as non-inputs. Globs and recursive directories are not declarations. Unknown or
undeclared paths do not qualify, including documentation paths not explicitly
selected by the producer.

Only the latest actual successful, Exited-zero, complete and untruncated
publication can apply to a changed clean committed source. Typed native Git
observes the canonical root, branch, raw status, HEAD/tree and index equality,
verifies both original and current commits and their real ancestry, and reads
the complete tree delta. All actual changes must be declared regular-file A/M/D
entries with consistent modes and blobs; modification cannot change mode.
Renames, special entries, binary content, dirty/index mismatch, nonancestor and
undeclared inputs refuse reuse. Typed I/O, incomplete and contradictory native
observations propagate failure and never authorize another effect as fallback.
Current observations repeat before a retained applicability decision skips work.

`sm-workflow.local-publication-source-applicability.v1` retains the literal
original Request, clean current Source, canonical root/branch, producer
declaration, actual selected program JSON, native HistoryCommit ancestry and
complete StageChange delta. It binds to the original actual successful result,
its participant, optional owner Session, target/workspace/component, artifact,
destination and safeProgramIdentity. The external observation's publicationOwner
remains its independent owner locator, not a Session identifier.

Ordinary nonexplicit ensure may retain `Unnecessary` for the current source with
an optional applicability fact. It adds no actual target publication attempt and
does not replace the original latest Published result. Repeated prerequisites
reuse the current decision only while fresh applicability remains valid.
Explicit publication retains ordinary execution semantics. A newer failed,
UNKNOWN, incomplete or nonstarted actual writer blocks reuse of the old success;
collection never searches past it. Historical collect keeps original results
literal and performs no publication or progression.

Public `LocalPublicationResult` and successful `CollectionResult` carry optional
`applicability` separately from `publication`. Normal collect/collectLatest
reassess the latest actual result; undeclared, dirty or input-changing sources
return no applicability. The original publication remains literal Published.
New unnecessary LP.Result may retain applicability only with no process or
preparation and its exact current source. It never qualifies as Published.
Absent new fields are omitted, preserving old JSON; explicit null, malformed
facts and applicability attached to Published fail closed. Nonempty producer
declarations are retained in publicationPolicy; empty policy text stays literal.

## Necessity and continuation

Only the latest actual target publication, when successful for a clean native source, permits skipping the
same source and known documentation-only edits. A target-scoped immutable attempt sequence retains actual order; any intervening dirty/other-source publication supersedes the old receipt, and a latest intent without a terminal result prevents new-effect bypass. Missing applicable success,
actual source/build edits, and explicit requests require publication. Unknown
staged paths alone produce a bounded build-relevance question. Unknown unstaged
inputs are conservatively rebuilt; an answer cannot stand in for unknown bytes.
Renamed source paths retain their original provenance in this decision.

Historical target-attempt traversal matches `identity`, `component`, `workspace`
and `dependency`. Changing only `beforeChecks` or `beforeSync` does not change
that effect identity: ordinary source necessity still decides whether a new
request publishes, and retained unknown effects still prevent execution.
Scheduling changes grant no retry or renewed authority. Persisted targets and
requests retain both flags; replay, collection and qualified successor admission
continue to require their exact full bindings. Historical rows are never rewritten.

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
Source and Request schemas and equality are unchanged; absent Result extensions
retain their literal old representation. No hash, second
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
