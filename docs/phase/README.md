# sm-workflow incremental-use roadmap

Common Evidence adopted 2026-10-20: [Phase 12](phase-12.md), owned by
sm-workflow開発, delivers one automatic Action -> actual archive -> common
Journal -> fresh-process read route before Phase 6.4 E2 adoption. Phase 6.4,
owned by sm-workflow-dev開発, reuses it for operational acceptance and extends
the same service for failure/batch/compression/retention. No absolute display-path
API, AI journal transcription or duplicate service is required. Phase 11's
accepted Journal is reused; 11.1 and 7–10 are not prerequisites. Preserve ongoing
6.4 execution and all accepted results. See the [shared specification](../spec/common-evidence.md)
and [both-chat handoff](../journal/2026/10/2026-10-20-phase-12-common-evidence-handoff.md).

Journal delivery priority, adopted 2026-10-20: [Phase 11](phase-11.md) is a short
independent delivery before the remaining Phase 6.4 work: common structured
record/read/find, accepted-finding recording and installed trial-skill connection.
It does not depend on 6.4 archival or on Phases 7–10. Phase numbering does not
set execution order. Phase 6.4 consumes this shared Journal through its normal
adoption boundary while retaining ongoing work, issued requests and evidence.
[Phase 11.1](phase-11.1.md) adds decisions/issues/handoffs and revision/batch
management separately; it does not delay the 6.4 trial. The main rollout below
remains, with this selected prerequisite delivery inserted before 6.4's remaining
integration. No active Goal or closed Phase is reset by this planning decision.
See the [design](../design/development-journal.md) and
[handoff](../journal/2026/10/2026-10-20-common-journal-phases-handoff.md).

File Workspace clarification adopted 2026-10-15: Phase 6 P6-SBX03 uses protected
`file_read_text` / `file_write_text` for development-file manipulation and retains
ordinary `read_resource` / `read_resource_text` with runtime-owned limits for
logical resources. SBT -> Git -> File implementation order is unchanged;
workspace authority is coarse per admitted logical root. This does not resume
local worktree operations or pull Phase 7 overlays forward. See the
[implementation handoff](../journal/2026/10/2026-10-15-phase-6-file-workspace-dsl-handoff.md).

