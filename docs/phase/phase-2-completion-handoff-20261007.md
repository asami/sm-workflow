# Phase 2 完了へ向けた作業handoff

作成日: 2026-10-07。対象: `/Users/asami/src/dev2026/sm-workflow`。
引継ぎ先: 既存の「sm-workflow開発」タスク。
Goal identity: `01a103b3-5f45-7ec2-ac5c-7f9fb01df42a`。

この文書は作業手順の引継ぎであり、実装・テスト・再開の実行記録ではない。
既存Goal、原始base、PLAN epoch、元の9件、レビュー・失敗履歴、消費済み
カウンタ3/3と修正枠を保持する。新Goalや新しい修正枠を作って履歴を消さない。

## 1. 今回の到達点

Phase 2は、SplitPhase・RepositorySync・GoalPhaseの**公開Operationを直接呼び、
実際の効果と保存状態からの再開を実証する**ところまでで完了させる。
Executable Specificationが入力・AI回答・レビュー回答・Human回答を供給する。
それを実際のAI／人との対話連携が完成した証拠とは扱わない。

CLI配線・スキル接続・抽象思考モードはPhase 6、workspace／overlayはPhase 7、
広範なtransport・provider・回復パターンはPhase 8。これらをPhase 2へ戻さない。
現在の正本は[Phase 2契約](phase-2.md)と
[P2-M01..M05チェックリスト](phase-2-executable-specification-checklist.md)。

## 2. 現在地と再利用する証拠

| 対象 | 確認されている状態 | 残る扱い |
| --- | --- | --- |
| SplitPhase | 公開OperationでPreview／Apply、実際の文書変更、Human分岐、別JVM・中断後の回復を検証 | 選択した有限シナリオをP2-M02/M03へ対応付ける |
| RepositorySync | 実Git同期・競合処理・SBT成功／失敗・実効果後の別JVM回復を検証 | 既存証拠を統合受入へまとめる |
| GoalPhase | ordered Step、判断・修正・レビュー回答、コミット、Phase完了、実SBTを検証 | Step完了とPhase完了を区別して統合受入する |
| 1121 | 直近4項目の補正後、8 suite・163/163成功。限定独立レビューPASS | Phase全体のレビューPASSには読み替えない |
| 1122 | 全体1487件中1471成功・16失敗、166 suite完了・1 suite中断 | 全体FAILを保持し、未解決2参照の補正後に必要な再検証 |
| 実行状態 | 追加2参照の補正承認待ちが続き、既存Goalはblocked | 実行再開・補正範囲の反映を既存タスクで扱う。本書の作成だけでは再開しない |

最新の詳細は[統合検証journal](../journal/2026/10/2026-10-07-three-workflow-integrated-validation.md)
の `Actual1121`、`Actual1122`、`Read-only cause established`、`Goal BLOCKED`。
チェックリストの現在の境界表も参照する。古いroute-mapや過去のCURRENT段落にある
「未接続」「全機能必須」を、現時点の未実装リストとして再採用しない。

## 3. 最初に行う補正：2参照を同じ作業で同期する

対象は次のテスト用参照だけ。

- `src/test/resources/workflow/goal-phase-workflow-abi.json`
- `src/test/resources/workflow/goal-phase-candidate-admission-producer-abi.json`

調査済みの差分は、前者52箇所・後者40箇所の出所行番号で、現在の生成値が
いずれも8小さい。CML説明文の整形に伴うもので、その他の構造・Operation・policyは
一致している。これは1122時点の診断であり、その後の編集があれば影響を確認する。

1. 既存タスクの限定補正範囲へ、この2参照と再検証をまとめて反映してから着手する。
   直前の限定指示やblocked状態を、本書が自動的に変更したとは扱わない。
2. 実際の現行生成物から該当するGoalPhase projectionを使って参照を同期する。
   手作業で一律 `-8` を適用するのではなく、正規の生成結果を使う。
3. productionの契約、CML意味、生成Scalaや各失敗テストの本文をこの補正に混ぜない。
   テスト削除や期待値の緩和でPASSにしない。整形だけの追加編集もここでは止める。
4. 失敗した次の4 suiteを一まとまりで確認する。ここに新しい限定レビューの
   関門を追加せず、後述の統合レビューで補正と既存修正を扱う。

