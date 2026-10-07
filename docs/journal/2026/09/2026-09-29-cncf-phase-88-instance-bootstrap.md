# CNCF Phase 88 S2: configured Workflow instance bootstrap

Date: 2026-09-29
Status: upstream bootstrap slice validated; Phase 2 / Phase 88 remain in progress

## Implemented boundary

Within the user-authorized CNCF Phase 88 scope, added
WorkflowInstancePersistenceSource and WorkflowInstancePersistenceBinding, wired
through Component.Factory and normal ComponentFactory bootstrap. The
provider-neutral create/load/append SPI and S1 codec/provider remain unchanged.
Component receives a proven binding only after declaration and durable provider
admission. This is a host composition API, not a new public Operation or CLI.

Source selection follows the existing Continuation bootstrap rule: a Component
source takes precedence over its Factory, including an explicit None. Legacy
components without a declaration remain opt-out. Required persistence with
missing durable storage, malformed policy/catalog/collection, or noncanonical
store identity rejects; no implicit memory provider is introduced.

Canonical managed SqlDataStore views are lease-bound. Retaining the view created
at bootstrap would leave later requests holding an expired lease. The immutable
binding instead retains admitted declaration/environment/owner and resolves a
fresh managed view and S1 provider inside each owning-Subsystem lease. Each call
receives its current ExecutionContext, leaves caller datastore scope unchanged,
and shares the SystemNode-owned physical resource. It neither closes that
resource nor grants command/Operation authority.

## Executable evidence

ComponentFactoryWorkflowInstancePersistenceBootstrapSpec now has eight examples:
legacy opt-out; fresh-context Factory binding; Component source precedence;
explicit Component opt-out; missing/memory-only storage rejection; malformed
declaration rejection before resource creation; two concurrent expected-revision
writers through one binding; and owning-Subsystem shutdown/reopen with a
separately admitted definition catalog and stale-revision rejection.

SBT used registered cncf_command_runner, gpt-5.6-luna / medium, through the typed
receipt-first route and shared serial wrapper. All attempts terminated and
released the lock. No source edits occurred while an invocation was live.

- 236: 15/16 focused examples succeeded; a negative fixture incorrectly
  constructed an empty CollectionId before reaching bootstrap. Its type
  assertion failed; repaired the fixture to supply a null declaration field.
- 237: all 16 focused examples succeeded (7 bootstrap, 2 existing Continuation
  bootstrap, 7 S1 provider). Receipt SHA-256:
  `687fff418cddc8a86b237ab453c4b205e5111346e5373a8dbbe41f608063cfc3`.
- 238: after adding the eighth bootstrap example for shared-binding concurrent
  CAS, component/Workflow/datastore regression succeeded: 109 suites, 876
  succeeded, 0 failed, 7 canceled, 3 pending. The seven canceled cases require
  opt-in live MySQL. Pending examples are two existing ClientComponentSpec
  HTTP examples and one existing SqliteDataStoreSpec column example.
  Canceled/pending cases are not executed acceptance.

Final logical command:
`sbt --batch "testOnly org.goldenport.cncf.component.* org.goldenport.cncf.workflow.* org.goldenport.cncf.datastore.*"`.

Final registered runner: `/root/sbt_receipt_c06868b76909f4f5847b`.
Receipt SHA-256:
`036b33d70962b6355a522e5d6d2265e0cc58a24368d3fa765ed404a55613263e`.
Validated complete-tree SHA-256:
`dba72d076bc2dd8436a69004821f7fffa8c2e72dc4a5fdcf1e2e034db677cd6c`.

Receipt:
`/tmp/skill.cncf.d/cncf-command-execution-2b83880b619b73d4d9a9b74d5ecebd02d556375857ab3b97cfd2f3dc2854a56f-e2a52657491e8d1228bd1ebf1025867d/command-execution-sha256-036b33d70962b6355a522e5d6d2265e0cc58a24368d3fa765ed404a55613263e`.

Log:
`/tmp/skill.cncf.d/cncf-sbt-002d3d1505d8c0b0c40e968964df6ab9c3f4cada5ea269904178e1ad9ec10ea9-4e28eef63a9cc3785a75815ec77d8e6f/14927-20260929T024652Z.log`.

The parent verified the receipt, terminal marker and source identities. S2
source digests are recorded in the corresponding CNCF journal; S1 source
digests and the other-owned sm-split-phase definition remain unchanged.
Following journal/checklist/Phase edits are documentation-only and not part of
the captured validation tree. No whole-CNCF full-test or commit acceptance claim.

## Next boundary

Next is joint instance/Continuation/issued-work/claim/evidence recovery. Existing
WorkflowInstanceAtomicTransitionV1 admission and AtomicWorkflowSuspensionEngine
describe a boundary; an interface alone is not evidence of shared-store atomic
commit/rollback. Separate persistence calls must not be labeled atomic.

S2 shutdown/reopen recreates canonical owners in one test process, not a
separate-process launcher restart. Migration/retention/delivery policy names
are validated identities, not newly implemented policy execution. No production
GoalPhase service, server/MCP, resident-reference refresh, or Phase 87 visibility
was completed. No downstream artifact refresh or changed-CNCF-byte consumption,
publishLocal, skill deployment, commit, push or remote publication occurred.

Public bindings remain sm-goal-phase / GoalPhaseWorkflow, sm-split-phase /
SplitPhaseWorkflow and sm-repository-sync / RepositorySyncWorkflow. Skills
return issued semantic results only; legacy cncf-* operation remains independent.

See [Phase 2](../../../phase/phase-2.md),
[checklist](../../../phase/phase-2-executable-specification-checklist.md) and
[entry inventory](../../../notes/phase-2-runtime-entry-inventory.md).
