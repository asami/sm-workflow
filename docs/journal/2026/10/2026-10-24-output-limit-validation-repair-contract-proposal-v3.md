# Output-limit validation repair: frozen contract proposal v3

Date: October 24, 2026
Status: concrete design for consumer review and a subsequent Human implementation decision; NOT implemented or approved for implementation
Producer: `/Users/asami/src/dev2026/sm-workflow`
Real Session owner: `sm-workflow-dev開発`

This supersedes the proposed contracts in [v1](2026-10-24-output-limit-validation-repair-contract-proposal-v1.md)
and [v2](2026-10-24-output-limit-validation-repair-contract-proposal-v2.md), without
replacing their history or evidence. The consumer's review resolved v2's design
question: a historical association is an explicitly scoped independent Human
adoption, **not a mechanically proved causal fact**. That review is a design
instruction, not Human implementation or real-Session effect authority.

## 1. Decision and safety boundary

The recommended contract is viable with the limitations below. No unresolved
external verifier, retrospective native proof or per-attempt AI algorithm is
assumed. Mechanical source matching and native reconstruction belong to the
program. The independent Human adopts their historical association and the
exact corrective Plan in one decision, explicitly accepting the unresolved
old result/effects and unobserved managed descendants. Neither party invents
the old validation result or a descendant-settlement observation.

The consumer's existing separate dirty
`src/test/probes/GoalPhaseCmlDerivationProbe.scala` is preserved. Scope/dirty
admission reuses the repaired existing candidate preconditions: this proposal
does not require the whole working tree to be clean or reject already permitted
out-of-scope work. It adds neither authority to edit that work nor a new dirty-
path exception. Exact original candidate and owned-path checks remain strict.

The existing caller owns acquisition/provenance of its actual public tool
exchange. Shape validation does not machine-authenticate an API response or
turn an arbitrary uploaded JSON page into a trusted execution receipt. Existing
independent issuer/store admission and the actual Human answer remain the
authority boundary. The new contract does not supply cryptographic source
authentication. It describes that limitation to the Human rather than claiming
it is solved by command equality, a file URI, a timestamp or an issuer label.

This source kind is restricted to the actual latest pending native intent for
**one ordinary whole-Phase Test after every original Step is accepted**. It is
not retry permission, a generic UNKNOWN escape, or a Git/stage/commit recovery.
The only new native effect of adoption is one Direction. Future work requires
a separate ordinary Advance and all ordinary checks, review and closure.

If current native facts show a live execution, stored TerminationUnconfirmed,
an observed later result/direction, a foreign dirty path or any other refusal
below, the Human choice cannot override that refusal. Unobserved descendants
are a specifically disclosed residual risk, never an inferred absence of a
live process. No process scan or new settlement registry is introduced.

## 2. Actual original source and normal caller path

The producer independently retrieved the original page through the existing
public `read_thread` tool with `includeOutputs=true`, `turnLimit=8`. Its original
tool return is retained at:

`.codex-workflow/disposable/output-limit-contract-v2-20261024/091-public-read-thread-original.json`

The actual locator is:

```
threadId = 01a1555d-b4a7-7be3-8232-5396c2f5e293
turnId   = 01a16d18-58b0-74a0-973a-23c373cc503e
itemId   = exec-cf5991d4-64bc-4b23-9050-4665a7fb8b7a
```

Its actual `commandExecution` is `failed`, exitCode1, durationMs2665964, cwd
the consumer RuntimeRoot. The command is the exact installed CLI, normal
GoalPhase Advance, real Session and redirects to the original091 output files.
See v2 section2 for those complete paths and acquisition details. The complete
public page, not the caller's diagnostic `terminal.json`, is the selected source.
It proves the caller invocation ended; it does not prove which native intent
it reached or that the SBT descendants settled. The original native pending
intent has no public invocation locator, and that absence is not repaired in
old history.

Reuse only the source-selection portion of current
`caller/NativeCallerInformation.scala`, extracted to private
`caller/NativeCallerInformationSource.scala`. Preserve current
`prepareCallerInformation` / `recordCallerInformation` and their real
`WorkflowIssuedWorkContext` requirement. The native recovery handler does not
call them with a fabricated Request or a previous semantic work context.
The historical `host_source_read` provider in `host-terminal-source.md` remains
withdrawn. No provider process, App Server proxy, second model, arbitrary
ProcessExec or development-workstation helper modification is needed.

The caller performs its ordinary public API read and forwards the opaque full
page resource plus the real locator. The program selects/validates the original
item and binds it to the native snapshot for an explicit Human decision. The
caller does not build a receipt, parse terminal outcomes, assert `ended=true`,
infer a native association or reconstruct the target. If the page is not
available, the new branch refuses; missing source is not a new retry reason.

## 3. Fixed invocation comparison policy

V2 did not identify a current resolved setting for original CLI/caller roots.
V3 adds exactly one optional component parameter instead of leaving that input
to ambient cwd, AI reconstruction or an unimplemented verifier:

`sm-workflow.goal-phase.validation-repair.caller-source`

Its closed object is:

```text
CallerSourcePolicy(
  schemaVersion = "sm-workflow.validation-repair-caller-source-policy.v1",
  threadId: String,
  installedExecutable: String,
  runtimeRoot: String,
  taskRoot: String)
```

Declare/decode it through CNCF `ComponentParameterKey.optional` and
`ComponentInitializationParameters` in the existing runtime composition. This
is resolved configuration passed to the owning Declaration/Installation; the
application does not parse config as operation parameters or consult env/system
properties as a fallback. Absence disables only historical-source adoption;
native output-limit retention and the concrete-result route still work. Null,
unknown/missing keys, empty/unusable paths and invalid thread identity refuse
configuration. It is not an executable grant or proof of a prior invocation.

