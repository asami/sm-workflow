# Phase 1 Evidence Map

Status: completed

Final state: all 34 current checklist items are verified. Full-229 passed
526/526 tests, 56 suites, terminal exit 0/lock released. Actual Codex judgment,
Provider parity and exact three-profile handoff are recorded below. Earlier
slice statements remain historical; they do not reopen completed items.
Audited at: 2026-09-29
Authority: [Phase 1 executable-specification checklist](phase-1-executable-specification-checklist.md)

This document maps existing acceptance IDs to implementation and executable
evidence. It adds no closure authority, development item or production-hardening
gate. Only `verified` items are checked in the existing checklist. `partial`
means that some implementation exists but the entire item is not proved;
`missing` means that application implementation or execution evidence is
absent; `pending-final` denotes final validation and closure after remaining work.

## Validation and provenance

The initial reconciliation changed only this map, the checklist, Phase 1 record
and README. It did not rerun SBT. The preceding full test was
`SMWF-P1-REPOSYNC-MERGED-CONVERGENCE-FULL-20260928-172`: 48 suites / 426 tests,
zero failed / aborted / pending, SBT / wrapper exit 0, `lock=released`.
Its immutable receipt SHA-256 is
`d84df083b007740c31ac90fcd8f3675460b111fc63bb30b42c245218699d4135`
and validated tree SHA-256 is
`f3ff527515d33275eab7c43b0fcaba49f3a2a3641048b37adedd50a8b2faaf8c`.
See the [merge-convergence execution record](phase-1.md).

At that reconciliation, all 178 Git-visible product/build paths under `src/`,
`project/`, `scripts/`, `bin/`, `build.sbt`, `project.yaml` and `.gitignore`
matched the content hashes in receipt 172, with no additional Git-visible
source. Ignored build outputs were excluded. Subsequent documentation or source
changes are not represented as the tree validated by 172.

The subsequent worker-mapping slice passed focused 93/93 tests across three
suites and full 434/434 tests across 49 suites. Both verified receipts identify
the same tree, `82e368dac595f49f3454ba940d61a283697cdc803bd5185cd33481f1279e930a`:

- Focused `SMWF-P1-WORKER-MAPPING-FOCUSED-20260928-173`, receipt
  `628df3dd781bc5f364aeb7565d80ae3be504de2afac7cf87c030bd0a914ef4cd`.
- Full `SMWF-P1-WORKER-MAPPING-FULL-20260928-174`, receipt
  `5218eceff15afb66ea4526eec466b5cdc3ef7b116e405278a51881830f6e13a0`.

Both finished with SBT / wrapper exit 0 and the exact invocation-scoped
`lock=released` marker. The post-validation acceptance / evidence updates are
documentation-only and are not included in that validated tree. CML, generated
ABI fixtures and the upstream baselines were unchanged in that slice. Exact receipt
locators and runner identities are recorded in [Phase 1](phase-1.md).

The subsequent CAM-04 connection adds two specifications to the existing
GoalPhase review fixture. Focused validation
`SMWF-P1-FOCUSED-TO-FULL-REVIEW-FOCUSED-20260928-177` passed 29/29 tests across
five suites, SBT / wrapper exit 0, `lock=released`. Receipt SHA-256:
`40956e572a12e7b1826f44e65aa54ee4406b406178ecd71b77130d988d6ddea1`;
validated tree: `cd5319fc90100816cc33dd72a6a01eaaeb7ed37bbaff88a842009549b0903def`.
The fresh full-178 request passed read-only preflight, but platform approval
review timed out before helper execution. No wrapper / SBT start, start marker
or full-test receipt exists. Full-174 remains the preceding full-suite evidence,
not validation of this new test tree. This execution-infrastructure stop is not
a new application work item or ABI deficiency. Exact failure / request details
are recorded in [Phase 1](phase-1.md). Subsequent documentation is outside the
focused-177 validated tree.

Repaired-candidate preparation subsequently passed focused validation
`SMWF-P1-REPAIRED-CANDIDATE-FOCUSED-20260928-179`: 20/20 tests across three
suites, including six new specifications, SBT / wrapper exit 0 and
`lock=released`. Receipt SHA-256:
`23fdc56d064b75633bce4aea3b82447cd61c5cb37da4d36033152837a8271d12`;
validated tree: `b1fd73a016da3996212b045d24077936e01326948d6d72b36dccef2a3b08f74d`.
This independently selected validation covers new application source, not a
retry or replacement of denied full-178. No full-suite success was claimed for
that preparation-only slice.
The runner returned no textual outcome; the parent located and verified the
immutable execution receipt, exact bound runner identity, saved outputs and SBT
summary. They prove successful terminal execution without another SBT attempt.
Documentation updates after focused-179 are outside that validated tree.

The connected GoalPhase v2 repair loop subsequently passed focused-187:
24/24 tests across five suites, including five new connected specifications.
Full-188 passed 447/447 tests across 51 suites, zero failed / canceled / ignored /
pending or aborted. Both prove SBT / wrapper exit 0 and the exact
invocation-scoped `lock=released` marker, with the same validated tree:
`af3cd023a10725d9cfa27b4c4c80e27bc2f367596ebf3ee8e8661e503cc64ab5`.

- Focused `SMWF-P1-REPAIR-LOOP-FOCUSED-RETEST-20260928-187`, receipt
  `c90c388cfc2d320d26cc9f65130438f86b07778fa67434616a72a8bc82a5933a`.
- Full `SMWF-P1-REPAIR-LOOP-FULL-20260928-188`, receipt
  `63d83480a23144a5303c18839c3bcb771e14fba44b1294c04f088b95b474a177`.

