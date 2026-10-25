# Bounded output-limit retention and whole-Phase corrective adoption: proposal v1

Date: October 24, 2026
Status: concrete design proposal only; implementation and live adoption are not authorized by this document
Producer: `/Users/asami/src/dev2026/sm-workflow`
Consumer owner: sm-workflow-dev development

Review update: [proposal v2](2026-10-24-output-limit-validation-repair-contract-proposal-v2.md)
records the actual public091 source, the remaining native causal-binding gap,
normal Resource transport for both proposed Operations and the real382
old-runtime defect fixture. This v1 remains historical; neither version is an
implementation or live-Session authorization.

Latest design: [v3](2026-10-24-output-limit-validation-repair-contract-proposal-v3.md)
records explicit Human historical association rather than retrospective causal
proof. Earlier proposal text below is retained unchanged for traceability.

## Recommendation and existing delivery

Implement one sm-workflow-only repair with two connected parts: a versioned,
byte-preserving SBT receipt reader/writer, and an explicitly described corrective
Plan adoption for a narrowly selected whole-Phase validation frontier. Reuse CNCF
process/capture values, native mechanical storage, independent host evidence,
ordinary typed Git/SBT UoW execution and the existing corrective-Step machinery.
No CNCF, Core, Cozy, SimpleModeler, sbt-cozy or development-workstation source
change is proposed. No new runtime, process registry, AI runner or global lock
is needed.

The completed timeout-three-choice work remains applicable; its four affected
suites/34 tests, two installed-command scenarios, publication and review are not
restarted. See `2026-10-24-timeout-recovery-choices-handoff.md`. The additional
generated-ABI failure remains separate, recorded and unresolved. This proposal
is not evidence that the new route works.

## Existing-route assessment

Read-only public 092 describes UNKNOWN source2161/frontier2162/intent2162,
Plan `phase-6.5-three-workflow-optimization@2`, Session
`e89e8ba92d074262e4c8a10ef11d1183`, whole-Phase check `repository-full`, ordinal8,
attempt0, candidate HEAD `20183694b0ef3a3b33da5a8470475db159a452f0`, tree
`aca30272ae087bc0eee04542ea584bdf237efd13`. All six accepted Steps remain.

| Existing route | Actual applicability |
| --- | --- |
| Validation recovery AuthorizeNewAttempt | Supports only another same-candidate selected check after fresh exact Human direction. It does not admit source correction or a new Step. |
| Validation recovery Defer | Preserves UNKNOWN and permits continued read-only diagnosis/preparation. No execution. |
| Confirmed-timeout LimitedRepair / failed-Phase repair adoption | Requires an actual intent/result/final Decision receipt. 092 has no such result and is ineligible. The old 086 receipt is a different source. |
| Remaining-Plan adoption | Excludes unknown native effects and this completed-prefix whole-Phase validation frontier. |
| Whole-Phase candidate recovery | Requires PhaseInspection before the selected check, not the later PhaseValidation intent. |
| Ordinary Plan refresh | Locks the Phase scope once this candidate/check has been issued. It cannot be used to bypass its pending intent. |

Thus no existing public contract safely admits a corrective Step before another
whole Test at 092. Repeating a roughly44-minute check merely to obtain a different
failure classification is not the recommendation. Preparing the concrete source
repair/proposal is already reversible work and needs no preliminary LimitedRepair
authorization. The new decision occurs only when that concrete Plan is adopted.

The diagnostic handoff, original 091 stderr, 092 response and completion map are
under the consumer's existing task root:

`/Users/asami/src/dev2026/sm-workflow-dev/target/sm-workflow/runtime-phase-6.5-20261023/target/sm-workflow/tasks/phase-6.5-01a1555d-b4a7-7be3-8232-5396c2f5e293/`

`inputs/p65-completion-requirements-and-evidence-gaps-092.json` is an analysis
map, not a new Plan or acceptance receipt. Its original30 Phase conditions plus
one Step condition, whole validation, one integrated independent Phase review,
ordinary local closure and Phase7 handoff remain required.

## 1. Byte-preserving native receipt

### In-memory ownership

Use existing CNCF `ProcessExecutionResult`, `ProcessExecutionCapture` and
`ProcessExecutionTermination`; introduce no substitute process result semantics.
`WorkflowSbtCapture` is only their closed JSON persistence/projection adapter.
Extend private `RepositorySyncNativeMergeValidation.Receipt` with a final
`processObservation: Option[ProcessExecutionResult] = None`. None identifies
historical receipt data, not a missing new observation to be guessed later.

