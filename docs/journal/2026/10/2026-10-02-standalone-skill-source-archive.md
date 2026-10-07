# Standalone semantic skill source archive

Goal State: IMPLEMENT — sm-workflow Phase 2. Previous turn completed actual JAR
resource packaging and focused regression evidence (213 tests / 33 suites).
User-authorized parent limited recovery remains the execution owner; parent
model and effort are not directly observable. No agent was started.

## Frozen slice

Own build.sbt, SkillResourcePackagingSpec.scala and this journal. Whole target
programs were inspected before editing. Preserve unrelated dirty work and all
Phase acceptance/review/original-base obligations. No acceptance commit here.

Factor the exact three source mappings into skillSourceFiles. Both component
resources and skillBundle use those mappings. skillBundle creates only a
target-owned ZIP with standard skills/<name>/SKILL.md paths and the admitted
component version in its filename. Missing source fails rather than producing
substitute instructions. No installation, overwrite of user skills, script
execution, framework dependency, digest or invented Operation binding is added.

The new AnyWordSpec / GivenWhenThen scenario opens the actual ZIP and component
JAR and compares each entry with the independently read authoring source.
Exact ZIP enumeration detects missing, duplicate and extra files. Streams and
archives are closed on failure. The existing JAR namespace scenario remains.

Validation plan: serial cozyPrepareRuntime, packageBin, skillBundle, then the
existing GoalPhase/helper/normal-server accumulator and packaging spec. No
edits during the live invocation. Independent review and full Phase validation
are not replaced by this focused evidence.

This ZIP is a source distribution candidate, not the complete neutral
SkillBundleManifest contract. Manifest compatibility/entry bindings, integrity
policy, CAR integration, safe installer, installed semantic round trips and
AI invocation-cost acceptance remain open. P2-C02 remains unchecked.

## Observed validation

Attempt 684 terminated before lock acquisition and before SBT startup with
`permission metadata artifact is missing or consumed`. The parent constructed
the command by replacing the delegation ID before replacing the old full path;
that left the old allocation directory with the new filename. This was a
parent request-construction error, not platform permission denial or a product
test failure. No live process was restarted and no metadata was replayed.
Attempt 685 used a fresh allocation and permission record, replacing the exact
old path before the delegation ID. Both attempts remain in the attempt ledger.

Invocation SMWF-STANDALONE-SKILL-ARCHIVE-DIRECT-20261002-685 completed the frozen
commands: cozyPrepareRuntime, packageBin, skillBundle and the focused test
accumulator. Full summary was read: sbt_exit=0, wrapper_exit=0, lock=released;
214 tests succeeded in 33 completed suites, with no failed/aborted/canceled/
ignored/pending tests. Both actual-archive packaging scenarios passed, along
with existing normal-user CLI/server decoder and admission regression specs.
git diff --check passed after the command terminated.

Receipt: /tmp/skill.cncf.d/a-e6f7c701-c815-4d36-9678-0f11f72109a8/62316-20261001T172048Z.summary.json

Generated candidate: target/sm-workflow-skills-0.1.0-SNAPSHOT.zip. This contains
exactly the three standard skill source paths, with bytes equal to the component
JAR and authoring files. No user installation or CAR publication occurred.

Nonblocking build observation: IO.zip's two-argument overload emits a
deprecation warning requesting an explicit static-timestamp policy. Instruction
byte equality is verified; reproducible archive bytes/timestamps are not yet
claimed. The existing unused cozyCarName setting warning also remains. Neither
warning was silenced or used to redefine distribution acceptance.

Full Phase review, original-base admission and acceptance/release gates remain
required. This focused result does not close Phase 2 or P2-C02.
