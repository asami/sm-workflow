# Normal typed SBT validation

Status: current connection contract. Actual validation188B passed 206 checks in
20 selected suites, including all seven normal SBT cases, the catalog,
DirectPreview and the corrected preparation/null-presence matrix. Normal and
lost-acknowledgement Goal completion retain their actual results over repeated
same-runtime and fresh-runtime reads without another completion or check.
Actual193 passed 139 checks in 21 selected suites, qualifying the common
fixture and repair-negative migration across all 18 concrete shared-fixture
consumers (9 direct, 8 via `NativeHostPreparationManagedFixture`, 1 via
`NativeHostPreparationOperationFixture`) plus 3 generated ABI/completion/retention
guards. This is scoped normal-route qualification; Step and Phase acceptance
remain open.

`WorkflowManagedProcessExecution.repositoryValidationC(core, ordinal, kind)` accepts
only ordinals 1–16 and constructs the logical
`SbtTarget(WorkspaceId("sm-workflow-repository-validation-" + ordinal))`.
Its ProcedureActionCall composes protected `sbt_compile`, `sbt_test` or
`sbt_test_only` for the selected Compile, Test or TestOnly kind as thin typed
intent; the omitted kind remains Test.
Constructing that intent performs no permission, service, binding or launch
check. The interpreter checks the actual current UoW context, admits and
executes the selected operation through the existing managed process lifecycle,
and returns `SbtExecutionResult` through that same UoW. The application supplies
no ProcessExecutionRequest, executable, argv, task string, cwd or environment.
Generic SBT-tagged ProcessExec cannot substitute for this typed boundary.

GoalPhase and RepositorySync pass the actual `.processResult` to their existing
durable receipt conversion. Selected check identity, saved intent,
candidate/policy preconditions, uncertainty and no automatic replay remain
unchanged. Review acceptance and effect authority remain separate from semantic
implementation results and check receipts.

The public runtime V2 host JSON selects typed SBT-only declarations for isolated
cases. V3 composes typed Git and SBT declarations for the owning workflows.
Both fix the component owner, workspace roots, same-owner Manage access,
independent capability grants, a real absolute normal `sbtExecutable`, finite
limits and environment. CNCF owns binding, command rendering and terminal
interpretation. The exact vector after the executable is
`--batch -Dsbt.server.autostart=false -Dsbt.supershell=false -Dsbt.color=false test`.
SBT owns its native locks and legitimate tool/JVM children. Product execution
requires neither a global wrapper nor scripted, Java/SBT JAR fallback, custom
executor main, isolated caches or permission-symlink workarounds.

Development validation follows the latest AGENTS policy: the registered
`cncf_command_runner` uses the machine's shared serial wrapper for independent
top-level SBT invocations and releases its lock before the next invocation.
That development scheduling rule adds no product runtime prerequisite.
`runtimeTestOnly` prepares official Cozy runtime artifacts and packages the
current CAR before its supplied Test/testOnly selection. Ordinary test/testOnly
compilation performs neither preparation. Existing shared-fixture fork=false
and parallelExecution=false settings remain.

`TypedSbtHostPlanFixture` constructs public host JSON and the selected build
without executing a tool. Its reusable `normalExecutable` resolves only
`smworkflow.spec.sbt` to an absolute regular executable realpath, with no guessed
fallback. The three isolated SBT cases use SBT-only V2 and ordinal 1.
The common Repository/Goal/Repair fixture now constructs a clean Git plan
directly with `TypedGitHostPlanFixture.plan`, the actual test-owned repository
and bare upstream, exact conflict paths, selected Goal paths and Step messages,
then composes public V3 SBT declarations for the distinct required ordinals.
Combined `programs` is empty; typed File use shares the existing Git Manage
workspace. Ordinary runtime bootstrap decodes these public plans.
The SBT environment retains JAVA_HOME, HOME and PATH with JDK bin. The execution
limit defaults to 180000 ms; the closed repair timeout example selects 90000 ms.
Other limits remain 10000 ms launch, 1000 ms termination grace, 131072 stdout
bytes and 16384 stderr bytes. The existing SBT suite retains its independently
selected success/failure inputs and clean typed composition.

The SBT 1.9.7 / Scala 2.12.18 selected build compiles and executes `ProductCheck`
against `value.txt`. This deliberate test-owned product main prints its actual
native checkout cwd, fails on an incorrect value and records its external PID
in `sbt-executions.txt`. It is product-test input, never a product executor.
Common setup prepares the five fixed build paths only when `build.sbt` is
absent, before the original first setup/commit. Those paths are staged with
literal pathspecs immediately before that existing commit, so initial push and
clone retain the real build without adding a commit or changing setup indices.
Plan construction and fresh-runtime reopen never rewrite build inputs.

