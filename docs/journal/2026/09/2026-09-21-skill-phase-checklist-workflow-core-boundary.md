# Skill-managed Phase / Checklist と sm-workflow Core の境界

- 日付: 2026-09-21
- 状態: Design decision
- 対象: sm-workflow / sm-* skills
- 関連: [sm-workflow 設計ノート](../../notes/sm-workflow-design.md)

## 背景

sm-workflow を開発進行へ適用する場合、Phase / Checklist の解釈と更新は skill が担い、Workflow の実行状態と Action の進行は sm-workflow が担う。この二者は密接に連携するが、同じ情報モデルを共有すると sm-workflow に software-development-specific な planning context が侵入し、generic Workflow runtime としてのスコープが崩れる。

特に skill 側だけが知る情報がある。Phase の意図、Checklist の記述上の意味、Closure の判断理由、過去の設計判断、project convention、関連文書の文脈などである。これらは Workflow の実行そのものには不要な場合が多い。

## 決定

sm-workflow の核を execution semantics に限定する。

- Workflow / WorkflowRun
- State
- Action / JudgmentAction
- Transition
- typed Input / Output
- Participant / Binding
- Result / Evidence / Receipt
- Execution History / revision / continuation

Phase、Checklist、Closure Criteria とその planning semantics は skill が所有する。

> sm-workflow owns execution semantics. Skill owns planning semantics and context. Skill maps workflow execution onto its Phase/Checklist model.

## Mapping の所在

Workflow と Phase / Checklist の対応関係は sm-workflow に持たせず、skill 側の Workflow Projection / Workflow Mapping として扱う。

対応は 1:1 ではない。一つの Checklist Item が複数 Action と Evidence に対応することも、一つの Workflow execution が複数 Checklist Item の判断材料になることも認める。

skill は少なくとも次を対応付けられる必要がある。

- Phase / ChecklistItem / ClosureCriterion
- Workflow / State / Action
- Result / Evidence
- completion/evidence/reconciliation rule
- mapping を作った source revision

sm-workflow の generic domain に phaseId/checklistItemId を必須属性として導入しない。

## Context の境界

skill context 全体を Workflow に渡さない。実行に必要な情報だけを bounded ExecutionContext として materialize する。

ExecutionContext は skill context の mirror ではない。特定 WorkflowRun / WorkOrder を実行可能にする input snapshot であり、Phase の意味や planning history を sm-workflow の永続モデルへ移すためのものではない。

## 同期

同期は state replication ではなく semantic reconciliation とする。

```text
Phase / Checklist + Skill-only Context
        |
        | interpret / project
        v
Workflow Mapping + ExecutionContext
        |
        | execute
        v
sm-workflow Core
        |
        | Result / Evidence / History
        v
Skill reconciliation
        |
        | interpret / update
        v
Phase / Checklist
```

skill は Result / Evidence を自分の planning semantics に戻して解釈し、Checklist の完了、分割、追加、Closure 判定を行う。

Phase / Checklist が実行中に変更された場合は source revision の差を skill が検出し、mapping を reconcile する。sm-workflow は Markdown や Phase 文書の変更意味を解釈しない。

## 禁止する方向

- Phase / Checklist を sm-workflow の正本にする。
- Workflow state と checklist checked state を dual-write する。
- checklist item と Action の 1:1 対応を前提にする。
- skill-only context を「便利だから」という理由で Workflow domain に追加する。
- sm-workflow が Phase / Checklist の更新方針を決定する。
- sm-workflow が software-development-specific planning semantics を理解する。

## Scope guard

sm-workflow の情報モデルへ新しい概念を追加する際は、次を問う。

> Phase / Checklist を持たない別用途の Workflow でも、その情報は execution semantics として必要か。

No であれば、原則として profile または skill 側に置く。

## 影響

この決定により、今後の Phase 1 / executable specification では Workflow core 自体だけでなく、skill 側 Mapping と bounded ExecutionContext の境界を検証対象にする必要がある。

一方、Phase / Checklist parser、project-specific planning policy、Markdown 更新規則そのものは sm-workflow core の executable specification に取り込まない。これらは対応する sm-* skill/profile 側で保証する。

JudgmentAction についても同じ原則を適用する。JudgmentAction は Workflow 内の判断実行点を表すが、その結果が Phase / Checklist 上で何を意味するかは skill が mapping/reconciliation する。
