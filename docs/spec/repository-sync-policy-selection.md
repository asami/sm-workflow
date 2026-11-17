# RepositorySync policy selection and retained Human direction

Status: P8-02 source and executable specifications authored, 2026-11-18.
The complete candidate's native compilation, checks, independent Step review and
installed qualification remain with the parent workflow. The selected contract is
[local main integration](repository-sync-local-main-integration.md).

The ordinary Component properties are
`sm-workflow.repository-sync.main-integration` (`local-first`, `direct`,
`confirm`) and `sm-workflow.repository-sync.uncommitted-policy` (`auto-commit`,
`confirm`, `error`). Their omitted values are `local-first` and `auto-commit`.
Optional ordinary input fields `mainIntegration` and `uncommittedPolicy` select
the value for a new call after normal Component configuration resolution.
Empty, malformed and unknown values fail configuration. A Session captures the
selection in its original native Start; directory-list intake captures it before
starting children and forwards those same values. Later configuration does not
replace them. Historical Starts without this capture retain their old scope.

An explicit non-main destination retains its configured branch. A call already
on main has one remote route and requires no artificial route-choice question.
Managed dependency remote-main selection requires scoped `direct`; an explicit
conflicting configured route is refused before effects, including directory
children. Other local dependency checkouts are not collected.

At the original pre-fetch frontier, `error` returns `Error` with condition
`UncommittedChanges` and the actual dirty inventory. It neither commits dirty
work nor appends a fetch/purpose intent. `auto-commit` retains ordinary semantic
purpose grouping, exclusion, candidate validation and independent review; it is
not blanket acceptance of every dirty path.

`confirm` uses an actual native Decision, public
`submitRepositorySyncPolicyDecision`, atomic completion and independent host
Human evidence under `repository-sync-policy-human-answer`. The ordinary Human
preparation route (`family=RepositorySync`, `kind=human`) accepts the retained
context and supplied `RepositorySyncPolicyDecisionAnswer`. Preparation is not
consent or provisioning. The answer contains `choice` and a nonempty `rationale`.
Submit records direction only; separate Advance owns later effects.

The dirty-work question offers `AutoCommitPurposes`, `Defer`, `Cancel`.
`AutoCommitPurposes` continues through ordinary purpose grouping in the same
Session, with actual source HEAD/tree/status/index checked against the question.
It does not accept excluded work or waive checks or review. Unanswered questions
redisplay without another history entry. Retained Defer and Cancel remain
distinct waits. Actual accepted answers remain with the native Decision owner;
no approval ledger is introduced.

## Canonical participant and retained two-hop route

The delivered CNCF `GitOperation.Worktrees` supplies native read-only metadata.
It does not grant another workspace. A new main-directed work-branch Start
captures the source, admitted canonical main and exact selected remote. Linked
main selection distinguishes missing, ambiguous and prunable entries. The
selected source and canonical checkout must match native roots and branches;
registered endpoints and actual remote advertisements must agree.

For separate clones, normal Component property
`sm-workflow.repository-sync.canonical-main-workspaces` is a JSON object mapping
the source workspace identity to its canonical-main workspace identity, for
example `{"project-work":"project-main"}`. Both are existing admitted typed Git
repository/workspace bindings. Actual commit metadata must establish shared
history before mutation; mapping, directory names or matching configured URLs
alone do not establish clone identity. The canonical clone also needs one
unambiguous registered source-fetch workspace at its own root, with the selected
source checkout as its fixed endpoint and the selected source branch as its
remote branch. The existing typed Fetch imports that captured contribution;
no shell command, dynamic endpoint override or source push is introduced.

The original Start remains immutable. Mechanical phase receipts append to the
same native Session and retain its history and repair accounting. Source purpose
preparation precedes canonical integration. The local hop is one-way and uses
its explicit captured source tip, not GitHub tracking, for relation and merge
assessment. Its receipt namespace distinguishes that relation from the later
remote hop. Typed commit metadata provides complete reachable histories, bounded
to 100,000 unique commits per history; missing proof, exhausted bounds or an
unestablished common ancestor fail rather than imply equality. Immutable object
observations are reused within the current call only.

Existing preservation, integration, impact selection, structural admission,
semantic conflict/review/repair and typed commit owners apply to the admitted
participant. Local success records the source and integrated canonical tip but
does not push, publish, close the synchronization or advance the source branch.
Only canonical-main/remote convergence uses normal non-force push and closes the
Session. Remote failure retains the local phase and resumes only its unfinished
suffix. Native uncertain intents retain observation/settlement, never blind
replay or direct fallback. Final output reports source contribution, retained
local integration and actual remote outcome separately.

`confirm` offers `LocalFirst`, `Direct`, `Defer`, `Cancel` when canonical main is
available. Otherwise the actual setup reason remains visible and only the
available direct/defer/cancel routes are offered. A route answer does not waive a
separate dirty-work question on either admitted participant. Historical Starts
without a captured participant plan keep their existing scope and are not
silently upgraded.

Directory intake resolves all declared participants before starting children.
Each existing source child keeps one native Session across its hops. A coherent
shared-target cohort retains one actual Human route decision naming its sources
and target; later members read that exact native completion rather than asking
again or installing another approval ledger. Local contributions execute under
the existing aggregate's expected-revision ownership. Remote convergence waits
for declared local contributions; one source owns the remote suffix, and other
members wait for its normal completion, including any failed/unknown settlement.
An explicitly listed main member keeps its own input, and remaining source
Sessions collect the then-equal target without duplicate pushes or local replay.
No machine-wide lock or other-worktree dirty inventory is introduced. Conflicting
canonical identities remain `SharedTargetSetupRequired` before children/effects.

A known pre-effect dirty `error` can be explicitly refreshed after resolution;
normal collection also observes a child that progressed through its own public
operation. Failed/UNKNOWN effect intents receive no such setup retry shortcut.

`RepositorySyncPolicyContinuationSpec` authors isolated native Git/DataStore
examples for pre-fetch dirty refusal in both entries, actual work-branch setup
setup refusal, unanswered decisions, refusal without independent Human evidence,
specification-owned answer provisioning, and same-Session continuation after a
configuration change. `RepositorySyncLocalMainSpec` adds linked/mapped-clone
two-hop histories, explicit/direct isolation, actual Human and purpose completion
with excluded work, coherent shared-target batching and remote-read failure
continuation after local success. `RepositorySyncPoliciesSpec` covers selection
and shared identity refusal. All repositories, remotes and Human evidence in
these examples are specification-owned; no live GitHub push, consumer PASS or
Step acceptance is asserted here.
