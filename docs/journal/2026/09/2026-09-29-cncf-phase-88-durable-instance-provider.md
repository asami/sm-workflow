# CNCF Phase 88 S1: durable instance provider

Date: 2026-09-29
Status: first upstream provider slice validated; Phase 2 / Phase 88 in progress

## Authority and ownership

The user explicitly added CNCF Phase 88 implementation to the sm-workflow task:
"作業に追加して進めて". Source mutation scope is sm-workflow and
`/Users/asami/src/dev2025/cloud-native-component-framework`. The upstream
baseline is HEAD `c4e1b24c134512f848aa11bd42d2664bddbb1396`; it was clean
before this slice. ArtScene remains read-only. Cozy was added only as a
validation dependency to refresh its project-owned runtime classpath; its
existing dirty sources/documentation were preserved.

## Implemented boundary

Added `WorkflowInstanceRecordJsonV1` and
`WorkflowInstanceDataStorePersistence` in CNCF. The unchanged
WorkflowInstancePersistence SPI remains create/load/append. No Operation,
alternate engine, implicit store, dispatcher, public CLI grammar, SQL/JDBC,
management registry, lease implementation or command authority was added.

The strict versioned codec retains full admitted definition provenance,
current facts, immutable history and suspension references. Rehydration selects
a separately admitted exact startup ABI catalog; database metadata never
constructs an executable definition. Unknown schema/fields, altered provenance
and divergent history/current projections reject.

The borrowed provider requires an explicit validated store/policy configuration,
collection, admitted catalog and canonical datastore capability for atomic
Entity CAS plus authoritative readback. Initial Workflow revision 0 maps to
positive detached Entity revision 1 (`cncf_revision`). One conditional write
updates both the version and the entire history/current payload. Concurrent
writers cannot both consume an expected revision. Storage overflow rejects
rather than wrapping.

Canonical property names (`configurationPayload`, `recordPayload`) survive
SQL column normalization. Their private `cncf.workflow-json.v1:` text envelope
prevents canonical SQL structured-value decoding from reinterpreting opaque
JSON, nested ABI fields, arrays and nulls. This is a storage representation,
not a skill/Operation contract or a bespoke SQL backend.

The provider borrows its datastore and execution context; it does not close a
caller-owned resource. Explicit policy identity is preserved and compared, not
executed as a new migration/retention/lease mechanism. Instance persistence
alone is not an atomic instance/issued-work/claim/evidence recovery boundary.

## Executable evidence

Registered `cncf_command_runner`, gpt-5.6-luna / medium, through the serial
wrapper and typed receipt-first route. All invocations reached a terminal
completion with `lock=released`; no concurrent top-level SBT, sandbox bypass,
fallback runner, source edit in SBT, staging or commit.

- 230: first focused validation stopped before compilation because Cozy's
  development runtime classpath was stale.
- 231: validation-only Cozy `cozyExportRuntimeClasspath` succeeded. This was
  build/environment maintenance, not Cozy source mutation or publication.
- 232: compilation exposed a non-public datastore helper reference; the adapter
  was repaired to use public EntityRevisionBinding.
- 233: 20/22 focused tests passed; the two SQLite cases exposed canonical property
  normalization/JSON structured decoding, repaired by the private text envelope.
- 234: focused instance-provider, existing SPI and native CAS suites passed
  (22 tests).
- 235: after adding malformed-storage/missing-catalog no-write guards,
  `sbt --batch "testOnly org.goldenport.cncf.workflow.* org.goldenport.cncf.datastore.*"`
  succeeded: 35 suites, 244 succeeded, 0 failed, 7 canceled, 1 pending.
  Canceled cases are existing opt-in live MySQL acceptance without
  `CNCF_LIVE_MYSQL_TEST=true`; the pending case is an existing
  SqliteDataStoreSpec example. Neither is represented as executed acceptance.
  All seven new provider examples ran successfully.

Final 235 runner: `/root/sbt_receipt_38115f6b31f38997a707`.
Receipt SHA-256:
`d70bbfa03cc0769c4f28684ca5f6899b49b60b1769c3ad0052199d52f881414a`.
Validated complete-tree digest:
`231280bd66aba620990c5636ea27a03d8771154b705d861a5523be36a67f9d8e`.

Receipt:
`/tmp/skill.cncf.d/cncf-command-execution-99cd1c0310730d6930b34e28a14b9e438f4a319e4c80de99292741f029560627-567e38ff7f323296109e6bdb8bc4f0d5/command-execution-sha256-d70bbfa03cc0769c4f28684ca5f6899b49b60b1769c3ad0052199d52f881414a`.

Log:
`/tmp/skill.cncf.d/cncf-sbt-623cf31b2793a75e9d5533349b2edf8c242be9cd0672112b95551c68a2081708-9a789102c27774091ba5c8a86a1893ad/1057-20260929T022749Z.log`.

Validated source SHA-256 identities:

- WorkflowInstanceDataStorePersistence.scala:
  `b339803d0fe2795e277d8dfb4f95cffa88b9442e8c76ea60310fd9bb56c2316b`
- WorkflowInstanceRecordJsonV1.scala:
  `8d45d253219c0512eaa06e0319460d95a94907e0668ea9f0a883f4c790f5c7bc`
- WorkflowInstanceDataStorePersistenceSpec.scala:
  `94585c1315104c0ad2ba106ad5fef9e874dba10b427ae94672d93f6ca142dbe2`

The above receipt belongs to the captured validation tree. Subsequent Phase,
inventory/checklist and journal updates are documentation only; the source
identities above remain unchanged. This is not a commit-acceptance or whole
CNCF full-test claim.

## Remaining work at the S1 checkpoint

The subsequent [S2 bootstrap slice](2026-09-29-cncf-phase-88-instance-bootstrap.md)
closes the configured binding/resource-lease gap below. This S1 record retains
its original validation scope; joint recovery and downstream consumption remain open.

Next: canonical configured Factory/bootstrap binding, datastore ownership and
lifecycle; then exact instance/issued-work/claim/evidence recovery composition.
The provider is not yet installed in the production application assembly or
reflected in sm-workflow's resolved SNAPSHOT. A typed local dependency-artifact
refresh is required before downstream validation consumes changed CNCF bytes.

Normal registered GoalPhase CLI with no server is the first consumer slice,
followed by shared server/MCP, resident-reference refresh and Phase 87 canonical
visibility. No private engine, shadow registry or simulated Git effect is an
acceptable substitute. SQLite resource close/reopen within this provider spec
is not a separate-process launcher restart test.

Public skills remain `sm-goal-phase`, `sm-split-phase`,
`sm-repository-sync`, semantic-result-only and independent of legacy
`cncf-*`. No skill deployment, remote publication, publishLocal, commit or push
was performed. The other-owned SplitPhase definition remains unchanged.

See [Phase 2](../../../phase/phase-2.md),
[checklist](../../../phase/phase-2-executable-specification-checklist.md) and
[entry inventory](../../../notes/phase-2-runtime-entry-inventory.md).
