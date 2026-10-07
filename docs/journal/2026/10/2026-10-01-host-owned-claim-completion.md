# Host-owned claim and joint closing completion execution

Status: internal execution implemented and verified; public Operation/CLI assembly remains open.

The preceding turn strengthened active-UoW failure examples. This turn adds
`WorkflowCompletionDataStorePersistence.executeSelectedClosingHostC` inside CNCF,
instead of substituting a managed UoW factory into the legacy persistent runtime.
The legacy runtime's postcommit claim-completion callback and the common
Operation's separate instance append are not used by this internal path.

## Execution contract

The host supplies the selected declarative closing program, exact current
instance and next history entry, expected actual closing-result type, same-store
DataStoreSpace and ordinary event batch/delivery binding. These inputs are not
new public request fields or skill routing choices.

The executor admits the same physical instance/Continuation provider, exact
issued WorkOrder, semantic result/facts/evidence, component owner, canonical
result representation, active current instance, saved suspension boundary and
adjacent next entry before acquiring a canonical CAS claim. After acquiring its
own token, it reconstructs the WorkOrder from the actual claimed Continuation
and compares it with the saved issued order before interpreting the closing
program. The actual closing result must match the host-declared closing type;
that type need not be the semantic submitted result type.

The prepared program/result/local intents flow into the existing joint native
Completion participant and a fresh ManagedCompletionUnitOfWork. Completed
Continuation, instance history, semantic result, actual closing result, admitted
local intents and policy-selected durable events use that one completion commit.
Only successful lifecycle completion returns verified retained completion. No
second append, legacy runtime finalization or postcommit data write occurs.

Failed preparation releases only the acquired token. After a UoW exists, only
explicit Aborted termination permits release; Committed, Indeterminate or
missing termination never does. A release attempt is consumed before its CAS,
so failed/throwing release is not retried. Release failure is retained alongside
the primary failure. Replacement claims cannot be released under the old token.
Control exceptions are rethrown and interruption restored after bounded
pre-native cleanup. A claim acquisition failure/uncertainty returns directly;
without a verified acquired token no cleanup claim is fabricated.

## Executable evidence

Real SQLite/host and actual EventBus tests cover:

- Available -> owned Claimed -> joint Completed, retaining distinct semantic
  and actual closing types before subscriber dispatch;
- exact adjacent history with no separate runtime finalization or append;
- durable/transient original-order delivery and rejection of replay before
  interpreting the program or dispatching again;
- invalid semantic facts and foreign owner rejection before claim/program;
- wrong actual closing type and Failed closing release before native commit;
- replacement token preservation on failed owned-token release;
- ordinary preparation exception and InterruptedException cleanup, preserving
  interrupt status;
- actual subscriber failure after native completion, retaining Completed state
  and actual closing result without claim release or replay.

Focused-399 passed 443 tests/41 suites before the additional owner/control cases.
Focused-400 compiled and ran 444 tests, with one new fixture assertion failure:
recovered DefinitionBinding uses another object identity despite identical
canonical instance content. The assertion was changed to exact canonical JSON,
matching existing production admission, without relaxing product behavior.
Final focused-401 passed **444 executed tests across 41 suites**, zero failures,
aborts, cancellations or ignored; two existing RuntimeContext placeholders are
pending. SBT/wrapper exits 0/0 and lock released; summaries/log failure evidence
were inspected. Shared serialization and normal caches were used.

Final log: `/tmp/skill.cncf.d/cncf-sbt-1f51f266223f226557b60483cb99f8198a632050f5b911a22bf8869a2367dd87-44d1b2f4cc922cc26d73a8220bee71ae/13133-20260930T193035Z.log`.
Summary: adjacent `13133-20260930T193035Z.summary.json`.

SHA-256:

- WorkflowCompletionDataStorePersistence.scala: `8252c5e7d48b3667ce7995e3acdae93bc60aea2156aa8a9ae8720315a67e7f6a`.
- WorkflowCompletionDataStorePersistenceSpec.scala: `aad053a5ccb04557e2a01ba1ae0f6958465c0d8fc1f0c5730a97d47cc603c5f1`.

## Remaining production integration

No public completion Operation selects this executor yet. The next integration
must bind a host-owned completion backend to the admitted selected Action and
actual-result contract; skills/request flags must not select storage routes or
receive tokens. Fresh managed host leases and configured grants remain required.
Native rollback/readback uncertainty and writer competition still need integrated
owned-claim/bus evidence; the existing separate kernel/provider tests are not a
substitute. Default runtime behavior is unchanged.

Refresh the admitted producer SNAPSHOT before consuming the new source in
sm-workflow, then verify actual bootstrap/launcher start/completion/reopen and
server/MCP/management/profile/skill packaging acceptance. No repository-full,
downstream runtime or Phase completion is claimed. All acceptance checkboxes
remain unchanged. No commit, release, publication, push or sandbox relaxation
occurred. The full Phase goal remains active.
