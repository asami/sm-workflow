# Runtime-owned single-repository synchronization

Status: adopted target contract; implementation and installed qualification pending.
Date: 2026-10-27
Delivery: [Phase 6.9](../phase/phase-6.9.md).

## Responsibility and allocation

Both clean and dirty entry improvements belong to Phase 6.9. Phase 6.8 retains
its existing commit completion/recovery and mechanical hygiene scope, including
shared RepositorySync commit integration; it does not acquire these new start,
selection or synchronization requirements. [Phase 6.11](../phase/phase-6.11.md)
composes this route for an explicit directory list under the
[batch contract](repository-sync-directory-list.md), retaining individual public
operations. Phase 7 consumes that route for project-local worktree/subtree and
related-project development rather than rebuilding ordinary batch synchronization.

For a configured repository, the caller supplies the repository selection and
the user's synchronization intent. Runtime resolves the branch/tracking target,
existing authority, Git bindings, session and necessary effects. Missing or
ambiguous business information is requested once, when needed. Routine caller
source inspection, per-operation executable/grant registration, authority JSON
assembly and StartupImport configuration toggling are not the normal interface.
Internal effect records may remain separate; they must not require separate AI
round trips. Existing intent is applied through the public runtime contract,
not fabricated consent or bypass of a genuine permission refusal.

Use CNCF typed Git operations, PATH git by default and explicit configured
overrides. Reuse existing resource, process, authority and persistence owners.
General first-time environment installation remains with its existing plan.

## Clean main route

Runtime fetches the selected tracking ref and classifies the actual fetched tip.

| Relationship | Action | Additional product tests or AI review solely for sync |
| --- | --- | --- |
| Equal | Return synchronized | None |
| Local ahead | Non-force push and convergence confirmation | None |
| Remote ahead | Fast-forward and convergence confirmation | None |
| Diverged, no program-affecting integration | Merge, commit and push | No FULL; request semantic help only for unresolved meaning/conflicts |
| Diverged, program-affecting integration | Merge candidate, impact-selected checks/review, commit and push | Reuse applicable evidence; run only required uncovered checks |

Fast-forward is the ordinary pull-only route; do not create a merge candidate or
review merely to consume existing commits. A later remote advance is handled by
the existing relationship/conflict route without force push or false success.
This contract adds no periodic observation or speculative Git preflight loop.

## Validation and semantic judgment

Assess changes from both parents and any actual conflict-resolution edits.
Program impact includes source, executable specifications, build/dependency
definitions, generators, executable content and runtime configuration. A path
under docs is not sufficient proof when it is a consumed build/runtime input.
Conversely, a merge commit, changed HEAD, UPDATED status or document change is
not a reason by itself to run FULL.

Reuse actual applicable compile, test and review results across Phase/Step/sync
boundaries. Check relevance to changed behavior, selected coverage and consumed
dependency/configuration inputs using existing evidence. Do not demand equality
of the whole Git revision, add content hashes, or rerun tests merely to obtain a
new receipt. Evidence absence is not a reason to test a push-only/fast-forward
route; uncertainty about a genuinely program-affecting merge is assessed at that
boundary. Select focused tests for bounded uncovered behavior and FULL only
where the admitted impact requires it. FULL excludes HEAVY; do not expand either
classification or add external-service tests here.

Deterministic acceptance ends the assessment without an AI gate. Delegate only
unresolved semantic questions, using the existing triage/review/FIX route.
Re-review covers findings, repair delta and affected scope. Large textual diffs
use retained evidence, structured summaries and requested ranges; size alone
does not require Human handling or make the workflow fail. Binary changes use
appropriate metadata/evidence and semantic handling when needed, not invented
text reviews. Missing relevant information must remain visible.

## Dirty entry: commit by purpose, then join the clean route

1. Runtime inventories actual staged/unstaged/untracked changes and associates
   them with available Task/Step/Finding/Journal records and prior results.
2. Reuse a recorded coherent change purpose and acceptance when available.
   Ask AI only for unresolved rationale, grouping, dependencies or semantic
   impact; batch related questions. File count or one-file-per-commit is not the
   grouping rule. One purpose may span files; one file may contain several
   purposes. If splitting produces invalid intermediate states, propose a
   coherent combined unit with its reason.
3. Reuse the existing Step-equivalent acceptance/commit route for each unit:
   applicable compile/focused checks and conditional review, with 6.8 hygiene and
   recovery. Preserve applicable successful evidence instead of retesting it.
