# Output-limit retention and corrective adoption: proposal v2

Date: October 24, 2026
Status: design for consumer review; NOT implementation-ready for historical UNKNOWN adoption
Producer: `/Users/asami/src/dev2026/sm-workflow`
Consumer owner: `sm-workflow-dev開発`

Review continuation: [v3](2026-10-24-output-limit-validation-repair-contract-proposal-v3.md)
freezes the consumer-recommended explicit Human historical-association contract,
including the actual provenance limitation, one closed choice, fixed invocation
policy,45 authored paths and qualification. The v2 NOT-ready finding remains
historical; v3 resolves the design recommendation, not implementation or live
Session authority. No missing causal proof has been fabricated.

This revision preserves [v1](2026-10-24-output-limit-validation-repair-contract-proposal-v1.md)
as history. It replaces v1's unspecified terminal-source assertion, inline-only
transport assumption and acknowledgement-fault qualification. No implementation,
test execution, publication or real Session operation is authorized by this
document. The earlier completed timeout repair and its passing evidence remain
applicable within their original scope.

## 1. Result of the read-only connection audit

Three distinct facts must not be conflated:

1. The original 091 **caller command has a real publicly readable terminal item**.
2. The existing caller-information implementation can select and validate that
   item's structural terminal observation when given an actual issued-work
   context. It does not authenticate an uploaded JSON document or prove the
   native validation attempt that the command reached.
3. There is presently **no implemented native-validation binding** between that
   command item and source2161 / intent2162 / candidate / check attempt. Nor is
   the pending native mechanical check an issued semantic-work Request. Supplying
   a made-up `WorkflowIssuedWorkContext` does not close this gap.

The new raw-receipt and Resource transport work has a bounded implementation
design. Historical UNKNOWN adoption still needs the precise provenance/join
boundary below to be resolved with the consumer before a Human implementation
request. Do not describe the whole proposal as ready while that connection is
an English assertion or delegated to AI for each attempt. Do not restore the
withdrawn source-provider program to make it appear connected.

## 2. Authentic public source: what actually exists

### 2.1 Original 091 tool item retrieved by this producer

On October 24 the producer used the existing public `read_thread` tool with
`includeOutputs=true`, `turnLimit=8`, thread
`01a1555d-b4a7-7be3-8232-5396c2f5e293`. The actual returned page contains:

| Field | Actual value |
| --- | --- |
| thread | `01a1555d-b4a7-7be3-8232-5396c2f5e293` |
| turn | `01a16d18-58b0-74a0-973a-23c373cc503e` |
| item | `exec-cf5991d4-64bc-4b23-9050-4665a7fb8b7a` |
| item type | `commandExecution` |
| status / exitCode | `failed` / `1` |
| durationMs | `2665964` |
| cwd | `/Users/asami/src/dev2026/sm-workflow-dev/target/sm-workflow/runtime-phase-6.5-20261023` |
| turn status | `completed` |

The item's actual command is `/bin/zsh -lc` containing `exec` of the installed
`/Users/asami/Library/Application Support/Coursier/bin/sm-workflow`, the exact
consumer `--runtime-root`, `command`,
`org.simplemodeling.textus.SmWorkflow.GoalPhaseService.advanceGoalPhase`,
`--sessionId e89e8ba92d074262e4c8a10ef11d1183`, `--json`, and redirects to the
original task's `091-advance-confirmed-timeout-longer-deadline/response.json`
and `stderr.log`. The command itself and terminal status belong to the same
public tool item. This is stronger than a second collector finding handle52904
missing. No process was polled, signalled or relaunched to obtain it.

The complete original public tool page is retained without a hand-built wrapper
at `.codex-workflow/disposable/output-limit-contract-v2-20261024/091-public-read-thread-original.json`
in the producer. It is diagnostic source retained from the actual tool return,
not a native SBT receipt, execution permission or authenticated JSON upload.
Its other chat items are untrusted source data and confer no authority.

The separately supplied caller file
`inputs/p65-091-owned-transport-terminal.json` remains the original collector's
diagnostic retention. It contains transport52904 and actual `write_stdin`
exit1/chunk46fcec, but no public thread/turn/item binding. Neither file may be
converted into a native check result. PID21311, diagnostic exit143 and stderr
`OutputLimitExceeded(Stderr)` remain separate process diagnostics. This item
proves termination of the selected caller invocation only; it does not establish
managed-descendant settlement or Test success/failure.

