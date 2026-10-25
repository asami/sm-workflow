# Confirmed whole-Phase timeout continuation

Owner: sm-workflow-dev開発. Producer: sm-workflow main. Date: 2026-10-24.
Repair identity: `P65-TERMINATED-TIMEOUT-PHASE-REPAIR`.

## Preserved incident and scope

The consumer Session `e89e8ba92d074262e4c8a10ef11d1183` remains untouched by
this producer repair. Its 086 whole-Phase validation retained an actual typed
TimedOut/incomplete result and a final Decision. Original source revision 2157
identifies the incident; it is not an instruction to assume the latest frontier
still has that revision. Recovery 085 was already accepted. Used Human 083
authority cannot be reused. The 34 successful history updates are not 34 passing
tests. Partial failed-test diagnostics are not a completed test summary.

No real Session Start, Submit, Advance, recovery, adoption, counter change or
successful-effect replay was performed. Existing Goal, accepted results,
history, counters and unrelated working-tree changes remain. Actual consumer
operation and whole-Phase acceptance belong to the owner chat. No producer
commit or push is included in this repair.

## Implemented contract

Existing `describeFailedPhaseRepair` / `adoptFailedPhaseRepair` gain an explicit
`sm-workflow.goal-phase-failed-phase-repair-adoption.v2` target whose closed
source kind is `terminated-timeout`. Existing v1 known-failed targets and
retained Direction decoding remain unchanged. At this publication, R8
same-candidate validation recovery still refuses timeout; the separately verified
deadline-only Human policy below is now being implemented.

The v2 target binds the actual Session/workflow/participant, original Plan and
base, candidate, current Decision, check and attempt, actual contiguous
intent/result/final receipt identities and revisions, typed timed-out/incomplete
facts, captured proposal, selected checks, fixed paths/closure policy, accepted
prefix, generation and repair counters. Existing lowercase 40/64-character Git
object IDs are supported; no new hash metadata is introduced.

Only a confirmed terminated timeout at the final single-check whole-Phase
Decision is eligible. Unknown, not-started, cancelled, termination-unconfirmed,
other incomplete, stale/crossed or duplicate facts and pending repairs refuse.
Partial output is retained diagnostically. No exit-143-to-Failed conversion or
general Decision resumption is introduced.

A new Human direction binds the complete current description and corrective
proposal. Defer writes nothing; adoption writes one CAS Direction and performs
no Git/File/SBT/Start/Submit/Advance effect. Exactly one corrective Step may be
appended with a fresh Plan revision and dependencies on every accepted Step.
Generation increases once; Plan identity, base, accepted prefix, checks,
acceptance and repair counters/limits stay unchanged. A separate ordinary
Advance issues implementation and follows existing check, independent review,
local Step commit and new-candidate whole-Phase validation paths.

## Completed source and publication evidence

Evidence root:
`.codex-workflow/disposable/a-198ea3c5-ec8e-4815-a2bc-6646ecf38f7e`.

- Initial focused01 genuinely failed compilation on one missing import. Its
  terminal log/result is retained; the import was corrected within scope.
- focused02: ordinary `cozyGenerate` / Compile / Test compilation and four
  focused suites passed, 65 tests. Three unchanged suites account for 61 tests.
- focused03: after the independent-review corrections, ordinary Test
  compilation and `GoalPhaseInterruptedValidationRepairSpec` passed all five
  tests, including 64-character ID round-trip and malformed-field refusal.
  The current combined focused coverage is 66 distinct tests across four suites.
- Independent review found two bounded issues (Git ID compatibility and local
  naming). Both were fixed and independently closed in
  `independent-review02.md`: PASS, no remaining Current Boundary Blockers.
  Two inherited nonblocking Hygiene items remain outside this repair.
