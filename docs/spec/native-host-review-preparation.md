# Native host review preparation

The normal Goal and Repository Request outputs carry selected review context
from their owning issued WorkOrder. The public
`org.simplemodeling.textus.smworkflow.host.NativeHostReviewPreparation` consumes
that context and an independently supplied review response to prepare a managed
record input using the existing `sm-workflow.native-host-evidence.v7` codec.
The independent host owns the review and provisioning of that record. Preparation
does not attest that a review happened or admit its result.

This is the S6-01D2A prerequisite boundary. Specification-issued WorkOrders and
managed rows exercise its behavior; they do not establish genuine independent
review, Human consent, effects or installed task completion. The connected
independent host/writer and all three genuine installed command task loops remain
later D2 obligations. See [normal host ownership](normal-three-workflow-host.md)
and [native installation](native-client-installation.md).

## Issued context and passive API

Goal `ReviewStep` and `ReviewPhase` return
`response.work.review.hostContext`. Repository merge review returns
`response.review.hostContext`; a Goal repository child forwards that child's
review context through the existing `repositoryReview` projection. Implementation,
repair and Human outputs supply no review host context. Pending review display
uses the retained issued WorkOrder and the actual current owning component.

`WorkflowHostReviewContext` contains `purpose`, `componentInstance`, a structured
Record `target` and `WorkflowHostReviewCompletion`. Completion contains the actual
structured Record `handle`, `runId`, `continuationId` and declared `typeIdentity`.
These are issued context for passive preparation; they add no Submit parameter
and do not carry expected revisions, model snapshots or effect grants.

`goalC(context, result, resultReference, executionEvidence, issuer, occurredAt)`
consumes `GoalPhaseWorkResult`. The corresponding `repositoryC` consumes
`RepositorySyncNativeMergeReviewResult`. Both return
`Consequence[Prepared]`, where `Prepared(recordIdentity: String, record: Record)`
is the existing purpose-specific managed record identity and its
`nativeHostEvidencePayload`. The caller supplies the result, actual result
reference, actual reviewer worker identity, explicit issuer and `java.time.Instant`.
There is no default worker, issuer or occurrence time.

The host provisions the returned row through its existing managed input/bootstrap
mechanism after genuine review. In executable specifications only, existing
`call.createC("host_evidence", prepared.recordIdentity, prepared.record)`
provisions explicit specification input. No production writer, bootstrap
orchestration, Operation, CLI control route or trust registration is introduced.

## Rules

R1: Context comes only from the actual owning issued review, its selected input,
declared result type and actual completion. Wrong or missing consumed purpose,
type, owner or completion fails. Nonreview work has no review host context.

R2: Preparation projects selected domain target and verdict into the existing
purpose-specific binding and v7 codec. Normal Record/JSON conversion and generated
readers own the wire. At passive review intake only, the opaque handle and selected
target accept their declaration names and the normal command formatter's default
snake names. The handle pairs are `componentIdentity/component_identity`,
`workflowIdentity/workflow_identity`, `workflowRevision/workflow_revision`,
`instanceIdentity/instance_identity` and `protocolVersion/protocol_version`.
Goal target pairs are `sourceRevision/source_revision`, `workKind/work_kind`,
`checksReceipt/checks_receipt`, `changedPaths/changed_paths` and the optional
`testChecks/test_checks`. Repository target
pairs are `validatedRevision/validated_revision`, `localTip/local_tip`,
`remoteTip/remote_tip`, `indexTree/index_tree`, `validationMode/validation_mode`,
`changedPaths/changed_paths` and `selectedChecks/selected_checks`; each structured
Goal or Repository check accepts `exitCode/exit_code`. A selected check retains
`identity`, `termination`, integer `exitCode` and, only when supplied, typed `sbt`.
The existing `WorkflowSbtValidation.Facts` decoder validates `kind`, `outcome`,
optional `selection` and optional `reason`; its encoder supplies canonical facts.
Only within `sbt.selection`, `testClasses/test_classes`,
`completedSuites/completed_suites` and `testsRun/tests_run` are fixed aliases.
Only these pairs project into canonical names
before the existing v7 codec and native target validators. Nested plan, base and
candidate fields retain their supplied identity, head and tree names. There is no
native envelope, new serializer, complete-copy verifier, revision seal or source
custody proof.

