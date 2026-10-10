# GoalPhase artifact references — existing Phase 5 removal policy

Status: implemented; registered-runner verification pending. This is an
implementation-deviation correction, not a new policy, approval or repair frame.
The remaining supported-path migration and integrated acceptance remain open.

## R1 — Selected address and actual bounded read

CML and the handwritten Reference contain Blob identity and storage reference,
not a required byteSize/digest certificate. Normal generation produces the
changed public value; generated files are not edited. Actual Blob identity and
selected storage address must resolve correctly. Payload access is through the
owning current call and configured store; the actual stream is read at most
limit+1 bytes and oversized or unavailable payloads fail. Native Blob metadata
and external storage contracts are unchanged. Workflow performs no additional
digest calculation, declared-size equality or unchanged-content comparison.

E1: GoalPhaseBlobArtifactSnapshotSpec accepts changed in-bound payloads and
nonmatching legacy metadata, while wrong addresses, absent payloads and actual
size overflow reject. Managed Operation coverage reads real current files after
pool/BlobStore reopen and rejects escaped calls and actual resolution denial.

## R2 — Persisted address, not duplicate scope proof

New v2 records contain schema, catalog address and references. The address
selects actual Workflow/submission/Step/purpose. It does not contain content
digests. New reference JSON omits digest/size; v1 readers ignore obsolete
digest/size and duplicate scope claims, including malformed or omitted values,
without rewriting, renewing, manufacturing authority or rehashing. Unknown
nonlegacy fields, unsupported schema, foreign catalog address and malformed
actual references fail. No source/generator/declaration equality replaces them.

E2: pure codec legacy cases and managed physical SQLite reopen cover the old
record without a digest gate. Changed candidate content at the same scoped
address does not require a separate artifact-catalog certificate.

## R3 — Current scoped input and actual Create outcome

Catalog resolution occurs once through the actual current-call reader. There
is no post-read selection re-resolution or whole-pair equality proof. Initial
retention consumes an existing scoped row instead of comparing it to a retry
input. The managed installer reads the selected actual payload before Create;
an existing row selects its own references. It never replaces a row or grants
write permission. A successful Create is followed by actual readback; malformed
scope or missing/failed readback remains transaction-indeterminate, not success.
Unknown Create outcome is preserved and recovered by observing the same row.

E3: stored selection and applicable readback revision changes are consumed
without equality certification. E4: actual resource refusal prevents Create.
Managed tests preserve actor, original Start, collection ownership, Create
grant, absence, foreign scope and escaped-call rejection, with read counts.

## Remaining work

StepCloseRequest program/planning digests, closure request digests and other
review/candidate/application management contracts still require connected
source/consumer/storage/legacy/test removal. Their existing fixture inputs do
not constitute evidence of whole removal. Actual executor/Workflow completion,
validation/FIX/cost integration and assembled independent read-only review are
open. These focused cases cannot close Phase 5 or its removal checklist.
