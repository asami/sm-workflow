# Review-origin standalone FIX runtime contract

Date: 2026-10-27 (Asia/Tokyo)
Status: P67-T3 source entry prepared; native validation and installed acceptance pending

`StandaloneFixService.startStandaloneReviewFix` accepts one adopted review-origin
repair request. It uses the existing `StandaloneFixWorkflow`,
`advanceStandaloneFix`, `submitStandaloneFixWorkResult` and diagnostic information
operation. `NativeWorkflowPreparationService.prepareStandaloneReviewFixStart`
passively prepares its normal selected-input import; this grants no effect or
independent reviewer authority. No second Session is wrapped around each repair,
and no Task/GoalPhase is created to obtain acceptance.

`StandaloneReviewFixInput` contains the ordinary standalone repository/request
identity, objective, owned paths, selected checks, conditions, source references,
review policy, code/documentation kind, Scala lint policy and repair limit. It
also adopts `originalReview`, `reviewContext` and ordered `originalFindings`.
Each original finding has its identity, original summary, original affected paths
and source references. Finding identities must be unique and original paths stay
within adopted request ownership. Complete-input selection freezes these facts;
subsequent source edits do not silently change the active request. Original review
and source references travel in the native WorkOrder context and remain distinct
from a later independently admitted affected-review result.

The shared `BoundedWorkNativeRun` observes the actual initial candidate and issues
ordinary bounded Repair work from the identified findings; an artificial failing
test is unnecessary. The original findings enter the existing repair-control
window. Semantic repair, typed runtime validation, assessment, limits and common
Evidence/Journal remain the T1/T2 owners. An existing Phase repair stays in that
Phase rather than entering this independent request to reset its obligations.

A review-origin `StandaloneFixWorkResult` includes `reviewDispositions` with
exactly one entry for each original finding, in adopted order:

| Field | Meaning |
| --- | --- |
| identity | Original finding identity; no substituted or omitted finding |
| outcome | `fixed`, `not-applicable`, or `unresolved` |
| basis | `mechanical` or `semantic` |
| rationale | Concrete reason supporting this disposition |
| changedPaths | Actual repair paths attributed to this finding, within request ownership |
| checks | Existing selected logical check identities that verify this finding |

A fixed code finding requires attributed changes and a selected behavioral check.
A not-applicable finding has no attributed changes and retains its explicit
reason. Unresolved meaning stays unresolved and requires scoped review. The union
of changed paths must equal the observed candidate delta. Native assessment links
check identities to actual successful selected receipts for that candidate;
semantic claims of check success cannot supply those receipts. An unattributed
candidate reaches Human with its existing evidence/counts retained, without a
successful disposition. Compiler failure keeps remaining tests not executed;
UNKNOWN execution never closes findings or grants effect replay.

When dispositions have mechanical bases, all adopted conditions and the required
actual check batch pass, and no explicit review or unsettled meaning remains,
mechanical assessment accepts the standalone repair without an AI review or
waiver call. A semantic basis, unresolved finding, explicit review requirement or
other unresolved question requests the existing independent scoped review. Its
actual evidence binds the assigned input/candidate and a worker different from
all recorded repair authors. An accepting review must settle every original
finding and retain valid actual change/check attribution.

Affected review covers original findings, repair delta and affected meaning.
Original whole-repository review is not repeated. Review findings return to the
same Repair work and counting window; prior review/candidate and applicable
successful checks remain retained. An unchanged candidate can reuse its complete
successful check batch. New code changes require the shared native validation
path; no caller SBT command, repair runner, private-store update or new ledger is
introduced. Optional new source/result/state fields preserve old standalone and
Task payload decoding; accepted work is not replayed to adopt this input.

Accepted review-origin repairs remain uncommitted. An explicitly reasoned
not-applicable/no-change disposition is still recorded against the original
finding; it is not confused with an initially passing test-only no-repair result.
No implicit commit, push, publication, integrated Task review or Phase review is
added. Existing source/operation admission and host-bound physical paths still
apply; actual scope/effect expansion uses its ordinary Human boundary.

`StandaloneReviewFixSpec` supplies bounded fixture-owned disposition/check cases
and a public Start/Advance episode with isolated typed Git observations and no
SBT binding. Those specifications do not prove installed repair success,
independent review provenance, skill deployment or other-chat delivery. The owning
workflow runs the selected native checks. Phase acceptance and installed skill
qualification remain later observations and no checklist box is closed by this
source contract.
