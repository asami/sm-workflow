# Phase 11.1: 共通Journalの判断・課題・引継ぎの単件主経路

status=in_progress
planned_at=2026-10-20
owner=sm-workflow
depends_on=phase-11.md
design=[共通開発Journal](../design/development-journal.md)
sequence=Phase 7 -> Phase 11.1 -> Phase 8 -> Phase 8.1 -> Phase 9 -> Phase 18
split_record=[分割記録](https://github.com/asami/sm-workflow/blob/9debf6484e3e16e6739b83688a30a814772c7b29/docs/journal/2026/11/2026-11-11-journal-single-entry-and-batch-split.md)

## 目的と順序

Phase 11の実store・writer・公開読取・試用スキルを再利用し、Decision／Issue／Handoffと
単件の型付き管理を追加する。2026-11-11の分割により、Phase 7の後、Phase 8の前に
使える最小経路を完成させる。Phase 7や受入れ済みの11／12を開き直す前提にはしない。
一括管理と利用経路拡大は[Phase 11.2](phase-11.2.md)へ移し、その完成を待たずに閉じる。

1. 実際の一件の指摘→対応判断→課題→引継ぎを既存の意味判断結果から記録し、関係を辿って読む。
2. 訂正と状態変更を期待revision付き管理Operationへ接続し、元内容・理由・出所を保持する。
3. 必要最小限のscope／状態／関連ID検索と選択詳細の読取りを既存の公開経路へ接続する。
4. 実際に利用する経路を一つ選び、通常記録→関連参照→訂正／状態更新→別プロセス読取を
   一件通す。必要な差分検証・独立review・利用可能な成果の引渡しで通常closureする。

## 2026-11-11の移管対応

既存P111 IDを保持する。次の移管は未実装作業の配置変更であり、完了ではない。

| 元項目 | 11.1に残す部分 | 後続の移管先 |
| --- | --- | --- |
| P111-04 | 単件の状態・関連ID検索、選択詳細 | 有限batchと個別結果はP112-01〜03 |
| P111-05 | 一つの実利用経路で共通Operationを使う | 追加の選択スキル／利用経路への展開はP112-04 |

P111-01〜03は維持し、P111-06は絞った単件主経路の検証・引渡しを所有する。
Phase 8の不要確認削減やPhase 9のTakeoverをここで実装せず、それらが参照できる記録を提供する。

## 完了チェックリスト

- [x] P111-01: Decision／Issue／Handoffの最小型を定義し、実際の意味判断結果から通常記録できる。
- [x] P111-02: 指摘・判断・修正・検証・引継ぎをID参照で辿れ、同じ記述を複数正本にしない。
- [x] P111-03: 単件の訂正／課題状態更新で期待revision、訂正理由・前revisionを保持し、同時更新の衝突を黙って上書きしない。
- [x] P111-04: 単件主経路に必要な状態・関連ID検索と選択詳細を実際に扱える。有限batchはP112-01〜03へ移管し、ここでは完了を要求しない。
- [x] P111-05: 一つの実利用経路が共通Operationを使い、管理だけのAI反復や別index管理を要求しない。追加経路はP112-04へ移管する。
- [ ] P111-06: 実作業一件の記録・関連参照・訂正／状態更新・別プロセス読取を検証し、必要なreview／closureと利用可能な成果の引渡しを完了する。

検索や記録はHuman consent、review acceptance、Workflow進行、計画採用を代行しない。
一般的なHuman判断を記録することと、進行中の特定質問へ回答することを混同しない。
既存のPlan／Goal／修正履歴と検証結果を保持する。承認済みの別機能を再検証するのは
実際の影響がある場合に限る。Evidenceの保持管理は6.4の所有、CBD評価は10の所有とする。
全Git文書移行、全スキル対応、export、別Workflow engine、独自SQL／lockは含めない。
2026-11-11の分割時点では計画変更のみであり、既存Goal／SessionのStart・改訂・採用・完了操作や
Phase 6.11の追加修復は行わなかった。現在の単件主経路の実装・利用範囲は以下で区別する。

## 単件主経路の実利用と残る受入れ境界

Decision／Issue／Handoff、型付き関係、期待revision付き訂正・状態更新、限定検索と
選択詳細は共通Journalへ接続した。[利用手引き](../notes/development-journal-delivery.md#phase-111-単件apiの利用手引き2026-11-17)で
既存の公開Operationとhost前提を示す。Phase 11／12の不変Entryと読取ABIを維持する。

選択した利用先は、このPhaseのcallerが実際に行ったhost保存先の整合判断と、
その前提を利用手引きへ引き渡す一件である。DecisionからIssue／Handoffを参照し、
Issueの訂正、状態変更、元revisionと現行版の別プロセス読取、状態・関連IDによる
限定検索を通常のinstalled CLIで確認した。元内容・出所・変更理由は同じEntryに保持する。
JournalのIssue状態は正式な検証・review／closureの代わりに完了扱いにしない。

過去の不変Findingは限定した公開検索では見つからず、実Findingから始まる過去記録の
連鎖は未検証である。Finding型の関係・読取の仕様検証と、今回の実作業の意味判断を
出所とする単件実利用を区別し、この不足を架空のFindingで埋めない。Humanの追加方針に
従い、過去記録の探索を続けず、今回の実記録と必要な検証・独立reviewで閉じる。

保存先解決の共通化と既存データの移行は、別途依頼済みのsm-workflow開発が所有する。
今回のhost設定整合を恒久的なruntime修正とは扱わず、別保存機構や移行を追加しない。
運用結果・raw logは `~/backup/work.d/YYYY/MM/DD/sm-workflow/phase-11.1/` に保持し、
`docs/journal/` は設計記録だけに使う。P111-06の正式review／closureは未完了である。