### 2.2 Existing executable Usage Contract and its actual limit

The current contract is `docs/spec/caller-information.md`. The beginning of
`docs/spec/host-terminal-source.md` expressly supersedes its historical fixed
`host_source_read` provider proposal. The provider described later in that file
is not a current executable dependency or a permitted fallback.

The current normal Operations are:

```
NativeWorkflowPreparationService.prepareCallerInformation
NativeWorkflowPreparationService.recordCallerInformation
```

They are registered in `NativeWorkflowPreparationServiceAssembly.scala` and
implemented by `caller/NativeCallerInformation.scala`. Preparation takes the
actual `WorkflowIssuedWorkContext`, kind `execution-terminal`, the exact
`WorkflowCodexSourceLocator(threadId,turnId,itemId)` and caller identity.
Recording reads the original request and observation file URIs through the
current Resource context. It checks unchanged issued context, exact source and
kind, actual outcome/time, one matching turn and item, terminal completed/failed
status, integer exitCode and no declared source truncation. Its reference is
`codex:<thread>:<turn>:<item>@1`. The terminalObservation is the selected original
item. It is not a business receipt or Human consent.

For the actual page above, the existing source-item predicate's thread, turn,
type, status and exit-code conditions are satisfied. **This is a read-only code
and source inspection, not an executed Operation test**. The current program
does not compare command/cwd to a native validation target. Its passive result
does not persist or append a Direction. The caller must retain the actual tool
exchange; source shape and caller identity are attribution, not cryptographic
authentication. An arbitrary supplied JSON page cannot acquire trust merely by
passing those checks.

The existing `native-goal-command.py` actions `prepare-information` and
`record-information` require a retained `kind=Request` with its real
`issuedWorkContext`. `NativeWorkflowCallerExecution.validateIssuedC` requires an
actual workflow handle/run/continuation/revision and execution requirement.
A pending native PhaseValidation/SBT intent does not provide that Request.
Passing a previous semantic Request or manufacturing a new context would
cross the purpose boundary. No existing normal helper directly solves this case.

### 2.3 Minimal missing capability and boundary needing disposition

The missing capability is a runtime-owned, deterministic **native validation
invocation observation binding**, reusing caller source selection and the
current independent evidence store. It is not another acquisition process,
App Server proxy, generic command dispatcher, AI runner or PID registry.

If admitted, its source selection/shape parser belongs in a shared private
`caller/NativeCallerInformationSource.scala`; existing `NativeCallerInformation`
delegates to it without weakening its issued-context contract. A new private
`admission/GoalPhaseValidationPriorInvocation.scala` owns the native binding:

- Read the complete original public tool page through the normal Resource
  reader, select its exact locator once and preserve the selected original item
  plus source reference. No caller-supplied `ended`, replacement item, exit code
  or claimed validation result is accepted instead of that source.
- Compare the actual command and cwd to the fixed installed-CLI invocation
  shape, resolved RuntimeRoot, exact Session, normal operation and captured
  caller output location. This is observation checking only; it executes no
  shell or argv. Reject other commands, redirects, roots or sessions. Do not
  normalize a merely similar command into an accepted one.
- Obtain check identity/ordinal/attempt, source/frontier/intent, Plan/base/
  candidate, participant and opaque process plan from the actual native source
  traversal. These are not manually transcribed by the caller.
- Reuse independent evidence issuer/provisioning and exact new adoption-purpose
  binding. A passive caller-information result by itself never becomes consent
  or an independently authenticated permission token.

**Historical join limitation:** the public item names Session and original
caller output but no native intent reference/candidate/check. The original
native intent payload likewise contains no public caller invocation locator.
Inspection of the supplied 091 stderr found no2161/2162/candidate join; its
partial terminal event names only operation/workspace/program/PID. Thus a
command/Session match alone does not mechanically prove that this item owned
this exact native attempt. The current source is available; the missing piece
is the native causal association, not a source lookup or an absent exit code.

