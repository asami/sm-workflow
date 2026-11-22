# Development Artifact Overlay

Date: 2026-10-06 (Asia/Tokyo)
Status: accepted planning contract; implementation and executable acceptance pending
Owning phase: [Phase 7](../phase/phase-7.md)
Acceptance: [Phase 7 overlay checklist](../phase/phase-7-artifact-overlay-checklist.md)

## Purpose and boundary

The [three-mode dependency delivery contract](dependency-development-delivery.md)
distinguishes ordinary assigned-chat commit/publishLocal delivery from isolated
development selections. This overlay is not a prerequisite for assigned-chat
delivery and ordinary publishLocal is not evidence of overlay acceptance.
Reuse this contract for explicitly selected managed-subtree/isolation needs;
the queue and completion policy belong to Phase 7's dependency delivery owner.

Give each development effort its own local artifact repository and let every
consumer worktree explicitly select a set of development artifacts. Cover local
development JARs and dependency metadata. Preserve ordinary dependency
resolution and global caches. Delegate SBT/Ivy/Coursier shared-cache exclusion
to those tools; sm-workflow does not impose machine-wide SBT serialization.
Independent builds may proceed concurrently. This is not a mirror of all
dependencies or a replacement cache.

This is a native sm-workflow capability. Workflow owns operation state,
progression and results; CNCF owns standard configuration/resources; typed
execution adapters own SBT and Cozy effects. Skills are optional callers through
the Phase 6 interface, not controllers of an additional file-based workflow.

The first driver uses SimpleModeler in the Workbench development workspace, with
Cozy and CNCF participating. These names are example configuration, never fixed
branches in the implementation. Git repository synchronization and artifact
publication are distinct operations; a successful Git sync does not publish or
select a new artifact, and artifact publication does not authorize a Git push.

## 1. Ownership and explicit location

One owning worktree holds the overlay for a development effort. Define its
project composition and local worktree operation under
`${project}/.textus/sm-workflow/`, using CNCF logical Component resources. The
standard local-provider layout is:

```text
<owner-worktree>/
  .textus/sm-workflow/
    resources/                    # versioned project/worktree definitions
    work.d/
      checkouts/                  # explicitly participating related worktrees
      artifacts/<overlay-id>/
        repository/               # development JARs and Ivy metadata
        staging/                  # incomplete publication output

CNCF-managed typed state:
  overlay -> owner and logical repository/staging resources
  publication -> origin, output set, dependency selection and outcome
  selection -> immutable mapping to completed publications
consumer binding -> consumer worktree, overlay and selection ID
```

The consumer binding is an explicit typed reference, not a JAR copy. Resolve its
location through the logical resource API, never parent/sibling directories,
repository names or the most recently used workspace. Consumers with no binding
use ordinary resolution. An invalid explicit binding fails; it does not imply
detachment. Owner/consumer worktree identities are part of the project workspace
model and are not inferred from branch names.

### Project composition and worktree operation

Version-controlled definitions under `resources/` describe the owning project,
participating repositories and dependency relationships, selected development
branches, whether to create a dedicated worktree or use an explicitly registered
existing worktree, producer/consumer roles and overlay ownership. Fix concrete
file/schema names during implementation using the standard CNCF configuration
and resource mechanisms, rather than introducing another discovery convention.

Keep machine-specific bindings, including absolute paths to existing external
worktrees, in CNCF local configuration. sm-workflow obtains the resolved typed
configuration and uses CNCF resource APIs for checkout/artifact operations.
Shared project definitions do not need one developer's absolute paths. Do not
silently move or replace a registered existing worktree.

`resources/` definitions are source-controlled; `work.d/` checkouts, artifact
repositories, staging and runtime state are not. Project setup owns the ignore
rules. Publication origins and current consumer selections remain typed runtime
records, distinct from the shared project definition. A producer checkout can
disappear without invalidating an already retained publication.

Place development JARs and Ivy metadata under
`work.d/artifacts/<overlay-id>/repository/`. sm-workflow passes the selected set
and the resource-resolved repository location to sbt-cozy for that build.
sbt-cozy resolves exactly the selected development coordinates; it does not scan
the directory for the newest JAR. Ordinary dependencies keep their existing
repositories/caches, and an exact selected development coordinate may also be
served by the normal cache. Each consumer retains its own explicit binding.

