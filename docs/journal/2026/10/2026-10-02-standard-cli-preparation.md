# Standard CLI preparation under limited recovery

User-directed parent limited recovery continues without agents. Preserve the
whole sm-workflow Phase 2 scope and existing dirty files. No acceptance receipt,
original-base admission, commit, release or Phase completion is inferred.

The standard Factory passed packageBin and 203/203 examples in invocation 655.
The separate-process development evidence set under target/cncf.d is absent.
Prepare it with the project-owned logical task `cozyPrepareRuntime`, exact SBT
argv `["--batch", "cozyPrepareRuntime"]`, through the shared serial wrapper.
Do not execute scripts/update-runtime-classpath.sh, which invokes raw SBT.

The user's explicit recovery overrides the skill's in-progress command-route
migration stop and delegation route only. Preserve a fresh permission record,
the complete consumed-ID ledger, scoped platform escalation, ordinary caches,
machine-wide serialization and final lock-release/log/summary handshake.
Do not invoke unconverted command-intake/receipt helpers or fabricate acceptance
authority. Generated build artifacts are allowed in this repository; ordinary
SBT/Ivy/Coursier cache writes are allowed. No source producer edits, publication,
Git index/commit/push, credentials, remote writes or destructive cleanup.

After preparation, inspect all three actual generated development artifacts and
the canonical CLI configuration route before selecting a one-shot launcher
invocation. Do not fabricate actor, human selection, grants or verifier, or use
test-only runtime admission in production. Git and human-answer adapters remain
unavailable. Separate-process proof is still pending.

## Preparation failure and bounded packaging repair

656 started real SBT and terminated with exits 1/1 and lock=released. Its full
summary was read: no compile failure, no tests run. Cozy rejected missing
development Component identity evidence. target/cozy/model-metadata.json exists
but omits componentStyle because this CML has no STYLE declaration. The current
packager explicitly accepts a schema-3 authored src/main/car descriptor on this
style-less route; it does not generate the development descriptor from the
surface alone. Do not select a new domain-full capability style to clear this
error or fabricate generated target evidence.

Owned repair: add src/main/car/component-descriptor.json containing only the
exact project.yaml namespace/id/version, plus ComponentPackagingDescriptorSpec
exercising the actual framework loader and absence of policy/authority fields.
This is ordinary packaging identity, not a storage grant or runtime admission.
Rerun the same cozyPrepareRuntime and the packaging/default Factory examples.
No Cozy, sbt-cozy, CNCF, CML or production authority semantics are changed.

656 evidence:
/tmp/skill.cncf.d/a-3aa972c9-c83d-4674-994d-b692414e4f0d/5464-20261001T150103Z.summary.json

657 accepted the Component descriptor, then rejected the missing mandatory
src/main/car/abi-manifest.json before running tests. Exits 1/1, lock=released;
full summary read. This was another concrete packaging prerequisite, not
platform refusal or compile failure.

Extend the frozen packaging repair to src/main/car/abi-manifest.json and the
same packaging example. Mechanically project the current generated CML model
metadata using the exact public ABI shape of CozyCarAbiManifest.create: all
declared services, operation names/kinds/input/output types, model type fields
and entities; exact project identity; no declared CAR dependencies per
project.yaml. Do not use an empty operation facade, change generated target
evidence, add capability styles or manufacture execution authority. Verify the
authored ABI equals that projection on generation. This is a declared CML ABI,
not a claim that all declared operations are bound in the current default
Factory; its nine managed operations and missing adapters remain unchanged.

657 evidence:
/tmp/skill.cncf.d/a-05f9b5fe-39c9-44e9-a5e7-e1def322e8f0/6574-20261001T150402Z.summary.json

## Successful preparation and next bounded runtime check

