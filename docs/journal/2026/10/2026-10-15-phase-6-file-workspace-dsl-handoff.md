# Phase 6 File Workspace DSL: 実装handoff

Date: 2026-10-15 (Asia/Tokyo)
Status: adopted plan; implementation/acceptance pending
Owner: sm-workflow Phase 6 / P6-SBX03
Producer: CNCF Phase 110の必要部分。CBD16は同じeffect分類を消費。

## 引継ぎの目的と正本

GitHubの[File Workspace DSL仕様案（959a869）](https://github.com/asami/sm-workflow/blob/959a869/docs/journal/2026-10-15-file-workspace-dsl-safety-sandbox.md)
と、このチャットで合意した作戦を[Phase 6計画](../../../phase/phase-6.md#file-workspace-dsl-and-ordinary-read-limits--adopted-2026-10-15)
へ反映した。実装担当はこのhandoffを既存の進行中計画へ取り込み、同じGoalの残作業として進める。
本書自体は実装要求のdispatch、state遷移、証跡の受理やPhase完了を行わない。

追加要件として[Large text / streaming仕様案（91a7eaa）](https://github.com/asami/sm-workflow/blob/91a7eaa/docs/journal/2026-10-15-file-workspace-large-text-streaming.md)
も採用した。以下の大規模テキスト節は通常readの上限、必要時の部分読取、後続streamingを
区別する。Phase 6の全体streaming実装や実行順序の変更を要求するものではない。

先行する[ProcessExecution/SBT調査journal](2026-10-15-phase-6-managed-effects-and-sbt-plan.md)
の経路一覧と履歴は保持する。「managed resourceへ移す」という記述を、今回は
通常resource読取と開発workspace操作に分けて具体化する。公開`read_resource_bounded`
を採用する解釈は、本handoffで置き換える。

## 決定した契約

| 用途 | protected内部DSL | CNCFの責務 |
| --- | --- | --- |
| 論理resource読取 | `read_resource` / `read_resource_text` | resource解決、権限、通常readへの実行制限 |
| 開発対象ファイル操作 | まず`file_read_text` / `file_write_text` | logical workspace/root解決、粗い読書き権限、read/write実装 |
| Git | `git_status` / `git_diff` / `git_commit` / 必要時`git_pull`等 | typed Git semantics、実行と結果解釈 |
| SBT | `sbt_compile` / `sbt_test`等 | typed build/test semantics、実行と結果解釈 |

これらは実装コードとCNCF実行基盤の間にあるprotected Behavior operation群へ配置する。
既存のeffect/UoW構成に合わせ、API追加のためだけに別executorや新しいUoW algebraを
必須にしない。`protected`は可視性であって権限ではなく、CNCF admissionを通す。

workspaceの論理参照と相対pathを渡し、host絶対pathを操作引数にしない。
ExecutionContextに結びついたrootをruntimeが解決する。File/Git/SBTが同じ対象bindingを
利用できる形とし、それぞれに別のroot設定・権限台帳を作らない。ただしfile権限から
Git remoteアクセスやSBT実行権限が派生するわけではない。

権限はroot単位の粗いものとする。割り当てた開発checkoutはMANAGE/read-write、
dependencyは別途許可がなければread-only、workflow stateはその用途のrootに限定する。
parent traversal、別名・symlink解決等でrootを抜ける操作をCNCFで拒否する。
pathごとのSELinux型ACLは作らない。rootへの書込権限があっても、workflowの選択した
変更対象・意図を無視して全ファイルを変更してよいわけではない。

権限と実行制限は分ける。最大readサイズはruntime/execution設定から解決し、通常readの
取得中に適用する。全読込後のサイズ検査だけ、または無制限readへのfallbackでは満たさない。
アプリやAIに「安全なAPIを選ぶ」「最大サイズを申告する」という責任を課さない。

## 実装順序

1. **対象bindingと制限の最小契約を確定する。** 論理workspace/targetとruntime limitsの
   取得元を決める。これはSBT/Git/Fileで対象指定が分裂しないための契約整理であり、
   完全なSandboxContext階層やFile操作全種類を先行実装する工程ではない。
2. **現在のSBT -> Gitの順序を維持する。** `sbt_test`等で実際の結果を同じworkflowへ
   戻し、必要なGit status/diff/選択commitを接続する。不要なruntime/JAR準備の依存分離も維持する。
3. **通常resource readと最小File DSLを実装する。** 現在追加されている
   `read_resource_bounded`の取得処理を通常readの裏側へ移し、公開安全版DSLを撤去する。
   provider内部の上限付き取得処理は再利用し、必要に応じて内部契約を整理する。
   続いて`file_read_text` / `file_write_text`とworkspace authorityを接続する。
4. **既存5操作を移行して一本の経路を通す。** 下表の移行後、read -> edit -> write ->
   `sbt_test` -> `git_diff` -> 同じworkflowの継続を実行する。旧Process経由を通常経路に残さない。
5. **既存計画のcommand/HTTP制御とCBD接続を完了する。** default deny / explicit opt-in、
   shell除外、CNCF共通分類を用いたCBD16の最小laundering検出を接続し、実タスクを完了する。

| 既存Purpose | 移行先 |
| --- | --- |
| ObserveSelectedDocuments | `file_read_text`とプロセス内の文書状態判定 |
| CaptureConflictFiles | `file_read_text`と必要な現在値の返却 |
| ObserveConflictFiles | `file_read_text`とプロセス内比較 |
| ApplySelectedDocuments | `file_write_text`とアプリ側の文書編集意味 |
| ApplyConflictFiles | `file_write_text`とアプリ側のconflict解決意味 |

最初に決めるwrite契約は、必要な新規作成時の非上書きと既存内容の更新である。
型付きの書込モード・実結果・必要な競合の扱いを示す。ハッシュ台帳や無変更証明を追加せず、
既存の選択対象と変更意図を守る。atomic replace等が必要なら一時ファイル・cleanupは
CNCF実装内部で行い、アプリへfile_move/deleteを要求する理由にしない。
複数ファイル全体のrollbackや、任意の同時編集者に対する完全なtransactionは主張しない。

binary read/write、list、exists、mkdir、copy、move、deleteは実際の不足が生じたものだけ
追加する。create/writeの戻り値で存在衝突を扱えるなら、毎回existsを先に呼ばせない。
copy/moveを採用する場合はsource/destination双方の権限を確認し、workspace境界を
すり抜ける操作として扱わせない。初回にこの全語彙を完成させる必要はない。

## 追加要件: 大規模テキストと後続streaming

### 通常の全件読取

`file_read` / `file_read_text`と`read_resource` / `read_resource_text`は単純な全件読取APIを
維持する。既定の最大readサイズ候補は **1 MiB（1,048,576 bytes）**。
これは追加仕様の候補値であり、既存実装・配備済み設定がこの値だという主張ではない。
CNCFのruntime/execution設定として既定値と適用範囲を確定し、取得中に適用する。
大きなlogを読むために呼出し側が上限を上げたり、安全版APIを選んだりする設計に戻さない。
全件読取の上限超過を、黙って先頭だけ返す成功結果に変えない。

### Phase 6で実需がある部分読取

候補は次の専用DSL。全部をPhase 6の完了条件にせず、実ドライバの要求で選択する。

| DSL候補 | 意味 |
| --- | --- |
| `file_read_head` | 先頭の限定範囲 |
| `file_read_tail` | 末尾の限定範囲 |
| `file_read_lines` | 指定した行範囲 |
| `file_search_text` | テキスト検索の限定結果 |

典型的な利用はtailでエラーを発見 -> keyをsearch -> 周辺linesを取得、である。
要求する行数やbyte数は取得条件であって権限ではない。CNCF runtimeの最大行数・byte数が
常に上限になる。File Workspaceの同じroot/read権限を利用し、別のlog専用権限台帳は作らない。

実装は全ファイルをメモリに載せてからsliceしてはならない。行単位でも極端に長い一行を
無制限に蓄積しない。必要な範囲の取得にseekや逐次scanを使うかはproducerで決める。
bounded memoryとファイル全体をscanしないことは別の保証なので、計測していない高速性は
主張しない。採用する操作について、要求範囲、返した位置、runtime cap到達による打切り、
続きの有無/不明等の意味を決める。完全な検索結果や全件読取と誤認させない。
行番号・encoding等の細部も選択した操作に必要な分だけ確定する。

受入れは、全件read上限より大きいlogから選択した部分だけを取得できること、runtimeの
行/byte capが効くこと、全件bufferingをしていないこと、部分結果がworkflowへ正しく
伝わることに絞る。既存producer証跡と重複しないfocused specを用いる。
現実のPhase 6経路に部分読取の必要がなければ、候補として保持し未実装をblockerにしない。

### 後続の行/チャンクstreaming

`file_stream_lines` / `file_stream_line_chunks`は具体的な大規模log workflowが必要になった
段階で追加する。例えば200行ずつ処理して十分な情報が得られたら停止できる形を想定するが、
200は例示であり固定既定値や権限ではない。

後続実装では、同じworkspace authorityのもとで、runtimeのchunkごとの最大行数/byte数と
適用する総stream制限、resource lifetime、close/cancel、bounded buffering、backpressureを
所有する必要がある。ファイル全体を先に読んでからstreamを返す実装では満たさない。

今回はこの拡張点と責務を残す。汎用streaming framework、常駐tail監視、全provider共通の
stream lifecycle実装や、そのための試験matrixをPhase 6へ追加しない。
SBT -> Git -> 必要なFile操作 -> command/HTTP -> CBDの順序と6.3集約検証担当は維持する。

## 既存23テストと検証範囲

以前の`S6-01D4U-bounded-resource-qualification145.json`にはCNCFの4 suite / 23 tests成功が
記録されている。これはその時点のsource-level qualificationであり、公開DSLの採用決定や
新しいFile Workspace/consumer接続の成功を示さない。以下のように引き継ぐ。

- 読取アルゴリズム、上限超過、不正UTF-8等の既存振舞いは実装・specを再利用する。
- API変更でaffectedとなるspecは通常read入口に合わせて更新し、必要な範囲で再実行する。
  「旧23件PASS」を新APIのPASSへ名前だけ付け替えない。
- runtimeの制限が通常APIから実際に適用されること、超過前に取得が制限されることを確認する。
- managed workspace内の正常read/write、root escape、read-only dependencyへの書込拒否を
  CNCFの適切な層で確認する。sm-workflowは自身のbindingと同一workflowへの結果接続を確認する。
- readにwritable lockや別JVMが必要ないこと、旧Process file-I/O経路を受理しないことを確認する。
- applicableなproducer証跡をconsumerで再実行しない。全path×tool×providerのmatrix、
  per-Step安全証明書、新しい検証用hash、過剰な再読取・存在確認は追加しない。

読取制限とFile DSLの追加はP6-SBX03の具体化として扱い、checkboxや新しいPhaseを増やさない。
唯一のdeliveryチェックは[shared checklist](../../../phase/managed-test-suites-phase-checklist.md#phase-6-managed-effects-correction--2026-10-15)。
P6-SBX01/02/04/05、従来の未完了義務、実際の履歴・finding/repair数も維持する。

## 受け手の次の作業と範囲

進行中のPhase 6親担当は、更新したPhase本文・P6-SBX03を既存計画へ採用し、残作業の
見積りと実装manifestに反映する。最初の実装単位は引き続き最小SBT producer/consumer。
File操作の全実装や、新しいparent設定の回答待ちをその前提にしない。既存の有効な設定・
権限は再利用し、計画条件に実質変更がある部分だけ既存手順で扱う。このhandoffから新しい
control値、lease状態、repair予算を作らない。

CNCFが汎用runtime、sm-workflowがworkflow固有意味とconsumer、CBDが共通分類の解析を
所有する。必要なproducer work itemの部分的deliveryを記録し、Phase110/16全体を完了としない。

ここでのworkspaceは既存のadmitted checkoutの論理的なアクセス境界。
ローカルworktree運用の中断を解除せず、新規worktree作成、Phase7 artifact overlay、
完全なServer/Client/Operation SandboxContext、Loading Admission全域、server伝播、
Docker handoffは取り込まない。command中心/client軽量確認、6.1〜6.3の既存担当、
集約full-suiteが6.3、evidence storageの6.4が別Phaseという分担は変えない。

## この文書更新の実施範囲

Phase 6本文、P6-SBX03、共通security profile、6.1の消費側説明、roadmapとjournalの
相互参照を更新。実装コード、進行中の`.codex-workflow`状態、producer checkout、
test結果、commit/pushは変更・実行していない。開発チャットへの送信も行っていない。
