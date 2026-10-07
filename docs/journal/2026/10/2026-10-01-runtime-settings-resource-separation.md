# Runtime settings and physical storage separation

## Scope and evidence

Continue sm-workflow Phase 2 under the retained CNCF Phase 88 admission and
human-authorized parent-direct recovery. No new repository, Phase, publication,
commit, or acceptance authority is added. Existing dirty work is preserved.

Invocation `SMWF-CNCF-HOST-OWNERSHIP-DIRECT-20261001-413` completed with five
executed tests in two suites, no failures/pending/aborts, SBT/wrapper exit 0/0,
and the shared lock released. Its summary was reread before the next invocation.
The owned host releases its binding and closes its registered managed resource;
an inherited SystemNode remains running until its runtime owner closes it.
This does not prove closure of unregistered caller-owned stores.

The remaining pool imbalance comes from settings consumers calling
`RuntimeConfig.from`: the default Subsystem factory, Blob factory and Metrics
factory each create a caller-owned DataStoreSpace while only using settings.
The physical resource is not registered with that host's SystemNode, so its
normal shutdown cannot close it. The managed Workflow cursor store is separate.

## Frozen first repair frontier

Owned CNCF source paths:

- `src/main/scala/org/goldenport/cncf/config/RuntimeConfig.scala`
- `src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala`
- `src/test/scala/org/goldenport/cncf/config/RuntimeSettingsSpec.scala`

Introduce package-scoped typed RuntimeConfig.Settings, parsed by the same
configuration/alias/validation implementation as ordinary RuntimeConfig.
Settings have no DataStoreSpace or EntityStoreSpace. Settings-only decoding
neither acquires stores nor updates global execution-history settings.
Ordinary RuntimeConfig construction validates settings first, then acquires
its existing caller-owned spaces and performs its execution-history update.
No memory fallback, shadow provider, request-controlled storage authority, or
additional resource registry is introduced. Switch the default Subsystem
factory to settings-only decoding without changing repository-bootstrap routing.

Executable spec must check repeated SQLite-configured settings decoding leaves
the database absent, aliases/mode overrides survive, common invalid settings
fail before physical acquisition, and production debug-auth rejection remains.
Existing RuntimeConfig and public host/ownership specs provide regressions.

## First validation and next repair frontier

Invocation `SMWF-CNCF-RUNTIME-SETTINGS-DIRECT-20261001-414` ran:

```text
--batch
testOnly org.goldenport.cncf.config.RuntimeSettingsSpec org.goldenport.cncf.config.RuntimeConfigSpec org.goldenport.cncf.config.RuntimeOperationModeConsumerProjectionSpec org.goldenport.cncf.subsystem.SubsystemEventStoreOwnershipSpec example.workflowhost.SubsystemEventStoreBootstrapSpec
```

All 49 executed tests in five suites passed; zero failures, canceled, pending
or aborted; SBT/wrapper 0/0 and lock released. Both terminal marker and matching
JSON summary were read. The configured-host log still shows extra pools, as
expected before correcting Blob and Metrics. This is focused validation only.

The next same-cause repair adds
`src/main/scala/org/goldenport/cncf/component/builtin/metrics/MetricsComponent.scala`
to the exact CNCF frontier. Its factory reads Settings and passes only
OpenTelemetryExportConfig to the service; the existing package-scoped
RuntimeConfig constructor remains a compatibility projection. Operation mode
continues to come from the deferred admitted security-policy supplier at export
time, not settings captured at construction. No transport or storage authority
is added. The existing consumer projection spec is retained unchanged.

## Metrics validation

Invocation `SMWF-CNCF-METRICS-SETTINGS-DIRECT-20261001-415` ran:

```text
--batch
testOnly org.goldenport.cncf.config.RuntimeSettingsSpec org.goldenport.cncf.config.RuntimeOperationModeConsumerProjectionSpec org.goldenport.cncf.subsystem.SubsystemEventStoreOwnershipSpec example.workflowhost.SubsystemEventStoreBootstrapSpec org.goldenport.cncf.workflow.WorkflowCompletionDataStorePersistenceSpec
```

All 47 executed tests in five suites passed; zero failures, canceled, pending
or aborted; SBT/wrapper 0/0 and lock released. Terminal session 95663 exited 0,
and its matching summary was read. No SBT remains owned by this turn.
The complete log shows 109 pool starts and 66 completed shutdowns. This is not
full closure: configured canonical hosts still acquire one unregistered
settings-only pool through Blob. Different suite sets mean these aggregate
counts must not be compared as an identical-workload performance measurement.

Evidence:

- First summary: `/tmp/skill.cncf.d/cncf-sbt-0edefdf5b3a9b7921dcafd713710f0eb2cbb2a522e97b5bdac0cbfeed27b919c-14b74aabd8cea8055e7d601c50b3393c/53740-20260930T204037Z.summary.json`
- Metrics summary: `/tmp/skill.cncf.d/cncf-sbt-1fc79ddda447b9af9d20ca22289a799c7493650469e3b7069cc152fb99e8da0c-420fd6d8841a051df997b2efd2d613d4/55030-20260930T204221Z.summary.json`
- Metrics full log: same directory, `55030-20260930T204221Z.log`.

Validated source SHA-256:

| CNCF target program | SHA-256 |
| --- | --- |
| RuntimeConfig.scala | `07cf429c6fdc55b54e919c3a478cb212d89e2c20c53ae14097b64694e0d5ce32` |
| SubsystemFactory.scala | `3f98fbae1ce4a4d861469a4d89b733efcf08a98e189b65c58db78b07365eaf4c` |
| MetricsComponent.scala | `2619f3ab7d6713f745599ea1dfed2ae3fa031857dedacee248cc0f4a5164c33b` |
| RuntimeSettingsSpec.scala | `48649dabf03e560f3b44b54bf0dd417b487e37fa66f697eba34a26d9220463c4` |

Existing modified primary sources were read completely before their edits;
new executable spec was authored/read as a complete program. No unrelated
whole-file cleanup was admitted. Source diff whitespace check succeeded.
No independent review, acceptance commit, release or producer refresh occurred.

## Remaining work

Blob still uses RuntimeConfig.from and must be switched to the typed settings
path. Metrics is corrected and its deferred admitted operation-mode projection
passed the unchanged consumer projection spec.
Generic Subsystem settings consumers need a bounded follow-on correction too.
Do not report the resource leak fully resolved before those consumers and the
host-level start/stop evidence are verified. The application generated Operation,
launcher integration, dependency refresh, and full Phase assurance remain open.
