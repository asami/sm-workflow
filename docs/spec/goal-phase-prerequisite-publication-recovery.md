# GoalPhase prerequisite publication recovery

## Primary generation repair v2 and publication recovery v5

RGEN01. A distinct primary generation classification admits the observed sbt-cozy
missing-input, INPUT-kind syntax and Workflow operation-binding structural failures before selected checks. It requires the actual failed
`beforeChecks` primary publication, exact Session, participant, candidate source,
request namespace, target and fixed `publish-local` program. Both typed process
streams must be complete and untruncated, with confirmed `Exited(1)`. The only
failed task summary is `(cozyGenerate) [sbt-cozy] cozy delegate failed (1) for ...`.
The source-error marker must be the entire standalone `[error] Error[No input]`
line or the exact fully qualified syntax family
`[error] Error[org.goldenport.exception.SyntaxErrorFaultException: Operation '<identifier>' INPUT Value '<identifier>' requires input-kind=COMMAND|QUERY.]`.
Both identifiers require a letter or underscore followed only by letters,
digits or underscores. Prefixes, suffixes, other exception classes, OUTPUT
errors and other input-kind messages do not qualify. The additional finite family is exactly
`[error] Error[org.goldenport.exception.SyntaxErrorFaultException: WORKFLOW '<identity>' OPERATION '<entry>' does not admit nested structural content.]`
or the same complete line with `REQUIRED-OPERATION` instead of `OPERATION`.
Workflow and entry identities must be nonempty, already trimmed, and contain
neither a quote nor control characters; real CML identities, including hyphens
and non-ASCII text, are allowed. This is the direct-binding structural refusal
issued by Cozy's provided/required-operation parser, not general syntax-error
admission. Other contexts, classes, messages, leading text and suffixes refuse.
The same closed source
predicate applies to a final source-error line. The capture must also contain the
error JSON diagnostic `GENERATION_PROVENANCE_OUTPUT_TAMPERED`, with expected
`at least one generated Scala artifact`, actual `none`, and message
`Generation provenance cannot describe an empty output.` The repeated diagnostic
after the task summary is retained and accepted. Every entry in each qualifying
provenance error array must be that existing empty-output diagnostic; mixing in
another failure code refuses the whole capture. The error stream admits only
recognized source diagnostics, that provenance, and the demonstrated delegate
failure envelope: the wrapper RuntimeException root, fixed sbt-cozy generator,
command/cwd/recovery/recent-log metadata, notice-level
`MutableDevelopmentPairAccepted`, Java stack trace lines and the single
`cozyGenerate` task summary. The retained isolated structural-old capture also
contains neutral SLF4J multiple-provider bootstrap metadata. Exactly these
complete bodies are permitted after `[warn] [sbt-cozy/cozy] ` or `[error] `:

- `SLF4J(W): Class path contains multiple SLF4J providers.`
- `SLF4J(W): Found provider [<provider-class>@<hex-identity>]`
- `SLF4J(W): See https://www.slf4j.org/codes.html#multiple_bindings for an explanation.`
- `SLF4J(I): Actual provider is of type [<provider-class>@<hex-identity>]`

The provider class is a nonempty fully qualified Java name with dot-separated
identifier segments (letters, digits, underscore and dollar; no leading digit),
and the identity is nonempty hexadecimal. Control characters, brackets within
the identity, malformed names, other SLF4J messages and added text refuse on
these channels. Provider equality is not required because these entries are
metadata. They never establish positive generation-source proof. This finite
family describes the observed logger scaffold in that saved isolated capture;
it adds no source syntax family and claims no successful new-side continuation.
The original complete typed capture, exact source/provenance, fixed program,
authority and repair-count requirements remain unchanged.

Repeated source/provenance is permitted. An
unrecognized `Error[...]`, another exception or failure message, admission
rejection, other provenance error or another failed task refuses even when a
qualifying source marker appears elsewhere. Source-looking metadata cannot
establish the source diagnostic. A warning alone does not prove
the failure. Arbitrary exits, missing markers, other or mixed failing tasks,
Compile, Test, doc-only, publication success, nonstart, cancellation, timeout,
UNKNOWN, incomplete text and truncation do not qualify.

The closed failure kind is derived from the retained typed capture. Generation
uses `sm-workflow.goal-phase-prerequisite-primary-repair.v2` for the immutable
repair Fact and `sm-workflow.goal-phase-prerequisite-publication-recovery.v5`
for its public Target and Direction envelope. Compile retains its exact v1 Fact
and v3 Target/Direction bytes and classifier. Generation in a v1/v3 envelope and
Compile in a v2/v5 envelope refuse. The v4 Target remains exclusively the
current-source artifact adoption contract. Existing business operations and
optional `publicationFailure` context carry these versions; no CML field or
storage controller is added. Input recognition, independent answer binding and
passive preparation use the same closed Target validation.

Generation uses the same ordinary reservation, repair budgets, trend/limit
decisions, current source capture, broad versus one-shot publication authority,
fresh checks, independent review and Step closure as R4–R6. Its directive names
generation and preserves the actual original failed capture, controller,
candidate, accepted result, Plan, base, prefix, dependency lineage, excluded
dirty paths and repair counters. `testsExecuted` remains false. Recollection
neither spends another reservation nor manufactures check or publication PASS.

At a retained eligible failed primary frontier, public
`describeGoalPhasePrerequisitePublicationRecovery --sessionId <saved-session>`
returns the v5 generation Target. A later ordinary `advanceGoalPhase` reserves
the existing repair and then issues `RepairStep` (the reservation call may first
return the retained failed result). The assigned worker corrects the missing
CML INPUT or direct Workflow operation binding and submits only that issued repair. Ordinary Advance captures a new
candidate, runs the required publication and checks, and reaches independent
review. The already accepted implementation is never submitted again; there is
no new Start, counter reset or replay of an unknown effect. This describes the
consumer continuation contract; this document does not claim it has been run
against any consumer Session.

