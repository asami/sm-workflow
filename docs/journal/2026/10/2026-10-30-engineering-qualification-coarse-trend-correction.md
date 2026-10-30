# Engineering qualification: eliminate coarse-status recurrence stops

Date: 2026-10-30
Status: source correction and focused regression verified; not activated in a live Session

## Incident and attribution

The confirmation originated in the runtime, not in an invented skill-side AI
restriction. `qualifyDelegateTaskHumanRepairRecovery` returned
`engineering-policy-human-required`, offering `ContinueEngineeringQualification`.
The caller in `sm-workflow開発` forwarded that actual requirement.

The retained `engineering-attempt9-type-fix01-public-target.json` in the
`p610-publication-repair-destination` task records engineering counters 8/8 and
limit 10. Its four trend observations refer to attempts 5, 6, 7 and 9. All were
represented as `check:publication-recovery:failed`. Earlier attempts reported
three failed tests; attempt 9 instead reported Scala E007 type mismatches between
String and java.nio.file.Path in the CLI regression fixture. Check identity and
failure status erased that distinction, causing a false recurrence/stall stop.
The evidence does not establish whether the earlier test failures shared a cause.

Implementation location: branch `codex/p610-publication-repair` in the existing
`sm-workflow-p610-publication-repair-20261029` checkout. Its concurrent staged and
unstaged work was retained. The base sm-workflow checkout does not yet contain
this engineering implementation; do not claim the installed runtime is fixed.

## Correction

- Reuse the existing SBT diagnostic extractor for trend identities, without its
  presentation-only group limit. Compiler errors, test failures and suite aborts
  retain diagnostic content; check status and compilation summaries alone do not
  prove recurrence. Exact diagnostic identity is not a causal AI classification.
- Preserve historical coarse observations and engineering targets. New admission
  excludes coarse status strings from trend proof and can read available retained
  diagnostics. Missing diagnostics mean insufficient trend evidence; they do not
  mean PASS. Historical targets and already recorded directions remain readable.
- Same-boundary candidate correction and necessary validation retain the original
  authority and normal counters. Genuine diagnostic recurrence and the ten-cycle
  hard ceiling still stop through the runtime; unresolved execution and changed
  authority remain ineligible for automatic replay.
- Update the common caller contract and sm-delegate-task in the implementation
  checkout, canonical sm-workflow and shared deployed skill source. Do not add
  per-attempt consent, fabricate a new Human answer or bypass a runtime stop.
  Existing explicit user limits and real-Session exclusions remain effective.

## Verification

Independent regression validation for this correction used the registered SBT
runner, GPT-6 Luna / medium. It did not execute the consumer's Phase/Task or its
13-suite engineering qualification. The selected specifications were:

- DelegateTaskHumanRepairQualificationSpec
- IncrementalSbtFailureDiagnosisSpec

Result: 2 suites completed, 20 tests succeeded, zero failures/aborts. SBT and
wrapper exited 0 with process termination and sbt-native locking evidence.
The tests cover different compiler/test diagnoses, same-authority continuation,
retained Task/counters, real recurrence stops, unknown diagnostics and hard-limit
rejection. Existing diagnostic extraction regressions also pass.

Receipt, relative to the implementation checkout:
`.codex-workflow/disposable/a-8e4a25e8-72eb-4405-bd1c-353495b03d7f/command-result.json`.
The raw output and wrapper log/summary references are retained beside that receipt.
All three changed skill copies pass skill validation, and diff whitespace checks
pass. Full/HEAVY qualification and installed-runtime acceptance were not rerun.

## Delivery and preservation

Integrate these source changes into the owning runtime delivery with its normal
validation/publication path. No runtime was published or activated by this task.
Do not import the old coarse-status stop as a new Human-consent prerequisite;
allow the corrected public route to evaluate the same retained authority and
actual diagnostics. A real non-convergence or allowance stop remains distinct.

No actual Session operation, Human submission, Advance, reset, re-Start, accepted
re-Submit, history rewrite or counter change was performed. Prior successful
engineering/consumer results were not replayed. The later timeout reported by
the development chat remains its original evidence and is not resolved by this
source correction. Consumer Session operations remain with their owner.
