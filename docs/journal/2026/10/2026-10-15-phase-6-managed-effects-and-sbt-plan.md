# Phase 6: ProcessExecution / SBT調査とGitHub計画の統合

Date: 2026-10-15 (Asia/Tokyo)
Status: adopted plan; implementation and acceptance pending
Scope: sm-workflow Phase 6。既存の6.1/6.2/6.3分割と集約検証所有者を維持。

同日追記: ファイル移行の具体的な公開API・実装引継ぎは
[File Workspace DSL handoff](2026-10-15-phase-6-file-workspace-dsl-handoff.md)を参照。
通常resource readとFile Workspace操作を区別し、公開`read_resource_bounded`を
維持する解釈は後続handoffで置き換える。以下の調査記録と履歴は保持する。

## 採用した判断

Process Executionを使ってfilesystem accessを代替した現実の実装を、
Capability Launderingとして扱う。固定argv、登録済みprogram、別JVMへの隔離だけでは、
ファイル操作の権限は成立しない。禁止されたfilesystem/network/datastore effectを
別の許可済みprocessで実行しても、正規のcapability使用にはならない。

今回の利用者指示は「GitHubで更新したPhase 6に、このチャットの検討結果を合わせ、
計画を更新して詳細をjournalに残す」。本変更は計画の反映であり、実装の開始、
テスト成功、producer Phase完了、commit/pushを意味しない。

実装順序は次の一本の経路とする。

```text
sm-workflowの実際の要求・対象・effectを特定
  -> CNCF110: 最小External Effect分類 + SBT typed DSL
  -> 実際のbuild/test結果を同じworkflowへ返す
  -> CNCF Git typed DSLでstatus/diff/選択したcommit
  -> file I/OをCNCF managed resource経路へ移し、Process経由を拒否
  -> 未登録command/HTTP拒否と範囲内の明示opt-in成功
  -> CBD16の最小laundering検出を同じ分類で接続
  -> installed command/skillによる実タスク完了
```

権限境界を備えたメインルートを先に成立させ、その上に6.1以降のlifecycle、
live plan、Step Admission、FIX、Phase/Finalの枝を接続する。安全でない旧経路を
一度受理してから後のPhaseで直す、という順番にはしない。

## 確認したGitHub資料と取得範囲

2026-10-15に各originをfetchし、次の内容を確認した。