An unsupported primary result returns an explicit structured invalid
`Unsupported primary publication failure` diagnostic. It cannot fall through to
dependency-only UNKNOWN recovery. That recovery also validates the optional
dependency binding before dereferencing it and returns a structured invalid
diagnostic when absent. Supported dependency and UNKNOWN continuations retain
their existing behavior and authority.

E14 in `GoalPhasePrerequisitePrimaryRepairSpec` covers the missing-input
signature, strict negatives, one reservation and truthful unexecuted tests.
Its separately selectable `GoalPhasePrerequisitePrimaryRepairCodecSpec` covers
v2/v5 roundtrips, rejected crossed versions, unchanged Compile canonical
serialization and independent preparation. E16 in the separately selectable
`GoalPhasePrerequisitePrimaryGenerationSyntaxSpec` covers the exact
INPUT-kind and both structural binding families, complete stdout/stderr and
stderr-only captures, mixed diagnostic/provenance refusal, strict error/task/target/process/termination/capture
negatives, and the next ordinary same-Step reservation under the original limit,
retaining the first failure. These typed captures are specification data, never
actual publication receipts.

E15 in the explicit-only `@DoNotDiscover`
`NativeGoalPrimaryGenerationRecoveryCliSpec` defines one paired installed
episode on a tiny real sbt-cozy project. Select these JVM properties:

- `smworkflow.spec.installedCommand`: absolute normal installed CNCF command
  using the newest launcher.
- `smworkflow.spec.generationLocator`: absolute caller-owned locator file;
  absent for capture, retained unchanged for continuation.
- `smworkflow.spec.generationMode`: exactly `capture-old`, `continue-new`,
  `resume-current-generation`, `qualify-current`, `collect-review`,
  `capture-history-old`, `continue-history-new`, `capture-structural-old`,
  `continue-structural-new` or `collect-structural-closure`.
- `smworkflow.spec.generationLogIndex`: optional for `continue-new`, default
  `2000`, with `2000` or `3000` allowed. The bounded resume mode requires the
  explicit unused value `4000`.
- For `collect-structural-closure` only,
  `smworkflow.spec.generationClosureLocator` is the explicit absolute
  `structural-closure-locator.json` beside the unchanged original
  `generationLocator`. It names the same original locator and Session plus
  the actual saved public repair, accepted repair, Step review, Step
  publication, accepted controlled review, closure Wait and Phase outputs.
  This passive collector requires an explicit `generationLogIndex` of `4000`
  or `5000`, selecting an unused range and preserving every earlier log.
- For `resume-current-generation` only,
  `smworkflow.spec.generationFailureOutput`,
  `smworkflow.spec.generationRepairOutput` and
  `smworkflow.spec.generationRepairSubmitOutput` select the exact existing public
  second-failure, first-RepairStep and accepted first-Repair Submit stdout files
  (the retained episode's `3009.stdout.json`, `3005.stdout.json` and
  `3008.stdout.json`). Each must be a real normalized file directly in that
  locator's exact fixture `logs` directory and match its Session and typed result.

`smworkflow.spec.installations` must be absent; this episode supplies no runtime
image installation override. SBT resolution uses
`TypedSbtHostPlanFixture.normalExecutable` and requires no additional property.

Use the exact suite selector
`testOnly org.simplemodeling.textus.smworkflow.admission.NativeGoalPrimaryGenerationRecoveryCliSpec`
with optional ScalaTest filter `-- -z "E15 paired installed primary generation recovery"`.
First run `capture-old` using the old installed runtime before producer
publication. It makes one actual failed `publishLocal`, retains the accepted
implementation, captures the old description's 500/`None.get`, and saves only
caller routing plus actual public outputs in the explicit locator. After normal
producer publication and installed selection, run `continue-new` using that
same locator and physical fixture. The narrow reopen helper checks the exact
root, retained host and Plan and allocates a disjoint finite log range; it scans
no repositories and writes no native records.

Continuation describes the saved failure, issues and recollects one repair,
corrects INPUT and the supported `- input-kind :: COMMAND` property on
`ProbeInput`, and requires real publication, Compile and focused ScalaTest
success before the independent-review frontier. It checks unchanged original
failure bytes, accepted result, Plan/base/prefix, repair count and original HEAD;
repeated collection must repeat no successful effects. Every normal Git/SBT/UoW
effect belongs to the installed runtime. No full project copy, seeded native
state/result, fake executable, wrapper or independent SBT runner is used.
Specification-owned fixture setup and semantic results confer no consumer
authority. The episode stops before independent review acceptance and Step
commit; parent-owned actual validation and installed receipts establish whether
it qualifies. Its presence alone claims no runtime qualification.

The separately selected finite pair `capture-structural-old` and
`continue-structural-new` uses a fresh caller-owned `generationLocator` and the
same tiny real sbt-cozy fixture, never a copy of the product. Capture must run
before producer publication. Its genuine malformed CML retains the corrected
`ProbeInput` COMMAND input-kind, full public `ProbeService.echo` INPUT/OUTPUT,
`ProbeResult`, `ProbeLifecycle` Ready/Done states, and `GenerationWorkflow` v1
with a valid constituent, derivations and initial state. Only the Workflow
OPERATION binding is malformed: nested TYPE/INPUT/OUTPUT replaces the required
direct binding. Capture requires the real fully qualified structural error,
complete typed `Exited(1)`, no selected checks, and the old description's
structured 409 `Unsupported primary publication failure`. Repeated ordinary
collection must not republish. The locator retains actual public output paths
and bytes, accepted implementation, source/config/Plan/base, HEAD/index,
counters and fixed invocation logs; it writes no native state.