Paths must be absolute normalized exact local paths. `taskRoot` must be a strict
descendant of `runtimeRoot/target/sm-workflow/tasks/`. The known v1 rendering
below admits spaces but rejects controls, quotes, backslash, dollar and backtick
in configured paths, rather than inventing another shell grammar. `threadId`
uses the existing exact source-locator identity rule. The Session must be safe
in this fixed unquoted position: `[A-Za-z0-9][A-Za-z0-9._:-]{0,255}`. An
unsupported identity/rendering fails; there is no alternative command parser.
The complete resolved policy is captured in the target and compared again at
adoption and before the first new corrective work is issued.

The source selector supplies only `outputName`, a single safe existing task
child name matching `[A-Za-z0-9][A-Za-z0-9._-]{0,127}` other than `.` or `..`.
The program derives response/stderr paths under the resolved taskRoot. It
compares the selected item's command **exactly** with this one rendering:

```text
/bin/zsh -lc "exec '<installedExecutable>' --runtime-root '<runtimeRoot>' command org.simplemodeling.textus.SmWorkflow.GoalPhaseService.advanceGoalPhase --sessionId <nativeSessionId> --json > '<taskRoot>/<outputName>/response.json' 2> '<taskRoot>/<outputName>/stderr.log'"
```

The actual091 item has this rendering. No command is executed to compare it.
Equivalent quoting, extra arguments/commands/redirections, a helper launch,
another operation/Session/executable/cwd/root, client mode or another output
location refuses. This finite allowance is source qualification for this old
incident, not a new product shell execution route. Match does not prove causal
association to the native intent; that remains part of the Human adoption.

## 4. Closed input and source observation

Add only these two neutral GoalPhase public Operations and four generated DTOs:

```text
describeGoalPhaseValidationRepair
  GoalPhaseValidationRepairDescriptionInput(
    sessionId: String, proposal: Record, priorInvocation: Option[Record])
  -> GoalPhaseValidationRepairDescriptionResult(
    sessionId: String, outcome: String, message: String,
    componentInstance: String, target: Record, choices: Vector[String],
    adoptionOperation: String)

adoptGoalPhaseValidationRepair
  GoalPhaseValidationRepairAdoptionInput(
    sessionId: String, target: Record, proposal: Record,
    choice: String, rationale: String, reference: WorkflowArtifactReference)
  -> GoalPhaseValidationRepairAdoptionResult(
    sessionId: String, accepted: Boolean, outcome: String, message: String)
```

Both Operations declare optional `input` and optional `inputResource: string`
and enforce exactly one. The full business DTO is decoded identically for both.
Description priorInvocation is absent for a native output-limited result and
required for the historical UNKNOWN source. Explicit null is not omission.
After the existing public-name normalization, the handler checks the exact
business-key set before generated ValueReader decoding: description requires
sessionId/proposal and allows only priorInvocation in addition; adoption requires
exactly sessionId/target/proposal/choice/rationale/reference. Unknown keys or
colliding aliases are not silently ignored by a generated reader. This belongs
to the new handler in row6, with Resource selection in row33; no generic business
decoder widening is implied. A source/proposal failure returns the normal typed
Consequence failure, not a manufactured partial target or another source kind.
It is the following closed **selector**, not an alleged prior receipt:

```text
PriorInvocationSelector(
  schemaVersion = "sm-workflow.validation-prior-invocation-selector.v1",
  sourceLocator: WorkflowCodexSourceLocator(threadId, turnId, itemId),
  sourceResource: String,
  outputName: String)
```

`sourceResource` is an absolute local file URI containing the complete original
public page, read through current CNCF Resource authorization/finite limits and
strict UTF-8. The source must name the configured caller thread. Select exactly
one turn and item with the exact locator; duplicated selected turns/items,
wrong kind, missing/partial/truncated selected source and unknown exit refuse.
The complete bounded API page may have `hasMore=true`: this means more thread
history, not a truncated selected item or permission to replace it. No full
thread discovery or second API acquisition is done inside the runtime.

For this finite old command shape, the selected item has exactly these keys:

```text
{type, id, command, cwd, status, exitCode, durationMs}
```

Type is `commandExecution`, id is the selected item, command/cwd match section3,
status is completed/failed with integer exitCode (completed/0 or failed/nonzero),
and durationMs is a nonnegative integer. All fields are nonnull. Extra fields
(including declared truncation), running/unknown status and malformed values
refuse this historical branch. An API source shape change needs a separately
reviewed contract update, not a permissive fallback. Existing caller-information
behavior is preserved; the shared selector's extraction does not impose this
new native-command shape on unrelated existing callers.

After selection, the program creates this exact immutable snapshot:

```text
PriorInvocationSnapshot(
  schemaVersion = "sm-workflow.validation-prior-invocation-snapshot.v1",
  sourceLocator: WorkflowCodexSourceLocator,
  sourceReference: WorkflowArtifactReference,
  sourceResource: String,
  sourceObservedAt: String,
  selectedItemJson: String,
  policy: CallerSourcePolicy,
  outputName: String,
  proofScope = "caller-invocation-terminated-only")
```

sourceReference is `codex:<thread>:<turn>:<item>@1`. sourceObservedAt is the
runtime's actual observation instant during description, not a reconstructed
execution-end time or a caller's `ended` assertion. selectedItemJson is the
canonical compact serialization of the exact selected original item, with
unchanged field values; retain it as opaque text so Record normalization cannot
alter command/source keys. It is derived from the source, not user-authored.
No digest, signature, receipt backfill or new capture datastore is introduced.

