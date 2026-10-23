# SBT execution recovery belongs to the execution infrastructure

Date: 2026-10-21, Asia/Tokyo
Status: bounded runtime/host correction, mechanical capacity and current local installed startup/delayed-running continuation qualified; normal publishLocal complete. Optional unchanged consumer compile remains unexecuted; generic distributed-worker fencing is not claimed. Separate mutable-runtime-closure classloading issue remains a Development Candidate.
Issue: `SBT-EXECUTION-RECOVERY-001`

## Problem and required ownership

SBT worker startup failures have repeatedly required the parent AI to inspect
logs, decide that execution never began, select another worker, reconstruct a
handoff and request another exception. Coordinating those actions across chats
does not satisfy runtime-owned execution or the Continuation Protocol. A
successful manually recovered build does not close this design issue.

The user explicitly requires sm-workflow's execution infrastructure to handle
worker startup, execution-state classification, recovery and handoff
deterministically. The parent AI and skills must not be the execution recovery
state machine. This is a design correction to the ongoing repair, not another
request to approve one stopped worker's transfer.

The requirement applies to infrastructure-owned SBT execution, including a
configured executor/worker adapter where one is used. Application validation
continues to use typed `sbt_` DSL operations inside UoW. It must not fall back to
the parent spawning SBT, assembling execution receipts or interpreting prose to
choose recovery operations.

## Execution contract

The execution infrastructure owns:

1. Binding the selected typed operation, target, current authority and execution
   identity to the configured executor; constructing and validating its startup
   request mechanically.
2. Starting the executor, observing authoritative startup/execution facts,
   waiting, collecting results and preserving the actual failure record.
3. Classifying a proven pre-execution failure separately from execution that
   started, completed, or has an unknown outcome. Missing output, an absent
   receipt or an AI statement that nothing ran is not proof of non-execution.
4. Selecting a bounded recovery or successor under the existing authority and
   configured policy. Proven unused authority may be transferred only under
   its applicable contract; permission consumption must not be duplicated or
   silently renewed. No parent-created exception or chat-to-chat negotiation
   is part of the normal path.
5. Fencing the old executor so that a delayed start or result cannot race with
   its successor, and correlating the successor's real result with the same
   requested validation. If this cannot be established, stop with the actual
   typed uncertainty instead of automatically dispatching another execution.
6. Returning the collected result to the owning workflow and continuing its
   normal validation/acceptance transitions. Executor replacement alone must
   not replay an accepted semantic result or create a new workflow Session.

These are logical obligations, not a prescription for a new private store,
outbox, digest, signature or write/readback protocol. Reuse the existing
execution, UoW and workflow completion contracts; identify any actual missing
capability at its owner before implementation.

## Continuation boundary

Ordinary startup, waiting, result collection and eligible recovery are program
work. They do not require an AI turn merely to continue or a Human question
merely because another executor is needed.

When a genuine semantic or authority decision is required, the workflow issues
the corresponding typed Continuation. For example, authorizing a new attempt
despite an UNKNOWN old effect is different from transferring a proven unused
execution. The external Skill presents the actual Human question and returns
the actual answer through its declared completion. The runtime validates the
answer and selects the subsequent action. Neither a new direct Human provider
nor a parent-selected chain of recovery APIs is a prerequisite.

Unknown effects remain unknown. A Human authorization for a new attempt is not
retroactive evidence of non-execution or success. Existing independent review
and Human provenance requirements remain in force.

## Responsibility boundaries

| Owner | Responsibility |
| --- | --- |
| sm-workflow execution infrastructure | Execution lifecycle, policy-selected recovery/handoff, workflow correlation, normal continuation and operator-visible state |
| CNCF typed SBT adapter and UoW/process execution | Ordinary SBT command execution, checks, authoritative lifecycle/result facts and process cleanup |
| Configured executor/host adapter, including shared runners where applicable | Mechanical request delivery, observed executor identity/start/result and termination/fencing; no workflow or recovery decisions in skill prose |
| Skill / current Codex | Only issued semantic work or Human interaction, and return of its typed result |

Concrete changes in another repository require evidence of the missing owned
capability. This record does not declare new cross-repository implementation
complete or authorize unrelated work.

SBT commands remain ordinary SBT commands. Necessary exclusion belongs to SBT's
own locks. Do not introduce a machine-wide serialization wrapper, a `scripted`
verification prerequisite, product-specific mains or a generic process fallback.

## Acceptance evidence

Use isolated executions through the normal installed CLI and real typed
SBT/UoW path to demonstrate:

- A proven startup failure is classified and recovered without the parent
  interpreting logs, constructing evidence or arranging a worker handoff.
- A delayed old executor cannot execute after transfer or overwrite the
  accepted successor result.
- An execution that may have started remains UNKNOWN when its result is lost;
  it is not silently retried or converted to NotExecuted/success. Any actual
  Human decision follows the normal Continuation contract.
- Successful launch, waiting and result collection return to the same workflow
  validation and ordinary continuation, with startup failures and attempts
  preserved. Failure and cancellation follow their real lifecycle outcomes.
- Existing authority, scope and targets remain enforced; recovery does not
  reset repair counts, bypass independent review or replay accepted work.

Reuse applicable prior tests and evidence. No additional review cycle or whole
test responsibility is introduced by naming this issue. Compilation or a mock
state transition alone does not establish operational acceptance.

## Retained work and next action

Preserve the current Goal, Session, Plan/base, accepted results, histories,
repair counts, dirty changes, 25 focused passing tests, independent review,
ordinary publishLocal and one passing installed recovery episode recorded in
[the source-history repair assessment](2026-10-21-source-history-prewrite-failure-recovery-assessment.md).
Those results retain their original scope; they do not prove this execution
recovery design is implemented.

