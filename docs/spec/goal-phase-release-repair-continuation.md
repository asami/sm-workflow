# Release repair and the retained legacy Human frontier

Newly issued `ReviewPhase` work captures
`releaseRepairContract: sm-workflow.phase-release-repair.v1`. Its independently
admitted in-scope `repair` result enters ordinary phase-level `RepairStep` using
the original Plan, completed Steps, ownership and repair allowance. It adds no
Human gate or corrective Step. A marker is never supplied by the reviewer.

Saved marker-absent requests retain their original interpretation. An already
accepted Phase repair review followed by its unanswered native Human question
can continue only through a separately admitted actual Human direction. Existing
engineering permission to implement this runtime is not that direction.

## Public continuation

`GoalPhaseService.describeGoalPhaseReleaseRepairContinuation` accepts
`GoalPhaseReleaseRepairContinuationDescriptionInput(sessionId)` and returns the
exact current target, choices `ContinueReleaseRepair`, `Defer`, component
instance and recovery operation. Description performs no effect or transition.

Eligibility requires the adjacent original accepted marker-absent `ReviewPhase`
repair and current unanswered Human; all original Steps closed; remaining
ordinary repair allowance; successful actual Phase checks and a pinned
candidate; every actual failed required condition and corresponding unresolved
REVIEW issue; no proposal, conflict, pending repair or closure effect. An
untyped `decision-needed` report is outside this route.

The target contains the original complete owning-codec Request and Result,
question/assignment/result references and native revisions, Plan/base/completed
prefix, generation and counters, candidate/checks and complete resolved host
scope, validation policy and fixed closure programs. Original report and check
output remain complete; there is no copied Human presentation envelope.

`GoalPhaseService.recoverGoalPhaseReleaseRepairContinuation` consumes that exact
target with `sessionId`, `choice`, `rationale`, `reference`. `Defer` writes
nothing. `ContinueReleaseRepair` requires independently provisioned actual
Human evidence for the exact component, participant, reference, rationale and
target under purpose `goal-phase-release-repair-continuation`. Normal
`NativeWorkflowPreparationService.prepareEvidence` kind
`release-repair-continuation` passively prepares the observed direction. It
authenticates no supplied claim and grants no workflow authority itself.

Recovery appends one versioned Release variant in the existing native
DecisionSupersession family. The old accepted review and unanswered question
remain; it neither answers that question nor resubmits the review. Recovery
performs no Git, SBT, Submit or Advance. A separate ordinary Advance rechecks
the saved host, original prefix and actual typed Git head/index/managed scope,
then issues the original phase-level repair once, consuming generation and one
ordinary repair batch once. Stale, duplicate, crossed or drifted targets refuse.

## Resource input for large targets

Both operations accept exactly one of `--input <JSON>` and
`--inputResource <absolute local file URI>`. Use Resource input for a complete
large target. Configure the normal host's actual narrow read roots and byte cap
before the call; for example `textus.resource.url.file.roots` names the input
directory and `textus.resource.read.max-bytes` is `16777216` when a complete
target exceeds the default. The cap must accommodate the actual serialized
input, not merely its report summary. These settings grant no Human authority.

```sh
sm-workflow --runtime-root "$host" command \
  org.simplemodeling.textus.SmWorkflow.GoalPhaseService.describeGoalPhaseReleaseRepairContinuation \
  --inputResource file:///absolute/inputs/release-description.json --json
sm-workflow --runtime-root "$host" command \
  org.simplemodeling.textus.SmWorkflow.GoalPhaseService.recoverGoalPhaseReleaseRepairContinuation \
  --inputResource file:///absolute/inputs/release-direction.json --json
```

The description input is `{"sessionId":"<actual returned Session>"}`. The
direction input uses the returned `target` Record unchanged, the chosen closed
choice, independently observed rationale and actual reference. Provision the
separately prepared normal host evidence through its ordinary importer before
recovery; source metadata or this document never substitutes for evidence.

## Remaining checks, review and Final

Only the assigned original blocker batch is repair scope. After its result,
native Advance inspects the delta within the union of original Plan ownership,
runs fresh affected selected Phase checks and requests independent scoped
re-review retaining the original integrated report and exact issue dispositions.
An old successful receipt is not claimed for a changed candidate.

