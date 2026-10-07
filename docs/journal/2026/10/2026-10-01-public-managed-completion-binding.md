# Public Completion Operation and opaque managed host binding

Status: common Operation integration verified with SQLite; sm-workflow production bootstrap/CLI remains open.

The previous turn implemented owned-claim execution. This turn connects it to
the common WorkflowCompletionServiceOperation through an optional, configuration-
time WorkflowManagedCompletion binding. The binding has a private constructor
and its execution entry is package-private. Requests contain no route flag,
claim token, provider handle or DataStoreSpace.

## Implementation

WorkflowManagedCompletion.bindC captures the Subsystem, Component identity,
store configuration, admitted definitions/collections/codecs, completion
Operation, actual closing-result type and trusted event-selection function.
Its call borrows a verified DataStoreSpace under the Subsystem's captured grant,
managed lease and EventStore policy. The canonical adapter is constructed inside
that scope and uses the existing host-owned claim/closing/native UoW executor.
Finalized committed events dispatch through that Subsystem's real EventBus with
the actual Operation ExecutionContext, without another EventStore append.

The common Completion Operation binds this opaque value only with a matching
semantic codec and a closing selector. Managed completion requires an adjacent
selected next instance. Legacy runtime/fresh-UoW/append behavior remains the
unchanged default when no managed binding is supplied. A managed binding may
use ContinuationRuntime.empty: the legacy runtime is not invoked as a fallback.
No skill or request chooses between the paths.

The outer Operation still performs pure selection and ordinary no-effect commit
before its afterCommitResponseC managed completion. It does not interpret closing
effects in that outer UoW; the host executor owns the actual joint native commit.
This does not claim two physical commits for enlisted effects or completion
inside the outer default UoW. Instance revalidation now compares canonical JSON
instead of DefinitionBinding object identity, which is not stable across reads.
The response receives the actual closing result and verified retained instance,
not a separately appended compatibility projection.

The trusted event selection function is a host binding, not a persistence-policy
override or external-command authority. Production application selection and
grants must still be admitted explicitly. The unchanged host policy decides
durable versus transient persistence.

## Executable evidence

The new example invokes the fixture-declared typed Provided Operation through
Subsystem.executeOperationResponse and a registered Component/service. It uses
actual SQLite storage and the Subsystem EventBus. The legacy UoW factory and
separate create/append ports reject if called and record zero calls. The example
checks exact semantic and actual closing results, adjacent history, Completed
claim and one actual subscriber dispatch. Missing JSON rejects before claiming
or interpreting. Replay invokes neither closing nor delivery again.

A second scenario rejects response projection after successful storage/delivery.
It preserves Completed state and both results; replay cannot rerun the closing
program or dispatch. This is response uncertainty, not a rollback or retry grant.
GeneratedWorkflowAbiSpec is included to cover existing legacy Provided API paths.
The new declaration is fixture-owned, not proof of sm-workflow's generated ABI,
launcher bootstrap, server/MCP or a CLI-started instance.

Focused-402 compiled but failed the new public test because its Subsystem lacked
explicit runtime user/security admission. The existing RuntimeBindingAdmissionFixture
was registered only in that test; production security was unchanged. Final
focused-403 passed **461 executed tests in 42 suites**, zero failed/aborted/
canceled/ignored, two existing RuntimeContext placeholders pending. SBT/wrapper
exits 0/0, lock released; persistent summaries and failure diagnostics were read.
This is focused validation, not repository-full or downstream application proof.

Log: `/tmp/skill.cncf.d/cncf-sbt-bcb8e4c5565265e6b799779352460b17b2f3b0545e6b99b46f03c819a1f7183b-8ecef802643410b97aecfc28f177086b/22447-20260930T194439Z.log`.
Summary: adjacent `22447-20260930T194439Z.summary.json`.

SHA-256:

- WorkflowManagedCompletion.scala: `c876cd18c912d4ab8def247d1cf820141025609525b3a44f54848cd7f66cc72e`.
- WorkflowCompletionServiceOperation.scala: `b43980e06cd8f5795f32a51676046975706e469b6a1c93e2420e8b2ab8aded93`.
- WorkflowCompletionDataStorePersistenceSpec.scala: `6113dd9de063164aa389d01cff9951eadfd855b4d6f3e3e62c8a0a155d98943b`.

## Next production work

Refresh the validated CNCF development SNAPSHOT, record resolved consumer artifact
identity, and bind actual GoalPhase completion in the sm-workflow service assembly.
Use canonical per-call instance/issued-work bindings, admitted host declarations
and real application result/Action contracts, not the borrowed test adapters.
Then prove launcher Start -> exact WorkOrder -> completion -> reopen, followed
by shared server/MCP/management/profile and distributable sm-* skill acceptance.
Integrated rollback/provider-readback uncertainty/writer competition and durable
EventStore query/replay remain open; current success cannot close those gates.

All production acceptance checkboxes remain unchanged. No default runtime
replacement, application CLI activation, artifact publication/refresh, commit,
release, push or sandbox relaxation occurred. Goal remains active.
