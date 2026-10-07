# Workflow storage composition before Component core

Continue admitted CNCF Phase 88 / sm-workflow Phase 2 after producer 433.
Freeze new producer source `workflow/WorkflowComponentStorage.scala` and new
external-package executable spec `org/simplemodeling/cncfclient/WorkflowComponentStorageSpec.scala`.
Existing ComponentCreate, instance binding and managed completion source were
read completely; the new code delegates to those contracts without modifying
ComponentCreate, initialization order or resource ownership.

Public composition input is ComponentCreate plus exact Component, Workflow
configuration/catalog and granted instance/Continuation collections. The binder
uses the owning Subsystem internally, verifies same-store delivery, catalog and
captured grant, and returns an immutable opaque composition dependency. It
exposes only instance, issued-work and managed-completion bindings. It exposes
no Subsystem, DataStoreSpace, provider, connection, pool, lease or claim token.
Binding the composition itself is settings/admission only; instance binding
probes through the existing managed short-lease API, not a private resource.
Codecs, completion Operation and event callback remain explicit per binding;
event policy and resource lifecycle remain host-owned.

Executable spec must use a package outside org.goldenport.cncf. Exercise real
PrimaryComponentFactory.createPrimaryC and bind dependencies inside create_Core,
before the new Component has a core. Persist an initial record using the returned
binding, close/reopen the host and reconstruct the factory dependencies without
reinitializing records. Old bindings fail after owned shutdown. Pure bind rejects
foreign grants, malformed catalog and missing host declaration; no permissive
fallback or config-reading resource acquisition. No actual application service,
launcher acceptance, schema migration or Phase closure is claimed.

## Focused producer evidence (434)

`SMWF-CNCF-COMPONENT-STORAGE-COMPOSITION-DIRECT-20261001-434` passed 57 tests
across five suites on its first attempt; source and test compilation succeeded,
SBT/wrapper exit 0/0 and serial lock released. Producer `git diff --check` passed.
The three new examples ran from an external application package. Actual
PrimaryComponentFactory.createPrimaryC calls the new composition binder inside
create_Core while `comp.coreOption` is empty. It constructs all three typed
dependencies, persists an initial record, and reconstructs the dependencies on
a fresh host to read exactly that record. An old instance binding fails after
owned shutdown. The events callback is not invoked during assembly. Invalid
grants/catalog are rejected; a missing declaration rejects without creating the
settings-only SQLite file. Existing issue/completion/participant/UoW suites pass.

- log: `/tmp/skill.cncf.d/cncf-sbt-5d1f1f9e27657c63a8f5a73226df171f6ff4cc79c350c9ccda85aca354f3f21c-1b4be0903a8135e8262f22677f0feaa8/3594-20260930T220804Z.log`
- summary: `/tmp/skill.cncf.d/cncf-sbt-5d1f1f9e27657c63a8f5a73226df171f6ff4cc79c350c9ccda85aca354f3f21c-1b4be0903a8135e8262f22677f0feaa8/3594-20260930T220804Z.summary.json`

No changes were needed to ComponentCreate or the Component lifecycle. The new
public WorkflowComponentStorage.bindC consumes ComponentCreate as a capability
and accesses its host only inside CNCF. This closes the external factory access
gap without publishing a raw Subsystem accessor. The composition binding owns
no additional resource. This test does not independently measure shared pool
count; the prior canonical host executable evidence remains separate.

Next refresh the admitted local CNCF SNAPSHOT (including 433 and 434), verify
its exact consumer classpath identity, and consume this public composition seam
in the application service assembly. Actual host selection/invocation/closure
authority, production factory configuration and launcher proof still remain.
No Phase checklist or assurance gate is marked complete by these focused tests.
