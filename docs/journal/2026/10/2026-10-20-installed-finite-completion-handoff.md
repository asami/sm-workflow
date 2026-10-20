# Installed CLIによる有限継続経路の引渡し

## 修正の対象

専用agentを認識する新チャットへの引継ぎ後も、継続用の
`native-goal-command.py` が開発checkoutのlauncher・生成classpath・
component-dev-dirを組み立てていた。通常のinstalled CLIを使う公開契約と
一致しないため、明示的な `--executable` と `--host` を受け取り、固定の
Operationを通常commandとして呼ぶ形へ修正した。旧 `--primary` は受け付けない。
受動的なauthority準備callerと実際の呼出し元の仕様も同じ契約へ更新した。

`--action complete` は、現在の要求へのSubmitを一度だけ行い、同じSessionの
accepted応答を表示・保存した後、元のtask routingを再読込してからAdvanceを
一度だけ行う。routingの変更・欠落・破損、応答や実行結果の不確定時は停止し、
保存済み出力を保持する。再送、別Sessionの選択、開発launcherへのfallbackはない。

## 配布先と呼出し

development-workstationを正本として、既存のtools bundle／installerで次を配布する。

- `common/codex/scripts/native-goal-command.py`
- `common/codex/scripts/native-goal-authority.py`
- 3つのsmスキル、5つの契約文書、既存の5つの専用agent定義。

インストール済みスキルからのhelper相対位置は
`../../scripts/native-goal-command.py`。親チャットがmanaged Pythonと範囲を
限定したescalationで直接呼ぶ。subagentにCLI実行を渡さない。
`--executable` は通常のinstalled sm-workflowの絶対パスであり、ソースcheckoutの
選択ではない。`--host` は既存Sessionを所有するhostを明示する。
task・request・submissionは保持済みの実際の入力、outputはtask内の新しい空directoryを使う。
現在の公開仕様は [有限caller契約](../../../spec/native-goal-command-task.md) と
[host実行契約](../../../spec/native-host-execution.md) に従う。

製品側の通常skillBundleとcomponent JARにも、上記2つのhelperと有限caller契約を
含める。今回はPython／スキル資源の共有配布で反映できるため、保留中のScala終了処理の
変更を混ぜた追加publishLocalは行っていない。すでに公開済みのruntime選択を保持した。

## 保持した状態と継続先

引継ぎ元の報告では、新チャットで `sm_workflow_review` の独立レビューは受入れ済み。
そのレビューを再実行しない。Phase 6.4の既存Session
`96c294d73be3d48067f3721d8a2abf33`、Plan revision 1、候補、受理済みE1結果、
成功済みCompile、Goal・履歴・修正回数を保持する。機能開発checkoutは編集していない。
本修復では実SessionのStart／Submit／Advance、StartupImport、commit／pushを実行していない。

引継ぎ先の準備結果はStartupImport未設定と報告されている。責任を持つ開発チャットで
既存の通常設定・準備済み入力を確認し、同じ要求に対する継続を行う。
新規Start、受理済みSubmit、成功済みCompileの再実行で復旧しない。
この記録は他チャットへの連絡許可や、新しい実行権限を与えない。

## 確認記録

対象Python仕様、共有配布仕様、通常SBTの配布物仕様と再現性、独立したfocused reviewを
既存の受入手順で確認した。実行結果、対象・条件・限界、配布確認は
`target/installed-completion-20261020/final-integration.md` に保持する。
test-owned executableの実プロセス確認はtransportの証拠であり、実Sessionの
Admission／Submit／Advance成功を主張するものではない。
シグナル・子孫プロセス終了の未解決事項は
[既存の保留記録](2026-10-20-sbt-runtime-execution-and-deferred-termination.md) に残す。
