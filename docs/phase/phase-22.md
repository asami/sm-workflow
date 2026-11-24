# Phase 22: Runtime Stabilization and Difficult Continuations

Status: planned
Selected: 2026-11-18 (Asia/Tokyo)
Owner: sm-workflow development
Checklist: [Phase 22 acceptance](phase-22-checklist.md)
Scheduled: after [Phase 23](phase-23.md) in the [current roadmap](README.md#adopted-development-priority--2026-11-22)

## Allocation and priority

On 2026-11-19 the Human explicitly approved a FORMAL Phase 8 local release
without additional validation or review. This Phase now owns all remaining
unverified portions of the original thirty requirements, as well as the
Workflow management problems described below. Additional release checks are
NOT RUN — USER-APPROVED; no historical failure or unverified condition becomes
PASS. Phase 8 is a valid released predecessor. This Phase's completion and
native management reconciliation are not retrospective Phase 8 release gates.

The following paragraph retains the earlier 2026-11-18 allocation direction;
the explicit 2026-11-19 release disposition governs its remaining obligations.

The Human selected a separate stabilization Phase for difficult residual work.
Phase 8's performance/cost improvements remain required, and Phase 8 continues
through its normal Steps rather than forced closure. Finish a useful bounded
Step and continue to the next instead of expanding one repair indefinitely.
Reach prioritized API-use delivery through the existing Phase 8 -> Phase 8.1 ->
Phase 9 order. The 2026-11-18 allocation placed this Phase after API delivery
and left its exact slot open. The current roadmap schedules it after Phase 23.

This transfer covers difficult generalization, robustness and extended assurance.
Required Phase 8 performance/cost behavior and bounded representative acceptance
stay in Phase 8. Authored source, a local publication and older checks do not
establish connected qualification. Accepted Steps, source changes, Sessions,
failed/UNKNOWN observations and repair counts retain their actual meaning.

## Phase 7 operational qualification

P22-11 carries the remaining connected delivery case from the 2026-11-22
[Phase 7 operational integration](phase-7.md#operational-release-follow-up--2026-11-22).
The selected case is `Phase7ManagedDevelopmentSpec`'s
“keep passing canonical checks from certifying B and attribute correctly bound
B failure.” After admitting the fixture's owned capture workspace, its
`DirtyPostCommit` branch reached the retained commit and blocked publication.
The fixture still expects inline exit codes in a compact public receipt.
Confirm actual selected check results through the owning retained-capture route,
then verify that changing tracked or untracked B source after its accepted
commit cannot trigger publication or certify A. Retain the independently
observed B failure and the passing connected delivery case. Record the original
failed test results and complete the unfinished assertions during stabilization.

## Caller results and on-demand diagnostics

P22-12 removes redundant parameter handling to reduce the data the AI caller
has to receive, retain and pass through each workflow call. In Phase 28.1's
`projectSemanticWorkResult`, the caller carried the saved response and stderr
paths in its shared invocation context and forwarded them with progress events.
These paths describe the call's outputs.

Remove `responseReference` and `stderrReference` from invocation context,
intermediate progress, caller handoffs and ordinary return values. Remove the
logic that copies or interprets these path parameters. Return the short values
needed to continue, such as the outcome and next issued action, directly as
structured fields. Keep the existing call and return indicators.

When diagnosis requires more detail, query sm-workflow through a read
Operation. Reuse `EvidenceService.readEvidenceRange` for bounded reads of
managed Evidence and the selection model of
`GoalPhaseService.resolveGoalPhaseDiagnosticInformation` for focused diagnosis.
The latter operates on retained failed-check context for an issued RepairStep.
For caller-owned `response.json` and `stderr.log`, connect the capture to
managed Evidence at save time. The caller program associates the files with
the exact invocation or attempt using the existing result or Evidence
identity. The read Operation resolves that association without an
AI-supplied filesystem path.
Project only the requested data into a suitable format, for example selected
response fields, a structured error summary or bounded matching stderr lines.
The ordinary path completes without fetching those details. The read has no
effect on Session progression or the original execution.

Verify a successful projection can continue from its ordinary result alone.
For a failed call, verify that the read Operation returns selected diagnostics
from the exact attempt, including when a Session contains multiple attempts.
Confirm both routes omit the path parameters throughout ordinary processing.
Compare the information delivered to the AI and the required caller handoffs
before and after on these cases so any reduction is grounded in observation.

## Hygiene and related-project warnings

P22-13 inventories Hygiene and build warnings in sm-workflow and the related
projects that supply its build, validation or runtime. Identify the affected
repositories and current warnings at stabilization start.
Inventory every unresolved Hygiene entry in each in-scope repository's
`docs/journal`, including entries unrelated to a current build warning.
Reconcile duplicates and already-resolved items against current evidence.
Select a bounded subset for this Phase under the existing
[Phase 23 Hygiene policy](phase-23.md#hygiene-selection-during-stabilization-planning--2026-11-19).
Record the selected items and their acceptance conditions. Keep unselected
items open with their original owners.

Attribute each warning to its emitting build step and producing repository.
Prioritize compiler warnings in production and test sources, together with
warning noise in the normal connected build and validation path. Fix selected
warnings in the owning project or generator, under its existing configuration
and validation route. Keep unrelated maintenance in its existing backlog.

Pass the affected producers' build checks and confirm that the selected
warnings are gone in sm-workflow's connected validation using the fixed
artifacts. Update the original Hygiene dispositions with attributable
results. Keep any unresolved Hygiene or warning explicit with its owner and
reason.

## Selected residual work

| Item | Work and acceptance boundary |
| --- | --- |
| P22-01 | Continue the same admitted work after operational conditions change by constructing a compatible runtime from the actual declared environment and source. Use the existing source-succession, publication, artifact-selection and resource owners. Require actual current provenance and affected checks; preserve earlier applicable results and original authority. A changed tree or artifact alone is not a new Human decision. Uncovered authority and unknown effects remain explicit. |
| P22-02 | Generalize captured validation ordering and its historical compatibility through the existing qualification owner. Preserve required check identities, kinds, ordinals, grants, old wire formats and immutable historical receipt order. Complete and qualify the retained source-only supplement. Phase 8 retains its necessary failure-first validation and cost-reduction delivery. |
| P22-03 | Resolve source-history normalization failures involving several managed comment blocks through the existing rule and public result-collection/recovery owner. Preserve original dates/authorship, actual successful prefixes and failure/UNKNOWN history. A normalization failure must not become an unexplained perpetual repair loop or a reason to rerun successful product checks. |
| P22-04 | Finish extended connected qualification of the transferred stabilization features, including real current-runtime construction, stale/crossed provenance refusal and same-Session continuation. Retain Phase 8's bounded checks and actual instructions/cost observations at their own scope; do not move its required acceptance wholesale here. Close with bounded residual dispositions rather than recursive assurance work. |
| P22-05 | Resolve or explicitly disposition every remaining unverified part of original Phase 8 completion rows 1–30, using the preserved per-row requirement/evidence/residual inventory. Retain bounded successes at their actual scope, including fourteen checks and FULL 2,980 tests / 284 suites. Do not claim all thirty criteria passed or invent comparative cost/provider usage. Preserve separately allocated Phase 9, 13 and 11.2 ownership. This is successor assurance, not a condition for the approved Phase 8 release. |
| P22-06 | Reconcile the accepted FORMAL release with retained native management state through existing public owners. Provide an explicit disposition for unresolved issue carryover, including original issue 8 and failed extended qualification ordered-validation:1918, without treating declined/not-reviewed as resolved or manufacturing PASS. Preserve the native independent review's decision-needed outcome, Session, Plan, accepted Steps, failures/UNKNOWNs, repair count 10/10 and successful results. Avoid re-Start, accepted-result replay, duplicate commits/effects and private-state edits. A valid approved NOT RUN release remains consumable by successors. |

The operational requirement is successful execution under the current supported
conditions when an appropriate environment can be built. Preserving a historical
condition is not itself the objective. Historical adoption remains evidence about
its original subject; a reconstructed runtime needs current evidence without
pretending that an old target-specific answer was a new Human answer.

## Preserved implementation and qualification boundary

The starting material is the released Phase 8 work, including its canonical
source-succession/artifact-applicability and qualification execution-order
supplements. The final bounded native validation completed Compile, TestCompile,
eleven selected connected batches and FULL successfully. Earlier source-only
observations describe an intermediate state. Generalized reconstruction,
historical ordering robustness, extended artifact replacement and the remaining
per-row conditions are still unverified. Assess each retained result at its actual
scope rather than assuming every connected scenario passed.

The original Phase 8 native Session is
`a38c308c60a36de8c7b33336ca8641c7`, Plan
`phase-8-runtime-practical-closure-20261117@1`. Its final retained repair accounting is
10/10 and its actual native state is `Human`, with issue 8 `OPEN/UNRESOLVED`.
The independent Step review returned `decision-needed`; no integrated Phase
review or native Phase terminal transition is claimed. This transfer neither
refunds that allowance nor authorizes an eleventh
attempt, re-Start, accepted-result replay, private-state editing or replacement
of the old consumer Session. Future Phase execution has its own normal admission
and keeps this origin/history attributable.

Operational artifacts stay under
`~/backup/work.d/YYYY/MM/DD/sm-workflow/<target>/`; they are not stored in
`docs/journal`. The [Phase 8 evidence map](../notes/phase8-requirement-evidence-map.md)
and [qualification design](../design/phase8-qualification-connection.md) retain
the original requirements and the distinction between authored and verified work.

## Delivery method

Select one coherent failing path. First establish its end-to-end isolated
behavior using the normal runtime-owned build/publication/validation route;
then run required broader checks and independent review. Reuse existing owners
and attributable results. No second resolver, repair controller, execution
lifecycle or approval gate is introduced by this plan.

The already accepted Phase 8 work remains usable at its actual accepted scope.
This Phase is not a retrospective completion condition for earlier delivery.
