# Developer handoff: best-effort repository sync after Phase completion

Date: 2026-10-23
Status: initial GPSYNC-01..08 producer repair verified and locally published; CNCF Phase 116 owner completed adoption and Phase closure; later GPSYNC-09..12 and shared-skill promotion remain pending
Owner: sm-workflow development chat

## User decision

Complete the Phase after its required development results and commits are
accepted. Then attempt RepositorySync as a best-effort follow-up. Failure or
safe deferral of synchronization must not undo Phase completion. The user asked
to include this in the next development, not to execute or migrate live runs now.

### Subsequent implementation scope

The CNCF owning chat subsequently received the Human instruction
「どうしたらよいの？cncfを締めたい」 in the context of the pending C1-C3
completion correction. The sm-workflow parent independently read that native
Human message (thread `01a0f928-bec2-7703-8782-43e85f553dd7`, turn
`01a16631-e729-7901-af86-2ef8b6cb4d3f`). The parent treats the bounded producer
correction, focused tests, independent review and normal local publication as
necessary prerequisites for the requested CNCF closure. This is the parent's
scope interpretation of the actual instruction, not a verbatim implementation
approval or an AI-to-AI grant.

The original planning-only decision remains historical. Implementation now
proceeds in sm-workflow; adopting policy and continuing the real Phase 116
Session remain with the CNCF owner. No live Session adoption, new Phase,
consumer source change or remote Git effect follows from this producer work.

## Observed problem

Read-only inspection of GoalPhaseNativeRepositoryClosure found that pre-child
admission currently requires the accepted HEAD/tree and global cleanliness (or
the narrow staged-management exception). The Phase 6.4 caller's retained
`e4-127-phase-closure-advance/stderr.log` reports the corresponding 409,
"accepted whole-Phase candidate changed before child Start". The prior
assessment recorded unrelated work despite the accepted candidate remaining
unchanged. RepositorySync's FULL_STATE preservation can stage/checkpoint dirty
work, so removing only the parent dirty check is not a valid correction.

The CNCF Phase 116 retained final-review Advance instead reports Wait at
PhaseClosing awaiting RepositorySync authority. This is a separate observed
condition, not proof that its sync child ran or failed. Both examples motivate
separating completed development from optional repository synchronization.

## Next action and scope

Follow [C1-C3 and GPSYNC-01..12](../../../notes/goal-phase-post-completion-sync-plan.md)
and the [planned contract](../../../spec/goal-phase-post-completion-sync.md).
Schedule the bounded completion correction before Phase 6.6 diagnostic work,
at the next coherent boundary; do not interrupt or reset 6.4/6.5 work.

First prove durable Phase completion with unrelated dirty work preserved and
sync deferred. Then connect an already authorized clean sync and separate result
projection. Finally deliver supported adoption for the same existing pre-child
PhaseClosing Session. A safe deferral is an accepted best-effort outcome, not a
need to implement general dirty-worktree integration in this delivery.

The previously proposed 17-file scoped-sync repair is retained as assessment
history, not a mandatory batch or unchanged implementation instruction. Reuse
its causal findings and preservation tests. Reestimate the actual contract and
implementation surface for this simpler completion-first delivery. Selected
committed-candidate sync and protected-dirty merge can follow independently.

## Preservation and handback

Keep current source work, original Goal/Session/Plan/base/Step identities,
accepted candidate/commits/checks/reviews, histories and repair counts. Record
policy adoption through the normal supported path; never patch Session storage,
restart a completed Step, re-Submit accepted work or relabel an old failure.
For an already started/unknown child, retain its identity and observe its actual
state without blind replay. This plan does not grant remote Git effects.

Report Phase completed separately from sync succeeded/deferred/failed/unknown
and its actual reason/resume reference. Reuse current Workflow persistence and
Journal/Evidence. No timer, new outbox, AI monitoring loop or extra bookkeeping
agent is needed. Do not automatically stage, commit, stash or discard unrelated
work. The runtime owns Git effects; skills display/continue the issued contract.

