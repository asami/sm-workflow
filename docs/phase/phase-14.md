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

Start from a user question or failing observation; gather bounded deterministic diagnostics first; form/test hypotheses with semantic AI only where needed; distinguish confirmed causes from conjectures; report affected components/repositories and hand off a proposed fix to sm-goal-phase. Reuse existing lint, validation metadata, logs and Evidence; no duplicate diagnosis engine.

## Acceptance boundary

One real defect investigation and one change-impact investigation produce actionable evidence and a goal-phase handoff without unintended source changes.

## Planning rules

Deliver one practical representative route first, reusing registered typed Operations, Workflow/Continuation, Git/SBT adapters and common Evidence. A listed candidate skill is not automatically a requirement for an independent workflow or new engine. Keep optional breadth subject to real-use need; preserve existing goal-phase/repository-sync/catch-up responsibilities. No implementation or test completion is claimed.
