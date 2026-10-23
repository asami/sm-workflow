# Phase 15: Development Workspace Operations

status=planned
Planned: 2026-10-21
Owner: sm-workflow
Dependencies: Phase 7 Development Workspace and Phase 13 recursive catch-up for related functionality

## Goal

Expose coherent user-facing workspace preparation, inspection and dependency update operations.

## User-facing skills / routes

sm-workspace-status, sm-bootstrap, sm-upgrade (candidate)

## Scope and implementation strategy

Use existing workspace graph, Artifact Overlay status/verify and catch-up; start with a thin status skill over typed operations. Bootstrap must respect Cozy project generation ownership; upgrade must distinguish Git updates from provider-owned localJar publication and explicit consumer selection. Do not add a Workspace Profile or parallel configuration/state store.

## Acceptance boundary

A selected multi-repository workspace can be inspected and its actionable blockers explained; additional bootstrap/upgrade routes are selected only when existing operations cannot satisfy real scenarios.

## Planning rules

Deliver one practical representative route first, reusing registered typed Operations, Workflow/Continuation, Git/SBT adapters and common Evidence. A listed candidate skill is not automatically a requirement for an independent workflow or new engine. Keep optional breadth subject to real-use need; preserve existing goal-phase/repository-sync/catch-up responsibilities. No implementation or test completion is claimed.
