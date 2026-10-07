# Configured managed Factory assembly

Date: 2026-10-01
Status: bounded implementation and focused validation passed; production activation open

The previous goal turn made progress: actual packageBin and all 71 tests in ten
suites passed for typed runtime parameters (attempt 488). Continue the same
Phase; no production acceptance or Phase completion follows from that evidence.

Inspection confirms that WorkflowComponentStorage supplies managed instance,
issued-work and completion bindings without exposing a raw provider or lease.
DataStoreSpace has public collection lookup/search, but managed binding and
host-view admission remain CNCF-owned. Standalone compatibility access must not
be used to open an independent pool for application authority records. Durable
selection/association/closure authority and default launcher activation remain
unfinished; no new storage API or authority record format is selected here.

Freeze this implementation to GoalPhaseManagedComponentFactory and the managed
suspension composition helper. Add a main-code configuredC assembly entry which
accepts the admitted catalog and explicit host-owned service/repair ports, checks
their producer correlation, declares the seven required typed parameters, and
uses the existing post-resolution Factory hook to decode the canonical snapshot
and invoke the managed binder. No authority is inferred from configuration and
no storage owner is created. Invalid construction fails before exposing a Factory;
remaining service-port validation stays in the existing bindServiceC chain.

Replace the test-only hand-written callback with this main-code entry. Existing
managed SQLite Start/reopen and ordinary/interrupted repair/re-review scenarios
must traverse it; retain exact decoded configuration/collection/catalog checks
and add incomplete catalog/port/producer rejection examples. Run actual packageBin
and the same ten-suite accumulator. The tests still supply fixture-owned host
authority ports and catalog input; they do not prove real CLI authority or a
default production deployment.

Attempt 489 (SMWF-CONFIGURED-MANAGED-ASSEMBLY-DIRECT-20261001-489) passed actual
Compile / packageBin and all 71 tests in ten completed suites. SBT/wrapper exits
are 0, shared lock released, no failed/aborted/canceled/ignored/pending cases.
The complete structured summary was read at
`/tmp/skill.cncf.d/cncf-sbt-4eae9f14c660f4e80d53f3ee68c24a6911273072fd0adc1419a13045725dbc45-e413d037813c9bd0a0d75b34f84ca0bc/65229-20261001T020729Z.summary.json`.
The three existing Start-selected managed scenarios now use configuredC rather
than a test-authored decoder/binder callback. Missing catalog, service ports,
repair ports and producer inputs are rejected at assembly construction. Main
configuration/catalog admission and downstream service-port validation remain
unchanged. git diff --check passed before validation.

Next production obligations remain exact: durable host selection, submission
association and closure authorization/decision adapters over the managed shared
provider, canonical default launcher binding, admitted recovery Operations,
actual managed commands/receipts, distribution and independent acceptance review.
No private pool, placeholder authority, production activation, commit or Phase
acceptance is introduced by this bounded assembly entry.
