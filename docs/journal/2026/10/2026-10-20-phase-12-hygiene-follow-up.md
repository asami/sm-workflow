<!-- cncf-phase-finding:HYG-P12-ADOPTION-HEADER-01:begin -->
### HYG-P12-ADOPTION-HEADER-01 — Historical missing continuation-runtime version headers

Status: OPEN. Discovery: STEP_REVIEW, 2026-10-20. Owner repository: goldenport-cncf.
Evidence: src/main/scala/org/goldenport/cncf/workflow/ContinuationRuntimePersistence.scala:14–35 has descriptive comments and declarations without since/version headers. Original base d9109fd7920346d643448d8d602f65414510da50 contains the same omission; the Phase 12 change adds Superseded to the existing enum.
Nonblocking reason: demonstrably pre-existing header maintenance debt, with no demonstrated supersession behavior or persistence regression. Risk: incomplete source-history documentation.
Proposed maintenance boundary: Header metadata in this existing source file, preserving actual historical dates and executable content.
Dependency/resume: separately selected header-maintenance task with reliable source-history evidence. Prohibited workaround: no invented historical dates or incidental header cleanup during Phase 12 acceptance.
Source limitation: the earlier record text for this historical ID was unavailable. This record states the independently verified present observation and does not reconstruct the earlier scope.
Source review: /root/p12_step_conformance. Final Phase commit: recorded by the containing release history.
<!-- cncf-phase-finding:HYG-P12-ADOPTION-HEADER-01:end -->

<!-- cncf-phase-finding:HYG-P12-APP-PRIVATE-NAMING-01:begin -->
### HYG-P12-APP-PRIVATE-NAMING-01 — Historical private helper naming in adoption integration files

Status: OPEN. Discovery: BASELINE_REVIEW, 2026-10-20. Owner repository: sm-workflow.
Evidence: GoalPhaseOrderedProgression.scala:108–116; GoalPhaseNativeRun.scala:199–201; NativeHostEvidence.scala:28,70,130,167–188; ThreeWorkflowHostEvidencePorts.scala:109; ThreeWorkflowHostParameters.scala:44,113 retain historical private naming.
Nonblocking reason: pre-existing naming debt with no demonstrated Phase 12 behavioral regression. Risk: maintenance readability.
Proposed maintenance boundary: Private declarations and their references in those files; preserve public labels, generated contracts and serialized fields.
Dependency/resume: separately selected naming-maintenance task. Prohibited workaround: no incidental cleanup during Phase 12 acceptance or blocker repair.
Source review: /root/p12_formal_baseline. Final Phase commit: recorded by the containing release history.
<!-- cncf-phase-finding:HYG-P12-APP-PRIVATE-NAMING-01:end -->

<!-- cncf-phase-finding:HYG-P12-HEADER-NORMALIZATION-01:begin -->
### HYG-P12-HEADER-NORMALIZATION-01 — Historical same-month Scala version-history entries

Status: OPEN. Discovery: STEP_REVIEW, 2026-10-20. Owner repository: sm-workflow.
Evidence: GoalPhaseNativeRun.scala:26–28; GoalPhaseNativeServiceAssembly.scala:18–19; ThreeWorkflowHostParameters.scala:22–24; ThreeWorkflowLauncherAssembly.scala:13–14; GoalPhaseNativeFindingRecordingSpec.scala:34–35; ThreeWorkflowLauncherAssemblySpec.scala:19–20 retain October 2026 history entries before the latest October 20 version. Each header block is unchanged from original base 55c2b28ab3d5c8662041ce87a5b769ea89940ddb. The canonical version-update instruction excludes history entries in the latest version's month/year.
Nonblocking reason: pre-existing header-shape debt without a demonstrated Phase 12 behavioral regression. Risk: inconsistent source-history presentation.
Proposed maintenance boundary: Normalize only these existing header blocks under the canonical version-update instruction; preserve valid historical dates and executable content.
Dependency/resume: separately selected header-maintenance task. Prohibited workaround: no incidental cleanup, fabricated dates, or source-history rewriting during Phase 12 acceptance.
Source review: /root/p12_step_conformance. Final Phase commit: recorded by the containing release history.
<!-- cncf-phase-finding:HYG-P12-HEADER-NORMALIZATION-01:end -->

<!-- cncf-phase-finding:HYG-P12-REVIEW-001:begin -->
### HYG-P12-REVIEW-001 — Pre-existing validation helper naming

