# SM Repository Sync Workflow Definition

Status: normative design input for `RepositorySyncWorkflow`

Source workflow: legacy `cncf-repository-sync` skill

Target: CML/CNCF Workflow and StateMachine runtime

## Purpose

`RepositorySyncWorkflow` は、一つの Git repository の local branch と configured tracking
branch を、履歴を一切書き換えずに一つの canonical history へ統合し、両 tip の一致を
検証する pure Workflow / StateMachine である。dirty local state があれば、まず全 dirty
path を一つの non-acceptance checkpoint として保存する。次に fetched remote tip との
関係を決定的に分類し、equal、local-ahead、remote-ahead fast-forward、または diverged
merge を選ぶ。最後に exact tracking mapping だけを non-force push し、fetch 後の equal
tips、`0/0` ahead/behind、clean tree を証明する。

これは release、Phase/Step acceptance、tagging、publication、deployment、PR 作成、remote
configuration 変更、credential 更新を含まない repository integration profile である。

AI cost の最小化は明示的な目的である。通常の clean/equal、local-ahead、remote-ahead、
および Git が conflict-free に統合できる diverged merge は、semantic AI Action を一件も
発行しない。ここでいう conflict-free は修正の衝突が起きていないことを表す。same semantic
target への異なる修正が衝突した場合、AI が resolution plan を計算するか、authority/複数妥当解
なら human Decision を待つ。uncommitted merge result の bounded review/repair も AI の担当である。
Git command、checkpoint、merge、validation、commit、push、再fetch はすべて Workflow
provider が管理する。

## Phase 1 common-contract alignment

Phase 1 closure binds this profile to the CNCF Phase 77 common contract. Its
profile-specific start Operation delegates to `WorkflowStartRequest` and
projects the resulting `WorkflowHandle` plus first `Continuation` (or typed
terminal) as `WorkflowInteraction`. A current `Continuation` remains the
canonical typed semantic boundary and Skill/Codex wire correlation; it is not
a second root identity or lifecycle. A typed completion is a
`ContinuationResult` admitted through `advanceWorkflow(handle, response?)`.
The older `Suspended(Continuation)` and profile-specific continuation wording
below describes runtime state or application payload specialization only; it
cannot replace the common Continuation protocol.

## Profile and skill identity

- CML Workflow name / definition selector: `RepositorySyncWorkflow`
- bundled/public skill name: `sm-repository-sync`
- skill path in bundle: `skills/sm-repository-sync/SKILL.md`
- source compatibility reference: legacy `cncf-repository-sync`

`SkillBundleManifest` は `sm-repository-sync -> RepositorySyncWorkflow` を versioned
binding として宣言する。skill 名から Workflow definition ID を推測または文字列変換で
導出しない。CLI selector は
`sm-workflow run start --workflow RepositorySyncWorkflow ...` とする。

公開 profile は GitHub、CNCF、Scala、SBT、Codex agent、local path、temporary receipt の
名称へ依存しない。remote endpoint は configured tracking remote の opaque identity として
扱う。GitHub remote の許可、Scala source-history policy、SBT validation selection は
host-side `GitSyncPolicy` / repository adapter が提供する optional policy であり、public
skill contract の前提ではない。

legacy `cncf-repository-sync` は rename、overwrite、forward、implicit migration、state
sharing をしない。`sm-repository-sync` は legacy skill を呼ばず、versioned
`sm-workflow` protocol のみを利用する。同じ worktree/branch への concurrent mutation は
Workflow state の共有ではなく `WorkspaceMutationLease` と provider-side repository lock
で排他する。

## Ownership boundary

### Workflow owns

- one repository/worktree、named local branch、tracking remote/ref mapping、remote endpoint
  identity の admission。
- full-state dirty scope、complete index classification、unsafe material scan、pre/post tree
  identity。
- checkpoint、fast-forward、non-rewriting merge、merge commit、non-force push の guards と
  typed receipts。
- fetched remote tip、merge base、ahead/behind、pending `MERGE_HEAD`、push retry budget、
  convergence evidence。
- deterministic validation policy、merge review/repair budget、current revision、lease、
  idempotency、terminal result。
- next enabled operation と `Completed | Suspended | Failed` の解釈。

### Workflow does not own

- public skill name/path、prompt、model、reasoning effort、agent role、conversation history、
  tool permission UI。
- raw Git executable、argv、shell expression、environment、credential location、network
  implementation、lock-file path。
- remote server setting、branch-protection rule、credential mutation、force-push exception、
  release/publication/deployment/PR semantics。
