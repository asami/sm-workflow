# Managed Test Suites and Verification Cost Guard

- Date: 2026-10-06
- Status: Design decision
- Scope: sm-workflow
- Related: Candidate-Admission Model, Deterministic Operations and Closing, Goal Phase Workflow

## Purpose

sm-workflow owns deterministic execution, observation, and policy evaluation of project Test Suites used by Workflow validation/admission. AI owns semantic design and maintenance of those suites. Runtime MUST NOT ask AI to rediscover official test selection on every validation.

~~~text
AI analyzes project/testing needs
  -> proposes/revises managed TestSuite
  -> definition is admitted/registered
  -> sm-workflow selects suite by declared purpose
  -> deterministic provider executes it
  -> receipt/evidence is recorded
  -> deterministic cost/result policy is evaluated
  -> warning when observed behavior violates expectation
  -> Human decides whether improvement is needed
  -> AI reviews tests/suite
  -> revised definition is admitted/registered
~~~

This is a Human-in-the-Loop engineering-improvement loop, not an autonomous test optimizer.

## Responsibility boundary

### AI

AI MAY inspect project/build/test structure, propose suite composition, split/merge suites, improve slow tests, and propose expected duration plus rationale for legitimately long-running suites. AI returns a typed TestSuite proposal/revision. AI is not required for ordinary suite selection or execution.

### sm-workflow

sm-workflow MUST keep admitted TestSuite definitions as project development assets; select a suite from Workflow purpose/policy rather than ad-hoc AI choice; execute it through a typed deterministic operation/provider; capture result, duration, execution identity and definition revision; evaluate registered policy; and emit typed warnings without inventing remediation.

### Human

A human decides whether a warning deserves engineering work and may request AI review. A warning MUST NOT silently rewrite a suite, suppress itself, or authorize AI repair.

## TestSuite model

Minimum semantics:

~~~text
TestSuiteDefinition
  id: TestSuiteId
  purpose: TestSuitePurpose
  revision: TestSuiteRevision
  operation: TestOperationRef
  expectedDuration: Duration?
  warningThreshold: Duration?
  durationClass: NORMAL | LONG_RUNNING
  rationale: String?
~~~

Initial purposes SHOULD reflect Workflow needs rather than build-tool vocabulary:

- SMOKE: cheapest registered post-implementation validity check; normally seconds to tens of seconds.
- FOCUSED: bounded functional/regression validation for the changed area; normally tens of seconds where practical.
- ADMISSION: routine Candidate acceptance validation; target at or below 60 seconds by default.
- FULL: time-bounded comprehensive project regression; target at or below 10 minutes by default.
- HEAVY: completeness-oriented exhaustive/high-cost validation; hours are acceptable when justified.

A suite is a logical validation asset, not synonymous with one shell command.

### Operation binding

TestSuiteDefinition MUST reference an admitted typed test operation/provider configuration. It MUST NOT carry arbitrary executable or free-form argv supplied by AI. Existing deterministic-operation rules remain in force. Provider implementation may use sbt, Gradle, Flutter, npm, etc. below this semantic model.


## Purpose-specific suite construction and time budgets

Purpose defines the validation objective and time budget; it does not require a separate duplicate body of test code. AI SHOULD compose managed suites from existing tests/selectors and may reuse the same test in multiple purposes. The sets are independent and MUST NOT require a strict physical subset relation such as SMOKE subset FOCUSED subset ADMISSION subset FULL subset HEAVY.

### SMOKE

SMOKE optimizes for the cheapest useful rejection of an invalid Candidate. It contains compilation/type checks naturally provided by the ecosystem plus a very small representative runtime set. For Scala/sbt, test-source compilation gives broad structural/type coverage even when only a few tests are selected for execution.

### FOCUSED

FOCUSED validates a bounded changed area. A project MAY register multiple named focused suites such as goal-phase, repository-sync, admission, or execution-routing. Workflow/change classification selects among already admitted logical suite IDs; ordinary execution MUST NOT ask Codex to improvise individual test classes each time.

### ADMISSION

ADMISSION is the routine Candidate-acceptance suite. It SHOULD maximize useful regression confidence while remaining continuously affordable in the development loop. The default engineering target is 60 seconds or less. Important contracts, representative happy/failure paths, major invariants and historically fragile boundaries are candidates for inclusion.

