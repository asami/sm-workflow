# Phase 5 clarification: remove unnecessary control checks and internal expiry

Date: 2026-10-05 (Asia/Tokyo)
Status: adopted into Phase 5; implementation and verification pending

## Direction and placement

The user clarified that [Phase 5](../../../phase/phase-5.md) achieves its
"minimum practical vertical slice" by removing unnecessary checks, including
hash-based checks, as well as simplifying execution routing. The plan now makes
that removal an explicit completion condition and includes internal expiry
removal in the same scope. The [checklist](../../../phase/phase-5-unnecessary-checks-checklist.md)
tracks both. Phase 6 inherits this simplified contract when generalizing it.

The initial placement in Phase 3 was mistaken and has been undone. The local
checkout lacked the newer Phase 5 plan; its existing content was retrieved from
GitHub main (file blob `33dcb6c079d532a035e96b672bc8458deb0f17bb`)
before these additions. No new Phase is created and no earlier Phase is reopened.

The removal covers all management hash/digest checks, application-level tamper
detection/prevention, independent unchanged-artifact proofs, and redundant
invariant checks on supported workflow paths. It must not replace them with
full copies, snapshots, seals, whole-body comparisons or a new integrity ledger.
Actual contradictory inputs still fail at the appropriate typed boundary;
normal Git changes, command/results, test logs and independent review remain
the development evidence. Ordinary Git references are not management hashes.

## Confirmed behavior and intended change

StepClosureAdmission, GoalPhaseClosureAuthority, GoalPhaseHumanInteractionVerification,
SplitPhaseHumanAnswerVerification, SplitPhaseTypedStartHumanVerification and
RepositorySyncMergeReviewAdmission compare execution/evaluation time with
`validUntil`. An otherwise matching approval, human response or review can be
rejected solely because its deadline passed. CML, record codecs and tests also
carry or require expiry; this is not merely descriptive metadata. The finding
does not establish a universal 60-minute duration in the product.

Remove that internal local-development expiry policy through the connected
producer, callers, persisted records and tests. Preserve authorized scope,
current request/state, actual participant/permission and withdrawal semantics.
Keep event timestamps as facts where useful. Do not replace expiry with renewal,
extra approval prompts, hashes, copied bodies or unchanged-artifact proofs.
Legacy records must not be discarded or treated as newly authorized to make
them usable under the revised contract.

Network/process timeouts, actual operation-call lifetime and explicitly
time-limited external credentials are different concerns. The shared SBT
skill's permission expiry remains a separate shared-skill repair; no product
workaround or lock bypass is part of this adoption.

## Record scope

Only the Phase 5 plan, its supplementary checklist and this journal are updated. Runtime
source, current Phase 2 scope/progress, historical acceptance and shared skills
are unchanged. No implementation, product test, independent review, commit or
release is claimed. Completion requires executable cases showing that supported
paths require neither management hashes nor separate unchanged-content evidence,
and passage of time alone no longer invalidates applicable local evidence,
while concrete scope/state/permission contradictions still fail.

### 2026-10-09 R24 mechanical native result ownership

Following terminal Actual61/review, the parent corrects mechanical checkpoint
storage and consumption: compact native locators, legacy used-member projection,
one actual retained native result and declared generated document readability.
Unused independent input copies and full native-versus-mapping body certificates
no longer gate mechanical observation. Fresh planning reads actual current rows;
public advance drops its earlier-observation equality, and private capture uses
native atomic transition state/CAS/grants without duplicate copy preflight or
post-commit body proof. Required unavailable-input/readback uncertainty behavior
is preserved. Current estimates261, legacy/reopen, unused/used-row corruption,
document-body drift, native absence and stale-CAS scenarios are authored.
UNVERIFIED pending complete affected suites/read-only review. All remaining
typed Start/AI/scope/native/nested/framework/storage and wider adopted work stays
OPEN. Same full ACTIVE Goal/original9/history/repair3of3/frames, commands831.
No new policy/reapproval/frame/reset/checkbox or partial acceptance.

Actual62 subsequently compiles and runs18complete suites,261tests255pass6fail,
SBT1/wrapper1/lockreleased05:18:40JST. Independent bounded FINDINGS CB-R24-SPEC-
001..004 identifies stale mechanical scope/routing/current261 oracles and one
changed misplaced Then. Parent waits for both original turns to terminate, then
corrects only the two specs: actual used inputs/native result rather than copy
rejection, selected Preview Terminal for261, adjacent semantic GWT. Product code
is not changed to recreate removed checks or old tie. Original failure/review
kept, no PASS relabel. UNVERIFIED pending fresh same18 suites/independent closure.
Same full ACTIVE Goal/history/repair3of3/frames, commands832; no new policy/frame/
reset/checkbox/partial acceptance; all wider adopted OPEN obligations remain.

