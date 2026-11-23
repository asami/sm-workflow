# Phase 28.1 acceptance checklist

Status: planned
Plan: [Phase 28.1](phase-28.1.md)
Source: [Phase 28 deferred conditions](phase-28-checklist.md#deferred-from-the-local-release)

## P281-01 — Metals connection (P28-02a, issue-13)

- [ ] The normal packaged repair entry point resolves an authorized Metals
  provider from its configuration and ships at least one allowlist rule for a
  supported Metals action. A controlled LSP subprocess exercises the production
  provider selection, launch admission, transport, and rule. The workflow
  applies the selected edit and passes the checks without calling AI repair.
- [ ] Disabled or unavailable Metals, timeout, missing negotiated capability,
  and no eligible action leave the original diagnostic available to the
  ordinary repair path. The owned process and its resources are cleaned up.

## P281-02 — Native formatting and lint correction (P28-05, issue-14)

- [ ] The regular repair path applies and revalidates the eligible Scala
  terminal-newline correction and at least one allowlisted correction produced
  by the configured formatter or lint tool. The fixtures show the original
  finding, applied edit, and check results without an AI repair call.
- [ ] The shared patch path rejects inadmissible edits and restores the
  candidate after failed revalidation. The check history retains the original
  diagnostic, the attempted action, and the revalidation or rollback result.

## P281-03 — Packaged acceptance and release

- [ ] Both capabilities are exercised through the packaged workflow, with
  case-specific evidence for the diagnostic, tool or rule, affected paths,
  and validation result. The review checks that the existing deterministic
  compiler repair and fallback path still work.
- [ ] Independent review and normal local release cover both usable paths.

After release, confirm the configured Metals connection and formatting/lint
fixes on actual development projects. Record broader Scala-version coverage
and operational measurements under the original Phase 28 follow-up conditions.