After normal new producer publication, structural continuation reopens that
same physical fixture and Session at unused log index 2000 (or explicitly
3000). The public v5 description must match its saved failure and original
Plan/base/prefix. Ordinary Advance reserves exactly one same-Step repair; its
first response may retain the failure before a second response issues RepairStep.
Pending recollection has no additional charge or effect. Only the newly issued
repair replaces the nested Workflow binding with
`operation = ProbeService.echo`, preserving every public model declaration. One
new semantic Submit creates the fresh candidate. Exactly two publication
attempts total and one actual Step Compile and focused TestOnly must reach
ReviewStep, with the original failed result and already accepted implementation
unchanged.

This structural pair additionally exercises actual native Step closure using
passive `prepareEvidence`, its selected normal import, Review Submit, the real
StepClosure authority Wait, and passive `prepareAuthority` plus import. These
review and authority inputs are explicitly **SPECIFICATION-OWNED controlled
inputs**. They are not actual independent provider, Human or consumer evidence;
the parent's independent source review remains separate. The resulting real
Step commit must name the original HEAD as parent and the reviewed candidate
tree, retaining one ordinary repair at the Step review. The existing shared
host declares separate Step and Phase Compile/TestOnly checks and a
`beforeChecks` publication for the new Phase candidate after the Step commit.
There are therefore three distinct publication attempts by ReviewPhase:
the original failure, the repaired Step publication, and the Phase publication
whose source HEAD is the actual Step commit and whose tree is the committed
candidate tree. All request identities differ; the Phase publication is a new
declared effect, not replay of the successful Step publication. The structural
fixture alone appends a countable focused-test marker: one completion at
ReviewStep and a second only for the distinct Phase check after closure. Their
different Step/Phase work contexts, commit snapshots and receipts distinguish
that declared Phase execution from replay of the Step success. Old modes retain
their existing CREATE_NEW marker behavior. Continuation stops at the actual
pending ReviewPhase frontier, with no Phase review submission or whole-Phase
completion claim. Another ordinary collection must repeat neither successful
publication, checks nor commit. Only after all assertions does it write
`structural-generation-qualification-result.json` inside the fixture root,
recording actual failure, publication, Step commit, Step/Phase receipts and
qualification limitations. Presence of these modes alone establishes no
execution result and no activation of the consumer's retained Session.

If the episode already reached ReviewPhase and only a fixture assertion failed,
`collect-structural-closure` qualifies its retained results without rerunning the
full continuation. The caller-selected closure locator identifies exactly the
saved public `3005` RepairStep, `3008` accepted repair Submit, `3009` ReviewStep,
`3010` successful Step-publication collection, `3016` accepted controlled Review
Submit, `3017` StepClosure authority Wait and `3020` ReviewPhase stdout JSON
files. Every file must be normalized, real, and directly in the same fixture's
`logs` directory. The collector checks their typed Session, Plan/base, source,
failure context, Step repair count one, actual commit parent/tree and separate
Step/Phase Compile and single-test TestOnly receipts. The Phase window's repair
count zero is ordinary progression; it does not erase the saved Step charge.

The complete real invocation sequence must remain exactly seven entries:
failed publication; repaired Step publication, Compile and TestOnly; distinct
Phase publication, Compile and TestOnly. The original invocation prefix and
both focused-test completion markers remain unchanged. One ordinary Advance
recollects only the already pending ReviewPhase request in the explicitly
selected unused `4000` or `5000` log range;
the public publication collectors read the latest Phase publication and both
earlier exact records. Before and after these calls, the collector compares
source, index, HEAD, caller routing, config/Plan, invocation logs, selected
accepted/failed output bytes and both locators. It performs no Start, source
edit, Plan selection, semantic Submit, authority preparation, publication,
check or commit. Only after those assertions may it write
`structural-generation-qualification-result.json` in that same fixture root,
naming actual public outputs, the real Step commit, all three source-bound
publications and both sets of check receipts. The controlled review/authority
inputs remain SPECIFICATION-OWNED, and the frontier remains pending ReviewPhase
without whole-Phase or consumer completion. Defining the collector does not
claim that its qualification has succeeded.

The collector identifies exactly four business invocations by complete
qualified operation selectors present as argv members, in order: one
`GoalPhaseService.advanceGoalPhase`, one
`LocalPublicationService.collectLatestLocalPublication`, then two
`LocalPublicationService.collectLocalPublication` calls. The fixture also logs
its permitted read-only Git observations; transport filenames and total log
count do not define the business-operation count. The result's
`collectionOutputs` contains only the three actual publication-collector stdout
paths, excluding those Git observation logs.

The bounded `resume-current-generation` mode continues the existing episode
whose first accepted repair added INPUT alone and then encountered the exact
terminated INPUT-kind syntax failure. It preserves that INPUT-only text as the
expected current source; setup never rewrites the failed subject. The mode
checks the explicit public artifacts, current latest syntax failure, original
first failure, both already accepted results, complete Plan/base/prefix, source,
original HEAD and current count one. No old semantic Submit or publication is
replayed. Ordinary Advance retains the second failure and reserves repair two
under the original limit two; a separate Advance issues the new RepairStep.
Pending recollection repeats no effects. Only this new assigned repair adds the
real `ProbeInput` input-kind bullet, leaving `ProbeResult` unchanged, and receives
one new semantic Submit. The runtime then owns the third publication, actual
Compile and real focused test before independent review.

