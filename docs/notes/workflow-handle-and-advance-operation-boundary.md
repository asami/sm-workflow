# Workflow Handle and Advance Operation Boundary

Status: normative design input for the public Workflow Operation boundary

Source decision:
[`2026-09-18-workflow-handle-advance-operation-boundary.md`](../journal/2026/09/2026-09-18-workflow-handle-advance-operation-boundary.md)

Operation-surface refinement:
[`2026-09-21-sm-workflow-skill-adapter-review-fix-handoff.md`](../journal/2026/09/2026-09-21-sm-workflow-skill-adapter-review-fix-handoff.md)

## Purpose

公開 `sm-*` skill と durable WorkflowInstance の間を、profile 固有の開始 Operation、安定した
`WorkflowHandle`、current `Continuation` を含む `WorkflowInteraction`、Continuation が指定する
profile 固有の completion Operation で構成する。`WorkflowHandle` が唯一の公開 root identity であり、
`Continuation` はその Handle に属する immutable な current-boundary Value Object である。
Skill は CML Workflow、StateMachine、永続 state を直接操作せず、CNCF Operation boundary
だけを使用する。

```text
human selects sm-goal-phase
  -> StartGoalPhase(typed input, WorkflowInvocationSelection)
  -> CNCF WorkflowStartResult(handle + first Continuation)
  -> WorkflowInteraction(handle + current Continuation)
  -> AI / Human / external event produces a typed response
  -> Continuation-selected completion Operation(handle, ContinuationResult)
  -> CNCF runtime internal advance evaluation
  -> next WorkflowInteraction
```

この形は `GoalPhaseWorkflow`、`SplitPhaseWorkflow`、`RepositorySyncWorkflow` に共通する。
開始時の admission と入力 schema は profile が所有し、開始後の進行、応答受理、再実行、
境界返却は generic Workflow runtime が所有する。`advance` は runtime 内部の progression
evaluator の概念名であり、Skill-facing の第二の generic Operation を意味しない。

## Selected public operation shape

### Profile-specific start Operations

初期 profile はそれぞれ独立した開始 Operation を持つ。

```text
StartGoalPhase(GoalPhaseStartInput, WorkflowInvocationSelection)
  -> WorkflowInteraction

StartSplitPhase(SplitPhaseStartInput, WorkflowInvocationSelection)
  -> WorkflowInteraction

StartRepositorySync(RepositorySyncStartInput, WorkflowInvocationSelection)
  -> WorkflowInteraction

SubmitGoalPhaseWorkResult(handle, ContinuationResult[GoalPhaseWorkResult])
SubmitSplitPhaseWorkResult(handle, ContinuationResult[SplitPhaseWorkResult])
SubmitRepositorySyncWorkResult(handle, ContinuationResult[RepositorySyncWorkResult])
ResolveApplicationDecision(handle, ContinuationResult[DecisionResult])
  -> WorkflowInteraction
```

これらの名前は working ABI name であり、CML/CNCF の命名規則に合わせた最終名は生成 ABI
設計時に確定する。ただし、次の意味は固定する。

- 開始 Operation 自体が exact Workflow definition binding を表す。
- caller は generic start request に自由形式の `workflowDefinitionId` を渡さない。
- profile 固有 Operation が typed start input、admission、初期 authority check を所有する。
- start は framework `WorkflowStartRequest` を通じて durable WorkflowInstance を冪等に
  作成または取得し、framework `WorkflowStartResult` の `WorkflowHandle` と first
  `Continuation`（または typed terminal）を `WorkflowInteraction` として返す。
- profile-specific start は common Start/Handle/Continuation contract の application
  specialization であり、別の start lifecycle や generic protocol ではない。
- non-terminal Continuation は、次に使用する exact registered completion Operation identity と
  typed response contract を持つ。Skill は response の内容から Operation を選ばない。

従来案の public `StartWorkflowRun` はこの境界では採用しない。generic start application service
を内部実装として共有してもよいが、skill-facing Operation として公開しない。

### Human-selected invocation authority

`sm-goal-phase`、`sm-split-phase`、`sm-repository-sync` は別々の human-selected entry point
である。public skill の直接選択、または host/client が提示した候補からの明示選択が、対応する
profile-specific start Operation の invocation authority になる。

特に `GoalPhaseWorkflow` と `SplitPhaseWorkflow` の間では次を必須とする。

