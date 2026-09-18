# sm-workflow 設計方針

- 日付: 2026-09-16
- 状態: Design recorded / implementation not started
- 詳細設計: [sm-workflow 設計ノート](../../../notes/sm-workflow-design.md)
- 実装フェーズ: [Phase 1: Advance-Centered Local Workflow Core](../../../phase/phase-1.md)

## 背景

`cncf-goal-phase` などの skill による workflow は、長時間にわたる状態、再開、排他、証跡を自然言語指示だけで安定して管理するには限界がある。この問題を skill の追記だけで解消せず、CML Workflow / StateMachine を使う Textus コンポーネントとして実現する方向を採る。CNCF runtime は内部 adapter の候補であり、公開 skill の依存にはしない。

新系列の名称は `sm-*` とする。既存 `cncf-*` は置き換えず、長期間併用する。

## 今回確定した方針

1. `sm-workflow` を Textus CAR として新規開発する。
2. `sm-*` skill は `sm-workflow` の CLI、将来的には同一 service contract の MCP/server を呼ぶ薄い client とする。
3. workflow state の正本は component/runtime 側に置き、skill の会話履歴には置かない。
4. component は型付き Work Order を発行し、Codex skill が編集、検証、レビュー、commit を実行して Result/Receipt を返す。
5. component は raw shell や任意 script を直接実行しない。CML Action は型付き Operation に結び付ける。
6. `cncf-*` と `sm-*` の workflow state は共有せず、自動 import、dual write、暗黙 migration を行わない。
7. 同じ worktree/path への同時 mutation だけは、中立な `WorkspaceMutationLease` port で拒否する。
8. `sm-*` skills は CAR に同梱し、component と共に配布可能にする。
9. skill bundle は framework-neutral な公開 manifest とし、standalone 配布と CAR 同梱の両方を可能にする。
10. Cozy Phase 62 と CNCF Phase 77 の完了済み handoff を Phase 1 の先行条件とする。
11. 公開 `sm-*` skill は `cncf-*` skill、`cncf` command、CNCF 固有 schema/runtime API に依存しない。
12. local/standalone profile の永続化には、Textus runtime が管理する SQLite を第一候補とする。
13. Codex/model利用コストの低減を副次効果ではなく、明示的な設計目標とする。
14. `advance`を通常進行の中核operationとし、機械的遷移を吸収して次の意味的作業だけを返す。
15. 最初の実装フェーズを、Textus-managed SQLite、`advance`、CLI、reference workflow、薄いpublic skillまでのlocal vertical sliceとして定義する。

## Phase 1 定義

Phase 1 `Advance-Centered Local Workflow Core` と、そのclosure authorityとなるchecklistを作成した。

- [Phase 1](../../../phase/phase-1.md)
- [Phase 1 checklist](../../../phase/phase-1-checklist.md)

Phase 1には`sm-goal-phase`の完全実装、installer、MCP/server、CNCF adapterを含めない。まずgenericなreference workflowで「automatic transitionはTextusが吸収し、Codexにはsemantic Work Orderだけを返す」ことを、SQLite再起動・競合・冪等性・コスト構造を含めて閉じる。

### 2026-09-18 Phase 1 scope revision

上記の `sm-goal-phase` deferred 判断を更新し、Phase 1 に `GoalPhaseWorkflow` と
`SplitPhaseWorkflow`、および対応する thin public skills `sm-goal-phase` と
`sm-split-phase` を含める。Workflow名とskill名はmanifestで明示的にbindする。
legacy `cncf-goal-phase` / `cncf-split-phase` は変更せず、別名・別状態の互換workflow
として長期併用する。generic `sm-workflow-run` / `sm-workflow-resume` skill は Phase 1 の
配布skill名にせず、profile skills が versioned `sm-workflow` protocol を直接利用する。

## 追記: コスト低減と `advance`

`sm-workflow` の中心価値を、durable stateだけでなく「LLMを必要な意味判断に限定すること」と定める。

設計上の原則:

- automatic transitionにCodex/modelを呼ばない。
- 原則として一つのsemantic boundaryにつき一回のCodex作業とする。
- `advance`はdeterministicな遷移をbounded fixed pointまで進める。
- Codexへ返すのは次の一件の`WORK_ORDER`、`DECISION`、`WAIT`、`TERMINAL`のいずれかだけとする。
- 完全履歴はSQLiteへ保持するが、通常Continuationには展開しない。
- `WORK_ORDER`の発行とleaseは、executorが分かる場合に同じtransactionで行う。
- `work complete`や`decision resolve`は受理後にserver-side `advance`を実行し、次のContinuationを同じ応答で返す。
- `WAIT`はwake conditionを返し、Codexによるpollingを発生させない。

automatic transitionは、永続済み入力だけで決まり、外部I/O、追加権限、LLM、人間判断を必要としない遷移に限定する。編集、validation、review、user decision、外部job待ちはsemantic boundaryとして越えない。

