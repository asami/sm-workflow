# SM Goal Phase Workflow Definition

Status: normative design input for `GoalPhaseWorkflow`

Source workflow: legacy `cncf-goal-phase` skill and its Phase State Machine reference

Target: CML/CNCF Workflow and StateMachine runtime

## Purpose

`GoalPhaseWorkflow` は legacy `cncf-goal-phase` の制御意味論を参照しつつ、skill や
Codex 会話履歴に依存しない純粋な
Workflow / StateMachine として定義する。Workflow は一つの明示された Phase を
Phase Entry から Step/Slice delivery、Phase review、release closure まで進行させる。

Skill は Workflow の owner ではない。AI が担当する typed Action の provider として
IoC binding され、CNCF runtime が返す `Suspended(Continuation)` を受け取り、typed
Result / Evidence を resume する participant である。

## Phase 1 common-contract alignment

Phase 1 closure binds this profile to the CNCF Phase 77 common contract. Its
profile-specific start Operation delegates to `WorkflowStartRequest` and
projects the resulting `WorkflowHandle` plus first `Continuation` (or typed
terminal) as `WorkflowInteraction`. A current `Continuation` remains the
canonical typed semantic boundary and Skill/Codex wire correlation; it is not
a second root identity or lifecycle. A typed completion is a
`ContinuationResult` admitted through the registered completion Operation
identified by the current Continuation, such as `SubmitGoalPhaseWorkResult`.
The older `Suspended(Continuation)` and profile-specific continuation wording
below describes runtime state or application payload specialization only; it
cannot replace the common Continuation protocol.

## Profile and skill identity

- CML Workflow name / definition selector: `GoalPhaseWorkflow`
- bundled/public skill name: `sm-goal-phase`
- skill path in bundle: `skills/sm-goal-phase/SKILL.md`
- source compatibility reference: legacy `cncf-goal-phase`

`SkillBundleManifest` は `sm-goal-phase -> GoalPhaseWorkflow / StartGoalPhase` のbindingを
versioned fieldで宣言する。skill名をWorkflow definition IDまたはstart Operation identityとして
推測したり、文字列変換で導出しない。
one-shot launcher selector は registered `StartGoalPhase` Operation identity とする。MCP は
同じ typed Operation の adapter であり、skill は transport 固有 argv から Workflow 名を
組み立てない。

`GoalPhaseWorkflow` と `SplitPhaseWorkflow` のどちらを開始するかは、run 作成前に
人間が明示的に選択する。public skill を直接利用する場合は `sm-goal-phase` の選択自体が
その invocation authority になる。host/client は Phase evidence から別 Workflow を推奨
表示できるが、skill、Workflow、terminal result のいずれも別 Workflow を自動開始しない。

Phase Entry が split を要求した場合、この run は `SPLIT_REQUIRED` terminal result を返して
終了する。terminal result は source Phase、entry evidence/proposal reference、および
`SplitPhaseWorkflow` の advisory recommendation を含められるが、次の run の開始権限や
state migration を含めない。人間が `sm-split-phase` を選択したときだけ、独立した
`SplitPhaseWorkflow` run を新規作成する。

legacy `cncf-goal-phase` skill は rename、overwrite、forward、または implicit migration
しない。両 skill は別の entry point と別の durable state を持ち、長期併用する。
`sm-goal-phase` は legacy skill を内部呼び出しせず、versioned `sm-workflow` protocol
だけを利用する。同一 worktree/path の同時 mutation は workflow state 共有ではなく
`WorkspaceMutationLease` coordination で拒否する。

## Ownership boundary

### Workflow owns

- 一つの Phase identity と admitted repository boundary。
- Phase Entry、planning、Step delivery、review、repair、commit、closure の状態。
- guard、repair budget、review epoch、validation/commit prerequisite。
- typed Action input/result と `Completed | Suspended | Failed` の解釈。
- current revision、history、pending continuation、decision latch。
- 次に実行可能な Action と terminal outcome。

### Workflow does not own

- skill 名、skill path、prompt、Codex model、reasoning effort、agent role。
- 一 turn 一 state、commentary、`State End` の Markdown 表示。
- CML/public Action contract 内の SBT/Git/CLI raw command sequence。
- participant の spawn、resume、tool permission、UI transport。
- 別 Workflow の選択、開始、run/state の移送。
- SQLite/JDBC/path details。

raw command の具体的な argv と process lifecycle は Workflow runtime が所有する
operation provider の責務とする。それ以外は participant adapter、host policy、または
status projection の責務とする。

## Aggregate structure

