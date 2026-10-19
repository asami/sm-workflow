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
Python runner; every invocation performs exactly one finite host action. The
normal `complete` action joins one Submit observation to one explicit outer
Advance. Other actions retain their single-call behavior for diagnostics and
explicit operations. The owning host prepares each empty output directory;
`complete` exclusively prepares its `advance` child before making Submit.

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

Use normal `NativeHostPreparationService.prepareAuthority` with generated
`WorkflowAuthorityPreparationInput` for independently issued Step or PhaseClosure
authority. Its single `--input` DTO supplies `request` and `reference` file URIs,
actual participant, trusted issuer, historical `occurredAt` and collection. The
request is the full actual closing `Wait` with its supported `authorityContext`.
Step and Phase purposes/scopes remain distinct; an ordinary Wait or a review
Request supplies no closing authority. Encoding the scope does not issue
authorization. Native readers retain their current issuer, owner, scope, time
and managed read admission.

Use normal `prepareRepositoryEffect` with generated
`WorkflowRepositoryEffectPreparationInput` for independently issued
`CommitPending`, `Push` or `FinalFetch` authority. Its DTO contains selected
`selection`, `target` and `reference` file URIs, the exact selected effect and
explicit participant/issuer/time/collection. Trusted physical Git target and
endpoint remain host-owned. It emits passive importer input without Git effects.
Neither preparation replaces genuine source evidence or independently admitted
authority; StartupImport alone provisions the separately selected rows.

On error or uncertain effects, stop the current caller branch and retain its
observed history and available streams. Later work requires a NEW independently
admitted execution with its own task and actual returned ID. Do not replay the
failed call or infer rollback or interrupted recovery. The existing common
Operation loop, command caller and adopted security profile retain their owners;
preparation does not supply native checks, independent review or closure.

## Explicitly prepare Repository effect input

Use finite action `prepare-repository-effect` for the existing generated
`WorkflowRepositoryEffectPreparationInput` contract. The same five common
`primary`, `host`, `task`, `action` and `output` options and owned task/output
preflight apply. Alongside them, all nine fields below are required named pairs;
none has a default or optional form.

| Option | Generated field | Forwarding |
| --- | --- | --- |
| `component-instance` | `componentInstance` | Verbatim selected component instance |
| `selection` | `selection` | Explicit existing absolute normalized regular file as a `file:` URI |
| `target` | `target` | Explicit existing absolute normalized regular file as a `file:` URI |
| `effect` | `effect` | Exactly `CommitPending`, `Push` or `FinalFetch` |
| `reference` | `reference` | Explicit existing absolute normalized regular file as a `file:` URI |
| `participant` | `participant` | Verbatim actual selected participant |
| `issuer` | `issuer` | Verbatim supplied issuer |
| `occurred-at` | `occurredAt` | Verbatim supplied historical time |
| `collection` | `collection` | Strict three-label dotted `entity:` address |

```sh
/absolute/path/to/managed-uv run --script /absolute/path/to/sm-workflow/scripts/native-goal-command.py \
  --primary /absolute/path/to/sm-workflow \
  --host /absolute/path/to/host \
  --task /absolute/path/to/host/target/sm-workflow/tasks/owned-task \
  --action prepare-repository-effect \
  --output /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-prepare-effect \
  --component-instance '<selected component instance>' \
  --selection /absolute/path/to/selected-repository-selection.json \
  --target /absolute/path/to/trusted-repository-target.json \
  --effect CommitPending \
  --reference /absolute/path/to/actual-effect-source-reference.json \
  --participant '<actual participant>' --issuer '<actual issuer>' \
  --occurred-at '<actual historical timestamp>' \
  --collection entity:smworkflow.cli.effect_authority
```

Replace every placeholder with independently selected actual values. Select
exactly one of the three supported effect names. Each collection label must
match `[A-Za-z][A-Za-z0-9_]*`; slashes, missing or additional labels refuse.
Missing, duplicate, blank, unpaired, unknown and action-inapplicable options
refuse before native invocation. Unavailable or non-regular selected files and
relative or unnormalized paths also refuse before invocation. No selection is
reconstructed from a repository, task route or business input.