Success requires three actual publication attempts total, one successful
Compile and one real focused test, immutable first and second failed bytes and
accepted artifacts, count two without reset, and no repeated successes on
ReviewStep recollection. Only after those observations does the fixture write
`generation-continuation-result.json`, naming both failures, the new repair and
real publication/check receipts. A missing work result fails with the actual
response diagnostics. An unexpected source error remains a failed test; this
mode is no generic repair controller and authorizes no unknown-effect retry,
private-state edit, new Start, new Session, independent-review acceptance or
Step commit. Parent-owned qualification remains necessary.

The explicit `qualify-current` mode uses a new absent caller-owned locator and
a new tiny fixture with canonical CAR packaging metadata:
`packaging.kind: car`, `packaging.car.runtime.cncf.minimum: 0.5.3-SNAPSHOT`,
`excluded: []`, and `tested: [0.5.3-SNAPSHOT]`. This metadata belongs only to
new fixture setup. The existing episode's later terminated `cozyBuildCar`
metadata failure remains outside the Generation classifier and retains its
actual failed publication and exhausted two-repair history without edits or
retries.

On the current installed runtime, the first genuinely failed ordinary Advance
already reserves repair one. Current qualification therefore skips the
historical description call, requires actual count one under limit two, and
saves the exact failure and locator. The same mode then reopens that same new
physical fixture at unused log index `2000` through the existing continuation
helper. Its first ordinary Advance must issue the already reserved RepairStep
directly, without a second reservation or budget reset. Existing full public
Plan/base/prefix, failure-context, pending-recollection, actual complete CML
correction, new semantic Submit, real publication/Compile/TestOnly and pending
independent-review assertions still apply.

Current qualification neither repeats the old 500 capture nor requires a v5
description at a Repair frontier. The historical `capture-old`/`continue-new`
pair retains its old-500 and saved-Validation-frontier v5 checks; the separate
resume mode retains its exact historical artifact contract. `qualify-current`
is fresh qualification of corrected fixture inputs, with no operation on the
earlier saved Session or replay of its successful effects. It requires the
normal installed command and new locator, with no installations override,
production change, new publication of the producer, native-state write or
fabricated review/Step success. Actual qualification remains parent-owned.

## Ordinary primary Compile repair and same-scope publication v3

R4. A genuine primary `beforeChecks` publication Compile failure belongs to the
current selected Step's ordinary repair scope. The actual publication must be
failed, terminated with a nonzero exit, bound to the current candidate's own
request namespace and fixed primary Component/workspace/program, and retain
complete untruncated stdout and stderr. SBT task-summary errors must include
`(Compile / compileIncremental) Compilation failed` and may also include the exact
parallel `(Compile / doc) Scaladoc generation failed` summary. Every task-summary
error and the final non-timing error must be one of those two. A doc-only failure,
arbitrary exit, Test failure, late publication error, other task failure,
admission refusal, nonstart, cancellation,
timeout, UNKNOWN or partial effect does not qualify. Check selection and every
check/result namespace must remain unstarted. A dependency-bearing route must
already have its complete authenticated source capture. The earlier missing
dependency failure keeps its existing SourceRequired obligation.

The failing Advance returns the original actual publication failure. It may
append one immutable native primary-repair fact and reports its actual ordinary
repair progress. The next ordinary Advance reconstructs that fact and issues
the existing RepairStep, or the existing limit/trend Decision. No additional
Human answer is required to correct covered source. Reservation uses the normal
selected allowance, ordinary count, trend stop and runtime ceiling of ten. It
increments generation and counts once; recollection does not refund or consume
another repair. Existing ContinueRepairs remains the limit continuation.

R5. The repair fact retains the actual original publication request/result,
candidate and accepted semantic result, complete selected Plan/base/Step/prefix,
controller and repair snapshot, fixed process/publication policy and dependency
lineage. Native reconstruction authenticates the adjacent predecessor and reads
the original failure's own immutable result namespace even after a successor
becomes latest. `publicationFailure`, an optional Record at the end of the
existing work request/view, carries that real failure, repair reference, original
changed paths and diff, with `testsExecuted = false`. Full compiler captures
remain intact. No NativeValidation payload, check receipt, validation success or
independent review is fabricated.

That publication failure is the work cause only for its same-Step Repair at the
failure's next generation with the retained directive. This includes the
existing independently admitted ContinueRepairs transition from a limit or trend
Decision, whose renewed window reserves one batch and resets observations.
Later actual selected-check or independent-review repairs retain
their own exclusive checkFailure or review context. Historical dependency,
unselected-dirty and scope-authority lineage remains available across those
ordinary same-Step repairs. The first corrected candidate uses the confirmed
failure successor anchored to the actual failed publication. Later check or
review corrections use ordinary prerequisite publication after the same scope
authority evaluation; an old failed predecessor is never replayed after success.

R6. Normal RepairStep Submit is followed by actual primary candidate capture.
The versioned candidate's explicit `primaryPublicationRepair` relation identifies
the authenticated repair fact and its original candidate/capture source. When
the effective dependency source is unchanged, its capture keeps its original
generation, source revision, source mode, bytes, stages and accepted
artifact/UNKNOWN lineage. It is checked for live freshness and reused without
recapture or restaging.

A formally adopted, authenticated newer source successor instead receives a
fresh capture of the actual current source under the current generation and
source revision, through existing Git/UoW observation and contiguous native
record authentication. The old failure, capture and repair reference remain
immutable history; their bytes are not substituted for the selected successor.
Neither branch transfers an old PASS or permission to the new candidate or
replays UNKNOWN effects. Normal publication authority, fresh actual selected
checks, independent review and normal Step closure remain necessary.

