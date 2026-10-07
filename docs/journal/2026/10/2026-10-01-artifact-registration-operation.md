# CML and CLI entry for initial GoalPhase artifacts

## Frozen Phase 2 boundary

Continue the active Phase 2 goal under the user-directed parent-direct limited
recovery. Previous turn made progress: managed initial registration and its real
SQLite/Blob/reopen scenario passed 553 (167 tests/22 suites). Current source
was inspected before connecting the entry. No new initialization API, private
pool, external mutation, commit or Phase acceptance is admitted.

Owned paths: src/main/cozy/sm-workflow.cml; admission's
GoalPhaseArtifactRegistrationServiceOperation.scala,
GoalPhaseArtifactReferenceRecord.scala, GoalPhaseArtifactReadPolicy.scala and
GoalPhaseManagedComponentFactory.scala; the existing
GoalPhaseArtifactCatalogRetentionSpec.scala and
GoalPhaseManagedSuspensionOperationSpec.scala; this journal. All mutations stay
under the admitted sm-workflow root. Generated target outputs are build-owned.
Other dirty/untracked work is preserved. Parent /root directly implements in
standard active-interface mode; actual model/effort are not directly observable.
No subagents or skill mutations. Retain shared SBT serialization and scoped
platform review. No source edits during live SBT.

## Operation contract and affected consumers

CML declares registerGoalPhaseArtifacts on GoalPhaseService with typed
GoalPhaseArtifactRegistrationCommand/Result and explicit Blob-reference,
selection and receipt value types. Long-valued byte sizes/revisions are not
stringified. This is an application registration Operation outside the four
GoalPhase progression operations, not a new Workflow engine/action selector.

Closed JSON input contains schemaVersion, subject (the existing typed
StepCloseRequest) and selection (a complete versioned Blob-reference pair).
The caller supplies no handle, purpose, collection, owner, maximum size,
authorization, receipt or refresh intent. These are not artifact uploads or
filesystem paths: BlobStorageRef is checked by the owning managed Blob service
and existing bounded actual-byte verifier. Scope is always Candidate, derived
from the real stored submission handle; request content digests must match the
selected pair. Factory-owned maximum size bounds decoding and actual reads.

The operation invokes exactly the previously validated managed writer: actual
submission/Start/actor admission, exact opaque Create grant, current lease,
real bytes, immutable retention and committed readback. Its returned receipt
derives the Workflow identity/source candidate and uses the decoded retained
pair. It asserts initial catalog retention only, not current Workflow revision,
Step closure, human approval, successful commands or mutable reference refresh.

Shared pair JSON projection/decoding is package-private value code, not storage
authority or a new persisted format. Existing storage bytes/schema are unchanged.
The artifact declaration decoder reuses the same exact resolved read policy;
it adds no grant, provider or configuration side effect.

Configured host-record Factory composition includes the entry only when the
host explicitly supplies initialArtifactMaximumBytes. Existing hosts remain
unchanged with None. Invalid bounds are refused; opt-in requires the exact
artifact destination declaration, same logical Workflow store and collections
outside association/instance/Continuation purposes. Ordinary reopen installs
no data. The generated default Factory and MCP readiness remain unactivated.

## Executable specification selection

Pure command examples reject extra control/storage/receipt fields, invalid
schemas/shapes, lossy numeric revision, mismatched content and caller-independent
size violations. Generated CML metadata and typed nested value construction
are compiled and verified.

The existing physical public registration scenario now declares distinct
submission/artifact Create grants, stores real local payloads and opts in through
the production configured Factory. Only its original human-selection installer
is test-only; neither submission nor artifact registration uses an installer.
It performs initial artifact Create and exact retry through normal Subsystem
dispatch, then uses CncfRuntime's standard selector/--json and actual configured
local ingress without injected CLI actor context or a server process. Service
and CLI must return identical stored receipts. A fresh host/BlobStore repeats
both against the same physical database/root; history remains original Start.
Anonymous requests, injected receipt, changed selection and absent Create grants
must refuse without further Blob reads. Existing registration races/reopen and
managed review/repair regressions remain unchanged in meaning.

Class S validation: Compile / packageBin and the 22-suite accumulator from 553,
under fresh identity 554. Require actual generation/compilation and terminal
summary, both exit codes and shared-lock release. This is proportional validation,
not full Phase acceptance or a newly current full-suite receipt.

## Remaining work

Default launcher/server/MCP activation and public human selection remain open.
The explicit test selection source is not authenticated human-consent proof.
Artifact production/managed upload and guarded refresh coupled to the exact
Workflow revision remain separate obligations. Skill semantic responses still
do not select operations, maintain SQL state or perform an advance loop.
No production integration, independent review, release or acceptance is inferred
solely from the initial-registration transport proof.

## Focused result

554 actually regenerated 74 Scala sources through the configured Cozy backend,
compiled the generated/new/affected production sources and both affected test
sources, packaged, then passed all 169 tests in 22 suites. Zero failures,
aborted, canceled, ignored or pending. SBT/wrapper exits 0/0, shared lock released
and session terminal. The complete summary and physical/pure operation examples
were read from
/tmp/skill.cncf.d/a-eef9b6f7-81ca-48c3-ad7b-65ff8a5998c9/85876-20261001T083023Z.summary.json
and its adjacent log. git diff --check passed. No source edits during live SBT.

The production configured Factory exposed the CML entry only under its explicit
bounded opt-in. Initial Service registration and exact retry used four actual
Blob gets, normal CLI retry used two more, and fresh host Service/CLI recovery
used four more (ten total). Injected receipt, anonymous request, changed
selection and a read-declared but no-Create-grant host left the count unchanged.
Both transports returned the exact same derived/stored artifact receipt; neither
advanced original Start history. Existing registration race/reopen behavior and
review/repair regressions passed. No catalog fixture installer was used in this
public operation scenario; the original human selection remains test-installed.

The generated default Factory, MCP and standalone external launcher deployment
remain unactivated; this evidence uses CncfRuntime's real CLI dispatch in the
test's configured host, without a server process. It is not an independent
OS-process CLI deployment/restart test or proof of public human attribution.
Guarded refresh/Workflow coupling, independent review and complete Phase release
remain open. No acceptance commit, push or publication is claimed.
