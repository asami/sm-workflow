# Journal正規化とPhase 6.4の開発引継ぎ

## 人間から引き継いだ方向

継続チャットから、人間の「開発をsm-workflow開発に引き継いで」という明示的な
依頼と、新しいjournal方針を受け取った。保存・参照・更新はsm-workflowの公開Operation
と実行基盤が担当し、スキル／AIは記録の意味判断、入力、実際の結果利用を担当する。
具体API・保存形式・移行方式は、この方向だけでは採択済みとしない。
送り元は並行編集・Workflow操作を終了する旨を明示している。

[契約案](../../../spec/development-journal-proposal.md) は、新しい構造化Entryを
既存の設定済みComponent datastoreで管理し、Markdownを表示にする案を具体化した。
EntryとEvidenceをID参照で結び、証拠の保存先・表現・保持期間の正本をEvidenceRecordに
残す。現在のavailabilityはlookup時点の観測とし、test結果、review／acceptance、
storage結果、journal書込み結果を区別する。journalはHuman consentやWorkflow状態を
代替しない。既存Git文書の移行・廃止・削除は行わない。

初回接続はexecution-evidence entryの実際の記録とfresh-process読取を対象とする。
設計判断・課題・引継ぎの共通項目は案に含めるが、すべての汎用機能、bulk移行、
file exportをE2の新たな完了条件として追加しない。

## 実際の継続地点

元チャット：`01a1555d-b4a7-7be3-8232-5396c2f5e293`。
継続チャット：`01a156b7-d60e-7cb3-a0bb-c2ce483b22f9`。
Session：`96c294d73be3d48067f3721d8a2abf33`。
実行hostは `sm-workflow-dev`。この記録と契約案は、所有開発リポジトリ
`sm-workflow` 側の変更であり、feature checkoutは編集していない。

E1は独立review、通常Submit、必要なhost準備、通常native Step closureを経て
commit `07a97986cbee9ffe71fdf69b89a60eff98768134` で完了している。
既存のCompile、review、受理済みSubmitを再実行しない。
前のinstalled helper引渡し記録にあるStartupImport未設定は、その時点の報告であり、
現在地はその後のE1 closureとE2 dependency Humanへ進んでいる。

現在の実際のfrontierは保持済みtaskの
`e2-011-complete/advance/response.json` にあるHuman。
continuationは `96c294d73be3d48067f3721d8a2abf33:ordered-human:26`。
対象は `P64-E2-public-file-location@1` のCNCF既存2ファイルだけである。
4択はApproveDependencyScope／RejectDependencyScope／Defer／Cancelのまま。
開発引継ぎもjournalの設計方向も、この質問への回答として扱わない。
同じ保存済み質問への実際の回答を新所有チャットで求めており、まだ送信していない。

## 採用経路の確認と用意した候補

公開済みE2 Requestの `work.selected_plan` を読んだ。Plan revision 1のE2には
CML／Scala、証拠保存仕様、Phase／checklist／既存journalがowned pathsとして含まれる。
E2 purposeは実際のjournal記録を含み、E3のjournal失敗分離とE4の指示更新も既存scopeに
含まれる。最小のruntime経由journal記録は、E2の限定revisionで扱える可能性がある。
全汎用journal機能の追加を、この範囲への暗黙の拡張とはしない。

`target/journal-normalization-20261020/bounded-plan-revision-candidate.json`
に完全なraw Planの候補を用意した。これは未発行・未提出・未採択であり、現在の
Humanも保持済みPlanも変更していない。revisionを2とし、E2 purposeだけにruntime
経由の記録とfresh-process Entry読取を明記した。E1／E3／E4、所有範囲、依存関係、
check identity、Phase checks、修正上限、raw Usage Contractは元の値を保持した。
候補作成時の構造比較はnative captureの受入れ証跡ではない。

現在の依存Humanが実際に回答され、通常経路でE2へ戻った後、新しいdecision-neededと
独立したPlan revision質問を用いる。依存案とrevision案を同じ結果へ入れない。
採用前に、実際の候補と選択済み文書の組が通常captureの境界を通ることを確認する。
他Stepのbinding・チェック済みflag・受理済み結果を維持し、E1文書の方針変更は
採択後の追記として扱う。過去のE1受入れを新契約の実装済み証明へ読み替えない。

「実際のAI journal記録」をruntime-mediated記録とする明示的な解釈が、保存済みUsage
Contractの変更を必要とする場合は、既存の限定revisionでは扱えない。その制約を報告し、
所有runtimeで正式な変更契約を設計する。guard削除、質問の差替え、native store編集、
新Session、resetで回避しない。今回、その追加runtime機構は実装していない。

## 依存先と保持事項

