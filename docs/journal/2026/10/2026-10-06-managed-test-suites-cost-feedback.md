# Managed Test Suites and Verification Cost Feedback Loop

Date: 2026-10-06
Status: design decision
Related: Candidate-Admission Model, deterministic operations/closing, Human-in-the-Loop Continuation/Admission

## Context

Job-management development exposed excessive development time from repeated validation. Two causes matter: AI/Skill may choose too broad a regression scope for a small change, and selected tests may themselves contain expensive setup/repeated computation. A Skill rule that blindly executes a fixed focused set after every repair amplifies both.

An initial idea was to leave test selection with Codex while sm-workflow only records duration/retry reasons. That leaves official validation selection nondeterministic: every agent turn can rediscover a different safe set and repeatedly broaden it.

The revised decision is to make purpose-specific Test Suites first-class sm-workflow development assets.

## Decision

AI designs and maintains TestSuite definitions. sm-workflow registers admitted definitions, selects them by Workflow purpose, executes them deterministically, records evidence, and evaluates simple cost policy.

~~~text
AI: semantic TestSuite design/improvement
sm-workflow: purpose selection + execution + measurement + policy evaluation
Human: decide which warnings deserve improvement work
~~~

Official Workflow validation is therefore not an ad-hoc Codex test-selection decision. Codex may still use narrow exploratory tests during implementation; those are distinct from managed focused/admission/full suites that produce Workflow validation evidence.

## First guard: admission validation over one minute

The first practical rule is deliberately small: an ordinary ADMISSION TestSuite taking more than 60 seconds records a duration warning.

The warning is not a test failure and does not reject Admission by itself. It surfaces development-process friction to a human, who can ask AI to inspect the suite/tests, improve them, and register a revised definition.

Some tests legitimately require more than one minute. Their definition may declare LONG_RUNNING with rationale and expected duration/explicit threshold. This remains observable and is not a blanket ignore-performance flag.

~~~text
managed suite
 -> deterministic execution
 -> measured evidence
 -> warning
 -> human identifies improvement point
 -> AI reviews suite/tests
 -> revised suite is admitted
 -> later measured evidence
~~~

Human-in-the-Loop remains at the improvement decision. sm-workflow does not autonomously optimize or rewrite tests.

## Why this belongs in sm-workflow

Existing design already places build/test and other established procedural commands in typed deterministic operations rather than Skill/AI orchestration. Managed Test Suites extend that boundary from who launches the process to which admitted validation asset represents each Workflow purpose.

This strengthens Candidate-Admission semantics: admission asks for declared validation evidence rather than asking AI to improvise a validation procedure each time.

## Why suite maintenance still belongs to AI

Correct focused/admission/full composition depends on code structure, regression risk, test architecture and evolving project knowledge. Automatic dependency/test selection in sm-workflow would encode semantic reasoning as brittle deterministic machinery.

AI performs that semantic analysis when a suite is created/reviewed. The result becomes an admitted reusable project asset; ordinary executions remain deterministic until evidence and human judgment request another revision.

~~~text
AI analysis -> admitted TestSuite -> deterministic repeated execution -> evidence -> occasional AI revision
~~~

## Implementation consequences

A definition needs stable identity, purpose, revision and typed operation binding. Duration policy needs expected duration/threshold, NORMAL versus LONG_RUNNING, and rationale for long-running behavior.

Execution produces a receipt bound to suite revision and candidate revision. Warning records observed duration and evaluated threshold. Full test logs need not be copied into Workflow state; bounded diagnostics/reference are enough.

Definitions belong with version-controlled project sm-workflow resources through CNCF logical Component resource APIs. Receipts/warnings belong to runtime state/evidence. Do not hard-code physical .textus paths or create a second configuration resolver.

The default ADMISSION warning threshold is 60 seconds and should be configurable through the existing CNCF Component configuration mechanism.

## Relationship to current phases

This is operational hardening and MUST NOT expand Phase 1 completion. Phase 1 explicitly defers production operational providers and cost dashboards. Implement this in the first suitable operational/provider phase after required runtime/resource boundaries are available, then integrate it with GoalPhase admission/closing.

