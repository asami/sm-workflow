# SM Split Phase Workflow Definition

Status: normative design input for `SplitPhaseWorkflow`

Source workflow: legacy `cncf-split-phase` skill

Target: CML/CNCF Workflow and StateMachine runtime

## Purpose

`SplitPhaseWorkflow` は legacy `cncf-split-phase` の制御意味論を参照しつつ、skill や
Codex 会話履歴に依存しない純粋な Workflow / StateMachine として定義する。一つの
oversized、reasoning-cost-
heterogeneous、または semantic gate を含む Phase を、独立に閉じられる ordered child
Phases へ分割する。

この Workflow は product implementation を行わない。authoritative planning documents
の snapshot、分割提案、preview、適用、競合解消、documentation-only validation だけを
所有し、child Phase を開始しない。

AI cost reduction は副次効果ではなく第一級の設計目標である。Workflow は過去 Phase の
予定/実績時間と作業分類を収集し、versioned calibration policy で通常作業を見積もり、
contiguous な分割候補を列挙して決定的に最適化する。AI が担当するのは、履歴から推定
できない新規性の高い作業の補完見積もりと semantic boundary 判定だけとする。番号付け、
packing、validation ownership、document rendering、collision scan、three-way merge、
static validation は Workflow が実行する。

## Phase 1 common-contract alignment

Phase 1 closure binds this profile to the CNCF Phase 77 common contract. Its
profile-specific start Operation delegates to `WorkflowStartRequest` and
projects the resulting `WorkflowHandle` plus first `Continuation` (or typed
terminal) as `WorkflowInteraction`. A current `Continuation` remains the
canonical typed semantic boundary and Skill/Codex wire correlation; it is not
a second root identity or lifecycle. A typed completion is a
`ContinuationResult` admitted through the registered completion Operation
identified by the current Continuation, such as `SubmitSplitPhaseWorkResult`.
The older `Suspended(Continuation)` and profile-specific continuation wording
below describes runtime state or application payload specialization only; it
cannot replace the common Continuation protocol.

## Profile and skill identity

- CML Workflow name / definition selector: `SplitPhaseWorkflow`
- bundled/public skill name: `sm-split-phase`
- skill path in bundle: `skills/sm-split-phase/SKILL.md`
- source compatibility reference: legacy `cncf-split-phase`

`SkillBundleManifest` は `sm-split-phase -> SplitPhaseWorkflow / StartSplitPhase` のbindingを
versioned fieldで宣言する。skill名をWorkflow definition IDまたはstart Operation identityとして
推測したり、文字列変換で導出しない。
one-shot launcher selector は registered `StartSplitPhase` Operation identity とする。MCP は
同じ typed Operation の adapter であり、skill は transport 固有 argv から Workflow 名を
組み立てない。

`SplitPhaseWorkflow` の開始は人間が明示的に選択する。`sm-goal-phase` の
`SPLIT_REQUIRED` terminal result や host/client の recommendation は開始候補を提示できるが、
それ自体は invocation authority ではない。`sm-split-phase` の選択または exact
profile-specific `startSplitPhase(...)` によって、別の run として開始する。前 run の
typed source/evidence/proposal reference は入力として参照できるが、
run identity、revision、Decision、Continuation、durable state を暗黙に移送しない。

split 完了後も Workflow は child goal を開始しない。host/client は適用済み child を
`GoalPhaseWorkflow` の候補として表示できるが、どの child をいつ開始するかは人間が選び、
各選択は独立した `sm-goal-phase` invocation になる。

legacy `cncf-split-phase` skill は rename、overwrite、forward、または implicit migration
しない。両 skill は別の entry point と別の durable state を持ち、長期併用する。
`sm-split-phase` は legacy skill を内部呼び出しせず、versioned `sm-workflow` protocol
だけを利用する。同一 worktree/path の同時 mutation は workflow state 共有ではなく
`WorkspaceMutationLease` coordination で拒否する。

## Ownership boundary

### Workflow owns

- source Phase identity、invocation mode (`APPLY | PREVIEW`) と exact planning authority。
- immutable source snapshot と optimistic revision/digest。
- completed history、unfinished scope、Step/Slice/closure ownership inventory。
- historical planned/actual duration evidence、work classification と estimation policy version。
- known-work estimate、contiguous candidate enumeration、lexicographic partition optimization。
- child numbering、collision/idempotency classification、strategy order。
- six-hour target、eight-hour ceiling、short-child merge/rebalance constraints。
- validation timing/method、aggregate owner、bootstrap rule。
- frozen `PhaseSplitProposal` とその decision digest。
- deterministic document projection、three-way merge、write set、static validation。
- merge conflict classification、resolution ledger、receipts、terminal outcome。

### Workflow does not own

- child Phase の implementation、test、review、release、goal lifecycle。
- skill 名/path、prompt、model、reasoning effort、agent role、turn scheduling。
- arbitrary shell、free-form argv、AI が選ぶ Git/SBT command sequence。
- user authority を必要とする Phase identity の再利用、nested numbering、scope change。
- planning split の commit、push、publish、deploy。
- `GoalPhaseWorkflow`、child goal、または別 Workflow run の選択と開始。

Skill は Workflow owner ではない。external semantic Action の provider として
`Suspended(Continuation)` を受け、typed Result/Evidence を返す thin participant である。

## Cost objective

通常の apply 経路の semantic AI invocation budget は次とする。

