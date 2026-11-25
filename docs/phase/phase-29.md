# Phase 29: Scala Lint Quality and Bad Smell Analysis

Status: planned
Planned: 2026-11-22 (Asia/Tokyo)
Scheduled: after Phase 22 and before Phase 17 (after Phase 23)
Depends on: Phase 23; consumes applicable Phase 28 deterministic auto-fix integration
Checklist: [Phase 29 acceptance](phase-29-checklist.md)
Strategy: [Scala lint quality strategy](../journal/2026/11/2026-11-22-scala-lint-quality-strategy.md)

## Current scheduling — 2026-11-24

Schedule Phase 29 after Phase 22 (and after Phase 23) and before Phase 17. The lint scope and acceptance are unchanged. See the [current roadmap](README.md#adopted-development-priority--2026-11-24). Earlier scheduling notes below are historical.

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

## Diagnostic repair policy — 2026-11-22

Apply this policy to lint Errors and Warnings through the existing repair and
Admission owners. Phase 28 consumes it for deterministic selection and fallback.

1. Preserve the diagnostic and identify the violated contract, actual source of
   the value and intended behavior. A diagnostic's severity or disappearance
   alone does not decide whether a change is correct.
2. Prefer a code correction that establishes the contract: remove an unnecessary
   defensive check, correct the model/type or normalize external input at its
   owning boundary. Use a proven deterministic fix where available; otherwise
   use the existing bounded AI repair route. Preserve meaningful empty values
   and the specified reject/absence behavior rather than silently dropping data.
3. If the operation is genuinely required at an external/nullable boundary,
   express its contract through the applicable nullable type and, where needed,
   a source-local semantic annotation. The annotation identifies the rule/target,
   boundary, nullability/normalization obligation and justification. Ordinary
   standalone lint validates its supported meaning, placement and code
   consistency. A generic warning-suppression annotation or reason text alone
   does not establish that contract.
4. Re-lint, compile and run impact-appropriate tests under existing Admission.
   If the repair policy still cannot be satisfied within the authorized scope
   and bounded repair route, request Human direction. Report the original and
   remaining diagnostics, attempted corrections, concrete blocker, relevant
   evidence, alternatives and their behavior/risk. No silent waiver, downgrade,
   unlimited retry or successful-completion claim follows from exhaustion.

Human confirmation is for the unresolved contract, exception or scope decision,
not every deterministic/AI repair or every valid annotation. Keep any explicitly
accepted exception attributable and bounded; an answer authorizes only the
selected disposition, not a fabricated code fix or proof of safety.

### Null handling and semantic annotations

A boundary that can actually supply null declares that fact in the supported
type/interop contract and converts or rejects it once before internal use.
Internal APIs retain their non-null/optional contract. An annotation does not
perform normalization or make a value non-null by itself. If the producer omits
absent Map entries and no nullable producer path is established, do not assume
repeated internal null guards are necessary merely because the original data
came from an external service; investigate their actual contract first.

Analyze null tests and equivalent supported predicates by their meaning. Replacing
`value != null` with `Objects.nonNull(value)` is an acceptable expression change
when the guard is justified, but is not on its own a repair of redundant defensive
logic or an undeclared boundary. A suppression, helper extraction or predicate
substitution must not evade the original rule.

Qualify both a legitimate nullable interop boundary with normalization and an
internal non-null value with an unjustified guard. Cover the direct comparison
and `Objects.nonNull` forms, valid/misplaced/stale semantic annotations and plain
suppression-only bypass attempts in standalone lint. Where analysis cannot
establish the contract, return an explicit unresolved/context-dependent finding;
do not invent proof or categorically ban legitimate boundary guards. AI/Human
judgement remains responsible for rationale beyond static analysis capability.

## Boundaries
Phase 28 owns deterministic repair mechanics; Phase 29 owns Scala lint coverage and annotation semantics. Producer linter rules belong to their actual repositories and owners; sm-workflow owns integration/quality gates. No AI-generated blanket suppression or infinite fix loops. No Phase/Session is started by this plan.

## Exit criteria
Evidence-backed rule inventory and representative new detection; valid/invalid/stale annotation tests in standalone lint; deterministic fix or AI judgement integration on representative warnings; measured false positives and bounded validation; independent review and normal closure.
