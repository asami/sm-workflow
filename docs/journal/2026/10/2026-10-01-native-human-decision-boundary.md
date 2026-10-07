# Native Human Decision boundary and limited recovery

Continue the existing Phase 2 under the user's explicit limited recovery and
`回避して`. Parent `/root` owns this read-only CNCF investigation and the
sm-workflow spec/documentation edits, standard mode; model/effort are not
directly observable. Bypass skill-only delegation/execution gates, not platform
review, actual storage authority, or machine-wide SBT serialization. No agent,
source-scope expansion, commit, push, publication or Phase closure is selected.

## Observed contracts

The public preparation/answer Operations validated by invocation 582 retain
question/receipt observations at the accepted-review revision. They do not yet
publish a native suspended Decision or complete its native Continuation.

In the current CNCF source:

- `WorkflowIssuedWorkDataStorePersistence.requestC` explicitly rejects every
  continuation other than WorkOrder. Its exact encode/decode uses
  `WorkflowWorkOrderJsonV1` and its immutable payload is `issuedWorkPayload`.
- `WorkflowInstanceDataStorePersistence.prepareIssuedSuspensionC` delegates
  exact issued-byte admission to that same WorkOrder adapter before compound
  publication. Merely supplying a generic WorkflowInteraction does not admit
  a Decision.
- `WorkflowCompletionDataStorePersistence` loads that exact issued WorkOrder,
  requires its kind and projects the claimed WorkOrder. Managed completion
  therefore cannot be reused for a human Decision by changing only the codec.
- `WorkflowDecisionJsonV1` already supports Decision wire projection. Wire
  round-trip is not managed publication, claim, completion or transition authority.

The added consumer behavior specification round-trips the real human Decision
codec and verifies refusal by both WorkOrder wire entry points. This is a
dependency-boundary test, not native DataStore integration or human proof.

## Next implementation boundary

Extend CNCF with explicitly kind-bound managed Decision issue/recovery and
completion, retaining the existing WorkOrder API/wire behavior unchanged.
The same shared Subsystem-managed DataStoreSpace/provider, actual declarations,
independent guards and lease must own the instance/Continuation/issued bytes
transaction. Do not use raw SQLite, a separate pool, a side-state append or an
AI WorkOrder disguise to bypass the missing API.

After that producer contract is executable and refreshed for the consumer:

1. Workflow derives the accepted-review base and a new Decision identity and
   successor revision. Existing revision-3 observations remain immutable;
   native publication must not reuse their identity with a different contract.
2. Atomically publish the native suspension and exact issued Decision. Equal
   retry reads the same boundary; foreign/stale facts refuse without writes.
3. Resolve the actual pending Decision and verify the exact independently
   attributable human answer. Ordinary actor provenance remains insufficient.
4. Retain the answer/receipt, then recover deterministic native completion
   through the exact pending boundary. Interrupted follow-up reads the existing
   accepted answer without another human choice, verifier call or effect replay.
   Do not claim joint answer/history atomicity unless explicitly implemented.
5. Workflow selects follow-up. Neither answer choice grants Git authority;
   bounded command authorization and uncertain-effect recovery remain separate.

Required executable cases: issued-kind separation; stale/wrong-question answer;
exact retry and conflicting answer; interruption before/after answer retention;
compound rollback/readback uncertainty; physical reopen; Service/ordinary CLI
parity; unchanged legacy stop and WorkOrder routes. A fixture verifier remains
a fixture, not production human-channel acceptance.

## Validation

Invocation `SMWF-HUMAN-NATIVE-BOUNDARY-DIRECT-20261001-583` ran the exact
`testOnly org.simplemodeling.textus.smworkflow.admission.GoalPhaseHumanDecisionContractSpec`
through the required serial wrapper with first-attempt scoped platform
escalation. One changed test source compiled; existing CML generated sources
were reused. All 7 tests in 1 suite passed, both SBT/wrapper exits were 0,
and the authoritative terminal marker reported `lock=released`.

Log: `/tmp/skill.cncf.d/a-74a586a3-6285-4498-8396-b3365b3aef19/65890-20261001T105500Z.log`.
Summary: `/tmp/skill.cncf.d/a-74a586a3-6285-4498-8396-b3365b3aef19/65890-20261001T105500Z.summary.json`.

This is focused consumer kind-separation evidence only. Invocation 582 remains
prior broader evidence; neither run establishes native Decision issue/completion
or current repository-full Phase acceptance. No source changes were made to CNCF.
