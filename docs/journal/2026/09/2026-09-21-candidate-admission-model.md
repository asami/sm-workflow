# Candidate-Admission Model adopted by sm-workflow

- Date: 2026-09-21
- Status: Design decision

The Step-close discussion converged on a general model: the Skill autonomously brings program and management artifacts to the state it considers complete, optionally performing review in advance, and then asks sm-workflow to judge whether that state can be closed.

sm-workflow evaluates the submitted candidate and evidence. If a full review, re-review, repair, validation or authority decision is missing, it returns the corresponding Continuation/Decision. Once the missing result is supplied, admission evaluation resumes automatically. When all requirements are met, deterministic validation/commit closes the Step.

This is named **Candidate-Admission Model (CAM)**.

The model resolves the tension between Skill autonomy and deterministic Workflow governance. It also explains JudgmentAction at smaller granularity: the semantic worker proposes a typed judgment candidate; StateMachine admission/guards retain transition authority.

Phase 1 is updated to make GoalPhaseWorkflow an executable proving case for CAM. The implementation must demonstrate proactive scoped/full review evidence reuse, Admission Gap materialization, no duplicate close request after Continuation completion, and final commit only after admitted program plus management-file state.

Cross-project direction: sm-workflow proves the pattern, CNCF generalizes runtime semantics, Cozy generalizes declarative/ABI support.


## Human Admission surfaces

Candidate-Admission is also the common gate for externally visible or consequential actions such as publication, release, deployment, knowledge admission, and workflow continuation where human authority is required.

Human confirmation should be separable from full review. Many candidates may already have deterministic validation, executable-spec evidence, focused/full review, and sufficient context, leaving only the final authority decision. These candidates can be classified as suitable for a lightweight confirmation surface.

Pixel Watch / Wear OS is the initial reference scenario:
- Confirm: exercise Admission authority and continue the workflow.
- Later/Defer: keep the candidate pending.
- Review: escalate to Smartphone/Fold/Desktop for evidence, diff, editing, or rejection.

The Watch is not expected to perform full review. It is a low-friction Admission authority surface. A suspicious candidate should leave the Watch path and move to a richer review surface.

The same Admission Action semantics must be callable from Watch, Smartphone, Fold, Web, Desktop, email links, or future channels without changing workflow semantics.