The new CML declares the ordinary Repair Operation and typed completion API.
Actual `cozyGenerate` generated 56 Scala sources; the actual Cozy canonical
JSON generator exported the two checked-in fixtures. CNCF's mixed-action
receiver had incorrectly compared ordinary Operations with CAM-only sidecar
actions. The bounded receiver fix passed 3/3 focused tests and was locally
published at the unchanged `0.5.3-SNAPSHOT` coordinate before consumer tests.
No Cozy source change was made. Full-188 is independently selected validation
of this new connected tree, not a retry or recovery of denied full-178.
At that slice's documentation reconciliation, all 185 Git-visible product /
build paths matched full-188, with no additional product path.
Documentation updates after full-188 are outside its validated tree. Exact
runner identities, receipt locators, failures and provenance are recorded in
[Phase 1](phase-1.md).

The subsequent SplitPhase frozen semantic-input foundation passed focused-190:
69/69 tests across four suites, including nine new specifications. Full-191
passed 456/456 tests across 52 suites, zero failed / canceled / ignored / pending
or aborted. Both prove SBT / wrapper exit 0 and invocation-scoped
`lock=released`, with the same validated tree:
`57afe9c5144c42aa341d1c2697023d591bec74045ff8b57d5d71f48269ca55e4`.

- Focused `SMWF-P1-SPLIT-SEMANTIC-INPUTS-FOCUSED-RETEST-20260928-190`, receipt
  `7d21190dba048d963be890f0c5f7f338884bb1a86942f6113ad83ace85489a4d`.
- Full `SMWF-P1-SPLIT-SEMANTIC-INPUTS-FULL-20260928-191`, receipt
  `209b761ef851feed37b7b3c2365ef7df66fc8570829f3b14d63a3b4136629379`.

Focused-189 stopped during new test compilation because of the ScalaTest
`pending` name collision and ambiguous wildcard `Outcome` imports. Those test
defects were repaired before 190; the failure remains recorded in Phase 1.
CML, generated ABI fixtures and upstream source were unchanged in this slice.
All 187 Git-visible product / build paths match full-191 at this reconciliation.
The later English documentation updates are outside its validated tree.
This historical slice proves pure semantic payload / admission and deterministic
reassessment, not common WorkOrder publication, public completion or resume.

