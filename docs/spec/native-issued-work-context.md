# Native issued work context

An actual normal GoalPhase, RepositorySync or SplitPhase Request carries optional
`issuedWorkContext` in its declared AdvanceResult. This generated structured
Value describes the owning CNCF WorkOrder alongside the existing business view.
The existing semantic skill reads that issued view and context under its current
procedure. Start, Advance, Submit and their business parameters remain unchanged.

## Rules

R1: The finite thinking modes are `analysis`, `design`, `implementation` and
`review`. The assigned business meaning selects the mode. Reasoning level,
semantic reasoning class, execution placement, independence, model and profile
are separate concepts; none selects or changes the thinking mode implicitly.

R2: Project only the actual owning CNCF WorkOrder, using
`WorkflowWorkOrderJsonV1.encodeC` with its owning payload codec. CNCF owns the
generic handle, request, context, execution requirement and completion/evidence
contracts. The selected JSON objects become Records through `RecordDecoder.json`
without rewriting fields. There is no second generic protocol, whole-protocol
verification pass, private storage lookup or reconstructed issue.

R3: Export the declared input type, never `input.value` or the private
presentation. The view contains no claim token, synthetic provider, consent,
principal, grant or assurance fields. Its identities and references locate
context and supply no permission, dispatch, execution, review evidence or
Workflow acceptance.

R4: Keep the actual outer Goal task ID and the exact issued child submission
ID, result type and operation. Goal child output forwards the child's
`issuedWorkContext` verbatim. Submit acknowledges its own typed result;
only a separate explicit Advance selects subsequent work. Human, Wait and
Terminal remain distinct native outcomes and carry no issued-work view.

R5: The generated Value and all three generated AdvanceResult readers consume
the selected typed fields through ordinary Record/JSON output. Projection fails
as a Consequence for an absent interaction/mode/codec/current/request,
non-WorkOrder, incompatible input type or unconsumable canonical value.
Missing required consumed data is never substituted. Optional input/result
types, disposition and reasoning class retain their native absence.

R6: Native evidence, admission, independence, permission, CAS and progression
retain their owners. Required missing security or provider evidence remains
Gap/Unknown. Relevant unchanged prerequisite evidence may be reused only for
its checked contracts. Existing protected Step review, including the FIX16 M2
obligation, and full Phase review remain required; PHASE-6.3 owns primary
aggregate full-suite validation. This view introduces no security engine or
additional review stage.

## Declared generated shape

Each of `GoalPhaseAdvanceResult`, `RepositorySyncAdvanceResult` and
`SplitPhaseAdvanceResult` appends optional
`issuedWorkContext: WorkflowIssuedWorkContext` as its last field. Ordinary wire
readers also accept its generated `issued_work_context` name. The new Value has
exactly this field order; `1` is required and `?` is optional.

| Field | Type | Multiplicity | Canonical source |
| --- | --- | --- | --- |
| thinkingMode | string | 1 | Assigned semantic mapping below |
| schemaVersion | string | 1 | Root schemaVersion |
| handle | org.goldenport.record.Record | 1 | Root handle |
| runId | string | 1 | Request runId |
| continuationId | string | 1 | Request continuationId |
| expectedRevision | string | 1 | Request expectedRevision |
| requiredOperation | string | 1 | Request requiredOperation |
| operation | org.goldenport.record.Record | 1 | Request operation |
| inputType | string | ? | Request input.typeIdentity only |
| resultType | string | ? | Request resultType |
| context | org.goldenport.record.Record | 1 | Request context |
| completion | org.goldenport.record.Record | 1 | Request completion |
| evidence | org.goldenport.record.Record | 1 | Request evidence |
| completionOperation | org.goldenport.record.Record | 1 | Request completionOperation |
| executionRequirement | org.goldenport.record.Record | 1 | Root requirement |

`handle` preserves canonical component, workflow, workflow revision, instance
and protocol version. `context` preserves summary, required facts, references
and snapshot. `completion` retains its identity and required facts; `evidence`
retains its identity and required evidence. Both operation Records retain their
service/operation pair. `executionRequirement` preserves the native capabilities,
risk, reasoning and Boolean `reviewRequired`, plus disposition and reasoningClass
only when the canonical owner supplies them. The in-memory canonical projection
applies no defaults or field renaming. Ordinary CLI output uses the existing
CNCF recursive output naming policy, whose default is snake_case. After generated
intake, nested generic Records retain the observed wire keys.

