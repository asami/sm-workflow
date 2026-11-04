# Phase 18: Tool-Neutral Validation, Project Checks and Flutter Integration

Status: planned; no implementation or acceptance claimed.
Date: 2026-10-28
Owner: sm-workflow development
Predecessor: [Phase 6.7](phase-6.7.md) shared Task/FIX route and existing GoalPhase/RepositorySync validation
Producer: CNCF Phase 117 (required for W4–W5)
Checklist: [P18 acceptance](phase-18-checklist.md)
Handoff: [design handoff](../journal/2026/10/2026-10-28-tool-neutral-validation-handoff.md)

## Purpose and preservation

Commonize SBT, project-declared checks and Flutter validation requests/results
inside sm-workflow so existing Workflow progression, repair, review and
acceptance can consume the selected tool's actual evidence.
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
| W3 | W2 | Connect RepositorySync, StandaloneFix and other shared-engine consumers; add project-declared checks and Python defaults; qualify persistence/resume | Mixed Python/TOML/skill repositories use applicable checks; old records remain readable and new results resume through supported public routes without duplicate execution |
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

## Project-declared validation and Python defaults — 2026-11-05

W1 specifies a project validation declaration; W3 connects it to the common
execution/result contract and RepositorySync acceptance. A declaration identifies
the Check, applicable changed inputs, registered execution binding, workspace,
project environment, selected targets and success conditions. An explicit project
setting takes precedence over the documented default. File types identify
candidate checks; they do not prove that all project behavior is covered.

For Python, the initial default is `python -m unittest discover` in the declared
project environment and working directory. A project using pytest explicitly
selects `python -m pytest`; project settings may override discovery paths and
test selection. These are logical invocations resolved through the existing
registered execution owner, not a requirement to use the machine's global Python.
Resolve the selected framework before execution. A missing interpreter/framework
is an unavailable prerequisite; do not silently switch frameworks or install one.
Capture discovered/executed/skipped test counts and required target coverage.
No discovered tests, all-skipped tests or unverified selection are not automatic
success even if the process exits zero; any allowance requires explicit project
acceptance policy.

The same repository may declare Python syntax/import/lint/tests, TOML parsing
and application-specific schema/reference checks, and skill frontmatter,
referenced-file and bundled-script/isolated-behavior checks. TOML syntax alone
does not establish valid application configuration. Structural skill checks do
not replace required semantic review. Check contents and sufficient coverage
remain project-specific; execution, waiting, collection, verdict mapping,
repair/review progression and retained evidence are shared.

CNCF retains registered tool/process execution and tool-specific results;
sm-workflow owns selection, common result adaptation and Workflow acceptance.
Reuse applicable existing registered execution APIs and identify any missing
producer capability during W1/W3. Do not encode these checks as fake SBT tasks
or have AI construct execution evidence. W3's project-check qualification can
proceed independently of Flutter's W4 producer dependency. For an existing
Session, use a qualified public selection/adoption route where needed and
preserve accepted results, history and repair counts.

## Validation and completion

W1–W2 cover common outcome mapping, SBT success/failure/non-execution/uncertainty,
selection verification, FixCheck and existing GoalPhase transitions. W3 covers
historical decoding, new result roundtrips, public resume, applicable accepted
result reuse and each shared consumer's repair/review/closure behavior.

W3 additionally qualifies explicit Python settings overriding the default,
unittest default discovery, explicitly selected pytest, missing framework,
zero/all-skipped tests and incomplete target evidence. Exercise a mixed
Python/TOML/skill repository through actual RepositorySync checks, formal
failure -> repair -> revalidation -> independent review and purpose commit,
plus interrupted continuation without replaying successful checks. Include
configuration/schema and referenced-file failures, not only valid syntax.

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
