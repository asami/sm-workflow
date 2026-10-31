# ADMISSION-only labels and standard annotations — development handoff

Date: 2026-10-31
Status: specification, development plan and operating guidance updated;
implementation and new acceptance pending.
Destination: sm-workflow development.
Requester/return chat: 01a0fa78-944b-7ed2-b04e-9deafbeca751.

## Decision and supersession

The user approved ADMISSION as the only custom test classification. Use standard
ScalaTest Slow for long-running cases and DoNotDiscover for suites intentionally
absent from automatic discovery but usable by explicit selection. Retire active
SMOKE, FOCUSED, FULL, HEAVY and ON_DEMAND purpose declarations/queries. FULL and
focused remain run scopes; heavy is plain language for Slow-tag execution, not a
new registry or enum alias. Do not keep a Slow-to-HEAVY normalization layer.
Assess SMOKE cases individually for ADMISSION coverage/time rather than mass
relabeling. Feature, expectedDuration, requirements and exact identity remain.

This supersedes the ON_DEMAND correction, blanket runnable DoNotDiscover removal,
and dual Slow/HEAVY authoring guidance. It does not discard accepted commits,
assertions, scripts or successful evidence. DoNotDiscover targets remain active
for occasional/nighttime execution and are maintained when exercised. Genuine
sbt scripted retains its framework meaning. No new scheduler/AI monitoring.

## Concrete work and order

1. CNCF: reduce the custom purpose vocabulary and update actual declaration/query,
   annotation/provider/CML mapping and framework binding call sites. Preserve
   standard class/per-test Slow and DoNotDiscover behavior; do not fake framework
   support with class registration. Inspect current branches and staged work first.
2. sm-workflow: connect one small route from selected tests through preparation,
   runtime execution and attributed result. Update project/LabelledTests,
   testing/LabelledTestSelection, ValidationTestPolicy, compiled inventory,
   declarations, CML/config/CLI projections and relevant fixtures as actually needed.
3. Migrate real tests, including the two delivered low-frequency OnDemand suites,
   to standard annotations while preserving bodies/prerequisites. Names may express
   their subject/use without implying an ON_DEMAND metadata contract. Correct
   active help/spec/packaged skills. No compatibility aliases for retired labels;
   preserve historical evidence readers and original historical vocabulary.
4. Verify the changed contract and deliver through existing runtime-owned checks,
   commit and publication when required. Return exact source revisions, actual
   selection/test results, review/delivery scope and remaining adoption limitations.

Normal test/FULL excludes Slow and automatic DoNotDiscover discovery. ADMISSION
retains those ordinary exclusions. Explicit Slow does not include excluded suites
unless the suite itself is selected. Focused may select any needed actual test,
including Slow or an explicit-only suite, without FOCUSED membership. Metadata
inspection precedes selection; lifecycle/resource preparation follows final
selection. External-service requirements remain separate from duration; no Docker
or external-server startup in ordinary FULL. SQLite/small same-JVM loopback remain
eligible. No unrequested whole-suite/catalogue execution.

## Plans and acceptance

- CNCF: `docs/spec/test-selection-standard-annotations.md` and Phase 103's separate
  TA-C1–5 follow-up. The closed Phase and its accepted checklist stay closed.
- sm-workflow: [selection specification](../../../spec/label-driven-test-selection.md)
  and [TA-S1–4 / TA-A01–06 plan](../../../notes/label-driven-test-migration-plan.md#standard-annotation-follow-up--2026-10-31).
- Runtime operating guidance: `docs/spec/native-host-execution.md` and packaged
  sm-* skills; deployed development-workstation shared guidance is updated too.

Acceptance covers normal/ADMISSION/Slow/explicit-suite/named-focused selection,
class/per-case tags, combined Slow+DoNotDiscover, inheritance, accurate empty/
not-executed results, preparation-after-selection and one harmless actual dispatch.
Use bounded producer/consumer tests. Preserve duration estimation and ADMISSION's
one-minute aggregate body-time guideline, not a fixed timeout. Previously passed
22/13 and 7/77/16 cases retain their original coverage, not evidence of this change.

## Preservation and coordination

Existing ON_DEMAND commits a840e382 / 6c1c0ef, earlier 678bae6f / 3fb54cc1 /
bbc040fe and all outcomes remain. Do not rewrite their history or repeat successful
checks solely for result collection. Preserve current dirty/staged work, Sessions,
accepted results, repair counts and unrelated development. No reset, accepted
Submit replay, sm-workflow-dev Phase resume or live consumer activation is implied.

Use supported scope adoption for active work, or a linked new Task if predecessor
Tasks are terminal; do not wait for a new post-return lifecycle API. This handoff
is approved requirements, not an issued WorkOrder. The earlier local-main merge
request remains separate: reconcile its actual state and retain both histories,
without treating integration of obsolete ON_DEMAND as acceptance of this policy.
No push or new integration engine is requested here. Return one attributable
completion/blocker result; no acknowledgment or progress/polling loop.

## Preserved annotation increment — 2026-10-31

The already-issued, separately authorized ON_DEMAND-to-standard-annotation Tasks
finished under their original contracts. This is applicable seed evidence, not
acceptance of the broader ADMISSION-only target above.

- CNCF native GoalTask `113de25e01199a6ce6a78ef1f0499407`: Terminal, scoped local
  commit `09aecfa1690568312b55d1df904369422ca711ee` on the existing repair worktree.
  Compile succeeded; 24 tests across 3 suites passed, with zero aborts. Actual
  ScalaTest automatic discovery excluded the annotated harmless suite, explicit
  suite execution ran its two leaves, and ordinary memory/SQLite parity remained.
  Environment/budget-only Slow suite annotations became DoNotDiscover; the deep
  individual Slow cases and original provider assertions were retained.
- Consumer native GoalTask `110f34939811f10f3f2b07650c2dfe65`: Terminal, scoped local
  commit `348a29a053c634734e267bc580fe55127c3cdd59` on the existing repair worktree.
  Compile/TestCompile succeeded. Initial 12-test evidence was retained; final
  qualification passed 13 tests across 2 suites, zero aborts. The normal generic
  SBT runner actually executed the exactly named DoNotDiscover fixture without
  runtime preparation. The retained catalogue bodies were not executed.
- Independent read-only reviews were accepted through the native Task path.
  Two documentation repairs and their original findings remain recorded in the
  consumer Task; no counters or previous results were reset.
- Canonical CNCF and sm-workflow keep their pending merges and original staged
  sets (13 and 8 paths respectively). Owned annotation/build edits are present
  in their working trees; the repair-worktree history has not been integrated
  into main. The incoming Knowledge PostgreSQL suite was separately annotated
  in the CNCF working tree and is outside the 24-test source candidate.
- No live consumer Session, old accepted Submit, runtime activation, local
  publication or remote push was performed for this increment. Unrelated work,
  including the existing consumer catalogue timestamp change, was preserved.

The other legacy purpose values still require TA-C1–5 / TA-S1–4 migration. Do not
interpret this increment as ADMISSION-only product delivery. Its native result
locators are retained in the repair worktrees under
`target/sm-workflow/explicit-selection-task`; an attributable local summary is
`/private/tmp/standard-annotation-increment-result-20261031.json`.
