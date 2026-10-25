# Project initialization Operation: producer and control-center handoff

Date: 2026-10-24 (Asia/Tokyo)
Status: specification and development plan adopted; implementation pending
Recipients: sm-workflow開発 and textus-control-center

## Observed problem and decision

During control-center Phase 5.1 setup the caller hand-built runtime configuration
and encountered an incomplete RepositorySync review policy, a forbidden HOME
entry in typed Git declarations, and a missing response output directory. These
were mechanical preparation issues, not product test failures. The inspected
chat subsequently reported successful Start with Session
`fd87cec948d0933cb6181af1f696cb52`. Treat this as a historical observation and
inspect current public state before adoption; do not restart that Session.

The user requests a new initialization Operation reachable without existing project
runtime configuration. Proposed name: initializeProject. It must reuse or create
the installation and return actual readiness/locations/gaps, with no automatic
Workflow Start. Initialization is project-scoped, not a new database per Phase.

## Producer instructions

Implement the [specification](../../../spec/project-initialization.md) using the
[INIT-1–4 plan/checklist](../../../notes/project-initialization-delivery-plan.md).
This is a bounded Phase 15 early slice, independent of broader multi-repository
workspace and catch-up prerequisites. Establish the installed minimal bootstrap
entry, one GoalPhase setup and normal preparation/Start connection first.

Do not put the new Operation behind the complete configuration it creates. Reuse
CNCF managed resources/UoW and existing component defaults; keep the CLI a thin
transport. Generate compatible policy/env/declaration values from shared typed
contracts, not copied AI-authored templates. Preserve genuine semantic Plan/test
choices and existing authority; neither initialization nor caller authentication
fabricates Human decisions or permission for subsequent effects.

Add repeat-safe existing-installation reuse and explicit partial/conflict results.
Keep overrides, DBs, Sessions and evidence. Directory preparation must preserve
old response output. Deliver runtime and sm-goal-phase integration together, with
actual validation and an English consumer adoption procedure. Installed API/CLI
names remain proposed until implemented; this handoff grants no fictional route.

## Consumer instructions

Keep ongoing control-center work, its actual current runtime configuration and
Session, accepted work, history, repair counts and verification results. The
earlier manual setup is not invalidated by the new design. Do not overwrite it,
create another Session, rerun successful checks or pause independent work merely
to wait for this feature. Adopt the delivered reuse/public continuation path at
the appropriate boundary. Fresh-install verification belongs in an isolated
representative project, not a reset of this running consumer.

Share concrete remaining setup gaps with the producer using existing diagnostics.
The user authorizes sharing delivery/adoption information between these chats.
No new per-command approval, checksum framework, source launcher or AI recording
gate is introduced.

This planning update changes documentation only; no active configuration, Session,
product code, validation result, Git commit or remote state was changed.