一つの巨大な flat machine ではなく、次の composite として構成する。

```text
GoalPhaseWorkflow
  +-- InvocationLifecycle
  |     EntryAssessment
  |     ParentCapabilityAssessment
  |     GoalActive
  |     Terminal
  +-- PhaseDeliveryLifecycle
  |     Baseline
  |     Planning
  |     StepDelivery
  |     PhaseClosure
  +-- StepLifecycle
  |     Implementation
  |     AcceptanceReview
  |     Repair
  |     ReReview
  |     Commit
  +-- DecisionLifecycle
  |     None
  |     Pending
  |     Resolved
  |     Blocked
  `-- EvidenceLifecycle
        Required
        Current
        Stale
        Accepted
```

`GoalPhaseWorkflow` の source of truth は CML definition と generated ABI であり、
skill 内の prose state table ではない。

## Run context

```text
GoalPhaseRun
  workflowDefinitionId
  workflowDefinitionVersion
  runId
  revision
  phaseIdentity
  invocationSelectionReference
  authorityReference
  entryEvidenceSnapshotReference
  entrySemanticInputDigest?
  durationEstimationPolicyVersion
  admittedRepositories
  ownedPathBoundary
  planEpoch
  currentStep
  currentSlice
  planOwnerLease
  stepAcceptanceLedger
  phaseReviewLedger
  repairLedger
  validationLedger
  commitLedger
  pendingDecision?
  pendingContinuation?
  terminalOutcome?
```

Goal / Phase / Step / Slice はこの software-development profile の語彙であり、generic
CNCF Workflow ABI の必須語彙にはしない。

## Top-level states

| State | Meaning | On-entry Action class |
| --- | --- | --- |
| `ENTRY_PREPARING` | authority、既知duration evidence、closure evidenceをsnapshotする | deterministic operation |
| `ENTRY_SEMANTIC_ASSESSMENT` | 未知duration、closure/boundaryだけを補完する | semantic AI |
| `ENTRY_ADMITTING` | evidenceを検証しPROCEED/SPLIT/INVALIDを分類する | deterministic operation |
| `PARENT_CAPABILITY_ASSESSMENT` | planning demand と participant capability の適合を評価する | internal deterministic |
| `BASELINE_INVENTORY` | owned delta と evidence を immutable snapshot にする | deterministic operation |
| `BASELINE_REVIEW` | Phase-owned existing work を意味的に評価する | semantic AI |
| `BASELINE_ADMITTING` | baseline assessmentを検証・分類する | deterministic operation |
| `PROVISIONAL_INVENTORY` | matching PARKED manifest、checkpoint、drift を収集する | deterministic operation |
| `PROVISIONAL_ASSESSMENT` | frozen provisional evidence を意味的に評価する | semantic AI |
| `PROVISIONAL_ADMITTING` | provisional assessmentを検証・分類する | deterministic operation |
| `PLAN_DESIGNING` | Step/Slice、review profile、validation、ownership案を作る | semantic AI |
| `PLAN_ADMITTING` | plan coverage、authority、operation capability を検証して凍結する | deterministic operation |
| `IMPLEMENTING` | 現在の Slice/Step を実装する | semantic AI |
| `IMPLEMENTATION_VALIDATING` | result/deltaを検証しfocused validationを実行する | deterministic operation |
| `PROVISIONAL_VALIDATING` | adopted provisional Step を現 tree で検証する | deterministic operation |
| `STEP_REVIEW_PREPARING` | current Step review manifest を構築する | deterministic operation |
| `STEP_REVIEWING` | Step acceptance review を行う | semantic AI |
| `STEP_REVIEW_ADMITTING` | review dispositionとevidenceを検証・分類する | deterministic operation |
| `STEP_REPAIRING` | admitted Step blocker を修復する | semantic AI |
| `STEP_REPAIR_VALIDATING` | repair deltaとfocused validationを検証する | deterministic operation |
| `STEP_REREVIEW_PREPARING` | focused re-review manifest を構築する | deterministic operation |
| `STEP_REREVIEWING` | Step repair を focused re-review する | semantic AI |
| `STEP_REREVIEW_ADMITTING` | re-review dispositionとcycle lineageを分類する | deterministic operation |
| `STEP_COMMITTING` | accepted Step を exact manifest で commit する | deterministic operation |
| `PHASE_REVIEW_PREPARING` | plan epoch の complete Phase review manifest を構築する | deterministic operation |
| `PHASE_REVIEWING` | plan epoch に一度の comprehensive review を行う | semantic AI |
| `PHASE_REVIEW_ADMITTING` | Phase review dispositionを検証・分類する | deterministic operation |
| `PHASE_REPAIRING` | admitted Phase blocker を修復する | semantic AI |
| `PHASE_REPAIR_VALIDATING` | repair delta、validation、CAR lintを検証する | deterministic operation |
| `PHASE_REREVIEW_PREPARING` | focused closure re-review manifest を構築する | deterministic operation |
| `PHASE_REREVIEWING` | repair cycle の focused closure review を行う | semantic AI |
| `PHASE_REREVIEW_ADMITTING` | re-review dispositionとconvergenceを分類する | deterministic operation |
| `PHASE_RELEASE_COMMITTING` | final evidence を検証して release commit を作成する | deterministic operation |
| `FORCE_RELEASE_COMMITTING` | explicit authority 下の例外 release を作成する | deterministic operation |
| `AWAITING_DECISION` | human decision の continuation を保持する | human participant |

Terminal outcomes:

- `PHASE_CLOSED`
- `SPLIT_REQUIRED`
- `BOUNDARY_INVALID`
- `RESTART_REQUIRED`
- `BLOCKED`
- `FAILED`
- `CANCELLED`

`SPLIT_REQUIRED` の typed terminal payload は次とする。

```text
GoalPhaseTerminalResult
  outcome: SPLIT_REQUIRED
  runId
  revision
  phaseIdentity
  entryEvidenceSnapshotReference
  proposalReference?
  recommendation: WorkflowRecommendation
