# Native client and task-private skill installation

For the practical setup and task sequence, start with
[the three-skills start-of-use guide](../notes/three-skills-start-of-use.md).
The Phase 6.3 supported tiers are ordinary split planning, synchronization of one
selected repository and limited Goal development in one repository. Production
acceptance depends on formal Phase closure. Dated historical fixture-only and
pending statements below retain their original evidence bounds; they do not
replace the current release scope or establish its acceptance.

Use the ordinary CNCF launcher with the production component factory and a
normally configured host. The [business client contract](workflow-business-client.md)
defines named native command/client calls and the exact caller routing file. The
[common Operation loop](common-workflow-operation-loop.md) defines current
Operations, business types, issued routes and completion behavior. These remain
the interface authorities; installation introduces no transport or route table.

Current real task execution uses `command`. Client qualification is one
lightweight ordinary FQN Start connection with actual canonical-ID retention;
that connection does not complete a task. Normal client supports the same
Operations and named business parameters as command, with execution placement
and connection settings owned by CNCF. Semantic skills return the actual typed
result and host control; normal supported semantic handoffs use the host's
finite `complete` action described below.

Normal command Advance Requests expose the generated
[issued work context](native-issued-work-context.md) alongside their business
view. Read the assigned semantic mode, canonical identity, generic requirement
and completion/evidence contracts from that actual output. Preserve the outer
Goal ID and exact issued child submission ID/operation/type when child work is
forwarded. Private input payload and presentation are omitted; context grants
no consent, permission, provider assurance or task acceptance. Human, Wait and
Terminal carry no issued-work view. Submit and explicit Advance retain their
native owners and business contracts.

## Complete one issued semantic handoff

