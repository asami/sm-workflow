# Managed Blob artifact bytes

## Frozen implementation manifest

IMPLEMENT-BLOB-ARTIFACT-1 continues Phase 2, task
01a0a6d7-7043-73a2-a808-1367fe5fdb1f, parent /root DIRECT under retained
explicit user limited recovery. Previous turn made progress: current-call
snapshot routing passed 147/147 tests, nineteen suites, terminal lock release.
No live command. Model/effort not directly observable, standard; disclosure
precedes edits. Only /Users/asami/src/dev2026/sm-workflow source edits.

Own this journal, new admission/GoalPhaseBlobArtifactSnapshot.scala and test
admission/GoalPhaseBlobArtifactSnapshotSpec.scala, plus existing test
admission/GoalPhaseManagedSuspensionOperationSpec.scala. Prefixes are respectively
src/main/scala/org/simplemodeling/textus/smworkflow and
src/test/scala/org/simplemodeling/textus/smworkflow. Existing public SnapshotPort,
CML/ABI, pure admission/digest, host read policies and provider contracts unchanged.

Source evidence: BlobPayloadSupport.service(component) selects the owning
Subsystem's actual builtin BlobService; its public BlobStore.get accepts managed
storage references. Entity-backed BlobPayloadSupport.readManagedPayload additionally
uses BlobRepository metadata and its current datastore binding. No admitted
cross-component metadata grant/mapping is available here; do not silently read
BlobRepository under the sm-workflow store or claim that metadata admission.

Implement a package-private reader with a trusted current-call ReferencePort.
It supplies a complete pinned program/planning pair (canonical Blob EntityId,
BlobStorageRef, exact size/digest) once per load. This is host-owned managed
reference selection, never request fields, sourceMode inference or a public
upload/registration Operation. Keeping selection current-call permits host
reference refresh after repair, rather than permanently pinning an obsolete
candidate at Factory creation. Durable reference storage/installation remains open.

Verify owning current SmWorkflow core, canonical logical application store and
active lease by the existing ProcedureActionCall application-store binding
before reference lookup/provider access and again before publication. Restore
the prior DataStoreSpace binding on every outcome. Resolve only the actual builtin
Blob service; no private provider, pool, in-memory fallback or factory context.
Validate the entire pair before get. Compare get result ID/ref/size/digest with
the pinned facts, bounded-read actual payload at declared-size + one byte,
close its stream, require exact actual size and SHA-256. Artifact hashes retain
the established non-control candidate-content purpose, not management authority.
Return no partial snapshot/default on any failure. Provider filesystem behavior
remains CNCF's existing trust contract, not SecureDirectoryStream equivalence.

Executable spec authoring: AnyWordSpec/Matchers/GivenWhenThen, adjacent clauses.
Pure tests verify metadata/byte substitutions, null/malformed/unbounded references,
truncation/growth, missing payload file and context-free/null call refusal. A real
managed-dispatch probe uses the Subsystem builtin BlobService port with an actual
LocalBlobStore, creates owned test payload files through its canonical put API,
reopens a fresh store/host, verifies exact bytes and escaped lease refusal, then
modifies one owned payload while retaining metadata and requires rejection.
Reference selection is a fixture host port, not durable master-data proof.

Validate packageBin plus retained nineteen suites and the new suite (twenty),
fresh typed permission/allocation, scoped platform escalation, shared SBT lock,
normal caches and complete terminal summary. Explicit `回避して` exception stays
limited to skill-only old route stops. No commit, push, publication, dependency
refresh, upstream source edit, upload of user data or external cleanup. Required
new metadata policy/schema or generic provider change returns to planning.
Production reference catalog, artifact production and default launcher host
remain open; focused validation does not close Phase 2 or independent acceptance.

## Validation failure and frozen repair

542 compiled/packaged and ran 150 tests: 149 succeeded, one physical Blob
scenario failed at escaped-call refusal (line 516). The captured completed call
returned valid bytes. Both exits 1, lock released; full summary and exact failure
read from /tmp/skill.cncf.d/a-1ab2169e-91b6-4e59-84ca-63fe7f751cdc/47948-20261001T071105Z.summary.json.
Session terminal, ID consumed. No automatic lease-safety claim is valid.

FIX-BLOB-LEASE-1 owns the same source/spec/journal paths, parent DIRECT under
the retained recovery override. Evidence: Subsystem.bindManagedApplicationDataStoreC
uses _with_managed_datastore_lease_c, which may open/release a new binding lease
outside dispatch; binding itself performs no protected store read. Earlier
record readers refuse escaped access because actual provider access verifies
the borrowed view, not because the bind call alone is sufficient. Do not use
binding as an active-call validator, add dummy production SQL reads, access
private lease state by reflection or introduce a new upstream API in this repair.

Require the trusted ReferencePort to supply verifyForCallC(core), with no default
implementation/fallback, in addition to current pair resolution. Concrete host
ports must verify their actual opaque storage lease on every access. Adapter
calls that verifier before selection/provider get and after bounded payload reads
before publication. Binding only selects the application store context; it is
not the verifier. This is an explicit injected host dependency, not proof that
all caller-supplied implementations are authentic. Production durable reference
ports remain unimplemented; default Factory does not select this candidate.

