---
name: sm-delegate-task
description: Deliver one authorized development request to an available existing chat with native validation, local commit, selected publication and attributable result adoption.
---

Follow the [workflow message language contract](../../docs/spec/native-host-execution.md#workflow-message-language).
Default to English for new workflow/AI requests and results. Use Japanese for
user-facing reports and questions. Preserve approvals and logs in their original language and wording.

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

Show the preparation and matching return/error for each
[sm-workflow call](../../docs/spec/native-host-execution.md#chat-progress-indicators).
Show each [subagent dispatch and result receipt](../../docs/spec/native-host-execution.md#subagent-call-display)
as its own chat update.

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

Before selecting this cross-chat route for dependency development, apply the
[delivery choice](../../docs/spec/native-host-execution.md#dependency-development-delivery-choice).
Present local managed worktree (default) and assigned chat when a choice is needed;
reuse an existing selection. A response without mode choices or missing managed
registration does not require a destination chat. Local worktree delivery stays
with the managed runtime route; this skill handles the selected chat method.
Recommend project-private JAR use and offer machine-common JAR use as a
separate explicit selection. For an existing managed assignment, use the shared
[continuation contract](../../docs/spec/native-host-execution.md#continuing-a-managed-dependency-assignment)
for supplemental inventory or eligible retained consumer verification.

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
An explicit `fix-check` uses the [Session checkout binding](../../docs/spec/native-host-execution.md#session-state-and-semantic-returns).
Read [FixCheck attribution and exceptions](../../docs/spec/native-host-execution.md#fixcheck-attribution-and-exceptions) when it returns lint diagnostics.

## Waiting for another chat

For a new default wait after authorized dispatch, the requester itself uses
[parent-owned cross-chat waiting](../../docs/spec/native-host-execution.md#parent-owned-cross-chat-waiting).
Keep the existing Goal active in this turn. Call Codex `wait_threads` directly
with the destination and latest cursor, and re-enter the same wait after a
routine timeout. Once the matching result is available, inspect and admit it
through the existing requester route, then continue the same Goal and Session
without a separate resume prompt. Surface actual Human decisions and honor an
explicit stop. Preserve the request, history, repair counts and checks.

Progress reporting defaults to OFF. Preserve explicit ON/OFF preferences and
honor changes in the parent using the
[mode selection](../../docs/spec/native-host-execution.md#cross-chat-waiting-mode-selection).
An admitted destination Start is recorded by the runtime. Continue with
ordinary Advance even when a progress acknowledgment is omitted, unavailable
or denied. Receipt of a Start notice is optional for the requester's wait.
Explicit **notification-only** / **結果返信で続行** uses the
[reply-based return contract](../../docs/spec/native-host-execution.md#cross-chat-notification-only-return)
when the user chooses to end this turn after dispatch.

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
include the waiting list in any final handoff if the requester turn ends. No status queries
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
the request name, task/role, scope, expected result, and selected model ID and
reasoning effort. Include these in chat before the tool call. Follow the shared
[request and result pairing](../../docs/spec/native-host-execution.md#subagent-call-display);
the later generated dispatch/receipt markers do not replace this pre-call notice.

Adopt the actual authorized objective, repository, literal owned paths, ordered
conditions, checks, source references and complete `DelegateTaskContract`.
Retain distinct requester, destination and return identities, Component, role,
permitted effects and exclusions. Missing authority is a concrete boundary;
neither request text nor passive preparation supplies consent. Use an available
existing destination chat through its authorized transport; no new chat, queue,
busy-chat interruption or polling controller is implied.

Carry the selected JAR mode separately from the delivery choice. For an SBT
consumer, follow the [parent build prerequisites](../../docs/spec/native-host-execution.md#parent-sbt-build-prerequisites)
for that mode. Report missing setup, actual publication and parent build
verification separately.

For a fresh request, use the intent already established during selection to
author `workClassification` with separate `implementation`, `repair` and
`review` members. Use the fields and values in the
[bounded Task work classification](../../docs/spec/native-host-execution.md#bounded-task-work-classification).
Record `mechanical` only when the coding work explicitly has that nature.
The requester passes the assignment through the existing dispatch. The
destination uses it for fresh issuance and follows the retained assignment for
pending work.

At dispatch, include the return chat identity, permitted information, covered
work and a verifiable reference to the original user approval in the handoff.
Carry the same authority into covered follow-up repairs. At return, send the
result directly when all three scope elements still match, including for a new
repair commit or Session. Resolve any uncovered part under the shared
[result reply authority](../../docs/spec/native-host-execution.md#cross-chat-result-reply-authority).

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
absent timing remains strict. For necessary new files within the original
destination purpose and authority, use `extendDelegateTaskOwnedPaths` with the
exact unclaimed issue, paths and rationale before dispatch. Continue with
ordinary Advance. Verify from the caller's dispatch records that this issue
was never dispatched. Check that each added path is an ordinary file or a
planned file under ordinary directories in the same checkout. Changes to condition timing use the
public description/correction route and actual Human selection.
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
`DelegateTaskDeliveryService.observeDelegateTaskDelivery`.

## Requester adoption

1. On the requester deployment, call
   `DelegateTaskDeliveryService.describeDelegateTaskAdoption` for the existing
   Session. Keep its exact target and the actual observation, selection,
   reference and execution evidence.
2. Call `NativeWorkflowPreparationService.prepareEvidence` with family
   `DelegateTask` and kind `adoption` on that deployment. Obtain the issuer from
   the selected CNCF Subsystem's runtime identity. Check the `subsystem` entry
   in the assembly actually selected for the requester, including the CAR's
   assembly when using the shipped deployment. The Operation reads the
   running Subsystem's name and independently resolves
   `sm-workflow.external-evidence.issuer` from the effective deployment
   configuration. Use the actual Subsystem name for the input issuer.
3. If preparation reports an issuer mismatch, report the diagnostic and keep
   the requester at its current revision. If the input value was wrong, correct
   it from the actual runtime identity. If the Subsystem and declaration
   differ, have the deployment owner resolve that discrepancy before preparing
   evidence again on the same Session.
4. Have the owning host independently admit and provision the resulting
   import under `delegate-task-requester-artifact-adoption`. Then call
   `DelegateTaskDeliveryService.observeDelegateTaskDelivery` with event `adopt`
   for the same described target. The adoption Operation checks the stored
   evidence independently. [Requester operation contract](../../docs/spec/native-delegate-task-caller.md#transport-and-adoption)

A transport acknowledgment, local commit, publication, return, artifact
attachment and actual consumer activation are separate facts. Report their actual
references and remaining limitations. Source fixtures never establish a real
cross-chat delivery or consumer check. Collect retained results after interruption;
do not re-Start, replay accepted Submit or reset history/counters.