コスト低減が安全性低下にならないよう、validation、review、permission、receiptは維持する。削減対象はそれらの間にある状態確認、分岐選択、完了判定、次order生成などの機械的な進行である。

## 追記: 公開 skill の CNCF 非依存化

公開 skill を汎用的に配布するため、当初の「skill bundle を CNCF の汎用契約として所有する」という表現を撤回する。最新版の判断は次である。

- `SkillBundleManifest` は特定 framework/launcher に所属しない中立 schema とする。
- public skill が呼ぶのは versioned `sm-workflow` CLI/MCP protocol だけとする。
- core の語彙は WorkflowRun / Stage / WorkItem / WorkOrder とし、Goal / Phase / Step は任意 profile の語彙とする。
- workflow runtime は port として公開し、CNCF はその optional implementation adapter にする。
- Textus/CNCF launcher は bundle の producer/installer adapter になれるが、実行時依存にはしない。
- CNCF 固有の agent、receipt、temporary path、SBT lock は host integration に隔離する。
- CAR に同梱した bundle と standalone bundle は同一 manifest/content digest を持つ。

## 追記: Textus-managed SQLite persistence

workflow の正本は Textus 側にあるため、local/standalone profile では SQLite を既定 backend とする案を採用する。公開 skill は SQLite に依存せず、`sm-workflow` service contract だけを見る。

境界は次のようにする。

- Textus runtime/launcher が SQLite provider、path、lifecycle、migration を所有する。
- workflow domain は `WorkflowStore` 等の provider-neutral port を使い、JDBC、SQL、SQLite type/path を直接扱わない。
- 一つの component-local database で複数 workspace/run と横断 lease を管理する。
- state transition、history 追記、次の Work Order 発行、idempotency record を一つの短い transaction にする。
- Codex の編集、validation、review、user decision 待ちは transaction 外に置く。
- SQLite profile は single-machine 用とし、multi-host は別 datastore provider に切り替える。

既存 Textus/CNCF 実装には、この判断を支える次の evidence がある。

- Textus CBD Support は SQLite を development profile の backend とし、component/domain から SQLite/JDBC/path を隠す方針を採用している。
- Textus ArtScene は同じ SQLite file を新しい runtime から開き、状態が再起動後も残ることをテストしている。
- 現行 SQLite datastore は bounded busy timeout と immediate transaction を設定している。
- 条件付き遷移は revision guard、競合時の非更新、transaction rollback を検証している。

未確定なのは Textus-owned local-data root、database file 名、WAL/foreign-key/durability の最終設定、backup/migration policy である。既存の `~/.cncf/...` default path を公開 `sm-workflow` の既定にはしない。

## Phase 1 の upstream Phase 依存

Phase 1 の開始条件を、Cozy Phase 62 と CNCF Phase 77 の連続した release
handoff に固定した。

- **Cozy Phase 62 — CML WORKFLOW Language and Producer ABI** は、first-class
  `WORKFLOW` declaration、StateMachine / Composite StateMachine への lowering、
  typed Operation reference、automatic/semantic-boundary の closed contract、
  generated Workflow ABI、real CML fixture を所有する。`advance`、SQLite、lease、
  public skill は所有しない。
- **CNCF Phase 77 — WORKFLOW ABI Admission and Progression Contract** は、Cozy
  ABI の fail-closed admission、ComponentFactory discovery、independent
  WorkflowInstance persistence SPI、bounded deterministic progression evaluator、
  existing `ExecProgram[UnitOfWorkOp, A]` への typed Action/Operation binding を
  所有する。default datastore、SQLite、public CLI/server、skill distribution、
  Codex cost policy は所有しない。
- **Textus `sm-workflow` Phase 1** は、Phase 77 の reusable contract を
  component-local SQLite binding、WorkflowRun / WorkOrder / Continuation、lease、
  public protocol、公開 skill、cost observability に具体化する。公開契約は CNCF
  固有型を露出しない。

Phase 1 は、両 upstream Phase の completed release、互換 ABI version、real-source
fixture identity、Cozy/CNCF の exact commit を記録するまで開始しない。未完了の
phase、暫定 fixture、hand-written definition を根拠に `sm-workflow` 内で独自 DSL、
生成物コピー、CNCF ABI emulator を作らない。

## 最小実用フロー

```text
start -> plan -> edit -> validate -> review -> complete
```

各外部作用は Work Order として lease され、Result/Receipt の受理後にだけ状態が進む。`advance`はその前後の機械的遷移を吸収する。プロセス終了後は datastore から run を復元し、skill は会話履歴なしで次のsemantic orderを取得できるものとする。

初期の Work Order 種別は次とする。

- `PLAN`
- `EDIT`
- `VALIDATE`
- `REVIEW`
- `COMMIT`
- `USER_DECISION`

### 2026-09-18 semantic/deterministic separation revision

