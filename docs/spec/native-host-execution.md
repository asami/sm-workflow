# Native caller execution handoff

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


Terminology revision 2026-10-23: [runtime/caller terminology](runtime-caller-terminology.md)
is authoritative. Host denotes only a network destination. In this document,
caller execution/dispatch belongs to CallerIntegration, command execution to
Runtime, and configuration-directory selection to RuntimeRoot. Old concrete
historical identifiers remain in original records; the executable names below
use the adopted interface without compatibility aliases. Follow the [adoption plan](../notes/runtime-caller-terminology-adoption-plan.md)
for coordinated API/CLI/configuration cutover and existing Session preservation.

The [Codex caller information contract](host-terminal-source.md) returns a typed
information request to the existing caller and consumes its attributable result
in the same retained continuation. The former fixed ProcessExec/App Server proxy
proposal is superseded, not a prerequisite. Runtime Human authority remains
separate from information collection and implementation approval. The
[adoption plan](../notes/codex-caller-information-adoption-plan.md) records the
remaining implementation/acceptance work without resetting active Sessions.

The existing caller uses public `caller.NativeWorkflowCallerExecution` to connect an
actual generated `WorkflowIssuedWorkContext` from a Request to CNCF's finite
`WorkflowExecutionHarness`. This library bridge does not install a Codex/provider
driver, invoke tools, dispatch an application command or advance a Workflow.
Actual installed host/tool/provider availability, all three skill families and
genuine separate chat/session observations remain Phase 6 acceptance obligations.

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
work types, or let semantic workers execute Phase SBT. The owning workflow still
issues work, runs normal validation and admits results. For the already-authorized
broken-runtime restoration exception, the producer delivery owner coordinates the
existing standalone execution facilities. Missing future RRC support does not
suspend that existing applicable route. Keep RRC implementation in parallel;
never represent standalone evidence as proof that RRC is installed.

Execution/result collection has one owner. That owner collects the running
invocation and returns the actual terminal result; consumer chats must not
independently poll its logs or request progress-only AI turns. Prefer existing
completion notifications; when a wait is necessary, use the existing cursor and
bounded wait without repeated unchanged-state polling. User-facing updates use
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

For the supported finite paired completion, the parent directly executes the
deployed `../../scripts/native-goal-command.py --action complete` relative to an
installed sm skill, passing `--executable <absolute installed sm-workflow>` and
the explicit runtime-root/task/request/submission/fresh-output paths. Use managed Python
and scoped escalation for this exact finite invocation, with no general shell or
interpreter approval prefix. This is the existing host-owned pair, not a command
subagent or a substitute Workflow. The helper launches only the selected
installed executable with `--runtime-root <runtime-root> command <fixed Operation>`; it never
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

## Validation cadence: ADMISSION, FULL and HEAVY

For newly prepared execution plans, use the existing canonical purpose metadata
with this default cadence. Purposes are selections, not three nested suite sets.

| Purpose | Normal timing | Selection |
| --- | --- | --- |
| ADMISSION | Daytime implementation/repair and Step acceptance | A small, nonempty set covering the changed contract and acceptance-critical regressions |
| FULL | Integrated Phase validation before Phase completion | Broad ordinary regression coverage, excluding HEAVY and external-service requirements |
| HEAVY | A separately scheduled overnight batch | Long-running, nested-SBT, multi-process and external-service integration coverage |