4. Commit units in dependency order with purpose-bearing messages, then join
   the same clean synchronization route. No second blanket validation/review is
   added merely because the work crosses the dirty-to-clean boundary.

The user sync request covers intended work in its declared scope; unknown intent
or another task's ongoing edits require classification and, if unresolved, a
focused user decision. No silent all-files checkpoint substitutes for accepted
purpose commits. Preserve partial staging and unrelated work; do not stash,
reset, discard or commit excluded changes to manufacture a clean tree. If such
changes remain, return the concrete unfinished local-preparation outcome while
preserving completed commits. Do not overwrite an existing active owner.

## Continuation, evidence and adoption

Reuse the [Git correction](../phase/repository-sync-git-correction.md) and
[6.8 commit/recovery](commit-completion-and-recovery.md) rather than adding an
executor, approval ledger or retry framework. Collect completed effects; continue
only unfinished work. Live or unknown effects keep their actual states. Report
local commit success separately from remote synchronization failure/deferment.

Record selected relation, purpose groups, validation reuse/selection reasons,
semantic requests and actual outcomes in existing Journal/Evidence/Finding.
No extra AI call or periodic monitoring is introduced for recording/display.
Preserve Session/Plan/base, accepted results, failure/UNKNOWN history, counters
and successful checks. Public adoption is required for an active Session; these
documents authorize no private state rewrite, reset, re-Start or accepted replay.

This target supersedes blanket full-test/review and automatic dirty-checkpoint
wording in older RepositorySync descriptions for the Phase 6.9 delivery. Keep
historical results and currently running contracts intact until qualified adoption.

## S2 authored standard-runtime connection

The standard runtime document shortcut consumes the optional normal host
`sm-workflow.repository-sync.document-inputs` declaration. Actual parent/index
Git deltas must be covered by declared document scopes and outside every declared
consumed-input scope; unknown paths and nonordinary/unavailable input remain
unfinished. The declaration must describe real build/runtime consumption, so a
path under docs alone never proves non-consumption. Equal, push-only and
fast-forward do not require this merge-specific declaration.

Document-only candidates use native structural checks and existing conditional
assessment. Actual parent overlap selects existing scoped triage; actual conflict
selects the conflict route. Deterministic admission creates no semantic review
receipt or repeated product PASS. Binary/incomplete input stays explicitly
unexamined. Large text uses CommonEvidence and `EvidenceService.readEvidenceRange`
with zero-based Unicode code point offset/count; range access is not review
coverage. See the [S2 source/evidence note](../notes/phase-6.9-s2-merge-assessment.md)
for configuration, API inputs, authored cases and runtime qualification limits.

## S3 program impact connection

The standard runtime accepts optional host `sm-workflow.repository-sync.program-inputs`
coverage/consumption scopes, explicit broad input paths, ordinary focused/FULL
check bindings and native prior-evidence locators. Actual typed parent/index
changes select coverage; broad or uncovered input changes select FULL with
their actual paths and rationale. Merge/UPDATED and metadata alone select no
FULL, and this route adds no HEAVY selection. Known consumed paths are distinct
from unknown semantic inputs.

Prior evidence is read from actual managed GoalPhase Start, validation and
committed accepted Step closure records under the same repository/branch and
principal. Exact typed consumed-input deltas govern reuse; original receipts and
review references retain their source attribution. Successful validation never
manufactures independent review or waives unresolved merge interaction. Failed
and unknown native checks retain their existing FIX and no-blind-replay routes.
See the [S3 source/evidence note](../notes/phase-6.9-s3-program-impact.md) for
configuration, authored specifications and remaining qualification limits.

## S5 completed effects and same-Session delivery

Integration now loads its original `integration-intent` before any Git operation.
A fresh intent may perform its selected fast-forward/merge once. Re-entry inspects
the saved parents/relation against actual native Git facts; an unestablished
outcome stays UNKNOWN even when the worktree appears unchanged. Existing commit,
push and final-fetch intent observation retain their original no-replay owners.
A failed push or later remote movement cannot discard accepted local commits or
convert their result into remote success. A retained native terminal is collected
from its actual saved convergence and closure, without fresh Git, SBT or review.

