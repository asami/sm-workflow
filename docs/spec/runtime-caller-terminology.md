# Runtime, caller and network host terminology

Status: connected implementation, isolated verification and normal persisted-format/runtime cutover complete, 2026-10-23; remaining purpose-specific prose is tracked separately.

## Meaning and scope

`host` denotes only the network destination host: a hostname/address identifying
the machine or service to connect to. A full endpoint additionally has its own
scheme/port/path. Do not use `host` for a caller, runtime, local directory,
configuration bundle, executor, evidence issuer or their ownership.

This applies to sm-workflow type/package/member names, CML/generated DTOs,
wire fields/schema labels, configuration keys, CLI arguments, help, errors,
skill instructions and current specifications. Historical records and external
APIs retain their original spelling. A tool-owned `hostId` is not renamed by
sm-workflow; translate its meaning at the integration boundary if necessary.

| Concept | Term |
| --- | --- |
| Existing Codex/client invoking sm-workflow | Caller / CallerIntegration |
| Executing CNCF/sm-workflow machinery | Runtime |
| Directory selecting `.textus` configuration and runtime working directory | RuntimeRoot / runtimeRoot / `--runtime-root` |
| Directory of the repository being developed | RepositoryRoot; distinct from RuntimeRoot even if equal |
| Configuration and service/port assembly | RuntimeComposition / RuntimeParameters |
| One issued execution's selection/attempt state | ExecutionSession; not the Workflow Session or Codex conversation |
| Human/review/effect observations admitted from an independent issuer | ExternalEvidence; issuer/participant and purpose remain explicit |
| WorkOrder review context / Decision context | ReviewContext / HumanDecisionContext |

These terms name existing responsibilities. They introduce no new runtime,
ownership registry, evidence store, security gate or execution engine.

## Concrete rename contract

The old column identifies current code, not aliases to support after cutover.
Apply the new names with their actual existing responsibility. Do not globally
replace every `Host` by `Caller` or `Runtime`.

| Current identifier | Target identifier / action |
| --- | --- |
| CLI `--host <directory>` | `--runtime-root <directory>`; same selected directory |
| caller options/result field `host` meaning that directory; `canonicalHostRoot` | `runtimeRoot`; no directory move |
| `ThreeWorkflowLauncherAssembly.Host` / `HostDecoder` | `RuntimeComposition` / `RuntimeCompositionDecoder` |
| `ThreeWorkflowHostParameters` | `ThreeWorkflowRuntimeParameters` |
| `sm-workflow.host.definition` | `sm-workflow.runtime.definition` |
| `sm-workflow.three-workflow-host.v1` | `sm-workflow.three-workflow-runtime.v1` |
| `sm-workflow.{goal-phase,repository-sync,split-phase}.host-records.*` | Same family with `input-records.*`; retain collection destinations |
| `NativeHostEvidence` | `NativeExternalEvidence`; not a replacement for Common Evidence |
| `sm-workflow.host.evidence.*` | `sm-workflow.external-evidence.*`; same store, collection and issuer |
| `sm-workflow.native-host-evidence.v7` | `sm-workflow.native-external-evidence.v7`; same business payload semantics |
| `nativeHostEvidencePayload` | `nativeExternalEvidencePayload` |
| `ManagedHostRecordRead` | `ManagedInputRecordRead` |
| `GoalPhaseHostRecordReadPolicy` | `GoalPhaseInputRecordReadPolicy` |
| `RepositorySyncHostSelectionReadPolicy` / `SplitPhaseHostSelectionReadPolicy` | `RepositorySyncInputSelectionReadPolicy` / `SplitPhaseInputSelectionReadPolicy` |
| `ThreeWorkflowHostEvidencePorts` | `ThreeWorkflowExternalEvidencePorts` |
| `NativeWorkflowHostExecution` | `NativeWorkflowCallerExecution` (bridge for issued caller work) |
| `WorkflowHostExecutionPrepareInput` / `WorkflowHostExecutionRecordInput` / `WorkflowHostExecutionState` | `WorkflowCallerExecutionPrepareInput` / `WorkflowCallerExecutionRecordInput` / `WorkflowCallerExecutionState` |
| `NativeHostPreparationService` | `NativeWorkflowPreparationService`; its existing passive preparation Operations remain passive |
| `NativeHostExecutionInput` | `NativeCallerExecutionInput` |
| `WorkflowHostReviewContext` / `WorkflowHostReviewCompletion` | `WorkflowReviewContext` / `WorkflowReviewCompletion` |
| `WorkflowHostHumanContext` | `WorkflowHumanDecisionContext` |
| `WorkflowHostAuthorityContext` | `WorkflowAuthorityContext` |
| review/Human DTO field `hostContext` | `reviewContext` / `humanDecisionContext`, respectively |
| `NativeHostReviewContext` / `NativeHostHumanContext` / `NativeHostAuthorityContext` | `NativeReviewContext` / `NativeHumanDecisionContext` / `NativeAuthorityContext` |
| `NativeHost*Preparation`, `NativeHost*Scope`, remaining input helpers | Remove the role-free `Host` segment, retain the concrete purpose; use Caller only for actual caller integration |
| `smworkflow.host` package | Split by existing purpose into `smworkflow.caller` (caller execution bridge) and `smworkflow.preparation` (passive preparations); do not add abstractions merely to fill packages |
| `HostSourceRead`, `host_source_read`, terminal-provider configuration/DTOs | Withdraw under the caller-return plan; do not rename into a new read DSL |

