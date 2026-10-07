# SplitPhase handoff scope contract

The current SplitPhase definition is `split-phase-selected-application-v12`. Its handoff
scope task is distinct from the partition-estimate/boundary task and older
novel-work inputs. Its managed continuation is included in the normal
three-workflow factory. Earlier additive assemblies remain separate fixtures,
not requirements to create a replacement default runtime.
The planning-only and paired Human assemblies have different downstream
availability; see the [Human direction contract](split-phase-human.md).

See the [reference manual](index.md) and [user guide](user-guide.md) for runtime
availability and host prerequisites.

## Operations

| Operation | Owner and purpose | Result boundary |
| --- | --- | --- |
| `SplitPhaseService.advanceSplitPhase` | Workflow observes the instance and original context, absorbs mechanical planning and selects required work or selected document effects from the saved Start mode. | Genuine WorkOrder/Decision, selected PREVIEW or actual APPLY Terminal, retained observation or explicit error; no caller-selected stage. |
| `SplitSemanticService.assessHandoffScopes` | Required semantic SPI for `AssessHandoffScopes`. The client identifies evidence-producing and receiving item scopes for the exact requested original transfers. | `SplitPhaseScopeSemanticResult`; no command, state, candidate or authority selector. |
| `SplitSemanticAdmissionService.admitHandoffScopes` | Declared deterministic admission associated with that Judgment. Its declaration is not an independently installed public handler. | Admission must be reached through the actual owning native call and result contract, not by decoding data. |
| `SplitPhaseService.submitHandoffScopeResult` | The configured managed public completion validates the actual issued task, original actor, result and native suspension, then uses canonical managed completion. | Completion acknowledgement of this semantic boundary only; not whole-application Terminal. |

## Advance input

`SplitPhaseAdvanceCommand` contains only `instanceIdentity` and a context with
`identity` and `revision`. It contains no next-operation, processing-stage,
candidate, repository or storage selector.

The application's command frame uses the exact prefix
`sm-workflow.split-phase-advance-command.v1:` followed by a closed JSON object
with `schemaVersion` and `payload`. The whole UTF-8 frame is bounded to 16 KiB;
the payload must match the generated command shape exactly. This describes a
codec, not permission to call an unavailable binding. Use the configured
client's encoder, not a fabricated context or an approximate envelope.

## Issued scope request

`SplitPhaseScopeSemanticRequest` carries the original context, checkpoint and
source references, a host-assigned result reference, distinct unresolved handoff
gaps and the relevant original items with their closure, prerequisites and
authority. Each gap retains its complete original transfer and the ordered
producing/receiving source ranges.

Workflow retains the complete candidate frontier, including Built candidates
and repository/numbering Decisions. A semantic request does not discard those
candidates or grant access to an executable planner. Stored or decoded payloads
alone do not prove current checkpoint provenance or native execution authority.

## Semantic result

`SplitPhaseScopeSemanticResult` echoes the exact context and checkpoint and
contains `decision`, `rationale`, `scopes` and `evidence`.

- `Enriched` supplies scopes for exactly the issued gap identities in their
  original order. Each producing and receiving range is a nonempty, distinct,
  ordered subset of its original requested range and has a rationale.
- `DecisionNeeded` supplies no partial scopes. Its rationale and evidence remain
  required; it does not approve a human choice or allow execution.

Evidence references must be nonempty and distinct and also belong to the actual
execution evidence. The common completion envelope must use the host-assigned
result reference and match the exact issued continuation, revision, snapshot
and contracts. Extra nested fields, old payload aliases, foreign references,
reordered/duplicate scopes and missing evidence are rejected.

The application request and result payload codecs use separate closed schemas
`sm-workflow.split-phase-scope-request.v1` and
`sm-workflow.split-phase-scope-result.v1`. Their envelope contains only
`schemaVersion` and `payload`; decode limits payloads to 1 MiB. CNCF owns the
outer WorkOrder/result envelopes. Do not return just the application payload
where the host expects the complete result envelope.

## Effects and retained observation

Before issue or completion, the managed route independently checks original
Start/context facts, checkpoint mapping, effective facts, Built documents and
the complete canonical history prefix. It distinguishes actual mechanical
checkpoint provenance from an actual retained partition completion.

