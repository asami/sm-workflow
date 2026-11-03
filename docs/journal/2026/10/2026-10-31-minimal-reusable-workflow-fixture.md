# Minimal reusable workflow fixture

Superseded classification decision (2026-10-31):
[ADMISSION and standard annotations handoff](2026-10-31-admission-standard-annotations-handoff.md)
replaces ON_DEMAND and blanket runnable-DoNotDiscover removal. Retain the decisions,
commits and verification below as history; do not reapply their retired labels.


Date: 2026-10-31
Status: initial deliveries reported; ON_DEMAND correction requested, implementation and validation pending.
Development owner: sm-workflow development.

Current intake decision: the user authorized a new linked correction Task after
the original Tasks returned Terminal/Returned. The successor handoff at the end
supersedes instructions below requiring correction inside the original request.

Terminology correction: the ON_DEMAND handoff below supersedes earlier uses of
"scripted" for ScalaTest suites. Those paragraphs retain the decision history,
not permission to hide runnable tests with `@DoNotDiscover`.

## Objective

Replace the product-source-copy fixture used by
`NativeDelegateTaskHumanRepairRecoveryCliSpec` with a small dedicated Scala/CAR
test project. The user explicitly selected reuse with case-state reset rather
than copying a project for every case. The user also confirmed that ordinary
workflow testing should not rebuild and republish sm-workflow itself.
The subsequent user decision excludes self-update from routine executable-spec
suites. Retain its reproducible procedure as an opt-in scripted scenario, for a
manual one-time confirmation or separately selected unattended execution. This
must not become a replacement mandatory self-update suite or development gate.

This is a new test-improvement deliverable, not reopening the accepted incident
repair. Preserve its Sessions, history, counts, successful evidence, accepted
closure and any still-owned execution. sm-workflow-dev remains paused.

## Observed coupling to remove from ordinary workflow coverage

In the current repair checkout, `NativeWorkflowCliFixture` prepares an
`application-source` copy of sm-workflow and copies its production sources,
scripts, skills, docs and build into each scenario. The five-case specification
then repeatedly builds and publishes that application. Its shared preparation
currently shares source inputs, not a reusable compiled test project.

The test also selects each newly published sm-workflow CAR as its runtime.
`DelegateTaskHumanRepairQualification` explicitly constrains its engineering
publication target to SmWorkflow. This is a self-repair qualification contract,
not a generic small-component publication contract. Merely replacing source
files would therefore not be a correct refactoring.

Keep the normal installed sm-workflow runtime fixed for the workflow fixture.
Use a genuinely separate fixture Component identity and artifact coordinates.
Do not label fixture output as SmWorkflow, synthesize publication receipts,
weaken runtime admission, or claim a fixture publication updated the runtime.
Use existing general Task/delegation/repair/publication routes for the ordinary
behavior. Remove the self-update scenario from the routine fixture, recording
that coverage as opt-in scripted. Existing bounded contract checks for runtime facts
can remain; they do not require real product rebuilds or publication. Do not
claim that ordinary Component publication proves runtime self-update.

## Reuse boundary

- Create one small suite-owned project directly from fixture resources: minimal
  build/CAR definition, one small Scala behavior, one controllable failing/passing
  test and the files needed for ownership/commit assertions. Do not copy the
  sm-workflow production tree or rebuild its runtime inside test cases.
- Run cases serially against that dedicated project. Keep dependency caches and
  compatible incremental compilation output; do not run sbt clean per case.
- Before the next case, restore test input and Git baseline and reset only that
  case's native Session/store/configuration/publication state. Do not operate on
  developer repositories, real consumer Sessions or shared published artifacts.
- Close and settle the case-owned processes and stores before reuse. A timed-out
  or interrupted case must not leave a child that can write into the next case.
  If settlement is unknown, stop reuse and retain the evidence; do not assume a
  missing response proves termination or add a monitoring/retry service.
- Retain case-specific logs, typed results and evidence outside the reset area.
  Preserve genuine failure, refusal, cancellation, timeout and UNKNOWN meanings.
- Reuse is local to one suite invocation. Do not introduce a global shared
  writable fixture, leases/quotas, binary comparisons or a new cache framework.

## Main route and acceptance

First prove the small target through actual correction, focused validation,
Review admission, selected Git commit, real ordinary local publication and result
return. Keep runtime execution and publication ownership unchanged. Shaped review
inputs in executable specifications must not be called real independent AI review.

