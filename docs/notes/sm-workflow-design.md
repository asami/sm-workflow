# sm-workflow 設計ノート

- 状態: Draft
- 初版: 2026-09-16
- 更新: 2026-09-16（公開 skill の CNCF 非依存化）
- 更新: 2026-09-16（Textus 管理の SQLite local persistence 案）
- 更新: 2026-09-16（コスト低減目標と `advance` 中心設計）
- 更新: 2026-09-18（repository sync profile と managed Git provider 境界）
- 対象: Textus CAR `sm-workflow` と `sm-*` Codex skills
- 実装フェーズ: [Phase 1: Advance-Centered Local Workflow Core](../phase/phase-1.md)

## 1. 目的

`sm-workflow` は、Codex skill の指示文だけでは安定して維持しにくい長時間ワークフローを、Textus コンポーネントとして実行・永続化するための基盤である。

最初の実用対象は、現在 `cncf-goal-phase` などが担っている長時間の開発進行管理である。ただし `cncf-*` を置換したり、その状態を引き継いだりはしない。新しい `sm-*` 系列を独立して追加し、両系列を長期間併用できるようにする。Goal / Phase / Step は初期 profile の語彙であり、`sm-workflow` core の型にはしない。

同時に、本コンポーネントを CML の Workflow / StateMachine を実運用で使いこなす参照実装とする。

### 1.1 明示的な設計目標

`sm-workflow` は、LLM/Codex を意味判断が必要な作業にだけ使い、機械的な workflow 進行へ使わないことで、実行コストを低減する。これは副次効果ではなく主要な設計目標である。

コストには次を含む。

- Codex/model invocation 数
- 入力・出力 token と履歴再構築量
- agent/tool session の起動と往復
- 人間に不要な確認や中間報告を求める回数
- 再開時に状態を再推論するための処理

中心となる不変条件は次である。

> `advance` が機械的遷移を吸収し、Codex には次の意味的作業だけを返す。

目標は原則として「一つの意味的境界につき一回の Codex 作業、機械的遷移に対する Codex 作業はゼロ」とする。コスト低減を理由に validation、review、権限確認、証跡を省略してはならない。

## 2. 前提

### 2.1 確定している前提

- `cncf-*` と `sm-*` は長期間併用する。
- `sm-*` は新規の名前空間、状態、コマンド、配布物を使用する。
- `sm-*` skill は汎用的に公開し、利用者側に `cncf-*` skill、CNCF launcher、CNCF 固有 schema/runtime API を要求しない。
- `sm-*` skill は薄いクライアントとし、ワークフロー本体を skill の自然言語指示へ埋め込まない。
- workflow の状態遷移、再開、排他、冪等性、証跡は `sm-workflow` の公開 service contract の背後にある runtime が担う。
- local/standalone profile の既定永続化候補は、Textus runtime が管理する SQLite とする。
- Codex skill は planning、semantic edit/repair、review、例外分析を担う。定型化された
  validation、inspection、generation、commit、権限/状態検証は Workflow-owned typed
  operation または human Decision が担う。
- skill は CAR に同梱して配布できるようにする。
- skill bundle の形式は CAR や CNCF から独立した中立な公開契約とし、CAR 同梱と standalone 配布の両方を可能にする。
- Textus/CNCF 側には、その中立な bundle を生成・検証・導入する adapter を実装する。
- Cozy における WORKFLOW 開発フェーズの完了を、本実装の前提とする。

### 2.2 現時点の実装事実

- Cozy の既存 CML では `COMPOSITE-STATEMACHINE` が正規のルートであり、`WORKFLOW` は独立キーワードとして確定していない。
- CNCF の Workflow runtime 統合は Phase 64 で計画されており、`sm-workflow` runtime port の実装候補になる。
- 現在の `WorkflowEngine.inMemory` は長期実行、プロセス再起動、複数クライアントからの再開を満たさない。
- `cncf-launcher` と `textus-launcher` の skill install はそれぞれ計画段階である。ただし、これらは公開 bundle の利用に必須の依存ではなく、導入 adapter の候補である。
- Textus CAR には、runtime が SQLite file を component-local datastore として束縛し、再起動後に同じ状態を復元する既存実例がある。
- 現行 SQL datastore は SQLite に `busy_timeout=10000` と `transaction_mode=IMMEDIATE` を設定し、revision guard を用いた条件付き遷移を提供している。

したがって、以下の設計は到達目標であり、既存 API の実装済み機能を表すものではない。

## 3. 設計原則

