# Phase 5: Goal-Oriented Iteration Semantics

Status: planned

## Goal

技術的再実行である Retry と、Goal 達成のための意味的反復である Iteration を明確に分離し、AI / Human participant を含む Workflow の反復作業を表現しやすくする。

## Scope

- workflow-level Iteration semantics の必要性を実運用 evidence から評価
- NeedsInput / NeedsRevision / Rejected 等の participant outcome
- review -> revision -> review のような Goal-oriented loop
- iteration count / termination condition / escalation の必要最小限の表現
- RetryPolicy と Iteration semantics の非混同

## Initial compatibility rule

専用 Iteration feature がなくても、既存 StateMachine の State / Transition / Guard / Context で同等の loop を表現できることを維持する。専用 abstraction は、実運用で反復パターンの重複と運用上の必要性が確認された範囲に限定する。

## Acceptance criteria

- Retry と Iteration が runtime / history / diagnostics 上で区別される。
- AI / Human participant の revision-oriented outcome を通常 failure と混同せず progression に利用できる。
- dedicated Iteration abstraction を導入する場合も既存 StateMachine semantics と整合する。
