# Phase 25 follow-on development priority

Decision date: 2026-11-24 (Asia/Tokyo)

## Rationale

Keep the immediate reductions in AI calls and repair cycles first. Finish the
ordinary OpenAI Task API route in Phase 25, then deliver usable Metals and
deterministic formatting/lint fixes in Phase 28.1 so eligible corrections do
not need an AI call.

Next, strengthen the workflow before expanding the provider pool. Phase 18
supplies tool-neutral validation for Phase 23; Flutter integration keeps its
existing CNCF dependency. Phase 23 separates current blockers from later
improvements to avoid repeated repair and review. Phase 22 stabilizes recovery
and continuations. Phase 29 then strengthens Scala lint findings and reduces
false positives without broadening the deterministic repair contract.

Deliver everyday development support next: Task and Standalone Fix extensions
(17), recursive repository catch-up (21), workspace operations (15), and
development investigation (14). Catch-up remains ahead of workspace operations.

Expand AI routing only after that work. Phase 13 establishes usage observations
before the shared-server and dual-CLI baseline (26) and capability/cost-aware
routing (27). Phase 20 aggregates the Phase 13 measurements across full Phase
runs and remains after Phase 27 in the adopted sequence. Leave the existing
eligibility, credit limits and fallback policy intact.

## What this changes

An earlier roadmap on GitHub put Phase 18 immediately after Phase 28. A
November 22 local revision then put Phase 25 ahead of Phase 18 and deferred
the measurement and provider expansion work. The November 23
cost-first revision added Phase 28.1 after Phase 25 and moved measurement and
provider expansion ahead of Phase 18. This decision keeps the first two Phases
from that revision, but moves validation, stabilization, and development support
ahead of broader provider expansion. See the [current roadmap](../../../phase/README.md#adopted-development-priority--2026-11-24) for the ordered plan.

The discussion briefly considered splitting Phase 25. We kept it intact once
T3 was clarified as normal-route qualification and safe failure handling, not
an expansion of the provider pool. We also considered omitting Flutter from
the cost-first path; the adopted sequence retains it in the validation group
rather than making it an immediate cost-reduction prerequisite.

This is a scheduling decision, not an acceptance or Session change. Preserve
Phase 25's T1–T3 plan, the local releases of Phases 24 and 28, the Phase 7.1
hold, and the scope of the Phase 7.2 worktree-retirement plan. Phases 10, 19, and 16
remain deferred.

## Worktree-retirement priority — 2026-11-24 (Asia/Tokyo)

The user placed [Phase 7.2](../../../phase/phase-7.2.md) at the start of the
second group: Phase 28.1 → Phase 7.2 → Phase 18. Retiring eligible temporary
worktrees prevents recurring disk pressure and does not need to wait for Flutter
or lint work. First prioritize the narrow RepositorySync outcome: after verified
remote synchronization, remove only the explicitly bound, clean, inactive
worktree. The same path covers a later manual RepositorySync after a failed
automatic sync. Keep failed or uncertain syncs and unsafe worktrees intact;
local Phase closure remains independent. This moves the schedule only;
implementation and existing worktrees are unchanged.

## Daily allowance and Codex completion metrics — 2026-11-24 (Asia/Tokyo)

The adopted order is **[25](../../../phase/phase-25.md) →
[28.1](../../../phase/phase-28.1.md) → [25.1](../../../phase/phase-25.1.md) →
[25.2](../../../phase/phase-25.2.md) → [7.2](../../../phase/phase-7.2.md) →
[18](../../../phase/phase-18.md)**. This supersedes the immediate-predecessor
sequence in the previous entry; Phase 7.2 still opens the stabilization group.
The rest of the roadmap remains in the same order.

Phase 25.1 retains the separate OpenAI daily free-allowance reservation and
settlement scope. The user put Phase 25.2 immediately after it: ordinary
sm-workflow completion posts two tables directly in the initiating Codex
chat, covering actual AI calls by engine, configured route, thinking mode and
level, and retained deterministic repairs by tool (Scalafix, Metals, other).
Configured route does not establish billing. This records scope and order only;
it starts no execution and changes no existing acceptance or Session.