- `SPLIT_REQUIRED` や child goal recommendation は advisory evidence であり、開始権限ではない。
- recommendation は host/client が候補を表示するために使えるが、別 Workflow を開始しない。
- 人間が `sm-split-phase` または対象 child の `sm-goal-phase` を選択したときだけ、新しい
  start Operation と新しい WorkflowInstance を作る。
- prior handle、run identity、revision、Decision、pending interaction、durable state は別
  WorkflowInstance へ移送しない。
- recommendation から typed start input reference を引き継ぐ場合も、current source digest と
  new invocation authority を start Operation が再検証する。

`WorkflowInvocationSelection` の exact ABI は後続設計で定めるが、少なくとも participant identity、
explicit selection identity、選択された start Operation/profile、任意の recommendation
reference、idempotency key を相関できなければならない。skill や Workflow runtime が
人間選択 record を自己生成してはならない。

## WorkflowHandle contract

`WorkflowHandle` は durable WorkflowInstance を Operation boundary 越しに指定する、opaque
かつ serializable な typed reference/capability である。mutable Workflow object ではない。

概念上、handle は次を安全に carry または resolve できる。

```text
WorkflowHandle
  componentIdentity
  workflowDefinitionIdentity
  workflowInstanceIdentity
  protocolVersion
  capabilityReference
```

exact field、署名、opaque token 化、Component locator は ABI 設計へ defer する。公開契約では
次の制約を守る。

- `setState`、`transitionTo`、`resume` などの state mutation method を公開しない。
- SQLite/JDBC/path、内部 StateMachine state、transition candidate、history を含めない。
- WorkflowInstance の current revision や pending boundary が変わっても、instance identity として
  の handle は安定している。
- `expectedRevision`、interaction correlation、idempotency key は advance request/response 側に
  置き、handle を mutable cursor にしない。
- handle の所持だけで新しい権限を与えない。caller identity、profile policy、operation-specific
  authorization は各 call で検証する。
- Component が WorkflowInstance と履歴を所有・永続化し、skill は handle だけを保持する。

## Completion Operation and runtime advance contract

Skill-facing progression は current Continuation が指定する registered application completion
Operation を通じて行う。Goal/Split/RepositorySync の work result と application Decision は
それぞれ typed Operation を持ち、各 Operation は CNCF common Result/Decision submission を
呼び出す。別の generic `advanceWorkflow` public protocol は設けない。

```text
ApplicationCompletionRequest[R]
  handle: WorkflowHandle
  continuationIdentity
  expectedRevision
  contextSnapshot
  response: ContinuationResult[R]
  participantIdentity
  capabilities
  idempotencyKey

Continuation-selected completion Operation(request)
  -> WorkflowInteraction
```

一回の completion Operation は次を一つの bounded evaluation として行う。

1. handle、caller authority、revision、idempotency、current Continuation を検証する。
2. response を current Continuation の Operation identity、expected result type、evidence、authority
   と照合して deterministic に admit する。
3. CNCF runtime の internal advance evaluator が Workflow-owned automatic transition と admitted
   deterministic Operation を、定められた上限まで実行する。
4. 次の external interaction boundary または terminal outcome に到達したら、一つの
   `WorkflowInteraction` を返す。
5. state、history、Operation receipt、Continuation、idempotency result を必要な transaction
   boundary で永続化する。

初期境界は profile-specific start が返す `WorkflowInteraction` に含まれる。外部作業または
人間判断の完了後は、同じ handle と current Continuation に対応する typed
`ContinuationResult` を、その Continuation が指定した completion Operation へ渡す。
`continuationIdentity`、`expectedRevision`、`ContextSnapshot` は current Continuation と一致
しなければならない。

```text
StartGoalPhase(...)
  -> WorkflowInteraction(handle,
       continuation = AIWorkRequest(
         id = I1,
         revision = R1,
         completionOperation = SubmitGoalPhaseWorkResult))

SubmitGoalPhaseWorkResult(handle,
  ContinuationResult(continuationId = I1, expectedRevision = R1, ...))
  -> DecisionRequest | AIWorkRequest | WaitCondition | TerminalResult
```

同じ revision/idempotency key の response 再送は、二重受理や二重実行を行わず、保存済みの
同じ結果を返す。stale revision、異なる Continuation、Operation identity不一致、結果型不一致、
authority 不一致は fail-closed で拒否する。

## WorkflowInteraction contract

`WorkflowInteraction` は framework `WorkflowHandle` と current `Continuation` の公開
projection である。内部 state や遷移列ではなく、caller が次に扱う外部境界を表す。non-terminal
interaction の kind、identity、expected revision、ContextSnapshot、typed request/response
contract は canonical Continuation と一致し、interaction 独自の lifecycle や root identity を
作らない。

