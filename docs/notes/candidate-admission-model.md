# Candidate-Admission Model for sm-workflow

- Date: 2026-09-21
- Status: Normative design principle
- Related: [Skill Adapter Design](sm-workflow-skill-adapter-design.md), [Goal Phase Workflow](sm-goal-phase-workflow-definition.md)

## Principle

sm-workflow adopts the **Candidate-Admission Model (CAM)** as its primary Skill/Workflow collaboration model.

> Skill constructs a candidate. sm-workflow admits it. CNCF runtime commits it.

The Skill has semantic autonomy. It may implement, edit planning/management artifacts, validate, review and repair according to its own reasoning. It brings the workspace to the state it believes should be accepted, then submits that candidate to an application Operation such as RequestStepClose.

sm-workflow does not trust the Skill's completion claim as transition authority. It evaluates the candidate against Workflow closure/admission policy.

## Step closure

~~~text
Skill
  semantic work
  program + management files current
  optional proactive review
       |
       v
RequestStepClose(candidate snapshot, evidence)
       |
       v
sm-workflow Admission Evaluation
  sufficient evidence -> deterministic final validation -> commit -> closed
  review gap          -> Review Continuation
  stale evidence      -> focused/full ReReview Continuation
  blockers            -> Repair Continuation
  authority gap       -> Decision
       |
       v
Continuation result
       |
       +----> Admission Evaluation
~~~

The initial close request records durable closure intent. The Skill does not re-request close after every gap. Continuation completion automatically resumes admission evaluation.

## Evidence-driven admission

Review is evidence, not a mandatory procedural slot. A Skill may review proactively and submit scoped/fresh ReviewEvidence. sm-workflow decides whether that evidence satisfies the required closure scope.

A scoped review may be useful but insufficient for a required full review. A fresh full review should be reused. A later repair may stale all or part of previous evidence, causing focused or full re-review according to closure policy.

Admission requirements should be modeled as evidence/authority/validation requirements where practical. Missing requirements become Admission Gaps and are materialized as Continuations, deterministic operations, or Decisions.

## Ownership

Skill:
- semantic candidate construction;
- program and Skill-owned planning/management-file reconciliation;
- proactive semantic work;
- typed semantic Result/Evidence.

sm-workflow:
- application admission/closure policy;
- required evidence scope/freshness;
- repair/re-review convergence policy;
- deterministic validation and commit prerequisites;
- application terminal meaning.

CNCF:
- generic Workflow/Continuation identity and runtime progression;
- durable suspension/resume;
- typed generic Evidence envelope;
- revision/idempotency;
- admitted transition commitment.

## Generalization

GoalPhaseWorkflow is the first proving case. SplitPhaseWorkflow and RepositorySyncWorkflow should apply the same model where a semantic actor constructs a candidate proposal/resolution and the Workflow admits/applies/commits it.

The model is intentionally broader than software development and is expected to generalize to organizational work approval, peer review/publication, editing and knowledge admission.