Subsequent Actual63 passes261/261 in same18 complete suites,0aborted,
SBT0/wrapper0/lockreleased05:33:34JST; parent current-request reader accepted.
Fresh independent specification closure CB[]/new HYG[]/DEV[] resolves four scoped
blockers. Original Actual62 failure and R24 FINDINGS remain unchanged. R24 bounded
verified, not full Phase/changed-data/storage acceptance. Same full ACTIVE Goal/
original9/history/repair3of3/frames, commands833; no policy/frame/reset/checkbox/
partial closure. All wider adopted TypedStart/AI/scope/native/nested/framework/
native full-facts minimization and later Phase obligations remain OPEN.
## 2026-10-09 R25 selected mechanical consumption

Parent proceeds after Actual63's261/261 and independent closure terminal PASS.
Mechanical consumers no longer repeat receipt metadata/target checks or build
synthetic copies only for that verifier. Selection resolves the actual requested
instance; CNCF owns native input admission. Mechanical scope consumes actual
retained next/current without historical Start/Context reread or independent
native-prefix copy comparison. Declared generated rows retain typed readability.
Four obsolete inert verifier tests removed; fixture moved; actual SQLite/operation
refusal and unused historical scope-row cases added. UNVERIFIED pending seventeen
complete suites/fresh independent review. Remaining AI/scope/producer/nested/
framework/full-facts and all wider adopted obligations remain OPEN.
Same full ACTIVE Goal/original9/history/repair3of3/frames, commands833. No new
policy/reapproval/frame/reset, historic result rewrite or partial acceptance.

Actual64's pre-render input provenance mistake is retained; existing revision16
permits one successor65 with original authority/subject/attempt/permission/argv.
Actual65 compiles17complete,258tests257pass1fail,SBT1/wrapper1/lockreleased; fresh
FINDINGS SPEC-001 duplicate fixture setup and DOC-002 stale manual, no additional
production regression. Both turns terminal before parent corrects only these
two items. Original failure/review retained. UNVERIFIED pending complete managed
suite/independent closure, sixteen already successful suites remain applicable.
Commands834, same full ACTIVE Goal/history/repair3of3/frames; wider OPEN unchanged.

Actual66 passes139/139 in complete changed managed suite,1complete/0aborted,
SBT0/wrapper0/lockreleased06:12:41JST,current-request reader accepted. Other sixteen
successful whole suites remain relevant, original65 failure/FINDINGS/64 stop
preserved. Fresh source closure PASS CB[]/newHYG[]/DEV[] resolves SPEC-001/DOC-002.
R25 bounded verified only. Remaining AI/scope/producer/publication/nested/framework/
full-facts/wider obligations remain adopted OPEN. Commands835, same full ACTIVE
Goal/history/repair3of3/frames; no policy/frame/reset/checkbox or partial closure.

R26 parent correction removes passive planning mapping full-copy inspection from
producer/encoder/old v1 decoder/nested completion/scope consumers and private
publication self-copy gates/duplicate native instance encoding. Actual Create
effect calculation remains unchanged. New both-origin codec and actual managed
native/physical-reopen cases distinguish historical input/revision copies from
the actual selected native computation. UNVERIFIED pending complete affected
suites and fresh independent read-only review. Same full ACTIVE Goal/history/
repair3of3/frames, commands835; independent-outcome/AI/scope/nested/framework/
full-facts/wider work remains adopted OPEN. No policy/reapproval/frame/reset,
historical rewrite, checkbox or partial checkpoint/Phase/Goal acceptance.

Actual67 compile failure before tests is preserved; after terminal/no review live
parent corrects only two first-value for-expression structures. Actual68 same17
complete suites subsequently passes261/261,0aborted,SBT0/wrapper0/lockreleased
2026-10-09 06:38:06JST; current-request reader accepted. Fresh protected-boundary
read-only source assessment Technical PASS CB[]/newHYG[]/DEV[] after nine whole
primary files6562lines/connected owners and semantic CAR/manual/CML review.
Original pending68 disclosure stays historical; parent admits subsequent result
separately. R26 bounded verified, not overall storage/changed-data/Phase closure.
Commands837, same full ACTIVE Goal/history/repair3of3/frames. Remaining independent-
outcome/AI/scope/nested/framework/full-facts/wider obligations stay adopted OPEN,
no checkbox/new policy/frame/reapproval/reset or historical rewrite. Evidence
../../../../.codex-workflow/phases/PHASE-5/checkpoint-68-parent-result.md.

### R27 snapshot wire/meaning separation (2026-10-09)

Parent removes snapshot producer self-decode/source comparison and reader
regenerated-JSON body comparison. Standalone/native/full legacy nesting now
uses declared wire fields/types/variants/decimal strings and existing size
bounds, reusing existing selected-facts declarations. Neither storage path
interprets the complete frontier or produces execution admission; actual
Workflow consumers interpret meaning at use. Their remaining duplicates and
all wider adopted removal work stay OPEN, not exempt. Storage-versus-selected-
use, deep malformed wire members and both legacy origins have new executable
scenarios, UNVERIFIED pending full affected suites/independent assessment.
Same full ACTIVE Goal/original9/history/repair3of3/frames, commands837, no new
policy/reapproval/reset/frame, historical rewrite or partial Phase acceptance.

