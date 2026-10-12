# sm-workflow incremental-use roadmap

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

Execution/progress ownership, adopted 2026-10-12: Phase 6 implements the whole
command lifecycle for SBT/Git and other supported tools in sm-workflow, while AI
performs semantic edits/judgment. Phase documents remain the external plan and
progress interface; runtime encodes them and refreshes the latest relevant plan
before next-work selection, retaining execution facts and updating only owned
progress fields. No unchanged-content proof or AI command/log relay is required.
Phase 5.3 hands off its common loop without new acceptance gates. Phase 7 uses
the same execution/synchronization for workspace and overlay work; Phase 8
selected extensions and Phase 9 convergence/provider routing preserve it.
See [Phase 6 command ownership](phase-6.md#command-execution-ownership--2026-10-12)
and [live synchronization](phase-6.md#phase-documents-and-live-plan-synchronization--2026-10-12),
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

Staged quality review, adopted 2026-10-13: [Phase 6](phase-6.md#staged-quality-review-across-projects--2026-10-13)
drives CNCF Phase 106 generic Evidence/Admission integration, Cozy Phase 81
CML review metadata and cbd-support Phase 15 scoped quality evaluation. Connect
a real Step first, then integrated Phase Review, affected repair/re-review and
Final evidence reuse. P6-SQ01..05 and each owner's checklist define the bounded
dependencies; do not wait for unrelated full roadmaps or create circular Phase
closure gates. Phase 7 consumes the route; Phase 9 adds convergence/provider
switching. Initial review/Final behavior belongs to Phase 6, without reopening
Phase 5.2/5.3 or implying automatic execution of another project's plan.

Revised 2026-10-06: Phase 5 builds and validates the runtime without creating
skills. Phase 6 defines abstract thinking modes and the skill interface, creates
and connects the skills, and starts gradual real use after connected acceptance.
This replaces the earlier plan to start skill-based use after Phase 5.

Build a small usable system, start using it, and grow it from actual development
needs. The current rollout is **Phase 2 -> Phase 5 / Phase 5.1 -> Phase 5.2 -> Phase 5.3 -> Phase 6 -> Phase 7 -> Phase 8**.
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
| [6: Thinking modes and skill integration](phase-6.md) | Thinking modes and skill contracts, test metadata design/revision, TEST_FIX/REVIEW_FIX, AI recording of Hygiene/development candidates, human-selected improvement and dependency Component boundary approval | Start gradual skill-based real use after connected acceptance; dependency edits yield to developer judgment in planning/implementation/review, and recording discoveries does not adopt them as required work |
| [7: Multi-repository synchronization and artifact overlay](phase-7.md) | Synchronize/validate the workspace and consumer artifact selections using CNCF-discovered project purpose/feature queries | Continue use; deliver the bounded SimpleModeler overlay acceptance and collect optional later needs in Phase 8 |
| [8: Runtime extensions and practical closure](phase-8.md) | Resolve necessary Phase 2 carryover and needs found during use | Reassess necessity, finish selected work and close this rollout |

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
| [6](phase-6.md#execution-plan--2026-10-07) | A: mode/skill/client/result contract through a complete GoalPhase task; B: validation planning, FIX, AI document recording of nonblocking findings and human-selected improvement on the same interface | C: integrated acceptance, usable installation and gradual real use |
| [7](phase-7.md#execution-plan--2026-10-07) | A: native publish/select/status through actual SBT resolution; B: Cozy generation/selection lifecycle; C: workspace sync using the same resources and validation runtime | D: combined overlay/workspace acceptance, CNCF Phase 101 full closure, then Phase 7 closure |

These letters are work ordering, not new tracking IDs or approval/review gates.
Write matching specs with the connected implementation. Reuse existing runtime
and producer evidence at its actual scope; check changed producer/consumer
connections and required regressions. Review the integrated behavior, not every
helper. Phase 7 native operation wiring starts in A rather than after all adapter
work. Its workspace work can proceed after shared bindings are ready without
waiting for unrelated generation/prune details. All accepted checklists remain.
Initial skill use still starts after Phase 6; no requirement is silently removed
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
  Phase 6 delivers the skill-connected minimum and starts gradual real use;
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
  and resume reuse; Phase 6 connects typed fixer/reviewer feedback; Phase 7 applies
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
Phase 6 connects metadata/test authoring skills and Phase 7 extends use
across repository/overlay contexts. CNCF Phase 101 resource work remains separate.
[textus-cbd-support Phase 14](https://github.com/asami/textus-cbd-support/blob/main/docs/phase/phase-14.md)
static analysis/KPI uses the same ABI and runtime evidence;
implementation is assigned to Mac mini, with coordination documents updated here.
Its application completion is not a gate for Phase 5 runtime acceptance.
