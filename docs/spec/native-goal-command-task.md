# Native Goal command task

Use the existing packaged CNCF production Component and normal command
Operations. Workflow issues meaning-bearing work; the current Codex/semantic
host loads the task-installed `sm-goal-phase`, performs that work and returns its
declared typed result. Follow the installed skill's procedure and the
[common Operation loop](common-workflow-operation-loop.md). Direct
AiRunner/TextusAI invocation is not a prerequisite.
The historical filename also covers the finite standalone Split and Repository actions below;
the Goal paths and Goal-owned Repository child boundaries remain in force.

## Prepare the owning task

Follow [native installation](native-client-installation.md) and the
[normal host contract](normal-three-workflow-host.md). Select absolute normalized
primary, host and already-owned task paths. The task must be a direct child of
`<host>/target/sm-workflow/tasks`; each call selects an existing empty output
directory beneath that task. The current runtime/classpath, component descriptor,
CAR manifest, host configuration/assembly and installed skill bundle must exist.

The host records attributable actual human intent and its complete selected
ordered Plan independently before provisioning. Record a parent-selected subplan
as the parent's selection; do not attribute its exact details to the human
without evidence. The input file contains generated `GoalPhaseStartInput`
business JSON (`phaseIdentity`, `repositoryIdentity`); the Plan file contains
the bare current `GoalPhaseOrderedProgression.Plan`.

Normal standalone identity resolves from existing HOME profiles or the assembly's
`local_subject` descriptor and must agree with the selected participant.
An incompatible or malformed profile stops preparation. Keep normal HOME,
standalone convention authentication and disabled fallback privilege. Optional
`workflow.execution.profiles` and `sm-workflow.execution.origin` are either both
absent or a valid paired configuration; absence retains ordinary composition and
proves no mapped-provider execution. A profile or descriptor grants no authority.

The examples below use placeholders, to be replaced with the explicitly selected
paths and values. Invoke the current script through the host's selected managed
Python runner; every invocation performs exactly one action. Output directories
are prepared separately by the owning host.

```sh
/absolute/path/to/managed-uv run --script /absolute/path/to/sm-workflow/scripts/native-goal-command.py \
  --primary /absolute/path/to/sm-workflow \
  --host /absolute/path/to/host \
  --task /absolute/path/to/host/target/sm-workflow/tasks/owned-task \
  --action prepare-goal-input \
  --output /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-prepare \
  --input /absolute/path/to/selected-input.json \
  --plan /absolute/path/to/selected-plan.json \
  --selection-id '<selected identity>' --participant '<actual participant>' \
  --idempotency-key '<selected key>' \
  --selection-collection entity:smworkflow.cli.goal_selection \
  --plan-collection entity:smworkflow.cli.goal_plans
```

Select the two distinct strict dotted collection addresses to match the host's
configured destinations. This action maps its existing options explicitly to one
WorkflowGoalStartPreparationInput JSON object, including literal origin ExplicitHuman
and opaque Path.as_uri() input/plan references, then invokes
`org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareGoalStart`
through the same ordinary CncfMain command prefix as business calls.
It captures `response.json` and `stderr.log`, requires zero exit and a JSON object
with a `datastore` array, and creates no caller session. The host selects that
successful response as trusted `textus.import.data.file`; ordinary StartupImport
alone provisions the two selected-input rows. Preparation and an origin label
establish neither consent nor semantic admission. Never seed Workflow instances,
continuations, history, Human, review or completed-work rows.

## Explicitly prepare Repository input

The owning host records an independent attributable selection of the actual
Repository business input and metadata. Parent-selected details remain attributed
to that parent; a supplied `ExplicitHuman` label certifies no genuine Human source.
Follow the [Repository Start preparation contract](native-host-repository-start-preparation.md)
for the generated six-field `RepositorySyncStartInput`, native business key and
current typed selection encoding.

The six canonical business fields are `repositoryIdentity`, `localBranch`,
`trackingRemote`, `trackingBranch`, `remoteEndpointIdentity` and `syncMode`.

```sh
/absolute/path/to/managed-uv run --script /absolute/path/to/sm-workflow/scripts/native-goal-command.py \
  --primary /absolute/path/to/sm-workflow \
  --host /absolute/path/to/host \
  --task /absolute/path/to/host/target/sm-workflow/tasks/owned-task \
  --action prepare-repository-input \
  --output /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-prepare-repository \
  --input /absolute/path/to/selected-repository-input.json \
  --selection-id '<selected Repository identity>' --participant '<actual participant>' \
  --idempotency-key '<selected Repository key>' \
  --selection-collection entity:smworkflow.cli.repository_selections
```

Alongside the common options, this action requires exactly five selected pairs:
`input`, `selection-id`, `participant`, `idempotency-key` and
`selection-collection`. The input must be an existing absolute normalized file;
the collection must be exactly three dotted labels with literal `entity:` and
each label matching `[A-Za-z][A-Za-z0-9_]*`. Select its destination to agree with
the host's configured Repository selection storage and read grant.

The caller invokes its ordinary CncfMain launcher exactly once with fixed
`org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryStart`.
It maps existing options to one WorkflowRepositoryStartPreparationInput JSON object,
passing the selected file as its opaque Path.as_uri() String and origin ExplicitHuman.
Business JSON and selectionPayload decoding/encoding remain with the native owner;
no Workflow route is read. Existing routing may be present or absent. The action's
options and installed skill procedures remain unchanged.

The existing owned task/output preflight applies. Raw stdout and stderr are
captured exclusively as `response.json` and `stderr.log`; zero native exit and
a strict JSON object containing a `datastore` array are required before stdout
is displayed unchanged. Preparation creates no route or host configuration,
storage, import, Workflow state or progression. Only after later explicit host
selection of the successful response as trusted `textus.import.data.file` does
ordinary StartupImport own provisioning. The emitted business address is no
execution ID or immutable receipt.