| Repository / reference | 確認内容 |
| --- | --- |
| [sm-workflow `cd73fe3` Phase 6](https://github.com/asami/sm-workflow/blob/cd73fe3/docs/phase/phase-6.md) | Safety correction節、P6-SBX01〜05、CNCF110 early slice |
| [sm-workflow Safety Sandbox journal](https://github.com/asami/sm-workflow/blob/cd73fe3/docs/journal/2026-10-15-safety-sandbox-development-driver.md) | 基本機能後の段階的Sandbox導入という元の全体方針 |
| [CNCF `549ca76c` Phase 110](https://github.com/asami/goldenport-cncf/blob/549ca76cce3784c8c1f3f0d995b9524c42b02dea/docs/phase/phase-110.md) | P110-01、P110-04、Phase 6 early delivery、以後のSandbox work |
| [CBD Support `8c5b8c4` Phase 16](https://github.com/asami/textus-cbd-support/blob/8c5b8c4a121b17453c6684a60ec0534e22ca172f/docs/phase/phase-16.md) | Phase 6 early rule slice、CNCF分類の利用、静的検出の限界 |

先のSandbox journalにある「基本機能の後」は全体導入の順序として維持するが、
今回判明した欠陥に必要な部分は新しいPhase 6 correctionが先取りする。
GitHubの未分割Phase 6本文をローカルへ丸ごと上書きせず、ローカルの6〜6.3分割、
運用はcommand中心、clientは軽い接続確認、既存の過剰チェック抑制規則へ統合した。
Phase 8の拡張計画全体は今回の文書更新対象にしていない。

CNCF/CBDのremote-tracking refsは取得したが、checkoutのmergeやsource変更はしていない。
ローカルCNCF checkoutにはPhase110文書がまだないため、上表は取得したcommitへの参照。
checkout配置の違いを古いリンクと誤認して共有文書を書き換えることはしない。
計画資料の取得は実装・artifact・保証の提供完了を示さない。

## ProcessExecutionの実装調査

対象は調査時のローカル作業ツリー。開発中の変更を含む。
入口の[WorkflowManagedProcessExecution](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowManagedProcessExecution.scala)
には35個の`Purpose`（file系5、Git系30）と、ordinalで選ぶ4個の経路がある。
これは静的な経路数であり、一つのworkflowが必ず39回processを実行するという意味ではない。

### file系5経路: 置換必須

| Purpose | 実際の処理 | 判断 |
| --- | --- | --- |
| ObserveSelectedDocuments | 選択Phase文書の読取・状態判定 | managed resource readへ移す |
| ApplySelectedDocuments | 選択Phase文書の作成・部分更新 | managed resource writeへ移す |
| CaptureConflictFiles | conflict対象ファイルの現値取得 | managed resource readへ移す |
| ObserveConflictFiles | 現値と要求の比較 | resource read後のプロセス内計算へ移す |
| ApplyConflictFiles | 選択conflictファイルへ変更を適用 | managed resource writeへ移す |

[SelectedPhaseDocumentProgram](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/runtime/SelectedPhaseDocumentProgram.scala)
と[RepositoryConflictFileProgram](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/runtime/RepositoryConflictFileProgram.scala)
は別JVMで起動され、JSON stdin/stdoutを介して内部のFiles/FileChannel処理を呼ぶ。
ファイル操作にprocess起動、classpath、exit、timeout、出力量上限の問題を持ち込んでいる。
observe/captureにもlockファイルの作成・書込権限・排他取得が必要な実装になっている。

正式なCNCF resource契約のもとで、選択した対象へのアクセスと必要な非上書き条件を
維持する。純粋な読取に書込lockを要求しない。旧実装の繰返しread、全ancestor検査、
固定数値上限、lockを機械的にコピーせず、producer保証とアプリ上の意味に照らして
必要なものだけ残す。実装前に具体的なCNCF APIで不足を特定し、不足分はCNCFへ実装する。
CAR内の直接Filesアクセスへの付け替えや、別mainラッパーの新設は修正にならない。

### Git系30経路: tool実行は妥当、所有層と呼出しの必要性を見直す

実argvは[RepositoryGitRuntimePrograms](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/runtime/RepositoryGitRuntimePrograms.scala)
にある。次の各PurposeはGitの意味を持つので、通常のfile I/Oと同一視しない。
CNCF typed Git DSLへ移し、sm-workflowはsemantic operationを選択する。

| Purpose（既存名） | 用途・扱い |
| --- | --- |
| GitBranch / GitRoot | 作業branch / repository rootの取得 |
| GitRemoteTip | リモート参照。network/remote scopeが必要 |
| GitHead / GitTree | commit / tree identityの取得 |
| GitStatus | worktree/index状態の取得 |
| GitShallow / GitReplacements | 履歴判定の前提。各分岐で必要な場合に限定 |
| GitMergeHead / GitTrackingTip | merge状態 / tracking ref |
| GitRelation / GitMergeBase / GitIsAncestor | 履歴関係の判定 |
| GitFetch | remote取得。local statusの権限とは別 |
| GitFastForward / GitMerge / GitPendingMerge | 選択されたmerge意図の実行 |
| GitCommitPending | pending mergeのcommit |
| GitPush | remote書込。別途admitted destinationと意図が必要 |
| GitStageFullState | index変更。既存名をそのまま包括的stage権限にせず、必要な対象を限定 |
| GitIndexTree | indexからtreeを生成するGit object操作 |
| GitCreateCheckpoint / GitInstallCheckpoint | checkpoint commit作成 / ref更新 |
| GitCommitMetadata | commit属性の読取 |
| GitUnmergedIndex | conflict indexの読取 |
| GitBlobContent | Git object内容の読取。`cat-file`は通常ファイルの`cat`代替ではない |
| GitIndexEntries / GitCheckPendingIndex | index内容 / pending差分条件の確認 |
| GitPendingDiff / GitPendingPaths | pending差分 / 変更pathの取得 |

[RepositorySyncManagedGitObservation.inspectC](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncManagedGitObservation.scala)
は一回の観測で8回、trackingがあると10回の逐次Git呼出しを構成する。
Git実行自体の正当性と、毎回全項目を調べる必要性は別問題。
変更のないeffect区間での結果共有や必要項目の選択は改善候補だが、実測した性能問題や
全観測の削除を意味しない。Phase 6で全Git操作の最適化・全面的batch APIを先に作らず、
実ドライバが使うものから移す。後続で使う操作はそのdelivery ownerが同じDSLを拡張する。

### 動的選択4経路

| 経路 | 判断 |
| --- | --- |
| stageConflictC | 選択conflict pathのstage。Git DSLへ |
| stageGoalCandidateC | 選択Goal pathのstage。Git DSLへ |
| commitGoalStepC | 選択Step commit。Git DSLへ |
| repositoryValidationC | 設定されたcheckの実体に応じる。SBTならSBT DSL、他toolなら明示opt-in、内部計算ならin-process |

ordinalで固定programを選ぶことは任意argvより限定的だが、実体のeffect分類と権限を
不要にはしない。public resultがexecution authorityを新しく作ってはならない。

## SBT経由の必要性

| 箇所 | 確認事実 | 採用した対処 |
| --- | --- | --- |
| [build.sbt](../../../../build.sbt) `Test / compile` | 全test/testOnlyがcozyPrepareRuntimeとCompile/packageBinに依存 | runtime/CLI/package統合テストだけに準備を結びつける。普通のunit/codecテストには課さない |
| [SbtValidationProgram](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/runtime/SbtValidationProgram.scala) | ProcessExecution -> 中継JVM -> SBT JVM。中継は実cwdを設定 | CNCF SBT adapterにcwd/env/launcher/resultsを集約。sm-workflow独自main/ProcessBuilder wrapperを解消 |
| [ThreeWorkflowSbtOperationSpec](../../../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowSbtOperationSpec.scala) | 実SBT testが小さなProductCheckをcompile/runしてファイル内容をassert | 実SBT bridgeの検証として必要。通常のfile読取とは区別し、DSL消費へ更新して限定的に保持 |
| build.sbt `runtimeScripted` | Scala 2.12のscriptedが子SBTを所有。specはDoNotDiscover | 選択したSBT結合試験の用途は妥当。全テストへの横展開はしない |
| build.sbt `skillBundle` / `checkSkillBundleReproducibility` | 配布ZIP作成 / 再現性試験 | build成果物に属する処理として妥当。通常起動の前提にはしない |
| [run-server.sh](../../../../scripts/run-server.sh) / run-server-debug.sh | 準備済みruntime-classpath等を使いlauncherで起動 | SBTを起動しない方式を維持。不足時に無条件の再buildをしない |
| [native-goal-command.py](../../../../scripts/native-goal-command.py) | 通常commandはlauncherからCncfMainを直接呼ぶ | 通常のOperation起動にSBTを追加しない。host preparation用独自mainの妥当性まで、この事実で保証しない |
| scripts/update-runtime-classpath.sh | 管理されたcozyPrepareRuntimeを案内してexitするだけ | 現在SBTを起動している箇所として数えない |

`cozyPrepareRuntime`のproducer定義はsbt-cozy/CozyPlugin.scalaにあり、Compile/compile、
classpathとmanifest等の生成へ依存する。タスク依存があることと、毎回全compile/packageを
やり直すことは別。増分処理はあるため、性能改善の数値は主張しない。

調査した通常運用sourceには、ファイル読取だけを目的に`sbt runMain`を起動する経路は
見つからなかった。SbtValidationProgram.definitionCの呼出しは確認したsourceでは上記の
SBT統合specにある。外部host設定の全配置を実行検査したわけではない。
CML derivation specのJava/compiler subprocessもSBT子起動とは区別する。

typed SBT DSLを作るだけでは過剰なSBT依存はなくならない。次を同時に守る。

- 通常のデータ読取・変換はresource APIとプロセス内計算で行う。
- 準備済みアプリの起動は正規launcher/Operationを使う。
- SBTは必要な生成、compile、dependency解決、package、testに限る。
- SBT taskは任意コードを実行し得る。typed `test`をhostile build containmentと説明しない。
  semantic workerが任意task式やrunMainへ逃がす経路を公開しない。
- 必要な統合試験は残し、通常testの依存切り分けで試験を未実行のまま落とさない。
  6.3の集約full-suiteには全通常specを含め、分離した特殊SBT結合試験のfocused evidenceは
  実際に適用可能な範囲で再利用する。新しい全試験をStepごとに繰り返さない。

## 所有者、受入れ、過剰検証の抑制

### 追記: protected operation群への専用内部DSL追加

利用者の追加指示は「git_pull、sbt_testのような専用内部DSL」と、その配置を
「UoWと実装コードの間にあるprotectedのoperation群」とすること。
この位置にGit/SBT専用の操作を追加する方針を採用した。

現在のCNCF `BehaviorProcessExecutionPart`は`protected final def process_exec`
で要求を受け、ProcessExecutionAdmissionで解決し、`ExecUowM`と既存の
`UnitOfWorkOp.ProcessExec`へつないでいる。新しい内部DSLもこの構成に沿う。

```text
Operation / Serviceの実装コード
  -> protected git_pull / sbt_testなど（型付き要求・結果）
  -> CNCF ExecutionContext / effect admission / tool adapter
  -> 既存のmanaged execution・UoW経路
  -> 実際のGit/SBT実行
  -> 型付き結果を実装コード・workflowへ返す
```

`git_status`、`git_diff`、`git_add`、`git_commit`、`git_fetch`、`git_pull`、
`git_push`、`sbt_compile`、`sbt_test`、`sbt_test_only`、`sbt_package`を
必要に応じた語彙とする。これはAPIの方向であり、Phase6の最初に全メソッドを
実装する要求ではない。既存のfetch/merge判断を一律git_pullへ置換せず、
git_pullを使う場合のintegration方針を型付きで明示する。publishの追加は実需と
採用範囲に従い、テスト権限から公開権限を派生させない。

呼出し側は論理repository/build targetと操作固有の引数だけを渡す。
メソッド名だけ専用にして内部で任意argv/task式を通す形にはしない。
意味のあるpath/ref/test selector/message等は型付き値の中で表現し、すべての文字列を
禁止するような過剰な制約にはしない。実際のcwd、実行ファイル、environment、
argvへの変換、stdoutの解釈、exit/failure/unknownの結果化はCNCFが所有する。

`protected`は可視性の境界であり権限の境界ではない。専用メソッドでも同じCNCFの
admission/effect制御を通す。sm-workflow側の重複guardは追加しない。
専用UoW nodeの要否はCNCFの実装設計に委ね、API追加のためだけに新しいalgebraや
executorを必須化しない。UoWへ組み込んでもGit/SBTの外部effectがrollback可能になる
わけではない。`_c`等の補助形も実際の利用と既存CNCF規約に合わせて決める。

この追記も計画のみ。protected内部DSLの実装や実行検証はまだ行っていない。

P6-SBX01〜05の唯一のdelivery checklistは
[shared checklist](../../../phase/managed-test-suites-phase-checklist.md#phase-6-managed-effects-correction--2026-10-15)。
Phase本文とこのjournalにはcheckboxの複製を作らない。

| Owner | 今回必要なdelivery | 含めないもの |
| --- | --- | --- |
| CNCF110 early slice | 最小External Effect、SBT/Git semantic DSL、process/HTTP opt-in、必要なresource API不足の解消 | 全階層SandboxContext、Loading Admission全域、server伝播、Docker handoff |
| CBD16 early slice | CNCF分類に基づく認識可能なlaundering診断、正規Git/SBT/resource経路との区別 | 全アプリsecurity rule群、別のpolicy taxonomy、静的解析による完全安全証明 |
| sm-workflow6 | 実際の使い手、旧file process経路除去、SBT準備の必要性分離、P6-SBX受入れ | 独自Sandbox/guard/scan framework |
| 6.1 | 受理済み基盤の上のcommand lifecycle、live plan、STEP品質 | P6-SBXの作り直し・再受入れ |
| 6.2 | 同じ基盤でFIX、recording、dependency判断 | security名目のauthority拡張・counter reset |
| 6.3 | 影響した統合箇所の受入れ、Phase/Final再利用、集約full-suite | producer内部試験の一律再実行 |

追加検証は「明示採用した契約」「今回変更の影響」「適用可能な既存証跡では不足」の
三条件が揃うときだけ。この欠陥修正は三条件を満たすが、全security familyへ拡張しない。
manifestはrequest、runtime/deployment設定がgrant。command-name登録だけ、AIの自己申告、
lint成功だけで権限を与えない。未知の実行結果をPASSにしない。

実際の同一workflowへのresult/continuation、認められた成功と認められないeffectの拒否、
旧file wrapperが通常経路に残らないことを狭い接続specで示す。全command×provider×scopeの
matrix、新しいhash台帳、証明書、重複したproducer内部分岐試験は作らない。

ProcessExecutionで行うGit object操作や、SBT自身の正規のbuild/test子processまで一律禁止
しない。意図したmanaged tool operationと、他capabilityの迂回を区別する。
強い隔離を要する悪意あるCAR/build全般の封じ込めは、このearly sliceの保証ではない。

## 進行中作業と見積り

既存のGoal、issued context、実結果、review/finding ID、repair数を維持し、影響した証跡だけ
再評価する。Phase6の旧6h/5–7hは分割時の見積りであり、追加producer作業を含む新見積り
ではない。次の通常の計画境界で、実装済み分を差し引いて残作業とproducer integrationを
再見積りする。新しい許可儀式、全Phase再開始、架空の所要時間は追加しない。

6.1〜6.3とroadmapに残っていた2026-10-13のCNCF/Cozy一律待機文は、Phase6の
2026-10-14既存main checkoutへの統合権限と今回の限定slice計画への参照に更新する。
今回の計画採用は各producer Phase全体の起動・完了や無関係な変更の権限ではない。
通常開発時のtop-level SBTは引き続き登録runnerと機械全体の直列化契約を使う。

Phase6 acceptance時はCNCF110/CBD16の対応work itemの部分的deliveryと証跡を記録する。
Phase6完了をPhase110/16完了と扱わない。後の本格実施で成果を作り直さない。
6/6.1/6.2のrepository-fullはdeferred-not-run、集約ownerは6.3、6.4は別のまま。

この更新では実装・SBT・性能測定・テスト・commit/pushは行っていない。更新した相互参照、
P6-SBXの単一所有、既存32 ID + SBX 5 IDの一意性、6.3集約ownerの維持、差分の空白を
静的に確認した。全リンク走査では、既存HEADにもあるchecklistのPhase5.1旧見出しリンク
`connection-verification-transferred-from-phase-5--2026-10-11`が未解決だった。
これは今回追加した参照ではなく、歴史的受入れ内容の指示先を推測して変更していない。
