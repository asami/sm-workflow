# Managed Test Suites and Verification Cost Feedback Loop

Date: 2026-10-06
Status: design decision
Related: Candidate-Admission Model, deterministic operations/closing, Human-in-the-Loop Continuation/Admission

## Context

### 2026-10-12 follow-up: role-based delegation and review context

The user clarified that the ordinary interaction is parent chat <-> sm-workflow.
An upper-tier programming model is the parent operating default; GPT-6.1 Sol
high is a current example, not a fixed requirement. Expensive Astra is not the
routine programming default. Concrete model mappings remain environmental.
Implementation/repair should use the cheapest suitable profile where practical;
review may use stronger models when needed. Parent and delegated model choices
are independent, and no command-only subagent is required.

Review delegation also serves context isolation, including with the same model.
Supply applicable requirements, changes, necessary surrounding code/contracts,
actual validation and relevant findings rather than the entire conversation or
implementation deliberation. Permit additional relevant context, preserve final
review coverage and independence, and do not mistake higher effort for separation.
Model, effort, context scope and independence are separate selection dimensions.

Phase 6 now records the initial selection/context policy and P6-DG01..03 in its
existing connected acceptance. Phase 9 consumes it for observed re-routing.
This is a planning update; no live chat model, provider configuration or runtime
implementation has been changed.

### 2026-10-12 follow-up: command ownership and live Phase-plan synchronization

The user adopted runtime ownership of the entire command activity: SBT/Git and
other configured tools, including preparation, process lifecycle, actual result
collection and cleanup. AI does semantic editing/diagnosis/review, not command
execution or log forwarding. Existing effect permissions remain unchanged.

Phase documents are the external planning/progress interface. Runtime encodes
them as its working plan and refreshes the relevant latest documents during work,
before next dispatch. Stable item IDs retain actual results/history across plan
edits. Runtime projects structured progress into the latest owned fields; AI
authors narrative/rationale. In-flight scope/acceptance conflicts yield a specific
decision, not silent loss of work, blanket retesting or proof of unchanged files.
This is normal live synchronization, not recovery of abandoned executions.

Phase 6 implements both contracts in its connected driver (P6-EX01..03,
P6-PS01..04). Phase 5.3 supplies the common loop without new gates; Phases 7/8/9
consume the same ownership for workspace effects, selected extensions and
convergence/provider routing. No implementation, tests or release are claimed by
this planning update; active Goal/base/epoch/counters/history remain untouched.

### 2026-10-12 follow-up: review work versus deterministic maintenance

The user clarified that source-history comment editing belongs entirely to
sm-workflow; AI workers/reviewers should not have to maintain those fields.
Given/When/Then requires semantic understanding and belongs to AI review and
adopted correction, together with other meaning-dependent Hygiene observations
such as names, comments, documentation/examples and local readability.

Phase 6 now specifies the connected ownership and P6-HR01..04 acceptance:
runtime applies declared history/formatter rules before candidate validation/
review; one integrated AI review covers semantic correctness and applicable
Hygiene; adopted fixes use the common REVIEW_FIX/Submit loop; unadopted
nonblocking discoveries use AI document recording. Classification, execution
owner and adoption are separate decisions. Tool failures remain tool outcomes,
not automatic AI history-edit assignments. Routine mechanical maintenance is
not an AI Fix cycle; actual AI Hygiene fixes retain convergence/hard-limit rules.

