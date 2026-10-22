# Runtime caller format migration

P116-MIGRATION-01 supplies a finite offline installation procedure for a runtime
whose complete old configuration and retained native evidence predate the caller
format. It preserves the original Goal, Session, failed records, continuations,
history, native receipts and repair counters. It does not issue evidence, decide
Human authority, recover a validation result or advance a workflow.

The implementation is [migrate-runtime-caller-format.py](../../scripts/migrate-runtime-caller-format.py).
Executable examples E1–E13 are in
[test_migrate_runtime_caller_format.py](../../scripts/tests/test_migrate_runtime_caller_format.py).
Qualification results on actual data belong to the owning workflow's retained
evidence; this document does not assert that qualification or live application
has happened.

## Procedure

Use the workstation's installed `uv` and stable uv-managed Python >=3.12. Choose
an absolute canonical existing runtime root and a new absolute output directory
outside that root. The output's parent must already exist. Keep the retained
plan directory private: it contains original configuration, evidence and Human
source bytes.

```sh
uv run --no-cache --managed-python --no-python-downloads --script \
  scripts/migrate-runtime-caller-format.py plan \
  --runtime-root /absolute/cncf-root \
  --output /private/tmp/caller-format-plan \
  --expected-evidence-count 27
```

The count is an operator assertion for this particular incident. Twenty-seven
is not a universal format requirement. A failed prerequisite leaves live files
untouched; an interrupted plan may retain a partial output directory. Choose a
fresh destination for another plan and retain the failed output for inspection.

Inspect `plan.json`, `plan-report.json`, `before-config.json`, `before.sqlite`,
`proposed-config.json` and `fixture/.textus/config.json`. The proposed live config
keeps the original datastore and journal paths. The fixture config points only
to copied databases, and its runtime definition retains the original repository
target/root. Existing `.textus/assembly.yaml` is copied verbatim. Separate
explicit journal SQLite files have retained backups and read-only fixture
copies. The fixture supports an ordinary installed CLI **read-only describe**
against its runtime root. Select the installed CLI's documented describe syntax;
do not run Start, Submit, Advance, recover or any effectful workflow command on
the fixture. Its process program descriptions still refer to the original
repository and are not permission to execute them.

After parent-owned tests, isolated installed reads and independent review, the
runtime owner may explicitly apply the prepared plan. Stop workflow writers
operationally before planning and application; the tool does not inspect or kill
processes, acquire a custom global lock, or assert that a runtime is quiescent.

```sh
uv run --no-cache --managed-python --no-python-downloads --script \
  scripts/migrate-runtime-caller-format.py apply \
  --plan /private/tmp/caller-format-plan/plan.json
```

Retain `apply-report.json` and all backups. The runtime owner can then perform
the installed CLI's ordinary read sequence against the original root to inspect
the existing Session. Applying this format does not authorize a retry, confer a
recorded/accepted workflow result, or replace the owner's separate authority
for any subsequent workflow operation.

## Finite source and path boundary (R1)

The tool accepts only an absolute canonical root, explicit SQLite datastore,
regular nonsymlink configuration/database/import files, and a fresh nonsymlink
output. Existing destination collisions, source inode replacement, copied-plan
relocation and symlink swaps are refused. Generated directories use mode 0700;
generated files use 0600, and retained SQLite backups/journal fixture copies use
0400. The live replacement config uses 0600. It uses standard-library Python,
fixed SQL writes and SQLite online backup, without shell, subprocess, network,
AI, imported execution, or workflow operations. Limits are 8 MiB per JSON/config
input, 512 MiB per database/sidecar and one million rows per table.

Explicit separate journal paths are copied and isolated. Other explicitly
configured Store path/URL/root/file/directory overrides, non-SQLite Store kinds,
and entity-import pointers are prerequisites requiring their owner to resolve
the unsupported layout. This is a finite normal-config migration, not a generic
configuration evaluator or an arbitrary database writer.

## Configuration and evidence mapping (R2)

All fourteen old keys must exist without new-key collisions. Unknown old-family
keys, partial/new configurations and already-projected evidence layouts are
refused by `plan`; they are never silently overwritten.

| Old spelling | New spelling |
| --- | --- |
| `sm-workflow.host.definition` | `sm-workflow.runtime.definition` |
| `sm-workflow.host.evidence.{issuer,record-collection,store-name}` | `sm-workflow.external-evidence.*` |
| `sm-workflow.goal-phase.host-records.{association-collection,authorization-collection,decision-collection,plan-collection,selection-collection,store-name}` | `sm-workflow.goal-phase.input-records.*` |
| `sm-workflow.repository-sync.host-records.{selection-collection,store-name}` | `sm-workflow.repository-sync.input-records.*` |
| `sm-workflow.split-phase.host-records.{selection-collection,store-name}` | `sm-workflow.split-phase.input-records.*` |

