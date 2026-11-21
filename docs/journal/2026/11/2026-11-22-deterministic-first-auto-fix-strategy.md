# Development Strategy: Deterministic-First Auto-Fix (2026-11-22)

## Decision
Insert [Phase 28](../../../phase/phase-28.md) immediately after Phase 24 in the adopted development sequence: running 7/9 -> 24 -> **28** -> 18 -> 23 -> 22 -> 17 -> 21 -> 15 -> 14. Deferred Phases 10/19/13/20/25/26/27/16 retain their scope and dependencies. This is scheduling only; no Session starts.

## Rationale
The current AI Fix path can handle compiler errors and lint warnings, but programmatic fixes should precede inference whenever provably safe. Compiler diagnostics and language-server code actions may offer targeted edits; Scalafmt/Scalafix provide established transformations. Their availability and safety must be verified by Scala version and error category.

## Project-level Metals configuration
Metals integration is enabled by default and configurable through the project's `project.yaml`, not a mandatory tool; projects can explicitly disable it. Define and validate enabled state, server/executable selection, timeouts and resource bounds; use workspace-authorized process execution and deterministic fallback when disabled, unsupported or unavailable. Exact YAML keys must be aligned with the existing project schema during implementation, rather than inventing a parallel configuration system.

## Language-neutral interface, Scala-first delivery
Phase 28 defines a reusable, language-neutral diagnostics/code-action/patch contract and implements/qualifies its **Scala adapter** using Metals LSP plus compatible Scalafix/Scalafmt capabilities. Do not hardwire Metals into the shared engine. Java, TypeScript, Python and Dart adapters are future extensions and are not Phase 28 acceptance conditions. Servers differ in Code Action support; use capability discovery and fail-safe patch validation. Keep `project.yaml` authoritative and Metals enabled by default unless explicitly disabled; missing/unavailable Metals must fall back to supported non-Metals checks.

## Architecture
Compile/Lint -> structured diagnostics -> allowlisted deterministic fix candidates -> scoped patch -> compile/lint/focused test -> existing Admission. Ambiguous or unresolved diagnostics -> existing AI Judgement/Fix. No duplicated orchestration, unbounded retries, speculative edits or unapproved source writes.

Warnings are Bad Smell candidates. AI/human decides code fix versus justified acceptance; acceptance is recorded as source-local annotation understood by standalone linter. Unjustified automatic annotation insertion is prohibited. Invalid, stale or unnecessary annotations must be reported. Quality target: zero unresolved errors and zero unjudged warnings, not simply zero warnings.

## Ownership and evaluation
Scala/CAR linter owners implement rule semantics and safe transforms; sm-workflow integrates them with existing authorization, convergence and evidence. Prioritize real compiler errors and hygiene; assess broader semantic fixes separately. Measure deterministic fix coverage, correctness, latency, fallback and actual AI calls avoided; do not claim a fixed cost saving. Preserve the 512 KiB configurable source-size hygiene policy and stabilization ownership.

## Handoff
Implement only after Phase 24's usable delivery. Existing Phase 18/23/22 scope is unchanged, but its scheduled slot moves after Phase 28. No prior acceptance or runtime status is altered.

## AI Capability Pool allocation principle (2026-11-22)
AI execution is not a Local -> API -> CLI hierarchy. After deterministic fixes, select from an eligible AI Capability Pool by task capability, quality, security and lowest expected total incremental cost. Explicitly include expiring surplus subscription/free-tier allowances (such as Copilot when permitted), promotional API credits, remaining quota, reset times, future demand, retries and opportunity cost. Favor otherwise-wasted capacity without sacrificing convergence or generating unnecessary work. Shared quota pools must not be double counted; unknown remaining allowance is not verified free; no silent paid overage or unsupported automation. Phase 27 owns this cost-aware pool scheduler, while Phase 28 owns deterministic repair before AI selection.

## Adopted development sequence (2026-11-22)
After currently running Phases 7 and 9: **24 -> 28 -> 18 -> 23 -> 26 -> 27 -> 22 -> 17 -> 21 -> 15 -> 14**. Phase 26 common Server and multiple CLI integration, and Phase 27 AI Capability Pool cost optimization, are intentionally scheduled after Phase 23 to avoid premature heavy architecture work. No Phase 27.1/27.2 split is adopted. Phase 25 remains deferred; only proven bounded prerequisites may be scheduled through their owners. Existing Phase states and acceptance are unchanged.

## Development order refinement — 2026-11-22

Adopted order after active 7/9: **24 -> 28 -> 18 -> 23 -> 25 -> 26 -> 27 -> 22 -> 17 -> 21 -> 15 -> 14**. Move Phase 25 ahead of Phase 26 so its generic textus-ai Task API and provider capability contract is qualified before common Server/multi-CLI integration. Phase 27 then optimizes the combined AI Capability Pool, including otherwise-expiring eligible allowances. Prior sequence entries in this journal are historical and superseded for scheduling only; no status, Session, scope or acceptance changes.
