# Phase 27 Acceptance Checklist

Status: planned; no acceptance or implementation claimed.

- [ ] P27-01 Confirm accepted Phase 26 shared-server Codex/Claude CLI baseline.
- [ ] P27-02 Inventory Phase 9/13/24/25 provider, billing, accounting and continuation contracts without duplicate implementations.
- [ ] P27-03 Define execution route identity and shared quota-pool attribution (CLI/API/SDK, provider/model/account/payer).
- [ ] P27-04 Verify current entitlement and billing behavior for each selected route; mark unknowns explicitly.
- [ ] P27-05 Ensure no automatic paid overage, BYOK or paid API fallback without explicit authorization.
- [ ] P27-06 Qualify one additional actual route (selected API, Copilot CLI or Copilot SDK) on a bounded task.
- [ ] P27-07 Demonstrate deterministic capability/availability/cost-aware route selection and finite refusal/fallback.
- [ ] P27-07a Verify configurable provider order for small compiler repairs in the ordinary workflow after applicable deterministic fixes.
  - With the setting omitted, an eligible repair selects OpenAI first. The setting is scoped to small compiler repairs.
  - With local-first enabled, one successful local patch passes revalidation and completes without an OpenAI call.
  - An unavailable local provider, a known decline, no usable patch, or a failed revalidation can continue to OpenAI under the existing usage policy. For a failed patch, confirm rollback before the switch. Retain the Session, attempt outcomes, and shared repair allowance.
  - Ineligible OpenAI usage or an unresolved uncertain result prevents automatic OpenAI dispatch. Exhausted or unknown allowance cannot silently cause paid overage.
- [ ] P27-07b Use the [Phase 25.1](phase-25.1-checklist.md) allowance outcome for OpenAI routing decisions and reuse its provider-level validation.
- [ ] P27-08 Preserve authoritative Workflow, workspace ownership, Continuation and Admission on handoff.
- [ ] P27-09 Capture actual per-task outcome, time, usage/cost evidence and unknowns; avoid fabricated monetary equivalents.
- [ ] P27-10 Perform scoped connected validation and independent review before closing.
