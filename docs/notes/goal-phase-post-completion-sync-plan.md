# Next development: complete GoalPhase before best-effort RepositorySync

Status: initial completion/safe-deferral delivery verified and locally published; Human-confirmed continuation extension planned
Owner: sm-workflow development chat
Decision: 2026-10-23
Specification: [completion and follow-up](../spec/goal-phase-post-completion-sync.md)
Handoff: [English developer handoff](../journal/2026/10/2026-10-23-goal-phase-post-completion-sync-handoff.md)

## Scheduling and scope

Implement this bounded completion correction at the next coherent development
boundary, ahead of Phase 6.6 diagnosis work. It is a standalone follow-up slice,
not a new numbered Phase or an extra acceptance obligation for ongoing 6.4/6.5.
Do not interrupt issued work or reopen their accepted Steps. Phase 6.6 retains
D1-D4 and all P66 criteria; this slice does not depend on those diagnostics,
Phase 7 workspace support, Phase 9 model/convergence work, or CBD integration.

The earlier 17-file selected-candidate/dirty-sync proposal is not the mandatory
first repair. Its scope combined completion with the ability to synchronize a
dirty repository. Retain that assessment as history and reuse relevant evidence;
implement completion separation first. The subsequent user decision adds normal
Human-confirmed HEAD/dirty continuation and the committed-candidate sync route;
general dirty merge remains deferred. Reestimate
actual affected files after tracing the normal CML/controller/persistence and
caller contracts; do not treat the old file count as the new fixed scope.

## Main route first

1. **C1 — Durable local completion.** Connect accepted required commits/checks/
   review to Phase completion without global cleanliness or successful remote
   synchronization as an unconditional prerequisite. Align the normal CML,
   controller, generated Operation results and persistence transition together.
   Prove completion with unrelated unstaged/untracked work preserved and no
   authorized remote effects. Present moved HEAD and dirty changes in one Human
   question; an accepted answer continues the same Session and is consumed by
   downstream gates. Handle outstanding Phase-owned commits normally.
2. **C2 — Best-effort follow-up.** Use existing RepositorySync for an already
   authorized safe target, after completion is durable. Return/display Phase
   completed plus the actual separate sync state and existing continuation.
   Show clean synchronization and a confirmed moved-HEAD/dirty route for actual
   Equal/LocalAhead committed candidates, preserving dirty files outside commits.
   Do not defer solely because already acknowledged dirty work remains. Disabled
   effects, actual conflicts or unsupported worktree-changing integration may
   defer sync with a concrete reason. General protected-dirty merge is later work.
3. **C3 — Adoption and delivery.** Connect the same policy to an existing
   pre-child PhaseClosing run without replaying accepted work. Cover an already
   linked child and uncertain effects proportionately, preserve history, then
   provide the normal installed skill/runtime handoff. Caller display uses the
   actual result and adds no AI call or polling loop solely for bookkeeping.

Reuse existing CNCF Workflow persistence, typed Git effects, Journal/Evidence
and caller display. Runtime code belongs to sm-workflow; change shared skill
projections only where the delivered contract requires it. Consumer repositories
and their live Sessions are not automatic mutation targets. Do not commit,
push or publish consumer work as part of fixing this incident.

## Acceptance checklist

The initial GPSYNC-01..08 boundary passed focused verification, independent
review, normal local publication/runtime activation and isolated installed CLI
verification. The CNCF Phase 116 owner then explicitly adopted that policy and
completed the existing Session, with synchronization truthfully deferred for
missing effect authority. Applicable existing results remain reusable.

The checklist below retains the latest amended requirements. GPSYNC-09..12 and
their added Human-confirmed HEAD/dirty requirements in earlier items are still
pending; unchecked amended items do not discard the accepted initial delivery.
Shared-skill promotion is also pending. No new live Session action is authorized
or performed by this planning update.

- [ ] GPSYNC-01: Required accepted development results and commits establish
      durable Phase completion before any RepositorySync Start or remote effect.
- [ ] GPSYNC-02: Unrelated modified/untracked work remains intact and outside
      commits; after any required Human confirmation it does not block completion.
      Outstanding Phase work is not lost.
- [ ] GPSYNC-03: A clean authorized route completes synchronization and reports
      both outcomes. A confirmed dirty Equal/LocalAhead route also continues.
      Disabled/no-authority/unsupported-integration routes report a concrete
      deferred/not-attempted outcome while retaining completed Phase status.
- [ ] GPSYNC-04: Sync failure, Human wait and unknown/running execution remain
      truthful separate outcomes; none reopens the completed Phase or forces a
      new permission question for Phase completion alone.
- [ ] GPSYNC-05: Reopen and interrupted-handback cases retain Phase completion
      and the actual child/effect state without duplicate Start, push or blind
      retry. Test before-sync-start and after-sync-start interruption boundaries.
- [ ] GPSYNC-06: An existing pre-child PhaseClosing Session adopts the new
      policy through supported continuation, retaining accepted checks/review,
      commits, Plan/base/Session/history and repair counts. Preserve an existing
      child if present; no private store patch or accepted-result replay.
- [ ] GPSYNC-07: Ordinary standalone FULL_STATE behavior remains intact;
      follow-up deferral never stages/stashes/checkpoints unrelated work. Actual
      candidate-changing integration keeps its applicable validation obligations.
- [ ] GPSYNC-08: Normal installed caller/skill output distinguishes Phase
      completion and sync outcome/reason/resume reference. Deliver focused
      regression results, an isolated connected demonstration and English
      adoption instructions; do not claim live consumer acceptance from fixtures.

- [ ] GPSYNC-09: HEAD movement alone, dirty changes alone and both together
      produce an understandable existing Human question, combined when both are
      present. Approval continues the same Session/frontier with the acknowledged
      state; rejection preserves work and waits for the selected correction.
- [ ] GPSYNC-10: The approved decision survives normal handback/reopen and is
      consumed by downstream gates without repeat prompts or global-clean refusal.
      A materially new change is distinguished from the acknowledged state and
      prompts only for the new decision needed; no TTL/hash/extra approval gate.
- [ ] GPSYNC-11: With moved HEAD and dirty work confirmed, an isolated actual
      Equal/LocalAhead sync succeeds without including dirty work in a commit.
      Keep original base/candidate history and actual evidence attribution; no
      automatic Phase test/review replay or implicit FULL_STATE checkpoint.
- [ ] GPSYNC-12: Follow-up presentation identifies any actual conflict, missing
      effect authority, unsupported integration or unknown effect without treating
      it as a fresh rejection of the acknowledged HEAD/dirty state. Phase completion
      remains durable; unknown effects are observed rather than replayed.

Use existing scoped fixtures and local bare remotes for Git effects. Reuse
accepted validation within its actual scope; run focused compile/behavior checks
for the changed boundary and an integrated review under the existing policy.
Avoid a full-test/review cycle per helper or sync attempt. Required broader
validation remains governed by the actual delivery boundary.
