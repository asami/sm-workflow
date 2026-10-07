# Managed artifact catalog read

Phase: 2, active and incomplete. Owner /root; retained parent-direct limited
recovery, model/effort not directly observable, standard policy. Previous goal
turn: progress, catalog codec and focused 548 (155/155).

## Frozen implementation boundary

Before edits the DIRECT display selected new admission/GoalPhaseArtifactReadPolicy.scala
and GoalPhaseArtifactReferenceCatalog.scala, existing GoalPhaseHostRecordReadPolicy.scala,
ManagedHostRecordRead.scala, GoalPhaseBlobArtifactSnapshot.scala, plus existing
GoalPhaseBlobArtifactSnapshotSpec.scala, GoalPhaseManagedSuspensionOperationSpec.scala
and this journal. Source/spec prefixes are src/main/scala and src/test/scala under
org/simplemodeling/textus/smworkflow/admission. Preserve all other dirty work.
No public Operation/CML/ABI, Component parameter declarations, default Factory,
startup, write/installer, provider, pool, upstream source or artifact refresh.
No commit, push or publication; full Phase acceptance remains unproven.

The internal artifact read restriction consumes only the owning Subsystem's
resolved typed configuration: sm-workflow.goal-phase.artifacts.store-name and
reference-collection. Exact canonical logical store/EntityStore collection are
required; missing, coerced, aliased or normalized destinations fail. This is
not a common framework read/write grant. It does not extend the old four-purpose
decoder with a required fifth role. Existing host-record declarations, when
present, must remain complete and disjoint from the artifact collection.
No JVM-property, request destination, live configuration or default-provider
fallback supplies the declaration.

ManagedHostRecordRead retains the same call/owner checks, current application
binding, actual managed provider load and binding restoration. Its two public
internal entry points select their fixed purpose policy; the admission callback
is private and cannot be supplied through transport. Shared resolved-value
lookup is exposed only to admission internals, not as a grant issuer.

The new read-only DataStorePort stores only logical store/collection/byte bound.
Each call derives its exact catalog key from admitted scope, performs actual
current-lease catalog access and decodes the complete typed pair against scope.
Absent row/payload, malformed/cross-scope data and invalid bounds fail closed.
There is no private provider/pool, captured ExecutionContext, installer, Map,
cache, write, sentinel read or missing-data fallback in the production reader.
verifyForCallC now receives Scope and reads that real record; this verifies
storage access, not authenticated consent or a live Workflow revision lock.

The Blob adapter uses scope-aware verification before selection and after
bounded actual-byte checks. Exact selection revalidation is the final catalog
operation before publication. Previously a further lease-only callback followed
selection comparison; with an actual catalog reader that would observe new
selection facts without comparing them. The final order is therefore lease
verification followed by full pair/scope revalidation, which also performs actual
lease-protected storage access. This is an observation point, not a transaction
covering later Workflow/Blob/Git effects or a claim of race-free future commit.

## Executable validation

Class S: Compile / packageBin and the retained twenty-suite accumulator. New
pure policy example covers flat/nested configuration, missing/malformed values,
destination mismatch, overlap with authorization and incomplete legacy policy.
Existing host-record tests remain unchanged to detect accidental fifth-role
requirements or admission regressions.

The new real managed-dispatch test owns one temporary SQLite database and local
Blob root. Its explicitly test-only current-call installer Creates the catalog
and negative rows; it is not a new Operation, production writer, startup hook,
write grant or accepted installation receipt. Production DataStorePort is then
used for all record/lease/pair reads. A fresh host, fresh reader and fresh
LocalBlobStore reopen physical rows and bytes without any installer/reference
fixture Map. The probe stops before Workflow progression and uses an explicitly
fixture-built scope; it does not prove production Start/selection admission.

Missing/wrong-scope/malformed records and missing artifact declaration must
refuse before Blob get. Live selection-revision mismatch must fail under an
otherwise valid current call; escaped calls must fail actual catalog access.
Both calls read actual bounded Blob bytes, and previous corruption/reselection/
closure/repair regressions remain in the accumulator. No source edits while SBT
is live; require fresh permission identity, shared serial lock and terminal
summary with both exits and lock release. Complete Phase validation/review/
original-base/release and commit authority obligations remain open.

Next work: admitted production catalog installation and guarded refresh/version
ownership, artifact materialization, current Workflow guard coupling, default
Factory/launcher/MCP activation and complete Phase acceptance. The reader alone
does not authenticate whoever inserted the persisted payload or grant writes.

## Focused result

549 compiled/packaged and passed all 157 tests in twenty suites, zero failed,
aborted, canceled, ignored or pending. SBT and wrapper exits zero, shared lock
released and session terminal. Complete summary and new pure/physical scenario
log sections were read from
/tmp/skill.cncf.d/a-bdf77a6a-aa73-4a85-884f-8693c37c7ad9/68405-20261001T075813Z.summary.json
and its adjacent log. git diff --check passed.

The physical test installed owned rows exactly once via its explicit test-only
installer, then a new host/reader/store recovered the same catalog selection and
actual Blob bytes without reinstalling. Two successful snapshots caused exactly
four Blob gets. Missing, malformed and wrong-scope records, followed by a host
without the artifact declaration, left that count unchanged. A live current
catalog read rejected a different selected revision; captured completed calls
were also refused. Existing real Blob refresh/corruption/lease assertions and
managed review/repair regression tests passed with the final verification order.

These results prove the concrete reader route and host-resource reopen within
the test's explicit installation/scope fixture. They do not establish production
catalog writing, guarded refresh, authenticated artifact installation, public
Start-to-catalog admission or default launcher/MCP activation. The test's stable
reader-selection delegate is wiring only; production DataStorePort, not a fixture
Map or authorization sentinel, performed all catalog/lease/selection reads.

No private pool/provider was added and the production reader holds no resource
ownership. Common Subsystem teardown/reopen supplied the actual storage lifecycle.
No source edits while SBT was live. No acceptance commit, push, publication,
independent review or Phase release is claimed; the goal remains active.