The caller leaves all three source files opaque, converts their explicit paths
with `Path.as_uri()` and forwards their URIs and literal metadata in one
`--input` JSON object. Its sole ordinary CncfMain command invokes fixed
`org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryEffect`
with `--json`. Native URI acquisition, domain decoding and passive importer
record encoding remain with that existing Operation. There is no arbitrary
operation or argv selector, alternate main or fallback.

Zero exit and an actual strict bounded JSON object with a `datastore` array
acknowledge passive output only. Successful stdout is displayed unchanged;
actual stdout/stderr are retained as `response.json` and `stderr.log`. Native
failure, timeout, malformed output or a non-datastore response stops with the
available original streams and no retry. This action reads or creates no
workflow route, returns no caller Session ID and performs no Start, Submit,
Advance, import, save or Git effect. Existing routing may be present or absent.

Encoding creates no authority. Only a separately selected trusted import through
ordinary StartupImport provisions the passive rows; import also establishes no
effect admission. The native workflow still admits independently issued
authority against its principal, selected target, issuer, scope and time before
any effect execution. The host owns the trusted physical Git target and endpoint
and every later runtime action. Supplied participant, issuer, time, effect or
reference metadata certifies no genuine Human provenance or permission. Retain
the attributable actual source evidence; never fabricate a Human decision from
preparation success, fixture data or a metadata label. The installed semantic
skill continues to own assessment only.

## Pre-Start checklist

Before Goal Start, require the following, using the preparation contracts above:

1. Record the owning host's genuine Goal business selection and complete ordered
   Plan, attributing parent-selected details to the parent. If the installed
   closure invokes RepositorySync, independently record its attributable
   Repository business selection. Supplied `ExplicitHuman` certifies no consent.
2. Match the actual selected participant to the normal host local subject. For
   the Repository child, require all six fields (`repositoryIdentity`,
   `localBranch`, `trackingRemote`, `trackingBranch`, `remoteEndpointIdentity`,
   `syncMode`) to agree with the trusted logical target and physical host binding;
   native principal and target owners still admit their use.
3. Require successful normal passive `prepareGoalStart` output containing
   `goal_selection` and `goal_plans`. When that closure invokes RepositorySync,
   also require successful normal passive `prepareRepositoryStart` output
   containing `repository_selections`, both before this Goal Start.
4. Select trusted `textus.import.data.file` before normal startup. For combined
   use, its standard `datastore` envelope must preserve all three original
   producer collection entries and row payloads at their selected distinct
   collection addresses, with matching storage destinations and managed read
   grants. Preparation does not provision; ordinary StartupImport alone saves
   or creates these mutable input rows. Use the detailed composition above.
5. Keep independent review, native child eligibility and genuine Step,
   PhaseClosure and RepositoryEffect authority with their separate owners.
   Preparation, import and Start establish none of these permissions; separate
   PhaseClosure authority and Repository selection are required for child entry.
6. Use a fresh owned task with no occupied route. Retain Start's actual returned
   canonical ID in that exact task file and reread it for independent calls.
   Failure or uncertain effects stop the caller branch with its history retained;
   infer neither replay permission nor rollback. Later work requires a new
   independently admitted execution as described above.

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

## Run configured semantic work

The current primary contracts `docs/spec/goal-phase-native-execution.md` and
`docs/spec/native-host-execution.md` define this handoff. The Plan
supplies semantic classification, ReasoningClass and review responsibility.
The host's canonical `workflow.execution.profiles` mapping and companion
`sm-workflow.execution.origin` supply concrete selection and the actual origin
descriptor. Both absent retain ordinary composition; a partial or invalid pair
stops. Configuration names and successful preparation prove no provider work,
availability, independence or permission.

1. Use the actual returned Start ID in the exact owned task's routing file.
   Check the whole issued Request: owning Goal, Step/Plan, source revision,
   mode, required facts, result type, completion route and nonempty
   `issuedWorkContext` (or agreeing `issued_work_context`). Preserve that entire
   context, including handle, run, continuation, expected revision and execution
   requirement; a task/chat ID or profile label cannot replace it. Load the
   actual task-installed `sm-goal-phase` and consume the saved effective
   acceptance boundary for this assigned kind.
