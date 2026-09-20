# Server Application, MCP, and One-Shot CLI Direction

status=decision
date=2026-09-20
phase=[Phase 2](../../../phase/phase-2.md)

## Decision

After Phase 1 closes the application Workflow executable specifications, sm-workflow will move to a full server-application architecture based on CNCF Phase 88 support.

The model follows textus-art-scene: use normal CNCF ComponentFactory/bootstrap, Aggregate/View facilities, durable datastore integration, and resident master/reference data. Skill integration uses MCP.

At the same time, every admitted operation must remain usable from the CNCF launcher/CLI in a one-shot process. The server is the preferred interactive deployment, not a prerequisite for correctness.

## Why

A persistent server gives sm-workflow a natural home for repeated MCP interactions, resident reference/master data, efficient Views, and the generic CNCF Workflow management UI. One-shot CLI keeps local/CI/debugging usage simple and avoids forcing daemon operation for isolated work.

The two modes share one Workflow runtime and one application service. No server-only Workflow model and no CLI-only Workflow model are permitted.

## Upstream requirement

CNCF Phase 88 must first establish the generic server/one-shot support and inventory reusable ArtScene patterns. CNCF Phase 87 supplies generic Workflow Web management. sm-workflow then acts as the first representative consumer of both.