Adoption rereads that bound page URI through current Resource rules, reselects
the exact original item and compares its values/policy to the captured snapshot.
It does not regenerate sourceObservedAt and mistake its new read time for a
changed target. Missing/changed source or source root denial refuses before a
Direction. A later ordinary replay of an already accepted Direction uses its
retained snapshot; it does not reacquire or reauthenticate an old tool page.

## 5. Closed native target and limitations

All new target/source objects reject unknown keys, missing required members,
explicit null, crossed identifiers, invalid versions and contradictory values.
Existing reused models retain their exact codecs/optional-field conventions.
No failed decoder switches to another source kind. The target is exactly:

```text
ValidationRepairTarget(
  schemaVersion = "sm-workflow.goal-phase-validation-repair-target.v1",
  source: OutputLimitedSource | HistoricalUnobservedSource,
  plan: GoalPhaseOrderedProgression.Plan,
  completed: Vector[GoalPhaseOrderedProgression.ClosedStep],
  repairProgress: GoalPhaseRepairProgress.Snapshot,
  generation: Long,
  candidatePaths: Vector[String],
  closurePolicy: GoalPhaseNativeClosure.Policy,
  captured: GoalPhasePlanRevision.Captured,
  correctiveChecks: Vector[GoalPhaseNativeValidation.Check],
  processPlan: String,
  limitations: NativeOutputLimitLimitations | HistoricalAssociationLimitations)
```

plan is the full original effective Plan; completed is the actual ordered
accepted prefix. captured contains the complete raw proposal and its resolved
effective Plan through existing acceptance handling. All original acceptance
conditions, Step definitions, accepted commits/reviews, Phase checks/review
classification, repository/Phase/Plan identity and repair limit remain exact.
Only proposal revision changes and exactly one nonempty corrective Step is
appended, depending on all original Steps. Its exact paths/checks/Step commit
must already be admitted by candidatePaths and fixed host/closure policies.
No speculative later Step, broad ownership amendment or old Step reopening.
The work may cover known errors and the bounded hang investigation of tests
active/suspected at interruption. Missing active-test identity remains unknown;
the final log line alone does not identify an exclusive running test.

processPlan is the **opaque complete** normal resolved process-plan JSON string,
compared JSON-semantically as existing WholePhase recovery does. The application
does not own executable/argv/limit interpretation. repairProgress's exact three
fields are automaticCount, ordinaryCount and limit. The program also reconstructs
and preserves the entire controller and its cumulative history/counters; the
three-field presentation is not a reset or replacement ledger. Original
base and candidate snapshots below use the existing head/tree model.

Source1, with all keys mandatory, is unchanged concrete-result scope:

```text
OutputLimitedSource(
  kind = "output-limited",
  sessionId, workflowIdentity, workflowRevision, participant,
  planIdentity, planRevision, base, candidate,
  sourceRevision, frontierRevision, step, checkIdentity,
  checkOrdinal: Int, attempt: Int,
  intent: ReceiptRef, result: ReceiptRef, validation: ReceiptRef,
  termination = "output-limit-exceeded",
  stream: "stdout" | "stderr", outcome = "incomplete")
ReceiptRef(identity: String, revision: String)
NativeOutputLimitLimitations(
  schemaVersion = "sm-workflow.native-output-limit-limitations.v1",
  result = "native-output-limited-incomplete",
  output = "retained-prefix-not-complete-test-output")
```

It requires the actual contiguous intent/result/final incomplete receipt and
current whole-Phase Decision, all Steps accepted and one ordinary Test.
CNCF's typed returned variant is the result; diagnostic exit143 is not used.

Source2 is exactly:

```text
HistoricalUnobservedSource(
  kind = "historical-unobserved-result",
  sessionId, workflowIdentity, workflowRevision, participant,
  recovery: GoalPhaseValidationRecovery.RecoveryTarget,
  intent: ReceiptRef,
  resultDisposition = "unobserved",
  priorInvocation: PriorInvocationSnapshot)
HistoricalAssociationLimitations(
  schemaVersion = "sm-workflow.historical-validation-association-limitations.v1",
  association = "requires-explicit-human-adoption-not-mechanically-proven",
  oldResult = "unobserved",
  managedDescendants = "unobserved",
  priorEffects = "unresolved-and-may-overlap-with-new-work",
  callerSource = "caller-retained-public-api-source-not-machine-authenticated")
```

recovery must be the actual current native unresolved target, without
knownFailedPhase, dependency or terminatedTimeoutPhase extensions. The native
reconciler reconstructs source/frontier, check identity/ordinal/latest attempt,
candidate/base/Plan, participant, accepted prefix and repair counters. It
requires the saved frontier to be that latest pending intent, with no native
result/final receipt, uncertainty result, continuable NotExecuted/admission
rejection or accepted later recovery direction. No document-backed/metadata/
dependency/multiple-check/Step-validation source is admitted. No unrelated
terminal/candidate-recovery contract is interpreted as this source.

For the previously published092 snapshot, this reconstructs source2161 /
frontier2162 / intent2162 and check repository-full ordinal8 attempt0 for the
same base/candidate/Plan. The future real owner must use its actual current
description; these historical numbers are not a manufactured future target.

## 6. One exact Human choice and independently bound evidence

