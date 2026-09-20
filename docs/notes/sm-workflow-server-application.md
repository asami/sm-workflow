# sm-workflow Server Application Architecture

status = proposed
date = 2026-09-20
target_phase = 2

## Decision

sm-workflow will be built as a full CNCF/Textus server application, using the same general architecture as textus-art-scene, and will also remain executable as a one-shot component through the CNCF launcher/CLI.

## Application structure

The long-lived process owns expensive/repeated runtime setup: component bootstrap, datastore/provider lifecycle, Workflow definition/profile catalog, View indexes, and stable reference/master projections. This makes MCP calls and Web observation cheap and avoids reconstructing application context on every Skill turn.

CNCF Workflow runtime remains the Workflow authority. sm-workflow uses Aggregate/Entity only for application-owned durable concepts and View for query projections. It must not mirror current Workflow state into a second mutable aggregate.

## Resident data

Candidate resident data includes workflow/profile definitions, ReasoningLevel mapping policy, project/repository descriptors, and operation/presentation metadata. Each resident structure must identify its durable/configuration authority and refresh/version rule.

## Interfaces

- MCP: normal Skill integration.
- CNCF Web Workflow Management: human observation of all running/completed sm-workflow instances.
- launcher/CLI: one-shot invocation when a server is not desired.
- typed Scala/application API: internal composition/testing.

All interfaces call the same application service.

## CLI compatibility

One-shot execution bootstraps the component, resolves the configured durable store, loads required reference data, invokes one registered typed Operation, emits the canonical response, and terminates. It therefore remains suitable for CI and shell automation without requiring an MCP/server process.
