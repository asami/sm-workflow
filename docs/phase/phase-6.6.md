# Phase 6.6: Incremental SBT Failure Diagnosis and Repair

## Completed native execution

Session `f01d809d1b4bfe8a4b5c241dac301b81` reached `Terminal/completed`
through ordinary Advance060, retaining Plan1, the original base and D1–D4.
Native validation1435 passed all1774 tests across182 suites. Independent
ReviewPhase1438 accepted all sixteen Plan1 conditions; Submit057 accepted that
result. Final commit is `cd10dd8c1ba4681d5f9e107b56a84d505d02f70a`.
Earlier UNKNOWN and failed results and consumed repair counts remain historical.
No accepted result or successful check was replayed to establish completion.
Repository synchronization was deferred at closure and is handled separately.
P66-17/P66-18 planning additions below do not amend this completed sixteen-condition
Plan. The [retained-evidence reconciliation](../journal/2026/10/2026-10-26-phase-66-p66-17-18-evidence-reconciliation.md)
confirms both completed within the earlier recovery and continuation. Their stale
unchecked projection has been corrected; no successor work remains for these IDs.

## Historical D3 correction — 2026-10-26

The consumer reports D1/D2 accepted and committed, with D3's repair accepted and
source-history Human226 pending at repair count 1/10. Preserve that actual state;
the original planned baseline below must not reset it. Consume the
[SHR-C1–7 correction](source-history-recovery-result-collection-correction.md):
recover completed producer receipts first, connect same-authority source-history
normalization and public adoption, then continue D3 and D4 with applicable existing
results. RRC completion alone does not settle source-history recovery. No caller
bypass or reimplementation of D1/D2. See the
[English handoff](../journal/2026/10/2026-10-26-source-history-recovery-and-result-collection-handoff.md).


Status: completed — 2026-10-26
Planned: 2026-10-23
Owner: sm-workflow development chat
Predecessor: Phase 6.5; preserve ongoing Phase 6.4/6.5 work
Checklist: [Phase 6.6 acceptance](phase-6.6-checklist.md)
Specification: [incremental SBT failure diagnosis](../spec/incremental-sbt-failure-diagnosis.md)
Design: [diagnostic interaction](../design/incremental-sbt-failure-diagnosis.md)
Delivery detail: [D1 through D4](../notes/incremental-sbt-failure-diagnosis-plan.md)

Execution started on 2026-10-25 through the installed `sm-goal-phase` caller.
Plan `phase-6.6-incremental-sbt-failure-diagnosis`, revision 1, captures D1–D4 and
all sixteen P66 conditions. The original base is
`2110f194c11f031a600a2c117da1a1761f618890`. D1 was natively accepted and committed as
`cdefff3d2928c87973abf00ad5f06d850cf8b28d` after compile, lint and seven focused
tests passed. Its conditional Step assessment admitted without executing an
independent Step review. D2 was natively accepted and committed as
`51166691a39a2d5f87b00a6d84774ff73b35a467` after compile, lint and nine focused
tests passed. Its conditional assessment also admitted without independent Step
review; these Step outcomes do not establish integrated Phase acceptance.

D3 was natively accepted and committed as
`38b53fa5c689bf18b6ab095d001e21ba12da35b2` after Compile, lint and four
focused diagnostic-repair tests passed. Its conditional Step assessment admitted
without an independent Step review. The earlier test compilation failure and
source-history interruption remain retained. Public same-authority normalization
adoption superseded Human226 without answering it, preserved the old UNKNOWN and
eight successful prefix results, and resumed ordinary validation and closure.
D3 consumed repair count 1/10; ordinary D4 starts its own 0/10 Step window.
D4 was natively accepted and committed as
`1873451f8baa0c49215942a1c2cf02a8a60becd6` after Compile, lint and both
connected diagnostic cases passed. The actual public repair-policy direction
was admitted through response038, retaining the selected limit 10 and the
existing counts. This establishes Step admission, not independent Phase review
or publication of the combined diagnostic runtime.

The first actual `repository-full` validation (source1267) executed 1755 tests:
1751 passed and four failed, with a terminated SBT exit 1. That formal failure
is preserved. The qualified RRC first-Phase-failure correction then issued an
ordinary whole-Phase `RepairStep` through response045, with all four closed
Steps and Plan1/base intact, automatic count1 and ordinary count1/10. No prior
Phase review is claimed. Response046 captured the bounded Cozy dependency
proposal; actual Human1273 approval was formally accepted through response047.
That approval resumed `ordered-repair:1275`, expected revision1276.

The user selected **Cozy開発** as the owner of the two-file generator repair,
its tests, independent review and normal publishLocal. Consumer catalog
corrections and integration of the delivered first-failure RRC source are
prepared in this checkout. Cozy delivery subsequently passed 51 tests across
three suites and independent review; normal publication of repaired Cozy
0.3.3-SNAPSHOT and the unchanged SimpleModeler prerequisite succeeded. Existing
normal generation consumes those artifacts. Native validation1325 then passed
1773 of1774 tests, retaining one failed exact ABI catalog assertion. The next
ordinary repair corrected the service-versus-Workflow operation distinction
without weakening public input/type/multiplicity assertions. Native
validation1378 passed all1774 tests across182 suites, with zero aborted,
canceled, ignored or pending tests and terminated SBT exit0. Both earlier
formal whole-Phase failures remain retained.
The RRC source transfer preserves the P66 diagnostic additions and excludes
unrelated producer PA/DSR development. At the first whole-Phase repair, the qualified RRC image was
installation `843f164a-bb11-4ab2-9cc6-9ac2f4be407e`, update
`6dbe77da-eaeb-4707-875c-283423b59502`; this image predates the new diagnostic
operations. The first actual independent Phase review1381 reviewed the complete
115-path candidate tree `bb507f15e13c1a5697014423c39fe23ec88d6d9e` and found
fourteen conditions satisfied. Its P66-11/P66-12 findings require combined
publication, installed qualification and usable adoption handoff. Public
Submit051 accepted that repair judgment; separate Advance052 issued
`ordered-repair:1383`, expected revision1384, with automatic3/ordinary2/limit10.
The existing sm-workflow development chat owns that normal producer delivery;
the consumer owns the document correction and subsequent native continuation.
At that handback, combined delivery, review acceptance and closure remained pending. No Start,
accepted Submit or successful validation was replayed.

