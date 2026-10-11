# Phase 7: Multi-Repository Synchronization and Development Artifact Overlay

Status: planned
Planned: 2026-10-04
Depends on: Phase 1, Phase 5, Phase 6

## Execution plan — 2026-10-07

Continue gradual use of the accepted Phase 6 interface. Reuse Phase 2's
single-repository sync/effect path and Phase 5's validation/resources, rather
than build another workflow engine or selector. Use one explicitly configured
driver workspace for overlay and repository tests; project names remain data.
These batches preserve every overlay and workspace acceptance item below.

### A. Normal operation to publication, selection and real compilation

1. Bind the shared project/repository/worktree graph and overlay ownership through
   CNCF Phase 101 resources. Reuse the Phase 5 slice and implement only necessary
   workspace extensions. Resolve dedicated and explicitly registered worktrees;
   no proximity discovery or second path/configuration model.
2. Fix the typed publication/selection input and SBT adapter contract together:
   unique development version format, cross-module/transitive coordinates,
   staging completion and the explicit per-build JVM-property argument. Keep
   existing dependency declarations and build.sbt unchanged on supported,
   enabled sbt-cozy; document plugin setup separately.
3. Implement native publish/select/status and their execution adapter alongside
   SBT publication/resolution. Prove normal sm-workflow invocation -> staged
   JAR/Ivy publication -> selected set -> actual consumer compilation -> result.
   Start with one producer/consumer, then extend the same spec to two consumers
   selecting different versions. Do not leave native operation wiring until
   after every SBT/Cozy helper is complete.

Existing tracking: P7-DAO-01..04 plus the publication/selection parts of 06/07;
acceptance A01/A04/A07/A08/A12 is developed with this connection. Test incomplete
publication and missing/conflicting inputs before allowing a consumer selection.
This milestone is not acceptance of all six operations or all driver scenarios.

### B. Generation, selection changes and the remaining operations

Connect Cozy generation and generated-output selection records to the same
published artifacts, then implement native verify/detach/prune and complete
status. Update/rollback must refresh or restart retained classpaths and regenerate
affected output; verify generated Scala against the selected runtime API.
Exercise unchanged ordinary Cozy, a new-generator/old-runtime mismatch, corrected
selection, rollback/detach and protected active/pinned dependency closures.
Use the shared two-consumer fixture for these scenarios instead of constructing
a new installation for every operation.

Tracking: P7-DAO-05..07 completion and A02/A03/A05/A06/A09, plus P7-TS03/04.
Observe that target shared Ivy artifacts remain untouched across all six
operations through the actual execution/output paths; do not add hash/copy-based
integrity certificates or cache-wide scans. Selection verification is resolution
and compilation evidence, not an unchanged-source proof.

### C. Workspace synchronization on the same resources and validation runtime

Extend existing RepositorySync to the explicit root plus related branches.
Connect fetch/classification, allowed integration, per-repository outcomes and
dependency-ordered validation before permitted non-force push/convergence.
Reuse CNCF-discovered FULL queries: validate changed dependencies before the
root, omit precautionary FULL for unchanged repositories, and preserve explicit
conflict/failure outcomes. Overlay publication/selection remains a separate
explicit operation; Git sync must not silently publish or switch artifacts.

Tracking: existing nine workspace scenarios, P7-TS01/02/04 and P7-DAO-A11.
Use table-driven Git outcome cases and representative real repository effects.
Independent test scheduling is allowed by product policy; development SBT still
uses the external registered runner/serial wrapper. Neither sm-workflow nor a
test fixture may bypass that harness rule to demonstrate product concurrency.

Batch C depends on the common resource/operation bindings in A, not completion
of every generation/prune detail in B. Both B and C remain required before
closure; C can advance once its actual prerequisites are available. Do not
suspend all workspace progress for unrelated overlay implementation detail.

### D. Integrated acceptance and upstream closure

