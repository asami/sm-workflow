# Official runtime ABI handoff: dependency scope admitted

The user explicitly instructed: `bt-cozyを修正対象に追加して進めて`.
In the immediate preceding question the exact repository was sbt-cozy;
interpret this as admission of `/Users/asami/src/dev2026/sbt-cozy`.
Keep the same sm-workflow Phase 2 objective. Local source changes and bounded
validation are admitted; public publication, push, release and commits are not.
Continue the previously requested parent-direct limited recovery.

The admitted dependency is clean `main`, version `0.1.18-SNAPSHOT`.
sm-workflow defaults to released `0.1.17` and already supports
`-Dsbt.cozy.version` / `SBT_COZY_VERSION`; use that existing override for
downstream validation after a local SNAPSHOT refresh, not release mutation.

Own CozyRuntimeAbiResources.scala, CozyRuntimeAbiResourcesSpec.scala and
bounded generation/cache/resource wiring in CozyPlugin.scala. Preserve official
sidecar bytes before delegate-work cleanup and expose them under
`META-INF/cozy/` through Compile/resourceManaged. Reject ambiguous official
generation paths, record a content-checked handoff epoch, remove stale owned
files on absent declarations/no sources/legacy backend and restore deleted
resource projections. Bump generation-state version to invalidate old caches.
No CML parsing, alternate ABI serialization or fixture promotion is admitted.

Validation planned: focused handoff and existing delegated-generation specs;
local plugin publishLocal; consumer generation/resource/package checks using
the existing version override. The new isolated-JAR classloader spec checks
the resource projection helper, not actual SBT packageBin wiring or official
generator integration; those remain separate required downstream evidence.
## Focused evidence

Attempt 468 (`SMWF-SBTCOZY-ABI-HANDOFF-DIRECT-20261001-468`) passed
45 tests in two suites, with zero failed/canceled/aborted. Both SBT and wrapper
exited 0 and the shared lock was released. The structured summary was read:
`/tmp/skill.cncf.d/cncf-sbt-7da281c1b19cacd7d761b7bf6fce1874184d64d0decfdff9f7b5c89ef21a4abc-81ad1e1ca7fb5abfa41c0b8b8fa55788/90391-20261001T002656Z.summary.json`.

Attempt 469 (`SMWF-SBTCOZY-ABI-REFRESH-DIRECT-20261001-469`) successfully
published `0.1.18-SNAPSHOT` to normal local Ivy for downstream validation;
SBT/wrapper exited 0 and lock was released. This is not public publication.
No Phase closure is claimed; actual downstream generation/packaging and runtime
catalog admission remain to be proved.

Attempt 470 stopped before generation because the Cozy development runtime
classpath was stale. Attempt 471 successfully ran the official
cozyExportRuntimeClasspath task without Cozy source changes. Attempt 472 then
failed aggregate provenance validation: current sbt-cozy preserves the canonical
main-root `scala/` prefix even when the consumer's configured cozyTargetDir
already ends in `scala`, producing `scala/scala/org/...`. The expected provenance
contains `scala/org/...`. Keep provenance validation enabled. Extend the same
admitted generation frontier with generatedDestination and a regression case
covering canonical main-root, explicitly configured Scala-root and domain
outputs. This is a source placement correction, not producer-byte rewriting.
Both failed attempts terminated and released the shared lock; no packaging
success or runtime admission is claimed from them.

Attempt 473 passed 46 tests in two suites and refreshed local SNAPSHOT output.
Read-only inspection also found interrupted duplicate-root files with no
generated-files manifest (the failed provenance rebind occurs before manifest
installation). Extend the same correction to remove only exact producer-byte
matches at mechanically derived obsolete paths. Preserve conflicting edits and
fail closed, and copy Scala bytes unchanged. Add the interruption/conflict
regression before the next downstream run; do not remove a whole managed tree.

## Current verified boundary

Attempt 474 passed 47 tests in two suites and refreshed local
`0.1.18-SNAPSHOT`. SBT/wrapper exited 0 and lock released. The structured
summary was read at
`/tmp/skill.cncf.d/cncf-sbt-8fe070f469b5e711a74aaad9d2873b50e3f0e8b6d16c8ef43bd212c413490fc7-f9ed4d9d8fa8106016c89f751dd8bf94/2751-20261001T003617Z.summary.json`.

Attempt 475 successfully ran sm-workflow `Compile / packageBin` with the
existing `-Dsbt.cozy.version=0.1.18-SNAPSHOT` override. Official generation
produced 65 Scala sources and provenance rebinding passed without disabling its
checks. SBT/wrapper exited 0 and lock released; the structured summary was read
at `/tmp/skill.cncf.d/cncf-sbt-5b5651c384772a4174f6777a4f760ec1e56c2059fe4936c3f0605dce7f666d03-380611c23bf7c0fd1cf3137bd4809720/3405-20261001T003710Z.summary.json`.

The staged official sidecar, Compile/resourceManaged projection and actual
main JAR entry all have the same SHA-256 for each resource:

- `META-INF/cozy/statemachine-workflow-abi.json`:
  `5ba2009ac2dd557eaa335bcccd2d3851c56f2eafb964eef5ad789de0dc66ed83`.
- `META-INF/cozy/candidate-admission-producer-abi.json`:
  `3feb7b126d0d3afcb9990e408559909b27660daacf9f9814114155c439c1c198`.

The actual packaged artifact is
`target/scala-3.3.8/textus-sm-workflow_3-0.1.0-SNAPSHOT.jar`.
No test fixture was copied to main resources. The helper executable spec also
uses an isolated parentless JAR classloader; actual packageBin byte checks were
performed separately with unzip/shasum.

Attempt 476 passed the previous downstream accumulator unchanged: 58 tests in
eight suites, zero failed/canceled/aborted; SBT/wrapper exited 0 and lock released.
Generation reused the complete 65-source cache with the runtime ABI manifest.
The structured summary was read at
`/tmp/skill.cncf.d/cncf-sbt-08e72a4fd761471c0d83603445ffacfc4caa3aa794c4b6d747a888a2527d9a1a-04aac4d092ff040006be00de39b58af4/4217-20261001T003825Z.summary.json`.
Both repository diff checks passed. No SBT session remains live.

This closes the observed official sidecar packaging gap, not Phase 2 or CLI
Workflow acceptance. Runtime Catalog still receives single-Workflow fixture
text in its current tests. The real official resource contains GoalPhase,
SplitPhase and RepositorySync together, while CNCF CandidateWorkflowAbi.parseC
requires exactly one workflow. Next resolve explicit compiled Workflow identity
selection in the canonical receiver while retaining the full original artifact
bytes/fingerprint. Do not filter/re-serialize the JSON inside the Component or
promote the single-Workflow fixture as a production resource. Full producer
policy correlation, trusted runtime configuration/host authority, declared
public recovery and managed external commands remain unfinished. The default
plugin version is unchanged; no public publication, push, commit or Phase
acceptance was performed.
