# SplitPhase managed explicit Start record registration

Phase 2 IMPLEMENT; retained limited parent-direct recovery, no agents.
Previous 723 passed 270 / 38 focused tests after one recorded syntax repair.
Own new SplitPhaseManagedStartRecordRegistration.scala, new registration spec,
the extension of SplitPhaseExplicitStartRecordSpec.scala and this journal only.
Read-only source inventory confirms CNCF ComponentRecordStorage.bindForCallC
requires a current managed lease and independently captured Create grant.

Register complete immutable data only, not a Start or WorkOrder. One canonical
row per independently selected invocation: explicit record identity must equal
the exact selection identity already present in the command. This is a new
registry invariant, not an alias/migration of historical hash-backed records.
It prevents a second instance allocation at another record identity for the
same selection; full record equality also fixes revision/idempotency/context.
This does not infer consent from that identity or from stored origin.

Require the exact authenticated current participant, owning sm-workflow call,
same-store eleven-disjoint host declaration, independent CNCF record Create
grant and fresh selection/source/proposal re-admission before retention. Pure
retention creates only an absent row, accepts exact existing equality and
refuses conflicts without overwrite. Create success is insufficient: readback
must establish exact stored data. Failed/unknown Create or missing/divergent
readback returns transaction-indeterminate; later ordinary retry may recover
the actual row, never compensate/delete or manufacture success.

Executable proof covers pure equality/collision/uncertain retention and actual
managed SQLite Service/CLI registration across pool reopen, missing grant,
foreign actor/expired capsule, exact retry and conflicting instance/revision.
Private fixture installs baseline records once and supplies a configured-local
authenticated actor; neither is real initial human consent. The transport
probe always refuses Start. No instance Create/Append/suspension/issue claimed.

Fresh 724 runs unchanged runtime/JAR/focused accumulator; at most three
converging mechanical repairs. No edits during SBT. No producer/shared skill/
public Operation or payload/factory/pool/provider changes, Git/stage/commit/
push/publish/install or unrelated processes. Full Phase authority/base, review,
validation/release, actual human ingress and production issuer remain open.

## Bounded pre-review repair ledger

724 compiled production/test sources and packaged JAR; 273 / 275 tests passed
in 39 suites. Two new spec failures: retention tested the display message for
a reason stored in structured Descriptor.Facet.Reason, and physical registration
used observed.get after dispatch did not enter the probe. No platform denial.
Tool/SBT/wrapper exit 1 and lock released. Exact summary:
/tmp/skill.cncf.d/a-7cf6daaa-fca9-4528-8e9f-3bae91885ee1/33851-20261001T203546Z.summary.json

Repair 1 of at most 3, orchestrated cncf-test-fix under retained parent-direct
recovery: test the existing structured transaction-indeterminate reason, and
replace the uninformative None.get with a fail reporting actual transport
rejection/transport/mode. Source and all semantic expectations unchanged.
725 reruns the same accumulator to close the predicate defect and localize the
same physical-dispatch failure. Full validation remains at Phase release.

725 passed 274 / 275 tests in 39 suites. Structured uncertainty checks now
pass; diagnostic narrowed the remaining case to ordinary CLI before probe:
argument.missing[textus.subsystem.user-mode (direct Component requires
user.fixed-context-compatible@1)]. Service reached the registration probe.
Tool/SBT/wrapper exit 1, shared lock released. Evidence:
/tmp/skill.cncf.d/a-2890e1be-b957-4302-86f4-de67e96b306d/35334-20261001T203742Z.summary.json

Repair 2 of at most 3: set textus.subsystem.user-mode=standalone in this owned
configured-local registration fixture, matching the existing GoalPhase managed
registration CLI pattern. No authentication/permission/default bypass or
production change. 726 reruns unchanged accumulator; the cause is now specific
and inside the same frozen fixture boundary, not a platform denial.

## Verified evidence

726 passed runtime/JAR preparation and all 275 tests / 39 suites with zero
failures/aborts. Four new pure retention cases and the actual SQLite registry
case passed, including ungranted, initial and reopened hosts through Service
and ordinary CLI. Full summary and new scenario entries were read. Tool/SBT/
wrapper exit 0 and lock released. Two bounded test-only repairs consumed;
production registration/retention bytes remained unchanged after initial freeze.

Missing independent Create grant refused registration and left the row absent.
Configured-local authenticated calls with the exact grant created and observed
the complete record; equal Service/CLI retries and fresh pool reopen recovered
the original data. Different instance, revision, record identity or participant
were refused and exact subsequent reread/retry retained the original row.
Expired capsules refused registration. The canonical instance collection stayed
empty in all modes; fixture baseline installer ran once. No actual initial
human consent or Workflow progression was established by these fixtures.

Pure retention tests confirm one Create only, full-data conflicts without
overwrite, structured transaction-indeterminate after failed/unknown Create
or missing/divergent observation, and ordinary equal retry recovering the
actual retained row after observation resumes. Those observation masks are
pure fault injection, not physical crash/rollback or SQLite uncertainty proof.

Evidence summary:
/tmp/skill.cncf.d/a-6dc1abb8-a89e-4781-9ca4-fe6db50d3f2d/36380-20261001T203915Z.summary.json

Evidence log:
/tmp/skill.cncf.d/a-6dc1abb8-a89e-4781-9ca4-fe6db50d3f2d/36380-20261001T203915Z.log

Whitespace check passed. No stage or commit. Registration is a private
current-call data seam, not a public Operation or standard Factory activation.
Real human Start ingress, explicit semantic context/Continuation/receipt and
canonical issuer/response recovery remain open. Original full Phase validation,
independent review, acceptance commits and release remain pending; goal active.
