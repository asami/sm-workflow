# GoalPhase scoped quality admission

Author: ASAMI, Tomoharu. Since: Oct. 17, 2026. Version: Oct. 18, 2026.

## Applicability — Phase 10, adopted 2026-10-18

This CBD-specific specification belongs to [Phase 10](../phase/phase-10.md).
Its evaluation/coverage is not required or claimed in initial production use.
Phase 6.1 must connect the existing actual reviewer/Admission/authority route
without mandatory CBD calls; 6.2/6.3 retain ordinary review and validation.
This edit does not change the existing source or certify that route yet.
Historical CBD fixtures/results retain only their original evidentiary scope.
See the [handoff](../journal/2026/10/2026-10-18-production-first-cbd-review-phase-10.md).

## External execution target — adopted 2026-10-18

CBD Phase 15 owns scoped evaluation; Phase 15.1 owns common request preparation
and external-result acceptance. Their
[execution specification](https://github.com/asami/textus-cbd-support/blob/main/docs/spec/scoped-review-execution-contract.md)
is the provider contract (the local adopted document may precede publication).
The sections below retain existing implementation/spec evidence. The following
target changes their synchronous acquisition/SPI-only assumptions for the
orchestrated route; it does not claim that transition already implemented.

Prepare review knowledge before issuing semantic work. Carry the request/review,
subject and selected scope through the existing work/Continuation, receive the
actual executor's typed result, submit it to CBD acceptance and consume the
ordinary returned result through the existing CAM. Keep the same pending Review
and ordinary Submit/Advance/commit authority. Submit must not invoke another
hidden semantic CBD AI review. CBD remains a CAR Operation boundary, without a
Maven dependency on its generated types or a second local Admission engine.

Keep existing requirement, coverage, deterministic finding and blocker semantics.
Adapt result decoding and provenance policy explicitly for the actual external
route: do not require fabricated Textus AI SPI facts from Codex, treat a logical
review purpose as proof of AiRunner execution, or guess provider/model identity.
Required attribution must describe the actual execution and selected subject;
missing facts remain an explicit gap. Reuse lower-level checks at their owner.

The consumer chooses same-context execution where independence is not required;
existing independent Review requirements select an explicit independent executor.
Neither one worker's approval nor a typed reference proves independence or
grants commit permission. Reuse the existing workflow for result/continuation;
no new conversation storage, replay/deduplication engine or compatibility alias.

Connected acceptance requires prepare -> actual external review -> CBD result
acceptance -> CAM -> same Review/Submit/Advance -> authorized Step commit, with
zero CBD AI-provider calls on the orchestrated preparation/result path. Retained
fixture/compiler/SPI results remain evidence of their original scope, not this
new route. Validate changed connections; do not repeat unchanged entire suites.
Phase 10 awaits accepted required CBD15/15.1 handoffs and owns this connected
acceptance. Phase 6.3 retains its separate initial-use aggregate full validation;
that validation does not accept future Phase 10 changes.

`GoalPhaseScopedQualityAdmission` is an internal passive consumer projection
from the normal CNCF `OperationResponse.RecordResponse` returned by
`CbdSupport.CbdReviewAdmin.reviewScopedQuality` to the existing CNCF
`CandidateAdmissionModel` (CAM). CBD is a CAR component; this boundary requires
no CBD Maven compile dependency or direct generated CBD class import.
`GoalPhaseScopedQualityReview` connects this projection to the normal Goal
Review/Submit evidence port. Typed fixture examples below establish consumer
behavior only; real provider execution and connected runtime acceptance remain
pending actual verification.

## R1: Selected typed requirements and ordinary evidence

The application's internal `Selection` contains ordinary review ID/revision, subject
ID/revision, checks, paths and exact model subjects. Empty, duplicate or unknown
check selections and missing target or general scope are invalid input. Null
typed fields, collections and options are errors rather than absent evidence.
Requirements and materials belong to the later actual Operation request and
are not CAM selection inputs. Internal `Selection`, `Result`, `CheckResult`
and `Finding` values are application projections. They define no public CML,
wire schema, `RecordPresentable` contract or output encoder.

Exactly the selected checks become CAM requirements, in selection order.
`cml-contract` is Deterministic with empty path scope and exactly the selected
model subjects. Every other check (`semantic-consistency`,
`implementation-quality`, `excessive-checks`, `integration`) is Semantic with
the selected paths and model subjects. Scope type is
`sm-workflow.scoped-quality-scope.v1`. Missing CML model scope remains an
explicit coverage gap; paths never supply invented model coverage.

The candidate reference is the requested subject ID/revision and its typed
payload type is `sm-workflow.scoped-quality-selection.v1`. Every returned check contributes one
CAM evidence entry with its returned scope, full typed
`ScopedQualityReviewResult` payload, returned subject reference, ordinary review
ID plus check ordinal identity, and review ID provenance. The full result
retains review revision, rule revision, provider, purpose and model. A duplicate
result check is invalid; extra/unrequested checks are also invalid. No returned
entry is silently selected or discarded. Only the existing CAM evaluates
correlation, evidence/scope type and coverage.

Executable examples: C1, C2 and C4 in
[GoalPhaseScopedQualityAdmissionSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseScopedQualityAdmissionSpec.scala).

## R2: Coverage, provenance and failure gaps

Absent results or selected checks yield CAM `Missing`. Returned subject or
subject revision mismatches yield CAM `WrongTarget` using the actual returned
reference. Freshness requires the requested ordinary review ID/revision,
subject references and fixed rule revision `cbd-scoped-quality-v1`; mismatches
yield CAM `Stale`. Freshness does not compare material bytes, hashes, diffs,
copies, timestamps or TTLs.

Coverage requires result status `completed`, matching check status `covered`,
exact selected path and model subject sets, no check limitations and a
nonblank provider ID. Semantic checks additionally require purpose exactly
`car-review.semantic-consistency`. Deterministic CML coverage requires nonempty
selected model subjects and does not require semantic purpose or model.
Unsupported/failed/unavailable status, partial or mismatched scope, missing
provider attribution and missing/mismatched semantic purpose remain CAM
`InsufficientCoverage` gaps. Empty findings do not establish coverage.

Unknown or null statuses, malformed checks/findings/options, unknown severity
and findings outside the requested check/scope fail as typed input errors.
The allowed result statuses are `completed`, `unavailable`, `failed`; check
statuses are `covered`, `unsupported`, `failed`. Input errors do not become
successful or missing results.

Executable examples: C2 and C4 in
[GoalPhaseScopedQualityAdmissionSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseScopedQualityAdmissionSpec.scala).

## R3: Findings grant no effects

Evaluation retains all typed findings and exposes read-only `blockingFindings`
for severity `blocker`. `hygiene` and `development-candidate` remain retained,
nonblocking findings. `requireAdmittedC` accepts only `accept`, `repair` and
`decision-needed`. Any CAM gap prevents every disposition with a typed
state-conflict diagnostic containing check IDs and defects, without source
material. With no gaps, blocker findings prevent `accept`; `repair` and
`decision-needed` retain the actual findings for existing lifecycle handling.
An accept string or zero findings alone never yields admission.

Evaluation also retains the actual typed result, when present. On CAM gaps,
the diagnostic preserves the existing check-ID/defect prefix and appends the
fixed result status and returned selected check IDs/statuses (at most five
fixed check identities). Per check, only these known CBD limitation codes are
reported, uniquely and in sorted order: `ai.spi.unavailable`,
`ai.purpose-or-provider.unavailable`, `ai.execution.failed`,
`ai.response.invalid`, `ai.provenance.unavailable`. Unknown limitation text is
replaced by a fixed `redacted` marker. Provider/model IDs, paths, subjects,
findings, raw responses, source, diffs and requirements are never included in
the appended diagnostics. Admitted success is unchanged. Retaining typed causes
does not relax coverage, freshness, scope or blocker gates, authenticate stored
references, or establish actual provider completion; connected integration
remains pending separate actual verification.

This pure adapter performs no provider, file, process or network execution. It
does not authenticate a provider, consume a worker proof/trust bit, issue
authorization, choose a lifecycle transition, allocate repair or permit a
commit. Existing owning lifecycle and authority boundaries remain responsible
for those effects. Real CBD provider tests are separate producer evidence.

Executable examples: C1, C3, C4 and C6 in
[GoalPhaseScopedQualityAdmissionSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseScopedQualityAdmissionSpec.scala).

## R4: Ordinary Operation Record decoding

`decodeResultC` consumes the ordinary camelCase CML result keys only:
`reviewId`, `revision`, `subjectId`, `subjectRevision`, `status`, `checks`,
`findings`, `providerId`, `purpose`, `model` and `ruleRevision`. Nested checks
use `checkId`, `status`, `paths`, `modelSubjects` and `limitations`; findings
use `identity`, `checkId`, `severity`, `path`, `modelSubject` and `message`.
Unrelated ordinary fields are ignored. Duplicate keys within any decoded
Record are rejected before an occurrence can be selected.

Required strings must be actual strings; missing, explicit null or wrong
scalar types fail as typed input. Required `revision` uses the existing Long
ValueReader, including integral BigDecimal values, followed by the existing
positive revision validation in `evaluateC`. Optional strings are `None` only
when absent; explicit null and wrong types fail. Field existence distinguishes
an omitted field from a present null.

CML `*` multiplicity permits omitted empty collections. Absent `checks`,
`findings`, `paths`, `modelSubjects` and `limitations` decode as empty vectors;
explicit empty sequences are also valid. Present values must be existing
Vector/Seq/Array containers with valid elements. Nested checks and findings
must be actual Records. The decoder never parses strings into arrays or
Records, stringifies arbitrary scalars, or silently discards invalid elements.
Invalid-input diagnostics contain fixed field names without source payload.

Successful decoding establishes only the input's shape. Actual CAM correlation,
freshness, coverage and findings gates still apply: omitted checks remain
`Missing`, unsupported or partial coverage remains pending, and blockers
prevent acceptance. Decoding does not authenticate a provider. C5 Record
fixtures exercise decoder-to-CAM behavior and are not actual provider evidence;
the connected normal Operation route still requires real provider validation.

Executable examples: C5 in
[GoalPhaseScopedQualityAdmissionSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseScopedQualityAdmissionSpec.scala).

## R5: Host-first normal accept gate

The normal `ThreeWorkflowHostEvidencePorts.goalReview` first constructs its
existing binding and requires independently issued host evidence for the actual
submission. Only after that proof does `GoalPhaseScopedQualityReview.admitC`
process the disposition. `repair` and `decision-needed` acquire no configuration,
files or provider result here; their existing lifecycle validation still applies.
Missing or unknown dispositions fail. Every `accept` must pass this gate; no
operation input, product configuration flag or worker success can bypass it.

For accept, the actual call's `core.component.flatMap(_.subsystem)` owns the
invocation. The helper constructs `Request.of(component = "CbdSupport", service
= "CbdReviewAdmin", operation = "reviewScopedQuality")` with each ordinary flat
request Record field mapped to `Property(field.key, field.value.single, None)`.
It invokes `executeOperationResponseInChildContext(request,
core.executionContext)`, retaining the normal component Operation, ActionCall,
ProviderEngine and provider-step UoW route. Only the actual
`OperationResponse.RecordResponse` is accepted. Missing component, subsystem,
operation or provider, and failed or malformed responses, prevent acceptance.
There is no direct CBD implementation import or alternate response encoding.

The actual returned Record flows through `decodeResultC`,
`evaluateC(selected, Some(actual))` and `requireAdmittedC(evaluation, "accept")`.
Returned references, coverage, gaps, limitations and findings remain the inputs
to the existing CAM and blocker gate. There are no successful-coverage defaults.
Host effect authorization remains separate from typed response shape and CAM
coverage. The helper adds no lifecycle transition, storage contract or public
result DTO.

Executable examples: B2-01 through B2-05, B2-13, B2-16 and B2-17 in
[GoalPhaseScopedQualityReviewSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseScopedQualityReviewSpec.scala).

## R6: Actual assignment scope and current model configuration

Selected paths are exactly the nonempty, unique `input.review.changedPaths`.
Each must be a literal workspace-relative filename accepted by
`WorkspaceFilePath.parseC`; URI, absolute, control, backslash and parent paths
are invalid. The ordinary review ID is the submitted result-reference identity;
review revision is `input.generation + 1`, requiring a nonnegative generation
below `Long.MaxValue`. Subject ID/revision are `input.instanceIdentity` and
`input.sourceRevision`. These IDs are nonblank, control-free and at most 128
characters. Paths and termIds are at most 1024 characters.

The four semantic checks are always selected, in order:
`semantic-consistency`, `implementation-quality`, `excessive-checks`,
`integration`. Explicit configured model scope prepends `cml-contract`; the full
selected termId vector remains model scope for every check. CML deterministic
scope retains empty paths under R1. No generated-file relationship, identity
registry, content hash, copied-source seal, TTL or retry is introduced.

The helper resolves these internal keys from the current
`core.executionContext.resolvedParameters` on every accept call:

| Key | Required exact type and meaning |
| --- | --- |
| `sm-workflow.scoped-quality.model-metadata` | `ConfigurationValue.StringValue`: one exact literal workspace-relative metadata filename |
| `sm-workflow.scoped-quality.model-subjects` | `ConfigurationValue.ListValue`: nonempty distinct `StringValue` entries containing the exact actual Cozy termIds, each at most 1024 characters |

Both properties must be present together or both absent. Null, partial, wrong
typed, empty, duplicate or invalid configured values fail with
`configurationInvalid` and fixed diagnostics containing no raw values. There
is no manual command parsing, environment/system-property lookup or boot-time
configuration snapshot. An actual changed `.cml` path requires both properties.
When both are absent and there is no changed `.cml`, `cml-contract` is explicitly
not applicable to this selection; all four semantic checks remain mandatory.
When both are present, `cml-contract` is selected even for a non-CML changed path,
and the exact selected metadata file must be acquired. Missing metadata fails;
SM does not duplicate CBD's schema or declaration lint.

Executable examples: B2-04, B2-06 through B2-11 and B2-19 in
[GoalPhaseScopedQualityReviewSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseScopedQualityReviewSpec.scala).

## R7: Complete bounded material and requirement acquisition

Each selected changed filename is read through `WorkflowFileExecution.readTextC`
on logical `FileTarget` workspace `sm-workflow-repository`, under the actual
owning call. Existing File failures propagate. Missing text for a deleted file
contributes no source material, while the selected path and actual review diff
remain present. Available source materials have fixed ordinal IDs `source-1`,
`source-2`, and so on, and their actual selected paths. The actual `Context.diff`
always contributes one `diff-1` material. Configured metadata is acquired through
the same logical File port and contributes exactly one `metadata-1`
`cml-metadata` material without an invented modelSubject or unrelated path scope.

Requirements contain the actual assigned purpose, applicable document instructions
from `GoalPhaseDocumentSource.instructionsC(input.documents, input.stepIdentity)`,
and applicable saved effective-acceptance usage/descriptions. Worker success or
its acceptance summary is never substituted for those requirements.

Every request vector is bounded by 64 entries. Each material content is bounded
by 262144 UTF-8 bytes, and all material plus requirement content together is
bounded by 1048576 UTF-8 bytes. Requests exceeding any bound fail before provider
invocation. Material is not truncated, split into extra requests, or removed to
fit the bound; selected files and conditions are never silently dropped.

Executable examples: B2-01, B2-09 through B2-15 and B2-18 in
[GoalPhaseScopedQualityReviewSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseScopedQualityReviewSpec.scala).

## R8: Fixture evidence and pending connected acceptance

The package-only recording `Port` exercises logical file acquisition and ordinary
request/response Records with current `ResolvedParameters`. Production always
constructs its File/owning-subsystem port; no product setting or operation input
selects a recording port. Recording responses use observed request references
and scope to exercise the existing CAM. They establish no provider authenticity
or independent host authority.

Actual provider execution, real host-proven Submit and subsequent Advance,
the actual assigned C4 review and CML coverage, and local commit remain pending
until separately verified on their normal runtime paths. Consumer fixture
qualification alone cannot close those requirements or authorize effects.

### Explicit retained C4 closure consumer

The manual `@DoNotDiscover`
[NativeGoalScopedQualityClosureCliSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeGoalScopedQualityClosureCliSpec.scala)
encodes one finite continuation of the original isolated C4 source-history
session. It requires all of these explicit JVM properties:

| Property | Required actual input |
| --- | --- |
| `smworkflow.spec.scopedQualityRoot` | Existing canonical direct child of `target/sm-workflow/work/native-cli`, retaining `provider-review.json`, the original `provider-source-history-task`, actual Start input, host configuration, source and successful Step Test |
| `smworkflow.spec.scopedQualitySubmit` | Existing canonical file under that root containing the actual normal accepted Submit output: `accepted=true`, `outcome=accepted`, and the exact retained review/task session ID |
| `smworkflow.spec.scopedQualityAuthorityReference` | Explicit normalized `file:` URI for an existing canonical file under that root containing the operator's ordinary `WorkflowArtifactReference` |
| `smworkflow.spec.sbt` | Existing absolute normal SBT executable selected through `TypedSbtHostPlanFixture.normalExecutable` |

Before any execution, the parent must independently establish authentic native
local effect authorization for this retained scenario and its local Step commit
and Phase Test. A supplied reference is attribution, not authenticated authority;
the accepted output's shape and matching session do not authenticate its
provenance. Neither this specification nor compilation establishes that
authorization. The parent must obtain and retain the actual accepted Submit on
its authorized normal provider/CAM route separately. This closure consumer
never calls Submit or the scoped-quality provider and cannot retry a denied
request. Missing, malformed, negative or mismatched prerequisites fail before
configuration mutation or command execution, without skipping or substituting
success evidence.

The consumer reopens only that original source-history session at unused log
index `2000`; existing reopen callers retain default index `1000`. It preserves
the current isolated host/SBT/workspace configuration and appends only typed
`CommitMetadata` and fixed `CommitSelected` selection `goal-step-1`, with message
`P61 isolated source-history Step` and the fixture's existing local commit
identity. Ordinary `bin/launcher` / `org.goldenport.cncf.CncfMain` commands first
produce the missing-StepClosure-authority Wait. Only its actual saved output,
the retained Start participant, current configured issuer, actual UTC time and
explicit reference feed normal `prepareAuthority`; `selectPreparedImport`
selects that output for standard StartupImport on the next normal Advance.
There is no direct database/state write or installed authority port.

Assertions require the original reviewed parent/candidate tree, exactly one
closed `step-1`, unchanged semantic value 42 and generated history header,
one-source native Git diff, matching actual HEAD/parent/tree/message, clean
status, and exactly one additional actual Phase ProductCheck with a successful
typed Test receipt. The last Advance must retain the identical pending
`ReviewPhase`, without another commit or Test. Actual normal outputs and stderr
are retained with `CREATE_NEW` under `quality-closure/`, alongside bounded
read-only native Git observation logs; normal invocation logs remain under
`logs/2000.stdout.json`, `2000.stderr` and the following indices. The consumer
does not accept Phase review or advance beyond this pending request.

Parent-selected validation first uses ordinary `Test / compile` through the
registered `cncf_command_runner` and shared SBT serialization wrapper. Only
after the genuine inputs and platform authorization exist may the parent
explicitly select `testOnly` for this manual suite with the properties above;
repository-full testing remains deferred. Authoring and compilation establish
no performed closure, runtime-route proof or Phase acceptance. Real
provider/Submit execution and actual CML model-scope coverage remain open until
their separate normal evidence is obtained.
