<!-- cncf-phase-finding:HYG-P6-CBD-CARRIER-COMMENT-402:begin -->
ComponentKnowledgeProjection.scala:22 still describes prior carrier admission as exact Component/release/digest. The current carrier consumer contract deliberately removes carrier-SHA admission while retaining distinct per-resource sha256 and legacy review metadata. Update this internal explanatory comment in a separate maintenance task to match Component/release plus schema/path admission. No runtime security guarantee or resource-integrity behavior should change.
<!-- cncf-phase-finding:HYG-P6-CBD-CARRIER-COMMENT-402:end -->

<!-- cncf-phase-finding:HYG-P6-INHERITED-GWT-402:begin -->
RuntimeConfigSpec retains 26 unchanged historical behavior blocks without adjacent semantic Given/When/Then, and ExecutionContextSpec retains five. The original-base diff shows these scenarios were not newly authored or behaviorally changed by PHASE-6. New RuntimeConfig scenarios have adjacent GWT; the existing ExecutionContext scenario path correction preserves its GWT. Schedule separate executable-document maintenance for the 31 retained scenarios; do not require new closure specifications or broaden current acceptance.
<!-- cncf-phase-finding:HYG-P6-INHERITED-GWT-402:end -->

<!-- cncf-phase-finding:HYG-P6-INHERITED-NAMING-402:begin -->
Retained target-program private-member naming debt remains outside the PHASE-6 blocker repair boundary. The attached declarations occur verbatim in their original-base files; representative sm-workflow helpers include GoalPhaseNativeCandidate.inspectC/reference/covers and GoalPhaseNativeRun.token/reference/empty. Schedule one separate maintenance pass under the owning repository rules. Preserve public/schema/override contracts and distinguish moved legacy helpers from newly authored declarations. This debt has no demonstrated behavioral impact and does not block the current Phase.
<!-- cncf-phase-finding:HYG-P6-INHERITED-NAMING-402:end -->