| Situation | Semantic AI calls | Workflow behavior |
| --- | ---: | --- |
| current かつ complete な `SPLIT_REQUIRED` proposal を採用可能 | 0 | proposal を検証し deterministic に適用する |
| proposal がなく、全作業を履歴から推定でき、semantic boundary も明示済み | 0 | deterministic estimate/optimizer だけで proposal を作る |
| 未知作業の見積もり、または semantic boundary 判定が必要 | 1 | `AssessNovelSplitWork` だけを発行する |
| apply 中に semantic merge conflict が残る | +1 per frozen conflict set | `ResolveSplitMergeConflicts` を発行する |
| numbering/authority に人間判断が必要 | 0 additional AI required | typed `DECISION` で停止する |

AI に history collection、既知作業の calibration、候補列挙、partition selection、番号生成、
document edit、command execution、diff scan、再検証を依頼しない。同一 unresolved input
または conflict set に対する同じ AI result の再取得も行わない。結果が invariant を
満たさなければ typed failure または human decision に分類する。

## Aggregate structure

```text
SplitPhaseWorkflow
  +-- InvocationLifecycle
  |     EntryAssessment
  |     SourceSnapshot
  |     Terminal
  +-- ProposalLifecycle
  |     EvidenceAdoption
  |     DeterministicEstimation
  |     SemanticEnrichment
  |     DeterministicOptimization
  |     DeterministicValidation
  |     Frozen
  +-- ApplicationLifecycle
  |     Projection
  |     ConflictClassification
  |     NonCollidingComposition
  |     SemanticMerge
  |     Write
  +-- ValidationLifecycle
  |     Structural
  |     Ownership
  |     Reference
  |     Diff
  +-- DecisionLifecycle
        None
        Pending
        Resolved
        Blocked
```

source of truth は CML definition と generated ABI であり、skill 内の prose procedure
ではない。

## Run context

```text
SplitPhaseRun
  workflowDefinitionId
  workflowDefinitionVersion
  runId
  revision
  sourcePhaseIdentity
  invocationSelectionReference
  originGoalPhaseTerminalResultReference?
  invocationMode: APPLY | PREVIEW
  validationTiming: FINAL_ONLY | EACH_PHASE
  sourceAuthorityReference
  sourceSnapshotReference
  sourceSnapshotDigest
  repositoryBoundary
  planningDocumentSet
  estimationPolicyVersion
  durationEvidenceSnapshotReference
  workItemEstimates[]
  semanticBoundarySet[]
  proposal?
  proposalDigest?
  projectionReference?
  writeSet?
  conflictSet?
  conflictResolutionLedger
  validationLedger
  pendingDecision?
  pendingContinuation?
  terminalOutcome?
```

SQLite、JDBC、artifact path はこの profile の public Action contract に含めない。

Start input は cross-Workflow state ではなく、独立 run を構築する immutable reference
だけを受け取る。

```text
SplitPhaseStartInput
  sourcePhaseIdentity
  invocationMode: APPLY | PREVIEW
  sourceAuthorityReference
  sourceSnapshotReference?
  originGoalPhaseTerminalResultReference?
  proposalReference?
  invocationSelection: WorkflowInvocationSelection
```

`originGoalPhaseTerminalResultReference` と `proposalReference` は任意である。指定された場合も
source identity、digest、authority を entry assessment で再検証し、prior run の revision、
Decision、Continuation、lease、pending state を復元または継承しない。

## Top-level states

| State | Meaning | On-entry Action class |
| --- | --- | --- |
| `ENTRY_ASSESSMENT` | exact source、mode、authority boundary を解決する | deterministic |
| `SOURCE_INVENTORY` | planning set、history、unfinished scope、validation capability を snapshot する | deterministic operation |
| `PROPOSAL_ADOPTION` | current な prior proposal を採用可能か検証する | deterministic |
| `WORK_ESTIMATION` | history と固定policyから既知作業の時間を計算する | deterministic operation |
| `SEMANTIC_ENRICHMENT` | 未知作業の見積もりと semantic boundary だけを補完する | semantic AI |
| `SEMANTIC_ENRICHMENT_ADMITTING` | AI補完のschema、authority、対象範囲を検証する | deterministic operation |
| `PARTITION_OPTIMIZATION` | contiguous候補を列挙し、固定目的関数で proposal を選ぶ | deterministic operation |
| `PROPOSAL_VALIDATION` | numbering、packing、ownership、handoff、validation policy を検証する | deterministic |
| `PROPOSAL_FROZEN` | proposal digest と source revision を固定する | automatic |
| `PREVIEW_PROJECTION` | edit せず preview result を生成する | deterministic |
| `DOCUMENT_PROJECTION` | authoritative documents の desired projection を生成する | deterministic operation |
| `CONFLICT_CLASSIFICATION` | base/current/desired の差を non-collision / modification collision / authority collision に分類する | deterministic operation |
| `NON_COLLIDING_COMPOSITION` | 非重複・同一値・canonical projection を conflict でないこととして compose する | deterministic operation |
| `SEMANTIC_MERGE` | modification collision の解決案を作る | semantic AI |
| `RESOLUTION_VALIDATION` | AI resolution を authority/invariant に照らして検証する | deterministic |
| `APPLYING_DOCUMENTS` | current revision に CAS で write set を適用する | deterministic operation |
| `STATIC_VALIDATION` | applied split を documentation-only に検証する | deterministic operation |
| `AWAITING_DECISION` | authority または複数妥当解の選択を待つ | human participant |

