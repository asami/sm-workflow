# Phase 8: Runtime Extensions and Practical Closure

Status: planned
Decision: 2026-10-05; documented 2026-10-06 (Asia/Tokyo)
Planned after: Phase 7, with carryover recorded from Phase 2 onward
Depends on: practical Phase 2, Phase 5 and the selected Phase 6.3/7 delivery

## Phase 6 split dependency correction — 2026-10-13

The earlier broad Phase 6 references in this document denote the accepted
[6 -> 6.1 -> 6.2 -> 6.3 family](phase-6.md#applied-split--2026-10-13), not
completion of the narrowed first member alone. Consume 6's client/modes/session/
provider contract, 6.1's execution/plan/Step-review contract, 6.2's FIX/reset/recording/
dependency contract and 6.3's integrated PHASE/FINAL/use evidence. Initial staged
review is owned by 6.1 and 6.3; it is not deferred to Phase 9. This Phase's identity,
scope and original historical decisions remain; no successor is started here.

## Safety Sandbox adoption boundary (2026-10-15)

Safety Sandbox is deliberately adopted only after the basic command-driven workflow functions are operational. It must not become a prerequisite that delays the foundation delivered by the earlier phases.

Use real sm-workflow operation as the development driver for CNCF Phase 110 and textus-cbd-support Phase 16. Map CNCF hierarchical sandbox scopes as Server -> Client/Command -> Operation/Worker, with the resolved Effective Sandbox carried by CNCF ExecutionContext. During initial CLI use, exercise Client/Command and Operation scopes against real Git/SBT/worktree/provider effects; when resident-server operation is selected, add the Server deployment scope without changing Operation semantics.

Gaps discovered by this use are fixed in the owning shared layer: CNCF for runtime capability/enforcement/loading admission, CBD Support for lint/review, sm-workflow only for workflow-specific scope/profile definitions. Do not add sm-workflow-specific direct filesystem/process/network bypasses. AI-generated workflow/component code must also not define and invoke its own `main`, Scala `@main`, App-style launcher or equivalent execution root; sm-workflow execution remains owned by CNCF Runtime/Operation boundaries. Reusable logic is an Operation/Service/API, while a real external executable is invoked only through admitted managed process execution.

Detailed rationale: [Safety Sandbox adoption as a development driver](../journal/2026-10-15-safety-sandbox-development-driver.md).

Adopt it incrementally against the real workflow in this order: (1) Command/Operation ExecutionContext scope, (2) worktree/resource boundaries, (3) managed Git/SBT process execution and arbitrary-shell rejection, (4) provider/network authority, (5) CAR Loading Admission plus CBD lint/review, and (6) Server Sandbox plus Client->Server ExecutionContext propagation when resident-server operation is introduced. Each slice is accepted through an actual sm-workflow scenario and may create a bounded upstream CNCF Phase 110 or cbd-support Phase 16 gap; re-consume the upstream fix before moving on. Do not build all sandbox families in advance.

## Goal

Use observed operational problems to select the work for this phase. A new
capability belongs here only when it resolves a concrete problem in supported use.
Close the current development rollout by resolving the necessary extensions
deferred from Phase 2 and missing functionality discovered through real use
after Phase 6.3 integrated acceptance and during Phase 7. Phase 5 validates
the foundation without creating skills; Phase 6 defines thinking modes and
creates/connects the initial skills. Follow the [incremental-use roadmap](README.md).
Phase 8 is not a prerequisite for gradual use and must not retroactively reopen
the accepted practical scope of an earlier phase.

Local LLM integration is reference information for developer-run manual trials,
not a Phase 8 or Phase 9 completion condition. During development, use the
[local-provider trial notes](phase-8-local-llm-reference.md) as reference
information. Results from those trials may justify a selected extension only
when real use shows a concrete need; the trial itself creates no acceptance gate.

## Carryover from Phase 2

The [2026-10-07 Phase 2 revision](phase-2.md#completion-ownership-and-carryover)
makes the following ownership effective for planning now, rather than waiting
for every historical checkbox to pass. Implementation/acceptance status remains
as recorded; no transfer claims completion.

The owning user's 2026-10-11 Phase 5.1 correction supersedes historical
intermediate-recovery, replay and competing-submission guarantees in this
carryover. Those requirements are withdrawn, not a mandatory deferred backlog.
Normal continuation after a requested answer remains supported; abandoning an
unsuccessful run means starting a new execution from the beginning, without
restoring uncertain intermediate effects. Retain historical IDs/results as
history and record withdrawn items as superseded. Reintroduction would require
an explicit new scope decision, not merely an old unchecked row.

| Original IDs | Remaining work allocated after practical Phase 2 |
| --- | --- |
| P2-T01..T05; transport remainder of P2-T06 | Remaining resident-server/MCP/CLI exposure and equivalence selected by actual need; historical concurrency/intermediate-recovery guarantees are superseded. Direct public Operations and required decisions remain the foundation, with Phase 5.1's new-execution fresh start. Initial skill-facing command/CLI wiring belongs to Phase 6 |
| P2-R01..R03 | Resident-reference refresh and generic management/Job visibility beyond necessary normal startup |
| P2-U05/U15/U16 and extended D/provider/event variants | Only breadth not needed by the selected practical scenarios; existing validated primitives remain reusable |
| P2-C01 | Phase 5 provides already-planned observations and Phase 6 initial-use feedback; broader comparative cost/round-trip evaluation belongs here, with benefit unproven until measured |
| P2-C02 and bundle notes | Initial skill creation/connection belongs to Phase 6; broader optional distribution/coverage belongs here |

P2-R04/R05 practical three-route assembly and real-effect requirements remain
Phase 2. Workspace-wide synchronization/overlay remains Phase 7. No complete
selected workflow, broken normal entry or required independent decision/review
is transferred here. Resolve individual extended rows by actual need during
use, retaining original IDs and existing evidence rather than re-inventorying
the complete history on each continuation.

The table is an initial ownership map, not an assertion that every feature is
unimplemented or a final decision to implement every historical idea. Preserve
the original Phase 2 checklist IDs and evidence in the Phase 2 planning record.
The user selected practical goal-phase, split-phase and repository-sync routes
on 2026-10-06. At the scheduled Phase 2 handoff, record each route and the actual
remaining behavior for each transferred item. Work necessary for any of these
three routes remains in Phase 2, including connected completion, required
effects, permissions and normal continuation under the corrected Phase 5.1
boundary; an entire selected workflow cannot be deferred
while claiming Phase 2 success.

| Origin | Extension owned here when not needed for the Phase 2 practical route | Handoff state |
| --- | --- | --- |
| P2-D01..D03; P2-U05/U15/U16 | Broader provider, transaction and event integration beyond the selected route | Separate necessary extensions from withdrawn recovery guarantees; retain existing partial validation |
| P2-G01..G06 | Additional GoalPhase operations/scenarios beyond its selected practical route | Preserve normal start/work/result/continuation coverage; apply Phase 5.1's fresh-start boundary |
| P2-T01..T06 | Remaining server/MCP/CLI exposure and equivalence selected by actual need | Record installed transports and unimplemented/unvalidated routes separately; withdrawn concurrency/intermediate-recovery guarantees are not pending acceptance |
| P2-R01..R03 | Resident references, refresh and generic management/Job visibility | Carry only the uncompleted integration; do not replace canonical ownership |
| P2-R04..R05 | Remaining workflow-profile coverage and real external-command adapters | Retain actual implementation/effect evidence; selected real-use adapters cannot be mock-only |
| P2-C01..C02 and bundle notes | Remaining cost/round-trip measurement and broader skill distribution/coverage beyond the Phase 6 minimum | Public Operation control stays in Phase 2; initial command/CLI wiring and skill creation/connection belong to Phase 6, not this backlog |

Completed P2 preparation and upstream primitives remain completed at their actual
evidence scope. Transferring a row does not mark its unchecked specifications
passed, reset repair counts, change the original base/PLAN epoch, fabricate old
state or require unrelated tests to be rerun. No copying of generated artifacts
or content-integrity ledger is required for this handoff.

## Needs discovered during use

Record an ordinary item here or link an existing journal with:

- original ID/phase or discovery context and the concrete user scenario;
- impact: blocks supported use, bounded enhancement, or optional future work;
- current implementation state, known limitations and existing test/review links;
- disposition: addressed in Phase 6/7, selected for Phase 8, unnecessary,
  superseded, or explicitly assigned to a later plan, with a brief reason.

Initial skill integration gaps are resolved in Phase 6. Use-blocking gaps found
after its acceptance receive early attention during real use and Phase 7; users
need not wait for Phase 8. Keep those phase scopes bounded. Other missing functions accumulate
here without forcing repeated expansion of the current implementation batch.
No real-use discoveries are claimed by creating this plan; add them as observed.

Acceptance Boundary behavior introduced in Phase 6 is part of this real-use observation. If practical use shows that a project quality attribute is missing or poorly expressible, record the concrete supported-use impact here. Do not broaden the boundary merely because Review or an implementation provider proposes stronger assurance. Boundary vocabulary/configuration extensions are selected in Phase 8 only from observed need; the richer mechanism that uses the boundary for convergence and provider routing belongs to Phase 9.

The [Development Artifact Overlay](../spec/development-artifact-overlay.md)
initial delivery and its six SimpleModeler acceptance scenarios belong to
Phase 7. Record only optional later expansion here; do not defer core publication,
selection, native operation integration or consumer verification to Phase 8.

## Parent round trips and context transfer (2026-11-09)

Selected operational improvements from the
[AI cost investigation](../journal/2026/11/2026-11-09-ai-cost-observations-and-phase-workflow-comparison.md).
Completed sm-workflow examples show about 67% fewer credits per declared Step
and 64% fewer parent responses under the native route, but different work,
review policy and unfinished/recovery costs prevent a causal or whole-operation
savings claim. Use the observed repeated calls and context transfer to select
bounded improvements; the historical percentage is not an acceptance target.
Implementation and installed acceptance remain pending.

### Selected behavior

1. **Request a coherent unit of work.** Let the existing runtime advance through
   deterministic work, execution, retained-result collection and ordinary
   continuation until the next actual semantic assignment, Human decision,
   authority boundary or terminal outcome. Do not return to the parent merely
   to transcribe the next control argument or collect another unchanged status.
   Preserve independent review and required validation as distinct obligations;
   a larger orchestration unit neither enlarges effect authority nor combines
   unrelated implementation into an unbounded worker assignment.
2. **Pass evidence by reference in both directions.** Use existing typed
   result/artifact references for detailed validation, review, logs and history.
   Keep request/target association, the result summary, unresolved decisions
   and the next action in the compact exchange. Return changes since the last
   collected state instead of repeating the full Session or unchanged output.
   The consuming parent/worker must be able to resolve the relevant evidence
   for the selected target; missing, mismatched or inaccessible evidence remains
   unverified. Retain actual outcomes and detailed diagnostics at their existing
   owner rather than truncating them to manufacture a successful small response.
3. **Isolate substantial implementation context.** Delegate a substantial
   implementation or repair assignment to a scoped subagent even when the
   selected execution requirement calls for Sol High. Higher capability alone
   is not a reason to perform all editing inline in the parent. Use the bounded
   task, relevant contracts/source references, owned changes and acceptance
   criteria as the handoff; return the result, change/evidence references and
   remaining issues without importing the worker's full conversation. Small
   bounded edits may remain inline. Use task/context needs rather than a fixed
   file-count threshold, retain existing model/effort selection, and preserve
   independent review. Connect this to the existing assignment and review-handoff
   rules instead of adding a competing agent coordinator.
4. **Internalize waiting and mechanical continuation.** Use existing runtime
   and host execution/lifecycle owners for pure process/agent waiting, status
   collection and deterministic continuation. Resume the parent for meaningful
   results, decisions or exceptions that need its judgment, retaining actual
   progress visibility. Preserve active Goal continuation, matching request and
   result, cursors, stop/defer behavior and live/unknown outcomes. A notification
   without continued Goal execution does not meet this behavior. Where the host
   cannot supply equivalent continuation, retain the supported waiting route,
   state the limitation and do not claim that optimization delivered.
5. **Reuse applicable successful work.** Within supported ordinary continuation,
   consume existing accepted results and validation at their actual target,
   scope and authority rather than repeating successful work after every wait
   or repair. Recheck affected work when changes invalidate that evidence.
   Preserve failed attempts and the same Session/history/repair budget. This
   does not restore the withdrawn intermediate-recovery guarantees or permit
   replay of an uncertain external effect; the existing fresh-start boundary
   still applies when abandoning an unsuccessful run.
6. **Check mechanical inputs before dispatch.** Validate control JSON, required
   arguments and evidence references through existing input/effect owners before
   launching semantic work or external effects. Handle already-supported
   deterministic preparation within its existing authority; report invalid
   input as such rather than inventing a semantic repair attempt. Missing
   authority, live/unknown effects and genuinely new decisions retain their
   existing handling. Do not introduce another preflight approval loop.

### Connected acceptance and ownership

Exercise a bounded normal development assignment through implementation,
required validation/review and completion, including a substantial assignment
whose selected requirement is Sol High. Also exercise a waiting continuation
and a known failed check repaired through the same supported Session. Verify:

- The runtime completes consecutive deterministic operations without a parent
  AI response for each mechanical transition; it returns at the real semantic
  or Human boundary and never crosses it under inferred authority.
- Compact requests/results resolve the actual detailed evidence. Recollection
  does not repeat unchanged history; missing or wrong-target references cannot
  establish validation or review success. Subagents receive sufficient scoped
  context and the parent receives a usable outcome without the full worker log.
- Waiting preserves active Goal continuation and stop/error detection. Relevant
  activity/progress remains visible even when parent inference is unnecessary;
  unsupported host behavior is reported separately from delivered behavior.
- A repair retains valid earlier results and rechecks what it affects. Invalid
  control inputs do not launch effects or consume a semantic repair attempt.
  Human answers, independent review, live/unknown outcomes, accepted effects,
  history and cumulative repair counts retain their existing semantics.

Compare equivalent scenario boundaries and required checks before/after using
available parent/runtime round trips, response counts, control JSON sizes,
context/usage observations and rework or missing-evidence reports. Record
unavailable measurements honestly; a byte count alone does not establish lower
cost or quality. Phase 8 owns these operational changes and proportionate
scenario observations. [Phase 13](phase-13.md) owns systematic parent/child
usage collection and aggregation; [Phase 9](phase-9.md) owns broader model
comparison and observed provider rerouting. Neither Phase's full delivery is a
new prerequisite here. Do not create a new accounting or workflow ledger.

Prioritize runtime round trips/waiting, compact evidence exchange, scoped worker
context, then avoiding repeated successful work. Local-LLM replacement of the
Luna chat waiter remains a deferred candidate, not a Phase 8 completion gate.
Reuse existing runtime/evidence owners; no management hashes, new approval
mechanism or automatic change to issued Requests/active Sessions is selected.

## Context compaction coordination framework (2026-11-07)

Selected bounded enhancement: long-running development chats should be able to
prepare context compaction at meaningful workflow boundaries. Phase 8 owns the
coordination framework. Automatic compaction through the actual desktop host is
a best-effort integration goal, not a Phase 8 completion prerequisite.

The framework has the following bounded responsibilities:

1. Identify a suitable boundary after accepted work, such as an accepted Step
   result, a settled investigation/design decision, or Phase completion. Consider
   accumulated context and expected next work; do not require compaction after
   every Step or introduce a mandatory workflow gate.
2. Prepare concise continuation information: the current objective and user
   constraints, owning workflow Session and next action, accepted work, owned
   dirty paths, relevant decisions, and references to actual validation/review
   results. Retain outstanding command/agent identities and uncertain effects
   when present; preparing a summary does not establish their completion.
3. Expose an optional host compaction request associated with that boundary and
   target chat. The host owns its supported execution route and scheduling after
   the current AI turn completes. A summary or request acknowledgement is not
   evidence that compaction finished.
4. Distinguish unsupported/deferred requests from actual host completion,
   failure, or unknown outcome. Continue ordinary supported work when compaction
   is unavailable or deferred. Preserve the existing Session, authority,
   accepted results and pending work; compaction does not re-Start a workflow,
   repeat completed validation/approval, or replay uncertain external effects.

Codex App Server documents `thread/compact/start` and completion through the
`contextCompaction` item lifecycle. The installed Codex
`0.166.0-alpha.6` schema includes that method, but this chat currently exposes no
direct compaction tool. Connection to the desktop-owned chat and actual
automatic execution remain unverified. These are discovery facts, not a promise
of a supported host adapter. See the [official host API](https://learn.chatgpt.com/docs/app-server#trigger-thread-compaction).

Framework acceptance covers boundary selection, usable continuation information,
optional host handoff and truthful outcome handling, including an unavailable
host. Report actual host integration separately. Preserve the existing runtime
and evidence owners; do not add a second workflow ledger, management hashes,
unchanged-content proofs, a competing app-server owner or a new approval loop.

### Work classification in practice — selected for Phase 8

In the November 5–6 reviews, closing a prescribed rename and checking whether
a Human choice still applies to the authorized target both appeared as re-review
work, although they require different evidence. The bounded Task/Fix producer
currently assigns both STANDARD complexity and SMALL footprint. Start there:
classify a new assignment by the question it must settle, then verify the
distinction before extending it to new GoalPhase assignments.

Acceptance: a reviewer can close the rename against its finding and diff; a
one-line authority change still receives a contract review. Distinguish diagnosis
of an unknown failure from repair of an established cause. Keep issued Requests,
independent review and runtime validation unchanged. Assess reopened findings
and handoffs returned for missing evidence. Broader model benchmarking remains in Phase 9.
See the [work classification journal](../journal/2026/11/2026-11-06-work-classification-precision.md).

Use Terra by default for ordinary implementation and repair, including
prescribed edits. Phase 8 does not assign coding to Luna while Terra is
available. Record whether the target, transformation and expected behavior are
fixed so the handoff and acceptance criteria are precise. Select any required
independent reviewer by the question to be resolved.

| Acceptance case | Repair assignment | Independent review assignment |
| --- | --- | --- |
| Rename a specified helper at its declaration and two named call sites; preserve behavior | Terra applies the prescribed edit | A different Terra checks the finding, diff and preserved behavior |
| Fix a known parser error where braces, indentation and extracting an expression are still viable | Terra selects and implements the fix | A different Terra checks syntax and behavior |
| Apply an exact one-line edit affecting which target a Human may authorize | The edit is prescribed | Retain the contract review of choice and target regardless of who edits |

Retain Luna for established non-coding work such as waiting for a matching
cross-chat result. Terra unavailability does not introduce a new Luna coding
fallback. The rename pilot is evidence for this planning choice, not a general
model benchmark. Preserve existing mappings, review requirements and already
issued Requests.

For a focused re-review with a stated finding or question, identifiable evidence
and a clear pass/fail criterion, choose **Sol Medium first**. This includes
questions requiring several receipts. Keep **Sol High** for reviews that search
for unknown problems, including integration reviews with no predefined finding.
If the question changes from checking a finding to discovering what else is
wrong, use High for that new assignment. If a receipt is missing, report what
remains unverified and obtain the evidence. A Medium result does not require a
second review by High. The Terra examples above cover ordinary implementation
and repair checks; this rule selects effort for independent Sol reviews.

The November 8 pilots used fresh Sol agents with identical handoffs and evidence
within each pair, one run per effort. Total tokens include cached input.

| Focused re-review | Medium: seconds / total tokens | High: seconds / total tokens | Result at both efforts |
| --- | ---: | ---: | --- |
| Publication after interruption, before and after the fix | 13.33 / 60,914 | 12.45 / 60,527 | Found the duplicate-publication path before the fix and correctly closed the finding afterward; source review only. |
| Three publication attempts in one Session | 12.32 / 58,112 | 17.08 / 57,979 | Distinguished requests and commit HEADs; the count alone did not establish replay. Reaching ReviewPhase did not establish Phase acceptance. |

Across the two cases, neither effort missed an expected finding, raised a false
finding or required a follow-up. Medium was faster in aggregate but not in
both cases, and did not use fewer total tokens. These pilots support the focused
routing choice only; they did not test discovery review or measure credits.

When evaluating classification, distinguish a follow-up assignment or
continuation after a validation wait from a correction prompted by a finding
or failed check. Use existing task and validation records. If the reason for a
resume is unknown, report it as unknown; a resume count alone is not a failure
count. This is an observation for the classification item, not a new reporting
subsystem. Token collection remains in Phase 13.

### Review handoff between assignments

In November, the reviewer of the common continuation went on to review a
separate Resource change with roughly 400,000 input tokens on its first
response to the new assignment. Keep the same reviewer while the same unresolved
finding depends on earlier reasoning. For a distinct assignment, use a fresh
independent reviewer and hand over the question, the relevant diff and finding,
the invariants to preserve, and what remains unverified. Link the evidence
instead of forwarding the conversation history.

Acceptance: exercise both transitions. The original reviewer can follow an
open finding to closure; the fresh reviewer can decide the separate question
from the handoff and cited evidence without reading the previous chat. Check
for reopened findings or missing evidence as well as input size.

### Completion reports for consumers

The November synchronization record retained an intermediate publication failure
and a later successful publication and synchronization. Lead the report with
what the consumer can use now. Distinguish source changes, validation,
publication and consumer verification; mark a stage not checked or not
applicable when appropriate. Name the next action and its owner, or state that
none remains. Keep earlier failed attempts in the linked history.

Acceptance: from the opening of a report for that scenario, the consumer can
tell whether publication ultimately succeeded and whether consumer verification
was actually performed. A publish receipt alone must not imply consumer
acceptance. Apply this to the presentation of existing results, without adding
a runtime stage or replaying successful work.

### Human decisions in normal operation

Inventory recent Human stops before changing their routes. For each distinct
request in the selected GoalPhase, GoalTask and RepositorySync episodes, cite
the Session/request or retained record, the question and evidence actually
shown (mark unavailable fields unknown), prior authority, what was still
undecided, and the disposition: proceed, investigate first, or ask. Separate
actual answers from fixture decisions and planned policies. Count repeated
prompts separately from distinct decisions; do not infer redundant approvals
from a stop or a resume alone.

Initial cases from November 5–8:

| Record | What the record establishes | Phase 8 disposition |
| --- | --- | --- |
| November 8 formal source-history incident, `P611-SM-FORMAL-SOURCE-HISTORY-FAILURE-CONTINUATION-01`, retained `incident-brief.json` | Actual approval covered bounded repair, verification and continuation; the formal failure had been reported as Wait, as had a missing outcome. | For a known failure within that scope and the remaining repair allowance, continue ordinary repair without another answer. For a live or missing outcome, collect status; do not treat it as a terminated failure. |
| [GoalTask repair-trend record](../journal/2026/11/2026-11-05-goal-task-human-repair-continuation.md) | A Human approved producer re-entry in a defined scope after NotAccepted. The separately stopped consumer did not thereby receive authority. | Reuse the decision only for the named producer work. Check its target and remaining allowance mechanically; neither reset the budget nor transfer consent to the consumer. |
| [Primary publication recovery](../spec/goal-phase-prerequisite-publication-host-recovery.md), November 8 fixture `canonical-primary-host-episode.json.directed.json` | The contract requires a separate Human direction for a whole-primary successor with possible partial effects. The fixture is not live consent. | Inspect the destination and retained request under existing read authority first. Reuse confirmed success. Retry only when the effect is known absent or repetition is demonstrably safe **and** existing authority covers it. Change and verify the owning contract before enabling any newly automatic route. |

A failure alone is not a question for Human. Exhaust available, authorized
investigation before escalating. Preserve the Session, evidence, limits and
successful effects. If evidence is unavailable and no safe action is possible,
report what is blocked and what observation would unblock it; a generic
approval cannot establish a missing fact. Ask only for a decision the workflow
cannot make, such as accepting a specific residual risk, changing scope, or
choosing how to handle conflicting local work under explicitly selected
RepositorySync `confirm`.

A repair may create or edit source files needed for its authorized objective.
An initial path estimate is not a Human-imposed restriction: update the planned
paths with the reason and continue without asking solely because a file was
added. Respect explicit exclusions and ownership. If investigation identifies
a concrete risk that the existing authority and checks do not resolve (for
example, overwriting another contributor's changes or breaking compatibility
for a consumer), ask about that consequence before making the affected edit.

Exercise both branches using the user-reported Workbench example: adding
`CandidateProposal.scala` as the 27th repair path needs no confirmation on
path count alone. In a variant where that edit would break an existing consumer
contract, show the affected consumer and the compatibility choice. Keep the
checkpoint, Session history, repair count and required checks intact in both
cases; do not bundle them into a generic request to continue.

**B5 canonical dependency source example (2026-11-09).** Public response187
proposed `AdoptCanonicalDependencySourceRevision` for Session
`3585e04191dce0bc0f70e394b6745767`, Plan2/P611-B5, source/frontier3148,
generation31, repair1/10. It selected already published/recollected CNCF main
`c6c6f09bcf94e5ac1b116a59d189dc563cebf64c`, binding4 to5, retaining the original
30 paths plus nine approved paths, B2 conditions, B5 approval and accepted work.
Adoption would record Direction; ordinary Advance would issue the same B5
Repair. It did not grant a new primary publication attempt.

The retained prior channel record shows that the AI-written maintenance question
itself excluded real-Session source adoption and new publication, with a separate
decision promised after repair. Human approved that bounded maintenance request;
this is not evidence that Human independently requested a prohibition on normal
dependency adoption. The subsequent explanation initially treated that split as
a sufficient reason to ask again without examining whether the split served an
actual user decision. See the
[record, analysis and selected correction](../journal/2026/11/2026-11-09-ai-cost-observations-and-phase-workflow-comparison.md#follow-up-example-unnecessary-human-boundaries-in-p611-b5).

For new work, frame the authorized development objective to include necessary
same-purpose file additions, target-inventory updates, incorporation of approved
dependency delivery and ordinary source/binding updates. A reference revision,
file count, changed failure reason or public API's current Human requirement is
not alone a material decision. Distinguish the original requester's authority
from a helper's limited maintenance assignment: limiting the helper does not by
itself revoke authority already held by the requester. Keep implementation,
validation and independent review at their required boundaries without requiring
a new Human answer at every internal transition.

Ask when the proposed action actually changes the objective, supported consumer
behavior/compatibility, acceptance conditions, Component/ownership boundary or
external-effect authority beyond what was authorized, or when a concrete residual
risk remains for Human to decide. A required filename or source revision alone
does not establish such a change. Continue to honor real explicit restrictions
and live/unknown effects. Correct the owning public contract before enabling a
new automatic route; do not reinterpret the historical answer, fabricate source
adoption evidence or bypass the actual current Session gate.

Acceptance: in a prospective equivalent scenario, approved dependency delivery
and its necessary same-purpose source/file updates proceed through the existing
owners into normal Repair without a new source-choice prompt solely because
binding/path inventory changed. A variant with genuinely unapproved ownership,
consumer compatibility or publication effects presents that specific decision.
Both retain accepted work, all B2/B5 conditions, UNKNOWN/failure history and the
same repair count. After a genuine answer, mechanical adoption/Advance does not
create a second confirmation or parent handoff for each control argument.

Observe avoidable prompts and their associated parent responses, target
recollection, evidence preparation and context/transfer cost alongside completion
and rework. Human waiting time itself is not an inference charge. This case's
incremental credits have not been measured, so record no invented saving. The
selected correction removes unnecessary decisions rather than merely shortening
the question or batching multiple artificial approvals into one.

**RepositorySync input classification example (2026-11-09).** Session
`9d01b91080ca146bf2aee9a46e16aa4e`, public frontier44, offered
`AdoptInputClassificationAndContinueReview` for six previously unknown documents.
The prompt reported successful Compile, FULL (2,692 tests) and FixCheck, two
purpose-based commits and already authorized non-force GitHub main synchronization.
The proposed continuation retained those successes/history/repair counts without
rerunning tests and sent the same candidate to fresh independent review.

The retained first description refused a proposal that treated six selected
consumed program inputs as documents. The corrected description retained all
six as consumed inputs and reported zero applicability issues. These observations
support the correction's applicability, not review acceptance or a new validation
result. The [case journal](../journal/2026/11/2026-11-09-ai-cost-observations-and-phase-workflow-comparison.md#follow-up-example-repositorysync-input-classification-at-frontier44)
records the paths and retained descriptions. The
[current v3 contract](../spec/repository-sync-validation-continuation.md#retained-review-input-classification-continuation-v3)
requires exact independent Human adoption; that current implementation boundary
is not evidence that classification inherently requires a Human decision.

Selected correction: let the owning semantic assessment and runtime establish
input classification from actual build/packaging/skill consumption, candidate
identity and selected check coverage within existing authority. Correcting an
unknown classification is ordinary technical work when it changes no accepted
behavior, required checks, purpose, ownership or effect authority. Do not request
Human confirmation solely to adopt that technical result. The runtime preserves
actual receipts and proves their applicability; the independent reviewer still
assesses the candidate. Zero applicability issues cannot substitute for that
review, and a `.md` suffix cannot remove a consumed input from coverage.

Acceptance: the equivalent corrected classification continues to independent
review with the same candidate, successful check receipts, purpose commits and
repair/history records, without a new Human answer or test rerun solely for the
classification. A proposal that hides consumed inputs, changes the candidate or
check applicability, or lacks evidence must not reuse those receipts as proof;
investigate or run the necessary checks through the owner. Ask only if resolution
needs an actual acceptance, compatibility, ownership or authority decision.
Preserve the original refused description and subsequent correction. Qualify the
owning public route before changing automatic behavior; no live Session adoption,
review acceptance, push or replay is authorized by this planning example.

**B4 delegation example (operator-reported prompt).** A CML generation
failure reportedly missed the route back to ordinary repair in the same Step.
The prompt bundled assignment to the sm-workflow owner, testing, review,
isolated recovery, commit, local publication and integration into one yes/no
question. It called the fix a public contract change without identifying any
consumer-visible choice. A different cause from a previous repair is not by
itself a new decision; 1/10 is not a repair stop.

Check the existing contract first: if this is a known formal failure that
should already return to ordinary repair, route the scoped fix and its checks
under existing authority. Keep UNKNOWN distinct, retain the same Session and
13 successful history updates, and resume B4 after verified delivery without
replaying accepted work. Report progress instead of requesting a blanket
approval. If a new behavior or effect needs a Human decision, name the caller,
show what it observes today and under the proposal, and ask only about the
unresolved consequence. Do not infer commit, publication or integration
authority from an answer about failure classification.

**SuiteMismatch after generation recovery (operator-reported prompt).** The
earlier generation repair reportedly succeeded in the real Session. A newly
encountered validation result is not by itself a new material purpose under
the delegation contract. Compare the original objective and authorized effects
with the proposed change. If the objective already covers recovering B4 in
the same Step, route a verified mismatch back to ordinary repair within that
scope and report progress. Keep the completed generation repair closed.
[SuiteMismatch](../spec/sbt-validation-runtime.md) is an unverified test
selection: preserve that negative result and require evidence that the selected
suite actually ran before accepting validation. Do not substitute another suite
or treat incomplete capture as a confirmed mismatch. If the proposal instead
requires changing the accepted suite or the original authorization expressly
covers generation failures only, identify that exact difference and its impact
before asking. Acceptance: varying only the failure reason must not introduce
a Human prompt; changing what counts as passing must not inherit approval.

**Repair-window example — verification Session `09311fb5…`, Human 29.**
The November 8 capture (`formal-source-history-failure-continuation-20261108/
capture-host/11-validation.stdout.json` under the run artifacts) records
`sm-workflow.repair-trend.v1`, automatic/ordinary counts 2/2, limit 2, and
choices ContinueRepairs, Defer and Cancel. The operator reported that the
remaining issue was a test comparing entire initial and recollected Wait
responses, with 63 earlier successes. Those details are the operator's
diagnosis, not facts inferred from the counter.

In this capture, the fixture's `goal-plan.json` selected `repairLimit: 2`.
Runtime used the smaller of that selected value and its ceiling of 10. The
denominator was therefore a real limit for this historical Session, not a
Human-selected limit or a display of two out of ten.

For unfinished ordinary repair, the workflow stops the repair loop only for
**observed divergence** or **10 cumulative repair attempts**. Apply the same
rule to derived verification plans. For divergence, cite comparable repair
results showing sustained non-convergence (for example oscillating findings
or a growing set of failures). A single failed check or an internal progress
checkpoint does not establish divergence. Without such evidence, continue at
2/10 and at 9/10; stop at 10/10 before an eleventh repair. Retain the count
across reopen and continuation; do not renew it through a new plan or Session.

Exercise the Wait expectation against its contract. A decision about changing
that contract is separate from repair-loop accounting. Use the historical 2/2
receipt as evidence of the current gap, not as a lower-limit exception.
This plan does not retroactively change the Session or answer Human 29.

For each retained question, show the target and operation, what is known and
unknown, evidence supporting that distinction, the limits of prior authority,
and the concrete options (including deferral) with their consequences. Explain
what the answer will permit. Record the answer through the existing route and
resume without asking again for the same decision.

Acceptance: exercise automatic continuation after a known authorized failure,
collection/reuse after an interrupted publication, and an unresolved partial
effect where a new decision really is needed. Check the actual user-visible
question against the record: can the user tell what will happen if they agree
or decline? On reopen, verify no duplicate question, repeated successful
effect or silent expansion of authority.

## Repository synchronization: uncommitted-change policy (2026-11-08)

Selected bounded enhancement: expose `uncommittedPolicy` through the normal
RepositorySync public input/configuration route. The reported use case required
preserving uncommitted work, but the caller substituted `cncf-repository-sync`
when the current automatic-commit route did not fit. Keep synchronization and
continuation owned by sm-workflow, with these three selectable behaviors:

| Value | Planned behavior |
| --- | --- |
| `auto-commit` (default) | Preserve the existing purpose-based preparation: classify intended changes, apply the required acceptance, commit coherent purposes in dependency order, then synchronize. |
| `confirm` | Present the actual blocking changes and a concrete proposed resolution to Human. Apply the authorized resolution through the existing runtime route, perform required checks/commits, and continue synchronization in the same Session. |
| `error` | Report the blocking uncommitted changes and finish the invocation with an explicit error, preserving local changes and completed work without automatic correction or dirty-change commits. |

Omission preserves today's default. Apply the same policy to single-repository
and directory-list entry points, resolve and retain the effective selection in
the existing Session context, and reject unsupported values as configuration
errors. This parameter concerns uncommitted local changes; it is independent of
`syncMode` and does not prohibit a merge commit needed to synchronize committed
history. A clean repository needs no dirty-change question under any policy.

`auto-commit` covers intended authorized work only. Preserve partial staging,
excluded work and another owner's ongoing changes. In `confirm`, use the actual
inventory to explain what is blocked and what the proposed action changes;
record the real answer and resolve the admitted blocker rather than merely
reporting another stop. A choice to preserve changes that still prevent safe
integration remains an explicit unfinished outcome. This policy does not promise
a dirty-tree merge or authorize stash, discard or excluded-change commits.

Reuse existing Human-answer and continuation operations. Preserve the Goal,
Session, accepted results, history, repair counts and successful checks; continue
only unfinished work without repeating accepted effects or the same approval.
Retain actual handling of live/unknown effects and genuinely new decisions.
Do not substitute another synchronization skill or direct Git commands for a
missing parameter or continuation capability, and do not add a second approval
ledger or per-repository confirmation loop for ordinary default synchronization.

The public contract, normal entry instructions and executable specifications
must distinguish these behaviors before delivery. Detailed scenario coverage
and the current implementation gap are recorded in the
[planning handoff](../journal/2026/11/2026-11-08-repository-sync-uncommitted-policy.md).
This is planned work, not a change to the currently supported public API.

## Repository synchronization: local main before GitHub main (2026-11-09)

Expose the String property `sm-workflow.repository-sync.main-integration`:
`local-first` is the default when absent; `direct` selects synchronization from
the working checkout to the requested GitHub main without an automatic update
of the separate canonical main checkout. Reuse normal configuration resolution,
reject invalid values before route-dependent effects and retain the effective
selection in the existing Session. No automatic mode switch follows a failure.
`confirm` asks Human to choose the actual route before synchronization mutation,
explaining the selected targets and effects. Record the real answer and continue
in the same Session, retaining it across operations/continuations without asking
again for the same decision. Defer/cancel and absent answers do not select a
route automatically; dirty-change decisions remain independent.

Selected default for an ordinary main-directed request from a working worktree:
integrate the admitted source contribution into the canonical local main
worktree first, then synchronize that main with the selected GitHub main.
Reuse the existing RepositorySync/typed Git, purpose acceptance, conflict and
continuation routes. Resolve the real canonical checkout; do not infer it from
this machine's directory layout or bypass it with a direct source-to-remote push.

A request from main skips the local hop. Explicit non-main tracking targets
retain their meaning. Preserve source branch identity, excluded dirty work,
successful checks, local integration and existing Session/history/counters;
remote failure resumes only the unfinished work. This is an adopted target,
not a claim that the current public input/installed runtime supports both hops.
See the [target contract and acceptance scenarios](../spec/repository-sync-local-main-integration.md)
and [decision journal](../journal/2026/11/2026-11-09-repository-sync-local-main-first.md).

Phase 7 managed dependency worktrees use the distinct
[isolated dependency route](../spec/repository-sync-local-main-integration.md#phase-7-managed-dependency-route):
the selected B worktree synchronizes directly with its explicit GitHub branch,
without another local main checkout. Main-directed dependency requests explicitly
select `direct` in their scoped context; non-main targets keep their branch.
Ordinary main integration retains the `local-first` default. Do not include other
B worktrees' dirty or unpublished work, require their cleanliness, or switch
routes on failure. This allocation preserves parallel-development independence
and does not silently retarget existing Sessions or add routine Human prompts.

## Selection and execution

Carry forward Phase 6 command ownership and live Phase-document synchronization
for every selected extension. Broader tool adapters may be chosen here from real
need, but the required SBT/Git lifecycle and normal plan/progress synchronization
are Phase 6 deliveries, not deferred backlog. AI semantic providers must not
become shell runners. A selected extension updates the encoded plan from the
latest adopted documents and writes progress without replacing narrative edits
or erasing prior facts. Verify affected connections in the selected scenario;
do not reopen earlier acceptance or add a new synchronization test campaign.

1. Review Phase 2 carryover, Phase 5 runtime evidence, Phase 6 connected acceptance
   and actual use after Phase 6.3 and during Phase 7. Confirm which
   functions are still needed and which are unnecessary or superseded.
2. Freeze a finite set of user-visible scenarios for this closing phase. Record
   a reason for exclusions; do not silently drop an unresolved supported-use bug.
3. Implement each connected scenario and its Executable Specifications together.
   Use focused checks during implementation, then integrated validation and
   independent review, with affected rechecks after fixes.
4. Exercise the completed supported workflow in normal use and update usage
   instructions, supported operations and remaining explicit limitations.

Phase 3/4 remain separately planned extensions. Consume them only when a selected
real requirement needs them; do not make their full completion an automatic
Phase 8 gate. Broader CNCF work is driven only to the boundary needed by selected
scenarios and retains its owning project's responsibilities.

## Completion

- [ ] Every inherited/discovered item has an explicit disposition and a link to
      actual evidence or a reason for exclusion/future placement.
- [ ] The finite selected scenarios are implemented and their connected
      Executable Specifications and required regressions pass.
- [ ] Independent review has no unresolved blockers for the supported scope;
      known supported-use failures are not relabeled optional to claim closure.
- [ ] Work classification distinguishes a focused re-review (Sol Medium first)
      from a search for unknown problems (Sol High). Check missed and false
      findings and follow-up work before comparing time or token totals.
- [ ] Recent Human stops have evidence-backed dispositions. Authorized
      investigation and recovery proceed without a prompt; any remaining
      question shows the decision, evidence, uncertainty and consequences.
      A necessary source addition proceeds without confirmation on path count
      alone; a concrete unresolved compatibility or ownership risk is presented
      before the affected edit. Reopening does not repeat a decision or effect.
- [ ] The B5 response187 example has a prospective ordinary-continuation case:
      necessary same-purpose file/target additions and approved dependency
      source updates do not create a Human decision solely from path/binding
      changes or an AI-authored maintenance split. Genuine objective, consumer,
      acceptance, ownership or effect-authority changes retain the actual
      decision. Existing explicit exclusions and historical answers are not
      rewritten; the owning public contract is qualified before automation.
- [ ] The RepositorySync frontier44 example classifies unknown inputs through
      semantic assessment and runtime coverage checks, retaining applicable
      successful validation and proceeding to independent review without an
      extra Human answer solely for classification. Consumed-input demotion,
      changed candidates/check applicability and missing evidence cannot reuse
      old receipts as proof; no review PASS or new push authority is inferred.
- [ ] Ordinary and derived verification plans stop unfinished repairs only on
      evidenced divergence or at 10 cumulative attempts. Demonstrate an early
      divergence stop, continuation at 2/10 and 9/10 without divergence, and
      no eleventh attempt or counter reset on resume.
- [ ] Real-use instructions match the available normal entry routes and outcomes.
- [ ] Main-directed worktree synchronization integrates through canonical local
      main before GitHub main, with direct-main/non-main cases, dirty ownership,
      two-hop failure continuation and batch shared-target behavior covered.
- [ ] `sm-workflow.repository-sync.main-integration` covers omitted/default and
      explicit `local-first`, `direct`, `confirm`, normal configuration overrides,
      invalid values and retained active-Session selection in both entry routes.
- [ ] Phase 7 managed dependency synchronization explicitly selects the isolated
      direct route for remote main, preserving other local B worktrees even
      when dirty or ahead of GitHub. Non-main targets and A's own route retain
      their meaning; ordinary main integration remains local-first. Conflicting
      explicit route configuration is resolved before effects without a silent
      override or local-checkout fallback.
- [ ] Human route selection precedes synchronization mutation, applies the
      chosen route in the same Session, and covers defer/cancel, unanswered
      questions and shared-target batching without repeated approval.
- [ ] RepositorySync exposes `uncommittedPolicy`: omitted/default `auto-commit`,
      explicit `confirm`, and `error`, consistently for single and directory-list
      callers; clean input and invalid values have specified outcomes.
- [ ] `confirm` applies an actual Human-authorized resolution and continues in
      the same Session; tests retain excluded work, accepted effects, checks,
      history and repair counts without repeating approval or switching skills.
- [ ] `error` preserves dirty changes and returns a concrete error without
      automatic dirty-change commits; default purpose-based acceptance remains.
- [ ] The context compaction coordination framework covers the selected
      boundary/continuation/handoff/outcome behavior. Actual automatic desktop
      compaction is reported separately as best effort and does not block closure.
- [ ] A coherent runtime-owned work unit advances through mechanical operations
      without parent AI handoffs at each transition, returning at actual semantic,
      Human and authority boundaries while retaining required validation/review.
- [ ] Compact control exchanges use resolvable evidence references and changed
      state summaries; missing/wrong-target evidence cannot establish success.
      A substantial selected Sol High assignment uses scoped subagent context
      and returns a usable summary with references rather than its full history.
- [ ] Selected waiting and ordinary repair scenarios retain active Goal/Session
      continuation, progress visibility, actual outcomes, applicable earlier
      results, required affected rechecks and the original repair allowance.
      Unsupported host integration is explicit; notification alone is not
      accepted as automatic Goal continuation.
- [ ] Mechanical input failures are detected before effects/semantic dispatch
      without consuming a semantic repair attempt or adding an approval loop.
- [ ] Before/after observations cover comparable scenario boundaries, round
      trips, exchange/context size and rework at their available evidence scope.
      No universal 67% savings, local-LLM adoption or completed Phase 9/13 is
      required or claimed by these operational improvements.
- [ ] Normal closing work for the delivered scope is complete, with history
      retained and no claim that unimplemented optional features were delivered.

An item found unnecessary through use may close with that reason. Phase 8 closes
the agreed rollout, not every imaginable future feature. Preserve Phase 5's
removal of management hashes, tamper defenses, unchanged-content proofs and
internal time-only expiry across every extension; do not reintroduce them as
completion or evidence-reuse requirements.
