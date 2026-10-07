# Phase 1 three-Workflow consumer handoff

This identifies the intended application definition/fixture artifacts, not a CAR
publication or production skill installation. Public skills are `sm-goal-phase`,
`sm-split-phase`, and `sm-repository-sync`; legacy `cncf-*` remains independent.

## Source identity

Git base: `75337065397b49e1f663edac0b63600174e87e55` plus uncommitted fixture
implementation. Focused-228 binds all 200 product/build paths under `src/`,
`project/`, `scripts/`, `bin/`, `.gitignore`, `build.sbt`, and `project.yaml`.
Receipt: `035c91f7f883ab41145d6f7ccb9726fa4b46015ec0023940164db43f920ddd73`;
complete tree: `f6ad5fb5beeba8f6972e654fa5622b85d0373df6ae30e994a8aa607d69b0eb83`.
The content-bound receipt—not the Git base alone—identifies this implementation.
Final full-229 passed 526/526 tests across 56 suites, receipt
`ed90d2514e47449cefc567705bb0f3de8d0bc49f21038e57b6320c725ce2389b`,
complete tree `397303c4b65416ccfe1d369bd0df65521ff5926326a1960bc01f861a9bc1c0de`.
All 200 intended product/build hashes and the three generated ABI hashes remain
unchanged after validation. Documentation-only closure is recorded in
[Phase 1](phase-1.md); no commit/publication is implied.

Released baseline: Cozy Phase 62.3 fixture
`db5e2b6bc6a035b9c598abdeee56d856116ee0a4`, handoff
`f3c85b3de12c78efb6e88e210af71f1bd84ea9a5`; additive Provided API
`8d70aff3bc4b6e311bd7de38c209f08a7ad72bf0`; CNCF Phase 77.2 accepted handoff
`74aa88f4c4e4a8b2e4d681fe285cfe57bca9ba49`.
On 2026-09-29 the observed CNCF checkout was
`c4e1b24c134512f848aa11bd42d2664bddbb1396` (clean), Cozy was
`23de0b0b867e3f4d271c9b794b81baef65fb2d14` (28 dirty entries belonging to
other work, untouched here). Released history, current checkouts and consumed
SNAPSHOT binaries are distinct identities; source-to-binary equality is not assumed.

## ABI artifacts and fixtures

`src/main/cozy/sm-workflow.cml` SHA-256:
`1fa7fbd3b2115980db5c1922c21150ce36e5a297a40e54139d193fc968f3c55b`.
Schemas: `cozy.cml.statemachine-workflow-abi.v1`,
`cozy.cml.candidate-admission-producer-abi.v1`,
`cozy.cml.statemachine-provided-api-abi.v1`.
The following files are generated under
`target/scala-3.3.8/src_managed/main/scala/domain/statemachine/providedapi/`:

| Workflow / revision | Generated file / SHA-256 |
| --- | --- |
| GoalPhaseWorkflow / `goal-phase-step-close-v2` | `GoalPhaseWorkflowStateMachineProvidedApi1.scala` / `b72a43a0bde079d9e312cc8862b930f34ee8594b282ea8149df0737584f1d9b1` |
| SplitPhaseWorkflow / `split-phase-semantic-preview-v2` | `SplitPhaseWorkflowStateMachineProvidedApi2.scala` / `768b028de1fb77b49e9bc9a0659bfadae1dae19f446fa540d048986ad025fb06` |
| RepositorySyncWorkflow / `repository-sync-merge-convergence-v1` | `RepositorySyncWorkflowStateMachineProvidedApi3.scala` / `7389b5a31eea55f21db6fa8fa0067e7c04523039d919e810aeb7cd0a02f51e54` |

Retained GoalPhase JSON under `src/test/resources/workflow/`:

- `goal-phase-workflow-abi.json`: `86128694abef582670b80eddb454f8bca599119a4b525cdc80aa99afbf7edbc9`.
- `goal-phase-candidate-admission-producer-abi.json`: `db4a5d128a54d10957e6e659ed0c616734b24320a3a5b43de7f50ff24e243a64`.

Representative connected specifications under
`src/test/scala/org/simplemodeling/textus/smworkflow/admission/`:

- `GoalPhaseRepairLoopSpec.scala`: `f3a433e24aebd959a30c4cf9814f69031927daca90a3244f106fbfbf676eb887`.
- `SplitPhaseSemanticWorkflowSpec.scala`: `1c15ae27b465663f759be900ffd1f581c5a94bc44bb88b2b97813e1fee744293`.
- `RepositorySyncMergeValidationAdmissionExecutionSpec.scala`: `730fc05c2fbdc14db46decaa0f704dcf7606948422db9a9ba5d699fd3259be05`.
- `GoalPhaseWorkerFixtureHost.scala`: `f1f2d61681d6efb2a42dd9e53382cc93e1993227224aed3285424974d039a528`.

The receipt binds the full source inventory, not only these representative files.
Ignored generated output hashes above are not represented as tracked Git content.

## Actual resolved dependencies

Build coordinates: Scala `3.3.8`, Cozy `0.3.3-SNAPSHOT`, CNCF `0.5.3-SNAPSHOT`,
sbt-cozy `0.1.17`. `project.yaml` SHA-256:
`4329a374cfea139de101fc21de1959a420ea3a6fdef5521a4b2e4ba28cbe4b5d`;
`project/plugins.sbt`: `edfa50df4c767fe0fd618b634d2dbcdbacd2c3ca2d95141b4710ecdf98892d92`.
Actual live Test classpath export
`target/streams/test/fullClasspath/_global/streams/export`:
`df6b208a27b0682410026e386e16cdadf7af51d63ef3a12ee55a22f48b99af57`.

- `/Users/asami/.ivy2/local/org.goldenport/goldenport-cncf_3/0.5.3-SNAPSHOT/jars/goldenport-cncf_3.jar`:
  `ecb5ef1f4b6fd2511a9eb833f3dde9c4e1f6a322fb87abf1da8bb3d61f7f48b7`.
- `/Users/asami/.ivy2/local/org.goldenport/goldenport-core_3/0.4.3-SNAPSHOT/jars/goldenport-core_3.jar`:
  `9db85e55709d1d1a5eb8981a641d02dc71e51ec46c107d92cec8d98e1ec8d511`.
- Build runtime classpath resolves the plugin at
  `/Users/asami/.ivy2/local/org.goldenport/sbt-cozy/scala_2.12/sbt_1.0/0.1.17/jars/sbt-cozy.jar`.

## Actual worker / Provider replacement

[Retained live proof](evidence/goal-phase-codex-worker-20260929.json) contains
the current issued WorkOrder, observed Codex semantic answer, exact common
Result/ExecutionEvidence, public CLI outcomes and both transition traces.
The Host—not the worker—supplied the envelope and attested execution identity.
The independent known-candidate Provider used the same typed judgment contract
and definition/revision without inventing AI metadata. Each retained its
Start-created instance and stopped at missing commit authority; no real Git
mutation occurred. Logical review timestamps are not wall-clock execution claims.
The discarded stdin sessions are neither proof nor resumed instances.