New typed SBT results carry Some(actual result). Check the selected target and
operation through existing attempt admission, then preserve the actual result.
Tagged SBT currently has no WorkArea/artifact projections; require empty artifacts
for this writer rather than silently dropping an unexpected artifact result.
No `ProcessExecutionRequest` is constructed in sm-workflow.

### Exact new wire

New typed SBT receipts use the following complete closed record. The version is
on the nested receipt; enclosing native Goal validation remains v2 and the
RepositorySync state envelope retains its current identity.

```json
{
  "schemaVersion": "sm-workflow.native-sbt-validation-receipt.v2",
  "identity": "repository-full",
  "sbt": {"kind":"test","outcome":"incomplete","selection":null,"reason":null},
  "process": {
    "termination": {"kind":"output-limit-exceeded","stream":"stderr"},
    "stdout": {"contentBase64":"","byteCount":0,"truncated":false},
    "stderr": {"contentBase64":"4oI=","byteCount":2,"truncated":true},
    "artifacts": [],
    "elapsedMillis": 100,
    "safeProgramIdentity": "isolated-example"
  }
}
```

The example is shaped data, not a reconstructed 091 receipt. Its stderr is the
first two bytes of a three-byte UTF-8 code point. Store each byte stream once.
Use standard RFC4648 Base64 with padding, no whitespace and canonical re-encoding;
it is byte transport, not a hash, identity, integrity or authority protocol.
Byte counts are nonnegative JSON Long integers equal to decoded retained bytes.
They are not the unknown total original stream sizes. Elapsed milliseconds are
nonnegative; program identity follows the existing canonical identity rules.
All listed fields are mandatory; unknown keys, missing keys, explicit null in
required fields, noncanonical Base64 and contradictory facts refuse. Existing
SBT Facts retains its existing explicit optional selection/reason encoding.

Termination is a closed tagged codec for the existing CNCF cases: `exited` with
an integer `exitCode`; `output-limit-exceeded` with `stream=stdout|stderr`; or
the parameterless kinds `not-started`, `launch-failed`, `timed-out`, `cancelled`,
`termination-unconfirmed`, `artifact-limit-exceeded`. No exit143 is invented for
an incomplete case. Preserve only the exit code actually represented by Exited.
The observed diagnostic exit143 remains separately attributable diagnostic data.

For output-limit admission require typed SBT Incomplete, the named affected
stream truncated, valid capture lengths and no verified TestOnly success.
Both streams may be truncated; the termination stream is the actual CNCF value,
not inferred from which flag was first inspected. Also retain a typed
TerminationUnconfirmed result with truncated bytes, without assigning an affected
termination stream or confirming settlement. That result grants no corrective
adoption under this proposal. Other nontruncated existing terminal cases keep
their current admission rules. Contradictory Exited/success with truncation,
missing capture, mismatched counts/operation or invalid successful text still
refuse. A receipt can be valid incomplete evidence without being successful.

### Text and compatibility

New persistent v2 receipts omit old stdout/stderr text fields. Their private
in-memory text accessors and public diagnostic text are derived from actual
bytes, never persisted a second time. For complete captures retain the existing
strict UTF-8/no-NUL rule. A truncated capture is decoded incrementally with
REPORT; an incomplete trailing UTF-8 sequence of at most three bytes may remain
undecoded. Preserve those bytes in Base64 and expose the exact
`trailingUndecodedBytes` count. Do not insert U+FFFD or drop a malformed interior
sequence. Interior malformed UTF-8 or NUL gives `textStatus=unavailable` and no
diagnostic text; raw incomplete evidence still remains. Empty public legacy
text in that case is explicitly accompanied by unavailable metadata, never
treated as proof of an empty or complete original stream.

Append optional `processObservation: Record` to public
`GoalPhaseNativeCheckReceipt` and `RepositorySyncNativeValidationReceipt`.
It projects the exact process record plus per-stream derived
`textStatus=complete|prefix|unavailable` and `trailingUndecodedBytes`.
Existing required stdout/stderr fields remain diagnostic strings. Incomplete
termination, SBT Incomplete, truncation and textStatus are always exposed
together; the projection cannot mark a prefix complete. Ordinary successful
check admission requires valid complete captures regardless of displayed text.

