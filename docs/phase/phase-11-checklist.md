# Phase 11 checklist

status=completed
completed_at=2026-10-20
phase=[Phase 11](phase-11.md)

J1／J2／J3は通常検証・独立Stepレビュー・ローカルコミットで受入れ済み。必須Phase全体reviewの命名指摘は機械的改名で解消した。初回全体testの12件失敗と修正後のTestコンパイル失敗を保持し、既存契約との接続修正および集合型変換の訂正の2回を145件の重点テスト・独立focused reviewで確認した。再検証の通常全体testは159スイート・1504件成功、失敗0・aborted0で終了した。既存の明示opt-inが必要なSIGTERM fixture準備1件はcanceledで、実行済みとは扱わない。最終releaseはこの完了記録を含む独立コミットで確定する。6.4の受入れやチェックを移し替えない。

## J1: 実記録と読取

- [x] P11-01: 既存のOperation／review結果とDataStoreの実際のwrite・identity契約から最小型と適用境界を確定する。
- [x] P11-02: 通常Operationが共通writerで実Entryを保存し、業務結果と別の記録状態／参照を返す。
- [x] P11-03: 別プロセスの公開readで実Entryを取得し、元source・対象・結果・帰属を確認する。

## J2: Findingと検索

- [x] P11-04: 実際の受理済みFindingを既存eligibilityで記録し、journal転記だけのAI作業を発行しない。
- [x] P11-05: scope／kind／source／期間を指定した有限findと、短い表示・選択詳細を利用できる。
- [x] P11-06: 業務成功と記録失敗を分離し、同一source再参照・矛盾・不明結果を扱って業務を再実行しない。

## J3: 試用への引渡し

- [x] P11-07: 同梱smスキルの共通記録説明を整合させ、実際の配備元・配備先へ既存の導入手順で反映する。
- [x] P11-08: 6.4試用hostの通常公開経路でEntry参照・検索・読取を確認する。未対応機能を公開済みと表示しない。
- [x] P11-09: 6.4の既存Goal／要求／履歴／修正回数／検証結果を保持した採用境界と残作業をhandoffに記す。
- [x] P11-10: 変更に対応した検証、通常review／closure、利用説明から実際の受入れ範囲を確定する。