Terminal outcomes:

- `SPLIT_PREVIEWED`
- `SPLIT_APPLIED`
- `SPLIT_ALREADY_APPLIED`
- `DECISION_REQUIRED`
- `BLOCKED`
- `FAILED`
- `CANCELLED`

## Primary transition definition

### Entry and proposal

```text
start
  -> ENTRY_ASSESSMENT

ENTRY_ASSESSMENT -- exact source admitted --> SOURCE_INVENTORY
ENTRY_ASSESSMENT -- nested numbering / authority ambiguity --> AWAITING_DECISION
ENTRY_ASSESSMENT -- invalid boundary --> FAILED

SOURCE_INVENTORY -- snapshot complete --> PROPOSAL_ADOPTION
SOURCE_INVENTORY -- missing validation capability not owned by bootstrap --> BLOCKED

PROPOSAL_ADOPTION -- current complete proposal --> PROPOSAL_VALIDATION
PROPOSAL_ADOPTION -- proposal construction required --> WORK_ESTIMATION

WORK_ESTIMATION -- all estimates and boundaries resolved --> PARTITION_OPTIMIZATION
WORK_ESTIMATION -- novel work / semantic boundary unresolved --> SEMANTIC_ENRICHMENT

SEMANTIC_ENRICHMENT -- typed enrichment submitted --> SEMANTIC_ENRICHMENT_ADMITTING

SEMANTIC_ENRICHMENT_ADMITTING -- valid --> PARTITION_OPTIMIZATION
SEMANTIC_ENRICHMENT_ADMITTING -- missing semantic input --> SEMANTIC_ENRICHMENT
SEMANTIC_ENRICHMENT_ADMITTING -- authority decision required --> AWAITING_DECISION
SEMANTIC_ENRICHMENT_ADMITTING -- invalid closed result --> FAILED

PARTITION_OPTIMIZATION -- unique best proposal --> PROPOSAL_VALIDATION
PARTITION_OPTIMIZATION -- no admissible partition --> FAILED
PARTITION_OPTIMIZATION -- equal optimum requires authority choice --> AWAITING_DECISION

PROPOSAL_VALIDATION -- valid --> PROPOSAL_FROZEN
PROPOSAL_VALIDATION -- missing semantic input exposed --> SEMANTIC_ENRICHMENT
PROPOSAL_VALIDATION -- authority/identity decision required --> AWAITING_DECISION
PROPOSAL_VALIDATION -- closed invariant violation --> FAILED

PROPOSAL_FROZEN -- PREVIEW --> PREVIEW_PROJECTION --> SPLIT_PREVIEWED
PROPOSAL_FROZEN -- APPLY --> DOCUMENT_PROJECTION
```

`SEMANTIC_ENRICHMENT -> SEMANTIC_ENRICHMENT_ADMITTING -> PARTITION_OPTIMIZATION ->
PROPOSAL_VALIDATION` は同一 evidence に対する prompt retry loop ではない。AI result は
frozen unresolved-item set に一度だけ対応し、deterministic admission 後の候補列挙と選択は
常に Workflow が行う。validator が新しい semantic input 不足を発見した場合だけ新しい
digest の enrichment を一度発行し、同じ rejection lineage の反復は
`AWAITING_DECISION` または `FAILED` にする。

### Apply, merge, and validation

```text
DOCUMENT_PROJECTION -- exact split already present --> STATIC_VALIDATION
DOCUMENT_PROJECTION -- projection ready --> CONFLICT_CLASSIFICATION

CONFLICT_CLASSIFICATION -- no current/desired overlap --> APPLYING_DOCUMENTS
CONFLICT_CLASSIFICATION -- non-colliding composition required --> NON_COLLIDING_COMPOSITION
CONFLICT_CLASSIFICATION -- modification collision --> SEMANTIC_MERGE
CONFLICT_CLASSIFICATION -- identity/authority conflict --> AWAITING_DECISION

NON_COLLIDING_COMPOSITION -- verified --> APPLYING_DOCUMENTS
NON_COLLIDING_COMPOSITION -- collision exposed --> SEMANTIC_MERGE | AWAITING_DECISION

SEMANTIC_MERGE -- typed resolution submitted --> RESOLUTION_VALIDATION

RESOLUTION_VALIDATION -- valid --> APPLYING_DOCUMENTS
RESOLUTION_VALIDATION -- authority decision required --> AWAITING_DECISION
RESOLUTION_VALIDATION -- invalid/non-convergent --> FAILED

APPLYING_DOCUMENTS -- compare-and-set success --> STATIC_VALIDATION
APPLYING_DOCUMENTS -- concurrent revision drift --> CONFLICT_CLASSIFICATION
APPLYING_DOCUMENTS -- bounded I/O failure --> WAIT | FAILED

STATIC_VALIDATION -- valid + changed --> SPLIT_APPLIED
STATIC_VALIDATION -- valid + identical --> SPLIT_ALREADY_APPLIED
STATIC_VALIDATION -- mechanically repairable projection defect --> DOCUMENT_PROJECTION
STATIC_VALIDATION -- semantic defect --> SEMANTIC_MERGE
STATIC_VALIDATION -- authority defect --> AWAITING_DECISION

AWAITING_DECISION -- resolution accepted --> ENTRY_ASSESSMENT | PROPOSAL_VALIDATION | RESOLUTION_VALIDATION
AWAITING_DECISION -- defer/cancel --> BLOCKED | CANCELLED
```