The route borrows the owning Subsystem's initialized storage and uses canonical
managed issue/completion. It creates no second pool, production SQLite access,
application claim/lock or implicit retry. Returned scope subsets do not change
candidate ownership, original closure obligations or transfer authority.

An advance while the genuine scope task is pending returns that same issued
work. After a valid result is committed, the completion returns a WAIT
acknowledgement for the semantic boundary. Later advance independently observes
the retained completion. The planning assembly continues an ALL-Built frontier
through any genuinely required reasoning to the saved selected plan, without
inventing Human work. The paired Human assembly also issues or recovers a
Decision when the whole frontier requires Human direction, and can continue
independently verified ProvideResolution through planning/reasoning. A semantic
acknowledgement or Decision alone never means document application.

For a selected complete plan, the original Start determines PREVIEW or APPLY.
PREVIEW returns deterministic source sections and child material without writes.
APPLY saves native ApplyingDocuments intent, invokes only the runtime-fixed
document capability, saves applied readback and finally appends Completed.
The runtime owns executable, target, complete argument vectors, limits, driver
and grant. Intent/effect/completion are separate durable stages; file updates
are individually atomic, not a crash-atomic filesystem/DB transaction. An
uncertain acknowledgement stops that call; later advance observes saved state
and actual files before applying missing work. Completed observation does not
repeat the writes or allocate a new selection. Document conflicts currently
fail explicitly; their semantic/Human resolution handler is not yet installed.

## Compatibility and remaining validation

Existing v5/v6/v7/v8/v9/v10 definitions are incompatible data, not automatically migrated or
silently rebound. Current v12 generated declarations do not establish their
storage compatibility.

The reasoning extension declares a distinct `assessReasoningDemand` capability
and `submitReasoningResult` completion. The additive planning assembly installs
the paired native issue/result/durable-successor handlers. The normal
three-workflow factory composes that connection with its configured host ports.
They are not aliases for the handoff-scope operations above. The scope client
must return only the issued scope result, not choose a reasoning operation.

## Native Human direction

The Human interface introduced in v7 independently declares `PresentSplitHumanDirection`, Required
`SplitHumanService.presentSplitHumanDirection` and Provided
`SplitPhaseService.submitSplitHumanDirection`, with the Human lifecycle
`Idle -> Awaiting -> Answered`. These are not GoalPhase Human operations or
semantic Judgment alternatives.

The paired Human host assembly installs native issuance and completion through
public operations. The normal factory now selects the paired connection;
the earlier planning-only assembly does not supply those Human handlers.
Its input has the original context and first-checkpoint
references, the exact scope-publication reference and the complete bounded
scope frontier. Workflow selects a genuine Human need from the whole frontier;
ALL-Built remains in deterministic planning. Native issue/recovery borrows the
existing owning Subsystem storage. A decoded payload, declared choice or
authenticated relay is neither Human evidence nor application permission.

Fresh answers use independently verified host evidence, not relay authentication
as consent. The completion records HumanAnswered. Later advance can return a
typed BLOCKED Terminal for Defer or CANCELLED Terminal for Cancel. ProvideResolution
names separate interpretation data; the paired Human assembly observes the
actual resolution and continues its published frontier through planning and
reasoning to selected Preview. The normal factory includes these handlers,
without issuing Human evidence or granting client storage access. The
[Human contract](split-phase-human.md) describes
the answer envelope and retained/uncertain recovery boundaries.

Focused Service/CLI and physical SQLite-reopen examples exercise both semantic
alternatives, crossed result/context rejection and foreign-actor refusal.
Fresh982 passed1,171/1,171 across138 suites in the three-family regression,
including five known-work APPLY/reopen cases. Their original Start setup is
explicitly private. Later direct-Operation scenarios run the normal factory,
but document-conflict semantic/Human resolution is not an installed capability.
The completed-owned-work guard now passes first-Apply and partial-child
regressions and independent bounded review. Integrated regression/Phase review
remain required: current whole-root1122 failed in distinct GoalPhase producer
fixtures, not this guard. Selected positive tests do not close that boundary.
Broader transports and public skills retain their
separate Phase assignments. Rendered/runtime Help remains unverified; supporting
tests alone do not establish practical Phase or release acceptance.
