# Typed host storage plan extraction

## Frozen implementation slice

Same sm-workflow Phase 2 / admitted CNCF Phase 88 producer. Parent limited
recovery overrides delegation routing only; no acceptance/release claim.
Parent runtime profile is not directly observable. No agent is spawned.

Owned producer paths:
- src/main/scala/org/goldenport/cncf/subsystem/SubsystemStoragePlan.scala (new)
- src/main/scala/org/goldenport/cncf/subsystem/SubsystemEventStoreBootstrap.scala
- src/test/scala/org/goldenport/cncf/subsystem/SubsystemStoragePlanSpec.scala (new)

The existing bootstrap and construction capture helpers were inspected whole.
Extract a package-private immutable plan from the policy, persistence declaration
and optional record declaration. Pure createC rejects missing/null inputs and
reserved-record collisions using the existing validator. A separate trusted
construction scope supplies exactly those declarations and an EventStore with
the existing policy, restoring enclosing captures after normal/exceptional exit.
Reuse this scope in both existing public bootstrap overloads with unchanged ABI,
node ownership, first-install behavior and provider path. No JSON/YAML schema,
configuration authority admission, runtime CLI activation or new grants.

New behavior specs use AnyWordSpec, GivenWhenThen and adjacent clauses: pure
validation leaves construction captures unchanged; null inputs fail; each
reserved infrastructure collection fails; exact construction captures are
restored after an exception. Existing public bootstrap/provider specs check
SQLite first installation, reopen and host isolation. These tests do not prove
CLI provenance admission or full Phase closure.

Focused validation: shared serial SBT wrapper, fresh permission/full consumed-ID
ledger, exact producer testOnly for SubsystemStoragePlanSpec,
example.workflowhost.SubsystemEventStoreBootstrapSpec and
org.goldenport.cncf.datastore.ComponentRecordStorageDeclarationSpec. No
publishLocal is needed until this extraction passes; no commit/push/release.
Producer source writes require scoped native escalation. Preserve all other
dirty files. No private pool, reset, ambient grant lookup or initial data install.

## Focused validation and bounded fixture repair

661 compiled production and test sources, completed 3 suites, passed 10/11,
and terminated exits 1/1 with lock=released. Full summary inspected:
/tmp/skill.cncf.d/a-fa2ce3d8-ea7f-4632-9e9e-672a4a4b0e07/17779-20261001T153343Z.summary.json

Failure STORPLAN-FIXTURE-1 is confined to the new exception-restoration setup:
EntityCollectionId's UniversalId rejects hyphens in encoded subkind. Frozen
orchestrated M1 repair: replace the six outer collection fixture labels with
underscore labels in the already owned new spec only. No production, schema,
authority or expectation changes. Parent limited-recovery execution, actual
runtime profile not directly observable. One byte-changing repair attempt of
at most three pre-review attempts, separate from Phase closure repair counters.
Rerun the exact three-suite command using a fresh permission ID. Full validation
and independent acceptance remain with the original Phase gates.

## Verified result and remaining boundary

662 passed 11/11 examples in 3 suites, production/Test compilation included.
The matching wrapper marker and actual terminal process both reported exits
0/0, lock=released. Full summary inspected; zero failed/aborted/canceled/ignored/
pending. Producer diff check passed. Evidence:
/tmp/skill.cncf.d/a-a0d2e26d-6b7a-4f0f-8aa7-7c6c41e7c8c9/18595-20261001T153514Z.summary.json

The pure plan creates no Subsystem, pool or runtime grant. Its separate trusted
scope now backs both existing public bootstrap overloads; absent optional record
data retains the existing enclosing-capture behavior. Reserved collection checks
and exception restoration passed without weakening expectations. Existing public
SQLite first-install/reopen and ordinary-host isolation regressions also passed.
One M1 fixture repair used; no Phase closure-repair cycle or independent review
is claimed. No new nonblocking Hygiene/Development Candidate was repaired.

This is a reusable internal construction boundary, not canonical CLI activation.
Next: freeze and implement source-bearing Subsystem storage parameter decoding
and host admission before SystemNode allocation, then use this plan's scope
around the existing canonical runtime factory. Do not treat plan construction as
source/actor admission. SNAPSHOT dependency refresh is still needed before any
downstream validation of these new producer bytes. Full Phase validation,
independent review, original-base admission and exact acceptance/release remain
open. No publishLocal, stage, commit, push, release or runtime config change in
this slice.