ADMISSION targets an aggregate **test-body time of at most
one minute**, excluding preparation and cleanup. This is not a fixed timeout or
a per-test budget; retain actual results even when the target is exceeded. See
[label-driven selection](label-driven-test-selection.md#admission-operating-target)
for timing attribution and declarations.

Do not select an entire HEAVY suite for every repair merely because the changed
case belongs to it. When daytime integration evidence is necessary, select the
affected operations explicitly through the existing FOCUSED route, retaining
their actual cost and requirements. Reuse applicable successful results for
unchanged inputs and coverage; changed behavior or a new failure requires the
affected validation. Suite-name selection without a purpose filter can include
HEAVY operations and must not be treated as an ADMISSION or FULL shortcut.

Plan and host-policy authors own the selections. The semantic skill supplies
their meaning and acceptance coverage; the runtime resolves the captured
selection, prepares its requirements, executes it and retains actual results.
Use the existing purpose/feature queries and exact-operation filters. Do not
add another test catalog, infer purpose from elapsed time or clock time, or
combine all three purposes into the ordinary query.

Where a project has no suitable ADMISSION declarations yet, select a bounded,
nonempty affected FOCUSED set and report its actual coverage while classification
is completed. Do not invent ADMISSION metadata, broaden automatically to HEAVY,
or treat an empty selection as successful admission validation.

The scheduler/caller owns overnight invocation and result notification. Record
the tested revision/input scope and keep overnight results separate from current
Step/Phase results. Review failures in the next development cycle; do not make
every ordinary Step wait for the overnight batch unless its actual acceptance
contract explicitly requires that result. This policy schedules no job itself.

This cadence applies to future plans and explicit supported revisions. It does
not narrow an already captured requirement or alter a running invocation,
Session, accepted work, history or repair accounting. Daytime changes to those
requirements use the existing planning/adoption route.

## Product SBT lifecycle

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
CLI call. Independent AI-owned top-level development tests and publication
retain `cncf_command_runner`, the shared wrapper, normal caches and sbt-native
locking. This distinction changes no general development execution policy.

The former manual registered SBT host bridge is rejected and retired, including
current typed `registered_sbt` execution; there is no V8 execution promise.
V4 host configuration requires explicit migration to V3 with
`registeredSbtHost` removed. No automatic fallback, stored Session edits or
relabeling of historical accepted evidence is permitted. Historical schemas and
decoding remain readable. See the [retirement contract](registered-sbt-host-execution.md).

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

### Chat progress indicators

Use a bold standalone heading for preparation, start and return, with a compact
detail line underneath. Prefix every intervening progress commentary with the
running marker. Text labels carry the meaning alongside icons; these are chat
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
| **🌀 🔵 AI作業中** | Parent/assigned AI reasoning or editing after actual handoff. |
| **🌀 🟣 AIレビュー中** / **🌀 🟠 AI修復中** | Actual AI review or admitted repair. |
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

After confirmed handback, mark the actual AI work/review/repair or user-wait actor.
If actors overlap, name both. Use the swirl `🌀` for AI. Include existing messages,
result/acceptance values, supplied repair counts and retained response/stderr links
without adding output-only operations or exposing secret payloads.

Render these markers in existing commentary/output handling. Add no AI invocation,
agent, continuation prompt, polling/status query, dedicated wait loop, persisted
display state or periodic message solely for presentation. Existing runtime
telemetry remains runtime-owned. Tool buffering or unavailable UI coloring is a
display limitation, not permission to restart work or alter timeouts.

### Subagent call display

For sm-goal-phase, the owning host makes each already-authorized semantic
subagent call visible in normal commentary, including reuse/resume and an
already-authorized alternate. Keep the selected role, purpose, known Session/Step
and the real returned agent identity together. A configured role name is not an
actual agent ID. Use the existing selection/agent definition or dispatch arguments
for the selected model and reasoning effort; show that source and label these
values as configured/requested until execution metadata confirms them. If inherited
settings cannot be observed, say inherited/unknown rather than inferring them
from the parent, role name or a previous invocation. Never change model selection
to improve the display.

Use these compact paired markers in the normal dispatch/result handling:

- **🌀 ▶ subagent 呼出し** — purpose/role, model and reasoning effort with
  configured/requested/observed labels, and known Session/Step. This announces
  dispatch, not confirmed execution. When dispatch returns an actual identity,
  include it in the existing progress update; distinguish accepted from started
  if the tool does not establish execution.
- **🌀 │ subagent 実行中** — actual agent identity and relevant observed progress
  when an ordinary work update is already due. Name the parent and child
  separately if both are working.
- **🌀 ◀ subagent 結果受領** — the same agent identity/role and model/effort,
  actual outcome and concise result. Prefer observed model/effort when supplied;
  otherwise retain the configured labels. A child result is not Step acceptance.
- **🌀 🔴 subagent 呼出し失敗** or **🌀 🟡 subagent 状態不明** — actual
  dispatch failure or uncertain status; do not manufacture an identity, success,
  termination or replacement call.

If runtime-owned subagent events arrive while sm-workflow is still running,
retain the outer **⚙️ 🔵 │ sm-workflow 実行中** marker and show the child detail
inside it. Do not imply control returned to the parent. Buffered observations
may be summarized together as 出力一括受信; unavailable child details stay unknown.
Parent INLINE work uses the existing AI marker and is never reported as delegation.
Use only normal calls, returned metadata and existing events: no extra AI turns,
status/model queries, polling, continuation prompts or display ledger. Missing
display metadata is not a work-admission failure or a reason to pause work.

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

The workstation distribution provides five roles: `sm_workflow_analysis`,
`sm_workflow_design`, `sm_workflow_implementation`, `sm_workflow_repair` and
`sm_workflow_review`. Initial definitions select `gpt-6.1-sol` / `medium` and
omit inherited skill-discovery instructions. Analysis/design/review stay
read-only by their semantic contract; implementation/repair own only the paths
explicitly issued. Role instructions do not create filesystem permissions.
The parent admits matching actual profile/identity and genuine independence;
never relabel an inherited or different execution as the selected role.

The workstation's `scripts/sm-workflow-tools.py` packages the three skills,
five contracts, two finite caller scripts, five agent files and a standalone installer. It installs only
those selected files, shared skill links and regular deployed sm agent files.
Dry-run is the default; occupied discovery paths or differing contents stop
before writes unless file replacement was explicitly selected. See the
workstation's `docs/design/sm-workflow-agent-distribution.md` for installation.
The current product skillBundle still contains no agent definitions; product
packaging integration is pending. File installation and actual newly loaded
agent behavior are separate verification boundaries.
Installed `sm-workflow` command calls remain parent-owned with scoped escalation.

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
[Repository native execution](repository-sync-native-execution.md). The existing
review/repair pair remains required when the key is present; additive optional
conflict must be explicit CODING for a fresh mapped conflict. Omission preserves
unmapped conflict behavior, while explicit null or malformed input rejects.
The exact fresh conflict missing-issued-row branch binds once; pending
issues and completions retain their original requirement and host Session;
new configuration binds only a genuinely new issue. Missing mapped classes or
dispositions still reject. Fresh Split partition/scope DESIGN and reasoning
ANALYSIS now consume the explicit finite input in
[Split native execution](split-phase-native-execution.md). Their inherited
independent-review obligation stays intact; saved issues and retained Sessions
are reused through normal completion even after mapping replacement. Human
and other unbound Repository operations retain their original obligations. A
conflict decision-needed result uses its real separate Human channel, whose
response has no issued work context. This binding installs no provider.

## R9 — Ordinary command and caller loop

Select complete concrete input files yourself from the actual Request and
explicit artifacts. With the explicitly selected installed executable, the two commands are:

```sh
"$installed_executable" --runtime-root "$runtime_root" command org.simplemodeling.textus.SmWorkflow.NativeWorkflowPreparationService.prepareExecution \
  --input "$(cat "$task/prepare-execution-input.json")" --json

"$installed_executable" --runtime-root "$runtime_root" command org.simplemodeling.textus.SmWorkflow.NativeWorkflowPreparationService.recordExecution \
  --input "$(cat "$task/record-execution-input.json")" --json
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
[native Start preparation](native-host-start-preparation.md) and the
[normal preparation contract](normal-host-preparation-operations.md), including
the current generated input's required participant among its nine fields.
It grants no storage, Start or consent. Split semantic metadata requires object
result with only optional completionFacts/executionEvidence and rejects
resultReference because native owns its assigned destination. Native Human,
effects, independent review and Preview/Apply remain separate under the
[common loop](common-workflow-operation-loop.md); no helper performs them.

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
and object executionEvidence. Require the fixed target's actual acknowledgement
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
