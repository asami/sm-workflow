# Phase 18: Tool-Neutral Validation and Flutter Integration

Status: planned; no implementation or acceptance claimed.
Date: 2026-10-28
Owner: sm-workflow development
Predecessor: [Phase 6.7](phase-6.7.md) shared Task/FIX route and existing GoalPhase/RepositorySync validation
Producer: CNCF Phase 117 (required for W4–W5)
Checklist: [P18 acceptance](phase-18-checklist.md)
Handoff: [design handoff](../journal/2026/10/2026-10-28-tool-neutral-validation-handoff.md)

## Purpose and preservation

Commonize SBT and Flutter validation requests/results inside sm-workflow so
existing Workflow progression, repair, review and acceptance can use either tool.
CNCF supplies tool-specific DSLs/results: preserve `sbt_xxx` and consume new
`flutter_build`, `flutter_test` and `flutter_analyze`. Do not make SBT DSLs
tool-neutral or move Workflow acceptance into CNCF.

This numbered plan replaces the former unnumbered plan. W1–W5 retain their
work-unit identities; the separate P18 checklist owns acceptance tracking.
Preserve active Sessions, accepted work, history, repair counts and verification.
This plan starts no execution and does not change live configuration.

## Roadmap position and dependencies

Phase 18 is a toolchain extension after the shared execution baseline delivered
by Phase 6.7, alongside the separately scoped Phase 17 extensions. Its number
does not require completion of all Phases 8–17. Preserve the existing primary
roadmap sequence; this Phase does not become a new
gate for those deliveries or silently enlarge their active Steps.

W1 aligns with CNCF Phase 117 C1. W2–W3 can proceed with the existing SBT route
while CNCF C2–C4 are developed. W4 needs W3 and the qualified CNCF C5 delivery;
W5 then establishes consumer acceptance. Existing qualified behavior from 6.8/6.9
must be preserved if present at implementation time; this Phase does not rebuild
their completion or synchronization features. Phase 7 retains multi-repository
delivery; independent producer handoff is sufficient for this initial integration.

## Main route first

| Unit | Depends on | Work | Connected result |
| --- | --- | --- | --- |
| W1 | Existing validation baseline; align with CNCF C1 | Specify common requests/results/evidence and migration mapping | Purpose, selection, process state, validation verdict and retained evidence have a tool-neutral contract; existing FixCheck semantics remain covered |
| W2 | W1 | Adapt SBT results and GoalPhase progression | Existing SBT behavior is preserved while progression/acceptance consume common results |
| W3 | W2 | Connect RepositorySync, StandaloneFix and other shared-engine consumers; qualify persistence/resume | Old records remain readable and new results resume through supported public routes without duplicate execution |
| W4 | W3 and CNCF Phase 117 C5 | Bind Flutter configuration and typed DSLs | Analyze/test/build execute for the selected target/profile and convert real results to common validation |
| W5 | W4 | Qualify real Flutter development, SBT regression and installed usage | Failure/repair/revalidation/review/acceptance and interrupted continuation are evidenced; consumer delivery is independently reviewed |

## Contract and implementation boundary

Define the smallest coherent common contract, considering proposed names
`ValidationRequest`, `ValidationAttempt`, `ValidationResult` and
`ValidationEvidence`. Separate Compile/Analyze/Test/Build purpose from all/selected
test scope; do not require every tool to implement every purpose. Map SBT TestOnly
within its adapter and explicitly preserve FixCheck outcomes and continuation.

Separate process termination from validation verdict. Retain not-executed,
uncertain and unverified outcomes; exit zero alone is insufficient when selected
tests or required artifacts are unproven. Zero/all-skipped or unverified tests
are not automatic acceptance; any allowance belongs to explicit project policy.
Keep tool-specific facts, diagnostics, counts, artifact/log references and the
association with the requested Check/workspace/profile. Programs process raw
logs; normal AI handback uses summaries and relevant diagnostic references.

Inspect `WorkflowSbtValidation`, `GoalPhaseNativeValidation`,
`WorkflowManagedProcessExecution`, `RepositorySyncNativeMergeValidation`,
`StandaloneFixNativeRun` and related capture/codecs/recovery under
`src/main/scala/org/simplemodeling/textus/smworkflow/admission/`.
Trace shared Task/FIX callers rather than replacing names mechanically.
ScalaTest discovery in `GoalPhaseValidationMetadata` and CNCF's SbtExec binding
stays in the SBT route. Start Flutter with explicit test selection; equivalent
automatic discovery is not an initial prerequisite. Update generated contracts
through their existing CML/generation path when required.

## Validation and completion

W1–W2 cover common outcome mapping, SBT success/failure/non-execution/uncertainty,
selection verification, FixCheck and existing GoalPhase transitions. W3 covers
historical decoding, new result roundtrips, public resume, applicable accepted
result reuse and each shared consumer's repair/review/closure behavior.

W4 covers each Flutter operation, unavailable SDK/target, mismatched selection,
zero/all-skipped tests, diagnostics and artifact evidence. W5 exercises a real
recorded Flutter project/SDK/target through success and failure -> repair ->
revalidation -> review -> acceptance, plus interruption/resume. Include SBT
regression and required integrated independent review. Fixture conversions and
CNCF operation success alone do not prove consumer Workflow completion.

Historical SBT records retain their original meaning. Never fill absent facts
with success. If an active Session needs migration, provide and qualify its public
adoption/continuation route while retaining identities, history and repair counts.
Do not substitute re-Start, private storage edits, accepted-result replay or
rerunning successful effects. Lost acknowledgement and unconfirmed termination
must not cause blind duplicate execution.

Exclude device/emulator management, distribution/publication and nested Workflow
management. Qualify at least one explicitly selected build target/mode and do
not claim unsupported platforms. Execute required checks through their existing
owning routes and keep missing evidence incomplete.

## Producer handoff and consumer delivery

The producer plan is `docs/phase/phase-117.md` in goldenport-cncf. Request its
bounded outcome, scope and acceptance conditions. CNCF completes its own
implementation, compilation, tests and required review, returning usable
revision/artifact, API guidance, evidence and limitations. It is not a child
Workflow in the parent Session; source edits alone are not completed delivery.
Reconcile already-recorded dependency work through supported public procedures.

W5 returns the usable sm-workflow version, configuration examples, qualified
SDK/targets, validation/review evidence, old-Session handling and remaining gaps.
Publication/deployment retains its actual authorization. Consumer acceptance
remains separate from producer acceptance, avoiding circular whole-Phase gates.
