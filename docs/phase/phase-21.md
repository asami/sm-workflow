# Phase 21: Recursive Repository Catch-up

status=planned
Originally planned: 2026-10-21
Restored: 2026-11-16
Depends on: Phase 7 shared dependency graph and accepted Git/Continuation contracts
Owner: sm-workflow
Checklist: [P21 acceptance](phase-21-checklist.md)

## Restoration and allocation

This restores the former Phase 13 catch-up plan from commit
`b0ba5e3d28111e46cbeb29af0609e6a29b41d11b`, immediately before
`9e70eb6ccdf3d780a4a5946d8a0a89b78f098d4f` replaced that file with AI usage
accounting. The original plan was introduced by
`0de0acf74cb50384768b208691375ed934296d37`. Current
[Phase 13](phase-13.md) and its U13 checklist remain unchanged.

The six original P13-01–06 requirements are retained below as P21-01–06,
respectively. This is restoration of planned work, not implementation,
qualification or a change to an active Plan. Phase 21 has no selected delivery
slot and does not change the roadmap's existing execution priority. Preserve
existing Goals, Sessions, accepted work, histories, repair counts and successful
checks. [Phase 20](phase-20.md) retains the AI-consumption summary plan;
recursive catch-up is allocated to Phase 21 so both plans remain available.

## Goal

Provide a separate `sm-repository-catchup` workflow to bring a selected local development repository and its recursively registered local development repository dependencies up to date with (a) GitHub configured tracking refs or (b) same-machine local main. Do not redefine `sm-repository-sync`, whose purpose is remote convergence including non-force push.

## Scope and work items

- P21-01 (restored P13-01): Consume Phase 7's dependency model with independent `localRepository`, `localJar`, `localProject` properties. Resolve explicit local development repositories recursively with visited-set/cycle handling and stable dependency-first execution; do not discover arbitrary library dependencies by filesystem proximity.
- P21-02 (restored P13-02): Implement typed target selection `remote`, `local-main`, and explicit `all` (remote then local main). Snapshot source refs and local branches before mutation. No implicit push, force reset, or destructive cleanup.
- P21-03 (restored P13-03): Support fast-forward and explicit rebase integration, with safety gates for dirty worktrees, shared branch history and conflicts. Never silently rewrite shared history.
- P21-04 (restored P13-04): Provide a first-class merge route preserving history. Use Human-in-the-Loop Continuation for conflicts, authority decisions and optional pre-commit approval; allow continue/cancel and validate resolution before committing.
- P21-05 (restored P13-05): Provide dry-run/plan, per-repository outcome and blocked/partial result reporting. Reuse existing typed Git operations, permissions, workspace mutation lease and evidence services; do not build another Git executor.
- P21-06 (restored P13-06): Verify recursive producer/consumer scenarios, local-main-only offline operation, remote operation, diverged histories, merge yield/resume, rebase safety, cycles, and dirty-state refusal.

## Dependency artifact boundary

`localJar` is provider-controlled: only the producing repository builds, validates, publishes and updates its local artifact. Consumers resolve the explicitly published version. Catch-up never implicitly rebuilds or republishes producer JARs; `localProject` is an independent, explicitly selected source-coupling option. Preserve Phase 7's per-consumer selection and development-scoped artifact repository isolation.

## Acceptance

A selected root and its registered development dependencies are brought to the chosen target without losing uncommitted work or rewriting shared history unexpectedly. Human merge decisions can suspend and resume through the common Continuation protocol. Partial/blocked results are truthful, and artifact publication is unchanged unless separately requested. No implementation or tests are claimed by this plan.

## Workspace model clarification (2026-10-21)

The recursive graph is the Phase 7 **Development Workspace** dependency-edge model. `localRepository`, `localJar`, and `localProject` describe each consumer-to-provider relationship, not global provider properties. Only registered `localRepository` edges are traversed for catch-up. Do not introduce a separate Workspace Profile configuration or infer Git dependencies from `localJar`/`localProject`. Artifact selection remains explicit and provider publication remains independent.

## Optional operation candidates — reference only (2026-10-21)

See [Development Workspace operation candidates](https://github.com/asami/sm-workflow/blob/9debf6484e3e16e6739b83688a30a814772c7b29/docs/journal/2026/10/2026-10-21-development-workspace-operation-candidates.md) for possible Status, Impact, Freshness, Diagnostics and related operations. This is non-normative reference material: no additional implementation, acceptance item or dependency is introduced by this link. Evaluate overlap with existing typed Operations before selecting any candidate. That historical journal's catch-up references to Phase 13 now resolve to this restored Phase 21.

## Candidate adoption clarification (restored 2026-11-16)

Phase 7 may naturally deliver Dependency Status, Artifact Freshness and basic Dependency Impact using its existing Operations; Phase 21 consumes those capabilities when available, without making speculative new Operations a prerequisite. Workspace Catch-up itself is Phase 21's required core function. See the [candidate journal](https://github.com/asami/sm-workflow/blob/9debf6484e3e16e6739b83688a30a814772c7b29/docs/journal/2026/10/2026-10-21-development-workspace-operation-candidates.md).

## Phase 15 consumption

[Phase 15](phase-15.md) reuses this catch-up route for related workspace upgrade
functionality. Its early single-project initializer and Phase 8 startup
integration retain their existing separate dependencies and scope.

## Main integration allocation — 2026-11-17

The user selected retaining main's existing Phase 20 AI-consumption summary and assigning this catch-up plan to Phase 21. The six requirements keep their order as P21-01–06. The original restoration and allocation history remains in Git; no implementation, Phase start or acceptance follows from this numbering correction.
