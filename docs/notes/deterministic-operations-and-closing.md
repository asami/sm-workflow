# Deterministic Operations and Closing

- 状態: Design decision
- 日付: 2026-09-16
- 関連: `sm-workflow-design.md`

## 責務境界

`sm-workflow` は、AI/Codex を意味判断が必要な semantic work に限定し、手続きとして確立した build、test、Executable Specification、git などを typed deterministic operation として component/runtime 側で実行する。

```text
AI / Skill
  PLAN / EDIT / REVIEW / exception analysis
          |
          v
      Result / Decision
          |
          v
sm-workflow advance
  automatic transition
  deterministic operation
          |
          +-- BuildProject
          +-- RunTests
          +-- RunExecutableSpecification
          +-- InspectChanges
          +-- StageChanges
          +-- CommitChanges
          |
          v
next semantic boundary or terminal
```

Deterministic Operation は raw shell を CML Action に埋め込む仕組みではない。Workflow は意味を持つ typed Operation を参照し、implementation/provider が必要に応じて `sbt`、`git` 等の外部 process を実行する。将来は BuildProject に対して sbt/Gradle/Flutter/npm 等の provider を選択できる構造を想定する。

## `advance` の拡張

`advance` は automatic transition だけでなく、Workflow が明示的に許可した deterministic operation も実行対象とする。次の Semantic Boundary または Terminal State に到達するまで bounded に進行する。

- Automatic Transition: 永続済み状態から副作用なしに一意に決まる遷移。
- Deterministic Operation: 型付き Operation として宣言され、明確な input/output、permission、failure semantics、receipt を持つ処理。外部作用を含み得る。
- Semantic Boundary: AI/Human の意味判断が必要な境界。

Deterministic Operation の失敗を直ちに Codex Work Order へ変換しない。定義済み retry で回復可能なら runtime が処理し、環境待ちは WAIT、意味判断が必要なら WORK_ORDER、人間判断・追加権限が必要なら DECISION へ分類する。

## Review ACCEPT と Closing

AI Review は semantic boundary である。Review が `ACCEPT` になった時点で AI の通常作業は終了し、commit 用の追加 Work Order は発行しない。

```text
REVIEW WorkOrder
  -> AI Review
  -> ACCEPT
  -> SubmitReviewResult
  -> Closing
       -> verify current revision
       -> verify review/validation evidence
       -> InspectChanges
       -> StageChanges
       -> CommitChanges
       -> record commit SHA and receipts
  -> Completed
```

`CommitChanges` は local closing の deterministic operation とする。commit SHA は WorkflowRun の Evidence/Receipt として保存し、変更、Review Evidence、WorkflowRun、Commit を追跡可能にする。

Push、Pull Request、Merge、Deployment は local commit と分離する。これらは remote publish としてより強い Authorization / Approval Boundary を要求する後続機能とする。

## AI計算コストと決定性

この構造により、AIが `git status -> diff -> add -> commit` や `sbt` の実行を逐次選択し、出力を解釈して次の手順を再推論する必要を減らす。同じArchitecture上の選択によって、AI計算コスト低減と実行の非決定性低減を同時に得る。

AIで探索した semantic work が繰り返され、意味・規則・完了条件が十分に明確になった場合は、Workflow revision で typed deterministic operation へ昇格できる。

```text
AI exploration
  -> Knowledge
  -> typed Workflow / Operation
  -> deterministic execution
```
