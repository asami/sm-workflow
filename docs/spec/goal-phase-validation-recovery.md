# GoalPhase selected validation recovery

The distinct [validation repair adoption](goal-phase-validation-repair-adoption.md)
contract handles genuine output-limited incomplete results and narrowly qualified
historical unresolved whole-Phase Test intents. Existing recovery choices remain.
Caller termination is neither a failed native Test nor a confirmed timeout.
Explicit independent association/unknown-effects adoption appends one corrective
Step through the new contract, without amending or rerunning the old check.

Date: October 22, 2026. Implementation contracts: VR-SM-01, SBT-EXECUTION-RECOVERY-001,
P64-FORMAL-PHASE-FAILURE-01 and P65-TERMINATED-TIMEOUT-DEADLINE-RECOVERY
(explicitly authorized limited public continuation).

## Dependency subject (October 24, 2026; source extension)

Under [dependency validation](goal-phase-dependency-validation.md), DV-07, the
existing selected-check recovery target optionally includes the fixed binding
reference, dependency repository/workspace, base and candidate. The primary
Session/Plan/base/candidate/source/frontier/check/attempt/intent fields retain
their meaning. The genuine independent Human evidence binding includes this
complete subject. Another dependency, workspace or candidate is another target;
an old answer does not authorize it. Null or incomplete subjects refuse. Legacy
absence remains absence and asserts no dependency result.

Rejection, terminal NotStarted, actual failure and uncertain outcome retain their
existing distinct histories. Only existing explicit new-attempt direction can
authorize a new attempt for an unknown result. Recovery adds no new process
executor, success receipt or counter reset. Current grants and dependency
freshness are checked by the ordinary Advance owner. The executable source cases
in `GoalPhaseValidationRecoverySpec` and `GoalPhaseNativeDependencyValidationSpec`
exercise subject preservation and unknown-prefix non-replay; they are
specification-owned inputs, not genuine production Human consent.

An interrupted dependency candidate/staging capture is conservatively retained
and refused without replay. The existing primary candidate recovery descriptors
do not yet describe the new dependency stage prefix. Qualifying a public recovery
route for that prefix requires separately scoped work in its existing owner;
this extension must not pretend that the old primary-only target authorizes it.

GoalPhase retains the original selected check intent before invoking CNCF's owning
UnitOfWork. A missing result means its old outcome is unknown. A host configuration
change, a permission change, an error message, authentication, or a later successful
check does not establish what that old attempt did.

## Rules

### R1 — Compatible immutable facts

The existing `sm-workflow.goal-phase-native-validation.v2` payload keeps its old
`receipts` and `stopped` values. Optional vectors `admissionFailures`, `uncertainties`
and `newAttempts` default to empty when absent and are omitted when empty. No old
record is migrated or rewritten. Every later record retains every earlier fact as
a prefix, together with the original source, selected checks, metadata and candidate.

An admission failure contains `checkIdentity`, the zero-based `attempt`, and the
actual bounded `Conclusion.show` diagnostic. Uncertainty contains the same fields
in a separate vector. Neither is a process receipt, `not-started`, SBT
`NotExecuted`, check success or a semantic repair verdict. A new-attempt direction
contains the old check identity/attempt/intent revision, actual evidence identity
and revision, participant and rationale.

### R2 — CNCF owns observations

The thin Compile/Test/TestOnly attempt DSL runs in the actual owning UnitOfWork.
Only `SbtExecutionAttempt.Observed` is converted to the existing real SBT receipt.
`AdmissionRejected` records its diagnostic without inventing a process. A later
ordinary Advance may create the next attempt after host preparation correction.
`Uncertain` records its diagnostic and blocks automatic replay. A real observed
timeout, cancellation or unconfirmed termination retains its actual captured
text and explicit `timed-out`, `cancelled` or `termination-unconfirmed` receipt
with no exit code and Incomplete SBT facts. Historical `indeterminate` receipts
remain readable. These outcomes stay at Decision without another check,
automatic replay or repair-count increment; only Exited nonzero remains a
repairable product failure. No outcome silently becomes success or safe replay.

Application code supplies no executable, argument vector or generic Process
fallback. CNCF's owning UnitOfWork executes the existing local process driver
under ordinary V1–V3 typed process grants. It owns launch, finite waiting,
termination and result capture. The former V4 manual registered-host rendezvous
is retired; no per-check AI runner, response file or execution script is needed.
Actual `[cncf-sbt-event]` JSON stderr observations expose process activity without
changing the Workflow frontier or granting acceptance. This document and the
isolated fixtures alone do not establish installed execution.

Product SBT runs through `WorkflowManagedProcessExecution`, typed `sbt_*` intents,
the owning CNCF UnitOfWork, the fixed SBT adapter and `LocalProcessExecutionDriver`.
That runtime owns launch, waiting, result capture and terminal handle cleanup.
The normal product path has no AI SBT worker or agent handoff. Development-tool
command runners are a separate workflow and supply no product execution receipt.

