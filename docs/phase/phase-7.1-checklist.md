# Phase 7.1 acceptance checklist

Status: on hold by user decision — 2026-11-23; no implementation or acceptance claimed.
Plan: [Phase 7.1](phase-7.1.md).
Mark items complete only with actual attributable evidence and its scope.

All items below retain the original source-integration proposal and are
suspended. Source-first defaults are not the current usage policy. Continue with
the existing project-scoped local JAR connection (recommended), or an explicitly
chosen machine-wide local JAR connection, as described in the plan's hold
decision. No checklist item or live Session is reset or marked complete.

## Selection and parent setup — P71-S1

- [ ] P71-01: Delivery choice (`DevelopmentSubtree`/`AssignedChat`) and build connection (`localProject`/`localJar`) are independent and presented accurately; a worktree choice never silently enables localJar.
- [ ] P71-02: New applicable managed dependencies prefer/default to localProject, with explicit localJar opt-out; configured/user-selected and captured modes, Fixed restrictions and repository ownership remain effective. Unsupported source mode returns its actual limitation without fallback.
- [ ] P71-03: Parent `build.sbt` and `project/plugins.sbt`, effective adapter/plugin version, project/module identity and dependency mapping have concrete setup diagnostics. Registration/status success alone never reports usable delivery.

## Source-project execution — P71-S2

- [ ] P71-04: Reusable one-time parent build opt-in consumes exact runtime-selected project sources; later worktree changes use scoped inputs without rewriting baseline dependencies, embedding machine paths or mutating ambient/shared configuration.
- [ ] P71-05: A real parent uses an identifiable provider source change in Compile and Test without provider publishLocal or a stale published JAR; an unselected build retains ordinary dependency behavior.
- [ ] P71-06: Only declared source dependencies enter the actual graph/classpath; baseline artifacts and selected source classes do not coexist as duplicate definitions. A bounded transitive selection is qualified and unsupported build forms fail explicitly.
- [ ] P71-07: Typed Compile, TestCompile, normal discovery and selected TestOnly retain the same exact selected-source subject and existing execution authority; failures/unknown outcomes stay actual failures/unknowns.

## Applicability and lifecycle — P71-S3

- [ ] P71-08: Changes to provider source, selected project or consumed dependency graph invalidate affected parent validation even when the parent Git tree is unchanged; unchanged exact subjects reuse only applicable retained evidence.
- [ ] P71-09: Source selection update, detach to baseline and reattachment have actual parent behavior/classpath evidence without repeated build-file edits or implicit publication. Failed changes retain the prior selection facts and actual failure.
- [ ] P71-10: Two consumers can select distinct managed provider worktrees independently; no unrelated canonical checkout, foreign source/JAR or shared-Ivy mutation substitutes for either selected dependency.
- [ ] P71-11: Supported fresh-process continuation preserves the exact selected source and native Session, accepted results, prior successful checks, UNKNOWN/failure history and cumulative repair counts without re-Start or accepted Submit replay; existing localJar records are not silently migrated.

## Installed delivery and closure — P71-S4

- [ ] P71-12: The installed public route reports registered, setup-incomplete, selected-unverified and verified states with actual scope; positive source use, missing parent setup, invalid source binding and failed/corrected continuation are exercised through normal owners.
- [ ] P71-13: A connected textus-ai-core-style managed dependency fixture demonstrates source-based parent consumption and both mode choices. Live Phase 25 adoption, if separately selected, retains its original Session/decisions; a fixture supplies no live-consumer acceptance.
- [ ] P71-14: Necessary bounded CNCF/sbt-cozy deliveries have attributable canonical source, required checks/review and normal distribution/adoption evidence before sm-workflow claims their capabilities; unneeded producer work is omitted.
- [ ] P71-15: Skills/help describe source preference, explicit JAR alternative, parent prerequisites and limitations; proportionate connected verification, independent integrated review, local closure and a durable English usage/evidence record complete this Phase. Source synchronization remains separately selected.
