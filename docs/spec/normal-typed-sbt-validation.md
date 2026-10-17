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

`WorkflowManagedProcessExecution.repositoryValidationC(core, ordinal)` accepts
only ordinals 1–16 and constructs the logical
`SbtTarget(WorkspaceId("sm-workflow-repository-validation-" + ordinal))`.
Its ProcedureActionCall composes protected `sbt_test` as thin typed intent.
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


## Explicit typed validation consumer (authored, unverified)

The S6-01D6T2 consumer change adds the closed trusted Check kind `test` or
`test-only` to the existing logical `sm-workflow-repository-validation-N`
binding. The existing ordinal and owning UoW still select the host program.
`test` dispatches `sbt_test`; `test-only` dispatches `sbt_test_only`. CNCF owns
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

Current-schema records explicitly read absent Check `kind` as Test and absent
Receipt `sbt` as no typed facts. Test encoding preserves the historical absent
kind shape; absent `sbt` remains omitted. Present null, mistyped or unknown kind,
and present null or malformed `sbt`, fail decoding. These rules apply through
Goal validation, Repository validation state and native progress, Goal review
work, Repository review Request and the existing host definition. Unsupported
older schema versions remain unsupported. Existing saved records are not
rewritten, migrated or promoted from Test to TestOnly.

Both native workflows compare the selected identity and kind against actual
receipts; retained selection also resolves the original ordinal in the current
policy. A legacy receipt without typed facts can satisfy only Test. Exact
positive TestOnly facts are required even at raw zero. Retained results and
unknown intents are read without replay. Review Context and Request retain
complete typed receipts; evidence targets project typed operation, outcome,
classes and counts without copying raw logs or policy bodies. All-None review
targets retain their historical shape.

This consumer implementation and its codec/predicate scenarios are authored
and await parent-selected validation. The seven actual normal Test episodes
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
