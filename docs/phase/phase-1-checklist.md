# Phase 1 Checklist: Advance-Centered Local Workflow Core

Status: planned

This checklist is the closure authority for [Phase 1](phase-1.md). Deterministic operation / closing requirements are specified by [Phase 1 deterministic closing addendum](phase-1-deterministic-closing-addendum.md).

## Entry Gates

- [ ] P1-E01: Record the completed Cozy Phase 62 release, exact commit, real CML fixture, and admitted `cozy.cml.workflow.*` ABI version.
- [ ] P1-E02: Verify the Cozy Phase 62 ABI preserves reused StateMachine / Composite StateMachine semantics, typed Operation references, and declared automatic versus typed semantic-boundary metadata.
- [ ] P1-E03: Record the completed CNCF Phase 77 release, exact commit, ComponentFactory admission evidence, and compatible ABI-version policy.
- [ ] P1-E04: Verify the Phase 77 evaluator consumes declared progression without CML parsing or semantic-boundary crossing and provides the typed Operation binding required by deterministic operations.
- [ ] P1-E05: Verify the Phase 77 WorkflowInstance SPI separates process persistence from entity StateMachine persistence and leaves datastore, retention, lease, public protocol, and skill behavior to the consuming component.
- [ ] P1-E06: Verify the Textus component-local datastore binding is available without adding CNCF-specific fields to the public `sm-*` skill or public protocol.
- [ ] P1-E07: Record unresolved upstream gaps and stop before local DSL, generated-ABI copy, or CNCF-emulator implementation.

## Identity and Project Baseline

- [ ] P1-B01: Freeze component identity, package namespace, CAR coordinate, and public protocol namespace.
- [ ] P1-B02: Scaffold the Textus CAR without adding public CNCF-specific dependencies.
- [ ] P1-B03: Separate CML, generated ABI, domain/application logic, storage port, deterministic-operation providers, CLI adapter, and skill bundle paths.
- [ ] P1-B04: Define local, test, and packaged runtime profiles.

## CML and Public Contract

- [ ] P1-C01: Define WorkflowRun, WorkOrder, Decision, and Evidence lifecycle state machines.
- [ ] P1-C02: Define the generic WorkflowDefinition, PlanRevision, Stage, and WorkItem model.
- [ ] P1-C03: Bind the Cozy Phase 62 / CNCF Phase 77 declared progression metadata to WorkflowRun lifecycle and Continuation outcomes without reclassification or a parallel local DSL.
- [ ] P1-C04: Define `AdvanceWorkflowRun` and the closed `Continuation` outcome schema.
- [ ] P1-C05: Define Start, Work Result, Review Result, Decision, Status, History, and Cancel operations.
- [ ] P1-C06: Version the public CLI/JSON schema and document compatibility behavior.
- [ ] P1-C07: Define typed deterministic-operation contracts for build, test, executable specification, git inspection/staging/commit, including input/output, failure semantics, authorization, idempotency, and receipt.
- [ ] P1-C08: Verify no raw shell or arbitrary script is exposed as a Workflow Action contract.

## Advance Evaluator

- [ ] P1-A01: Implement deterministic automatic-transition selection.
- [ ] P1-A02: Implement bounded progression across automatic transitions and permitted deterministic operations.
- [ ] P1-A03: Reject ambiguous automatic transitions with a typed invariant failure.
- [ ] P1-A04: Detect cycles/limit overflow without creating a Codex Work Order.
- [ ] P1-A05: Stop at Work Order, Decision, Wait, and Terminal boundaries.
- [ ] P1-A06: Issue and lease one Work Order atomically when executor identity is present.
- [ ] P1-A07: Persist idempotency result and return the same Continuation on replay.
- [ ] P1-A08: Compose server-side advance into start, work-result, review-result, and decision-result mutations.
- [ ] P1-A09: Keep status/history read-only and side-effect free.
- [ ] P1-A10: Execute deterministic operations without Codex/model invocation and persist their typed receipts/evidence.
- [ ] P1-A11: Classify deterministic-operation failures through declared retry / WAIT / WORK_ORDER / DECISION / failure rules rather than automatically delegating them to Codex.

## SQLite Persistence

