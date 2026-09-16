# Workflow Execution Protocol と Continuation Context Mechanism

- 日付: 2026-09-17
- 状態: Design exploration / handoff

## 背景

Workflow の論理構造としては、CNCF/Textus Workflow Runtime が control flow を所有し、必要な地点で AI、Human、Service、Component Operation を呼び出す orchestration が自然である。

一方、Codex/ChatGPT Skill のように Workflow Runtime 側から AI を直接起動できない環境では、外部の Skill / Parent AI が `sm-workflow` を呼び、Workflow が semantic boundary で処理を suspend し、外部 executor が結果を返して resume する構造になる。

この違いを Workflow Model 自体の違いにはせず、同じ Workflow semantics に対する Execution Protocol の違いとして扱う。

## 二つの Workflow Execution Protocol

### Orchestration Protocol

Workflow Runtime が execution driver / orchestrator となり、Participant を直接 invoke する。

```text
Workflow Runtime
  -> Operation / Participant Invocation
  -> AI / Human Task / Service / Component
  -> Result
  -> Workflow continues
```

### Continuation Protocol

Workflow Runtime は semantic boundary で suspend し、Continuation を外部 Driver へ返す。外部 Driver が AI / Human / Service を実行し、Result を Workflow へ返して resume する。

```text
External Driver
  -> advance
  -> Workflow Runtime
  -> Continuation
  -> External Driver
  -> AI / Human / Service
  -> ContinuationResult
  -> resume / advance
  -> Workflow Runtime
```

Skill / Parent AI は Continuation Protocol の execution driver ではあるが、Workflow semantics の owner / orchestrator ではない。次に何を実行してよいかは WorkflowRun / StateMachine / Continuation が決定する。

したがって責務を次のように区別する。

- Workflow Runtime: Control Semantics Owner
- Orchestration Protocol: Workflow Runtime が direct Orchestrator
- Continuation Protocol: External Driver が suspend/resume を駆動
- AI Worker: Semantic Work Executor

## 同じ Workflow Model を共有する

Orchestration Protocol と Continuation Protocol で Workflow Definition を分けない。

たとえば `ReviewChange -> ReviewResult` という semantic operation は共通である。

Orchestration Protocol:

```text
Workflow
  -> invoke ReviewChange
  -> ReviewResult
  -> continue
```

Continuation Protocol:

```text
Workflow
  -> yield Continuation(ReviewChange)
  -> external execution
  -> submit ReviewResult
  -> continue
```

Protocol が変えるのは delivery / execution driving であり、StateMachine、Guard、Operation semantics、Result schema、Completion Criteria は共有する。

原則:

> Who drives execution changes; workflow semantics do not.

## Continuation 共通機構

Continuation は単なる「次の状態」ではなく、外部 Executor が作業し、Workflow を安全に resume するための Resume Contract とする。

概念構造:

```text
Continuation
  identity
    runId
    continuationId
    revision

  boundary
    kind
    operation
    requiredCapabilities

  context
    ContextBundle

  completionContract
    resultSchema
    completionCriteria

  executionPolicy
    risk
    authorization
    lease
    expiresAt
```

Continuation のライフサイクルは概念的に次の形になる。

```text
advance
  -> yield Continuation
  -> external execution
  -> ContinuationResult
  -> resume(continuationId, result)
  -> advance
```

`sm-workflow` の既存 `Continuation` / `WorkOrder` / revision / lease / idempotency は、この共通 Continuation Protocol の reference implementation として整理する。

## Context の持ち回り

Continuation Protocol では外部 Executor が Workflow Runtime の外で semantic work を実行するため、再開に必要な Context を明示的に受け渡す共通機構が必要になる。

ただし、Conversation 全体、Source 全体、全ログを Continuation payload にコピーする方式は採らない。Context は正本、Work 用情報、Execution 用一時情報を分離し、Reference 中心で扱う。

### 1. Workflow Context

Workflow Runtime が durable な正本として所有する。

例:

- WorkflowRun
- Plan / PlanRevision
- current Stage / WorkItem
- Decision
- Evidence
- Result / Receipt

Workflow Context 全体を Continuation へ複製しない。

### 2. Work Context

その semantic work を実行するために必要な情報。

例:

```text
target
inputs
constraints
completionCriteria
requiredEvidence
references
```

Review の場合は対象 revision、変更ファイル、Model revision、build/test/spec evidence、Acceptance Criteria 等を含む。

### 3. Execution Context

実際の Executor / Driver が実行時に必要とする一時情報。

例:

- workspace binding
- available tools / capabilities
- dispatch / reasoning policy
- temporary artifacts
- host-specific execution hints

Execution Context は Workflow domain の正本にはしない。host/model 固有情報を Workflow Definition へ固定しない。

## ContextBundle と Reference

Continuation が大量データを直接持つのではなく、必要最小限の summary / facts と、dereference 可能な ContextReference を持つ。

```text
ContextBundle
  summary
  requiredFacts
  references
    - model reference
    - artifact reference
    - evidence reference
    - source/workspace revision reference
```

Executor は必要な情報だけを取得する。これにより Parent/Worker の Context Budget を制御し、長時間 Workflow での token/context 肥大を抑える。

ContextReference の具体的 URI/schema は今後の設計対象とする。特定 host の file path や conversation id を public contract に直接固定しない。

## Context Snapshot / Staleness

Continuation 発行後に Workflow、Model、Workspace が変更される可能性があるため、Continuation は「何を前提に発行されたか」を示す Context Snapshot を持つ。

概念例:

```text
ContextSnapshot
  workflowRevision
  modelRevision?
  workspaceRevision?
  evidenceRevision?
```

resume 時には Continuation が前提とした snapshot と現在値を照合する。重要な前提が変化している場合は stale continuation として fail closed し、必要に応じて再発行・再レビューする。

特に Review -> Closing -> Commit では、Review した対象 revision と commit 対象が一致することを Context Snapshot / Evidence によって保証する。

## Continuation の一般化

Continuation を次の組み合わせとして捉える。

```text
Continuation
  = Resume Contract
  + Context Carrier
  + Completion Contract
  + Evidence Boundary
```

この仕組みは AI 専用ではない。Human Task、外部 Service、非同期 Job、remote executor にも利用できる。

## Orchestration Protocol との共通化

Context 機構は Continuation 専用にしない。

Orchestration Protocol でも Participant Invocation に ContextBundle、CompletionContract、EvidenceContract が必要になる。

したがって共通概念を次のように考える。

```text
Workflow Invocation Contract
  operation
  ContextBundle
  CompletionContract
  EvidenceContract

Delivery / Execution Protocol
  - Orchestration: push / invoke
  - Continuation: yield / resume
```

これにより同じ Workflow / Operation / Context / Completion / Evidence semantics を保ったまま、実行環境に応じて Protocol を選択できる。

将来的には Workflow 全体で一つの Protocol を固定するだけでなく、Operation / Participant binding ごとに Orchestration と Continuation を選択・混在できる構造を検討する。

## sm-workflow への位置付け

`sm-workflow` はまず Continuation Protocol の reference implementation として発展させる。

現在の `advance` 中心設計、durable WorkflowRun、Continuation、WorkOrder、Decision、WAIT、revision、lease、idempotency、SQLite persistence は、この方向と整合する。

今後の設計では次を検討する。

1. Continuation / ContextBundle / ContextSnapshot / ContextReference の共通 model と schema。
2. ContinuationResult / resume contract の明確化。
3. stale continuation の fail-closed semantics。
4. Context Budget と lazy dereference。
5. Orchestration Protocol と Continuation Protocol が共有する Workflow Invocation Contract。
6. CML/CNCF Workflow Execution Model への一般化。
7. Operation/Participant 単位での protocol binding と混在実行。

この journal は `sm-workflow` の実装詳細だけで閉じず、Cozy/CML/CNCF 側へ Workflow Execution Protocol の共通機構として handoff するための設計入力とする。