Phase 4 Service Bus is a natural publication path for warnings/evidence. Control Center/cbd-support can later consume the facts for KPI/trend review.

Phase 7 dependency-aware RepositorySync should use managed FULL suites rather than invent another full-test command-selection mechanism. Its existing policy still decides when FULL is required; the registered suite decides what that project's FULL validation means.

## Guard against overengineering

Initial implementation MUST NOT add automatic test dependency analysis, adaptive thresholds, historical statistical anomaly detection in the execution path, autonomous AI remediation, hash/integrity ledgers, duplicate full log storage, or global sbt/build-tool locking.

Measure the simple case first. Immediate success is: a one-minute-plus admission test becomes a visible actionable warning and can drive one explicit Human -> AI improvement cycle.

## Expected effect

The symptom development is taking too long becomes attributable evidence. sm-workflow can identify which admission suite/revision consumed time. Once AI improves the suite, that decision is registered and reused; later Codex runs do not rediscover the same official selection strategy or silently broaden it out of caution.

Detailed normative/implementation design: docs/notes/managed-test-suites-and-verification-cost-guard.md

## Follow-up decision: staged validation and evidence-triggered FIX

The post-implementation path was refined further. The desired normal sequence is SMOKE -> FOCUSED -> semantic AI REVIEW. In Scala/sbt, SMOKE has unusually good leverage: executing a small selected test still requires compilation of the test source set, so a very small runtime sample can provide broad compile/type consistency evidence before more expensive focused execution.

When SMOKE or FOCUSED fails, the semantic action is named TEST_FIX rather than returning to a fresh IMPLEMENTATION or using an ambiguous generic repair. TEST_FIX means: revise the current Candidate using deterministic test-failure evidence. It does not mean blindly satisfy the failed assertion; AI may conclude that production code, test code, TestSuite definition, or an upstream requirement/design gap is the actual issue.

Review findings use REVIEW_FIX. TEST_FIX and REVIEW_FIX are two evidence-triggered forms of a common FIX concept. Both are implementation-like semantic work and neither has acceptance authority. Their distinction is the trigger/evidence contract: TEST_FAILURE versus REVIEW_FINDING.

After either FIX changes the Candidate, sm-workflow restarts deterministic validation at SMOKE. A FOCUSED failure therefore follows TEST_FIX -> SMOKE -> FOCUSED. Review findings follow REVIEW_FIX -> SMOKE -> FOCUSED -> re-review. The agent's claim that a fix is complete never substitutes for these checks.

Operational provider ERROR/TIMEOUT is not automatically TEST_FIX, because the Candidate may be correct. A slow successful test is also not TEST_FIX; duration warnings remain part of the separate Human -> AI TestSuite improvement loop.

This refinement further separates three concerns: AI constructs/revises semantic Candidates; sm-workflow produces deterministic validation evidence; AI Review evaluates semantic quality only after cheap and focused mechanical validation have passed.


## Follow-up decision: five validation purposes and bounded FULL

The managed purpose taxonomy is now SMOKE / FOCUSED / ADMISSION / FULL / HEAVY. These are validation objectives, not five duplicated test-code trees. AI composes each logical suite from existing tests/selectors, and strict physical subset nesting is not required.

The practical budgets are intentionally different. SMOKE should normally finish in seconds to tens of seconds; FOCUSED should remain bounded around the changed area; ADMISSION targets one minute or less; FULL targets ten minutes or less. These are engineering targets and cost guards, not correctness results.

FULL is explicitly defined as time-bounded comprehensive regression, not literally every expensive test. This avoids the common failure mode where the word full causes ordinary project validation to grow without bound. Tests that cannot reasonably fit the roughly ten-minute operational envelope should be optimized, represented more cheaply where valid, or moved to HEAVY. A project-specific FULL exception is allowed when explicit, justified, and registered with an expected duration.

HEAVY is the completeness-oriented outer class. It may run for hours and may contain exhaustive combinations, broad E2E/integration matrices, long-running concurrency/performance checks, large-data tests, or multi-toolchain matrices. It is not part of the ordinary implementation loop and is invoked only by explicit Workflow/release/milestone policy or human request.

