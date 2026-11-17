# Phase 15: Development Workspace Operations

status=planned
Planned: 2026-10-21
Owner: sm-workflow
Dependencies: [Phase 7 Development Workspace](phase-7.md) and [Phase 21 recursive catch-up](phase-21.md) for related functionality

Catch-up allocation restored 2026-11-16: Phase 21 retains the former Phase 13
catch-up plan. Current Phase 13 remains AI usage accounting and is not the
catch-up dependency. This reference correction preserves the early initialization
slice and broader workspace scope; it selects no implementation or delivery slot.

Early-slice amendment (2026-10-24): single-project sm-workflow initialization
depends only on the installed CLI and existing CNCF resource/configuration
facilities. It does not wait for the broader dependencies above. See
[initializeProject specification](../spec/project-initialization.md),
[INIT-1–4 delivery/checklist](../notes/project-initialization-delivery-plan.md) and
[English handoff](https://github.com/asami/sm-workflow/blob/9debf6484e3e16e6739b83688a30a814772c7b29/docs/journal/2026/10/2026-10-24-project-initialization-handoff.md).
Deliver a minimal entry callable without project configuration, then GoalPhase
setup and existing-installation reuse. Preserve live Sessions and overrides;
initialization is separate from Start and does not regenerate the product project.

[Phase 19](phase-19.md) consumes this bounded initialization delivery in a clean
Docker HEAVY suite. It owns installation/customization qualification, not another
bootstrap implementation; its broader matrix is not an INIT completion gate.

## Phase 8 ordinary-host startup integration — 2026-11-12

[Phase 8](phase-8.md#program-owned-host-initialization-and-evidence-setup--2026-11-12)
selects the observed manual CommonEvidence setup gap as an operational repair.
Reuse this same early initializer and INIT-1–4 ownership for typed defaults,
admitted storage/File binding preparation and existing-installation reuse; do
not implement or qualify a second initialization engine. Deliver the needed
capability without waiting for broader Phase 15 workspace or Phase 19 coverage.
Existing INIT IDs/statuses and active installations remain unchanged.

## Goal

Expose coherent user-facing workspace preparation, inspection and dependency update operations.

## User-facing skills / routes

sm-workspace-status, sm-bootstrap, sm-upgrade (candidate)

## Scope and implementation strategy

Use existing workspace graph, Artifact Overlay status/verify and [Phase 21 catch-up](phase-21.md); start with a thin status skill over typed operations. Bootstrap must respect Cozy project generation ownership; upgrade must distinguish Git updates from provider-owned localJar publication and explicit consumer selection. Do not add a Workspace Profile or parallel configuration/state store.

## Acceptance boundary

The early slice demonstrates fresh initialization through the actual installed
entry and reuse of an existing installation without resetting Session/history or
validation. INIT-A01–08 track this separately from the broader workspace scope.

A selected multi-repository workspace can be inspected and its actionable blockers explained; additional bootstrap/upgrade routes are selected only when existing operations cannot satisfy real scenarios.

## Planning rules

Deliver one practical representative route first, reusing registered typed Operations, Workflow/Continuation, Git/SBT adapters and common Evidence. A listed candidate skill is not automatically a requirement for an independent workflow or new engine. Keep optional breadth subject to real-use need; preserve existing goal-phase/repository-sync/catch-up responsibilities. No implementation or test completion is claimed.