The normal host must declare a fixed closure program with step identity
`phase-release`, a distinct ordinal and its typed Git selection/message. Final
uses the existing Phase authority for the exact accepted target and the fixed
typed Git program. It records one actual Release delta commit whose parent is
the immutable completed-Step tip. Completed Step commits are never reopened.
Changed authority/program/selection/candidate refuses; a retained commit intent
with absent or uncertain actual result is never replayed automatically.

## Executable evidence

`GoalPhaseReleaseRepairContinuationSpec` covers captured marker compatibility,
legacy/new routing, exact required-condition batch, independent binding, strict
tagged disposition, scope/field rejection, passive preparation and one-time
counting. `Phase65GoalPhaseFixSpec` covers distinct Final intent/execution/commit
receipts. `Phase65GoalPhaseFixCliSpec` covers ordinary new Release repair and
explicit observation recording with real typed checks and Git.
Its missing-Final-program counterexample refuses before commit; withholding
the selected Final commit capability after authority leaves a real retained
intent, and restoring the capability still does not replay that intent.

`NativeGoalReleaseRepairContinuationCliSpec` is an opt-in installed qualification.
It requires explicit distinct immutable old/new installation roots, the normal
installed command, `setup` or `continue` stage and one absolute immutable
locator. Setup runs the old ordinary fixture to its real Human stop and writes
the locator once. Continue reopens that same repository/runtime/Session under
the new image; negative controls reuse the saved frontier, then the separately
prepared fixture Human direction, remaining work/check/re-review/Final complete.
Every review and Human in this isolated suite is specification-owned test data.

## Finite terminal completion and isolated same-image qualification

P65-FINAL126-TERMINAL-01 distinguishes actual Final Git success, its adjacent
native intent/execution/committed chain, the durable local-completion payload,
and native Completed lifecycle. A commit at HEAD proves none of the other
facts by itself. CNCF mechanical admission accepts an Active unsuspended source
and either an Active unsuspended successor or a Completed successor with empty
progression and suspension. It retains owner, operation, reference, payload,
correlation, CAS and native adjacency checks; no append from Completed follows.

The additional opt-in installed scenarios select exactly one explicit absolute
immutable root with `smworkflow.spec.terminalReopenInstallations` and the existing
`smworkflow.spec.installedCommand` and normal SBT executable. Use ScalaTest's
focused selection for `Installed finite terminal completion on one new immutable
image`; the original two-image legacy setup/continue scenario remains separate.
Fresh setup, accepted candidate, real Final, terminal failure and ordinary
reopen all use that same newly selected installation through the normal host.
Source compilation, another image, or an older successful run is insufficient.

The successful-Final case discovers the unique native instance table/column/row
through JDBC in its freshly created SQLite fixture. Ephemeral BEFORE INSERT and
UPDATE rejection triggers select only that instance's serialized root
`current.lifecycle == completed`, with its exact schema and identity. Historical
lifecycle fields in receipt/history payloads do not match. Intent, execution,
committed and local-completion writes remain available. No row, receipt or
payload is patched or seeded. The fixture retains the trigger SQL, actual failed
invocation/error, native before/after snapshots, counters and Git observations.

The first failed call must retain one actual successful Final with its original
single parent, accepted tree and selected paths, then a real terminal-write error
without Terminal. Removing only the fixture triggers and explicitly invoking
ordinary Advance must append exactly one missing Completed successor and return
Terminal. Actual SBT invocation logs, native review/result/stage/source-history/
Final receipts and Git history must show zero subsequent effect increments.
A further passive Advance must return identical Terminal with no new revision,
history, receipt or effect. Original accepted work and all failures remain.

A separate isolated case withholds the actual selected Final commit capability
after authority, allowing ordinary execution to retain a genuine intent and
actual error. Restoring the host and ordinary Advance still returns Wait, with
no fabricated execution/committed/local-completion receipt or automatic replay.
Neither case inspects or operates any real Session.

Qualification requires actual focused results, physical persisted evidence,
independent review, normal CNCF then SM local publication/runtime activation,
and inspection that the exact selected image includes both the SM completion
caller and finite CNCF guard. The new scenarios have not been certified PASS by
this text. Preserve historical PASS claims with their original scope. Stop on
an ambiguous fixture target, missing usable native chain, unsafe atomicity,
another required file/API/schema, or insufficient same-image evidence; report
the precise gap without private-store repair or successful-effect replay. The
real Session's saved checkpoint and next public operation remain with its owner.