```

`WorkflowRecommendation` は `SplitPhaseWorkflow` と typed start-input reference を示す
advisory evidence であり、invocation authority ではない。host/client はこの payload を
候補表示に使えるが、別 run の `WorkflowInvocationSelection` を作成できるのは、人間が
その候補を明示選択した後だけである。

## Primary transition definition

### Entry and recovery

```text
start
  -> ENTRY_PREPARING

ENTRY_PREPARING -- all entry inputs resolved --> ENTRY_ADMITTING
ENTRY_PREPARING -- unknown duration / closure / boundary --> ENTRY_SEMANTIC_ASSESSMENT
ENTRY_PREPARING -- invalid authority --> BOUNDARY_INVALID

ENTRY_SEMANTIC_ASSESSMENT -- typed result submitted --> ENTRY_ADMITTING

ENTRY_ADMITTING -- split-required --> SPLIT_REQUIRED
ENTRY_ADMITTING -- boundary-invalid --> BOUNDARY_INVALID
ENTRY_ADMITTING -- proceed --> PARENT_CAPABILITY_ASSESSMENT
ENTRY_ADMITTING -- authority decision required --> AWAITING_DECISION
ENTRY_ADMITTING -- invalid semantic result --> FAILED

PARENT_CAPABILITY_ASSESSMENT -- suitable -->
  [matching PARKED] PROVISIONAL_INVENTORY
  [owned existing work] BASELINE_INVENTORY
  [otherwise] PLAN_DESIGNING
PARENT_CAPABILITY_ASSESSMENT -- attestation-required --> AWAITING_DECISION
PARENT_CAPABILITY_ASSESSMENT -- restart-required --> RESTART_REQUIRED

PROVISIONAL_INVENTORY -- snapshot ready --> PROVISIONAL_ASSESSMENT
PROVISIONAL_INVENTORY -- invalid authority --> AWAITING_DECISION

PROVISIONAL_ASSESSMENT -- result submitted --> PROVISIONAL_ADMITTING

PROVISIONAL_ADMITTING -- incomplete --> PLAN_DESIGNING
PROVISIONAL_ADMITTING -- complete-step --> PROVISIONAL_VALIDATING
PROVISIONAL_ADMITTING -- authority/drift decision --> AWAITING_DECISION
PROVISIONAL_ADMITTING -- invalid result --> FAILED

BASELINE_INVENTORY -- snapshot ready --> BASELINE_REVIEW
BASELINE_INVENTORY -- invalid authority --> AWAITING_DECISION

BASELINE_REVIEW -- result submitted --> BASELINE_ADMITTING

BASELINE_ADMITTING -- accepted --> PLAN_DESIGNING
BASELINE_ADMITTING -- material decision --> AWAITING_DECISION
BASELINE_ADMITTING -- invalid result --> FAILED
```

Skill-owned artifact restoration and Phase-base recovery are internal operations before
the affected state Action. Successful restoration resumes the same state and is not a
separate semantic Work Order.

### Step delivery loop

```text
PLAN_DESIGNING -- plan result submitted --> PLAN_ADMITTING