- arbitrary product repair outside the frozen merge boundary。

`GitOperationsProvider` owns exact process execution. Every selected operation is bound to the
Workflow run revision, repository identity, named branch, immutable input tips, allowed mutation
roots, remote/network policy, timeout, idempotency key, and receipt schema. The provider resolves
an executable and typed argv internally; neither an AI result nor `sm-repository-sync` can inject
a command, ref, path, environment value, or generic shell fragment.

## Aggregate structure

```text
RepositorySyncWorkflow
  +-- InvocationLifecycle
  |     BoundaryAssessment
  |     Snapshot
  |     Terminal
  +-- LocalPreservationLifecycle
  |     DirtyScope
  |     HistoryMaintenance
  |     FullStateCheckpoint
  |     CleanGate
  +-- IntegrationLifecycle
  |     Fetch
  |     RelationClassification
  |     FastForward | Merge
  |     Validation | MergeReview | MergeCommit
  +-- RemoteConvergenceLifecycle
  |     NonForcePush
  |     RemoteAdvanceReconciliation
  |     EqualTipVerification
  +-- DecisionLifecycle
  |     None
  |     Pending
  |     Resolved
  |     Blocked
  `-- EvidenceLifecycle
        Captured
        Current
        Stale
        Accepted
```

The CML definition and generated ABI are the source of truth. The public skill is not a prose
state table or a Git dispatcher.

## Run context

```text
RepositorySyncRun
  workflowDefinitionId
  workflowDefinitionVersion
  runId
  revision
  repositoryIdentity
  workspaceHandle
  localBranch
  trackingRemote
  trackingBranch
  remoteEndpointIdentity
  syncMode: FULL_STATE
  repositoryPolicyVersion
  boundarySnapshotReference
  fetchedRemoteSnapshotReference?
  localTipBeforeSync
  remoteTipBeforeSync?
  mergeBase?
  relationBeforeIntegration?
  dirtyPathSet[]
  indexClassification: EMPTY | COMPLETE_FULLY_STAGED
  unsafeMaterialFindings[]
  historyMaintenanceReceipt?
  checkpointCommit?
  preMergeLocalTip?
  selectedMergeHead?
  pendingMergeReference?
  conflictSet[]
  conflictResolutionLedger
  validationLedger
  mergeReviewLedger
  mergeRepairLedger
  mergeCommit?
  pushAttemptCount
  pushedTip?
  convergenceReceipt?
  pendingDecision?
  pendingContinuation?
  terminalOutcome?