When that successor changes the dependency source covered by the original
existing-artifact acceptance, ordinary Advance first returns a current-source
artifact Wait. `describeGoalPhasePrerequisitePublicationRecovery` describes the
closed `sm-workflow.goal-phase-prerequisite-publication-recovery.v4` target:
the exact current primary publication target, immutable previous artifact
adoption and UNKNOWN, current capture guard/source and complete current process
policy. The independent Human chooses exactly
`AcceptExistingArtifactsWithUnknownEffects` or `Defer` through the existing
`recoverGoalPhasePrerequisitePublication` operation and evidence preparation.
Defer grants no authority and appends no native direction. Acceptance appends
a separate current-source artifact direction under the same Session and keeps
the old direction, failure, UNKNOWN, capture, history and repair counters.
It makes no retrospective claim that the UNKNOWN publication succeeded or
terminated. The v1–v3 targets remain readable with their existing meanings.

Only a later ordinary Advance consumes that exact current-source acceptance,
rechecking live guard/source, current policy and both original-request and
latest-target UNKNOWN records. It still requires the separate primary
publication permission and fresh checks. Changed source, stale candidate or
frontier and duplicate recovery refuse; neither old acceptance nor source
adoption supplies the fresh Human artifact decision.

For this authenticated same-root, same-branch committed source succession, only
the existing source workspace's single read-only `TreeEntries` inventory may
extend from the previous exact paths to the currently approved exact paths.
The complete retained process policy must otherwise remain equal, including
programs, settings, workspaces, grants, limits, validation and destinations.
This read inventory relation grants no publication or check execution authority.

The initial repair fact retains the typed Git dirty paths that were outside the
failed candidate's actual selected/staged changes. The same chain preserves
this original unselected baseline during reconstruction. New candidate staging
excludes those unrelated paths while allowing newly dirty corrective files
covered by both the unchanged host selectors and the Step's ownership. Typed
actual dirty records determine distinct concrete paths in registered-selector
order; a directory covering an excluded child is never staged. Safe renames
select both endpoints. Unowned, excluded, malformed or ambiguous endpoints,
ancestor overlaps and path-prefix lookalikes refuse selection. The immutable
excluded records must retain their original index/status, and the actual global
index must remain owned, host-covered and free of excluded paths and rename
sources.

Each refined child uses a single literal `GitPath` in `GitSelectionParameters`,
with its existing covering `goal-path` identity and the same `StageSelectedAll`
operation. CNCF preflights every selected primary repair target, including exact
registrations, before source-history inspection
or effects and staging intent, then admits execution through the owning UoW.
The registered program, binding, grant, settings and authenticated process policy
remain unchanged; selection data grants no authority. Exact registered paths
retain their existing staging route. Missing or refusing admission stops before
effects. No `StageFullState` registration, grant or execution is required. A
current pinned typed observation may confirm an already indexed `D/space`
removal, or the old endpoint of an indexed rename with both literal endpoints
selected and no standalone or reintroduced old-path record. This primary repair
confirmation records ordinary candidate progress from actual status and global
index checks; it issues no add for an already absent indexed path and claims no
Git execution result. Rename destinations and all other paths retain actual
typed staging and result checks. Intent/result, acknowledgement and UNKNOWN
guards remain in force; observation grants no operation replay. Normal
source-history and staging effects operate only on the concrete corrective
paths; no generic Git command or index rewrite is introduced. Retained concrete
children require the authenticated current primary repair relation; nonrepair
and legacy candidate behavior remains unchanged.

R7. Ordinary installed beforeChecks policy plus the admitted current Step covers
its necessary same-scope publication through existing execution/security
admission. Such repair and publication proceeds automatically. Explicitly narrow
legacy source/publication directions remain narrow: their consumed one-shot
grant does not cover publication of a new candidate. Corrective work is still
ordinary in-scope RepairStep. At the actual new candidate, existing describe and
recover Operations expose the closed
`sm-workflow.goal-phase-prerequisite-publication-recovery.v3` target with
`missingAuthority = new-primary-publication`. Available choices are
`AuthorizePrimaryPublicationOnce`,
`AuthorizePrimaryPublicationAndSameScopeRepairs`, and `Defer`.

The broad choice explicitly covers necessary publication following later owned
source repairs under the same Component, participant, Session, Plan, base,
accepted prefix, Step ownership/acceptance, fixed programs, ordered targets and
destination. It requires actual independent Human evidence through the existing
prerequisite-publication-recovery purpose and normal preparation/provisioning;
prose, a config flag, changed bytes or an old attempt receipt cannot establish
it. A changed Component, program, target, destination or ownership remains
uncovered. Each actual candidate/attempt has its own publication request/result.
The latest conflicting writer still blocks execution. Before automatic repair,
the optional public v3 choices `RepairPrimarySource` and
`RepairPrimarySourceAndContinuePublication` support genuine legacy/manual
continuation; neither replaces the automatic ordinary route.

R8. v1/v2 target meanings and existing v7 dependency-source bytes remain
unchanged. First source capture, legacy artifact adoption, original dependency
UNKNOWN, prior accepted results and successful effects remain historical facts.
There is no new public Operation, Session, permission registry or dependency
acquisition. The focused primary-repair specification covers classification,
canonical codecs, deterministic reservation/recollection, normal limits/trends,
explicit capture reuse, exact one-shot/broad scope and source attribution. The
installed E7/E8 cases separately qualify ordinary automatic continuation and
genuinely narrow new-candidate authority.

