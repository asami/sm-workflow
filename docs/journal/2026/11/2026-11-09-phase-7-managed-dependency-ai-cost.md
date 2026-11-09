# Phase 7 managed dependency development: AI cost objective

Date: 2026-11-09 (Asia/Tokyo)
Status: planning clarification; implementation and installed acceptance pending
Owner: Phase 7 managed dependency development and Development Artifact Overlay

## Confirmed operating scenario

The user confirmed the intended Phase 7 managed-development route: when project
A needs a change in dependency B, develop B in A's managed worktree area, validate
and commit it, publish its artifacts locally, select that exact result in A,
validate the consumer, and synchronize both projects' source contributions with
their configured integration/GitHub destinations under existing authority.

This is the managed development mode, alongside Fixed and assigned development
chat modes. It does not automatically replace all existing chat assignments.
The existing order still proves ordinary assigned-chat commit/publishLocal and
consumer adoption before broadening to the managed-subtree/overlay route.

The [overlay contract](../../../spec/development-artifact-overlay.md#1-ownership-and-explicit-location)
uses `.textus/sm-workflow/work.d/checkouts/` for related worktrees and
`work.d/artifacts/<overlay-id>/repository/` for published development artifacts,
under that same managed area. This is not a new `target/` worktree convention.
A selects a producer-published result including JARs and dependency metadata;
there is no manual JAR copying, newest-file discovery or implicit source build
on consumer resolution. Logical resources resolve the actual locations.

Git source synchronization, artifact publication/selection and consumer
acceptance remain distinct. Both A and B are explicit synchronization targets;
the current best-effort policy records success, failure, defer/wait or unknown
truthfully. A conflict or deferred sync does not revoke completed development
or usable local publication and does not count as GitHub convergence. Artifact
publication alone grants no push permission, and Git sync does not republish or
switch the selected artifact.

## Expected AI cost benefit

The managed route can reduce management inference otherwise spent on composing
and delivering a cross-chat B request, waiting/collecting it, restating scope and
validation, transferring results, and coordinating exact consumer adoption.
This is a hypothesis about avoidable orchestration, not a measured reduction
for equivalent A/B tasks. The
[AI cost investigation](2026-11-09-ai-cost-observations-and-phase-workflow-comparison.md)
does not provide a causal percentage for this mode; its approximately 67%
per-Step difference must not be reused as a forecast.

The benefit depends on using existing runtime owners for worktree preparation,
build/publication, artifact selection, waiting and synchronization. Asking the
parent AI to issue and interpret every mechanical step can retain the same
overhead even when directories move under A. Avoid new AI polling or an extra
queue/approval controller.

Implementation, semantic review, required tests and effect authority remain
necessary. Worktree setup, additional builds or loss of B's accumulated knowledge
can offset savings. Hand off the bounded task with relevant B contracts/source,
decisions and evidence references, and return concise outcome/artifact references
and unresolved issues. Keep context sufficient for the actual work without
copying an entire chat or forcing repeated discovery. Moving work to a subagent
or another chat does not remove that work from total cost.

Reuse valid producer checks at their actual target and scope. A still performs
its affected generation/compilation/integration checks, and independent review
retains its normal obligation. Additional necessary files or technical inventory
updates within authorized purpose do not alone create another Human decision;
real scope, ownership, compatibility or effect-authority choices retain theirs.

## Planning and observations

[Phase 7](../../../phase/phase-7.md#ai-cost-in-managed-dependency-development--2026-11-09)
now connects this objective to the existing P7-DD-09 and A/C/D scenarios, without
adding a separate benchmark campaign or changing the revised main-route order.
During those scenarios, record what existing observations support:

- parent/management responses and dispatch, wait, result-transfer and Human
  handoffs for attributable A/B work;
- control/JSON transfer sizes, context and cached/uncached/output usage where
  available, with actual models rather than role-name assumptions;
- setup/build work, semantic implementation and independent review, rework and
  missing-evidence returns, including work outside the parent chat;
- successful B delivery, A integration and separate A/B synchronization outcomes.

Compare equivalent requested outcomes, dependency changes and required checks
with assigned-chat episodes where possible. Preserve failed/unfinished work and
mark unobserved or unattributable cost unknown. A small or unmatched sample is
not a universal saving estimate; response/byte counts alone are not a quality or
monetary result. No fixed reduction threshold or new accounting service is a
Phase 7 gate.

Phase 8 owns broader parent round-trip/context-transfer improvements; Phase 13
owns systematic parent/child usage collection, and Phase 9 broader model/routing
evaluation. None of their full deliveries is a new Phase 7 prerequisite. Local
LLM chat-waiter replacement remains a deferred candidate, not a dependency of
this route.

This clarification changes planning documents only. It starts no development,
worktree, build, publication or synchronization and changes no configured mode,
active Session, accepted result, history, repair count or current project owner.

## Follow-up: runtime-managed A stop, B subagent wait and A resumption

The user clarified the selected managed-development composition: stop A's work
synchronously, execute B in a dedicated subagent, await its result, then return
to A. The user specifically selected runtime ownership of the complete
stop/dispatch/wait/resume sequence as a Phase 7 planning requirement.

Stopping A means retaining its pending continuation in the **active Goal and
same A Session** while waiting for B, not cancelling/completing the Goal or
starting another execution later. A's dependency-requiring implementation does
not proceed while the required B outcome is unresolved. Other independent work
is not globally serialized by this dependency wait.

The existing runtime keeps A's continuation and B request/worker/result
association. The registered host route creates the dedicated worker and reports
its actual identity; existing typed caller-mediated dispatch can be used where
required by the host. There is no new agent controller, chat-memory scheduler,
queue ledger or independent Goal/Session wrapping the original work.

B receives the bounded objective, ownership/worktree, relevant contracts/source
and acceptance/evidence references. Its selected capability may still be Sol
High. The runtime/effect owners run required checks, commit and publication;
independent review remains separate from B's implementer. Subagent isolation
separates context and responsibility, but actual filesystem/resource/effect
authority is enforced by the existing owners.

Pure waiting is runtime work: do not repeatedly invoke the parent, add a separate
LLM monitor, or poll through AI for progress-only events. Keep existing visible
activity and status through actual lifecycle observations. Resume parent
inference for a meaningful B outcome, a semantic problem, a genuinely unresolved
Human decision or an exception requiring judgment. Preserve stop/defer and
live/unknown-effect behavior. Human waiting time itself is not an inference fee.

On successful B delivery, retain the actual source/commit, published artifact
identity and validation/review references and schedule A's original continuation
through the existing host/Goal integration. A receives a short result with
resolvable references, explicitly selects that artifact and performs its affected
integration checks. A transport ACK, completed agent, saved result or notification
alone is not proof of delivered B work or resumed A execution. Failed checks,
missing/failed publication, decision-needed and UNKNOWN results stay distinct;
they cannot silently release A as if its dependency succeeded.

Keep the same Goal/Session, accepted A/B work, original pending continuation,
history and cumulative repair accounting. Reuse valid producer evidence at its
actual scope without replaying accepted Submit, successful effects or unrelated
checks. Source synchronization remains separate and follows existing authority
and best-effort reporting. This design does not reinstate withdrawn arbitrary
intermediate-recovery guarantees or authorize uncertain-effect retries.

The updated [Phase 7 plan](../../../phase/phase-7.md#runtime-managed-ab-dependency-execution--2026-11-09)
and existing [P7-DD-09](../../../phase/phase-7-dependency-delivery-checklist.md)
now require the connected successful sequence plus B failure/decision-needed and
stop/defer cases. Verify that pure waiting emits no periodic parent/monitor
inference and that the matching result actually resumes the same A continuation
into consumer integration. An unavailable host integration is an explicit
unimplemented limitation, not an accepted notification-only substitute. Reuse
existing A/C/D scenarios and owners rather than adding a separate framework or
benchmark gate.

The earlier seven-day investigation attributed about 17.5% of observed total
credits to parent responses issuing narrow waiting calls. That population spans
other workflows and includes response context cost; it is neither a measured
A/B-only expense nor a fully removable amount. Runtime-managed waiting targets
those avoidable inference turns, with no fixed saving promised. Include B worker
setup/context, implementation, review and rework when comparing total cost.
Inlining B may save a handoff yet enlarge A's context, so omitting the dedicated
worker is not automatically cheaper. Keep sufficient B knowledge without copying
the full chat or forcing new discovery.

Only the Phase 7 plan, its existing dependency checklist and this journal were
updated. No worker was dispatched and no Goal, Session, mode, ownership, runtime,
worktree, publication or synchronization was changed.

## Follow-up: two synchronization routes for parallel development

The user clarified that Phase 7's managed dependency completion should synchronize
the selected B worktree only with GitHub, leaving other machine-local worktrees
out of the operation to preserve parallel-development independence. The user
confirmed that this and ordinary main consolidation are two different purposes
and requested explicit documentation of the distinction.

| Purpose | Planned route |
| --- | --- |
| Ordinary integration into main | Working checkout -> canonical local main checkout -> selected GitHub main; `local-first` remains the default. |
| Managed dependency B developed for A | A's registered B worktree <-> explicitly selected B GitHub branch; no separate local-main hop. |

The managed dependency request/configuration explicitly selects the existing
`sm-workflow.repository-sync.main-integration=direct` for a remote-main target.
A non-main dependency target keeps its configured tracking branch. This is
scoped selection through existing configuration, not a machine-wide override,
new configuration precedence or automatic change to A/root's own route.
Inconsistent explicit route selection must be resolved before affected effects;
neither silent override nor fallback to a different checkout is permitted.

Other B worktrees, including its canonical local main checkout, are not sync
participants. Their dirty files and unpublished branch commits are not gathered,
merged or committed; those checkouts/local branches are not updated, and their
cleanliness is not a prerequisite. Normal Git fetch/object/remote-tracking
bookkeeping and protection of genuinely shared resources remain with the
existing owner. This source-level independence adds no global lock, new clone
requirement or parallel synchronization engine.

Fetch and reconcile the selected GitHub target normally, retaining conflicts,
required validation/review and permitted non-force push. A dependency's direct
route is not unconditional overwrite of GitHub. A later explicit request can
integrate B into canonical local main through the normal route; that is separate
work, not an automatic Phase 7 completion side effect. Artifact selection and
producer/consumer acceptance remain separate from source synchronization, and
the existing best-effort sync policy reports failed/deferred/unknown outcomes
without revoking usable local delivery.

Updated the [Phase 7 plan](../../../phase/phase-7.md#dependency-synchronization-routes--2026-11-09),
[P7-DD-09](../../../phase/phase-7-dependency-delivery-checklist.md),
[dependency delivery specification](../../../spec/dependency-development-delivery.md#managed-dependency-synchronization-isolation--2026-11-09),
[main integration specification](../../../spec/repository-sync-local-main-integration.md#phase-7-managed-dependency-route)
and the corresponding Phase 8 planning/unchecked acceptance. Qualification
must contrast ordinary local-first integration with the explicit managed direct
route, including an unrelated dirty/ahead B worktree, unchanged non-main targets
and actual remote conflict/failure handling through existing scenarios.

These are planned contracts; implementation/installed qualification remains
pending. No current request, Goal, Session, route setting or project ownership
was changed. No worktree was created, and no Git source integration, publication,
commit, push or synchronization was performed while recording this decision.