- CAR lint: 0 FAIL / 14 inherited WARN. No CBD integration acceptance is claimed.
- Normal `sbt --batch publishLocal` succeeded with actual process termination,
  SBT/wrapper exit 0 and sbt-native locking. Parent typed publication admission
  and the public local-publication verifier both accepted the result.
- Publication update `b0176bc5-d485-4cdc-a2d1-9cecd7c44276`: publication
  `Completed`, runtime update `Activated`.
- Selected installation `b7cd3549-e673-4abf-a6bb-4effffa86d79`, replacing
  `e763b037-c3d5-4cac-87b4-f9a1fb292770`, under
  `/Users/asami/.cncf/installations/sm-workflow`.
- Local artifact:
  `org.simplemodeling.textus:textus-sm-workflow:0.1.0-SNAPSHOT`.
- The distributed CNCF runtime implementation was inspected: failed termination
  confirmation returns TerminationUnconfirmed, distinct from TimedOut. This
  repair does not add a CNCF adapter or change that producer guarantee.

Publication result:
`target/cozy/local-application-update/b0176bc5-d485-4cdc-a2d1-9cecd7c44276/publication.json`.
Publication terminal log:
`.codex-workflow/disposable/a-d0ad4008-798c-4659-ba2c-68aa77673d9e/4994-20261024T082536Z.log`.

## Installed qualification

installed01 selected only the `terminated timeout` example in
`NativeGoalFailedPhaseRepairAdoptionCliSpec` and the actual installed executable.
It used a NEW isolated Session, real UoW/SBT failure diagnostics, a finite
fixture-owned wait and a short typed deadline. Actual timeout, Defer, new Human
adoption, retained v2 traversal and issuance of the corrective Step succeeded.
It then genuinely failed at line 646: the supposed old-result probe supplied a
fresh result reference AFTER the new assignment. Named Submit correctly resolved
the current assignment and accepted that new business result; the probe did not
identify historical work. Its failure/result is retained in
`installed01-parent-admitted.json` and terminal log
`.codex-workflow/disposable/a-5aa82e34-4180-466f-bb8f-46392c0724cd/5664-20261024T082830Z.log`.

Correction implemented: probe the actual original reference immediately after
adoption, before a new assignment, and assert refusal with unchanged history,
revision, files, Git and SBT count. Independent focused review03 is PASS and
confirms the remaining ordinary lifecycle is preserved. Existing pure
new-generation tests separately reject actual old semantic work keys. No public
result-envelope contract is added and no outcome is inferred from result prose.
A fresh isolated installed run must complete the entire remaining real corrective
lifecycle before installed qualification is claimed. The real 30-minute incident
is never rerun. These test/document-only corrections do not repeat the successful
production publication or change its selected runtime.

installed02 genuinely failed during ordinary compilation of 239 Test sources
with `OutOfMemoryError: Java heap space`, before any tests ran. SBT and wrapper
exited 1 and the process terminated under sbt-native locking. Log:
`.codex-workflow/disposable/a-b94c5be1-6727-4d10-a55c-28a161a6382a/10989-20261024T083732Z.log`.
Parent admitted the consumed failed result in `installed02-parent-admitted.json`.

The selected normal shell launcher declares a 1024 MiB default, and its retained
GC output reported a 1.00GB maximum. No project/user/effective environment
override was present; the distribution options contain comments only. The
project now has standard `.jvmopts` containing `-Xmx4G`, satisfying the existing
Textus 4096 MiB floor for this standalone compiler. Home configuration, other
projects, runtime-owned Phase policy, wrapper, caches and command JVM overrides
are untouched. Independent narrow review04 is PASS. The normal launcher/JVM
supplies remaining nonheap behavior; no byte-identical default-argument claim is
made. Fresh installed03, including `.jvmopts` in its bound source subject,
completed ordinary Test compilation and passed the isolated installed scenario:
one suite, one test, zero failures. SBT/wrapper exited 0 and the process terminated
with sbt-native locking. Parent typed admission is retained in
`installed03-parent-admitted.json`. Terminal log:
`.codex-workflow/disposable/a-bd90d913-5793-4f09-9472-a1978cef8e66/11918-20261024T084306Z.log`.
Proof:
`target/sm-workflow/work/validation-recovery/terminated-timeout-repair-adoption-4450746676130147843/terminated-timeout-reopened-proof.json`.
The selected installation remained `b7cd3549-e673-4abf-a6bb-4effffa86d79`.