The mapped product route already invokes CNCF's fixed typed SBT adapter inside
UoW; it does not dispatch an AI SBT worker. The admitted repair keeps that
ownership and adds bounded supervision to the existing native check program.
Only a durably saved, valid actual NotStarted result permits one fresh dispatch
for the same selected check in one Advance. History reconciliation and current
preconditions precede its new intent. Missing/uncertain results, launch timeout,
cancellation and authority rejection grant no automatic extra dispatch.

The normal Continuation repair also connects the exact unresolved first
source-history intent to the existing durable ordered Human question and its
issued completion. It preserves the original UNKNOWN effect and independent
Human evidence. This is separate from proven-NotStarted SBT recovery and grants
no permission to replay an accepted semantic result or a successful check.

Production edits and controlled regressions are being completed in the existing
sm-workflow repository. Focused generation/compile/tests, independent review,
ordinary publishLocal and isolated installed CLI verification remain required
before accepting the repair. The controlled startup tests exercise the actual
production orchestration with controlled effects; they alone do not establish
an observed installed startup failure or complete infrastructure acceptance.
External development-tool command runners retain their own execution contract;
their manual recovery is not the product solution or product evidence.

Live E3 Session operations remain with the owning development chat. No live
Start, Submit, Advance, accepted-result replay or repair-count change occurs here.

## Same-task host connection correction

The runtime repair passed focused independent review. The seven-suite run
retained 54 successes, two fixture failures and one unselected opt-in SIGTERM
fixture. Both failures were corrected without weakening production guards:
the ordinary Human CLI example passed and the Human-preparation suite passed
10/10. One intermediate filtered selection ran zero tests and supplies no
coverage. Normal local publication of this runtime boundary succeeded.

That publication does not establish the complete host route. Read-only mapping
confirmed that the existing shared `native-goal-command.py complete` lacks its
issued Goal Human branch. `HOST-CONTINUATION-003` corrects this necessary
mechanical gap within the same requested continuation: actual supplied Human
answer/reference and genuine host records go to existing prepareEvidence;
the existing CNCF fixed import-file binding selects its actual output for
Submit startup; only accepted issued Submit permits one normal Advance.
The shared host is canonical and the product distribution carries the same
bounded correction. No new Human API, provenance source or parent-authored
approval rows are admitted. Host validation, independent review, updated local
publication and an isolated installed CLI episode remain pending.

The host correction now has 35 passing Python behavioral cases, including
three controlled real subprocess calls, unchanged configuration, fixed binding
selection, refusal/uncertainty and routing/cancellation stops. These qualify
the caller program, not genuine Human provenance or an installed product
episode. The exact original execution records remain outside Git in the
selected disposable allocation and monthly backup; the independent host
review and installed qualification remain separate gates.

## Current disposition

The requested normal Continuation and deployed finite-host connection are now
verified through the isolated installed CLI. Native preparation, the actual
accepted ordered Human completion, ordinary Advance, one real typed SBT check
and the next independent ReviewStep all returned to the same isolated workflow.
Original UNKNOWN intent and accepted prefix, Plan/base, counters, host config
and routing were retained. Focused independent source closure passed; the
updated normal `publishLocal` is complete. No live consumer operation, accepted
result replay, new Goal/Start, commit or push was performed here.

The first host qualification exposed an inadmissible Global configuration
binding. `CB-HOST-IMPORT-TARGET-001` is closed by the fixed canonical
`--textus.import.data.file=` argument: CNCF admits its Args snapshot under the
actual selected subsystem. That failed invocation remains failure evidence;
the subsequent fresh isolated qualification passed. No CNCF code or host YAML
parsing was needed. Shared code is canonical; product distribution is updated.

The NotStarted supervision is verified at its actual production orchestration
boundary with controlled owning-UoW effects. This does not qualify genuine
installed startup-failure/delayed-executor fault injection or live Human capture.
Those limits remain explicit; the larger infrastructure acceptance claims above
are not inferred from the successful normal installed path. External development
runner handoffs remain separate from the product runtime solution.

`HYG-HOST-CONTINUATION-003-001` (existing private parameter naming) and
`HYG-HOST-CONTINUATION-003-002` (existing large-spec organization) remain separate
nonblocking maintenance. Earlier Hygiene dispositions and all failure records
remain retained.

Terminal execution, review disposition and complete agent-use records are kept
outside Git at
`macmini:~/backup/journal/2026/10/sm-workflow/source-history-recovery/01a13249-14e0-7470-8144-f9af8145cec6/`.
Default retention date is 2026-11-20, with an explicit hold while consumer
handoff or failure investigation needs them. Original bound private records
remain in place; archive creation authorizes no cleanup.

## Same-task typed SBT output admission correction

The consumer retained its actual E4 success and local commit
`808bc32400428331ab0fc8586c421668cf586218`. Its later real Phase full Test
exited1, after which receipt admission failed and the old intent remained
UNKNOWN. Supporting test logs are not a substitute for its missing capture;
the exact original rejection predicate remains probable rather than confirmed.
That old effect was neither backfilled nor automatically retried here.

`SBT-CAPTURE-01` removes the duplicate application64KiB cap from actual typed
SBT output and retained typed-SBT text. CNCF remains the owner of finite capture
limits. Completeness/count/truncation, strict UTF8/NUL, typed identity and
termination/outcome checks stay enforced. Structural results, legacy receipts
and diagnostics retain their existing64KiB limits. No new schema, generic
process fallback or CNCF capability was introduced.

