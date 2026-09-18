# Phase 1: Advance-Centered Local Workflow Core

Status: planned

## Goal

Textus-managed SQLite 上に durable な WorkflowRun を保持し、`advance` が機械的遷移を吸収して、公開 `sm-*` skill には次の意味的作業だけを返す local/standalone vertical slice を完成させる。

Phase 1 の中心命題は次である。

> `advance` が機械的遷移を吸収し、Codex には次の意味的作業だけを返す。

LLM/Codex 利用コストの低減は副次効果ではなく、本 Phase の明示的な受入目標とする。validation、review、permission、receipt を省略せず、それらの間にある状態確認、分岐、完了判定、次作業生成を Textus 側へ移す。

## Completion Statement

Phase 1 は、利用者が reference workflow を `sm-workflow` CLI または同梱された薄い skill から開始し、process を終了・再起動しても継続でき、各 semantic Work Order の完了応答から次の semantic boundary が直接返り、途中の automatic transition に Codex/model invocation を必要としない状態で完了する。

## Upstream Phase Dependencies

Phase 1 は、次の二つの upstream Phase がそれぞれの release closure を完了し、
相互に対応する versioned handoff を提供した後にだけ開始できる。`planned`、
途中の producer fixture、hand-written definition、または consumer 側の暫定
adapter は Entry Criteria を満たさない。

### Cozy Phase 62 — CML WORKFLOW Language and Producer ABI

`asami/cozy` Phase 62 は CML の first-class `WORKFLOW` declaration を確定する
producer boundary である。Phase 1 が受け取るのは、StateMachine / Composite
StateMachine semantics を再利用しながら、次を明示した release 済み generated ABI
である。

- Workflow definition identity/version と source identity。
- typed Operation reference と既存 logical-Action program への接続。
- `automatic` と typed semantic boundary (`WORK_ORDER | DECISION | WAIT`) の
  closed progression contract。
- real CML fixture、deterministic generation evidence、および CNCF Phase 77 に
  引き渡す exact ABI version。

Phase 62 は `advance`、WorkflowRun persistence、SQLite、lease、public skill を
実装しない。これらを Cozy 側に再導入しないことも Phase 1 の依存契約である。

### CNCF Phase 77 — WORKFLOW ABI Admission and Progression Contract

`asami/goldenport-cncf` Phase 77 は、Cozy Phase 62 の frozen ABI を CML 再解析
なしで admission し、ComponentFactory discovery、WorkflowInstance persistence
SPI、typed Action / Operation integration、および bounded deterministic progression
evaluator を提供する consumer boundary である。

Phase 1 は Phase 77 の次の完了済み contract に依存する。

- supported Workflow ABI version の fail-closed admission と source-correlated
  diagnostics。
- generated definition を唯一の canonical input とする ComponentFactory
  discovery。
- entity-local StateMachine persistence と別の WorkflowInstance identity,
  revision, history, correlation contract。
- automatic progression を評価し、semantic boundary または terminal outcome を
  一つ返す evaluator。semantic boundary を越えたり、external Action を実行したり
  しないこと。
- Cozy real-source fixture から CNCF admission までの reproducible evidence と、
  `sm-workflow` consumer handoff。

Phase 77 は default datastore、SQLite、WorkOrder lease、public CLI/server、skill
distribution、Codex cost policy を提供しない。Phase 1 は同 Phase の reusable
contract を component-local Textus runtime に bind するが、公開 `sm-*` skill と
public protocol に CNCF 固有 contract を露出しない。

### Fixed Handoff Rule

Phase 1 の開始時に、Cozy Phase 62 release、CNCF Phase 77 release、互換性のある
Workflow ABI version、real-source fixture identity、および両 repository の exact
commit/revision を記録する。Phase 62 と Phase 77 のどちらかが未完了、互換性未確認、
または handoff evidence を欠く場合、`sm-workflow` 内に一時的な Workflow DSL、
生成物コピー、CNCF ABI emulator を作って迂回しない。upstream gap と exact resume
point を記録して Phase 1 を開始前のまま保持する。

## Entry Criteria

