# Workflow Runtime Extension Roadmap

## Purpose

sm-workflow を早期に実運用へ投入しつつ、StateMachine / Workflow の実用的な制御機能を段階的に拡張するための設計メモ。

基本方針は、特殊用途ごとに State を増やすのではなく、StateMachine の汎用機構（State / Transition / Event / Guard / Action / Context）を土台にし、Workflow 層で運用上の意味論を与えることである。

## Priority

最優先は「多少機能不足でも sm-workflow を運用可能なレベルで完成させる」こととする。したがって初期運用に不可欠な機能だけを先行し、残りは実運用から得られる要求を踏まえて段階的に導入する。

## Mandatory operational minimum

### Retry

外部 command、AI worker、git、sbt、CNCF Operation 等の一時的失敗から回復できるようにする。

初期版は以下に限定する。

- fixed maximum attempts
- fixed retry delay
- attempt count を実行 context に保持
- retry exhaustion を明示的 failure として扱う

exponential backoff、jitter、詳細な failure taxonomy は後続 Phase とする。

### Timeout

Action / participant invocation が永久に戻らない状態を防止する。

初期版では Action execution 単位の timeout を提供し、timeout を観測可能な execution outcome とする。

## Later extensions

### Scheduling and lifecycle control

- Deadline: 相対 timeout ではなく絶対時刻による期限。
- Timer / Wait: retry delay 以外の通常の待機、指定時刻・経過時間による再開。
- Cancellation: 実行中または待機中 Workflow の明示的取消。
- Runtime suspension/resumption: WAIT / Human / AI / external event 等で実行資源を解放し、event で継続する内部機構。公開 API として suspend/resume を必須とはしない。

### Failure and execution safety

- Failure classification: retryable / permanent および代表的 failure reason。
- FailurePolicy: failure class に応じた retry / fail / transition。
- Idempotency / duplicate protection: retry や再送時の二重副作用を防止する execution identity / idempotency key。

### Goal-oriented iteration

Retry と Iteration は分離する。

- Retry: 同じ処理を技術的理由で再実行する。
- Iteration: 結果を評価し、Goal 達成のために業務的・知的作業を反復する。

初期版では Iteration を通常の StateMachine transition で表現する。後続で必要性が確認された場合に workflow-level semantics を追加する。

AI / Human participant の outcome として Completed / Failed だけでなく NeedsInput / NeedsRevision / Rejected 等を扱える拡張もこの領域に含める。

## Design constraints

- Retry 回数を Retry1 / Retry2 / Retry3 のような State 展開で表現しない。
- 実行時 counter や deadline は Context / execution state に保持する。
- Workflow runtime の convenience semantics と StateMachine core semantics を混同しない。
- Phase 1 の完成と運用開始を後続拡張のために遅らせない。
- 後続拡張は sm-workflow の実運用で得られた evidence を優先して具体化する。