Focused verification passed38 cases in3 suites. The new explicit-only CLI
class was initially compiled but unselected; one closed-set enrollment in
build.sbt corrected that test-connection omission. Its normal CLI execution
then passed1/1. Independent source review, including the enrollment, passed.
Normal publishLocal completed2026-10-21 13:14:55JST at the unchanged CAR
coordinates. The published-artifact installed CLI qualification passed1/1 at
13:17:13JST: genuine Test exit1 with more than64KiB actual stdout becomes the
public RepairStep, and a fresh installed process reopens the exact same
Continuation and issued context without an extra SBT execution.

This closes the focused capture repair, not genuine installed startup-failure
or delayed-old-executor fault injection. The consumer's separate full-test
failures and any new Human decision for its old UNKNOWN attempt remain with
its owning workflow. Goal, Session, accepted results, history, counters and
all unrelated dirty changes are preserved; no real Session Start/Submit/Advance,
commit or push occurred here. Nonblocking historical lock prose and existing
private naming remain HYG-SBT-CAPTURE-01/02 rather than repair additions.

Actual terminal records and the complete agent-use ledger are retained at
`macmini:~/backup/journal/2026/10/sm-workflow/sbt-capture-runtime-continuation/01a13249-14e0-7470-8144-f9af8145cec6/`.
Keep the records during active handoff/investigation and for30 days after its
completion; review on2026-11-20. Original bindings remain in place.

## Later consumer failure: upstream mechanical receipt capacity

The consumer's separately authorized next real full check reached Exited(0)
(pid55558,911806ms), then its enclosing CLI failed at CNCF's
WorkflowMechanicalTransitionJsonV1:103. `SBT-MECHANICAL-RECEIPT-02` remains
unresolved. That codec bounds the entire current/next/payload envelope to1MiB
on encoding and reopening; the actual host admits finite captures larger
than that. Its existing upstream executable specification explicitly requires
oversized data to fail. Correcting this boundary requires an explicit CNCF
mechanical-persistence policy decision and dependency repair scope, beyond
the earlier application64KiB repair. No ad hoc application storage, truncation,
codec bypass or fabricated receipt is acceptable.

The old and new uncertain attempts, candidate, accepted E1-E4, histories and
counters remain untouched here. The prior normal and installed CLI successes
keep their <=131072-byte native fixture scope and do not close this new issue.
No additional implementation, SBT execution or publication has been performed
for this new issue. Its diagnosis, proposed bounded acceptance work and owner
are in the producer's private assessment:
`macmini:/private/tmp/sm-workflow-sbt-capture-repair-20261021/parent/mechanical-receipt-dependency-assessment.md`.

### Human-selected configurable capacity repair

The owning Human subsequently selected CNCF's unchanged1MiB default with
sm-workflow's16MiB default and normal property configuration. Project-specific
values use existing CNCF parameter resolution; this adds no project search or
override mechanism. The earlier global16MiB proposal is superseded, retained
as history rather than implementation authority.

`workflow.mechanical-receipt.max-bytes` is the selected optional public
Component parameter, measured in complete UTF8 JSON bytes, finite1..67108864.
The existing immutable workflow persistence Configuration holds the resolved
limit across preparation, atomic save, postcommit decode and reopening.
The same project resolves the same configured policy in a fresh process;
lowering it rejects previously larger records rather than relaxing admission.
Capture limits, typed ownership/history checks and uncertain-effect handling
are unchanged. No unrestricted retention or old-reader compatibility is claimed.

Exact frozen source/configuration and isolated CLI regression manifests are
retained under the private parent directory above. Two registered
cncf_fix_worker_terra gpt-6.1-sol/high editors implement disjoint owned paths:
`/root/mechanical_receipt_config_fix_20261021` and
`/root/mechanical_receipt_cli_fix_20261021` (START1 each). The parent retains
diagnosis, verification selection, independent review and publication admission.
The live Session/intent228, accepted results, Plan/base/candidate, histories
and repair counts remain untouched. New tests use only fresh target-owned
isolated Sessions; the prior one-use real execution permission remains consumed.

### Verification found an additional Configuration codec connection

CNCF's first focused run passed39 cases in4 suites and its normal publishLocal
succeeded. Source review passed the initially owned15 paths. The downstream
sm run then failed during Compile, before any suite ran: the existing
SplitPhase retained-context owner codec could not derive the new validated
WorkflowMechanicalReceiptLimits value. That failed attempt and consumed
permission remain retained; executor `status=recorded` was not interpreted as
test success.

The same capacity repair now includes that actual Configuration serialization
connection and immutable value equality. Old five-field context data denotes
the historical CNCF1MiB default; an explicit malformed new field must fail.
The canonical current configuration remains independently resolved and selected
at preparation. This adds no fallback execution or inference of a looser policy.
The exact four-path correction is delegated to the registered Sol/high editor
`/root/mechanical_receipt_codec_fix_20261021`. New verification uses fresh
attempts; no real Session operation or successful consumer check is repeated.

The CODEC/REOPEN amendment's independent Sol/high review passed all five owned
source/spec paths. The corrected CNCF run passed40 cases in4 suites; its fresh
normal publishLocal completed2026-10-21 14:46:23JST. The earlier39-case result
and failed downstream Compile remain distinct historical results. CAR lint
returned0 with no FAIL and13 preexisting warnings outside the amendment.

The two existing isolated large-result CLI cases now begin and accept work
under the old1MiB capacity policy, then select normal sm16MiB/default or an
explicit8MiB project setting for the same Session. This tests current policy
selection without new Start or resubmission after the property change; it does
not claim an actual old-binary migration. The real consumer remains untouched.

Nonblocking Hygiene retained separately: HYG-MECHANICAL-RECEIPT-01 existing
large fixture organization; HYG-MECHANICAL-RECEIPT-02 older local helper names;
HYG-MECHANICAL-CONTEXT-GWT-01 older GWT clause placement. No additional repair
or review loop is authorized by those maintenance observations.