Use the existing CNCF hierarchical Component configuration and resource provider;
sm-workflow must not discover physical paths or maintain a second config lookup.
The local provider supplies this standard mapping and project setup supplies
ignored runtime storage. Workflow logic uses logical resources rather than
concatenating the physical path itself.
Keep overlay/publication/selection/binding state in the application-owned typed
store using canonical Entity/Repository facilities; Workflow progression remains
in the canonical Workflow runtime. No parallel JSON lifecycle ledger is required.
An SBT adapter may receive a bounded generated configuration containing logical
selection references and resolved settings, but it is not a second authority.

The original skill-oriented `.codex-workflow/artifacts` and consumer
`artifact-selection.json` layout is not the sm-workflow public contract. This
feature neither requires that layout nor silently migrates existing files.

## 2. Publication identities and origin

Every publication records:

| Field | Meaning |
| --- | --- |
| Workspace/overlay ID | Development effort owning this publication |
| Original coordinates | Normally declared organization, published module and version |
| Development coordinates | Coordinates with a unique version for this publication |
| Publication ID | Identity of one publication, shared by a declared multi-module batch |
| Producer origin | Repository, worktree, commit where available, and uncommitted-change record |
| Build dependency selection | Exact selection used to build this producer, or explicit ordinary-dependency mode |
| Declared output set and outcome | Required/optional artifacts, final locations and publication result |

Use actual published module identities including Scala/platform cross suffixes;
do not collapse distinct modules by guessing a base name. Configuration, artifact
type and classifier are retained where needed for correct SBT resolution.

For example an original `1.1.26-SNAPSHOT` receives a unique development version.
The SBT adapter design must fix its concrete format and cross-publication mapping
before implementation, with uniqueness across overlays and publications. It must
not reuse a mutable shared SNAPSHOT coordinate for different development content.

Published coordinates are never overwritten through overlay operations. A rebuild
allocates a new publication ID and version. Reusing an occupied coordinate is an
input conflict, not permission to replace it. Dirty producer builds are allowed:
record the actual Git revision (or its absence), staged/unstaged/untracked change
information and relevant ordinary Git diff/build-log references. Commit ID alone
must not imply a clean build or completely describe dirty-source provenance.

These records describe the build; they are not source immutability certificates.
Do not add management hashes, source snapshots, duplicated generated output,
whole-file comparisons, tamper detection or time-only expiry. Immutable here means
the API does not rewrite a published coordinate or selection, not that startup
must prove stored bytes have not changed. Reject contradictory inputs and actual
resolution/publication errors at their owning boundaries.

## 3. Repository contents and dependency metadata

Publish only explicitly selected development modules:

- Required: compile/runtime JARs and Ivy metadata describing transitive dependencies.
- Optional: declared auxiliary artifacts such as sources JARs.
- Excluded: wholesale copying of Scala, cats or other ordinary dependency JARs.

Metadata uses selected development coordinates for development dependencies and
preserves ordinary coordinates for all other dependencies. Record the selection
used during the producer build, including its own overlay dependencies. The
selected dependency mapping must affect both that build and its published
metadata. Do not require mechanical rewriting of source `build.sbt` files.

SBT integration must define how it supplies scoped build settings and dependency
overrides for direct and transitive dependencies, aggregation/cross-built modules
and relevant configurations. Repository precedence alone is insufficient.

### Build invocation and common build definition (2026-10-06)

The selected transport is an explicit JVM system-property argument supplied by
sm-workflow for each build invocation. Do not use a shell-wide environment
variable or ambient directory discovery to activate an overlay. For example,
the planned adapter can receive `-Dsm.artifact.selection=<execution-input-path>`;
fix the final property name and input schema with the SBT adapter contract.
The input identifies the consumer, overlay and immutable selection and supplies
the resolved build settings needed by the adapter. It is a bounded execution
input derived from canonical state, not a second selection/lifecycle ledger or
a copy of source/generated artifacts.

Use the same checked-in `build.sbt` for ordinary builds and all overlay selections.
Normal dependency declarations remain the baseline. Do not rewrite `build.sbt`,
`project.yaml` dependency declarations, resolver literals or source versions when
publishing, selecting, rolling back or detaching. Do not persist invocation
settings with `session save`. No selection argument means ordinary dependency
resolution; an explicit invalid selection remains an error.

The adopted 2026-11-12 method requires explicit one-time consumer opt-in in
`build.sbt` and `project/plugins.sbt`, with supported sbt-cozy installed. Enable
`org.goldenport.cozy.CozyDevelopmentArtifactsPlugin`, wrap selected direct
coordinates with `cozyDevelopmentDependency`, and declare expected original
coordinates through `cozyDevelopmentModules`, including transitive-only modules.
The helper preserves ordinary coordinates when no explicit selection is supplied.
A valid development selection does not require an ordinary baseline artifact to
have existed or to resolve. Invalid explicit input fails without fallback.

