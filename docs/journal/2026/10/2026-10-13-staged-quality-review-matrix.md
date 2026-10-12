# Staged quality review matrix: Step Close, Phase Review, Phase Final

Date: 2026-10-13
Status: workflow direction
Related: GoalPhaseWorkflow, Candidate-Admission Model, Phase 6, Phase 9, CAR lint, cbd-support Review, Cozy

## Decision

GoalPhaseWorkflow invokes architectural/deterministic quality checks and semantic quality review at three lifecycle boundaries: Step Close, Phase Review, and Phase Final. The same checker is context-sensitive: scope and purpose differ by boundary.

## Shared review matrix

| Concern | Step Close | Phase Review | Phase Final |
|---|---|---|---|
| CAR lint: CNCF/Textus rules | changed scope, primary | whole Phase, primary | unresolved/blocking confirmation |
| CAR lint: model/ABI consistency | changed scope, primary | integrated state, primary | freshness/blocker confirmation |
| CAR lint: prohibited/risky patterns | primary | primary | unresolved confirmation |
| Review: Step requirements | primary | supporting evidence | normally no new search |
| Review: Usage Contract | affected Slice | whole Phase, primary | blocking completion |
| Review: implementation quality | changed scope, primary | whole-system perspective | repair delta when needed |
| Review: excessive checking/assurance | changed scope, primary | cross-Step/system-wide, primary | repair delta when needed |
| Review: unnecessary complexity/abstraction | changed scope, primary | cross-Step/system-wide, primary | repair delta when needed |
| Review: duplicate implementation/state | relevant scope | primary | only unresolved/delta |
| Review: component integration | relevant connected scope | primary | blocking completion |
| Review: architecture fitness | relevant scope | primary | only affected delta |
| Review: cross-Step assumption conflicts | normally out of scope | primary | unresolved confirmation |
| Acceptance Boundary compliance | primary | primary | primary |
| Finding resolution | after repair | primary | primary |
| Evidence coverage/freshness | primary | primary | primary |
| New issue discovery | primary within Step | primary across Phase | normally not exploratory |
| Release blockers absent | not a Step concern | identify/classify | primary |

Interpretation:

- **Step Close** asks: is the delivered Step/Slice locally sound and admissible?
- **Phase Review** asks: is the integrated result sound as a whole? This is the broadest semantic quality review.
- **Phase Final** asks: is closure/release justified by fresh, sufficient evidence? It should not restart an unconstrained exploratory review.

CAR lint and cbd-support Review are therefore not three unrelated implementations and should not blindly rerun an identical full scan. The lifecycle context selects scope, checks, evidence reuse, and blocking policy. Phase Final preferentially reuses admitted evidence and examines repair deltas/unresolved blockers.

## sm-workflow responsibility

sm-workflow owns **when** these checks are required and how their evidence participates in Admission, Repair, ReReview, commit and closure.

A useful application-level context is:

- `STEP`: change/Slice-oriented review before Step admission and commit.
- `PHASE`: integrated comprehensive review after all planned Steps are accepted.
- `FINAL`: closure-readiness confirmation after Phase review/repair.

These are sm-workflow lifecycle concepts, not generic CNCF ABI vocabulary.

Conceptual progression:

```text
Step delivery
  -> CAR lint(STEP)
  -> cbd-support Review(STEP)
  -> Admission -> Step commit
  ... repeated ...
  -> CAR lint(PHASE)
  -> cbd-support Review(PHASE)
  -> bounded repair / re-review
  -> CAR lint(FINAL)
  -> cbd-support Review(FINAL)
  -> deterministic final validation
  -> Phase release/close
```

Policy may avoid an unnecessary invocation when already-fresh evidence fully satisfies the corresponding AdmissionRequirement. In particular FINAL is not permission to duplicate a fresh comprehensive Phase Review.

## Cross-project contract

- CNCF supplies generic Requirement/Evidence/coverage/freshness/Admission semantics.
- Cozy supplies model-derived review knowledge and scope metadata from CML.
- cbd-support supplies CAR lint and semantic quality Review/check profiles.
- sm-workflow maps development lifecycle boundaries onto those generic capabilities and governs progression.

This matrix should guide later executable specifications rather than create extra mandatory per-finding review cycles.

## Development-plan adoption — 2026-10-13

The owner adopted sm-workflow Phase 6 as the concrete consumer/driver. Required
producer/provider capabilities are implemented and accepted in their own projects:
CNCF Phase 106, Cozy Phase 81 and textus-cbd-support Phase 15. The connected
sequence is minimum shared contract -> real Step admission/commit -> integrated
Phase Review -> affected repair/re-review -> Final evidence reuse and closure.
Each project owns its implementation/checklist; do not wait for unrelated roadmap
items or import an entire provider backlog into Phase 6. Phase 9 extends this
working route with convergence/provider switching rather than delivering it first.

This project's [Phase 6 plan](../../../phase/phase-6.md)
records the bounded delivery. The shared driver/acceptance scenarios are in
[sm-workflow Phase 6](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-6.md).
Freshness means applicability to the actual current subject and requirements;
no TTL, hash/copy-based unchanged-content proof or automatic full review at every
boundary is introduced. Plans are PLANNED, not implementation or acceptance
claims. Existing active work and its Goal/base/epoch/history/counters are retained.