## Primary failure continuation v2

R1. The closed `sm-workflow.goal-phase-prerequisite-publication-recovery.v2`
target is distinct from the existing correction v1 and dependency UNKNOWN v1
targets. It applies only to the ordinary Step's exact two ordered beforeChecks
targets: the original dependency prerequisite skipped by an authenticated
`AcceptExistingArtifactsWithUnknownEffects` Direction, followed by a primary
publication with an actual typed terminated nonzero result. Checks must not have
started. Session, Plan, original primary base, completed prefix, primary candidate,
accepted semantic result and repair accounting remain exact. No third-target,
Task, whole-Phase or generic publication-to-FIX route is admitted.

The target retains original UNKNOWN request/result and artifact Direction,
the genuine primary request/result, current controller/native frontier, current
dependency guard, exact policies, optional v7 capture, and each earlier operation
fact. The original candidate record need not equal the post-Direction frontier:
native traversal proves every actual adjacent predecessor. Same-root corrected
dependency bytes enter a fresh SourceRequired guard without rewriting the old
UNKNOWN guard. Historical reconstruction uses retained exact guards and receipts;
fresh adoption re-observes selected source bytes. Old canonical schemas remain
readable and are never rewritten or reinterpreted as v2.

For source selection alone, a SourceRequired origin retains the authenticated
original dependency guard instead of observing the old binding through a logical
workspace now bound to a declared successor. Current primary source, process and
publication policy remain live checks. The selected v7 source is observed
separately, including its root, branch, committed base, inventory and exact
current sourceRoot program. Ordinary primary descriptions still observe the old
source, and an existing capture always requires current freshness.

R2. The public phases are:

| Phase | Available decision |
|---|---|
| SourceRequired | Defer; use the existing dependency-source describe/adopt Operations |
| ArtifactDecision | AcceptExistingArtifactsWithUnknownEffects or Defer for the captured corrected source |
| PrimaryDecision | AuthorizePrimaryPublicationOnce or Defer for the exact latest genuine failure |
| PrimaryUncertain | Defer only; collect retained outcomes |

An already retained unspent primary grant exposes Defer while ordinary Advance
owns its execution. Source adoption, artifact adoption and primary authorization
remain separate operations/results. The v7 combined source choice
`AdoptDependencySourceRevisionAndContinuePublicationWithUnknownEffects` can
supply one actual Human authority for the concrete ordered plan. Source adoption
runs no effects. Separate ordinary Advances perform source capture, retain one
artifact fact, retain one primary-authorization fact, then execute/collect the
primary attempt and ordinary checks. Each derived fact records its actual native
result identity and `inheritedFrom` naming the authenticated combined source
directive. No new Human evidence reference or binding is invented. The source-only
choice leaves later uncovered decisions public. Existing UNKNOWN consent alone
does not authorize artifacts at changed bytes.

Inheritance requires the exact captured source, unchanged primary snapshot,
original uncertainty and failed result, ordered targets and complete resolved
program/policy. Deterministic native revision/generation changes caused by these
operations are authenticated history. A material program change invalidates
inheritance and exposes the specific current individual decision; a changed or
invalid source guard refuses. The resolved primary PublishLocal program must
explicitly configure a nonempty environment key whose value equals
Consumption.sourceRoot exactly. Ambient environment, substring matches and caller
argv supply no root selection. Actual producer integration establishes that the
declared source is consumed.

R3. A typed ConfirmedFailureSuccessor is separate from the UNKNOWN-only qualified
successor. Its immutable version-2 `:qualification`, request, next target ordinal,
intent and result use the existing publication store. The latest actual target
attempt must still be the admitted previous genuine failure. Fixed source and
policies are checked before reservation. Repeated Advance recollects the same
result; generic ensure cannot spend a qualified grant. Successful later checks,
review and real ordinary closure do not repeat publication.

Genuine NotExecuted may retain its nextPreparation token. A later ordinary
Advance proves the entire child prefix consists of matching confirmed nonstarts,
reserves one child, and performs at most one preparation. The first started
terminal or uncertain outcome consumes that grant. Missing tokens, admission
rejection, partial reservation and UNKNOWN never imply a new attempt. Collectors
are read-only and follow only retained children. A new genuine nonzero successor
may expose a fresh PrimaryDecision requiring specific new authority. The combined
source decision never preapproves later failures or primary UNKNOWN. No validation
success or receipt is fabricated from publication failure, preparation or UNKNOWN.

## Complete public Resource inputs

The existing `recoverGoalPhasePrerequisitePublication`,
`describeGoalPhaseDependencySourceRevision` and
`adoptGoalPhaseDependencySourceRevision` Operations accept exactly one of `input`
or `inputResource`. Inline input retains its normal business shape. A Resource is
an absolute local file URI without authority/query/fragment containing the whole
same input record, including the complete target. The executing call performs
normal CNCF `read_resource_text` authorization, finite acquisition and UTF-8
decoding before strict business admission. Raw duplicate keys, alias collisions,
unknown fields and explicit nulls refuse. Denied, malformed, truncated or oversized
Resource reads retain their typed cause and perform no fallback or recovery.

Use the owner's normal installed host and `--inputResource
file:///absolute/path/complete-answer.json`. Do not put a large target into shell
argv or split/trim it. `textus.resource.read.max-bytes` must admit the complete
envelope, including policy expansion; measured input size is not an additional
application limit. Passive prepareEvidence keeps its existing request/result-file
path and existing evidence purposes. Prepared JSON needs independent trusted host
provisioning; it does not authenticate a Human answer.

