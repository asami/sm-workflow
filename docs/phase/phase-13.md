# Phase 13: AI使用量の実測と呼び出し完了表示

status=planned
planned_at=2026-10-22
owner=sm-workflow
development_chat=sm-workflow開発
checklist=[Phase 13 checklist](phase-13-checklist.md)
spec=[AI使用量](../spec/ai-usage-accounting.md)
design=[記録経路](../design/ai-usage-accounting.md)

2026-10-27 allocation: [Phase 6.10](phase-6.10.md) reuses already acquired,
attributable usage for a lightweight Phase-close source-delta ratio. New usage
acquisition and attribution remain here. The 6.10 projection neither requires
this Phase's completion nor expands this Phase into a historical dashboard.

## 目的と位置付け

sm-goal-phaseなどの終了時に、親＋subagent（再委譲を含む）の実測トークン数を
今回の呼び出し単位で表示する。AIコスト削減を観測できるようにする独立した短いPhase。
実際の使用モデルの種類とモデル別思考回数も表示し、親チャットの自己分と委譲先分を
明確に分ける。思考回数は観測したモデル呼び出し数で、turn数しか得られない場合は別表示する。
最上位はCodex/ローカルLLMの区分別表示とする。その他/実行先未確認も明示し、
区分の中で親/委譲先・モデル・token・思考回数と取得状態を確認できるようにする。
既存caller/runtime実行結果と受入れ済み共通Journal/Evidenceを再利用する。
番号順に7〜12の未完了項目を待つ計画ではない。Phase 9のモデル評価は前提にしない。

本計画は進行中の6.4/6.5等に新たな完了条件を加えず、既存Goal/Session/Plan、
受入れ、履歴、修正回数と検証結果を保持する。計画作成ではGoalを開始しない。

## 主経路と順序

[用語規約](../spec/runtime-caller-terminology.md)に従い、hostはネットワーク接続先に
限定する。情報を取得・表示するのはCallerIntegration、実行基盤はRuntime、設定を
置く場所はRuntimeRootと呼ぶ。型・設定・CLIも[採用計画](../notes/runtime-caller-terminology-adoption-plan.md)
に合わせ、旧名互換性を追加しない。U13-01/02に必要な接続部分から適用し、全rename
完了を計測の主経路の前提にしない。

2026-10-23採用：U13-01で、Codex情報の取得責務を既存の呼出元に固定する。
必要な情報要求を返し、呼出元が実際の観測を返して同じSessionを継続する境界を
既存APIへ対応付ける。既に届いたusageは再取得しない。U13-02ではこの主経路で
一件通し、U13-03で再送・未取得・遅延結果の扱いを確認する。
`host_source_read`、別App Server取得proxy、新規Codexモデル実行、
`process_exec_text`の実装を前提にしない。後者は未採用の開発候補である。
terminal復旧情報の変更は[呼出元情報取得の採用計画](../notes/codex-caller-information-adoption-plan.md)
で扱い、Phase 13の完了を既存復旧作業の前提にしない。

| 順序 | 作業 | 受入れ |
| --- | --- | --- |
| U13-01 接続確定 | 現行CallerIntegration/Runtime、CNCF Resources、親子実行識別、Codex usage、実モデルとモデル呼び出し数を対応付け、必要なproducer拡張だけ確定。 | 指標別の取得可否、思考回数の単位、包含/累積の意味、対象の実行接続、共通型・表示場所が明確。 |
| U13-02 GoalPhase主経路 | 一つのsm-goal-phase呼び出しと実際の委譲先を計測し、保存・集計・終了表示まで接続。 | 親＋子の実測tokenと親/委譲先別のモデル・思考回数/取得限界を表示し、保存値から再読可能。fixtureだけでは完了しない。 |
| U13-03 共通化と境界 | RepositorySync/SplitPhaseへ同じ経路を適用。再委譲、失敗、再試行、再送、累積更新、再開、未取得を扱う。 | 二重計上せず、並行チャットを除外し、計測状態を正しく表示。 |
| U13-04 配備・引渡し | 必要なCallerIntegration/三スキルの接続を配備し、実際の呼び出し終了表示と既存経路への無影響を確認。 | 実測対応する接続と未対応接続の境界、検証結果、導入手順を引き渡せる。 |

主経路は「実行→usage取得→親子帰属→保存→完了表示」。型や集計helperだけを先に
広げず、実際に使う呼出元の接続で一件通す。通知が提供されないdesktop経路は接続課題として
残し、独立CLIの実測だけでそのdesktop経路の対応完了を主張しない。

U13-01で実際の推論先による区分を確定し、U13-02のCodex主経路から区分付きで保存する。
U13-03では利用可能なローカルLLMの同じ観測経路と混在実行を接続する。
Codex経由のローカル推論もローカル側だけへ計上する。ローカル接続を検証できない場合は
未検証の接続課題を残し、fixtureだけで実測対応済みとしない。新しいローカルモデルの
選定/導入や能力評価は含めず、Phase 8/9の完了を新たな前提にしない。

## 完了条件・対象外

チェックリストU13の実測接続と境界検証を完了する。終了時には全対象取得済み、
部分計測、未取得を区別する。最終回答生成後の表示はCallerIntegrationが行い、そのための追加AIを
起動しない。計測障害でも通常の開発・制御返却を妨げない。
思考回数・モデル・トークンの取得状況をそれぞれ検証し、親を集計から落とさず、
同じモデルの親/子を区別する。回数を公開しない接続では未取得とturn数の別表示を実証し、
思考回数の実測対応済みとはしない。実モデル別の使用統計は範囲内だが、性能評価は含めない。

Phase累計ダッシュボード、料金推定、モデル比較/LLM評価、予算による制限、
新たな承認/レビューゲート、過去の全sessionログ回収は対象外。
focused検証と影響範囲のレビューを選び、必要な最終統合検証を行う。無関係な既存
成功検証を再実行せず、過去の結果を新しい計測機能の合格証拠にも置き換えない。
共通runtimeの進行中変更は所有者と接続差分を合わせ、巻き戻しや同時編集を避ける。

引継ぎ: [2026-10-22 journal](../journal/2026/10/2026-10-22-ai-usage-accounting-handoff.md)。
取得責務の追記: [2026-10-23 handoff](../journal/2026/10/2026-10-23-codex-caller-information-handoff.md)。
