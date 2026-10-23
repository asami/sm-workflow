# Codex情報取得を既存の呼出元へ戻す採用計画

Date: 2026-10-23
Status: typed ingress, isolated installed round trip and normal runtime adoption verified; remaining connected boundary coverage pending
Owner: sm-workflow開発（既存のCodex実行記録/復旧連携作業）

## 目的と境界

[Codex terminal情報仕様](../spec/host-terminal-source.md)の改訂を現在の作業へ採用する。
sm-workflowは情報要求を返し、既存Codex呼出元が実観測を取得・返却し、同じSession/
継続位置で処理を続ける。API読取り自体がcontextを失うという前提ではない。
取得責務を現在の呼出元に置き、別AI実行やruntime内の取得proxyを不要にする。

新PhaseやGoalの開始指示ではない。Phase 6.4/6.5の完了条件を全面改訂せず、現在
発行中の対象作業への差分を通常の採用手続で対応付ける。使用量計測への同じ責務分担は
[Phase 13](../phase/phase-13.md)に記載し、その完了を既存復旧作業の前提にしない。

## 主経路を先に通す順序

CI-01〜04に接続する命名は[用語整理計画](runtime-caller-terminology-adoption-plan.md)
と[正本](../spec/runtime-caller-terminology.md)を採用する。hostはネットワーク接続先のみ。
既存CodexはCaller、設定ディレクトリはRuntimeRoot、実行組立てはRuntimeComposition。
旧providerをrenameして延命せず、新しいcaller往復を目的別の型・設定・CLI名で通す。
非接続部分の全rename完了をCI-02の前提にはしない。旧名aliasを置かず、影響する両端を
同時更新し、必要な保存形式変換だけを明示的に行って同じSession/履歴を保持する。

1. **CI-01 契約対応。** 既存issued-work/return、terminal観測、Human証跡の境界へ
   対象情報・相関ID・戻し先・取得状態を対応付ける。「固定providerだけが証明する」
   前提を除き、呼出元の実ツール結果の帰属を扱う。情報取得とHuman承認を混同しない。
2. **CI-02 一往復。** 一つの対象で、要求返却→既存呼出元の取得→結果返却→同じ
   Sessionの継続を通す。別Codexモデル実行、`host_source_read`、取得用ProcessExec
   proxyを起動しない。raw観測と表示要約を分ける。
3. **CI-03 境界。** 別execution/Session、部分取得、未取得、重複返却、返却中断からの
   再開を既存の継続機構で扱う。terminal事実からdescendant終了・業務成功を推測しない。
   必要証拠が未取得なら当該復旧は未確認のまま。optional usage不足は通常開発を止めない。
4. **CI-04 配備。** 影響するcaller/service/skill接続を配備して実往復を確認する。
   旧provider専用宣言・新DSL・専用テストは利用者を確認して整理する。共通の既存
   ProcessExecは変更しない。未リリース旧案への互換性層や自動fallbackは作らない。

wire/Operation名はCI-01で実際のpublic APIへ接続し、足りない部分だけ確定する。
別engine/dispatcher/承認プロトコルを新設しない。未接続のまま既存DTOへ同等JSONを
投入して証跡の由来を代替しない。取得できない実情報をAIで補完しない。

## 差分受入れ

- [x] CI-01: caller request/resultと継続先が対応し、独立Human判断と情報取得を区別する。
- [ ] CI-02: 同じCodex呼出元の実観測で一往復し、元のSessionから継続する。fixtureだけを実接続成功としない。
- [ ] CI-03: 別対象・再送・未取得・遅延/再開を扱い、元commandや成功済み検証を再実行しない。
- [ ] CI-03: helper/CLI/descendant/業務受入れの証明範囲を保持し、必要なHuman入力を情報返却で代替しない。
- [x] CI-04: 旧provider/DSLを前提にせず実配備経路で利用でき、既存Goal・履歴・受入れ・修正回数・検証結果を保持する。

影響するcontract/caller/serviceのfocused検証と、一つの実接続を中心にする。
必要な独立レビューは変更した入力帰属と継続境界に絞り、適用可能な旧結果を再利用する。
旧結果を新経路の合格証拠とはしない。`process_exec_text`は依存関係・完了条件に含めない。
[開発候補とhandoff](../journal/2026/10/2026-10-23-codex-caller-information-handoff.md)を参照。

## 2026-10-23の実績対応

CI-01は実装・focused検証済み。CI-02は同じ既存callerの実ツール観測を、
新しい隔離installed CLIの同じSessionへ返却して継続位置の維持を確認した。
実Sessionへの適用条件まで含む項目を、隔離結果だけで全完了にはしていない。
CI-03は別revision/source・部分/未取得・重複返却を検証し、証明範囲を保持した。
CI-04のlive切替とN-04の必要な選択済みrecord変換も、その後通常updaterと
実DBコピーによる読取り確認を経て完了した。実SessionへのStart・Submit・Advance・
復旧・成功済み処理の再実行は含めない。残るCI-02/03の広い実接続条件は保持する。
[実績・制限・証跡](../journal/2026/10/2026-10-23-codex-caller-information-adoption-status.md#implementation-and-isolated-connected-acceptance--2026-10-23)を参照。
