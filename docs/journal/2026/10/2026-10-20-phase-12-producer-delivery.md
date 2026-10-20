# Phase 12 minimum producer delivery and Phase 6.4 adoption

date=2026-10-20
status=minimum-route-deployed-consumer-plan-adopted-paused-before-E2-local-closure-pending
producer=sm-workflow開発
consumer=sm-workflow-dev開発 / sm-workflow-dev Phase 6.4 既存Session継続

## Available boundary

通常installed `sm-workflow`から、Advance内の実Test完了 → UTF-8 stdout保存 →
共通Journal記録 → 別プロセスのJournal/Evidence ID読取が利用可能になった。
同じSessionの未受入れ残作業だけを新Planへ採用する公開操作も配備した。
producerは6.4の実Session、E1受入れ、base、pending Human、履歴、修正回数を操作していない。
その後consumer所有チャットで実Plan採用が成功し、E2発行前にユーザー指示で停止した。
末尾の追記を現在のconsumer境界とする。

これは先行producerの実装・配備引渡しであり、6.4 E2〜E4受入れでも、Phase 12の
最終ローカルclosureでもない。今回のソースはsm-workflow main、CNCF mainと
development-workstationの未コミット差分に保持されている。既存コミットを今回の
実装コミットとは表示しない。別作業の変更も保持し、今回のGitHub同期は行っていない。

| Owner | Actual interface |
| --- | --- |
| Common Evidence | `EvidenceService.readEvidence`、`recordEvidenceJournal`。入力はEvidence `identity`と任意`revision`。 |
| Automatic producer | native validation batchの最初の完了済みTest/TestOnly stdout一件。Compile・実行中・他Actionは対象外。 |
| Journal | 正規`EvidenceStorageObservation` variant。Findingのeligibilityは変更しない。 |
| Remaining Plan | `GoalPhaseService.describeGoalPhaseRemainingPlan`、`adoptGoalPhaseRemainingPlan`。 |
| Independent host | `NativeHostPreparationService.prepareEvidence`の新kind `remaining-plan-adoption`。受動的な通常StartupImport入力生成。 |

`validation.evidence_recording`は保存とJournalのstatus/参照を別々に返す。
readEvidenceは現在の取得可否と過去の保存観測を分ける。保存writerはFile UoWの
既知の書込結果を使い、同じIDを読み戻して成功判定する処理は持たない。
型付きSBT結果がUnverifiedならexit 0でもpassedにはしない。

## Normal local deployment

- CNCF: `org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT`を通常`publishLocal`でIvy localへ更新。
- CAR: `org.simplemodeling.textus:textus-sm-workflow:0.1.0-SNAPSHOT`を通常`publishLocal`で
  `~/.cncf/local/repository/car/org/simplemodeling/textus/textus-sm-workflow/0.1.0-SNAPSHOT/`へ更新。
- 起動: `/Users/asami/Library/Application Support/Coursier/bin/sm-workflow --host <existing-host> command ...`。
  起動元はローカルCARとIvy runtimeであり、source classpath・独自mainを使わない。
- これは同一座標のローカルSNAPSHOT更新。別機へ配備済みとは扱わない。
- 実SBTはCNCF UoW内。共有全体lockやscripted前提を追加していない。

両公開結果の型付きlocal-publication検査は受理済み。公開後の通常installed CLIでも
最小保存経路と残Plan採用経路を検証済み。詳細の実行ログ・結果・reviewはGitへ複製せず、
`macmini:~/backup/journal/2026/10/sm-workflow/phase-12/phase12-common-evidence-20261020/`に保持する。
Phase 12がactiveの間は期限なし。完了後30日を基準とし、未解決調査はhold対象とする。

## Evidence host setup

既存hostのComponentParamと管理I/Oを使う。`sm-workflow.evidence`は次の全フィールドが必要。
collectionはWorkflow・host authority・Journalとは別の、既存EntityCollectionId ownerが
解決した正規IDを使う。以下は実検証hostの値で、consumerの既存config全体を上書きしない。

```json
{
  "schemaVersion": "sm-workflow.evidence.v1",
  "storeName": "workflow",
  "collection": "smworkflow-cli-entity_collection-ec1_10_smworkflow_3_cli_15_common_evidence-0-stable",
  "hostIdentity": "macmini-phase12-installed-specification",
  "workspaceIdentity": "evidence-archive",
  "directory": "logs"
}
```

既存process-execution planへ論理workspace `evidence-archive`と、そのcomponentに対する
必要なFile管理grantを追加し、Evidence collectionを既存store/datastore planへ宣言する。
共通Journalの`sm-workflow.journal`も通常の別collection/store/grantで設定する。
プロパティはComponentParam/ExecutionContextから解決する。操作パラメタでconfigを解析しない。

物理rootはhostが選ぶ`~/backup/journal/YYYY/MM/<project>/<phase-or-task>/<run>/`。
`logs`親ディレクトリはhost設営で事前作成する。アプリは管理File DSLだけで書き、
NIO・shell・汎用Processによるmkdir/fallbackを追加しない。実検証rootは
`macmini:~/backup/journal/2026/10/sm-workflow/phase-12/installed-episode-16715556281649887971/`。
このrootやfixtureのSession/権限をconsumerへコピーしない。

通常Advanceで返ったJournal Entry IDを`JournalService.readJournalEntry`へ渡し、
EntryのEvidence IDを`EvidenceService.readEvidence --input '{"identity":"<returned-id>"}' --json`
へ渡す。Journalだけ未記録なら同じEvidence IDで`recordEvidenceJournal`を使う。
これは保存済み観測だけの継続であり、元Test・archive・成功済みSubmitを再実行しない。
保存結果が不明なら不明のまま保持し、再保存を成功させるためのblind retryはしない。