The exact `installed-sync-route` source selector is
`org.simplemodeling.textus.smworkflow.admission.Phase69InstalledRepositorySyncSpec`.
It composes controlled normal clean/dirty Operations, interrupted integration
collection, strict public validation-adoption contracts and conditional precommit
Step review. Source qualification is distinct from fresh installed CLI observation.
The publication owner must publish the integrated product, activate the returned
image through the normal updater, load its exact packaged skills and retain fresh
clean/dirty CLI results. No source test name or historical PASS proves that delivery.

Public adoption remains bounded by each existing owner: validation continuation,
GoalPhase remaining Plan/dependency scope, closure and repair-policy continuation.
Preserve the actual complete target, Session/Plan/base, accepted prefix, failures,
UNKNOWN history and counters. An old pending Human question uses its applicable
public owner and actual independent direction; neither a skill nor installation
supersedes it silently. The
[precommit Step review](goal-phase-precommit-step-review.md) route carries the
qualified operational repair forward without treating an external report as
independent native review.

Native progress and validation receipts retain actual relation, selected effects,
purpose and check/reuse reasons. Existing CommonEvidence and Journal producers
project their real sources; disabled/unavailable Journal is not successful
recording, and structural admission is not AI review. No periodic checks or
additional AI are introduced to obtain a display/record. See the
[S5 handoff](../notes/phase-6.9-s5-delivery-and-handoff.md) for actual observations
and remaining qualification work.

## Purpose check repair outside the current inventory

The existing repair-scope correction operations also qualify necessary unchanged
baseline files for a genuine failed selected check on a retained Purpose
candidate. The distinct v4 correction target and Purpose inventory policy bind
the actual native failure, earlier prepared candidate, full request and ordered
plan, original ownership and repository state, and exact available unclaimed
pending repair WorkOrder. The virtual candidate tree and the dirty original index
are separate facts. Historical v1-v3 pending-merge contracts remain unchanged.

Qualification reads ordinary existing tracked candidate text through typed Git
and compares newly added baseline text with the original worktree through the
typed file API. It validates the fixed clean candidate workspace and original
HEAD/branch/index and inventory content without checks or writes. The v4
contract refuses a newly added path belonging to a later Purpose, naming the
path and group. The separately qualified v5 ownership target and Purpose
inventory v2 bind that overlap to the original ordered groups and captured root
text. Existing shared current paths retain their ordinary semantics. No
file-count or byte-size Human gate is introduced.

