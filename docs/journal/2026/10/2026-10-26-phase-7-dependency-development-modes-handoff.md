# Phase 7 dependency development modes and delivery handoff

Date: 2026-10-26 (Asia/Tokyo)
Status: specification and planning update only

## Adopted user direction

The user selects three modes: fixed dependencies fail when a change is required
and await user direction; assigned development chats handle queued requests FIFO;
managed development subtrees produce work for later synchronization.

All assigned-chat deliveries include local commit and publishLocal. Small defects
use sm-fix with those explicit delivery operations. Larger ad hoc development uses
sm-goal-task; Phase development uses sm-goal-phase. Larger development ends with
best-effort sm-repository-sync. Failure/defer of that synchronization must not
revoke completed development or local publication.

## Implementation ownership and order

Phase 6.7 owns the single Task/FIX execution routes and initial existing-chat
handoff. Phase 7 owns dependency-mode configuration, runtime FIFO and delivery/
adoption/sync coordination. Preserve active Phase 6.6 and 6.7 plans and work.
Phase 17 remains the optional extension holding plan; do not move its entire
backlog into this implementation.

Start Phase 7 with one assigned-chat small-defect request through actual commit,
publishLocal and consumer integration. Next connect FIFO with two requests,
larger Task/Phase execution and best-effort sync. Bring fixed-mode refusal into
the initial mode boundary. Then prove managed-subtree development and explicit
synchronization using the existing A/B/C resources, and finish remaining original
overlay/workspace requirements at D. The ordinary assigned-chat publishLocal
route must not wait for the full artifact-overlay implementation.

The new [specification](../../../spec/dependency-development-delivery.md) and
[P7-DD checklist](../../../phase/phase-7-dependency-delivery-checklist.md) supply
the contract. [Phase 7](../../../phase/phase-7.md) preserves original A/B/C/D IDs,
overlay checklist and required upstream closure. No completion boxes were checked.

## Practical handoff

Carry objective, Component, acceptance, allowed operations, request/source
references, destination chat and result-return target. Existing approval covers
necessary same-purpose files and selected commit/publication; actual runtime
admission remains authoritative. Use one destination execution owner and
existing public WorkOrder/Continuation, not a chat-memory queue or polling agent.

Return actual commit and published artifact identity/outputs plus validation/
review and unresolved items. Consumer acceptance remains separate and occurs in
its original Session. Keep local delivery available even if optional synchronization
fails or waits for Human direction. Observe uncertain effects rather than replay
them; keep actual process ownership if synchronization is still running.

A development subtree is a logical managed development area, not a decision to
use the Git subtree command. Resolve its implementation with CNCF resources and
existing Phase 7 workspace/artifact contracts. This document does not authorize
ad hoc unmanaged worktree operation or manual artifact copying.

Preserve all active source edits, Sessions, original bases, accepted work, history,
repair counts and successful/failed/unknown evidence. No accepted Submit replay,
private-state rewrite, gratuitous revalidation, additional AI monitoring or
message-only progress loops. This journal is a handoff artifact; it does not
claim implementation, execution, chat dispatch or installed acceptance.
