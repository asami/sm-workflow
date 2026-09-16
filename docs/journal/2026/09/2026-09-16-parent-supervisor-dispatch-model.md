# Parent Supervisor / Semantic Worker Dispatch Model

- 日付: 2026-09-16
- 状態: Design decision / operational model

## 背景

`sm-workflow` では deterministic な workflow progression、build/test/spec、local git closing を runtime 側へ移し、AI/Codex は意味判断が必要な semantic work に限定する。

この前提で、Codex の親タスクと子タスクの責務、および `sm-workflow` の呼び出し境界を整理する。

## 基本構成

親タスクを Planner / Supervisor / Dispatcher とする。親タスクは全体計画と WorkflowRun の監視を担当し、`sm-workflow` の start / advance / submit / status 等の command は親タスク自身が直接実行する。

`sm-workflow` command を呼ぶだけのために別の LLM 子タスクを起動しない。command invocation と Continuation の取得は semantic work ではなく control-plane operation と位置付ける。

```text
Parent Task
  default: capable planning/supervision model
  |
  | plan / monitor / dispatch / reasoning-policy selection
  |
  +--> sm-workflow command directly
  |      start / advance / submit / status
  |              |
  |              v
  |         Continuation
  |
  +--> Semantic Child Task
         implementation / review / re-review / analysis
                    |
                    v
              Result / Evidence
                    |
                    v
              sm-workflow
```

## 親タスクの責務

親タスクは次を担当する。

- Goal、Phase、Workflow全体のPlanを理解する。
- WorkflowRunの現在位置と次のContinuationを監視する。
- `sm-workflow` commandを直接呼び出す。
- `WORK_ORDER` の性質に応じて子タスクへsemantic workを委譲する。
- workの複雑度、重要度、必要能力を見て適切なモデル/思考レベルを選択する。
- 子タスクの詳細な会話履歴を保持するのではなく、Result / Evidence / Receiptを通じて全体状態を追跡する。
- 例外、再計画、escalationが必要な場合に全体Planを調整する。

運用上は、親タスクには高いplanning/supervision能力を持つ構成を用いる。現時点の候補として通常は Terra/xhigh、より高度な抽象化・設計判断が必要な場合は Sol/high 等を利用できる。ただし、これらの具体的なモデル名は `sm-workflow` の永続contractには含めない。

## 子タスクの責務

子タスクは semantic worker として、WorkOrder単位の意味的作業を担当する。

例:

- implementation
- architecture-sensitive implementation
- review
- re-review
- analysis
- exception analysis

子タスクは Workflow 全体をControlしない。WorkOrderの入力、必要Context、Capability、Completion Criteriaを受け取り、構造化されたResult / Evidenceを返す。

これにより、子タスクの大きなContextを親タスクへそのまま戻す必要を減らす。

## Reasoning / Model Dispatch Policy

Workflow DefinitionやWorkOrderに特定製品のモデル名を固定しない。

`sm-workflow` が保持できるのは、たとえば次のようなモデル非依存の要求である。

```text
workKind: IMPLEMENTATION | REVIEW | ANALYSIS | ...
complexity: ROUTINE | HIGH | DEEP
requiredCapabilities:
  - scala
  - architecture
reviewPolicy: REQUIRED
risk: LOW | NORMAL | HIGH
```

親タスク側のDispatch Policyが、これをその時点で利用可能なモデルと思考レベルへ写像する。

概念例:

```text
routine implementation -> implementation向け標準設定
high implementation    -> implementation向け高推論設定
architecture review    -> 抽象思考・review向け高推論設定
re-review              -> review結果とriskに応じて設定
```

この分離により、モデル名称、価格、性能、reasoning levelの変更をWorkflow Definitionから切り離す。

## `sm-workflow` との責務境界

4者の責務を次のように整理する。

```text
Parent AI
  = Planner / Supervisor / Dispatcher

Child AI
  = Semantic Worker

sm-workflow
  = Deterministic Control & Execution Plane

Human
  = Choice / Approval
```

`sm-workflow` は、どの具体的AIモデルを使うかを決定しない。semantic boundaryの種類、必要Capability、複雑度、riskなど、dispatch判断に必要な構造化情報を提供する。

親タスクは、`sm-workflow` が既に決定的に処理できる作業を子AIへ委譲しない。build、test、Executable Specification、git closingなどは `sm-workflow` 内部で処理する。

## コスト上の原則

LLMを必要としないWorkflow Operationの起動にLLM子タスクを使わない。

```text
control-plane command
  -> parent directly invokes sm-workflow

semantic work
  -> parent selects appropriate AI worker

deterministic work
  -> sm-workflow runtime executes
```

これにより、子タスク起動、Context受け渡し、command選択の再推論、結果の再要約といった不要なAI計算を削減する。

同時に、親タスクが全体Contextを保持し、子タスクはWorkOrder単位の限定Contextを扱うことで、長時間WorkflowにおけるContext肥大を抑える。