- [ ] P1-S01: Define provider-neutral workflow, transition, lease, idempotency, and operation-receipt storage ports.
- [ ] P1-S02: Freeze the Textus-owned platform-aware local-data root and database filename policy.
- [ ] P1-S03: Bind the local profile to SQLite without exposing JDBC/SQL/path details to domain code.
- [ ] P1-S04: Enable and verify WAL, foreign keys, bounded busy timeout, and immediate write transactions.
- [ ] P1-S05: Atomically persist current state, history, Work Order/lease, and Continuation result where applicable.
- [ ] P1-S06: Define schema versioning, forward migration, integrity check, and SQLite-safe backup policy.
- [ ] P1-S07: Store large artifacts outside SQLite and retain typed digest/reference receipts.
- [ ] P1-S08: Prove restart recovery and concurrent ownership exclusion.
- [ ] P1-S09: Persist closing evidence and successful local commit SHA against the WorkflowRun.

## CLI and Reference Workflow

- [ ] P1-R01: Implement the Phase 1 CLI command surface.
- [ ] P1-R02: Emit versioned machine-readable JSON and a separate human projection.
- [ ] P1-R03: Implement the reference PLAN -> CHANGE -> deterministic validation -> REVIEW -> deterministic closing workflow profile.
- [ ] P1-R04: Prove start returns the first semantic Work Order after internal automatic transitions.
- [ ] P1-R05: Prove each semantic Work Order completion returns the next semantic boundary in the same response after intervening deterministic progression.
- [ ] P1-R06: Prove explicit advance resumes the current boundary without conversation history.
- [ ] P1-R07: Prove WAIT supplies a wake condition and does not require busy polling.
- [ ] P1-R08: Implement `BuildProject`, `RunTests`, `RunExecutableSpecification`, `InspectChanges`, `StageChanges`, and `CommitChanges` as typed operations/providers for the reference workflow.
- [ ] P1-R09: Prove REVIEW ACCEPT enters Closing and reaches Completed without a COMMIT Work Order when the normal deterministic path succeeds.
- [ ] P1-R10: Prove successful Closing records commit SHA and operation receipts.
- [ ] P1-R11: Prove push, PR creation, merge, and deployment are not executed by Phase 1 Closing.

## Public Skill and Bundle

- [ ] P1-K01: Author the thin `sm-workflow-run` skill.
- [ ] P1-K02: Keep state progression, deterministic build/test/git command sequences, retry policy, SQLite details, and CNCF-specific routing out of the skill.
- [ ] P1-K03: Define a framework-neutral `SkillBundleManifest` with file digests and protocol compatibility.
- [ ] P1-K04: Produce a standalone bundle that can be validated without CNCF or CAR resolution.
- [ ] P1-K05: Include the same bundle bytes in the CAR and verify digest equivalence.
- [ ] P1-K06: Document install prerequisites without implementing installer adapters in Phase 1.

## Cost and Safety Acceptance

- [ ] P1-Q01: Record automatic transition, deterministic operation, semantic Work Order, and client round-trip counts.
- [ ] P1-Q02: Measure continuation and resume-context payload sizes.
- [ ] P1-Q03: Prove automatic-transition and deterministic-operation growth does not increase Codex/model invocations when no new semantic boundary is introduced.
- [ ] P1-Q04: Prove no extra Codex turn is needed solely to discover the next semantic state or execute build/test/git closing.
- [ ] P1-Q05: Prove validation, review, permission, and receipt boundaries remain enforced.
- [ ] P1-Q06: Prove a Work Order does not expand host tool authority.
- [ ] P1-Q07: Prove public skills/schemas contain no required CNCF command, skill, agent, receipt, or local path.
- [ ] P1-Q08: Prove REVIEW ACCEPT to Completed requires zero additional Codex/model turns on the normal deterministic closing path.
- [ ] P1-Q09: Prove `CommitChanges` refuses stale revision or missing required review/validation evidence.
- [ ] P1-Q10: Prove failed commit never produces Completed and preserves diagnostic/evidence needed for recovery.

## Validation and Closure

- [ ] P1-V01: Pass CML generation and generated ABI compatibility validation.
- [ ] P1-V02: Pass unit tests for advance, continuation, idempotency, ambiguity, cycle handling, and deterministic-operation progression.
- [ ] P1-V03: Pass SQLite persistence, rollback, restart, migration, and concurrency tests.
- [ ] P1-V04: Pass CLI contract tests for all outcomes and conflicts.
- [ ] P1-V05: Pass the end-to-end reference workflow restart scenario including REVIEW ACCEPT -> deterministic Closing -> local commit -> Completed.
- [ ] P1-V06: Pass standalone/CAR skill bundle equivalence validation.
- [ ] P1-V07: Pass public dependency-boundary static checks.
- [ ] P1-V08: Pass CAR structure/lint and documentation checks.
- [ ] P1-V09: Record all validation receipts against the final intended tree.
- [ ] P1-V10: Update Phase 1 status and evidence only after every required item is complete.
