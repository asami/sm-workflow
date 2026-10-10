# R38 side-conversation handoff: remove unnecessary checks, not just duplicates

Date: 2026-10-09, Asia/Tokyo.
Status: focused read-only observations; NOT implementation, runtime validation,
independent Phase acceptance or an execution-recovery authorization.

## Human clarification to carry into the main task

The human rejected the assumption that common result validation is necessary
and merely needs to run once. Consolidating an unnecessary check preserves the
wrong behavior. Determine necessity first, remove unnecessary checks, then place
only genuinely needed consuming conditions at their actual effect boundary.

The intended distinction is:

- Consume the current input and actual Workflow context to detect a contradiction
  that affects the requested operation.
- Do not retain copies of inputs, declarations, generated information or selected
  diagnostics merely to demand later equality or prove that they did not change.
- Persistence of real Workflow state, original issued meaning and necessary
  execution context is not prohibited. Its existence does not justify arbitrary
  copy comparisons, unchanged-content proof or repeated admission.
- Calling a check state, authority, evidence, integrity or structural validation
  does not establish its necessity.

No hash-to-identity/revision substitution, whole-declaration equality, validated
mark, new verifier, comparison ledger or extra admission certificate is an
acceptable replacement for an unnecessary check.

## Current code observed in this side conversation

The main task may change these files after this observation. Read their current
contents before acting; line numbers locate evidence, not a resumption gate.

### 1. Retained worker selection is still used as a returned-copy certificate

File: `src/main/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowWorkerMapping.scala`
in `/Users/asami/src/dev2026/sm-workflow`.

Lines 79–84 refuse a completion when returned profile, mapping-policy version or
model differs from the retained Dispatch selection, and require a nonempty worker
identity. This is not merely conversion or contradiction detection at completion.
Lines 51–54 also encode/decode the issued WorkOrder and demand `decoded == issued`
before worker selection. Lines 60–63 combine real capability/risk/review matching
with rejection of repeated capability entries. These predicates require separate
necessity assessment; whole projection equality is not worker compatibility.

[Returned selection comparison](/Users/asami/src/dev2026/sm-workflow/src/main/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowWorkerMapping.scala:79).
[Whole projection equality](/Users/asami/src/dev2026/sm-workflow/src/main/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowWorkerMapping.scala:51).

### 2. Common admission still compares the copied requirement as a whole

File: `src/main/scala/org/goldenport/cncf/workflow/WorkflowProtocolV1.scala`
in `/Users/asami/src/dev2025/cloud-native-component-framework`.

Lines 333–335 require `requirement.contains(x.requestedRequirement)`, a complete
case-class equality check against the issued requirement, then validate that
copied requirement. This is broader than consuming the actual disposition fields.
Lines 327–338 also reject duplicate/unused malformed references and blank
worker/model diagnostics, and repeat requirement/observation validation.
Do not preserve these predicates solely because they are in the consumer.

[Copied requirement equality](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/workflow/WorkflowProtocolV1.scala:333).

### 3. Early and repeated result admission is still implemented

`SkillWorkResultNormalizerV1.admitC`, lines 29–34, invokes common admission after
conversion. `ContinuationSpiAdapter.Bound.admitC`, lines 45–62, loads the issue,
checks Component/target, recovers a claim, selects native conditions and invokes
common admission after its normalization hook. Loose resume invokes it again
before runtime consumption (`WorkflowProtocolV1.scala`, lines 365–372).

`WorkflowResultJsonV1` calls `_validate_envelope_c` on both encoding and decoding
(lines 24/91). Lines 94–113 reject duplicate references and diagnostic fields,
among other conditions. This is more than natural JSON/payload conversion.
Do not remove only the normalizer's call while leaving unnecessary predicates
in Bound, codecs, completion or retained-format readers.

[Normalizer admission](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/workflow/SkillWorkResultNormalizerV1.scala:29).
[Bound admission](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/workflow/ContinuationSpiAdapter.scala:45).
[Result envelope checks](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/workflow/WorkflowResultJsonV1.scala:94).

