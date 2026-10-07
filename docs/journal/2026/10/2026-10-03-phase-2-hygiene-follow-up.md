<!-- cncf-phase-finding:HYG-1107-01:begin -->
**HYG-1107-01 — retain existing identity and OPEN disposition.** Private-member naming remains inconsistent with `_snake_case`, for example [issuer](/Users/asami/src/dev2026/sm-workflow/src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowHostEvidencePorts.scala:19) and [classify/scopeRows](/Users/asami/src/dev2026/sm-workflow/src/main/scala/org/simplemodeling/textus/smworkflow/runtime/SelectedPhaseDocumentProgram.scala:129). Behavioral risk was not established. Owner: sm-workflow; future behavior-preserving Hygiene task. Do not incidentally rename or relabel it.
<!-- cncf-phase-finding:HYG-1107-01:end -->

<!-- cncf-phase-finding:HYG-1124-01:begin -->
### HYG-1124-01 — Explicit checkout-only links in the packaged user guide
Owner repository: /Users/asami/src/dev2026/sm-workflow\
Status: OPEN; nonblocking presentation maintenance.\
Evidence: src/main/car/manual/user-guide.md:33–36 names two repository-only fixture/specification links. Actual1125 CAR lacks these targets, while all15 manual-internal navigation references resolve.\
Risk: packaged-manual readers cannot open those source links; the current text explicitly discloses that limitation and promises neither bundled test source nor a production issuer.\
Maintenance boundary: a separately selected manual presentation improvement. Do not add tests to the CAR, claim deployed clients, alter runtime behavior or make this a Phase2 gate.
<!-- cncf-phase-finding:HYG-1124-01:end -->

<!-- cncf-phase-finding:HYG-SMWF-P2-BASE-001:begin -->
### HYG-SMWF-P2-BASE-001 — CAR public documentation metadata

Owner repository: sm-workflow
Status: OPEN; nonblocking maintenance candidate
Evidence: baseline-car-lint.txt reports 41 missing operation descriptions, nine missing service descriptions, one missing component description, and absent canonical reference-manual and user-guide entrypoints. These are 53 documentation warnings in the captured non-strict CAR-lint receipt.
Risk: generated help lacks explanatory metadata and canonical manual navigation. This observation does not establish that any unwired operation is production-ready.
Maintenance boundary: author public descriptions and manual navigation against the actually installed operation set in a separately selected documentation boundary. Preserve generated API identity and do not expose unfinished operations merely to satisfy documentation checks.
Qualification: runtime help output was not executed or inspected; no release-ready claim is made.
<!-- cncf-phase-finding:HYG-SMWF-P2-BASE-001:end -->

<!-- cncf-phase-finding:HYG-SMWF-P2-BASE-002:begin -->
### HYG-SMWF-P2-BASE-002 — Historical progress prose within the active Phase document

Owner repository: sm-workflow
Status: OPEN; nonblocking presentation maintenance
Evidence: docs/phase/phase-2.md retains earlier progress statements that production runtime assembly is not yet implemented and later, more specific records showing the configured default GoalPhase managed assembly and negative-admission MCP connection. The same document explicitly leaves normal-user success, full transport acceptance and SplitPhase installation open.
Risk: reading an isolated older paragraph can misstate the current bounded implementation frontier.
Maintenance boundary: clarify chronological qualifiers or consolidate the current progress summary while retaining every historical evidence limitation and unfinished acceptance obligation. This is not authority to alter Phase status, historical acceptance or review budgets.
<!-- cncf-phase-finding:HYG-SMWF-P2-BASE-002:end -->

<!-- cncf-phase-finding:HYG-SMWF-P2-CYCLE2-001:begin -->
### HYG-SMWF-P2-CYCLE2-001 — Retained target and supporting private-member naming debt

Owner repository: sm-workflow
Status: OPEN; nonblocking maintenance candidate
Evidence: GoalPhaseLauncherAssemblySpec.scala:30-75 retains private take, component, collection, instances, continuations, persistence, execution, settings, host and create declarations without the required leading underscore. Directly affected unchanged CallScopedOperationBinding.scala retains private var delegate; GoalPhaseManagedComponentFactory.scala retains private def validateHostC. Exact predecessor comparison establishes that these declarations were not introduced by this repair.
Risk: naming inconsistency only; no behavioral defect is established.
Maintenance boundary: a separately selected behavior-preserving naming task. These observations do not authorize cleanup in the present repair.
<!-- cncf-phase-finding:HYG-SMWF-P2-CYCLE2-001:end -->
