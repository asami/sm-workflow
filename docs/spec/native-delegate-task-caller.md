# Finite public DelegateTask caller

Date: 2026-11-05
Owner: Phase 6.8 C5/D4. This contract describes the existing public operations;
it adds no scheduler, command pipeline or caller-owned Workflow.

## Start and issued work

Write the adopted `DelegateTaskInput` to an absolute local `file:` URI. Its fields
are `repositoryIdentity`, `requestIdentity`, `objective`, nonempty `ownedPaths`,
`checks`, `conditions`, `sourceReferences`, `reviewRequired`, `kind` (code or
documentation), `scalaLint`, `repairLimit`, `sourceKind`, `dependencies`, and
`delegation`. Each artifact reference is `{identity, revision}`.

The required delegation fields are `requesterIdentity`, `destinationIdentity`,
`returnDestination`, `componentIdentity`, `role` (requester or destination),
`validationScope` (localized, broad or documentation), nonempty
`permittedOperations`, `exclusions`, nonempty `authorizationReferences`, optional
`publicationTarget`, `artifactCoordinates`, and `consumerInstructions`.
Required operations are dispatch, implement, validate, commit, review, return and
adopt. Publication additionally needs publishLocal, the destination's registered
target and artifact coordinates. The requester retains that target as a delivery
requirement; it does not register or execute it locally. Literal exclusions must
not contradict permitted effects.
Requester and destination are different owners. The two roles use otherwise
matching adopted requests, including request identity and authorization sources.

Call `NativeWorkflowPreparationService.prepareDelegateTaskStart --input <json>`
with `WorkflowDelegateTaskStartPreparationInput` fields `input` (the file URI),
`selectionId`, `participant`, `origin: ExplicitHuman`, `idempotencyKey`,
`selectionCollection: entity:smworkflow.cli.goal_selection`. Select the returned
normal import through the existing host. Independently admitted selection/authority
is required; the import is not proof of consent.

Call `DelegateTaskService.startDelegateTask` with `--input <DelegateTaskInput>`,
`--selectionId`, `--participant`, `--idempotencyKey`; optional `--sessionId` is only
a checked proposal. Retain the returned canonical Session. Exact redelivery uses
that same role's selection and returns its retained Session. Changed owners or
input do not create permission to replace an existing request.

The requester retains the full destination repository, owned paths, required
check names, conditions, authorization and publication target as request data.
Start enters `DispatchReady` with no resolved checks, candidate, check receipts,
semantic or review reference, commit or publication result. Advance returns
`Dispatch` for that same Session; it does not issue a semantic WorkOrder or
authorize a destination effect. After an attributable `dispatch-observed`
observation, Advance returns `Wait` at `AwaitResult`. Exact Start redelivery
collects the retained frontier, including an existing configured requester
record, instead of recreating the request or resolving destination execution.

`advanceDelegateTask --sessionId` returns the actual Request/Dispatch/Return/wait
or terminal view. Only a Request assigns semantic work. Preserve its full
`issuedWorkContext`, submit operation, Session and result type. The worker returns
`DelegateTaskWorkResult`: outcome, summary, findings, nonempty conditions
(`identity`, `satisfied`, `detail`), unresolvedQuestions and optional
diagnosticRepair/fixAssessment. Use normal `prepareExecution`/`recordExecution`
with genuine executor observations. `submitDelegateTaskWorkResult` takes the
original Session, result, resultReference, issuedWorkContext and actual admitted
executionEvidence. A separate Advance owns checks, acceptance and commit.
Those semantic work, validation and commit operations belong to the destination
role. A requester never completes them on the destination's behalf.

## Correct an existing unsubmitted implementation or repair inventory

Use the current destination Session and the actual pending Implement or Repair
`issuedWorkContext.continuationId`; do not Start again or create a repair Session.
An initial Implement or current Repair WorkOrder must still be unclaimed and
unsubmitted. Repair accepts new same-purpose literal `additionalOwnedPaths` with
an empty `submissionConditions` list, retaining the existing timing plan. Only
initial Implement may select condition timing. Repair correction preserves the
failed checks, candidate, prior results, generation, repair counts and limit;
ordinary Advance issues another Repair on the same Session. The description
operation takes a structured input because the Session, old continuation,
additional paths and submission IDs belong to one exact target:

```text
org.simplemodeling.textus.SmWorkflow.DelegateTaskService.describeDelegateTaskScopeCorrection --input <description-json>
```

The `DelegateTaskScopeCorrectionDescriptionInput` is:

```json
{
  "sessionId": "<actual-destination-session>",
  "oldContinuationId": "<actual-pending-continuation>",
  "additionalOwnedPaths": [],
  "submissionConditions": ["<Human-selected-original-condition-ID>"]
}
```

