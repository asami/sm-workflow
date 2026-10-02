# Skill Work Classification and Execution Routing

Status: proposed specification
Date: 2026-10-03
Related: Phase 5, Phase 6

## Purpose

Define the responsibility boundary between sm-* Skill planning, sm-workflow execution policy, CNCF generic execution requirements, and the concrete execution harness.

## Responsibility model

```text
sm-* Skill
  -> application-specific WorkClassification

sm-workflow
  -> logical execution disposition
     placement + independence + semantic reasoning requirement

CNCF Workflow Protocol
  -> generic ExecutionRequirement / ExecutionEvidence envelope

Execution Harness
  -> concrete provider/profile selection
```

The Skill answers what kind of work this is. sm-workflow answers how it must logically be executed. The harness answers who/what actually executes it.

## WorkClassification

Initial software-development assessment candidates:

- complexity: TRIVIAL | SIMPLE | STANDARD | COMPLEX
- responsibility: PROGRAMMING | ENGINEERING
- contextFootprint: SMALL | MEDIUM | LARGE
- semantic work kind / ReasoningClass

TRIVIAL is semantic, not a line-count contract. A one/two-line local edit is common evidence, but a one-line authorization/domain-semantic change may be non-trivial. TRIVIAL means very small/local, no unresolved design judgment, and locally/easily verifiable.

## Execution disposition

The resolved requirements are orthogonal:

- placement: INLINE | DELEGATED
- independence: OPTIONAL | REQUIRED
- ReasoningClass / abstract reasoning level

INLINE means the current execution participant may perform the work. DELEGATED requires another execution context/provider. REQUIRED independence means the completion must demonstrate the required separation from the producer context.

## Initial Phase 5 policy

- TRIVIAL implementation -> INLINE fast path, regardless of normal concrete model/profile preference.
- bounded implementation -> INLINE when current executor satisfies the resolved profile.
- PROGRAMMING commonly maps to delegated Luna/high.
- ENGINEERING commonly maps to Sol/high; matching bounded parent execution is allowed.
- context-heavy implementation may be DELEGATED to protect parent orchestration context without implying independence.
- REVIEW -> DELEGATED + REQUIRED independence by default; same model/effort does not remove the separation requirement.

No fast path bypasses validation, review, Admission, evidence, authorization, or Workflow acceptance.

## Phase boundary

Phase 5 implements the practical development slice. Phase 6 generalizes provider capability matching, fallback/escalation, execution identity, context/resource routing, and participant classes after operational evidence exists.

CNCF owns the generic ExecutionRequirement/ExecutionEvidence concepts. sm-workflow must not create a parallel generic protocol or move TRIVIAL / PROGRAMMING / ENGINEERING into CNCF.
