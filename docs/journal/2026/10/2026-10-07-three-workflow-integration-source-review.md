# Preliminary three-workflow integration source review

Date: 2026-10-07. Actual reviewer:
`/root/three_workflow_integration_readonly_1107`, default agent with explicit
`gpt-6.1-sol` / `high`, `fork_turns=none`, read-only delegation.
Parent: `/root`, same active Goal `01a103b3-5f45-7ec2-ac5c-7f9fb01df42a`.

This is the original returned preliminary report, not a sealed Phase acceptance,
new full-review/reassessment chain, repair admission or evidence of test execution.
The parent has not admitted these provisional IDs for repair. Original9/R5/
addition-onlyR6/A7/null/limit3/closed3of3 and consumed759through1106 remain intact.
Request1107 remains NONSTART/unconsumed; its two new scenarios are unverified.
Keep the original source findings and failures unchanged.

## Original returned report

Preliminary result: the default factory now connects all three native services, and the selected successful Operation paths have substantial real-effect/recovery evidence. Three production issues remain in the current connected boundary. This is read-only source review, not formal Phase acceptance or a repair admission.

Current Boundary Blockers:

- **CB-1107-01 [P1] — Conflict staging interprets a declared filename as a Git pathspec.** [RepositoryGitRuntimePrograms.scala](/Users/asami/src/dev2026/sm-workflow/src/main/scala/org/simplemodeling/textus/smworkflow/runtime/RepositoryGitRuntimePrograms.scala:127) constructs `git add -- path`. [RepositorySyncNativeConflictInput.scala](/Users/asami/src/dev2026/sm-workflow/src/main/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncNativeConflictInput.scala:106) permits relative filenames containing `:`, `*`, and `[`. An actual assigned conflict named `:(glob)**`, for example, can stage multiple files instead of its literal file; other magic/glob names can fail to stage the assigned conflict. Subsequent index validation may refuse progression, but the index mutation has already occurred. This is a consumed production connection, not unrelated legacy debt. Fix direction: use the existing literal-pathspec pattern already applied to Goal staging at line137; add a real connected conflict case using a literal magic/glob filename. Source defect established; reproduction was not executed.

- **CB-1107-02 [P2] — A valid reasoning `DecisionNeeded` completion has no connected Human successor.** [SplitPhaseTypedReasoningCompletion.scala](/Users/asami/src/dev2026/sm-workflow/src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseTypedReasoningCompletion.scala:69) legitimately saves `DecisionNeeded` with absent downstream. The subsequent public advance reaches [SplitPhaseManagedPlanningFrontier.scala](/Users/asami/src/dev2026/sm-workflow/src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseManagedPlanningFrontier.scala:34), which returns `serviceUnavailable` requiring a Human handler. Reopening repeats that refusal; no Decision is issued. This directly affects the Phase2 required unknown/reasoning path. Fix direction: connect the retained semantic result to the existing declared Human/result machinery while preserving exact question/evidence binding and no partial selected plan. The current Human contract describes a scope frontier, so do not silently repurpose that scope question without establishing compatibility.1106 covers reasoning `Enriched`, not this successor.

- **CB-1107-03 [P2] — Document recovery accepts changed managed Scope content as the applied postimage.** [SelectedPhaseDocumentProgram.scala](/Users/asami/src/dev2026/sm-workflow/src/main/scala/org/simplemodeling/textus/smworkflow/runtime/SelectedPhaseDocumentProgram.scala:136) verifies embedded contract equality, owned row IDs/order, duplicates, and completion of unrelated rows. It ignores owned closure-criteria cells and owned statuses. After interrupted real Apply, independently changing an owned child’s displayed closure criterion while retaining its IDs and embedded contract still produces `Applied`; owning advance can close with that inconsistent document. [ThreeWorkflowDirectOperationSpec.scala](/Users/asami/src/dev2026/sm-workflow/src/test/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowDirectOperationSpec.scala:473) currently replaces the entire child, which exercises missing-contract refusal but misses this narrower changed postimage. Fix direction: validate the semantic managed sections against the saved selected projection, preserving unrelated history. Strengthen1107 with an edit to an owned criterion that preserves the contract/IDs, expecting conflict, unchanged native intent, and no second Apply. No whole-file hash or byte seal is needed.

