# Phase 5: Semantic Reasoning Classes and Runtime Mapping

Status: planned
Planned: 2026-09-30
Depends on: Phase 1
Planned after: Phase 2 for the current practical rollout

## Dependency boundary

Phase 5 is functionally dependent only on Phase 1. Phase 2 (server/MCP), Phase 3 (Resolved Failure Model), and Phase 4 (Service Bus events) are orthogonal extensions and are not prerequisites for semantic reasoning resolution. Phase 5 is scheduled after Phase 4 only to preserve the current implementation sequence.

A minimal Phase 1 + Phase 5 configuration MUST support one-shot/CLI execution through semantic WorkClassification / ReasoningClass -> execution disposition -> CNCF standard Component configuration mapping -> concrete provider profile. The current practical rollout follows Phase 2 so the same policy is also available through the server/MCP path.

## Goal

Make sm-workflow practically usable for daily development while decoupling semantic work description from provider/model-specific controls. Workflow/Skill planning classifies the work; sm-workflow resolves the logical execution requirement; the execution harness resolves the concrete provider/model/effort at invocation time.

Phase 5 is the minimum practical vertical slice. Generic Skill/Workflow execution-protocol generalization beyond this proven slice is deferred to Phase 6.

## Semantic work kinds

Initial vocabulary:

- PLANNING — decomposition, sequencing, and change-scope decisions.
- ANALYSIS — understand state, causes, dependencies, and evidence.
- DESIGN — determine solution structure, contracts, models, APIs, or state machines.
- CODING — create or modify implementation artifacts and tests.
- REVIEW — evaluate artifacts/changes against requirements, design, quality, and evidence.
- JUDGMENT — bounded admission, selection, approval, or candidate/evidence decision.

Testing is not initially separate: test creation is normally CODING, diagnostic interpretation ANALYSIS, and acceptance evaluation REVIEW.

## Semantic intensity

Each work kind is subdivided only as far as sm-workflow can meaningfully select the class from workflow context. Initial vocabulary may include ROUTINE, STANDARD, DEEP, CRITICAL, and EXHAUSTIVE, but supported intensities MAY differ by work kind.

A reasoning class is the semantic combination (for example CodingDeep, ReviewCritical, JudgmentStandard). It is one workflow-level semantic requirement, not two provider knobs.

Multiple reasoning classes MAY map to the same concrete execution profile.

## Runtime mapping

    Workflow Action
        |
        | ReasoningClass = ReviewDeep
        v
    Reasoning Profile Resolver
        |
        | CNCF Component configuration
        v
    Concrete Execution Profile
        +-- provider
        +-- model
        +-- provider-specific reasoning mode/effort
        +-- invocation controls
        |
        v
    Codex / other provider

The resolver belongs to the CNCF execution/runtime boundary. sm-workflow selects semantic intent; runtime configuration determines how the current environment realizes it. The canonical split-file location is `~/.textus/components/sm-workflow/config.yaml` when the Component ID is `sm-workflow`; this location is selected by CNCF's standard Component configuration binding. sm-workflow consumes the resolved typed configuration and does not directly discover or open that path.

## Initial Codex mapping target

Current operating targets intentionally collapse the richer semantic vocabulary:

- Coding PROGRAMMING: GPT-6.1 Luna / high as the primary delegated candidate.
- Coding ENGINEERING: GPT-6.1 Sol / high as the primary candidate; a matching bounded parent may execute inline.
- Review Standard: GPT-6.1 Sol / high in an independent execution context.
- Review Critical/broad: GPT-6.1 Sol / xhigh in an independent execution context.
- TRIVIAL implementation: INLINE fast path regardless of the normal concrete profile mapping, provided Workflow acceptance requirements remain unchanged.

These concrete bands MUST NOT determine the number of semantic classes. Planning, Analysis, Design, and Judgment mappings are operational configuration, not Workflow semantics.

## Scope

