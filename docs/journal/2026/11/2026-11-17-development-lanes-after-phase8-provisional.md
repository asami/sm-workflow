# Development lanes after provisional Phase 8 work

Date: 2026-11-17 (Asia/Tokyo)
Status: user-selected future development procedure; journal record only

## Decision and effective boundary

After the currently selected provisional Phase 8 development is completed,
continue the rollout in two parallel development lanes. The earlier serial
order was Phase 7 -> Phase 11.1 -> Phase 8 -> Phase 8.1 -> Phase 9 -> Phase 18,
as recorded in the [roadmap](../../../phase/README.md) and the
[earlier sequencing decision](2026-11-11-parallel-development-first-and-phase-8.1.md).
The new procedure removes Phase 7 from the serial lane and assigns it a separate
parallel owner. It does not declare the current work or any Phase complete.

| Development chat | Assigned development order |
| --- | --- |
| SimpleModeling開発 | Phase 11.1 -> Phase 8 -> Phase 8.1 -> Phase 9 -> Phase 18 |
| sm-workflow-dev開発 | Phase 7, in parallel with the SimpleModeling lane |

The later regular Phase 8 work retains and reuses the provisional work and its
actual validation results. Implement and qualify the remaining requirements;
do not repeat completed implementation merely because the regular sequence
returns to Phase 8, and do not treat provisional completion as whole-Phase
acceptance. Preserve original Phase requirements and explicit dependencies.

## Current provisional development at Phase and Step level

Added at the user's request: summarize development units and their order here,
rather than operational procedures or execution logs. The provisional work is
the selected early portion of Phase 8 plus its necessary prerequisites; it is
not completion of all Phase 8 or all Phase 15 work.

| Order | Phase / development unit | Intended result | Position at this recording |
| --- | --- | --- | --- |
| 1 | Runtime prerequisite support; no new Phase or Step number | Continue an existing Task when necessary ownership or retained-validation bindings change, preserving its original purpose and progress. | Prerequisite support delivered; its results are retained. |
| 2 | INIT-1: Phase 15 early initialization capability consumed by Phase 8 | Ordinary `startXXX` prepares or reuses the required environment at project-root-relative default locations and returns a usable Session ID. The skill does not perform separate initialization, and no initialization-only CAR is required. | INIT-1 completed as a prerequisite; this does not close the broader initialization or Phase 15 scope. |
| 3 | SST-1: Phase 8 Session-owned operational state and thin skill inputs | Runtime owns Session-bound RepositorySync candidate/check workspaces, validation connections and continuation state, reducing per-Session configuration work in the skill. Preserve the same Session and its prior results through setup and repair. | Current selected development unit; connected qualification, remaining repair and Task closure are in progress. |
| 4 | Phase 8 planned follow-on: program-owned semantic result submission; Step number not assigned here | Convert worker results into the issued public result type in the program, eliminating parent-AI JSON reconstruction. | Retained next priority after Session-owned state; not reported as implemented or accepted. |

The priority discussed for early Phase 8 development is **runtime prerequisites
-> INIT-1 -> SST-1 -> program-owned semantic result submission**, followed by
the applicable remaining operational improvements and qualification/closure.
This is a priority summary, not adoption of a new Step. The regular SimpleModeling
lane starts with Phase 11.1 after the actually adopted provisional scope is
completed. Required validation, independent review and local closure belong to
each adopted unit. Finishing an auxiliary runtime repair does not by itself
complete the feature unit that needs it.

Program-owned semantic result submission and the other remaining Phase 8
operational improvements retain their original requirements and adopted scope.
No new official Step number or additional provisional completion gate is created
by this journal, and no already-adopted provisional work is silently moved to
the later regular Phase 8. Reuse accepted provisional results and address the
remaining requirements when the regular lane reaches Phase 8.

## Source synchronization and command supply

The user clarified that sm-workflow-dev開発 does not directly use an executable
supplied from SimpleModeling開発's development checkout. The supply route is:

1. SimpleModeling開発 develops sm-workflow through its assigned Phase sequence.
2. Synchronize that development result into sm-workflow開発.
3. sm-workflow開発 provides the `sm-workflow` command through its normal delivery
   route.
4. sm-workflow-dev開発 uses that normally provided command to conduct Phase 7.

Keep feature-development responsibility, source synchronization, normal command
delivery and actual consumer use attributable as separate outcomes. Synchronizing
source alone does not prove that the consumer command has been updated. A
development checkout launcher or a task-private patched runtime is not the
standard supply route selected by this decision.

## Failure handling during the rollout

If the supplied workflow engine cannot conduct Phase 7 because of a bug or
other runtime problem, sm-workflow-dev開発 waits for SimpleModeling開発's next
version, delivered through the synchronization and normal command-supply route
above. Repeated ad hoc engine-repair requests that continually redirect the
SimpleModeling Phase sequence are not the default development procedure.

Retain the failure observations and existing Session, candidate, history,
repair counts and applicable successful results while waiting. Waiting for a
new engine version does not authorize restarting work or replaying accepted
effects. This policy concerns the engine used to conduct development; it does
not transfer ordinary Phase 7 implementation fixes to another owner.

After SimpleModeling開発 has completed through Phase 18, any remaining inability
of sm-workflow-dev開発 to use the supplied command is handled as ordinary bug
fixing in sm-workflow開発. That maintenance responsibility is distinct from its
normal source-integration and command-delivery role during the preceding rollout.

## Recording scope and preservation

The user requested this journal record and explicitly said not to notify the
parent chat. This entry records the future procedure; it does not send messages,
change active development assignments, edit the roadmap or Phase plans, operate
a Goal/Session, synchronize repositories, or publish/install a command.

Preserve existing worktrees, staged and unstaged changes, active work, accepted
results, histories and repair counters. Normal execution and effect permissions
continue to apply when the procedure is actually carried out. No product source,
configuration or Git index change, commit, push, publication or test execution
is part of this documentation request.
