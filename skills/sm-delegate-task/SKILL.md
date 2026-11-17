---
name: sm-delegate-task
description: Deliver one authorized development request to an available existing chat with native validation, local commit, selected publication and attributable result adoption.
---

## Shared Journal operations

For a concrete Journal recording or lookup purpose, use the
[semantic-entry contract](../../docs/spec/development-journal-semantic-entries.md).
Decision, Issue and Handoff use the delivered public operations; keep immutable
Entry reads, actual references and Workflow acceptance separate.

## Compact runtime responses

When an issued response contains retained references, follow the shared
[retained response contract](../../docs/spec/native-host-execution.md#retained-responses-and-prepared-imports).
Read only needed exact owner revisions; keep the original issued context and
return contract. A reference or successful detail read is not workflow acceptance.


## AI activity in chat

The owning caller follows the shared [AI activity quick start](../../docs/spec/native-host-execution.md#ai-activity-quick-start)
from its first work update and on resumed work. Forward generated parent/subagent
work, wait and result markers to chat; `Agent Use` or tool stdout alone is
insufficient. Reuse the existing renderer and actual observations. Periodic
separate-chat reporting OFF does not disable these transitions. This display
step adds no execution, delegation or acceptance authority.

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


# sm-delegate-task

For the explicitly human-selected special sm-workflow maintenance route where
the destination executes with cncf-* skills, use
[cncf-delegate-task](../cncf-delegate-task/SKILL.md). This skill applies when the
destination executes through sm-workflow; the requesting Phase retains its
normal public continuation and acceptance in either case.

In the destination chat, for new local implementation work, follow the shared
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
requests from retained context. Follow [receipt ordering](../../docs/spec/native-host-execution.md#result-receipt-inspection-and-runtime-acceptance):
show actual receipt before artifact inspection, then distinguish content inspection
from actual native acceptance. Worker completion alone is not runtime acceptance;
keep it unknown until observed. A Workflow-owned AI request is not a live
sm-workflow invocation: use the AI/chat marker with optional `進行管理：sm-workflow`,
not a gear/running prefix. End the actual call interval on response and keep any
separately observed concurrent runtime activity distinct. Distinguish result receipt from runtime adoption;
include the waiting list in the dispatch turn's final handoff. No status queries
or new display storage are needed.

Use the [delegation contract](../../docs/spec/delegate-task.md),
[finite public caller contract](../../docs/spec/native-delegate-task-caller.md),
[native execution contract](../../docs/spec/native-host-execution.md) and
[runtime publication contract](../../docs/spec/runtime-owned-local-publication.md).
This is a thin semantic/caller adapter to an independent runtime entry.
It is the standard route for new authorized development deliverables sent to an
existing chat. Apply the [new-request/continuation boundary](../../docs/spec/native-host-execution.md#new-cross-chat-development-requests-and-existing-continuations):
continue corrections to the original deliverable in its existing request; use
this entry for a distinct purpose or acceptance obligation. Do not duplicate
ongoing legacy work or accept a direct development message as native admission.
Missing setup is reported concretely, not silently bypassed with another skill
or a standalone execution route.

The owning caller invokes the installed CLI directly with first-attempt scoped
escalation. Use the native host's program-generated activity presentation and
forward `presentation.commentary` to chat. Confirm the selected public operations
in the installed runtime; source documentation or skill installation alone does
not establish their availability. Report a missing operation as a runtime setup
gap and retain the existing Session instead of substituting another workflow.

Before each authorized subagent spawn/resume, announce `subagent依頼：` with
the request name, task/role, scope and expected result. Follow the shared
[request and result pairing](../../docs/spec/native-host-execution.md#subagent-call-display);
the later generated dispatch/receipt markers do not replace this pre-call notice.

Adopt the actual authorized objective, repository, literal owned paths, ordered
conditions, checks, source references and complete `DelegateTaskContract`.
Retain distinct requester, destination and return identities, Component, role,
permitted effects and exclusions. Missing authority is a concrete boundary;
neither request text nor passive preparation supplies consent. Use an available
existing destination chat through its authorized transport; no new chat, queue,
busy-chat interruption or polling controller is implied.

Requester and destination have different host requirements. The requester
retains destination checks and publication as delivery requirements, uses its
own read-only repository observations and issues transport work only. It does
not need a destination checkout, executor profile or executable check/publication
bindings. The destination requires its actual logical origin/dispatch policy or
complete concrete mapping/origin pair and the execution bindings for its adopted
scope. Partial supplied configuration is invalid; do not invent executor facts.

Use `NativeWorkflowPreparationService.prepareDelegateTaskStart`, then the
independently admitted `DelegateTaskService.startDelegateTask` and ordinary
`advanceDelegateTask`. Preserve the actual Session and issued context on re-entry.
For issued semantic work, perform only its owned scope and return the declared
`DelegateTaskWorkResult`; independent review remains read-only and independent.
Use the exact Request's retained route: `operatingProfile.dispatch` uses the
[versioned dispatch adapter](../../docs/spec/native-host-execution.md#forward-goalphase-and-bounded-task-logical-dispatch)
for actual launch/return and `execution_evidence`; legacy concrete work keeps
its normal execution operations. Independently admit genuine evidence and
submit it with the original `issuedWorkContext`, actual result and reference to
`submitDelegateTaskWorkResult`. A semantic return is not native acceptance.

Keep every adopted condition ID and its original order. For new requests,
`submissionConditions` selects which conditions must be true at submission;
an omitted or empty list defers all to final `DeliveryAcceptance`. Historical
absent timing remains strict. Correct an eligible pending initial issue only
through the public description/correction route and actual Human selection.
Read-only DeliveryAcceptance evaluates retained delivery evidence; it executes
no checks, commit or publication and cannot request repair. See the
[condition timing contract](../../docs/spec/delegate-task.md#initial-condition-timing-and-pending-legacy-scope-correction).

Runtime owns validation selection/execution, diagnostic recovery, counters,
integrated acceptance, local commit and required `publishLocal`. Do not call a
sibling skill as an executor, run SBT/publication, add FULL merely for delegation,
create receipts or replay unknown effects. Standalone FIX retains its original
uncommitted completion contract. Observe missing capabilities through the issued
public route rather than building a second runner or control engine.

For repair qualification, apply [same-scope engineering continuation](../../docs/spec/native-host-execution.md#same-scope-engineering-continuation--2026-10-30).
Reuse covering authority for changed candidates and necessary checks; do not add
per-attempt consent. Present an actual runtime stop with its evidence, distinguish
coarse failure status from convergence evidence, and correct a producer gap
without fabricating Human direction. Preserve explicit user limits and consumer
Session exclusions.

Record actual dispatch/result/return using
`DelegateTaskDeliveryService.observeDelegateTaskDelivery`. Requester adoption
requires `describeDelegateTaskAdoption` and independently admitted exact adoption
evidence. A transport acknowledgment, local commit, publication, return, artifact
attachment and actual consumer activation are separate facts. Report their actual
references and remaining limitations. Source fixtures never establish a real
cross-chat delivery or consumer check. Collect retained results after interruption;
do not re-Start, replay accepted Submit or reset history/counters.
