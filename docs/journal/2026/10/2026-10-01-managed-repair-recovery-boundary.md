# Managed repair recovery boundary investigation

Date: 2026-10-01
Status: source-backed implementation preparation; no new runtime or acceptance proof

Continue the existing Phase 2 and admitted CNCF Phase 88 work. The preceding
turn verified that canonical Subsystem construction prepares the shared storage
before Component core assembly. That infrastructure is not the remaining
repair-loop gap. Preserve existing source, provider ownership and Phase scope.

## Current authoritative paths

`GoalPhaseRepairLoop.afterReviewC` derives an Intent from the issued review and
submitted judgment. Its `publishC` then saves that Intent, appends the suspended
instance, publishes the Continuation, claims it and publishes the WorkOrder
through separate prerequisites. A failed prerequisite returns no WorkOrder,
but that does not establish atomicity or rollback of earlier durable writes.
The current managed three-Operation Service deliberately does not advertise
`submitStepRepair`; its composition must not be treated as the complete service.

`WorkflowManagedCompletion` currently exposes no public retained-result query.
The existing provider's `WorkflowCompletionDataStorePersistence.loadC` does
verify a Completed Continuation, canonical result bytes, issued-work admission
and adjacent suspension/completion history. Accessing the provider directly
from the application would bypass the opaque host-bound entry used for writes.
The missing seam is a host-bound query, not a new application pool or SQLite
connection.

## Recovery must not redo an already committed admission

`GoalPhaseRepairCandidateBoundary.retainIntentC` calls
`GoalPhaseReviewResultAdapter.assessC`, whose freshness check uses the supplied
`asOf`. Completion records retain instance revision, the semantic result and an
optional typed closing result. Neither the V1 record nor its V2 closing envelope
retains the host admission timestamp. Consequently, calling `retainIntentC`
with restart time is not equivalent to recovering the original admitted Intent:
the review evidence may have expired after valid completion.

Do not fabricate the admission time from worker-provided `reviewedAt`, evidence
expiry, restart time or the result's context reference. Do not require a fresh
AI review merely to rediscover a previously committed repair decision. Existing
candidate preparation already distinguishes this case: it re-evaluates evidence
for the repaired candidate while preserving the earlier repair judgment.

## Implementation direction

Use the retained managed completion as the durable precursor of the repair
transition, rather than adding an independently written Intent record. Keep
fresh judgment admission and committed-judgment recovery as distinct APIs.

1. Provide an opaque, host-bound typed completion query through the framework.
   Each call must re-admit the captured Component, store and collection grant,
   borrow the shared managed resource briefly, and delegate existing canonical
   completion/history verification. It grants no claim and performs no replay.
   Raw JSON, a decoded record or a caller assertion is not an admission proof.
2. Bind application recovery to that verified query and the retained issued
   review. Require the exact generated review completion Operation, workflow,
   instance, continuation, result reference and typed closing result; require a
   repair decision and the matching committed history entry. Do not invoke the
   fresh-admission freshness check at restart time. Recovery supplies no invented
   historical admission timestamp. The current Intent shape therefore needs
   separation of historical judgment authority from observation-time ordering,
   not merely a new loader feeding the existing `retainIntentC` unchanged.
3. Derive the repair continuation identity, input, judgment reference and budget
   deterministically from those committed facts. Extend the pure WorkOrder
   projection to the generated repair completion contract; do not select an
   arbitrary completion Operation from skill or worker input.
4. Persist the repair suspension, Continuation and issued WorkOrder through the
   existing managed atomic suspension boundary. No independent Intent save,
   legacy runtime claim or private application transaction remains on this path.
5. Treat completion and next suspension as two explicit managed transitions,
   with a recoverable interval between them. A crash after review completion but
   before repair publication must be recoverable by Workflow advance from the
   retained completion; a callback alone is not a recovery mechanism. Compare
   expected current revision, recognize an exact already-issued successor, and
   reject changed or later incompatible successors without replaying AI work.
6. Apply the same structure after repair completion: read authoritative artifact
   bytes, derive the new candidate and atomically issue a fresh full review.
   Unchanged bytes still require a new judgment; prior evidence is retained but
   never silently promoted to acceptance.

This uses existing shared-storage ownership and managed transition semantics.
It does not authorize a schema-version change, a fabricated timestamp, a raw
provider escape hatch or removal of repair from Phase acceptance. Freeze exact
query/proof and application contracts before implementing their public edges.

## Required executable evidence

- Valid repair judgment committed before expiry remains recoverable after expiry
  and host reconstruction; a new expired submission is still rejected.
- Absent, wrong-instance, wrong-operation, uncommitted or altered retained facts
  cannot yield repair authority or a WorkOrder.
- Crash after review completion and before successor publication resumes through
  deterministic advance, not resubmission of the consumed review Result.
- Suspension failure leaves no partial successor; retry and concurrent advance
  return or observe one exact successor without duplicate AI work.
- Fresh-host repair Result completion and subsequent full-review issuance retain
  the original Start correlation and exact adjacent history.
- No legacy Intent map, independent Intent write, application pool, direct SQL,
  semantic callback replay or unexpected Git closure is exercised.

No source behavior was changed and no SBT command was run in this investigation.
Focused passing evidence through invocation 441 remains historical evidence for
the review slice only. Managed repair, production host ports, default factory,
CLI/server recovery and complete Phase assurance remain open.
