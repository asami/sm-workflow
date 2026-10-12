# Phase 5／5.1 不要チェック残存レビュー handoff

日付: 2026-10-11（Asia/Tokyo）
対象: `/Users/asami/src/dev2026/sm-workflow`
状態: 静的レビューの引継ぎ。修正・動作再現・受入・開発再開は未実施。

採用先: [Phase 5.2](phase-5.2.md)（2026-10-11、planned）。ユーザーは全指摘の対応を独立Phaseに定義し、「互換性は必要ないので完全に移行」を指定した。
以降の実施方針はPhase 5.2を優先する。本書のDEV-01の任意整理・旧経路保持、および互換入口を一括削除しないという留保は撤回済み。
呼出側・保存形式・Spec・説明を新しい最小契約へ揃え、旧入力・旧経路・互換アダプタ・fallbackを撤去する。
スキル開発はこれからのため、Phase 6へ互換インタフェースを残さない。最小入力にする目的には、比較材料の再送をなくし不要チェックの残存・再導入を防ぐことも含む。
以下の観測結果は履歴として保持するが、完了済みPhaseの再オープンや実装開始の記録にはしない。

## 引継ぎの目的と境界

ユーザーの依頼は「Phase 5／5.1の対象となっている無駄なチェックが残っていないか」のレビューと、そのhandoff作成。
通常経路の残存指摘をCB-01／CB-02、旧経路の整理候補をDEV-01として記録する。
続く「OperationにはセッションID的なものと本質的なパラメタだけを渡す方針が徹底されているか」のレビューを、ユーザーの指示により本書へ追記した。公開Operation入力の残存指摘はOP-01／02／03。
これらのIDは今回のレビュー内の識別子であり、既存の正式レビュー台帳へ登録済みという意味ではない。

[Phase 5](phase-5.md)／[Phase 5.1](phase-5.1.md)の完了状態、正式リリース判断、既存の検証結果、Goal、原始base、PLAN epoch、レビュー履歴・修正回数を変更しない。
本書は完了済みPhaseの再オープン、新たな実装指示、修正回数の追加、製品テストの再実行を意味しない。
受け取り側は既存の作業指示に沿って扱うこと。既存の許可を無視して、handoff受領だけを理由に一般的な再承認を要求しない。

