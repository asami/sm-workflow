# Parallel development first and Phase 8.1 allocation

Date: 2026-11-11 (Asia/Tokyo)
Status: adopted planning direction; no execution or acceptance claimed

## Decision and rationale

The user wants Phase 7 parallel development available early. Renumber the
planned Scala-quality work from 6.12 to 8.1 rather than making it Phase 7's entry
gate or replacing the existing Phase 9 convergence/provider-routing plan.

The delivery order is:

1. Phase 7: managed parallel development first within the Phase, then its
   remaining assigned-chat/FIFO and workspace/artifact acceptance.
2. Phase 8: runtime operational improvements, including continuation,
   waiting/result collection and reference-based exchanges.
3. Phase 8.1: A shared strict lint; B strict Scala 3 generation/AI authoring;
   C Core type-safe collection APIs and actual consumer adoption.
4. Existing Phase 9: acceptance-bounded convergence and provider routing.

Clarified by the user on the same day: use the simple Phase order
**7 -> 8 -> 8.1 -> 9**. Keep parallel-development priority within Phase 7;
do not split the rollout into mixed Phase 7/8 work or a later remainder stage.

Prove two independent managed development lanes early within Phase 7. Each A waits for its
selected B and resumes through runtime/host integration; unrelated work proceeds.
Use separate managed source/artifact resources and selected-worktree GitHub sync.
Keep assigned-chat delivery/FIFO and all original overlay/workspace scenarios
as remaining Phase 7 obligations, not deleted or silently accepted work.

## Scope and preservation

[Phase 8.1](../../../phase/phase-8.1.md) retains the existing expanded canonical
6.12 A/B/C plan and all SLP-A01–33, SLP-B01–04 and SLP-C01–05 criteria as OPEN.
The older development-copy A-only plan is brought into agreement with that
already documented scope. Existing producer plans keep their own identities;
old 6.12 links resolve through relocation records, with historical journals left
unchanged. Independent A/B preparation can proceed earlier; C needs actual Core
delivery. This is scheduling, not an additional producer API or technical gate.

The [Phase 7 plan](../../../phase/phase-7.md),
[Phase 8 plan](../../../phase/phase-8.md) and roadmap carry this order. All
existing permission/scoped-JAR proposals, accepted results, pending Humans,
Goals, Sessions, UNKNOWN/failed trials, histories and repair counts are retained.
Nothing here closes, restarts or advances Phase 6.11, replays a successful
operation, authorizes publication/push, or dispatches another chat.

## Conditional overlap clarified by the user

The default order remains 7 -> 8 -> 8.1 -> 9. If the remaining Phase 8 work can
be assessed at about half a day including validation/review, and is independent
of unfinished Phase 7 delivery, Phase 8 may develop in parallel with Phase 7.
The current plan contains multiple operational topics; no half-day estimate or
remaining-implementation assessment has yet been established. Do not drop
acceptance to fit that time, or start either Phase from this planning note.

## Subsequent decision: Phase 18 after Phase 9

The user subsequently directed: "それでは9の後に18を進めるようにしよう。"
The current selected delivery order is therefore
**7 -> 8 -> 8.1 -> 9 -> 18**. The earlier four-Phase sequence above remains the
history of the initial decision; this addition fixes Phase 18's previously
unassigned scheduling priority.

[Phase 18](../../../phase/phase-18.md) owns tool-neutral SBT/Flutter validation
and actual Flutter consumer acceptance. It reuses the Phase 6.7 baseline and
does not wait for unrelated Phases 10-17. Its W4-W5 still require the qualified
CNCF Phase 117 C5 producer delivery; W1-W3 can align with that producer within
the scheduled Phase 18 work. This decision does not start CNCF Phase 117 or
make Phase 18 a condition for Phase 9 closure.

The Phase 7/8 overlap option remains conditional on the recorded half-day
assessment and independence; it does not move Phase 18 before Phase 9. Update
the roadmap and current Phase sequence summaries consistently, retaining all
existing Phase identities, W1-W5/P18 acceptance items, active work, evidence,
Sessions, repair accounting and Phase 6.11 closure priority. No implementation,
adoption, validation, publication, commit, push or successor execution is
authorized or performed by this planning update.

## Subsequent decision: split Journal delivery

The user subsequently agreed to split Phase 11.1. The minimum single-entry
Decision/Issue/Handoff route stays in 11.1 before Phase 8; finite batch management
and selected additional consumers move to the separate Phase 11.2. The current
default priority is **7 -> 11.1 -> 8 -> 8.1 -> 9 -> 18**. Phase 11.2 has no fixed
slot and does not gate 8/9/18. See the
[split record and original-item mapping](2026-11-11-journal-single-entry-and-batch-split.md).
Earlier sequences above remain the history of those decisions. Preserve the
conditional 7/8 overlap only for selected work independent of unfinished 7/11.1
delivery under the already recorded assessment. No Phase or implementation starts.