The next sm verification also stopped during Compile before running suites:
SplitPhaseTypedStartJsonV1 has the second derived Configuration owner. The
initial correction left its mapping local to the context codec. The parent
retains this second consumed failed attempt and corrected the connection by
moving the same validated mapping to existing shared RawCodecs imported by
both owners. The two actual serialized Configuration consumers were checked;
no new persistence policy or private-row workaround is added. Typed Start
roundtrip/current-config regressions join the existing context checks. CNCF
source and its successful40-case/publication evidence are unchanged.

Subsequent sm tests03 compiled and executed26 passive-owner cases:24 passed,
2 incident-added malformed metadata assertions failed; the runtime task did
not execute. The complete terminal failure and consumed permission remain
retained. A source-review PASS did not establish those runtime assertions.

Read-only consumer inspection confirmed candidate808bc324 still uses ordinary
generic Configuration derivation. Requiring a dedicated codec source merge
would change that fixed candidate. The parent therefore froze SOURCE amendment:
the optional capacity travels as standard Option[Int], validated into finite
limits once at the CNCF operational boundary. Historical five-field JSON keeps
None/1MiB semantics; current normal sm property resolution still selects16MiB
or its valid explicit override. Dedicated incident Configuration codecs are
removed rather than required in the old consumer. Normal configured null or
malformed values still reject; passive optional-null semantics remain ordinary
Option semantics. Earlier codec proposals/reviews are retained as superseded
history. New source review, producer tests/publication and isolated CLI proof
are required for this changed implementation. A standalone target-only compile
of unchanged consumer source is selected as a dependency-specific prerequisite;
it is not a real workflow action, full Test, candidate edit or retry grant.

SOURCE producer verification completed2026-10-21 15:08:32JST:41/41 tests in4
suites, SBT/wrapper0, own process terminated with sbt-native locking. Public
typed validation admission succeeded for mechanical-cncf-tests03. This covers
the revised source, including null/invalid operational selection refusal before
effects. Consumer source compile, current sm runtime tests and final local
publication/installed verification remain pending at this point in the history.

Independent SOURCE review returnedPASS, no current blockers or Development
Candidates; its two pre-existing naming/GWT observations are nonblocking Hygiene
HYG-SBT-MECHANICAL-SOURCE-001/002 and do not expand the repair. CNCF ordinary
publishLocal then completed15:12:19JST, refreshing unchanged0.5.3-SNAPSHOT in
normal local Ivy. Public typed publication admission succeeded. The next exact
prerequisite is unchanged consumer808bc324 standalone compile, target outputs
only, without its real workflow/full Test or candidate modification.

That optional consumer compile did not run: its registered development runner
reported successful intake selection but failure to retain the actual selection,
then stopped before preparation/platform submission/SBT. This is a reported
development-tool preparation stop, not compile failure/success or authoritative
product NotStarted proof. The parent retained the original child/unused scope
and did not resume it, replace it, reconstruct output, transfer authority or ask
for another handoff exception. Standalone compile of unchanged808bc324 remains
UNVERIFIED. The independent current sm passive/runtime tests continue; no live
Session action or source integration is substituted for the missing check.

Current sm tests04 completed15:17:20JST:28/28 passive-owner tests in2suites plus
35/35 runtime/normalCLI tests in4suites,63 total, SBT/wrapper0 terminated and
typed validation admitted. Both actual large-result CLI cases passed. They keep
the same isolated started/accepted Session while selecting current16MiB/default
or explicit8MiB normal policy; real Test failure resumes RepairStep, real Test
success reaches ReviewStep and specification-owned normal closure yields one
typed local Step commit plus pendingReviewPhase. Fresh process reopening
preserves exact context/receipt with no extra SBT PID/commit. Specification-owned
semantic inputs are not genuine independent review/provider acceptance.

Parent normal CAR publication preparation initially selected its producer as
its own dependency consumer and was rejected during CommandRequest construction,
before any intake/child/submission/SBT. This bookkeeping error was corrected to
the actual sm-workflow-dev consumer basis already used by CAPTURE01. Its unused
metadata remains retained. New publish02 is the first valid request, not an
execution retry; destinations/coordinates and no live-Session effects remain
unchanged. No tests or successful operation were repeated for this correction.

Normal sm-workflow publishLocal completed15:20:57JST, SBT/wrapper0 terminated,
public typed publication admitted. It built and published the unchanged
0.1.0-SNAPSHOT CAR under the ordinary ~/.cncf/local/repository path; no remote
publication/push/deployment occurred. The exact same2 isolated large-result
scenarios are now selected with the absolute installed executable, using this
published CAR; their result remains pending until actual terminal admission.

The selected installed2case run stopped during its development runner's intake
selection, with a reported locator transcription error before preparation.
No installed test executed. The actual failed call/result was not available;
the child returned only an abbreviated error report, and app read_thread could
not resolve its canonical child. Missing files do not prove nonexecution. The
generic same-child locator-recovery contract requires retained actual raw
failure plus independent no-submission admission, so no recovery ticket,
replacement child, new authority, resume or manual handoff exception was issued.
Preserve this separate infrastructure stop as unresolved. Current capacity
installed validation is UNVERIFIED, rather than reusing the earlier64KiB case
as proof of this new capacity behavior. Normal63-case sm verification and both
current local publications remain valid within their actual scope.

Real consumer96c294d73be3d48067f3721d8a2abf33, acceptedE1-E4, candidate808bc324,
Plan/base, unresolvedintent228, Goal/history/counters and other dirty work are
preserved. No real Start/Submit/Advance/Test, private store write, commit/push or
remote publication was performed. Broader installed startup-failure and delayed
old-executor qualification remains open; no claim of complete issue closure.