```scala
enablePlugins(org.goldenport.cozy.CozyDevelopmentArtifactsPlugin)
libraryDependencies += cozyDevelopmentDependency("example" %% "library" % "1.0").value
cozyDevelopmentModules += ("example" %% "library" % "1.0")
```

This replaces the earlier automatic-activation/no-helper premise. The same
checked-in opted-in build definition is used for ordinary, selected, updated,
rolled-back and detached invocations. Selection changes never rewrite that
build definition or its original dependency declarations. Verify effective
project/configuration precedence and actual direct/transitive coordinates.
The implementation must reject authored settings that contradict a selection.

The delivered initial adapter supports ordinary JVM libraries and Scala-cross
modules. It rejects sbt-plugin/meta-build mappings, project references, selected
classifiers and unmanaged JARs; builds replacing its validation or compile/test
wrappers are unsupported. These limits do not waive the original SimpleModeler,
Cozy generation, compatible/incompatible CNCF, lifecycle or integrated acceptance.
Those obligations remain pending until implemented and observed.

sm-workflow constructs the exact process arguments from the selected operation.
Passing an argument alone is insufficient until the adapter is installed and
enabled. For an existing SBT/Cozy process, apply a supported explicit selection
reload or restart it with the new argument before subsequent work; a startup
property must not be presented as dynamically changing an already running JVM.
Keep ordinary cache use and tooling-owned cache exclusion unchanged.

## 4. Immutable selection sets

A selection maps original module coordinates to exact completed publications and
development coordinates in one overlay. For example S3 may select C2, M5, N4 and
Z3 for goldenport-core, SimpleModeler, CNCF and Cozy respectively. Publishing M6
does not change S3. Create S4 and explicitly switch a consumer to use M6.

Reject conflicting mappings for the same dependency identity; do not use list
order or "latest" to decide. A selection cannot mix development publications
from another overlay. A consumer may switch to a different overlay only through
an explicit complete selection change, never implicit fallback or partial mixing.

Selection creation does not establish compatibility. Record the producer's build
selection separately from the consumer's effective selection. Intentional
consumer overrides apply transitively; differences from a producer's build
selection remain visible and require relevant consumer verification, not a claim
that provenance proves compatibility. In particular, compile generated Scala to
check that the selected CNCF provides APIs required by the selected generator.

Switching back to retained S3 is a selection rollback, not a Git rollback or
artifact rebuild. Old sets stay unchanged. Unretained artifacts may later be
explicitly pruned; report that a pruned selection is unavailable rather than
claiming rollback remains possible forever.

## 5. Resolution rules

| Situation | Required behavior |
| --- | --- |
| No development override for a dependency | Existing resolvers and global caches |
| Explicit development override | Exact development coordinates from the selected set, including transitive use |
| Selected publication incomplete or unknown | Error; no ordinary-version fallback |
| Required selected artifact cannot be resolved | Error; no ordinary-version fallback |
| Contradictory mapping for one module | Error; no implicit winner |
| Development artifact from a different overlay in the effective graph | Error identifying the conflicting dependency |
| Exact completed development coordinates available in global cache | Normal cache reuse is allowed |

A cache hit may supply an artifact of a known completed publication; it must not
turn an incomplete publication into a completed one or substitute a different
version. Availability means the required exact artifact can be resolved, including
through the permitted cache. Missing-artifact acceptance must cover genuine
unavailability, not assume every cache hit is an isolation failure.

Expose the effective resolved coordinates and file paths, including cache paths,
for direct and transitive dependencies. Compare typed selection/resolution facts,
not file-content certificates. Default consumers without an overlay must not
discover development versions merely because those versions exist in a cache.

## 6. Publication and selection transition

```text
select producer, declared outputs and build dependency selection
  -> build and generate dependency metadata into staging
  -> confirm the declared output set is complete
  -> complete publication into the repository
  -> create a new immutable selection set
  -> explicitly switch the consumer
  -> refresh execution classpaths and regenerate affected output
  -> compile and run required consumer validation
```

Only completed publications can be selected. A failed or interrupted build leaves
no selectable partial publication. For a multi-module batch, all declared outputs
must be complete before any part of the batch becomes selectable. The adapter
design must define staged publication visibility and failure recovery without
exposing staging as a normal resolver. A consumer sees one complete selection
binding, not a partially written mapping. Publication success is not consumer
verification success; selection may legitimately be selected-but-unverified or
selected-with-failed-verification, reported honestly by `status`.