Reader dispatch is explicit: absent nested schemaVersion reads the exact
historical receipt shape and sets processObservation=None; the exact v2 tag
uses the new strict decoder; every other tag refuses. A malformed v2 record
never falls back to the old reader. Encoding a decoded historical receipt
preserves its historical shape/text/facts, including legacy untyped receipts.
No stored row migration or backfill is needed. Old binaries must refuse new
records; rollback to an old binary after v2 writes is not qualified. Forward
qualification must reopen old receipts/Session prefixes across the installed
runtime switch and then reopen new receipts in a separate process.

For newly admitted output-limit results use native payload outcome `incomplete`,
not `failed`. Add explicit pure `IncompleteChecksObserved` and CML
`checks-incomplete`/`phase-checks-incomplete` edges into Decision with an
incomplete reason. Existing failed results and timeout proofs keep their current
state/wire semantics. RepositorySync retains the incomplete receipt and waits;
it does not receive the new Goal corrective-adoption capability.

## 2. One concrete corrective-Plan decision

### Operations and DTOs

Add neutral public Operations; do not extend a failed-result proof to UNKNOWN:

- `GoalPhaseService.describeGoalPhaseValidationRepair`.
- `GoalPhaseService.adoptGoalPhaseValidationRepair`.

Their CML DTOs are:

```text
GoalPhaseValidationRepairDescriptionInput(
  sessionId: String, proposal: Record, priorInvocation: Option[Record])
GoalPhaseValidationRepairDescriptionResult(
  sessionId: String, outcome: String, message: String,
  componentInstance: String, target: Record, choices: Vector[String],
  adoptionOperation: String)
GoalPhaseValidationRepairAdoptionInput(
  sessionId: String, target: Record, proposal: Record,
  choice: String, rationale: String, reference: WorkflowArtifactReference)
GoalPhaseValidationRepairAdoptionResult(
  sessionId: String, accepted: Boolean, outcome: String, message: String)
```

Description is history/configuration-only preparation: no AI, Git/File/SBT,
workflow write or Human question is needed to make the proposal reviewable.
It returns `adoption-required`; actual execution authority still belongs to
normal host grants and separately issued work. Add a preparatory guidance link
to this operation in existing validation-recovery messages without changing
that operation's two-choice UNKNOWN target or three-choice timeout target.

There are exactly two closed source kinds under target schema
`sm-workflow.goal-phase-validation-repair-target.v1`:

1. `output-limited`: exact current unsuspended whole-Phase Decision with the
   genuine v2 output-limit result, immediately adjacent final incomplete receipt,
   all Steps accepted and exactly one ordinary Test check. Reconcile the entire
   contiguous actual attempt chain, intent/result/final receipt references and
   revisions. CNCF's actual returned typed variant is the termination proof;
   TerminationUnconfirmed and diagnostic stdout are not substitutes. Description
   priorInvocation must be absent. Choices are `AdoptRepairPlan`, `Defer`.
2. `unobserved-result`: exact current unsuspended PhaseValidation pending intent
   after all Steps are accepted, one ordinary Test check and no result/final
   validation receipt for its latest attempt. Use the existing native unresolved
   chain reconciler and require the current saved record is that actual intent;
   no stored Uncertain result, dependency/metadata selection or continuable
   NotExecuted/admission-failure branch is admitted. For 092 these are exactly
   source2161/frontier2162/intent2162, not old2160. Description priorInvocation
   is mandatory and the choices are exactly
   `AdoptRepairPlanAcceptingUnresolvedEffects`, `Defer`.

The new target has exactly these top-level fields:
`schemaVersion`, `source`, `plan`, `completed`, `repairProgress`, `generation`,
`candidatePaths`, `closurePolicy`, `captured`, `correctiveChecks`, `processPlan`.
All are required and non-null. `plan`, `completed`, counters, paths, closure and
captured raw/effective proposal reuse the existing native models and guards.
`processPlan` is the current resolved opaque normal process-plan JSON string,
compared as parsed JSON as in whole-Phase candidate recovery; sm-workflow does
not interpret executable/argv/limits from it.

