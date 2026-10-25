# Phase 6.6: Incremental SBT Failure Diagnosis and Repair

Status: planned
Planned: 2026-10-23
Owner: sm-workflow development chat
Predecessor: Phase 6.5; preserve ongoing Phase 6.4/6.5 work
Checklist: [Phase 6.6 acceptance](phase-6.6-checklist.md)
Specification: [incremental SBT failure diagnosis](../spec/incremental-sbt-failure-diagnosis.md)
Design: [diagnostic interaction](../design/incremental-sbt-failure-diagnosis.md)
Delivery detail: [D1 through D4](../notes/incremental-sbt-failure-diagnosis-plan.md)

## Next-delivery scheduling clarification — 2026-10-23

The [completion-first, best-effort sync correction](../notes/goal-phase-post-completion-sync-plan.md)
is the next independent delivery before D1-D4. It has its own GPSYNC checklist;
it does not expand P66 acceptance or require ongoing 6.4/6.5 to redo accepted work.
Deliver the local completion/safe-deferral route first rather than requiring
general dirty-worktree synchronization. Phase 6.6 remains planned and retains
its diagnostic scope and existing predecessor/adoption policy.

## Goal

Runtime control correction (2026-10-25): reuse the
[RRC corrective delivery](runtime-repair-control-correction.md) for ordinary
same-scope repair, convergence stops and the ten-cycle ceiling. Its immediate
Control Center restoration path is independent of D1-D4 and the sync correction.
Do not add a Phase 6.6 caller-owned retry/approval loop or require ongoing 6.4/6.5
to restart. D3 adopts the qualified common owner and preserves all prior results.

Deliver one normal workflow path from an actual SBT failure to structured
diagnostics, additional information requested by the AI, a concrete repair and
successful focused behavioral validation. Use information appropriate to each
decision instead of repeatedly sending complete logs. Preserve access to all
available evidence; no claimed cost-saving percentage is required.

Phase 6.4/6.5 continue with their existing repair procedures and the already
corrected caller helper. They do not wait for this Phase. Phase 6.6 consumes their
applicable accepted interfaces and results, including Step/Release FIX and
conditional review. Creating this plan starts no Goal or implementation run and
does not change an executing plan or acceptance policy.

## Completed baseline: caller size-limit removal

The fixed 8 MiB response rejection, 1 MiB stderr rejection and response read cap
have already been removed. The component script and the workstation-distributed
script were confirmed byte-identical on 2026-10-23; the installed sm-goal-phase
skill resolves that distributed helper. Response reading uses `stream.read()`
without the former fixed cap. Exit/JSON handling and raw output preservation remain.

Existing regression cases cover 9 MiB responses, stderr beyond 1 MiB and a real
child emitting 9 MiB response/2 MiB stderr with live events. These cases were
inspected, not rerun for this planning update. Preserve their actual existing
execution records; this document does not invent a new test PASS or certify a
separately packaged CAR. Removal is completed baseline work, not a Phase 6.6 task
or a new prerequisite for 6.4/6.5. Do not reintroduce duplicate caller ceilings.

## Development order: connect the main path first

| Unit | Deliverable | First connected outcome |
| --- | --- | --- |
| D1 | One actual retained SBT failure becomes an attributed diagnostic view backed by Common Evidence | AI receives useful assertion/exception/source/context details and truthful omission/availability information |
| D2 | Minimal typed additional-information request/result through existing issued-work continuation | AI requests several details together, managed reads return them to the same diagnosis context and Session |
| D3 | One concrete Fix Manifest enters the existing repair assignment and validation path | Actual focused behavioral tests pass and work returns to ordinary admission |
| D4 | Connected validation, normal delivery and consumer adoption instructions | Delivered contracts and caller projections agree; existing state and applicable results remain reusable |

