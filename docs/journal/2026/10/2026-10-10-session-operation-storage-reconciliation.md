# Session Operation storage reconciliation

Date: 2026-10-10, Asia/Tokyo.
Status: initial reconciliation followed by parent-owned connected source/spec
implementation. New code is UNVERIFIED; no integrated runtime or independent
acceptance is claimed. The initial-turn evidence below remains historical.

The preceding goal turn only confirmed already completed Actual105; it was not
new implementation progress. The current turn inspects actual source to begin
Phase 5.1's first connected work item, under the already adopted Phase 5 direction.
The side handoffs remain historical planning-only records, not execution grants.

## Findings that change the implementation path

1. CNCF actually provides optional `SecurityContext.session.sessionId`; the normal
   `ThreeWorkflowLauncherAssembly` does not connect it to native Workflow state.
   This disproves an assumption that a new generic host session abstraction is
   needed, but does not prove implicit Codex chat identity is supplied.
2. `WorkflowInstancePersistenceBinding` exposes create/load/append, not session
   search. `DataStoreSpace.search` and canonical searchable `SqlDataStore` exist.
   Reuse scoped managed query facilities instead of a private SQL connection or
   falsely advertising a session lookup already implemented by instance identity.
3. `ComponentRecordStorage.stageCreateC` is genuinely usable as declarative local
   Create in `WorkflowManagedMechanicalTransition.executeC`; preparation rejects
   local guards and other effects. It cannot update a session aggregate with CAS.
4. Goal/Repository Start use initial create, activation append and then mechanical
   receipt. Split Start uses initial create/activation append and no mechanical
   receipt. The mechanical API admits active sources only. Consequently adding a
   post-Start association Create, or applying the existing mechanical API to a
   NotStarted source, cannot establish atomic session/Start publication. This is
   the native storage boundary to reconcile, not a reason to add copied proofs.
5. Current `NativeWorkflowResultInput` resolves actual issued meaning by explicit
   request identity and still requires a result reference. It does not implement
   session-only selection. The existing native history retains suspension IDs,
   allowing candidate generations to be resolved without a new comparison ledger.
6. A sole pending request is not necessarily an unambiguous answer target. A
   completed older generation may still have an answer in flight. Any resolver
   that ignores those generations can close newer work incorrectly. Reuse an
   existing request selector only when this genuine ambiguity exists.
7. Split context owner is separate real business data selected through typed
   Start/partition preparation. A new session adapter must resolve it server-side,
   not merely remove `instanceIdentity` while still requiring a context echo.

## Connected specification and next implementation boundary

[Session context resolution](../../../spec/session-workflow-context-resolution.md)
defines R1–R5/E1–E12 for initial publication, explicit/actual implicit context,
three-service advance, multiple targets, multi-generation delayed answers,
result consumption and actual CAS. These are authored cases, not executed specs.

The next product correction must connect the native Start publication, actual
scoped association lookup and all three public consumers together. Determine
where real routing data is retained with Start before adding an adapter; do not
implement a side-write index or assume Create-only staging admits arbitrary
guards. Resolve actual result assignments in the same producer/consumer path.
No per-helper acceptance/re-review cycles, new verifier or unchanged-copy ledger.

This reconciliation leaves actual session/executor/result integration, remaining
unnecessary checks, resources/cost, integrated B/C, Phase6+, Help/package/release
and the full Goal open. It does not relocate current Phase 5 obligations.

## Evidence and continuity

Inspected complete normal result ingress, three-service launcher, native Goal/
Repository Start, typed Split Start/preparation/storage, actual instance binding,
mechanical transition/program capture, fixed-target Component record storage;
selected provider search and instance wire/read/write paths. Source observation
is neither a runtime result nor independent assurance.

No registered runner/readonly reviewer spawned, SBT/CLI/paid AI invoked or execution
request/permission allocated. No active test input changed. No product source,
shared CNCF/textus-ai code, deployed runner, Git commit or release changed.
Same native Goal01a103b3-5f45-7ec2-ac5c-7f9fb01df42a remains ACTIVE; original nine,
originals/full history/R5/A7/unknown null/repair upper3 consumed3/all existing
frames unchanged. Ordinary completed executions remain873, separate from repair
accounting; old failures/source findings are not relabeled PASS.