Do not retrofit such an association into old history, label a timestamp/PID/log
match as native proof, or invent an unimplemented trusted verifier. The consumer
must review whether a new narrowly typed, explicitly attributed historical
association attested by the existing independent authority plus the one Human
unknown-effects choice is acceptable, or whether stronger retained correlation
must first be found. That is an additional public-contract decision, not an
already implemented capability. Future dispatch-time correlation would require
native persistence of an actual invocation reference and cannot retroactively
prove091. No such new future registry is included silently here.

The eventual checker, if that boundary is accepted, must perform all mechanical
matching in program code. The existing caller only uses its ordinary public
tool and forwards the opaque original source; the AI must not assemble a
per-attempt proof, select a replacement execution context or implement the
correlation algorithm in chat. No second model is called for collection.

Until this boundary has a concrete admitted implementation and executable
qualification, the historical UNKNOWN path is **not ready**. Missing/crossed/
live/partial observations must fail closed for this adoption while preserving
UNKNOWN; output-limited native-result retention and unrelated work remain
independent. This document does not ask Human to approve a vague verifier.

## 3. Normal Resource input for both new Operations

Both new public Operations keep their business DTOs from v1:

```
describeGoalPhaseValidationRepair(input: GoalPhaseValidationRepairDescriptionInput)
adoptGoalPhaseValidationRepair(input: GoalPhaseValidationRepairAdoptionInput)
```

Each generated Operation declares exactly one of optional `input` and optional
`inputResource: string`. Runtime selection requires XOR, so optional in the ABI
does not mean an absent business input is valid. `inputResource` contains the
**entire same DTO**, not only target or processPlan. Description includes the
complete proposed Plan and any admitted prior-invocation selector; adoption
contains the full returned target/proposal, exact choice/rationale/reference.

Extract the existing transport selection from
`admission/NativeWholePhaseRecoveryInput.scala` into private
`admission/NativeWorkflowResourceInput.scala`, parameterized by the generated
business `ValueReader[A]`. Preserve the old helper's public-within-module API
as a delegating transport facade. Both new handlers call the same generic
selector/reader. This extraction changes no old recovery admission or evidence
semantics and avoids a second URI grammar.

The new `GoalPhaseValidationRepairAdoption` OperationDefinition chooses only
Inline[A] or Resource before execution. Its `CommandAction`/`ProcedureActionCall`
reads Resource via `read_resource_text(reference, Some(UTF_8))` in the current
ExecutionContext, then uses `NativeWorkflowOperationInput.decodeValueC[A]` and
the same admission port as inline. It selects the Session context only from
the decoded full DTO after the read. It never captures a construction-time
Resource context, uses Files.readString or parses process execution policy.

The accepted URI is absolute local `file:` with no authority, query or fragment,
no empty/relative/whitespace source. Existing CNCF root authorization, finite
`textus.resource.read.max-bytes`, streaming acquisition and strict UTF-8 REPORT
decoding apply. The business decoder rejects nonobject/malformed/incomplete/
explicit-null input. Do not use an independent application byte ceiling or a
fallback to inline when Resource reading fails. No new permission, candidate
Git, stage, history rewrite, SBT, Submit or Advance follows from input reading.

The existing code connection is real: WholePhaseCandidateRecovery's Action
already invokes `NativeWholePhaseRecoveryInput.readC` with `read_resource_text`;
CNCF `BehaviorFeaturePart` delegates to `execution_context.resources.readText`,
and `ResourceContent.textC` uses strict REPORT decoding. `UrlResourceAccess`
opens the authorized file and applies existing acquisition limits during read.

### Required large-input installed acceptance

At least adoption, and preferably description too, must be called through an
independent **installed CLI process** with `--inputResource <absolute file URI>`
and its full generated DTO. No direct in-process mock reader qualifies this.

Build a legitimate isolated host process plan containing finite, genuinely
declared fixture programs and policies, resolved by normal CNCF configuration.
Make the actual opaque returned processPlan and full target large enough that
the measured UTF-8 business JSON exceeds the platform's actual ARG_MAX plus its
environment allowance. Retain the measurement and actual input size. Do not
pad with a field that admission ignores, shrink the target or hand-edit its
opaque processPlan. If current host-plan bounds make such a legitimate fixture
impossible, report that exact capacity conflict instead of claiming coverage.