Complete all six SimpleModeler driver cases, supporting overlay cases, nine
workspace cases and assigned managed-suite coverage using the assembled paths.
Required independent review covers the native operations, adapters, consumer
connections and sync/selection separation together. Reuse applicable producer
checks; repeat affected generation/compilation/tests after selection changes even
when Git revisions match. No blanket full test follows an unchanged sync.

Retain the existing order: Phase 7 driver acceptance -> CNCF Phase 101 full
acceptance/closure -> Phase 7 closure. Identify the remaining upstream closure
items during batch A and handle relevant gaps with these connections, so they
are not discovered only at the end. Do not claim full producer closure from the
Phase 5 minimum slice or weaken this gate through evidence reuse. Preserve
independent upstream acceptance obligations; batching is not a review exemption.
Document supported operations/worktree setup and continue use; optional broader
transports, providers and measurements stay in Phase 8.

## Use during development (2026-10-05)

Follow the revised [incremental-use plan](README.md): Phase 5 supplies the
runtime foundation without skills; Phase 6 delivers thinking modes and skill
integration. Gradual use begins after Phase 6 connected acceptance and continues
through Phase 7. Exercise this phase against real,
explicitly configured project repositories and dedicated dependency worktrees;
use the resulting needs to prioritize bounded additions.

Use the accepted Phase 6 skill-facing contract for this integration. Extend it
explicitly when the synchronization scenario requires it and validate affected
skill consumers together; do not introduce a parallel invocation/result protocol.

Keep the planned synchronization/validation scenario as this phase's delivery
boundary. Missing functionality that prevents supported use is addressed early;
nonblocking additions belong here only when directly related and bounded.
Record other discoveries and inherited Phase 2 extensions in
[Phase 8](phase-8.md), retaining their origin, use case, implementation state
and existing evidence. Do not expand Phase 7 into every possible repository,
provider or recovery scenario. Its existing CNCF Phase 101 driver/closure
responsibility remains part of the planned work; the broader Phase 8 backlog
is not an additional Phase 7 completion gate.

Implement the connected synchronization route and its Executable Specifications
as a coherent batch, using compile/focused checks during development and
integrated validation plus independent review at the behavior boundary. Review
fixes require affected checks and required regressions, not repeated unrelated
reviews for every internal component. Once the planned scenario is accepted,
continue use and close the remaining necessary work in Phase 8.

## Goal

Extend the Phase 1 `RepositorySyncWorkflow` from synchronization of one project repository into dependency-aware synchronization and validation for a development project and the related repositories whose project-specific branches it uses.

For a root project X, synchronization MUST include X itself and the related repositories explicitly participating in X through project-specific branches. When remote changes are incorporated into one of those related repositories, sm-workflow validates the changed dependency first and validates X only after all changed dependencies have passed their full tests.

This is an extension of the existing `RepositorySyncWorkflow`, not a second synchronization workflow. It is also the project-workspace convergence counterpart to `sm-goal-phase`: GoalPhase closes work locally in one admitted repository/worktree; RepositorySync brings the root repository and its dedicated related worktrees into synchronized, validated GitHub convergence.

## Development Artifact Overlay (2026-10-06)

Deliver [Development Artifact Overlay](../spec/development-artifact-overlay.md)
as native sm-workflow functionality for the same explicitly configured project
workspace. An owning worktree has a logical development-artifact repository;
each consumer explicitly selects an immutable set of unique development
coordinates. Record real producer origin and build dependencies. Do not overwrite
published coordinates, silently select the latest version, mix foreign overlays
or fall back to ordinary versions when a selected artifact is unavailable.

sm-workflow owns typed publication/selection/verification state and registered
Operations. CNCF provides standard Component configuration, persistence and
logical resource locations. SBT integration handles staged JAR/Ivy publication
and direct/transitive dependency selection; Cozy handles generator/runtime
selection, generated-output records and classpath refresh. Skills use the Phase 6
interface to call these Operations; they do not implement the state machine or
own a parallel `.codex-workflow` artifact ledger. The original skill-oriented
layout/command-envelope proposal is superseded by this native product design.

