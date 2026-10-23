# host用語をネットワーク接続先に限定する採用計画

Date: 2026-10-23
Status: connected source, isolated CLI and normal format/runtime adoption complete; remaining purpose-specific wording pending
Owner: sm-workflow開発
Spec: [runtime/caller用語規約](../spec/runtime-caller-terminology.md)

## 位置付け

新Phase/Goalを開始せず、現在の呼出元情報取得CI-01〜04と共有する境界から採用する。
今回の文書更新で進行中の実装・発行作業・Sessionや既存の検証結果は変更しない。
Phase 6.4/6.5全体の再計画・既存受入れのやり直しにはしない。
Phase 13もcaller/runtimeを使う。用語整理全体の完了を、その一件の主経路の不要な
前提にせず、接続する両端の名前は同時に揃える。

## 実行順序

1. **N-01 対応表と使用箇所。** 正本の具体名を起点にCML、Scala、CLI parser/
   launcher、設定、wire、保存済み選択、installer/配布skillの利用箇所を確認する。
   実行マシン・caller・runtime root・repository root・issuerを混ぜない。進行中の
   差分を読み、旧providerを新名で完成させず撤去予定として扱う。
2. **N-02 主経路。** CI-01/02に必要なcaller request/resultとexecution bridge、
   passive preparation service、`--runtime-root`、runtime設定を一つの接続範囲で
   更新する。登録済みスキル・配布元・通常launcherも合わせる。型/生成API/全call site
   を更新し、旧名のaliasを残さずcompileで取り残しを検出する。
3. **N-03 残る目的別境界。** review/Human/authority context、input-records、
   external-evidence、package、診断/help/現行文書を対応表に揃える。既存保存先は
   名前変更だけで移動しない。更新済み経路は旧名fallbackを持たず、未切替経路の
   作業を勝手にリセットしない。
4. **N-04 同一Sessionへの採用。** 必要な選択済みrecordだけ明示的に一度変換し、
   元の記録・相関・業務値を保持して通常readerは新形式だけにする。既存呼出しの
   終了を待って影響する設定/runtimeを切り替える。隔離fixtureで引継ぎを検証してから
   実際の継続先へ採用する。適用可能な旧検証結果を再利用し、新しい名前で未検証の
   成功を主張しない。

development-workstationの配布元と各実行環境の選択済み設定も変更対象の接続先として
列挙する。dev????等のディレクトリ名やユーザー固有パスはidentityではない。
外部Codexツールの`hostId`等は変更しない。既存のネットワークhostname/URI hostも維持する。

## 完了条件

- [x] N-01: 非ネットワークhostの型/設定/CLI/wire/保存/配布の対応表と実利用者が確認できる。
- [x] N-02: CMLからの通常生成とコンパイルが通り、旧ソース名のalias/forwarderがない。
- [x] N-02: 実際のcaller要求→観測返却→同じSession継続を新名の主経路で確認する。
- [x] N-03: CLI/help/設定/生成DTO/配布skillが一致し、旧`--host`や旧keyを利用するfallbackがない。
- [ ] N-03: Host文字列の残留を目的別に確認する。ネットワーク・外部API・履歴・改訂対応表の記載を機械的な全削除対象にしない。
- [x] N-04: 実際の影響形式に対する引継ぎfixtureで、Session/continuation/Step・修正回数・受入れ・証跡参照を保持する。
- [x] N-04: 必要な一回限りの変換とruntime切替を記録し、通常経路に旧形式readerを残さない。
- [x] N-04: 現行文書/開発handoffに利用可能範囲と未確認点を記録し、既存検証の適用範囲を維持する。

検証は変更した接続のfocused試験、通常生成/compile、代表的な継続の一往復を中心にする。
履歴保存のために全テスト・全レビューを再実行しない。名前変更を口実に追加承認層、
管理hash、lease、独自snapshot storeや汎用migration engineを導入しない。

引継ぎ: [2026-10-23 journal](../journal/2026/10/2026-10-23-network-host-terminology-handoff.md)。

## 2026-10-23の実績対応

N-01の対応表とN-02の接続は実装・通常生成/compile・実caller観測を使った
隔離installed CLIで確認済み。N-03の主経路は新名で一致し、非接続箇所の
目的別表現の整理を残す。N-04はその後、実DBコピーの履歴読取り、元データを残す
22件の新形式投影、両実行先の設定変更、通常updaterによる切替と両rootのHelpで
確認済み。実Sessionの進行・復旧やHuman判断はこの切替に含めない。
[実績記録](../journal/2026/10/2026-10-23-network-host-terminology-adoption-status.md#implemented-and-verified-connected-boundary--2026-10-23)を参照。