Builds consume the selection resolved at start. A subsequent switch must not
alter an in-flight build's inputs. Serialize conflicting selection/start/prune
operations at the owning boundary and retain explicit in-use references until
the operation ends; no expiry or unchanged-content proof is required.

An already-running SBT or Cozy process must refresh/reload its dependency state
or restart under the new selection before running subsequent work. Do not report
the new selection as effective while the old classpath is still used. Record
the selection used for Scala generation; on selection change or rollback,
regenerate affected outputs and refresh compile/runtime classpaths. If impact
cannot be narrowed, regenerate the selected consumer's affected product set.
Do not clear all global caches as the mechanism for switching versions.

## 7. Operations and lifetime

Names below are conceptual; fix CLI/task names in the adapter design.

| Operation | Responsibility |
| --- | --- |
| `publish` | Build/publish the declared producer modules into the selected overlay; do not switch consumers implicitly |
| `select` | Create a selection from completed publications or choose an existing set, then explicitly bind the consumer |
| `status` | Report selected/effective dependencies, origins, resolved files and actual validation results or pending work |
| `verify` | Check selection versus actual resolution and perform relevant consumer compatibility validation |
| `detach` | Explicitly remove the overlay selection, refresh classpaths and regenerate affected output using ordinary dependencies |
| `prune` | Explicitly delete eligible unreferenced overlay artifacts; never clean global caches or shared Ivy outputs |

Expose these as registered typed sm-workflow Operations through the same CLI/MCP
service. Use the common Workflow request/continuation/result and execution
contracts for multi-step effects rather than inventing an overlay-specific
runner/permission envelope. Read-only status uses the canonical application view.
Publication, selection and verification remain separate outcomes. A caller or
skill must not advance the state by editing persisted records itself.

Protect currently selected artifacts, running-build inputs and explicitly retained
selection sets from pruning. Follow their required development dependency closure
and keep origin/publication records needed to explain retained artifacts.
Do not immediately delete publications when a producer worktree is removed.
Show prune candidates and the effects of the requested cleanup; perform deletion
only within its explicit scope. Selection alone is not deletion authority.

## 8. Execution and component responsibilities

| Owner | Required implementation |
| --- | --- |
| sm-workflow | Workspace/publication/selection lifecycle, registered Operations, typed state and dependency-aware verification orchestration through common Workflow contracts |
| CNCF resource provider | Explicit ownership and logical resource access to configured overlay/worktree locations |
| SBT integration | Unique development coordinates, staged publication, Ivy metadata, direct/transitive selection and resolved-file reporting |
| Cozy integration | Generator/runtime selection, selection recorded with generated products, classpath refresh and targeted regeneration |
| CNCF execution harness / SBT adapter | Execute typed publication/verification with scoped outputs and real process results; rely on SBT/Ivy/Coursier for their own cache exclusion |
| Phase 6 skill/client adapter | Invoke the registered Operations and return issued semantic results; own no publication/resolution/selection state machine |

Add a typed overlay-publication operation/adapter to the native runtime execution
path. Do not relabel a shared-Ivy `publishLocal` success as overlay publication
or accept an arbitrary command string as the implementation of this operation.

The typed operation must carry producer, owning worktree/workspace, declared modules
and artifacts, overlay/staging output locations and the producer's dependency
selection. Derive bounded SBT execution from the adapter contract. Permit its
explicit owner-local output locations without granting source edits or arbitrary
repository writes. Include normal declared build outputs and cache access.
Keep actual execution permissions at the execution boundary. SBT/Ivy/Coursier
own their shared-cache coordination; do not add an SBT-wide or machine-wide
mutex in sm-workflow or its product SBT adapter. Coordinate genuine conflicts
such as the same publication destination, consumer binding or active input/prune
operation by that resource, not because two operations invoke SBT. Do not use
isolated caches or remote publishing as implementation shortcuts. No Codex agent name or shared-skill
receipt schema belongs in the product's public operation/result contract.

Record process exit/logs, completed output locations and publication identity.
Distinguish build failure, incomplete publication and consumer validation failure.
No result from this route authorizes push, commit, public deployment or implicit
pruning. Normal resolvers/global caches continue to operate for ordinary modules.

The current [shared-skill dependency refresh contract](/Users/asami/src/development-workstation/common/codex/skills/cncf-workflow-protocol/references/typed-command-control.md)
is a development-environment limitation, not the product interface: it admits
exact `--batch publishLocal`, SNAPSHOT coordinates and outputs outside source
repositories. It cannot execute this new publication as-is. If the Codex
development harness needs to invoke the feature during implementation, extend
that harness in its owning project to call the typed runtime operation within
the admitted scope. The current Codex development environment still requires
its registered runner, scoped escalation and serial wrapper for development SBT.
This is a local harness constraint, not a product scheduling rule or permission
for the product to duplicate SBT cache locking. Do not make product operation depend on a
skill-managed lifecycle or use an old receipt as evidence of overlay success.

