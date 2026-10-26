# Phase 14: Development Investigation

status=planned
Planned: 2026-10-21
Owner: sm-workflow
Dependencies: Existing Git, validation, lint, Evidence/Journal and Continuation contracts; no Phase 7/13 completion prerequisite

## Goal

Investigate defects, build/test failures and change impact with grounded evidence before modifying product sources.

## User-facing skills / routes

sm-investigate, sm-diagnose, sm-change-impact

## Scope and implementation strategy

Start from a user question or failing observation; gather bounded deterministic diagnostics first; form/test hypotheses with semantic AI only where needed; distinguish confirmed causes from conjectures; report affected components/repositories and hand off a proposed fix to the owning workflow. Reuse existing lint, validation metadata, logs and Evidence; no duplicate diagnosis engine.

The [standalone fix contract](../spec/standalone-fix-acceptance.md), adopted
2026-10-24, supplies a planned Step-equivalent repair route when repair is also
authorized: focused tests and conditional review, without creating a Phase or
implicit commit. Existing Phase work stays in its current sm-goal-phase Session.
Investigation alone authorizes no source edit, and this integration does not make
standalone FIX completion a prerequisite for Phase 14's read-only acceptance.
Initial Task/FIX delivery is now [Phase 6.7](phase-6.7.md); the remaining
investigation-to-authorized-execution integration is [Phase 17](phase-17.md).
Reuse that route rather than implementing another repair owner in Phase 14.
See the [delivery plan](../notes/standalone-fix-delivery-plan.md).

For an authorized ad hoc development assignment through integrated review and
local commit, hand off to the planned [sm-goal-task](../spec/goal-task.md) route.
It accepts this investigation as one possible input and shares FIX execution;
Phase 14 investigation alone still authorizes neither edits nor commits.

## Acceptance boundary

One real defect investigation and one change-impact investigation produce actionable evidence and a proposed-fix handoff without unintended source changes. The handoff identifies the existing Phase owner or the planned standalone repair route as applicable; it does not itself execute or accept the fix.

## Planning rules

Deliver one practical representative route first, reusing registered typed Operations, Workflow/Continuation, Git/SBT adapters and common Evidence. A listed candidate skill is not automatically a requirement for an independent workflow or new engine. Keep optional breadth subject to real-use need; preserve existing goal-phase/repository-sync/catch-up responsibilities. No implementation or test completion is claimed.
