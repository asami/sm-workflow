# Timeout recovery choices and bounded hang investigation

Date: October 24, 2026
Boundary: P65-TIMEOUT-THREE-CHOICE, producer restoration only
Status: focused repair verified; independent review, local publication and installed qualification complete

## Delivered behavior

For an actual confirmed terminated whole-Phase timeout, the normal public
`describeGoalPhaseValidationRecovery` now offers the ordered choices:

| Wire choice | Human meaning | Effect of recovery operation |
| --- | --- | --- |
| `AuthorizeNewAttempt` | Extend the deadline and try the same candidate/check | Retains the existing exact-target retry Direction. Actual normal host deadline configuration must be adjusted before a separate ordinary Advance; the authorization does not change configuration. |
| `LimitedRepair` | Investigate and prepare a bounded repair | Independently admits the exact Human evidence and returns `limited-repair-preparation-required`. No native state transition, retry, source edit, Plan adoption or execution. |
| `Defer` | Defer execution for deeper investigation | Retains the original Decision and grants no retry. |

Limited repair includes both fixing known errors and identifying the test actually
running when execution stopped, investigating/debugging it as a suspected hang,
and fixing established causes. Use actual start/end observations, retained output
and diagnostic stack/process evidence when needed. A timeout does not establish
a hang; the last printed test may have finished. Report unknown test identity or
missing evidence honestly. Investigation may instead establish normal slow
execution or external waiting. CNCF retains execution and result interpretation.

The limited-repair response directs the caller to the existing
`describeGoalPhaseFailedPhaseRepair` operation with a captured concrete proposal,
then independently authorized `adoptGoalPhaseFailedPhaseRepair`, then separate
ordinary Advance for the corrective Step. Selecting investigation does not adopt
an unknown future Plan. There is no second repair executor or automatic fallback.

Unknown outcomes and genuine terminated nonzero failures keep their existing two
choices. Target-specific passive preparation rejects broadened, omitted or
reordered choices and rejects LimitedRepair on non-timeout targets. Exact current
source, participant and independent Human evidence remain mandatory. Existing
timeout Direction v1 and accepted history remain unchanged.

## Validation and limits

Evidence root:
`.codex-workflow/disposable/a-6d8406c3-97d0-4416-997e-69d35777c7a8/`.

- focused01: normal Cozy generation produced 281 Scala sources; production and
  test compilation succeeded. Repair-adoption 6 and timeout-recovery 4 tests
  passed. An additional generated-ABI suite passed 2 and failed 1; the whole
  invocation is failed, with SBT/wrapper 1 and actual termination.
- focused02: only the two unexecuted relevant suites were selected, 24/24 passed,
  SBT/wrapper 0, terminated, sbt-native. The four affected suites total 34 passed.
- Independent focused review found no behavioral blocker. Its new local test
  helper naming observation was corrected (`prepare` to `_prepare_`, definition
  and four uses only); behavior and assertions are unchanged. Current Test
  compilation during installed verification covered that mechanical rename.
- Parent inspected the bounded implementation and ran owned-path diff checks.
- CAR lint exited 0, no FAIL, 14 inherited WARN outside this change (existing
  direct-access cautions, missing ABI baseline and explicit development plugin).
  CBD review integration is not configured for this repair.
- publish01: normal `sbt --batch publishLocal` succeeded, SBT/wrapper 0,
  process terminated, sbt-native. Typed publication verification accepted;
  publication `Completed`, runtime update `Activated`.
  Coordinate `org.simplemodeling.textus:textus-sm-workflow:0.1.0-SNAPSHOT`;
  update `294331d3-4aa8-45dc-a520-cc48d48260cb`;
  selected installation `382e8019-45b6-4572-884c-f7eb67b02e7e`.
- installed01: exact two suites filtered by `timeout recovery choices`, actual
  installed command and selected runtime, **2 suites / 2 tests passed**;
  SBT/wrapper 0, process terminated, sbt-native. Current test compilation passed.
  Both scenarios independently prepared/imported their exact LimitedRepair
  Human evidence, received the pure response, checked original history and
  effect counts, and continued through the existing normal route.
  E5 adopted concrete Plan `terminated-timeout-repair-2` and completed corrective
  implementation/check/review/local Step commit/new whole-Phase Test.
  E15 changed only the selected Test program deadline 120000 to 300000 ms;
  original Plan revision 1 and repair progress automatic 0 / ordinary 0 / limit 2
  remained. Both original timeout receipts stay `timed-out` / `incomplete`.
  The parent read actual CLI outputs and retained fixture proofs in
  `installed01-proof-summary.json`; no previous proof was relabeled as current.

