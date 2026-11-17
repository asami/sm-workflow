# 共通Journal先行開発の判断ノート

date=2026-10-20
status=planned

## 決定

6.4の完成を待ってJournalを拡張する当初案を変更し、[Phase 11](../phase/phase-11.md)を
6.4の残作業より先に短く実施する。6.4はsm-workflowの実際の試用でもあるため、
共通保存・検索だけでなく試用スキルの更新・配備・実接続までをPhase 11に含める。
番号11は7〜10の後という意味ではない。関連機能を全部一度に仕上げることも要求しない。

| 所有 | 範囲 |
| --- | --- |
| Phase 11 | 共通Entry、実結果／Findingの記録、read／find、同梱・配備スキル、6.4試用への引渡し。 |
| Phase 12 | 共通EvidenceのID・保存・取得とAdvanceからの自動記録。Phase 11のJournalへ実保存観測を接続する。 |
| Phase 6.4 | Phase 12の採用・実運用検証。共通サービス上でbatch・圧縮・保持管理を拡張する。 |
| Phase 11.1 | Decision／Issue／Handoff、単件の関係検索・訂正・状態更新と一つの実利用経路。 |
| Phase 11.2 | 11.1の単件機能を使う有限batch・個別結果管理と選択した追加利用先への展開。 |

