# Phase 28.2: Strict Scala Comparison Migration and Deterministic Repair

Status: planned; execution not started
Planned: 2026-11-24 (Asia/Tokyo)
Scheduled: immediately after [Phase 25.2](phase-25.2.md), followed by [Phase 7.2](phase-7.2.md)
Checklist: [Phase 28.2 acceptance](phase-28.2-checklist.md)
Decision: [strict comparison migration journal](../journal/2026/11/2026-11-24-strict-scala-comparison-migration.md)

## Goal and boundaries

Make comparison intent explicit in ordinary Scala development and automate only
migrations whose type and behavior can be verified. Reuse [Phase 8.1-B/C](phase-8.1.md)
and the deterministic candidate patch, revalidation and rollback path from
[Phase 28](phase-28.md) / [28.1](phase-28.1.md). Do not reopen their accepted
work or make 28.2 a gate for their closure. The current FixCheck analysis and
scope issue is separate: AnalysisUnavailable means a comparison could not be
judged, not that it is incorrect or eligible for rewrite. Do not suppress such
findings or broaden Phase 28.1 to migrate existing source. Retain strict
comparison migration as deferred work until Phase 28.2. Earlier FixCheck
outcomes remain recorded as observed; deferral does not mean PASS.

Goldenport Core owns strict collection APIs; the owning consumer resolves the
actual Core artifact. sm-workflow owns repair selection, provenance,
verification, and Admission integration. [Phase 29](phase-29.md) retains
broader lint coverage, quality policy and annotation semantics.

## P282-01 — Adopt typed collection comparison in normal consumers

- Start from a real retained diagnostic attributable to a supported membership
  site, not a baseline example presumed to be a match. Separately record
  unresolved type analysis and the bounds of the qualifying scope.
- Reuse `Vector[A].typeSafe.containsEq` and the intended `cats.Eq[A]`. Verify
  the actual resolved artifact and ordinary Compile/Test consumer path, plus
  compile rejection of a wrong-type query through the intended API.
- Add `Vector[A].typeSafe.indexOfEq` in Goldenport Core for observed consumer
  need. Capture the receiver's element type as for containsEq, use an explicit
  Eq, and preserve first-match and not-found behavior. If supporting a start
  position, specify and test its boundary behavior; otherwise refuse to
  rewrite such overloads. Do not add speculative methods on other collections.

## P282-02 — Add a safe rule to the existing programmatic repair route

- Allowlist one narrow, resolved Vector membership migration first. Verify the
  receiver, method and argument types, required imports and Eq on the actual
  classpath, and that the selected Eq preserves the original site's behavior.
  Start with a verified simple case such as same-type String membership. Add
  an indexOf rule only against the delivered API and supported signature.
- Decline AnalysisUnavailable, unresolved overloads, broad/widened types,
  unavailable APIs, ambiguous equality, and changes of meaning. Never turn
  value comparison into ID comparison, or reference comparison into structural
  comparison, merely to clear lint. No casts, universal Eq/CanEqual, blanket
  suppression, or textual replacement based only on a method name.
- Use the existing scoped patch and revision checks, repeat originating lint,
  compile and focused behavior checks, and use existing rollback on failure.
  Preserve the diagnostic and reason on refusal; keep ordinary review or
  fallback policy and Admission unchanged. Successful automatic repair does not
  require an AI repair call.

## P282-03 — Establish direct and explicit comparison conventions

- Qualify `import scala.language.strictEquality` and bounded `CanEqual` in a
  representative Scala 3 file/module with positive and negative checks. Treat
  the import as an intentional scope migration, not a per-warning rewrite.
  Coordinate rather than duplicate the generated/AI-authored adoption in 8.1-B.
- Reuse Cats Eq for explicit value semantics and existing identity helpers for
  ID comparison. Use an explicit array comparison that matches the requirement
  (element equality or reference identity). Add a narrow named utility only
  where no existing API expresses the intent. Set/Map/hash-based semantics and
  broad existing-code migration are outside this first slice.
- Verify the normal installed sm-workflow path on a representative real site,
  report supported and refused cases, and retain independent review evidence.
