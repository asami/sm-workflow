# RepositorySync uncommitted-change policy for Phase 8

Date: 2026-11-08 (Asia/Tokyo)
Status: selected planning item; implementation and acceptance pending
Owner: sm-workflow Phase 8

## Observed need and decision

The user reported that a caller chose `cncf-repository-sync` because automatic
commit of uncommitted changes did not match the requested synchronization.
The user wants Human-directed blocker resolution followed by actual progress,
while preserving automatic purpose-based commits as the ordinary default.
The selected design is a three-valued public parameter, `uncommittedPolicy`:

- `auto-commit` (default): accept and commit intended coherent change purposes,
  then synchronize using the existing runtime route.
- `confirm`: show the actual blocker, proposed resolution and affected changes;
  obtain the real Human decision, apply the authorized resolution, perform the
  necessary validation/commits and continue in the same Session.
- `error`: preserve changes and completed work, report the precise blocker and
  return an error without automatic correction or dirty-change commits.

This selects a Phase 8 requirement; it does not establish a currently supported
argument or change the behavior of existing Sessions.

## Current contract and implementation boundary

The [runtime orchestration specification](../../../spec/repository-sync-runtime-orchestration.md)
and [directory-list specification](../../../spec/repository-sync-directory-list.md)
define purpose-based preparation followed by clean synchronization. Excluded
ongoing work is preserved and can produce an unfinished local-preparation
outcome. The current public Start input does not expose a dirty-change commit
policy. `syncMode` is a separate synchronization choice; internal clean-route
declarations are not public switches for disabling automatic commits.

Extend the normal public input/configuration and existing runtime continuation
route for both single-repository and directory-list callers. Retain the effective
policy in the existing Session context; adopt any later change through the
supported public decision route. Keep automatic commits limited to intended,
authorized scope with existing acceptance. A no-dirty-commit selection does not
ban a necessary merge commit for already committed history.

Human resolution must address the actual remaining blocker. If the chosen
preservation leaves safe integration impossible, explain that unfinished
condition instead of claiming success. Preserve partial staging, excluded work,
other owners' edits, existing Goal/Session, history, repair counts and accepted
results. Reuse successful checks and continue only unfinished effects. Keep
unknown/live effects distinct from authorization for a new action.

Do not switch to another skill or direct Git execution because the requested
policy is missing. Do not add a second workflow controller, approval ledger or
mandatory per-repository confirmation loop to the default route.

## Planned acceptance scenarios

- [ ] Omitted policy and explicit `auto-commit` both retain purpose grouping,
      appropriate acceptance and commits before normal synchronization.
- [ ] `confirm` inventories actual staged/unstaged/untracked blockers, obtains
      an actual scoped decision, resolves it and reaches synchronization in the
      same Session without requesting the same approval again.
- [ ] A decision to preserve excluded work does not silently commit, discard
      or stash it; an unresolved integration blocker remains explicitly pending.
- [ ] `error` leaves dirty changes intact, returns a precise error and does not
      perform automatic local correction or dirty-change commits.
- [ ] Clean input proceeds normally under all three policies; invalid values
      return a configuration error before policy-dependent effects.
- [ ] Single-repository and directory-list routes resolve the same policy and
      retain completed per-repository outcomes without replaying successful work.
- [ ] Continuation retains Goal/Session, history, repair counts and applicable
      successful checks; new decisions and uncertain effects retain their normal
      handling. The caller does not substitute `cncf-repository-sync`.

## Documentation delivery

Added the selected requirement and unchecked completion criteria to
[Phase 8](../../../phase/phase-8.md#repository-synchronization-uncommitted-change-policy-2026-11-08).
Preserved the existing Phase 8 edits and planned status. Verification for this
delivery covers documentation whitespace and added local links only. No runtime
implementation, workflow execution, SBT validation, commit or publication is
claimed by this planning update.
