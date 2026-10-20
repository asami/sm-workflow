# 共通開発Journalの設計

date=2026-10-20
status=planned-not-implemented
owner=sm-workflow

## 目的と実装境界

AIは意味判断を行い、Operationがその受理済み内容と実行事実を記録・検索・管理する。
記録だけのAI転記、ファイル名判断、index更新を通常経路から取り除く。
[Phase 11](../phase/phase-11.md)を6.4の残作業より先に短く実施し、6.4の試用で使う。
[Phase 11.1](../phase/phase-11.1.md)は拡張であり、試用開始の前提ではない。
この計画は実行中のSession、発行済み要求、既存文書の編集権限を変更しない。

## 共通モデル

| 要素 | 意味 |
| --- | --- |
| Entry identity / revision | Runtimeが割り当てる通常の業務IDと履歴revision。承認証明ではない。 |
| Kind / typed content | ExecutionObservation、Finding、Decision、Issue、Handoff。種類ごとに必要な項目を定義する。 |
| Scope | 実際に判明したproject、Phase、Step、Runと対象への参照。不明な値を補わない。 |
| Source / attribution | 元Operation結果・review observation・Human回答と実際の発信者。申告と認証済み帰属を区別する。 |
| Context | 要求対象と実選択対象、関係する適用設定、分岐理由。現在の設定と過去の適用値を混ぜない。 |
| Content | 要約、根拠、採用理由、未解決点、種類別の個別結果。AIの内部思考の保存は要求しない。 |
| Relations | 根拠・訂正元・関連課題・対応修正・検証・commit・Evidenceの参照。 |
| Times | 発生時刻、観測時刻、記録時刻を必要な範囲で区別する。 |

指摘、指摘への対応判断、実際の修正結果は別の意味として参照で結ぶ。
既存の実行結果・review・Workflow・Evidenceの正本をJournalへ移さず、必要な情報と
参照を記録する。共通モデルはテーブル数や汎用イベント基盤の新設を要求しない。
Phase 11ではExecutionObservationとFindingのみを実装する。他の型は11.1の設計対象であり、
未実装Operationや受入不能な種類を公開済みと表示しない。

## 記録と結果の境界

通常の業務Operationは受理済みの意味判断／自身の実行結果から共通writerを呼び、
業務結果、Journal記録状態、実際のEntry参照、短い表示をまとめて返す。
低水準helper、読取、status poll、Journal管理自身を逐一再帰記録しない。
スキルは元の結果で意味判断を一度返し、記録のための再要約をしない。
共通型で表せない内容だけを宣言済みの任意拡張とする。

業務結果、Journal書込み結果、review／acceptance、Evidence取得可否は別の事実とする。
業務成功後にJournal保存が失敗しても、その業務効果を再実行しない。
同じUoWを使っても外部効果との原子性を仮定しない。設定済みComponent DataStoreの
実際のcreate／query／conditional update／transaction契約に沿う。
記録権限は既存の有効化境界で設定し、読取Operationへ暗黙の書込みを追加しない。

同一source/resultへの再参照では既存Entryを利用し、内容が矛盾するなら衝突として扱う。
内容hashによる同一性判定、別台帳、AIの未使用証明は加えない。
結果不明の書込みを盲目的に再実行せず、実際のsourceとEntryの保存関係を確認する。
必要なproducer能力が欠ける場合は、その具体的な境界を実装・検証し、成功形の代用をしない。

## 検索・提示・履歴

公開名はCML化時に確定する。既存案のreadJournalEntryとfindJournalEntriesをPhase 11で
接続し、ID／revision読取、scope／kind／source／期間による有限の検索を提供する。
通常応答は要点と参照、詳細は選択された項目・件数に限定する。全ログや全履歴を毎回AIへ返さない。
この表示上の制限を保存情報の削減にしない。Operationが実際に把握した対象・適用設定・
分岐理由・個別結果・失敗情報は、必要な型付き詳細と元結果への参照として保持する。
後からAIが再構成した要約より詳しく有用な記録を、そのまま共有・再読できるようにする。
型付きの補足・訂正・課題状態更新と有限batchは11.1でmanageJournalEntriesへ接続する。
訂正は元revision・理由を保存し、競合を黙って上書きしない。batchは個別結果を返す。

構造化Entryを新規管理記録の正本とし、Markdownは導出表示とする。
既存Git文書の自動移行・削除・dual writeはしない。新規適用境界を明示する。
検索結果は意味判断の材料であり、適用可能性・Human consent・Workflow進行権限を生成しない。

## Phase 6.4との独立性

Phase 11は既存のOperation結果やreview参照だけで成立させ、6.4のEvidenceRecord、
archive、実保存先取得、圧縮、保持管理の完成を要求しない。
Evidence参照がなければ不在のまま保持し、未実装archiveや架空のEvidence IDを作らない。
6.4は引渡されたJournalを再利用してarchive結果を記録する。
既存のpending Humanや発行済みfile-edit-only要求は書き換えず、通常の契約採用境界で接続する。

## スキルと配備

同梱sm-goal-phase、sm-repository-sync、sm-split-phaseの共通説明を整合させる。
実際に対応済みの結果からEntry参照・記録状態を表示し、必要時だけ公開検索を使う。
全スキルに新たな記録義務を課さない。既存の独立review／Human／Submitの責務は維持する。
実際の配備元とインストール先を既存手順で解決し、同梱版・配備版を整合させる。
6.4試用hostで実際のOperation呼出し・読取が通ることまでを先行引渡しの範囲とする。
ソース文面の変更だけを試用可能としない。既存Sessionの互換adapterや強制restartは作らない。

関連: [契約案](../spec/development-journal-proposal.md)、
[用途精査](../spec/development-journal-skill-usage-inventory.md)、
[判断ノート](../notes/development-journal-delivery.md)。
