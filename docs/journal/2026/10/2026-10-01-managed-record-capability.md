# Managed Create-only component record capability

Continue existing Phase 2 under retained parent-direct limited recovery; previous
turn was implementation/validation progress (42 tests). No new goal, subagent,
commit, publication or initialization API. Freeze this pass to CNCF's new
ComponentRecordStorage, new ComponentRecordStorageConstruction, Subsystem's
captured declaration and narrow binding method, and new same-package
ComponentRecordStorageHostSpec. Preserve all other dirty work.

The exact admitted CNCF source root remains
`/Users/asami/src/dev2025/cloud-native-component-framework`. This journal is
owned in sm-workflow. Existing Subsystem responsibility/initialization order is
preserved: capture pure declaration once, no record installation on construction.
Runtime capability requires exact captured owner/store/collection, excludes
captured EventStore/Workflow targets, canonical allocated StringEntryId, an
already-active lease and the issuing thread. Resolve solely from host configuration
through the existing managed binding; accept only a managed SQL provider. The
private dedicated DataStoreSpace routes only that admitted provider. Expose only
fixed-identity load/Create; no provider, save, update, delete or lease escapes.
Recheck the same active lease/thread before every effect. Actor authentication
and application request/provenance validation remain separate and required.

Parent runtime model/effort is not directly observable; execution ownership uses
the retained user-directed parent-direct recovery, not a synthetic worker.
IMPLEMENT is a bounded captured-storage/lease integration inside the admitted
root. Executable-spec gate: AnyWordSpec, Matchers, GivenWhenThen next to setup,
action and expectations. Prove no-grant/outside-lease rejection without resource
opening, exact-scope rejection, immutable duplicate/no-reset, SQLite reopen and
escaped-capability/thread rejection. Focused serial SBT includes the new spec,
declaration spec and existing EventStore/pool regression specs. Stop on unexpected
overlap, unadmitted root or scope change; do not consume a review repair cycle.
Public trusted bootstrap capture, actual registration Operation dispatch,
committed-readback publication, actor admission and full Phase closure remain
required. This internal construction seam alone is not production activation.

## Implementation and focused evidence

The captured declaration and fixed-identity capability are implemented. Binding
requires the host snapshot, an already-active lease and exact declared tuple;
it never opens a replacement dispatch lease. A dedicated private DataStoreSpace
contains only the admitted managed SQL view. Every load/Create verifies the
issuing thread and same still-active lease. No raw provider, mutation selector
or connection ownership is exposed. Payloads may not override the physical ID.

Invocation `SMWF-MANAGED-RECORD-DIRECT-20261001-521` compiled and passed 45 tests
in four suites, both exits zero and lock released. Its full summary was read.
However, an intended spec-only patch failed because its hunks were not in source
order, and the orchestration proceeded to validation instead of stopping on that
edit failure. Record this as an executable-spec authoring/validation ordering
violation, not as accepted final evidence. No source was edited during the live
invocation. After terminal completion, the same owned spec patch was reordered,
applied successfully, and its action/expectation clauses were checked before
fresh validation. The orchestration now checks patch exit before proceeding.

Invocation `SMWF-MANAGED-RECORD-DIRECT-20261001-522` passed 46 tests in four
suites: ComponentRecordStorageHostSpec, ComponentRecordStorageDeclarationSpec,
EventStorePersistenceHostSpec and SystemNodeDataStorePoolRuntimeSpec. All tests
succeeded, none aborted; compile and both exits succeeded, lock released. The
complete summary was read:
`/tmp/skill.cncf.d/cncf-sbt-187525e8e1998a4420b67080599886cfd68bdb9c9cb58b3772e1bd941c8f315b-9a1ee30f2a29188ee6c31f52aa7625bb/45225-20261001T042531Z.summary.json`.

Four new host scenarios prove reserved-target rejection before resolution;
absence of grants/active lease with no resource opening; Create/duplicate,
SQLite reopen and escaped thread/lease rejection; and exact scope rejection.
These use the framework's internal managed-lease test seam. They do not prove
the public Operation adapter, simultaneous Create recovery, actor admission or
the final registration receipt's committed readback. Existing EventStore/pool
tests remain regression evidence, not registration/Workflow physical-sharing
integration proof. No source publication was selected or performed.

## Remaining integration and source-size disposition

Measured Subsystem.scala is now 2,749 lines, above the shared source-size
threshold; the new capability is 46 lines and host spec 142. Subsystem already
had multiple runtime responsibilities, and this pass adds a record-admission
group. Do not declare that growth reviewed/accepted merely because tests pass.
The next implementation frontier must evaluate extracting that bounded group
into a dedicated internal binding helper while retaining Subsystem capture,
active-lease ownership and exact behavior. Any such extraction needs its own
owned paths and regression evidence; unrelated Subsystem refactoring remains
out of scope. No correctness finding is downgraded to Hygiene here.

Then expose trusted host construction capture through the existing bootstrap
path and exercise ComponentRecordStorage.bindForCallC through actual registration
Operation dispatch using the same pool as Workflow/EventStore. Authentication,
typed original-Start/request admission, duplicate outcome classification and
committed readback remain mandatory before success publication. Phase 2 stays
active; no acceptance commit, release or goal-completion claim.
