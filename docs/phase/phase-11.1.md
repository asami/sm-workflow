# Phase 11.1: 共通Journalの判断・課題・引継ぎ管理

status=planned
planned_at=2026-10-20
owner=sm-workflow
depends_on=phase-11.md
design=[共通開発Journal](../design/development-journal.md)

## 目的と順序

Phase 11の実store・writer・公開読取・試用スキルを再利用し、Decision／Issue／Handoffと
型付き管理を追加する。6.4試用開始やarchive完成の前提にはせず、必要な機能から独立実施する。
Phase 11の先行効果を得るために本Phase全体を前倒ししない。

1. 実際の一件の指摘→対応判断→課題→引継ぎを既存の意味判断結果から記録し、関係を辿って読む。
2. 訂正と状態変更を期待revision付き管理Operationへ接続し、元内容・理由・出所を保持する。
3. 必要なscope／状態／関連ID検索と有限batchを接続し、部分成功を個別結果で返す。
4. 対応済みのスキル利用・配備を拡張し、実経路と必要な差分検証から通常closureする。

## 完了チェックリスト

- [ ] P111-01: Decision／Issue／Handoffの最小型を定義し、実際の意味判断結果から通常記録できる。
- [ ] P111-02: 指摘・判断・修正・検証・引継ぎをID参照で辿れ、同じ記述を複数正本にしない。
- [ ] P111-03: 訂正理由・前revisionを保持し、同時訂正の衝突を黙って上書きしない。
- [ ] P111-04: 状態・関連ID検索、選択詳細、有限batchの個別成功／失敗／不明を実際に扱える。
- [ ] P111-05: 試用スキルが共通Operationを使い、管理だけのAI反復や別index管理を要求しない。
- [ ] P111-06: 変更に対応した検証・通常review／closureと後続利用への引渡しを完了する。

検索や記録はHuman consent、review acceptance、Workflow進行、計画採用を代行しない。
一般的なHuman判断を記録することと、進行中の特定質問へ回答することを混同しない。
既存のPlan／Goal／修正履歴と検証結果を保持する。承認済みの別機能を再検証するのは
実際の影響がある場合に限る。Evidenceの保持管理は6.4の所有、CBD評価は10の所有とする。
全Git文書移行、全スキル対応、export、別Workflow engine、独自SQL／lockは含めない。