1. **CML は構造を定義する。** Workflow、StateMachine、Operation、状態遷移、受理条件をモデル化する。
2. **公開 contract と runtime 実装を分離する。** `sm-workflow` は中立な service/runtime port を公開し、永続化や回復の具体実装を差し替え可能にする。
3. **core と profile を分離する。** `sm-workflow` core は Run / Stage / Work Item / Work Order を扱い、Goal / Phase / Step などの運用語彙は profile が対応付ける。
4. **skill は semantic AI との境界だけを担う。** component から bounded semantic work を取得し、型付き semantic result を返す。
5. **意味判断と決定的処理を分離する。** semantic AI Action の前後に deterministic prepare/admission を置き、定型化された外部作用は typed operation provider が実行する。raw shell や任意 command を Work Order にしない。
6. **一つの事実に一つの正本を置く。** workflow state の正本は Textus datastore とし、skill の会話履歴や Markdown を正本にしない。
7. **併用は共有状態ではなく明確な隔離で成立させる。** `cncf-*` と `sm-*` の暗黙変換、dual write、自動 migration は行わない。
8. **公開 skill に内部基盤を漏らさない。** CNCF 固有の command、schema、agent、receipt、directory を public skill contract に含めない。
9. **`advance` を唯一の通常進行入口にする。** deterministic な内部遷移を固定点まで評価し、次の semantic boundary だけを返す。
10. **履歴と continuation を分離する。** 完全な監査履歴は保存するが、Codex への通常応答には次の作業に必要な最小情報だけを載せる。
11. **profile invocation は人間が選ぶ。** recommendation と開始権限を分離し、skill、Workflow、terminal result が別 Workflow を自動開始しない。

### 3.1 汎用 core と workflow profile

公開部分を次の二層に分ける。

- generic core: WorkflowDefinition、WorkflowRun、Stage、WorkItem、WorkOrder、Decision、Evidence、Result/Receipt
- workflow profile: 開発手順や組織ごとの語彙、順序、受理条件を generic core に写像する定義

`GoalPhaseWorkflow`、`SplitPhaseWorkflow`、`RepositorySyncWorkflow` は generic core を
使う最初の Workflow definitions であり、CNCF 型の再公開ではない。対応するpublic
skillsはそれぞれ `sm-goal-phase`、`sm-split-phase`、`sm-repository-sync` とする。前二者は
Phase delivery と Phase planning split、後者は non-rewriting Git repository integration を
所有する。将来は同じ core 上に、文書制作、調査、release、運用手順など別用途の
Workflow/skill binding を追加できる。

skill名とWorkflow名は同一にせず、`SkillBundleManifest`の明示bindingを正本とする。

```text
sm-goal-phase  -> GoalPhaseWorkflow
sm-split-phase -> SplitPhaseWorkflow
sm-repository-sync -> RepositorySyncWorkflow
```

### 3.2 Profile invocation selection

`sm-goal-phase` と `sm-split-phase` は相互に呼び出す一つの dispatcher ではなく、別々の
human-selected entry point である。skill を直接選ぶ場合はその選択が exact Workflow
binding の invocation authority になる。generic host/UI は `SkillBundleManifest` の binding
を使って候補と説明を表示できるが、`StartWorkflowRun` は常に一つの exact
`workflowDefinitionId` を受け取り、runtime が目的や前 run の result から profile を推測
してはならない。

`GoalPhaseWorkflow` が split 必要性を判定した場合は `SPLIT_REQUIRED` terminal result と
advisory `SplitPhaseWorkflow` recommendation を返す。この result は別 run を開始せず、
人間が `sm-split-phase` を選択した場合にだけ独立した split run を作る。split 適用後も
child goal は開始せず、host が child 候補を表示し、人間が選択した child ごとに独立した
`sm-goal-phase` run を開始する。run identity、revision、Decision、Continuation、durable
state は Workflow 間で暗黙移送せず、必要な source/evidence/proposal reference だけを typed
start input として渡す。

## 4. 全体構成

```text
sm-* Codex skill
    |
    | advance / complete through typed CLI or MCP
    v
sm-workflow public service contract
    |
    | generated Workflow / StateMachine / Operation API + runtime port
    v
Workflow runtime implementation
    |-- standalone/Textus runtime + SQLite (default local provider)
    `-- optional CNCF runtime adapter
    |
    | persistent state, history, lease, idempotency
    v
Work Order
    |
    | Codex performs plan / semantic edit or repair / review / exception analysis
    v
Semantic Result
    |
    v
deterministic admission / validation / closing
    |
    `---- typed receipts and transition back into sm-workflow
```

CLI と MCP/server は異なる workflow 実装を持たない。同じ application service を呼ぶ二つの adapter とする。初期 vertical slice は CLI を主経路とし、対話性や remote access が必要になった時点で MCP/server を追加できる構造にする。

