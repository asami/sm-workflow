# Codex情報取得の呼出元返却とprocess_exec_textの開発候補

Date: 2026-10-23
Status: user-authorized specification/plan update; implementation and acceptance pending
Recipient: sm-workflow開発

## 採用方針

Codex情報取得はsm-workflow内の`host_source_read`ではなく、既存Codex呼出元へ
要求を返す。呼出元が現在のcontextと取得手段で実観測を返し、同じSession/継続位置で
再開する。情報取得のために別モデル実行を起こさない。

API読取り自体がcontextを消すわけではない。別AI実行と実行記録の読取APIは区別する。
旧案もモデルturnを開始する仕様ではなかったので、「旧案は必ずAIトークンを追加消費する」
という理由では撤回しない。撤回理由は、取得責務を既存呼出元へ置き、不要な汎用host
読取入口を増やさないことである。

- [改訂仕様](../../../spec/host-terminal-source.md)
- [既存host契約](../../../spec/native-host-execution.md)
- [CI-01〜04採用計画](../../../notes/codex-caller-information-adoption-plan.md)
- [Phase 13](../../../phase/phase-13.md) / [チェックリスト](../../../phase/phase-13-checklist.md)
- [AI使用量仕様](../../../spec/ai-usage-accounting.md) / [設計](../../../design/ai-usage-accounting.md)

## 開発チャット向けhandoff

現在進行中の作業を維持し、CI-01→CI-02の一往復を先に通す。新規Goal/Sessionを
開始せず、既存発行作業へ仕様差分を対応付ける。未確定な実行を未実行と決め付けて
再送しない。既存受入れ・修正回数・成功/失敗結果・証跡・並行変更を保持する。

作業ツリーには`NativeHostTerminalSource`関連service、provider script、tests、caller
接続の未コミット作業がある。本変更ではそれらを変更・削除・stageしていない。
旧仕様本文も改訂仕様内にhistorical proposalとして保存した。旧案を完成させる義務や
互換性要件にはせず、接続が置き換わるときに所有者が不要部分を整理する。

情報返却は任意JSONを信頼する口にしない。要求先/対象/観測元を既存の型付き契約へ
結び、元のツール観測とAIの説明を区別する。帰属確認が旧provider専用実装に閉じて
いれば、CI-01で呼出元結果を扱える最小のpublic ingressへ改訂する。旧DSLや新しい
hash/証明台帳をその代用品として作らない。

terminal証拠が取れなければ、それを必要とする復旧は未確認のまま残す。optionalな
usage/model情報は未取得表示とし、通常開発を止めない。観測返却を別途必要なHuman
判断として扱わない。取得経路変更だけを理由に新しいHuman回答を要求しない。

Phase 13にも同じ取得責務をU13-01/02へ反映した。既存usage通知が足りれば再取得しない。
親/subagent、Codex/ローカルLLM、二重計上防止、未取得の従来要件を維持する。
Phase 13完了はterminal復旧連携の前提ではない。

## Development Candidate: DEV-CNCF-PROCESS-EXEC-TEXT

Status: OPEN / not adopted / not required by current Codex integration
Owner: CNCF ProcessExecution / protected operation DSL
Target Phase: 未割当。最初の実利用を採用するPhaseで扱う。

登録済みプログラムへテキストを入力し、成功時のstdoutテキストを返す薄いDSLの案。

```scala
protected final def process_exec_text(
  program: ProcessCapabilityId,
  input: String
): ExecUowM[String]
```

既存`process_exec(request)`を使い、別driver/UoW操作を作らない。候補契約はUTF-8、
stdoutをtrimしない、非ゼロ終了・timeout・cancel・出力打切りを完全なテキスト成功に
しないこと。stderrや終了状態を個別に扱う利用者は既存の`ProcessExecutionResult`を使う。
これは未実装案で、確定ABIではない。

今回は使わないので実装しない。短い関数でも文字コード・改行・出力上限・失敗情報の
仕様維持に負担がある。採用条件は、テキスト往復が必要な登録済み外部プログラムの
実利用があり、その利用と同時に仕様・focused検証を定義できること。
`host_source_read`の改名や、通常ファイル読取りのProcessExecution代替にはしない。
今回の計画の依存関係・完了条件には含めない。

## この変更の確認範囲

文書のみ変更。実装・テスト・実行中Session・既存証跡は変更していない。
新経路の実接続/配備確認はCI-02/04に残る。このjournalを引継ぎ正本とし、
別チャットへの送信は本変更には含めていない。

## 2026-10-23 addendum: reserve host for network destinations

Adopt the [terminology handoff](2026-10-23-network-host-terminology-handoff.md)
alongside CI-01 through CI-04. Reserve host for network destinations and use
responsibility-specific caller/runtime/composition/root names elsewhere. Retain no
compatibility layer for old names, while preserving existing Sessions, histories
and validation results. The linked specification and N-01 through N-04 plan define
the authoritative mapping and adoption procedure. This addendum does not establish
implementation completion or adoption into a live Session.
