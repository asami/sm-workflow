# Start committed readback

Phase 2 parent-direct limited recovery, bounded implementation. Own
GoalPhaseStartExecution.scala, GoalPhaseStartExecutionSpec.scala and this journal.
Preserve unrelated edits; no public Operation/schema, hash identity, pool,
upstream API, actor authority or record installation change.

Observed root: startC validates create/append return values but publishes WAIT
without separately loading the committed Active record. The planned registration
route requires retained Start provenance; a returned write projection alone is
insufficient. After activation load the same configured identity, require exact
typed equality with the admitted Active projection, and return no WAIT when
missing, divergent or failed. Preserve the existing retry and initial-append
recovery behavior; do not compensate/reset committed state.

Executable authoring gate: new AnyWordSpec behavior uses GivenWhenThen clauses
adjacent to setup, action and expectations. Scenarios cover missing, divergent
and failed readback, then recovery of the same Start without another write.
Validate packageBin and the existing eighteen-suite accumulator through the
shared serial SBT wrapper, then git diff --check. This is not installation,
authenticated actor admission, transaction atomicity, default CLI or Phase
acceptance evidence.

Attempt512 compiled and completed all 136 tests in 18 suites: 134 succeeded,
two rehydration scenarios in GoalPhaseClosureAuthorityExecutionSpec failed.
DefinitionBinding reconstruction contains a fresh runtime definition object;
object equality is not persisted-record equality. Correct only the new readback
comparison to the existing canonical WorkflowInstanceRecordJsonV1 encoding,
which also validates the retained record. Convert failed verification after
activation to transaction-indeterminate rather than claiming nonpublication.
No fixture weakening or source/ABI expansion is needed. Preserve this failed
attempt; rerun the same accumulator under a fresh serial identity.
Summary: `/tmp/skill.cncf.d/cncf-sbt-3598141aa7dc22c1198a2a22712123be75ff6d5a6ddb43be7bc788188e7dcb96-e53b9e088ce3e585869879d76443d83e/17196-20261001T034208Z.summary.json`.
Its terminal marker reports sbt/wrapper exit 1 and lock released.

## Verified result

Attempt `SMWF-START-READBACK-DIRECT-20261001-513` completed packageBin and
136/136 tests in 18 suites, with zero failed/aborted/canceled/ignored/pending.
The full structured summary confirms compilation success, sbt/wrapper exit 0
and lock released. No live SBT session remains. git diff --check passed.

Summary: `/tmp/skill.cncf.d/cncf-sbt-1941e55b32858ea6154207a9a5237faaca4ee73b5f5be6ca45bbc204db02ae8a-5dd02311dd9f51bc255f390e0daa9c53/23094-20261001T034408Z.summary.json`.

The new behavior scenario proves no WAIT on absent/divergent/failed readback
and successful retry of the original committed Start without an extra write.
Existing rehydrated-definition and actual managed SQLite reopen scenarios also
pass the added readback gate. This does not yet provide an independently
authenticated Start provenance/registration receipt or host-record writer.
All production installation, actor binding, controlled command, default
launcher and full Phase assurance/release obligations remain open. No commit
or goal completion was performed.