ADMISSION is not merely a mechanically truncated FULL suite. AI designs it for routine acceptance value under the time budget. Persistent breach of the one-minute target is an improvement signal: optimize tests, revise selection, or move expensive coverage outward while preserving appropriate assurance.

### FULL

FULL means the strongest comprehensive regression that remains practical for ordinary project operation. The default engineering target is 10 minutes or less. Repository synchronization, release preparation and other larger boundaries may require it.

FULL does not mean literally every possible test regardless of cost. A test that causes the suite to persistently exceed the practical budget SHOULD be reviewed for optimization, representative substitution, or placement in HEAVY. A project MAY admit a justified FULL exception above 10 minutes, but the exception MUST be explicit and carry rationale/expected duration rather than silently redefining FULL.

### HEAVY

HEAVY is the outer validation class for completeness-oriented tests that should not distort normal development latency. Hours are acceptable. Examples include exhaustive combinations, broad integration/E2E matrices, long-running concurrency tests, large-data tests, performance/regression campaigns, multiple runtime/toolchain matrices, and other validation excluded from FULL primarily because of cost.

HEAVY prioritizes required completeness over the ordinary time budget. It is not part of the normal implementation -> SMOKE -> FOCUSED -> REVIEW loop. It runs only when explicit Workflow policy, a release/milestone policy, or human request requires it.

HEAVY still records duration and expected-duration evidence. It is exempt from the ordinary ADMISSION 60-second and FULL 10-minute targets, but a declared three-hour suite taking ten hours may still produce a cost anomaly warning under a future/general duration policy.

### Time-budget interpretation

The initial standard is therefore:

~~~text
SMOKE     seconds to tens of seconds
FOCUSED   tens of seconds where practical
ADMISSION target <= 1 minute
FULL      target <= 10 minutes
HEAVY     no ordinary upper budget; hours are acceptable
~~~

These are engineering targets, not correctness semantics. ADMISSION/FULL budget breach does not by itself turn a passing test into failure. Explicit exceptions are allowed when justified, but recurring excess should remain visible and reviewable rather than becoming accidental normality.


## Fixed project suites and Slice-focused validation

The purposes do not all have the same ownership/lifetime.

SMOKE, ADMISSION and FULL are fixed project-level managed suites. HEAVY is also project-level when the project defines it, but it is optional and explicitly invoked. FOCUSED is different: its concrete validation set is resolved for each development Slice.

~~~text
Project Test Suites
  SMOKE       fixed
  ADMISSION   fixed
  FULL        fixed
  HEAVY       fixed/optional

Slice Validation
  FOCUSED     resolved per Slice
~~~

This distinction is normative. sm-workflow MUST NOT model one ever-growing project-global FOCUSED suite and run it for every Slice.

### Standard focused feature suites

A project SHOULD maintain reusable standard focused suites for stable functional areas, for example conceptually:

~~~text
focused.goal-phase
focused.repository-sync
focused.candidate-admission
focused.execution-routing
focused.test-suite-management
~~~

These are admitted reusable project assets designed by AI and maintained as the feature/test architecture evolves.

During Slice planning, AI MUST select the focused validation profile as part of the Slice acceptance design. If one standard feature suite covers the Slice, the Slice records only that logical suite reference. Runtime then executes the admitted reference deterministically; it does not ask AI to rediscover test classes after implementation.

### Composition and Slice-specific focused suites

If one standard feature suite is insufficient, prefer composition of admitted standard suites. A Slice may conceptually declare:

~~~text
focusedValidation:
  suites:
    - focused.goal-phase
    - focused.candidate-admission
~~~

If composition still cannot express the required validation precisely, AI MAY propose a Slice-specific focused definition. The Slice-specific definition is admitted with the Slice plan and is then immutable for that Slice revision unless the plan itself is revised/admitted.

Where useful, a Slice-specific profile MAY compose standard suites plus a small explicit additional selector rather than duplicating their contents. The implementation representation should preserve logical references and avoid copying large test lists into every Slice.

### Planning-time rule

Focused validation is an acceptance-design decision, not a post-implementation improvisation:

~~~text
Slice planning
  -> determine changed semantic/functional area
  -> standard focused suite sufficient?
       yes -> reference admitted standard suite
       no  -> compose admitted standard suites
               -> still insufficient?
                    yes -> propose/admit Slice-specific focused definition
  -> admit Slice plan including FocusedValidationProfile
  -> implementation
