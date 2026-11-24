# Phase 28.1: Deliver Scala Metals and Lint Auto-Fix

Status: closed
Closed: 2026-11-24 (Asia/Tokyo)
Planned: 2026-11-23 (Asia/Tokyo)
Scheduled: immediately after [Phase 25](phase-25.md), followed by [Phase 25.1](phase-25.1.md)
Depends on: [Phase 28 local release](phase-28.md#formal-local-release)
Checklist: [Phase 28.1 acceptance](phase-28.1-checklist.md)

## Human-approved formal local release — 2026-11-24

The user approved a FORMAL local release under the
[Phase 8 precedent](phase-8.md#human-approved-formal-release--2026-11-19).
Additional release validation and independent review are **NOT RUN —
USER-APPROVED**. The local release Step plan contains the accepted P281-01 and
P281-02 commits `42f406ef` and `41dd8097`. The release also includes the
implemented P281-03 candidate with its outstanding native disposition.

In native Session `edba64e3bcfb3384e026e6d1b0ac4d18`, Plan
`4-packaged-complete-scala-cleanup-inventory-4`, validation revision `1137`
tested candidate tree `28a6af056b57fe0ff62f02a1713df01b0ab514d1`.
Compile and all 27 focused tests passed. FixCheck exited with code 1 and
reported 557 affected diagnostics. The issue remains `OPEN` with disposition
`BLOCKED`, and the Session retains Human1146 and the repair count of 7/10.
Native P281-03 acceptance and a native Phase terminal transition remain absent.

Comparison analysis that could not be established and migration to stricter
comparisons are assigned to Phase 28.2. Other unresolved diagnostics remain
open under issue-8. The adopted follow-on order is Phase 25.1 → 25.2 → 28.2 →
7.2. Comparison migration is a Phase 28.2 obligation. Operational qualification
on actual development projects follows local release.

## Goal and scope

Deliver the two unfinished Phase 28 capabilities in normal sm-workflow repair:
the Scala Metals connection (P28-02a, issue-13) and deterministic Scala
formatting/lint correction with both Scalafix and Scalafmt (P28-05, issue-14).
Each tool participates in ordinary repair when applicable. The workflow
applies supported edits to the candidate and revalidates before falling back
to AI repair.

Reuse Phase 28's deterministic patch and validation path, including the
released compiler correction. The original release, Session, review
dispositions, and evidence remain historical records. The
[adopted development priority](README.md#adopted-development-priority--2026-11-22)
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

In ordinary repair, request only the Metals `quickfix` kind and apply only
eligible quick fixes. Exclude LSP `refactor.*` and `source.*` actions from
this path, including `source.organizeImports` and `source.fixAll`. Use the
project's admitted Scalafix rules for import organization where configured.

## P281-02 — Apply deterministic formatting and lint corrections

Connect the existing Scala terminal-newline correction and both tools to the
regular repair path.

The ordinary sequence is **applicable compiler-error repair → source cleanup →
final validation**. Compiler-error repair tries Metals Quick Fix and then
eligible Scalafix rewrites for the remaining diagnostic. Source cleanup runs
eligible Scalafix cleanup rewrites, enabled source-history normalization and
Scalafmt in that order. Each operation consumes the candidate left by the
preceding edit. Before Scalafix semantic rules, reuse compiler information
for that exact candidate if available. If an edit made it stale, refresh it
through the existing compile route only when a selected semantic rule needs
it. Final validation applies to the resulting candidate, including the
originating check and any affected focused checks.

Use the same source cleanup stage for ordinary implementation candidates before
their existing final validation, even when compilation has no errors. Select
admitted changed handwritten Scala files. Use the effective settings for each
operation. Skip an empty selection and reuse applicable validation evidence
when cleanup leaves the candidate unchanged.

- **Scalafix:** Use the same effective project lint configuration and rule
  identities as the existing lint check. In compiler-error repair, select
  only applicable rewrites whose purpose is to fix the diagnosed error.
  In source cleanup, apply the project's eligible behavior-preserving rewrites
  to the selected files, such as import organization or redundant syntax
  removal. Keep check-only rules as diagnostics. Preserve Phase 29's ownership
  of rule and annotation semantics.
- **Source-history comments:** In the formatting stage, reuse the existing
  normalizer and [effective history selection](../spec/scala-lint-profiles.md#source-style-and-history-override).
  Apply it only where the project uses source-history management and only to
  eligible changed Scala files. A project with effective style `none`,
  including an explicit override of a team profile, skips normalization.
  Use the existing date, authorship and history-preservation rules. Run this
  before formatting so final checks cover both changes on the same candidate.
  Reuse the completed normalization at later commit preparation when it still
  applies to that candidate.
  The 2026-11-24 decision assigns the update and conformance check to the
  workflow program. Align the applicable `ai-directive` rules and skills when
  the automatic route is delivered. Implementation assignments omit manual
  header updates, and review and commit preparation consume the program's
  result without asking AI to repeat the check. Report failures through the
  existing recovery path. Apply the change in responsibility to routes covered
  by automatic update and checking.
- **Scalafmt:** Apply the project's configured formatter to selected handwritten
  Scala files that need formatting. Re-run the formatter check and verify
  idempotence. Follow the existing
  [Scala 3 structural layout rule](../spec/scala-lint-profiles.md#structural-layout-braces-outside-expression-leaves).
  Preserve explicit braces for structural definitions and DSL composition, and
  preserve permitted indentation syntax in terminal implementation expressions.
  Keep blanket optional-brace removal disabled and rerun `NoStructuralIndentation`
  on the formatted Scala 3 candidate under the applicable lint profile.

Derive scoped patches from tool output in an isolated copy of the admitted
source when a tool writes files. Limit edits to admitted handwritten Scala
files. Preserve scope checks for both tools and leave automatic suppressions,
generated files and whole-project rewrites outside this delivery.

Use the same candidate-bound patch application for Metals and formatting/lint.
Apply through existing revision, path, and byte checks. Re-run the originating
validation and any affected focused checks. On failed revalidation, use the
existing rollback path and retain the original diagnostic, attempted fix, and
check result. Preserve uncertain results and existing retry accounting. Use
the ordinary AI fallback for unresolved cases under its selected provider policy.

## P281-03 — Verify and deliver the packaged workflow

Exercise Metals, Scalafix and Scalafmt through the normal packaged repair entry
point in isolated Scala fixtures. Also exercise formatting through ordinary
implementation with no compiler errors. For Metals, use a controlled LSP subprocess
through the production transport, provider selection and allowlist rule. For
Scalafix and Scalafmt, verify each tool independently. Also exercise one
candidate through the complete ordinary tool sequence. Verify actual application,
successful revalidation, and that eligible successful cases require no AI
repair. Cover missing or unavailable tooling, an inadmissible edit, and
rollback after failed revalidation. Reuse applicable Phase 28 evidence.

Review and release all three usable tool paths together. A configuration
setting, isolated adapter test, or generated patch alone does not satisfy
delivery. Confirm installed Metals and both configured tools on actual Scala
projects after release. The broader compatibility, baseline, and
latency/AI-call measurements remain in the
[Phase 28 follow-up record](phase-28-checklist.md#deferred-from-the-local-release).

## Dedicated refactoring API follow-up

Provide a dedicated sm-workflow API for explicitly requested refactoring,
such as cross-file symbol renames, extraction and structural reorganization.
The request identifies the transformation and its scope. Reuse existing
workspace ownership, candidate patch, validation and Admission contracts.
Return a reviewable change and its validation evidence. This API is a
separate follow-up from the Phase 28.1 automatic path.