Repair setup specializes that tracked ProductCheck before the initial commit.
The actual resolved shared line passes the original check. The actual repaired
shared line selects only the closed connected/failed/indeterminate/intent-only
mode: connected passes; failed raises a real assertion and exits 1; indeterminate
sleeps 180000 ms under the 90000 ms managed lifecycle limit and yields an
indeterminate receipt with no exit code; intent-only prints and flushes an actual
NUL before returning normally, so bounded receipt conversion refuses it and
retains intent without result. Each negative branch creates its actual PID
marker exclusively. Later Advance must Wait without changing either PID marker.
No check receipt, native intent or output is supplied by the fixture.

The executable obligations are:

- Isolated success/failure first commit selected intent in a managed Operation,
  then execute typed Test once from a fresh call and save the typed outcome,
  actual receipt, original capture bytes/counts/truncation, termination, elapsed
  time and safe identity. A fresh runtime rereads identical evidence and the
  one-entry PID marker. A saved intent without a result stays uncertain without
  launching a process.
- Repository success and failure now execute through public Start/Advance with
  required product validation. Success reaches Terminal/Completed; an actual
  nonzero product test remains Wait/Active. After the original runtime closes,
  fresh Advance must retain the selected receipt, native revision/history and
  exactly one PID entry, including the failed receipt without retry.
- Both one-Step Goal episodes perform the actual implementation edit, StepCheck,
  separately evidenced review and Step commit, PhaseCheck, Phase review and
  separately authorized installed RepositoryCheck. One episode completes
  normally; the other loses the first `goal-repository-completed`
  acknowledgement after the actual native completion append. That public call
  must report failure while the owning Goal is already Completed. A normal
  public retry in the same runtime must return its actual typed Terminal.
  Both episodes repeat public Goal Advance at least twice in that runtime.
  Repeated and fresh Goal and child
  Repository Advances must retain whole-Phase completion, child convergence and
  receipt, native revisions/histories and exactly three distinct PID entries,
  with no fourth launch or second completion acknowledgement. The retained
  original Step commit and current repository tips must agree throughout.
  Native business fields are compared across runtimes
  without requiring DefinitionBinding object identity. Closed cores and
  overlapping mutable Setup callbacks are never reused.

All seven cases passed actual188B: three isolated, two whole-Repository and two
whole-Goal cases. Each Goal retained exactly three distinct external ProductCheck
PID entries after same-runtime and fresh-runtime result reads, including the
lost post-append acknowledgement case.
Fixture review/Human/effect evidence does not establish genuine installed
operator acceptance. The common fixture's generic Java validation definitions
and repair JVM probe are removed. Actual193 qualified its original
Repository/Goal/Repair scenarios across all 18 concrete shared-fixture consumers:
9 direct, 8 via `NativeHostPreparationManagedFixture` and 1 via
`NativeHostPreparationOperationFixture`. This includes transitive preparation
and Git-only consumers, alongside SBT consumers. With the generated ABI,
completion and retention guards, all 21 selected suites passed 139 checks.
That bounded result does not establish original installed-task acceptance.
The two File Conflict/ConflictLiteral negatives explicitly select Git-only
plans on initial boot and physical reopen. Their required SBT policy remains
required: missing permission retains denial/Wait and no SBT PID marker.
The five TypedGit-only cases likewise execute no unsolicited SBT despite the
tracked build precondition. These intentional negatives grant no broader
product-test or whole-workflow acceptance.

The selected installed host `phase6-command-main-2` now replaces its residual
generic launch definitions with public strict V3 typed Git declarations. Its
`sbtPrograms` is empty: the old Git diff document check is not an SBT product
test. Required Step/Phase/Repository validation policy remains required. The
parent must select a truthful normal SBT build/test target before actual workflow
progression; this configuration supports passive preparation only and supplies
no actual SBT result or installed acceptance.

All nine original product main routes are absent from source and the actual
packaged JAR. Original full workflow/installed acceptance, Step review, the
sole full Phase review and local release remain required. Aggregate full-suite
validation remains owned by Phase 6.3. Historical actual1100/1101 belong to the
[Phase 2 record](../phase/phase-2-executable-specification-checklist.md), not to
current typed-SBT acceptance. See the
[runtime contract](sbt-validation-runtime.md).


