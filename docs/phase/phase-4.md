# Phase 4: Failure Policy and Execution Safety

Status: planned

## Goal

Phase 2 の単純 Retry を、failure の意味と副作用安全性を考慮した実用的 execution policy へ拡張する。

## Scope

- retryable / permanent を中心とする Failure classification
- FailurePolicy
- failure reason と diagnostics
- idempotency / duplicate protection
- execution identity / idempotency key
- retry / timeout / late completion / redelivery に対する duplicate side-effect protection
- 必要性が実運用で確認された場合の backoff 拡張

## Design constraints

Failure taxonomy は過剰に細分化せず、実際の policy 分岐に必要な分類から導入する。Idempotency は Retry を安全にするための execution contract として扱い、Workflow state の見かけ上の重複防止だけで済ませない。

## Acceptance criteria

- retryable failure と permanent failure で異なる progression を選択できる。
- duplicate invocation / late completion が同一 logical execution の副作用を重複確定しない。
- failure reason と policy decision を history / diagnostics から追跡できる。
