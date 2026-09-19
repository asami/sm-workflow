# Workflow Runtime Extension Prioritization

Date: 2026-09-20

## Context

StateMachine / Workflow を実用運用する際に、単純な状態遷移だけでは不足する制御機能を整理した。候補には Retry、Timeout、Deadline、Timer / Wait、Cancellation、failure classification、idempotency、runtime suspension/resumption、Goal-oriented Iteration がある。

同時に、sm-workflow では機能網羅よりも、多少機能不足でも実運用可能なレベルへ早く到達することを最優先とする。

## Decision

初期運用の必須追加機能は Retry と Timeout に限定する。

Retry は外部 command、AI worker、git、sbt、CNCF Operation 等の一時的失敗を吸収するために必要である。初期版は maximum attempts と fixed delay のみとし、高度な policy は持ち込まない。

Timeout は外部処理が戻らず WorkflowRun 全体が停止し続けることを防ぐために必要である。初期版は Action / participant invocation の execution timeout を中心とする。

これらは Phase 1 の完成後、最初の operational-hardening Phase として追加する。

## Deferred capabilities

残りは次の三群へ分離する。

1. Scheduling / lifecycle: Deadline、Timer / Wait、Cancellation、runtime suspension/resumption。
2. Failure / execution safety: failure classification、FailurePolicy、idempotency / duplicate protection。
3. Goal-oriented iteration: Retry と区別された Iteration、および AI / Human participant の NeedsInput / NeedsRevision / Rejected 等の outcome semantics。

Iteration は当面、既存 StateMachine の通常遷移で表現できるため、専用 runtime feature を初期完成条件にしない。

## Architectural principle

StateMachine core には State / Transition / Event / Guard / Action / Context という一般機構を維持し、Retry 等の実用制御を特殊 State の列挙として埋め込まない。

特に Retry と Iteration を区別する。

- Retry は技術的再実行。
- Iteration は Goal 達成のための意味的反復。

sm-workflow を最初の実戦投入先として使い、実運用で必要性が確認された機能を後続 Phase へフィードバックする。Workflow Engine の機能網羅を先に完成させるのではなく、運用可能な vertical slice を先に閉じる。
