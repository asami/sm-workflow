# Runtime-owned single-repository synchronization

Status: adopted target contract; implementation and installed qualification pending.
Date: 2026-10-27
Delivery: [Phase 6.9](../phase/phase-6.9.md).

## Responsibility and allocation

Both clean and dirty entry improvements belong to Phase 6.9. Phase 6.8 retains
its existing commit completion/recovery and mechanical hygiene scope, including
shared RepositorySync commit integration; it does not acquire these new start,
selection or synchronization requirements. Phase 7 composes the qualified route
across related projects rather than rebuilding it.

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
