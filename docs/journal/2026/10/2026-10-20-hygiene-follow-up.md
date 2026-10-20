# Hygiene follow-up

Non-normative maintenance record. These items do not reopen completed Phases or
change the current Goal, Session, repair counts, or deferred runtime qualification.

## HYG-SBTR-01 — OPEN

- Discovery: 2026-10-20, SBT/runtime review; retained during ordinary reason-separated commits.
- Location: sm-workflow, `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeGoalValidationRecoveryCliSpec.scala` (private fixture identifiers) and `GoalPhaseValidationRecoveryOperationSpec.scala` (protected `_recovery_plan`).
- Evidence: fixture naming differs from the shared private snake_case and protected-member conventions.
- Category/priority/risk: naming, low; inconsistent maintenance conventions.
- Boundary: existing fixture naming is outside the accepted interruption-result repair.
- Follow-up: rename fixture-local identifiers and their mechanical references together; preserve behavioral assertions.
- Later task/commit: not scheduled.

## HYG-SBTR-02 — OPEN

- Discovery: 2026-10-20, SBT/runtime review; retained during ordinary reason-separated commits.
- Location: development-workstation, `common/codex/skills/cncf-workflow-protocol/scripts/test/test_typed_command_executor.py`, existing executor scenario.
- Evidence: an existing unmodified scenario lacks adjacent Given/When/Then annotations.
- Category/priority/risk: specification readability, low; setup and observable expectations are less explicit.
- Boundary: the unchanged scenario is outside the selected execution-contract correction.
- Follow-up: annotate the actual setup/action/assertion boundaries without changing behavior or adding redundant tests.
- Later task/commit: not scheduled.

## HYG-SBTR-03 — OPEN

- Discovery: 2026-10-20, ordinary reason-separated commit review.
- Location: development-workstation, `scripts/test/sm_workflow_launcher_spec.py`, `test_launch_should_select_execve_and_preserve_artifact_host_and_literal_arguments`.
- Evidence: the test name and comment still say execve while the actual assertion checks `--fork=false`.
- Category/priority/risk: stale test wording, low; readers may infer stronger launcher ownership coverage than the assertion provides.
- Boundary: wording does not invalidate the current argument-transport assertion; actual PID ownership remains explicitly deferred.
- Follow-up: align the name and comment with the existing same-JVM launch option, without claiming PID qualification.
- Later task/commit: not scheduled.
