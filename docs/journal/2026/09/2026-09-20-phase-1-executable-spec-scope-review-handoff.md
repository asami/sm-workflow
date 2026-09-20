# Phase 1 Executable-Specification Scope Review Handoff

Date: 2026-09-20
Status: review handoff / planning reconciliation input
Target: sm-workflow Phase 1 and checklist
Upstream baseline: CNCF Phase 64 -> 64.2 -> 77

## Review conclusion

CNCF Phase 64 / 64.2 / 77 are now aligned around the minimum critical path for sm-workflow. Phase 77 owns the common typed Workflow contract, including generic Start, WorkflowHandle, Continuation kinds, WorkOrder/Result/Evidence envelopes, abstract ExecutionRequirement/ReasoningLevel, minimum Presentation, and schema-versioned fail-closed Skill/Codex JSON encoding.

sm-workflow Phase 1 still contains scope and ownership assumptions from the earlier “production-ready local workflow core” plan. Phase 1 must now be reduced to the agreed completion line: **the three sm-workflow application Workflows execute reproducibly as executable specifications on the CNCF Phase 77 foundation**.

## Required architectural correction

sm-workflow must not redefine CNCF generic Workflow concepts.

The following are CNCF Phase 77-owned common Value Objects/contracts:

- WorkflowStartRequest / WorkflowStartResult
- WorkflowHandle
- Continuation and its WORK_ORDER / DECISION / WAIT / TERMINAL variants
- WorkOrder envelope
- ExecutionRequirement
- ReasoningLevel
- CapabilityRequirement / RiskLevel
- WorkResult / ContinuationResult envelope
- Evidence / ExecutionEvidence common envelope
- Presentation / Progress common structure
- common schema/version/fail-closed JSON wire envelope

Remove or rewrite Phase 1 sections that describe these as an sm-workflow “Generic workflow model” or “Generic Workflow JSON Protocol” owned by the application.

sm-workflow supplies application-specific typed payloads and policy:

```text
CNCF WorkflowStartRequest[GoalPhaseStartInput]
CNCF WorkOrder[GoalPhaseWorkInput]
CNCF WorkResult[GoalPhaseWorkResult]
CNCF Terminal[GoalPhaseResult]

CNCF WorkflowStartRequest[SplitPhaseStartInput]
...

CNCF WorkflowStartRequest[RepositorySyncStartInput]
...
```

## Revised Phase 1 goal

Recommended goal statement:

> Define the sm-workflow application payload types and three software-development Workflow definitions, bind them to the CNCF Phase 77 typed Workflow runtime, and prove their intended behavior through deterministic executable specifications.

Phase 1 is not required to deliver production-quality skills, CLI, datastore operations, installation/distribution, or operational hardening.

## Revised completion statement

Phase 1 completes when:

1. GoalPhaseWorkflow, SplitPhaseWorkflow, and RepositorySyncWorkflow are represented as accepted CML/generated definitions.
2. Their application-specific start/work/result/terminal payloads are typed and versioned.
3. They execute against the CNCF Phase 77 Start/Continuation/Result/Terminal contract.
4. deterministic/test Providers drive the scenarios without production external infrastructure.
5. automatic/deterministic progression requires no AI turn.
6. semantic boundaries produce the expected WorkOrder/Decision/Wait/Terminal values.
7. WorkOrders consume CNCF abstract ReasoningLevel and sm-workflow demonstrates a versioned Host/Skill mapping policy.
8. application content populates CNCF Presentation and can be rendered for a test/Codex-console projection without presentation text controlling progression.
9. schema-versioned JSON fixtures round-trip the CNCF envelope plus sm-workflow application payloads.
10. executable specifications for all three Workflows are reproducible.

Production connectivity and operational usability are explicitly post-Phase-1 work.

## Keep in Phase 1

### Application model

- GoalPhase application Value Objects.
- SplitPhase application Value Objects.
- RepositorySync application Value Objects.
- application-specific Decision/Wait/Terminal payloads only where needed.
- application-specific Evidence payloads where needed.

### Workflow definitions

- GoalPhaseWorkflow.
- SplitPhaseWorkflow.
- RepositorySyncWorkflow.
- automatic versus semantic-boundary classification through admitted CML/CNCF semantics.
- deterministic admission/validation around semantic AI results.
- no AI ownership of next state, command execution, commit readiness, or Workflow routing.

### Executable-spec infrastructure

- deterministic/test Providers.
- fixed fixtures.
- application JSON codec/schema fixtures.
- minimal test renderer for CNCF Presentation.
- minimal versioned ReasoningLevel -> test worker-profile mapping used only to prove the application boundary.
- tests for accepted/rejected typed results and semantic progression.

### Evidence

- exact Cozy/CNCF handoff versions used by the specs.
- reproducible spec commands/results.
- evidence that sm-workflow did not implement a second generic Workflow runtime/protocol.

## Remove from Phase 1 completion requirements

The following current Phase 1 material should be deleted from the completion contract or moved to post-Phase-1 follow-up planning.

### Production datastore/runtime

- Textus-managed production SQLite as the Phase goal.
- SQLite path/location policy.
- WAL/busy-timeout/foreign-key operational tuning.
- production migration/retention policy.
- restart/concurrency/lease hardening beyond the minimum deterministic fixture needed by specs.

Phase 77 owns the provider-neutral persistence SPI. A deterministic/in-memory/test provider is sufficient for Phase 1 executable specifications unless a small persistence fixture is necessary to prove a specific application semantic.

### Full public CLI

Do not require the full current command surface:

```text
run start
run advance
work start
work complete/fail
decision resolve
run status/history/cancel
```

