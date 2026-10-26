# 外部規則によるソース履歴comment driverの最小実装案

## Recovery correction — 2026-10-26

[SHR-01..06](../spec/goal-phase-source-history-recovery.md#same-authority-comment-recovery--adopted-2026-10-26)
extends the existing pure comment driver and managed File/UoW path. Keep the
retained effective rule/date and make normalization idempotent. Native candidate
continuation reads the affected current comment, records its postcondition or
performs bounded normalization, and appends attributable reconciliation while the
old UNKNOWN receipt remains unchanged. Do not add a generic effect-replay engine,
lock/hash protocol or AI recovery controller. Extend existing public continuation
for pending legacy Human questions; test it with the normal installed caller.
The historical implementation/qualification below remains evidence for its original
scope. It does not prove this new recovery behavior.


status=selected-app-only-contract; C1-C2-focused-3-suites-54-tests-qualified-not-accepted; C3-native-cli-2-suites-6-tests-qualified; C4-genuine-edit-connected-review-admission-commit-pending
date=2026-10-17
owner=sm-workflow Phase 6.1 / P61-COMMAND
requirements=P6-HR01, P6-HR04

この文書は選択した設計とC1／C2／C3／C4の限定接続境界を記す。C1／C2は親で実際の3スイート54テスト成功とlock解放を確認済み。
C3の通常CLIは5履歴シナリオと既存repair testの2スイート6テストが成功し、production／test compileと両exit 0／lock解放を確認した。
C4はgenuine selected medium ImplementStepから通常history整備・typed stage／Test・独立Review待ちまで接続した。
独立レビュー／CAM Evidence・Admission／ローカルcommitはP61-ADMISSIONへ引き継ぐ未完了作業であり、providerの配備や受入完了を表さない。
既存Goal、PLAN epoch、履歴、修正回数と、既定のStep／Phaseレビューを保持する。

## 責任分担

今回の修正対象はsm-workflow内とする。外部ルールproviderを参照するという仕様から、
goldenport-coreやCNCFの変更が必要とは導けない。先の外部ライブラリ追加案は撤回した。
sm-workflowが呼出しごとの解決済み設定から外部規則データを取得・型検証し、実際に変更された
Step所有パスへの適用を通常の候補検査・検証・レビュー・commitへつなぐ。
共通configの取得、設定階層、コマンド入力との優先順位はCNCFが所有する。
対象ソースのworkspace認可とファイル操作は既存のCNCF File内部DSL／UoW経路を使う。

```text
sm-workflowの通常Advance（C3通常CLI実検証済み、C4 genuine edit接続済み）
  → 呼出しのExecutionContext.resolvedParametersから正式キーを取得
  → none / goldenport / 設定エラーの単純な分岐
  → goldenportの外部規則を型検証し、専用comment driverで純粋に解釈
  → candidateのmechanical intentを保存
  → 既存のCNCF File内部DSL／現在のUoWで対象ソースだけを読み書き
  → actual resultを保存してから通常のGit stage／SBT検証／独立レビュー要求へ
  → 実際の独立レビュー結果／Admission／ローカルcommit（後続で未完了）
```

外部規則の提供形式は、以下の解決済みconfiguration内の宣言的データとして選択した。
上の流れのうち、C2は`WorkflowSourceHistory.applyC`から現在のFile UoWまでを実装した。
正式キーを読んだ後の単純な分岐から専用`GoldenportSourceHistoryDriver`を呼び出す。
C3は通常Advanceの候補処理・mechanical記録・宣言済みCLI propertyまでを接続した。
汎用provider registry、plugin discovery、新しいprovider processは追加しない。
共有Markdown規則があることだけを実行可能providerの提供済み証拠にはしない。
規則をアプリに固定実装することで外部規則の要件を置き換えない。
独自main、shell、汎用Processへのfallback、新しいProcessExecutionRequest用途を追加しない。

## 最小の変更範囲

| リポジトリ | 変更する責任 |
| --- | --- |
| sm-workflow / main | 外部規則との接続、通常候補検査での履歴整備とintent／結果の保持、設定文書、対象仕様と通常コマンド検証 |

goldenport-core、CNCF、Cozy、CBD Support、共有スキルの変更はこの案に含めない。
外部ライブラリ追加の承認待ちは撤回する。新規リポジトリや新規artifact、一般的な
formatter基盤の整備も含めない。
実装時の正確なファイル一覧は、既存契約との接続を確認したImplementation Manifestで固定する。

## 設定と型付き境界

- `sm-workflow.source-history.style`は正確な文字列`none`（省略時の既定）と`goldenport`だけを
  受け付ける。`sourceHistory.style`は以前の説明上の省略名であり、aliasにはしない。`asami`も追加しない。
- `sm-workflow.source-history.rule`は外部規則データを保持する。同じ名前空間で今回選択した追加キーである。
- `none`ではruleキーの取得もdecodeも行わず、規則が不在・不正でも無視する。
  履歴用の対象ソース読取、更新、検査、警告を行わない。通常の意味的コメントの責任は残る。
- `goldenport`ではruleが必須。`ConfigurationValue.ObjectValue`の構造化データ、または
  JSON objectを含む`StringValue`を受け付ける。null、他のscalar、list、壊れたJSON、非対応規則は
  `Consequence.configurationInvalid`とし、暗黙の規則へのfallbackを設けない。
- `WorkflowSourceHistoryParameters.resolveC(context)`は非nullの実`ExecutionContext`から
  PUBLICな`context.resolvedParameters`を毎回参照する。`resolveC(parameters)`は同じ型検証境界を提供する。
  起動時設定のcache、component初期化時snapshot、直接のRuntimeContext変更、reflectionは使わない。
- 共通configとコマンド入力は同じ正式キーを使う。設定ファイルの探索・読込と優先順位は
  CNCFの`ResolvedParameters`が所有し、アプリは入力元を区別せず解決済みの値を消費する。
  operationによる上書きの型合成仕様はC1、コマンド宣言と通常CLI実行仕様はC3で扱う。
  未宣言の任意オプションが使えると仮定しない。
- 宣言済みcomponent初期化snapshotやprotected宣言キーを要求時の値で置き換えない。
  この呼出しごとのsource-history設定はhuman journalで選択したpublicなprojectionを消費する。
  通常の業務operation parameter処理を変更せず、command文字列の解析や独自resolverも追加しない。
- `WorkflowSourceHistoryRule`の純粋な呼出しは変更パス、原文、根拠のある日付・既知の出自情報を受け、
  更新文または対象外の結果を返す。専用driverはその結果を既存File DSLへ接続する。
- dispatcherの型付き結果は無効／対象外／不在／変更不要／更新とprovider identity、日付の根拠・不確実性を
  区別する。実行したという事実をStep受入やcommit権限へ変換しない。

## 最初に提供する規則

共有の`ai/directive/samples/version-update-instruction.md`を規則の出典とする。
規則の提供形式は`sm-workflow.source-history-rule.v1`の閉じたobjectデータとする。
以下は必要な振る舞いであり、提供済みproviderや通常経路への接続の存在を表さない。
sm-workflowから参照文書をAIに読ませて履歴整備を依頼する運用にはしない。

### 選択したC1の宣言的契約

`WorkflowSourceHistoryRule`は外部から渡されたJSONと原文を扱うcomment driver internalsの純粋なinterpreterとする。
ファイル、設定、時計、network、process、service discoveryにはアクセスしない。
`selectionC(style, rule)`は`Selection(style, Option[Rule])`を返す。
`none`は不要な規則を検査せず`Selection("none", None)`を返し、`goldenport`は対応する有効な規則を要求する。
独立した`Reference(workspace, path)`や規則ファイルloaderは設けない。

規則文書のフィールドは次に限定する。欠落、未知、重複、型不正、曖昧な値は
`Consequence.configurationInvalid`とし、暗黙の規則へのfallbackを設けない。

| フィールド | 契約 |
| --- | --- |
| schemaVersion / identity | `sm-workflow.source-history-rule.v1`／空白だけでない安定した完全一致identity |
| style / format | `goldenport`／`scala-block-history-v1` |
| sourceRoots / extensions / excludedSegments | 相対source root配列／`["scala"]`／対象外パスsegment配列 |
| tags | `since`, `version`, `history`, `author`の安全で一意な単一行tag |
| datePattern / locale | 完全な日付を往復できるJava DateTimeFormatter pattern／BCP47 locale |
| rollover | `month`, `day`, `never`のいずれか |
| removeHistoryAtSince / removeHistoryInCurrentPeriod | since同日履歴／現在期間履歴の除去を指定するboolean |
| header | Scala block commentの`open`, `linePrefix`, `close` |

実際の規約値は[common configuration fragment](../../examples/source-history/goldenport-config.json)に置く。
正式なstyleキーとruleキーを含む通常の共通config入力の例であり、hostがCNCFへ供給できる。
sm-workflowがこの例やJSON resource pathを別途開くこと、自動読込、既定fallbackはない。
production configは変更しない。外部規則とは外部から供給される規約データであり、外部ライブラリの追加や
配備済みproviderの存在を意味しない。
source root、除外segment、tag、日付表記、locale、期間、除去flag、header layoutは
interpreter内のgoldenport専用定数として固定しない。任意の正規表現や式、shell、codeは受け付けない。

`decodeC(raw)`は型付き`Rule`を返す。`applyC(rule, Input)`の入力は`path`, `text`,
`updatedOn`, `createdOn`, `author`, `dateUncertain`であり、日付は呼出し元から渡す。
結果は`providerIdentity`, `outcome`（`updated`, `unchanged`, `excluded`）, `text`,
`dateUncertain`を持つ。失敗時は更新文を返さない。
新しいheaderまたは既存managed blockでsinceが欠ける場合、根拠のある作成日を使い、
不明なら供給された更新日を使って不確実性を明示する。sinceは履歴・最新日の前に置く。
著者は供給された場合だけ追加し、既存のsince、著者、由来、履歴の相対順序と非commentソースを保持する。

C1は呼出しごとの解決済み設定の型検証、純粋な選択・decode・適用と実行仕様までを対象とする。
C2は専用comment driverを既存File DSL／実UoWへ接続する。C3はその呼出しを通常候補／CLIへ接続する。
configurationをアプリ独自のFile DSL loaderで再構成しない。
C1の仕様だけではP6-HR01の通常コマンド実行やP6-HR04の受入を成立させない。

### 実装したC2のdispatcher／driver境界

`WorkflowSourceHistory.applyC(core, target, source)`は非nullの実所有call／contextを要求し、
呼出しごとに`WorkflowSourceHistoryParameters.resolveC(core.executionContext)`を呼ぶ。
`selection.style`の単純なScala matchで`none`は`disabled`、providerなし、不確実性falseを返す。
この分岐はsourceやtargetに触れず、履歴用のFile操作・検査・警告も行わない。
`goldenport`は選択済み規則を専用driverへ渡し、想定外の選択は設定エラーとする。
registry、起動時cache、別process、fallbackは設けない。

`Source(path, updatedOn, createdOn, author, dateUncertain)`は明示的な出自情報である。
driverが時計やconfigファイルから日付・著者を補わない。`Result(style, outcome,
providerIdentity, dateUncertain)`のoutcomeは`disabled`, `excluded`, `missing`, `unchanged`, `updated`。
`none`以外は選択した外部規則のidentityを保持し、解釈した結果の不確実性を引き継ぐ。

`GoldenportSourceHistoryDriver.applyC`は実所有core、論理`FileTarget`、外部`Rule`と`Source`を受ける。
最初に純粋な`WorkflowSourceHistoryRule.includesC(rule, path)`で既存の規則・正規化path検証と
対象選択を行う。対象外は`excluded`を返し、sourceの字句解析やFile読取を行わない。
対象なら`WorkspaceFilePath`へ変換し、`WorkflowFileExecution.readTextC`で完全な原文を取得する。
CNCFがworkspace、権限、root、encoding、取得上限を所有する。読取拒否や上限超過はそのまま失敗を返す。

原文が`None`なら`missing`を返し、削除済みソースを再作成しない。`Some(text)`は既存の純粋な
`Rule.applyC`へ渡す。`unchanged`なら書かず、ReadOnlyでも変更不要の結果を返せる。
`updated`なら実読取原文をexpectedとする`FileWriteMode.UpdateExisting(text)`で一度だけ書く。
実際の`Updated`だけを更新成功とし、`Conflict`は明示的な`stateConflict`で停止する。
想定外の結果もエラーとし、producerの失敗を成功に変換したり、再試行や例外の隠蔽をしたりしない。
productionには直接の`java.nio`入出力、hash、新しいprocess、規則loader、workspace取得実装を追加しない。

`WorkflowSourceHistoryDriverSpec`は既存`WorkflowFileRuntimeFixture.withCore`の共通設定、
`sm-workflow-repository`の実grantと通常Operation UoWを使う実行仕様である。
外部の独自tag／ISO日付、明示日付、Unicodeソースと独立したfixture側のbyte読戻しを使う。
この仕様を含むC1／C2の親実検証は3スイート54テスト成功である。通常コマンド受入の記録ではない。

- 対象は変更されたScala製品ソースと実行仕様。削除済み、sample、生成物、vendor、
  build出力は除外する。最初から全リポジトリを走査する処理は追加しない。
- ファイル全体を字句として扱い、文字列リテラルをコメントと誤認せず、package／importの
  後にある長い履歴コメントも扱う。判定不能・不正な日付はツールエラーとする。
- 既存の`@since`、著者、由来と履歴の相対順序を保持する。最新の`@version`を一つにし、
  同月履歴を圧縮し、`@since`と同日の重複履歴を除く。別月の旧`@version`は履歴として残す。
- `scala-block-history-v1`の日付行は既存の`@since`も含め、同じ意味の日付を選択したformatterで再出力する。
  宣言した全tagの最大幅で値の列を揃え、通常tagは左寄せ、履歴tagは右寄せにする。
  標準tagでは` * @since   <date>`、` *  version <date>`、` * @version <date>`、
  新規著者行は` * @author  <author>`となる。既存著者行などの日付以外の文字は変更しない。
  既存blockでは、最初のdepth-oneの通常star行の水平空白とstar位置を保持し、star後の基準空白を一つにする。
  star行のないinline blockでは、開始delimiterより前の同じ行が水平空白だけならその字下げを
  設定済み`linePrefix`に加える。新規headerとinline fallbackでは、設定prefixが水平空白・star・水平空白
  だけならstar位置を保ち、star後を基準空白一つに正規化する。それ以外のliteral prefixは保持する。
  新規headerのdelimiterは設定値を使い、LF／CRLFとも同日再適用時のbyteを保持する。
- 日付・作成日・著者は根拠のある供給値だけを使う。現在のFile APIには信頼できる作成／更新metadataがないため、
  C3は所有coreの`executionContext.clock`から取得した当日を更新日とし、`createdOn=None`、`author=None`、
  `dateUncertain=true`を渡す。system既定zoneやNIO metadataで補わず、不確実性をactual resultにも保持する。
- 履歴がない対象ファイルには規則に沿うコメントを設ける。既知の作成日・著者を使い、
  不明な著者は作らない。コメント以外のソースを変えず、繰返し適用は変更不要になる。

## 通常経路への接続

C3の`GoalPhaseNativeSourceHistory`はcandidate内のmechanical bridgeであり、C2 driverはdurable intentや再実行権限を所有しない。
候補パスを観測した後、stageより前に実行する。成功した整備を含む実際の候補を保存し、
以後の検証・レビュー・commitがその候補を扱う。レビュー済みの原文を後から書き換えない。
変更のない後続処理では適用可能な整備結果を再利用する。

整備のeffect前に既存のmechanical intentを保存し、実際の結果をstage前に記録する。
未解決のintentは既存continuationで停止する。結果未確定は成功にも自動再実行の
許可にもせず、既存の判断経路へ戻す。providerや書込の失敗はtool／configurationの問題として
残し、AIへ日付修正を依頼したりAI修正回数へ加算したりしない。各種エラーは後続のstage、
検証、commitを止める。変更検知用のhash、コピー台帳、TTLは設けない。

## 検証と受入

1. sm-workflowの対象仕様で、別月履歴・同月圧縮・長いコメント・履歴なし・文字列内の疑似marker・
   CRLF・不正入力・再適用を検証する。コメント外のコードと出自が保たれることを確認する。
2. C1の型合成仕様で共通configのobject／string規則、CNCF operationによる同一キーの上書き、
   同一ExecutionContextでの現在呼出し値、`none`でのrule非参照、不正設定の型付き失敗を扱う。
   これは通常CLI経路の証明ではない。C2の実UoW接続仕様で`none`の無操作、専用driver選択、
   対象ソースのworkspace拒否・読取制限、実際の更新、規則不在／失敗、未確定結果を確認する。
3. 隔離した計画の通常CLIで、AIが履歴を整備せずにソースを変更し、runtimeが外部規則を
   適用してから実SBT検証・既存レビュー／Admission・ローカルcommitへ進むことを確認する。
   製品Phase 7は使用しない。

既に合格したB4のSBT修正接続、B5のGit競合解決接続は再利用する。独立した追加レビュー工程を
作らず、現在のCOMMAND Stepレビューと既定のPhase最終手順へ統合する。
開発用SBTは既存の登録runner／共有ロック経由、製品側は通常SBTとsbt自身のlockを使用する。
全体テストの担当は引き続きPhase 6.3。push・配備はこの案に含めない。

設定経路と正式キーの権威は[2026-10-17 journal](../journal/2026/10/2026-10-17-source-history-resolved-configuration-contract.md)
に従う。C3の実装と実検証、C4の接続はそれぞれの限定結果であり、provider配備やStep／Phase受入の証拠にしない。

### C3のcandidate／永続記録／通常CLI接続

`GoalPhaseNativeCandidate`は既存の実Git観測と所有範囲検査を保つ。選択したstage pathと現在のStep所有範囲に
含まれるdirty leafの宛先だけを観測順に重複除去し、indexまたはworktreeが`D`のentryと`renamedFrom`を対象にしない。
whole-Phaseには空配列を渡す。リポジトリ探索、全走査、management writebackの新規履歴整備は行わない。
`prepareC`はinspection保存の前に現在呼出しの正式設定を解決する。`none`はpath／rule／日付／Fileに触れず、
`goldenport`は既存`Rule.includesC`だけで対象を選ぶ。不正設定はinspection intentも残さない。

対象ごとにoriginal candidate frontierのrevision、Step、path、style、provider identity、ISO更新日、不確実性と
任意のactual outcomeを持つ`GoalPhaseNativeSourceHistoryReceipt`を保持する。
envelopeは`{schemaVersion,payload}`、schemaは`sm-workflow.goal-phase-native-source-history.v1`。
日付はbusiness provenanceであり、改ざん検出snapshotではない。raw source、hash、コピーは保持しない。
既存Candidateのv2 payload／codecは変更しない。

既存`F.advanceOperation`にbindしたmechanical storageで、`ordered-candidate:source-history-intent-i`を保存してから
`goal-source-history-prepared:<path>`をacknowledgeする。論理`FileTarget(WorkspaceId("sm-workflow-repository"))`と
実所有core／supplied Sourceで既存dispatcherを一度だけ呼び、actual successを`goal-source-history-executed:<path>`で
acknowledgeする。style／identity／不確実性に矛盾しないactual outcomeを対応する`source-history-result-i`へ保存し、
`goal-source-history-recorded:<path>`をacknowledgeする。保存された次recordを次の履歴対象と既存stage foldへ渡す。
root、grant、encoding、取得はCNCFが所有し、新しいtargetや設定可能な権限は設けない。

File／rule／storageの失敗はその型のまま停止し、acknowledgement失敗は既存candidate同様transaction-indeterminateとする。
intentまたはresultだけが保存されfinal candidateがない場合は既存continuationがpartial／unknownのWaitへ進み、
自動replay、rollback、AI修正、回復経路は追加しない。actual resultの後に既存Git stageが正規化済みソースを観測し、
実tree／diffを既存SBT検証と独立ReviewStep contextへ渡す。

CMLの`GoalPhaseService.advanceGoalPhase`だけに、正式な2キーをoptional string propertyとして宣言した。
sessionId、INPUT／OUTPUT DTO、業務bindingは保つ。通常のgeneratorがRequestDefinitionを生成する。
managed sourceの手編集、独自CLI parser、未宣言optionへの依存、アプリ独自precedenceはない。

`NativeGoalSourceHistoryCliSpec`の次の5シナリオは通常CLIで実検証済みである。

1. common goldenport＋外部object ruleから、headerなしのfixture semantic edit（compiled value=42）を通常Submit／別Advanceで
   正規化し、実diff、positive typed Test receipt、実SBT PIDをReviewStepへ渡す。再Advanceは同一review／bytes／PIDを保つ。
2. malformed common ruleを、宣言した同じ正式styleキーのCLI `none`で上書きし、bare semantic sourceの通常stage／Testへ進む。
3. style省略時は`none`としてmalformed ruleを無視し、意味的commentを保ったまま通常stage／Testへ進む。
4. 不正style／選択したgoldenport rule不在の2 subcaseは、source／index変更・Test前に失敗する。同じfrontierで
   actual common configを`none`へ訂正するとReviewStepへ進む。
5. valid ruleとmalformed managed dateはprepared intent後に失敗し、別Advanceはpartial candidateのWaitになる。
   source／indexを保ち、stage／Test／RepairStep／replayを行わない。

fixtureは隔離Phase1、repository `smwf-p61-cli-source-history`、単一所有Scala source、Scala2.12.18／SBT1.9.7の
通常compiled `HistoryExample.value==42`を使う。semantic editはfixtureが書いた制御入力であり、genuine AI provider、
review result、Admission、commitの実績ではない。親は通常artifact準備を伴うこのsuiteと既存
`NativeGoalCheckRepairCliSpec`の2スイート6テスト成功、production／test compile、両exit 0／lock解放を確認した。
common goldenport／外部object、同じ正式キーのCLI `none`上書き、既定`none`と未使用の不正rule、
選択設定の不正／欠落によるpreflight失敗、bounded tool failureは上記契約どおりに動作した。
アプリ独自config／command parserやprivate RuntimeContext accessは追加していない。全体検証はPhase6.3が所有する。

### C4のgenuine semantic editと通常consumer接続

ordinary preparationは1スイート1テスト成功。その後genuine selected medium ImplementStepが
`HistoryExample.value`を42へ変更し、手作業の履歴整備は行わなかった。semantic workerはSBT／Git／processを実行していない。
通常recordExecution／Submit／Advanceが同じcurrent public `ExecutionContext.resolvedParameters`の
外部規則データと単純な`none`／`goldenport` dispatchを使い、File UoW経由でconfigured headerを生成した。
actual typed stage／Testを経て独立Review待ちになり、normal consumerは1スイート1テスト成功、
両exit 0／lock解放を確認した。再AdvanceはSBTを追加しない。親の実ファイル観測でもheader生成とsemantic valueの保持を確認した。
要求した設定と親が観測したactual completionは異なる証拠であり、runtime／model attestationを主張しない。

このrouteの独立レビュー／CAM Evidence・Admission／ローカルcommitは未完了でP61-ADMISSIONが所有する。
P61-COMMANDはconnected／focused validatedで、既存のprotected independent Step reviewとlocal acceptance commit待ち。
P6-HR01／P6-HR04やPhase全体の完了、installed-host受入、upstream library／provider実装は主張しない。
固定されたprotected初期化設定をoperationで上書き可能にはせず、通常の業務operation parameterは引き続き扱う。
