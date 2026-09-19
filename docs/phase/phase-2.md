# Phase 2: Adopt Workflow Retry and Timeout

Status: planned

## Goal

Cozy / CNCF が提供する Retry / Timeout を sm-workflow の Action / Participant execution で利用し、多少機能不足でも実運用可能な最小 safety を得る。

## Dependency

- Cozy Workflow Retry / Timeout ABI
- CNCF Workflow Retry / Timeout runtime

sm-workflow は Retry / Timeout semantics を再実装しない。

## Scope

- sm-workflow の必要 invocation に maximum attempts / fixed retry delay を設定する。
- hanging invocation に execution timeout を設定する。
- retry exhaustion / timeout を既存 status / result / diagnostics に接続する。
- reference workflow で transient failure と hanging invocation を検証する。

## Non-goals

Deadline、general Timer / Wait、Cancellation、FailurePolicy、general idempotency、Iteration 等の Cozy / CNCF 将来拡張。sm-workflow 側では使用が必要になるまで計画対象にしない。

## Acceptance

transient failure は configured retry 内で回復でき、hanging invocation は timeout で無期限停止を回避できる。sm-workflow に独自 Retry / Timeout engine を持ち込まない。
