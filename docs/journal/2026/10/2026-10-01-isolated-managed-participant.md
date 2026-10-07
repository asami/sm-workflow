# Isolated managed participant conformance resumed

Status: source-level lifecycle conformance verified; production activation open.

The prior explanation-only goal turn was no progress. The approval-based stop
was a parent classification error, not a missing user action. This continuation
used the already authorized parent-direct recovery with the mandatory shared
SBT wrapper. No command runner, extra approval, sandbox change or alternative
write path was introduced. The rejected combined central UoW patch was not
repeated.

## Current implementation and evidence boundary

In CNCF Phase 88, the private `ManagedCompletionParticipant` binds one original
thread and exact TransactionContext object, consumes the native attempt before
calling it, and preserves Verified / Rejected / Indeterminate independently
from local lifecycle success. Null, malformed or throwing native outcomes are
uncertain, never evidence of rollback. Terminal state precedes callbacks,
cleanup, delivery and discard, preventing reentrancy or replay.

Delivery is admitted only after frozen callbacks and cleanup succeed. Rejection,
uncertainty or a lifecycle failure suppresses delivery. Discard is memory-only;
it cannot compensate storage, release a claim or schedule replay. Interrupt and
control exceptions preserve their original throwable and interruption semantics
after local cleanup/discard. A later local failure does not downgrade verified
storage into an abort.

The executable spec has nine scenario groups, including malformed native
returns, callback/cleanup failure, delivery/discard failure, wrong transaction,
wrong thread, reentrancy, precommit abort, and control exceptions at native,
callback, cleanup, delivery and discard boundaries. Its native provider and
ordered batch are trusted test doubles. This proves coordinator conformance,
NOT actual SQLite enlistment, production event delivery or P2-U05 acceptance.

Source SHA-256 at focused-379:

- `src/main/scala/org/goldenport/cncf/workflow/ManagedCompletionParticipant.scala`:
  `3c8b231f8668ae9027e578e71ff108e3fc10a2bf0efce3d27f8f176408d537f9`.
- `src/test/scala/org/goldenport/cncf/workflow/ManagedCompletionParticipantSpec.scala`:
  `794cc3e1060974136e91eba7760b702678d5ca1f14e64dd13887bb054406a62a`.

## Serialized validation

376 passed 292 tests in 31 suites before the final control-exception cases.
377 stopped in generation because Cozy's development runtime classpath was
stale; it did not compile or execute the changed spec. This was a dependency
prerequisite, not a participant failure or new human approval boundary.

378 (`SMWF-COZY-CLASSPATH-RECOVERY-DIRECT-20261001-378`) ran exactly
`["--batch", "compile", "cozyExportRuntimeClasspath"]` in
`/Users/asami/src/dev2025/cozy`. Compilation and export succeeded. Only the
SBT-only dependency scope was used; no Cozy source was edited.

Evidence: `/tmp/skill.cncf.d/cncf-sbt-d16545f0a456d32c63ef6339652dfa47662642d209ebd2a8399df786ee07c32a-b3cd6e04e15886d00fe0d6e215b80af3/60146-20260930T171125Z.log`
and adjacent `60146-20260930T171125Z.summary.json`.

379 (`SMWF-CNCF-ISOLATED-PARTICIPANT-RESUME-DIRECT-20261001-379`) reran the
same exact focused-376 31-suite argument vector in
`/Users/asami/src/dev2025/cloud-native-component-framework`. It compiled the
changed production/spec files and passed **294/294 tests, 31 suites**, zero
failed, aborted, canceled, ignored or pending. Log and structured summary were
read; both invocations had SBT/wrapper exits 0/0 and `lock=released`.

Evidence: `/tmp/skill.cncf.d/cncf-sbt-e06271d76c149450779c5345299d2d57ad7101c066d02e3f07f5a35caa0f4445-6a0ba7d2e9c738e4ca82d44b7e7e1ee2/60782-20260930T171208Z.log`
and adjacent `60782-20260930T171208Z.summary.json`. The summary retains the exact
testOnly suite vector. Existing UnitOfWork resource, postcommit and 2PC NOOP
regressions are included; repository-full validation remains the release gate.

## Remaining production boundary

Current Git diff checks show no changes to central UnitOfWork.scala or
UnitOfWorkResource.scala. The participant is not installed in EventEngine or
public Operations. Bind actual prepared host storage and active closing/data
intents in the same physical provider domain next; reject unenlisted effects.
Only actual native conformance can justify a bounded central managed opt-in
change. That change must preserve compatibility and distinguish uncertain
completion from abort so existing failed-resume claim-release handling cannot
release an uncertain claim.

Actual ordered mixed-event delivery, rollback/competition, committed readback
uncertainty, affected RuntimeContext/continuation consumer checks, application
assembly, CLI/MCP, restart, management, packaging and sm-* skill distribution
remain open. S12/P2-U15/P2-U16/P2-U05 remain unchecked. No acceptance, commit,
release, publication, push or goal completion is asserted.