## Assigned semantic mappings

| Public Request | Assigned business meaning | Mode | Owning input codec |
| --- | --- | --- | --- |
| Goal work | ImplementStep / RepairStep | implementation | GoalPhaseNativeWork.inputCodec |
| Goal work | ReviewStep / ReviewPhase | review | GoalPhaseNativeWork.inputCodec |
| Repository conflict | Resolve selected conflicts | implementation | RepositorySyncConflictSemantic.inputCodec |
| Repository merge review | Review selected merge | review | RepositorySyncNativeMergeReview.inputCodec |
| Split partition | Assess unresolved partition | design | SplitPhaseExplicitPartitionSemanticJson.requestCodec |
| Split handoff scope | Resolve requested scopes | design | SplitPhaseScopeSemanticJson.requestCodec |
| Split reasoning | Classify unresolved reasoning | analysis | SplitPhaseExplicitReasoningSemanticJson.requestCodec |

Only those four exact Goal kind strings are accepted. Projection consumes the
same actual interaction that supplies the existing business input. It does not
resolve a provider, read configuration, execute work or choose a new route.
Redisplaying one pending WorkOrder preserves its selected context. Distinct
actual instances, continuations, revisions and result routes retain their own
values.

## Normal command consumption and child routing

Retain the actual Start session in the exact caller-owned task file as described
by [native installation](native-client-installation.md), read it anew and call
normal command Advance. The declared Request supplies both its business work
and issued context. Use the issued submitOperation, submitSessionId and resultType
under the existing [common Operation loop](common-workflow-operation-loop.md).
The view does not alter skill definitions or invocation procedures.

For a Goal-owned Repository child, the AdvanceResult sessionId remains the outer
Goal ID. The submission ID and issued context handle/run belong to the actual
child. Preserve both identities, return the child result to its exact declared
submission operation and explicitly Advance the outer Goal afterwards. A child
Human/Wait/Terminal supplies no WorkOrder context. Closing authority context,
review host context and Human host context keep their separate contracts.

Real tasks use command. The one normal FQN client Start smoke remains light
connectivity qualification, with its existing canonical-ID retention. There is
no additional remote task matrix or raw transport procedure.

## Executable examples and limits

| Example | Rules | Source obligation |
| --- | --- | --- |
| E1 | R1,R2,R5 | Four modes and exact Goal mappings retain explicit canonical metadata |
| E2 | R1,R2,R6 | Native reasoning/class/disposition preserve meanings and absence |
| E3 | R2,R3,R5 | Private payload/presentation omitted structurally; declared types and required facts retained |
| E4 | R2,R5 | Unavailable, non-WorkOrder and incompatible consumed values fail as Consequence |
| E5 | R2,R4,R5 | Distinct routes stay distinct; redisplay and optional type absence remain native |
| B3 | R1,R2,R5 | All three compiled generated readers consume strict nested wire and reject missing required values |
| O-Goal | R1,R2,R4,R6 | Normal Goal implementation/repair/Step review/Phase review retain actual contracts and outcome distinctions |
| O-Repository | R1,R2,R4,R6 | Actual conflict/review and selected Goal child projection preserve context and child routing |
| O-Split | R1,R2,R4,R6 | Actual partition/scope/reasoning expose design/design/analysis; Human/Terminal omit |
| CLI-E1/CLI-E2 | R1,R2,R4,R5 | Independent normal command processes retain task-owned routing and stable pending context |
| CLI-E4/CLI-E5/CLI-E6 | R1,R2,R4,R5 | Existing rejection observations retain the previously issued context without advancing or retrying |

`NativeWorkflowIssuedWorkContextSpec` uses small specification-owned canonical
WorkOrders. Managed Operation specifications independently observe actual issued
metadata after Advance; their fixture actors, answers and enclosing projection
IDs are not genuine installed tasks, review or consent. The wire and CLI
specifications supply consumer evidence only for their selected contracts.

This is an issued-context prerequisite. Actual installed all-three-family
semantic command tasks, provider configuration/replacement/fallback, adopted
minimum Effective Acceptance Boundary and its required provider evidence,
genuine independently attributable bounded Goal execution, original Step
acceptance and full Phase closure remain required. Imported producer plans
remain PLANNED until actual availability is established and establish no
security PASS. No Phase completion or executed validation is claimed here.
