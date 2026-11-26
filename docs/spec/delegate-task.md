# Delegated development and delivery

Date: 2026-11-05
Status: D1–D3 source routes delivered; C5 distribution alignment authored; installed and actual cross-chat qualification pending.
Owner: [Phase 6.8](../phase/phase-6.8.md), D1–D4.
Public skill: [sm-delegate-task](../../skills/sm-delegate-task/SKILL.md), packaged by the Component.
Finite caller: [public operations and host bindings](native-delegate-task-caller.md).
Source packaging does not establish actual installation.

Ordinary issued semantic work follows the shared
[sm caller role-resolution contract](native-host-execution.md#ordinary-sm-semantic-role-resolution).
An independent cncf-* maintenance producer is a separately authorized route;
its model/fallback/Manifest rules never replace the consumer's retained sm work.

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

## Reconstruction of derived requester validation policy

A requester may retain an incorrect derived check or Scala lint requirement
before the destination accepts Start. The common management reconstruction
policy supplies assessment, genuine direction admission and logical resume
selection for both GoalPhase and this requester route. The existing native
bounded owner remains responsible for Session history, storage and Advance.

`DelegateTaskService.describeDelegateTaskRequestReconstruction --sessionId`
reads the active original requester at `DispatchReady`. Its opaque target carries
only the component instance, Session, source revision, original participant,
request identity and typed checks/Scala lint policy. The result explains any
ineligibility and offers `AdoptDerivedValidationPolicy` or `Defer`; a description
supplies no effect or Human authority.

`DelegateTaskService.reconstructDelegateTaskRequest` accepts exactly one typed
`input` or UTF-8 `inputResource`. The input contains the described target,
corrected checks and `scalaLint` (`off` or `required`), choice, rationale,
original participant, actual Human reference and attributable intake observation.
The observation names the destination, notification status, intake status,
handler status, optional destination Session and actual source references.

An eligible delivered notification has an actual `rejected-before-start`
intake and an `ended` handler. An eligible `not-delivered` notification has
actual `not-submitted` intake and an `ended` handler. Both require independently
admitted evidence covering the complete policy correction, observation and
source references. A known destination Session, accepted or started intake,
live handler, or unknown effect refuses with an explanation. Missing receipts
or source absence never establish nonexecution or justify switching lint off.

Use the existing `NativeWorkflowPreparationService.prepareEvidence` with
`family=DelegateTask` and `kind=request-reconstruction` to derive an importer
from the actual description and complete correction input. The independently
provisioning native evidence owner authenticates purpose
`delegate-task-request-reconstruction`, target, direction, Human reference and
participant. Passive preparation grants no authority.

An adopted direction appends one optional typed `requestReconstruction`
checkpoint and effective input in the same native Session. Only checks and
Scala lint policy change. Business purpose, paths, conditions, submission
timing, source and authority references, publication requirements, operations,
base, original input and selection, all prior records/results and actual repair
counters remain retained. Code delivery still requires Compile and appropriate
TestOnly declarations. The requester performs no destination preflight or
execution; destination Start resolves its actual host and retains mandatory
lint whenever selected.

Ordinary `advanceDelegateTask` returns `Dispatch` with optional typed
`DelegateTaskView.dispatchInput`, derived from current canonical requester
input with only the role changed to `destination`. Submit that complete input
to the destination's normal first Start. Repeated requester Start remains
strict, and returned producer results must match the current corrected
request, including checks and lint. Recollecting the identical admitted
direction uses its original target after revision advances and appends nothing;
conflicting directions refuse. `Defer` leaves the state unchanged.

Executable evidence: `WorkflowManagementReconstructionSpec`,
`DelegateTaskRequestReconstructionSpec`, `DelegateTaskDeferredLintSpec` and
`DelegateTaskRequesterRoutingSpec`. The separate installed qualification covers
an old requester reopened by the new component and the destination's real
validation, commit, publication and strict return.

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

Cross-chat waiting defaults to [parent-owned waiting](native-host-execution.md#parent-owned-cross-chat-waiting).
With an active Goal, the requester remains in the same turn, waits on the
destination via `wait_threads`, and continues the same Session after receipt
and adoption of the correlated result. Follow
[mode selection](native-host-execution.md#cross-chat-waiting-mode-selection)
for optional reporting and explicit notification-only return. Retain the
admitted request and Sessions.

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

A materialized `DeliveryAcceptance` decision-needed result supports supplemental
qualification on the same destination Session. The existing
`describeDelegateTaskSemanticContinuation` returns a compact target for the
actual completed judgment and its unresolved condition IDs. To resume, use
`resumeDelegateTaskSemanticContinuation` with `ReevaluateDelivery`, the exact
target, and `deliveryQualification` matching its commit, tree and the conditions
being reevaluated. Use `upstream` to reference the producer's actual reports. The
independently admitted Human direction binds those references to the recorded
judgment and publication. The evidence issuer verifies the semantics and material
origin of an external report.

Use the existing `prepareEvidence` kind `semantic-continuation` for the complete
direction and the usual independently authorized import. Ordinary Advance
issues a new read-only judgment with the actual prior result and supplemental
admissions. A different worker evaluates the complete initial conditions.
Reusing a judgment or upstream evidence reference cannot create another
admission. Commit, publication, validation, review and repair control remain
attached throughout. An actually accepted new judgment reaches ReturnReady
through normal Submit and Advance.

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
and the proposed observation. The public Operation compares the issuing
Subsystem's runtime name with its effective issuer declaration and the
presented issuer before producing its passive projection. A mismatch reports
the conflicting issuer values and leaves the requester Session unchanged.
The resulting projection must be independently
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

For destination `scalaLint=required`, normal selection includes one installed
FixCheck when explicit checks do not already select it. Explicit check order and
the original requester input stay unchanged. Standard deferred assembly resolves
that binding from the selected repository's configured typed SBT programs and
workspace, retaining its actual validation ordinal. Missing or ambiguous relevant
bindings refuse before Start; lint-off selection adds no lint check. The returned
result still requires actual successful lint evidence under the original policy,
and its original explicit checks and condition identities remain binding.

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

Use `conditions` for the semantic obligations of the request. The selected
`checks`, `reviewRequired`, retained local commit and optional
`delegation.publicationTarget` already specify the ordinary runtime effects.
The destination verifies those effects from retained typed evidence. Callers can
omit `submissionConditions` and list only semantic conditions such as
`conditions: [preserve-input-text, append-one-terminator]`. Both are reported
false during Implement, Repair and Review and evaluated at DeliveryAcceptance.
An explicit `submissionConditions: [preserve-input-text]` evaluates that
condition at submission and defers the other. The full subset requires every
condition true at submission. The runtime uses the declared subset as written.
A missing condition or prematurely true deferred condition refuses.

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

For path-only additions within the existing authorization, the owning caller
uses `extendDelegateTaskOwnedPaths` before dispatch of an unclaimed Implement
or Repair. This records the same-purpose rationale and original delegation
authority with the same native supersession. The runtime checks the accepted
Start, current participant, source, explicit exclusions and any retained parent
inventory. It admits exact additions that do not capture existing unrelated
changes. A separate ordinary Advance issues the amended work. Original timing,
conditions, operations, history and counts carry forward. Existing Human scope
correction remains available for an actual judgment about authority or timing.

After that correction retires an Implement issue, ordinary Advance can rebind
the still-unissued implementation to the observed current source. It requires
the same root and branch, valid adopted ownership and a current HEAD descended
from the retained execution base. It retains a mechanical checkpoint with the
current base and unrelated dirty snapshot before issuing the successor. The
earlier checkpoint remains immutable history, and the adopted input, condition
timing, counters and partial or staged work are preserved. If the observation
does not qualify, Advance refuses; an already issued worker, Review and candidate
recovery are outside this continuation. Subsequent implementation freshness and
candidate checks still use the newly retained source boundary.

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
condition true. Issuance, result admission and requester receipt share the check,
review, commit and publication evidence gate. Ordinary local publication must
be ready for the configured target and Component and match the accepted commit
and tree. Native `unnecessary` remains a ready result. Managed development must
retain its scoped publication for the requested edge, provider Component and
selected coordinates, from the clean accepted commit and tree. Missing or
crossed evidence, failed checks and UNKNOWN effects cannot satisfy the gate. A
successful judgment appends the final report and evidence reference while
preserving checks, prior independent review, candidate, commit, publication and
counters.

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
ordinal two. Public requester and destination preparation/Start omit submission
timing and retain the same default empty selection. Actual typed Compile/TestOnly, destination
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

### Selected Test/TestOnly result recovery

`describeDelegateTaskValidationRecovery` describes an active destination Session's
retained selected Test/TestOnly unresolved intent or genuinely recorded ended
Incomplete result, successful applicable prefix, unchanged Git candidate,
and current host configuration without advancing or running a check. The selected
SBT test ceilings are conservative capture upper bounds: CNCF may admit stricter
host limits, and the description does not assert the exact limits of a future run.
The old native process result and raw captures remain unknown when unavailable.

`recoverDelegateTaskValidation` accepts an exact described target and a closed
historical proof through the independently provisioned host evidence boundary.
The proof references both a terminal event and completed caller invocation; its
historical association is an attestation, not a native process receipt or a PASS.
Passive `prepareEvidence` uses family `DelegateTask` and kind
`delegate-task-validation-recovery`; preparation alone grants no authority.
Recovery appends only a direction, preserving the original immutable frontier.
A later ordinary Advance must recheck the direction and policy and own any new
intent. An incomplete or missing capture cannot establish successful validation.

The described `captureAvailability` is `not-retained` when no native
reservation exists, `absent` when its admitted File read confirms no artifact,
`available` only after the exact owner, Session, intent, check and source
association and complete canonical contents are verified, and
`unavailable-or-unreadable` when those facts cannot be established. The latter
is not evidence of loss. A matching host-attested `available` direction may
collect the real retained result without rerunning SBT. For `not-retained` or
`absent`, an independently admitted `unavailable` direction retains the lost
result and permits only a separately prepared new attempt. Neither path turns
missing or partial data into success.

A recorded confirmed-ended Incomplete Wait has optional target field
`retainedOutcome: "incomplete"`. Its selected index is the last receipt index;
the earlier receipts are the successful prefix. An unresolved intent uses the
next unrecorded index and omits that field, retaining the historical full-Test
wire. The exact native frontier binds the original receipt. TerminationUnconfirmed,
missing native observations and unrelated Wait states do not qualify. Ordinary
Advance exposes the stopped result and recovery need without restarting checks.
An independently admitted exact Human direction removes only that last
Incomplete receipt from the new active projection. Native history, input, base,
candidate, accepted semantic results, condition reports and repair counts remain.
Separate Advance runs the same remaining check once and reuses applicable
retained TestCompile and estimate. Repeated collection or direction cannot
authorize a second attempt. Defer or no recovery call runs nothing.

Every ordinary new DelegateTask TestOnly attempt requires the existing
`sm-workflow-validation-artifacts` workspace with its File Manage grant, finite
artifact budget and `workflow.mechanical-receipt.max-bytes` configuration before
SBT. There is no unreserved fallback. Preflight includes complete native
transition envelopes and actual declared selection/estimate metadata, using the
minimum of selected output ceilings and the unique fixed program's declared
stdout/stderr ceilings as conservative upper bounds without raising host limits.
Missing fixed ceilings retain the selected ceilings; malformed or ambiguous
declarations refuse preflight. These bounds do not claim exact effective CNCF
limits. The current factory default is 16 MiB; CNCF Configuration None means
1 MiB; the existing maximum remains 64 MiB. Missing artifact configuration is a
PRE-START failure. A sized locator is persisted before execution and actual
canonical capture is archived before the business result save, so a failed save
leaves a discoverable reservation. Artifact completeness does not imply complete
stdout/stderr: OutputLimitExceeded and truncation remain Incomplete, and an OS
progress exit 143 is not a native Exited(143). Captures preserve actual TestOnly
selection, estimate, mode, batch and outcome; the current ordinary fixed target
must still match. A foreign, changed or unreadable artifact is never absent.

The proof records caller terminal status and caller exit code separately from the
child Test termination and its terminal-event OS exit code. The event OS exit
code is not converted into a native SBT process exit code for an output-limit
termination. A failed caller with exit code 1 is an ended invocation,
not a successful check. Artifact content digests detect changed stored bytes;
they are not proof of execution, Human authorization or source authority. A
structurally valid complete capture containing undecodable text or NUL remains
stored and discoverable, but is not admitted as behavioral success or diagnostic
text. A reserved attempt whose SBT admission is confirmed rejected remains
waiting; this boundary supplies no automatic retry for it.

`not-retained` describes only the absence of a capture locator in this exact
native intent. It does not assert that no bytes exist in another provider. If a
caller knows of a genuine durable result in an unregistered provider, report the
unsupported provider or setup gap rather than attesting that its bytes are
absent. This recovery boundary does not discover or import unrelated providers.

## Repair-trend continuation

`describeDelegateTaskRepairContinuation` reads the exact retained target of an eligible active destination Task with remaining native repair allowance. A Human repair-trend stop offers `ContinueRepair` and `Defer`. A genuine known-ended `Incomplete` Test or TestOnly observation at Wait offers `RepairBeforeRetry` and `Defer`; its target has the explicit `recorded-incomplete.v1` source. The latter requires the correctly bound actual process/capture, successful selected-check prefix and no pending work, unresolved current effect or commit. A termination-unconfirmed observation and a validation-only Task do not qualify. Historical acknowledged UNKNOWN results remain in native history.

Prepare and independently admit the actual Human direction using the existing `DelegateTask` `repair-continuation` evidence purpose. `resumeDelegateTaskRepair` checks the exact target, source-specific choice, participant and evidence. ContinueRepair retains its original candidate freshness requirement. RepairBeforeRetry uses the common source reconstruction assessment: root, branch, repository and ownership must remain compatible, and unresolved merges remain blocked. Source movement within that boundary is recaptured by ordinary Advance. Neither direction converts Incomplete into Failed or a successful check.

Defer leaves the checkpoint untouched. An accepted repair direction appends only a normal Repair checkpoint, preserving the input, base, candidate, receipts, successful prefix, conditions, generation, repair control, counters and all historical directions. RepairBeforeRetry clears only the finished validation-recovery direction and capture reservation in this active successor projection; their original checkpoints and consumed authority remain historical. A separate ordinary `advanceDelegateTask` issues Repair with the available diagnostics. Only the normal accepted Repair completion increments its existing batch accounting. A changed candidate takes the ordinary selected checks; this continuation grants no reuse of successful checks across changed candidates. Repeating the exact admitted direction collects its existing result without another checkpoint or work issue. Changed, stale and foreign directions are refused. Legacy targets retain their original wire shape and ContinueRepair ancestry.

The explicitly selected `NativeDelegateTaskRecordedIncompleteRepairCliSpec` qualifies one physical isolated Session across installed versions. Set `smworkflow.spec.recordedIncompleteRepairPhase=prepare-old` with the OLD Component, then `resume-new` with the NEW Component; keep the same absolute ignored `smworkflow.spec.recordedIncompleteRepairFixture` directory. Both stages use the existing `smworkflow.spec.installedCommand` and `smworkflow.spec.componentFile` selections. The OLD stage runs real Compile, FixCheck and bounded TestOnly to retain an actual output-limit Incomplete. The NEW stage exercises Defer and independent Human repair admission, ordinary Repair/Submit, changed-candidate checks, independent review, local commit and the original snapshot/capture retention. Its authority and semantic judgments belong only to that specification-owned Session.

The description reports the retained generation, repair counts and limit, successful check prefix, receipt and observation counts; its opaque target carries the exact retained receipts and observations. Recurring check or test names indicate a coarse trend and do not establish that failures share a root cause. Description runs no checks.

## Requester reads of referenced validation captures

An external selected Test/TestOnly receipt remains a compact reference in the returned
result. The requester must read the complete canonical capture before it can
accept that receipt as a successful check. The requester host may declare
`sm-workflow.delegate-task.returned-capture-read-bindings` with schema
`sm-workflow.delegate-task-returned-capture-read.v1` and a `bindings` array.
Each binding specifies `sourceComponentInstance`, `sourceSessionId`,
`sourceRepository`, `sourceParticipant`, `sourceRoot`, `sourceBranch`,
`sourceBaseHead`, `sourceCandidateTree`, `sourceCommit`, `sourceWorkspace`,
`readWorkspace`, and `maximumBytes`. These are independently configured host
facts, not values inferred from the incoming receipt. The read workspace must
be separately granted read access to the original capture root; the finite
maximum cannot exceed the native capture ceiling. The original workspace and
reference remain unchanged in the stored result.

Receipt, adoption and later reopening require an exact, unambiguous binding and
an available File read. The Session, owner, selected check and candidate must
match, and the complete artifact must satisfy its size, digest, canonical
content and process-observation checks. Missing, changed, denied, foreign,
ambiguous, incomplete or oversized evidence is refused. Configuring a binding
does not itself establish successful validation or authorize a consumer effect.

### Semantic Human continuation

A destination stopped by an accepted Implement or Repair `decision-needed` result may describe its current target with `describeDelegateTaskSemanticContinuation`, using `DelegateTaskSemanticContinuationDescriptionInput` and receiving `DelegateTaskSemanticContinuationDescriptionResult`. The current `semanticReference` must select exactly one actual native issued work and completion, with matching Session, source revision, work kind and complete hydrated source state. The materialized Human must match that completion's `completedC` state. Earlier completions remain history and do not exclude a later stop. The target includes that original issue and completion, complete result and unresolved questions, native frontier and retained business input. Description only reads native state. Ordinary Advance must first materialize a submitted completion as Human.

Positive consistent generations and counters, remaining native automatic allowance, historical terminal failed checks, candidates, review history, repair trends without a current stop and earlier admitted directions are supported. A derived trend already covered by an actual earlier `ContinueRepair` admission may remain historical: its exact repair context, observations, generation, counts, candidate and receipts must match, and its persisted native admission must precede the current actual issued Repair source. An actual Repair issued after `RepairBeforeRetry` may likewise retain its known-ended Incomplete when that Repair returns `decision-needed`. Its exact original participant, input, candidate, selected checks, receipts, repair control, generation and counters must remain unchanged. The native before/admitted/source checkpoints are verified even when the trend stop is empty, with the finished recovery direction and reservation absent from the admitted successor. Metadata decoding preserves compact references; runtime admission reopens and validates the actual ended capture. This preserves the existing semantic choices without converting Incomplete to Failed or success. New or changed policy observations remain stopped. Pending work or checks, UNKNOWN or unterminated effects, unresolved current trend or budget stops, commit/reconciliation, validation recovery/capture, supersession, hygiene/records and terminal Sessions retain their own boundaries. They cannot be continued through a semantic direction.

The host prepares independently attributable Human evidence with `prepareEvidence`, family `DelegateTask` and kind `semantic-continuation`, using the exact description and `DelegateTaskSemanticContinuationInput`. The input supplies the same target, participant, Human reference, a closed choice (`ContinueAfterDependencyResolution` or `Defer`), rationale and nonempty actual upstream result references. Independently admitted evidence must cover this stop's questions and dependency resolution. An earlier direction or upstream result alone does not approve a later stop. Both description and resume accept exactly one `input` or `inputResource`; normal CNCF Resource authorization, UTF-8 reading and finite limits apply to large targets and directions.

`resumeDelegateTaskSemanticContinuation` observes typed Git root, branch, HEAD, index and status and uses `WorkflowManagementReconstruction.requireReconstructibleC` and `selectedC`. Logical source ownership and rename boundaries remain fixed. Source movement within that boundary selects ordinary fresh capture; hard contradictions and uncertain effects reject despite direction. Retained checks are historical facts, never manufactured successful evidence for changed source. Admission retains base, candidate, receipts, generation, counters, controls and accepted history, and appends only a native checkpoint at the authenticated original Implement or Repair kind. A separate ordinary Advance owns source recapture, successor work and subsequent candidate/check reconstruction. Resume launches no work, checks, Git mutation, commit or publication. Defer changes no workflow state.

The latest direction retains earlier directions through its exact target's business ancestry, with the same owner, Session, participant and input and strictly decreasing frontier revisions. Native history remains immutable. A genuinely new eligible Human stop receives a fresh target. Exact replay of any earlier admitted direction returns `already-resumed` without appending or clearing a newer stop, even while later work is pending. Changed, stale or crossed targets and changed historical answers refuse. Original initial targets remain decodable and collectible. The separately issued successor's ordinary context exposes the latest matching semantic Human reference and actual upstream references so its worker can consume that resolution.

Public semantic and repair-trend targets may carry compact external capture references. Decoding validates their metadata without interpreting the absent hydrated process fields as executed success or UNKNOWN. New description and admission still load the actual native source, reopen and validate the associated capture, and require full hydrated stop eligibility. Target correspondence compares the complete canonical typed public representation, so hydration alone does not change the target. A historical repair-trend direction additionally requires its actual native before/admitted/issued association and the hydrated original trend eligibility.

Executable qualification is in `DelegateTaskSemanticContinuationOperationSpec` and `WorkflowManagementReconstructionSpec`. The source scenario installs an archived fixture-owned failed receipt before real ContinueRepair admission and actual issued/completed Repair, exercising compact targets and nested historical trend metadata. The separately selected `NativeDelegateTaskRepeatedSemanticContinuationCliSpec` uses `smworkflow.spec.semanticContinuationPhase=before|after` and one absolute dedicated `smworkflow.spec.semanticContinuationFixture` path. Before publication it retains one actual later Repair semantic stop with inline failed-check facts and observes the old installed refusal. After normal publication it reopens that same Session without replacing those facts, admits a complete Human answer exceeding 128 KiB through Resource input and separately issues Repair. Known failed-check checkpoint facts are explicitly fixture-owned and do not claim an executed test.

The Lake owner must perform its own current-target description, independent Human evidence admission, and ordinary Advance against its original R2 after this capability is installed. Delivery of this capability does not itself resume or accept the Lake or Curator work.

### Review decision continuation

A destination owner can describe a materialized independent Review `decision-needed` Human frontier with `describeDelegateTaskReviewContinuation`. The description carries a compact typed target identifying the component owner, Session, frontier revision and original issued Review and result reference. Candidate, validation receipts, original issued work and completion remain in native storage. The native operation resolves and verifies those records and their full context; the caller’s target is not authority. Description does not progress the Session. The owner prepares independent Human evidence with `review-continuation`, naming that exact description, either `ReevaluateReview` or `Defer`, and attributable clarification references. `resumeDelegateTaskReviewContinuation` admits only the independently observed matching direction. Defer leaves the frontier unchanged.

Reevaluation retains the original Review and its result, the input, historical checks and repair counts. It appends a Review checkpoint with the original result, Human direction and clarification references. A separate ordinary Advance observes current source and reconstructs the candidate and checks when the retained candidate no longer applies, before issuing a fresh independent Review. The successor Review work contains the retained original completion and Human clarification plus an `originalWork` reference. The reviewer reads the exact original Review work and issued context through `WorkflowRetainedWorkService.readWorkflowRetainedWork`, using the public form of that reference. The new Review may accept or request same-purpose repair under the existing policy. This operation neither overrides the original verdict nor commits, publishes, or performs a consumer owner's Session work. Exact repeated admission collects the retained direction.
