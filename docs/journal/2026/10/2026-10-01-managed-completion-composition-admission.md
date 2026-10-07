# Managed completion composition admission

During Phase 2 production-factory inspection, `create_Core` runs before
Component initialization and its later Workflow instance bootstrap. The current
application factory still binds generated placeholder services. ComponentCreate
exposes settings but not its owning Subsystem to external factories, while the
opaque managed binders currently accept a Subsystem. Do not obtain private
authority through reflection, a synthetic initialized Component or a private
application pool. An explicit framework composition seam remains to be connected.

Before that connection, close the existing same-lineage composition admission
gap: WorkflowManagedIssuedWork.bindC validates the captured store/collection
grant, definition catalog and same-store delivery before returning a binding;
WorkflowManagedCompletion.bindC currently omits those checks. Freeze producer
paths `src/main/scala/org/goldenport/cncf/workflow/WorkflowManagedCompletion.scala`
and `src/test/scala/org/goldenport/cncf/workflow/WorkflowManagedIssuedWorkSpec.scala`
in the already admitted CNCF Phase 88 repository. Both were read completely.

Reuse the existing pure captured-grant admission and catalog validation at
completion bind time. Preserve execution-time verified shared resource checks,
opaque binding shape, event policy, short leased transactions, claim authority
and ordinary Operation callback. No new public grant or configuration key.
Add executable admission checks for foreign Component, store and each collection,
invalid definition catalog and unsupported delivery. Binding must not invoke
the events callback or acquire an additional physical resource/lease. Require
valid binding to continue succeeding. Run focused issued-work, participant,
managed UoW and existing completion/host regression suites through the serial
wrapper. This is not production-factory or Phase acceptance evidence.

## Focused producer validation (433)

`SMWF-CNCF-MANAGED-COMPLETION-ADMISSION-DIRECT-20261001-433` passed 54 tests
in four suites on the first attempt; compilation succeeded, SBT/wrapper 0/0 and
serial lock released. `git diff --check` passed. Valid completion still binds;
foreign owner/store/root/successor, duplicate definition catalog and cross-store
delivery are rejected during composition. No events callback was invoked;
the pre-existing managed resource count stayed one and active leases stayed zero.
Existing actual completion, event/rollback, participant and managed UoW coverage
also passed. This is not a new full-suite receipt.

- log: `/tmp/skill.cncf.d/cncf-sbt-647e824203f287ee76bd36c1142d73aba18fbba9770a048fa28646bab500d811-0d471491c711597e1d52728f673f89c1/99817-20260930T215854Z.log`
- summary: `/tmp/skill.cncf.d/cncf-sbt-647e824203f287ee76bd36c1142d73aba18fbba9770a048fa28646bab500d811-0d471491c711597e1d52728f673f89c1/99817-20260930T215854Z.summary.json`

Consumer JAR refresh and application validation must follow before claiming
this updated admission in sm-workflow. The existing 426 artifact remains the
last resolved consumer artifact; do not attribute this source change to it.
The next factory connection should provide an opaque host-owned composition
context through ComponentCreate, with no raw provider/pool or private-member
access. Its precise public seam must be frozen against these lifecycle facts
before implementation. Host selection/invocation/closure authority remain real
dependencies, not permissive defaults supplied by the factory.