~~~

After implementation the normal path is therefore deterministic:

~~~text
IMPLEMENTATION
  -> fixed project SMOKE
  -> Slice FocusedValidationProfile
  -> semantic REVIEW
  -> fixed project ADMISSION
  -> Closing
~~~

A TEST_FIX changes the Candidate, not the Slice validation design. It returns through project SMOKE and the same admitted Slice FocusedValidationProfile. If failure evidence demonstrates that the focused profile itself is wrong or insufficient, that is a validation-design gap and requires an explicit Slice plan/TestSuite revision rather than silent test expansion by the fixing AI.

### Promotion of repeated Slice-specific knowledge

Repeatedly similar Slice-specific focused definitions are a signal that a reusable feature suite is missing. Human/AI review may promote the recurring pattern into a standard focused feature suite. This follows the general sm-workflow progression from semantic discovery to admitted reusable deterministic knowledge.

## Registration and revision

AI-produced TestSuite content is a candidate, not runtime authority. Registration MUST validate schema/id/purpose, validate admitted operation/provider binding, reject arbitrary command injection, create a distinguishable definition revision, and preserve provenance so execution evidence identifies the exact definition revision.

Updating a suite creates a new admitted revision. Existing receipts remain bound to the revision actually used.

Project-owned definitions SHOULD live in the normal version-controlled sm-workflow Component resource area through CNCF Component resource APIs. Runtime receipts/warnings belong in runtime state/work storage. sm-workflow MUST NOT hard-code a physical .textus path. The exact serialization filename is intentionally non-normative; do not introduce a second configuration resolver.

## Execution

Workflow policy requests a logical purpose such as ADMISSION. sm-workflow resolves the current admitted definition and conceptually invokes:

~~~text
RunTestSuite(
  workflowHandle,
  candidateRevision,
  testSuiteId,
  testSuiteRevision
) -> TestSuiteExecutionReceipt
~~~

Minimum receipt:

~~~text
TestSuiteExecutionReceipt
  executionId
  testSuiteId
  testSuiteRevision
  purpose
  candidateRevision
  startedAt
  completedAt
  duration
  outcome: PASSED | FAILED | ERROR | TIMEOUT
  providerExecutionEvidence
  resultReference?
~~~

Do not duplicate complete test output in every Workflow record. Preserve bounded diagnostics/result references using existing evidence/observability mechanisms. PASSED evidence is fresh only for the candidate/revision and suite revision it validates; existing Candidate-Admission freshness rules remain authoritative.


## Candidate validation pipeline and FIX work

After an implementation result returns, sm-workflow SHOULD apply progressively more expensive validation before requesting semantic review:

~~~text
IMPLEMENTATION
  -> SMOKE
       fail -> TEST_FIX -> SMOKE
  -> FOCUSED
       fail -> TEST_FIX -> SMOKE -> FOCUSED
  -> REVIEW
       findings -> REVIEW_FIX -> SMOKE -> FOCUSED -> REVIEW
  -> ACCEPT
  -> ADMISSION validation
  -> Closing
~~~

### SMOKE purpose

SMOKE is the cheapest registered TestSuite intended to reject an obviously invalid Candidate before focused validation or AI review. For Scala/sbt projects, the suite SHOULD exploit the fact that running even a small test normally requires test compilation: all test sources are compiled before the selected test executes. Thus a Scala SMOKE suite may combine full test-source compilation with only a very small representative runtime test set. This gives materially broader structural/type coverage than the number of executed tests alone suggests.

This is a project/provider property, not a generic assumption: other ecosystems may define SMOKE differently. AI designs and registers the project-appropriate SMOKE suite; sm-workflow only executes the admitted definition.

The managed TestSuite purposes are SMOKE, FOCUSED, ADMISSION, FULL and HEAVY. DEVELOPMENT may remain an exploratory/harness concern unless a concrete Workflow requirement later justifies a separate managed purpose.

### FIX as semantic candidate revision

TEST_FIX and REVIEW_FIX are specializations of a common FIX concept. FIX means semantic work that revises an existing Candidate using newly obtained evidence. It is not a deterministic patch operation and has no validation authority.

~~~text
FIX
  trigger: TEST_FAILURE | REVIEW_FINDING
  originalRequirement
  currentCandidate
  triggerEvidence
  relevantPriorEvidence
