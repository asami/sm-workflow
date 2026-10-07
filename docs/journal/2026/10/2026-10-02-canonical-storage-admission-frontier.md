# Canonical CLI storage admission frontier

## Recovery scope

The user's direct instruction `回避して` continues the previously authorized
limited parent recovery in the same sm-workflow Phase 2. Do not recreate a
runner or repeat a chat approval merely to satisfy an unconverted skill control
boundary. This is not permission to bypass platform review, the shared SBT
lock, owner checks, independent acceptance review, or original-base gates.
No new goal, successor Phase, commit, publication, or test success is implied.

## Current source findings

The preceding Factory discovery repair reached the required component setting
check through the actual launcher. This investigation is read-only producer
work; it does not report a new platform denial or a new validation run.

The concrete host insertion point is
`CncfRuntimeInstanceLifecyclePart`: `_runtime_configuration_preflight` resolves
the selected Subsystem identity and source-bearing configuration candidates
before `SystemNode.create`. The subsequent `SystemNodeConstruction.withNode`
calls `DefaultSubsystemFactory.runtimeDefaultWithScopeC`. Runtime bindings are
admitted to the constructed Subsystem later. Storage admission needed before
Component construction cannot rely on that later admission alone.

`CncfRuntimeConfigurationProjection` consumes already loaded source snapshots,
not a merged configuration. Canonical split files distinguish a Subsystem
instance from a Component class/instance; the closed parameter catalog decides
which target kinds are permitted. There is currently no storage declaration
parameter in that catalog. Introducing the declaration requires a typed
parameter and its source/target admission, not merely a new string lookup.

`SubsystemStorageInitialization.withPreparedStorage` already precedes service
assembly, borrows the SystemNode-owned pool, and verifies Workflow spaces. It
only prepares declared storage when `EventStorePersistenceConstruction.current`
is present. `SubsystemEventStoreBootstrap` supplies this capture for the
programmatic host route; the ordinary CLI route does not yet supply it.

Do not decode owner grants from `GenericSubsystemFactory`'s effective merged
configuration: descriptor defaults can originate from a discovered Component.
Nor use `ConfigurationAccess.getString` for grant admission: it permits scalar
coercion and ambient system-property/config-file fallback. Neither establishes
that the selected host admitted a particular owner/collection permission.

## Next implementation boundary

1. Decode the EventStore policy, Workflow declarations and optional create-only
   record declarations as bounded typed data, using existing declaration
   constructors and reserved-collection checks. Decoding acquires no pool,
   creates no path and installs no initial data.
2. Admit the declaration for the selected host/Subsystem with explicit source
   and target evidence before allocating the SystemNode. Component defaults
   are requests/configuration, not self-issued host authority. The precise
   external parameter schema and permitted origins must be frozen before
   activating this path; this journal does not invent that schema.
3. Carry the admitted declaration into the canonical construction scope before
   `runtimeDefaultWithScopeC`, reusing the existing EventStore/record capture
   and common-pool preparation boundaries. Do not instantiate another
   Subsystem through the programmatic bootstrap as a CLI workaround.
4. Let `create_Core` assemble services only after that infrastructure is ready.
   Keep initial cursor/data installation separate and reopen without reset.

Required executable specifications cover no-resource decode/preflight failure,
Component-origin and wrong-target rejection, reserved collection rejection,
one shared physical pool with isolated owner views, assembly-failure cleanup,
and a separate-process CLI positive construction plus missing/invalid-setting
refusal. Stored human selection, actor authentication and execution adapters
remain separate requirements; storage admission cannot manufacture them.

## Evidence disposition

Only this journal is added in this investigation. No Scala, generated output,
runtime configuration or producer source is changed. No SBT or launcher is
invoked. Earlier 85 producer and 205 consumer focused successes remain the
preceding slice's evidence, not tests of this proposed connection. Phase 2,
independent review, full validation and exact acceptance/release remain open.
