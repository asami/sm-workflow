# Delegated development and delivery

Date: 2026-11-05
Status: D1–D3 source routes delivered; C5 distribution alignment authored; installed and actual cross-chat qualification pending.
Owner: [Phase 6.8](../phase/phase-6.8.md), D1–D4.
Public skill: [sm-delegate-task](../../skills/sm-delegate-task/SKILL.md), packaged by the Component.
Finite caller: [public operations and host bindings](native-delegate-task-caller.md).
Source packaging does not establish actual installation.

## Independent entry, shared execution

`sm-delegate-task` supplies an authorized request to an existing destination chat
through a runtime-owned delegation Workflow. It is not a wrapper around
`sm-test-fix`, `sm-review-fix` or `sm-goal-task`. Those entries keep their existing
test scope, review, commit and completion contracts. Do not call them and then
widen their test scope, add publication obligations or reinterpret their terminal
result to satisfy delegation.

Reuse the shared WorkOrder/Continuation, bounded implementation/repair execution,
diagnostic information, validation selection, conditional assessment/review,
integrated acceptance, typed Git/SBT, Journal/Evidence and result collection.
Add only the delegation business lifecycle and thin caller adapter; no second
executor, FIX controller, publication runner or skill-owned state machine.
Concrete public Operation/DTO names are declared in CML: DelegateTaskService,
DelegateTaskDeliveryService and NativeWorkflowPreparationService.prepareDelegateTaskStart.
Use their normal ABI/help/resources and the finite caller contract.

## Request and ownership

The request identifies requester and destination owners, target Component and
repository, objective and source/failure/finding references, acceptance conditions,
validation requirements, permitted operations and existing authorization, delivery
requirements and return destination. Work inventories are not per-file consent.
Carry existing authority under the [6.8 contract](commit-completion-and-recovery.md)
when Component/purpose/acceptance/operations are covered. A real uncovered effect
or explicit exclusion retains its actual decision.

The requester runtime owns dispatch coordination, receipt and consumer adoption.
The destination runtime owns development, required checks, commit and publication.
Each retains its actual Session and correlates them with the existing request
identity; do not merge their histories or create a new repair Session on redelivery.
Where the chat transport belongs to the caller, runtime issues typed dispatch or
return work to that authorized caller. Preserve the existing chat context; no new
Codex API session or autonomous messaging authority is implied.

Requester admission and destination execution have separate host requirements.
An exact valid requester keeps the full destination request as metadata and
starts at `DispatchReady` with no selected executable checks or execution facts.
Its normal host still owns complete-input selection, participant and idempotency
admission, canonical Session/storage/definition/closure checks, independent
adoption evidence and read-only Git observation of its own caller repository.
The configured caller root and branch must match the actual observation; its
retained head and dirty paths describe that caller only. Unrelated staged and
dirty files are preserved without destination path filtering or any staging,
normalization or commit. The requester needs no producer checkout, concrete
executor mapping/origin, destination check binding or publication registration.
Optional mapping/origin configuration must still be a valid pair. No parent
executor model or destination evidence may be invented to satisfy metadata
admission.

The destination retains strict logical or concrete operating-context resolution, adopted
repository/path/dirty-scope admission, installed validation and publication
bindings, and genuine execution, review, commit and publication evidence.
StandaloneFix and GoalTask retain their existing execution requirements.
Final delivery evaluates the retained committed candidate and its matching
successful publication. It requires neither a globally clean publication
worktree nor an unchanged list of later worktree modifications. Unstaged work
is preserved and is not represented as part of that committed result. Root,
branch, committed HEAD/index tree, qualification and publication correspondence
remain owned by their existing boundaries.
The requester exemption adds no operation, schema, persistence, property,
executor, permission or queue. See the [finite caller sequence and role-specific
host bindings](native-delegate-task-caller.md) for normal prepare, Start,
Advance, delivery observation, description and adoption operations.