## Deterministic duration estimation and partition optimization

### Evidence collection

`CollectPhaseDurationEvidence` は authoritative Phase records と accepted execution receipts
から、各 completed comparable unit について次を収集する。

```text
PhaseDurationObservation
  observationId
  workClassKey
  repositoryClass
  planningDemand
  plannedDuration?
  actualDuration
  completedAt
  authorityReference
  evidenceDigest
```

`workClassKey`、history window、required fields、observation ordering は versioned
`DurationEstimationPolicy` が固定する。Workflow は newest/latest の曖昧な探索や prose
類似度で comparable evidence を選ばない。不完全、重複、authority digest 不一致の
observation は理由を付けて除外する。

### Fixed calibration

各 current work item は次の順で見積もる。

1. current authority に explicit baseline duration があり、exact `workClassKey` の comparable
   observations に planned/actual pair が十分ある場合、policy が固定する median ratio を
   baseline に掛ける。
2. explicit baseline がなく、exact class の actual observations と normalized work units が
  十分ある場合、policy が固定する median actual-per-unit を current units に掛ける。
3. policy が定める minimum sample count を満たさない、work class が新規、または current
   work が既存 class の closure/complexity envelope を越える場合は `UNRESOLVED_NOVEL` とする。
4. result は policy の fixed quantum へ丸め、固定 overhead table を一度だけ加える。

median、minimum sample count、history window、normalization unit、rounding quantum、overhead
table、outlier admission rule はすべて `DurationEstimationPolicy` の versioned data とする。
実行時に AI が係数、comparable set、丸め方を変更できない。同じ evidence snapshot と
policy version は同じ estimate と evidence trace を返す。

`UNRESOLVED_NOVEL` item だけを `AssessNovelSplitWork` に渡す。AI result は estimate range、
novelty basis、nearest known class、assumptions、evidence references を返す。Workflow は
schema/range/authority を検証して `workItemEstimates` に取り込み、AI が既知 item の
deterministic estimate を上書きすることを拒否する。

### Semantic boundary input

Source Phase の独立 closure と authority は proposal の自己申告から推定しない。
host は `docs/phase/snapshots/<snapshotIdentity>.json` に
`sm-workflow.split-phase-source-snapshot.v1` catalog record を登録する。
catalog の exact UTF-8 bytes の SHA-256 が Start の source snapshot digest となる。
record は `sourceSnapshotIdentity`、`sourcePhaseIdentity`、
`sourceAuthorityReference`、`revision`、ordered `orderedUnfinishedItems[]`、
`occupiedPhaseIdentities[]`、`phaseDocumentSha256` を持つ。Start と proposal
assessment は同じ catalog path を再読込し、変更された bytes は digest mismatch で拒否する。
catalog の文書 SHA と source Phase file の実 bytes も毎回照合する。proposal は catalog を
供給・改変できず、別の snapshot identity を同じ run に継承できない。

Phase 1 の file reader は `docs/phase/phase-<identity>.md` の一つの
`split-phase-source-boundary.v1` fenced block だけを機械可読な source 契約として読む。
host-owned snapshot reference は repository root、source snapshot identity/digest、
Phase identity、authority reference、Phase file の SHA-256 を固定し、reader は実ファイル
の bytes と照合する。block は `schemaVersion`、`sourcePhaseIdentity`、
`sourceAuthorityReference`、ordered `items[]`（identity、closureCriteria[]、
prerequisiteItems[]、authorityReference）、`authorizedHandoffReferences[]` を持つ。
block がなければ証拠待ちの `WAIT`、宣言された block が不正・重複・stale なら拒否する。
自然文の Scope/Closure contract から item-to-criterion 対応を補完しない。

```split-phase-source-boundary.v1
{
  "schemaVersion": "sm-workflow.split-phase-source-boundary.v1",
  "sourcePhaseIdentity": "77",
  "sourceAuthorityReference": "source-authority-1",
  "items": [
    {"identity": "work-1", "closureCriteria": ["close-work-1"], "prerequisiteItems": [], "authorityReference": "source-authority-1"}
  ],
  "authorizedHandoffReferences": []
}
```

この block 自体は proposal や skill が後付けする admission 証拠ではない。source snapshot
の取得者が authoritative document として登録した場合にのみ、snapshot-bound port が供給する。
実 planning repository の snapshot catalog 登録は Phase 1 の後続作業である。

authority documents に exact handoff kind/input/action/output/owner/invalidation reason が既に
ある boundary は deterministic に採用する。不明な edge だけを同じ
`AssessNovelSplitWork` request に含める。AI は edge を `BOUNDARY | NOT_BOUNDARY |
DECISION_REQUIRED` に分類し、`BOUNDARY` では complete typed handoff を返す。

### Candidate enumeration and selection

`OptimizePhasePartition` は Step/Slice の dependency order を保った contiguous partitions を
全列挙する。semantic boundary、atomic closure、authority transition、validation timing が
許可しない edge を横断する候補は除外する。各 candidate の duration は owning items の
accepted estimates と fixed Phase overhead の和として計算する。

admissibility filter:

- each child is independently closable;
- calibrated expected duration is `<= 8h`;
- ordinary `1–2h` child is rejected;
- `<5h` child has complete adjacent rebalance evaluation;
- `<4h` child has predecessor/successor merge evaluation and a genuine semantic handoff;
- validation/dependency/authority constraints are satisfied.

