# 共通Journalの先行Phaseと試用スキルの引継ぎ

date=2026-10-20
status=completed-and-trial-connected

## 採用した順序

ユーザーは共通OperationによるJournalの仕様検討に続いて開発フェーズ定義と
design／notes／journalへの記録を依頼し、さらに「6.4の前にゲリラ的に開発」、
「6.4はsm-workflow試用なのでスキルも対応」と指定した。
6.4完了後に着手する当初案は採用しない。

[Phase 11](../../../phase/phase-11.md)を短い先行開発とし、共通Entryの実記録・読取、
受理済みFindingの自動記録、有限検索、同梱／配備スキル、6.4試用hostへの実接続を所有する。
[Phase 11.1](../../../phase/phase-11.1.md)で判断・課題・引継ぎと訂正／batchを拡張する。
11.1、7〜10、6.4のarchive完成は11の前提ではない。6.4のarchive／保持管理は残す。

## 開発チャット向け指示

1. 進行中の6.4とhostの実態を確認し、既存Goal/base/Plan、E1受入れ、pending Human、
   発行済み要求、履歴、修正回数、実装・検証をそのまま保持する。旧journalのfrontierを
   現在地と決めつけず、計画追加をその質問への回答・reset・新Session開始と扱わない。
2. Phase 11は既存Operation／review結果から一件を記録・別プロセスで読める主経路を先に通す。
   DataStoreの実能力を確認し、CML／正規UoWを使う。独自store／SQL／ファイル台帳を追加しない。
3. Findingは受理済み結果と元IDを利用し、既存Step／Phase eligibilityを保持する。
   journal転記だけのAI要求を新しい適用境界で通常記録へ移す。発行済み要求を途中変更しない。
4. スキルはEntry参照・記録状態・短い表示を使い、必要時に公開read／findを呼ぶ。
   同梱sm-goal-phase／sm-repository-sync／sm-split-phaseの説明と実配備元を整合させ、
   6.4試用hostで実際に通ることを確認する。全スキルへの無条件の記録義務は追加しない。
5. 6.4へAPI／型／配備経路／実結果／採用境界を引き渡す。6.4のJournal案はこの共通成果を
   再利用し、別writer／storeを作らない。archive結果やEvidence参照は6.4で接続する。
6. 業務成功と記録失敗を分離し、記録のためにテスト・commit・archiveを再実行しない。
   既存結果と履歴を再利用し、追加検証は変更した実経路と不足ケースに限る。
   現在のSBT方針は登録済みrunnerとsbt-native lock。別の機械全体lockを追加しない。

## 仕様資料と保持範囲

[設計](../../../design/development-journal.md)は共通型・正本・効果／記録の分離を定義し、
[notes](../../../notes/development-journal-delivery.md)は順序と6.4への責任分担を記録する。
[契約案](../../../spec/development-journal-proposal.md)と
[用途精査](../../../spec/development-journal-skill-usage-inventory.md)を出発点として使う。
既存Git文書の一括移行・削除、raw証跡のGit格納、SIGTERMの変更、SIGINT制御返却は含めない。
Phase 6.3と6.4の計画本文／チェック済み状態、native実行記録は今回編集していない。
以下の採用記録より前は計画のみであり、この節の作成時点では公開APIの実装・配備・試用成功は未実施だった。

## Phase 11から6.4への採用情報

J1とJ2は通常検証・独立Stepレビュー・ローカルコミットで受入れた。
J3では通常の`publishLocal`でCARを更新し、既存の`skillBundle`から3スキルと
7補助ファイルを試用hostの`target/sm-workflow/tasks/phase-11-journal-adoption/installed-skills`
へ展開した。共有の発見用3スキルはdevelopment-workstationの正本をsymlinkで参照する。
agent定義の追加配布は本更新に含まれない。必須の独立Phase全体reviewは命名指摘1件の機械的改名で閉じた。初回全体testの12件失敗と修正後のTestコンパイル失敗は履歴として保持し、既存契約に沿う接続修正と集合型変換の訂正の2回を145件の重点検証・独立focused reviewで確認した。通常全体testの再検証は159スイート・1504件成功、失敗0・aborted0で終了した。既存の明示opt-inが必要なSIGTERM fixture準備1件はcanceledで、実行済みとは扱わない。通常publishLocalとinstalled CLIの公開find/readも確認した。最終releaseコミットに本引渡しと完了したPhase／checklistを含める。既存の命名負債3件は正本Hygieneに残す。

