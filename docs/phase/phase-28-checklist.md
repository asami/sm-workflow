# Phase 28 acceptance checklist

Status: closed
Plan: [Phase 28](phase-28.md)

The [formal local release](phase-28.md#formal-local-release) records Plan 6
adoption, validation 1103, independent ReviewPhase 1114, and native closure
revision 1120. Checked items are the seven criteria met at closure. The five
unchecked items retain their review disposition in the deferred list below.

- [x] P28-01: Inventory compiler diagnostics, LSP actions and Scala 2.12/3 tool compatibility.
- [x] P28-01a: `project.yaml` Metals configuration schema, default-on/explicit opt-out, bounded lifecycle and unavailable-server fallback tests.
- [ ] P28-02: Baseline existing AI repair and lint outcomes with attributable metrics.
- [ ] P28-02a: Language-neutral diagnostics/code-action/patch interface with a working Scala Metals adapter; capability negotiation and fallback verified. Other languages explicitly deferred.
- [x] P28-03: Typed deterministic patch contract, ambiguity checks and workspace authorization.
- [x] P28-04: Verified compiler-error fixes with real integration evidence.
- [ ] P28-05: Verified formatting/Scalafix/lint deterministic fixes.
- [x] P28-06: Source-local annotation schema and standalone linter verification of accepted/stale/invalid warnings.
  Consume Phase 29's semantic boundary/normalization policy; plain suppression
  alone is not a resolution. Direct null tests and Objects.nonNull substitutions
  cannot bypass the same rule. Do not generate a justification automatically.
- [x] P28-07: Bounded retry, recompile/re-lint, focused test, rollback and AI fallback.
  For Errors and Warnings, require a contract-preserving correction or qualified
  semantic exception. If the existing authorized deterministic/AI repair route
  cannot satisfy the policy, request Human direction with retained diagnostics,
  attempts, concrete blocker, evidence and alternatives; no silent waiver,
  endless retry or false success. Cover both normal resolution and escalation.
- [ ] P28-08: Regression across applicable Scala versions and producer interfaces.
- [ ] P28-09: Actual success/latency/AI-avoidance measurements and limitations.
- [x] P28-10: Independent review, Admission and normal local closure.

No item is accepted merely by documentation or an unexecuted tool capability.

## Deferred from the local release

Human1112 adopted these deferrals to release the qualified Scala workflow.
ReviewPhase 1114 retained the original observations. The two implementation
issues remain open.

| Condition | Review disposition | Remaining work |
| --- | --- | --- |
| P28-02 | Not evaluated | Establish comparable operational baselines for AI repair and lint. |
| P28-02a | Unsatisfied (issue-13) | Connect the Scala Metals adapter to the runtime and qualify the connected provider. |
| P28-05 | Unsatisfied (issue-14) | Connect deterministic formatting and lint repair to native patch application and revalidation. |
| P28-08 | Not evaluated | Verify real Scala 2.12 and Scala 3 projects and their producer interfaces. |
| P28-09 | Not evaluated | Measure operational repair success, latency, and AI calls avoided. |
