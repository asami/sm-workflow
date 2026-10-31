# Test selection with ADMISSION and standard annotations

Date: 2026-10-31 (Asia/Tokyo)
Status: approved target; coordinated producer/consumer migration pending.
Prior LTEST delivery remains accepted within its original scope. The target below
supersedes its multi-purpose authoring guidance; it does not reopen that delivery.
Plan: [TA-S1–4 and TA-A01–06](../notes/label-driven-test-migration-plan.md#standard-annotation-follow-up--2026-10-31).
Handoff: [approved migration and preservation](../journal/2026/10/2026-10-31-admission-standard-annotations-handoff.md).

## Classification and run scope

ADMISSION is the only custom classification label. Retire active SMOKE, FOCUSED,
FULL, HEAVY and ON_DEMAND purpose declarations and query values. Do not introduce
aliases, duplicate labels or a Slow-to-HEAVY normalization layer. FULL and focused
remain descriptions of run scope, not test memberships. Assess former SMOKE
coverage individually before assigning ADMISSION; do not bulk relabel it.

Keep canonical CNCF feature, expectedDuration, execution requirements, logical
operation identities and provenance. Existing stored evidence retains its original
vocabulary and scope; it is not an active selector or acceptance of this migration.

## Standard annotations and explicit selection

Use ScalaTest `@org.scalatest.tags.Slow` or per-case `taggedAs` only for long-running
work. Use `@org.scalatest.DoNotDiscover` for suites requiring explicit execution,
including special-environment and low-frequency acceptance. Combine them when both
apply. An external database or Docker dependency alone is not a duration label.
These suites remain active and maintained when exercised, rather than disabled.

Normal test/FULL and ordinary ADMISSION exclude Slow and automatic discovery of
DoNotDiscover suites before lifecycle setup. Selecting Slow alone does not include
DoNotDiscover suites. Name those suites explicitly through the supported generic
SBT path, optionally with actual case/tag filters. Broad patterns and feature-only
queries must not re-enroll them. No class-name Set, persistent enrollment or
per-suite build edit is required. Named unrestricted suites may run all their cases.
Focused validation can explicitly select affected Slow or explicit-only tests.
Report the real selection; an empty run is not successful requested coverage.

SQLite, in-memory providers and small same-JVM test-owned loopback fixtures remain
ordinary. Keep actual external prerequisites separate and retain required live
opt-ins; annotations neither prepare resources nor authorize effects.

## ADMISSION operating target

ADMISSION targets at most one minute of aggregate selected test-body time,
excluding compile, resolution, discovery, startup, fixture preparation/cleanup and
result retention. This is not a per-case allowance, fixed timeout, assertion or
automatic cancellation rule. Preserve a successful over-target result and assess
selection/cost using its actual coverage. Preserve existing duration estimation.

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