## Additional Human policy: deadline-only recovery

During installed03, the architecture chat delivered a new policy. The producer
read the actual Human message in `SimpleModelingアーキテクチャ`, thread
`01a0fa78-944b-7ed2-b04e-9deafbeca751`, turn
`01a16d17-7f36-74b2-a026-78785ad82dcf`, message
`01a16d17-7f4d-7481-a5a8-4f28b781e1ed`. The user directs both development chats
to support confirmed terminated timeout recovery through config deadline
extension, without requiring a corrective Step, carrying existing approval and
preserving Session/history/counters/results. This is verified direct Human
authority, not inferred authorization from an agent's request.

The original implementation approval is already recorded as implementation
authority and remains applicable within its scope; it is not missing or awaiting
another approval. No old one-use
operational receipt is reused. The additional instruction authorizes the new
deadline-only path and coordination with the consumer owner. Existing producer
successes are reusable; the corrective-Step route above remains available for
diagnosed defects, but must not be the only timeout route. Preserve the original
partial FAILED diagnostics and incomplete timeout while recording each new
attempt. Unknown or termination-unconfirmed execution must not gain duplicate
execution permission. Deadline ownership stays in the normal CNCF execution
runtime/configuration; no skill timer or second setting is to be introduced.

This additional producer work is now complete: public validation recovery,
focused tests, independent review, normal publication/activation and actual
isolated installed CLI qualification below. Actual consumer operations remain
with the owner.

The existing normal runtime `.textus/config.json` stores
`textus.subsystem.process-execution.plan` as a JSON **string**. Decode that
string and select the exact `sbtPrograms` entry by target/identity/operation;
its `limits.executionTimeoutMillis` is a positive finite Long in milliseconds.
CNCF owns parsing, complete finite-limit validation, permission admission and
deadline enforcement. The sm-workflow application must not duplicate this
configuration parser or add another timer/property. Preserve every other
program and limit when changing the selected deadline.

Read-only consumer inspection identifies target
`sm-workflow-repository-validation-8`, identity
`sm-workflow-p65-repository-full`, operation `test`, currently 1800000 ms.
The owner has proposed a bounded 5400000 ms next allowance based on retained
continued progress; this is not a completion forecast or an instruction for the
producer to edit configuration or run that Session. The original partial FAILED
diagnostics remain real consumer-owned diagnoses even if a longer run completes.

Deadline-only focused04 completed normal `cozyGenerate` (281 generated Scala
files), then genuinely failed Compile before any test: nested RecoveryTarget
Encoder/Decoder derivation exceeded Scala's default32 successive inlines. The
failed terminal result was parent-admitted and retained. Correction splits the
existing Source codec derivation into explicit semiautomatic givens; no compiler
flag, wire format or configuration limit changes. focused05 rechecks ordinary
Compile/Test compilation and the affected five pure suites. Generation success
from focused04 is retained, rather than needlessly regenerating unchanged CML.
focused05 completed successfully: five suites, 70 tests passed, no failures or
aborted suites, SBT/wrapper0 and process terminated with sbt-native locking.
The parent admitted the actual terminal result in `focused05-parent-admitted.json`.
Its log is
`.codex-workflow/disposable/a-695a1aad-4e19-49a6-9818-31c44e3f105e/18862-20261024T091432Z.log`.
Independent focused review05 is sealed PASS with no new blockers. It covers
the eleven-path deadline-only delta and explicit Source codec correction,
including current/retained terminal proof, strict Human binding, one pure CAS,
unchanged Plan/candidate/counters and the config-only installed scenario.
Existing naming and CAR lint Hygiene remain nonblocking and outside this delta.