Final parent checks: both producer git diff --check passed; consumer source/
build paths have no diff and HEAD remains808bc324. Actual188 selected execution,
permission, failure, review, manifest and agent-use records were copied without
moving original bindings to macmini:~/backup/journal/2026/10/sm-workflow/
mechanical-receipt-capacity/01a13249-14e0-7470-8144-f9af8145cec6/.
See its parent/mechanical-result.md for current qualification/limits and
parent/mechanical-agent-ledger.md for every START/RESUME/profile. Retain while
handoff/investigation remains active, then30days; review2026-11-20. No cleanup,
real store copy or new readback/integrity certificate was introduced.

The parent subsequently recovered the missing installed-runner raw evidence
without executing another command: the same child returned its complete actual
selection call/result from retained context, including literalquestionmarks in
the erroneous locator, plus confirmation no later renderer/checker/history/
submission/review/denial/start call occurred. This supplements the earlier
abbreviated report; neither failure nor initial stop is rewritten. Parent public
inspection accepted the exact original intake/child/argv and live unconsumed
permission. Existing generic locator-transcription ticket builder validated
current subject and absence of execution evidence, then accepted one SAME-child
ticket. Evidence-onlyRESUME1 used no tools; RESUME2 follows its original3line
recovery contract. No new child, attempt, authority, permission, exception or
product Session effect is allocated. Installed test result is still pending;
recovery acceptance is not execution or product recovery qualification. Initial
188-record archive remains the earlier truthful stopped snapshot; subsequent
evidence will be retained in a separate supplement, preserving that snapshot.

Installed continuation subsequently executed2026-10-21 15:30:53JST through that
same original request and generic recovery ticket. Actual2tests in1suite:
E1passed, E2failed. E1 proves the published installed command returns the
genuine large failed Test receipt to RepairStep and reopens without replay.
E2 failed at actual installed Advance pid98647, isolated Session
a3fadf062425dcb1fd43e1c01a7b896c, with HTTP500 IOException: No space left on
device, before ReviewStep. SBT/wrapper exited1 and terminated with sbt-native
locking. Public successful validation admission refused this failed execution;
executor receipt exit0 is not SBT success. The original stop, consumed
permission and failed execution remain preserved; no second ticket, retry,
replacement, authority transfer, weaker assertion or product workaround follows.

Read-only disk observation: Data volume926GiB,100%,308MiB available. Current sm
target3.4GiB includes native-cli346MiB; sm disposable167MiB, real consumer
target398MiB, CNCFtarget1.3GiB. No deletion or cache relocation was performed:
these areas contain other work, accepted history, generated products and
current evidence. Capacity must be restored under an explicit cleanup scope
or by user-managed action before selecting a fresh ordinary isolated E2
verification. Reuse current CNCF41/41, sm63/63, independent SOURCE PASS and both
normal publishLocal successes while their subject/artifacts remain unchanged.
No repeat of successful publication or real Session action is warranted.

Remaining issue qualification is explicit: installedE2 and optional unchanged
consumer compile remain unverified, as do genuine installed startup-failure
and delayed-old-executor fault injection. The current environmental failure
does not establish a new source defect or complete SBT-EXECUTION-RECOVERY-001.
Goal/realSession96c294d73be3d48067f3721d8a2abf33, accepted results, Plan/base,
candidate808bc324, unresolvedintent228, history/counters and all unrelated
changes remain intact. No real Start/Submit/Advance/Test or commit/push occurred.

The subsequent actual failure/recovery evidence, parent assessment and complete
agent-use ledger were archived as41records under the separate
installed01-continuation-supplement directory of the existing monthly archive.
Original188records and live bindings remain unchanged. Both producer final
git diff --check checks passed; consumer HEAD remains808bc324 and source/build
paths have no diff. Supplement retention matches the original active-handoff
then30day policy; review2026-11-20. No cleanup or success certification occurred.

Human subsequently reported disk capacity restored; parent read-only df
confirmed66GiB available. Under the existing development repair authority,
fresh ordinary mechanical-sm-installed02 selects ONLY the missing installedE2
using ScalaTest substring "explicit 8MiB policy". Original installed01 failure,
consumed permission, stopped/recovered child and isolated failed Session stay
unchanged; this is no transfer, old-attempt retry or Session restart.
Published CAR/normal installed command is reused. No source change, successful
Test/Step replay or publishLocal repeat is selected. All real consumer state,
Goal/history/counters and other work remain preserved. Result pending until
actual process termination, nonzero test count and typed success admission.

Capacity-restored installed02 completed2026-10-21 15:56:25JST: selected E2
passed1/1 in1suite, SBT/wrapper0 and process terminated, sbt-native locking;
public typed validation admission accepted. Actual published installed command
executed typed SBT/UoW StepTest, normal specification-owned semantic review/
authority, exactly one typed Stepcommit, PhaseTest and pendingReviewPhase.
Fresh command reopening preserves the exact large receipt/context without
extra SBT PID/commit. Prior installed01 E1 success is reused; its E2 ENOSPC
failure/consumed authority and original stopped record are not rewritten as
success. Both capacity scenarios now have actual installed proof across the
two runs. No source edit, repeat E1 or repeat publishLocal occurred.

