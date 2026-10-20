# Phase 6.3: ordinary-use execution overhead and development handoff

Date: 2026-10-18
Status: adopted development plan; no implementation or production acceptance claim

## Decision and incident

The user assigns this additional work to Phase 6.3 because Phase 6.2 is already
in development. Preserve 6.2's plan, implementation, issued work and verification
results. Integrate the follow-up into normal production-use acceptance rather
than add another Phase or verification campaign.

The [asamiair Core Phase 3 incident](https://github.com/asami/sm-workflow/blob/main/docs/journal/2026/10/2026-10-18-core-phase3-goal-phase-execution-overhead.md)
records five SBT wait counters totaling 100 minutes 30 seconds, versus reported
task times totaling 95 seconds. The counters are sampled, not exact wall-clock
measurements; task time excludes some startup/orchestration. Shared queue waits,
repeated small repairs, unnecessary representation assertions, disproportionate
delegation and a mistyped suite caused overhead. Independent review also found
a genuine HOCON large-integer defect: meaningful review must remain.

## Assessment at adoption

Source inspection used local sm-workflow HEAD `63ae6d8` and its working tree,
with existing Phase 6.1 acceptance records. Uncommitted successor work is not
promoted to accepted delivery. No new product test or cost comparison was run.

| Area | Established evidence and remaining limit |
| --- | --- |
| Runtime commands | [Typed SBT/Git execution](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowManagedProcessExecution.scala) already exists. Selection is bound by configuration; this removes repeated AI command reconstruction, not shared-resource contention. Configured suite-name errors can still occur. |
| Proportionate delegation | [Native execution specifications](../../../../src/test/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeExecutionSpec.scala) and the [skill contract](../../../../skills/sm-goal-phase/SKILL.md) permit Routine INLINE work while retaining independent review. They do not automatically optimize batch size or prevent every poor delegation choice. |
| Reuse | [Native validation](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeValidation.scala) retains same-candidate results. [6.1 acceptance](../../../phase/phase-6.1.md#current-acceptance-state--2026-10-18) records repeated Advance without another SBT or commit. This does not establish selective reuse after a product edit. |
| Timing | The inspected [validation receipt](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/RepositorySyncNativeMergeValidation.scala) does not expose queue/startup/test/AI timing breakdown. Do not infer a measured reduction from runtime ownership or test counts. |
| Continuous progression | The host owns Submit display and the next explicit Advance. The [native caller](../../../../scripts/native-goal-command.py) performs one selected operation per invocation; that alone is not installed host-loop proof. 6.1 explicitly leaves installed-use acceptance to 6.3. |

The conclusion is partial implementation and bounded acceptance, with remaining
ordinary-use integration work. It is not a claim that sm-workflow reproduces all
the old skill failures, nor that the incident has already been eliminated.

## Instructions for the sm-workflow development chat

1. Continue the current Phase 6.2 execution under its adopted contract. At the
   next ordinary planning boundary, note this as a successor 6.3 handoff only.
   Preserve Goal/base/PLAN epoch, requests, counters, finding/repair history,
   accepted commits, tracked/untracked and generated changes, and all actual
   success/failure evidence. Collect in-flight results under their issued scope;
   do not retarget workers, reset the Goal, reopen accepted work or repeat checks
   merely to adopt this document. Do not copy new obligations into 6.2.
2. When 6.3 starts, reconcile actual 6/6.1/6.2 delivery against
   [P63-USE01..03](../../../phase/managed-test-suites-phase-checklist.md#phase-63-production-use-acceptance--2026-10-18).
   Reference applicable existing evidence and record only missing connections in
   the existing plan. Update remaining estimates from those gaps; the earlier
   six-hour estimate is not new evidence that this integration is already done.
3. Follow the [cost-first order](2026-10-18-production-cost-first-execution-order.md):
   ready SplitPhase output, single-repository RepositorySync, then missing limited
   GoalPhase connections. A separately blocked skill must not stop ready work.
   Connect the installed host loop with the first affected route: accepted Submit
   -> actual message/outcome display -> next explicit outer Advance, without a
   human continuation prompt or a model turn whose only job is continuation.
   Preserve genuine decisions, rejected/uncertain results, Wait and Terminal.
4. In USE03's ordinary development/repair example, deliver implementation and
   executable specifications together. Correct known related assertion/minor
   defects as a batch before the next selected run; use INLINE where admitted
   and retain required independent review. Assert adopted semantics rather than
   incidental renderer order/whitespace. Runtime selects configured typed checks
   and collects actual results; semantic workers do not rebuild command strings.
   Useful early feedback for a real uncertainty remains appropriate.
5. Reuse same-candidate results without duplicate effects. After edits, explain
   affected selected checks/review and retained evidence in the existing handoff
   or result. A changed product/input must receive required affected validation;
   a label or procedure-only repair does not invalidate unrelated results. Do
   not add a selective-reuse engine, hashes, TTLs, copied-state comparisons or
   a per-repair proof ledger. Classify procedure failures separately from product
   test failures and repair only the actual deficient connection.
6. Use those normal acceptance runs for feedback: available SBT invocation,
   semantic delegation and continuation-only interaction counts; queue wait,
   startup/build, test and AI/orchestration durations where observed. State the
   source, overlap and unknown intervals. Carry already available facts through
   existing results if needed; do not build a telemetry subsystem or run tests
   only to collect timing. No numerical savings threshold or complete timing
   decomposition blocks release; do not claim measured savings without data.
7. Reconcile these results within ordinary integrated review/Final and the
   existing single aggregate full validation. Reuse common-loop/failure evidence
   across family routes instead of multiplying scenarios. Keep USE01's actual
   authorized push/convergence, USE02's usable split plans and USE03's actual
   local development/closure. A genuine supported-route gap remains 6.3 work.

## Completion and retained boundaries

The [6.3 plan](../../../phase/phase-6.3.md#execution-overhead-in-ordinary-production-acceptance--2026-10-18)
and existing USE01..03 rows now include these details. They remain unaccepted
until actual owner acceptance; no new completion ID, review cycle or mandatory
per-repair report is introduced. Supported use remains ordinary split-phase,
ordinary single-repository repository-sync and limited single-repository
goal-phase. Phase 7 retains related-project management, 6.4 evidence archival,
and Phase 10 CBD integration. None becomes an initial-use prerequisite.

The aggregate sequence remains PHASE-6/6.1/6.2/6.3 with PHASE-6.3 as its sole
full-validation owner. Required review, meaningful coverage and effect authority
remain. Automatic optimal batching, new schedulers and shared-resource contention
elimination are not completion conditions. Product execution policy is distinct
from the current shared development procedure: keep the required serialized SBT
runner; this plan authorizes no wrapper bypass or change to active commands.

This update edits only the 6.3 plan, shared checklist's 6.3 section, roadmap and
this handoff. It does not edit 6.2, product/test code, private workflow state or
acceptance results, or execute tests, deployment, commit, push or another chat.

## Review policy addendum — 2026-10-18

The user additionally adopts bounded re-review, integration-oriented Phase
Review and strict Final evidence reuse in Phase 6.3. Test-tier selection and
review scope are separate concerns; no new test or review tier is introduced.
The [6.3 review acceptance policy](../../../phase/phase-6.3.md#review-scope-and-reuse-acceptance--2026-10-18)
and existing shared checklist now make the intended behavior observable.

Current native progression issues `ReviewStep` again after repair/validation,
and issues `ReviewPhase` for the Phase. Those labels alone do not ensure bounded
re-review or integration-oriented review. The accepted 6.1 independent-review/
Admission/commit connection remains valid in its original scope; it is not proof
that all requested review-scope and Final-reuse behavior is already accepted.

Instructions for the development chat:

1. Keep active 6.2 work and its plan/results unchanged. Adopt this successor
   requirement at the next ordinary planning boundary without restarting tasks,
   retargeting issued work, changing counters or reopening accepted deliveries.
   When 6.3 begins, identify only missing connections in its existing plan.
2. Prepare each repair re-review with the previous review/findings and their
   dispositions, the repair diff, affected requirements/interfaces, relevant
   validation and sufficient surrounding context. Ask for finding resolution and
   affected regressions only. Preserve applicable approval elsewhere. A narrow
   diff may have wider semantic impact; include that impact with a concrete
   reason, without automatically returning to whole-Step/Phase exploration.
3. Prepare Phase Review with accepted Step results and the integrated Phase
   requirements/Usage Contracts. Review connections, cross-Step assumptions,
   uncovered requirements, duplication, excessive checking and architecture.
   Retain the comprehensive integrated review; do not repeat each Step's local
   review or ignore genuine newly discovered integration blockers.
4. At Final, consume applicable review/validation, findings, repair deltas and
   release prerequisites. Sufficient evidence must lead to closure without a new
   semantic AI review invocation. A genuine missing or changed requirement gets
   only the affected additional review, with the reason in the existing result.
   A new stage/commit/reviewer or elapsed time alone does not invalidate approval.
5. Prove this in the existing USE03 and ordinary integrated acceptance route:
   bounded repair -> affected independent re-review -> integration-oriented
   Phase Review -> Final evidence reuse/closure. Inspect actual issued materials,
   results and review invocations, not only prompt wording or state labels.
   Reuse applicable evidence for the uncovered-gap branch; fill actual gaps with
   the main-route implementation. Do not create a separate per-finding review,
   scope-certification reviewer, artificial production defect or test campaign.

Related minor repairs remain one coherent batch where appropriate. Independent
review remains required; reducing scope is not permission to waive it. Real
changed-contract/impact/coverage gaps can expand the affected review, with their
reason recorded normally, rather than forcing a blanket rerun. No hashes, TTL,
copied-state proofs or new applicability ledger are introduced.

The existing aggregate full-validation owner, three use tiers and Phase 10 CBD
allocation remain unchanged. This addendum changes only the 6.3 development plan,
its existing checklist detail and this journal; it records no implementation,
review success, test execution or live-state modification.

## Compile/test guarantee reuse addendum — 2026-10-18

The user adopts compilation, selected tests and applicable existing review as
combined admission evidence. Do not ask AI to reconfirm their established
guarantees. A repair with no remaining unconfirmed semantic judgment must proceed
without an additional review. This decision supersedes the earlier addendum's
unconditional "independent review remains required" wording for minor repairs;
it retains independence when new semantic review is actually required.

Scala compilation mechanically establishes conditions formalized by its type
system/compiler within the actual build scope. Incremental compilation preserves
that role through dependency-aware reuse/recompilation. Admission/focused test
selection does not imply that only the selected suite's source is typechecked.
Retain the actual project/configuration/dependency scope: this is not evidence
of unselected behavioral tests, excluded configurations or unbuilt consumers.
Tests and prior semantic review each retain their own coverage and provenance.

Instructions for the development chat:

1. Preserve active Phase 6.2, its plan/issued work/counters, all implementation
   and validation/review results. Adopt this additional behavior only in 6.3;
   do not retrofit it into the ongoing 6.2 run or retroactively change acceptance.
2. At the existing 6.3 gap assessment, inspect the native repair -> successful
   checks -> ReviewStep path. Plan the minimal integration with existing
   requirement/finding scope, repair context, actual validation and Admission
   so covered repairs do not issue another semantic review. Prompt narrowing
   alone cannot reduce the invocation count while this path remains unconditional.
3. Reuse compiler-established static conditions, selected-test behavior and
   applicable prior semantic decisions. For an already settled repair/finding,
   consume its actual completion evidence. If that covers its acceptance
   conditions and leaves no unconfirmed semantic question, proceed to ordinary
   Admission/closure without a review request. If meaning/impact remains
   uncertain, request only that affected independent review. Do not infer
   coverage from a triviality label, physical line count or compiler success
   alone; an author claim is not evidence of fulfilled conditions.
4. Record the applicable existing evidence and fulfilled conditions in the
   ordinary admission result. Retain original review target/provenance; never
   manufacture a new reviewer PASS or retarget old approval to changed code.
   Current-candidate and actual commit authority still apply. Do not add a
   separate AI review to approve skipping review, a hash/TTL/copy certificate,
   another evidence ledger or a parallel acceptance engine.
5. Within existing USE03 and integrated acceptance, show a covered repair
   reaching Admission/closure with zero additional semantic review requests/
   invocations, and a repair with an outstanding semantic question receiving
   affected independent review. Reuse applicable cases/results and fill actual
   connection gaps; do not create a separate test campaign. No initial review,
   Phase integration review or full-validation obligation is removed merely by
   classifying tests as admission/focused.

Phase Review continues to use Step evidence for integration and Final continues
to reuse sufficient results without new semantic review. No producer internal
guarantee is redundantly reviewed by the application. Ordinary test selection,
aggregate full-validation ownership, the three production-use tiers and Phase 10
CBD allocation remain unchanged. This is a plan/checklist/journal update, not
implementation or evidence that the review-omission path already works.

## Step/release review batching and AI cost — 2026-10-18

The user makes the normal review unit explicit: ordinary local corrections,
including typical one-line/mechanical fixes, receive no individual review.
Consolidate business/semantic review at Step completion and release. This also
addresses AI invocation and repeated-context cost. This latest decision refines
the preceding addenda: do not replace per-edit review with a per-edit decision
or AI review about whether review can be omitted.

Instructions for the development chat:

1. Leave active 6.2 and all current implementation, validation, review and live
   execution records intact. Adopt the successor policy in the 6.3 remaining
   work plan at the normal boundary; do not restart or reinterpret accepted work.
2. Keep ordinary edits and compiler/test corrections inside the same coherent
   Step implementation/repair batch. Compile and run required selected tests as
   appropriate, then continue work without individual review or an omission
   certificate. Do not create a Step or completion handoff for each small edit.
   One-line business-sensitive changes still need applicable tests/authority,
   but their semantic questions normally join the Step closing review.
3. At Step completion, review the accumulated business behavior, requirements,
   contracts and meaning once through the existing independent review route.
   Reuse compiler/test guarantees rather than ask AI to check them again. Do
   not introduce a new AI classifier or reviewer to approve each local edit.
4. If that review finds issues, batch related fixes. Where existing review and
   actual compilation/selected tests satisfy the settled completion conditions,
   continue ordinary Admission without another review. Otherwise consolidate
   outstanding semantic questions and affected regressions into a bounded
   closing re-review. Genuine blockers remain unresolved until actually handled;
   this is not an unconditional one-review limit or permission to fabricate PASS.
5. At release, use the existing Phase Review for business completeness and
   integration, consuming accepted Step results. Add no separate release review.
   Final reuses sufficient results and closes without a new exploratory AI call;
   an actual new gap receives only the necessary affected work.
6. In existing USE03/integrated acceptance, show local corrections advancing
   within a batch without review/omission calls, one consolidated Step review,
   applicable evidence reuse or a bounded closing re-review, and release through
   the existing Phase Review followed by Final reuse. Retain actual requests,
   results and available invocation/token/cost facts in the ordinary evidence.
   Do not invoke AI or rerun scenarios solely to measure cost; make no unmeasured
   monetary-savings claim or numerical release threshold.

The [current plan](../../../phase/phase-6.3.md#review-scope-and-reuse-acceptance--2026-10-18)
and existing checklist reflect these boundaries. Integrate the minimal batching/
progression change with existing runtime/Admission rather than add another engine.
Ordinary test tiers, aggregate full validation, current effect authority, accepted
evidence, production-use tiers and Phase 10 CBD allocation are preserved. This
append records a planning change only; no product code, 6.2 plan, live state or
verification result is changed, and no test, review, commit or push is performed.


## Known uncommitted failure continuation — 2026-10-19

The user assigns this follow-up to Phase 6.3: a failure within already authorized
scope whose actual outcome confirms no commit was created must continue normally
without asking for a limited-recovery exception. Preserve active 6.2, the existing
Goal, history, repair counts and applicable validation/review evidence.

### Observed incident and remaining gap

The actual 6.2 P62-FIX attempt staged 117 paths and stopped at
`git diff --cached --check`: two formerly untracked new files had an extra EOF
blank line. The ordinary unstaged diff check had not included those files.
The commit result was `partial-failure` with no commit receipts. The bounded fix
record establishes no commit was created and two whitespace-only corrections;
shared `cncf-commit-execution` rules prohibiting post-start retry/index changes
then led to a Human-approved limited recovery. This was not a 117-path limit or
an exhausted repair budget. Source records remain under the existing local
`.codex-workflow/phases/PHASE-6.2/` names `fix-P62-COMMIT-EOF.md` and
`commit-P62-FIX-eof-recovery-plan.json`; the original result is referenced there.
This journal summarizes them without moving, rewriting or promoting evidence.

At inspection, [native Step closure](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeClosure.scala)
saves intent before typed Git execution and records a completed result after
success. [Continuation](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeWorkContinuation.scala)
pauses Closing when the completed result is absent. That does not establish a
normal path for a known no-commit failure. Normal repair continuation and successful
Step-commit evidence do not prove this particular branch accepted. Reconcile the
actual code delivered by 6.2 at 6.3 entry; do not overwrite concurrent progress.

### Instructions for the development chat

1. Continue active 6.2 under its adopted contract. This document does not change
   live Goal/base/Plan, issued requests, index, counters or results, authorize an
   old request replay, or replace existing shared-skill execution rules. Adopt
   the change in 6.3's existing remaining-work plan at the normal boundary.
2. In package 1, inspect actual typed Git outcomes and closure continuation. In
   package 3, connect the smallest missing path with USE03's local Step closure;
   fix a shared producer gap at its owner if an earlier main route needs it.
   Reuse accepted implementation and checks rather than add a recovery engine.
3. Distinguish a known no-commit failure from a created commit and an unknown
   result. Consume CNCF's outcome and ordinary Git observations at their owning
   boundary; neither a missing success receipt nor a failing exit code alone
   proves that no commit was created. Do not add a consumer proof ledger,
   management hashes or repeated validation of producer guarantees.
4. For a confirmed no-commit failure within the existing authority, correct the
   cause, update only the intended staging and perform a new normal commit
   attempt. Preserve unrelated staged/unstaged work. Reuse standing permission;
   no additional Human question, continuation-only AI call, blanket index reset
   or unstage-to-empty prerequisite. Retain earlier failures and the new actual
   result separately in the existing execution history.
5. If the applicable commit already exists, consume it and continue without a
   duplicate commit. If the effect remains unknown or ownership/target conflicts,
   observe and retain the pending state instead of blindly retrying. A genuine
   unresolved choice or scope/authority expansion still uses the existing Human
   path. Broad automatic replay or general interrupted-effect recovery is not
   added by this bounded normal failure contract.
6. Keep the same Goal/base/Plan/Step and complete history. Staging/commit retry
   alone neither increments nor resets/refunds/renews repair counts. Actual
   repair work follows existing accounting; accepted Step closure retains its
   normal counter transition. Reuse applicable validation/review; the incident's
   EOF correction does not itself justify another SBT or semantic-review call.
   Changed product meaning receives only affected checks and the existing
   consolidated review. Preserve evidence provenance; never manufacture PASS.
7. In the existing installed USE03 acceptance, exercise a new-file whitespace
   failure before commit, correction/staging update and exactly one successful
   local commit, then continue the same workflow with no additional approval.
   Observe preservation of unrelated work, history/counters and applicable results.
   Reuse prior cases for created/unknown outcomes and fill only missing coverage.
   Record the actual outcome and any remaining limitation in ordinary evidence;
   helper-only success is insufficient. Keep the existing aggregate full test
   and Phase Review/Final reuse; add no separate verification campaign.

The [Phase 6.3 plan](../../../phase/phase-6.3.md#known-uncommitted-failure-continuation--2026-10-19)
and existing [P63-USE03 checklist](../../../phase/managed-test-suites-phase-checklist.md#phase-63-production-use-acceptance--2026-10-18)
carry this acceptance obligation. The known no-commit branch is normal continuation
of an active workflow, not a requirement to abandon it and start a new Goal.
General recovery exclusions continue to apply to unknown/interrupted effects.
This append records a plan only, not implementation, runtime validation or Phase
acceptance. No 6.2 document, product code or live execution record is edited by
this planning update.


## Known unexecuted validation continuation — 2026-10-19

The user extends the preceding normal-continuation policy to validation: when
non-execution is established and validation target, operation and authority are
unchanged, preparation corrections and executor replacement require no additional
Human approval. Assign the missing implementation/acceptance to Phase 6.3;
preserve active 6.2, Goal, history, repair counts and applicable results.

### Incident and inspection

The actual validation98 decision recorded a runner error in the required request
retention procedure, before renderer, platform submission, SBT or Submit. The
shared contract did not admit automatic transfer of this unused authority, so a
one-case Human exception was requested for a new registered runner. The actual
record is `.codex-workflow/phases/PHASE-6.2/control.json`, decision
`p62-validation98-unused-transfer`; the retained `run.json` records the original
and successor runner with development counts 41/Phase0. These are historical
observations, not instructions to modify the live run or replay its request.

[Typed execution](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowManagedProcessExecution.scala)
already lets Runtime run selected validation without an AI reconstructing the
SBT command. [Native validation](../../../../src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeValidation.scala)
saves intent before execution and retains an intent without result rather than
launching again. The [host execution contract](../../../spec/native-host-execution.md)
permits a configured alternate for selected UNAVAILABLE/DECLINED outcomes;
that limited alternate is not proof of this known pre-launch correction path.
Reconcile actual 6.2 delivery at entry: the inspection identifies a continuation
gap, not completed product acceptance or a product test failure.

### Instructions for the development chat

1. Adopt this as successor 6.3 work at the ordinary planning boundary. Keep 6.2's
   live Goal/base/Plan/Step, issued work, implementation, counters and all actual
   results intact. Do not reinterpret its shared-skill permissions, replay an old
   request, reset state or reopen accepted Steps just to adopt this handoff.
2. In package 1 identify only the missing lifecycle/result connection. Implement
   with USE03's first selected validation in package 3, or an earlier affected
   shared route. CNCF/host owns launch and executor lifecycle; sm-workflow consumes
   those facts to continue validation. Reuse existing typed execution, retention
   and dispatch ownership; add no parallel authorization/recovery framework.
3. Consume actual execution-owner evidence of non-execution. Missing output or
   an intent alone is not sufficient. For confirmed unexecuted work, fix preparation
   or replace the executor under the same target/operation/standing authority,
   then dispatch normally without another Human question. Make the superseded
   dispatch terminal before its replacement can launch; do not allow both workers
   to execute. No new per-transfer AI reviewer, proof ledger or command rewriting.
4. Running work keeps its actual execution and result collection. Completed work
   supplies its actual applicable result. Unknown execution stays under observation
   without blind retry. A caller/observer change alone does not launch new work.
   Preserve actual platform permissions and serialization. Request Human judgment
   only for a genuine unresolved choice or a new scope/authority requirement.
5. Retain preparation failures honestly and preserve all existing results with
   their original scope. Preparation/dispatch fixes and executor replacement are
   not product repairs and neither increment nor reset/refund/renew repair counts.
   Product repairs and accepted Step closure retain existing accounting. Do not
   label an unexecuted test as failed/passed or repeat completed checks merely
   because an executor changed. No extra review is needed for the transfer itself.
6. In the normal installed USE03 route, exercise a confirmed pre-launch failure,
   corrected preparation/executor replacement, one actual selected validation,
   collected result and same-workflow continuation with zero additional approvals
   or continuation-only AI calls. Observe preserved Goal/history/counts/results
   and no competing dispatch. Reuse running/completed/unknown cases and fill only
   missing coverage; helper-only proof is insufficient. Keep existing Phase Review,
   Final reuse and aggregate validation without another campaign or work ID.

The [Phase 6.3 plan](../../../phase/phase-6.3.md#known-unexecuted-validation-continuation--2026-10-19)
and existing [P63-USE03 checklist](../../../phase/managed-test-suites-phase-checklist.md#phase-63-production-use-acceptance--2026-10-18)
include this obligation alongside known uncommitted failure continuation. Both
are normal continuation under standing authority; neither promises arbitrary
replay or general recovery of unknown effects. This is a planning update only:
no product implementation, active 6.2 change, runtime validation or acceptance is
claimed, and no live execution records are modified.


## Long Advance and runtime-owned SIGTERM handling — 2026-10-20

Latest user direction: do not modify the Phase 6.3 development plan or checklist.
Share this policy with the existing development chat **sm-workflow開発**, which
is already implementing the affected route. This journal is a coordination
handoff only; it neither reopens the closed Phase nor adds acceptance to its
existing closure. Preserve ongoing work, original Goal/base/Plan, all history,
repair counts, applicable verification results and existing completion records.

Advance may take an hour or longer. Remove skill/caller-owned fixed 60/240-second
execution cutoffs. Extending the caller deadline from observed SBT budgets still
leaves monitoring and termination in the wrong owner and does not meet this
latest decision. The normal caller waits for the actual response; waiting does
not require repeated AI invocations. Runtime owns execution deadlines, monitoring
and result collection. A transport/UI wait limit is not authority to kill the
underlying execution or start another Advance.

On an explicit interruption, deliver SIGTERM as a termination request to the
sm-workflow/CNCF execution owner. The owner stops scheduling new work, propagates
cancellation to active operations, confirms termination of its managed child and
descendant processes, and preserves logs, actual outcomes and Workflow interruption
state before exit. Grace periods and any required escalation belong to the
execution infrastructure, not a second timer in the skill helper. Merely killing
the caller or sending TERM/KILL to a process group does not establish completed
cancellation, descendant termination or durable state preservation. Signals apply
only to the owned execution, never unrelated user processes.

SIGKILL cannot run receiver cleanup. If termination or effects cannot be confirmed,
retain an unknown outcome without success or automatic replay. Connect to the
existing policy: confirmed unexecuted work can continue under unchanged authority;
running work is awaited, completed results are collected, and unknown work is
observed. Cancellation itself neither resets/refunds repair counts nor turns a
canceled validation into a product-test failure. Preserve prior evidence with its
actual scope and normal accounting for genuine product repairs/Step closure.

Development handoff: reuse ongoing implementation and applicable tests without
rollback. Align the caller, installed skill instructions and CNCF lifecycle at
their existing owners. Verify that a normal Advance can outlast 240 seconds and
support an hour or longer without a caller cutoff, and exercise actual SIGTERM
propagation, managed-descendant termination and retained cancellation/result state
through the connected route. Reuse existing cases; distinguish controlled-clock
or shortened-duration coverage from a genuinely measured hour-long execution.
No new framework, waiting-only AI loop or separate full-test campaign is implied.
Current SBT policy uses the registered runner and sbt-native boot/dependency/workspace
locks; this handoff does not introduce machine-wide serialization.

The user explicitly authorized notification of **sm-workflow開発**. The policy and
the instruction not to edit the 6.3 plan/checklist were sent to that chat
(`01a13249-14e0-7470-8144-f9af8145cec6`). Delivery is not a claim of adoption,
implementation completion or successful runtime validation. This chat edits only
this journal; product code, plans, checklists and live execution records remain
owned by their current work.


## Future candidate: SIGINT return of control to the skill — 2026-10-20

The user wants a future mechanism that interrupts sm-workflow with SIGINT and
returns control to the calling skill. Defer this feature to avoid mixing its
semantics into the current SIGTERM cancellation/cleanup work. No Phase is
allocated, and no Phase 6.3 plan/checklist, current acceptance obligation or live
execution state changes as a result of this record.

Current scope remains long-running Advance without a caller-owned timeout and
runtime-owned SIGTERM cancellation, cleanup and truthful outcome retention.
The preceding handoff's "explicit interruption" refers to that termination
request; it does not require a new SIGINT handback/resumption protocol now.
This deferral does not instruct removal of existing interrupt handling.

Future design must specify the interruption boundary, the returned outcome and
control context, treatment of in-flight effects/child processes, and whether/how
the same workflow may resume without duplicate execution. These remain open
questions, not present guarantees of suspension, recovery or automatic resume.
Preserve Goal/history/repair counts and applicable evidence in that design.
The development chat should record this candidate and continue its current work
without adding SIGINT implementation, tests or a release dependency now.


## JVM shutdown hook to CNCF termination — 2026-10-20

The user adopts the standard JVM shutdown hook as the entry to CNCF's existing
termination lifecycle. Append this direction to the development handoff only;
do not edit the Phase 6.3 plan/checklist or reopen its closure. Preserve ongoing
implementation, Goal/base/Plan, history, repair counters and applicable test/review
results. SIGINT return of control to the skill remains the future candidate above.

Use `Runtime.getRuntime.addShutdownHook(...)` to connect JVM shutdown to one
coordinated CNCF termination operation. For the normal supported JVM launch,
SIGTERM delivered to the owning JVM initiates its shutdown hooks. A launcher
must deliver the request to that owner; registration alone does not establish
signal forwarding through an outer process. Do not reintroduce a skill-owned
watchdog or deadline-based kill. Reuse the existing lifecycle/registration if
present instead of adding competing cleanup implementations.

CNCF owns the ordered shutdown: stop admission of new work, request cancellation
of active operations, confirm managed child/descendant termination, retain actual
outcomes and Workflow interruption state, flush logs and close resources. Keep
storage and logging usable until dependent result recording finishes. Hooks and
ordinary threads may run concurrently; coordinate cleanup with normal completion
and make repeated termination requests safe. Do not depend on registration order
of separate JVM hooks. Runtime owns bounded cleanup/grace and escalation, with
unknown outcomes retained when termination or effects cannot be confirmed.

The hook supplies an execution opportunity, not automatic business compensation,
transaction rollback or a guarantee that every cleanup finishes. Reuse existing
operation-specific cancellation/compensation where applicable; no general new
compensation engine is requested. SIGKILL cannot run a shutdown hook. Ordinary
`finally` blocks alone do not establish shutdown completion. Observe the actual
supported JVM configuration: `-Xrs` changes normal signal handling, so a hook
must not be assumed to receive SIGTERM under that configuration.

References: [Runtime shutdown API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Runtime.html)
and [Java launcher signal handling](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html#standard-options-for-java).
These explain the mechanism, not acceptance of the local CNCF integration.
Reuse ongoing SIGTERM lifecycle work and applicable tests; fill the actual
signal-to-hook-to-CNCF connection gap and report its observed outcomes. Do not
rerun unrelated validation or add an independent review campaign for this entry.
A shutdown hook ends in JVM termination; it does not implement the deferred
SIGINT protocol for returning control while preserving a running JVM. Existing
interrupt handling is not removed by this deferral.

This entry is a direction for **sm-workflow開発**, not a claim that implementation
or connected validation has completed. Only this journal is changed here; all
live execution and product changes stay with the ongoing development work.