Build D1 through D3 as one vertical slice before broadening extraction coverage.
Use retained failure evidence to develop extraction and an isolated representative
workflow to prove the connection. Do not finish a general parser, scheduler or
model comparison before the first repair works. Record unresolved format or
source coverage honestly rather than adding speculative infrastructure.

sm-workflow performs extraction, managed reads and work management. AI infers
causes, requests more context and proposes repair units. A repair worker receives
the concrete direction. Do not split automatically by failed test: many failures
may share one cause, and identical exceptions may have different causes.

## Compilation failure within SBT test execution

The main path explicitly includes errors in production or test-source compilation
triggered by `sbt test` or selected focused tests. D1 extracts the affected build
project/configuration when available, compiler message, file/line/column, relevant
source and supplied expected/actual type information. Missing fields remain
unknown. D2 can request declarations, call sites, generated interfaces or build
context needed to distinguish a root error from cascading diagnostics. AI groups
causally related compiler errors into a concrete D3 repair; do not assign a worker
per compiler message or require a new diagnosis engine.

When compilation prevents selected tests from starting, record compilation as
failed and those tests as **not executed because compilation failed**. Do not
report them as failed tests, canceled tests, passed tests or a successful empty
selection. An SBT nonzero exit alone does not establish this cause: use actual
compiler/execution evidence and retain unknown when execution cannot be determined.
For aggregate builds, retain any tests that did run and their actual results;
the not-executed disposition applies only to the affected selections.

After repair, normal compilation must succeed and the selected focused behavioral
tests must actually execute. Compilation success alone is not completion. If tests
then fail, continue the same diagnosis/repair path with the new test diagnostics,
retaining the original compiler failure and consumed repair counts. Verify this
branch in the same D1–D4 slice; it is not deferred to Phase 9. See P66-13..15.

## Acceptance and verification

Complete the [P66 checklist](phase-6.6-checklist.md) with actual evidence. The
representative connection must show failure → diagnostic overview → one batched
information round trip → one repair → focused-test success → existing admission.
Compilation alone or an empty test selection is not focused-test success.

Information retrieval and hypothesis clarification consume no repair cycle and
add no Human permission or independent-review gate within existing scope. Actual
repair assignments retain existing counting and allowance semantics. Reuse
unaffected results and the selected Step/Release review policy. Full/heavy tests
remain governed by the existing delivery requirements, not each information read
or local repair. Broaden validation only for an actual affected dependency or
execution-order concern.

## Dependencies, exclusions and preservation

The [standalone sm-fix delivery plan](../notes/standalone-fix-delivery-plan.md)
is a follow-on consumer of this diagnosis path and Phase 6.5 FIX. It provides
Step-equivalent acceptance outside a Phase, using focused tests and conditional
review. Its entrypoints/checklist do not enlarge Phase 6.6 acceptance or require
ongoing Sessions to migrate. Keep the shared diagnosis and repair path reusable.

Reuse CNCF typed SBT and managed File/Resource access, Common Evidence/Journal,
the caller continuation and the existing repair/admission path. Do not create
another storage engine, executor, diagnosis scheduler or approval framework.
If a specific producer capability is missing, identify that bounded dependency;
do not use shell/ProcessExecution to replace filesystem access.

Phase 9 retains convergence/provider switching and its existing automatic-Fix
limit. Phase 13 accounting is optional observation. LLM evaluation, automatic
parallel repair scheduling, universal log-format coverage and new convergence
policy are outside Phase 6.6. The baseline size-limit removal is also outside
its remaining work.

Preserve current source changes, Goal/Session/Step/continuation identities,
accepted commits, histories, repair counts and success/failure/UNKNOWN results.
Do not reset ledgers, replay accepted work, reclassify historical results or
reopen Phase 6.4/6.5/12 acceptance. Adopt the delivered capability at an appropriate
existing continuation boundary after issued calls finish. The sm-workflow-dev
worktree and its live work are not edited by this plan.

[English development handoff](../journal/2026/10/2026-10-23-phase-6.6-incremental-diagnosis-handoff.md).
