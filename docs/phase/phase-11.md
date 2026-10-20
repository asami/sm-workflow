# Phase 11: 共通Journalの先行実用化と試用スキル接続

status=completed
completed_at=2026-10-20
planned_at=2026-10-20
owner=sm-workflow
execution_priority=before-remaining-phase-6.4-work
checklist=[Phase 11 checklist](phase-11-checklist.md)
design=[共通開発Journal](../design/development-journal.md)

## 位置付け

6.4の試用で直ちに使うための短い独立開発。番号11は7〜10の完了待ちを意味しない。
既存の受入れ済みOperation／Component DataStore／通常review結果を出発点とし、
6.4のarchive・保持管理には依存しない。6.4で検討中の最小Journal案を共通機能として
先行所有する。6.4側で同じstore／writer／読取APIを再実装しない。
Phase 6.3の計画・完了記録は変更しない。この文書はGoal開始や現在の6.4をresetする指示ではない。

## 主経路

```text
既存業務Operationの実際の結果／受理済み指摘
  -> 同じ通常処理で共通Entryを記録
  -> 業務結果・記録状態・Entry ID・短い表示を返す
  -> 試用スキルが転記なしで結果を利用
  -> 別プロセスの公開Operationで検索・読取
```

最初の対象は既存GoalPhaseの受理済みFindingとそのrecording結果。
Step指摘は実際のreview済みStep commit後、Phase指摘は受理済みPhase review後という
既存eligibilityを維持する。単なるAI報告を受入済み指摘へ昇格させない。
ExecutionObservationとFindingの最小型をこの経路に必要な範囲で実装する。

## 順序と完了条件

| Step | 内容 | 終了時に使えるもの |
| --- | --- | --- |
| J1 | 実際の既存契約を確認し、最小CML型・writer・readを一つの通常Operationへ接続する。 | 実結果から保存されたEntryが、別プロセスで同じIDから取得できる。 |
| J2 | Findingの通常受理／記録境界と有限findを接続する。失敗時に業務結果を保持する。 | 指摘転記だけのAI作業なしで、実際の記録と必要な検索ができる。 |
| J3 | 同梱・配備スキルを更新し、6.4の試用hostから通常経路を使い、既存結果を再利用して閉じる。 | 6.4開発チャットがEntry参照・状態表示・公開検索を利用できる。 |

J1の最初から実接続し、全型やhelperの完成後まで主経路を待たせない。
J2の既知の保存失敗・重複source・衝突・不明結果は必要な分だけ同じ経路で検証する。
スキル更新はJ1から準備し、J3を文書だけの配備工程にしない。

## 現在の進行

J1: `06ae0cb`、J2: `af1b6bc`、J3: sm-workflow `32aac7a`／development-workstation `6d18f87`を受入れ済み。J2は対象Finding検証12件成功と変更影響のない既存4スイートの成功結果を再利用し、独立Stepレビューで受入れた。元の5スイート実行の失敗履歴は保持している。J3は対象CLI検証2件・配布検証4件、通常publishLocal、隔離Sessionのinstalled Submitと試用hostの公開find/readを確認し、独立Stepレビューで受入れた。必須の独立Phase全体レビューの命名指摘は機械的改名で解消した。初回全体testの12件失敗と修正後のTestコンパイル失敗を保持し、公開操作一覧・timeout期待値・テスト設定分離と、保存済み中断結果を追加Gitなしで返す接続修正、および集合型変換の訂正を実施した。145件の重点テストと同じ全体review系譜の独立focused reviewで確認後、通常全体testは159スイート・1504件成功、失敗0・aborted0、SBT／wrapper終了0で完了した。既存の明示opt-inが必要なSIGTERM fixture準備1件はcanceledで、実行済みとは扱わない。通常publishLocalとinstalled CLIの公開find/read接続も確認した。この正本・checklistと引渡しを最終releaseコミットへ含める。既存Goal・6.4 Session・履歴を保持し、J3までの累積Step修正5／Phase修正0を保持し、全体テスト修正と集合型変換の訂正の2回を追加した（最終Step修正5／Phase修正2）。命名修正はlogicを変えないM0適合修正として別記録した。共有Phase一覧・戦略の別作業は保持し、当該projectionの同期だけを延期する。

## 引渡しと既存作業

6.4へ公開Operation・型・適用境界・実際の配備場所と取得結果を引き渡す。
6.4の既存Goal/base/Plan、E1受入れ、pending Human、発行済み要求、履歴、修正回数、
検証結果は保持する。通常の採用境界で新しいJournal経路を使い、既存のfile-edit-only
要求を途中で差し替えない。契約変更が必要なら実際の変更範囲を既存所有者で扱う。
未採用状態を引渡し済みと表示しない。6.4の残作業はEvidence保存・圧縮・保持管理等として残る。

## 検証と範囲

実DataStoreへの記録、別プロセス読取、通常公開呼出しと配備済みスキルの利用を確認する。
既存reviewやテストを転記のために再実行しない。実際の指摘を処理したことと、
synthetic fixtureでのエラー確認を区別する。journalだけのAI呼出しゼロを接続経路で確認する。
対象変更に応じたcompile／focused、既存Phaseのreview／release規約を適用し、
helperごとのreviewや別の広範囲検証を追加しない。6〜6.3のfull履歴は再開しない。
SBTは登録済みrunnerとsbt-native lockを使用する。コスト削減値を未測定で主張しない。

Decision／Issue／Handoffの管理、訂正batch、全スキル移行、既存Git文書移行、
Evidence archive、ファイルexport、SIGINT、終了処理変更は本Phaseの対象外。
前者の共通Journal拡張は[Phase 11.1](phase-11.1.md)で独立実施する。