An identifier collision is resolved by reusing the existing type if its meaning
is identical, or by a purpose-specific name if not. Do not create `V2`, `Legacy`,
`Compat`, type aliases or forwarding wrappers just to preserve old names.
The narrower mapping must be recorded before editing the affected declaration.

## No compatibility layer; preserve live work

New production interfaces accept only the target names. Old CLI flags, keys,
DTO aliases, dual schema readers and fallback paths are not supported. Change
CML sources and regenerate affected APIs using the normal generator; compile
against updated callers so stale source references fail visibly. Remove obsolete
selected configuration at its source instead of letting ignored unknown keys
conceal a missed update. Do not add blanket unknown-field validation elsewhere.

Preservation is not permanent compatibility. Keep actual Goal/Workflow Session,
run/continuation/Step IDs, accepted commits, repair counts, decisions, execution
outcomes and evidence references. No new Start, successful-work replay, invented
consent, cleared ledger or reset is allowed for renaming.

For serialized active inputs affected by field/schema spelling, inventory the
actually selected records. If conversion is necessary, use one explicit bounded
adoption conversion outside ordinary runtime readers. Preserve an original copy
and a record of the mapping in the existing evidence location, retain IDs and
business values, and switch the selected configuration/runtime together. Unchanged
records need no conversion. A required format change must not reinterpret a
signature/hash or mutate a historical receipt; define the relevant boundary
before conversion, without adding new integrity certificates.

Historical receipts, archived logs, journal quotations and prior success/failure
results remain original evidence. Do not rewrite them to appear produced by the
new interface. Pending/active calls finish under their original binding before
the affected boundary is switched; no forced termination or mid-call settings
change. The runtime root and storage paths stay in place.

## Related contracts

- [Adoption plan](../notes/runtime-caller-terminology-adoption-plan.md)
- [Caller information return](host-terminal-source.md)
- [Caller execution bridge](native-host-execution.md)
- [Runtime composition](normal-three-workflow-host.md)
- [Developer handoff](../journal/2026/10/2026-10-23-network-host-terminology-handoff.md)

The linked existing filenames and old-identifier descriptions are traceability
locators during implementation, not supported aliases. Move active documentation
and update its links together at cutover; do not break historical evidence links.
