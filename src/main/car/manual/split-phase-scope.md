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
`sm-workflow.split-phase-advance-command.v1:` followed by a JSON object
with `schemaVersion` and `payload`. There is no separate command-codec byte
limit for formatting or ignored diagnostic data. Transport/runtime resource
policies remain separate; codec acceptance is not a resource grant.
The decoder consumes the generated instance/context fields and ignores unused
diagnostics at every object level; it does not compare the whole input with a
regenerated JSON body. Extra stage, action, storage or command fields do not
select those controls. Missing or wrongly typed consumed fields and incompatible
schemas still fail conversion; actual current-call authority, context and native
state are resolved by the owning Operation. Logical reference strings are not
trimmed or pre-admitted as physical planning keys by this codec. The actual
planning-index read/publication owns its escaped-key bound when that key is
consumed; missing contexts fail at the owning context lookup. This describes a
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

`SplitPhaseScopeSemanticResult` contains only `decision`, `rationale`, `scopes`
and `evidence`. Workflow resolves the original context and checkpoint from its
issued request; the answer does not echo or certify them.

- `Enriched` supplies scopes for exactly the issued gap identities in their
  original order. Each producing and receiving range is a nonempty, distinct,
  ordered subset of its original requested range and has a rationale.
- `DecisionNeeded` supplies no partial scopes. Its rationale and evidence remain
  required; it does not approve a human choice or allow execution.

Evidence references must be nonempty and distinct and also belong to the actual
execution evidence. Normal reduced submission resolves the host-assigned result
destination and issued controls from managed state; return `result.value`, not a
destination comparison copy. Historical full-envelope input must use the assigned
result reference and actual issued continuation, revision, snapshot and contracts.
Unused legacy context/checkpoint copies and diagnostic fields are ignored, not
compared or used to select execution. Actual incompatible schemas or consumed
field types fail decoding; foreign business references, reordered/duplicate
scopes and missing evidence fail at their consuming boundary.

The application request and result payload codecs use separate typed schemas
`sm-workflow.split-phase-scope-request.v1` and
`sm-workflow.split-phase-scope-result.v1`. Producers emit `schemaVersion` and
`payload`; readers decode consumed typed fields without an exact-key or
re-encoding comparison certificate. Old unused fields do not grant authority.
Decode limits payloads to 1 MiB. CNCF owns the
outer WorkOrder/result envelopes. The normal component also accepts
`sm-workflow.result-submission.v1` with the typed semantic payload under
`result.value`, consumed evidence and session or necessary request selection.
This is distinct from returning a bare application payload where the host expects
a submission frame or a historical complete result envelope.

## Effects and retained observation

The mechanical route resolves the selected checkpoint's actual native receipt.
CNCF owns receipt and native-state admission; scope consumes that receipt's
postimage and actual current instance, not independently reopened Start/context
bodies or reconstructed prefix copies. The route checks the authenticated
participant and requires declared generated documents to exist and be readable.
An AI-origin checkpoint instead resolves its genuine retained partition
completion; it cannot substitute a mechanical receipt or recompute on absence.
These origins remain distinct from the scope task's actual issue/completion
contracts and native state/CAS transitions.

The full v1 payload can retain historical input copies and revision metadata.
Reading those passive values does not certify their agreement with the computed
snapshot or admit a transition. Mechanical observation uses the actual native
receipt's postimage/current state, not the historical payload's revision echo.
Publication consumes its privately constructed capture without rechecking full
copies of the same data. Remaining AI/scope and nested-codec checks are separate
implementation work; this distinction does not grant them a blanket exemption.

The route borrows the owning Subsystem's initialized storage and uses canonical
managed issue/completion. It creates no second pool, production SQLite access,
application claim/lock or implicit retry. Returned scope subsets do not change
candidate ownership, original closure obligations or transfer authority.

ScopeProjection derives its native-result address from the genuine owning
predecessor; it creates and reads no separate application locator. Observation
consumes the actual native receipt, not an application copy or historical proof.
Old full, malformed, crossed-reference or absent application copies are ignored
without rewriting. Only new Built documents accompany the real native transition.
Native payloads no longer emit comparison-only scopeWork, scopePredecessor or
scopeIssued; old versions ignore them. Real receipt before/next/current state
supplies the native state, without old-row rewriting or repeat publication.
Actual crossed receipt owner/type/address/instance data fails at its consuming
boundary. A missing receipt is actual absence; the real native state/CAS prevents
stale replay after an append. Required document readability is independent of
unused physical columns or document-copy equality. Other source/frontier/resource
checks remain separate adopted removal work.