Source capture generation advances once, repairs are not refunded, original
dependency UNKNOWN is never replayed, and all actual publication results remain
queryable. Consumer source integration, host configuration and real consumer
Session decisions/Advance are outside this producer repair. Once actual checks
start, their ordinary validation/FIX and selected review/closure owners apply.

The failed v1 contract below remains exact. An actual UNKNOWN prerequisite has
the separate [UNKNOWN continuation contract](goal-phase-prerequisite-publication-unknown-recovery.md)
through these same operations and distinct target/choices; it never satisfies
the v1 failure predicate.

This contract applies to an active ordinary Step at its unsuspended
`phase.Validation` candidate frontier. Its original dependency admission and
complete native capture must already exist, no check or metadata selection may
have started, and the exact prerequisite publication must be the target's latest
actual terminated nonzero result. A missing request, successful publication,
admission refusal, not-started execution, timeout or UNKNOWN is ineligible.
Whole-Phase and dependency source-direction recovery retain their own owners.

`GoalPhaseService.describeGoalPhasePrerequisitePublicationRecovery` accepts the
bare `--sessionId <actual-session>` business parameter. Same-purpose Session-only
descriptions use this flat Session parameter; structured target/answer judgments
use their declared typed `--input` or `--inputResource`. The old `--input`-only
description wrapper is not accepted. It returns the finite target and choices
`AdoptCorrectedSourceAndPublishOnce` and `Defer`. The target binds the actual
component, participant, Session, full reconstructed controller, Plan, original
base, accepted Steps, generation and repair progress; the complete old primary
candidate and stages; original dependency admission and actual accepted native
ancestry; fixed publication target/policy and immutable failed result; and a
managed File/Git guard for the corrected source. The index must still equal the
old captured dependency tree. At least one worktree correction must lie within
the same binding's selected paths; foreign dirty/index paths, root, branch, base,
workspace and scope changes refuse. The normal resolved process plan and Step
check policy are bound as opaque canonical sorted JSON.

The target's wire codec uses canonical sorted JSON strings for its opaque full
controller and actual process observation. Their internal keys and nulls remain
unchanged through public Core Record conversion. The other structured target
fields omit absent options. Unknown fields and noncanonical opaque strings
refuse; this does not change any older target or process codec.

An independently supplied Human answer is prepared with the normal
`NativeWorkflowPreparationService.prepareEvidence` kind
`goal-phase-prerequisite-publication-recovery`, family `GoalPhase`, and actual
description, answer, artifact reference, execution evidence, participant, issuer
and time. The trusted host independently provisions that evidence under the
dedicated purpose. Preparation does not provision records or grant authority.
Design approval, an AI answer, authentication and an old Human question are not
this independent evidence.

`GoalPhaseService.recoverGoalPhasePrerequisitePublication` accepts exactly
`sessionId`, `target`, `choice`, `rationale` and `reference` in its typed
`--input` or `--inputResource`. It re-observes the
actual current target and admits the exact independent target/answer binding.
`Defer` admits evidence and appends nothing. Adoption appends one mechanical
direction through the normal Advance operation with causation
`ordered-prerequisite-publication-recovery:<old-frontier>`. It remains at
Validation and performs no staging or publication. Plan, base, completed Steps,
generation and repair counters remain unchanged.

A separate ordinary Advance processes only the selected worktree corrections.
Each correction uses its original binding path ordinal (`goal-path-N`), with a
durable intent before Git staging and a result containing its exact guarded
`StageFact`. Completed original stages are retained. An intent without its
actual result is UNKNOWN and never replays. Every stage must preserve approved
worktree bytes and all unselected entries. The observation also has its own
intent/result and records actual incremental paths and diff against the real
accepted native prefix obtained from `DependencyChain.anchorC(binding)`.

The new dependency admission retains the same binding and approvals. It adds
that authentic accepted prefix when migrating an old absent-prefix admission;
the first Step still uses no prefix. The new dependency capture and candidate
use the actual direction record revision as their source revision. Original
primary selection, stages, snapshot, diff, history, continuation and hygiene
remain exact. The recovered capture has this domain object's mechanical proof
and no fabricated generic `dependencyOrigin`. Generic candidate retention still
authenticates the original candidate; its validators and dependency chain rules
are unchanged.

The walker authenticates the original historical cursor, immutable failed
request, adjacent native direction, every intent/result pair and the recovered
candidate before installing the effective candidate and admission. Historical
replay does not re-observe current source bytes; later publication catalog
timestamp output therefore does not change the retained recovery proof. Live
normal validation freshness remains mandatory.

Current process-policy compatibility is checked after those actual ordered
effects have been reconstructed. A matching policy retains the existing reading
behavior. A changed process can be read only for a complete correction with no
UNKNOWN effect and a canonical original terminated primary publication failure
at its reconstructed corrected candidate. The exact ordered dependency and
primary targets, publication store/collection, Session and participant remain
bound. A later successful successor does not erase that historical failure.
Incomplete or UNKNOWN corrections refuse changed grants; live correction
Advance still requires its original process policy. Reading completed history
grants no source adoption or new publication authority.

Before publication for this recovered candidate, normal validation preconditions
check primary and dependency freshness and the native chain. The ordinary
Step's owned paths establish selection eligibility; its retained selected paths
define the candidate's validation scope. Owned but unselected worktree changes
remain untouched. Selected unstaged changes, any unrelated index delta,
conflicts and cross-boundary renames still refuse, alongside exact candidate
HEAD, tree and index checks. The ordinary
prerequisite owner derives the successor request from the new candidate source
revision. It retains success, failure and UNKNOWN normally, so repeated Advance
does not repeat staging or publication. A new failed successor needs a new
actual correction target and independent Human answer. No retry flag, manual
request identity, new controller, generation refund or altered old capture is
introduced.

