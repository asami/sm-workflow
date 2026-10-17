# RepositorySync managed native Git observations

Date: 2026-10-15 (Asia/Tokyo)
Status: typed primary consumer migration completed bounded source-level parent
qualification (66 passing checks in eight suites); independent review pending.
NativeHost main retirement and complete installed tasks remain open; the typed
File consumer replacement below has separate pending qualification.

## Current contract

`WorkflowManagedProcessExecution.executeGitC` accepts only a closed CNCF
`GitOperation[A]` and the fixed logical `GitTarget` workspace
`sm-workflow-repository`. Its exhaustive adapter calls the protected `git_*`
methods and executes their typed intent in the actual owning call's current
UnitOfWork. The DSL constructs intent; CNCF's interpreter admits the actual
Component, current workspace binding and independent process grant before
execution. The primary constructs no Git ProcessExecutionRequest, argv,
executable, driver, context or generic fallback.

The trusted host supplies separate strict `cncf.runtime-process-execution.v3`
configuration. CNCF normal bootstrap decodes the public JSON, binds the existing
canonical repository directory, and owns executable, environment, finite
limits, fixed endpoint/branches, selected paths/messages and process lowering.
`RepositoryGitRuntimePrograms.Target` retains only business target fields for
existing scope/authority comparisons. It defines no programs or grants.
See [normal typed Git workflow](normal-typed-git-workflow.md) for selectors,
host fields, receipt compatibility and consumer responsibilities.

`RepositorySyncManagedGitObservation` maps CNCF typed branch, root, HEAD/tree,
status, shallow/replacement/merge state, tracking tip, history counts, common
ancestor and commit metadata into the existing business values. It does not
parse process output. Literal filename and rename ordering survive that mapping;
`GitStatusEntry.unmerged` owns conflict classification. Git object IDs remain
native repository facts. Persisted business String inputs are converted through
public `GitObjectId.parseC`/`GitBranchName.parseC` and independently revalidated
by the interpreter.

Application policy refuses shallow/replacement history, unrelated histories,
unsupported merge observations and automatic integration of a pending merge or
unmerged index. CNCF refuses malformed, multiple-base, incomplete and invalid
text output before it becomes a typed value. Missing tracking state carries no
invented count or convergence. Separate observations are not an atomic snapshot;
the owning workflow reobserves its selected physical target and preconditions.

Fetch belongs to the saved owning workflow intent, not the observation adapter.
Its fixed host endpoint and tracking destination preserve local files and do not
force, prune, write FETCH_HEAD or expand remote authority. Preservation,
integration and convergence consume typed results. Only the closed pending-merge
conflict outcome permits subsequent actual typed conflict observation; arbitrary
nonzero exits do not establish conflict. A negative result creates no confirmed
stage, and an uncertain saved intent is not automatically replayed.

The normal-bootstrap observation specification covers qualified real dirty/reopen,
local-ahead/diverged/unrelated/common-ancestor, fast-forward, pending-merge then
commit, nonforce/wrong-destination and missing-current-grant examples. Fixture
ProcessBuilder commands create only preselected local repository inputs; subject
Git calls go through the actual Component and current UoW. Producer decoder and
security guarantees are reused. Parent qualification passed 61 checks in seven
suites plus five representative public workflow checks in one suite, with zero
failed, aborted, canceled, ignored or pending checks. The public
Equal/LocalAhead/RemoteAhead first-loop and reopen results preserve terminal,
history and reflog; missing Fetch grant and unknown check intent/reopen without
replay also passed. This bounded source-level qualification is recorded in
[normal typed Git workflow](normal-typed-git-workflow.md); product-test assurance
and genuine installed operator acceptance remain unestablished.

The conflict and repair File consumers now use typed capture/observe/apply in
`RepositorySyncFileEffect` through the same `sm-workflow-repository` workspace.
`WorkflowManagedProcessExecution` retains only qualified typed Git/SBT adapters;
the generic File request/stdout route and both File product mains are retired.
This consumer source change does not inherit qualification from the earlier
Git-only checks. See [normal typed File workflow](normal-typed-file-workflow.md)
for the current File contract and remaining whole-workflow obligations.

