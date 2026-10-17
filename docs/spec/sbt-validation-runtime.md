# Normal SBT validation runtime

Status: current typed runtime contract. Historical actual188/193 qualify their
selected normal Test scenarios. The S6-01D6T2 TestOnly consumer integration is
implemented and previously focused-qualified in actual202B. The additive normal
Goal validation output below is implemented and focused-qualified, with its public
projection observed through ordinary retained-result Advances; installed workflow
acceptance remains open. Historical actual1100/1101 remain in the
[Phase 2 record](../phase/phase-2-executable-specification-checklist.md); they
qualify their historical adapter only.

## Host selection and owning UoW execution

RepositorySync and GoalPhase select `sm-workflow-repository-validation-N`
(ordinal 1–16) from their saved host validation policy. Public Advance and
semantic completion inputs cannot select an executable, task or directory.
`WorkflowManagedProcessExecution.repositoryValidationC` constructs protected
`sbt_test` or `sbt_test_only` intent from the trusted closed Check kind for the logical workspace and returns the typed
`SbtExecutionResult`. The DSL is a thin intent constructor; permission, service,
binding, workspace, limits and lifecycle checks occur in the interpreter using
the actual current UoW. Admission, direct ordinary sbt execution and typed
result return belong to that same UoW and its existing managed process
lifecycle. A generic ProcessExec is no typed-SBT fallback.

The public runtime V2 SBT plan fixes the normal absolute executable, selected
repository workspace, owner, Manage access, capability grant, environment and
finite limits. V3 combines public typed Git and SBT declarations with the
existing common Manage workspace for typed File effects. Ordinary bootstrap
decodes the plan; no private binding, resolver or mocked driver supplies it.
CNCF renders the fixed `--batch -Dsbt.server.autostart=false
-Dsbt.supershell=false -Dsbt.color=false test` argv, sets the actual native
checkout cwd and owns capture, timeout, cancellation and descendant termination.
SBT owns its native locks and legitimate children. A real nonzero exit stays a
failed receipt; no application main or Java/SBT JAR invocation replaces the
normal executable.

The owning Workflow saves intent before execution and its actual bounded
receipt afterward. Semantic results report only assigned work. Unrecorded
outcomes remain uncertain and neither reopening nor failed validation grants
automatic replay. Host assembly checks do not replace current-UoW checks.
The declaration controls invocation; arbitrary selected build code still
requires trusted host selection. It establishes no hostile-build containment.

Product execution uses no global SBT wrapper or scripted prerequisite.
Development validation follows the latest AGENTS policy separately: the
registered `cncf_command_runner` serializes independent top-level SBT through
the shared wrapper and waits for its released lock. Managed child SBT is
launched by the owning test UoW using normal tool caches and native locks.

## Public-Operation executable obligations

`ThreeWorkflowSbtOperationSpec` remains `DoNotDiscover` and is explicitly
selected through `runtimeTestOnly` with `smworkflow.spec.sbt`. Ordinary discovery
does not launch nested SBT. The selected property must be an absolute regular
executable. `runtimeTestOnly` prepares required normal runtime artifacts and
the current CAR; ordinary focused test/testOnly compilation does not. It does
not supply `smworkflow.spec.sbt`. Selected suites using
`ThreeWorkflowManagedRepositoryFixture` also require that explicit normal
executable property through `TypedSbtHostPlanFixture`, even when their outer
host already fixes `sbtExecutable`.

`TypedSbtHostPlanFixture` supplies only public JSON and deliberate selected
build files. Isolated success/failure/unknown use SBT-only V2 with ordinal 1.
Whole Repository and Goal cases directly compose `TypedGitHostPlanFixture`
with typed SBT V3, empty generic `programs`, fixed owner/grants and the selected
Goal paths/messages. They use real test-owned local/bare repositories,
SQLite/DataStoreSpace and production Start/Advance/Submit Operations. Fixture
setup stages the selected build before the initial commit. No seeded native
history, manual product receipt or fabricated success replaces execution.

The SBT 1.9.7 / Scala 2.12.18 build compiles and executes `ProductCheck` against
the actual `value.txt`. Its deliberate test-only main is product input, never
an executor. It prints the actual native cwd and appends its PID outside the
repository in `sbt-executions.txt`. A correct value passes; an incorrect value
raises a real assertion. Existing bounded capture and isolated saved-intent,
actual-result and uncertain-no-replay obligations remain intact.

The two enabled Repository scenarios must consume that actual output and exit:
success converges to Terminal/Completed; failure stays Wait/Active without push
or retry. After the original runtime closes, a fresh public Advance must retain
the same selected receipt, native revision/history and exactly one PID entry.

