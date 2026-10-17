# Workflow business client

Use the existing CNCF `command` entry for current operational tasks on the
configured, authenticated host. Direct Operation API and normal client calls
consume the same declared business Operations. The generated business Values and their
readers provide the typed interface. No sm-workflow-specific adapter or transport
port is required.

Normal client uses the same Operation selectors and named business parameters
as command for all three families. CNCF owns execution placement, remote
connection settings and transport mapping. Semantic skill definitions and
invocation procedures remain unchanged when those settings change. Current
client validation is one lightweight ordinary Start connection and canonical
result check; complete remote operation/family task matrices are not required.
Actual installed command skill execution and real task completion remain required.

The [common Operation loop](common-workflow-operation-loop.md) owns the finite
Operation/type table, named parameters, completion metadata and result-reference
requirements. CML and generated metadata are authoritative; callers do not
maintain another route table. The [normal three-workflow host](normal-three-workflow-host.md#host-configuration)
defines admission, trusted storage/process configuration and independent
evidence. Authentication, a session ID, configuration or a decoded Value alone
does not grant Start consent, command authority or review/Human acceptance.

## Direct configured Operation

Use the owning Subsystem dispatcher after normal host bootstrap, with its actual
ingress-resolved actor, session and UnitOfWork. A new ExecutionContext alone is
not authenticated host admission. Supply named `Property` values; generated
Values, their Records and existing transport JSON are consumed as the declared
business types.

For example, after the host admits the actual GoalPhase Start selection:

```scala
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.textus.smworkflow.value as V

// owner, actor and input belong to the host's actual admitted call.
val startedC: Consequence[V.GoalPhaseStartResult] =
  owner.executeOperationResponseInContext(
    Request.of(component = "org.simplemodeling.textus.SmWorkflow",
      service = "GoalPhaseService", operation = "startGoalPhase",
      properties = List(Property("input", input.toRecord(), None))),
    actor
  ).flatMap {
    case OperationResponse.RecordResponse(record) =>
      summon[ValueReader[V.GoalPhaseStartResult]].readC(record)
    case _ => Consequence.stateConflict("Expected GoalPhaseStartResult RecordResponse")
  }
```

An available usable proposal adds named `Property("sessionId", proposedId, None)`;
otherwise omit that property entirely. Display the actual returned `message`
and retain the successful nonblank canonical `sessionId` as described below.
For an independent Advance, retrieve that task's canonical ID and pass only
named `Property("sessionId", canonicalId, None)` to `advanceGoalPhase`; read
the actual RecordResponse with the generated `ValueReader[GoalPhaseAdvanceResult]`.
The other families and issued Submit routes use their declared generated readers
from the common Operation contract. Failed dispatch, nonbusiness responses and
failed conversion remain errors; do not infer acceptance from presentation.

## Native CNCF command and client CLI

Use the host's configured prepared runtime and official launcher, with the
component's fully qualified business Operation. The owning host must already
have admitted its normal assembly and independent Start selection. Current
task loops use `command` with the fully qualified Operation and named business
parameters. Normal remote client calls select their intended CNCF server with
the recognized `--baseurl` option on each call. Do not rely on a host-file
`client.baseurl` setting for these native invocations. Do not put storage/process
grants or consent in CLI arguments.

These illustrative operational GoalPhase calls use named business inputs and
`--json`, which selects JSON with the existing default `data` output shape:

```sh
/path/to/sm-workflow/bin/launcher \
  --classpath-file /path/to/sm-workflow/target/cncf.d/runtime-classpath.txt \
  --main-class org.goldenport.cncf.CncfMain -- \
  --component-dev-dir /path/to/sm-workflow \
  --textus.assembly.descriptor=/path/to/assembly.yaml \
  command org.simplemodeling.textus.SmWorkflow.GoalPhaseService.startGoalPhase \
  --input '{"phaseIdentity":"phase-6","repositoryIdentity":"sm-workflow"}' \
  --sessionId '<usable proposal for this execution>' \
  --json

/path/to/sm-workflow/bin/launcher \
  --classpath-file /path/to/sm-workflow/target/cncf.d/runtime-classpath.txt \
  --main-class org.goldenport.cncf.CncfMain -- \
  --component-dev-dir /path/to/sm-workflow \
  --textus.assembly.descriptor=/path/to/assembly.yaml \
  command org.simplemodeling.textus.SmWorkflow.GoalPhaseService.advanceGoalPhase \
  --sessionId '<canonical ID retrieved from this task routing file>' \
  --json
```

Omit `--sessionId` on Start when no usable proposal exists. Advance consumes
the actual returned ID, including when it differs from the proposal. Substitute
the actual admitted business input and prepared host paths; preparation alone
does not complete an installed workflow task.

## CNCF-owned remote transport

Normal client maps the same declared Operation and named business input to
the existing CNCF server transport. CNCF owns the HTTP route, body construction
and serialization; callers do not reproduce that mapping or maintain a route
table. Existing ingress authentication and trusted host configuration still
apply. With the same prepared launcher/host prefix used above, a lightweight
remote Start uses:

```sh
client org.simplemodeling.textus.SmWorkflow.GoalPhaseService.startGoalPhase \
  --input '{"phaseIdentity":"phase-6","repositoryIdentity":"sm-workflow"}' \
  --baseurl 'http://127.0.0.1:<selected port>' --json
```

Optional usable Start proposals still use `--sessionId`; subsequent independent
calls retrieve the actual canonical ID from the selected task's routing file.
All Operations, including Advance and issued Submit, use their same command
selectors and business parameters under normal client. For example, remote
Advance uses:

```sh
client org.simplemodeling.textus.SmWorkflow.GoalPhaseService.advanceGoalPhase \
  --sessionId '<canonical ID retrieved from this task routing file>' \
  --baseurl 'http://127.0.0.1:<selected port>' --json
```

The same normal-client capability applies to SplitPhase and RepositorySync.
The Advance illustration does not extend the selected one-Start connection
qualification into remote task completion. Raw client-http is internal
deprecated compatibility only and is excluded from new public recipes.
Strict generated numeric intake follows the core
[exact JSON integer contract](../../../goldenport-core/docs/spec/value-reader-json-integers.md).
These examples illustrate existing contracts; they claim no execution or
installed-task acceptance.

## Task-owned canonical session routing

The hosting task's native CNCF command/client caller owns retention in the host
project's already assigned task work directory:

```text
target/sm-workflow/tasks/<already-owned-task-directory>/workflow-session.json
```

For that one existing task/execution, the complete payload is only:

```json
{"family":"GoalPhase","sessionId":"<actual canonical Start ID>"}
```

`family` is the owning Start family: `GoalPhase`, `SplitPhase` or
`RepositorySync`. This is caller routing metadata, which the host may expose
as this exact artifact to a skill. It contains no chat/actor token, Workflow
state, issued work copy, request snapshot, native control body, acceptance
stamp, permission or evidence. It is not native Workflow storage, a new
Operation argument, task selector, lookup registry or Codex-only facility.

After successful Start the host persists the actual nonblank returned ID,
even when different from its proposal, and retrieves this exact file for each
independent call. The task is already selected by the host: do not search task
directories, list candidates or expose another instance/selection identifier.
Missing, blank or invalid routing context is a normal error; never reconstruct
an ID from prose, previous work, native storage or another task.

Propose an available host/chat ID only when it is usable for this one execution;
never assume Codex exposes an ID. Otherwise omit the Start parameter. A host
chat ID is never implicitly reused for several Starts. Each separate execution
has its own task directory and file, including a second execution in the same
chat. Do not replace an occupied task's canonical ID. Failed or uncertain Start
does not create a successful session record and does not authorize blind
restart or replay.

For illustration, two independently selected tasks in different chats retain:

| Already-owned task file under the host project | Complete routing payload |
| --- | --- |
| `target/sm-workflow/tasks/task-a/workflow-session.json` | `{"family":"GoalPhase","sessionId":"canonical-goal-a"}` |
| `target/sm-workflow/tasks/task-b/workflow-session.json` | `{"family":"SplitPhase","sessionId":"canonical-split-b"}` |

The host may explicitly interleave A Advance, B Advance, B's issued Submit,
then A's issued Submit. Each call retrieves its own file; actual issued Submit
IDs remain attached to the corresponding current business view. A separate
RepositorySync execution started in A's chat owns
`target/sm-workflow/tasks/task-c/workflow-session.json` with
`{"family":"RepositorySync","sessionId":"canonical-repository-c"}`.
It neither replaces A's file nor reuses A's ID. These names/IDs illustrate the
contract and are not candidate discovery, real sessions or acceptance evidence.

## Explicit host loop and semantic participant

1. The host admits the actual human Start selection and invokes the existing
   Start. Display its actual message and persist the actual canonical outer
   family/ID in the owning task's routing file.
2. For explicit Advance, retrieve that task's record and use the outer ID.
   Display the actual message and consume the typed `kind`, current issued
   business view and route. Check its outer family/session agrees with the task.
   Do not infer state from text or query status/history/storage to recover a route.
3. `Request` assigns only the declared semantic work. `Human` requires the
   host's independent actual human channel and genuine evidence; AI judgment
   or authentication is not consent. `Wait` follows its actual condition without
   spin/polling. `Terminal` ends the execution; it grants no new Start authority.
4. Submit the generated result or answer to the current issued `submitOperation`
   with its `submitSessionId` and declared `resultType`. Pass only genuinely
   supplied consumed metadata. The common Operation contract owns which routes
   require `resultReference`; do not manufacture a reference, execution evidence,
   Human consent, review acknowledgement or typed success. Missing or blank route
   data and incompatible result types remain ordinary errors.
5. For Goal child work, Submit uses the issued child `submitSessionId`, and its
   actual response ID must agree with that consumed child ID. Keep the outer
   Goal family/ID in the routing file for the next Goal Advance. For ordinary
   work likewise check the actual submission response ID against the issued ID.
6. Display the actual Submit `message`, boolean `accepted` and `outcome`.
   After `accepted=true`, the host may explicitly Advance the retained outer
   session. Rejection, transport failure, bad conversion, uncertain effect,
   incompatible result or ID mismatch must not trigger success-path Advance,
   retry or restart. There is no automatic polling or chaining.

The semantic skill performs only currently issued work and that exact typed
Submit, then returns control to the host. It may read the host-exposed routing
artifact and check the owning outer context; it does not own Start, Advance,
Human admission or the caller loop. Workflow owns mechanical validation,
commands, review admission, effects, commit receipts and recovery under existing
grants. Routing retention cannot substitute for any of those permissions or
evidence. MCP may be available as an existing host entry, but is not required
and does not imply a new wrapper.

## Delivery and evidence boundary

The unaccepted adapter and its exclusive executable specification are removed.
This document and the three semantic skills describe direct native use; they
add no driver, endpoint, serializer, storage engine, provider or server ABI.
Existing native Operation and artifact specifications remain unchanged.

Actual installed command skill proof across independent calls and interleaved
tasks, all three families, current host preparation and a bounded real task
remain required Phase 6 work. The selected lightweight normal-client connection
is separate from that real task completion. Authoring this contract claims no passing
validation, independent review, installation, task, Step or Phase completion.