上記の「各外部作用をWork Orderにする」設計を更新する。Work Orderは `PLAN | EDIT |
REPAIR | REVIEW | EXCEPTION_ANALYSIS` のsemantic AI workだけに限定する。`VALIDATE` と
`COMMIT` はWorkflow-owned typed deterministic operation、`USER_DECISION` は独立した
Decision boundaryとする。

すべてのsemantic Actionはdeterministic prepareでimmutable inputを受け取り、resultは
別のdeterministic admission/validationを通るまでstate、ledger、acceptance、commit
readinessを変更しない。AI resultはnext state、command、validation acceptance、cycle
count、retry policy、commit readinessを含めない。

skillはWorkflowが選びleaseした一件のfully materialized AI requestを実行し、matching
resultを返して終了する。result内容から次のskill、agent、operation、command、Decisionを
呼び分けるdispatcherにはしない。follow-upはadmission後の`advance`だけが選ぶ。

## 長期併用の境界

`cncf-*` と `sm-*` は、skill 名、command 名、schema namespace、workflow state を分離する。併用の安全性は「両方を同一状態へ書かせること」ではなく「独立状態と狭い共有排他」によって確保する。

同じ repository を同時に扱う必要がある場合は別 worktree を標準とする。同じ worktree かつ重複 path の mutation は拒否する。lease の owner は `cncf|sm` の固定列挙ではなく衝突しない namespace とする。machine-wide SBT serialization への接続は CNCF 開発環境用 adapter の責務であり、公開 skill には見せない。

## skill bundle 配布

standalone artifact、開発ソース、published CAR が、同一の中立 `SkillBundleManifest` と digest 規則を使う構成にする。

- standalone bundle: CNCF/Textus を必要としない標準 skill directory
- `cncf skill ...`: development source 用の任意 adapter
- `textus skill ...`: published CAR 用の任意 adapter

両 launcher は public skill の実行時依存にはしない。install 自体は server 起動、任意 script、AI/MCP 呼び出しを行わない。MCP 設定変更は明示 option に分離し、既存 skill を暗黙に上書きしない。

## 根拠として確認した既存計画・実装

- Cozy Phase 47 は Composite StateMachine を完了し、Workflow をその specialization/profile として位置付けている。
- CML grammar/validation 設計は raw script ではなく型付き Operation を Action に用いる。
- CNCF Phase 64 / 64.2 は Composite StateMachine、Workflow runtime、既存 `UnitOfWorkOp` 実行経路の前提を計画している。Phase 77 が Cozy Phase 62 の first-class Workflow ABI admission と progression contract をその上に追加する。
- 現在の CNCF `WorkflowEngine.inMemory` は durable workflow の要件を満たさない。
- `cncf-launcher` Phase 1 は development source の skill install を計画している。
- `textus-launcher` Phase 1 は published CAR の skill bundle install を計画している。

参照先は設計ノートの「関連資料」に記録した。

## 実装順序

1. Cozy Phase 62: first-class `WORKFLOW` source、generated ABI、producer fixture
2. CNCF Phase 77: ABI admission、ComponentFactory、WorkflowInstance SPI、progression evaluator
3. CNCF 固有型を含まない public protocol と runtime port
4. `AdvanceWorkflowRun`、`Continuation`、upstream progression classification の binding
5. Textus-managed SQLite standalone runtime と optional CNCF runtime adapter
6. 中立 `SkillBundleManifest`、standalone bundle、CAR 同梱
7. Textus/CNCF producer/installer adapter
8. `advance`中心の`sm-workflow` vertical slice
9. CNCF 型に依存しない `sm-goal-phase` / `sm-split-phase` profile skills
10. legacy `cncf-goal-phase` / `cncf-split-phase` を変更しない併用 acceptance
11. 文書制作、調査、release など別 profile への展開

## 保留事項

- Cozy Phase 62 が確定する CML Workflow の具体構文と ABI version（`sm-workflow` は再定義しない）
- Cozy Phase 62 / CNCF Phase 77 が確定する automatic transition / semantic boundary の表現と admission diagnostics（`sm-workflow` は consume する）
- `maxAutomaticTransitions`と循環診断
- server-side auto-advanceの既定policy
- cost metricsの保持・数値目標
- component/CAR identity
- SQLite を既定とする datastore port/provider の具体 API
- Textus-owned local-data root と database file 名
- WAL、foreign key、durability、backup、migration の確定 policy
- local projection/config の名称と要否
- `WorkspaceMutationLease` port と既定実装の所有先
- 中立 manifest schema の配布先と versioning policy
- MCP/server の初期 slice への包含
- receipt artifact の保持・redaction policy
- BoK/CBD catalog における類似 component の存在確認

BoK/CBD MCP は今回の環境から利用できなかったため、catalog identity の確認は未完了である。identity を固定する implementation phase の入口で再確認する。

## 今回行っていないこと

- source code、CML、build definition の作成
- validation/test の実行
- commit、publish、skill install
- `cncf-*` の変更

この journal は設計判断の保存であり、実装開始の記録ではない。