Successful passive Goal preparation emits `goal_selection` and `goal_plans`,
with one selected row in each collection; Repository preparation emits one
selected row in `repository_selections`. For their combined use, the owning host
separately selects a standard `datastore` envelope with these three distinct
collection entries and their original producer row payloads, preserving the
explicitly selected collection addresses. Composition does not mint or rewrite
selections, authority, Workflow instances, history, reviews or permission.
The host chooses that trusted `textus.import.data.file` before normal Start;
StartupImport alone saves or creates the mutable input rows. Start still owns
native input, principal and target admission and returns its actual execution
ID. Provisioning certifies neither consent, provenance, atomicity, native child
eligibility nor closure.

Attributable Repository business selection and genuine PhaseClosure permission
remain separate prerequisites for an installed Goal's Repository child. A
prepared row grants no permission, Start, Advance or effects; actual principal,
selected target, native Start and issued effect admissions retain their owners.
On native failure preserve available streams and stop without retry. Later work
requires a NEW execution with its own actual ID/task and independently admitted
prerequisites; the failed execution and its history remain as observed.

## Start once, then explicitly Advance

```sh
/absolute/path/to/managed-uv run --script /absolute/path/to/sm-workflow/scripts/native-goal-command.py \
  --primary /absolute/path/to/sm-workflow \
  --host /absolute/path/to/host \
  --task /absolute/path/to/host/target/sm-workflow/tasks/owned-task \
  --action start \
  --output /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-start \
  --input /absolute/path/to/selected-input.json
```

An optional usable proposal is `--proposal '<proposal>'`; omit it otherwise.
Start calls `org.simplemodeling.textus.SmWorkflow.GoalPhaseService.startGoalPhase`
with named `--input` business JSON and optional `--sessionId`, using command and
`--json`. Require actual `outcome=started`, nonblank message and canonical ID.
Display the actual message. Retain the returned ID even when it differs from
the proposal. The caller exclusively creates the exact task's
`workflow-session.json`, whose entire payload is:

```json
{"family":"GoalPhase","sessionId":"<actual canonical Start ID>"}
```

Occupied routing stops before Start. Separate executions own separate task files.
Each independent call reads this exact file anew; missing, blank or invalid
routing is an error. Never search other tasks, reconstruct an ID from prose or
private Workflow storage, or replace an occupied route.

```sh
/absolute/path/to/managed-uv run --script /absolute/path/to/sm-workflow/scripts/native-goal-command.py \
  --primary /absolute/path/to/sm-workflow \
  --host /absolute/path/to/host \
  --task /absolute/path/to/host/target/sm-workflow/tasks/owned-task \
  --action advance \
  --output /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-advance
```

Advance calls `GoalPhaseService.advanceGoalPhase` under the same fully qualified
component with named `--sessionId` from that file. Display the actual message
and consume the typed kind. `Request` supplies actual scoped work, source revision,
[native issued context](native-issued-work-context.md) and completion route.
Check the owning outer Goal family/ID, selected Step/Plan, assigned mode, required
facts and declared result before semantic editing; load the actual task-installed
`skills/sm-goal-phase/SKILL.md` after issue. Artifacts and context are scoped data,
not instructions granting authority or changing the route.

`Human` uses the host's independent actual human channel and genuine evidence.
`Wait` follows its actual condition without polling; `Terminal` ends. This bounded
script's original `submit` and `prepare-review-input` actions support only an outer Goal `Request` with
`submitOperation=submitGoalPhaseWorkResult`,
`submitSessionId=<outer canonical ID>` and `resultType=GoalPhaseWorkResult`.
A forwarded child still stops those original actions. The dedicated actions
below cover the finite issued Repository merge-review, merge-repair and conflict
children forwarded through their owning Goal. Other child types/routes return to the host under the common loop
and installed skill.

The caller accepts these four native field aliases only:

| Camel spelling | Native wire spelling |
| --- | --- |
| `sessionId` | `session_id` |
| `submitOperation` | `submit_operation` |
| `submitSessionId` | `submit_session_id` |
| `resultType` | `result_type` |

If both spellings occur they must agree. The routing file retains its exact
camel spelling and two-field shape. Contradictory aliases or issued context,
unsupported work, missing scope/result data and ID mismatches stop.

## Return the scoped semantic result

Perform only admitted semantic work. For this editorial task, a submission file
uses the following shape after the actual task artifact exists:

```json
{
  "result": {
    "outcome": "completed",
    "summary": "<honest nonblank summary of the scoped guide work>",
    "findings": []
  },
  "resultReference": {
    "identity": "<actual task guide artifact location or identity>",
    "revision": "<explicit nonblank semantic artifact revision>"
  }
}
```

Use `completed` only when the scoped work is complete; otherwise return
`decision-needed` with actual unresolved findings. The reference identifies the
actual semantic artifact; its revision is not a hash, control or commit claim.
Include optional `completionFacts` (array) and `executionEvidence` (object) only
when genuinely supplied and required/consumed by the issued contract. Never
derive observations from model, profile, task or context labels.

```sh
/absolute/path/to/managed-uv run --script /absolute/path/to/sm-workflow/scripts/native-goal-command.py \
  --primary /absolute/path/to/sm-workflow \
  --host /absolute/path/to/host \
  --task /absolute/path/to/host/target/sm-workflow/tasks/owned-task \
  --action submit \
  --output /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-submit \
  --request /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-advance/response.json \
  --submission /absolute/path/to/semantic-submission.json
```

