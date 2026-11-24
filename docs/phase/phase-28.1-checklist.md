# Phase 28.1 acceptance checklist

Status: closed
Plan: [Phase 28.1](phase-28.1.md)
Source: [Phase 28 deferred conditions](phase-28-checklist.md#deferred-from-the-local-release)

The [local release record](phase-28.1.md#human-approved-formal-local-release--2026-11-24)
records the scope of the successful focused qualification, the failed FixCheck
and the comparison work assigned to Phase 28.2. The checklist continues to
show which requirements have no formal Phase acceptance.

## P281-01 — Metals connection (P28-02a, issue-13)

- [ ] The normal packaged repair entry point resolves an authorized Metals
  provider from its configuration and ships at least one allowlist rule for a
  supported Metals action. A controlled LSP subprocess exercises the production
  provider selection, launch admission, transport, and rule. The workflow
  applies the selected edit and passes the checks without calling AI repair.
- [ ] The ordinary Metals request is limited to `quickfix`. Returned
  `refactor.*` or `source.*` actions produce no edits in this path.
- [ ] Disabled or unavailable Metals, timeout, missing negotiated capability,
  and no eligible action leave the original diagnostic available to the
  ordinary repair path. The owned process and its resources are cleaned up.

## P281-02 — Native formatting and lint correction (P28-05, issue-14)

- [ ] The regular repair path applies and revalidates the eligible Scala
  terminal-newline correction.
- [ ] A selected compiler diagnostic is repaired by an eligible Scalafix
  rewrite when its prerequisites are available. Recheck the diagnostic on
  the repaired candidate.
- [ ] The ordinary pre-validation cleanup uses the effective project lint
  configuration to apply behavior-preserving Scalafix rewrites on selected
  files. The fixture demonstrates an actual edit and a successful recheck
  without an AI repair call. Check-only rules remain diagnostics.
- [ ] Scalafmt uses the project's formatter configuration on the selected
  handwritten Scala files. The fixture records an actual edit, a successful
  formatter check and idempotence without an AI repair call.
- [ ] An ordinary implementation candidate with no compiler errors still
  formats changed handwritten Scala files before final validation. Unchanged
  files remain outside the selection, and an empty selection skips execution.
- [ ] A Scala 3 formatting fixture preserves explicit braces for structural
  definitions and DSL composition, and preserves allowed indentation in
  terminal implementation expressions. The formatted candidate passes the
  existing `NoStructuralIndentation` check.
- [ ] The formatting stage applies the existing source-history normalizer
  before Scalafmt for an enabled project, retaining established authorship
  and history. An effective `none` project skips comment changes, including
  when it explicitly overrides a team profile. Commit preparation reuses
  applicable normalization for the same candidate.
- [ ] In the delivered automatic route, the program updates and checks managed
  history comments on the final candidate. Align `ai-directive` and the
  applicable skills so implementation, review and commit preparation require
  no routine AI action for the same comments. Verify an ordinary candidate
  whose implementation left the comments untouched.
- [ ] A combined fixture exercises compiler-error repair with Metals Quick Fix, then
  Scalafix cleanup → enabled source-history normalization → Scalafmt → final
  validation on the resulting candidate. Retain the actual tool outcomes.
  Use matching compiler information for a selected Scalafix semantic rule,
  refreshing it after the Metals edit when necessary.
- [ ] The shared patch path rejects inadmissible edits and restores the
  candidate after failed revalidation. The check history retains the original
  diagnostic, the attempted action, and the revalidation or rollback result.

## P281-03 — Packaged acceptance and release

- [ ] Metals, Scalafix and Scalafmt are exercised through the packaged workflow, with
  case-specific evidence for the diagnostic, tool or rule, affected paths,
  and validation result. The review checks that the existing deterministic
  compiler repair and fallback path still work.
- [ ] Independent review and normal local release cover all three usable tool paths.

After release, confirm the configured Metals connection and formatting/lint
fixes on actual development projects. Record broader Scala-version coverage
and operational measurements under the original Phase 28 follow-up conditions.
