# Phase 26: Dual CLI Operation with Codex and Claude Code

Status: planned
Planned: 2026-11-21 (Asia/Tokyo)
Owner: sm-workflow development
Checklist: [Phase 26 acceptance](phase-26-checklist.md)
Related: [server architecture](../notes/sm-workflow-server-application.md), [Phase 6](phase-6.md), [Phase 9](phase-9.md), [Phase 22](phase-22.md), [Phase 24](phase-24.md), [Phase 25](phase-25.md)

## Current scheduling — 2026-11-24

Schedule Phase 26 after Phase 13 and before Phase 27, following the validation and development-support groups. Phase 25's applicable delivered Task API contract and the CLI Continuation boundary remain unchanged. See the [current roadmap](README.md#adopted-development-priority--2026-11-24). Earlier scheduling notes below are historical.

## Goal

Enable one developer to use Codex CLI and Claude Code CLI interchangeably against **one authoritative sm-workflow Server** through the same sm-workflow CLI, while preserving existing Workflow state, Continuation, review and Admission semantics. The first deliverable is practical dual-client operation, **not** an autonomous worker fleet, generalized scheduler, provider benchmark, or dashboard.

## Architecture and ownership

- CNCF Workflow runtime on the sm-workflow Server owns Workflow authority and durable state. Do not create a second mutable workflow store.
- Each interactive Codex/Claude Code session invokes the same public sm-workflow CLI/Operation contract as a client of that server. Confirm real remote routing and authentication, not merely local one-shot behavior.
- A client is not automatically a resident worker. Multi-wait, worker registry, leasing, autonomous polling and server-initiated dispatch are **later optional extensions**, not Phase 26 acceptance requirements.
- sm-workflow owns development Workflow, Continuation, candidate/Admission and workspace ownership. textus-ai owns reusable provider API credentials, billing and provider selection where Phase 25 applies; CLI subscription authentication stays in each CLI's supported host environment.
- Textus Development Center will later compose live workflow progress with GitHub Projects/Issues/PRs; Textus Control Center observes server/subsystem operations. Neither UI is required to complete this Phase.
- Textus Orchestrator may later coordinate autonomous workers; do not introduce a duplicate scheduler now.

## Delivery slices

### D1 — Inventory and common client connection

1. Inventory existing CNCF server, `scripts/run-server.sh`, CLI `command --baseurl`, MCP and existing Codex skill/Continuation behavior. Verify actual supported routes.
2. Define one canonical server endpoint/configuration and a common, provider-neutral CLI invocation contract for both clients; avoid separate Codex/Claude workflow semantics.
3. Confirm server start, health, auth boundary, stable Workflow IDs, and persistence/restart behavior needed for this scenario. Explicitly hand off generic unresolved recovery to Phase 22 rather than silently claiming it works.

### D2 — Claude Code adoption and two-client interoperability

1. Supply a minimal Claude Code integration instruction/skill using the existing sm-workflow CLI public contract. Reuse the Codex integration rather than duplicating the workflow engine.
2. Run a real Codex CLI session and a real Claude Code CLI session against the **same server**, each performing a bounded normal Workflow operation with canonical response and traceable session/actor attribution.
3. Demonstrate sequential cross-client handoff: start/advance or inspect from one client, then continue/inspect from the other without resetting Workflow, replaying accepted effects or losing authoritative state.
4. Handle stale contexts, unauthorized operations, conflicting updates and workspace ownership by normal server-side Admission/concurrency rules; no direct datastore editing.

### D3 — Practical operation and safety

1. Document installation, authentication, startup, client configuration, troubleshooting and recovery of interrupted CLI sessions on one host; optional second-host verification if supported.
2. Confirm both CLIs' current supported non-interactive/interactive invocation and billing/auth mode before relying on subscription allowances. **Never equate CLI plan allowances with free API credits.** No automatic paid API fallback; no unsupported credential sharing or circumvention of provider limits.
3. Retain evidence of actual commands, provider/actor identity, server Workflow IDs, responses, errors, cost/billing mode where verifiable, and bounded connected tests.
4. Review and close with normal sm-workflow Admission/validation; do not mark tests or integration accepted without actual evidence.

## Explicit non-goals

- Automatic scheduling between idle Codex and Claude workers; IoC multi-wait/long polling/lease/heartbeat.
- Quota-aware autonomous provider switching or pooled API credits.
- Development Center/Control Center UI implementation.
- Full remote multi-host deployment, HA or general production operations.
- Reimplementing Phase 9 routing, Phase 24 API-credit use, Phase 25 textus-ai API adapters, or Phase 22 generalized runtime stabilization.

## Exit criteria

A developer can start the existing sm-workflow Server, connect **both Codex CLI and Claude Code CLI through sm-workflow CLI**, perform real operations on one shared Workflow, and safely hand off the next step between them. The runbook and connected evidence make it reproducible. Subsequent autonomous multi-wait and dashboard integration remain independently planned.

## Scheduling

Follow [Phase 13](phase-13.md) in the [current development priority](README.md#adopted-development-priority--2026-11-24). Deliver the smallest practical dual-CLI interoperability slice using existing server/CLI foundations to support [Phase 27](phase-27.md). No Phase/Session is started by this plan.

## Historical scheduling decision — 2026-11-22

Adopted execution order places Phase 26 **after Phase 23 and before Phase 27**, following Phases 24, 28 and 18. This delays common Server/multi-CLI architecture work until strategic convergence is strengthened. Keep Phase 26's existing functional scope and acceptance unchanged. Phase 25 is deferred; inspect concrete provider/Task API prerequisites and address only required gaps through normal owners. No Session starts from this planning change.

## Historical prerequisite update — 2026-11-22

Schedule Phase 26 after **Phase 25** (and Phase 23), before Phase 27. Consume Phase 25's accepted generic Task API/provider contract where applicable; preserve CLI-agent Continuation semantics rather than treating CLI as stateless API inference. Do not assume Phase 25 completion without evidence. This supersedes older scheduling-only notes; scope and acceptance unchanged.
