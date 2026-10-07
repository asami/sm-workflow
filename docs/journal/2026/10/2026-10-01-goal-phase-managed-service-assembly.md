# Managed GoalPhase review-slice Service assembly

Continue Phase 2 after 440. Freeze new main GoalPhaseManagedServiceAssembly and
the complete existing GoalPhaseManagedSuspensionOperationSpec (whole-file read
retained from the prior slice; current composition inspected before editing).
Move the three-Operation composition out of the test factory into application
main code. Bind a single storage declaration, explicit host selection/issue
ports, producer ABI, event callback and one clock used by issue and completion.
Reject incomplete aggregate dependencies before assembling sub-bindings.

Name the bounded entry bindReviewSliceC: it returns startGoalPhase,
requestStepClose and submitStepReview only, not a pretend complete generated
service. Verify those Operations are declared in the generated ABI and return
exact ordered bindings. submitStepRepair is not advertised without a managed
repair implementation. This is implementation sequencing, not a removal of
repair from Phase acceptance. Preserve each operation's admitted storage and
host authority semantics; no fallback or test stores in the application source.

Use the main Service assembly in the existing public Start -> issue -> reopen ->
completion executable example. Add aggregate incomplete-port checks; retain
all identity/history/replay/callback checks. No default factory configuration,
production authority store, CLI/server, real command or Phase closure claim.

## Focused consumer evidence (441)

`SMWF-GOAL-MANAGED-SERVICE-ASSEMBLY-DIRECT-20261001-441` compiled the new
main source and updated spec, then passed 44 tests in five suites on its first
attempt. SBT/wrapper 0/0; serial lock released and git diff --check passed.
The actual factory now calls the application main Service assembly and uses
its ServiceDefinition directly. Missing aggregate selection, event callback or
issue authority rejects before successful composition. Public Start -> managed
issue -> fresh-host Service reconstruction -> typed review completion remains
green with exact Start identity/correlation/history and replay rejection. The
three bindings share a single declaration and the issue clock is passed to
completion; no independent completion clock is selected by the factory.

- log: `/tmp/skill.cncf.d/cncf-sbt-fd491da42019e21b29a9cbc186259ef18af0f82cb1b7c04c03efe886b77c6b04-788756797c0178f0445d90bb34baaa13/17918-20260930T223613Z.log`
- summary: `/tmp/skill.cncf.d/cncf-sbt-fd491da42019e21b29a9cbc186259ef18af0f82cb1b7c04c03efe886b77c6b04-788756797c0178f0445d90bb34baaa13/17918-20260930T223613Z.summary.json`

This closes the test-only Service-composition gap, not host authority storage
or default SmWorkflowPrimaryFactory configuration. The result advertises no
submitStepRepair implementation, and accepted review still yields WAIT rather
than terminal closure. Next connect the managed repair/completion lifecycle and
explicit durable host ports; full Service/command/CLI/server/MCP/management and
Phase assurance/commit requirements remain unchanged and open.
