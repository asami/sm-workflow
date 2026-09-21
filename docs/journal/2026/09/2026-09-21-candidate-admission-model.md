# Candidate-Admission Model adopted by sm-workflow

- Date: 2026-09-21
- Status: Design decision

The Step-close discussion converged on a general model: the Skill autonomously brings program and management artifacts to the state it considers complete, optionally performing review in advance, and then asks sm-workflow to judge whether that state can be closed.

sm-workflow evaluates the submitted candidate and evidence. If a full review, re-review, repair, validation or authority decision is missing, it returns the corresponding Continuation/Decision. Once the missing result is supplied, admission evaluation resumes automatically. When all requirements are met, deterministic validation/commit closes the Step.

This is named **Candidate-Admission Model (CAM)**.

The model resolves the tension between Skill autonomy and deterministic Workflow governance. It also explains JudgmentAction at smaller granularity: the semantic worker proposes a typed judgment candidate; StateMachine admission/guards retain transition authority.

Phase 1 is updated to make GoalPhaseWorkflow an executable proving case for CAM. The implementation must demonstrate proactive scoped/full review evidence reuse, Admission Gap materialization, no duplicate close request after Continuation completion, and final commit only after admitted program plus management-file state.

Cross-project direction: sm-workflow proves the pattern, CNCF generalizes runtime semantics, Cozy generalizes declarative/ABI support.