## Explicit typed validation consumer (focused qualification accepted)

The S6-01D6T2 consumer change introduced the closed trusted Check kinds `test`
and `test-only`; P61-COMMAND-A adds `compile` to the existing logical
`sm-workflow-repository-validation-N`
binding. The existing ordinal and owning UoW still select the host program.
`compile` dispatches `sbt_compile`; `test` dispatches `sbt_test`; `test-only`
dispatches `sbt_test_only`. All three compose and return through the actual
owning UoW. CNCF owns
fixed TestOnly classes from the trusted host declaration, command construction,
permission, execution and selection verification. Application calls carry no
class names, suite expressions, configuration or arbitrary tasks.

A product Receipt can now contain `sbt` facts with `kind`, `outcome`, `selection`
and `reason`. Outcomes are `succeeded`, `failed`, `incomplete` and
`unverified-tests`. Successful TestOnly facts retain the actual bounded exact
`testClasses`, `completedSuites` equal to their count, and positive `testsRun`.
Raw exit zero with absent selection or any CNCF selection refusal is retained
as `unverified-tests`, with its actual refusal reason and unchanged raw capture.
It cannot open Review or satisfy completion. No application stdout parser or
claimed success supplies these facts.

Compile receipts require an actual `SbtExecutionResult` whose operation is
Compile. Test and TestOnly likewise require their exact operation; no selected
kind can accept another operation's result or receipt. Compile success requires
the actual zero exit and succeeded outcome, with no test count, selection or
reason. Nonzero and incomplete Compile results retain their negative outcome,
raw stdout/stderr and exit semantics. Compile never accepts TestOnly selection
metadata or persisted `unverified-tests`, selection or reason facts.

Current-schema records explicitly read absent Check `kind` as Test and absent
Receipt `sbt` as no typed facts. Test encoding preserves the historical absent
kind shape; absent `sbt` remains omitted. Present null, mistyped or unknown kind,
and present null or malformed `sbt`, fail decoding. These rules apply through
Goal validation, Repository validation state and native progress, Goal review
work, Repository review Request and the existing host definition. Unsupported
older schema versions remain unsupported. Existing saved records are not
rewritten, migrated or promoted from Test to Compile or TestOnly.

Both native workflows compare the selected identity and kind against actual
receipts; retained selection also resolves the original ordinal in the current
policy. A legacy receipt without typed facts can satisfy only Test. Exact
positive TestOnly facts are required even at raw zero; selected Compile requires
matching actual Compile facts. Retained results and
unknown intents are read without replay. Review Context and Request retain
complete typed receipts; evidence targets project typed operation, outcome,
classes and counts without copying raw logs or policy bodies. All-None review
targets retain their historical shape.

Parent accepted P61-COMMAND A-2: 2 selected suites and 17 passing tests, with
SBT/wrapper exit 0 and the shared serial lock released. This qualifies the
focused consumer/codec behavior. Parent also accepted A2-RUNTIME-1: 1 suite and
2 actual normal generator/compiler scenarios, with SBT/wrapper exit 0 and the
shared lock released, as scoped below. The seven actual normal Test episodes
qualified by actual188 and the 18 shared-fixture consumers qualified by
actual193 retain their original scope. Actual200 qualified the CNCF TestOnly
producer (nine selected suites, 111 passing checks); it does not establish this
consumer or an installed TestOnly workflow. The selected installed host still
has no truthful SBT declarations. A later trusted host binding must select the
genuine current `NativeHostPreparationOperationSpec` and
`NativeHostRepositoryStartInputSpec`, then return actual nonzero selected-suite
facts to the same ordinary installed workflow. That configuration and native
progression are not performed here. Original installed workflow, security/CBD,
independent review, Step/Phase and release obligations remain open.

## Normal Compile and configured source generation (runtime qualification accepted)

P61-COMMAND-A2 adds two isolated normal Compile scenarios to
`ThreeWorkflowSbtOperationSpec`. The public host fixture accepts a final typed
Compile or Test operation parameter, preserving Test as its default and the
existing Test declarations. Only the two new scenarios select Compile through
SBT-only V2 authority. Each first persists its Compile intent in a completed
managed Setup Operation, then a fresh owning call reads that intent and invokes
`repositoryValidationC(core, 1, Kind.Compile)`, converts the actual result with
`sbtReceiptC` and persists the existing complete result evidence.

