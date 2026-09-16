# sm-workflow Deterministic Closing

- 日付: 2026-09-16
- 状態: Design decision

## 決定

`sm-workflow` の責務境界を更新する。AI/Codex は意味判断が必要な作業を担当し、git、sbt、build、test、Executable Specification など、手続きとして確立した処理は `sm-workflow` 側の typed deterministic operation として実行する。

特に AI Review が `ACCEPT` になった場合、commit のための追加 Work Order を Codex へ返さない。Review Result の受理を契機として `sm-workflow` が Closing へ遷移し、内部的に必要な検証と Git 操作を実行して Completed まで進める。

```text
CHANGE WorkOrder
  -> AI edit
  -> SubmitWorkResult
  -> deterministic build / test / executable spec
  -> REVIEW WorkOrder
  -> AI Review
  -> ACCEPT
  -> SubmitReviewResult
  -> Closing
       -> verify revision/evidence
       -> inspect git changes
       -> stage changes
       -> commit
       -> record commit SHA / receipt
  -> Completed
```

## Automatic Transition と Deterministic Operation

両者は区別する。

- Automatic Transition: 永続済み状態から副作用なしに一意に決まる状態遷移。
- Deterministic Operation: Workflow が許可した型付き Operation として実行される、外部作用を含み得る決定的処理。
- Semantic Boundary: AI または Human の意味判断が必要な境界。

`advance` は、次の Semantic Boundary または Terminal State に到達するまで、実行可能な Automatic Transition と許可された Deterministic Operation を処理する。

Deterministic Operation は raw shell を Workflow Model に埋め込む仕組みではない。Workflow/CML は `BuildProject`、`RunTests`、`RunExecutableSpecification`、`InspectChanges`、`StageChanges`、`CommitChanges` のような typed Operation を参照し、その implementation/provider が必要に応じて sbt、git 等を呼び出す。

## AI / sm-workflow / Human の責務

- AI: PLAN、EDIT、REVIEW、例外分析など semantic work。
- sm-workflow: workflow control、automatic transition、deterministic operation、build/test/spec、local git closing、evidence/receipt。
- Human: Choice、Approval、追加権限が必要な判断。

`CommitChanges` は local closing の一部として扱える。push、Pull Request 作成、merge、deployment など remote publish は commit と分離し、より強い authorization/approval boundary を持つ後続機能とする。

## コストと決定性

この境界変更により、AIが git status/diff/add/commit や sbt invocation を逐次選択し、その出力を解釈して次のコマンドを再推論する必要を減らす。

したがって deterministic operation への移行は、AI計算コスト削減と実行の非決定性低減を同時に実現する。

AIで探索して十分に形式化できた semantic work は、将来の Workflow revision で typed deterministic operation へ昇格できる。この成熟過程を `AI exploration -> Knowledge -> Workflow/Operation -> deterministic execution` と捉える。

## Phase 1 への反映

Phase 1 の reference workflow と acceptance では、少なくとも Review ACCEPT 後の local closing を deterministic path として扱う。Git commit を行うための COMMIT Work Order は発行しない。

Phase 1 の範囲では remote push/PR/deployment は Non-Goal のままとする。