Transport acknowledgement is not acceptance, work started, tests passed or result
delivery. Consume actual observations and retained outputs. No extra AI calls,
periodic polling or progress-only chat messages manage waiting or icons. Initial
6.8 scope is one admitted request to an available selected existing chat. Durable
multi-request FIFO and busy-destination scheduling remain in Phase 7; do not
interrupt a different task or claim FIFO support from this minimum route.

## Runtime-owned validation scope

Determine the required verification from the delegated acceptance conditions,
actual impact and existing applicable evidence, independently of other skills'
fixed contracts. Retain explicit requirements; use deterministic selection first
and delegate only unresolved semantic questions.

| Case | Required route |
| --- | --- |
| Localized implementation/repair with bounded impact | Compile and applicable focused behavioral tests/lint; conditional review |
| Shared or broad impact not covered by focused tests, or explicitly required FULL | Required FULL coverage, reusing applicable prior checks |
| Documentation-only outcome without executable impact | Applicable document/semantic checks; no invented product-test requirement |

Cross-chat dispatch, publication and a new delivery record alone never imply
FULL. Preserve FULL/HEAVY classification and explicit execution requirements;
do not substitute compile-only success for required executed behavioral tests.
Compile failures leave dependent tests not-executed. Validation failures use the
shared diagnosis/FIX/affected revalidation and runtime convergence/10-cycle limit,
without another delegation-specific counter or refund of earlier attempts.

Use conditional scoped reviews and integrated outcome review where the delegated
acceptance requires it. Reuse covered judgments; do not duplicate inner and outer
reviews or an already satisfied FULL simply because delivery follows development.
The consumer separately assesses actual integration impact and reuses producer
evidence at its demonstrated scope.

## Delivery lifecycle

Runtime progresses: admitted request -> dispatch -> destination work -> required
validation/acceptance -> local commit -> required publishLocal -> result
return -> requester adoption. Local commit is mandatory for successful delegated
development completion; an uncommitted implementation is progress, not a completed
delegate result. Publication is selected by delivery requirements, with normal
dependency delivery requiring it. Legitimate no-change work returns the applicable
existing commit/publication without empty or duplicate effects. If commit authority
is genuinely missing, resolve that boundary rather than silently completing
uncommitted. A code-bearing request carries in-scope commit authority at admission.

Destination sm-workflow uses the shared commit/recovery owner and
[LocalPublicationService](runtime-owned-local-publication.md). Runtime decides
publication necessity, performs typed SBT when required and collects the actual
result. AI answers only an issued unresolved build-relevance question; neither
skill starts SBT or a publication subagent. Missing capability is reported to its
runtime owner, not worked around by invoking another skill.

Return source/commit, actual focused/FULL scope and results, review disposition,
artifact coordinates and publication result/selection references, unresolved work
and consumer instructions. Development accepted, committed, published, returned
and adopted are separate outcomes. Publication failure retains successful work
and commit; continue only unfinished delivery. Lost result transport collects the
retained result without repeating implementation, tests, commit or publication.
Live/unknown effects keep their uncertainty and are not blindly retried.

The requester adopts the reported artifact through its ordinary runtime route,
including activation/reload when needed; publishLocal success alone does not
prove that the running consumer uses it. Preserve the selected artifact's
attribution if the same development coordinates are later republished. Reuse
existing publication/selection resources, not new hash gates or manual JAR copies.

Optional best-effort RepositorySync is a separate post-delivery policy. Reuse an
applicable existing sync result and do not hold local delivery on remote failure.
Phase 6.9 owns sync simplification; it is not needed to prove basic 6.8 delivery.
Explicit delegation of an entire named Phase remains a Phase 7 composition using
the existing GoalPhase contract, not a reimplementation within this minimum Task.

## Qualification and preservation

