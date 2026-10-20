<!-- cncf-phase-finding:HYG-P11-J1-001:begin -->
### HYG-P11-J1-001 — Pre-existing private helper naming

Owner repository: sm-workflow (/Users/asami/src/dev2026/sm-workflow).
Target: a separately authorized repository naming-maintenance task.
Evidence: GoalPhaseNativeRun.scala lines 192–194 retain private token, reference and empty; ThreeWorkflowHostParameters.scala lines 44 and 111 retain private json and targetC. These declarations are present in original base 3509ca5bf11bb748362b38cf6bec0f3c8f65ea5d.
Nonblocking reason: this is existing naming debt, with no demonstrated J1 behavioral regression.
Maintenance boundary: private declarations and their references only, under a separately selected task.
Dependency: none beyond task authorization. Resume condition: that maintenance task is selected.
Prohibited workaround: no incidental cleanup during J1 blocker repair and no public or serialized-name changes.
<!-- cncf-phase-finding:HYG-P11-J1-001:end -->

<!-- cncf-phase-finding:HYG-P11-FULL-001:begin -->
### HYG-P11-FULL-001 — Pre-existing local and private-parameter naming

Owner repository: sm-workflow (/Users/asami/src/dev2026/sm-workflow).
Target: a separately authorized repository naming-maintenance task.
Evidence: GoalPhaseNativeWorkContinuation.scala line 817 retains ordinary local actualInstances. NativeWorkflowCliFixture.scala line 1100 retains private-method parameter startingLogIndex; method-local helpers read/configured/array remain at lines 791/797/814, 936/942/959, and 1107/1113/1132; read/configured remain at 1251/1259, and reap remains at 1564. These declarations are present in original Phase base 3509ca5bf11bb748362b38cf6bec0f3c8f65ea5d.
Nonblocking reason: existing naming debt without a demonstrated Phase 11 behavioral regression.
Maintenance boundary: those local declarations, the private-method parameter, and their references only; preserve public named-argument labels and serialized names.
Dependency: none beyond task authorization. Resume condition: that maintenance task is selected.
Prohibited workaround: no incidental cleanup during the Phase 11 blocker repair, and no public, generated, or serialized-name changes.
<!-- cncf-phase-finding:HYG-P11-FULL-001:end -->

<!-- cncf-phase-finding:HYG-P11-TEST-001:begin -->
### HYG-P11-TEST-001 — Pre-existing specification helper naming

Owner repository: sm-workflow (/Users/asami/src/dev2026/sm-workflow).
Target: a separately authorized specification naming-maintenance task.
Evidence: GoalPhaseCmlDerivationSpec.scala:16,36 retains execute/javaExecutable; GoalPhaseGeneratedAbiSpec.scala:19,27 retains operations/request; RepositorySyncNativeValidationApplicabilitySpec.scala:24–32 retains unprefixed private members, with camel-case locals including wrongCheck/sourceRecord/resultRecord/resultWire; ThreeWorkflowLauncherAssemblySpec.scala:24–26 retains limitKey/component/creation; ThreeWorkflowDirectOperationSpec.scala:85–94,183,343,347 retains nonconforming private helpers/members, alongside existing local helpers. Ordinary Git inspection confirms these declarations existed at original base 3509ca5bf11bb748362b38cf6bec0f3c8f65ea5d.
Nonblocking reason: existing naming debt, with no demonstrated behavioral regression from this repair.
Maintenance boundary: private/local declarations and their references only. Preserve public named-argument labels and serialized names.
Dependency: separate task authorization. Resume condition: that maintenance task is selected.
Prohibited workaround: no incidental cleanup in the Phase 11 test repair and no public, generated or serialized-name changes.
<!-- cncf-phase-finding:HYG-P11-TEST-001:end -->
