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
