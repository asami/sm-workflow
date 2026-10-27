# Phase 19: Docker Clean Installation and Customization Qualification

Status: planned; no implementation or test success claimed.
Date: 2026-10-28
Owner: sm-workflow development
Specification: [clean installation qualification](../spec/clean-installation-qualification.md)
Checklist: [P19 acceptance](phase-19-checklist.md)
Handoff: [English development handoff](../journal/2026/10/2026-10-28-docker-installation-customization-handoff.md)

## Position and dependencies

Create an independent HEAVY qualification track. Its number does not require
completion of all earlier Phases and does not alter the main 6.8 -> 6.9 -> 6.10
-> 7 rollout or active acceptance. Begin with available ordinary distribution,
CLI, installer and initialization capabilities. Consume the bounded Phase 15
[INIT-1–4](../notes/project-initialization-delivery-plan.md) delivery for genuine
configuration-free bootstrap and the [assembly-free correction](assembly-free-startup-correction.md)
where required. Missing producer work is a named gap, not a fixture workaround.
Only the three-profile cases depend on 6.10. General test execution does not wait
for 7, 9, 13 or 18; reuse their delivered capabilities when relevant.

Preserve existing development, Session/Plan, accepted work, histories, repair
counts and verification. This plan does not install into the real HOME, change
model/config choices, run Docker, publish artifacts or start a new Goal.

## Main route first

| Unit | Work | Exit evidence |
| --- | --- | --- |
| I1 | Inventory actual distribution/install/init APIs, supported platforms and config precedence; map CI-01–18 to existing tests and producer gaps | A concrete normal user recipe and per-case support/owner/dependency map; no hand-built private bootstrap |
| I2 | Build one fresh non-root Linux fixture, install normal runtime and skills bundle, initialize and complete one clean RepositorySync; restart and read its result | CI-01–04 and initial CI-17 through the actual installed route, not only extracted files or source fixtures |
| I3 | Add path/config/data/toolchain customizations and preservation scenarios | CI-05–12; actual effective SBT settings and one tiny validation; 6.10 profile variants enter when its contract is delivered |
| I4 | Add supported updates, interruption/recovery, isolation, removal and resource cleanup | CI-13–18 with retained state/evidence and controlled failures; no production data or remote effects |
| I5 | Connect canonical HEAVY selection, installer documentation and reproducible developer/CI entry; review coverage and host boundaries | FULL/ADMISSION exclusion, actual run receipts, evidence/cleanup accounting and explicit remaining platform/client gaps |

Wire minimum HEAVY metadata and owned cleanup during I2, not after destructive
failure cases. I5 expands and qualifies selection/coverage. Establish one normal
user path before adding combinations. Reuse product packaging, installer,
initialization, Git/SBT execution, Journal/Evidence and lifecycle mechanisms.
Retain existing successful results at their actual scope; do not rerun an entire
product FULL suite for every container/configuration.

## Completion criteria

Complete the supported matrix in [P19](phase-19-checklist.md) with actual Docker
evidence on at least one declared Linux platform. A Docker/prerequisite absence
is not a passed/skipped qualification. Explicitly separate pure/local tests,
Docker integration and native desktop checks. Use focused verification during
implementation and one integrated review of installation/configuration behavior.

The ordinary fresh-install, customization-preserving reinstall/update, restart,
failure retention and owned cleanup routes must pass. Planned producer-dependent
rows remain pending until delivered or explicitly re-scoped; do not declare full
matrix coverage while hiding them. Cost/quality profile selection can be tested
without live model execution. Actual Codex role loading and chat UI require a
separate host qualification and are not claimed by Docker success.

Do not require universal backward compatibility: select and document supported
update pairs. Existing session/data preservation requirements still apply, and
incompatible upgrades need an explicit supported migration or refusal rather
than silent destructive replacement. No automatic downgrade after data migration.