PLAN_ADMITTING -- accepted + implementation required --> IMPLEMENTING
PLAN_ADMITTING -- accepted + implementation already complete --> STEP_REVIEW_PREPARING
PLAN_ADMITTING -- semantic gap --> PLAN_DESIGNING
PLAN_ADMITTING -- authority/protected expansion --> AWAITING_DECISION
PLAN_ADMITTING -- invalid closed result --> FAILED

IMPLEMENTING -- result submitted --> IMPLEMENTATION_VALIDATING

IMPLEMENTATION_VALIDATING -- passed --> STEP_REVIEW_PREPARING
IMPLEMENTATION_VALIDATING -- semantic work incomplete --> IMPLEMENTING
IMPLEMENTATION_VALIDATING -- bounded blocker --> STEP_REPAIRING
IMPLEMENTATION_VALIDATING -- authority expansion --> AWAITING_DECISION
IMPLEMENTATION_VALIDATING -- invalid result --> FAILED

PROVISIONAL_VALIDATING -- passed --> STEP_REVIEW_PREPARING
PROVISIONAL_VALIDATING -- bounded repair --> STEP_REPAIRING
PROVISIONAL_VALIDATING -- authority expansion --> AWAITING_DECISION

STEP_REVIEW_PREPARING -- manifest ready --> STEP_REVIEWING
STEP_REVIEW_PREPARING -- stale/invalid evidence --> IMPLEMENTATION_VALIDATING

STEP_REVIEWING -- disposition submitted --> STEP_REVIEW_ADMITTING

STEP_REVIEW_ADMITTING -- clean + more work in Step --> PLAN_DESIGNING
STEP_REVIEW_ADMITTING -- clean + Step closure-ready --> STEP_COMMITTING
STEP_REVIEW_ADMITTING -- bounded blockers --> STEP_REPAIRING
STEP_REVIEW_ADMITTING -- scope/protected expansion --> AWAITING_DECISION
STEP_REVIEW_ADMITTING -- invalid disposition --> FAILED

STEP_REPAIRING -- result submitted --> STEP_REPAIR_VALIDATING

STEP_REPAIR_VALIDATING -- M0/M1 waiver admissible --> PLAN_DESIGNING | STEP_COMMITTING
STEP_REPAIR_VALIDATING -- M2 or review required --> STEP_REREVIEW_PREPARING
STEP_REPAIR_VALIDATING -- semantic repair incomplete --> STEP_REPAIRING
STEP_REPAIR_VALIDATING -- expansion/non-convergence --> AWAITING_DECISION
STEP_REPAIR_VALIDATING -- invalid result --> FAILED

STEP_REREVIEW_PREPARING -- manifest ready --> STEP_REREVIEWING
STEP_REREVIEWING -- disposition submitted --> STEP_REREVIEW_ADMITTING

STEP_REREVIEW_ADMITTING -- clean --> PLAN_DESIGNING | STEP_COMMITTING
STEP_REREVIEW_ADMITTING -- MCR tail admissible --> STEP_REPAIRING
STEP_REREVIEW_ADMITTING -- same-lineage narrowing and cycle < 3 --> STEP_REPAIRING
STEP_REREVIEW_ADMITTING -- stalled/expanded/cycle exhausted --> AWAITING_DECISION
STEP_REREVIEW_ADMITTING -- invalid disposition --> FAILED

STEP_COMMITTING -- success + remaining Steps --> PLAN_DESIGNING
STEP_COMMITTING -- success + all Steps accepted --> PHASE_REVIEW_PREPARING
STEP_COMMITTING -- failure --> AWAITING_DECISION
```

### Phase closure loop

```text
PHASE_REVIEW_PREPARING -- manifest ready --> PHASE_REVIEWING
PHASE_REVIEW_PREPARING -- stale/invalid evidence --> AWAITING_DECISION

PHASE_REVIEWING -- disposition submitted --> PHASE_REVIEW_ADMITTING

PHASE_REVIEW_ADMITTING -- clean --> PHASE_RELEASE_COMMITTING
PHASE_REVIEW_ADMITTING -- hygiene/development candidates only --> PHASE_RELEASE_COMMITTING
PHASE_REVIEW_ADMITTING -- bounded current-phase blockers --> PHASE_REPAIRING
PHASE_REVIEW_ADMITTING -- scope/upstream/protected expansion --> AWAITING_DECISION
PHASE_REVIEW_ADMITTING -- invalid disposition --> FAILED

PHASE_REPAIRING -- result submitted --> PHASE_REPAIR_VALIDATING

