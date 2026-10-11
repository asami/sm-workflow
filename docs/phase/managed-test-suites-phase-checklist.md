# Managed Test Suites: Phase 5/5.1/6/7 delivery and acceptance

Date: 2026-10-06
Status: planned; implementation/executable acceptance not performed by this update
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
Phase 5.1 connection verification:
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

## Phase 6 — AI/skill connection

Execution mapping (2026-10-07): [Phase 6 batch A](phase-6.md#execution-plan--2026-10-07)
first connects the actual skill/client/result path. Batch B covers P6-TS01..06
on that same interface using distinct branches of the shared driver. Batch C
accepts the integration. Reuse Phase 5 deterministic policy coverage rather
than re-running its complete state/threshold matrix through live AI.

- [ ] P6-TS01: AI changes tests and CNCF annotations/CML properties through
      normal Candidate/review/admission. Slice planning selects feature IDs,
      explicitly revising insufficient classification when needed. Runtime uses
      FOCUSED plus feature union and admitted supplemental refs/typed parameters.
      AI authors the Slice validation management file; no duplicate metadata
      registry or per-run improvised selectors.
- [ ] P6-TS02: TEST_FIX/REVIEW_FIX share the FIX semantic contract with distinct
      trigger evidence and original requirement/current Candidate; both route
      as implementation-like work. Runtime, not the fixing skill, owns validation
      outcomes and next transitions. Exercise both connected correction paths.
- [ ] P6-TS03: A fix can identify test/suite/requirement/design gaps instead of
      blindly changing production code. Invalid validation design follows explicit
      plan/metadata revision; TEST_FIX cannot silently broaden the admitted profile.
- [ ] P6-TS04: Human selects a recorded warning, AI receives bounded context and
      proposes no change/metadata revision/code change/both; normal admission and
      validation apply. Later observed executions support any improvement claim.
      Warning alone triggers neither AI work nor repair authorization.
      This existing human-selected path also supports a developer asking AI to
      reconsider the development plan from cost/convergence/issue indicators.
      A bounded replanning example may exercise the same path: the developer
      adopts the plan, history/counters remain, and indicators alone neither
      diagnose the plan as wrong nor change it. No additional acceptance gate
      or measurement infrastructure is required for this usage.
- [ ] P6-TS05: Fix results report semantic depth, cross-file coupling, minor-fix
      rationale and bounded convergence hints separately from deterministic
      counts. Multiple independent file-local changes can remain STANDARD_LOGIC;
      coordinated logic is COMPLEX_LOGIC. AI optimism does not override the
      guard; BLOCKED triggers handoff. No extra instrumentation is required just
      to enrich a hint, and the skill cannot reset/exempt substantive work by
      changing task names or treating every SIMPLE_LOGIC change as a minor bug.

- [ ] P6-TS06: Connected fixers account for assigned IDs and cannot declare
      unsolicited issues FIXED. Reviewers reuse supported IDs and report relevant
      coverage/resolution. Exercise FIXED awaiting validation, UNRESOLVED with
      supported partial progress, DECLINED, BLOCKED, recurrence and bounded
      intermediate scope. Final obligations remain; unknown identity mapping
      does not require semantic matching infrastructure.

Source-note coverage: connected cases 9, 11–15, 21–25, 28–37 and 40–47. Reuse Phase 5 runtime
acceptance rather than duplicating metadata interpretation or test-selection paths in skills.
Start gradual skill-based use only after the Phase 6 connected scope is accepted.

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
      Resume preserves issues/counts and reuses applicable completed evidence;
      changed overlay inputs invalidate corresponding evidence despite unchanged
      source revision. Use the Phase 5/6 policy without resetting the hard limit
      or creating a repository-specific ledger (source cases 46–49).

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
