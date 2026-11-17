# 共通開発Journalの設計

date=2026-10-20
status=single-entry-source-implemented-validation-pending
owner=sm-workflow

## 目的と実装境界

AIは意味判断を行い、Operationがその受理済み内容と実行事実を記録・検索・管理する。
記録だけのAI転記、ファイル名判断、index更新を通常経路から取り除く。
[Phase 11](../phase/phase-11.md)を6.4の残作業より先に短く実施し、6.4の試用で使う。
[Phase 11.1](../phase/phase-11.1.md)は判断・課題・引継ぎの単件主経路、
[Phase 11.2](../phase/phase-11.2.md)は後続のbatch管理・利用経路拡大を所有する。
いずれも受入れ済みPhase 11の試用開始の前提を後付けしない。
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
Phase 11のExecutionObservationとFindingの不変payloadは維持する。
11.1の単件source実装はDecision／Issue／Handoffを同じJournalへ追加する。
実行検証・独立review・実利用一件と配備引渡しは親Workflowの後続境界であり、
source実装だけを検証済み・配備済みと表示しない。

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
型付きの補足・訂正・課題状態更新は11.1で単件の管理経路へ接続する。
有限batchと追加利用先への展開は11.2へ移し、既存案manageJournalEntries等の公開名・
接続は各Phaseで既存Operationに合わせて確定する。11.1の完了はbatch対応を待たない。
訂正は元revision・理由を保存し、競合を黙って上書きしない。batchは個別結果を返す。

構造化Entryを新規管理記録の正本とし、Markdownは導出表示とする。
既存Git文書の自動移行・削除・dual writeはしない。新規適用境界を明示する。
検索結果は意味判断の材料であり、適用可能性・Human consent・Workflow進行権限を生成しない。

## Phase 6.4との独立性

2026-10-20の責務更新: [Phase 12](../phase/phase-12.md)が
[共通Evidence](../spec/common-evidence.md)の保存・取得とAdvanceからの自動記録を先行所有する。
6.4はその採用・運用検証とbatch/圧縮/保持拡張を行う。Phase 11の既存producerと受入れは
保持し、12がEvidence保存観測の正規producerを同じJournalへ接続する。
Journal自体はEvidence完成を待たず利用でき、Evidence参照なしの既存Entryもそのまま有効。
AI直接Markdown編集や絶対表示パス取得を新経路の必須条件にしない。

Phase 11は既存のOperation結果やreview参照だけで成立させ、6.4のEvidenceRecord、
archive、実保存先取得、圧縮、保持管理の完成を要求しない。
Evidence参照がなければ不在のまま保持し、未実装archiveや架空のEvidence IDを作らない。
Phase 12は引渡されたJournalを再利用してarchive結果を記録し、6.4がその実経路を採用する。
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

## Phase 11.1 単件source実装

CML所有の追加DTOとJournalServiceのrecordJournalEntry、correctJournalEntry、
updateJournalIssueState、readJournalSemanticEntry、findJournalSemanticEntriesを定義する。
既存DTOのABIと不変Entry読取を維持するため、選択semantic詳細は追加の型付き読取を使う。
共通findJournalEntriesもsemantic kindを受け、同じ有限summaryを返す。
具体的なinput、状態、結果と制限は[実行契約](../spec/development-journal-runtime.md#phase-111-semantic-single-entry-operations)に記す。

semantic Entryは元sourceとkindから可逆な業務IDを持つ。一行内に元内容から連続する
全revision・出所・変更時刻・訂正理由を保持し、現在のmanaged purposeで既存DataStoreの
compare-and-setを使って期待revision付きで置換する。共通collectionの既存不変Entryは
文字列revision列を使うため、semantic行には別の整数semanticRevisionを保持する。
providerはこの整数を条件付きで進め、同じ置換で公開revision・内容・履歴を更新する。
読取は整数tokenと履歴の一致を確認し、欠落や不整合を推測補完しない。
元scope／kind／出所は変更しない。
訂正はIssue状態を変えず、状態変更は別Operationで行う。検索用の状態と有限16件の関連IDは
同じ行の現在内容から導出し、検索時にも一致を検証する。別の台帳や独自lockは設けない。

Finding／Decision／Issue／Handoffのlinkは対応kindのcanonical IDとrevisionを保持する。
Fix／Validationは既存所有者の結果参照を保持し、Journalへ新たな正本を複製しない。
linkの形の検証は参照先の実在・認証・受入れを証明しない。意味記録と検索はHuman回答、
review acceptance、計画採用、Workflow進行を生成しない。通常読取は選択revision一件だけを
返し、全履歴の反復転記を要求しない。batch管理・利用経路の追加は11.2に残す。
