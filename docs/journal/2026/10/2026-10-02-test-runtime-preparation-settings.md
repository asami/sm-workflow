# Test runtime preparation settings

The user requested: "テスト設定を補正して続けて". This is a bounded
test-configuration correction within the retained parent-only limited recovery.
It neither replaces the original Phase 2 Goal nor accepts a Step or Phase.
No agents, skill edits, upstream source edits or Git mutations are selected.

## Observed gap and selected correction

`CanonicalGoalPhaseLauncherSpec` launches real CLI/server processes using the
official `target/cncf.d/runtime-classpath.txt`, `component-descriptor.json` and
`car-runtime-manifest.json`. Its `run` method explicitly requires these files.
The existing `build.sbt` did not connect their producer to normal `test` or
`testOnly`; earlier validation invocations supplied `cozyPrepareRuntime`
separately. Passing those earlier tests is not clean-test configuration proof.

The installed sbt-cozy source defines `cozyPrepareRuntime` in terms of main
compilation and official runtime evidence generation, with no Test dependency
and no independent top-level SBT process. Add it as a prerequisite of
`Test / compile`, which both test entry points already consume. This preserves
the normal test selection/filtering instead of overriding their input tasks.
No fixture substitutes for official generation and no tracked runtime files
are introduced.

Set `Test / parallelExecution := false`, `Test / fork := false` and
`Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat`.
The upstream CNCF build also uses serial tests and Flat layering. These settings
make integration-suite scheduling and the in-process resource/reflection loader
explicit; they are not evidence that a particular race has been reproduced.
CLI/server examples still start their own real separate processes. Do not copy
upstream ManualSpec/TimingSpec exclusions or introduce an authentication bypass
property, so the consumer's complete test coverage remains selected.

## Validation boundary

Run one fresh serial-wrapper invocation with exact logical argv:

```text
["--batch", "show Test / parallelExecution", "show Test / fork",
 "show Test / classLoaderLayeringStrategy", "test"]
```

There is deliberately no explicit `cozyPrepareRuntime` argument: successful
runtime generation before the full test execution must come from the build
dependency. Do not delete existing runtime files or run `clean` solely to prove
absence; inspect the task output and actual regenerated artifact timestamps.
This verifies task execution without claiming an independent empty-cache build.

Use the user's retained parent-only execution exception, a fresh typed
permission allocation, the complete prior consumed-ID ledger, normal caches,
fixed JVM settings, first-attempt scoped platform review and the unchanged
machine-wide SBT lock. No edits while SBT is live. Consume and retain the
actual matching terminal marker and complete machine-readable summary; report
any stable failure before freezing a separate bounded repair.

Full-suite success alone is not independent review, acceptance, public
`advanceSplitPhase` completion, checkpoint recovery or Phase closure. The
passive planning snapshot and native publication/recovery remain development
work after this test-settings correction.

## Result

Invocation `SMWF-TEST-SETTINGS-FULL-DIRECT-20261002-803` confirmed the three
settings as false/false/Flat. Without an explicit preparation command, normal
`test` wrote the official runtime classpath and development runtime manifest
before executing tests. All 123 suites completed: 924 tests, 922 succeeded,
two failed, no aborted/canceled/ignored/pending tests. Compilation succeeded;
SBT/wrapper exits were 1/1 and the matching terminal marker confirmed lock
release. The complete summary and both exact failure diagnostics were read.

Log: /tmp/skill.cncf.d/a-c6791cf7-c0b8-4ccb-89d3-ec4ec15dd0da/69174-20261002T112250Z.log
Summary: /tmp/skill.cncf.d/a-c6791cf7-c0b8-4ccb-89d3-ec4ec15dd0da/69174-20261002T112250Z.summary.json

## Bounded stale-contract correction

The failures were not a permission denial, compilation problem, skipped suite
or reproduced scheduling race. `WorkflowStateMachineGuardSpec:79` expected
only the historical SplitPhase Judgment, although CML now also declares
`AssessPartitionSemanticInputs` with exactly `Enriched` and `DecisionNeeded`.
Extend the exact ordered expectation; keep the all-alternative/state/event
disjointness check unchanged, covering both old and new alternatives.

`ComponentPackagingDescriptorSpec` compared the complete authored ABI manifest
with the current generated CML metadata. The authored manifest omitted the
new partition operations/types. Extend the corrective scope to
`src/main/car/abi-manifest.json`, mechanically projecting the full existing
Cozy public ABI shape from `target/cozy/model-metadata.json` under the original
project component identity and declared empty CAR dependencies. Preserve all
old declarations, source metadata, production semantics and the full equality
spec. No empty facade, filtered comparison, snapshot test waiver or runtime
authority is introduced. Runtime preparation still validates the descriptor;
the declarative ABI is not proof that all its operations are bound by default.

Repair counter for this test-settings correction: 1/3; predecessor consumer
0/3 and producer 3/3 remain unchanged. After the settled repair, re-run one
fresh normal `test` (without a separately invoked prerequisite). No source
edits while it is live. Full-suite success remains separate from Phase closure.

## Settled validation and development continuation

Invocation `SMWF-TEST-SETTINGS-REPAIR-FULL-DIRECT-20261002-804` ran only
`--batch test`. The new build dependency again wrote the official runtime
classpath and development manifest before executing the suites. The changed
guard spec compiled, and all **924/924 tests in 123 suites passed**, with zero
failed/aborted/canceled/ignored/pending tests. SBT/wrapper exits were 0/0 and
the exact terminal marker confirmed `lock=released`. The complete summary and
both formerly failing suites' successful examples were read. There were no
source or spec edits during either live SBT invocation.

Log: /tmp/skill.cncf.d/a-d78d36d8-4c42-4a6a-a9d1-0a0909902a8a/71654-20261002T112725Z.log
Summary: /tmp/skill.cncf.d/a-d78d36d8-4c42-4a6a-a9d1-0a0909902a8a/71654-20261002T112725Z.summary.json

ABI synchronization added exactly three operations and twenty partition types.
No old operation or type was removed or changed; service/entity declarations
and non-ABI component fields are unchanged. The original complete-equality
spec now passes without filtering or a fixture fallback. Timestamp-based CML
generation reuse was observed, so this is not a clean-generation claim.

Tracked `git diff --check` and explicit whitespace checks of all owned untracked
build/ABI/spec/journal paths passed. The complete consumed execution ledger
contains 473 IDs, through 804. This correction used one bounded repair (1/3),
without altering other repair histories. No independent review, Git mutation,
remote publication or original Phase acceptance is claimed.

The test-configuration interruption is resolved. Continue the original
mechanical checkpoint consumer design at its passive planning snapshot
contract: retain the whole mixed candidate frontier, unresolved needs,
short-child review, selection scores (including values exceeding Long) and
authority distinctions without re-enumeration on recovery. Then connect the
snapshot to the admitted native checkpoint/document transaction and public
current-state advance. Those production changes were not implemented by this
test-settings correction; the original Goal and required closure remain open.