The installed host uses `scripts/native-goal-command.py --action complete` with
the usual `--primary`, `--host`, exact already-owned `--task`, fresh empty
`--output`, and required absolute `--request`/`--submission` files. Follow the
[normal host completion recipe](native-goal-command-task.md#return-the-scoped-semantic-result).
Load the actual installed semantic skill for the issued work, obtain its genuine
typed result and consumed evidence, and return control to this host action.

`complete` consumes the exact task routing file and actual Request DTO. It
permits only the existing fixed Goal pair, three Split pairs and three Repository
review/repair/conflict pairs compatible with the retained outer family. Goal's
Repository child retains its distinct Submit ID and outer Goal Advance ID.
It provisions an exclusive `advance` output child before Submit, captures raw
Submit response/stderr in the selected output, validates and displays the actual
Submit message, Boolean accepted and outcome as one flushed JSON line. Only
matching-ID `accepted=true`, `outcome=accepted` permits one explicit ordinary
outer Advance. Capture its response/stderr in `advance`, verify actual outer ID
and kind, display its JSON as the second line, and stop on Request, Human, Wait
or Terminal. There is no user continuation prompt or continuation-only AI turn
between that accepted Submit and Advance, and no semantic work inside the action.

Rejection, malformed or contradictory response, transport failure, timeout or
uncertain Submit stops nonzero without Advance. A failed/uncertain Advance keeps
the already displayed Submit and available output, returns nonzero, and performs
no retry, resubmission, restart, discovery or pending-call replay. Human retains
its independent actual channel and Wait follows its actual condition without
spin. Existing single-action Submit/Advance operations retain their exact output
behavior for diagnostics and explicit calls. `complete` gives semantic skills
no Advance authority and installs no provider or review engine. Mechanical
fixture specifications do not establish installed route acceptance, genuine
task provenance or Phase completion; actual installed-use evidence remains due.

## Finite host execution actions

For an actual mapped Request, the existing `scripts/native-goal-command.py`
supports two additional finite actions through the ordinary preparation service:

| Action | Required action options | Optional action option | Normal Operation |
| --- | --- | --- | --- |
| `prepare-execution` | `--request`, `--mapping`, `--origin` | none | `NativeHostPreparationService.prepareExecution` |
| `record-execution` | `--request`, `--execution-session`, `--observation` | `--reference` | `NativeHostPreparationService.recordExecution` |

Both also require the existing `--primary`, `--host`, `--task`, `--action` and
`--output` options, as exact named pairs. Paths are explicit absolute normalized
existing files/directories. Output is a selected fresh directory beneath the
selected already-owned task. Mapping and origin select the original canonical
snapshot and origin wrapper; execution-session explicitly selects the previous
call's retained `execution-session.json`. Observation contains the actual flat
CNCF selected-attempt JSON, including the observed attempt/profile, outcome,
descriptor when applicable, and only observed resource metrics. COMPLETED
requires one generated WorkflowArtifactReference JSON file via `--reference`;
other outcomes forbid that reference.

The caller reads the exact retained Goal routing file and requires actual
Request.sessionId to equal its outer ID. It permits that Goal's existing own
work or forwarded Repository review/repair child route and forwards the complete
nonempty issuedWorkContext object inline without renaming nested fields. Consumed
declaration/wire aliases must agree, including
issuedWorkContext/issued_work_context, nextSelection/next_selection and
completionEvidence/completion_evidence. Native execution still rejects unavailable
non-Goal class/disposition mappings. No new family or target selector exists.

Each selected action invokes exactly one normal command with one typed `--input`.
It retains `response.json` and `stderr.log`, the actual opaque Session string
unchanged in `execution-session.json`, optional canonical selection text unchanged
in `execution-selection.json`, and optional actual structured evidence in
`execution-evidence.json`. These files are host artifacts, separate from the
business `workflow-session.json` route. Existing files are never overwritten.
A native failure, malformed response, contradictory consumed alias or transport
uncertainty stops without promoting completion evidence or retrying.

The ordinary loop is explicit: retain the actual Request; prepare once with its
original configuration; retain the returned Session; perform only actually
available and admitted selected host work; record its honest observation using
the explicit saved Session. Only an allowed first UNAVAILABLE/DECLINED may expose
the one configured alternate. Record that observation before doing the alternate
work, then explicitly select that record call's Session for its later observation.
Do not scan for newest artifacts, reconstruct a lost Session, re-prepare pending
work or use a later mapping to replace its retained selection. Blocked, failed,
uncertain, exhausted and INLINE-unavailable work ends without success-path Submit
or Advance. Missing metrics remain unknown.

Only final operational COMPLETED returns completion evidence. Supply that actual
generated evidence with the compatible semantic result/reference to the existing
named Submit through the host's `complete` action for a supported issued route;
that action checks and flushes actual accepted/message/outcome before its one
explicit outer Advance. Neither preparation/recording action invokes a
provider, ranks/chooses attempts, auto-Submits or auto-Advances. Config/descriptors
authenticate no executor, reviewer, operator or effect permission. Follow the
[three public shapes and current reader contract](native-host-execution.md#r6--three-generated-public-values-and-two-normal-operations).
Command remains the operational route, client remains light connectivity,
skills retain their semantic responsibility, and PHASE-6.3 retains aggregate validation.
Actual installed providers, all-family tasks and genuine two-chat acceptance
remain required.

## Prepare the current component

Select the actual sm-workflow checkout as `primary`. Request the registered
`cncf_command_runner` to run this logical SBT argument vector in that checkout:

```text
["--batch", "cozyPrepareRuntime", "Compile/packageBin", "skillBundle"]
```

The runner uses the machine-wide
`/Users/asami/.agents/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh`
route with normal SBT caches and scoped access. Wait for its terminal receipt
and `lock=released` before another top-level SBT invocation. The launcher runs
no SBT. No upstream build, publication or new global tool is required by this
recipe; unavailable dependencies remain a preparation prerequisite.

Preparation provides `target/cncf.d/runtime-classpath.txt`,
`target/cncf.d/component-descriptor.json` and
`target/cncf.d/car-runtime-manifest.json`, the current packaged component JAR,
and `target/sm-workflow-skills-0.1.0-SNAPSHOT.zip` at the current project version.
Select the ZIP returned by `skillBundle` when that version changes. Do not use
a stale bundle or add the JVM test classpath to the native runtime.

## Admit the owning host

The host has its own absolute working directory, `.textus/config.json` and
selected `.textus/assembly.yaml`. Follow the
[normal host configuration](normal-three-workflow-host.md#host-configuration)
for common Workflow persistence, typed `sm-workflow.host.definition`, selected
record/plan destinations, separate evidence declarations and actual Subsystem
storage/process plans. Select the official development component with
`--component-dev-dir <primary>`; its production `impl.ComponentFactory` owns
the three-family composition.

The assembly owns a stable subsystem identity and standalone user mode, with
convention authentication and an explicit `local_subject`. The effective normal
standalone identity must agree with the selected input participant. Existing
HOME standalone-user profiles participate in normal framework resolution; an
incompatible or malformed profile is a prerequisite failure. Do not override
HOME or introduce a controlled test descriptor, deterministic ID provider,
fallback privilege or injected execution context to conceal that failure.

When initial selected inputs are imported, trusted file configuration selects
`textus.import.data.file` pointing to an explicit JSON file. Its current shape
is `{"datastore":[{"collection":"entity:<major>.<minor>.<name>","records":[{"id":"<business key>","<storage payload field>":"<typed storage encoding>"}]}]}`
for EntityCollection imports. Goal selection uses
`entity:smworkflow.cli.goal_selection`; its selected Plan uses
`entity:smworkflow.cli.goal_plans`. This import field uses the entity address,
while host storage configuration and grants retain canonical
`EntityCollectionId.print` values. Do not put those canonical printed IDs into
the import file's `collection` field.
For Goal selection, use `NativeWorkflowStartBusinessKey` with the actual input
and participant, `ProfileSelectionJsonV1` and `GoalPhaseSelectedPlanJsonV1` with
the actual current Definition/Plan serializers. The selected Plan relates to
that selection and participant. CNCF `StartupImport` owns the import. Storage
grants declare destinations; they do not create selected inputs or consent.
Never seed instances, continuations, event/cursor/history or fabricate completed
work, Human issuance or independent review evidence.

Real Git observations use protected typed `git_*` intents in the owning call's
current UnitOfWork. The host supplies separate strict
`cncf.runtime-process-execution.v3` JSON for the canonical existing workspace,
fixed endpoint/branches, normal Git executable, limits, environment and actual
component workspace/process grants. CNCF normal bootstrap binds those declarations
and the interpreter checks current authority; CLI arguments and semantic results
supply neither execution configuration nor argv. The retained
`RepositoryGitRuntimePrograms.Target` is business data only. See
[normal typed Git workflow](normal-typed-git-workflow.md). Mechanical validation,
review and closure need their own genuine admitted prerequisites when reached.

Ordinary outbound client HTTP also needs confidential trusted host configuration
from the owning Subsystem's Cwd, Home or Project files. For Start, grant the
canonical built-in Client owner `org.goldenport.cncf.Client` the `POST` method
at the exact selected server origin, with finite connect/read/response limits.
The owner is the CNCF Client component. The SmWorkflow Operation's metadata and
the caller's `--baseurl` select business work and connection, respectively;
neither grants HTTP authority. CLI options, ambient properties and component
metadata cannot install this host plan.

For an explicitly selected illustrative server port `18080`, merge this field
into the owning host's `.textus/config.json`. The parameter value is a JSON
**string** containing the strict inner plan; `baseUrl` ends in `/`, and the
grant names only the exact origin and POST method:

```json
{
  "textus.subsystem.http-execution.plan": "{\"schema\":\"cncf.runtime-http-execution.v1\",\"baseUrl\":\"http://127.0.0.1:18080/\",\"limits\":{\"connectTimeoutMillis\":1000,\"readTimeoutMillis\":10000,\"responseBytes\":1048576},\"grants\":[{\"componentId\":\"org.goldenport.cncf.Client\",\"origins\":[\"http://127.0.0.1:18080\"],\"methods\":[\"POST\"]}]}"
}
```

Use that same selected port in the server configuration and ordinary client
`--baseurl`. The normal native fixture selects its scenario's actual available
port and writes this plan before startup. Nonremote scenarios omit the HTTP
field. Missing HTTP configuration must fail the outbound call with
`http.execution.unconfigured`; a GET-only grant at the same origin must refuse
POST Start with `http.execution.method`. These are current-call CNCF authority
checks. See the producer's [HTTP execution contract](../../../goldenport-cncf/docs/spec/http-execution-runtime.md).

The native qualification specification uses a fresh scenario under
`target/sm-workflow/work/native-cli`, a clean owned local Git repository and
local bare upstream. Its selection and Plan rows are explicit specification
inputs. Its ordinary command scenarios grant Git observations and stop at the first assigned Request;
it grants no ScalaTest/JVM-test-classpath check or fake validation. Those inputs
exercise admission mechanics and are not genuine human consent or review.
The explicit denied-Git scenario retains the same workspace and registered
observation declarations but withholds their component process capability
grants. Its one ordinary command Start must fail with
`process.execution.capability-grant` and no successful started result.

## Install the selected skill bundle

Choose an already-owned semantic task and a fresh destination beneath that
host's target tree. For example, with the current bundle version and explicitly
chosen paths:

```sh
primary=/absolute/path/to/sm-workflow
host=/absolute/path/to/owning-host
bundle="$primary/target/sm-workflow-skills-0.1.0-SNAPSHOT.zip"
skill_parent="$host/target/sm-workflow/tasks/owned-semantic-task"
skill_destination="$skill_parent/installed-skills"
mkdir -p "$skill_parent"
mkdir "$skill_destination" && unzip "$bundle" -d "$skill_destination"
```

Keep the existing names `sm-goal-phase`, `sm-split-phase` and
`sm-repository-sync`. Their sources are installed at
`<skill_destination>/skills/<skill-name>/SKILL.md`. Use existing `unzip`; do
not install into the user's home, replace existing skills or select a bundle
by searching arbitrary candidates. An occupied destination requires an
explicitly chosen fresh task-private destination.

The same ZIP also installs four supporting contract resources at
`<skill_destination>/docs/spec/`:

- `native-host-execution.md`
- `goal-phase-acceptance-boundary.md`
- `goal-phase-dependency-scope.md`
- `goal-phase-validation-metadata.md`

Together with the three `SKILL.md` files, these are the bundle's exact seven
files. Before loading a skill, verify that all seven are present in the fresh
destination and that its direct `../../docs/spec/` Markdown links resolve there,
removing any `#fragment` before checking the file path. These contracts support
the installed instructions' host execution, acceptance boundary, dependency scope
and validation metadata guidance. Use the selected bundle's installed resources;
missing files require correcting that bundle and installing into a fresh
task-private destination, with no copy from another checkout or silent fallback.

A capable semantic host must load the installed `SKILL.md` source for the
actual assigned work. Listing or extracting the files is only installation,
and ordinary CNCF CLI execution alone does not load an AI skill. The host
supplies actual issued business work and genuine result/evidence destinations
to the loaded skill. The skill performs that work and returns its actual typed
result and host control. The hosting caller owns `complete` for supported issued
routes, Start, explicit Advance and independent Human admission; semantic skills
do not implement caller loops or gain authority to Advance.

## Prepare actual selected Goal input and Plan

Before Goal provisioning, the owning host records its attributable actual
selection of business input and the complete ordered Plan, with explicit
selection identity, participant and idempotency key. Follow
[Goal Start preparation](native-host-goal-start-preparation.md) for the normal
`org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareGoalStart`
COMMAND with one `--input` WorkflowGoalStartPreparationInput JSON object. Its
`input` and `plan` Strings are absolute local file: URIs for generated Goal business
JSON and the bare existing ordered Plan. Explicit `selectionId`, `participant`,
`origin: ExplicitHuman`, `idempotencyKey`, `selectionCollection` and `planCollection`
retain the selected host facts. Collections use strict dotted entity addresses and
must be distinct. Use the [canonical normal launcher/input contract](normal-host-preparation-operations.md).
Current-call CNCF readText owns UTF-8 reads, resource acquisition/release and runtime
caps; request creation reads nothing. Normal errors produce no successful importer.

After observing success, the caller captures stdout in its task-owned host input
file and selects that exact file as `textus.import.data.file`. Existing ordinary
StartupImport owns mutable save/create provisioning of exactly two selected-input
rows. The tool reuses the packaged Goal definition, generated input reader/JSON
codec, existing Plan codec and native storage/business-key owners. It writes no
file or row, creates no runtime state, starts no Workflow and grants no authority.
The explicit origin label and successful tool conversion establish no Human
provenance; attributable real selection must precede provisioning. Native owners
retain participant, selection, scope, authorization and semantic Plan admission.

These selected inputs are a prerequisite for normal Goal Start. Preparation and
specification transport success cannot satisfy actual installed task, provider,
independent Goal or original Step/Phase closure obligations. Ordinary command
FQNs, canonical task-ID retention, installed skill procedures and the one normal
client Start connection smoke remain unchanged.

## Prepare actual selected Repository Start

The owning host records its attributable actual Repository business selection
before provisioning. Follow
[Repository Start preparation](native-host-repository-start-preparation.md) for
`org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryStart`.
Use the same ordinary CncfMain command prefix and one `--input`
WorkflowRepositoryStartPreparationInput JSON object. `input` is an absolute local
file: URI for the six-field RepositorySyncStartInput; `selectionId`, `participant`,
`origin: ExplicitHuman`, `idempotencyKey` and `selectionCollection` are explicit.
The host selects the strict dotted import destination matching its configured
selection storage/read grant. The direct structured datastore response has exactly
one business-key selection row. Save it separately to trusted textus.import.data.file;
ordinary StartupImport owns mutable save/create. Preparation certifies no provenance,
grants no authority and invokes no Workflow, importer or Git process.

Current native Start retains participant, selected target and business-entry
admission. Goal PhaseClosure permission does not supply its child Start business
selection. A fresh real Goal host needs both attributable Repository selection
and genuine separate PhaseClosure authority before child entry. Accepted review
and Start grant no `CommitPending`, `Push` or `FinalFetch`; separately issued
RepositoryEffect inputs remain necessary at their owning boundaries.

A failure ends the caller branch. Use a NEW execution with its actual returned
canonical ID and exact task-owned routing file; do not automatically replay the
failed task or infer rollback or interrupted recovery. Preparation and controlled
fixture Start are prerequisites, leaving genuine installed task acceptance and
the existing Operation parameters, skill procedures and remaining obligations
unchanged. Actual work uses command, the single client Start remains light
connection qualification, and PHASE-6.3 retains aggregate validation ownership.

## Use the normal native entry

Run each call from the owning host directory so normal host files are resolved.
The following illustrative local Start supplies named business input and one
usable execution proposal. Use the host's actual admitted phase/repository:

```sh
cd "$host"
"$primary/bin/launcher" \
  --classpath-file "$primary/target/cncf.d/runtime-classpath.txt" \
  --main-class org.goldenport.cncf.CncfMain -- --force-exit \
  --component-dev-dir "$primary" \
  --textus.assembly.descriptor="$host/.textus/assembly.yaml" \
  command org.simplemodeling.textus.SmWorkflow.GoalPhaseService.startGoalPhase \
  --input '{"phaseIdentity":"phase-6","repositoryIdentity":"sm-workflow"}' \
  --sessionId '<usable proposal for this one execution>' \
  --json
```

The existing `--force-exit` external process-adapter switch propagates ordinary
operation exit status to the shell. Use this same launcher prefix for command,
client and server calls.

Omit the `--sessionId` parameter when no usable proposal exists. A successful
zero-exit business response supplies the actual nonblank `sessionId` and
`message`. Display that message and create the selected task's exact
`target/sm-workflow/tasks/<already-owned-task-directory>/workflow-session.json`
with only `family` and that actual `sessionId`, as specified in the
[caller routing contract](workflow-business-client.md#task-owned-canonical-session-routing).
Always retain the actual ID if it differs from the proposal. Never replace an
occupied record, search task candidates or reconstruct routing from native
storage or prose. A second execution, including one for the same participant,
owns a separate task directory and returned ID.

For an independent local Advance, retrieve that exact file anew and use the
same launcher prefix with:

```sh
command org.simplemodeling.textus.SmWorkflow.GoalPhaseService.advanceGoalPhase \
  --sessionId '<canonical ID just retrieved from the selected task file>' \
  --json
```

For REST, the server host explicitly configures `textus.server.host` as numeric
`127.0.0.1`, a selected available `textus.server.port`, and a scenario-local
`textus.server.port-assignment-file`. Start the normal launcher with that host
prefix and `server`. The caller owns and reaps the returned server Process;
do not find or kill another process by port or name. The native qualification
fixture bounds readiness to 30 seconds, child invocations and the server
scenario to 60 seconds, and cleanup to a five-second TERM grace followed by
force/reap when needed. Readiness checks actual HTTP, never Workflow Advance.

The client selects that exact server with the normal `--baseurl` request option
on every remote call. With the same prepared launcher prefix, use FQN Start:

```sh
client org.simplemodeling.textus.SmWorkflow.GoalPhaseService.startGoalPhase \
  --input '{"phaseIdentity":"phase-6","repositoryIdentity":"sm-workflow"}' \
  --baseurl 'http://127.0.0.1:<selected port>' --json
```

A normal client supports Advance with the same named business parameter as
command. This illustrates remote capability beyond the selected one-Start
connection qualification:

```sh
client org.simplemodeling.textus.SmWorkflow.GoalPhaseService.advanceGoalPhase \
  --sessionId '<canonical ID just retrieved from the selected task file>' \
  --baseurl 'http://127.0.0.1:<selected port>' --json
```

CNCF owns transport mapping and named-parameter serialization for every normal
client Operation. Callers do not construct HTTP paths or bodies. Use the
[business client guidance](workflow-business-client.md#cncf-owned-remote-transport)
and actual generated Operation declarations for other calls. Raw client-http is
internal deprecated compatibility and is excluded from new public recipes and
qualification scenarios. Decode actual structured business output through its
declared generated ValueReader. Generated numeric reads follow the core
[exact JSON integer contract](../../../goldenport-core/docs/spec/value-reader-json-integers.md).
Malformed, nonbusiness or error output remains failure, not an alternate
envelope or inferred acceptance. A current Request must retain the owning
outer ID and provide actual work, `submitOperation`, `submitSessionId` and
`resultType`. Submit only that issued route and compatible typed result with
genuinely supplied consumed references. Goal child Submit uses its issued
child ID; the next Goal Advance retains the outer ID.

The normal `--json` option selects JSON with the existing default `data` output
shape. Local commands and the server invocation do not need `--baseurl`.

Goal review Requests also retain `work.review.hostContext`; Repository merge
review Requests retain `review.hostContext`. A Goal repository child forwards
the actual child's review context with its issued child Submit route. These
nested generated Values contain the actual owning component, selected native
target and issued completion context as structured Records. Implementation,
repair and Human Requests provide no review host context.

An independent owning host uses the public
[native host review preparation](native-host-review-preparation.md) API with the
actual independent review response, result reference, worker, explicit issuer
and time. The returned managed row is passive input; the host owns genuine review
and provisioning through existing input/bootstrap, and the configured native
Reader owns applicability and read permission. Preparation supplies no Human
consent or Git/check/closure grant. Specification-created rows are prerequisite
behavior evidence only; actual host/writer integration and real tasks remain D2.

Submit keeps its existing named `sessionId`, compatible typed result and genuine
reference/completion/execution evidence fields. The caller never echoes native
handle, run, continuation or revision as Submit controls. For supported semantic
routes, select the host's `complete` action to display and flush the actual
Submit acknowledgement before one explicit outer Advance in the same finite
host action. Human responses retain their independently admitted host procedure.

A bare `sessionId` (including `session_id`) declared by the resolved Operation
is business context. CNCF preserves it in the original operation Request while
excluding only that colliding bare name from authentication projection. Explicit
`x-textus-session`, `x-cncf-session`, `textus.session`, `cncf.session` and session
cookies remain authentication material. An undeclared bare session alias retains
its authentication meaning; a business ID alone supplies no authentication evidence.

## Rules and executable examples

R1: Use normal launcher/production host inputs and strict declared business JSON
results. R2: Retain the actual canonical ID in the exact owned task file and read
it anew for independent calls. R3: Select the owned remote endpoint with explicit
`--baseurl` on ordinary FQN client calls. R4: An error or rejected input stops the
caller branch without automatic progression, retry or restart.
R5: Existing normal client and command calls consume explicit trusted host
authority. Client Start requires the exact built-in Client/origin/POST grant
and finite HTTP limits; registered typed Git operations require their component
process capability grants in addition to the workspace binding. Connection
options, business input and Operation metadata supply neither grant. Missing or
insufficient authority must produce a nonzero invocation without a successful
started business result, and that caller branch ends there.

`NativeWorkflowCliSpec` binds these examples through visible afterWord metadata
and adjacent Given/When/Then. This mapping records obligations, not execution results.

| Example | Rules | Observable contract |
| --- | --- | --- |
| E1 | R1,R2 | Proposed-ID Start retains the actual ID for independent Advance |
| E2 | R1,R2 | Three executions interleave with separately retained canonical IDs |
| E3 | R1,R2,R3,R5 | With the exact built-in Client/POST/origin host grant, one ordinary FQN Start reaches the selected loopback endpoint, retains and rereads its actual canonical ID, and is the final client call; server plus Start use two distinct native PIDs |
| E4 | R2,R4 | Omitted required Advance ID fails and ends the call sequence |
| E5 | R2,R4 | Blank required Advance ID fails and ends the call sequence |
| E6 | R1,R4 | Incompatible Start input fails without following a successful execution |
| E7 | R1,R3,R4,R5 | Without an HTTP plan, exactly one ordinary FQN client Start with named input and `--baseurl` fails nonzero with `http.execution.unconfigured` and no successful started result; the sequence ends at server plus one client |
| E8 | R1,R3,R4,R5 | A GET-only grant at the same exact owned origin refuses the ordinary POST Start nonzero with `http.execution.method` and no successful started result; the method-scope sequence ends at server plus one client |
| E9 | R1,R4,R5 | With its workspace and typed Git declarations retained but observation process grants withheld, exactly one ordinary command Start fails nonzero with `process.execution.capability-grant` and no successful started result; no server or subsequent call occurs |

E7, E8 and E9 end without any subsequent retry, Start, Advance or fallback. E1 and E2 retain
the ordinary scoped command success and independent continuation obligations.
The HTTP negatives reuse the producer's pre-driver zero-effect assurance;
this native fixture observes consuming-path rejection and its invocation
sequence, without asserting transport counters. The Git negative makes no
runtime launch-count or storage-atomicity claim. These failed calls carry no
issued-work-context example mapping.

These nine scenarios specify host consumption. Before execution, their presence
establishes no security PASS. Even successful execution would not establish a
genuine installed semantic task, independent review, operator consent or
Step/Phase acceptance; the existing remaining obligations still apply.

## Remaining acceptance

Missing/blank required IDs, incompatible inputs, transport or conversion
failure, rejection and uncertain effects end the current caller branch. They
authorize no automatic Advance, retry or restart. Human, Wait and Terminal
follow their actual common-loop conditions. Genuine human issuance and
independently suitable review remain requirements at their owning consumers.

S6-01D1 qualifies normal bootstrap, command calls, one lightweight normal-client
Start connection and independently retained caller routing. Its task labels do
not prove different real human chats or real task completion.
S6-01D2 still requires actual installed skill use on a real semantic task,
all-three-family installed command task completion, issued Goal child routing, differing canonical-ID
consumer proof and broader error/Human/Wait/Terminal behavior. Typed modes,
provider replacement/fallback, execution independence and Effective Acceptance
Boundary work remain incomplete. Independent review, proportionate Step
validation/acceptance and full Phase closure also remain incomplete. These
installation instructions contain no executed-command or task acceptance claim.

## Prepare actual review and Human host input

Retain the actual normal command Advance Request/Human JSON at its assigned
boundary. The independent host reviews the assigned candidate or obtains the
native Human answer and supplies actual result/reference/execution metadata,
issuer and occurrence. Follow [Human preparation](native-host-human-preparation.md)
and the [normal evidence preparation Operation](native-host-evidence-tool.md). Use
`org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareEvidence`
with one concrete WorkflowEvidencePreparationInput JSON object through the
[standard CncfMain launcher](normal-host-preparation-operations.md). File fields are
absolute local file: URIs; metadata and the existing Human/review rules stay explicit.

The tool prints standard EntityCollection bootstrap input. The caller saves its
successful stdout to a trusted configuration-selected `textus.import.data.file`
input; the next ordinary command startup uses existing CNCF StartupImport
save/create provisioning. The tool writes no row, policy or file and the returned
input is no committed receipt. Provisioning remains mutable without an immutable
or atomic import promise. Encoding and the issuer option do not authenticate
independent review or Human origin.

Existing current Reader, managed read permission and native completion admit the
actual purpose-specific input. Keep normal Human/review Submit parameters and
acknowledgements; explicitly Advance afterwards. Human preparation grants no
review, Step/Phase/Git effect or Split Start authority. Real execution continues
through command, the lightweight client Start remains connectivity qualification,
and all Operations retain the same named business parameters on both placements.
Skill definitions and invocation procedures remain unchanged.

Actual trusted native Start/Step/Phase/Git context provisioning and
genuine independently attributable installed tasks for all three families remain
mandatory later D2 work. Fixture answers and compiled tool-process examples
cannot satisfy those actual task obligations or establish Phase acceptance.

## Prepare actual selected Split Start consent

The owning trusted host supplies its selected external allocation reference,
current v3 allocation Record, current selected Source Record and actual component
instance from normal selection/assembly. Follow
[Split Start preparation](native-host-start-preparation.md) for the unchanged
NativeHostStartPreparation.splitC business API and normal
`org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareSplitStart`.
Its one WorkflowSplitStartPreparationInput JSON object has componentInstance,
allocationReference, allocation, source, reference, participant, issuer, occurredAt
and collection. The four selected file fields are absolute local file: URIs;
CNCF owns their current-call UTF-8 acquisition. Use the [normal launcher contract](normal-host-preparation-operations.md).

The unchanged v7 consent scope retains the actual owner, allocation participant,
business input, selected Source reference/phase/authority and optional proposal
reference. The allocation reference determines the separate lookup. No fabricated
Workflow instance, session, native handle or private Workflow-store read is needed.
The host separately obtains genuine operator consent; JSON encoding and issuer
flags authenticate no provenance. Explicit metadata participant remains separate
from actual native principal admission, preserving current correlation semantics.

Successful stdout is one standard importer row, which the host saves to its
trusted `textus.import.data.file`; existing normal StartupImport owns mutable
save/create provisioning. Preparation itself writes no row, chooses no clock,
grants no permission and calls no Workflow. Current Reader/principal/read grants,
issuer/purpose/lookup/selected target/nonfuture occurrence and native consent
control admission. A NEW normal Start may then return its real session ID;
retain it in the exact caller-owned task file and separately Advance. Start
consent grants no Human answer, Step/Phase closure or RepositoryEffect permission.

Under the [adopted security profile](../phase/phase-6-security-review-profile.md),
declared input/target, least-authority DataStore use, consent/effect separation
and explicit file/process attribution require relevant evidence through existing
Step/Phase owners. Actual CAR lint/scoped review provider evidence remains open
until admitted; imported PLANNED producer documents establish no availability or
security PASS. No new scanner, policy or review stage is added. Actual tasks use
command, and the single normal FQN client Start smoke remains connectivity only.
Genuine host/operator/task provenance, separate D2E RepositoryEffect work,
all-three installed task loops, modes/providers/Boundary and full closure remain
required. PHASE-6.3 retains primary aggregate full-suite ownership.

## Prepare actual closing authority input

Retain the full actual Goal command Advance JSON when missing Step or Phase
authority produces a closing Wait with `authorityContext`. The selected context
contains the existing native target and owning component. Ordinary Waits and
Request/Human/Terminal or forwarded child results carry no closing context.
Accepted review provides no commit, child Start or Git effect permission.

The genuinely responsible operator independently authorizes that target and
supplies its actual reference JSON, participant, trusted issuer and historical
ISO Instant. Follow [closing authority preparation](native-host-authority-preparation.md)
for the normal `org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareAuthority`
COMMAND with one WorkflowAuthorityPreparationInput JSON object. Its request and
reference are absolute local file: URIs; participant, issuer, occurredAt and strict
dotted collection remain explicit. The actual current call uses ordinary CNCF
readText and the unchanged generated/business readers. Use the [normal route and input schema](normal-host-preparation-operations.md).

Successful stdout is one standard configured input row. The host saves it to its
trusted `textus.import.data.file`; the next ordinary startup uses existing
StartupImport mutable save/create provisioning. The tool writes no file or row,
calls no Workflow, chooses no clock/identity and grants no permission. Encoding
and issuer flags authenticate no operator origin, and the input is no immutable
or atomic receipt. Ordinary CNCF command errors produce no successful importer JSON.

Existing trusted Reader and native process grants control use. Step/Phase scope,
owner, lookup, issuer, purpose and nonfuture occurrence remain selected;
reference/participant metadata is preserved without adding target correlation.
The caller separately Advances under its existing procedure to observe the Step
commit or installed repository child result. Only observed child Terminal closes
the outer Phase. Skill and normal command/client parameters remain unchanged.

This passive preparation is a prerequisite only. Distinct Split Start consent
and RepositoryEffect inputs/provisioning, genuine independently attributable
installed command tasks for all three families and complete Step/Phase acceptance
remain mandatory. Fixture rows and finite process success do not satisfy them.

## Prepare actual selected RepositoryEffect input

Use the owning host's current ProfileSelection Record with String
`selectionPayload`, selected `RepositorySyncStartInput`, actual component instance
and explicit physical Git target. Follow
[RepositoryEffect preparation](native-host-repository-effect-preparation.md) for
`NativeHostRepositoryEffectPreparation.repositoryC` and normal
`org.simplemodeling.textus.SmWorkflow.NativeHostPreparationService.prepareRepositoryEffect`.
Select exactly one CommitPending, Push or FinalFetch. One
WorkflowRepositoryEffectPreparationInput JSON object supplies componentInstance,
selection, target, effect, reference, participant, issuer, occurredAt and collection.
selection, target and reference are absolute local file: URIs, read in that order
through current-call CNCF readText. Target retains String root/localBranch/remote/
remoteBranch/endpoint. The dotted import address remains distinct from canonical
storage IDs in grants. See the [normal launcher and schemas](normal-host-preparation-operations.md).

The genuinely responsible operator independently authorizes the exact current
effect. JSON and origin/issuer flags certify no consent. Current RepositoryEffect
observation retains equality between metadata operator participant and the selected
scope participant. Preparation can encode a different nonblank participant; native
observation refuses it. Start and accepted review give no Git effect permission.

The caller saves successful stdout to trusted `textus.import.data.file`; ordinary
CNCF StartupImport owns mutable save/create provisioning. The tool writes no row
or file and invokes no Workflow/Git/process. Current Reader, native principal/read
grants, target/lookup/issuer/purpose/time and independent effect/process/CAS/intent
owners control use. Sole help reads nothing; failure exits two with its own stderr
diagnostic and no JSON. There is no automatic Start/Advance/replay/retry. A NEW
execution returns its own actual session, retained in its exact owned task file,
and separately Advances under the existing skill and Operation procedure.

Actual tasks continue through command and the one normal FQN client Start smoke
remains light connectivity evidence. The selected security boundary uses existing
Phase 6.1/6.2/6.3 evidence owners; imported PLANNED provider documents prove neither
availability nor PASS. Fixture rows and finite process examples prove no genuine
operator/provider provenance. All-three installed command tasks, modes/providers,
Effective Acceptance Boundary and full original Step/Phase closure remain
mandatory; primary aggregate full-suite validation stays with PHASE-6.3.
