# Managed follow-up interruption and public recovery gap

Date: 2026-10-01
State: IMPLEMENT; sm-workflow Phase 2

Parent-direct limited recovery continues. Own only
GoalPhaseManagedSuspensionOperationSpec and this journal. No source/ABI/CML,
public Operation, producer, pool, factory, commit or acceptance mutation.

Current CNCF builtin WorkflowComponent.WorkflowService declares five read
methods (definitions, definition detail, instances, instance detail and history).
Its DefaultWorkflowService queries the legacy WorkflowEngine. There is no
mutating recovery Operation in that inspected contract. sm-workflow CML declares
four GoalPhaseService Operations (start, request close, submit review and submit
repair); none is a generic advance/recovery command. Private managed publication
methods are not a launcher-facing recovery API. A future public recovery entry
must be explicitly typed and declared, with authorized instance/continuation and
expected-revision correlation; neither query-side mutation nor completion replay
is an acceptable substitute. Its contract remains to be frozen before edits.

Extend the normal Start/four-Operation repair scenario with a second independent
run: fail authoritative artifact lookup after the ordinary repair result has
committed, assert revision 5 with no suspension, reject completion replay,
reopen the host, and use the existing private managed publication binding to
recover exactly one re-review successor. This proves the retained failure gap
under normal dispatch, not public CLI recovery. Retain adjacent Given/When/Then,
original success scenario and six-suite accumulator. No full gate substitution.

## Observed executable evidence

Attempt `SMWF-MANAGED-FOLLOWUP-INTERRUPTION-DIRECT-20261001-464` compiled
the changed spec and passed 53 tests in six suites, zero failed/aborted. SBT
and wrapper exited 0, lock released. The structured summary was read:
`/tmp/skill.cncf.d/cncf-sbt-b0b590698fdd01b4ca3e8cae14295d87c65a31fc883ada5a976693b4ed697953-dc659dc6c19f30adde17e10dfb065518/67678-20261001T000015Z.summary.json`.
The adjacent `.log` retains full output. `git diff --check` passed.

Both normal-dispatch runs start from explicit test-host human selection and
reopen the same SQLite database between semantic turns. In the interrupted run,
unavailable authoritative artifact bytes make submitStepRepair return Failure
after its managed completion has committed revision 5. The exact repair-result
causation remains, suspension is empty and the deterministic next review issued
work is absent. Replaying submitStepRepair fails without a second artifact read
or history change. A new host binds the existing private managed publisher and
issues revision 6 from the retained completion. Retrying that publication with
unavailable bytes returns the identical issued work without another lookup.
Fresh review then commits revision 7; replay is refused. No unauthorized Git,
closure authorization or Decision call occurs.

This is application-level failure-gap evidence over the existing managed
provider, not a process-kill/concurrency test or public recovery implementation.
Next runtime work must freeze a declared typed recovery contract and connect
production host authority/configuration, not expose private methods through an
undeclared CLI grammar. Full Phase scope and all acceptance/release gates remain
open; no commit, publication or acceptance was performed.
