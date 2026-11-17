---
name: sm-goal-task
description: Develop one authorized journal or direct-instruction Task through native validation, independent integrated review and selected local commit.
---

## Shared Journal operations

For a concrete Journal recording or lookup purpose, use the
[semantic-entry contract](../../docs/spec/development-journal-semantic-entries.md).
Decision, Issue and Handoff use the delivered public operations; keep immutable
Entry reads, actual references and Workflow acceptance separate.

## Semantic result detail

For a retained worker artifact, use the versioned format in [semantic result submission](../../docs/spec/semantic-result-submission.md). A worker may report concise outcome, summary, declared result type and its result Resource. Direct complete public results remain supported. The normal caller uses `projectSemanticWorkResult` and forwards the lossless public JSON through `resultResource` with the original issued context, result reference and execution evidence. Do not manually unwrap transport envelopes, invent fields or resubmit an accepted result. Projection is a read/format operation, not Submit or acceptance.

## Compact runtime responses

When an issued response contains retained references, follow the shared
[retained response contract](../../docs/spec/native-host-execution.md#retained-responses-and-prepared-imports).
Read only needed exact owner revisions; keep the original issued context and
return contract. A reference or successful detail read is not workflow acceptance.


## Show who is working

Use the shared [AI activity quick start](../../docs/spec/native-host-execution.md#ai-activity-quick-start)
for the renderer location and actual chat forwarding.

Before each authorized subagent spawn/resume, announce `subagent依頼：` with
the request name, task/role, scope and expected result. Follow the shared
[request and result pairing](../../docs/spec/native-host-execution.md#subagent-call-display);
the later generated dispatch/receipt markers do not replace this pre-call notice.

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

Periodic separate-chat progress reporting OFF does not disable these actor
transitions. `Agent Use` and tool stdout alone do not deliver the icon message.

## Native semantic execution owner

Resolve ordinary runtime-issued `sm_workflow_*` work through the
[sm caller contract](../../docs/spec/native-host-execution.md#ordinary-sm-semantic-role-resolution),
preserving its issued context and captured role/profile/effort/disposition and
alternate policy. Do not use `cncf-agent-execution`, its model selector/fallback,
or CNCF Manifest/verifier rules for this resolution, even if retaining an sm
role name. An explicitly authorized independent cncf-* maintenance producer is
separate; its consumer still uses native sm admission. Preserve current workers,
reviews, Session/history/counters and successful evidence; an unnecessary CNCF
reference alone does not require restart, re-review or another approval.


# sm-goal-task

For newly assigned implementation or repair, follow the shared
[implementation handoff](../../docs/spec/native-host-execution.md#implementation-handoff): normally use a fresh subagent
without parent history and state the purpose, approach, scope, acceptance criteria
and expected return. Preserve the issued disposition and any retained continuation.

## Test classification and selection

Follow the [standard-annotation policy](../../docs/spec/native-host-execution.md#validation-cadence-with-standard-annotations).
ADMISSION is the sole custom label; FULL/focused are run scopes. Use standard
Slow for heavy selection and DoNotDiscover for explicit-only suites. Preserve
captured checks/results and use the runtime's supported selection route.

## Waiting for another chat

Default to [active-Goal Luna waiting](../../docs/spec/native-host-execution.md#active-goal-luna-wait)
after authorized cross-chat dispatch. Keep the existing Goal active and wait with
collaboration.wait_agent(timeout_ms=3600000), or the longest duration permitted
by the execution rules. Luna low checks the destination every 120 seconds, using
wait_threads within its 120-second API limit and any shorter execution limit.
Do not duplicate polling, start unrelated work, or use that polling interval as
the parent's timeout. Preserve request, Session, history, repair counts and results.

Parent progress reporting defaults to **OFF**. **動作状況を報告して** /
**動作状況の報告をON** enables reports at 2 -> 4 -> 8 -> 10 minute intervals;
**動作状況の報告をOFF** disables them. Pass progress_reporting and destination title
to the waiter. The report concerns the destination chat's work, not Luna's work.
On a matching PROGRESS while ON, display its supplied commentary once and resume
the same wait; do not query status, acknowledge, run workflow operations or spawn
another waiter. OFF ignores queued progress. Terminal completion/attention is
always returned immediately. Reporting ON consumes parent tokens.

The user may switch ON/OFF during waiting. Send one REPORTING_MODE message with
request_id and progress_reporting to the same child via collaboration.send_message.
Do not interrupt/restart it or change the destination request. Apply OFF at the
parent display boundary immediately; the child applies the message at its next
message boundary. OFF -> ON starts a new two-minute reporting interval; repeated
ON leaves the schedule intact. Follow the shared
[mode selection](../../docs/spec/native-host-execution.md#cross-chat-waiting-mode-selection).

The explicit option **Luna待機なし** / **notification-only** uses the
[notification-only return contract](../../docs/spec/native-host-execution.md#cross-chat-notification-only-return):
end the requester turn after dispatch, with no Luna or parent polling, and resume
on the authorized result reply or human instruction. This is a skill option, not
a new CLI flag. Retain an explicitly selected mode within its stated scope; see
[mode selection](../../docs/spec/native-host-execution.md#cross-chat-waiting-mode-selection).
Do not silently opt out on model unavailability or duplicate an existing waiter.
Neither mode creates/resumes a Goal or overrides an explicit human stop. This
contract does not guarantee zero cost or control client UI/wakeup behavior.

At each send/receive, follow the [separate-chat display contract](../../docs/spec/native-host-execution.md#separate-chat-delegation-display):
show **💬 📨 別チャットへ依頼中** at the actual send outcome, separately from
parent-wait indicators. Name the destination/sender and request, then show this caller's outstanding
requests from retained context. Distinguish result receipt from runtime adoption;
include the waiting list in the dispatch turn's final handoff. No status queries
or new display storage are needed.

Use the [public runtime contract](../../docs/spec/goal-task-runtime.md) and
[existing-chat delivery contract](../../docs/spec/task-fix-delivery.md).
The [native host contract](../../docs/spec/native-host-execution.md) owns actual
execution and the [caller information contract](../../docs/spec/caller-information.md)
owns attributed observations. This skill is a thin caller/semantic adapter.

For a new authorized development deliverable assigned to another chat, route
through [sm-delegate-task](../sm-delegate-task/SKILL.md) and the
[new-request/continuation boundary](../../docs/spec/native-host-execution.md#new-cross-chat-development-requests-and-existing-continuations).
Keep corrections to an existing deliverable in its existing request, and preserve
this Task's own validation, integrated review and commit contract. The destination
runs its DelegateTask, not this skill as a substitute executor. Report an actual
missing route rather than silently reverting to direct-message development.

## AI activity forwarding

Follow [receipt ordering](../../docs/spec/native-host-execution.md#result-receipt-inspection-and-runtime-acceptance):
at the first opportunity after actual result delivery, forward the generated
receipt marker **before** reading result files, inspecting diffs or submitting
to the runtime. Include it in the first update saying the worker has returned.
Then distinguish `内容確認済み` after inspection from `runtime受理済み` after
actual native acceptance. Worker `completed` means only its assigned work is
finished; it does not establish verification success or native acceptance.
Keep `accepted=unknown` until native acceptance is observed. Render receipt
from the delivered message/envelope first; never bundle it after result-file
reads, diff assertions or submission-file preparation in the same script.

Workflow ownership is not active execution. `runtimeControl=sm-workflow` adds
only `進行管理：sm-workflow`; AI dispatch/work/result uses its own marker without
a gear/running prefix. End an actual runtime invocation's interval when its
response returns work to the caller, then show parent/subagent work separately.
Only a separately observed live invocation may retain a runtime progress marker;
a local display-helper command is not such an invocation. No extra polling,
AI call or repeated receipt display.


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
before/after intervals tied to actual calls; child results imply neither native
acceptance nor cancellation of another live call.
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
required diagnostic/disposition facts. Route execution observations by that
Request's retained capture: work with `operatingProfile.dispatch` uses the
[versioned dispatch adapter](../../docs/spec/native-host-execution.md#forward-goalphase-and-bounded-task-logical-dispatch)
for preparation, actual launch/return and `execution_evidence`. Legacy concrete
work keeps its existing `prepareExecution`/`recordExecution` route. Never resolve
pending work from today's host configuration or relabel an observed model from
the selected profile. The caller independently admits genuine evidence, then submits
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
capability documented in the Task contract with the independently admitted
evidence required by its selected choice. Exact covering nonexecution evidence
permits only its declared continuation; accepting unresolved effects requires
genuine Human direction. Missing logs or an unclaimed WorkOrder authorize neither.
Preserve the old issue, actual effect disposition, accepted results and counters.

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

For an actually malformed historical Task WorkOrder, use the public description
and recovery operations in the runtime contract. Preserve its exact target and
original sources. A fresh classified issue is ineligible. ContinueWithCoveringNonexecution
requires independent exact host evidence; AuthorizeOneNewAttemptAcceptingUnresolvedEffects
requires genuine Human direction and keeps UNKNOWN. Neither Available nor missing logs
prove nonexecution. Recovery supersedes only the eligible issue; a separate Advance
owns the corrected issue. Collect a retained disposition before another action.