The enabled one-Step Goal starts from the actual clean committed build,
changes only its assigned input and submits only its semantic result. The
owning workflow runs StepCheck, supplies the actual receipt to separate review,
commits the independently authorized reviewed Step, and runs PhaseCheck.
Separate Phase review/effect evidence then invokes the installed Repository
Operations and their third actual check. After closing the original runtime,
fresh Goal and child Repository Advances must retain whole-Phase completion,
child convergence and receipt, both native revisions/histories and exactly
three distinct PID entries. DefinitionBinding is compared by persisted business
fields across runtimes; no closed core or overlapping Setup callback is reused.

These executable obligations retain their historical actual188B qualification;
test-owned review/Human/effect evidence establishes no genuine operator issuance.
The common `ThreeWorkflowManagedRepositoryFixture` consumers and
`RepositorySyncNativeMergeRepairContinuationSpec` now use normal typed Git/SBT
composition, with the generic Java validation placeholders and repair JVM probe
removed. Their bounded actual193 qualification covers 139 checks in 21 selected
suites; original installed Conflict/Repair/Goal acceptance and independent
Step/Phase acceptance remain required. The selected clean host composition
bypasses none of its required checks and does not establish those acceptances.
See [normal typed SBT validation](normal-typed-sbt-validation.md) for the current
connection and remaining Step/Phase/installed acceptance obligations.


## Actual TestOnly receipt and retained compatibility

The trusted validation Check kind is exactly `test` or `test-only`, defaulting
explicitly to Test only when the current saved Check omits `kind`. Present null,
Boolean, numeric or unknown kind fails. The thin adapter accepts only this
kind and the existing ordinal: no application class list, task expression,
configuration, executable, argv or generic process fallback is added. CNCF
owns any fixed host TestOnly classes and renders its normal `testOnly` command
under the same current owning UoW and managed lifecycle.

The CNCF fixed host environment must explicitly include ordinary build
dependencies such as Cozy, even when `sbtExecutable` is absolute. The child's
PATH is host-owned, never inherited from the caller or injected by the
application. Changing that environment does not rerun a retained intent/result
or make raw zero a pass.

For these selected fixture suites, the runtime-owned fixed SBT environment
supplies `JAVA_TOOL_OPTIONS` with the unquoted single option
`-Dsmworkflow.spec.sbt=/Users/asami/Library/Caches/Coursier/arc/https/github.com/sbt/sbt/releases/download/v2.0.9/sbt-2.0.9.zip/sbt/bin/sbt`.
This is the existing ordinary installed SBT script selected by the actual
Coursier entry; its path contains no whitespace. The host's `sbtExecutable`
remains `/Users/asami/Library/Application Support/Coursier/bin/sbt`. Direct JVM
parsing accepts a quoted property containing spaces, but the normal SBT shell
runner splits `JAVA_TOOL_OPTIONS` and appends the resulting tokens to Java argv,
retaining quote characters. That quoted form therefore fails ordinary SBT
launch before tests. The fixed property is a trusted host test prerequisite;
it exposes neither application argv nor mutable caller environment. The fixed
TestOnly classes, workspaces, grants and finite limits still govern the same
current-UoW execution.

Native product receipts project the actual `SbtExecutionResult` into optional
`sbt` facts. `kind` and `outcome` use the closed wire strings; `selection` retains
actual `testClasses`, `completedSuites` and positive `testsRun` only for verified
TestOnly success. `reason` holds the actual CNCF refusal name for
`unverified-tests`: `MissingSelection`, `IncompleteCapture`, `InvalidReport`,
`ZeroTests`, `SuiteMismatch` or `FailedTests`. Failed and incomplete typed facts
retain kind with neither selection nor reason. Raw termination, exit and exact
bounded strict UTF-8 stdout/stderr retain their existing meaning. Incomplete
raw capture remains uncertain and cannot fabricate a receipt.

A complete raw-zero TestOnly result without verified selection is durably
negative. Product gates require raw zero plus succeeded typed facts of the
exact selected kind; absent facts are compatible only with historical Test.
Goal/Repository selected identity, order and current identity/ordinal/kind
resolution remain required. A changed kind cannot reuse a saved Test result.
A retained unknown intent is not run again.

The canonical Check and Receipt companion codecs apply across current Goal
validation, Repository state/progress and both review payloads, including
ordinary host definition decoding. Missing `sbt` explicitly remains None and
is omitted on encoding; present null or malformed facts fail. No saved row or
unsupported schema is upgraded. Review evidence targets include actual typed
selection/count/outcome where present, while historical no-facts targets keep
their old shape and raw logs do not become evidence bindings.

