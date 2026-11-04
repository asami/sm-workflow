# Standalone validation and retained-result recovery

Standalone Fix accepts the explicit `validation` input kind for a request to
qualify selected checks of a retained candidate. This kind has no task,
delegation, review origin or review-required semantics. A passing complete
selected batch reaches `NoChange`; it does not accept a code repair, commit or
publication. A failed check requires Human attention and does not issue source
repair work. Original business conditions remain unassessed when no semantic
completion occurred.

The `code` kind continues to require Compile and an actually nonempty focused
TestOnly result for acceptance. A new standalone code Start must select both
before staging or execution. Historical inputs remain readable, including an
older code request selecting only TestOnly; reading one does not downgrade its
purpose or remove its acceptance obligations.

## Durable observation

The final successful native check observation appends its actual receipt and
enters `Assess`. The native workflow saves that checkpoint before assessment.
Ordinary Advance then assesses the saved state and saves the resulting state.
An assessment failure therefore leaves the successful observation available.
Candidate reuse follows the same ordering.

## Recovery service operations

The existing StandaloneFixService exposes two normal service operations without
changing the workflow identity or revision:

* `describeStandaloneFixValidationRecovery` accepts
  `StandaloneFixValidationRecoveryDescriptionInput(sessionId, observation)`.
  `observation` is the JSON described below. Description reads the authoritative
  native frontier, retained payload and managed evidence, then returns status,
  message, an opaque exact `target` JSON string, and choices
  `CollectSuccessfulValidation` and `Defer`. It does not write a direction or
  execute a check.
* `recoverStandaloneFixValidation` accepts
  `StandaloneFixValidationRecoveryInput(sessionId, target, choice, rationale,
  reference)` and returns the existing StandaloneFixView. `reference` is a
  WorkflowArtifactReference identifying independently provisioned Human
  evidence. `Defer` leaves the state unchanged. Collection appends an `Assess`
  checkpoint; a subsequent ordinary Advance performs assessment and can reach
  `NoChange`.

Recovery is limited to a retained standalone code request with exactly one
pending TestOnly check, stage `ValidationIntent`, empty receipts, no accepted
semantic reference, no task/delegation/review origin, and no required review.
It does not restage the historical candidate or require a Git observation of
later worktree changes. The recovered result qualifies only that original
candidate.

The opaque target has schema
`sm-workflow.standalone-fix-validation-recovery.v1` and binds the actual component
instance, Session, participant, native frontier revision, original input,
candidate head/tree/paths, selected check, pending identity, retained state
conditions and fully observed evidence. It omits the candidate's potentially
large diff. Unknown or hybrid fields and unsupported schemas are rejected.
Adoption reloads the authoritative state and rereads the evidence; exact target
equality is required. Changed report bytes, stale frontiers, another Session or
owner, and duplicate consumption refuse collection.

## Managed external evidence

An observation uses this closed shape; workspace names, paths, suites, PID and
program identity must identify the actual retained execution:

```json
{
  "schemaVersion": "sm-workflow.retained-external-validation.v1",
  "source": "RetainedExternalValidation",
  "pid": 12125,
  "programIdentity": "actual-program-identity",
  "check": "selected-check-identity",
  "workspace": "sm-workflow-repository-validation-4",
  "suites": ["example.ActualQualificationSpec"],
  "terminalLog": {"workspace": "managed-event-workspace", "path": "advance.stderr.log"},
  "sbtStream": {"workspace": "sm-workflow-repository-validation-4", "path": "test-stream.log"},
  "junitReport": {"workspace": "sm-workflow-repository-validation-4", "path": "test-report.xml"}
}
```

File references are typed logical workspaces and relative paths. Reads use
WorkflowFileExecution and CNCF file targets with its existing read bounds. The
SBT stream and report workspace must be the selected check's
`sm-workflow-repository-validation-N` workspace; the terminal log may be in
another managed workspace.

The terminal log must contain exactly one matching `[cncf-sbt-event]` started
event and terminal event for operation `test-only`, with the same positive PID,
workspace and program identity. The terminal result must be `Exited(0)`, with
zero exit code and positive elapsed time. Missing, conflicting or duplicate
events are rejected. The JUnit parser prohibits DTDs and external entities. It
requires the exact declared suites, named testcases with matching class names,
positive consistent test counts, and zero failures, errors or skipped tests.
The SBT stream must corroborate the declared suites and passing nonempty counts.
Raw contents of all three files are retained in the target and recovery
direction so adoption can detect changed bytes.

## Independent admission and attribution

Recovery uses the existing trusted NativeExternalEvidence issuer, configured
record collection and preparation ingress. Its purpose identity is
`standalone-fix-validation-recovery`. The exact admitted binding is:

```json
{
  "componentInstance": "actual-component-instance",
  "standaloneValidationRecovery": {
    "target": {},
    "answer": {
      "source": "Human",
      "choice": "CollectSuccessfulValidation",
      "rationale": "Independent attribution of the retained execution to this candidate",
      "participant": "actual-participant",
      "reference": {"identity": "actual-reference-identity", "revision": "actual-reference-revision"}
    }
  }
}
```

Here `target` is the complete described target object. The independently
provisioned Human record attests the report/program/original-candidate
relationship. An authenticated participant or request choice alone cannot
authorize collection. The reader is bound lazily for recovery calls, so other
StandaloneFix hosts do not gain an unconditional evidence-reader prerequisite.

Successful collection changes only the original input's kind to `validation`,
the stage to `Assess`, the pending field, and the separately typed
`recoveredValidation` direction. The original code input remains in that
direction's target. Candidate, base, prior receipts, counters, generation,
repair control, source references and original conditions are preserved.
Assessment independently checks that relationship before completing. The
direction remains attributed external validation evidence; it is never
converted into a native process result or validation receipt. Old states omit
`recoveredValidation` and remain decodable.

The operations do not execute Git, SBT or a check, import another Session's
receipt, accept later source edits, publish, commit, rewrite an old checkpoint,
or force a successful code-repair outcome.
