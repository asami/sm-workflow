# GoalPhase managed Start assembly

Continue Phase 2 after 438. Freeze new main source GoalPhaseManagedStartAssembly,
the complete existing GoalPhaseManagedSuspensionOperationSpec, and comment-only
updates to GoalPhaseStartExecution/GoalPhaseStartServiceOperation (all existing
sources read completely before editing). Bind the generated Start Operation in
create_Core using explicit host selection and the same admitted Workflow storage
composition. No authority record is constructed from request input; the host
SelectionPort remains a required dependency.

Validate declaration and generated Workflow identity before storage access.
Delegate create/load/append to canonical managed instance binding, rejecting
configuration drift. No memory runtime/store, raw provider, private pool or
automatic selection. Retain current provider create and CAS/history rules.
Spec executes normal Subsystem dispatch, proves missing/non-human selection
rejects, accepted Start persists one Active entry, identical retry/reopen does
not reset history, and changed payload under the same idempotency key rejects.
Authority port in the spec is explicit test input, not production durable
authority evidence. No claim of atomic create-plus-activation, concurrent Start
retry safety, default factory, complete service, CLI or Phase acceptance.

## Focused consumer evidence (439)

`SMWF-GOAL-MANAGED-START-ASSEMBLY-DIRECT-20261001-439` compiled three main
sources and the modified spec and passed 43 tests in five suites on its first
attempt. SBT/wrapper 0/0; serial lock released and git diff --check passed.
The new example invokes main-code Start assembly inside actual create_Core and
executes the Operation through normal Subsystem dispatch. Missing selection and
Skill-origin selection fail. Explicit host test selection permits Start; the
record is Active at revision 1 with exactly one history entry. Same command
retry returns the same response. Changed Phase payload under the same key fails
without changing the saved instance. After owned-host shutdown, a fresh host
reassembles Start and returns the identical response with identical history.
Removing selection then rejects retry without changing the recovered record.
The existing managed review/completion/replay examples also pass.

- log: `/tmp/skill.cncf.d/cncf-sbt-876a71360572802924ff13aad50a01da5961fc87fe4f3598bd163b0bb1a8b992-be08ff8782491805c4ffecf7e9b20aaf/9615-20260930T221853Z.log`
- summary: `/tmp/skill.cncf.d/cncf-sbt-876a71360572802924ff13aad50a01da5961fc87fe4f3598bd163b0bb1a8b992-be08ff8782491805c4ffecf7e9b20aaf/9615-20260930T221853Z.summary.json`

Start and review are still tested as separate managed routes. Next integrate
managed issue on the exact Start-produced instance through the same Service,
then review completion after reopen. Production selection/invocation/closure
authority stores remain unimplemented and this test port is not their substitute.
Default factory/configuration, repair and real deterministic commands, process-
separated CLI/server/MCP and generic management visibility remain required.
No Phase acceptance checklist or validation/review/commit gate is closed here.
