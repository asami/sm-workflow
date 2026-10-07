# Canonical runtime storage preparation

## Frozen source and verification frontier

Continue sm-workflow Phase 2 / admitted CNCF Phase 88. The previous source turn
closed named and descriptor factory preparation for an installed trusted
EventStore declaration (focused-418: 90 tests / nine suites). Runtime lifecycle
inspection now shows `CncfRuntime._initialize` creates its owning SystemNode,
then enters `runtimeDefaultWithScopeC` under `SystemNodeConstruction.withNode`.
It does not automatically install an EventStore persistence declaration.

Do not invent a new CLI/configuration grammar or promote programmatic factory
tests to launcher proof. The next target program is the new CNCF executable spec
`src/test/scala/org/goldenport/cncf/subsystem/CanonicalRuntimeStorageInitializationSpec.scala`.
It must call the existing `CncfBootstrap.initialize(BootstrapConfig(...))`,
install trusted policy/declaration through the existing construction scopes,
and observe the real runtime-owned node, rather than a replacement runtime.

Prove preparation before extra-Component assembly, explicit first-install,
owned handle shutdown, fresh-runtime reopen retaining cursor, and construction
scope isolation from a subsequent ordinary runtime. Use existing controlled
test descriptors/configuration and canonical managed store APIs; no direct SQL,
private pool, generated Operation substitute or new command grammar.

This is a canonical runtime integration prerequisite. It does not prove a
plain launcher discovers a declaration, real GoalPhase service assembly,
separate-process recovery, schema migrations or Phase completion.

## Executed evidence

`SMWF-CNCF-CANONICAL-RUNTIME-STORAGE-DIRECT-20261001-419` executed 18 tests in
six suites, all passed, zero failures/canceled/ignored/pending/aborts,
SBT/wrapper 0/0 and shared lock released. Session 52331 is terminal and the
matching invocation summary was read. The new two examples ran alongside
runtime-owned datastore shutdown, launch-failure, Generic preparation, owned
and borrowed EventStore lifecycle and the public programmatic host regressions.

The test invokes the existing public `CncfBootstrap.initialize` and the actual
runtime lifecycle. A pool is registered and its preparation lease is released
before the extra-Component callback runs. Explicit cursor first-install succeeds.
Closing the owned handle terminalizes its node and clears its registry/binding;
a fresh runtime owns a different node, opens the same database and observes the
retained cursor without resetting it. A subsequent ordinary runtime receives
neither the earlier EventStore policy nor a persistence declaration. The
controlled runtime suppresses pool console logging, so no log-derived global
pool-count assertion is made; the specs inspect the owning registry directly.

Summary: `/tmp/skill.cncf.d/cncf-sbt-b3d24b44ae7bd56bae95d6818cf3ecf1342966c7bd79572397c38b74b90b4219-47e36c8713a447da55b35b899415d0a5/74637-20260930T211438Z.summary.json`.
Log: the adjacent `74637-20260930T211438Z.log`.

The trusted construction scopes were explicitly supplied by this internal spec.
Therefore this proves the runtime can consume an already installed declaration,
not that the ordinary launcher discovers one automatically. No new production
entry API, CLI grammar, config key, generated Operation, source-policy fallback,
schema migration, downstream artifact refresh, review or acceptance commit was
introduced in this turn. Next work must connect admitted host declarations at
the production bootstrap boundary and consume the refreshed upstream artifact
in the real GoalPhase service assembly. The full Phase remains in progress.