R3: The caller supplies the actual independent reviewer result, result reference,
worker, explicit issuer and occurrence time. Preparation chooses none of these,
performs no review and manufactures no success or independent attestation.

R4: Preparation returns only a managed record input. Existing independently
configured Reader/admission, current issuer and managed read permissions control
its use. Preparation performs no storage write, configuration/clock lookup,
Workflow call, permission grant or progression.

R5: Prepared evidence applies only to the selected actual native owner, target,
result reference, worker, verdict and continuation. Review evidence cannot
substitute Human or Step/Phase/repository effect authority. Submit acceptance
still requires independent admission and native completion; next work still
requires explicit Advance.
Typed SBT kind, outcome and any selected classes, completed suite count and test
count participate in target equality through preparation, v7 storage and actual
managed observation. A valid different selection, count, kind or outcome, or
stripped typed facts, cannot reuse the original review evidence. This binding
adds no independent reviewer proof or execution grant.

R6: Only consumed fields affect meaning. Unused metadata and grant annotations
are inert. Declaration-only, snake-only and semantically matching double
spellings retain the same selected meaning. Contradictory double spellings fail;
neither spelling takes precedence. Missing, null or invalid consumed fields still
fail without defaults, including object/check-array structure and the existing
integer generation and exit requirements. Both spellings of a structured Goal
check array are separately projected before agreement is checked, including
nested selection aliases; inert annotations do not create contradictions.
Present null, nonobject, unknown or semantically invalid `sbt` fails. Missing or
malformed required nested selection fields, fractional or out-of-range integer
counts, invalid or duplicate classes, inconsistent suite counts and zero tests
fail through the existing Facts rules. No kind, success, selection or count is
inferred. Absent `sbt` retains the exact historical three-field check shape;
absent Goal `testChecks` remains omitted, while an explicitly supplied empty
array remains explicit. Present malformed arrays are never treated as absence.
This projection does not rename
arbitrary or recursively nested annotation fields. Goal repair summary/findings and Repository repair findings, affected
paths and rationale retain their exact selected meaning; unused nonrepair
diagnostics retain the existing `Json.Null` behavior. Actual task completion
remains separate from specification inputs.

R7: Ordinary Goal `ReviewStep` and `ReviewPhase` host admission consumes the actual
independently supplied reviewer result through the configured managed Reader,
without a CBD call or another AI review. The actual owner, target, result
reference, worker, verdict and continuation remain bound to independently
provisioned host evidence. Existing semantic acceptance gates, separate effect
authority and explicit Advance remain required. CBD-scoped review integration
belongs to Phase 10; ordinary admission does not fabricate CBD review coverage.

R8: An actual accepted ordinary review and its saved normal StepClosure Wait
retain the successful validation of the same reviewed candidate and selected
checks. The Wait is an input to the existing public `prepareAuthority` route,
not permission to commit. A separately authorized host reference, retained Start
participant and current issuer prepare the ordinary StartupImport input; only
explicit Advance consumes it for typed Git/UoW Step closure and the Phase
ProductCheck. Continuation consumes the actual saved Wait without repeating its
earlier Advance, appending grants or clearing previous configuration, logs or
execution facts. It must observe the real local commit and fresh ProductCheck,
then stop at pending independent ReviewPhase. This bounded isolated fixture
establishes neither CBD coverage nor installed-skill or Phase acceptance.

## Executable examples

