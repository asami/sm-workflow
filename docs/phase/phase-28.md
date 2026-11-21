# Phase 28: Deterministic Diagnostics and Lint Auto-Fix

Status: planned
Planned: 2026-11-22 (Asia/Tokyo)
Scheduled: immediately after Phase 24; before Phases 18, 23, 22
Depends on: Phase 24 practical API-credit route
Checklist: [Phase 28 acceptance](phase-28-checklist.md)
Decision: [Deterministic-first development strategy](../journal/2026/11/2026-11-22-deterministic-first-auto-fix-strategy.md)

## Goal
Reduce AI calls, repair latency and unnecessary edits by applying proven programmatic compiler-diagnostic and lint fixes before AI repair. Preserve existing Candidate–Admission, workspace permissions, finite retries, test evidence and Take Over. No AI inference is permitted in the deterministic stage.

## Delivery
- D1 Inventory actual Scala 2.12/3 compiler diagnostics, Metals/LSP code actions, Scalafix/Scalafmt and current scala-lint/car-lint integration. Measure baseline error categories and costs. Do not assume every diagnostic has a safe code action.
- D1a Define optional Metals usage in each project's `project.yaml` (enabled/disabled, executable or server configuration, timeout and resource limits). Default enabled (`true`) unless explicitly disabled; validate schema and configuration, and gracefully fall back to non-Metals diagnostics when unavailable. Never launch a server merely because it is installed.
- D2 Implement a typed diagnostic -> candidate patch -> validation adapter. Select only unambiguous, semantics-preserving and explicitly allowlisted fixes; check revision, workspace scope and overlapping edits; fail closed on ambiguity.
- D3 Deliver useful deterministic fixes for imports, formatting, safe syntax/API migrations and other verified compile-error/hygiene categories. Integrate existing Scalafix/Scalafmt where compatible. Preserve original diagnostic and patch provenance.
- D4 Strengthen scala-lint/car-lint warnings as Bad Smell candidates. A warning is resolved by a code fix or justified source-local annotation that the linter itself validates. Detect invalid, stale and unnecessary annotations. Do not automatically insert a justification annotation: AI/human judgement is required for acceptance.
- D5 Recompile/re-lint and run impact-appropriate focused tests after each bounded patch batch. Revert unsafe/failed patches; preserve errors, history and counters. Route unresolved cases through existing AI Fix/Admission, without creating a second repair loop.
- D6 Record deterministic fix success, failure, time and avoided AI calls as measured observations; compare with baseline without fabricated savings. Qualify representative Scala 2.12/3 cases and independent review.

## Boundaries
- Metals is an optional per-project tool, not a global prerequisite (default enabled, explicit opt-out). Start and stop its process within approved workspace capabilities, bound initialization and requests, and record whether code actions were actually available.
- No blanket automatic type casts, stub insertion, arbitrary method rename, suppression annotations, or speculative refactors.
- Linter ownership retains rule/fix definitions; sm-workflow orchestrates, validates and admits patches. Producer changes require their own ownership and acceptance.
- 512 KiB source-size hygiene is a configurable warning policy, not permission to truncate or rewrite source automatically.
- Source-local annotations must be readable and validated in ordinary standalone lint runs, not just sm-workflow.
- A fix is not successful merely because a diagnostic disappeared; compile, lint, focused tests and admission remain authoritative.

## Exit
Demonstrate at least one real compiler-diagnostic deterministic fix, one lint/hygiene fix, correct unresolved-warning annotation validation, a safe refusal/fallback, and measurable before/after outcomes. Preserve all existing Phase/Session states. Planning does not start execution.