For each build, sm-workflow supplies the selected artifact input explicitly as
a JVM system-property argument. The same checked-in build.sbt serves ordinary
and overlay builds without per-selection edits. Put input handling and dependency
application in reusable common SBT integration, with any one-time plugin bootstrap
documented separately. Do not require project-specific parsing, persistent shell
environment selection or source-version rewriting to switch dependencies.

The existing build definition keeps its ordinary policy and dependency-coordinate
declarations. With a supported sbt-cozy installed and enabled, activate overlay
handling solely through the explicit selection argument; do not require
`.settings(cozyDevelopmentArtifactOverlaySettings)` or any other overlay-specific
build.sbt declaration. No argument retains ordinary behavior; an invalid explicit
selection fails. Plugin installation/enabling or version updates are separate
prerequisites, not per-build edits. Verify an existing sbt-cozy-enabled build.sbt
works without modification and without per-dependency wrappers. Actual resolved
JAR paths change with the selected unique development coordinates.

Define shared project composition and worktree operation under
`${project}/.textus/sm-workflow/resources/`: repository/dependency membership,
development branches, dedicated-versus-existing worktree policy, producer/consumer
roles and overlay owner. CNCF local configuration supplies machine-specific
existing-worktree paths. The provider places managed checkouts under `work.d/`
and development JARs/Ivy metadata under
`work.d/artifacts/<overlay-id>/repository/`, outside Git. sm-workflow resolves
these logical resources and passes explicit selection input to sbt-cozy; neither
component infers membership from directory proximity or selects the newest JAR.
Concrete definition filenames and helper APIs remain adapter design work.

Provide publish/select/status/verify/detach/prune. Preserve ordinary dependency
resolution and global caches, and leave SBT/Ivy/Coursier cache exclusion to those
tools. Do not introduce an SBT-wide or machine-wide mutex. Coordinate only real
resource conflicts such as the same publication target or selection/prune race.
The Codex development harness's existing serial wrapper remains an external
execution constraint and is not part of the product's public workflow contract.

Implement SBT publication/resolution with native sm-workflow operation/execution
adapters and normal invocation from the first batch, then extend that connected
path through Cozy selection/regeneration and the remaining operations. Keep the scope to the
[overlay checklist](phase-7-artifact-overlay-checklist.md), including the six
SimpleModeler driver cases: two independent consumer versions, unaffected
ordinary Cozy, generated-code/runtime API mismatch detection, no fallback on
failure, correct generated output/classpaths after update/rollback, and no
changes to shared `~/.ivy2/local` target artifacts across all six operations.
Use actual typed selection/resolution facts and compiler/tests, not management
hashes, full-content copies or tamper/unchanged-state certificates.

Overlay operations are distinct from Git synchronization and do not implicitly
publish, select, push or prune merely because a repository was synchronized.
Phase 7's Git full-test policy below remains unchanged. An explicit dependency
selection change additionally requires affected regeneration, compilation and
relevant tests even with unchanged Git revisions; unchanged Git alone does not
prove an unchanged dependency environment. Publication/set creation alone is not
consumer compatibility acceptance. Phase 8 may take optional later breadth,
not the initial overlay delivery or its six required acceptance cases.

## GoalPhase / RepositorySync responsibility split

`sm-goal-phase` owns repository/worktree-local development closing:

```text
edit -> test/review -> admission -> CommitChanges -> local commit -> Step/Phase close
```

`sm-repository-sync` owns Project Workspace synchronization/convergence:

```text
root + dedicated related worktrees
  -> fetch/classify
  -> fast-forward or non-rewriting merge as required
  -> dependency-aware validation/full tests
  -> permitted non-force push
  -> convergence verification
```

