# Configured service assembly through the existing factory hook

The user selected the latter option: use the existing post-resolution Factory
initialization hook. Resume the same active Phase 2 goal; do not add a CNCF API
or change create_Core ordering. Retain parent-direct limited recovery.

Own admission/GoalPhaseConfiguredComponentFactory.scala,
admission/GoalPhaseManagedComponentFactory.scala, its new configured-factory
spec, and the existing managed suspension spec's factory composition helper.
Preserve unrelated changes. The new private factory takes host-owned typed
parameter declarations/path routes and a service binder. Each Component carries
its own immutable ComponentCreate; no mutable factory-global capture or pool.
create_Core creates only an empty provisional protocol. The existing Factory
hook receives the canonical resolved parameter snapshot, binds the service,
copies only the core protocol, and then invokes normal component initialization.
Preserve instanceId, jobEngine, factory, role, origin and all admitted metadata.
Reject bad configuration before advertising a component; no fixture fallback.

Share the existing GoalPhase managed-service binding through a pure helper so
the configured hook can build the same four Operations, not a second dispatcher.
Use this path in existing real SQLite managed Start/review/repair/re-review
specs. Separate factory specs check resolution order, named instance metadata,
independent reusable factory constructions and missing/malformed input without
calling the service binder. Every new or modified scenario uses explicit
Given/When/Then and behavior-oriented matchers.

Focused validation: existing eight-suite accumulator plus the configured
factory suite. Preserve actual packageBin/JAR catalog checks. No new CNCF
protected contract, default launcher activation, host authority synthesis,
managed command adapter, release, commit or publication is selected. This
factory route is a real prerequisite for default launcher configuration, not
proof that its still-missing durable authority adapters are complete.

Attempt 484 compiled and packaged main code, then Test compile rejected the
new spec's `key` field because ScalaTest Matchers already defines that keyword.
Rename the probe to limitKey and use an explicit immutable-vector append.
No tests ran; SBT/wrapper exited 1 and lock released. Complete summary was read
at `/tmp/skill.cncf.d/cncf-sbt-f6dbb2f51b9f08231543e263720d6b04d53d06f4f7f3b198cc3e2865c9dfbf22-81b8ba1041f6c42934f451907c0634c9/44858-20261001T014155Z.summary.json`.
Read-only Core inspection also establishes that protocolLogic is derived from
protocol and provisional Core includes framework default services. Preserve
those services, reject collisions, and rebuild only protocolLogic alongside
the protocol; retain all resource/identity/engine fields. This is the frozen
hook's core-consistency obligation, not a framework contract change.

Attempt 485 successfully packaged the actual main JAR and passed all 66 tests
in nine suites, zero failed/canceled/ignored/pending/aborted. SBT/wrapper exited
0 and the shared lock was released. Complete summary was read at
`/tmp/skill.cncf.d/cncf-sbt-9084441f43280151061447d3227b7cd940b6a3df365012903f4be34962e13e7d-b7ab98184e2d272b84526168c0ba6446/49134-20261001T014944Z.summary.json`.
The new factory cases verify named instance override provenance, snapshot
identity through ordinary initialization, retained jobEngine/factory/metadata,
separate creations on a reused factory, missing/malformed settings before binder
invocation and rejected/null-service fail-closed composition. Existing actual
managed SQLite Start/review/reopening/repair/re-review scenarios now use this
post-resolution hook and pass, including interrupted follow-up recovery.

This resolves the selected construction-order prerequisite without changing
CNCF's API or creating a pool. It is not production default CLI acceptance:
impl/ComponentFactory.scala still selects placeholder services. Application
configuration keys, durable human selection/association/closure authority,
managed external command receipts and default factory binding still need real
adapters. Path-route-specific configured-hook coverage and independent acceptance
review remain required before declaring the whole configuration boundary closed.
No SBT session remains from 485, and no commit/publication/Phase closure occurred.
