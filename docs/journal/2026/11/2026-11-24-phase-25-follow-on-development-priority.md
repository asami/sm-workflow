# Phase 25 follow-on development priority

Decision date: 2026-11-24 (Asia/Tokyo)

## Rationale

Keep the immediate reductions in AI calls and repair cycles first. Finish the
ordinary OpenAI Task API route in Phase 25, then deliver usable Metals and
deterministic formatting/lint fixes in Phase 28.1 so eligible corrections do
not need an AI call.

Next, strengthen the workflow before expanding the provider pool. Phase 7.2
starts this group because retiring explicitly temporary dependency worktrees
after verified remote synchronization reduces disk pressure without blocking
local Phase closure. Phase 18 then supplies tool-neutral validation for Phase 23; Flutter integration keeps its
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

The November 22 roadmap put tool-neutral validation immediately after Phase 25
and deferred the measurement and provider expansion work. The November 23
cost-first revision added Phase 28.1 after Phase 25 and moved measurement and
provider expansion ahead of Phase 18. This decision keeps the first two Phases
from that revision, but moves validation, stabilization, and development support
ahead of broader provider expansion. See the [current roadmap](../../../phase/README.md#adopted-development-priority--2026-11-22) for the ordered plan.

This is a scheduling decision, not an acceptance or Session change. Preserve
Phase 25's T1–T3 plan, the local releases of Phases 24 and 28, the Phase 7.1
hold, and Phase 7.2 ownership and protections. Phases 10, 19, and 16 remain
deferred.