`NativeHostReviewPreparationSpec` uses visible example sentences, afterWord
spec/example/rule metadata and adjacent Given/When/Then. Its WorkOrders and rows
are explicit specification inputs. The Goal and Repository Operation specs also
retain actual public review context, reject missing records without revision
change, provision public-prepared specification rows, Submit compatible named
results and select subsequent work with explicit Advance.

| Example | Rules | Observable contract |
| --- | --- | --- |
| E1 | R1,R2,R3 | Selected issued Goal review crosses strict generated normal Record intake, prepares a v7 row and applies relative to its actual native owner/target; implementation and repair have no review host context |
| E2 | R1,R2,R3 | Selected issued Repository merge review with check exit zero crosses structured nested Record intake and prepares applicable v7 evidence |
| E3 | R2,R3 | Repair findings/directives survive exactly; unused nonrepair diagnostic changes leave selected bindings unchanged |
| E4 | R1,R3,R4 | Missing consumed context/reference/worker/issuer/time and crossed purpose/type fail without defaults |
| E5 | R3,R4,R5 | Different actual owner/target/reference/worker/verdict/continuation refuses admission; physical fixture provisioning still requires a live managed read call and supplies no Human/effect authority |
| E6 | R2,R4,R6 | Unused metadata/grant annotations are discarded; wrong consumed types fail without TTL, complete-copy equality or new trust grants |
| E7 | R1,R2,R3,R5,R6 | Fixture-issued Goal ReviewStep/ReviewPhase and Repository review cross the real normal command formatter and strict generated reader, retaining the same canonical v7 payload, actual native owner/target/completion and successful check exit |
| E8 | R1,R2,R4,R6 | Every fixed consumed alias pair accepts agreement and rejects contradiction, absence, null and wrong types; malformed check structure and fractional generation/exit fail under existing native validators |
| E9 | R1,R2,R4,R5,R6 | Fixture-issued typed Goal ReviewStep/ReviewPhase and direct/forwarded Repository reviews cross normal command formatting, generated readers and public prepareEvidence; raw v7 storage retains explicit TestOnly kind, succeeded outcome, two classes, two suites and 25 tests; managed observation rejects crossed selected facts, fixed aliases require semantic agreement, historical absence stays absent and malformed presence fails |
| E10 | R3,R4,R5,R7 | Fixture-issued ReviewStep and ReviewPhase use the actual ordinary Goal review port and configured managed Reader without CBD or scoped-quality metadata: missing host evidence refuses admission, matching explicitly provisioned evidence admits, and altered candidate tree, result reference, worker or verdict refuses reuse. These fixture rows establish neither genuine independent connected review nor completed task proof |
| E11 | R3,R4,R5,R7,R8 | Explicit `NativeGoalScopedQualityClosureCliSpec` consumes an actual accepted independent review and saved normal StepClosure Wait, requires the retained matching passed candidate validation and separately authorized host reference, then uses normal prepareAuthority/StartupImport/Advance to observe exactly one isolated typed local Step commit, a fresh ProductCheck and pending ReviewPhase. Repeated Advance adds no check or commit; no Submit, CBD call or AI review is repeated. Parent-authorized real inputs are required, and this does not establish installed-skill or Phase acceptance |

`NativeTestOnlyValidationReceiptSpec` exercises the real v7 encoder, decoder and
observation boundary for Goal and Repository selected facts, historical shapes,
crossed valid facts and inert annotations. `NativeHostReviewPreparationSpec`
exercises normal formatter/readback, fixed typed aliases and explicit
specification provisioning followed by managed reading.
`NativeHostPreparationOperationSpec` invokes the real public `prepareEvidence`
Operation against selected normal output files for both Goal reviews, direct
Repository review and a forwarded Goal child. It checks explicit raw stored
facts and owning child completion, and separately observes that passive
preparation provisioned no row. Preparation returns an importer input; storage
provisioning and independent admission remain separate actions. These fixtures
establish no genuine review, Human consent or effects.

These clauses state obligations and specification behavior, not executed
validation results or Step/Phase acceptance.