```text
WorkflowInteraction
  handle
  continuation?: Continuation
  kind:
    AI_WORK_REQUEST
    HUMAN_DECISION_REQUEST
    WAIT_CONDITION
    COMPLETED
    FAILED
  request? | waitCondition? | terminalResult?
  responseContract? // Continuation の typed completion contract の projection
  advanceSummary
```

- `AI_WORK_REQUEST`: fully materialized な一件の semantic work と matching result schema を返す。
- `HUMAN_DECISION_REQUEST`: 選択肢、判断 authority、必要 evidence、typed response schema を返す。
- `WAIT_CONDITION`: job、timer、external event、別 owner の完了条件を返す。AI に polling 作業を
  発行しない。
- `COMPLETED`: typed terminal result と accepted evidence/receipt reference を返す。
- `FAILED`: 回復不能な typed terminal failure を返す。

`advanceSummary` は from/to revision、吸収した automatic transition / deterministic Operation
の件数、必要なら bounded diagnostic reference を含められる。完全な history、過去 receipt
本文、内部 state 候補一覧は含めない。

protocol validation error、authorization error、revision conflict、unknown handle は
`FAILED` terminal outcome と混同せず、Operation call failure として返す。Workflow 自体の
admitted failure だけが `FAILED` interaction になる。

## Human and AI response admission

Human と AI は同じ Handle/Continuation admission architecture を使うが、profile と
interaction kind に対応する completion Operation と response の意味型は分ける。

```text
AIWorkRequest       <-> AIWorkResult
HumanDecisionRequest <-> HumanDecisionResult
WaitCondition       <-> admitted event / condition satisfaction
```

共通規則は次のとおりである。

- participant は Workflow state、next state、transition、次 Operation を指定しない。
- AI result は command request、routing directive、commit readiness を返さない。
- human decision は列挙済み choice または schema 化された authority input として記録する。
- response は interaction identity、revision、participant identity、expected result type、evidence
  contract を照合してから受理する。
- response 受理後の次処理は Workflow が選び、同じ completion Operation call の内部で
  runtime advance evaluator が次の境界まで進む。

個別の profile work-result submission と application Decision resolution は registered typed
Operation として公開する。ただし、CNCF common Handle/Continuation/Result lifecycleを
specializeするだけであり、profileごとの独立runtime protocolを作らない。

## Deterministic Operation boundary

runtime internal advance evaluator は pure automatic transition だけでなく、Workflow definition
が明示し、runtime が admit した typed deterministic Operation も実行する。

```text
admit external response if present
  -> automatic transition
  -> deterministic prepare / build / test / inspect / git operation
  -> deterministic result admission
  -> next semantic or human boundary
```

raw shell、任意 argv、任意 working directory、任意 network/credential access を request や AI
response に含めない。provider が operation identity、typed argv projection、working directory、
mutation root、network/credential policy、timeout、retry、receipt schema を所有する。

deterministic Operation の失敗は定義済み policy に従って retry、`WAIT_CONDITION`、
`AI_WORK_REQUEST`、`HUMAN_DECISION_REQUEST`、`FAILED` のいずれかへ分類する。未定義の失敗を
AI work へ自動変換しない。

## Suspend/resume terminology

公開 lifecycle に別の `Suspended` state token、mutable `Continuation` object、独立した
`resumeWorkflow` Operation を設けない。WorkflowInstance 自体が durable な
waiting/progression state を所有し、再開とは同じ handle と canonical Continuation に対応する
`ContinuationResult` を Continuation-selected completion Operation に渡すことである。

`Continuation` は CNCF common contract の public typed boundary であり、Skill/Codex wire
では `ContinuationRequest` / `ContinuationResult` として schema-versioned, fail-closed に
交換できる。ただし、これは次を満たす。

- `WorkflowHandle` と並ぶ第二の root identity、独立 state ownership、または lifecycle にならない。
- continuation identity、expected revision、ContextSnapshot、typed completion/evidence は
  handle に解決される current boundary の相関・admission contract である。
- caller に Workflow mutation authority を与えず、admission 後の次状態は runtime が選ぶ。
- public application projection は `WorkflowInteraction` であり、canonical Continuation の
  kind と typed response contract を省略・置換しない。

したがって既存の `GoalPhaseContinuation`、`SplitPhaseContinuation`、
`Suspended(Continuation)`、typed `resume` result は、意味要件を失わずに common
Continuation request/response correlation と registered completion Operation admission schema へ
再配置する。

## Read and control Operations

