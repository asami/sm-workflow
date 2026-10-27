# Phase 6.7: Ad Hoc Task and Standalone Fix Delivery

Status: Release repair 4 fixture correction prepared; fresh repository-full and affected PhaseReview pending.

Latest observation (Request110): actual FULL1232 selected all six local native
cases and ran 196 suites/1936 tests, with 1935 passed and one terminal-reference
fixture failure. Five of the six native cases passed, including controlled failed/
UNKNOWN intent and empty-commit protection. The failing case incorrectly replayed
a historical mechanical save after Completed. Release repair 4 retains the valid
initial active intent and uses typed retained checkpoint collection plus ordinary
duplicate Advance, preserving exact payload/count/commit assertions. No production
guard changes. Continuation1235/expected1236 retains Plan2, Phase4/10; required
issue8 is REOPENED and issue11 OPEN. Both FIXED semantic claims await fresh normal
FULL and affected review. Prior1195 success and1232 failure remain unchanged;
earlier six-unexecuted statements below describe the preceding handoff. Checklist
acceptance remains pending; no physical timeout is asserted.
Planned: 2026-10-26 (Asia/Tokyo)
Owner: sm-workflow development
Predecessor: consume the qualified Phase 6.5 execution and Phase 6.6 diagnosis paths
Checklist: [P67 acceptance](phase-6.7-checklist.md)
Specifications: [GoalTask](../spec/goal-task.md), [standalone FIX](../spec/standalone-fix-acceptance.md)
Handoff: [delivery allocation](../journal/2026/10/2026-10-26-phase-6.7-task-fix-priority-handoff.md)

## Current Release repair 3 status (Request109)

Actual repository-full1195 passed 195 suites/1930 tests. Independent affected
PhaseReview1198/1199 resolved issues9/10/12 and retained issue11: the six new
`BoundedWorkNativeOutcomeSpec` cases were excluded because their declaration was
RuntimeTestDeclaration (FOCUSED/HEAVY). These cases use owned SQLite/File/Git,
without external services or nested SBT, and now declare LocalTestDeclaration
(FOCUSED/FULL). Global classification and build rules remain unchanged.

The actual repair continuation1200/expected1201 retains Plan2 and Phase
automatic3/ordinary3/limit10. Only issue11 is open. Its FIXED semantic claim awaits
normal FULL execution of the six cases and affected PhaseReview; no six-case PASS
is attributed to validation1195. Controlled failed commit/UNKNOWN-intent fixtures
remain distinct from an actual physical timeout. All checklist boxes remain
pending formal acceptance, with the eleven conditions and accepted history intact.

## Historical Release repair 2 handoff (Request104)

Plan2 retains completed T1–T4, including T4 commit `775b1ded`. The current
whole-Phase repair is continuation1163/expected1164, automatic2/ordinary2/limit10.
The first independent Phase review satisfied P67-01/02/06/07/09/10/11 within its
recorded scope and requested four corrections for P67-03/04/05/08. Those findings
now have source corrections and new public qualification evidence; their FIXED
semantic dispositions await fresh native full checks and formal PhaseReview.

Publication105 activated image `57ace7b8`. A new journal-derived Task completed
normal source adoption, implementation, integrated review findings, same-Task
repair1/10, fresh Compile plus four passing tests, independent affected acceptance
and selected commit `a82c704eb18ee46081bc575124be2c5cfaec9552`. Its three owned files
were committed while tracked unrelated notes retained their exact dirty bytes.
This is a deliberately seeded square example, not a production defect. A separate
new Task reached public NoChange after Compile plus five passing tests and
independent acceptance, with no commit, counts0/0, unchanged HEAD and exact
unrelated untracked bytes. Both terminals retain five Evidence references without
a projection warning. Public Evidence read and Journal find also establish the
journal example's stored raw focused output and three storage observations.

The shared Task/FIX adapter now translates confirmed outcomes to Common Evidence's
passed/failed vocabulary, retains terminal references, and reports archive
conflict/indeterminate and Journal availability truthfully. Six authored native
regression cases remain unexecuted. Controlled failed/UNKNOWN checkpoint cases
are test designs, not observed physical timeout or live failure claims. The
[handoff](../journal/2026/10/2026-10-27-phase-6.7-t4-source-handoff.md)
records exact public artifact paths and remaining qualification. Checklist boxes
remain pending formal acceptance; the eleven original conditions are unchanged.

## Purpose and priority

Enable an authorized development or repair request to be delivered to another
existing chat as one coherent outcome. The destination uses sm-workflow to own
validation, repair progression, acceptance and selected closure; the requesting
chat receives a usable result instead of coordinating each edit or test.

Promote the high-priority single-repository parts of the previously unnumbered
[GTASK](../notes/goal-task-delivery-plan.md) and
[SFIX](../notes/standalone-fix-delivery-plan.md) plans here. Those IDs retain their
meaning and evidence; this is allocation, not duplicate implementation.
[Phase 17](phase-17.md) owns their remaining expansion.
Pull further bounded items from Phase 17 only when actual use needs them, with an
explicit plan/checklist transfer and retained evidence. Do not make its whole
backlog a prerequisite or silently expand currently executing work.

## Main route first

