# Phase 7.2: RepositorySync FixCheck and Temporary Dependency Worktree Retirement

Status: planned
Planned: 2026-11-24
Scheduled: immediately after [Phase 28.2](phase-28.2.md) and before [Phase 18](phase-18.md).
Execution started: no
Owner: sm-workflow-dev development; RepositorySync owns retirement.
Depends on: [Phase 7](phase-7.md) managed dependency delivery and direct synchronization.
Checklist: [Phase 7.2 acceptance](phase-7.2-checklist.md).

## Goal

Retire an explicitly temporary, Phase-owned dependency Git worktree after
RepositorySync verifies that its accepted source has reached the configured
GitHub target branch. The same rule applies when automatic post-Phase sync
succeeds and when an operator later runs RepositorySync manually after an
automatic sync failure, deferment or unknown outcome.

Phase closure remains local: complete and commit the Phase without waiting for
GitHub synchronization. A failed or deferred synchronization, or incomplete
retirement, does not reopen the completed Phase.

Give build and RepositorySync validation the same deterministic FixCheck repair
behavior. Each route has its own option, enabled by default.

## Delivery groups

| Step | Work | Observable outcome |
| --- | --- | --- |
| P72-01 | Temporary worktree ownership and intent | When a Phase explicitly selects temporary local dependency development at Start or during execution, prepare its dedicated worktree from the selected GitHub main tip through the existing managed workspace owner. Support the agreed local `.codex-workflow/checkouts` placement through the workspace mapping, not a hard-coded RepositorySync path. Record the exact worktree/repository, target branch, Phase, and explicit retire-after-sync intent. |
| P72-02 | Verified synchronization and retirement | Reuse the existing direct RepositorySync route. After a verified remote result and successful safety checks, retire only the bound temporary worktree through Git. Automatic and manually invoked RepositorySync use the same persisted intent and post-sync path. |
| P72-03 | Deferred recovery and installed qualification | Preserve the worktree and intent across sync failure, deferment, conflicts, unknown effects or unsafe retirement. Qualify later manual sync with automatic retirement, crash/reconnect and protection cases on disposable repositories. |
| P72-04 | FixCheck selection and shared repair | Keep the existing build option and add an independent RepositorySync option. Both default to enabled. Use one deterministic repair and revalidation path, and return an error if blocking diagnostics remain. |

## P72-04 — FixCheck selection and shared repair

- Keep the existing build-time FixCheck option and add an independent
  synchronization-time option. Both default to `true`. An explicit `false`
  skips FixCheck and its automatic repair for that route. Compile and test
  selection follow their existing impact rules.
- In RepositorySync, select only Scala files whose program logic the merge
  combines or edits. Apply this rule to both local-main integration and the
  remote merge. A complete file carried through from either parent, including
  an added file, is outside FixCheck scope. A proven ordinary comment or
  source-history-only difference is also outside scope. Skip FixCheck when
  there are no eligible files.
- Share the same implementation for build and synchronization. Reuse the
  deterministic Scala cleanup and candidate-bound patch path from
  [Phase 28.1](phase-28.1.md#p281-02--apply-deterministic-formatting-and-lint-corrections).
  Use the project's effective rule configuration. Apply only eligible
  deterministic corrections within the selected scope and rerun the
  applicable checks on the resulting candidate.
- If automatic repair cannot clear all blocking diagnostics, return an error
  and retain the diagnostic and candidate state. End the automatic repair
  attempt at that point. A failed synchronization candidate cannot proceed to
  commit, push or worktree retirement. The existing local Phase closure
  remains valid.

## Boundaries

- Bind cleanup to the accepted source revision and exact configured target. A
  push attempt, local remote-tracking ref or generic success notification is not
  proof. Verify authoritative remote inclusion of the selected result and that
  later work in the worktree would not be lost.
- Before retirement, verify current ownership and identity, no tracked or
  untracked user changes, and no running or resumable work that still uses the
  worktree. Use Git-aware non-forced worktree removal. Preserve evidence,
  artifacts and unrelated branches; do not sweep existing checkouts or independent
  clones. Keep permanent or caller-supplied worktrees unless separately admitted.
- Retain and report the synchronization and retirement outcomes separately.
  A retirement refusal remains recoverable; retry through the existing owner,
  without replaying already accepted Git effects. A later manual RepositorySync
  needs its existing sync/push authority but no second delete instruction for the
  explicitly opted-in worktree. A raw Git push is not a RepositorySync trigger.
- Reuse current Phase, workspace, authority and RepositorySync records. No
  automatic retry loop, forced push, Phase reopening, or changes to the
  project-scoped JAR route. [Phase 7.1](phase-7.1.md) source-project integration
  stays on hold. [Phase 22](phase-22.md) operational qualification is unchanged.

Registration does not select a rollout slot, start a Session, synchronize any
remote or authorize removal of current worktrees.
