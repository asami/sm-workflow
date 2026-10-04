# Phase 7: Dependency-Aware Multi-Repository Synchronization and Validation

Status: planned
Planned: 2026-10-04
Depends on: Phase 1, Phase 5, Phase 6

## Goal

Extend the Phase 1 `RepositorySyncWorkflow` from synchronization of one project repository into dependency-aware synchronization and validation for a development project and the related repositories whose project-specific branches it uses.

For a root project X, synchronization MUST include X itself and the related repositories explicitly participating in X through project-specific branches. When remote changes are incorporated into one of those related repositories, sm-workflow validates the changed dependency first and validates X only after all changed dependencies have passed their full tests.

This is an extension of the existing `RepositorySyncWorkflow`, not a second synchronization workflow. It is also the project-workspace convergence counterpart to `sm-goal-phase`: GoalPhase closes work locally in one admitted repository/worktree; RepositorySync brings the root repository and its dedicated related worktrees into synchronized, validated GitHub convergence.

## GoalPhase / RepositorySync responsibility split

`sm-goal-phase` owns repository/worktree-local development closing:

```text
edit -> test/review -> admission -> CommitChanges -> local commit -> Step/Phase close
```

`sm-repository-sync` owns Project Workspace synchronization/convergence:

```text
root + dedicated related worktrees
  -> fetch/classify
  -> fast-forward or non-rewriting merge as required
  -> dependency-aware validation/full tests
  -> permitted non-force push
  -> convergence verification
```

RepositorySync MUST consume already-created local commits from GoalPhase without reimplementing GoalPhase closing. Conversely, GoalPhase MUST NOT grow multi-repository fetch/push/convergence semantics merely because its current worktree belongs to a larger Project Workspace.

## Example

Project X uses project-specific branches of CNCF and Cozy.

If synchronization incorporates a remote change into CNCF only:

```text
sync Project X / CNCF / Cozy
  -> CNCF: remote change incorporated
  -> Cozy: unchanged
  -> full test CNCF
  -> full test Project X
```

If both CNCF and Cozy incorporate remote changes:

```text
sync Project X / CNCF / Cozy
  -> CNCF: remote change incorporated
  -> Cozy: remote change incorporated
  -> full test CNCF --+
                       +-> when both succeed -> full test Project X
  -> full test Cozy --+
```

Independent dependency tests SHOULD execute concurrently. Phase 5 execution policy applies: sm-workflow MUST NOT serialize sbt merely because multiple tests use sbt.

## CNCF Component resource integration

Phase 7 is the primary sm-workflow driver scenario for CNCF Phase 101. Dedicated/project-specific checkouts or worktrees MUST be acquired through the CNCF logical Component workspace/resource API. RepositorySync logic MUST NOT construct `$PROJECT/.textus/sm-workflow/...` paths itself.

The standard local mapping places sm-workflow definitions at the Component-area root, version-controlled assets under `resources/`, and runtime state/worktrees under ignored `work.d/`. The project generator supplies the generic `.textus/*/work.d/` ignore rule; RepositorySync MUST NOT edit `.gitignore`. The local provider may map project resources under the project-local Textus component area, while the logical contract keeps that physical layout outside Workflow semantics. Project-local runtime state/DataStore and worktrees share the same Component resource ownership model, but configuration continues to use CNCF's existing hierarchical configuration resolver.

## Repository set

The synchronization set consists of:

1. the root development project; and
2. related repositories explicitly used through project-specific/dedicated branches for that root project.

RepositorySync MUST NOT recursively synchronize every transitive library dependency merely because it exists in the build dependency graph.

The source of the project-specific repository/branch relationship MUST be deterministic project configuration or equivalent explicit project metadata. Discovery by repository-name guessing is not allowed.

## Synchronization result

RepositorySync records a typed result for every repository sufficient to distinguish at least:

- UNCHANGED — no remote change was incorporated;
- UPDATED — remote change was incorporated into the local project branch;
- CONFLICT — synchronization cannot be completed without resolution;
- FAILED — synchronization itself failed.

The implementation MAY refine UPDATED into fast-forward/merge/rebase-related facts when useful, but validation policy is based on the semantic fact that external changes were incorporated, not on a particular Git command.

A fetch with no incorporated working-branch change is not UPDATED.

## Validation policy