The physical test verifier uses the existing managed-record lease probe solely
inside the test, not a production sentinel read/metadata grant. Keep escaped-call
expectations unchanged. Pure test resolver verification fails closed if invoked.
Re-run the same twenty-suite accumulator with a fresh ID after the repair;
no weakening of positive payload, corruption or negative lease assertions.

disclosure_violation: the repair scope and parent ownership were described
before FIX edits, but the full FIX DIRECT display block was emitted only after
them. Record the ordering violation, not a retrospective valid gate. Ownership
remains unambiguous (/root); no new root or authority. The full corrective display
preceded subsequent validation. Report once at this turn's State End.

543 passed the same 150 tests in twenty suites, both exits zero, lock released;
complete summary read at
/tmp/skill.cncf.d/a-aef19059-f40e-44f3-bef1-588597e3dd60/49916-20261001T071624Z.summary.json.
Session terminal, fresh ID consumed. Extend the same frozen negative lease
obligation with a private test-host verifier refusal after pair selection: even
valid payload bytes cannot be published if the final host verification fails.
No production fault parameter or new authority/policy is introduced.

## Final validation and remaining boundary

544 passed all 150 tests in twenty suites, with zero failed, aborted, canceled,
ignored or pending tests. Compile / packageBin completed; SBT and wrapper exits
were both zero and the shared lock was released. The complete invocation summary
and the new pure/managed Blob scenario log sections were read from
/tmp/skill.cncf.d/a-0d92a35a-5c97-4847-92c0-ab6ef20d0100/50534-20261001T071748Z.summary.json
and its adjacent .log. No live command remains. git diff --check passed.

The executable specs establish bounded actual-byte verification, fresh physical
BlobStore/managed-host reopen, rejection of equal-length payload corruption with
stale metadata, escaped-call refusal under the concrete test lease verifier, and
refusal to publish valid bytes when final host verification fails. They do not
establish production reference authorization for arbitrary injected ports.

The production durable Step/project/role reference catalog, concrete trusted
reference/lease-verifier implementation, artifact materialization and default
Factory/launcher integration remain open. The test-only managed-record probe is
not a production verifier design. No new pool/provider, public API or grant was
introduced, and no commit, push or publication occurred. This focused accumulator
is progress evidence only: Phase 2 and independent acceptance remain incomplete.

## Follow-up investigation: scoped reference selection before catalog storage

The preceding goal turn made implementation/validation progress (544). This
follow-up inspected the current sources before choosing the next dependency.
Owner /root, retained parent-direct limited recovery; only this journal changes.
No source, public contract, configuration declaration or durable schema changes.

Observed sources:

- GoalPhaseCallScopedSnapshot.Port.loadForCallC and
  GoalPhaseBlobArtifactSnapshot.ReferencePort.resolveForCallC receive only a
  Step string and ActionCall.Core. These are current-call routing, not an
  admitted Workflow/submission/candidate selection envelope.
- StepClosureAdmission.StepSnapshot.validateC checks a nonempty Step identity
  and two digests; it does not require project/Phase-wide uniqueness. A
  production catalog keyed only by that string would therefore introduce an
  unestablished global-identity assumption. This is a design risk, not evidence
  that the existing private test reference port has selected a wrong artifact.
- GoalPhaseStepCloseServiceOperation validates the association-derived Handle,
  retained current instance and Awaiting position, then derives the candidate.
  These admitted facts exist above the snapshot reader; parsing transport
  arguments again inside ReferencePort would not carry their validation.
- Review completion and repair follow-up already use current-call wrappers,
  but have different retained result/issued-work paths. Fixing only the initial
  request would leave the normal automatic review/repair route unresolved.
- GoalPhaseStepClosureBoundary.commitPlanC checks actual snapshot bytes against
  the commit request before command dispatch. That content check must remain;
  digest equality alone does not establish reference ownership or admission.
- GoalPhaseHostRecordReadPolicy currently declares only selection, association,
  authorization and decision purposes. None is an artifact catalog grant. Its
  decoder requires all four distinct purpose collections, so adding a fifth
  enum member mechanically would also change existing host configuration
  requirements; that is not a harmless reader-only edit.

This changes the next action: establish an internal admitted snapshot-scope
handoff across request, review completion and repair follow-up before freezing a
catalog codec or its installation path. The scope must come from retained
Workflow/association/issued-work facts, correlate the exact Step and candidate,
and retain the selected reference-pair revision through final verification.
Do not infer scope from a Step label, ambient latest entry, request destination,
authentication sentinel or a permissive injected boolean. These are constraints
for the next design, not a newly accepted ABI/schema or implementation PLAN.

The durable reader will need an explicitly declared artifact purpose and real
call-scoped access to that catalog; an authorization-collection probe remains
test-only. Whether references are immutable per candidate or require a guarded
versioned selection must be resolved against repair materialization before a
writer is activated. Final verification must check the selected facts, not just
lease liveness, so valid bytes cannot be published from a concurrently replaced
pair. No arbitrary installed catalog or dummy SQL read is admitted by this note.

Required subsequent evidence spans two Workflows with the same Step label,
cross-submission/candidate rejection, both automatic completion paths, repaired
reference selection, concurrent selection change, escaped-call rejection and
fresh-host reopen with the same physical Subsystem store. The existing 150
passing tests do not cover this production scope/catalog boundary.

No SBT rerun is appropriate for this evidence-only journal addition. The 544
receipt remains historical validation of its source tree, not proof of the new
design constraints. Phase 2 remains active; no acceptance, commit or publication
is claimed.
