# NICT開発スキル障害のPhase 6系列への反映とhandoff

Date: 2026-10-17 (Asia/Tokyo)
Status: 既存要件の明確化を計画へ反映。追加実装・検証結果ではない。
対象: sm-workflow Phase 6 / 6.1 / 6.2 / 6.3

## 開発チャットが採用する方針

進行中のPhase 6は継続する。NICT開発で実際に起きた手続き上の障害を、
既存の項目ID・担当分担・受入条件に対応付けた観察事例として取り込む。
新しいPhase完了条件や復旧機構を追加せず、適用可能な既存証拠を利用する。

現在発行済みの要求・進行中の作業を差し替えず、その結果を保持する。
次の通常の計画／dispatch更新時に開発チャットが既存条件との対応を確認して採用する。
過去のStepを再開したり、修正回数をリセットしたり、Phaseを最初から実行し直す
指示ではない。実装変更が必要になった部分は、その担当の既存作業内で範囲と影響を
確定する。共有コンポーネント変更の実際の権限境界も維持する。

## 元の事例と証拠の限界

- [NICT開発で実際に発生したスキル障害](https://github.com/asami/sm-workflow/blob/main/docs/journal/2026/10/2026-10-17-nict-development-skill-failures.md)
- [sm-workflowのソースベースの点検](https://github.com/asami/sm-workflow/blob/main/docs/journal/2026/10/2026-10-17-phase96-delivery-friction-sm-workflow-review.md)

NICT開発チャットが依存CNCF Phase 96を開発する過程で、古い文脈の優先、
runner ID転記ミス、復旧例外の誤解釈、inspectorパスの推測、verifier戻り値の
保持漏れが起きた。これらは製品テスト失敗とは別の実行手続きの障害である。
P96-08では製品側の検証・独立レビューを保持したままcommit前に停止し、
一つの停止が3親ターン続いた。その後同じ23パスのcommit完了が報告されている。
3回のGit実行失敗、繰り返されたPhase全体レビュー、製品受入証拠の破棄とは扱わない。

元journalは一部の旧child transcriptの欠落を明示している。今回の反映作業では
asamiairのprivate記録を再検証していない。事例はGitHub上のretrospectiveに基づく。
sm-workflowで同じ障害が再現したと主張するものでもない。
元journalはローカルcheckoutにないためGitHubの参照を保持した。dirty treeへの
pull/mergeやprivate実行証拠のGitへのコピーは行っていない。

## 一つずつの担当と確認観点

| 既存担当 | 既存範囲への対応 | 今回追加しないもの |
| --- | --- | --- |
| [Phase 6](../../../phase/phase-6.md) | session/common loop・実skill/result。現在の要求・ID・戻り値をruntime/hostが直接扱い、受理後の準備済み進行に人の「続けて」や進行だけのAIターンを要しない | 新しいremote client全体試験、後続Phaseの前倒し完了条件 |
| [Phase 6.1](../../../phase/phase-6.1.md) | P6-EX01..03、P6-HR01/04。コマンド選択・起動・結果保持と、既知の未実行／結果不明／実テスト失敗／実権限拒否の区別 | 汎用retry、復旧engine、網羅的な中断試験 |
| [Phase 6.2](../../../phase/phase-6.2.md) | P6-TS01..06と既存recording。変更影響に見合う検証・review、適用可能な証拠保持、一括記録 | per-check再利用engine、helperごとのreview、記録の再帰review |
| [Phase 6.3](../../../phase/phase-6.3.md) | P6-SQ02..05と統合受入。既存PHASE証拠・修正差分でFINALを判断 | 第二の全面探索review、各前提Phaseの一律再実行 |

既存の主経路を先に通す。Phase 6の現運用はcommand中心であり、remote clientは
採用済みの軽い接続確認のままとする。Human、Wait、拒否、エラー、Terminalは
それぞれの意味を守る。「自動で進む」は無条件Advanceやblind retryの意味ではない。
決定済みの機械的処理をAIに再転記させない一方、意味判断と独立レビューは残す。

## 進行中作業・既存証拠の扱い

編集直前に確認した最新の保存状態は`IMPLEMENT359-state-end.json`で、
隔離された実Design provider -> recordExecution -> typed Submit受理 -> 通常Advance
によるPREVIEW完了を記録していた。次はその結果を保持して残りの元来の範囲を
選ぶPLANとされている。これは確認時点の記録であり、今後の状態を固定しない。
また、全GoalPhase完了や全client loopの受入を意味しない。

Phase 6の既存文書には、検証成功から同じ公開Advance内でReviewStepを発行した
記録もある。これらを「全て未実装」としてやり直さない。既存証拠を要件に対応付け、
不足する実運用上の接続だけを現在のscenario内で確認する。

実行前にintentが保存されている場合、外部コマンド未実行でも「何も変わっていない」
とは限らない。未知の外部効果を未実行扱いにして再実行もしない。
Phase 6.1で結果型／伝播を変える必要がある場合は、その変更だけを見積り・確認する。
今回の文書更新は新しい結果型を定義せず、進行中のPhase 6実装にも変更を加えない。

手続きだけの変更は製品側の証拠を一律に無効化しない。一方、製品・入力・要件が
変われば、既存契約に沿って影響範囲を検証する。同一candidateの結果保持を、
変更後の個別check再利用が実装済みである証拠とみなさない。

[段階別quality review方針](2026-10-13-staged-quality-review-matrix.md)の
STEP / PHASE / FINAL分担とPhase 6.3のaggregate full validation責任は維持する。
新しいhash管理、整合性台帳、状態ごとの文書更新、追加承認・release gateは設けない。

## 今回の変更と残作業

Phase 6/6.1/6.2/6.3に、同じ日付のNICT観察節を追記した。
既存本文、項目ID、status、checkbox、分担・集約検証宣言は保持した。
`.codex-workflow`のlive plan、発行要求、state、Goal、証拠、counterには書き込んでいない。
製品コード・共有スキル・test fixtureの変更、SBT実行、commit、pushは行っていない。
開発チャットへのメッセージ送信も行っていない。

開発チャットの次の作業は、通常の計画更新時にこの対応表を既存の残作業へ結び付け、
適用済みの証拠と不足部分を判断すること。文書追記だけで新しい動作や受入を
完了扱いにせず、必要になった差分だけを元の担当で処理する。
