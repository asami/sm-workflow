# Phase 13: Recursive Repository Catch-up

status=planned
Planned: 2026-10-21
Depends on: Phase 7 shared dependency graph and accepted Git/Continuation contracts
Owner: sm-workflow

## Goal

Provide a separate `sm-repository-catchup` workflow to bring a selected local development repository and its recursively registered local development repository dependencies up to date with (a) GitHub configured tracking refs or (b) same-machine local main. Do not redefine `sm-repository-sync`, whose purpose is remote convergence including non-force push.

## Scope and work items

- P13-01: Consume Phase 7's dependency model with independent `localRepository`, `localJar`, `localProject` properties. Resolve explicit local development repositories recursively with visited-set/cycle handling and stable dependency-first execution; do not discover arbitrary library dependencies by filesystem proximity.
- P13-02: Implement typed target selection `remote`, `local-main`, and explicit `all` (remote then local main). Snapshot source refs and local branches before mutation. No implicit push, force reset, or destructive cleanup.
- P13-03: Support fast-forward and explicit rebase integration, with safety gates for dirty worktrees, shared branch history and conflicts. Never silently rewrite shared history.
- P13-04: Provide a first-class merge route preserving history. Use Human-in-the-Loop Continuation for conflicts, authority decisions and optional pre-commit approval; allow continue/cancel and validate resolution before committing.
- P13-05: Provide dry-run/plan, per-repository outcome and blocked/partial result reporting. Reuse existing typed Git operations, permissions, workspace mutation lease and evidence services; do not build another Git executor.
- P13-06: Verify recursive producer/consumer scenarios, local-main-only offline operation, remote operation, diverged histories, merge yield/resume, rebase safety, cycles, and dirty-state refusal.

## Dependency artifact boundary

`localJar` is provider-controlled: only the producing repository builds, validates, publishes and updates its local artifact. Consumers resolve the explicitly published version. Catch-up never implicitly rebuilds or republishes producer JARs; `localProject` is an independent, explicitly selected source-coupling option. Preserve Phase 7's per-consumer selection and development-scoped artifact repository isolation.

## Acceptance

A selected root and its registered development dependencies are brought to the chosen target without losing uncommitted work or rewriting shared history unexpectedly. Human merge decisions can suspend and resume through the common Continuation protocol. Partial/blocked results are truthful, and artifact publication is unchanged unless separately requested. No implementation or tests are claimed by this plan.

## Workspace model clarification (2026-10-21)

The recursive graph is the Phase 7 **Development Workspace** dependency-edge model. `localRepository`, `localJar`, and `localProject` describe each consumer-to-provider relationship, not global provider properties. Only registered `localRepository` edges are traversed for catch-up. Do not introduce a separate Workspace Profile configuration or infer Git dependencies from `localJar`/`localProject`. Artifact selection remains explicit and provider publication remains independent.