Reduce the automated test set instead of porting the five heavy scenarios intact:

| Concern | Verification owner |
| --- | --- |
| Small-target failure, correction, focused validation, Review admission, commit, publication and result return | One representative real integration path |
| Refusal, repeated requests, stale input, repair trend/count/limit and supplement decisions | Existing or narrowly extended bounded contract tests with explicit fixture-owned facts |
| Actual process timeout/cancel/termination and capture | Existing CNCF execution tests; add a small integration case only for a demonstrated uncovered sm-workflow connection |
| sm-workflow self-update | Opt-in scripted procedure; manual or separately selected unattended run, outside routine suites |

Do not run ten real build/publication cycles merely to prove a repair-count
boundary. Keep the semantics and honest coverage attribution, not the expensive
old arrangement. Verify reset/reuse with a focused fixture-isolation check rather
than duplicating the full main integration solely to prove cleanup.
Select an individual affected case using
the existing -t/-z support when appropriate; do not rerun unrelated successful
cases simply because they share a suite class. Keep HEAVY classification for
actual multi-process integration work and keep FULL free of that work.

Acceptance evidence should include:

1. No sm-workflow production-source copy/build/publication inside ordinary cases;
   the fixed runtime and separate fixture Component are identifiable.
2. Actual small-target Git/SBT/publication results and preserved ownership checks.
3. Fixture-isolation evidence for consecutive uses of the same project without
   prior case inputs, Session state, Git changes or publication results affecting
   the next use; this does not require two full integration runs.
4. Preserved compile caches and retained evidence, plus safe treatment of an
   unsettled process before any reuse.
5. An explicit coverage map for the former self-repair assertions: preserved,
   moved to contract tests, opt-in scripted or deferred, never silently relabeled as
   covered. Self-update must not remain an automated test prerequisite.
6. Measured wall time and actual CLI/SBT/publication counts for selected cases.
   Reuse existing execution telemetry; add no AI measurement calls.

Keep the implementation focused on these test fixtures and existing selection
facilities. Any necessary product correction must have a concrete demonstrated
need; do not add a generic recovery or execution framework just for this fixture.
Return the scoped diff, actual checks, limitations and normal delivery result as
one outcome. No full-suite replay, runtime publication or consumer activation is
required merely to deliver test-source changes.

## HEAVY versus scripted operating policy

HEAVY executable specifications contain selected scenarios with continuing
regression value: repeatable requirements, meaningful failure signals and an
execution cost justified by the regression they detect. Expensive integration
alone is not a reason to retain every historical incident scenario in HEAVY.
Keep the existing FULL/HEAVY separation and change-specific focused selection.

Use scripted for one-off qualification, incident reproduction, migration or
self-update procedures worth retaining for future reference. Scripted is an
execution form, not another purpose label, and this policy does not require the
sbt-plugin scripted harness for a non-plugin procedure. Use the repository's
appropriate existing script mechanism rather than inventing a new test runner.

Retain each procedure's purpose, original incident/requirement, prerequisites,
target versions, explicit invocation, expected result, effects and cleanup. The
script describes how to reproduce a check; an execution record describes what
actually happened. Keep scripts and concise durable summaries in Git, with large
logs and working evidence in the existing non-Git evidence area.

An explicitly selected manual or unattended/nighttime run may execute the script
and retain its exit status, duration, versions and output references without AI.
Do not schedule such a job now or create an AI heartbeat/monitor. Failure retains
the result for human triage; it does not automatically invoke an AI or repair loop.
Self-update procedures use a dedicated environment and do not modify development
runtime selections or live consumer Sessions. Existing runtime ownership of SBT
and other effects remains in force; scripted is not a skill-side SBT bypass.

Scripted scenarios are not automatically included in ADMISSION, FULL or HEAVY,
and their execution is not a prerequisite for ordinary development completion.
Their catalogue can inform a later test-set review: promote a scenario to an
executable specification only when recurring regression value warrants it, and
remove redundant mandatory invocations while preserving the scripts and their
historical execution facts.
There is no requirement to implement a registry, scheduler or promotion engine.

## Existing scripted adoption — development handoff supplement