Only the embedded definition's `schemaVersion` changes from
`sm-workflow.three-workflow-host.v1` to
`sm-workflow.three-workflow-runtime.v1`. Every target, repository, Goal, authority,
limit and unrelated user setting keeps its original value. There is no global
replacement of strings in values.

The sole evidence table is `host_evidence`, with string `id` and
`nativeHostEvidencePayload`. Every row must have the outer prefix and inner
`schemaVersion` `sm-workflow.native-host-evidence.v7`. Malformed JSON, duplicate
JSON keys, duplicate/invalid IDs, unknown schemas, absent required string fields
and nonmapping bindings are refused. The added TEXT column
`nativeExternalEvidencePayload` retains each same ID and changes only the outer
and inner schema spellings to `sm-workflow.native-external-evidence.v7`.
Original payload bytes, references, issuer, purpose, binding and Human provenance
remain intact. The old column is retained as original evidence, and is not a
fallback readable by the new runtime.

## Isolation and complete retained-state comparison (R3)

Planning uses online backup into `before.sqlite`, copies that snapshot into the
fixture and transforms only the fixture database. Verification compares complete
SQLite schema objects, table columns and typed cell values against the expected
finite projection, and runs `integrity_check`. Tables unrelated to evidence,
indexes, native receipts, instances, continuations, counters and journal rows
must remain identical. Source file/sidecar bytes and identities are checked
before/after planning. No digest, private acceptance token or configurable SQL
statement establishes acceptance.

## One-time StartupImport retirement (R4)

The observed normal JSON shape is a sole `datastore` array containing sections
with `collection` and `records`. The supported collection is exactly
`entity:smworkflow.local.host_evidence`; every record contains exactly `id` and
`nativeHostEvidencePayload`. Each item must already match one retained actual
row and value exactly. Only after proving every item redundant does the tool
remove `textus.import.data.file` from the proposed live and fixture configs.
It retains the actual source file, original bytes in `before-import.json`,
original source path and verified item count in the plan. Unknown containers,
collections, fields or unprovisioned/different records cause an explicit
unresolved-import prerequisite. The tool never imports, replays, converts or
reissues Human evidence.

## Owner application and crash recovery (R5)

Application derives paths and expected changes again from the retained original
config/snapshot, verifies selected-plan location and file identities, and
requires the current configuration to equal either the original bytes or the
exact proposed bytes. Every original database table/schema/cell and separate
journal must still match the plan. Any new business progress, Goal/Session
change, receipt or journal write requires a new plan.

An ordinary SQLite `BEGIN IMMEDIATE` transaction spans complete comparison,
column addition and guarded updates by exact old value and ID. Only the exact
pre-migration database or the complete expected projection is admissible;
partial/foreign projection values are refused. The database commits before the
private prepared config is atomically renamed into place. These two files are
**not one atomic transaction**. A crash after the database commit and before
config replacement is recoverable by applying the same retained plan: the exact
already-installed projection is recognized without duplicating evidence. A
fully applied plan is also idempotent, and an exact private pending config file
can be reused after interruption. A foreign pending file causes refusal.

Reports certify only format installation and unchanged retained cells/journal
at verification time. Ordinary SQLite locking protects the database transaction;
the tool supplies no cross-file or cross-database lock. Owners must avoid
concurrent configuration/journal writers. If a later verification observes a
change, retain the plan and original failure instead of resetting state or
claiming workflow acceptance. There is no automatic rollback that deletes the
new column or restores old business state.

## Executable examples

| Examples | Rules | Observable contract |
| --- | --- | --- |
| E1–E2 | R1–R4 | Isolated 27-row projection, original bytes/authority/limits preserved, redundant pointer retired |
| E3–E6 | R1,R2,R4 | Unprovisioned imports, malformed/unknown/duplicate/mixed state refused |
| E7–E8 | R3,R5 | Exact-once application and interrupted/full application recovery |
| E9–E10 | R3,R5 | Business and journal progress invalidate an old plan |
| E11–E12 | R1,R5 | Explicit paths, cross-root relocation, symlink changes and foreign projections refused |
| E13 | R1,R3 | Unhandled Stores and wrong operator count refused |
