# Native caller execution handoff

## Session state and semantic returns

The runtime owns candidate allocation, check bindings, accepted results,
history and repair accounting. Skills supply business intent, semantic work and
actual operator decisions; they do not construct per-Session slot/check-policy
objects. The finite caller keeps only the opaque routing and issued references
needed for supported continuation. See the [Session candidate resource contract](repository-sync-directory-list.md#session-candidate-resource-admission).

In bounded GoalTask, DelegateTask and StandaloneFix work, the runtime resolves
an explicit `fix-check` against the Session's admitted checkout and configured
check binding. Its position in the requested check list does not select a
workspace. Use the runtime's reported binding and retained result.

For offline review package assembly, use `assemble_review_package` from
the product's `scripts/goal-phase-dispatch.py`. When complete files have
already been supplied, use `review_package`. Unconfirmed completeness uses
the ordinary capable reviewer.

For GoalPhase/GoalTask semantic results, the normal caller projects supported
worker artifacts through `projectSemanticWorkResult` and forwards its complete
public JSON Resource with the original issued context. Direct public results
remain supported. Other workflow result types retain their existing public
contracts; this projection introduces no generic envelope or fallback for them.
Keep one visible command interval across projection, Submit and paired Advance.
Projection does not supply execution attribution, Human consent or acceptance,
and never authorizes replay of an accepted result. See [semantic result submission](semantic-result-submission.md).

## Authorization in delegated delivery — 2026-10-25

Delegate Component, purpose, acceptance conditions and permitted operations with
the retained authorization reference. Planned file lists are implementation
inventories, not automatic Human approval boundaries. Necessary extra files and
their rationale go to sm-workflow; the runtime admits same-boundary updates and
issues Human requests only for real boundary changes or unresolved authority.
The skill does not independently require reapproval and does not bypass a formal
runtime Human request. See [PA-01..07](goal-phase-dependency-scope.md#purpose-based-authorization--adopted-2026-10-25)
for current-versus-target behavior and public adoption.

Keep exact changed paths for ownership, validation and commit selection, and keep
explicit user restrictions and separate effect grants. Share one usable delivery
outcome rather than per-file permission messages. Preserve existing Sessions,
accepted work, history, counters and results. Already approved restoration proceeds
under its existing supported route while the new admission contract is delivered.


## Repair control ownership — 2026-10-25

GoalPhase repair continuation, convergence and the normal ten-batch hard ceiling
belong to sm-workflow. The caller presents actual runtime requests and returns
semantic results; it must not add one-run-only, stop-on-any-failure or per-finding
approval clauses to an authorized repair scope. See the
[skill boundary](../../skills/sm-goal-phase/SKILL.md#runtime-owned-repair-and-stop-decisions).
Runtime restoration is an execution-route exception, not a caller-owned repair
controller. Until RRC is delivered, existing authorized restoration continues via
that applicable route; missing RRC is tracked in parallel, not made a new gate.
Missing producer capability is reported as that gap, not fabricated as a runtime
Human request. Existing explicit human restrictions and genuine
new authority remain distinct from AI-authored conditions.

Preserve current Session/work identity, history, active/cumulative counts and
validation results. Adoption uses supported public transitions without direct
storage edits, re-Start or accepted-result replay. This instruction update does
not establish installed runtime convergence/restoration support. The producer's
`docs/spec/runtime-owned-repair-control.md` and RRC corrective plan track delivery.

The [caller information contract](caller-information.md) returns a typed request
to the existing caller and consumes its attributable observation in the same
retained continuation. The former runtime-side terminal provider is withdrawn.
Information return grants no Human authority, Submit, Advance or replay.

The existing caller uses public `caller.NativeWorkflowCallerExecution` to connect an
actual generated `WorkflowIssuedWorkContext` from a Request to CNCF's finite
`WorkflowExecutionHarness`. This library bridge does not install a Codex/provider
driver, invoke tools, dispatch an application command or advance a Workflow.
Actual installed host/tool/provider availability, all three skill families and
genuine separate chat/session observations remain Phase 6 acceptance obligations.

## Same-scope engineering continuation — 2026-10-30

An authorized repair includes its necessary affected validation within the same
Component, purpose, acceptance conditions and permitted operations. A type fix,
new candidate or next engineering check does not alone require fresh consent.
Carry the original attributable authority through the supported runtime route;
preserve explicit one-attempt restrictions when the user actually imposed them.

Distinguish an available choice/registration target from an actual runtime stop.
For a real `engineering-policy-human-required` response, report the retained
reason and observations. A repeated `check:<name>:failed` is a status, not proof
of repeated cause or non-convergence. If the runtime mistakes coarse statuses
for a trend, correct the producer; do not invent a fresh Human answer or bypass
the formal stop in the skill. Genuine observed non-convergence, exhausted runtime
allowance, unresolved effects and scope expansion retain their existing handling.

Keep original authorization, attempt ancestry, repair counts, failed history and
applicable successful receipts. Collect completed work without replay; revalidate
only what the changed candidate requires. Engineering authorization is separate
from permission to operate a consumer's real Session. Do not reset, re-Start,
re-Submit or operate that Session when the user excludes it.

## Existing authorization and current-target registration

Keep the user's authority separate from its registration against the runtime's
current target. A description or preparation response, including
`adoption-required`, identifies an available transition or missing registration;
it does not by itself require a new Human decision. The caller uses attributable
user direction that already covers the same Component, purpose, acceptance
conditions and operations. Preserve explicit restrictions. A reference file,
passing checks, a policy default or another agent's assertion cannot supply
missing user direction.

For an actual runtime Human request, use its supported evidence/admission route.
When that contract permits an existing applicable decision, submit its real
provenance without asking the same question again. Do not fabricate a fresh
answer, reuse consent for a different choice, or override a runtime rejection.
If no applicable direction exists, present the unresolved decision to the user.
If existing direction is sufficient but the public route cannot admit it, report
the exact operation and contract gap for producer correction; do not relabel the
gap as missing user consent or implement a skill-local recovery controller.

### Repair-policy adoption

For `describeGoalPhaseRepairPolicy`, distinguish the returned description from
the Workflow's actual Human/Wait state. Where the user already authorized the
proposed runtime repair policy in the same scope, carry that direction through
normal `prepareEvidence` with kind `repair-policy-adoption`, existing trusted
provisioning, and `adoptGoalPhaseRepairPolicy`. Preserve its actual source,
participant and occurrence metadata. The current target binding is a technical
registration, not a newly invented consent event. Broad engineering approval
alone does not select a policy; retain separately required decisions, including
the legacy Release-repair contract. Policy adoption grants no commit authority.

### Ordinary Step closure

For a Closing `Wait` carrying `authority_context`, use the existing authorization
for ordinary local Step commits when it covers that target. The configured
`prepareAuthority` Operation (or its existing `native-goal-authority.py` caller)
prepares the record; the existing trusted CNCF StartupImport route provisions it.
Preparation and an authorization-reference file alone do not register authority.
Then let ordinary Advance perform runtime admission and commit. Do not run Git
commit in the skill or infer remote/publication permission.

If policy adoption changes the runtime source revision, use the resulting
current Closing context for authority registration rather than the old binding.
A changed technical binding alone does not require renewed human permission.
Preserve the same Session, accepted work, history, repair counts and applicable
successful validation. Registration does not reset or consume a repair batch,
re-Start, replay accepted Submit, or require repeating valid checks. Actual
candidate changes and unresolved effects retain their runtime handling.

## Outcome-based delegation

Assign a coherent authorized outcome to one delivery owner: diagnose, implement
within scope, coordinate required validation/review, and return the usable result
with actual evidence and remaining limitations. Provide the purpose, acceptance
conditions, owned scope, existing authority and retained work references once.
Routine technical choices within that boundary belong to the delivery owner;
do not require a consumer round trip to freeze each edit, approve a test fixture
correction or relay every reviewer question. New authority or a material unresolved
requirement/contract decision retains its actual escalation route.

This delegation boundary does not expand permissions, collapse generated native
work types, or let workers for the parent Session execute its Phase SBT.
Separately commissioned dependency delivery follows the boundary below.
The owning workflow still issues work, runs normal validation and admits results.
This includes validation, build and local publication of sm-workflow itself.
A missing or failing runtime route grants no standalone fallback. Use only an
applicable explicitly authorized bootstrap/recovery contract and retain its exact
scope; never represent standalone evidence as proof of installed runtime support.

Execution/result collection has one owner. That owner collects the running
invocation and returns the actual terminal result; consumer chats must not
independently poll its logs or request progress-only AI turns. Cross-chat
requesters use [parent-owned waiting](#parent-owned-cross-chat-waiting) by default.
An explicitly selected notification-only mode ends the requester turn and uses
the authorized result reply. For an owned runtime invocation, retain its existing
completion/collection mechanism. User-facing updates use
already-available observations, not extra execution, probes or status agents.
Cross-chat messages carry a completed/usable result, material scope/contract change,
or a decision that the delivery owner cannot resolve. Avoid duplicate progress
narration and redundant authority rechecks when the attributable direction is
already established.

Batch independent review at the applicable semantic/acceptance boundary. Reuse
compile, focused validation and prior review evidence; corrections with no new
semantic question do not acquire a new individual review gate. Dates, version
comments and other mechanical conformance edits are handled in the existing batch.
Review genuine public-contract questions together. A reviewer remains independent
of implementation, but is attached to the delivery outcome, not used as a consumer
checkpoint for every internal step. Respect actual tool visibility: if only the
consumer can reach an existing reviewer, send one consolidated review package and
return the consolidated findings, rather than bouncing every question through it.
Changing reviewer alone is not a reason to repeat completed review.

A handoff contains actual changed scope, outcomes, applicable result references,
remaining failures/uncertainties and the next supported consumer action. Reuse
existing records; do not create a separate snapshot/approval ledger for delegation.
Preserve Sessions, work identities, accepted results, history and active/cumulative
repair counts. An end-to-end responsibility is not permission to bypass real
runtime decisions, unknown-effect settlement or publication authority.

## Implementation handoff

For a new, bounded implementation or repair, the owning chat normally delegates
the edits to a fresh subagent with no parent chat history (`fork_turns=none`),
even when both use the same model. The parent owns design decisions, integration
and checking the returned work against the acceptance criteria. Preserve an
already issued INLINE disposition, retained worker continuation or selected
role/profile. This rule concerns local subagents; it does not replace a
separately commissioned dependency chat.

Give the worker a self-contained handoff with these five explicit items:

- **Purpose:** the problem and the observable behavior the change should produce.
- **Approach:** the agreed method and why it was chosen; identify decisions left
  to the implementer and link only the evidence needed to make them.
- **Scope:** the repository, owned files or components, and boundaries to preserve.
- **Acceptance criteria:** observable outcomes and the checks by which the owner
  will assess them, including behavior that must remain unchanged.
- **Return:** the required result type, changed paths, actual checks and results
  (or checks still owed by the owner), and unresolved issues or assumptions.

Carry these items in the issued assignment or its referenced handoff. Preserve
the exact native Request and adapter-generated launch arguments. Reference
specific decisions, files and evidence rather than copying a transcript. A
worker's report is input to the owner's verification, not runtime acceptance.

## Workflow message language

Use English by default for newly authored prose exchanged between sm-workflow
and AI, including requests, worker handoffs, acceptance criteria, findings,
result summaries and unresolved questions for another agent. Use Japanese for
progress updates, reports and questions addressed to the user, including Human
decision prompts. Choose the language by the intended reader even when both
messages pass through the same chat.

Preserve the original wording of user instructions and approvals, logs and
diagnostics when carrying them as evidence. Keep identifiers, paths and typed
protocol values exact. When an English handoff needs to explain a Japanese
approval, summarize its scope in English and include the original text or a
verifiable reference to it. Apply the default when authoring new prose; accept
existing and mixed-language payloads as issued without a language gate.

For API work, apply this contract when constructing the actual provider request,
requesting typed output and presenting the result to the user.

## Human decision prompts

When the runtime requires a Human decision, ask what the user is deciding in
plain Japanese. Lead with what will happen and why the decision is needed.
Show the actual scope or effect of accepting and the consequence of the relevant
alternatives. Use the native choice value alongside its plain-language meaning
as needed for submission. A choice code, a recommendation label, or a link to a
payload cannot carry the explanation on its own.

For a repair or retry, name the failure or finding, what will be changed or
run again, and any earlier effect that could be repeated.

Use the actual saved question and its source, such as the proposed Plan and
review findings. When the display summary omits the business details, read
their exact retained references before presenting the choices. If a material
effect is still unclear, report the missing detail and keep the decision
pending. Apply this presentation to the existing question without adding
another approval round trip or widening the selected operation.

### Release scope changes

When a Plan revision excludes a release requirement, state the affected
capability by name, whether its implementation is missing, and what the user
will be unable to do in that release. Name verification that can follow
delivery separately. An unverified condition alone does not establish that
its implementation exists. A critical capability missing from the delivered
workflow remains an implementation gap.

For example, when a proposal would release Phase 28 without the Metals
connection and lint auto-fix, ask:

> 今回のリリースから、Metalsの修正案を取得・適用する機能と、
> lint・整形の自動修正を外してよいですか。この版では両機能が使えず、
> リリース後の実運用でも確認できません。

Present the actual alternatives in user terms, such as adopting that reduced
release scope or keeping the original implementation requirements. Use a
recommendation only with an explicit reason and after showing the trade-off.
A direction to postpone real-environment checks applies to verification and
carries no permission to postpone implementing those capabilities.

## Cross-chat result reply authority

For either waiting mode, the requester includes the authorized return chat
identity, the information the user permits sharing, the work covered (including
any covered follow-up repairs), and a reference to the original user instruction
or approval that the destination can verify. Carry these together in the
existing handoff and forward them when issuing a covered repair. An AI request
alone cannot establish user consent.

At return, compare the actual recipient, outgoing information and repair purpose
with that approval, respecting any later user restriction. Send a covered result
directly. A new request ID, Session or commit for a covered repair does not by
itself require another Human decision. For example, approval to share commit IDs
and verification references for a repair series covers the next commit in that
series. An approval limited to a named commit applies only to that commit.
Omit optional information outside the grant. If a necessary part of the reply
lacks verifiable authority, retain the result and ask only for that specific
permission. Record completed transport and requester adoption through their
respective existing operations.

## Cross-chat waiting mode selection

After authorized dispatch, default to [parent-owned cross-chat waiting](#parent-owned-cross-chat-waiting).
Keep one collection owner, the requester. If there is an active Goal, keep it
active and continue its existing Session when the matching result is admitted.
An absent Goal stays absent. A user pause or stop takes effect immediately.

An explicit **notification-only** / **結果返信で続行** selects turn ending and the
authorized reply transport. Preserve an already captured notification-only
choice, including choices recorded under the older **Luna待機なし** label.
For new default waits, a request to avoid Luna selects parent-owned waiting.
If a legacy waiter already owns an in-flight request, keep its existing wait
until it returns or hands back control. Do not start a second collector,
redispatch the destination or modify the request, Goal, Session or history
to change wait mode.

Parent reporting defaults to OFF. **動作状況の報告をON** or
**動作状況を報告して** enables it, and **動作状況の報告をOFF** disables it.
Retain the preference with the request. ON uses compact wait snapshots at
intervals of 2, 4, 8, then 10 minutes between reports. Schedule from the last
report; switching OFF to ON starts at two minutes, and repeated ON retains
the current schedule. OFF suppresses progress only. Report actual destination
activity, with its observation time when needed. A matching result or actionable
attention takes precedence over a progress report. A reporting toggle in
notification-only mode leaves that mode in effect.

For an active legacy waiter, send one `REPORTING_MODE` message with the matching
`request_id` and `progress_reporting=on/off` to the same child when the preference
changes. Apply OFF immediately at the parent display boundary by suppressing queued
progress. Keep collecting the terminal result through the same child.

## Cross-chat notification-only return

Use this section for an explicitly selected notification-only mode. After authorized
dispatch and recording its actual transport outcome, end the requester turn with
a brief pending-result report. Preserve request ID, requester/destination chat
identities, existing Sessions, issued context and result locators. Ending a turn
does not itself complete or pause a Goal, cancel the destination or discard an
owned runtime invocation. If automatic Goal continuation occurs without a result
or human instruction, make no tool call and end that turn; do not query status or
start monitoring. Notification-only does not guarantee that the client never
starts an extra continuation turn, and a paused Goal requires its normal resume
route. Do not advertise it as equivalent to retaining an active waiting turn.

Do not use read_thread/wait_threads loops, dedicated waits, log inspection,
heartbeat/cron jobs, liveness probes, timeout wakeups, monitoring agents or automatic
retry/redelivery to await another chat. Do not send progress requests or ask for
receipt acknowledgments. A later explicit human request for status permits a
bounded inspection, not an ongoing monitor. This rule concerns cross-chat waiting;
it does not replace runtime-owned process waiting, termination or result retention.

Apply the [cross-chat result reply authority](#cross-chat-result-reply-authority)
from dispatch through result delivery. Resume requester work within its existing
authorization.
If reply authority or a usable return transport is absent, retain the result for
human retrieval and disclose that limitation rather than promising automatic wakeup.

On actual completion, the destination sends one consolidated result message using
the available authorized existing-chat transport. Include the original request ID,
actual outcome, Session/result/evidence references, unresolved limits and the next
supported requester action. This follow-up resumes the requester when the transport
supports it; merely finishing the destination turn is not a completion subscription.
Do not create a fresh chat, Goal, Session or Codex API conversation for the reply.

On the received result, correlate it with the retained request and use normal
runtime receipt/adoption and continuation. Transport delivery alone is not native
acceptance. Reuse retained receipt/adoption on duplicate notifications; do not replay
completed work or send an acknowledgment that starts a reply loop. No exactly-once
transport guarantee or new delivery ledger is implied.

If the destination encounters an actionable blocker during its own work, it may
send one authorized consolidated blocker report instead of a completion result.
Do not automatically redispatch or restart it. If either chat, execution or return
transport stops abnormally, human recovery is the default: retain all existing
work, history, counters and evidence, and leave it stopped. Missing notifications
are not detected by AI polling, automatic retries or additional monitoring. A failed
or uncertain send is not automatically resent. Resume only on a delivered result
or a human instruction; an explicit human pause remains in force.

<a id="active-goal-luna-wait-trial"></a>
<a id="active-goal-luna-wait"></a>

## Parent-owned cross-chat waiting

The requesting Codex chat performs the wait through the Codex app tool
`wait_threads`. The requester owns transport collection and the normal
sm-workflow result receipt and adoption. Keep the existing active Goal and
current turn while the destination is working. New waits need no Luna
subagent or separate monitoring process.

1. Retain the actual authorized send result, destination chat and host IDs,
   original request ID, expected result marker, and existing Session and issued
   work references. Reuse the latest cursor from this destination. If needed for
   a new dispatch, capture a compact baseline with `timeoutMs: 0` before
   sending. If the request was already sent, examine a first snapshot for its
   result before treating the returned cursor as the next starting point.
2. Call `wait_threads({targets: [{threadId, hostId, afterCursor}], timeoutMs})`
   for the destination. Omit unavailable optional fields and copy the returned
   opaque cursor verbatim. Use the longest wait permitted by the tool and the
   caller's execution limit, shortened only as needed for an enabled reporting
   deadline. The tool supports up to 120000 ms per call. On an ordinary timeout
   with no matching event, keep the same request and re-enter the wait with the
   returned cursor, without a status query or progress-only turn. Do not wake
   on commentary alone. If the caller can handle unchanged timeouts in the same
   tool execution, do so and yield control for user input or a meaningful event.
3. Handle a new user instruction before re-waiting. On destination completion
   or attention, verify the actual request ID, destination, issued context and
   result marker or reference. A chat turn ending, an approval request, and a
   delivered development result are distinct. If an event about this request
   lacks the necessary detail, use one bounded `read_thread` read of the
   relevant turn or collect the authorized reply. Ignore unrelated or already
   handled events. Keep waiting for an outstanding request when evidence shows
   it is still running or queued. If its turn ended and the matching result is
   missing, or the transport is unavailable or repeatedly returns the same
   immediate event, report the concrete gap and retain the request for later
   collection.
4. Show receipt when the matching result is observed. Use the existing
   requester receipt and adoption operations exactly once, and proceed through
   ordinary continuation in the same Goal and Session after acceptance. Leave
   actual approvals, scope changes or missing evidence at their normal
   boundary. Reuse accepted receipts for duplicate notifications. A wait
   timeout, tool failure or chat interruption grants no retry of the
   development request.

Use the [cross-chat display contract](#separate-chat-delegation-display) for the
actual dispatch, wait and receipt. Use the compact snapshot already returned by
the wait for explicitly enabled progress reports. The requester resumes
decision-making at the returned event; the tool does not promise to reopen an
ended requester turn or to accept a result for sm-workflow. For explicit turn
ending, use the [notification-only return](#cross-chat-notification-only-return).
Ordinary timeout handling and optional reporting can still consume requester
tokens.

## New cross-chat development requests and existing continuations

Use [sm-delegate-task](../../skills/sm-delegate-task/SKILL.md) for a new
user-authorized development deliverable assigned to another existing chat.
Classify by Component, purpose, acceptance conditions and requested operations,
not by chat, Phase or Session identity, a repeated issue label, or the word
"continuation". Use existing task context for this distinction; no extra AI call,
classification Operation, approval artifact or routine user confirmation is needed.

- A correction needed to satisfy the original deliverable, a missing result,
  or failed consumer acceptance of that deliverable continues the original
  request. Retain its owner, identity, accepted work and applicable evidence.
- An additional feature, a different purpose or acceptance obligation, or a
  separate deliverable is a new request even in the same chat/Phase. Do not
  absorb it into a legacy repair merely to retain direct-message execution.
  Necessary files or technical adjustments within the original acceptance
  boundary alone do not create a new request or require renewed consent.

For a new request, prepare and admit the DelegateTask through its supported
public operations before transporting its issued work. The message tool is
transport, not an alternative execution contract. The destination uses that
DelegateTask's issued work and runtime-managed checks, repair, local commit,
selected publishLocal and return; it does not start a sibling goal-task/test-fix
executor. Their existing public test and completion contracts remain unchanged.
Use [the finite caller contract](native-delegate-task-caller.md) for actual
operations and attributable receipt/adoption; transport success is not acceptance.

Continue an already-running legacy request on its existing authorized route.
Do not wrap or duplicate it in a fresh DelegateTask, re-Start, replay accepted
Submit, reset counters or rerun successful checks just to change routing.
This preserves the outstanding deliverable, not an indefinite exemption for
future development. Existing explicitly authorized runtime-restoration contracts
retain only their actual scope; describing a new request as restoration alone
creates no exception.

If the installed DelegateTask operation, configuration or admission is missing,
report the exact observed gap and retain the pending request and existing work.
Do not silently fall back to a direct development message, sibling skill or
standalone SBT/publication runner. Correct the connection through an applicable
authorized route; do not make this gap a stop condition for unrelated ongoing
work. Genuine new effects or unresolved authority use the normal public decision
path, while applicable existing authorization is carried forward.

## Dependency development delivery choice

When dependency development needs a delivery choice, present both
**ローカルworktree（既定）** (`DevelopmentSubtree`) and **担当チャット**
(`AssignedChat`). Reuse an actual user selection or configured/captured mode
without asking again. The managed local route is the default for a new
participating dependency without an explicit mode; retain explicit `Fixed`,
repository-owner restrictions and existing Session bindings.

A missing managed-dependency registration or a response without delivery choices
does not select `AssignedChat` or require a chat identity/new-chat permission.
Explain the two delivery methods separately from dependency-scope approval;
do not append them to a closed Human answer DTO. Carry the selected method
through supported runtime configuration/admission. If setup is missing, use
the normal program-owned setup route; if it cannot represent or apply the choice,
report that concrete gap while preserving the Session and pending work.

For the local method, use the runtime-managed dependency worktree in the
project's standard development area. For the chat method, use the configured
existing owner and the authorized DelegateTask route below. Both methods
retain the separately selected JAR consumption binding. An already
dispatched request keeps its original delivery owner and binding.

### Parent SBT build prerequisites

Choose consumption separately from `DevelopmentSubtree` and `AssignedChat`.
Present **プロジェクト専用ローカルJAR（推奨）** first and **マシン共通ローカルJAR**
as an explicit selection. The captured choice stays with the Session.

For a project-private JAR, retain the supported `localJar=true` configuration.
Inspect the actual parent's `build.sbt` and `project/plugins.sbt`:

- A supported effective sbt-cozy version and enabled
  `CozyDevelopmentArtifactsPlugin`. Existing `CozyPlugin` enablement can already
  require it; do not demand duplicate enablement or infer support from a version
  declaration without considering actual overrides and the delivered adapter.
- The selected original direct dependencies use `cozyDevelopmentDependency`,
  and `cozyDevelopmentModules` declares the expected original coordinates,
  including transitive-only modules. Inspect dependency helpers and effective
  project/configuration settings. Do not inject a transitive-only module as a
  direct dependency or assume registration puts an absent module in the graph.
- The runtime supplies the exact scoped selection input for the parent build,
  and the selected publication, resolved coordinates and Compile/Test classpath
  have actual applicable adapter evidence. Configuration alone is not validation.

Apply private JAR configuration once. Publication, selection, update, rollback
and detach use the existing runtime and leave baseline build declarations in
place.

For a machine-common JAR, explicitly configure `machineLocalJar` on the
managed edge with the existing normal `publicationTarget` and the declared
consumer `consumerJar` path in Ivy local at the original dependency coordinates. Keep
`localJar=false` and omit `localProject` and private batch settings for that
edge. The parent uses its ordinary dependency and local repository resolver.
This route needs no sbt-cozy opt-in or development overlay declarations.
Register the expected JAR directory as a CNCF File workspace with read access.
The runtime retains the ordinary publication result and the exact JAR's content
digest. The parent checks the digest and the actual SBT test classpath. The same
coordinates, especially SNAPSHOT, can be replaced by another local publication
and affect other consumers. A replacement invalidates reuse of the saved check
until the expected contents are available again.

Report the selected mode, missing setup, actual publication, and parent build
verification separately. Missing private JAR setup leaves that route pending.
Machine-common publication requires its explicit selection and approved scope.
Phase 7.1 source-project integration remains on hold.

## Independent dependency delivery

When an authorized parent task needs changes in a dependency repository, the
requesting chat defines the required outcome, repository/scope, acceptance
conditions, usage contract and [delivery method](#dependency-development-delivery-choice). Apply the
[new-request/continuation routing](#new-cross-chat-development-requests-and-existing-continuations)
above to the selected chat method. For new chat delivery, use an authorized destination and sm-delegate-task;
the existing message tools carry its issued work and actual return. This contract
alone does not authorize creating or messaging a chat. The requesting chat remains
responsible for collecting the result and integrating it into the parent project.

The dependency chat owns the delivered implementation and required semantic work;
its DelegateTask runtime owns validation, repair progression, local commit and
selected publication. Do not launch an independent SBT runner for that delivery.
An already-running legacy request retains its applicable execution route as
described above. The requester and destination retain distinct Sessions; this is
not a child Workflow in the parent Session. Parent integration checks remain
owned by the parent's workflow, rather than being moved to the dependency chat.

Return the actual changed revision and artifact version/location, scope delivered,
compile/test commands and results with retained evidence references, required
review outcome, unresolved issues and concrete consumption instructions. Identify
uncommitted changes or unavailable artifacts honestly. Source edits alone do not
complete delivery; failed or missing required validation remains incomplete.
Artifact publication uses runtime-owned LocalPublication under its existing
authorization and version rules.

The parent consumes the delivered dependency and uses its own sm-workflow for
parent-project integration checks, required review and formal Step/Phase
acceptance. Dependency validation is evidence about the delivered dependency;
it is not a substitute for parent acceptance. This separation is based on the
actual deliverable and ownership, not renaming a parent check as external work.

Preserve any pending dependency assignment, scope question, Session identity and
history already registered in the parent. Reconcile actual returned work through
the supported public decision/result/continuation procedures applicable to that
record. Do not edit internal storage, invent a successful result, discard a
pending obligation or replay accepted work. When no supported reconciliation
exists, report that precise parent integration gap and retain the record;
authorized independent dependency development can still proceed. Existing
native dependency-scope admission remains applicable to work recorded there.

### Continuing a managed dependency assignment

For an unclaimed implementation using a project-private JAR in the selected
local worktree, the parent can approve supplemental owned paths within the same
Step, Component, purpose and delivery. Use
`describeDelegateTaskScopeCorrection` with the destination Session,
`oldContinuationId`, literal `additionalOwnedPaths` and
`supplementManagedInventory: true`. Use `prepareDelegateTaskScopeCorrection`,
the actual Human choice for that target and `correctDelegateTaskScope`, then
ordinary `advanceDelegateTask` in that Session. The original exclusions,
explicit exact-path restrictions and registered ownership still apply. Keep
the original parent order and approval, issued work, results and repair counts.

When the original scoped consumer Compile was rejected before launch and
another verification already succeeded for the same binding, use
`ManagedDevelopmentService.describeDevelopmentConsumerVerificationRecovery`
with `edgeIdentity`, original `requestIdentity`, `publication`, `selection`
and the successful `verificationRequestIdentity`. Include `publicationEdge`
when the original request names a separate one. Use the same input with
`adoptDevelopmentConsumerVerificationRecovery` when eligible. The runtime
matches both attempts to the same participant, workspace, publication,
selection and current consumer source. Ordinary requester
`advanceDelegateTask` returns the original parent WorkOrder using the adopted
result. The rejection stays in history. No Compile or publication is repeated.
An unknown or launched original attempt does not qualify.

## Codex Goal registration

When the user explicitly asks the owning Codex host to start or resume Phase
development through `sm-goal-phase`, that request authorizes registering the
Phase as a Codex Goal. The host performs this step before starting new runtime
work or resuming semantic work; the semantic executor and native CLI do not
register it. Reading a skill, inspecting status or receiving an issued worker
request alone does not authorize a new Goal.

1. Call `get_goal`. Reuse an unfinished Goal for the same requested Phase.
   If a different unfinished Goal exists, explain the conflict and obtain the
   user's direction before starting new work; do not overwrite or complete it.
2. If no unfinished Goal exists, call `create_goal` with a concrete objective
   naming the selected repository, Phase, intended deliverable and required
   validation/review/closure. Include the actual retained session/task reference
   when resuming. Supply `token_budget` only when explicitly requested by the
   user. Check the tool's actual result before reporting Goal registration.
3. Continue through the existing admitted host entry. The Codex Goal is host
   progress tracking; it supplies no native Start selection, session ID, effect
   authority or acceptance. Resume an existing runtime session through its exact
   retained route, including when repairing missing Codex Goal registration;
   never repeat Start merely to register a Goal.

If Goal tools are unavailable or registration fails, explicitly report that the
Codex Goal was not registered and preserve any existing runtime session. Do not
claim registration from a native Start response or replay a runtime operation
to repair the host Goal. Goal status changes follow the host tools' actual
contracts: mark complete only after successful native Phase closure and all
remaining requested work, never on Start, an accepted Step, Wait or failure.
Pause and blocked status retain their tool-specific user-direction and repeated
blocking requirements.

## Installed sm-workflow command

For `sm-goal-phase`, `sm-repository-sync` and `sm-split-phase`, the owning
host chat calls the installed `sm-workflow` executable directly through its
command tool. Do not route that invocation through `cncf_runtime_runner`,
`cncf-runtime-execution` or another command-execution subagent. This specific
route takes precedence over generic runtime-runner routing for this command.
Semantic work and independent review retain their existing executor selection.

Parent-owned command transport does not transfer the Operation's business work
to the parent AI. For RepositorySync, the runtime owns Git observation, fetch,
relationship classification, integration, validation/review selection, commit,
non-force push and final confirmation. The caller passes authorized intent and
renders the returned outcome; it does not preflight repositories, choose Git
commands or assemble a separate validation Task for ordinary synchronization.
Generic "ordinary Git/external mutations run in the parent" rules apply outside
this runtime-owned scope, not as a fallback for dirty inputs, missing setup or
an unsupported intent. Preserve the actual requested scope and existing Session;
report a precise unavailable contract instead of silently changing workflows.
The normal path needs no additional AI decisions for deterministic progress.

Install this command with CNCF's `install-cli --published-target
org.simplemodeling.textus.SmWorkflow`, leaving the component version unspecified.
The generated executable accepts `<Operation> [parameters]`; select the host as
its process cwd. `--runtime-root` remains an option of the finite Python caller,
which sets cwd itself, and is not an argument of the installed executable.
Do not add the launcher's `command` token to a generated command invocation.
The launcher selects the newest enabled stable or SNAPSHOT catalog version,
preferring stable over the SNAPSHOT of the same base version.

On the first attempt, set `sandbox_permissions: "require_escalated"` so normal
runtime caches and configured runtime outputs are accessible. Use the resolved
absolute installed command path, explicit runtime root/cwd and the intended literal
arguments. Limit `prefix_rule` to that executable and, where useful, its fixed
runtime-root/operation arguments; do not request a general shell or interpreter prefix.
Normal artifact selection remains with the installed command; do not substitute
a checkout launcher or development classpath for this installed route.

Escalation is a platform execution request, not workflow effect authority or
provider/model placement escalation. It grants no Start selection, semantic
acceptance, Human answer, Git effect, Submit or Advance beyond the existing
authorized operation. A platform denial stops the call with its actual reason;
retain actual output and uncertainty without replaying an uncertain effect.
Do not disable platform controls or broaden the command to evade a denial.
The existing host completion and caller-loop contracts still govern accepted
Submit and the following explicit Advance.

### Finite caller invocation

Resolve the installed skill to its real directory and use the absolute path to
its shared `scripts/native-goal-command.py`. Start the finite Python caller
through the workstation's managed Python on the first call:

```text
uv run --no-cache --managed-python --no-python-downloads --script /absolute/shared/scripts/native-goal-command.py <arguments>
```

The installed helper is distributed as a regular non-executable file. Use this
route for supported actions such as `prepare-goal-input`, `advance` and `complete`.
Public operations outside the helper continue to use the installed
`sm-workflow` executable directly.

For the supported finite paired completion, pass `--action complete`,
`--executable <absolute installed sm-workflow>` and the explicit
runtime-root/task/request/submission/fresh-output paths. Request one-shot
escalation for the complete invocation, including the selected executable and
arguments. This is the existing host-owned pair, not a command
subagent or a substitute Workflow. The helper launches only the selected
installed executable with `<fixed Operation>`, selecting `<runtime-root>` as the process cwd; it never
constructs a development classpath or selects an artifact. It retains/displays
actual accepted Submit before reopening the original family/Session routing for
one explicit outer Advance. Changed routing, rejection or uncertainty stops with
raw outputs preserved and no replay. See the
[installed finite caller contract](native-goal-command-task.md).

The same finite `complete` action accepts the ordinary Goal Human pair
`submitGoalPhaseOrderedDecision` / `GoalPhaseOrderedDecisionAnswer` only under
the retained `GoalPhase` Session. This branch requires an explicit existing
`--human-provenance` file containing actual `participant`, `issuer`, `occurredAt`
and strict dotted `collection`, plus exactly supplied answer, resultReference
and executionEvidence objects in the submission file. Semantic Requests reject
this option. Genuine channel records supply answer and provenance; Python
does not capture/authenticate a Human channel, choose direction or create
worker, reference, issuer or time.

The program calls existing passive `prepareEvidence` once with the whole
original issue and supplied records. It retains/displays the actual datastore
response under `preparation`, then selects that unchanged response through the
single fixed canonical runtime configuration argument
`--textus.import.data.file=<absolute preparation/response.json>`
on the issued Human Submit only. CNCF admits its Args snapshot under the
actually selected subsystem. Ordinary StartupImport provisions the native
generated rows; no persistent host configuration or import row is edited.
Native independent-Human admission stays authoritative. Only actual accepted
storage-only Submit and unchanged routing permit one ordinary outer Advance,
without that configuration argument. Preparation/Submit uncertainty, rejection, cancellation
or changed routing stops without Advance or retry and retains all prior actual
outputs. See the caller contract for exact input shapes and output sequence.

### Public Operation inputs

Use the selected Operation's published input definition. A Session-only
description uses `--sessionId`; an adoption, answer or reconstruction with
structured business data uses its typed `--input` or `--inputResource` route.
Where both transports are offered, supply exactly one and retain the complete
DTO and target. Do not move individual fields between transports, invent flags,
shorten a target or rebuild it from presentation text. A Resource URI uses the
configured CNCF Resource/UoW authorization, UTF-8 decoding and read budget;
put the actual artifact under an already authorized root. A refusal does not
authorize adding another reader or an application-side size bypass.

### Scala diagnostic repair and lint evidence

#### GoalPhase compiler repair

After a failed Step validation and the normal repair reservation, ordinary
Advance may apply a registered deterministic correction before issuing
`RepairStep`. The connected recipe handles the exact supported Scala 3 Circe
`E008` error-member case. Runtime binds the edit to the retained compiler
receipt and selected candidate, records its outcome, and runs the selected
candidate inspection and revalidation. A formal revalidation failure can
restore the prior bytes and index before ordinary repair. Use the runtime's
returned state and dispatch semantic repair only when it issues a WorkOrder.

When an issued repair says the deterministic edit was reverted, the retained
failure describes the checked candidate before rollback. Use the current
issued candidate and owned source for edits, and the retained failure for
diagnosis. When the runtime reports an unconfirmed write or rollback conflict,
retain that state and follow its returned continuation. Preserve the original
results and repair budget.
After deterministic repair, ordinary Advance can select a configured bounded
compiler task for a supported Compile or TestCompile diagnostic. The runtime
prepares the input from the retained diagnostic and exact candidate blob,
records the invocation, and admits at most one owned replacement. It owns
application, candidate capture, revalidation and rollback. Continue semantic
repair only from an issued WorkOrder. Preserve observed usage and the disposition
of declined or rejected proposals. A missing or unbound result remains uncertain.
Metals/LSP integration and automatic formatter or Scalafix repair are separate
capabilities. Their availability depends on the selected installed runtime and
its attributable retained evidence.

#### Bounded API review and provider usage

For an independent ReviewStep or ReviewPhase with a complete READY package,
ordinary dispatch can use bounded API review when the selected caller mapping
has `"suppliedReviewApi": "bounded"`. Pass the package as `supplied_review`
and its canonical file as `supplied_review_path` to the existing adapter. The
selected runtime invokes the configured bounded Task runner. It may prefer
OpenAI when the host has opted in and a verified allowance covers the request.
The provider reserves the bound before dispatch. Retain actual provider, model
and usage from the result. A reservation or reported token count alone does
not establish a billing amount.

Submit only the completed typed review with attribution. For a retained bounded
decline, unavailability, failure or budget refusal, the adapter accepts the
original review and `retained_api_attempt` to prepare the ordinary independent
reviewer. Keep started or uncertain attempts with the original invocation.
Incomplete packages or a mapping without bounded selection can use the ordinary
reviewer. The skill does not select new provider authority or call a provider
directly.

#### FixCheck attribution and exceptions

Use the retained typed `fix-check` result and its actual diagnostic scope. The
producer can omit a standalone managed history date hunk when actual Git base
and index bytes and the resolved history rule prove the rewrite. Other hunks
keep their coordinates. Mixed changes and unverified history retain ordinary
conservative attribution. Keep the reported scope and preserve past results
when a new validation uses the narrower attribution.

When available, use the structured rule, severity, range, disposition and
provenance alongside the retained check outcome. A null-boundary
`VerifiedBoundary` observation confirms only the supported source shape and
resolved parameter/predicate. Business correctness and Human acceptance require
their existing evidence. Keep stale, invalid, unsupported or missing evidence
and suppression warnings with their actual disposition. Use only an applicable
admitted exception and retain the author's own justification. Request Human
direction through the existing path when the runtime issues that decision.

### Current-source and source-history continuation

Use these routes only when the selected installed runtime supports them. Retain
the actual public response and any stated eligibility limit; the presence of
source code or a new skill is not installed qualification.

For the selected installed native Phase route that supports managed Scala history,
implementation does not make routine manual header updates. The runtime owns
enabled normalization and conformance checking on the admitted candidate.
Independent review consumes the attributable result for that candidate and checks
substantive concerns, rather than repeating routine manual header checks. Commit
preparation uses the same candidate result when applicable. Failed or interrupted
history operations use the native recovery path below. Hand-authored and manual
routes outside this automation retain their applicable history rules; older skills
remain read-compatible and do not prove that the installed runtime supports it.

For an interrupted history-comment normalization, obtain the current target
through `GoalPhaseService.describeGoalPhaseSourceHistoryNormalization
--sessionId <existing-session> --json`. Its `adoption-required` presentation
does not by itself require a new Human answer. When the affected intent retains
a v2 invocation association, ordinary Advance can accept factual caller evidence
under the already held same-authority rule:

- Preserve the original call's finite stderr event
  `goal-source-history-invocation` and its complete `invocation` object. It binds
  Component instance, Session, operation and invocation identity. An event from
  a later call cannot substitute for the affected intent's association.
- Collect the original call's actual termination through its existing transport
  handle or retained result. An absent receipt, elapsed time, current comment,
  chat completion or a successful unrelated process proves neither termination
  nor the old File effect's result. No extra polling agent or process scan is
  needed to return an observation already held by the caller.
- Supply a JSON artifact with `schemaVersion` equal to
  `sm-workflow.goal-phase-source-history-caller-observation.v1`, the complete
  current `target`, exact original `invocation`, actual `sourceReference`
  (`identity`, `revision`), canonical `observedAt`, and `termination` equal to
  `exited`, `live` or `unknown`. Only `exited` carries the actual `exitCode`.
  These are factual observations, not consent, an old effect result or a Human
  Direction. Do not fabricate references, times or process observations.
- Return that artifact through the normal installed command, with the existing
  host as cwd:
  `GoalPhaseService.advanceGoalPhase --sessionId <existing-session>
  --sourceHistoryObservationResource file:///absolute/observation.json --json`
  (use the full `org.simplemodeling.textus.SmWorkflow.` Operation prefix).
  The current finite caller's `advance` action has no observation option: use
  the direct CLI transport with normal event retention/forwarding for this
  call. Do not add an unsupported helper flag or a replacement wrapper.

Only exact, current, terminal evidence can admit automatic selection. Missing,
live, unknown, stale or crossed evidence admits no writer or checks. The runtime
reads first: a conformant comment needs zero writes; one uniquely prescribed
managed-comment correction uses the retained complete rule/date and observed
text. The original UNKNOWN remains unchanged. An unresolved new reconciliation
intent is not automatically retried. Runtime acceptance precedes normal candidate,
selected validation and independent review. Repeated collection reuses results.

Old v1 UNKNOWN has no invocation association. Do not retrofit one from current
content or manufacture an automatic selection/Human answer. Keep its supported
explicit recovery/adoption semantics and actual evidence; report the precise
missing fact if neither path is applicable. A retained pending question is not
answered by a factual observation; only the runtime may supersede an eligible
technical question unanswered. Installed qualification of new associated cases
does not establish that every legacy pending question can be superseded.

When actual Phase/checklist selection or current-source applicability changes,
the common Operation is `GoalPhaseService.reconstructGoalPhaseDevelopment`.
Supply exactly one typed `input`/`inputResource` containing `sessionId`, distinct
workspace-relative `phasePath` and `checklistPath`, complete raw Plan `proposal`
and `rationale`. The runtime reads the documents, preserves accepted completion,
base, repair counters and original evidence, and assesses the actual remaining
scope. Collect pending work first; genuine ambiguity uses the returned Human
channel. Do not use reconstruction to replace a potentially active worker or
to clear unknown writer/publication/commit effects.

Primary Phase Compile, TestCompile, Test and TestOnly use the selected
candidate's checkout. If a wrong execution host stopped validation before
launch after an authenticated completed prefix, correct the execution host
through the supported configuration and use the same reconstruction route
when owned source has changed and the Plan, retained candidate and observed
HEAD still match. The runtime retains the earlier observations for their
original subjects and requires a fresh candidate capture. Report the returned
blocker for missing provenance, unchanged owned source or unresolved effects.

For a formal normalization failure, the runtime assesses reusable versus fresh
prefix paths under the saved rule/date and ownership. Callers do not mark the
prefix complete or erase the failure. A separate ordinary Advance owns current
capture and affected checks. Old successful checks remain true for their
original subject; changed source requires only the affected fresh qualification.
No reStart, accepted Submit replay, repair refund or new consumer operation is
implied by any of these routes.

### Runtime configuration boundary

The development target repository and sm-workflow's runtime host have distinct
roles, even when deliberately selected at the same path. `--runtime-root` selects
the runtime working directory; it does not declare the target a CAR/SAR project
or require `.textus/config.json` and `.textus/assembly.yaml` there. Retain an
existing Session's selected host and storage; do not move it to another root to
work around a configuration error.

When configuring a new dedicated host, choose its CNCF assembly `subsystem`
and instance labels independently of the Phase number. Each label starts with
a letter and may contain letters, digits, underscores and hyphens. For a Phase
named `28.1`, a subsystem label such as `sm-workflow-phase-28-1` is valid.
Keep `28.1` in the Plan and in paths where it identifies the Phase. Bind the
host configuration to the actual chosen labels. Use the existing CNCF
configuration resolver to validate them.

The installed launcher and finite callers validate their executable, selected
directory and owned task/output paths. CNCF's normal runtime configuration
resolver owns home/project/cwd files, supported formats, environment and argument
precedence, and explicitly selected configuration sources. Do not duplicate that
resolver in the skill or preflight one fixed filename as mandatory. The launcher
must not manufacture `textus.assembly.descriptor` from the working directory.
Use that property only when an assembly override is actually selected; CNCF
retains validation of an explicitly selected missing or invalid descriptor.
The installed sm-workflow CAR is selected separately from the target's packaging.

Use the existing configured runtime host and public setup/admission route for
actual missing host, check, Git or storage registration. Do not create dummy
configuration/assembly files in a target such as Cozy, equate missing fixed
filenames with an unavailable Workflow, or substitute standalone SBT/commits for
native acceptance. Report actual CNCF configuration/admission failures with
their observed cause.

<a id="validation-cadence-admission-full-and-heavy"></a>
## Validation cadence with standard annotations

ADMISSION is the only custom classification label. FULL and focused describe run
scope, not test memberships. Use standard ScalaTest Slow and DoNotDiscover rather
than custom HEAVY/ON_DEMAND labels. SMOKE, FOCUSED, FULL, HEAVY and ON_DEMAND active
purpose values are retired by the coordinated producer/consumer migration.
Do not add aliases or automatically map Slow into a second HEAVY classification.

| Selection | Operation |
| --- | --- |
| ADMISSION | Short basic acceptance from its declared members within ordinary exclusions |
| FULL | Normal discovered regression tests, excluding Slow and special external-environment work |
| Slow | Explicit standard Slow-tag selection, including selected nighttime runs |
| Explicit-only suite | DoNotDiscover plus exact suite selection and its real prerequisites |
| focused | Actual affected suite/case/feature/tag selection, without a FOCUSED label |

Use class `@org.scalatest.tags.Slow` or per-case `taggedAs` with the standard Slow
tag. DoNotDiscover remains selectable by an explicit suite request; it is not a
disabled test. Selecting Slow alone does not automatically discover those suites.
A broad query must not re-enroll them. Selecting an unrestricted named suite may
run all its cases; report actual selection. Keep external-service requirements
separate from duration and exclude their suites before setup. SQLite and small
test-owned same-JVM loopback fixtures remain eligible for FULL.

ADMISSION targets at most one minute of aggregate test-body time, excluding
preparation/cleanup. It is not a fixed timeout. Preserve feature, expectedDuration,
execution requirements and actual result scope. A focused request may explicitly
include Slow/explicit-only cases as needed; do not repeat an entire suite solely
because an affected case belongs to it. Empty selection is not successful coverage.

Runtime owns selection, preparation, execution and evidence. Apply new selections
through supported planning/adoption; preserve active Sessions, captured checks,
accepted results, history and counts. Documentation does not prove deployed support.
Use actual supported operations and report a concrete obsolete-selector/adapter
gap; do not invent commands, silently rewrite issued checks or bypass runtime-owned
SBT. Unrelated ordinary work does not wait for migration completion.

<a id="on_demand-operating-policy"></a>
## Low-frequency test operation

Keep DoNotDiscover suites and genuine scripts active and invocable when needed
or explicitly selected for nighttime execution. Maintain invocation instructions,
prerequisites, expected results and cleanup. Repair drift when exercised, preserve
original failures and separate repaired reruns, and distinguish real product bugs.
No continuous qualification, catalogue replay, new scheduler, AI monitor or
automatic AI repair is required. Reserve scripted for actual sbt scripted, never
as a nickname for excluded ScalaTest suites. Existing successful evidence retains
its original scope. Earlier ON_DEMAND guidance is superseded, not an active alias.

## Unstarted execution continuation

Continue already authorized preparation without another Human confirmation only
when actual tool/provider observations establish nonexecution, unconsumed
permission and the same target, operation, scope, acceptance, destinations and
effects. Preserve explicit user restrictions. Missing logs alone do not establish
nonexecution; retain the stopped owner's actual result and disposition.

Correct the identified preparation problem through the existing execution owner.
For a runtime-owned operation, use its supported public continuation or result
collection. A missing capability is a runtime contract gap, not permission to
replace it with skill-owned execution. An old worker whose loaded contract cannot
continue stays retired when ownership transfers; the old failure stays recorded.
Do not re-Start, replay accepted Submit, rerun completed effects or reset counters.
Provider safety/policy refusal, permission/sandbox denial, a live execution,
consumed authority and uncertain execution do not qualify. Do not route around
those results with another prompt, model, worker or tool, or request approval to
bypass them. New effects still require their existing authority.

## Product SBT lifecycle

Textus SBT has a required minimum maximum heap of 4 GiB, including human shell
invocations. Project `.jvmopts` settings and larger heap requirements remain
supported by the policy. Normal workflow execution resolves the ordinary
`cncf.sbt.jvm.maximum-heap-mib` property (default 4096 MiB) in the CNCF typed SBT
producer, preserving larger project options. Manual/standalone SBT outside
sm-workflow uses standard SBT config. No extra global launcher is installed.
Producer source validation does not establish
adoption by an already installed consumer runtime or machine configuration.

On an actual heap failure, retain diagnostics and identify the failing JVM and
effective setting when available. Distinguish compiler heap exhaustion from a
forked test, Cozy JVM, metaspace failure or OS termination; unknown heap remains
unknown. Correct the owning runtime/configuration through normal continuation.
Do not mask missing runtime behavior with machine configuration, parse JVM options
inside a skill, inject per-check JAVA_OPTS or automatically increase memory.

For separately authorized manual/standalone SBT, inspect the selected launcher's
actual option precedence, user sbtopts, project .jvmopts/.sbtopts and explicit
arguments/environment. A later fixed --mem option can override a larger project
heap; an imported dependency's .jvmopts does not configure the primary SBT JVM.
Resolve XDG_CONFIG_HOME and any SBT_ETC_FILE before proposing a concrete minimal
shared-setting diff. Obtain human confirmation for that exact diff and its
effects on other projects before editing; reuse approval already covering it.
Test authority alone does not approve home configuration changes. Preserve larger
project requirements and unrelated settings. A config change is not a successful
validation; retain the same Session, failures, repair counters and execution owner.
Sufficient configured heap does not rule out excessive demand or a memory leak.

The installed sm-workflow CLI's native local driver executes ordinary SBT
inside its already escalated, admitted UoW. The execution platform owns launch,
waiting, timeout, bounded stdout/stderr capture, termination and validation
interpretation. Its actual program-produced observations supply skill display
data. Skills do not create a per-check AI runner, prepare native SBT bridge
requests, launch/wait for a child or manufacture/publish completion responses.

The ordinary installed CLI call waits for its actual response, including when
Advance takes an hour or longer. There is no caller-owned execution cutoff,
fixed startup cutoff or telemetry-derived deadline extension. Runtime programs
own execution deadlines, monitoring, heartbeat and result collection; waiting
does not require repeated AI calls or extra per-check agents/operations. A
transport/UI wait limit does not authorize killing the runtime, relaunching it
or issuing another Advance. CNCF retains per-check deadlines and checks.

An explicit SIGTERM is forwarded once to the exact owned runtime as a
termination request. Runtime closes new-launch admission, cancels active
operations, verifies managed-child/descendant termination and preserves logs,
actual results and durable Workflow interruption state before closing runtime
storage/resources and exiting. Grace and any required escalation belong solely
to existing runtime configuration. The caller adds no TERM/KILL process-group
fallback or cleanup timer and never scans or signals unrelated user processes.
Sending TERM is only a request: runtime-confirmed settlement and the actual
durable result are required to establish cancellation. SIGKILL, missing evidence
or unconfirmed termination/effects remain unknown, without fabricated success,
cancellation or automatic replay. No universal termination guarantee is made
for escaped, detached or uninterruptible processes.

Use the existing normal continuation policy: confirmed non-execution permits
preparation correction or executor replacement under unchanged target,
operation and authority; await running work, collect applicable completed
results and observe unknown work without blind replay. A caller change creates
no new execution or approval requirement. Cancellation neither turns validation
into a product-test failure nor resets/refunds/renews repair counts. Preserve
existing interrupt handling; a new SIGINT handback/resumption protocol is a
deferred candidate, without new tests, acceptance or Phase-plan obligations.

This product execution belongs to the platform program within the installed
CLI call. Parent-project validation for its issued implementation/repair, Step
acceptance and Phase completion stays on Submit/Advance, including development of sm-workflow
itself. A supplementary or pre-Submit test label does not make that validation
an independent command. The caller propagates this boundary to editing workers;
missing check selection or progression is corrected through the owning workflow,
not replaced with an AI-created runner intake. This rule applies to the parent
Session's workers; independently commissioned dependency development follows
[independent dependency delivery](#independent-dependency-delivery).

Separately scoped standalone development tests/publication and restoration of a
broken runtime unable to execute its checks retain `cncf_command_runner`, the
shared wrapper, normal caches and sbt-native locking. Identify the separate
purpose in existing task context without a new approval/classification artifact.
External receipts are not automatic Phase acceptance. Adoption preserves ongoing
work, Session identity, history, repair counts and attributed validation evidence;
collect running work, reuse applicable evidence through supported admission and
do not restart or rerun solely because the routing policy was clarified.

The former manual registered SBT host bridge is rejected and retired, including
current typed `registered_sbt` execution; there is no V8 execution promise.
V4 host configuration requires explicit migration to V3 with
`registeredSbtHost` removed. No automatic fallback, stored Session edits or
relabeling of historical accepted evidence is permitted. Historical schemas and
decoding remain readable. See the [retirement contract](registered-sbt-host-execution.md).

## Recovering completed results after caller interruption

On resumption after a chat/model/transport interruption, or an explicit status
request, reconcile retained executions before reporting a producer as incomplete
or starting replacement work. A chat error is not a process failure. Use the
original invocation/work identity and exact saved handle/result/output references
in the existing continuation or handoff; no new tracking ledger or global scan.

1. Read the attributable terminal receipt and relevant summary. A completed
   success, failure or cancellation is its actual outcome regardless of whether
   the owning chat sent a final message. If still running, collect the original
   invocation through its supported delivery/wait mechanism. If no terminal result
   is available, report that uncertainty and retain the unresolved reference.
2. For publication, consume the normal `publishLocal` result and its publication/
   runtime activation records. Distinguish publication success, runtime activation,
   installed qualification, producer acceptance and consumer adoption. Do not infer
   any of these from chat status or from another invocation's success.
3. Recover all outstanding known verification results, including commands completed
   after the chat stopped. Reuse success at its actual candidate/image/selection;
   retain failures and genuinely missing results. Do not repeat successful commands
   merely to obtain a new chat summary. Source changes require only the applicable
   validation under the existing policy.
4. Update the existing handoff with the recovered facts and next action. Collecting
   an execution receipt does not Submit/Advance a consumer Session or complete its
   Phase. The owning workflow performs any required admission through its public
   route. Share one usable delivery when messaging is authorized; do not request
   status-only AI turns from an unavailable producer.

This is event-driven recovery, not periodic monitoring. Preserve the original
Session, accepted results, history and repair counts; no fresh execution, extra
AI monitor, automatic model switch, duplicate Submit or automatic publication is
introduced. The current [long-running collection](#long-running-invocation-collection)
contract still governs ordinary waiting. A missing live handle with known saved
outputs calls for receipt recovery, not abandonment or blind replay.

## Long-running invocation collection

For Advance or complete taking minutes or hours, preserve the one owned execution
and collect its actual terminal result. The runtime owns deadlines, heartbeat,
monitoring and child settlement. The caller does not run a timer-driven tail/read
of stderr, response-size check, status query or process scan just to establish
that execution is still pending. Do not add a monitoring agent, scheduled check,
caller timeout or another execution to fill a quiet interval.

Prefer an existing transport completion notification or pending-call resume. When
explicit collection is necessary, use the supported wait/resume on the original
execution handle as needed for eventual result delivery; do not treat every yield
as an event requiring AI narration, a log probe or another business Operation.
A transport yield ends neither the process nor the workflow. Keep actual handle
and output references in the existing continuation context; do not invent a new
poll ledger. Removing redundant monitoring must not discard a live handle, strand
its result, detach work without supported delivery, or end the owning workflow as
complete. Resume normal processing when its actual result becomes available.

Show the matched start and return, plus material changes already exposed by the
owned invocation: a newly selected check, a failure, cancellation, required action
or a changed execution condition. Suppress unchanged waiting, repeated heartbeat,
empty response and elapsed-time-only updates. Runtime telemetry remains available
in retained evidence; the chat is not a copy of each heartbeat. These display rules
create no reporting cadence or requirement to invoke AI for liveness. Apply the
same rule to the owner's own command and to delegated producer work.

An explicit user status question or a concrete new diagnostic need may justify a
bounded read. State the purpose and observed uncertainty. Silence/empty output
alone establishes neither hang nor success. Lost transport follows existing
reconciliation; no blind replay, extra Advance, restart, automatic kill or counter
refund. Preserve Session/work identity, accepted results, all history, repair
counts and prior success/failure/UNKNOWN evidence.

Skill text does not implement event delivery. If the available transport requires
model resumption to collect a result, use that minimum supported mechanism and
disclose the limitation rather than claiming zero-AI waiting or abandoning the
pending invocation. A future transport improvement must reuse the execution/result
owner; no new skill-side monitor is introduced by this contract.

## Visible sm-workflow exchanges

During `sm-goal-phase`, `sm-repository-sync` and `sm-split-phase`, the owning host narrates command exchanges in the user's
language through chat commentary, without requiring a verbose/debug option.
Show each call when it happens, including preparation, Start, Submit and Advance;
do not defer all reporting to the final answer or leave it only in tool output.

- Before invoking the command, use a short `sm-workflow →` message naming the
  actual operation, selected Phase/Step, known target session and input purpose.
  For a substantial input, summarize its intended result and link the exact
  selected input file instead of dumping its JSON. A Start proposal is only a
  proposal until its response supplies the actual ID.
- After return, use `sm-workflow ←` to report the actual response's `kind` and `outcome`,
  `accepted` when present, and runtime `message`. Quote the message as data and
  explain its practical meaning briefly when needed. For Advance, name the
  actual Request/Human/Wait/Terminal kind and any issued work kind/Step or
  waiting condition. Show the actual session ID at Start or when the target
  changes, distinguishing a Goal-owned child from its outer Goal.
- For preparation responses without business status fields, describe the
  actual returned artifact/record kind. Do not invent accepted, completed or
  next-work claims from process exit alone. On rejection, nonzero exit,
  malformed output or transport uncertainty, report the observed failure and
  bounded relevant diagnostic, with links to retained response/stderr files.
  Explain whether the command is still running or has stopped using only
  observed process state; no response yet is not a Workflow Wait.

The host's existing `complete` action keeps Submit and the permitted explicit
Advance in one invocation. Announce that pair before the call and report both
actual responses in order when returned, distinguishing result acceptance from
the next Workflow instruction. Do not insert a continuation prompt or extra AI
turn between them, split the action, replay a call, or query status solely to
produce commentary. While a call is running, report only material changes already
available from its owned output. Do not repeat unchanged waiting or create probes
to supply commentary; follow the long-running invocation collection contract.

Use the existing retained raw outputs as detail links; this display adds no
event store, wrapper, control state or runtime operation. Keep credentials and
private input contents out of commentary. Reporting does not change effect
authority, independent review or the original stop/continuation rules.

The same concise checklist is mandatory in all three skills, in normal mode:

| Moment | Required commentary using actual data |
| --- | --- |
| Before | `sm-workflow → <operation>; purpose=<purpose>; Session=<known ID or unknown>; Step=<known Step or unknown>`; include the known Phase/target and exact selected input link when useful. |
| During, on material change only | `sm-workflow … <actual observation>; transport=<observed running/stopped/unknown>`; report relevant changes already exposed by the owned invocation. Do not narrate repeated waiting/heartbeat or poll to fill this row. |
| After | `sm-workflow ← kind=<actual or unknown>; outcome=<actual or unknown>; accepted=<actual or unknown>; message=<actual or unknown>; work=<issued work or unknown>; next=<actual waiting/continuation condition or unknown>`; include supplied current/cumulative repair counts and validation/runtime observations with exact retained-output links. |
| Failure/uncertainty | State the observed rejection, transport/nonzero/parse failure or missing output and what remains unknown; link exact retained response/stderr. State running/stopped only from observations, without inferring success or a Workflow Wait. |

Optional fields and counters absent from the actual program response stay
`unknown` (or explicitly absent); never fabricate zero, acceptance, a Step or
completion. Keep the outer Session and actual child target distinct. A running
transport with no response is not Workflow `Wait`; display Wait only from its
actual returned kind and condition. The existing `native-goal-command.py`
supplies program-produced exchange/event data; skill prose alone does not
guarantee that a terminal/tool transport exposes live OS output. If output is
buffered or unavailable, report that visibility limit, then display the actual
observations when available without claiming earlier delivery.

The before/after checklist applies to every call, including preparation and
paired `complete`; display its actual Submit and permitted Advance responses
in order. Narration must appear in chat commentary even without debug flags;
raw tool output, a detail link or the final answer alone is insufficient.
Use exact returned/retained raw-output paths as links. Do not dump secret source
contents, full argv or environment. Do not add state queries, Submit or Advance
solely to obtain display data, split the paired completion, replay calls or
alter continuation/stop rules.

Report actual deadlines when first observed or changed; include relevant
termination/descendant settlement and durable result references at handback/failure.
Keep heartbeat detail in retained runtime output rather than repeating it in chat. Runtime telemetry is observation, not a caller deadline or effect
authority. Distinguish a forwarded SIGTERM request from runtime-confirmed
settlement and the durable actual result. Missing observations remain unknown;
a requested signal, elapsed time or root-process exit alone establishes neither
descendant cleanup, durable interruption state nor validation success. Preserve
the actual response/uncertainty and existing normal continuation authority;
invent no extra approval or AI wait loop and no automatic replay.

### Command stream retention and forwarding

Choose an event-preserving transport before launching the already-authorized
Operation. For actions supported by the deployed finite caller, including
ordinary `advance` and paired `complete`, use that existing caller with the
selected installed executable, runtime root, owned task and output directory.
Its `native_call` already saves the complete native response/stderr and emits
flushed progress records while the CLI runs. Leave the helper's stdout/stderr
connected to the command tool. An outer `> file 2> file`, end-only subprocess
capture or parsing stdout only after exit discards that live display path;
the caller's retained files make such outer capture unnecessary. The helper's
stdout is a stream of progress and business JSON objects, not one JSON document.

For Operations without a supported helper action, preserve the normal direct
installed-CLI route and use the command transport's existing event-streaming
and retention facilities. Do not invent an action or execution wrapper for
presentation. At existing command-tool returns and required same-handle result
collection, forward new nonempty `presentation.commentary` before the next wait
or result inspection. A tool transcript alone is not chat commentary. An
available start or material-progress observation must not be replaced by plain
"Advance is running" prose.

If the transport itself buffers output or supplies no live events, disclose
that visibility limit and retain start-unconfirmed until actual evidence arrives;
use the existing buffered-result presentation then. This limitation authorizes
no live-log reader, extra poll, display-only AI turn or runtime probe. Apply a
corrected stream path to subsequent invocations only; do not replay, restart,
signal or add a second collector to an already-running call for display repair.

### Chat progress indicators

Show two distinct boundaries in ordinary chat for every sm-workflow invocation.
Before sending it, show **⚙️ sm-workflow 呼び出し準備** with the operation.
At the first tool return with a terminal response, show **⚙️ 🔵 ◀ sm-workflow
応答受信・制御返却 ━━━━━━━━━━━** for the same operation, before inspecting its
business result or starting the next invocation. Use the matching error or
unknown marker if the call failed or its completion is unconfirmed. A quick
call can have no live start/progress event and still has both display boundaries.
For a paired `complete`, the pair brackets the outer Submit/Advance exchange.

Direct installed-CLI calls, including calls whose streams are redirected to
retained files, must forward the same invocation indicators as helper calls.
Forward those records from each already-required tool return, including short
reads and resumed work. For a direct call whose return exposes a usable
terminal response but no presentation record, render `response` from that
same return and forward it in chat.
The AI activity renderer deliberately does not supply them. Before launch and
at actual start/return/error boundaries, use the bundled
`command_activity_record(event, context, observation)` and forward its nonempty
`presentation.commentary` verbatim in the existing commentary update. Plain
"waiting for Advance" prose, raw tool output or an AI actor marker is insufficient.
Use only observations already delivered by the owned command; add no probes,
runtime calls, execution wrapper or display ledger. Do not read live logs solely
to recover a marker. Buffered start/end observations are displayed together as
`出力一括受信`, without claiming live delivery.

This stateless renderer consumes the existing command progress event names:
`invocation`, `runtime-event`, `sbt-event`, `process-terminated`, `response`,
plus observed `launch-failed`, `response-invalid` or `unknown`. The finite helper attaches its
presentation to those same progress records. Direct callers supply the same
observed facts; a `response` is eligible only after actual process termination
and response receipt. A zero exit without a decoded response has no successful
handback presentation. `running` heartbeats and unchanged SBT `waiting` events
have empty commentary. Do not forward them or create a new AI turn for display.
Only real start/end and substantive check/cancellation/error changes are shown.

The optional display-only `intervalPosition=opening|middle|closing` describes
an already selected paired `complete` transport. Intermediate preparation,
Submit and process events do not close that outer interval; its final Advance
response does. This field authorizes no operation and stores no control state.
Absent position means a single call. AI dispatch/work/result rendering remains
separate, with no automatic gear prefix.

Use a bold standalone heading for preparation, start and return, with a compact
detail line underneath. Prefix runtime invocation progress with the running
marker. AI work and result commentary retain their own actor markers, even
when sm-workflow owns the overall Workflow. Text labels carry the meaning alongside icons; these are chat
presentation, not ANSI/background styling, runtime states or a new control ledger.
Keep the gear `⚙️` as the sm-workflow actor marker in every phase, including
running, return, error and unknown; status colors and interval arrows supplement it.

| Marker | Observed boundary |
| --- | --- |
| **⚙️ sm-workflow 呼び出し準備** | Input/output preparation or launch intent; runtime start is not confirmed. |
| **⚙️ 🔵 ▶ sm-workflow 実行開始 ━━━━━━━━━━━** | The selected CLI/runtime has actually started; `制御：sm-workflow`. |
| **⚙️ 🔵 │ sm-workflow 実行中** | Actual ongoing progress/wait for this invocation; keep `制御：sm-workflow` visible. |
| **⚙️ 🔵 ◀ sm-workflow 応答受信・制御返却 ━━━━━━━━━━━** | The owned call returned its response and ended; `制御：親チャット`. |
| **⚙️ 🔴 sm-workflow 起動前エラー** | Confirmed failure before CLI launch; no execution interval started. |
| **⚙️ 🔴 ◀ sm-workflow 呼び出し終了・エラー** | The owned call ended with an observed failure; business outcome/cleanup can still be unknown. |
| **⚙️ 🟡 │ sm-workflow 実行状態不明** | Lost/insufficient visibility; no claim of runtime termination or normal handback. |
| **🤖 🛠️ 親AI作業中** | The parent itself is actively performing work, including editing/fixing code; state the activity. Never use for waiting alone. No blue circle. |
| **🤖 ✨ AI作業中** | An active subagent is doing the work; identify the responsible subagent and activity. No blue circle. |
| **💬 📨 別チャットへ依頼中** | A request was sent to another chat; active work is not yet confirmed. Name the destination chat and task. |
| **💬 ✨ 別チャット作業中** | Available observations confirm work in the named destination chat; include its activity. |
| **💬 ⏳ 別チャット待機中** | Available observations establish that the named destination chat is waiting; include the known reason. |
| **👤 🟡 ユーザー入力待ち** | A genuine user answer is required. |
| **🟢 完了** | Only the named task/operation is confirmed complete. |

The existing Before checklist supplies preparation details; During supplies the
running detail; After supplies the matching returned/error detail. Repeat the
actual operation and known Session/Step at start and end. Distinguish repeated
calls using an existing output reference or a short display-only ordinal, not
a new receipt/ID. Start and return form one visible interval despite intervening
tool output. Do not use the old generic calling label for preparation and execution.

The helper's `invocation` event precedes Popen and establishes launch intent only.
Use existing runtime-started, actual running/SBT output or command-tool process
observations for the start heading. A transport session for the helper alone
does not establish that the target CLI started. Known preparation failure stays
outside the interval; a corrected authorized attempt starts its own interval
when actually launched. This changes no retry or approval authority.

For `complete`, announce Submit + permitted Advance together and keep one outer
interval until the helper returns. Display intermediate responses inside that
interval with the running prefix, not a parent-control-return marker. A business
Operation named prepare still has its own execution interval after its CLI starts.

If output is buffered, retain preparation/start-unconfirmed until evidence arrives;
then show matching start/end together with `出力一括受信`. Do not imply earlier
live delivery or create extra AI turns to obtain it. If visibility is lost during
execution, use the unknown marker; diagnostic parent activity is not proof the
runtime stopped. A returned Wait condition, accepted Submit, root exit or a
requested SIGTERM alone proves neither whole-Workflow completion nor descendant
settlement. Preserve those distinctions in the detail line.

AI actor commentary is generated by the existing bundled Python helper's
[AI activity API](#program-generated-ai-activity), including actual parent work
and parent waiting. The caller forwards its generated commentary verbatim in
existing dispatch, progress and result handling. A handback, dispatch or result
collection does not establish active parent work. If parent and child both work,
feed their separate actual observations. Keep actual command start/end intervals
separate from those AI observations. Workflow ownership alone never opens or
extends a running interval. Preserve acceptance values, counts and retained links.

Add no AI invocation, agent, continuation prompt, polling/status query, dedicated
wait loop, persisted display state or periodic update for presentation. The
optional finite local display transport below is not a business command and
does not call the installed CLI. Buffering or unavailable coloring is a display
limitation, never permission to restart work or alter timeouts.

### Separate-chat delegation display

At an already authorized send and actual result, pass `actor=chat`, the actual
destination `chatTitle`, purpose and existing reference through the
[AI activity API](#program-generated-ai-activity). Forward
`presentation.commentary` verbatim. An opaque chat ID is not a title.
For an outgoing request this is the destination; for an incoming result it is
the sender, paired with the original request context. For a result reply sent
by the delivery owner, name the original requester as destination. Do not reuse
an unrelated chat's activity context to manufacture a matched result.

In the same ordinary commentary, add an explicit direction line using known
chat titles: `送信：このチャット → <destination>｜<request/result>｜依頼ID=<id>｜<purpose>`
or `受信：<sender> → このチャット｜<request/result/blocker>｜依頼ID=<id>｜<summary>`.
Before sending say `送信予定`; after the actual tool result distinguish `送信済み`,
`送信失敗` and `送信結果不明`. A send acknowledgment confirms transport only, not
destination work or acceptance. For incoming work requests, label them as requests,
not completed results. Missing titles/IDs are explicitly unknown; retain an
available chat ID as a secondary locator. Do not query chat status merely to fill
display fields. Include available titles and return identity in the outgoing
handoff so the receiver can display the actual source without another lookup.

After each actual send outcome and each received request/result/blocker, display
`依頼完了待ち（このチャットからの依頼）` followed by a compact list derived from existing
request/continuation records. Each row contains destination title, request ID or
existing reference, short purpose and last known result state. This is the list
of this caller's outgoing requests, not all machine-wide work or a list of chats
currently executing. Multiple requests to one chat remain distinguishable.

- A confirmed request send adds that request as `結果待ち（実行状況は未確認）`.
  A failed send is not a delivered request; show the failure separately. An
  uncertain send is shown separately as `送信結果不明`, with no automatic resend.
- A final result removes only its matching request from completion-waiting and
  shows `結果受信・採用未確認` separately until normal runtime adoption decides it.
  A progress message or blocker is not completion: retain the request, with the
  observed blocker/human-recovery status where applicable. Duplicate results do
  not remove other requests or trigger another acknowledgment/reply.
- Receiving a new incoming request does not add it to this caller's outgoing
  waiting list. Sending a result reply likewise creates no new waiting entry.
- If there are no pending requests in the retained context, show `なし（把握範囲）`.
  If context is incomplete, say `一覧は把握範囲のみ／他は未確認`; never infer an empty
  global list or destination completion from silence or elapsed time.

The direction and list are companion text to the existing generated activity
marker, not a change to its output or the sm-workflow gear interval. Include the
pending list in the dispatch turn's final handoff as well, so ending the turn does
not hide it inside collapsed commentary. Use existing context only: no new display
ledger, AI call, runtime Operation, thread scan, status query, timer or monitoring.
An event-driven list is a retained observation, not a live dashboard. Follow the
[waiting mode selection](#cross-chat-waiting-mode-selection). Use the snapshot
from the requester wait for reporting, without extra scans to populate this list.
Explicitly enabled parent reporting is the bounded exception for forwarding
already observed destination status; it does consume parent tokens.

Actual working/waiting observations already available may be passed too; silence,
elapsed time or the parent's wait do not establish destination activity.
Keep delayed/buffered observation timing honest in the surrounding existing
update. This grants no messaging, execution or continuation authority and never
ends the ongoing runtime interval.

### Subagent call display

Display the generated **🤖 ▶ subagent 呼出し** after actual dispatch and
**🤖 ◀ subagent 結果受領** when the same agent's result arrives. Keep both
distinct in user-facing chat, even for an immediate result or a dispatch
without intermediate progress. At receipt, identify the same request and
agent and say that the parent has received the result. Its inspection and
runtime acceptance follow separately.

Before each authorized spawn/resume tool call, the caller must display an
ordinary user-facing commentary starting with `subagent依頼：`. Give the short
request name, selected task name or existing agent ID, role, purpose, owned scope
and expected result. Include the selected model ID and reasoning effort in that
same visible line, for example `モデル gpt-6.2-sol / 思考レベル medium（指定）`.
For explicit launch arguments use the requested values. For a fixed registered
role use the known configured values, and for resume use the retained selection
for that agent. Mark each missing value `不明`. The announcement describes
selection and does not establish the actual executing model, start or a new ID.
For a parallel batch, show each assignment before dispatch. Tool payloads,
terminal output and later markers do not substitute for the announcement.

All six native skills use the [AI activity API](#program-generated-ai-activity)
at actual authorized dispatch/resume and result handling, including reuse and an
already authorized alternate. Keep purpose, role, Session/Step and the real
returned agent ID together. A role is not an agent ID. Retain the returned
`activityContext` in the caller's ordinary continuation context for the paired
result; this is neither a display ledger nor an admission receipt.
Pass the selected model and reasoning effort to the renderer as `requested` or
`configured` context. A dispatch acknowledgement that returns only the agent ID
leaves that context intact. Use `observed` only for explicit runtime data.
For a spawn with explicit launch arguments, map `model` to
`context.model` and `reasoning_effort` to `context.reasoningEffort`, each as
`{value: <selected value>, provenance: "requested"}`. The observation
whitelist ignores `configuredModel` and `configuredReasoningEffort`.
Reuse that dispatch record's `activityContext` for working, waiting and
receipt for the same agent. Alongside the working marker, identify the subagent
or its role in the existing progress text.
Repeat the announced request name and actual agent identity in the surrounding
dispatch/result update. At receipt, summarize the actual outcome and any known
remaining issue without implying inspection or native acceptance. A failed or
unconfirmed spawn/resume gets an explicit failure/unknown update for that same
request, rather than disappearing from the visible sequence. Preserve existing
context only; add no display ledger, status query or agent invocation for this.
Unknown metadata never blocks work or grants a new call, retry or acceptance.

### AI activity quick start

Use this display contract in both sm-* callers and cncf-* development callers,
including parent-only investigation, editing and repair. Forward the generated
`presentation.commentary` in the ordinary chat update at the actual work,
wait and result boundaries. An `Agent Use` block or a tool's JSON/stdout output
alone does not deliver the icon message to the user. The separate-chat
`progress_reporting=OFF` setting disables periodic destination reports only;
it does not disable these actor transitions or result receipt messages.

Resolve the selected skill directory before locating the existing shared
renderer: installed skills may be symlinks. In this workstation layout,
`Path.home() / ".agents/skills/cncf-command-execution"` resolves to the directory
under `common/codex/skills`. Its `parents[1] / "scripts/native-goal-command.py"`
is the renderer; there is no per-skill `scripts/native-goal-command.py`.
Use the already available caller module where possible. For a local Python
invocation, use the configured uv-managed Python and import the module without
running its business-command entry point. Replace the example purpose with the
current actual work:

```python
from pathlib import Path
import runpy

skill = (Path.home() / ".agents/skills/cncf-command-execution").resolve()
caller = runpy.run_path(str(skill.parents[1] / "scripts/native-goal-command.py"))
record = caller["ai_activity_record"](
    "working", {"actor": "parent", "purpose": "スキルのAI作業表示を確認"},
    {"started": True})
print(record["presentation"]["commentary"])
```

The caller then copies that returned commentary verbatim into its user-facing
commentary message, together with its normal progress explanation. This example
reports only the parent's observed work; it creates no Session, agent, file,
monitor or workflow Operation. Supply other known context through the same API;
unknown model, Session or Step values do not prevent display. Use actual child
identities and observations for subagent dispatch/work/receipt; do not create a
child or poll one just to exercise its icon. Rendering failure is reported in
the ordinary update and does not block the authorized work.

### Caller activity transitions

The caller supplies activity facts at its existing work boundaries; the renderer
cannot observe the parent's work by itself. This applies to sm-* and cncf-*
callers, including a resumed turn and work without any subagent dispatch.

| Existing boundary | Observation and presentation |
| --- | --- |
| Parent begins a real investigation, planning, edit or repair segment | Send `actor=parent, event=working` and its purpose; forward generated commentary in the first normal progress update, before continuing that segment's tools/work. |
| Before an authorized subagent spawn/resume call | Display `subagent依頼：` with the planned assignment and expected result; do not claim a returned ID or actual start yet. |
| Subagent dispatch/resume returns | Forward the dispatch indication with actual identity and available model/effort provenance. A generic `Agent Use` block does not replace it. |
| Existing dispatch output or worker message confirms active execution | Use the retained dispatch context for `actor=subagent, event=working` and identify the subagent in the same ordinary update as **🤖 ✨ AI作業中**. An acknowledgement without a start observation stays dispatch-only. |
| Parent waits while a child works | Preserve the child's observed activity and distinguish the parent's wait; do not label the parent working merely because it is collecting/waiting. |
| Actual result arrives | Forward the receipt marker at the first caller opportunity, before file/diff inspection or Submit; follow [receipt ordering](#result-receipt-inspection-and-runtime-acceptance). |
| Actual wait or runtime control transfer | Reflect that transition in the ordinary update; preserve existing gear intervals and the actual actor. |

The parent can directly report its own current task activity without claiming
access to hidden reasoning, model identity or runtime status. Missing Session,
Step or model details remain unknown and do not suppress the marker. A genuinely
new segment after waiting/resume gets its own first update; unchanged ongoing
activity does not require heartbeats or a new message per tool/file.

Use the existing API or finite local transport and forward its generated
`presentation.commentary` verbatim. The caller may render multiple already
observed transitions in one ordinary update. Prefer the existing in-process
renderer where available; do not create a persistent display input/ledger or
start a sm-workflow Operation merely to announce work. For example, inside the
existing caller integration (the output function is the ordinary chat sink):

```python
# The parent is now beginning the authorized work, not merely waiting.
display = native_caller.ai_activity_record(
    "working", {**known_context, "actor": "parent", "purpose": actual_purpose},
    {"started": True})
forward_commentary(display["presentation"]["commentary"])
```

Rendering or printing JSON in a terminal is not proof of chat delivery. The
caller must forward the commentary; unavailable rendering/delivery is reported
in the next ordinary update without pausing business work or retrying effects.
No extra AI invocation, status poll, recurring monitor or assertion of unobserved
activity is permitted. Existing generic agent-disclosure fields may accompany
this generated presentation but cannot replace the actor marker in sm-* work.

### Result receipt, inspection and runtime acceptance

These are three different observations. Apply this order to subagent results
and, when applicable, separate-chat results:

1. **Result received**: at the first caller opportunity after an actual result
   is delivered, render `event=result` with the retained actor/request identity
   and forward `presentation.commentary` immediately. Do this before reading
   result files, inspecting diffs, deciding acceptance or sending a runtime
   Submit. Any first prose statement that the worker returned must include the
   receipt indication in that same update. Do not defer it until inspection.
2. **Content inspected**: after the actual artifact/diff checks, report
   `内容確認済み` and the relevant conclusion in the normal progress update.
   An inspection finding is reported as such; it does not undo receipt or prove
   validation success or native acceptance. Do not emit another receipt marker.
3. **Runtime accepted**: report `runtime受理済み` only after the runtime's actual
   attributable acceptance response. Submit dispatch or transport completion is
   not acceptance. Rejected, failed or unknown acceptance stays explicit.

A worker's `completed` means that worker finished its assigned work. It does
not mean tests passed, review passed, the Step/Phase closed, or the runtime
accepted the submission. At receipt, keep native `accepted=unknown` unless
actual native acceptance is already known; never map a worker's generic
acceptance flag into native acceptance. A known negative native response can
be reported as false, but pending submission alone is not rejection.

A result queued during a tool call is displayed when the parent next receives
control; do not poll or interrupt an existing operation for display. Preserve
any independently observed live sm-workflow invocation; AI receipt never
starts or extends its gear interval. If displaying an already-handled result
late, say `受領済み結果の表示（遅延）` in the surrounding update instead of claiming
it just arrived. Repeated views of the same result do not create a new receipt.
A completion-only status without a returned result is not result receipt.

The existing renderer already supplies the receipt marker. This is caller
ordering, not a new business Operation, workflow state or display event type.
Use existing updates for inspection and acceptance; add no AI call, monitoring,
extra validation, wait or persistent display ledger. Preserve Sessions, history,
repair counters, existing results and the native submission/acceptance contract.

### Program-generated AI activity

The bundled `scripts/native-goal-command.py` owns deterministic AI presentation.
`ai_activity_record(event, context=None, observation=None)` returns JSON-safe
`kind=native-ai-activity`. `observe_ai_activity(...)` attempts the existing
`display_observation` sink once and returns the record best-effort. Neither
changes native-command-progress records, business responses or runtime state.
The record contains `event`, sanitized `activityContext`, whitelisted
`observation`, `pairing`, `identityConflict`, explicit/unknown `started`,
`accepted`, `outcome`, effective `model` and `reasoningEffort`,
`presentation.commentary` and `delivery`.

Input schema:

| Field | Supplied actual metadata |
| --- | --- |
| `event` | `dispatch\|working\|waiting\|result\|failed\|unknown`, from an actual observation, never elapsed time. |
| `context.actor` | `parent\|subagent\|chat`. |
| `context.action` | `start\|resume`, when applicable. |
| Context scope | `purpose`, `role`, `sessionId`, `stepId`, `agentId`, `chatTitle`, `reference` (an existing locator only). |
| `context.runtimeControl` | `sm-workflow\|parent\|unknown`; retained workflow-management context, not proof of a live invocation or current executing actor. |
| `context.model`, `context.reasoningEffort` | `{value: nonempty-string, provenance: configured\|requested\|observed}`. Unobservable inherited settings remain unknown. |
| `observation` | Only `agentId`, `chatTitle`, `model`, `reasoningEffort` (the latter two require `provenance=observed`), `outcome`, `summary`, `reason`, boolean `accepted` and boolean `started`. |

Scalar input is bounded to 512 characters and strips control/newline/bidi
content while preserving original printable characters in the inert structured
whitelist data. All dynamic scalar values are Markdown/HTML-escaped only when
formatting commentary, preserving their visible spelling without active headings
or links. Exact bounded agent IDs/chat titles remain unchanged in pairing data;
oversized/control-bearing identities become unknown, never truncated matches.
Unknown keys, raw argv/env/tool/result payloads are never copied. Missing or
invalid values stay unknown. The API copies only the closed whitelist.

Dispatch retains the actual returned ID (or existing resume ID), but start is
unconfirmed unless `started=true` is explicitly observed. An empty resume ack
establishes neither start nor acceptance. Results are `pairing=matched` only
when retained original identity and actual result identity match. Missing
identity or conflicting IDs/titles produces unknown attribution; a conflict has
no selected winner. Chat pairing uses the actual title; any supplied ID/title
contradiction also prevents pairing. This is attribution, not authentication.
Result/summary receipt does not establish success or native acceptance. Only
explicit structured observations supply outcome/accepted.

Observed model/effort supersede display selection only with observed provenance.
Original configured/requested selection stays in `activityContext`; it is never
rewritten into an execution claim. Do not infer model from a role, parent or
previous invocation.

The canonical commentary is a bold marker heading and compact detail line.
Parent working and waiting are distinct. Subagent dispatch includes start/resume;
working requires an actual work observation. Result, failure and unknown remain
distinct. Chat records use the actual chat title and their own dispatch, work,
wait, result, failure and unknown markers. Purpose, role, identity, Session/Step
and model/effort provenance are explicit even when unknown. Results show explicit
or unknown outcome/accepted; available reason/summary is bounded.
`runtimeControl=sm-workflow` adds only `進行管理：sm-workflow` to the detail
line. AI commentary never adds a gear/running prefix or asserts control transfer.
Only actual command lifecycle observations create the separate gear start/end
interval. When an invocation returns an AI request, end that invocation interval
and display the requested AI work under its own actor; a still-open Workflow or
Session does not mean the runtime process is running. Concurrent live runtime
work may retain its own separately evidenced progress indication. Local display
helper execution is not a sm-workflow invocation. No extra status probes are needed.

Actual caller integration (shown inside the caller that already owns its normal
authorized dispatch and result handling; `forward_commentary` is its existing
chat commentary output, not a new tool/service):

```python
# The existing caller first announces the planned request in ordinary chat.
forward_commentary(request_announcement)  # request name, task/role, scope, expected result
# Use the same explicit arguments being passed to the spawn, if supplied.
display_context = dict(selected_context)
for source, field in (("model", "model"), ("reasoning_effort", "reasoningEffort")):
    if launch_args.get(source):
        display_context[field] = {"value": launch_args[source], "provenance": "requested"}
# Existing authorized dispatch/resume returns its actual response.
spawn_response = authorized_dispatch(existing_request)
display = native_caller.observe_ai_activity(
    "dispatch", display_context,
    {"agentId": spawn_response["agent_id"]})
forward_commentary(display["presentation"]["commentary"])  # VERBATIM
activity_context = display["activityContext"]  # normal continuation context

# When an existing observation confirms this agent is working:
# display = native_caller.observe_ai_activity("working", activity_context, actual_observation)
# forward_commentary(display["presentation"]["commentary"])

# Later, the same existing result handler receives the actual result.
# Render/forward receipt BEFORE artifact reads, diff inspection or runtime Submit.
display = native_caller.observe_ai_activity(
    "result", activity_context,
    {"agentId": actual_result.agent_id, "summary": actual_result.summary})
forward_commentary(display["presentation"]["commentary"])  # VERBATIM
```

Map only fields actually returned by that tool. Pass known model selection with
its source in `selected_context`, not an assumed observed model. Include outcome,
accepted, started or observed model/effort only when the actual response supplies
them. For resume, keep the existing identity and `action=resume`; an empty response
adds no start claim. Already available start/work/wait observations may be fed
later through the same API, with no dedicated query. Parent INLINE work and
actual parent waits use `actor=parent`; existing-chat sends/results use
`actor=chat` and their actual titles. Forward generated commentary at each of
these existing boundaries, without composing/rebuilding AI markers in the model.

For callers without an in-process Python binding, the same bundled helper has
one optional pure local finite transport:

```text
native-goal-command.py ai-activity --observation /absolute/observation.json
```

Use the documented managed-Python helper invocation. The UTF-8 JSON file is
`{event, context, observation}`, a regular nonsymlink file no larger than 64 KiB.
Only that literal argument pair is accepted. This branch runs before business
argument parsing: no installed CLI, operation, host/task requirement, runtime
mutation or new workflow. Parse its one output record and forward
`presentation.commentary` verbatim; retain its `activityContext` as above.
Invalid/missing input emits safe unknown when possible and display failure
returns zero, including a broken output pipe. No retry is introduced.

`delivery=not-attempted` means pure construction. The emitted record says
`attempted`; the API return says `sink-returned` or `failed`. A returned sink
does not prove chat visibility. Renderer/input/sink failure never raises into
business execution or initiates retries; a safe unknown record is acceptable.
Report failed/unavailable delivery honestly in the next normal update. For
buffered output forward the generated commentary unchanged when received and
label the surrounding update `出力一括受信`; do not claim earlier live delivery.
No extra AI call, agent, wait, polling, status query, display persistence or
sm-workflow command may be introduced solely for this output.

Behavioral regression evidence is authored in
`scripts/tests/test_native_ai_activity.py`; caller integration additionally
requires actual existing dispatch/resume and result forwarding evidence.

## sm-workflow agent definitions

DELEGATED semantic work uses separately named `sm_workflow_*` agent definitions
distributed by sm-workflow and installed in the selected host. Keep these
definitions independent of existing `cncf_*` agents, CNCF skill invocations and
CNCF-specific Manifest/verifier protocols. Each agent consumes its actual issued
sm-workflow scope and returns the declared typed result and genuine evidence.
Implementation/repair and independent review retain distinct responsibilities;
an editor does not become its own independent reviewer.

The original host configuration selects the actual installed role, model and
reasoning effort. Do not silently reuse a CNCF agent, invent a registered role,
copy model identity from another executor or force routine INLINE work into a
child. Missing definitions are an installation gap for that delegated work.
Retain the existing Session/selection and continue unaffected work.

The distribution target is an sm-workflow bundle including the agent definition
files alongside its skills/contracts. On this workstation the shared copies
belong under `development-workstation/common/codex/agents/sm_workflow_*.toml`;
deploy agent definitions as regular files under `~/.codex/agents/`, separately
from skill discovery symlinks. Preserve existing CNCF definitions. Edit the
shared workstation sources first and reflect selected changes into the product
at the chosen integration boundary.

The workstation distribution provides five semantic roles: `sm_workflow_analysis`,
`sm_workflow_design`, `sm_workflow_implementation`, `sm_workflow_repair` and
`sm_workflow_review`. The workstation retains `sm_workflow_chat_waiter` for
legacy installations and already assigned waits. The current skills wait in the
requester using Codex chat tools. Initial semantic definitions select `gpt-6.1-sol` / `medium` and
omit inherited skill-discovery instructions. Analysis/design/review stay
read-only by their semantic contract; implementation/repair own only the paths
explicitly issued. Role instructions do not create filesystem permissions.
The parent admits matching actual profile/identity and genuine independence;
never relabel an inherited or different execution as the selected role.

The workstation's `scripts/sm-workflow-tools.py` packages seven sm-* skills,
twenty-two contracts, four scripts (finite caller, passive authority caller,
offline caller-format migration and logical dispatch adapter), six agent files
and a standalone installer (42 files). It installs only
those selected files, shared skill links and regular deployed sm agent files.
Dry-run is the default; occupied discovery paths or differing contents stop
before writes unless file replacement was explicitly selected. See the
workstation's `docs/design/sm-workflow-agent-distribution.md` for installation.
The product's normal `skillBundle` and component JAR contain seven skills,
twenty-two contracts, four scripts, five semantic agent definitions and portable
`install-sm-workflow.py` (41 files); the workstation waiter is not a product role.
Product installation follows
`docs/spec/native-client-installation.md`; the selected installer works without
a development-workstation checkout. File installation and actual newly loaded
agent behavior remain separate verification boundaries.
Installed `sm-workflow` command calls remain parent-owned with scoped escalation.

### Ordinary sm semantic role resolution

This is the owning contract for sm-goal-phase and other sm-* callers resolving
runtime-issued semantic work. The route is:

`issued context + retained selection -> sm caller adapter -> selected sm role -> actual result -> native admission`

1. Consume the actual issued work, required result type and retained selection.
   Preserve profile/mapping revision, disposition, selected role/model/effort,
   independent context and the captured alternate policy. A pending assignment
   is not reclassified from today's configuration, role naming or a future plan.
2. For admitted INLINE, keep the issued inline route. For DELEGATED, use the
   corresponding installed `sm_workflow_*` role through the existing sm caller
   tools. If an adopted version supports a selected-profile launch adapter, use
   only that sm-owned adapter and captured contract. Newly issued GoalPhase work
   explicitly capturing `standard-2026-11-01` uses the product-owned
   `goal-phase-dispatch.py` and its captured mapping as described by sm-goal-phase.
   Legacy work retains its registered role and original alternate policy. Never
   override a fixed role or reinterpret pending work through a new mapping.
3. Do not use `cncf-agent-execution` as the normal role resolver, including when
   intending to keep an sm role name. Its model-selection tables, Terra/Sol
   fallback, custom-first/equivalent-profile rules, CNCF Manifest preparation,
   verifiers and runners are not this caller's contract. Shared TOML storage and
   generic collaboration tools do not imply shared workflow policy.
4. Apply availability/alternate handling only from the retained sm execution
   contract. Preserve historical policy versions; do not import CNCF rules or
   infer permission to replay an executed/uncertain attempt. Keep genuine gaps
   attributable without fabricating new Human gates or a replacement Session.
5. Observe actual launch/result identity and independent-review scope, return
   the declared typed result, and let normal native admission decide acceptance.
   Receipt, inspection and native acceptance remain separate observations.

If a caller unnecessarily read a CNCF skill but launched the correct sm role
under its issued contract, correct subsequent routing guidance and continue.
Do not discard an otherwise valid review, re-Start/re-Submit, reset counters or
repeat successful checks merely because of that reference. Investigate an actual
profile/context mismatch through the existing sm result path; this rule does not
bless mismatched execution as valid.

An expressly authorized independent cncf-* maintenance producer may use
`cncf-delegate-task` and CNCF agents for its own delivered repair. Its requester
continues the original sm Session and normal acceptance. Producer authorization
is not authority to move the consumer's ReviewStep/RepairStep to CNCF. A repository
named sm-workflow or a dependency defect alone does not select the special route.

This clarification introduces no new resolver, wrapper, dispatch ledger, model
probe, poll, AI call or permission step. Preserve active reviewers, ongoing work,
Session/Plan, complete history, repair counts and valid verification evidence.

## R1 — First issued input and retained scope

`prepareC(issued, mapping, origin)` accepts the actual public Request context,
the canonical mapping snapshot resolved for that first issue and the genuine
origin executor descriptor. The current protocol and four finite thinking modes
analysis, design, implementation and review are accepted. Native handle, run,
continuation and expected revision are required. CNCF converts the unchanged
issued executionRequirement and consumes its disposition; pending work is never
passed back to first-issue `mapping.resolveC`. Unmapped invocation, Human, Wait
or Terminal is not a successful mapped execution.

The Prepared value retains the original public context and host Session. The
public scope, required result type and completion route stay with the issued
request. Concrete profiles and observed attempts belong to the host's existing
logical/task resource binding, outside the Goal business Plan and Workflow
storage. The host retains this Session at first issue for redisplay and restart.
A missing original session or mapping snapshot is a gap; a newer configuration
cannot reconstruct a pending selection.

## R2 — Operational sequence and honest outcomes

1. Obtain the actual issued scope and original canonical configuration through
   the existing host entry. Prepare and retain the host Session once.
2. Consume `session.nextSelection`. Perform only actually available and admitted
   INLINE work or the configured independently scoped executor through existing
   host tools. INLINE needs no mandatory child. Provider/model names alone grant
   no capability, availability or permission.
3. `Prepared.recordC(selection,outcome)` delegates to CNCF using an actual
   observed outcome. Record honest detail and only observed resources before
   selecting any configured alternate. UNAVAILABLE/ProviderUnavailable and
   DECLINED/CapabilityDeclined permit the one explicitly configured alternate
   only after the first DELEGATED attempt. BLOCKED/AuthorityBlocked or
   ResourceBlocked and FAILED/SemanticFailure or UncertainExecution stop.
   Host transport/callback uncertainty is FAILED/UncertainExecution. Exhaustion
   and INLINE unavailability stop. No retry or placement escalation exists.
4. Only final operational COMPLETED with an actual matching executor can supply
   completion evidence to the ordinary semantic Submit. Submit still converts
   the typed semantic payload and owns the saved requirement, evidence, CAS and
   independent review admission. Its actual accepted/message/outcome controls
   whether the host separately invokes explicit Advance. Rejection or uncertainty
   cannot trigger success-path Advance.

Missing metrics remain unknown: elapsedMillis, inputTokens, outputTokens,
monetaryCost with paired currency, and machineIdentity are optional observations,
not estimates, inferred zero/cost, benchmarks or permission limits. CNCF enforces
nonnegative known numbers and concrete profile/context relations. UNAVAILABLE
has no executor; COMPLETED requires the actual executor. Other noncompleted
outcomes may omit it, but any supplied descriptor must match the selected work.
COMPLETED says work returned, not that its semantic result was accepted.

## R3 — Completion evidence and independent admission

`completionEvidenceC(references)` requires genuinely supplied artifact references
and final COMPLETED. It projects existing `WorkflowExecutionEvidence` with the
actual executor context as workerIdentity, actual model as modelIdentity and
existing `WorkflowExecutionObservation`. It creates no business result, Human
answer, reviewer evidence, authentication, consent or effect authority. Required
review must still be independently prepared and admitted through its existing
host review path, even after execution completion.

The assigned semantic worker returns only its requested meaning and normal typed
result. Provider DECLINED, BLOCKED or uncertainty returns control to the host
without a fabricated completed or decision-needed payload, Submit or eager
Advance. A valid semantic decision-needed payload retains its ordinary result
contract and genuine independent Human channel.

## R4 — Restoration contract

The host uses CNCF `encodeC`/`decodeC` for its versioned
`cncf.workflow-execution-harness.v1` artifact, then
`restoreC(originalIssued, decodedSession)`. The bridge checks the consumed typed
work reference and execution requirement for correlation/contradictions,
including contradictory declaration/wire handle fields. It does not compare a
full public/private WorkOrder body or create an immutability certificate.
Unrelated diagnostics grant no authority. Restoration never consults a later
mapping or reads runtime storage. Retained sessions keep their original concrete
selection while new issued continuations use their own first-issue snapshot.

## R5 — Existing ownership and capability limits

The host owns tools, retention, observations and explicit caller control.
CNCF owns generic profile/attempt/resource/execution contracts. The bridge
only projects the actual public issue and completion evidence. Goal policy,
semantic results, mechanical commands, native revision/CAS, review, Human,
commit/repository effects and completion remain with their existing owners.
Missing host/provider binding does not authorize a replacement engine, new
request, generic Process fallback, new service/main or fabricated availability.
Command remains the primary operational route; the client retains its adopted
lightweight connection scope. Split review/full-suite ownership remains unchanged.

## R6 — Three generated public values and two normal Operations

The existing `NativeWorkflowPreparationService` adds two passive COMMAND Operations,
`prepareExecution` and `recordExecution`. Each has exactly one named `input` of
the corresponding generated value type and returns `WorkflowCallerExecutionState`
as a direct structured JSON object. The six importer Operations retain their
existing `WorkflowPreparedImport` output and behavior. These conversions perform
no provider IO, import/save, Workflow Start, Submit or Advance.

| Value | Fields in declared order |
| --- | --- |
| `WorkflowCallerExecutionPrepareInput` (COMMAND) | required `issuedWorkContext: WorkflowIssuedWorkContext`, `mapping: string`, `origin: string` |
| `WorkflowCallerExecutionRecordInput` (COMMAND) | required `issuedWorkContext: WorkflowIssuedWorkContext`, `session: string`, `observation: string`; optional `reference: string` |
| `WorkflowCallerExecutionState` | required `session: string`; optional `nextSelection: string`, `completionEvidence: WorkflowExecutionEvidence` |

The issued DTO is inline structured data from the actual Request. Input file
Strings are exact absolute local `file:` URIs: mapping is the original canonical
`cncf.workflow-execution-profile-mapping.v1` snapshot; origin is the existing
`sm-workflow.execution-origin.v1` wrapper. Preparation consumes its `current`
descriptor without re-resolving the wrapper's preference for already issued work.
Record's session URI selects the explicit previously retained artifact;
observation selects one flat CNCF attempt JSON containing its supplied ordinal
and concrete profile. A supplied selection must match the retained Session's
current selection; the helper never substitutes `Session.nextSelection` for it.

## R7 — Current reader, wire aliases and opaque retention

`NativeCallerExecutionInput` borrows only the normal executing call's
`String => Consequence[String]` reader. All required and optional URI metadata
validates before acquisition. Prepare reads mapping then origin. Record reads
Session then observation and restores the Session against the actual issued DTO;
it reads one reference only for COMPLETED. Existing CNCF current-resource roots,
provider classification, UTF-8, byte/stream caps and release remain the owners.
Configure narrow `textus.resource.url.file.roots` and
`textus.resource.read.max-bytes` before boot; URI metadata gives no read grant.
There is no application NIO reader, arbitrary public Map, storage writer or
provider engine.

The consumed requirement aliases are `reviewRequired`/`review_required`,
`reasoningClass`/`reasoning_class`, and nested
`originContextIdentity`/`origin_context_identity`. Equal values agree;
contradictions reject before the strict issued decoder. Existing handle aliases
retain the same consumed-field rule. Unknown/missing/type errors remain errors;
generic context and concrete profile controls are never recursively renamed.

The response's `session` is opaque canonical
`cncf.workflow-execution-harness.v1` JSON **text**, and `nextSelection`, when
present, is opaque canonical `encodeSelectionC` JSON **text**. Retain these strings
unchanged outside business Workflow storage. Normal CLI recursive display naming
must not transform their contents. Only `completionEvidence` is ordinary generated
structured data. First-issue preparation requires the original snapshot; restore
requires the explicit retained Session and never reads a later mapping. Missing
original artifacts do not authorize rediscovery, re-preparation or migration.

## R8 — Record outcome and separate semantic completion

COMPLETED requires `reference` to select one existing generated
`WorkflowArtifactReference` JSON. Its generated ValueReader and the bridge create
completion evidence only after CNCF admits the supplied selected attempt and
actual matching descriptor. Noncompleted outcomes forbid a supplied reference
and emit no completionEvidence. Pending and first UNAVAILABLE/DECLINED Sessions
emit their allowed finite nextSelection; blocked, failed, uncertain, exhausted
or INLINE-unavailable Sessions emit no retry or success. Resource unknowns stay
unknown under R2; no metrics or provider availability are inferred.

Operational completion is separate from business acceptance. A caller supplies
the actual returned completionEvidence with its compatible semantic result and
genuine reference to the existing named Submit. It checks that Submit's actual
accepted/message/outcome before a separate explicit Advance. This passive API
certifies no reviewer, operator consent, authenticated provider, effect authority
or semantic success. Newly issued Repository native conflict, independent native
merge review and affected-file native merge repair use explicit classifications
and canonical mapping/origin described in
[Repository native execution](https://github.com/asami/sm-workflow/blob/fa18abd2c53c1b525d355d240f0c7520c68034d8/docs/spec/repository-sync-native-execution.md). The existing
review/repair pair remains required when the key is present; additive optional
conflict must be explicit CODING for a fresh mapped conflict. Omission preserves
unmapped conflict behavior, while explicit null or malformed input rejects.
The exact fresh conflict missing-issued-row branch binds once; pending
issues and completions retain their original requirement and host Session;
new configuration binds only a genuinely new issue. Missing mapped classes or
dispositions still reject. Fresh Split partition/scope DESIGN and reasoning
ANALYSIS now consume the explicit finite input in
[Split native execution](https://github.com/asami/sm-workflow/blob/fa18abd2c53c1b525d355d240f0c7520c68034d8/docs/spec/split-phase-native-execution.md). Their inherited
independent-review obligation stays intact; saved issues and retained Sessions
are reused through normal completion even after mapping replacement. Human
and other unbound Repository operations retain their original obligations. A
conflict decision-needed result uses its real separate Human channel, whose
response has no issued work context. This binding installs no provider.

## R9 — Ordinary command and caller loop

Select complete concrete input files yourself from the actual Request and
explicit artifacts. With the explicitly selected installed executable, the two commands are:

```sh
(cd "$runtime_root" && "$installed_executable" org.simplemodeling.textus.SmWorkflow.NativeWorkflowPreparationService.prepareExecution \
  --input "$(cat "$task/prepare-execution-input.json")" --json)

(cd "$runtime_root" && "$installed_executable" org.simplemodeling.textus.SmWorkflow.NativeWorkflowPreparationService.recordExecution \
  --input "$(cat "$task/record-execution-input.json")" --json)
```

The existing finite `native-goal-command.py` offers `prepare-execution` with
`request,mapping,origin`, and `record-execution` with
`request,execution-session,observation` plus optional `reference`, in addition to
the shared executable/runtime-root/task/action/output options. It checks the retained outer
Goal ID against Request.sessionId, accepts the existing Goal-owned Repository
review/repair/conflict child routes, and forwards the whole nonempty issued DTO unchanged.
The conflict pair is exactly `submitRepositorySyncWorkResult` with
`RepositorySyncConflictResult`, with a nonblank issued child ID distinct from
the retained Goal ID. The same Request kind, outer ID and consumed alias checks
apply before dispatch; no conflict body or profile is inferred.
Consumed `issuedWorkContext`/`issued_work_context`,
`nextSelection`/`next_selection`, and
`completionEvidence`/`completion_evidence` aliases must agree. No family or target
selector is added; native mapped input still owns applicability.

The same finite actions accept retained `SplitPhase` routing for only these
exact action/operation/type pairs from the actual selected Request:

| Semantic action | Issued operation | Issued result type |
| --- | --- | --- |
| `submit-split-partition` | `submitPartitionSemanticResult` | `SplitPhasePartitionSemanticResult` |
| `submit-split-scope` | `submitHandoffScopeResult` | `SplitPhaseScopeSemanticResult` |
| `submit-split-reasoning` | `submitReasoningResult` | `SplitPhaseReasoningSemanticResult` |

For Split, both Request.sessionId and submitSessionId must equal the retained
Split ID, the kind must be Request, and all consumed aliases must agree. The
whole nonempty issuedWorkContext remains opaque; no business body, classification,
profile or outcome is inferred. Cross-family pairs, Goal/Repository children
under Split, Split pairs under Goal, or crossed IDs refuse before launch/output.
The route file has only family/sessionId and accepts the three closed families
GoalPhase, SplitPhase and RepositorySync;
original Goal actions keep their Goal-only boundary. The passive calls still
invoke only fixed prepareExecution/recordExecution with the explicit artifacts.
Record selects the original retained Session even after mapping changes.

The [finite caller contract](native-goal-command-task.md) covers separate
`prepare-split-input`, `start-split`, `advance-split` and the three semantic
actions. Passive Split Start preparation follows
[native Start preparation](https://github.com/asami/sm-workflow/blob/fa18abd2c53c1b525d355d240f0c7520c68034d8/docs/spec/native-host-start-preparation.md) and the
[normal preparation contract](https://github.com/asami/sm-workflow/blob/fa18abd2c53c1b525d355d240f0c7520c68034d8/docs/spec/normal-host-preparation-operations.md), including
the current generated input's required participant among its nine fields.
It grants no storage, Start or consent. Split semantic metadata requires object
result with only optional completionFacts/executionEvidence and rejects
resultReference because native owns its assigned destination. Native Human,
effects, independent review and Preview/Apply remain separate under the
[common loop](https://github.com/asami/sm-workflow/blob/fa18abd2c53c1b525d355d240f0c7520c68034d8/docs/spec/common-workflow-operation-loop.md); no helper performs them.

Each action makes one ordinary call in a fresh selected output directory. Along
with `response.json` and `stderr.log`, it retains the actual Session string as
`execution-session.json`, optional selection text as `execution-selection.json`,
and optional structured evidence as `execution-evidence.json`. Record explicitly
selects the preceding call's `execution-session.json` through
`--execution-session`; it never scans candidates, reconstructs a missing Session,
overwrites output, chooses an attempt/provider, auto-Submits or auto-Advances.
Failure/transport uncertainty retains raw streams without promoting completion.
The opaque Session is no business session ID or accepted Submit.

For conflict completion, select that original retained Session when recording
the actual observation, even after configuration changes. The finite
`submit-repository-conflict` action forwards the supplied result/reference and
optional facts/evidence once to the issued Repository child and checks its actual
acknowledgement. Submit retains only a proposal; a separate explicit Advance
owns effects/checks or genuine Human continuation and newly issued independent
review. Prepare/record perform none of those steps.

## R12 — Standalone Repository caller and retained execution

The historical `native-goal-command.py` filename also covers standalone
Repository. The fixed ordinary CncfMain bootstrap, full component FQN,
executable/host/task/action/output pairs, owned directories and exclusive outputs
remain the same. `start-repository` requires input and permits optional proposal;
it forwards the whole strict input object text and supplied `--sessionId` proposal
to `org.simplemodeling.textus.SmWorkflow.RepositorySyncService.startRepositorySync`.
Require actual `outcome=started`, nonblank message and nonblank agreed returned
ID. Exclusively retain `{"family":"RepositorySync","sessionId":"<actual ID>"}`
even if the proposal differs or was omitted. Occupied or concurrently occupied
routing is never overwritten. The regular UTF-8/O_NOFOLLOW route reader accepts
exactly family/sessionId and only GoalPhase, SplitPhase or RepositorySync.
Original Goal and Split actions keep their exact family checks.

`advance-repository` has no additional action options and rereads the saved
Repository route for fixed `RepositorySyncService.advanceRepositorySync` with
the actual retained ID. Require the same actual response ID, nonblank message
and kind Request, Human, Wait or Terminal. Every action waits for its actual
response without a caller execution cutoff, including an Advance lasting an
hour or longer. Monitoring, deadlines and SIGTERM settlement belong to runtime
under the [product SBT lifecycle](#product-sbt-lifecycle). No public family,
operation, service, target or timeout selector is introduced.

Three existing semantic actions accept only these exact Repository pairs:

| Action | Fixed Repository operation | Issued result type |
| --- | --- | --- |
| `submit-repository-review` | `submitNativeMergeReview` | `RepositorySyncNativeMergeReviewResult` |
| `submit-repository-repair` | `submitNativeMergeRepair` | `RepositorySyncNativeMergeRepairResult` |
| `submit-repository-conflict` | `submitRepositorySyncWorkResult` | `RepositorySyncConflictResult` |

Standalone Request.sessionId and submitSessionId both equal the saved Repository
ID. Under GoalPhase, Request.sessionId equals the saved Goal ID while the issued
nonblank child submitSessionId must differ. Split refuses Repository pairs.
Require Request kind and agreeing consumed declaration/snake aliases. Each action
still requires exactly request/submission files. Forward supplied object result
and required object resultReference, with only optional array completionFacts
and object executionEvidence. Only `submit-repository-conflict` also accepts
`resultResource`: supply exactly one object `result` or nonblank string
`resultResource`, together with the same required object `resultReference` and
optional metadata. The helper forwards the URI unmodified as one
`--resultResource` argument; it does not open, copy or split the complete result
file, infer a size threshold or retry using another transport. Other semantic
actions retain their existing inline metadata contract.

For example, the conflict helper's `--submission` file may contain:

```json
{
  "resultResource": "file:///absolute/authorized/complete-conflict-result.json",
  "resultReference": {"identity": "actual-conflict-result", "revision": "1"}
}
```

The referenced UTF-8 file contains the complete `RepositorySyncConflictResult`
JSON object (decision, resolutions and rationale), without wrapper metadata.
The equivalent public native command is:

```text
sm-workflow org.simplemodeling.textus.SmWorkflow.RepositorySyncService.submitRepositorySyncWorkResult --sessionId <actual-issued-session> --resultResource file:///absolute/authorized/complete-conflict-result.json --resultReference '{"identity":"actual-conflict-result","revision":"1"}' --json
```

Exactly one of `--result` and `--resultResource` is required. Resource selection
does no I/O; the owning executing call reads after loading the actual Session
and current assignment. Only absolute local file URIs without authority, query
or fragment are accepted. The normal Resource boundary enforces configured
roots, finite byte budgets and UTF-8 decoding; a URI grants no read permission.
Configure `textus.resource.url.file.roots` and `cncf.resource.read.max-bytes`
for the authorized host before boot. There is no application-specific byte cap
or implicit inline fallback. Current context, revision, result-reference,
participant and conflict-coverage admission still apply to the complete DTO.

The helper has no Purpose submission action. For an actual issued
`submitRepositoryPurposeResult`, use the normal installed Operation directly:

```text
sm-workflow org.simplemodeling.textus.SmWorkflow.RepositorySyncService.submitRepositoryPurposeResult --sessionId <actual-issued-session> --resultResource file:///absolute/authorized/complete-purpose-result.json --resultReference '{"identity":"actual-purpose-result","revision":"1"}' --json
```

The referenced UTF-8 file contains the complete public
`RepositorySyncPurposeResult` object: decision, rationale, every group including
each intended `content.text`, and excludedPaths. It contains no submission
wrapper, source hashes or partial replacement for the issued result. Supply
exactly one of inline `--result` and `--resultResource`; the same required
`--sessionId`, `--resultReference` and optional facts/evidence apply. The owning
ProcedureActionCall reads the Resource after loading the actual Session and
current assignment, using normal Resource/UoW authorization and UTF-8 decoding.
Configure the actual authorized file root and a finite
`cncf.resource.read.max-bytes` budget before host boot; the common default
remains 1 MiB. A URI supplies no permission. There is no helper file reading,
size-based transport fallback, split result or omitted content. Existing
current-context, revision, result-reference, participant and Purpose coverage
admission remain authoritative. Accepted Submit saves the proposal; separate
normal Advance owns candidate checks, acceptance, installation and synchronization.
The conflict transport and its retained qualification remain applicable.

Require the fixed target's actual acknowledgement
ID, Boolean accepted=true, outcome=accepted and nonblank message. Bodies remain
opaque; Submit supplies no effects, retry, authority or automatic Advance.

`prepare-repository-review-input` uses the same fixed review route guard. Saved
family alone selects RepositorySync for standalone or GoalPhase for a child;
kind remains review. One passive normal
`NativeWorkflowPreparationService.prepareEvidence` call carries whole selected
existing request/result/reference/executionEvidence file URIs and explicit
issuer/occurredAt/strict collection. The caller parses no business body or
reviewContext. Success requires a datastore array and retains raw streams without
changing route, configuration or input. It performs no provisioning, review,
consent, Submit or Advance. Original prepare-review-input defaults to GoalPhase;
prepare-repository-input remains passive and routing-independent.

Shared prepare-execution/record-execution admit standalone Repository only for
those three pairs and the same retained request/submit ID. They preserve Goal
own work, three distinct Goal children and three same-ID Split pairs. Forward
the whole nonempty issuedWorkContext unchanged with agreeing consumed aliases.
Prepare selects explicit mapping/origin URIs; record selects the explicit
original retained execution-session, actual observation and optional reference.
The fixed preparation service, opaque Session/selection retention and structured
evidence alias guards remain unchanged. A later mapping cannot reconstruct the
pending Session; these actions execute no provider, infer no profile and
automatically Submit or Advance no result.

Every action makes one normal call. Nonzero exit, runtime timeout, malformed/nonobject/
duplicate/nonfinite/bounded output or uncertain acknowledgement retains raw
streams and stops without retry/progression. The current UoW owns Git/SBT/file/
HTTP effects and checks, direct normal binaries and native SBT locks; all nine
retired main entry points remain retired. There is no application Process fallback
or global product serialization. Genuine Human channels, effects, consent and
independent review keep their native owners. Command stays primary and client
qualification stays light. R1–R9 above retain their original responsibilities.

## Executable specifications

`NativeWorkflowCallerExecutionSpec` E1 covers all four generated public scopes,
generic artifact restoration and actual completion projection; E2 covers
missing/mismatched native references and unmapped/unsupported input; E3 covers
noncompleted outcomes; E4 covers actual descriptor relations, references and
inert diagnostics. These are specification-owned observations.

`NativeCallerExecutionOperationSpec` N1–N4 use the configured normal service and
actual current file reader for four scopes, declared/snake aliases, opaque state,
unavailable/declined alternate completion, stopped/exhausted outcomes, consumed
contradictions, reference metadata and root/byte-cap denial.
`NativeCallerExecutionCliSpec` C1–C2 use independent ordinary CncfMain processes
for actual Start/task retention/Advance, prepare/record, normal semantic Submit
acknowledgement and pending redisplay/restoration despite later configuration.
`test_native_goal_command.py` E25–E29 cover finite typed forwarding, explicit
artifact retention, aliases/task correlation and native uncertainty/refusal;
E25/E26 include Goal/review/repair/conflict routes and agreeing route aliases.
E30–E33 cover one-call conflict Submit, exact child acknowledgement and refusal
of crossed routes, malformed/bounded responses, options and selected files.
E34–E40 add finite standalone Split forwarding: E34 nine-field passive
preparation, E35 canonical Start and four bounded Advance observations, E36
three exact semantic pairs with Split metadata, E37 all three mapped execution
routes/aliases with whole context and explicit original Session, E38 closed
family/issued-route refusals, E39 raw-stream retention on uncertainty and E40
exact option/file/metadata admission. The original E25/E26 Goal/review/repair/
conflict cases and earlier assertions remain required. Adjacent Given/When/Then
and executable expectations describe obligations, not validation results.
Their mocked subprocess observations establish caller specification coverage;
actual installed provider execution, task delivery and genuine two-chat work
require their own evidence.
E41–E47 add exactly seven standalone Repository caller methods while preserving
all 58 earlier methods and their assertions. Each has adjacent Given/When/Then
and mocks only caller subprocess.run. Their R12 mapping is:

| Example | Observable caller obligation |
| --- | --- |
| E41 | Canonical Start/proposals/ID aliases, occupied/raced routing and four explicit Advance kinds; caller waits for the actual response without an execution cutoff |
| E42 | Three same-ID semantic pairs/aliases, opaque required result/reference and absent/present optional metadata with canonical acknowledgement |
| E43 | Saved standalone/Goal-child review family and whole explicit URI metadata, without input/configuration/routing changes |
| E44 | Three execution pairs/aliases, whole context, original Session after mapping changes and opaque returned artifacts |
| E45 | Crossed family/session/child/operation/type/kind/context and contradictory aliases refuse before launch/output |
| E46 | All lifecycle/Submit/review/prepare/record actions retain streams on transport, strict/bounded response or acknowledgement uncertainty without retry/progression |
| E47 | Exact options, strict collection, explicit paths, required resultReference, metadata types and unknown fields refuse before launch/output |

These mocked tests establish finite caller behavior only when validated. They
claim no successful installed/provider/task/two-chat work or Step/Phase acceptance.

`SmWorkflowGeneratedHelpSpec` retains the eight-operation service catalog and
ordinary generated input/output descriptions. Their supplied executors and
outcomes establish no installed provider or genuine user chat acceptance.

The existing `ThreeWorkflowGoalOperationSpec` mapped loop connects E7 first
Routine INLINE, E8 retained pending selection/new configuration and nonprogressing
operational outcomes, and E9 first ReviewStep unavailable-to-alternate completion
to actual independent host review preparation and normal public Submit/explicit
Advance. Its fixtures keep semantic/Human/repair/closure and typed Git/SBT owners.
These examples qualify neither installed providers nor genuine two-chat work.

`RepositorySyncNativeExecutionSpec` E1–E4 cover explicit paired Component
classification and finite fresh binding/refusal. The E5 `mapped repository
execution` example in `ThreeWorkflowRepositoryOperationSpec` connects actual
stored review/public context, normal host preparation, retained Session
restoration after changed configuration, observed completion, separately
prepared review evidence and normal Submit/explicit Advance. E6 in
`RepositorySyncNativeMergeRepairContinuationSpec` connects the original mapped
review, actual INLINE repair, proposal-only Submit, separate application/fresh
ordinary SBT and newly mapped independent review. These remain owned local
Git/SQLite and specification executor scenarios, with no installed provider,
real chat or all-family acceptance claim.

The same adapter's E7 adds optional conflict shape and strict CODING binding,
old-pair compatibility and missing mapped conflict/class refusal. Repository E8
extends the existing public Human-preparation E6 with exactly two mapped conflict
execution variants, resolved and decision-needed, alongside all three original
directions. Each retains the first actual public conflict's canonical Session,
reopens under changed origin/profile/classification without the old coding class,
repeats pending Advance and issued-row reads, and restores the original Session
through CNCF encode/decode. Missing or crossed original-origin observations refuse
without native/file/SBT progression. Valid Submit retains only the proposal;
separate Advance owns materialization and ordinary typed UoW SBT, or the genuine
separate Human question and specification-owned evidenced answer. Each newly
issued ReviewCritical prepares its own later DELEGATED/REQUIRED Session, records
a separately scoped executor and supplies its own independent host review
evidence before normal Submit and separate convergence. These authored programs
await parent validation; they claim no installed provider, genuine two-chat work
or Step/whole-Phase acceptance.

`SplitPhaseNativeExecutionSpec` E1–E4 and the `ThreeWorkflowDirectOperationSpec`
E5 partition / E6 scope-reasoning programs are authored for parent validation.
The latter preserve all legacy directions and add one mapped variant per program,
using actual saved issues, first-public-context host Sessions and completed
specification-owned observations with genuine typed Submit. Partition reopens
under valid changed mapping/origin without its original DesignStandard class,
restores the retained original Session, and retains the compatible presentation
completion observer. Missing/crossed observations refuse before progression;
Submit and next Advance remain separate. No execution success or installed
provider/all-family/two-chat acceptance is claimed by authoring these examples.

### Forward GoalPhase and bounded Task logical dispatch

An explicit `sm-workflow.goal-phase.dispatch-policy=standard-2026-11-01`
selection affects only new missing-issued-row GoalPhase, GoalTask and destination
DelegateTask semantic work. The retained
`operatingProfile.dispatch` carries logical role, disposition and finite candidate
identities; the versioned product caller adapter supplies concrete model/effort
assignments. The legacy caller harness refuses these captures rather than
resolving them through changed current mappings. Old absent/single-alternate
captures retain the existing harness contract.

The packaged `goal-phase-dispatch.py` adapter requires the actual Request, matching
mapping revision, admitted repository path, and caller capability evidence. Its
`launch` object is the exact generic collaboration start arguments, including
native semantic role instructions and a fresh independent context when delegated.
`launched` binds those arguments to the real canonical `task_name` (or exposed
`agent_id` alias); contradictory aliases are refused and the raw return is retained. `complete` binds
that same agent's actual result; `inline_complete` instead binds the issued parent
context without a launch. `execution_evidence` projects the existing public
WorkflowExecutionEvidence fields using actual retained artifact references.
It omits modelIdentity unless separately observed, and uses the retained logical
candidate as profileIdentity. Existing independent host evidence preparation,
Submit and later Advance retain their own authority. The whole dispatch state
retains requested/resolved model and effort, policy/mapping revisions, pre-start
fallback reasons, exact launch arguments/identity, provider observations or unknown
and resources. Keep that caller artifact beside the actual semantic return.

This is an adapter contract, not a new scheduler, provider-availability service,
CNCF agent policy, permission to start extra trial work or assertion of installed
qualification. A capability gap returns unknown/unavailable. A typed confirmed
unstarted model/effort rejection may follow only the captured next edge. Started,
uncertain, policy/refusal and semantic outcomes never enter that fallback.

For this forward logical route, the host registers
`sm-workflow.goal-phase.origin-context` with its genuine current caller context
identity and selects the dispatch-policy. The logical identity does not require
an observed model/provider, concrete mapping or fabricated legacy descriptor.
It must agree with an existing concrete origin if both are configured. Missing
logical context is an explicit preparation gap. Legacy already-issued work keeps
its captured contract. A context registration is not an execution observation.

A caller may supply `request_artifact` to adapter preparation: the exact absolute
local path of its retained public Request JSON. Preparation checks equality with
the original Request and retains both that reference and the unchanged Request.
For Requests exceeding 131,072 serialized characters this reference is required;
the launch message references it instead of duplicating unbounded logs/base64.
Keep the public file available to the semantic worker. There is no private store
access or shortened replacement WorkOrder. Restore checks the captured executor
against the raw launch return and never re-resolves current configuration.
Captured fallback subsets are validated against versioned edges, then followed
exactly; full mapping edges cannot extend the captured chain.

Passive caller observations use the existing normal Phase completion-recording
owner, with the closed property contract in ai-operating-profiles.md. They are
bound to actual retained native work and accepted commits, persisted once in the
completion metrics and reused by recollection. They do not authorize an effect,
change native outcomes or replace independent evidence preparation. Missing or
crossed attribution stays unavailable; metrics never trigger workflow repair.

New GoalTask and destination DelegateTask Starts preflight their prospective
semantic stages through the same pure operating-profile capture used by GoalPhase.
A destination requires a valid actual logical origin with the
standard dispatch policy, or the existing complete concrete mapping/origin pair.
Invalid logical origins, partial concrete pairs and contradictory contexts refuse
before Start effects. Component initialization through
`ThreeWorkflowRuntimeParameters` remains the configuration owner. Logical
GoalTask and destination DelegateTask support both the configured
`sm-workflow.runtime.definition` host/declaration route and the deferred
`StandardWorkflowRuntime` route without a runtime definition. Both logical
routes require the actual typed configured origin and standard dispatch policy;
the deferred route also retains its existing concrete execution path.

### Bounded Task work classification

Fresh GoalTask and DelegateTask inputs may provide `workClassification`. It
contains `implementation`, `repair` and `review`, each with `kind`, `complexity`,
`domain`, `footprint` and optional `nature`. Use `CODING` for code implementation
and repair, `DESIGN` for documentation implementation and repair, and `REVIEW`
for independent review. Complexity is `TRIVIAL`, `SIMPLE`, `STANDARD` or
`COMPLEX`, domain is `PROGRAMMING` or `ENGINEERING`, and footprint is `SMALL`,
`MEDIUM` or `LARGE`. The policy supports `hardest` and `parent-inline`, with
`mechanical` reserved for explicitly mechanical coding. Classification supplies
semantic intent. The existing operating policy selects the execution profile.

Fresh bounded `Implement`, `Repair`, `Review` and `DeliveryAcceptance` Work retains the optional
`operatingProfile` capture together with its resolved native requirement before
suspension. An explicit Task assignment supplies the Implement, Repair and Review
classification. Older inputs that omit it use STANDARD/PROGRAMMING/SMALL with
CODING for code, DESIGN for documentation and REVIEW for isolated Review.
DeliveryAcceptance uses the implementation capability class for read-only
post-delivery judgment after actual checks, required independent review, commit
and selected publication. It is separate from independent Review and owns no
delivery effects. The finite
caller maps those names to the deployed implementation, repair and review roles
using the same unchanged Request, policy and actual tool observations as Phase
work. It owns no validation, commit, publication or retry progression.
After Start, fresh bounded issuance checks host readiness and resolves only its
current stage. Exact DelegateTask Start redelivery uses the retained admission.
Removing the logical origin or standard policy after
Start refuses first Advance before issuing a row or changing the native frontier
or history.

The dispatch-only DelegateTask requester validates its admitted owners, complete
input and profile consistency without needing the destination's execution
mapping. It issues transport work and performs no local semantic implementation.
Actual bounded Submit compares the public `issuedWorkContext` with the stored
assignment and admits completion evidence against that assignment's retained
candidate chain, plus native state, ownership, worker and independent-review
evidence requirements. A delayed context or outside-chain result refuses. Pending
work is recollected unchanged after live host configuration changes.
The public issued context projects the same saved operating capture. Logical
completion requires a nonempty actual worker identity equal to its observed
executor context identity; independent Review then compares that consistent
identity with the retained implementers and repairers. Historical and concrete
captures retain their existing identity rules.

Historical bounded Work with no capture decodes as absent and re-encodes without
the field; present null or malformed captures refuse. Historical absent captures
retain their original concrete checks. Standalone FIX semantic work continues
through its concrete binder, and validation-only FIX remains check-only. The
legacy concrete caller harness and public Operation signatures are unchanged.
These source routes do not establish private D2 delivery or installed
qualification; those remain separate publication and qualification obligations.

## Repository repair scope and complete result transport

The standard FULL_STATE owner derives a fresh failed-check repair assignment
from the actual current integration candidate. It refuses unsupported text or
assignment counts rather than silently issuing only the earlier conflict paths.
The configured assignment limit remains 32 by default, with the existing 128
framework guard.

For an already issued narrow check-origin repair, describe through
`RepositorySyncService.describeRepositorySyncRepairScopeCorrection --sessionId`.
The returned exact target binds its native issue, failed-check receipt, current
candidate, Git frontier and permissible changed text paths. Submit actual Human
direction through `adoptRepositorySyncRepairScopeCorrection`, choosing exactly
`input` or `inputResource`. `AdoptCurrentIntegrationRepairScope` needs explicit
sorted selected paths, the original retained participant's execution, an
existing reference and dedicated independent Human evidence, including
`priorExecutionEnded=true` and `acceptOriginalUncertainty=true`. Available and
unclaimed does not establish nonexecution; original external effects remain
UNKNOWN. `Defer` writes nothing.

Passive preparation uses `prepareEvidence` family
`RepositorySyncRepairScopeCorrection`, kind `adoption`, the actual public
description and supplied answer/reference/execution/participant/issuer/time.
It supplies no termination observation or consent. Native atomic supersession
retains the original issue, failed checks, receipts and history. A separate
ordinary Advance captures and issues the selected subset without changing the
original repair attempt count. Adoption performs no check or source/Git effect.

`submitNativeMergeRepair` also accepts exactly one of `result` and
`resultResource`. A Resource is an absolute local authorized file URI containing
the complete repair result DTO. The owning Resource/UoW reader handles finite
acquisition and UTF-8 decoding before unchanged semantic/current-assignment
admission. The `submit-repository-repair` caller forwards the URI unread with
unchanged result reference, facts and execution evidence, as conflict Submit
already does. No other helper action gains Resource result transport.

## Retained responses and prepared imports

The current public caller returns compact control summaries and exact owner
references. Follow [Workflow response references](workflow-response-artifact-references.md)
for availability, provenance and acquisition; display control output without
expanding full historical bodies. This extends transport while preserving local
waiting-mode, reporting, activity-display and actual-call interval rules.

When an assigned semantic body is represented by `retainedWork`, use the
[finite caller invocation](#finite-caller-invocation) with
`--action read-work --request <control-artifact>` and the ordinary
executable/runtime-root/task/output arguments, or use its
actual `WorkflowRetainedWorkService.readWorkflowRetainedWork` reference. Preserve
the detail wrapper (`availability`, exact `reference`, original `response`);
the dispatch and submission adapters consume it directly. Shared evidence
preparation can receive the exact owner `resource` instead of a copied full
envelope. Do not choose a newer revision, inspect private stores or replay
Start/Advance/accepted Submit to reconstruct content. Legacy complete Requests
remain usable; unavailable, foreign or mismatched detail remains a real failure.

Normal importer preparation may return `retainedImport` after confirmed storage.
Read it through `NativeWorkflowPreparationService.readPreparedImport` and pass
that unchanged complete importer to the existing trusted import route. The
bundled ordinary Goal Human completion caller performs this read before Submit,
retaining its normal visible call interval. Explicit `--preparationFormat
legacy-full` is a selected lower-level compatibility format, not an automatic
fallback after failed/unknown retention. Prepared output grants no execution,
review, Human or acceptance authority.

For an authorized configured checkout list, use
`RepositorySyncService.syncRepositoryDirectories` and retain its aggregate
Session/result reference; see [directory-list synchronization](repository-sync-directory-list.md).
Use the actual `ConfigurationRequired` Session/revision with
`refreshRepositoryDirectoryConfiguration` after correcting normal host settings.
The runtime owns traversal, partial results and effect recovery. Explicit
single-repository/lower-level operations remain available without an AI-owned
per-directory execution loop or automatic discovery of additional repositories.

A GoalTask supplemental validation direction does not grant or preflight SBT execution. Configure the derived additional ordinal workspaces and fixed typed programs; normal CNCF admission occurs at the separate Advance. See [GoalTask supplemental validation continuation](goal-task-validation-continuation.md).
