# Scala Lint Quality Strategy (2026-11-22)

## Scheduling decision
Add [Phase 29](../../../phase/phase-29.md) **after Phase 23 and before Phase 25**, retaining the adopted sequence after active 7/9: 24 -> 28 -> 18 -> 23 -> **29** -> 25 -> 26 -> 27 -> 22 -> 17 -> 21 -> 15 -> 14. This is scheduling only; no active Session or accepted result changes.

## Design
Phase 28 builds language-neutral deterministic auto-fix mechanics and a working Scala adapter. Phase 29 strengthens Scala lint detection and standalone annotation semantics. Treat warnings as Bad Smell candidates: resolve through code fix or an explicit justified source-local annotation, validated by the linter. Machine-safe fixes precede AI judgement; context-dependent smell detection must not become simplistic unconditional bans. Detect stale, invalid and unnecessary annotations, but do not assume the linter can establish the truth of an architectural rationale.

Inventory and reuse Scala lint, Scalafix, Scapegoat, WartRemover, car-lint and Phase 8.1 capabilities before extending them. Target type safety, complexity, exceptions, mutable state, concurrency, oversized files, redundant defensive logic and overabstraction; minimize false positives and redundant rules. Source-size hygiene remains configurable (512 KiB starting policy). Preserve Scala 2.12/3 compatibility and existing project profiles. Implement rule semantics in the owning linter repository, integration in sm-workflow, and use Phase 28 repair/Admission rather than a new repair engine.