Managed-effects correction adopted 2026-10-15: [Phase 6](phase-6.md#safety-correction-managed-gitsbt-and-capability-laundering--2026-10-15)
now drives the minimum CNCF110/CBD16 producer slice before accepting its main
route: SBT DSL -> Git DSL -> managed resource I/O and Process-file rejection ->
default-deny/opt-in command and HTTP -> shared classification and real workflow
continuation. P6-SBX01..05 belong once to Phase 6. This also separates ordinary
focused tests from unnecessary runtime/JAR preparation. Successors reuse the
contract/evidence; PHASE-6.3 remains the aggregate full-suite owner. Full Safety
Sandbox stays later work. The original Phase 6 duration estimate predates this
scope and must be reassessed for remaining work. See the
[detailed journal](../journal/2026/10/2026-10-15-phase-6-managed-effects-and-sbt-plan.md).

Security projection adopted 2026-10-14: the
[Phase 6 family security profile](phase-6-security-review-profile.md) assigns
Slice/Boundary selection to 6, Step evidence to 6.1, FIX/recording to 6.2 and
Phase/Final assessment to 6.3. It preserves the split and PHASE-6.3 aggregate
validation ownership; producer plans alone are not implementation acceptance.

Decision: 2026-10-05; replanned at the user stop on 2026-10-07 (Asia/Tokyo)

Phase 5 / 5.1 Goal execution boundary, adopted 2026-10-11:
[preserve mixed work -> Phase 5 accepted release and stop -> separately instructed Phase 5.1 accepted release and Goal end](../journal/2026/10/2026-10-11-phase-5-release-boundary-handoff.md).
Checkpoint `2291d2fec20cf7335a8bec2af4077c4b2dd0e71a` saves existing work without
acceptance. Latest user revision: Phase 5 releases the completed implementation
at that stage; actual connection verification moves to Phase 5.1. The later
explicit user decision approves an unverified formal Phase 5 release with
commit only: additional tests/review are NOT RUN — USER-APPROVED, not PASS.
Open connection work is carried forward, not declared
complete. Phase 5 formally released in
`cf053352161bccc59871395cc8797e492f5e13c6`; the later explicit user instruction
resumed Phase 5.1 under the same Goal. Its latest fixed normal-use/check-removal
functional acceptance is PASS; the user has authorized its formal local release
without additional tests/review. Native commit/closure success establishes that
release and the adopted Goal end. Phase 6+ remains
planned future work outside this Goal's revised ending scope, not completed or
cancelled. The longer rollout below remains a roadmap, not authority to advance
automatically beyond either release/stop boundary.

Planned follow-up, adopted 2026-10-11: [Phase 5.2](phase-5.2.md) completes the
minimal public Operation contract and removes residual checks before Phase 6.
The user requires complete migration: no legacy input interfaces, compatibility
adapters or fallback routes remain for future skills to adopt. This additional
plan preserves the preceding release/Goal history and does not start execution.

Successor design, adopted 2026-10-12: [Phase 5.3](phase-5.3.md) replaces the
uniform JSON-string business carrier with declared Start, work-result and Human
answer types. Start returns one session ID per execution; Advance and replies use
that ID without extra target selectors. JSON is a transport representation. Phase 5.2's
control-copy removal remains; Phase 6 consumes the Phase 5.3 contract.

Phase 5.3's delivery is the common GoalPhase/SplitPhase/RepositorySync loop:
Start -> Advance -> work Request or Human question -> typed Submit -> display
the returned chat-oriented result -> Advance again. It removes unnecessary
information retrieval and supporting code as well as obsolete selectors.
Phase 6 implements actual client/skill/chat integration with no extra status
queries or AI acknowledgement step; Wait, Terminal and errors govern continuation.
The adopted priority is lower AI cost and simpler implementation/debugging over
fewer transport calls. Advance stays separate from Start/Submit; accepted-result
display and the following Advance require no additional AI decision step.

Session identity clarification, 2026-10-12: Phase 5.2 hands off the runtime's
current identity inputs and selector inventory. Phase 5.3 P53-06 replaces the
many-target session lookup with Start returning one session ID per execution and
removes public selection/instance/context/request ambiguity selectors. Phase 6
defines and implements [ID acquisition/allocation, caller retention and propagation](phase-6.md#session-identity-lifecycle--2026-10-12)
across independent command/REST calls and independent developments in different
chats (P6-ID01..03). A usable chat ID may be proposed; otherwise Start allocates
one. Clients retain the returned ID. Distinct executions use distinct IDs.
This does not expand Phase 5.2 implementation; Phase 5.3 owns the runtime
identity correction and Phase 6 its actual client integration.

Execution/progress ownership, adopted 2026-10-12: Phase 6.1 implements the whole
command lifecycle for SBT/Git and other supported tools in sm-workflow, while AI
performs semantic edits/judgment. Phase documents remain the external plan and
progress interface; runtime encodes them and refreshes the latest relevant plan
before next-work selection, retaining execution facts and updating only owned
progress fields. No unchanged-content proof or AI command/log relay is required.
Phase 5.3 hands off its common loop without new acceptance gates. Phase 7 uses
the same execution/synchronization for workspace and overlay work; Phase 8
selected extensions and Phase 9 convergence/provider routing preserve it.
See [Phase 6 command ownership](phase-6.1.md#command-execution-ownership--2026-10-12)
and [live synchronization](phase-6.1.md#phase-documents-and-live-plan-synchronization--2026-10-12),
with P6-EX01..03 and P6-PS01..04 in the connected checklist. These are planned
deliveries, not a claim of existing implementation or authority to resume a Goal.

Delegation policy, adopted 2026-10-12: normally progress through parent chat and
sm-workflow, delegating semantic work for appropriate cost/capability, reasoning,
independence or context scope. The parent normally uses an upper-tier programming
profile; bounded implementation/fix favors suitable cheaper profiles, while
review can use a stronger profile and a separate, focused context as needed.
No fixed model names, most-expensive default, full-history review inheritance or
mandatory child per step. Phase 6 implements this
[operating policy](phase-6.md#parent-chat-delegation-and-review-context--2026-10-12)
with P6-DG01..03; Phase 9 extends it with validation-observed re-routing.

Production-first revision, adopted 2026-10-18: [Phase 10](phase-10.md) owns the
unfinished CBD-backed P6-SQ01..05 integration and CBD15/15.1 handoffs. Its
[checklist](phase-10-checklist.md) retains their IDs and unchecked status.
Phase 6.1 keeps ordinary reviewer-result/Admission/commit, 6.2 repair and 6.3
integrated review/Final/full validation. Initial production use after 6.3 does
not wait for CBD. Phase 10 is scheduled separately after initial use; it neither
waits for nor blocks unrelated 6.4/7/8/9 work. Keep the accepted Phase 6 managed-
effect/CBD16 slice and all existing source/evidence. See the
[development handoff](../journal/2026/10/2026-10-18-production-first-cbd-review-phase-10.md).

The earlier 2026-10-13 staged-review allocation and same-day CBD15/15.1 adoption
remain history. Transfer unfinished integration rather than claiming it complete.
Generic review, independence, actual authority and meaningful acceptance remain
required for initial use.

Revised 2026-10-06: Phase 5 builds and validates the runtime without creating
skills. Phase 6 defines abstract thinking modes and the skill interface, creates
and connects the skills; Phase 6.3 starts gradual real use after integrated acceptance.
This replaces the earlier plan to start skill-based use after Phase 5.

Build a small usable system, start using it, and grow it from actual development
needs. The current rollout is **Phase 2 -> Phase 5 / Phase 5.1 -> Phase 5.2 -> Phase 5.3 -> Phase 6 -> Phase 6.1 -> Phase 6.2 -> Phase 6.3 -> Phase 6.4 -> Phase 7 -> Phase 8**.
Phase numbers do not require Phases 3 and 4 to precede Phase 5.

Revised 2026-10-09: [Phase 5.1](phase-5.1.md) names the connected correction for
necessity-first check removal and session-resolved public Operations. It is a
subphase of Phase 5, not a reset of its acceptance or development history. Only
session context and actually required parameters/results cross the caller
boundary; Workflow resolves its internal context. Skill and CLI wiring remain
in Phase 6, using the corrected runtime contract.

| Phase | Delivery boundary | Use and carryover |
| --- | --- | --- |
| [2: First usable runtime slice](phase-2.md) | Completed finite direct-Operation slice in `ed1a76b07bb00c88123c50ef907f90604d3ce1bf` | Retain its actual evidence/history; later scope corrections do not reopen Phase 2 |
| [3: Resolved Failure Model](phase-3.md) | Supply the modeled failure contract to implementation workers | Orthogonal extension, scheduled by real need and its upstream dependencies |
| [4: Service Bus development events](phase-4.md) | Publish development events and integrate monitoring | Orthogonal extension, not a prerequisite for initial use |
| [5: Minimum runtime foundation](phase-5.md) | Phase 5-stage implementation and recorded limitations, including delivered execution/resource/metadata-facing contracts and check-removal changes | Formal release commit and stop; unverified state approved by user, no new tests/review and no claim of completed connection verification |
| [5.1: Connection verification and session-resolved Operations](phase-5.1.md) | Fixed normal public Operation request/result progression, safe selected file effects, NEW execution from the beginning, and removal of unnecessary checks | Functional acceptance PASS; formal release without additional validation/review is user-approved, with native closure establishing Goal end. Preserve history/counters; no direct AI executor, skill integration or excluded recovery guarantees |
| [5.2: Minimal interface and complete migration](phase-5.2.md) | Complete session-resolved Start/advance/result inputs, align public definitions and remove residual checks and legacy routes | Followed by Phase 5.3 business-type redesign; preserve preceding closure/history |
| [5.3: Common Workflow loop and minimal data access](phase-5.3.md) | Align all three families with typed Start/Advance/Submit, one session per execution and chat-oriented submission results | Before Phase 6; delete obsolete selectors, unnecessary reads and their processing without compatibility routes |
| [6: Typed modes and real skill/client completion](phase-6.md) | Actual semantic request/result/session/provider loop and bounded GoalPhase task | Freeze the usable client contract; successors remain planned |
| [6.1: Runtime commands, live plans and Step Admission](phase-6.1.md) | Actual command lifecycle, document sync, deterministic maintenance and ordinary review/commit | Consume Phase 6 typed effects/evidence; no CBD review handoff wait |
| [6.2: FIX, recording and dependency decisions](phase-6.2.md) | Correction/improvement/reset, semantic maintenance, actual discovery recording and Human dependency decisions | Consume Phase 6.1; recording does not adopt repairs |
| [6.3: Integrated quality/Final and start of use](phase-6.3.md) | Three-family connected acceptance (P63-USE01..03), PHASE/FINAL and aggregate repository-full validation | Ordinary SplitPhase and single-repository RepositorySync; limited single-repository GoalPhase; no wait for 6.4/7/8/9/10 |
| [6.4: Common Evidence adoption and development operation](phase-6.4.md) | Adopt Phase 12 automatic archive/Journal/read; complete failure/batch/compression/retention use | Preserve active E1/history and all P64 IDs; own consumer validation/closure |
| [12: Common Evidence and automatic Action recording](phase-12.md) | Actual common identity/save/read and Advance-to-Journal connection for one log producer | Short priority runtime delivery before 6.4 E2, independent of 11.1 and 7–10; [checklist](phase-12-checklist.md) |
| [7: Multi-repository synchronization and artifact overlay](phase-7.md) | Synchronize/validate the workspace and consumer artifact selections using CNCF-discovered project purpose/feature queries | Full practical GoalPhase adoption with related-project management; retain local-development/sync/artifact ownership and existing overlay acceptance |
| [8: Runtime extensions and practical closure](phase-8.md) | Resolve necessary Phase 2 carryover and needs found during use | Reassess necessity, finish selected work and close this rollout |
| [11: Common Journal and trial skills](phase-11.md) | Actual common record/read/find, accepted Finding recording, packaged/deployed skills and Phase 6.4 trial-host connection | Short priority delivery before remaining 6.4 work; no 6.4 archive or 7–10 completion dependency |
| [11.1: Journal decisions, issues and handoffs](phase-11.1.md) | Related semantic records, correction/history, state/query and bounded batch management | Reuse 11 independently; not a prerequisite for the 6.4 trial |


## Applied Phase 6 split — 2026-10-13

### Production availability by completed Phase — clarified 2026-10-18

These are release/acceptance commitments, not claims that current code already
provides every feature. Initial production use starts after 6.3 acceptance;
6.4 storage, 7 workspace breadth, 8 selected extensions, 9 observed routing and
10 CBD review integration are not prerequisites for that initial use.

| Completed Phase | Available delivery | Production-use boundary |
| --- | --- | --- |
| 6 | Installed skill/client start, work/result/continuation, configured semantic roles and shared typed Git/SBT/File effects | Bounded real task; whole-family initial production acceptance remains in 6.3 |
| 6.1 | Runtime command/result ownership, live plan/writeback, maintenance and ordinary review/Admission/local Step commit | Basic development route; no mandatory CBD review |
| 6.2 | TEST_FIX/REVIEW_FIX, affected validation, findings/recording, continuation/reset and dependency decisions | Repair-inclusive development route ready for integrated acceptance |
| 6.3 | Installed skills, integrated review/Final and aggregate validation | `sm-split-phase` and single-repository `sm-repository-sync`: ordinary production use. `sm-goal-phase`: limited production use within one selected repository, through development/repair/validation/review/local closure |
| 6.4 | Adopt shared Evidence/Journal references and real storage; batch, compression and retention operation | Adds operation to existing use tiers; no duplicate store or new initial-use gate |
| 7 | Root/related-repository dependency-aware sync, dedicated worktrees and development artifact publish/select/verify/detach/prune | Full practical adoption of GoalPhase together with related-project management. GoalPhase still closes locally; RepositorySync and artifact operations own cross-project synchronization and artifact selection |
| 8 | Finite extensions selected from observed use and retained needs, potentially additional transports/providers/management and sandbox scopes | Actual delivered features depend on explicit scenario selection; no blanket promise of all server/MCP/local-LLM features |
| 9 | Detect non-convergence from validation/repair history and switch admitted provider/profile within the acceptance boundary | Adds observed routing and cost/convergence improvement to existing role selection and repair |

The concrete 6.3 condition is
[single-repository production acceptance](phase-6.3.md#single-repository-production-acceptance--2026-10-18),
tracked once as P63-USE01 in the
[shared checklist](managed-test-suites-phase-checklist.md#phase-63-production-use-acceptance--2026-10-18).
The [development handoff](../journal/2026/10/2026-10-18-phase-63-single-repository-production-handoff.md)
preserves active work and existing evidence. A normal one-repository sync such
as development-workstation must not be deferred to Phase 7 because that Phase
also contains RepositorySync work. Repository names are examples, not hard-coded
targets or authorization to push them during planning.

The [three-skill use-tier clarification](phase-6.3.md#three-skill-use-tiers--2026-10-18)
and [latest handoff](../journal/2026/10/2026-10-18-three-skill-production-tiers-handoff.md)
make this distinction explicit. Limited GoalPhase use is a supported scope, not
permission to skip required review, validation or actual local closure. Normal
SplitPhase use does not imply Phase 7 worktree provisioning or artifact management.

The current planned sequence is **6 -> 6.1 -> 6.2 -> 6.3 -> 6.4 -> 7**, followed by
unchanged 8/9 scheduling. [Phase 6's split map](phase-6.md#applied-split--2026-10-13)
assigns every adopted requirement once; earlier references to the broad "Phase 6"
in this roadmap describe that complete family. Exact current owners are the rows
above and the shared checklist. General gradual use begins after Phase 6.3;
Phase 6 itself must prove a bounded actual skill/client task. Complete validation is
`final-only` / `sbt-full-suite`, owner PHASE-6.3; each member still needs focused
validation, independent review, Step acceptance, closure and local release.
Needed CNCF/Cozy integration follows Phase 6's adopted existing-main scope;
the earlier wait-only instruction is superseded for that scope.
The four-member split and aggregate ownership are retained. P6-SBX01..05 add
the bounded correction to Phase 6 as recorded above. The user
subsequently adopted Phase 6.4 as additional evidence-storage scope with its
own validation/closure; it does not move the aggregate owner from 6.3, postpone
gradual-use eligibility or absorb existing Phase 6.2 recording responsibilities.
Phase 6.4's [checklist](phase-6.4-checklist.md) is separate from the managed-suite
checklist. This planning addition claims no implementation, execution or commit.

## Cost-first delivery order

Cost-first execution order, adopted 2026-10-18: [6.3](phase-6.3.md#cost-first-implementation-order--2026-10-18)
reuses accepted setup/evidence, completes ready ordinary split/single-repository
sync routes, fills limited GoalPhase gaps, then accepts/releases the complete
initial-use scope. [Phase 7](phase-7.md#cost-first-execution-order--2026-10-18)
connects one producer/consumer through A into C workspace synchronization before
the remaining generation/selection/prune and workspace branches. Required
driver-specific generation moves forward with the main route. Closure obligations,
6.4's predecessor role, existing work IDs and accepted evidence remain unchanged.
See the [development handoff](../journal/2026/10/2026-10-18-production-cost-first-execution-order.md).

The [Core execution-overhead follow-up](phase-6.3.md#execution-overhead-in-ordinary-production-acceptance--2026-10-18)
also belongs to 6.3's existing P63-USE01..03 acceptance. Preserve active 6.2 work
and evidence; prove host-owned continuous progression, proportionate batches and
applicable validation reuse in the normal installed routes. Report available cost
facts and unknowns there, without an extra benchmark, gate or savings threshold.
The [adoption handoff](../journal/2026/10/2026-10-18-phase-63-execution-overhead-handoff.md)
records the remaining gaps and safe adoption sequence.

## Independent post-adoption review integration

| Phase | Delivery boundary | Scheduling |
| --- | --- | --- |
| [10: CBD Review Integration](phase-10.md) | CBD prepare/external execution/result acceptance, then integrated review, affected repair and Final reuse; P6-SQ01..05 | After initial production adoption and required bounded producer handoffs; no dependency on whole 7/8/9 completion and no initial-use gate |

## Scope and implementation policy

Latest planned follow-up: [Phase 5.2 complete migration](phase-5.2.md), followed
by [Phase 5.3 common Workflow loop and minimal data access](phase-5.3.md).
[Phase 5.1 fixed acceptance and release](phase-5.1.md) retains the Goal ending
boundary above; the new plan does not resume it. Phase 2 and Phase 5 releases
are complete. The following Phase 2 resumption account is retained
as history, not the currently executing Phase:
[Phase 2 remaining connected delivery](phase-2.md).
Development resumed after the explicit 2026-10-07 plan-adoption instruction.
Finish the pending producer handoff
and normal host connection first, prove a complete SplitPhase route, connect
RepositorySync then GoalPhase, and perform integrated validation/review/closing.
All three routes remain required. Preserve completed work rather than restarting
the implementation from this plan. Phase 2 acceptance does not require Phase 6
skills, Phase 7 workspace breadth or Phase 8 comparative cost/transport coverage.
The broader historical Goal and the current Phase completion boundary are
reported separately; retaining history does not restore deferred gates.

The detailed 2026-10-07 execution plans preserve the accepted scope:

| Phase | Connected implementation order | Completion/handoff |
| --- | --- | --- |
| [5](phase-5.md#practical-completion-condition) | Record the delivered Phase 5-stage tree and explicit user-approved unverified disposition; preserve mixed work and actual history | Formal stage release commit only and stop; carry open connections to Phase 5.1 |
| [5.1](phase-5.1.md#current-fixed-acceptance-boundary--2026-10-11) | Latest fixed normal-use/check-removal boundary supersedes broader historical A/B/C requirements | Bounded connected acceptance/review, formal release and Goal end; no skills or restored over-guarantees |
| [5.2](phase-5.2.md#connected-implementation-order) | One minimal public contract through all three normal workflows; migrate connected callers/storage and remove old paths in the same batch | Integrated specs/review; hand session-resolved behavior to Phase 5.3 |
| [5.3](phase-5.3.md#connected-implementation-order) | Connect the full GoalPhase loop first, then SplitPhase/RepositorySync; align types/responses and delete old selection and unnecessary-read processing in the same delivery | Integrated specs/review; hand the common loop, session and chat-display contract to Phase 6 without compatibility routes |
| [6](phase-6.md#goal-and-connected-implementation-order) | Actual installed skill/client/modes and one-session result loop on accepted runtime | Accepted bounded GoalPhase completion and frozen interface |
| [6.1](phase-6.1.md#goal-and-connected-implementation-order) | Runtime commands/live plans/maintenance and ordinary Step review/Admission | Actual CBD-independent Step Admission/commit and execution/plan/evidence handoff |
| [6.2](phase-6.2.md#goal-and-connected-implementation-order) | FIX/improvement/reset, recording and dependency decisions | Accepted connected branches and complete contracts |
| [6.3](phase-6.3.md#goal-and-connected-implementation-order) | Integrated PHASE/repair/FINAL and setup/use acceptance | Aggregate full validation and gradual-use eligibility |
| [6.4](phase-6.4.md#main-route-first) | Adopt Phase 12 real automatic one-log route, then failure/batch/compression/retention | Consumer acceptance on the common service; preserve accepted E1 and existing history |
| [7](phase-7.md#execution-plan--2026-10-07) | A: native publish/select/status through actual SBT resolution; B: Cozy generation/selection lifecycle; C: workspace sync using the same resources and validation runtime | D: combined overlay/workspace acceptance, CNCF Phase 101 full closure, then Phase 7 closure |

These letters are work ordering, not new tracking IDs or approval/review gates.
Write matching specs with the connected implementation. Reuse existing runtime
and producer evidence at its actual scope; check changed producer/consumer
connections and required regressions. Review the integrated behavior, not every
helper. Phase 7 native operation wiring starts in A rather than after all adapter
work. Its workspace work can proceed after shared bindings are ready without
waiting for unrelated generation/prune details. All accepted checklists remain.
Gradual daily skill use starts after Phase 6.3; no requirement is silently removed
to make a phase appear complete.

- Phase 2 practical completion does not claim every original runtime feature is
  implemented. Its revised contract takes precedence over historical full-scope
  statements. Keep completed evidence and unfinished work distinguishable.
- Phase 5 formally releases the delivered foundation with the user's approved
  NOT RUN disposition, without building provisional skills;
  Phase 5.1 verifies actual executor/resource/metadata/managed-validation connections.
  Phase 5.2 completes the minimal interface and removes remaining unnecessary
  checks and compatibility routes. Phase 5.3 restores explicit business types
  in Operation contracts while keeping JSON at the transport boundary, before
  skills are developed.
  Phases 6–6.3 deliver the skill-connected minimum; 6.3 starts gradual real use;
  Phase 7 proceeds alongside that use. Do not wait for Phases 7/8 to start use.
- Abstract thinking modes can change a skill's task, context and expected result,
  not only its model settings. Design their interface and selected skills together
  in Phase 6; do not prematurely freeze a Phase 5 public skill contract.
- Fix missing behavior that prevents a supported real-use scenario early. Add
  nonblocking work to an active phase only when directly related and bounded;
  otherwise record it in Phase 8 with its observed use case and impact.
- Freeze connected implementation and Executable Specifications for each usable
  delivery. Compile and run focused tests as needed, then validate/review the
  integrated behavior. Avoid a separate acceptance cycle per internal helper.
- Preserve required independent review and meaningful validation. After a fix,
  recheck affected behavior and required regressions rather than repeating
  unrelated checks. Specification expectations remain grounded in agreed behavior.
- Phase 8 is not an obligation to implement every old idea. Record why an item
  proved unnecessary or superseded; move genuinely new ambitions to future plans
  rather than leaving this rollout permanently open.
- Phase 7 includes [Development Artifact Overlay](../spec/development-artifact-overlay.md)
  as native product functionality, not a skill-managed artifact workflow. Use
  CNCF resources and SBT/Cozy adapters; leave SBT cache exclusion to its tools
  without a machine-wide product mutex. Development-harness restrictions stay
  outside the public product contract.
- [Managed Test Suites](../notes/managed-test-suites-and-verification-cost-guard.md)
  are allocated by the [Phase 5/6/7 checklist](managed-test-suites-phase-checklist.md).
  AI designs test metadata and selects Slice features; runtime queries CNCF
  Phase 103 discovery, with actual runtime connection acceptance in Phase 5.1.
  Executable test annotations/CML properties are the source
  of test declarations. The management file remains the Slice validation plan:
  acceptance conditions, metadata selection, additional operation refs and typed
  parameters. Evidence records the actual execution; metadata is not duplicated.
  ADMISSION 1min/FULL 10min are cost targets, not failure or expiry rules. FIX
  revalidation uses SMOKE and the same admitted Slice FOCUSED without silent
  expansion. Runtime warnings require a human decision before AI improvement.
  Phase 4 event delivery and later dashboards/analytics do not block this slice.
- Fix convergence uses the current affordable measurements and bounded AI hints.
  Ordinary repair count is a convergence parameter; hygiene/minor corrections
  do not increment it but their repetition still affects convergence judgment.
  A separate absolute hard limit of 10 automatic Fix cycles counts all batches,
  including hygiene/minor work. Phase 5 owns both counts/guard transitions,
  Phase 6 supplies AI assessments and Phase 7 uses the shared behavior. No
  automatic allowance reset, project-lifetime repair total or new expensive
  instrumentation is introduced.
- Orca-derived Issue tracking extends those same boundaries: Phase 5 owns scoped
  issue identity, deterministic Fix reconciliation, evidence-confirmed resolution
  and resume reuse; Phase 6.2 connects typed fixer/reviewer feedback; Phase 7 applies
  them across repositories/overlay selections. Zero-fix is a stall signal that
  still allows supported partial progress. Narrowing intermediate review cannot
  discard issues or required acceptance coverage. Missing stable source identity
  is not a new execution blocker; no hash/copy/TTL integrity mechanism is added.

Use the ordinary Phase 8 list and existing journals for carryover. No new hash,
snapshot, whole-content comparison, approval-expiry rule or independent
unchanged-state proof is introduced. Preserve existing Goal identities, original
bases, PLAN epochs, review/repair histories and actual validation evidence.

This roadmap changes planning only. It neither completes a Phase nor changes a
live Goal, performs the user's scheduled stop, resumes development, or publishes
or commits a release. The existing development task applies the revised scope
at its coherent stopping/handoff boundary.

The 2026-10-06 resumption is retained in the
[resumption record](../journal/2026/10/2026-10-06-three-workflow-resumption.md).
The subsequent 2026-10-07 user stop supersedes it: CNCF runtime validation 1059
passed 123 tests / 17 suites; SimpleModeler source/spec are applied but unvalidated,
and request 1060 was not started. See the
[stopped handoff](../journal/2026/10/2026-10-07-approved-help-and-runtime-bootstrap-resumption.md)
and [replanning decision](../journal/2026/10/2026-10-07-development-plan-optimization.md).
This planning revision neither resumes the Goal nor checks off runtime acceptance.

Decision record: [incremental-use rollout](../journal/2026/10/2026-10-06-incremental-use-phase-rollout.md).
Interface/use timing revision: [Phase 6 skill integration](../journal/2026/10/2026-10-06-phase-6-skill-integration-boundary.md).
Phase 2 workflow selection: [three-workflow practical boundary](../journal/2026/10/2026-10-06-phase-2-three-workflow-boundary.md).


## Validation metadata producer/consumer sequence

[CNCF Phase 103](https://github.com/asami/goldenport-cncf/blob/main/docs/phase/phase-103.md)
provides the shared ABI and discovery. Phase 5 preserves the delivered foundation;
Phase 5.1 verifies actual runtime queries/selection/execution connections.
Phase 6.2 connects metadata/test authoring skills and Phase 7 extends use
across repository/overlay contexts. CNCF Phase 101 resource work remains separate.
[textus-cbd-support Phase 14](https://github.com/asami/textus-cbd-support/blob/main/docs/phase/phase-14.md)
static analysis/KPI uses the same ABI and runtime evidence;
implementation is assigned to Mac mini, with coordination documents updated here.
Its application completion is not a gate for Phase 5 runtime acceptance.


## GitHub planning summary — retained historical context

The following summary was published in the merged GitHub parent
`915e0e5ff0d5910353b62bec0940eab560e085e9`. The detailed roadmap above
retains the subsequently adopted Phase 5.2/5.3 and Phase 6–9 directions.

> The planned sequence is Phase 2, Phase 5/5.1, Phase 6, Phase 7 and Phase 8.
> This page indexes the Phase 6–8 planning material published here. Earlier
> phase work and its acceptance remain separate.
>
> | Phase | Planned delivery | Use |
> | --- | --- | --- |
> | [6: Thinking modes and skill integration](phase-6.md) | Connect selected skills and command invocation to the existing workflow runtime | Begin gradual use after connected acceptance |
> | [7: Multi-repository synchronization and artifact overlay](phase-7.md) | Connect native publication, selection and workspace behavior to real consumers | Continue use and validate the selected overlay scenarios |
> | [8: Runtime extensions and practical closure](phase-8.md) | Select necessary extensions from carryover and real-use findings | Close the finite supported scope without requiring every optional provider experiment |
>
> The [manual local LLM trial](phase-8-local-llm-reference.md) is reference
> information. It is not a completion condition for any of these phases.

## 2026-10-21 repository catch-up planning

[Phase 7](phase-7.md) owns the shared dependency-property model and provider-owned `localJar` publication boundary. New [Phase 13](phase-13.md) owns separate recursive repository catch-up to GitHub or same-machine main, including rebase and human-intervenable merge. Phase 13 reuses Phase 7 graph/typed Git/Continuation facilities and does not modify the existing RepositorySyncWorkflow remote convergence contract. Both remain planned; numbering does not mandate immediate execution.