Phase 1 の `SplitPhaseCandidateSetAssessment` は、単一の host-owned source
snapshot catalog に束縛した全候補の gate evidence を検査し、admissibility と
duration frontier までを返す。候補ごとの source boundary 自己申告は受け付けず、
証拠がない候補や closure/authority 契約がない候補を winner にしない。
正しく記述された short-child 不適格候補は候補単位で reject し、ほかの候補の評価を
継続する。全候補が決着し duration leader が一つならその段階だけは決定できる。
duration 同点なら `CostPolicyRequired` とし、cost-isolation、overhead を含む最終選定と
proposal admission は別段階である。
`SplitPhaseCostIsolation` は、明示された versioned profile 単価・material-saving 閾値と
全 item の reasoning demand に基づき、duration 同点候補の低コスト隔離効果を採点する。
`SplitPhaseCostEvidenceCatalog` は host-owned
`docs/phase/cost-policies/<snapshotIdentity>.json` を読み、source snapshot digest、Phase
identity、authority、ordered item coverage を照合する。欠落は `CostPolicyRequired`、
不一致は拒否する。`decideCatalogWithCostC` は duration 同点時だけ `CostRanked` を返し、
file bytes の digest を後続検証用に保持する。cost frontier はまだ proposal admission
ではない。`SplitPhaseOverheadPriority` は duration・cost 両方で同点の候補だけを対象に、
child setup、semantic handoff、validation run、review、commit の明示的な重みで
administrative overhead を比較する。validation run は `FinalOnly` で1回、
`EachPhase` で child 数とし、各 child の expected duration に含まれる Phase overhead
は重複計上しない。`SplitPhaseOverheadPolicyCatalog` は host-owned
`docs/phase/overhead-policies/<snapshotIdentity>.json` を source snapshot の実 bytes
の digest、Phase identity、authority に束縛する。欠落は `OverheadPolicyRequired`、
不一致は拒否する。`decideCatalogWithOverheadC` は必要な場合だけ `OverheadRanked` と
policy document digest を返す。これも preview であり、実運用 policy 値の登録、
authority を考慮した最終 tie-break、proposal admission、PREVIEW/APPLY が残る。
`decideCatalogWithFinalC` は、duration/cost/overhead で一意になった候補を
objective preview として返す。overhead まで同点なら `SplitPhaseFinalTieBreak` が
host-owned source boundary で各候補の authority digest を再検証し、handoff の
authority reference と授受 child の item 所有を署名に含める。異なる署名
を boundary vector の辞書順で選ばず `AuthorityDecisionRequired` とし、同一 digest
の場合に限って child 数、次いで source item 順の boundary vector で選ぶ。
この選定結果も proposal admission や planning write の権限を与えない。
`SplitPhaseProposalAdmission` は選定された candidate の draft identity/digest と
参照 proposal を照合し、host-owned source snapshot を使う既存の proposal assessment
を通して in-memory の admission outcome を返す。proposal 欠落と objective 未確定は
pending、不一致と source drift は拒否する。ここでは文書を書き換えず、PREVIEW/APPLY
は実行直前に source/policy evidence を再検証する。
admission outcome は source snapshot と partition policy の digest、cost/overhead
policy 文書の bytes digest も保持する。`revalidateCatalogC` は現在の host-owned 入力で
再評価し、元の outcome と一致した場合だけ返す。policy 文書変更を含む差分は古い判定を
失効させる。この再照合自体は planning write や PREVIEW/APPLY の実装ではない。
`SplitPhasePreviewProjection` は、再照合済み admission と source boundary から
child の所有 work item、closure、時間、validation owner、依存、handoff を型付きの
読み取り専用 projection にする。proposal/validation evidence を固定して投影前後に
admission を再照合する。公開 `previewSplitPhase` はこの projection を revision 2 の
durable instance に結び、成功時に revision 3 の Completed append と Terminal JSON
を返す。PREVIEW は planning document を書き換えず、APPLY と document projection /
CAS write は別に実装する。
生成 CML は `split-phase-preview-v1` として `Previewed` state と
`previewSplitPhase` Provided Operation を宣言済みで、CNCF の entry binding も
3段階を受け入れる。Operation の runtime 実行は host-owned catalog の再照合と
Completed append に接続済みで、古い admission や APPLY mode は拒否する。
source/policy 再照合と instance append の間は共有 transaction ではない。
`SplitPhaseDocumentProjection` は再照合済み admission と exact source Phase file から
source/child Phase の desired Markdown と expected/desired SHA-256 を決定的に作る。
これは read-only の write-set plan であり、対応していない文書形式や既存 child path
を拒否する。`SplitPhaseDocumentConflict` は read port で current SHA-256 を確認し、
exact base、全 path の already-applied、部分適用を含む conflict を分類する。
異なる bytes を自動 merge せず、実文書を変更しない。
`SplitPhaseDocumentCas` は返却された結果について全件適用または補償復元を host port に要求し、
適用後の全 desired digest を再確認する。fixture の in-memory port で一回だけの適用、
同一再送、同時変更、全件を適用しない port の拒否を検証した。これは port 契約であり、
この契約はプロセス停止・停電時の multi-file atomicity を含まない。
`SplitPhaseDocumentFilePort` は Phase 文書だけを対象とする協調 lock、expected digest
の再照合、同一ディレクトリ内 staging、失敗時の補償復元を提供する。コピーした fixture
でのみ使用し、実 Phase 文書にはまだ接続していない。公開 APPLY Operation も
コピーした fixture だけで検証する。crash recovery は今回の
緩いトランザクション境界には含めず、別フェーズの課題として扱う。
現行の `SplitPhaseDocumentStaticValidation` は、CAS の前に projected Phase 文書を
厳密な boundary parser で再読し、Scope と source item coverage、closure、authority、
proposal provenance を確認する。これは狭い fixture の subset であり、上記の
`ValidateAppliedSplit` 全項目や実ファイル適用を証明するものではない。
`SplitPhaseApplyDocuments` は host-only の下位境界で、APPLY mode の Start と
proposal-assessed instance/current admission を再確認して文書だけに適用する。
`SplitPhaseApplyPlanStore` と `SplitPhaseApplyExecution` は、host-owned の不変計画を
文書適用前に保存し、そのdigestをintent履歴に結び付ける。完了appendが失敗しても、
保存計画から適用済み文書を確認してCompleted履歴を再試行できる。
生成 CML は `split-phase-apply-v1` として PREVIEW/APPLY の別々の終端分岐を
宣言し、CNCF の actionless entry binding は両 Provided Operation を照合する。
公開 `applySplitPhase` は host-owned repository root と catalog を使用し、
成功時に revision 4 の Completed instance に結び付いた `SplitPhaseApplyResult`
の Terminal JSON を返す。同一 command の再送は保存計画と文書を照合し、
異なる APPLY idempotency key は履歴照合で拒否する。公開経路の実行検証は
コピー fixture に限定し、プロセス停止中の部分的な複数ファイル適用は自動復旧しない。

