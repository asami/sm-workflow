# GoalPhase managed review through Subsystem dispatch

Continue the admitted Phase 2 application completion boundary after focused
validation 431. Freeze the complete application test program
`src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseManagedSuspensionOperationSpec.scala`.
It was read completely before editing. No production contract or framework
source change is selected in this slice.

Issue review work using the existing managed Operation, shut down its host,
and reopen the same database. Bind the generated `submitStepReview` Operation
to opaque managed completion and real public instance/issued-work bindings.
Register the test Component with the Subsystem and execute the public Operation
through Subsystem dispatch, including its after-commit response callback.
Explicit packaged runtime-admission test support is permitted only in this spec;
this is not evidence of production launcher configuration discovery.

Require a typed accepted review to clear the suspension and append exactly one
history entry. Reject a mismatched result before mutation and reject the accepted
result's replay both immediately and after another fresh-host reopen. Independent
legacy append rejects and the legacy UoW supplier throws. Event-free completion
must not require cursor installation. No Git, remote publication, acceptance
commit, or Phase closure is performed. Do not claim persisted result-payload
readback without an actual public read API assertion. Production factory wiring,
deterministic Step closure and CLI/server restart remain separate obligations.

## Focused execution evidence (432)

`SMWF-GOAL-MANAGED-REVIEW-DISPATCH-DIRECT-20261001-432` compiled the modified
spec and passed 41 tests across five suites on its first execution. SBT/wrapper
exit 0/0; the serial wrapper reported the lock released. `git diff --check`
also passed. Existing managed binding, review snapshot, review boundary and
repair-loop suites remained green.

The new executable example issues work without an EventStore cursor, closes
the host and uses a new host over the same SQLite file. Its public Subsystem
dispatch rejects a review of a different candidate revision without changing
the suspended root. The accepted typed review returns WAIT only after the
managed completion callback appends revision 3, clears suspension, and retains
the review-result causation. Identical replay fails without changing that root.
A third fresh host recovers the exact reviewed root and original issued work;
replay still fails. The failing legacy append and throwing legacy UoW supplier
remain in the fixture throughout. No application raw provider, SQL access or
legacy runtime claim implements the successful completion.

- log: `/tmp/skill.cncf.d/cncf-sbt-94794b3f5d295e747a94cf95efe618c445933dc3ba61e16ff3af012586dbffe0-ab62a40ed76dc1eb47adba71f3569b83/98017-20260930T215538Z.log`
- summary: `/tmp/skill.cncf.d/cncf-sbt-94794b3f5d295e747a94cf95efe618c445933dc3ba61e16ff3af012586dbffe0-ab62a40ed76dc1eb47adba71f3569b83/98017-20260930T215538Z.summary.json`

This proves managed review completion through normal Subsystem Operation
dispatch under explicit test admission, not production ComponentFactory wiring,
launcher discovery, separate-process restart, result-payload readback, actual AI
execution, or deterministic Git closure. These and the Phase assurance gates
remain open; no Phase status or acceptance checklist is marked complete.