The isolated SBT 1.9.7 / Scala 2.12.18 build configures ordinary
`Compile / sourceGenerators` and selects `target/selected-generated` as
`Compile / sourceManaged`. SBT writes `CompileGenerated.scala`, emits
`COMPILE_GENERATOR_ENTERED` with the actual repository cwd and appends its
external PID to `compile-generations.txt`. A source without a main references
the generated value. The success input generates `val value: Int = 42`; the
failure input assigns a String to Int, requiring a real compiler type error.

The authored cases require actual typed success or failure, exit/termination,
bounded untruncated captures and byte counts, selected generated content and
exactly one external generator PID. Success requires the generated and consumer
classes and passes only the Compile product gate. Failure requires a nonzero
compiler exit, the type mismatch diagnostic, no consumer class and a closed
product gate. Both receipts retain Compile facts without selection or reason.
Fresh-runtime reads must preserve the exact durable intent and result without
adding a generator PID entry.

Parent accepted A2-RUNTIME-1: both actual generator/compiler success and failure
scenarios passed in 1 suite with 2 tests. SBT/wrapper exit 0 and the shared serial
lock was released. This proves only normal owning-UoW Compile, configured source
generation, actual compiler success/failure and retained fresh-runtime reads.
The preceding Test qualification and A-2 focused consumer/codec result retain
their own scope. Existing normal ProductCheck supplies representative terminating
application acceptance through the same owning typed SBT path; it starts no
service, so service-specific readiness/cleanup is not applicable. This does not
establish full external-tool coverage or installed-host acceptance. The actual
B4 failure-to-AI repair connection is qualified below; independent COMMAND Step
review/local acceptance commit and the other Phase obligations remain open.

## P61-COMMAND-B selected failed-check repair (pure, managed runtime and B3/B4 CLI qualification)

New Step validation can select `repair-required` only for an actual-shaped
nonempty ordered typed receipt prefix with positive product-successful
predecessors and final matching `failed` facts, `exited` termination and nonzero
exit. Compile, Test and TestOnly use the same classification. Unknown intent,
legacy facts, missing/partial positive results, timeout, incomplete or unverified
tests, crossed identities/kinds/order, exhausted allowance and whole-Phase scope
never create an automatic repair. Historical saved `failed` outcomes retain
Decision without migration or replay.

The owning validation transition persists Repair and is also used for retained
admission and history reconstruction. Issued RepairStep carries separate
`checkFailure` business context with the actual failed candidate, selected
checks, raw typed receipts, changed paths and diff. It replaces no independent
review context: exactly one valid source is required. Semantic completion still
requires the selected acceptance report and returns to fresh runtime inspection,
checks and independent review. Submit supplies no receipts or commands.

Parent accepted B focused pure qualification: 2 suites and 47 passing tests,
including generated public DTO compilation. These scenarios use pure
actual-shaped facts and codecs; this result establishes neither actual external
execution nor provider proof.

P61-COMMAND-B2 adds one managed public-operation connection obligation in
`ThreeWorkflowSbtOperationSpec`: `repair a real failed SBT check through assigned
semantic work and fresh runtime validation`. Four separate runtimes issue
ImplementStep, submit incorrect semantic input and execute a real failed Test,
reopen the identical RepairStep and correct the assigned input before a fresh
runtime-owned Test, then reopen the identical pending independent ReviewStep.
The scenario requires actual failed and successful typed Test receipts, native
checkout output, two distinct external PID entries, retained original failure
diagnostics and no extra launch on Submit or pending-work reopen. It preserves
the original base, selected plan and consumed repair generation and leaves the
native Goal active at `phase.Review`.

The deterministic semantic fixture supplies only assigned source corrections
and participant result evidence. It supplies no native receipts, independent
review, effect authority or provider invocation. Parent accepted actual B2
managed public-operation validation: 1 selected suite and 1 passing test, with
SBT/wrapper exit 0 and the shared development lock released. This qualifies the
connected actual failure, assigned semantic-fixture correction, fresh validation
and retained pending review through ordinary public Operations.

P61-COMMAND-B3 authors the separate normal CLI connection in
`NativeGoalCheckRepairCliSpec`: `carry actual failed checks through ordinary CLI
repair and fresh validation`. An opt-in host fixture prepares the ordinary
selected SBT build before its initial repository commit, selects only value.txt
for workflow staging and combines finite typed Git observations with typed Test.
Separate standard CncfMain Start, Advance and semantic Submit commands retain the
actual canonical task and issued public work context. The selected incorrect
input must produce a real failed Test and RepairStep; only the assigned fixture
correction changes it to the expected newline. Explicit Advance must then run a
fresh successful Test and issue independent ReviewStep. Pending Repair and Review
rereads must retain their actual public context without another SBT launch, and
the two checks must have distinct external PID markers. Public CLI assertions
use the actual issued context and route; B2 retains the internal generation proof.