残った候補を versioned lexicographic objective で一意に選ぶ。

1. authority/correctness/closure violation count（常に0でなければ不採用）。
2. `sum(abs(childExpectedHours - 6h))` を最小化する。
3. sub-five-hour child count を最小化する。
4. deterministic cost-isolation policy が material と判定した lower-cost execution region を
  最大化する。
5. child/handoff/validation/review/commit overhead を最小化する。
6. それでも同値なら child count、次に dependency order 上の boundary vector の辞書順で
  安定して tie-break する。

cost-isolation policy の compatibility table、material-saving threshold、Phase overhead は
versioned configuration とし、AI が価格や profile-to-token multiplier を発明しない。
authority上異なる候補を単なる辞書順で選んではならず、その場合は human Decision とする。

## Phase split proposal

`PhaseSplitProposal` は AI の自由形式提案ではなく、accepted estimates、semantic boundaries、
versioned optimizer result から Workflow が生成する typed decision record である。

```text
PhaseSplitProposal
  sourcePhase
  sourceAuthorityDigest
  invocationMode
  validationTiming
  validationMethod
  validationDriver?
  bootstrapPolicy
  invocationAuthority
  sourceGoalStatus
  sourceGateEvidence
  packingTargetHours: 6
  packingCeilingHours: 8
  estimateCalibrationEvidence
  estimationPolicyVersion
  durationEvidenceSnapshotDigest
  deterministicEstimates[]
  novelWorkEstimateResults[]
  optimizationObjectiveValues
  consideredCandidateDigest
  splitReasons[]
  completedHistoryRefs[]
  children[]
  semanticHandoffs[]
  mergeAttemptsForSub4hChildren[]
  rebalanceAttemptsForSub5hChildren[]
  adjacentMergeRejectionEvidence[]
  expensiveReasoningKernels[]
  lowerCostRegions[]
  profileTransitionHandoffs[]
  overheadTradeoffEvidence
  referencesThatMustNotChange[]
  nonGoals[]
```

Each child contains:

```text
PhaseSplitChild
  identity
  goal
  ownedUnfinishedItems[]
  closureCriteria[]
  validationOwnership
  dependencies[]
  predecessor?
  successor?
  calibratedExpectedDuration
  uncertaintyRange
  packingFit
  planningDemand
  recommendedParentProfile
  profileCostRole
  expensiveReasoningKernel?
  frozenInputHandoffs[]
  producedHandoffs[]
```

model/effort fields are profile planning data projected into CNCF planning documents, not
generic Workflow engine semantics or participant routing instructions.

## Semantic AI Actions

| Operation | Input | Result |
| --- | --- | --- |
| `AssessNovelSplitWork` | unresolved novel items, unresolved edges, deterministic estimates, immutable authority | `SemanticSplitInputs | SplitDecisionNeeded` |
| `ResolveSplitMergeConflicts` | frozen conflict set, base/current/desired semantic models, invariants | `SplitConflictResolutionPlan | SplitDecisionNeeded` |

`AssessNovelSplitWork` decides only inputs that require semantic understanding:

- estimate range and assumptions for `UNRESOLVED_NOVEL` work only;
- exact semantic handoff boundaries not already explicit in authority;
- unresolved expensive reasoning kernels versus settled lower-cost execution regions;
- ownership/dependency interpretation that cannot be derived from typed authority.

It does not select comparable history, calibrate known work, enumerate or select partitions,
choose filenames/suffixes/metadata enums, construct command argv, apply patches, or choose the
next state.

その result は `SEMANTIC_ENRICHMENT_ADMITTING` が unresolved item/edge identity、schema、
range、authority digest、既知estimate非上書きを検証するまで proposal input にならない。
同様に `ResolveSplitMergeConflicts` result は `RESOLUTION_VALIDATION` を通るまで write set
を変更しない。どちらのAI resultもtransition、validation acceptance、ledger mutation、
retry、write/commit readinessを返せない。

