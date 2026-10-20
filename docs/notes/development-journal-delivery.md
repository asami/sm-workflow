# 共通Journal先行開発の判断ノート

date=2026-10-20
status=planned

## 決定

6.4の完成を待ってJournalを拡張する当初案を変更し、[Phase 11](../phase/phase-11.md)を
6.4の残作業より先に短く実施する。6.4はsm-workflowの実際の試用でもあるため、
共通保存・検索だけでなく試用スキルの更新・配備・実接続までをPhase 11に含める。
番号11は7〜10の後という意味ではない。関連機能を全部一度に仕上げることも要求しない。

| 所有 | 範囲 |
| --- | --- |
| Phase 11 | 共通Entry、実結果／Findingの記録、read／find、同梱・配備スキル、6.4試用への引渡し。 |
| Phase 12 | 共通EvidenceのID・保存・取得とAdvanceからの自動記録。Phase 11のJournalへ実保存観測を接続する。 |
| Phase 6.4 | Phase 12の採用・実運用検証。共通サービス上でbatch・圧縮・保持管理を拡張する。 |
| Phase 11.1 | Decision／Issue／Handoff、関係検索、訂正・状態更新・batch管理。 |

## この分割の理由

レビュー指摘の転記は、既に型付きの意味判断結果が存在するため機械処理へ移しやすい。
そこで一件の実記録・別プロセス読取を先に通す。Evidence保存が未完成でも、既存の
review／Operation結果参照でJournalを使える。偽のEvidence IDを挿入する必要はない。
全種類・全管理機能を先に作る案は初期効果を遅らせるため11.1へ分ける。

構造化記録は詳しく保持し、AIへの通常応答は短くする。明示された項目・件数で詳細を検索し、
読取のたびに全履歴を返さない。AIの意味判断は共通型で受け、追加のjournal担当を呼ばない。
実際の入力token／呼出し／転記回数を既存の利用結果から確認し、削減量の未測定値は示さない。

## 保持と採用

2026-10-20の追加採用: [Phase 12](../phase/phase-12.md)をsm-workflow開発で先行実施し、
6.4のE2へ実保存まで通った最小経路を渡す。全Action・媒体・管理機能の完成を待たない。
AIからの通知・転記は既知のAction生成物には不要。明示登録は外部資料などに残し、
公開参照はJournal/Evidence ID、物理保存は既存CNCF管理I/Oとする。
[両開発チャット向けhandoff](../journal/2026/10/2026-10-20-phase-12-common-evidence-handoff.md)を参照。

これは設計と計画の記録であり、6.4の現在の質問へ回答したりSessionを変更したりしない。
開発checkout／試用hostの実際の進捗を所有チャットで確認し、既存Goal／E1受入れ／
修正回数／検証結果を保持して通常の採用境界で使う。古い文書上のstatusを理由に進捗を戻さない。
現在発行済みのfile-edit-only要求はその契約のまま扱う。新しい要求への切替は正規の境界で行う。
6.3の計画は変更しない。終了処理・SIGTERM実装を混ぜず、SIGINT制御返却は将来候補のまま。

関連: [設計](../design/development-journal.md)、[用途精査](../spec/development-journal-skill-usage-inventory.md)、
[引継ぎ](../journal/2026/10/2026-10-20-common-journal-phases-handoff.md)。
