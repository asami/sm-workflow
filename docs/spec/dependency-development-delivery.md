# Dependency Development and Delivery

Date: 2026-10-26 (Asia/Tokyo)
Status: adopted planning contract; implementation and installed acceptance pending
Owner: [Phase 7](../phase/phase-7.md)
Acceptance: [dependency delivery checklist](../phase/phase-7-dependency-delivery-checklist.md)

## Phase 6.8 delivery predecessor — 2026-10-27

[sm-delegate-task](delegate-task.md) supplies an independent single-request
contract in Phase 6.8. It reuses the common execution engine without calling
sm-test-fix/goal-task as wrapper children or changing their validation/completion
contracts. Runtime owns verification scope, mandatory commit, required publication
and result return/adoption. Phase 7 adds modes, durable FIFO and workspace breadth.

## Delivery priority — 2026-11-11

The Phase order is **7 -> 8 -> 8.1 -> 9**.
Within [Phase 7](../phase/phase-7.md#parallel-development-first--2026-11-11),
qualify minimum managed parallel development first: prove two isolated A/B lanes,
then complete assigned-chat/FIFO and remaining overlay breadth. All original
delivery-mode, review, authority and acceptance contracts remain. Phase 8 owns
subsequent operational improvements; 8.1 contains the former 6.12 Scala-quality
plan. Neither is a Phase 7 entry gate.

FIFO serializes requests to the selected destination queue, not unrelated lanes.

### Current native route boundary — 2026-11-12

The scoped producer/consumer Operations support independent explicit source and
artifact resources. `Phase7ManagedDevelopmentSpec` additionally specifies two
physical producer/consumer pairs with separate private repositories, identical
ordinary coordinates and request names, and different selected development
versions. Its scenario retains A's failed compile, executes B's publication and
compile, then reconnects A to its existing native records and exact selection.
These are authored executable specifications; this source update supplies no
execution or acceptance receipt.

The resumed P7-C investigation distinguishes supported caller dispatch from an
automatic managed dependency continuation. The native public Request already
contains its unchanged issued context, result type, completion Operation,
continuation and expected revision. The selected caller can dispatch that
bounded semantic assignment to a dedicated worker and receive its completion in
the live parent. Neither a direct runtime-to-Codex RPC nor another coordinator is
needed for that supported handoff. `NativeWorkflowResultInput` and
`GoalPhaseNativeWorkContinuation` own ordinary result admission and continuation
completion. A worker's final answer is still only semantic input, not a B delivery
accepted after validation, independent review, commit and publication.

P7-C now composes the native selected-source RepositorySync and bounded
DelegateTask owners. The optional `dependencyWorkEdge` in the same managed
resource declaration selects B's source for a destination Task. Its public
`sourceReferences` includes exactly one real pending GoalPhase A WorkOrder
identity and expected revision. B's existing native checkpoint retains A's
Component instance, participant, Session, Plan, Step, generation and complete
edge. A remains suspended; its semantic result is never synthesized to Start B.

The CNCF owners already provide `WorkflowProtocolV1.resumePersistedWorkOrderC`,
`resumeIssuedWithInstanceGuardC`, `ContinuationRuntime.resumeWithProgramC` and
managed atomic completion. These are real native Session continuation APIs;
Codex Goal UI status is not their prerequisite. This composition preserves
ordinary managed completion instead of invoking a loose resume with an invented
A semantic result. `JobUnitOfWorkSubmission` is the Job owner's private staged
post-commit activation mechanism. Its presence does not itself register an
external semantic worker's terminal event with a native business Operation.

The existing live parent can dispatch B's actual issued Request and submit the
real result through native Submit/Advance. The source changes do not install an
external worker-terminal-event subscriber or ended-turn chat scheduler. That
remaining adapter question is separate from the native composition below and
from the existing CNCF continuation APIs. No new upstream Git/SBT DSL is required
by this composition. All executable specifications in this update are authored
source; validation, independent review and integrated qualification are pending.
Managed B source/artifact selection and matching A resumption remain runtime-owned.
Synchronize only that selected B worktree with its configured GitHub branch;
unrelated/canonical B source changes and cleanliness are not inputs. Ordinary
main integration retains its separate local-first route. This schedule changes
no current Session, mode, authority, accepted work, history or repair count.

### Selected-source RepositorySync connection — 2026-11-12

The optional `repositorySyncEdge` field in the existing
`sm-workflow.managed-development` declaration selects one of that declaration's
dependency edges. There is no separate workspace profile. A normal
`RepositorySyncService.startRepositorySync` request uses the selected edge's
`provider.repository` as `repositoryIdentity` and explicit `FULL_STATE` intent.
`StandardWorkflowRuntime` resolves the edge's `provider.workspace` against the
CNCF process plan, requires its exact configured Git repository identity and
reuses the normal native single-repository assembly. `localJar` and `localProject`
do not redirect source synchronization. Missing/ambiguous/crossed configuration
fails before Git effects, without another checkout lookup or producer build.

For a configured remote `main`, explicitly set
`sm-workflow.repository-sync.main-integration=direct` in that development scope.
An absent value or `local-first` conflicts with the selected managed route and
fails before execution. A non-main destination retains its configured branch.
Without `repositorySyncEdge`, the ordinary resolver keeps its existing behavior;
this change does not implement Phase 8 local-first integration. Requests for
unrelated repository identities retain their own ordinary resolution.

Advance resolves the original native Session root and rejects a changed edge
which would move that Session to another B checkout. Existing native Git grants,
participant admission, merge validation/review, convergence and actual impact
selection remain the owners. Clean equal/push/fast-forward handling does not
introduce precautionary product checks. Source synchronization neither publishes
B nor activates an artifact in A. The registered `Phase7ManagedDevelopmentSpec`
authors resolver failure cases and a public native Start/Advance/reconnect case
with an unrelated unusable canonical checkout and no SBT programs; no execution
receipt is provided by this source assignment.

### Native bounded delivery and matching A — 2026-11-12

`dependencyWorkEdge` is independent of `repositorySyncEdge` and uses the same
explicit resource graph. Only the matching destination repository resolves to
its provider workspace; requester and unrelated bounded entries retain their
ordinary owners. Native continuation resolves the original retained B root and
branch before effects. Configuration cannot move a pending/completed bounded
Session to a different checkout. Missing or crossed provider metadata fails.

For managed destination work, `publicationTarget` explicitly names the selected
edge and `artifactCoordinates` names its exact development version. The native
B request identity is the immutable publication identity; it must satisfy the
existing scoped operation identity constraints. The version is the existing
`ManagedDevelopment.version(original, scope, requestIdentity)` mapping. This
route calls the existing scoped producer operation after B validation, review
and local commit; it never falls through to ordinary shared `publishLocal`.
The returned scoped publication must identify B's accepted commit and committed
tree, with empty native dirty-path and dirty-content observations. The runtime
checks this before publishing and when admitting the retained publication;
stable post-commit modified or untracked source is not accepted delivery source.
The existing
`DelegateTask.Delivery` retains the publication and original A capture. Ordinary
non-managed delegated publication retains its prior behavior.

The public B `sourceReferences` supplies A's actual continuation identity and
expected revision. Native storage checks the real A WorkOrder, current active
suspension, Component instance, participant, Plan, Step, generation and consumer
repository and the actual retained Start root against the edge's explicitly
configured consumer workspace root. Sharing a repository identity does not make
two consumer worktrees interchangeable. The typed capture lives in B's existing native bounded checkpoint,
not a new lifecycle ledger. Missing, stale or crossed A references fail before
B's native Start record is created. Existing native A approval and effect gates
remain in force; a reference is never effect authorization.

Once the caller has recorded actual B dispatch, requester `advanceDelegateTask`
loads B's same-store native checkpoint. A pending B WorkOrder, failure or
Human/decision frontier remains a wait without invoking B, resetting either
repair count or blocking a different Session. A native `ReturnReady`/`Returned`
checkpoint is admitted through the existing `DelegateTask.returnedC` and
`observeC` rules, using its exact immutable native locator. An explicit defer
stops automatic collection; ordinary explicit delivery/adoption direction stays
with its existing owner. Native force closure remains the stop boundary.

A scoped local worktree requester collects new results through this Advance.
To continue an older `ResultReceived` that retained a file URI as its receipt,
call `observeDelegateTaskDelivery` with event `bind-local-result`, the same
requester Session, the observed destination Session, and the destination
snapshot's exact checkpoint identity and revision as `sourceReference`. Include
a rationale. The runtime opens that retained checkpoint in the same store and
compares its business result with the saved receipt. It appends the binding
while keeping the old observation, then ordinary description, adoption and
Advance can use the original destination evidence.

Managed adoption retains the existing independently admitted host evidence
requirement and remains `result-and-selection-attachment-only`. It performs no
consumer selection, compilation or A completion. The caller explicitly invokes
the existing scoped artifact Operations. A managed requester remains natively
Active after this attachment because its integration observations and original A
handoff remain pending. Advance retains changed observations through the existing
native checkpoint owner before projecting their state; unchanged observations do
not append history. Ordinary nonmanaged Adopted delivery remains terminal.
Historical managed instances already marked Completed are preserved and refused
by this integration route; Advance does not reopen or rewrite them.
The caller explicitly invokes
`selectDevelopmentArtifact` and `compileDevelopmentConsumer` through their
existing scoped artifact owner. Use B's request identity for `publication` and
the requester's request identity for `requestIdentity` and `selection`.

After adoption, requester `advanceDelegateTask` rereads B's actual native
checkpoint and the exact retained scoped consumer result. The publication,
development coordinates and selection reference must match the original edge
and native B output. The exact selection reference is
`ManagedDevelopment.key(definition, edge, "selection", requesterRequestIdentity)`
at revision `1`. Missing, failed, unverified or unknown consumer results return
`Wait` without executing a build. Compilation records the consumer's actual
native Git source and dirty-file content digests before/after execution; a
changed source cannot produce verified evidence. Advance rejects verification
that no longer matches that consumer source, as well as stale/crossed A work.

Successful Advance returns `kind=Request`, the original A public work record,
unchanged `issuedWorkContext` and expected revision, A's `submitSessionId`,
`submitGoalPhaseWorkResult`, and `GoalPhaseWorkResult`. The existing live caller
dispatches that exact Request and submits A's real semantic result/evidence to
the normal managed completion owner. This is an executable typed caller handoff,
not just a continuation locator or instruction message. The B requester state
and consumer evidence remain separately attributable; rereading the handoff
does not create another A Start or consume A's WorkOrder. Deferred requests and
failed B work do not expose A as ready. The composition requires no autonomous
ended-turn scheduler or Codex Goal UI reactivation.

Before managed B Start or Advance, all actual ordinal SBT validation aliases
must uniquely bind the selected B worktree root and required operation. Canonical
or A aliases cannot certify B. Missing coverage fails before execution; native
SBT admission continues to own executable, selectors, grants and termination.
All 44 original Phase conditions, existing histories, accepted results and repair
counts remain. Complete two-lane native delivery
and nine-case impact-selected workspace acceptance still require execution and
independent review; the authored source is not that evidence.

## Three configured modes

### Phase policy and scoped artifacts — 2026-11-10

Consume [Phase development permissions](phase-development-permissions.md).
Projects already incorporated into managed development scope need no additional
dependency-repair Human decision. For external repair and shared CLI update,
independent Phase properties default to false; admitted initial true or genuine
same-Phase Human enablement permits further in-purpose work without repeated
permission questions. Actual explicit Fixed/exclusion choices remain restrictions;
do not silently reinterpret configured development ownership.

Phase 7 dependency JARs are published/selected only within the development scope
through existing artifact repositories, consumer bindings and overlay/selection.
Ordinary shared local publication remains unchanged. This replaces the earlier
unscoped publishLocal shortcut and applies to all delivery modes below. Shared
CLI replacement, remote publication and push remain separate effects.

Select a mode per participating dependency Component, rather than deriving it
from a repository path, dirty status or availability of a chat.

| Mode | When dependency development is necessary | Delivery boundary |
| --- | --- | --- |
| Fixed | Return a structured dependency-change-required error with reason and affected consumer work; await user direction | No automatic edit, delegation, publication or source switch |
| Assigned development chat | Enqueue an authorized outcome request to the configured development chat; service its queue in FIFO order | Required development acceptance, local commit and successful scope-private publication; return usable result |
| Development subtree | Use an explicitly selected development area with retained source/repository/branch and owner | Develop, validate and commit there, then register the result as synchronization work |

Fixed-mode errors are unresolved dependency requirements, not evidence of repair
divergence or a failed test. Only affected consumer work waits. Existing results
remain valid in their original scope. Changing mode requires applicable user
direction through the supported admission route.

A development subtree means a managed dependency development area here. It does
not mandate the Git subtree merge command or silently enable unmanaged worktree
use. Resolve the concrete checkout/worktree/source integration through CNCF
Component resources and Phase 7 workspace/artifact contracts. Keep the canonical
source and intended synchronization destination explicit. Isolated development
must not implicitly publish into the ordinary cross-chat delivery route.

## Managed dependency synchronization isolation — 2026-11-09

Use two synchronization routes for their distinct purposes:

| Selected purpose | Source integration and remote route |
| --- | --- |
| Ordinary integration into main | Working checkout -> canonical local main checkout -> selected GitHub main, using the normal `local-first` default. |
| Phase 7 managed development of dependency B for A | A's selected B worktree <-> explicitly configured B GitHub branch, using direct synchronization without another local checkout. |

The managed dependency route preserves parallel development independence.
Its completion synchronization includes only that registered B checkout and
its explicit remote/branch. It must not gather other machine-local worktrees,
merge their unpublished branches or dirty content, update their local main
checkout/branch, or require those worktrees to be clean before B can synchronize.
This includes B's separate canonical development checkout. A's root route keeps
its own declared purpose and target; B's isolation does not silently retarget A.

Bind this route explicitly to the existing dependency-edge/request configuration
when preparing the selected synchronization. If B targets remote main, select
`sm-workflow.repository-sync.main-integration=direct` for that dependency's
synchronization context through the normal configuration/input route. Do not
change the machine-wide or ordinary project default. For a non-main target,
retain the configured tracking branch; the main-integration property does not
redirect it. See the [main integration contract](repository-sync-local-main-integration.md#phase-7-managed-dependency-route).

Fetch and reconcile the selected GitHub branch normally, with applicable checks,
review and permitted non-force push. Remote conflicts and genuinely shared Git
resource conflicts keep their existing owners; isolation is not permission to
overwrite remote work or update a branch underneath another checked-out worktree.
Normal shared Git object/remote-tracking bookkeeping is not synchronization of
another worktree's source. Do not add a machine-wide lock or a new checkout
isolation mechanism to enforce this planning rule.

Retain the selected route, checkout, remote/branch and actual results in the
existing Session. Do not fall back to local-main integration on failure or
retarget a running Session because configuration changed. A later explicitly
requested normal integration may consolidate B into its canonical checkout; it
is separate work, not an automatic dependency-completion effect. Existing
best-effort synchronization and distinct artifact-publication/adoption outcomes
remain unchanged. This is a planned contract, not current runtime qualification
or authority to change present project ownership.

## Request and completion contract

Retain one request identity and its requester Task/Session, destination chat and
Component/repository, objective, sources, acceptance conditions, allowed operations,
authorization references, selected execution entry and result-return destination.
Use existing typed WorkOrder/Continuation and storage; no skill-owned queue ledger.

| Request | Destination execution | Required delivery | Post-delivery synchronization |
| --- | --- | --- | --- |
| Small defect | Independent delegation Workflow through sm-delegate-task; shared repair engine | Impact-selected focused/FULL validation, required review, local commit, scope-private publication | Only when selected by the request |
| Larger ad hoc development | Independent delegation Workflow through sm-delegate-task; shared development engine | Delegated acceptance and required integrated review, local commit, scope-private publication | Best-effort sm-repository-sync |
| Explicit Phase development | sm-goal-phase | Actual Phase completion, local commits, scope-private publication | Best-effort sm-repository-sync |

Size is selected from the requested outcome, not an automatic file-count threshold.
Standalone fix keeps its own no-implicit-commit contract and goal-task keeps its
own test/review/commit contract. Neither is extended by this delegation route.
Delegate completion requires a local commit (or applicable existing commit for
no-change), with scope-private artifact publication required for dependency delivery. Task/Phase local completion
and delivery publication remain distinct recorded outcomes. The initial request
carries the necessary authorization, so ordinary in-scope commit/publication
does not create another permission question. Preserve actual human restrictions
and runtime effect admission; test success does not itself grant authority.

Use existing runtime-owned SBT and development-coordinate/version rules with a
scope-private publication destination. Reuse the
[overlay contract](development-artifact-overlay.md) and artifact repository/selection
for assigned-chat delivery as well as managed subtrees. Require the minimal
isolated publication/resolution route first, not all overlay breadth. No manual
JAR copy, resolution-internal edit or fallback to shared publishLocal when isolation
is unavailable. Local delivery does not imply remote/public release publication.

## Runtime-owned FIFO

Each configured development chat has a durable FIFO of admitted requests and
handles one active request at a time. Record enqueue order through the existing
runtime owner; the chat's conversation memory does not own ordering.
Different queues may progress independently, subject to actual resource ownership;
no machine-wide SBT lock is introduced.

A message acknowledges transport, not enqueue acceptance or execution. Correlate
the request, actual destination execution and returned result. Redelivery or
caller reconnection retrieves the retained request/result and does not enqueue
or start the same work again. Reuse existing continuation/effect records rather
than adding a second replay/receipt framework.

An active request awaiting input remains explicitly waiting. Default FIFO does
not silently skip it. An authorized hold/cancel releases its place explicitly
and retains history; resumption/requeue order is recorded. Avoid circular waiting:
a destination must not synchronously queue prerequisite work behind its own
active request. Surface the dependency cycle for a bounded plan correction.
No AI polling, progress-only messages or timer-driven semantic turns manage this
queue. Use existing events and ordinary result collection.
When dispatch needs the chat application's tools, return the typed dispatch work
to the existing authorized caller and record its actual result. Runtime queue
ownership does not require a new Codex API session or loss of chat context.

## Commit, scoped publication and consumer adoption

Normal delivery is development acceptance -> actual local commit -> scope-private publication
-> result return -> consumer artifact adoption and affected integration validation.
For a legitimate no-change outcome, return the applicable existing commit rather
than manufacturing an empty one, and establish the required publication result.

Return actual changed scope, commit reference, artifact coordinates/version and
publication outputs, validation/review evidence, unresolved findings and consumption
instructions. Record development, commit, local publication, synchronization and
consumer adoption separately. Source edits or commit success alone do not mean
the dependency is delivered. A publication failure preserves completed development
and commit; resume only the missing operation through supported recovery.
Unknown effects require observation/settlement before any retry.

When the assigned development chat reports successful completion and required
local publication, use the [requester continuation](native-delegate-task-caller.md#transport-and-adoption).
Resume the existing consumer work with normal dependency resolution. Diagnose
publication or artifact selection if that work encounters a failure.

Managed worktree delivery retains the existing scoped publication and selection.
If a later scoped publication replaces the same development coordinates, resolve
the actual selection through the runtime's existing publication resources.

Producer validation is reusable evidence about that producer result. The consumer
owns affected generation/compilation/integration checks and formal acceptance
in its unchanged Session. Do not repeat unrelated producer tests or equate
publication with consumer acceptance.

## Best-effort synchronization after larger development

Once development and scope-private publication succeed, expose the usable local result and
attempt the selected sm-repository-sync under existing synchronization authority.
Schedule the existing completion hook after required publication in this composed
delivery; do not run it both at Phase closure and after publication.
Reuse the established post-completion policy; do not add another synchronization
attempt if the underlying Phase already supplied the applicable result.
Record succeeded, failed, deferred/waiting or unknown honestly.

A synchronization failure or unresolved Human/conflict condition does not revoke
development completion, scoped publication success or consumer usability. It does not
hold local delivery or the development FIFO indefinitely. Retain the outstanding
sync reference for separate continuation; do not spin/retry or force push.
A still-running sync retains its actual process/resource ownership and must not
be raced by new conflicting work. Return/update its outcome through existing
collection, without delaying local result availability. If synchronization changes
source, any newly published artifact is a separate attributed delivery.

## Phase responsibility and preservation

[Phase 6.7](../phase/phase-6.7.md) provides one Task/fix execution, conditional
inner review, integrated Task review/commit and the initial existing-chat handoff.
Phase 6.8 supplies independent delegated execution and mandatory commit/selected
publication/return/adoption. Phase 7 configures the three modes, FIFO and related-
project adoption/synchronization using that qualified route. Phase 7 also reuses existing GoalPhase;
it does not reimplement Phase development in 6.7.

[Phase 17](../phase/phase-17.md) retains optional Task/FIX source, selection and
composition extensions; these do not block the first queue/delivery route.
Reuse shared authorization/convergence, Evidence/Journal and typed Git/SBT.
Preserve active 6.6/6.7 and other Sessions, bases, accepted work, history, counts
and all successful/failed/unknown evidence. No new Session wrapping for each
existing Phase repair, private storage edit, counter reset or accepted Submit replay.

## P7-DD source implementation — 2026-11-12

The optional `delivery` member of the existing `sm-workflow.managed-development`
configuration is an array of `{ "edge": "<declared edge>", "mode": "Fixed" }`,
`{ "edge": "<declared edge>", "mode": "AssignedChat", "chat": "<actual configured destination identity>" }`,
or `{ "edge": "<declared edge>", "mode": "DevelopmentSubtree" }`.
`dependencyWorkEdge` selects the applicable entry. Unknown modes/edges, duplicate
entries, a missing AssignedChat identity, or a chat on another mode are invalid.
Historical configurations without `delivery` retain their existing managed subtree
route. Existing Sessions retain the mode captured at their original Start; later
configuration does not move them to a new queue or remove queue exclusion.

### Parent JAR consumption

The delivery owner and parent build connection are independent. Recommend a
project-private local JAR. The existing `localJar=true` route publishes scoped
development coordinates and verifies the parent's explicit scoped selection.
The parent enables its applicable sbt-cozy overlay once. Subsequent publication
and selection use the existing runtime Operations.

A developer may explicitly select a machine-common local JAR by setting the
edge's `machineLocalJar` with `publicationTarget` and `consumerJar`.
`publicationTarget` names the provider's existing ordinary local publication
registration. `consumerJar` is the absolute expected Ivy local JAR path on
the parent's ordinary classpath for the original coordinates. This edge uses
`localJar=false` and has no source-project or private batch selection. The
parent's normal dependency declaration and local resolver supply the JAR.
The host registers ordinary provider `publish-local`, parent TestCompile
with classpath discovery, and a CNCF File workspace with read access rooted at
the expected JAR directory. No parent build plugin is needed for this route.

The bounded provider records the actual normal publication result, verifies the
accepted local source after execution and retains the SHA-256 of the JAR reported
by the publisher. The parent attaches the receipt and original coordinates
through ordinary requester adoption. For this mode the selection reference
identifies the actual LocalPublication request at revision `1`. The following
ordinary Advance retains the parent compile and its observed test classpath and
recollects the original WorkOrder once the expected JAR's contents match. Recording a
delivery or adopting its receipt does not prove parent use. A machine-common
SNAPSHOT can be replaced by another local publication. The runtime checks the
digest around compilation and before reusing the saved success. Verification
describes the observed classpath and contents, without reserving the shared JAR.

Existing issued requests retain their captured edge and published effects.
Missing private setup leaves the private route unavailable. The separate
provider-Phase delivery retains its existing scoped route. Phase 7.1 owns the
deferred source-project integration.

### Selecting a route from GoalPhase

A fresh implementation or repair result can request managed delivery through its
typed dependency proposal. The proposal identifies the concrete provider and
consumer and explicitly permits source edits and local publication. The ordinary
GoalPhase Human question retains the proposal and exposes optional
`dependencyDelivery` with both alternatives:

- `DevelopmentSubtree` is labeled **Local worktree** and is the display default
  when available.
- `AssignedChat` is labeled **Assigned development chat**. It needs a matching
  existing chat registration on the same dependency edge.

Both alternatives require the registered provider worktree and branch and the
prerequisites for the selected JAR use. An unavailable alternative explains its
missing or disabled prerequisite. If no active managed edge matches the
proposal, both alternatives report the missing repository registration and the
chat alternative also reports the missing assigned chat. Fixed dependencies
offer neither route. Phase policy permission to repair does not select a
delivery route. The question also allows rejection, deferral and cancellation. Untyped requests,
scope-only decisions, previously issued questions and previously admitted
delivery routes retain their ordinary behavior.

The actual Human answer selects the route by its choice ID. Fresh submission
checks the selected route against the saved offer and current registration.
Adding a registration leaves the issued question unchanged. Use
`RejectDependencyScope` on that question, continue the same Session through
ordinary Advance, and submit a new typed proposal from the reissued WorkOrder to
obtain an offer from the updated registration. The original question and result
remain in history. Accepted history is read from the saved question and
answer. The reissued WorkOrder exposes the exact `deliveryRoute` and actual
answer reference in its decision context. The same-subject inventory receipt
preserves that route while identifying the effective approved inventory.

The existing requester Start reads the pending WorkOrder and retains the selected
binding for ordinary dispatch. The bounded destination Start reads that exact pending WorkOrder and
checks the original owner, current registration, source root and branch,
workspace and host prerequisites for the selected JAR route, approved paths and
actual answer reference.
Local worktree uses `managed-worktree:<provider workspace>` as the destination.
Assigned development chat uses the exact registered identity and existing FIFO.
Reconnection uses the original saved Start binding. Either route still requires
the existing independent Start selection and normal execution, validation,
review, commit and the selected publication authority. Selecting a route performs no
dispatch, installation, publication or repository synchronization.

Ordinary destination scope correction uses the approved GoalPhase inventory.
For an unclaimed implementation issued to the selected local worktree using
a project-private JAR, call
`describeDelegateTaskScopeCorrection` with the same destination Session,
the current `oldContinuationId`, the exact `additionalOwnedPaths`, and
`supplementManagedInventory: true` to propose an addition to that inventory.
The description binds the current parent WorkOrder, its original Human
approval and purpose, and the literal supplemental paths. The parent
WorkOrder, Step, managed Component and selected delivery must still match.
The original exclusions, any explicit exact-path restriction, and the
destination's registered ownership apply.

Use `prepareDelegateTaskScopeCorrection` and the normal independently recorded
Human selection for that exact description. Then call `correctDelegateTaskScope`
with the selected target and reference, using `inputResource` if necessary.
The correction retains this approval with the same destination Session. Normal
`advanceDelegateTask` issues the amended implementation. The original parent
WorkOrder, route and approval remain in history, as do the original destination
issue, accepted results and repair accounting. A stale or crossed association
requires a current description and genuine selection for that target. Timing-only
corrections use the ordinary bounded contract.

This route uses the registered local JAR publication program even when the edge
also declares a source project. Source-project build integration and separate
effects remain with their existing owners.

Fixed requester Start retains the existing consumer/delegation input and returns
`DependencyChangeRequired` on Advance with the affected Component and objective.
No dispatch is exposed. A Fixed destination refuses execution. Phase booleans do
not override Fixed. Direction to change the mode stays with its existing owner;
a failed dependency check or transport acknowledgement cannot change it.

AssignedChat destination Start, after ordinary complete-input/participant/owner
admission, enqueues its immutable native Session/request in a chat aggregate in
the existing canonical Workflow store. The aggregate uses the same native
expected-revision mechanical transition owner. One chat has one active request;
other chats and managed subtree lanes remain independent. Start/admission is
separate from transport `dispatch-observed`, and the aggregate is not a second
executor, repair counter or artifact ledger. A concurrent queue revision conflict
returns a conflict without an execution permit. Reconnection observes/reuses the
original request; it cannot silently adopt a changed body or another Session.

Advance claims the first eligible request before issuing implementation. An
active request in Human/wait retains its place. On the first claim the runtime
records the actual current clean provider base, allowing an already queued request
to follow a predecessor's accepted commit without capturing that predecessor's
uncommitted work. Accepted local commit plus the required publication and
post-publication acceptance release the place at `ReturnReady`; transport return,
consumer adoption and separate synchronization do not retain the queue place.
Failed/unverified publication stays pending, preserving the existing accepted
commit/checks and the publication owner's replay protections.

The original `DelegateTaskContract.permittedOperations` may explicitly include
`queue-hold`, `queue-cancel` and `queue-resume`. The existing delivery observation
Operation accepts those events only for the actual destination Session, original
control authorization reference, current participant and a safe Implement/Human/
ReturnReady/Returned frontier without a pending semantic WorkOrder. A prior intent
with unknown effects cannot release its place through this route. Hold removes
its place explicitly; resume appends a held request at the end with a native
event. Cancellation retains the entry/history and cannot become successful
delivery or resume. A declared `prerequisiteFor` in the delegation contract names
an unfinished request whose prerequisite is being requested; enqueuing it to the
same chat refuses with `dependency-cycle`, rather than waiting behind itself.

The captured `Phase7ManagedDevelopmentSpec` includes the new business examples
and public native-operation AssignedChat and Phase-policy scenarios. They are
authored source, not execution evidence. Real app dispatch/return observations
remain the live authorized caller's responsibility; neither chat identity text
nor these fixture actors establish external-chat authority. The existing native
bounded delivery and matching A handoff remain the supported connected route.
A declared `providerPhase` in `DelegateTaskContract` supplies the existing native
`sessionId`, exact original `startRevision`, immutable `publication` and
`producerSelection`. There is no bounded destination Start for this mode. The
provider continues its original GoalPhase; requester Advance passively reads its
canonical accepted local or strict terminal through those owners' readers. A
pending/failed/decision-needed Phase remains a wait. A force-closed, crossed or
unaccepted result cannot become delivery. The typed Phase delivery retains its
actual accepted Plan/prefix/review, source revision/root/branch, publication and
original A capture; it is never encoded as a fictitious bounded `S.State`.

The provider owner explicitly invokes `publishDevelopmentArtifact` with the same
`providerPhase` locator in `ManagedDevelopmentInput`; required publication never
runs in requester collection. Preflight verifies native Phase completion and its
actual clean provider commit/tree before a fresh scoped publication. Reconnection
reads the existing immutable result without building again, including after
subsequent synchronization changes the provider source. Consumer adoption and
explicit integration use the same exact publication and original A handoff as
bounded delivery.

`DelegateTaskContract.synchronization`, when selected, supplies the existing
RepositorySync business target and requires the original `repository-sync`
operation grant. Bounded delivery attempts it only after actual successful scoped
publication. The normal owner still admits the selected source, remote/branch,
execution and push permissions; the data field grants none. Managed main requires
the existing scoped `main-integration=direct` property and explicit
`repositorySyncEdge`. An absent/incompatible binding records a deferred outcome,
leaving local delivery usable.

For a provider Phase, a newly selected original Plan may explicitly declare
`deliverySynchronization: "after-scoped-publication"` under the existing local
completion policy. Its native local closure records that declaration without
starting an early child. Strict synchronization is never silently deferred, and
ordinary Plan permission adoption cannot change this sequencing. The explicit
provider publication input may select the synchronization target after the
artifact succeeds. If the Phase already has a native child/result, that exact
actual outcome/reference is reused instead of starting a second child. Neither a
locator nor source artifact success supplies new push authority.

The artifact owner's existing immutable native Component records reserve one
sync child and retain its actual outcome. Start/Advance is attempted once through
the existing public RepositorySync owner. Unknown intent, failure, defer and
Human/request outcomes preserve local usability and the original child link;
redelivery never retries them. Further child work uses its own ordinary public
continuation. Source synchronization that changes the checkout does not rewrite
or republish the already attributed artifact.

The selected suite includes authored native provider-Phase pending/refusal and
nonblocking post-publication deferred-sync cases in addition to the assigned-chat
and Phase-policy cases. Full successful provider-Phase delivery and remote-sync
integration still require the owning runtime's actual checks and independent
review. These source/specifications make no actual dispatch, publication,
synchronization or Phase acceptance claim.


### Assigned-chat repair contract — 2026-11-16

A real managed provider GoalPhase Start now captures its configured delivery
binding and admits its own immutable native Start payload/Session to the same
per-chat FIFO as bounded destinations. It retains the GoalPhase execution owner,
Plan, repair history and original Start reference. Advance obtains the queue
permit before issuing work or executing further native Phase effects; a queued
Phase returns Wait without changing its Session, pending work or repair counts.
Native execution-source reads and semantic completion require the active permit.
Other chats remain independent. Requester Phase locators must match that actual
Start and its captured assigned-chat binding. Historical Starts without that
capture are not silently migrated by a new request's configuration.

The declared Phase publication/selection locator is frozen in that same queue
entry. Only the actual accepted Phase source plus its verified immutable scoped
publication can release its place. Pending, failed or unknown publication leaves
the active place retained. Reconnection can complete an interrupted release by
reading the already retained publication, without rebuilding or completing the
Phase again. Requester attachment and consumer adoption remain separate. No
bounded provider Session, accepted Phase result or new effect grant is invented.

For `observeDelegateTaskDelivery`, ordinary transport/adoption observations keep
`commandIdentity` absent. Each `queue-hold`, `queue-cancel` or `queue-resume`
command must supply a distinct nonempty `commandIdentity` as well as its original
operation/authorization grant and exact destination Session. The native chat
aggregate retains the complete command body and applied status/order. Replaying
an old identity/body returns retained evidence without reapplying its transition
over newer direction. Reusing an identity with another event, grant, Session or
body refuses. A new command may reuse the same valid grant. Historical records
remain readable without manufactured command identities; new controls cannot
omit the identity.

A redelivered bounded Start supplies the original actual `sessionId`. The owning
Start route authenticates the existing definition, idempotency key, participant
and complete input before composing the source/mode. It recovers the original
root, branch and captured binding before mode admission. Current configuration
applies only to a genuinely new request, so changing AssignedChat to Fixed or to
a different chat cannot silently reject, move or re-enqueue the original request.
Force closure, changed bodies/owners and crossed physical sources still refuse.

The authored Phase7 regression cases cover mixed native bounded/Phase FIFO in
both admission orders, two genuine provider Phases, queued history preservation,
unfinished-publication exclusion, immutable control-command replay, and retained
Start replay after configuration changes. These are source specifications; their
runtime validation and independent review are separate evidence. Full successful
provider-Phase publication/integration remains subject to its owning native
qualification and the original Phase acceptance conditions.
