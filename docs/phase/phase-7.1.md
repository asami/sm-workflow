# Phase 7.1: Managed Dependency Source-Project Integration

Date: 2026-11-23
Status: on hold by user decision — 2026-11-23; no implementation or qualification started by this plan.
Owner: sm-workflow-dev development; bounded producer work stays with its owner.
Depends on: the delivered Phase 7 managed-worktree, delivery and validation routes.
Checklist: [P71 acceptance](phase-7.1-checklist.md).

## Hold decision — 2026-11-23

Source-project integration is not selected for implementation. The source-first
defaults and delivery steps below are retained as the original proposal, not
active execution requirements. Resume this Phase only after an explicit decision
to revisit its scope.

Continue using the existing project-scoped local JAR route as the recommended
connection. Machine-wide local JAR consumption is also an explicit user choice,
for example when the parent should retain ordinary dependency settings without
the scoped adapter opt-in. Shared publication can affect other consumers of the
same coordinates; do not silently fall back between these connections. This
decision does not claim that both choices are already connected in public
Operations or qualify an unverified parent build.

Keep delivery location (managed local worktree or assigned chat) separate from
the chosen JAR connection. Preserve existing implementation, accepted results,
Goals, Sessions, histories and repair counts. This hold changes no live Session,
captured assignment or existing Phase 7 acceptance.

## Goal and boundary

Connect an existing managed dependency worktree to the parent SBT build through
`localProject`, so ordinary parent compilation and tests use that selected source
without a dependency publication round trip. Preserve Phase 7's accepted work,
scoped `localJar` route, explicit Fixed mode and original evidence. A registered
worktree is not proof of a usable parent build.

The reported consumer is Phase 25's `textus-ai-core` dependency. Its managed
worktree was selected, but the registration used `localJar=true`; the parent
build's source-project connection was not established. The inspected
`ManagedDevelopmentRuntime.repositorySyncInputC` rejects `localProject` because
it lacks an admitted scoped SBT adapter. The current JAR path also requires
`ManagedDevelopment.edgeC`'s local JAR binding, and sbt-cozy's overlay adapter
restricts project references. Declaring a source binding alone cannot remove
those execution gaps. This Phase delivers the missing connected route.

Source synchronization, development delivery and parent build consumption retain
their distinct owners. `DevelopmentSubtree` versus `AssignedChat` selects the
delivery method; `localProject` versus `localJar` selects the build connection.
This Phase follows Phase 7 by scope, without reopening its acceptance. No delivery
slot is selected here, and the adopted Phase 25/18 roadmap is not reordered.
The separate Phase 22 operational qualification remains with its existing owner.

## Intended selection and use

- For a new applicable managed JVM dependency, prefer source-project integration
  as the normal default. Offer `localJar` as an explicit alternative/opt-out;
  preserve explicit user/configured choices, Fixed restrictions and captured
  Session bindings. Unsupported source integration reports its actual gap rather
  than silently enabling localJar.
- Confirm the actual parent's `build.sbt` and `project/plugins.sbt` integration,
  supported adapter/plugin version, selected project/module and dependency
  mapping. Supply a reusable one-time build opt-in. Subsequent worktree selection,
  update, detach and reattachment use runtime-supplied scoped inputs rather than
  repeated build-file edits, absolute checkout literals or ambient environment.
- Retain the normal baseline dependencies when no source selection is active.
  A selected source project replaces only its declared dependency in the actual
  graph; do not compile both its baseline JAR and source classes. Initial scope
  is ordinary compatible JVM/Scala projects with explicit project identities.
  Unsupported build forms remain concrete limitations, not a broad adapter bypass.
- Report registered, setup-incomplete, selected-unverified and verified states
  separately, naming missing files/settings/bindings. Only actual applicable
  parent checks establish source consumption. Git synchronization does not
  publish a dependency or trigger an implicit producer build.

## Delivery steps

| Step | Work | Acceptance |
| --- | --- | --- |
| P71-S1 | Reconcile Phase 7 bindings and the reported consumer; connect delivery/build-mode selection and parent preflight. | Both delivery methods are visible when needed; source is preferred, explicit opt-out is retained, and missing parent settings are named. |
| P71-S2 | Deliver the reusable parent-build opt-in and scoped source-project execution adapter with bounded producer handoffs. | A tiny parent compiles and tests against its managed dependency source without local publication; ordinary unselected builds and the existing JAR route still work. |
| P71-S3 | Connect native candidate/check applicability and source-selection lifecycle to the existing managed owners. | Changed dependency source invalidates affected parent evidence even when the parent tree is unchanged; switching/detaching and separate consumer selections preserve histories and isolation. |
| P71-S4 | Qualify the installed public route, update skills/help and perform independent integrated review and normal local closure. | Connected positive/negative behavior and the reported consumer pattern have actual evidence; limitations, accepted source revisions and usage are durably recorded. |

## Producer ownership and validation

sm-workflow owns selection, setup reporting, original Session continuation and
consumer acceptance. CNCF changes, if needed for typed SBT source inputs and
Compile/TestCompile/metadata-selected TestOnly execution, are implemented and
recovered on canonical main by `CNCF開発`. sbt-cozy's owner delivers any required
project-reference/build adapter and its focused checks. Source integration must
use those supported owners instead of removing the JAR adapter's checks or
adding caller-built SBT commands, temporary build scripts or source copies.

Reuse actual applicable Phase 7 evidence. Qualify with small real parent/provider
repositories: an unchanged baseline, source-visible API change, Test-only change,
a bounded selected transitive dependency, failure and corrected continuation,
detach, two independent consumers, and fresh-process reopening. Use normal
runtime-owned checks with the exact selected source subject and independent
review; no repeated broad predecessor validation solely for a new Phase label.
Source changes must not reuse stale JAR or prior-source success as new evidence.

The reported Phase 25 case is an integration target, not authority to alter its
live Session, pending Human or build files now. Any later adoption uses the
supported public route and its actual selected target. Preserve accepted results,
UNKNOWN/failure records, cumulative repair counts and successful effects without
re-Start, accepted Submit replay or automatic migration of active localJar work.
This planning change starts no Goal, Session, producer request, publication or
repository synchronization and marks no acceptance item complete.