Actual69 original/one admitted same-child recovery remain pre-render NONSTART/
unconsumed on erroneous bbb6 locator despite parent's actual bb46 dispatch;
correct original public inspection/recovery builder acceptance grants no start.
Both raw stops and unused ticket remain. Source audit finds ONLY CB-R27-001,
GWT actions after Then in two specs/three scenarios. Both turns terminal before
parent freezes/corrects outcome captures/expectation placement. Fresh current
Actual70 passes350/350 across28complete suites,0aborted,SBT0/wrapper0/lockreleased,
parent COMPLETE-current-request reader accepted. Fresh focused read-only M2
source closurePASS CB[]newHYG[]DEV[], original FINDINGS/pending disclosure retained.
R27 bounded verified, all remaining producer/consumer/AI/scope/framework/fullfacts/
wider work adopted OPEN. Commands838,same full ACTIVE Goal/original9/history/
repair3of3/frames; no newpolicy/reset/frame/reapproval/checkbox/partial acceptance.
Evidence ../../../../.codex-workflow/phases/PHASE-5/snapshot-70-parent-result.md.

R28 parent correction removes constructed snapshot's copied boundary/facts
comparison and emitted-record reinspection. Sealed W.Outcome kind supplies its
Projected disposition; Human calculation consumes it directly. Raw selected
Preview invokes document interpretation before rendering, without repeating
full-frontier classifications. Two new genuine behavioral scenarios, complete
28-suite regression, static CAR and independent read-only assessment pending.
No new schema/admission certificate/native grant, management digest or planning
rerun. Actual state/scope/CAS/selected-document numbering/application remain at
owners; remaining duplicates/full-facts and wider adopted obligations stay OPEN.
Same full ACTIVE Goal/original9/all history/repair3of3/frames,commands838; no new
policy/reapproval/allowance/frame/reset or partial Phase acceptance. Frozen scope
../../../../.codex-workflow/phases/PHASE-5/projection-71-boundary.md.

Actual71 current-input352/352 in28complete suites,0aborted,SBT0/wrapper0/lockreleased,
completed2026-10-09 07:39:16JST; parent COMPLETE-current-request reader accepted.
Independent protected-boundary SOURCE PASS CB[]newHYG[]DEV[],wholefive1008lines/
connected actual owners/manual/CML; original pending-execution disclosure remains
historical and parent terminal admission separate. StaticCAR114OK347WARN0FAIL/
exit0, existingHYG/runtimeHelp/package limitations remain. R28 bounded verified,
not whole raw/native/consumer/fullfacts/Phase/Goal acceptance. Commands839,same
full ACTIVE Goal/original9/history/repair3of3/frames,all remaining adopted OPEN
unchanged; no newpolicy/reapproval/allowance/frame/reset/checkbox/historical rewrite.
Evidence ../../../../.codex-workflow/phases/PHASE-5/projection-71-parent-result.md.

## 2026-10-09 R29 owning advance dispatch and passive frontier

Parent corrects ManagedPlanningFrontier/ManagedAdvanceServiceOperation and one
real native behavioral scenario. Passive frontier read no longer performs raw
meaning classification or duplicate whole native history-prefix equality. Public
dispatch carries its existing decoded command and observed original Start mode,
avoiding redecoding and another whole frontier read solely for PREVIEW/APPLY.
Selected handlers still observe their own actual Core/native/mode/state/CAS and
effect authority; selected source/instance scope joins remain. Underlying CNCF
prefix equality is still adopted OPEN, not accepted as necessary or fixed here.
Real SQLite passive read versus refused use/no append/reopen and28complete
causal suites/staticCAR/fresh readonly source review pending. No new public
schema/token/foreign-Observed method/grant/copy proof. Same full ACTIVE Goal/
original9/history/repair3of3/frames,commands839 unchanged; no policy/reapproval/
allowance/frame/reset/checkbox/historical rewrite/partial Phase acceptance.
../../../../.codex-workflow/phases/PHASE-5/frontier-72-boundary.md.

Actual72 pre-render accepted-helper provenance stop and all four native calls
retained; no SBT/platformsubmission occurred. Existing revision16 ONE successor73
keeps original subject67/attempt72/permission72/authority/scope/argv/effects;
old72 do-not-resume, no renewal/sharedcontract/new frame/reset. Actual73 current
complete-request reader accepted352/352,28complete/0aborted,SBT0/wrapper0/
lockreleased,completed2026-10-09 08:04:53JST. Fresh protected independent SOURCE
PASS CB[]newHYG[]DEV[],wholethree5462lines/actualowners/manual/CML/completeCAR,
original pending cutoff preserved separately. StaticCAR114OK347WARN0FAIL,
existingHYG/runtimeHelp/release limitations unchanged. R29 bounded verified,
not whole Phase/Goal. Commands840,same full ACTIVE Goal/original9/history/repair
3of3/frames. Framework prefix/selfappend/fullbytes/readback and Preview/APPLY
wholecopy/payload proofs plus wider adopted work remain OPEN, not exempt or
DEV deferred. No newpolicy/reapproval/allowance/frame/reset/checkbox/historical
rewrite. ../../../../.codex-workflow/phases/PHASE-5/frontier-73-parent-result.md.