Independent prior-worker lifecycle direction and UNKNOWN acceptance use existing
evidence preparation and atomic native supersession. Separate ordinary Advance
captures and issues the fixed qualified inventory. Its existing nativeRepair
assignment exposes the failed candidate text, complete later-owner intent,
captured root and exact required owner/path pairs in `purposeWork`. A worker
returns current candidate replacements and a separate explicit
`purposeContinuation` containing integrated later text for every assigned pair.
Nonoverlapping repairs may append unchanged baseline files; ownership repairs
keep the original captured request and later plan groups exact. Separate
progress `ownership` overlays only the assigned paths when preparing the
effective later group. Actual installation retains `projectedWorktree` so that
restoring later work preserves the accepted current correction as well as later
intent. Every other original field, source record, dependency, exclusion,
counter lineage and receipt remains retained. The ordinary next slot uses
the failed virtual tree as its baseline and obtains fresh affected checks,
independent review and actual local Purpose commit. Adoption alone grants no
candidate or validation acceptance. See the
[failed-check continuation specification](repository-sync-formal-failure-continuation.md#failed-purpose-baseline-repair-inventory)
for compatibility rules and the separately selected old/new installed fixture.

An actual semantic/text conflict returns `decision-needed` with the precise
path and later owner, no replacements and no ownership continuation. Ordinary
Advance returns `Wait` / `RepairDecisionRequired` with the retained rationale,
without candidate effects, checks or commits. Repetition stays passive until
actual semantic/Human resolution; no automatic consent or follow-up API is
inferred. The fixture's distinct `ownership-capture` and `ownership-continue`
modes retain actual old v4 prepare refusals, qualify new v5 ownership, and
separately prove successful integration and an unresolved nondestructive Wait.
They preserve all ID06 selectors and evidence. Only actual parent-owned
installed execution establishes qualification; authored assertions do not
constitute live independent review or consumer completion.

## Final publication before completion

When the selected local-publication target declares `beforeSync: true`, native
convergence first publishes the final integrated clean source via
LocalPublicationService. Publication never commits. RepositorySync retains the
actual publication, captures its ordinary CAR catalog outputs, commits those
exact outputs through typed Git/UoW, then performs non-force push, final fetch and
clean convergence observation. Re-entry collects retained publication/stage/commit
facts; missing or uncertain effects are not retried. Unrelated output or dirty
work prevents completion and is preserved. A publication-generated change is not
an exemption from the clean-tree completion condition.

## ID08: distinct current commit and accepted root projection

A repaired Purpose may commit a current baseline correction while a later
original Purpose retains a different integrated final worktree text for the same
path. These are separate typed Git inputs. The current group's corrected content
remains `GitPurposeChange.content`, and the captured actual root remains
`expectedWorktree`. When a baseline tree exists and an accepted later original
owner covers that path, its complete integrated text is supplied separately as
`GitPurposeWorktreeProjection`. This affects CNCF's root-after projection only;
index projection, staged remainder and source precondition checks stay owned by
CNCF and are unchanged.

Every accepted later original owner of the same path must agree on the exact
optional integrated content. Different values require actual semantic/Human
resolution before any preparation effect; first-owner or last-owner selection is
forbidden. Explicit `None` content means deletion and is distinct from absence of
an explicit projection. Without a baseline tree or accepted later ownership, no
projection is invented.

The optional `Candidate.worktreeProjection` retains unique selected-path content
and is reconstructed into the full same CNCF request for both Materialize and
Commit. Duplicate paths, paths outside the current selected changes and explicit
projection without a baseline refuse. Legacy candidates with the field absent
retain their original meaning. No old accepted result is replayed and no legacy
candidate is silently upgraded.

The public failed-preparation continuation is specified in
[formal failure continuation](repository-sync-formal-failure-continuation.md).
It preserves the already accepted repair and immutable original failure, records
one explicit Human direction, then leaves the new namespaced preparation to the
ordinary typed Git/UoW progression. It neither grants authority to later Purpose
failures nor changes existing validation, review, commit or terminal acceptance.

## ID09: root expectations for retained qualified baselines

Successive ownership repairs keep three facts separate: the qualified failed
candidate's baseline text, the authenticated root expectation in saved progress,
and the explicitly integrated later-owner text. For every qualified baseline,
typed root reads compare against `projectedWorktree` first or the original request
expectation next, including an explicit deletion. Only a newly qualified path
without a captured expectation takes its baseline text as the root expectation.
The next ownership projection uses these expectations from the original saved
progress; candidate replacements never overwrite retained root provenance.
Nonoverlapping baseline append behavior and legacy absent projections retain
their existing contracts, without new persisted fields or inventory policy.

An already qualified clean addition present in retained `projectedWorktree` but
absent from the original request uses the current effective group's admitted
content as its explicit root-after projection only when it has no later owner.
Explicit deletion remains deletion. This avoids an unnecessary three-way merge
against an earlier failed candidate while preserving the captured root-before
guard. Original request paths receive no override from this rule. Supplied later
resolution takes precedence; missing or conflicting later resolution keeps its
existing behavior.

The existing snapshot equality, actual candidate inventory, clean ordinary file
shape, safe unique paths, HEAD, branch, index, scope and later-owner checks remain
required. Root drift refuses. After accepted repair and a known pre-effect 409,
the same Session continues through ordinary Advance without replaying the accepted
Submit or resetting history, accepted results, failures, UNKNOWN effects or
counters. See the [formal continuation contract](repository-sync-formal-failure-continuation.md#id09-retained-baseline-provenance-across-ownership-repairs).

## ID11: retained push and later remote movement

A fresh convergence attempt reads the advertised remote tip before creating a
push intent. If the remote already advertises the accepted final tip, native
progression records an actual present observation without claiming that a push
process ran. If the advertised tip differs from the tracking relation used to
admit the candidate, progression retains `NativePushRefreshRequired` and returns
`Wait`. It performs no push against unassessed remote inputs. This check applies
again to a genuinely new push after integration or repair.

After typed `GitOperation.Push` returns, native progression saves its exact
operation, outcome, value-presence and `ProcessExecutionResult` before calling
`valueC` or returning failure. `NativePushFailed` retains an actual exited
failure; `NativePushUncertain` retains incomplete or invalid returned facts.
Process captures use the existing capture/termination encoding and retain native
artifact metadata without another application byte or count limit. These are
Git observations and carry no invented SBT facts. An older resultless push intent
remains UNKNOWN. A CLI exit, external comparison or absent worker does not prove
that original execution ended and cannot supply its missing process body.

`describeRepositorySyncPushContinuation --sessionId` observes the current clean
native root and advertised remote. Its exact canonical target binds the
Component instance, original Start context and principal, Session, root, branch,
endpoint, native frontier cause and revision, source namespace, retained intent
when present, saved committed native source and final tip/tree, original fixed
validation policy and check selection, actual Git observation when present, and
one fixed finite continuation plan. Optional absent facts are omitted. Explicit
null, malformed or unknown fields refuse. No opaque Purpose snapshot is needed.

`adoptRepositorySyncPushContinuation --input` or `--inputResource` consumes the
typed fields `sessionId`, `target`, `choice`, `rationale`, `directionReference`,
`priorExecutionEnded`, `acceptOriginalUncertainty` and `executionEvidence`.
`RefreshRemoteAndContinue` requires independently admitted Human evidence for
this exact description and answer, including `priorExecutionEnded=true` and
`acceptOriginalUncertainty=true`. The rationale is retained in full. `Defer`
writes no direction or effect. A stale, crossed or contradictory answer refuses;
the exact already saved answer is idempotent. Human evidence preparation and
adoption neither accept a candidate nor execute Git or checks.

Separate ordinary `advanceRepositorySync` selects the actual latest native
adoption and authenticates its adjacent immutable history prefix. The new
namespace is `push-continuation-<frontierRevision>`; its adoption key is
`<namespace>-adopted`. Original keys and every earlier Purpose commit, check,
review, failed result and UNKNOWN intent remain unchanged. Historical proof is
compared with its exact old prefix, including when later revisions are present.

The finite direction permits one real typed Fetch under the existing host
authority, ordinary necessary preservation/integration, applicable selected
checks and independent review, at most one new non-force push, then final
convergence observation. Native progression records `refresh-fetch-intent`,
then the actual returned capture in `refresh-fetch-result` before further Git
reads, and then the actual relation in `refresh-fetch-observed`. Missing returned
facts produce an uncertain result without repeating Fetch. New failure or
remote movement retains a new describable frontier and requires another exact
independent direction. A post-adoption remote change before Fetch returns a
refresh-required frontier without executing Fetch.

Fresh integration uses the existing preservation, relationship, conflict/Human,
validation, review and repair owners. A new merge candidate has genuine current
check/review provenance; old candidate receipts are cleared rather than renamed
as new executions. Equal, fast-forward and local-ahead relations retain their
ordinary proportional policy. Repair retains its original cumulative budget.
Publication continues through the existing applicability and necessity owner;
another namespace alone does not require repeating successful publication.

Active push continuation dispatch precedes old Purpose and original fetch/merge
caches. Native merge/conflict candidates authenticate the actual saved fresh
Fetch, relation and active direction. Convergence commit, push, final fetch and
converged keys use that authenticated scope, including after repair. Completed
result collection selects the same scope and remains read-only. It never
replays an accepted Submit, resets original keys, infers a prior failure from
diagnostic byte counts, force-pushes, or manufactures terminal convergence.
# Common validation initialization

The standard runtime shares validation initialization with its service composition.
For RepositorySync it reads the existing managed process profile and the ordinary
`build.sbt` / `project.yaml` declarations. No caller-authored per-run
`program-inputs` JSON is required for a conventional SBT project. Initialization
does not execute a build, create a grant or claim a successful result.

Available checks retain their actual operation, ordinal, repository workspace and
component grants. The standard fallback selects Compile, available FixCheck and
Test (FULL); it never guesses a TestOnly selector or a suite's precise consumption
scope. Source/build scopes and a declared `cml.source` are consumed inputs.
README, LICENSE, CHANGELOG and development notes/journals are standard document
scopes; explicit project consumption overrides these defaults. Other paths remain
unknown and require FULL coverage when integrated in a merge.

Explicit `document-inputs` and `program-inputs` each take precedence independently.
An unknown project or unavailable declaration does not create authority, a PASS,
or an initialization refusal for a light sync. Actual fetched parent/index deltas
still determine whether a merge needs product validation. Equal, local-ahead and
fast-forward retain their existing structural-only integration policy.

Existing configured prior-evidence references retain their native reader and
actual input-applicability checks. There is no new evidence ledger or claim of
global discovery of historical Sessions. Inferred defaults do not attach unknown
historical results to incomplete consumption scopes: without applicable retained
evidence the selected checks execute normally.
