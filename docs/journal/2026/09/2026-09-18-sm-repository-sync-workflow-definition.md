# SM Repository Sync Workflow Definition

Date: 2026-09-18

## Decision

legacy `cncf-repository-sync` の repository integration semantics を、public skill
`sm-repository-sync` と CML Workflow definition `RepositorySyncWorkflow` に分離して
Phase 1 の reference profile とする。

この追加は legacy skill の置換、rename、forward、state migration、dual write ではない。
`cncf-repository-sync` と `sm-repository-sync` は別の entry point、durable state、runtime
policy を持ち、長期間併用する。

## Architecture

`RepositorySyncWorkflow` は one repository / named branch / configured tracking mapping を
snapshot し、full-state dirty checkpoint、fetch、relation classification、fast-forward または
non-rewriting pending merge、validation、merge review、merge commit、non-force push、equal-tip
verificationを Workflow-owned deterministic operation として進める。

通常の equal/local-ahead/remote-ahead と conflict-free merge は AI Work Order を発行しない。
非重複・同一値・canonical projection は修正の衝突ではなく、Workflowが composition する。
同じ semantic target への異なる修正は `ModificationCollision` として、
`ResolveRepositoryMergeConflicts` または human Decision を必須にする。AI は
`ReviewRepositoryMerge`、`RepairRepositoryMerge` の bounded semantic Action も担当する。Skill は leased
`AIWorkRequest` 一件を実行して typed result を返すだけであり、Git command、branch/remote
選択、checkpoint、merge、commit、push、retry、next state を選ばない。

Git provider は current run revision、frozen tips、remote/ref mapping、allowed mutation roots、
network policy、idempotency key、receiptに拘束された operation だけを実行する。AI sandbox を
緩和せず、raw executable/argv/shell/credential/path の注入、force/rebase/squash/amend/reset/
clean/stash、tag/PR/release/publication/deployment、remote configuration 変更を拒否する。

## Collision boundary correction

非重複、同一 normalized value、canonical projection は compatibility condition であり、
修正の衝突ではないため Workflow が deterministic composition できる。一方、同じ stable
semantic target へ current と desired が異なる修正をした `ModificationCollision` は、結果が
一見一意でも Workflow が自動採択しない。authority が不変で解釈を一つに定められる場合は
AI の `ResolveRepositoryMergeConflicts`、authority/identity の変更または複数妥当解は human
Decision を必須とする。この規則を共通 three-way merge boundary と `SplitPhaseWorkflow` にも
適用した。

dirty state は全パスを一つの non-acceptance checkpoint に保存するか、partial/mixed index、
unsafe material、scope ambiguity として停止する。subset sync は提供しない。push の間に remote
が進んだ場合は一回だけ再fetch/reclassifyし、二度目は AIに委譲せず
`WAITING_FOR_REMOTE_STABILITY` に遷移する。

## Design records

- [SM Repository Sync Workflow Definition](../../../notes/sm-repository-sync-workflow-definition.md)
- [sm-workflow design](../../../notes/sm-workflow-design.md)
- [Phase 1](../../../phase/phase-1.md)
- [Phase 1 checklist](../../../phase/phase-1-checklist.md)

## Deferred implementation condition

これは normative design input であり、CML `WORKFLOW`/generated ABI と Textus-managed
durable runtime の prerequisite が閉じるまでは、actual Git provider や public skill を
擬似実装しない。実装時は `RepositorySyncWorkflow` を source of truth とし、legacy skill の
prose procedure をもう一つの state machine として複製しない。
