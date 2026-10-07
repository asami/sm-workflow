# Subsystem and Component storage initialization responsibilities

Date: 2026-10-01
Status: design direction recorded from the discussion; not an implementation or acceptance report

Subsystem provides and manages the shared database infrastructure. Each Component
assembles its own repositories, services and Operations on that infrastructure.
Configuration decoding precedes both and must not acquire database resources.
This note records the requested common-pool direction and the initialization
responsibilities discussed alongside it; it does not claim that all callers
have already been migrated.

## Why configuration decoding created a database

The inspected `DataStoreSpace.create(configuration)` implementation is a resource
factory, not just a configuration parser. It selects a DataStore backend, creates
that backend and registers it in a new DataStoreSpace. Memory configuration
creates a memory store; SQLite configuration ensures the parent directory and
constructs a SqlDataStore. Calling this factory from configuration decoding
therefore makes a settings-only read acquire physical storage resources.

The factory's purpose is legitimate, but its placement mixes configuration
interpretation with runtime construction. Removing incidental acquisition from
settings consumers and making legitimate database users share the managed pool
are both required. Merely closing each incidental pool would retain the wrong
ownership structure.

The earlier [settings and resource separation journal](2026-10-01-runtime-settings-resource-separation.md)
records the repair and validation evidence available at that point. This note
does not revise that historical evidence or report new tests.

## Responsibility boundaries

Config decodes and validates values. It may describe a backend, connection
destination and policy, but it must not open a connection, create a pool or
prepare a database directory as a side effect of being read.

| Concern | Subsystem initialization | Component initialization |
| --- | --- | --- |
| Configuration | Validate subsystem settings and determine the common database destination | Obtain the validated settings needed by the Component |
| Database pool | Create and manage the common pool once for the selected database | Borrow the common infrastructure; never create a private pool |
| DataStore | Construct the shared physical provider | Obtain an authorized DataStoreSpace and assemble repositories over it |
| Authorization | Resolve owner and collection grants for each Component | Operate only within the supplied grants |
| Schema | Coordinate validated initialization and migration execution | Declare the required schema and migrations |
| EventStore | Assemble the common service and event-type persistence policy | Connect event emission and subscriptions |
| Workflow | Assemble common instance persistence, Continuation and execution infrastructure | Register Workflow definitions, codecs and semantic actions |
| Operation | Provide the common execution environment and transaction boundary | Bind services and Operations to the supplied dependencies |

Sharing a physical pool does not grant unrestricted access. Component-specific
DataStoreSpace views retain their owner and collection permissions. EventStore
policy selects events for persistence; declaring a storage destination does not
mean that every event is persisted. Components send events normally rather than
choosing persistence at each send.

## Initialization order

1. Decode and validate configuration without connecting to a database.
2. Collect Component declarations and validate storage destinations, grants and
   schema requirements before acquiring physical resources.
3. Construct the Subsystem's common pool and physical provider.
4. Run required migrations through the managed initialization procedure.
5. Supply Components with authorized DataStoreSpace views and common services.
6. Assemble each Component's repositories, services and Operations, and connect
   its event subscriptions and Workflow definitions.
7. Expose the assembled application through the CLI or server only after the
   complete initialization succeeds.

Component declaration collection must not depend on first constructing the
Component's database-backed runtime services. This keeps validation ahead of
resource acquisition and makes the ordering enforceable.

Initial data installation is an explicit operation separate from ordinary
startup. Reopening a host must not reset an existing Workflow instance,
Continuation or event cursor. Initialization failure must unwind acquired
resources and subscriptions without closing infrastructure borrowed from an
external owner.

## Pool ownership and shutdown

For the current selected database, use one common physical pool. Repository,
DataStoreSpace and Component creation must not duplicate it. A pool is a shared
connection manager, not a single connection or a transaction shared by all
operations; each operation still uses its managed transaction boundary.

Shutdown proceeds in reverse dependency order: stop new admissions, let active
work finish or follow its defined cancellation policy, release Component
subscriptions and borrowed views, then let the physical owner close the pool
once. Component shutdown never closes the common pool.

Subsystem is the application-facing management boundary. If SystemNode performs
the physical ownership internally, that is delegation to one owner, not a second
pool or a second shutdown authority. A Subsystem borrowing a runtime-owned
SystemNode releases its binding but does not stop that node or close its pool.

The uniqueness requirement is per selected physical database, not one global
pool across unrelated database destinations or separate processes. If multiple
destinations are explicitly supported later, each needs a distinct identity and
one managed pool. No arbitrary request input may select a new pool or bypass
the admitted storage policy.

## Verification required before claiming completion

Executable specs should prove that configuration reads do not acquire storage;
multiple Components using the same configured database share one physical
pool; authorization remains isolated despite that sharing; and concurrent
initialization does not create duplicate pools. Lifecycle checks should cover
startup failure cleanup, owned shutdown, borrowed-node survival and database
reopen without resetting retained Workflow state.

These are verification requirements for the direction recorded here, not test
results. No source edit, build, migration, commit or Phase acceptance is performed
by recording this note.
