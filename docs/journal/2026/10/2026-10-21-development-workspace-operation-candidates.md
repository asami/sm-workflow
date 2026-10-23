# Development Workspace operation candidates (2026-10-21)

Status: reference / candidate backlog; **not committed Phase acceptance scope**

## Context

Phase 7 defines the Development Workspace as the single development-environment configuration: a Project/Repository/Worktree graph with consumer-to-provider dependency edges carrying independent `localRepository`, `localJar`, and `localProject` properties. There is **no separate Workspace Profile** configuration layer. Artifact Overlay provides explicit producer publication, immutable selection and consumer binding. The producer owns building/validating/publishing/updating localJar; consumers resolve selected published artifacts only. Phase 13's recursive catch-up traverses registered localRepository edges, independently of localJar publication.

## Candidate operations and capabilities

| Candidate | Intent | Tentative fit | Priority |
| --- | --- | --- | --- |
| Dependency Status | Show per-edge local checkout, branch relation, selected artifact and source-project binding | Phase 7 | High |
| Dependency Impact | Identify affected consumers, generation, focused validation after a dependency or selection change | Phase 7 | High |
| Artifact Freshness | Display published artifact provenance (source revision, publication version/time) separately from current Git HEAD; informational only, never auto-publish | Phase 7 | High |
| Workspace Catch-up | Apply Phase 13 catch-up to a selected root and its registered localRepository dependency closure | Phase 13 | High |
| Dependency Build | Explicitly request ordered producer builds, respecting producer ownership and approval; not an implicit consumer-side effect | Future, only if observed need | Medium |
| Dependency Diagnostics | Explain missing/conflicting local artifacts, invalid selections, project binding and resolver errors | Phase 7 candidate | Medium |
| Workspace Snapshot | Record selected repo commits and immutable artifact selections for reproducibility, without bulk copying or integrity machinery | Future, only if observed need | Medium |
| Workspace configuration variants | If real use needs machine-specific variants, evaluate extensions **within** Development Workspace, not a separate Workspace Profile | Future, only if observed need | Medium |

## Design constraints

1. Candidates are **reference information** for Phase 7 and Phase 13, not new mandatory implementation, tests, review cycles or acceptance gates.
2. Reuse existing typed workspace graph, Artifact Overlay publication/selection/status/verify, Git operations, validation metadata and Evidence/Journal. Avoid duplicate state stores or overlapping Operations.
3. Git catch-up/sync must not implicitly rebuild, republish or reselect localJar; source revision and published artifact version remain distinct.
4. A freshness mismatch is an observation, not proof of invalidity or an automatic rebuild trigger.
5. Favor cheap deterministic status/impact diagnostics, using AI only for semantic ambiguity.
6. Evaluate additions through concrete use cases before assigning them to a Phase.

## Candidate next decision

During Phase 7 planning, compare Dependency Status, Artifact Freshness and basic Dependency Impact against existing Overlay status/verify and dependency-aware validation; only add uncovered requirements. During Phase 13 planning, treat Workspace Catch-up as the existing recursive workflow's root-level invocation, not another engine. Defer the rest until evidence of practical need.

## Follow-up decision: ordinary Phase 7 implementation (2026-10-21)

The candidate designation does **not** prevent implementation during Phase 7. Dependency Status, Artifact Freshness, and basic Dependency Impact may be implemented as part of normal Phase 7 delivery whenever they arise naturally from existing workspace graph, Overlay status/verify, provenance and dependency-aware validation. Prefer extending existing typed operations over adding new public operations. Such bounded inclusion needs no separate Phase or approval; do not add an unconditional acceptance gate or new state machinery. Material scope expansion still requires an explicit decision. Workspace Catch-up remains Phase 13's core responsibility.
