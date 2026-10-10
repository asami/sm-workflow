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

<!-- cncf-phase-finding:HYG-174-001:begin -->
### HYG-174-001 — Diagnostic SQLite fixture work-artifact placement

Owner repository: sm-workflow
Status: OPEN; nonblocking, unadmitted maintenance
Source: actual independent `/root/pending_partition_actual174_readonly_review`, configured gpt-6.1-sol/high, bounded174 FINDINGS with separate CB-PENDING174-001.
Evidence: SplitPhaseManagedPlanningCheckpointSpec.scala:5275 newly uses Files.createTempDirectory("smwf-pending-diagnostics-") in the OS temporary root instead of required target/ placement.
Risk: work-artifact placement only; actual174146/146 and native terminal/current-request evidence remain trustworthy.
Maintenance boundary: separately admitted fixture-placement correction preserving unique database, real public Operations, both callbacks and physical reopen. No cleanup, migration, current blocker promotion or automatic repair is authorized.
<!-- cncf-phase-finding:HYG-174-001:end -->

<!-- cncf-phase-finding:HYG-174-002:begin -->
### HYG-174-002 — Pre-existing managed specification size and grouping

Owner repository: sm-workflow
Status: OPEN; nonblocking, unadmitted maintenance
Source: actual independent `/root/pending_partition_actual174_readonly_review`, configured gpt-6.1-sol/high.
Evidence: SplitPhaseManagedPlanningCheckpointSpec.scala has5800lines at actual171 and5921lines/146examples at174, beyond5000line/100example guidance. This is newly recorded demonstrably pre-existing debt, not malformed new behavior.
Risk: broad fixture/vocabulary and multiple public/native surfaces complicate maintenance; no current correctness or reviewability failure established.
Maintenance boundary: separately planned coverage-preserving split. Preserve shared host/fixture vocabulary and every behavior/expectation; no incident cleanup or counter/frame changes.
<!-- cncf-phase-finding:HYG-174-002:end -->

<!-- cncf-phase-finding:HYG-176-001:begin -->
### HYG-176-001 — Orphaned private reference helper

Owner repository: sm-workflow
Status: OPEN; nonblocking, unadmitted maintenance
Source: actual independent `/root/partition_reconstruction_actual176_readonly_review`, configured gpt-6.1-sol/high, bounded176 PASS/currentCB[].
Evidence: SplitPhasePartitionEnrichmentStorage.scala:53 retains private `_exact(identity, revision)` after removal of its only storage-reader callers. Whole current source/caller inspection shows no caller.
Risk: maintenance clarity only; the helper executes no admission gate or effect. Actual17614/14 and native terminal/current-request evidence remain trustworthy.
Maintenance boundary: separately admitted unused-private-helper removal, preserving actual purpose metadata and bindTargetsC behavior. No cleanup, current blocker promotion, new repair frame or counter change is authorized.
<!-- cncf-phase-finding:HYG-176-001:end -->