## Original planning evidence and execution status

This change updates planning/specification documents only. No product code,
CML, runtime configuration, installed artifact, live Session or consumer checkout
was changed. No SBT, Git mutation, publication or live sync was run. Existing
validation remains historical evidence; all GPSYNC acceptance items are pending.

## Subsequent producer implementation status

The initial GPSYNC-01..08 bounded implementation is verified in the original
sm-workflow checkout and locally published. Earlier worker scope stops and
actual failed attempts are retained; they are not validation successes. Existing
strict regression fixtures select `repository-sync-required` through the normal typed host parameter
`sm-workflow.goal-phase.completion-policy`. The default for new Starts is
`local-completion-best-effort-sync`; an absent policy in an old saved Start
continues to mean strict completion until supported explicit adoption.

The focused repair, normal publication/runtime activation and isolated installed
CLI route were verified, and the same integrated independent review passed for
the issued initial boundary. Detailed actual execution/review evidence remains
outside Git at local backup location
`macmini:~/backup/journal/2026/10/sm-workflow/gpsync-completion-first/20261023/`
(retention 2026-11-22; hold while the CNCF116 owner consumes the continuation).
The real CNCF Session has not been operated here. Its owner uses the actual
public description, independent Human direction, normal evidence preparation,
policy adoption and ordinary Advance on that same Session. The later GPSYNC-09..12
amendment below remains follow-on work and is not accepted by the initial review.
The updated component-packaged skill also awaits separate normal promotion to
shared development-workstation discovery; this repair did not edit that checkout.

### Subsequent consumer outcome

Read-only confirmation of the CNCF owner's retained ordinary results shows the
existing Session `06016330fa63b5eac6097ff2b8ff42c2` accepted explicit
`AdoptLocalCompletion`, returned `local-completion-adopted`, then reached
Terminal/completed through ordinary Advance. Synchronization was separately
deferred because no current independently granted Phase-closure synchronization
authority existed. The owner subsequently synchronized CNCF through its ordinary
Git route; Phase 116 is closed. These are consumer-owned results, not Session
operations performed by this producer chat. They do not implement or accept the
later GPSYNC-09..12 extension or shared-skill promotion.


## Subsequent user decision: confirm changed HEAD and uncommitted work

The user requests Human confirmation when HEAD has moved or uncommitted changes
are present, followed by ordinary continuation if both are acceptable. This
amendment updates the target contract while producer implementation is in
progress; it does not replace another owner's source edits or claim that the
new behavior is already implemented.

Show previous/current HEAD, a concise commit/change summary, staged/unstaged/
untracked scope and the intended next action in one combined question. Consume
the answer through the existing Human continuation and retain it in ordinary
Workflow history. Approval continues the same Session/frontier; the same
acknowledged condition must not fail a later cleanliness/candidate gate or ask
for another approval. Only material new changes need another decision.

The normal continue choice preserves uncommitted work. Explicitly identify and
authorize any intended inclusion in a checkpoint/commit instead of assuming that
acknowledging dirty state grants it. Original base, accepted candidate/commit/
check/review history and repair counts remain. A changed continuation HEAD does
not rewrite historical test results; reuse applicable results without automatic
full-test/review replay solely because HEAD moved.

C1 now includes the combined Human decision. C2 includes an actual confirmed
Equal/LocalAhead committed-candidate synchronization with dirty files preserved.
The previous permission to defer solely on dirty-state presence is superseded
for acknowledged, supported cases. Actual conflicts, unsupported incoming
integration, missing effects or unknown execution remain concrete sync reasons,
while Phase completion stays durable. General protected-dirty merge remains
later work; no new outbox, approval service, hash/expiry protocol or AI gate.

GPSYNC-09..12 cover these cases in the next-development checklist. Preserve the
ongoing producer work and its actual verification results when adopting this
amendment. This amendment changed documents only; no live Session was answered,
rebound, restarted or advanced, and no Git effect was executed by this chat.
