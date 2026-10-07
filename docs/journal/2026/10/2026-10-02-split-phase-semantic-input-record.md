# SplitPhase semantic baseline input record

Phase 2 IMPLEMENT, retained limited parent-direct recovery. Previous turn
produced source evidence and the recovery contract; goal remains active with
original scope, review/base/commit/release obligations unchanged.

## Frozen implementation boundary

Own new SplitPhaseSemanticInputRecordJsonV1.scala,
SplitPhaseHostSemanticInputReadPolicy.scala, SplitPhaseSemanticInputDataStorePort.scala,
the existing ManagedHostRecordRead.scala read seam, and new
SplitPhaseSemanticInputRecordSpec.scala, plus this journal.

Implement a closed versioned data-only baseline record with explicit record
reference/revision and Continuation reference, complete assessment, inventory,
draft, baseline validation, boundary and frozen request. Never decode Intent or
Admitted. Reconstruct Intent through ordinary existing semantic preparation
only after exact full current facts match the stored baseline. Add a declared
same-store tenth disjoint collection and a read-only current-call adapter.
Context-free reads refuse. Host declaration does not grant provider access.
No new public semantic payload or operation grammar, issuer, writer or producer
API. Existing fixture hash calculations are not promoted to production.

Executable specs cover projection round-trip, recursive extra/missing fields,
unsupported record revision/schema/reference, complete baseline drift even when
its request digest is unchanged, forged semantic admission, reconstruction and
current-call/policy refusals. This unit does not prove real SQLite intent read,
full Retained recovery, publication, or standard Factory activation. Next unit
must prove physical read/reopen and connect the remaining provenance fields.

Use the unchanged runtime/JAR/focused SplitPhase accumulator through the shared
SBT wrapper with fresh invocation 711. At most three converging pre-review
mechanical repairs; none initially. No edit while SBT is live. Parent actual
profile unobservable, standard mode; no agents, grants, acceptance or Git
mutation. Native sandbox review and shared serialization remain intact.

## Validation and bounded repairs

711 terminated with compile failure before tests: automatic Circe derivation
through InputRecord/Facts/BaselineValidation could not derive the nested
calibration Request vector encoder/decoder. Tool/SBT/wrapper exits were 1 and
lock=released. Full summary and diagnostics were read. No runtime claim.
Repair 1 freezes only named BaselineValidation encoder/decoder derivation at
that existing data type boundary, preventing repeated nested inline derivation;
keep the data schema, exact recursive checks and behavior unchanged. Revalidate
the same accumulator with fresh invocation 712. Two repair slots remain.

712 compiled production code and built runtime/JAR successfully, then test
compilation refused the fixture field named pending: it collides with ScalaTest's
PendingStatement member, causing seven derived type errors. No tests ran;
tool/SBT/wrapper exits 1, lock=released. Repair 2 is only the test fixture field
rename to pendingEvidence and its references. No semantic/source/schema change;
revalidate unchanged accumulator with fresh invocation 713. One slot remains.

713 completed cozyPrepareRuntime, packageBin and the unchanged focused
accumulator: 256 successful tests / 36 completed suites, no failures/aborts.
Tool/SBT/wrapper exits all 0, terminal lock=released. Read full success summary
and all five new scenario entries. Two mechanical repair slots were used;
neither changed semantic admission, data schema or weakened assertions.

The executable evidence proves complete projection round-trip and admitted
reconstruction, full typed drift refusal independently of stored request hashes,
recursive extra/missing-field/private-admission refusal, exact tenth-collection
composition and context-free/null-call refusal. This does not prove positive
managed reads, current-call reconstruction against real SQLite, authority grant
revocation, full Retained recovery or production issuing. Next work must add
physical provider/Operation read and reopen evidence before claiming that path.

Summary:
/tmp/skill.cncf.d/a-846728cc-c972-4bb0-b2d7-8bfe9a59df81/10433-20261001T193449Z.summary.json

Log:
/tmp/skill.cncf.d/a-846728cc-c972-4bb0-b2d7-8bfe9a59df81/10433-20261001T193449Z.log

Whitespace check passed. Goal stays active with original Phase requirements;
this is implementation progress, not acceptance or a blocker audit.