```

`FULL_STATE` is deliberately the only initial mode. Every dirty path must be part of the intended
local state. The profile never silently selects a subset, stashes another task's bytes, cleans the
tree, resets an index, or absorbs an unknown path into a checkpoint.

## Top-level states

| State | Meaning | On-entry Action class |
| --- | --- | --- |
| `ENTRY_ASSESSMENT` | repository, named branch, tracking mapping, remote policy, lease を解決する | deterministic |
| `BOUNDARY_SNAPSHOT` | HEAD、complete porcelain/index、dirty bytes、tips、merge base を immutable に記録する | deterministic operation |
| `DIRTY_SCOPE_ADMITTING` | full-state scope、index shape、unsafe material を closed rules で分類する | deterministic |
| `FETCHING_TRACKING_REMOTE` | frozen tracking remote の ref を取得する | deterministic external operation |
| `FETCH_ADMITTING` | fetched endpoint/ref/tip と pre-fetch boundary を検証する | deterministic |
| `HISTORY_MAINTENANCE` | repository-declared source-history policy を必要時だけ適用する | deterministic operation |
| `CHECKPOINT_PREPARING` | post-maintenance full-state checkpoint authority を凍結する | deterministic |
| `CHECKPOINT_COMMITTING` | exact dirty state を one-parent non-acceptance commit にする | deterministic external operation |
| `CLEAN_GATE` | checkpoint 後の index/worktree と operation receipts を確認する | deterministic |
| `RELATION_CLASSIFICATION` | local/remote tips、merge base、ahead/behind を equal/ahead/diverged に分類する | deterministic |
| `FAST_FORWARDING` | remote-ahead clean branch を exact remote tip へ fast-forward する | deterministic external operation |
| `MERGE_PREPARING` | exact local parent と fetched remote tip を merge boundary に凍結する | deterministic |
| `MERGE_STARTING` | no-commit/no-ff merge を開始し `MERGE_HEAD` を確認する | deterministic external operation |
| `MERGE_CONFLICT_CLASSIFICATION` | pending merge の unmerged entries を non-collision / modification collision / authority collision に分類する | deterministic operation |
| `SEMANTIC_MERGE` | modification collision の typed resolution plan を求める | semantic AI |
| `SEMANTIC_MERGE_ADMITTING` | resolution plan を authority/tree/conflict IDs に照らして検証する | deterministic |
| `MERGE_RESULT_PREPARING` | synthesized source に必要な declared history policy を適用する | deterministic operation |
| `INTEGRATION_VALIDATING` | structural and policy-selected validation を実行する | deterministic external operation |
| `MERGE_REVIEW_PREPARING` | frozen pending merge boundary と review input を構築する | deterministic |
| `MERGE_REVIEWING` | pending merge result を bounded semantic review する | semantic AI |
| `MERGE_REVIEW_ADMITTING` | review disposition、repair budget、scope を検証する | deterministic |
| `MERGE_REPAIRING` | admitted merge-local semantic repair plan を返す | semantic AI |
| `MERGE_REPAIR_ADMITTING` | repair plan を適用可能な typed write set として検証する | deterministic |
| `MERGE_COMMITTING` | exact two-parent merge result を commit する | deterministic external operation |
| `PUSHING_TRACKING_BRANCH` | exact mapping を non-force push する | deterministic external operation |
| `REMOTE_ADVANCE_RECONCILING` | push 時に remote が進んだ場合の一回だけの再fetch/reclassify を行う | deterministic |
| `CONVERGENCE_VERIFYING` | fetch 後の equal tips、0/0、clean state を検証する | deterministic external operation |
| `AWAITING_DECISION` | authority 又は複数妥当解を待つ | human participant |
| `WAITING_FOR_REMOTE_STABILITY` | bounded remote retry を使い切ったため external change を待つ | WAIT continuation |

Terminal outcomes:

- `SYNCHRONIZED`
- `ALREADY_SYNCHRONIZED`
- `UNSAFE_MATERIAL`
- `BOUNDARY_INVALID`
- `MERGE_ABORTED`
- `DECISION_REQUIRED`
- `BLOCKED`
- `FAILED`
- `CANCELLED`

## Primary transition definition

### Boundary, preservation, and relation classification

```text
start
  -> ENTRY_ASSESSMENT
  -> BOUNDARY_SNAPSHOT
  -> DIRTY_SCOPE_ADMITTING
  -> FETCHING_TRACKING_REMOTE
  -> FETCH_ADMITTING

DIRTY_SCOPE_ADMITTING -- unsafe material --> UNSAFE_MATERIAL
DIRTY_SCOPE_ADMITTING -- detached/ambiguous/unrelated boundary --> AWAITING_DECISION | BOUNDARY_INVALID
DIRTY_SCOPE_ADMITTING -- clean --> FETCHING_TRACKING_REMOTE
DIRTY_SCOPE_ADMITTING -- dirty full state --> FETCHING_TRACKING_REMOTE

FETCH_ADMITTING -- clean local state --> RELATION_CLASSIFICATION
FETCH_ADMITTING -- dirty local state + history policy applicable --> HISTORY_MAINTENANCE
FETCH_ADMITTING -- dirty local state + no maintenance --> CHECKPOINT_PREPARING
FETCH_ADMITTING -- endpoint/ref/permission failure --> BLOCKED | WAITING_FOR_REMOTE_STABILITY

HISTORY_MAINTENANCE -- bounded post-state accepted --> CHECKPOINT_PREPARING
HISTORY_MAINTENANCE -- policy/authority ambiguity --> AWAITING_DECISION
HISTORY_MAINTENANCE -- changed scope/index --> BOUNDARY_INVALID

CHECKPOINT_PREPARING -- frozen --> CHECKPOINT_COMMITTING
CHECKPOINT_COMMITTING -- receipt accepted --> CLEAN_GATE
CHECKPOINT_COMMITTING -- failed/no mutation --> BLOCKED | FAILED
CLEAN_GATE -- clean --> RELATION_CLASSIFICATION
CLEAN_GATE -- drift --> BOUNDARY_INVALID

