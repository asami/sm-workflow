# Native Goal command task

Use the existing packaged CNCF production Component and normal command
Operations. Workflow issues meaning-bearing work; the current Codex/semantic
host loads the task-installed `sm-goal-phase`, performs that work and returns its
declared typed result. Follow the installed skill's procedure and the
[common Operation loop](common-workflow-operation-loop.md). Direct
AiRunner/TextusAI invocation is not a prerequisite.

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
configured destinations. This finite action delegates to
`org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareGoalStart`
through normal `CncfMain` COMMAND with one named `--input` generated
`WorkflowGoalStartPreparationInput` JSON object and `--json`. The caller converts
the selected input and Plan paths to absolute local `file:` URIs; the DTO also
contains `selectionId`, `participant`, `origin: ExplicitHuman`, `idempotencyKey`,
`selectionCollection` and `planCollection`. It forwards opaque business files
without interpreting their contents.
It captures `response.json` and `stderr.log`, requires zero exit and a JSON object
with a `datastore` array, and creates no caller session. For Goal-only use, the
host may select that successful response as trusted `textus.import.data.file`;
ordinary StartupImport alone provisions the two selected-input rows. When the
installed Goal closure invokes RepositorySync, require the combined three-entry
envelope described below and in the pre-Start checklist before Goal Start.
Preparation and an origin label establish neither consent nor semantic admission.
Never seed Workflow instances,
continuations, history, Human, review or completed-work rows.

The current primary contract `docs/spec/normal-host-preparation-operations.md`
defines the six normal passive preparation Operations and generated DTOs. This
guide cites current primary-only documents by repository-relative contract name
when they are absent from this older selected worktree. The executing CNCF
ProcedureActionCall acquires UTF-8 through CNCF `readText`, subject to the host's
explicit `textus.resource.url.file.roots` and `textus.resource.read.max-bytes`.
URI metadata grants no read access. The normal configured launcher and service
own this call; there is no product-private preparation main, extra executor JVM,
application file reader or generic command/main/argv selector.

## Explicitly prepare Repository input

The owning host independently records an attributable selection of the actual
Repository business input and metadata before provisioning. Parent-selected
details remain attributed to that parent; supplied `ExplicitHuman` metadata
certifies no genuine Human source. The current primary repository contract
`docs/spec/native-host-repository-start-preparation.md` defines the generated
`RepositorySyncStartInput`, native business key and typed selection encoding.
That contract is cited by repository-relative name because this selected native
worktree does not contain it.

The six canonical business fields are `repositoryIdentity`, `localBranch`,
`trackingRemote`, `trackingBranch`, `remoteEndpointIdentity` and `syncMode`.
The selected file contains ordinary business JSON. Native `RecordDecoder` and
the generated `ValueReader` own decoding; the generated derived `Codec.AsObject`
owns canonical JSON, and `ProfileSelectionJsonV1` owns `selectionPayload`
storage encoding. The caller forwards the selected file as an opaque absolute
local `file:` URI. It adds no
business policy, permission or physical-target selection; actual native input,
principal and selected-target admission retain their existing owners.

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

Alongside the common `primary`, `host`, `task`, `action` and `output` options,
this fixed action requires exactly `input`, `selection-id`, `participant`,
`idempotency-key` and `selection-collection`. The input is an existing absolute
normalized file. The collection is literal `entity:` and exactly three dotted
labels matching `[A-Za-z][A-Za-z0-9_]*`, selected to match the configured storage
and read grant. The caller invokes the existing normal `CncfMain` launcher once
with COMMAND
`org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryStart`,
one `--input` generated `WorkflowRepositoryStartPreparationInput` JSON object
and `--json`. That object contains the file URI, `selectionId`, `participant`,
literal `origin: ExplicitHuman`, `idempotencyKey` and `selectionCollection`.
It reads no Workflow route; routing may be present or absent. Main, origin,
plan, request, proposal and permission are not selectable caller options.

Existing owned task/output preflight and exclusive raw `response.json` and
`stderr.log` capture apply. Require zero native exit and a strict JSON object
with a `datastore` array before displaying stdout unchanged. The passive native
owner emits one selected Repository row; preparation creates no caller route,
host configuration, import, storage, Workflow state or progression. Its business
address is no execution ID or immutable receipt.

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

An installed Goal's Repository child requires both separately admitted genuine
PhaseClosure permission and attributable Repository business selection. See the
current primary `docs/spec/native-host-authority-preparation.md` and
`docs/spec/native-host-repository-effect-preparation.md`, also absent from this
selected worktree. Actual Start, accepted Submit and accepted review grant no
`CommitPending`, `Push` or `FinalFetch`; genuine independently issued
RepositoryEffect authority remains separately admitted by its native owner.
Prepared rows and origin labels grant no permission or effect.

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
`Wait` follows its actual condition without polling; `Terminal` ends. The normal
`submit` action supports an outer Goal `Request` with
`submitOperation=submitGoalPhaseWorkResult`,
`submitSessionId=<outer canonical ID>` and `resultType=GoalPhaseWorkResult`.
The current finite caller also supports only the following declared Repository
child routes under that same outer Goal task:

| Action | Issued operation | Issued result type |
| --- | --- | --- |
| `submit-repository-review` | `submitNativeMergeReview` | `RepositorySyncNativeMergeReviewResult` |
| `submit-repository-repair` | `submitNativeMergeRepair` | `RepositorySyncNativeMergeRepairResult` |

For either child action, the Request's session ID must match the owning outer
Goal file. Its nonblank issued `submitSessionId` must be distinct from that outer
ID. Call the declared `RepositorySyncService` operation with this actual child
ID, require the response ID to match it and preserve the outer routing file for
the host's next Goal Advance. Never guess a child ID. Other families, work or
routes stop and return to the host; the caller exposes no arbitrary operation,
main or argv selector.

`prepare-review-input` checks the outer Goal route;
`prepare-repository-review-input` checks the declared child review route above.
Both take actual `request`, `result`, `reference`, `execution-evidence`, `issuer`,
`occurred-at` and `collection` options, convert selected file paths to opaque
file URIs and invoke normal `NativeHostPreparationService.prepareEvidence` with
one `WorkflowEvidencePreparationInput` JSON DTO. The current caller supplies
`family: GoalPhase` and `kind: review`; the native reader distinguishes the
actual forwarded child context. Review forbids participant metadata. Genuine
independent review must precede preparation; encoding supplies no provenance
or accepted review. The same public Operation supports genuine Human evidence
under its declared family/kind contract, requiring actual participant metadata
and the full actual Human output. Its result and reference must come from the
independent human channel. See current primary
`docs/spec/native-host-human-preparation.md`. Preparation itself never answers
Human or advances a Workflow.

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

Failure, rejection, malformed/duplicate-key/nonfinite JSON, conversion error,
timeout, incompatible principal/dependency, contradictory route or uncertain
effects preserve available output and end that call with nonzero exit. Stop
without retry, restart or automatic progression. Retain task artifacts for the
owning host; do not recover through status/history/storage.

## Owning-UoW checks and public review order

After accepted semantic Submit, the owning host explicitly selects Advance.
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
runtime and receipt contract, also cited by name when absent in this worktree.

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
independence, `minimumAcceptanceBoundary`, original Step evidence/acceptance,
`solePhaseReview` and release remain separate obligations under the minimum
Effective Acceptance Boundary. Preparation and selected checks discharge none
of those obligations by themselves.