The user explicitly applies the operating policy to **existing scripted**, not
only future procedures. The normative selection policy is now recorded in
[Label-driven test selection](../../../spec/label-driven-test-selection.md#execution-and-preservation).
This is a specification and development handoff; invocation wiring is not claimed
to be changed or verified by this documentation update.

As part of the already requested test-fixture improvement, inventory build-task,
validation-profile, CI and completion-rule links that run scripted automatically.
Remove their status as prerequisites for ordinary development completion, including
indirect aggregate/wrapper invocations. Keep every existing script and retained
validation result. Leave active executions and their retained selection intact.

Choose only recurring regression requirements worth their execution cost for HEAVY
executable specifications. Reuse small fixtures and bounded contract tests rather
than mechanically porting all scripted cases. Report a compact coverage map:
the original procedure, the requirement, selected HEAVY/contract coverage where
appropriate, or continued opt-in scripted status. Preserve FULL/HEAVY separation
and explicit focused selection; no script catalogue replay is required for
adoption. Selection checks and relevant small-fixture checks establish the new
invocation boundary. Unexecuted procedures remain unexecuted, not implicitly PASS.

Manual or explicitly selected unattended runs remain possible without AI polling.
No scheduling or new execution framework is requested. Keep the accepted incident
repair closed and preserve existing Sessions, history, repair counts and passed
results. Return this adoption's actual changes, checks and remaining limitations
with the existing fixture-improvement deliverable, without a separate progress or
acknowledgment loop.

## Active scripted maintenance — user clarification

Scripted retains past test procedures and checks needed only occasionally, while
remaining an active execution target. Apply this to existing and new scripts:
preserve their invocation entry points and documented prerequisites, and keep
them selectable for on-demand or explicitly selected nighttime execution.
Exclusion from ordinary development prerequisites must not disable the scripts
or turn them into documentation-only archives.

Continuous verification against every product revision is not required. Exercise
the procedure when needed and fix environment/specification drift discovered at
that time. Keep genuine product regressions distinct from stale test procedures;
do not weaken assertions to hide a regression. Retain the original execution
result and record any repaired rerun separately, preserving previous results and
their version context. An unattended run may save its result without AI; a failure
can be maintained afterward without adding an automatic AI repair/monitor loop.

This clarification supplements the existing fixture-improvement handoff. Keep all
existing scripts, results, ongoing work and Sessions. Do not run or repair the
entire catalogue as an adoption gate, and do not create a nighttime schedule in
this documentation task. HEAVY continues to own selected recurring regression
coverage; scripted remains available for low-frequency reuse and maintenance.
The specification now states this explicitly; implementation adoption is still
owned by the development chat.

## Routing and coordination

The producer is currently closing the accepted repair and arranging its local
commits. Do not mix this new test improvement into that acceptance or stage its
files concurrently. Register this distinct deliverable through the supported
native task/delegation intake before issuing work; this journal is requirements,
not an admitted WorkOrder or execution authority record. If an intake connection
is unavailable, identify that concrete limitation without running an ad-hoc
replacement executor or reopening the incident repair.

The coordinating requester ends its turn after the authorized handoff. The
development owner returns one attributable result to chat
`01a0fa78-944b-7ed2-b04e-9deafbeca751`; no polling, acknowledgment loop or progress
relay is requested. Abnormal termination is left for human recovery.

## ON_DEMAND correction — authoritative handoff

The user authorizes adding `ON_DEMAND` (`on_demand` in discussion) and correcting
the returned test-set implementation. Calling a low-frequency ScalaTest suite
"scripted" was a terminology error. Reserve scripted for the actual sbt scripted
mechanism. Add the purpose to CNCF's shared metadata and use its existing
producer/consumer path in sm-workflow, rather than creating a private label.

Continue the original test-improvement deliverable. The development chat reported
candidate repair `678bae6f127a64c380b2894354a578bf36654837`, fixture cleanup
`3fb54cc1341dfc491db30317e7408503f89ed391` and Release cleanup
`bbc040fed6cdc040eda65a53cdb313f2bdb178ae` on separate branches, each based on
`113586f9b5a51e074c89c0e3145b707a00cc7f9a`; integration/push was not reported.
Its 7, 77 and 16 successful tests and publication facts remain scoped evidence,
not evidence of this new classification. Preserve those commits/results and
coordinate the actual branches; do not replace their independent changes.

Implementation order and acceptance:

1. Extend the canonical CNCF purpose model and existing parsing/discovery/selection
   contract for ON_DEMAND. Inspect current producer/consumer revisions first.
2. Connect sm-workflow purpose selection, metadata declarations and results to the
   shared purpose. Exclude ON_DEMAND from ordinary ADMISSION/FULL/HEAVY, including
   inherited/default memberships. Explicit ON_DEMAND or named focused selection
   admits it; a broad FOCUSED/feature query does not silently include it.
3. Remove `@DoNotDiscover` from the returned runnable low-frequency suites and
   declare ON_DEMAND. Remove associated manual enrollment if present. Correct
   suite/document names and invocation examples so they no longer claim to be sbt
   scripted. Keep real scripts and historical execution records.
4. Prove discovery, negative default/FULL/ADMISSION/HEAVY selection, positive
   ON_DEMAND and named focused selection, metadata parsing, result attribution
   and preparation-after-selection with bounded tests and one harmless dispatch.
   Do not rerun self-update or the expensive historical catalogue for adoption.
5. Return exact source revisions, changed selection behavior, actual checks and
   remaining limitations. Use runtime-owned validation/commit and only required
   runtime-owned publication for the producer/consumer change. Publication is not
   itself consumer activation, and transport receipt is not native acceptance.

Keep the tests active and available for occasional/nighttime execution. Repair
procedures when execution exposes drift, retain failed and corrected-run results
separately, and distinguish a product bug from test drift. No continuous
qualification, scheduler, AI polling or automatic AI repair loop is requested.
Preserve existing Sessions, history, repair counts, accepted results, ongoing work
and the sm-workflow-dev pause. This corrects the original delivery, not the closed
incident repair. Reply once with the actual outcome; do not add progress/ack loops.

## Operational skill adoption

The shared development-workstation native execution contract now defines the
ON_DEMAND operating policy. All seven sm-* entry skills and cncf-heavy-test
reference it. Ordinary ADMISSION/FULL/HEAVY must not select ON_DEMAND implicitly;
low-frequency checks remain available for explicit execution and maintenance at
use time. Existing selections and evidence are preserved. Actual sbt scripted
keeps its separate meaning and invocation route.

The installed skills on this machine resolve to the updated source through
symlinks. Shared references, frontmatter and diff whitespace were checked.
The skill quick validator could not run because PyYAML is unavailable; no validator
success is claimed. Product support for the new purpose remains pending the
development deliverable. The operating policy does not claim runtime availability,
authorize a fallback executor, or block unrelated development on that availability.

## New linked ON_DEMAND correction Task — superseding intake decision

The development owner reported both original Tasks as Terminal/Returned with no
issued WorkOrder. The user then explicitly approved a new correction request.
Do not implement a post-return lifecycle extension or keep advancing the terminal
Sessions. Use ordinary DelegateTask preparation/start/admission for a distinct
request, proposed identity `on-demand-classification-correction-20261031`, with
the existing development chat as destination. This identity is proposed input,
not a claim that native admission or a new Session has already occurred.

Carry existing authorization for CNCF shared-purpose support, sm-workflow
selection and suite migration, required focused verification/review, local commit
and runtime-owned publication where required. Do not ask the user to approve the
same scope again. Record original request/Session identities, the three retained
commits and results in sourceReferences and covering approval in the existing
authorizationReferences. Keep request/destination/return identities attributable.
Source for actual terminal observations and implementation boundaries:
`sm-workflow-candidate-path-repair-20261031/target/sm-workflow/on-demand-correction-20261031/assessment.md`
and sibling `continuation-observation.json` (resolve the checkout in this workspace).

The original Sessions remain terminal; their generation2/ordinary1/automatic1 and
generation3/ordinary2/automatic2, accepted results and histories are immutable.
The new Task has its own Session and separate repair accounting. Preserve old
counts as provenance; this is not a refund or a reset of either predecessor.
It is a correction to returned deliverables, not an escape from an active stop.

The shared development-workstation delegation contracts and sm-delegate-task /
sm-goal-task entry instructions now distinguish active continuation from a linked
post-return correction. Existing input/reference fields and ordinary independent
admission suffice; no new DTO or lifecycle Operation is requested. The development
owner should perform normal intake preparation using its established configuration
before product edits; this message/document is authorization and requirements,
not an issued WorkOrder or fabricated admission result.

Keep the ON_DEMAND implementation and bounded acceptance above unchanged. Preserve
the separately based source commits when combining the actual changes. Retain
all successful verification and publication evidence, and run only newly needed
checks. No remote push, consumer Session operation, reopening of the accepted
incident, sm-workflow-dev resume, catalogue replay or AI polling is requested.
Return the actual new Task identity/Session and final source/check/delivery result
once, or a concrete normal-intake failure if encountered. Do not treat the absent
post-return correction API as a prerequisite for this new Task.
