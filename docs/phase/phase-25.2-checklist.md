# Phase 25.2 acceptance checklist

Plan: [Phase 25.2](phase-25.2.md)

- [ ] P252-01: Completion reports confirmed AI calls by engine and configured
  route (Codex, OpenAI monthly, OpenAI daily, named other), mode, and level.
  A mixed run distinguishes actual execution, rejected dispatch, retry, fallback,
  unknown execution, and replay without claiming billing status.
  Report available input/output/total token usage and coverage. Fixtures cover
  complete, partial, absent and known-zero usage, cumulative and replayed
  usage, and token subcategories without double-counting.
- [ ] P252-02: Completion reports retained, revalidated repair applications by
  Scalafix, Metals and other programmatic corrections. Controlled events prove
  attribution using available Phase 28.1 routes; no-op, check-only lint, rejected and rolled-back edits do not count.
  At least one currently available repair is verified end to end.
- [ ] P252-03: Normal packaged completion displays the Markdown tables in the
  initiating Codex chat, with matching structured counts for ordinary,
  zero-activity, and incomplete-evidence runs. A CLI-only artifact is not enough.
  Resume/child replay is deduplicated, with no added AI call or closure gate.
- [ ] P252-04: Verify the non-AI resolution numerator/denominator, attempts
  vs retained edits, repeat compile/test/lint/review cycles, and elapsed time
  vs cumulative stage times. Include multiple edits in one episode, fallback
  to AI, unresolved or missing dispositions, resumed/replayed events, parallel
  spans, and unknown duration. Show AI role and route-switch reasons where
  retained evidence is available, without requiring a new instrumentation path.
- [ ] Record review and release evidence. Distinguish live observations from
  fixtures and unavailable tool routes.
