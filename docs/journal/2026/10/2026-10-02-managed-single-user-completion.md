# Managed single-user completion

Date: 2026-10-02 (Asia/Tokyo)
Status: bounded implementation and focused producer/consumer validation passed;
not Step or Phase acceptance.

## Governing direction and scope

Apply the single-user typed-control policy to the default CNCF managed
completion used by sm-workflow. CNCF Phase 88 is already in this task's mutation
scope. Preserve all unrelated dirty work. Use parent-direct limited recovery;
do not delegate or bypass native permissions or the machine-wide SBT lock.

Frozen source scope in `/Users/asami/src/dev2025/cloud-native-component-framework`:

- `src/main/scala/org/goldenport/cncf/workflow/WorkflowCompletionDataStorePersistence.scala`
- `src/main/scala/org/goldenport/cncf/workflow/ContinuationRecordJsonV1.scala`
- `src/test/scala/org/goldenport/cncf/workflow/WorkflowCompletionDataStorePersistenceSpec.scala`

## Contract

The managed host consumes an Available Continuation directly and records
Completed without a claim token. It must never claim, release, create a fake
token, reconstruct a claimed WorkOrder, or fall back to the explicitly claimed
low-level API. A privately constructed completion selection holds the loaded
source, admitted semantic result and appended history. Mechanical preparation
uses that selection without rerunning its semantic admission after the closing
program.

Keep current Component/collection grants, the same physical DataStore provider,
the existing joint storage/local-effect/selected-event transaction and
authoritative readback. No direct SQLite workflow route is added. Failure before
native commit leaves the stored Available source unchanged. Failure in delivery
after commit does not undo or replay the retained result. Preserve interruption.

The record codec accepts Completed without a token; Claimed still requires a
valid token and Available forbids one. Existing explicitly claimed low-level
APIs remain separate for their callers in this bounded change. Their retained
existence and regressions are not evidence of single-user conformance, and they
are not a default managed adapter or fallback.

## Executable specifications

Seed the managed examples by atomic suspension only, with no claim/release even
in setup. Check Available/None during closing, Completed/None on durable
readback, unchanged storage on invalid semantic/owner/closing inputs and closing
exceptions, interruption, postcommit subscriber failure and no second execution.
Reject a stored Claimed source before interpreting a closing program; no reclaim
or contention scenario is a required single-user behavior.

Run the completion persistence and candidate-admission persistence suites plus
the managed participant/UoW and Component storage suites through the serialized
SBT wrapper. No publish, commit, full Phase closure or production integration is
authorized by a focused green result alone.

## Validation progress

Invocation 749 reached actual terminal SBT/wrapper exit 1 and `lock=released`.
Main compilation succeeded. Test compilation found one new fixture error:
`ContinuationRecordJsonV1.encodeC` returns JSON text, not a Circe `Json` value.
No tests ran. Bounded mechanical repair 1/3 parses that returned text before
altering the malformed-token fixture and renders it back to text. The selected
command and product contract are unchanged; a fresh invocation will rerun the
same five suites.

Evidence: `/tmp/skill.cncf.d/a-b29b2b4e-039a-42de-b470-4a7b97cfc241/33704-20261001T233407Z.summary.json`.

Invocation 750 completed all five selected suites: 68/68 successful, zero
failed/canceled/pending/aborted, SBT/wrapper exit 0 and `lock=released`.
This includes the actual public WorkOrder and Decision completion Operations,
same-provider managed bootstrap/reopen, token-free completion, unchanged source
on pre-native failure and postcommit failure without replay. Explicit legacy
low-level regression examples passed separately; that does not make their
claim protocol conformant. One mechanical repair was used.

Evidence: `/tmp/skill.cncf.d/a-8a4d8b8d-b140-4ae9-a60a-ee389c8c5cb6/34646-20261001T233624Z.summary.json`.

The sm-workflow consumer has not yet been validated against these new producer
bytes. Next derived prerequisite: refresh the existing CNCF development
SNAPSHOT locally with `publishLocal`, then run the existing sm-workflow focused
accumulator. This is dependency-artifact-refresh, not local-warehouse release or
remote publication. Preserve the same coordinate and normal local cache; no
source version update, remote upload, commit, deployment or shared-skill change.
Full repository validation, independent acceptance and Phase closure remain open.

## Refresh and consumer outcome

Invocation 751 completed `publishLocal` at the unchanged CNCF development
coordinate, SBT/wrapper exit 0 and `lock=released`.
Evidence: `/tmp/skill.cncf.d/a-6f7d3af0-60ee-4fc7-9ac8-8c17faf655d0/35582-20261001T233835Z.summary.json`.

Invocation 752 ran `cozyPrepareRuntime`, `packageBin` and the existing focused
sm-workflow accumulator: 381/381 successful, 53 suites completed, zero
failed/canceled/pending/aborted, SBT/wrapper exit 0 and `lock=released`.
The generated runtime classpath selects the actual updated local jar:
`/Users/asami/.ivy2/local/org.goldenport/goldenport-cncf_3/0.5.3-SNAPSHOT/jars/goldenport-cncf_3.jar`.
No consumer source changes were needed. Existing compiler warnings remain.
Evidence: `/tmp/skill.cncf.d/a-f94e9706-3101-4896-b621-e548cd1f72c9/36244-20261001T234004Z.summary.json`.

Parent-direct execution used the retained user recovery direction. The two
execution skills supplied fresh typed permission/allocation, the common SBT
lock, native scoped review and process-derived terminal evidence. No custom
agent, hash-backed intake/receipt adapter, Git mutation, remote publication,
deployment or shared-skill mutation was used.

The default managed WorkOrder/Decision completion now has no claim/release or
claimed-projection calls. This does not convert the separate low-level APIs,
legacy application hash semantics, all other defensive control paths or the
still-unconnected typed partition semantic Operation. Their passing regressions
are not policy conformance or Phase completion. Continue the typed production
partition flow on this default claim-free completion contract, without selecting
or falling back to an explicitly claimed API. Original base/authority admission,
independent Step acceptance, full Phase validation/review and release remain
required.