## Subsequent main-task implementation — 2026-10-10

The next Goal continuation made actual source progress, not another planning-only
pass. Parent applied the scoped CNCF patch through the first approved elevated
`apply_patch` call; no sandbox probe, direct SQL, SBT or Git write was used.

- Native initial create retains optional actual invocation session and Workflow
  family in the same row. No side-write index, copied command/policy or additional
  session engine is created. Existing CAS patches preserve routing fields.
- Managed `WorkflowInstancePersistenceBinding.findBySessionC` returns all stored
  candidates inside the existing Subsystem lease. This is not authority, latest
  selection, successful activation or completion of Start. No-session legacy rows
  stay readable and are not silently reassociated.
- Normal three-service assembly wraps Start/advance with opaque explicit or
  actual implicit session. Start consumes its original business input; advance
  resolves actual associated state. Several instances require a real instance
  selector. Split discovers its actual owned partition records in the already
  declared managed collection, requiring a context selector only for ambiguity.
- Reduced result input resolves the applicable native issue history, including
  completed generations. A request selector is no longer universally required;
  ambiguous or foreign selection cannot silently select the newer pending task.
  Current result value/reference/evidence remain consumed by existing completion.
  Historical identity-only and full-envelope paths remain supported.
- Authored native storage spec covers exact opaque identity, legacy/other-session
  scope, allocation vs Start, multiple candidates, duplicate/stale conflicts and
  physical SQLite reopen. Existing whole normal Goal/Repository/Split Operation
  specs now exercise implicit/explicit session routing and fresh runtimes. Goal's
  actual late-generation case refuses missing selection without changing pending
  history; Split Preview uses a sole issue result without a request-ID echo.

No source implementation or authored test above is a passing result. The first
fresh validation selection is ordinary109: two complete CNCF provider suites
(three new cases and ten inherited provider cases; actual count from execution).
Downstream SNAPSHOT refresh, all affected complete functional suites and one
integrated read-only review are still required. Do not create a separate helper
acceptance/re-review cycle or claim consumer compilation from producer tests.

Remaining edges include repeated Start under a different/legacy association,
multiple/foreign target selection through public Operations, malformed unrelated
partition rows, all semantic/Human result families, required reference consumption
and actual provider execution. The existing full Phase5/later/Help/package/release
Goal stays open. Original nine/history/repair3/3/frames remain unchanged; ordinary
completed executions are still873 before any actual109 terminal result.

### Actual109 and in-operation routing correction

Actual109 completed13/13, two complete native suites, SBT0/wrapper0/shared
lockreleased. Parent's complete-current-request public reader accepted the actual
result/log/summary; [original result](../../../../.codex-workflow/phases/PHASE-5/session-109-parent-result.md)
preserves its exact scope. Ordinary count874; repair3/3 and frames unchanged.
This does not cover subsequent source or compile the normal consumer.

Source consumption exposed another real boundary: the general
`ExecutionContext.withSecurityContext` rebinds RuntimeContext and creates a new
UnitOfWork. It is suitable before an invocation, not for attaching a routing
identifier inside a live Operation. Parent therefore added the generic public
`ExecutionContext.withRoutingSessionIdentity` API, preserving actual runtime/UoW,
response lineage, actor/capabilities and authentication attributes while changing
only the opaque session identity. The existing security setter was not changed.
The component uses this actual API rather than inaccessible private copies or a
fabricated context; an already supplied implicit session uses the original context.

The native spec now also observes the same live UoW and unchanged actor capability
ownership. That resource-identity assertion prevents orphaning pending effects;
it is not a saved-copy/unchanged-data certificate or a new runtime verifier.
Current source/specs after109 are UNVERIFIED. Fresh110 selects the two complete
native suites again for this changed context scope (four new/native cases plus
ten original provider cases; counts only from actual execution). The old13/13 is
not relabeled as coverage of this API. Planned111 is an unallocated dependency
refresh draft and may run only after a successful actual110 result. No repair or
permission recovery is involved and no selected inputs were edited while109 ran.

### Subsequent execution, authentication-origin correction and114 preparation stop

