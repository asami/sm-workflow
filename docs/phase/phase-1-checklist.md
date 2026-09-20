# Phase 1 Checklist: Historical Advance-Centered Local Workflow Core Inventory

Status: superseded as Phase 1 closure authority

> **Historical inventory only.** This file is not normative, is not an active
> checklist, and must not be used to start, validate, or close Phase 1.

This is the pre-2026-09-20 operational planning inventory. It is retained for
post-Phase-1 operational hardening and is no longer the closure authority for
[Phase 1](phase-1.md). The current closure ledger is
[Phase 1 Executable-Specification Checklist](phase-1-executable-specification-checklist.md).
No item in this historical inventory is implicitly accepted, deleted, or
implemented by that scope reduction.

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
- [ ] P1-C09: Define typed planning-document snapshot, projection, three-way non-collision/composition and modification-collision classification, compare-and-set write, and validation contracts without exposing file-edit or Git command mechanics to AI Actions.
- [ ] P1-C10: Restrict semantic Work Order kinds to planning, editing/repair, review, and exception analysis; represent validation/commit as deterministic operations and user authority as a Decision.
- [ ] P1-C11: Require every semantic Action to consume a deterministic immutable input snapshot and pass a distinct deterministic result-admission state before it can affect workflow state or acceptance ledgers.
- [ ] P1-C12: Define one fully materialized `AIWorkRequest` and matching `AIWorkResult` contract with no candidate-operation list, next-state field, command request, or routing directive.
- [ ] P1-C13: Require `StartWorkflowRun` to name one exact human-selected Workflow definition; treat cross-Workflow recommendations as advisory only and prohibit automatic profile selection, chaining, or run/state migration.

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
- [ ] P1-A12: Execute admitted procedural external processes, including Git/SBT, only from Workflow-owned deterministic-operation providers, covering command resolution, bounded environment/capabilities, serialization or lifecycle control, completion, retry/failure classification, and receipt persistence; never issue them as Skill/AI Work Orders.
- [ ] P1-A13: Keep the AI tool sandbox unchanged while enforcing Workflow-managed operation allowlists, current-state/revision guards, typed argv projection, working-directory, mutation-root, network/credential, timeout, and audit boundaries without expanding authority through a Continuation or Work Order.
- [ ] P1-A14: Reject provider-registry misses, executable/free-form argv injection, generic shell, arbitrary script, and operations not admitted in the current Workflow state.
- [ ] P1-A15: Mechanically compose only non-overlapping, identical-value, or canonical-projection changes that are not modification collisions; require a semantic Work Order for every `ModificationCollision` and a human Decision for authority conflicts or multiple valid resolutions.
- [ ] P1-A16: Reject semantic results that attempt to select next state, commands, validation acceptance, ledger mutation, repair-cycle count, retry policy, or commit readiness.

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
- [ ] P1-R03: Implement `GoalPhaseWorkflow` as the first reference Workflow from the normative pure StateMachine definition derived from legacy `cncf-goal-phase`, covering Phase Entry, PLAN, Step/Slice delivery, review, bounded repair, Step commits, full Phase review, and release closure.
- [ ] P1-R04: Prove start returns the first semantic Work Order after internal automatic transitions.
- [ ] P1-R05: Prove each semantic Work Order completion returns the next semantic boundary in the same response after intervening deterministic progression.
- [ ] P1-R06: Prove explicit advance resumes the current boundary without conversation history.
- [ ] P1-R07: Prove WAIT supplies a wake condition and does not require busy polling.
- [ ] P1-R08: Implement `BuildProject`, `RunTests`, `RunExecutableSpecification`, `InspectChanges`, `StageChanges`, and `CommitChanges` as typed operations/providers for the reference workflow.
- [ ] P1-R09: Prove REVIEW ACCEPT enters Closing and reaches Completed without a COMMIT Work Order when the normal deterministic path succeeds.
- [ ] P1-R10: Prove successful Closing records commit SHA and operation receipts.
- [ ] P1-R11: Prove push, PR creation, merge, and deployment are not executed by Phase 1 Closing.
- [ ] P1-R12: Prove the `GoalPhaseWorkflow` CML/generated ABI contains no skill name/path, model, reasoning effort, agent role, prompt, raw command, or turn-scheduling contract.
- [ ] P1-R13: Bind only semantic AI Required Operations through `Suspended(Continuation)` and typed resume Result/Evidence; keep deterministic build/test/git and workflow-control operations on internal providers.
- [ ] P1-R14: Prove deterministic-test and skill-backed providers produce equivalent state/history/terminal outcomes for the same typed Action results.
- [ ] P1-R15: Prove build/test, generation/inspection, and local Git inspect/stage/commit complete through Workflow-owned providers while the Skill receives no external command or raw command sequence.
- [ ] P1-R16: Implement `SplitPhaseWorkflow` from the normative pure StateMachine definition derived from legacy `cncf-split-phase`, covering evidence collection, deterministic estimation/optimization, bounded semantic enrichment, preview/apply, numbering, planning-document projection, conflict resolution, idempotency, and static validation.
- [ ] P1-R17: Prove a current complete `SPLIT_REQUIRED` proposal applies with zero semantic AI Work Orders; a source containing only known work and explicit boundaries also builds and applies its proposal with zero semantic AI Work Orders.
- [ ] P1-R18: Prove non-overlapping and generated-projection changes are composed as non-collisions on Workflow providers, while every `ModificationCollision` produces `ResolveSplitMergeConflicts` and identity/authority or multiple-valid conflicts produce a human Decision.
- [ ] P1-R19: Prove split AI results never directly edit planning files or run Git; Workflow validation and compare-and-set write own every applied mutation.
- [ ] P1-R20: Prove split preview mutates no planning file, idempotent apply creates no duplicate child/note, and no split run starts a child goal, runs SBT/runtime validation, commits, or pushes.
- [ ] P1-R21: Freeze a versioned duration-estimation policy covering comparable evidence selection, sample threshold, median calculation, normalization, rounding, overhead, and outlier admission, and prove the same evidence snapshot/policy produces identical estimates.
- [ ] P1-R22: Prove only `UNRESOLVED_NOVEL` work and unresolved semantic edges enter `AssessNovelSplitWork`; known-work estimates cannot be overwritten by AI.
- [ ] P1-R23: Enumerate contiguous partition candidates and select the unique result with the versioned admissibility filter, lexicographic objective, and stable tie-break without AI participation.
- [ ] P1-R24: Split goal-phase Phase Entry, provisional adoption, planning, implementation, step/phase review, repair, and re-review into deterministic prepare, semantic Action, and deterministic admission/validation states.
- [ ] P1-R25: Prove split-phase novel-work/boundary enrichment and semantic conflict resolution each pass distinct deterministic admission/validation states before proposal or write-set mutation.
- [ ] P1-R26: Implement `RepositorySyncWorkflow` from the normative pure StateMachine definition derived from legacy `cncf-repository-sync`, covering full-state checkpoint, fetch, relation classification, fast-forward, non-rewriting merge, proportional validation, merge review, non-force push, and convergence verification.
- [ ] P1-R27: Prove clean equal, local-ahead, remote-ahead fast-forward, dirty checkpoint plus push, and conflict-free diverged merge reach their terminal outcomes with zero semantic AI Work Orders.
- [ ] P1-R28: Prove every repository `ModificationCollision` issues `ResolveRepositoryMergeConflicts` or, for authority/multiple-valid outcomes, a human Decision; pending merge review and policy-bounded merge repair are the only other semantic Actions.
- [ ] P1-R29: Prove the Git provider accepts only run-bound typed operations and rejects AI/skill supplied executable, argv, shell fragment, remote, ref, branch mapping, force mode, history rewrite, tag, remote configuration, credential, release, publication, deployment, PR, and arbitrary cleanup requests.
- [ ] P1-R30: Prove full-state checkpoint admits every dirty path or rejects the boundary; it never selects a subset, stash/clean/reset/restores another path, or proceeds from a partial/mixed index.
- [ ] P1-R31: Prove pending merge uses frozen local/remote parents, remains uncommitted until validation/review complete, preserves both parents in the exact merge commit, and aborts safely to the post-checkpoint tip on failure.
- [ ] P1-R32: Prove a non-fast-forward push due to one newly advanced remote fetches/reclassifies once, while a second advance returns `WAITING_FOR_REMOTE_STABILITY` without an AI call or unbounded retry.
- [ ] P1-R33: Prove a split-required `GoalPhaseWorkflow` terminates as `SPLIT_REQUIRED` with an advisory `SplitPhaseWorkflow` recommendation but does not start `sm-split-phase`, create a split run, or migrate current run state.
- [ ] P1-R34: Prove `SplitPhaseWorkflow` starts only after explicit human selection, consumes prior source/evidence/proposal references as typed input rather than migrated state, and never starts a child `GoalPhaseWorkflow`; each selected child starts as an independent run.

