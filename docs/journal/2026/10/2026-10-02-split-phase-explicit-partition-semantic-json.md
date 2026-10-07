# Closed explicit partition semantic payloads in common Workflow JSON

## Frozen implementation unit

Phase 2 IMPLEMENT, retained user-authorized limited parent-direct recovery;
no agents, parent profile unobservable, standard mode. Previous 745: 365 tests
/ 50 suites passed; progress. Own only new SplitPhaseExplicitPartitionSemanticJson,
its Spec and this journal. Preserve all existing source/APIs/shared fixtures,
unrelated dirty work, prior repair ledgers, shared skills and upstream.

Use separate request/result payload type identities and exact v1 schemas, with
full nested shape closure by typed decode and canonical JSON comparison. Return
only Request/Judgment data, never Prepared/Admitted/gate/lease/claim. Preserve
the exact explicit references, ordered vectors, semantic range and rationale;
do not infer authority or perform semantic admission during decoding. Invalid
numerical/coverage data may decode structurally and must fail its owning result
constructor. No legacy hash schema, alias or decoder fallback.

Implement PayloadCodec for the actual CNCF WorkflowWorkOrderJsonV1 and
WorkflowResultJsonV1 envelopes. Checked encoding reports malformed/oversized
application data before using the common encoder; a per-call cached payload
codec avoids checking/encoding the same application data again. Payload size
limit is 1 MiB. Bare codec encoding of incomplete data returns JSON null rather
than throwing; use the checked wrappers for a structured encoding failure.

Prove common WorkOrder/result round trips and structural issued-result checking,
then actual explicit semantic admission and planning resumption. Fixture frames
are deliberately private transport data, not canonical persisted issue, runtime
one-shot consumption, initial human consent or production CLI activation.
The planned payload identities still need CML/Operation registration in the
converted route. Keep envelope/grant/instance/configuration checks separate.

Fresh 746: unchanged runtime preparation/JAR/focused accumulator via the serial
SBT wrapper, current typed permission/allocation and native review. At most
three converging same-unit mechanical pre-review repairs; no source edits during
live SBT. No hashes/adapters, application locks/claims/retries, public CML or
operation activation, storage/provider/grant/pool/producer, shared-skill/upstream
edit, Git mutation, publish/install or external command dispatch. Full original
Phase scope, authority/base admission, independent review, managed context/result
storage, real WorkOrder completion, CLI/restart and release remain required.

## Implementation and executable evidence

Implemented the two application PayloadCodecs and checked common-envelope
wrappers. The application envelope is exactly `schemaVersion` plus `payload`;
the request/result schemas and type identities are distinct. Canonical typed
JSON comparison rejects extra or missing nested fields. The cap applies to
UTF-8 bytes, not character count. Decoding restores raw Request/Judgment only:
structurally complete invalid ranges, missing coverage or duplicate evidence
remain data until the existing semantic result boundary refuses them.

Added ten Given/When/Then executable scenarios covering:

- Exact mixed, novel-only, edge-only and ordered multi-gap requests, retaining
  source references, criteria, prerequisites, adjacent items and authority.
- Both semantic decisions, and separate refusal of inadmissible decoded data.
- Unknown and omitted fields at every object depth, unsupported schemas,
  historical management-digest fields and participant execution/authority fields.
- Primitive/null, fractional/string integer and enum alias refusal.
- Actual CNCF common WorkOrder and ContinuationResult JSON round trips;
  incompatible type identities fail before invoking the supplied payload decoder.
- Common structural result matching followed by the actual private semantic
  admission and Workflow resumption to its computed AllocationRequired outcome.
  Known durations and source edge authority are unchanged.
- Crossed instance/run/continuation/revision/snapshot/type, missing common facts
  and evidence; a crossed application context is refused at semantic admission,
  not falsely granted by a matching common envelope.
- Exact inert Unicode/quoted/multiline explanations and a non-ASCII payload whose
  character length fits but UTF-8 bytes exceed the transport cap.
- Incomplete typed payloads and absent/wrong-kind/wrong-type WorkOrder input,
  returning a structured checked-encoding failure rather than a partial frame.

Invocation `SMWF-SPLIT-EXPLICIT-PARTITION-SEMANTIC-JSON-DIRECT-20261002-746`
used the unchanged shared serial wrapper, `cozyPrepareRuntime`, `packageBin`
and the retained focused accumulator. Actual native session 96626 terminated
with exit 0; wrapper reported SBT exit 0, wrapper exit 0 and `lock=released`.
375 tests / 51 suites passed, including all ten new scenarios. No repair was
needed (0/3). No source edits occurred during the live invocation.

Evidence: `/tmp/skill.cncf.d/a-cf67645a-65fb-4bbd-88ab-914ce057bf26/17894-20261001T230444Z.summary.json`
and its sibling `.log`; the new suite's current JUnit report also records ten
tests, zero errors/failures. `git diff --check` after recording the outcome
passed with exit 0. No acceptance commit or Phase closure was performed.

## Remaining integration boundary

This is converted application transport and pure result/resumption evidence,
not an issued persistent WorkOrder, execution permission, consumed result,
initial human consent or registered production Operation. Private fixture
service names are not claimed as public component operations. Common structural
`admitResultC` is not canonical storage/authority proof. No claim/recovery/runtime
resume API was invoked. The old hash-backed semantic workflow is still present
and does not become conformant merely because its regression scenarios pass.

Continue toward real typed stored context, Workflow-owned canonical issue and
completion and CML/Operation/CLI integration without a legacy hash/claim adapter.
Retain the entire original Phase 2 scope and its independent acceptance,
original-base/authority, comprehensive review, full validation and release gates.