658 passed cozyPrepareRuntime and 5/5 examples in three suites, with zero
failures/aborts/canceled/ignored/pending, exits 0/0 and lock=released. Complete
summary read. The official task generated all three required target/cncf.d
artifacts. Descriptor identity matches project.yaml; the ABI example compares
all nine declared CML services, 37 operations and 82 types (zero entities) to
current generated model metadata. No production binding count is inferred from
that declarative ABI. git diff --check passed.

658 evidence:
/tmp/skill.cncf.d/a-1dfc126e-6c45-4266-8247-ea0aa6300759/7653-20261001T150651Z.summary.json

Freeze one separate-process negative check: exact executable
/Users/asami/src/dev2026/sm-workflow/bin/launcher, cwd this project, argv
`--classpath-file /Users/asami/src/dev2026/sm-workflow/target/cncf.d/runtime-classpath.txt
--main-class org.goldenport.cncf.CncfMain --
--component-dev-dir /Users/asami/src/dev2026/sm-workflow help`.
The fully read launcher uses the existing classpath rather than coursier
fetch and execs Java; no SBT. Lifecycle one-shot, 30-second expected bound.
Normal CNCF artifact-cache access only if canonical admission needs it; no
publication, remote writes, Git mutation, credentials, user-process termination,
or destructive cleanup. Parent execution uses the authorized limited recovery
in place of agent routing. No test admission, actor, selection, storage grant
or policy defaults are supplied. Required result: terminal configuration
refusal, not a positive managed-host or operation-dispatch proof. A zero exit
with an error diagnostic is a failed CLI run, never successful operation use.

The frozen help invocation terminated with exit 0 and ordinary top-level help.
It did not construct the managed host and therefore did not exercise the
intended missing-configuration refusal. No refusal/successful Workflow claim.
Tool output chunk 6ce852; reviewed launcher SHA-256
0903ef70fa5c75a8b5a491baa5272ea7f74ecde58325309a757d8a2c1279a36e
is script-integrity evidence only, not workflow/command authority.

Select the same reviewed executable/classpath/dev-root and replace only app
command `help` with `command meta.help`, the canonical operation dispatch
example in the CLI's actual output. This is a distinct assembly-level negative
check, not a rerun claiming lost evidence. Same one-shot scope and prohibitions;
require actual terminal host configuration refusal before claiming fail-closed
assembly. No authority-bearing operation input is manufactured.

## Actual separate-process finding

`command meta.help` reached assembly and reported:
`system.corrupted-exception:java.lang.NoSuchMethodException:
org.simplemodeling.textus.smworkflow.admission.GoalPhaseConfiguredComponentFactory.<init>()`.
Initial output chunk f00d15, owned session 55731; subsequent same-session poll
terminated exit 0 (chunk c7e7e5). The diagnostic makes this an unsuccessful CLI
assembly check despite exit 0. No process remains; no managed operation dispatch,
missing-setting refusal or positive shared-pool construction was proved.

Read-only diagnosis: ComponentRepository._find_factory_class enumerates concrete
BundleFactory/Factory classes, excludes interface/abstract/module classes and
sorts by bundle preference, nesting and class name. It does not filter for the
public no-argument construction contract that _create_components_from_factory
then requires. The host-only GoalPhaseConfiguredComponentFactory has mandatory
constructor arguments and sorts before impl.ComponentFactory. The latter is
the valid no-argument launcher Factory. ComponentProvider has a separate
preferred-factory lookup, so its tests do not cover this repository discovery
route. Do not add a no-argument helper constructor with fabricated dependencies,
select a placeholder Factory, or remove configuration/grant requirements.

Next bounded repair frontier: correct actual repository discovery eligibility
for launcher-instantiable factories with an executable example that includes
an earlier-sorting host-only parameterized Factory, then refresh the admitted
CNCF producer artifact and repeat this actual consumer launcher check. Source
changes and validation for that new frontier have not yet occurred. The separate
canonical configuration path must also establish real storage declarations
before positive managed operation dispatch; current main-source declaration
construction is confined to SubsystemEventStoreBootstrap. No test-only admission
should be moved into production. All original Phase 2 obligations remain open.
