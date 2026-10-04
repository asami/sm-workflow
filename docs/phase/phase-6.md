# Phase 6: Generic Skill Execution Requirement and Provider Selection

Status: planned
Planned: 2026-10-03
Depends on: Phase 5, CNCF Phase 80 minimum execution-requirement extension

## Goal

Generalize the execution-routing model proven by Phase 5 into a reusable Skill/Workflow execution contract without making sm-workflow own provider-specific or harness-specific semantics.

Phase 5 proves the minimum practical development slice. Phase 6 extracts the reusable model for broader sm-* work and for CNCF generalization.

## Boundary

The intended three-layer boundary is:

```text
Skill / application planning
  -> application-specific WorkAssessment / WorkClassification

Workflow application policy
  -> generic logical ExecutionRequirement

Execution Harness
  -> concrete Provider Selection
  -> ExecutionEvidence
```

Input assessment remains application-specific where its meaning is domain-specific. Generic execution requirements and evidence belong to CNCF.

## Scope

1. Reconcile Phase 5 WorkClassification with the generic CNCF ExecutionRequirement contract.
2. Stabilize placement semantics such as INLINE / DELEGATED without referring to ChatGPT/Codex parent/child task topology.
3. Stabilize independence semantics and execution-context identity sufficient to prove producer/reviewer separation.
4. Define Requirement -> ExecutionEvidence conformance and Admission behavior.
5. Generalize context-isolation / context-footprint routing beyond the Phase 5 heuristic; evaluate explicit context-budget evidence only if operationally justified.
6. Define provider capability matching and configuration-driven Provider Selection without making provider identity Workflow transition semantics.
7. Define bounded fallback/escalation behavior for unavailable or insufficient providers.
8. Generalize the contract to Human, remote worker, OpenClaw-like worker, local model, and other execution participants where the same semantics apply.
9. Preserve direct control-plane invocation: Workflow commands themselves do not require a child AI task.
10. Record enough execution evidence to evaluate routing quality, independence, admission rate, retries, latency, usage and cost.
11. Consume CNCF Phase 101 logical Component resource APIs for runtime state/work resources where execution participants require project-local storage; provider/harness code MUST NOT depend on a hard-coded `.textus/sm-workflow` layout.
12. Keep configuration on CNCF's existing hierarchical Component configuration mechanism; Phase 6 MUST NOT create a second configuration lookup/merge model.

## Workflow responsibility boundary

The generic execution contract MUST preserve the software-development responsibility split proven by sm-workflow: GoalPhase may close and locally commit one admitted repository/worktree, while RepositorySync may orchestrate synchronization/convergence across the Project Workspace. ExecutionRequirement generalization MUST NOT merge these application responsibilities into one generic closing operation.

## Genericization rule

Do not move software-development classifications such as TRIVIAL or PROGRAMMING / ENGINEERING into CNCF merely because Phase 5 uses them. Generalize only the resolved execution concepts that are meaningful across applications.

## Executable Specification direction

Demonstrate at least:

- application-specific assessments mapping to the same generic execution requirement;
- INLINE execution by the current participant;
- DELEGATED execution by another provider;
- REQUIRED independence rejecting completion that reuses a prohibited producer execution identity;
- provider replacement without Workflow definition change;
- provider unavailability/fallback without changing semantic WorkOrder identity;
- equivalent generic behavior for an AI worker and at least one non-identical participant class;
- ExecutionEvidence sufficient for requirement conformance and later audit.

## Non-goals

- Moving sm-goal-phase planning semantics into CNCF.
- Treating concrete model/provider names as Workflow guards.
- Building a universal autonomous-agent framework.
- Making context-budget optimization a prerequisite for Phase 5 practical use.