公開 `sm-*` skill が認識するのは `sm-workflow` CLI/MCP の versioned protocol までとする。その背後が standalone runtime、Textus runtime、CNCF adapter のどれであるかは観測も要求もしない。

通常経路では、skill が StateMachine の各 state/transition、候補operation、result disposition
を解釈しない。`advance` が次の一件のAI処理をexact `AIWorkRequest`として指定する。skillは
その依頼を実行し、typed `AIWorkResult`を同じWork Orderへ返すだけである。次の処理選択は
result受理後のWorkflowだけが行う。

## 5. 責務境界

| 層 | 所有するもの | 所有しないもの |
| --- | --- | --- |
| Cozy/CML | Workflow 表現、StateMachine、Operation、生成 ABI | runtime の永続化や Codex の実行方法 |
| `sm-workflow` public protocol | versioned command/API、JSON schema、capability negotiation | 特定 runtime/launcher の名称 |
| Workflow runtime port | instance、transition、lease、冪等性、回復、履歴 | profile 固有ポリシー |
| optional CNCF adapter | CNCF runtime への port 実装、既存環境との協調 | public skill contract |
| `sm-workflow` CAR | plan、Work Order、receipt 検証、運用ポリシー | Codex tool の直接操作 |
| `sm-*` skill | exact `AIWorkRequest` の一回実行と typed result 返却 | operation/state/next-action選択、result disposition、durable state |
| installer adapter | 中立 skill bundle の検証、導入、更新、削除 | bundle schema の所有、workflow の進行判断 |

### 5.1 公開 skill の許可依存

公開 bundle に含まれる skill が直接依存してよいものは次に限定する。

- Codex skill の標準ファイル規約
- versioned `sm-workflow` CLI または MCP protocol
- bundle に同梱された JSON Schema、template、説明資料
- manifest に明記された一般的な host capability

次は依存禁止とする。

- `cncf-*` skill の存在や呼び出し
- `cncf` launcher command
- `cncf.*` schema namespace
- CNCF 固有 agent role、receipt locator、temporary directory、lock path
- `/Users/asami/...` のような開発環境固有 path

CNCF 開発環境で必要な routing、SBT serialization、既存 workflow との協調は、公開 skill の外側にある host policy または optional adapter が扱う。

## 6. CML モデル

### 6.1 Workflow の表現

Cozy Phase 62 は `WORKFLOW` を CML の first-class declaration として導入する。
これは第二の状態遷移言語ではなく、既存 StateMachine / Composite StateMachine
semantics へ normalize する explicit Workflow identity と progression-boundary
contract である。具体的な grammar、lowering、validation、generated ABI version は
Phase 62 の release closure で固定し、`sm-workflow` はその ABI を独自 DSL や
生成物コピーで置き換えない。

### 6.2 状態機械

最小構成は次の四つの局所 StateMachine と、それらを束ねる Workflow である。

`WorkflowRunLifecycle`

```text
Requested -> Planning -> Active -> Completing -> Completed
                    |         |
                    |         +-> AwaitingUser -> Active
                    +------------> Failed
                    +------------> Cancelled
```

`WorkOrderLifecycle`

```text
Offered -> Leased -> Running -> Succeeded
    |         |          +-----> Failed
    |         +----------------> Expired
    +--------------------------> Cancelled
```

`DecisionLifecycle`

```text
Pending -> Resolved
    +----> Declined
    +----> Expired
```

`EvidenceLifecycle`

```text
Missing -> Submitted -> Accepted
                   +-> Rejected -> Submitted
                   +-> Stale ----> Submitted
```

上位 Workflow は局所状態から `Planning`、`Delivery`、`Acceptance`、`Closure` を導出する。profile 固有の Phase / Step / Slice などの個数に応じて動的に StateMachine 型を生成せず、generic な Stage / WorkItem の plan data として扱う。

### 6.3 Operation

CML Action は型付き Operation を参照する。raw shell、任意 script、skill 本文を Action の実体にはしない。

初期 Operation 候補:

- `StartWorkflowRun`
- `AdvanceWorkflowRun`
- `PlanWorkflowRun`
- `OfferWorkOrder`
- `AcquireWorkOrder`
- `StartWorkOrder`
- `SubmitWorkResult`
- `AcceptEvidence`
- `RejectEvidence`
- `ResolveDecision`
- `CompleteWorkflowRun`
- `FailWorkflowRun`
- `CancelWorkflowRun`

`StartWorkflowRun` は exact `workflowDefinitionId` と profile 固有 typed start input を必須に
し、recommendation、terminal result、skill名の文字列推測から別 Workflow を選択または
chain しない。別 Workflow の開始は常に新しい人間選択と新しい run identity を必要とする。

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

