# RepositorySync formal failed-check continuation

`describeRepositorySyncValidationContinuation --sessionId` supports the explicit
`sm-workflow.repository-sync-formal-failure-continuation.v1` policy for a genuine
retained failed product check on a Purpose candidate or a pending merge. The
existing resultless nonmerge v1 contract remains unchanged. This policy does not
introduce a nonmerge formal-failure FIX lifecycle.

The target retains the original check, full diagnostic receipt, successful
ordered prefix, intent/result and candidate references/revisions, native
frontier, selected validation policy and fixed Purpose workspace when applicable.
The original native rows and candidate remain immutable. Structural failure,
missing/resultless output, UNKNOWN termination, incomplete captures, changed
selection, candidate or source lineage are ineligible.

The target also binds the current ordinary resolved process-execution plan from
the call's ExecutionContext. CNCF interprets and executes that plan. The target
does not reconstruct a historical environment or claim that a configuration
difference was proven. No executable, command vector, language interpreter or
untyped process fallback is introduced.

The public Record projection carries each opaque `processPlan` object as its
complete JSON text string. This includes plans inside nested prior host
directions. Record field-name formatting and public schema-name normalization
therefore cannot change host-map keys such as `JAVA_HOME`, `HOME`, `PATH` or
mixed-case and punctuation keys. The text preserves all original keys, explicit
nulls, arrays, objects and values; sm-workflow does not interpret or normalize
the CNCF declaration inside it.

Target decoding restores those strings to their exact JSON objects before the
existing domain decoder, target validation and canonical equality check.
Malformed JSON text, scalar/array/null plans, null target fields and extra
target fields refuse. Internal encoding and historical stored object-shaped
plans remain unchanged and continue to decode directly. No target-equality
waiver or plan normalization is applied. An old unadopted public description
whose host keys were already changed by Record formatting must be described
again; its original arbitrary key spelling cannot be recovered from that
mangled object. This adds no operation or compatibility alias.

The choices are `AuthorizeNewValidationAfterHostCorrection` and `Defer`. The
existing public evidence-preparation operation binds the full target, Component,
participant, reference, choice, ordinary nonempty rationale and both lifecycle
assertions under `repository-sync-validation-continuation` evidence. If a repair
WorkOrder is pending, it must be actual, available, unclaimed and incomplete;
the adopting answer requires `priorExecutionEnded=true` and
`acceptOriginalUncertainty=true`. Those assertions concern the prior semantic
worker and its UNKNOWN external effects. The check itself remains formally
failed. Without a pending issue, both assertions are false and not applicable.
Absence of a claim is never evidence that a worker had no effects.

Adoption supersedes the exact pending native WorkOrder through
WorkflowManagedWorkOrderSupersession, or appends a mechanical direction when no
issue exists. It records `FormalFailureContinuationAdmitted` without checks,
staging, candidate changes, commits or publication. An identical repeat at that
frontier is observable; stale or contradictory repeats refuse. Defer is passive.

A separate `advanceRepositorySync --sessionId` authenticates retained direction,
source lineage, current candidate, host plan and selected policy. Purpose
progress retains the same repair attempt, group cursor, slot, candidate and
commits, with a separate `-host-<frontier-revision>` attempt suffix. Pending merges
receive a `formal-failure-<frontier-revision>` namespace with an authenticated
link to the unchanged original candidate. Only the successful prefix is seeded;
the old failed row is excluded from the new attempt and stays readable.

Normal Advance saves the next intent before ordinary typed SBT execution. A
resultless new intent is not replayed. A new genuine failure returns to ordinary
FIX handling; later checks, assessment, review and convergence retain their
normal owners. A later host correction may describe that new failed result
without resetting repair counters or imposing a host-attempt cap.

Authored evidence:

- `RepositorySyncFormalFailureContinuationSpec`: full diagnostics, Purpose/merge
  targets, successful prefixes, strict discrimination and exact host bindings;
  the public Record serialization/normalization/decoding path preserves opaque
  host plans and nested prior directions, with malformed-plan refusals.
- `RepositorySyncPurposeRuntimeSpec`: independent attempt keys, historical
  defaults and unchanged repair/cursor/slot/candidate/commit accounting.
- `NativeRepositoryFormalFailureContinuationCliSpec`: tiny real product
  repositories, actual SBT launcher failure under an isolated invalid JVM launch option,
  public describe/preparation/adoption, host drift refusal, old Submit rejection,
  separate Advance and successful-prefix preservation on Purpose and merge routes.

These are authored specifications. Generation, compilation and execution results
belong to the parent verification workflow.

The CLI suite also supports retained installed upgrade episodes. Select
`smworkflow.spec.formalFailureEpisodeStage=before` and an existing absolute
`smworkflow.spec.formalFailureEpisodeDirectory`, together with the old
`smworkflow.spec.installedCommand`. This creates the tiny Purpose and merge
failures and saves `purpose.json` and `merge.json` containing only fixture
root/Session, public host/plan metadata and actual stdout. It does not call the
new description/adoption operations. After publication, select the same directory,
`formalFailureEpisodeStage=after` and the new installed command. The suite uses
`NativeWorkflowCliFixture.reopenInstalledRepository` to continue those actual
roots and Sessions without rerunning successful checks or editing native rows.
With no episode-stage property it exercises fresh normal sessions.