This is a Phase 6 planning update, not an implementation claim or another
Phase 5.3 requirement. Historical descriptions below retain their context;
the current [Phase 6 contract](../../../phase/phase-6.md#review-and-maintenance-responsibilities--2026-10-12)
and design-note responsibility clarification govern this distinction.

### Original context

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

## Earlier interpretation: ten counted fixes, exclusions and bounded observation

The accounting interpretation below is superseded by the two-counter decision
that follows. Its bounded-observation decision remains current.

The user selected an initial hard limit of 10 counted Fix cycles. This is a
backstop for an abnormal/runaway loop during unattended overnight operation,
expected to be exceptional rather than a normal repair budget. It deliberately
limits counted repairs even while their trend appears favorable. A successful
result at the limit can still close; further counted repair enters handoff.

HYGIENE and minor bug corrections do not consume soft/hard cycle counts.
History-comment adjustments and GWT explanatory-comment insertion/repositioning
are included in hygiene when executable behavior is unchanged. Large structural
refactoring is not exempt merely for intending equivalent behavior. Minor bug
fixes include small compile corrections and local restoration of an already
agreed behavior; not every SIMPLE_LOGIC change is minor. Record the assessment
and rationale, without inventing an unchanged-content proof. Mixed substantive
and exempt work consumes one cycle.

Excluded fixes remain in the actual history and trend evaluation, with normal
applicable validation. Exclusion must not reset failure history or neutralize
stalled/diverging/AI-BLOCKED handling. Do not introduce a hidden total-attempt
limit that counts excluded work again. Count the current logical Fix loop across
chat/process continuations, not all old fixes accumulated over the project's life.

The user also accepted the present observation level because more exact data
collection has technical and cost limits. Use the current ConvergenceVector and
existing validation/provider facts; richer failure identities are useful when
already available but are not new mandatory instrumentation. Missing values stay
unknown. Expand bounded AI feedback about progress, remaining issues, temporary
scope growth and required decisions as operational experience warrants, rather
than requiring a more elaborate deterministic analyzer now. AI hints stay
advisory except the existing explicit BLOCKED signal.

This clarifies the earlier blanket hard-limit wording: the bound is on counted
repair work, not every hygiene/minor revision. Runtime accounting/guard work
belongs to Phase 5; semantic assessment/hints belong to the Phase 6 skill
connection; Phase 7 consumes those common paths. This document update does not
reclassify past receipts, change live counters or mark any implementation tested.

## Final clarification: convergence parameter and absolute automatic-cycle limit

The user identified that excluding hygiene/minor corrections from every limit
could allow endless automatic repair, then confirmed the separation: ordinary
repair count is a parameter of convergence judgment; the hard limit always stops
further automatic repair. This supersedes the earlier exclusion from both limits.

Keep two counts in the same logical Fix loop. Substantive fixes increment the
ordinary count. Hygiene/minor fixes do not, but their outcomes and repetition
still affect convergence judgment. Every dispatched Fix batch increments the
separate automatic-cycle count, regardless of classification. One batch with its
applicable validation/review is one cycle, not one per file, finding or test run.
Reserve that cycle at dispatch and retain it across interruption/restart.

The default absolute hard limit is 10 automatic cycles. A successful tenth result
may close; any further repair requires typed FIX_CYCLE_LIMIT handoff, including
hygiene/minor-only repair or favorable convergence/AI feedback. No automatic
reset, allowance renewal or relabeling bypass is permitted. Ordinary count and
soft threshold inform convergence rather than unconditionally stopping useful
work; all-cycle trend handling can stop earlier when warranted.

Phase 5 implements both counts and deterministic guard transitions, Phase 6
supplies bounded semantic assessments/hints, and Phase 7 uses the common policy.
Preserve active-loop history across chats/processes without charging unrelated
future loops. This documentation update changes no live workflow counters or
implementation and adds no expensive measurement prerequisite.


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

## Integration decision: evidence-confirmed issue progress and bounded reuse

The comparative-study section above is preserved from the user's GitHub journal
at commit `9c34ceba98d3810bc77a78907c9ffb4ba64f572c`. It records the investigation;
the following integration refines its proposed adoption. This update does not
independently re-audit Orca source or adopt its three-turn default.

Stable issue identity and request/result reconciliation strengthen convergence:
they distinguish actual resolution, recurring findings and newly discovered work.
Keep that state inside existing Workflow/Candidate/Evidence storage, with no
parallel integrity ledger or stage-as-commit lifecycle. Sources that cannot
supply cross-observation identity keep local finding IDs and unknown mapping;
missing identity is not a new prerequisite or reason to stop development.

The integration distinguishes FIXED claims from evidence-confirmed RESOLVED.
A claim remains unresolved until relevant fresh test/review evidence supports
closure. An issue omitted from a narrowed review is not resolved. DECLINED stays
unresolved; UNRESOLVED permits honest partial progress without a false FIXED or
BLOCKED report. Unknown/unrequested IDs, duplicates, missing entries and malformed
results are ordinary input-contract errors, not a reason to build tamper checks.
Preserve actual Candidate work when a structured report is invalid.

The zero-fix rule is a strong stall signal, not unconditional rejection of a
partially completed repair. Existing validation/trend evidence may show progress
inside one open issue. AI hints explain that evidence but cannot alone override
guard policy. With no supported progress, use STALLED handling rather than
blindly repeat the same request. AI BLOCKED stops immediately. The two-counter
contract remains: ordinary substantive repairs inform convergence; every repair
batch, including partial/hygiene/minor work, counts toward the absolute hard
limit of 10. New issue IDs or plan revisions do not renew the active allowance.

Issue counts describe known scoped findings and validated per-cycle transitions,
not all product defects. Do not count fixer claims as resolved or repeated
observations as new/reopened events. Missing correspondence stays unknown.
Collection remains bounded to available evidence; no extra measurement system,
per-issue test/review cycle or costly data collection is required.

Intermediate review may narrow under admitted policy while covering affected
boundaries; required SMOKE, Slice FOCUSED, final review and ADMISSION remain.
Resume reuses applicable completed evidence using existing Candidate, definition,
scope and relevant execution-input bindings, including overlay selection. A
restart or committing previously tested work alone does not invalidate it.
No TTL, hashes, full copies, unchanged-content certificates or stage commits are
introduced. Changed relevant inputs still invalidate corresponding evidence.

Phase 5 owns typed issue state, reconciliation, guard inputs and evidence reuse;
Phase 6 connects structured fixer/reviewer responses and bounded hints; Phase 7
exercises the same behavior across repository and overlay contexts. Local
acceptance cases 1–39 are retained and Orca-derived/refined cases become 40–49,
avoiding the GitHub draft's overlapping 33–38 numbers. This is a specification
update, not executed acceptance or a change to a live Goal/counter.


## Follow-up decision: CNCF Validation/Test Metadata ABI replaces suite registry

The Test Suite representation was simplified further. The normal source of truth is no longer a sm-workflow-managed suite definition/resource file. Executable specifications/test operations carry CNCF Validation/Test Metadata ABI annotations/properties, and sm-workflow constructs SMOKE/FOCUSED/ADMISSION/FULL/HEAVY selections by querying CNCF-resolved metadata.

Operation/scenario is the primary granularity, with class/specification metadata acting as defaults/shared feature context. One operation may participate in multiple purposes, allowing the same test program to serve SMOKE, ADMISSION and FULL without duplication.

FOCUSED becomes feature-driven rather than registry-driven. Slice planning selects feature identities; sm-workflow runs FOCUSED-tagged operations whose features match the admitted Slice selection. If the classification itself is insufficient, the Slice revises Test Architecture metadata explicitly rather than creating an ad-hoc runtime test list.

CNCF owns the portable semantic ABI and annotation/property merge/discovery semantics. sm-workflow owns runtime policy such as the one-minute ADMISSION and ten-minute FULL targets, execution receipts, freshness and warnings. cbd-support consumes the same ABI statically. This removes duplicated management data and makes metadata changes themselves visible Test Architecture changes.


## Producer phase and coordinated consumer plan

CNCF Phase 103 now owns the ABI/discovery producer work. sm-workflow Phase 5
drives and consumes it; Phase 6 edits source metadata/test architecture through
skills; Phase 7 applies the same queries across repository/overlay inputs.
This supersedes the earlier journal's registry/source-definition-file planning;
old discussion remains history. GitHub input revision:
`f9213defae3a76332101d9bbcdc8ae0d87edb77d`.

The current note removes residual registry wording from selection, receipts,
admission and improvement. Runtime receipts reference resolved operations,
metadata/ABI revisions and execution context. Query policy remains sm-workflow
configuration; it does not become a second test membership authority. Metadata
changes follow Candidate/review/admission, including coverage-reduction review.
Operation expected duration is not automatically aggregate query duration.

The locally refined Issue lifecycle, partial-progress rule, both counters and
absolute hard limit of 10 remain in force. Acceptance cases 1–49 are preserved
with metadata-dependent updates; ABI consumer cases are added as 50–54.

All three projects' coordination documents are updated here. cbd-support's
implementation remains assigned to Mac mini; its static-analysis/KPI completion
does not gate CNCF producer acceptance or sm-workflow's initial runtime slice.
Its runtime-observation comparisons consume bounded evidence references rather
than running tests itself or changing metadata automatically.


Mac mini's consumer review is incorporated in CNCF Phase 103 and cbd-support
Phase 14: explicit feature population, discovery completeness/unannotated entries,
operation/source revision, and comparable duration scope. Runtime handoff keeps
query/build/setup/operation measurements distinct and missing facts Unknown.
The shared ADMISSION 1 -> 0 declaration fixture exercises both consumers without
requiring fabricated runtime receipts or an implicit tested-feature denominator.


## Clarification: retain the management file as a Slice validation plan

The user corrected the blanket withdrawal of the management-file idea. Retain
it with a distinct responsibility: CNCF metadata declares test purpose/feature/
execution requirements; the Slice management file defines acceptance conditions,
metadata queries, supplemental Test Operation references and typed parameters;
execution Evidence records actual selected/executed inputs and results. The plan
must not copy metadata into a second declaration authority.

Normally select FOCUSED plus feature and supplement gaps with explicit refs,
without forcing one-off source metadata edits. Plan changes use normal admission;
execution requirements and final validation obligations remain. Evidence binds
plan revision and actual invocation parameters, deduplicating identical invocations
without collapsing intentionally different parameter cases. CBD Support separates
declared, planned and executed coverage and their deltas. This supersedes earlier
wording that could be read as prohibiting all Slice management files.

## Indicator purpose and developer-requested replanning — 2026-10-07

The developer clarified how to use the indicator output already planned for
sm-workflow: notice potential inefficiency, stagnation or divergence, then decide
whether to ask AI to reconstruct the development plan. Indicators provide a
reason to investigate and supporting material, not proof that the plan is wrong.

Examples include successful local fixes without practical acceptance progress,
new/reopened issues growing faster than resolutions, repeated validation cost
without further acceptance evidence, and unplanned dependency work. Interpret
only available evidence; missing relationships remain unknown. Bounded AI hints
can explain context without adding instrumentation, scans, tests or comparison
records.

The loop is observation -> developer judgment/request -> AI diagnosis and plan
proposal -> developer adoption -> updated plan execution -> later observation.
Reordering work or combining batches may be sufficient; scope reduction is not
assumed. When scope allocation changes, update current completion conditions and
validation planning consistently, preserve deferred-item identities/destinations
and actual history, and do not confuse historical requirements with current
completion obligations. Replanning does not reset counters or renew an unresolved
repair loop.

Phase 5's existing output supplies the observations. Phase 6's existing
human-selected improvement and thinking-mode/skill interface supports the request.
This is a purpose/usage clarification, not a new replanning engine, automatic
optimizer, release gate or stop rule. Existing typed guard decisions, explicit
AI BLOCKED and the absolute cycle limit remain unchanged. No active development
plan, Goal or implementation was changed by recording this decision.
