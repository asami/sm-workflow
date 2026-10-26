# Phase 7: Multi-Repository Synchronization and Development Artifact Overlay

## Dependency development delivery priority — 2026-10-26

Adopt [three dependency modes and delivery](../spec/dependency-development-delivery.md):
Fixed, assigned development chat with runtime-owned FIFO, and managed development
subtree. Track implementation in the [P7-DD checklist](phase-7-dependency-delivery-checklist.md).
The English [handoff](../journal/2026/10/2026-10-26-phase-7-dependency-development-modes-handoff.md)
records the user direction and preservation rules.

Assigned-chat work completes through local commit and successful publishLocal.
Small defects use sm-fix with explicit commit/publication delivery; larger ad hoc
development uses sm-goal-task; Phase development uses sm-goal-phase. Larger work
then attempts best-effort sm-repository-sync, with development/local publication
and synchronization reported separately. A failed/deferred sync does not revoke
local delivery or consumer usability.

Phase 6.7 supplies single-request Task/FIX execution and initial chat handoff.
Phase 7 owns mode selection, durable FIFO, delivery policy, consumer adoption,
subtree/workspace selection and synchronization. Do not add these queue/three-mode
requirements to ongoing 6.6/6.7 acceptance. Existing GoalPhase execution is reused.
Phase 17 retains optional Task/FIX breadth and is not an initial dependency.

### Revised main-route order

This order takes precedence over the older overlay-first sequence below.
Retain original A/B/C/D and P7-DAO identities, existing evidence and remaining
acceptance; the new ordinary publishLocal route is not overlay acceptance.

| Order | Work | Connected evidence |
| --- | --- | --- |
| 1 | Reconcile accepted 6.7 execution and existing authorization/publication/result contracts; configure fixed/chat/subtree mode and one producer/consumer | Fixed-mode refusal is truthful; actual contract gaps are bounded |
| 2 | Assigned-chat small defect -> existing fix -> local commit -> publishLocal -> result return -> consumer integration | P7-DD-04/07/08 main route with actual installed execution |
| 3 | Runtime FIFO for two requests; Task/Phase deliveries and post-publication best-effort sync using existing C operations | Ordering, retained results and nonblocking failed/deferred sync; no AI queue controller |
| 4 | Managed subtree development and sync target registration; reuse A/B resource/artifact selection and C dependency-order synchronization | Explicit origin/destination and isolated consumer selection; no general worktree use before qualification |
| 5 | Complete remaining B/C, all P7-DD/DAO and original workspace scenarios, then D integrated review/upstream closure | Preserve existing validation; no duplicate whole-route qualification for unchanged behavior |

Ordinary cross-chat publishLocal must work without completing all overlay
operations first. Required generation is included only when the selected consumer
needs it. Subtree denotes a logical managed area; the concrete Git/source layout
uses the existing CNCF resources rather than assuming a Git subtree command.
Planning changes no active Session, original base, accepted result, repair count
or validation history. Reconcile pending Human records only through public
admission; necessary same-purpose file additions do not create caller approval gates.

Authorization reuse (2026-10-25): related-project delivery consumes the
[purpose-based authorization correction](purpose-based-authorization-correction.md).
Additional necessary files within the admitted Component/purpose/acceptance/operations
are scope updates judged by sm-workflow, not automatic Human questions. Preserve
real Component and operation boundaries. Reuse the producer implementation and
evidence; do not create another consent owner or repeat accepted Phase 6 work.


Evidence ownership update (2026-10-20): consume [Phase 6.4](phase-6.4.md)'s
accepted operational use of [Phase 12](phase-12.md)'s common Evidence service.
Use Evidence/Journal IDs and automatic result recording; earlier references to
AI recording or exact destinations do not require Markdown edits or public
absolute paths. Shared storage remains one service. Existing work IDs, overlay
scope and evidence remain unchanged.

Production-first revision (2026-10-18): references below to Phase 6 delivery of
CBD-backed P6-SQ01..05 are superseded by [Phase 10](phase-10.md). Consume the
accepted ordinary review/Admission route for this Phase; do not wait for Phase
10 or claim deferred CBD coverage. Reuse CBD integration when actually accepted
and explicitly selected. Other owned delivery/validation obligations remain.


Status: planned
Planned: 2026-10-04
Depends on: Phase 1, Phase 5, Phase 6.3, Phase 6.4, Phase 6.5

## Workflow-optimization predecessor — 2026-10-20

[Phase 6.5](phase-6.5.md) now follows 6.4 and precedes this Phase. Consume its
accepted conditional Action policy, ordinary reviewless Step admission and
Step/Release FIX alongside 6.4 evidence storage. PLAN selects semantic-review
Steps when needed; Release retains integrated business/meaning review. CBD
remains OFF pending Phase 10; observed convergence extensions remain in Phase 9.
The existing A -> minimum C -> remaining B/C -> D implementation order, work IDs
and acceptance remain unchanged. Earlier ordinary Step-review references describe
the producer history, not a new mandatory per-Step review after 6.5 adoption.
See the [handoff](../journal/2026/10/2026-10-20-phase-65-workflow-optimization-handoff.md).