~~~

TEST_FIX is triggered by deterministic TestSuite failure evidence. Its request contains the original requirement, current Candidate, failed suite identity/revision, execution evidence and bounded failure diagnostics. The AI decides whether the cause is implementation code, test code, TestSuite design, or a requirement/design gap. TEST_FIX MUST NOT mean merely making an assertion pass.

REVIEW_FIX is triggered by semantic ReviewEvidence/findings. Its request contains the original requirement, current Candidate, ReviewEvidence and required findings. It may address design, responsibility boundaries, overimplementation, missing requirements, naming/structure, or other semantic review findings.

Both are implementation-like semantic work for execution routing even though their triggers differ. REVIEW_FIX does not require a review-class worker merely because its input came from review. Concrete reasoning/provider selection remains governed by Phase 5/6 execution policy.

### Revalidation rule

A FIX result is only a new Candidate. The AI MUST NOT declare validation success or select the next Workflow state.

After TEST_FIX or REVIEW_FIX changes the Candidate, previously applicable validation evidence becomes stale according to normal Candidate-Admission freshness rules. sm-workflow restarts the required deterministic validation chain from SMOKE. A REVIEW_FIX therefore normally flows through SMOKE and FOCUSED before re-review. A TEST_FIX from FOCUSED also returns through SMOKE before FOCUSED is rerun.

The Workflow MUST NOT rerun only the previously failing individual test and treat that as closure evidence unless the admitted validation policy explicitly defines that as sufficient. This preserves the separation between semantic Candidate revision and deterministic acceptance evidence.

### Failure semantics

A TestSuite FAILED result requests TEST_FIX rather than an untyped generic repair. ERROR and TIMEOUT remain operational outcomes and MUST NOT automatically be converted into TEST_FIX unless policy/evidence establishes that Candidate semantic work is required. Duration warnings likewise do not trigger TEST_FIX; they enter the Human -> AI TestSuite improvement loop described above.

## Admission duration guard

The first operational guard is intentionally simple:

> An ADMISSION TestSuite whose completed execution exceeds 60 seconds SHOULD emit a warning unless its admitted definition explicitly declares a justified long-running expectation/policy.

The default 60-second value is sm-workflow application policy and SHOULD be configurable through the existing CNCF Component configuration mechanism. It is not a CNCF generic Workflow constant.

A duration warning is NOT a failed test, Admission rejection, timeout, permission to skip validation, permission for sm-workflow to modify tests, or permission for AI to auto-repair.

For NORMAL suites the policy is conceptually:

~~~text
threshold = suite.warningThreshold or defaultAdmissionWarningThreshold
if purpose == ADMISSION and duration > threshold:
    emit TestSuiteDurationWarning
~~~

For LONG_RUNNING suites, the definition MUST contain rationale plus expected duration or explicit warning threshold. LONG_RUNNING is not a permanent suppression bit. Execution is still measured and SHOULD warn when it materially exceeds the registered expectation/threshold.

Do not implement adaptive statistical thresholds initially. Historical trend analysis can be added later from accumulated evidence without making ordinary execution nondeterministic.

## Warning model

Minimum warning evidence:

~~~text
TestSuiteDurationWarning
  warningId
  workflowHandle
  executionId
  testSuiteId
  testSuiteRevision
  purpose
  observedDuration
  warningThreshold
  durationClass
  rationale?
  candidateRevision
~~~

Warnings are durable diagnostic facts. They SHOULD be publishable through Phase 4 Service Bus integration and visible to Control Center. Their existence does not change semantic Workflow state unless a future explicit policy says otherwise. Do not put AI-generated diagnosis in the deterministic warning.

## Human -> AI improvement loop

When a human selects a warning for improvement, create a bounded semantic work request containing the current TestSuite definition/revision, relevant receipts, warning facts, bounded test/build context, and the human instruction.

AI may return no change with rationale, a TestSuite revision, a test implementation change, or both. Normal candidate/review/admission applies to code changes. A proposed TestSuite revision separately passes TestSuite registration admission. Subsequent executions provide new evidence; sm-workflow MUST NOT claim improvement until observation supports it.

~~~text
observation -> human judgment -> AI semantic improvement -> deterministic re-admission -> observation
~~~

not:

~~~text
observation -> autonomous self-modification
~~~

## Interaction with Codex during implementation

Codex remains free to use narrowly scoped exploratory developer tests when useful, subject to harness policy. Those checks are not automatically official Workflow validation evidence. Official focused/admission/full validation is the registered TestSuite executed by sm-workflow. This prevents repeated just-in-case expansion of official validation scope.


## Fix Convergence Guard

TEST_FIX and REVIEW_FIX form bounded semantic revision loops. sm-workflow MUST observe convergence and MUST NOT allow an unbounded Fix -> Test/Review -> Fix cycle.

The guard combines four independent inputs:

1. deterministic change-surface and validation metrics;
2. AI-reported semantic change classification;
3. AI convergence self-assessment, initially advisory except for an explicit hard-stop signal;
4. bounded cycle-count policy.

### Deterministic ConvergenceVector

sm-workflow derives measurable values from Candidate revisions and validation evidence rather than asking AI to count them:

~~~text
ConvergenceVector
  sourceFilesTouched
  testFilesTouched
  managedResourcesTouched
  externalResourcesTouched
  linesAdded
  linesDeleted
  failedTests
~~~

External resources mean resources outside ordinary source/test files whose participation expands the effective change boundary, such as explicit dependency/repository resources, external service/API bindings, datastore/schema resources, generated/deployed artifacts, or equivalent typed resources known to the Workflow/provider. The implementation SHOULD use typed resource identities already available from CNCF rather than filesystem guessing.

Each Fix cycle records its vector and delta from the preceding Candidate. The vector is preserved as a vector; the initial implementation MUST NOT collapse it into an opaque scalar convergence score.

A single expansion does not imply divergence. sm-workflow evaluates a bounded recent trend so a sequence that expands modestly while locating a problem and then contracts may continue. Sustained expansion, persistent non-improvement, or oscillation is suspicious. The initial policy should use simple deterministic rules over a small recent window rather than statistical/ML anomaly detection.

### AI semantic change classification

Every TEST_FIX/REVIEW_FIX result SHOULD report the semantic depth of the actual revision using this closed ordinal vocabulary:

~~~text
HYGIENE
TRIVIAL_COMPILE_FIX
SIMPLE_LOGIC
STANDARD_LOGIC
COMPLEX_LOGIC
~~~

Semantics:

- HYGIENE: no logic change; formatting, harmless cleanup, naming/hygiene and equivalent changes.
- TRIVIAL_COMPILE_FIX: mechanically small compile correction without intended logic change, such as a missing import, spelling/identifier typo, or similarly local compile defect.
- SIMPLE_LOGIC: localized logic change that normally fits within one function/method or equivalent logical unit.
- STANDARD_LOGIC: ordinary logic change whose individual logic units remain within one file/module-local implementation boundary.
- COMPLEX_LOGIC: one integrated semantic/logic change whose correctness depends on coordinated logic across multiple files/components/boundaries.

Physical file count does NOT determine semantic class. If several files are changed but each contains an independent file-local STANDARD_LOGIC correction, the aggregate classification remains STANDARD_LOGIC rather than COMPLEX_LOGIC. COMPLEX_LOGIC requires cross-file semantic coupling for the same logical change.

The classes form an ordinal depth for trend evaluation only:

~~~text
HYGIENE < TRIVIAL_COMPILE_FIX < SIMPLE_LOGIC < STANDARD_LOGIC < COMPLEX_LOGIC
~~~

The ordinal MUST NOT be interpreted as a linear cost ratio.

The AI result should conceptually include:

~~~text
FixChangeAssessment
  changeClass
  affectedLogicalUnits
  crossFileLogic: Boolean
  rationale
~~~

sm-workflow records this separately from deterministic file/resource counts. A semantic class that grows over successive Fix cycles is useful divergence evidence even when physical file count is flat.

### AI convergence self-assessment

Every Fix result SHOULD also provide a forward-looking/self-assessment for future use:

~~~text
AIConvergenceAssessment
  status: PROGRESSING | STALLED | REGRESSING | BLOCKED
  confidence: HIGH | MEDIUM | LOW
  rationale
~~~

Initially PROGRESSING/STALLED/REGRESSING are advisory evidence and MUST NOT override deterministic guard policy. They are retained so later operation can evaluate calibration and usefulness of AI self-assessment.