At the next slice's initial stop, the SplitPhase connection was implemented but
not yet behavior-validated. Its actual CML producer probe 193 passed; 192 exposed an
incomplete constituent mapping, which was fixed in application CML.
Focused-194 was denied by platform approval review before SBT / compilation
started, citing unknown-scope escalated rendered-request risk and insufficiently
specific authorization in `go ahead`. No retry or full-test attempt followed.
Nine new connected specifications are present but are not passing evidence.
Full-191 remains the last full-suite success for the preceding foundation tree,
not the new connected product tree. Exact runner identities, frozen request,
producer receipts and informed-approval boundary are recorded in
[Phase 1's pending connection record](phase-1.md).

After the user's informed permission, new focused invocation 195 started and
found missing CML COMMAND input kinds. Invocation 196 then found duplicated
generated Command wrappers for two operations sharing one input type inside one
Service. Application CML now marks request / result inputs and separates
semantic review from deterministic admission Services; no upstream source edit
was required. Focused-197 passed 65/65 tests across the five selected suites,
including all nine new connected SPW specifications. SBT / wrapper exit 0 and
the exact invocation-scoped `lock=released` marker are verified.

- Invocation: `SMWF-P1-SPLIT-SEMANTIC-CONNECTED-FOCUSED-RETEST-20260928-197`.
- Receipt: `7813dd9e5dfc15d19c7c4e5433ea9b8818cd38cf2bcb8266165ca136aa4ff159`.
- Validated tree: `75e915f552ff52eb326a543b1536ec4be14213e01e381d9ca632e11a04ab78c6`.

This proves generated binding / compile coverage and the connected Service /
CLI semantic-to-PREVIEW path, not a new full-suite result, actual Codex judgment,
typed authority Decision or Phase closure. At that reconciliation, all 190
Git-visible product / build paths matched focused-197; later English
documentation was outside that tree.
Full-191 covers only the preceding foundation. Exact failure receipts, runner
identities and result-display recovery are recorded in
[Phase 1](phase-1.md).

Full-198 subsequently passed 465/465 tests across 53 suites on the connected
product tree, with zero failed / canceled / ignored / pending or aborted tests.
SBT / wrapper exit 0 and the exact invocation-scoped `lock=released` marker are
verified. Generation reused the 65 sources generated by focused-197.

- Invocation: `SMWF-P1-SPLIT-SEMANTIC-CONNECTED-FULL-20260928-198`.
- Receipt: `f87d433841e3183489ee2aa45e4d2767d2570df488fcc08e1aa9dd578f619c33`.
- Validated tree: `bab6e6e7d5c1961894809ec952884bcb73a408c4b0405ac476d15a0e07a679cf`.

At that slice, all 190 Git-visible product / build paths matched full-198. Later
English documentation was outside its complete validated tree. This is full
regression evidence, not final intended-tree validation after remaining Phase 1
work, actual Codex judgment proof, typed authority Decision or Phase closure.

The subsequent GoalPhase public Start-to-close composition passed focused-201
(31/31, five suites) and full-202 (474/474, 53 suites). Nine new specifications
start from an empty store and use all four generated Provided Operations through
one fixture Service. Normal review, repair / re-review and proactive review
close only the Start-created instance; host association, invalid results and
loose persistence failures are also checked. Both receipts validate tree
`4360b200f103b342edffeef8855e2ca1a66bc263ae91c96c46de0e0b5d3adebb`.
Full-202 receipt SHA-256 is
`6e2d00508ca4bea25d2e39eaa8a93fc99b4f6356421c1c2c276fb437e84c8dc7`.
At that slice, all 191 product / build paths matched full-202; later documentation updates are
outside its complete tree. Two initial compile failures (199 / 200) were fixed
in application code / tests before 201. No Cozy, CNCF or CML edit was needed.
These fixture commit ports do not perform real repository commits, and an SBT
runner is not actual application Codex judgment evidence.

The subsequent SplitPhase identity Decision stop passed focused-204 (68/68,
three suites) and full-205 (482/482, 53 suites), including eight new public /
negative specifications. Both prove SBT / wrapper exit 0 and `lock=released`
on tree `3f98af0be949254f03d05382a528971a043547f8c90a0db2f1c4409704314ca4`.

- Focused `SMWF-P1-SPLIT-IDENTITY-DECISION-FOCUSED-RETEST-20260928-204`, receipt
  `e40b22d338fae0e84d830067e18aed8c27311f6097b279e0c7a6718d0470ea3f`.
- Full `SMWF-P1-SPLIT-IDENTITY-DECISION-FULL-20260928-205`, receipt
  `765f64c3e89cd71640620ce24252581a6a90ecebc7d236f27855ef0156f34367`.

All 192 product / build paths matched full-205 at that slice. Later English
documentation is outside its complete tree. 203 stopped at test compilation
on function-type syntax / ambiguous imports, fixed before 204. The parent
verified 204 / 205 original intake-owned immutable receipts after runner output
transport did not show their successful execution; no SBT was rerun for display.
This proves only the opt-in SplitPhase identity/numbering Decision projection,
not a human-result execution engine or all three profiles' authority boundaries.
CML / generated ABI fixtures and upstream sources were unchanged. Exact
runner identities, locators and terminal evidence are recorded in Phase 1.

The subsequent RepositorySync `decision-needed` stop connection is now a
validated fixture. Focused-206 stopped at test
compilation. Focused-207 ran 137 tests across two suites: 133 passed, four
failed because the new projection compared opaque DefinitionBinding object
identities instead of admitted ABI facts. The parent corrected this to the
existing full-fact comparator and strengthened rehydration / host-negative
checks. Focused-208 was platform-denied at read-only preflight, before SBT
started. After explicit informed user approval of the exact two suites,
scope-identical new-209 was independently submitted with the actual approval
in its verified native parent suffix; it was also denied before preflight ran.
Both denied intakes remain unstarted. The updated command contract separates
read-only rendering from separately reviewed execution. The direct continuation
in the sm-workflow development chat selected independent validation-only work:
focused-210 passed 137/137 tests across two suites and full-211 passed 490/490
across 53 suites, both with exit 0 and `lock=released` on tree
`f5ffb3b6d9a3d7cdf2e0976ae913c199f0fad97341f0687d0885c50fe40ffefc`.
Their receipts are `da44ac50a1928071434de2ef507c772fac90d62c9846b603fa20efdeba3a85a4`
and `5d2c09c0fe86f2f6c7ba86c8c3c7430bdb2159339166ecca728605dd7096cafc`.
At that slice all 193 product / build paths matched full-211; later documentation is outside
its complete tree. The other-owned design note is unchanged. Exact locators,
old denials and new independently permitted execution are recorded in Phase 1.

The subsequent GoalPhase pre-reviewed authority gate passed focused-216
(32/32, four suites) and full-217 (497/497, 54 suites), including seven new
specifications and rehydrated Service / CLI authorized closure. Both prove SBT /
wrapper exit 0 and invocation-scoped `lock=released` on the same tree:
`07c47f00c9299b39d7d8fbe1838db0aaf7aad84aa9b210770050e9d90f66738e`.
Receipts are `d3e59091357a205796e6faaff524bc95393a4aff52c8a03020840eb269735fda`
and `4a8ef48554450fdafdc01a54ad5924286a7f80b52adce27268ce27e41c5d7f47`.
At that slice all 195 product / build paths matched full-217; later changes are
outside the complete tree. CML, checked-in ABI fixtures and upstream source are
unchanged. Compile failures 212 / 213 and initial passing 214 / 215 remain
historical evidence. Exact locators and identities are in Phase 1.

The follow-on integrated GoalPhase authority composition adds ten specifications
covering accepted-review
retention, automatic review / repair authority gating, exact authorization,
read-only re-projection and fail-closed proof / instance / artifact drift.
Focused-218 stopped before SBT: the registered command runner's fixed `python3`
preflight conflicts with the updated helpers' uv-managed CPython prerequisite.
No start marker or execution receipt exists for 218. After workstation agent
synchronization, fresh focused-219 passed 42/42 tests across four suites and
full-220 passed 507/507 across 54 suites, both with SBT / wrapper exit 0 and
invocation-scoped `lock=released` on complete tree
`5f9450fd0fd434ec578182c9fc1c2c32a33fb9f68fac9631d4ce398b77cd1408`.
Receipts are `2cc00b20523a01e145c75d1d3385f0287233d3377ee24168360e2962158397a3`
and `f67ee5ec40f9277e17be08cf216418f1c54e00b1ce79d5eabf367dfe4d21c43a`.
At that slice all 196 product / build paths matched full-220. Post-validation documentation
is outside that complete tree. The registered runners used the updated uv-managed
preflight and unchanged mechanically rendered execution requests; no parent-direct
SBT or permission bypass occurred. GPI is now executable evidence for this
bounded fixture connection; no item is promoted beyond its proved scope.

The subsequent combined SplitPhase semantic / identity fixture adds ten
specifications. Focused-221 ran 80 tests (79 passed / one failed), exposing
common completion's opaque ABI object-identity comparison after rehydration.
A local exact-ABI persistence adapter repairs that boundary without editing
CNCF or accepting foreign definition facts. Fresh focused-222 passed 80/80
tests across four suites and full-223 passed 517/517 across 54 suites, both
with SBT / wrapper exit 0 and `lock=released` on the same complete tree
`064170110055d1e4cba0d5852f75007a2dc70cc9dcc0d43f79c1e0f38e21749c`.
Receipts are `8ba556f32acd7a950a111cd0daae367669bbd774c5691b9b0886caf21ebe3740`
and `dcee239c1411fce40fb65263b51f6ea64a5957ae006bb5471590f3211ccc8a86`.
At that slice all 196 product / build paths matched full-223; later documentation is outside
that complete tree. SPC closes the remaining fixture-composition gap for
CAM-07 / P1-A03 / P1-S02 / P1-S03. Actual application Codex judgment,
full profile guard mapping and final intended-tree handoff remain open.
Failed-221 is retained as failed evidence. All three command runners were
registered `gpt-5.6-luna / medium`; exact identities and locators are in Phase 1.

The subsequent generated judgment-guard slice adds actual generated permits
checks to SplitPhase suspension / admission / deterministic entry / PREVIEW /
APPLY and RepositorySync reviewed-merge push / convergence. GoalPhase's existing
generated closure guard is retained. Four new specifications check all 18
declared entity edges and eight JudgmentAction alternatives; six connected
public execution suites verify disposition and evidence/authority gates.
Focused-224 passed 199/199 tests across seven suites with SBT / wrapper exit 0
and `lock=released`, on complete tree
`04a31db5bbb7e7f685efb556a4196275caf7f004d095cb0fc7ace1692f06d73b`.
Receipt SHA-256 is
`e85d5e5fbeb2179d297f840239db1293627a45ff3f5f35c1e301a6a4468f1d32`;
all 197 product/build paths match. JG verifies P1-S08's bounded fixture mapping.
Full-225 was platform-denied before the receipt helper or SBT started. There
is no full result / execution receipt for that invocation. After explicit
informed user approval for one retry, full-226 passed 521/521 tests across
55 suites with SBT / wrapper exit 0 and `lock=released`. Its verified receipt is
`aaa2248e80283fe664342f9a4806be3ee62081722cdc68b177f647c8a240cd21`, binding
complete tree `e1d6ed112fa015ede9e4cd474f91a177c669936058f8401797569ab1d5e28402`.
All 197 product/build paths still match focused-224. Full-223 remains historical
evidence for its earlier tree. Result-recording documentation is outside
full-226's complete validated tree. Actual worker and final intended-tree
validation / handoff remain open.

| Subject | Recorded baseline |
| --- | --- |
| sm-workflow HEAD | `75337065397b49e1f663edac0b63600174e87e55` + uncommitted fixture work. Judgment-guard mapping passed focused-224 (199/199, 7 suites) and the explicitly authorized full-226 retry (521/521, 55 suites); all 197 product/build paths match. Full-225 remains unstarted/denied. Result-recording documentation is outside full-226's complete tree. Passing full-223, failed-221, unstarted-218 and prior failed / denied attempts remain historical |
| Cozy Phase 62.3 producer | fixture `db5e2b6bc6a035b9c598abdeee56d856116ee0a4` / handoff `f3c85b3de12c78efb6e88e210af71f1bd84ea9a5` |
| additive Cozy placed / Provided API | `8d70aff3bc4b6e311bd7de38c209f08a7ad72bf0` |
| CNCF Phase 77.2 released handoff | accepted implementation `74aa88f4c4e4a8b2e4d681fe285cfe57bca9ba49` |
| observed Cozy checkout | `12a0e6d79321f146592bd527301d967fba774c94` at producer-193 (preserving other work's dirty source / docs) |
| observed CNCF checkout | `29b08d14ff86eabae4442dbbf3327d8983261f1a` + bounded receiver / specification changes, validated by focused-180 and locally refreshed by 181 |
| selected build coordinates | Scala `3.3.8` / Cozy `0.3.3-SNAPSHOT` / CNCF `0.5.3-SNAPSHOT` / sbt-cozy default `0.1.17` |
| Workflow / Candidate / Provided schema | `cozy.cml.statemachine-workflow-abi.v1` / `cozy.cml.candidate-admission-producer-abi.v1` / `cozy.cml.statemachine-provided-api-abi.v1` |
| application revisions | GoalPhase `goal-phase-step-close-v2` / SplitPhase `split-phase-semantic-preview-v2` / RepositorySync `repository-sync-merge-convergence-v1` |
| current CML SHA-256 | `1fa7fbd3b2115980db5c1922c21150ce36e5a297a40e54139d193fc968f3c55b` |
| GoalPhase Workflow JSON SHA-256 | `86128694abef582670b80eddb454f8bca599119a4b525cdc80aa99afbf7edbc9` |
| GoalPhase producer JSON SHA-256 | `db4a5d128a54d10957e6e659ed0c616734b24320a3a5b43de7f50ff24e243a64` |

The upstream release baseline is recorded in CNCF's
[released consumer handoff](../../../../dev2025/cloud-native-component-framework/docs/phase/phase-77.2-sm-workflow-handoff.md);
it is not assumed identical to the observed checkouts or SNAPSHOT coordinates.
Cozy 62.3 and CNCF 77 / 77.1 / 77.2 / 90 phase documents are closed.
The actual specifications below prove Phase 90 CAM consumer integration.
The final three-Workflow artifact / resolved-dependency handoff remains P1-S05.

## Evidence references

- **JG**: [judgment guard map](phase-1-judgment-guard-map.md), [WorkflowStateMachineGuardSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowStateMachineGuardSpec.scala), existing [GoalPhase closure](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseStepClosureBoundary.scala), connected [SplitPhase semantic](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticWorkflow.scala), [readiness](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseProposalAssessmentExecution.scala), [PREVIEW](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePreviewExecution.scala), [APPLY](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseApplyExecution.scala) and [reviewed-merge convergence](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncMergedConvergenceExecution.scala). Focused-224 proves generated edge/alternative separation and connected disposition admission across all three profiles. No worker-selected state or authority, fabricated self-transition or direct semantic-to-terminal shortcut is allowed. This is fixture proof, not actual Codex judgment or newly connected runtime permits checks for every pre-review normal RepositorySync edge. Full-225 remains unstarted/denied; explicitly authorized full-226 passed 521/521 tests across 55 suites.
- **WM**: [WorkflowWorkerMapping](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowWorkerMapping.scala), [WorkflowWorkerMappingSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowWorkerMappingSpec.scala). Host-owned versioned policy, all four ReasoningLevels, capability / risk / review compatibility, frozen selection, exact completed worker / model / profile / policy evidence, stale-work rejection, common Result round-trip and profile replacement without changing issued work. These are fixture Host attestations, not actual Codex dispatch.
- **GP**: [GoalPhaseReviewBoundarySpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseReviewBoundarySpec.scala). Post-commit review publication, typed Result admission, pre-reviewed closure, public accept completion with automatic close, repair remaining nonterminal, exact program / planning snapshots and commit receipts.
- **GPR**: [GoalPhaseRepairCandidateBoundary](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseRepairCandidateBoundary.scala), [GoalPhaseRepairCandidateBoundarySpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseRepairCandidateBoundarySpec.scala). Immutable admitted repair-judgment correlation, actual snapshot-derived current candidate, retained old evidence and freshness / coverage re-evaluation. This is preparation only, not Repair completion admission or automatic work publication.
- **GPL**: [GoalPhaseRepairLoop](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseRepairLoop.scala), [GoalPhaseRepairCompletionOperation](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseRepairCompletionOperation.scala), [GoalPhaseRepairLoopSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseRepairLoopSpec.scala). Typed ordinary Repair WorkOrder / Result admission, retained intent, current-candidate full re-review and exact deterministic closure. Service / CLI, changed / unchanged candidates, one close invocation / one commit, replay / stale headers / state-selector / old-target / artifact-drift rejection, one-repair budget and loose failure boundaries.
- **GPS**: [GoalPhaseFixtureServiceAssembly](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseFixtureServiceAssembly.scala), [GoalPhaseRepairLoopSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseRepairLoopSpec.scala). Focused-201 / full-202 proves empty-store public Start through review / typed completion and exact terminal on the same instance via Service / CLI, including repair, proactive evidence, host association and fail-closed persistence. Start history is retained; no manually stored active baseline or production Git provider substitutes for it.
- **GPA**: [GoalPhaseClosureAuthority](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseClosureAuthority.scala), [GoalPhaseClosureAuthorityExecutionSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseClosureAuthorityExecutionSpec.scala). Focused-216 / full-217 prove seven new specifications for an opt-in pre-reviewed public Start / Service / CLI authority gate. Missing exact host-held authority yields a stable Decision with unchanged Awaiting history and zero commit; malformed / stale authority, host intent and instance / ABI / byte / authority drift fail closed. Separately recorded exact authority permits one closure, including rehydrated bindings. No callable human endpoint or Decision Result/resume is added; this passing evidence does not cover the later automatic-review integration.
- **GPI**: [GoalPhaseReviewedClosure](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseReviewedClosure.scala), optional GPS assembly / review-completion binding and ten added [GoalPhaseRepairLoopSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseRepairLoopSpec.scala) specifications. Focused-219 / full-220 prove retained one-shot accepted-review proof and automatic authority gating after review / changed or unchanged repair, stable stop-only Decision, exact authorized closure, proactive review gating and host-only read recovery without resubmitting Result or committing. Retention / display / proof / instance / candidate / authority drift failures are explicit. No shared transaction or human Result/resume API is added. Unstarted-218 remains historical, not execution evidence.
- **CAM**: [StepClosureAdmissionSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/StepClosureAdmissionSpec.scala), [StepCloseRequestAdapterSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/StepCloseRequestAdapterSpec.scala), [GoalPhaseStepCloseRoutingSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseStepCloseRoutingSpec.scala). Typed submissions, full / focused coverage, invalidation on program / planning changes and declared Required SPI routing.
- **SP**: [SplitPhaseStartExecutionSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseStartExecutionSpec.scala). Public Start / assessment / PREVIEW / APPLY Operations, source-bound evidence, copied-document CAS, identical host-bound Service / CLI terminals and apply replay.
- **SPD**: [SplitPhaseIdentityDecision](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseIdentityDecision.scala), [SplitPhaseStartExecutionSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseStartExecutionSpec.scala). Focused-204 / full-205 proves public Start → typed identity Decision on the same stopped instance, stable Service / CLI retries, strict application input and host context, unavailable binding / stale source / concurrent instance rejection and WorkOrder-result separation. It is an opt-in stop-only projection; human operation identities are metadata and no human completion endpoint is registered. At that slice semantic composition was rejected; SPC now proves the combined binding.
- **SPC**: [SplitPhaseFixtureServiceAssembly](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseFixtureServiceAssembly.scala), [SplitPhaseSemanticWorkflow](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticWorkflow.scala), [SplitPhaseProposalAssessmentExecution](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseProposalAssessmentExecution.scala) and ten added [SplitPhaseSemanticWorkflowSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticWorkflowSpec.scala) specifications. Focused-222 / full-223 prove both ports on the same host/store, pre-AI identity Decision, same-instance semantic PREVIEW and rehydrated ABI admission through Service / CLI, stable read-only display recovery, strict metadata/context/live-evidence/instance checks, and foreign ABI / new collision rejection before claim consumption. Deterministic readiness and APPLY remain unchanged; semantic `decision-needed` stays WAIT. No human Operation registration, authority manufacture or Result/resume engine is added. Failed-221 exposed the local exact-ABI adapter need and remains failed evidence.
- **RSD**: [RepositorySyncMergeDecision](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncMergeDecision.scala), existing public merge-review completion and [RepositorySyncMergeValidationAdmissionExecutionSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncMergeValidationAdmissionExecutionSpec.scala). Focused-210 / full-211 prove eight added specifications for opt-in typed Decision after durably retained revision-9 review, stable host-only projection recovery, strict frozen input / metadata / rehydrated ABI facts and no commit authority. Denied-208 / 209 remain unstarted. This is a stop-only fixture connection, not human Result/resume execution or a production Git adapter.
- **SPS**: [SplitPhaseSemanticInputs](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticInputs.scala), [SplitPhaseSemanticInputsSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticInputsSpec.scala). Validated frozen joint gap request, typed judgment, exact semantic coverage / authority admission, stale validation-input rejection and deterministic reassessment. Foundation tests alone do not prove common claim / publication / completion.
- **SPW**: [SplitPhaseSemanticWorkflow](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticWorkflow.scala), [SplitPhaseSemanticJson](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticJson.scala), [SplitPhaseSemanticWorkflowSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticWorkflowSpec.scala). Focused-197 proves generated semantic Action / Required SPI binding, saved intent / common WorkOrder, strict public completion, live pre-claim admission and automatic same-instance PREVIEW through Service / CLI. Nine specifications also prove single-kind gaps, rejection / no-claim-consumption, nonterminal decision-needed and loose persistence failures. Process-memory fixture stores do not claim production restart durability.
- **RS**: [RepositorySyncMergeValidationAdmissionExecutionSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncMergeValidationAdmissionExecutionSpec.scala). Start-bound retained chain, review WorkOrder / public completion, distinct accept / repair / decision-needed / reject assessments, exact commit, merged push / convergence terminal and failure remaining nonterminal.
- **RS-NORMAL**: [RepositorySyncConvergenceExecutionSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncConvergenceExecutionSpec.scala). EQUAL / REMOTE_AHEAD / LOCAL_AHEAD typed terminals and public / CLI projections.
- **ABI**: [GoalPhaseGeneratedAbiSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/GoalPhaseGeneratedAbiSpec.scala), [GoalPhaseCncfConsumerSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/GoalPhaseCncfConsumerSpec.scala), [SplitPhaseGeneratedAbiSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/SplitPhaseGeneratedAbiSpec.scala), [RepositorySyncGeneratedAbiSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/RepositorySyncGeneratedAbiSpec.scala). Actual Cozy generation, CNCF admission, source correlation / exact fixture hashes and typed Provided Operations for all three profiles.
- **START**: [ProfileStartAdmissionSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/ProfileStartAdmissionSpec.scala) and [GoalPhase](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseStartExecutionSpec.scala) / [SplitPhase](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseStartExecutionSpec.scala) / [RepositorySync](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncStartExecutionSpec.scala) Start specifications. Explicit human selection, exact common Start projection, rejection of recommendations and manufactured selection.
- **SCOPE**: [Phase 1 goal / exclusions](phase-1.md), [historical deterministic closing addendum](phase-1-deterministic-closing-addendum.md), [ComponentFactory](../../src/main/scala/org/simplemodeling/textus/smworkflow/impl/ComponentFactory.scala). Distinguishes test-only explicit bindings from production component registration, real Git and operational hardening.

## Checklist dispositions

The initial reconciliation verified 16 of 34 items. Worker-mapping validation
adds P1-A05; focused-177 proves CAM-04; focused-187 / full-188 proves CAM-05 / 06.
Focused-197 completes the former missing SplitPhase definition / payload
boundary for P1-A01 / A06 / A07. GPS focused-201 / full-202 completes P1-S01.
SPC focused-222 / full-223 completes CAM-07 / P1-A03 / P1-S02 / P1-S03.
JG focused-224 completes P1-S08; full-225's unstarted denial does not supply
final intended-tree validation evidence or invalidate the focused proof.
Explicitly authorized full-226 adds full regression evidence for the guard tree;
it does not complete remaining application work or its final acceptance.
Currently **34 verified**, none partial, missing or pending-final. Live worker
proof, three-profile consumer handoff, full-229 final regression and Phase status
update complete the bounded application/fixture scope.
Partial items distinguish absent implementation from unconnected executable proof;
they do not require reimplementation of existing functionality.

| ID | Disposition | Evidence / remaining boundary |
| --- | --- | --- |
| CAM-01 | verified | GP passes candidate program / planning snapshots to the typed Step-close Operation, without a next-state input. Skills can use this wire; actual Codex dispatch is a separate proof. |
| CAM-02 | verified | CAM / GP generates and retains Candidate / Submission identity, artifact digests and review evidence, evaluating admission before commit. Production disk durability is not required. |
| CAM-03 | verified | GP's pre-reviewed Step closes without a review WorkOrder; CAM proves reuse of fresh full review. |
| CAM-04 | verified | GP focused-177 proves public Step-close retains exact scoped evidence in the persisted WorkOrder, requires the declared full-review SPI, rejects inadequate or stale completion without consuming its claim, and accepts fresh full review through public / CLI completion with one close invocation and one exact commit receipt. |
| CAM-05 | verified | GPR / GPL derives the current candidate from actual program / planning bytes, preserves original submission and retained evidence, rejects old-target / expired / inadequate coverage and automatically publishes the policy-required fresh full-Step review. Unchanged bytes never convert a repair judgment to acceptance. |
| CAM-06 | verified | GPL through Service / CLI connects Review / Repair / ReReview to the existing exact snapshot / commit boundary on the same instance, with one public close invocation and one terminal commit. Replay and worker-supplied state selectors are rejected. |
| CAM-07 | verified | GP / RS / SPW exposes missing semantic evidence as typed WorkOrder; RS-NORMAL / RS and SP / SPC provide deterministic evidence/readiness gates. SPD / RSD / GPA / GPI / SPC prove explicit authority/identity Decision stops. SPC combines SplitPhase identity precheck with semantic progression without AI authority manufacture. This is fixture stop-boundary proof, not a human resume engine. |
| CAM-08 | verified | GP / RS strict Result codecs, candidate / evidence admission and commit / snapshot gates reject worker-supplied state / claim / command / commit selectors. |
| CAM-09 | verified | GP passes exact program + planning bytes / digests to the commit fixture and appends Completed only after a matching receipt. This is not a completed real Git provider. |
| CAM-10 | verified | CAM specializes CNCF generic Candidate / Submission. sm-workflow owns Step scope / closure / commit semantics without copying or changing framework DTOs. |
| P1-E01 | verified | Records released upstream chain, ABI / compatible runtime versions and closed Cozy 62.3 / CNCF 77 and 90 handoffs. ABI / CAM proves consumer admission. |
| P1-E02 | verified | START / GP / SP / RS uses CNCF common protocol / codecs / instance binding. Application types are payloads / policies, not generic envelopes. |
| P1-E03 | verified | Public WorkOrder / WAIT / terminal uses the same framework Handle and closed Continuation. GP / RS checks issued headers, context and completion identity. |
| P1-A01 | verified | Existing GP / SP / RS payload evidence plus SPS / SPW focused-197 now covers typed Start / semantic work / admitted Result / terminal for all three profiles. Public SplitPhase completion projects the common typed PREVIEW terminal, not a second generic envelope. |
| P1-A02 | verified | All three CML Workflow / StateMachine definitions have actual producer / consumer ABI bindings. Generated semantic Action binding is P1-A06; completed same-instance fixture execution is P1-S01. Actual worker and authority Decision evidence remain separate. |
| P1-A03 | verified | GP / GPS / SP / RS-NORMAL / RS / SPW prove each definition's deterministic and semantic fixture paths. SPD / RSD / GPA / GPI add public authority Decision boundaries; SPC focused-222 / full-223 closes combined SplitPhase semantic/identity composition with strict host/store association. No production provider or human execution engine is claimed. |
| P1-A04 | verified | GP / SP / RS passes common MinimalPresentation to WorkOrder / WAIT / terminal; title, situation and optional next action remain display-only. |
| P1-A05 | verified | WM maps ReasoningLevel through a versioned compatible Host policy and normalizes completed external evidence. GP uses an actually issued WorkOrder through semantic admission and public completion, with the same judgment as the deterministic Result. Selection is not a guard; deterministic Providers invent no dispatch profile. |
| P1-A06 | verified | ABI / GP / RS declares contextual review judgments; SPW focused-197 binds the generated novel-work / split-boundary JudgmentAction and exact Required SPI. CML goal, context, alternatives, criteria, expected result and evidence contracts are admitted; deterministic assessment / catalog / PREVIEW / APPLY remain operations. |
| P1-A07 | verified | GP / RS plus SPS / SPW admits typed decision / rationale / evidence without next state. SPW focused-197 proves strict public completion and rejects unknown decisions, state selectors, known-input overwrite and invented authority before common claim consumption. |
| P1-S01 | verified | GPS focused-201 / full-202 proves public Start → review WorkOrder → typed Result → exact Step-close terminal on the same instance, also through repair / re-review. SPW proves semantic PREVIEW and RS proves reviewed merge convergence from their public Start chains. These are fixture execution proofs, not production runtimes. |
| P1-S02 | verified | GP / SP / RS-NORMAL / RS prove deterministic Providers without AI work; GP / RS / SPW semantic gaps remain explicit WorkOrders. SPD / RSD / GPA / GPI / SPC prove common Decision stops without an extra AI turn or authority grant. SPC stops identity conflicts before semantic publication and preserves collision-free semantic PREVIEW / deterministic readiness. |
| P1-S03 | verified | START / GP / SP / RS / SPW strict common codecs round-trip each profile's payloads and reject schema, identity, revision/context, result and required-evidence drift. SPD / RSD / GPA / GPI / SPC round-trip typed Decision, reject host input / context / operation / authority selectors and separate semantic Results from authority stops. SPC proves the combined SplitPhase wire through Service / CLI, including rehydration and replay rejection. |
| P1-S04 | verified | Public profile Start / completion / deterministic Operations expose common projections. No generic StartWorkflowRun / advanceWorkflow API is introduced. |
| P1-S05 | verified | [Three-profile consumer handoff](phase-1-consumer-handoff.md) records exact CML/generated Provided ABI/fixture hashes, released and observed upstream revisions, consumed SNAPSHOT JAR hashes, Git base plus content-bound fixture tree, and live worker evidence. Final regression is P1-C01, not another artifact-definition gap. |
| P1-S06 | verified | [Live proof](evidence/goal-phase-codex-worker-20260929.json): actual gpt-6-astra worker /root/goal_phase_codex_judgment answered the current issued review WorkOrder; Host normalization and public CLI completion retained the exact typed decision/rationale/evidence and original Start handle, continuation/revision/context. Runtime session 31299 exited0 and cleaned up. Stand-ins and discarded stdin sessions are not actual-worker evidence. |
| P1-S07 | verified | The same live proof compares an independent fixed known-candidate deterministic Provider using ReviewStepResult through the same public completion Operation. Workflow identity/revision and retained revision 1/2/3 lifecycle/progression match; both stop at missing authority. Provider has no invented worker/model/dispatch metadata. Semantic evidence prose/request digests differ legitimately; transition authority and no-Git boundary are unchanged. |
| P1-S08 | verified | JG focused-224 maps all eight generated JudgmentAction alternatives to admitted guarded progression or retained WAIT / stop-only Decision, checks all 18 declared entity edges and rejects nonedges / alternative-as-event shortcuts. GP / GPL / RS / SPW reject unknown decisions / state selectors; GPA / GPI / SPC prove authority/identity separation. Actual generated permits checks gate judgment-related closure / SplitPhase progression / reviewed-merge convergence before effects. Human Result/resume and actual Codex execution are not claimed. |
| P1-S09 | verified | START checks host-owned explicit human invocation against exact profile / identity / revision / participant / key. Existing check retained. |
| P1-S10 | verified | GP / SP / RS exercises the same Operation via direct Service and CNCF CLI JSON, using WorkOrder-selected completion and common envelopes. Existing check retained. |
| P1-X01 | verified | SCOPE adds no production runtime / CAR / SQLite / lease / broad UI / worker-infrastructure / shared-transaction gate. The one bounded actual-worker fixture proof is verified by P1-S06 without creating production dispatch infrastructure. |
| P1-X02 | verified | Service bindings are explicit in spec-owned Subsystem / Component. Production factory exposes no three-profile bound runtime, and MCP-ready services remain empty. Temporary copied documents are not a production operational surface. |
| P1-C01 | verified | Full-229 passed 526/526 across 56 suites on final tree 397303c4b65416ccfe1d369bd0df65521ff5926326a1960bc01f861a9bc1c0de; independently verified receipt ed90d2514e47449cefc567705bb0f3de8d0bc49f21038e57b6320c725ce2389b, exit 0/lock released. All 200 product/build files and three generated ABI hashes match after validation. Later closure records are documentation-only, outside the complete receipt tree. |
| P1-C02 | verified | Current checklist 34/34 verified; Phase 1 status updated to completed only after live proof, exact handoff and final regression. Historical operational/deferred inventory does not add closure gates. No production runtime/skill bundle, Git adapter, CAR publication or commit is claimed. |

## Bounded remaining work

1. **Worker mapping / Host evidence — complete** (P1-A05):
   WM consumes CNCF abstract ExecutionRequirement / ReasoningLevel and selects a
   compatible profile through a Host-owned versioned policy. Only Host-attested
   completion records external evidence; deterministic Providers invent no
   profile. Claims, resume authority and semantic judgment remain in existing
   CNCF / application boundaries. Actual bounded Codex dispatch is now verified by P1-S06.
2. **GoalPhase repair loop — complete for CAM-05 / 06**:
   focused-to-full-review publication is proved by focused-177 (CAM-04).
   GPR focused-179 implements current-candidate preparation. GPL focused-187 /
   full-188 connects typed Repair completion and fresh re-review to existing
   closure admission. No second close request, stale-evidence inheritance or
   worker-selected next state. JG now completes broader judgment guard mapping
   for P1-S08, without reopening this bounded repair loop.
3. **SplitPhase semantic-to-PREVIEW connection — complete for this slice**:
   SPW focused-197 connects SPS to generated declarations, common WorkOrder,
   public Result admission and same-instance PREVIEW. Live pre-claim checks and
   existing objective assessment are proved; deterministic APPLY is unchanged.
   Do not reopen this bounded connection for production recovery or shared
   transactions. Broader Decision / final validation items remain below.
4. **GoalPhase public Start-to-close composition — complete** (P1-S01):
   GPS binds all four generated Provided Operations to the same host ports.
   Empty-store Start through review / result / terminal and repair / re-review
   is proved by focused-201 / full-202, preserving Start identity and history.
   Do not reopen this bounded connection for production Git or restart recovery.
5. **Authority Decision projection / judgment guards — complete** (CAM-07 / P1-S08):
   expose application authority gaps through common typed DECISION, not
   WORK_ORDER Results. Phase 77.2 released Decision wire projection, not a
   submission engine. Separate the Phase 1 stop boundary from decision execution
   requiring further authority.
   SPD / RSD / GPA now prove bounded typed Decision stops. RSD passed 210 / 211;
   denied 208 / 209 remain historical and were never reused. GPA passed 216 / 217
   for proactive full-review requests and separately retained exact authority.
   GPI passed 219 / 220 for automatic review / repair authority gating and
   retained-proof read recovery; unstarted-218 is historical. SPC passed
   222 / 223 for combined SplitPhase semantic / identity options, read-only
   projection recovery and pre-AI identity stops. Failed-221 remains recorded.
   Host Decision metadata is not callable human Operations and no resume engine
   is claimed. JG focused-224 maps every generated judgment alternative to its
   permitted entity progression or stopped state, with connected deterministic
   admission gates. Full-225's denial is an execution-provenance condition,
   not a new Phase item or human-resume requirement; final validation is now full-229.
6. **Application proof / final handoff — complete** (P1-S05 / S06 / S07, P1-C01 / C02):
   actual Codex judgment and an independent deterministic Provider use the same
   contract/definition/transition semantics. The exact three-profile handoff and
   full-229 (526/526) final regression close the bounded Phase 1 scope. Public skill
   delivery is subsequent work and retains the `sm-*` naming/independence contract.

Keep denied full-178 unstarted unless platform-supported permitted recovery
exists for that exact action. That validation stop does not prohibit separately
requested implementation. The next connected SplitPhase implementation is now
present and passed focused-197 after informed permission and concrete CML
repairs, followed by full-198 (465/465, 53 suites). Denied 194 was never reused.
Those earlier slices supplied bounded GoalPhase repair, SplitPhase input/PREVIEW
and public Start-to-close proof without actual-worker or closure evidence.
The subsequent retained live proof, exact consumer handoff and full-229 now
complete Phase 1. No staging, commit, publication or production release is
claimed. Shared transactions and production restart/recovery remain separate work.

## Connected repair-loop boundary

GoalPhase v2 declares `RepairStep` as `kind = OPERATION`, the
`repair-step-capability` Required SPI and `submitStepRepair` typed completion.
It is ordinary repair execution, not a fabricated CAM judgment. The generated
Provided contract is checked before runtime binding. The CNCF receiver fix
admits this mixed ordinary / CAM artifact without weakening exact CAM matching;
Cozy's existing producer already supports the required declarations.

After admitted `repair` review completion, GPL retains the original issued
request and admitted rationale as immutable intent. It publishes repair work
only after intent and matching suspension prerequisites succeed. Typed Repair
completion is admitted before common one-shot claim consumption. It reloads
actual program and planning bytes, derives current digests and re-evaluates
retained evidence without rewriting the original submission or issued request.

Current policy always requires a fresh full-Step judgment after repair, even
for unchanged bytes with reusable full coverage. The re-review WorkOrder keeps
all retained evidence; it does not turn a prior `repair` into `accept`. Fresh
accepted completion returns to the existing exact snapshot / commit gate. GPL
proves one public close request, the same instance through revision 8, and one
terminal commit, through Service and CLI for changed and unchanged artifacts.

The host-owned default budget permits one repair. A second repair judgment
returns nonterminal WAIT without a new suspension or commit. Intent persistence
failure, missing repaired snapshot and later artifact drift leave the instance
Active, claim no Closed state and issue no unsupported work. Completion,
intent persistence, suspension and publication are separate steps under the
existing loose transaction contract; recovery / atomic rollback is not claimed.
The fixture rejects replay, stale revision / context, old-target review and
worker-supplied next-state fields. GPR separately covers planning-only changes
and evidence expiry. These proofs complete CAM-05 / CAM-06, not broader
StateMachine authority progression, actual Codex dispatch or Phase 1 closure.