- Cozy Phase 62 と CNCF Phase 77 の fixed handoff が上記の規則を満たす。
- Workflow が StateMachine / Composite StateMachine の意味論を再利用している。
- generated ABI が automatic transition と typed semantic boundary を識別できる。
- CML Action が raw command ではなく型付き Operation を参照できる。
- Textus runtime が component-local datastore を構成できる。

## Design Invariants

1. `advance` は通常進行の唯一の evaluator である。
2. automatic transition は Textus 側だけで実行し、Codex/model へ返さない。
3. 一回の応答は次の一つの `Continuation` だけを返す。
4. `Continuation.outcome` は `WORK_ORDER | DECISION | WAIT | TERMINAL` の closed set とする。
5. semantic boundary を推測で越えない。
6. `start`、`work complete`、`work fail`、`decision resolve` は受理後に同じ evaluator を呼び、次の `Continuation` を同じ応答で返す。
7. `status` と `history` は read-only であり、状態遷移を起こさない。
8. workflow の正本は Textus-managed datastore に置き、skill、会話履歴、workspace 内 Markdown を正本にしない。
9. public skill、public CLI schema、public JSON schema は CNCF 固有 contract に依存しない。
10. SQLite、JDBC、SQL、database path は skill/CML/domain contract に公開しない。

## Scope

### S1. Textus CAR baseline

- `sm-workflow` を Textus CAR として構成する。
- CML source、generated ABI、ComponentFactory、domain/application logic、CLI adapter の境界を分ける。
- public namespace と component identity を固定する。
- local runtime profile と test profile を用意する。

### S2. Generic workflow model

次の generic model を定義する。

- `WorkflowDefinition`
- `WorkflowRun`
- `PlanRevision`
- `Stage`
- `WorkItem`
- `WorkOrder`
- `Decision`
- `Evidence`
- `Result`
- `Receipt`
- `Continuation`

Goal / Phase / Step などの語彙は core model に入れず、workflow profile が Stage / WorkItem へ写像する。

### S3. Workflow and StateMachine

少なくとも次の lifecycle を CML で定義する。

- `WorkflowRunLifecycle`
- `WorkOrderLifecycle`
- `DecisionLifecycle`
- `EvidenceLifecycle`

最初の reference workflow は
[`GoalPhaseWorkflow`](../notes/sm-goal-phase-workflow-definition.md) とし、public skill
`sm-goal-phase` から明示的にbindする。legacy
`cncf-goal-phase` skill の
Phase Entry、PLAN、Step/Slice delivery、review、bounded repair、commit、Phase closure
を純粋な Workflow / StateMachine として実装する。skill/model/agent/turn scheduling は
state semantics に含めず、AI 担当 typed Action だけを Required Operation の external
provider として Continuation Protocol へ bind する。

第二の reference workflow は
[`SplitPhaseWorkflow`](../notes/sm-split-phase-workflow-definition.md) とし、public skill
`sm-split-phase` から明示的にbindする。legacy
`cncf-split-phase` skill を source compatibility とし、一つの
source Phase の inventory、proposal adoption/design、preview/apply、planning-document
projection、conflict merge、static validation を純粋な Workflow / StateMachine として
実装する。current で complete な `SPLIT_REQUIRED` proposal があれば semantic AI を
呼ばずに適用する。proposal がない場合も、過去 Phase の予定/実績時間と作業分類を
versioned policy で収集・calibrate し、contiguous candidates を列挙して決定的に
最適化する。履歴から推定できない新規作業と未確定 semantic boundary だけを
`AssessNovelSplitWork` に渡す。

`sm-goal-phase` と `sm-split-phase` は別々の human-selected entry point とする。skill の
直接選択、または host/client が提示した候補からの明示選択が exact Workflow invocation
authority になる。`GoalPhaseWorkflow` が split 必要性を判定した場合は
`SPLIT_REQUIRED` terminal result と advisory recommendation を返すだけで、
`SplitPhaseWorkflow` を自動開始しない。人間が `sm-split-phase` を選択した場合に新しい
runを作る。split適用後もchild goalを自動開始せず、人間が選択したchildごとに独立した
`sm-goal-phase` runを開始する。Workflow間でrun identity、revision、Decision、
Continuation、durable stateを暗黙移送しない。

