# Managed GoalPhase Component Factory

Date: 2026-10-01
State: IMPLEMENT; sm-workflow Phase 2

Continue explicitly authorized parent-direct limited recovery. Own the new
GoalPhaseManagedComponentFactory main source, ManagedSuspensionOperationSpec
and this journal. Preserve other changes. No public Operation/ABI/CML, pool,
producer, configuration schema, default launcher factory, commit or acceptance
change. This is a bounded application composition step, not production authority.

Provide a main-code BundleFactory/PrimaryComponentFactory over the existing
four-Operation managed Service assembly. It receives explicit host declaration,
ports and ExecutionContext, constructs SmWorkflowPrimaryComponent and binds the
Service inside create_Core. Do not create an ExecutionContext or physical pool,
invent human selection/association/closure authority, enable MCP before parity
validation, or include semantic provider stubs as production implementations.
Move the normal Start/reopen executable scenarios from their anonymous factory
to this main-code factory. Keep pure preflight assertions before construction.
Run the existing six suites plus ComponentFactorySpec. Default launcher factory
and trusted configuration-to-port resolution remain separate unfinished work.

Attempt 465 failed Compile before tests: the new Primary extends a generated
Factory with a concrete create_Component implementation, requiring an explicit
override modifier. Parent-direct mechanical correction adds that modifier only.
SBT/wrapper exited 1, lock released; the structured summary was read. This
failure supplies no behavior validation or Phase acceptance evidence.

## Observed executable evidence

Attempt `SMWF-MANAGED-COMPONENT-FACTORY-DIRECT-20261001-466` compiled the
new main Factory and changed spec, then passed 54 tests in seven suites, zero
failed/aborted. SBT/wrapper exited 0, lock released. The structured summary was
read at
`/tmp/skill.cncf.d/cncf-sbt-ab33756479a3839d5874d2f5261f94814a832e0fceb808815232d98a7c6044a1-145fb69faa7d34551d09e59683241423/71413-20261001T000458Z.summary.json`;
the adjacent `.log` retains full output. `git diff --check` passed.

The normal Start/public review and both automatic repair/re-review
Given/When/Then scenarios now use the main-code BundleFactory rather than an
anonymous test Primary factory. It builds an actual SmWorkflowPrimaryComponent,
binds only the four managed Service Operations in create_Core, retains empty
mcpReadyServices, and borrows host storage without allocating another pool.
The interrupted-follow-up path still retains revision 5, rejects completion
replay, recovers one revision-6 successor and accepts fresh review at revision 7.
Host selection/association and unavailable closure ports remain explicit test
fixtures, not durable production authorization. The unconfigured default
ComponentFactory boundary test remains green and does not prove runtime wiring.

Validated main source SHA-256:
`58ca093b5063415e3c0962b633de03f327f63d53194d908f9837f61fecc9a6b8`.
Validated spec SHA-256:
`6d794b5c03f8a95d289ff6cd69b69f2e433fb461e74183115eb738a4ef616535`.

The launcher default is intentionally unchanged: trusted configuration-to-port
resolution, durable human selection/association/closure authority, generated
typed public recovery, separate-process CLI/server/MCP parity, managed external
commands and the remaining Phase acceptance/release gates are incomplete.
No publish, commit, full review, release or Phase acceptance was performed.