2026-11-11の追加採用：旧11.1を上記二つへ分け、11.1はPhase 7の後・8の前に実施する。
11.2は実利用で必要になった時に別途選び、8／9／18の前提にしない。
[分割記録](https://github.com/asami/sm-workflow/blob/9debf6484e3e16e6739b83688a30a814772c7b29/docs/journal/2026/11/2026-11-11-journal-single-entry-and-batch-split.md)に旧項目の移管を残す。

## この分割の理由

レビュー指摘の転記は、既に型付きの意味判断結果が存在するため機械処理へ移しやすい。
そこで一件の実記録・別プロセス読取を先に通す。Evidence保存が未完成でも、既存の
review／Operation結果参照でJournalを使える。偽のEvidence IDを挿入する必要はない。
全種類・全管理機能を先に作る案は初期効果を遅らせるため11.1へ分ける。

構造化記録は詳しく保持し、AIへの通常応答は短くする。明示された項目・件数で詳細を検索し、
読取のたびに全履歴を返さない。AIの意味判断は共通型で受け、追加のjournal担当を呼ばない。
実際の入力token／呼出し／転記回数を既存の利用結果から確認し、削減量の未測定値は示さない。

## 保持と採用

2026-10-20の追加採用: [Phase 12](../phase/phase-12.md)をsm-workflow開発で先行実施し、
6.4のE2へ実保存まで通った最小経路を渡す。全Action・媒体・管理機能の完成を待たない。
AIからの通知・転記は既知のAction生成物には不要。明示登録は外部資料などに残し、
公開参照はJournal/Evidence ID、物理保存は既存CNCF管理I/Oとする。
[両開発チャット向けhandoff](https://github.com/asami/sm-workflow/blob/9debf6484e3e16e6739b83688a30a814772c7b29/docs/journal/2026/10/2026-10-20-phase-12-common-evidence-handoff.md)を参照。

これは設計と計画の記録であり、6.4の現在の質問へ回答したりSessionを変更したりしない。
開発checkout／試用hostの実際の進捗を所有チャットで確認し、既存Goal／E1受入れ／
修正回数／検証結果を保持して通常の採用境界で使う。古い文書上のstatusを理由に進捗を戻さない。
現在発行済みのfile-edit-only要求はその契約のまま扱う。新しい要求への切替は正規の境界で行う。
6.3の計画は変更しない。終了処理・SIGTERM実装を混ぜず、SIGINT制御返却は将来候補のまま。

関連: [設計](../design/development-journal.md)、[用途精査](../spec/development-journal-skill-usage-inventory.md)、
[引継ぎ](https://github.com/asami/sm-workflow/blob/9debf6484e3e16e6739b83688a30a814772c7b29/docs/journal/2026/10/2026-10-20-common-journal-phases-handoff.md)。


## Phase 11.1 単件APIの利用手引き（2026-11-17）

上記のPhase 11／12の判断・引渡し履歴を保持した追加手引きである。
P111-01-04のsource実装はcommit `3d1db94917c880c9805fe84dd4e1403bca9b2124` に
受入れ済み。ここに示す入力例は説明用であり、P111-05-06の実利用・別プロセス読取・
独立review・Phase closureの成功記録ではない。その実結果は所有callerが別途保持する。

### 呼出しとhostの前提

既存のCNCF CLI／REST／configured Operationから `JournalService.<operation>` を呼ぶ。
CLIでは各Operationの唯一の `input` 引数に、下表のJSON objectを `--input` で渡す。
Operation名とJSON field名の大文字小文字を保ち、任意項目は省略する。
入力にstore／collection／SQL／pathは渡さない。
[実行契約](../spec/development-journal-runtime.md#fixed-host-configuration)の
`sm-workflow.journal`、同じnamed managed purpose／collection、ComponentのCreate scopeと
読取権限をhostに設定し、別プロセスでもそのhost設定を使う。

hostは起動時のglobal DataStoreと後段のmanaged Workflow storeも整合させる。
StartupImportを使う場合、既存の `textus.datastore.kind`／`textus.datastore.path` と
managed Workflow bindingが同じ意図したresourceを解決するよう明示する。
CAR defaultsだけで早期global storeも選ばれたとは推定しない。Journalのnamed purposeは
その設定とは別に確認する。権限検査を通した通常bindingを使い、DBのコピーやmemoryへの
切替で不一致を隠さない。これは既存host設定の前提であり、新たな自動設定機能ではない。

### 七つのOperationと正確な入力

下表の `?` は省略可、`[]` は配列、他は必須。`string` のrevisionもJSON文字列で渡す。
これは [CML](../../src/main/cozy/sm-workflow.cml) の公開DTOに対応する。

| Operation（入力型） | JSON fields | 結果型 |
| --- | --- | --- |
| `findJournalEntries` (`JournalFindInput`) | `repositoryIdentity: string`, `phaseIdentity?: string`, `sessionIdentity?: string`, `kind?: string`, `sourceReference?: Reference`, `recordedFrom?: string`, `recordedBefore?: string`, `limit?: int` | `JournalFindResult` |
| `readJournalEntry` (`JournalReadInput`) | `identity: string`, `revision?: string` | `JournalReadResult` |
| `recordJournalEntry` (`JournalSemanticRecordInput`) | `kind: string`, `repositoryIdentity: string`, `phaseIdentity: string`, `stepIdentity?: string`, `sessionIdentity: string`, `sourceReference: Reference`, `content: Content` | `JournalRecordingState` |
| `correctJournalEntry` (`JournalSemanticChangeInput`) | `identity: string`, `repositoryIdentity: string`, `expectedRevision: string`, `sourceReference: Reference`, `reason: string`, `content: Content` | `JournalRecordingState` |
| `updateJournalIssueState` (`JournalIssueStateInput`) | `identity: string`, `repositoryIdentity: string`, `expectedRevision: string`, `sourceReference: Reference`, `reason: string`, `state: string` | `JournalRecordingState` |
| `readJournalSemanticEntry` (`JournalSemanticReadInput`) | `identity: string`, `revision?: string` | `JournalSemanticReadResult` |
| `findJournalSemanticEntries` (`JournalSemanticFindInput`) | `scope: JournalFindInput`, `state?: string`, `relatedIdentity?: string` | `JournalFindResult` |

`Reference` は `WorkflowArtifactReference` の完全な
`{"identity": string, "revision": string}`。
`Content` は `JournalSemanticContent` の
`{"summary": string, "rationale": string, "links": Link[], "state"?: string}`。
`Link` は `JournalSemanticLink` の `{"kind": string, "reference": Reference}`。
summary／rationale／変更reasonは空白だけにできない。scopeと参照は空白の補正を期待せず
正確な値を渡す。semantic kindは `Decision`／`Issue`／`Handoff`。
Issueは必ず `open`／`inProgress`／`resolved`／`closed` のstateを持ち、他kindはstateを省く。
linksは0〜16件で同一linkの重複は不可。link kindは
`Finding`／`Decision`／`Issue`／`Fix`／`Validation`／`Handoff`。

Findingは不変Journalの実Entry参照（revision `"1"`）、Decision／Issue／Handoffは
該当kindの実semantic Entry参照を使う。Fix／Validationは既存所有者の実結果参照を渡す。
参照先を複製した正本や架空のEvidence IDを作らない。形・kindの検証は参照先の実在、
認証、意味の正しさや受入れを証明しない。必要な詳細は各所有者の公開読取で確認する。

共通findのkindは上のsemantic三種と既存の `ExecutionObservation`／`Finding`／
`EvidenceStorageObservation`／`StepAssessment`／`MergeAssessment`／`SplitRouting`。
source filterは元sourceの完全tupleを指定する。timeはwhole-second UTC
（例示形式 `YYYY-MM-DDTHH:mm:ssZ`）、`recordedFrom` 以上、`recordedBefore` 未満。
両方指定するときは前者を後者より前にする。limitは1〜20、省略時20。
state filterはkind省略または `Issue` に限る。relatedIdentityは現在のlinksの参照先identityへの
完全一致であり、逆向き検索はcallerが必要なscopeで明示する。推移的なgraph探索ではない。

### 実結果からつなぐ一件の経路

以下は既存callerが使える値の組立て例である。`findingRef`／`decisionSource`／
`issueSource`／`handoffSource`／`changeSource` とscope／本文は、実作業の公開結果と
意味判断からcallerが供給する変数であり、測定済みの結果や実行scriptではない。

```javascript
// Illustrative object construction only; every reference comes from actual returns.
const decisionInput = {
  kind: "Decision", ...scope, sourceReference: decisionSource,
  content: {summary: decisionSummary, rationale: decisionRationale,
    links: [{kind: "Finding", reference: findingRef}]}
};
// After recordJournalEntry succeeds, use decisionResult.entry unchanged.
const issueInput = {
  kind: "Issue", ...scope, sourceReference: issueSource,
  content: {summary: issueSummary, rationale: issueRationale, state: "open",
    links: [{kind: "Decision", reference: decisionResult.entry}]}
};
// After the Issue succeeds, link the Handoff to issueResult.entry.
const handoffInput = {
  kind: "Handoff", ...scope, sourceReference: handoffSource,
  content: {summary: handoffSummary, rationale: handoffRationale,
    links: [{kind: "Issue", reference: issueResult.entry}]}
};
const relatedInput = {
  scope: {...queryScope, kind: "Issue", limit: 1},
  state: "open", relatedIdentity: decisionResult.entry.identity
};
```

`scope` は必須repositoryIdentity／phaseIdentity／sessionIdentityと任意stepIdentity、
`queryScope` は上表のfind fieldsだけを持つ。結果statusを確認してから参照を渡す。
既存callerは一つの意味判断で得た本文と実参照を通常Operationへ渡せる。
OperationごとにAIを呼ぶ転記loop、専用client、別index、Markdownへの二重記録は不要。

訂正前は選択したIssueを `readJournalSemanticEntry` で読み、`found` の
`entry.entry.identity`／`entry.entry.revision` を `identity`／`expectedRevision` に使う。
`correctJournalEntry` のcontentには完全な置換内容と現在のstateを渡し、実changeSourceと
reasonを付ける。省略したlinksは保持されない。元scope／kind／originalSourceは変わらない。
成功した `updated` のEntry revisionを次の期待値に使う。
状態変更は別の `updateJournalIssueState` で行い、本文・linksを保持する。
stateの選択は実際の意味判断に帰属し、記録Operationの成功だけで元の問題をresolvedにしない。

別プロセスでは同じhostの公開findで関連IssueやHandoffを選び、返った実Entry参照を
semantic readへ渡す。revision省略は現在、指定はその一件の過去revisionを返す。
元revisionを明示して元内容／originalSourceと変更後のreason／previousRevisionを確認できる。
Findingや既存不変Entryには従来の `readJournalEntry` を使う。旧DTOと不変payloadのABIは維持し、
semantic詳細は追加readerを使う。検索は最大280文字のsummaryとtruncation表示、詳細は完全な
typed contentと最大4096文字のviewを返す。通常応答は全履歴を展開しない。

### 結果の扱いと引渡し境界

writeの `recorded`／`alreadyRecorded`／`updated` だけが実Entry参照を持つ。
同じ元source・kindと同等の元内容の再recordは、訂正後でも現在の実参照を再利用する。
元内容が異なる再recordはconflict。変更は期待revisionが古ければconflictで上書きしない。
共有の公開 `revision` はtext、providerのCAS guard `semanticRevision` は整数であり、
callerは前者だけを使う。guard欠落／不整合や壊れたpayload／projectionは拒否し、補完しない。
実装は各prior content／revision／source／変更時刻／reasonを同じ行に保持する。

`invalid`／`absent`／`conflict`／`disabled`／`unavailable`／`indeterminate` を成功扱いしない。
不明な書込acknowledgementはindeterminateであり、blind replayをせず公開読取で状況を確認する。
semantic readは `found`／`absent`／`invalid`／`disabled`／`unavailable`、不変readには
`unsupportedRevision` もある。findは `found`／`invalid`／`disabled`／`unavailable`。
foundの空配列は正常な不一致。検索はphysical ID昇順であり時刻順ではない。
`hasMore=true` ならfilterを絞る。件数・cursor・全件網羅性は保証しない。
providerはfilterをlimitより前に適用し、limit超過確認行も破損検証する。

運用Journalはnamed managed storeに置く。`docs/journal` はDESIGN ONLYであり、
実行／test／reviewの生logや運用Journalを追加しない。実証拠とlogは
`~/backup/work.d/YYYY/MM/DD/<project>/<target>`（本作業は `sm-workflow/phase-11.1`）に保持する。
Journalへの記録・訂正・検索はHuman回答、review acceptance、計画採用、Submit／Advanceを代行しない。
Findingを起点とする利用では実Finding参照を使う。今回Humanが選んだ実作業の引渡しには、
実際の判断を出所とするDecision→Issue→Handoff参照、実訂正／状態変更と
期待revision、別の通常プロセスからの公開読取、現在candidateに必要な検証と独立reviewの
実結果を所有callerが残す。batch・追加利用先・全Git移行・Phase 8挙動はこの手引きの範囲外。

今回のcallerは、実際のhost設定整合判断と利用手引きの課題をこの経路で記録し、
同じIssueのrevision 1／3の別プロセス読取と関連・状態検索を確認した。
過去の不変Findingを起点とする実利用は未検証であり、架空のFindingは作成していない。
原保存先の整合・移行の恒久対応は既存の担当へ引き継ぎ、この単件APIの受入れと分ける。
検証・review・closureの状態は[Phase 11.1](../phase/phase-11.1.md)で管理し、
このガイドの例やJournalの状態だけから正式完了を推定しない。