Parent accepted actual B3 ordinary CLI validation: 2 selected suites and 3 passing
tests, covering `NativeGoalCheckRepairCliSpec` and `NativeHostExecutionCliSpec`,
with SBT/wrapper exit 0 and the shared serial lock released. The repair scenario
stops at pending independent Review and supplies no review, commit or closure.
Its source correction is initial semantic fixture data; this qualification does
not establish genuine external provider repair.

P61-COMMAND-B4 authors two separately selected cases in
`NativeGoalProviderRepairCliSpec`: `prepare actual provider repair` and
`consume actual provider repair`. The suite is admitted only by explicit
testOnly selection and remains excluded from default discovery. Preparation
requires explicit `smworkflow.spec.providerOrigin` and the normal selected SBT
executable. Initial Start/Implement has a selected classification and no mapped
disposition. Its deliberately incorrect value and initial semantic Submit are
explicit fixture inputs, without execution evidence. Only after that Submit does
the host install the requested `p61-provider-sol-medium` OpenAI/gpt-6.1-sol profile
with `reasoning-effort=medium`, the parent high profile and isolated context.
CodingProgrammingStandard and ReviewStandard select medium without fallbacks.
These are requested configurations, not observations of provider execution.

Explicit Advance must execute actual failing Test and retain the typed nonzero
receipt, checkout diagnostics and candidate in RepairStep. A repeated Advance
must retain identical work/context with one external SBT PID. Ordinary
prepareExecution consumes the original mapping and origin and returns the actual
medium selection and opaque execution session, without completion evidence.
Preparation saves `provider-issued.json`, `provider-prepared.json` and the raw
`provider-session.json`, plus original `provider-mapping.json`,
`provider-origin.json` and passive `provider-input.json` business inputs in the
owned root. After all assertions it prints the exact anchored
`P61_PROVIDER_REPAIR_ROOT=<actual root>` and stops while the value remains
incorrect and genuine Repair is pending. The passive input grants no authority.

The parent must dispatch genuine external semantic work under that selected
profile with the original Request and actual failure context. The provider owns
only `repository/value.txt` and `provider-result.json`, may inspect assigned
source/diagnostics, and runs no SBT, Git, launcher or process commands. The result
file is a closed object with `result` and `reference`, using the public
GoalPhaseWorkResult and WorkflowArtifactReference shapes. Its reference identity
is the actual provider child context and revision is `1`. Only after actual
native provider completion does the parent write `provider-observation.json`
using the public harness attempt shape: actual selection, COMPLETED status,
Completed reason, actual executor context/profile, detail and observed resources.
Unknown resource fields are omitted. Observation coherence authenticates no
provider provenance; actual dispatch and honest observations remain parent-owned.

Consumption requires explicit `smworkflow.spec.providerRoot` and reopens only
that normalized existing direct child of `target/sm-workflow/work/native-cli`.
It reads the public host definition, Plan and existing provider task route;
it rewrites no host, Plan, build, Git or Workflow state. Its ordinary command
logs start at unused index 1000, preserving earlier outputs. Repeated Advance
must still equal the saved actual Repair despite the provider-corrected value.
Ordinary recordExecution consumes the original opaque session, actual observation
and semantic reference, returning completion evidence without another SBT.
Named Submit then supplies the actual provider semantic result/reference and that
returned evidence. Only an explicit subsequent Advance runs fresh Test and
issues pending independent Review with a positive typed receipt, fresh candidate,
retained base/plan/completed context and exactly two distinct external SBT PIDs.
A repeated Review Advance must retain that work and context without a third check.

Parent qualified actual B4 preparation plus the existing accumulator: 3 suites
and 4 tests. A genuine selected medium semantic provider then completed the
assigned repair, and the actual normal consumer passed 1 suite/1 test. Runtime
owned the failed Test, fresh Test and transition to pending independent Review;
the semantic worker ran no SBT, Git or process commands. Requested configuration
and parent-observed actual completion are distinct evidence, without runtime/model
attestation. This supplies no independent review acceptance, Step/Phase commit or
closure. COMMAND is connected and focused validated, pending its existing
protected independent Step review/local acceptance commit. P6-EX02, installed-task
acceptance, security/CBD, the sole full Phase review and local release remain
open; aggregate full-suite validation remains Phase 6.3. No raw logs or private
evidence package are added to Git.
