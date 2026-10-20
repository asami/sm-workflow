# Step Review Reduction and Workflow Quality Optimization Plan

Date: 2026-10-20

## Context

sm-workflow Phase 6.3 produced the first practical workflow route, and Phase 6.4
is currently being developed by using that 6.3-delivered sm-workflow itself.
Phase 6.4 therefore provides a useful real-development baseline for the current
quality workflow.

The current GoalPhase progression requires semantic AI review at Step close.
In practice this has two costs:

1. AI review is expensive and adds a nondeterministic review/fix/re-review loop
   to every Step.
2. More importantly, per-Step AI review tends to find increasingly local or
   speculative improvements. Repeating that behavior can expand implementation,
   tests, state and defensive machinery beyond what the Plan actually needs.
   This review-induced scope expansion can make the final system larger and more
   complex without proportional product value.

CNCF/Textus has an unusually strong deterministic implementation harness:
Scala's type system, pure-functional style, monadic composition, the UnitOfWork
DSL/effect boundary, focused executable tests, CAR lint, and the planned Scala
source lint. A successful full compile already checks a substantial structural
and type-level proposition set; focused tests then check the changed behavioral
contract.

## Working hypothesis

Ordinary Step acceptance does not need mandatory semantic AI review when the
deterministic harness is sufficiently strong.

The target model is:

```text
PLAN
  |
  +-- STEP
  |     implementation
  |       -> full compile + focused test
  |       -> Scala lint
  |       -> CAR lint
  |       -> FIX when needed
  |       -> affected re-validation
  |       -> optional semantic Step Review when actually needed
  |       -> Step commit
  |
  +-- STEP ...
  |
  +-- RELEASE
        -> integrated/full test
        -> Scala lint
        -> CAR lint
        -> cbd-support Review
        -> comprehensive AI Review
        -> release/review FIX when needed
        -> affected re-validation / scoped re-review
        -> Final
        -> release commit
```

FIX and re-validation remain first-class Step responsibilities. The proposed
optimization removes only the rule that every successful Step must pass a
mandatory AI semantic Review before commit.

`Stage.Review` / `WorkKind.ReviewStep` should remain available. Step Review
becomes conditional/optional rather than being deleted from the workflow.

## Rollout plan

### 1. Complete Phase 6.4 with the current workflow

Do not change the active Phase 6.4 workflow merely to test this proposal.
Complete 6.4 using the Phase 6.3-delivered behavior so that evidence-storage
development remains attributable to its own scope and the current workflow has
a real-development baseline.

Use already available evidence where practical to observe:

- number of Steps;
- number of Step reviews;
- review findings;
- REVIEW_FIX activity;
- source/test growth attributable to review findings;
- findings first discovered at Phase/Release review.

Do not create a heavy new measurement subsystem merely for this comparison.

### 2. Introduce a bounded workflow-optimization Phase after 6.4

Tentative placement:

```text
6.3  initial practical sm-workflow
  -> 6.4 evidence storage, current-workflow baseline
  -> 6.5 workflow quality optimization (candidate)
  -> 7 multi-repository/artifact-overlay development
```

The exact Phase number is provisional until the plan is adopted. Its main
behavioral change should be narrow: a passing deterministic Step no longer
requires STEP_FULL AI review by default.

The normal Step Admission evidence becomes full compile/focused validation plus
the selected Scala lint and CAR lint results. Failed checks retain the existing
FIX/re-validation/convergence behavior.

### 3. Preserve conditional Step Review

Do not initially construct a large rule matrix for when Step Review is required.
Default to no semantic Step Review after deterministic admission, while retaining
the existing review continuation for an explicitly selected semantic judgment,
Human/architecture boundary, or other case where the current workflow determines
that semantic review is actually needed.

Learn additional review conditions from observed Release findings rather than
speculating about every possible risky Step in advance.

### 4. Keep Release semantic assurance strong

The optimization does not remove semantic review from release quality.

Release/Phase closure retains:

- integrated/full validation;
- Scala lint;
- CAR lint;
- cbd-support semantic Review;
- comprehensive AI Review;
- bounded review fixes;
- affected re-validation and necessary scoped re-review;
- Final evidence/closure confirmation.

The Release review should judge the completed Plan as a whole: requirement and
Usage Contract satisfaction, integration across Steps, architectural fit,
unnecessary complexity, duplicate state/mechanisms, overchecking and other
semantic issues that deterministic Step checks cannot establish.

This changes the emphasis from repeated local additive review to whole-result
necessary-and-sufficient review.

## Feedback loop

For findings discovered at Release, classify whether earlier Step detection was
actually needed:

```text
Release finding
  |
  +-- Release detection was sufficient
  |
  +-- should have been caught earlier
        |
        +-- type/model improvement
        +-- focused-test improvement
        +-- Scala-lint rule
        +-- CAR-lint rule
        +-- conditional Step Review case
```

Prefer strengthening deterministic harnesses when a recurring problem can be
expressed mechanically. Reserve Step AI Review for genuinely semantic residuals.

The experiment is successful if Step-review removal does not materially increase
important Release findings while reducing review/fix churn, implementation growth
and workflow overhead. A finding that is safely and efficiently detected at
Release is not by itself evidence that per-Step review should be restored.

## Design principle

The intended direction is:

> Use the compiler, pure-functional/monadic structure, UnitOfWork DSL, executable
> tests and lint as the ordinary Step quality harness. Use AI for semantic
> residuals and integrated Release judgment rather than asking AI to re-evaluate
> every locally valid Step.

This is not a reduction of FIX discipline or Release quality. It is an attempt
to move quality assurance to the cheapest and most deterministic mechanism that
can correctly own each class of problem, while reducing review-induced scope
expansion.