PHASE_REPAIR_VALIDATING -- passed --> PHASE_REREVIEW_PREPARING
PHASE_REPAIR_VALIDATING -- semantic repair incomplete --> PHASE_REPAIRING
PHASE_REPAIR_VALIDATING -- same-lineage deterministic failure --> PHASE_REPAIR_VALIDATING
PHASE_REPAIR_VALIDATING -- root/boundary expansion --> AWAITING_DECISION
PHASE_REPAIR_VALIDATING -- invalid result --> FAILED

PHASE_REREVIEW_PREPARING -- manifest ready --> PHASE_REREVIEWING
PHASE_REREVIEWING -- disposition submitted --> PHASE_REREVIEW_ADMITTING

PHASE_REREVIEW_ADMITTING -- clean --> PHASE_RELEASE_COMMITTING
PHASE_REREVIEW_ADMITTING -- MCR tail admissible --> PHASE_REPAIRING
PHASE_REREVIEW_ADMITTING -- same-lineage narrowing and cycle < 3 --> PHASE_REPAIRING
PHASE_REREVIEW_ADMITTING -- stalled/expanded/cycle exhausted --> AWAITING_DECISION
PHASE_REREVIEW_ADMITTING -- invalid disposition --> FAILED

PHASE_RELEASE_COMMITTING -- success --> PHASE_CLOSED
PHASE_RELEASE_COMMITTING -- failure --> AWAITING_DECISION

AWAITING_DECISION -- approved entry/replan/repair --> ENTRY_PREPARING | PARENT_CAPABILITY_ASSESSMENT | PLAN_DESIGNING
AWAITING_DECISION -- explicit single-repository force release --> FORCE_RELEASE_COMMITTING
AWAITING_DECISION -- defer/unsupported/unanswered limit --> BLOCKED

FORCE_RELEASE_COMMITTING -- success --> PHASE_CLOSED
FORCE_RELEASE_COMMITTING -- failure --> FAILED
```


## Step closure request protocol

Step closure is initiated by an explicit application Operation `RequestStepClose`; it is not entered merely because implementation or a proactive Skill review returned success.

Before requesting close, the Skill brings program artifacts and Skill-owned planning/management files to the latest state it believes satisfies the Step. It may attach zero or more typed `ReviewEvidence` records from proactive reviews.

`RequestStepClose` establishes durable closure intent and submits the current artifact snapshot plus available evidence. Workflow admission then determines whether closure requirements are satisfied.

```text
RequestStepClose
  -> STEP_CLOSURE_EVALUATING
       -> evidence sufficient -> deterministic final validation -> STEP_COMMITTING
       -> scoped review insufficient -> STEP_REVIEW_PREPARING(full)
       -> stale review after bounded repair -> STEP_REREVIEW_PREPARING(focused/full)
       -> blockers -> STEP_REPAIRING
       -> authority ambiguity -> AWAITING_DECISION
```

A review record is evidence, not progression authority. Closure evaluation checks review scope, reviewed artifact revision/snapshot, freshness, disposition, unresolved finding lineage, validation evidence and commit prerequisites.

A proactive scoped review can therefore be accepted as useful evidence while still producing a `ReviewStep(STEP_FULL)` Continuation. Conversely, a fresh full-Step review that satisfies the current closure policy is reused and must not be repeated merely because closure was requested later.

After the initial close request, completion of Review/Repair/ReReview Continuations automatically resumes closure evaluation through the registered completion Operation and CNCF bounded progression. The Skill does not issue another close request after each result.

The final commit is Workflow-owned deterministic work and closes the admitted program and planning/management-file state together. A successful Step commit remains execution evidence; the Skill-side meaning of planning items is owned by the Skill, but those management files must already reflect the latest reconciliation before final commit.

## Action contracts

### Semantic/deterministic separation rule

すべての semantic AI Action は次の三段境界を通る。

```text
deterministic prepare
  -> immutable input snapshot / manifest
  -> semantic AI Action
  -> typed semantic result
  -> deterministic admission / validation / persistence
  -> next transition
