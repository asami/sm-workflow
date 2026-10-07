# Phase 1: Application Workflows on the CNCF Common Contract

Status: completed

## Goal

Phase 1 は、CNCF Phase 77 の common Start/Handle/Continuation/Result contract を
specialize する software-development application layer を executable specifications
で完成させる。成果は次に限定する。

- GoalPhaseWorkflow、SplitPhaseWorkflow、RepositorySyncWorkflow
- application-specific typed Value Objects
- 三つの CML Workflow / StateMachine definitions
- CML typed Operation definitions と Action binding
- deterministic/test Providers と fixtures
- ReasoningLevel mapping policy
- CNCF `JudgmentAction` specialization with Codex as the initial external worker
- Presentation specialization
- schema-versioned JSON codecs / fixtures
- Executable Specifications

Phase 1 は production-ready local runtime を作る Phase ではない。

## Completion Statement

Phase 1 は、三つの application Workflow が CNCF の common contract に binding され、
typed Operation を外部 interface として executable specification から呼び出せること、
deterministic progression が AI turn を必要とせず、semantic boundary だけが typed
Continuation として現れることを再現可能に示した時点で完了する。

唯一の closure authority は
[Phase 1 Executable-Specification Checklist](phase-1-executable-specification-checklist.md)
である。

## Upstream Dependencies

Phase 1 は、次の versioned handoff に依存する。

### Cozy Phase 62.3

Cozy Phase 62.3 は CML の first-class `WORKFLOW` declaration と generated ABI を提供する
producer boundary である。StateMachine / Composite StateMachine semantics、typed Operation
reference、automatic progression と typed semantic boundary の区別を含む release 済み
handoff を使用する。

Cozy 側へ `advance`、Workflow persistence、SQLite、lease、public skill を再導入しない。

### CNCF Phase 77 and Phase 90

CNCF Phase 77 は generated Workflow ABI を admission し、common Workflow contract、Continuation runtime と bounded deterministic progression を所有する。CNCF Phase 90 はその上に optional Candidate-Admission runtime layer を提供する。Phase 1 の CAM/Step-closure path は Phase 90 を前提とし、generic Candidate/Admission runtime を sm-workflow 内に再実装しない。

CNCF Phase 94 は EventStore / DataStore / WorkflowInstance / Continuation / UnitOfWork の共有トランザクションを後で実装する独立 Phase であり、sm-workflow Phase 1 の入口・完了条件には含めない。Phase 1 は CNCF Phase 77.1/77.2 の明示的に緩い post-commit Continuation 境界を使い、コミット後の継続永続化が失敗または不確定なら未完了として報告し、その失敗結果から外部 WorkOrder を渡さない。これを原子的 rollback と呼ばない。Phase 90 の Candidate-Admission 依存は引き続き有効である。

書き込み結果が不確定な場合、保存先には `Available` な Continuation が残り得る。
失敗応答から WorkOrder を返さないことは、別 runtime からの claim を全面的に
排除した証明ではない。この場合の照合・回復は未完了として扱い、共有トランザクション
への強化は CNCF Phase 94 に残す。

Phase 77 から少なくとも次を consumer として利用する。

- `WorkflowStartRequest` / `WorkflowStartResult`
- `WorkflowHandle`
- closed `Continuation`
- `ContinuationRequest` / `ContinuationResult` / `WorkResult`
- Workflow / Continuation identity と expected revision
- `ContextSnapshot`
- typed Result / Evidence / `ExecutionEvidence`
- schema-versioned fail-closed JSON codecs
- minimum `Presentation` / `Progress`
- provider-neutral `JudgmentAction` / `JudgmentResult`

`sm-workflow` はこれらの generic contract を再定義しない。

Phase 90 から Candidate/Submission、AdmissionRequirement/Evaluation/Gap/Result、typed Evidence binding と freshness/coverage extension hook を利用する。`RequestStepClose` は application-specific Candidate/Submission specialization、Step closure policy は application-specific AdmissionRequirement/Evaluation specialization とする。Review scope、Phase/Checklist、Git commit semantics は sm-workflow 側に残す。Admission Gap は Continuation そのものではなく、必要な semantic Action を選択し、その Action が外部結果を必要とするときだけ Phase 77 Continuation runtime が suspension を提供する。

## Entry Criteria

- Cozy Phase 62.3、CNCF Phase 77、および CAM/Step-closure path について CNCF Phase 90 の compatible release handoff を確認できる。
- exact ABI version、fixture identity、repository revision を記録できる。
- generated ABI が automatic progression と semantic boundary を識別できる。
- CML Action が raw command ではなく typed Operation を参照できる。

条件を満たさない場合、暫定 Workflow DSL、generated-ABI copy、CNCF ABI emulator を
`sm-workflow` に作って迂回しない。

## Initial implementation status

2026-09-25 に Cozy `init component` で CAR の初期構造を作成した。生成された
`src/main/cozy/sm-workflow.cml` は汎用 entity CRUD の雛形であり、この時点では
GoalPhaseWorkflow / SplitPhaseWorkflow / RepositorySyncWorkflow の定義でも、
Phase 90 CAM の consumer 証明でもない。雛形の受け入れを Phase 1 の完了証拠に
数えない。次の実装は GoalPhase の Step-close 最小経路から始め、実際の Cozy
生成 ABI と CNCF Phase 77/90 を接続して不足を特定する。

現在のローカル基線は Cozy Phase 62.3 が閉鎖された Cozy checkout
`8d70aff`、CNCF Phase 90 が閉鎖された checkout `c4930b57` である。
fixture identity、generated ABI version、consumer 実行結果は未記録であり、
Entry Criteria と checklist の項目はこの bootstrap だけでは完了しない。
初期 CAR lint は構造上の FAIL なしで通過した。未生成の ABI manifest / CAR と
未完成の reference manual・user guide・Operation help は警告として残る。

2026-09-26 に最初の application CAM slice として
`StepClosureAdmission` を追加した。Step candidate の revision は program と
planning/management の両 digest に結び、sm-workflow 所有の full/focused review
scope と明示時刻による freshness policy を CNCF Phase 90 の
`CandidateAdmissionModel` に渡す。fresh な full-Step review の再利用、focused
review の coverage gap、いずれかの digest を変える修復後の失効、後着 evidence
による同一 submission の再評価、期限切れ・不正 payload の拒否を6件の限定 spec
で確認した。この検証は現在の CNCF compiled classes に対するもので、公開済み
`0.5.3-SNAPSHOT` JAR、CML 生成 ABI、CAR 全体の SBT 検証を証明しない。
この時点では `RequestStepClose` の CML 宣言と runtime 実装、Continuation、
StateMachine guard、決定的 commit への接続は未実装であり、CAM checklist の
完了項目にはまだ算入しない。

同日、汎用 CRUD CML を GoalPhase Step-close の最小 `WORKFLOW` / `STATE-MACHINE`、
`RequestStepClose` / `ReviewStep` typed Operation、Judgment/Admission Action の
draft に置き換えた。ComponentFactory も新しい Service Operation 名に追従した。
これは CML producer 側の受理と CNCF consumer 実行が済むまで設計・実装途中とする。
インストール済み Cozy CLI の `modeler-scala` は exit 0 で
`ACTION 'ReviewStep' KIND must be OPERATION` と報告し、生成物を出さなかった。
Cozy checkout `8d70aff` の `candidate-admission-workflow.cml` fixture は同じ
`kind = JUDGMENT` / `kind = ADMISSION` を使用するため、この CLI の結果を現行
producer ABI の不受理証明とは扱わない。

当時のローカル Cozy compiled classes を使う producer-only probe
`src/test/probes/GoalPhaseCmlProducerProbe.scala` は、CML source SHA-256
`999aee5280810f61d621fccacdf68f32f5837a1532834a90645d829e04eb7912`
について、source-attributed Workflow 正規化、ReviewStep/AdmitStepClose の
Judgment/Admission 関係、required/provided Operation 各1件、および両 ABI の
in-memory Scala descriptor 生成を確認した。ABI version は
`cozy.cml.statemachine-workflow-abi.v1` と
`cozy.cml.candidate-admission-producer-abi.v1`。canonical JSON SHA-256 は順に
`d3093642479c5e660fff6789764c0fcd5877a31e6ace123dd70a58f427552f52` と
`9f5094ecc54956b3a5b75d773440d1d479708e727bd04bc86666e7240272a1e5`。
これはインストール済み CLI の
`modeler-scala` 成功、CAR `cozyGenerate` / compile、CNCF consumer binding、
Continuation 実行を証明しない。checklist は引き続き未チェックとする。

その後、Cozy `0.3.3-SNAPSHOT` と CNCF `0.5.3-SNAPSHOT` を現行 checkout から
`publishLocal` し、`sbt-cozy` を `0.1.17` に更新した。生成 provenance が要求する
`src_managed/main/scala` に `cozyTargetDir` を合わせ、GoalPhase の Review と
Step-close を別 Service に分けて同一入力型の生成内重複を回避した。当時の CML source
SHA-256 は `df3e17a9c645d0960ca0faa338c8658c5b49efacd5cad35292963e00884fc65c`。
この変更により上記 probe の JSON digest は旧 CML の履歴証拠となり、この CML の
digest としては使用しない。

現行 CML で `cozyGenerate` は Scala 21ソースを生成し、`compile` は生成物を含む
23ソースで成功した。生成 Workflow / Candidate-Admission / Provided API の
相関を確認する2件の spec を追加し、`sbt test` は3 suite・9 test 成功した。これらは
GoalPhase の生成・コンパイル経路と現存する CAM policy/unit tests の成立を示す
診断結果であり、実 CAR packaging、CNCF Workflow/Continuation consumer 実行、
SplitPhase/RepositorySync、launcher fixture、Phase 1 checklist の完了はまだ示さない。

続いて同じ CML source（上記 SHA-256）を Cozy `modeler-scala` で出力し、
`cozy.cml.statemachine-workflow-abi.v1` と
`cozy.cml.candidate-admission-producer-abi.v1` の実 JSON を fixture 化した。
raw JSON SHA-256 は順に
`8e88da6340e6472a7773478c731027b2de4dc211f40d3ad0e86f101a72cbf16f` と
`2e66c12aeec87c16bbb18f15271fe4b77018a5d67663267f78149e21a61c8f2d`。
最初の CNCF consumer テストは、判断 `StepReviewService.reviewStep` と受理
`GoalPhaseService.requestStepClose` が同一 Operation でなければならないという
受理器の制約で失敗した。CNCF は両者の同一入力型・input binding と
Judgment/Required SPI の Operation 一致を維持し、Judgment と Admission 自体は
別 Operation として認めるよう修正した。CNCF の受理器 spec は15件成功し、
修正版 `0.5.3-SNAPSHOT` を `publishLocal` してから sm-workflow の consumer spec
3件が成功した。この spec は sidecar と Workflow JSON の組を CNCF で受理し、
`WorkflowInstancePersistence` の definition identity に binding する。
これは生成 ABI の consumer 接続までの診断結果であり、実際の Step-close 実行、
Continuation、commit、他の二つの Workflow、Phase 1 checklist の完了は示さない。

次に生成された `StepCloseRequest` を application CAM policy に変換する
`StepCloseRequestAdapter` を追加した。入力の Step snapshot とレビュー日時、対象 revision、
scope を検証し、レビュー済みなら受理、focused review なら full-Step coverage gap、
修復前 revision のレビューなら WrongTarget とする4件の spec が成功した。
現在の CML `scope` は文字列のため、アダプタは `full-step` または
`focused:<単一path>` だけを受け付ける暫定的な application 入力文法である。
この結果は CML Operation 入力から CAM 判定への接続までであり、
`requestStepClose` の実行サービス、review WorkOrder、Continuation、
StateMachine 遷移、commit はまだ実装・実行していない。

続いて `GoalPhaseStepCloseRouting` を追加し、生成 ABI を受理した GoalPhase の
active WorkflowInstance に対して、CAM の full-review gap を CML 宣言済み
`ReviewStep` Required SPI へ照合する。fresh な full review が揃えば review route は
返さず、未宣言の操作や suspended instance は拒否する。限定 spec は3件成功した。
これは純粋な admission/routing
境界であり、WorkOrder の発行、Continuation の保存、`requestStepClose` の実行、
StateMachine 遷移、commit の証明ではない。

次の `GoalPhaseReviewBoundary` は Step-close の Provided Operation 入力と candidate
revision を照合し、review gap がある場合だけ CNCF `Continuation` と typed WorkOrder
input を準備する。CNCF の post-commit prerequisite に渡す WorkflowInstance の
suspension record も純粋に導出する。限定 spec 4件では、commit 前の claim 拒否、
WorkflowInstance 保存後の Continuation claim、および post-commit 保存失敗時に
Continuation が claim できないことを確認した。これは緩い transaction 境界の fixture
であり、保存失敗が原子的に rollback されるという意味ではない。

続いて `ReviewStepResult` に decision / rationale / reviewEvidence を定義し、
`GoalPhaseService.submitStepReview` を profile-specific completion Operation として
CML に追加した。この時点の CML source SHA-256 は
`5e63c5941311b7a4561e79937c754d3b76d98b7d8167d60580dda2ccd65e8c03`。
Cozy `modeler-scala` から再取得した Workflow / Candidate-Admission / Provided API
JSON の SHA-256 は順に
`f02eee51ac5f72b154ef1d89b6a77a064ca7c9c1d6fe273af8bd97f63e76b3c3`、
`20424a7f5aea2bd60632ea8fb1c3806718bb9b7523fb14abc2337d8ab4c15e0b`、
`e45adf9b7001e8c8df3fed50a158229cbc2c2fd0ca34e7ce22d2dab3aff7394f`。
`cozyGenerate; test` は7 suite・23 test 成功した。

`GoalPhaseReviewBoundary` は生成 Provided API から completion Operation と入力結果型を
照合し、呼び出し側から渡された suspension record に一致する Continuation だけを CNCF
`issueRecoveredWorkOrderC` で発行する。限定 spec 4件では commit 前の発行拒否と
commit 後の typed WorkOrder 発行・記録を確認した。これは in-memory fixture であり、
別ターンの `submitStepReview` 実行、Step Closed への遷移・決定的 commit、永続 store
での復旧、他の二つの Workflow は未証明である。

`GoalPhaseReviewResultAdapter` は発行済み WorkOrder と提出 `ContinuationResult` の
identity / revision / ContextSnapshot / result type を CNCF 境界で照合し、生成
`ReviewStepResult` の decision・rationale・reviewEvidence を producer sidecar と
CAM に受理する。元の Step-close candidate に対して証拠の revision・時刻・scope を
再評価し、full-Step の `accept` のみ closure-ready とする。`repair` は再評価結果と
ともに差戻しとして保持し、scope 不足の `accept`、古い candidate revision、異なる
result type は拒否する。限定 spec 4件でこの純粋な境界を確認したが、Result を使った
この時点では Continuation の resume、StateMachine 遷移、Step Closed への commit は
まだ行っていない。

`GoalPhaseReviewResumeBoundary` は受理済みレビュー結果を保存済み WorkOrder と
WorkflowInstance suspension に再照合し、CNCF の one-shot Continuation resume を
実行する。その後、結果参照を causation に持つ suspension なしの Active record を
append する。in-memory fixture では accept 後の resume と二重 resume 拒否を確認した。
Continuation 消費と WorkflowInstance append は現在の緩い transaction 境界に従い
別の永続操作である。append 失敗時に原子的 rollback されたとは扱わない。
この直接呼び出し fixture 単独では公開 Service Operation の実行、review evidence の
再起動可能な永続保存、決定的 commit、Step Closed / Terminal への遷移を証明しない。

`GoalPhaseReviewCompletionOperation` は Cozy 生成 Provided API の
`GoalPhaseService.submitStepReview` と `ReviewStepResult` / `StepReviewSubmissionResult`
を CNCF completion binder に結び、提出 JSON の型・必須フィールド・余分な
フィールドを厳密に検査する。公開 Service 経由の in-memory fixture では、発行済み
WorkOrder と保存済み suspension に一致する full-Step accept のみを resume し、
WorkflowInstance を revision 3 の suspension なし Active に append した後、
`WAIT` JSON を返す。二重送信と不正な結果は拒否する。限定 spec 4件は成功した。
ただし review evidence の再起動可能な永続保存、実 Git commit、
Terminal への実運用経路は未実装・未検証である。
Phase 1 checklist は引き続き未完了とする。

`GoalPhaseStepClosureBoundary` を追加した。公開 review 完了後の
Active instance と元の WorkOrder/Result を再照合し、full-Step admission がなお有効で、
生成 `StepCloseLifecycle` が `Awaiting --admitted--> Closed` を許す場合だけ、program と
planning/management の両 digest・candidate revision・review evidence identity・遷移を
決定的 commit port に渡す。snapshot port から取得した program と planning の実バイト列を
それぞれの SHA-256 digest と照合し、一致した二つの内容を commit port に渡す。返された
receipt が要求と一致した後に限り Completed history を
append し、CNCF Terminal 投影を fixture で確認する構成である。commit port は Phase 1 では
in-memory test provider である。commit 要求には instance・submission・candidate・両 digest・
review evidence・遷移から導出した安定 idempotency key を付け、port に同一要求の receipt 再返却と
同一キーの異なる要求の拒否を要求する。fixture では commit 後の Completed append が一度失敗しても、
同じ時点・同じ入力での再試行が二重 commit を作らず revision 4 に進むケースを追加した。
program または planning の内容が review 対象から変わった場合は commit 前に拒否し、
同じ idempotency key に異なる snapshot を渡す場合も fixture commit port が拒否する。
実 Git commit、receipt の再起動可能な保存、時刻が進んで review evidence が失効した場合や
append 成否が不確定な場合の照合・回復はまだ実装しない。共有ロック経由の限定 SBT
`testOnly`（`SMWF-P1-STEP-CLOSE-TEST-20260926-02`）では 2 suite・7 test が成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。これは in-memory fixture の
検証であり、Phase 1 全体の完了や実 Git commit の証明ではない。snapshot 内容の
照合を追加した後の限定 `testOnly`（`SMWF-P1-SNAPSHOT-TEST-20260926-01`）も
2 suite・7 test が成功し、`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。

`ProfileStartAdmission` は application 側の起動権限 gate として、host/client が保存した
人間の明示選択を selection ID で読み、選択された Start Operation、Workflow definition/
revision、participant、idempotency key と typed input を照合してから CNCF
`WorkflowStartRequest` に写す。recommendation reference は相関情報に限り、推奨だけ、
Skill/Workflow/terminal result 起源の記録、別 profile の選択では起動要求を作らない。
これは純粋な admission fixture であり、WorkflowInstance 作成と三つの profile の
end-to-end 起動はまだ証明しない。共有ロック経由の
限定 `testOnly`（`SMWF-P1-START-AUTH-TEST-20260926-01`）は 1 suite・3 test が成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。

GoalPhase の CML に `startGoalPhase` Provided Operation と
`GoalPhaseStartCommand` / `GoalPhaseStartInput` を追加した。生成 ABI の Start input/result
を `GoalPhaseStartCommandAdapter` が照合し、host-owned の人間選択記録を通過した場合だけ
型付き CNCF `WorkflowStartRequest` を作る。調査用の `modeler-scala` 出力を通常の
`target/` 内に置いたため初回 `cozyGenerate` は provenance 検査で停止したが、出力を
一時領域へ退避した後の共有ロック経由 `cozyGenerate; testOnly`
（`SMWF-P1-START-GENERATE-TEST-20260926-02`）では 5 suite・14 test が成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。これは GoalPhase の
生成宣言と Start admission までの検証である。この時点では公開 Operation からの実行・永続化、
WorkflowInstance 作成、SplitPhase/RepositorySync の Start は未実装・未検証だった。
現在の CML source SHA-256 は
`42f274ce12e6a891af50796a9241e2dda7268bc8704563df319f9baf5d04fcf7`。

その後 `GoalPhaseStartExecution` と `GoalPhaseStartServiceOperation` を追加した。
host が保存した明示的な人間選択を検証した上で、同一 idempotency key に対応する
GoalPhase `WorkflowInstance` を NotStarted として作成し、`step.Awaiting` の Active
history を append する。返す `WorkflowHandle` / `WAIT` は CNCF の共通 contract を使用し、
`ContextSnapshot` は定義 revision に結び付ける。同じ要求の再送では instance と history を
増やさず、同じ key で異なる入力は拒否する。作成後の append 失敗からの再試行も fixture で
確認した。CML 生成 Provided API の `GoalPhaseService.startGoalPhase` に照合した Service
Operation の JSON は version と必須フィールドを厳密に検査する。共有ロック経由の限定
`testOnly`（`SMWF-P1-START-SERVICE-TEST-20260926-05`）は 1 suite・4 test 成功、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。

この Service 経路は host-owned selection store と in-memory instance store を注入した
fixture であり、既定の `ComponentFactory` に production provider を登録したものではない。
実 store の再起動・同時実行・不確定な append の回復、launcher 起動、
SplitPhase / RepositorySync の Start と三つの Workflow の end-to-end 実行は未証明である。
Phase 1 は引き続き in_progress とする。

続いて `GoalPhaseService.requestStepClose` の公開 Service fixture を追加した。host が
`submissionIdentity` から既存の `WorkflowHandle` を解決し、strict な versioned
`StepCloseRequest` JSON、active `step.Awaiting` instance、生成 CML Provided Operation を
照合する。fresh な full-Step evidence があれば追加 review を発行せず、二つの artifact
digest と実内容を確認して決定的 commit port、Completed append、CNCF Terminal JSON
へ進む。evidence が足りなければ既存の CAM/Required SPI に従って Continuation と
WorkflowInstance suspension を緩い UnitOfWork 境界で保存し、claim 後に typed
WorkOrder JSON を返す。この経路は host-owned invocation port と in-memory stores を
注入した fixture であり、既定の `ComponentFactory` には登録していない。共有ロック
経由の限定 `testOnly`（`SMWF-P1-STEP-CLOSE-SERVICE-TEST-20260926-04`）は 1 suite・
5 test 成功、`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。review 完了後の
公開 Service 経由の自動閉鎖、実 Git commit、再起動可能な store、launcher 経由の実行、
他の二つの profile は未証明であり、Phase 1 の完了を意味しない。

`GoalPhaseReviewCompletionOperation.bindAutoCloseC` は、公開
`submitStepReview` の accept を既存の Step-close 境界へ結線した。保存済み WorkOrder、
review result、resume 済み Active instance を再照合し、deterministic commit と
Completed append が成功した場合に同じ Operation から CNCF `TERMINAL` JSON を返す。
Skill が二度目の `requestStepClose` を提出する必要はない。repair の場合は commit せず
`WAIT` を返す。in-memory fixture の限定 `testOnly`
（最終状態の `SMWF-P1-AUTO-CLOSE-TEST-20260926-02`）は 1 suite・6 test 成功、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。ただし resume append と
commit/Completed append は単一の durable transaction ではない。後段で失敗した場合の
再起動後の自動回復や実 Git commit は未実装であり、Phase 1 は継続中である。

次に `SplitPhaseWorkflow` の独立した entry 最小 slice として、CML に
`SplitPhaseStartInput` / `SplitPhaseStartCommand`、`SplitPhaseService.startSplitPhase`、
`EntryPending` StateMachine を追加した。これは GoalPhase terminal result を自動的に
引き継ぐ宣言ではなく、別 run を明示選択で開始するための型付き入口である。
Cozy 生成中に goldenport-scala-library の `TreeMap` 結合が非空 map を誤って
`TreeMap` へ cast する既存不具合に遭遇したため、型を保った map 結合に修正し、
回帰 spec 1 件を通してローカル SNAPSHOT を更新した。Cozy もその SNAPSHOT に更新し、
`cozyGenerate` は 30 Scala source の生成に成功した。生成 Workflow / Provided API と
Start command を照合する限定 `testOnly`
（`SMWF-P1-SPLIT-ENTRY-TEST-20260926-02`）は 1 suite・1 test 成功、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。

ただし、CML の `0..1` として宣言していた三つの任意参照は、goldenport-record の
多重度記法ではなく、Cozy が `Option[String]` を生成する `?` で表す必要があった。
この source syntax を `?` に修正した。修正後の生成 Scala の optional shape と、任意値の
欠落表現は、直列 invocation
（`SMWF-P1-OPTIONAL-ABI-VERIFY-20260926-01`）の
`cozyGenerate; testOnly org.simplemodeling.textus.smworkflow.SplitPhaseGeneratedAbiSpec org.simplemodeling.textus.smworkflow.GoalPhaseCncfConsumerSpec`
で検証した。生成された Scala は三つすべて `Option[String]` となり、2 suite・5 test
成功、`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。この focused spec は
Scala 上の presence/absence を検証するが、JSON round-trip は検証していない。
SplitPhase の Start admission / instance 作成、実際の proposal・適用・terminal、
RepositorySync の定義、および Phase 1 の全体受入れは未完了である。

この CML 追加で GoalPhase consumer fixture の source location も移動したため、
現在の Cozy checkout の `modeler-scala` 経路から Workflow / Candidate-Admission
sidecar を再生成し、GoalPhase 部分を fixture に反映した。任意参照の `?` 修正後の
現行 CML SHA-256 は
`3b75be864c88e6167c5634d1ae0c67deebb45cb16e1ed391124c94c927a17dbc`、
GoalPhase Workflow JSON と Candidate-Admission JSON の SHA-256 はそれぞれ
`0b95c82dbfb41841484b89c4f42b9ee75687fe541355d7455b409477074fd4c1`、
`af741d406dbcaa07ac5d6cbda6a6915b6d45685822bfeff37c74b1136c261435`。
`?` 修正前の fixture 更新後に記録した直列 SBT 全 `test`
（`SMWF-P1-SPLIT-REGRESSION-20260926-02`）は 11 suite・35 test 成功、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。これは `?` 修正前に
記録された sm-workflow 回帰結果であり、今回の修正を検証した結果ではない。Phase 1
checklist の全項目完了も意味しない。

2026-09-27 に `SplitPhaseStartCommandAdapter`、`SplitPhaseStartExecution`、
`SplitPhaseStartServiceOperation` を追加した。生成された Provided Operation と入力型を
照合し、host 保存の `ExplicitHuman` selection を要求する。`APPLY` / `PREVIEW` 以外の
mode と空の任意参照を拒否し、三つの `Option[String]` を versioned JSON で null / string
として往復する。実行コードは GoalPhase から state を移さず、別の `split-phase-*`
instance と `entry.EntryAssessment` progression を作る。限定 `testOnly`
（`SMWF-P1-SPLIT-START-TEST-20260927-01`）は 1 suite・3 test 成功、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。これは全 main source の
コンパイル、選択 admission、JSON round-trip の証拠であり、実 instance 作成の成功証明ではない。

この時点では definition binding が不足していた。CNCF `CandidateWorkflowAbi` は
Action が非空で Candidate-Admission sidecar に一致する Workflow だけを binding し、
Cozy の producer は candidate action のない `SplitPhaseWorkflow` を sidecar に含めない。
旧 `GeneratedWorkflowAbi` も Action と Required SPI を非空要求する。

この不足に対して CNCF に actionless entry 専用の `GeneratedEntryWorkflowAbi` と
`WorkflowInstancePersistence.bindEntryDefinitionC` を追加した。生成 Workflow と
Provided API の identity・revision・source location・単一 Start operation を照合し、
Action / Required SPI を偽造せずに durable definition へ binding する。CNCF 限定
`testOnly`（`SMWF-P1-ENTRY-BINDING-CNCF-TEST-20260927-01`）は 1 suite・3 test
成功し、更新済み `goldenport-cncf_3:0.5.3-SNAPSHOT` を直列 `publishLocal`
（`SMWF-P1-ENTRY-BINDING-CNCF-REFRESH-20260927-01`）で反映した。

sm-workflow の `SplitPhaseDefinitionBinding` は Cozy 生成済みの二つの Scala descriptor
からこの CNCF binding を構築する。`SplitPhaseStartExecutionSpec` は独立した
`split-phase-*` instance、同一 command の idempotent retry、異なる payload と
recommendation-only authority の拒否、append 失敗後の再開、公開 Service を通る
`WAIT` 応答を fixture で検証した。限定 `testOnly`
（`SMWF-P1-SPLIT-EXECUTION-TEST-20260927-01`）は command adapter と合わせて
2 suite・7 test 成功し、各 invocation の `sbt_exit=0`、`wrapper_exit=0`、
`lock=released` を確認した。続く sm-workflow 全 `test`
（`SMWF-P1-SPLIT-REGRESSION-20260927-01`）も 13 suite・43 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。これは
in-memory durable-port fixture の実行証明であり、
production store、SplitPhase の proposal 適用と terminal、RepositorySync、Phase 1
全体の完成証明ではない。Phase 1 は継続中であり、P1-R34 は未完了のままとする。

続いて `SplitPhaseEntryAssessment` を追加し、host-owned port が返す source snapshot
の Phase identity・authority・revision・digest と、任意 proposal candidate の source
correlation を Start 時に照合するようにした。明示的な snapshot reference が異なる場合、
proposal が別 source/digest を指す場合は instance を作らない。受理した source evidence
を Start の causation digest と `WAIT` の ContextSnapshot に反映し、同一 Start key の
再試行時に source が変わっていれば既存 run を黙って再結合しない。proposal はこの段階では
候補であり、partition・番号・ownership・適用の admission 済みとは扱わない。限定
`testOnly`（`SMWF-P1-ENTRY-ASSESSMENT-TEST-20260927-01`）は 1 suite・7 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。Start 後の source inventory、
proposal 検証、PREVIEW/APPLY terminal は引き続き未実装である。最終的な表示文言の修正後に
実行した全 `test`（`SMWF-P1-ENTRY-ASSESSMENT-REGRESSION-20260927-01`）は
13 suite・46 test 成功し、`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。

続いて `SplitPhaseProposalPrecheck` を追加した。Start で照合した source snapshot と
proposal candidate の digest を実際の draft と source inventory に結び付け、未完了項目の
重複・欠落・順序変更、子 Phase の 8 時間超過・独立 close 不可、suffix 番号の不整合を
拒否する。nested/unknown 番号と既存 child identity との衝突は自動解決せず
`DecisionRequired` とする。この純粋な shape precheck は Start 後の Operation には
まだ接続しておらず、calibration・handoff・validation ownership・document projection
を含む proposal admission や PREVIEW/APPLY の許可を意味しない。全 `test`
（`SMWF-P1-SPLIT-PROPOSAL-PRECHECK-20260927-01`）は 14 suite・51 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。Phase 1 と P1-R34 は
引き続き未完了である。

`SplitPhaseProposalAssessment` は、Start の entry assessment が照合した source snapshot
と proposal candidate の ID から host-owned inventory / draft を読み込み、上記 precheck
へ渡す決定的な評価経路として追加した。proposal 未指定は `ProposalRequired`、不足・変化した
証拠は failure、番号衝突は `DecisionRequired` に分類する。全 `test`
（`SMWF-P1-SPLIT-PROPOSAL-ASSESSMENT-20260927-02`）は 15 suite・55 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。現行の生成 ABI と CNCF
`GeneratedEntryWorkflowAbi` は1状態・Start 1 Operation に限定されるため、この評価経路は
まだ公開 completion Operation または durable な Workflow transition に結合していない。
正式な proposal admission、PREVIEW/APPLY terminal、Phase 1 completion の証明ではない。

次の増分として、SplitPhase CML に `ProposalAssessment` state と
`assessSplitProposal` Provided Operation を追加し、生成された Workflow / Provided API を
CNCF の entry definition に結合した。CNCF `GeneratedEntryWorkflowAbi` は従来の
1-state / Start-only fixture digest を維持しつつ、2-state / Start + post-entry
Operation を opt-in で検証する。CML 生成とコンパイル後の全 `test`
（`SMWF-P1-STAGED-CML-FULL-TEST-20260927-02`）は 15 suite・55 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。この時点では公開
Operation の実行 binding と revision 2 への durable append は未実装であり、
`ProposalAssessment` への実行時遷移、proposal admission、PREVIEW/APPLY の完成を
主張しない。

続いて `SplitPhaseProposalAssessmentExecution` と公開
`SplitPhaseService.assessSplitProposal` binding を追加した。Start に結び付いた
source/proposal evidence と保存済み revision 1 を再照合し、proposal の shape check が
`ShapeReady` の時だけ `shape-ready` transition で revision 2 / `entry.ProposalAssessment`
を append する。proposal 不在または人の判断が必要な場合は revision 1 の `WAIT` に留める。
同一 command の再試行は履歴を増やさず、異なる idempotency key、誤った expected revision、
append 失敗後の再試行も fixture で検証した。公開 Service 経由の JSON round-trip と
revision 2 の結果も検証した。全 `test`
（`SMWF-P1-SPLIT-STAGE-FULL-TEST-20260927-03`）は 15 suite・59 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。この遷移は proposal の
形状確認だけであり、calibration・handoff・validation ownership・document projection
を含む正式な proposal admission、PREVIEW/APPLY、production store、Phase 1 の完了を
意味しない。

続いて `SplitPhaseValidationOwnership` を proposal validation の部分境界として追加した。
shape-ready draft と source/proposal digest に結び付けた child 宣言について、closure
criterion、依存・前後関係、validation owner を確定的に照合する。`FINAL_ONLY` は同一
repository の直列チェーンと最後の child による validation を、`EACH_PHASE` は各 child
自身の validation を要求する。異なる source/digest、子の順序・identity 重複、closure
欠落、複数 repository の `FINAL_ONLY`、直列 edge 欠落は fail closed とした。全 `test`
（`SMWF-P1-VALIDATION-OWNER-FULL-TEST-20260927-06`）は 16 suite・63 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。これはまだ公開 Operation
への結合や正式な proposal admission ではない。calibration、semantic handoff、short-child
merge/rebalance、document projection と PREVIEW/APPLY は残る。

次の限定増分として `SplitPhaseDurationCalibration` と
`SplitPhaseSemanticHandoff` を追加した。前者は versioned policy と observation ID 順の evidence
snapshot digest の一致を要求し、exact work/repository class、history window、観測
digest、重複、policy outlier を確定的に分類する。explicit baseline がある場合は
planned/actual pair の median ratio、ない場合は actual-per-unit median を使い、固定
quantum への丸めと repository-class overhead を一度だけ適用する。証拠不足または既存
unit envelope 超過は `UnresolvedNovel` に残し、既知 estimate を AI が上書きする経路を
設けない。後者は authority に明示済みの kind/input/action/output/owner/invalidation
reason を持つ handoff を proposal digest と子 Phase 順序に結び、producing child と
receiving child、および receiving child 側の exactly-once 記録を照合する。未知の
semantic edge を推測・採用する機能ではない。限定 `testOnly`
（`SMWF-P1-CALIBRATION-HANDOFF-TEST-20260927-07`）は2 suite・7 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。これらは純粋な
validation input であり、公開 Operation への結合、short-child merge/rebalance、
optimizer、document projection、PREVIEW/APPLY、正式な proposal admission は未実装である。
planned pair 欠落の除外 trace を補った後の全 `test`
（`SMWF-P1-CALIBRATION-HANDOFF-FULL-TEST-20260927-08`）は70 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。

続いて公開 `SplitPhaseService.assessSplitProposal` の host-owned proposal port に
validation evidence の取得を加え、`SplitPhaseProposalValidation` で上記三つの純粋な
検証を結合した。source/ownership/handoff digest と全 unfinished item の calibrated
estimate を照合し、各 child の expected duration が所有 item の合計と異なれば拒否する。
既知 work の証拠不足は `NovelWorkRequired` として revision 1 の `WAIT` に留め、
検証 evidence が揃う場合だけ既存の `shape-ready` transition で revision 2 へ進む。
revision 2 の causation と WAIT evidence revision には proposal と policy/evidence/
handoff/ownership を束ねた digest を用い、進行後に証拠が変わった同一 command の
再試行は拒否する。限定 `testOnly`
（`SMWF-P1-PROPOSAL-VALIDATION-RETEST-20260927-10`）は3 suite・21 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。最初の限定実行
（`...FOCUSED-20260927-09`）は test source の import 衝突でコンパイルに失敗し、
修正後に同じ対象を再実行した。これは公開 Operation に接続した部分検証であり、
未知 edge の semantic Action、short-child merge/rebalance、optimizer、document
projection、PREVIEW/APPLY、正式な proposal admission は引き続き未実装である。
続く全 `test`（`SMWF-P1-PROPOSAL-VALIDATION-FULL-20260927-11`）は76 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。

続いて short-child の duration-visible な隣接候補を列挙する
`SplitPhaseShortChildReview` を、公開 `assessSplitProposal` が使う部分検証へ接続した。
各 child の calibrated item 合計を前提に、通常の1–2時間 child を拒否し、4時間未満には
明示済みの隣接 semantic handoff を要求する。4時間未満の隣接 merge と5時間未満の
contiguous item 移動による rebalance を両隣について検査し、8時間 ceiling 内の
未選択な duration-viable 候補があれば進行を拒否する。試行 trace の digest は proposal
validation evidence に結び付ける。限定 `testOnly`
（`SMWF-P1-SHORT-CHILD-FOCUSED-20260927-12`）は26 test、全 `test`
（`SMWF-P1-SHORT-CHILD-FULL-20260927-13`）は81 test 成功し、両方とも
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。これは
duration-visible な最小 guard であり、atomic closure、未知 semantic edge、候補全体の
partition optimizer と lexicographic objective、document projection、PREVIEW/APPLY、
正式な proposal admission は未実装である。

次の未検証増分として `SplitPhasePartitionEnumeration` と fixture を追加した。明示された
item 順、edge disposition、versioned overhead/件数上限から contiguous な分割候補を全列挙し、
8時間 ceiling を超えた候補を分けて記録する。`Unresolved` edge は semantic assessment
待ちに残し、
件数上限では途中結果を返さない。closure/validation/cost の評価、最適候補の選択、
公開 Operation への結合はまだない。focused test
（`SMWF-P1-PARTITION-ENUM-FOCUSED-20260927-14`）は runner が intake locator を
誤入力したため SBT 起動前に拒否され、コンパイル・テスト証明は未取得である。
その後、未解決 edge を人の Decision に直結させず `SemanticAssessmentRequired` として
`AssessNovelSplitWork` に渡す型へ修正した。現行 tree の限定 `testOnly`
（`SMWF-P1-PARTITION-SEMANTIC-FOCUSED-20260927-15`）は5 test、全 `test`
（`SMWF-P1-PARTITION-SEMANTIC-FULL-20260927-16`）は86 test 成功し、両方とも
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` の receipt を確認した。

続いて `SplitPhasePartitionPriority` を追加し、列挙済み候補の duration-visible な
objective prefix（6時間からの絶対偏差の総和、次に5時間未満の子の数）を決定的に
順位付けした。同点候補はすべて duration leader として残し、辞書順で最終候補を
採用しない。候補 coverage が不正な入力は拒否する。closure、validation、
short-child admissibility、cost-isolation、overhead、および authority 上の差異の
評価は未実装であり、この結果を正式な proposal admission として使わない。
限定 `testOnly`（`SMWF-P1-PARTITION-PRIORITY-FOCUSED-20260927-17`）は成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` の receipt を確認した。
全 `test`（`SMWF-P1-PARTITION-PRIORITY-FULL-20260927-18`）は90 test 成功、
失敗0で、同じ終了・ロック解放条件を確認した。

次の限定増分で `SplitPhaseCandidateAdmissibility` を追加した。候補 digest に closure、
authority、validation、short-child の gate 結果を対応付け、全候補の coverage と
evidence digest の形式を検査する。明示的に reject された候補を除外し、未判定の
非 reject 候補が一つでもあれば duration 順位から採用候補を出さない。全候補が
決着した場合も返すのは eligible duration 順位と同点 leader までで、cost/overhead
評価や最終選択は行わない。gate の真偽をこの純粋な結合処理が証明するわけではなく、
closure/validation/authority の実証済み evaluator と公開 Operation への結合は残る。
限定 `testOnly`（`SMWF-P1-CANDIDATE-ADMISSIBILITY-FOCUSED-20260927-19`）は
5 test 成功、失敗0で、`sbt_exit=0`、`wrapper_exit=0`、`lock=released` の
receipt を確認した。
全 `test`（`SMWF-P1-CANDIDATE-ADMISSIBILITY-FULL-20260927-20`）は
95 test 成功、失敗0で、同じ終了・ロック解放条件を確認した。

候補列挙の `phaseOverheadMinutes` と既存 short-child review の item 合計前提に
差があることが分かったため、`SplitPhaseShortChildReview` は明示された Phase overhead
を child duration に一度だけ加え、隣接 merge では二重計上された一回分を差し引くようにした。
既存 proposal validation は overhead 0 の従来入力を保ち、候補経路では versioned
partition policy に束ねた overhead を渡す。`SplitPhaseCandidateGateDerivation` は
候補 digest・policy digest・draft の所有 item/時間を照合し、既存 handoff、
validation-owner、short-child 検証から後二者の gate evidence を導出する。
独立 closure と authority equivalence は、draft の自己申告だけでは証明せず
`Pending` のままにする。したがって最終候補の選択や公開 Operation の admission には
まだ接続しない。限定 `testOnly`（`SMWF-P1-CANDIDATE-GATES-FOCUSED-20260927-21`）は
10 test 成功、失敗0で、`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
全 `test`（`SMWF-P1-CANDIDATE-GATES-FULL-20260927-22`）は101 test 成功、
失敗0で、同じ終了・ロック解放条件を確認した。公開 proposal validation の
overhead 0 前提を versioned Phase overhead と統一する作業は残る。

その公開 proposal validation に `SplitPhasePartitionEnumeration.Policy` を必須 evidence
として追加した。calibrated item 合計に policy の Phase overhead を一度加えて child
duration と照合し、同じ値を short-child review に渡す。policy digest を validation
evidence digest に含め、同じ proposal/観測でも policy version が変われば前の
evidence を再利用しない。非ゼロ overhead を公開 `assessSplitProposal` の host-owned
evidence port から通す fixture を追加した。既存 overhead 0 fixture も明示 policy
として維持する。限定 `testOnly`（`SMWF-P1-PROPOSAL-OVERHEAD-FOCUSED-20260927-23`）は
25 test 成功、失敗0で、`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
これは候補の closure/authority の完成証明や最終 optimizer 選択を意味しない。
全 `test`（`SMWF-P1-PROPOSAL-OVERHEAD-FULL-20260927-24`）は104 test 成功、
失敗0で、同じ終了・ロック解放条件を確認した。

続いて公開 `SplitPhaseProposalValidation` は calibrated work の順序と versioned
partition policy、明示 edge disposition から contiguous 候補を再列挙し、選択済み
draft の item 所有・overhead 込み duration が候補集合に含まれることを要求する。
considered-candidate digest と選択候補 digest を validation evidence に束ねるため、
同じ draft でも候補集合が変われば古い evidence は再利用できない。未知 edge は
`SemanticBoundaryRequired` として公開 assessment の revision 1 WAIT に残し、
最終 admission や Step/Phase の選択を推測しない。closure・authority の独立証明、
全候補の適格性評価、cost-aware winner 選択はなお残る。限定 `testOnly`
（`SMWF-P1-PARTITION-BINDING-FOCUSED-20260927-25`）は28 test 成功、失敗0で、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
未知 edge で公開 Operation が revision 1 の WAIT に残る fixture を追加した後の
全 `test`（`SMWF-P1-PARTITION-BINDING-FULL-20260927-26`）は108 test 成功、
失敗0で、同じ終了・ロック解放条件を確認した。

次の限定増分では `SplitPhaseSourceBoundary` を追加し、提案者自身の
`independentlyClosable` 宣言とは別の host-owned source-document projection によって、
全 unfinished item と closure criterion の一意な帰属、後続 child への closure 依存が
ないこと、source が許可した item/handoff authority reference を照合する。
`SplitPhaseCandidateGateDerivation` はこの契約がない候補の closure/authority を従来どおり
`Pending` に保ち、source digest・Phase identity・authority・item 順序・criteria・
依存・許可参照が一致する場合だけ候補 gate の evidence digest を導出する。
現段階の contract は純粋な検証入力で、公開 source reader/Operation からの供給は
未実装である。したがって fixture で gate が通っても、実 Phase の authoritative
projection や proposal admission の完成証明にはならない。
限定 `testOnly`（`SMWF-P1-SOURCE-BOUNDARY-FOCUSED-20260927-27`）は7 test、
全 `test`（`SMWF-P1-SOURCE-BOUNDARY-FULL-20260927-28`）は111 test 成功し、
両方とも `sbt_exit=0`、`wrapper_exit=0`、`lock=released` の receipt を確認した。

続いて公開 `assessSplitProposal` の host-owned proposal port に source snapshot identity
指定の `loadSourceBoundaryC` を追加した。既存の calibration・partition 検証が済んだ後に
別取得した source 契約と child closure、前提 item、item/handoff/semantic-edge の
authority reference を照合する。契約未提供なら `SourceBoundaryRequired` として
revision 1 の `WAIT` を保ち、不一致は拒否する。一致した場合だけ proposal validation
digest と closure/authority digest を束ねて revision 2 の証拠にする。公開 Service fixture
は同一 command の WAIT 後の再試行で revision 2 に進むことを確認した。
ただしここでの port 実装は fixture であり、実 planning 文書から contract を抽出する
production reader、全候補 admission、最終 optimizer、PREVIEW/APPLY は未実装である。
限定 `testOnly`（`SMWF-P1-PUBLIC-BOUNDARY-FOCUSED-20260927-29`）は29 test、
全 `test`（`SMWF-P1-PUBLIC-BOUNDARY-FULL-20260927-30`）は113 test 成功し、
両方とも `sbt_exit=0`、`wrapper_exit=0`、`lock=released` の receipt を確認した。

次に `SplitPhaseSourceBoundaryDocumentReader` を追加した。host が固定した
source snapshot identity/digest、Phase identity/authority、Phase Markdown の SHA-256
を受け、`docs/phase/phase-<identity>.md` の実 bytes を照合してから versioned
`split-phase-source-boundary.v1` block だけを読む。block のない従来の自然文 Phase は
`None` として WAIT を維持し、宣言された block の不正、stale file、identity/authority
不一致は拒否する。reader を公開 assessment port に差し込む fixture は、proposal port の
自己申告ではなく実ファイルから契約を取得して revision 2 用の証拠を作る。
実 planning repository の snapshot catalog 登録、既存 Phase 文書への明示 block の追加、
全候補 admission と最終 optimizer、PREVIEW/APPLY はなお未実装である。
限定 `testOnly`（`SMWF-P1-SOURCE-READER-FOCUSED-20260927-31`）は12 test、
全 `test`（`SMWF-P1-SOURCE-READER-FULL-20260927-32`）は118 test 成功し、
両方とも `sbt_exit=0`、`wrapper_exit=0`、`lock=released` の receipt を確認した。

続いて `SplitPhaseSourceSnapshotCatalog` を追加した。host が指定した snapshot
identity に対応する `docs/phase/snapshots/<identity>.json` を読み、catalog の実 bytes
の SHA-256 を Start と proposal assessment に共通の source digest として渡す。
catalog は Phase identity/authority、unfinished/occupied item の順序、Phase Markdown
の SHA-256 を保持し、proposal 側は同じ catalog から source inventory と boundary
document reference を取得する。再取得時の digest drift、stale Phase file、不正な
catalog/identity は拒否する。catalog が存在しない場合、または自然文だけで boundary
block のない Phase 文書の場合は契約を推測せず `Pending`/`WAIT` に保つ。
fixture では Start と proposal assessment の digest 一致、stale 拒否、prose-only の
未解決を確認した。実 planning repository の catalog と明示 boundary block の登録、
全候補 admission と最終 optimizer、PREVIEW/APPLY はなお未実装である。
限定 `testOnly`（`SMWF-P1-SOURCE-CATALOG-FOCUSED-20260927-33`）は2 test、
全 `test`（`SMWF-P1-SOURCE-CATALOG-FULL-20260927-34`）は121 test 成功し、
両方とも `sbt_exit=0`、`wrapper_exit=0`、`lock=released` の receipt を確認した。

さらに catalog を実ファイルから読む port を Start execution と proposal assessment
execution の同じ run に通す fixture を追加した。source digest が一致すれば revision 2
まで進み、Start の後に catalog bytes が変わると proposal evidence の append 前に
拒否して revision 1 に留まる。実 planning Phase に明示された source boundary はまだなく、
この fixture は実 Phase の closure/authority を認定するものではない。
限定 `testOnly`（`SMWF-P1-CATALOG-EXECUTION-FOCUSED-20260927-35`）は16 test、
全 `test`（`SMWF-P1-CATALOG-EXECUTION-FULL-20260927-36`）は123 test 成功し、
両方とも `sbt_exit=0`、`wrapper_exit=0`、`lock=released` の receipt を確認した。

次に `SplitPhaseCandidateSetAssessment` を追加した。単一の host-owned catalog
snapshot から source inventory と boundary を取得し、列挙済みの全 candidate に
一対一の evidence があることを確認してから各 gate を導出する。候補ごとに異なる
source 契約を caller が差し込むことを拒否し、契約のない source では closure と
authority を `EvidenceRequired` に留める。結果は admissibility と duration frontier
までであり、cost-aware winner の選定、proposal admission、PREVIEW/APPLY は行わない。
全 `test`（`SMWF-P1-CANDIDATE-SET-FULL-20260927-37`）は126 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` の receipt を確認した。

候補集合の評価では、構造的に不正な evidence は引き続き全体を拒否する一方、
正しく記述された短時間 child の不適格性はその候補だけを `Rejected` にするよう修正した。
これにより他の候補の gate と duration 順位を失わない。さらに
`SplitPhaseObjectiveDecision` は、全候補が決着し duration leader が一つなら
`DurationDecisive`、同点なら `CostPolicyRequired` を返す。後者を辞書順で採用せず、
cost-isolation policy と overhead 評価、proposal admission、PREVIEW/APPLY は未実装のまま
明示的に残す。全 `test`（`SMWF-P1-OBJECTIVE-DECISION-FULL-20260927-38`）は
127 test 成功し、`sbt_exit=0`、`wrapper_exit=0`、`lock=released` の
検証済み receipt を確認した。

続いて `SplitPhaseCostIsolation` に duration 同点候補だけを対象にした純粋な採点器を
追加した。明示された versioned policy の高コスト・低コスト単価と material-saving
閾値、全 work item の reasoning demand を必須とし、同じ candidate/source digest に
結び付けて低コスト profile に隔離できる child の節約量を算出する。fixture の数値は
採点規則の検証用であり、実際のモデル価格を主張しない。この段階では source 側の
独立した policy・demand evidence reader がなく、cost frontier の preview に限り、
最終候補の選定や proposal admission には使わない。全 `test`
（`SMWF-P1-COST-ISOLATION-FULL-20260927-39`）は127 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` の検証済み receipt を確認した。

続いて `SplitPhaseCostEvidenceCatalog` を追加した。host-owned
`docs/phase/cost-policies/<snapshotIdentity>.json` の厳密な schema と path を検証し、
元の catalog の実 bytes から得た snapshot digest、Phase identity、authority、
ordered item coverage に一致する policy と reasoning demand だけを採用する。
文書欠落は `CostPolicyRequired`、stale/malformed 文書は拒否する。
`decideCatalogWithCostC` は duration 同点時だけこの file-backed evidence を読み、
cost frontier と policy/document digest を `CostRanked` として返す。これは最終 winner
の admission ではなく、後続の overhead・authority tie-break と proposal admission は
未実装である。fixture の単価は検証用の任意単位であり、実運用の価格設定ではない。
全 `test`（`SMWF-P1-COST-SOURCE-FULL-20260927-40`）は131 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` の検証済み receipt を確認した。

続いて `SplitPhaseOverheadPriority` と `SplitPhaseOverheadPolicyCatalog` を追加した。
duration と cost の両方で同点となった候補だけについて、child setup、semantic
handoff、validation run、review、commit の明示的な versioned 重みを採点する。
validation run は `FinalOnly` なら1回、`EachPhase` なら child 数とし、各 child の
expected duration に計上済みの Phase overhead は二重計上しない。policy は host-owned
`docs/phase/overhead-policies/<snapshotIdentity>.json` から読み、source snapshot の
実 bytes の digest、Phase identity、authority との一致を要求する。文書欠落は
`OverheadPolicyRequired`、stale/malformed 文書は拒否する。
`decideCatalogWithOverheadC` は同点時に `OverheadRanked` と policy document digest を
返すが、これは候補の preview であり proposal admission や planning write ではない。
fixture の重みは採点規則の検証用の任意単位であり、実運用 policy ではない。
全 `test`（`SMWF-P1-OVERHEAD-SOURCE-FULL-20260927-41`）は134 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` の検証済み receipt を確認した。
残るのは実運用 policy 値、同点時の authority を考慮した最終 tie-break、
proposal admission、PREVIEW/APPLY である。

次に `SplitPhaseFinalTieBreak` と `decideCatalogWithFinalC` を追加した。
duration、cost、overhead の各段階で一意になれば objective 上の選定結果を返す。
それでも複数候補が残る場合、host-owned source boundary から各候補の authority
digest を再検証し、handoff の authority reference と授受 child の item 所有も
署名に含める。署名が異なれば `AuthorityDecisionRequired` として human
Decision を要求し、同じ場合だけ child 数、次いで source item 順の boundary vector
で安定的に一意化する。いずれも objective preview であり、proposal admission や
planning document の更新権限ではない。実運用 policy 値、proposal admission、
PREVIEW/APPLY は引き続き残る。全 `test`
（`SMWF-P1-FINAL-TIE-FULL-20260927-45`）は134 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` の検証済み receipt を確認した。

続いて `SplitPhaseProposalAdmission` を追加した。objective が一意に選んだ
candidate の draft identity/digest と Start が参照する proposal を一致させ、既存の
proposal assessment（shape、calibration、handoff、validation ownership、source
boundary）を host-owned snapshot 経由で実行する。未選択・proposal 欠落は pending、
不一致や検証中の source drift は拒否する。これは fixture で検証した in-memory の
admission 判定であり、公開 Operation や planning write の実装ではない。
全 `test`（`SMWF-P1-PROPOSAL-ADMISSION-FULL-20260927-46`）は135 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` の検証済み receipt を確認した。
`revalidateCatalogC` で、admission に束縛した source snapshot、partition policy、
cost/overhead policy 文書の digest と proposal evidence を現在の host-owned 入力から
再評価し、以前の outcome と異なれば拒否する。実ファイルの cost policy を変更した
fixture でも古い admission は失効する。全 `test`
（`SMWF-P1-PROPOSAL-RECHECK-FULL-20260927-47`）は135 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` の検証済み receipt を確認した。
これは PREVIEW/APPLY に渡す直前の判定境界であり、文書の読み取り projection、
書き込み、CAS、公開 Operation はまだ実装していない。cost/overhead の fixture 重みを
実運用値として採用したわけでもない。

SplitPhase の generated ABI は `split-phase-preview-v1` に進め、`Previewed`
state、`previewSplitPhase` Provided Operation、専用 command/result 型を追加した。
CNCF の actionless entry binding も Start → proposal assessment → terminal の
3段階を受け入れる。CNCF の focused test と sm-workflow の全 `test`
（`SMWF-P1-PREVIEW-ABI-FULL-20260927-57`、136 test 成功）は通過した。
続いて `SplitPhasePreviewExecution` と公開 `previewSplitPhase` binding を接続した。
revision 2 の proposal-assessed instance と PREVIEW command のみを受け、host-owned
catalog の admission を投影前後に再照合する。成功時だけ revision 3 の Completed
history を CAS append し、child・handoff を含む `SplitPhasePreviewResult` の
Terminal JSON を返す。古い admission、APPLY mode、異なる revision、append 失敗は
Terminal にせず拒否する。再送による二重 append も拒否する。focused test
（`SMWF-P1-PREVIEW-RUNTIME-RETEST-20260927-59`）で public Operation まで確認した。
source/policy の再照合と instance append は単一の共有 transaction ではないため、
両者の間の競合を完全には排除しない。APPLY と文書 write は引き続き範囲外である。

`SplitPhasePreviewProjection` は再照合済み admission と host-owned source boundary から、
子 Phase の work item、closure criterion、期待時間、検証担当・依存、semantic handoff を
型付きの読み取り専用結果へ投影する。proposal/validation evidence は一度取得した値に
固定して再照合し、投影後も再照合する。fixture の source Phase file bytes が変わらず、
新しい child Phase file も作らないことを確認した。全 `test`
（`SMWF-P1-PREVIEW-PROJECTION-FULL-20260927-48`）は136 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` の検証済み receipt を確認した。
これは PREVIEW の表示データ境界の独立検証記録である。後続作業で Workflow の
`SPLIT_PREVIEWED` terminal と公開 Operation に接続した。

次に `SplitPhaseDocumentProjection` を追加した。host-owned catalog/admission を再照合して
source Phase file の exact bytes を読み、source と child Phase の desired Markdown、
proposal digest、各 path の expected/desired SHA-256 を read-only の write-set plan にする。
既存の source Scope 表・boundary block 以外の内容は保持し、未対応の形式、source drift、
既存 child path は拒否する。fixture の 21 focused test
（`SMWF-P1-DOCUMENT-PROJECTION-FOCUSED-20260927-61`）が成功した。
さらに `SplitPhaseDocumentConflict` は read port で現在の各 path を読み、exact base なら
`Ready`、全 path が desired bytes なら `AlreadyApplied`、部分適用や別内容なら
`Conflict` と分類する。host 側の安全な path 読み取りを契約とし、digest 差分を
semantic merge と推定しない。focused test
（`SMWF-P1-DOCUMENT-CONFLICT-FOCUSED-20260927-63`）が成功した。
全 `test`（`SMWF-P1-DOCUMENT-CONFLICT-FULL-20260927-64`）も142 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
続いて `SplitPhaseDocumentCas` に、全 path の expected digest を一度に照合して
全 desired bytes を一括適用する host port 契約と呼び出し境界を追加した。
適用後に全 path が desired digest と一致することを読み直し、同一再送は書き込みなしで
`AlreadyApplied`、CAS 中の revision drift は `Conflict` または同一内容なら
`AlreadyApplied` とする。fixture の in-memory port による focused test
（`SMWF-P1-DOCUMENT-CAS-FOCUSED-20260927-65`）が成功した。
全 `test`（`SMWF-P1-DOCUMENT-CAS-FULL-20260927-66`）も146 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
これは返却結果について部分適用を残さない port 契約と検証である。
次に `SplitPhaseDocumentStaticValidation` を追加した。既存の厳密な boundary decoder を
投影文書の事前解析にも使い、source/child の Phase identity、suffix order、Scope 表、
closure/prerequisite/authority の全 item coverage、proposal/source digest provenance を
照合する。不整合があれば CAS port の読み書き前に拒否する。focused test
（`SMWF-P1-DOCUMENT-STATIC-FOCUSED-20260927-67`）が成功した。
全 `test`（`SMWF-P1-DOCUMENT-STATIC-FULL-20260927-68`）も148 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
これは現在の狭い Phase-document fixture 用の静的事前検証であり、strategy/index、
semantic links、公開 APPLY Operation は未接続である。
実 Phase 文書への書き込みは行っていない。
`SplitPhaseDocumentFilePort` を追加し、協調 lock 下で全 expected digest を再照合し、
同一 Phase ディレクトリの一時ファイルから適用する。途中失敗は元の source と child
の不存在へ補償復元する。コピーした fixture に限って適用・同一再送・child 競合・
途中失敗・symlink 拒否を検証する。これは緩いトランザクション管理であり、
プロセス停止・停電時の multi-file atomicity は保証しない。実 Phase 文書、
公開 APPLY Operation、Workflow state への接続はまだ行わない。
全 `test`（`SMWF-P1-DOCUMENT-FILE-FULL-20260927-70`）は153 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
`SplitPhaseApplyDocuments` は host-only の APPLY 文書境界である。明示的に選択された
APPLY Start、revision 2 の proposal-assessed instance、現在の host-owned admission、
source snapshot と projected document の静的検証を通してから、コピーfixtureの
write set をファイルportへ渡す。PREVIEW mode は catalog 読み取り前に拒否する。
文書適用と同一 prepared plan の再送を検証した。この時点の文書専用経路は
Workflow instance を revision 2 の Active のままにする。
全 `test`（`SMWF-P1-APPLY-DOCUMENTS-FULL-20260927-71`）は155 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
続いて `SplitPhaseApplyPlanStore` は instance と Start command に結び付いた計画を
host-owned な不変JSONとして、文書適用前に非上書きで保存する。同一再送、変更された
計画・Start、改ざんされた保存内容を検証する。`SplitPhaseApplyExecution` は計画保存、
revision 3 のintent履歴、文書CAS、revision 4 のCompleted履歴を順に進める。
intent append失敗時には文書を変更せず、完了append失敗後には保存計画から再開する。
完了済み再送はsource catalogを再読せず、保存計画と適用済み文書を照合する。
全 `test`（`SMWF-P1-APPLY-RESUME-FULL-20260927-73`）は160 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
これはコピーfixtureのhost-only経路であり、この時点では公開 APPLY Operation の
生成ABI接続、strategy/index と semantic-link の全検証、プロセス停止中の
複数ファイル復旧が残っていた。

続いて `SplitPhaseWorkflow` を `split-phase-apply-v1` に進め、`Applied` state と
`applySplitPhase` Provided Operation、専用 command/result 型を追加した。CNCF の
actionless entry binding は PREVIEW/APPLY の二つの終端分岐を認識し、既存の
単一終端 ABI digest は変更しない。公開 APPLY は revision 2 の開始から保存計画、
intent、文書 CAS、Completed 履歴を進め、同一 command の再送では source catalog
を再読せず、変更された APPLY idempotency key を拒否する。Service fixture の実行は
コピーした Phase 文書だけを対象にする。実 Phase 文書には適用していない。
strategy/index と semantic-link の全検証、プロセス停止中の複数ファイル復旧は
引き続き別の境界である。
Cozy の CML 再生成後、公開 APPLY の Service fixture を含む全 `test`
（`SMWF-P1-APPLY-PUBLIC-FULL-20260928-81`）は161 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
この Service fixture は host-owned port を明示注入している。通常の
`ComponentFactory` はまだ `SplitPhaseService` を登録しておらず、Cozy 生成の
Operation 本体は未実装 placeholder である。したがって launcher からの実行証明ではない。
次の接続境界は host-owned port を注入した fixture component assembly と
launcher JSON adapter の同値性検証（P1-S10）であり、placeholder をそのまま
公開しない。
`SplitPhaseFixtureServiceAssembly` は CML に列挙された4 Operation の順序と identity
を照合し、host-owned port を注入した4つの実行 binding を単一 Service に束ねる。
コピーした Phase fixture の同一 in-process component から Start → proposal assessment
→ APPLY terminal と同一再送を通した focused test
（`SMWF-P1-FIXTURE-ASSEMBLY-FOCUSED-20260928-82`）は42 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。これは
launcher プロセスの JSON 経路や production factory の登録証明ではなく、P1-S10 は未完了である。
さらに公開4 Operation の request schema に fixture JSON を単一の `arg1`
位置引数として宣言した。`CncfRuntime.executeCommandResponse` の `--json` grammar
から同一 component の APPLY を再送し、直接 Operation 呼び出しと同じ terminal JSON
および unknown selector の拒否を確認した。最初の focused test
（`SMWF-P1-LAUNCHER-GRAMMAR-FOCUSED-20260928-83`）では引数宣言不足により
41/42 test 成功・1 test 失敗となり、この宣言追加後の focused retest
（`SMWF-P1-LAUNCHER-GRAMMAR-RETEST-20260928-84`）は42/42 test 成功、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` だった。これは in-process
CNCF command adapter の検証であり、launcher プロセス起動や production factory
登録の証明ではないため、P1-S10 は引き続き未完了である。
同じ fixture Service で Start と proposal assessment の直接 Operation 応答を
CLI `--json` 再送と照合し、APPLY の terminal 応答に加えて不正 JSON と未知 selector
の拒否を検証した focused test（`SMWF-P1-LAUNCHER-GRAMMAR-TRIPLE-20260928-85`）は
42/42 test 成功、`sbt_exit=0`、`wrapper_exit=0`、`lock=released` だった。
この検証も in-process の `CncfRuntime` 経路であり、実 launcher プロセスの
factory composition を示すものではない。
さらに WorkOrder の `completionOperation` が指定する GoalPhase
`submitStepReview` に共通の JSON `arg1` request schema を与えた。独立した
fixture で直接 Operation と CNCF CLI `--json` をそれぞれ実行し、accept の
Terminal と repair の Wait の common envelope が各分岐で一致すること、および
同じ result の二重提出が両経路で拒否されることを確認した。GoalPhase と
SplitPhase の focused test（`SMWF-P1-GOAL-COMPLETION-CLI-20260928-86`）は
48/48 test 成功、変更後の sm-workflow 全 `test`
（`SMWF-P1-TRANSPORT-ABI-FULL-20260928-87`）は162/162 test 成功し、
ともに `sbt_exit=0`、`wrapper_exit=0`、`lock=released` だった。
これで Continuation-selected completion の transport 非依存性は fixture
で示したが、実 `CncfMain` プロセスへの host-bound fixture factory 注入と
その JSON 入出力は未検証である。通常の production factory に placeholder を
追加せず、この残境界を P1-S10 に維持する。
続いて GoalPhase の `startGoalPhase` と `requestStepClose` にも同じ単一
`arg1` JSON request schema を宣言した。Start の直接 Operation 呼び出しと
in-process CNCF CLI `--json` 再送が同じ Wait envelope を返すこと、不正 JSON
が拒否されることを確認し、Step-close の WorkOrder 分岐も CLI 経由で検証した。
focused test（`SMWF-P1-GOAL-CLI-FOCUSED-20260928-88`）は10/10 test 成功した。
さらに test classpath 専用 factory に host-owned の選択・instance port を注入し、
別 JVM の `CncfMain` に `GoalPhaseService.startGoalPhase` の JSON command を渡した。
`SMWF-P1-CNCFMAIN-START-SMOKE-20260928-89` は実 launcher から
`cncf.workflow-protocol.v1` の Wait JSON（expectedRevision `1`）を返した。
変更後の全 `test`（`SMWF-P1-CNCFMAIN-FULL-20260928-90`）は162/162 test 成功し、
いずれも `sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
これにより P1-S10 の fixture 範囲、すなわち登録済み typed Operation の
launcher JSON adapter と Continuation-selected completion の transport 非依存な
common envelope は成立した。production factory への登録や永続化運用の証明ではない。
生成 Operation placeholder を production に公開せず、実 Phase 文書にも変更を加えていない。
P1-S09 の公開 Start 経路についても、GoalPhase と SplitPhase の CLI JSON fixture で
host selection の origin を Recommendation / Skill / Workflow / TerminalResult に
置き換え、別 profile の Start Operation を指定した場合をそれぞれ拒否する検証を追加した。
拒否時は WorkflowInstance の create/append がともにゼロのままで、正しい
ExplicitHuman selection を戻すと従来どおり開始できる。focused test
（`SMWF-P1-START-AUTH-FOCUSED-20260928-91`）は46/46 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
この時点では RepositorySync の profile-specific Start が未定義だったため、
三 profile 全体を求める P1-S09 は未完了だった。
次の RepositorySync entry slice では、normative design の one repository・named local
branch・configured tracking mapping を `RepositorySyncStartInput` に置き、
`RepositorySyncWorkflow` の actionless `BoundaryPending` と
`RepositorySyncService.startRepositorySync` を CML から生成した。
`RepositorySyncDefinitionBinding` は生成された Workflow / Provided API の一致を
CNCF entry ABI に照合し、`RepositorySyncStartCommandAdapter` は `FULL_STATE` の
typed input と host-owned `ExplicitHuman` selection の一致だけを common
`WorkflowStartRequest` に写す。推奨、Skill、Workflow、terminal result 由来の
selection、別 profile の Operation、不完全な tracking 入力は拒否する。
最初の生成・focused test（`SMWF-P1-REPOSYNC-ENTRY-20260928-92`）は生成順の
クラス名指定を誤って compile failure となり、修正後の focused retest
（`SMWF-P1-REPOSYNC-ENTRY-RETEST-20260928-93`）は4/4 test 成功した。
全 `test` の初回（`SMWF-P1-REPOSYNC-ENTRY-FULL-20260928-94`）は
GoalPhase fixture の CML source hash / source line が旧値で 165/166 test 成功・
1 test 失敗となった。生成結果に合わせて GoalPhase の二つの ABI fixture と pin を
更新した後の全 retest（`SMWF-P1-REPOSYNC-ENTRY-FULL-RETEST-20260928-95`）は
166/166 test 成功し、`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
続く Start execution fixture では、host tracking mapping と入力の完全一致を
WorkflowInstance 作成前に要求し、生成 definition に沿った `NotStarted` record から
`sync.BoundaryAssessment` の `Active` progression と Wait を返す。明示的な人間の
selection がない場合や mapping が違う場合は instance を作らず、同一 key の再送は
idempotent に扱い、同じ key で変更された入力は拒否し、初回 append failure 後は
同じ Start を再開できる。focused test
（`SMWF-P1-REPOSYNC-START-20260928-96`）は4/4 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
さらに生成された `RepositorySyncService.startRepositorySync` を公開 fixture Operation
に binding し、direct Operation と in-process CNCF CLI `--json` が同じ Wait JSON を返す
ことを確認した。推奨・Skill・Workflow・terminal result 由来の selection、別 profile
Operation の selection、host tracking mapping の不一致、余剰 field や不正 JSON は
instance 作成前に拒否される。変更後の全 `test`
（`SMWF-P1-REPOSYNC-AUTH-FULL-20260928-98`）は171/171 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
これで P1-S09 の三 profile の fixture 証明は完了とする。この fixture は Git 操作を
行わず、production factory 登録も行わない。fetch / merge / push と convergence の
運用検証はこの Start 権限境界とは別であり、Phase 1 全体は引き続き未完了である。
次の RepositorySync boundary slice では、CML に
`RepositorySyncService.assessRepositoryBoundary` と `sync.BoundaryCaptured` を追加し、
definition revision を `repository-sync-boundary-v1` に更新した。host の snapshot port
が返す repository/worktree、named branch、tracking mapping、endpoint、policy version、
HEAD と tree/index/worktree digest、dirty path set、index shape を検証し、detached HEAD、
pending merge、partial index、不正 path、設定不一致を拒否する。受理した snapshot は
digest に結び付けて同じ WorkflowInstance の revision 2 へ進める。同一 snapshot と
idempotency key の再送は再 append せず、変更された snapshot は拒否する。公開
Operation と in-process CLI `--json` が同じ Wait envelope を返すことも確認した。
生成と focused test の初回（`SMWF-P1-REPOSYNC-BOUNDARY-20260928-99`）では
append 後の causation 照合が強すぎて3 test が失敗した。Start 履歴の照合を維持して
修正し、focused retest（`SMWF-P1-REPOSYNC-BOUNDARY-RETEST-20260928-100`）は
13/13 test 成功した。GoalPhase fixture の CML source line と hash pin を更新した後、
全 `test`（`SMWF-P1-REPOSYNC-BOUNDARY-FULL-RETEST-20260928-102`）は
175/175 test 成功し、`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
次の dirty-scope slice では、同じ `assessRepositoryBoundary` の host port に
snapshot digest・policy version・staged path set・全 dirty path の scan set・
unsafe findings・scan digest を追加した。snapshot と異なる path、欠けた scan、
partial staging、unsafe finding、別 snapshot/policy の証拠を revision 2 への進行前に
拒否する。同一証拠の再送だけを idempotent に受理し、変更された scan digest は拒否する。
Git や filesystem の読み取りは Workflow に追加しない。focused test
（`SMWF-P1-REPOSYNC-DIRTY-FOCUSED-20260928-103`）は11/11、全 `test`
（`SMWF-P1-REPOSYNC-DIRTY-FULL-20260928-104`）は177/177 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
この boundary slice は host が取得した local snapshot と dirty-scope 証拠の形を
照合するところまでである。実ファイル内容の独立検証、fetch、relation classification、
checkpoint、merge、push、convergence、および RepositorySync terminal は未実装で、
P1-S01 と Phase 1 全体は未完了である。
次の fetch-admission fixture では `RepositorySyncFetchAssessment` を追加し、host が返す
receipt の repository/worktree、tracking mapping と endpoint、fetched tip、対象 ref と
変更 ref、fetch 前後の local HEAD と tree/index/worktree digest、endpoint policy、完了状態を
boundary および dirty-scope digest に照合した。対象外 ref の変更、local state の変更、
失敗または未承認の fetch、tip の不一致は拒否する。tracking ref がすでに fetched tip を
指す no-op fetch も受理する。最初の focused test
（`SMWF-P1-REPOSYNC-FETCH-FOCUSED-20260928-105`）はテスト fixture の tip 不整合で
4/5、修正後の focused retest（`SMWF-P1-REPOSYNC-FETCH-RETEST-20260928-106`）は5/5、
全 `test`（`SMWF-P1-REPOSYNC-FETCH-FULL-20260928-107`）は182/182 test 成功し、
`sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
これは純粋な receipt admission であり、実際の fetch、CML の次 state と公開 Operation、
checkpoint 後の relation classification はまだ接続していない。P1-S01 と Phase 1 全体も
引き続き未完了である。
続いて CML に `RepositorySyncService.admitTrackingFetch`、typed fetch command/result、
`sync.FetchAdmitted` を追加し、definition revision を `repository-sync-fetch-v1` に更新した。
既存の CNCF `GeneratedEntryWorkflowAbi` が一つの中間段階しか受理しなかったため、
既存 shape の digest 構成を維持したまま ordered additional stages の検証を追加した。
CNCF の focused test（`SMWF-P1-CNCF-MULTISTAGE-FOCUSED-20260928-108`）は7/7成功し、
同じ `0.5.3-SNAPSHOT` を dependency refresh として `publishLocal`
（`SMWF-P1-CNCF-MULTISTAGE-REFRESH-20260928-109`）した。Cozy の変更は不要だった。
公開 fetch fixture は Start と boundary の履歴、選択・mapping、snapshot/dirty-scope digest、
host fetch receipt を照合して同じ instance の revision 3 に一回だけ append する。
同じ receipt/key の再送は同じ Wait を返し、不正 revision、別 boundary key、欠けた receipt、
local drift、変更された receipt/key は進行を拒否する。append failure では revision 2 を
維持し、matching no-op fetch の再試行も検証した。direct Operation と in-process CLI
`--json` は同じ generic Wait envelope を返す。
CML 再生成後の初回全 test（`SMWF-P1-REPOSYNC-FETCH-PROGRESSION-20260928-110`）は
新しい spec の括弧抜けで Test compile failure となった。修正後の全 retest
（`SMWF-P1-REPOSYNC-FETCH-PROGRESSION-RETEST-20260928-111`）は34 suite・186/186 test 成功し、
108/109/111 の `sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
初回110も `sbt_exit=1`、`wrapper_exit=1`、`lock=released` で終了している。
GoalPhase の既存 ABI fixture は再生成された source line に合わせ、CML/hash pin も更新した。
これは host-owned port を注入した deterministic fixture であり、実 Git fetch、production
factory 登録、checkpoint/clean gate、relation classification、merge/push/convergence、
RepositorySync terminal はまだ実装していない。次は dirty full-state checkpoint と clean gate
の receipt admission を接続する。P1-S01 と Phase 1 全体は引き続き未完了である。
次の preservation slice では `RepositorySyncCheckpointAssessment` を追加した。
boundary、dirty-scope、fetch の既存 assessment を再受理して digest の差し替えを拒否し、
host の full-state freeze を source HEAD/tree/index/worktree digest、全 dirty path、
policy version、fetch digest と照合する。freeze が明示した complete tree OID/digest と
checkpoint receipt の committed tree の一致、元 HEAD のみを親とする一親 commit、
`FULL_STATE_NON_ACCEPTANCE` purpose、完了済み execution evidence を要求する。
続く clean gate は checkpoint HEAD/tree、repository/worktree/branch、tracking mapping、
policy、fetch 済み tip の保持、empty index、dirty path なし、detached/pending merge なしを
照合する。初期 snapshot が clean の経路では freeze/checkpoint receipt を読み込まず、
元 HEAD/tree digest と同じ clean gate を要求する。失敗した段階から後続 evidence load に
進まず、freeze/commit/inspection evidence の変更は result digest に反映する。
focused test（`SMWF-P1-REPOSYNC-CHECKPOINT-FOCUSED-20260928-112`）は10/10、
全 `test`（`SMWF-P1-REPOSYNC-CHECKPOINT-FULL-20260928-113`）は35 suite・196/196成功し、
両 invocation の `sbt_exit=0`、`wrapper_exit=0`、`lock=released` を確認した。
これは read-only host ports の証拠を照合する純粋な fixture である。complete tree が実際の
全 dirty ファイル内容を表すことの独立検証、history maintenance、freeze/commit 実行、
CML の公開 preservation Operation と次 state、relation classification は未実装である。
CNCF/Cozy/CML は変更せず、実 Git 操作・コミットも行っていない。次はこの assessment を
公開 Operation と durable progression に接続する。P1-S01 と Phase 1 全体は未完了のまま。

続く preservation progression slice では、CML に
`RepositorySyncPreservationAdmissionCommand/Result` と公開 Operation
`RepositorySyncService.admitLocalPreservation` を追加し、RepositorySyncWorkflow を
`repository-sync-preservation-v1` に更新した。generated Workflow / Provided API は
四つの stage Operation と `LocalStatePreserved` state を出力する。
`RepositorySyncPreservationAdmissionExecution` は既存 selection/mapping、元の
boundary/dirty-scope と fetch receipt を再受理し、Start/boundary/fetch の三件の履歴を
完全一致で確認してから preservation evidence を読む。受理された checkpoint/clean-gate
digest と preservation key を causation に結び付け、同じ WorkflowInstance の revision 4
に `sync.LocalStatePreserved` を一回だけ append する。
初期 clean、dirty、complete-fully-staged の各 fixture、同じ evidence/key の再送、変更された
boundary/fetch/preservation key や fetch/checkpoint/inspection evidence の拒否を確認した。
証拠不足・不一致・append failure は revision 3 を維持し、matching retry は重複 append を
起こさない。public binding は欠けた port や別 Workflow definition を拒否し、direct Operation
と in-process CLI `--json` は同じ generic Wait を返す。fetch 後の clean-route presentation も
relation classification の前に clean gate を要求する順序に合わせた。
CML 追加で移動した GoalPhase ABI fixture の source line と CML/fixture hash pin は、再生成
された Workflow / Candidate-Admission producer の source correlation に合わせて更新した。
生成と focused test（`SMWF-P1-REPOSYNC-PRESERVATION-FOCUSED-20260928-114`）は14/14成功。
追加した binding 拒否 test が private-constructor class の `DefinitionBinding.copy` を使ったため、
初回 full test（`SMWF-P1-REPOSYNC-PRESERVATION-FULL-20260928-115`）は Test compile failure
で終了した。既存の admitted SplitPhase binding を使う test に修正した後の full retest
（`SMWF-P1-REPOSYNC-PRESERVATION-FULL-RETEST-20260928-116`）は36 suite・203/203成功した。
114/116 は `sbt_exit=0`、`wrapper_exit=0`、115 は両 exit=1、すべて `lock=released` を確認した。
これは read-only host ports と in-memory persistence を注入した deterministic fixture であり、
実 freeze/checkpoint 作成やファイル内容の独立証明、production factory 登録、永続 store の
実装・再起動検証、relation classification、merge/push/convergence、RepositorySync terminal は
未実装である。CNCF/Cozy の変更、実 Git 操作・コミットは行っていない。次は preservation
後の local tip と fetch 済み tracking tip の relation classification を接続する。
P1-S01 と Phase 1 全体は引き続き未完了である。

relation assessment slice では `RepositorySyncRelationAssessment` を追加した。
boundary/dirty-scope/fetch/preservation の既存 evidence を再受理し、preservation digest と
clean gate が保持した repository/worktree、tracking mapping、policy、post-checkpoint local tip、
fetched tracking tip に一致する host graph receipt だけを読む。結果は `EQUAL`、`LOCAL_AHEAD`、
`REMOTE_AHEAD`、`DIVERGED` の closed classification と次の deterministic action の案内である。
一致には同じ merge base と ahead/behind=0/0、片側 ahead には反対側 tip を唯一の merge base
とすること、分岐には両 count>0 と両 tip とは異なる唯一の merge base を要求する。
unrelated/複数又は重複 merge base、負の count、矛盾した shape、incomplete/replacement history、
古い tip/mapping/preservation/policy を拒否し、upstream assessment の digest 差し替えは graph
port を読む前に失敗する。inspection evidence と count は result digest に結び付ける。
remote-ahead の元 HEAD 上で checkpoint を作った fixture は、checkpoint local tip と保存済み
remote tip で分岐になることを確認した。元 local tip への差し戻しは拒否し、暗黙の再fetchや
snapshot の再取得は行わない。SHA-1/SHA-256 の一貫した OID width と各四分類も検証した。
focused test（`SMWF-P1-REPOSYNC-RELATION-FOCUSED-20260928-117`）は11/11、全 `test`
（`SMWF-P1-REPOSYNC-RELATION-FULL-20260928-118`）は37 suite・214/214成功した。
両 invocation の `sbt_exit=0`、`wrapper_exit=0`、`lock=released` と immutable receipt を確認した。
これは host-reported graph facts の整合性を照合する純粋な fixture であり、実 commit graph の
独立走査・証明、公開 relation Operation/CML state/durable progression、fast-forward/merge/push/
convergence、RepositorySync terminal は未実装である。CNCF/Cozy/CML の変更、実 Git 操作・
コミットは行っていない。次はこの分類結果を公開 Operation と次 state に接続する。
P1-S01 と Phase 1 全体は引き続き未完了である。

relation progression slice では、CML に `RepositorySyncRelationAdmissionCommand/Result`、
公開 `RepositorySyncService.admitTipRelation`、`RelationClassified` state を追加し、
RepositorySync の generated Workflow / Provided API revision を
`repository-sync-relation-v1` に更新した。CNCF / Cozy の source や機能には変更を加えていない。

`RepositorySyncRelationAdmissionExecution` は、元の Start と boundary/scope/fetch の
history prefix を照合し、persisted revision 4 の preservation receipt / clean gate と
一致する graph receipt だけを admission する。同じ WorkflowInstance に
`sync.RelationClassified` の revision 5 を一度だけ append し、relation digest を
causation と WAIT context に結び付ける。四つの relation は共通の classified state で
受け、typed assessment に基づく next-action を presentation に返す。この presentation は
後続 route の authority / selector ではない。

保持済み証拠と idempotency key が同じ再送は同じ WAIT を返し、履歴を増やさない。
異なる key / graph receipt / upstream evidence は拒否する。preservation 前には graph port
を読まず、不足・不整合・host load failure・append failure は revision 4 を残す。
公開 Operation と CNCF の in-process CLI JSON について、EQUAL / LOCAL_AHEAD /
REMOTE_AHEAD / checkpoint 後の DIVERGED の四経路で同じ WAIT を返すことを確認した。

生成 ABI に合わせ、GoalPhase の固定 producer fixture の source line と CML/fixture SHA を
更新した。focused 18/18、full 221/221（38 suites）が成功した。
validation IDs は `SMWF-P1-REPOSYNC-RELATION-PROGRESSION-FOCUSED-20260928-119` と
`SMWF-P1-REPOSYNC-RELATION-PROGRESSION-FULL-20260928-120`。両方の exit 0 と
`lock=released`、実行 agent identity / unchanged tree を immutable receipt で確認した。

ここでの persistence は in-memory fixture を通した CNCF persistence contract の検証であり、
実ストアの再起動・回復や実 Git graph の独立走査の完成証明ではない。実 fast-forward、
merge、push、convergence / terminal、production factory 登録はまだ未実装である。
次は REMOTE_AHEAD の exact-target fast-forward receipt admission を最小の deterministic
slice として進める。P1-S01 と Phase 1 全体は引き続き未完了である。

fast-forward assessment slice では `RepositorySyncFastForwardAssessment` を追加した。
既存 boundary / scope / fetch / preservation / relation を再 admission し、保持済み証拠から
再導出した REMOTE_AHEAD と一致する場合だけ host の fast-forward evidence を読む。
EQUAL / LOCAL_AHEAD / DIVERGED、改変した enum / digest、上流証拠の不一致は host load 前に拒否する。

実行前の frozen state は relation digest、repository/worktree、tracking mapping、policy、
exact source tip / tree、保持済み fetched tip、およびその fetched commit の target tree に
結び付ける。worktree/index が clean、attached HEAD、pending operation が空であることを要求する。
receipt はその frozen digest、source tip、exact target tip / tree、selected local branch ref の
単一更新、fast-forward-only / completed と execution evidence を照合する。実行後の state は
receipt digest に結び付け、target tip / tree、tracking tip、mapping と clean gate を再照合する。
merge / rebase / cherry-pick / revert / unknown pending operation、追加 ref 更新、別 tip / tree、
不完全・欠落・null evidence、各 host load failure は成功にしない。

focused 31/31、full 231/231（39 suites）が成功した。validation IDs は
`SMWF-P1-REPOSYNC-FASTFORWARD-FOCUSED-20260928-121` と
`SMWF-P1-REPOSYNC-FASTFORWARD-FULL-20260928-122`。immutable receipt の実行 agent identity、
unchanged tree、exit 0、`lock=released` を確認した。SHA-1 / SHA-256 OID、保持済み証拠の
同一再読、pre-state / target-tree / execution / post-state evidence による digest の変化を検証した。

これは host 所有の receipt / pre/post state を受け入れる pure assessment であり、実 Git の
object/tree 独立走査、ref 更新、fetch、push は行わない。公開 fast-forward Operation / CML state /
WorkflowInstance revision 6 への progression は次の slice で接続する。現在の RepositorySync
progression は revision 5 のままであり、fast-forward 結果だけで convergence / terminal としない。
CNCF / Cozy への変更・コミットはなく、P1-S01 と Phase 1 全体は引き続き未完了である。

fast-forward progression slice では CML に
`RepositorySyncFastForwardAdmissionCommand/Result`、公開
`RepositorySyncService.admitFastForward` と `FastForwardAdmitted` state を追加し、
generated Workflow / Provided API revision を `repository-sync-fast-forward-v1` に更新した。
GoalPhase の固定 producer fixture の source line / SHA は、同じ CML の生成位置に追従させた。

`RepositorySyncFastForwardAdmissionExecution` は persisted revision 5 の history prefix と
boundary / scope / fetch / preservation / relation を照合してから fast-forward evidence を読む。
再 admission した REMOTE_AHEAD と exact-target receipt / post-state clean gate が一致する場合だけ、
同じ WorkflowInstance に `sync.FastForwardAdmitted` の revision 6 を append する。
digest と idempotency key を causation に結び付け、同じ証拠の再送は同じ非終端 WAIT を返す。
変更した key / 上流証拠 / fast-forward evidence、他の三 relation、不正な保存済み progression、
不足・失敗・不整合の pre/receipt/post evidence を成功にはしない。
append 前の失敗は revision 5 を残す。保存後に応答だけが失敗した場合は成功や rollback を
報告せず、保持済み revision 6 を同じ再送で照合し、履歴を重複させない。
公開 Operation と CNCF in-process CLI の strict JSON が同じ WAIT を返すことも確認した。

最初の validation `SMWF-P1-REPOSYNC-FASTFORWARD-PROGRESSION-FOCUSED-20260928-123` は
Cozy development classpath stale により生成前に失敗した。Cozy source は変更せず、
`SMWF-P1-COZY-CLASSPATH-REFRESH-20260928-124` の `cozyExportRuntimeClasspath` で再出力した。
続く focused `SMWF-P1-REPOSYNC-FASTFORWARD-PROGRESSION-FOCUSED-20260928-125` は22/22、
full `SMWF-P1-REPOSYNC-FASTFORWARD-PROGRESSION-FULL-20260928-126` は40 suite・242/242成功した。
各 invocation の terminal marker / `lock=released` と immutable receipt を確認した。
成功した二つの sm-workflow validation は同じ source tree
`3c3295f2c5f7723bb7718a33869ffd02998e274d1536b6cce791a0d604992ee8` を検証した。
full runner の報告 locator には省略があったため、その文字列は拒否し、元の verified intake の
receipt directory に存在する唯一の保存済み receipt を read-only で取得して digest / identity /
tree / terminal output を検証した。receipt の作成・補修や SBT の再実行はしていない。
この段落は検証後の記録追記であり、上記 tree digest に含めない。

これは保持済み host evidence と in-memory persistence fixture による executable specification
であり、実 Git fetch / ref 更新 / merge / push、実ストアの再起動・回復、production factory 登録を
実装したものではない。fast-forward admission は convergence / terminal ではなく、WAIT の次は
`Verify convergence` とする。次は EQUAL と fast-forward 後の convergence evidence assessment。
CNCF / Cozy source の変更・コミットはなく、P1-S01 と Phase 1 全体は引き続き未完了である。

convergence assessment slice では `RepositorySyncConvergenceAssessment` を追加した。
保持済み boundary / scope / fetch / preservation / relation を再 admission し、EQUAL は
fast-forward evidence なし、REMOTE_AHEAD は再 admission した exact-target fast-forward
assessment が完全に一致する場合だけ、確認用 fetch receipt を読む。他の relation、改変した
上流 digest / enum / fast-forward pre/receipt/post evidence、欠落・null input は host load 前に拒否する。

確認用 fetch receipt は predecessor digest、同じ repository/worktree / tracking mapping /
endpoint / policy、exact local tip/tree と clean pre-state に結び付ける。selected remote branch と
tracking ref 以外への更新、local tip/tree の変更、未完了・endpoint 不受理は成功にしない。
fetched tip は保存済み target tip と一致する必要があり、remote が新しく進んでいた場合は
成功や暗黙の reclassify / retry fetch にしない。後続の verification はその fetch digest に
結び付け、equal tips、ahead/behind 0/0、complete/non-replacement graph、同じ tree、clean
index/worktree、attached HEAD、および merge/rebase/cherry-pick/revert/unknown pending operation
がないことを要求する。共有の観測 state shape は既存 fast-forward assessment の `State` を使う。

新規12 spec で両経路、証拠の改変・欠落・load failure、保持済み証拠の同一再読、SHA-1 / SHA-256
OID、checkpoint を保持した EQUAL、および target とは異なる新 tip 同士が一致していても拒否する
ことを検証した。pre-state / verification-fetch execution / graph / post-state inspection の各 evidence
を result digest に結び付ける。初回 `SMWF-P1-REPOSYNC-CONVERGENCE-FOCUSED-20260928-127` は
新規 test fixture の関数型宣言の構文エラーで失敗し、宣言を修正した。
focused `SMWF-P1-REPOSYNC-CONVERGENCE-FOCUSED-RETEST-20260928-128` は33/33、
full `SMWF-P1-REPOSYNC-CONVERGENCE-FULL-20260928-129` は41 suite・254/254成功した。
両成功 invocation の immutable receipt は同じ source tree
`27bbd19dbe8f644ddf414d5d4d9f9077e41894f54bd12f7cffd2c3b4d078bca9`、agent identity、exit 0、
`lock=released` を検証した。この段落は検証後の記録追記であり、上記 tree digest に含めない。

これは host 所有の確認用 fetch / graph / state receipt を照合する pure assessment である。
実 Git fetch / object・graph 独立走査 / ref 更新 / push、公開 convergence Operation / CML state /
Workflow terminal、実ストアの再起動・回復、production factory 登録は未実装である。
既存 WorkflowInstance の progression はこの slice では変えず、次は EQUAL と fast-forward 後の
収束結果を公開 Operation と typed terminal に接続する。CML / CNCF / Cozy の変更・コミットはなく、
P1-S01 と Phase 1 全体は引き続き未完了である。

convergence terminal slice では、CML に `RepositorySyncConvergenceCommand/Result`、
公開 `RepositorySyncService.admitConvergence`、`Converged` state を追加した。
generated Workflow / Provided API revision は `repository-sync-convergence-v1` とし、
既存五 stage と一 terminal を CNCF entry binding に結び付ける。新しい DTO 挿入による
GoalPhase の source line 移動は、既存 ABI JSON fixture と source/fixture digest に反映した。

`RepositorySyncConvergenceExecution` は、元の Start と上流 evidence から再構成した
history prefix を、保存済み同一 WorkflowInstance と照合してから convergence evidence を読む。
EQUAL は relation admission の revision 5、REMOTE_AHEAD は exact-target fast-forward の
revision 6 とその idempotency key が一致する場合だけ受理する。LOCAL_AHEAD / DIVERGED と
route/key/revision の不一致は downstream evidence を読む前に拒否する。
収束 receipt を admission し、typed result と Presentation を検証した後でだけ、revision 6 / 7 の
Completed entry を append し、CNCF common `projectTerminalC` から typed Terminal を返す。
Completed の active progression / suspension は空とし、既存 history / correlation を保持する。
変更のない EQUAL は `ALREADY_SYNCHRONIZED`、checkpoint 後の EQUAL と fast-forward 後は
`SYNCHRONIZED` とする。caller は outcome / tip / tree / evidence digest を指定できない。

同じ key/evidence の再送は同じ Terminal を返し、変更した key/evidence は append しない。
append 前の拒否は Active predecessor を残す。保存後に応答だけ失敗した場合は Completed を
rollback したとは報告せず、同じ再送で保存済み completion を照合する。
新規10 spec で両経路と checkpoint、履歴改変、unsupported route、不正 command、欠落・失敗・
advanced remote / dirty verification、changed replay、保存前後の失敗、strict typed payload codec、
公開 Operation / CNCF in-process CLI の同一 Terminal 応答を確認した。

初回 `SMWF-P1-REPOSYNC-CONVERGENCE-TERMINAL-FOCUSED-20260928-130` は Cozy development
classpath stale で生成前に失敗した。`SMWF-P1-COZY-CLASSPATH-REFRESH-20260928-131` の
`cozyExportRuntimeClasspath` 後、132 は新規 CML service の OUTPUT/TYPE section 不備で、
133 は optional attribute の `0..1` が String として生成されたためコンパイルで失敗した。
sm-workflow CML を既存の OUTPUT/TYPE section と optional `?` 表記に修正し、Cozy source は
この作業では編集していない。focused
`SMWF-P1-REPOSYNC-CONVERGENCE-TERMINAL-OPTION-RETEST-20260928-134` は4 suite・25/25、
full `SMWF-P1-REPOSYNC-CONVERGENCE-TERMINAL-FULL-20260928-135` は42 suite・264/264成功した。
両成功 receipt の source tree は
`c9cc22fa31d548791ee0426b580ee8da0cd913fd9d621c5e912b9ecb4e3098a8` で一致し、agent identity、
exit 0、matching terminal marker / `lock=released` を確認した。
一部 runner の locator 報告や134の終了報告は受入証拠に使わず、元の verified intake 保存先に
揃った実 receipt と stdout/stderr を read-only で照合した。再実行・receipt の作成や補修はしていない。
この段落は検証後の記録追記であり、上記 source tree digest に含めない。

これは retained host evidence / in-memory persistence の executable specification である。
実 Git fetch / ref 更新 / push / merge、実ストアの再起動・回復、production factory 登録は
未実装で、LOCAL_AHEAD / DIVERGED は完了経路ではない。次は LOCAL_AHEAD の push 前判定と
exact-target receipt admission を deterministic fixture で進める。今回 CNCF / Cozy の機能追加、
コミット・push は行わず、P1-S01 と Phase 1 全体は引き続き未完了である。

LOCAL_AHEAD push assessment slice では `RepositorySyncPushAssessment` を追加した。
保持済み boundary / scope / fetch / preservation / relation を再 admission し、完全に一致する
LOCAL_AHEAD だけを受理する。他の relation、改変された上流 digest / enum / graph facts、
欠落・null input は push evidence load 前に拒否する。

push 前の state は同じ repository/worktree / local・tracking branch mapping / endpoint / policy、
保存済み local tip/tree と fetched tip、clean index/worktree、attached HEAD、pending operation
なしを要求する。独立に観測された advertised remote tip は元の fetched tip と一致し、
complete/non-replacement graph で remote が local の ancestor である必要がある。
remote の進行は拒否し、暗黙の fetch / reclassify / retry はしない。host の authority evidence は
同じ relation、mapping、source/destination tips と non-force / remote-write の両受理に結び付ける。
これは保持済み host attestation の照合であり、Git provider が受理する権限 token ではない。

receipt は frozen-state digest、exact local/remote refs、元の destination tip と保存済み local tip
への更新に結び付ける。selected remote branch 一つ以外への更新、force、tags、mirror、endpoint
不受理、未完了を拒否する。ローカル tracking ref は元の tip のまま変更なし、または selected
tracking ref だけを local tip に更新した場合のみ受理し、local branch や他の ref の更新は拒否する。
後続 state は receipt digest と同じ mapping/local tip/tree、報告された tracking tip、clean gate に
一致させる。authority / graph / pre-state / execution / post-state evidence を result digest に束ねる。
push receipt の受理だけでは convergence / terminal としない。

新規13 spec で clean / checkpoint 後の LOCAL_AHEAD、unsupported route、上流・前後 state・
authority・receipt の不一致、missing/null/load failure、保持済み同一証拠の再読、SHA-1 / SHA-256
OID を検証した。focused `SMWF-P1-REPOSYNC-PUSH-FOCUSED-20260928-136` は3 suite・36/36、
full `SMWF-P1-REPOSYNC-PUSH-FULL-20260928-137` は43 suite・277/277成功した。
両 immutable receipt の source tree は
`6b8b74bc99964ebe294a5c1c80f99e4604cf20b2044c9bbf7e1f8656d52bc4fc` で一致し、agent identity、
exit 0、matching terminal marker / `lock=released` を検証した。この段落は検証後の記録追記であり、
上記 source tree digest に含めない。

今回は pure assessment / deterministic fixture だけを追加し、CML / 公開 push Operation /
WorkflowInstance progression は変更していない。実 Git push、reconciliation retry budget、
実ストアの再起動・回復、production factory 登録は未実装である。次はこの retained push admission
を公開 Operation と保存済み Workflow history に接続し、その後 LOCAL_AHEAD の確認用 fetch /
convergence に接続する。CNCF / Cozy source の変更・コミット・push は行わず、P1-S01 と
Phase 1 全体は引き続き未完了である。

push admission connection slice では CML に `RepositorySyncPushAdmissionCommand/Result`、
公開 `RepositorySyncService.admitPush`、`PushAdmitted` state を追加した。
generated Workflow / Provided API revision は `repository-sync-push-admission-v1` とし、
八 state / 八 provided operation を entry binding に結び付けた。CML DTO 挿入による GoalPhase
source line の移動は既存 ABI JSON fixture と source/fixture hash に反映した。

`RepositorySyncPushAdmissionExecution` は Start と上流 evidence から再構成した history prefix
を保存済み同一 instance と照合し、relation admission の revision 5 から retained LOCAL_AHEAD
push evidence を受理した場合だけ revision 6 の Active `sync.PushAdmitted` を append する。
同じ key/evidence は同じ WAIT を再投影し、異なる key/evidence は再 append しない。
別経路の revision-six stage は push evidence load 前に拒否する。次 action は確認用 fetch /
convergence とし、この Operation は native push や Completed terminal を実行・生成しない。
公開 fixture Operation は closed schema の command JSON と CNCF common WAIT JSON を使う。
新規14 spec に checkpoint 後の経路、unsupported relation、履歴・証拠・command 不一致、
missing/load failure、保存前後の失敗、exact replay、公開 Operation / in-process CLI を追加した。

検証 attempt `SMWF-P1-REPOSYNC-PUSH-ADMISSION-FOCUSED-20260928-138` は未実行である。
登録 runner `/root/sbt_receipt_8074bbec167faf1fb269` は最初の preflight と一回の同一-runner
recovery 後の実行で、opaque intake parent の末尾 `...116c2e0e-...` を `...116c2e0-...` に
欠落させた。元の intake は存在し `.execution-started` がないこと、recovery renderer の返した
command は正しいが runner の実 tool command は欠落していることを read-only で確認した。
これは locator 転送の実行インフラ失敗であり、SBT は未開始、validation receipt / terminal marker
はなく、focused/full 成功証拠には使わない。許された同一-runner recovery を使い切ったため、
別 runner / 新 envelope / parent-direct SBT では再実行していない。

138停止時点では、この接続差分は保存済みだが未検証であり、CML 生成・重点テスト・full テストが必要だった。
LOCAL_AHEAD の convergence 接続、実 Git push、production factory / 実ストア再起動・回復は
未実装。CNCF / Cozy source 編集・コミット・push は行わず、P1-S01 / Phase 1 は未完了のままとする。

続行指示により登録 `cncf_command_runner` の原本定義を修正した。receipt-first / recovery の
renderer が返す `execution_request` は、同じ `functions.exec` 内で JSON parse し、返された
object を直接 `tools.exec_command` に渡す。opaque locator の手入力・再転記を禁止した。
原本は `codex-customizations/agents/cncf_command_runner.toml`、配備は既存 `sync-agents.py` を
使い、model / reasoning effort / authority / escalation / shared lock / recovery budget は変えていない。
原本・配備先の SHA-256 は
`6f5ef7b71195b04d20ff8ff7890873fc136d417f7aaffcaa7fdd348f6aa4a543` で一致し、実行後の
`sync-agents.py --check-only` は21 regular-file agent 全件で成功した。

修正後の新しい登録担当による
focused `SMWF-P1-REPOSYNC-PUSH-ADMISSION-RUNNER-REPAIR-FOCUSED-20260928-139` は
`cozyGenerate` と6 suite・52/52、
full `SMWF-P1-REPOSYNC-PUSH-ADMISSION-FULL-20260928-140` は44 suite・291/291成功した。
両 immutable receipt の source tree は
`a176281773426665d9ada5c1a214096119cc16152c30ffb51949d595abac2362` で一致し、exit 0 と
matching terminal marker / `lock=released` を検証した。実行担当は順に
`/root/sbt_receipt_1fef3359220fa45d326e`、`/root/sbt_receipt_d12fa81b5944daf05198`、両方とも
`gpt-5.6-luna / medium`、route `registered-custom-role`、logical role / agent type
`cncf_command_runner` である。receipt digest は順に
`3fc218914f1a86d0f7c4a3813210b7d9b845e84f6e5f8e26b6f106633271b73f`、
`cf098c32c88be19caa3e0c245b5ff7d26854ff0f315fe6c29dba4ab5d9c0b1ea`。
これは runner 修正後の behavioral validation であり、138の intake / ticket / runner は再利用していない。
この段落は検証後の記録追記であり、上記 source tree digest に含めない。

push admission の公開 Operation / generated ABI / persisted history 接続の検証は完了した。
次は LOCAL_AHEAD の確認用 fetch / convergence を retained evidence で接続する。
実 Git push、production factory / 実ストア再起動・回復はまだ未実装であり、今回も CNCF / Cozy
source 編集・コミット・push は行っていない。P1-S01 / Phase 1 全体は引き続き未完了である。

LOCAL_AHEAD push convergence slice では、保持済み push assessment 全体を再 admission し、
その digest と exact post-state を predecessor にして確認用 fetch / 0/0 graph evidence を照合する
`Push` route を追加した。push 後の selected tracking ref は、元の fetched tip のままでも、
push 時に local tip へ更新済みでもよい。確認用 fetch の before はその exact tracking tip に
一致させ、after は保持済み local tip に一致させる。tracking tip が変わる場合は selected
tracking ref 一つの更新報告が必須であり、変更なしとの矛盾、他 ref の更新、remote advance、
local tip/tree の変更、dirty / pending operation、非0/0・不完全・replacement graph は拒否する。

`RepositorySyncConvergenceCommand` に optional `pushIdempotencyKey` を追加し、
公開 command schema を `sm-workflow.repository-sync-convergence.v2`、generated Workflow / API
revision を `repository-sync-push-convergence-v1` に更新した。fast-forward と push の key は
排他的で、EQUAL は両方なし・revision 5、fast-forward / push は対応する key 一つ・revision 6
だけを受理する。旧 wire schema、欠落 key field、型不一致、caller outcome / extra field を拒否する。
DTO 行挿入による GoalPhase の source line +1 は既存 JSON fixture と source/fixture hash に反映した。

execution は保存済み revision 6 の Active `sync.PushAdmitted` と exact push key / digest を確認し、
確認用 fetch / equal-tip evidence が一致した場合だけ revision 7 の Completed / `SYNCHRONIZED`
を append する。異なる stage は push evidence load 前、異なる key / push evidence は convergence
load 前に拒否する。同じ key / evidence の再実行は同じ typed terminal を返し、履歴を増やさない。
保存前の失敗は Active のまま、保存後の応答不確定は同じ再実行で保存済み completion を照合する。
これらは retained evidence / in-memory persistence の executable specification であり、native Git
操作や remote-write permission token を実行・発行するものではない。

新規9 spec と既存 transport spec の LOCAL_AHEAD 拡張で、追跡 ref の両 post-push 形態、
checkpoint tree 保持、SHA-1 / SHA-256 OID、上流・push evidence の再 admission、unsupported /
multiple predecessor、tracking 更新の報告矛盾、remote/local drift、missing/load failure、
exact history / replay / append failure、closed v2 command と direct / CLI の同一 terminal を検証した。
focused `SMWF-P1-REPOSYNC-PUSH-CONVERGENCE-FOCUSED-20260928-141` は
`cozyGenerate` と7 suite・73/73、
full `SMWF-P1-REPOSYNC-PUSH-CONVERGENCE-FULL-20260928-142` は44 suite・300/300成功した。
両 immutable receipt の source tree は
`26d487b6a0bc58832fe65edcd9bd15a07ba8d87f2f8f309d396d674770fa8f5c` で一致し、exit 0、
agent identity、matching terminal marker / `lock=released` を検証した。
実行担当は順に `/root/sbt_receipt_f664c8648bc87dbf0275`、
`/root/sbt_receipt_47508c1c2d093cc2fff1`、両方とも `gpt-5.6-luna / medium`、
route `registered-custom-role`、logical role / agent type `cncf_command_runner`。
receipt digest は順に
`9d621ec8181de71aa61ec044ded1bc5ecbe7ef49ba2fc7465886a0f92fa5a424`、
`8dbb1c6e22a3de91b608beda25f7ee3cb109043ba7c5cfb92039940227d45316`。
この段落は検証後の記録追記であり、上記 source tree digest に含めない。

次は DIVERGED の exact two-parent merge boundary と conflict-free integration evidence を
deterministic fixture で進める。今回も実 Git fetch / merge / push、production factory、実ストア
再起動・回復、remote retry budget は未実装である。これらの operational hardening は Phase 1 の
closure 必須条件には追加しない。CNCF / Cozy source の変更・コミット・push は行わず、
P1-S01 / Phase 1 全体は引き続き未完了である。

DIVERGED pending-merge assessment slice では、`RepositorySyncMergeAssessment` を追加した。
保持済み boundary / scope / fetch / checkpoint / relation 全体を再 admission し、exact DIVERGED
だけを受理する。clean pre-state、ordered local / fetched の二親、single merge base、remote tree を
固定し、receipt は `--no-commit --no-ff`、HEAD 不変、MERGE_HEAD が selected fetched tip 一つ、
他 ref 更新なし、canonical changed paths を必須にした。pending state の HEAD tree は不変の
pre-state、result tree は complete staged merge result として区別する。pending operation は
MERGE 一つだけで、exact mapping / tree / tracking / staged paths と unresolved entry なしを照合する。

structural evidence は同じ pending digest / 二親 / result tree に binding し、unmerged entry、
conflict marker、unsafe finding、未完了 scan を拒否する。親ごとの whitespace diagnostic は
canonical path / positive line / exact parent OID を持つ閉じた evidence とし、同じ path / line が
両親に対する診断に現れる場合だけ blocker にする。片側から継承された診断は保持し、結果を書き換えない。
各境界の inspection / execution digest と診断を最終 digest に含め、missing / null / load failure、
不一致 evidence は後段 load 前に停止する。これは deterministic retained fixture の判定であり、
Git 実行、product validation、semantic review、merge commit、terminal completion は行わない。

新規14 spec で clean / checkpoint、SHA-1 / SHA-256 OID、上流の再 admission、親順序・base、
auto-commit / fast-forward / HEAD 移動、unsafe path、pending-state drift、scan 不一致、
片側 / 両側 whitespace、missing / null / failure、digest binding と同一 evidence replay を検証した。
focused `SMWF-P1-REPOSYNC-MERGE-ASSESSMENT-FOCUSED-20260928-143` は3 suite・35/35、
full `SMWF-P1-REPOSYNC-MERGE-ASSESSMENT-FULL-20260928-144` は45 suite・314/314成功した。
両 immutable receipt の source tree は
`08b825e400a4bc68f92f8289d22f96c6c613214b92730f61a3120f26ba88dc52` で一致し、exit 0、
agent identity、matching terminal marker / `lock=released` を検証した。
実行担当は順に `/root/sbt_receipt_3593c23fd3fa17abadb8`、
`/root/sbt_receipt_51b1511b45bb2bf83ba3`、両方とも `gpt-5.6-luna / medium`、
route `registered-custom-role`、logical role / agent type `cncf_command_runner`。
receipt digest は順に
`b2b4a021dbe30a320f7211c86b278a80badaed230745b5171aacb8fad8edb30c`、
`de59e823f89d5636dda477e40a6d4ae25dfa7ea53a0609ed0594d4786694214b`。
144 は locator 誤写による raw `COMMAND_EXECUTION_REQUEST_INVALID` の起動前停止後、
親が original intake の未消費を確認し、許可された一回の same-runner recovery で実行した。
intake / attempt / route / agent は変更せず、二つ目の SBT invocation は作成していない。
この段落は検証後の記録追記であり、上記 source tree digest に含めない。

次はこの retained merge assessment を公開 Operation / persisted history に接続する。
product validation policy / semantic review / two-parent commit はその後の未実装境界として残す。
今回も実 Git fetch / merge / push、CNCF / Cozy source の変更・コミットは行っていない。
production factory / 実ストア再起動・回復などの operational hardening は Phase 1 の closure
必須条件には追加しない。P1-S01 / Phase 1 全体は引き続き未完了である。

DIVERGED merge admission slice では、`RepositorySyncMergeAdmissionExecution` と
`RepositorySyncMergeAdmissionServiceOperation` を追加し、保持済み merge assessment を
公開 `RepositorySyncService.admitMerge` / strict JSON / CNCF CLI に接続した。CML に typed
`RepositorySyncMergeAdmissionCommand` / Result、`MergeAdmitted`、relation からの明示 transition
を追加し、Workflow / generated API revision を `repository-sync-merge-admission-v1` に更新した。
generated ABI は9 state / 9 provided Operation を照合し、新しい stage を definition binding に含める。
DTO 挿入による GoalPhase の source line +18 は既存 ABI fixture と source / fixture hash に反映した。

execution は保存済み revision 5 の Active `sync.RelationClassified` と全 upstream history / evidence を
再照合し、exact DIVERGED の uncommitted conflict-free merge assessment が通った場合だけ
revision 6 の Active `sync.MergeAdmitted` を一回 append する。結果は検証・レビュー待ちの WAIT
であり、product validation / review / commit / SYNCHRONIZED terminal を示すものではない。
同じ key / evidence は同じ WAIT を返して履歴を増やさず、異なる key / evidence / prior history /
revision-six stage は拒否する。保存前の失敗は revision 5 のまま、保存後の応答不確定は同じ再実行で
保存済み entry を照合する。未コミット merge の実行や remote-write authority の発行は行わない。

新規16 spec で persisted relation 必須、三つの非DIVERGED route、checkpoint history 保持、
missing / null / failed / inconsistent evidence、command / upstream drift、key / digest replay、
append 前後の失敗、ordered parents / conflict marker / auto-commit 拒否、片側 whitespace の履歴 binding と
両親診断 blocker、異なる stage / public binding / closed wire shape、direct Operation / CLI の同一 WAIT を
検証した。145 は新規 fixture の selection revision 誤りで16件が Start admission 前に拒否された
（既存59件成功）。その一箇所を修正し、同じ logical argv を新しい146で再検証した。
145 の runner は結果回収前に終了したが、親が元の immutable receipt、native command completion、
matching terminal / `lock=released` を検証してから146へ進んだ。二重実行や同じ intake の再利用はしていない。

focused `SMWF-P1-REPOSYNC-MERGE-ADMISSION-FOCUSED-20260928-146` は `cozyGenerate` と
7 suite・75/75、full `SMWF-P1-REPOSYNC-MERGE-ADMISSION-FULL-20260928-147` は46 suite・330/330成功した。
両成功 receipt の source tree は
`5d54096f8081c6b9b10e6c46b6f16ad61d35f76163f4017c06f82f75b055c825` で一致し、exit 0、
exact agent identity、matching terminal marker / `lock=released` を検証した。
145 / 146 / 147 の実行担当は順に `/root/sbt_receipt_73114957d493c285efa7`、
`/root/sbt_receipt_91e8d2b591f7a0d235fa`、`/root/sbt_receipt_04199cd55ace45d0a4f7`。
すべて `gpt-5.6-luna / medium`、route `registered-custom-role`、logical role / agent type `cncf_command_runner`。
receipt digest は順に
`b3f0976e938e365adb2bbd3e951f758113979e3116ca12f459a1fb9152b812a2`（145・SBT exit 1）、
`05b2298a47e8c1162f19e14252c9e9df519f4379d824b82fe921e9daa8b43e3c`、
`1bccbc3806fa4bf40766f94f44d0d7b4a1713c2c8469b23c71354268b9211a4b`。
この段落は検証後の記録追記であり、上記 source tree digest に含めない。

次は merge 差分に合った product validation policy と semantic review evidence の判定を
deterministic fixture で進める。two-parent commit / push / convergence はその後の未実装境界である。
今回も実 Git fetch / merge / push、CNCF / Cozy source の変更・コミットは行っていない。
production factory / 実ストア再起動・回復などの operational hardening は Phase 1 の closure
必須条件には追加しない。P1-S01 / Phase 1 全体は引き続き未完了である。

merge validation / review assessment slice では、`RepositorySyncMergeValidationAssessment` と
`RepositorySyncMergeReviewAdmission` を追加した。保持済み upstream chain / pending merge を再照合し、
ordered parents / result tree / 完全な changed-path classification / repository policy を digest に binding する。
Documentation / Metadata だけなら product check は不要、LocalSource / LocalBuild / LocalSpec は
repository が明示した coverage / rank から対象全体を覆う最優先 check、Runtime / PublicContract を含む
差分は明示された broad checks 全件を要求する。extension からの推測や raw argv の受け入れは行わない。
保持済み validation receipt は exact plan / selected checks、successful / completed、同一 pending HEAD /
staged tree / parents / scope を満たす必要がある。check の実行や再実行はこの assessment の責務に含めない。

既存 CNCF CAM を使い、validation digest / review policy に binding した candidate に full-merge review を
要求する。caller-supplied time に対して fresh な初期・後着 evidence は同じ結果に再利用し、focused /
wrong target / wrong type / stale evidence は admission gap として保持する。Repair / DecisionNeeded /
Reject は review evidence が受理されても acceptance ではない。重複する review、malformed finding /
provenance は拒否し、worker が transition / test / commit authority を選ぶ構造は追加していない。
この slice は純粋な判定境界であり、semantic WorkOrder の発行、review completion Operation、repair の
実行、persisted validation / review history はまだ接続していない。

新規17 spec で dirty / clean checkpoint、40 / 64-character OID、三種の検証範囲、policy coverage / rank、
missing / null / failed evidence、pending merge drift、selection / digest weakening、CAM freshness / scope /
disposition、validation / review policy 変更による旧レビュー無効化を検証した。
focused `SMWF-P1-REPOSYNC-MERGE-VALIDATION-FOCUSED-20260928-148` は4 suite・53/53、
full `SMWF-P1-REPOSYNC-MERGE-VALIDATION-FULL-20260928-149` は47 suite・347/347成功した。
両 receipt の source tree は
`20a507849d6520dbac3e4955d42b10ae703f91a5c1b71315074d3b31d5e2ccda` で一致し、exit 0、
exact agent identity、matching terminal marker / `lock=released` を検証した。
148 の runner 返答は結果なしだったが、親が元の immutable receipt と native command の completed /
exit 0 を確認してから149へ進んだ。148 の二重実行や intake の再利用は行っていない。
148 / 149 の実行担当は `/root/sbt_receipt_60994bd7711c5dfa87f1` と
`/root/sbt_receipt_2a9b21493837b227198b`。両方 `gpt-5.6-luna / medium`、
runtime source / route は `registered-custom-role`、logical role / agent type は `cncf_command_runner`。
receipt digest は順に
`6a57eb03a372e50513f2b6920db81f6e688a008397f0ee858a193ab586728ac0` と
`866fddf1118f9456b503ac617ac7c678be3f126660ed310d6f4ab8c3a935ce6e`。
この段落は検証後の記録追記であり、上記 source tree digest に含めない。

次はこの retained validation / CAM review 判定を公開 Workflow Operation / persisted history に接続し、
不足する semantic evidence を共通 Continuation へ渡す。two-parent commit / push / convergence は
その後の未実装境界である。今回も実 Git fetch / merge / push、CNCF / Cozy source の変更・コミットは
行っていない。operational hardening を closure 必須条件に追加せず、P1-S01 / Phase 1 は引き続き未完了。

merge-validation public admission slice では、CML に
`RepositorySyncMergeValidationAdmissionCommand` / Result、`MergeValidated` と公開 Operation
`RepositorySyncService.admitMergeValidation` を追加した。Workflow / generated API revision は
`repository-sync-merge-validation-v1`、generated ABI は10 state / 10 provided Operation。
GoalPhase の source line +18 を既存二つの ABI fixture と source / fixture hash に反映した。

execution は保存済み revision 6 の Active `sync.MergeAdmitted` と Start / boundary / fetch /
preservation / relation / pending merge の全履歴・証跡を再照合してから、保持済み product validation
receipt を判定する。成功した場合だけ revision 7 の Active `sync.MergeValidated` を一回 append し、
validation digest に binding したレビュー待ち WAIT を返す。同じ key / evidence の再実行は履歴を
増やさず、upstream drift / validation plan・policy・execution digest の変更や別 stage を拒否する。
保存前の失敗は revision 6 のまま、保存後の応答不確定は保存済み entry の exact replay で回復する。
この Operation は Git merge、product check の実行、レビューの発行・受理、commit / push /
SYNCHRONIZED terminal を行わず、WAIT の nextAction を外部 WorkOrder と見なしていない。

新規16 spec で persisted merge 必須、checkpoint history 保持、upstream / validation drift、
missing / null / failed evidence、三種の検証範囲、key / digest replay、append 前後の失敗、
malformed command / public binding / strict wire shape、direct Operation / CLI の同一 WAIT を検証した。
150 は親の一括更新が途中省略された command output を patch に使ったため、既存 preservation
spec を壊して Test/compile に失敗した。親が native command event の省略されていない更新前の
内容から322行・既存7 spec を復元した。既存 Scala spec は意図した revision 更新以外に欠落が
ないこと、ABI fixture は source line 更新だけ、consumer spec は三つの hash 更新だけであることを
更新前の内容と照合した。テストの削除・期待値の緩和はしていない。

focused `SMWF-P1-REPOSYNC-MERGE-VALIDATION-ADMISSION-FOCUSED-20260928-151` は
`cozyGenerate` と6 suite・55/55、full
`SMWF-P1-REPOSYNC-MERGE-VALIDATION-ADMISSION-FULL-20260928-152` は48 suite・363/363成功した。
両成功 receipt の source tree は
`c871ca17f9875e556401a244b4c4892d6c4c7a1047021fe4300420c4564b8711` で一致し、exit 0、
exact agent identity、matching terminal marker / `lock=released` を検証した。
150 / 151 / 152 の実行担当は順に `/root/sbt_receipt_0b85f2888ef22332f088`、
`/root/sbt_receipt_2cc3924690beb510c804`、`/root/sbt_receipt_16601418b9dec0afb018`。
すべて `gpt-5.6-luna / medium`、runtime source / route は `registered-custom-role`、
logical role / agent type は `cncf_command_runner`。receipt digest は順に
`9eaf4b7bc628ef1f39a8d82877d7edf49d104b7e275b8865fd5b1062ca92c2f5`（150・SBT exit 1）、
`eaa8262a6cbed4dce4324929a6ad9edc03a5fa3df72c2aa344f2b654caaae8f3`、
`8fb3bd228a20bbe580eab25c820fa8cbb9730299352725480e9169d831f2e527`。
この段落は検証後の記録追記であり、上記 source tree digest に含めない。

次は CAM full-merge review 要求 / 後着 evidence を公開 Workflow の共通 Continuation と保存済み
revision 7 に接続する。現行の actionless entry binding へ required JudgmentAction を手動で捏造せず、
既存の CML / candidate Workflow binding に沿って進める。two-parent commit / push / convergence は
その後の未実装境界である。今回も実 Git 操作、CNCF / Cozy source の変更・コミットは行っていない。
operational hardening を closure 必須条件に追加せず、P1-S01 / Phase 1 は引き続き未完了。

merge-review Continuation boundary slice では、CML に typed review Request / Finding / Result と
`ReviewMerge` JudgmentAction、`AdmitMergeReview` AdmissionAction、Required SPI
`MergeReviewService.reviewMerge`、公開 Operation declarations
`RepositorySyncService.requestMergeReview` / `submitMergeReview` を追加した。
開始時の `sync.subject: RepositorySyncStartInput` とレビュー対象を混同せず、
`review.subject: RepositorySyncMergeReviewRequest` を専用の Retained context constituent として
明示した。Workflow revision は `repository-sync-merge-review-v1`、
generated ABI は10 state / 2 Action / 1 Required SPI / 12 provided Operation。
この slice は公開 Operation の handler / registration の実装完了を意味しない。

binding は実際に生成された Workflow / Provided API / Candidate-Admission producer descriptors を
構造的に JSON へ写し、CNCF の公開 Candidate-Admission / CandidateWorkflow admission と
`bindCandidateDefinitionC` を通す。actionless entry metadata へ架空の Action を加えていない。
producer の深い JSON encoder は型ごとに導出し、compiler の inline 上限を変更していない。
opaque DefinitionBinding の private fields や object identity に依存せず、admitted definition の
全公開 facts と digest を照合するため、同じ定義を読み直した record も扱える。
GoalPhase の既存 ABI fixture は source line +56 と source / fixture hash のみ更新した。

boundary は revision 7 の Active `sync.MergeValidated` と Start / boundary / fetch /
preservation / relation / merge / validation の全 retained history、command keys、validation digest、
candidate revision、review policy、生成済み Required SPI、Provided request の型・context を
再照合する。不足した full-merge review は共通 Continuation に routing し、revision 8 の
suspension を projection するだけで暗黙に保存・発行しない。host が exact suspension を保存してから
CNCF の ordered publication を行い、claim 後に共通 WorkOrder を発行する。
issuer 自身も WorkflowInstance store を読み、実際に保存された exact suspension がなければ拒否する。
WorkOrder は pending head / tree / digest / ordered parents / changed paths / selected checks、
validation digest、review policy、candidate revision を含み、completion は
`RepositorySyncService.submitMergeReview` / typed `RepositorySyncMergeReviewResult`。
wire に内部 claim token を公開しない。

新規14 spec で generated candidate binding、clean / dirty pending merge、upstream / command /
validation / policy / declaration drift、free-text summary と空欄拒否、projection と保存の分離、
save-before-publication、保存失敗時の claim 拒否、保存を省略した claimed Continuation の拒否、
rehydrated definition、issued-store 失敗からの exact recovery / replay、strict WorkOrder JSON、
fresh full review の再利用、stale / focused gaps、repair / decision-needed / reject と acceptance の
分離を検証した。fresh accept evidence を持っていても state advance / commit authority を与えない。
手動レビューでは durable store の再照合と opaque definition の全 facts 比較を追加した。
この実装は既存の緩い ordered persistence を使い、共有トランザクション機構に拡張していない。

153 は Cozy development runtime classpath が stale で生成前に停止した。154 は既存
`cozyExportRuntimeClasspath` だけを実行し、Cozy source を編集せず build output を更新した。
155 の CML INPUT 構文、156 の private field access、157 の encoder derivation、
158 の malformed test 型推論、159 の identifier 検査を free-text summary に使ったバグは
それぞれ親が修正した。既存テストの削除や assertion の緩和はしていない。
159 の runner 返答は観測不可だったが、親が original immutable receipt と同じ child の native
execution event の completed / receipt helper exit 0 を照合し、SBT exit 1 と terminal /
`lock=released` を確認した。二重実行はしなかった。
160 の初回 preflight は runner の locator 読み違いで実行前に拒否された。元の intake が未消費で
execution-start marker がないことを recovery builder でも検証し、同じ canonical child / intake /
command / authority を一回の typed recovery ticket で再開した。attempt の再作成や fallback は
していない。command は成功したが、159 の返答欠落 / 160 の transport recovery は runner 側の
nonblocking infrastructure degradation としてこの記録に残す。

focused `SMWF-P1-REPOSYNC-MERGE-REVIEW-BOUNDARY-FOCUSED-20260928-160` は
`cozyGenerate` と6 suite・69/69、full
`SMWF-P1-REPOSYNC-MERGE-REVIEW-BOUNDARY-FULL-20260928-161` は48 suite・377/377成功した。
両成功 receipt の source tree は
`786b7a2770db98c3d826fe4b77ff34dda0c6bdcd580a9707b71dee08238eeac9` で一致し、exit 0、
exact agent identity、matching terminal marker / `lock=released` を検証した。
153–161 はすべて `gpt-5.6-luna / medium`、
runtime source / route は `registered-custom-role`、
logical role / agent type は `cncf_command_runner`。
以下の153 / 155–160 は invocation
`SMWF-P1-REPOSYNC-MERGE-REVIEW-BOUNDARY-FOCUSED-20260928-<番号>`、
154 は `SMWF-P1-COZY-RUNTIME-CLASSPATH-REFRESH-20260928-154`、161 は上記 full invocation。

| 番号 | 結果 | 実行担当 | immutable receipt SHA-256 |
| --- | --- | --- | --- |
| 153 | 失敗: stale Cozy classpath | `/root/sbt_receipt_a545aa69c5139b1b28e6` | `5f5d56c0b528e297a786ab778aee33cbab0472a836a8ae457884ebca6170a3ee` |
| 154 | 成功: classpath export | `/root/sbt_receipt_395407888197fde58ed5` | `eb7a67d1a2eba4753e2c8b3da99abb175ee58d0b182d596c9f3a3ef479c4b4af` |
| 155 | 失敗: CML INPUT | `/root/sbt_receipt_47097ce333da0955a5f5` | `ae3264732758fd4789d213640791b84aaae9c7b47994e2f9c3d93a41b6a77a52` |
| 156 | 失敗: private ABI field access | `/root/sbt_receipt_c8b29ee62635759ad40b` | `806d0642e15959c0fba8411528bc90e7cc7279b53c86fcc1bfbca42892282948` |
| 157 | 失敗: deep encoder derivation | `/root/sbt_receipt_b20fa1a15929582408ee` | `6b61156d65025d72c5e1ee87bde971b3105f27f5ec19b1a8bf876a81fc668216` |
| 158 | 失敗: test generic inference | `/root/sbt_receipt_a4fac6b2330d4942a915` | `aabba68c444f9cd20a822e12de6d1fe43f10c654d2b1e7d6f0b685bca51c6a11` |
| 159 | 失敗: 60/69 (summary guard) | `/root/sbt_receipt_b38a8ac4c042718221e8` | `bd1bf142ce76593fe36bf6c7de75a923c3ac73b061a2e80813167e0367d1467a` |
| 160 | 成功: 69/69 | `/root/sbt_receipt_5fd1dab08b3b63325463` | `53fba57e95006d6dfa120720fd6f46e1a4afa4f9b92dae03828cd268f0f28c6e` |
| 161 | 成功: 377/377 | `/root/sbt_receipt_948216f4efef9aa7fac5` | `8f4456d04125f888bec7fa708e7dc6c6116468da41b5067910983fd595bf3fb2` |

この記録追記は検証後の documentation-only 更新であり、上記 source tree digest に含めない。
次は公開 review request handler と後着 typed review evidence の受理・guarded resume を接続する。
two-parent commit / push / convergence はその後の未実装境界である。
実 Git 操作、CNCF / Cozy source の編集、stage / commit / release / phase closure は行っていない。
Cozy に元からある未コミット作業を保持した。P1-S01 / Phase 1 は引き続き未完了。

public merge-review slice では `RepositorySyncMergeReviewServiceOperation` に
`requestMergeReview` / `submitMergeReview` の明示登録用 binding を実装し、実際の生成済み
Provided Operation の型と revision を照合した。deterministic/test host が retained submission /
validation command / run identity、WorkflowInstance store、common Continuation runtime、
issued WorkOrder store を注入する。これは public Operation / CLI fixture の完成であり、
production launcher への host adapter 自動登録や実 Git repository 実行の完成ではない。

request は11項目の typed payload を host-retained validation から再構成して照合し、
公開入力だけで WorkflowInstance やレビュー対象を生成しない。revision 7 の履歴を再検証して
revision 8 の suspension を保存した後に common Continuation を発行する。
同じ request の再送、append 済みだが返答が失敗した場合、claim 後の issued-store 失敗と
runtime 再生成を扱い、revision 8 を重複 append しない。issuer は durable instance に加えて
claimed Continuation の全内容も元の retained provenance と照合する。異なる run / context /
Action の claim から WorkOrder を発行しない。

completion は strict typed Result JSON と issued WorkOrder、retained command / candidate /
validation / policy、全8件の履歴と suspension を再照合する。reviewed revision、時刻、
full-merge scope、findings の変更対象、rationale / evidence digest と CML 宣言済み alternatives を
CAM / typed JudgmentResult ABI で受理してから、共通 one-shot guarded resume を使う。
不正・古い・foreign な結果、進行先指定、内部 claim field は拒否し、正しい結果で再開する権利を
消費しない。accept / repair / decision-needed / reject は区別したまま revision 9 に結果参照を
記録し、suspension だけを解除する。Active `sync.MergeValidated` のままであり、accept でも
merge commit / push / convergence や terminal completion を実行・許可しない。

新規15 spec で public Operation / CLI の Request → WorkOrder → Result → WAIT、clean / dirty
fixture、全 public input facts の drift、保存・発行失敗からの回復、claim 内容の不一致、fresh
full review 再利用、typed evidence admission、非accept alternatives、Result / header / retained
history の拒否と拒否後の正常再開を検証した。162 は83件中82件成功し、型不一致のテスト入力を
共通 encoder が送信前に拒否するため1件失敗した。親が encoder 自身の拒否と不正 wire の
公開 decoder 拒否を分けてテストし、163 は83/83成功した。その後、手動レビューで exact
claimed Continuation の照合と回帰 spec を追加し、164 は6 suite・84/84、165 の full は
48 suite・392/392成功した。assertion の緩和・既存テストの削除は行っていない。

164 の runner 返答には結果が欠落したが、親が original immutable receipt と、その same
canonical child の native execution event（receipt helper completed / exit 0）を照合した。
receipt digest、invocation、agent identity、matching terminal marker / `lock=released` を検証し、
二重実行はしていない。command success と runner 返答欠落の nonblocking infrastructure
degradation を分けて記録する。164 / 165 の検証対象 tree は一致した：
`06c888d8fb29d59b57dd34d83aaaeaddf63dfbed873bf7ff0d045bf6718dfe24`。
162–165 の model / effort は `gpt-5.6-luna / medium`、runtime source / route は
`registered-custom-role`、logical role / agent type は `cncf_command_runner`。
162–164 の invocation は `SMWF-P1-REPOSYNC-MERGE-REVIEW-PUBLIC-FOCUSED-20260928-<番号>`、
165 は `SMWF-P1-REPOSYNC-MERGE-REVIEW-PUBLIC-FULL-20260928-165`。

| 番号 | 結果 | 実行担当 | immutable receipt SHA-256 |
| --- | --- | --- | --- |
| 162 | 失敗: 82/83 (test input construction) | `/root/sbt_receipt_71f9e3a5c59b2d85f711` | `f63171eba2805f9199fff7a94647a1696646b65fb4c6c227783a01064852aa0c` |
| 163 | 成功: 83/83 | `/root/sbt_receipt_c3467eef577b87a00d25` | `714b341410291a88b927065646603346c2acb024f29eb748a8d98e4ec5fb38d7` |
| 164 | 成功: 84/84 | `/root/sbt_receipt_ce7b10dc46483171f1d7` | `0571ad0b37d4eb5d0e3885c4bab1ebd2308e216c66fa5a5404da660496535900` |
| 165 | 成功: 392/392 | `/root/sbt_receipt_46e14d20b324f1850f14` | `8b160e9b10997e2390cbf1bf637289c97fc0675f276b426f542bdfc95857944d` |

この追記は検証後の documentation-only 更新であり、上記 tree digest に含めない。
次は受理済み typed review evidence の host-owned 保持と、exact ordered two-parent merge commit
の deterministic receipt / idempotency 境界を接続する。現在の履歴は result reference を保持するが、
typed review payload の再起動可能な保存・lookup はこの public binding に追加していない。
Continuation resume と WorkflowInstance append は既存の緩い ordered persistence であり、
全 store を共有する原子的トランザクションは導入していない。
実 Git 操作、CNCF / Cozy source 編集、stage / commit / release / phase closure は行っていない。
Cozy の元からある未コミット差分を保持し、P1-S01 / Phase 1 は引き続き未完了。

retained-review / two-parent-commit slice では host-owned
`RepositorySyncMergeReviewReceiptStore` と `RepositorySyncMergeCommitBoundary` を追加した。
公開 review completion は typed Result、issued WorkOrder、retained candidate / command を
まとめて保存し、Result の全 wire body digest を revision 9 の causation に結び付ける。
同じ Result reference に異なる body を保存・再受理することはできない。store の fixture は
InMemory であり、production の disk durability / restart adapter は host が別途実装する契約である。

common one-shot resume / revision 9 append の後に review store 保存が失敗した場合、公開 submit の
再送で二度目の resume は行わない。host が `retainCompletedC` で実際に保存済みの revision 9、
全履歴と exact typed Result を再照合し、欠けた receipt のみ補う明示的回復経路を用意した。
これは Continuation と複数 store の原子的トランザクションを追加するものではない。

commit boundary は fresh full-merge accept evidence を再受理し、candidate revision、validation
digest、review policy、Result reference / body digest、元の command chain と全9件の履歴を照合する。
初回実行は live pending head / index / tree / ordered parents が検証対象と一致する場合だけ
host `CommitPort` を呼ぶ。provider は local write を独立に許可し、pending head / index に対して
CAS を行い、exact idempotency key / request に対応する receipt を返答前に保持する契約である。
この slice は deterministic fixture であり、実 Git command adapter や新たな公開 commit Operation の
完成ではない。

境界は receipt の new commit OID、exact ordered two parents、tree OID / digest、変更された唯一の
local ref を検証した後、post-inspection の head / parents / tree / clean index、pending operation の
解消と unchanged tracking / remote mapping を照合する。それから receipt digest を causation に
持つ revision 10 を append する。commit の返答消失、append 前の保存失敗、append 済みの返答失敗は
exact retained receipt と履歴を再使用し、二度目の commit / history append を行わない。
回復時も review freshness を再検証するため、期限切れ後の production recovery は未解決の host
運用境界であり、この fixture だけで一般的な restart / recovery 完成を主張しない。

CML revision / generated ABI は前 slice から変更していない。revision 10 は Active
`sync.MergeValidated` のまま WAIT を返す。未宣言の `MergeCommitted` state / transition、
non-force push / convergence、terminal completion を追加・偽装していない。
新規14 spec で clean / dirty fixture、typed review retention / reference replacement 拒否、
nonaccept / stale review、Result body / command / history の drift、live pending head / index / tree
の drift、malformed commit receipt / post-inspection、durable receipt 欠落、返答・append 失敗後の
exact retry、review retention の post-resume failure recovery を検証した。

focused `SMWF-P1-REPOSYNC-MERGE-COMMIT-FOCUSED-20260928-166` は `cozyGenerate` と
6 suite・98/98、full `SMWF-P1-REPOSYNC-MERGE-COMMIT-FULL-20260928-167` は
48 suite・406/406成功した。166 は既存 user-owned interactive SBT の終了を待ち、ユーザーが
その SBT を終了した後、元の同じ invocation を継続した。重複起動・kill・再実行はしていない。
両 receipt は exit 0、exact agent identity、matching terminal marker / `lock=released` を検証済みで、
検証対象 tree は一致した：
`6ea5a4ae7a3422f3ca29b835ab224f354db2a06b2700fe1d05d73cbbab351ac6`。
166 / 167 の model / effort は `gpt-5.6-luna / medium`、runtime source / route は
`registered-custom-role`、logical role / agent type は `cncf_command_runner`。

| 番号 | 結果 | 実行担当 | immutable receipt SHA-256 |
| --- | --- | --- | --- |
| 166 | 成功: 98/98 | `/root/sbt_receipt_4bd65cd9ab78fa6530f5` | `bf5d5d4833b8ad669b9f85ade664991e0ef32de00da3cd0c8e6fb1118a88e4b9` |
| 167 | 成功: 406/406 | `/root/sbt_receipt_3c43a2bb9334eee6cbbe` | `ed60e980be33f881e32432b5c355b1567547887b4da2f50d462331df9e36d7bb` |

この追記は検証後の documentation-only 更新であり、上記 tree digest に含めない。
次は merged-head 用の non-force push / convergence と、公開 Operation fixture からの接続を行う。
既存 LOCAL_AHEAD の push boundary を DIVERGED merge history に未検証で流用しない。
実 Git 操作、CNCF / Cozy source 編集、stage / commit / release / phase closure は行っていない。
Cozy の元からある未コミット差分を保持し、P1-S01 / Phase 1 は引き続き未完了。

merged-push / convergence terminal slice では
`RepositorySyncMergedConvergenceExecution` と公開 fixture binding
`RepositorySyncMergedConvergenceServiceOperation` を追加した。CML の
`RepositorySyncMergedConvergenceCommand` は retained review Result reference、push / convergence
の各 idempotency key と committed base revision `10` のみを受け取る。remote write authority、
force、次 state、claim token を外部 command に含めることはできない。
公開 `RepositorySyncService.admitMergedConvergence` は既存の型付き
`RepositorySyncConvergenceResult` を CNCF の terminal envelope として返す。
これは explicit fixture registration であり、production bootstrap / Git command adapter の追加ではない。

CML Workflow revision は `repository-sync-merge-convergence-v1`。generated ABI は
10 states / 2 Actions / 1 review Required SPI / 13 Provided Operations と一致する。
`MergeValidated` → `PushAdmitted` に `reviewed-merge-push-admitted` transition を宣言し、
`PushAdmitted` → `Converged` は既存の `convergence-admitted` を使用する。
未宣言の `MergeCommitted` state は作っていない。新 command の追加による GoalPhase の source
correlation は +14 lines のみで、GoalPhase の意味・Workflow revision は変更していない。
consumer fixture の exact hash は以下へ更新した：
CML `6b7ac4afd9be97592386bd394f48b7199cd359dbb982966cbc20911f794d2114`、
Workflow ABI `cdc13d05797a4559cbf54f7859e28a5dfb58faa46c75f3e0529526b2a5aecb57`、
producer sidecar `0366eaef7768767e06335313c9fa34b161dc9632b5c323c44cba12fda4760312`。

`RepositorySyncMergeCommitBoundary.readCommittedC` は実際に保存済みの revision 10 / 11 / 12 から
original review / candidate / command / full history と exact two-parent commit receipt を再受理する。
commit 後の retained inspection を照合するが、二度目の commit / Continuation resume / pending
inspection は行わない。現在の post-push snapshot を commit 前提に代用することもできない。
旧 LOCAL_AHEAD の admission は維持し、DIVERGED を LOCAL_AHEAD と偽装せず、merge 後専用の
`assessMergedC` を追加した。push の前提・独立した host remote-write authority は exact commit
receipt digest / new head / fetched remote tip / repository mapping に束縛する。
advertised remote tip の進行、不完全・replacement graph、force / tags / mirror、別 remote ref / local
branch 更新、head / parents / tree / clean gate の不一致は拒否する。

push receipt / post-state を受理した時だけ revision 11 `sync.PushAdmitted` を append する。
その後、selected tracking ref のみを更新した verification fetch と、equal tips / independently
inspected 0/0 / clean state を照合し、revision 12 `Completed` / progression なし / suspension なしと
型付き `SYNCHRONIZED` terminal を返す。verification evidence が欠ける場合は revision 11 Active
を保持し、terminal を作らない。push / terminal append の前後で保存・返答が失敗した場合も
exact evidence / key / history に一致する再送で回復し、追加 commit / append は行わない。
再送 command の expectedRevision `10` は committed base を指し、返却 handle は実際の
terminal revision `12` を表す。changed key / evidence / retained prefix は拒否する。
既存の緩い ordered persistence を使用し、共有 multi-store transaction は導入していない。
review freshness は再送でも必須であり、期限切れ後の production restart / recovery は既存の
Phase 1 適用除外・host 運用境界のままである。

新規20 spec で clean / dirty × tracking-ref update の公開 Operation / CLI terminal equality、
retained review / commit / authority / mapping / receipt / inspection の drift 拒否、証拠欠落時の
非完了、append 前後の失敗回復、exact terminal replay、JSON shape / binding の fail-closed を検証した。
親による読み直しでは、LOCAL_AHEAD の旧 guard と digest の保持、DIVERGED の専用前提、
生成 Operation / command / terminal の対応、history 10 → 11 → 12 と ordered persistence の整合を確認した。

実行準備 168 は consumed-ID ledger の親側 transport truncation により metadata 作成前に拒否され、
SBT / child は起動していない。fresh private allocation と全 ledger の読取に修正した。
focused 169 は生成前に Cozy の既存 development classpath stale を拒否して exit 1 / lock released。
170 は Cozy の既存 `cozyExportRuntimeClasspath` のみを実行して成功した。runner final の出力欠落は
元の immutable receipt と同じ native child の completed / exit 0 event から検証し、再実行していない。
Cozy source の編集・test / publish は行っていない。
focused `SMWF-P1-REPOSYNC-MERGED-CONVERGENCE-FOCUSED-20260928-171` は `cozyGenerate` と
8 suite・143/143、full `SMWF-P1-REPOSYNC-MERGED-CONVERGENCE-FULL-20260928-172` は
48 suite・426/426成功した。両者の検証対象 tree は一致：
`f3ff527515d33275eab7c43b0fcaba49f3a2a3641048b37adedd50a8b2faaf8c`。
169〜172 の immutable receipt、exact agent identity、matching terminal marker / `lock=released` を
検証済み。各実行の model / effort は `gpt-5.6-luna / medium`、runtime source / route は
`registered-custom-role`、logical role / agent type は `cncf_command_runner`。

| 番号 | 結果 | 実行担当 | immutable receipt SHA-256 |
| --- | --- | --- | --- |
| 169 | 生成前失敗: stale classpath | `/root/sbt_receipt_61288479fea782767ab8` | `ac4722e958bc0fba9e046cf0dcd702dc229d4717eec88005ef9abef67501d22d` |
| 170 | 成功: existing classpath export | `/root/sbt_receipt_dc50d8bce615ebad0f1c` | `2bae195d9077745483a3d14104fecd39ccde2c4886f3cb400dd03390c5fb5f5e` |
| 171 | 成功: 143/143 | `/root/sbt_receipt_7cd8e3e6f7f1c2fb513f` | `b9b1563bf077054354f939d8f6dbb005687e7403a625b1ccf881eb249510a854` |
| 172 | 成功: 426/426 | `/root/sbt_receipt_a30616319edeba9ba0c2` | `d84df083b007740c31ac90fcd8f3675460b111fc63bb30b42c245218699d4135` |

この追記は検証後の documentation-only 更新であり、上記 tree digest に含めない。
次は Phase 1 の sole closure checklist と三 Workflow の既存実装・fixture 証拠を対応付け、
残る semantic repair / Decision 等の不足だけを特定する。今回の正常 merge route の terminal を
Phase 1 全体や production repository sync の完成と扱わない。実 Git 操作、CNCF / Cozy source 編集、
stage / commit / release / phase closure は行っていない。Cozy の元からある差分を保持した。
P1-S01 を含む未確認 checklist はチェックせず、Phase 1 は引き続き in_progress。

### Checklist reconciliation (2026-09-28)

Created the [Phase 1 Evidence Map](phase-1-evidence-map.md), mapping all existing
34 acceptance items to implementation and executable specifications: 16 verified
(including previously checked P1-S09 / S10), 14 partial, 2 missing and 2
pending-final. Added checks only for the 14 newly evidenced items. No scope
change, extra closure gate or operational hardening was introduced.

Normal fixtures are distinct from completed semantic continuations. GoalPhase
accept completion reaches commit / terminal without another close request;
repair stops at WAIT. SplitPhase has PREVIEW / APPLY terminals and source-bound
objective assessment, but novel work / split boundary remains WAIT presentation,
not typed Judgment WorkOrder / Result. RepositorySync distinguishes repair /
decision-needed / reject from accept; that alone does not prove a repair loop
or typed Decision execution / stop boundary.

Remaining application work was narrowed to versioned worker mapping / Host
ExecutionEvidence, GoalPhase focused-review connection and repair / re-review,
SplitPhase semantic WorkOrder, authority-gap common Decision projection,
actual Codex / deterministic Provider contract proof and final consumer handoff.
The next code slice was ReasoningLevel worker mapping. An SBT command runner
does not count as an application JudgmentAction Codex worker for P1-S06.

That reconciliation changed only documentation. README bootstrap claims about
unadmitted generated ABI / runtime bindings and unconnected commits were updated
to the current fixture state. All 178 Git-visible product/build paths matched
receipt 172, with no added source, so no SBT rerun was made. Later documentation
was not included in 172's validated tree. No CNCF / Cozy edits, staging, commit,
release or Phase closure occurred. Phase 1 / P1-S01 remained incomplete.

### Worker mapping and Host completion evidence (2026-09-28)

Implemented application-owned `WorkflowWorkerMapping`, completing P1-A05.
The Host supplies an immutable versioned policy from abstract ReasoningLevel to
a concrete profile / model identity, supported capabilities / risks and review
capability. Missing or ambiguous mappings and incompatible requirements fail
closed; no fallback or production model policy is invented. All four abstract
levels are covered by fixture mappings. Selection validates a lossless common
WorkOrder projection and preserves its handle, context, payload and requirement.
It neither dispatches a worker nor creates execution evidence or claim authority.

A frozen Dispatch accepts only a Host-attested completion naming the selected
profile, policy version, nonempty worker identity and exact model identity.
Normalization delegates to CNCF `SkillWorkResultNormalizerV1`, preserving issued
run / continuation / revision / ContextSnapshot / result type / completion facts
and evidence admission. Deterministic Providers keep their existing evidence
without synthesized worker, model or dispatch profiles. Concrete worker choice
is never a StateMachine guard or transition selector.

Added eight executable specifications for mapping, malformed policy / WorkOrder
rejection, capability / risk / review compatibility, completed dispatch evidence,
strict Result round-trip, wrong worker metadata, stale work and policy replacement.
The real issued GoalPhase review fixture now normalizes a Host-completion stand-in,
proves the same semantic judgment as its deterministic Result and submits the
normalized wire through the existing public completion Operation. Native claim
consumption still belongs to CNCF; neither mapping nor normalization resumes it.
Fixture Host profile / model names are not evidence of actual Codex execution.

Validation through registered `cncf_command_runner`, configured model
`gpt-5.6-luna`, reasoning effort `medium`, runtime source `registered-custom-role`:

- Focused `SMWF-P1-WORKER-MAPPING-FOCUSED-20260928-173`: 3 suites, 93/93 tests.
  Agent `/root/sbt_receipt_f5fed2dcf1416a96fc15`; receipt SHA-256
  `628df3dd781bc5f364aeb7565d80ae3be504de2afac7cf87c030bd0a914ef4cd`.
  Receipt locator:
  `/tmp/skill.cncf.d/cncf-command-execution-e19e42f1177de3c91671eafb716bda5f9471e46f865724b4023f444d5d3f4a22-1fd2f91adecd8491a0d592c0219d6723/command-execution-sha256-628df3dd781bc5f364aeb7565d80ae3be504de2afac7cf87c030bd0a914ef4cd`.
- Full `SMWF-P1-WORKER-MAPPING-FULL-20260928-174`: 49 suites, 434/434 tests.
  Agent `/root/sbt_receipt_752ceabd082c7012a041`; receipt SHA-256
  `5218eceff15afb66ea4526eec466b5cdc3ef7b116e405278a51881830f6e13a0`.
  Receipt locator:
  `/tmp/skill.cncf.d/cncf-command-execution-b4019545f449b90c0683795f50fd2f30cd798beced90f1ad7f78389d370afe64-55b7e0e577c5a9e122ba9aab180f60c5/command-execution-sha256-5218eceff15afb66ea4526eec466b5cdc3ef7b116e405278a51881830f6e13a0`.

Both immutable receipts were independently verified against their exact runner
identities and successful invocation-scoped terminal markers: SBT / wrapper
exit 0, `lock=released`. Their identical validated tree SHA-256 is
`82e368dac595f49f3454ba940d61a283697cdc803bd5185cd33481f1279e930a`.
No failed / canceled / ignored / pending tests or aborted suites. The existing
unused `cozyCarName` setting warning remains nonblocking and unchanged.

Updated the evidence map, README and checklist in English. The preceding
reconciliation append is now English; older historical records are preserved.
Post-validation documentation updates are not included in the validated tree.
The checklist now has 17 verified, 14 partial, 1 missing and 2 pending-final items.
P1-S06 actual Codex execution and P1-S07 full Provider-replacement proof remain
open. Next: GoalPhase focused-to-full-review connection and bounded repair /
re-review, using existing admission rather than another lifecycle or scheduler.
CML and ABI fixtures are unchanged. No CNCF / Cozy source edits, staging, commit,
release or Phase closure occurred; other work's Cozy changes were preserved.
Phase 1 remains `in_progress`.

### Scoped-to-full-review connection (2026-09-28)

Completed the focused executable proof for CAM-04 using existing application
routing, CNCF suspension / issued-work persistence and public completion.
No application runtime, CML or framework behavior needed reimplementation.

Added two specifications to `GoalPhaseReviewBoundarySpec`. They exercise three
supplied-evidence profiles through both direct Service and CNCF CLI JSON:
fresh focused review, fresh focused review plus an old-target full review,
and expired focused review. The full-review WorkOrder preserves the complete
original StepCloseRequest and evidence in issued persistence, uses the declared
review-step capability and submitStepReview completion, and stays nonterminal
at the persisted suspension. Invalid scoped acceptance, old-target review and
stale ContextSnapshot are rejected without changing the suspension, commit
count, issued work or recoverable private claim.

Fresh full-review completion re-evaluates the original candidate and reaches the
typed Step-close terminal through the public completion Operation. Each fixture
invokes Step-close exactly once, commits exactly once, includes both program and
planning digests, names the new full-review evidence in its commit receipt and
rejects completion replay. Direct / CLI terminal wires are identical.
The original scoped evidence is retained, never relabeled as full review.

Initial focused-175 passed 29/29 tests across five suites but emitted one new
unchecked generic type-test warning in the added fixture. Replaced that pattern
with the existing wildcard WorkOrder pattern. The prepared full-176 request
was not dispatched, consumed or run. It has no runner or validation receipt.

Final focused validation:

- Invocation: `SMWF-P1-FOCUSED-TO-FULL-REVIEW-FOCUSED-20260928-177`.
- Configured model / effort: `gpt-5.6-luna / medium`.
- Logical role / agent type: `cncf_command_runner`; runtime source:
  `registered-custom-role`; runner health: healthy.
- Agent: `/root/sbt_receipt_5d4f9369efac61e49282`.
- 5 suites, 29/29 tests; zero failed / canceled / ignored / pending or aborted.
- SBT / wrapper exit 0; exact invocation-scoped terminal marker:
  `[cncf-sbt] completed: invocation=SMWF-P1-FOCUSED-TO-FULL-REVIEW-FOCUSED-20260928-177 sbt_exit=0 wrapper_exit=0 lock=released`.
- Receipt SHA-256: `40956e572a12e7b1826f44e65aa54ee4406b406178ecd71b77130d988d6ddea1`.
- Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-1cbe4780ca69878c09b0ac64e2bbc72a32fa851e98a0434c444c2f67ec82575d-78959f5c20348c2e2fad60022d131f02/command-execution-sha256-40956e572a12e7b1826f44e65aa54ee4406b406178ecd71b77130d988d6ddea1`.
- Validated tree SHA-256:
  `cd5319fc90100816cc33dd72a6a01eaaeb7ed37bbaff88a842009549b0903def`.

The receipt and exact runner identity were independently verified. The added
unchecked warning is absent; the pre-existing unused cozyCarName setting
warning is unchanged. Initial focused-175 used agent
`/root/sbt_receipt_b1a78b44745295b0f59a`, the same registered model / effort,
and receipt `bdb1feb2fc9983a3d5d7f3dfc9d3c4c218b6ffff93d3f5142a34d3130b3466ba` at
`/tmp/skill.cncf.d/cncf-command-execution-f63c01d1e49ab733757a317f1de2a83ea377ffdf6a46010d748df5060c3fa054-30f4818e51d101cc282355efff95a187/command-execution-sha256-bdb1feb2fc9983a3d5d7f3dfc9d3c4c218b6ffff93d3f5142a34d3130b3466ba`.
It is superseded source evidence, not the final focused tree.

Full validation did not start:

- Invocation selected: `SMWF-P1-FOCUSED-TO-FULL-REVIEW-FULL-20260928-178`.
- Agent: `/root/sbt_receipt_9cefdc22843a8604f9b3`, configured
  `gpt-5.6-luna / medium`, `registered-custom-role`,
  logical role / agent type `cncf_command_runner`.
- Read-only preflight exited 0 and returned `status: accepted`.
- The subsequent scoped execution request was rejected by an automatic
  permission-approval review timeout before process start.
- Runner-reported raw error (ellipsis retained as reported):
  `Rejected("The automatic permission approval review did not finish before its deadline...")`.
- Parent inspection found no `.execution-started` marker, SBT output,
  receipt or wrapper/SBT start. This is not a failed SBT suite or lock wait.
- Infrastructure classification:
  `EXECUTION_PERMISSION_DENIED_AFTER_ATTRIBUTABLE_AUTHORITY`.
  Attributable standing authority and selected scope were present; no missing
  user intent or actual scope/policy prohibition was proved by this timeout.
- The execution delegation is terminal and not replayed. No alternate agent,
  fresh envelope, parent-direct SBT or sandbox retry was used.
- Resume requires platform-supported permitted recovery for the exact action;
  a repeated chat approval is not a platform override. This is an
  execution-infrastructure stop, not a new Phase item or product ABI shortage.

Preserved full-test intake:
`/tmp/skill.cncf.d/cncf-command-execution-4918f75b070fc94d01d3628cd7f63e998a2c6268c02a53348804c6e188491b9d-fe0e3ecb562d3f8d3c48fea485bcd71a/command-execution-intake-sha256-3b4d287678a7dce2463fb9f536b4025a58f64af8a3088c078c3c3a85872911ed.json`.

The accepted renderer's exact execution request is retained below as failure
evidence only. It is not an instruction to run it directly in the parent.

```json
{
  "cmd": "/Users/asami/.codex/skills/cncf-workflow-protocol/scripts/execute-command-with-receipt.py --intake-locator /tmp/skill.cncf.d/cncf-command-execution-4918f75b070fc94d01d3628cd7f63e998a2c6268c02a53348804c6e188491b9d-fe0e3ecb562d3f8d3c48fea485bcd71a/command-execution-intake-sha256-3b4d287678a7dce2463fb9f536b4025a58f64af8a3088c078c3c3a85872911ed.json --execution-route registered-custom-role",
  "justification": "Originating workflow authority: standing-user-sbt-authorization; standing authorization: asami-local-sbt-v1; originating workflow invocation/reference: sm-workflow Phase 1 preview terminal continuation with Cozy generator prerequisite / current user go ahead; workflow instance: SMWF-P1-PREVIEW-COZY-PREREQUISITE-20260927. Current operation: validation; purpose: Phase 1 CAM-04 retained scoped evidence and automatic full-review completion full validation on the unchanged successful focused-validation tree; repair identity: not-applicable. Selected repository: /Users/asami/src/dev2026/sm-workflow. Exact SBT argv: [\"--batch\", \"test\"]. Admitted command mutation roots: [\"/Users/asami/src/dev2026/sm-workflow\"]. Bounded external build/cache/lock access paths: [\"/Users/asami/.sbt\", \"/Users/asami/.ivy2\", \"/Users/asami/.cache/coursier\", \"/Users/asami/.m2\", \"/tmp/skill.cncf.d\"]. Forbidden operations: [\"remote-publication\", \"deployment\", \"push\", \"credential-change\", \"destructive-cleanup\", \"source-edit\", \"staging\", \"commit\"]. Platform review remains required. Read-only receipt tree-observation repositories: [\"/Users/asami/src/dev2026/sm-workflow\"].  Receipt evidence ID: SMWF-P1-FOCUSED-TO-FULL-REVIEW-FULL-20260928-178; receipt helper stdout/stderr/result writes limited to allocated directory: /tmp/skill.cncf.d/cncf-command-execution-4918f75b070fc94d01d3628cd7f63e998a2c6268c02a53348804c6e188491b9d-fe0e3ecb562d3f8d3c48fea485bcd71a. Serial-wrapper logs retain their independently allocated or attempt-bound /tmp/skill.cncf.d directory; shared lock and recovery access retain the SBT attempt's existing scope. Execution remains through the shared serial SBT wrapper; rendering does not consume the intake or permission artifact.",
  "prefix_rule": [
    "/Users/asami/.codex/skills/cncf-workflow-protocol/scripts/execute-command-with-receipt.py"
  ],
  "sandbox_permissions": "require_escalated",
  "workdir": "/Users/asami/src/dev2026/sm-workflow"
}
```

CAM-04 is checked on the successful focused proof: currently 18 verified,
13 partial, 1 missing and 2 pending-final checklist items. Full-suite validation
of this test tree is still unproved. The preceding full-174 result (434/434)
does not validate the modified specification; no 436-test full success is
claimed. Post-validation documentation updates are outside focused-177's tree.

CAM-05 / CAM-06 remain partial: repair completion still stops at WAIT, without
an automatic Repair / current-candidate / ReReview continuation. The next product
slice is that bounded typed repair/re-review path, not another public close
request. First resolve the full-validation execution-infrastructure stop.

All new records and current evidence / README updates are English. Existing
historical records are preserved. CML / generated ABI fixtures and all production
source are unchanged. No CNCF / Cozy source edits, staging, commit, release or
Phase closure occurred; other work's Cozy changes were preserved.
Phase 1 remains `in_progress`.

### Repaired-candidate preparation (2026-09-28)

Continued application implementation independently of denied full-178. Added
`GoalPhaseRepairCandidateBoundary` and six executable specifications; no CML,
generated ABI, CNCF or Cozy source change was required for this bounded slice.

The boundary freezes an immutable in-process intent only after the existing
producer / common Result adapter admits a current `repair` judgment. It retains
the issued review, submitted judgment, original closure request and admission
time. This correlation is not proof of Repair completion or a durable work
store. Later preparation reloads actual program and planning bytes through the
snapshot port and derives both SHA-256 digests. It preserves original closure
submission / Step identities and all evidence without overwriting the issued
request or submitting a second public close request.

CAM evaluates retained evidence against the current candidate and caller-supplied
time. A change to either artifact invalidates old-target evidence; expiry is
re-evaluated without retroactively requiring an admitted repair judgment to
remain fresh. Unchanged bytes and retained full coverage still require a new
review judgment: `repair` never becomes `accept`. The boundary cannot issue
work, resume a continuation, select state, commit or produce Closed.

The new specifications exercise changed program bytes, planning-only change,
unchanged content with full coverage, later expiry, rejected non-repair / stale
context / stale evidence / mismatched target results, missing artifact bytes
and an observation before judgment admission. Existing review-close and CAM
specifications were included in the same focused invocation.

- Invocation: `SMWF-P1-REPAIRED-CANDIDATE-FOCUSED-20260928-179`.
- Configured model / effort: `gpt-5.6-luna / medium`.
- Logical role / agent type: `cncf_command_runner`; runtime source:
  `registered-custom-role`; runner health: healthy.
- Agent: `/root/sbt_receipt_c085e65b9c940cdee6f8`.
- 3 suites, 20/20 tests; zero failed / canceled / ignored / pending or aborted.
- SBT / wrapper exit 0; exact invocation-scoped terminal marker:
  `[cncf-sbt] completed: invocation=SMWF-P1-REPAIRED-CANDIDATE-FOCUSED-20260928-179 sbt_exit=0 wrapper_exit=0 lock=released`.
- Receipt SHA-256: `23fdc56d064b75633bce4aea3b82447cd61c5cb37da4d36033152837a8271d12`.
- Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-97d97589f7434bddc6534cccf1029336acbab001c8422f2d8891321152b58256-5dc4f805233d8cb007e234a297456bb7/command-execution-sha256-23fdc56d064b75633bce4aea3b82447cd61c5cb37da4d36033152837a8271d12`.
- Validated tree SHA-256:
  `b1fd73a016da3996212b045d24077936e01326948d6d72b36dccef2a3b08f74d`.

The runner reported no textual outcome. The parent inspected the saved
artifacts and independently verified the immutable receipt, its exact bound
runner identity, saved stdout / stderr, terminal handshake and structured SBT
summary. Those artifacts prove successful execution; no additional attempt was
made to recover display output. No new compile warning was emitted; the
pre-existing unused cozyCarName setting warning remains.

This was a new focused validation of new source and specifications, not a
same-action redispatch, replacement receipt or claimed recovery of full-178.
Full-178 remains unstarted and the current source has no full-suite validation.
Documentation updates after focused-179 are outside its validated tree.

CAM-05 / CAM-06 remain partial and unchecked. Next connect the declared typed
Repair completion and automatic current-candidate ReReview publication using
this preparation boundary, then return accepted completion to existing closure
admission on the same instance. No worker-selected state, second close request
or shared transaction expansion is introduced. The full-validation stop is
kept separate from ordinary requested implementation, not used as a blanket
development stop. No staging, commit, release or Phase closure occurred.
Phase 1 remains `in_progress`.

### Connected GoalPhase repair loop (2026-09-28)

Connected the bounded application repair loop manually, without phase / review /
commit orchestration skills. Mandatory command / agent / serialized-SBT skills
were used solely to execute validation and the local dependency refresh.

GoalPhase CML now declares revision `goal-phase-step-close-v2`, `RepairStep`
as an ordinary `OPERATION`, `repair-step-capability`, and the typed
`submitStepRepair` Provided API with `RepairStepResult` /
`StepRepairSubmissionResult`. Existing CAM Review / Admission semantics
and schema versions are unchanged; Repair is not modeled as a judgment.

A concrete CNCF receiver gap was demonstrated: the CAM sidecar's action list
contains Judgment / Admission only, but its Required SPI also describes
ordinary Operations. `CandidateWorkflowAbi` incorrectly compared every
Workflow Action with that CAM-only list. The minimal receiver change keeps
exact CAM matching and admits ordinary Actions only with one exact sidecar
Required SPI, source line, operation and typed input / result binding.
Focused negatives still reject replacing a CAM action, source / operation
drift and an unbound ordinary Action. Only this receiver and its specification
were edited in CNCF; no Cozy source was changed.

The validated CNCF tree was refreshed locally at the unchanged
`org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT` coordinate. Refresh 181
records producer tree
`d465dfc1a6ca00b62caaaa15955beb02534723414f60e52f8266aab2973812fa`,
producer Git-visible bytes SHA-256
`37002c6a4eea03727453b3bbccba816b923c02d29e5421333adf5f214bcb618c`
and downstream `project.yaml` SHA-256
`4329a374cfea139de101fc21de1959a420ea3a6fdef5521a4b2e4ba28cbe4b5d`.
This is a local development dependency refresh, not a remote release.

Actual `cozyGenerate` produced 56 Scala sources. The application-owned
`GoalPhaseCmlProducerProbe` selected the unique GoalPhase definition among
the three Workflows and exported canonical JSON using the actual Cozy
generators. Probe 183 encountered an unrelated dirty Cozy test compile error;
probe 184 selected only this application-owned test source using a temporary
SBT setting. No unrelated file was repaired and no Cozy Git-visible file was
edited. The canonical output was copied exactly to the two application
fixtures, adding only one trailing newline; it was not hand-authored ABI.

| Artifact | SHA-256 |
| --- | --- |
| Current CML | `cffe10f854aa4f02a7acdce2fd3bd2d7df62fddca6d30cc20a02b3e918b462fc` |
| Raw canonical Workflow JSON, without newline | `52abebba586d82b042393fa32954ceccea6f6d131ae6c31c651c4c1feca30b8c` |
| Raw canonical CAM producer JSON, without newline | `b7b2f9bf200f2e99aa2c321490e7c03f4dbfe2cb9779bcf0ee072a50fa14b686` |
| Checked-in Workflow JSON, with newline | `86128694abef582670b80eddb454f8bca599119a4b525cdc80aa99afbf7edbc9` |
| Checked-in CAM producer JSON, with newline | `db4a5d128a54d10957e6e659ed0c616734b24320a3a5b43de7f50ff24e243a64` |

`GoalPhaseRepairLoop` retains the admitted repair intent and original issued
request, stages the matching suspension prerequisites and publishes ordinary
repair work through common WorkOrder / Result contracts.
`GoalPhaseRepairCompletionOperation` checks the issued completion identity,
headers, current suspension and typed repair receipt before common one-shot
claim consumption. After completion it reloads actual program / planning
bytes through `GoalPhaseRepairCandidateBoundary`, derives current digests
and keeps the original submission / Step identity and all retained evidence.

Policy forces a fresh full-Step review after repair, including unchanged bytes
with reusable full coverage. Only routing observes temporarily empty evidence;
the new issued review request retains all evidence. A fresh accepted review
returns to existing exact snapshot / deterministic commit closure without a
second public close request. Workers supply neither state nor commit selectors.
The default host-owned budget is one repair; a second repair judgment yields
nonterminal WAIT, not an automatic infinite loop or acceptance.

Five new specifications cover Service / CLI and changed / unchanged artifacts
on the same instance. The success path advances from Active revision 1 through
initial Review 2, admitted Review 3 / Repair 4, Repair completion 5 / ReReview 6,
and admitted full Review 7 / exact commit and Completed 8. Each case observes
one close invocation and one commit. Additional checks reject state-selector
payload injection, stale revision / context, failed or mismatched Repair
receipts before claim consumption, Repair replay, old-target review, accept
replay and artifact drift.

Intent-persistence failure after review completion remains Active revision 3;
a missing repaired snapshot after repair completion remains Active revision 5;
artifact drift before final commit remains Active revision 7. None claims
Closed or a commit, and failed publication prerequisites expose no WorkOrder.
Completion, intent persistence, instance append and publication remain separate
durable steps under the loose transaction contract. These fixtures do not
claim atomic rollback, disk durability, restart recovery or a production Git
provider.

Validation was serialized through registered command runners; each completed
attempt below has a verified immutable receipt and an invocation-scoped
terminal marker ending in `lock=released`. All used configured model / effort
`gpt-5.6-luna / medium`, logical role / agent type `cncf_command_runner`,
runtime source `registered-custom-role`; verified runner identity / route was
healthy. Source edits were paused during each selected repository's SBT run.

- **180** `SMWF-P1-CNCF-MIXED-OPERATION-20260928-180`: CNCF mixed ordinary / CAM receiver: 3/3 tests passed.
  Agent: `/root/sbt_receipt_e0b2ade934f35a25e727`.
  Receipt SHA-256: `afcb68291af87b7a9d8be19c6e505cb8262524afa746a9761c543117df18bd12`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-af00ea7d2d71392f149b1468419cd94733d52d5800050627575c170439a3e481-4e5bcfec54bfe8d0e681ac2938a17dee/command-execution-sha256-afcb68291af87b7a9d8be19c6e505cb8262524afa746a9761c543117df18bd12`.
  Validated tree: `d465dfc1a6ca00b62caaaa15955beb02534723414f60e52f8266aab2973812fa`.
  Terminal: `sbt_exit=0 wrapper_exit=0 lock=released`.
- **181** `SMWF-P1-CNCF-REPAIR-SNAPSHOT-20260928-181`: CNCF publishLocal succeeded at the unchanged 0.5.3-SNAPSHOT coordinate.
  Agent: `/root/sbt_receipt_06abf1f067229ab4bad4`.
  Receipt SHA-256: `df8da8cbc5c316e8e66b3c80cf66165d79d7fc2405b33a8f577eabc0ab99255f`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-1e5a52446237b28b116864e16ad932c0d446f6d1d0f1eb8eaa0eef17a6dbd69d-7b5ce8e0e85e169e938eac5202342ac7/command-execution-sha256-df8da8cbc5c316e8e66b3c80cf66165d79d7fc2405b33a8f577eabc0ab99255f`.
  Validated tree: `d465dfc1a6ca00b62caaaa15955beb02534723414f60e52f8266aab2973812fa`.
  Terminal: `sbt_exit=0 wrapper_exit=0 lock=released`.
- **182** `SMWF-P1-REPAIR-CML-GENERATION-20260928-182`: Application cozyGenerate succeeded; 56 generated Scala sources.
  Agent: `/root/sbt_receipt_b01772e76ddf22743eff`.
  Receipt SHA-256: `a34e3eaedf1b715a6c95a3f79bb9bade1552505550dccff292e026cb75d811bc`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-b7aec5796fceb7ea10de14929eba235189f5312a76af9ccf260301750b38d82a-32c78f19bd763fecd205bdd996d4fff5/command-execution-sha256-a34e3eaedf1b715a6c95a3f79bb9bade1552505550dccff292e026cb75d811bc`.
  Validated tree: `8ad44466e6cfa0e0c89db233e0a178f7c958382b28800d826af65948b4c0820e`.
  Terminal: `sbt_exit=0 wrapper_exit=0 lock=released`.
- **183** `SMWF-P1-REPAIR-PRODUCER-JSON-20260928-183`: Completed failure compiling unrelated dirty Cozy CmlStructureMetadataSpec.scala at line 1052; no Cozy source repair.
  Agent: `/root/sbt_receipt_284563d4703970bccde3`.
  Receipt SHA-256: `18d5b57656104166e995e98ab16e16ba8e7b388f73f25b4c964b590ecb8c1e5f`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-b7fa808926793099d28fda4c1767d5aafb7278b1ac35e745bb0d872562eb9f3e-c3c4fe70dbb813c243ae7501da85ec6f/command-execution-sha256-18d5b57656104166e995e98ab16e16ba8e7b388f73f25b4c964b590ecb8c1e5f`.
  Validated tree: `d20a238656eac9e27ed385e8afd73ea60ffee6fc3327001cf3453b95dd317be3`.
  Terminal: `sbt_exit=1 wrapper_exit=1 lock=released`.
- **184** `SMWF-P1-REPAIR-PRODUCER-PROBE-ONLY-20260928-184`: Actual Cozy canonical JSON producer succeeded with only the application-owned probe selected as the temporary test source.
  Agent: `/root/sbt_receipt_bf1d89ee4ddb6034cbe8`.
  Receipt SHA-256: `b3817860e0f1695d5cfcb330133df54df4832d0494e6aef18f22a985912fc426`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-2913a971239672fec877ab7325ab5641f5334edbd5e6c8ea29f7c12fb7771ce3-87704cc77acfd41b58b6ee0419abdc02/command-execution-sha256-b3817860e0f1695d5cfcb330133df54df4832d0494e6aef18f22a985912fc426`.
  Validated tree: `b9047075de955babffbbf046765c2e537e006362f5efe68fcef3f5b787385524`.
  Terminal: `sbt_exit=0 wrapper_exit=0 lock=released`.
- **185** `SMWF-P1-REPAIR-LOOP-FOCUSED-20260928-185`: 23/24 tests passed; the negative fixture used a context rejected during JSON encoding before the intended Operation call.
  Agent: `/root/sbt_receipt_ebc8146d0d27842f3ecb`.
  Receipt SHA-256: `10c98607e8baff4accbf6bd8a0b090580c93c04cd0b51287b804314904b14dac`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-af8842886b443b30d47c15658ac01426800c27e80a5bdc40bcafb8d64d2d9819-e78dc5eaf148ebfe98252c01a8819bae/command-execution-sha256-10c98607e8baff4accbf6bd8a0b090580c93c04cd0b51287b804314904b14dac`.
  Validated tree: `a3deac4ec06e7b550b5ee73b9aa979d6dc5d980fd0444b22e1cacc8ab141890c`.
  Terminal: `sbt_exit=1 wrapper_exit=1 lock=released`.
- **186** `SMWF-P1-REPAIR-LOOP-FOCUSED-FIX-20260928-186`: Completed fixture compilation failure: a WorkOrder extractor incorrectly narrowed a bounded request type.
  Agent: `/root/sbt_receipt_36c14f6aeaf803b69e4f`.
  Receipt SHA-256: `221d6815233c408cffa9d8e93d897cf896bb374aebe33c8b227c8141d5dba7ca`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-635eb22de93fbec0b61e6761bb62383ff19fb18bea7970d101ef02d69ea4598a-69b8198a52ac775e258e0443191c6538/command-execution-sha256-221d6815233c408cffa9d8e93d897cf896bb374aebe33c8b227c8141d5dba7ca`.
  Validated tree: `0b60a43851f8b70d4d14ab86964997a24621d0842530f231fa02678241a82395`.
  Terminal: `sbt_exit=1 wrapper_exit=1 lock=released`.
- **187** `SMWF-P1-REPAIR-LOOP-FOCUSED-RETEST-20260928-187`: 24/24 tests passed across five suites, including five new connected loop specifications.
  Agent: `/root/sbt_receipt_a12c6c1e96a1bfd71266`.
  Receipt SHA-256: `c90c388cfc2d320d26cc9f65130438f86b07778fa67434616a72a8bc82a5933a`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-be177e876317520b32737fb42c11fd8ec6e76238f181ba155210e769d53e2823-3eefb9f39a405d3474bc86dbe95f20aa/command-execution-sha256-c90c388cfc2d320d26cc9f65130438f86b07778fa67434616a72a8bc82a5933a`.
  Validated tree: `af3cd023a10725d9cfa27b4c4c80e27bc2f367596ebf3ee8e8661e503cc64ab5`.
  Terminal: `sbt_exit=0 wrapper_exit=0 lock=released`.
- **188** `SMWF-P1-REPAIR-LOOP-FULL-20260928-188`: 447/447 tests passed across 51 suites; zero failed / canceled / ignored / pending or aborted.
  Agent: `/root/sbt_receipt_25801cea36ff0ce837ba`.
  Receipt SHA-256: `63d83480a23144a5303c18839c3bcb771e14fba44b1294c04f088b95b474a177`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-9f09df23019b7cb7dda304aa3de97f4e071284b6bc527e0aa81da822ab0bcff2-3b9dbd054277c5e70aee651ed95ba52a/command-execution-sha256-63d83480a23144a5303c18839c3bcb771e14fba44b1294c04f088b95b474a177`.
  Validated tree: `af3cd023a10725d9cfa27b4c4c80e27bc2f367596ebf3ee8e8661e503cc64ab5`.
  Terminal: `sbt_exit=0 wrapper_exit=0 lock=released`.

Focused-187 and full-188 validate the same application tree,
`af3cd023a10725d9cfa27b4c4c80e27bc2f367596ebf3ee8e8661e503cc64ab5`.
The parent independently checked receipt hashes, actual bound runner IDs,
terminal lock-release markers and saved outputs. Several runner turns exposed
no textual output; verified artifacts supplied the outcome without another
SBT execution. Full-188 ended successfully at 2026-09-28 20:26:29 JST.
No new compile warning was emitted in the final focused / full invocations;
the pre-existing unused cozyCarName setting warning remains.

Denied full-178 remains unstarted. Full-188 is new validation of the genuinely
new connected application tree, not a retry, replacement envelope or claimed
recovery of that denied action.

CAM-05 / CAM-06 are now checked as verified. The checklist records 20 verified,
11 partial, 1 missing and 2 pending-final items out of 34. This does not complete
P1-S08's broader StateMachine guard / authority mapping or GoalPhase public
Start-to-close proof. SplitPhase typed semantic work / Result, typed authority
Decision projection, actual Codex judgment and final handoff remain bounded
work. Next implement SplitPhase's semantic boundary without reimplementing its
objective assessment or document CAS.

Post-validation acceptance updates are English and documentation-only, outside
the full-188 validated tree. All 185 Git-visible product / build paths still
match the receipt's content hashes, with no additional product path. Historical
records and unrelated changes are
preserved. No staging, commit, remote publication, release or Phase closure
occurred. Phase 1 remains `in_progress`.

### SplitPhase frozen semantic-input admission (2026-09-28)

The user authorized continued Phase 1 development with `go ahead`.
This bounded slice adds application-owned semantic input / judgment admission
before connecting SplitPhase to common WorkOrder publication and completion.
No phase, review or commit orchestration skill was used. Only the mandatory SBT
execution skills were used for validation; their registered-runner contract
determined the serialized test execution and immutable receipt records below.

Implemented:

- [SplitPhaseSemanticInputs](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticInputs.scala)
  prepares one frozen request covering both unresolved novel work and unresolved
  split boundaries. It uses existing proposal precheck, duration calibration,
  handoff, validation ownership and source closure / authority checks.
- The context digest binds the source / proposal, deterministic policies,
  calibrated outcomes, closure / authority evidence and occupied Phase IDs.
  Known work estimates remain host-owned. The typed judgment contains
  `enriched` or `decision-needed`, rationale, evidence references, estimate
  ranges / assumptions / novelty basis and boundary assessments, not next state.
- Pure admission checks exact source / proposal / context correlation, exact
  ordered gap coverage, positive bounded ranges, evidence and rationale.
  Missing / duplicate / extra / known-item results and newly invented authority
  are rejected. A decision-needed judgment cannot smuggle enrichment.
- Only unresolved estimates use their admitted range's conservative upper
  endpoint. They receive no fabricated accepted historical observation IDs.
  Known calibration and known boundaries are preserved. Reuse against changed
  draft, calibration inputs, partition policy or edge evidence is rejected.
- Existing proposal validation runs again, including child duration, considered
  partitions and short-child constraints. Semantic admission is not proposal
  acceptance: an admitted larger range can still fail deterministic validation.
  Proposal assessment verifies the resolved boundaries and binds the semantic
  receipt into readiness evidence.

[SplitPhaseSemanticInputsSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticInputsSpec.scala)
adds nine specifications: joint / single-kind gaps, exact enrichment and
assessment, overwrite / authority / range / coverage rejection, foreign
correlation / unsupported decisions, separate decision-needed, changed-input
rejection and deterministic rejection after semantic admission.

This is a pure in-process foundation, not a second generic Workflow protocol.
It creates no Workflow instance, claim, WorkOrder, public result submission,
transition, file write or commit. SplitPhase CML remains
`split-phase-apply-v1`; no generated semantic Action / Required SPI was added.
Common WorkOrder publication, strict public completion, live context checks
before claim consumption and same-instance continuation to PREVIEW still need
connection. The existing copied-document CAS is unchanged.
The decision-needed outcome is not a common authority Decision projection.
No actual Codex judgment dispatch or restart / recovery durability is claimed.

Validation / provenance:

All three invocations used `cncf_command_runner`, model `gpt-5.6-luna`,
reasoning effort `medium`, runtime source `registered-custom-role`, through the
shared serialized SBT wrapper with first-attempt scoped escalation. Each used
its frozen attempt / intake and derived receipt-first task identity.

- **189** `SMWF-P1-SPLIT-SEMANTIC-INPUTS-FOCUSED-20260928-189`: Completed test compilation failure: the new fixture's `pending` name collided with ScalaTest and wildcard imports made `Outcome` ambiguous. Production source compiled; no behavior-test success is claimed.
  Agent: `/root/sbt_receipt_9a129803ee0ec4a97ca3`.
  Receipt SHA-256: `c0700477a28b1b94161c83e48d542bf16adc30d90e39115001eddf14d2e72224`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-f760091580a84b946320256a3330376c1516762b51ae3f44daf3525dd544d3d2-8aad3132c572a8204ab76cec29793dc4/command-execution-sha256-c0700477a28b1b94161c83e48d542bf16adc30d90e39115001eddf14d2e72224`.
  Validated tree: `1b0f400bf37b8fdcd47d5485a02fe5d3453ddd281332798fe2028dc567ac5c44`.
  Terminal: `sbt_exit=1 wrapper_exit=1 lock=released`.
- **190** `SMWF-P1-SPLIT-SEMANTIC-INPUTS-FOCUSED-RETEST-20260928-190`: 69/69 tests passed across four suites, including all nine new semantic-input specifications.
  Agent: `/root/sbt_receipt_7b2349e8aa19d3b3b388`.
  Receipt SHA-256: `7d21190dba048d963be890f0c5f7f338884bb1a86942f6113ad83ace85489a4d`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-69717d754026f6a878ce89e901d77c64e4c7015c90ec61c45c9dc2d85cf869f3-f38adf6dbbe188b96a6345e9a478b7a6/command-execution-sha256-7d21190dba048d963be890f0c5f7f338884bb1a86942f6113ad83ace85489a4d`.
  Validated tree: `57afe9c5144c42aa341d1c2697023d591bec74045ff8b57d5d71f48269ca55e4`.
  Terminal: `sbt_exit=0 wrapper_exit=0 lock=released`.
- **191** `SMWF-P1-SPLIT-SEMANTIC-INPUTS-FULL-20260928-191`: 456/456 tests passed across 52 suites; zero failed / canceled / ignored / pending or aborted.
  Agent: `/root/sbt_receipt_e77a7582c1986b6a9827`.
  Receipt SHA-256: `209b761ef851feed37b7b3c2365ef7df66fc8570829f3b14d63a3b4136629379`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-b85d1cde9bac8604339d520218928dec9855049d9811a2248adacda0cf53324a-dbe32c63874c741c6bfbab37b2b064b6/command-execution-sha256-209b761ef851feed37b7b3c2365ef7df66fc8570829f3b14d63a3b4136629379`.
  Validated tree: `57afe9c5144c42aa341d1c2697023d591bec74045ff8b57d5d71f48269ca55e4`.
  Terminal: `sbt_exit=0 wrapper_exit=0 lock=released`.

Focused-190 and full-191 validate the same application tree,
`57afe9c5144c42aa341d1c2697023d591bec74045ff8b57d5d71f48269ca55e4`.
The parent independently verified receipt hashes, exact bound runner identities,
saved outputs and invocation-scoped terminal lock-release markers.
Full-191 ended successfully at 2026-09-28 20:57:18 JST. The test compilation
defects in 189 were fixed before 190, not hidden or counted as passing evidence.
Denied full-178 remains unstarted; 191 validates genuinely new source, not a
retry or claimed recovery of that action.

CML, checked-in generated ABI fixtures and build coordinates were unchanged;
no CNCF or Cozy source edits were made in this slice. The English documentation
updates follow validation and are outside the validated tree. All 187
Git-visible product / build paths match full-191's content hashes, with no
additional or missing product path.

The evidence map remains 20 verified, 11 partial, 1 missing and 2 pending-final
out of 34: this foundation advances partial SplitPhase items but does not
complete their public / same-instance contracts. The next connected slice is
generated semantic Action / Required SPI, common WorkOrder publication and typed
public completion using this admission gate, then the existing PREVIEW path.
Unrelated work and historical records are preserved. No staging, commit,
release or Phase closure occurred. Phase 1 remains `in_progress`.

### SplitPhase semantic WorkOrder / public completion connection — validation pending (2026-09-28)

The user authorized continued Phase 1 development with `go ahead`. This slice
connects the preceding frozen semantic-input foundation to common CNCF work
publication and public completion. No phase, review or commit orchestration
skill was used. Only the mandatory SBT execution skills were used; their
registered-runner contract determined serialized execution and receipt evidence.

Implemented, but not yet compiled or behavior-validated:

- SplitPhase CML revision `split-phase-semantic-preview-v2` declares
  `AssessNovelSplitWork` as a JudgmentAction, the genuine
  `AdmitSplitSemanticInputs` admission Action, the
  `assess-novel-split-capability` Required SPI and typed public
  `SplitPhaseService.submitSplitSemanticResult` completion. The second
  constituent retains the typed semantic request rather than changing the
  existing Start payload into a semantic request.
- [SplitPhaseDefinitionBinding](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseDefinitionBinding.scala)
  admits the actual generated Workflow / Provided API / producer descriptors.
  It does not inject missing semantic Actions into actionless entry metadata.
- [SplitPhaseSemanticWorkflow](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticWorkflow.scala)
  retains the frozen intent, saves the matching suspension and uses common
  issued WorkOrder persistence and the declared public completion Operation.
  Live source, proposal, policies, occupied IDs and authority context are
  rechecked before typed semantic admission and common one-shot claim
  consumption. Strict application payload codecs remain inside CNCF's existing
  common WorkOrder / Result envelopes.
- Admitted enrichment is designed to rerun existing deterministic proposal
  checks, construct a host-owned objective catalog, and automatically return
  read-only PREVIEW on the same instance: Start revision 1, semantic suspension
  2, admitted Result 3, Completed PREVIEW 4. A public preview command cannot
  provide the private host-only admitted record.
- `decision-needed` is designed to remain nonterminal WAIT, with no catalog
  selection or new authority. It is not a common typed authority Decision.
  The semantic connection is PREVIEW-only; deterministic APPLY / document CAS
  remains unchanged.
- [SplitPhaseSemanticWorkflowSpec](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticWorkflowSpec.scala)
  adds nine intended specifications using the actual source document/catalog:
  Service / CLI same-instance PREVIEW; single-kind gaps; rejected coverage,
  ranges, authority and context before claims; stale headers / state-selector
  JSON; live host drift; nonterminal decision-needed; publication failures;
  separate loose completion / catalog / terminal-append failures; and rejection
  of public revision-three preview without an admitted host record.
  These are unexecuted specifications, not passing evidence. A static correction
  after the denial moves the wrong-result-type rejection to raw wire injection,
  because the common encoder correctly refuses to encode a mismatched type.

Validation / execution stop:

All three runners used `cncf_command_runner`, model `gpt-5.6-luna`, reasoning
effort `medium`, runtime source `registered-custom-role`, with receipt-first
frozen attempts and scoped escalation. No independent concurrent SBT ran.

- **192** `SMWF-P1-SPLIT-SEMANTIC-PRODUCER-20260928-192` failed actual
  CML normalization: `entry-pending` did not map the new `semantic` role.
  This application CML error was fixed by declaring every constituent in all
  derivations and the initial mapping, following existing RepositorySync syntax.
  It is not an upstream ABI deficiency.
  Agent: `/root/sbt_receipt_af66d9d0160d601555bd`.
  Receipt SHA-256: `40c236c84183d72352a180e6d681476011cb2d9407a4cfd51cc6587d73a75591`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-aa5ab05c0594c6e470aa6bd44df5895992a2a3401c2253a4a0bb5ccfc39a20b1-ea7a7b51fd92be996b939a0aa29b9a74/command-execution-sha256-40c236c84183d72352a180e6d681476011cb2d9407a4cfd51cc6587d73a75591`.
  Terminal: `sbt_exit=1 wrapper_exit=1 lock=released`.
- **193** `SMWF-P1-SPLIT-SEMANTIC-PRODUCER-RETEST-20260928-193` passed
  actual Cozy CML normalization and canonical GoalPhase JSON export.
  Agent: `/root/sbt_receipt_289ea93af0e377099e25`.
  Receipt SHA-256: `2d16ae2756507d409fc923d10e6bc3ffc8b5a4bde4944b1fbd5beb07b7a7c111`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-68d2f3c352290de8c7a5168236ffef1a6286664973ca0251e2cf8a6eeb07874b-319b153f22a3df3b07d4c96b5f233836/command-execution-sha256-2d16ae2756507d409fc923d10e6bc3ffc8b5a4bde4944b1fbd5beb07b7a7c111`.
  Selected Cozy tree: `401ea2ed32ec09ca0bd3d7ef7c87391657e73e09864bd388e343278a13a2afce`,
  observed HEAD `12a0e6d79321f146592bd527301d967fba774c94`.
  The probe uses only the app-owned probe as the invocation's unmanaged Test
  source, preserving unrelated Cozy changes. This producer result does not
  compile or test the new Scala 3 application code.
  Terminal: `sbt_exit=0 wrapper_exit=0 lock=released`.
- **194** `SMWF-P1-SPLIT-SEMANTIC-CONNECTED-FOCUSED-20260928-194` stopped
  at `EXECUTION_PERMISSION_DENIED_AFTER_ATTRIBUTABLE_AUTHORITY`.
  Agent: `/root/sbt_receipt_451542163df6207532f9`.
  Frozen intake: `/tmp/skill.cncf.d/cncf-command-execution-e3c31cd95850a7fa383dc5aa167ea9d43875647645e50f4cd54e67ee34efc827-7c3689a01fbf892045803f5b41b189c9/command-execution-intake-sha256-dc7dff37ed3ea9d29d79a38bdcec2f671a4c09967bfc8eb139a67691189b6548.json`.
  Platform approval review rejected the required receipt-first preflight before
  execution, citing an untrusted intake locator / escalated rendered-request
  unknown-scope risk and insufficient specificity in `go ahead`.
  No helper / wrapper / SBT start, application compile result, behavior result,
  execution receipt or terminal lock marker exists for this attempt.
  It was not retried through another runner, invocation or direct command.
  No full test was attempted. Further execution requires explicit informed
  approval or a genuinely materially safer permitted alternative.

The intended focused command selects exactly
`SplitPhaseSemanticWorkflowSpec`, `SplitPhaseSemanticInputsSpec`,
`SplitPhaseStartExecutionSpec`, `SplitPhaseGeneratedAbiSpec` and
`GoalPhaseCncfConsumerSpec` under the registered runner / shared wrapper.
It needs ordinary SBT / Ivy / Coursier / Maven cache access and may generate
managed sources / build outputs or resolve dependencies. It authorizes no
source edits, staging, commit, push, release, credential changes or cleanup.

Current CML SHA-256:
`2696f0d2c99e7fa1da31645beaa31b676c120c2e2004bc1bd79209dc4d713f07`.
The actual 193 canonical GoalPhase outputs match the existing checked-in
fixtures exactly, including their one trailing newline:
Workflow `86128694abef582670b80eddb454f8bca599119a4b525cdc80aa99afbf7edbc9`;
producer `db4a5d128a54d10957e6e659ed0c616734b24320a3a5b43de7f50ff24e243a64`.
The consumer spec pins the new CML hash without weakening fixture checks.

Full-191 (456/456, 52 suites) is the last full-suite success and validates only
the preceding foundation tree, not this new implementation. Static
`git diff --check` passes, but it is not compilation or behavior validation.
No CNCF or Cozy source edits were made by this slice. English records and
unrelated changes are preserved; no staging, commit, release or Phase closure
occurred. The evidence map remains 20 verified, 11 partial, 1 missing and
2 pending-final. Phase 1 remains `in_progress`.

### SplitPhase connected PREVIEW — focused validation passed (2026-09-28)

After the previous platform denial, the user explicitly permitted the five
focused suites with `許可する` in direct native turn
`01a0e811-41d8-7353-9cd8-6b922a3df52b`. The selected scope is unchanged:
`SplitPhaseSemanticWorkflowSpec`, `SplitPhaseSemanticInputsSpec`,
`SplitPhaseStartExecutionSpec`, `SplitPhaseGeneratedAbiSpec` and
`GoalPhaseCncfConsumerSpec` in sm-workflow, with ordinary SBT caches / generated
build outputs. No staging, commit or push was requested or performed.

The existing originating workflow anchor was retained; its original occurrence
was not rebound to the new permission message. The actual history suffix now
contains the informed permission. A fresh frozen invocation 195 included
denied 194 in the consumed-ID ledger and was accepted by platform review.
The denied 194 intake was never rendered / executed again. This is the new
explicitly permitted validation, not a fabricated success or an execution of
194 through another route. No phase, review or commit orchestration skill was
used. Only the mandatory SBT execution skills determined runner, serialization,
scoped cache access and immutable receipt evidence.

Concrete repairs:

- Invocation 195 stopped in actual `cozyGenerate`: public completion input
  `SplitPhaseSemanticResult` lacked `input-kind = COMMAND`. Both semantic
  request and result now declare the COMMAND input kind.
- Invocation 196 generated 65 Scala sources but failed compilation because
  `assessNovelSplitWork` and `admitSemanticInputs` shared the same command
  input type inside one generated Service namespace, producing duplicate
  `SplitPhaseSemanticRequest` wrappers.
- Application CML separates contextual judgment in `SplitSemanticService`
  from deterministic admission in `SplitSemanticAdmissionService`, preserving
  the same typed semantic request and genuine Judgment / Admission correlation.
  No generated output was hand-edited, and no Cozy or CNCF source change was
  needed. The CML hash pin was updated after each source repair; canonical
  GoalPhase fixture bytes remain unchanged.
- Invocation 197 regenerated 65 Scala sources and compiled the connected
  application and test sources successfully. All five selected suites passed:
  65/65 tests; zero failed / canceled / ignored / pending or aborted.
  The nine new connected specifications all passed.

The connected proof covers Service and CNCF CLI public Start, one joint
semantic WorkOrder, retry of the same pending assessment without another
suspension, strict typed public completion, existing deterministic reassessment
and automatic read-only PREVIEW on the same instance through revisions 1–4.
Known estimates / authority remain unchanged. Invalid coverage / estimate
ranges / new authority / foreign context / stale headers / payload type /
nested state selectors and current host-policy / source / proposal / occupied-ID
drift are rejected before claim consumption. The original valid result can
still complete afterward. Replay cannot produce another catalog / terminal.
Source document bytes remain unchanged.

Edge-only and novel-only gaps also reach PREVIEW. `decision-needed` consumes
its admitted completion into Active revision 3 and nonterminal WAIT without
selecting a catalog or granting authority. It is not a typed authority Decision.
Intent / suspension publication failures produce no WorkOrder; separate
resume-append, catalog and terminal-append failures produce no Completed state.
Public revision-three preview cannot provide the private host-only admitted
record. Fixture stores are process-memory and the existing loose transaction
contract remains non-atomic. The semantic connection is PREVIEW-only;
deterministic APPLY and copied-document CAS were regression-tested unchanged.

All runners used `cncf_command_runner`, model `gpt-5.6-luna`, reasoning
effort `medium`, runtime source `registered-custom-role`, healthy registered
route, shared serialized wrapper and first-attempt scoped escalation:

- **195** `SMWF-P1-SPLIT-SEMANTIC-CONNECTED-FOCUSED-AUTHORIZED-20260928-195`: Actual generation failed before application compilation: missing semantic input-kind.
  Agent: `/root/sbt_receipt_aa15de43702d0f2e32e3`.
  Receipt SHA-256: `d0b721c1d99daa8758eac537a2ccae20f6da745bc4fc1fe6ba18ab5d64da18a2`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-7ab5dcf509eb009c057522deaea98c83c6167e030ca6f314fed1951e992a7eff-70a32c395f0125d4a6a1ca571b667b91/command-execution-sha256-d0b721c1d99daa8758eac537a2ccae20f6da745bc4fc1fe6ba18ab5d64da18a2`.
  Validated tree: `b9cd2189f04cfd9b9e626c5e38c38a4242aa6e3be13a09188adb0cb8fa69a0e4`.
  Terminal: `[cncf-sbt] completed: invocation=SMWF-P1-SPLIT-SEMANTIC-CONNECTED-FOCUSED-AUTHORIZED-20260928-195 sbt_exit=1 wrapper_exit=1 lock=released log=/tmp/skill.cncf.d/cncf-sbt-1764f3a307bba079ea5e813209f7d38937a30ae3ee29bbe6ed50d03a62780416-95160eb227d44c12b66c3ff8da7c42dd/53992-20260928T125241Z.log summary=/tmp/skill.cncf.d/cncf-sbt-1764f3a307bba079ea5e813209f7d38937a30ae3ee29bbe6ed50d03a62780416-95160eb227d44c12b66c3ff8da7c42dd/53992-20260928T125241Z.summary.json`.
- **196** `SMWF-P1-SPLIT-SEMANTIC-CONNECTED-FOCUSED-RETEST-20260928-196`: Generation succeeded; application compilation failed on duplicate generated Command wrappers.
  Agent: `/root/sbt_receipt_1bb8d6f22d4fa5648b2e`.
  Receipt SHA-256: `cd72d768d6befc8b835b429ccc690cd4cbc579bcb136ababc9b17391c2bbd9a8`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-d5f05b3323f66e00477296a291931c38d22b18c23df10095a0c08a5b29ef54f2-2337e744c4ec2c277bf2cb6c798b9dc7/command-execution-sha256-cd72d768d6befc8b835b429ccc690cd4cbc579bcb136ababc9b17391c2bbd9a8`.
  Validated tree: `79fd633f34af93774bf9573b16262498ea41110643edf1484bbc345a3c1b5c40`.
  Terminal: `[cncf-sbt] completed: invocation=SMWF-P1-SPLIT-SEMANTIC-CONNECTED-FOCUSED-RETEST-20260928-196 sbt_exit=1 wrapper_exit=1 lock=released log=/tmp/skill.cncf.d/cncf-sbt-5a3dd6d7687f0c9fb1a8100fa9ceed9602cc621c4a2ac47c7a547acc1781eaf1-fbf4b280adbe9b1c3dddea8eb9f0b333/55359-20260928T125438Z.log summary=/tmp/skill.cncf.d/cncf-sbt-5a3dd6d7687f0c9fb1a8100fa9ceed9602cc621c4a2ac47c7a547acc1781eaf1-fbf4b280adbe9b1c3dddea8eb9f0b333/55359-20260928T125438Z.summary.json`.
- **197** `SMWF-P1-SPLIT-SEMANTIC-CONNECTED-FOCUSED-RETEST-20260928-197`: 65/65 tests passed across five suites; generated and compiled sources successfully.
  Agent: `/root/sbt_receipt_22a33e82a8b69eaab792`.
  Receipt SHA-256: `7813dd9e5dfc15d19c7c4e5433ea9b8818cd38cf2bcb8266165ca136aa4ff159`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-28ebe5ccc3ffc177b50ed24fe1a80685f195435a236968ad207dbb257b4847be-5ff69d56369922cbe0abdd9e7f1be7ec/command-execution-sha256-7813dd9e5dfc15d19c7c4e5433ea9b8818cd38cf2bcb8266165ca136aa4ff159`.
  Validated tree: `75e915f552ff52eb326a543b1536ec4be14213e01e381d9ca632e11a04ab78c6`.
  Terminal: `[cncf-sbt] completed: invocation=SMWF-P1-SPLIT-SEMANTIC-CONNECTED-FOCUSED-RETEST-20260928-197 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-aeb689651b2d84ed69bac5c064329aa244e3d500f0d3a0af912d5131d697acc8-2a85214fd8ddcc6cdd69d8d33f3acbbd/56831-20260928T125732Z.log summary=/tmp/skill.cncf.d/cncf-sbt-aeb689651b2d84ed69bac5c064329aa244e3d500f0d3a0af912d5131d697acc8-2a85214fd8ddcc6cdd69d8d33f3acbbd/56831-20260928T125732Z.summary.json`.

The 196 and 197 runners returned no textual receipt result while their
already-started helpers completed. The parent waited and recovered the immutable
receipts from the original intake-owned directories, independently verified
their hashes, exact bound agent IDs, saved output and invocation-scoped
`lock=released` markers. No extra SBT was launched to recover result display.
A read-only process check confirmed the original execution; no process was
killed or lock bypassed. Focused-197's SBT success was recorded at
2026-09-28 21:58:59 JST.

Current CML SHA-256:
`1fa7fbd3b2115980db5c1922c21150ce36e5a297a40e54139d193fc968f3c55b`.
GoalPhase fixture SHA-256 values remain:
Workflow `86128694abef582670b80eddb454f8bca599119a4b525cdc80aa99afbf7edbc9`;
producer `db4a5d128a54d10957e6e659ed0c616734b24320a3a5b43de7f50ff24e243a64`.
The exact source / fixture hash consumer test passed in 197.

All 190 Git-visible product / build paths match focused-197's content hashes,
with no additional, missing or changed product path. English documentation
updates follow validation and are outside its complete validated tree.
Full-191 remains the preceding foundation-only full test (456/456, 52 suites);
no new full test ran in this explicitly five-suite scope.

The former absent SplitPhase typed payload / generated judgment / public-result
boundary is now proved. Together with existing GoalPhase / RepositorySync
evidence, this completes P1-A01 / A06 / A07 without redefining their acceptance.
The map and sole checklist now agree: 23 verified, 8 partial, 1 missing and
2 pending-final out of 34. GoalPhase public Start-to-close composition, common
authority Decision, actual Codex judgment / deterministic replacement proof
and final intended-tree full validation / handoff remain open.
Unrelated work is preserved. No staging, commit, release, remote publication
or Phase closure occurred. Phase 1 remains `in_progress`.

### SplitPhase connected PREVIEW — full regression validation passed (2026-09-28)

The user requested continued development with `go ahead` in native parent turn
`01a0e820-fc01-7e63-9e7d-529fe295dc0c`, after focused-197 proved the connected
SplitPhase slice. This invocation selected full regression validation of that
settled product tree. It did not retry denied 194 or denied full-178; both remain
unstarted. The existing original authority anchor was retained, and its actual
consecutive parent history was included in the runner dispatch.

Full validation:

- Invocation: `SMWF-P1-SPLIT-SEMANTIC-CONNECTED-FULL-20260928-198`.
- Repository: `/Users/asami/src/dev2026/sm-workflow`.
- Logical argv: `["--batch", "test"]`.
- Model / effort: `gpt-5.6-luna / medium`.
- Runtime source / route: `registered-custom-role`.
- Agent type: `cncf_command_runner`.
- Actual bound agent: `/root/sbt_receipt_cf3ea7d22fd7d351b077`.
- Receipt SHA-256: `f87d433841e3183489ee2aa45e4d2767d2570df488fcc08e1aa9dd578f619c33`.
- Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-382b24036bf6ec000b55b8a74ccf393a49499f5191946743c9117316fea5cb65-c2921d804f7c09282d5f58134f762057/command-execution-sha256-f87d433841e3183489ee2aa45e4d2767d2570df488fcc08e1aa9dd578f619c33`.
- Validated tree SHA-256: `bab6e6e7d5c1961894809ec952884bcb73a408c4b0405ac476d15a0e07a679cf`.
- Result: **465/465 tests passed across 53 suites**, with zero failed /
  canceled / ignored / pending tests and zero aborted suites.
- SBT / wrapper exit: `0 / 0`; verified lock state: `released`.
- Exact terminal: `[cncf-sbt] completed: invocation=SMWF-P1-SPLIT-SEMANTIC-CONNECTED-FULL-20260928-198 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-7e028a27dd00290a1bac57e05a4bf6c9ec5591b02aa815a21c4879b7d70c37c0-713167f76be1c4e740cccc2741cd3e86/64100-20260928T131404Z.log summary=/tmp/skill.cncf.d/cncf-sbt-7e028a27dd00290a1bac57e05a4bf6c9ec5591b02aa815a21c4879b7d70c37c0-713167f76be1c4e740cccc2741cd3e86/64100-20260928T131404Z.summary.json`.

SBT recorded success at 2026-09-28 22:14:28 JST. The generator reused the
65 managed Scala sources generated by focused-197 because CML timestamps were
unchanged; this full invocation did not regenerate them. Compilation and the
full suite succeeded, including the nine connected SplitPhase semantic workflow
specifications, the actual generated ABI consumers, GoalPhase repair-loop
regressions and RepositorySync specifications.

The parent independently verified the immutable receipt digest, exact bound
agent identity, successful exit and invocation-scoped terminal marker before
editing documentation. All 190 Git-visible product / build paths match the
receipt's content hashes, with no additional, missing or changed product path.
The same audit passed after the English documentation update. Those later
documentation bytes are outside the complete validated Git tree; only the
unchanged product / build coverage is carried forward.

Current CML SHA-256 remains
`1fa7fbd3b2115980db5c1922c21150ce36e5a297a40e54139d193fc968f3c55b`.
The checked-in GoalPhase Workflow / producer fixtures are unchanged, and their
exact source-correlation consumer tests passed. No application or upstream
source repair was needed in this full-test slice.

The evidence map and sole checklist retain **23 verified, 8 partial, 1 missing
and 2 pending-final out of 34**. Full-198 is current regression evidence, not
P1-C01's final intended-tree validation after the remaining implementation.
GoalPhase public Start-to-close composition, common typed authority Decision,
actual Codex judgment / deterministic replacement proof and the final
three-profile handoff remain open. The next development boundary is GoalPhase
public Start-to-close composition, followed by the other recorded open items.

No staging, commit, push, release or Phase closure occurred. Other-owned work
was preserved. Phase 1 remains `in_progress`.

### GoalPhase public Start-to-close — connected and full-tested (2026-09-28)

The user requested continued development with `go ahead` in native parent turn
`01a0e829-e6c5-7fe0-a8b0-80f6e3d428d5`. The parent implemented only the selected
GoalPhase same-instance composition gap, without GoalPhase / review / commit
orchestration skills. Mandatory SBT execution routing retained the original
authority anchor and actual consecutive parent history.

`GoalPhaseFixtureServiceAssembly` binds the four actual generated Provided
Operations — Start, requestStepClose, submitStepReview and submitStepRepair —
to one explicitly supplied set of host stores and ports. It rejects missing
ports, foreign Workflow binding and incompatible Required SPI. Production
ComponentFactory registration, CML and upstream source are unchanged.

Nine new executable specifications in `GoalPhaseRepairLoopSpec` use the same
existing public Operations with an initially empty instance store. The actual
Start Operation alone creates the instance and appends Active revision 1.
The host explicitly associates a close submission with that returned Handle;
public input cannot manufacture that association.

The specifications prove:

- Service and CLI Start → review WorkOrder → typed accepted Result → exact
  fixture commit → Completed through revisions 1–4, preserving Start history
  and identity, with one instance create, one close request and one commit.
- Changed / unchanged Repair → fresh full re-review → Completed through
  revisions 1–8 on that same Start instance, without another close request.
- Proactive full review closes through revisions 1–2 without a semantic
  WorkOrder.
- Human selection and exact host submission association are mandatory; foreign
  Handles, state selectors and invalid Results are rejected before consuming
  the claim.
- Failed initial Active append can resume the same initial record. Failed
  review suspension publishes no WorkOrder. Artifact drift admits no commit.
  Failed terminal append after a successful fixture commit returns failure,
  not Completed, and replay cannot create a second commit.

The commit port is simulated retained fixture evidence, not actual repository
Git execution. These loosely coordinated persistence operations do not claim a
shared transaction or production restart / recovery semantics. The SBT runner
is not an application Codex JudgmentAction worker.

Validation used `gpt-5.6-luna / medium`, runtime source / route
`registered-custom-role`, agent type `cncf_command_runner`, throughout:

| Attempt | Result | Actual bound agent | Receipt SHA-256 |
| --- | --- | --- | --- |
| 199 | Compile failure: missing common-protocol imports | `/root/sbt_receipt_03775e75cc9a972f2902` | `d00c21bf8512193b3fde6da43a43865f02e8a7dbd2818e9a0a942b7b2d7ae0af` |
| 200 | Test compile failure: immutable binding has no copy | `/root/sbt_receipt_61a4bccab2e778e90550` | `141de8885351749141c51a80eccd5e688788773effae408a8a113f1c2d6ac150` |
| 201 | 31/31, five suites | `/root/sbt_receipt_0ce69914a32d340b2fec` | `f01a75e3632ad225896dfd1529feeaaeba1bd216f78d3059fc8a9c43d345d95f` |
| 202 | 474/474, 53 suites | `/root/sbt_receipt_c233eed4592aaa518605` | `6e2d00508ca4bea25d2e39eaa8a93fc99b4f6356421c1c2c276fb437e84c8dc7` |

199 failed on missing imports for CNCF common `ExecutionRequirement` /
`MinimalPresentation`, repaired in the new assembly. 200 compiled the assembly
but failed because a negative test called `copy` on immutable
`DefinitionBinding`. That test now uses the actual generated SplitPhase
binding to prove rejection of a foreign Workflow. No upstream ABI shortage
was found.

201 passed all nine new specifications and existing GoalPhase suites. Its
runner returned no textual execution result; the parent recovered and verified
the immutable receipt from its original intake-owned directory, without
launching another SBT to recover display. Full-202 was separately selected full
regression validation after that verified success, not a result-display retry.
It passed **474/474 tests across 53 suites**, with zero failed / canceled /
ignored / pending tests and zero aborted suites, at 2026-09-28 22:31:05 JST.

Exact receipt / terminal evidence:

- **199** `SMWF-P1-GOAL-PUBLIC-START-CLOSE-FOCUSED-20260928-199`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-3a773751db2983d2c2e33bc304d8cd88b6ff1afb06539979edd5a173e828d4e3-929cc2a41e068cbe227e3278723aeba6/command-execution-sha256-d00c21bf8512193b3fde6da43a43865f02e8a7dbd2818e9a0a942b7b2d7ae0af`.
  Validated tree: `dc56604ea0e64b1265bee8060f18087c14ded00951ef57b5c56003c37bb3c6a7`.
  Exact terminal: `[cncf-sbt] completed: invocation=SMWF-P1-GOAL-PUBLIC-START-CLOSE-FOCUSED-20260928-199 sbt_exit=1 wrapper_exit=1 lock=released log=/tmp/skill.cncf.d/cncf-sbt-d0744878dd1ac4149dbbbc815c4f2da7e573a8dd639a05c2b017a4a6abba87bf-a46f0ff53471397df146cf4f721e3655/69656-20260928T132535Z.log summary=/tmp/skill.cncf.d/cncf-sbt-d0744878dd1ac4149dbbbc815c4f2da7e573a8dd639a05c2b017a4a6abba87bf-a46f0ff53471397df146cf4f721e3655/69656-20260928T132535Z.summary.json`.

- **200** `SMWF-P1-GOAL-PUBLIC-START-CLOSE-FOCUSED-RETEST-20260928-200`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-ebf8202a2c3e1fd0e8f6020b068c07e67ee041923537e8cf3de02cec6514d840-b875177a3c27f5fb1f4119e9fac6d4b7/command-execution-sha256-141de8885351749141c51a80eccd5e688788773effae408a8a113f1c2d6ac150`.
  Validated tree: `7932aedd1923fa182c9bd59629cde4d5e86de2c7a37c23065a1e05bfc5ff21a4`.
  Exact terminal: `[cncf-sbt] completed: invocation=SMWF-P1-GOAL-PUBLIC-START-CLOSE-FOCUSED-RETEST-20260928-200 sbt_exit=1 wrapper_exit=1 lock=released log=/tmp/skill.cncf.d/cncf-sbt-c84f93fe9e3aec5899bc601cbb2d02634a4f363a76d905713d15a65e2f45510e-67d6ccac79d8aae682e2c5ff9a9379a9/70414-20260928T132656Z.log summary=/tmp/skill.cncf.d/cncf-sbt-c84f93fe9e3aec5899bc601cbb2d02634a4f363a76d905713d15a65e2f45510e-67d6ccac79d8aae682e2c5ff9a9379a9/70414-20260928T132656Z.summary.json`.

- **201** `SMWF-P1-GOAL-PUBLIC-START-CLOSE-FOCUSED-RETEST-20260928-201`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-4fd430296f2129a52ce261d22c6c8c2b425cd568ef460ef2cbc2ff2d6dad0ebd-775a201f36e0951fb22d048f4fce66b4/command-execution-sha256-f01a75e3632ad225896dfd1529feeaaeba1bd216f78d3059fc8a9c43d345d95f`.
  Validated tree: `4360b200f103b342edffeef8855e2ca1a66bc263ae91c96c46de0e0b5d3adebb`.
  Exact terminal: `[cncf-sbt] completed: invocation=SMWF-P1-GOAL-PUBLIC-START-CLOSE-FOCUSED-RETEST-20260928-201 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-f47930248cf961f0591caec5188aebce40703c1e5bb5522f4b3a7e717050f4cd-09815ceea33fb3b67a8edfe52a6167a0/72662-20260928T132916Z.log summary=/tmp/skill.cncf.d/cncf-sbt-f47930248cf961f0591caec5188aebce40703c1e5bb5522f4b3a7e717050f4cd-09815ceea33fb3b67a8edfe52a6167a0/72662-20260928T132916Z.summary.json`.

- **202** `SMWF-P1-GOAL-PUBLIC-START-CLOSE-FULL-20260928-202`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-23c2dad369ce0ebfa34b71422f366924be2da18e144994b4cdb7d05daa625d81-f305e9fe68ff813e8ca12848d818e4a4/command-execution-sha256-6e2d00508ca4bea25d2e39eaa8a93fc99b4f6356421c1c2c276fb437e84c8dc7`.
  Validated tree: `4360b200f103b342edffeef8855e2ca1a66bc263ae91c96c46de0e0b5d3adebb`.
  Exact terminal: `[cncf-sbt] completed: invocation=SMWF-P1-GOAL-PUBLIC-START-CLOSE-FULL-20260928-202 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-0d2dd16ebc010b9d233a54774066a49a09621016a1cbfbe1d9fc700233eedf45-4aa478a8a004b2900bd48d1a9b5bc5c1/73470-20260928T133040Z.log summary=/tmp/skill.cncf.d/cncf-sbt-0d2dd16ebc010b9d233a54774066a49a09621016a1cbfbe1d9fc700233eedf45-4aa478a8a004b2900bd48d1a9b5bc5c1/73470-20260928T133040Z.summary.json`.

Focused-201 and full-202 validate the same tree:
`4360b200f103b342edffeef8855e2ca1a66bc263ae91c96c46de0e0b5d3adebb`.
All terminal markers verify invocation identity, SBT / wrapper exits and
`lock=released`; no invocation overlapped another top-level SBT command.

All 191 Git-visible product / build paths match full-202 before and after the
English documentation update, without additional, missing or changed product
paths. Later documentation bytes are outside that complete validated tree.
CML SHA-256 remains
`1fa7fbd3b2115980db5c1922c21150ce36e5a297a40e54139d193fc968f3c55b`;
the exact GoalPhase source / fixture consumer tests passed.

Together with the existing SplitPhase semantic PREVIEW and RepositorySync
reviewed convergence evidence, this completes **P1-S01** without redefining its
acceptance. The map and sole checklist now agree: **24 verified, 7 partial,
1 missing and 2 pending-final out of 34**. P1-A03 still lacks authority Decision
fixtures. Common typed authority Decision is the next development boundary;
actual Codex judgment / deterministic replacement and final intended-tree
validation / three-profile handoff also remain open.

No staging, repository commit, push, release or Phase closure occurred.
Other-owned work is preserved. Phase 1 remains `in_progress`.

### SplitPhase identity Decision — public stop projection and full validation (2026-09-28)

The user continued development with `go ahead` in native parent turn
`01a0e848-7989-77a2-abed-562aa9dc1e39`. The parent selected the existing
identity/numbering authority gap, without GoalPhase / review / commit
orchestration skills. Only mandatory SBT execution routing skills were used:
`cncf-command-execution`, `cncf-agent-execution` and
`cncf-sbt-serial-execution`. They preserve the original authority anchor and
the verified 56-turn parent suffix rather than creating new authority.

`SplitPhaseIdentityDecision` adds a strict application input codec and an
explicit host projection port. The existing generated
`SplitPhaseService.assessSplitProposal` can opt into CNCF
`WorkflowDecisionJsonV1` for two already assessed reasons:
`child-phase-identity-collision` and `nested-or-unknown-phase-numbering`.
It preserves the public Start Handle, Active revision 1 and source/proposal
evidence; it does not append, suspend, claim, resume, grant authority, choose
a partition or invoke PREVIEW/APPLY.

The host supplies the entire common ContinuationRequest for that exact
frozen intent, including stable run/continuation and its human-boundary
operation identities. They are stop-only metadata, **not generated Provided
API, callable endpoint registration or Required SPI**. Result type is absent;
no human-result submission or execution engine is claimed. The port cannot
reuse the generated semantic Required SPI, semantic operation or SplitPhase
completion service. Source/proposal/context/revision drift is rejected, and
the stopped durable instance is reloaded after host projection before the
Decision wire is returned. The ordinary common encoder validates the
remaining wire identities and contract shape.

Eight new specifications in `SplitPhaseStartExecutionSpec` prove:

- Public Start → assessment through Service and CLI returns the same typed
  Decision, with stable retries, one create and one initial Active append.
- Occupied child identity and nested numbering stop without granting authority.
- Source-boundary gaps, unresolved semantic estimates and deterministic
  readiness do not become human Decisions or call the human projection port.
- Changed host payload/context/revision, reused semantic SPI/completion,
  missing source/proposal references, result type (including null) and malformed
  continuation/completion identities fail closed without progression.
- An explicitly enabled but unavailable host boundary returns failure; the
  explicitly unconfigured legacy fixture retains its existing WAIT behavior.
- Stale selectors/source drift reject before host lookup; a concurrent instance
  change after lookup rejects without returning the stale Decision.
- The typed application input codec rejects unknown approval/selector fields,
  missing fields, unsupported reasons, malformed digests and null JSON.
- Decision cannot decode as WorkOrder/WAIT or accept a fabricated approval
  through common WorkOrder Result admission. The fixture's human completion
  metadata identity is deliberately not callable.

This is an opt-in identity Decision projection, not an all-profile runtime.
The optional human projection and semantic Workflow binding cannot yet be
combined; that combination fails binding rather than silently skipping either
path. Split semantic `decision-needed` remains WAIT. Equal-objective authority
tie-break, GoalPhase closure authority, RepositorySync authority connections and
actual application Codex JudgmentAction execution remain distinct open work.
A future human submission boundary must not be routed through WorkOrder Result
or treated as an automatic approval. No production / transaction / restart
hardening gate is added.

Validation used `gpt-5.6-luna / medium`, runtime source / route
`registered-custom-role`, agent type `cncf_command_runner`, throughout:

| Attempt | Result | Actual bound agent |
| --- | --- | --- |
| 203 | Test compilation failed; main source compiled | `/root/sbt_receipt_eaabd5f31ab21551a1b6` |
| 204 | 68/68, three suites, all eight new specifications | `/root/sbt_receipt_c7e512fbba38a59be8b5` |
| 205 | 482/482, 53 suites | `/root/sbt_receipt_40ad8014119e67d1b9ef` |

203 exposed new test function-type syntax and ambiguous ContinuationIdentity
imports. The tests now parenthesize the function type and use an explicit
runtime identity alias. A null result-type rejection was also added before
204. No upstream ABI shortage was observed.

204's runner reported lost visible execution output and a rejected subsequent
read-only preflight because the intake had already started. 205's runner
returned an indeterminate display result without its completion output.
For both, the parent recovered the **original intake-owned** execution receipt,
verified its digest, exact bound runner, immutable saved output and matching
invocation-scoped terminal marker, and read the SBT summary. Those original
receipts independently prove successful terminal execution. No SBT was rerun
to recover display, and no intake or permission artifact was reused. Full-205
was selected only after focused-204's terminal success was verified.

Exact immutable receipt / terminal evidence:

- **SMWF-P1-SPLIT-IDENTITY-DECISION-FOCUSED-20260928-203**
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-f3a4a191b06c21e8d03ab708289f08c0dcda35f03b11f8f560b4cfb628722ed9-197d7deafd4a66271bab39778a9881b0/command-execution-sha256-4e89acc5be412bf48d7c87276f04dfbcfe5e277402e62eff558d1c9467e94521`.
  Receipt SHA-256: `4e89acc5be412bf48d7c87276f04dfbcfe5e277402e62eff558d1c9467e94521`.
  Validated tree: `209f75b48e812bb69416ec01ed2c69d062abcdd026dc4c187d2be3720b0d0f7c`.
  Exact terminal: `[cncf-sbt] completed: invocation=SMWF-P1-SPLIT-IDENTITY-DECISION-FOCUSED-20260928-203 sbt_exit=1 wrapper_exit=1 lock=released log=/tmp/skill.cncf.d/cncf-sbt-f6a0f8c11f1d4ceae5f0b07947436326599ac0af8f33dcb51e51bfdc121bdc70-d61bc3f062f4708f7e3281b5f84cb766/89726-20260928T140901Z.log summary=/tmp/skill.cncf.d/cncf-sbt-f6a0f8c11f1d4ceae5f0b07947436326599ac0af8f33dcb51e51bfdc121bdc70-d61bc3f062f4708f7e3281b5f84cb766/89726-20260928T140901Z.summary.json`.

- **SMWF-P1-SPLIT-IDENTITY-DECISION-FOCUSED-RETEST-20260928-204**
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-19941b5ac10f370a9d40db95b9e95640e7d91b7f791bb2e401caaee3040b878c-d5b8b30966ddd707735288607b17cc80/command-execution-sha256-e40b22d338fae0e84d830067e18aed8c27311f6097b279e0c7a6718d0470ea3f`.
  Receipt SHA-256: `e40b22d338fae0e84d830067e18aed8c27311f6097b279e0c7a6718d0470ea3f`.
  Validated tree: `3f98af0be949254f03d05382a528971a043547f8c90a0db2f1c4409704314ca4`.
  Exact terminal: `[cncf-sbt] completed: invocation=SMWF-P1-SPLIT-IDENTITY-DECISION-FOCUSED-RETEST-20260928-204 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-755f96e0736761c9bea45197f692e0ca89aca0856430282bb054e73e4fd1cf52-9d46f53f268301555d05f7b473c1b492/90358-20260928T141023Z.log summary=/tmp/skill.cncf.d/cncf-sbt-755f96e0736761c9bea45197f692e0ca89aca0856430282bb054e73e4fd1cf52-9d46f53f268301555d05f7b473c1b492/90358-20260928T141023Z.summary.json`.

- **SMWF-P1-SPLIT-IDENTITY-DECISION-FULL-20260928-205**
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-f3f8b2168d39e6132c992732468e978073abf55354996712d4e28eb9e356af97-7d2e0d92fe4f0b089721986aa45ff1c7/command-execution-sha256-765f64c3e89cd71640620ce24252581a6a90ecebc7d236f27855ef0156f34367`.
  Receipt SHA-256: `765f64c3e89cd71640620ce24252581a6a90ecebc7d236f27855ef0156f34367`.
  Validated tree: `3f98af0be949254f03d05382a528971a043547f8c90a0db2f1c4409704314ca4`.
  Exact terminal: `[cncf-sbt] completed: invocation=SMWF-P1-SPLIT-IDENTITY-DECISION-FULL-20260928-205 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-2c36bfab3dc9cc73a6dc75ac9866750c9d8602b050feb92c93b50f154060a2b3-21e6ea79767e147b13b960020556d430/91537-20260928T141218Z.log summary=/tmp/skill.cncf.d/cncf-sbt-2c36bfab3dc9cc73a6dc75ac9866750c9d8602b050feb92c93b50f154060a2b3-21e6ea79767e147b13b960020556d430/91537-20260928T141218Z.summary.json`.

Focused-204 and full-205 validate the same tree:
`3f98af0be949254f03d05382a528971a043547f8c90a0db2f1c4409704314ca4`.
Full-205 completed at 2026-09-28 23:12:29 JST with **482/482 tests across
53 suites**, zero failed / canceled / ignored / pending tests or aborted suites,
SBT / wrapper exit 0 and verified `lock=released`. CML generation reused the
unchanged 65 generated sources. No top-level SBT invocations overlapped.

All 192 Git-visible product/build paths match full-205, with no additional,
missing or changed product paths. Later English documentation is outside the
complete validated tree. The other-owned SplitPhase design note matches its
preceding full-202 bytes. CML SHA-256 remains
`1fa7fbd3b2115980db5c1922c21150ce36e5a297a40e54139d193fc968f3c55b`;
Cozy / CNCF source and checked-in generated fixtures were not changed.

The evidence map and sole checklist remain **24 verified, 7 partial, 1 missing,
2 pending-final out of 34**. CAM-07 / P1-A03 / P1-S02 / P1-S03 now record the
bounded SPD evidence, but are not checked while the remaining authority
boundaries are unproved. Final intended-tree validation/handoff remains separate.

No staging, repository commit, push, release or Phase closure occurred.
Other-owned work was preserved. Phase 1 remains `in_progress`.


### RepositorySync merge Decision — implementation candidate and validation stop (2026-09-29)

The user continued with `go ahead` in native parent turn
`01a0e985-5b46-7b13-a513-3cd474a3bf57`. Development stayed manual: no
GoalPhase, review or commit orchestration skill was used. Only the local-policy
mandatory SBT routing skills (`cncf-command-execution`,
`cncf-agent-execution`, `cncf-sbt-serial-execution`) selected execution.
The original authority anchor was preserved. The parent recovered its prior
56-turn projection from its actual saved dispatch records, verified the native
ID sequence, and appended this actual user turn for the 57-turn suffix.
No authority was re-created from missing output or a generic approval.

This slice implements a stop-only candidate for RepositorySync's existing
CML `ReviewMerge` alternative `decision-needed`. The normal public
`submitMergeReview` completion still admits the exact issued semantic Result,
clears the review suspension, and saves Active revision 9. It then retains the
exact candidate / issued WorkOrder / Result before an optional host-owned
`RepositorySyncMergeDecision.Port` projects common typed Decision.

The strict Decision input contains the full validated pending-merge request,
exact retained Result reference and digest of the entire Result wire. The
host request must preserve the original run, current Handle / revision /
snapshot / input and candidate / review references, and use a different
continuation identity from the consumed semantic review. It cannot reuse the
generated review Required SPI, review operation or RepositorySync completion
service. Result type is absent. Human operation identities remain metadata,
not generated or registered callable endpoints.

`RepositorySyncMergeReviewServiceOperation.projectDecisionC` is a host-only
read projection, not another Provided API or completion. It re-admits the
full stored command / candidate / review / history chain, projects, and
rechecks retained provenance, the review store and current instance after
host lookup. A failed projection after review resume leaves revision 9 and
its retained Result intact; recovery must read this saved review rather than
repeat the one-shot completion. No new claim, suspension, resume, approval,
merge commit, push or document mutation is introduced by Decision projection.
Existing accept / repair / reject and the unconfigured legacy binding retain
WAIT. The existing commit gate still rejects every nonaccepting disposition
before commit-provider lookup.

Eight added specifications target:

- Public Service and CLI completion into Decision on the same Handle, stable
  host-only read retries, revision 9 and rehydrated admitted definition facts.
- WAIT compatibility for other dispositions / unconfigured binding and invalid
  binding rejection.
- Invalid semantic Results rejected before claim or host projection.
- Recovery after post-resume projection failure from the retained typed Result,
  without another completion.
- Frozen host input / run / context / reference drift, semantic SPI reuse,
  continuation reuse and malformed metadata rejection, with zero commit calls.
- Missing / substituted / expired review, candidate-command drift and history
  drift rejected before host lookup.
- Instance, review-store and retained-provenance changes after lookup rejected
  without returning a stale Decision.
- Strict nested input/reference codec rejection of approval / state / selector
  fields; Decision cannot be admitted as a WorkOrder Result, and its human
  completion metadata is not callable.

**These are implementation intentions, not passing connected proof.** The
initial focused run 206 compiled main source but stopped at the new test
fixture's function-type syntax. The parent fixed its missing parentheses.
Focused-207 then executed 137 tests across two suites: **133 passed and four
failed**, with zero canceled / ignored / pending tests or aborted suites.
The four failures exposed the new projection's opaque DefinitionBinding
reference comparison. Existing bindings are opaque classes, not value-equality
case classes. The parent replaced that comparison with the existing
`RepositorySyncMergeReviewBoundary._sameDefinition` full admitted-fact
comparator. Tests were strengthened to require successful host lookup in
negative cases, reject reuse of the consumed continuation, and exercise
rehydrated definitions.

Focused-208 was selected for this repaired tree but its read-only receipt
preflight was platform-denied before rendering. No SBT or full test started
for 208. The exact runner denial was:

> Rejected("This action was rejected due to unacceptable risk. Reason: The command dynamically renders and executes a request from an unverified intake file, whose command and side effects are unknown; generic approvals do not authorize this potentially escalated action. The agent must not attempt to achieve the same outcome via workaround, indirect execution, or policy circumvention. Proceed only with a materially safer alternative, or if the user explicitly approves the action after being informed of the risk. Otherwise, stop and request user input.")

The runner reported no execution receipt consumed. The parent observed only
the original allocation / request / intake files: no `.execution-started`
marker, execution bundle or terminal marker existed for 208. The selected
delegation is treated as spent for redispatch purposes, not as permission to
reuse its artifact. The parent did not retry that action, use another runner,
execute SBT directly, weaken the envelope, or launch a full test.

Classification: `EXECUTION_PERMISSION_DENIED_AFTER_ATTRIBUTABLE_AUTHORITY`.
This is an execution-infrastructure stop, not a new Phase / Task / Step /
Slice, Hygiene item or Development Candidate. Resume requires
platform-supported permitted recovery for the exact rejected action;
generic approval does not transfer platform permission. The execution skills'
denial contract caused validation to stop. The comparison repair is saved,
but untested and not acceptance evidence.

All attempts used `gpt-5.6-luna / medium`, agent type
`cncf_command_runner`, runtime source / route `registered-custom-role`:

| Attempt | Result | Actual bound agent |
| --- | --- | --- |
| 206 | Main source compiled; new test fixture compilation failed | `/root/sbt_receipt_f9bf1439262101c7f4c8` |
| 207 | 137 tests, 133 passed / four failed, two suites | `/root/sbt_receipt_7f0d04c80d89d25916b1` |
| 208 | Read-only preflight denied; SBT unstarted | `/root/sbt_receipt_39c72fa11f40d881bc53` |

Selected logical SBT argv for all three attempts:
`["--batch", "testOnly org.simplemodeling.textus.smworkflow.admission.RepositorySyncMergeValidationAdmissionExecutionSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseStartExecutionSpec"]`.

Exact immutable terminal evidence for the two started attempts:

- **SMWF-P1-MERGE-DECISION-FOCUSED-20260929-206**
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-6747ef94df629a5741f200b65bbdcdb558523e851b0e377c3fe803a420040fb7-b9f9b5a70916a0e61ec76dc95fad655c/command-execution-sha256-9fead526ae9c74ee4d4d1d15f28a38a4073e316a35736946f2cff31b4ab5063c`.
  Receipt SHA-256: `9fead526ae9c74ee4d4d1d15f28a38a4073e316a35736946f2cff31b4ab5063c`.
  Validated command tree (failed outcome): `5a991aee2034c715c4e82ad12fde913d12b2eb6ccf6e21e4e8a927997f48d7cc`.
  Exact terminal: `[cncf-sbt] completed: invocation=SMWF-P1-MERGE-DECISION-FOCUSED-20260929-206 sbt_exit=1 wrapper_exit=1 lock=released log=/tmp/skill.cncf.d/cncf-sbt-ba704472a789720ae2fbdf74cfa5d36c094d7b12f4e224bf749e177dcd10b388-c4aa544ca21f6b18456acb8c81e7c0f7/92674-20260928T195042Z.log summary=/tmp/skill.cncf.d/cncf-sbt-ba704472a789720ae2fbdf74cfa5d36c094d7b12f4e224bf749e177dcd10b388-c4aa544ca21f6b18456acb8c81e7c0f7/92674-20260928T195042Z.summary.json`.

- **SMWF-P1-MERGE-DECISION-FOCUSED-RETEST-20260929-207**
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-902979654c01db6826fbe983134da4e9efb7c3e7f7a1540e7a06a286780de9c5-85b6bf09b0fc888f2f8a33674aaf3e4b/command-execution-sha256-219523401b918ba595f172a6eba03abd5a54fe3e2d71b6f737fc1143333980ad`.
  Receipt SHA-256: `219523401b918ba595f172a6eba03abd5a54fe3e2d71b6f737fc1143333980ad`.
  Validated command tree (failed outcome): `92d4b82658788818dd4f99617de25c6f7f17fa91128fa54e93e8c191aa16eb9c`.
  Exact terminal: `[cncf-sbt] completed: invocation=SMWF-P1-MERGE-DECISION-FOCUSED-RETEST-20260929-207 sbt_exit=1 wrapper_exit=1 lock=released log=/tmp/skill.cncf.d/cncf-sbt-724b774a01e16094d1e4f5d840aa7a72babb0e569c28e32f58b15f9944fdc025-aadfb6e8b5666355f24676ad5981929a/93410-20260928T195210Z.log summary=/tmp/skill.cncf.d/cncf-sbt-724b774a01e16094d1e4f5d840aa7a72babb0e569c28e32f58b15f9944fdc025-aadfb6e8b5666355f24676ad5981929a/93410-20260928T195210Z.summary.json`.

Denied-208 intake locator:
`/tmp/skill.cncf.d/cncf-command-execution-3e9170cebfe0c4dffc6707eb6d63b410354accd2034216231299bc1dd4694e68-7dcec9badffee347d8dffdb27d401f7e/command-execution-intake-sha256-4c116cdf85788154cb24fa9b70058ae95f23f997ddc0e4abc5ccf6cf6f79f288.json`.
Frozen attempt request:
`/private/tmp/skill.cncf.d/cncf-workflow-66fd22cd3560e4a5505140e42c2c11643c8df0874c30db9c1dc329f18a4df44c-fd86901ca944a1ac3a0c30da2c40abf9/attempt-request.json`.
It has no SBT completion marker, exit status, validated command tree or
execution receipt. Do not invent a successful lock-release marker for an
unstarted invocation. 206 and 207 both independently prove SBT / wrapper
exit 1 and `lock=released`; their command receipts were verified before
any next attempt. No top-level SBT invocations overlapped.

The current 193 Git-visible product/build paths differ from failed-207 in
exactly the new Decision module and its test file; no product path was added
or removed after that failed receipt. This read-only byte inventory does not
validate the repair. Last passing full-205 covers the earlier 192-path
SplitPhase identity Decision product tree only. The other-owned
`docs/notes/sm-split-phase-workflow-definition.md` matches its pre-slice
bytes and was not edited. CML SHA-256 remains
`1fa7fbd3b2115980db5c1922c21150ce36e5a297a40e54139d193fc968f3c55b`.
CML, generated ABI fixtures, Cozy and CNCF source were not changed.

The sole checklist and evidence map retain **24 verified, 7 partial,
1 missing and 2 pending-final out of 34**. RSD-PENDING is explicitly not
verified evidence; no checkbox or item disposition was promoted. English
documentation records the unvalidated candidate and exact stop. No staging,
repository commit, push, release or Phase closure occurred. Phase 1 remains
`in_progress`.

### RepositorySync Decision validation — explicit approval observed, platform still denied (2026-09-29)

The user explicitly replied `承認する` after the parent disclosed the
delegation-file execution risk and requested permission for the exact two
focused suites. Native parent turn: `01a0e99b-333b-7460-9e29-ac71ecb87461`; exact text:
`承認する\n\n`. This is observed direct user intent, not a native
platform approval token.

The parent honored this new explicit user instruction with one fresh,
scope-identical request `SMWF-P1-MERGE-DECISION-FOCUSED-USER-AUTHORIZED-20260929-209` for independent platform review.
This was not an automatic retry, transport-recovery ticket or envelope-expiry
refresh. Denied-208 remained immutable and unstarted, its selected ID was
included in the new consumed-ID ledger, and neither its intake nor its
permission artifact was reused. The original authority anchor / origin were
preserved; the verified actual parent suffix grew to 58 turns and included
the new approval. No source, test, CML, upstream or agent/skill-contract edits
were made in this continuation.

Configured execution: `gpt-5.6-luna / medium`, runtime source / route
`registered-custom-role`, agent type `cncf_command_runner`, action
`START`, actual bound agent `/root/sbt_receipt_3d218dd7d041bfa60004`.
The command remained:
`["--batch", "testOnly org.simplemodeling.textus.smworkflow.admission.RepositorySyncMergeValidationAdmissionExecutionSpec org.simplemodeling.textus.smworkflow.admission.SplitPhaseStartExecutionSpec"]`,
in `/Users/asami/src/dev2026/sm-workflow`.

Intake locator:
`/tmp/skill.cncf.d/cncf-command-execution-0027717f9385698e1a56f9b2699efe560a27a856a3f6820128ade1c6a862a2bc-c3acbccccfeb9a604c9eec10b4d14ed8/command-execution-intake-sha256-c740e6c61562000e66c7064de32408753973273263ae69997fff0748098351ff.json`.
Frozen attempt request:
`/private/tmp/skill.cncf.d/cncf-workflow-f60e1cf9735df98a2ec42f3f1748a196956179371da39c845fff43d4a5674404-4af8eee154bdf5b0ce83acc9a9a9754b/attempt-request.json`.
Attempt bundle:
`/private/tmp/skill.cncf.d/cncf-workflow-f60e1cf9735df98a2ec42f3f1748a196956179371da39c845fff43d4a5674404-4af8eee154bdf5b0ce83acc9a9a9754b/attempt`.
The parent read-only `verify-sbt-command-attempt` returned `accepted` and
attempt SHA-256
`3d3736eafc9bd6261e1e9217283d8552b91444becd0997385594ea11c0763ca3`.
That consistency check does not grant platform permission or execute tests.

The runner's scoped read-only preflight was again denied before it ran:

> Rejected("This action was rejected due to unacceptable risk. Reason: The command may execute an opaque, receipt-controlled request with escalated privileges, potentially including SBT side effects, while the actual payload and scope are unavailable; approval of the wrapper does not clearly authorize unknown downstream actions. The agent must not attempt to achieve the same outcome via workaround, indirect execution, or policy circumvention. Proceed only with a materially safer alternative, or if the user explicitly approves the action after being informed of the risk. Otherwise, stop and request user input.")

Result: `EXECUTION_PERMISSION_DENIED_AFTER_ATTRIBUTABLE_AUTHORITY`.
The user's explicit approval is present; this is not missing user intent.
SBT was not started. The original intake parent contains only allocation,
request and intake files, with no execution-start marker or execution receipt.
No SBT exit status, wrapper status, terminal marker, test counts or lock state
is claimed for 209. A new full test was not started.

The parent exhausted safe read-only checks of the actual request, verified
attempt and installed rendering/execution source. The request selects only
the exact two suites in the application repository. Its complete exclusions
include remote publication, deployment, push, credential change, destructive
cleanup, source editing, staging and commit. Expected effects remain ordinary
local SBT build outputs / caches, cooperative lock and allocated evidence.
`render-command-execution-request.py` verifies the intake, nested scope and
representable repository tree and returns an execution request; it does not
invoke the SBT executor or consume the intake. The separate receipt executor
owns the actual execution and one-shot marker. These observations explain
the selected scope but do not override the rejection.

No denied action was redispatched after this refusal. No alternate role,
parent-direct SBT, isolated cache, changed command, hidden payload or weakened
binding was used. No further identical user approval was requested. The
remaining blocker is permitted execution of the exact reviewed route, not
application acceptance or a new Phase gate. Changing the command/agent/skill
execution contract is distinct work and has not been authorized or performed
by this approval alone.

The application candidate remains unvalidated after the ABI-comparison repair.
The current 193 product/build paths still differ from failed-207 only in
`RepositorySyncMergeDecision.scala` and its test file. The other-owned design
note is unchanged. The sole checklist / evidence map remain **24 verified,
7 partial, 1 missing and 2 pending-final out of 34**. Documentation is English;
no acceptance item was promoted. No staging / commit / push / release occurred,
and no Phase was closed.


### RepositorySync Decision validation — separated execution route succeeds (2026-09-29)

The direct continuation instruction in the sm-workflow development chat
(native turn `01a0e9b0-1761-7751-a6c6-e2c709c9c55c`, parent thread
`01a0a6d7-7043-73a2-a808-1367fe5fdb1f`) selected validation after the
command skills and deployed runner had been updated. This is manual
validation-only continuation, not a GoalPhase invocation, Phase owner
transfer, review, acceptance commit or closure.

The old 208 / 209 intakes remain immutable and unstarted. Their denial records
above are historical facts, not successful authorization or reusable artifacts.
The new route ran the read-only renderer as its own non-escalated tool call,
surfaced the concrete repository / argv / mutations / external access, retained
the structured request, and submitted that unchanged request in a later tool
call for separate scoped platform execution review. Both new executions were
permitted and actually started SBT. Contract changes alone are not represented
as native approval overrides.

Because the stopped authority belonged to the separate SimpleModeling
development chat, this validation uses the actual current parent's direct
continuation and standing `asami-local-sbt-v1` authority, not a fabricated
suffix containing another task's user history. Its validation-only authority
anchor is:
`/private/tmp/skill.cncf.d/cncf-workflow-133891d4db715725f6beb187044e6ff68b57c798a6f982b1a82dc4a133402a40-28c7991057d2fe1b522a89e3cde533ca/anchor`,
SHA-256 `61567787a61065bd7f93d73d51ccdf9782197fd1ee7bd2a90d5bef4efc4eda7a`.
The saved current-message origin and parent-verified dispatch retain the actual
one-turn suffix. Old consumed IDs, including 208 / 209, remain in the new
ledger; no old permission artifact or intake was reused.

The deployed current policy selects the installed
`/Users/asami/.agents/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh`.
The earlier `~/.codex/skills` user-skill path has been migrated and is not used
as an alternate execution path. The same machine-wide lock, normal dependency
caches, typed receipt boundary and closed prohibitions remain mandatory.

| Attempt | Validation | Result | Actual bound agent |
| --- | --- | --- | --- |
| 210 | Exact RepositorySyncMergeValidationAdmissionExecutionSpec / SplitPhaseStartExecutionSpec | 137/137 tests, 2 suites | `/root/sbt_receipt_d5c5b7f00fb42708b3a6` |
| 211 | `["--batch", "test"]` | 490/490 tests, 53 suites | `/root/sbt_receipt_acab6e1e722fd7a10ba1` |

Both used configured `gpt-5.6-luna / medium`, registered custom role
`cncf_command_runner`, route `registered-custom-role`, runner health
`healthy`, infrastructure issue `none`, command result `success`,
command completion `terminal`, SBT / wrapper exit 0 and verified
`lock=released`. There were no failed / canceled / ignored / pending tests
or aborted suites, and no independent top-level SBT overlap.

- **Focused-210 — SMWF-P1-MERGE-DECISION-FOCUSED-SEPARATED-20260929-210**
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-070f0faa239921b434c18bdb9475757da1df6c77298d17851ce976aaeb02f835-1f53f209174b0910b741757c2595ad6f/command-execution-sha256-da44ac50a1928071434de2ef507c772fac90d62c9846b603fa20efdeba3a85a4`.
  Receipt SHA-256: `da44ac50a1928071434de2ef507c772fac90d62c9846b603fa20efdeba3a85a4`.
  Exact terminal: `[cncf-sbt] completed: invocation=SMWF-P1-MERGE-DECISION-FOCUSED-SEPARATED-20260929-210 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-a1afab429f0a84e26ba98d6c8e653ec835c6e3874619390da982c48eed973120-7aa08ef57dfbdb112a5642acc6bf30cd/15821-20260928T202924Z.log summary=/tmp/skill.cncf.d/cncf-sbt-a1afab429f0a84e26ba98d6c8e653ec835c6e3874619390da982c48eed973120-7aa08ef57dfbdb112a5642acc6bf30cd/15821-20260928T202924Z.summary.json`.

- **Full-211 — SMWF-P1-MERGE-DECISION-FULL-SEPARATED-20260929-211**
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-8a41ca149f764c1dc5be889a730c62430ab5c9dc15a5db51eaed3d184a8110db-92f333dc1a48d6f8cda88a832069eb71/command-execution-sha256-5d2c09c0fe86f2f6c7ba86c8c3c7430bdb2159339166ecca728605dd7096cafc`.
  Receipt SHA-256: `5d2c09c0fe86f2f6c7ba86c8c3c7430bdb2159339166ecca728605dd7096cafc`.
  Exact terminal: `[cncf-sbt] completed: invocation=SMWF-P1-MERGE-DECISION-FULL-SEPARATED-20260929-211 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-6a0e95458f97059bd61430ee9f4e3ddc5ccc84172b9ed12f5a93297e0cc136fb-54b0bd51434e1594f8a5d2a3fb8a3467/17198-20260928T203146Z.log summary=/tmp/skill.cncf.d/cncf-sbt-6a0e95458f97059bd61430ee9f4e3ddc5ccc84172b9ed12f5a93297e0cc136fb-54b0bd51434e1594f8a5d2a3fb8a3467/17198-20260928T203146Z.summary.json`.

The parent independently verified both immutable receipts and their prebound
agent identities. They validate the same unchanged complete tree:
`f5ffb3b6d9a3d7cdf2e0976ae913c199f0fad97341f0687d0885c50fe40ffefc`.
All 193 Git-visible product/build paths match full-211; the following English
documentation updates are outside that complete validated tree. The
other-owned SplitPhase design note is unchanged. CML SHA-256 remains
`1fa7fbd3b2115980db5c1922c21150ce36e5a297a40e54139d193fc968f3c55b`.
No application source, test, CML, generated fixture, Cozy or CNCF edit was
needed in this continuation.

RSD is now bounded passing evidence for public merge-review completion into
a stop-only typed Decision, retained-review read recovery, strict host
intent / input / provenance and zero commit authority. It is not a human
Result/resume engine or production Git adapter. README, evidence map and the
sole checklist now reflect that distinction. Remaining GoalPhase authority
connections, actual application Codex judgment round trip and final intended
three-profile handoff remain open.

The checklist / evidence map still have **24 verified, 7 partial, 1 missing
and 2 pending-final out of 34**. No item was promoted merely because these
regressions pass. No staging, repository commit, push, publication, release
or Phase closure occurred. Phase 1 remains `in_progress`.


## GoalPhase pre-reviewed closure authority Decision — fixture slice (2026-09-29)

The direct user continuation `go ahead` selected the next bounded application
development slice. No Phase Goal or goal skill was started; this is manual
Phase 1 development, not Phase acceptance. The public
`GoalPhaseService.requestStepClose` receives an opt-in pre-reviewed authority
binding. The legacy automatic review / repair assembly remains unchanged.

### Application boundary

- `GoalPhaseClosureAuthority` requires existing fresh full-Step review before
  looking up authority; missing or stale review is not silently accepted.
- The Workflow loads read-only host-retained local-commit authority bound to the
  exact Handle, instance revision, candidate/program/planning and the digest of
  the complete request including review evidence. It uses CNCF CAM's Authority
  requirement and common authority-gap routing, not an AI authority judgment.
- Missing authority projects a strict common typed Decision with a frozen
  application input and stable host-owned identities. The Start-created Active
  Awaiting instance, history and candidate bytes remain unchanged; no commit,
  claim, WorkOrder or authority grant occurs.
- Host human-boundary identities are metadata only. Semantic Required SPIs and
  GoalPhase callable operations cannot be reused for those identities. No human
  completion endpoint, Decision Result admission or resume engine is registered.
- Malformed/stale authority is rejected, not converted into another Decision.
  Host intent drift and concurrent instance / ABI / artifact / authority drift
  are rechecked before response or commit.
- Separately retained exact authority permits one reviewed fixture commit and
  terminal append. Authority is never manufactured by the Decision output.
  Commit and instance append remain separate operations, not an atomic transaction.
- `GoalPhaseStepClosureBoundary.sameRecord` compares every immutable public ABI
  fact plus all other record fields. Both read re-admission and closure append
  accept rehydrated equivalent bindings and reject changed fixture provenance.

Seven new executable specifications cover Service / CLI Start-to-Decision,
stable retry and Result separation; separately recorded exact authorization
and one closure through both transports with fresh and rehydrated bindings;
missing ports/review/artifact prerequisites; malformed authority; altered host
Decision input/context/SPI/endpoint identities; lookup races including changed
ABI fixture hash; and strict nested codec rejection of approval/state selectors.

This gate is pre-reviewed-only. It cannot yet be composed with the existing
automatic review / repair WorkOrder loop. No operational Git/SBT provider,
public skill, generic command surface or CNCF/Cozy source edit is added.

### Command execution provenance

The command-routing, agent-resolution, workflow-protocol and serial-SBT skills
required immutable receipt-first attempts and registered command delegation.
All six runners used configured `gpt-5.6-luna / medium`,
`runtime_source: registered custom role`, logical role / agent type
`cncf_command_runner`. Parent-observed native origin was
`01a0e9bb-c3ec-75f1-9103-936ee04d3639`, exact text `go ahead\n\n`,
within thread `01a0a6d7-7043-73a2-a808-1367fe5fdb1f`.
The verified anchor SHA-256 is
`b6d8396c1527a40c7ef8d3ab16c19ae164d7279c59bf2787c2c141c0d98e9f73`.

Each runner independently rendered read-only scope, retained the exact
structured execution request, ended that tool orchestration and submitted it
unchanged in a subsequent scoped execution call. The SBT helper invoked the
installed shared serial wrapper; no parent-direct SBT or lock bypass occurred.
Every recorded command is terminal with `lock=released`; failures are command
compile failures, not unhealthy runners. All runners are
`runner_health: healthy`, `infrastructure_issue: none`.

- **SMWF-P1-GOAL-AUTHORITY-FOCUSED-20260929-212**: Compile rejected: CNCF-private candidate-definition access; no tests ran.
  SBT / wrapper exit 1 / 1, `lock=released`.
  Actual agent: `/root/sbt_receipt_075383356f2e7ce52f5c`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-44565e989f9455b3844f78668f363bede9040c75de413771688a5d89a1db1678-267980678bafcca3b78f272fa2405d02/command-execution-sha256-c959c9829b8a323f18002ac3396ac552f3feb11a2061d08387a4f0255bd3cb86`.
  Receipt SHA-256: `c959c9829b8a323f18002ac3396ac552f3feb11a2061d08387a4f0255bd3cb86`.
  Validated tree: `a068abc7824ce3a4d9b2db836b751b365859e6962b463f0cb927838b67ace6b3`.

- **SMWF-P1-GOAL-AUTHORITY-FOCUSED-RETEST-20260929-213**: Main source compiled; new negative fixture resultType required a typed reference; no tests ran.
  SBT / wrapper exit 1 / 1, `lock=released`.
  Actual agent: `/root/sbt_receipt_73de9981600b9ce4f84a`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-bbc87e117bf9ebed9a5e043673f79f59532ee41d71bc8bd608b3ce1732e37b1d-04e601a4cda181730d75def2d19f09df/command-execution-sha256-8895161829a29696b1b2a63cdf0158a177427418d13e994b1b91d0572a856c7e`.
  Receipt SHA-256: `8895161829a29696b1b2a63cdf0158a177427418d13e994b1b91d0572a856c7e`.
  Validated tree: `93ba14884e042bf40385355268504b9c428de01ff6474881642e527426127b24`.

- **SMWF-P1-GOAL-AUTHORITY-FOCUSED-RETEST-20260929-214**: Focused: 32/32 tests, 4 suites.
  SBT / wrapper exit 0 / 0, `lock=released`.
  Actual agent: `/root/sbt_receipt_ee4b9930fce364197041`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-c0f30afcfe1ff0d61d82fde80535ba8aeb5aad2a9952ed8ab4c14457554bd79a-d4bfa474f4a6e839abdd3911da6c8aa7/command-execution-sha256-06b536d2ddfa6cb0f4b32390cf866494e493b0ee1547450e48865586b2f8cb86`.
  Receipt SHA-256: `06b536d2ddfa6cb0f4b32390cf866494e493b0ee1547450e48865586b2f8cb86`.
  Validated tree: `7a303446729bdeb9cfe33e4d38fb89e0bc4482f69745fb2aa212f362962ab70d`.

- **SMWF-P1-GOAL-AUTHORITY-FULL-20260929-215**: Full: 497/497 tests, 54 suites; initial authority tree.
  SBT / wrapper exit 0 / 0, `lock=released`.
  Actual agent: `/root/sbt_receipt_79ee510202001c3da5f2`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-9e5e4add213138849d8035ab95b2d96e67d41304141573c4744f4505b6a0c3fe-9c8aa2753f65250ce6d4f20c70bf52db/command-execution-sha256-d5e8bd6f3bdd5f63466688b77fe6d98b168a5984a188e2441a3ea73c13b1dbed`.
  Receipt SHA-256: `d5e8bd6f3bdd5f63466688b77fe6d98b168a5984a188e2441a3ea73c13b1dbed`.
  Validated tree: `7a303446729bdeb9cfe33e4d38fb89e0bc4482f69745fb2aa212f362962ab70d`.

- **SMWF-P1-GOAL-AUTHORITY-REHYDRATED-FOCUSED-20260929-216**: Focused: 32/32 tests, 4 suites; strengthened rehydrated closure.
  SBT / wrapper exit 0 / 0, `lock=released`.
  Actual agent: `/root/sbt_receipt_1ef621d358346c7761d7`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-de7b70d7a685439f7576743c050f973c02c791578b77bd4b501a1c665e909799-cf8d392d2aa104ceb0a59235f17bedb1/command-execution-sha256-d3e59091357a205796e6faaff524bc95393a4aff52c8a03020840eb269735fda`.
  Receipt SHA-256: `d3e59091357a205796e6faaff524bc95393a4aff52c8a03020840eb269735fda`.
  Validated tree: `07c47f00c9299b39d7d8fbe1838db0aaf7aad84aa9b210770050e9d90f66738e`.

- **SMWF-P1-GOAL-AUTHORITY-REHYDRATED-FULL-20260929-217**: Full: 497/497 tests, 54 suites; strengthened current tree.
  SBT / wrapper exit 0 / 0, `lock=released`.
  Actual agent: `/root/sbt_receipt_ff129e2b13a1b22d52e3`.
  Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-69fd490925b075129eb8e098398ea912329c8a16bc6638f08ea455e783794218-8b02722aac9492fad9b6afa928343c28/command-execution-sha256-4a8ef48554450fdafdc01a54ad5924286a7f80b52adce27268ce27e41c5d7f47`.
  Receipt SHA-256: `4a8ef48554450fdafdc01a54ad5924286a7f80b52adce27268ce27e41c5d7f47`.
  Validated tree: `07c47f00c9299b39d7d8fbe1838db0aaf7aad84aa9b210770050e9d90f66738e`.

Focused-216 and full-217 identify the same unchanged complete tree:
`07c47f00c9299b39d7d8fbe1838db0aaf7aad84aa9b210770050e9d90f66738e`.
The full-217 exact terminal marker is:

```text
[cncf-sbt] completed: invocation=SMWF-P1-GOAL-AUTHORITY-REHYDRATED-FULL-20260929-217 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-cfe0274e58cce83d104c1cff4b101f116867e2165679b5d46d48194fcb72a133-e18592ec83d20db9f957befefccaf717/48855-20260928T210703Z.log summary=/tmp/skill.cncf.d/cncf-sbt-cfe0274e58cce83d104c1cff4b101f116867e2165679b5d46d48194fcb72a133-e18592ec83d20db9f957befefccaf717/48855-20260928T210703Z.summary.json
```

The parent independently verified all six receipts, prebound actual agent
identities, terminal markers and saved test summaries. Full-217 proves 497/497
tests across 54 suites with zero failed / canceled / ignored / pending or
aborted. All 195 Git-visible product / build paths match full-217. The later
English README / evidence-map / checklist / Phase updates are outside its
complete validated tree. The other-owned SplitPhase design note is unchanged.
CML SHA-256 remains
`1fa7fbd3b2115980db5c1922c21150ce36e5a297a40e54139d193fc968f3c55b`.
No checked-in ABI fixture, Cozy or CNCF source changed.

GPA is bounded passing fixture evidence, not complete combined semantic /
authority progression or human execution. CAM-07 / P1-A03 / P1-S02 / P1-S03 /
P1-S08 remain partial. The sole checklist remains **24 verified, 7 partial,
1 missing, 2 pending-final out of 34**. Automatic GoalPhase review/repair
authority composition, combined SplitPhase semantic/identity binding, actual
application Codex judgment and final intended-tree handoff remain open.

No staging, repository commit, push, publication, release or Phase closure
occurred. Phase 1 remains `in_progress`.

## GoalPhase integrated review / repair authority — unvalidated continuation (2026-09-29)

The direct user continuation `go ahead` selected integration of the existing
GoalPhase authority gate with its automatic review / repair fixture. This is
manual Phase 1 development; no Phase Goal, acceptance commit or Phase closure
was started.

### Written application changes (not executable evidence)

- The explicit fixture Service assembly accepts an optional combined authority /
  accepted-review receipt binding. Legacy unconfigured behavior and the separate
  pre-reviewed-only opt-in binding remain available. Their authority bindings
  cannot be selected together.
- Normal review / repair WorkOrders remain semantic work. After admitted fresh
  full-review completion, the Workflow retains the exact issued WorkOrder,
  consumed Result and reviewed Active instance before inspecting host-held
  local-commit authority. The normalized authority input includes retained
  evidence and the accepted full review, bound to the current candidate and
  instance revision.
- Missing authority is intended to return the existing strict common Decision,
  not a second WorkOrder, another review, approval or a terminal result.
  Already recorded exact authority is intended to permit the existing bounded
  fixture commit / terminal path without another public close request.
- `GoalPhaseReviewedClosure` provides an immutable receipt-port contract and
  process-memory fixture implementation. Exact writes are idempotent; replacing
  proof for the same instance / revision fails. A canonical WorkOrder / Result
  digest checks payload integrity, not a signature or an authority grant. The
  host receipt store remains trusted.
- A host-only retained-proof projection is intended to recover a failed Decision
  display without submitting the one-shot Result again. It re-admits the review,
  ABI binding, instance, artifact bytes and authority, and rechecks the retained
  record around host lookups. This read path explicitly refuses commit even if
  authority has since appeared. No public Operation, human completion endpoint
  or Decision Result/resume engine is added.
- Review completion, receipt persistence, authority lookup, commit and instance
  append remain separate operations. A retention failure after completion leaves
  the Result consumed and the instance Active; it does not claim shared atomic
  rollback or automatic recovery when no receipt was saved.

Ten additional `GoalPhaseRepairLoopSpec` cases are written for Service / CLI
acceptance-to-Decision and read-only recovery; already recorded exact authority;
changed / unchanged repair and fresh full re-review; proactive full review;
incomplete binding; retention failure / divergent return; failed display
recovery; payload / instance / candidate / authority drift; lookup-time receipt
drift; and immutable idempotent receipt storage. They have not compiled or run.

### Execution stop and retained provenance

The command-routing, agent-resolution, workflow-protocol and serial-SBT skills
required receipt-first typed delegation. The registered role was verified as a
regular deployed file configured with `gpt-5.6-luna / medium`.
Runtime source: registered custom role. Logical role / agent type:
`cncf_command_runner`. Actual delegated agent:
`/root/sbt_receipt_2e6bab890ca3a1bac89e`.

- Evidence / attempt identity:
  `SMWF-P1-GOAL-INTEGRATED-AUTHORITY-FOCUSED-20260929-218`.
- Native originating turn: `01a0e9e8-e95e-72d3-9ef5-c886c67aa8c8`,
  exact observed user text `go ahead\n\n`, in thread
  `01a0a6d7-7043-73a2-a808-1367fe5fdb1f`.
- Authority anchor SHA-256:
  `19033b36a39dca95b65efced615210fcbb322013f49386abeade641de76c9245`.
- Verified attempt SHA-256:
  `0516fed0ab939ba1f1ae62b69fbd0fa364eec5aef6f8ea8da1146ac2f07a10b1`.
- Immutable intake locator:
  `/tmp/skill.cncf.d/cncf-command-execution-fe65dad26e775aa56c216e963723e4e26a006c573933d8271d67fda50507c8c4-25605a127e31726c3924f6b9f2c29e44/command-execution-intake-sha256-308d2e79742890cbea5a0634153452cb07b277bf839792d1211f825f75e47df8.json`.
- Selected repository: `/Users/asami/src/dev2026/sm-workflow`.
  Logical argv: `--batch` and one `testOnly` argument naming
  `GoalPhaseRepairLoopSpec`, `GoalPhaseClosureAuthorityExecutionSpec`,
  `GoalPhaseReviewBoundarySpec` and `GoalPhaseStepCloseRoutingSpec`.

The runner's read-only preflight returned:

```text
ENVIRONMENT_PRECONDITION_ERROR: uv-managed CPython is required; run workstation bootstrap
```

This is not a platform denial or lack of user approval. The updated helper
requires stable uv-managed CPython >=3.12, while the deployed runner's frozen
preflight still invokes `python3`; on this workstation that resolves to
`/opt/homebrew/bin/python3`. The installed uv-managed route successfully built
and verified the parent-owned typed artifacts, so installing Python or changing
the shell profile is not the indicated repair. The registered runner invocation
contract needs synchronization with the updated helper runtime prerequisite.

SBT and the shared wrapper never started. The parent independently checked that
the intake's `.execution-started` marker is absent. No command execution receipt,
test summary, validated tree or `lock=released` claim exists for 218. There was
no parent-direct SBT, route substitution, profile edit, recovery retry, cache
redirection or lock bypass. All opaque private metadata stayed private.

### Current-tree accounting and handoff

The current Git-visible product/build inventory has 196 paths. Relative to the
previous full-217 product hashes, exactly five existing paths changed
(`GoalPhaseClosureAuthority`, `GoalPhaseFixtureServiceAssembly`,
`GoalPhaseReviewCompletionOperation`, `GoalPhaseStepCloseServiceOperation`
and `GoalPhaseRepairLoopSpec`) and `GoalPhaseReviewedClosure` was added.
The other 190 paths match; no product path was removed. This inventory check is
not compile/test evidence. Full-217's 497/497 result applies only to its earlier
pre-reviewed authority tree, not these changes.

CML SHA-256 remains
`1fa7fbd3b2115980db5c1922c21150ce36e5a297a40e54139d193fc968f3c55b`.
The other-owned SplitPhase note remains
`915a7ff2adb3a0f671ddfc58c6975b484354799dbf58b02c1ee848f700ad1cc3`.
No checked-in generated ABI fixture, Cozy / CNCF source or global skill / agent
configuration was changed. `git diff --check` passed for tracked changes;
untracked Scala compilation remains pending. HEAD is still
`75337065397b49e1f663edac0b63600174e87e55`; the index remains empty.

GPI is written implementation only, not acceptance evidence. The sole checklist
remains **24 verified, 7 partial, 1 missing and 2 pending-final out of 34**.
Resolve the registered runner runtime mismatch before validating the integrated
GoalPhase slice. SplitPhase semantic / identity composition, actual application
Codex judgment and final intended-tree handoff remain open. This execution stop
adds no Phase acceptance requirement, task, Hygiene item or Development Candidate.
No staging, repository commit, push, publication, release or Phase closure
occurred. Phase 1 remains `in_progress`.


## GoalPhase integrated review / repair authority — validated continuation (2026-09-29)

The direct user continuation `続けて` resumed the written integration after
workstation agent synchronization. The previous unstarted-218 record above
remains historical; it was not retried or converted into validation evidence.
No product changes were required in this continuation.

### Executable boundary now proved

Ten additional specifications in `GoalPhaseRepairLoopSpec` now compile and pass.
The explicit optional fixture assembly connects normal review and bounded
repair / fresh full re-review to the existing authority gate, on the public
Start-created instance through both Service and CLI. Accepted review is consumed
once and retained before host authority lookup. Missing authority returns a
strict common Decision with no commit, additional semantic WorkOrder or false
terminal. Already recorded exact authority allows the existing exact fixture
commit and Completed append without another public close request. Proactive
full review also enters the same gate without issuing or retaining a WorkOrder.

The immutable accepted-review receipt preserves the issued WorkOrder, consumed
Result, reviewed instance and canonical payload-integrity digest. A host-only
read projection re-admits those facts and can recover a failed Decision display
without resubmitting Result. It cannot commit, consume a human Result or resume
authority, even when exact authority later appears. Drift in proof, instance,
candidate bytes, authority or the retained receipt during lookup fails closed.
Receipt writes are exactly idempotent and reject replacement. Incomplete host
bindings are rejected before Start.

These remain loose durable steps, not a shared atomic transaction. Failure to
retain proof after common completion leaves the Result consumed and the instance
Active; no rollback or automatic recovery of absent proof is claimed. Host
receipt / authorization fixtures are trusted ports, not signatures, actual
human approval or a production Git / SQLite adapter. No callable human Operation
or Decision Result/resume API was added. The unconfigured legacy assembly and
the separate pre-reviewed-only binding retain their existing behavior.

### Independent command evidence

The command-execution, agent-resolution, workflow-protocol and serial-SBT skills
required typed receipt-first delegation and sequential execution. The deployed
`cncf_command_runner` is a regular file, SHA-256
`61bc8617ec3e29a8460c7008b94635d7e830034978711bbc4132c691c03ba8a9`,
matching the development-workstation canonical definition. Its updated uv-managed
read-only renderer ran independently of the later scoped execution call;
the returned execution request was used unchanged. SBT used the normal caches
and shared serial wrapper, with no parent-direct execution, lock bypass,
isolated cache, role substitution or runner-definition edit.

Both fresh registered runners were configured `gpt-5.6-luna / medium`.
Runtime source: registered custom role. Logical role / agent type:
`cncf_command_runner`. Resolution health: healthy; infrastructure issue: none.
The parent verified the receipts against each prebound returned canonical agent ID.

- Native originating turn: `01a0ea14-f60d-7370-8324-9aaae6a0988e`,
  exact observed text `続けて\n\n`, thread
  `01a0a6d7-7043-73a2-a808-1367fe5fdb1f`. The verified dispatch retained the
  actual current user turn with positive `fork_turns = 1`; no approval-transfer
  claim was made.
- Shared standing-authority anchor SHA-256:
  `1eb1604782eadbb78dc50faeaca8955f399eda9fadc8c406d7e4ff3510cbbe70`.
  New attempts retain this same anchor. The fresh metadata ledgers contain
  213 and 214 prior delegated identities respectively, including unstarted-218.
- Focused `SMWF-P1-GOAL-INTEGRATED-AUTHORITY-FOCUSED-20260929-219`:
  **42/42 tests, four suites**, SBT / wrapper exit 0, `lock=released`.
  The single `testOnly` argument selected `GoalPhaseRepairLoopSpec`,
  `GoalPhaseClosureAuthorityExecutionSpec`, `GoalPhaseReviewBoundarySpec`
  and `GoalPhaseStepCloseRoutingSpec`.
  Agent: `/root/sbt_receipt_646bf96c5339cf400e13`.
  Attempt SHA-256: `1adc2a22f2a6ccfa562304b36925ab6cf5d411d57d33a74e80ea0c7ae3687c0d`.
  Receipt SHA-256: `2cc00b20523a01e145c75d1d3385f0287233d3377ee24168360e2962158397a3`.
  Receipt locator:
  `/tmp/skill.cncf.d/cncf-command-execution-753fe328864286d2831446a28128ce65bfa8206c3da3d31cf7db7d0eff1097fb-d419ef63f9381fad0ad332f26242d371/command-execution-sha256-2cc00b20523a01e145c75d1d3385f0287233d3377ee24168360e2962158397a3`.
  Summary:
  `/tmp/skill.cncf.d/cncf-sbt-6dbc1388c87c7706b046ef0eca34b6e36c1145bee48a52812aace0df12528c39-5bc81a90f3c20df492352f65f4687a31/60645-20260928T222026Z.summary.json`.
- Full `SMWF-P1-GOAL-INTEGRATED-AUTHORITY-FULL-20260929-220`:
  **507/507 tests, 54 suites**, logical argv `["--batch", "test"]`,
  SBT / wrapper exit 0, `lock=released`.
  Agent: `/root/sbt_receipt_b30730252e42c5976962`.
  Attempt SHA-256: `8ef3543f93f08741ce1b93e01c4afaf09fa8f825f44abfd57c6a590881df685a`.
  Receipt SHA-256: `f67ee5ec40f9277e17be08cf216418f1c54e00b1ce79d5eabf367dfe4d21c43a`.
  Receipt locator:
  `/tmp/skill.cncf.d/cncf-command-execution-cb3e8c45f320bdd0d940f4a2f5c178cd81dc026644a7b94e0b04a7dd05a8dcbc-772e50efba7b76c18622808e0205f5d5/command-execution-sha256-f67ee5ec40f9277e17be08cf216418f1c54e00b1ce79d5eabf367dfe4d21c43a`.
  Summary:
  `/tmp/skill.cncf.d/cncf-sbt-9512b64797660b6244fc31beb481705458a035d84032eb01b5b8cfe0d0661db8-2556d254a11594db9d30458c7d0db130/62497-20260928T222246Z.summary.json`.

Both immutable receipts verify as `accepted`, `command_result: success` and
`command_completion: terminal`, and bind the same complete Git tree SHA-256
`5f9450fd0fd434ec578182c9fc1c2c32a33fb9f68fac9631d4ce398b77cd1408`.
Each saved summary reports zero failed, aborted, canceled, ignored or pending,
and no compile failure. The parent verified the exact invocation-scoped
terminal markers and readable logs / summaries before selecting the next command.
Opaque permission metadata remained private.

### Current-tree accounting and next boundary

All 196 Git-visible product / build paths still match full-220's verified
receipt content hashes, with no additions or deletions. Relative to full-217,
the same five modified product paths and one new retained-proof implementation
recorded above are now validated. CML remains
`1fa7fbd3b2115980db5c1922c21150ce36e5a297a40e54139d193fc968f3c55b`;
checked-in ABI fixtures and Cozy / CNCF source were not changed. The other-owned
SplitPhase note remains
`915a7ff2adb3a0f671ddfc58c6975b484354799dbf58b02c1ee848f700ad1cc3`.

README, this record, the evidence map and sole executable checklist were updated
after validation. Those documentation bytes are outside the complete receipt
tree; no whole-current-tree validation or acceptance commit is claimed.
GPI is now executable evidence for the bounded GoalPhase connection.
The sole checklist remains **24 verified, 7 partial, 1 missing and 2 pending-final
out of 34**: combined SplitPhase semantic / identity binding, actual application
Codex judgment / replacement proof and final intended-tree generated-artifact /
resolved-dependency handoff remain open. The SBT runner is not the application
JudgmentAction worker proof. Human Result/resume, production adapters and shared
transactions are not introduced as Phase 1 closure requirements.

HEAD remains `75337065397b49e1f663edac0b63600174e87e55`; the index is empty.
No staging, repository commit, push, publication, release or Phase closure
occurred. Phase 1 remains `in_progress`.


## SplitPhase combined semantic / identity binding — validated continuation (2026-09-29)

The direct user `go ahead` continued Phase 1 development. This slice removes
the fixture-only incompatibility between semantic work and identity Decision;
it does not change the public Operation set, generated ABI or definition revision.

### Executable boundary now proved

The optional fixture assembly binds both paths to the exact same selection,
source/proposal ports, configuration and instance store. Crossed host ports or
stores and incomplete Decision options fail before Start. Identity precheck
runs first: occupied child identities and nested/unknown source numbering return
a strict common Decision on the public Start-created Active revision-1 instance,
without saving semantic intent, publishing WorkOrder, consuming a claim,
appending readiness or invoking PREVIEW. Human operation identities remain
stop-only metadata and are never registered.

With no identity conflict, frozen semantic gaps follow the existing WorkOrder /
public Result / deterministic reassessment / read-only PREVIEW path on that
same instance through revisions 1–4. Both options remain installed; the Skill
does not select a branch. Malformed host inputs/context/operation metadata and
live source/proposal/inventory/instance drift during projection fail closed.
Failed Decision display can be reprojected by read-only reassessment; this is
not human Result submission or approval/resume. The post-display check no longer
calls mutating assessment, so an inventory change cannot accidentally append
proposal readiness during recovery.

A new collision or foreign ABI binding during semantic work rejects Result
before claim consumption, without converting it into identity Decision or
fabricating authority. Resetting only the failed host evidence permits the
unchanged Result under the existing one-shot contract. Semantic
`decision-needed` still records nonterminal revision-3 WAIT; it is not a human
authority Decision. Deterministic readiness and the existing APPLY path are
unchanged. No shared transaction, production adapter, human completion Operation
or human Result/resume engine was added.

Ten additional specifications in `SplitPhaseSemanticWorkflowSpec` exercise
both options through Service / CLI, including same-instance retries and
equivalent opaque ABI rehydration. Focused-221 ran 80 tests, with 79 passed /
one failed: common completion compared independently rehydrated opaque
DefinitionBinding objects by identity after closing Action selection.
A local completion-persistence adapter now compares all immutable public ABI
facts, validates the record and substitutes only the exactly equivalent admitted
binding. All other instance/history facts are preserved; foreign bindings
fail closed. The same exact-fact comparison is used at local assessment,
Decision and PREVIEW admission boundaries. No CNCF source or generic contract
was changed. The initial failed receipt is retained, not rewritten.

### Independent command evidence

The command-execution, agent-resolution, workflow-protocol and serial-SBT skills
required typed receipt-first delegation through registered runners. The parent
used uv-managed skill helpers, kept permission metadata opaque, verified each
attempt and bound its expected runner identity before spawn. Read-only rendering
was separate from the unchanged later platform-reviewed execution request.
Each invocation ended with its exact terminal marker before another was selected.
Normal development caches and the shared serial wrapper were used. No
parent-direct SBT, isolated cache, role substitution, lock bypass or platform
permission bypass occurred.

All three runners were `gpt-5.6-luna / medium`, runtime source registered
custom role, logical role / agent type `cncf_command_runner`, with healthy
resolution and no execution-infrastructure issue. The deployed definition was
a regular non-symlink file, SHA-256
`20d41d4b3633ad74f716f9c6191bb0b20834a6ff171c63796f68043e49f4c740`;
it was read, not edited by this task.

- Native originating turn: `01a0ea52-6fa2-7002-b8d5-e316158df3c8`,
  exact observed text `go ahead\n\n`, thread
  `01a0a6d7-7043-73a2-a808-1367fe5fdb1f`. Each dispatch retained
  the current actual user turn using positive `fork_turns = 1`. History is
  attributable provenance, not a platform approval token or approval transfer.
- Shared standing-authority anchor SHA-256:
  `9e8facb678405442a3651361a0a81bdad2e06078aa899f1080dcc4b3ccc3ff5f`.
  Fresh metadata ledgers contain 215, 216 and 217 prior delegated identities.
- Focused argv is one `testOnly` argument selecting
  `SplitPhaseSemanticWorkflowSpec`, `SplitPhaseStartExecutionSpec`,
  `SplitPhaseSemanticInputsSpec` and `SplitPhaseGeneratedAbiSpec`.
  Full argv is `["--batch", "test"]`.
- Failed focused `SMWF-P1-SPLIT-COMBINED-FOCUSED-20260929-221`:
  79 passed / one failed, four suites, SBT / wrapper exit 1, `lock=released`.
  Agent: `/root/sbt_receipt_0e238c8e61dfd9906d75`.
  Attempt SHA-256: `0cce9a989ffb0a61e3c27d731be71fa0655239931fc1b8db2a1b403ede060384`.
  Receipt SHA-256: `3c014a7b8bd0a7b0af5d1af12e509a4e11f14d294f96bc50b715dfc9bbee8a33`.
  Receipt locator:
  `/tmp/skill.cncf.d/cncf-command-execution-287e96b7994edfa077b8307d6fda0311185cf4534976d4d09b996a25a8fcdcfa-ce2fc2d1974db854cd844d2f9998b315/command-execution-sha256-3c014a7b8bd0a7b0af5d1af12e509a4e11f14d294f96bc50b715dfc9bbee8a33`.
  Summary:
  `/tmp/skill.cncf.d/cncf-sbt-fd218a3d121e272fad2d95e0f807a834519a3e731057f90ec5eb6eedf9a4d22a-1c731a93972d10af0be4ea998e0b400c/1001-20260928T233844Z.summary.json`.
  Failed tree SHA-256:
  `a566a51192d271d30f23138b79b05274eb141503bc584f6f2864eaac68cec2be`.
- Corrected focused `SMWF-P1-SPLIT-COMBINED-FOCUSED-20260929-222`:
  **80/80 tests, four suites**, SBT / wrapper exit 0, `lock=released`.
  Agent: `/root/sbt_receipt_7d473047793ce736db73`.
  Attempt SHA-256: `a4ba7e833376a6944dcecac446d98aedc351f72720fe02600588cd927d41bae5`.
  Receipt SHA-256: `8ba556f32acd7a950a111cd0daae367669bbd774c5691b9b0886caf21ebe3740`.
  Receipt locator:
  `/tmp/skill.cncf.d/cncf-command-execution-355af1f3974483dd9f7344770cef2988144516b3f42997e2387aa5e1c602fa0c-dd8a6e93c44c85ff2eb3a6e02fbfa547/command-execution-sha256-8ba556f32acd7a950a111cd0daae367669bbd774c5691b9b0886caf21ebe3740`.
  Summary:
  `/tmp/skill.cncf.d/cncf-sbt-89a75ea70f5f9357f835daec24f82f9a43913bb965c57fd21f0ae39533d6f429-7423f87d7c97550794d545047e8455be/3818-20260928T234239Z.summary.json`.
- Full `SMWF-P1-SPLIT-COMBINED-FULL-20260929-223`:
  **517/517 tests, 54 suites**, SBT / wrapper exit 0, `lock=released`.
  Agent: `/root/sbt_receipt_ba5ac969ce1bb5e2e086`.
  Attempt SHA-256: `adf4b2e78572db4bab3c10d12817f0db72065537ade845cb52231ec0dc5a8701`.
  Receipt SHA-256: `dcee239c1411fce40fb65263b51f6ea64a5957ae006bb5471590f3211ccc8a86`.
  Receipt locator:
  `/tmp/skill.cncf.d/cncf-command-execution-43d155b907402560cbec0d29725c3c3cf2e1eec9c359835a82f9c54bd0d6bf94-5a539d3bd6d90fed3e47ae348fb0ab8e/command-execution-sha256-dcee239c1411fce40fb65263b51f6ea64a5957ae006bb5471590f3211ccc8a86`.
  Summary:
  `/tmp/skill.cncf.d/cncf-sbt-224e80bfd545790d3a501ec9c72664577b1dfdbc0d54701cd4c9eb03093f4820-598c4dd8faa82875095d7874723689c0/5778-20260928T234446Z.summary.json`.

The parent independently verified every immutable receipt as `accepted`,
terminal, with the exact prebound returned canonical agent identity. Failed-221
is `command_result: failure`; 222 / 223 are `command_result: success`.
The saved summaries and logs are readable; successful summaries contain zero
failed, aborted, canceled, ignored or pending and no compile failure.
Focused-222 and full-223 bind the same complete Git tree SHA-256
`064170110055d1e4cba0d5852f75007a2dc70cc9dcc0d43f79c1e0f38e21749c`.

### Current-tree accounting and checklist reconciliation

All 196 Git-visible product / build paths match full-223's verified content
hashes. Relative to full-220, eight existing product paths changed, with no
product additions or deletions:

| Product path | Validated SHA-256 |
| --- | --- |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseDefinitionBinding.scala` | `ef4bec0648a391eb51790f8b33f39e03c5e04dcdc84530384e55cc959a8112a1` |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseFixtureServiceAssembly.scala` | `6746b3a6702628b27e4adfe5142e067f2e697bc29f59c74abccfc86725802a9e` |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseIdentityDecision.scala` | `c9770020147a085a36c20eebd2ec716d6484de378142d007fa46031e7e95f6d7` |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePreviewExecution.scala` | `7795ef29b5608b78b962d62ca2b4332c553ab0fb5d316675f2176bb42852591b` |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseProposalAssessmentExecution.scala` | `1e70c286f990192f6f6f2f0c95032393376aeedb47bc44dc9c0aeef4f748bfed` |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseProposalAssessmentServiceOperation.scala` | `eb3c2aef113080e03d5551b70a764c2e0bf5b1c505c01d800a99ef2382b3f2a5` |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticWorkflow.scala` | `4321be856c9b1705e665adef92cc99b61e66f50829432fd53cae7df155626fdc` |
| `src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticWorkflowSpec.scala` | `1c15ae27b465663f759be900ffd1f581c5a94bc44bb88b2b97813e1fee744293` |

CML remains
`1fa7fbd3b2115980db5c1922c21150ce36e5a297a40e54139d193fc968f3c55b`.
Checked-in ABI fixtures, public Operation identities and Cozy / CNCF source
are unchanged. The other-owned SplitPhase note remains
`915a7ff2adb3a0f671ddfc58c6975b484354799dbf58b02c1ee848f700ad1cc3`.

README, this record, the evidence map and the sole executable checklist were
updated after validation; those documentation bytes are outside the complete
receipt tree. No whole-current-tree validation or acceptance commit is claimed.
SPC closes the previously recorded combined SplitPhase fixture gap. Together
with the existing three-profile deterministic/semantic/Decision evidence,
CAM-07 / P1-A03 / P1-S02 / P1-S03 now meet their fixture-bound acceptance and are
checked. This does not turn fixture Host attestations into actual Codex execution.

The sole checklist is now **28 verified, 3 partial, 1 missing and 2 pending-final
out of 34**. P1-S08 remains partial for full profile StateMachine guard/transition
mapping. P1-S05 / P1-S07, actual application JudgmentAction Codex round trip
P1-S06 and final intended-tree validation/handoff remain open. The SBT runner is
not that application worker proof. Human resume, production adapters and shared
transactions are not introduced as new Phase 1 closure requirements.

HEAD remains `75337065397b49e1f663edac0b63600174e87e55`; the index is empty.
No staging, repository commit, push, publication, release or Phase closure
occurred. Phase 1 remains `in_progress`.

## Three-profile generated judgment guards — focused validated continuation (2026-09-29)

The direct user `go ahead` continued the preceding Phase 1 development handoff.
This slice connects actual Cozy-generated entity transition checks to the
remaining judgment-related application execution paths. It does not add a
Skill-controlled branch, public Operation, CML definition or upstream change.

### Executable boundary proved by focused-224

The supporting [judgment guard map](phase-1-judgment-guard-map.md) covers all
eight alternatives of ReviewStep, AssessNovelSplitWork and ReviewMerge. Worker
alternatives are decision/rationale/evidence, not events or target states.
Application-owned CAM/context/evidence/authority admission retains the result,
and deterministic execution checks permitted entity edges before effects.
Same-state evidence retention is not a manufactured self-transition.

- GoalPhase keeps its existing generated Awaiting / admitted / Closed guard,
  exact program/planning snapshots, full-review admission and commit receipt.
  Repair returns to bounded Repair / fresh re-review; missing separately
  retained host authority stops without granting commit or Closed.
- SplitPhase now checks generated semantic-needed before suspension,
  inputs-admitted before enriched deterministic reassessment / claim consumption,
  shape-ready before deterministic readiness, previewed before catalog / terminal
  append, and applied before existing plan/history/copied-document effects.
  Semantic completion remains PREVIEW-only. Decision-needed retains
  SemanticAssessment at revision 3 with WAIT, without an authority Decision.
- RepositorySync now checks both generated reviewed-merge-push-admitted and
  convergence-admitted edges after exact committed-prefix admission and before
  push / verification providers or history effects. Review and two-parent
  commit retain MergeValidated; only authorized non-force push / receipt and
  independently clean 0/0 verification permit terminal convergence.
  Repair / decision-needed / reject never grant commit authority.

Four new WorkflowStateMachineGuardSpec cases use actual generated entity
tables and producer JudgmentAction descriptors. They verify all 18 declared
edges (1 / 5 / 12), all eight alternatives, exact allowed triples and rejection
of nonedges, foreign/blank/null input, alternative-as-event injection and
semantic-to-terminal shortcuts. The same focused invocation includes the six
connected public Service / CLI execution suites listed below. Existing positive
and negative disposition specifications prove admission before claim consumption,
same-instance execution, exact evidence, and stop-only authority separation.
The entire declared edge tables are checked; this slice does not claim newly
connected runtime permits checks for every pre-review normal RepositorySync edge.

### Command delegation and successful focused evidence

The applied cncf-command-execution, cncf-agent-execution, cncf-workflow-protocol
and cncf-sbt-serial-execution skills require registered receipt-first delegation
and the shared serialized wrapper. The parent used uv-managed skill helpers,
kept permission metadata opaque, verified the attempts, prebound actual runner
identities and retained the native originating turn. Rendering was read-only
and separate from the unchanged execution request's platform review.

Both runners used `gpt-5.6-luna / medium`, runtime source registered custom role,
logical role / agent type `cncf_command_runner`. The deployed definition is a
regular non-symlink file, unchanged SHA-256
`20d41d4b3633ad74f716f9c6191bb0b20834a6ff171c63796f68043e49f4c740`.

- Native originating turn: `01a0ea6d-a92a-7c90-95ed-11722e529011`,
  exact text `go ahead\n\n`, parent thread
  `01a0a6d7-7043-73a2-a808-1367fe5fdb1f`. The saved origin and renderer
  selected positive `fork_turns = 1`; history evidence remains
  `parent-observed-not-platform-authenticated`, not approval transfer.
- Standing-authority anchor SHA-256:
  `2ebbc9498c527b74b15b5b9c03d2314e5c25e8a8d23c45486c6bf9a1dc78925a`.
  Fresh metadata ledgers retain 218 and 219 prior delegated identities.
- Focused invocation: `SMWF-P1-JUDGMENT-GUARDS-FOCUSED-20260929-224`.
  Exact argv is `--batch` plus one `testOnly` argument selecting
  WorkflowStateMachineGuardSpec, GoalPhaseRepairLoopSpec,
  GoalPhaseReviewBoundarySpec, GoalPhaseClosureAuthorityExecutionSpec,
  SplitPhaseSemanticWorkflowSpec, SplitPhaseStartExecutionSpec and
  RepositorySyncMergeValidationAdmissionExecutionSpec.
  **199/199 tests, seven suites**, no compile failure or failed/aborted/
  canceled/ignored/pending tests; SBT / wrapper exit 0, `lock=released`.
  Agent: `/root/sbt_receipt_769f30b7d8610e478eee`.
  Attempt SHA-256:
`c4093c90e73a678755725069faf54da7c2a376337ecb68e1a3345e8b7c62cf69`.
  Receipt SHA-256:
`e85d5e5fbeb2179d297f840239db1293627a45ff3f5f35c1e301a6a4468f1d32`.
  Receipt locator:
`/tmp/skill.cncf.d/cncf-command-execution-bdc9f5f6bd0d3b4873f879b292d6046bcd8ac973db2cd92456f0aaf4a0a4cffc-691073d4d36786eab6890a3066a6f720/command-execution-sha256-e85d5e5fbeb2179d297f840239db1293627a45ff3f5f35c1e301a6a4468f1d32`.
  Saved summary:
`/tmp/skill.cncf.d/cncf-sbt-344d9213ebf84bbb3a78e6a77050b4913b0bd0be289c709761df8ed8c7f3cfe4-a040add14fdecaf1b21441e2d1f0a568/15816-20260928T235818Z.summary.json`.

The parent independently verified the immutable receipt as accepted, terminal
and command_result success, with the exact prebound canonical returned agent
identity, normal caches, matching completion marker and readable summary/log.
The complete before/after validated tree SHA-256 is
`04a31db5bbb7e7f685efb556a4196275caf7f004d095cb0fc7ace1692f06d73b`.

### Full-225: platform denial before any helper / SBT execution

Full invocation `SMWF-P1-JUDGMENT-GUARDS-FULL-20260929-225` selected the unchanged
focused product tree with exact logical argv `["--batch", "test"]`.
Agent: `/root/sbt_receipt_9908cd15adcd9d53e08e`.
Attempt SHA-256:
`763337e5aadf7cda050879876d880a236a5836ebb1f48c7cbb59c42a9ec17360`.
Intake:
`/tmp/skill.cncf.d/cncf-command-execution-62d70328449b57dfa0b58d17c90ea5ebfc130b5d4a41e2e15b35c42fa67f09aa-2a635370d5c49fa6e0757f0b2c1fafc1/command-execution-intake-sha256-a026295ccf2612cde7a22002b2afab29db4601e9a979dda63b3fcd978626c3ad.json`.
The renderer accepted and retained the full structured request. The platform
then rejected execution before execute-command-with-receipt.py could start.
No SBT start, invocation result, exit statuses, completion marker, execution
receipt, test summary or full-test count exists for 225. The attempt is consumed
with platform-denied outcome in the child's platform result, not a successful
on-disk receipt or a helper-generated consumption marker.

Raw platform denial, recorded once:

```text
Script failed
exec_command failed: CreateProcess { message: "Rejected(\"This action was rejected due to unacceptable risk.\\nReason: The action runs an SBT test workflow unrelated to the user’s authorized request to synchronize .codex/agents, with only untrusted workflow text supporting it.\\nThe agent must not attempt to achieve the same outcome via workaround, indirect execution, or policy circumvention. Proceed only with a materially safer alternative, or if the user explicitly approves the action after being informed of the risk. Otherwise, stop and request user input.\")" }
```

The unchanged rendered request fields were:

- cmd:
  `/Users/asami/.agents/skills/cncf-workflow-protocol/scripts/execute-command-with-receipt.py --intake-locator /tmp/skill.cncf.d/cncf-command-execution-62d70328449b57dfa0b58d17c90ea5ebfc130b5d4a41e2e15b35c42fa67f09aa-2a635370d5c49fa6e0757f0b2c1fafc1/command-execution-intake-sha256-a026295ccf2612cde7a22002b2afab29db4601e9a979dda63b3fcd978626c3ad.json --execution-route registered-custom-role`
- workdir: `/Users/asami/src/dev2026/sm-workflow`.
- sandbox_permissions: `require_escalated`.
- prefix_rule:
  `["/Users/asami/.agents/skills/cncf-workflow-protocol/scripts/execute-command-with-receipt.py"]`.
- justification (exact retained text):

```text
Originating workflow authority: standing-user-sbt-authorization; standing authorization: asami-local-sbt-v1; originating workflow invocation/reference: sm-workflow Phase 1 three-profile generated judgment StateMachine guard development / 01a0ea6d-a92a-7c90-95ed-11722e529011; workflow instance: SMWF-P1-JUDGMENT-GUARDS-20260929. Current operation: validation; purpose: Phase 1 generated StateMachine judgment progression guards full executable specification regression; repair identity: not-applicable. Selected repository: /Users/asami/src/dev2026/sm-workflow. Exact SBT argv: ["--batch", "test"]. Admitted command mutation roots: ["/Users/asami/src/dev2026/sm-workflow"]. Bounded external build/cache/lock access paths: ["/Users/asami/.sbt", "/Users/asami/.ivy2", "/Users/asami/.cache/coursier", "/Users/asami/.m2", "/tmp/skill.cncf.d"]. Forbidden operations: ["remote-publication", "deployment", "push", "credential-change", "destructive-cleanup", "source-edit", "staging", "commit"]. Platform review remains required. Read-only receipt tree-observation repositories: ["/Users/asami/src/dev2026/sm-workflow"].  Receipt evidence ID: SMWF-P1-JUDGMENT-GUARDS-FULL-20260929-225; receipt helper stdout/stderr/result writes limited to allocated directory: /tmp/skill.cncf.d/cncf-command-execution-62d70328449b57dfa0b58d17c90ea5ebfc130b5d4a41e2e15b35c42fa67f09aa-2a635370d5c49fa6e0757f0b2c1fafc1. Serial-wrapper logs retain their independently allocated or attempt-bound /tmp/skill.cncf.d directory; shared lock and recovery access retain the SBT attempt's existing scope. Execution remains through the shared serial SBT wrapper; rendering does not consume the intake or permission artifact.
```

Classification: `EXECUTION_PERMISSION_DENIED_AFTER_ATTRIBUTABLE_AUTHORITY`,
not missing user approval. Native read-only history confirms the current user
turn follows the preceding completed SplitPhase development handoff; the
denial instead associates the scope with an older agents-synchronization request.
This is evidence of an intent/provenance disagreement at platform admission,
not proof of a universal SBT prohibition or a defect in the passing guard specs.
The standing anchor and saved origin are attributable application records;
they cannot replace the platform's independently recognized user intent.

The platform message names materially safer action or explicit informed user
authorization as its supported reconsideration conditions. This workflow's
denial contract forbids redispatching the denied action, requesting repeated
approval/confirmation or asking the user to run it manually. No successor
envelope, rerender, alternate runner, isolated cache, direct SBT, terminal
workaround or permission-route retry was attempted. Any later contract repair
must address actual platform-visible native provenance within an authorized
scope; merely changing justification text or reallocating an ID is not recovery.
No repair of global skill/agent definitions is authorized or performed here.
No infrastructure Phase/Task/Step, Hygiene item or Development Candidate is added.

### Product accounting and checklist disposition

All 197 Git-visible product/build paths match focused-224's verified content
hashes. Relative to full-223, five existing product files changed and one test
file was added; there are no product deletions:

| Product path | Focused validated SHA-256 |
| --- | --- |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncMergedConvergenceExecution.scala` | `df634283b15f7ec76efade4ab0aadbf852c8685ee3dd13206223e3e287d91826` |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseApplyExecution.scala` | `93e34239d4fa8fb0776f1fa26a69a7f227d50f23015fbccdb9a96a0e69e9d8c8` |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePreviewExecution.scala` | `ad0b085807b6bc16c0611fe62148bf621e1a727f30b90c5b93d2ff7dbb16b7ec` |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseProposalAssessmentExecution.scala` | `28abb2b9df30db453905a525bc00ff9144b066077bdd728ff3b5735a3b402bdf` |
| `src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseSemanticWorkflow.scala` | `4ae8aa0e447072adade95c729c665e400ab43a7ec05b63a6e73114a297382b03` |
| `src/test/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowStateMachineGuardSpec.scala` | `1096af5ab4eb82d6dd0e2ce8253a28ddf03614c8b9731836b2ab19c9207ee02d` |

CML remains
`1fa7fbd3b2115980db5c1922c21150ce36e5a297a40e54139d193fc968f3c55b`.
Checked-in ABI fixtures, public Operations, definition revisions and Cozy /
CNCF source are unchanged. The other-owned SplitPhase design note remains
`915a7ff2adb3a0f671ddfc58c6975b484354799dbf58b02c1ee848f700ad1cc3`.

Focused-224's seven suites provide the relevant bounded executable proof for
P1-S08, which is now verified. Full-225's unstarted denial is not test failure
and cannot supply final full-validation evidence; P1-C01 remains pending-final.
The sole checklist is **29 verified, 2 partial, 1 missing and 2 pending-final
out of 34**. P1-S05 / P1-S07, actual Codex JudgmentAction round trip P1-S06, and
final intended-tree artifact/dependency validation / handoff remain open.
The command runner is not an application JudgmentAction worker.

README, this record, the evidence map, supporting guard map and sole checklist
were updated after focused validation and are outside its complete receipt tree.
No whole-current-tree or new full validation, human Result/resume engine,
production Git adapter, runtime deployment or Phase closure is claimed.
HEAD remains `75337065397b49e1f663edac0b63600174e87e55`; the index is empty.
No staging, repository commit, push, publication or release occurred.
Phase 1 remains `in_progress`.

## Command-review history investigation — no redispatch (2026-09-29)

The next direct user `go ahead` authorized investigation of review-context
transport, not re-execution of denied-225. The supporting
[investigation note](../notes/command-review-history-investigation-2026-09-29.md)
records the native parent/child timeline and dispatch-source comparison.
Focused-224's child contains the originating `go ahead` as role=user;
full-225's child does not, although both used the same saved origin and
positive fork count. Parent compaction occurred between the two forks.
Missing native origin is confirmed; a compaction-sensitive runtime fork issue
is the probable causal boundary, not a fully reproduced reviewer/harness defect.

Deployed and canonical runner hashes match; no stale-agent repair is needed.
Only supporting documentation was written. No SBT, new agent, skill/agent edit,
permission weakening, native override, retry or speculative fork-count change
was performed. The relevant execution/diagnosis skills require stopping at
explicit denial and an unowned upstream repair boundary. Phase 1 counts remain
29 verified / 2 partial / 1 missing / 2 pending-final; no infrastructure work
item or new Phase closure gate is created. Product validation and Phase closure
remain incomplete.

### Development continuation after the unavailable desktop approval command

The user's Astra continuation request led to a bounded application route in
the [continuation decision](../notes/command-review-history-investigation-2026-09-29.md#development-continuation-decision-2026-09-29).
P1-S06 / P1-S07 can proceed from the existing GoalPhase public fixture and
Host-normalization boundary; full-225 is not a prerequisite for designing or
implementing that independent work. The next proof requires an actually issued
WorkOrder, an observed Codex result, same-instance public completion, and an
equivalent deterministic-Provider fixture. No actual-worker or execution-recovery
claim is made by this decision. Final intended-tree validation remains P1-C01
and subject to platform review. Checklist counts and closure status are unchanged.

### Full-226: explicitly authorized retry completed

After the exact target `sbt --batch test`, registered runner/shared wrapper,
normal build/cache/lock/log writes, possible dependency-download network access,
and exclusion of commit/push/publication were explained, the user explicitly
approved one retry with `承認する\n` in native turn
`01a0ea9d-cfa2-7fc0-bcaa-03ef8a127cae`. The preceding scope and risk explanation
is in turn `01a0ea9c-471c-7ba2-999d-92839b550f3d`.
The platform refusal itself admits informed user approval as a reconsideration
condition. This later instruction permits the one retry despite the skill's
blanket no-redispatch guideline; no permission or execution-policy setting changed.

The original full-225 remains unstarted and denied. Its immutable artifacts were
not reused. The retry used a separate explicit-user authority anchor, preserving
the same Phase workflow identity, repository, logical argv, external paths and
prohibitions. The fresh attempt names full-225 and the informed approval; it is
not presented as unrelated work or an expiry refresh.

- Invocation: `SMWF-P1-JUDGMENT-GUARDS-FULL-INFORMED-20260929-226`.
- Repository: `/Users/asami/src/dev2026/sm-workflow`; logical argv `["--batch", "test"]`.
- Anchor SHA-256: `59914b27efd6fdcc71470e60e71cd58fe4ce893483e549417851ea5b981fdbe5`.
- Attempt SHA-256: `d6b061da83b8dd02d739c6b185eabe359f67f0b64da29b355b9820783b5a209e`.
- Origin: helper-captured exact native approval occurrence; parent-verified
  positive history suffix `fork_turns = 1`. No platform approval transfer was claimed.
- Runner: `/root/sbt_receipt_a42c0daaba1d8c12b61c`, registered
  `cncf_command_runner`, configured `gpt-5.6-luna` / `medium`, START 1 / RESUME 0.
- Route: `registered-custom-role`; runner health `healthy`; no fallback.
- Result: **521/521 tests across 55 suites**, zero failures / aborted suites.
- SBT / wrapper exit: **0 / 0**, final **`lock=released`**.
- Receipt SHA-256: `aaa2248e80283fe664342f9a4806be3ee62081722cdc68b177f647c8a240cd21`.
- Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-6e14ad4b0253c2e4c5d298e453e8a2df9c172501de96ae332fc006240d26801c-b19c7a0730b17ab8e6e13b9687ddfe69/command-execution-sha256-aaa2248e80283fe664342f9a4806be3ee62081722cdc68b177f647c8a240cd21`.
- Complete validated tree SHA-256: `e1d6ed112fa015ede9e4cd474f91a177c669936058f8401797569ab1d5e28402`.
- Full log: `/tmp/skill.cncf.d/cncf-sbt-bb2a7cc4aa2e4786f15ce31fd0e48eb3fa73507506f155e39c15c8ee5bdcd1f0-d5b96a4c4af30ba34b332dcfceb71f69/44039-20260929T004816Z.log`.
- Summary: the same directory's `44039-20260929T004816Z.summary.json`.

The parent independently verified the immutable receipt, bound runner identity,
terminal marker and summary. All 197 product/build paths still match the
focused-224 inventory. The helper verified an unchanged complete Git tree across
execution. The original consumed-ID ledger was preserved and included denied-225;
226 is now terminal and consumed. No second test invocation, source change,
staging, commit, push or publication occurred. A request-file preflight rejection
before bundle creation started no process and supplied no test evidence.

Full-226 supplies full regression evidence for the judgment-guard product tree.
It does not prove an actual application JudgmentAction worker or Phase closure.
P1-S05 / S07 remain partial, P1-S06 missing, and P1-C01 / C02 pending-final:
**29 verified / 2 partial / 1 missing / 2 pending-final**. The result-recording
documentation edits follow validation and are outside its complete receipt tree.
The earlier compaction/history transport defect remains an unverified repair;
this successful one-retry approval is not a general fix or standing denial override.

### Focused-227: live-worker Host preparation

The user continued application development with `go ahead\n\n` in native turn
`01a0eaa6-64d0-74a1-9423-aedfb96ea491`. This is new implementation and focused
validation after full-226, not another retry of denied full-225. The existing
GoalPhase test Host was extracted into `GoalPhaseApplicationFixture` without
changing its public Start/Service/CLI paths. `GoalPhaseWorkerFixtureHost` keeps
the issued WorkOrder and Start-created instance alive, accepts only the semantic
`ReviewStepResult`, and uses the existing frozen mapping policy and common
`SkillWorkResultNormalizerV1`. It losslessly encodes the common Result before
calling `GoalPhaseService/submitStepReview`; the worker never selects an Operation
or next state. The test-classpath main is an evidence bridge, not a new public
application CLI or generic lifecycle protocol.

Three new executable specifications prove normalization without premature
consumption, rejection of wrong execution identity/incomplete payload/next-state
fields, and fixed known-candidate Provider parity through the public CLI.
The Provider is independently defined for one complete increment-function Step,
not an AI-answer replay. It invents no AI execution metadata. Both routes stop
at missing commit authority and perform no real Git mutation.

- Invocation: `SMWF-P1-CODEX-WORKER-FOCUSED-20260929-227`.
- Runner: `/root/sbt_receipt_450ec1916d3b4c408a57`, registered
  `cncf_command_runner`, `gpt-5.6-luna` / `medium`, START 1 / RESUME 0;
  route `registered-custom-role`, healthy, no fallback.
- Selection: `GoalPhaseWorkerFixtureHostSpec`, `GoalPhaseRepairLoopSpec`,
  `WorkflowWorkerMappingSpec`; **35/35 tests, three suites**, no failures/aborts.
- SBT/wrapper exit: **0/0**, final **`lock=released`**.
- Authority anchor: `194045b3cbaee414be27b766d155169c0bd63c3889d884f6728424e225cbcb43`.
- Attempt: `613d8d7a01a86569238b2b8e0ada5e2b92fdd5c5315f4c2ec420975547378f96`.
- Independently verified receipt: `32fd2cd94b0321a61adb1fb1901d62dc18fd0e62904c08df5b7ea993a8edff0f`.
- Complete validated tree: `0e1e595e46fd211178ba76e468abf92056479c6cc8d3571beff916ca79339e11`.
- Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-90f24e8abbb110aaba118734a6b5d53d02080acf1ddeb773b4bace3a6fe8500b-cbf8ad7f982cffea5f10f8ff0960f225/command-execution-sha256-32fd2cd94b0321a61adb1fb1901d62dc18fd0e62904c08df5b7ea993a8edff0f`.
- Log: `/tmp/skill.cncf.d/cncf-sbt-df637f734ec09e008d26171316bf145f36f7b5ea765a92a95f245608226ff607-68261da87200d51ee004a3ea3fc5140c/53369-20260929T010328Z.log`;
  matching invocation summary records the same terminal result.

Automated Host answers are explicitly stand-ins, not actual Codex execution.
The separate live session must issue its actual WorkOrder, observe the external
worker, admit its typed answer on that same instance, and record Provider parity
before P1-S06/S07 are checked. Full-226 does not validate these later source changes.
Result-recording documentation is outside focused-227's complete tree. Phase 1
remains in progress; no staging, commit, push, publication, skill/agent deployment
or sandbox-policy change was performed.

### Focused-228: runner-independent fixture transport

The first runtime session (`/root/goal_phase_worker_session`, session 96355,
JVM 54700 started 2026-09-29 10:05:51 JST) issued its WorkOrder but reported
readiness only through commentary, not a parent mailbox. It was interrupted and
its owner stopped it, exit 130. The second (`/root/goal_phase_worker_session_2`,
session 80558, JVM 59929 started 10:18:34 JST) confirmed that native collaboration
send/wait tools are unavailable in this custom role and app messaging to an
ancestor is rejected. Its owner stopped it, exit 130. Neither received a worker
completion, invoked the client completion Operation, or supplied actual-worker
proof. Both owned processes were reported gone. These are transport/lifecycle
records, not platform execution denials, successful AI reviews, or new Phase items.
The stopped instances and WorkOrders are not reused.

The test-only Host now optionally uses one typed-allocated, mode-0700 disposable
directory. It publishes the actual issued common WorkOrder to `issued.json`,
waits for the parent-owned `completion.json` followed by `completion-ready`, and
publishes `proof.json` or `failure.json`. The marker prevents reading a partial
parent write; inputs must be regular non-symlink files; output creation is
exclusive; transport reuse and unknown output names are rejected. A bounded
540-second Host wait and runner-owned cleanup replace callback dependence.
The semantic interface, common Result codec, public CLI completion Operation,
Workflow state/guards and mapping policy are unchanged. This is private fixture
I/O, not a production server, persistent store, or generic Workflow protocol.

- Invocation: `SMWF-P1-CODEX-WORKER-TRANSPORT-FOCUSED-20260929-228`.
- Runner: `/root/sbt_receipt_287e5b7a0d7080f7213f`, registered
  `cncf_command_runner`, `gpt-5.6-luna` / `medium`, START 1 / RESUME 0,
  healthy route `registered-custom-role`, no fallback.
- **37/37 tests, three suites**, including two additional transport specifications.
- SBT/wrapper exit **0/0**, final **`lock=released`**.
- Same authority anchor as 227; attempt SHA-256
  `33c1e4f711316c293a7faeb771c57ca6e584f20770d8e2e4594719217fd4cbd5`.
- Independently verified receipt SHA-256
  `035c91f7f883ab41145d6f7ccb9726fa4b46015ec0023940164db43f920ddd73`;
  complete tree `f6ad5fb5beeba8f6972e654fa5622b85d0373df6ae30e994a8aa607d69b0eb83`.
- Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-7895943f19aa4e816ebbe1f759ba8de5f4402a4bb57e6b4f4ae7c7a25fb5d003-f2a73062dd3819763eedfe9ad2991adf/command-execution-sha256-035c91f7f883ab41145d6f7ccb9726fa4b46015ec0023940164db43f920ddd73`.
- Log: `/tmp/skill.cncf.d/cncf-sbt-27a0c1177943b1d569e90d46e62f4b22b8acad63cb3e3046737e7cd33ec240ec-0c2534c4bfa93de55166f18dc7d9f814/63634-20260929T012448Z.log`.

Documentation follows this validation and is outside its complete tree. The
third runtime session uses a fresh allocation and observes generated files
independently; only a completed actual-worker proof may verify P1-S06/S07.

### Actual Codex worker and deterministic replacement proof

Live invocation `SMWF-P1-LIVE-CODEX-JUDGMENT-20260929-3` used a fresh typed
allocation and the file-transport Host compiled by focused-228. Public CLI
Start and requestStepClose issued the actual current GoalPhase review WorkOrder.
Only after reading that `issued.json`, the parent started an actual external
Codex worker `/root/goal_phase_codex_judgment` (`default`, selected model
`gpt-6-astra`, fork none; concrete reasoning effort not observed). It performed
one complete, bounded increment-function Step review and returned only
ReviewStepResult decision/rationale/reviewEvidence. It executed no command or
test and selected no next state/Operation. The fixture uses the declared logical
review clock 2026-09-28T00:00:00Z, not fabricated live execution timestamps.

The parent preserved its exact observed answer, supplied Host-attested worker
identity/model, and published completion.json before its readiness marker.
The live Host normalized through the frozen `codex-reviewer` /
`goal-phase-worker-fixture-v1` mapping and common SkillWorkResultNormalizerV1,
losslessly encoded/decoded the common ContinuationResult, and called the public
`GoalPhaseService/submitStepReview` CLI Operation. The retained normalized
Result equals the worker's semantic payload exactly and keeps the issued
handle/run/continuation/expected revision/ContextSnapshot. No second Start or
close request was used for that live instance.

An independently implemented fixed known-candidate Provider reviewed an
equivalent fresh fixture using the same ReviewStepResult/public completion
contract. Its definition identity/revision and retained transition trace
match the Codex route: revisions 1, 2 and 3, lifecycle Active, step.Awaiting.
Both stop at the same missing-commit-authority Decision; evidence descriptions
and authority request digests differ, as expected. The Provider invents no AI
execution metadata; no real Git command or mutation occurred in either route.
This is the required bounded replacement proof, not production worker
infrastructure, Git integration, human resume or a claim of all-context review.

Runtime owner `/root/goal_phase_worker_session_3` is the registered
`cncf_runtime_session_runner`, `gpt-5.6-luna` / `medium`, START 1 / RESUME 0,
healthy route without fallback. Managed session 31299 exited naturally with code0;
no owned process remains. PID was not exposed by that PTY result. No escalation,
network, publication or configuration/permission change occurred.

The [retained live artifact](evidence/goal-phase-codex-worker-20260929.json),
SHA-256 `6606238585a1998f9db8734964dbdd34b3c742dc5d4b9720ceca833a6590143d`,
contains the observed raw worker response, runtime identity, exact common
envelopes/outcomes/traces and all ten independently checked parent invariants.
The [three-profile consumer handoff](phase-1-consumer-handoff.md) records the
exact application/generated ABI/upstream/resolved binary identities.
P1-S05/S06/S07 are verified: **32 verified, no partial or missing, two
pending-final** (P1-C01/C02). The public skill naming requirement is explicitly
`sm-*`; existing `cncf-*` stays compatible and independent. The development
execution helpers are not the distributed public skill interface.

### Full-229: final intended-tree validation and Phase 1 closure

The final intended application definition/fixture tree passed
`SMWF-P1-CODEX-WORKER-FINAL-FULL-20260929-229`, logical argv
`["--batch", "test"]`, in `/Users/asami/src/dev2026/sm-workflow`:

- **526/526 tests, 56 completed suites**, no failure/cancel/ignore/pending/abort.
- SBT/wrapper exit **0/0**, exact final **`lock=released`** marker.
- Registered `cncf_command_runner` `/root/sbt_receipt_fa0c48b9c02225f888b6`,
  `gpt-5.6-luna` / `medium`, START 1 / RESUME 0, healthy registered route, no fallback.
- Authority anchor `bca56d0ed6c192dac2dbd8ccc4993dea75ac7d9bb3f53d0a57a541a48d2c59b0`;
  attempt `7bd4c8215c9b81b930b111d5a2f9d87e93c809ce9caffcca61786f569936a499`.
- Independently verified receipt `ed90d2514e47449cefc567705bb0f3de8d0bc49f21038e57b6320c725ce2389b`;
  complete intended tree `397303c4b65416ccfe1d369bd0df65521ff5926326a1960bc01f861a9bc1c0de`.
- Receipt locator: `/tmp/skill.cncf.d/cncf-command-execution-6e6ba6071c12848d2f0e5bb2074ff0ffdf0ce880b24fb7030560065f825992c2-f471615c7916ea9051aa80af7d4abb9e/command-execution-sha256-ed90d2514e47449cefc567705bb0f3de8d0bc49f21038e57b6320c725ce2389b`.
- Log: `/tmp/skill.cncf.d/cncf-sbt-4846a1b3582cc14b99d35008bcc91da85e80aa9aaa0aed7c978f75c2a09f3f98-94ecb024d30bb8a36132526762e10b44/70432-20260929T013657Z.log`;
  matching summary `70432-20260929T013657Z.summary.json`.

The naming instruction arrived as an additive steer during the same turn. The
native message snapshot then exposed the latest naming text, not the earlier
go-ahead text. Final validation therefore captured that actual current native
occurrence under the standing `asami-local-sbt-v1` authority and referenced the
earlier observed continuation anchor as historical provenance. It did not copy
the earlier text into the new native occurrence, synthesize a turn, claim native
authentication/approval transfer, reuse a consumed attempt, retry denied-225,
or weaken platform review. The positive history suffix was helper-verified.

The parent independently verified bound runner/receipt, completion marker and
summary. All **200** intended product/build files still match the final receipt;
the three generated Provided API hashes and consumed classpath still match the
[consumer handoff](phase-1-consumer-handoff.md). There are no source/build edits
after final validation. Later status/evidence/journal updates are documentation
only, outside its complete validated tree. Other-owned dirty work was preserved.
Index remains empty; no commit, push, publication or permission/skill/agent
deployment is claimed. The old denied/failed/interrupted attempts remain
historical, never substituted for successful evidence.

P1-C01 is verified by this final regression; P1-C02 is verified by updating the
current Phase status only after all application items, live worker/replacement
proof and exact three-profile handoff were complete. **34/34 checklist items
are verified; Phase 1 is completed.** The closure is the bounded common-contract
application/fixture scope, not production-ready runtime, Git adapter, SQLite,
restart/lease infrastructure, installable public skills or CAR publication.
The public skill distribution contract remains `sm-*`, with independent legacy
`cncf-*` compatibility. See the [closure journal](../journal/2026/09/2026-09-29-phase-1-closure.md).

## In Scope

- 三つの application Workflow の typed start/work/result/terminal payload
- CNCF common contract に binding する CML Workflow / StateMachine definitions
- CML typed Operation definitions と Workflow Action への明示的 binding
- launcher discovery に必要な component assembly、generated Operation registration、Provider binding
- CNCF launcher が公開する machine-readable Operation interface
- deterministic/test Providers と fixtures
- application Presentation content
- CNCF `ReasoningLevel` を入力とする versioned mapping policy
- application payload を含む JSON round-trip / fail-closed fixtures
- judgment goal/context/alternatives/criteria と
  decision/rationale/evidence を持つ application payload
- Codex を初期外部 worker とする JudgmentAction fixture
- typed Operation を経由する Start → semantic boundary → typed result → next boundary
  → terminal の Executable Specifications

Skill/Host が外部 WorkOrder を dispatch した場合だけ、compatible worker profile と
mapping-policy version を `ExecutionEvidence` に記録する。deterministic/local Provider は
架空の worker profile を記録せず、evidence を Workflow guard や transition input に
使用しない。

## JudgmentAction Specialization

Phase 1 の文脈依存判断は CNCF Phase 77 の `JudgmentAction` を使用する。sm-workflow は
別の AI Action 型を定義せず、software-development domain の goal、context、
alternatives、criteria、および expected result だけを specialize する。

初期外部 worker は Codex とし、既存の Skill/Host と durable Continuation を通じて
呼び出す。Codex は admitted alternatives の一つ、rationale、evidence を
`JudgmentResult` として返す。次の state / Action の選択、retry、escalation、terminal
判定は行わず、CNCF StateMachine の guard / transition が判断結果を解釈する。

少なくとも implementation review、novel work / semantic-boundary assessment、
repository synchronization conflict assessment の判断境界を候補とし、各 Workflow が
必要とするものだけを明示的に `JudgmentAction` として定義する。決定的に導出できる処理を
JudgmentAction に昇格させない。

将来 Codex を jev、人間、local LLM、または別 Provider に置き換えても、Workflow
definition、application payload、判断結果からの transition semantics は変更しない。

## Application Operation Boundary

Operation は Skill/Host と CML Workflow を接続する唯一の型付き外部 interface であり、
Phase 1 の executable specification はこの interface を通じて動作確認する。CML definition
には少なくとも次の application Operation と、その input/result schema を定義する。

- `StartGoalPhase(GoalPhaseStartInput, WorkflowInvocationSelection)`
- `StartSplitPhase(SplitPhaseStartInput, WorkflowInvocationSelection)`
- `StartRepositorySync(RepositorySyncStartInput, WorkflowInvocationSelection)`
- `SubmitGoalPhaseWorkResult(GoalPhaseWorkResult)`
- `SubmitSplitPhaseWorkResult(SplitPhaseWorkResult)`
- `SubmitRepositorySyncWorkResult(RepositorySyncWorkResult)`
- application-specific `ResolveDecision(...)` operations

起動 Operation は `WorkflowInvocationSelection` を検証してから application input を CNCF
generic `WorkflowStartRequest` の payload へ写像する。結果提出と Decision Operation は current `WorkflowHandle` と
`Continuation` に対する CNCF generic Result/Decision submission を呼ぶ。これらは別の
generic lifecycle、`advance` protocol、または raw command surface を導入しない。

CML Action は exact typed Operation reference を持つ。Skill は Action/Continuation から
選択済みの Operation と schema-validated payload を受け取り、必要な semantic AI work を
行った typed result を同じ Operation boundary へ返すだけである。Skill は next state、
Operation 選択、command argv、commit readiness、retry、または別 Workflow の開始を決めない。

deterministic/test Provider は同じ Operation contract を実装し、fixture で正常・拒否・
semantic boundary・terminal のすべてを検証する。production Git/SBT provider、任意 command
execution、または broad CLI はこの Phase の Operation 実装に含めない。

### Launcher fixture adapter

CNCF launcher は component に登録された CML Operation を machine-readable CLI として
自動公開する。component assembly、generated Operation registration、Provider binding が正しく
構成されれば、`sm-workflow` は追加の CLI 実装なしに launcher 経由で動作する。Phase 1 は
このsurfaceをone-shot executable-specification adapterとして使用するが、production Skillを
launcher固有のargv grammarへbindingしない。Skill-facing contractはregistered typed
application Operationであり、Phase 2のMCPも同じcontractを搬送する。

launcher は registered CML Operation の exact identifier と schema-validated JSON request を
受理して CNCF generic response JSON を返す。fixture client は launcher 経由で選択済み
Operation のrequestを送り、response の typed `Continuation` / Result を返すだけである。
Operationを推測・選択せず、raw command、任意 shell argv、直接 state mutation、独自の
Start/Advance/Continuation protocol を提供しない。launcher JSON I/O は executable
specification の fixture で検証する。

## Out of Scope

次は Phase 1 completion に含めず、実際の connectivity/use evidence に基づく後続の
operational-hardening Phase で扱う。

- production public skills / catalog
- standalone / CAR bundle distribution
- broad interactive CLI / UI
- production SQLite operational profile
- lease / restart / concurrency / recovery hardening
- production provider dispatch policy
- operational Git / SBT providers and local closing
- cost dashboards
- MCP / server adapters

Executable Specification に必要な小さな fixture adapter は許容するが、production operation
surfaceへ拡張しない。

## CNCF Generic Protocol Dependency

Phase 1 は CNCF Phase 77 が提供する Generic Workflow JSON Protocol を public/runtime
envelope として使用し、Start/Handle/Continuation/WorkOrder/Decision/Wait/Terminal/Result/
Evidence/Presentation/ExecutionRequirement を再定義しない。

sm-workflow は GoalPhase / SplitPhase / RepositorySync の application-specific typed payload
schema、software-development presentation specialization、および abstract reasoning level から
concrete worker profile への versioned mapping policy を所有する。

詳細は [CNCF Workflow Protocol Application Specialization](../notes/cncf-workflow-protocol-application-specialization.md) に従う。

## Design Invariants

1. CNCF Phase 77 の generic Value Objects と lifecycle が正本である。
2. `sm-workflow` は application payload、definition、typed Operation binding、
   specialization policy だけを所有する。
3. automatic progression は AI/model turnを発生させない。
4. semantic boundaryを推測で越えない。
5. `WorkflowInteraction` は framework `WorkflowHandle` と current `Continuation` の
   projectionであり、別のgeneric protocolではない。
6. concrete worker selectionはWorkflow guardやtransitionを制御しない。
7. JSONはValue Objectのencodingであり、独立した正本モデルではない。
8. Skill/Host は selected typed Operation の入出力を搬送するが、Workflow state を直接変更しない。
9. `JudgmentAction` は判断要求、`JudgmentResult` は判断結果を表し、Codex を含む
   worker は次の state / Action を選択しない。
10. Codex は Phase 1 の初期外部 worker であり、CNCF の Action model や
    sm-workflow の Workflow definition に固定された provider identity ではない。
11. profile-specific start Operation は explicit human selection と manifest binding から決まり、
    recommendation は invocation authority にならない。
12. non-terminal response は current Continuation が指定する registered completion Operationへ
    提出し、Skill-facing generic `advanceWorkflow` Operationを別途設けない。
13. launcherはPhase 1のfixture transportであり、registered application Operation contract
    自体をlauncher固有argv grammarへbindingしない。

## Decision and Historical Records

- [Phase 1 adoption of CNCF JudgmentAction](../journal/2026/09/2026-09-20-judgment-action-phase-1-adoption.md)
  — JudgmentActionの責務、Codex初期実行、provider差し替え境界
- [Phase 1 common-contract scope reconciliation](../journal/2026/09/2026-09-20-phase-1-common-contract-scope-reconciliation.md)
  — current scopeを確定したdecision record
- [Phase 1 executable-specification scope review handoff](../journal/2026/09/2026-09-20-phase-1-executable-spec-scope-review-handoff.md)
  — CNCF common-contract specialization の review input
- [Historical Phase 1 design](../notes/phase-1-pre-reconciliation-design.md)
  — SQLite、CLI、CAR、SkillBundle、cost metrics等を含む旧設計の保存版
- [Historical operational checklist](phase-1-checklist.md)
  — superseded planning inventory
- [Deferred deterministic closing addendum](phase-1-deterministic-closing-addendum.md)
  — 後続のconnectivity / operational-hardening候補

これらのhistorical/deferred資料はPhase 1のscopeまたはclosure条件を拡張しない。
