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

## Three configured modes

Select a mode per participating dependency Component, rather than deriving it
from a repository path, dirty status or availability of a chat.

| Mode | When dependency development is necessary | Delivery boundary |
| --- | --- | --- |
| Fixed | Return a structured dependency-change-required error with reason and affected consumer work; await user direction | No automatic edit, delegation, publication or source switch |
| Assigned development chat | Enqueue an authorized outcome request to the configured development chat; service its queue in FIFO order | Required development acceptance, local commit and successful publishLocal; return usable result |
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
| Small defect | Independent delegation Workflow through sm-delegate-task; shared repair engine | Impact-selected focused/FULL validation, required review, local commit, publishLocal | Only when selected by the request |
| Larger ad hoc development | Independent delegation Workflow through sm-delegate-task; shared development engine | Delegated acceptance and required integrated review, local commit, publishLocal | Best-effort sm-repository-sync |
| Explicit Phase development | sm-goal-phase | Actual Phase completion, local commits, publishLocal | Best-effort sm-repository-sync |

Size is selected from the requested outcome, not an automatic file-count threshold.
Standalone fix keeps its own no-implicit-commit contract and goal-task keeps its
own test/review/commit contract. Neither is extended by this delegation route.
Delegate completion requires a local commit (or applicable existing commit for
no-change), with publishLocal additionally required for dependency delivery. Task/Phase local completion
and delivery publication remain distinct recorded outcomes. The initial request
carries the necessary authorization, so ordinary in-scope commit/publication
does not create another permission question. Preserve actual human restrictions
and runtime effect admission; test success does not itself grant authority.

Use ordinary publishLocal through the existing runtime-owned SBT path, with
normal development-coordinate/version rules. This mode does not require an
artifact overlay, manual JAR copying or edits to dependency-resolution internals.
The [overlay contract](development-artifact-overlay.md) applies to explicitly
isolated development selections, not as a prerequisite for assigned-chat delivery.
Remote/public release publication is not implied by publishLocal.

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

## Commit, publishLocal and consumer adoption

Normal delivery is development acceptance -> actual local commit -> publishLocal
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

The consumer uses the reported published result, not whichever development build
happens to be newest. If later publishLocal replaces the same development
coordinates before adoption, preserve attribution and resolve the actually selected
artifact through existing runtime publication/selection resources. Do not silently
attribute a different artifact to the earlier successful delivery. Keep this
necessary connection inside the runtime rather than adding per-read hash gates
or AI filesystem checks.

Producer validation is reusable evidence about that producer result. The consumer
owns affected generation/compilation/integration checks and formal acceptance
in its unchanged Session. Do not repeat unrelated producer tests or equate
publication with consumer acceptance.

## Best-effort synchronization after larger development

Once development and publishLocal succeed, expose the usable local result and
attempt the selected sm-repository-sync under existing synchronization authority.
Schedule the existing completion hook after required publication in this composed
delivery; do not run it both at Phase closure and after publication.
Reuse the established post-completion policy; do not add another synchronization
attempt if the underlying Phase already supplied the applicable result.
Record succeeded, failed, deferred/waiting or unknown honestly.

A synchronization failure or unresolved Human/conflict condition does not revoke
development completion, publishLocal success or consumer usability. It does not
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