Use the common [completion-record/Terminal contract](commit-completion-and-recovery.md#completion-records-and-terminal-scope--2026-10-27).
Required completion records and their local commit stay inside runtime closure;
semantic prose may be issued to AI before completion. No external post-Terminal
commit skill is used. A destination's completion is scoped to that destination;
requester adoption and optional remote sync remain distinct outcomes.

Prove one installed cross-chat request through actual focused success, commit,
publishLocal, result return and requester adoption first. Also prove impact-based
FULL selection and completed-result reuse/failure continuation with bounded cases.
Keep test-fix/goal-task contracts unchanged and reuse their underlying engine
evidence; do not repeat their full acceptance merely to introduce an adapter.

Preserve active Sessions, original bases, accepted results, history, repair counts
and successful verification. Do not migrate an active 6.7 episode to delegation,
re-Start it or replay accepted Submit. Current authorized work continues on its
existing public route until explicit qualified adoption. Phase 7 retains modes,
FIFO, dependency graph and subtrees; Phase 17 retains broader Task/FIX inputs.


## Source connection for P68-D1/D2 — 2026-10-27

The independent CML entry is `DelegateTaskService`: `startDelegateTask`,
`advanceDelegateTask`, `submitDelegateTaskResult` and its information operations.
`prepareDelegateTaskStart` prepares the complete public `DelegateTaskInput` for
normal selection/profile admission. Preparation is passive; it is not Human
consent. The input includes distinct requester/destination identities, return
owner, Component, purpose, conditions, source and authorization references,
permitted operations, literal exclusions, impact scope and optional publication
target/coordinates. Both roles use the same admitted request with only `role`
changed. The destination refuses a missing actual logical origin/dispatch policy
or required concrete execution/profile pair before fresh semantic work. The requester validates the complete input and any
optional profile/origin pair without resolving destination executable bindings.
The runtime derives one stable native Session identity per
request, Component and role; exact Start redelivery returns that Session's current
native state and rejects changed ownership/input. There is no second queue or
execution ledger.

The independently named DTOs and workflow use the existing bounded WorkOrder,
Continuation, semantic completion, execution-admission, repair-count, check,
review and commit machinery. They do not call the goal-task or test-fix public
entry. Localized code requires actual compile and focused behavioral checks;
broad scope retains an explicitly selected FULL check. Detailed review remains
conditional unless the request requires it. The original goal-task mandatory
review and standalone FIX uncommitted completion contracts remain unchanged.
Destination commits use the shared selected staging and mechanical hygiene policy;
required publication uses `LocalPublicationRuntime` and its retained actual
request/result. A legitimate unchanged candidate returns the applicable existing
commit. A publication wait retains the commit and successful checks.

Fresh destination `Implement`, `Repair`, independent `Review` and
`DeliveryAcceptance` retain an optional
`operatingProfile` capture and the resolved requirement in the actual issued Work.
They share GoalPhase's pure capture computation and the existing versioned finite
caller adapter, including its deployed semantic role definitions and actual tool
observations. Selection is configuration, not an observed parent/provider/model.
DeliveryAcceptance uses the implementation capability class for read-only
post-delivery judgment after actual checks, required independent review, commit
and selected publication. It is separate from independent Review and owns no
delivery effects.
Pending work keeps its stored route after configuration changes. Actual Submit
checks the original public issued context, captured candidate chain, native
ownership/state and actual worker; Review also requires independently admitted
host evidence and a worker different from every retained implementer/repairer.
Delayed contexts, malformed/crossed captures and evidence outside the captured
chain refuse. A result cannot supply a replacement operating capture.
Each fresh missing-row destination issuance repeats preflight against the actual
input and host before capture and suspension. Removing origin or standard policy
after Start refuses before any new issued row or native frontier/history change.
The public issued context contains the exact stored capture. Logical completion
also requires the actual worker identity to equal the observed executor context
identity before candidate-chain and independent-review admission; concrete and
historical captures preserve their existing identity rules.

Older Work without the optional capture retains its original concrete meaning and
omits the field when re-encoded. A present null or malformed capture is refused.
The existing concrete caller harness remains unchanged; standalone FIX semantic
execution keeps its concrete route and validation-only FIX stays check-only.
This logical source connection does not establish private D2 publication,
installed qualification or actual cross-chat delivery acceptance.

`DelegateTaskDeliveryService.observeDelegateTaskDelivery` records attributable
`dispatch-observed`, `result-received`, `return-observed`, `adopt` or `defer`
observations. Dispatch does not certify acceptance. A received result must match
the original owners, full input, exact destination Session, accepted conditions,
actual checks and commit, and required publication target/source. Destination
`ReturnReady` differs from observed `Returned`; requester `ResultReceived`
differs from `Adopted`. Exact duplicate observations preserve the current state;
changed observations of an already recorded event are refused.

Requester `adopt` attaches the returned result and attributable artifact selection
only. `describeDelegateTaskAdoption` exposes the exact current Component instance,
Session, source revision, participant and returned target. The ordinary evidence
preparation route accepts family `DelegateTask`, kind `adoption`, that description
and the proposed observation. Its passive projection must be independently
admitted by the existing configured host-evidence owner under
`delegate-task-requester-artifact-adoption`; caller JSON alone is insufficient.
Admission binds the current target, received source commit, exact coordinates,
selection reference and observing participant. The stored adoption scope is
`result-and-selection-attachment-only`. Optional activation/check references are
attribution, not proof that this service performed or accepted those effects.
Actual artifact selection, reload and affected consumer checks remain with the
normal host/artifact owner. Publication or transport success cannot stand in for
their evidence.

`DelegateTaskWorkflowSpec` authors executable coverage of this ordinary route,
actual fixture compile/focused checks/commit/publication, redelivery, no-change,
attachment admission and policy contrasts. Its fixture transport and host
observations are specification-owned. Source authorship does not establish an
installed cross-chat result. The authorized isolated sample and actual consumer
selection/use qualification remain pending on their public runtime owners; no
old Session or P67 fixture is migrated by this connection. D3/D4 and C4/C5 retain
their remaining failure, completion-record, installed/help and live acceptance
work.


## Completion records and delivery continuation

An explicit `sm-workflow.completion.records` target with family `delegate-task`
and the original request identity selects existing owned Markdown destinations.
Their evidence-backed clerical updates are prepared in the shared bounded owner
before local closure and coalesce with the implementation commit when possible.
No-change requests keep their applicable implementation commit and create a record
commit only when an explicitly required record is missing. This does not change
the standalone FIX contract or authorize a new semantic narrative after Terminal.

Broad impact selects the uniquely installed `FULL` Test binding in addition to
explicit focused acceptance checks. Localized impact does not add FULL merely
because delegation or publication occurs. Missing or ambiguous required bindings
refuse at the destination before issuing unusable work. The requester retains
these full requirements in its original input with an empty executable selection;
it cannot substitute empty receipts for the genuine returned destination checks.
Existing check ordering retains compile
failure short-circuiting; FULL and HEAVY remain distinct bindings.

Delivery publication uses the existing LocalPublication decision/execution/result
contract. A retained successful result is collected, a confirmed nonzero terminal
failure may receive a retained successor for the same delivery source, and a live
or unknown result is not replayed. Accepted implementation, checks, review,
original failure and local commit survive this continuation. Publication is an
artifact outcome; requester adoption and consumer activation are separate facts.

## Initial condition timing and pending legacy scope correction

All adopted condition identities remain mandatory in every semantic result, in
their original order. New public `DelegateTaskInput` has a final repeated optional
`submissionConditions` field. Its default absence at the public input becomes
the explicit durable value `Some(Vector.empty)`: no condition is evaluated at
submission and every original obligation is deferred to final DeliveryAcceptance.
This is an empty timing selection, never lost or removed conditions. A nonempty
selection must be a unique order-preserving subset of the original identities;
the full original vector is valid too. Requester and destination carry the same
declared timing with only their role changed. Standalone FIX and ordinary
GoalTask cannot use this delegation timing field.

For example, `conditions: [behavior, validation, commit, publication]` with
`submissionConditions: []` reports all four false during Implement, Repair and
Review, with truthful detail that actual later checks and delivery are not yet
established. `submissionConditions: [behavior]` reports behavior true and the
other three false when successful implementation or review is submitted. The
full subset requires all four true at submission. No English-string heuristic
classifies timing, and no additional Human timing selection is required for a
new request's declared subset. A missing condition or prematurely true deferred
condition refuses.

Historical durable `S.Input` without the optional field retains `None`, omits
that field when re-encoded and preserves strict all-true submission semantics.
Reading an old record never changes its timing. Existing pending legacy requests
may use the already available public correction described below when an actual
responsible Human selects it; no private row migration or accepted resubmission
is implied. Validation-only qualification remains nonsemantic. Requester
transport retains the complete original conditions, owners, authority and
explicit timing. A returned destination cannot substitute different timing for
a new explicit request; already supported explicitly corrected legacy timing
remains admissible with all other original request fields unchanged.

For an existing still-unclaimed initial Implement issue, the destination can use
`describeDelegateTaskScopeCorrection`, passive
`prepareDelegateTaskScopeCorrection`, and `correctDelegateTaskScope` to admit the
exact current-call ExplicitHuman selection on that same Session. Description
binds the actual Component instance, native frontier, issue, business payload,
root, branch, participant, and the selected original IDs. The actual responsible
Human chooses that subset; maintenance authorization and passive preparation do
not supply that choice. The same published correction also supports additional
literal owned paths for the same purpose, subject to the retained host ownership
and delegation exclusions. It replaces no original ownership, purpose, operation
or authority.

The same public route also accepts an unclaimed, unsubmitted Repair issue when
necessary same-purpose literal inventory is missing. Supply additional owned
paths and leave submission timing empty; Repair cannot select a new timing plan.
The existing failed check receipts, candidate, prior results, generation, repair
counts and selected limit stay attached. The superseded issue remains historical
and ordinary Advance issues the successor Repair in the same Session. This does
not permit re-Start, a new repair window, effect replay or private storage edits.

Correction appends a typed native WorkOrder supersession, preserving the old
immutable issue, base, generation, repair control and counters, prior results and
UNKNOWN effects. An unclaimed issue does not establish that external effects did
not occur. Correction changes no source file and executes no checks, commit or
publication. Only a separate ordinary Advance issues the amended successor.
Repeated exact correction is idempotent; a changed or stale target is refused.
Changing a declared timing subset requires a fresh description of the exact
current still-unclaimed initial Implement issue and its independently selected
actual Human correction. A changed subset is permitted even when that input
already declares timing. The same subset without new paths is a no-op and
refuses. Invalid, duplicate, out-of-order or foreign condition IDs refuse.
Existing immutable checkpoints and correction histories retain their original
meaning; passive description and normal Submit provide no replacement authority.
An old worker result cannot complete the
successor: after any correction history, submission must carry the exact current
`issuedWorkContext`, and every historical supersession checkpoint is verified.
Force-closed work remains closed.

After actual selected validation, required independent review, local commit and
normal required publication are ready, pending later conditions cause ordinary
Advance to issue read-only `DeliveryAcceptance` through the existing
implementation capability. This is final semantic evaluation of the original
obligations against genuine retained validation, delivery and application
evidence. It does not execute implementation, checks, commit or publication
again, and it cannot request repair, diagnostic work or a new review disposition.
Only accept or decision-needed is available. Acceptance requires every original
condition true and retains the actual committed source and ready required
publication. It appends the final report and evidence reference while preserving
checks, prior independent review, candidate, commit, publication and counters.

`ReturnReady` still requires all final conditions, actual checks, required
review, the selected source commit and actual required publication. Requester
receipt preserves the original explicit timing, or the destination's explicitly
adopted correction of a historical absent timing plan, while
continuing to compare the original conditions, ownership, authority and every
other request field. It does not certify consumer activation or application from
prose or publication alone.

`NativeCommonDelegateTaskCliSpec` is an explicitly selected, DoNotDiscover
RuntimeTestDeclaration over one tiny isolated LineWriter build. Its normal host
has no runtime.definition and supplies a fixed nonempty TestOnly binding at
ordinal two. Public requester transport and destination preparation/Start share
the original explicit empty timing. Actual typed Compile/TestOnly, destination
local commit and normal tiny artifact publication precede DeliveryAcceptance.
Final result transport and repeated collection must retain those effects
without duplication. Human, worker and transport judgments in this fixture are
specification-owned. Source authorship of this case is not an installed result,
actual consumer consent, independent product review or consumer activation.

## Returned inventory continuation — 2026-11-07

Requester intake accepts the original producer input and an order-preserving
extension of its owned paths and source references. Added paths must be valid,
unique, permitted by the unchanged exclusions, and accompanied by an added
source reference. Existing paths and references remain exact prefixes; all other
request and delegation fields retain their original comparison, including owners,
purpose, checks, conditions, operations and authority. The retained source
reference identifies the producer's correction but does not authenticate another
runtime's storage. The caller and existing effect boundary retain attribution.

## Confirmed-ended selected validation continuation

GoalTask and destination DelegateTask share explicit continuation for an actual
exited-zero Unverified TestOnly result with a usable completed-test observation
and no failed assertion, including a complete `ZeroTests` observation with zero
tests and zero completed suites. `ZeroTests` stays unsuccessful: it permits an
explicit new attempt after correction, never acceptance or a fabricated PASS.
`describeDelegateTaskValidationContinuation` and
`adoptDelegateTaskValidationContinuation` use the declared bounded typed inputs;
GoalTask exposes the same common path as `describeGoalTaskUnverifiedValidationContinuation`
and `adoptGoalTaskUnverifiedValidationContinuation`. Existing supplemental
GoalTask operations and stored directions retain their original meaning.

A description contains the existing Session/frontier, affected check and owned
scope. `RetrySelectedCheck` admits a new intent only for the same candidate;
`AdoptCurrentOwnedCandidate` permits native observation of the current selected
owned changes, including necessary same-purpose inventory additions. `Defer`
changes no state. Use `prepareEvidence`, family GoalTask/DelegateTask and kind
`selected-validation-continuation`, with the actual Human direction and current
host evidence admission. Preparation is passive, not consent.

Adoption runs no source or SBT effect. Ordinary Advance owns the new check or
candidate capture. The old unverified receipt and successful prefix remain in
native history. Unchanged source reuses its applicable prefix and retries only
the affected check before unstarted later checks. Changed production source
requires new Compile coverage; old success is never relabelled. Conditions,
check identities/order, original base, accepted implementation and repair
counters are retained. This path does not admit unknown/live execution,
permission/admission refusal or failed assertions, and creates no alternate
runner, state store or receipt ledger.

With the current ordinary typed `testOnly <classes>` execution route,
`@DoNotDiscover` can suppress even a named suite and produce an accurately
reported zero-test selection. Correct the producer's suite classification or
supported build selection before admitting the new attempt. Where an explicitly
selected private-input suite must stay out of normal full validation, use the
existing standard `Slow` classification with a full-test-only tag exclusion and
an explicit TestOnly selection that admits that suite. Removing `DoNotDiscover`
alone must not silently add private tests to full validation. No CNCF execution
contract change or alternative test runner is implied. A changed build or test
source uses `AdoptCurrentOwnedCandidate` so fresh Compile and affected checks
cover the new candidate; the old zero-test result remains in its native history.
