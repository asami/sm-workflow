# Purpose-based authorization and same-purpose scope updates

Date: 2026-10-25
Status: policy/specification/plan adopted; runtime implementation and live adoption pending.

## Observed problem

Control Center Phase 5.1 Unit B required two completion owners in sm-workflow,
`GoalPhasePostCompletionSync.scala` and `GoalPhaseNativeRepositoryClosure.scala`,
to record completion together with the actual terminal transition. They were
outside the originally listed 23 files. The retained runtime route did issue a
formal `Human / ApproveDependencyScope` request; relaying that request was valid.
The problem is the file-bound authorization decision, not permission to ignore a
real runtime request. The two-file approval is already retained; preserve that
25-file delivery and its verification. Do not ask for it again.

## Decision and caller order

The user explicitly selected Component, purpose, acceptance conditions and
operations as the authorization boundary. A necessary file addition within that
boundary does not require another approval.

1. AI submits the additional files and why they are needed for the admitted outcome.
2. sm-workflow compares the request with actual retained authorization, admits an
   in-boundary update, or issues a formal Human request for a real boundary change
   or unresolved authorization.
3. The skill asks the user only for that formal request. It never anticipates a
   per-file approval rule, silently ignores a runtime request or fabricates an answer.

Planned files remain useful implementation inventory. Actual files remain necessary
for candidate capture, validation and commit selection. Neither is a blanket grant
for unrelated changes. Explicit human exclusions and operation grants remain in
force. Ordinary semantic work explains purpose; no extra AI gate is introduced.

## Delivery handoff

Read [PA-01..07](../../../spec/goal-phase-dependency-scope.md#purpose-based-authorization--adopted-2026-10-25)
and [PA-D1–5](../../../phase/purpose-based-authorization-correction.md).
Implement the existing typed owners end to end, including dependency validation's
current equality with approved path unions. Merely changing skill prose will not
remove the runtime failure. Prioritize one additional-file admission through real
Submit/continuation/capture/validation before broadening scenarios.

- sm-workflow development: own domain/public admission, history-based authorization,
  candidate/validation integration and public same-Session adoption. Deliver runtime
  qualification and packaged skill together; identify the installed behavior honestly.
- SimpleModeling development: keep the current coherent restoration outcome and its
  already approved files/validation. Return one usable delivery with retained results;
  do not wait for this new contract or RRC and do not add per-file permission messages.
- textus-control-center development: collect that result and resume existing Phase 5.1
  through supported public procedures. Retain Session
  `fd87cec948d0933cb6181af1f696cb52`, all approval/attempt history and current counts.
- sm-workflow-dev development: reuse the producer correction when available without
  interrupting active work or inventing a local file-scope bypass.

For every consumer, preserve Goal/Session, original base, Plan, accepted work,
failed/unknown/successful results, repair counts and pending obligations. Reference
existing user direction in public adoption instead of asking it again. Keep old
file-bound records readable without rewriting them. No re-Start, accepted re-Submit,
internal-store edit or counter reset. Retained results prove only their actual
candidates; validate newly affected changes without repeating unrelated checks.

Until delivery, use the actual installed ABI. If it issues Human, relay that actual
request and reconcile already available direction through the supported route.
If adoption is unsupported, report the specific producer gap; do not treat this
policy document as a runtime receipt or block separately approved restoration.

## This change's limits

This change updates source/deployed-source skill guidance, shared delegation
contracts, dependency scope/validation specifications, design and the corrective
plan. It changes no product Scala, runtime configuration, Session storage, counters
or recorded validation outcome. Existing implementation and historical evidence
remain intact. Source guidance does not prove packaged CAR installation or runtime
acceptance. The development chats receive this handoff in English.
