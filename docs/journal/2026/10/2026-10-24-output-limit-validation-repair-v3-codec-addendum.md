# Output-limit repair v3 — proposed merge-repair codec addendum

Status: proposal v2 only, not approved or applied (2026-10-24).
The unapplied v1 candidate is retained privately; v2 preserves the legacy parent null shape.

The original45-path v3 approval and separately pending authored-ABI addendum
remain unchanged. No real Session operation is included.

## Observed blocker

Actual focused01 terminated with SBT/wrapper1 before executing any test.
Normal CML generation had succeeded in compile03. The current compiler rejects
RepositorySyncNativeMergeRepair.scala:56/57 because its local generic derived
Encoder/Decoder of the shared validation Receipt recursively derives the new
optional ProcessExecutionResult and exceeds32 inline expansions.

Actual retained admission:
/private/tmp/sm-output-limit-v3.D91Lt1/focused01-parent-admitted.json

Actual log:
.codex-workflow/disposable/a-79ec3459-3da1-4bb2-b5c5-c2400dbe1c13/30776-20261024T124759Z.log

This local derived codec would also bypass the explicitly approved shared
legacy/new Receipt wire dispatch. Raising -Xmax-inlines or giving arbitrary raw
ProcessExecutionResult derivation is not the prescribed correction.

## Exact additional authored file

src/main/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncNativeMergeRepair.scala

Replace only its two local derived Receipt codecs with references to the
shared parent-compatible companion adapters inside original owned row3:

```scala
private given Encoder[V.Receipt] = V.Receipt.mergeRepairEncoder
private given Decoder[V.Receipt] = V.Receipt.mergeRepairDecoder
```

Concrete unapplied patch:
/private/tmp/sm-output-limit-v3.D91Lt1/merge-repair-codec.candidate.patch

The same candidate source is retained alongside it. git apply --check passed;
this proves applicability only. The actual authored source is unchanged.

The existing derived merge-repair parent writes `sbt:null` when Facts is absent,
while the canonical validation-state Receipt writer omits that field and its
reader rejects present null. Blind forwarding to the canonical reader/writer
would therefore alter historical merge-repair data. V2 instead prescribes two
small typed companion adapters in already owned RepositorySyncNativeMergeValidation:
new tagged observations delegate the existing canonical new codec; legacy
encoding retains the original six fields including nullable sbt; legacy
reading retains Option Facts acceptance of null/absence and other old behavior.
If any schemaVersion is present, only strict tagged decoding is allowed, with
no failed-new-reader legacy fallback. The canonical state/Goal legacy codec
remains unchanged. Add the actual merge-repair parent old-null/raw-tag roundtrip
regression to the already owned NativeTestOnlyValidationReceiptSpec.

The adapter names are internal API only. No new schema, operation, raw format,
public field or effect. Preserve all
merge repair Request/Result types, parent schema/version, admission behavior,
legacy shared Receipt reads and exact v3 tagged writer/reader. Change no
compiler setting and add no generic derivation or compatibility fallback.

## Verification and boundary

If approved, add this one path to the same original repair batch. Ordinary
compile and the already selected receipt/compatibility regressions verify the
correction. Add a focused existing-owned test for the actual merge-repair
parent codec roundtrip only if needed to prevent bypass of shared wire.
Include the actual additional delta in the existing independent focused review
and ordinary publishLocal/isolated installed qualification. No full-test or
review restart; previous failures and successes remain historical evidence.

The separate source ABI proposal remains required for packaging, but permission
for either file does not imply permission for the other. Together they would
extend45 to47 authored paths, with the same v3 semantics and verification owner.

Preserve Goal, Session, accepted results, base, all history/counts and unrelated
changes. No real consumer Start/Submit/Advance/adopt/recover/retry; ownership
remains sm-workflow-dev開発. No commit, push, remote publication or deployment.