```

AI Action は意味内容だけを返す。`nextState`、command/argv、validation pass/fail、ledger
更新、repair-cycle count、commit readiness、retry policy を返さない。edit/repair Action が
lease-bound workspace を変更する場合も、その変更を自分で acceptance 済みとは判定しない。

deterministic operation は snapshot、schema、authority、diff ownership、validation receipt、
review disposition vocabulary、cycle lineage、commit precondition を検証するが、未決の仕様、
architecture、finding severity、semantic boundary を発明しない。決定的に分類不能なら AI
Work Order または human Decision で停止し、heuristic に次stateを選ばない。

AI result から commit/terminal state へ直接遷移する edge は禁止する。必ず対応する
`*_ADMITTING` または `*_VALIDATING` state を経る。

### Semantic AI Actions

次は Skill host から見た executable command ではなく、CML StateMachine Required
Operation として定義する。Workflow/generated binding が一つの Required Operation を
fully materialized `AIWorkRequest` にし、local skill はその一件を external provider として
実行してmatching resultを返すだけである。skillがoperationを選択またはdispatchしない。

| Operation | Input | Result |
| --- | --- | --- |
| `AssessPhaseEntrySemantics` | unresolved duration items、closure/boundary edges、frozen authority | `PhaseEntrySemanticInputs | PhaseDecisionNeeded` |
| `AssessBaseline` | phase authority、owned delta、existing evidence | `BaselineAssessment` |
| `AssessProvisionalAdoption` | PARKED manifest、checkpoint ranges、current drift | `ProvisionalAdoptionAssessment` |
| `PlanPhaseDelivery` | phase scope、budget、current ledgers | `PhaseDeliveryPlan` |
| `ImplementSlice` | frozen Slice、owned paths、acceptance contract | `ImplementationResult` |
| `ReviewStep` | frozen Step boundary、diff、validation evidence | `ReviewDisposition` |
| `RepairStep` | admitted finding IDs、frozen repair frontier | `RepairResult` |
| `ReReviewStep` | previous review、repair evidence | `ReviewDisposition` |
| `ReviewPhase` | plan epoch、accepted Steps、complete Phase delta | `ReviewDisposition` |
| `RepairPhase` | blocker lineage、cycle ledger、repair frontier | `RepairResult` |
| `ReReviewPhase` | full review、cycle evidence、validation receipts | `ReviewDisposition` |

`PhaseDeliveryPlan` は semantic work decomposition と acceptance intent、
`ImplementationResult` / `RepairResult` は変更内容とsemantic completion claim、
`ReviewDisposition` は finding と意味的判定だけを表す。いずれも Workflow transition、
command execution、validation acceptance、commit authorization を含まない。

`AssessPhaseEntrySemantics` は既知作業の決定的duration estimateを上書きせず、
`UNRESOLVED_NOVEL` workのrangeと未確定closure/boundaryだけを返す。split/proceed/invalid
の最終分類は `ENTRY_ADMITTING` がclosed rulesで行う。

これらの provider は実行 placement により `Completed(Result)` または
`Suspended(Continuation)` を返せる。Workflow definition は AI、human、direct、test
provider のどれが選ばれたかで state semantics を変えない。

### Human Actions

- `ProvideParentProfileAttestation`
- `ResolvePhaseDecision`
- `AuthorizeMaterialSpecificationChange`
- `SelectForceRelease`

Human participant は AI skill binding と別の provider binding を持つ。同じ
Continuation identity/revision/staleness contract は共有する。

### Deterministic/Internal Operations

- `ResolvePhaseAuthority`
- `PreparePhaseEntryEvidence`
- `EstimateKnownPhaseEntryWork`
- `AdmitPhaseEntrySemantics`
- `ClassifyPhaseEntry`
- `VerifyOrRestorePhaseBase`
- `SnapshotBaselineEvidence`
- `AdmitBaselineAssessment`
- `SnapshotProvisionalEvidence`
- `AdmitProvisionalAssessment`
- `ValidateAndFreezePhasePlan`
- `InspectOwnedImplementationDelta`
- `VerifyPlanComplete`
- `RunFocusedValidation`
- `RunFullPhaseValidation`
- `RunApplicableCarLint`
- `PrepareStepReviewManifest`
- `AdmitStepReviewDisposition`
- `ValidateStepRepairResult`
- `PrepareStepReReviewManifest`
- `AdmitStepReReviewDisposition`
- `PreparePhaseReviewManifest`
- `AdmitPhaseReviewDisposition`
- `ValidatePhaseRepairResult`
- `PreparePhaseReReviewManifest`
- `AdmitPhaseReReviewDisposition`
- `ClassifyRepairConvergence`
- `BuildReviewManifest`
- `CreateStepCommit`
- `CreatePhaseReleaseCommit`
- `CreateForceReleaseCommit`
- `PersistTransitionAndEvidence`

raw shell、任意 script、SBT argv、Git argv は Workflow Action contract にしない。
各 operation は typed input/output、authorization、idempotency、failure semantics、
receipt を持つ provider に bind する。

### External command execution boundary

Git と SBT を含む外部 command の execution owner は、可能な限り Workflow runtime
とする。Skill/AI は procedural な command の選択、argv の構築、process 起動、
retry、終了判定を行わない。

```text
Workflow advance
  -> typed deterministic Action
  -> component-local operation provider
  -> exact external process execution
  -> typed result + receipt/evidence
  -> persisted transition
  -> continue advance