Native110 passed14/14; derived111 local SNAPSHOT refresh succeeded, zero tests.
Connected consumer112 compiled and executed ten whole suites69 cases:65 passed,
4 FAILED at actual participant authentication admission for Goal/Repository/
Split session Start. Original failures are retained, not revised to PASS.
The public parent validation reader correctly rejected112 as acceptance.

After runner FINAL, parent traced SecurityContext copying to loss of existing
internal authentication origin, despite equal principal/capability values.
Public routing API now uses existing SecurityAuthenticationProvenance.inherit;
Goal/Repository fixtures use this API rather than reconstructing their security.
No actual actor/consent/state/CAS/effect gate is relaxed, no new verifier or
comparison proof. Native113 passed18/18 including provider/local/unadmitted
origin behavior, same live UoW and existing provenance regressions. Parent actual
terminal/log/summary/current public reader confirmed SBT0/wrapper0/lockreleased.
Ordinary executions878; original repair3/3 and every frame unchanged.

Fresh derived114 did NOT start SBT. History helper accepted existing standing
authorization, but runner misread the validation-only branch's exclusion as
also excluding standing authorization for local dependency refresh. Read-only
clarification acknowledged that local interpretation error; original stop held.
Parent independently confirmed original typed intake/live unconsumed permission
and no executor evidence. This is NOT a platform refusal or test failure.

Existing automatic recovery cases do not cover this pre-render dependency-refresh
stop. The [114 stop/proposed narrow recovery](../../../../.codex-workflow/phases/PHASE-5/session-114-pre-render-standing-stop.md)
retains original raw helper/input/child history and exclusions. No shared skill
or runner definition is changed, no replacement/replay allocated.115 is not
prepared; corrected consumer remains UNVERIFIED, integrated independent source
review pending, every full Phase5.1 acceptance obligation still OPEN.

Normal Split discovery was also corrected before consumer validation: it selects
actual owner before decoding interpretation data, parses a selected row once,
and does not gate selected work on unrelated malformed rows. The direct pending
case now stores such a foreign row in the declared `split_contexts` collection
and requires unchanged original work across explicit-session runtime reopen.
This is authored coverage, not executed evidence. Integrated tests/review and
all whole-Goal acceptance remain open.

### Independent connected source review after113 — original FINDINGS

Fresh read-only `/root/session_source_after113_readonly` (Sol/high) reviewed the
whole14 selected programs and actual consuming paths; its FINAL is SOURCE
FINDINGS, not runtime acceptance. The complete
[original report](../../../../.codex-workflow/phases/PHASE-5/session-source-after113-review-original.md)
retains both stable current IDs and all nonblocking observations. CPB-SESSION-
AFTER113-001 finds that Direct Preview's common session helper removes the
deliberately unissued request selector from its negative test; actual resolution
then receives a valid unique-generation answer rather than the intended foreign
selector. CPB-SESSION-AFTER113-002 is current private Scala identifier conformance
for the two new native session field constants, not a persisted-format or runtime
routing defect. Their wire strings must remain unchanged.

The reviewer traced existing authentication origin/live UoW, all native history
generations, declared context owner and managed storage/commit boundaries. No
other current connected-source blocker was reported; this is not blanket
approval of wider remaining checks. Four HYG proposals are inherited maintenance,
DEV empty, no incidental cleanup or new acceptance gate. Original R8/five reviews,
repair3/3/all frames/ordinary878 remain unchanged; source review creates no
allowance or Phase completion. Source/spec inputs were held through FINAL.

The [bounded parent proposal](../../../../.codex-workflow/phases/PHASE-5/session-source-after113-parent-disposition-and-proposal.md)
covers only these two IDs in three program files (negative action, two private
member names and one spec reference), not implemented or approved.114's separately
recorded procedural limited recovery still awaits human direction.112 remains
65/69 with4FAILED,113 producer18/18 PASS; corrected consumer remains UNVERIFIED,
115 not allocated/spawned. All whole Phase5/5.1/later/Goal obligations stay OPEN.

### Human-approved two-source corrections and114 procedural continuation