RepositorySync MUST consume already-created local commits from GoalPhase without reimplementing GoalPhase closing. Conversely, GoalPhase MUST NOT grow multi-repository fetch/push/convergence semantics merely because its current worktree belongs to a larger Project Workspace.

## Example

Project X uses project-specific branches of CNCF and Cozy.

If synchronization incorporates a remote change into CNCF only:

```text
sync Project X / CNCF / Cozy
  -> CNCF: remote change incorporated
  -> Cozy: unchanged
  -> full test CNCF
  -> full test Project X
```

If both CNCF and Cozy incorporate remote changes:

```text
sync Project X / CNCF / Cozy
  -> CNCF: remote change incorporated
  -> Cozy: remote change incorporated
  -> full test CNCF --+
                       +-> when both succeed -> full test Project X
  -> full test Cozy --+
```

Independent dependency tests SHOULD execute concurrently. Phase 5 execution policy applies: sm-workflow MUST NOT serialize sbt merely because multiple tests use sbt.

## CNCF Component resource integration

Phase 7 is the primary sm-workflow driver scenario for CNCF Phase 101. Dedicated/project-specific checkouts or worktrees MUST be acquired through the CNCF logical Component workspace/resource API. RepositorySync logic MUST NOT construct `$PROJECT/.textus/sm-workflow/...` paths itself.

The standard local mapping places sm-workflow definitions at the Component-area root, version-controlled assets under `resources/`, and runtime state/worktrees under ignored `work.d/`. The project generator supplies the generic `.textus/*/work.d/` ignore rule; RepositorySync MUST NOT edit `.gitignore`. The local provider may map project resources under the project-local Textus component area, while the logical contract keeps that physical layout outside Workflow semantics. Project-local runtime state/DataStore and worktrees share the same Component resource ownership model, but configuration continues to use CNCF's existing hierarchical configuration resolver.

## Repository set

The synchronization set consists of:

1. the root development project; and
2. related repositories explicitly used through project-specific/dedicated branches for that root project.

RepositorySync MUST NOT recursively synchronize every transitive library dependency merely because it exists in the build dependency graph.

The source of the project-specific repository/branch relationship MUST be deterministic project configuration or equivalent explicit project metadata. Discovery by repository-name guessing is not allowed.

## Synchronization result

RepositorySync records a typed result for every repository sufficient to distinguish at least:

- UNCHANGED — no remote change was incorporated;
- UPDATED — remote change was incorporated into the local project branch;
- CONFLICT — synchronization cannot be completed without resolution;
- FAILED — synchronization itself failed.

The implementation MAY refine UPDATED into fast-forward/merge/rebase-related facts when useful, but validation policy is based on the semantic fact that external changes were incorporated, not on a particular Git command.

A fetch with no incorporated working-branch change is not UPDATED.

## Validation policy

1. Every related repository with result UPDATED MUST receive its full test.
2. Related repositories without incorporated remote changes MUST NOT receive a full test merely as a precaution.
3. Full tests for independent UPDATED related repositories MAY run concurrently.
4. The root project full test MUST wait until every required related-repository full test succeeds.
5. If one or more related-repository full tests fail, the root full test MUST NOT be reported as validation of the synchronized dependency set. The Workflow remains unresolved with explicit failure evidence.
6. If the root project itself is UPDATED, it requires a full test.
7. If any related repository is UPDATED, the root project requires a full test even when the root repository itself was unchanged, because the effective dependency baseline of the root project changed.
8. Therefore, when neither the root nor any related repository incorporates remote changes, synchronization completes without a precautionary full test.

## Managed validation across repositories and overlays (2026-10-06)

Consume Phase 5's managed suites and Phase 6's semantic design/FIX connection,
following [the suite contract](../notes/managed-test-suites-and-verification-cost-guard.md)
and [decision journal](../journal/2026/10/2026-10-06-managed-test-suites-cost-feedback.md).
The policy above determines **when** a repository needs FULL; CNCF Phase 103
metadata discovery determines its FULL operation membership. FULL is not an
unconditional synonym for `sbt test`. Do not introduce a registry, another
annotation parser or AI-selected execution-time class list.

