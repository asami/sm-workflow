# Managed repair Intent recovery slice

Date: 2026-10-01
Phase: sm-workflow Phase 2
State: IMPLEMENT

Continue after focused consumer 451. Retain the parent-direct execution recovery
and existing repository scope. Own only GoalPhaseReviewResultAdapter.scala,
GoalPhaseRepairCandidateBoundary.scala, GoalPhaseManagedSuspensionOperationSpec.scala
and this journal. Their current complete source was read before editing. No CNCF
or Cozy source changes, new pool, direct provider access, schema change, CLI,
Operation advertising, commit or acceptance change is part of this slice.

Separate fresh judgment admission from committed recovery. The new recovery
entry accepts only the framework's opaque Retained witness, the exact issued
review and current instance history. Verify generated workflow identity/version,
review Operation/capability/input/result, producer binding and repair alternative,
candidate/evidence/result references, exact typed closing result and the adjacent
committed review history entry. Require the same instance Handle. Do not call
fresh assessment, evaluate wall-clock freshness, infer a past admission time or
construct an Assessed value that claims current evidence freshness.

Keep historical admission time optional on Intent. Fresh admission records its
actual supplied time; recovered Intent leaves it absent and records only the
explicit observation time plus its retained-completion witness. Candidate-byte
preparation orders observations against that observation time and continues to
evaluate evidence at its own current time. Record producer revalidation with
the explicit descriptive freshness facet committed-admission, not current;
this is correlation of already committed facts, not renewed semantic approval.

Extend the existing three-host executable scenario to recover Intent after
expiry and prepare a fresh full-review candidate from authoritative bytes.
Retain stale evidence and require a new judgment. Reject absent witness,
incompatible current history, changed input and changed issued headers without
side effects. Preserve all prior query/closing/history/replay checks and adjacent
Given/When/Then clauses. Validate the six focused consumer suites (the preceding
five plus GoalPhaseRepairCandidateBoundarySpec) through the shared serial wrapper
and unchanged locally refreshed CNCF SNAPSHOT. No full validation substitution.

This is the durable prerequisite for managed repair publication. The legacy
repair loop is not migrated by this slice; atomic successor publication,
advance recovery, complete Service/default factory/host authority and Phase
assurance remain required.

## Focused validation evidence

Invocation `SMWF-MANAGED-REPAIR-INTENT-RECOVERY-DIRECT-20261001-452` completed
through the shared SBT serial wrapper in this repository. The persistent
invocation summary reports 51 successful tests in six completed suites, zero
failed or aborted suites, SBT exit 0, wrapper exit 0 and the shared lock released.
The command selected GoalPhaseManagedSuspensionOperationSpec,
GoalPhaseManagedCompletionBindingSpec, GoalPhaseReviewSnapshotSpec,
GoalPhaseReviewBoundarySpec, GoalPhaseRepairLoopSpec and
GoalPhaseRepairCandidateBoundarySpec with `--batch testOnly`.

Persistent evidence:

- Log: `/tmp/skill.cncf.d/cncf-sbt-00bc32a0ee575113b758e65b430b818e5a08a8381a46eea2a1914628bbea40c7-76c3be0e310a26300ac52ff184a70446/36523-20260930T231013Z.log`
- Summary: `/tmp/skill.cncf.d/cncf-sbt-00bc32a0ee575113b758e65b430b818e5a08a8381a46eea2a1914628bbea40c7-76c3be0e310a26300ac52ff184a70446/36523-20260930T231013Z.summary.json`

The summary was read again on continuation, rather than rerunning successful
validation to recover its result. Current source byte identities were captured:

| Input | SHA-256 |
| --- | --- |
| GoalPhaseReviewResultAdapter.scala | `0a77afe7011fce102d604e43bc5ac7b461a5454acdc0642760b6f593879b5bc1` |
| GoalPhaseRepairCandidateBoundary.scala | `0eaccadd8e39f550147d7752208915e648017dd3c28c38f51541c912854a9446` |
| GoalPhaseManagedSuspensionOperationSpec.scala | `152f82276dc6629e3da940e099d78bf0d0f8d6619ccf7c7566cb6cac5b61c4a7` |
| Locally refreshed goldenport-cncf_3 0.5.3-SNAPSHOT JAR | `2c4a0f75d1e8ce4241deaa14d13ff5b1bdd03983546a0eb83878ac158332e2b1` |

The dependency JAR identity still matches the recorded refresh 448. No new
producer publication or SBT process was started by this evidence-intake turn.
`git diff --check` also exited 0; existing untracked implementation and unrelated
dirty planning records remain preserved.

This is focused executable evidence for retained repair Intent recovery, not a
Step acceptance review, full Phase validation, acceptance commit or release.
Next required work is managed successor repair publication: instance suspension,
Continuation and issued WorkOrder must commit together, and `advance` must
recover the interval after review completion without relying on an in-memory
Intent map or a completion callback.

## Next integration edge confirmed from current source

`GoalPhaseRepairLoop.publishC` still performs `IntentPort.saveC`, instance append,
legacy UnitOfWork commit, readback, runtime claim and issued-work publication as
separate prerequisites. A failure after instance append can therefore leave
no jointly published successor WorkOrder. Do not label that route managed or
reuse its independent append in the managed successor implementation.

`GoalPhaseManagedIssueAssembly` already binds an opaque Component storage view
and `WorkflowManagedIssuedWork` for `submitStepReview`, but does not bind a repair
publication route. Its legacy UnitOfWork supplier deliberately throws. The next
slice must bind repair publication for `submitStepRepair` through the same
authorized Component storage boundary, deriving the original request from the
retained completion witness rather than introducing a serialized Intent store.
Completion callbacks may request that transition, but durable `advance` recovery
must be able to perform it independently. Existing legacy repair fixtures remain
compatibility evidence only until that managed route is wired and exercised.
