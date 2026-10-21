# Phase 16: Development Experimentation

status=planned
Planned: 2026-10-21
Owner: sm-workflow
Dependencies: Existing validation and Evidence/Journal contracts; source/workspace restoration mechanisms only as needed for selected experiment

## Goal

Compare implementation strategies or model/provider choices on equivalent tasks using repeatable evidence.

## User-facing skills / routes

sm-experiment; implementation alternatives and AI-model A/B comparison

## Scope and implementation strategy

Define bounded experiment inputs, evaluation criteria and isolated repository/commit context; run candidate implementations or reasoning engines, collect comparable compile/test/review outcomes and cost/decline observations, and record corpus candidates. Prefer reuse of existing worktrees, workflow and evidence facilities; do not build a generalized experiment platform first.

## Acceptance boundary

A small reproducible A/B experiment produces comparable results and an explicit recommendation without contaminating ordinary development state.

## Planning rules

Deliver one practical representative route first, reusing registered typed Operations, Workflow/Continuation, Git/SBT adapters and common Evidence. A listed candidate skill is not automatically a requirement for an independent workflow or new engine. Keep optional breadth subject to real-use need; preserve existing goal-phase/repository-sync/catch-up responsibilities. No implementation or test completion is claimed.
