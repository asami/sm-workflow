# Phase 2: Operational Minimum — Retry and Timeout

Status: planned

## Goal

Phase 1 で成立した sm-workflow の local/standalone vertical slice を、外部処理の一時失敗と無期限停止に耐えられる最小限の実運用レベルへ引き上げる。

本 Phase は sm-workflow の早期運用開始を優先し、Retry と Timeout 以外の高度な Workflow runtime feature を scope に入れない。

## Scope

### Retry

- Action / participant invocation に maximum attempts を指定できる。
- retry delay は fixed delay とする。
- current attempt を durable execution context に保持する。
- process restart 後も attempt state が破綻しない。
- maximum attempts 到達時は retry exhaustion を明示的 failure outcome とする。

### Timeout

- Action / participant invocation に execution timeout を指定できる。
- timeout 到達時に invocation を timeout outcome として確定できる。
- timeout 後の late completion が WorkflowRun を二重進行させない。
- timeout / retry の結果を status / history / diagnostics から確認できる。

## Non-goals

- exponential backoff / jitter
- Deadline
- general Timer / Wait
- Cancellation
- detailed failure classification / FailurePolicy
- general idempotency framework
- public suspend / resume API
- dedicated Iteration runtime semantics
- NeedsInput / NeedsRevision / Rejected 等の拡張 outcome taxonomy

## Acceptance criteria

- transient failure を発生させる reference Action が configured maximum attempts 内で成功し、Workflow が継続する。
- permanent failure の reference Action が maximum attempts で停止し、retry exhaustion が観測できる。
- hanging reference Action が configured timeout で終了扱いとなり、Workflow が無期限に停止しない。
- restart を挟んでも attempt count と timeout に関する durable state が整合する。
- Retry / Timeout を使わない既存 reference workflow の semantics を変更しない。

## Completion statement

sm-workflow を実運用へ投入する際、一時的な invocation failure と戻らない invocation が WorkflowRun 全体を恒久停止させない最小限の execution safety が成立した時点で Phase 2 を完了とする。
