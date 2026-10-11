# Managed Test Suites and Verification Cost Guard

- Date: 2026-10-06
- Status: Design decision
- Scope: sm-workflow
- Related: Candidate-Admission Model, Deterministic Operations and Closing, Goal Phase Workflow

## Purpose

sm-workflow queries CNCF-resolved validation metadata to select and execute
project validation deterministically. Executable Specifications/test operations
own their declarations. AI designs test architecture and proposes source/model
changes; ordinary selection does not rediscover test lists through AI.

~~~text
Executable Specification/Test Operation + CNCF metadata
  -> CNCF discovery
  -> sm-workflow query/select/execute
  -> runtime evidence/cost/convergence guards
  -> human-selected improvement -> AI proposal -> normal review/admission
~~~

## Responsibility boundary

CNCF owns vocabulary, ABI, merge semantics and normalized discovery.
sm-workflow owns dynamic execution, evidence and guards. textus-cbd-support owns
static analysis, KPI and human-review presentation. AI changes test programs or
annotations/CML properties through normal Candidate/review/admission; runtime
observations never silently rewrite source metadata.

Producer plan: [CNCF Phase 103](https://github.com/asami/goldenport-cncf/blob/main/docs/phase/phase-103.md).
The [CNCF ABI proposal](https://github.com/asami/goldenport-cncf/blob/main/docs/notes/validation-test-metadata-abi-proposal.md)
is the shared vocabulary source. sm-workflow Phase 5 drives/consumes it, Phase 6
connects skills and Phase 7 extends use across repositories/overlays. cbd-support
implementation runs on Mac mini; the coordinated plan is maintained across all
three projects. Its dashboards/KPI completion does not gate initial runtime use.

## CNCF Validation/Test Metadata ABI consumer model

The management-file proposal is retained as a Slice validation plan, with responsibility separated from test declarations. CNCF metadata on Executable Specifications/Test Operations owns purpose, feature and execution-requirement declarations. The Slice plan owns acceptance conditions, metadata selection conditions, explicit supplementary operation references and typed execution parameters. Evidence records what was actually selected, executed and observed. The plan does not copy or override metadata as a second source of test declarations.

~~~text
Executable Specification/Test Operation
  + CNCF Validation Metadata
      purposes: Set[SMOKE|FOCUSED|ADMISSION|FULL|HEAVY]
      features
      expectedDuration?
      durationClass
      executionRequirements
        |
        v
CNCF resolved metadata discovery
        |
        v
Slice validation plan (acceptance + query + additional references/parameters)
  -> sm-workflow selection + deterministic execution + runtime evidence
~~~

The same operation may participate in several purposes. Purpose membership is explicit and does not imply strict subset nesting.

Class/specification-level metadata provides defaults/shared context; operation/scenario metadata is the primary selection/execution granularity. sm-workflow consumes CNCF's resolved merge semantics and MUST NOT independently reinterpret annotation inheritance.

No separate YAML/registry is required to add an ordinary test to SMOKE/ADMISSION/FULL/HEAVY: changing the source annotation/property changes the declared Test Architecture and is itself a reviewable Candidate change.

### Selection

Project-level queries:

- SMOKE = resolved operations containing SMOKE purpose.
- ADMISSION = resolved operations containing ADMISSION purpose.
- FULL = resolved operations containing FULL purpose.
- HEAVY = resolved operations containing HEAVY purpose and invoked only by explicit policy/request.

FOCUSED is Slice-scoped:

- Slice planning selects one or more feature identities;
- runtime selects resolved operations containing FOCUSED and matching the admitted Slice feature selection;
- the admitted Slice plan supplements that query with explicit Test Operation references and typed parameters where needed; a new reusable feature classification may still be proposed when appropriate.

Stable feature tags provide reusable focused grouping; the management file supplies the Slice-specific combination without duplicating those tags.

### Shared discovery and observation handoff

CNCF Phase 103 discovery supplies scope/completeness (COMPLETE/PARTIAL/UNKNOWN),
unannotated operations and diagnostics, scoped feature/operation identity and
metadata/source revisions. Required selection policy cannot silently treat a
partial inventory as a complete passing validation set. The Component/Slice's
explicit target feature population is separate from test-observed feature tags;
missing coverage denominators remain unknown rather than inferred.

For [cbd-support Phase 14](https://github.com/asami/textus-cbd-support/blob/main/docs/phase/phase-14.md),
expose bounded runtime evidence references alongside operation/artifact/revision,
metadata/query/overlay inputs, execution environment, measurement scope/unit,
outcome/timeout/retry/attempt and execution ID/provenance. Query elapsed time,
queue/build/setup time and operation/test-body time are not interchangeable.
If an adapter lacks operation-level timing, report that limitation rather than
allocating suite duration or adding a measurement-only run. Missing observations
remain Unknown; NotRun requires an explicit known execution disposition.

Static analysis/KPI and comparison of compatible runtime samples belong to
cbd-support on Mac mini. Its UI/KPI completion is not a runtime prerequisite.
The common first fixture removes a feature's sole ADMISSION declaration: retain
the 1 -> 0 metadata delta and runtime coverage-policy outcome without claiming
unprovided execution evidence.

### Runtime policy remains sm-workflow-owned

CNCF defines metadata semantics, not development policy. sm-workflow owns the engineering targets and guards:

- ADMISSION target <= 1 minute;
- FULL target <= 10 minutes;
- HEAVY may take hours;
- actual execution duration, result, warning and Candidate freshness are runtime Evidence.

Expected-duration declarations from the ABI may refine operation-level anomaly detection. Runtime observations never write themselves back into source annotations automatically.

### Metadata changes

Purpose/feature changes are Test Architecture changes. TEST_FIX/REVIEW_FIX MUST NOT silently remove ADMISSION/FOCUSED coverage merely to make validation pass or run faster. Such metadata changes remain normal Candidate changes subject to diff/review/admission and are visible to cbd-support static analysis.

### Operation binding

The discovered executable specification/test operation MUST ultimately bind to an admitted typed deterministic execution mechanism. Metadata MUST NOT introduce arbitrary executable/free-form argv. Existing deterministic-operation/provider rules remain authoritative.

## Purpose-specific suite construction and time budgets

Purpose defines the validation objective and time budget; it does not require a separate duplicate body of test code. AI SHOULD classify existing test operations with purpose/feature metadata and may reuse the same test in multiple purposes. The sets are independent and MUST NOT require a strict physical subset relation such as SMOKE subset FOCUSED subset ADMISSION subset FULL subset HEAVY.

### SMOKE

SMOKE optimizes for the cheapest useful rejection of an invalid Candidate. It contains compilation/type checks naturally provided by the ecosystem plus a very small representative runtime set. For Scala/sbt, test-source compilation gives broad structural/type coverage even when only a few tests are selected for execution.

### FOCUSED

FOCUSED validates a bounded changed area. Slice planning selects feature IDs;
runtime queries FOCUSED operations whose resolved features intersect that set.
Multiple selected features form a union, supplemented by the admitted plan's
explicit operation references. Deduplicate identical operation/parameter/context
invocations; deliberately different parameter cases remain distinct. Runtime
executes the plan deterministically rather than asking AI for a new test list.

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


## Project queries and Slice-focused validation

SMOKE, ADMISSION and FULL are fixed project-level purpose queries, not frozen
membership lists. HEAVY is a project query invoked only by explicit policy.
FOCUSED uses the Slice's admitted feature selection. The selected operations
are resolved from the current Candidate's metadata; changing metadata is a
reviewable Test Architecture change, not permission to silently shrink coverage.

Plan the combination before implementation. Normally use FOCUSED plus feature;
when insufficient, add explicit operation references/typed parameters to the
Slice plan rather than forcing metadata changes solely to express one Slice.
Revise source metadata when the test declaration itself needs correction.
TEST_FIX reruns SMOKE and the same admitted plan. A required unresolved reference
or uncovered acceptance condition is an explicit gap, never a silently skipped
operation or empty passing result. No ever-growing global FOCUSED list is needed.

### Slice validation plan management file

The management file is a version-controlled project/Slice planning resource read
through existing CNCF logical resource APIs. Exact format/path is an implementation
decision, not a new independent configuration resolver. Its conceptual contents:

~~~text
SliceValidationPlan
  id / revision / sliceRef
  acceptanceConditions
  metadataSelection          // normally FOCUSED + selected feature IDs
  additionalOperations       // stable Test Operation refs + typed parameters
  executionParameters        // bindings to declared operation input schemas
  acceptanceCoverageRefs     // which selections/references address each condition
~~~

Store declarations of this Slice's required combination, not copied purpose,
feature or execution-requirement values. Resolve references through the same
CNCF operation discovery/typed binding. Supplemental operations need not gain
FOCUSED membership merely because this plan includes them. Their declared
execution requirements still apply; the plan cannot weaken them or inject an
arbitrary executable/argv. Invalid references, parameters or conflicting bindings
produce ordinary typed input errors. Deliberate parameter variants are explicit
invocations; overlapping identical query/reference invocations execute once.

The plan is admitted with the Slice and evolves through normal plan/Candidate
review. A fixer cannot silently change conditions, drop required coverage or
substitute a cheaper operation. Explicit supplemental HEAVY work is permitted
when included in the admitted plan/policy; it is never silently added by runtime.
SMOKE, required final review and ADMISSION remain separate obligations.

Evidence references the plan ID/revision, query and explicit-reference selection
origins, actual resolved operations/parameters/context and results. A planned
operation is not execution evidence. Reuse follows existing applicability rules,
including relevant plan/parameter changes, without hashes, snapshots or TTLs.

## Metadata admission and revision

Source annotations or Cozy-owned CML properties are the declaration authority.
CNCF interprets supported ABI values and merge rules. sm-workflow validates the
query's required coverage and typed operation/provider binding using those
resolved facts, without parsing annotations or reimplementing merge semantics.
Metadata must not introduce executable/free-form argv.

Metadata/test changes follow normal Candidate/review/admission and retain source
and ABI revision provenance. Runtime query results are derived execution data,
not a second authoritative TestSuite registry. They may be referenced by receipts
without copying full source, logs or defining integrity hashes. Policy/Slice
configuration and runtime receipts use existing CNCF logical resource APIs;
source test metadata is not moved into a sm-workflow management file.

## Execution

Workflow policy requests a purpose and, for Slice validation, an admitted plan
containing query conditions, supplementary references and typed input bindings.
CNCF discovery supplies resolved metadata and typed operation references.
Conceptually the existing RunTestSuite operation executes that derived selection:

~~~text
RunTestSuite(workflowHandle, candidateRevision, purpose, validationPlanRef,
             resolvedSelectionRef) -> TestSuiteExecutionReceipt

TestSuiteExecutionReceipt
  executionId
  purpose
  selectedFeatures
  validationPlanRef / validationPlanRevision
  selectionOrigins / actualParameterBindings
  candidateRevision
  metadataAbiVersion
  metadataSourceRevision
  resolvedSelectionRef       // actual operation IDs/references and input context
  policyRevision
  startedAt / completedAt / duration
  outcome: PASSED | FAILED | ERROR | TIMEOUT
  providerExecutionEvidence
  resultReference?
~~~

A logical execution/selection ID is not a source suite-registry ID. Bind evidence
to actual operations, metadata/test revision and relevant execution inputs,
including overlay dependencies. Preserve bounded references through existing
Candidate/Evidence mechanisms. Do not duplicate full output or add hashes,
whole-file comparison or expiry certificates. Existing Candidate-Admission
applicability rules decide evidence reuse.

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

SMOKE is the cheapest purpose-selected validation set intended to reject an obviously invalid Candidate before focused validation or AI review. For Scala/sbt projects, the suite SHOULD exploit the fact that running even a small test normally requires test compilation: all test sources are compiled before the selected test executes. Thus a Scala SMOKE suite may combine full test-source compilation with only a very small representative runtime test set. This gives materially broader structural/type coverage than the number of executed tests alone suggests.

This is a project/provider property, not a generic assumption: other ecosystems may define SMOKE differently. AI designs the project-appropriate tests and SMOKE metadata; sm-workflow only executes the admitted definition.

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

TEST_FIX is triggered by deterministic TestSuite failure evidence. Its request contains the original requirement, current Candidate, failed selection and validation-plan identity/revision, execution evidence and bounded failure diagnostics. The AI decides whether the cause is implementation code, test code, TestSuite design, or a requirement/design gap. TEST_FIX MUST NOT mean merely making an assertion pass.

REVIEW_FIX is triggered by semantic ReviewEvidence/findings. Its request contains the original requirement, current Candidate, ReviewEvidence and required findings. It may address design, responsibility boundaries, overimplementation, missing requirements, naming/structure, or other semantic review findings.

Both are implementation-like semantic work for execution routing even though their triggers differ. REVIEW_FIX does not require a review-class worker merely because its input came from review. Concrete reasoning/provider selection remains governed by Phase 5/6 execution policy.

### Revalidation rule

A FIX result is only a new Candidate. The AI MUST NOT declare validation success or select the next Workflow state.

After TEST_FIX or REVIEW_FIX changes the Candidate, previously applicable validation evidence becomes stale according to normal Candidate-Admission freshness rules. sm-workflow restarts the required deterministic validation chain from SMOKE. A REVIEW_FIX therefore normally flows through SMOKE and FOCUSED before re-review. A TEST_FIX from FOCUSED also returns through SMOKE before FOCUSED is rerun.

The Workflow MUST NOT rerun only the previously failing individual test and treat that as closure evidence unless the admitted validation policy explicitly defines that as sufficient. This preserves the separation between semantic Candidate revision and deterministic acceptance evidence.

### Failure semantics

A TestSuite FAILED result requests TEST_FIX rather than an untyped generic repair. ERROR and TIMEOUT remain operational outcomes and MUST NOT automatically be converted into TEST_FIX unless policy/evidence establishes that Candidate semantic work is required. Duration warnings likewise do not trigger TEST_FIX; they enter the Human -> AI TestSuite improvement loop described above.

## Admission duration guard

An ordinary ADMISSION execution over 60 seconds emits a cost warning under
sm-workflow policy. The default FULL target is 10 minutes. These configurable
query-level targets are not CNCF ABI constants, test failures, timeouts,
authorization expiry or permission to skip validation.

Operation expectedDuration and durationClass come from CNCF metadata. Aggregate
query execution duration is measured separately: do not assume summing operation
estimates predicts compilation, shared setup or parallel runtime. A LONG_RUNNING
operation does not suppress the entire ADMISSION/FULL warning. A justified
query-level exception/threshold and rationale belong to existing sm-workflow
policy configuration, not a second test-membership file or an invented ABI field.
Operation-level comparisons use compatible operation evidence when available;
unavailable granularity stays unknown. Explicit long-running exceptions remain
observable against their declared expectations. No adaptive statistical optimizer
or extra run solely to collect duration is required.

## Warning model

Minimum warning evidence:

~~~text
TestSuiteDurationWarning
  warningId
  workflowHandle
  executionId
  resolvedSelectionRef
  metadataSourceRevision
  policyRevision
  purpose
  observedDuration
  warningThreshold
  durationClass
  rationale?
  candidateRevision
~~~

Warnings are durable diagnostic facts. They SHOULD be publishable through Phase 4 Service Bus integration and visible to Control Center. Their existence does not change semantic Workflow state unless a future explicit policy says otherwise. Do not put AI-generated diagnosis in the deterministic warning.

## Human -> AI improvement loop

When a human selects a warning for improvement, create a bounded semantic work request containing the current resolved Validation Metadata identity/revision, relevant receipts, warning facts, bounded test/build context, and the human instruction.

AI may return no change with rationale, metadata annotation/property changes, test implementation changes, or both. All changes use normal Candidate/review/admission and CNCF interpretation; there is no TestSuite registry registration step. Subsequent executions provide evidence before sm-workflow claims improvement. Coverage-reducing metadata changes remain explicit Test Architecture decisions.

~~~text
observation -> human judgment -> AI semantic improvement -> deterministic re-admission -> observation
~~~

not:

~~~text
observation -> autonomous self-modification
~~~

### Using indicators for developer-requested replanning (2026-10-07)

Existing cost, convergence and issue-movement outputs also help developers notice
that a development plan may be inefficient, stalled or expanding. They are
evidence for reconsidering the plan, not a verdict that the plan is wrong.
Successful tests or locally converging fixes alone do not establish progress
toward the current practical acceptance conditions.

Use available observations and bounded AI hints to explain, for example, fixes
without acceptance progress, new/reopened issues outpacing resolutions, repeated
validation cost without additional acceptance evidence, or growing unplanned
dependency work. Relate these observations to existing Slice validation plans,
issue records and execution evidence where the relationship is known. Missing
acceptance/dependency information remains unknown; this use does not require new
metrics, automatic semantic diagnosis, extra tests/scans or a comparison ledger.

The intended usage is:

1. sm-workflow presents the observed indicators, recent trend, evidence references
   and uncertainty to the developer.
2. The developer decides whether to ask AI to investigate and reconstruct the
   development plan, using the existing human-selected semantic-work path.
3. AI considers the current acceptance conditions, unresolved issues, recent
   changes and known dependencies. It proposes a rationale and bounded changes
   to work order, implementation batches or scope allocation, or explains why
   retaining the plan is appropriate.
4. The developer adopts the proposal or requests changes. The adopted plan updates
   the current completion conditions and relevant validation plan consistently;
   deferred work keeps its identity, actual state and destination. Historical
   obligations are not silently restored as current completion requirements.
5. sm-workflow continues under the adopted plan; subsequent observations support
   any claim that the change improved progress or cost.

Indicators alone do not dispatch AI, rewrite a plan, reduce scope or introduce
another automatic stop. Existing Fix Convergence Guard decisions, AI BLOCKED and
the absolute cycle limit still apply. Replanning preserves actual history, issue
states, counters and applicable evidence; it does not renew an unresolved loop's
allowance. Phase 5 supplies the existing observations; Phase 6 connects this use
to developer-requested AI work. No separate replanning engine or additional
initial-release gate is introduced.

## Interaction with Codex during implementation

Codex remains free to use narrowly scoped exploratory developer tests when useful, subject to harness policy. Those checks are not automatically official Workflow validation evidence. Official focused/admission/full validation is the admitted metadata-query/explicit-reference combination executed by sm-workflow. This prevents repeated just-in-case expansion of official validation scope.


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

Initial collection is deliberately limited to these values and existing
Candidate/provider/test evidence at reasonable cost. Do not make precise semantic
failure identity tracking, a new dependency analyzer, extra test executions or
expensive resource scans prerequisites for convergence assessment. Use failure
identities/sets when the provider already supplies them. Missing measurements
remain unknown, not zero or fabricated improvement. Resolve ambiguous meaning
with the AI assessment/rationale and later contract improvements rather than
expanding deterministic data collection now.

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

- HYGIENE: no logic change; formatting, harmless cleanup, naming/hygiene and equivalent changes. This includes history-comment adjustments and adding/repositioning GWT explanatory comments without changing executable test behavior. A major file split or structural refactoring is not exempt merely because its intended behavior is unchanged.
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
  minorBugFix: Boolean
  rationale
~~~

sm-workflow records this separately from deterministic file/resource counts. A semantic class that grows over successive Fix cycles is useful divergence evidence even when physical file count is flat.

`minorBugFix` identifies a small local correction of an already agreed behavior,
including TRIVIAL_COMPILE_FIX or a bounded SIMPLE_LOGIC bug correction. It does
not mean every SIMPLE_LOGIC change is exempt: a new feature, requirement/design
change, broad restructuring or coordinated cross-boundary correction is counted.
The rationale explains the classification using ordinary code/test evidence;
do not require a separate proof that all other files are unchanged. A mixed Fix
containing counted work consumes one cycle, not one count per file or finding.

### AI convergence self-assessment

Every Fix result SHOULD also provide a forward-looking/self-assessment for future use:

~~~text
AIConvergenceAssessment
  status: PROGRESSING | STALLED | REGRESSING | BLOCKED
  confidence: HIGH | MEDIUM | LOW
  rationale
~~~

Initially PROGRESSING/STALLED/REGRESSING are advisory evidence and MUST NOT override deterministic guard policy. They are retained so later operation can evaluate calibration and usefulness of AI self-assessment.

Use the bounded rationale to return useful hints: what improved, what remains,
why a temporary expansion was needed, or what decision/change would unblock work.
Prefer extending this feedback over adding costly deterministic measurements.
Hints can cite existing evidence but do not authorize new work or override the
current policy. More detailed structured feedback can be added after operational
experience; it is not an initial-completion prerequisite.

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

Do not treat a smaller file/line change alone as proof of closure. Evaluate the
available vector and validation outcomes at this initial level, retain uncertainty
and AI hints, and avoid imposing additional expensive collection merely to make
every convergence judgment exact.

### Cycle limits

Keep two separate counts within the current logical Slice/Candidate Fix loop:

- **Ordinary repair count**: counts substantive fixes and is a parameter of
  convergence judgment, alongside recent trends, validation results and bounded
  AI feedback. Its soft threshold is not an unconditional stop. HYGIENE and
  minor bug corrections do not increment this count, but their repeated results
  still affect convergence judgment; they are not invisible to the guard.
- **Automatic Fix cycle count**: counts every Fix cycle, including HYGIENE and
  minor bug corrections. The default **hard limit is 10 cycles**. It stops further
  automatic repair regardless of favorable trends, AI optimism or classification.

One cycle is one dispatched repair batch followed by its applicable validation
and review. Several findings/files repaired together consume one cycle, not one
per finding, file, test command or review invocation. Reserve the automatic cycle
when dispatching the batch, so failure or restart cannot grant a free extra Fix;
validation/review completes that cycle rather than incrementing it again.

TRIVIAL_COMPILE_FIX is normally minor; SIMPLE_LOGIC is ordinary-count-exempt only
when the actual correction meets the minor-bug definition above. A mixed batch
with substantive work increments both counts once. A hygiene/minor-only batch
increments only the automatic count. Keep every cycle's semantic classification,
rationale, revision and execution history. No exclusion bypasses the hard limit.

All cycles affect the existing trend policy and applicable validation. Repeated
unsuccessful minor corrections, oscillation, non-improvement or AI BLOCKED can
lead to warning/stop/handoff before the hard limit. Do not clear recent failure
history when an ordinary-count-exempt cycle occurs.

Retain both counts and history across chats, forks or process restarts. Do not
charge an unrelated later development loop for old fixes elsewhere in the
project, or reset an active unresolved loop merely by changing its task/name,
reclassifying a fix, or reaching the hard limit. Resumption after that stop needs
an explicit decision; the guard must not automatically renew its allowance.
This design update alone does not mutate historical records or live counters.

#### Explicit continuation approval and Step completion — 2026-10-11

Delivery owner: Phase 6 batch B, with connected acceptance in P6-TS05 and the
Phase 6 completion conditions. Implement missing runtime transitions and normal
client/skill wiring together. Phase 9 consumes this behavior for provider
switching; it does not own or delay initial reset delivery.

An explicit user approval to continue the stopped repair work resets both the
ordinary repair count and the automatic Fix cycle count to zero for that work.
Record the approval and prior consumption in the existing decision/history
record. The configured hard limit remains 10; the approved continuation starts
a new counting window under that limit. Approval covers the continued repair
work, not just one finding or batch. Do not request approval again for each
finding merely because the preceding window exhausted its allowance. One
approval opens one window, not repeated automatic renewals.

Keep cumulative execution history, unresolved issues and measured convergence
trends across the reset. Evaluate subsequent actual progress normally; the old
FIX_CYCLE_LIMIT disposition is resolved by approval and must not immediately
stop the new window using the preceding window's consumed count. Independent
authority/input gaps are not resolved merely by resetting a counter.

Successful Step completion with its accepted Step commit closes that Step's
repair loop. The next Step starts both counts at zero automatically, without a
separate continuation approval. Preserve the completed Step's history; do not
charge its consumption to the next Step or an independent Phase-close repair
loop. A checkpoint, WIP commit or repair commit within an unfinished Step does
not constitute Step completion and does not reset its active counts. A Phase
close loop already in progress likewise retains its own counts.

These are the reset boundaries. Provider switches, chat/process changes,
relabeling work, or merely hitting the limit do not reset either count.

- Below the ordinary soft threshold: continue when the guard finds no stop reason.
- At/above that threshold: CONVERGING or SLOW_CONVERGENCE may continue within the
  hard limit; STALLED should warn/escalate according to policy.
- DIVERGING: stop without waiting for the hard limit.
- AI BLOCKED: stop immediately.
- Hard limit: after the tenth automatic Fix cycle, evaluate its result normally.
  A successful candidate may close. If any further repair is needed, including
  hygiene or a minor bug correction, enter typed handoff with FIX_CYCLE_LIMIT;
  never dispatch an eleventh Fix in the same counting window automatically.
  Explicit user continuation approval opens the new window defined above.

Numeric limits remain versioned policy/configuration values. Ten is the initial
hard-limit default; the ordinary soft threshold/recent trend window belongs to
configured policy. Runtime or AI must not raise/reset the limit automatically.
The hard limit is an exceptional backstop against runaway unattended operation;
ordinary repair count helps judge convergence rather than declaring a fixed
number of useful repairs wrong.

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

The handoff SHOULD present the cycle history, ConvergenceVectors/deltas, unresolved/reopened issue identities where available, semantic change classes, validation outcomes and AI assessments. sm-workflow reports facts and policy outcome; it does not choose a new semantic strategy.

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

Neither example is judged by one metric alone. The guard evaluates the available
vector/trend, while the semantic class and hints remain AI-provided evidence.
The explicit hard bound covers every automatic Fix cycle. Ordinary-count
exclusions never bypass it; all cycles also remain subject to convergence judgment.

## Finding ledger and Fix reconciliation

Track concrete findings across Fix cycles using typed Workflow state. The useful
Orca behaviors are finding identity, structured Fix reconciliation, bounded
review narrowing and reuse on resume. They extend existing Candidate/Admission
and Evidence contracts; they do not introduce a second state store, a
stage-as-Git-commit protocol or an integrity ledger.

### FixIssue identity and lifecycle

~~~text
FixIssue
  id: FixIssueId
  source: TEST | REVIEW | CHECK | other admitted source
  sourceIdentity?       // producer/repository and test/check/finding identity
  summary
  evidenceReference
  status: OPEN | RESOLVED | DECLINED | REOPENED
  latestDisposition?   // fixer claim, separate from validated status
~~~

Assign an issue ID when a finding enters the loop and carry it in subsequent
requests/results. When available, retain the source's stable test/check/review
identity with its repository/provider scope, so identical names in different
projects do not collide. Reviewers receive relevant existing IDs and explicitly
reference them when reporting the same issue again. Runtime checks IDs and
transitions; it does not infer semantic equivalence through fuzzy text matching,
line numbers or content hashes.

Missing stable source identity is not a new execution blocker. Retain the finding
with its assigned local issue ID and evidence; cross-observation correspondence
may remain unknown. Do not invent a match, fabricate complete coverage, run extra
scans or require AI to build an identity system before work can continue. Existing
test/review coverage remains authoritative even when issue mapping is incomplete.

- OPEN: an unresolved finding.
- DECLINED: the fixer declined the requested correction, with rationale. It is
  still unresolved; this status is not an accepted waiver or scope change.
- RESOLVED: applicable fresh Test/Review evidence confirms resolution under the
  admitted validation policy. A fixer claim alone never enters this state.
- REOPENED: a previously RESOLVED issue is observed again with the same supported
  identity. Repeated observations while already unresolved update its evidence;
  they do not manufacture new issues or repeated reopen transitions.

Keep one current status per issue and retain its history across cycles/restarts.
OPEN, DECLINED and REOPENED all belong to the unresolved set. A policy-authorized
waiver/scope change uses the existing explicit decision path; the fixer cannot
remove a required finding by declining it. Zero unresolved tracked issues alone
does not establish acceptance when required validation/review remains incomplete.

### FixResult reconciliation and validation

TEST_FIX/REVIEW_FIX receives the explicit unresolved issue IDs assigned to that
repair batch, alongside the original requirement and bounded evidence. The
structured response accounts for each requested issue exactly once:

~~~text
FixIssueDisposition
  issueId
  disposition: FIXED | UNRESOLVED | DECLINED | BLOCKED
  rationale?

FixResult
  candidateRevision
  issueDispositions
  changeAssessment
  convergenceAssessment
~~~

UNRESOLVED permits honest partial progress without falsely claiming FIXED or
declaring BLOCKED. Its bounded rationale can explain remaining work and reference
existing progress evidence. It creates no authority to expand the task.

Runtime reconciles the response against the supplied request IDs. Unknown or
unrequested IDs, duplicate dispositions, missing requested entries and malformed
entries are input contradictions: return a typed result-contract error through
existing failure handling rather than applying an ambiguous ledger update or
accepting a partial success report. Preserve actual Candidate changes and valid
prior evidence; a bad report neither erases work nor grants validation success.
New findings can enter through ordinary test/review evidence intake, separately
from this response; the fixer cannot declare unsolicited issues FIXED.

FIXED is a recorded claim awaiting validation. Keep the issue unresolved until
applicable fresh evidence confirms it; do not increment resolvedIssues on that
claim. Use the normal admitted SMOKE/FOCUSED/semantic review chain, with evidence
identifying the covered issue or the admitted check/scope that establishes its
resolution. Runtime consumes typed outcomes rather than judging code semantics.
Multiple issues may share one valid test/review result; do not add a separate test
or review invocation per issue. Re-reporting an unconfirmed FIXED claim keeps the
issue unresolved; re-reporting a validated RESOLVED issue makes it REOPENED.

A finding's absence from a narrowed review or unrelated passing test is not
resolution. Validation coverage must actually address it. DECLINED/UNRESOLVED
retain unresolved state, while a valid BLOCKED disposition or AI BLOCKED
assessment immediately enters the existing error/decision path.

### Issue movement as convergence evidence

Add the following counts to ConvergenceVector where supported by the ledger:

~~~text
openIssues       // current unresolved set, including DECLINED and REOPENED
resolvedIssues   // transitions to evidence-confirmed RESOLVED in this cycle
newIssues        // newly identified issues first recorded in this cycle
reopenedIssues   // RESOLVED -> REOPENED transitions in this cycle
~~~

These are counts of known issue IDs within the current loop, not estimates of all
defects in the product. Retain bounded coverage/unknown information when sources
cannot provide reliable correspondence; do not treat missing metrics as zero.
Unchanged observations do not count again as new/resolved/reopened events.
An issue resolved and subsequently reopened in one cycle contributes to both
transition counts and ends in the unresolved set; net improvement is not inferred
from resolvedIssues alone. Combine issue movement with available validation
outcomes and existing surface/trend metrics. Smaller edits with recurring issues
are not automatically convergence.

### Zero-fix signal and partial progress

When requested unresolved issues remain and reconciliation reports zero FIXED
claims, treat that as strong evidence of possible STALLED behavior. It is not an
unconditional immediate stop based on that number alone. Likewise, FIXED claims
without confirmed resolution do not establish progress.

Evaluate the normal cycle's available validation results, issue movement, recent
trend and any admitted plan/validation-design revision. Supported partial
progress, such as fewer failing checks within one still-open issue, may justify
CONVERGING/SLOW_CONVERGENCE continuation under the existing guard policy. Bounded
AI hints explain that evidence but optimism alone cannot override the guard.
Cosmetic Candidate changes or DECLINED-only replies are not themselves progress.
If no supported progress is observable, classify STALLED and use configured
warning/decision handling instead of blindly resubmitting the same request.

Do not require extra diagnostics or test runs solely to establish progress more
precisely. Preserve uncertainty and apply the existing bounded trend policy.
AI BLOCKED still stops immediately, and the absolute hard limit of 10 automatic
Fix cycles includes all partial/hygiene/minor repairs regardless of issue count
or the ordinary repair count. Neither a new issue ID nor a plan revision resets
an active unresolved loop's history or allowance.

### Bounded intermediate review scope

An admitted review policy may narrow intermediate semantic re-evaluation to
still-open/recently-fixed findings and the affected change scope. A repair that
affects another boundary must use the relevant configured review coverage;
selection cannot depend solely on which reviewer previously reported a finding.
Retain all unresolved findings even when they are outside that intermediate
review's scope. Do not treat omission from a review response as resolution.

After Candidate modification, required SMOKE and the same admitted Slice FOCUSED
remain in force. Required final review and ADMISSION keep their configured
scope. Narrowing is a cost optimization within policy, never permission to skip
or silently reduce those obligations or create per-issue review cycles.

### Fresh evidence reuse on resume

Restart alone must not rerun completed Test/Review work. Reuse available evidence
when the existing Candidate-Admission contract establishes that it covers the
current Candidate, metadata/query policy or review definition revision and required scope, including
relevant execution inputs such as selected overlay dependencies. Different
dependency selections may invalidate evidence even when source revision matches.
Reuse completed applicable steps and execute only remaining/invalidated required
steps; an unfinished operation is not a successful receipt.

Restore issue state and both counters with that history. A process/chat restart
does not change the Candidate, reset the allowance, or reopen resolved issues by
itself. Committing previously tested work is not by itself an input change;
Git HEAD is not a substitute for the existing logical Candidate/input binding.
Use ordinary Git/context and typed execution facts. Do not add TTL expiry,
content hashes, whole-file copies, unchanged-content certificates or stage
commits as a prerequisite for reuse.

## Failure and retry

FAILED/ERROR/TIMEOUT are separate from duration warnings. FAILED means tests ran and validation was negative; ERROR means provider/infrastructure could not produce a valid result; TIMEOUT means explicit timeout policy was reached; duration warning means execution completed but cost exceeded expectation.

Retries MUST follow explicit operation/workflow retry policy. A slow suite MUST NOT be automatically rerun merely because it was slow. Repeated executions preserve separate receipts so unnecessary repetition can be diagnosed.

## Observability

Evidence MUST be sufficient to answer: which purpose/metadata revision ran; why it ran; duration; outcome; warning; repetition/reason; and whether later human/AI revision improved observed cost. cbd-support and Control Center may later consume these facts for KPI/trend review.

## Initial implementation slice

1. Drive CNCF Phase 103 and consume its versioned Validation/Test Metadata ABI and operation discovery.
2. Query project purposes and execute the Slice validation plan: acceptance conditions, FOCUSED plus feature union, supplemental operation references and typed parameters; report missing required coverage.
3. Bind discovered operations to typed execution and RunTestSuite receipts with metadata/operation/context references.
4. Measure duration and evaluate ADMISSION/FULL cost policy, including explicit long-running exceptions.
5. Persist/query/display bounded warnings and execution evidence through existing CNCF resources.
6. Connect metadata/test source changes through normal Candidate/review/admission and human-selected improvement.
7. metadata/test revision N replaced by N+1 -> old receipts remain attributable to N and new execution uses N+1.
8. validation metadata cannot inject arbitrary commands; execution remains typed/provider-bound.
9. Deliver FixIssue reconciliation, evidence-confirmed issue movement, review narrowing and resume reuse.
10. Provide shared metadata/evidence references for cbd-support on Mac mini, without implementing its static analysis/KPI here.
11. Verify the acceptance scenarios below.

## Acceptance scenarios

1. ADMISSION suite 20s -> PASSED, no duration warning.
2. ADMISSION NORMAL suite 61s -> PASSED plus duration warning; duration alone does not reject Admission.
3. ADMISSION suite fails in 5s -> FAILED; no conflation with duration warning.
4. justified LONG_RUNNING admission suite expected 180s, actual 120s -> no default 60s warning.
5. LONG_RUNNING exceeds declared policy threshold -> duration warning.
6. same suite executes twice -> two receipts; no hidden deduplication or invented retry.
7. metadata/test revision N replaced by N+1 -> old receipts remain attributable to N and new execution uses N+1.
8. validation metadata cannot inject arbitrary commands; execution remains typed/provider-bound.
9. human-selected warning can form bounded AI improvement request; warning alone does not trigger AI work.
10. code change after PASSED evidence follows existing Candidate-Admission freshness policy.
11. SMOKE failure -> TEST_FIX; successful fix restarts at SMOKE before FOCUSED.
12. FOCUSED failure -> TEST_FIX -> SMOKE -> FOCUSED; failing test alone is not sufficient closure evidence.
13. REVIEW findings -> REVIEW_FIX -> SMOKE -> FOCUSED -> re-review.
14. TEST_FIX may identify a test/suite/design problem instead of blindly modifying production code.
15. provider ERROR/TIMEOUT and duration warning do not automatically become TEST_FIX.
16. FULL completes within 10 minutes -> normal evidence with no FULL-budget warning.
17. FULL persistently exceeds 10 minutes -> cost warning/review signal without converting PASS to failure.
18. explicitly justified FULL exception above 10 minutes uses its declared expectation.
19. multi-hour exhaustive operations declare HEAVY membership and does not inherit ADMISSION/FULL time targets.
20. HEAVY is not automatically inserted into the normal implementation validation loop.
21. Slice selects one feature tag and executes matching FOCUSED operations without post-implementation AI test selection.
22. Slice selects two feature identities and executes the union of matching admitted FOCUSED operations.
23. Slice not covered by the normal FOCUSED feature query admits supplementary Test Operation references/parameters in its validation plan; metadata changes are needed only when declarations themselves need revision.
24. TEST_FIX reruns the same admitted Slice validation plan, including supplemental operations and parameters, and cannot silently broaden/reduce required coverage.
25. insufficient validation causes an explicit plan revision and, where declarations need correction, a metadata revision; no ad-hoc runtime test expansion.
26. gradually contracting vectors across several Fix cycles are allowed beyond the soft limit when policy classifies SLOW_CONVERGENCE.
27. sustained expansion of source/external-resource surface reaches DIVERGING and stops before hard limit.
28. AI BLOCKED stops automatic cycling immediately and enters typed error/decision handling.
29. favorable convergence cannot dispatch an eleventh automatic Fix under the default hard limit of 10, regardless of classification; a successful tenth result may complete normally.
30. three physical files with independent file-local changes may report STANDARD_LOGIC; file count alone does not force COMPLEX_LOGIC.
31. one coordinated semantic change spanning multiple files reports COMPLEX_LOGIC.
32. AI semantic class/self-assessment is recorded separately from deterministic ConvergenceVector and cannot override deterministic policy except explicit BLOCKED.
33. history comments, GWT comment insertion/repositioning and other nonstructural HYGIENE revisions remain in history but do not increment the ordinary repair count; each batch still increments the automatic cycle count and affects convergence judgment.
34. a minor compile/local bug correction increments only the automatic count; an ordinary SIMPLE_LOGIC feature change, major restructuring or mixed substantive/minor batch increments both counts once, regardless of findings/files/test-command count.
35. excluded corrections do not reset the recent trend; repeated failure/oscillation or AI BLOCKED can stop an excluded-only loop under the existing convergence policy.
36. existing metrics and bounded AI hints suffice for initial operation; unavailable additional failure/resource detail is unknown and does not trigger expensive scans, extra tests or fabricated zeros.
37. continuation in another chat/process retains both active-loop counts, including a dispatched interrupted cycle; a distinct later Slice/Fix loop does not inherit unrelated historical consumption.
38. ten consecutive hygiene/minor-only cycles with favorable AI feedback leave the ordinary count unchanged but stop further automatic repair with FIX_CYCLE_LIMIT; relabeling, chat switches and restart do not renew the allowance.
39. the ordinary repair count and all-cycle results participate in convergence judgment; crossing the soft threshold alone does not stop converging work below the absolute hard limit.
40. unknown/unrequested issue IDs, duplicate or missing dispositions and malformed FixResult entries produce a typed contract error without applying ambiguous issue updates or erasing actual Candidate changes.
41. FIXED alone leaves the issue unresolved and resolvedIssues unchanged; relevant fresh validation resolves it, and a later same-identity failure reopens it without creating a new issue.
42. DECLINED and UNRESOLVED remain in openIssues; valid BLOCKED stops immediately. Declining an issue is not a waiver.
43. requested open issues plus zero FIXED claims and no supported progress reaches STALLED handling; cosmetic edits and repeated identical requests do not create progress.
44. zero FIXED claims with supported partial progress may continue within policy and the absolute hard limit; favorable AI hints alone cannot override the guard.
45. narrowed intermediate review preserves unexamined issues and does not infer resolution from absence; affected boundaries and configured final review/SMOKE/FOCUSED/ADMISSION coverage remain required.
46. restart reuses applicable completed Test/Review evidence and runs only remaining/invalidated steps; changed suite/scope or relevant overlay inputs invalidate corresponding evidence even if source revision matches.
47. missing stable source identity preserves bounded findings with local IDs and unknown correspondence without blocking execution, fuzzy matching, extra scans or fabricated zero metrics; same names from different repositories remain distinct.
48. ledger metrics count validated transitions once; repeated observations do not inflate new/reopened/resolved counts, and a resolved-then-reopened issue ends unresolved rather than falsely signaling net progress.
49. restart or a commit of previously tested work alone does not invalidate applicable evidence or reset issue/cycle history; no TTL, hash, copy comparison or stage commit is required for reuse.

50. class/operation defaults and overrides are consumed through CNCF-resolved metadata, including scoped completeness/unannotated-operation diagnostics; partial discovery is not silently accepted as complete, and runtime does not reimplement merge or require a suite membership file.
51. FOCUSED with two admitted features and supplemental references executes identical operation/parameter/context combinations once; distinct admitted parameter cases remain distinct, and missing required coverage is a gap.
52. metadata changes that remove ADMISSION/FOCUSED membership remain visible architecture changes and require normal review/admission; a fixer cannot silently trade away coverage.
53. operation expected duration and measured query duration are distinct; a LONG_RUNNING operation alone does not suppress ADMISSION/FULL query warnings.
54. common metadata identity/revision plus bounded runtime references, measurement scope/unit and execution context are available to cbd-support; missing operation timing remains Unknown, query time is not allocated to operations, and no static-analysis/KPI service is required to execute a runtime query.

55. the Slice management file records acceptance conditions, metadata query, supplemental operation references and typed parameters without copying or changing test metadata.
56. an explicitly referenced operation outside the FOCUSED query executes with its declared requirements; it does not acquire a new purpose/feature tag merely through selection.
57. unresolved references, invalid parameters, conflicting bindings or attempts to weaken execution requirements produce typed input errors; no arbitrary command fallback or silent omission.
58. receipts retain plan revision, selection origins and actual operation/parameter/results; changing a relevant plan/input triggers applicable revalidation while a plan alone proves no execution.

## Non-goals

- AI selecting official tests on every Workflow execution.
- autonomous rewriting of slow tests.
- automatic warning suppression.
- adaptive/ML test selection.
- content hashing/integrity machinery.
- duplicate full test-log storage.
- global build-tool serialization.
- treating duration as correctness.
