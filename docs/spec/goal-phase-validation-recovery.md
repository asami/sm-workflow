# GoalPhase selected validation recovery

Date: October 20, 2026. Implementation contract: VR-SM-01.

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

The ordinary caller has no execution cutoff or telemetry-derived deadline. It
waits for the actual response with programmatic monitoring/heartbeat. Explicit
SIGTERM requests termination once from the exact owned runtime; runtime closes
launch admission, settles managed descendants and saves actual result/captures
and Workflow interruption state before storage/resource shutdown and exit.
Only existing runtime configuration owns grace/escalation. There is no caller
kill-group fallback or new SIGINT handback protocol. Missing response or
unconfirmed termination stays unknown without acceptance or automatic replay.
See the [product SBT lifecycle](native-host-execution.md#product-sbt-lifecycle).

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
Validation or PhaseValidation with a saved unresolved selected attempt qualifies.
It restores the original Validation source through `payload.sourceRevision` and
the admitted native history prefix, rather than treating the latest intent as a
new Validation source.

The generated description returns `outcome: "decision-required"`,
`recoveryOperation: "recoverGoalPhaseValidation"`, the actual component instance,
choices exactly `["AuthorizeNewAttempt", "Defer"]`, and a complete typed target:

```text
sessionId, planIdentity, planRevision, base:{head,tree}, candidate:{head,tree},
sourceRevision, frontierRevision, step, checkIdentity, checkOrdinal, attempt,
intentRevision
```

`recoverGoalPhaseValidation` takes one generated `input` value containing
`sessionId`, that exact `target` record, `choice`, `rationale` and
`reference:{identity,revision}`. It routes the current owning call to the selected
canonical Session and verifies the actual participant. Missing providers and
missing/crossed/stale evidence refuse. Both choices require independent host Human
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
declared typed fields and validates the declared choice. The owning reader verifies
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

The trusted host remains responsible for obtaining genuine Human direction and
independently provisioning its evidence. An AI-created answer, login identity,
supplied description, supplied files or successful preparation is not Human
consent. The API accepts HumanNewAttempt direction only. Historical
provider-observation mode is unsupported; stderr prose, absent results and config
edits cannot be imported as `NotExecuted` or any other execution fact.

### R6 — Owning-chat continuation sequence

Use the existing canonical Session ID. Do not call Start again or resubmit the
already accepted implementation. The owning chat follows this exact sequence:

1. Call normal `GoalPhaseService.describeGoalPhaseValidationRecovery` with
   `{"sessionId":"<existing Session ID>"}`. Retain the actual typed response and
   complete target unchanged. If it refuses, do not invent a target or authorize
   a different frontier.
2. Present the actual description and ask the Human: “The old selected check
   outcome remains unknown. Do you authorize a NEW attempt of this SAME fixed
   selected SBT check using current host permissions, accepting that it may
   repeat effects of the old attempt, or do you Defer? Original native history,
   Plan, base, candidate and repair counters will remain retained.” Record the
   genuine choice and rationale through the independent trusted host channel.
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
5. After accepted authorization, call normal `GoalPhaseService.advanceGoalPhase`
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

`GoalPhaseValidationRecoverySpec` covers old-wire compatibility, exact retained
admission/uncertainty facts, immutable recovery adjacency, skipped/duplicate/stale
chains and strict independent evidence binding. Its constructed data represents
test inputs rather than actual provider observations.

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
