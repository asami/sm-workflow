# SplitPhase terminal after a recorded Human answer

The answer-recording boundary remains Active/HumanAnswered for every answer.
An independently authenticated later `advanceSplitPhase` consumes the retained
canonical answer and its complete original lineage; it does not renew consent.

Defer maps to BLOCKED and Cancel to CANCELLED. The declared CML successors are
Answered --human-deferred--> Deferred and Answered --human-cancelled--> Cancelled.
Exactly one canonical native append completes the instance, retaining the whole
HumanAnswered prefix and correlation, with neither progression nor suspension.
Its causation identifies the answered Decision. No documents, external commands,
new side records or EventRecord batch are selected by these two transitions.
The existing public owned instance binding supplies storage and authoritative
readback; the active-only mechanical receipt contract remains unchanged.

The closed `SplitPhaseAdvanceResult` terminal payload carries the versioned
schema `sm-workflow.split-human-terminal.v1`, outcome, original context reference,
Decision identity and retained Human answer reference. The complete value and
fixed presentation are derived solely from retained facts and the bound Workflow
definition, not the current clock, mutable policy or a regenerated proposal.
Payload decoding is passive and supplies no progression authority.

Return the canonical Workflow Terminal codec only over the matching durable
Completed instance. Later advance and physical reopen admit the exact one-entry
terminal successor and derive the same result without append, consent verifier,
allocation, processor, AI call or callback replay. Identical Human submissions
continue to observe their original answer; completingRevision stays unchanged
while currentRevision describes the actual later instance.

An uncertain append acknowledgement/readback returns transaction-indeterminate,
not a Terminal or an immediate retry. A later call reads authoritative facts
before choosing retained observation or still-pending deterministic work.
An inert test-only observation may interrupt acknowledgement after the actual
canonical append; it provides no result, storage or transaction authority.

Absent/conflicting answer, incorrect terminal causation, divergent prefix,
foreign relay, wrong original context or incompatible definition refuses.
ProvideResolution remains a distinct validation/resumption branch, never a
BLOCKED/CANCELLED fallback. Its implementation and all original PHASE-2 duties
remain required; Defer/Cancel alone cannot close Human handling or the Phase.

If a later feature actually selects nonderivable terminal data or transactional
effects/events, specify their atomic managed publication before implementation.
No such additional fact/effect is required for these deterministic branches.

Executable acceptance must cover both genuine first roots, both terminal choices,
Service/CLI, retained submission/advance, canonical native history, no repeated
effects, failure before mutation, actual post-append acknowledgement interruption,
physical reopen and foreign/contradictory-data refusal. No current test result
yet establishes this newly specified behavior.
