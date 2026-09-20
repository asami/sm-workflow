# sm-workflow Skill Adapter Design

- Status: Draft / normative design for sm-* skill generation
- Date: 2026-09-21
- Audience: authors/generators of sm-* skills
- Runtime counterpart: [sm-workflow design](sm-workflow-design.md)

## 1. Purpose

This is the primary design document to read when generating or implementing an sm-workflow-facing Skill. It is intentionally written from the Skill side.

A Skill owns planning semantics and project context. It reads and updates Phase / Checklist material, interprets closure and next-work meaning, maps that planning model to an executable sm-workflow/CNCF Workflow, projects only the bounded execution context required by the Workflow, and reconciles returned Result/Evidence back into the planning model.

The Skill is not a Workflow engine. It must not duplicate CNCF Workflow progression, continuation, revision, idempotency, evidence-envelope, retry, lease, or durable execution state.

Core rule:

> Skill owns planning semantics and context. sm-workflow owns software-development execution semantics. CNCF Workflow owns generic execution protocol/runtime.

## 2. Sources of truth

| Domain | Source of truth | Skill responsibility |
| --- | --- | --- |
| Planning | Phase / Checklist / Closure Criteria and project documents | read, interpret, update |
| Execution | CNCF Workflow + sm-workflow Result/Evidence/History | invoke, observe, reconcile |

WorkflowMapping is a Skill-owned projection connecting these domains. It is not a third authoritative state machine and should be reconstructable where practical. Conversation history is never a source of truth.

## 3. Skill-owned model

The Skill owns Phase, Checklist, ChecklistItem, ClosureCriterion, planning status/revision, project conventions/rationale, WorkflowMapping, planning-side reconciliation rules, and references to relevant notes/journals/specifications. These concepts must not be pushed into generic CNCF Workflow merely to simplify Skill implementation.

Conceptual WorkflowMapping:

~~~text
WorkflowMapping
  mappingId
  sourceRevision
  planningRefs
    PhaseRef
    ChecklistItemRef*
    ClosureCriterionRef*
  executionRefs
    WorkflowHandle?
    WorkflowRef
    ActionRef*
    ResultRef*
    EvidenceRef*
  reconciliationRules
  createdFrom
~~~

Mapping cardinality is not 1:1. One ChecklistItem may require multiple Actions/Evidence items; one execution result may affect multiple planning items.

## 4. Skill lifecycle

### Read / Interpret

Read the current Phase, Checklist, closure rules, and only the project context needed to interpret them. Resolve current planning revision. Determine the required outcome, relevant closure conditions, whether existing evidence already satisfies work, and whether a Workflow must be started, observed, or resumed.

This interpretation may conclude that a Workflow would be useful, but it does
not grant invocation authority. A Skill must not turn a recommendation,
terminal result, planning inference, or existing mapping into authority to
start another Workflow.

### Select / authorize start

Starting a Workflow requires an exact human-selected profile entry point. A
direct selection of `sm-goal-phase`, `sm-split-phase`, or
`sm-repository-sync`, or an explicit host/client selection of that entry
point, supplies invocation authority for the corresponding profile-specific
start Operation. The host/client records that selection as
`WorkflowInvocationSelection`; the Skill carries it but must not manufacture
or rewrite it.

The Skill resolves the selected entry point through the versioned
`SkillBundleManifest` binding. It does not derive a Workflow definition or
Operation identifier by string conversion. `SPLIT_REQUIRED`, child-goal
recommendations, and other cross-Workflow recommendations are advisory only;
they may provide a typed source reference but cannot create the selection
record or start a new WorkflowInstance.

### Map / Project

Create or refresh WorkflowMapping. Do not assume ChecklistItem = Action.

Produce the application-owned bounded execution payload, principally SmExecutionContext and relevant start/work payload. Projection is intentionally lossy: keep planning history, unrelated rationale, and project-only context on the Skill side. Optional source correlation is for traceability and remains opaque to CNCF progression.

### Invoke

Invoke the selected registered sm-workflow application Operation through a
transport-neutral typed operation client. MCP and the CNCF launcher/CLI are
adapters for the same registered Operations; a generated Skill must not embed
either transport's command grammar as Workflow semantics.

Start Operations are profile-specific and carry CNCF Phase 77 generic typed
Workflow DTOs with sm-workflow application payloads:

~~~text
StartGoalPhase(GoalPhaseStartInput, WorkflowInvocationSelection)
StartSplitPhase(SplitPhaseStartInput, WorkflowInvocationSelection)
StartRepositorySync(RepositorySyncStartInput, WorkflowInvocationSelection)

profile Start Operation validates WorkflowInvocationSelection
  -> WorkflowStartRequest[profile start input]
  -> WorkflowStartResult[WorkflowHandle, Continuation]

WORK_ORDER -> WorkOrder[SmWorkRequest]
WorkResult[SmWorkResult]
TERMINAL[SmWorkflowOutcome]
~~~

