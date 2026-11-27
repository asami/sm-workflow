# Phase 7.2 acceptance checklist

Status: planned; all items OPEN; execution not started.
Plan: [Phase 7.2](phase-7.2.md).

## P72-01 — Temporary worktree ownership and intent

- [ ] An explicitly selected temporary dependency worktree can be prepared at Phase Start or later through the existing workspace owner, from the verified configured GitHub main tip without altering another local main.
- [ ] Bind the exact owned worktree, repository, Phase, remote and branch, and explicit retire-after-sync intent. Capture the accepted source at local closure. Reconnect retains the same binding; ordinary registered worktrees are not converted to temporary ones.

## P72-02 — Verified synchronization and retirement

- [ ] Local commit and Phase completion stand without remote success. Connected post-Phase automatic RepositorySync verifies the source is on the exact configured remote branch and then retires the selected worktree without a separate delete request.
- [ ] Before removal recheck the exact worktree's identity, remote inclusion, cleanliness and absence of active or resumable references. Unpublished later commits, dirty/untracked user files, active work, pinned references, other branches, clones and mismatched targets are protected. Use normal, non-forced Git worktree removal and retain delivery/validation evidence.
- [ ] Sync success and retirement outcome are separately visible. A failed removal retains intent and a reason without undoing a verified sync or closing another worktree.

## P72-03 — Deferred recovery and installed qualification

- [ ] Automatic sync failure, conflict, deferred or unknown outcome leaves both worktree and intent intact. A later authorized manual RepositorySync for that same binding verifies success and triggers automatic retirement, without reopening the Phase or requiring a new deletion instruction.
- [ ] Reconnect, a repeated successful manual sync, and interruption at the retirement boundary converge safely; do not duplicate a push, delete a replacement checkout or turn an unknown result into success.
- [ ] Qualify the installed RepositorySync route with disposable Git repositories and worktrees. Record connected positive and protected cases, required checks and independent review before closure; do not test by removing a user's existing checkout.

## P72-04 — FixCheck selection and shared repair

- [ ] Verify the existing build-time option and the independent RepositorySync option both default to `true`. Explicitly setting either to `false` skips its FixCheck and automatic repair. The other route and the applicable Compile and tests still run according to their own settings.
- [ ] Exercise both local-main and remote merge selection. FixCheck receives only Scala files whose program logic was combined or edited. Cover a clean merge that combines logic, an added or modified source carried through from one parent, and a proven ordinary comment or source-history-only change. A candidate with no eligible files skips FixCheck.
- [ ] Through both build and RepositorySync, apply the same supported deterministic correction, bind the edit to the selected candidate and revalidate successfully. Verify that files outside the selected scope are untouched.
- [ ] Through both routes, a remaining blocking diagnostic returns an error and retains its evidence. Verify that automatic repair stops and that a failing synchronization candidate produces no commit, push or retirement.