CNCFのpublic File結果には依然target／pathと内容またはoutcomeだけがあり、実際の
absolute保存先は公開されていない。journalをID参照にしても、人間向けの実host／
保存先要件は維持するため、この不足を消去したとは扱わない。
旧2ファイル案の適否は公開されたadmitted locationのproducer contractとして判断し、
私的binding、推測HOME、別display設定、NIO／shellへ逃がさない。
今回CNCFには編集していない。

Goal、base、Plan epoch、修正回数0／0／10、受理済み結果、finding／実行／失敗履歴を
保持する。本引継ぎ後に実SessionのStart／Submit／Advance、SBT、実provider呼出し、
publishLocal、commit／pushは行っていない。保留中の終了処理変更も混ぜていない。
機能開発checkoutの既存4文書変更はそのまま保持している。

次の実作業は、実際のHuman回答の受入れと通常継続、その後の限定した契約採用である。
採択後にCML／current-call datastore／証拠参照の実装とfocused検証へ進む。
P64-01..18、one-file／fresh-process実接続、失敗分離、batch／compression／retention、
独立Phase review、全体検証、通常releaseは未実施分を含めそのまま残る。

## 追加の直接指示：共通記録と管理をOperation内に寄せる

人間からさらに、共有できるjournalはできるだけOperation内で取得し、スキル固有の
journalは任意追加とすること、管理もOperationで一括してAIコストを抑えることが
指示された。契約案をこの方向へ更新した。

通常の業務Operationが自身の実結果・文脈・証拠参照から共通Entryを記録し、業務結果と
journalの実際の記録状態／参照をまとめて返す。AIによる追加のrecord呼出しやindex／
Markdown／保持情報の管理を必須にしない。スキル固有の意味判断や補足は許可された
typed入力の任意部分とし、共通Entryの実結果・権限・証拠を上書きさせない。
検索・訂正・選択したlifecycle処理もOperationの責任とし、必要な一括操作は有限の
typed batchと実際のper-entry outcomeで扱う。journal管理自体の再帰記録やAI pollingを
増やさない。業務効果とjournal保存の失敗分離、既存文書の保持、証拠TTLとの区別は維持する。

AIコスト削減は呼出し回数・管理判断・入出力往復を減らす設計上の狙いであり、効果を
実測済みとは記録しない。この直接指示も、現在の別件Humanへの回答として扱わない。
先の「AIがJournal Operationへ常に別送する」候補は更新対象であり、履歴として残す。
更新候補は `target/journal-normalization-20261020/bounded-plan-revision-operation-journal-candidate.json`。
Proposal revision 2だが、提案内のPlan revisionは未採択の2のままであり、実際のPlan epochを
進めたものではない。前候補からE2 purpose以外のPlanフィールドを保持した。

## 実際のスキル用途の精査と情報量

人間の追加指摘を受け、3つのsmスキルとjournalへ言及するCNCFスキルの該当箇所、
typed review projection、製品のfinding recording契約／実装を読み、
[用途の対応表](../../../spec/development-journal-skill-usage-inventory.md)を作成した。
HYG／DEV、review結果、判断理由、候補の配置、引継ぎ、実行／commit関係は共通化候補。
AIが意味判断した内容でも、スキル固有の保存形式にする必要はない。
特にsm Goal V7のfile-edit-only recording Requestは、既存typed observationを使って
Operation側へ移せる具体候補である。ただし発行済み要求や既存Sessionの定義を
置き換えず、Step commit／Phase review後という実際のeligibilityも維持する。

Operationが保有する実対象、関連する設定／policy、分岐理由、個別検証結果、時刻、
失敗／未確定情報、実際のresult／artifact／Evidence参照を構造化して残す方針を
契約案へ追記した。短いAI要約だけへ縮退させない。人間には要点、利用側には同じ正本の
詳細／検索viewを提供する。未取得情報を捏造せず、raw証拠をGitへ複製しない。
共通化率やAIコスト削減の実測値はまだなく、今回スキルの変更／配布は行っていない。

## 後続の直接指示：6.4より先行する独立Phaseと試用スキル

ユーザーは「6.4の前にゲリラ的に開発」「6.4はsm-workflowの試用なのでスキルも対応」
を指定した。[Phase 11](../../../phase/phase-11.md)を短い先行開発として新設し、
共通Entryの保存・読取・検索、受理済みFindingの記録、同梱／配備スキルと実試用接続を所有させる。
上記のE2 purpose修正候補は未採択の履歴として保持し、共通Journal基盤をE2内で
作り始める指示としては使わない。6.4は先行成果を再利用し、Evidence保存等を継続する。
[Phase 11.1](../../../phase/phase-11.1.md)は判断・課題・引継ぎ／訂正／batchの拡張で、
6.4試用開始を待たせない。現在の質問や発行済み契約、Goal・履歴・修正回数・検証結果は保持する。
最新の作業指示は[先行Phase handoff](2026-10-20-common-journal-phases-handoff.md)を参照する。
今回の更新は計画・設計文書のみで、製品コード、実スキルの編集・配備、Workflow操作は未実施。
