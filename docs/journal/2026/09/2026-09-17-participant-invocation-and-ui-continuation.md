# Participant Invocation と UI Continuation

Date: 2026-09-17
Status: Design refinement

## Refinement

Orchestration / Continuation はWorkflowRun全体の排他的modeではなく、Action/Participant単位のInvocation Bindingとして扱う。

`sm-workflow` が外部SkillからContinuation-drivenに利用される場合でも、runtime内部のtyped deterministic OperationはORCHESTRATION/direct invocationで実行できる。

```text
Parent/Skill -> advance
  sm-workflow
    -> BuildProject    : direct/orchestration
    -> RunTests        : direct/orchestration
    -> AI Review       : continuation -> Skill/AI
    -> Human Approval  : continuation -> UI
    -> CommitChanges   : direct/orchestration
```

したがってContinuation利用は「全ActionをParent/Skillへ返す」ことを意味しない。semantic boundaryのうち外部Participantとの非同期境界だけをyieldする。

## Human UI

Human Approval/DecisionはContinuationの標準Participantとして扱う。

`sm-workflow` はUI dialogを直接開かず、Approval/Decision Continuationをpersistしてreturnする。Web/Flutter/CLI等のUI clientはpending continuationを取得し、Context/Schema/Presentation Hintから画面を構成し、typed Resultをsubmitする。

これによりUI sessionとWorkflowRun lifetimeを分離し、長時間workflow、端末切替、再接続に対応できる。

## AIとの共通性

AI WorkOrderとHuman ApprovalはParticipantは異なるが、Continuation identity、revision/snapshot、ContextBundle、CompletionContract、EvidenceContract、lease/idempotent resumeを共有する。

- AI continuation -> Skill / MCP / AI worker
- Human continuation -> Web / Flutter / CLI UI

## sm-workflow impact

既存のContinuation modelをAI/Codex専用にしない。generic Participant Continuationとして設計し、WorkOrderとDecision/Approvalをprojection/profileとして扱える構造を検討する。

Phase 1では現在のAI-oriented vertical sliceを維持しつつ、public schema/APIが将来のHUMAN continuationを阻害しないことを確認する。CML/CNCF Phase 62/77で確定する共通Invocation ABI/runtime contractをconsumerとして採用する。