```

- `BuildProject`、`RunTests`、`RunExecutableSpecification` の provider が必要な
  top-level SBT process を起動し、serialization、terminal completion、exit status、
  output evidence を管理する。
- `InspectChanges`、`StageChanges`、`CreateStepCommit`、
  `CreatePhaseReleaseCommit`、`CreateForceReleaseCommit` の provider が exact Git
  boundary、staged paths、tree identity、commit receipt を管理する。
- formatter、generator、linter、package manager、compiler、test runner、local service
  client、artifact inspector 等も、下記 admission 条件を満たせば Workflow-owned
  deterministic operation として実行する。
- provider は WorkflowRun revision、authorization、operation identity、idempotency key
  に束縛される。stale operation result は state transition に使用しない。
- command failure は typed operation failure として Workflow に戻し、宣言済みの
  retry / WAIT / DECISION / failure transition で処理する。
- raw stdout/stderr は必要に応じて artifact storage に置き、Workflow history には
  digest と typed reference を保持する。
- Skill へ procedural external command execution を要求する Work Order を発行しない。

External command を Workflow provider に admission する条件:

1. typed input/output と command purpose が固定できる。
2. executable identity、working directory、argv、environment が bounded に解決できる。
3. allowed read/write roots、network、credential、process lifecycle が明示できる。
4. completion、failure、timeout、retry、idempotency を閉じた結果へ分類できる。
5. stdout/stderr、生成物、tree mutation を receipt/evidence として検証できる。
6. 実行中の意味判断や新しい command 選択を AI に要求しない。

条件を満たさない exploratory command は自動的に Workflow provider へ昇格させない。
まず semantic AI Action として扱い、command pattern と完了条件が安定した時点で typed
deterministic operation に昇格する。

AI host の tool sandbox は緩和しない。AI に外部 command 実行権限を追加する代わりに、
Workflow runtime/provider が Workflow の管理状態の中で allowlist 済み typed
operation だけを実行する。provider 自身が operation-specific capability、mutation
root、network/credential policy、timeout、監査 receipt を強制し、Continuation や
Work Order が追加権限を付与しないことを保証する。

管理実行の security conditions:

- command は provider registry に登録された operation identity/version から解決し、
  AI/Skill が executable や自由形式 argv を注入できない。
- 実行前に current Workflow state、expected revision、guard、authorization、precondition、
  idempotency key を検証する。
- typed input は schema validation 後に provider が安全な argv へ射影し、shell parsing
  や arbitrary command interpolation を使用しない。
- state ごとの allowed operation set を閉じ、同じ command でも許可されていない state
  からは実行しない。
- process result、side effects、artifact/tree identity、receipt を同じ WorkflowRun と
  operation attempt に相関付ける。
- provider registry 外の command、generic shell、arbitrary script は fail-closed で拒否する。

## Continuation contract

Semantic Action が external provider に配置された場合のみ、次を発行する。

```text
GoalPhaseContinuation
  continuationId
  runId
  expectedRevision
  phaseIdentity
  planEpoch
  state
  operationIdentity
  operationInput
  expectedResultType
  contextSnapshot
  completionContract
  evidenceContract
  capabilityConstraints
  expiryOrWakeCondition?
```

Resume input:

```text
GoalPhaseContinuationResult
  continuationId
  runId
  expectedRevision
  operationIdentity
  result
  evidenceReferences
  participantIdentity
  idempotencyKey
