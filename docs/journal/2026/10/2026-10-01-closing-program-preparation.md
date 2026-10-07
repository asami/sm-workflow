# Declarative closing-program preparation

Status: internal preparation and native composition verified; Phase 2 remains
open. The preceding explanation-only turn was no progress. This turn inspected
current sources and persistent diagnostics, repaired executable specs and
completed new current-source validation. No new approval was required.

## Implemented boundary

`WorkflowClosingLocalIntent` privately freezes canonical revision guards and
Create-only records, recursively copying nested records and sequences. Every
projection is fresh; source array aliases and exported records cannot become
commit authority. Intent tokens are not host grants or evidence of a write.

`WorkflowClosingProgramPreparation.prepareC` folds an actual
`ExecUowM[ActionExecution]` through a dedicated preparation interpreter. The
private `StageWorkflowClosingLocalIntent` operation returns Unit for accepted
declaration, not completed storage. Only that operation is supported. Ordinary
DataStore writes, external operations and Authorize are rejected before their
interpretation; later stages are not visited. A valid actual Completed result
is required; Failed, Suspended, malformed results, missing programs and duplicate
targets cannot publish a preparation. Seven local guards and 64 Creates are
bounded; interruption preserves its control semantics.

The ordinary UnitOfWork interpreter refuses this staging primitive rather than
executing it through its ordinary data path. Its local classification adds no
commit permission. The new private host participant adapter obtains fresh
projections from the preparation and passes them into the existing host-selected
same-provider native completion. It does not execute local writes separately.

Real SQLite typed Completion integration now uses a test-authored closing
ExecUowM across empty, transient, retained, new and mixed event input. Preparation
leaves storage unchanged; one native commit retains the Workflow pair and local
guard/Create post-images. Delivery remains a test port, admitted only after
participant finish. Captured policy, explicit trusted test-host local grants,
reserved EventStore exclusion and native authoritative readback are preserved.

## Validation and correction history

- Attempt 386: production compiled, Test compilation failed on seven missing
  cats syntax extensions in two specs. It was a code error, not an approval
  refusal; SBT/wrapper 1/1 and lock=released.
- Attempt 387: both imports repaired; 319/320 tests passed in 34 suites. The
  failing alias-freeze fixture used an ordinary collection where canonical
  guarded updates require EntityStore. The fixture was corrected without
  relaxing the production contract.
- Attempt 388: same 34-suite selection, plus Create-limit and interruption
  scenarios, compiled and passed **322/322**, no failed, aborted, canceled,
  ignored or pending tests. SBT/wrapper 0/0 and lock=released. Persistent summary
  and relevant complete-log diagnostics were inspected. Normal caches and the
  mandatory serialized wrapper were used; no concurrent SBT was started.

386 evidence: `/tmp/skill.cncf.d/cncf-sbt-9e6899affac476a6e5326c186b97e4ab764b0388496e31fad3a633e31fdefdf9-f6e97d1bbfbd49e2cd2684b876538443/79935-20260930T180246Z.log`
and adjacent `79935-20260930T180246Z.summary.json`.

387 evidence: `/tmp/skill.cncf.d/cncf-sbt-25ade98aa3cc27394beacbce7d6cfb27cb8639f49b88cf417594a6150b101bbe-f2bad87d28fa14494a40118304054830/82756-20260930T181037Z.log`
and adjacent `82756-20260930T181037Z.summary.json`.

388 evidence: `/tmp/skill.cncf.d/cncf-sbt-7074f09d105e7f069f26be35227ea586acc68c7e03dd719a8c7ebc7f7bf6460d-c61dde3e7c57949088c12dd20c4d0bfa/83816-20260930T181316Z.log`
and adjacent `83816-20260930T181316Z.summary.json`.

Current SHA-256:

- `WorkflowClosingLocalIntent.scala`: `ca217c8dd9153f4ed050a6a1117ce4a3d2d3c8112d8992b5782557e03abed3b1`.
- `WorkflowClosingProgramPreparation.scala`: `1fbe54c0079834cd23f1d7d52af83ed53e16d1409187e8aa88639c52351628e6`.
- `WorkflowPreparedDataStoreTransition.scala`: `28cf8f8716c85933da7d0036ae7a966af02e4f63cceaa1e78d9369e391198d69`.
- `UnitOfWorkOp.scala`: `38dd0993f6680e37246a97a1ead3ca877268d110a0cd6c07afd179a3b2d2ffdf`.
- `UnitOfWorkProgramPlanning.scala`: `329fc2041e1f7d52bf4288af1c6652eeb1eb7e351178f4fceb544a6583df7c01`.
- `UnitOfWorkInterpreter.scala`: `b9e07bc085aac067c1ade7fb300d5423676af196ed61188eb9c95d3e4ded1f56`.
- `WorkflowClosingProgramPreparationSpec.scala`: `749f1dffd31b911eaf3d90a92d96c9a37f229dbd3d4baad1e017601b4d874fe0`.
- `WorkflowCompletionDataStorePersistenceSpec.scala`: `7eef5c7034e0c01af9859797359ec2258540d9b783423fef35be74a147e6b281`.

## Remaining production obligations

The program is real but authored by trusted test code, not selected/generated
from an admitted CML/StateMachine closing Action. This compiler supports a
declarative subset, not the general existing interpreter or its authorization
protocol. Outer Operation/security admission still needs an explicit binding.

The actual Action result is retained in the in-memory preparation. The generic
host adapter currently consumes its local intents only: it does not prove that
this Action result determines or is durably included in the independently
prepared Completion result. That binding and authoritative result readback must
be implemented before production activation; two separately successful results
are not equivalent evidence.

Production local declarations, live UoW opt-in/uncertain termination, affected
continuation/claim-release consumers, actual finalized original-order event
delivery, no transient restart/replay, launcher/CLI/MCP and packaging remain
required. Central commit state/termination and public Operations were not
activated. Existing ordinary interpreter only gains a fail-closed staging case.
No full-repository validation, acceptance, release, commit, push or publication
is claimed. S12/P2-U05/P2-U15/P2-U16 and the full Phase goal remain open.