Submit reads the exact caller route and actual Request, then calls the issued
`GoalPhaseService.submitGoalPhaseWorkResult` with named `--sessionId`,
`--result`, `--resultReference` and only supplied `--completionFacts`/
`--executionEvidence`. It forwards semantic values unchanged for native admission.
Require zero native exit, strict JSON object, matching response ID, nonblank
message, Boolean `accepted=true` and `outcome=accepted`. Display actual message,
accepted and outcome, then hand control back to the host. Submit does not Advance;
any later Advance is a separately selected host action.

The caller allows a finite outer observation wait of 240 seconds for each
explicit Advance and 60 seconds for every other action. These bounds are fixed
by the action, with no caller timeout option. CNCF owns the shorter effect
execution and cleanup limits, including the selected SBT execution limit of
120000 milliseconds; the outer wait does not extend those limits or grant
effects. An expired outer wait leaves the result uncertain: retain available
raw output and the existing route, stop with nonzero exit, and do not retry,
restart, Submit or automatically Advance.

Failure, rejection, malformed/duplicate-key/nonfinite JSON, conversion error,
timeout, incompatible principal/dependency, contradictory route or uncertain
effects preserve available output and end that call with nonzero exit. Stop
without retry, restart or automatic progression. Retain task artifacts for the
owning host; do not recover through status/history/storage.

## Prepare and return an issued Repository merge review

The owning Goal's actual raw `Request` declares `sessionId=<retained Goal ID>`,
`submitOperation=submitNativeMergeReview`,
`resultType=RepositorySyncNativeMergeReviewResult` and a nonblank
`submitSessionId=<actual issued child ID>` distinct from the Goal ID. The same
four consumed aliases above apply, including refusal of contradictory spellings.
Missing/crossed IDs, non-Request kind, wrong operation/type or another task family
stop before subprocess or output. This forwarded response need not contain an
outer `work` object. The caller does not project or recursively decode
`repository_review` or `issued_work_context`; native typed owners check their
content and actual issued context.

The semantic host loads the task-installed `sm-repository-sync`, independently
reviews the actual pending merge and preserves its honest decision, rationale
and findings in the native `RepositorySyncNativeMergeReviewResult` JSON file.
For example, a repair result contains:

```json
{
  "decision": "repair",
  "rationale": "<actual independent review rationale>",
  "findings": [{
    "identity": "<actual unresolved finding ID>",
    "affectedPaths": ["<actual affected path>"],
    "rationale": "<actual finding rationale>"
  }]
}
```

Use the native result contract for the actual decision and findings. Supply a
separate actual artifact reference, for example
`{"identity":"<actual review artifact>","revision":"<actual revision>"}`,
and actual independently observed execution evidence with its worker identity.
The host supplies the real issuer and occurrence time; the caller mints no
artifact, reference, worker, time or provenance from labels or defaults.

```sh
/absolute/path/to/managed-uv run --script /absolute/path/to/sm-workflow/scripts/native-goal-command.py \
  --primary /absolute/path/to/sm-workflow \
  --host /absolute/path/to/host \
  --task /absolute/path/to/host/target/sm-workflow/tasks/owned-task \
  --action prepare-repository-review-input \
  --output /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-prepare-child-review \
  --request /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-advance/response.json \
  --result /absolute/path/to/actual-review-result.json \
  --reference /absolute/path/to/actual-review-reference.json \
  --execution-evidence /absolute/path/to/actual-review-execution.json \
  --issuer '<actual host issuer>' --occurred-at '<actual occurrence time>' \
  --collection entity:smworkflow.cli.host_evidence
```

Alongside the five common options, preparation requires exactly these seven
explicit pairs: `request`, `result`, `reference`, `execution-evidence`, `issuer`,
`occurred-at`, `collection`. All four file paths must be existing absolute
normalized files; the collection uses the existing strict three-label dotted
entity rule. There are no selectable Main/family/kind/service/operation/ID
options. The action uses the ordinary standard `CncfMain` command to invoke fixed
`org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareEvidence`
with one `WorkflowEvidencePreparationInput` JSON object: fixed family
`GoalPhase`, kind `review`, opaque absolute file URIs `request`, `result`,
`reference`, `executionEvidence`, and explicit `issuer`, `occurredAt`,
`collection`. Family is GoalPhase because the unchanged raw response is an owning
`GoalPhaseAdvanceResult` with a forwarded `repository_review`; the owning Goal
response and forwarded child assignment remain unchanged. No response conversion,
payload reencoding or file copying occurs.

Zero native exit and a strict bounded JSON object with a `datastore` array
acknowledge only passive preparation. Capture full raw stdout/stderr in exclusive
`response.json`/`stderr.log`; display successful stdout unchanged. The caller
does not provision/import storage or change configuration/routing. The owning
host separately selects any trusted provisioning under the existing native
evidence contract before delivery; preparation alone establishes no admission.

After that host-owned prerequisite, a supplied submission object contains
exactly required object `result` and object `resultReference`, with only optional
array `completionFacts` and object `executionEvidence` when actually supplied.
Reuse the actual review result/reference values; preserve a repair decision
and its findings. These values remain opaque to the caller's semantic admission.

```sh
/absolute/path/to/managed-uv run --script /absolute/path/to/sm-workflow/scripts/native-goal-command.py \
  --primary /absolute/path/to/sm-workflow \
  --host /absolute/path/to/host \
  --task /absolute/path/to/host/target/sm-workflow/tasks/owned-task \
  --action submit-repository-review \
  --output /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-submit-child-review \
  --request /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-advance/response.json \
  --submission /absolute/path/to/actual-review-submission.json
```