Both sources bind Session, workflow identity/revision, participant, original
base, Plan identity/revision, candidate HEAD/tree, generation, original validation
source revision, exact current frontier revision, unique check identity/ordinal,
latest attempt ordinal and actual intent identity/revision. The output-limited
source additionally binds result and final validation identity/revision,
`termination=output-limit-exceeded`, actual stream and `outcome=incomplete`.
The unobserved source instead contains the exact original public recovery target,
`resultDisposition=unobserved`, and the complete supplied priorInvocation below.
It has no fake result, Decision, timeout source or KnownFailedPhase.

For avoidance of field-name ambiguity, use these exact source records, with
all fields mandatory, no extension keys and no explicit null:

```text
ReceiptRef(identity: String, revision: String)
OutputLimitedSource(
  kind = "output-limited",
  sessionId: String, workflowIdentity: String, workflowRevision: String,
  participant: String, planIdentity: String, planRevision: String,
  base: O.Snapshot, candidate: O.Snapshot,
  sourceRevision: String, frontierRevision: String,
  step: String, checkIdentity: String, checkOrdinal: Int, attempt: Int,
  intent: ReceiptRef, result: ReceiptRef, validation: ReceiptRef,
  termination = "output-limit-exceeded", stream: "stdout" | "stderr",
  outcome = "incomplete")
UnobservedSource(
  kind = "unobserved-result",
  sessionId: String, workflowIdentity: String, workflowRevision: String,
  participant: String,
  recovery: GoalPhaseValidationRecovery.RecoveryTarget,
  resultDisposition = "unobserved",
  priorInvocation: ValidationPriorInvocation)
```

UnobservedSource.recovery must have no knownFailedPhase, dependency or
terminatedTimeoutPhase extension; its shared Session must match sessionId.
The existing recovery target supplies its exact Plan/base/candidate/check/
source/frontier/intent/attempt fields; its native latest intent reference is
reconstructed and checked against that target. References and revisions follow
existing canonical native reference/integer-revision validation, and all nested
new records use exact-key decoding. Choices and priorInvocation eligibility
are selected from the actual native source before comparing the supplied target;
failure to prove one source never falls back to the other.

### Prior invocation evidence and residual risk

The unobserved source's priorInvocation (ValidationPriorInvocation above) is the
exact closed record:

```text
schemaVersion = sm-workflow.validation-prior-invocation.v1
invocationIdentity: String
resultReference: WorkflowArtifactReference
state: terminated
exitCode: Int
observedAt: actual ISO8601 instant
managedDescendants: unobserved
diagnosticReferences: Vector[WorkflowArtifactReference] (1 to16, unique)
```

It describes the owner caller invocation only. The trusted independent host must
actually observe that invocation's terminal tool result and its retained source,
and verify that it belongs to this exact check/intent/candidate. It supplies this
observation together with the real Human answer through the existing independent
evidence provider. A supplied Boolean, PID scan, passive preparation, copied log
or AI assertion is not proof. No PID is used as a durable execution identity.
Do not backdate observedAt; it is the actual observation time. Existing references
are reused without copying full output into this target.

The program proves native history/selection/frontier equality, absence of a saved
result/successor/retry direction, accepted prefix, complete exact proposal, host
scope/program policy, participant and independent evidence binding, and wins one
native CAS. It does not prove the old test outcome, absence of old external/file
effects or managed-descendant settlement. Those remain UNKNOWN/unobserved.
An observed still-live owner or a stored TerminationUnconfirmed result refuses
this branch. Missing terminal owner evidence also refuses.

The deliberately new Human meaning is: accept those explicitly disclosed
unresolved prior effects/descendant observations and adopt this one concrete
corrective Plan, without asserting settlement or waiving validation. That is
the single `AdoptRepairPlanAcceptingUnresolvedEffects` decision. It is not an
automatic safe-retry assertion; normal fresh candidate/ownership checks and
sbt-native workspace locks remain. If the person cannot accept that residual
risk, Defer remains available and further authoritative observation is required.
Do not turn the new branch into permission for arbitrary unknown Git/stage/
commit/push/closure or all types of failed process.

### Adoption, persistence and replay

Reuse failed-Phase corrective eligibility: same Plan identity/Phase/repository,
repair limit, Phase checks/review classification and exact raw/effective
acceptance; every original Step and accepted commit/review remains identical.
Only revision changes and exactly one nonempty corrective Step is appended,
depending on every original Step. Its exact ownership entries must already be
in current candidatePaths, its checks must be fixed host selections and its
Step commit program must already be mapped in the complete closure policy.
The captured concrete proposal identifies actual error/hang-investigation work,
paths and affected verification; missing active-test identity stays unknown.
No future unspecified Plan is adopted, and no old Step is reopened.

