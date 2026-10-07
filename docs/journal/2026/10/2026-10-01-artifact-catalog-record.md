# Artifact catalog record contract candidate

Phase: 2, active and incomplete. Owner /root; retained parent-direct limited
recovery. Model/effort not directly observable; standard policy. Previous goal
turn made progress: reference-pair final verification, 547: 153/153.

## Frozen boundary

Before edits the DIRECT display selected new admission/GoalPhaseArtifactReferenceRecord.scala,
existing test admission/GoalPhaseBlobArtifactSnapshotSpec.scala and this journal.
Source prefixes are src/main/scala/org/simplemodeling/textus/smworkflow and
src/test/scala/org/simplemodeling/textus/smworkflow. No other source, public
Operation/CML/ABI, configuration, startup, provider or pool changes. No write,
upsert, initial-record installer, default Factory activation, external repository
mutation, dependency refresh, commit, push or publication. Preserve other work.

The codec is an internal candidate for a durable selection payload, not proof
that a catalog exists or that these bytes were authoritatively installed.
Closed schema/prefixed storage carries catalogKey, exact scope and complete
selection pair. Scope retains owning Component/Workflow definition revision/
instance/protocol, submission, Step, purpose and original candidate content facts.
No review judgment or human authorization is created by encoding or decoding.
Review evidence is not catalog identity: automatic judgment adds evidence for
unchanged bytes without changing the selected artifact target.

The logical address is an explicit composite of owning Component, Workflow
definition/revision, instance, submission, Step and read purpose, using UTF-8
length-prefixed field encoding for unambiguous separation. It is not a framework
EntityId, opaque grant, allocated random identity or freshness token. Candidate
digests are compared in the payload, not used to derive the address or control
identity. RepairSuccessor retains the old candidate lineage in scope while the
pair may identify new bytes. The codec does not update either scope or pair.

Each selection retains its host-allocated identity/revision and complete program
and planning Blob IDs, storage references (including optional version/etag), exact
size and content digest. Decode requires a current caller-supplied validated
scope and rejects semantic mismatch, unknown/missing fields, old schema, invalid
canonical Blob ID, numeric coercion, invalid optional facts and unbounded members.
JSON formatting/key order is not identity. Actual byte verification remains in
the Blob adapter; this codec is neither a digest signature nor provenance proof.

## Required validation and next production boundary

Class S: Compile / packageBin and the retained twenty-suite accumulator, adding
two closed-codec scenarios to the Blob spec. Positive roundtrip retains all
provider facts. Negative cases cover foreign Workflow/revision/submission/Step/
purpose/candidate, absent fields, schema/key substitution, unknown approval,
numeric coercion/fraction, invalid Blob ID, bounds, missing optional field,
invalid optional value and traversal key. Content change retains the address
but invalidates the old scope match; pretty JSON retains semantic meaning.

These pure examples do not establish physical durability, scope authorization,
duplicate-key JSON hardening, a generic catalog query or an installation receipt.
Next implement an explicitly declared artifact collection reader under the real
current managed lease. Initial lease verification must read the actual scoped
catalog, not an authorization sentinel. Required writer/refresh behavior must
remain separate from startup and be admitted through managed Create/guarded
update contracts; never use unguarded save/upsert or fabricate a success receipt.
The existing ReferencePort's lease-only callback lacks a Scope argument, so that
internal handoff must carry scope before a concrete reader can verify its real
catalog access without a side channel. Full Phase review/release remain open.

## Focused result

548 compiled/packaged and passed all 155 tests in twenty suites, zero failed,
aborted, canceled, ignored or pending. SBT and wrapper exits zero, shared lock
released and session terminal. The complete summary and new codec scenario log
sections were read from
/tmp/skill.cncf.d/a-70477de3-c198-4f83-aefd-2e32a01f237f/64645-20261001T074931Z.summary.json
and its adjacent log. git diff --check passed.

Canonical Blob IDs and optional provider version/etag survived the closed
roundtrip. Foreign target/purpose/source candidate and all authored malformed
examples were refused. Candidate content changes retained the same explicit
catalog address while refusing the old payload's source scope. Neither codec
nor tests performed production installation or enabled a catalog reader.

Next boundary remains actual declared-collection managed reads with scope-aware
lease verification, committed catalog installation/recovery and reference-pair
revalidation, followed by artifact production and default host integration.
The codec supplies no write grant, human consent, initialization, commit receipt
or production durability claim. No source edits occurred during live SBT; no
acceptance commit, push or publication. Phase 2 remains active and incomplete.
