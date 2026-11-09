# RepositorySync: local main integration before GitHub main

Date: 2026-11-09 (Asia/Tokyo)
Status: user-selected default; implementation and installed acceptance pending
Owner: sm-workflow RepositorySync / Phase 8 extensions

The user requested that synchronization with main, when initiated from a
working worktree, first synchronize with the local main worktree and then
synchronize that main with GitHub main. The local canonical development
checkout is part of the integration path, rather than an optional copy to
update after a direct remote push.

Recorded the [target contract](../../../spec/repository-sync-local-main-integration.md)
and added the selected behavior and completion criterion to
[Phase 8](../../../phase/phase-8.md#repository-synchronization-local-main-before-github-main-2026-11-09).
The normal main-checkout route skips the local hop; requests for another
tracking branch keep their explicit target. Both affected checkout states use
the existing ownership/dirty-work decisions, including the separately planned
three-way uncommitted policy. No new approval/controller or caller-side Git
fallback is selected.

Current single-repository declarations bind one checkout/local branch/tracking
target. The directory-list route composes selected checkout members. These
representations do not by themselves establish the requested two-hop main
integration. The source checkouts inspected here also include a separate
development clone, so linked-worktree discovery alone cannot stand in for an
explicit canonical-checkout mapping in every environment.

The implementation must reuse the existing RepositorySync and typed Git owners,
preserve successful local integration if later remote work fails, and keep
existing Goal/Session, accepted results, history, checks and repair counts.
Running Sessions are not silently retargeted by this planning decision.

## Follow-up: property-controlled selection

The user subsequently requested property-based control. Added the String
property `sm-workflow.repository-sync.main-integration` to the target contract:
`local-first` is the default when omitted, while `direct` selects the normal
working-checkout-to-requested-GitHub-main route. Direct mode does not require
or implicitly update a separate canonical main checkout. This choice controls
the integration path, independently of dirty-change policy and remote/branch
selection.

The planned implementation reuses normal home/project and Component resolution,
rejects invalid values, and retains the selected effective mode in the existing
Session. It does not silently change a running Session after a property edit
or use direct mode as an automatic fallback when local integration fails.
Phase 8 and the acceptance scenarios initially covered both values and configuration
resolution. No actual configuration file or installed runtime was changed.

## Follow-up: Human route selection

The user requested a Human-selection option as well. Added `confirm` as the
third property value, keeping `local-first` as the default. The runtime presents
the actual source/main/remote targets and each route's effects before sync
commits, merges or pushes. It records the real route choice and continues in
the same Session. The retained choice applies to the remaining work without
asking again at every operation or continuation. Defer/cancel and no answer do
not authorize a default route.

Route selection does not replace the independent uncommitted-change decision.
Existing authority, Goal/Session, history, accepted results and repair counts
remain intact. Both entry routes use this behavior, with shared-target choices
reused in directory-list synchronization. The target specification, Phase 8
and unchecked acceptance scenarios have been updated; implementation remains
pending.

This delivery changes planning/specification documents only. It does not
implement or activate the new default, perform synchronization, start a workflow,
change branches, commit, push, or alter unrelated in-progress source changes.