Use new evidence purpose `goal-phase-validation-repair-adoption`, binding
`componentInstance` and `validationRepairAdoption(target, answer)` with exact
choice/rationale/participant/reference and independently verified issuer/time.
Passive `prepareEvidence(family=GoalPhase, kind=validation-repair-adoption)`
only serializes that actual input and description to existing StartupImport.
It does not issue consent, verify terminal facts, call AI or provision storage.
There is no preceding LimitedRepair call or second question for the same adoption.

Adoption appends one native mechanical Direction, schema
`sm-workflow.goal-phase-validation-repair-adoption.v1`, reference
`<session>:ordered-validation-repair-adoption:<frontierRevision>@1`.
Use pure Unit effect and one guarded native CAS. CML adds the explicit
`validation-repair-adopted` edge from PhaseValidation to Implementation for the
unobserved source and from Decision to Implementation for output-limited.
Do not fabricate a Decision in between. The pure controller entry has distinct
guards for those two sources, sharing only preserved-Plan/appended-Step checks
with existing failed repair. Clear active candidate/check/review/closure
projections; generation increments once, while accepted Steps, original base,
all ordinary/cumulative repair counts/limits and old results remain unchanged.

Separate ordinary Advance first consumes that exact adjacent Direction and
reconstructs its original history prefix. No old intent/result is overwritten,
inserted or completed. It reuses the existing append-only historical path/closure
policy rules, preserves all finding/issues/prior review ledgers and uses current
authority for future effects. Before issuing the first corrective ImplementStep,
reuse `GoalPhaseNativeValidation.preconditionsC` against the original candidate,
original controller/validation source and original document/recording scope.
This is the existing typed root/status/head/index/PendingPaths UoW check, not
an application argv or process scan. Also compare the current resolved opaque
process plan with the admitted target. A changed repository/candidate/index,
foreign dirt or changed live host plan refuses before issuing semantic work;
these observations do not infer old success or descendant settlement. Historical
Direction reconstruction uses its recorded plan and policies; later config
changes do not retrospectively rewrite completed history. Run this pre-issue
check only when native history has no already-issued corrective work request.
Once that request is retained, ordinary work/result traversal reuses it and
must not compare its edited worktree to the old pre-repair candidate or issue
another request. No new private once-only flag or registry is introduced.
Then it issues only the new corrective ImplementStep, followed by ordinary
fresh candidate/check, independent Step
review where required, local Step commit, required whole Test, one integrated
independent Phase review and ordinary local closure. Old accepted operations
and successes are not reissued. Reopening a saved Direction/work/result is
observation, not a replay. Stale evidence and concurrent/duplicate adoption fail.

## 3. Finite normal host profile

The selected382 runtime contains the existing public SbtTestOutputParameters
classes. CNCF already resolves numeric properties from current UoW parameters:
`cncf.sbt.test.stdout.maximum-bytes` (default67108864) and
`cncf.sbt.test.stderr.maximum-bytes` (default8388608). Each effective ceiling is
the minimum of the property/default and admitted host limit. 091's local host
still selects stdout2097152/stderr131072, with neither property explicitly set.
Its receipt property is absent (Goal default16MiB); Resource read is16MiB.

For the isolated qualification and proposed single-check consumer profile use:

| Existing normal setting | Selected value |
| --- | ---: |
| selected repository-full host stdoutBytes and stdout test property | 33554432 (32MiB) |
| selected repository-full host stderrBytes and stderr test property | 8388608 (8MiB) |
| workflow.mechanical-receipt.max-bytes | 67108864 (existing maximum64MiB) |
| executionTimeoutMillis | 5400000, unchanged |
| launchTimeoutMillis / terminationGraceMillis | 10000 / 1000, unchanged |
| artifactCount / artifactBytes / workAreaBytes | 1 / 2097152 / 4194304, unchanged |
| Resource read | 16777216, unchanged unless actual complete business input needs more |