Write that exact DTO as UTF-8 to an allowed fixture file, leave Resource's
finite16MiB bound in place if it fits, invoke only the short URI argument,
and assert the exact target/proposal survive through actual admission and a
separate-process reopen. New handlers and generated ABI/help must agree.
Negative installed cases include both/neither inputs, authority/query/fragment,
outside-root/symlink denial, missing/over-cap file, invalid UTF-8, malformed
JSON and explicit null. They must precede any evidence consumption or native
Direction write. Old WholePhase recovery retains its existing behavior/tests.

## 4. Same old-runtime defect fixture, not an acknowledgement surrogate

Pin the **pre-upgrade** isolated caller to immutable installation
`382e8019-45b6-4572-884c-f7eb67b02e7e`. Its manifest still exists at
`/Users/asami/.cncf/installations/sm-workflow/382e8019-45b6-4572-884c-f7eb67b02e7e/runtime-installation.json`.
The component is `components/textus-sm-workflow.car`, nested `component/main.jar`;
runtime/0000.jar is Scala, not the sm-workflow component. Read-only inspection
of the actual nested class confirms the old truncation/byte-count rejection
diagnostic. This observation does not claim the reproduction was run.

Qualification after implementation admission must:

1. Create only a new isolated test repository, RuntimeRoot, storage and Session
   using that old installed runtime. Establish a real accepted Step prefix by
   ordinary fixture work/review/commit; no preloaded accepted-history forgery.
   Save its Plan/base/accepted results and current native prefix.
2. Declare one ordinary typed whole-Phase Test in the normal fixed SBT host
   program. Select a deliberately small finite stderr ceiling and an ordinary
   finite SBT test that emits past it. It must execute through the actual
   `sbt_*` DSL/UoW path; no Process fallback, product main, external SBT wrapper
   or top-level test runner substitutes for product validation.
3. Observe real CNCF `OutputLimitExceeded(Stderr)` and actual managed process
   termination, then the **old sm-workflow receipt-admission refusal**. Require
   the original intent to be present and result/final validation receipt absent;
   a separate old installed describe returns UNKNOWN. Save original streams,
   tool source/actual invocation observations and actual launch count. Do not
   inject an acknowledgement error or manually remove a result to simulate it.
4. Switch only that isolated caller's selected runtime to the qualified new
   installation. Do not Start again, mutate old receipts, reset counters,
   replay the test or replace the accepted prefix. Describe the same UNKNOWN
   through a separate installed process. Verify native definition/history
   compatibility by reopening actual persisted data, not only a DTO codec.
5. Once section2's provenance/binding boundary is resolved and implemented,
   prepare one concrete appended corrective Step and the actual one combined
   Human direction. Adoption appends exactly one Direction and no result for
   the old intent. Reopen and ordinary Advance issue only new corrective work.
6. Complete genuine new scoped repair/check, independently accepted Step
   review, local Step commit, new whole Test, one integrated Phase review and
   ordinary local closure in that fixture. Capture immutable prefix equivalence,
   unchanged counters except legitimate new work, exact generation increment,
   no accepted Submit replay and actual SBT launch counts. Repeat pending
   continuation in a fresh process to demonstrate no duplicate effect.

This replaces v1 acceptance4 entirely. Do not touch real091, replay actual
accepted work or use the real Session to construct the fixture. The new-result
case is separate: new runtime plus a small real output ceiling must persist
raw incomplete output-limit data and return truthful Decision, and a separate
process must read the same bytes without running SBT again.

The full UNKNOWN end-to-end acceptance remains unqualified until section2's
missing causal/provenance binding is resolved. A successful reproduction of the
old bug is not evidence that adoption/continuation works.

## 5. Receipt arity, codecs, projection and immutable acceptance

The v1 byte-preserving nested wire remains the proposed repair:
`sm-workflow.native-sbt-validation-receipt.v2`, one canonical Base64 copy of each
actual CNCF capture, actual byteCount/truncated/termination/elapsed/program,
closed exact keys and empty tagged-SBT artifacts. No hash is introduced.
New valid output-limit plus Incomplete is retained; incomplete is not Exited143,
failed Test, successful Test or complete text. Split trailing UTF-8 bytes remain
raw and presentation says prefix/unavailable; malformed interior/NUL is never
silently replaced. Unconfirmed termination remains unconfirmed and ineligible
for adoption. Complete successes retain strict UTF-8 and selection checks.

