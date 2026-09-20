# Phase 1: Application Workflows on the CNCF Common Contract

Status: planned

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

### CNCF Phase 77

CNCF Phase 77 は generated Workflow ABI を admission し、common Workflow contract と
bounded deterministic progression を所有する。Phase 1 は少なくとも次を consumer として
利用する。

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

## Entry Criteria

- Cozy Phase 62.3 と CNCF Phase 77 の compatible release handoff を確認できる。
- exact ABI version、fixture identity、repository revision を記録できる。
- generated ABI が automatic progression と semantic boundary を識別できる。
- CML Action が raw command ではなく typed Operation を参照できる。

条件を満たさない場合、暫定 Workflow DSL、generated-ABI copy、CNCF ABI emulator を
`sm-workflow` に作って迂回しない。

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

- `StartGoalPhase(GoalPhaseStartInput)`
- `StartSplitPhase(SplitPhaseStartInput)`
- `StartRepositorySync(RepositorySyncStartInput)`
- `SubmitGoalPhaseWorkResult(GoalPhaseWorkResult)`
- `SubmitSplitPhaseWorkResult(SplitPhaseWorkResult)`
- `SubmitRepositorySyncWorkResult(RepositorySyncWorkResult)`
- application-specific `ResolveDecision(...)` operations

起動 Operation は application input を CNCF generic `WorkflowStartRequest` の payload へ
写像する。結果提出と Decision Operation は current `WorkflowHandle` と
`Continuation` に対する CNCF generic Result/Decision submission を呼ぶ。これらは別の
generic lifecycle、`advance` protocol、または raw command surface を導入しない。

CML Action は exact typed Operation reference を持つ。Skill は Action/Continuation から
選択済みの Operation と schema-validated payload を受け取り、必要な semantic AI work を
行った typed result を同じ Operation boundary へ返すだけである。Skill は next state、
Operation 選択、command argv、commit readiness、retry、または別 Workflow の開始を決めない。

deterministic/test Provider は同じ Operation contract を実装し、fixture で正常・拒否・
semantic boundary・terminal のすべてを検証する。production Git/SBT provider、任意 command
execution、または broad CLI はこの Phase の Operation 実装に含めない。

### Launcher-provided command interface

CNCF launcher は component に登録された CML Operation を machine-readable CLI として
自動公開する。component assembly、generated Operation registration、Provider binding が正しく
構成されれば、`sm-workflow` は追加の CLI 実装なしに launcher 経由で動作する。Skill が使用する
command interface はこの launcher-provided surface であり、
`sm-workflow` は独自の executable、CLI protocol、または argv grammar を所有しない。

launcher は registered CML Operation の exact identifier と schema-validated JSON request を
受理して CNCF generic response JSON を返す。Skill は launcher 経由で選択済み Operation の
request を送り、response の typed `Continuation` / Result を返すだけである。Operation を
推測・選択せず、raw command、任意 shell argv、直接 state mutation、独自の
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