## 9. Integration order and executable acceptance

Fix the behavior in this specification first. Before coding the SBT adapter,
settle its exact version format, task/setting interface, module/configuration
mapping, metadata rewriting and completed-publication visibility. Then implement:

1. SBT publication and direct/transitive resolution.
2. Cozy generation/classpath selection and regeneration.
3. Native sm-workflow Operations/execution-adapter integration and CLI/MCP;
   Phase 6 skills consume that same interface where needed.

Use the existing registered runner for any authorized development validation;
do not execute a new overlay operation through the old publication route while
its admitted adapter is absent. Build connected implementation/specification
batches and independently review integrated behavior, not each helper in isolation.

The first acceptance driver is SimpleModeler, with six required scenarios:

1. Two consumer worktrees select different SimpleModeler development versions
   and actually resolve/use their respective versions.
2. Ordinary Cozy without an overlay keeps ordinary dependency resolution and
   expected generation behavior, unaffected by overlay publication/selection.
3. A generator emitting a new runtime API with an older selected CNCF fails
   consumer generated-code compilation; a compatible selection passes. Both
   outcome and actual selected/resolved coordinates are visible.
4. Publication failure, incomplete multi-module output and a genuinely missing
   selected artifact never silently resolve to the ordinary version.
5. Update and rollback refresh generated Scala and SBT/Cozy classpaths to the
   effective selection, including an already-running process. Detach likewise
   returns to ordinary dependencies through an explicit transition. Ordinary,
   selected, updated, rolled-back and detached builds use the same source build
   definition: selection changes operate through explicit invocation input,
   never build-file rewriting. Observe ordinary Git changes and actual resolved
   dependencies; do not add a file-immutability certification gate.
6. Throughout all six operations, the target artifacts under shared
   `~/.ivy2/local` are not modified. Observe configured destinations and actual
   publication/deletion effects; do not add a runtime integrity ledger to prove it.

Also cover conflicting selections, a foreign overlay in a transitive graph,
allowed global-cache reuse of exact development coordinates and prune protection
for active/pinned/in-use dependencies. Demonstrate that independent build requests
are not serialized solely for invoking SBT, while actual shared-output and
selection/prune conflicts are coordinated at their resource boundary. A local
test harness's SBT serialization must not alter the product policy under test.
Use ordinary command results, resolver
reports, generation records and consumer compilation/tests as evidence.

This plan does not claim any of these tests have run. The six driver scenarios
and supporting resolution/lifetime rules are Phase 7 acceptance; optional breadth
can be recorded in Phase 8 without silently dropping the initial delivery.

## 10. Minimal P7-A native route (2026-11-12)

`ManagedDevelopmentService` adds `publishDevelopmentArtifact`,
`selectDevelopmentArtifact`, `compileDevelopmentConsumer` and
`readDevelopmentArtifactStatus` to the normal CLI/MCP composition. Their typed
input is `edgeIdentity`, `requestIdentity`, `publication`, `selection`; callers
supply identities rather than physical paths or SBT command strings. The optional
`sm-workflow.managed-development` Component parameter declares `storeName`,
`collection` and `definition`. The definition declares `scope`, `ownerWorkspace`,
owner-relative `repository`/`staging`, and explicit consumer-to-provider `edges`.
Each edge reuses `GoalPhaseDependencyScope.Component` identities for provider and
consumer and independently names `localRepository`, `localJar`, `localProject`
and the original `requested` coordinates (`organization`, exact cross-module
`module`, `version`). The minimum JAR route requires a local JAR binding; a local
project binding does not implicitly become an artifact build or dependency.

CNCF's already-resolved process/workspace configuration maps logical workspaces
to machine-local roots. There is no second physical workspace profile, search for
a nearby checkout, or latest-artifact discovery. The host must grant the owner
workspace and its Component-record collection, producer Git observation and the
specific producer `scoped-publish` and consumer `compile` SBT programs. The
producer and consumer build definitions explicitly opt in as described above.
Shared project definitions belong under Component `resources/`; runtime roots
and artifacts belong in ignored `work.d/`. The current minimum consumes explicit
registered existing workspace bindings; managed checkout acquisition remains an
obligation of the later workspace route.

