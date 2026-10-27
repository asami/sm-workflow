# SBT／Flutterの検証結果共通化とCNCFとの責務分担

Date: 2026-10-28 (Asia/Tokyo)
Status: 設計方針合意・実装計画。実装、検証、配備は未実施。
Audience: sm-workflow開発、CNCF開発。

## 合意した設計

CNCFにFlutter固有の `flutter_build`、`flutter_test`、`flutter_analyze` を追加し、
sm-workflow側でSBTとFlutterの検証結果を共通化する。
既存の `sbt_xxx` DSLの意味は変えない。CNCFにWorkflow共通検証モデルを移す案は採用しない。

| 所有者 | 責務 |
| --- | --- |
| CNCF | 各ツールの型付きDSLと結果、UoWでの実行、出力解釈、既存プロセス管理 |
| sm-workflowのツール別接続部分 | 検証要求を各DSLへ対応付け、各実行結果を共通結果へ変換 |
| sm-workflowのWorkflow | 必要な検証、修正・レビューへの進行、Step／Phaseの正式受入れ |

生産側journal: goldenport-cncfリポジトリの
`docs/journal/2026/10/2026-10-28-flutter-execution-dsl-handoff.md`。
特定端末のcheckoutパスではなくリポジトリ名で参照する。

## 現状の結合箇所

調査対象は `src/main/scala/org/simplemodeling/textus/smworkflow/admission/` 配下。

- `WorkflowSbtValidation.scala`: 検証種類・結果とScalaテストクラスによる選択確認が結合している。
- `GoalPhaseNativeValidation.scala`: 実行・継続が `SbtExecutionAttempt` を直接扱う。
- `WorkflowManagedProcessExecution.scala`: 検証要求をSBTの型付き操作へ接続する。
- `RepositorySyncNativeMergeValidation.scala`: 結果保存にSBT固有の形式がある。
- `StandaloneFixNativeRun.scala`: standalone修正の検証もSBT結果に依存する。
- `GoalPhaseValidationMetadata.scala`: CNCFのScalaTest発見・バインディングを利用する。

これらは変更候補の責務境界であり、今回変更したという記録ではない。
CNCFの `ValidationOperationBindingV1` が生成する `SbtExec` はSBT経路に残す。
FlutterのためにScalaTestの登録情報やクラス名を偽装しない。

## 共通検証モデル

型名は仮案。詳細仕様で既存型との整合を確定する。

| 型 | 内容 |
| --- | --- |
| ValidationRequest | Check識別子、Workspace、検証目的、選択対象、実行プロファイル参照 |
| ValidationAttempt | 実行結果あり、起動前拒否、実行状況不明の区別 |
| ValidationResult | 終了状態、検証判定、要求対象の実行確認、診断・集計・成果物参照 |
| ValidationEvidence | 実際のログ、ツール固有結果、実行環境などの根拠 |

検証目的は `Compile`、`Analyze`、`Test`、`Build` を区別する。
全体／指定対象という選択は目的と分離し、SBTの `TestOnly` はそのSBT固有実現として扱う。
FlutterのBuildはターゲット向け成果物生成でありCompileに見せかけない。
すべてのツールにすべての目的の実装を要求しない。
既存 `FixCheck` の意味と受入れ条件は移行仕様で保持し、単純な列挙置換で脱落させない。

SBT接続部分は既存DSL／`SbtExecutionResult` を使い、Flutter接続部分は新DSL／
Flutter固有結果を使う。共通モデルへ変換しても元の固有結果・根拠は保持する。
プロセス終了と検証合格を分け、成功、失敗、未実行、判定不能を扱う。
必要なテストが0件、全件スキップ、要求した対象が未確認、出力不完全の場合は
自動合格にしない。許容条件は対象プロジェクトの明示した受入れ条件に従う。
ビルドの対象・モード・成果物も要求との対応を確認する。

ログ全文の解析は実行側プログラムで行う。AIには集計・必要な診断・詳細参照を渡し、
全文の読み直しを通常の進行条件にしない。実行状況不明を新しい試行で隠さない。

## 実装計画

1. **W1 — 共通契約と移行仕様**
   結果型、検証目的、選択・実行確認、ツール別接続、保存形式を仕様化する。
   現在のSBT合否、FixCheck、診断、修正回数・再開の意味を対応表で明確にする。
2. **W2 — SBT経路の接続**
   既存SBT結果を共通結果に変換し、GoalPhaseの進行・受入れを共通モデルへ接続する。
   既存のテスト選択確認、失敗・中断・再開が変わらないことを検証する。
3. **W3 — 共通利用箇所と保存・再開**
   RepositorySync、StandaloneFix、保存・復元、結果収集を同じ共通契約に揃える。
   ScalaTest固有の発見・選択はSBT側に保持する。
4. **W4 — Flutter接続**
   検証済みCNCF成果を利用し、解析・通常テスト・指定ターゲットのビルドを接続する。
   初期版は明示的なテスト対象指定を可能にし、ScalaTest同等の自動発見を前提にしない。
   SDKとターゲットの対応条件を明示し、未対応条件を別の操作へ黙って置換しない。
5. **W5 — 統合受入れ**
   実際のFlutterプロジェクトで成功、失敗→修正→再検証、未実行、中断、再開を確認する。
   SBT経路の回帰、Workflow別の結果利用、必要な独立レビューを完了する。
   CNCFの検証成功のみを親Step／Phase合格にはしない。

W1–W3はCNCF側の開発と並行して検討・実装できる。W4–W5の実接続はCNCF成果の返却後に行う。
実機・エミュレータを伴う統合テスト、配布・公開、ネストWorkflow管理は初期範囲外。

## 保存済みSessionと引継ぎ

過去のSBT結果はその形式・意味で読み取れるようにする。欠けた共通フィールドを
成功と補完しない。進行中Sessionで移行が必要なら、対応する公開手順を明示する。
既存のSession、Check、受入れ済み結果、履歴、修正回数を維持し、内部ストレージの直接編集、
再Start、成功結果の再登録、実行済み検証の意味の変更で移行を代用しない。

親チャットはCNCFへの成果・範囲・受入れ条件を明確にし、返却後の統合を所有する。
CNCF側は独立した通常開発として実装・コンパイル・テスト・必要なレビューまで完了し、
版・利用方法・検証結果と根拠・残件を返す。ソース編集だけでは完了としない。
親Session内の子Workflowにせず、ネスト管理機能を先行開発しない。
親に登録済みの依存作業は記録を保持し、実際の返却成果を公開手順で整合させる。
対応手順がなければその統合上の欠落を報告し、架空の成功で解消しない。

このjournalの作成は開発担当チャットへの送信、実装、配備、既存Phaseの受入れを意味しない。

## 開発計画への接続

[開発計画](../../../phase/phase-18.md)に作業単位、依存順序、完了条件、
検証範囲とOPENチェックリストを記録した。計画作成のみで、実装は未開始。

## Phase形式への整理

計画を [Phase 18](../../../phase/phase-18.md) と
[独立チェックリスト](../../../phase/phase-18-checklist.md) に整理した。
旧非番号計画は置き換え、C1–C5／W1–W5の作業単位を引き継ぐ。
CNCFは完了済み116に続く117、sm-workflowは6.7の実行基盤を前提とする18に割り当てた。
共通モデル・SBT接続は生産側と並行可能で、Flutter接続はCNCF 117の検証済み成果を待つ。
既存の6.8〜7の実行順序・受入れは維持し、計画上の全項目は未着手のままとする。