Normal combined producer publication subsequently completed through the existing
LocalApplicationUpdate registration: update
`9495ae12-99d6-427a-a18c-efe8395f10ec`, installation
`93ffa228-f7d2-49d1-a53d-87d1340896e3`, coordinate
`org.simplemodeling.textus:textus-sm-workflow:0.1.0-SNAPSHOT`.
Its publication receipt records Completed and Activated. The producer retained
the entire normally accepted image, with its original identity and metadata,
for isolated installed diagnostic qualification. Actual installed02 qualification
passed both Compile/Test cases with SBT/wrapper exit0 and confirmed termination.
It exercised retained compiler failure, managed information/Evidence/Journal,
manifest/history, a later behavioral failure, successful Compile plus three
focused behavioral tests, and normal Step continuation in each isolated Session.
Original installed01 failure/partial fixture effects remain retained. The
[English D4 handoff](../journal/2026/10/2026-10-26-phase-66-d4-connected-adoption-handoff.md)
records the usable boundary and actual receipts. This completes the reported
delivery work; P66-11/P66-12 remain subject to normal consumer repair admission
and independent review. No live Session was operated solely for demonstration.

On 2026-10-26 the consumer recovered the producer's original terminal receipts:
`sm-rrc-20261025-publish01-result@1` reports successful publication with exit 0;
the publication observation reports Completed and runtime Activated for update
`f78e8820-0768-4b63-a9cb-fe575077299a`, installation
`d19b540a-647f-4dc6-84ed-f510037183ad`. Original qualification receipt
`sm-rrc-20261025-installed03-result@1` also reports exit 0, and its retained
stdout records the two selected retained-failed/timeout recovery cases passing,
with final completion at 2026-10-25 22:25:28 JST. The producer chat's capacity
failure did not undo those completed commands.

The producer subsequently delivered its consolidated result on 2026-10-26:
all nine known original command receipts accounted for, 236 distinct focused
tests across twelve suites passed, independent review and two closure passes
without a remaining blocker, and six distinct isolated installed cases passed.
The installed cases cover the ten-batch ceiling/tenth-batch success, early
evidence-based recurrence stop, legacy 3-to-10 policy adoption, fresh
failure-to-Release-FIX, saved-failure adoption/FIX and confirmed-timeout recovery.
These are focused/isolated results with test-owned decisions, not a new full-suite
run or real consumer consent. Successful commands/publication were not repeated.
Producer qualification and delivery are complete. The consumer selected the
subsequent qualified SHR image (which contains RRC) and completed live comment
normalization adoption/continuation. At that earlier delivery, public repair-policy adoption and RRC-06/08
consumer acceptance were distinct pending work; image selection alone did
not adopt a new policy in the existing legacy Session. Actual response038 and
the subsequent first-failure repair above now establish policy adoption and
consumer repair dispatch; combined diagnostic delivery subsequently completed as recorded above. Detailed evidence is retained in the
producer's execution archive through 2026-11-25.

The separately adopted source-history/result-collection correction requires
runtime reconciliation of the current comment under the retained rule and
effective date, preserving the old UNKNOWN, successful prefix, pending question
and counts. Reuse existing user direction through its delivered public same-Session
route; a missing normalization receipt alone is not a request for another
consumer permission. That public route is now delivered and live adoption succeeded through retained
responses 017 (description), 018 (independent preparation), 019 (adoption),
020 (ordinary resumed checks), 021 (normal Step authority), and 022 (D3 closure
and D4 issue), in the existing task directory. The selected image is installation
`54ea51ef-6e14-405a-8938-532b6d2c9a23`, update
`9e0d06c6-dc27-4f00-820f-5f0b7976cd04`. Producer installed qualification passed
nine isolated cases; this is distinct from the now observed live D3 continuation.
The original policy direction and actual old terminal command evidence were
recovered from retained chat execution results; no successful command was rerun
for collection. New P66-17/P66-18 planning conditions do not silently rewrite
native Plan revision1 or reopen accepted D1/D2/D3. That D4 source integration of
qualified RRC/SHR with D1-D3 preceded the combined distribution recorded above.

The first checklist projection recorded fourteen independently satisfied conditions
and qualified delivery for the other two. That first review remains a repair
judgment; subsequent ReviewPhase1438 accepted all sixteen conditions and Advance060
completed the Phase, as recorded in the completion summary above.
The completed Phase 6.4/6.5 Sessions and their evidence are retained.

## Next-delivery scheduling clarification — 2026-10-23

The [completion-first, best-effort sync correction](../notes/goal-phase-post-completion-sync-plan.md)
is the next independent delivery before D1-D4. It has its own GPSYNC checklist;
it does not expand P66 acceptance or require ongoing 6.4/6.5 to redo accepted work.
Deliver the local completion/safe-deferral route first rather than requiring
general dirty-worktree synchronization. At that scheduling decision Phase 6.6
remained planned; its later completed execution retained that diagnostic scope
and predecessor/adoption policy.

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