## Historical evidence boundary

The following retained reports describe earlier generic ProcessExec source and
its then-current acceptance boundary. They do not qualify this typed migration,
normal V3 configuration, File migration or the remaining product launch routes.

## Historical executable evidence after the ancestry addition

Fresh988 passed294/294 in23 completed suites, zero failed/aborted/canceled/
ignored/pending, SBT0/wrapper0/sharedlockreleased. Parent admitted current typed
result/consumed permission and complete642-through988 history. Six managed Git
examples and four public native fetch examples ran. Actual local-ahead then
divergence retains the real ancestor; actual unrelated roots refuse observation
without changing HEAD or starting a merge. Multiple-base refusal has strict
capture/parser evidence, not an executed crisscross history. Seven shared
document-effect examples and the remaining RepositorySync/guard suites passed.

[Actual988 log](/private/tmp/skill.cncf.d/a-e585e2ce-e90b-4df3-aa15-6cdc5280051c/3701-20261006T010224Z.log).
Admission: `.codex-workflow/phases/PHASE-2/three-workflow-command-988-validation.json`.
This is an affected regression run;987 remains whole-family support for its
earlier input. Normal installation/integration/convergence are still unfinished.

## Historical executable evidence before the ancestry addition

Fresh registered runner request 984 passed 11/11 tests in two completed suites,
zero failed/aborted/canceled/ignored/pending, SBT0/wrapper0 and released shared
lock. Parent admitted the actual current typed result and consumed permission.

- Four RepositorySync scenarios cover actual Git effects/reopen, NUL-path and
  all-four relation parsing, incomplete/unknown output refusal, and missing
  capability grant/extra argv/invalid target/environment refusal.
- The actual effect scenario creates uniquely owned local/peer/bare repositories
  under `target/sm-workflow/work/repository-git`, using separately fixed,
  explicitly fixture-only setup capabilities. It executes real fetch and
  observes actual equal, remote-ahead and divergent histories, unchanged HEAD
  and dirty status across fetch, staged/unstaged/untracked content preservation,
  absence of FETCH_HEAD, and the same graph/files after a physically new runtime.
  Local-ahead classification has deterministic parser evidence here, not yet a
  standalone real-history progression/convergence scenario.
- Seven existing actual selected-document effect scenarios pass in the same
  invocation, covering compatibility of the shared canonical process adapter.

Actual log:
[/private/tmp/skill.cncf.d/a-2166757c-d95c-4456-be17-6c87538fbc52/76655-20261006T001150Z.log](/private/tmp/skill.cncf.d/a-2166757c-d95c-4456-be17-6c87538fbc52/76655-20261006T001150Z.log).
Admission: `.codex-workflow/phases/PHASE-2/three-workflow-command-984-validation.json`.

Request 983 compiled product sources but failed Test/compile on one fixture-only
anonymous-subclass widening error; no test or Git process ran. Parent replaced
that helper with a named SetupCall and retained the failure. Request 984 is a
fresh current-source validation, not a replay or counter reset. Complete consumed
history is 638 through 984. Original nine/R5/A7/null/repair3-of-3 are unchanged.

No installed Start/advance, durable fetch intent/recovery, clean merge/conflict
work issue/result, non-force convergence, whole GoalPhase closure or integrated
independent acceptance follows from these supporting results. They remain in
the connected Phase 2 implementation batch, not a helper acceptance cycle.

Subsequent [owning native fetch](repository-sync-managed-fetch.md) connects the
additive public Start/advance pair and CML Fetching state to these programs.
Fresh986 passed291/291 RepositorySync/common-guard/document-effect tests including
three real native public fetch/reopen/refusal episodes.984 alone never establishes
this later connection. Normal installation, semantic conflict and final non-force
convergence remain unfinished.