The unchanged consumer standalone compile is OPTIONAL for this published
capacity repair and remains UNEXECUTED, not silently passed. Evidence-based
assessment: current Configuration standard Option[Int]=None retains original
five-argument construction, tested by CNCF41; actual producer ordinary aggregate
derivation compiles and28passive owner tests pass. Parent read-only comparison
with consumer808bc324 shows TypedStartJsonV1 identical, RawCodecs @version only,
ContextJsonV1 @version/private given names only with the same Encoder/Decoder
types and derive expressions. No dedicated Configuration codec/source fix is
required. Missing/null optional historical metadata and current normal policy
selection are tested. This is source-compatibility/installed connection evidence,
not full consumer compile, old-binary ABI or extractor compatibility evidence.
Do not revive the stopped optional compile request or repeat native E1 Compile.

Real Session recovery remains owned by its development chat: select the normal
published CAR/current capacity policy, retain Plan@2/candidate808bc324/accepted
results and unresolvedintent228, then satisfy the actual existing ordered
Continuation decision before any new uncertain-effect attempt. No missing old
receipt is synthesized, UNKNOWN cleared, permission renewed or full Test
authorized by this repair record. No real Start/Submit/Advance/Test occurred.
The separate genuine installed startup-failure/delayed-old-executor fault tests
remain open under SBT-EXECUTION-RECOVERY-001; they do not reopen the now-verified
mechanical capacity connection. No outgoing chat message was sent.

Final installed02 success, retained actual request/result/log/summary, recovery
prerequisite assessment and full agent-use ledger were archived as39records at
macmini:~/backup/journal/2026/10/sm-workflow/mechanical-receipt-capacity/
01a13249-14e0-7470-8144-f9af8145cec6/installed02-capacity-restored/.
Earlier188-record snapshot and41-record failed01 supplement remain unchanged.
Final producer diff checks passed. Retention remains active-investigation hold,
then30days, review2026-11-20. No cleanup, real-store copy, commit or push.

## Remaining installed startup and delayed-execution qualification — 2026-10-21

The human explicitly authorized completing the remaining genuine startup-failure
and delayed-old-execution checks, without restarting or resubmitting the real
consumer Session or replaying successful work. Existing capacity results and both
normal publishLocal successes remain accepted at their recorded scope.

Frozen E8/E9 verification adds only a new isolated installed CLI spec, its explicit
SBT selection entry and the corresponding specification evidence description.
E8 changes only a private regular copy of the normal SBT distribution launcher,
after bootstrap, from the first actual Compile. CNCF must observe actual
NotStarted, retain it, apply its bounded fresh-dispatch policy, then resume the
same isolated Session after the private launcher is restored. The successful
first Compile must never replay. E9 holds an actual old Compile on a finite
fixture-owned gate and checks that another ordinary Continuation cannot dispatch
a duplicate; the original result must settle normally after release.

These checks target the current local execution path. They do not introduce a
product fault-injection setting, external worker registry, invented receipt,
application exclusion, AI-owned SBT recovery or a generic distributed fencing
claim. CNCF's existing controlled delayed-launch tests retain their distinct
scope. Actual new results remain pending; no acceptance is inferred from this
plan. No operation has been issued against consumer Session
96c294d73be3d48067f3721d8a2abf33, and candidate/base, accepted E1–E4, intent228,
Goal, repair counters and unrelated work remain unchanged.

### Qualification disposition without resetting state

The isolated qualification encountered a test import error and then a fixture
binding comparison error. Neither partial attempt established complete scenario
acceptance. The fixture corrections retained the original assertions and owned
cleanup. Independent review resolved `CB-SBT-STARTUP-CLI-CLEANUP-001` before the
remaining qualification continued. Successful first Compile work was not
replayed, and no real consumer operation, permission handoff, Goal reset or
repair-counter change was introduced.

Individual attempts, actual results, logs and the original journal are retained
outside Git at `macmini:~/backup/journal/2026/10/sm-workflow/reasoned-sync/20261023/evidence-originals/`.
The management outcome and qualification limits below remain authoritative.

### Separate concurrent consumer classloading observation

The owner chat independently reported a later authorized real attempt2,
intent230/source225, followed by actual Test Exited(1) and CLI ZipException with
an empty response. This parent performed only direct read-only artifact/log
inspection, not a Session operation or outgoing chat message. The newer owner
history supersedes this journal's historical intent228 observation without
rewriting it. Current candidate remains808bc324 and Plan@2.

The consumer episode spans16:07–16:23. Its runtime classpath references local Ivy
CNCF0.5.3-SNAPSHOT; that path now has mtime16:16:34JST/41524291bytes, differing from
this parent's15:12 publication observation41507904bytes. Current ZIP members
fully read without CRC failure, including the two classes missing from the old
CLI, while current16:23 test XMLs also retain invalid LOC-header errors. Active
SNAPSHOT replacement is a probable hypothesis, not a confirmed cause: this
parent did not observe the old process's open-file identity or identify that
later publication's owner. No source-test repair is justified from these
classloading aborts alone, and no blind republish/retry follows.

`DEV-SBT-RUNTIME-CLOSURE-001`: owning runtime/artifact-selection boundary should
assess stability of a selected mutable development classpath for the lifetime
of an execution. Resume with concrete publication/open-file evidence and an
explicit design scope if a change is needed. Do not implement a global SBT lock,
cache isolation, local hash control, store patch or implicit fallback. This
separate candidate does not block the independent isolated E8/E9 verification.

### Installed qualification completed — 16:35:18JST

`startup-sm-installed03` completed both actual installed CLI cases: 2/2 passed,
one suite, zero failures or aborts; SBT and wrapper exit0, process terminated.
Parent independently checked the actual result, counts, log and terminal marker;
the existing typed validation admission accepted this fresh request. The first
two consumed failed requests and their partial execution evidence remain intact.