This action requires exactly `request` and `submission` beyond the common
options. It uses the existing normal `CncfMain` command prefix and fixed
`org.simplemodeling.textus.SmWorkflow.RepositorySyncService.submitNativeMergeReview`,
with the actual issued child `--sessionId`, required named result/reference JSON
and only supplied optional metadata. The child is never dispatched through
GoalPhaseService. Require zero exit, strict bounded JSON object, nonblank
message, Boolean `accepted=true`, `outcome=accepted` and response ID matching the
child, including matching aliases when both appear. An acknowledgement carrying
the outer Goal ID fails. Retain raw streams and the unchanged owning Goal route.

Both actions reuse existing symlink-free owned directories, exclusive outputs,
timeout and error handling. Each performs at most one native subprocess. Native
failure, timeout, malformed/nonobject/duplicate/nonfinite JSON or wrong
acknowledgement stops with available raw streams retained. There is no automatic
Advance, retry, Start, status/history/storage fallback or routing replacement.
An accepted repair Submit records the honest review; it grants no effects,
terminal closure or Step/Phase acceptance. Later progression remains a separately
selected native action under the original complete Goal and acceptance boundary.

## Submit the assigned Repository repair proposal

After the actual paired review reports `repair`, a separately selected outer
Advance can return the child's native repair Request. Use its actual affected
findings and captured file texts to perform the assigned implementation work.
Return the declared `RepositorySyncNativeMergeRepairResult` with decision
`repaired`, meaningful rationale and exact ordered complete replacement texts,
or `decision-needed` with rationale and no replacements. The
[repair contract](repository-sync-native-merge-repair.md) owns semantic admission
and bounded text/path policy. A proposal selects no command, repository root,
permission or reviewer evidence.

The finite action `submit-repository-repair` requires exactly `request` and
`submission` beyond the common options. Its request is the unchanged normal
forwarded `GoalPhaseAdvanceResult` from the existing owning task. It consumes
`kind=Request`, an outer `sessionId` matching that task's retained route, a
nonblank different child `submitSessionId`, `submitOperation=submitNativeMergeRepair`
and `resultType=RepositorySyncNativeMergeRepairResult`. Consumed camel/snake
aliases must agree when both appear; inert annotations are ignored. Missing or
crossed route facts fail before launch. Original outer-only actions retain
their own scope.

```sh
/absolute/path/to/managed-uv run --script /absolute/path/to/sm-workflow/scripts/native-goal-command.py \
  --primary /absolute/path/to/sm-workflow \
  --host /absolute/path/to/host \
  --task /absolute/path/to/host/target/sm-workflow/tasks/owned-task \
  --action submit-repository-repair \
  --output /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-submit-child-repair \
  --request /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-advance-repair/response.json \
  --submission /absolute/path/to/actual-repair-submission.json
```

The supplied submission contains object `result` and genuine object
`resultReference`, plus only actually supplied optional array `completionFacts`
and object `executionEvidence`. The caller forwards these business values
opaquely through the existing launcher/classpath to normal command FQN
`org.simplemodeling.textus.SmWorkflow.RepositorySyncService.submitNativeMergeRepair`
with the actual child session and JSON output. It performs one subprocess and
requires zero exit, strict bounded object output, `accepted=true`,
`outcome=accepted`, a nonblank message and that child's matching response ID.
The outer Goal route and raw response/stderr ownership remain unchanged.

Submit records only the proposal, without eager Advance, file/Git effects,
checks or new review. Later explicit Advance owns no-overwrite materialization,
fixed staging, new candidate-specific checks and a full independently admitted
review. Decision-needed stays nonterminal Wait requiring actual separate Human
handling. Fresh acceptance still requires separately granted current convergence
effects. There is no caller retry, route replacement, import or authority grant.
Source and specification fixtures do not deliver the original live task repair.

## Standalone Split preparation, lifecycle and semantic result

Use the same five common `primary`, `host`, `task`, `action` and `output`
options and existing owned task/output preflight for a separate Split execution.
Follow [Split Start preparation](native-host-start-preparation.md), the
[canonical preparation contract](normal-host-preparation-operations.md) and the
[common Operation loop](common-workflow-operation-loop.md). The host records
the actual attributable selection and retains native principal, consent and
scope admission. Supplied metadata grants no provenance or authority.

`prepare-split-input` requires exactly these nine additional fields:

| Option | Generated `WorkflowSplitStartPreparationInput` field | Forwarding |
| --- | --- | --- |
| `component-instance` | `componentInstance` | Verbatim text |
| `allocation-reference` | `allocationReference` | Explicit existing absolute normalized file URI |
| `allocation` | `allocation` | Explicit existing absolute normalized file URI |
| `source` | `source` | Explicit existing absolute normalized file URI |
| `reference` | `reference` | Explicit existing absolute normalized file URI |
| `participant` | `participant` | Required verbatim text |
| `issuer` | `issuer` | Verbatim text |
| `occurred-at` | `occurredAt` | Verbatim text |
| `collection` | `collection` | Strict three-label dotted `entity:` address |

The current generated input requires `participant`; this finite command follows
that generated contract even where older preparation prose describes it as
optional. The four selected file contents remain opaque to the caller. One
ordinary CncfMain command invokes fixed
`org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareSplitStart`
with one `--input` JSON object and `--json`. Zero exit and a strict bounded object
containing a `datastore` array acknowledge passive preparation only. Preserve
raw `response.json`/`stderr.log` and display successful stdout unchanged. This
action never reads or writes routing, imports or stores rows, claims provenance,
Starts or progresses work. Native acquisition and semantics retain their owners;
later explicitly selected trusted provisioning remains a separate host action.

`start-split` requires `input` and allows only optional `proposal` beyond the
common options. Forward the entire existing strict JSON object text as `--input`
and a supplied proposal as `--sessionId` to fixed
`org.simplemodeling.textus.SmWorkflow.SplitPhaseService.startSplitPhase`.
Require zero exit, strict bounded object, nonblank actual message/canonical ID,
agreeing consumed ID aliases and `outcome=started`. Exclusively retain the
actual returned ID, even if a proposal differs, in the task's exact route:

```json
{"family":"SplitPhase","sessionId":"<actual canonical Split Start ID>"}
```

Existing or concurrently occupied routing is never overwritten. `advance-split`
has no action fields and rereads only that retained Split route before calling
fixed `SplitPhaseService.advanceSplitPhase` with its `--sessionId`. Require the
same actual response ID, nonblank message and one of `Request`, `Human`, `Wait`
or `Terminal`. The fixed outer observation timeout is 240 seconds for Goal,
Split or Repository Advance, 60 seconds otherwise. Display the original response and
return control after that single call. Human handling, Submit and any later
Advance are independently selected actions.

The route reader accepts only the exact `{family,sessionId}` shape, regular UTF-8
JSON through the existing symlink-free file open, with family `GoalPhase`,
`SplitPhase` or `RepositorySync` and a nonblank ID. Original Goal actions still
require `GoalPhase`; Repository actions admit only the two owner shapes described
below. Only explicit Split actions require `SplitPhase`; passive
prepare/record execution reads the closed retained family internally. There is
no public family, service, operation, target or timeout selector.

Each Split Submit action requires exactly `request` and `submission` beyond
the common options, both existing absolute normalized files. The actual selected
Request must have `kind=Request`, `sessionId=<saved Split ID>` and
`submitSessionId=<same saved Split ID>`, with this action-selected exact pair:

| Action | Fixed `SplitPhaseService` operation | Required `resultType` |
| --- | --- | --- |
| `submit-split-partition` | `submitPartitionSemanticResult` | `SplitPhasePartitionSemanticResult` |
| `submit-split-scope` | `submitHandoffScopeResult` | `SplitPhaseScopeSemanticResult` |
| `submit-split-reasoning` | `submitReasoningResult` | `SplitPhaseReasoningSemanticResult` |

All four consumed route aliases listed above must agree. Missing/crossed family,
session, child, kind, operation/type or contradictory aliases refuse before
launch/output. The caller neither inspects business partition/scope/reasoning
content nor infers a route. The supplied submission requires object `result`
and permits only optional array `completionFacts` and object
`executionEvidence`. `resultReference` and every other key are rejected: native
Split owns the assigned result destination. Existing Goal/Repository actions
continue requiring their genuine supplied `resultReference` object.

Forward opaque supplied values in order `--sessionId`, `--result`, then only
supplied `--completionFacts` and `--executionEvidence`, through the fixed fully
qualified Split service operation and `--json`. Require zero exit, strict
bounded object, actual same Split response ID, nonblank message, Boolean
`accepted=true` and `outcome=accepted`. Retain raw streams and unchanged routing.
Each action makes exactly one call, with no automatic Advance, Human answer,
effect, review or retry. Failure, timeout or uncertain acknowledgement stops
with available streams preserved and no promoted completion.

`prepare-execution` and `record-execution` also accept exactly these three pairs
under retained `SplitPhase`, requiring the same saved Request/Submit ID and
nonempty whole issued context. They retain all old Goal-owned routes and checks.
Follow [native host execution](native-host-execution.md): prepare forwards the
whole issued context and explicit original mapping/origin URIs to fixed
`NativeHostPreparationService.prepareExecution`; record forwards that context,
the explicitly selected original `execution-session.json`, actual observation
and optional reference URIs to fixed `recordExecution`. Retain the actual opaque
Session/selection strings and structured evidence with agreeing consumed aliases.
Later mapping changes do not permit reconstructing or replacing the original
Session. No provider work, inferred outcome, Submit or Advance follows implicitly.

The actual task-installed `sm-split-phase` procedure owns the scoped semantic
work. Native Human continuation, document effects, independent review and
Preview/Apply keep their separate owners and admissions. Passive preparation,
operational completion or accepted semantic Submit grants none of them and
establishes no installed provider, genuine two-chat work or Phase acceptance.

## Standalone Repository lifecycle and semantic result

The same five common `primary`, `host`, `task`, `action` and `output` options
and owned task/output preflight apply to an independently selected Repository
execution. Existing `prepare-repository-input` remains passive and independent
of routing. Its original explicit URI/metadata, native acquisition, trusted
provisioning and genuine selection obligations above remain in force.

`start-repository` requires only `input` beyond the common options, with optional
`proposal`. It sends the whole selected strict JSON object text as `--input` and
only a supplied proposal as `--sessionId` to fixed
`org.simplemodeling.textus.SmWorkflow.RepositorySyncService.startRepositorySync`
through the ordinary configured CncfMain command prefix and `--json`. Zero exit,
strict bounded JSON object, actual `outcome=started`, nonblank message and
nonblank returned ID with agreeing consumed aliases are required. Exclusively
save the actual returned ID even when the proposal was omitted or differs:

```json
{"family":"RepositorySync","sessionId":"<actual canonical Repository Start ID>"}
```

Occupied routing stops before launch; a route occupied during Start is never
overwritten. The route reader accepts exactly `{family,sessionId}` with the
closed families `GoalPhase`, `SplitPhase`, `RepositorySync`, canonical camel
route keys, a nonblank ID and the existing regular UTF-8/O_NOFOLLOW checks.
Original Goal/Split lifecycle actions keep their exact family checks. There is
no public family, service, operation, target or timeout selector.

`advance-repository` has no action fields. It rereads the selected saved
Repository route and calls fixed `RepositorySyncService.advanceRepositorySync`
with that `--sessionId`, using the same full component prefix. Require matching
actual response ID, nonblank message and kind `Request`, `Human`, `Wait` or
`Terminal`. Advance does not interpret an opaque outcome as Submit acceptance.
Its fixed outer bound is 240 seconds; Start and other actions use 60 seconds.
One call displays the unchanged response and returns control to the host. Native
principal, scope, consent and checks remain with that current call's owner.