The actual private Receipt currently has **six** parameters, with default
optional SBT facts. Add the seventh optional actual ProcessExecutionResult at
the end with default None. Source search found no positional Receipt extractor
matches in main/test Scala; existing five/six-argument constructors therefore
need not all be edited. Do not infer codec compatibility from constructor arity.

- `RepositorySyncNativeMergeValidation.Receipt` has explicit Encoder/Decoder:
  dual read, legacy write unchanged for None, strict tagged new write/read for
  Some. Unknown/malformed new tags never fall back to legacy.
- Repository native State uses derived codecs over Receipt; Goal native Payload
  uses derived codecs plus its existing normalization/validity checks. Qualify
  both enclosing codecs with old and new data. Do not alter enclosing Goal v2
  identity or synthesize absent old captures.
- `NativeWorkflowOperationOutput` owns the two existing Repository wire
  constructor sites and one Goal check constructor site. Update all three and
  the corresponding generated DTOs with the optional marked process projection.
- `GoalPhaseNativeReviewCompletion` directly validates receipt members before
  semantic-review proof. It must validate the new optional observation through
  the shared strict validator; it cannot accept new malformed process data just
  because old text/facts fields are nonnull. Preserve all old success rules.
- `GoalPhaseNativeEvidence` archives only genuine completed successful/failed
  Exited Test output. New incomplete data must not be archived as complete
  evidence. No change to that owner is planned if its current exclusion holds;
  include its existing tests in selected regression evidence.

The one32MiB stdout /8MiB stderr profile,64MiB existing mechanical receipt
maximum, representative2162-entry history through the real encoder and all
finite-limit guards from v1 remain required. Native raw output is stored once;
no duplicated full text or extra capture datastore. Current host2MiB/128KiB
limits are not changed in the real consumer by preparing this design. Resource
capacity, SBT output capacity and Git1MiB/default finite16MiB policy are separate.

### Producer and consumer derivation are different owned contracts

Producer GoalPhase derivation is13 configurations/six Required; new public
operations expand its Provided inventory from25 to27 while retaining its native
workflow identity/revision. The consumer reports an in-progress
`GoalPhaseOrderedTriageProducerProbe` contract of14 configurations/seven Required.
Do not overwrite the consumer's dirty probe/CML or its31 acceptance conditions
with producer13/six, and do not call a producer pass evidence for consumer
Triage. Any consumer merge/qualification stays with its owner. The actual
old-runtime fixture is required to qualify persistence across source changes.

## 6. Complete authored-file audit before a Fix Manifest

All paths are relative to producer `/Users/asami/src/dev2026/sm-workflow`.
This is an inventory for review, not an issued mutation manifest. Existing
mixed/unrelated deltas must be frozen and preserved at implementation entry.
The v1 table had33 paths including one generated catalog: **32 authored paths**.
The bounded core/transport audit below has38 authored paths. Source-binding
work adds seven conditional authored paths, for **45** if all are admitted.
Do not freeze the conditional subset while its historical contract is unresolved.