BLOCKED is a hard-stop signal: the AI explicitly judges that continuing the same Fix loop is not appropriate or safe without an upstream decision/change. sm-workflow MUST stop automatic Fix cycling and enter the typed error/decision handling path. A future contract may refine explicit hard-stop reasons such as requirement gap, design gap or validation-design gap.

### Trend policy

The deterministic guard classifies recent behavior conceptually as:

~~~text
CONVERGING
SLOW_CONVERGENCE
STALLED
DIVERGING
~~~

CONVERGING means the observed change/validation surface is generally contracting. SLOW_CONVERGENCE allows several cycles when contraction is real but gradual. A small temporary increase MUST NOT immediately fail the loop.

STALLED means meaningful improvement is not observable over the configured recent window. DIVERGING means sustained expansion/oscillation or comparable deterministic evidence shows the loop moving away from closure. Exact initial thresholds/window sizes belong to versioned sm-workflow policy/configuration, not to AI prompts.

Examples of useful deterministic signals include consecutive growth of source/resource surface, failed-test count that does not improve, repeated reappearance of the same failure identities, and repeated oscillation between recent failure sets. Avoid sophisticated semantic inference in the guard.

### Cycle limits

Trend evaluation is combined with bounded cycle limits. The policy SHOULD provide a soft limit and a hard limit.

- Below soft limit: continue when no divergence/hard-stop is present.
- At/above soft limit: CONVERGING or SLOW_CONVERGENCE may continue; STALLED should warn/escalate according to policy.
- DIVERGING: stop without waiting for the hard limit.
- AI BLOCKED: stop immediately.
- Hard limit: stop automatic cycling regardless of apparently favorable trend.

The initial numeric limits MUST be configuration/policy values rather than hard-coded domain constants. Their purpose is to prevent infinite cycling, not to claim that a particular number of Fixes is inherently wrong.

### Error/Decision handoff

Guard termination enters existing typed failure/decision handling rather than inventing a new repair strategy:

~~~text
Fix cycle
  -> Convergence Guard
       CONTINUE
       or
       ERROR / DECISION HANDOFF
         reason:
           CONVERGENCE_DIVERGING
           CONVERGENCE_STALLED
           FIX_CYCLE_LIMIT
           AI_BLOCKED
~~~

The handoff SHOULD present the cycle history, ConvergenceVectors/deltas, semantic change classes, validation outcomes and AI assessments. sm-workflow reports facts and policy outcome; it does not choose a new semantic strategy.

### Convergence examples

A healthy sequence may look like:

~~~text
cycle   source files   external resources   semantic class
1       8              2                    COMPLEX_LOGIC
2       5              1                    STANDARD_LOGIC
3       2              0                    SIMPLE_LOGIC
4       1              0                    TRIVIAL_COMPILE_FIX
~~~

A suspicious/diverging sequence may look like:

~~~text
cycle   source files   external resources   semantic class
1       4              0                    SIMPLE_LOGIC
2       9              2                    STANDARD_LOGIC
3       16             5                    COMPLEX_LOGIC
~~~

Neither example is judged by one metric alone. The guard evaluates the vector/trend, while the semantic class remains AI-provided evidence and the cycle bound guarantees termination.


## Finding ledger and Fix reconciliation

A Fix loop MUST track the identity and lifecycle of concrete findings rather than only count rounds or rely on free-form AI summaries. This design is informed by Orca's review/fix implementation, but is expressed in sm-workflow Candidate/Admission terms.

### FixIssue ledger

Test/review findings that participate in a Fix cycle SHOULD be normalized to stable typed issue identities where the producing source can support them:

~~~text
FixIssue
  id: FixIssueId
  source: TEST | REVIEW | CHECK | other admitted source
  sourceIdentity
  title/summary
  evidenceReference
  status: OPEN | RESOLVED | DECLINED | REOPENED
~~~

The ledger is carried across Fix cycles. A later observation of the same logical issue refreshes/reopens the existing identity rather than blindly creating another unrelated entry. Resolved issues leave the open set but remain in history. The implementation MUST avoid contradictory simultaneous open/resolved records for the same issue identity.

Test failure identity should use stable test/check identity when available. Review finding identity requires a typed/stable finding identity from the review result; sm-workflow MUST NOT invent semantic identity by fuzzy text matching.

### FixResult reconciliation