Publication invokes only CNCF's fixed `smDevelopmentArtifactPublish` transport,
with a bounded `cncf.scoped-sbt-input.v1` envelope and nested
`sbt-cozy.development-artifact.v1`. The version is
`originalVersion-smdev-{scopeLength}-{scope}-{publicationLength}-{publication}`.
The immutable publication records actual Git origin (including dirty paths),
producer selection, exact module/JAR/Ivy paths, adapter report and completed
publication marker. Process success alone is insufficient. Published adapter
output is never shared-Ivy `publishLocal` evidence.

Selection creates an immutable edge/consumer binding to a completed publication;
it performs no provider build. Consumer compilation writes a fresh execution
input for that request and uses typed CNCF Compile. A verified result requires
both actual successful process termination and the adapter's matching selected
coordinates and Compile classpath report. Status reads retained facts only and
distinguishes published, selected-unverified, verified, failed, admission-rejected,
unverified, unknown and not-found. Reusing an execution request identity with different input
or participant rejects; redelivering the same completed input reuses its result.
An intent without an observed result remains unknown and is never rerun implicitly.

These are native Component records, not skill-maintained JSON lifecycle files.
The JSON under the owner workspace is bounded execution input and actual adapter
output. This minimum is a single-module route; selection sets, generation,
verify/detach/prune, update/rollback, in-use retention, multi-module publication,
transitive breadth, two-lane continuation and full Phase acceptance remain in
the later ordered Steps. The executable specification is
`org.simplemodeling.textus.smworkflow.admission.Phase7ManagedDevelopmentSpec`;
its authored scenarios require runtime execution and independent review before
acceptance. No result in this document claims those checks have run.

## P7-B consumer connection (2026-11-21; unvalidated)

The adopted producer is sbt-cozy `0.1.18-SNAPSHOT`, canonical source commit
`04f521f3002cdc5a2c2e7c1d11cb2971d821eda8`. Its requester adoption records
producer delivery and artifact selection, separately from sm-workflow checks.
The changes described here require native validation and independent review.
They do not complete P7-B or any Phase acceptance checkbox.

A configured edge can add an optional `batch` object. Its `modules` list names
literal SBT project IDs and their ordinary exact `requested` coordinates.
`consumerModules` identifies the selected consumer graph. The optional
`generator` contains `cozyVersion`, `modelerVersion` and `modules`, all ordinary
coordinates drawn from the same declared batch. For example:

```json
{
  "modules": [
    {"project":"cozy","requested":{"organization":"org.simplemodeling","module":"cozy_2.12","version":"0.3.3-SNAPSHOT"}},
    {"project":"modeler","requested":{"organization":"org.simplemodeling","module":"simplemodeler_2.12","version":"1.1.26-SNAPSHOT"}},
    {"project":"cncf","requested":{"organization":"org.goldenport","module":"goldenport-cncf_3","version":"0.5.6-SNAPSHOT"}}
  ],
  "consumerModules": [
    {"organization":"org.goldenport","module":"goldenport-cncf_3","version":"0.5.6-SNAPSHOT"}
  ],
  "generator": {
    "cozyVersion":"0.3.3-SNAPSHOT",
    "modelerVersion":"1.1.26-SNAPSHOT",
    "modules": [
      {"organization":"org.simplemodeling","module":"cozy_2.12","version":"0.3.3-SNAPSHOT"},
      {"organization":"org.simplemodeling","module":"simplemodeler_2.12","version":"1.1.26-SNAPSHOT"}
    ]
  }
}
```

Project and module identities must be unique, the edge's primary `requested`
coordinate must be a consumer member, and both explicit generator roots must
agree with their configured base versions. This configuration grants no new
workspace or execution authority. Native publication emits the existing CNCF
scoped SBT envelope with nested `sbt-cozy.development-artifact.v2` and declared
`producers`. It retains the entire v2 batch completion, v3 artifact inventory,
member controls and actual producer report. A partial batch or mismatched
report cannot create a selectable native publication. The adapter validates
actual files and the complete direct/transitive graph on consumption.

Consumer input has separate runtime `mappings` and `generator.mappings`.
The adopted adapter resolves and launches the generator's Scala 2.12 graph in a
fresh child JVM independently of the consumer's Scala 3 graph. Native verified
status additionally requires actual Compile resolution for every configured
runtime member, successful generation, Scala product inventory, the adapter's
SBT producer resolution and child observations, matching Cozy/Modeler base
versions and actual private root CodeSource paths. Process exit alone cannot
establish this outcome. Full resolver/child reconciliation remains the adopted
adapter's responsibility; sm-workflow does not invent a second resolver.