2. At first issue, the host selects explicit original mapping/origin snapshots.
   The existing caller's `prepare-execution` action takes `--request`,
   `--mapping` and `--origin` alongside common primary/host/task/action/output
   options and calls normal `NativeHostPreparationService.prepareExecution`.
   Prepare once and retain the returned opaque `execution-session.json` and
   `execution-selection.json` unchanged. Pending/reopened work uses that original
   Session and selection, including after configuration replacement. A later
   mapping cannot reconstruct a missing pending Session or authorize another
   preparation. These passive calls execute no provider.
3. Through existing host tools, perform only the admitted selected real executor's
   scoped semantic work. Ordinary INLINE work needs no mandatory child. Record
   actual executor context/profile, outcome and honest detail; include only
   observed resources, leaving unavailable metrics unknown. Neither configuration
   nor a workflow/process/chat identity establishes an executor observation.
   Only the first DELEGATED `UNAVAILABLE` or `DECLINED` may permit the single
   configured alternate under native rules. `BLOCKED`, `FAILED`, uncertainty,
   exhaustion or INLINE unavailability returns control without success, retry,
   fabricated `completed`/`decision-needed` or automatic Advance. A genuine
   semantic `decision-needed` result retains its separate independent Human route.
4. The host's `record-execution` takes `--request`, `--execution-session` selecting
   the original retained artifact, `--observation` and, only for actual
   `COMPLETED`, `--reference` selecting the generated artifact reference. It calls
   normal `NativeHostPreparationService.recordExecution`; retain its opaque
   Session and actual structured `completionEvidence`/`execution-evidence.json`.
   Noncompleted outcomes supply no reference or completion evidence. Preserve
   raw outputs and stop on failure or uncertainty without reconstructing success.
5. Return the actual typed semantic result with honest observations for every
   applicable required saved acceptance condition, as defined by current primary
   `docs/spec/goal-phase-acceptance-boundary.md`. `all` applies here; `step` also
   applies to ImplementStep/RepairStep/ReviewStep, while `phase` applies only to
   ReviewPhase. Excluded conditions stay advisory. Only actual final operational
   completion supplies the returned execution evidence with its genuine
   result/reference to the host's `complete` action below. That finite action
   checks the actual Submit target ID, message, Boolean accepted and outcome,
   displays and flushes the observation, then explicitly Advances the retained
   outer session once on acceptance. Accepted Submit establishes semantic
   admission only.

Command remains primary and client qualification remains lightweight. Independent
Step/Phase review, Human consent and native repository closure authority keep
their existing owners. Thin typed `git_*`/`sbt_*` intent retains checks, direct
normal binary execution and typed results inside CNCF's owning UoW, with native
SBT locks and no global product wrapper or `scripted` prerequisite. PHASE-6.3
retains aggregate full-suite responsibility. This handoff supplies no check,
review, effect, terminal task or outer Phase acceptance claim.

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
  --action complete \
  --output /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-complete \
  --request /absolute/path/to/host/target/sm-workflow/tasks/owned-task/call-advance/response.json \
  --submission /absolute/path/to/semantic-submission.json
