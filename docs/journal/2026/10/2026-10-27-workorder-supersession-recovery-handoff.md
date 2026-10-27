# WorkOrderの置き換えと既存Session復旧の引継ぎ

Date: 2026-10-27 (Asia/Tokyo)
Status: サイド会話で整理した設計方針。実装・検証・公開・実Session復旧の完了記録ではない。

## 結論

不完全な発行済みWorkOrderは上書きせず、元の内容を履歴へ保持して
Supersededとし、補正済みの新WorkOrderへ置き換える。
外部には新しい依頼を「現在の依頼」として提示し、新旧を別IDで関連付ける。
WorkOrderのorderは順番ではなく、外部担当への作業指示・依頼を意味する。

CNCF側は、既存のDecision置き換え機構を基に、WorkOrderにも適用できる
汎用的な置き換えAPIを整える。独立した「退役」状態管理系や、任意の状態を
指定できる管理APIは作らない。具体的な公開名・型・ファイル分割は未確定であり、
既存APIの一般化または共通内部処理と種別別の入口で実現できる。

sm-workflow側には復旧Operationを設け、既存Sessionの通常Advanceへ戻す。
新規依頼の不備を防ぐ原因修正と、既存依頼の復旧は別々に実証・記録する。

## この方針に至った責務の整理

sm-workflowのSessionと、CNCFが保持する汎用Workflowインスタンスの
実行状態は区別する。

- sm-workflow: Session、Plan、Step、受理済み結果、修正回数、復旧理由、
  復旧可能性、承認の適用、補正内容と業務上の継続位置を決める。
- CNCF: 汎用Workflowの依頼・継続状態・結果の管理、永続化、
  revisionによる競合検出、必要な更新を確定するトランザクションを担う。

Sessionを別のWorkflowへ乗り換えたり作り直したりする提案ではない。
同じWorkflowインスタンスについて、旧依頼を待つ状態を安全に解消する。

## 読み取り確認した既存機構

今回のサイド会話では、goldenport-cncfチェックアウトの以下を確認した。
チェックアウト位置は環境依存であり、下記の相対パスをリポジトリ内で解決する。

- `src/main/scala/org/goldenport/cncf/workflow/WorkflowProtocolV1.scala`
  の`WorkflowContinuation.WorkOrder`が作業依頼を定義する。
  Decision、Wait、Terminalも同じWorkflowプロトコルの応答である。
- `src/main/scala/org/goldenport/cncf/workflow/WorkflowManagedDecisionSupersession.scala`
  は、現在の未回答Decisionを照合し、旧ContinuationをSupersededにする更新と
  Workflow履歴・停止状態の更新を同一のmanagedトランザクションで行う。
  元の質問と置き換え記録を保持するが、対象種別はDecisionに限定されている。
- `src/main/scala/org/goldenport/cncf/workflow/WorkflowComponentStorage.scala`
  は`bindDecisionSupersessionC`を公開している。
- `src/main/scala/org/goldenport/cncf/workflow/WorkflowMechanicalTransition.scala`
  の通常機械的遷移は、停止中のインスタンスを受け付けない。

したがって、必要性の説明は「無効化機能が全く存在しない」ではなく、
「既存の履歴付き置き換え機構をWorkOrderにも適用し、安全な通常継続へ接続する」
とする。Decision専用というチェックを外すだけでは、WorkOrderの実行競合を扱えない。

## 復旧の公開経路

```text
通常Advance → 発行済みWorkOrder → 結果受入れ → 次の処理
                    │
              不完全で実行不能
                    ↓
sm-workflow復旧Operation
  → 現在の対象・実行状況・既存承認を確認
  → CNCFの置き換え処理で旧依頼と継続状態を一体更新
                    ↓
通常Advance → 補正した新WorkOrderを発行 → 通常の実行・結果受入れ
```

復旧Operationの入力はSession、対象依頼、復旧理由、必要な補正情報を基本とする。
現在の状態や承認はruntimeが取得し、呼出し元による任意状態の指定を認めない。
対象revisionなどの競合防止情報を公開契約へ含めることは、この制約と両立する。