host/client adapter は UI または直接選択された public skill から
`WorkflowInvocationSelection` を構築する。`WorkflowRecommendation`、semantic AI result、
skill、Workflow runtime は selection record を自己生成できない。recommendation を利用する
場合も `recommendationReference` として相関させるだけで、`workflowDefinitionId` と
participant selection を置き換えない。

### 6.4 Automatic transition と semantic boundary

Cozy Phase 62 の CML Workflow と generated ABI は、遷移を少なくとも次の二種類に
分類できなければならない。具体的な CML 構文と source validation は Phase 62 が、
ABI admission と deterministic evaluator は CNCF Phase 77 が所有する。
`sm-workflow` は admitted classification を consume し、再分類しない。

`AutomaticTransition`

- 入力がすべて永続状態に存在する。
- 結果が deterministic である。
- LLM、人間、filesystem、network、外部process、追加権限を必要としない。
- 同じ database transaction 内で完了できる。
- idempotent、または revision guard により一度だけ適用される。

`SemanticBoundary`

- 計画、編集、評価、レビューなどの意味判断を必要とする。
- workspace/tool/外部serviceへの作用を必要とする。
- ユーザー判断や新しい権限を必要とする。
- 外部結果、時刻、lease、job completion を待つ必要がある。

`advance` は `AutomaticTransition` だけを実行する。semantic boundary を自動化したと推測して越えてはならない。

## 7. ドメインモデル

### 7.1 WorkflowRun

主要属性:

- `runId`
- `workflowKind`
- `projectIdentity`
- `targetIdentity`
- `status`
- `revision`
- `createdAt` / `updatedAt`
- `requestedBy`
- `activePlanRevision`
- `terminalReason`

### 7.2 Plan

Plan は Stage / WorkItem の順序、依存、完了条件を表す immutable revision である。Goal / Phase / Step などを使う profile は、それらを Stage / WorkItem へ写像する。進行中の Plan を上書きせず、新 revision を作り、`WorkflowRun.activePlanRevision` を明示的な遷移で更新する。

### 7.3 WorkOrder

Work Order は component から Codex skill へ渡す、実行可能だが権限を限定した仕事の単位である。

主要属性:

- `workOrderId`
- `runId`
- `kind`: `PLAN | EDIT | REPAIR | REVIEW | EXCEPTION_ANALYSIS`
- `scope`: 対象 repository、worktree、path、論理境界
- `inputRevision`
- `expectedResultSchema`
- `idempotencyKey`
- `leaseOwner`
- `leaseExpiresAt`
- `preconditions`
- `acceptanceCriteria`

Work Order は「何を達成すべきか」と「返却形式」を表し、任意コマンド列を運ばない。
`VALIDATE`、`COMMIT`、`USER_DECISION` は Work Order kind にしない。前二者は
Workflow-owned deterministic operation、後者は `Decision` boundary とする。

すべての Work Order は Workflow が構築した immutable input snapshot を受け取る。
semantic Result は別の deterministic admission state で schema、authority、owned delta、
evidence freshnessを検証されるまで Workflow state や acceptance ledger を変更しない。

AI executor向けWork Orderは、候補一覧ではなく一つのfully materialized requestを持つ。

```text
AIWorkRequest
  workOrderId
  runId
  expectedRevision
  operationIdentity
  operationVersion
  objective
  typedInput
  expectedResultSchema
  evidenceContract
  allowedMutationScope?
  lease
```

skillは`operationIdentity`を見て別skill、command、agent、workflow処理を選択しない。
Workflow/generated bindingが既に選んだrequestをそのまま実行する。返却も次だけとする。

```text
AIWorkResult
  workOrderId
  runId
  expectedRevision
  operationIdentity
  result
  evidenceReferences
  idempotencyKey
```

`AIWorkResult`にnext operation/state、command request、routing directiveを含めない。

### 7.4 Result / Receipt

Result は意味上の結果、Receipt はその結果を裏付ける証跡である。

例:

- 編集対象と resulting tree identity
- validation command identity、終了状態、実行時刻
- review disposition と blocker ID
- commit identity
- user decision と入力者

秘密情報、巨大な標準出力、無制限の会話履歴は receipt に保存しない。必要な要約と content-addressed artifact reference を保存する。

### 7.5 Continuation

`advance` は内部状態や遷移候補の一覧ではなく、次の一つの boundary を表す `Continuation` を返す。

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

- `WORK_ORDER`: Codex等のexecutorが行う次の意味的作業を一件だけ返す。
- `DECISION`: ユーザーまたはpolicy ownerの判断が必要である。
- `WAIT`: 他のowner、job、timer、外部eventを待つ。Codexにpolling作業を返さない。
- `TERMINAL`: completed、failed、cancelled の最終結果を返す。