TEST_FIX/REVIEW_FIX receives an explicit set of open FixIssue identities. The AI result SHOULD account for those identities with a typed disposition, conceptually:

~~~text
FixIssueDisposition
  issueId
  disposition: FIXED | DECLINED | BLOCKED
  rationale?

FixResult
  candidateRevision
  issueDispositions
  changeAssessment
  convergenceAssessment
~~~

sm-workflow deterministically reconciles the result against the request before accepting the new Candidate as a Fix result. Unknown issue IDs, duplicate contradictory dispositions, or malformed/unaccounted result entries are rejected or surfaced as degraded evidence according to explicit policy. AI cannot manufacture authority by claiming to have fixed an issue it was not handed.

A FIXED claim removes the issue from the carried open set provisionally; subsequent Test/Review may re-report the same issue, in which case it becomes REOPENED. A DECLINED issue remains open with the latest rationale. BLOCKED stops the automatic loop and enters typed error/decision handling.

The fixer's FIXED claim is not validation evidence. The normal SMOKE/FOCUSED/REVIEW chain determines whether the issue actually stays resolved.

### Ledger metrics in ConvergenceVector

The convergence evidence SHOULD additionally expose deterministic ledger counts:

~~~text
openIssues
resolvedIssues
newIssues
reopenedIssues
~~~

These augment rather than replace file/resource/test metrics. For example, decreasing file surface with increasing reopened issues is not automatically healthy convergence.

### Zero-fix stop

If a Fix request contains one or more open issues but the reconciled result fixes none and does not produce an admitted plan/validation-design revision, sm-workflow SHOULD classify the cycle as STALLED and stop or escalate according to Convergence Guard policy rather than repeatedly issue the same Fix request.

An explicit AI BLOCKED disposition stops immediately. A DECLINED-only result is not progress merely because the Candidate changed cosmetically.

### Narrowing re-evaluation scope

After a Fix, re-evaluation MAY narrow semantic reviewers/checks to sources relevant to still-open/recently-fixed findings when the admitted review policy supports it. This is an optimization, not an authority shortcut.

The required deterministic chain remains SMOKE plus the Slice FocusedValidationProfile after Candidate modification. Final acceptance still requires the configured review/admission scope. Narrowing MUST NOT silently reduce a required final review or ADMISSION TestSuite.

### Fresh evidence reuse on resume

Workflow restart/resume MUST NOT repeat expensive TestSuite/Review work merely because the process restarted when existing evidence is still fresh for the same Candidate revision, TestSuite/review definition revision, and required scope. Existing Candidate-Admission freshness rules decide reuse. A changed Candidate or changed required definition/scope stales the corresponding evidence.

This is the sm-workflow equivalent of stage-resume reuse without introducing a second stage/commit model.

## Failure and retry

FAILED/ERROR/TIMEOUT are separate from duration warnings. FAILED means tests ran and validation was negative; ERROR means provider/infrastructure could not produce a valid result; TIMEOUT means explicit timeout policy was reached; duration warning means execution completed but cost exceeded expectation.

Retries MUST follow explicit operation/workflow retry policy. A slow suite MUST NOT be automatically rerun merely because it was slow. Repeated executions preserve separate receipts so unnecessary repetition can be diagnosed.

## Observability

Evidence MUST be sufficient to answer: which suite/revision ran; why it ran; duration; outcome; warning; repetition/reason; and whether later human/AI revision improved observed cost. cbd-support and Control Center may later consume these facts for KPI/trend review.

## Initial implementation slice

1. TestSuiteDefinition/TestSuitePurpose/TestSuiteRevision Value Objects including fixed project SMOKE/ADMISSION/FULL, optional project HEAVY, and reusable FOCUSED feature suites.
2. Project resource loading and schema validation.
3. typed RunTestSuite deterministic operation/provider binding.
4. TestSuiteExecutionReceipt with measured duration.
5. ADMISSION default 60-second TestSuiteDurationWarning.
6. LONG_RUNNING + rationale + expected/threshold semantics.
7. warning persistence/query/presentation hook.
8. typed registration/revision path for AI-proposed definitions.
9. bounded Human -> AI review request using warning + receipts.
10. TEST_FIX/REVIEW_FIX typed semantic work requests and revalidation transitions.
11. Slice FocusedValidationProfile with standard reference/composition/Slice-specific definition and planning-time admission.
12. Fix Convergence Guard with deterministic ConvergenceVector, AI semantic change classification/self-assessment, trend policy, and bounded cycle limits.
13. FixIssue ledger and deterministic FixResult reconciliation, including zero-fix stop and reopened/new issue metrics.
14. bounded re-evaluation narrowing and fresh-evidence reuse on resume.
15. acceptance scenarios.