Executable-specification observation:

- **CB-1107-04 [P2, authoring] — Affected generated Goal ABI scenarios lack semantic GWT boundaries.** The blocks beginning at [GoalPhaseGeneratedAbiSpec.scala:36](/Users/asami/src/dev2026/sm-workflow/src/test/scala/org/simplemodeling/textus/smworkflow/GoalPhaseGeneratedAbiSpec.scala:36) and [line85](/Users/asami/src/dev2026/sm-workflow/src/test/scala/org/simplemodeling/textus/smworkflow/GoalPhaseGeneratedAbiSpec.scala:85), now containing ordered-Human declarations, have no Given/When/Then clauses. Under the inspected review skill this is `AUTHORING_GATE_FAILURE` for affected authored scenarios, not evidence of runtime malfunction. Their compatibility assertions have value; retain them and author adjacent semantic boundaries. The direct Operation/recovery suites inspected use `AnyWordSpec`, matchers, and semantic GWT clauses. Whole-suite authoring conformance is not certified.

Hygiene:

- **HYG-1107-01 — Existing naming debt in target programs.** Examples include `private val issuer` in [ThreeWorkflowHostEvidencePorts.scala:19](/Users/asami/src/dev2026/sm-workflow/src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowHostEvidencePorts.scala:19) and private camelCase helpers in the runtime programs. These do not satisfy the default `_snake_case` private-member rule. They predate the new1107 scenarios; no incidental renaming is admitted.
- **HYG-1107-02 — Stale checked-in ABI reference.** [abi-manifest.json](/Users/asami/src/dev2026/sm-workflow/src/main/car/abi-manifest.json:1) omits the ordered-Human service/Operation present in current CML/generated runtime ABI. The consumed runtime sidecars contain the current declarations, and the inspected sbt-cozy packaging path generates its manifest separately. Therefore this is reference-asset maintenance, not proof that the fifteenth runtime Operation is missing.

Development candidates:

- **DEV-1107-01 — Real skill/Human-client deployment remains Phase6-owned.** Owner: sm-workflow/client integration. Dependency: delivered typed public Operations and host evidence contracts. Resume with Phase6 integration; do not introduce a replacement issuer, fabricated evidence, or a separate required client as a Phase2 workaround. Semantic mapping belongs to Phase5, workspace work to Phase7, and broad provider/transport/cost coverage to Phase8.

Boundary and evidence:

The frozen primary boundary is the actual factory-connected admission/runtime source, three host assembly files, CML/ABI declarations, consumed build/configuration, and affected ThreeWorkflow/generated ABI/factory specs. I traced assembly, managed effects, selected document recovery, native Git convergence, reasoning completion, ordered Goal continuation, and relevant producer bootstrap/Help interfaces. I did **not** complete a whole-file audit of every reachable admission module, every test body, or all upstream dirty changes.

Original logs independently show1101 **3/3**,1104 **19/19**, and1106 **9/9**. These establish their selected earlier inputs and scenarios, not the entire current candidate.1107’s two real-document interruption scenarios remain **unexecuted**. Remaining evidence includes assembled regressions/full validation, affected generated Help/ABI checks, required semantic-decision breadth, relevant uncertain-effect resolution, and final independent integrated review. Failed1102/1103/1105 remain failures.

CAR lint evidence is parent-supplied exit0 with visible WARN entries; its464-line output was not fully inspected here. Direct filesystem/process detectors identify runtime-program code as well as possible bypasses, so they do not establish blanket unsafety. CBD integration is unverified. No tests, SBT/CLI starts, writes, hashes/seals, staging, commits, network calls, or nested agents were performed. Original history, counters, acceptance state, and consumed count759 remain unchanged.
