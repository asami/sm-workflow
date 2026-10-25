# Runtime-owned repair control: producer and Control Center handoff

Date: 2026-10-25 (Asia/Tokyo)
Recipients: sm-workflow開発; textus-control-center開発
Status: skill/specification/plan update; runtime implementation and adoption pending

## Subsequent transition correction — 2026-10-25

The [outcome-based delegation handoff](2026-10-25-outcome-based-delegation-restoration-handoff.md)
corrects the implementation/adoption wording below. RRC absence must not become
a prerequisite for already-authorized restoration: the producer owner continues
through the existing applicable standalone execution route, while RRC is built
in parallel. Keep historical instructions and results, but do not interpret
"implement the missing route" as "wait for RRC before any further qualification".
Consolidate technical work and review at the delivery boundary; retain genuine
runtime decisions, permissions, counters and unknown-effect handling. This is a
correction of the earlier handoff, not a new grant or runtime acceptance claim.

## User direction

Remove AI-authored stopping conditions. sm-workflow owns convergence decisions
and the ten-automatic-batch hard ceiling. Preserve existing Sessions, accepted
work, history, repair counts and validation results. The user authorizes this
correction and sharing; do not request the same policy approval again. This
handoff does not confer unrelated effect/publication authority or claim execution.

## Incident and evidence

The Control Center caller's retained current response was Request / RepairStep,
not Human, for Session `fd87cec948d0933cb6181af1f696cb52`, CC51-S1. Its actual
repair_progress was automatic=1, ordinary=1, limit=3. Historical exhausted windows
remain recorded; these observations must not overwrite later runtime state.

Evidence lives under the Control Center task-relative directory
`.codex-workflow/sm-workflow/phase-5.1-20261024/target/sm-workflow/tasks/control-center-phase-5.1-start/`:

- `call-complete-s1-independent-review-continue-human-r1/advance/response.json`:
  actual Request / RepairStep and counts.
- `cc51-s1-sm-workflow-review-repair-freshness-execution-proposal-r2.md`, line 26:
  caller-created exclusion of automatic correction/re-execution after failure.
- `cc51-s1-runtime-restoration-fixture-correction-execution-proposal-r1.md`,
  lines 36 and 48: another one-run stop and the prior clause used as its rationale.
- `cc51-s1-sm-workflow-review-repair-freshness-runtime-result-r5.json` and
  `cc51-s1-sm-workflow-review-repair-freshness-failure-diagnosis-r1.md`:
  actual qualification failure and retained diagnosis.

Source qualification reached two suites: 25 tests, 18 passed, 7 failed. The third
suite was not reached after the earlier failure. Diagnosis confines correction
to the already-admitted NativeGoalDependencyValidationCliSpec.scala: public JSON
normalization, interruption-fixture configuration reuse and asserting the actual
early rejection for the negative authority case. Reuse that diagnosis/review;
it is not proof the proposed fix or subsequent qualification has executed.

The immediate repeated approval arose from caller-authored restoration conditions,
not an observed runtime divergence or ten-cycle exhaustion. The earlier user did
approve a combined restoration proposal; retain that fact. The new direction
supersedes the AI-created blanket exclusion within its already-authorized scope,
without erasing the old approval or failure records.

The source Plan default is 10 but explicit limits currently extend to 64; the
consumer captured 3. Advisory Fix assessments exist. Complete minimal convergence
control and restoration integration are not established by source inspection.

## Producer instructions

Implement the [RRC corrective slice](../../../phase/runtime-repair-control-correction.md)
under the [specification](../../../spec/runtime-owned-repair-control.md) and
[design](../../../design/runtime-owned-repair-control.md). Connect one actual
restoration failure to repair, focused qualification and supported consumer
continuation first. Reuse existing repair controllers, execution and public
Operations; do not build another skill/helper loop, counter or authority ledger.

Deliver runtime evidence-based convergence decisions and the ten-batch ceiling.
One failed test is not divergence. Stop sustained divergence before the ceiling;
successful batch ten may finish, but no automatic batch eleven. Preserve the
ordinary/automatic count distinction and existing result history.

Supply public same-Session policy adoption for accidental caller limits and old
AI-created approval waits. For a limit correction such as 3 -> 10, preserve the
consumed count and record the actual direction and policy change. Do not silently
reset or renew a window. Truly human-selected smaller limits remain attributable.
If the policy transition/restoration controller is missing, implement it in the
authorized producer scope in parallel. Existing authorized restoration continues
through the applicable standalone route; do not require RRC completion or ask
the consumer for a new one-run exception. Actual Session policy changes still
require a supported public transition and never direct storage editing.

Use the existing Journal/Evidence/Finding owner for available stop provenance and
cause-versus-recovery facts without added AI calls. Runtime cannot observe unseen
chat text: use actual supplied facts and retain unknown. Do not make recording a
new continuation gate. Reuse existing tests/reviews; qualify changed behavior,
installed delivery and actual adoption separately.

## Control Center instructions

Retain the exact current Session, pending work, original base, accepted results,
active/cumulative counters, all failure history and validation evidence. Follow
the latest actual runtime state; the snapshot above is evidence, not state to copy.
Reuse the fixture diagnosis and existing correction scope. Do not fabricate Human
requests, reset to Start, resubmit accepted work or edit Session storage.

Remove the caller's additional one-failure/one-run approval conditions in adopting
the user's direction. That does not bypass a genuine typed runtime stop or permit
blind replay. Receive the qualified restoration outcome from its producer owner
and continue original work through the existing supported consumer route. Adopt
the new public repair/policy route when qualified; its absence does not stop the
already-authorized restoration. Track the missing capability in parallel, without
requesting approval for each fixture correction or unchanged qualification.
Collect live/unknown effects;
do not duplicate them. Only qualified artifacts may be adopted.

## Delivery boundaries

The workstation-distributed and component-bundled sm-goal-phase instructions were
updated; the installed skill is a symlink to the workstation source. The bounded
RRC delivery is linked from the roadmap and Phase 6.6; Phase 9 retains broader
provider routing/comparison and convergence refinement. Ongoing 6.2/6.4/6.5 work
and accepted evidence are not reopened. No Scala implementation, Session mutation,
SBT execution, publication or runtime switch is performed by this documentation
update. All RRC behavioral acceptance remains pending until actual qualification.

Documentation checks: affected tracked files passed `git diff --check`; new
document links resolve; the added skill section matches both source copies and
the installed symlink resolves to the workstation copy. The standard skill
validator could not run because PyYAML is absent from both available Python
environments. No validator PASS or runtime behavioral PASS is claimed.