E8 retained both actual NotStarted observations, including the one bounded
automatic fresh dispatch. After restoring only the fixture-owned launcher,
ordinary Continuation of the same isolated Session ran only the unfinished
second Compile and reached ReviewStep. Its successful first Compile was not
replayed; a further Continuation created no additional Compile or intent.
E9 observed the old SBT actually running. Concurrent ordinary Continuation
returned Wait without a second dispatch; releasing the finite owned gate let
that exact execution return its result. Further Continuation retained the same
result and history. Plan, base, candidate and repair counters stayed unchanged.

This proves the current local NotStarted recovery and already-running execution
ownership paths. E9 does not inject a delayed ProcessBuilder launch future or
establish generic distributed worker fencing. Existing CNCF controlled-launch
tests retain their separate scope. The real consumer Session was not operated
on by this parent; the owner chat's independent newer history remains intact.

Actual intake and result:
`.codex-workflow/disposable/a-208426dd-dde3-496a-86c4-f0f8c0d69c60/`.
Actual log and summary:
`.codex-workflow/disposable/a-99c5321d-7d22-4ffc-ab66-adfa4cda1a86/36442-20261021T073400Z.log`.
Full scoped result and agent-use summary:
`/private/tmp/sm-workflow-sbt-capture-repair-20261021/parent/startup-installed-result.md`.
Independent review passed, including closure of the owned cleanup finding.
The delta is test/specification/enrollment only; existing normal publishLocal
results are reused without another publication. No real Start/Submit, successful
native check replay, Goal reset, commit or push. Unrelated work is preserved.

### Runtime distribution v3 — implementation and initial activation

The Human adopted `P64-runtime-distribution-stability@3`, including initial local
registration and the ordinary producer `publishLocal` pipeline. The bounded
20-path implementation spans development-workstation, CNCF, sbt-cozy and this
consumer. It materializes normal resolved runtime JARs and the actual task CAR
as independent regular files, admits ordinary staged Help, and atomically
selects the concrete installation. CLI selection and UoW-owned typed SBT share
the manifest through ordinary resolved configuration. The plugin maps typed
dependency classpaths; CNCF requires its check task before the original SBT task.
There is no global lock, new product main, mutable-classpath fallback or cache
isolation. The Human explicitly permits the actual overlapping verification
under sbt-native exclusion.

Focused CNCF 40/40 and sbt-cozy 21/21 tests passed; both ordinary dependency
`publishLocal` invocations succeeded. The newly enrolled installed consumer
specification compiled with the existing test sources. Python launcher 5/5,
runtime helper 15/15 before the final resolver correction, the targeted YAML
scalar regression 1/1, and final resolver/recovery 13/13 passed with their
individual input boundaries retained. Independent source review passed the
20-path scope and the subsequent bounded workstation correction. None of these
results alone proves the live CLI/UoW publication-overlap scenario.

The actual first application publication, update
`2dc48881-5dba-4f21-b85a-df8fab8fb8cc`, completed CAR publication but failed runtime
resolution before activation. Coursier repeated an identical Core coordinate/file
association in its graph. The corrected resolver collapses that association and
continues to reject conflicting pairs and duplicate classpath entries. The
original publication, runtime failure, resolver output and command failure are
preserved. No successful CAR publication was replayed to repair this result.

The explicit failed-runtime-stage operation used the original completed
publication and actual task archive, under the parent-observed absence of an
intervening application publisher. It created new linked evidence at update
`c208699a-6b05-44da-b976-cfe281113be9`, admitted ordinary Help and returned Activated
for installation `a0802d3e-c190-49ad-afc3-1e85143964fd`. These local source records
do not authenticate historical bytes or promise concurrent publication exclusion.
This is a bounded recovery of the failed stage, not the normal publication path
or an implicit retry of an unknown result.

The first selected overlap validation stopped in runner preparation before any
checker, platform submission or SBT. The renderer returned successfully, but its
immediate output was not passed to the retention parser. Its actual raw state
and the unconsumed original request are being retained for the existing
pre-submission recovery assessment. Live overlap acceptance remains pending.
The real development Session, accepted E1–E4, attempt2/intent230/source225,
candidate, Goal/history/counters and unrelated changes remain untouched.

Actual native inputs/results and agent records for v3 are retained under
`/private/tmp/sm-workflow-sbt-capture-repair-20261021/runtime-v3/`. Application
publication and recovery evidence are under the corresponding UUID directories
in `target/cozy/local-application-update/`. No commit or push has been performed.

### Runtime v3 — actual overlapping publication and remaining applicability

The earlier runner preparation stop used the existing admitted same-child,
unconsumed-request retention recovery; its raw stop and all subsequent outcomes
remain separate. Actual overlap01 then failed because its isolated plugin build
omitted the normal repository resolver. Actual overlap02 correctly rejected the
fixture's ScalaTest3.2.19 against the selected complete runtime's3.2.10. Both
failed requests and actual installed UoW results are retained without replacement.
The fixture resolver and aligned runtime declaration were corrected within its
qualification scope; this alignment is not proof for the actual application.

Overlap03 reached actual G1 UoW Test PID50435, then normal concurrent producer
`publishLocal` completed CAR publication and automatic runtime activation for
update `d3f11710-67d1-449e-b8b9-02968647a849`, installation
`d3dc97aa-9891-46fd-9dd9-4a728e06bba9`. The normal publication command returned0;
no separate install invocation performed this switch. Old Test50435 then exited0
and its original installed CLI returned ReviewStep. Its CNCF and Core class and
resource origins are copied G1 files under installation a0802d3e. The outer test
subsequently failed while interpreting a JVM synthetic Lambda log entry whose
source is its named host rather than a file URL. Consequently this is retained
partial live success, not completed acceptance of every preservation assertion.

