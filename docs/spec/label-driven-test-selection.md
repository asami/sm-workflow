# Test selection in sm-workflow

Date: 2026-10-31 (Asia/Tokyo)
Status: sm-workflow supplement to the shared test policy. Prior LTEST delivery
remains accepted within its original scope.
Plan: [TA-S1–4 and TA-A01–06](../notes/label-driven-test-migration-plan.md#standard-annotation-follow-up--2026-10-31).
Handoff: [approved migration and preservation](https://github.com/asami/sm-workflow/blob/9debf6484e3e16e6739b83688a30a814772c7b29/docs/journal/2026/10/2026-10-31-admission-standard-annotations-handoff.md).

Classification and default execution follow the
[ai-directive test policy](../../RULE.md#scala-test-classification-and-default-execution).
sm-workflow uses CNCF's compiled test inventory and framework selection. The
[normal typed SBT route](normal-typed-sbt-validation.md) binds those results to
workflow checks. Label-based invocation will be specified separately.

## Selection and workflow evidence

For `FULL`, `TestOnly` and `ADMISSION`, keep the exact selected operations and
their CNCF feature, duration, execution requirements, identity and provenance.
For focused validation, use the affected suites or cases and retain the actual
selection in the Session. Report empty and unexecuted selections explicitly.
Measure the `ADMISSION` operating target using test-body time and retain the
actual coverage, duration and outcome in its result.

## Execution and preservation

Inspect metadata and standard framework tags, select actual operations, bind their
requirements, then prepare only selected resources, execute and retain attributed
results. CNCF owns common metadata and framework bindings; sm-workflow consumes
those facts through its ordinary SBT/UoW route. Do not create a second registry,
force another framework into fabricated ScalaTest labels, bypass the execution
owner or reinterpret a runtime failure as permission for a wrapper fallback.

Preserve original scenario assertions, scripts, ongoing changes, Sessions, base,
Plan, accepted results, history and repair counts. Active issued work finishes
under its retained contract; adopt changes at the supported next boundary or a
linked new Task after terminal predecessors. Do not re-Start, replay accepted
Submit, repeat successful checks solely for result collection or rewrite history.
Previously successful 22/13 and 7/77/16 results retain their original scopes.

Maintain explicit invocation, prerequisites, expected results and cleanup for
occasional/nighttime tests. No catalogue replay, scheduler, AI polling or automatic
AI repair is required. Reserve scripted for the actual sbt scripted mechanism.

## Acceptance and remaining work

Establish one small producer-to-consumer dispatch before widening migration.
Verify normal/ADMISSION/Slow/named-focused/explicit-suite selection, class and
per-case tags, both annotations together, inheritance, preparation after selection,
real harmless framework execution, correct empty/not-executed results and exact
result attribution. Complete real declarations/config/CML/compiled inventory and
caller updates through TA-S1–4. Documentation alone is not product acceptance;
reuse applicable evidence without claiming earlier tests prove this new contract.

## Mode requirements alongside standard annotations — 2026-11-02

Follow [per-test mode selection](development-mode-validation.md#per-test-declarations-build-binding-and-workflow-ownership).
CNCF metadata declares required modes independently of test classification and
framework tags. Select eligible tests first, expand required variants and only
then prepare resources. Matching a mode never overrides an explicit-only or
Slow exclusion. Shared build.sbt integration binds actual target mode;
sm-workflow invokes and collects test/parameter/mode outcomes without re-parsing
annotations or maintaining a second table. VM-07 coordinates producer adoption.
