# Record storage trusted host construction

Continue the same Phase 2 goal under retained parent-direct limited recovery.
Previous turn made implementation and focused verification progress (46 tests),
with no live SBT handle remaining. Full Phase acceptance remains incomplete.

Freeze IMPLEMENT to CNCF's new internal ComponentRecordStorageBinding,
Subsystem's existing record binding/capture group, the existing
SubsystemEventStoreBootstrap companion and new ComponentRecordStorageBootstrapSpec.
Exact admitted source root:
`/Users/asami/src/dev2025/cloud-native-component-framework`. Preserve all other
dirty work; own only this journal in sm-workflow. No source changes in Cozy,
no commit/publication or new initialization lifecycle.

Extract the bounded record admission group out of oversized Subsystem, preserving
its captured declaration, active lease ownership and provider resolution through
host configuration. The helper has five explicit constructor dependencies and
one binding responsibility; no general Subsystem refactoring. Existing public
capability and internal binder signatures/behavior remain unchanged.

Add an explicit seven-argument overload of the existing trusted bootstrap createC,
with a ComponentRecordStorageDeclaration. Retain the six-argument public method
and defaults unchanged, preserving its JVM/source signature. Check reserved
Workflow/EventStore collections before canonical construction; use the existing
private construction scope to capture the declaration. No records, SQL resource,
cursor or pool may be opened merely by this declaration. Ordinary hosts do not
inherit authority after the construction scope ends. Runtime participant
authentication is not supplied by trusted host construction.

Executable-spec authoring gate: AnyWordSpec, Matchers, GivenWhenThen at actual
boundaries. New scenarios prove pure public host construction, scope isolation,
lease-bound fixed identity Create/read and reserved-target refusal before resource
opening. Existing host capability/declaration/EventStore/pool specs must remain
green after extraction. Parent runtime model/effort is not directly observable;
retain the explicit parent-direct recovery owner rather than asserting a worker.
Select focused shared-serial SBT; stop on overlap/unadmitted source or necessary
semantic change. No full-review repair cycle is consumed by this IMPLEMENT pass.
Actual transport registration Operation, actor admission, committed success
readback and Phase acceptance/release remain required.

## Bounded spec correction before acceptance

Invocation 523 completed with compilation success, 50/51 tests passing in six
suites, both exits one and lock released. The full summary was read. Stable
failure `TEST-BOOTSTRAP-PREPARED-POOL-1` is the new bootstrap spec's zero-pool
expectation at line 50, not a declaration-induced extra pool or implementation
regression. Current `SubsystemStorageInitialization.prepareC` already resolves
the captured Workflow/EventStore shared managed resource during canonical
construction, before Component core assembly. SubsystemFactory/GenericSubsystemFactory
use that path. This implements the retained user-directed common-pool ordering.

Freeze a parent-direct bounded FIX to only the new bootstrap spec: compare the
declared host to ordinary canonical construction (one prepared pool), prove the
record is initially absent, and verify the fixed record capability reuses the
prepared pool. An ordinary host's denial must preserve its already-prepared pool,
not assert zero. Preserve reserved-target pre-construction refusal. No production
code, initialization contract, acceptance requirement or source scope changes.
The new declaration must add no resources/writes of its own; canonical baseline
infrastructure preparation is not forbidden. Fresh focused validation is required.

## Focused verification outcome

Invocation `SMWF-RECORD-HOST-CONSTRUCTION-DIRECT-20261001-524` passed all 51
tests in six suites, with no failures or aborted suites. Compilation and both
exits succeeded, lock released; the complete summary was read:
`/tmp/skill.cncf.d/cncf-sbt-dae049a20935e5a0b748d827569dd890d7b6a7aa96bddfd9cfc5507abbf4043b-1f1010109efb3459569bcccd9d5245a4/48717-20261001T043233Z.summary.json`.
The focused command retained declaration, host capability, EventStore, SystemNode
pool and original public bootstrap coverage, plus the new bootstrap spec.

Observed new behavior: canonical host construction already prepares one shared
resource; adding record authority does not add another. Before explicit Create,
the allocated registration identity is absent. Its later load/Create reuses the
one prepared resource. Subsequent ordinary bootstrap has no record grant even
when opening the same physical database. Reserved target collision is rejected
before canonical construction. Public call binding/actual registration transport
remains unverified and is not inferred from internal managed-lease test access.

Subsystem is reduced from 2,749 to 2,730 lines; the internal binding helper is
46 lines with one responsibility. This extracts only the newly introduced group,
not a claim that existing Subsystem source-size debt is resolved or independently
reviewed. Existing six-argument bootstrap createC and defaults remain in source;
the explicit seven-argument overload is additive, not a replacement startup API.
No record or cursor is installed by the new declaration capture.

Next connect the typed registration request/original-Start contract to the public
call-scoped capability through a real Operation, with independent participant
authentication and committed-readback receipt publication. Producer artifact
refresh, downstream integration evidence and independent/full Phase assurance
remain required. No acceptance commit, publication, release or completion claim.