The bounded parser correction checks the exact synthetic Lambda host and its
observed ordinary installed-file origin. The retained-success qualification
branch uses original capture0, actual terminal0, marker, module vector, PID and
saved check intent, and writes fresh CLI captures at unused ordinals. It requests
only description/Continuation for that successful isolated Session plus one
separately new G2 Session's necessary Test. It neither fabricates the original
OS exit nor repeats G1 Test or successful G2 publication. Independent source
review passed this correction; its changed-input execution is pending.

**CB-RUNTIME-V3-003 remains open.** Both current sm-workflow and sm-workflow-dev
project.yaml declare ScalaTest3.2.19 in Compile and Test; the updater's independent
CNCF-root-only resolution selects3.2.10. The exact mapping intentionally rejects
this real consumer conflict, including before Compile. The aligned fixture
cannot establish ready-to-resume for that consumer. A concrete proposed contract
correction is to materialize the registered application's normally resolved
ordered module closure from SBT, rather than substitute an independently resolved
CNCF-only closure. Keep actual dependency declarations and strict matching; do not
downgrade silently, relax checks or use mutable fallback. This contract decision
is awaiting Human response. It adds no real Session operation or restart.

All four touched repositories passed current `git diff --check`. The protected
development checkout still has candidate HEAD808bc324. Goal, accepted results,
Session, Plan/base, repair counts, previous failures and unrelated modifications
remain intact. No commit or push. Original execution evidence remains at its
private bound locators; the agent ledger and decision proposal are under the
previously recorded runtime-v3 evidence directory.

### Retained-success qualification completed

The changed-input retained-success qualification passed1/1 with actual SBT and
wrapper exit0, process terminated and sbt-native exclusion. It reused original
G1 captures and saved intent, observed fresh G2 description/Continuation of that
same successful isolated Session, and retained complete record/payload, Plan,
base, candidate, history, ordinals, counters and the one original Test PID. A
separately new G2 isolated Session performed its one necessary successful Test;
both CLI and UoW Test class/resource origins belonged to the selected concrete
installation. There was no original G1 Test or G2 publication replay.

Typed intake/result allocation:
`.codex-workflow/disposable/a-51272eaa-f095-42ff-88b0-869409a4e027/`.
Actual log and summary:
`.codex-workflow/disposable/a-cf716b0f-425f-4939-ae04-6f24105c04a3/51516-20261021T100528Z.log`.
This completes the retained overlap/preservation qualification for its explicitly
aligned fixture. It does not close CB-RUNTIME-V3-003 or authorize real consumer
operations. The earlier failed outer test remains failed historical evidence.
The contract decision above remains pending; no further implementation or
publication is inferred from this success.

### Non-sbt-cozy source build applicability: Phase116 owner observation

The runtime-owner chat received the authorized Phase116 handoff and performed
read-only diagnosis. Its original installed CLI/UoW contract-compile actually
failed before compilation because CNCF's build does not define the injected
cozyCheckInstalledRuntime task. CNCF uses its own existing generation contract;
forcing sbt-cozy adoption is not prescribed. The causal source is the unconditional
application-level manifest propagation to every admitted SBT target in resolveC.

CB-RUNTIME-V3-004 records the missing target-build binding distinction. Beyond the
absent task, the current complete application closure demands a CNCF dependency
JAR in a build compiling CNCF itself, and has different ScalaTest selection from
that build. Skipping the check, changing only its task name, or the CB003 app
closure correction alone cannot establish safe compatibility. A generic target
SBT integration independent of Cozy generation, with explicit target dependency
binding, needs an accepted design before this frozen repair expands. Current
unsupported binding remains fail-closed; no fallback is added.

The private non-cozy-sbt-binding-assessment.md under the recorded runtime-v3
evidence directory contains actual source/observation, the proposed responsibility
boundary and required acceptance. Phase116 Session06016330fa63b5eac6097ff2b8ff42c2,
CC-01 issue-1, counts1/1/10, five staged documents and the owning blocked Goal are
unchanged. No command or native operation was run against that Session. This
record does not claim repair, validation, Phase completion or permission to
resubmit its previously accepted implementation.

The Phase116 follow-up prefers updating sbt-cozy only when a version change is
sufficient, and excludes implied permission for a new plugin/generation switch.
Read-only inspection confirms there is no existing sbt-cozy dependency to update
and its AutoPlugin is noTrigger. A version-only repair is not applicable. No
plugin/version/build/activation change or SBT execution was performed; CNCF's
unstaged diff remains empty and the same five CC-01 documents remain staged.

### Human decision: retain current scope; defer actual application applicability

On 2026-10-21 the Human answered the CB-RUNTIME-V3-003 application-runtime
closure proposal: 「現行範囲に留め、実アプリへの適用は保留する」.
The proposed change from CNCF-root-only resolution to the registered application's
complete normally resolved dependency closure is NOT adopted or implemented.
CB003 remains OPEN; aligned-fixture qualification is retained with its original
limits and does not establish actual-application readiness. No further publication
or actual Session operation follows from this decision.

The preceding clarification remains distinct: ordinary Compile/Test/TestOnly
should work without sbt-cozy, with UoW/workspace/capability/lifecycle controls.
The application CLI's installed manifest does not by itself justify forcing its
entire runtime or a Cozy-specific task onto every target build. Explicit target
runtime binding needs declared applicability and fail-closed admission when
unsupported. CB-RUNTIME-V3-004 records that separate applicability problem; this
CB003 answer neither authorizes a new generic plugin nor adopts the deferred
application-closure proposal. No new source/build contract is claimed implemented.

All previous implementation, failed and successful evidence, original Goals,
Sessions, accepted results, Plan/base/candidates/history/repair counts and unrelated
work remain retained. No reStart/reSubmit, replay of successful work, stage change,
commit or push was performed for this decision record.
