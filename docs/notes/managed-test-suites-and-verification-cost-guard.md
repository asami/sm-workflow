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

- DEVELOPMENT / FOCUSED: bounded feedback during candidate construction/repair when required.
- ADMISSION: deterministic validation required to admit/close a candidate.
- FULL: broad regression validation required by explicit Workflow policy such as RepositorySync.

A suite is a logical validation asset, not synonymous with one shell command.

### Operation binding

TestSuiteDefinition MUST reference an admitted typed test operation/provider configuration. It MUST NOT carry arbitrary executable or free-form argv supplied by AI. Existing deterministic-operation rules remain in force. Provider implementation may use sbt, Gradle, Flutter, npm, etc. below this semantic model.

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

## Failure and retry

FAILED/ERROR/TIMEOUT are separate from duration warnings. FAILED means tests ran and validation was negative; ERROR means provider/infrastructure could not produce a valid result; TIMEOUT means explicit timeout policy was reached; duration warning means execution completed but cost exceeded expectation.

Retries MUST follow explicit operation/workflow retry policy. A slow suite MUST NOT be automatically rerun merely because it was slow. Repeated executions preserve separate receipts so unnecessary repetition can be diagnosed.

## Observability

Evidence MUST be sufficient to answer: which suite/revision ran; why it ran; duration; outcome; warning; repetition/reason; and whether later human/AI revision improved observed cost. cbd-support and Control Center may later consume these facts for KPI/trend review.

## Initial implementation slice

1. TestSuiteDefinition/TestSuitePurpose/TestSuiteRevision Value Objects.
2. Project resource loading and schema validation.
3. typed RunTestSuite deterministic operation/provider binding.
4. TestSuiteExecutionReceipt with measured duration.
5. ADMISSION default 60-second TestSuiteDurationWarning.
6. LONG_RUNNING + rationale + expected/threshold semantics.
7. warning persistence/query/presentation hook.
8. typed registration/revision path for AI-proposed definitions.
9. bounded Human -> AI review request using warning + receipts.
10. executable specifications.

## Executable specifications

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

## Non-goals

- AI selecting official tests on every Workflow execution.
- autonomous rewriting of slow tests.
- automatic warning suppression.
- adaptive/ML test selection.
- content hashing/integrity machinery.
- duplicate full test-log storage.
- global build-tool serialization.
- treating duration as correctness.