`ResolveSplitMergeConflicts` receives only unresolved semantic conflicts. It returns typed
per-conflict dispositions bound to conflict IDs and the frozen source/current/proposal
digests. It does not edit files or run Git. If two materially different resolutions remain
valid, or the resolution changes authority, it must return `SplitDecisionNeeded`.

Workflow/generated binding が選択した一つの `AssessNovelSplitWork` または
`ResolveSplitMergeConflicts` をfully materialized `AIWorkRequest`として渡す。skillはrequest
内容を見て処理を呼び分けず、指定されたAI処理のtyped resultを同じWork Orderへ返して
終了する。次のoperation/stateはWorkflow admissionと`advance`だけが選ぶ。

## Deterministic operations

- `ResolveSourcePhaseAuthority`
- `SnapshotPlanningDocumentSet`
- `InventoryCompletedAndUnfinishedScope`
- `DetectFullValidationCapability`
- `ResolvePriorSplitEvidence`
- `CollectPhaseDurationEvidence`
- `ClassifyKnownAndNovelWork`
- `EstimateKnownWorkItems`
- `ValidateSemanticSplitInputs`
- `EnumerateContiguousPartitions`
- `OptimizePhasePartition`
- `AllocateChildIdentities`
- `ValidatePhaseSplitProposal`
- `PlanAggregateFinalValidation`
- `RenderSplitDocumentProjection`
- `ScanPhaseIdentityCollisions`
- `BuildThreeWayMergeModel`
- `ClassifySplitConflicts`
- `ApplyNonCollidingSplitComposition`
- `ValidateSemanticConflictResolution`
- `ApplyPlanningWriteSet`
- `ValidateAppliedSplit`
- `InspectPlanningDiff`
- `PersistSplitEvidence`

legacy helper の `plan-split` / `validate-split` と `git diff --check` 相当は、typed
operation provider 内で実行する。Skill/AI に Python、Git、SBT その他の command を
要求しない。この documentation-only Workflow は SBT/runtime validation を実行しない。

## Conflict and merge policy

競合は source snapshot、current planning set、desired projection の three-way semantic
model で判定する。非重複、同一 normalized value、canonical proposal から再生成できる
projection は修正の衝突ではない。Workflow は compatibility を検証して compose できるが、
これを conflict resolution と扱わない。

### Workflow-side non-colliding composition

- base に対する current/desired の変更範囲が非重複である。
- 両側が同じ normalized value または同じ completed-history bytes を保持する。
- strategy/index/checklist が frozen proposal から再生成可能である。
- machine-readable validation metadata が typed helper result から再生成可能である。
- stable identity を持つ dated note/record の append が重複せず合成できる。
- current status や completed history を保持したまま new planned child metadata を投影できる。
- stale generated projection を canonical proposal から置換でき、human-authored semantic
  authority を上書きしない。

These conditions prove that the two edits do not collide. The composition receipt records operation
version、input digests、compatibility reason、output digest、changed paths を残す。同じ receipt
の replay は同じ result を返す。

### AI-side semantic merge

同じ stable semantic entity、Step/Slice ownership、goal、closure、dependency、handoff に
current と desired が異なる修正をした時点で `ModificationCollision` とする。Workflow は
outputが一見一意でも自動採択せず、次を `ResolveSplitMergeConflicts` へ委譲する。

- 同じ unfinished Step/Slice の ownership が current と desired で異なる。
- goal、closure criterion、dependency、handoff の意味が両側で変わっている。
- current edit が新しい semantic boundary、reasoning kernel、validation requirement を導入する。
- append-only history と current planning statement の区別に意味判断が必要である。
- stale reference が historical evidence か誤った current ownership か一意に分類できない。

AI resolution も Workflow の validator と CAS write を必ず通る。AI が返した patch を
無検証で適用しない。

### Human decision boundary

次は non-colliding composition も AI authority も越えられない。

- proposed child identity が別の authoritative Phase と衝突する。
- decimal-suffixed source の nested numbering rule が定義されていない。
- completed history の移動・改変、既存 closed Phase policy の変更が必要になる。
- branching/multi-repository authority と final-only policy が両立しない。
- 複数の materially different semantic resolutions が同等に妥当である。
- repository/user authority の拡張、破壊的上書き、別 Phase の repurpose が必要である。

## Guards and invariants

1. 一つの run は一つの exact unsuffixed source Phase だけを分割する。
2. first child は source identity/file を保持し、later child は `.1`, `.2`, ... を使う。
3. later unsuffixed Phases を renumber しない。identity は decimal number でなく string として扱う。
4. completed history は source Phase に残し、unfinished item は exactly one child が所有する。
5. すべての child は independently closable である。
6. ordinary packing は calibrated expected six hours を中心とし、eight hours を超えない。
7. sub-five-hour child は complete adjacent rebalance evidence を必要とする。
8. sub-four-hour child は predecessor/successor merge attempt と genuine semantic handoff を必要とする。ordinary one/two-hour child は拒否する。
9. cost optimization は authority、correctness、closure、packing の後に適用する。
10. expensive kernel の分離は durable handoff と overhead を上回る saving を必要とする。
11. semantic edge は receiving child に exactly once 記録する。
12. default validation timing は `FINAL_ONLY`、explicit opt-in だけが `EACH_PHASE` を選ぶ。
13. `FINAL_ONLY` は serial single-repository chain と final owner を必要とする。
14. apply は proposal digest と current document revision の compare-and-set を使う。
15. exact same applied split は idempotent success とし、異なる identity collision は上書きしない。
16. preview は Git-visible mutation を行わない。
17. apply は product code、SBT/runtime test、goal start、commit、push を行わない。
18. successor child を自動的に開始しない。
19. deterministic estimate は exact evidence snapshot と estimation policy version に束縛する。
20. AI は `UNRESOLVED_NOVEL` item 以外の duration estimate を変更できない。
21. partition candidate の列挙、admissibility、objective evaluation、tie-break は Workflow が行う。
22. semantic enrichment/merge result は別の deterministic admission/validation state を通るまで proposal/write set/stateを変更しない。
23. prior `SPLIT_REQUIRED` result は typed input reference としてのみ利用し、prior run の
    identity/revision/stateを移送せず、split後のchild goalも自動開始しない。