復旧の確定では、旧依頼のSuperseded化、理由と関連、継続位置、
補正済み依頼を生成する情報、復旧履歴を既存のmanaged状態管理で一体保存する。
新依頼を後続のAdvanceで発行する場合は、確定した再発行待ち状態から再開し、
途中のプロセス終了や呼出し再試行でも復旧記録や新依頼を重複生成しない。
後継IDは実際の発行時に確定・関連付けし、未発行の依頼を発行済みとして提示しない。

同じ対象の既完了の復旧呼出しは、保存済みの確定結果を回収する。
別の更新・claim・結果受入れと競合したら、最新状態を読み直して再判定する。
古い承認を変化した対象へ自動適用せず、不明なトランザクション結果も成功とみなさない。

復旧Operation自体はGitやSBTを起動しない。確定後の通常Advanceへ任せる。

## 実行状況ごとの扱い

| 対象の実行状況 | 必要な扱い |
| --- | --- |
| 未実行を確認できる | 旧依頼を保持して置き換え、補正後の通常発行へ進む。 |
| 完了済み | 実結果を回収し、通常の検証・受入れ条件に従う。再実行しない。 |
| 実行中 | 結果回収または利用可能な既存キャンセル経路へ接続する。無効化で停止したとは扱わない。 |
| 実行有無・結果が不明 | 不確定な事実を保持して照合する。置き換えだけで新試行を許可しない。必要な明示判断を求める。 |

Available、unclaimed、初期revision、実行記録が見つからないことだけでは、
外部で未実行だった証明にならない。依頼の無効化と外部処理の停止・未実行は別である。

旧依頼への遅延応答は新依頼の結果として受理しない。
実行証跡として保持し、必要な照合へ接続する。

## 今回の最小実装と検証

最小ケースは、未実行が確認できる不完全なWorkOrderを履歴付きで置き換え、
同じSessionの通常Advanceで正しい依頼を発行できること。
既存の停止Taskがその未実行条件を満たすかは、別途実証する必要がある。
満たさない場合は、不明な効果を保持した公開の判断経路を先に用意する。

検証には次を含める。

- 既存Decisionの置き換え動作が維持されること。
- 旧WorkOrderの内容・履歴を保持し、補正後の新依頼を別IDで関連付けること。
- 復旧確定と新依頼発行の間でプロセスが終了しても、同じSessionから再開できること。
- 復旧の再呼出し、競合claim、遅延結果、古いrevisionによる二重更新を防ぐこと。
- 完了済み・実行中・UNKNOWNを未実行として扱わないこと。
- Session、受理済み結果、成功済み検証、履歴、修正回数を保持すること。

同じComponent・目的・受入条件・操作の範囲の復旧は既存承認を適用する。
単なる依頼生成・登録の補正で修正回数を増やさず、成功済み処理を再実行しない。
一方、UNKNOWN後の新試行判断や未承認の依存Component変更まで許可されたとは解釈しない。
再Start、受理済み結果の再Submit、保存データの直接編集による復旧は行わない。

## メインチャットへの接続

開発はsm-workflow-dev側で進める。運用側sm-workflowチェックアウトで
未公開機能を開発する依頼へ差し戻さない。

[既存T4継続記録](2026-10-27-phase-6.7-t4-source-handoff.md)には、
既存Phase Session `f1269ff94453c37f17583da7d008164e`、
停止Task `4698ba5ab263a9b42ec517ae479820ff`と、
CNCF依存範囲に関するHuman561の発行記録がある。
これは参照した継続記録の情報であり、実操作前の最新状態は公開APIで確認する。

本書は既存の依存提案を、上記の既存Supersession拡張という設計に整合させるための
引継ぎである。Human561への回答、新しい試行の許可、実Session操作、
コード実装、検証、publishLocal、ランタイム切替をこの文書作成で実施したものではない。
具体的な依存範囲と現在の公開判断を、メインチャットの既存手続きへ接続する。
