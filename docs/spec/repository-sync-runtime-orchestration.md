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
