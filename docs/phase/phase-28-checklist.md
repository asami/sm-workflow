# Phase 28 acceptance checklist

Status: planned; no acceptance evidence yet.
Plan: [Phase 28](phase-28.md)

- [ ] P28-01: Inventory compiler diagnostics, LSP actions and Scala 2.12/3 tool compatibility.
- [ ] P28-01a: `project.yaml` Metals configuration schema, default-on/explicit opt-out, bounded lifecycle and unavailable-server fallback tests.
- [ ] P28-02: Baseline existing AI repair and lint outcomes with attributable metrics.
- [ ] P28-02a: Language-neutral diagnostics/code-action/patch interface with a working Scala Metals adapter; capability negotiation and fallback verified. Other languages explicitly deferred.
- [ ] P28-03: Typed deterministic patch contract, ambiguity checks and workspace authorization.
- [ ] P28-04: Verified compiler-error fixes with real integration evidence.
- [ ] P28-05: Verified formatting/Scalafix/lint deterministic fixes.
- [ ] P28-06: Source-local annotation schema and standalone linter verification of accepted/stale/invalid warnings.
- [ ] P28-07: Bounded retry, recompile/re-lint, focused test, rollback and AI fallback.
- [ ] P28-08: Regression across applicable Scala versions and producer interfaces.
- [ ] P28-09: Actual success/latency/AI-avoidance measurements and limitations.
- [ ] P28-10: Independent review, Admission and normal local closure.

No item is accepted merely by documentation or an unexecuted tool capability.