1. Define ReasoningClass and the six initial work kinds.
2. Define meaningful semantic intensities per work kind.
3. Define how Actions request a ReasoningClass.
4. Define the CNCF-facing resolver contract and concrete execution-profile representation.
5. Define and implement user-level mapping through CNCF standard Component configuration binding with deterministic lookup.
6. Remove provider-specific reasoning levels from sm-workflow semantics where superseded.
7. Provide default mappings for the supported Codex environment.
8. Record both requested semantic class and resolved concrete profile in diagnostics/evidence.
9. Add WorkClassification input from sm-* planning, initially covering TRIVIAL/SIMPLE/STANDARD/COMPLEX, PROGRAMMING/ENGINEERING, and bounded context-footprint hints without making physical line count normative.
10. Resolve the minimum practical execution disposition: INLINE or DELEGATED, independence OPTIONAL or REQUIRED, plus semantic ReasoningClass/level.
11. Implement TRIVIAL implementation as an INLINE fast path; it MUST NOT bypass validation, review, Admission, evidence, or authorization.
12. Allow bounded implementation to execute INLINE when the current executor satisfies the resolved profile; delegate when a different profile is required or context footprint should be isolated.
13. Require review to use an independent execution context even when its concrete model/effort equals the implementation parent.
14. Consume the minimum CNCF generic ExecutionRequirement/ExecutionEvidence extension supplied by CNCF Phase 80 for placement, independence, and execution identity/evidence; do not define a parallel sm-workflow protocol.
15. Add Executable Specifications for mapping, many-to-one collapse, missing configuration, mapping changes, trivial inline execution, matching-profile inline implementation, Luna delegation, and independent review.
16. Treat independent sbt/build/test operations as parallelizable by default. sm-workflow MUST NOT introduce a process-wide or sbt-wide mutex merely because an operation invokes sbt.
17. Delegate Ivy/Coursier/sbt shared-cache coordination and locking to sbt and its underlying tooling. sm-workflow MUST NOT duplicate those implementation-level locks.
18. Keep harness-specific execution restrictions outside Workflow semantics. In particular, a Skill adapter MAY serialize sbt invocations when the Skill execution environment itself fails on concurrent sbt execution; that serialization is an adapter workaround and MUST NOT become an ExecutionRequirement or a general sm-workflow policy.
19. Treat genuine semantic conflicts separately from tool identity: operations that write the same protected work area or otherwise have an explicit application/workflow conflict MAY require coordination, but "uses sbt" alone is not such a conflict.

## Executable Specification requirements

Demonstrate that a Workflow requests semantic reasoning without provider details; distinct semantic classes may resolve to the same profile; mapping changes affect subsequent invocation without Workflow changes; coding resolves across current medium/high/xhigh bands; review resolves across high/xhigh bands; missing mappings fail explicitly; and provider-specific values do not leak back into persisted Workflow semantics.

Also demonstrate two independent sbt operations can be admitted for concurrent execution and are not serialized solely because both invoke sbt. A harness/Skill-specific serialization constraint, when present, remains local to that adapter and does not alter the persisted Workflow or resolved generic ExecutionRequirement. The specification MUST NOT add Ivy/Coursier cache locking to sm-workflow; those locks remain the responsibility of sbt/tooling.

## Practical completion condition

Phase 5 MUST NOT close until the CNCF Phase 101 minimum Component resource slice required by sm-workflow has been implemented and verified through the CNCF project, and sm-workflow has consumed that upstream API without hard-coded project resource paths.

Phase 5 is practically complete when a Sol/high parent can directly call sm-workflow control operations and the same workflow can demonstrate: TRIVIAL self/inline implementation; Luna/high delegated PROGRAMMING; bounded matching-profile Sol/high inline implementation; and separate Sol/high or xhigh review with required independence, with requested requirements and actual execution evidence preserved.

## GoalPhase repository-local closing boundary

Phase 5 preserves the existing `sm-goal-phase` / `GoalPhaseWorkflow` closing responsibility: implementation, review/admission, deterministic validation, staging/CommitChanges, commit evidence, and Step/Phase close occur against the currently admitted repository/worktree. GoalPhase closing does not attempt to synchronize or converge the complete multi-repository Project Workspace with GitHub.

A dedicated CNCF/Cozy worktree is therefore a valid GoalPhase working repository: GoalPhase can change, validate, review, and locally commit that worktree exactly as it can the root repository. Cross-repository fetch/merge/push/convergence remains the responsibility of `sm-repository-sync` / `RepositorySyncWorkflow` in Phase 7.

## Project resource integration

Phase 5 establishes the consumer-side rule that sm-workflow MUST use CNCF logical Component resource APIs rather than construct project-local physical paths. Existing CNCF configuration resolution remains authoritative for configuration; sm-workflow consumes the merged Component configuration and does not reproduce current-directory/project/home/system lookup.

CNCF Phase 101 is the upstream implementation phase for non-configuration resources and is explicitly driven from sm-workflow Phase 5. During Phase 5 execution, the workflow MUST start/advance CNCF Phase 101 far enough to deliver the minimum logical Component resource API required by sm-workflow, then consume and verify that API before Phase 5 closes. Phase 5 MUST NOT substitute a private sm-workflow filesystem abstraction when the upstream slice is missing.

This is a cross-project driven-development dependency rather than a requirement that all of CNCF Phase 101 be completed before Phase 5 starts. Phase 5 may begin first, discover/materialize the required CNCF slice, drive Phase 101, resume sm-workflow integration, and close only after the required upstream acceptance evidence is available. Phase 7 later drives the workspace/worktree scenario to broaden and harden the same CNCF API.

## Non-goals

- Encoding Codex effort names as abstract Workflow levels.
- Requiring identical intensity sets for all work kinds.
- Automatic cross-provider cost optimization.
- Full generic provider capability negotiation, fallback/escalation, context-budget accounting, or Human/remote-worker generalization; these belong to Phase 6 / CNCF Phase 80 follow-up work.
- Changing closure criteria of preceding Phases.
- Providing global sbt, Ivy, or Coursier locking as a safety mechanism.
- Promoting a Skill-runtime concurrency workaround into Workflow semantics or generic runtime policy.