## Existing Phase 6.4 adoption procedure

詳細契約は[remaining Plan adoption](../../../spec/goal-phase-remaining-plan-adoption.md)。
操作主体は既存Session所有チャット一つに限定する。新Start・DB直接編集・旧Human回答は不要。

1. 既存Sessionと実際の現在frontierを確認し、同じPlan identity/Phase/repository、全Step ID・
   順序・owned paths・依存・分類・修正枠を保つraw proposalを作る。受理済みE1は完全保持し、
   未受入れE2〜E4のpurpose/checksと実File-owned受入条件だけを新方針へ更新する。
2. `describeGoalPhaseRemainingPlan --input '{"sessionId":"<existing>","proposal":<raw-proposal>}' --json`
   で、実際のpending question/revision、旧Plan/base/accepted prefix/countsと新契約を取得する。
   proposalは必須。返ったtargetを再構成せずそのまま採用入力へ入れる。
3. `GoalPhaseRemainingPlanAdoptionInput`に同じSession/target/proposal、具体的rationaleと
   実際の新Human指示referenceを入れる。今回の設計判断はarchitectureチャット
   `01a0fa78-944b-7ed2-b04e-9deafbeca751`の実Human item
   `01a158a5-e6f7-7022-8568-162b1eea9e96`で再確認する。同じ設計承認を求め直さない。
4. 既存の独立hostが新directiveを専用purposeでadmitする。通常`prepareEvidence`入力は
   family=`GoalPhase`、kind=`remaining-plan-adoption`、実description/採用入力のfile URL、
   reference/executionEvidenceのfile URL、participant、issuer、occurredAt、既存正規collection。
   execution.workerIdentityはparticipantと一致させる。準備Operationは権限発行者ではない。
   実行ホストが実指示を検証してから、返った入力を通常StartupImport経路へ渡す。
5. `adoptGoalPhaseRemainingPlan --input <actual-adoption-JSON> --json`を一回実行。
   既知の`accepted=true / remaining-plan-adopted`の後だけ通常Advanceを続ける。
   不明なら観測して止め、同じ効果をprobeとして再実行しない。
6. 旧Humanは未回答の記録とsupersession dispositionを保持し、次の未受入れStepだけを
   Implementationで発行する。E1・旧検証結果・base・修正回数を保持する。新契約での
   検証・独立レビュー・Admission・実Step commitは今後の仕事として残る。

隔離installed検証はSPECIFICATION-OWNEDの新directiveであり、live 6.4のHuman証跡ではない。
既存の旧依存案を承認した扱いにはせず、新directiveが残作業を明示的に置き換える。
受理済みE1へ遡及適用しない。actual consumer採用の成否はconsumer所有者が記録する。

## Remaining work and retained outcomes

最小producer/管理I/O/Journal/独立host採用接続のfocused検証と独立レビューは実施済み。
同じ実結果の参照再利用、型付きnegative、既知保存失敗・不明、Journalのみの継続を扱う。
詳細の件数、途中の失敗、修正・再検証、review findings/dispositionは上記ローカル記録に保存する。
既存の命名Hygieneは別課題として保持し、Phase受入れへ紛れ込ませない。

未実装は広範なupload/find/manage、他Action/画像/巨大データ、batch・圧縮・hold/due・
保持期限の実行管理。保持基準だけをメタデータに保持する。6.4 E3/E4が同じ共通サービスを
拡張し、二重writerを作らない。CNCF directory/compression追加が必要なら具体的に別途判断する。

Phase 12の残る全体レビュー・通常ローカルclosure/commitと、6.4側の実開発E2受入れは
未完了。最小producerは利用できるため、それらのPhase全体完了を互いに待つ循環は作らない。

## Consumer update: adopted, stopped before E2

2026-10-20、既存consumer継続チャット
`01a156b7-d60e-7cb3-a0bb-c2ce483b22f9`で実architectureユーザー指示の確認、describe、
独立host証拠準備、通常StartupImport、採用を実施し、`accepted=true /
remaining-plan-adopted`が返った。nativeチャット記録とconsumerからの引渡し報告を確認した。
同じSession `96c294d73be3d48067f3721d8a2abf33`でPlan 1→2となり、旧Human
`ordered-human:26`は回答せずsuperseded-by-adoptionとして保持する。
E1・base・履歴・修正回数は保持し、旧API承認・再Startは行っていない。

採用後のAdvanceは`--session-id`形式で入力し、`Workflow requires parameter sessionId`
でexit 1、responseは空だった。E2発行・consumer接続成功・E2/E3/E4受入れは未確認。
既知成功した採用を再実行する必要はない。保持された失敗証跡はconsumer checkoutの
`.codex-workflow/disposable/a-17d12164-3ee4-4412-935d-bf96d21bdeb3/stderr.log`にある。

その後、実ユーザーitem `01a158ed-ebb7-7561-8b6f-5c29f281735f`の「こっちは止めて」により
consumer継続チャットは停止した。producerもこの停止をnative記録で確認した。
consumer Session操作・再開依頼は行わず、その所有者の新しい再開指示まで保持する。
これはconsumer側の停止であり、producerのPhase 12作業停止とは解釈しない。
共通Evidenceの先行配備・実Plan採用成功と、未実施の6.4実開発受入れを区別する。
