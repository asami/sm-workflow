# sm-workflow Skill-side Planning / Execution Boundary

- Date: 2026-09-21
- Status: Design decision / history
- Main design: [sm-workflow Skill Adapter Design](../../notes/sm-workflow-skill-adapter-design.md)
- Runtime design: [sm-workflow Design](../../notes/sm-workflow-design.md)

## Background

The initial sm-workflow design correctly moved durable Workflow progression out of Codex Skill instructions and into a typed Workflow runtime. Phase 77 on CNCF then established the generic Start / Handle / Continuation / WorkOrder / WorkResult / Evidence protocol and durable StateMachine/Workflow execution boundary.

During review of Phase/Checklist operation, a second boundary became important: Phase and Checklist are not merely another representation of Workflow state.

The Skill interprets and updates Phase/Checklist as a planning model. sm-workflow executes a software-development Workflow. CNCF provides generic execution semantics. The Skill also possesses project context that is useful for planning but harmful if copied into the generic Workflow model.

## Evolution of the decision

The first idea was to exchange broad Goal/WorkOrder and WorkResult structures between Skill and sm-workflow. This was refined because it risked making sm-workflow understand Phase/Checklist semantics.

The next step separated:

- planning semantics and context: Skill
- software-development execution semantics: sm-workflow
- generic execution protocol/runtime: CNCF Workflow

This introduced the Skill-owned WorkflowMapping: a projection between planning references and Workflow/Action/Result/Evidence references. Mapping is not assumed to be 1:1.

The design then aligned sm-workflow DTOs with CNCF Phase 77. CNCF owns the generic envelope and runtime control information; sm-workflow supplies application-owned typed payloads. Phase/Checklist remain outside both DTO domains.

## Why a separate Skill design document is needed

The runtime-oriented sm-workflow design is insufficient as the main input to Skill generation. A Skill generator needs the opposite viewpoint:

1. what planning material is authoritative;
2. what the Skill must interpret;
3. how WorkflowMapping is built and revised;
4. how Skill-only context is projected into bounded SmExecutionContext;
5. how CNCF Workflow DTOs are invoked without duplicating runtime mechanisms;
6. how Result/Evidence is reconciled back to Phase/Checklist;
7. how planning revision drift and recovery are handled;
8. which responsibilities are explicitly prohibited.

For this reason, docs/notes/sm-workflow-skill-adapter-design.md is created as the primary Skill-side design input.

## Key decision

> Skill owns planning semantics and context. sm-workflow owns software-development execution semantics. CNCF Workflow owns generic execution protocol/runtime.

The two authoritative states are planning state (Phase/Checklist) and execution state (CNCF/sm-workflow). WorkflowMapping connects them but does not become a third Workflow state machine.

Synchronization is semantic reconciliation, not dual-write or field synchronization.

## Consequences

Future sm-* Skill generation should read the Skill Adapter Design first, then consult sm-workflow runtime/profile documents for the concrete Workflow it invokes.

Skill acceptance should verify recovery without conversation history, bounded context projection, source-revision reconciliation, use of CNCF generic DTOs, and explicit planning reconciliation.

Future changes to sm-workflow should avoid solving Skill planning problems inside the runtime. If a proposed runtime field exists only because a Phase/Checklist-oriented Skill wants it, first consider keeping it in WorkflowMapping or Skill context.

The Skill design can evolve independently of the generic Workflow runtime as long as the CNCF typed protocol and sm-workflow application payload contract remain stable.
