# Deterministic Operations and Closing

- 状態: Design decision
- 日付: 2026-09-16
- 関連: `sm-workflow-design.md`

## 責務境界

`sm-workflow` は、AI/Codex を意味判断が必要な semantic work に限定し、手続きとして確立した build、test、Executable Specification、git、generation、lint、inspection などを typed deterministic operation として Workflow component/runtime 側で実行する。Skill/AI は procedural external process を起動しない。

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

Deterministic Operation は raw shell を CML Action に埋め込む仕組みではない。Workflow は意味を持つ typed Operation を参照し、Workflow runtime が選択・管理する implementation/provider が必要に応じて `sbt`、`git` その他の外部 process を実行する。command selection、argv、serialization/lifecycle、process completion、failure classification、receipt 永続化は Workflow 側の責務であり、Skill へ委譲しない。将来は BuildProject に対して sbt/Gradle/Flutter/npm 等の provider を選択できる構造を想定する。

Workflow provider への配置は AI sandbox の権限を緩める仕組みではない。AI sandbox は厳しいまま保ち、Workflow の current state/revision/guard と provider registry で admission された typed operation だけを管理実行する。operation ごとに executable identity、typed argv projection、working directory、allowed mutation roots、network/credential policy、timeout、result/receipt schema を固定し、Continuation または Work Order による権限拡張と、AI/Skill からの arbitrary command 注入を禁止する。

## `advance` の拡張

`advance` は automatic transition だけでなく、Workflow が明示的に許可した deterministic operation も実行対象とする。次の Semantic Boundary または Terminal State に到達するまで bounded に進行する。

- Automatic Transition: 永続済み状態から副作用なしに一意に決まる遷移。
- Deterministic Operation: 型付き Operation として宣言され、明確な input/output、permission、failure semantics、receipt を持つ処理。外部作用を含み得る。
- Semantic Boundary: AI/Human の意味判断が必要な境界。

Deterministic Operation の失敗を直ちに Codex Work Order へ変換しない。定義済み retry で回復可能なら runtime が処理し、環境待ちは WAIT、意味判断が必要なら WORK_ORDER、人間判断・追加権限が必要なら DECISION へ分類する。

## Semantic Action sandwich

AI担当stateを、入力収集、AI判断、検証、遷移決定が混在した大きな処理にしない。すべての
semantic Action は次の構造にする。

```text
deterministic prepare
  -> immutable input snapshot
  -> semantic AI Action
  -> typed semantic Result
  -> deterministic admission / validation
  -> automatic next-state selection
```

prepare は authority、current revision、owned paths、diff/evidence snapshot、acceptance
contract を固定する。AI は planning、semantic edit/repair、review、exception analysisだけを
行い、command、validation result、next state、ledger update、cycle count、retry、commit
readiness を返さない。

admission は schema、revision、authority、owned delta、evidence freshness、closed disposition
vocabularyを検証し、必要なtyped validationを実行する。意味的曖昧さをdeterministic
heuristicで埋めず、追加semantic Actionまたはhuman Decisionとして明示的に停止する。

このsandwichにより、AIが自分の変更を自分でacceptしてcommitへ進めることと、Workflowが
未決の意味を推測することの両方を禁止する。

| Responsibility | Owner |
| --- | --- |
| authority/evidence/diff snapshot、known-work estimate | Workflow deterministic operation |
| plan、semantic edit/repair、review findings、unknown-work/boundary analysis | semantic AI Action |
| schema/authority/result admission、validation/lint、convergence、next transition | Workflow deterministic operation |
| command selection/argv/process/retry/receipt、Git commit | Workflow deterministic operation provider |
| new authority、複数の妥当なsemantic解、破壊的例外 | human Decision |

semantic skillはこの表のdispatcherではない。Workflowが選んだexact `AIWorkRequest`を一件
実行し、matching `AIWorkResult`を返して終了する。result内容に応じて別skill、agent、
operation、command、Decisionを呼び分けるlogicをskillへ置かない。次の処理は
deterministic admissionと`advance`が選ぶ。

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

この構造により、AIが `git status -> diff -> add -> commit`、`sbt`、formatter、generator、linter 等の実行を逐次選択し、出力を解釈して次の手順を再推論する必要を減らす。同じArchitecture上の選択によって、AI計算コストと実行の非決定性を低減し、AI sandbox を緩和せずに管理された command execution を実現する。

AIで探索した semantic work が繰り返され、意味・規則・完了条件が十分に明確になった場合は、Workflow revision で typed deterministic operation へ昇格できる。

```text
AI exploration
  -> Knowledge
  -> typed Workflow / Operation
  -> deterministic execution
```

## Conflict merge boundary

Workflow-owned mutation と current artifact に差分がある場合は、base/current/desired の
typed three-way model を作る。非重複変更、同一 normalized value、canonical model から
再生成できる projection は **修正の衝突ではない**。Workflow はその compatibility を
検証して機械的に composition できるが、これを conflict resolution と呼ばない。

同じ stable semantic entity、field、hunk、ownership、goal、closure、dependency、handoff
へ両側が異なる意味を持つ修正をした時点で `ModificationCollision` とする。Workflow は
出力が一見一意に見えてもこれを自動採択しない。authority が変わらず、解が一つに定まる
semantic assessment を必要とする collision は frozen conflict set を bounded semantic AI
Action に渡す。AI は patch や command を直接適用せず、conflict ID ごとの typed resolution
plan を返す。Workflow が authority/invariant を検証し、current revision に
compare-and-set で適用する。

identity/authority の変更、破壊的上書き、複数の同等に妥当な解は AI の authority を
越えるため human Decision で停止する。この境界により、non-colliding composition の AI
cost をゼロにしつつ、実際の修正衝突で意味判断または権限判断を省略しない。