This is a deliberately finite host stdout ceiling below the global64MiB
default; the global defaults are not changed. 40MiB retained capture needs at
most55924056 Base64 bytes plus fixed field overhead, leaving about10.67MiB of a
64MiB receipt for the native current/next histories, facts and envelope. The
actual complete serialized envelope is what the existing CNCF bound checks.
Qualify a same-shape2162-event history and the maximum-profile captures through
the real encoder before selecting this profile; no universal capacity guarantee
for arbitrary histories/multiple checks is claimed. If that actual envelope
does not fit, do not silently raise the64MiB hard limit or drop history/output;
return the precise separate capacity gap. No result is turned successful.

Persisting raw bytes only once avoids JSON control-character expansion and
duplicate stored diagnostic text. Presentation may derive text without changing
native receipt bytes. Complete output remains subject to strict text validity.
WorkArea/artifact ceilings do not have to equal stdout/stderr: tagged SBT has no
WorkArea output projection, and process capture is stored by the existing native
receipt owner. No capture artifact workaround or second datastore is introduced.

Actual consumer host edits stay with its owner after delivery and are scoped
to its normal config, not a product-source change in this proposal. Resource
and receipt capacity are separate existing settings; no new byte-limit property,
outer timer, timeout adapter or per-check AI agent is added. Git remains at its
separate1MiB default / explicit finite16MiB maximum policy unchanged.

## 4. Exact proposed authored files

All paths below are relative to producer `/Users/asami/src/dev2026/sm-workflow`.
Existing unrelated deltas in these and other files are preserved. The eventual
Fix Manifest must freeze current bytes and only the prescribed delta.

| File | Prescribed delta |
| --- | --- |
| `src/main/cozy/sm-workflow.cml` | Two public Operations/four DTOs, optional public receipt observation, explicit incomplete/adoption edges and evidence preparation contract. Preserve existing workflow identities/revisions, states and Required operations. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowSbtCapture.scala` (new) | CNCF value JSON codec, byte/text projection and strict validation above. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncNativeMergeValidation.scala` | New optional native observation, explicit dual nested receipt codec, actual output-limit admission and complete-success guards. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeValidation.scala` | Persist/reopen typed incomplete output-limit, explicit incomplete outcome, shared read-only actual attempt/source proof for the new owner; no UNKNOWN backfill. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseOrderedProgression.scala` | Explicit incomplete event and guarded new corrective adoption entries; preserve existing failed/timeout/refresh guards. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseValidationRepairAdoption.scala` (new) | Closed source/target/Direction, two Operations, exact binding, CAS and retained traversal. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeWorkContinuation.scala` | History-only source selection, new public methods and Direction dispatch before ordinary effects. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeRun.scala` | Optional distinct trusted installation, validation and native storage operation admission. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeServiceAssembly.scala` | Register the two actual public Operations. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowRuntimeParameters.scala` | Bind the new trusted evidence installation without a new configuration property. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowExternalEvidencePorts.scala` | Admit the new purpose through the existing host evidence store/issuer. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowLauncherAssembly.scala` | Add the two selected public entries to the actual publication set. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/NativeExternalEvidence.scala` | Add the distinct purpose and closed binding selector under existing v7 envelope; no reinterpretation of old evidence. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/preparation/NativeExternalEvidenceInput.scala` | Register only the new GoalPhase evidence kind. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/preparation/NativeValidationRepairPreparation.scala` (new) | Passive exact description/proposal/Human binding preparation. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/NativeWorkflowOperationOutput.scala` | Project actual optional process observation and marked diagnostic prefix. |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseValidationRecovery.scala` | Guidance to prepare a concrete repair without broadening existing recovery choices/authority. |
| `docs/spec/normal-typed-sbt-validation.md` | Explicit v2 byte receipt, dual reader, incomplete/result and finite profile contract. |
| `docs/spec/goal-phase-validation-repair-adoption.md` (new) | New closed public adoption semantics and examples. |
| `docs/spec/goal-phase-validation-recovery.md` | Link the optional concrete preparation/adoption route; retain all current recovery guards. |
| `docs/spec/normal-host-preparation-operations.md` | Actual new passive kind/DTO/authority distinction. |
| `src/main/catalog/car/org/simplemodeling/textus/textus-sm-workflow.yaml` | Only catalog changes produced by ordinary generation, if the new public contract changes that generated file. |

Proposed executable-spec files:

| File | Required evidence |
| --- | --- |
| `src/test/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowSbtCaptureSpec.scala` (new) | Exact raw/UTF-8/codec/termination/old-reader and maximum-envelope tests. |
| `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeTestOnlyValidationReceiptSpec.scala` | Both enclosing codecs reopen v2 and legacy; preserve strict successful selection and malformed controls. |
| `src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseValidationRepairAdoptionSpec.scala` (new) | Closed binding/source, pure transitions, immutable prefix/counters and rejection matrix. |
| `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeGoalOutputLimitValidationCliSpec.scala` (new) | Real isolated installed typed-SBT output limit, durable result, fresh-process reopen and output-limited corrective route. |
| `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeGoalUnobservedPhaseRepairAdoptionCliSpec.scala` (new) | Old-runtime missing-result fixture, current-runtime UNKNOWN retention, one combined exact Human direction, fresh-process continuation, candidate drift and stale/concurrent controls. |
| `src/test/scala/org/simplemodeling/textus/smworkflow/GoalPhaseGeneratedAbiSpec.scala` | Exact two new inputs/results, refusal of missing input and27-operation inventory. Preserve/report existing unrelated failure; no weakened assertion. |
| `src/test/scala/org/simplemodeling/textus/smworkflow/SmWorkflowGeneratedHelpSpec.scala` | Public names/help follow generated contract. |
| `src/test/scala/org/simplemodeling/textus/smworkflow/ComponentPackagingDescriptorSpec.scala` | Published operation/input inventory follows the generated descriptor. |
| `src/test/scala/org/simplemodeling/textus/smworkflow/GoalPhaseCmlDerivationSpec.scala` | Ordinary actual producer derivation and unchanged13 states/six Required roles; new Provided inventory. |
| `src/test/probes/GoalPhaseCmlDerivationProbe.scala` | Exact27 Provided names while preserving workflow version and derivation count. |
| `src/test/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowDirectOperationSpec.scala` | Bring the explicitly affected ordinary publication assertion to the actual generated set; no blanket skipped inventory check. |

Ordinary `cozyGenerate` regenerates `target/scala-3.3.8/src_managed/main/scala/`
and generated ABI resources from the CML. The new DTOs, SmWorkflowComponent,
GoalPhaseRunLifecycle and GoalPhase Provided/workflow/producer ABI outputs are
generation products, never hand-edited. No new product main or manual test
registration is permitted. If implementation exposes another authored path or
an upstream source requirement, return that exact change for scope disposition;
do not silently expand this inventory.

## 5. Acceptance and refusal criteria

Use Given/When/Then behavioral specs and applicable current labels. Reuse prior
successful timeout evidence; no whole repository run is required for this producer
repair. The consumer still owes its original whole validation and Phase review.

1. Shaped receipt tests preserve every byte, counts, truncation, affected stream,
   elapsed time and original incomplete outcome. Include a split2/3-byte UTF-8
   boundary, malformed interior, NUL, both truncated streams, stdout and stderr
   limits, invalid counts/Base64, output-limit success contradiction, TestOnly
   selection contradiction and unconfirmed termination. Complete valid success
   and nonzero failure keep their exact typed meanings. Preserve old encoding
   and both Goal/Repository enclosing codec roundtrips.
2. Maximum profile40MiB raw capture plus representative2162-event native envelope
   fits the actual selected64MiB encoder; assert actual serialized bytes. Include
   JSON-control/multibyte complete text and raw incomplete data. No second stored
   copy of text; actual over-capacity fails rather than truncating history.
3. A real finite isolated Test emits past a small selected stderr limit through
   ordinary `sbt_` UoW; a typed output-limit result is persisted, CLI returns a
   truthful incomplete Decision and a fresh CLI process reads identical raw data
   without another SBT launch. OutputLimit and deadline expiry remain distinct.
4. Create a separate pre-upgrade isolated Session using the currently installed
   runtime and an existing acknowledgement fault boundary to retain a missing
   result after its real process has ended. Keep the original native prefix and
   old complete receipts. Switch only its fixture runtime to the candidate
   installation, then describe the same UNKNOWN; do not use actual091/092.
   This validates existing-definition compatibility across actual publication,
   not merely a case-class conversion or fresh Start after upgrade.
5. Read-only diagnosis/description/preparation performs no Human prompt, AI,
   SBT/Git effect or native write. A new concrete appended-Step proposal and
   independently observed fixture terminal invocation/Human answer use one
   combined unknown-effects adoption. Defer does nothing. Adoption writes
   exactly one adjacent Direction and no old result/Decision. Old UNKNOWN,
   original six accepted prefix facts, base and counters remain byte/semantic
   equivalents as appropriate; generation increases exactly once.
