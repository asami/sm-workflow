# Phase 26 Acceptance Checklist — Dual CLI Operation

Status: planned; all items unverified until actual evidence is recorded.

- [ ] P26-01 Inventory and verify existing server, CLI remote Operation, authentication and persistence boundaries.
- [ ] P26-02 Document one canonical sm-workflow Server endpoint and provider-neutral CLI client contract.
- [ ] P26-03 Provide Claude Code integration guidance using existing public sm-workflow CLI operations.
- [ ] P26-04 Verify Codex CLI can call the selected shared server with real Workflow identity and response.
- [ ] P26-05 Verify Claude Code CLI can call the **same** server with real Workflow identity and response.
- [ ] P26-06 Demonstrate safe sequential cross-client continuation without duplicate effects or state reset.
- [ ] P26-07 Verify server-side authority on stale/conflicting/unauthorized requests and workspace ownership.
- [ ] P26-08 Confirm CLI authentication/billing assumptions; paid API fallback disabled unless explicitly authorized.
- [ ] P26-09 Produce reproducible runbook and connected test/trace evidence; report unknowns and Phase 22 residuals.
- [ ] P26-10 Complete normal scoped validation, independent review and Admission before claiming Phase closure.

Out of scope: resident worker registry, IoC multi-wait, scheduler, lease, provider auto-switching and dashboard. Track these as subsequent separate work only when selected.
