# Delegated development and delivery

Date: 2026-10-27
Status: adopted target contract; implementation and installed qualification pending.
Owner: [Phase 6.8](../phase/phase-6.8.md), D1–D4.
Public skill: `sm-delegate-task` (planned, not yet installed by this document).

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
Concrete public Operation/DTO names are to be defined in CML during development,
not inferred from the skill name. Generate the normal ABI/help/resources there.

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
changed. Missing complete-input execution/profile mapping is refused before a
fresh WorkOrder. The runtime derives one stable native Session identity per
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
Delegate commits use the shared selected staging and mechanical hygiene policy;
required publication uses `LocalPublicationRuntime` and its retained actual
request/result. A legitimate unchanged candidate returns the applicable existing
commit. A publication wait retains the commit and successful checks.

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