The selected submission IDs must be a nonempty, unique, order-preserving proper
subset of the original conditions. The actual responsible Human must choose
those exact IDs. Do not infer them from lexical condition text or from permission
to repair the runtime. With no timing change, an empty submission list instead
retains the existing plan, and new same-purpose literal additional owned paths
must be supplied. Added paths retain the configured host and exclusion checks.

Retain the returned `target` string unchanged. Write the complete
`DelegateTaskScopeCorrectionInput` to an absolute local file URI:

```json
{
  "sessionId": "<actual-destination-session>",
  "oldContinuationId": "<actual-pending-continuation>",
  "target": "<exact-description-target-string>",
  "selectionId": "<independently-selected-correction>",
  "participant": "<actual-participant>",
  "idempotencyKey": "<correction-key>",
  "reference": {"identity": "<actual-Human-selection-reference>", "revision": "<actual-revision>"}
}
```

Call `NativeWorkflowPreparationService.prepareDelegateTaskScopeCorrection
--input <preparation-json>`, with `input` equal to that file URI and
`selectionId`, `participant`, `origin: ExplicitHuman`, `idempotencyKey` and
`selectionCollection: entity:smworkflow.cli.goal_selection`. The selection
metadata must match the complete correction input. This only prepares a passive
import; the existing host must independently admit the exact Human selection.
Then call `DelegateTaskService.correctDelegateTaskScope --input
<complete-correction-json>`, or use `--inputResource <absolute-local-file-URI>`
for the complete correction file when retained check output exceeds CLI limits.
Select exactly one form; the existing Resource authorization and configured
read limits apply. It returns `corrected-awaiting-advance`; repeated
exact input returns `already-corrected`. A changed target, subset, participant,
Session or issue is refused. A previously adopted timing plan cannot be replaced.

A separate `advanceDelegateTask --sessionId <same-session>` issues the successor
WorkOrder. Keep its actual current `issuedWorkContext`; the retired issue remains
historical and cannot be submitted as that successor. Neither correction nor
description changes files, checks, counters, source base, prior results or
unknown external effects. Force-closed work cannot be reopened this way.

Every Implement, Repair and Review result must retain all original condition IDs
in order. With the selected timing plan, report submission IDs true and later
IDs false; no premature all-true completion is admitted. Once normal validation,
required independent review, local commit and required publication finish,
ordinary Advance issues read-only `DeliveryAcceptance` through the same
implementation capability. Return accept with all original conditions true only
after evaluating their actual evidence, or decision-needed for unresolved
meaning. This work cannot launch checks, change source, commit, publish or ask
for a repair. Final ReturnReady and requester receipt still require every
original condition, actual qualification and retained source/publication.

`DelegateTaskScopeCorrectionSpec` covers native managed supersession, condition
timing, legacy wire omission and final gates. The explicitly selected
`NativeDelegateTaskScopeCorrectionCliSpec` uses the normal installed executable
and optional Component archive inputs described below. Its tiny isolated
destination uses an existing concrete fixture profile and genuine public
prepare, Start, description, independently fixture-selected correction and
Advance. It checks same-Session successor binding, immutable condition IDs,
idempotence, retired-result refusal and absence of file, SBT or publication
effects. All its Human and worker facts are SPECIFICATION-OWNED; it establishes
no actual consumer choice, full delivery or application result.

## Transport and adoption

`DelegateTaskDeliveryService.observeDelegateTaskDelivery --input <json>` takes
`sessionId`, `event`, `sourceReference`, optional `destinationSessionId`, optional
`result`, optional `artifactSelection`, and `rationale`. Closed events are
dispatch-observed, result-received, return-observed, adopt and defer. Pass the real
destination Session on transport observations. Supply the actual returned state
as result; do not synthesize a successful state from source text or acknowledgment.

The normal requester sequence is:

| Public operation | Retained outcome |
| --- | --- |
| `NativeWorkflowPreparationService.prepareDelegateTaskStart` | Passive complete-input selection import; independently provision it through the existing host |
| `DelegateTaskService.startDelegateTask` | Canonical requester Session at `DispatchReady` |
| `DelegateTaskService.advanceDelegateTask` | `Dispatch`, or unchanged `Wait` after observed dispatch |
| `DelegateTaskDeliveryService.observeDelegateTaskDelivery`, event `dispatch-observed` | `AwaitResult`, with the actual destination Session and source reference |
| Same observation operation, event `result-received` | `ResultReceived` only after checking the genuine returned destination state |
| `DelegateTaskDeliveryService.describeDelegateTaskAdoption` | Exact current requester adoption target after receipt |
| `NativeWorkflowPreparationService.prepareEvidence`, family `DelegateTask`, kind `adoption` | Passive evidence projection for independent host admission |
| `DelegateTaskDeliveryService.observeDelegateTaskDelivery`, event `adopt` | `Adopted` for the independently admitted unchanged target |

