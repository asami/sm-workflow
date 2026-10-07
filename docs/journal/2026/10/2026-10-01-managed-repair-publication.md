# Managed repair successor publication

Date: 2026-10-01
Phase: sm-workflow Phase 2
State: IMPLEMENT

Continue the parent-direct limited recovery explicitly requested by the user.
Own GoalPhaseReviewBoundary.scala, new GoalPhaseManagedRepairPublication.scala,
GoalPhaseManagedSuspensionOperationSpec.scala and this journal. Preserve all
coexisting work. No CNCF or Cozy mutation, new provider/pool, schema/ABI change,
public Operation advertising, legacy repair replacement or acceptance change.

The managed publisher binds only captured Component storage authority. On
advance, load original issued review, opaque retained completion and authoritative
instance history. Recover committed repair Intent without fresh review admission.
Project the exact successor from the completion revision and preserve the
original candidate and retained result reference. Bind the declared RepairStep
SPI and bounded repair policy. Publish suspension, Continuation and issued work
through WorkflowManagedIssuedWork.suspendC, never an independent append or
Intent map. For an exact already-committed successor return identical retained
work; reject unrelated instance advancement. A racing loser may return conflict
and retry through advance; never publish another successor.

Extend the three-host executable behavior with explicit adjacent Given/When/Then:
expired retained judgment can publish repair, repeated advance is idempotent,
host reopen recovers the exact successor, and missing review or incompatible
repair policy fails without changing history. Existing query/legacy tests remain.
Focused validation is the six-suite command from invocation 452 through the
shared serial wrapper and the unchanged local CNCF SNAPSHOT. Full acceptance,
repair completion/re-review, public advance wiring and default factory remain
separate unfinished requirements.

## Implementation and focused evidence

Added GoalPhaseManagedRepairPublication with captured Component storage bindings
for instance reads, review work/completion query and repair work publication.
`advanceC` loads committed facts, derives the completion prefix and one deterministic
successor, then uses managed `suspendC`. Exact retries and a fourth reopened host
return the same issued WorkOrder without appending history. Other advancement
is refused. It never calls the legacy repair loop, independent append, claim,
Intent persistence or a fresh review assessment. Post-commit verification
disagreement is classified transaction-indeterminate, not as definite rollback.

GoalPhaseReviewBoundary now shares its pure issue projection while keeping
review and repair completion tuples separately fixed by their named entry points.
No generated ABI, CML, schema or public Service Operation was changed.

Invocation 453 passed 51 tests in six suites. After adding the incompatible
repair-capability refusal assertion and strengthening post-commit diagnostic
classification, final invocation
`SMWF-MANAGED-REPAIR-PUBLICATION-DIRECT-20261001-454` also passed 51 tests in six
suites, zero failures/aborts; SBT exit 0, wrapper exit 0, lock released. Both
used the six-suite command frozen above, sequentially through the serial wrapper.
The final structured summary was read; no live SBT handle remains.

- Final log: `/tmp/skill.cncf.d/cncf-sbt-8fa1e5438a17a39af1c583ab24af46eaa58aabdaaa76012f333ecda065927584-f44c78e3218e6173db287902346025a0/44063-20260930T232604Z.log`
- Final summary: `/tmp/skill.cncf.d/cncf-sbt-8fa1e5438a17a39af1c583ab24af46eaa58aabdaaa76012f333ecda065927584-f44c78e3218e6173db287902346025a0/44063-20260930T232604Z.summary.json`

Source SHA-256 identities captured during and after final validation matched:

| Source | SHA-256 |
| --- | --- |
| GoalPhaseReviewBoundary.scala | `842008f7ab530710bab6c332eb71d5a905fce09398abe8f93fa63ed9f9ac0944` |
| GoalPhaseManagedRepairPublication.scala | `9af794b528f9f68dc1b55f2779c5407cfb6acc8be3e4555c9a8c3a35726075f2` |
| GoalPhaseManagedSuspensionOperationSpec.scala | `89df368fb782ec1c3c35c25d35f62b3284c29b127e7c8771e472154f978d3e0f` |

The locally refreshed CNCF SNAPSHOT remains
`2c4a0f75d1e8ce4241deaa14d13ff5b1bdd03983546a0eb83878ac158332e2b1`;
no producer republish or Cozy/CNCF source mutation occurred. `git diff --check`
passed. Existing legacy repair regression suites passed unchanged.

The executable scenario binds publication inside a fixture Component's
`create_Core`, commits review via normal Subsystem dispatch, shuts down that
host before any repair publication, and exercises recovery after expiry on the
next two hosts. This proves the internal managed recovery/publication slice;
it is not public CLI advance wiring, a separate-process test, a concurrent race
or failure-injection proof, repair completion/re-review or production default
factory configuration. Those remain required, as do independent acceptance
reviews, full validation and Step/Phase commits. No Phase closure is claimed.