This creates a useful pressure gradient: routine assurance stays fast, while expensive completeness is preserved rather than deleted. ADMISSION should not become FULL, and FULL should not become HEAVY merely because more tests exist.


## Follow-up decision: FOCUSED is Slice-scoped, project suites are fixed

A further distinction is now explicit. SMOKE, ADMISSION and FULL are fixed project-level managed suites. HEAVY is also project-level when defined, but optional and explicitly invoked. FOCUSED is not one global project suite: its concrete validation profile is selected as part of each Slice plan.

The project maintains reusable standard focused suites for functional areas. Slice planning should first reference one standard feature suite, then compose multiple admitted standard suites when necessary, and only then define a Slice-specific focused profile for a case that cannot be expressed cleanly by the standard catalog.

This moves focused-test selection to acceptance design. By the time implementation starts, sm-workflow already knows the exact logical FocusedValidationProfile. When implementation returns, the deterministic path is project SMOKE -> Slice FOCUSED -> AI REVIEW -> project ADMISSION. Codex does not choose individual official tests at that point.

TEST_FIX does not get authority to broaden the focused scope. It revises the Candidate and reruns SMOKE plus the same admitted Slice profile. If test evidence reveals that the profile itself is inadequate, the correct response is an explicit validation-design/Slice-plan revision. This keeps test-selection knowledge reviewable and reusable instead of hiding it inside an agent repair turn.

Repeated Slice-specific profiles should be reviewed for promotion into reusable standard feature suites. Thus recurring AI-discovered validation knowledge gradually becomes deterministic project test architecture.


## Follow-up decision: Fix Convergence Guard and semantic change depth

Fix/Test/Review loops need bounded convergence control rather than a simple retry counter. The chosen design combines deterministic change-surface metrics, AI semantic change classification, AI self-assessment, and explicit soft/hard cycle limits.

sm-workflow measures a ConvergenceVector for every Candidate revision: source files touched, test files touched, managed resources touched, external resources touched, lines added/deleted, and failed-test count. These values are retained as a vector and trend; they are not collapsed into an unexplained convergence score. Temporary expansion is allowed, because a valid investigation may broaden before contracting. Slow but real convergence may therefore continue for multiple cycles. Sustained expansion, oscillation or non-improvement can enter error/decision handling before the hard cycle limit.

AI additionally classifies each Fix revision as HYGIENE, TRIVIAL_COMPILE_FIX, SIMPLE_LOGIC, STANDARD_LOGIC or COMPLEX_LOGIC. The classification measures semantic depth, not physical file count. Multiple files containing independent file-local ordinary changes remain STANDARD_LOGIC. COMPLEX_LOGIC means one integrated logical change requires coordinated behavior across files/boundaries.

AI also returns PROGRESSING/STALLED/REGRESSING/BLOCKED as a convergence self-assessment. This is initially retained as advisory evidence for future calibration. BLOCKED is the exception: it is an explicit signal that the same automatic Fix loop should not continue, so sm-workflow enters typed error/decision handling immediately.

Cycle limits remain necessary even when the measured trend looks favorable. A soft limit permits continued CONVERGING/SLOW_CONVERGENCE work; a hard limit always terminates automatic cycling. Conversely, clear DIVERGING behavior or AI BLOCKED can terminate before the hard limit. Numeric limits are policy/configuration values, not embedded assumptions about how many Fixes are universally correct.

The resulting principle is: permit bounded exploration and slow convergence, detect widening change surfaces early, preserve AI semantic/self-assessment as separate evidence, and guarantee that automatic Fix cycling always terminates or hands control to an explicit decision/error path.


## Comparative study: VirtusLab Orca review/fix loop

A source-level review of VirtusLab/orca was performed as a comparison for the emerging sm-workflow Test/Fix/Review design. Orca shares the core principle that procedural orchestration should be deterministic code rather than instructions an AI is expected to remember. Its current review loop provided several concrete operational lessons.

### Findings worth adopting