Use the normal installed Operation selector
`org.simplemodeling.textus.SmWorkflow.<Service>.<operation>` directly, with its
ordinary named arguments. Dispatch acknowledgement alone cannot pass
`result-received`, describe adoption or adopt. Returned destination checks,
accepted conditions, required review, local commit and required publication
remain mandatory even when the requester has none of those execution bindings.

For adoption first call `describeDelegateTaskAdoption --sessionId` on the requester.
It returns componentInstance, sessionId, sourceRevision and exact target.
`NativeWorkflowPreparationService.prepareEvidence`, family DelegateTask and kind
adoption, consumes absolute file URIs for the actual description, observation
result, result reference and execution evidence plus the actual issuer/time and
host evidence collection. Independently provision the resulting evidence through
the host's `delegate-task-requester-artifact-adoption` admission. Passive output
does not authenticate itself. Then observe adopt against that unchanged target.

Artifact selection fields are sourceCommit, coordinates, selectionReference,
optional activationReference and consumerCheckReferences. Adoption is explicitly
result-and-selection attachment only. Execute/observe actual activation and
consumer checks through their normal authorized owners; do not infer them from
an adoption label. Re-entry collects recorded publication/return/adoption, never
replays an uncertain effect. A stale target is re-described before any new decision.

## Required ordinary host capabilities

Both roles need their normal Workflow instance/continuation stores, selected
input collection, actual participant, complete-input human selection and
idempotency admission, configured local evidence/journal stores where selected,
and immutable read roots for input/evidence files. Independent adoption evidence
is required when the requester adopts. Canonical Session, definition, storage,
closure and cross-owner redelivery checks apply to both roles.

The requester configures its **own caller repository**, root and branch with
ordinary typed read-only Git observation programs. Start observes that actual
root, branch and head and checks the configured root/branch. Retained payload
Git facts and dirty paths describe the caller only; they are not a producer
base or producer observation. Caller dirty and staged files are preserved
without comparing them to destination paths, staging, normalization or commit.
No producer checkout is required. The request's repository/paths may therefore
name a different repository from the caller's configured host.

Requester metadata admission requires no concrete execution profile/origin,
installed destination check bindings, destination publication target or SBT
program. If profiles/origin are supplied, they must still form a valid pair;
partial or malformed configuration is refused. Do not invent a parent model or
executor descriptor to dispatch a request. This exemption applies only to an
exact valid `requester` contract in DelegateTask; it changes no destination,
GoalTask or StandaloneFix execution requirement.

The destination needs a concrete execution profile mapping and current origin,
covering CodingProgrammingStandard or DesignStandard for implementation and
ReviewStandard for possible review. Provider configuration is not a worker
execution observation. Bind its adopted repository and candidate paths to
ordinary typed Git programs, retaining its strict root/branch and dirty-scope
admission; the destination commit selection is `delegate-task`. Bind its exact
check names to Compile and nonempty focused TestOnly classes (or an
impact-justified FULL), normal SBT workspace grants and finite output/time
limits. Register its selected LocalPublication target, workspace, typed
PublishLocal program, normal local artifact repositories and independent
publication relevance admission if required. Genuine executor, review and
publication evidence stays with the executing destination. Missing required
bindings are setup gaps; no fallback to a generic process, sibling skill or
hand-written receipt is authorized. No new properties or permissions are added.

## Explicit installed qualification inputs

`NativeDelegateTaskRequesterCliSpec` is one explicitly selected installed case
and does not participate in automatic suite discovery. Required normal test
process environment:

| Environment | Equivalent system property | Meaning |
| --- | --- | --- |
| SM_WORKFLOW_SPEC_INSTALLED_COMMAND | smworkflow.spec.installedCommand | Absolute executable normal installed CLI |

The system property takes precedence. Optional `smworkflow.spec.componentFile`
selects an actual published Component archive through the existing host
configuration. No installations-root input is required. Missing/invalid required
inputs fail; no source launcher/classpath fallback or publication is performed
by the spec. The parent supplies the actual installed artifact before this
check. The case uses a fresh isolated caller with no mapping, a foreign code
request with destination-only checks/publication, ordinary passive preparation,
Start, Advance, redelivery and observation to `AwaitResult`, with premature
adoption refused and no execution or caller Git effects. It retains invocation
stdout/stderr paths in its `requester-result-paths.json`. These observations are
SPECIFICATION-OWNED; they prove no real chat delivery or destination execution.
