# Phase 1: Judgment-to-StateMachine Guard Map

Status: supporting executable evidence, not a second closure checklist.

This map supports P1-S08 in the
[sole executable checklist](phase-1-executable-specification-checklist.md).
It covers all eight alternatives of the three generated JudgmentActions.
A worker returns decision / rationale / evidence, never an event, target state,
commit command or authority grant. Application admission and deterministic
evidence gates choose the permitted progression. Skills do not dispatch branches.

## Complete judgment disposition map

| Workflow / Action | Admitted alternative | Application-owned gates | Generated entity transition / stopped state |
| --- | --- | --- | --- |
| GoalPhase / ReviewStep | `accept` | Exact issued full-Step review, fresh candidate and program/planning bytes, resumed Awaiting record; optional exact host commit authority; matching commit receipt | `Awaiting --admitted--> Closed` only after those gates. Missing host authority is stop-only Decision, not Closed. |
| GoalPhase / ReviewStep | `repair` | Retain the repair judgment, derive the current candidate from actual bytes, publish bounded Repair / fresh re-review work | Remain Awaiting until a new accepted full review and closure admission. No `repair` self-transition; budget exhaustion is nonterminal WAIT. |
| SplitPhase / AssessNovelSplitWork | `enriched` | Exact frozen unresolved-input coverage, context and authority; generated CAM admission; deterministic proposal reassessment, catalog and PREVIEW admission | `SemanticAssessment --inputs-admitted--> ProposalAssessment --previewed--> Previewed`. Public completion automatically returns same-instance PREVIEW. |
| SplitPhase / AssessNovelSplitWork | `decision-needed` | Admit and retain the exact semantic Result without manufacturing missing authority | Retain SemanticAssessment, revision-3 nonterminal WAIT. This alternative is not a human authority Decision or a generated self-transition. |
| RepositorySync / ReviewMerge | `accept` | Exact fresh full merge evidence; admitted two-parent commit and receipt; separately authorized non-force push and receipt; independent clean 0/0 verification | Review and commit retain MergeValidated. Then `MergeValidated --reviewed-merge-push-admitted--> PushAdmitted --convergence-admitted--> Converged`. Acceptance alone cannot commit, push or complete. |
| RepositorySync / ReviewMerge | `repair` | Retain typed review disposition; refuse commit admission | Remain MergeValidated, revision-9 WAIT. No push or convergence. |
| RepositorySync / ReviewMerge | `decision-needed` | Retain typed review; optional host-owned stop-only Decision projection, without granting commit authority | Remain MergeValidated, revision-9 WAIT in the unconfigured binding, or Decision with the opt-in host binding. No callable human completion/resume engine. |
| RepositorySync / ReviewMerge | `reject` | Retain typed review disposition; refuse commit admission | Remain MergeValidated, revision-9 WAIT. Rejection is not manufactured terminal convergence. |

History retention is not automatically an entity state change. In particular,
GoalPhase repair, SplitPhase semantic WAIT, and RepositorySync review / commit
can append evidence while retaining the same entity progression. Missing or
invalid evidence never becomes an admitted transition merely because the
alternative says `accept` or `enriched`.

## Generated definitions and connected execution

[WorkflowStateMachineGuardSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowStateMachineGuardSpec.scala)
uses the actual Cozy-generated entity classes and CandidateAdmission producers,
not copied runtime implementations. Four specifications check all 18 declared
edges (GoalPhase 1, SplitPhase 5, RepositorySync 12), their exact predecessor /
event / successor triples, and exhaustive rejection of nonedges, alternative
tokens, foreign/blank/null values and shortcut closure/convergence. They also
check the generated Action identities and all eight declared alternatives,
which are disjoint from state-change events and target states.

The connected execution gates are:

- [GoalPhaseStepClosureBoundary](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseStepClosureBoundary.scala): the existing generated `Awaiting / admitted / Closed` check precedes closure effects; live candidate, authority and exact commit receipt remain separate gates.
- [SplitPhaseSemanticWorkflow](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticWorkflow.scala): generated `semantic-needed` before suspension and `inputs-admitted` before enriched reassessment / common claim consumption. Decision-needed retains the current progression.
- [SplitPhaseProposalAssessmentExecution](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseProposalAssessmentExecution.scala): generated `shape-ready` before deterministic readiness history.
- [SplitPhasePreviewExecution](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePreviewExecution.scala): exact retained readiness / semantic Result and generated `previewed` before catalog / terminal append.
- [SplitPhaseApplyExecution](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseApplyExecution.scala): generated `applied` before plan/history/document effects, preserving existing source-bound admission and copied-document CAS. Semantic completion remains PREVIEW-only.
- [RepositorySyncMergedConvergenceExecution](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncMergedConvergenceExecution.scala): exact retained committed prefix and both generated reviewed-merge push / convergence edges before providers and history effects. Existing receipt, remote authority and independent verification gates remain mandatory.

The table tests the entire declared entity edge sets, but this slice connects
generated permits checks to judgment-related progression and SplitPhase
deterministic entry / terminal paths. It does not claim newly added runtime
permits checks for every pre-review normal RepositorySync edge.

The same focused invocation includes connected public Service / CNCF CLI
specifications: GoalPhaseRepairLoopSpec, GoalPhaseReviewBoundarySpec,
GoalPhaseClosureAuthorityExecutionSpec, SplitPhaseSemanticWorkflowSpec,
SplitPhaseStartExecutionSpec, and RepositorySyncMergeValidationAdmissionExecutionSpec.
They exercise every disposition above, exact same-instance progression, retained
evidence/authority stops, unknown decisions and worker-supplied state selectors
rejected before claim consumption. These are fixture Providers and Host ports;
the SBT runner is not an application JudgmentAction worker.

## Validation and limits (2026-09-29)

Focused-224 passed **199/199 tests across seven suites**, with SBT / wrapper
exit 0 and `lock=released`. Its verified receipt binds complete tree SHA-256
`04a31db5bbb7e7f685efb556a4196275caf7f004d095cb0fc7ace1692f06d73b`.
All 197 Git-visible product / build paths still match that receipt.
The [Phase 1 record](phase-1.md) retains the exact invocation, runner, receipt,
summary and product hashes. This focused executable proof verifies P1-S08's
bounded fixture mapping; final intended-tree full validation remains P1-C01.

Full-225 was platform-denied before the receipt helper or SBT started and remains
unstarted. After explicit informed user approval for one retry, full-226 passed
**521/521 tests across 55 suites**, with SBT / wrapper exit 0 and `lock=released`.
Its verified receipt SHA-256 is
`aaa2248e80283fe664342f9a4806be3ee62081722cdc68b177f647c8a240cd21`, binding
complete tree `e1d6ed112fa015ede9e4cd474f91a177c669936058f8401797569ab1d5e28402`.
All 197 product/build paths still match focused-224. Full-223's 517/517 remains
historical evidence for the earlier tree. Documentation recording full-226's
result is outside its complete receipt tree; final Phase validation remains open.

No CML / public Operation / generated ABI revision or upstream source changed.
No production Git adapter, SQLite/runtime deployment, human Result/resume engine,
acceptance commit or Phase closure is claimed. Actual Codex judgment round trip,
actual-worker / deterministic replacement, and final artifact/dependency handoff
remain separate Phase 1 items.
