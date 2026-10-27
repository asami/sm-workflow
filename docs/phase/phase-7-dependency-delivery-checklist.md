# Phase 7 dependency development delivery checklist

Status: pending; no implementation or runtime acceptance claimed
Owner: [Phase 7](phase-7.md)
Contract: [dependency development and delivery](../spec/dependency-development-delivery.md)

These items extend the existing A/B/C/D plan; retain its existing IDs and
evidence. A checkbox is a delivery obligation, not a separate review ceremony.

- [ ] P7-DD-01: Explicit Fixed/AssignedChat/DevelopmentSubtree selection is
  consumed from dependency configuration. Fixed reports a dependency-change
  error and waits for user direction without editing/delegating or resetting work.
- [ ] P7-DD-02: Two admitted requests to one development chat execute FIFO with
  one active request. Transport acknowledgement is distinct from runtime admission.
- [ ] P7-DD-03: Request redelivery/reconnection reuses retained identity/result;
  waiting, explicit hold/cancel and resume preserve queue history without
  duplicate execution or silent reorder. Self-queue dependency cannot deadlock.
- [ ] P7-DD-04: A small defect uses Phase 6.8 delegation/shared execution with
  impact-selected verification, mandatory local commit and required publishLocal,
  then real consumer integration. No repeat permission within unchanged scope.
- [ ] P7-DD-05: An ad hoc request uses the independent delegation route without
  altering test-fix/goal-task contracts; an explicit Phase uses existing GoalPhase
  composition. Return accepted development/commit/publication outcomes and reuse
  applicable evidence without a duplicate lifecycle.
- [ ] P7-DD-06: Larger delivery attempts best-effort repository sync after local
  publication, with one applicable attempt/result. Failed/deferred/unknown sync
  neither revokes local delivery nor creates a blocking automatic retry loop.
- [ ] P7-DD-07: Commit success followed by publication failure preserves the
  commit and validations; recovery addresses only missing delivery, and unknown
  effects are not blindly replayed.
- [ ] P7-DD-08: Consumer adoption names the published artifact and originating
  request/commit and runs affected integration checks. Later same-coordinate
  publication cannot silently substitute a different result.
- [ ] P7-DD-09: A resource-managed development subtree retains origin, selected
  branch/area and synchronization destination; validated committed work becomes
  a synchronization target without touching canonical or unrelated work.
- [ ] P7-DD-10: Existing pending dependency/Human records reconcile through
  public continuation with genuine existing direction; all Sessions/history/
  counts/results survive. Real authority expansion retains Human admission.
- [ ] P7-DD-11: Queue and result lifecycle are runtime-owned; cross-chat icons
  use existing observations with no additional AI calls or periodic monitoring.
- [ ] P7-DD-12: Installed caller/runtime delivery and operational guidance
  demonstrate fixed, chat and subtree modes with applicable existing evidence.
  All remaining overlay/workspace obligations retain their original acceptance.