Description is passive: read current native history/configuration and input
Resources, validate proposal/source and return `outcome=adoption-required`,
exact target, available choices and `adoptionOperation`. No AI, Human prompt,
candidate Git, file mutation, SBT or native write occurs in description.

For source1 the choices are exactly `AdoptRepairPlan`, `Defer`.
For source2 the choices are exactly:

```
AdoptHistoricalAssociationAndRepairPlanAcceptingUnresolvedEffects
Defer
```

The long choice means all of the following **together**, for this complete
displayed target/proposal:

1. Adopt the association of this exact original caller source with this exact
   UNKNOWN native intent as a historical association under Human authority.
2. Accept that native causal proof, the old result and managed-descendant
   settlement are absent, that source provenance is the existing caller's
   retained actual public API exchange rather than new machine authentication,
   and that unresolved old effects may overlap with new work.
3. Adopt this exact bounded appended-Step Plan while retaining every original
   requirement/result/history/counter and all remaining verification/reviews.

No separate preliminary LimitedRepair question is needed; no second answer
certifies what was not observed. Ordinary `AdoptRepairPlan` is invalid for
source2. `Resume`, `ApproveEngineering`, development/design approval, old083/
090 references, the timeout answer, an AI paraphrase or a passive JSON import
cannot replace this actual decision. Rationale alone cannot change its meaning.

The new purpose is `goal-phase-validation-repair-adoption` in the existing
`sm-workflow.native-external-evidence.v7` envelope and normal independent
issuer/store. The exact binding is:

```text
{
  componentInstance,
  validationRepairAdoption: {
    target: ValidationRepairTarget,
    proposal: GoalPhasePlanRevision.Proposal,
    answer: {choice, rationale, participant, reference: WorkflowArtifactReference}
  }
}
```

No extra binding keys are accepted. Participant must equal the native source
participant; issuer, reference, occurrence time and actual answer are admitted
by the existing independent evidence authority, never an installation default
or the caller identity alone. target includes the real selected item/locator,
fixed invocation comparison policy, full native snapshot, complete proposal
capture and all fixed limitations. proposal must exactly equal captured.proposal
after the existing public-name normalization; changing reason/raw acceptance/
effective acceptance/one proposed path changes the binding. The actual Human
must see those facts and limitations before the choice is requested.

Normal `prepareEvidence(family=GoalPhase,kind=validation-repair-adoption)`
reads the actual description, complete adoption input, reference/execution
metadata and explicit participant/issuer/time through the current finite
preparation contract. `NativeValidationRepairPreparation` validates and builds
the exact StartupImport record. It performs no Human verification by inference,
AI call, source acquisition, provisioning, Submit or Advance. Existing trusted
ingress owns observing/provisioning the genuine independently issued answer;
prepared bytes are not that authority. For source2 it additionally binds the
actual original caller provenance and complete historical limitations above;
there is no new trusted execution verifier or forged Textus provider identity.

## 7. Direction-only adoption and ordinary continuation

Adoption strictly decodes the complete supplied input, reconstructs current
native facts, validates the exact source/page and policy, revalidates captured
proposal/check/path/closure/opaque process-plan equality and independent
evidence, and wins one guarded native CAS. Failure changes no old intent/result.
`Defer` requires its own exact evidence and writes no Direction.
Successful CAS returns accepted=true/outcome=adopted; admitted Defer returns
accepted=true/outcome=deferred with no write. Accepted here refers only to this
specific decision, not Test/Step/Phase completion. Refusals use the ordinary
typed Operation failure; an uncertain CAS result is not reported as adopted or
retried automatically. If historical description cannot be made because its
source is unavailable, existing validation-recovery Defer remains available
under its unchanged contract; the new route does not force another execution.

For either actual adopting choice persist exactly:

```text
{
  schemaVersion: "sm-workflow.goal-phase-validation-repair-adoption.v1",
  payload: {
    target: ValidationRepairTarget,
    proposal: GoalPhasePlanRevision.Proposal,
    choice, rationale, participant, reference: WorkflowArtifactReference
  }
}
```

Its reference is
`<session>:ordered-validation-repair-adoption:<frontierRevision>@1`.
Mechanical effect is pure Unit. No stage, source-history, candidate observation,
SBT, local commit or automatic Advance executes during adoption. One explicit
`validation-repair-adopted` CML transition takes PhaseValidation→Implementation
for source2 or Decision→Implementation for source1. No fake intervening Decision
or successful/failed Test result is inserted. Base, completed Steps/results,
findings/issues/prior reviews, repair limits/counts and old unknown/incomplete
records stay exact; generation increments once. Clear only active candidate,
checks/review/closure projections for genuinely new work.

Separate normal Advance reconstructs the adjacent Direction from its original
native prefix and captured policy. Do not reinterpret its accepted historical
association as mechanical proof or rewrite the unresolved old result. Before
the **first** new corrective ImplementStep request, and only if no such retained
request already exists, reuse native candidate preconditions: typed UoW
repository root/status/head/index/PendingPaths with original candidate and
original document/recording scope; current candidate/path/closure/check and
opaque process-plan/policy comparison. Candidate/index/foreign-dirty or live
policy drift rejects before issuing work. No machine-wide lock/process gate.

Here foreign-dirty means dirt rejected by that existing scoped predicate, not
every status entry outside the proposed Step. Preserve already allowed unrelated
changes (including the consumer probe above) byte-for-byte. The new Step does
not acquire their ownership. This is reuse of existing admission, not a broader
allowlist or a clean-tree prerequisite.

