# Phase 29 acceptance checklist

Status: planned; no acceptance claimed.
Plan: [Phase 29](phase-29.md)

- [ ] P29-01: Inventory actual lint rules/tools and Phase 8.1 accepted baseline.
- [ ] P29-02: Define rule taxonomy, stable diagnostics and configurable thresholds/profiles.
- [ ] P29-03: Qualify type safety, exceptions, complexity, state and concurrency checks.
  Include actual nullable-boundary declarations and normalization before internal
  use. Distinguish legitimate guards from redundant checks on non-null values;
  direct null comparison and Objects.nonNull do not evade the same policy.
- [ ] P29-04: Detect selected overdefensive/overabstracted Bad Smell candidates without categorical bans.
- [ ] P29-05: Validate source-local accepted-warning annotations in standalone linter (valid/invalid/stale/unnecessary).
  For boundary exceptions, validate the supported semantic contract, rule/target,
  placement and consistency with normalization. A plain suppression annotation
  or rationale text alone is insufficient. Annotation presence does not prove
  null safety or correctness beyond the analyzer's actual capability.
- [ ] P29-06: Connect Phase 28 deterministic fixes and existing AI judgement/Admission without duplicate repair loops.
  Follow the plan's diagnostic repair policy for Errors and Warnings: establish
  the contract by code correction or a valid bounded semantic exception, then
  verify. If the authorized bounded route cannot satisfy it, present remaining
  diagnostics, attempts, blocker, evidence and alternatives for Human direction;
  do not hide the warning, reset the budget or claim successful repair.
- [ ] P29-07: Test Scala 2.12/3, false positives, regression and bounded focused verification.
- [ ] P29-08: Confirm producer ownership, independent review, measured results and normal local closure.