Orca keeps an explicit open-finding record across review/fix rounds. Findings have identities; fixer outcomes are reconciled against the findings actually supplied; fixed findings are pruned from the carried open set, declined findings remain open, and a later reviewer can report a supposedly fixed finding again. This is stronger than merely counting failures and is adopted as a typed FixIssue ledger in sm-workflow.

Orca also reconciles the fixer's structured fixed/declined response against the request instead of trusting a free-form statement that everything was fixed. sm-workflow adopts the same principle at the Candidate admission boundary: TEST_FIX/REVIEW_FIX results must account for supplied FixIssue identities and cannot manufacture unrelated issue authority.

A particularly useful termination rule is that Orca stops when findings exist but the fixer reports no fixes. sm-workflow generalizes this as a deterministic zero-fix STALLED signal within the Convergence Guard. This complements, rather than replaces, change-surface trends, AI BLOCKED, and cycle limits.

Orca supports narrowing later review rounds to reviewers that previously reported issues. sm-workflow adopts the optimization principle only in bounded form: intermediate semantic re-evaluation may narrow to unresolved/recently-fixed evidence, but Candidate changes still pass project SMOKE and Slice FOCUSED, and required final review/ADMISSION scope cannot be reduced implicitly.

Orca's resumable stages also reinforce the rule that completed expensive work should not be repeated after a process restart. sm-workflow already has a more general mechanism through Candidate revision, Evidence freshness and Admission. The adopted rule is therefore to reuse fresh matching Test/Review evidence rather than introduce Orca's stage/commit model.

### Orca loop bounds and cost lesson

Orca's current implementation uses a default maximum of three fix turns. Its own research records that a larger historical iteration ceiling allowed long review loops and growing review payload/token cost. This is practical evidence for sm-workflow's decision to keep explicit cycle bounds even when convergence metrics look favorable.

sm-workflow intentionally goes further than a fixed cap: deterministic ConvergenceVector trends, FixIssue ledger movement, AI semantic change depth, AI self-assessment, soft/hard limits and explicit error/decision handoff are evaluated together. Slow genuine convergence may continue past a soft threshold, while clear divergence, zero-fix stall or AI BLOCKED can stop earlier.

### What is not copied

Orca's stage-as-Git-commit/resume-point model is not imported because sm-workflow already has Workflow state, Continuation, Candidate/Admission and Evidence freshness. Likewise Orca's concrete reviewer-selection machinery is not made a new sm-workflow authority layer. The useful operational behaviors are generalized into the existing architecture instead of creating a parallel orchestration model.

The comparison therefore reinforces the direction: use AI for semantic implementation/review/fix, reconcile its structured results deterministically, keep unresolved work explicitly identified, rerun required machine validation after edits, reuse fresh evidence, and make loop termination a Workflow concern rather than an agent prompt convention.


## Follow-up decision: CNCF Validation/Test Metadata ABI replaces suite registry

The Test Suite representation was simplified further. The normal source of truth is no longer a sm-workflow-managed suite definition/resource file. Executable specifications/test operations carry CNCF Validation/Test Metadata ABI annotations/properties, and sm-workflow constructs SMOKE/FOCUSED/ADMISSION/FULL/HEAVY selections by querying CNCF-resolved metadata.

Operation/scenario is the primary granularity, with class/specification metadata acting as defaults/shared feature context. One operation may participate in multiple purposes, allowing the same test program to serve SMOKE, ADMISSION and FULL without duplication.

FOCUSED becomes feature-driven rather than registry-driven. Slice planning selects feature identities; sm-workflow runs FOCUSED-tagged operations whose features match the admitted Slice selection. If the classification itself is insufficient, the Slice revises Test Architecture metadata explicitly rather than creating an ad-hoc runtime test list.

CNCF owns the portable semantic ABI and annotation/property merge/discovery semantics. sm-workflow owns runtime policy such as the one-minute ADMISSION and ten-minute FULL targets, execution receipts, freshness and warnings. cbd-support consumes the same ABI statically. This removes duplicated management data and makes metadata changes themselves visible Test Architecture changes.