利用契約は[実装仕様](../../../spec/development-journal-runtime.md)を参照する。

| 対象 | 通常経路と適用範囲 |
| --- | --- |
| 既存の受理済みfile recording | `GoalPhaseService.submitGoalPhaseFindingRecording`が実結果からExecutionObservationを記録し、業務受理と別の`journalRecording`を返す。既発行要求のファイル編集・返却契約は変更しない。 |
| 新しい通常Finding | 既存のStep commit／Phase review eligibilityを保ち、managed intent/resultから自動記録する。`findingRecording.journaled`の実参照を利用し、転記だけのAI担当を追加しない。 |
| 必要な検索 | `org.simplemodeling.textus.SmWorkflow.JournalService.findJournalEntries`へ`--input` JSONを渡す。`repositoryIdentity`必須、Phase／Session／kind／完全なsource tuple／期間を絞り、limitは1〜20。 |
| 選択詳細 | `org.simplemodeling.textus.SmWorkflow.JournalService.readJournalEntry`へ実際に返った`identity`と`revision`を渡し、型付き詳細と表示viewを使う。 |

試用host `sm-workflow-dev`には既存Workflowとは別の`journal`目的を通常の
Component DataStore設定・record grantで追加した。既存Workflowの保存先、定義、
選択、Sessionは変更していない。構成値・canonical collectionは実装仕様に記載する。
通常installed CLIで、実specが保持した隔離Sessionの未消費回答を1回Submitし、
受理・Journal記録後、試用hostの公開findとreadから同じEntryのsource／対象／本文を取得した。
この接続確認はspecification-ownedの実処理であり、外部reviewerやproviderの証跡ではない。
SQLのseed／複製や、書込み後読戻しを必須とするappendロジックは追加していない。
詳細なローカル実行証跡はGitへ複製せず、所有Phaseのprivate stateで保持する。

### 保持した6.4と次の判断

Session `96c294d73be3d48067f3721d8a2abf33`、E1受入れcommit
`07a97986cbee9ffe71fdf69b89a60eff98768134`、Plan／base／Goal／履歴／修正回数と
pending Human `ordered-human:26`は保持した。本採用で6.4のStart／Submit／Advanceや
Human回答は実行していない。

人間の直接指示に従い、`P64-E2-public-file-location@1`の承認要求を先行させない。
Journal自体の保存・検索・読取は公開Entry IDと設定済みDataStoreで完結するため、
そのためだけの物理ファイル保存先APIは不要である。一方、6.4のarchive結果として
実際のabsolute保存先を表示する要件は別に残る。現在のFile結果の論理target／pathだけで
その要件を満たすとは扱わない。6.4所有者が適用後の実契約を再評価し、不要な部分は
通常の計画変更で取り下げ、必要なproducer APIだけ具体的理由を示して扱う。
既存Humanへの承認・拒否・取り下げはここでは捏造しない。

6.4のEvidence保存・圧縮・保持管理と11.1のDecision／Issue／Handoff管理は未実装の
後続範囲として残す。RepositorySync／SplitPhaseに新しい自動producerを追加したとは
表示しない。AIコスト削減量は未測定である。

## 後続採用: 共通Evidence Phase 12 — 2026-10-20

ユーザーは[Phase 12](../../../phase/phase-12.md)をsm-workflow開発で先行実装し、
6.4が採用・運用検証する方針を採用した。既知のAction生成物をAdvanceから自動保存し、
その実観測とEvidence参照を既存Journalへ記録する。Evidence用の正規producerは12で追加する。
上記の「absolute保存先を表示する要件は別に残る」は当時の整理であり、新しい契約では
必須条件から外す。Journal/Evidence IDで読取できるため、追加の絶対表示パスAPIと
AIによるMarkdown転記は不要。実ファイル保存・命名・取得は必要なままである。
11の受入れ・検証履歴と6.4のnative履歴を変更せず、通常の採用境界で接続する。
詳しくは[両開発チャット向けhandoff](2026-10-20-phase-12-common-evidence-handoff.md)を参照。
