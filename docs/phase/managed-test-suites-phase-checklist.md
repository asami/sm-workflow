# Managed Test Suites: Phase 5/5.1/6–6.3/7 delivery and acceptance

Date: 2026-10-06
Status: in-progress; PHASE-6.1 acceptance scope only
Phase acceptance scope: PHASE-6.1
Current status: P61-PLAN accepts P6-PS01..04 through its complete protected Step
review and mechanical conformance closure. Its local Step commit is next;
P61-COMMAND and P61-ADMISSION remain unfinished. The Phase 6.1 comprehensive
review and final release remain open; repository-full validation is
deferred-not-run to PHASE-6.3.
Historical PHASE-6 projection: closed; the Phase 6 owned rows are accepted through the original S6-01
protected independent Step review and its five repository-local acceptance
commits are complete. The sole Phase review and its M0 naming repair are
accepted. This final local release projection includes the accepted shared
aggregate tooling correction; formal closure requires the native release
commit and readback.
All other Phase ownership and completion states are preserved.
Owner: each row's declared Phase owner; the Phase 6 family shares this checklist.
Update rule: update a row from actual evidence and its owning Phase's existing
acceptance decision; preserve historical allocations and other Phase ownership.
Current Phase 5.1 applicability: the owning user's 2026-10-11 fixed normal-use
boundary supersedes this historical allocation. Restart/recovery, replay,
complete-history and competing-winner clauses below are not current acceptance
conditions. Shared metadata/validation infrastructure is consumed normally,
not exhaustively reverified by sm-workflow. The original IDs and evidence stay
historical; no new coverage or review observation automatically becomes a gate.
See [current fixed completion conditions](phase-5.1.md#current-fixed-acceptance-boundary--2026-10-11).
Design: [Managed Test Suites and Verification Cost Guard](../notes/managed-test-suites-and-verification-cost-guard.md)
Decision: [cost-feedback journal](../journal/2026/10/2026-10-06-managed-test-suites-cost-feedback.md)

Rows track connected behavior, not separate review cycles per helper. Use existing
tests/selectors as suite compositions; no duplicated test-code hierarchy is
required. Concrete contracts and all source-note acceptance scenarios remain
authoritative. This allocation creates no Phase 1/2 implementation gate.

## Phase 5 foundation / Phase 5.1 connected acceptance — runtime, direct operation and measurement

Latest allocation, 2026-10-11: Phase 5 releases its completed stage implementation
with its actual limitations recorded. The subsequent explicit user instruction
approves the unverified state for a FORMAL release commit without further tests
or review; NOT RUN — USER-APPROVED is not PASS or an unofficial-release marker.
The actual connection acceptance of P5-TS01–12 below moves
to Phase 5.1, keeping every original ID, scenario and evidence record. All open
boxes remain open, not Phase 5 release blockers or a claim of completed features.
Relevant existing deterministic/functional cases still verify delivered Phase 5
code. Missing connection implementation is carried as OPEN Phase 5.1 work;
no fixtures can stand in for that phase's actual execution evidence.

Historical execution mapping (2026-10-07), now carried to
[Phase 5.1 connection verification](phase-5.1.md#connection-verification-transferred-from-phase-5--2026-10-11):
[Phase 5 batch A](phase-5.md#execution-plan--2026-10-07)
supplies the real runtime/resources and simplified contracts. Batch B implements
P5-TS01/02/03/12 as the selection-to-result path, P5-TS06/07/08/09/10 as its
FIX/Issue/guard loop, and P5-TS04/05/11 as observations over the same executions.
Batch C accepts their integration. These groups reuse fixtures and existing
policy tests; they are not separate helper reviews or new completion gates.

- [ ] P5-TS01: Consume real CNCF Phase 103 versioned metadata/discovery and typed
      operation binding, including class/scenario merge fixtures and invalid
      input errors. Source tests/annotations/CML properties remain authoritative;
      no suite registry, consumer annotation parser or arbitrary argv path.
- [ ] P5-TS02: Project purpose queries and Slice FOCUSED feature union select
      resolved operations plus planned supplemental references/typed parameters.
      Deduplicate identical invocations, retaining deliberate parameter variants.
      The management file holds acceptance conditions without copied metadata. Missing required
      coverage is a gap. Metadata revisions/coverage reductions remain normal
      Candidate/review/admission work; fixing code cannot silently alter selection.
- [ ] P5-TS03: RunTestSuite executes actual discovered operations and records
      Candidate, metadata/ABI and policy revisions, query/features/operation refs,
      input context, duration, outcome and bounded result evidence. Repeated runs
      retain separate receipts; no full-log copies or integrity ledger.
- [ ] P5-TS04: ADMISSION 20s passes without warning; 61s passes with warning;
      a 5s failure stays FAILED. Configurable threshold and justified LONG_RUNNING
      expectations work, including warning on excess beyond their own threshold.
- [ ] P5-TS05: FULL <=10min and explicitly justified longer FULL behave correctly;
      recurring excess gives a declared-policy warning/review signal, never a
      duration-only failure. HEAVY supports hours and requires explicit policy
      or request, without inheriting ordinary ADMISSION/FULL budgets.
- [ ] P5-TS06: GoalPhase validates SMOKE -> Slice FOCUSED -> REVIEW -> ADMISSION
      -> Closing. Candidate-changing TEST_FIX/REVIEW_FIX returns through SMOKE
      and the same FOCUSED profile; REVIEW_FIX reaches re-review. A failed test
      alone is not sufficient closure unless the admitted policy says so.
- [ ] P5-TS07: Profile inadequacy requests explicit plan/metadata revision, not
      silent scope expansion. ERROR/TIMEOUT and cost warnings do not automatically
      request TEST_FIX. Warning persistence/query/CLI display and bounded
      human-selected improvement endpoints work without any skill implementation.
- [ ] P5-TS08: Convergence guard permits slow convergence beyond the soft limit,
      uses ordinary repair count as a convergence parameter, stops divergence/AI
      BLOCKED, and prevents an eleventh automatic Fix at absolute hard limit 10,
      even after ten hygiene/minor-only cycles and favorable AI hints. A successful
      tenth result may close. Hygiene/history/GWT comments and minor bug fixes
      increment only the automatic count, while their results affect convergence;
      mixed substantive batches increment both counts once, not per file/finding
      or test command. Dispatch reserves a cycle; restart cannot erase it or
      automatically renew allowance. Continuation preserves counts; unrelated loops do not
      inherit old consumption. Missing optional metrics remain unknown without
      expensive new collection, and excluded-only stagnation still reaches the
      configured convergence handoff rather than resetting history.

- [ ] P5-TS09: Reconcile requested issue IDs exactly; reject unknown/unrequested,
      duplicate, missing or malformed responses without erasing Candidate work.
      FIXED alone stays unresolved; relevant fresh evidence resolves and later
      recurrence reopens the same issue. DECLINED remains open, UNRESOLVED can
      report partial progress, and valid BLOCKED stops. Known scoped metrics do
      not double-count observations; absent source identity remains unknown
      without blocking work or triggering fuzzy matching/extra collection.
- [ ] P5-TS10: Zero-fix without supported progress reaches STALLED handling;
      supported partial progress may continue within the absolute hard limit.
      Narrowed review neither closes unexamined issues nor reduces required
      validation/final coverage. Restart reuses completed applicable evidence,
      reruns remaining/invalidated steps and retains both counts/issue history.
      A commit alone does not invalidate prior applicable evidence; no TTL,
      integrity comparison or stage-as-commit requirement is introduced.

- [ ] P5-TS11: Operation expected duration is distinct from query duration;
      LONG_RUNNING membership does not suppress all query warnings. Export bounded
      shared metadata/evidence references for cbd-support Phase 14, including
      completeness, target feature context and measurement scope/unit, without requiring its
      application to run. ABI cases 50–54 join the real producer/consumer acceptance.

- [ ] P5-TS12: Admit/load the Slice validation management file and resolve its
      acceptance conditions, query, supplemental references and typed parameters.
      Reject unresolved/conflicting inputs; preserve execution requirements.
      Evidence records plan revision, query/reference origins and actual inputs;
      plan changes use ordinary admission/applicability (source cases 55–58).

Source-note coverage: cases 1–8, 10–13, 15–29 and 32–58; runtime side of case 9.
Test duration policy deterministically; do not sleep for minutes/hours just to
exercise warning thresholds. Actual provider execution/receipt integration must
also be tested. No skill creation/connection, Service Bus or dashboard is a gate.

## Phase 6 family — AI/skill connection

Applied split, 2026-10-13: [6](phase-6.md) -> [6.1](phase-6.1.md) ->
[6.2](phase-6.2.md) -> [6.3](phase-6.3.md). Each existing P6 requirement below
has exactly one delivery owner; original IDs and assertion text remain.
Historical batches A/B/C described the complete pre-split family. Actual initial
client/modes/session/provider work is 6, commands/plans/STEP Admission is 6.1,
FIX/improvement/recording/decisions is 6.2, and PHASE/FINAL/integrated use is 6.3.
Reuse Phase 5 applicable deterministic evidence; no repeated complete live-AI matrix.

### Phase 6 managed-effects correction — 2026-10-15

Requirement: [Phase 6 safety correction](phase-6.md#safety-correction-managed-gitsbt-and-capability-laundering--2026-10-15).
These are the five upstream P6-SBX IDs, each with one delivery owner here.
They are a concrete affected-contract correction, not an optional generic scan.
Exercise them through the existing connected driver and focused branch specs;
reuse CNCF evidence without replaying its internal policy suite per workflow.

Current Phase 6 acceptance projection (2026-10-17): S6-01's complete accumulator
and all 24 original independent repair obligations passed the existing Step
review. The P6-SBX01..05, P6-ID01..03 and P6-DG01..03 rows below are accepted at
that boundary. Earlier intermediate pending statements retain their historical
scope. Repair79/streak0 and the sole unconsumed Phase review are preserved;
Phase 6.3 remains the aggregate full-suite owner. Active evidence stays in local
private workflow storage, outside the committed development artifacts.

All nine original product main methods and executable launch routes are absent
from current source and the actual packaged JAR. Actual-used typed Git and
owning-workflow consumer migration, including history/fetch/integration/result
interpretation, and current-UoW typed SBT checks have scoped focused qualification.
Historical172C passed 66 checks in eight suites; actual188B passed 206 checks in
20 selected suites, including all seven normal SBT cases and normal/lost
completion acknowledgement rereads. Actual193 passed 139 checks in 21 selected
suites across all 18 concrete shared-fixture consumers (9 direct, 8 via
`NativeHostPreparationManagedFixture`, 1 via
`NativeHostPreparationOperationFixture`) plus 3 generated ABI/completion/retention
guards. The five File consumers, five Git-only examples and five native
completion guards retain their declared scopes and counts.
The [normal typed SBT scenarios](../spec/normal-typed-sbt-validation.md) cover
isolated and selected owning Repository/Goal checks and durable no-replay
readback. These results do not establish genuine installed operator/reviewer
acceptance, close P6-SBX01, Step or Phase, or establish runtime parallelism.
Product sbt uses native locks without a global wrapper or scripted prerequisite;
development SBT follows the registered shared-serial runner policy. Original
Goal/history and all review/acceptance and Phase 6.3 aggregate-validation duties
stay open.

Git/SBT DSL acceptance requires thin typed UoW intent: construction performs no
checks or launch, while interpretation checks the actual current context,
admits and executes the operation and returns its typed result. For SBT, one
External `SbtExec` occurrence must preserve this order, reuse the managed
process lifecycle and reject tagged generic ProcessExec bypass. Host-assembly
validation remains distinct. Focused construction/recording, context A/B,
permission/service/binding denial and bypass evidence, followed by actual
normal SBT success/failure/uncertainty and durable no-replay connection, has
bounded qualification under the existing producer and consumer evidence. This
does not change completion boxes or establish original installed acceptance.

Product-route retirement is distinct from residual generic definitions in the
selected installed host `phase6-command-main-2`: this configuration correction
replaces them with public strict V3, exactly 21 typed Git declarations/grants on
the original target and empty generic/SBT programs. The original required
validation policy remains required. The legacy Git diff document check is not
an SBT product test; a truthful normal SBT build/test target is still needed
before actual workflow progression. Task-local ordinary resource reads have a
1 MiB cap for passive preparation, whose actual normal-command validation remains
with the parent. The original malformed captured pending repair request remains
unchanged and subject to exact expected-text refusal; it is not repaired by
source or host migration. Original genuine installed task/mode/session/provider/
result and bounded Goal obligations, Step/epoch review and acceptance commits,
the sole full Phase review and local release remain open. Preserve Phase
6.1/6.2/6.3 responsibilities and Phase 6.3's sole aggregate-validation ownership.

- [x] P6-SBX01: **Owner: [Phase 6](phase-6.md).** Build/test through the CNCF
      protected internal SBT DSL (`sbt_test`, `sbt_compile`, etc.) with typed
      parameters/results, composed through the existing UoW route, with no
      arbitrary task string/shell/ProcessExec exposed to the AI worker;
      the actual result resumes the same workflow. Use SBT only for required
      build/test work. Ordinary focused tests do not depend on runtime/JAR
      preparation; tests consuming those artifacts explicitly prepare them.
      Preserve one selected real-SBT integration case, not SBT-based file I/O.
- [x] P6-SBX02: **Owner: [Phase 6](phase-6.md).** Required local Git status,
      diff and selected commit use CNCF protected internal operations such as
      `git_status`, `git_diff` and `git_commit`; `git_pull` when needed has explicit
      typed integration semantics. Preserve bounded repository,
      selected paths and mutation intent; do not expose arbitrary Git argv.
      Additional existing Git operations migrate only as required by the driver.
- [x] P6-SBX03: **Owner: [Phase 6](phase-6.md).** The five selected-document/
      conflict-file Process operations migrate to the protected CNCF File
      Workspace DSL, initially `file_read_text` / `file_write_text`. Ordinary
      logical-resource reads remain `read_resource` / `read_resource_text`;
      runtime limits apply during normal acquisition without a public
      `read_resource_bounded` safety variant or unlimited fallback. Workspace
      authority binds a logical root and coarse read/write permissions, not
      arbitrary host paths or per-file ACLs. Demonstrate read/edit/write ->
      SBT test -> Git diff -> same-workflow continuation, plus focused root-escape,
      read-only-dependency-write and read-limit rejection at their owning layer.
      Attempted file read/write through generic Process Execution is denied/not
      admitted; ordinary managed read/write succeeds within its selected scope.
      A pure read does not create a lock file or require a writable filesystem.
      No direct CAR Files API or Java/main wrapper replaces the denied route.
      Reuse applicable prior producer evidence; normal-entry/limit-binding and
      consumer composition changes require only their missing focused evidence.
      The full-read default candidate is 1 MiB, to be settled in CNCF runtime
      configuration. Large logs do not justify raising it implicitly. If the real
      driver requires head/tail/lines/search, implement only the selected bounded
      partial read, respecting runtime line/byte caps without whole-file buffering,
      and report its range/truncation meaning. Line/chunk streaming remains a
      later extension, not an additional Phase 6 completion requirement.
      See the [handoff](../journal/2026/10/2026-10-15-phase-6-file-workspace-dsl-handoff.md).
- [x] P6-SBX04: **Owner: [Phase 6](phase-6.md).** Unconfigured external command
      and outbound HTTP destination are denied by default; an explicitly admitted
      command/provider succeeds within declared scope. Metadata request alone
      cannot grant authority. Shell interpreters are excluded from ordinary
      command opt-in; command-name registration cannot launder other effects.
      Bounded normal-route composition evidence is available in
      [native client installation](../spec/native-client-installation.md), E3/E7/E8/E9:
      admitted exact-origin POST succeeds; an absent HTTP plan, GET-only method
      scope and missing Git component grants refuse the corresponding calls.
      Supplied baseurl/business metadata grants no authority. Reuse delivered
      CNCF classification and ordinary-shell refusal guarantees within their
      contract; no producer-policy replay or arbitrary-binary assurance is implied.
      Accepted by the existing independent Step review; this establishes only
      the bounded consumer contract and does not establish whole CBD16 delivery.
- [x] P6-SBX05: **Owner: [Phase 6](phase-6.md).** Consume the CNCF110 bounded
      producer implementation and CBD16 laundering classification on the actual
      workflow; recognized Process-to-file misuse is attributable, and proper
      typed Git/SBT/resource use is distinguished. No sm-workflow policy engine,
      private bypass or producer-test duplication is introduced. Record partial
      producer work-item completion/evidence, not whole Phase110/16 completion.
      The bounded CNCF usage API and CBD16 typed provider/normal private QUERY
      are authored (2026-10-17), with human-approved CBD main participation.
      S6-01D35 additionally authors the authorized existing CNCF Capability/Usage
      dependency and fresh-UoW facade in main and migrates CBD knowledge consumers
      to current schema/path carriers with exact Component/release admission.
      This is initial dependency integration, not acceptance of CBD11/14 or
      CNCF78 as whole Phases; the normal managed-effect QUERY remains connected.
      Normal generation/compilation and 270 focused tests in 25 suites have
      succeeded. After normal `cozyPrepareRuntime`, the FQN command through
      standard `CncfMain --component-dev-dir` returned six correct classifications
      over supplied source observations and one genuine invalid-input 400.
      Parent intake checked the original snake_case wire responses; the private
      camelCase harness failure remains recorded. The original independent Step
      review accepted this bounded consumer contract; whole Phase110/16
      completion is not inferred. Original Goal/history, epoch1 and
      repair79/streak0 remain; all 24 original independent repair obligations
      are resolved. PHASE-6.3 alone owns the aggregate full-suite boundary.

The original P6-EX/PS/SQ deliveries remain assigned to 6.1/6.3. They consume this
foundation; PHASE-6.3 remains the sole family aggregate full-suite owner.

### Session identity lifecycle

These original P6-ID entries move from the old Phase plan to this single checklist.

- [x] P6-ID01: **Owner: [Phase 6](phase-6.md).** Demonstrate proposing an available
      chat/host ID and omitting it when unavailable. In both cases retain the ID
      returned by Start at the documented client location; no Codex-only assumption.
- [x] P6-ID02: **Owner: [Phase 6](phase-6.md).** Use the actual client across independent
      command processes or REST requests for Start -> issued work -> result/Human
      answer as applicable -> advance -> intended outcome. Read the retained ID
      and propagate it each time; prebuilt ExecutionContext fixture injection is insufficient.
- [x] P6-ID03: **Owner: [Phase 6](phase-6.md).** Two different chats conduct independent
      developments through the ordinary client with separate returned session IDs;
      interleaved calls reach only their own execution. Another execution in one
      chat retains a distinct ID. No extra selector, candidate list or state copy;
      missing/invalid IDs produce ordinary input errors.

### Managed-suite FIX and improvement

- [ ] P6-TS01: **Owner: [Phase 6.2](phase-6.2.md).** AI changes tests and CNCF annotations/CML properties through
      normal Candidate/review/admission. Slice planning selects feature IDs,
      explicitly revising insufficient classification when needed. Runtime uses
      FOCUSED plus feature union and admitted supplemental refs/typed parameters.
      AI authors the Slice validation management file; no duplicate metadata
      registry or per-run improvised selectors.
- [ ] P6-TS02: **Owner: [Phase 6.2](phase-6.2.md).** TEST_FIX/REVIEW_FIX share the FIX semantic contract with distinct
      trigger evidence and original requirement/current Candidate; both route
      as implementation-like work. Runtime, not the fixing skill, owns validation
      outcomes and next transitions. Exercise both connected correction paths.
      Carry the Phase 6 Effective Acceptance Boundary through both paths:
      required failures block acceptance; excluded assurance proposals neither
      block acceptance nor authorize another repair.
- [ ] P6-TS03: **Owner: [Phase 6.2](phase-6.2.md).** A fix can identify test/suite/requirement/design gaps instead of
      blindly changing production code. Invalid validation design follows explicit
      plan/metadata revision; TEST_FIX cannot silently broaden the admitted profile.
      Exercise an explicit adopted Goal/Phase override of a project default in
      the shared driver and verify consistent request/Review/Admission behavior.
      Conflicting adopted conditions need an owner decision during planning;
      providers cannot resolve them by silently enlarging the scope.
- [ ] P6-TS04: **Owner: [Phase 6.2](phase-6.2.md).** Human selects a recorded warning, AI receives bounded context and
      proposes no change/metadata revision/code change/both; normal admission and
      validation apply. Later observed executions support any improvement claim.
      Warning alone triggers neither AI work nor repair authorization.
      This existing human-selected path also supports a developer asking AI to
      reconsider the development plan from cost/convergence/issue indicators.
      A bounded replanning example may exercise the same path: the developer
      adopts the plan, history/counters remain, and indicators alone neither
      diagnose the plan as wrong nor change it. No additional acceptance gate
      or measurement infrastructure is required for this usage.
- [ ] P6-TS05: **Owner: [Phase 6.2](phase-6.2.md).** Fix results report semantic depth, cross-file coupling, minor-fix
      rationale and bounded convergence hints separately from deterministic
      counts. Multiple independent file-local changes can remain STANDARD_LOGIC;
      coordinated logic is COMPLEX_LOGIC. AI optimism does not override the
      guard; BLOCKED triggers handoff. No extra instrumentation is required just
      to enrich a hint, and the skill cannot reset/exempt substantive work by
      changing task names or treating every SIMPLE_LOGIC change as a minor bug.
      Through the existing decision route, explicit user continuation approval
      resets both active counts once, retaining history/trends and permitting
      subsequent batches without per-finding reapproval. Accepted Step closure
      with its commit starts the next Step at zero; checkpoint/WIP/within-Step
      repair commits do not reset an unfinished loop. Verify these boundaries
      in the connected driver under the 2026-10-11 reset clarification.
      Phase 6.2 implements missing runtime decision/guard/Step-close transitions
      and verifies normal client invocation, not just skill instructions.
      Include exhaustion -> approval -> multiple repair batches and accepted
      Step commit -> next Step, retaining the unfinished-Step checkpoint case.
      This is mandatory Phase 6.2 closure work; Phase 9 consumes its evidence and
      checks compatibility with provider switching rather than owning delivery.

- [ ] P6-TS06: **Owner: [Phase 6.2](phase-6.2.md).** Connected fixers account for assigned IDs and cannot declare
      unsolicited issues FIXED. Reviewers reuse supported IDs and report relevant
      coverage/resolution. Exercise FIXED awaiting validation, UNRESOLVED with
      supported partial progress, DECLINED, BLOCKED, recurrence and bounded
      intermediate scope. Final obligations remain; unknown identity mapping
      does not require semantic matching infrastructure.

### Staged quality review: Phase 6 cross-project driver

Requirement: [Phase 6 staged review](phase-6.3.md#staged-quality-review-across-projects--2026-10-13).
Producer/provider ledgers are CNCF REA-01..04 (Phase 106), Cozy RMS-01..04
(Phase 81) and cbd-support SQR-01..04 (Phase 15). All items remain planned.

- [ ] P6-SQ01: **Owner: [Phase 6.1](phase-6.1.md).** Phase 6.1 uses actual CML metadata, lint and a scoped independent
      semantic review through normal invocation and typed results. Covered
      requirements admit the Step and runtime performs its local commit. Generic
      ABI contains no STEP/PHASE/FINAL vocabulary; advisory output is not silently
      converted to Assurance or commitment authority.
- [ ] P6-SQ02: **Owner: [Phase 6.3](phase-6.3.md).** Phase 6.3 demonstrates that two locally acceptable Steps can conflict
      at an integrated Usage Contract. Phase Review detects the cross-Step gap;
      Step evidence alone cannot claim integrated PASS. Include the matrix's
      overchecking/duplicate-state judgment in the appropriate semantic scope,
      not a mandatory new deterministic lint rule or a per-finding review cycle.
- [ ] P6-SQ03: **Owner: [Phase 6.3](phase-6.3.md).** A bounded repair triggers affected re-review with stable finding
      identities and adequate surrounding context. Unrelated applicable evidence
      remains reusable; unresolved blockers prevent closure, nonblocking recorded
      findings do not require an empty backlog.
- [ ] P6-SQ04: **Owner: [Phase 6.3](phase-6.3.md).** Phase 6.3 completes Final and release using sufficient Phase Review
      plus applicable repair/validation evidence. Observe that no new comprehensive
      semantic review or duplicate full validation is invoked solely for Final.
      Required deterministic release checks and actual command outcomes remain.
- [ ] P6-SQ05: **Owner: [Phase 6.3](phase-6.3.md).** Insufficient coverage, incompatible evidence and changed acceptance
      produce specific gaps/affected work; missing facts cannot become PASS.
      Commit-only progression/time passage does not force re-review. Reuse relies
      on actual applicability, without hashes, content copies or TTL. Use bounded
      branch specs alongside the connected driver, not a broad live-AI matrix.

These are the shared end-to-end cases, not substitutes for owner-specific checks
and independent review. Producer closure requires only its bounded handoff, not
the whole sm-workflow Phase 6.3 closing commit; Phase 6.3 records the integrated result.

### Security review projection — adopted 2026-10-14

The [security profile](phase-6-security-review-profile.md) adds the following
acceptance detail to existing work. Preserve every P6 ID and completion state;
these rows create no separate review cycle or duplicate delivery checklist.

All rows are conditional on the profile's three applicability conditions:
explicitly adopted security contract, an affecting change, and insufficient
existing applicable evidence. Unaffected/out-of-scope concerns require no extra
execution; covered concerns reuse evidence. Neither creates a provider wait or
blocker. Ordinary use within CNCF/Cozy's delivered contract conditions reuses
their guarantees without application-level repetition of producer security tests.
Existing connected scenarios must cover this selection/reuse behavior as well
as a genuinely missing scoped check; no separate matrix, certificate, ledger or
per-Step security gate is required.

| Owner / existing work | Added acceptance detail |
| --- | --- |
| Phase 6 mode/context/provider/minimum Effective Acceptance Boundary | Select applicable security requirements/evidence for the changed Slice through the existing main route. Required missing evidence is a gap; developer/AI identity is not assurance. |
| Phase 6.1 / P6-SQ01 | Consume only missing applicable security lint/scoped review via the existing Step Evidence/Admission connection. Covered/unaffected cases make no extra security call; no extra security Step review. |
| Phase 6.2 / P6-TS02/03, P6-CB01..04 and finding recording | An attributable in-boundary security finding follows ordinary FIX/affected validation and dependency decisions; out-of-boundary proposals are recorded without automatic adoption. |
| Phase 6.3 / P6-SQ02..05 | Consume only uncovered affected cross-Step security assessment; retain affected repair/finding identity and reuse applicable Phase/producer evidence at Final. Only required applicable missing evidence creates an Admission Gap/Unknown. |

CNCF107/Cozy83/CBD16 provide the security content; imported plans do not prove
available implementation or acceptance. These handoffs supplement the existing
CNCF106/Cozy81/CBD15 connection. Preserve ongoing work/history and reassess only
affected evidence. Aggregate full validation remains exclusively PHASE-6.3;
Phase 6.4 remains outside that sequence.

### Parent chat, delegation and review context

Requirement: [Phase 6 operating policy](phase-6.md#parent-chat-delegation-and-review-context--2026-10-12).
Phase 6 owns client/placement/context; Phase 6.2 reuses it for FIX,
and Phase 6.3 accepts integrated behavior. These are not separate live-model trials.

- [x] P6-DG01: **Owner: [Phase 6](phase-6.md).** Ordinary parent chat -> runtime -> semantic work -> Submit/display
      -> Advance works without a mandatory child for every step. Commands remain
      runtime-owned. Delegated semantic results use that same completion route.
- [x] P6-DG02: **Owner: [Phase 6](phase-6.md).** Configuration can select a suitable lower-cost implementation/fix
      profile independently of the upper-tier parent default, and a stronger
      review profile when required. Changing concrete model/profile names does
      not change business semantics or bypass validation/independence. Verify
      selection with configured profiles, not a comprehensive live-model ranking.
- [x] P6-DG03: **Owner: [Phase 6](phase-6.md).** A reviewer receives scoped requirements, changes, necessary code/
      contracts and actual results without automatic full parent-history inheritance.
      Relevant missing context can be obtained; final review coverage is preserved.
      The same model can serve an independent review context; increased reasoning
      effort alone cannot establish independence. Inspect actual request/context
      assembly in the existing connected review, without copy/hash proof machinery.

### Command execution and live Phase-plan synchronization

Requirements: Phase 6 [command ownership](phase-6.1.md#command-execution-ownership--2026-10-12)
and [plan synchronization](phase-6.1.md#phase-documents-and-live-plan-synchronization--2026-10-12).
Phase 6.1 implements these in the connected driver; Phase 6.2 reuses them
for FIX/recording/improvement and Phase 6.3 accepts integrated behavior.

- [ ] P6-EX01: **Owner: [Phase 6.1](phase-6.1.md).** A normal skill-connected task reaches an actual SBT validation
      and a local Git effect through runtime-owned execution. Runtime prepares
      cwd/argv/environment, waits, captures the real result and progresses; the
      AI assignment contains no command execution or log-transcription work.
- [ ] P6-EX02: **Owner: [Phase 6.1](phase-6.1.md).** Exercise command failure -> semantic AI diagnosis/fix -> runtime
      revalidation, and Git conflict -> AI resolution -> runtime Git progression.
      Unknown completion is not successful execution or permission to retry.
      Existing effect permissions remain; missing tools yield an actual problem.
- [ ] P6-EX03: **Owner: [Phase 6.1](phase-6.1.md).** A representative configured generator/formatter and supported
      application acceptance command use the same execution owner. Where the
      command starts a service, runtime handles readiness and owned cleanup.
      Use real representative effects plus bounded branch specs, not exhaustive
      coverage of every external tool or an AI-runner fallback.
- [x] P6-PS01: **Owner: [Phase 6.1](phase-6.1.md).** Start encodes a real Phase document set, then an edit between work
      steps adds/reorders work. The next dispatch uses the current plan with
      stable item IDs, preserving completed results and history without extra
      caller comparison parameters or per-iteration AI plan reconstruction.
- [x] P6-PS02: **Owner: [Phase 6.1](phase-6.1.md).** Remove/change the acceptance of an in-flight item. Its result is
      retained with its issued context; unresolved acceptance/continuation uses
      the existing decision route. Duplicate IDs and invalid references fail as
      input contradictions; a checkbox cannot fabricate test/release success.
- [x] P6-PS03: **Owner: [Phase 6.1](phase-6.1.md).** Edit narrative/planning content after internal import and before
      progress writeback. Runtime updates only owned progress fields in the
      latest document, preserving those edits. An unresolved overlapping edit
      is reported without silently overwriting either party's work.
- [x] P6-PS04: **Owner: [Phase 6.1](phase-6.1.md).** A normal plan refresh retains Goal/base/epoch, fix/review history
      and applicable actual evidence. Changed acceptance is evaluated at its
      affected scope; no blanket retest, hash/copy proof, TTL or permission renewal
      follows merely from an edit. Demonstrate refresh before dispatch after Wait.

### Review and maintenance responsibilities

Requirement: [Phase 6 responsibility split](phase-6.2.md#review-and-maintenance-responsibilities--2026-10-12).
Exercise these within the existing Phase 6 review/FIX driver, not a new campaign.

- [ ] P6-HR01: **Owner: [Phase 6.1](phase-6.1.md).** AI changes an owned source file without maintaining its history
      fields. Runtime applies the declared rule before applicable validation/
      review/commit. The resulting fields follow that rule; the AI assignment
      and review do not request/check source-history edits. Include actual tool
      execution and preserve prior origin/authorship as required by the rule.
- [ ] P6-HR02: **Owner: [Phase 6.2](phase-6.2.md).** One integrated AI review covers a meaning-dependent GWT issue
      and another applicable item such as a misleading name/comment/example.
      It returns concrete findings and their dispositions without creating a
      review cycle per category or requiring a blanket audit of unrelated files.
- [ ] P6-HR03: **Owner: [Phase 6.2](phase-6.2.md).** Adopted GWT and other semantic Hygiene corrections are batched
      into the ordinary AI REVIEW_FIX request; the worker edits actual files
      and returns assigned-issue outcomes. Distinguish explanatory-only changes
      from assertion/behavior changes for validation and convergence accounting.
      Unadopted nonblocking observations are recorded without blocking release.
- [ ] P6-HR04: **Owner: [Phase 6.1](phase-6.1.md).** A deterministic maintenance error remains a tool/configuration
      outcome, not an AI history-edit task. Routine mechanical maintenance does
      not create an AI Fix cycle; actual AI Hygiene fixes retain the existing
      convergence/absolute-cycle rules. No hash/copy proof or additional repeated
      test/review loop is introduced to establish maintenance success.

### Dependency Component Boundary — Phase 6 connected decision handling

Requirement: [Phase 6 dependency boundary](phase-6.2.md#dependency-component-boundary-and-human-approval--2026-10-12).
Source: [2026-10-12 journal, commit 13ac53f](https://github.com/asami/sm-workflow/blob/13ac53f/docs/journal/2026/10/2026-10-12-dependency-component-boundary.md).
All items are planned and unverified. These extend the existing connected driver,
not the managed-test vocabulary or a separate execution/approval framework.

- [ ] P6-CB01: **Owner: [Phase 6.2](phase-6.2.md).** Planning for Component A identifies that dependency B must be
      internally modified. The AI returns the reason, proposed scope and affected
      Usage Contract; runtime retains the proposal and presents a Human Decision
      before dependency work is adopted/dispatched. Exercise an external library
      and a same-repository dependency: Component boundaries, not repository or
      worktree boundaries, determine the decision.
- [ ] P6-CB02: **Owner: [Phase 6.2](phase-6.2.md).** Implementation/FIX discovers the same need after an initially
      in-scope plan. It yields before editing B. Review identifies an unapproved
      cross-Component edit that escaped earlier detection and yields before work
      acceptance, preserving the actual edits and context for developer judgment.
      Neither stage silently expands scope or treats a passing test as approval.
- [ ] P6-CB03: **Owner: [Phase 6.2](phase-6.2.md).** Through the normal client, explicit approval adopts the stated
      dependency scope, normally as a separate work unit with acceptance criteria
      and a consumer link. Rejection/defer or a pending answer never authorizes
      that edit. An AI claim of approval cannot substitute for the developer's
      answer. A newly proposed change outside the approved scope yields again.
- [ ] P6-CB04: **Owner: [Phase 6.2](phase-6.2.md).** Normal Usage Contract consumption and diagnostic inspection do
      not trigger approval. Explicitly approved dependency work proceeds without
      repeated prompts, even across repositories; unapproved internal changes
      still yield when classified as hygiene or preserving the public API.
      Demonstrate one actual skill/client proposal -> runtime Human Decision ->
      developer answer -> permitted next work, plus deterministic branch specs.
      No new scope-copy parameters, proof ledger, TTL or filesystem monitor is
      introduced; document detection limits and complete integrated review.

Source-note coverage for P6-TS01..06: connected cases 9, 11–15, 21–25, 28–37 and 40–47. Reuse Phase 5 runtime
acceptance rather than duplicating metadata interpretation or test-selection paths in skills.
Start gradual skill-based use only after Phase 6.3 accepts the complete connected family.

## Phase 7 — RepositorySync and Development Artifact Overlay

Execution mapping (2026-10-07): [Phase 7 batches A/B](phase-7.md#execution-plan--2026-10-07)
cover P7-TS03 and selection-related P7-TS04 on real native overlay operations.
Batch C covers P7-TS01/02 and repository-related P7-TS04. Batch D accepts their
combination with the existing overlay/workspace scenarios; no second validation
runtime or additional phase-wide review cycle is introduced by these rows.

- [ ] P7-TS01: Git policy selects required repositories; each uses its own
      CNCF-discovered FULL operation membership. Dependencies precede root validation; unchanged
      repositories get no precautionary FULL. Missing required metadata coverage is an explicit gap,
      not ad-hoc commands or skipped coverage.
- [ ] P7-TS02: Passing slow FULL produces a warning while preserving PASS;
      explicit long-running exceptions work. No automatic retry, AI remediation,
      HEAVY promotion or SBT-wide product serialization occurs.
- [ ] P7-TS03: Overlay verification uses an admitted metadata purpose/feature query
      plus affected generation/compile operations. Actual selection and resolved
      dependencies accompany execution results, even if source revisions match.
      Detect incompatible generated runtime API usage and validate compatible
      selection/update/rollback paths without per-run test selection by AI.

- [ ] P7-TS04: Scope same-named issue sources to their repository/provider.
      Normal continuation after a requested answer preserves issues/counts and
      reuses applicable completed evidence within the same execution;
      changed overlay inputs invalidate corresponding evidence despite unchanged
      source revision. Use the Phase 5/6 policy without resetting the hard limit
      or creating a repository-specific ledger (source cases 46–49).
      This row does not require interrupted-effect recovery, replay or complete
      history guarantees. Abandonment follows Phase 5.1's new-execution fresh start.

These extend existing Phase 7 Git/overlay acceptance; no duplicate full-test
command-selection implementation is permitted. Suite/component storage follows
CNCF APIs, not a second physical-path/configuration convention.

## Limits retained across all phases

No autonomous suite optimization, adaptive statistical thresholds, automatic
warning suppression, management hashes, unchanged-content certificates, copied
full logs or global build-tool locks. A time target is not a timeout, expiry,
test failure or authority to discard coverage. Service Bus warning publication
belongs to Phase 4 integration; dashboards and statistical analysis are later
extensions, not prerequisites for the initial runtime/skill/use slices.