RELATION_CLASSIFICATION -- equal --> CONVERGENCE_VERIFYING
RELATION_CLASSIFICATION -- local-ahead --> PUSHING_TRACKING_BRANCH
RELATION_CLASSIFICATION -- remote-ahead + clean local --> FAST_FORWARDING
RELATION_CLASSIFICATION -- diverged --> MERGE_PREPARING
```

The relation is calculated only from the exact local tip and fetched tracking tip saved in the
current evidence snapshot. `CHECKPOINT_COMMITTING` changes only the local tip; it never causes a
second implicit fetch. Thus a checkpoint over a remote-ahead base deterministically becomes a
diverged relation and reaches the merge route.

### Integration, review, and push

```text
FAST_FORWARDING -- exact target reached --> PUSHING_TRACKING_BRANCH | CONVERGENCE_VERIFYING
FAST_FORWARDING -- local branch changed --> BOUNDARY_INVALID

MERGE_PREPARING -> MERGE_STARTING
MERGE_STARTING -- conflict-free pending merge --> MERGE_RESULT_PREPARING
MERGE_STARTING -- unmerged entries --> MERGE_CONFLICT_CLASSIFICATION
MERGE_STARTING -- cannot safely start --> MERGE_ABORTED | FAILED

MERGE_CONFLICT_CLASSIFICATION -- identical/non-overlapping (not a collision) --> MERGE_RESULT_PREPARING
MERGE_CONFLICT_CLASSIFICATION -- modification collision --> SEMANTIC_MERGE
MERGE_CONFLICT_CLASSIFICATION -- authority/multiple-valid-resolution --> AWAITING_DECISION

SEMANTIC_MERGE -- typed result submitted --> SEMANTIC_MERGE_ADMITTING
SEMANTIC_MERGE_ADMITTING -- valid --> MERGE_RESULT_PREPARING
SEMANTIC_MERGE_ADMITTING -- authority decision --> AWAITING_DECISION
SEMANTIC_MERGE_ADMITTING -- invalid/non-convergent --> MERGE_ABORTED | FAILED

MERGE_RESULT_PREPARING -- policy satisfied --> INTEGRATION_VALIDATING
INTEGRATION_VALIDATING -- valid --> MERGE_REVIEW_PREPARING
INTEGRATION_VALIDATING -- merge-local failure --> MERGE_ABORTED | FAILED
MERGE_REVIEW_PREPARING -> MERGE_REVIEWING
MERGE_REVIEWING -- typed disposition submitted --> MERGE_REVIEW_ADMITTING
MERGE_REVIEW_ADMITTING -- accept --> MERGE_COMMITTING
MERGE_REVIEW_ADMITTING -- admitted bounded repair --> MERGE_REPAIRING
MERGE_REVIEW_ADMITTING -- decision/blocker --> AWAITING_DECISION | MERGE_ABORTED
MERGE_REPAIRING -- typed repair submitted --> MERGE_REPAIR_ADMITTING
MERGE_REPAIR_ADMITTING -- valid --> MERGE_RESULT_PREPARING
MERGE_REPAIR_ADMITTING -- exhausted/invalid --> MERGE_ABORTED | FAILED
MERGE_COMMITTING -- exact two-parent receipt --> PUSHING_TRACKING_BRANCH

PUSHING_TRACKING_BRANCH -- accepted --> CONVERGENCE_VERIFYING
PUSHING_TRACKING_BRANCH -- remote advanced and retry unused --> REMOTE_ADVANCE_RECONCILING
PUSHING_TRACKING_BRANCH -- permission/rejection --> BLOCKED | FAILED
REMOTE_ADVANCE_RECONCILING -- fetched once --> RELATION_CLASSIFICATION
REMOTE_ADVANCE_RECONCILING -- remote advances again --> WAITING_FOR_REMOTE_STABILITY
CONVERGENCE_VERIFYING -- equal tips + 0/0 + clean --> SYNCHRONIZED | ALREADY_SYNCHRONIZED
CONVERGENCE_VERIFYING -- mismatch --> WAITING_FOR_REMOTE_STABILITY | FAILED
```

`MERGE_ABORTED` invokes only the provider's exact pending-merge abort operation. It restores the
already clean post-checkpoint local tip and leaves checkpoint/local/remote committed history intact.
It never resets, rebases, cleans, stashes, or discards a checkpoint.

## Semantic AI Actions

| Operation | Immutable input | Typed result |
| --- | --- | --- |
| `ResolveRepositoryMergeConflicts` | conflict IDs, base/local/remote semantic models, merge invariants, allowed merge boundary | `RepositoryMergeResolutionPlan | RepositorySyncDecisionNeeded` |
| `ReviewRepositoryMerge` | frozen two-parent merge result, affected contract set, validation evidence, review policy | `RepositoryMergeReviewDisposition` |
| `RepairRepositoryMerge` | admitted finding IDs, repair budget, frozen merge result, allowed paths | `RepositoryMergeRepairPlan | RepositorySyncDecisionNeeded` |

Every semantic action has the same sandwich:

```text
deterministic prepare
  -> immutable input snapshot
  -> one semantic AI Action
  -> typed result
  -> deterministic admission / validation
  -> automatic next-state selection
