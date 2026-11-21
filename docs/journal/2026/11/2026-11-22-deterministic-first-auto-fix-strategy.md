# Development Strategy: Deterministic-First Auto-Fix (2026-11-22)

## Decision
Insert [Phase 28](../../../phase/phase-28.md) immediately after Phase 24 in the adopted development sequence: running 7/9 -> 24 -> **28** -> 18 -> 23 -> 22 -> 17 -> 21 -> 15 -> 14. Deferred Phases 10/19/13/20/25/26/27/16 retain their scope and dependencies. This is scheduling only; no Session starts.

## Rationale
The current AI Fix path can handle compiler errors and lint warnings, but programmatic fixes should precede inference whenever provably safe. Compiler diagnostics and language-server code actions may offer targeted edits; Scalafmt/Scalafix provide established transformations. Their availability and safety must be verified by Scala version and error category.

## Project-level Metals configuration
Metals integration is enabled by default and configurable through the project's `project.yaml`, not a mandatory tool; projects can explicitly disable it. Define and validate enabled state, server/executable selection, timeouts and resource bounds; use workspace-authorized process execution and deterministic fallback when disabled, unsupported or unavailable. Exact YAML keys must be aligned with the existing project schema during implementation, rather than inventing a parallel configuration system.

## Architecture
Compile/Lint -> structured diagnostics -> allowlisted deterministic fix candidates -> scoped patch -> compile/lint/focused test -> existing Admission. Ambiguous or unresolved diagnostics -> existing AI Judgement/Fix. No duplicated orchestration, unbounded retries, speculative edits or unapproved source writes.

Warnings are Bad Smell candidates. AI/human decides code fix versus justified acceptance; acceptance is recorded as source-local annotation understood by standalone linter. Unjustified automatic annotation insertion is prohibited. Invalid, stale or unnecessary annotations must be reported. Quality target: zero unresolved errors and zero unjudged warnings, not simply zero warnings.

## Ownership and evaluation
Scala/CAR linter owners implement rule semantics and safe transforms; sm-workflow integrates them with existing authorization, convergence and evidence. Prioritize real compiler errors and hygiene; assess broader semantic fixes separately. Measure deterministic fix coverage, correctness, latency, fallback and actual AI calls avoided; do not claim a fixed cost saving. Preserve the 512 KiB configurable source-size hygiene policy and stabilization ownership.

## Handoff
Implement only after Phase 24's usable delivery. Existing Phase 18/23/22 scope is unchanged, but its scheduled slot moves after Phase 28. No prior acceptance or runtime status is altered.
