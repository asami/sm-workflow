# Managed GoalPhase Start-to-review path

Continue Phase 2 after 439. Freeze new main GoalPhaseManagedIssueAssembly and
the complete existing GoalPhaseManagedSuspensionOperationSpec, read before edit.
Compose generated requestStepClose in create_Core through the same public
WorkflowComponentStorage seam. Host invocation, review SPI/requirement,
presentation, snapshot/commit ports and closure-authority ports are explicit,
required dependencies. Preserve the existing automatic authority gate for a
pre-reviewed candidate; do not use the ungated closure fallback. No authority
or invocation is derived from semantic request/result bytes.

Use canonical instance SPI calls with exact declaration configuration checks;
ContinuationRuntime.empty plus mandatory managed-issued binding leaves no
legacy suspension/claim implementation. No fallback UoW supplier. Existing
closure authority and external command contracts are not rewritten here.
This is issue assembly, not managed Git closure/recovery acceptance.

Add one executable Start -> managed issue -> host reopen -> typed review result
path via three real Operations in the same Service. The instance must come only
from public Start, not fixture seeding. Test-controlled human selection and
submission association remain explicit evidence limits, not production stores.
Retain exact handle/issued work/Start correlation, revision/history and replay
checks. Snapshot/commit/authority callbacks must not run for a missing-review
issue or its completion WAIT. No CLI/server, default factory, repair Operation,
durable authority, Git command or Phase acceptance is claimed.

## Focused consumer evidence (440)

`SMWF-GOAL-MANAGED-START-REVIEW-PATH-DIRECT-20261001-440` compiled new main
issue assembly and the modified spec, then passed 44 tests in five suites on its
first attempt. SBT/wrapper 0/0, serial lock released; git diff --check passed.
The new example binds three application main-code Operations inside real
create_Core and registers one GoalPhaseService. Close without a host association
rejects. Public Start creates the sole instance; its Handle is explicitly
associated by the test host, then close atomically retains review work at
revision 2. The instance retains the Start correlation and two history entries.
Duplicate close fails. A fresh host reconstructs the same Service over the same
SQLite file and loads the exact issued work. Review completion appends revision
3, clears suspension and retains Start correlation. Replay rejects without
changing the record. Snapshot, commit, authority and Decision callbacks are
never invoked on this missing-review issue/completion-WAIT path. Missing issue
authority ports are rejected during assembly even though the tested candidate
does not need closure yet.

- log: `/tmp/skill.cncf.d/cncf-sbt-683d1011436e52900dca8cb40bd3ba69c8b501e1a2275b394152990841adb1db-3094ae247f0d465bf9f06f7f2ea06aaa/16171-20260930T223321Z.log`
- summary: `/tmp/skill.cncf.d/cncf-sbt-683d1011436e52900dca8cb40bd3ba69c8b501e1a2275b394152990841adb1db-3094ae247f0d465bf9f06f7f2ea06aaa/16171-20260930T223321Z.summary.json`

Same-Service assembly is still composed by the test factory; the default
application factory is not connected and host selection/association are test
inputs, not durable production authority. Next move this composition to main
service assembly and connect configuration/host authority sources without
memory fallback. Review currently returns WAIT; deterministic authorized
closure/receipt recovery, repair, complete generated service, CLI/server/MCP,
generic management visibility and Phase assurance/commit gates remain open.