完全な transition history や過去の receipt 本文は continuation に展開せず、必要なら `diagnosticReference` から別途取得する。

## 8. 実行プロトコル

### 8.1 `advance` の評価

`advance(runId, executorIdentity, capabilities, expectedRevision, idempotencyKey)` は次を行う。

1. revision、idempotency、既存leaseを検証する。
2. 現在有効な automatic transition を一意に選ぶ。
3. state 更新と transition event 追記を行う。
4. automatic transition が続く間、上限回数まで 2–3 を繰り返す。
5. semantic boundary に到達したら `Continuation` を一件返す。
6. `WORK_ORDER` で executor identity が与えられている場合は、可能なら発行とleaseを同じtransactionで行う。

複数の automatic transition が同時に有効で優先順位が決まらない場合は model invariant error とする。循環や異常に長い列は `maxAutomaticTransitions` で停止し、Codexに解決作業として渡さず診断可能な内部エラーにする。

### 8.2 skill の単一依頼実行

1. Workflow/host がlease済みのexact `AIWorkRequest`をskillへ渡す。
2. skillは指定された一件のsemantic AI処理だけを実行する。
3. skillはtyped `AIWorkResult`またはtyped failureを同じWork Orderへ提出して終了する。
4. componentはresultをdeterministic admissionし、同じ`advance` evaluatorで次の
   `Continuation`を選ぶ。
5. host/client adapterは返された`Continuation`をそのkindどおりに配送する。skillは
   result内容を見て次のskill、operation、command、Decisionを呼び分けない。

`DECISION`のuser presentation、`WAIT`のwake registration、`TERMINAL`の表示は
host/client adapterのmechanical envelope handlingであり、semantic AI skillの判断ではない。
`SPLIT_REQUIRED` terminal では host/client adapter が `sm-split-phase` を候補表示できるが、
開始は人間の明示選択後に別の `StartWorkflowRun` として行う。

`start`、`work complete`、`work fail`、`decision resolve` は、通常は server-side で
`advance` を続けて同じ `Continuation` envelope を返す。明示的 `advance` は再開、競合
回復、診断後の継続に使う。この合成は次の処理選択をWorkflow内で完了させるためのもので、
skillにdispatcher loopを持たせるものではない。

外部作用の最中に datastore transaction を保持しない。lease 期限切れ後の再取得を許容し、同じ `idempotencyKey` の重複提出は同じ `Continuation` を返す。

## 9. コマンド面

初期 CLI 案:

```text
sm-workflow run start --workflow <workflow-id> --target <target> --workspace <path>
sm-workflow run advance <run-id> --executor <executor-id>
sm-workflow work start <run-id> <work-order-id>
sm-workflow work complete <run-id> <work-order-id> --receipt <file>
sm-workflow work fail <run-id> <work-order-id> --receipt <file>
sm-workflow decision resolve <run-id> <decision-id> --input <file>
sm-workflow run status <run-id>
sm-workflow run history <run-id>
```

すべての mutation command は `expectedRevision` と `idempotencyKey` を内部または明示引数で持つ。人間向け表示と machine-readable JSON output を分け、skill は JSON の `Continuation` を使用する。`status` と `history` はread-onlyであり、自動遷移を起こさない。

### 9.1 コスト観測

run ごとに少なくとも次を観測する。

- `automatic_transition_count`
- `semantic_work_order_count`
- `client_round_trip_count`
- `continuation_payload_bytes`
- `resume_context_payload_bytes`

model invocation/token の実測値をhostから受け取れる場合は別metricとして記録する。取得できない場合も、上の構造指標で「機械的遷移がCodexへ漏れていないか」を検証できるようにする。

## 10. 永続化、排他、回復

- WorkflowRun、Plan、WorkOrder、Result、Receipt、Decision、transition history を永続化する。
- mutation は optimistic concurrency の `revision` 検査を必須とする。
- Work Order は期限付き lease と heartbeat を持つ。
- process 再起動後は datastore から run を復元し、未完了 order を再評価する。
- accepted receipt に依存する後続 order は、その receipt が stale になった場合に自動実行しない。
- 同じ worktree の重複編集は workflow 内 lease だけでなく、系列横断の execution lease でも防ぐ。

### 10.1 SQLite local persistence

local/standalone profile では SQLite を既定の物理 backend とする。ただし、SQLite は公開 skill や workflow domain の依存ではない。

- `sm-*` skill は DB file、SQL、JDBC、SQLite driver を認識しない。
- workflow domain/service は `WorkflowStore`、`TransitionStore`、`LeaseStore` などの Textus-owned port のみに依存する。
- Textus runtime/launcher が SQLite provider、database path、connection lifecycle、migration を構成する。
- test は同じ port に一時 SQLite または in-memory provider を束縛する。
- server/multi-host deployment では、同じ port に PostgreSQL 等の別 provider を束縛できる。