登録済みSBT runnerへ渡す論理コマンド（親から直接SBTを起動しない）:

```text
--batch
testOnly org.simplemodeling.textus.smworkflow.admission.GoalPhaseManagedSuspensionOperationSpec org.simplemodeling.textus.smworkflow.admission.GoalPhaseRuntimeCatalogSpec org.simplemodeling.textus.smworkflow.GoalPhaseCncfConsumerSpec org.simplemodeling.textus.smworkflow.admission.GoalPhaseRuntimeParametersSpec
```

行番号だけでruntime policy不一致になる比較の妥当性はPhase 5の不要チェック整理へ
引き継ぐ論点とする。本書はこの比較を永久に必要とは判定しない。一方、Phase 2の
完了作業中に新しい汎用比較機構の設計へ拡張せず、現在の契約下の参照同期を先に終える。

## 4. 3基本ルートの受入を一度にまとめる

コードを増やす前に、既存の実行結果をP2-M02/M03へ対応付ける。
各ルートについて、入口Operation、実効果、終端、別runtime/JVM再開、証拠の所在、
選択した利用で残る欠落を短い表にする。既存チェックリストの表を更新すればよく、
別の管理システムや証明台帳は作らない。

- SplitPhase: 実文書へのApplyと、選択された判断分岐・中断回復。
- RepositorySync: owned repository／local remoteでのGit効果、検証、収束と回復。
- GoalPhase: StepだけでなくPhase全体の終端、必要なSBT・コミットと回復。

既に検証した経路は作り直さない。必須の欠落が具体的に見つかった場合だけ、接続箇所と
Executable Specificationを同じbatchで補正する。代表経路に不要な新しい分岐・汎用化・
hygieneは後続へ記録する。履歴を残すことと、全履歴を毎回再審査することを混同しない。

## 5. 統合検証・独立レビュー・文書をまとめて閉じる

1. 関連修正と参照同期が揃ったcandidateで、必要なroot全体検証 `--batch test` を実行。
   producerが変わっていなければ、その検証やpublishを繰り返さない。
2. 実Operation／SBT連携の `runtimeScripted/scripted runtime/operation-sbt` は既存の
   成功証拠（1117等）と入力・対象経路の関連性を確認して再利用する。関連コードや
   依存が変わり証拠が適用できないときだけ再実行する。
3. 3ルートと現在の修正、実行結果、既知の制限をまとめた独立統合レビューを行う。
   先の限定PASSで代用せず、helperごとの新しい審査サイクルにも戻らない。
4. 修正後は影響範囲と必要な回帰を確認する。新しい根拠がなければ、同じ全体テスト・
   レビューを繰り返さない。今回も全体FAILなら、実際の原因と対象範囲をまとめて扱う。
5. P2-M05はOperationの使い方・入力／結果・限界・Phase 5/6/7/8への移管を整える。
   CAR/manual配布は現時点で未検証。今回の成果物として必要な配布確認だけを扱い、
   Phase 6のCLI／skill利用実証を新しい完了条件にしない。
6. P2-M02..M05を実際の証拠で判定し、未実装の後続所有を明記して通常のcommit／
   closingへ進む。広いGoalと今回のPhase完了を区別し、未達のGoalを完了扱いにしない。

SBTは登録済み `cncf_command_runner` と共有serial wrapperを使用し、最初から必要な
scoped escalationで実行する。各実行の終了とlock解放を待ち、並行SBT・消費済み依頼の
再利用をしない。これは既存実行条件の継承で、新しい検証ゲートではない。

## 6. 報告と作業の止めどころ

報告するのは「3ルートのどれが受入可能か」「残る具体的な機能欠落・失敗」
「次の統合検証／完了作業」。テスト件数や補正回数だけで進捗を示さない。
受入に不要な実装を増やさず、機械的同期のたびに新たな開発batchへ分断しない。
実際の権限・実行基盤の停止は、製品の未実装と分けて記録する。

本handoff作成時には、製品・参照JSONの変更、SBT、Goal再活性化、他チャットへの送信、
commit／pushは行っていない。既存の失敗・承認待ちを解消済みとは扱わない。
