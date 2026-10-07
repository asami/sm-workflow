# Configuration decoding and the Subsystem-owned shared pool

## Attributable direction

The human explicitly requested that Subsystem manage a single common DB pool,
then clarified that configuration-reading internals should perform the reuse,
rather than making every settings consumer manage resources itself. This
supersedes the planned caller-by-caller Blob/Generic settings migration as the
primary repair strategy. Retain useful validated settings parsing; do not treat
those earlier corrections as the complete pooling solution.

## Current evidence

SystemNode already owns the physical SQL registry, canonical identity
single-flight, bounded leases, drain and final close. Subsystem binds to it.
Managed ComponentDataStore resolution shares that registry, and Workflow plus
EventStore already require the same physical managed resource. The bypass is
DataStoreSpace.create(configuration), called during RuntimeConfig.from, which
immediately constructs an unregistered caller-owned SQLite pool. Runtime startup
also reads RuntimeConfig before creating its SystemNode/Subsystem.

Do not add another static/global pool cache or registry, cache a lease-bound
DataStore view for use after its lease, bypass owner/collection permissions, or
merge genuinely different database/credential identities silently. The deployed
shared database must have one physical pool; all logical collection users must
borrow through the existing owner. Config decoding must not allocate a second
pool before a managed application binding has an opportunity to select it.

## First central source slice

Frozen CNCF paths:

- `src/main/scala/org/goldenport/cncf/datastore/DataStoreSpace.scala`
- `src/test/scala/org/goldenport/cncf/config/RuntimeSettingsSpec.scala`

Make configured default storage acquisition lazy inside DataStoreSpace.create,
not in Blob, Metrics, or other callers. Retain eager rejection of missing SQLite
paths. A scoped managed application view takes precedence without initializing
the configured caller-owned default. Explicit useDataStore replaces the default
selection. If a standalone caller actually requests unbound storage, retain its
existing caller-owned semantics, initializing its default once under a lock.
This compatibility route is not a deployed shared-pool acceptance claim.

Executable evidence must prove ordinary RuntimeConfig.from creates no DB merely
to read settings, and two such spaces bound through the existing managed owner
resolve the same physical resource without initializing their private defaults.
Public host and managed completion regression must attribute every pool start
to an owner and compare terminal shutdowns. This slice eliminates eager bypass;
the remaining audit must still cover unbound runtime data access and launcher
bootstrap so they cannot fall back to standalone ownership in deployed use.

No Phase closure, review, commit, publication, or new repository authority is
added. Existing dirty/untracked work remains preserved.

## Focused evidence and initialization-order gap

`SMWF-CNCF-CONFIG-SHARED-POOL-DIRECT-20261001-416` completed with 84 executed
tests in seven suites, zero failures/canceled/pending/aborts, SBT/wrapper 0/0
and the shared lock released. Session 8026 is terminal. Its matching JSON
summary was read; the complete log has 69 pool starts and 69 completed
shutdowns. The new example proves two ordinary RuntimeConfig decodes do not
create a database and two managed bindings use the same physical resource.
These counts describe this exact focused workload only, not every production
startup path, permission boundary, or concurrency case.

Summary: `/tmp/skill.cncf.d/cncf-sbt-95c56828984ecc15d9446028a18279ecad79bbfb8b03ef1f594041bc41f09dae-1ec7521db72c2cc4fdfec3601fe8ff36/60674-20260930T205241Z.summary.json`.
The complete log is the adjacent `60674-20260930T205241Z.log`.

The human then asked whether the implementation follows
[Subsystem and Component storage initialization responsibilities](2026-10-01-subsystem-component-storage-initialization.md).
That document's seven-stage order is not yet implemented. Current central
lazy acquisition addresses configuration-only DB side effects; it must not be
substituted for the complete declared initialization order.

Observed mismatches:

- Component.Factory's `_create_participant_c` calls `create_Component` and
  `create_Core` before parameter/bootstrap initialization. Runtime services
  therefore need not wait for collection and validation of all declarations.
- DefaultSubsystemFactory constructs built-in Components before adding them;
  Subsystem `_prepare_components_c` bootstraps already constructed Components.
  There is no demonstrated pre-resource, whole-component storage-declaration
  admission stage in that path.
- Shared resources are currently resolved on demand, not in the required
  explicit common-pool/provider initialization stage before runtime assembly.
- The observed startup path has cleanup and security-configuration admission,
  but no demonstrated unified migration-before-service-assembly gate.
- Standalone configured DataStoreSpace still has caller-owned first-use
  compatibility behavior. Deployed unbound accesses must not reach it.

Next implementation must separate declaration/admission from runtime assembly,
create the admitted common provider, run required managed migrations, then
supply authorized component views and assemble services. Initial cursor/data
installation remains explicit and must not be moved into ordinary restart.
The whole seven-stage order requires its own executable spec. No completion,
independent review, acceptance commit or release is claimed here.

## Component core assembly clarification and next source frontier

The human clarified that Component initialization may happen while `create_Core`
assembles its services. Keep that assembly method: its existence or its position
before Component parameter initialization is not itself a defect. The requirement
is that Subsystem infrastructure needed by database-backed assembly be prepared
before entering it; Components borrow rather than own physical pools.

The next bounded CNCF source frontier is `SubsystemFactory.scala`, a new internal
`SubsystemStorageInitialization.scala`, and `SubsystemEventStoreOwnershipSpec.scala`.
For the canonical default persistent EventStore host, prepare each declared
Workflow/EventStore pair through the existing verified managed-space API before
built-in Component construction. Use the exact host configuration, not unrelated
global configuration. Keep the existing identity registry and scoped permissions;
never retain a lease-bound view after preparation. Hosts without a persistence
declaration retain their existing construction path.