Later human direction adopts the recorded two CPB IDs and114 procedure correction/
limited recovery only. Parent negative-selector/private-field/reference correction
is applied. Fresh /root/session_after113_two_cpb_readonly_closure returns SOURCE
PASS/CB[]/DEV[], original HYG retained OPEN; original whole14 FINDINGS untouched.
Its [complete source disposition](../../../../.codex-workflow/phases/PHASE-5/session-after113-two-cpb-source-closure.md)
distinguishes source resolution from unexecuted consumer/provider verification.

Procedure Python original27/3fixtureerrors retained; parent-only complete-subject
fixture correction subsequently passed27+14+12=53/53 with dedicated execution
agent. Independent six-file procedure review PASS; same standing admission helper
unchanged. Selected regular-file cncf_command_runner23 copied from canonical
development-workstation only (deployment revision10), not wholesale sync/symlink.
Auxiliary metadata lint lacks managed PyYAML, no install/substitute or false PASS.

Only one new114 request/result/derived child retains original114 attempt/live
permission/authority/scope/subject106/exactpublishLocal/local outputs. Native
source observed again by parent/public dispatch; no new permission or repair frame.
Fresh /root/sbt_receipt_cb9ba02c8acb484584a9 initially stopped before history helper,
misclassifying its overlooked actual native turn as unavailable. Read-only factual
correction and independent existing-procedure applicability review admit this SAME
loaded23 child doing its UNATTEMPTED selected-message preflight, not another
successor/recovery. Exact observable native text only, no inferred LF/saved text
or [] for present origin. Parent verified current SAME native ACTIVE Goal/public
live selection and issued that bounded continuation. At this cutoff real SBT/
publication pending;115 unprepared. Original22 child DO-NOT-RESUME throughout.
Original9/all history/R8/R5/A7/null/repair3/3/all frames/ordinary878 preserved;
no Step/Phase/wholeGoal acceptance or original failure relabeling.

### Actual114 refresh and115 ten-suite consumer terminal PASS

The sole fresh114 child completed its unattempted native preflight under the
existing applicable selected-message procedure. No inferred whitespace/saved
native text or changed input after rejection; actual helper standing acceptance,
normal renderer/checker, separate first scoped executor. Parent public local-
publication reader confirms original114 SUCCESS/TERMINAL/SBT0/wrapper0/lockreleased
and consumed original permission. Actual current native main1source compiled;
original local SNAPSHOT published, zero tests. Original stops/clarification kept.

After that exact successful successor predecessor, fresh115 selected SAME ten
complete consumer suites/69cases with current subject107. Registered Luna medium
runner/shared wrapper first scoped execution completed:69succeeded/0failed/
10completed/0aborted/0canceled/ignored/pending, SBT0/wrapper0/lockreleased. Parent
public current-request reader accepted complete actual result/consumed permission.
[Runtime evidence](../../../../.codex-workflow/phases/PHASE-5/session-115-parent-runtime-evidence.md)
selects original native log/summary, no copied/hash certificate. No input edit
while running or separate duplicate compile; no old112/114 replay or refund.
Native11318/18 predates private rename; changed provider-spec reference was
statically reviewed, not falsely newly executed by publication/consumer suites.

Focused source2CPB PASS retained; independent focused runtime-evidence closure
now requested, no new full review epoch/repair frame. Procedural Python53/53/
independent PASS and local publication do not establish whole Phase acceptance.
Ordinary880through115 separately; original9/all history/R8/R5/A7/null/repair3/3/
frames unchanged. Broad Phase5/5.1/R1-R5/E1-E12/executor/resources/cost/later/Help/
package/release/wholeGoal remain OPEN.

### Independent focused runtime-evidence closure — two IDs only

Same fresh read-only reviewer /root/session_after113_two_cpb_readonly_closure
inspected actual114/115 records/logs/summary/terminal markers and selected source
scenarios. FINAL focusedPASS/CB[]/DEV[],001/002closed, inherited4HYG retainedOPEN.
Original SOURCE PASS/runtime-unverified cutoff and original FINDINGS not replaced.

