# GoalPhase managed completion application binding

## Frozen application frontier

Continue sm-workflow Phase 2. Refresh-420/consumer-421 resolved the corrected
CNCF SNAPSHOT (`a5677a49439bda7eba06d50cbc2bc80e4c41fc854589227b2a6ecee23aa9c343`)
and passed 53 consumer tests in nine suites. The current application completion
binder does not pass the framework's existing opaque `WorkflowManagedCompletion`
dependency. Its whole source was read before the following edit.

Target programs: existing `GoalPhaseReviewCompletionOperation.scala` and new
`GoalPhaseManagedCompletionBindingSpec.scala` under the application admission
package. Add explicit host composition for a required managed dependency,
forward it to the canonical generic completion binder, and use
`ContinuationRuntime.empty` on that path so no legacy claim/resume is selected.
Keep legacy entry points and the same generated Operation, typed review
assessment, selected closing program and deterministic response projection.
Validate managed dependency, optional closure ports and repair-loop shape before
publishing the binding. Do not interpret SQL or duplicate the joint transaction.

The first new executable spec is binding admission/conformance: construct the
opaque managed dependency using public framework host APIs, reject its absence
or result-type mismatch, and preserve generated Operation request rejection.
It is not end-to-end managed completion evidence. Actual application-issued
suspension and issued-work access still require an opaque managed host binding:
the public instance binding supplies per-call create/load/append, but the issued
adapter currently requires a borrowed raw store and the Subsystem verified-space
entry is internal. Application tests must not enter a CNCF package or create a
private SQLite provider to bypass this gap. A generic producer-side host binding
is the next admitted CNCF Phase 88 dependency, followed by real GoalPhase
Operation completion/reopen. No Phase completion or production promotion here.

## Focused fixture repair (422)

Invocation `SMWF-GOAL-MANAGED-BINDING-DIRECT-20261001-422` compiled the
application changes but aborted the new suite at fixture construction:
`Event persistence policy requires a revision and unique explicit event-type rules`.
The existing six suites passed all 45 tests; the four new examples did not run.
SBT/wrapper exited 1/1 and released the serial lock.

Freeze blocker `SMWF-MANAGED-BINDING-FIXTURE-EMPTY-POLICY` as an orchestrated
M1 fixture repair: only `GoalPhaseManagedCompletionBindingSpec.scala`, replacing
its empty rule vector with one explicit transient event-type rule. Preserve
the production policy contract, assertions, binding semantics and repository
boundary. Re-run the same seven focused suites; full validation remains pending
at the Phase release gate. This is pre-review repair attempt 1 for this blocker,
not a Phase closure-repair cycle or acceptance.

## Focused result (423)

Invocation `SMWF-GOAL-MANAGED-BINDING-DIRECT-20261001-423` re-ran the same
seven suites after that one fixture correction. Test compilation succeeded;
49 tests passed, seven suites completed, none aborted. SBT/wrapper exited 0/0
with the serial lock released. `git diff --check` passed before validation.

Evidence:
- log: `/tmp/skill.cncf.d/cncf-sbt-e66f8fe4b9fd4f2c6c50803bba85f21a57a9a1cd7ac938154750d7bd8f6d28ef-3544c8dea989e0453249ec6638d1a4f0/84873-20260930T213056Z.log`
- summary: `/tmp/skill.cncf.d/cncf-sbt-e66f8fe4b9fd4f2c6c50803bba85f21a57a9a1cd7ac938154750d7bd8f6d28ef-3544c8dea989e0453249ec6638d1a4f0/84873-20260930T213056Z.summary.json`

The fixture blocker is resolved. This proves managed binding admission and
existing GoalPhase regression coverage, not actual managed suspension,
completion/reopen or deployed CLI/server/MCP assembly. The next dependency
remains the generic opaque managed issued-work/suspension host binding,
followed by the real application vertical slice. Full validation and independent
acceptance remain pending at the outer Phase gates. No commit or publication.