A minimal test harness/adapter may invoke CNCF Start/Continuation/Result operations. Production CLI ergonomics are post-Phase-1.

### Production public skills

Do not require production-ready:
- sm-goal-phase skill;
- sm-split-phase skill;
- sm-repository-sync skill;
- standalone/CAR skill bundle completeness;
- install/update/uninstall/catalog integration.

After Phase 1, manually build the skills while performing Codex connectivity tests and real development use.

If tiny fixture skill stubs help prove JSON exchange, keep them explicitly test-only and do not make their operational quality a closure criterion.

### Cost/operations breadth

Move the broad cost metric set, operational dashboards, production recovery documentation, remote-stability hardening, complete repository policy, and operational receipt policy out of Phase 1.

Executable specs may assert the structural property that automatic transitions do not generate semantic WorkOrders/AI turns. They do not need a production cost-observability subsystem.

### Runtime extensions

Do not add as Phase 1 blockers:

- Retry / Timeout;
- Deadline / Timer / Cancellation;
- rich FailurePolicy/idempotency framework;
- Phase 80/85/86 features;
- MCP/server adapter;
- remote transport/service discovery;
- production AI provider/model dispatch.

These are learned/refined from post-Phase-1 connectivity and use.

## Public operation section

The current Phase 1 “Public operation contract” should be rewritten.

Do not define a second generic Start/Advance/Continuation protocol in sm-workflow.

Instead describe application specialization, for example:

```text
startGoalPhase(input: GoalPhaseStartInput)
  -> CNCF WorkflowStartRequest[GoalPhaseStartInput]
  -> WorkflowHandle + Continuation

startSplitPhase(input: SplitPhaseStartInput)
  -> CNCF WorkflowStartRequest[SplitPhaseStartInput]

startRepositorySync(input: RepositorySyncStartInput)
  -> CNCF WorkflowStartRequest[RepositorySyncStartInput]
```

Subsequent progression uses CNCF Continuation/Result contracts. Application convenience operations may exist, but their semantics are projections over CNCF common Value Objects.

## Reasoning mapping

Keep the mapping policy in sm-workflow, not the abstract vocabulary.

```text
CNCF WorkOrder
  reasoningLevel = DEEP
        |
        v
sm-workflow Host/Skill mapping policy vN
        |
        v
test/concrete worker profile
        |
        v
ExecutionEvidence
```

Phase 1 only needs to prove that this mapping boundary works. Production model-selection optimization is later.

## Presentation

sm-workflow owns application content, not Presentation structure.

Example fixture expectation:

```text
CNCF Presentation
  title = "Implement Phase Step"
  currentSituation = "Planning accepted"
  nextAction = "Implement CWF-..."
  progress = ...
```

A test renderer may produce human-readable console text. No spec may parse that text to decide Workflow behavior.

## Recommended Phase 1 structure

Rewrite Phase 1 around approximately these sections:

1. Goal and completion line.
2. Upstream handoff: Cozy Workflow producer + CNCF Phase 77.
3. CNCF common-contract dependency.
4. sm-workflow application Value Objects.
5. Three Workflow definitions.
6. Deterministic/test Provider bindings.
7. Reasoning-profile mapping specialization.
8. Presentation content specialization.
9. Executable-spec scenarios.
10. JSON fixture/codec acceptance.
11. Closure evidence.
12. Explicit post-Phase-1 connectivity/hardening follow-up.

Avoid retaining production implementation sections merely as “non-goals” if they obscure the much smaller Phase 1.

## Recommended acceptance groups

### A1 — Common-contract specialization
- No generic Workflow VO/protocol is redefined.
- All application payloads are typed/versioned.
- CNCF Start/Continuation/Result/Terminal contracts are used directly.

### A2 — Workflow semantics
- All three Workflows reach expected semantic boundaries and terminal results.
- automatic progression is deterministic and emits no AI WorkOrder.
- invalid/ambiguous application results fail closed.

### A3 — AI semantic boundary
- WORK_ORDER carries CNCF ExecutionRequirement.
- abstract ReasoningLevel is mapped by versioned application/host policy.
- result returns typed Evidence/ExecutionEvidence.
- AI result cannot select next state or bypass deterministic admission.

### A4 — Presentation
- application content populates CNCF Presentation.
- test console projection shows current situation and next action.
- Presentation text is never control input.

### A5 — JSON boundary
- Start/Continuation/Result/Terminal fixtures round-trip with schema/version checks.
- application payload schema mismatch fails closed.
- JSON is encoding, not application state authority.

### A6 — Executable-spec closure
- GoalPhaseWorkflow executable spec passes.
- SplitPhaseWorkflow executable spec passes.
- RepositorySyncWorkflow executable spec passes.
- exact upstream ABI/runtime revisions and fixture versions are recorded.

## Post-Phase-1 plan

After Phase 1 closes:

```text
manual skill construction
  -> Codex connectivity
  -> actual development use
  -> skill refinement
  -> discover missing generic runtime features
  -> feed generic needs to Cozy/CNCF
  -> operational hardening
```

This is where production SQLite, CLI ergonomics, real skill packaging, Retry/Timeout, richer observability, and other operational facilities should be prioritized from evidence.

## Critical path

Do not expand this path:

```text
CNCF Phase 63.1 / 63.2 complete
  -> CNCF Phase 64
  -> CNCF Phase 64.2
  -> CNCF Phase 77
  -> sm-workflow Phase 1 executable specifications
```

Phase 64.1 is superseded planning history. Phase 80/85/86 and runtime-control extensions are not Phase 1 prerequisites.
