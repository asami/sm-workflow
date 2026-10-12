# Dependency Component Boundary and Human Approval

## Context

During development driven by `cncf-goal-phase`, an attempted change to the internals of another component on which the current component depends was detected. The workflow stopped rather than modifying that dependency automatically, leaving the decision to the developer.

This behavior should also be an explicit design principle of sm-workflow.

## Principle

A development goal has a component boundary. Dependencies are normally consumed through their public Usage Contract. Reaching the conclusion that a dependency itself should change is valid analysis, but changing the dependency's internals is not an implicit extension of the current goal.

When work would cross from use of a dependency's Usage Contract into modification of that dependency's internals, sm-workflow must stop automatic continuation and yield to Human Approval.

This is a **Component / Usage Contract Boundary**, not merely a repository or worktree boundary. Components may share a repository or worktree, so filesystem or Git boundaries alone are insufficient.

## Expected behavior

- Using a dependency through its public API / Usage Contract is normal work.
- Inspecting dependency internals may be allowed when needed for diagnosis or understanding.
- Identifying a required or desirable change in the dependency may be reported as a proposal.
- Modifying dependency internals is a boundary crossing and requires explicit developer judgment.
- If approved, the dependency-side change should normally become a separate goal/work unit with its own scope and acceptance criteria, rather than silently expanding the current goal.

## Relation to Acceptance Boundary

The rule should be integrated with the Usage Contract / Acceptance Boundary model.

The intended flow is:

`Usage Contract -> Acceptance Boundary -> implementation -> review`

The boundary should apply throughout the workflow, not only when a write is attempted:

1. **Planning** — detect plans that implicitly require changes inside another component and surface them for developer judgment.
2. **Implementation** — stop/yield before modifying dependency internals.
3. **Review** — detect accidental or unjustified cross-component changes even if they escaped earlier checks.

This supports top-down development from user-facing/use-facing contracts. It prevents a component task from expanding bottom-up into dependency repair, which otherwise risks unbounded scope and later integration rework.

## Design implication

sm-workflow should represent enough scope information to distinguish:

- current target component,
- permitted Usage Contracts,
- dependency components,
- changes inside the current Acceptance Boundary,
- proposed changes outside that boundary.

A boundary crossing is not necessarily an error. It is a **decision point**: automation should preserve the finding and context, then yield control to the developer.

## Reference case

The observed `cncf-goal-phase` behavior is a useful acceptance scenario for sm-workflow:

> While implementing component A, the reasoning process determines that dependency component B should be internally modified. sm-workflow does not modify B automatically. It records the reason and proposed change and yields for developer approval.

This scenario should eventually be usable as a regression/acceptance test for planning, implementation, and review boundary handling.