| # | Authored path | Owned purpose |
| --- | --- | --- |
| 1 | `src/main/cozy/sm-workflow.cml` | Two new public Operations/DTOs; both input/inputResource XOR contracts; optional receipt projection; incomplete/adoption edges; distinct evidence preparation purpose. |
| 2 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowSbtCapture.scala` (new) | Closed CNCF capture/result codec and text projection. |
| 3 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncNativeMergeValidation.scala` | Seventh Receipt field, explicit dual codec, actual incomplete admission and strict success guard. |
| 4 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeValidation.scala` | Actual output-limit persistence/reopen, incomplete result and exact native source traversal. |
| 5 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseOrderedProgression.scala` | Pure incomplete/adoption transitions preserving base/accepted prefix/counters. |
| 6 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseValidationRepairAdoption.scala` (new) | Closed target/source/Direction, both Resource-aware handlers and guarded CAS. UNKNOWN branch depends on section2. |
| 7 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeWorkContinuation.scala` | Native reconstruction/ports, Direction dispatch, pre-first-work candidate/policy check; never repeat pre-repair checks after retained work issuance. |
| 8 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeRun.scala` | Distinct evidence installation and operation admission. |
| 9 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeServiceAssembly.scala` | Actual public service registration. |
| 10 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowRuntimeParameters.scala` | Existing resolved-config evidence installation binding, no ambient parser. |
| 11 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowExternalEvidencePorts.scala` | New exact purpose under current trusted issuer/store. |
| 12 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowLauncherAssembly.scala` | Publish both real Operations. |
| 13 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/NativeExternalEvidence.scala` | Exact new adoption binding under existing v7 envelope. |
| 14 | `src/main/scala/org/simplemodeling/textus/smworkflow/preparation/NativeExternalEvidenceInput.scala` | Register new passive preparation kind only. |
| 15 | `src/main/scala/org/simplemodeling/textus/smworkflow/preparation/NativeValidationRepairPreparation.scala` (new) | Complete proposal/target/independent Human binding, no AI/effects/consent creation. |
| 16 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/NativeWorkflowOperationOutput.scala` | All three generated Receipt constructor/projection sites. |
| 17 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseValidationRecovery.scala` | Optional concrete repair guidance, existing two-choice UNKNOWN recovery unchanged. |
| 18 | `docs/spec/normal-typed-sbt-validation.md` | Versioned raw result, dual compatibility and finite limits. |
| 19 | `docs/spec/goal-phase-validation-repair-adoption.md` (new) | Exact source/target/adoption/continuation scope and refusals. |
| 20 | `docs/spec/goal-phase-validation-recovery.md` | Guidance without weakening old safety contracts. |
| 21 | `docs/spec/normal-host-preparation-operations.md` | New exact passive preparation input and authority distinction. |
| 22 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowSbtCaptureSpec.scala` (new) | Raw-byte/UTF-8/termination/strict codecs and real maximum encoder capacity. |
| 23 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeTestOnlyValidationReceiptSpec.scala` | New/legacy dual enclosing codecs, selection/contradiction guards. |
| 24 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseValidationRepairAdoptionSpec.scala` (new) | Pure transitions, full target/binding/retained-prefix/refusal matrix. |
| 25 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeGoalOutputLimitValidationCliSpec.scala` (new) | Real UoW output limit, persistence, independent installed reopen and concrete-result route. |
| 26 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeGoalUnobservedPhaseRepairAdoptionCliSpec.scala` (new) | Actual382 old defect, same isolated Session after switch and gated UNKNOWN adoption through genuine new closure; large Resource transport. |
| 27 | `src/test/scala/org/simplemodeling/textus/smworkflow/GoalPhaseGeneratedAbiSpec.scala` | Exact27 Provided and both input sources, not absent-input acceptance; preserve/report prior unrelated failure. |
| 28 | `src/test/scala/org/simplemodeling/textus/smworkflow/SmWorkflowGeneratedHelpSpec.scala` | Actual generated names/transport help. |
| 29 | `src/test/scala/org/simplemodeling/textus/smworkflow/ComponentPackagingDescriptorSpec.scala` | Exact packaged public operation/input inventory. |
| 30 | `src/test/scala/org/simplemodeling/textus/smworkflow/GoalPhaseCmlDerivationSpec.scala` | Actual producer13/six derivation and27 Provided. |
| 31 | `src/test/probes/GoalPhaseCmlDerivationProbe.scala` | Producer inventory only; no consumer Triage edits. |
| 32 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowDirectOperationSpec.scala` | Affected actual generated public-set assertions, no skipped/weak inventory test. |
| 33 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/NativeWorkflowResourceInput.scala` (new) | One generic inline/Resource selector and strict DTO reader, extracted from existing helper. |
| 34 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/NativeWholePhaseRecoveryInput.scala` | Delegate unchanged old facade to the common selector. |
| 35 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeWorkflowResourceInputSpec.scala` (new) | Generic XOR/URI/full-DTO/reader error boundary across both new DTOs. |
| 36 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeWholePhaseRecoveryInputSpec.scala` | Preserve existing transport observations after extraction. |
| 37 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeReviewCompletion.scala` | Shared strict validation of new optional observation before review proof. |
| 38 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeReviewCompletionSpec.scala` | Reject malformed new capture while preserving all legacy accepted evidence. |
| 39 | `src/main/scala/org/simplemodeling/textus/smworkflow/caller/NativeCallerInformationSource.scala` (new, conditional) | Shared original-source selection only, no new authentication claim. |
| 40 | `src/main/scala/org/simplemodeling/textus/smworkflow/caller/NativeCallerInformation.scala` (conditional) | Delegate selector without weakening real issued-context checks. |
| 41 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseValidationPriorInvocation.scala` (new, conditional) | Native command/source/target observation binding, only after historical boundary is resolved. |
| 42 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseValidationPriorInvocationSpec.scala` (new, conditional) | Actual source shape, exact allowed invocation and all crossed/missing/partial-source refusals; no fabricated native causal proof. |
| 43 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeCallerInformationOperationSpec.scala` (conditional) | Existing caller-context/source regressions after selector extraction. |
| 44 | `docs/spec/caller-information.md` (conditional) | Distinguish attribution/termination/native join/authority; no fake work context. |
| 45 | `docs/spec/host-terminal-source.md` (conditional) | Link exact admitted current native binding; retain withdrawal of historical provider. |

Rows33–38 are the six authored additions to v1's inventory: resource extraction
and its two test owners, old facade/test preservation and direct review-proof
validation. Rows39–45 are seven conditional source-binding owners, explicitly
not a ready hidden dependency. Tests needing execution without an authored
change include existing `NativeGoalWholePhaseCandidateRecoveryCliSpec`,
`NativeCallerInformationCliSpec`, `WorkflowSbtValidationSpec` and relevant
`CommonEvidenceSpec` assertions. Selecting a regression suite is not permission
to edit unrelated implementation.

Generated files, **not authored paths**: ordinary generation may change tracked
`src/main/catalog/car/org/simplemodeling/textus/textus-sm-workflow.yaml` and
produces managed DTOs, SmWorkflowComponent, GoalPhaseRunLifecycle, ABI/help and
workflow/Provided resources under target. Never hand-edit them. No manual test
registration, compatibility alias or product main is introduced. Journal v1,
this v2 and private diagnostic source retention are design records, separate
from production inventory. No development-workstation/upstream source change
is presently presumed; an actual missing external trust capability would need
its concrete owner and explicit scope rather than pretending rows39–45 solve it.

## 7. Next disposition and preservation

Return this concrete revision to consumer review first. It now names the actual
091 source and exact existing programs, Resource handler/ABI/spec changes and
the genuine old-runtime defect qualification. It also explicitly exposes the
remaining historical causal/provenance gap; it does not call it implemented or
replace it with per-attempt AI wiring. No further high-level Human question is
issued by preparing v2. The consumer should resolve that exact boundary before
the producer requests admission of a frozen persistence/public-contract scope.

After an actual implementation decision: use the bounded edit worker, selected
behavioral tests, one independent focused review, necessary normal publishLocal,
runtime build/acceptance/activation and isolated installed qualification. Product
Git/SBT effects remain UoW-owned and independent top-level producer builds use
registered runners with sbt-native locking. Neither locks nor test scope are
broadened by this proposal. Existing timeout34 passing tests / two installed
scenarios and successful publication are not repeated merely for design work.

All real consumer Goal/Session, six accepted Steps/results, original31 acceptance
conditions, base, PLAN revision, history, repair counters, old UNKNOWN/timeout
records and all tracked/untracked work are preserved. No actual091 retry,
recover/adopt/Advance, new Start, accepted Submit replay, commit, push or
Phase-completion claim has been performed here. Real Session progression stays
with `sm-workflow-dev開発` after a separately qualified delivery and its actual
current public description/Human decision.

## Agent usage

Parent-only read-only diagnosis/design. No worker, reviewer, SBT or runtime agent
was started or resumed for v2. Parent model/effort are unknown; not inferred
from older chat settings. Existing completed timeout agents remain completed.

| Agent | Model / effort | Role | START | RESUME | DIRECT |
| --- | --- | --- | ---: | ---: | ---: |
| `/root` | unknown / unknown | parent diagnosis and concrete design | 0 | 0 | 1 |
