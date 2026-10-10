# Development Artifact Overlay as native Phase 7 functionality

Date: 2026-10-06 (Asia/Tokyo)
Status: specification and Phase planning adopted; implementation pending

## User direction

The user proposed development-task-owned artifact repositories and explicit,
immutable consumer selections, named Development Artifact Overlay, then selected
Phase 7 as its implementation phase. The pasted proposal originally assumed a
skill-managed implementation. The user requested adaptation to sm-workflow and
confirmed that SBT shared-cache exclusion belongs to SBT/tooling rather than a
machine-wide serialization policy in sm-workflow.

## Adopted design

The [specification](../../../spec/development-artifact-overlay.md) preserves
unique publication coordinates, dirty-source origin, build dependency selections,
JAR/Ivy metadata, direct/transitive selection, no silent fallback, staged batch
publication, classpath/generation refresh, six operations and reference-aware
explicit pruning. Selection consistency and consumer compatibility are distinct;
generated-code compilation checks generator/runtime API compatibility.

Adaptations from the pasted skill proposal:

- sm-workflow owns typed application state and registered Operations, using
  common Workflow/execution contracts; a skill is only a client/semantic worker.
- CNCF standard Component configuration/resources select the physical location.
  An illustrative `.textus/sm-workflow/work.d/` provider mapping replaces a
  mandatory `.codex-workflow` layout. Consumer selection is a typed binding,
  not a separate authoritative file edited by a skill.
- SBT and Cozy execute through product adapters. A shared-skill `publishLocal`
  receipt cannot stand in for overlay publication; any Codex harness changes
  belong to that environment, not the public product protocol.
- No machine-wide/SBT-wide product mutex is introduced. SBT/Ivy/Coursier own
  their cache exclusion; real output/selection/prune conflicts are coordinated
  by resource. The current Codex development runner/serial-wrapper constraint
  remains separate and is not changed by this documentation task.
- Unique coordinates and non-overwriting publication are ordinary operation
  semantics, not a reason to restore hashes, content snapshots, tamper checks
  or unchanged-content certificates.

## Phase scope and acceptance

[Phase 7](../../../phase/phase-7.md) owns the initial delivery, with
[P7-DAO implementation/acceptance items](../../../phase/phase-7-artifact-overlay-checklist.md).
Implement SBT publication/resolution, then Cozy generation/classpath integration,
then native Operations/execution adapters and CLI/MCP using the Phase 6 client
boundary. Pin concrete SBT version format/task/settings/visibility behavior in
the adapter design before implementation; those names are not fabricated here.

Retain all six SimpleModeler driver cases: distinct versions for two consumers,
ordinary Cozy unaffected, generated API versus CNCF mismatch detection, explicit
failure without fallback, correct update/rollback generated output and classpaths,
and no mutation of target shared Ivy-local artifacts through all operations.
Also exercise transitive conflicts/cache behavior, partial publication, in-use
inputs and prune protection. Review integrated behavior rather than each helper.

The Git synchronization full-test policy remains in effect. Selection changes
also require relevant consumer regeneration/compilation/tests even when source
Git revisions have not changed. Phase 8 owns optional expansion, not the initial
overlay acceptance. No additional Phase 2/5/6 completion gate is introduced.

## Build-input decision

The user selected explicit arguments supplied by sm-workflow per build and wants
the same build.sbt for ordinary and development-overlay use. The specification
now assigns input handling to shared SBT integration, with a JVM-property input
and no per-selection source-build edits or persistent environment variable.
Existing sm-workflow already uses sbt-cozy, and Cozy's build has an individual
SimpleModeler version property override; neither is claimed to implement the
full overlay protocol. Reuse a common integration point instead of multiplying
per-module property branches. Any initial plugin installation/update/activation
is separate from ordinary selection changes. Acceptance covers the same build
definition across ordinary/select/update/rollback/detach behavior.

## Documentation changes

Historical proposal (superseded below): build.sbt explicitly defines overlay participation
and sbt-cozy assists that definition with shared settings/helpers. Preserve the
ordinary dependency-coordinate declarations, without per-dependency wrappers;
the effective development coordinate and JAR path can differ. Initial explicit
integration is allowed, while selection/update/rollback/detach do not rewrite
the build definition.

Final 2026-10-06 decision: the user removed the proposed
`.settings(cozyDevelopmentArtifactOverlaySettings)` declaration. With supported
sbt-cozy installed and enabled, the per-build selection argument activates the
overlay by itself. Keep an existing sbt-cozy-enabled build.sbt unchanged, including
its normal dependency declarations. Plugin version updates/normal installation
remain distinct prerequisites; no overlay-specific opt-in or dependency wrappers
are required. No argument means ordinary behavior; an invalid explicit selection
fails. The specification, Phase 7 plan and acceptance checklist now require this
behavior, including a pre-overlay build definition without special settings.

Project composition and local worktree operation use
`${project}/.textus/sm-workflow/resources/` for shared definitions and CNCF local
configuration for machine-specific paths. `work.d/` holds managed checkouts and
`artifacts/<overlay-id>/repository/` holds development JARs/Ivy metadata outside
Git. Native sm-workflow state owns consumer selections and passes the chosen
input to sbt-cozy, which resolves exact coordinates rather than scanning for the
latest JAR. The user's "Phase 75" reference was explicitly corrected to
sm-workflow Phase 7; no Cozy Phase 75 change is part of this update.

Created the specification and checklist; updated Phase 6 boundary notes, Phase 7
scope/acceptance, Phase 8 carryover notes and the roadmap. Existing concurrent
Phase 2/other development changes are retained. This is a documentation update:
no implementation, SBT execution, artifact publication, shared-cache change,
consumer binding change, prune, commit or deployment has occurred. Document
references, formatting and policy consistency are checked separately.