## Public Skill and Bundle

- [ ] P1-K01: Author the thin `sm-goal-phase`, `sm-split-phase`, and `sm-repository-sync` skills.
- [ ] P1-K02: Keep state progression, deterministic build/test/git command sequences, retry policy, SQLite details, and CNCF-specific routing out of the skill.
- [ ] P1-K03: Define a framework-neutral `SkillBundleManifest` with file digests and protocol compatibility.
- [ ] P1-K04: Produce a standalone bundle that can be validated without CNCF or CAR resolution.
- [ ] P1-K05: Include the same bundle bytes in the CAR and verify digest equivalence.
- [ ] P1-K06: Document install prerequisites without implementing installer adapters in Phase 1.
- [ ] P1-K07: Prove legacy `cncf-goal-phase`, `cncf-split-phase`, and `cncf-repository-sync` remain installed and unchanged, with no rename, overwrite, forwarding, shared state, or implicit migration from any `sm-*` skill.
- [ ] P1-K08: Prove `sm-goal-phase`, `sm-split-phase`, and `sm-repository-sync` call only the versioned `sm-workflow` protocol and contain no runtime dependency on a `cncf-*` skill.
- [ ] P1-K09: Prove each skill executes exactly the leased AI request and returns its typed result without choosing another skill, agent, operation, command, Decision, or next state.
- [ ] P1-K10: Keep Decision presentation, WAIT registration, terminal display, and next-Continuation delivery in the host/client adapter rather than semantic skill logic.
- [ ] P1-K11: Bind `sm-goal-phase -> GoalPhaseWorkflow`, `sm-split-phase -> SplitPhaseWorkflow`, and `sm-repository-sync -> RepositorySyncWorkflow` explicitly in `SkillBundleManifest`, including compatible definition versions, without name-derived lookup.
- [ ] P1-K12: Keep Workflow recommendation display and explicit profile/child selection in the host/client adapter; prohibit every `sm-*` skill from invoking another profile skill or treating a recommendation as start authority.

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
- [ ] P1-Q11: Measure AI tool-command calls avoided by Workflow-owned external-command providers without relaxing the AI sandbox.
- [ ] P1-Q12: Prove Workflow-managed command execution does not widen executable, filesystem, network, credential, or process authority and remains bound to current state/revision/guard.
- [ ] P1-Q13: Measure split proposal reuse, deterministic/novel work estimates, enumerated candidates, non-colliding compositions, `ModificationCollision` Work Orders, and human Decisions separately.
- [ ] P1-Q14: Prove deterministic inventory, numbering, rendering, merge, and static validation do not increase semantic Work Order count.
- [ ] P1-Q15: Prove history collection, known-work calibration, contiguous candidate enumeration, objective evaluation, and tie-break do not increase semantic Work Order count.
- [ ] P1-Q16: Prove deterministic prepare/admission states execute without model invocation and that one semantic result cannot trigger an unvalidated direct commit or terminal transition.
- [ ] P1-Q17: Measure repository-sync checkpoints, fetches, relation classifications, non-colliding merges, `ModificationCollision` Work Orders, human Decisions, validations, commits, pushes, review/repair Work Orders, and remote-stability WAITs separately; prove ordinary sync operations add no semantic invocation.

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
- [ ] P1-V11: Pass end-to-end `SplitPhaseWorkflow` through the `sm-split-phase` skill for preview, apply, restart, idempotency, deterministic-estimation, concurrent-edit merge, semantic-conflict, and authority-conflict scenarios.
- [ ] P1-V12: Pass side-by-side installation and separate-state acceptance for `sm-goal-phase`, `sm-split-phase`, `sm-repository-sync`, `cncf-goal-phase`, `cncf-split-phase`, and `cncf-repository-sync` without skill-name collision or implicit state transfer.
- [ ] P1-V13: Pass schema-negative tests for semantic results containing forbidden command, transition, validation, ledger, cycle, retry, or commit-readiness fields.
- [ ] P1-V14: Pass a dispatcher-negative test proving different AI result contents cannot cause the skill to select or invoke different follow-up processing; only Workflow admission and `advance` choose the next continuation.
- [ ] P1-V15: Pass end-to-end `RepositorySyncWorkflow` restart/idempotency/concurrency acceptance for clean equal, local-ahead, remote-ahead, dirty full-state checkpoint, conflict-free merge, semantic conflict, merge abort, one remote-advance retry, and final equal-tip verification.