Preparation failures must release this Subsystem binding but must not shut down
a SystemNode borrowed from its enclosing runtime. The current default startup
cleanup closes the node unconditionally, so that ownership distinction belongs
in this same frontier. Initial cursor installation remains explicit.

This does not implement generic descriptor/launcher preparation, whole-component
pre-resource admission, or a general schema migration gate. Do not claim the
seven-stage design or Phase is complete from this bounded hook.

## Pre-core default-host preparation evidence

The bounded hook is now implemented in `DefaultSubsystemFactory._default_with_scope`:
`SubsystemStorageInitialization.withPreparedStorage` runs before
`builtinComponents(subsystem)`, hence before those factories' `create_Core` calls.
For a declared persistent host it resolves every Workflow/EventStore pair using
the existing verified managed view, then releases the preparation lease while
the owning SystemNode retains the single physical resource. The helper derives
parameters from this host's configuration. It adds no registry and installs no
cursor or initial Workflow data. Component core/service assembly remains intact.

Preparation and assembly failures close an owned node, but only release the
logical binding when construction inherited a runtime-owned node. New specs
observe the pool before the assembly callback, fail that callback to exercise
owned cleanup, and reject a missing persistent destination while preserving the
borrowed node. Existing first-install/reopen and Workflow completion specs still
pass, including explicit cursor initialization rather than restart reset.

`SMWF-CNCF-PRECORE-SHARED-STORAGE-DIRECT-20261001-417` executed 86 tests in
seven suites, all passed, no failures/canceled/ignored/pending/aborts,
SBT/wrapper 0/0, shared lock released. Session 53202 is terminal and its
matching summary was read. The complete workload log contains 72 physical pool
starts and 72 completed shutdowns; this is focused-workload evidence, not a
whole-application lifecycle claim.

Summary: `/tmp/skill.cncf.d/cncf-sbt-efdec6e539338f8896d216ec009c9f46159fe1a4499d2c6ec162da0316c26cc5-bfc76f54cb7e41eaae631634e00e20ef/68992-20260930T210429Z.summary.json`.
Log: the adjacent `68992-20260930T210429Z.log`.

Remaining implementation: descriptor/named/identity construction must apply the
same ordering and borrowed-owner cleanup contract; full declaration admission
and required migration execution still need their own gate and executable spec.
The downstream CNCF artifact has not yet been refreshed for this source slice.
No review, acceptance commit, publication, release or Phase completion is claimed.

## Generic construction source frontier

The next same-lineage source frontier is `GenericSubsystemFactory.scala` and a
new `GenericSubsystemStorageInitializationSpec.scala`. The entire factory was
read before editing. Its named fallback and admitted descriptor paths both
construct repository Components before built-ins and use unconditional owned
startup cleanup. Replace those two assembly wrappers with the existing prepared
storage wrapper, before repository creation/discovery. Preserve descriptor-only
admission, repository-bootstrap policy, SPI resolution, effective configuration
merging and tool activation. TextusIdentity delegates to the descriptor path;
do not duplicate its preparation or change its identity admission.

Verify named/descriptor host storage sharing, effective descriptor configuration,
explicit initial installation and borrowed-node preservation on preparation
failure. Run the existing factory/descriptor/identity and admission regressions
as well as the new spec. This hook alone does not install a launcher persistence
declaration or implement general migrations, and no such completion is claimed.

## Generic preparation implementation and validation

Both Generic factory assembly wrappers now use `withPreparedStorage` before
repository building/discovery and built-in creation. Descriptor-only admission
still precedes Subsystem creation. TextusIdentity continues to delegate to the
same descriptor factory without a second initialization layer. No change was
made to repository policy, classloader identity, SPI resolution or tool activation.

The new three-example `GenericSubsystemStorageInitializationSpec` proves:

- named and descriptor hosts on one borrowed SystemNode retain one physical
  resource and two logical bindings, with no preparation lease retained;
- explicit initial cursor installation still succeeds after assembly, and a
  duplicate cannot reset it;
- descriptor storage defaults plus the supplied path override prepare only
  the effective selected database, not the descriptor-default database;
- missing persistent destinations fail on both routes and release the failed
  bindings without stopping the borrowed runtime node.

`SMWF-CNCF-GENERIC-PRECORE-STORAGE-DIRECT-20261001-418` executed 90 tests in
nine suites, all succeeded, zero failed/canceled/ignored/pending/aborted,
SBT/wrapper 0/0 and shared lock released. Session 48738 is terminal and its
summary was read. Existing Generic factory, descriptor configuration,
TextusIdentity, assembly-admission, public EventStore bootstrap, ownership,
Workflow completion and settings regressions all ran alongside the new spec.
The full focused log records 72 pool starts and 72 completed shutdowns.

Summary: `/tmp/skill.cncf.d/cncf-sbt-5872324593c6c6bb4016654ede685884754d0b5aba91f0c37a161bd06b93ecb9-f29be787f8fa2a8a4c110ca74da4f17b/71591-20260930T210855Z.summary.json`.
Log: the adjacent `71591-20260930T210855Z.log`.

This closes the previously listed named/descriptor preparation-hook gap for
hosts with an installed trusted persistence declaration. It does not prove
launcher configuration installs that declaration, admission of all storage
destinations before physical acquisition, general migrations, or the real
sm-workflow CLI vertical slice. Those remain Phase work. The upstream artifact
has not yet been refreshed; no independent review, commit or release occurred.