split 適用時の conflict は base/current/desired の typed three-way model で扱う。非重複、
同一値、canonical projection は修正の衝突ではなく、Workflow-owned deterministic operation
が composition する。同じ semantic target への異なる修正は `ModificationCollision` とし、
`ResolveSplitMergeConflicts` として AI provider に委譲する。identity/authority の変更や
複数の妥当解は human `DECISION` で停止する。AI は planning files や Git command を直接
操作せず、typed resolution も Workflow validation と compare-and-set write を通す。

Goal / Split profile は semantic AI Action の前に deterministic input preparation、後に
deterministic result admission/validation を置く。AI result は `nextState`、command、
validation acceptance、ledger mutation、cycle count、commit readiness を所有せず、
admission stateを経ずにcommitまたはterminalへ遷移しない。

第三の reference workflow は
[`RepositorySyncWorkflow`](../notes/sm-repository-sync-workflow-definition.md) とし、public
skill `sm-repository-sync` から明示的にbindする。legacy `cncf-repository-sync` skill の
full-state checkpoint、tracking fetch、equal/ahead/diverged分類、non-rewriting merge、
proportional validation、merge review、non-force push、equal-tip verification を pure
Workflow / StateMachine に写す。通常の同期と conflict-free merge は semantic AI Action を
発行せず、Workflow-owned Git provider が実行する。same semantic target への異なる修正は
`ModificationCollision` とし、AI resolution または human Decision を必須にする。AI は
uncommitted merge review、policy-bounded merge repair も担当し、Git command、
branch/remote選択、checkpoint/merge/commit/push、retry、terminal判定を所有しない。

`RepositorySyncWorkflow` は public profile として GitHub、CNCF、Scala、SBT、agent、
local path、receipt locatorを要求しない。remote allowlist、declared source-history
maintenance、validation selection は host-side repository policy に閉じる。full-state
checkpoint の後は必ず clean tree gate を通り、force/rebase/squash/amend/reset/clean/stash、
tag/PR/release/publication/deployment、remote configuration/credential mutation を fail-closed
で禁止する。push中のremote advanceは一度だけ決定的に再fetch/reclassifyし、二度目は
semantic AIでなく `WAIT_FOR_REMOTE_STABILITY` とする。

各 transition は Cozy Phase 62 の generated ABI と CNCF Phase 77 の admission を
通じて `automatic` または `semantic-boundary` として判定可能にする。Phase 1 は
その宣言を `Continuation` と WorkOrder lifecycle に bind するだけで、分類を
再定義または推測しない。automatic transition は永続済み入力だけから
deterministic に決まり、外部 I/O、LLM、人間判断、追加権限を要求しない。

### S4. Public operation contract

Phase 1 で公開する operation:

- `StartWorkflowRun`
- `AdvanceWorkflowRun`
- `StartWorkOrder`
- `SubmitWorkResult`
- `ResolveDecision`
- `GetWorkflowRunStatus`
- `GetWorkflowRunHistory`
- `CancelWorkflowRun`

`StartWorkflowRun` は exact `workflowDefinitionId` と typed start input を要求する。
runtime、skill、terminal result は前 run の内容から別 Workflow を選択または chain せず、
recommendation は invocation authority として扱わない。

```text
WorkflowInvocationSelection
  selectionId
  workflowDefinitionId
  participantIdentity
  selectionKind: EXPLICIT_HUMAN
  recommendationReference?

WorkflowRecommendation
  recommendationId
  sourceRunId
  sourceRevision
  recommendedWorkflowDefinitionId
  typedStartInputReference
  reasonCode
  advisoryOnly: true

StartWorkflowRunRequest
  workflowDefinitionId
  typedStartInput
  invocationSelection: WorkflowInvocationSelection
  idempotencyKey
```

host/client adapter は direct skill selection または UI selection から selection record を
作る。recommendation は候補表示と相関にだけ使い、別 Workflow の selection record や
start authority を生成しない。

`AdvanceWorkflowRun` の概念入力:

```text
runId
executorIdentity?
capabilities
expectedRevision
idempotencyKey
```

`Continuation` の概念出力:

```text
runId
revision
outcome: WORK_ORDER | DECISION | WAIT | TERMINAL
workOrder? | decision? | waitCondition? | terminalResult?
advanceSummary:
  fromRevision
  toRevision
  automaticTransitionCount
  diagnosticReference?
```

