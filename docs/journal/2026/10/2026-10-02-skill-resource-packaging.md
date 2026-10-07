# Skill authoring sources in the component JAR

Goal State: IMPLEMENT — sm-workflow Phase 2. Previous turn created and validated
three semantic skill sources. User-authorized parent limited recovery; no agents,
management hashes or acceptance/release claim.

## Frozen implementation

Own build.sbt, new SkillResourcePackagingSpec.scala and this journal only.
Whole build inspected; exact artifact identity is current project.yaml's
textus-sm-workflow_3:0.1.0-SNAPSHOT. Use ordinary SBT resource generation to
copy exactly three existing skill sources to META-INF/sm-workflow/skills under
resourceManaged. Missing source is a build failure; no generating alternative
instructions, user installation, launcher/runtime/schema changes or new digest.
Do not claim a neutral manifest/standalone bundle from these bare resources.

Executable spec opens the actual packageBin JAR, enumerates the entire skill
resource prefix and compares all three entries byte-for-byte with source files.
No wording/regex test is substituted for behavior, no control hash is used.
Validate official cozyPrepareRuntime, packageBin and the existing GoalPhase/
helper accumulator plus the new packaging spec using fresh serial SBT metadata.
No edits during live command, Git, publication, user config or consent records.
Standalone/CAR inclusion, manifests, real semantic worker/client round trips
and installer behavior remain open; P2-C02 is not marked accepted here.

## Observed validation

Serial invocation SMWF-SKILL-RESOURCE-PACKAGING-DIRECT-20261002-683 completed
cozyPrepareRuntime, packageBin and the frozen focused test accumulator.
The full wrapper summary records sbt_exit=0, wrapper_exit=0 and lock=released:
213 tests succeeded in 33 completed suites; no failures, aborted suites,
cancellations, ignored or pending tests. The actual JAR packaging assertion
passed, including exact resource enumeration and byte-for-byte source equality.
Existing normal-user CLI/server refusal and nine-operation decoder parity
specs also passed. git diff --check passed after command completion.

Receipt: /tmp/skill.cncf.d/a-d8bd540c-907f-47a5-a5a6-73a1844564f0/58998-20261001T171416Z.summary.json

This is focused packaging and regression evidence, not full Phase acceptance.
No skill installation, standalone bundle, CAR distribution validation,
independent review, Git commit, push or release was performed. The skill-creator
guidance used for the authoring sources keeps the bundled instructions minimal
and semantic-only; the packaging task copies their bytes without rewriting them.