1. Every related repository with result UPDATED MUST receive its full test.
2. Related repositories without incorporated remote changes MUST NOT receive a full test merely as a precaution.
3. Full tests for independent UPDATED related repositories MAY run concurrently.
4. The root project full test MUST wait until every required related-repository full test succeeds.
5. If one or more related-repository full tests fail, the root full test MUST NOT be reported as validation of the synchronized dependency set. The Workflow remains unresolved with explicit failure evidence.
6. If the root project itself is UPDATED, it requires a full test.
7. If any related repository is UPDATED, the root project requires a full test even when the root repository itself was unchanged, because the effective dependency baseline of the root project changed.
8. Therefore, when neither the root nor any related repository incorporates remote changes, synchronization completes without a precautionary full test.

## Dependency ordering

Validation follows the explicit project dependency relation, not repository enumeration order.

For the Phase 7 minimum scope, project X is the root and its dedicated-branch repositories are dependency nodes. If dependencies between those related repositories are explicitly known, their tests MUST respect that ordering. Otherwise independent nodes may be tested concurrently.

The root project is the final validation node.

## Operations

Retain the Phase 1 application boundary:

- `StartRepositorySync(RepositorySyncStartInput, WorkflowInvocationSelection)`
- `SubmitRepositorySyncWorkResult(RepositorySyncWorkResult)`

Extend their typed payloads/results as necessary rather than introducing a parallel ProjectSync protocol.

The Workflow should materialize bounded work orders for Git synchronization and full-test execution and receive typed evidence through the existing continuation/completion boundary.

## Evidence

For each repository preserve enough evidence to establish:

- repository identity;
- local/project-specific branch identity;
- synchronization result;
- whether a remote change was incorporated;
- before/after revision identity as normal Git revision evidence;
- whether full test was required;
- full-test execution identity and result when required.

Revision identifiers are observational Git evidence. Phase 7 MUST NOT introduce custom content hashing or integrity machinery.

## Conflict and failure behavior

A Git conflict is a normal explicit Workflow outcome requiring resolution. Phase 7 MUST NOT add speculative rollback, repository backup, duplicate working trees, integrity verification, or contamination-prevention machinery merely because synchronization may fail.

After conflict resolution, validation requirements are derived again from the actual incorporated changes.

## Executable Specification requirements

Demonstrate at least:

1. root and related repositories unchanged -> no full test;
2. root only UPDATED -> root full test;
3. CNCF-like dependency only UPDATED -> dependency full test, then root full test;
4. two independent dependencies UPDATED -> their full tests may overlap, then root full test after both succeed;
5. dependency full test failure -> root validation does not proceed as if the synchronized set were valid;
6. root unchanged but dependency UPDATED -> root still receives full test;
7. fetch without incorporation -> no UPDATE-triggered full test;
8. synchronization conflict -> explicit unresolved result, not automatic defensive recovery;
9. two sbt-based dependency full tests are not globally serialized by sm-workflow solely because they use sbt.

## CNCF Phase 101 closure gate

Phase 7 is the final driver acceptance for CNCF Phase 101. After the RepositorySync/worktree acceptance scenario succeeds, Phase 7 MUST cause/record CNCF Phase 101 full acceptance and closure before Phase 7 itself closes. A Phase 5 minimum-slice acceptance of Phase 101 is not sufficient for this gate.

The closure order is:

```text
sm-workflow Phase 7 driver acceptance
  -> CNCF Phase 101 full acceptance / close
  -> sm-workflow Phase 7 close
```

## Dogfooding / reference scenario

The development of sm-workflow itself is a normative driver scenario: sm-workflow uses a dedicated CNCF worktree to develop CNCF Phase 101 while the root sm-workflow project proceeds through its own phases. GoalPhase may locally close work in either admitted worktree; RepositorySync must later treat the root and dedicated CNCF (and, where present, Cozy) worktrees as one explicit Project Workspace for synchronization, dependency-aware validation, permitted push, and convergence evidence.

This scenario must be supported through the same public project/workspace model intended for later `sm-repository-sync` use, not by a one-off development script or hard-coded repository names.

## Practical completion condition

Phase 7 is complete when a real root project using at least two project-specific related repository branches can execute one RepositorySyncWorkflow that:

- synchronizes the complete explicit repository set;
- identifies exactly which repositories incorporated remote changes;
- runs full tests only where required by the validation policy;
- runs independent dependency tests concurrently where possible;
- waits for successful dependency validation before root validation; and
- returns typed synchronization and validation evidence for the complete operation.

## Non-goals

- Synchronizing every transitive build dependency.
- Global sbt/Ivy/Coursier locking.
- Full-testing unchanged dependency repositories as a precaution.
- Automatic semantic conflict resolution.
- Defensive repository backup/rollback/integrity machinery.
- Replacing Git's own merge/conflict semantics.
- General provider-selection semantics; generic execution-requirement semantics are established in Phase 6.