完全な history、過去 receipt の本文、内部 state 候補一覧は通常の `Continuation` に展開しない。

### S5. Advance evaluator

`advance` は一つの bounded evaluation として次を行う。

1. revision、idempotency、既存 lease を検証する。
2. 有効な automatic transition を一意に選ぶ。
3. state と current projection を更新する。
4. transition event を追記する。
5. 次も automatic である間、`maxAutomaticTransitions` まで繰り返す。
6. semantic boundary に到達したら一つの `Continuation` を構築する。
7. `WORK_ORDER` かつ executor が与えられている場合、発行と lease を同じ transaction で行う。
8. idempotency result と返却 `Continuation` を記録する。

Git/SBT を含む procedural external command は、typed deterministic operation として
admission できる限り Workflow runtime が component-local provider を通じて実行する。
Skill/AI は argv の選択、process 起動、retry、終了判定、commit 処理を行わず、外部
command を要求する `WORK_ORDER` も発行しない。provider は AI tool sandbox から独立
した管理実行境界を持つが、AI sandbox 自体は緩和しない。operation registry、current
state/revision/guard、operation-specific capability、mutation root、network/credential
policy、timeout、receipt を必須とし、権限を暗黙に拡張しない。AI/Skill からの
executable、free-form argv、generic shell、arbitrary script の注入は拒否する。

同時に複数の automatic transition が成立して優先順位が一意でない場合は model invariant error とする。循環または上限超過は internal diagnostic failure とし、Codex が解決すべき Work Order に変換しない。

### S6. Textus-managed SQLite persistence

local/standalone profile の既定 backend を SQLite とする。

- Textus runtime/launcher が provider、path、connection lifecycle、schema migration を所有する。
- domain/application logic は provider-neutral storage port のみに依存する。
- 一つの component-local database で複数 workspace と WorkflowRun を管理する。
- repository 内に database を作成しない。
- platform-aware Textus local-data root と database file 名を Phase 1 で固定する。
- WAL、foreign key enforcement、bounded busy timeout、immediate write transaction を有効にする。
- current state と append-only transition history を同じ mutation transaction で更新する。
- large artifact は database に直接蓄積せず、digest と Textus-managed artifact reference を receipt に保持する。

SQLite profile は single-machine、local CLI、loopback server、少数の並行 executor を対象とする。network filesystem、multi-host direct sharing、active-active server は対象外とする。

### S7. CLI adapter

Phase 1 の command surface:

```text
sm-workflow run start --workflow <workflow-id> --target <target> --workspace <path>
sm-workflow run advance <run-id> --executor <executor-id>
sm-workflow work start <run-id> <work-order-id>
sm-workflow work complete <run-id> <work-order-id> --receipt <file>
sm-workflow work fail <run-id> <work-order-id> --receipt <file>
sm-workflow decision resolve <run-id> <decision-id> --input <file>
sm-workflow run status <run-id>
sm-workflow run history <run-id>
sm-workflow run cancel <run-id>
```

- mutation command は `expectedRevision` と `idempotencyKey` を受理する。
- skill は machine-readable JSON output を使用する。
- human-readable output は同じ application result の projection とする。
- CLI は workflow semantics や SQLite access を再実装しない。

### S8. Reference workflow

`advance` を実証する小さな development workflow profile を同梱する。

```text
Requested
  -> [automatic] InitializePlan
  -> [automatic] SelectFirstWorkItem
  -> [semantic] PLAN work order
  -> [deterministic] AdmitPlanResult
  -> [automatic] SelectChangeWorkItem
  -> [semantic] CHANGE work order
  -> [deterministic] InspectChangeResult
  -> [deterministic] RunValidation
  -> [automatic] EvaluateAcceptance
  -> [terminal] Completed
```

これは engine acceptance 用の generic profile であり、CNCF の Goal/Phase contract を移植しない。

### S9. Thin public skill

- `skills/sm-goal-phase/SKILL.md`、`skills/sm-split-phase/SKILL.md`、
  `skills/sm-repository-sync/SKILL.md` を提供する。