### 4. Actual native context selection exists and must be distinguished

`WorkflowIssuedWorkDataStorePersistence._load_source_c`, lines 85–93, loads the
actual stored Continuation and issued payload. Completion replaces copied
completion/evidence conditions with that native Required Operation metadata
before consuming the result (`WorkflowCompletionDataStorePersistence.scala`,
lines 268–275). `sameContextScope` compares Workflow/model scope, not workspace
or evidence diagnostic snapshots (`WorkflowProtocolV1.scala`, lines 579–580).

These observations do not make every associated predicate necessary. Actual
target/type selection, native required-condition membership, execution placement
relations and transition state/CAS must each be justified by the field consumed
and concrete wrong effect prevented, not by a blanket security label. Membership
or transported executor data is not proof that an AI/executor actually ran.

## Plan and executable-spec status are different from code completion

The main record already recognizes the incomplete consolidation and states a
corrected removal direction:

`/Users/asami/src/dev2026/sm-workflow/.codex-workflow/phases/PHASE-5/r38-result-normalization-boundary.md`

Its corrected-direction section was explicitly NOT IMPLEMENTED at observation.
It records production patch refusals, a later informed approval and another
refusal, followed by read-only impact investigation. This side handoff does not
override those outcomes, authorize replay, split a denied patch or change routes.
Re-read the current record rather than assuming its earlier approval settles
current platform execution permission.

The observed CNCF E26/E27 in WorkflowClaimedSourceConsumptionSpec still assert
checked normalizer/Bound behavior and early refusals, including blank diagnostic
profile/policy/worker/model. They cannot serve as acceptance for the corrected
removal direction just because they pass.

Local WorkflowWorkerResultConsumptionSpec E1 instead authors harmless diagnostic,
repeated/unused-reference, copied-requirement and annotation differences passing
through result conversion and the actual GoalPhase submitStepReview Operation.
E2/E3 author real consuming-boundary negatives. Its normative document explicitly
states AUTHORED ONLY, not compiled/run, and does not substitute in-memory fixtures
for managed persistence, actual executor provenance or event/CAS evidence:

`/Users/asami/src/dev2026/sm-workflow/docs/spec/workflow-worker-result-consumption.md`

## Main-task continuation guidance

1. Reconcile the current source with the corrected direction, not the initial
   "common admission once" proposal. Inspect real callers/callback effect order
   and actual native consumers before deciding which predicates remain.
2. Remove unnecessary copy/diagnostic/unchanged-content checks throughout the
   actual producer, adapter, codec, consumer and retained-reader path. Do not
   introduce substitute proof machinery or merely relocate the same refusal.
3. For each remaining consuming condition, explain the concrete wrong effect
   caused by its absence and why that boundary must handle it. Reuse the actual
   selected native context; do not add a second saved comparison artifact.
4. Verify harmless differences are accepted through the real operation and
   persisted read path. Verify actual incompatible targets, missing consumed
   prerequisites or invalid executor relations cannot cause a closing effect,
   history/event commit or claim consumption. Preserve real conversion failures.
5. Keep authored, source-reviewed and executed evidence separate. Old check-
   preservation tests or narrow fixture successes cannot establish full removal.

Carry forward the parent's same full Goal, original9/originals/all history,
original repair upper3/consumed3 and existing repair frames. Do not reset counters,
add a repair allowance, relabel historical findings or call Phase5 complete.
Preserve parent implementation / registered runner tests / read-only independent
review and the mandatory shared serial SBT route. This handoff creates no fresh
command request, runner dispatch, permission, test receipt or repair frame.

## Side-conversation work boundary

Only local source/spec/record reads were performed for this confirmation. No
subagent was contacted or created; no SBT/tests, Git mutation, permission change,
Goal API change or source edit occurred. The human subsequently requested this
handoff; this new Markdown file is the only authorized write here. This is a
focused concern check, not whole-file/whole-Phase conformance assurance.