The three existing Repository semantic actions require exactly `request` and
`submission` beyond common options. Each admits only its action-selected pair:

| Action | Fixed `RepositorySyncService` operation | Required `resultType` |
| --- | --- | --- |
| `submit-repository-review` | `submitNativeMergeReview` | `RepositorySyncNativeMergeReviewResult` |
| `submit-repository-repair` | `submitNativeMergeRepair` | `RepositorySyncNativeMergeRepairResult` |
| `submit-repository-conflict` | `submitRepositorySyncWorkResult` | `RepositorySyncConflictResult` |

For retained `RepositorySync`, the actual `Request.sessionId` and
`submitSessionId` must both equal the saved Repository ID. For retained
`GoalPhase`, Request.sessionId still equals the saved Goal ID and the nonblank
issued submitSessionId must be distinct from it. `SplitPhase` refuses these
actions. The kind must be Request and all consumed camel/snake aliases must
agree. Missing or crossed family, session, child, kind, operation or result type
refuses before launch/output. Business bodies and hostContext remain opaque.
The Goal-child sections above continue to describe that owner shape.

Supply required object `result` and object `resultReference`; only supplied array
`completionFacts` and object `executionEvidence` may be added. Forward their
unchanged values once to the fixed Repository FQN with the actual issued target
ID. Require zero exit, strict bounded object, same target acknowledgement ID
with agreeing aliases, nonblank message, Boolean `accepted=true` and
`outcome=accepted`. Preserve raw streams and saved routing. Submit retains the
supplied proposal; it grants no effects, authority, retry or automatic Advance.

`prepare-repository-review-input` admits the same exact review pair and owner
shapes. Its seven required options remain unchanged. The saved route alone
selects internal family `RepositorySync` for a standalone Request or `GoalPhase`
for a forwarded child. The fixed kind is `review`; the fixed normal operation
is `NativeHostPreparationService.prepareEvidence`. Forward the complete explicit
existing request/result/reference/executionEvidence file URIs and supplied
issuer/occurredAt/strict collection. Never parse the semantic files or decode
hostContext in the caller. Zero exit and a strict bounded object with a datastore
array acknowledge passive preparation only. Preserve raw streams, inputs,
configuration and route; no provisioning, review, consent, Submit or Advance
follows. Original `prepare-review-input` remains Goal-only with its default
GoalPhase family.

Shared `prepare-execution` and `record-execution` accept saved `RepositorySync`
only for the three exact pairs above, Request kind and both request/submit IDs
equal to the saved ID. Existing Goal own work, all three distinct Goal children
and three same-ID Split pairs retain their guards. Forward the whole nonempty
issuedWorkContext with agreeing consumed aliases unchanged. Preparation selects
explicit original mapping/origin file URIs; recording selects the explicit
original retained execution-session, actual observation and optional reference.
Only fixed `NativeHostPreparationService.prepareExecution`/`recordExecution`
run. Retain actual opaque Session/selection strings and structured evidence with
the existing alias checks. Later configuration cannot reconstruct a pending
Session. No provider execution, inferred profile, automatic Submit or Advance
is supplied by these passive calls.

All actions keep exclusive raw response/stderr outputs, normalized explicit
paths, symlink-free owned directories and one normal subprocess. Nonzero exit,
timeout, malformed/nonobject/duplicate/nonfinite/bounded output or uncertain
acknowledgement stops with available streams retained and no retry/progression.
The current native UoW still owns Git/SBT/file/HTTP operations, direct normal
binaries and native SBT locks. All nine retired main entry points remain retired;
no application Process fallback or global product serialization is introduced.
Genuine Human handling, consent, independent review and effects retain their
separate owners. Command remains primary and client qualification stays light.

## Acceptance and evidence owners

Native Workflow owns semantic admission, mechanical checks/commands, independent
review admission, Human decisions, effects, commit receipts, recovery and closure.
A result, reference, profile, prepared row or accepted Submit grants none of that
authority and establishes no check, review, Human, terminal task or Phase closure.
The host owns Start and explicit Advance; the installed skill's semantic procedure
and normal business parameters remain unchanged. Real tasks use command. Normal
FQN client Start is only lightweight connection qualification, with actual ID
retention, under the [business client contract](workflow-business-client.md).

Apply the adopted [security review profile](../phase/phase-6-security-review-profile.md)
within the existing Effective Acceptance Boundary. Select the smallest missing
check only when a concrete security contract is adopted, this change affects its
conditions, and applicable evidence does not cover the affected requirement.
Reuse current caller behavioral evidence for unchanged canonical routing,
issued-only submission and one-action data/authority separation, plus applicable
native guarantees under their declared conditions. Installed Request → loaded
skill → actual semantic work → declared Submit acknowledgement requires its own
genuine integration evidence; preparation, mock results or a planning read alone
do not supply it.

An editorial guide needs no SQL/XSS/CSRF suite. CNCF107, Cozy83 and CBD Support16
remain PLANNED; plans prove neither availability nor security PASS. A specifically
required missing capability leaves only that acceptance open while independent
work continues. Existing Phase6.1 Step evidence/review, Phase6.2 FIX and
Phase6.3 cross-Step/Final evidence owners remain; PHASE-6.3 owns aggregate full-suite
validation. No additional security engine, provider query, scanner or review
cycle follows from this guide. All-three installed tasks, modes/providers,
independence, minimum Effective Acceptance Boundary and original Step/Phase
acceptance remain separate obligations.

## Return an issued Repository conflict proposal

