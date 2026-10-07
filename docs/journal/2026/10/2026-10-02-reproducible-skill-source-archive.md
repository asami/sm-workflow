# Reproducible skill source archive

Goal State: IMPLEMENT — sm-workflow Phase 2. Previous turn changed the build
and proved source/JAR/ZIP instruction equality (214 tests / 33 suites): progress.
User-authorized parent limited recovery remains active; no agents. Parent model
and effort are not directly observable. Preserve original Phase scope, bases,
independent review and acceptance/release gates.

## Frozen implementation

Own build.sbt, new project/SkillSourceArchive.scala, the whole previously
inspected SkillResourcePackagingSpec.scala and this journal only. Input mapping
is still the exact three admitted component-owned skill sources. No new
dependencies, runtime Operation, installer, manifest or hash calculation.

The installed SBT 1.9.7 IO class was inspected with javap: the three-argument
zip overload accepts the explicit optional timestamp. A shared build helper
sorts archive paths and uses a fixed timestamp, removing the new two-argument
overload deprecation rather than suppressing it. Both ordinary packaging and
the reproducibility probe use this same production packaging helper.

checkSkillBundleReproducibility first packages the actual source ZIP, then
copies only those sources under target/skill-bundle-reproducibility, sets probe
file mtimes to 2000-01-01 UTC and reverses the input sequence before repackaging.
It directly compares complete archive bytes. Original source mtimes and content
are not changed; no broad cleanup is performed. The new Given/When/Then
scenario independently reads copied files, their assigned mtimes and both ZIPs.
Existing source/JAR/ZIP namespace and instruction equality examples remain.

Validation: serial cozyPrepareRuntime, packageBin,
checkSkillBundleReproducibility, then the unchanged GoalPhase/helper/normal
CLI-server accumulator plus all packaging examples. No edits during the run.
This proves same-runtime packaging reproducibility only, not reproducibility
across SBT/JDK versions or operating systems, nor CAR archive equality.

## Separate manifest question

The current public distribution design requires file digests. Management hashes
remain forbidden. An asynchronous human question asks whether SHA-256 may be
used solely for distribution-file integrity, never Phase authority, decisions
or continuation control. No digest implementation or consent was inferred.
Other safe implementation work continues; P2-C02 remains unchecked.

## First observed validation and bounded correction

Invocation 686 completed cozyPrepareRuntime, packageBin,
checkSkillBundleReproducibility and the focused accumulator. Full wrapper
summary records exits 0/0, lock=released, 215 succeeded tests / 33 completed
suites and no failures, aborted, canceled, ignored or pending tests.

Receipt: /tmp/skill.cncf.d/a-44d5c571-aca3-456a-9891-525f26f90eb1/64773-20261001T172509Z.summary.json

The packaging deprecation was removed, but the probe's IO.setModifiedTime
introduced another deprecation. After terminal completion and full receipt
intake, freeze the one-line M1 correction in the already owned build.sbt:
use standard Files.setLastModifiedTime with the same explicit FileTime value.
No source/path/time policy or archive semantics change. Validate the actual
packaging/reproducibility task and all three archive scenarios again; the 686
CLI/server accumulator remains prior-tree evidence, not newly executed proof.

## Settled packaging evidence

Invocation 687 completed the official preparation, actual JAR packaging,
reproducibility check and all three archive executable scenarios. Full summary
was read: exits 0/0, lock=released, 3/3 succeeded in one completed suite with
no failed/aborted/canceled/ignored/pending cases. Neither newly introduced
deprecation appears in this output; existing unused cozyCarName remains.

Receipt: /tmp/skill.cncf.d/a-90852f4c-358a-4d54-bd0a-cf92b6e99635/66423-20261001T172655Z.summary.json

The unchanged runtime/spec accumulator passed at 686; the settled build delta
has direct actual-artifact proof at 687. This is proportional focused validation,
not a new full-Phase suite or independent acceptance review. No installation,
CAR publication, staging, commit, push or release was performed.