```

The AI result cannot contain `nextState`, command/executable/argv, validation acceptance, commit
or push readiness, retry count, remote endpoint, branch mapping, local/remote tip, ledger mutation,
or a direct file/Git mutation. A resolution/repair plan is bound to conflict or finding IDs and is
materialized by a provider-side compare-and-set write only after deterministic validation. The skill
executes exactly the leased `AIWorkRequest` and returns exactly the matching result; it never
chooses another operation, agent, command, Decision, or continuation.

## Deterministic operations and Git provider contract

The first profile needs these typed operations. Their names express a fixed purpose, not a public
raw-command API.

- `ResolveRepositoryBoundary`
- `CaptureRepositorySnapshot`
- `ClassifyIndexAndDirtyScope`
- `ScanUnsafeRepositoryMaterial`
- `FetchTrackingRemote`
- `AdmitFetchedTrackingState`
- `ApplyDeclaredHistoryMaintenance`
- `FreezeFullStateCheckpoint`
- `CreateFullStateCheckpoint`
- `VerifyCleanIntegrationGate`
- `ClassifyRepositoryRelation`
- `FastForwardTrackingBranch`
- `PrepareNonRewritingMerge`
- `StartPendingMerge`
- `ClassifyRepositoryMergeConflicts`
- `ValidateRepositoryMergeResolution`
- `ApplyRepositoryMergeResolution`
- `PrepareMergedSourceMetadata`
- `RunRepositoryIntegrationValidation`
- `PrepareRepositoryMergeReview`
- `AdmitRepositoryMergeReview`
- `ValidateRepositoryMergeRepair`
- `ApplyRepositoryMergeRepair`
- `CreateRepositoryMergeCommit`
- `PushTrackingBranchNonForce`
- `ReconcileRemoteAdvance`
- `VerifyRepositoryConvergence`
- `AbortPendingRepositoryMerge`
- `PersistRepositorySyncEvidence`

The provider accepts a closed operation identity and run-bound input only. For example,
`FetchTrackingRemote` may update only the admitted tracking ref; `StartPendingMerge` may merge
only the frozen fetched commit and must leave an uncommitted `MERGE_HEAD`; `PushTrackingBranchNonForce`
may push only the frozen `<local branch>:<tracking branch>` mapping and prohibits force/tags/other
refs. Each receipt records executable identity, resolved argv digest, input/output tip identities,
tree/index states, process completion, remote endpoint policy result, and generated commit hashes
where applicable. Raw stdout/stderr stays in artifact storage; history carries typed references and
digests.

The managed provider is not an AI sandbox relaxation. It is an independent, policy-enforced
execution boundary. AI/skill-supplied executables, free-form arguments, shell fragments, arbitrary
scripts, uncontrolled credentials, source-tree-wide cleanup, history rewrite, and remote setting
changes fail closed.

## Preservation, merge, and validation policy

### Full-state checkpoint

When dirty, `FreezeFullStateCheckpoint` requires all dirty paths, including untracked files and
deletions, to be inside the admitted worktree and full-state scope. It admits only an empty index or
the exact complete-fully-staged shape; a partial/mixed index is a boundary failure and is never
normalized. It scans bounded names/content for secrets, private keys, credentials, unexpected binary
artifacts, and unsafe generated outputs before any commit.

`CreateFullStateCheckpoint` produces one exact one-parent non-acceptance commit. It carries no
product validation, acceptance, release, publish, deploy, or push claim. The generic commit subject
and trailer are profile policy data, not a public skill instruction. The checkpoint receipt must
prove that the committed tree equals the frozen complete dirty state and that the post-commit
worktree/index are clean before integration proceeds.

`ApplyDeclaredHistoryMaintenance` is optional and repository policy driven. A host adapter may bind
a Scala `@version`/source-history rule, but the generic workflow merely requires that it has a
declared bounded file policy, captures pre/post bytes, and proves it changed neither dirty scope nor
unrelated index entries. It is never inferred from file extension or remembered prose.

### Non-rewriting integration and conflict boundary

Fast-forward is legal only for a clean local state and the exact fetched remote descendant.
Otherwise the provider starts a `--no-commit --no-ff` equivalent against the frozen remote commit.
It must not auto-commit and it must verify `MERGE_HEAD == selectedMergeHead`.

Identical normalized content, non-overlapping structured fields, or a deterministic generated
projection whose canonical source is unchanged are compatibility conditions, not modification
collisions. The provider records their input/output digests and composes them without an AI call.

When both sides modify the same semantic target to different values, `ModificationCollision` is
mandatory even if a provider could construct one syntactic output. The provider never automatically
resolves it or selects `ours`/`theirs` wholesale. A collision that can be assessed without changing
authority becomes `ResolveRepositoryMergeConflicts`; a public-contract/architecture/user-prose/
ownership/accepted-planning/closure/intentional-deletion collision that changes authority, or one
with multiple valid outcomes, becomes `AWAITING_DECISION`.

### Validation and merge review

`RunRepositoryIntegrationValidation` always runs structural checks: no unmerged entries, no conflict
markers, exact pending-merge identities, and two-parent whitespace classification. A whitespace
diagnostic blocks only if it occurs at the same result path/line relative to both parents; one-sided
diagnostics are inherited input and are not silently rewritten.

Product validation is selected by an immutable `RepositoryIntegrationValidationPolicy` from the
actual merge delta: documentation/metadata-only defaults to no product test, localized source/build/
spec changes select the narrowest declared test, and cross-cutting runtime/public-contract changes
select the required broader validation. A sync never runs tests just because it synchronized.

Only an uncommitted merge result receives `ReviewRepositoryMerge`. Its accepted review leads directly
to the deterministic merge commit. An admitted finding can produce at most the policy-bounded
`RepairRepositoryMerge` action; each repair returns to deterministic metadata preparation and
validation before re-review. A failed validation, unresolved review finding, exhausted repair budget,
or invalid repair aborts the pending merge without discarding the checkpoint.

## Remote retry and convergence

The initial fetch is discovery. It never authorizes overwrite of the local branch. `PushTrackingBranchNonForce`
uses the explicit workflow invocation's bounded remote-write authority only for the frozen admitted
mapping. On a non-fast-forward push caused by a newly advanced remote, the provider fetches once,
records a new remote snapshot, and returns to `RELATION_CLASSIFICATION`. The retried integration is
not a new semantic workflow or an unbounded push loop. A second remote advance reaches
`WAITING_FOR_REMOTE_STABILITY`; a client may resume only after a new remote-state event.

After every accepted push, `VerifyRepositoryConvergence` performs one fetch and requires all of:

- local tip equals the selected tracking tip;
- ahead/behind is `0/0`;
- worktree and index are clean; and
- no merge, rebase, cherry-pick, or revert state remains.

Only that receipt yields `SYNCHRONIZED`. Fetch-only, local-only merge, queued permission, unresolved
pending merge, or a push with no final equal-tip receipt is never success.

## Safety, persistence, and cost acceptance

All snapshots, leases, operation receipts, conflict/review lineages, retry budget, and final
convergence result persist in the Textus-managed SQLite store. Each external operation is
compare-and-set bound to `runId` and revision. If a repository/remote tree changes between snapshot
and operation, the provider returns typed drift evidence; `advance` reclassifies only where the
policy permits and never guesses a new remote, path set, branch, or conflict solution.

The profile must prove at least:

- clean equal/local-ahead/remote-ahead paths have zero semantic AI Work Orders;
- dirty full-state checkpoint plus local-ahead push has zero semantic AI Work Orders;
- conflict-free diverged merge, validation, merge commit, push, and convergence have zero semantic
  AI Work Orders;
- every `ModificationCollision` creates either `ResolveRepositoryMergeConflicts` or a human
  Decision; only non-colliding composition, merge review, and policy-admitted repair follow their
  respective declared paths;
- public skill/schema contains no `cncf-*` dependency, raw Git command, local path, agent role,
  SQLite path, credential, or receipt locator;
- no AI result can invoke Git, choose a ref/remote, bypass a stale revision, turn a failed validation
  into a commit, create a force push, or claim `SYNCHRONIZED`;
- restart, idempotent re-entry, concurrent lease, checkpoint-before-merge, one remote-advance retry,
  merge abort, and post-push equal-tip paths are reproducible from persisted evidence.

This definition is source-compatible with the operational safety intent of legacy
`cncf-repository-sync`, not an invocation, replacement, migration, or forwarding rule for that
skill. Both entry points remain independently installable and retain separate durable state.
