# Phase 15: Development Workspace Operations

status=planned
Planned: 2026-10-21
Owner: sm-workflow
Dependencies: Phase 7 Development Workspace and Phase 13 recursive catch-up for related functionality

Early-slice amendment (2026-10-24): single-project sm-workflow initialization
depends only on the installed CLI and existing CNCF resource/configuration
facilities. It does not wait for the broader dependencies above. See
[initializeProject specification](../spec/project-initialization.md),
[INIT-1–4 delivery/checklist](../notes/project-initialization-delivery-plan.md) and
[English handoff](../journal/2026/10/2026-10-24-project-initialization-handoff.md).
Deliver a minimal entry callable without project configuration, then GoalPhase
setup and existing-installation reuse. Preserve live Sessions and overrides;
initialization is separate from Start and does not regenerate the product project.

## Goal

Expose coherent user-facing workspace preparation, inspection and dependency update operations.

## User-facing skills / routes

sm-workspace-status, sm-bootstrap, sm-upgrade (candidate)

## Scope and implementation strategy

Use existing workspace graph, Artifact Overlay status/verify and catch-up; start with a thin status skill over typed operations. Bootstrap must respect Cozy project generation ownership; upgrade must distinguish Git updates from provider-owned localJar publication and explicit consumer selection. Do not add a Workspace Profile or parallel configuration/state store.

## Acceptance boundary

The early slice demonstrates fresh initialization through the actual installed
entry and reuse of an existing installation without resetting Session/history or
validation. INIT-A01–08 track this separately from the broader workspace scope.

A selected multi-repository workspace can be inspected and its actionable blockers explained; additional bootstrap/upgrade routes are selected only when existing operations cannot satisfy real scenarios.

## Planning rules

Deliver one practical representative route first, reusing registered typed Operations, Workflow/Continuation, Git/SBT adapters and common Evidence. A listed candidate skill is not automatically a requirement for an independent workflow or new engine. Keep optional breadth subject to real-use need; preserve existing goal-phase/repository-sync/catch-up responsibilities. No implementation or test completion is claimed.