```

The host's `complete` reads the exact caller route and actual Request, then calls the issued
`GoalPhaseService.submitGoalPhaseWorkResult` with named `--sessionId`,
`--result`, `--resultReference` and only supplied `--completionFacts`/
`--executionEvidence`. It forwards semantic values unchanged for native admission.
Require zero native exit, strict JSON object, matching response ID, nonblank
message, a Boolean `accepted` and nonblank `outcome`. Display and flush that
actual Submit JSON as one line. Only `accepted=true` with `outcome=accepted`
permits exactly one ordinary explicit outer Advance in this same host action,
without a user continuation prompt or continuation-only AI turn. Validate that
Advance's actual outer ID, message and kind, then display its JSON as the second
line and return control. Request returns semantic work to the host; Human uses
the independent actual human channel; Wait stops for its actual condition;
Terminal ends. None causes another call inside `complete`.

`complete` requires exactly `--request` and `--submission` alongside common
options for all three families. It selects only the original Goal pair above,
the three fixed Split pairs below, or the three fixed Repository review/repair/
conflict pairs below. All existing issued-route and submission metadata checks
apply, including agreeing consumed aliases. Split accepts only its own pairs;
Repository accepts only its own pairs; Goal accepts its own pair or an issued
Repository child pair. A Goal-owned child uses the actual distinct child Submit
ID while its following Goal Advance uses the retained outer ID. Other issued
operations are unsupported by this action; presentation never selects a route.

The raw Submit `response.json` and `stderr.log` remain in the selected empty
output directory. Before Submit, `complete` exclusively creates and opens its
`advance` child directory; the following call retains its raw files there.
Output and routing are never overwritten. Valid rejection is displayed and
returns nonzero without Advance. Invalid, mismatched or uncertain Submit also
returns nonzero without Advance. If Advance fails or is uncertain, the already
flushed Submit remains visible and all available raw output is retained; return
nonzero without retry, resubmission, restart, discovery or pending-call replay.
Successful output is newline-delimited JSON containing exactly two actual
observations, not an inferred completion summary.

The original `submit`, dedicated `submit-split-*`/`submit-repository-*` and
`advance*` actions described below remain available as explicit single-call
operations and diagnostics, with their original response/output behavior.
Their examples describe those individual actions. Normal supported semantic
handoffs use the host's `complete` action; a semantic skill returns its typed
result and control and gains no Advance or caller-loop authority.

The caller allows a finite outer observation wait of 240 seconds for each
explicit Advance (including the Advance inside `complete`) and 60 seconds for
Submit and every other native call. These bounds are fixed
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
## Owning-UoW checks and public review order

For supported semantic routes, the owning host selects `complete`, which
displays accepted Submit and performs one explicit outer Advance without an
intervening continuation-only interaction.
The native Workflow inspects/stages only its assigned candidate paths and selects
checks from its saved policy. Thin `git_*` and `sbt_*` DSL calls express logical
intent and closed parameters. Permission, service, binding, workspace, limits,
lifecycle and admission checks, direct normal binary execution and typed result
interpretation all occur inside CNCF's actual current UoW. The application
supplies neither `ProcessExecutionRequest` nor direct `ProcessExec`, executable,
arbitrary argv or a generic process fallback. The Workflow saves intent before
execution and the actual bounded result afterward. An unknown effect remains
uncertain; reopening or a failed check never authorizes replay.

The logical Git work target is `sm-workflow-repository`. The selected fixed
two-suite TestOnly build uses `sm-workflow-repository-validation-1`, bound to the
current primary repository. These are distinct trusted workspaces: candidate
Git staging does not redirect the primary build. The saved Check kind is
`test-only`; CNCF owns the fixed ordered selection:

1. `org.simplemodeling.textus.smworkflow.admission.NativeHostPreparationOperationSpec`
2. `org.simplemodeling.textus.smworkflow.admission.NativeHostRepositoryStartInputSpec`

The host owns the normal executable, workspaces, owner/capability/Manage grants,
fixed classes, environment and finite launch/execution/capture/termination limits.
Its actual `sbtExecutable` remains
`/Users/asami/Library/Application Support/Coursier/bin/sbt`. The fixed SBT PATH
includes the selected JDK and installed Coursier bin directory, including Cozy;
it is not inherited from a caller-selected environment. These fixture suites
also require the host's single unquoted `JAVA_TOOL_OPTIONS` option:

```text
-Dsmworkflow.spec.sbt=/Users/asami/Library/Caches/Coursier/arc/https/github.com/sbt/sbt/releases/download/v2.0.9/sbt-2.0.9.zip/sbt/bin/sbt
```

That existing installed script path has no whitespace and is the fixture's
normal SBT prerequisite. Quoting a property containing spaces in
`JAVA_TOOL_OPTIONS` fails the normal SBT shell runner's token handling. The host
executable remains the Coursier entry above; semantic work cannot substitute it.
CNCF runs normal SBT directly with its managed lifecycle; SBT owns native locks
and legitimate children. Product execution has no global SBT wrapper or
`scripted` prerequisite. Independent development SBT follows current AGENTS
policy separately, through registered `cncf_command_runner` and the shared
serial runner, waiting for `lock=released` before another top-level invocation.
See current primary `docs/spec/sbt-validation-runtime.md` for the normative
runtime and receipt contract, available in this checkout.

For this TestOnly selection, require actual termination `exited`, raw exit zero,
typed `sbt.kind: test-only`, `sbt.outcome: succeeded`, the exact ordered two
`selection.testClasses` above, `completedSuites: 2` and positive `testsRun`.
Raw zero alone, absent legacy facts, `unverified-tests`, incomplete capture,
failed or unresolved results do not pass. Typed selection and outcome are
CNCF-owned facts; callers and semantic authors must not manufacture them by
parsing logs. Historical no-facts Test compatibility does not qualify TestOnly.

When those checks pass and review is installed, the check-running public Advance
issues `Request` / `ReviewStep` in that same public call. Its actual saved checks
are in `work.review.checks` and the host review context. A top-level `validation`
field is not required on this Request. There is no mandatory successful Wait
or extra Advance before this first review issue. With unfinished or failed
validation, or absent review installation, the fallback can remain `Wait` with
its actual condition and validation. Follow that condition without treating
Wait or raw zero as success or automatically polling/retrying.

A later independently selected Advance may explicitly reopen an already pending
review through the retained suspension. It reuses the actual issued context,
candidate and saved checks without another SBT execution. This optional reopening
is not a transition required to issue the first review, an independent review
result or permission to replay uncertain effects. Passed checks and an issued
review grant no accepted review, Human authorization, Step commit, effect or
Phase closure.

`work.review.priorReviews` carries actual latest review reports reconstructed
only from admitted native Step/Phase completions. Each declared Record retains
the original kind, Step (or Phase), generation, candidate, checks receipt,
result identity/revision and complete semantic result, including its rationale
and findings. It contains no copied request, diff, log, provider snapshot or
authority certificate. Phase integration receives all latest actual Step
reviews and the latest Phase review when present. A Step re-review receives
that Step's previous report and the latest Phase report, so corrective Step
work can see the actual integration findings. Review-origin repair also
receives its previous context plus the just-admitted report; check-origin
repair keeps its current check-failure context.

The current accumulated diff, candidate and selected validation remain current.
Prior reports keep their original targets and are never relabeled as new PASS
results. An affected re-review uses the prior findings/report with current
impact to reuse still-applicable judgments, without automatically broadening
the review scope. These reports are reviewer business context, like the diff;
they do not change `NativeHostReviewScope`, independent evidence targets or
the exact assignment, candidate, checks, worker and actual-result admission.

Newly issued work always includes the available actual reports. Already issued
work retains every existing Context field exactly: only an originally empty or
absent `priorReviews` vector may remain empty when history now reconstructs
reports. A nonempty issued vector must match exactly, including findings,
targets and references. Explicit null or malformed vectors/reports refuse;
absent vectors default to empty. Metadata retains its existing validation
meaning and never carries review reports.

The optional original-review completion contract below connects one mechanically
covered repair to ordinary Closing. Prior reports alone still grant no acceptance
or effect authority. Final reuse, actual installed evidence and Phase completion
remain parent-owned obligations.

### Complete the original review's terminal-LF conditions

An independent `ReviewStep` may optionally return `completionConditions` as part
of its ordinary original `repair` verdict. It means every semantic judgment is
settled and the only remaining ordered findings are extra terminal-LF corrections,
exactly one finding per explicit current Step owned path. The declared Record is
`{"kind":"terminal-lf","paths":["src/one.scala","docs/check.md"]}`. The paths
are in finding order, distinct valid relative Git paths, contain no ISO controls,
and each occupy at most 4096 UTF8 bytes; there must be 1..16 paths and exactly as
many findings. No Plan revision or dependency proposal may accompany this option.
The reviewer remains read-only, omits it whenever any semantic question remains,
and is never required to supply it. ImplementStep, RepairStep, ReviewPhase and
other outcomes cannot declare it. Absent historical values remain absent;
explicit null, malformed kinds/paths and contradictory result contracts refuse.
Normal generated Submit converts the actual Record through the strict owner
codec, without argument parsing or alternate schemas. The independent verdict
attestation binds its exact kind, path content and order.

Only actual native Cursor reconstruction can select completion. The latest real
completion must be the current Step's completed RepairStep at the current
generation, with unchanged selected Plan, original base and completed prefix,
the exact original review directive/findings, and the latest actual prior
ReviewStep report in its retained context. That original `repair` report must
be from exactly the preceding generation. Its original identity/revision,
candidate, checks reference and semantic result remain unchanged. Both candidate
heads equal the current native tip, while their trees differ. Fresh native
validation must pass every selected Plan-required check under the existing
validation owner, with complete successful receipts including Compile and Test;
TestOnly alone is insufficient. Every unresolved current Step issue must be a
REVIEW issue from exactly those original findings, with an actual latest FIXED
repair disposition. Other unresolved issues keep affected review required.

Before issuing semantic work, the owner reads typed CNCF `TreeChanges` between
those two actual immutable trees and their actual old/new `BlobContent` values
inside the canonical UoW. The exact changed-path set must equal the original
condition-path set. Each difference must be an `M` change with equal regular
mode `100644`/`100755`, distinct present blob IDs, complete strict UTF8 without
NUL, at least two old terminal LF bytes, nonempty other content and exactly
unchanged nonterminal bytes followed by one LF. No new file, deletion, rename,
mode change, semantic change, CR normalization, changed space, empty delta or
extra path qualifies. A successful uncovered delta uses the ordinary affected
ReviewStep with actual prior reports/current diff/checks. Malformed, failed or
incomplete typed reads stop explicitly; no process fallback or inferred proof
is permitted. The existing Closing EOF-only-check route shares the byte rule
and retains its separate requirement for an actual earlier EOF-only precheck.

Successful proof records one ordinary mechanical closing-admission receipt:
`ordered-review-completion:<sourceRevision>`, with cause
`ordered-review-completion:covered`, through the existing native
`phase.Review` → `phase.Closing` `review-accepted` edge. Its bounded payload
retains source revision, Step/generation, original review identity/revision and
candidate/checks, actual repair result reference, current candidate/checks
references, condition paths and admitted typed path/mode/blob IDs. It contains
no full request/result copies or source bytes. Retained traversal reconstructs
the same source and issues before that record, validates the canonical full
native prefix and exact relationships, and reobserves immutable tree/blob facts
as ordinary stage inputs. Lost or unknown persistence stops without automatically
issuing semantic review or effects.

The controller enters Closing with the current candidate/checks and the ORIGINAL
review reference. It creates no new ReviewStep result, fresh PASS or semantic
WorkKey, changes no generation or repair counter, and grants no commit authority.
Exactly the proven original REVIEW issues become RESOLVED under the real
mechanical completion reference in their existing history/movement; FIXED repair
dispositions and earlier failures remain. The ordinary next operation uses
existing Step closure authority and selected commit behavior. The normal public
message identifies the original report, current checks reference and fulfilled
terminal-LF paths. This is completion of the original independent judgment and
ordinary closing admission, with no additional semantic actor or certificate.

Adjacent Given/When/Then in `GoalPhaseNativeReviewCompletionSpec`,
`GoalPhaseOrderedProgressionSpec` and `NativeHostReviewScopeSpec` specifies this
contract. Pure fixtures prove no actual independence, installed execution or
acceptance. Parent-selected generation/focused validation, actual installed
original review → real RepairStep → fresh Compile/Test → zero extra semantic
review request → ordinary selected commit, affected semantic repair re-review,
cross-Step Phase amendment/review, Final reuse, aggregate validation and the
comprehensive development Phase review remain unfinished acceptance obligations.

These paragraphs prescribe evidence to observe in actual normal calls; they
record no execution or PASS. A new independently selected execution may follow
parent planning after earlier uncertain work, with its own task and actual Start
ID. It is never automatic fallback, replacement routing or amendment of the old
execution's Defer, Plan, history or repair limit.

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

R13: `prepare-repository-effect` adds one finite passive caller to the existing
normal `prepareRepositoryEffect` Operation with all nine explicit generated
fields, exactly three supported effect names and strict dotted collection
validation. It forwards opaque selected files and literal metadata without
routing, selection reconstruction, inferred authority, import or effect
execution. R1–R12 retain their existing scopes.

`scripts/tests/test_native_repository_effect_command.py` specifies PHASE-6.3 /
SYNC with adjacent Given/When/Then clauses at every scenario leaf. Only the
subprocess boundary is mocked. Its temporary files and native-shaped streams
are executable specification fixtures, never actual consent, Human provenance,
imported authority or installed execution. These authored obligations claim no
validation result or Phase acceptance.

| Example | Observable behavior |
| --- | --- |
| SYNC-E1 | Each exact effect forwards all three opaque file URIs and literal metadata once to the sole normal API; absent or invalid existing routing, files and host configuration stay unchanged; only actual passive streams are retained |
| SYNC-E2 | Missing, duplicate, blank, unpaired, unknown or inapplicable pairs, unsupported effects and malformed collections refuse before native invocation/output |
| SYNC-E3 | Each selection/target/reference must be an existing absolute normalized regular file; refusal creates no output, route or reconstructed selection |
| SYNC-E4 | Native failure, timeout, strict malformed or non-datastore responses retain actual streams and stop without retry, unrelated operations or Session/route creation |

All passive preparation actions use the [canonical normal command/input/output contract](normal-host-preparation-operations.md). Their finite Python options, selected-task routing, no-overwrite, output retention, timeout cleanup, one native invocation, unknown-result stop/no retry and separate Submit boundaries are unchanged. Only the host's `complete` action connects a validated accepted Submit to one explicit outer Advance.

## Common host completion executable scenarios

`scripts/tests/test_native_host_progression.py` specifies PHASE-6.3 / HOST /
HOST-A with adjacent Given/When/Then clauses and subprocess-boundary fixtures:
Goal call order and flush before Advance; all three Split pairs; all three
Repository pairs; separate Goal-child/outer IDs; each Request/Human/Wait/Terminal
frontier stopping after two calls; rejection; wrong response ID and contradictory
aliases; transport/nonzero/timeout failure; strict malformed response; accepted
Submit followed by failed or invalid Advance; invalid issued routes and options
refused before any call; and exclusive occupied-output refusal. These behavioral
fixtures supply no real installed execution, provenance, route acceptance or
Phase completion evidence. Existing single-action specifications keep their
original obligations; parent-selected validation and actual installed task
acceptance remain separate.

## Retained native validation nonexecution continuation

The existing native Goal validation owner distinguishes an actual typed CNCF
`SbtExecutionOutcome.NotExecuted` from failed product validation and an unknown
effect. Only actual `ProcessExecutionTermination.NotStarted` projects to
`not-executed` / `not-started`, with no exit code or process capture. Launch
failure, timeout and other uncertain terminations retain their incomplete
meaning; an unresolved saved intent never implies nonexecution. A stopped
attempt supplies neither success nor a semantic repair and changes no repair
count, generation, Plan, original base, selected scope or authority.

The current v2 validation payload retains completed `receipts` and an optional
`stopped` vector. An absent vector reads as empty; explicit null or malformed
facts reject. Completed receipts remain the ordered selected prefix. Stopped
receipts retain their actual identity, kind and nonexecution facts in selected
order, including repeated stopped attempts and attempts preceding a later
success. The public validation checks show the completed prefix and, while its
current check remains stopped, its latest actual stopped receipt. A subsequent
success does not rewrite any earlier result or diagnostic record.

For selected index `i`, attempt zero retains `check-intent-i` and
`check-result-i`. Later attempts use `check-intent-i-retry-n` and
`check-result-i-retry-n`, with positive contiguous `n`. Each immutable intent
copies its actual prior payload; its one contiguous native result adds exactly
one stopped receipt or one completed receipt. Instance, definition, source,
candidate, selected metadata, canonical causation, revision and complete history
prefix must all match. Missing intents, gaps, rewritten prefixes, contradictory
terminal facts or attempts following ordinary completion reject. An unresolved
intent remains nonreplayable. Missing records never establish an execution fact.

Before any new check dispatch, the same owner reconstructs the actual chain,
reusing completed checks. Metadata discovery and selection occur once, only
after their complete successful ordinary prefix, and retain that prefix's
completed and stopped facts. The final validation outcome is admitted only as
the actual next successor of the reconstructed terminal chain with identical
receipt history and the existing exact outcome semantics.

A newly confirmed NotExecuted result ends check dispatch for that outer
Advance, returning Wait with confirmed nonexecution and no product result.
After preparation correction, the next explicit ordinary Advance can save one
fresh intent and dispatch one attempt for that stopped check under the standing
selection and existing typed UoW/policy owner. It never redispatches the old
intent, retries immediately, schedules backoff, creates a Human question or
creates new authority. Later selected checks run only after actual completion
of the current check. Genuine Human, Wait, rejected and Terminal boundaries
retain their existing owners.

`WorkflowSbtValidationSpec` and `GoalPhaseNativeRetentionApplicabilitySpec`
specify typed result projection, exact native append chains, retained success,
repeated nonexecution, unresolved fresh intents, later success/continuation,
codec compatibility and structural rejection with adjacent Given/When/Then
clauses. These pure actual-shaped fixtures assert no installed SBT/provider
acceptance. Parent-selected focused validation, the slow ordinary-tool and
installed connected qualification in the same GOAL Step, and final INTEGRATION
aggregate validation remain unfinished; this extension records no PASS,
acceptance, review-cycle change or repair-cycle consumption.

## Retained selected Step precheck continuation

Native Closing uses the same original Input and standing authority lookup on
each ordinary Advance. The host must explicitly declare 1..256 unique valid
`selectedPaths` for each `goal-step-N` on all three kinds `commit-selected`,
`check-selected-index` and `selected-index-changes`. Those vectors select only
that Step's owned paths, including any deliberately shared document paths.
There is no candidate-path or all-index fallback; update obsolete host
declarations before use. Fixed typed `blob-content` and `tree-changes` reads
and their actual grants are also required. These declarations create no
authority or accepted review.

Before dispatch, native branch, HEAD and HEAD tree still match the original
parent, and selected dirty content is staged and merged without selected
unstaged/untracked data. Unrelated staged and unstaged work is preserved; no
global index-tree comparison, unrelated stage/reset/unstage or reset-to-empty
ritual is required. CNCF retains ordinary typed UoW execution and grants.

The actual selected precheck is saved before any commit intent. A nonclean
precheck on the unchanged reviewed candidate ends this Advance with Wait at
that saved native Closing result, showing its actual finding paths and
diagnostics. The owner has dispatched no commit and saved no commit intent.
Correct only the selected staged content, then use the next ordinary Advance
under the original selection and standing authority. This known-no-commit
continuation changes no generation or repair counter and introduces no Human
reapproval, semantic work or same-call retry. An unresolved clean precheck,
unknown intent/result or interrupted acknowledgement remains Wait; missing
success or a generic exit never establishes nonexecution or replay permission.

An earlier actual EOF-only failure can permit only removal of extra terminal
LF bytes from the same reported owned paths. Complete ordinary UTF8 typed blob
bytes without NUL must establish unchanged regular mode `100644`/`100755`,
`M` status, present distinct old/new blob IDs, an original ending in at least
two LF bytes and a nonempty result containing all other bytes followed by
exactly one LF. The committed tree must have exactly that native path/mode/blob
change vector and the original single parent; selected dirty paths disappear
while unrelated work may remain. Semantic bytes, trailing spaces, CR, modes,
renames, deletions, foreign paths or unavailable/incomplete producer values
require affected validation/review rather than reuse of original results.

The v3 receipt retains the original candidate, checks/review targets and review
reference plus the actual attempt/precheck/native IDs. Failed results remain
append-only; only an actual latest known-failed frontier can select the next
attempt. A clean check saves one durable intent before one selected commit;
unknown outcomes never replay. Completed reconstruction admits every retained
record as its exact actual history prefix, re-observes immutable correction
blobs/tree changes and follows the normal next-Step transition. Old completed
v3 facts remain readable only under their exact original reviewed-tree meaning;
an incomplete old intent stays unknown and authorizes no new effect.

Adjacent Given/When/Then in `GoalPhaseNativeClosureSpec`,
`GoalPhaseOrderedProgressionSpec` and
`GoalPhaseNativeRetentionApplicabilitySpec` specifies this bounded behavior.
These targeted tests establish no installed acceptance or production
availability. Parent-selected focused validation and the actual installed
selected staging → EOF failure → correction → one commit → next Step proof,
available cost observations, aggregate validation and comprehensive Phase review
remain with their existing owners.