The actual forwarded `Request` must declare `sessionId=<retained Goal ID>`,
`submitOperation=submitRepositorySyncWorkResult`,
`resultType=RepositorySyncConflictResult` and a nonblank
`submitSessionId=<actual issued child ID>` distinct from the Goal ID. The four
consumed route aliases above must agree. Missing, wrong or crossed owner, kind,
operation, type or child stops before subprocess/output. The caller does not
parse the conflict body or select another route; original outer-only `submit`
still refuses this child.

Select `--action submit-repository-conflict` with exactly `--request` and
`--submission` alongside the five common options. Both select existing absolute
normalized files. Supply the actual conflict result's decision (`resolved` or
`decision-needed`), resolutions and rationale in object `result`, and its actual
artifact reference in object `resultReference`. Only genuinely supplied array
`completionFacts` and object `executionEvidence` may be added. These business
values remain opaque to the caller and subject to normal native admission.

The action calls fixed
`org.simplemodeling.textus.SmWorkflow.RepositorySyncService.submitRepositorySyncWorkResult`
exactly once with the issued child ID and unchanged supplied values. Acceptance
requires zero exit, strict bounded JSON, the actual child response ID, nonblank
message, Boolean `accepted=true` and `outcome=accepted`. Raw streams remain in
the selected fresh output, and the outer Goal routing remains unchanged.
Failure, timeout or uncertain acknowledgement stops without retry or Advance.

For a mapped conflict, `prepare-execution` and `record-execution` accept this
same finite child pair under the retained Goal owner, alongside existing Goal,
review and repair routes. They forward the whole nonempty issued context and
explicit selected artifact URIs. Retain the first actual execution Session;
record selects that original Session through `--execution-session`, including
after later mapping changes. Missing original artifacts authorize no inferred
profile, observation, provider, re-preparation or reconstructed Session.

Conflict Submit retains only a proposal. A separately selected explicit Advance
owns materialization and native checks for a resolved result, or the genuine
separate Human channel for decision-needed. Any newly issued independent merge
review keeps its own scope, executor and evidence obligation. Caller preparation,
recording and Submit grant none of those effects or Human/review observations.

## Repository caller rule and executable examples

R7: `prepare-repository-input` is finite passive forwarding to the fixed existing
Repository preparation Main with five explicit selected pairs and literal origin.
It reads no caller route, parses no business JSON, imports no row and automatically
performs no Start, Advance, Submit or retry. Preparation success supplies only a
prerequisite; it establishes no genuine provenance or installed task acceptance.

The existing caller input/output ownership rules retain exact argument pairs,
explicit normalized input paths, symlink-free owned directories, exclusive raw
outputs, strict bounded acknowledgements and one native call. E15–E18 in
`scripts/tests/test_native_goal_command.py` bind adjacent Given/When/Then and
unittest observations to those rules. Only `subprocess.run` is mocked; fixtures
claim no actual native provenance or installed acceptance. This mapping records
obligations, not executed results.

| Example | Rules | Observable behavior |
| --- | --- | --- |
| E15 | R2,R3,R5,R7 | Exact one fixed passive Main argv with canonical flags and raw stdout/stderr; absent or existing route, selected input and host configuration remain unchanged |
| E16 | R2,R3,R7 | Missing, duplicate, unknown, blank, unpaired or action-inapplicable options refuse before subprocess/output |
| E17 | R2,R3,R7 | Malformed entity collection and relative, unnormalized or missing input refuse before subprocess/output |
| E18 | R2,R3,R5,R7 | Nonzero, timeout or invalid strict/datastore acknowledgement retains available streams and stops without retry, import or route/configuration effects |

R8: `prepare-repository-review-input` and `submit-repository-review` cover only
the finite issued Repository merge-review child under exact owning Goal routing.
Preparation forwards the whole Goal response to fixed GoalPhase review decoding;
Submit calls fixed RepositorySyncService with the issued child ID and requires
that child's acknowledgement. Native semantic/context/evidence/effect admission
retains its existing owners; original outer-only actions remain outer-only.

E19–E23 in the same executable specification retain the fixture provenance
disclaimer and mock only `subprocess.run`. Every behavior leaf/subtest has
adjacent Given/When/Then; this mapping describes obligations, not executed
results or genuine child delivery.

| Example | Rules | Observable behavior |
| --- | --- | --- |
| E19 | R2,R3,R5,R8 | Snake/camel/matching-both forwarded Goal routes prepare once with fixed GoalPhase review Main; raw files, streams, config and outer route are unchanged |
| E20 | R2,R3,R5,R8 | Supplied repair result/reference and absent/present optional metadata dispatch once to exact Repository FQN/child ID; raw output and owning route remain without Advance |
| E21 | R2,R3,R4,R8 | Wrong/missing/crossed route/type/kind/child or contradictory consumed aliases refuse before output/process; original actions still reject a legitimate child |
| E22 | R2,R3,R8 | Missing/duplicate/unknown/blank/unpaired/inapplicable pairs, invalid explicit files/collections or submission metadata refuse before output/process |
| E23 | R2,R3,R5,R8 | Nonzero/timeout/strict or bounded response failure and wrong child acknowledgement retain raw streams/config/route and stop without retry |

R9: `submit-repository-repair` consumes only the current forwarded repair child
route under the actual owning Goal session, submits opaque supplied business
values once to its fixed Repository FQN, requires that child's acknowledgement
and preserves outer routing. Semantic/native/file/check/review/effect admission
remain at their existing owners.

P1 in `scripts/tests/test_native_repository_repair_command.py` has separate
adjacent Given/When/Then leaves for proper child submission, aliases/inert
annotations, wrong/missing/crossed route facts, finite action pairs, child
acknowledgement and native failure/timeout without extra calls. It mocks only
`subprocess.run`; these authored obligations record no validation PASS or native
task delivery.

