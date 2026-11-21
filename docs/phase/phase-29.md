# Phase 29: Scala Lint Quality and Bad Smell Analysis

Status: planned
Planned: 2026-11-22 (Asia/Tokyo)
Scheduled: after Phase 23 and before Phase 25
Depends on: Phase 23; consumes applicable Phase 28 deterministic auto-fix integration
Checklist: [Phase 29 acceptance](phase-29-checklist.md)
Strategy: [Scala lint quality strategy](../journal/2026/11/2026-11-22-scala-lint-quality-strategy.md)

## Goal
Strengthen Scala static quality analysis and Bad Smell detection without multiplying brittle prohibitions, unnecessary AI work or defensive code. Resolve warnings by a justified code fix or a source-local annotation that the standalone linter can verify.

## Scope and ownership
- Inventory actual Scala lint rules, Scalafix, Scapegoat, WartRemover, existing Phase 8.1 profile work and car-lint boundaries. Reuse existing accepted capabilities; do not reimplement duplicate rules.
- Strengthen detection for unsafe casts/null/partial operations, exception handling, mutability, blocking/concurrency, complexity, deep nesting, excessive file/function size, unused code and unnecessary dependencies.
- Add bounded heuristic Bad Smell candidates for redundant defensive checks, unnecessary abstraction, duplicated state and needless fallback. Context-dependent design judgements remain AI/human decisions, not unconditional Errors.
- Normalize rule IDs, severity, location and diagnostic evidence; configure thresholds and project profiles, including configurable 512 KiB source-size hygiene.
- Support source-local justified acceptance annotations readable by the standalone linter. Validate rule/target scope, stale/unused annotations and syntax; do not claim static analysis proves a rationale remains semantically correct.
- Integrate with Phase 28's language-neutral repair contract: safe deterministic fixes first, unresolved warnings to existing AI judgement for code fix or justified annotation; re-lint, compile, focused test and Admission.
- Qualify Scala 2.12 and applicable Scala 3 versions with real positive/negative fixtures. Prevent noisy false positives and rule duplication. No broad multi-language implementation.

## Boundaries
Phase 28 owns deterministic repair mechanics; Phase 29 owns Scala lint coverage and annotation semantics. Producer linter rules belong to their actual repositories and owners; sm-workflow owns integration/quality gates. No AI-generated blanket suppression or infinite fix loops. No Phase/Session is started by this plan.

## Exit criteria
Evidence-backed rule inventory and representative new detection; valid/invalid/stale annotation tests in standalone lint; deterministic fix or AI judgement integration on representative warnings; measured false positives and bounded validation; independent review and normal closure.