`WorkflowStartResult` returns the framework `WorkflowHandle` and first
`Continuation` (or terminal result). A non-terminal Continuation identifies
the exact registered completion Operation and typed response contract selected
by the Workflow. The Skill invokes that Operation, for example a profile work
result submission or application decision resolution, with the current handle,
Continuation identity/revision/ContextSnapshot, and typed result. It does not
select a completion Operation from result content.

The CNCF runtime admits the completion and internally drains automatic
progression to the next Continuation. There is no second Skill-facing generic
`advanceWorkflow` protocol alongside the registered application Operations.

Do not invent a second WorkflowHandle, Continuation, revision protocol, ContextSnapshot, generic Evidence envelope, retry, lease, or idempotency mechanism.

### Execute semantic work

When a semantic WORK_ORDER/Continuation is exposed, execute exactly the materialized request within scope and return the typed result/evidence through the completion Operation named by that Continuation. The Skill does not choose the next Workflow Action or completion Operation.

For JudgmentAction, return only the admitted typed judgment result (decision/rationale/evidence). Do not encode the next transition.

### Observe / Reconcile / Update

Observe typed Result, Evidence, terminal outcome, identity/revision and relevant history references. Never parse Presentation text to control execution.

Interpret execution facts in planning semantics using WorkflowMapping. A reconciliation may mark a ChecklistItem satisfied, leave it open, split/add planning work, update Closure assessment, recognize Phase closure, or supersede an old mapping.

Update Phase / Checklist only after reconciliation. Never implement unconditional field synchronization such as workflow.completed => checklist.checked.

### Re-evaluate

After updating planning material, re-read/re-evaluate the current planning revision. A terminal Workflow result does not automatically start another Workflow.

## 5. Context discipline

~~~text
Skill Context
  planning history
  rationale
  project conventions
  related documents
  Phase/Checklist semantics
  prior decisions
       |
       | bounded projection
       v
SmExecutionContext
       |
       | carried/snapshotted by
       v
CNCF ContextSnapshot
~~~

SmExecutionContext is application semantics. ContextSnapshot is CNCF's generic execution snapshot for identity/freshness/resume safety.

Before adding context to an execution payload ask: Does this information materially affect correct execution of this Workflow? If not, keep it Skill-side.

## 6. Workflow Step Closure and Checklist Closure are different

Workflow Step closure and planning Checklist closure are deliberately separate decisions.

sm-workflow owns execution-side Step closure. For GoalPhaseWorkflow this includes review admission, determination of whether re-review is required, validation/evidence checks, Step closure readiness, deterministic staging/commit, commit SHA/receipt recording, and transition to the next Step or Phase review.

The Skill does not decide whether sm-workflow should enter STEP_REREVIEWING or STEP_COMMITTING, and it must not run the commit sequence as semantic Skill work.

A successful Workflow Step commit is execution evidence. It does **not** directly mutate or imply the planning-side Checklist state.

~~~text
sm-workflow
  review / repair / re-review
       |
       v
  Step closure-ready
       |
       v
  deterministic commit
       |
       | Result / Evidence / commit receipt
       v
Skill WorkflowMapping
       |
       | semantic reconciliation
       v
Phase / Checklist update
~~~

The Skill decides what that execution evidence means for the Phase/Checklist by applying WorkflowMapping and current planning context. A ChecklistItem may remain open after a Step commit, multiple ChecklistItems may be satisfied by one committed Step, or additional planning work may be created.

Therefore the following implication is forbidden:

~~~text
Workflow Step committed => ChecklistItem checked
~~~

The only valid path is:

~~~text
Workflow Step committed
  -> typed Result/Evidence
  -> Skill reconciliation
  -> planning-side update if justified
~~~

Likewise, a ChecklistItem already marked complete does not authorize sm-workflow to skip its own review, validation, evidence, or commit guards unless the Workflow itself admits existing evidence through its defined states.

## 7. Step Close Request and proactive review evidence

The Skill is responsible for bringing program artifacts and planning/management files to the latest state it believes satisfies the Step. It may perform semantic review proactively for its own reasons before requesting closure.

The Skill then requests Step close. The close request is a closure intent plus evidence submission, not an assertion that closure conditions are satisfied.

Conceptually:

~~~text
RequestStepClose
  stepIdentity
  expectedRevision
  artifactSnapshotReference
  reviewEvidence[]
~~~

Review evidence must be typed and scoped rather than a boolean reviewed flag.

~~~text
ReviewEvidence
  reviewIdentity
  scope
  reviewedArtifactRevision
  disposition
  findingReferences
  evidenceReferences
  executionEvidence?
~~~

Initial scope vocabulary should distinguish at least scoped/focused review from full Step review; Phase-wide review remains a separate closure scope. Exact schema names may be refined by the profile contract.

sm-workflow owns closure policy. It evaluates submitted evidence for required scope, artifact/revision coverage, freshness, disposition, unresolved findings, and other closure prerequisites.

Therefore a Skill may submit a successful scoped review while the Workflow responds that a full Step review is still required:

~~~text
Skill
  proactive SCOPED review
  -> RequestStepClose(reviewEvidence = SCOPED ACCEPT)
sm-workflow
  -> closure evaluation
  -> Continuation: ReviewStep(STEP_FULL)