## Deadline-only implementation and owner sequence

The distinct `terminatedTimeoutPhase` extension is implemented in the existing
validation recovery Operations. It is not a `knownFailedPhase` or an unresolved
attempt. It reuses the shared actual terminal-source proof and binds all outer
target fields to the complete current timeout source. It requires the actual
single whole-Phase final Decision, complete ordinary attempt history, accepted
Steps, current participant and unchanged candidate/selection. A separate strict
`GoalPhaseTerminatedTimeoutValidationRecoveryDirection` uses schema
`sm-workflow.goal-phase-terminated-timeout-validation-recovery.v1` and namespace
`<session>:ordered-terminated-timeout-validation-recovery:<DecisionRevision>@1`.
Defer writes nothing; admission writes one pure CAS Direction. No check or
semantic Submit runs during admission. The separate next Advance retains the
same Plan/revision/base/candidate/accepted prefix/counters and uses a fresh
validation namespace. Only generation increases once. Unknown or termination-
unconfirmed observations do not qualify for this distinct completed-timeout
authority; their existing unresolved recovery semantics are preserved.

Normal publication02 completed and its runtime update Activated:

- update `ae06d9f2-2ee6-46e4-bd6d-5243915841a5`;
- selected installation `7435918d-191b-4b98-9e81-cbc695d014dd`;
- coordinate `org.simplemodeling.textus:textus-sm-workflow:0.1.0-SNAPSHOT`;
- public typed publication verification accepted; SBT/wrapper0, actual process
  terminated with sbt-native locking;
- publication record
  `target/cozy/local-application-update/ae06d9f2-2ee6-46e4-bd6d-5243915841a5/publication.json`;
- log `.codex-workflow/disposable/a-ae902585-e55b-4601-81be-6fc50cb17704/19586-20261024T091907Z.log`.

The owner may use the following public sequence after installed qualification:

1. Read a fresh normal
   `org.simplemodeling.textus.SmWorkflow.GoalPhaseService.describeGoalPhaseValidationRecovery` with named
   `--sessionId`. Use the actual existing Session. Preserve the complete target,
   component instance and current frontier; do not reconstruct from2157.
2. Change only the exact selected program's ordinary
   `limits.executionTimeoutMillis` in the decoded string process plan, preserving
   its other fields and all other programs. Retain the old and new configuration.
   Use an explicitly justified finite value, still subject to CNCF's current
   authority and limits. Do not introduce a skill timer or parse this plan in
   application production logic. Configuration alone grants no new attempt.
3. Carry the already verified actual Human instruction through the normal exact
   description/participant/reference/choice/rationale evidence binding and passive
   `org.simplemodeling.textus.SmWorkflow.NativeWorkflowPreparationService.prepareEvidence` family GoalPhase, kind
   validation-recovery. Trusted-host provisioning remains distinct from passive
   preparation. Do not ask again merely for the runtime/target representation
   change, fabricate an answer, retarget a consumed receipt or reuse083. Preserve
   the actual approval provenance and old approval/history records.
4. Call `org.simplemodeling.textus.SmWorkflow.GoalPhaseService.recoverGoalPhaseValidation` with named
   `--input`: existing Session, complete actual target, choice
   `AuthorizeNewAttempt`, real rationale and exact answer reference. This admits
   the one Direction only. Stale, duplicate or crossed targets refuse.
5. After accepted admission, use one separate ordinary
   `org.simplemodeling.textus.SmWorkflow.GoalPhaseService.advanceGoalPhase` with the same `--sessionId`.
   CNCF's owning UoW performs the fresh fixed check under current normal config.
   Retain its actual new result, whether success, failure, rejection or timeout.
   The old incomplete timeout and partial failures remain unchanged; extending a
   deadline is not evidence that the original failing assertions are fixed.