ScopePlanning also selects the actual native calculation from its independently
owning Scope, without an application locator, presence join or side Create.
Its native intent uses an empty UoW program; no dummy receipt or checked marker is
created. Old unused application copies and native priorScope bodies may be absent
or unreadable without gating reopening or being rewritten. Original first
checkpoint and Start mode come from the genuine owning Scope observation.
Actual receipt states and saved downstream supply the successor; stale replay
after receipt loss is prevented by the actual native state/CAS boundary.
Actor/storage/CAS/Create remain at their effect owners. Other remaining nested/
frontier/source/resource checks are separate adopted removal work, not collectively
endorsed by this contract.

The current no-application-copy correction is authored but UNVERIFIED pending
fresh complete executable suites and a fresh independent connected review.
That authoring cutoff is now followed by bounded executable validation and an
independent connected source assessment. Its sole stale empty-row fixture finding
was corrected without restoring a copy row; the complete changed managed suite
then passed142/142. Runtime Help and full Phase acceptance remain open.

Owning checkpoint readers use the current call's declared storage targets, not
an equality proof against construction-time target copies. The admitted runtime
Catalog is consumed without another whole-declaration or source-line comparison.
Partition completion likewise has no comparison-only read-purpose certificate.
Actual selected rows and native state/effect permissions still belong to their
consumers. These owning bindings have bounded complete executable validation
and a fresh independent connected source assessment; broader predicate removal,
runtime Help and Phase acceptance remain open.

ScopeProjection and ScopePlanning native calculation payloads carry SavedResult
data only. Fresh payloads omit nested before/next; existing-v1 readers ignore
those obsolete members even when absent or unreadable. Actual transition states
come from CNCF's outer receipt and its native definition catalog, not a second
payload history. Private preparation still supplies the genuine intent; decoded
results do not grant publication or new effects. Current SavedResult behavior
has bounded direct-Operation/executable validation and independent source review;
broader predicate removal, runtime Help and Phase acceptance remain open.

Retained ScopeProjection reads the declared Built proposal, ownership and handoff
documents for presence, typed readability and their selected addresses. It does
not compare their bodies with saved checkpoint copies. Readable same-reference
documentation changes leave the native-derived result unchanged; missing,
malformed or differently addressed deliverables fail at document consumption.
Unrelated files are not checked, and observation does not rewrite documents or
append native history. Other remaining source/copy checks are separate adopted
removal work, not justified by this document-read contract.

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

Planning snapshot storage admits the declared wire fields and types, not a
reconstructed copy or proof that a whole candidate frontier is executable.
Standalone, native payload and existing full checkpoint readers share this
contract. A typed passive record can be stored even when candidate relationships
are unusable; Workflow routing and selected document/preview use must interpret
those relationships at their actual use boundary. Decoding never grants native
state, publication or document-write authority. Further removal of duplicated
consumer checks remains open; this separation is not overall Phase acceptance.

A freshly constructed planning outcome is copied with its already reached
Workflow variant. The copier does not compare its copied boundary documents or
reclassify its own emitted record; Human calculation uses this constructed
disposition directly. Saved raw records are a different input: selected Preview
obtains document material through the document consumer before rendering, rather
than repeating that consumer's full frontier inspection at each Preview layer.
Neither projection admits native state nor grants document application authority.
Other duplicated consumer/owner checks remain under the Phase 5 removal audit.

Public advance carries its already decoded command and observed original Start
mode through internal routing. Selecting PREVIEW or APPLY does not by itself
read the whole saved frontier again. The chosen handler still reads its own
actual native source and mode before effects. A saved-frontier reader returns
passive data without interpreting its meaning for selected documents; the
document consumer must refuse unusable input. Component-level reading does not
repeat the native owner's full history-prefix comparison. This does not remove
the framework's remaining comparison or imply overall Phase acceptance.