Two registered consumer edges can share a completed producer publication by
explicitly supplying optional `publicationEdge` in the command input. That
named configured edge must have the same provider, repository binding, primary
coordinate and complete batch configuration. Each consumer keeps its own
selection and execution records. No implicit cross-edge/latest discovery occurs.

`verifyDevelopmentArtifact` uses the same retained Compile attempt as
`compileDevelopmentConsumer`. Update selects a new immutable selection identity
pointing to the completed B publication; rollback explicitly selects the exact
retained A publication. Each new execution gets a fresh input and typed SBT
invocation. Existing input files and chosen dependency sets are never rewritten.
`detachDevelopmentArtifact` requires the existing immutable selection, invokes
ordinary typed Compile without scoped input, and retains the prior selection
and actual process outcome. Status can report its retained `detached` outcome.
Detach does not delete artifacts, erase selection history or certify a change to
an already-running SBT/Cozy JVM. Generation/classpath changes take effect through
these new invocations; in-process mutation is not supported.

`selectDevelopmentArtifact` also maintains one current active selection per
configured consumer edge. Optional `retain: true` explicitly pins that immutable
selection; `retain: false` removes its pin. Omission does not create or remove a
pin. To release a historical pin, explicitly select it with `retain: false`, then
detach it or select another publication. Successful detach clears only its
expected active selection, preserving a newer concurrent switch and all pins.
Historical publication/selection facts remain immutable.

`pruneDevelopmentArtifact` uses the adopted CNCF `UnitOfWorkOp.FileDelete` on
literal owner-workspace filenames. Only newly admitted native publications have
a safe prune inventory. Coordinates, standard JAR/classifier/extension filenames,
Ivy descriptors and v1/v2/v3 completion markers are derived from the configured
logical repository. Retained absolute paths and actual completion metadata must
agree with that derivation; they cannot choose deletion targets. Source checkouts,
empty directories, persistent coordinate/batch claims, staging and shared caches
are not deletion candidates. No coordinate can be republished after pruning.

Owner-scoped Component records retain a contiguous immutable protection history.
A transition creates the next unique revision and requires exact readback.
Current lookup probes the contiguous sequence exponentially and then by binary
search, with logarithmic reads and constant stack usage. After a failed Create,
only a different valid immutable successor at that exact revision permits a
bounded no-effect transition retry; missing or equal-byte readback stays
ambiguous and fails closed. No compile/delete effect is retried. This
coordinates current selection, pins, native publication admission, in-flight use
and prune reservation across participating processes. It does not hold a lock
while SBT runs or serialize independent builds. Only exhaustion of the positive
Long revision range prevents a further append; existing status remains readable.

Producer and consumer use is acquired before SBT. An actual observed exit or
proven unstarted execution releases it; unconfirmed termination/interruption
retains it across restart. Active/pinned/in-use publications protect their
development dependency closure. An admitted dependent publication also prevents
deleting its dependency before that dependent is pruned. Unknown, foreign or
dangling closures refuse cleanup. Legacy publications and selections remain
readable/usable, but do not acquire destructive eligibility; an active or running
legacy selection with unknown closure conservatively prevents cleanup.

Prune checks the current protections and exact live inventory before reserving
the publication as unavailable. New selection/use then refuses it. Before every
FileDelete occurrence it durably records a pending outcome, then retains actual
`Deleted`, `Absent`, `Conflict` or `Failed`. Uncertain acquisition/termination is
retained as `Unknown`. Conflict/failure stops the remaining files. Interruption,
partial completion and unsuccessful progress retention remain unavailable across
restart; no successful or unknown delete is replayed. A repeated request returns
its retained result. A different request cannot restart that publication's prune.
Status exposes the bounded inventory, actual progress and current active/pinned/
in-flight protection. `pruned` requires all listed FileDelete results to be
Deleted/Absent; `prune-unavailable`, `prune-incomplete` and `unknown` certify no
such completion. CNCF's cooperative path/identity checks remain its documented
boundary, not an OS transaction against arbitrary external writers.