After a corrective request is durably issued, later reopening uses that exact
request/result traversal. It does not reapply the old pre-repair worktree check
to legitimate new edits or issue the work twice. No private once-only flag or
new registry is introduced. The normal new Step includes actual scoped work,
fresh typed check, required independent Step review, local Step commit, required
whole Test, one integrated independent Phase review and ordinary local closure.
All prior successful accepted operations remain retained, not re-executed.
Historical interpretation uses its captured path/closure prefixes; later allowed
configuration extensions do not rewrite old Directions. Current effect grants
still control future effects.

## 8. Raw result preservation, finite profile and Resource transport

Keep v1's complete byte-codec contract and v2's receipt/code audit: append the
seventh optional actual CNCF ProcessExecutionResult to the six-argument Receipt;
explicit legacy/new nested codec dispatch; new tagged
`sm-workflow.native-sbt-validation-receipt.v2` stores canonical Base64 raw capture
once with exact retained byteCount/truncated/actual termination/elapsed/program
and empty tagged-SBT artifacts. Legacy shape/text/facts encode unchanged, no
backfill or failed-new-reader fallback. Both enclosing Goal and Repository
codecs reopen old/new data. Preserve malformed-text, exact selection and complete
success guards, and validate the new member at direct review-proof boundaries.
No Receipt positional extractor was found in the authored main/test Scala audit;
existing constructor calls keep compatibility via the final default None.

New real OutputLimitExceeded+Incomplete persists as incomplete and reaches an
explicit Decision; it never becomes Exited143 or a completed failed/successful
Test. Split trailing UTF-8 bytes remain raw and presentation says prefix or
unavailable. Malformed interior/NUL does not become replacement text. Actual
TerminationUnconfirmed, including truncated capture, stays unconfirmed and
ineligible for adoption. Complete-output CommonEvidence excludes incomplete
captures. RepositorySync retains its new incomplete receipt and Wait; it gets
no Goal historical-adoption capability. Existing timeout/known-failed contracts
and their successful evidence remain unchanged.

The existing normal settings for the isolated proposed single-check profile are
host stdout33554432 / stderr8388608 and matching `cncf.sbt.test.*.maximum-bytes`,
plus existing `workflow.mechanical-receipt.max-bytes=67108864`. Test defaults
64/8MiB remain unchanged; the effective bound remains min(property,host limit).
Keep existing launch/execution/grace and artifact/workArea limits. Forty MiB
raw capture has at most55924056 Base64 bytes; test the actual serialized native
current/next histories with a representative2162-event envelope against the
existing64MiB hard maximum. Do not drop history/output or raise that maximum
if it does not fit. No universal arbitrary-history/multi-check capacity claim.
Git's distinct1MiB default/finite16MiB explicit maximum remains separate.

Both new Operations use private generic `NativeWorkflowResourceInput`, extracted
from the existing `NativeWholePhaseRecoveryInput` transport, with the old helper
delegating unchanged. In the actual ProcedureActionCall use the current
`read_resource_text(...,Some(UTF_8))`, then the identical generated business
decoder and normal Session/admission port. URI is absolute local file, no
authority/query/fragment; enforce XOR and existing Resource roots, finite read
limit, streaming acquisition and strict UTF-8. No Files.readString, inline
fallback, hand-reduced target or new application byte ceiling. Description and
adoption both support full DTO Resource input; source-page Resource read is
subject to the same current rules.

The installed acceptance must use a legitimate normal resolved host plan large
enough that the full returned opaque processPlan/DTO exceeds measured platform
ARG_MAX plus the environment allowance, while fitting the normal finite16MiB
Resource bound. Use actual declared finite fixture programs/policies, not padding
an ignored field or hand-editing processPlan. Write the exact full target/proposal
and independently provisioned answer to an allowed file and invoke the installed
CLI with only its short `--inputResource file:///...` argument. Confirm actual
size/limit and exact adoption/reopen; description must support the same path.
If legitimate plan/receipt/Resource bounds conflict, report that exact failed
qualification. A mocked reader or a smaller inline fixture is not a pass.

## 9. Exact authored inventory: 45 paths

The seven conditional v2 source-binding paths are now part of the proposed
frozen design. **45 authored paths, no upstream/development-workstation source
edit and no additional public Operation**, four public DTOs and two actual
Provided entries (25→27). The new configuration policy and its decoder/tests
are owned by rows10,41,42 and documented in rows19,21,44,45; no unnamed verifier
or separate policy helper remains outside this inventory. This is still a
design inventory, not a Human-admitted worker mutation manifest.

All paths are relative to producer `/Users/asami/src/dev2026/sm-workflow`.
Unrelated current deltas in every file must be preserved at implementation entry.

