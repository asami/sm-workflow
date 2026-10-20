# 共通Evidence Phase 12とPhase 6.4の責務分担・両開発チャットへの引継ぎ

date=2026-10-20
status=adopted-plan-with-minimum-producer-delivery

## 採用方針

ユーザーはJournalと同様のEvidence標準化、Advance内での自動記録、
独立Phase 12をsm-workflow開発で実装する方針を採用した。
[Phase 12](../../../phase/phase-12.md)と[チェックリスト](../../../phase/phase-12-checklist.md)、
[共通仕様](../../../spec/common-evidence.md)を作成し、[6.4](../../../phase/phase-6.4.md)を
共通機能の採用・実開発での運用検証に整理した。
両checkoutに同じ共通計画・仕様・handoffを置くが、Phase 12の実装所有者は一つである。
これは文書の採用であり、native Plan採用、Human回答、実装・配備・接続成功ではない。

| 所有 | 範囲 |
| --- | --- |
| Phase 11 / 共通Journal | 実行事実・判断・結果のEntry管理。既存成果・受入れを保持する。 |
| Phase 12 / sm-workflow開発 | 共通Evidenceモデル、実ログの保存・取得、Advanceでの自動producer、共通Journalへの接続、最小失敗処理と配備。 |
| Phase 6.4 / sm-workflow-dev開発 | 先行成果の採用、実開発でのE2、E3の運用失敗、E4のbatch・圧縮・保持・hold・dueと最終受入れ。 |

通常経路はActionの実結果・生成物からEvidenceを自動保存し、その参照をJournalに記録する。
AIに通知・命名・転記のためだけの呼出しを追加しない。明示登録は外部資料などの補助経路。
Journal IDとEvidence IDが公開参照であり、絶対パス表示APIやAIによるMarkdown編集は
必須条件から外す。実際の物理保存と命名は残り、既存CNCF管理I/Oが所有する。
JournalとEvidenceは別の正本だが、6.4専用の別writer/storeは作らない。

## sm-workflow開発向け

1. Phase 12は11.1や7〜10を待たず短く先行する。既存Journal実装・検証と現在の別作業を保持。
2. 最初から実際のテストAction一種類・UTF-8ログ一件を使い、Advance -> Evidence保存 ->
   Journal記録 -> 別プロセス取得を通す。抽象APIだけを6.4に引き渡さない。
3. 既存JournalはFinding/recording結果のproducerが対象で、Evidence用producerはまだない。
   正規の型付き保存観測を共通Journalへ追加し、Findingのeligibilityを偽装・緩和しない。
4. 同じ実結果・生成物の参照を再利用する。保存・Journal失敗のために元のtest/Actionを
   再実行しない。最低限の失敗・不明結果・Journalだけの継続を既存経路で確認する。
5. 通常の検証・review・release・配備を行い、実API、型、対象producer、host設定、
   artifact identity、結果と限界を6.4へ渡す。実行記録はGitにコピーしない。
6. 後続6.4のbatch/圧縮/保持機能も共通サービスの拡張として扱う。6.4所有者と変更ファイル・
   必要なproducer作業を合意し、同一コードへの競合編集やサービスの二重実装を避ける。

## sm-workflow-dev開発向け

1. 既存6.4のGoal/Session/base/Plan、E1受入れ、pending Human、履歴、修正回数、
   成功・失敗の検証結果と未コミット作業を保持する。6.4を最初からやり直さない。
2. 文書の採用は今回のユーザー指示で確定。現在のnative frontierを確認し、同じSessionの
   正規経路で残作業への採用を処理する。この文書をHuman回答やdecisionReferenceにしない。
   不要となった絶対パスAPIの承認を再度求めず、既存の判断を再利用する。
3. 現行runtimeが受入れ済みStep以降の変更を表現できない場合は、具体的な制約をruntime
   所有者へ渡す。Plan/database直接書換え、counter reset、架空の承認、再Startはしない。
   手続上の制約を共通Evidence機能の要件に混ぜない。
4. E1のP64-01〜03と受入れ記録は過去の設計受入れとして保持。E2〜E4のIDとunchecked状態も
   保持する。今回の未来の受入れ文言は、nativeで適用されるまで実行済み契約とは扱わない。
5. Phase 12の最小実装を採用し、E2で実際の開発ログから自動保存・Journal参照・再取得を
   確認する。producer検証は再利用し、6.4固有の接続だけを追加検証する。
6. E3で失敗と記録継続を、E4でbatch/圧縮/保持/hold/dueを同じサービス上で完成する。
   必要なCNCF directory/compression能力は別途具体化し、絶対表示パス取得とは分離する。
7. Phase 12完了を6.4完了に読み替えず、既存のPhase検証・review・closureを実結果で閉じる。
   6.5の計画と6.3の受入れには変更を加えない。

## 保持した事実と実行境界

調査時の試用checkoutはsm-workflow-devのdevelopmentブランチ。
既存Sessionは `96c294d73be3d48067f3721d8a2abf33`、E1受入れcommitは
`07a97986cbee9ffe71fdf69b89a60eff98768134`。task-privateの
`e2-012-journal-id-reconsideration.md`は未提出案であり、ordered-human:26と旧提案を保持している。
この記述は調査時点の参照であり、実行再開時の現在地は所有者が確認する。
旧提案の絶対パス表示・AI直接編集という設計動機は今回の仕様で置き換えるが、
旧質問・結果・受入れ履歴は削除しない。両checkoutの6.4進捗表示の差も完了推定で上書きしない。

初回の計画作成時に行ったのは仕様・計画・handoffの編集のみ。Start/Submit/Advance、テスト、配備、
コミット、別チャットへの送信は行っていない。Phase 12の項目は全て未実施。

## 先行producerの引渡し

現在の実装・通常ローカル配備・公開操作・残作業採用手順は
[producer delivery](2026-10-20-phase-12-producer-delivery.md)を参照する。
上記の初回未実施記録は履歴として保持する。6.4の実Session採用・E2〜E4受入れ、
Phase 12全体の最終ローカルclosureは、この先行配備とは区別する。