Current Incident Blocker: none for P65-TIMEOUT-THREE-CHOICE. This is focused
qualification, not a whole-repository or global ABI acceptance. No real incident
hang diagnosis or source repair was performed on the consumer's Session.

Actual retained log/summary locations and result identities are in
`focused01-parent-admitted.json`, `focused02-parent-admitted.json`,
`publish01-parent-admitted.json`, and `installed01-parent-admitted.json` in the
evidence root. Independent source review is `independent-review01.md`.

Current installed fixture proof locations:

- `target/sm-workflow/work/validation-recovery/terminated-timeout-repair-adoption-10021029774513335676/terminated-timeout-reopened-proof.json`
- `target/sm-workflow/work/validation-recovery/installed-deadline-only-timeout-4560958568751717833/deadline-only-timeout-reopened-proof.json`

For the owning development chat: retain its same Session and current frontier.
Do not turn this delivery into Start, accepted Submit replay or old successful
effect replay. When a genuine confirmed timeout requires a new choice, obtain the
current `describeGoalPhaseValidationRecovery` result and actual Human answer,
prepare/import exact independent evidence through
`NativeWorkflowPreparationService.prepareEvidence`, then call
`recoverGoalPhaseValidation` with that exact target/reference. A LimitedRepair
response requires bounded diagnosis and concrete proposal/adoption before work;
it is not a runnable assignment or retry grant. Leave an already running trial
with its owner and await its real terminal result before selecting recovery.

The optional ABI failure is retained separately: unchanged
`GoalPhaseGeneratedAbiSpec.scala:63` assumes all 25 Operations reject an empty
request and reports no failing Operation identity. This repair changes no
business parameter declarations, and its hand-installed Describe/Recover still
reject absent required inputs. Independent review found no causal link to this
three-choice change. Further generated-input investigation is separate; neither
global ABI success nor whole-suite success is claimed. Do not weaken the test or
erase the failed invocation to report this focused repair as globally validated.

## Preserved ownership

No real consumer Session was operated. Its Goal, accepted results, Plan/base,
history, repair counts and successful effects remain with `sm-workflow-dev開発`.
There was no real Start, Submit, Advance, recover or adoption from this producer
task. Actual hang investigation of that Session is also left to its owner.
Existing dirty files, generated evidence and prior successful and failed
validation/publication records remain. No commit or push is performed.

## Agent Usage Summary

The protected Human/public-contract repair and independent review use Sol high;
registered exact SBT work uses Luna medium. The parent runtime profile is not
available and is recorded as unknown. Counts refer only to this repair.

| Agent | Exact model | Effort | Runtime / role | START | RESUME | DIRECT | Outcome |
| --- | --- | --- | --- | ---: | ---: | ---: | --- |
| `/root` | unknown | unknown | parent runtime / diagnosis, admission, verification | 0 | 0 | 1 | Owns scope and actual result interpretation |
| `/root/p65_timeout_three_choice_fix01` | gpt-6.1-sol | high | registered cncf_fix_worker_terra / FIX | 1 | 1 | 0 | Frozen repair and mechanical helper rename complete |
| `/root/p65_timeout_three_choice_review01` | gpt-6.1-sol | high | direct default / independent review | 1 | 0 | 0 | No remaining current-boundary blocker |
| `/root/sbt_receipt_14fb0e5d34224a409f43` | gpt-6-luna | medium | registered cncf_command_runner / SBT | 1 | 0 | 0 | focused01 actual failure retained; generation and 10 focused tests passed |
| `/root/sbt_receipt_25a3c7b8056145dc8bbd` | gpt-6-luna | medium | registered cncf_command_runner / SBT | 1 | 0 | 0 | focused02 24 passed |
| `/root/sbt_receipt_101e52dccca24fc6b6c9` | gpt-6-luna | medium | registered cncf_command_runner / SBT | 1 | 0 | 0 | publish01 Completed / runtime Activated |
| `/root/sbt_receipt_aa0c78c3a55f4e91accf` | gpt-6-luna | medium | registered cncf_command_runner / SBT | 1 | 0 | 0 | installed01 2 suites / 2 tests passed |

Process note: the FIX RESUME disclosure was emitted immediately after the
followup call rather than before it; this ordering deviation is retained here.
It changes no authority, source ownership or execution permission.

Separate Hygiene: pre-existing naming maintenance and the inherited CAR cautions
remain outside this repair. Separate Development Candidate: investigate the
generated ABI empty-input aggregate failure with the exact accepting Operation
identified; owner sm-workflow, separate generated-input validation work, no
downstream parser or weakened assertion as a workaround.
