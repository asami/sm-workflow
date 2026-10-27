# GoalTask public runtime contract

`GoalTaskService.describeGoalTaskMalformedIssuedWorkRecovery` reads an exact
active native WorkOrder and its original business checkpoint. Its input is the
Session and old continuation identity. Eligibility requires both missing
`reasoningClass` and missing `disposition`; valid and partially classified issues
are refused. The description freezes the complete original native frontier,
immutable issue, participant, business input, base, checks, evidence, counters,
current corrected requirement and mapping/origin. The concrete DTO carries this
target as opaque JSON text so property-name conversion cannot rewrite native
fields or arbitrary profile-control keys.

`recoverGoalTaskMalformedIssuedWork` re-reads that target and requires independently
provisioned evidence under `goal-task-malformed-issued-work-recovery`. The closed
choices are `AuthorizeOneNewAttemptAcceptingUnresolvedEffects` and `Defer`.
Authentication, a source locator, passive preparation, issuer configuration,
Available/unclaimed state and missing logs provide no Human consent or
nonexecution proof. The evidence Reader binds the exact target, participant,
Human source/reference, answer and occurrence time. Known native claimed or
completed work is refused even with Human evidence. No covering external
nonexecution provider is implemented here; this route retains effects as UNKNOWN.

Recovery uses the CNCF managed WorkOrder supersession transaction to retain a
typed checkpoint while marking the immutable old issue Superseded and appending
an unsuspended native frontier. The exact entity revision from the compared
inspection is a mandatory transaction precondition, so a concurrent claim/release
cycle is refused even if the continuation returns to Available. It executes no Git, SBT, implementation, review,
Submit or commit. Defer changes no native state. Only a verified transaction is
success; conditional refusal and transaction uncertainty propagate from CNCF.
After any failed or interrupted response, collect the same target's retained
disposition before deciding whether another operation is needed; a failed
response is never proof of rollback.

Ordinary `advanceGoalTask` collects the checkpoint, saves unchanged business
state and issues once through the existing corrected issue path. A changed
mapping/origin is refused. The replacement's retained context references the
actual predecessor at issuance; no future ID is returned as an issued successor.
Repeated Advance redisplays the actual suspended request. Repeated recovery
collects the committed disposition, including after replacement completion, and
reports a successor only after reading its actual retained issue. Native
completion rejects late old results. Start, accepted Submit replay, counter
reset and successful-effect replay are not recovery mechanisms.

Task review evidence preparation accepts family `GoalTask`, kind `review`, an
actual `GoalTaskView` and a `GoalTaskWorkResult`. It uses the existing independent
StandaloneReview port semantics while requiring Task business input and the
exact Task completion identity. Runtime completion still excludes all
implementation workers and requires independently provisioned review evidence.

Task Submit accepts optional typed `issuedWorkContext`. Any supplied context must
match the actual current issue's complete identity, revision, Session, contracts,
context and execution requirement. Once durable native history contains a
verified committed WorkOrder recovery, this parameter is required on every
subsequent Task Submit, including after completion, later progression and physical
reopen. The original Request's context must accompany its result; callers must
never renew a delayed result's target. Legacy Tasks without recovery history may
omit it. This is request correlation, not worker authentication or review evidence.
The finite caller helper forwards the actual Task Request's context automatically;
submission metadata cannot supply or replace it.

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