一つの local Textus installation に、一つの `sm-workflow` component-local database を置き、その中で複数 workspace と WorkflowRun を管理する。repository ごとに DB を埋め込まない。これにより、別 repository/worktree をまたぐ run の検索と `WorkspaceMutationLease` の一元的な判定が可能になる。

database の具体 path は Textus の platform-aware local-data resolver が決める。skill、CML、domain logic に home directory や固定 path を埋め込まない。`~/.cncf/...` は公開 `sm-workflow` の既定 path にしない。

### 10.2 Transaction boundary

一回の workflow mutationまたは一回のbounded `advance` drainでは、次を一つの短い transaction で確定する。

1. expected revision / idempotency key / lease の検証
2. current state の更新
3. transition event の追記
4. automatic transition のbounded評価
5. 新しい Work Order、Decision、Evidence requirement の発行と任意のlease
6. idempotency resultと返却Continuationの記録

semantic AIによる編集/レビュー、Workflow providerによる外部validation/commit、human
Decision待ちはtransaction外で行う。SQLite write lockを外部作業の間保持しない。
Work Order、operation attempt、Decisionの開始/完了は条件付き遷移とrevision guardで
競合を検出する。

SQLite provider の初期運用要件:

- WAL mode
- foreign key enforcement
- bounded busy timeout
- explicit immediate write transaction
- UTC timestamp
- integrity check と SQLite-safe backup
- schema version と forward-only migration history

durability (`synchronous`) の値は Textus provider policy で決める。workflow state は再生成不能なユーザー判断や外部作用の receipt を含むため、性能だけを理由に durability を弱めない。

### 10.3 Logical storage shape

初期の論理 collection/table 候補:

- `workflow_definition`
- `workflow_run`
- `plan_revision`
- `stage`
- `work_item`
- `work_order`
- `work_order_attempt`
- `decision`
- `evidence`
- `receipt`
- `transition_event`
- `idempotency_record`
- `workspace_mutation_lease`
- `schema_migration`

名前は論理モデルであり、domain code が SQL table 名として参照するものではない。current state と append-only transition history を分け、同じ transaction で同期する。大きなログやartifact本体は DB に無制限に格納せず、digest、media type、size、Textus-managed artifact reference を receipt に保持する。

### 10.4 適用限界

SQLite profile は単一マシン上の CLI、loopback server、少数の並行 Codex task を対象とする。network filesystem 上の DB、複数ホストからの直接共有、active-active server は対象外とする。その要件が生じた場合は workflow contract を変えず、storage provider を切り替える。

## 11. `cncf-*` と `sm-*` の長期併用

### 11.1 名前と状態の分離

| 項目 | `cncf-*` | `sm-*` |
| --- | --- | --- |
| skill 名 | `cncf-goal-phase`, `cncf-split-phase`, `cncf-repository-sync` 等 | `sm-goal-phase`, `sm-split-phase`, `sm-repository-sync` 等 |
| executable | 既存 launcher/skill contract | `sm-workflow` |
| workflow state | 既存 `.codex-workflow` 等 | Textus datastore |
| schema namespace | 既存契約 | `sm.workflow.*`（案） |
| projection/config | 既存契約 | `.sm-workflow` / `.sm/workflow.yaml`（案） |

`.sm-workflow` と `.sm/workflow.yaml` は未確定の候補名であり、初回 implementation phase で固定する。

### 11.2 禁止事項

- `.codex-workflow` の自動 import
- `cncf-*` と `sm-*` への dual write
- 一方の terminal state を他方の terminal state と暗黙に同一視すること
- 同一 worktree・重複 path に対する両系列の同時 mutation
- legacy `cncf-goal-phase` / `cncf-split-phase` / `cncf-repository-sync` skill の rename、overwrite、forwarding
- `sm-goal-phase` / `sm-split-phase` / `sm-repository-sync` から対応する `cncf-*` skill を内部呼び出しすること

将来 migration が必要になった場合は、独立した明示コマンドとして設計し、まず read-only preview を要求する。

### 11.3 最小共有境界

両系列が workflow state を共有しなくても、同じ filesystem を破壊しないための中立な `WorkspaceMutationLease` port は共有できるようにする。

最低限のキー:

- canonical repository/worktree identity
- mutation 対象 path set
- owner namespace: reverse-domain 形式などの衝突しない任意文字列
- owner run/task identity
- acquired/expiry/heartbeat time