Query each required project's resolved metadata and record actual operation IDs,
metadata/ABI revision, Candidate/input context, reason, outcome and duration.
Missing required coverage is an explicit gap; metadata changes follow ordinary
Candidate/review/admission through Phase 5/6. Dependency validation precedes root
validation; failed dependencies do not permit successful-root acceptance.


The normal FULL target is <=10 minutes, with explicit justified exceptions and
separate optional HEAVY. Cost warnings do not change PASSED to FAILED, reject sync
by themselves, trigger a retry or invoke AI without a human decision. HEAVY is
not automatically added to RepositorySync. Independent tests remain eligible for
concurrent execution; SBT's own cache coordination stays outside Workflow policy.

Overlay selection changes can require validation even when source Git revisions
are unchanged. Capture the actual selection and resolved dependency context in
the invocation/result alongside the candidate and suite revisions. Perform
affected regeneration/compilation through the existing typed operations and run
the metadata-derived purpose/feature query required by the admitted policy.
Choose that profile before executing the change; do not improvise a broader test
set after a failure. Compile generated code to detect generator/runtime API
mismatch. Publication success alone is not compatibility evidence. Revision and
selection references are contextual facts, not hashes or unchanged-state proofs.

The [managed-suite Phase 7 acceptance](managed-test-suites-phase-checklist.md)
extends the existing Git/overlay cases: per-project FULL metadata selection and ordering,
unchanged repositories avoiding precautionary runs, PASS with cost warnings,
explicit HEAVY policy and overlay-context-aware verification using CNCF metadata
queries. No additional test-selection engine or integrity ledger is introduced.
Any resulting TEST_FIX/REVIEW_FIX uses the common Phase 5/6 convergence policy:
ordinary repair count as a convergence parameter, hygiene/minor ordinary-count
exclusions with retained convergence effects, bounded AI feedback, and a separate
absolute hard limit of 10 automatic Fix cycles including hygiene/minor work. Do not create repository-sync/overlay-specific lifetime
repair totals, reset active loops on a worktree/chat switch or add new measurement
machinery merely because several repositories participate.
Issue source identities include repository/provider scope so same-named failures
remain distinct. Use the shared ledger/reconciliation and evidence-confirmed
resolution rules, with no per-repository duplicate workflow. Resume reuses
completed applicable evidence; changed overlay selection/resolved dependencies
can invalidate it even when source revision matches. Verify both unchanged-context
reuse and changed-context revalidation while preserving issues and both counts.

## Dependency ordering

Validation follows the explicit project dependency relation, not repository enumeration order.

For the Phase 7 minimum scope, project X is the root and its dedicated-branch repositories are dependency nodes. If dependencies between those related repositories are explicitly known, their tests MUST respect that ordering. Otherwise independent nodes may be tested concurrently.

The root project is the final validation node.

## Operations

Retain the Phase 1 application boundary:

- `StartRepositorySync(RepositorySyncStartInput, WorkflowInvocationSelection)`
- `SubmitRepositorySyncWorkResult(RepositorySyncWorkResult)`

Extend their typed payloads/results as necessary rather than introducing a parallel ProjectSync protocol.

The Workflow should materialize bounded work orders for Git synchronization and full-test execution and receive typed evidence through the existing continuation/completion boundary.

## Evidence

For each repository preserve enough evidence to establish:

- repository identity;
- local/project-specific branch identity;
- synchronization result;
- whether a remote change was incorporated;
- before/after revision identity as normal Git revision evidence;
- whether full test was required;
- full-test execution identity and result when required.

Revision identifiers are observational Git evidence. Phase 7 MUST NOT introduce custom content hashing or integrity machinery.

## Conflict and failure behavior

A Git conflict is a normal explicit Workflow outcome requiring resolution. Phase 7 MUST NOT add speculative rollback, repository backup, duplicate working trees, integrity verification, or contamination-prevention machinery merely because synchronization may fail.