The authored `Phase7ManagedDevelopmentSpec` covers typed lifecycle replay,
explicit publication sharing, incomplete/conflicting batch refusal and a native
private batch -> real Cozy/Modeler generation -> real CNCF consumer route with
A/B/A, a second consumer, ordinary detach, real owned batch pruning and shared Ivy
observations. The native fixture also withholds a third member's declared POM
after earlier members stage, and supplies conflicting Cozy-to-Modeler dependency
metadata; both must fail without selectable completion or ordinary fallback.
The generated-API negative uses the same Logical UI CML and actual
selected generator output as the positive arm. A separate fixture compiler,
without the root's products or selected runtime, resolves pinned CNCF 0.5.2 and
must report the generated filename and missing GeneratedLogicalUiAbi symbol.
The unchanged build's explicit selection alone activates this old-runtime arm;
the normal compatible arm retains actual selected classpath evidence. It uses no
handwritten generated Scala or machine-specific cache pathname. Actual fixture
execution, integrated transitive/conflict/partial-batch qualification, real
cross-process qualification, running-process refresh qualification and independent
review remain separate runtime/Phase acceptance obligations. No test execution or
acceptance is claimed here.

Batch consumer reports are Workflow artifacts. Their complete identity bytes are
read through the owner workspace's File UoW with the existing
`workflow.artifact.max-bytes` project budget (64 MiB by default), decoded strictly
as UTF-8 and parsed with duplicate JSON keys rejected. The complete report passes
the same selected-coordinate and generator-origin validation before any evidence
is reduced. An unavailable, oversized or invalid report remains unverified.
Single-publication consumer behavior retains its existing report contract.

For batch results, persisted verification/status evidence retains the actual
consumer resolution, generator observations (including code origins, versions and
classpath), generated product inventory and each producer's run reference and
report-row count. It does not repeat the large raw producer resolution rows.
`resolution.rawReport` binds the unchanged full report by logical `workspace`,
`path`, exact `byteCount` and `sha256`; this reference grants no new file access.
Detailed producer resolution remains in that original report. This is an authored
repair; fresh runtime validation and independent acceptance remain required.

## P7-D supported use and qualification limits — 2026-11-21

Use the normal `ManagedDevelopmentService` operation and its explicit configured
edge/publication/selection. The existing owner records and actual result are the
source of status; the operation name or successful producer exit is insufficient.

| Operation | Supported interpretation |
| --- | --- |
| `publishDevelopmentArtifact` | Provider-owned scoped publication; only complete admitted outputs become selectable. It does not install a shared CLI. |
| `selectDevelopmentArtifact` | Select an exact immutable publication for this consumer; `selected-unverified` still requires affected consumer checks. |
| `readDevelopmentArtifactStatus` | Read retained publication, selection, protection and actual execution/prune outcomes; preserve failed/unknown/unverified distinctions. |
| `verifyDevelopmentArtifact` | Execute the selected consumer Compile/generation route and retain actual resolution; publication alone proves no compatibility. |
| `detachDevelopmentArtifact` | Invoke the ordinary consumer route with no selected overlay, retaining history and actual outcome. |
| `pruneDevelopmentArtifact` | Delete only admitted unprotected owner inventory through FileDelete; unknown/partial/conflicting effects remain unavailable and are not replayed. |

For update and rollback, select distinct immutable identities referring to the
new or retained publication and invoke verification again. Each authored native
execution starts a fresh typed SBT process and, for batch generation, a fresh
generator child JVM. This supplies process replacement for those invocations.
The adapter does not mutate or reload an SBT shell/Cozy JVM that was already
running independently. No actual supported stop/restart/reattach observation for
such a persistent process is supplied by the current P7-B/P7-D evidence.
Consequently the original running-process part of P7-DAO-A05 remains a Phase
qualification gap; new one-shot processes alone do not establish it.

The original 44 Phase conditions remain unchanged. Accepted Step reviews and
checks apply to their named source/artifact subjects. They establish no whole
Phase acceptance, cross-process qualification or CNCF Phase101 closure. Ordinary
no-input resolution, explicit one-time build opt-in, exact selected-coordinate
reports, unchanged shared Ivy artifacts and incompatible generated API diagnostics
must retain their own actual evidence on the integrated candidate.

P7-D's subsequent authored consumer-input repair reads the exact existing
immutable consumer invocation rather than interpreting active/pinned protection
as a selection. A known changed envelope reaches native impact-selected
validation with the same scoped SBT input, including when the consumer tree is
unchanged. The [current contract](repository-sync-local-main-integration.md#p7-d-consumer-input-continuation-repair--2026-11-21)
describes the one-envelope scope, unsupported localProject case, native child
job lifetime and adopted scoped-validation/worker-capacity dependency. The
consumer requires exact build-Manage, owner-ReadOnly and operation-specific
permissions. Confirmed child Jobs are joined and available receipts collected
even after another attempt becomes uncertain. Actual configured-host overlap
and scoped consumer checks remain runtime-owned qualification; these source
changes do not promote earlier evidence to whole-Phase acceptance.