| # | Authored path | Frozen purpose |
| --- | --- | --- |
| 1 | `src/main/cozy/sm-workflow.cml` | Four DTOs/two Operations, XOR inputs, optional receipt projection, incomplete/adoption edges and new preparation-purpose guidance. |
| 2 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowSbtCapture.scala` (new) | CNCF raw result/capture codec and truthful text projection. |
| 3 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncNativeMergeValidation.scala` | Seventh member/dual nested codec and real incomplete admission, strict success. |
| 4 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeValidation.scala` | Actual output-limit persistence/incomplete source and native pending-chain reconstruction. |
| 5 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseOrderedProgression.scala` | Pure incomplete/adoption entries, immutable accepted prefix/base/counters. |
| 6 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseValidationRepairAdoption.scala` (new) | Closed contracts/handlers, source-specific choice/evidence checks and Direction-only CAS. |
| 7 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeWorkContinuation.scala` | Current-source ports/traversal and retained Direction dispatch with pre-first-work guard. |
| 8 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeRun.scala` | Optional installation/source-policy and native storage operation admission. |
| 9 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeServiceAssembly.scala` | Register both actual public Operations. |
| 10 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowRuntimeParameters.scala` | Declare/resolve one optional caller-source component key and installation through CNCF normal composition. |
| 11 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowExternalEvidencePorts.scala` | Existing independent issuer/store admission for exact new purpose. |
| 12 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowLauncherAssembly.scala` | Publish both real Operations. |
| 13 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/NativeExternalEvidence.scala` | Distinct purpose/closed binding under existing v7 evidence envelope. |
| 14 | `src/main/scala/org/simplemodeling/textus/smworkflow/preparation/NativeExternalEvidenceInput.scala` | Register passive GoalPhase validation-repair-adoption preparation. |
| 15 | `src/main/scala/org/simplemodeling/textus/smworkflow/preparation/NativeValidationRepairPreparation.scala` (new) | Exact description/target/proposal/actual answer StartupImport conversion, no authority creation. |
| 16 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/NativeWorkflowOperationOutput.scala` | Both Repository and one Goal generated Receipt constructor/projection sites. |
| 17 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseValidationRecovery.scala` | Optional concrete preparation guidance, old UNKNOWN/timeout choices unchanged. |
| 18 | `docs/spec/normal-typed-sbt-validation.md` | Raw wire, dual compatibility and finite capacity. |
| 19 | `docs/spec/goal-phase-validation-repair-adoption.md` (new) | Full contract/config/source/trust/effect/continuation scope. |
| 20 | `docs/spec/goal-phase-validation-recovery.md` | New route guidance without altering old permissions. |
| 21 | `docs/spec/normal-host-preparation-operations.md` | Passive purpose and resolved-policy/independent-answer boundary. |
| 22 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowSbtCaptureSpec.scala` (new) | Exact bytes/UTF-8/termination/strict codecs and actual maximum native-envelope encoding. |
| 23 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeTestOnlyValidationReceiptSpec.scala` | Both enclosing old/new codecs and actual selection/success guards. |
| 24 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseValidationRepairAdoptionSpec.scala` (new) | Closed target/source/choice/limitations/evidence, pure transitions, CAS and refusal matrix. |
| 25 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeGoalOutputLimitValidationCliSpec.scala` (new) | Real new-runtime UoW limit, durable incomplete result, fresh installed reopen/concrete-result repair route. |
| 26 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeGoalUnobservedPhaseRepairAdoptionCliSpec.scala` (new) | Genuine382 defect→same isolated Session→one historical adoption→ordinary closure, large Resource and negative installed cases. |
| 27 | `src/test/scala/org/simplemodeling/textus/smworkflow/GoalPhaseGeneratedAbiSpec.scala` | Exact27 Provided and generated XOR parameter/result types; no absent-input acceptance. |
| 28 | `src/test/scala/org/simplemodeling/textus/smworkflow/SmWorkflowGeneratedHelpSpec.scala` | Generated names/inputResource/business descriptions. |
| 29 | `src/test/scala/org/simplemodeling/textus/smworkflow/ComponentPackagingDescriptorSpec.scala` | Exact packaged service/operation/input inventory. |
| 30 | `src/test/scala/org/simplemodeling/textus/smworkflow/GoalPhaseCmlDerivationSpec.scala` | Producer13/six derivation and27 Provided, persistence-compatible identity. |
| 31 | `src/test/probes/GoalPhaseCmlDerivationProbe.scala` | Producer inventory only; no consumer Triage/count rewrite. |
| 32 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowDirectOperationSpec.scala` | Actual generated publication-set assertions, no weakened inventory check. |
| 33 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/NativeWorkflowResourceInput.scala` (new) | One generic XOR/URI/full generated DTO Resource transport. |
| 34 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/NativeWholePhaseRecoveryInput.scala` | Preserve old facade API by delegation. |
| 35 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeWorkflowResourceInputSpec.scala` (new) | Both new DTO transport/read/decode/error boundaries. |
| 36 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeWholePhaseRecoveryInputSpec.scala` | Existing old-transport regression after extraction. |
| 37 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeReviewCompletion.scala` | Validate new optional member through strict shared proof validation. |
| 38 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeReviewCompletionSpec.scala` | Refuse malformed new capture, preserve genuine old review evidence. |
| 39 | `src/main/scala/org/simplemodeling/textus/smworkflow/caller/NativeCallerInformationSource.scala` (new) | Original page/locator item selection shared by old caller and new native adapter, no authentication invention. |
| 40 | `src/main/scala/org/simplemodeling/textus/smworkflow/caller/NativeCallerInformation.scala` | Delegate selection, preserve actual issued-context/current behavior. |
| 41 | `src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseValidationPriorInvocation.scala` (new) | Closed selector/policy/snapshot codec, exact command/cwd check, actual observation clock and historical limitations; never native causal proof. |
| 42 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseValidationPriorInvocationSpec.scala` (new) | Source/command and normal CNCF parameter decoding/installation absence/refusal cases, real historical attribution limits. |
| 43 | `src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeCallerInformationOperationSpec.scala` | Existing issued-context/source behavior preserved after extraction. |
| 44 | `docs/spec/caller-information.md` | Actual caller provenance vs native Human-adopted association; no fake Request or new verifier. |
| 45 | `docs/spec/host-terminal-source.md` | Current concrete route and continued withdrawal of provider proposal. |

Generated **separately**: normal cozyGenerate may change tracked
`src/main/catalog/car/org/simplemodeling/textus/textus-sm-workflow.yaml`; managed
DTOs/component/GoalPhaseRunLifecycle/ABI/help/Provided/workflow resources under
target are generated outputs. Never hand-edit them. No manual test registration,
new product main, global SBT lock, generic Process fallback or compatibility
alias. Journal versions and private retained source are design/evidence files,
not additional authored production paths. Existing other suites may be run as
regressions without editing them; no unrelated source edit is authorized.

Producer remains13 configurations/six Required, same workflow identity/revision.
The consumer's dirty `GoalPhaseOrderedTriageProducerProbe` contract of14/seven
and original31 acceptance conditions remain consumer-owned; no producer probe
replacement or count normalization is part of these45 paths.

## 10. Negative/refusal matrix

Every refusal leaves old native results/history/accepted prefix/counters exact
and launches no SBT or Git mutation. Existing CAS/ordinary UoW admission remains.

| Class | Must refuse |
| --- | --- |
| Source availability | Missing page/locator, partial or truncated selected item, duplicated selected turn/item, wrong type/status, missing/noninteger/null exit, bad duration, extra closed item fields. |
| Command/policy | Missing/invalid resolved source policy; different thread, executable, cwd, RuntimeRoot, Session, operation, task/output, quoting or added argv/redirects/shell segment; policy change after description. |
| Provenance claims | Diagnostic terminal.json/PID/log in place of public page; supplied item/ended Boolean/receipt; fabricated issuedWorkContext; passive import/caller identity treated as authenticated permission. |
| Native source | Not all Steps accepted; nonlatest intent; existing result/final/uncertainty/continuable branch or successor; stored TerminationUnconfirmed or known-live observation; Step/multiple/dependency/metadata/document-backed validation; unknown Git/stage/commit/push/closure. |
| Closed contracts | Unknown/missing/null/tag-mismatched source/target/limitations; malformed opaque JSON; source kind swapped after failed proof; new capture contradictions/invalid Base64/count/truncation/selection. |
| Plan/acceptance | Missing/lost/reordered/changed original Steps or accepted commit/review; changed base/identity/repository/Phase/check/review/acceptance/repair limit; multiple/empty new Steps, missing dependencies, paths/checks/commit mapping not admitted. |
| Human/evidence | Old083/090/timeout/Resume/ApproveEngineering/design approval; source2 with ordinary AdoptRepairPlan; missing/wrong participant/issuer/reference/time/purpose; any changed actual item/native target/Plan/limitations; AI instead of actual Human; duplicate/stale/crossed evidence. |
| Current-state drift | Frontier/generation/check/attempt/candidate/base/Plan/counters/prefix drift at CAS; head/tree/index/dirt rejected by existing scoped preconditions, or check/path/closure/process/source-policy drift before the first new work request. Already permitted unrelated dirty paths are preserved, not newly rejected or editable. |
| Resource transport | Both/neither inputs, relative/remote/authority/query/fragment URI, root/symlink denial, missing/over-limit file, invalid UTF-8/JSON/explicit null, source changed or unavailable at adoption. No inline fallback. |
| Replay/concurrency | Second adoption loses native CAS; separate read/reopen never reruns old check/stage/commit; already issued corrective work reused despite legitimate edits; no private retry flag or implicit new authority. |

Defer is a valid nonmutation choice, not an error or a trigger to broaden scope.
A failed new corrective validation retains its actual result and uses existing
ordinary repair/Human boundaries; the historical choice is consumed, not a
standing grant for further UNKNOWN attempts.

## 11. Actual execution acceptance after implementation admission

These are **required future qualifications**, not claimed results of writing v3.
Use behavioral Given/When/Then and current test labels, one independent focused
source review and applicable exact prior evidence. Do not add a new consumer
Phase-review lap or rerun accepted original successes.

1. Raw-byte/UTF-8/codec controls: split multibyte boundary, malformed interior,
   NUL, both streams/counts/canonical Base64, strict complete success, TestOnly
   selection contradictions, actual incomplete and unconfirmed variants, all
   legacy/new enclosing roundtrips and exact real64MiB mechanical envelope.
2. New runtime: real finite ordinary UoW SBT Test exceeds a deliberately small
   stderr limit; persist actual raw incomplete result, return incomplete Decision,
   read identical raw data from another installed CLI process without a new SBT
   launch. Complete real concrete-result corrective route with its own choice.
3. Old runtime: pin immutable installation
   `382e8019-45b6-4572-884c-f7eb67b02e7e` for a **new isolated** repository,
   RuntimeRoot/storage/Session. Establish actual accepted prefix through normal
   work/review/local commit. Run one finite normal typed whole Test with a small
   actual stderr ceiling. Require real CNCF OutputLimitExceeded followed by the
   old sm-workflow capture-admission error, saved latest intent with no result/
   final receipt and separately described UNKNOWN. No fault injection, manual
   result deletion or direct top-level SBT substitute qualifies this fixture.
4. Preserve the complete actual caller public tool exchange/locator and original
   outputs. Its command must match section3. Capture a native-history baseline,
   accepted commit/review/result byte/semantic snapshots and actual launch count.
   The fixture's new source is genuine, not a hand-authored public tool page.
5. Switch only that isolated caller to the new qualified installation; do not
   Start again, reset counters, migrate/backfill unknown results or replay the
   old Test. In a separate installed process describe the same UNKNOWN and one
   actual appended corrective Plan. This qualifies persisted-definition/source
   compatibility, not just case-class decoding. Retain exact old prefix.
6. Through the normal independent fixture Human-evidence path, admit one exact
   historical-association/unknown-effects/Plan answer with its genuine source
   provenance, reference/issuer/time and complete limitations. Specification-
   owned fixture authority is labelled as such, never confused with approval
   of the real consumer Session. Use the above-ARG_MAX real full inputResource
   path and actual finite host-plan/Resource bounds. Adopt once; verify one
   Direction and no old result/retry/automatic Advance.
7. Reopen in another installed process; separate normal Advance issues the one
   corrective work request after real preconditions. Reopen pending work and
   verify the same request. Perform genuine bounded new repair/check, independently
   accepted Step review, local Step commit, new whole Test, one integrated Phase
   review and normal local closure. Keep old UNKNOWN unresolved in history and
   all old successes unexecuted. Record exact effect/launch counts and counters.
8. Exercise the refusal matrix, changed source/current policy, candidate/foreign
   dirt before issuance, duplicate/concurrent adoption and genuine later work
   edits. Do not use an in-process mock alone for installed admission/reopen/
   Resource/old-runtime paths. Mocked source checks qualify only structural
   refusals, not authentic API acquisition or real end-to-end acceptance.
9. Normal generation/Test compilation, focused tests, independent review,
   necessary normal publishLocal and runtime build/acceptance/activation must
   cover the exact admitted producer bytes. Qualify installed CLI origin and
   UoW SBT from the actual immutable manifests. Registered top-level producer
   restoration builds use sbt-native locks; product SBT stays inside UoW. No
   scripted/global serialization prerequisite and no user process termination.

The genuine caller-source qualification cannot be simulated by launching the
old CLI as an unexposed child inside ScalaTest and labelling the outer SBT item
as that CLI item. Use these fixed qualification stages under row26's test-owned
isolated fixture, without adding a product main or new source-provider script:

- The setup stage establishes the actual old-runtime accepted prefix and saves
  its isolated RuntimeRoot/Session and exact finite qualification invocation.
  It does not run the final whole-Phase Test or create an alleged source page.
- The existing caller invokes that exact old **installed CLI Advance directly**
  once, using its ordinary command tool and retaining its actual terminal item.
  Product SBT execution is still inside UoW. The caller obtains the actual public
  page/locator from its existing API and forwards the opaque source resource;
  it performs no source parsing, proof assembly or target construction.
- The subsequent installed qualification stage, implemented in the same row26
  spec, consumes the retained isolated fixture and genuine page. It verifies
  old UNKNOWN without another launch, switches the fixture installation,
  calls normal describe/adopt via full Resource input and continues only new
  work/review/commit/whole Test/closure. Test-owned independent fixture Human
  authority remains explicitly identified; it is not a real-Session answer.

Do not rerun the successful setup or original old Advance when resuming the
qualification stage. Ordinary unit/in-process CLI cases may use specification-
owned shaped source for negative coverage, but that is separately reported and
cannot satisfy the genuine public-source stage. All interpretation remains in
program code; the caller makes the ordinary finite command/API calls only.

Include an additional positive scoped-dirty control: an already permitted
unrelated dirty file remains exactly unchanged through description/adoption and
new work issuance, while actual owned candidate/index drift is refused. This
uses the existing candidate predicate, not a special qualification exception.

The consumer's
`inputs/p65-prospective-unknown-corrective-step-analysis.json` is analysis only.
Its five diagnostic specs are within the consumer's Plan2 ownership according
to its read-only assessment. They are not producer repair paths, a formal FAILED
result, an adopted Plan or issued work, and are not added to these45 paths.

Do not use real091/actual Session to make this fixture, consume old Human083/090,
operate actual116/6.5 state, repeat a successful original Compile or imply all
consumer31 conditions passed. A previous generated-ABI failure remains separately
reported until its actual scoped resolution is verified. Reproduction or narrow
unit success alone does not establish installed closure qualification.

## 12. Review and authority sequence

V3 freezes the design requested by consumer review, including the one new
resolved-policy key within45 authored paths. The historical-association safety
gap is resolved **as a public-contract recommendation**, not by asserting a new
trusted verifier exists. The recommendation is ready for consumer review and
then a concrete Human implementation-scope decision. It has not been executed
or independently code-reviewed and grants no actual Session effect.

After Human actually admits implementation, freeze the live owned deltas and
worker Fix Manifest against these paths, implement/qualify/review/publish and
deliver exact installed identity, outcomes/limits and continuation procedure.
Only after qualified delivery does the real Session owner inspect its current
public description and request the one concrete Human historical-association/
Plan decision. That real decision is distinct from authorizing the producer
implementation. Do not ask a preliminary high-level permission question or
perform real adoption to demonstrate the design.

Preserve Goal, Session, base, accepted Steps/results, original31 requirements,
PLAN epoch/revisions, history/finding/failure ledgers, repair counters, all dirty
files/generated artifacts, old timeout/UNKNOWN records and prior successful
evidence. No source/test/config implementation, SBT, publishLocal, runtime
activation, commit/push, Start/Submit/Advance/recover/adopt was performed for v3.
Actual Session progression remains with `sm-workflow-dev開発`.

## Agent usage

Parent-only continuation of design. Exact parent model/effort are unavailable;
no inference from earlier model settings. No worker, reviewer, SBT or runtime
agent started/resumed; the existing incident's single DIRECT disclosure covers
these parent reads/design records.

| Agent | Exact model | Effort | Runtime / role | START | RESUME | DIRECT |
| --- | --- | --- | --- | ---: | ---: | ---: |
| `/root` | unknown | unknown | parent runtime / diagnosis and design | 0 | 0 | 1 |