- `SkillBundleManifest` に `sm-goal-phase -> GoalPhaseWorkflow` と
  `sm-split-phase -> SplitPhaseWorkflow`、
  `sm-repository-sync -> RepositorySyncWorkflow` のversioned bindingを明記し、skill名から
  Workflow名を推測しない。
- Workflowが選択・leaseしたexact `AIWorkRequest`をskillへ渡し、skillはその一件のAI処理を
  実行してtyped `AIWorkResult`を同じWork Orderへ返すだけにする。
- skill 内に state machine、phase progression、retry policy、SQLite path、CNCF commandを複製しない。
- skillはoperation候補、result disposition、next state、次に呼ぶskill/agent/commandを判断しない。
- skill、Workflow、terminal result は別 Workflow を自動開始しない。host/client は候補を
  表示できるが、人間の明示選択後に新しい run identity で開始する。
- `DECISION`、`WAIT`、`TERMINAL`の配送/表示はhost/client adapterがContinuation kindに
  従ってmechanically行い、semantic AI skillのdispatcher logicにしない。
- `AIWorkResult`にnext operation/state、command request、routing directiveを含めない。
- bundle は framework-neutral `SkillBundleManifest` を持ち、CAR に同梱した bytes と standalone bundle の bytes/digest を一致させる。
- legacy `cncf-goal-phase` / `cncf-split-phase` / `cncf-repository-sync` skill を rename、overwrite、forward、または
  state migration しない。両系列は別名、別run/stateで長期併用する。
- `sm-goal-phase` / `sm-split-phase` / `sm-repository-sync` は `cncf-*` skill を呼ばず、versioned
  `sm-workflow` protocol だけを使用する。

### S10. Cost observability

run ごとに少なくとも次を記録・表示できるようにする。

- `automatic_transition_count`
- `semantic_work_order_count`
- `client_round_trip_count`
- `continuation_payload_bytes`
- `resume_context_payload_bytes`
- `non_colliding_composition_count`
- `semantic_merge_work_order_count`
- `deterministic_work_estimate_count`
- `novel_work_estimate_count`
- `partition_candidate_count`

host が model usage を返せる場合は invocation/token/cost を追加 metric として受理するが、特定 host の usage API を public contract の必須依存にはしない。

## Non-Goals

- `cncf-*` の置換、変更、state migration、dual write
- CNCF runtime/Job/agent/receipt protocol との adapter
- `cncf skill ...` または `textus skill ...` installer の実装
- MCP/server adapter
- multi-host datastore と active-active execution
- remote publish、deployment、credential management
- arbitrary shell command を CML Action または Work Order payload として実行する機能
- model による automatic/semantic 分類の推測

## Deliverables

- Textus CAR source and generated ABI
- Workflow/StateMachine CML model, including `GoalPhaseWorkflow`, `SplitPhaseWorkflow`, and `RepositorySyncWorkflow`
- versioned public operation and JSON schema
- provider-neutral workflow storage port
- Textus-managed SQLite local provider binding
- bounded `advance` evaluator
- CLI adapter
- reference development workflow profile
- thin `sm-goal-phase`, `sm-split-phase`, and `sm-repository-sync` skills
- neutral skill bundle manifest and CAR inclusion
- unit, persistence, concurrency, restart, CLI, bundle, and cost-structure acceptance evidence
- public usage and recovery documentation

## Acceptance

### A1. Mechanical transition absorption

- A workflow containing multiple automatic transitions reaches the first semantic boundary in one `start` response.
- Completing a semantic Work Order returns the next semantic `Continuation` in the same response.
- No intermediate automatic state is presented as Codex work.
- No explicit `status` or `advance` round-trip is required between consecutive semantic Work Orders.
- Explicit `run advance` resumes an interrupted client and returns the same current boundary.

### A2. Continuation closure

- Every successful advance result is exactly one of `WORK_ORDER`, `DECISION`, `WAIT`, or `TERMINAL`.
- `WORK_ORDER` contains one bounded semantic task and its expected result schema.
- `WAIT` contains a wake condition and does not request polling work.
- Full history is available through `history` but absent from the normal continuation payload.

### A3. Determinism and failure safety

