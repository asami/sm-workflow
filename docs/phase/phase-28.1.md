# Phase 28.1: Deliver Scala Metals and Lint Auto-Fix

Status: planned
Planned: 2026-11-23 (Asia/Tokyo)
Scheduled: immediately after [Phase 25](phase-25.md), followed by [Phase 18](phase-18.md)
Depends on: [Phase 28 local release](phase-28.md)
Checklist: [Phase 28.1 acceptance](phase-28.1-checklist.md)

## Goal and scope

Deliver the two unfinished Phase 28 capabilities in normal sm-workflow repair:
the Scala Metals connection (P28-02a, issue-13) and deterministic Scala
formatting/lint correction (P28-05, issue-14). For each, the workflow must be
able to obtain a supported edit, apply it to the candidate, and revalidate it
before falling back to AI repair.

Reuse Phase 28's deterministic patch and validation path, including the
released compiler correction. The original release, Session, review
dispositions, and evidence remain historical records. The
[adopted development priority](README.md#adopted-development-priority--2026-11-24)
places Phase 28.1 after completion of the Phase 25 work in progress.

## P281-01 — Connect Metals to ordinary repair

Reuse the `project.yaml` Metals settings and `ScalaMetalsRepair` adapter.
Resolve the configured server identity to a supported Metals executable
through the packaged workflow's provider registration and existing launch
admission. The default enabled setting allows selection of an admitted
provider when a diagnostic needs it. Launch still requires that admission.
Connect the adapter to a bounded process owned by the workflow, scoped to the
selected workspace and candidate. Check the existing CNCF process and
workspace capabilities first. If a one-shot process is the available boundary,
use a workflow-owned companion for the LSP exchange and return typed code
actions to the existing patch admission path. The process owns its lifecycle
from initialization and document synchronization through shutdown and cleanup.

Ship at least one production allowlist rule for a supported Metals quick fix.
Use the original diagnostic, negotiated capabilities and document version to
select an unambiguous, semantics-preserving edit. Reject stale versions,
out-of-scope edits, and arbitrary LSP commands. If the server is disabled,
unavailable, times out, lacks the required capability, or has no eligible
action, retain the diagnostic and continue through the existing applicable
repair path.

## P281-02 — Apply deterministic formatting and lint corrections

Connect the existing Scala terminal-newline correction and at least one
supported, allowlisted formatting or lint correction from the configured
Scalafmt, Scalafix, or lint route. Select the tool rule and its eligible input
in a representative fixture. Derive a scoped patch from the tool output within
an isolated copy of the admitted source when the tool needs to write files.
Use the project's effective lint and formatter configuration. Limit changes to
the diagnosed, admitted handwritten Scala files. Preserve Phase 29's ownership
of lint-rule and semantic-justification policy. Automatic suppressions,
generated files, and whole-project rewrites are outside this delivery.

Use the same candidate-bound patch application for Metals and formatting/lint.
Apply through existing revision, path, and byte checks. Re-run the originating
validation and any affected focused checks. On failed revalidation, use the
existing rollback path and retain the original diagnostic, attempted fix, and
check result. Preserve uncertain results and existing retry accounting. Use
the ordinary AI fallback for unresolved cases under its selected provider policy.

## P281-03 — Verify and deliver the packaged workflow

Exercise both capabilities through the normal packaged repair entry point in
isolated Scala fixtures. For Metals, use a controlled LSP subprocess through
the production transport, provider selection, and allowlist rule. For
formatting/lint, use the configured tool and a fixture that starts with the
selected diagnostic. Verify actual application,
successful revalidation, and that eligible successful cases require no AI
repair. Cover unavailable Metals, an inadmissible edit, and rollback after
failed revalidation. Reuse applicable Phase 28 evidence.

Review and release both usable paths together. A configuration setting,
isolated adapter test, or generated patch alone does not satisfy delivery.
Confirm actual installed Metals and real Scala projects after release. The
broader compatibility, baseline, and latency/AI-call measurements remain in
the [Phase 28 follow-up record](phase-28-checklist.md).
