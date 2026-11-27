# Phase 28.2 acceptance checklist

Plan: [Phase 28.2](phase-28.2.md)

- [x] P282-01: Capture a real attributed comparison diagnostic. Qualify
  containsEq via the resolved Core binary in an ordinary consumer, including
  a type-correct call, wrong-type compiler rejection, and behavioral parity.
  Deliver indexOfEq for demonstrated need with first-match, empty/no-match,
  type-rejection fixtures. The delivered API has no starting-position overload;
  no such semantics are claimed.
- [x] P282-02: Exercise the allowlisted deterministic rule end to end through
  ordinary installed sm-workflow repair and Admission, with originating lint,
  compilation, focused tests, and no AI repair call for the accepted edit.
  Verify refusal when analysis, binding or semantic equivalence is missing and
  rollback on failed revalidation. A warning disappearing alone does not pass.
- [ ] P282-03: Qualify bounded strictEquality adoption: intended comparisons
  compile and mismatched types fail. Preserve distinct semantics for value,
  identity and array comparisons, and leave unsupported sites unchanged.
- [ ] Record scope and producer revision/binary identity, observed verification,
  and independent review. No mass migration, suppression or reopened Phase.

## Retained Step evidence

- P282-01 was accepted at `f086227407795f032ceddfd59eef7d8d69f1c298`:
  Compile and 18 focused tests succeeded, followed by independent review. The
  Core producer is `0cf6c63d85bc9b640a3f812ed592853a06233b81`; both actual
  classpaths selected the normal shared `0.4.3-SNAPSHOT` binary with SHA-256
  `525e5fc27e1ae838f97d5ae8e82146d26b448b67045dee0e049ee6b62332b79b`.
  `indexOfEq` covers first match, empty and missing values and rejects a wrong
  type. The real Phase 28.1 AnalysisUnavailable site remains historically
  unresolved; it was not evidence authorizing an edit.
- P282-02 was accepted at `3110dcf1fc03ebd2826ca8b233d9d4e18a8f4291`
  (candidate tree `13c3b782807093b9c7091b4330913979f8c9b7d2`): Compile,
  eight focused tests and FixCheck (exit 0), independent review, seven
  installed cases and attestation. An earlier result had 385 diagnostics,
  including 383 on unchanged lines; the candidate source was narrowed rather
  than bypassing those results. Earlier attempts remain recorded as observed.
- P282-03 source cases distinguish Cats `Eq` value comparison, typed source ID
  comparison, byte-element equality and array reference identity. Compilation,
  installed-route qualification, independent review and Phase acceptance remain
  pending; the examples are not evidence of their own successful execution.
