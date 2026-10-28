# Phase 6.9 S4 dirty purpose capability boundary

Date: 2026-10-28. Historical pre-approval status: implementation decision needed. The inventory diagnostic and preservation specification are authored; no S4 compile, lint, test, review, publication or installed observation is claimed. P69-08, P69-09 and P69-10 remain incomplete.

The actual assignment is `016-s3-authorized-advance/response.json` under task `phase-6.9-01a1555d-b4a7-7be3-8232-5396c2f5e293` in `target/sm-workflow/runtime-phase-6.9-20261028/target/sm-workflow/tasks`. It retains Session `db7e363d6c9c44dfd14f8e0605288127`, Plan `phase-6.9-runtime-owned-repository-sync@1`, original base `5ac369a131d99a9be88608e9c170d122159fcefc`, and actual S4 continuation `db7e363d6c9c44dfd14f8e0605288127:ordered-implementation:256` at expected revision `257`. Its accepted S3 commit is `85c6cb0eb60c13755646f73ac711660220de5ca8`; its three selected checks passed in the retained native admission. These are predecessor facts, not validation of the present edits. All fourteen acceptance conditions have phase scope.

The parent supplied the current installed task skill at `inputs/installed-skill-s4/skills/sm-goal-phase/SKILL.md`; this assignment consumed its implementation, result, dependency, acceptance and caller-execution instructions. Adjacent `source.json` identifies normally selected image `3eeae363-5ded-4f49-96f8-f7156a25e6b4`, update `783ab7b8-b287-4ab5-9b89-59a1e90a34d4`, its normal CAR and manifest, and 36 extracted resources. This is actual task skill consumption, not an installed observation of S4 behavior. No executable skill script was run.

## Pre-approval source assessment and concrete missing capability

Source inspection located the CNCF producer at `goldenport-cncf` in the current workspace and read its actual `GitExecution.scala`, `GitSelectedCommitExecution.scala`, `ProcessExecutionModel.scala` and `GitExecutionSpec.scala`. At that initial assessment, no producer file had been changed or command executed.

| Existing owner | Capability and limit |
| --- | --- |
| Typed Git `Status`, `IndexTree`, `TreeChanges`, `TreeEntries`, `BlobContent` | Actual status, tree and blob observation; no creation of a selected content candidate |
| `GitSelectedCommitExecution` | Resolves changed physical paths inside admitted logical scopes, probes their worktree cleanliness, then invokes the admitted selected commit |
| `ProcessProgramDefinition.gitSelectedWorktreeProbeC` | Uses the closed `diff --quiet` probe; a selected path with staged and unstaged differences is refused before commit |
| `GitOperation.StageSelected` / `StageSelectedAll` | Whole-path staging; using these on a partially staged shared file would replace its staged intent |
| `GitOperation.CommitSelected` | Closed whole-path commit, protected by the cleanliness probe; does not commit a purpose-specific content projection |
| Native GoalTask / GoalPhase Step acceptance | Real candidate staging, checks, assessment and attributable acceptance; does not supply a shared-file candidate construction operation |
| `GoalPhasePartialCommitClosure` | Proof/reconciliation of an already successful partial commit and bounded known normalization; does not construct independent purpose candidates |
| Native validation, review, FIX, Journal and Evidence | Existing owners to reuse after a genuine candidate is available; neither a locator nor a successful check supplies purpose or review consent |

The producer's authored `GitExecutionSpec` case named “refuse a selected path with staged and unstaged changes before mutating its index or creating a commit” explicitly asserts failed exit 1 with unchanged HEAD, selected index changes and worktree. This is inspected source specification, not a newly executed producer test.

The closed `GitOperation` algebra has no selected blob/tree construction, isolated candidate index, or commit of selected content while retaining independent index/worktree remainder. `CreateCheckpoint` accepts an already existing tree and uses a fixed checkpoint message; it cannot construct a purpose tree or supply ordered useful purpose messages. Installing a whole-index checkpoint cannot implement this requirement.

