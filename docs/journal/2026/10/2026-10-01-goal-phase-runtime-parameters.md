# Typed GoalPhase runtime persistence parameters

Continue the same Phase 2 after the user selected existing factory initialization.
Previous turn made progress: attempt 485 packaged the actual main JAR and passed
66 tests in nine suites. No SBT invocation remains live from that attempt.

Own GoalPhaseRuntimeParameters.scala, its executable spec, the managed suspension
spec's factory composition helper, and this journal. Parent-direct limited
recovery remains selected. No CNCF source/API or physical resource changes.

Declare seven required typed keys under sm-workflow.goal-phase.persistence:
store-name, store-identity, migration-policy, retention-policy, lease-policy,
instance-collection and continuation-collection. Collections use the existing
EntityCollectionId canonical serialization/parser, not a custom name grammar.
Policy values are explicit version references. Require exact nonblank strings;
reject trimming/coercion, noncanonical logical store names and aliased collections.
SameStore delivery is the current admitted runtime profile, using the explicit
store identity. No fallback store/policy or physical path is added.

Canonical Component parameter resolution supplies layers/provenance. Decode the
immutable snapshot to the existing managed Start Declaration bound to the
admitted catalog. Decoding grants no storage access and creates no pool. Existing
WorkflowComponentStorage/Subsystem admission must still validate configured
store and exact collections. No authority record is inferred from these settings.

Use these keys in the real managed SQLite factory helper so existing Start,
reopening and repair/re-review scenarios consume the decoded declaration.
Separate Given/When/Then scenarios test missing each key, wrong types/blank values,
malformed collection IDs, aliases, store normalization, explicit policy retention,
and missing catalog. No test fixture is promoted into main resources.
Validate actual packageBin and the nine-suite accumulator plus the new suite.
Durable selection/association/closure authority and default launcher wiring
remain separate required work; this is not full CLI or Phase acceptance.

Implementation now declares seven typed required keys, uses canonical
EntityCollectionId.parse with exact serialized-identity comparison, constructs
SameStore with explicit policy references, and rejects aliased collections.
The managed SQLite composition helper supplies canonical instance settings and
uses decodeC's resulting declaration, comparing it with the frozen host grant
scope before invoking the existing managed-service binder.

Attempt 486 (SMWF-GOAL-RUNTIME-PARAMETERS-DIRECT-20261001-486) selected actual
packageBin and ten suites, including five new runtime-parameter scenarios.
The wrapper accepted its fresh permission metadata and is currently waiting for
another active SBT invocation. The live PTY handle is 41746; repeated observation
confirmed the same handle still running and lock-wait messages through 90s.
This is a verified wait, not command completion, test evidence, platform refusal
or a blocker. Resume that same handle; do not start another SBT invocation or
edit this candidate's source while it waits. The implementation's diff check
passed. No test result or Phase acceptance is claimed yet.

Attempt 486 has now terminated with SBT/wrapper exits 1 and lock released.
Main packaging and all five new parameter scenarios passed, but three managed
Start/reopening scenarios failed because the test's ComponentCreate omitted a
canonical descriptor. Seven required typed declarations now correctly trigger
ComponentParameterContext admission, which requires that descriptor even when
instance settings are supplied. Freeze the repair to the managed test helper:
supply the same exact component identity/schema descriptor as the other
configured-factory specs, retaining existing host grants and metadata settings.
Do not bypass descriptor selection or change production admission. Also include
an uppercase store-name variant in the existing normalization-rejection scenario
to exercise the canonical-name comparison after exact-string decoding.
The complete summary was read at
`/tmp/skill.cncf.d/cncf-sbt-b3be0542cbe77732b5e86fc29746017e6357b3b4c3a93954ddb0214fc71fb310-d6228649146ec789c99cfa92d6a1923e/53213-20261001T015524Z.summary.json`:
71 tests, 68 passed, 3 failed, ten suites completed, no abort/cancel/pending.

Attempt 487 resolves descriptor admission but exposes an incorrect fixture
assertion: Declaration case-class equality compares its opaque DefinitionBinding
by object identity. Independently admitted bindings with identical facts are not
the same object. Replace the whole-Declaration assertion with exact configuration/
collection comparison plus GoalPhaseRuntimeCatalog.admitBindingC, which verifies
the admitted binding fields and exact raw artifact fingerprint. Apply the same
contract-based assertion to the new parameter success case. No production source
or admission rule is changed. This is a narrower test failure after the missing
descriptor was repaired, not a repeated descriptor failure.
SBT/wrapper exited 1 and lock released: 71 tests, 68 passed, 3 failed in ten suites.
Complete summary was read at
`/tmp/skill.cncf.d/cncf-sbt-4121588118dfa06417756b92fb954216f5ba934fc0c16d77b5e1b85d3f60b31d-a242dc3d83defd1a7103966a0a5f93c3/60496-20261001T015936Z.summary.json`.

Attempt 488 (SMWF-GOAL-RUNTIME-PARAMETERS-DIRECT-20261001-488) passed actual
Compile / packageBin and all 71 tests in ten suites. SBT/wrapper exits are 0,
the shared lock is released, and no suite aborted or test failed/canceled/
ignored/pended. The complete structured summary was read at
`/tmp/skill.cncf.d/cncf-sbt-1cd75f32e5da97a05f92a5367fa997620a9fe3ea79960ef8459db6db579fe57e-ef4e7b5d6ac90613df2d2e1d7cd353d3/62806-20261001T020408Z.summary.json`.
The descriptor and opaque-binding assertion repairs are now validated; no
production admission rule was relaxed. git diff --check also passed.

This confirms the bounded typed logical persistence configuration path through
the user-selected existing post-resolution Factory hook, including managed
SQLite Start/reopen and ordinary/interrupted repair/re-review examples. It is
not production CLI activation or durable host authority proof: the default
launcher still uses placeholder services and the managed examples retain
test-owned selection/association/closure authority ports. Preserve these as
open implementation obligations; no Phase or production acceptance item closes.
