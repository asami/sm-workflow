# Phase 3: Scheduling and Lifecycle Control

Status: planned

## Goal

実運用で長時間継続する WorkflowRun に必要となる時間制御と lifecycle control を追加する。

## Scope

- Deadline
- general Timer / Wait
- Cancellation
- runtime-internal suspension / resumption
- WAIT / Human / AI / external event 等で execution resource を解放して durable に継続する仕組み

## Design direction

suspension / resumption は runtime の内部機構として設計し、公開 API に suspendWorkflow / resumeWorkflow を露出することを前提にしない。semantic event / advance を通じた継続を基本とする。

Retry delay は Phase 2 の限定機能として維持し、本 Phase の general timer semantics へ自然に統合可能な構造とする。

## Acceptance criteria

- absolute deadline を durable に評価できる。
- timer/wait を process restart 越しに継続できる。
- cancellable な WorkflowRun を安全に terminal state へ移せる。
- waiting instance が実行 thread/resource を占有し続けない。
