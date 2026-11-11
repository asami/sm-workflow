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
Managed B source/artifact selection and matching A resumption remain runtime-owned.
Synchronize only that selected B worktree with its configured GitHub branch;
unrelated/canonical B source changes and cleanliness are not inputs. Ordinary
main integration retains its separate local-first route. This schedule changes
no current Session, mode, authority, accepted work, history or repair count.

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

The consumer uses the reported published result, not whichever development build
happens to be newest. If later scoped publication replaces the same development
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