`GoalPhasePrerequisitePublicationRecoverySpec` supplies focused pure codec,
binding, process classification, started-check, adjacent native stage/observation
fact reconstruction and immutable prerequisite-retention evidence. Its separate
`session-only-description` group exercises the normal owning Operation: bare
`sessionId` reaches business admission for an absent native Start, while the old
wrapper and missing or nontext Session parameters refuse. Interrupted
intent chains refuse dispatch; failed and UNKNOWN successor publications retain
the same request under repeated ordinary prerequisite selection. The separate `@DoNotDiscover`
`NativeGoalPrerequisitePublicationRecoveryCliSpec` requires an explicitly
selected installed CLI and normal SBT. It uses tiny disposable projects,
specification-owned independent Human provisioning, a genuinely checked, reviewed
and closed first Step, and an actual later `publishLocal` prerequisite failure.
The existing specification legacy-completion producer omits the second admission's
prefix before candidate creation, while retaining the accepted first source tree.
The approved selected source correction satisfies the real publication prerequisite;
ordinary Advance captures its incremental diff against the first native closure,
publishes real compiled output and recollects without replay. It does not read or seed an actual consumer store,
copy the sm-workflow source tree or perform recursive publication. Parent-owned
generation, compilation, focused validation, installed qualification and
independent review establish acceptance. The separate lightweight installed E12
uses a fresh normal fixture without native Start or nested SBT: bare
`--sessionId` reaches the absent-Start business refusal, missing and old
`--input`-only shapes refuse, and an explicit authorization credential retains
the fixed-profile provider's 403. It preserves the earlier heavy E11 evidence.

# Canonical dependency publisher source alignment

The existing `describeGoalPhasePrerequisitePublicationRecovery` operation also
describes the strictly discriminated target
`sm-workflow.goal-phase-dependency-publication-source-alignment.v1`. It addresses
a literal dependency publisher whose typed Git root differs from the effective
authenticated canonical source. This is an applicability correction, with an
independently qualified existing artifact; no host program or permission is
changed. Every historical Published, failed and UNKNOWN result remains intact.

Description accepts exactly one transport:

- Legacy `--sessionId SESSION` for a Session-only description.
- `--input` with the complete `{sessionId, publicationObservation}` business
  record, or a Session-only business record.
- `--inputResource file:///absolute/path` for that same typed business record.

The shared Resource reader belongs to the executing ProcedureActionCall/UoW and
uses UTF-8 and the existing raw duplicate-key/null checks. Mixed transports,
unknown fields, explicit nulls and crossed schemas refuse; no CLI shell parser,
fallback selector or arbitrary input-size cap is added. Session-only mismatch
description returns its actual target and the required producer proposal, with
only `Defer`; it never invents a publication. A complete supplied observation
regenerates the exact target for an independent Human. Wrong root/Component or
stale source remains disclosed in `applicabilityIssues` and offers only `Defer`.

The optional `publicationObservation` reuses the full
`GoalPhasePublishedDependencySourceAdoption.PublicationObservation` contract:
normal successful collect, original producer owner/participant/target/workspace,
complete untruncated Exited-zero process, its actual program/process policy,
source, destination, artifact and recollection references. Producer and consumer
names may differ. Qualification checks the producer workspace's declared root
against the current canonical root, its Component against the dependency
Component and its honest request source against the fresh native guard. A
wrong-root success stays successful historical evidence and is inapplicable.
An older mutable SNAPSHOT or a class-presence observation is insufficient.

Available positive choice is `AdoptRecollectedPublishedDependencyArtifact`;
`Defer` is passive and saves no Direction. The existing recovery input and its
`--input`/`--inputResource` transports remain unchanged. Positive recovery
requires actual independent `Purpose.PrerequisitePublicationRecovery` evidence
for the full exact target/answer/participant/reference. Passive preparation
also binds the actual recollection and artifact references. A caller-supplied
record or fixture answer alone grants no native authority.

The existing private native walk must authenticate the unstarted ordinary
Validation candidate, complete accepted chain and canonical capture before
describing/adopting. Accepted Repair must first complete its normal candidate
capture through the owner's ordinary Advance; description never performs it.
Recovery appends only one adjacent mechanical Direction at the same Validation
stage using managed storage/CAS, preserving Plan/base/candidate/completed prefix,
all approvals, accepted results, counters and original check obligations. Its
retained-history branch revalidates the exact predecessor, source and causation.
Only that authenticated saved Direction can provide the private permit to
ordinary native validation.

The permit skips the precisely bound obsolete dependency publisher. It supplies
no new primary permission. Before primary effects the actual configured program
must still select the current source and all existing authority gates apply.
A later separate ordinary Advance owns primary publication, checks, review and
Step commit. Target/policy/source drift refuses; no program rewriting, target
mutation, owner impersonation, successful-effect replay or intent/UNKNOWN
clearing is supported. This repair producer never operates the real consumer.

Focused semantic examples live in the alignment and prerequisite specifications.
The existing small installed later-source fixture adds only the opt-in
RootLineage mode (requiring CoverageLineage and UsageLineage), using the same
physical PREPARE_OLD/RESUME_CURRENT locator with distinct old/canonical tiny Git
roots. Its retained results test truthful old publication, no effects before
artifact authority, missing Human/wrong-root/stale/crossed refusal, passive Defer,
ordinary continuation and Step2 closure to ReviewPhase. Fixture evidence does
not establish live Human consent, provider success or whole-Phase acceptance.