```

resume は identity、revision、operation、context snapshot、result type、completion、
evidence を fail-closed で検証する。Skill は `nextState`、transition、repair cycle、
commit readiness を返さない。またresult内容から別skill、agent、operation、commandを
呼び出さず、次の処理選択をWorkflowのadmission/`advance`へ返す。

## Guards and invariants

1. 一つの WorkflowRun は一つの明示された Phase だけを完了する。
2. successor Phase や split child を自動的に開始しない。
3. Phase Entry が `PROCEED` になるまで goal-active state へ入らない。
4. plan epoch ごとに comprehensive Phase review は一度だけ行う。
5. automatic repair は同じ root/boundary 内の単調収束に限り最大三 cycle とする。
6. protected/public/architecture/repository-owner expansion は human decision で停止する。
7. Step commit は accepted Step review と current validation evidence を必要とする。
8. Phase release は全 Step acceptance、full Phase review、final validation、closure
   ledger、clean/preserved tree evidence を必要とする。
9. force release は明示された human authority と単一 mutation repository を必要とする。
10. provisional checkpoint は implementation evidence であり acceptance ではない。
11. deterministic failure を自動的に AI Work Order へ変換しない。
12. status projection、conversation、skill memory を durable state の正本にしない。
13. semantic AI Action は immutable prepared input だけを受け、typed semantic result だけを返す。
14. semantic AI result は対応する deterministic admission/validation を通るまで state、ledger、acceptance、commit readiness を変更しない。
15. deterministic operation は意味判断を補完せず、閉じた規則で分類不能なら semantic/human boundary で停止する。
16. validation、lint、manifest construction、diff ownership、review vocabulary admission、repair convergence、commit は AI Action に含めない。
17. Phase Entryのauthority収集、既知duration計算、split/proceed分類はdeterministicとし、AIは未知durationと未確定closure/boundaryだけを補完する。
18. `SPLIT_REQUIRED` はこの run の terminal result であり、`SplitPhaseWorkflow` を開始したり
    current run/stateを移送したりしない。

## Advance behavior

`advance` は current state から、次のいずれかまで bounded に progression する。

- external semantic Action の `Suspended(Continuation)`。
- human decision Continuation。
- retry/wake condition を持つ WAIT。
- terminal outcome。
- typed internal failure。

途中の authority verification、ledger update、manifest validation、Git/SBT その他の
外部 command を含む focused/full validation、generation、inspection、staging、commit
などの deterministic operation は Workflow runtime が実行し、model turn を要求しない。
旧スキルの「exactly one state per parent-task goal turn」は host delivery policy として
廃止し、同一 `advance` 内で安全な internal transition を連続実行する。

## Status projection

旧 skill の `Goal State` / `State End` は public state machine そのものではなく、
`GetWorkflowRunStatus` と `GetWorkflowRunHistory` から生成する human projection とする。
projection は read-only で、transition を発生させない。

Agent Use、model/effort、runtime source、tool permission は Action execution receipt の
host metadata として保持できるが、CML state、guard、transition の条件にはしない。

## First executable specification

Skill を起動しない deterministic test provider で少なくとも次を証明する。

1. clean Phase が entry、PLAN、Step implement/review/commit、Phase review、release を経て閉じる。
2. Step blocker が repair/re-review から同じ Step commit へ戻る。
3. Phase blocker が最大三 cycle で収束する。
4. cycle 3 の unresolved blocker が Decision Continuation を返す。
5. stale AI result が revision/context mismatch で拒否される。
6. internal operations の連続実行は AI/model invocation count を増やさない。
7. skill binding と deterministic test binding で同じ state/history/terminal result になる。
8. admitted external command は AI tool call と AI sandbox escalation を必要とせず、
   Workflow provider の bounded execution receipt を残す。
9. planning、implementation、review、repair、re-review の各AI resultが必ず別の
   deterministic admission/validation stateを通り、直接commit/terminalへ遷移しない。
10. semantic providerがvalidation、command、next-state、cycle-count、commit-readinessを
    返そうとした場合、schema validationで拒否される。
11. deterministic classifierがsemantic ambiguityを検出した場合、推測せずContinuationまたは
    human Decisionを返す。
12. known-entry evidenceだけのPhaseはentry AI invocation 0回で分類され、未知部分がある
    Phaseも `AssessPhaseEntrySemantics` resultをdeterministic admissionしてから分類される。
13. split-required entry は `SPLIT_REQUIRED` で終了し、advisory recommendation を返しても
    `sm-split-phase`、`SplitPhaseWorkflow`、child goal のいずれも自動開始しない。

## Source mapping

この定義は `GoalPhaseWorkflow` の source compatibility として、legacy
`cncf-goal-phase` の次を保存する。

- pre-goal Phase Entry と parent capability gate。
- baseline/provisional recovery route。
- Phase -> Step -> Slice delivery。
- Step acceptance review、repair、re-review、commit。
- plan epoch ごとの full Phase review。
- bounded three-cycle closure repair。
- human decision latch と force-release exception。
- final validation、closure ledger、release commit。

一方、agent selection、model tuning、turn scheduling、Markdown reporting、command routingは
Workflow semantics から外し、provider/adapter policy へ移す。
