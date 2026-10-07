# Managed GoalPhase MCP publication

Goal State: IMPLEMENT — sm-workflow Phase 2. Parent limited recovery, no agents.
Previous turn passed 24 admission examples. Human evidence channel preference is
unanswered; this slice neither chooses a channel nor creates human authority.

## Frozen implementation

Own GoalPhaseConfiguredComponentFactory.scala, GoalPhaseLauncherAssembly.scala,
GoalPhaseLauncherAssemblySpec.scala and this journal. Whole selected programs
were inspected. No CML/ABI, producer, launcher or public wire schema changes.
Use canonical CNCF McpToolCatalog/adapter over the existing registered operations,
not a second dispatch/schema engine. Default configured factories stay MCP-deny.
Only the actual standard launcher assembly opts in to its exact nine currently
bound GoalPhaseService operations after successful initialization. Never enable
an entire service or generated 37-operation ABI, human issuer/answer entries,
SplitPhase/RepositorySync placeholders, meta/system, or a missing operation.
Require opt-in names to exist in the composed service before exposure.

Spec uses existing honest shared-SQLite fixture to compare catalog names with
actual bound operations and each input schema with CNCF's canonical schema
projection. This is publication/binding evidence, not normal-user network MCP,
human verification, positive Start or server/CLI outcome parity. Keep existing
missing-policy/grant refusals. Run official cozyPrepareRuntime, existing
GoalPhase accumulator and normal separate-process CLI spec through fresh serial
SBT permission; no edits during live command. No publication, Git, server,
credentials, remote effects, new authority records or cleanup.

## Validation 676 and explicit default-deny coverage

Official preparation and 208/208 examples in 31 suites passed. SBT/wrapper
exits 0/0, shared lock released; terminal and full summary read:
`/tmp/skill.cncf.d/a-04afde1e-2a8f-430b-86a9-136f8ab89c6b/43250-20261001T164023Z.summary.json`.
Actual nine-tool catalog and input-schema comparisons passed; normal-user
separate-process CLI regressions remained green. This is not network MCP parity.

Extend only the already-owned assembly spec with explicit generic-factory
default-deny and nonexistent-operation opt-in refusal examples. Do not publish
that fixture service or execute placeholder operations. Revalidate the same
accumulator using a fresh permission identity; production bytes unchanged.

## Validation 677 — limited recovery completed

Parent executed the existing shared serial SBT wrapper directly under the
user-authorized limited recovery, without a registered runner. Platform review,
fresh single-use permission metadata and the shared lock were retained.
Official cozyPrepareRuntime and 209/209 examples in 31 suites passed, including
the additional default-deny and nonexistent-operation publication refusals.
SBT/wrapper exits were 0/0, the lock was released, and the complete summary was
read:
`/tmp/skill.cncf.d/a-49bd0fb1-ad52-45b6-9dca-b5412a0849b0/45287-20261001T164526Z.summary.json`.
`git diff --check` also passed. No source edits occurred during the invocation.

This closes only this bounded publication slice. Actual network MCP/server
parity, independently verified human selection, successful normal-user Start,
the remaining Phase 2 adapters, independent review and release remain open.
No Git commit, publication, authority record or Phase completion was performed.