These consumer codecs, predicates and dispatch changes are implemented and
previously focused-qualified by actual202B (71 checks). Existing seven normal
Test operations and their ProductCheck fixtures remain Test evidence.
Historical actual188/193 and producer actual200 keep their documented scopes;
producer tests do not establish installed TestOnly acceptance. The preserved
old host `phase6-command-main-2` has no TestOnly target. The fresh normal host
`phase6-testonly-command-1` fixes two genuine specification classes as its
TestOnly target, but its actual native validation failed: SBT exited 1 in
`cozyGenerate` because `cozy` could not be found in the configured SBT environment
PATH. The normally installed `cozy` resides in the Coursier bin directory, which
is absent from that fixed PATH. The fresh normal host
`phase6-testonly-command-2` is selected and configured with the installed
Coursier bin immediately after the selected JDK bin in its fixed SBT PATH.
Its actual native validation reached Cozy generation and the two selected
specification suites, then failed because the SBT JVM lacked the explicit
`smworkflow.spec.sbt` property required by their shared fixture, before fixture
runtime boot. This removes the earlier missing-Cozy cause without qualifying
TestOnly success. The fresh normal host `phase6-testonly-command-3` used the quoted
fixed property containing spaces. Its actual native validation failed at ordinary
SBT launch before tests because the shell runner split and appended that option.
The fresh normal host `phase6-testonly-command-4` is selected and configured with
the existing script path without whitespace in the fixed property above,
preserving the corrected PATH, the same two genuine classes and existing limits.
All three failed native executions, saved intent/results and staged guides remain
preserved. Retained-result reopening, genuine independent review and installed acceptance
remain required.
Later genuine selected specification suites, native progression, original
workflow/security/CBD obligations, independent Step/Phase review and release
remain required. Typed check success does not establish installed acceptance.

## Normal Goal validation output

The additive public output projection is implemented and focused-qualified;
two ordinary Advances have observed the retained failed validation result.
Normal `GoalPhaseAdvanceResult.validation` optionally exposes the actual selected
saved validation payload as `GoalPhaseNativeValidationView`. It carries
`sourceRevision`, `scopeIdentity`, the actual `candidate` head/tree, ordered
`selectedChecks` identity/kind, ordered actual `checks` and optional `outcome`.
A selected Step uses its Step identity as scope; whole-Phase validation uses
`phase:<phase identity>`. The projection does not select or execute checks.

Each `GoalPhaseNativeCheckReceipt` retains its exact bounded stdout/stderr,
termination and optional raw exit. Its optional `sbt` is
`WorkflowSbtValidationFacts`: actual `kind` (`test` or `test-only`), actual
`outcome` (`succeeded`, `failed`, `incomplete` or `unverified-tests`), optional
`selection` and optional `reason`. `WorkflowSbtTestSelection` carries the ordered
`testClasses`, integer `completedSuites` and Long `testsRun` from verified facts.
The same receipt projection serves actual Goal review output. It neither parses
logs nor recomputes success. All six rejection reasons above remain literal
typed facts; raw zero with `unverified-tests` remains a negative result without
a verified selection.

Historical receipt absence remains absent `sbt`; a historical Check without
kind retains only its current-codec Test default. It gains no TestOnly selection,
reason or new execution. Pending intent keeps absent `outcome` and its actual
completed receipt prefix, possibly empty. The view invents no receipt, failure
or success for an unresolved check.

Internal `GoalPhaseNativeValidation.advanceC` returns Wait with this projection
on both initial and retained paths. The owning
`GoalPhaseNativeWorkContinuation.continueAfterSemanticC` then calls
`continueReviewC`: when validation has passed and review is installed, it can
issue `Request` / `ReviewStep` within that same public check-running Advance.
The Request carries the genuine saved receipts in `work.review.checks` and its
host review context; a top-level `validation` view is not required on it.
Without an installed review, or with unfinished or failed validation, the
existing fallback can remain Wait with the actual validation. Another normal
Advance observes retained failure rather than rerunning it. Wait's actual
condition and message continue to govern that fallback.

A fresh explicitly selected public Advance can reopen the existing pending
ReviewStep through the early suspension branch, retaining the actual issued
review/context, candidate, check receipt and typed facts without executing checks
again. This is no mandatory extra transition before initial review, automatic
loop or independent review result. It asserts no instrumented launch counter.
Check success does not become an accepted Step or Phase; neither output grants
independent review, Human, effect or closure authority. The three failed native
executions remain history, and original workflow obligations still require their
separate qualification and acceptance.