この決定は progression boundary を定める。次の read/control Operation は別に公開できる。

- `getWorkflowStatus(handle)`: read-only projection。advance しない。
- `getWorkflowHistory(handle, cursor)`: bounded read-only history。advance しない。
- `cancelWorkflow(handle, expectedRevision, authority, idempotencyKey)`: 明示 authority を検証する
  lifecycle mutation。cancel admission 後に terminal interaction を返せる。

これらは runtime advance evaluator の代替にならず、automatic transition や deterministic
Operation を独自に進めない。

## Compatibility disposition

既存文書の語彙は次のように読み替える。

| Existing term or operation | Selected disposition |
| --- | --- |
| `StartWorkflowRun` | public API から profile-specific `StartGoalPhase` / `StartSplitPhase` / `StartRepositorySync` へ置換 |
| `AdvanceWorkflowRun` / `advance(runId, ...)` | public Operation から除外。bounded advance は start/completion Operation 内部の CNCF runtime evaluator |
| `Continuation` outcome envelope | canonical Continuation を含む public `WorkflowInteraction` projection。独立 root/lifecycle は作らない |
| `Suspended(Continuation)` | durable runtime suspension と public interaction を分離し、canonical Continuation は current boundary として保持 |
| typed `resume` input | current Continuation に対応する `ContinuationResult` として Continuation-selected completion Operation へ渡す |
| generic `SubmitWorkResult` | profile-specific registered result submission Operation へspecialize |
| generic `ResolveDecision` | application-specific registered Decision resolution Operation へspecialize |
| `runId` | handle が resolve する WorkflowInstance identity。raw identifier の単独利用を public contract に要求しない |

この読み替えは human-selected profile、closed interaction kind、revision/idempotency、lease、
deterministic admission、AI cost policy、status/history の read-only 性を変更しない。

## Required document reconciliation

この仕様を public ABI に採用する際は、少なくとも次を同じ revision で整合させる。

1. `sm-workflow-design.md` の public Operation、Continuation、transport adapter、実行 protocol。
2. `phase-1.md` と executable-specification checklist の Operation binding、Continuation closure、
   completion acceptance。
3. `sm-goal-phase-workflow-definition.md` の `Suspended(Continuation)`、
   `GoalPhaseContinuationResult`、CLI selector。
4. `sm-split-phase-workflow-definition.md` の `Suspended(Continuation)`、resume、
   `SplitPhaseContinuation`。
5. `sm-repository-sync-workflow-definition.md` の pending continuation、resume 表現。

部分更新で generic start/advance と profile-specific application Operation、または
Continuation lifecycle と handle/interaction projection を併存させない。

## First executable specifications

1. `StartGoalPhase`、`StartSplitPhase`、`StartRepositorySync` がそれぞれ profile 固有 typed
   input を admit し、安定した `WorkflowHandle` を返す。
2. direct skill selection が対応する invocation authority として記録され、recommendation だけでは
   start が拒否される。
3. `SPLIT_REQUIRED` から `SplitPhaseWorkflow` が自動開始されず、人間選択後に別 handle が作られる。
4. profile-specific start/completion Operation 内部の runtime advance evaluator が複数の
   automatic transition / deterministic Operation を吸収し、次の一件の
   `WorkflowInteraction` だけを返す。
5. AI response と human response が同じ Handle/Continuation admission architecture を通り、
   interaction種別ごとの registered Operation、typed schema、authority ruleでfail-closedに
   admissionされる。
6. stale revision、異なる interaction ID、結果型不一致、別 participant の response が state を
   変更しない。
7. 同じ idempotency key の start/completion replay が duplicate instance、transition、Operation、
   Work Order、Decision を作らず同じ結果を返す。
8. process restart 後も同じ handle で current interaction を取得し、response を渡して継続できる。
9. public payload に SQLite path、内部 StateMachine state、transition candidate、完全な history、
   arbitrary command が現れない。
10. `getWorkflowStatus` と `getWorkflowHistory` が read-only であり、runtime advance evaluator
    とは別の progression path を形成しない。

## Deferred ABI work

次はこの仕様で先取りせず、Cozy generated ABI、CNCF admission/progression contract、
sm-workflow public protocol を同時に設計する段階で確定する。

- Operation と type の最終命名・namespace。
- handle の encoding、署名、capability scope、失効・rotation。
- interaction/response の exact schema と version negotiation。
- expected revision と lease の transport representation。
- WAIT wake event の delivery と authorized event producer contract。
- cancel、timeout、retention、history pagination の exact semantics。