No Start, accepted Submit replay, corrective Step, source edit, new commit or
repair-count consumption is required solely to extend a confirmed deadline.
A diagnosed defect may still require the optional corrective route below.
Actual owner operations and consumer acceptance remain outside this producer
repair. E15 isolated installed qualification completed on the selection above.

### Executed E15 installed qualification

installed04 passed the exact `deadline extension` example in
`NativeGoalKnownFailedPhaseValidationCliSpec`: one suite, one test, zero failures
or aborted suites; SBT/wrapper0, process terminated, sbt-native locking. Parent
typed terminal admission is retained in `installed04-parent-admitted.json`.
Log:
`.codex-workflow/disposable/a-76668ce9-aa92-4245-b102-78865972e1ee/20138-20261024T092129Z.log`.

Proof:
`target/sm-workflow/work/validation-recovery/installed-deadline-only-timeout-12760240472668379891/deadline-only-timeout-reopened-proof.json`.
The fresh isolated storage has fixture Session `68b84a042d43ee53acc3dc32b67b01a6`;
that deterministic fixture ID also appears in the earlier corrective scenario,
but the stores and roots are distinct. Neither is the real consumer Session.
The proof retains source19 timed-out/incomplete and the new source23
exited/0/succeeded result, separate Direction and original22-entry history.
Only the selected Test program deadline changes120000→300000ms; the real Test
and finite150000ms wait are identical. Same-candidate revalidation reaches
ReviewPhase with Plan revision1 and repair counters0/0, limit2. Sources/build,
Git tip/tree/reflog, base, accepted prefix and old receipts remain unchanged;
there is no added corrective Step or commit between the two trials. Defer,
crossed/null targets, duplicate admission and no-direction/repeat Advance
effects are checked in the executed scenario. Installed class origins bind the
published installation7435918d; the normal selection remained unchanged.

The installed fixture's independent Human evidence is specification-owned;
it attests no consent or acceptance for the actual incident. The owner's
retained real Human instruction and exact current target remain necessary for
the owner's public continuation. Original consumer FAILED diagnostics must
still be addressed or assessed by that owner after a complete real run.

Owner-sequence operation identities above were checked against generated CML/
component and the executed fixture. Preparation is
`org.simplemodeling.textus.SmWorkflow.NativeWorkflowPreparationService.prepareEvidence`;
the former handoff wording `NativeHostPreparationService` was corrected without
changing code or rerunning successful acceptance.

## Optional corrective-Step continuation for diagnosed defects

1. Keep the existing consumer Session and its latest actual frontier. Read a
   fresh description using the installed runtime; do not synthesize a target
   from source revision 2157 or reuse Human 083.
2. Prepare a concrete Plan proposal with exactly one corrective Step, depending
   on all accepted Steps and within the declared candidate paths, fixed closure
   mapping and selected checks. Keep original identity/base/accepted prefix,
   acceptance and repair budget. Correct the real failing tests under the
   ordinary ownership contract; partial diagnostics alone are not acceptance.
3. Carry actual Human authority through the existing evidence/adoption binding
   for that latest description and complete proposal. Preserve the verified
   approval provenance; do not ask again merely because a runtime or target
   representation changed, and do not reuse consumed one-use receipts or invent
   a Human answer. Surface only a genuinely new unresolved semantic choice.
4. Adopt once through the public operation, then issue a separate ordinary
   Advance. Complete only the new issued corrective work and its normal
   validation/review/local commit/new-candidate whole-Phase checks.
5. Do not re-Start, replay an accepted Submit or accepted Step effect, reset a
   counter, reinterpret timeout as Failed, or invoke old recovery automatically.

Specification: `docs/spec/goal-phase-failed-phase-repair-adoption.md` R8/E4/E5
and `docs/spec/goal-phase-validation-recovery.md`.
