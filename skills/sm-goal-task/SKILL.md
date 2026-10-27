---
name: sm-goal-task
description: Develop one authorized journal or direct-instruction Task through native validation, independent integrated review and selected local commit.
---

# sm-goal-task

## Show who is working

Apply the [activity transitions](../../docs/spec/native-host-execution.md#caller-activity-transitions)
from the first actual work segment, including a resumed turn. Parent-only
investigation, planning, editing or repair needs `actor=parent, event=working`
in the first ordinary progress update for that segment; do not wait for a
subagent call, runtime response or code edit to trigger it.
For a subagent, forward dispatch/resume and paired results, and also forward
`actor=subagent, event=working` when existing tool output or a worker message
establishes active work. The generated **🤖 ✨ AI作業中** indication must not
be replaced by dispatch-only text or an `Agent Use` block. Unknown start is not
active work. Parent work uses the generated **🤖 🛠️ 親AI作業中** indication.
Use the bundled renderer and forward `presentation.commentary` verbatim to
chat: tool stdout alone is not a user-facing commentary message. End the active
indication at actual waiting/result/control-transfer boundaries in the ordinary
update; waiting alone is not parent work. Do not repeat unchanged status or
create extra AI calls, monitoring or business Operations for these indications.

Use the [public runtime contract](../../docs/spec/goal-task-runtime.md) and
[existing-chat delivery contract](../../docs/spec/task-fix-delivery.md).
The [native host contract](../../docs/spec/native-host-execution.md) owns actual
execution and the [caller information contract](../../docs/spec/caller-information.md)
owns attributed observations. This skill is a thin caller/semantic adapter.

## AI activity forwarding

At each existing authorized subagent dispatch/resume return and actual result
handler, pass only actual known metadata through the bundled
`native-goal-command.py` `observe_ai_activity` API (or its finite local
`ai-activity --observation /absolute/input.json` transport). Forward the returned
`presentation.commentary` **VERBATIM** to chat; retain its `activityContext` in
the ordinary continuation context and reuse it for the actual paired result.
Follow the [program-generated AI activity contract](../../docs/spec/native-host-execution.md#program-generated-ai-activity).
Do not compose AI markers, infer model/start/success/native acceptance or make
display metadata a work gate. Empty resume acknowledgements retain identity but
prove no start. Feed already available progress, actual parent work/wait and
authorized existing-chat dispatch/results (with the actual chat title) through
the same API when applicable. Keep ongoing runtime control and existing gear
before/after intervals intact; child results never imply parent handback.
Report buffered or failed delivery honestly in the normal update. Add no AI
call, agent, polling, dedicated wait, display ledger or sm-workflow command for
presentation.

Adopt an explicitly authorized objective, repository, literal owned paths, selected
checks, ordered conditions and original source references as `GoalTaskInput`. No Phase
management files are required. Freeze that input; later suggestions do not expand
scope. Existing Phase repair stays in its current Session. Preserve other edits.

Keep the target repository distinct from the sm-workflow runtime host. Follow
the [runtime configuration boundary](../../docs/spec/native-host-execution.md#runtime-configuration-boundary):
Cozy and other development targets need no local `.textus/config.json` or
`assembly.yaml` merely to use this skill. Missing fixed filenames alone neither
establish a setup gap nor authorize bypassing native validation or commit.

The authorized caller uses `NativeWorkflowPreparationService.prepareGoalTaskStart` to prepare
the selected import; a trusted host separately provisions actual selection and
authority. Start through `GoalTaskService.startGoalTask` with that same input and selected participant,
selectionId and idempotencyKey. Preparation, authentication and a pasted reference
are not Human consent. Missing host/check/Git registration is a concrete setup gap.
Never substitute a GoalPhase, private store access or a caller-owned executor.

Use `GoalTaskService.advanceGoalTask` on the retained Session. Native runtime owns candidate capture,
SBT selection/execution, evidence, diagnosis, repair limits, waiting and closure.
Do not run SBT, build a command runner, select extra tests, poll, reset counts or
replay an uncertain effect. Information requests use the assigned finite native
information operation and existing caller tools. No periodic AI status calls.

For actual issued semantic work, consume the complete work request and
`issuedWorkContext`, including adopted conditions, candidate, prior review,
original sources, failure and repair counts. Perform only the issued scope;
Review is read-only and independent of every implementation/repair worker.
Return `GoalTaskWorkResult` with actual findings, conditions, unresolved limitations and
required diagnostic/disposition facts. The caller records genuine execution
observations through existing `prepareExecution`/`recordExecution`, then submits
the actual result/reference/evidence and the unchanged `issuedWorkContext` from
that exact Request through `GoalTaskService.submitGoalTaskWorkResult`. Never
reconstruct or refresh this binding for a delayed result. A Task with committed
recovery history requires it on every later Submit, including after reopen and
later checkpoints; a late old binding is rejected. It correlates the result and
does not prove worker identity or independent review. Submit closes that
WorkOrder; a separate native Advance owns validation and request acceptance.
A semantic return is not native acceptance.

For an already selected Task, the deployed [finite caller](../../docs/spec/native-goal-command-task.md#retained-goaltask-transport)
can transport Advance, execution observations, review preparation and completion.
Use the original Request and Task family; its `start` action is GoalPhase-only.
Display actual `Submitted` and later `Incomplete` separately from acceptance.
If an issued Task is malformed, use only the installed public describe/recover
capability documented in the Task contract, with genuine admitted Human evidence.
Missing logs or an unclaimed WorkOrder do not authorize a replacement. Preserve
the old issue, UNKNOWN effects, accepted results and repair counters.

Task completion requires native integrated independent Review and actual scoped typed commit. No-change, commit failure and UNKNOWN remain distinct; preserve unrelated edits.

For another existing chat, obtain genuine direction for the exact destination,
objective/effects and result return unless already authorized. Retain distinct
requester and destination identities. Send through the existing authorized chat
tool; do not create a chat service or polling loop. Use public
`prepareTaskFixDelivery` for the unchanged actual destination response and its
real source artifact, and `prepareTaskFixAdoption` for the requester's explicit
adopt/defer/reject disposition. These passive preparations authenticate nothing
and mutate no Session. The requester consumes the attachment through its current
supported workflow, checks affected integration and supplies its own actual
semantic result. Return commit versus uncommitted truth and remaining gaps.
Installed and cross-chat acceptance require actual retained evidence; source
fixtures, dispatch acknowledgement and tool exit zero do not establish them.