> Owner: sm-workflow. Independent focused runtime-evidence closure resolves CPB-SESSION-AFTER113-001 with the preserved source correction and actual115 ten complete consumer suites, 69/69, SBT0/wrapper0/lockreleased. The complete Preview scenario exercises preserved unissued-request selection, refusal and unchanged pending/native state, positive selector omission, completion, duplicate acknowledgement, terminal and reopen behavior. Original112 failures and earlier source-only cutoff remain historical. This closes only the approved correction.

> Owner: CNCF. Independent focused closure resolves CPB-SESSION-AFTER113-002 using the exact source-reviewed private identifier/reference rename, actual114 current-main compilation/local refresh and actual115 connected consumer PASS. Serialized names, query/CAS and public behavior are unchanged. The renamed provider-spec reference is source-assessed; no post-rename provider-spec execution is claimed. Inherited Hygiene and broader acceptance remain open.

No new full-review epoch, allowance, repair frame, counter reset or approval policy.
Original9/all history/R8/R5/A7/null/repair3/3/frames preserved; ordinary880through115
separately. Same native full Goal ACTIVE, Phase5/5.1/all broader acceptance OPEN.

## R5 follow-through: assigned semantic destinations, not response certificates

This continues the existing adopted Phase5/5.1 implementation, not a supplemental
repair frame or a change to the prior two-ID approval. Those two IDs and114
limited recovery remain closed at their actual evidence scope. All earlier
source cutoffs, failures, native results and Hygiene remain preserved.

Parent traced the public reduced submission, actual typed issued load and owning
completion. Partition's `SplitPhaseExplicitPartitionSemantic.Request.reply.result`
is the original host reply allocation used by `SplitPhaseTypedPartitionCompletion`.
Handoff-scope's `SplitPhaseScopeSemantic.Request.result` is the stored checkpoint's
assigned scope destination consumed by its scope completion/publication. Reasoning's
`SplitPhaseExplicitReasoningSemantic.Request.result` is the original prepared
assignment consumed by reasoning completion and successor publication. Returning
these same addresses supplies no new business information: Workflow already owns
the destinations and writes/retains the result against them.

Parent changed ONLY three sm-workflow program/spec files for this follow-through:

- NativeWorkflowResultInput.scala: reduced ingress retains optional raw reference
  data without universal decoding; once the actual issued input is loaded, the
  three assigned semantic destinations come from that input. Their absent or
  obsolete malformed echo is not compared, validated or used for routing. Other
  real result/evidence references are decoded when the selected completion consumes
  them. No native producer, storage format, authority, CAS or pool changes.
- NativeWorkflowResultSubmissionFixture.scala: an explicit test-only omission
  option projects the real semantic answer without an assigned destination echo;
  defaults preserve other real result/evidence references and request selectors.
- ThreeWorkflowDirectOperationSpec.scala: three semantic paths omit the echo and
  assert result has only value; session Preview adds a completed-answer retry with
  obsolete malformed reference and harmless diagnostics, requiring retained ack
  and unchanged history. Existing request-selector and historical complete-envelope
  crossed-destination/changed-judgment negatives remain intact. Durable reopen and
  separate-JVM paths carry the new minimal submission.

Goal/Repository issued requests and Split Human questions do not assign these
semantic result destinations. Their real result/causation and external host
evidence references remain supplied; inferring a Human evidence address would
fabricate authentication/consent evidence. No whole-contract waiver is inferred
from a branch's name. The historical complete-envelope route still delegates
explicit destinations to existing owning consumers; it is not silently rewritten.

Current diff whitespace check passes. This source/spec delta is **UNVERIFIED**:
no new SBT request/permission/runner has been allocated, no test or independent
review has run against it. Actual11569/69 predates it, not a reused PASS. Next
validation is one fresh current-input registered runner request for complete
ThreeWorkflowGoalOperationSpec (4), ThreeWorkflowRepositoryOperationSpec (8), and
ThreeWorkflowDirectOperationSpec (22), expected34 at prior suite counts; actual
footer determines coverage. Main/Test compilation comes from testOnly, no duplicate
compile or CNCF publication. Independent read-only connected review follows the
actual terminal result. Necessary assignments, consumed meaning/evidence, issued
generations, state/CAS/scope/authority are not replaced by a front-end verifier,
unchanged-copy proof, hash or new validated marker.

