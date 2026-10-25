# Recovery observations and cause resolution

Date: 2026-10-24 (Asia/Tokyo)
Status: adopted design; implementation and live adoption pending

## Purpose and boundary

The [runtime-owned repair correction](runtime-owned-repair-control.md) adds
available stop/decision provenance to these existing observations. Distinguish
actual runtime Human requests from caller-added restrictions, authority deltas
and capability gaps. Recording must not introduce an approval-as-detector gate
or an AI invocation; absent caller facts remain unknown. Its bounded delivery is
tracked in [RRC](../phase/runtime-repair-control-correction.md), preserving the
qualification and pending status of the existing recovery work below.

Ordinary recovery must preserve progress without hiding defects. Recovery success
and cause resolution are independent facts. A corrected path selection can allow
the current commit to finish while the selection producer remains defective.
Neither automatic continuation nor Human authorization establishes cause closure.

This extends the existing [Journal runtime](development-journal-runtime.md),
[common Evidence](common-evidence.md) and [Finding recording](goal-phase-finding-recording.md).
It accompanies the selected-commit correction and narrow ordinary-recovery route;
it is not a new approval gate, AI detector, generic incident platform or Phase
completion prerequisite. The [delivery plan](../notes/recovery-observations-delivery-plan.md)
defines the bounded implementation sequence.

## Separate facts

Retain operation outcome, recovery disposition, recording outcome and cause
disposition separately. Map these concepts into existing typed models where
possible; the following are semantic requirements, not invented existing enums.

| Fact | Meaning |
| --- | --- |
| Operation outcome | Actual failure, non-execution, success or uncertainty of the original action |
| Recovery disposition | Pending, continued after correction, reconciled, deferred or failed, based on actual results |
| Cause disposition | Unassessed observation, suspected cause, confirmed defect, resolved or explained without a defect |
| Recording outcome | Actual Journal/Evidence/Finding persistence, partial failure or unavailable result |

An observed anomaly can be certain while its cause remains a hypothesis. For the
`.scalafix.conf` incident, the nonexistent unchanged path and actual pathspec
failure are facts; attribution to a particular selection generator needs source
evidence. Record that distinction rather than manufacturing a confirmed cause.

## Deterministic recording through the existing runtime

Use actual operation outcomes and existing recovery branches to trigger recording.
Record preparation correction, reconciliation, unresolved effects and recurrence;
ordinary expected execution with no recovery signal needs no incident entry.
No AI call is required to select, write, summarize or deduplicate these facts.
Do not parse free-form stderr into execution authority or cause certainty.

1. Evidence retains or reuses actual original result/log references, the correction
   description, relevant Git observations and the later execution/reconciliation
   result. Reuse existing stored payloads; do not copy all logs again.
2. Journal links the event, reason for the selected recovery branch, correction,
   recovery result, Evidence references and any related Finding. Derive the short
   human summary from a deterministic template and actual fields.
3. Finding tracks an observed contract violation or suspected product defect and
   its unresolved cause. Expected environmental failures remain observations unless
   there is evidence of a product issue. Ambiguous signals retain their uncertainty
   for the next already-planned semantic assessment.

Current Finding producers originate in accepted review records. Add an explicit
runtime-operation source through the same common owner; do not fabricate a review,
review PASS, Human decision or AI finding-recording work item. Reuse common public
Operations/UoW persistence, storage and retrieval, not a second store or direct SQL.

Minimum context: existing Session/Phase/Step where known, operation and actual
attempt/result reference, observed signal, recovery branch and rationale,
correction scope, recovery result, cause disposition and related Evidence/Finding
references. Missing facts remain absent/unknown. Do not retain full Plans, repeated
logs, secrets or a second execution envelope in the Journal summary.

Use existing source identity/revision, operation/attempt and a named signal kind
to distinguish an occurrence. Re-reading the same result adds no event or count.
A new actual occurrence may link to an existing Finding when an explicit cause
link or deterministic producer/contract identity establishes the relationship.
Do not equate identical exception text with a shared cause. Without that evidence,
retain separate observations for later semantic grouping. Avoid hashes, global
scans, a new registry and an AI call to decide every recurrence.

Recording is separate from the business effect. Journal failure must not turn a
completed commit into failure or replay commit/Submit. Report partial/unavailable
recording honestly and use the retained source with existing supported projection
recovery. Never claim stored Evidence for a failed archive. Where this producer
cannot yet repair a projection, expose the gap; do not invent a queue, silently
drop it, or block ordinary completed work merely to obtain AI transcription.

## Continuation and cause closure

When the old owner ended, no commit was created, and correction preserves the
accepted candidate and existing authority, ordinary continuation needs no new
Human approval. A matching completed commit uses reconciliation; live/unknown
effects require observation, not blind replay. New authority or an explicit
decision to repeat unresolved effects retains the genuine Human channel.
The planned change must be implemented through the owning recovery contract;
callers must not bypass today's admission or forge AuthorizeNewAttempt evidence.

A recovery Finding stays unresolved after a successful retry. Cause closure needs
an attributable correction or supported explanation, affected verification and
references that establish the causal issue was addressed. Reuse applicable tests
and accepted review; no universal additional review or full test is introduced.
An explained non-defect has its own disposition and does not claim a bug fix.
Later recurrence remains visible even after prior resolution.

Continuation blocking depends on the actual unresolved acceptance/safety issue,
not the mere existence of a Finding. Repeated recovery contributes observations
to existing convergence/repair policy; do not reset/refund repair counts or create
another counter budget. At an already-planned Step/Release assessment, unresolved
cause items are available together. A recording event alone launches no AI review.

Display example: "Commit selection corrected; workflow continued. Selection
defect remains open: Finding <id>." Use actual completion and identifiers only;
if recording failed, show that fact instead of a fictional Finding reference.

## Adoption

Preserve existing Session identity, Plan, accepted implementation/review, history,
repair counts and applicable validation results, including failed/unknown attempts.
Use public supported continuation/adoption; no re-Start, accepted Submit replay,
private database mutation or routine revalidation solely for this change.
Historical source records remain immutable. Optional projection of a retained
incident must use actual accessible records, be repeat-safe and be labelled as
recorded later; historical backfill is not a prerequisite for ongoing work.
