# Phase 1: Application Workflows on the CNCF Common Contract

Status: planned

## Goal

Phase 1 は、CNCF Phase 77 の common
Start/Handle/Continuation/Result contract を specialize する
software-development application layer を executable specifications で完成させる。
成果は次に限定する。

- GoalPhaseWorkflow、SplitPhaseWorkflow、RepositorySyncWorkflow
- application-specific typed Value Objects
- 三つの CML Workflow / StateMachine definitions
- deterministic/test Providers と fixtures
- ReasoningLevel mapping policy
- Presentation specialization
- schema-versioned JSON codecs / fixtures
- Executable Specifications

Phase 1 は production-ready local runtime を作る Phase ではない。

## Completion Statement

Phase 1 は、三つの application Workflow が CNCF の common contract に binding
され、deterministic progression が AI turn を必要とせず、semantic boundary だけが
typed Continuation として現れることを executable specifications で再現可能に示した
時点で完了する。

唯一の closure authority は
[Phase 1 Executable-Specification Checklist](phase-1-executable-specification-checklist.md)
である。

## Upstream Dependencies

Phase 1 は、次の versioned handoff に依存する。

### Cozy Phase 62.3

Cozy Phase 62.3 は CML の first-class `WORKFLOW` declaration と generated ABI
を提供する producer boundary である。StateMachine / Composite StateMachine
semantics、typed Operation reference、automatic progression と typed semantic
boundary の区別を含む release 済み handoff を使用する。

Cozy 側へ `advance`、Workflow persistence、SQLite、lease、public skill を再導入
しない。

### CNCF Phase 77

CNCF Phase 77 は generated Workflow ABI を admission し、common Workflow
contract と bounded deterministic progression を所有する。Phase 1 は少なくとも
次を consumer として利用する。

- `WorkflowStartRequest` / `WorkflowStartResult`
- `WorkflowHandle`
- closed `Continuation`
- `ContinuationRequest` / `ContinuationResult` / `WorkResult`
- Workflow / Continuation identity と expected revision
- `ContextSnapshot`
- typed Result / Evidence / `ExecutionEvidence`
- schema-versioned fail-closed JSON codecs
- minimum `Presentation` / `Progress`

`sm-workflow` はこれらの generic contract を再定義しない。

## Entry Criteria

- Cozy Phase 62.3 と CNCF Phase 77 の compatible release handoff を確認できる。
- exact ABI version、fixture identity、repository revision を記録できる。
- generated ABI が automatic progression と semantic boundary を識別できる。
- CML Action が raw command ではなく typed Operation を参照できる。

条件を満たさない場合、暫定 Workflow DSL、generated-ABI copy、CNCF ABI
emulator を `sm-workflow` に作って迂回しない。

## In Scope

- 三つの application Workflow の typed start/work/result/terminal payload
- CNCF common contract に binding する CML Workflow / StateMachine definitions
- deterministic/test Providers と fixtures
- application Presentation content
- CNCF `ReasoningLevel` を入力とする versioned mapping policy
- application payload を含む JSON round-trip / fail-closed fixtures
- Start → semantic boundary → typed result → next boundary → terminal を検証する
  Executable Specifications

Skill/Host が外部 WorkOrder を dispatch した場合だけ、compatible worker profile
と mapping-policy version を `ExecutionEvidence` に記録する。deterministic/local
Provider は架空のworker profileを記録せず、evidenceをWorkflow guardやtransition
inputに使用しない。

## Out of Scope

次は Phase 1 completion に含めず、実際の connectivity/use evidence に基づく
後続の operational-hardening Phase で扱う。

- production public skills / catalog
- standalone / CAR bundle distribution
- broad CLI / UI
- production SQLite operational profile
- lease / restart / concurrency / recovery hardening
- production provider dispatch policy
- operational Git / SBT providers and local closing
- cost dashboards
- MCP / server adapters

Executable Specification に必要な小さな fixture adapter は許容するが、production
operation surfaceへ拡張しない。

## Design Invariants

1. CNCF Phase 77 の generic Value Objects と lifecycle が正本である。
2. `sm-workflow` は application payload、definition、specialization policyだけを所有する。
3. automatic progression は AI/model turnを発生させない。
4. semantic boundaryを推測で越えない。
5. `WorkflowInteraction` は framework `WorkflowHandle` と current
   `Continuation` のprojectionであり、別のgeneric protocolではない。
6. concrete worker selectionはWorkflow guardやtransitionを制御しない。
7. JSONはValue Objectのencodingであり、独立した正本モデルではない。

## Decision and Historical Records

- [Phase 1 common-contract scope reconciliation](../journal/2026/09/2026-09-20-phase-1-common-contract-scope-reconciliation.md)
  — current scopeを確定したdecision record
- [Historical Phase 1 design](../notes/phase-1-pre-reconciliation-design.md)
  — SQLite、CLI、CAR、SkillBundle、cost metrics等を含む旧設計の保存版
- [Historical operational checklist](phase-1-checklist.md)
  — superseded planning inventory
- [Deferred deterministic closing addendum](phase-1-deterministic-closing-addendum.md)
  — 後続のconnectivity / operational-hardening候補

これらのhistorical/deferred資料はPhase 1のscopeまたはclosure条件を拡張しない。