Same full native ACTIVE Goal/original9/all history/R8/R5/A7/null/repair upper3
consumed3/all frames/ordinary880through115 unchanged. No new repair allowance,
counter adjustment, Phase acceptance, staging/commit/push or release.

### R5 actual116 behavior PASS and117 subsequent fixture compilation

Fresh116 executed three COMPLETE connected public Operation suites:34/34,
Goal4/Repository8/Direct22,3completed/0aborted/failed/canceled/ignored/pending,
SBT0/wrapper0/lockreleased,04:09:40JST. Actual registered regular23/Luna medium
runner, parent-observed native origin/public V4 dispatch, selected intake,
retained renderer/shared checker/separate first scoped executor99693. Parent
full-current-request public admission confirms SUCCESS/TERMINAL/current subject108/
actual child/consumed permission; original log/summary/footer and stderr terminal
marker read. Product inputs held throughout; no duplicate standalone compilation.

ONLY after116 terminal, parent aligned the new test-only public parameter/caller
labels to omitReference, and corrected manual/index/guide/scope + normal-host
prose for actual minimal submission versus historical envelopes/external evidence.
These are parent implementation conformance edits, not new repair IDs/frames.
Fresh117 exact Test/compile covers subsequent changed caller/fixture under the
documented AFTER-edit exception; no repeated34 tests. Actual registered regular23/
Luna medium/shared wrapper executor23175, SBT0/wrapper0/lockreleased/public current
subject109 reader accepted,04:13:43JST/20s. Zero tests, inherited pattern warnings.

Parent reran installed CAR lint helper with first scoped execution:0FAIL/347WARN/
114OK/helperexit0. Later prose/naming need actual review, not false prior-lint or
runtime Help acceptance. CBD supported integration not configured, no probing.
Actual locators/counts/effects/cutoffs are retained in
[116/117 parent runtime evidence](../../../../.codex-workflow/phases/PHASE-5/session-116-117-parent-runtime-evidence.md).
Fresh independent focused whole-three-program + causal consumer + actual result/
public-doc review now follows. No new whole-Phase review epoch or all R1-R5/E1-E12
claim. Original4HYG preservedOPEN. Same fullACTIVEGoal/original9/all history/R8/R5/
A7/null/repair upper3 consumed3/all frames;ordinary882through117 separately. No
counter adjustment, new allowance, staging/commit/push/release or Goal completion.

### R5 assigned-destination independent closure — focused PASS

Owner: sm-workflow. Independent focused connected read-only review by
/root/session_r5_assigned_result_readonly_review, configured GPT-6.1 Sol/high,
returns PASS with no new Current Boundary Blockers. The three target programs
were reviewed through EOF with actual typed issued-work, owning completions,
native conditional persistence and evidence consumers. Reduced partition,
handoff-scope and reasoning submissions resolve their Workflow-assigned
destinations; obsolete destination echoes are unused input. Other consumed
result/artifact/Human evidence references remain supplied, and historical full
envelopes retain their owning contract. No new verifier or validation stamp.

Actual116 provides34/34 across three COMPLETE connected Operation suites,
SBT0/wrapper0/lockreleased. Actual117 compiles the subsequent test-only
omitReference parameter/caller correction, zero tests. Fresh CAR input reports
0FAIL/347WARN/114OK; later public prose was semantically reviewed, without
claiming runtime Help or prior-lint coverage. Four inherited HYG records remain
OPEN without duplication; no new HYG or DEV. The reviewer executed no commands
or tests and changed no files.

The complete original result is retained in
[independent review original](../../../../.codex-workflow/phases/PHASE-5/session-r5-assigned-result-readonly-review-original.md).
This closes only the focused assigned-destination implementation boundary, not
all R1-R5/E1-E12 or a new full-Phase review epoch. Remaining semantic
context/checkpoint echoes, multiple associations, delayed-generation/CAS runtime
coverage, actual AI execution/resources/cost, CLI/skills and broader release
acceptance remain OPEN. Native Goal re-read is ACTIVE with the same full
objective. Original9/all history/R8/R5/A7/null/repair upper3 consumed3/all frames
are preserved; ordinary882through117 remains separate. No new repair frame,
counter change, commit, release or Goal completion.