## Static validation

`ValidateAppliedSplit` は少なくとも次を検証する。

1. child ごとの authoritative Phase document と strategy/index entry が exactly one。
2. identity、suffix order、former successor order、collision が正しい。
3. completed history が source に残り、unfinished scope/closure criterion が exactly one owner。
4. reasoning kernel、lower-cost region、profile-transition handoff の owner/consumer が一意。
5. duration、range、packing fit、short-child merge/rebalance evidence が complete。
6. incoming semantic handoff が receiving child に一度だけ存在し、typed fields が complete。
7. validation timing/method/driver/bootstrap/aggregate owner declarations が reciprocal。
8. dependency、predecessor/successor、approval、moved-scope links が整合する。
9. stale reference が historical または incorrect と全件分類される。
10. current gate に `SPLIT_REQUIRED` が残らず、pre-split evidence だけが dated history として残る。
11. exact tracked/untracked planning paths の whitespace/diff check が通る。
12. apply は materialized diff または exact idempotent split を証明する。

validation defect が automatic projection defect なら Workflow が再生成する。semantic defect
だけを AI conflict Action に渡し、authority defect は human Decision にする。

## Continuation contracts

```text
SplitPhaseContinuation
  continuationId
  runId
  expectedRevision
  sourcePhaseIdentity
  state
  operationIdentity
  operationInputReference
  expectedResultType
  sourceSnapshotDigest
  proposalDigest?
  conflictSetDigest?
  invariantSetVersion
  completionContract
  evidenceContract
```

resume は continuation/run/revision/operation、全 digest、result type、conflict IDs、evidence
を fail-closed で検証する。Skill は child identity、next state、write paths、merge method を
自由形式で追加できない。result内容から別skill、agent、operation、command、Decisionを
呼び出すこともできない。

## Advance behavior

`advance` は次の semantic boundary、WAIT、terminal、typed internal failure まで automatic
transition と deterministic operation を bounded に連続実行する。normal apply では
inventory、proposal adoption、rendering、merge、write、validation の途中状態を Codex に
返さない。

preview は frozen proposal の human projection を terminal result として返す。later apply
は source/proposal digest を再検証し、current なら同じ proposal を再利用する。

## First executable specifications

1. complete current gate proposal から AI invocation 0 回で split が適用される。
2. proposal がなくても既知 work と明示済み boundary だけなら AI invocation 0 回で proposal
   が生成・適用される。
3. unknown work または boundary だけを `AssessNovelSplitWork` 1 回で補完し、その後の候補
   列挙、最適化、適用、検証には追加 AI を使わない。
4. same evidence snapshot/policy が同じ estimates、candidate set、selected partition を返す。
5. preview は proposal を返し planning files を変更しない。
6. exact re-run は `SPLIT_ALREADY_APPLIED` になり重複 note/child を作らない。
7. non-overlapping concurrent edit は non-colliding composition として Workflow three-way
   model で AI なしに保存される。
8. every `ModificationCollision` が `ResolveSplitMergeConflicts` または human Decision を
   発行する。
9. AI resolution は invariant validator/CAS を通らない限り適用されない。
10. identity collision と nested numbering は human Decision で停止する。
11. child numbering、packing、history、validation ownership の invariant violation は fail closed。
12. split run は child goal、SBT/runtime validation、commit、push を開始しない。
13. skill-backed provider と deterministic test provider が同じ typed results に対して同じ
    state/history/terminal outcome を生成する。
14. semantic AI count、automatic transition count、deterministic operation count、non-colliding
    composition count、AI-delegated `ModificationCollision` count、human Decision count を
    receipt から測定できる。
15. novel-work/boundary enrichment と semantic merge のAI resultが、それぞれ独立した
    deterministic admission/validationを通らずにproposalまたはwrite setを変更できない。
16. `SPLIT_REQUIRED` recommendation だけでは run を開始できず、明示的に選択された
    `sm-split-phase` invocation が独立 run を作る。split terminal 後も `sm-goal-phase` を
    自動開始しない。

## Source mapping

この定義は `SplitPhaseWorkflow` の source compatibility として、legacy
`cncf-split-phase` の apply/preview、parent-owned split decision、
numbering、six-hour packing/eight-hour ceiling、short-child merge/rebalance、reasoning-cost
optimization、completed-history preservation、aggregate validation policy、authoritative
document update、static validation、idempotent apply を保存する。

一方、agent disclosure、model routing、command invocation、direct Markdown editing、turn
scheduling は Workflow semantics から外し、provider/adapter/status projection へ移す。