6. Reopen in another installed CLI process, consume Direction without replay,
   issue only the appended corrective work, perform actual scoped repair/check,
   independently accepted review, local Step commit and new whole Test through
   existing normal calls. This fixture's new work results are genuine new
   results, never resubmission of an accepted old result. Final integrated review
   and closure remain required; observe them in the isolated scenario rather
   than treating adoption/check success as completion.
7. Refuse wrong participant/issuer/reference/time/target/proposal, changed raw or
   effective acceptance, lost/reordered original Step, changed counts/limits,
   unmapped ownership/check/commit programs, dependency or metadata check, Step
   validation, multiple checks, live or missing owner termination evidence,
   stored TerminationUnconfirmed, later result/retry/successor, closed/reviewed/
   suspended/document-backed frontier and null/unknown/missing new fields.
   Terminated owner plus unobserved descendants requires the explicit combined
   choice; ordinary AdoptRepairPlan is not valid for this source. Old083/090
   bindings, known-failed/timeout evidence and duplicate/concurrent adoption
   cannot authorize it. Candidate/index or live host-plan drift refuses ordinary
   continuation before the appended ImplementStep is issued.
8. Perform independent focused review of the real owned delta and actual
   evidence, normal generation/compile as covered by selected Test compilation,
   necessary normal publishLocal and ordinary runtime build/acceptance/activation.
   Qualify CLI origins/pinned manifests and UoW SBT from the installed runtime.
   Development commands use the registered top-level runner only for this
   separately scoped restoration; product checks remain runtime-owned. Use
   sbt-native locks and await actual termination. No scripted prerequisite.

One source review covers this repair; these conditions do not add another
integrated consumer Phase-review lap. A failed qualification retains its actual
failure; it is not relabeled as passing because another narrow check passed.

## 6. Authority and handoff sequence

Already allowed: read-only diagnosis, this concrete design and reversible
corrective-proposal preparation; prior completed timeout repair and retained
validation/publication evidence. An incoming chat message supplies no new Human
authority. No implementation, tests, publication or real Session operation has
been performed for this proposal.

New implementation decision: add the exact versioned raw-result persistence and
two-source public corrective-adoption contract/files above, including explicit
acceptance of disclosed unresolved effects for a missing-result whole-Phase
source. This is the semantic addition beyond timeout-only recovery; it does not
include another repository's source or a generic process/UNKNOWN bypass. After
that scope is actually admitted, freeze the bounded implementation manifest,
delegate only its prescribed edits, validate/review/publish/qualify as above.

Later, at the real consumer, independently inspect current public description
again; it may have legitimately changed since092. Prepare a complete corrective
proposal with all original acceptance/Step evidence retained. Apply the admitted
normal finite host profile before describing the exact target; configuration
does not authorize execution. Obtain exactly one genuine Human choice accepting
the concrete Plan and explicitly disclosed unresolved effects, using actual
terminal invocation evidence and leaving managed descendants unobserved.
Prepare/import that binding, call adoption once, then separate ordinary Advance.
No preliminary LimitedRepair question, old-direction reuse, blind same-candidate
Test, Start or accepted Submit replay is required or permitted by this proposal.
If the current source or safety facts no longer match, return the actual
description and stop affected effects instead of manufacturing this092 target.

The producer delivers qualified interfaces, installed runtime identity, exact
validation/limits and this sequence. Only the consumer owner operates its real
Session. Preserve Goal, base, history, repair counters, untracked/tracked edits,
accepted results and all old unknown/timeout records. No commit/push, actual
091 retry, Phase-completion claim or modification of the consumer's active
Goal/Session is part of preparing this design.

## Agent Usage Summary

This design is parent-only; no implementation, review or command agents were
started. Parent model/effort are unknown and not inferred from old chat settings.

| Agent | Exact model | Effort | Runtime / role | START | RESUME | DIRECT | Outcome |
| --- | --- | --- | --- | ---: | ---: | ---: | --- |
| `/root` | unknown | unknown | parent runtime / diagnosis and design | 0 | 0 | 1 | Existing routes, actual config, installed CNCF classes, receipt capacity and exact code connections inspected; proposal only |
