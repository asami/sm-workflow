# Finite public DelegateTask caller

Date: 2026-10-27
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
adopt. Publication additionally needs publishLocal, its registered target and
artifact coordinates. Literal exclusions must not contradict permitted effects.
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

`advanceDelegateTask --sessionId` returns the actual Request/Dispatch/Return/wait
or terminal view. Only a Request assigns semantic work. Preserve its full
`issuedWorkContext`, submit operation, Session and result type. The worker returns
`DelegateTaskWorkResult`: outcome, summary, findings, nonempty conditions
(`identity`, `satisfied`, `detail`), unresolvedQuestions and optional
diagnosticRepair/fixAssessment. Use normal `prepareExecution`/`recordExecution`
with genuine executor observations. `submitDelegateTaskWorkResult` takes the
original Session, result, resultReference, issuedWorkContext and actual admitted
executionEvidence. A separate Advance owns checks, acceptance and commit.

## Transport and adoption

`DelegateTaskDeliveryService.observeDelegateTaskDelivery --input <json>` takes
`sessionId`, `event`, `sourceReference`, optional `destinationSessionId`, optional
`result`, optional `artifactSelection`, and `rationale`. Closed events are
dispatch-observed, result-received, return-observed, adopt and defer. Pass the real
destination Session on transport observations. Supply the actual returned state
as result; do not synthesize a successful state from source text or acknowledgment.

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

Provide the normal Workflow instance/continuation/journal stores, the selected
input collection, independent authority/execution/review/adoption evidence
admissions, immutable read roots for input/evidence files, concrete execution
profile mapping and current origin. Profiles cover CodingProgrammingStandard or
DesignStandard for adopted implementation and ReviewStandard for possible review.
Provider identity in configuration is not a worker execution observation.

Bind the adopted repository and candidate paths to ordinary typed Git programs;
the destination commit selection is `delegate-task`. Bind its exact check names
to Compile and nonempty focused TestOnly classes (or an impact-justified FULL),
normal SBT workspace grants and finite output/time limits. Register the selected
LocalPublication target, workspace, typed PublishLocal program, normal local
artifact repositories and independent publication relevance admission if required.
Requester and destination retain distinct Sessions. Missing bindings are setup
gaps; no fallback to a generic process, sibling skill or hand-written receipt.

## Explicit installed qualification inputs

`NativePhase68DeliveryCliSpec` is selected explicitly and does not participate in
automatic suite discovery. Required normal test process environment:

| Environment | Equivalent system property | Meaning |
| --- | --- | --- |
| SM_WORKFLOW_SPEC_INSTALLED_COMMAND | smworkflow.spec.installedCommand | Absolute executable normal installed CLI |
| SM_WORKFLOW_SPEC_PHASE68_INSTALLATIONS | smworkflow.spec.phase68Installations | Absolute existing selected installation root |

System properties take precedence. Missing/invalid inputs fail; no source-classpath
fallback or publication is performed by the spec. Runtime-owned publication and
activation must precede this selected check. The suite qualifies fixture-owned
public routes and actual packaged bytes; it is not evidence of real chat delivery.