基準は、[Phase 5の削除方針](phase-5.md#removal-of-unnecessary-control-checks)と[Phase 5.1の責任範囲](phase-5.1.md#scope-and-ownership)、[R38の必要性優先方針](phase-5-r38-side-review-handoff-20261009.md)。
入力に実際の矛盾があれば、その入力を消費する境界で例外・エラーにする。
中間成果物の不変性、保存前後の一致、返却された診断情報の一致を別途証明しない。
hashをオブジェクトidentity、全文コピー、再エンコード比較、台帳、検証済み印へ置き換えても解決にならない。

## CB-01 — SplitPhase内部生成物の参照一致チェック（P2）

対象:

- [SplitPhasePlanningCheckpointPreparation.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePlanningCheckpointPreparation.scala): `captureUnchangedC`（観測時38行）、`captureAllocatedC`（45–48行）。
- 接続元: [SplitPhaseManagedAdvanceServiceOperation.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseManagedAdvanceServiceOperation.scala) の `_fresh_c` → `_mechanical_c`。
- 関連する同種の確認: [SplitPhaseExplicitPartitionContext.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseExplicitPartitionContext.scala) の `prepareSemanticC`、[SplitPhaseTypedPartitionWorkIssue.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseTypedPartitionWorkIssue.scala) の `prepareC`。

`outcome.selection eq bound.context.selection`、partition／enumeratedの`eq`などを受理条件としている。
通常の `advanceSplitPhase` から到達する。意味が同じでも、別のオブジェクトとして構築された値はこの条件で拒否される。
現在の呼出元が同一インスタンスを渡す場合は通るため、通常操作が現在必ず停止する、という再現報告ではない。
構築済みの型付き結果について「同じ生成物であること」を追加確認している点が残存指摘。

補正方針:

- 作成側から消費側へのデータの流れを確認し、内部構築で成立する関係を参照一致で再証明する条件を外す。
- 独立した入力同士であれば、実際に別の対象へ作用してしまう矛盾だけを消費境界で扱う。
- `eq`をcase class全体の`==`、hash、保存コピー比較へ単純置換しない。
- 実行権限を現在のcallへ結びつける別の`eq`まで、検索置換で一括削除しない。

既存検証の入口は [SplitPhasePlanningCheckpointPreparationSpec.scala](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhasePlanningCheckpointPreparationSpec.scala)。
「equal references but different objectives」を拒否するケースは、実際に目的値も変わっている。これを「意味が同じ別オブジェクト」の再現済みテストと誤読しない。
補正後は既存の通常advanceから計画結果／必要なWorkOrderへ進む経路を確認する。
不変性証明や構築方法だけを固定するテストは追加しない。

## CB-02 — 消費しない追加フィールドによる拒否（P2）

対象:

- [NativeHostEvidence.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/NativeHostEvidence.scala): `decodeStorageC`（観測時79–82行）。
- [NativeHostEvidenceSpec.scala](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeHostEvidenceSpec.scala): 正常データへ`extra`を追加すると拒否する期待値（観測時58行）、現行形式へ旧`validUntil`を足した場合の拒否（96行）。
- 同種の形状確認: [SplitPhaseCalibrationRawShape.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseCalibrationRawShape.scala) の `_object`（12行）。[record codec](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseExplicitCalibrationRecordJsonV1.scala)／[source codec](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseCalibrationSourceJsonV1.scala)から利用される。

Native host evidenceは通常の3 Workflow構成で利用される。
必要な型付き入力が揃っていても、処理に使わないフィールドが一つ増えただけで、実際の対象・権限・結果との関係を確認する前に拒否する。
Calibration側も入れ子の各objectでキー集合の完全一致を要求する。
これらは「矛盾する消費入力」の拒否と「未使用の情報が存在すること」の拒否を混同している。

補正方針:

- 実際に消費するフィールドを型付きで読み、必要な値の欠落、型不正、対象・権限・結果の矛盾を扱う。
- 消費しない診断・旧証明フィールドは無視する。未知のフィールドを権限や制御として解釈しない。
- 既存テストの未使用フィールド拒否期待値を修正し、正常な意味が同じデータは通ることと、実際の矛盾は拒否されることを同じ関連Specで確認する。
- 全形式の網羅的な再設計、別のschema検証基盤、再エンコード一致証明を追加しない。

## DEV-01 — 旧経路の管理hashとコピー照合（Phase 5.2で完全移行）

通常の3 Workflow factoryとは別の旧構成に残る処理。通常入口の停止原因とは断定していない。
Phase 5.2では必要な振る舞いを新経路へ移し、旧経路自体を撤去する。互換性のための旧経路改修は行わない。

確認箇所:

- [GoalPhaseStepClosureBoundary.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseStepClosureBoundary.scala): `commitPlanC` → `admitSnapshotC`（観測時215行／251–255行）。プログラム・計画のSHA-256を要求し、不一致ならcommit前に拒否する。
- [SplitPhaseEntryAssessment.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseEntryAssessment.scala): `_validSource`／`_validProposal`（88–103行）。64桁digestとsourceSnapshotDigest一致を要求する。
- [SplitPhaseEntryRecordJsonV1.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseEntryRecordJsonV1.scala): 上記digestを保存形式でも必須にする。
- [SplitPhaseExplicitStartPreparation.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseExplicitStartPreparation.scala): 保存selection／assessmentと現在値の全体比較。
- [SplitPhaseManagedStartRecordRegistration.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseManagedStartRecordRegistration.scala): 保存後に読み返して`actual == expected`を要求する。

経路の区別:

- 現行の [ComponentFactory](../../src/main/scala/org/simplemodeling/textus/smworkflow/impl/ComponentFactory.scala) は `ThreeWorkflowHostParameters.factory` を選ぶ。
- [ThreeWorkflowLauncherAssembly](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowLauncherAssembly.scala) はnative Goal／Repositoryとtyped Splitの通常構成を選ぶ。
- 旧Split入口は [SplitPhaseManagedServiceAssembly](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseManagedServiceAssembly.scala) の `bindHostEntrySliceC` 等から別構成として到達可能。
- したがって、コードの残存と通常factoryでの実行は別の事実として扱う。

所有者はsm-workflow。既存Phaseの再オープンではなく、Phase 5.2の完全移行として扱う。
現行の利用者／生成API／保存形式との接続を調べ、利用中のproducer／codec／consumerも同時に新契約へ移行する。
旧入口・旧形式へのfallbackと専用テストを撤去する。旧入口を今後もサポートするかの再判断は不要。
偽digest、固定digest、無条件成功adapter、旧処理へのfallback、別の改竄検出で延命しない。
通常経路の補正を先に接続し、同じPhaseの完了までに旧経路撤去も済ませる。互換性維持を理由にPhase 6へ残さない。

## 追加レビュー — 公開Operationの最小入力

結論は「一部は実現しているが、通常入口に3点残る」。
公開入力はセッションの識別と、その操作で新たに与える本質的なパラメタを中心にする。
保存済みの依頼・状態や認証contextから解決できる値を、呼出側に再送・比較用の証明として要求しない。
複数の対象が本当に存在する場合の選択子、操作の結果本文、独立した実行証拠まで一括削除する意味ではない。

### OP-01 — Startのinputが旧コマンド全体を要求する（P2）

対象:

- [NativeWorkflowSessionInput.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/NativeWorkflowSessionInput.scala): Startの`input`を文字列またはJSONとして元のOperationへ渡す処理（観測時124行付近）。
- [GoalPhaseStartServiceOperation.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseStartServiceOperation.scala)（36–55行）、[RepositorySyncStartServiceOperation.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncStartServiceOperation.scala)（40–59行）、[SplitPhaseStartServiceOperation.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseStartServiceOperation.scala)（40–65行）: 旧Startコマンドのdecoder。
- [ProfileStartAdmission.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/ProfileStartAdmission.scala)（89–91行）、[GoalPhaseParticipantAdmission.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseParticipantAdmission.scala)（15–27行）: 保存selectionや認証主体との照合。

外側をsession入力にしても、中の`input`は業務入力に加えて`schemaVersion`、`selectionIdentity`、`participantIdentity`、`idempotencyKey`を要求する旧コマンドのまま。
participantは保存selectionおよび認証済み主体と比較され、keyも保存値と比較される。
[SplitPhaseTypedStartServiceOperation.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseTypedStartServiceOperation.scala)も旧Start decoderを利用するため、廃止済み入口だけの問題ではない。

補正では、認証主体や既に保存されたselectionのkeyを内部で解決し、呼出側の照合用echoを不要にする。
本当に必要なselectionの選択子と、現在の主体に対する権限確認は保持する。
識別子という名前だけで一律に削除しない。

[ThreeWorkflowDirectOperationSpec.scala](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/ThreeWorkflowDirectOperationSpec.scala)の`_session_wire_`（観測時1304–1313行）は、既に旧形式でencodeしたコマンド全体を`input`へ包む。
この成功だけでは業務入力のみのStartを確認できない。既存の通常入口Specで、内部解決できる比較用値を与えずにStartできることを確認する。

### OP-02 — 結果本文の証拠参照を外側にも重複要求する（P2）

対象:

- [SplitPhaseTypedPartitionCompletion.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseTypedPartitionCompletion.scala)（63–66行）。
- [SplitPhaseTypedScopeWorkIssue.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseTypedScopeWorkIssue.scala)（70–77行）。
- [SplitPhaseTypedReasoningCompletion.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/SplitPhaseTypedReasoningCompletion.scala)（56–59行）。
- [NativeWorkflowResultInput.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/NativeWorkflowResultInput.scala): 省略時のevidence参照を空にする処理（131–141行）と内部envelopeへの展開（200–214行）。

結果本文に含まれるevidence参照が、外側の`executionEvidence.references`にもすべて含まれることを要求する。
結果本文の参照が非空なら、同じ参照を外側で繰り返さない最小入力は拒否される。
Partition semanticは非空evidenceを要求するため、空集合だけの仮想的な問題ではない。

補正では同じ参照を二重送信させる条件を外し、意味上の証拠参照は一度だけ消費する。
別途必要な実行・reviewerの証拠とは区別する。意味上の参照から実行済みの証明を捏造したり、`executionEvidence`全体を無条件に削除したりしない。

既存の [NativeWorkflowResultSubmissionFixture.scala](../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/NativeWorkflowResultSubmissionFixture.scala)は旧envelopeから`completionFacts`と`executionEvidence`を引き継ぐ。
上記ThreeWorkflowDirectOperationSpecの最小入力確認（観測時1381–1387行）も両欄を含むため、この重複送信の省略は確認していない。
補正後は結果本文だけに参照を持たせる通常提出と、実際に独立した実行証拠が必要なケースを区別して確認する。

### OP-03 — 公開パラメタ定義に必須arg1が残る（P2）

対象:

- [WorkflowJsonCommandRequest.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowJsonCommandRequest.scala): `arg1`を`XString`／`Multiplicity.One`として定義（28–33行）。
- [NativeWorkflowDescriptionMetadata.scala](../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/NativeWorkflowDescriptionMetadata.scala): 上記の共通定義を利用（20–27行）。
- NativeWorkflowSessionInput: 元の`specification`を保持（92行）しつつ、Start以外は引数なしを実装上受け付ける（94–97行）。

host sessionから対象を解決できるadvanceは実装上引数なしで呼べるが、公開定義は必須の文字列`arg1`を案内する。
定義を参照して入力を作るclientに、実装の最小入力契約が伝わっていない。
公開定義とdecoderを揃え、実際に必須の入力と省略可能な入力を一致させる。新たなtransportやschema基盤は追加しない。
これは静的に確認した不一致であり、CLI／MCP clientでの拒否を動的に再現した報告ではない。Phase 6の接続受入が済んだとも扱わない。

### 既に実現している部分と維持事項

- NativeWorkflowResultInputはsessionから現在の保留依頼を解決し、`handle`、`runId`、`continuationId`、`expectedRevision`、`contextSnapshot`、結果型を保存済み依頼から補う（182–214行）。これらの呼出側再送を戻さない。
- Splitの3種のsemantic結果では、結果の宛先を保存済み依頼から解決する（223–230行）。`result.reference`のechoは不要。
- host invocationにsessionがある場合、`sessionIdentity`の重複送信は不要。一意なadvance対象のinstanceやSplit contextも内部で解決できる。
- 本当に複数の候補がある場合の`instanceIdentity`／`contextIdentity`／`requestIdentity`は、比較証明ではなく選択子として必要になり得る。
- 観測時は旧identity入力や完全envelopeの互換入口も存在した。通常入力の必須項目とは区別するが、後続のユーザー指定によりPhase 5.2ではこれらの旧入口も撤去する。互換入口保持の留保は撤回済み。
- Goal／Repository／Human等の実際の結果・成果物・独立した証拠の参照は、本質的な入力になり得る。参照という理由だけで除去しない。

## 実施順序と確認範囲

1. 上記関数と実際の呼出元を現在のtreeで読み直す。行番号や古い指摘文を最新実装の証明としない。
2. OP-01／02／03では、公開定義→最小入力→内部解決→通常のStart／advance／結果提出を接続した状態を先に作る。CB-01／02も生成側・消費側・既存Specを含む限定補正として扱い、helperごとの実装→テスト→レビューを繰り返さない。
3. 変更が影響する既存の通常Operation経路・関連Specをまとめて検証し、まとめてレビューする。旧envelopeを包むだけのfixtureで最小入力の実証を代用しない。無関係な全体再検証や、過去Phaseの受入のやり直しは追加しない。
4. DEV-01はPhase 5.2の完全移行対象とする。必要な振る舞いを新経路へ移し、旧経路と専用の互換処理・テストを撤去する。利用者の存在は移行対象の確認に使い、互換性維持の再承認を求める理由にはしない。

選択対象、現在の依頼・権限、状態遷移／CAS、必要なレビュー・テスト、無関係な既存ファイルへの上書き防止は保持する。
個々の条件の必要性は、外すと起きる具体的な誤動作で説明する。安全・evidence・integrityという名称だけで存続させない。
Phase 5.1で除外した二重回答、競合提出、完全履歴、同一replay、途中復元の保証を戻さない。
SBTが必要な補正は既存の登録runnerと共有serial wrapperの経路を使う。

## 今回の証拠と限界

- 通常factory、主要な呼出経路、上記条件と関連テスト期待値を静的に確認した。
- 追加レビューでは公開Operationの定義、session／result入力の展開、Start decoder、semantic結果の受理条件、既存Specの実際の入力を確認した。今回の追記ではコード変更・新たなテスト実行はしていない。
- 内部TTLによる期限切れ拒否は、今回検索・確認した実装では見つからなかった。NativeHostEvidenceの旧expiry読み取りは期限値を無視する。
- `occurredAt`が未来であることの検証、処理timeout、Gitのrevision／tree参照は、期限切れ・管理hashであると一括判定していない。
- CAR lintを次のコマンドで実行し、終了コード0、既存の静的警告を確認した。不要チェック除去の実証テストではない。

  ```sh
  uv run --no-cache --managed-python --no-python-downloads --script /Users/asami/.agents/skills/cncf-car-lint/scripts/cncf_car_lint.py /Users/asami/src/dev2026/sm-workflow
  ```

- SBT、停止事象の動的再現、命名／GWTの全体監査、独立Phase受入レビューは未実施。新しいPASSや正式なレビュー台帳は作成していない。
- 当該レビューから今回のhandoff作成までに製品コードは編集していない。本書だけを追加し、commit・push・Goal変更・他タスクへの送信は行わない。