## Evidence-storage predecessor — 2026-10-13

[Phase 6.4](phase-6.4.md) now follows 6.3 and precedes this Phase. Consume its
accepted local evidence allocation/archive/lookup and AI-recording interface
for released workspace execution evidence. Work files and logs/results/media
remain outside Git; retained evidence goes under the configured host's
`~/backup/journal/YYYY/MM/` root. Canonical source, decisions, accepted product
inputs and Phase state retain their roles. Storage location records do not
replace test/acceptance evidence, and archive failure is not product-test failure.

Use the facility before freezing selected synchronization paths; keep live
dependencies at their bound locations. Do not reimplement archival, silently
clean already tracked evidence or rewrite Git history. Artifact overlays remain
this Phase's separate development-artifact responsibility. The Phase 6–6.3
aggregate validation chain remains closed at its original final owner.

## Phase 6 split dependency correction — 2026-10-13

The earlier broad Phase 6 references in this document denote the accepted
[6 -> 6.1 -> 6.2 -> 6.3 family](phase-6.md#applied-split--2026-10-13), not
completion of the narrowed first member alone. Consume 6's client/modes/session/
provider contract, 6.1's execution/plan/Step-review contract, 6.2's FIX/reset/recording/
dependency contract and 6.3's integrated PHASE/FINAL/use evidence. Initial staged
review is owned by 6.1 and 6.3; it is not deferred to Phase 9. This Phase's identity,
scope and original historical decisions remain; no successor is started here.

## Execution plan — 2026-10-07

Continue gradual use of the accepted Phase 6 interface. Reuse Phase 2's
single-repository sync/effect path and Phase 5's validation/resources, rather
than build another workflow engine or selector. Use one explicitly configured
driver workspace for overlay and repository tests; project names remain data.
These batches preserve every overlay and workspace acceptance item below.

### Cost-first execution order — 2026-10-18

Keep the accepted 6.3 skills in production use while extending them; consume
6.4 evidence storage under the existing predecessor contract. The fastest useful
extension is one related producer/consumer development route, not completion of
every overlay operation before any workspace synchronization.

For the overlay branch after the ordinary assigned-chat delivery above, execute
**A's common bindings and one-producer/consumer main route -> C's minimum
connected workspace route -> remaining B and C cases -> D closure**. Batch letters
and work IDs retain their identities; this ordering creates no new tracking or
review cycles. Required generation from B is brought into the first route only
when that selected consumer actually needs it, not as a prerequisite to all C work.

| Order | Reuse and missing implementation | Exit evidence |
| --- | --- | --- |
| 1 | Reconcile 6.3 local GoalPhase/RepositorySync, 6.4 archive and existing CNCF resources/SBT/Cozy behavior. Bind one explicit producer/consumer workspace and identify remaining CNCF101 closure duties early. | Actual required contract gaps and evidence references in the current plan; no fresh workspace or helper layer if the existing one suffices. |
| 2 | A: connect local development/commit to explicit publish/select/status and actual consumer compilation. Bring required generation wiring forward from B for this driver. | One selected development artifact reaches the consumer through normal operations, with truthful failure outcomes and necessary focused validation. |
| 3 | C: extend the accepted single-repository sync to this root plus related producer, reusing typed Git, validation and admission. | Actual dependency-ordered validation and permitted non-force convergence on the same workspace. Artifact publication/selection remains explicit; Git sync cannot switch it. |
| 4 | Complete A's second-consumer isolation and all remaining B/C requirements using the same driver: generation mismatch/correction, update/rollback/detach, verify/prune protections, workspace conflict/failure branches and plan refresh. | Applicable existing evidence plus bounded changed-path tests cover every retained checklist obligation; no new installation per branch. |
| 5 | D: assess integrated behavior, finish missing required regressions, independent review and CNCF101 acceptance/closure, then Phase 7 closure and supported-use guidance. | Full practical GoalPhase adoption with related-project management, preserving local-development/artifact/sync ownership. |

Keep necessary failure checks with the operation they protect: incomplete
publication must never become selectable, and unvalidated/unauthorized changes
must never be pushed. Later branch completion is not permission to defer these
conditions. Validate the main route as soon as it connects. Do not rerun unchanged
6.3 workflows or producer internals merely to populate Phase 7 evidence; selection
changes do require affected generation/compilation/validation even when source
revisions match. Reuse accepted integrated evidence during D rather than repeat
every successful scenario. All six SimpleModeler driver cases, nine workspace
cases, existing checklist rows and CNCF101 closure remain required.

Early A/C success does not declare Phase 7 complete or restore general worktree
use before the applicable acceptance. Continue the already accepted 6.3 scope;
record Phase 7 driver execution as bounded development until its full adoption
boundary passes. No Phase 8/9/10 wait or new cost-measurement framework is added.
See the [execution-order handoff](../journal/2026/10/2026-10-18-production-cost-first-execution-order.md).

### Shared execution and plan ownership — 2026-10-12

Reuse the accepted ordinary review/Admission route from 6.1/6.3. Extend review
scope to actual workspace dependencies and consumer changes without another
review engine or repeated whole-Phase semantic review at Final. CBD P6-SQ01..05
and producer handoffs belong to Phase 10; consume them only when accepted and
selected, without making them a prerequisite for ordinary Phase 7 work.

Consume Phase 6's runtime-owned command lifecycle and live Phase-plan
synchronization. SBT/Git, generation, publication/selection effects and consumer
validation run through sm-workflow execution adapters from preparation to result
handling/cleanup, not AI command runners. AI handles semantic conflict resolution,
model/code edits and proposals; actual effects retain their existing permission
scope. No separate orchestration engine, AI log forwarding or new SBT mutex.

Refresh the relevant Phase documents before selecting the next workspace work,
retain per-repository/consumer execution facts under stable item IDs, and project
progress into the latest owned fields without replacing plan prose. Changed
dependency/acceptance requirements receive affected-scope handling under the
Phase 6 contract; document changes alone do not invalidate all workspace evidence.
Exercise this ownership through batches A/C and their integrated acceptance D:
include a plan edit between workspace steps and preservation of prior results.
No separate full workspace campaign is added for document synchronization.

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
   Start with one producer/consumer and connect C's workspace route before the
   remaining breadth. Then extend the same spec to two consumers selecting
   different versions. Do not leave native operation wiring until
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
integration. Gradual use begins after Phase 6.3 integrated acceptance and continues
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

The installed single-repository `sm-repository-sync`, including actual permitted
integration/validation/review/non-force push/convergence, must already be usable
at [Phase 6.3 completion](phase-6.3.md#single-repository-production-acceptance--2026-10-18).
Do not defer that basic route here or use Phase 7 as the first production-use
gate. This Phase adds multi-repository/worktree/dependency/overlay scope to the
accepted route, reusing its actual evidence and fixing only affected gaps.

For a root project X, synchronization MUST include X itself and the related repositories explicitly participating in X through project-specific branches. When remote changes are incorporated into one of those related repositories, sm-workflow validates the changed dependency first and validates X only after all changed dependencies have passed their full tests.

This is an extension of the existing `RepositorySyncWorkflow`, not a second synchronization workflow. It is also the project-workspace convergence counterpart to `sm-goal-phase`: GoalPhase closes work locally in one admitted repository/worktree; RepositorySync brings the root repository and its dedicated related worktrees into synchronized, validated GitHub convergence.

### Full practical GoalPhase adoption — 2026-10-18

Phase 6.3 already supplies ordinary `sm-split-phase` and single-repository
`sm-repository-sync`, plus limited single-repository `sm-goal-phase` use.
Phase 7 makes GoalPhase suitable for full practical use across related-project
development by connecting its local work/closure with this Phase's workspace,
worktree, development-artifact and dependency-aware synchronization operations.
This is the combined use of existing owners, not a new monolithic GoalPhase.

Use the existing A/B/C/D producer-consumer scenarios and accepted local GoalPhase
closure evidence to show the handoff: admitted local development/commit -> explicit
artifact publication/selection and affected consumer generation/validation ->
RepositorySync dependency-ordered synchronization and permitted convergence.
Record the actual selected repositories/artifacts and results. Do not repeat the
whole 6.3 campaign, introduce a second review cycle, or make Git sync silently
publish/select artifacts. Normal split/sync use remains available while this
extension is being developed; unsupported related-project use is not claimed
until its existing Phase 7 connected acceptance passes.

Update usage instructions at closure to distinguish the 6.3 limited GoalPhase
scope from this combined related-project workflow. Required validation, effect
authority and independence stay with their existing owners. See the
[development handoff](../journal/2026/10/2026-10-18-three-skill-production-tiers-handoff.md).

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

The assigned-chat delivery policy composes these existing owners: finish local
Task/Phase acceptance and commit, perform publishLocal, return the local result,
then attempt the selected best-effort sync. Reuse an applicable existing
post-completion sync result rather than scheduling it twice. Consumer adoption
does not wait for remote convergence; a live sync still retains its real resource
ownership. Failed/unknown publication is not a delivered dependency.

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
resolution rules, with no per-repository duplicate workflow. Normal continuation
after a requested answer retains applicable completed evidence, issues and both
counts. Changed overlay selection/resolved dependencies require affected
revalidation even when source revision matches. Verify these cases within the
normal execution. This does not promise recovery of an interrupted execution or
uncertain effects. The Phase 5.1 fresh-start boundary carries forward: abandon
an unsuccessful run and start a new execution from the beginning; do not restore
removed replay, history-comparison or intermediate-recovery guarantees.

## Dependency ordering

Validation follows the explicit project dependency relation, not repository enumeration order.

For the Phase 7 minimum scope, project X is the root and its dedicated-branch repositories are dependency nodes. If dependencies between those related repositories are explicitly known, their tests MUST respect that ordering. Otherwise independent nodes may be tested concurrently.

The root project is the final validation node.

## Operations

Consume Phase 5.3's current RepositorySync Start/Advance/Submit contract and
Phase 6's actual client integration. Extend the business payloads where required
for workspace scope; do not restore the historical WorkflowInvocationSelection
caller envelope or introduce a parallel ProjectSync protocol.

Git synchronization and full tests are runtime execution work, with actual
adapter results consumed through the existing continuation/completion boundary.
Only semantic work such as conflict resolution or review is dispatched to AI;
its Submit result is displayed before the next Advance as in the common loop.

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

The [dependency delivery checklist](phase-7-dependency-delivery-checklist.md),
existing multi-repository synchronization scenario below and Development Artifact
Overlay checklist must be accepted for Phase 7 completion.
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
- Defining a new quality/assurance policy for RepositorySync. Phase 7 consumes the Effective Acceptance Boundary established in Phase 6 and MUST NOT enlarge it merely because repository synchronization exposes constructible race, recovery or consistency scenarios.

RepositorySync validation and Review use the same Effective Acceptance Boundary as the owning Goal/Phase. In particular, precautionary full tests, backup/rollback/integrity machinery, duplicate synchronization arbitration or clean recovery from out-of-model operational accidents do not become Phase 7 acceptance requirements unless the boundary explicitly requires them. Phase 9 owns richer boundary-aware convergence/routing behavior.


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

## Dependency property ownership clarification (2026-10-21)

Phase 7's shared workspace/repository graph must represent each development dependency with independent `localRepository`, `localJar`, and `localProject` properties (P7-DEP01). The dependency repository owns building, validating and publishing `localJar`; the consumer only resolves the published artifact. Git repository-sync and catch-up must not trigger producer builds or republish JARs implicitly (P7-DEP02). Preserve explicit publish/select/status and per-consumer artifact selection already planned in A/B. Distinguish Git source revision from published artifact version; use development-scoped artifact repositories to avoid global Ivy pollution (P7-DEP03). Reuse this graph for Phase 13 catch-up rather than introducing another dependency configuration model. These are planned requirements, not completed work.

## Development Workspace dependency-edge clarification (2026-10-21)

The discussion concerns the **development environment definition itself**, not a new Workspace Profile abstraction. The existing Phase 7 Project/Repository/Worktree graph and Development Artifact Overlay remain the single configuration model. Revise P7-DEP01 interpretation: `localRepository`, `localJar`, and `localProject` are independent properties of the **consumer-to-provider dependency edge in a Development Workspace**, not intrinsic/global properties of the provider repository. Different consumers/worktrees may select different arrangements. `localRepository` supplies a registered local development checkout for Git operations; `localProject` is an explicit source-project binding; `localJar` is a consumer reference to a provider-published local artifact. The provider alone owns artifact build/validation/publication/update; consumer selection is explicit and cannot trigger provider publication. Reuse existing Consumer Binding and immutable Artifact Selection rather than adding a parallel profile, selector or state store. Phase 13 traverses only registered `localRepository` dependency edges. These are requirements, not implemented features.

## Optional operation candidates — reference only (2026-10-21)

See [Development Workspace operation candidates](../journal/2026/10/2026-10-21-development-workspace-operation-candidates.md) for possible Status, Impact, Freshness, Diagnostics and related operations. This is non-normative reference material: no additional implementation, acceptance item or dependency is introduced by this link. Evaluate overlap with existing typed Operations before selecting any candidate.

## Natural inclusion of operation candidates (2026-10-21)

During ordinary Phase 7 implementation, include Dependency Status, Artifact Freshness, and basic Dependency Impact when they follow naturally from the existing Development Workspace graph, Artifact Overlay status/verify, publication provenance, and dependency-aware validation. Prefer enriching existing typed operations/results rather than introducing separate operations, duplicate state, approval steps, or a new Phase. No additional approval is needed for such bounded implementation within Phase 7's existing acceptance boundary. Do not interpret the candidate journal as an unconditional new checklist or broaden Phase 7 into a general dependency-management system; materially larger scope requires a separate decision. See [candidate journal](../journal/2026/10/2026-10-21-development-workspace-operation-candidates.md).
