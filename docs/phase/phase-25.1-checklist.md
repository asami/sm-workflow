# Phase 25.1 acceptance checklist

Plan: [Phase 25.1](phase-25.1.md)

- [ ] P251-01: Completion reports confirmed AI calls by engine and configured
  route (Codex, OpenAI monthly, OpenAI daily, named other), mode, and level.
  A mixed run distinguishes actual execution, rejected dispatch, retry, fallback,
  unknown execution, and replay without claiming billing status.
- [ ] P251-02: Completion reports retained, revalidated repair applications by
  Scalafix, Metals and other programmatic corrections. Controlled events prove
  attribution; no-op, check-only lint, rejected and rolled-back edits do not count.
  At least one currently available repair is verified end to end.
- [ ] P251-03: Normal packaged completion shows a human summary and matching
  structured counts for ordinary, zero-activity, and incomplete-evidence runs.
  Resume/child replay is deduplicated, with no added AI call or closure gate.
- [ ] Record review and release evidence; defer live qualification of newly
  delivered correction tools to Phase 28.1 without relabeling fixtures as live.