- Same revision/idempotency key replay returns the same `Continuation` without duplicate transition or Work Order.
- Stale revision and lease conflict return typed conflicts without state mutation.
- Ambiguous automatic transitions fail as a model invariant error.
- A cycle or `maxAutomaticTransitions` overflow fails with a diagnostic reference and no Codex Work Order.
- A semantic boundary is never crossed without its accepted Result/Receipt or Decision.

### A4. SQLite durability and concurrency

- Reopening the same Textus-managed database restores run, plan, current state, pending/leased Work Order, Decision, Evidence, idempotency result, and transition history.
- State change, event append, next Work Order creation/lease, and continuation record commit atomically.
- Concurrent acquire/advance attempts cannot produce two owners for one Work Order.
- A failed transaction publishes no partial state or history.
- SQLite files are not placed in the managed workspace/repository.

### A5. Public dependency boundary

- Public skill and JSON schema contain no required `cncf` command, `cncf-*` skill, CNCF agent role, CNCF receipt locator, or machine-specific path.
- Skill/CML/domain source does not construct a JDBC URL, SQLite path, SQL statement, or table name.
- The standalone skill bundle can be validated without CNCF or a CAR resolver.
- Standalone and CAR-contained skill bundles have matching manifest and content digests.

### A6. Cost objective

- automatic transition count can increase without increasing semantic Work Order count.
- automatic transitions cause zero skill-requested Codex/model invocations.
- One semantic completion requires no extra Codex turn solely to determine the next state.
- Continuation payload size is measured and excludes full history and previous receipt bodies.
- Resume requires only run identity and bounded continuation context, not conversation replay.
- A current complete split proposal reaches validated apply with zero semantic AI Work Orders.
- A split without a reusable proposal requires zero semantic Work Orders when all work estimates and
  boundaries are already known; otherwise it requires one bounded novel-work/boundary Work Order.
  Deterministic inventory, numbering, projection, merge, and validation do not add AI calls.
- A `ModificationCollision` adds an AI Work Order unless authority or multiple-valid-resolution
  classification requires a human Decision; non-colliding composition adds neither.
- Known split work is estimated from the same frozen evidence and policy with reproducible results;
  candidate enumeration and partition selection add no AI Work Order.
- AI estimation is limited to work marked `UNRESOLVED_NOVEL`, and semantic classification is limited
  to unresolved boundaries.

### A7. Safety and authority

- Work Order acquisition does not grant shell, filesystem, network, commit, publish, or deployment authority.
- The thin skill continues to use the host's ordinary permission boundary.
- `advance` stops at user decision and new-authority boundaries.
- Cost reduction does not remove required validation, review, permission, or receipt.
- Workflow recommendation does not authorize or automatically start another Workflow; profile and
  child selection remain explicit human choices with separate run identities.

## Validation Plan

- CML generation and generated ABI compatibility checks
- unit tests for transition selection, fixed-point drain, continuation closure, idempotency, cycle/limit handling
- SQLite persistence, rollback, restart, and concurrent lease tests
- CLI JSON contract tests for every continuation outcome and typed conflict
- end-to-end reference workflow restart test
- split preview/apply/idempotency tests, including zero-AI proposal adoption, zero-AI known-work
  estimation/optimization, bounded novel-work enrichment, non-colliding composition,
  semantic-conflict delegation, and authority-conflict stop
- static dependency-boundary checks over public skill and schemas
- standalone/CAR skill bundle digest equivalence check
- cost-structure test correlating automatic transitions, semantic Work Orders, and client round trips
- CAR structure/lint and public documentation checks

All future top-level SBT validation must use the repository's machine-wide serialized SBT execution policy. Phase definition itself executes no validation.

## Closure Rule

Phase 1 is complete only when every required item in [phase-1-checklist.md](phase-1-checklist.md) is checked, all A1–A7 acceptance groups have reproducible evidence, and the evidence is bound to the final intended tree.

A partially working CLI, an in-memory-only engine, a skill that interprets state transitions itself, or a workflow that invokes Codex for automatic transitions does not satisfy Phase 1.

## Deferred Follow-Up

After Phase 1 closure, define separate phases for:

1. public skill catalog/install/update/uninstall distribution
2. optional MCP/server adapter
3. optional CNCF runtime and coexistence coordination adapter
4. additional document, research, review, release, and operations profiles