これは協調用の排他情報であり、workflow の意味状態ではない。`sm-workflow` core は特定 owner を列挙せず、CNCF との併用時だけ optional coordination adapter が既存系列の owner と相互運用する。同一 repository を扱う場合は別 worktree を標準運用とする。machine-wide SBT lock への接続も CNCF 開発環境用 host adapter の責務とし、公開 skill の依存にはしない。

## 12. skill bundle と配布

### 12.1 CAR の内容

`sm-workflow` CAR は少なくとも次を含む。同じ bundle directory は CAR から切り出して standalone artifact としても配布できる。

```text
component and generated workflow definitions
SkillBundleManifest
skills/sm-goal-phase/
skills/sm-split-phase/
skills/sm-repository-sync/
skills/sm-goal-task/          # 後続
skills/sm-review/             # 後続
skills/sm-validated-commit/   # 後続
```

skill bundle は component version、required `sm-workflow` protocol version、各 file の digest、install scope、entry skill、任意の MCP descriptor を manifest に持つ。CNCF の artifact coordinate、command、schema を必須 field にしない。
各entry skillは対応する`workflowDefinitionName`とcompatible definition version rangeを
明示し、skill名からdefinition名を推測しない。

### 12.2 中立な公開契約

`SkillBundleManifest` は特定 component framework や launcher に所属しない公開 schema として定義する。bundle 自体の検証は manifest と content digest だけで完結し、CAR metadata や CNCF runtime への問い合わせを要求しない。

- standalone bundle は標準的な skill directory として取得・検証・導入できる。
- CAR は standalone bundle と同じ bytes/manifest を同梱できる。
- `textus skill ...` は published CAR 用の任意 installer adapter とする。
- `cncf skill ...` は development source 用の任意 installer adapter とする。
- どちらの launcher も public skill の実行時依存にはしない。
- source-tree、standalone artifact、packaged CAR の digest/manifest 同値性を acceptance 対象にする。
- install は server 起動、任意 script 実行、AI/MCP 呼び出しを行わない。
- 既存 skill の上書きは暗黙に行わない。
- MCP 設定変更は `--configure-mcp` のような明示操作に分ける。

### 12.3 CNCF 側で作り込む範囲

CNCF 側の作業は公開 schema の所有ではなく、次の adapter/conformance 実装とする。

- development source から中立 bundle を組み立てる producer adapter
- manifest/digest を検証する installer adapter
- CAR package へ同じ bundle を収録する sbt-cozy integration
- `WorkspaceMutationLease` と既存 workflow/lock を接続する coordination adapter

これにより CNCF 環境では十分に統合しつつ、公開 skill は CNCF が存在しない環境でも導入・実行できる。

## 13. セキュリティと権限

- component は Work Order によって目的と scope を提示するが、Codex の権限を拡大しない。
- skill は各 tool の既存 approval/permission contract に従う。
- Work Order の受領は shell 実行の包括承認を意味しない。
- commit、publish、remote mutation は個別 workflow とユーザー権限に従う。
- server mode では caller identity、project authorization、audit history を必須にする。
- Result/Receipt は schema 検証し、表示用文字列を命令として再解釈しない。

## 14. 実装順序

1. **Cozy Phase 62: CML WORKFLOW Language and Producer ABI**
   - first-class `WORKFLOW` root と StateMachine / Composite StateMachine
     semantics への lowering
   - automatic transition / typed semantic boundary の closed classification
   - normalization、validation、generated ABI、producer fixture
2. **CNCF Phase 77: WORKFLOW ABI Admission and Progression Contract**
   - generated ABI admission と ComponentFactory bootstrap
   - WorkflowInstance persistence SPI と deterministic progression evaluator
   - existing `ExecProgram[UnitOfWorkOp, A]` への typed Operation / Action
     integration
3. **公開 protocol/runtime port phase**
   - CNCF 固有型を含まない CLI/JSON schema
   - `AdvanceWorkflowRun` と `Continuation`
   - durable WorkflowInstance の port
   - recovery、revision、idempotency、lease、history、observability の contract
4. **runtime adapter phase**
   - Textus-managed SQLite を既定とする standalone 実装
   - storage port と provider-neutral domain boundary
   - CNCF Workflow runtime と Operation/Job への optional adapter
5. **中立 skill bundle phase**
   - framework-neutral `SkillBundleManifest`
   - standalone artifact と CAR 同梱
   - Textus/CNCF installer adapter の conformance
6. **`sm-workflow` vertical slice**
   - `start/advance -> edit -> complete/advance -> validate -> complete/advance -> review -> complete`
   - 中間の機械的遷移はCodexへ返さない