The ordinary caller has no execution cutoff or telemetry-derived deadline. It
waits for the actual response with programmatic monitoring/heartbeat. Explicit
SIGTERM requests termination once from the exact owned runtime; runtime closes
launch admission, settles managed descendants and saves actual result/captures
and Workflow interruption state before storage/resource shutdown and exit.
Only existing runtime configuration owns grace/escalation. There is no caller
kill-group fallback or new SIGINT handback protocol. Missing response or
unconfirmed termination stays unknown without acceptance or automatic replay.
See the [product SBT lifecycle](native-host-execution.md#product-sbt-lifecycle).

### R7 — Bounded actual NotStarted recovery

For each selected check in one ordinary Advance, the product runtime permits at
most one extra fresh dispatch after an actual observed NotStarted result. It
first saves the original intent and exact actual result, acknowledges recording
and records evidence. Eligibility comes from the saved new stopped receipt:
`not-started`, no exit code, empty stdout and stderr, and valid SBT `NotExecuted`
facts for the same selected check. Absence, attempt counts and diagnostic prose
establish no such fact.

Before the extra dispatch, the runtime reconciles immutable Store history and
checks the current preconditions. The existing owning UnitOfWork re-admits the
same fixed check under current authority; its next ordinal comes from Store
reconciliation. A new intent precedes the new effect, and the original receipt,
metadata and candidate remain prefixes. The original stopped process handle
stays terminal. CNCF owns any late-launch cleanup. No registry, generic process
fallback, permission renewal or host configuration repair is implied.

A second actual NotStarted returns Wait with both attempts retained. A later
ordinary Advance retains its existing eligible resumption behavior, with the
same bound applied anew. Saved success skips execution. Missing or unresolved
results, typed uncertainty, admission rejection, LaunchFailed, timeout,
cancellation, unconfirmed termination and ordinary completed failure never
select the automatic extra dispatch. Launch timeout remains LaunchFailed and
cannot be reclassified as NotStarted. Existing failure/repair policy still
applies to completed checks.

Unavailable preparation or authority remains a typed stop. Correcting it does
not establish an old outcome or grant new authority. An unresolved UNKNOWN
attempt requires the independent HumanNewAttempt direction in R3–R6; that
direction is distinct from runtime recovery of a proven actual NotStarted.

### R3 — Explicit HumanNewAttempt direction

Exactly one explicit `AuthorizeNewAttempt` direction may follow an actual
result-less intent or its actual saved uncertainty result. It uses the existing
native mechanical transition and the key
`check-recovery-<checkIndex>[-retry-<attempt>]`. No old result is fabricated.
Contiguous native intent/result/recovery history must validate before a new
attempt is continuable. The next ordinary Advance creates attempt `old + 1`.

No duplicate authorization, skipped attempt, stale frontier, crossed Session,
Plan, base, candidate, selected check or metadata is admitted. Recovery retains
the original Plan, controller, history, candidate and repair counts; it does not
renew a repair window. Legacy saved `NotExecuted` attempts retain their existing
continuation behavior. Partial and unknown effects remain non-replayable without
the explicit supported direction.

### R4 — Exact public target and independent Human proof

The normal generated `GoalPhaseService.describeGoalPhaseValidationRecovery`
Operation takes `sessionId`. It reads actual native history without mutation,
execution, evidence provisioning or authority acquisition. Only the actual paused
Validation or PhaseValidation with a saved unresolved selected attempt qualifies
for the existing unknown path. R8 separately admits a known failed whole-Phase
Decision; R10 admits a confirmed terminated timeout with incomplete SBT facts.
It restores the original Validation source through `payload.sourceRevision` and
the admitted native history prefix, rather than treating the latest intent as a
new Validation source.

The generated description returns `outcome: "decision-required"`,
`recoveryOperation: "recoverGoalPhaseValidation"`, the actual component instance,
choices exactly `["AuthorizeNewAttempt", "Defer"]` for unknown and known-failed
targets, or `["AuthorizeNewAttempt", "LimitedRepair", "Defer"]` for a confirmed
terminated timeout target, and a complete typed target:

```text
sessionId, planIdentity, planRevision, base:{head,tree}, candidate:{head,tree},
sourceRevision, frontierRevision, step, checkIdentity, checkOrdinal, attempt,
intentRevision
```

`recoverGoalPhaseValidation` takes one generated `input` value containing
`sessionId`, that exact `target` record, `choice`, `rationale` and
`reference:{identity,revision}`. It routes the current owning call to the selected
canonical Session and verifies the actual participant. Missing providers and
missing/crossed/stale evidence refuse. Every choice requires independent host Human
evidence. The target is compared as complete typed data; it is not an opaque
approval token or a client-selected stage.

The strict selected binding is:

```json
{
  "componentInstance": "<actual description value>",
  "validationRecovery": {
    "target": "<the complete target object, not this placeholder string>",
    "answer": {
      "choice": "AuthorizeNewAttempt",
      "rationale": "<actual Human rationale>",
      "participant": "<actual authenticated submitting participant>",
      "reference": {"identity": "<actual answer artifact>", "revision": "<actual revision>"}
    }
  }
}
```

The evidence purpose is `goal-phase-validation-recovery`. Selection consumes only
declared typed fields and validates the choice against the actual decoded target.
`LimitedRepair` refuses for every non-timeout target. Timeout bindings remain
strictly canonical, including all answer fields. The owning reader verifies
the issuer, purpose, complete binding, nonfuture event time, actual participant
and actual reference. Unknown annotations carry no authority.

### R5 — Passive preparation grants nothing

The existing normal `NativeHostPreparationService.prepareEvidence` also accepts
family `GoalPhase`, kind `validation-recovery`, and required `participant`. Its
`request` file is the actual generated recovery description; its `result` file
is a generated `GoalPhaseValidationRecoveryAnswer` containing `choice` and
`rationale`. Supply actual reference, execution evidence, issuer and event time
through the existing absolute local file URI inputs. The execution worker must
equal the participant. Preparation returns existing structured StartupImport
rows; it performs no import, storage provisioning, recovery, Advance or check.
Preparation decodes the supplied target and requires the complete ordered
description choices to equal `choicesFor(target)`. A supplied description cannot
broaden legacy choices or omit, reorder or add timeout choices.

The trusted host remains responsible for obtaining genuine Human direction and
independently provisioning its evidence. An AI-created answer, login identity,
supplied description, supplied files or successful preparation is not Human
consent. The API accepts HumanNewAttempt direction for the exact described
unknown target, the distinct known failed whole-Phase target in R8, or the
confirmed terminated timeout target in R10. Historical
provider-observation mode is unsupported; stderr prose, absent results and config
edits cannot be imported as `NotExecuted` or any other execution fact.

### R6 — Owning-chat continuation sequence

Use the existing canonical Session ID. Do not call Start again or resubmit the
already accepted implementation. The owning chat follows this exact sequence:

1. Call normal `GoalPhaseService.describeGoalPhaseValidationRecovery` with
   `{"sessionId":"<existing Session ID>"}`. Retain the actual typed response and
   complete target unchanged. If it refuses, do not invent a target or authorize
   a different frontier.
2. Present the actual description. For the legacy unknown path ask the Human: “The old selected check
   outcome remains unknown. Do you authorize a NEW attempt of this SAME fixed
   selected SBT check using current host permissions, accepting that it may
   repeat effects of the old attempt, or do you Defer? Original native history,
   Plan, base, candidate and repair counters will remain retained.” Record the
   genuine choice and rationale through the independent trusted host channel.
   For a `knownFailedPhase` target, say the whole-Phase check is KNOWN FAILED
   with its actual terminated nonzero exit, and ask whether to authorize one
   fresh trial of the same single check on the exact retained candidate or Defer.
   Never describe that known failure as an unknown outcome.
   For a `terminatedTimeoutPhase` target, say the actual check terminated with a
   confirmed timeout and incomplete SBT facts. Present exactly three options:
   `AuthorizeNewAttempt` (deadline extension), `LimitedRepair`, and `Defer`.
   Deadline extension authorizes one NEW attempt of the same check on the same
   candidate; the normal host deadline must actually be adjusted before separate
   Advance. Authorization does not change the configured value. Limited repair
   authorizes known-error repair and bounded investigation/preparation, including
   evidence-based investigation/debugging of a suspected hang as specified in
   R10. Defer permits deeper investigation without retry. Preserve the old
   incomplete receipt/output and all Plan/base/accepted-prefix/counter facts;
   no corrective Step is required merely to extend the deadline.
3. Have that trusted host prepare and independently provision evidence for the
   exact component instance, target, participant, choice, rationale and actual
   answer reference. Passive `prepareEvidence` alone is not provisioning or
   authorization.
4. Call normal `GoalPhaseService.recoverGoalPhaseValidation` with the generated
   `input` value. Copy the exact description target; supply the genuine choice,
   rationale and reference. `AuthorizeNewAttempt` returns accepted and
   `new-attempt-authorized` after appending one record. This call executes no
   check, invokes no Advance and performs no semantic Submit. `Defer` returns
   accepted and `deferred` without inventing an outcome or granting a retry.
   Timeout-only `LimitedRepair` returns accepted and
   `limited-repair-preparation-required` without a history write or retry
   Direction. Diagnose/plan, then call `describeGoalPhaseFailedPhaseRepair` with
   a captured concrete proposal and independently authorize
   `adoptGoalPhaseFailedPhaseRepair`. This selection does not adopt an unknown
   future Plan or establish check success. Only the adopted Plan issues normal
   corrective work on separate Advance.
5. After accepted `AuthorizeNewAttempt`, call normal `GoalPhaseService.advanceGoalPhase`
   once with the same `sessionId`. That call owns the fresh selected attempt
   under current host permissions. A passing Compile/check result still leaves
   actual independent review and ordinary acceptance/closure unfinished. A new
   admission failure retains its diagnostic; a new unknown outcome requires its
   own exact independent decision. No earlier authorization carries forward.

Normal command selectors are
`sm-workflow.GoalPhaseService.describeGoalPhaseValidationRecovery`,
`sm-workflow.GoalPhaseService.recoverGoalPhaseValidation` and
`sm-workflow.GoalPhaseService.advanceGoalPhase`. The first and last accept named
`--sessionId`; recovery accepts named `--input` containing the generated business
JSON. They use the normal installed CLI/CncfMain, generated help, service metadata
and the normal MCP publication set. They introduce no custom main or native-store
editing endpoint.

## Executable examples and limits

### R8 — Distinct known failed whole-Phase target

The existing describe/prepare/recover Operations also admit one narrowly bounded
known failure. All Steps must already be closed, the controller must be at
`Decision` sourced by `PhaseValidation`, and the unchanged Plan must select exactly
one whole-Phase check. Its actual retained receipt must be typed SBT `Failed`,
terminated `exited` with a genuine nonzero exit, on the same inspected candidate.
The selected terminal attempt must have its actual intent and completed result,
followed immediately by the exact final failed validation receipt at Decision.
The source namespace is restored from that receipt's original `sourceRevision`.
Every prior attempt is loaded by its exact key and reconciled by the unchanged
ordinary validation attempt verifier. Prior stopped, admission-failure, uncertainty
or independently authorized unknown-attempt facts may remain only where that
verifier already admits the complete contiguous chain. Nonempty vectors or attempt
counts alone supply no proof. A missing, corrupt, crossed or duplicate predecessor,
unresolved terminal attempt, or recovery after the final completed result refuses.
No additional prefix transition vocabulary is introduced.
Step failures, PhaseReview decisions, document conflicts, pending repair, closure,
changed Plan/candidate/check policy, multiple checks, missing results, timeout,
cancellation, indeterminate/unexecuted/contradictory receipts and generation overflow
refuse. A successful sibling check is never rerun to recover a multi-check policy.

The R8 `knownFailedPhase` representation continues to exclude timeout and
incomplete observations. R10 provides a distinct same-candidate recovery target
for confirmed `TimedOut`/typed `incomplete`. A diagnosed product defect can instead
use the optional corrective-Plan route specified in
[failed whole-Phase repair adoption R8](goal-phase-failed-phase-repair-adoption.md).
It uses the existing describe/adopt failed Phase repair Operations, an explicit
`sm-workflow.goal-phase-failed-phase-repair-adoption.v2` source with
`kind: "terminated-timeout"`, a newly bound actual Human direction, and exactly
one dependent corrective Step followed by ordinary validation on its candidate.
It neither constructs KnownFailedPhase nor retries the unchanged candidate.

The legacy target encoding and meaning remain unchanged. A new optional
`knownFailedPhase` object is omitted when absent; absence decodes as legacy.
Explicit null, malformed, incomplete or unknown extension fields reject. Its
`schemaVersion` is `sm-workflow.goal-phase-known-failed-validation-target.v1`.
It binds `definitionIdentity`, `definitionRevision`, authenticated `participant`,
the original final validation `failedReceiptIdentity` and its actual
`failedReceiptRevision`, and actual integer nonzero `exitCode`. Existing target
fields still bind Session, Plan, base, candidate, original source, current Decision
frontier, scope, check identity/ordinal, actual terminal attempt and its own intent
revision. Thus UNKNOWN0/R6 → UNKNOWN1/R6 → UNKNOWN2/R6 → failed result3 binds
attempt `3` and intent3, never the earliest unknown intent. Those historical missing
results remain unknown; neither their R6 directions nor this new known-failed
direction settles their outcomes.
The complete extended target participates in the existing independent Human
evidence binding. Description and passive preparation perform no transition or
check; AI prose and authenticated login alone are never Human evidence.

### R9 — Separate durable direction and fresh validation round

After exact independent Human evidence admission, `AuthorizeNewAttempt` appends
one closed v1 direction payload using the existing mechanical transition/CAS,
fixed Advance operation and namespace
`<session>:ordered-known-failed-phase-validation-recovery:<DecisionRevision>@1`.
It carries the complete target and actual Human reference, participant and
rationale. Defer writes no recovery record and grants no effect. Duplicate,
stale or crossed targets fail. Ordinary Advance without a new direction remains
paused at the saved failure.
The direction must be NEW independent Human evidence for this exact latest real
failure. Earlier R6 evidence authorizes only its own subsequent attempt and cannot
authorize a trial after the later known failure, even if its input is retargeted.

The native owner applies `PhaseValidationRetryAdmitted` with the exact retained
snapshot/check vector, failed receipt reference and actual direction reference.
This pure event proves eligibility only; it never proves consent. The CML
`phase-validation-retry-admitted` transition selects `PhaseValidation`, clears
current failed check/review/decision acceptance and closure projections, and
increments the work generation exactly once. It preserves Plan, base, candidate,
tip, all completed Steps, repository receipt, both repair counters and limit.
It does not reopen implementation, assign repair, refund counts or fabricate review.

Native traversal loads the actual separate direction after its contiguous original
Decision predecessor, validates it against the original failed validation cursor
using the same complete terminal-attempt proof as describe and recover,
replays the pure event and retains the native history prefix. It clears only the
current cursor's validation projection for the new round. The fresh check uses
the newly admitted PhaseValidation source revision and the ordinary fixed
validation store, intent and typed UnitOfWork path. Before any effect it rechecks
current candidate/tree/workspace, authority and declared policy. Original validation
payloads/results and selected metadata remain untouched in their original source
namespace, including all already admitted R6 records and missing historical results;
no unknown recovery is appended there by the known-failed direction. Outcomes follow ordinary
PhaseReview and closure rules. No new registry, lock, outbox or mutable-store edit
is introduced.
This is a new validation round on the retained candidate, with only the existing
reconciler's supported prefixes. It provides no broader restart, repair, reset,
historical outcome settlement or successful-check replay authority.

### R10 — Confirmed terminated timeout and three recovery choices

The existing public describe/prepare/recover Operations admit an optional
`terminatedTimeoutPhase` target extension. Absence is omitted on the wire and
preserves every legacy constructor and unknown, dependency and known-failed
meaning. A present extension is the complete closed
`GoalPhaseInterruptedValidationRepair.Source`, with `kind: "terminated-timeout"`,
actual typed `termination: "timed-out"` and `outcome: "incomplete"`. It binds the
actual workflow and participant, Session/Plan/base/candidate, original validation
source and current Decision revisions, unique check and terminal attempt, and
actual intent/result/final validation receipt identities and revisions. Every
shared outer target field must equal that source. It cannot coexist with
`knownFailedPhase` or `dependency`. Null, missing, unknown, malformed, noninteger,
crossed or noncanonical fields refuse. Complete 40- or 64-character lowercase
hexadecimal Git identities remain supported.

Eligibility uses the same actual terminal-source proof as corrective adoption:
an unsuspended current final PhaseValidation Decision, all Steps accepted, one
whole-Phase check, unchanged actual selection/candidate/metadata, complete
contiguous ordinary attempt history and adjacent terminal result/final receipt.
Actual document-backed state is checked. Unknown, not-started, cancelled,
unconfirmed termination, other incomplete receipts, multi-check, Step, pending
repair, stale or crossed sources cannot supply this timeout authority. Ordinary
`repairable` still requires a genuine exited nonzero Failed result. Diagnostics
are retained as output; they do not turn a timeout into Failed or success.

The complete extension participates in the existing
`goal-phase-validation-recovery` purpose, exact participant/reference/rationale
binding and independent Human evidence. Unknown and known-failed targets retain
exactly `AuthorizeNewAttempt` and `Defer`. Only the confirmed timeout target
offers exactly `AuthorizeNewAttempt`, `LimitedRepair`, `Defer`, in that order.
Binding, selection, public operation input and passive preparation validate
choices against the actual decoded target; preparation requires the description's
complete choices to match that target exactly. Description and passive preparation
grant no effect; Defer writes nothing and permits deeper investigation without
retry. `AuthorizeNewAttempt`, labeled deadline extension for the Human, retains its
existing wire meaning and appends one pure Unit CAS
Direction, with type `GoalPhaseTerminatedTimeoutValidationRecoveryDirection`,
schema `sm-workflow.goal-phase-terminated-timeout-validation-recovery.v1` and
namespace
`<session>:ordered-terminated-timeout-validation-recovery:<DecisionRevision>@1`.
It never uses the known-failed or unresolved recovery direction namespace.
Duplicate, stale, crossed or earlier-choice evidence refuses.

After exact current-target reconstruction and independent Human admission,
`LimitedRepair` returns the existing result DTO with `accepted: true` and
`outcome: "limited-repair-preparation-required"`. Its scope is to fix known errors;
identify the test actually executing at termination from actual start/end
observations, retained output and, when needed, diagnostic stack/process evidence;
and investigate/debug a suspected hang and fix established causes. Timeout alone
is not proof of a hang. The last printed test may already have completed; report
test identity unknown or evidence missing honestly. Investigation may establish
normal slow execution or an external wait rather than deadlock. CNCF retains
ownership of execution and result interpretation. No app regex parser or fabricated
current-test identity supplies evidence.

LimitedRepair authorizes bounded investigation and preparation only. Its response
requests diagnosis/planning and directs the caller to existing
`describeGoalPhaseFailedPhaseRepair` with a captured concrete proposal, followed
by independently authorized `adoptGoalPhaseFailedPhaseRepair`. Only an adopted
Plan issues the normal corrective Step through separate ordinary Advance.
Selection neither adopts an unknown future Plan nor grants retry or check success.
It performs no Store/history/Plan/counter/Git/SBT mutation and appends no retry
Direction or Decision-to-Decision event. The original Decision stays current,
preserving existing interrupted-target proof and all original receipts/output.
There is no duplicate repair executor or new result field.

The existing `PhaseValidationRetryAdmitted` event and
`phase-validation-retry-admitted` edge select PhaseValidation and increase only
work generation by one. Plan identity/revision, base, candidate, accepted Steps,
repository receipt, repair counters and repair limit remain unchanged. Retained
traversal re-proves the actual historical source, exact participant/direction and
adjacent CAS successor. It clears the current validation/decision projections
for a fresh namespace while retaining the original result, captures and history.
The recovery call performs no File/Git/SBT, Start, Submit or Advance effect and
issues no corrective Step. Generic Decision resumption remains unchanged.

A SEPARATE ordinary Advance runs the same fixed check on the same candidate
under current normal CNCF host configuration and permissions. The existing
`.textus/config.json` key `textus.subsystem.process-execution.plan` contains a
JSON STRING. Its decoded `sbtPrograms` selects the exact program by
target/identity/operation. A host may extend that program's
`limits.executionTimeoutMillis`, a positive finite Long in milliseconds, while
preserving every other program and field and the complete finite execution
limits. CNCF admission and its driver own interpretation and deadline enforcement;
sm-workflow adds no production config parser, new property, timer or process
fallback. For deadline extension, the normal host deadline must actually be
adjusted before separate Advance; authorization does not change its configured
value. Config editing alone grants no retry or historical outcome. The new
attempt can genuinely succeed, fail or time out, and retains its own actual
receipt. Deadline-only recovery requires no fabricated source change, corrective
Step, new commit or repair-count consumption. A separately diagnosed defect may
still use the independently authorized corrective route.

E15 (`GoalPhaseTerminatedTimeoutValidationRecoverySpec`) supplies typed
specification-owned source and target data for strict 40/64 Git identity, complete
extension/direction and passive-preparation bindings, three timeout choices and
honest suspected-hang investigation scope. Missing, null, unknown,
crossed, ambiguous, noninteger, participant/reference and altered bindings refuse;
legacy unknown and known-failed wire meanings and two-choice preparation remain;
LimitedRepair and broadened supplied descriptions refuse for those targets.
These inputs claim no
production termination attestation.

The installed E15 in `NativeGoalKnownFailedPhaseValidationCliSpec`, named with
`timeout recovery choices` and `deadline extension`, uses a NEW root marked `deadline-only-timeout-fixture`.
Before initialization its outside-repository phase-ready input makes the real
ProductCheck succeed. Ordinary implementation, real Compile, independent fixture
review and actual local Step commit remain. The marker-scoped build wraps the
actual Test result, prints and flushes a completion marker, waits finitely for
150000 milliseconds, then returns that same result. Initial Test limit is
120000 milliseconds; Compile retains 1200000 milliseconds. The first installed
Advance must retain an actual TimedOut/incomplete receipt during the wait.
The description exposes all three choices. Actual passive preparation/import and
independent LimitedRepair admission return `limited-repair-preparation-required`
with diagnosis/proposal/adoption guidance and no native state, source, Git or check
execution change. The completed-Test marker demonstrates why the last output
cannot identify a still-running test; the fixture claims no hang diagnosis.
No-direction and Defer execute nothing; missing, crossed and new-null targets
refuse. Existing normal passive preparation supplies independently provisioned
fixture-owned Human evidence for the exact new target.

Only the selected Test program's configured deadline changes, from 120000 to
300000 milliseconds. The fixture asserts the existing JSON-string plan preserves
every other program and field, while source/build/phase-ready bytes, Git tip/tree/
reflog, Plan/revision/base/accepted prefix and counters remain identical. One
public recovery Direction executes no check. A separate installed Advance runs
the same real Test and finite wait to ReviewPhase, without ImplementStep,
corrective source changes or a new commit. Repeated Advance is stable and exactly
two Phase Tests run. The original incomplete receipt/history remain unchanged;
the new receipt uses a fresh validation namespace. Existing installed manifest
and runtime class/resource origins are captured, together with
`deadline-only-timeout-reopened-proof.json` under the isolated fixture root.
These are qualification assertions, not a claim that the opt-in case has run or
passed, production Human provenance, or permission to operate a real Session.

E10 (`GoalPhaseOrderedProgressionSpec`) covers pure eligibility, exact retained
facts, counters, new generation and crossed/repeated-event refusal. E11
(`GoalPhaseValidationRecoverySpec`) covers old-wire omission/defaults, strict new
extension decoding and complete independent bindings. E12
(`GoalPhaseValidationRecoveryOperationSpec`) creates a new real Compile Step,
independent fixture review/local commit and genuine failed Phase Test, then checks
missing/crossed/stale evidence, Defer, multi-check refusal and reopened history.

E13 (`NativeGoalKnownFailedPhaseValidationCliSpec`) is `@DoNotDiscover` and requires
absolute `smworkflow.spec.installedCommand`, `smworkflow.spec.runtimeRoot` and
`smworkflow.spec.sbt`. It creates a NEW isolated root, completes ordinary Compile,
independent fixture Step review and actual local Step commit, then observes one
real failing whole-Phase Test. A test-owned source reads an external fixture-owned
readiness marker; changing that marker does not edit the candidate. Installed
describe/prepare/recover independently bind test-owned Human direction. No-direction
and Defer continuations do not rerun Test; authorization alone executes nothing.
One later installed Advance runs one fresh real Test and reaches normal PhaseReview,
preserving the original failed receipt, Session/Plan/base/closed Step/counters.
The case requires exactly two Phase Test markers, actual typed exit/outcome and
runtime class/resource origins in the selected installed generation. Fresh
Continuation does not repeat the successful Test. These are required executable
assertions, not a claim that this opt-in qualification has run or passed. It
supplies no production Human provenance or real Phase Session operation.

E14 adds the combined UNKNOWN0/R6 → UNKNOWN1/R6 → UNKNOWN2/R6 → actual failed
result3 history. The pure chain case in `GoalPhaseValidationRecoverySpec` retains
the exact three authorizations and fourth pair, and rejects missing, crossed,
duplicated, rewritten or noncontiguous prefixes and unresolved/non-Failed terminal
outcomes. The ordinary `GoalPhaseValidationRecoveryOperationSpec` case completes
real Compile, independent fixture Step review and actual local Step commit, then
uses the explicitly test-owned normal-Operation acknowledgement hook immediately
after each Phase intent and before any Phase SBT effect. Separate fixture-owned
R6 evidence authorizes each next attempt. The fourth actual Test fails. Description
binds attempt3 and its own intent; missing, stale, wrong-ordinal, old-R6 and crossed
Human/candidate/Plan proofs refuse. Defer grants nothing. A NEW exact known-failed
direction appends once, and changing only external readiness lets ordinary Advance
run one fresh passing Test to PhaseReview. Reopening retains Session, Plan, base,
candidate, closed Step, repair counts and every old intent/recovery/result payload.

The separate E14 case in `NativeGoalKnownFailedPhaseValidationCliSpec` uses the same
opt-in absolute properties and a NEW isolated root, preserving E13. Initial
UNKNOWN0/1/2 creation is explicitly mixed preparation through the existing normal
Operation acknowledgement hook, not an installed-CLI fault effect. Every following
R6 describe/prepare/recover uses fresh installed public-command processes and
separate fixture-owned evidence. The fourth failed Test, known-failed
describe/prepare/recover, and fresh passing trial use the actual installed
executable and normal typed UoW SBT. Assertions require zero Phase Tests during
the injected unknowns, exactly two actual Phase Tests in total, pinned installed
class/resource origins, immutable old payloads, missing old results, retained R6
records, exact attempt3 targeting, no effect from Defer/authorization alone, and
duplicate recover/repeated Advance refusal or no repeat. This scenario neither
rewrites SQLite/history nor adds a product fault option or helper main. These
assertions are qualification requirements; their addition does not claim a passed
run or production Human provenance.

`GoalPhaseValidationRecoverySpec` covers old-wire compatibility, exact retained
admission/uncertainty facts, immutable recovery adjacency, skipped/duplicate/stale
chains and strict independent evidence binding. Its constructed data represents
test inputs rather than actual provider observations.

Its E7 cases execute the exact production single-check orchestration with
controlled Store and typed-UoW effect callbacks. They cover NotStarted followed
by success, two NotStarted stops, later-invocation ordinals, precondition refusal
before a fresh intent, successful/unresolved prefixes and unsafe outcome classes,
including nonempty captures accompanying NotStarted. Dispatch count, check
identity, persistence order and immutable attempt prefixes are observed. These
are controlled startup regressions, not genuine observed production failures or
complete infrastructure acceptance. Real normal installed CLI SBT execution and
the combined source-history recovery scenario remain separate parent-owned
verification.

`GoalPhaseValidationRecoveryOperationSpec` uses isolated repositories and normal
Operations. A real missing workspace or program reaches the owning UoW's admission
check; no application process receipt is synthesized. Host correction permits a
fresh ordinary attempt. Its unknown fixture uses an explicitly test-owned
acknowledgement failure immediately after actual intent commit and before effect.
The same Session survives reopen, denies missing/crossed/stale proof, preserves
Defer, appends genuine test-owned authorization and reaches pending independent
review after a real normal Compile with zero repair-count change.

`NativeGoalValidationRecoveryCliSpec` uses separate ordinary CncfMain processes
for description, passive preparation, recovery and progression. It retains the
actual generated description and caller Session route. No private native row is
patched, and no `NotExecuted` receipt is manufactured. Test-owned Human evidence
does not claim independent production Human provenance.
Repeated fresh-process Advance calls preserve the unknown intent before Human
direction; a later Advance at pending review does not rerun successful Compile.

The preparation-only normal-platform scenarios create new isolated roots and
stop after test-owned authorization, before any next Advance or SBT. They use
the ordinary existing host policy: the passing fixture selects distinct ordered
`compile` and `compile-second` checks with two ordinary Compile targets; the
nonzero fixture selects one Compile against deliberately ill-typed generated
Scala. The source-owned build generator records its actual process ID each time
Compile runs. Fixture preparation claims no process result.

After the selected preparation spec, the non-authoritative pointer
`target/sm-workflow/work/validation-recovery/latest-platform-episode.json` supplies
the newly generated root, Session ID, task route, description, authorization,
recovery input, normal config/assembly/database paths and expected check order.
The separate nonzero root is selected by `latest-platform-failure-episode.json`.
Both retain the original accepted test-owned semantic work and unknown old
intent. The pointer is metadata, never authority; the independently provisioned
test-owned direction and native recovery record remain the actual fixture inputs.
The full historical Compile episode keeps `latest-cli-episode.json` separately.
Older fixture roots and the actual feature Session are never reused or advanced
by this preparation.

The parent selects `runtimeTestOnly
org.simplemodeling.textus.smworkflow.admission.NativeGoalValidationRecoveryCliSpec
-- -z "prepare a fresh authorized normal-platform"` to prepare both new roots.
It then invokes the normal installed `sm-workflow --host <new-root>
sm-workflow.GoalPhaseService.advanceGoalPhase --sessionId <new-session> --json`
once per separately selected fixture, outside the enclosing SBT invocation.
No component development directory, source classpath override or per-check
execution helper is used for this installed progression. The passing result
must reach `Request` / `ReviewStep` with two actual ordered Compile receipts;
the nonzero result must retain its real compiler diagnostic and unsuccessful
receipt. Neither preparation nor process success supplies independent review
or Workflow acceptance.

`NativeGoalStartupRecoveryCliSpec` adds explicitly selected installed-CLI
examples E8 and E9 for SBT-EXECUTION-RECOVERY-001. Both require the absolute
`smworkflow.spec.installedCommand` executable and the normal actual
`smworkflow.spec.sbt`; every Advance under test uses the installed executable,
`--host` and the same fresh fixture Session. Initial test-owned Start and
implementation submission use the existing ordinary Operation fixture. Actual
caller PIDs, stdout and stderr remain beneath each isolated fixture root.
These examples are evidence requirements; adding them does not claim a passing
installed run or acceptance.

E8 uses two ordered Compile checks. A regular private copy of the admitted SBT
executable and its adjacent launcher JAR supplies only the second binding. The
first actual Compile's once-only source generator removes execution permission
from that owned copy after bootstrap. The installed Advance must save the first
success and two actual `not-started` stopped receipts, including the single
bounded extra dispatch, and return Wait with no repair increment. Actual CNCF
terminal events use `NotStarted` and supply neither PID nor exit code for those
stops. Restoring only the copy's original permissions allows a fresh same-Session
Advance to append the next ordinal and run the second actual Compile. First
success, stopped receipts, selected checks, candidate, Plan, base and history
remain retained; another fresh Advance observes pending ReviewStep without a
third Compile. Prelaunch NotStarted submits no launcher task. This establishes
no distributed late-worker fencing guarantee.

E9 holds one actual Compile behind a target-only source-generator gate for at
most 120 seconds, after writing its actual PID and readiness marker. While that
old execution is running, a second installed Advance must return unresolved
Wait without another intent, result, process, repair or replay. The original
intent remains retained. Gate release precedes cleanup on every exit; caller
waiting is bounded at 240 seconds and normal SBT at 180 seconds. Cleanup tracks
only the caller's own process descendants. The exact original process must
eventually return pending ReviewStep and save the sole actual result. A later
Advance preserves that result and the accepted implementation, Plan, base,
candidate, history and zero repair counts. Neither example supplies independent
review, closure, production Human provenance or Workflow acceptance.
