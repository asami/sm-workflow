# GoalTask public runtime contract

Date: 2026-10-27 (Asia/Tokyo)
Status: P67-T2 source implementation; native validation, actual Task commit episode and installed acceptance pending

`GoalTaskService.startGoalTask`, `advanceGoalTask`,
`submitGoalTaskWorkResult` and `resolveGoalTaskDiagnosticInformation` are ordinary
business operations. `NativeWorkflowPreparationService.prepareGoalTaskStart`
passively prepares the selected input import. Neither preparation nor a pasted
result supplies Human provenance or execution authority.

A `GoalTaskInput` adopts repository/request identity, objective, owned paths,
selected checks, ordered condition identities, source references, review policy,
code/documentation kind, Scala lint policy, repair limit, `sourceKind` (`journal`
or `direct-instruction`) and dependency references. The adopted values are frozen
by complete-input selection admission. Later journal edits and suggestions do not
silently change them. Source prose is a semantic requirement input; it is not
executed as a command or used as private runtime storage. No Phase number, Plan
or Phase-management file is required. The first route has one coherent work unit.

The Task has its own derived `GoalTaskWorkflow` definition and codecs. It uses
`BoundedWorkNativeRun`, the same native progression owner used by standalone FIX,
and `StandaloneFixExecution` for retained candidate/check/diagnostic/repair policy.
An initial `Implement` WorkOrder needs no prior failure and consumes no repair
allowance. Native Advance captures the candidate and runs the selected typed
checks. Actual failures issue the existing bounded `Repair` WorkOrder; incomplete
or unknown results wait without fabricated test success. Empty focused execution
and compile-only results cannot establish code success.

After checks, one independent integrated `Review` WorkOrder covers the adopted
objective, conditions and actual candidate. Its evidence uses the common scoped
independent-review admission, binding this actual Task input and continuation.
All recorded implementation/repair workers are excluded as Task reviewers. Review
findings stay in the same request and repair counter window. Affected review uses
retained prior findings/candidate and applicable validation; it adds no second
Phase or Release review. An unchanged candidate reuses applicable checks.

Task review acceptance reaches `CommitReady`, whereas standalone FIX acceptance
remains `Accepted` and uncommitted. The owning native call requires the host's
registered typed Git `goal-task` selection for `CheckSelectedIndex`,
`SelectedIndexChanges` and `CommitSelected`, with actual scoped physical paths,
message and commit identity. It never substitutes `goal-step-N`. Staging uses the
existing host-bound `goal-path-N` selections. The normal authority registration
route must admit these bindings; missing bindings remain an actual capability
gap. Source implementation does not install them into an active Phase host.

The first Task route requires clean selected ownership at Start to avoid silently
capturing pre-existing changes. It retains unrelated unstaged/untracked status
and preserves that work throughout. Unrelated staged changes are refused before
staging or commit; this route does not unstage or silently include them. Candidate
capture also refuses unrelated staged paths and ownership-crossing renames. It
stages only existing host-bound selections; necessary additions use that same
ordinary registration boundary, and real scope/effect expansion retains Human
admission. Broader dirty-selection support remains allocated separately.

The native runtime persists commit intent before the typed effect and retains the
actual execution outcome, physical paths and process output afterward. Closure
requires observed single-parent commit metadata with the reviewed tree and the
original parent, plus clean selected ownership. It returns the actual commit in
retained Task state. `CommitFailed` stays incomplete; `CommitIntent` and
`CommitUnknown` observe a matching commit without replaying the effect. An
unmatched or missing commit never becomes success or permission to retry. A
reviewed empty delta is `NoChange` and creates no empty commit.

The existing common Evidence archive and Journal projection receive actual
check, semantic and terminal facts. Archive/projection failure cannot undo a commit or authorize replay. Task terminal
state retains `projectionWarning` when an archive, Journal projection or required
installation is unavailable, separately from actual commit completion. Projection
references may then be absent; no effect is repeated to obtain them.

`GoalTaskWorkflowSpec` contains bounded fixture-owned check/commit admission and
an isolated public Start/Advance episode using typed Git observations. These are
executable specifications, not evidence of an installed Task, an actually executed
Task behavioral check batch, independent review, or committed delivery. The
owning runtime performs selected validation. Phase 6.7 checklist conditions remain
open until their actual phase-scoped observations exist. Existing Phase/standalone
schemas and optional-field decoding retain old Sessions, counters and attribution;
no migration, restart or accepted Submit replay is introduced.