R10: `submit-repository-conflict` consumes exactly the forwarded conflict route
above, forwards supplied semantic values once to the fixed Repository FQN and
requires the actual child's acknowledgement without replacing the outer route.
Prepare/record accept exactly the additional conflict operation/type pair and
retain the original opaque execution Session. Native proposal, Human, effects,
independent review and explicit Advance retain their separate owners.

E25/E26 add Goal/review/repair/conflict routes with declared, snake and agreeing
dual consumed aliases to the existing whole-context and opaque-retention
assertions. E30–E33 in `scripts/tests/test_native_goal_command.py` bind adjacent
Given/When/Then to the following obligations. Only `subprocess.run` is mocked;
these examples establish neither installed provider/task delivery nor genuine
two-chat work, and this mapping asserts no successful execution.

| Example | Rules | Observable behavior |
| --- | --- | --- |
| E30 | R2,R3,R5,R10 | Supplied resolved/decision-needed result/reference and absent/present optional facts/evidence dispatch once to the exact conflict child FQN; streams, files and outer route remain unchanged |
| E31 | R2,R3,R4,R10 | Missing/wrong/crossed owner, kind, operation, type, child and contradictory consumed aliases refuse Submit/prepare/record before subprocess/output; original Submit still rejects the child |
| E32 | R2,R3,R5,R10 | Nonzero, timeout, strict malformed/bounded response and unaccepted/wrong-child acknowledgement retain raw streams and stop without second call, promoted completion, Advance or routing changes |
| E33 | R2,R3,R10 | Exact options, strict submission metadata and explicit existing files are required before launch/output |

R11: Standalone Split preparation requires the nine generated fields above;
Start retains only the actual canonical Split ID, Advance observes once, and
only the three exact issued semantic routes can Submit or prepare/record mapped
execution under that retained family. Split metadata has no caller-assigned
resultReference. Native semantic, Human, effects, independent review and
Preview/Apply retain their separate owners. R1–R10 remain in force.

E34–E40 in `scripts/tests/test_native_goal_command.py` add adjacent
Given/When/Then executable expectations, mocking only `subprocess.run` and
preserving the earlier examples. These observations require parent validation;
they claim no actual installed task, provider, consent, two-chat or Phase
acceptance.

| Example | Rules | Observable behavior |
| --- | --- | --- |
| E34 | R1,R2,R3,R5,R11 | Exact nine-field passive Split mapping and fixed one-call datastore acknowledgement; opaque selected files/config and absent or existing routing stay unchanged |
| E35 | R2,R3,R5,R11 | Canonical returned Start ID, optional proposal, exclusive no-overwrite routing and four one-call Split Advance observations with fixed 240-second bound |
| E36 | R2,R3,R5,R11 | Three exact semantic action/pair routes and agreeing aliases forward opaque result and absent/present optional facts/evidence once, without resultReference, requiring same Split ID |
| E37 | R2,R3,R5,R11 | All three Split execution routes and aliases forward whole context and original explicit Session, retaining actual opaque artifacts despite changed configuration |
| E38 | R2,R3,R4,R11 | Missing/wrong/crossed family/session/type/operation/child/kind/context or contradictory aliases refuse before process/output; old Goal actions retain their boundary |
| E39 | R2,R3,R5,R11 | Every new action stops on failure, timeout, strict/bounded output or invalid acknowledgement, retaining streams/route without retry or promoted completion |
| E40 | R2,R3,R11 | Exact options, generated required participant, strict collection, explicit files and Split metadata types/unknown resultReference refuse before process/output |

R12: Standalone Repository adds only two fixed lifecycle actions and admits the
three existing semantic pairs under the saved same-ID Repository owner. The
distinct Goal-child rule, Split boundaries, passive review family selection,
whole issued context and explicit original execution Session remain as described
above. R1–R11 retain their scopes and obligations.

E41–E47 in `scripts/tests/test_native_goal_command.py` add exactly seven methods
with adjacent Given/When/Then, mocking only caller `subprocess.run`. The original
58 method names and assertions remain. These authored expectations establish
finite caller behavior only when validated; they assert no successful installed
provider/task execution, genuine two-chat work or Step/Phase acceptance.

| Example | Rules | Observable behavior |
| --- | --- | --- |
| E41 | R2,R3,R5,R12 | Canonical Repository Start with omitted/same/different proposal and declared/snake/agreeing dual ID aliases; occupied/raced routes survive; four explicit kinds use fixed 240-second Advance and reread actual ID |
| E42 | R2,R3,R5,R12 | Three exact semantic pairs and consumed aliases forward supplied opaque result/reference and absent/present optional facts/evidence once, requiring canonical same-ID acknowledgement |
| E43 | R2,R3,R5,R12 | Standalone/Goal-child review preparation selects fixed family from saved route and forwards whole explicit URIs/metadata, preserving streams, inputs, configuration and routing |
| E44 | R2,R3,R5,R12 | Three execution pairs and aliases preserve whole context, explicit original Session after mapping changes and opaque returned artifacts |
| E45 | R2,R3,R4,R12 | Wrong/crossed family, request/submit IDs, child, operation/type/kind/context and contradictory consumed aliases refuse before launch/output; original family boundaries remain |
| E46 | R2,R3,R5,R12 | Start/Advance/three Submits/review preparation/prepare/record stop on nonzero, timeout, strict/bounded output or acknowledgement uncertainty, preserving raw streams without retry/progression |
| E47 | R2,R3,R12 | Exact options, strict collections, explicit paths, required resultReference and metadata types/unknown fields refuse before launch/output |

All passive preparation actions use the [canonical normal command/input/output contract](normal-host-preparation-operations.md). The finite Python options, selected-task routing, no-overwrite, output retention, timeout cleanup, one native invocation, unknown-result stop/no retry and separate Submit boundaries are unchanged.
