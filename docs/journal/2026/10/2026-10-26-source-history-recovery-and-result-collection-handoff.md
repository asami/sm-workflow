# Source-history recovery and completed-result collection handoff

Date: 2026-10-26 (Asia/Tokyo)
Status: policy/specification/plan adopted; new runtime recovery implementation and
live consumer adoption remain pending. Existing execution results below are real
retained results, not proof of the new recovery contract.

## Incident and corrected execution status

Phase 6.6 consumer Session `f01d809d1b4bfe8a4b5c241dac301b81` reports D1/D2
accepted and committed. D3 repair Submit was accepted. Source-history normalization
then stopped with a missing result for
`src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseDevelopmentEventsSpec.scala`.
Public Human226 offers AuthorizeNewAttempt/Defer; its rationale keeps the old
normalization UNKNOWN. Eight earlier normalization outcomes were retained. Repair
counts remain automatic=1, ordinary=1, limit=10. The duplicate comment was reported
corrected; that alone did not reconcile the runtime history. This is a genuine
runtime request, not an AI-created repair ceiling.

The producer chat failed with `Selected model is at capacity` while collecting
qualification. Inspection on 2026-10-26 found that normal publishLocal had already
succeeded and the final installed03 command had also terminated successfully:

- Publication receipt: `/Users/asami/src/dev2026/sm-workflow/.codex-workflow/disposable/a-3d556d81-c91d-4e01-be21-c387d5c6f4ef/command-result.json`;
  outcome success, terminal, exit 0, process terminated.
- Publication/activation observation: `/private/tmp/sm-rrc-delivery-20261025/normally-published-runtime.json`;
  publication Completed, runtime Activated, installation
  `d19b540a-647f-4dc6-84ed-f510037183ad`, update
  `f78e8820-0768-4b63-a9cb-fe575077299a`. This is the selection at qualification,
  not an assertion about any consumer's current selection.
- Final qualification receipt: `/Users/asami/src/dev2026/sm-workflow/.codex-workflow/disposable/a-900e786a-a5f2-449e-96cb-3ecf93bb829f/command-result.json`;
  outcome success, terminal, exit 0. Its stdout records two selected cases passing,
  including retained-failed and timeout recovery; final completion was
  2026-10-25 22:25:28 JST. Earlier failures remain in their own records.
- Consumer request: `/Users/asami/src/dev2026/sm-workflow-dev/target/sm-workflow/runtime-phase-6.6-20261025/target/sm-workflow/tasks/phase-6.6-01a1555d-b4a7-7be3-8232-5396c2f5e293/015-d3-history-frontier/response.json`.

These results correct the claim that publication and final command completion were
unconfirmed. They do not by themselves complete producer acceptance, implement
this new comment-recovery behavior, or authorize an invented answer to Human226.
The producer must account for its other known receipts and finish the usable
handoff, without repeating these successful commands merely to recover narration.

## Adopted correction

Use [SHR-01..06](../../../spec/goal-phase-source-history-recovery.md#same-authority-comment-recovery--adopted-2026-10-26)
and the [interrupted-caller collection contract](../../../spec/native-host-execution.md#recovering-completed-results-after-caller-interruption).
Runtime reconciles the affected current comment using the retained rule/date.
Already normalized means observe and continue; unambiguously normalizable means
apply the same bounded update and continue. Old uncertainty stays in history.
Missing receipt alone does not trigger another Human decision. Preserve real
scope/intent conflicts and concrete failure diagnostics; no blind generic replay.

Skills collect saved terminal receipts after interruption instead of inferring
execution failure from chat failure. Keep publication, activation, qualification
and consumer adoption separate. Use original handles/references; no new polling,
AI monitor, automatic model switch, duplicate Submit or repeat publishLocal.

## Producer handoff: sm-workflow development

1. Recover the already completed delivery results first. Finish the factual RRC
   handoff from actual receipts; source/new installed behavior must remain distinct.
2. Implement [C2/C3](../../../phase/source-history-recovery-result-collection-correction.md)
   through the existing driver, native candidate/history and public continuation.
   Establish one comment-reconciliation-to-normal-validation route first.
3. Preserve old pending questions/UNKNOWN and append adoption/reconciliation using
   existing authority and this user direction. Do not ask again solely to adopt
   this policy or fabricate the old AuthorizeNewAttempt answer.
4. Qualify affected behavior, use normal publishLocal for new product changes when
   needed, and return one usable outcome with actual installed capability and
   concrete consumer continuation. Do not modify the consumer's Session storage.

## Consumer handoff: sm-workflow-dev development

Keep D1/D2, accepted D3 result, Session/base/Plan, all history, original Human226 and
repair counts 1/10. Update the factual producer status from retained receipts;
producer chat capacity failure did not undo publication or completed qualification.
Adopt the new comment recovery only through its delivered public path. Until then,
report the precise missing runtime capability, not a new caller approval rule.
Do not re-Start, re-Submit accepted work, invent a Human answer or edit workflow
storage. Resume normal D3 validation/admission after reconciliation. Reuse prior
results only at their actual scope; finish D4's result-collection adoption without
reopening accepted D1/D2. No automatic model change is requested by this handoff.

## Preservation and validation of this planning change

This turn changes skills/specifications/design/planning only. It does not execute
SBT, publication, live recovery, Submit or Advance, and does not alter Goal/Session,
history, counters or test receipts. Recovery versus cause resolution is recorded
through existing Journal/Evidence/Finding without extra AI calls. Shared skill
sources and packaged-source guidance are updated together; actual packaged runtime
qualification remains the producer's responsibility.