After conflict resolution, validation requirements are derived again from the actual incorporated changes.

## Executable Specification requirements

Demonstrate at least:

1. root and related repositories unchanged -> no full test;
2. root only UPDATED -> root full test;
3. CNCF-like dependency only UPDATED -> dependency full test, then root full test;
4. two independent dependencies UPDATED -> their full tests may overlap, then root full test after both succeed;
5. dependency full test failure -> root validation does not proceed as if the synchronized set were valid;
6. root unchanged but dependency UPDATED -> root still receives full test;
7. fetch without incorporation -> no UPDATE-triggered full test;
8. synchronization conflict -> explicit unresolved result, not automatic defensive recovery;
9. two sbt-based dependency full tests are not globally serialized by sm-workflow solely because they use sbt.

## CNCF Phase 101 closure gate

Phase 7 is the final driver acceptance for CNCF Phase 101. After the RepositorySync/worktree acceptance scenario succeeds, Phase 7 MUST cause/record CNCF Phase 101 full acceptance and closure before Phase 7 itself closes. A Phase 5 minimum-slice acceptance of Phase 101 is not sufficient for this gate.

The closure order is:

```text
sm-workflow Phase 7 driver acceptance
  -> CNCF Phase 101 full acceptance / close
  -> sm-workflow Phase 7 close
```

## Dogfooding / reference scenario

The development of sm-workflow itself is a normative driver scenario: sm-workflow uses a dedicated CNCF worktree to develop CNCF Phase 101 while the root sm-workflow project proceeds through its own phases. GoalPhase may locally close work in either admitted worktree; RepositorySync must later treat the root and dedicated CNCF (and, where present, Cozy) worktrees as one explicit Project Workspace for synchronization, dependency-aware validation, permitted push, and convergence evidence.

This scenario must be supported through the same public project/workspace model intended for later `sm-repository-sync` use, not by a one-off development script or hard-coded repository names.

## Practical completion condition

Both the existing multi-repository synchronization scenario below and the
Development Artifact Overlay checklist must be accepted for Phase 7 completion.
The Phase 7 [managed-suite integration items](managed-test-suites-phase-checklist.md)
are also part of those connected scenarios, not a separate generic framework.
Record actual integrated validation and independent review for each delivered
behavior; tracking rows are not separate helper-level review gates.

Phase 7 is complete when a real root project using at least two project-specific related repository branches can execute one RepositorySyncWorkflow that:

- synchronizes the complete explicit repository set;
- identifies exactly which repositories incorporated remote changes;
- runs full tests only where required by the validation policy;
- runs independent dependency tests concurrently where possible;
- waits for successful dependency validation before root validation; and
- returns typed synchronization and validation evidence for the complete operation.

## Non-goals

- Synchronizing every transitive build dependency.
- Global sbt/Ivy/Coursier locking.
- Full-testing unchanged dependency repositories as a precaution.
- Automatic semantic conflict resolution.
- Defensive repository backup/rollback/integrity machinery.
- Replacing Git's own merge/conflict semantics.
- General provider-selection semantics; generic execution-requirement semantics are established in Phase 6.


## Static-analysis consumer coordination

textus-cbd-support on Mac mini uses the same CNCF Phase 103 operation identities,
metadata revisions and bounded runtime evidence references for architecture
coverage/delta and declared-versus-observed duration review. Phase 7 must keep
repository and overlay context attributable in those references. It does not
implement cbd-support analysis or wait for its UI/KPI completion to validate
runtime queries. Cross-project planning documents are updated together.


Slice validation consumes the shared management-file contract: metadata query
plus explicit supplemental operation references/typed parameters tied to acceptance
conditions. Resolve them in the selected repository/overlay context and record
plan revision, selection origin and actual invocation inputs in evidence. A plan
supplement neither changes source metadata nor replaces required project FULL.