The concrete counterexample is one tracked file with accepted staged purpose A and independent ongoing unstaged purpose B. P69-09 also requires two independent purposes within a shared file to become separate valid commits; P69-10 requires excluded ongoing content and staging intent to survive. Whole-file staging destroys that distinction; the guarded whole-path commit correctly refuses it. Coalescing genuinely inseparable purposes is valid, but declaring independent or excluded content inseparable would silently narrow the adopted requirement. This gap requires a producer contract decision rather than weakening the existing protection or adding a shell executor to sm-workflow.

The accompanying typed `IMPLEMENTATION` dependency proposal requests a bounded CNCF capability for purpose-specific candidates with explicit expected parent/index/worktree facts, finite admitted content/paths, useful purpose messages, existing Git/UoW authority, preservation and unknown-effect handling. It does not prescribe another controller or ledger. Its Component/workspace names are proposed logical dependency coordinates, not evidence of host registration or authorization. The saved Plan reference identifies the actual Usage Contract being served. Dependency scope, host bindings, publication and any execution remain subject to the existing native dependency route; no cross-repository permission is inferred from Phase start.

## Initial preservation-only portion, before approval

`RepositorySyncDirtyInventory` projects the actual typed status into staged, unstaged, untracked, partially staged and rename inventories. Staged and unstaged membership intentionally overlap for partial files. JSON escaping keeps unusual names unambiguous. It supplies facts only; it makes no grouping, independence, content-coverage or acceptance claim.

The standard native dirty refusal now includes that inventory and explicitly states that no purpose grouping or commit acceptance occurred. It preserves the previous unfinished outcome before blanket preservation or integration. No new state, executor, authority, continuation kind or acceptance ledger is introduced.

`Phase69DirtyPurposeSpec` authors four controlled-repository cases through actual normal public Start/Advance: staged-only, unstaged-only, untracked-only and mixed partial/shared-file plus unrelated ongoing work. The assertions compare actual observed HEAD/tree/status, index tree and file content, check absence of native checkpoint/commit intents, and use no SBT binding. This is a preservation baseline while the capability is outstanding, not a claim that purpose grouping, separate accepted purpose commits or the full S4 acceptance route exists. No case has been executed by this agent.

## Initial remaining work, before approval

After a genuinely admitted producer contract is available, S4 still needs actual Task/Step/Finding/Journal applicability lookup, one coherent unresolved grouping request, semantic dependency/coalescing validation, ordered candidates through existing Step-equivalent checks/assessment/review/commit handling, honest retained exclusions, and clean-route joining. This partial deliverable does not manufacture any of those results. The parent owns native dependency submission and subsequent continuation. S5 and integrated Phase acceptance remain pending; Session, Plan, original base, accepted work, issue history, counters and publication-owned catalog metadata are preserved.


## Actual approved continuation and authored implementation

The genuine Human258 approval was consumed by the public completion and a separate
Advance issued `018-human258-approved-complete/advance/response.json`, continuation
`db7e363d6c9c44dfd14f8e0605288127:ordered-implementation:260`, expected revision 261.
Its decision context is `ApproveDependencyScope`; actual decision reference is
`inputs/human258-approval/source.json#userAnswer@1`. The exact admitted proposal is
`p69-s4-selected-purpose-candidate@1`. The nine CNCF paths in that proposal have now
been edited for that purpose; this statement is source authorship, not native
validation, independent review, publication or installed qualification.

The [approved source contract and configuration note](phase-6.9-s4-purpose-runtime.md)
describes the producer API, consumer connection, authored actual public Operation
cases and explicit limitations. The initial preservation-only test source has
been expanded into six public Operation cases including actual ordered shared-file
candidates, same-file excluded staged meaning, valid inseparable coalescing,
invalid dependency refusal and independently evidenced required review. The
initial capability decision is retained here as chronology; it is not a current
request for a new Human approval. All fourteen phase acceptance flags remain
unchanged pending the owning integrated assessment.