Status: OPEN. Discovery: BASELINE_REVIEW, 2026-10-20. Owner repository: sm-workflow.
Evidence: GoalPhaseNativeValidation.scala:873–874 retains private helpers covers and id, present before the Phase 12 delta.
Nonblocking reason: pre-existing naming debt with no demonstrated Phase 12 behavioral regression. Risk: maintenance readability.
Proposed maintenance boundary: Those private declarations and their references.
Dependency/resume: separately selected naming-maintenance task. Prohibited workaround: no incidental cleanup during Phase 12 acceptance or blocker repair.
Source review: /root/p12_formal_baseline. Final Phase commit: recorded by the containing release history.
<!-- cncf-phase-finding:HYG-P12-REVIEW-001:end -->

<!-- cncf-phase-finding:HYG-P12-ADOPTION-NAMING-01:begin -->
### HYG-P12-ADOPTION-NAMING-01 — Presently verified continuation-runtime local naming debt

Status: OPEN. Discovery: FULL_REVIEW, 2026-10-20. Owner repository: goldenport-cncf.
Evidence: src/main/scala/org/goldenport/cncf/workflow/ContinuationRuntimePersistence.scala:161–163 declares and consumes method-local lockPath. Original base d9109fd7920346d643448d8d602f65414510da50 contains the same declaration and use. The current naming rule requires flatcase for method-local values.
Nonblocking reason: demonstrably pre-existing naming debt without a demonstrated Phase 12 behavioral regression. Risk: maintenance consistency.
Proposed maintenance boundary: This local declaration and its use, preserving behavior and public or serialized labels.
Dependency/resume: separately selected naming-maintenance task. Prohibited workaround: no incidental naming cleanup during Phase 12 acceptance or blocker repair.
Source limitation: this stable ID was historically mentioned, but its original accepted record text was unavailable. This independently verified present observation does not reconstruct, replace as historical evidence, expand or claim resolution of that unavailable original scope.
Source review: /root/p12_phase_full_review. Final Phase commit: recorded by the containing release history.
<!-- cncf-phase-finding:HYG-P12-ADOPTION-NAMING-01:end -->

<!-- cncf-phase-finding:HYG-P12-FINAL-SUBSYSTEM-NAMING-01:begin -->
### HYG-P12-FINAL-SUBSYSTEM-NAMING-01 — Historical Subsystem local naming

Status: OPEN. Discovery: FOCUSED_RE_REVIEW, 2026-10-21. Owner repository: goldenport-cncf.
Evidence: src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:1016–1050 retains private parameter componentId and local bindings workflowStore, eventStore and readPolicy. These declarations are also present in original base d9109fd7920346d643448d8d602f65414510da50; the naming rule requires flatcase.
Nonblocking reason: pre-existing maintenance debt outside the HTTP repair delta, with no demonstrated behavioral regression. Risk: naming consistency and readability.
Proposed maintenance boundary: These private/local declarations and references, preserving public labels, storage contracts and behavior.
Dependency/resume: separately selected naming-maintenance task. Prohibited workaround: no incidental cleanup during Phase 12 closure repair.
Source review: /root/p12_final_fix_rereview. Final Phase commit: recorded by the containing release history.
<!-- cncf-phase-finding:HYG-P12-FINAL-SUBSYSTEM-NAMING-01:end -->

<!-- cncf-phase-finding:HYG-P12-CATALOG-TEST-NAMING-01:begin -->
### HYG-P12-CATALOG-TEST-NAMING-01 — Historical catalog specification naming

Status: OPEN. Discovery: FOCUSED_RE_REVIEW, 2026-10-21. Owner repository: sm-workflow.
Evidence: GoalPhaseCmlDerivationSpec.scala:15,35 retains private execute and local javaExecutable; GoalPhaseGeneratedAbiSpec.scala:18,28 retains private operations and request; SmWorkflowGeneratedHelpSpec.scala:160–161 retains local declaredServices and hostServices; admission/ThreeWorkflowDirectOperationSpec.scala:83–92,181 retains private _scope_frontier_, collection, declaration, instances, typedStart, startWire and withRuntime. These declarations are present in HEAD before the five-file repair. Current rules require single-leading-underscore snake_case for private members and flatcase for ordinary locals and private parameters.
Nonblocking reason: pre-existing naming debt; the repair introduces no naming violation or demonstrated behavioral regression. Risk: maintenance consistency.
Proposed maintenance boundary: Existing private/local declarations and their references in these four specifications, preserving public labels, fixtures and executable behavior.
Dependency/resume: separately selected specification naming-maintenance task. Prohibited workaround: no incidental cleanup during Phase 12 acceptance or repair.
Source review: /root/p12_catalog_rereview. Final Phase commit: recorded by the containing release history.
<!-- cncf-phase-finding:HYG-P12-CATALOG-TEST-NAMING-01:end -->
