# Internal scoped artifact handoff

Phase: 2 (active, incomplete)
Owner: /root; retained user-directed parent-direct limited recovery.
Model/effort: not directly observable; standard policy.

The preceding investigation changed the next dependency from catalog codec
implementation to carrying already validated scope into the artifact reader.
This is the same application boundary, not a new public API, schema, storage
grant, initialization API or provider. Full Phase acceptance remains unproven.

## Frozen implementation boundary and ownership

The pre-edit DIRECT display and commentary selected internal scope routing
across initial closure, review completion and repair follow-up, plus actual Blob
reader consumption and executable specs. Exact owned source programs under
src/main/scala/org/simplemodeling/textus/smworkflow/admission:

- GoalPhaseCallScopedSnapshot.scala
- GoalPhaseBlobArtifactSnapshot.scala
- GoalPhaseStepClosureBoundary.scala
- GoalPhaseClosureAuthority.scala
- GoalPhaseReviewedClosure.scala
- GoalPhaseRepairCandidateBoundary.scala

Owned specifications under src/test/scala/org/simplemodeling/textus/smworkflow/admission:
GoalPhaseBlobArtifactSnapshotSpec.scala and GoalPhaseManagedSuspensionOperationSpec.scala.
This journal is the only new documentation path. Preserve all other dirty work.
No CML/generated ABI, resolved configuration, upstream source or default Factory
activation. No acceptance commit, push, publication or dependency refresh.

Existing ordinary SnapshotPort and legacy current-call Port remain unchanged.
The new private ScopedPort fails closed for Step-only/current-call-only reads.
An ephemeral current-call wrapper preserves ScopePort, then the semantic
boundary binds an explicit Scope before bytes are read. A mismatched Step is
refused before invoking the reader. Scope carries the canonical owning Handle,
typed submission/Step request, derived candidate revision and read purpose.
It is correlation information, never authenticated provenance or a storage
capability; construction/shape validation alone cannot establish admission.

Pre-reviewed and accepted-review closure derive scope from validated retained
instances and typed input. Accepted-review projection and both authority reads
use the same scoped view. Repair candidate preparation uses the retained
issued review Handle and original candidate with RepairSuccessor purpose.
That candidate is a lineage anchor, not an assertion that repaired bytes still
have the old digests. Actual repaired bytes continue to derive the new request
digests; old issued work remains immutable. No rereading raw transport arguments
inside the Blob resolver and no global-Step or ambient-latest lookup.

Blob ReferencePort now receives this Scope rather than a Step string. It still
requires actual host lease verification before lookup and after bytes. The
reader uses the existing owning Subsystem Blob service, bounded actual-byte
checks and restores datastore bindings. No production dummy sentinel read.
Durable reference ownership, selected-pair revision/final revalidation and its
installation contract remain separate production work. Handle does not contain
the execution revision: a durable catalog design must retain its required
version/guard facts, rather than treat this stable identity as a revision lock.

## Executable evidence and validation selection

Class S: packageBin and the retained twenty-suite focused accumulator. New pure
scenarios retain distinct Workflow/submission/candidate scopes for equal Step
labels, distinguish repair lineage, deny null/foreign/malformed scope and
missing current call, and deny mismatched Step before callback. These prove
scope delivery, not physical catalog isolation or reference authorization.

Existing real managed dispatch/reopen specs now consume ScopedPort for accepted
review and pre-reviewed closure, and for all four ordinary/interrupted repair
follow-up cases. Captured scope assertions retain the exact source candidate
and purpose. Escaped views are tested with their full Scope as well as the
unscoped refusal; thus the active lease check is still exercised, not replaced
by an earlier missing-scope error. The physical Blob scenario uses an explicitly
fixture-built scope and actual Blob service/lease verification; it is not a
proof of a production admitted reference catalog.

545 compiled/packaged and completed twenty suites: 151 succeeded, one failed,
zero canceled/ignored/pending/aborted; SBT and wrapper exits 1, lock released.
Complete summary and exact failure were read from
/tmp/skill.cncf.d/a-199ac34e-561c-4319-bc55-3a4461d0e062/58116-20261001T073641Z.summary.json
and its adjacent log. The failed negative spec constructed ComponentId
"foreign.owner", whose namespace has fewer than two segments, before reaching
scope admission. Main implementation and other managed route tests passed;
the new spec was not accepted by this failed run.

FIX-SNAPSHOT-SCOPE-FIXTURE-1: same owner, one exact spec literal changed to
"org.foreign.Owner", preserving a canonical foreign owner and the original
refusal assertion. Full FIX DIRECT display preceded this repair. No product
contract or acceptance change, no live command during edits. Re-run the same
twenty suites with a fresh single-use permission identity, scoped platform
review, normal build caches and the shared SBT lock. No management hash or old
command-route gate is reintroduced. Full review, original-base authority and
release/commit gates remain unfulfilled; focused green results cannot waive them.

## Final focused result

546 passed all 152 tests in twenty suites, zero failed/aborted/canceled/ignored/
pending. Compile / packageBin succeeded; SBT and wrapper exits zero, shared lock
released, session terminal. The full summary and changed spec log sections were
read from
/tmp/skill.cncf.d/a-f8cea45d-85d3-4c72-8261-462b385e4549/58991-20261001T073820Z.summary.json
and its adjacent log. git diff --check passed. The fixture repair did not change
any production source, refusal assertion or selected validation command.

Source inspection confirms all existing semantic snapshot entry points now bind
the admitted candidate/repair scope before invoking a scoped reader. The shared
private commitPlanC consumes that already-bound SnapshotPort and retains its
actual-byte digest check before CommitPort. Existing pure/legacy fixture ports
continue their original contract; they do not become production scoped readers.

Remaining work: durable reference catalog and artifact materialization, explicit
artifact collection ownership/read admission, selected-pair version and guard
verification, concrete trusted host ReferencePort, actual commit-command service,
default Factory/launcher/MCP integration and the complete Phase requirements.
The same-label test proves delivery of distinct scopes to a reader, not isolation
of a not-yet-implemented physical catalog. No green accumulator is a substitute
for production activation, independent review or Phase release. Goal remains
active and no commit, push or publication occurred.
