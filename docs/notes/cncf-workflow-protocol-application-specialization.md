# CNCF Workflow Protocol Application Specialization

Status: normative application boundary

## Position

sm-workflow は CNCF Generic Workflow JSON Protocol の application consumer である。generic Start/Handle/Continuation/WorkOrder/Decision/Wait/Terminal/Result/Evidence/Presentation/ExecutionRequirement envelope を再定義しない。

sm-workflow が所有するのは software-development application schema と policy である。

## Application schemas

- GoalPhaseStartInput / GoalPhaseResult
- SplitPhaseStartInput / SplitPhaseResult
- RepositorySyncStartInput / RepositorySyncResult
- software-development WorkOrder input/result schemas
- application Decision payloads
- application terminal payloads

Phase / Step / Slice、repository、review、validation、commit 等は application payload 内に置く。

## GoalPhase start

skill は Phase 情報を収集し、schema-validated `GoalPhaseStartInput` を CNCF generic StartRequest の `input.payload` として渡す。skill prose や conversation history を canonical input にしない。

StartResult は CNCF generic WorkflowHandle + Continuation をそのまま使用する。

## Continuation

sm-workflow は generic Continuation.kind を制御に使用し、presentation text を parse しない。WORK_ORDER の application payload を typed software-development task として worker に渡し、typed Result/Evidence を返す。

## Console

CNCF generic Presentation を基礎に、Phase / Step / review / validation 等の application context を人間向けに表示する。JSON が正本であり、console text は projection である。

## Reasoning mapping

Workflow WorkOrder が要求する abstract reasoning level (`ROUTINE | STANDARD | DEEP | CRITICAL`) を、sm-workflow skill/host の versioned mapping policy で concrete worker profile に写像する。

mapping は設定/policyであり GoalPhaseWorkflow の StateMachine semantics ではない。actual selected profile/model/effort は可能な範囲で execution evidence として返す。

## Phase 1 executable specification

Phase 1 は少なくとも各 reference workflow について以下を JSON fixture で証明する。

```text
application StartInput
  -> CNCF StartRequest
  -> StartResult + Continuation
  -> application WorkResult / Decision
  -> generic Result submission
  -> next Continuation
  -> ...
  -> Terminal + application Result
```

Codex console projection も fixture response から生成できることを確認する。