| Unit | Reused plan scope | Connected delivery |
| --- | --- | --- |
| T1 | SFIX-1/2 | One sm-test-fix request consumes a real failure, diagnoses/repairs it, runs focused checks and returns accepted uncommitted changes; conditional review only |
| T2 | GTASK-1/2 | One sm-goal-task request from a journal or direct instruction develops one coherent work unit, completes integrated independent Task review and actual local commit |
| T3 | SFIX-3 finding input | sm-review-fix consumes identified findings through the same execution path; retain finding-to-change-to-validation references |
| T4 | GTASK-4 / SFIX-4 initial delivery | Package/deploy the three thin skills and public contracts; prove an actual authorized separate-chat request, destination execution, result return and requesting-chat adoption |

Deliver T1 and T2 end to end before broadening input formats or selection.
Connect caller/runtime packaging as each route becomes usable, not only after
all internal implementation. No Phase files or numbered Phase are required for
a Task or standalone repair. Reuse existing WorkOrder/Continuation and runtime
SBT, typed Git, diagnosis, conditional assessment and Journal/Evidence.
Do not build a second executor, repair controller or chat service.

## Separate-chat request and result boundary

Use existing authorized chat delivery. Carry the objective, Component/repository,
acceptance conditions, permitted operations, source/decision references, current
work and applicable validation evidence. Distinguish the requester from the
destination execution owner. File inventories describe work, not automatic
per-file consent gates; use the existing purpose-based admission and authority
registration route. Real scope/effect changes retain their Human boundary.

The destination owns its standalone Task/fix execution. It returns actual changed
scope, validation/review disposition, applicable commit references, remaining
issues and consumption instructions. The requester retains its own Session and
adopts the result through its supported route; a destination success does not
automatically complete the requesting Phase. Reuse applicable evidence without
unconditionally repeating it.

Existing Phase repairs stay in their current Session; do not wrap each issued
repair in another standalone Task. Independently authorized producer delivery
may be a standalone Task while the consumer Phase retains its own history.
No new chat is required if a suitable authorized delivery chat already exists.
No polling, AI status relay or approval loop is introduced for display.

## Acceptance policies

[Phase 7 dependency delivery](../spec/dependency-development-delivery.md) composes
these single-request routes with fixed/chat/subtree modes, FIFO and consumer
adoption. Its assigned-chat request explicitly selects local commit and publishLocal,
including for sm-fix; larger Task/Phase delivery adds best-effort repository sync
after publication. This does not change standalone fix's no-implicit-commit
default or make Phase 7 queue/publication integration a 6.7 completion gate.
Phase 6.7 retains its already selected operations and evidence; Phase 7 qualifies
the composed delivery without reimplementing the execution engine.

- sm-test-fix and sm-review-fix: one Step-equivalent repair request, actual
  focused checks and applicable lint, deterministic assessment, conditional
  independent review, no implicit commit.
- sm-goal-task: the same inner execution with one Task-wide independent
  integrated semantic review and actual scoped local commit. No duplicate
  Phase/Release review. Push/publication requires its own existing authorization.
- Compile failure retains affected tests as not executed. Compile-only or empty
  selection cannot establish successful behavioral validation.
- Reuse runtime convergence and the ten-batch policy with its existing counting
  windows and explicit human restrictions. Registration, information requests and
  changed callers reset no counters. Unknown effects are observed, not replayed.
- Public adoption of existing authority and supported commit registration are
  part of the main route; successful tests are not a substitute for authority.

## Preservation and completion

Complete the P67 checklist with actual source and installed-route evidence.
Preserve ongoing 6.6 and all existing Sessions, accepted work, history, counts
and success/failure/unknown results. Planning starts no Goal or runtime Session.
Do not migrate current work or rerun valid checks solely to adopt 6.7.
Needed producer gaps are bounded corrections, not a reason to finish all later
Phases first. Initial delivery has no Phase 7/9/14/17 completion dependency.

Phase 17 retains multi-unit composition, wider inputs, dirty/related-repository
selection and investigation integration. Phase 7 owns workspace/artifact support,
Phase 9 provider/model optimization, and Phase 14 investigation itself. CBD and
LLM evaluation are not added to 6.7.

## T2 source boundary

[GoalTask runtime](../spec/goal-task-runtime.md) connects journal/direct input,
shared bounded execution, integrated review and selected local commit. Selected
native validation and installed Task effects are owned by the runtime; this
source handoff does not close any phase-scoped acceptance condition.

## T3 source boundary

[Review-origin standalone FIX](../spec/standalone-review-fix-runtime.md) adds
original finding input and attributed dispositions through the shared T1/T2
engine. Selected native validation, installed skill delivery and actual Phase
acceptance remain owned by their later runtime boundaries.

## Historical T4 source delivery boundary

[Task/FIX delivery](../spec/task-fix-delivery.md) defines the three packaged thin
adapters, passive actual-result attachment and explicit requester disposition.
The [English handoff](../journal/2026/10/2026-10-27-phase-6.7-t4-source-handoff.md)
records public setup, compatibility and actual-evidence gaps. Adopted Plan2 added
the authored CAR ABI inventory to T4's ownership; its ordinary publication and
dedicated activation retain the active Phase's separate qualified runtime pin.
No installed availability, real Human direction, destination execution or
requester acceptance was established by those source fixtures. Subsequent actual
public observations are recorded in the current status above; checklist closure
remains subject to formal PhaseReview.