7. **`sm-goal-phase` / `sm-split-phase` / `sm-repository-sync` profile skills**
   - versioned `sm-workflow` protocol を呼ぶ薄い client
   - Goal / Phase / Step を generic Stage / WorkItem に写像
   - resume と user decision を含む
   - CNCF command、type、state、skill を参照しない
   - legacy `cncf-goal-phase` / `cncf-split-phase` / `cncf-repository-sync` は変更せず併用する
8. **併用 acceptance**
   - 両系列の同時 install
   - 別 worktree での並行運用
   - 同一 path mutation の拒否
   - development/published skill bundle の同値性
9. **適用範囲の拡張**
   - `sm-goal-task`
   - `sm-review`
   - `sm-validated-commit`

## 15. 最初の vertical slice の完了条件

- プロセスを終了しても run を再開できる。
- 同じ Textus-managed SQLite database を再度開き、run、pending Work Order、Decision、history を復元できる。
- 同じ completion を再送しても重複遷移しない。
- revision conflict と lease conflict が機械可読に返る。
- skill が会話履歴なしで `run advance` から再開できる。
- edit、validate、review の receipt が受理条件に使われる。
- user decision 待ちを terminal failure と混同しない。
- `cncf-*` の状態やファイルを変更しない。
- CNCF が導入されていない環境でも public bundle を検証・install できる。
- public skill は `cncf` command、`cncf-*` skill、CNCF 固有 schema を参照しない。
- 両系列を install した状態で名前衝突しない。
- 同一 worktree/path の同時 mutation を検出して拒否する。
- source-tree、standalone artifact、published CAR の skill bundle が同じ内容として検証される。
- skill/CML/domain source が JDBC URL、SQLite path、SQL table 名を直接参照しない。
- transition と次の Work Order 発行が同一 transaction で確定する。
- SQLite の同時 acquire test で一つの Work Order に複数 owner が成立しない。
- SQLite file を network filesystem や複数ホスト共有へ暗黙昇格しない。
- 複数のautomatic transitionを含むworkflowが、一回の`advance`で最初のsemantic Work Orderまで進む。
- `work complete`の応答が、中間stateを公開せず次のsemantic `Continuation`を返す。
- automatic transitionごとのCodex/model invocationがゼロである。
- `WAIT`がCodexによるbusy pollingを要求しない。
- `advance`の再送が同じrevision/idempotency keyに対して同じContinuationを返す。
- continuationに完全履歴を展開せず、次の作業に必要なbounded contextだけを返す。
- automatic transitionの循環または上限超過を内部エラーとして検出する。

## 16. 未決事項

- CML の表記を `WORKFLOW` root にするか Composite StateMachine profile にするか。
- CMLでautomatic transition / semantic boundaryを表す具体的なannotation/profile。
- `maxAutomaticTransitions`の既定値と循環診断形式。
- mutation commandがserver-side advanceを行う既定policyと、診断用停止option。
- cost metricの保持期間と数値目標。
- component identity、package namespace、CAR coordinate。
- SQLite local profile と将来の server/multi-host provider を切り替える storage port API。
- Textus platform-aware local-data root の規約と database file 名。
- SQLite provider の WAL、foreign key、durability、backup、migration の確定設定。
- `.sm-workflow` projection と `.sm/workflow.yaml` の要否・名称。
- `WorkspaceMutationLease` の中立 port と既定実装をどこが所有するか。
- 公開 `SkillBundleManifest` schema の配布先と versioning policy。
- MCP/server を最初の slice に含めるか、CLI acceptance 後にするか。
- receipt artifact の保管先、保持期間、redaction policy。
- BoK/CBD catalog 上に既存または類似 component identity があるか。

## 17. 関連資料

- `/Users/asami/src/dev2025/cozy/docs/phase/phase-47.md`
- `/Users/asami/src/dev2025/cozy/docs/design/cml-composite-statemachine-grammar-validation.md`
- `/Users/asami/src/dev2025/cozy/docs/journal/2026/09/2026-09-05-cml-workflow-cncf-runtime-coordination.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-64.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/09/2026-09-05-cml-workflow-runtime-continuity.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/workflow/WorkflowEngine.scala`
- `/Users/asami/src/dev2026/cncf-launcher/docs/phase/phase-1.md`
- `/Users/asami/src/dev2026/textus-launcher/docs/phase/phase-1.md`
- `/Users/asami/src/dev2026/textus-change-management/src/main/cozy/textus-change-management.cml`
- `/Users/asami/src/dev2026/textus-cbd-support/docs/journal/2026/07/phase-8-p8-42-sqlite-storage-decision-2026-07-23.md`
- `/Users/asami/src/dev2026/textus-art-scene/src/test/scala/org/simplemodeling/textus/artscene/ArtSceneStandaloneRestartSpec.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/spec/component-local-datastore-layout.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/datastore/SqliteConditionalTransitionSpec.scala`
