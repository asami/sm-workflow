# SplitPhase semantic intent recovery boundary

Status: source-grounded implementation design; not runtime or Phase acceptance.
Scope: existing Phase 2 SplitPhase managed semantic publication/completion.

## Authoritative source observations (updated through invocation 727)

`SplitPhaseSemanticWorkflow.Retained` currently holds the assessment command,
`Prepared`, private `SplitPhaseSemanticInputs.Intent`, active InstanceRecord and
Continuation. The existing fixture `prepareC` constructs these from application
readers. `revalidateC` repeats preparation before completion, comparing the
request, active record and Continuation. The managed completion-only path now
loads immutable semantic input facts and the original command through current
authorized host readers and reconstructs Retained. That recovery still invokes
the old hash-backed preparation; durable data does not make it the new
hash-free production path.

`Intent` contains private draft/evidence/calibration/boundary data. `Admitted`
contains a verified Intent plus a judgment. Neither is a wire authority witness;
decoding either directly from storage would bypass its constructor's admission.
The current proposal reader already rejects persisted `semanticInputs` and
reconstructs baseline evidence with `semanticInputs = None`.

The completion-only managed assembly was first tested in invocation 710.
Invocation 720 additionally proves host-record completion with current
selection, entry, proposal and semantic-input readers, without retained
application callback data on that route. Private baseline and WorkOrder
installers remain test fixtures; no public issuer is established by that test.
The standard managed entry Service still assembles only `startSplitPhase` and
`assessSplitProposal`, without a semantic issuer. No production completion
activation follows from the seeded completion test.

The current producer `WorkflowManagedIssuedWork.suspendC` accepts a suspension
intent, typed WorkOrder and event records. It calls prepared `hostParticipantC`
with an empty local-record-effects vector. Thus that public method does not
currently enlist a separate application intent record. Lower-level prepared
record facilities are not proof of this public composition. Do not describe
an independent intent write followed by suspendC as one transaction.

## Recovery contract

Persist data, never `Intent`, `Admitted`, a provider, a borrowed lease, a current
Operation capsule, or a whole in-memory workflow. The application record must
be a closed versioned projection containing:

- explicit record identity/revision and exact owning instance/Continuation;
- the complete assessment command, including its original selection,
  participant, source/proposal references and idempotency reference;
- exact source, proposal, inventory, validation, boundary and calibration
  record identities/revisions, and the selected workflow definition revision;
- the complete frozen semantic request and the original explicit context
  facts needed to compare it with current evidence;
- original execution requirement and completion Operation identity, checked
  against host declarations rather than accepted as grants.

The exact reference/revision representation must be settled with the existing
host record schemas before a codec is implemented. A missing revision must not
be replaced by newest-row selection or a computed management digest.

On recovery, the current Operation's authorized readers load the recorded
facts and current policy. Ordinary deterministic preparation reconstructs
Intent through its admitting constructor. Compare the complete frozen request
and provenance, not only a hash. Match the canonical saved WorkOrder, its
instance suspension and Completion contract before accepting an AI result.
Changes or missing evidence refuse completion without consuming its claim.
Stored data, actor authentication and transport JSON do not confer permission.

The canonical WorkOrder is the publication witness. An application intent
record alone never establishes an issued request, a Start, a suspension, or a
claim. If immutable preparatory records are written before publication, they
may survive an interrupted issue, but remain inert: no resume or completion
may proceed without the canonical matching WorkOrder. Equal retry recovers
the same record; conflicting bytes at the same identity are refused, never
overwritten. Such a sequence must be tested and described as two operations,
not atomic joint publication. If one atomic application-record publication is
required, extend the admitted producer's managed composition first; do not
bypass it with raw SQLite or an independently acquired provider.

## Hash-free production prerequisite

Current Start derives instance identity and start causation from SHA-256.
Semantic preparation derives Continuation identity from command/context hashes;
semantic input preparation derives context and judgment receipt hashes.
Re-running these fixture algorithms is not the required explicit-reference
production recovery contract. Existing seeded tests remain historical evidence,
not a migration/alias path for newly admitted production records.

Before activating the production issuer, replace management identities/context
matching with explicit host-owned references/revisions and complete typed
records. Preserve any separately justified artifact-byte integrity checks as
such; do not use them to infer authority. No new permission for digest use is
provided by this note. The change needs its own frozen source/test frontier,
payload compatibility decision and executable conformance proof; do not silently
change the existing payload codec while adding an intent store.

The replacement frontier is larger than Start and Continuation: proposal
precheck, calibration, handoff/ownership validation, source-boundary validation,
partition enumeration and final proposal validation still compute or require
management digests. Calling them from a new explicit-context constructor would
retain the forbidden dependency. See
[Hash-free semantic conversion frontier](split-phase-explicit-semantic-conversion.md)
for the source-grounded dependency order and required evidence. An explicit
reference is neither a grant nor a replacement for checking complete current
facts and deterministic rules.

## Required executable evidence

1. Actual public assessment issues work on the same Start-created instance,
   with no test-only Retained installer or legacy claim runtime.
2. Stop after preparatory record creation but before publication: restart
   must not expose work or accept completion from the preparatory record alone.
3. Stop after canonical publication but before response: exact retry/restart
   recovers one original WorkOrder and unchanged history, not a second issue.
4. New process with only declared storage and immutable source records can
   reconstruct Intent; no retained callback Map or captured Factory context.
5. Missing/changed referenced fact, foreign owner, expired call and missing
   grants all refuse without mutating the suspension or retained result.
6. Both decision-needed and enriched results use the common completion engine;
   enriched automatic Preview can recover after completion/response interruption
   without repeating AI judgment, publication or external mutation.
7. Service, normal launcher CLI and explicitly admitted MCP reach the same
   Operations; skills only perform issued semantic work and return results.

Invocation 720 proves seeded host-record Service/CLI decision-needed completion
and physical pool reopen. Invocations 721–726 add separate explicit Start
preparation, closed record storage/readers and immutable managed registration.
Invocation 727 proves normal registration recovery after six actual committed
Creates with interrupted immediate observations, followed by pool shutdown and
fresh-host reads. These records remain inert: no instance creation or issue.
None proves production publication, enriched response recovery, provider
uncertain commit, separate-process restart, real initial human consent or MCP
activation. Their journals retain exact command receipts and fixture limits.
