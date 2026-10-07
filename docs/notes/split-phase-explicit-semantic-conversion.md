# SplitPhase hash-free semantic conversion frontier

Status: implementation design, not executable evidence or Phase acceptance.
Scope: original sm-workflow Phase 2; retained parent-direct limited recovery.
No new repository, public Operation, storage grant or acceptance exception.

## Current boundary

The explicit Start record is immutable host-owned data. Its registration
re-admits current actor, selection, source/proposal facts, Workflow/configuration
and exact Create capability. It does not create a Workflow instance, attest
human consent or issue work. Its reference and explicit instance identity are
the starting inputs for the new route, not aliases for old derived identities.

The existing managed completion route reconstructs full stored facts, but then
calls `SplitPhaseSemanticInputs.prepareC` and
`SplitPhaseSemanticWorkflow.assembleRetainedC`. Both still generate hashes.
Replacing only Continuation identity or comparing full facts before those calls
does not remove that dependency. Existing fixture codecs and tests must not
become a compatibility adapter for new records.

## Source-grounded dependency map

| Current source | Remaining management-hash dependency | Required replacement |
| --- | --- | --- |
| `SplitPhaseProposalPrecheck` | Source/draft digest agreement and computed proposal digest | Exact source/proposal references and complete selected assessment, inventory and ordered draft comparison; retain shape/ownership constraints |
| `SplitPhaseDurationCalibration` | Observation self-digest, snapshot digest and policy digest | Explicit observation/snapshot/policy revisions with complete observations and policy; retain exclusions, sample admission, rounding and novelty classification |
| `SplitPhaseSemanticHandoff`, `SplitPhaseValidationOwnership` | Draft digest binding and returned plan digests | Exact proposal binding plus complete validated handoff/ownership plans; retain edge ordering, ownership and reciprocal validation obligations |
| `SplitPhaseSourceBoundary` | Source digest binding and computed closure/authority digests | Complete current source closure and authority declarations with explicit revisions; retain all closure, handoff and ownership checks |
| `SplitPhasePartitionEnumeration` | Source/policy/candidate/considered-set digests | Typed policy and ordered candidate/range results; retain exhaustive contiguous enumeration and deterministic ordering |
| `SplitPhaseProposalValidation` | Hash-composed Ready evidence and semantic receipt | Typed validation result containing exact inputs, calibrated outcomes, candidate selection and complete admitted semantic result |
| `SplitPhaseSemanticInputs` | Request context and judgment receipt hashes | Explicit context/result references plus complete facts and derived request/result data |
| `SplitPhaseSemanticWorkflow` | Derived Continuation ID, evidence revision, result causation and Preview idempotency | Host-owned explicit references bound to the canonical instance, issued WorkOrder and immutable result record |

This inventory follows actual method bodies, not the presence of imports alone.
In particular `calibrateC` checks `snapshotDigestOf`, `validateC` for handoffs
and ownership calls `digestOf(draft)`, and enriched validation incorporates
`receiptDigest`. These are reached by current semantic preparation/completion.

## New route data contract

Use purpose-specific typed references with canonical nonblank identity and
revision. Never choose a newest record, derive an identity from bytes, or accept
a caller's reference as admission. The host registers immutable records at
explicit identities; equal retry returns the original record and a conflicting
revision or payload at that identity is refused.

An explicit semantic context must contain:

- its reference and the exact explicit Start record reference;
- owning instance identity, expected instance revision and Workflow definition
  identity/revision, including current configuration binding;
- exact assessment command and selected completion Operation/requirement;
- explicit references and complete values for source, proposal, inventory,
  calibration observations/policy, partition policy, boundary, handoffs and
  validation ownership;
- the complete deterministic derivation: accepted/excluded observations,
  known estimates, unresolved work items and unresolved edges;
- separately assigned Continuation identity and context revision.

Do not manufacture revisions for current schemas that lack them. Convert the
owning record contracts and readers first. Distinguish a recorded absence from
an omitted required fact; optional fields remain explicit in the closed schema.
No stored Intent, Admitted, capability, borrowed lease or Operation capsule.

The AI request carries the explicit context reference and only the unresolved
semantic work with the evidence necessary to do it. The result echoes that
exact reference and returns novelty ranges, boundary judgments or a
decision-needed rationale. It cannot select the next Operation, issue commands,
replace known estimates, assign host record identities or grant authority.

Workflow loads the complete context and current referenced facts through the
current call, deterministically reconstructs the unresolved request, and checks
full equality. It verifies canonical issued WorkOrder, suspension, instance
revision and Completion contract separately before accepting the result. The
private admitted constructor is reached only after these checks, never by JSON
decoding. A changed fact is rejected even if its reference is unchanged.

The immutable result record contains its own host-owned reference, exact
context/Continuation binding, complete AI judgment and complete deterministic
admission/validation output. Preview idempotency and completion causation use
that reference, not a digest. Retry must recover the same canonical completion
and result; merely allocating another result reference cannot make a consumed
Continuation executable again.

## Conversion order and evidence gates

1. Convert calibration observation/snapshot/policy data and its pure algorithm.
   Prove identical numerical behavior, exclusion reasons and unresolved novelty
   without computing hashes. Include same-reference changed-observation and
   changed-policy refusal using independently loaded complete data.
2. Convert proposal/source and ownership/handoff/boundary record contracts and
   pure validation. Preserve source closure, semantic authority and original
   validation-owner obligations; references alone are insufficient.
3. Convert partition enumeration, ranking and proposal validation. Prove exact
   candidate completeness/order, 6-hour objective/8-hour bound and short-child
   merge/rebalance behavior. Return typed derivations, not digest witnesses.
4. Construct and register the explicit semantic context from those new types.
   Reconstruct only through admitting constructors and current readers. Prove
   full-field drift refusal, absent/foreign/expired-call/grant failures and
   restart recovery. The reachable path must not invoke old hash algorithms.
5. Bind canonical production issue/completion to explicit Start/context/result
   records. Preparatory records remain inert without a matching canonical
   WorkOrder. Unless the producer supports joint local record effects, record
   preparation and suspension are two operations, not one transaction.
6. Prove public Service/launcher CLI and admitted MCP behavior, genuine initial
   human ingress, separate-process restart and enriched Preview recovery, then
   complete the original review/validation/release gates.

These are dependency-ordered implementation units inside the existing Phase,
not successor Phases or a reduced definition of completion. Freeze exact
source/test ownership before each edit; numerical behavior is deterministic
work, not an AI task. The new route must use its own explicit types/schema with
no old/new decoder fallback or management digest disguised as a revision.

## Evidence limits

Invocation 727: 275 tests / 39 suites passed; six committed-Create observation
interruptions recovered after physical pool reopen. That executable evidence
does not test any newly proposed semantic conversion above. This document
changes the implementation frontier and corrects stale recovery observations;
no runtime/schema source was changed and no SBT rerun is justified by prose.
Phase authority/original bases, independent acceptance reviews, full validation
and release remain open. No stage, commit, publish or install.
