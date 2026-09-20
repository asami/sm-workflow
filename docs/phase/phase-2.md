# Phase 2: Server Application Runtime and MCP Integration

Status: planned
Planned: 2026-09-20
Depends on: Phase 1, CNCF Phase 88
Related CNCF: Phase 87

## Goal

Turn sm-workflow into a full long-lived CNCF/Textus server application while retaining one-shot launcher/CLI execution of the same typed Workflow operations.

The application follows the server architecture proven by textus-art-scene: canonical ComponentFactory/bootstrap, datastore-backed application state, Aggregate/View use, and resident master/reference data. sm-workflow-specific Workflow semantics remain the Phase 1 application definitions on top of CNCF's common Workflow runtime.

## Server architecture

    Codex Skill
        |
        | MCP
        v
    sm-workflow server
        |
        +-- Workflow application service
        +-- CNCF Workflow runtime
        +-- Aggregate / View
        +-- resident master/reference data
        +-- durable datastore
        |
        +-- generic CNCF Workflow Web Management

The MCP adapter is intentionally thin. It maps MCP tools to the existing typed operations and returns the canonical Handle/Continuation/Result projections.

## One-shot architecture

The server is preferred for normal interactive operation, but is not mandatory.

    CNCF launcher / CLI
        |
        v
    bootstrap sm-workflow component
        |
        v
    same Workflow application service
        |
        v
    typed result / continuation
        |
        v
    clean shutdown

One-shot mode MUST NOT implement a separate Workflow engine, separate persistence model, or alternate operation semantics.

## Scope

1. Adopt CNCF Phase 88 server-application support.
2. Implement long-lived sm-workflow application bootstrap.
3. Model application-owned durable data with canonical Entity/Aggregate facilities where appropriate.
4. Build View projections for Workflow lookup, current activity, history/result summaries, and application-specific navigation.
5. Load stable workflow/profile/project/reference master data at startup and retain it through canonical CNCF Collection/View facilities.
6. Define explicit refresh/version behavior for resident reference data.
7. Add MCP presentation for Skill use.
8. Ensure all MCP tools delegate to typed Phase 1 operations/common Workflow contracts.
9. Preserve launcher-provided one-shot invocation for start, result/decision submission, status/history/result, and other admitted operations.
10. Integrate with CNCF Phase 87 so all sm-workflow instances appear in the generic Workflow management UI and can be filtered by Component = sm-workflow.
11. Add restart and dual-mode Executable Specifications.

## Data ownership

- CNCF Workflow runtime: WorkflowInstance identity, progression, Continuation, lifecycle/history authority.
- sm-workflow Aggregate/Entity: application-specific durable state that is not duplicate Workflow progression state.
- View: derived query/read projection only.
- resident master/reference data: cached/projection form of configured durable/versioned authority.
- Job: optional correlated asynchronous execution; never required for Workflow visibility.
- Skill/MCP: no durable state authority.

## Master/reference data candidates

Initial candidates to evaluate rather than blindly persist include:

- Workflow definition/profile catalog;
- ReasoningLevel mapping policy;
- project/repository descriptors;
- operation/presentation metadata;
- validation/review policy tables.

The implementation must distinguish true master/reference data from live Workflow execution state.

## MCP surface direction

Expose only bounded application operations needed by Skills, conceptually including start, submit work result, resolve decision, inspect current continuation/status, and retrieve terminal result/evidence. Exact tool names and schemas are generated/adapted from the admitted typed operation contracts rather than hand-maintained as a second API.

## Executable Specification requirements

Demonstrate:

- server start -> start Workflow -> semantic continuation -> submit result -> terminal/result;
- process restart with durable Workflow state and resident reference data rebuilt correctly;
- MCP and launcher/CLI produce equivalent typed outcomes for the same admitted operation;
- CLI works with no server process running;
- server and CLI share canonical persistence/identity rules;
- sm-workflow instances are discoverable through generic CNCF Workflow management projections;
- component filtering isolates sm-workflow;
- no Job association is required for visibility;
- related Jobs are correlated when present;
- cached master/reference data can be refreshed without becoming mutable execution authority.

## Non-goals

- A private sm-workflow dashboard.
- Workflow semantics in the MCP adapter.
- Mandatory server installation for local use.
- A second CLI grammar that bypasses CNCF registered Operations.
- Duplicating Workflow state in an application Aggregate solely for convenience.