Skill
  -> full review result
sm-workflow
  -> re-evaluate closure
  -> deterministic commit
  -> Step Closed
~~~

If adequate full-review evidence is already supplied and remains fresh for the current artifact snapshot, sm-workflow must reuse it rather than request an unnecessary duplicate semantic review.

Review evidence freshness is revision-sensitive. A repair after a full review may invalidate that evidence. Closure policy may require only a focused re-review when the repair frontier is bounded, or a full review when the current tree is not sufficiently covered.

Once RequestStepClose establishes closure intent, the Skill does not repeatedly request close after every requested review/repair. Submission of each Continuation result resumes closure evaluation. When all requirements are satisfied, sm-workflow proceeds through deterministic validation/commit and closes the Step.

The separation is:

> Skill may review proactively. Workflow decides whether available review evidence satisfies closure requirements.

The Skill must keep planning/management files synchronized with semantic repairs before returning the relevant result. The final Step commit is Workflow-owned and should close the admitted program plus management-file state together.

## 8. Revision and reconciliation

The Skill must assume Phase/Checklist can change while a Workflow is running. Record the planning/source revision used to create a mapping.

Before applying execution results: read current planning revision; compare with mapping source revision; if unchanged reconcile normally; if changed re-interpret affected items, retain still-valid evidence, rebuild/supersede mapping as needed, and never ask sm-workflow to interpret the Phase/Checklist diff.

Git commit SHA may be used as source revision when appropriate, but the Skill contract does not require Git when another stable revision identity exists.

## 9. Recovery

A new Skill turn/session must recover without conversation history using current Phase/Checklist, required project context, stored/reconstructable WorkflowMapping/correlation, and CNCF Workflow handle/status/history/result/evidence.

If mapping metadata is missing, reconstruct conservatively from stable planning references and execution identity/history. Do not manufacture completion.

## 10. Failure boundaries

- planning ambiguity: Skill-side interpretation/input issue
- missing or mismatched human invocation authority: Skill/Host admission issue
- stale planning mapping: Skill-side reconciliation issue
- stale/invalid Workflow revision or Continuation: CNCF typed protocol/runtime issue
- domain execution failure: sm-workflow payload/result semantics
- missing evidence: planning remains incomplete unless Workflow contract rejects it
- infrastructure/provider failure: do not convert directly into Checklist semantics

## 11. Prohibited responsibilities

An sm-* Skill must not own durable Workflow progression; choose the next StateMachine Action; reproduce retry/lease/idempotency; define a generic Continuation/WorkflowHandle; treat conversation history as durable state; copy all project context into SmExecutionContext; make Phase/Checklist generic CNCF fields; assume 1:1 ChecklistItem/Action mapping; check items solely because a Workflow terminated; auto-chain another Workflow; make concrete AI model/provider transition semantics; or expose internal deterministic Actions as semantic Skill work.

## 12. Skill generation checklist

A generated Skill is acceptable only when it:

- identifies planning inputs and source of truth;
- requires an exact human-selected profile entry point and carries the host/client-recorded `WorkflowInvocationSelection`;
- treats recommendations as advisory and never converts them into start authority;
- resolves the profile-specific start Operation through the manifest binding;
- defines/reuses a WorkflowMapping strategy;
- records source revision;
- projects bounded SmExecutionContext;
- uses CNCF generic Workflow DTOs through registered sm-workflow application Operations;
- uses a transport-neutral typed operation client and keeps MCP/launcher grammar out of Skill semantics;
- executes only exposed semantic WorkOrders;
- submits typed Result/Evidence through the completion Operation selected by the current Continuation, without next-action directives;
- recovers without conversation history;
- explicitly reconciles execution facts to Phase/Checklist;
- handles planning revision drift;
- keeps Skill-only context outside sm-workflow/CNCF;
- does not auto-chain Workflow invocations.

## 13. Reference flow

~~~text
Human-selected sm-* entry point
        |
        | manifest binding + invocation authority
        v
Phase / Checklist / Skill-only Context
        |
        | Read + Interpret
        v
Skill-owned WorkflowMapping
        |
        | Project
        v
SmExecutionContext / Sm* payload
        |
        v
registered sm-workflow Operation
        |
        | transport adapter: MCP or launcher/CLI
        v
CNCF Workflow DTO + sm-workflow
        |
        | WORK_ORDER when semantic work is required
        v
Skill executes bounded semantic work
        |
        | typed WorkResult / Evidence through
        | Continuation-selected completion Operation
        v
CNCF Workflow progression
        |
        | Result / Evidence / Terminal
        v
Skill Observe + Reconcile
        |
        | planning update
        v
Phase / Checklist
~~~

## 14. Related design

- [sm-workflow design](sm-workflow-design.md)
- [CNCF Workflow protocol application specialization](cncf-workflow-protocol-application-specialization.md)
- [Workflow handle and advance operation boundary](workflow-handle-and-advance-operation-boundary.md)

This file is the primary Skill-side design input. CNCF owns generic execution protocol/runtime; sm-workflow owns software-development execution semantics; the Skill owns planning interpretation, mapping, context projection, and reconciliation.