## Acceptance scenarios

1. ADMISSION suite 20s -> PASSED, no duration warning.
2. ADMISSION NORMAL suite 61s -> PASSED plus duration warning; duration alone does not reject Admission.
3. ADMISSION suite fails in 5s -> FAILED; no conflation with duration warning.
4. justified LONG_RUNNING admission suite expected 180s, actual 120s -> no default 60s warning.
5. LONG_RUNNING exceeds registered threshold -> duration warning.
6. same suite executes twice -> two receipts; no hidden deduplication or invented retry.
7. revision N replaced by N+1 -> old receipts remain bound to N; new execution binds to N+1.
8. AI proposal with arbitrary command injection -> registration rejected.
9. human-selected warning can form bounded AI improvement request; warning alone does not trigger AI work.
10. code change after PASSED evidence follows existing Candidate-Admission freshness policy.
11. SMOKE failure -> TEST_FIX; successful fix restarts at SMOKE before FOCUSED.
12. FOCUSED failure -> TEST_FIX -> SMOKE -> FOCUSED; failing test alone is not sufficient closure evidence.
13. REVIEW findings -> REVIEW_FIX -> SMOKE -> FOCUSED -> re-review.
14. TEST_FIX may identify a test/suite/design problem instead of blindly modifying production code.
15. provider ERROR/TIMEOUT and duration warning do not automatically become TEST_FIX.
16. FULL completes within 10 minutes -> normal evidence with no FULL-budget warning.
17. FULL persistently exceeds 10 minutes -> cost warning/review signal without converting PASS to failure.
18. explicitly justified FULL exception above 10 minutes uses its registered expectation.
19. multi-hour exhaustive validation is registered as HEAVY and does not inherit ADMISSION/FULL time targets.
20. HEAVY is not automatically inserted into the normal implementation validation loop.
21. Slice using one standard focused feature suite records/reference-executes it without post-implementation AI selection.
22. Slice requiring two standard focused areas composes both logical suite references.
23. Slice not covered by standard suites admits a Slice-specific focused definition during planning.
24. TEST_FIX reruns the same admitted Slice FocusedValidationProfile and cannot silently broaden it.
25. evidence that the focused profile itself is insufficient creates an explicit validation-design/plan revision rather than ad-hoc test expansion.
26. gradually contracting vectors across several Fix cycles are allowed beyond the soft limit when policy classifies SLOW_CONVERGENCE.
27. sustained expansion of source/external-resource surface reaches DIVERGING and stops before hard limit.
28. AI BLOCKED stops automatic cycling immediately and enters typed error/decision handling.
29. favorable convergence still stops at the configured hard cycle limit.
30. three physical files with independent file-local changes may report STANDARD_LOGIC; file count alone does not force COMPLEX_LOGIC.
31. one coordinated semantic change spanning multiple files reports COMPLEX_LOGIC.
32. AI semantic class/self-assessment is recorded separately from deterministic ConvergenceVector and cannot override deterministic policy except explicit BLOCKED.
33. FixResult claiming an unknown/unrequested issue ID is rejected/degraded by deterministic reconciliation.
34. FIXED issue disappears from open set but reappears as REOPENED when subsequent validation reports the same stable identity.
35. open issues plus zero FIXED dispositions produces STALLED/escalation rather than an identical automatic Fix loop.
36. DECLINED issues remain open with latest rationale; BLOCKED enters error/decision handling.
37. re-evaluation may narrow intermediate semantic reviewer scope but cannot bypass SMOKE/Slice FOCUSED or required final review/ADMISSION scope.
38. restart reuses fresh matching Test/Review evidence and does not rerun it solely because the process restarted.

## Non-goals

- AI selecting official tests on every Workflow execution.
- autonomous rewriting of slow tests.
- automatic warning suppression.
- adaptive/ML test selection.
- content hashing/integrity machinery.
- duplicate full test-log storage.
- global build-tool serialization.
- treating duration as correctness.