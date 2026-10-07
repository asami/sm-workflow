# Storage plan configuration codec

## Frozen Phase 2 implementation boundary

Continue admitted CNCF Phase 88 producer work under parent limited recovery.
No agent spawned; parent runtime profile not directly observable. Owned new
producer paths are subsystem/SubsystemStoragePlanCodec.scala and
subsystem/SubsystemStoragePlanCodecSpec.scala under main/test Scala respectively.
Existing typed plan and declaration constructors were inspected. No existing
catalog, projection, runtime factory, grant, provider or public bootstrap is
changed in this slice. Independent/full Phase gates remain open.

Canonical projection flattens object-valued configuration. Preserve this model:
use one strict ConfigurationValue.StringValue containing a bounded JSON document
for the eventual typed storage-plan parameter, not arbitrary ObjectValue or
ambient ConfigurationAccess coercion. This pure codec is preparatory and is NOT
yet registered or source-admitted; parsing does not make a Component request
trusted host authority.

Closed wire: schema cncf.subsystem-storage.v1; policy {revision, rules} with
rule {eventType, mode}, mode persist/transient; persistence {storeName,
cursorCollection, eventCollection, deliveryIndexCollection, cursorEntryId,
workflowGrants}, grant {componentId, storeName, rootCollection,
continuationCollection}; records array of {componentId, storeName, collection}.
Every field required, including empty records for no explicit record declaration.
No aliases/defaults, legacy schema, unknown or duplicate fields. Collections use
canonical EntityCollectionId.print and owners strict ComponentId.parseC. Existing
policy/declaration bounds, ownership and reserved-collection validators apply.
Wire member order/whitespace is not identity. Maximum 64 KiB UTF-8 and nesting
depth 16; invalid UTF-16 input/BOM reject before parsing. Serialization returns
the complete typed plan data and creates no physical resources/captures.

Executable specs: semantic round trip with records and without; whitespace/key
order acceptance; required/unknown/duplicate/type/schema/enum rejection; bounded
size/depth/array rejection; invalid identities and reserved ownership rejection;
decode/encode leave enclosing construction state unchanged. AnyWordSpec plus
adjacent Given/When/Then, no weakened expectations. Shared serial-wrapper
testOnly new codec spec plus typed plan and public bootstrap regressions;
fresh permission/full ledger, scoped escalation, actual terminal and full summary.
No publishLocal, runtime activation, source admission, commit/push or release.

## Initial focused result and stronger boundary evidence

663 passed 14/14 examples in 3 suites, exits 0/0, lock=released. Full summary
read: /tmp/skill.cncf.d/a-72779b80-192f-4538-8094-556c60b76c61/21522-20261001T154308Z.summary.json

Before acceptance, parent inspection found the oversized fixture was whitespace
alone: failure would also follow from invalid JSON without the size guard.
Strengthen this same owned spec with valid padded JSON at exactly 65536 bytes
and one byte beyond; test that quoted brackets do not count as structural depth.
No product, wire contract, ownership or expectation weakening. Rerun exact
three-suite command on these final test bytes with fresh permission 664.
Independent review and full Phase gates are still open; prior green source
evidence does not substitute for validation of the stronger spec.

## Final focused evidence and next integration

664 passed 15/15 examples in 3 suites with compilation, actual terminal process
exit 0 and matching wrapper exits 0/0 lock=released. Full summary inspected;
zero failures/aborts/canceled/ignored/pending. Producer diff check passed.
/tmp/skill.cncf.d/a-88639784-7cf0-4f89-a32a-932d3b0f8de9/22424-20261001T154517Z.summary.json

The stronger spec proves valid JSON at 65536 UTF-8 bytes is accepted and the
same document one byte larger is refused; brackets inside a quoted revision
do not inflate structural depth. Pure semantic round trip, duplicate/unknown
members, strict types/identities, record reservations and capture isolation
passed. No independent review or full-validation assurance is claimed.

Next integration must register the original typed parameter witness with
Subsystem-instance target restriction, preserve source-bearing runtime
candidates and admit host authority before SystemNode allocation. Component
descriptor defaults projected by CncfDiscoveredAssemblyConfigurationProjection
must not become host grants, even though that projection names a Subsystem
target. Target restriction alone is insufficient provenance admission.
Runtime assembly must consume the admitted plan via the existing construction
scope, not parse a merged configuration or construct a second bootstrap host.
No external config file is written and no storage authority activated here.
Producer SNAPSHOT refresh is pending before downstream tests of these bytes.
All remaining Phase 2 workflow adapters, independent/full review and validation,
original-base gates and exact acceptance/release scope remain unchanged/open.
